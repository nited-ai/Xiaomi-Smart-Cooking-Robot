/*
 * C3 app-retaining standby. Separate from the cooking controller.
 * Read-only preflight: MCU version and three idle-status samples.
 * Do not add the OEM 0x44 setter here: v12 later gates GPIOs and disables
 * UART peripherals. Software-only recovery after that is not established.
 * No cooking, motor, heating, factory, firmware or arbitrary-frame interface.
 * Transport framing/CRC and exclusive-port checks match the product bridge.
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <poll.h>
#include <signal.h>
#include <dirent.h>
#include <sys/ioctl.h>
#include <sys/file.h>
#include <time.h>
#include <sys/wait.h>
#include <sys/system_properties.h>

static int fd=-1, sequence;
static volatile sig_atomic_t interrupted;
static unsigned char rx[1024];
static size_t used;
static int64_t millis(void) {
    struct timespec t; clock_gettime(CLOCK_BOOTTIME,&t);
    return (int64_t)t.tv_sec*1000+t.tv_nsec/1000000;
}
static int64_t suspended_millis(void) {
    struct timespec t; clock_gettime(CLOCK_MONOTONIC,&t);
    return millis()-((int64_t)t.tv_sec*1000+t.tv_nsec/1000000);
}
static void signal_handler(int sig) { (void)sig; interrupted=1; }
static uint16_t crc16(const unsigned char *p,size_t n) {
    uint16_t c=0;
    for(size_t i=0;i<n;i++){
        c^=(uint16_t)p[i]<<8;
        for(int j=0;j<8;j++) c=(c&0x8000)?(c<<1)^0x1021:c<<1;
    }
    return c;
}
static int other_owner(void) {
    DIR *d=opendir("/proc"); if(!d) return -1;
    struct dirent *e; int owner=0;
    while((e=readdir(d))){
        int pid=atoi(e->d_name); if(pid<=0||pid==getpid()) continue;
        char path[128]; snprintf(path,sizeof(path),"/proc/%d/fd",pid);
        DIR *f=opendir(path); if(!f) continue;
        struct dirent *x;
        while((x=readdir(f))){
            if(x->d_name[0]=='.') continue;
            char link[512],target[256];
            snprintf(link,sizeof(link),"%s/%s",path,x->d_name);
            ssize_t n=readlink(link,target,sizeof(target)-1);
            if(n<0) continue;
            target[n]=0;
            if(!strcmp(target,"/dev/ttyS2")){ owner=pid; break; }
        }
        closedir(f); if(owner) break;
    }
    closedir(d); return owner;
}
static int exchange(int op,int value,int response,unsigned char *data,size_t want) {
    /* Only read requests are permitted. */
    if((op!=0x20&&op!=0x22)||value!=0) return -1;
    unsigned char b[8]={0x5a,0,(unsigned char)op,0,0,0,0,0};
    int n=0; b[1]=(unsigned char)(sequence=(sequence+1)%255);
    uint16_t c=crc16(b,(size_t)n+5); b[n+5]=c>>8; b[n+6]=c&255;
    size_t sent=0,full=(size_t)n+7; int64_t end=millis()+1500;
    while(sent<full&&millis()<end){
        ssize_t k=write(fd,b+sent,full-sent);
        if(k>0) sent+=(size_t)k;
        else if(errno!=EINTR&&errno!=EAGAIN) return -1;
        else { struct pollfd p={fd,POLLOUT,0}; poll(&p,1,10); }
    }
    if(sent!=full) return -1;
    while(millis()<end){
        while(used>=7){
            size_t payload=(size_t)rx[3]+((size_t)rx[4]<<8),len=payload+7;
            if(rx[0]!=0x5a||payload>256){ memmove(rx,rx+1,--used); continue; }
            if(used<len) break;
            uint16_t expected=(uint16_t)rx[len-2]|((uint16_t)rx[len-1]<<8);
            if(crc16(rx,len-2)!=expected){ memmove(rx,rx+1,--used); continue; }
            int match=rx[1]==sequence;
            int ok=match&&rx[2]==response&&payload==want;
            if(ok&&want) memcpy(data,rx+5,want);
            memmove(rx,rx+len,used-len); used-=len;
            if(match) return ok?0:-1;
        }
        struct pollfd p={fd,POLLIN,0}; poll(&p,1,20);
        if(p.revents&POLLIN){
            if(used==sizeof(rx)) return -1;
            ssize_t k=read(fd,rx+used,sizeof(rx)-used);
            if(k>0) used+=(size_t)k;
        }
    }
    return -1;
}
static int status(unsigned char *d) {
    if(exchange(0x20,0,0x21,d,24)) return -1;
    printf("STATUS state=%u ih=%u gear=%u temp=%u motor=%u speed=%u power=%u error=%u\n",
        d[0],d[2],d[3],d[4],d[6],d[12]|d[13]<<8,d[10],d[11]);
    return 0;
}
static int idle(const unsigned char *d) {
    return (d[0]==0||d[0]==11)&&!d[2]&&!d[3]&&!d[6]&&!d[11]&&!d[12]&&!d[13]&&d[4]<50;
}
#define IO_RAIL "/sys/class/regulator/regulator.7/"
#define IO_CONSUMER "/sys/bus/platform/devices/reg-virt-consumer.5/"
static int file_equals(const char *path,const char *expected) {
    FILE *f=fopen(path,"r");if(!f)return 0;
    char value[80]={0};int ok=fgets(value,sizeof(value),f)!=NULL;fclose(f);
    value[strcspn(value,"\r\n")]=0;return ok&&!strcmp(value,expected);
}
static int write_value(const char *path,const char *value) {
    int target=open(path,O_WRONLY|O_CLOEXEC);if(target<0)return -1;
    size_t n=strlen(value);ssize_t wrote=write(target,value,n);close(target);
    return wrote==(ssize_t)n?0:-1;
}
static int read_number(const char *path) {
    FILE *f=fopen(path,"r");if(!f)return -1;
    int value=-1;if(fscanf(f,"%d",&value)!=1)value=-1;fclose(f);return value;
}
static int io_rail_ready(void) {
    // Known board only. Never alter the operating voltage or borrow an
    // existing virtual-consumer request. Retain its own I/O supply instead
    // of depending on an attached UART adapter during RAM sleep.
    return file_equals(IO_RAIL "name","axp806-dcdce")&&
        file_equals(IO_RAIL "microvolts","3300000")&&file_equals(IO_RAIL "state","enabled")&&
        file_equals(IO_CONSUMER "min_microvolts","0")&&file_equals(IO_CONSUMER "max_microvolts","0")&&
        file_equals(IO_CONSUMER "min_microamps","0")&&file_equals(IO_CONSUMER "max_microamps","0");
}
static int command(const char *tool,const char *arg1,const char *arg2,const char *arg3) {
    pid_t pid=fork(); if(pid<0) return -1;
    if(!pid){execl(tool,tool,arg1,arg2,arg3,(char*)NULL);_exit(127);}
    int state=0; int64_t end=millis()+10000;
    for(;;){
        pid_t done=waitpid(pid,&state,WNOHANG);
        if(done==pid)break;
        if(done<0&&errno!=EINTR)return -1;
        if(millis()>end){kill(pid,SIGKILL);waitpid(pid,&state,0);return -1;}
        usleep(100000);
    }
    return WIFEXITED(state)&&WEXITSTATUS(state)==0?0:-1;
}
static int radio_setting(const char *key) {
    // Only these two fixed settings are queried, never caller-controlled input.
    const char *off=!strcmp(key,"wifi_on")?
        "test \"$(/system/bin/settings get global wifi_on)\" = 0":
        "test \"$(/system/bin/settings get global bluetooth_on)\" = 0";
    const char *on=!strcmp(key,"wifi_on")?
        "test \"$(/system/bin/settings get global wifi_on)\" = 1":
        "test \"$(/system/bin/settings get global bluetooth_on)\" = 1";
    if(!command("/system/bin/sh","-c",off,NULL))return 0;
    if(!command("/system/bin/sh","-c",on,NULL))return 1;
    return -1;
}
static int radio(const char *which,int on){return command("/system/bin/svc",which,on?"enable":"disable",NULL);}
static int wait_wifi(void) {
    // Preserve Android's AP-rejection history while it selects another BSSID.
    // Toggling Wi-Fi here restarts those retries and delays reconnection.
    for(int i=0;i<120;i++){
        if(!command("/system/bin/sh","-c","test \"$(cat /sys/class/net/wlan0/carrier 2>/dev/null)\" = 1 && /system/bin/ip -4 addr show wlan0 2>/dev/null | /system/bin/grep 'inet ' >/dev/null",NULL))return 0;
        sleep(1);
    }
    return -1;
}
int main(int argc,char **argv) {
    setvbuf(stdout,NULL,_IOLBF,0);
    if(argc!=2||(strcmp(argv[1],"--check")&&strcmp(argv[1],"--suspend"))) return 2;
    int suspend_requested=!strcmp(argv[1],"--suspend"),wifi=-1,bt=-1,prepared=0,rail_requested=0;
    char serial[PROP_VALUE_MAX]={0};__system_property_get("ro.serialno",serial);
    if(strcmp(serial,"619246206")) {puts("WRONG_DEVICE");return 3;}
    if(getuid()!=0) return 3;
    int lock=open("/data/local/tmp/kitchenos-control.lock",O_RDWR|O_CREAT|O_CLOEXEC,0600);
    if(lock<0||flock(lock,LOCK_EX|LOCK_NB)){puts("CONTROL_BUSY");if(lock>=0)close(lock);return 4;}
    int owner=other_owner();
    if(owner){ printf("ABORT other UART owner %d\n",owner); close(lock); return 5; }
    fd=open("/dev/ttyS2",O_RDWR|O_NOCTTY|O_NONBLOCK|O_CLOEXEC);
    struct termios prior,t;
    if(fd<0||ioctl(fd,TIOCEXCL)<0||tcgetattr(fd,&prior)){
        if(fd>=0){ ioctl(fd,TIOCNXCL); close(fd); }
        close(lock); return 6;
    }
    t=prior; cfmakeraw(&t); cfsetispeed(&t,B115200); cfsetospeed(&t,B115200);
    t.c_cflag|=CLOCAL|CREAD; t.c_cflag&=~(CSTOPB|PARENB|CRTSCTS);
    t.c_cflag=(t.c_cflag&~CSIZE)|CS8; t.c_cc[VMIN]=t.c_cc[VTIME]=0;
    int result=1;
    if(tcsetattr(fd,TCSANOW,&t)) goto finish;
    signal(SIGTERM,signal_handler); signal(SIGINT,signal_handler);
    signal(SIGHUP,SIG_IGN); signal(SIGPIPE,SIG_IGN);
    unsigned char info[20],d[24];
    if(exchange(0x22,0,0x23,info,20)||info[0]!=12) goto finish;
    printf("MCU_VERSION %u\n",info[0]);
    for(int i=0;i<3;i++){
        if(status(d)||!idle(d)||interrupted) goto finish;
        usleep(100000);
    }
    if(!io_rail_ready()){puts("IO_RAIL_NOT_READY");goto finish;}
    if(suspend_requested){
        if(command("/system/bin/sh","-c","/system/bin/dumpsys activity activities | /system/bin/grep 'mResumedActivity.*com.kitchenos.connect/.ui.ApplianceActivity' >/dev/null",NULL))goto finish;
        wifi=radio_setting("wifi_on");bt=radio_setting("bluetooth_on");
        if(wifi<0||bt<0)goto finish;
        int rail_users=read_number(IO_RAIL "num_users");
        if(rail_users<1)goto finish;
        rail_requested=1;
        if(write_value(IO_CONSUMER "max_microvolts","3300000\n")||
           write_value(IO_CONSUMER "min_microvolts","3300000\n")||
           !file_equals(IO_RAIL "microvolts","3300000")||
           !file_equals(IO_RAIL "state","enabled")||
           read_number(IO_RAIL "num_users")<rail_users+1)goto finish;
        puts("IO_RAIL_RETAINED_3300000");
        // Existing RTC alarms remain owned by Android. No diagnostic wake timer.
        puts("PREPARING");prepared=1;
        if(command("/system/bin/input","keyevent","223",NULL)||radio("bluetooth",0)||radio("wifi",0))goto finish;
        for(int i=0;i<18&&!interrupted;i++)sleep(1);
        if(interrupted||radio_setting("wifi_on")!=0||radio_setting("bluetooth_on")!=0)goto finish;
        // A wake request during preparation cancels entry rather than sleeping again.
        if(command("/system/bin/sh","-c","/system/bin/dumpsys power | /system/bin/grep 'mWakefulness=Asleep' >/dev/null",NULL))goto finish;
        for(int i=0;i<3;i++){
            if(status(d)||!idle(d)||interrupted)goto finish;
            usleep(100000);
        }
        sync();
        int power=open("/sys/power/state",O_WRONLY|O_CLOEXEC);
        if(power<0)goto finish;
        int64_t slept_before=suspended_millis();
        puts("SLEEP");
        ssize_t wrote=write(power,"mem\n",4);close(power);
        if(wrote!=4)goto finish;
        printf("SUSPENDED_MS %lld\n",(long long)(suspended_millis()-slept_before));
        FILE *wake=fopen("/sys/power/pm_wakeup_irq","r");
        if(wake){int irq;if(fscanf(wake,"%d",&irq)==1)printf("WAKE_IRQ %d\n",irq);fclose(wake);}
        puts("RESUMED");
    }
    result=0;
finish:
    tcsetattr(fd,TCSANOW,&prior); ioctl(fd,TIOCNXCL); close(fd); close(lock);
    if(rail_requested){
        // Resume has already restored the normal driver references.
        int released=write_value(IO_CONSUMER "min_microvolts","0\n");
        released|=write_value(IO_CONSUMER "max_microvolts","0\n");
        if(released||!file_equals(IO_CONSUMER "min_microvolts","0")||
           !file_equals(IO_CONSUMER "max_microvolts","0")){
            puts("IO_RAIL_RECOVERY_REQUIRED");result=1;
        }else puts("IO_RAIL_RELEASED");
    }
    if(prepared){
        command("/system/bin/input","keyevent","224",NULL);puts("AWAKE");
        sleep(15);
        int restored=radio("wifi",wifi);
        if(wifi&&wait_wifi())restored=-1;
        restored|=radio("bluetooth",bt);
        if(restored)puts("RADIO_RECOVERY_PENDING");
    }
    puts(result?"REFUSED":"DONE");
    return result;
}
