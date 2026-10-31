/* Factory-root session broker. Linux/Android only; no UART or shell operations.
 * The existing bridge owns the STOP/physical-idle contract. Closing this broker
 * never manufactures an idle result, and a child is never forcibly killed.
 */
#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <poll.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <errno.h>
#include <limits.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define MAX_CONFIG 2048
#define MAX_REQUEST 256
#define MAX_CLIENTS 16
#define BUFFER_BYTES 16384
#define LEASE_SECONDS 14400
#define REQUEST_MS 2000

struct config {
    uid_t uid;
    char socket_name[108];
    char token[65];
    char bridge[PATH_MAX];
    long long expires;
    int64_t deadline;
    int bridge_fd;
};
struct request {
    int fd;
    uid_t uid;
    size_t used;
    char line[MAX_REQUEST + 1];
    int64_t deadline;
};
struct child {
    pid_t pid;
    int client, input, output;
    int stopped, exited, output_eof, code, exit_queued, output_newline;
    unsigned char to_child[BUFFER_BYTES], to_client[BUFFER_BYTES];
    size_t input_used, output_used;
};
static volatile sig_atomic_t interrupted;

static void on_signal(int sig) { (void)sig; interrupted = 1; }
static int64_t monotonic_ms(void) {
    struct timespec t;
    if (clock_gettime(CLOCK_MONOTONIC, &t)) return -1;
    return (int64_t)t.tv_sec * 1000 + t.tv_nsec / 1000000;
}
static void close_fd(int *fd) { if (*fd >= 0) close(*fd); *fd = -1; }
static int nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL);
    return flags < 0 ? -1 : fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}
static int private_dir(int fd) {
    struct stat s;
    return !fstat(fd, &s) && S_ISDIR(s.st_mode) && s.st_uid == 0 &&
           (s.st_mode & 077) == 0 && (s.st_mode & 0700) == 0700;
}

/* All components are resolved through no-follow directory FDs. The Android
 * outer /data/local/tmp may be shell-owned; the xcr-access subtree and final
 * session directory must be root-private. The bridge is opened relative to
 * the same held session FD, then executed by FD, so an outer rename cannot
 * substitute either config or executable after validation.
 */
