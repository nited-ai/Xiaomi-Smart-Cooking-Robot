"""Real Linux socket/process tests; synthetic bridge, never opens a device UART.

Run as root: python3 tools/tests/test_factory_broker.py
The build and all executable fixtures live in a private temporary Linux directory.
"""
import array
import os
from pathlib import Path
import select
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "machine/src/main/cpp/factory_access.c"
TOKEN = "12" * 32
APP_UID = 65534
FAKE = r'''
#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
static volatile sig_atomic_t stopped;
static void stop(int s) { (void)s; stopped=1; }
int main(int argc,char **argv) {
 setvbuf(stdout,NULL,_IONBF,0);
 signal(SIGTERM,stop); signal(SIGINT,stop); signal(SIGHUP,stop);
 if(argc!=2)return 90;
#ifndef READY_SUFFIX
#define READY_SUFFIX "\n"
#endif
 printf("FAKE_BRIDGE %s uid=%ld" READY_SUFFIX,argv[1],(long)getuid());
 if(!strcmp(argv[1],"--probe-fast"))return 7;
 char c;
 while(!stopped) {
  ssize_t n=read(0,&c,1);
  if(n<=0)break;
  if(c=='!') { puts("FAKE_GRACEFUL_STOP"); return 0; }
  if(write(1,&c,1)<0)break;
 }
 puts("FAKE_GRACEFUL_STOP");return 0;
}
'''
BACKWARD_TIME = r'''
#include <time.h>
time_t time(time_t *out) {
 static int calls;
 struct timespec t;
 clock_gettime(CLOCK_REALTIME,&t);
 time_t value=t.tv_sec-(calls++ ? 3600 : 0);
 if(out)*out=value;
 return value;
}
'''


def connect_as(name, uid=APP_UID):
    """Connect under a real peer UID and return that connected FD to the test."""
    parent, child = socket.socketpair()
    pid = os.fork()
    if pid == 0:
        try:
            parent.close()
            os.setgroups([])
            os.setgid(uid)
            os.setuid(uid)
            s = socket.socket(socket.AF_UNIX)
            s.connect("\0" + name)
            child.sendmsg([b"F"], [(socket.SOL_SOCKET, socket.SCM_RIGHTS,
                                    array.array("i", [s.fileno()]))])
            os._exit(0)
        except Exception:
            os._exit(90)
    child.close()
    _, ancillary, _, _ = parent.recvmsg(1, socket.CMSG_SPACE(4))
    _, status = os.waitpid(pid, 0)
    parent.close()
    if status or not ancillary:
        raise RuntimeError("test client could not connect")
    fds = array.array("i")
    fds.frombytes(ancillary[0][2][:4])
    s = socket.socket(fileno=fds[0])
    s.settimeout(4)
    return s


def receive_until(s, marker=b"\n", timeout=4):
    deadline = time.monotonic() + timeout
    data = b""
    while marker not in data:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise AssertionError("timed out receiving " + repr(marker) + ": " + repr(data))
        s.settimeout(remaining)
        block = s.recv(65536)
        if not block:
            break
        data += block
    return data


@unittest.skipUnless(os.name == "posix" and os.geteuid() == 0,
                     "requires Linux root for real SO_PEERCRED UID tests")
class BrokerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not SOURCE.exists():
            raise AssertionError("factory broker implementation does not exist")
        cls.build = Path(tempfile.mkdtemp(prefix="xcr-broker-build-", dir="/root"))
        cls.bin = cls.build / "broker"
        subprocess.run(["gcc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2",
                        str(SOURCE), "-o", str(cls.bin)], check=True)
        fake = cls.build / "fake.c"
        fake.write_text(FAKE)
        cls.fake = cls.build / "fake"
        subprocess.run(["gcc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2",
                        str(fake), "-o", str(cls.fake)], check=True)
        cls.fake_no_newline = cls.build / "fake-no-newline"
        subprocess.run(["gcc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2", '-DREADY_SUFFIX=""',
                        str(fake), "-o", str(cls.fake_no_newline)], check=True)
        wall = cls.build / "wall.c"
        wall.write_text(BACKWARD_TIME)
        cls.wall = cls.build / "wall.so"
        subprocess.run(["gcc", "-D_POSIX_C_SOURCE=200809L", "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror",
                        str(wall), "-o", str(cls.wall)], check=True)

    @classmethod
    def tearDownClass(cls):
        if hasattr(cls, "build"):
            shutil.rmtree(cls.build)

    def setUp(self):
        self.home = Path(tempfile.mkdtemp(prefix="session-", dir=self.build))
        self.bridge = self.home / "bridge"
        shutil.copyfile(self.fake, self.bridge)
        self.bridge.chmod(0o700)
        self.config = self.home / "broker.conf"
        self.name = "xcr.access.65534." + os.urandom(8).hex()
        self.expires = int(time.time()) + 60
        self.processes = []
        self.sockets = []
        self.write_config()

    def tearDown(self):
        for s in self.sockets:
            s.close()
        for p in self.processes:
            if p.poll() is None:
                p.terminate()
            try:
                p.communicate(timeout=4)
            except subprocess.TimeoutExpired:
                p.kill()
                p.communicate()
        shutil.rmtree(self.home)

    def write_config(self, suffix="", **updates):
        values = dict(version="1", uid=str(APP_UID), socket=self.name, token=TOKEN,
                      bridge=str(self.bridge), expires=str(self.expires))
        values.update(updates)
        self.config.write_text("".join(k + "=" + v + "\n" for k, v in values.items()) + suffix)
        self.config.chmod(0o600)

    def start(self, env=None):
        p = subprocess.Popen([str(self.bin), "--config", str(self.config)],
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
        self.processes.append(p)
        self.assertTrue(select.select([p.stdout], [], [], 3)[0], "no startup result")
        line = p.stdout.readline().decode()
        self.assertRegex(line, r"^XCR_ACCESS_READY pid=\d+ socket=" + self.name + r" expires=\d+\n$")
        self.assertNotIn(TOKEN, line)
        return p

    def client(self, operation="ID", uid=APP_UID, token=TOKEN, raw=None):
        s = connect_as(self.name, uid)
        self.sockets.append(s)
        s.sendall(raw if raw is not None else ("XCR1 " + token + " " + operation + "\n").encode())
        return s

    def rejected_start(self):
        p = subprocess.run([str(self.bin), "--config", str(self.config)],
                           capture_output=True, timeout=3)
        self.assertNotEqual(p.returncode, 0)
        self.assertNotIn(TOKEN.encode(), p.stdout + p.stderr)
        self.assertNotIn(b"XCR_ACCESS_READY", p.stdout)

    def test_identity_never_launches_bridge_and_peer_uid_is_root(self):
        self.start()
        s = self.client()
        pid, uid, gid = __import__("struct").unpack("3i", s.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
        self.assertEqual(uid, 0)
        self.assertGreater(pid, 0)
        result = receive_until(s, b"\0")
        self.assertEqual(result, f"XCR1 OK ID uid=0 expires={self.expires}\n".encode())
        self.assertNotIn(b"FAKE_BRIDGE", result)

    def test_wrong_token_and_wrong_uid_are_denied(self):
        self.start()
        self.assertEqual(receive_until(self.client(token="34" * 32)), b"XCR1 ERROR UNAUTHORIZED\n")
        self.assertEqual(receive_until(self.client(uid=0)), b"XCR1 ERROR UNAUTHORIZED\n")

    def test_status_is_root_only_and_does_not_launch_bridge(self):
        self.start()
        self.assertEqual(receive_until(self.client("STATUS")), b"XCR1 ERROR UNAUTHORIZED\n")
        result = subprocess.run([str(self.bin), "--status", "--config", str(self.config)],
                                capture_output=True, timeout=3)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, f"XCR1 OK STATUS uid=0 expires={self.expires}\n".encode())
        self.assertNotIn(TOKEN.encode(), result.stdout + result.stderr)

    def test_detached_ready_reports_live_child_and_root_admin_stops_it(self):
        result = subprocess.run([str(self.bin), "--detach", "--config", str(self.config)],
                                capture_output=True, timeout=3)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertRegex(result.stdout.decode(), r"^XCR_ACCESS_READY pid=\d+ socket=" + self.name)
        try:
            self.assertIn(b"XCR1 OK ID", receive_until(self.client()))
        finally:
            subprocess.run([str(self.bin), "--stop", "--config", str(self.config)],
                           capture_output=True, timeout=3, check=True)

    def test_probe_uses_only_fixed_argument_and_reports_exit(self):
        self.start()
        result = receive_until(self.client("PROBE"), b"\"code\":7}")
        self.assertIn(b"XCR1 OK PROBE uid=0", result)
        self.assertIn(b"FAKE_BRIDGE --probe-fast uid=0\n", result)
        self.assertIn(b'{"type":"access-exit","code":7}\n', result)

    def test_exit_record_is_on_its_own_line_after_unterminated_child_output(self):
        shutil.copyfile(self.fake_no_newline, self.bridge)
        self.start()
        result = receive_until(self.client("PROBE"), b"\"code\":7}")
        self.assertIn(b'uid=0\n{"type":"access-exit","code":7}\n', result)

    def test_one_child_busy_identity_and_graceful_completion(self):
        self.start()
        active = self.client("SERVE")
        self.assertIn(b"FAKE_BRIDGE --serve uid=0", receive_until(active, b"FAKE_BRIDGE"))
        self.assertEqual(receive_until(self.client("SERVE")), b"XCR1 ERROR BUSY\n")
        self.assertIn(b"XCR1 OK ID", receive_until(self.client()))
        active.sendall(b"PING\n!")
        result = receive_until(active, b"\"code\":0}")
        self.assertIn(b"PING\n", result)
        self.assertIn(b"FAKE_GRACEFUL_STOP", result)
        self.assertIn(b'"code":0', result)

    def test_disconnect_releases_child_and_broker_accepts_next(self):
        self.start()
        active = self.client("SERVE")
        receive_until(active, b"FAKE_BRIDGE")
        active.close()
        for _ in range(40):
            result = receive_until(self.client("PROBE"), b"\n")
            if b"BUSY" not in result:
                self.assertIn(b"XCR1 OK PROBE", result)
                break
            time.sleep(0.05)
        else:
            self.fail("disconnected child did not exit gracefully")

    def test_shutdown_is_root_only_and_preserves_child_stop(self):
        p = self.start()
        active = self.client("SERVE")
        receive_until(active, b"FAKE_BRIDGE")
        self.assertEqual(receive_until(self.client("SHUTDOWN")), b"XCR1 ERROR UNAUTHORIZED\n")
        result = subprocess.run([str(self.bin), "--stop", "--config", str(self.config)],
                                capture_output=True, timeout=3)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn(TOKEN.encode(), result.stdout + result.stderr)
        output = receive_until(active, b"\"code\":0}")
        self.assertIn(b"FAKE_GRACEFUL_STOP", output)
        p.wait(timeout=3)

    def test_lease_expiry_stops_child_and_exits(self):
        self.expires = int(time.time()) + 2
        self.write_config()
        p = self.start()
        active = self.client("SERVE")
        result = receive_until(active, b"\"code\":0}", timeout=5)
        self.assertIn(b"FAKE_GRACEFUL_STOP", result)
        p.wait(timeout=3)

    def test_lease_still_expires_when_wall_time_moves_backwards(self):
        self.expires = int(time.time()) + 2
        self.write_config()
        p = self.start(env=dict(os.environ, LD_PRELOAD=str(self.wall)))
        active = self.client("SERVE")
        self.assertIn(b"FAKE_GRACEFUL_STOP", receive_until(active, b"\"code\":0}", timeout=5))
        p.wait(timeout=3)

    def test_stop_after_lease_expiry_or_absent_listener_is_idempotent(self):
        self.expires = int(time.time()) - 60
        self.write_config()
        result = subprocess.run([str(self.bin), "--stop", "--config", str(self.config)],
                                capture_output=True, timeout=3)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, ("XCR_ACCESS_ALREADY_STOPPED socket=" + self.name + "\n").encode())

    def test_root_stop_still_loads_config_after_large_backward_wall_jump(self):
        self.write_config(expires=str(int(time.time()) + 999999))
        result = subprocess.run([str(self.bin), "--stop", "--config", str(self.config)],
                                capture_output=True, timeout=3)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_validated_bridge_fd_cannot_be_replaced_by_path_rename(self):
        self.start()
        replacement = self.home / "replacement"
        shutil.copyfile(self.bin, replacement)
        replacement.chmod(0o700)
        os.replace(replacement, self.bridge)
        result = receive_until(self.client("PROBE"), b"\"code\":7}")
        self.assertIn(b"FAKE_BRIDGE --probe-fast uid=0", result)

    def test_duplicate_broker_cannot_claim_readiness(self):
        self.start()
        self.rejected_start()

    def test_nonregular_config_cannot_hang_startup(self):
        self.config.unlink()
        os.mkfifo(self.config, 0o600)
        self.rejected_start()

    def test_unprivileged_process_cannot_start_or_stop_broker(self):
        public_bin = Path("/tmp") / ("xcr-broker-public-" + os.urandom(8).hex())
        shutil.copyfile(self.bin, public_bin)
        public_bin.chmod(0o755)
        try:
            for args in [[], ["--stop"]]:
                result = subprocess.run([str(public_bin), *args, "--config", str(self.config)],
                                        preexec_fn=lambda: os.setuid(APP_UID), capture_output=True, timeout=3)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(b"requires UID 0", result.stderr)
        finally:
            public_bin.unlink()

    def test_invalid_and_oversized_requests_do_not_execute(self):
        self.start()
        for raw in [b"INVALID\n", ("XCR1 " + TOKEN + " SHELL id\n").encode(),
                    ("XCR1 " + TOKEN + " SERVE /bin/sh\n").encode(),
                    b"X" * 257 + b"\n", b"XCR1\x00bad\n"]:
            self.assertEqual(receive_until(self.client(raw=raw)), b"XCR1 ERROR INVALID\n")

    def test_config_rejects_duplicate_unknown_and_invalid_fields(self):
        for suffix in ["uid=65534\n", "unknown=1\n", "\n", "token=\n"]:
            self.write_config(suffix)
            self.rejected_start()
        for updates in [dict(uid="0"), dict(uid="-1"), dict(uid="65534x"),
                        dict(expires=str(int(time.time()) - 1)),
                        dict(expires=str(int(time.time()) + 14402)),
                        dict(token="AB" * 32), dict(socket="/tmp/socket"),
                        dict(bridge="/bin/sh"), dict(version="2")]:
            self.write_config(**updates)
            self.rejected_start()

    def test_permissions_and_symlinks_are_rejected(self):
        self.config.chmod(0o644)
        self.rejected_start()
        self.write_config()
        self.bridge.chmod(0o777)
        self.rejected_start()
        self.bridge.chmod(0o700)
        self.home.chmod(0o755)
        self.rejected_start()
        self.home.chmod(0o700)
        original = self.home / "original"
        self.config.rename(original)
        self.config.symlink_to(original)
        self.rejected_start()

    def test_symlinked_bridge_and_config_ancestor_are_rejected(self):
        self.bridge.unlink()
        self.bridge.symlink_to(self.fake)
        self.rejected_start()
        self.bridge.unlink()
        shutil.copyfile(self.fake, self.bridge)
        self.bridge.chmod(0o700)
        alias = self.build / (self.home.name + "-alias")
        alias.symlink_to(self.home, target_is_directory=True)
        try:
            original = self.config
            self.config = alias / "broker.conf"
            self.rejected_start()
            self.config = original
        finally:
            alias.unlink()

    def test_partial_handshake_does_not_block_other_clients(self):
        self.start()
        stalled = connect_as(self.name)
        self.sockets.append(stalled)
        stalled.sendall(b"X")
        self.assertIn(b"XCR1 OK ID", receive_until(self.client()))


if __name__ == "__main__":
    unittest.main(verbosity=2)