static int session_dir(const char *path, char *basename, size_t cap) {
    if (!path || path[0] != '/' || strlen(path) >= PATH_MAX) return -1;
    char copy[PATH_MAX];
    strcpy(copy, path);
    char *last = strrchr(copy, '/');
    if (!last || !last[1] || strlen(last + 1) >= cap) return -1;
    strcpy(basename, last + 1);
    *last = 0;
    int fd = open("/", O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    if (fd < 0) return -1;
    int anchored = 0;
    char *cursor = copy + 1;
    while (*cursor) {
        char *slash = strchr(cursor, '/');
        if (slash) *slash = 0;
        if (!*cursor || !strcmp(cursor, ".") || !strcmp(cursor, "..")) goto bad;
        if (!strcmp(cursor, "xcr-access")) {
            struct stat s;
            if (fstat(fd, &s) || (s.st_mode & 0002)) goto bad;
            anchored = 1;
        }
        int next = openat(fd, cursor, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
        if (next < 0) goto bad;
        close(fd);
        fd = next;
        if (anchored && !private_dir(fd)) goto bad;
        if (!slash) break;
        cursor = slash + 1;
        if (!*cursor) goto bad;
    }
    if (!private_dir(fd)) goto bad;
    return fd;
bad:
    close(fd);
    return -1;
}

static int number(const char *s, unsigned long long limit, unsigned long long *value) {
    if (!*s || (s[0] == '0' && s[1])) return -1;
    unsigned long long n = 0;
    for (; *s; s++) {
        if (*s < '0' || *s > '9' || n > (limit - (unsigned)(*s - '0')) / 10) return -1;
        n = n * 10 + (unsigned)(*s - '0');
        if (n > limit) return -1;
    }
    *value = n;
    return 0;
}
static int lowercase_hex(const char *s, size_t bytes) {
    if (strlen(s) != bytes) return 0;
    for (size_t i = 0; i < bytes; i++)
        if (!((s[i] >= '0' && s[i] <= '9') || (s[i] >= 'a' && s[i] <= 'f'))) return 0;
    return 1;
}
static int load_config(const char *path, struct config *c, int admin) {
    memset(c, 0, sizeof(*c));
    c->bridge_fd = -1;
    char name[PATH_MAX];
    int dir = session_dir(path, name, sizeof(name));
    if (dir < 0) return -1;
    int fd = openat(dir, name, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK);
    if (fd < 0) { close(dir); return -1; }
    struct stat s;
    char data[MAX_CONFIG + 1];
    if (fstat(fd, &s) || !S_ISREG(s.st_mode) || s.st_uid ||
        (s.st_mode & 07777) != 0600 || s.st_size <= 0 || s.st_size > MAX_CONFIG) goto bad;
    size_t used = 0;
    while (used < (size_t)s.st_size) {
        ssize_t n = read(fd, data + used, (size_t)s.st_size - used);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) goto bad;
        used += (size_t)n;
    }
    if (memchr(data, 0, used) || data[used - 1] != '\n') goto bad;
    data[used] = 0;
    unsigned fields = 0;
    char *line = data;
    while (*line) {
        char *next = strchr(line, '\n');
        if (!next || next == line) goto bad;
        *next = 0;
        char *eq = strchr(line, '=');
        if (!eq || eq == line || !eq[1]) goto bad;
        *eq++ = 0;
        unsigned bit;
        unsigned long long n;
        if (!strcmp(line, "version")) {
            bit = 1; if (strcmp(eq, "1")) goto bad;
        } else if (!strcmp(line, "uid")) {
            bit = 2; if (number(eq, INT_MAX, &n) || !n) goto bad; c->uid = (uid_t)n;
        } else if (!strcmp(line, "socket")) {
            bit = 4; if (strlen(eq) >= sizeof(c->socket_name)) goto bad; strcpy(c->socket_name, eq);
        } else if (!strcmp(line, "token")) {
            bit = 8; if (!lowercase_hex(eq, 64)) goto bad; strcpy(c->token, eq);
        } else if (!strcmp(line, "bridge")) {
            bit = 16; if (strlen(eq) >= sizeof(c->bridge)) goto bad; strcpy(c->bridge, eq);
        } else if (!strcmp(line, "expires")) {
            bit = 32; if (number(eq, LLONG_MAX, &n)) goto bad; c->expires = (long long)n;
        } else goto bad;
        if (fields & bit) goto bad;
        fields |= bit;
        line = next + 1;
    }
    if (fields != 63) goto bad;
    char prefix[64];
    int prefix_n = snprintf(prefix, sizeof(prefix), "xcr.access.%lu.", (unsigned long)c->uid);
    if (prefix_n <= 0 || strncmp(c->socket_name, prefix, (size_t)prefix_n) ||
        !lowercase_hex(c->socket_name + prefix_n, 16)) goto bad;
    const char *config_slash = strrchr(path, '/');
    const char *bridge_slash = strrchr(c->bridge, '/');
    if (!bridge_slash || bridge_slash == c->bridge ||
        (size_t)(bridge_slash - c->bridge) != (size_t)(config_slash - path) ||
        strncmp(c->bridge, path, (size_t)(config_slash - path)) ||
        strcmp(bridge_slash + 1, "bridge")) goto bad;
    c->bridge_fd = openat(dir, "bridge", O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK);
    if (c->bridge_fd < 0 || fstat(c->bridge_fd, &s) || !S_ISREG(s.st_mode) ||
        s.st_uid || (s.st_mode & 0700) != 0700 || (s.st_mode & 07077)) goto bad;
    /* Restrict executable to ELF. Never introduce an interpreter/shell path. */
    unsigned char magic[4];
    if (pread(c->bridge_fd, magic, 4, 0) != 4 || memcmp(magic, "\177ELF", 4)) goto bad;
    time_t wall = time(NULL);
    int64_t now = monotonic_ms();
    if (wall < 0 || now < 0 || (!admin && (c->expires <= (long long)wall ||
        c->expires - (long long)wall > LEASE_SECONDS))) goto bad;
    /* Admin authenticates the original token/socket independently of current
     * wall time, including an expired lease or a backward clock correction. */
    c->deadline = admin ? now : now + (c->expires - (long long)wall) * 1000;
    close(fd); close(dir);
    return 0;
bad:
    close(fd); close(dir); close_fd(&c->bridge_fd);
    memset(c->token, 0, sizeof(c->token));
    return -1;
}

static socklen_t socket_address(const struct config *c, struct sockaddr_un *a) {
    memset(a, 0, sizeof(*a));
    a->sun_family = AF_UNIX;
    memcpy(a->sun_path + 1, c->socket_name, strlen(c->socket_name));
    return (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(c->socket_name));
}
static void response(int fd, const char *text) {
    /* Control responses are tiny. A new socket has no prior queued output. */
    (void)send(fd, text, strlen(text), MSG_NOSIGNAL | MSG_DONTWAIT);
}
static void success(int fd, const char *op, const struct config *c) {
    char line[160];
    snprintf(line, sizeof(line), "XCR1 OK %s uid=0 expires=%lld\n", op, c->expires);
    response(fd, line);
}
static int token_equal(const char *a, const char *b) {
    unsigned difference = 0;
    for (size_t i = 0; i < 64; i++) difference |= (unsigned char)a[i] ^ (unsigned char)b[i];
    return difference == 0;
}
static void stop_child(struct child *b) {
    if (b->pid > 0 && !b->stopped) {
        b->stopped = 1;
        b->input_used = 0;
        close_fd(&b->input);
        /* The unchanged bridge handles SIGTERM by requesting STOP and waiting
         * for telemetry. No escalation to SIGKILL and no fabricated idle. */
        if (!b->exited) (void)kill(b->pid, SIGTERM);
    }
}
static void disconnect_child(struct child *b) {
    close_fd(&b->client);
    b->output_used = 0;
    stop_child(b);
}
static int spawn_child(struct child *b, int client, const char *op, const struct config *c) {
    int input[2], output[2];
    if (pipe2(input, O_CLOEXEC)) return -1;
    if (pipe2(output, O_CLOEXEC)) { close(input[0]); close(input[1]); return -1; }
    pid_t parent = getpid();
    pid_t pid = fork();
    if (pid == 0) {
        /* A crashing broker still requests the bridge's normal STOP path. */
        (void)prctl(PR_SET_PDEATHSIG, SIGTERM);
        if (getppid() != parent) _exit(125);
        if (dup2(input[0], STDIN_FILENO) < 0 || dup2(output[1], STDOUT_FILENO) < 0 ||
            dup2(output[1], STDERR_FILENO) < 0) _exit(125);
        close(input[0]); close(input[1]); close(output[0]); close(output[1]);
        signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL); signal(SIGHUP, SIG_DFL);
        char *argv[] = { (char *)"bridge", (char *)(!strcmp(op, "SERVE") ? "--serve" : "--probe-fast"), NULL };
        char *env[] = { (char *)"PATH=/system/bin:/system/xbin:/usr/bin:/bin", (char *)"LANG=C", NULL };
        fexecve(c->bridge_fd, argv, env);
        _exit(126);
    }
    close(input[0]); close(output[1]);
    if (pid < 0) { close(input[1]); close(output[0]); return -1; }
    memset(b, 0, sizeof(*b));
    b->pid = pid; b->client = client; b->input = input[1]; b->output = output[0];
    b->output_newline = 1;
    (void)nonblocking(b->input); (void)nonblocking(b->output);
    return 0;
}

static void handle_request(struct request *r, struct child *b, const struct config *c, int *shutdown) {
    const char *error = "XCR1 ERROR INVALID\n";
    if (r->used < 71 || strncmp(r->line, "XCR1 ", 5) || r->line[69] != ' ') goto denied;
    const char *op = r->line + 70;
    if (strcmp(op, "ID") && strcmp(op, "SERVE") && strcmp(op, "PROBE") &&
        strcmp(op, "SHUTDOWN") && strcmp(op, "STATUS")) goto denied;
    int admin = !strcmp(op, "SHUTDOWN") || !strcmp(op, "STATUS");
    if ((admin ? r->uid != 0 : r->uid != c->uid) || !token_equal(r->line + 5, c->token)) {
        error = "XCR1 ERROR UNAUTHORIZED\n"; goto denied;
    }
    if (!strcmp(op, "SHUTDOWN")) {
        success(r->fd, op, c); close_fd(&r->fd); *shutdown = 1; stop_child(b); return;
    }
    if (*shutdown || monotonic_ms() >= c->deadline) { error = "XCR1 ERROR EXPIRED\n"; goto denied; }
    if (!strcmp(op, "ID") || !strcmp(op, "STATUS")) {
        success(r->fd, op, c); close_fd(&r->fd); return;
    }
    if (b->pid > 0) { error = "XCR1 ERROR BUSY\n"; goto denied; }
    if (spawn_child(b, r->fd, op, c)) { error = "XCR1 ERROR UNAVAILABLE\n"; goto denied; }
    success(r->fd, op, c);
    r->fd = -1;
    return;
denied:
    response(r->fd, error); close_fd(&r->fd);
}
static void read_request(struct request *r, struct child *b, const struct config *c, int *shutdown) {
    while (r->fd >= 0) {
        unsigned char byte;
        ssize_t n = recv(r->fd, &byte, 1, MSG_DONTWAIT);
        if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR)) return;
        if (n <= 0) { close_fd(&r->fd); return; }
        if (byte == '\n') {
            r->line[r->used] = 0;
            handle_request(r, b, c, shutdown);
            return;
        }
        if (byte < 32 || byte > 126 || r->used >= MAX_REQUEST) {
            response(r->fd, "XCR1 ERROR INVALID\n"); close_fd(&r->fd); return;
        }
        r->line[r->used++] = (char)byte;
    }
}
static void consume(unsigned char *buffer, size_t *used, size_t n) {
    *used -= n;
    memmove(buffer, buffer + n, *used);
}
static void relay(struct child *b) {
    if (b->client >= 0 && b->input >= 0 && !b->stopped) {
        ssize_t n = recv(b->client, b->to_child + b->input_used,
                         BUFFER_BYTES - b->input_used, MSG_DONTWAIT);
        if (n > 0) b->input_used += (size_t)n;
        else if (n == 0 || (errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR)) disconnect_child(b);
        if (b->input_used == BUFFER_BYTES) disconnect_child(b);
    } else if (b->client >= 0) {
        char byte;
        ssize_t n = recv(b->client, &byte, 1, MSG_PEEK | MSG_DONTWAIT);
        if (!n || (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR)) disconnect_child(b);
    }
    if (b->input >= 0 && b->input_used) {
        ssize_t n = write(b->input, b->to_child, b->input_used);
        if (n > 0) consume(b->to_child, &b->input_used, (size_t)n);
        else if (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR) stop_child(b);
    }
    if (b->output >= 0) {
        unsigned char discarded[4096];
        void *dst = b->client < 0 ? discarded : b->to_client + b->output_used;
        size_t capacity = b->client < 0 ? sizeof(discarded) : BUFFER_BYTES - b->output_used;
        if (!capacity) { disconnect_child(b); dst = discarded; capacity = sizeof(discarded); }
        ssize_t n = read(b->output, dst, capacity);
        if (n > 0) {
            b->output_newline = ((unsigned char *)dst)[n - 1] == '\n';
            if (b->client >= 0) b->output_used += (size_t)n;
        }
        else if (!n || (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR)) {
            b->output_eof = 1; close_fd(&b->output);
        }
    }
    if (!b->exited) {
        int status;
        pid_t got = waitpid(b->pid, &status, WNOHANG);
        if (got == b->pid) {
            b->exited = 1;
            b->code = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
            close_fd(&b->input);
        }
    }
    if (b->exited && b->output_eof && !b->exit_queued) {
        char line[96];
        int n = snprintf(line, sizeof(line), "%s{\"type\":\"access-exit\",\"code\":%d}\n",
                         b->output_newline ? "" : "\n", b->code);
        if (b->client < 0 || b->output_used + (size_t)n <= BUFFER_BYTES) {
            if (b->client >= 0) { memcpy(b->to_client + b->output_used, line, (size_t)n); b->output_used += (size_t)n; }
            b->exit_queued = 1;
        }
    }
    if (b->client >= 0 && b->output_used) {
        ssize_t n = send(b->client, b->to_client, b->output_used, MSG_NOSIGNAL | MSG_DONTWAIT);
        if (n > 0) consume(b->to_client, &b->output_used, (size_t)n);
        else if (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR) disconnect_child(b);
    }
    if (b->exit_queued && !b->output_used) {
        close_fd(&b->client); close_fd(&b->input); close_fd(&b->output); b->pid = 0;
    }
}

static int run_broker(const struct config *c, int ready_fd) {
    int listener = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    struct sockaddr_un address;
    socklen_t length = socket_address(c, &address);
    if (listener < 0 || bind(listener, (struct sockaddr *)&address, length) || listen(listener, MAX_CLIENTS)) {
        if (listener >= 0) close(listener);
        return 1;
    }
    struct request requests[MAX_CLIENTS];
    for (int i = 0; i < MAX_CLIENTS; i++) requests[i].fd = -1;
    struct child bridge;
    memset(&bridge, 0, sizeof(bridge));
    bridge.client = bridge.input = bridge.output = -1;
    signal(SIGTERM, on_signal); signal(SIGINT, on_signal); signal(SIGHUP, on_signal); signal(SIGPIPE, SIG_IGN);
    char ready[200];
    int ready_n = snprintf(ready, sizeof(ready), "XCR_ACCESS_READY pid=%ld socket=%s expires=%lld\n",
                           (long)getpid(), c->socket_name, c->expires);
    if (write(ready_fd, ready, (size_t)ready_n) != ready_n) { close(listener); return 1; }
    if (ready_fd != STDOUT_FILENO) close(ready_fd);
    int shutdown = 0;
    for (;;) {
        int64_t now = monotonic_ms();
        if (now < 0 || interrupted || now >= c->deadline) shutdown = 1;
        if (shutdown) {
            close_fd(&listener);
            stop_child(&bridge);
            for (int i = 0; i < MAX_CLIENTS; i++) if (requests[i].fd >= 0) {
                response(requests[i].fd, "XCR1 ERROR EXPIRED\n"); close_fd(&requests[i].fd);
            }
            if (!bridge.pid) break;
        }
        struct pollfd fds[MAX_CLIENTS + 4];
        size_t count = 0;
        fds[count++] = (struct pollfd){ listener, POLLIN, 0 };
        for (int i = 0; i < MAX_CLIENTS; i++) fds[count++] = (struct pollfd){ requests[i].fd, POLLIN, 0 };
        fds[count++] = (struct pollfd){ bridge.client, (short)(POLLIN | (bridge.output_used ? POLLOUT : 0)), 0 };
        fds[count++] = (struct pollfd){ bridge.output, POLLIN, 0 };
        fds[count++] = (struct pollfd){ bridge.input, bridge.input_used ? POLLOUT : 0, 0 };
        int timeout = 50;
        if (!shutdown && c->deadline - now < timeout) timeout = (int)(c->deadline - now);
        if (timeout < 0) timeout = 0;
        int result = poll(fds, count, timeout);
        if (result < 0 && errno != EINTR) { shutdown = 1; continue; }
        if (listener >= 0 && fds[0].revents & POLLIN) {
            int client = accept4(listener, NULL, NULL, SOCK_CLOEXEC | SOCK_NONBLOCK);
            if (client >= 0) {
                struct ucred cred;
                socklen_t size = sizeof(cred);
                int slot = -1;
                for (int i = 0; i < MAX_CLIENTS; i++) if (requests[i].fd < 0) { slot = i; break; }
                if (slot < 0 || getsockopt(client, SOL_SOCKET, SO_PEERCRED, &cred, &size) || size != sizeof(cred)) {
                    response(client, "XCR1 ERROR UNAVAILABLE\n"); close(client);
                } else {
                    requests[slot] = (struct request){ .fd=client, .uid=cred.uid, .used=0, .deadline=monotonic_ms()+REQUEST_MS };
                }
            }
        }
        for (int i = 0; i < MAX_CLIENTS; i++) if (requests[i].fd >= 0) {
            if (monotonic_ms() >= requests[i].deadline) {
                response(requests[i].fd, "XCR1 ERROR INVALID\n"); close_fd(&requests[i].fd);
            } else read_request(&requests[i], &bridge, c, &shutdown);
        }
        if (bridge.pid) relay(&bridge);
    }
    return 0;
}

static int admin_request(const struct config *c, const char *op) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return 1;
    struct timeval timeout = { .tv_sec=3, .tv_usec=0 };
    (void)setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    (void)setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    struct sockaddr_un address;
    socklen_t length = socket_address(c, &address);
    if (connect(fd, (struct sockaddr *)&address, length)) {
        int absent = errno == ECONNREFUSED || errno == ENOENT;
        close(fd);
        if (absent && !strcmp(op, "SHUTDOWN")) {
            printf("XCR_ACCESS_ALREADY_STOPPED socket=%s\n", c->socket_name);
            return 0;
        }
        return 1;
    }
    struct ucred peer;
    socklen_t size = sizeof(peer);
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &peer, &size) || size != sizeof(peer) || peer.uid) { close(fd); return 1; }
    char request[100];
    int n = snprintf(request, sizeof(request), "XCR1 %s %s\n", c->token, op);
    if (send(fd, request, (size_t)n, MSG_NOSIGNAL) != n) { close(fd); return 1; }
    memset(request, 0, sizeof(request));
    char received[200];
    size_t used = 0;
    while (used < sizeof(received) - 1) {
        ssize_t got = recv(fd, received + used, 1, 0);
        if (got <= 0) break;
        if (received[used++] == '\n') break;
    }
    close(fd);
    received[used] = 0;
    char expected[160];
    snprintf(expected, sizeof(expected), "XCR1 OK %s uid=0 expires=%lld\n", op, c->expires);
    if (strcmp(received, expected)) return 1;
    fputs(received, stdout);
    return 0;
}

int main(int argc, char **argv) {
    if (getuid() || geteuid()) { fputs("Factory access requires UID 0.\n", stderr); return 1; }
    const char *path = NULL;
    int detach = 0, admin = 0;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--config") && !path && i + 1 < argc) path = argv[++i];
        else if (!strcmp(argv[i], "--detach") && !detach) detach = 1;
        else if (!strcmp(argv[i], "--stop") && !admin) admin = 1;
        else if (!strcmp(argv[i], "--status") && !admin) admin = 2;
        else { fputs("Invalid factory access arguments.\n", stderr); return 2; }
    }
    if (!path || (admin && detach)) { fputs("Use --config PATH with optional --detach, --status or --stop.\n", stderr); return 2; }
    struct config config;
    if (load_config(path, &config, admin)) { fputs("Invalid or unsafe factory access configuration.\n", stderr); return 1; }
    umask(077);
    int result;
    if (admin) result = admin_request(&config, admin == 1 ? "SHUTDOWN" : "STATUS");
    else if (!detach) result = run_broker(&config, STDOUT_FILENO);
    else {
        int ready[2];
        if (pipe2(ready, O_CLOEXEC)) { close(config.bridge_fd); return 1; }
        pid_t pid = fork();
        if (pid == 0) {
            close(ready[0]);
            if (setsid() < 0) _exit(1);
            int null = open("/dev/null", O_RDWR | O_CLOEXEC);
            if (null < 0 || dup2(null, 0) < 0 || dup2(null, 1) < 0 || dup2(null, 2) < 0) _exit(1);
            if (null > 2) close(null);
            result = run_broker(&config, ready[1]);
            close(config.bridge_fd);
            memset(config.token, 0, sizeof(config.token));
            _exit(result);
        }
        close(ready[1]);
        result = 1;
        if (pid > 0) {
            struct pollfd p = { ready[0], POLLIN, 0 };
            if (poll(&p, 1, 3000) > 0) {
                char line[200];
                ssize_t n = read(ready[0], line, sizeof(line));
                if (n > 0 && (size_t)n < sizeof(line) && line[n-1] == '\n' &&
                    !strncmp(line, "XCR_ACCESS_READY ", 17)) {
                    if (write(STDOUT_FILENO, line, (size_t)n) == n) result = 0;
                }
            }
            if (result) { (void)kill(pid, SIGTERM); (void)waitpid(pid, NULL, 0); }
        }
        close(ready[0]);
    }
    close(config.bridge_fd);
    memset(config.token, 0, sizeof(config.token));
    if (result) fputs("Factory access operation failed.\n", stderr);
    return result;
}
