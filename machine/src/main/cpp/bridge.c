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
#include <sys/wait.h>
#include <sys/stat.h>
#include <inttypes.h>
#include <time.h>
#include <limits.h>

/* Product controller. No experiment, factory, raw-IH or arbitrary-frame commands.
 * Imported transport/CRC/interlocks from KitchenOSTest0.9; own source and pins.
 * Profile bytes follow CookingData/ControlBarViewModel's OEM start path. */
#include "control_profile.h"
static const char NORMAL_HEX[]="070a0000000000810000630000000000000000000000000000000000000000dc0078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a000000b40078141400000a0a4e22";
static const char MOTOR_HEX[]="030a00000000008100061e0000010000000000000000000000000000000006b400000000015a0606000000b40000000001000606008000b400000a0801000a0a008000b40000141402000a0a008000b40000141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a000000b40078141401000a0a91f6";
static const char FRY_HEX[]="030a000000000081000a001e0000000000000000000000000000000000800adcb4781414020a0a0a008000642978060302003c00008000826978090402053c0a0080008269780a0402053c0a008000b48c78141402050a0a008000b49678141401010a0a008000b49678141401010a0a008000b49678141401010a0a008000b49678141401010a0a008000b49678141401010a0a008000b49678141401010a0a008000b49678141401010a0a008000b49678141401010a0a008000b49678141401010a0a008000b496780a0501050a0af465";
/* OEM DefCookData.MODE_STRONG; no arbitrary raw-speed command. */
static const char STRONG_HEX[]="030a0000000000810002000200010000000000000000000000000000000002b40000000001780606000000b40000000001000606008000b400000a0801000a0a008000b40000141402000a0a008000b40000141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a008000b40078141401000a0a000000b40078141401000a0a8c73";
static int turbo_context=0,turbo_preparing=0,turbo_prepare_ack=0,turbo_saw_check=0,turbo_saw_running=0;
static int64_t turbo_prepare_due=0;
static int fd=-1,seq=0,pending=-1,pending_seq=0,probe=0,armed=0;
static int fast_probe=0;
/* Separate bounded diagnostic: never changes recipe budgets or owns a cook. */
static int pot_check=0,pot_id=0,pot_ack=0,pot_samples=0,pot_fault=0,pot_window=0;
static int ih_version=0,motor_version=0,version_confirmations=0;
static int64_t pot_started=0,pot_due=0,pot_poll_after=0,pot_last_sample=0;
static int statuses=0,infos=0,quitting=0,stop_check=0,stop_attempts=0;
static int saved_termios=0,failed=0,stopped_ack=0,update_step=0;
static int target_seconds=5,target_temp=0,target_speed=5,profile_kind=0;
static int phase_paused=0,paused_remaining=0,lidless_run=0,mcu_version=-1;
static int actual_temperature=-1,last_ih_status=-1,last_ih_gear=-1,last_motor_status=-1,last_motor_raw=-1;
static int state=-1,error_code=255,cover=-1,pot=-1,pot_status_raw=-1,cover_status_raw=-1;
static int cover_mapping_verified=1,pot_mapping_verified=0;
static int64_t sent_at=0,status_at=0,info_at=0,ping_at=0,start_at=0,deadline=0,session_deadline=0,stop_at=0;
static struct termios prior;
static unsigned char rx[4096];static size_t rxn=0;
static volatile sig_atomic_t interrupted=0;
static char queued[256];static int64_t queued_at=0;
static int64_t now_ms(void) { struct timespec t; clock_gettime(CLOCK_BOOTTIME,&t); return (int64_t)t.tv_sec*1000+t.tv_nsec/1000000; }
/* Match Android SystemClock.elapsedRealtime in captured logs, including sleep. */
static void on_signal(int s) { (void)s; interrupted=1; }
static uint16_t crc16(const unsigned char *p,size_t n) { uint16_t c=0; for(size_t i=0;i<n;i++){ c^=(uint16_t)p[i]<<8; for(int j=0;j<8;j++) c=(c&0x8000)?(c<<1)^0x1021:c<<1; } return c; }
static void event(const char *type,const char *msg) { printf("{\"type\":\"%s\",\"message\":\"%s\",\"diagnostic\":%s,\"potCheckId\":%d}\n",type,msg,pot_check?"true":"false",pot_check?pot_id:0); fflush(stdout); }
static int owns_control(void){return armed||pot_check;}
static void pot_event(const char *stage,const char *outcome,int idle){
 printf("{\"type\":\"potCheck\",\"stage\":\"%s\",\"id\":%d,\"outcome\":\"%s\",\"confirmedIdle\":%s,\"startAcked\":%s,\"phaseSamples\":%d,\"error\":%d}\n",stage,pot_id,outcome,idle?"true":"false",pot_ack?"true":"false",pot_samples,pot_fault);fflush(stdout);
}
static void hex(const unsigned char *p,size_t n,char *out) { static const char d[]="0123456789abcdef"; for(size_t i=0;i<n;i++){out[2*i]=d[p[i]>>4];out[2*i+1]=d[p[i]&15];}out[2*n]=0; }
static int unhex(const char *s,unsigned char *out,size_t max) { size_t n=strlen(s); if(n%2||n/2>max)return -1;for(size_t i=0;i<n/2;i++){unsigned int v;if(sscanf(s+2*i,"%2x",&v)!=1)return -1;out[i]=(unsigned char)v;}return (int)n/2; }
static size_t frame(int index,int op,const unsigned char *p,size_t n,unsigned char *out) { out[0]=0x5a;out[1]=index;out[2]=op;out[3]=n&255;out[4]=n>>8;if(n)memcpy(out+5,p,n);uint16_t c=crc16(out,n+5);out[n+5]=c>>8;out[n+6]=c&255;return n+7; }
/* MCU v12 consumes profile header byte7 bit3 in START, RESUME and lid watchdog.
 * Evidence: output/qa/lidless-profile-r2/firmware-flag-evidence.json.
 * Explicit local mode only. Range and MCU-error guards remain mandatory. */
#define PROFILE_OPEN_LID_FLAG 0x08
/* OEM CookProfile.setPhaseSpeed: stationary means direction0 AND magnitude0. */
static unsigned char motor_direction(int speed){return speed==0?0:speed>0?1:2;}
static int make_profile_mode(int kind,int seconds,int temp,int speed,int lidless,unsigned char *p){
 if(lidless&&kind!=PROFILE_NORMAL)return -1;
 if(!profile_supports(kind,seconds,temp,speed))return -1;
 const char *source=kind==PROFILE_TURBO?STRONG_HEX:kind==PROFILE_FRY?FRY_HEX:kind==PROFILE_MOTOR?MOTOR_HEX:NORMAL_HEX;
 if(unhex(source,p,210)!=210)return -1;
 p[0]=7;p[3]=p[4]=p[5]=0;p[6]=kind==PROFILE_TURBO?7:kind==PROFILE_FRY?5:kind==PROFILE_MOTOR?6:1;
 // Product maximum120min is below OEM's128min encoding branch in all three commands.
 p[8]=seconds/60;p[9]=seconds%60;p[29]=seconds/60;p[30]=seconds%60;p[32]=temp;
 p[36]=motor_direction(speed);p[37]=abs(speed);
 if(lidless)p[7]|=PROFILE_OPEN_LID_FLAG;
 // Preserve every dormant phase, header min/max and OEM power/migration/cycle field.
 uint16_t crc=crc16(p,208);p[208]=crc>>8;p[209]=crc&255;return 210;
}
static int make_profile(int kind,int seconds,int temp,int speed,unsigned char *p){
 return make_profile_mode(kind,seconds,temp,speed,0,p);
}
static int other_owner(void) {
 DIR *d=opendir("/proc");if(!d)return -1;struct dirent *e;int found=0;
 while((e=readdir(d))){int pid=atoi(e->d_name);if(pid<=0||pid==getpid())continue;
  char path[128];snprintf(path,sizeof(path),"/proc/%d/fd",pid);DIR *f=opendir(path);if(!f)continue;struct dirent *x;
  while((x=readdir(f))){if(x->d_name[0]=='.')continue;char link[384],target[256];snprintf(link,sizeof(link),"%s/%s",path,x->d_name);ssize_t n=readlink(link,target,sizeof(target)-1);if(n<0)continue;target[n]=0;
   if(!strcmp(target,"/dev/ttyS2")){found=pid;break;}}
  closedir(f);if(found)break;
 }closedir(d);return found;
}
static int stop_vendor(void) { pid_t p=fork();if(p==0){int dev=open("/dev/null",O_RDWR);dup2(dev,1);dup2(dev,2);execl("/system/bin/am","am","force-stop","com.chunmi.multicooker3",(char*)0);_exit(127);}if(p<0)return -1;int status;while(waitpid(p,&status,0)<0)if(errno!=EINTR)return -1;return WIFEXITED(status)?WEXITSTATUS(status):-1; }
static int send_op(int op,const unsigned char *data,size_t n) {
 unsigned char b[512];if(n>210)return -1;seq=(seq+1)%255;size_t len=frame(seq,op,data,n,b),off=0;int64_t write_started=now_ms(),end=write_started+200;int write_calls=0;
 // Match CVTESerial's bounded chunks. Relative sleeps between individual
 // bytes accumulated scheduler latency in the real foreground application.
 while(off<len&&now_ms()<end){size_t count=len-off;if(pot_check&&op==0x80&&count>32)count=32;ssize_t v=write(fd,b+off,count);write_calls++;if(v>0){off+=(size_t)v;}else if(errno==EAGAIN||errno==EINTR){struct pollfd w={fd,POLLOUT,0};poll(&w,1,10);}else break;}
 if(off!=len){event("error","UART-Schreibfehler");failed=1;return -1;}
 pending=op;pending_seq=seq;sent_at=now_ms();if(op>=0x80)printf("{\"type\":\"sent\",\"opcode\":%d,\"sequence\":%d,\"diagnostic\":%s,\"potCheckId\":%d,\"writeElapsedMs\":%lld,\"writeCalls\":%d}\n",op,seq,pot_check?"true":"false",pot_check?pot_id:0,(long long)(sent_at-write_started),write_calls);fflush(stdout);return 0;
}
static int stop_opcode(void){return turbo_context?0x8a:0x82;}
static void request_stop(const char *reason,int close_after){
 if(probe)return;turbo_preparing=0;turbo_prepare_ack=0;update_step=0;queued[0]=0;if(close_after)quitting=1;
 if(!stop_check){stop_at=now_ms();stop_attempts=0;stopped_ack=0;event("stopping",reason);}
 stop_check=1;pending=-1;if(stop_attempts<3){send_op(stop_opcode(),NULL,0);stop_attempts++;}
}
static int cover_allowed(int lidless){return cover_mapping_verified&&(lidless?(cover==0||cover==1):cover==0);}
static int safe_status(int lidless) { return cover_mapping_verified&&statuses>=3&&infos>0&&now_ms()-status_at<500&&now_ms()-info_at<1200&&cover_allowed(lidless)&&(!pot_mapping_verified||pot==1)&&error_code==0; }
static int remaining_seconds(void){if(!armed)return -1;if(turbo_preparing)return target_seconds;if(deadline<=0)return -1;if(phase_paused)return paused_remaining;int64_t rest=deadline-now_ms()-(profile_kind==PROFILE_TURBO?0:STOP_RESERVE_MS);return rest<=0?0:(int)((rest+999)/1000);}
/* Faster read-only weighing in confirmed idle. Cooking keeps its original cadence. */
static int status_interval_ms(void){
 if(pot_check)return 30;
 return (!probe||fast_probe)&&!armed&&!stop_check&&(state==0||state==11)&&error_code==0&&last_ih_status==0&&last_ih_gear==0&&last_motor_status==0&&last_motor_raw==0?50:100;
}
static int status_due(int64_t now){if(pot_check)return now-status_at>=30;return status_interval_ms()==50?now-status_at>=50:now-status_at>100;}
static int transport_wait_ms(int64_t now){
 if(status_interval_ms()==100||pending>=0||update_step||queued[0])return 20;
 int64_t remaining=status_at+status_interval_ms()-now;
 return remaining<=0?0:remaining<20?(int)remaining:20;
}
static void packet(const unsigned char *p,size_t n) {
 int op=p[2],index=p[1];size_t len=n-7;
 /* Only a CRC-valid response with the outstanding request's sequence is accepted. */
 if(pending<0||index!=pending_seq)return;
 int request=pending;
 if(request==0x20&&op==0x21&&len==24){
  pending=-1;const unsigned char *d=p+5;state=d[0];actual_temperature=d[4];cover_status_raw=d[7];pot_status_raw=d[8];error_code=d[11];status_at=now_ms();statuses++;
  uint32_t elapsed=(uint32_t)d[16]|(uint32_t)d[17]<<8|(uint32_t)d[18]<<16|(uint32_t)d[19]<<24;
  int motor=d[12]|d[13]<<8,weight=(int16_t)(d[14]|d[15]<<8);char raw[49];hex(d,24,raw);
  last_ih_status=d[2];last_ih_gear=d[3];last_motor_status=d[6];last_motor_raw=motor;
  printf("{\"type\":\"status\",\"diagnostic\":%s,\"potCheckId\":%d,\"state\":%d,\"temperature\":%d,\"coverOpen\":%d,\"coverStatusRaw\":%d,\"potPresent\":%d,\"potStatusRaw\":%d,\"coverMappingVerified\":%s,\"potMappingVerified\":%s,\"error\":%d,\"ihStatus\":%d,\"ihGear\":%d,\"motorStatus\":%d,\"motorRaw\":%d,\"weightRaw\":%d,\"remainingSeconds\":%d,\"elapsed\":%u,\"samples\":%d,\"receivedElapsedMs\":%lld,\"armed\":%s,\"raw\":\"%s\"}\n",pot_check?"true":"false",pot_check?pot_id:0,state,d[4],cover,cover_status_raw,pot,pot_status_raw,cover_mapping_verified?"true":"false",pot_mapping_verified?"true":"false",error_code,d[2],d[3],d[6],motor,weight,remaining_seconds(),elapsed,statuses,(long long)status_at,armed?"true":"false",raw);fflush(stdout);
  if(stop_check&&stopped_ack&&(state==0||state==11)&&error_code==0&&last_ih_status==0&&last_ih_gear==0&&last_motor_status==0&&last_motor_raw==0){
   armed=0;stop_check=0;deadline=0;turbo_context=turbo_preparing=turbo_prepare_ack=turbo_saw_check=turbo_saw_running=0;event("stopped","Stopp bestaetigt und MCU im Leerlauf");
   if(pot_check){pot_event("complete",pot_fault==2?"missing":pot_window?"clear_window":"unknown",1);pot_check=0;}
  }
  if(turbo_context&&!stop_check){
   if(turbo_preparing){
    if(state==9)turbo_saw_check=1;
    if((state!=0&&state!=11&&state!=9&&state!=10)||last_ih_status||last_ih_gear||last_motor_status||last_motor_raw)request_stop("Turbo: unerwartete Ausgaenge bei Vorbereitung",0);
   }else{
    if(state!=1&&state!=10&&state!=11)request_stop("Turbo: unerwarteter Laufzustand",0);
    if(state==1)turbo_saw_running=1;
    if(state==10&&turbo_saw_running)request_stop("Kochzeit beendet",0);
   }
  }
  if(pot_check&&!stop_check){
   if(error_code){pot_fault=error_code;request_stop("Topfpruefung: MCU-Fehler",0);}
   else if(state!=9||last_ih_status||last_ih_gear||last_motor_status||last_motor_raw){request_stop("Topfpruefung: unerwarteter Zustand",0);}
   else if(pot_ack){pot_samples++;pot_last_sample=now_ms();}
  }
  if(armed&&!stop_check&&(error_code!=0||state==8||!cover_allowed(lidless_run)||(pot_mapping_verified&&pot!=1))){char reason[160];snprintf(reason,sizeof(reason),"Schutzstopp: MCU-Zustand %d, Fehler %d, Deckelsignal %d, Topfsignal %d",state,error_code,cover,pot);request_stop(reason,0);}
  if(armed&&!stop_check&&profile_kind==PROFILE_MANUAL&&abs(target_speed)>60&&(actual_temperature<0||actual_temperature>80))request_stop("Manuell: ueber80 C hoechstens Stufe6",0);
  if(armed&&!stop_check&&(profile_kind==PROFILE_TURBO||profile_kind==PROFILE_MOTOR)&&(actual_temperature<0||actual_temperature>=60||last_ih_gear!=0))request_stop("Messerprofil: Temperatur- oder Heizungsgrenze erreicht",0);
  if(armed&&!stop_check&&profile_kind==PROFILE_FRY&&(actual_temperature<0||actual_temperature>190||last_ih_gear>120))request_stop("Bratprofil: Temperatur- oder Leistungsgrenze erreicht",0);
  if(armed&&!stop_check&&!turbo_preparing&&state==11)request_stop("Kochzeit beendet",0);
 }else if(request==0x22&&op==0x23&&len==20){
  pending=-1;info_at=now_ms();infos++;mcu_version=p[5];ih_version=p[6];motor_version=p[8];version_confirmations=mcu_version==12&&ih_version==4&&motor_version==5?version_confirmations+1:0;cover=cover_mapping_verified?((p[23]&128)?0:1):-1;pot=pot_mapping_verified?((p[23]&64)?1:0):-1;char raw[41];hex(p+5,20,raw);
  printf("{\"type\":\"info\",\"version\":%d,\"ihVersion\":%d,\"motorVersion\":%d,\"switches\":%d,\"raw\":\"%s\"}\n",p[5],p[6],p[8],p[23],raw);fflush(stdout);
  if(turbo_context&&!stop_check&&(mcu_version!=12||ih_version!=4||motor_version!=5))request_stop("Turbo: Platinenversion geaendert",0);
  if(armed&&!stop_check&&(!cover_allowed(lidless_run)||(pot_mapping_verified&&pot!=1)))request_stop("Deckel-/Topfsignal geaendert",0);
 }else if(request>=0x80){
  pending=-1;printf("{\"type\":\"ack\",\"opcode\":%d,\"success\":%s,\"response\":%d,\"diagnostic\":%s,\"potCheckId\":%d}\n",request,op==0?"true":"false",op,pot_check?"true":"false",pot_check?pot_id:0);fflush(stdout);
  if(pot_check&&request==0x80&&op==0){pot_ack=1;pot_poll_after=now_ms()+160;}
  if(request==0x89&&op==0&&turbo_preparing&&!stop_check)turbo_prepare_ack=1;
  if(request==stop_opcode()&&op==0)stopped_ack=1;
  if(request==0x81&&op==0){paused_remaining=remaining_seconds();phase_paused=1;}
  if(request==0x85&&op==0)phase_paused=0;
  if(op!=0){update_step=0;if(request!=stop_opcode()&&owns_control())request_stop("MCU hat Befehl abgelehnt",0);else if(request==stop_opcode()){event("error","MCU hat Stopp abgelehnt - physischen Zustand pruefen");failed=1;}}
 }else{char raw[600];hex(p,n,raw);printf("{\"type\":\"unexpected\",\"request\":%d,\"raw\":\"%s\"}\n",request,raw);fflush(stdout);}
}
static void receive_bytes(const unsigned char *p,size_t n) {
 if(n+rxn>sizeof(rx)){rxn=0;event("error","UART-Empfangspuffer uebergelaufen");}
 if(n>sizeof(rx))return;memcpy(rx+rxn,p,n);rxn+=n;
 while(rxn>=7){
  if(rx[0]!=0x5a){memmove(rx,rx+1,--rxn);continue;}
  size_t len=(size_t)rx[3]+((size_t)rx[4]<<8);if(len>256){memmove(rx,rx+1,--rxn);continue;}
  size_t full=len+7;if(rxn<full)break;
  uint16_t expected=(uint16_t)rx[full-2]|(uint16_t)rx[full-1]<<8;
  if(crc16(rx,full-2)!=expected){memmove(rx,rx+1,--rxn);continue;}
  packet(rx,full);memmove(rx,rx+full,rxn-full);rxn-=full;
 }
}
static int integer_token(const char *word,int *result){char *end;errno=0;long long value=strtoll(word,&end,10);if(errno||end==word||*end||value<INT_MIN||value>INT_MAX)return 0;*result=(int)value;return 1;}
static void command(char *line){
 int seconds,temp,speed;
 if(!strcmp(line,"PING")){ping_at=now_ms();return;}
 if(!strcmp(line,"STOP")){request_stop("Stopp angefordert",0);return;}
 if(!strcmp(line,"QUIT")){if(owns_control()||stop_check)request_stop("Verbindung wird beendet",1);else quitting=1;return;}
 if(!strcmp(line,"CHECK_POT")){
  if(probe||owns_control()||stop_check||!cover_allowed(1)||!safe_status(1)||actual_temperature<0||mcu_version!=12||ih_version!=4||motor_version!=5||version_confirmations<2||(state!=0&&state!=11)||last_ih_status||last_ih_gear||last_motor_status||last_motor_raw){event("error","Topfpruefung nur im frischen Leerlauf der geprueften Platinen");return;}
  if(pending>=0){if(pending==0x20||pending==0x22){snprintf(queued,sizeof(queued),"CHECK_POT");queued_at=now_ms();}else event("error","Steuerbefehl noch offen");return;}
  unsigned char fixed[210]={7,10,0,0,0,0,1,0x81,0,1,99};fixed[30]=1;uint16_t crc=crc16(fixed,208);fixed[208]=crc>>8;fixed[209]=crc&255;
  pot_check=1;pot_id++;pot_ack=pot_samples=pot_fault=pot_window=0;pot_last_sample=0;pot_started=now_ms();pot_due=pot_started+1500;pot_poll_after=pot_due;
  pot_event("checking","unknown",0);send_op(0x80,fixed,210);return;
 }
 if(pot_check){event("error","Topfpruefung besitzt die Steuerung");return;}
 if(turbo_context){event("error","Turbo erlaubt nur Stoppen; jeder Impuls braucht einen neuen Start");return;}
 if(!strcmp(line,"PAUSE")){if(armed&&!stop_check&&safe_status(lidless_run)){if(pending<0)send_op(0x81,NULL,0);else if(pending==0x20||pending==0x22){snprintf(queued,sizeof(queued),"PAUSE");queued_at=now_ms();}else event("error","Vorheriger Steuerbefehl noch nicht bestaetigt");}else event("error","Pause derzeit nicht moeglich");return;}
 char copy[256];if(strlen(line)>=sizeof(copy)){event("error","Befehl zu lang");return;}strcpy(copy,line);char *tokens[6],*save=NULL;int fields=0;
 for(char *word=strtok_r(copy," \t",&save);word&&fields<6;word=strtok_r(NULL," \t",&save))tokens[fields++]=word;
 if(fields>0&&!strcmp(tokens[0],"FRY_MOTOR")){
  if(fields!=2||!integer_token(tokens[1],&speed)||(speed!=0&&speed!=-10)){event("error","Anbratintervall: nur Motor0 oder rueckwaerts10");return;}
  if(probe||!armed||profile_kind!=PROFILE_FRY||state!=1||phase_paused||stop_check||update_step||lidless_run||!safe_status(0)||mcu_version!=12||ih_version!=4||motor_version!=5||version_confirmations<2||now_ms()>=deadline-STOP_RESERVE_MS||now_ms()>=session_deadline){event("error","Anbratintervall nur im bestaetigten laufenden Bratprofil");return;}
  if(pending>=0){if(pending==0x20||pending==0x22){snprintf(queued,sizeof(queued),"%s",line);queued_at=now_ms();}else event("error","Vorheriger Steuerbefehl noch nicht bestaetigt");return;}
  unsigned char motor[2]={motor_direction(speed),abs(speed)};
  target_speed=speed;send_op(0x88,motor,sizeof(motor));return;
 }
 int settings_only=fields==3&&!strcmp(tokens[0],"APPLY_SETTINGS");
 if(settings_only){seconds=target_seconds;if(!integer_token(tokens[1],&temp)||!integer_token(tokens[2],&speed)){event("error","Ungueltige Einstellungen");return;}}
 else if((fields!=4&&fields!=5)||!integer_token(tokens[1],&seconds)||!integer_token(tokens[2],&temp)||!integer_token(tokens[3],&speed)||(fields==5&&strcmp(tokens[4],"POT_CONFIRMED"))){event("error","Ungueltiger Befehl");return;}
 const char *name=tokens[0],*confirmation=fields==5?tokens[4]:"";
 if(strcmp(name,"START_TURBO")&&strcmp(name,"START_MANUAL")&&strcmp(name,"START_OPEN")&&strcmp(name,"START")&&strcmp(name,"APPLY")&&strcmp(name,"APPLY_SETTINGS")&&strcmp(name,"RESUME")){event("error","Unbekannter Befehl");return;}
 int starting=!strcmp(name,"START_TURBO")||!strcmp(name,"START")||!strcmp(name,"START_OPEN")||!strcmp(name,"START_MANUAL");
 int lidless=starting?!strcmp(name,"START_OPEN"):lidless_run;
 int kind=starting?(!strcmp(name,"START_TURBO")?PROFILE_TURBO:!strcmp(name,"START_MANUAL")?PROFILE_MANUAL:profile_for(seconds,temp,speed)):profile_kind;
 if(kind==PROFILE_TURBO&&(mcu_version!=12||ih_version!=4||motor_version!=5||version_confirmations<2)){event("error","Turbo nur fuer bestaetigte Platinen MCU12/IH4/Motor5");return;}
 if(kind==PROFILE_TURBO&&strcmp(confirmation,"POT_CONFIRMED")){event("error","Turbo erfordert Topfbestaetigung");return;}
 if(lidless&&mcu_version!=12){event("error","Deckelloses Profil ist nur fuer die untersuchte MCU-Version12 vorgesehen");return;}
 if(lidless&&(kind!=PROFILE_NORMAL||!profile_supports(PROFILE_NORMAL,settings_only?1:seconds,temp,speed))){event("error","Ohne Deckel: maximal100 C und Stufe3");return;}
 if(!profile_supports(kind,settings_only?1:seconds,temp,speed)){event("error","Einstellungen passen nicht zum aktuellen Kochprofil; fuer Profilwechsel zuerst stoppen");return;}
 if(!pot_mapping_verified&&(starting||!strcmp(name,"RESUME"))&&strcmp(confirmation,"POT_CONFIRMED")){event("error","Topf vor jedem Start/Fortsetzen am Display bestaetigen");return;}
 if(!safe_status(lidless)||stop_check){event("error","Kein frischer sicherer MCU-Status: Deckel, Topf und Fehler pruefen");return;}
 if((kind==PROFILE_TURBO||kind==PROFILE_MOTOR)&&(actual_temperature<0||actual_temperature>=60||last_ih_gear!=0)){event("error","Messerprofil erst unter60 C ohne aktive Heizung moeglich");return;}
 if(kind==PROFILE_MANUAL&&abs(speed)>60&&(actual_temperature<0||actual_temperature>80)){event("error","Ueber80 C hoechstens Stufe6 waehlen");return;}
 if(!starting&&(session_deadline<=now_ms()||(!settings_only&&now_ms()+(int64_t)seconds*1000+STOP_RESERVE_MS>session_deadline))){event("error","Aenderung ueberschreitet das verbleibende Sitzungsbudget");return;}
 if(pending>=0){if(pending==0x20||pending==0x22){snprintf(queued,sizeof(queued),"%s",line);queued_at=now_ms();event("queued","Befehl wartet auf laufende Statusantwort");}else event("error","Vorheriger Steuerbefehl noch nicht bestaetigt");return;}
 if(starting){
  if(armed||(state!=0&&state!=11)||last_ih_status!=0||last_ih_gear!=0||last_motor_status!=0||last_motor_raw!=0){event("error","Start nur im bestaetigten Leerlauf mit Nullausgaengen");return;}
  if(now_ms()-info_at>200){snprintf(queued,sizeof(queued),"%s",line);queued_at=now_ms();send_op(0x22,NULL,0);return;}
  unsigned char profile[210];if(make_profile_mode(kind,seconds,temp,speed,lidless,profile)!=210)return;
  profile_kind=kind;lidless_run=lidless;phase_paused=0;target_seconds=seconds;target_temp=temp;target_speed=speed;armed=1;start_at=now_ms();
  if(kind==PROFILE_TURBO){
   turbo_context=turbo_preparing=1;turbo_prepare_ack=turbo_saw_check=turbo_saw_running=0;
   turbo_prepare_due=start_at+9000;deadline=session_deadline=0;event("preparing","Turbo: Maschine pruefen");send_op(0x89,NULL,0);
  }else{session_deadline=start_at+(int64_t)profile_max_seconds(kind)*1000+STOP_RESERVE_MS;deadline=start_at+(int64_t)seconds*1000+STOP_RESERVE_MS;send_op(0x80,profile,210);}
 }else if(!strcmp(name,"RESUME")){
  if(!armed||state!=3){event("error","Fortsetzen nur fuer eigenen pausierten Lauf");return;}
  unsigned char data[5]={seconds/60,seconds%60,temp,motor_direction(speed),abs(speed)};
  target_seconds=seconds;target_temp=temp;target_speed=speed;deadline=now_ms()+(int64_t)seconds*1000+STOP_RESERVE_MS;send_op(0x85,data,5);
 }else{
  if(!armed||(state!=1&&state!=3&&state!=9)){event("error","Aendern nur im eigenen Lauf");return;}
  target_temp=temp;target_speed=speed;update_step=settings_only?2:1;if(!settings_only){target_seconds=seconds;deadline=now_ms()+(int64_t)seconds*1000+STOP_RESERVE_MS;if(phase_paused)paused_remaining=seconds;}
 }
}
/* Readiness grants one pulse only; called after input handling on the next loop.
 * STOP/QUIT clear turbo_preparing before any delayed ready response can start it. */
static void advance_turbo(int64_t now){
 if(!turbo_preparing||!armed||stop_check||quitting||pending>=0||!turbo_prepare_ack||!turbo_saw_check||state!=10)return;
 if(now>=turbo_prepare_due){request_stop("Turbo: Vorbereitung abgelaufen",0);return;}
 if(now-info_at>200){send_op(0x22,NULL,0);return;}
 if(!safe_status(0)||mcu_version!=12||ih_version!=4||motor_version!=5||actual_temperature<0||actual_temperature>=60||last_ih_status||last_ih_gear||last_motor_status||last_motor_raw){request_stop("Turbo: Startbedingungen verloren",0);return;}
 unsigned char profile[210];if(make_profile(PROFILE_TURBO,target_seconds,0,target_speed,profile)!=210){request_stop("Turbo: ungueltiges Profil",0);return;}
 turbo_preparing=0;start_at=now_ms();deadline=start_at+(int64_t)target_seconds*1000;session_deadline=deadline;
 /* Deadline begins before writing the profile. No stop reserve permits extra motor time. */
 send_op(0x80,profile,210);
}
static void watch_run(int64_t now){
 if(pot_check&&!stop_check&&now>=pot_due){pot_window=pot_ack&&pot_samples>=10&&pot_last_sample-pot_started>=1400&&!pot_fault;request_stop("Topfpruefung beendet",0);}
 if(armed&&!stop_check){
  if(now-status_at>1500)request_stop("Statusantwort fehlt",0);
  else if(now-info_at>1200)request_stop("Infoantwort fehlt",0);
  else if(turbo_preparing){if(now>=turbo_prepare_due)request_stop("Turbo: Vorbereitung abgelaufen",0);}
  else if(turbo_context&&now>=deadline)request_stop(turbo_saw_running?"Kochzeit beendet":"Turbo: Motorstart nicht bestaetigt",0);
  else if(now>=session_deadline)request_stop("Sitzungsbudget erreicht",0);
  else if(!phase_paused&&now>=deadline)request_stop("Eingestellte Laufzeit erreicht",0);
 }
}
static void advance_update(void){unsigned char data[2];if(update_step==1){data[0]=target_seconds/60;data[1]=target_seconds%60;send_op(0x86,data,2);update_step=2;}else if(update_step==2){data[0]=target_temp;send_op(0x87,data,1);update_step=3;}else if(update_step==3){data[0]=motor_direction(target_speed);data[1]=abs(target_speed);send_op(0x88,data,2);update_step=0;}}
static int selftest(void){
 unsigned char p[210],b[217];
 for(int seconds=1;seconds<=7200;seconds++){
  if(make_profile(PROFILE_NORMAL,seconds,100,-30,p)!=210||p[8]!=seconds/60||p[9]!=seconds%60||p[29]!=p[8]||p[30]!=p[9])return 1;
  if(frame(1,0x80,p,210,b)!=217||b[3]!=210||b[4]!=0)return 2;
 }
 if(profile_for(7201,0,5)||profile_for(301,0,100)||profile_for(20,100,100)||profile_for(10,180,100)||profile_for(10,0,-100)||profile_for(10,0,35)||profile_for(1,0,0))return 3;
 if(make_profile(PROFILE_FRY,1800,180,-10,p)!=210||p[6]!=5||p[10]!=0||p[11]!=30||p[41]!=0x80||p[42]!=0||p[33]!=120||p[36]!=2||p[37]!=10)return 4;
 if(make_profile(PROFILE_MOTOR,300,0,100,p)!=210||p[6]!=6||p[32]!=0||p[33]!=0||p[38]!=6||p[39]!=6)return 5;
 for(int temp=35;temp<=100;temp++)for(int seconds=1;seconds<=7200;seconds+=7199){
  if(make_profile(PROFILE_NORMAL,seconds,temp,0,p)!=210||p[32]!=temp||p[36]!=0||p[37]!=0||crc16(p,208)!=((unsigned)p[208]<<8|p[209]))return 6;
 }
 if(profile_for(1,34,0)||profile_for(1,101,0)||profile_for(1,180,0))return 7;
 event("selftest","PASS: product profiles, duration1-7200, frame/CRC, exact OEM dormant fields and forbidden combinations; no UART opened");return 0;
}
/* Memory/pipe fixtures only: exercise the real parser and interlocks, no UART. */
static int selftest_lidless(void){
 int pipes[2];if(pipe(pipes))return 20;fd=pipes[1];
 statuses=3;infos=1;mcu_version=12;status_at=info_at=now_ms();cover=1;error_code=0;state=0;
 last_ih_status=last_ih_gear=last_motor_status=last_motor_raw=0;
 if(safe_status(0)||!safe_status(1))return 21;
 cover=-1;if(safe_status(1))return 22;cover=1;
 pot_mapping_verified=1;pot=0;if(safe_status(1))return 23;pot=1;
 error_code=1;if(safe_status(1))return 24;error_code=0;
 char normal[]="START 60 100 -30 POT_CONFIRMED";command(normal);if(armed||pending>=0)return 25;
 char no_pot[]="START_OPEN 60 100 -30";pot_mapping_verified=0;command(no_pot);if(armed||pending>=0)return 26;
 for(int speed=-100;speed<=100;speed+=5){
  if(speed!=0&&abs(speed)<=30)continue;
  char invalid[100];snprintf(invalid,sizeof(invalid),"START_OPEN 60 0 %d POT_CONFIRMED",speed);command(invalid);if(armed||pending>=0)return 27;
 }
 for(int temp=101;temp<=180;temp++){
  char invalid[100];snprintf(invalid,sizeof(invalid),"START_OPEN 60 %d -10 POT_CONFIRMED",temp);command(invalid);if(armed||pending>=0)return 28;
 }
 // Unsupported/unknown MCU versions must never enter the new profile.
 for(int version=11;version<=13;version+=2){mcu_version=version;char unsupported[]="START_OPEN 60 100 -30 POT_CONFIRMED";command(unsupported);if(armed||pending>=0)return 37;}mcu_version=12;
 unsigned char closed_profile[210],open_profile[210];
 for(int sec=1;sec<=7200;sec++){
  if(make_profile(PROFILE_NORMAL,sec,100,-30,closed_profile)!=210||make_profile_mode(PROFILE_NORMAL,sec,100,-30,1,open_profile)!=210)return 38;
  for(int i=0;i<208;i++)if(open_profile[i]!=(i==7?(closed_profile[i]|PROFILE_OPEN_LID_FLAG):closed_profile[i]))return 39;
  if(crc16(open_profile,208)!=((unsigned)open_profile[208]<<8|open_profile[209]))return 40;
 }
 if(make_profile_mode(PROFILE_FRY,60,180,-10,1,open_profile)!=-1||make_profile_mode(PROFILE_MOTOR,60,0,100,1,open_profile)!=-1)return 41;
 status_at=info_at=now_ms();
 char start[]="START_OPEN 60 100 -30 POT_CONFIRMED";command(start);
 if(!armed||!lidless_run||profile_kind!=PROFILE_NORMAL||pending!=0x80)return 29;
 unsigned char sent[512];ssize_t count=read(pipes[0],sent,sizeof(sent));
 if(count!=217||sent[2]!=0x80||sent[5+7]!=(0x81|PROFILE_OPEN_LID_FLAG)||sent[5+6]!=1||sent[5+32]!=100||sent[5+37]!=30)return 30;
 pending=-1;state=1;
 for(const char **action=(const char*[]){"APPLY","RESUME",NULL};*action;action++){
  for(int temp=100;temp<=105;temp+=5)for(int speed=30;speed<=40;speed+=10){
   if(temp==100&&speed==30)continue;
   char invalid[100];snprintf(invalid,sizeof(invalid),"%s 60 %d %d",*action,temp,speed);command(invalid);if(pending>=0||update_step)return 31;
  }
 }
 char invalid_settings[]="APPLY_SETTINGS 105 -10";command(invalid_settings);if(pending>=0||update_step)return 32;
 char valid_settings[]="APPLY_SETTINGS 95 30";command(valid_settings);if(update_step!=2||target_temp!=95||target_speed!=30)return 33;update_step=0;
 // An open lid remains valid in the explicit mode; device errors still stop it.
 unsigned char info[27]={0};info[1]=7;info[2]=0x23;info[5]=12;pending=0x22;pending_seq=7;packet(info,sizeof(info));if(stop_check||cover!=1)return 34;
 unsigned char status[31]={0};status[1]=8;status[2]=0x21;status[5]=1;status[16]=2;pending=0x20;pending_seq=8;packet(status,sizeof(status));if(!stop_check||pending!=0x82)return 35;
 // All existing profiles still stop as soon as their lid signal opens.
 stop_check=0;pending=0x22;pending_seq=7;lidless_run=0;packet(info,sizeof(info));if(!stop_check||pending!=0x82)return 36;
 close(pipes[0]);close(pipes[1]);fd=-1;
 event("selftest","PASS: lidless explicit start,100 C/speed3 caps on start/apply/resume, pot/error guards and unchanged closed-lid interlock; no UART opened");return 0;
}
/* Actual CRC/framing parser and ACK/status transitions, with pipe-only output. */
static int selftest_expect_frame(int input,int opcode,size_t payload,unsigned char *out){
 struct pollfd wait={input,POLLIN,0};if(poll(&wait,1,1000)!=1)return 0;
 ssize_t n=read(input,out,512);if(n!=(ssize_t)(payload+7)||out[2]!=opcode||out[3]!=(payload&255)||out[4]!=(payload>>8))return 0;
 return crc16(out,(size_t)n-2)==((unsigned)out[n-2]<<8|out[n-1]);
}
static void selftest_reply(int opcode,const unsigned char *payload,size_t size){
 unsigned char data[512];size_t length=frame(pending_seq,opcode,payload,size,data);
 unsigned char high=data[length-2];data[length-2]=data[length-1];data[length-1]=high;
 receive_bytes(data,3);receive_bytes(data+3,length-3);
}
static int selftest_status_reply(int input,int next_state,int running){
 unsigned char sent[512],data[24]={0};if(send_op(0x20,NULL,0)||!selftest_expect_frame(input,0x20,0,sent))return 0;
 data[0]=next_state;data[4]=60;if(running){data[2]=1;data[3]=40;data[6]=1;data[12]=123;}
 selftest_reply(0x21,data,sizeof(data));return pending==-1;
}
static int selftest_info_reply(int input,int closed,int version){
 unsigned char sent[512],data[20]={0};if(send_op(0x22,NULL,0)||!selftest_expect_frame(input,0x22,0,sent))return 0;
 data[0]=version;data[18]=closed?0x80:0;selftest_reply(0x23,data,sizeof(data));return pending==-1;
}
static int selftest_lidless_sequences(void){
 int pipes[2];if(pipe(pipes))return 60;fd=pipes[1];
 statuses=3;infos=1;mcu_version=12;status_at=info_at=now_ms();cover=1;error_code=0;state=0;
 last_ih_status=last_ih_gear=last_motor_status=last_motor_raw=0;
 unsigned char sent[512],expected[210];char start[]="START_OPEN 60 100 -30 POT_CONFIRMED";
 mcu_version=-1;command(start);if(armed||pending!=-1)return 82;
 if(!selftest_info_reply(pipes[0],0,0)||mcu_version!=0)return 83;
 command(start);if(armed||pending!=-1)return 84;
 if(!selftest_info_reply(pipes[0],0,12)||mcu_version!=12)return 85;
 command(start);
 if(!selftest_expect_frame(pipes[0],0x80,210,sent)||sent[12]!=0x89||!armed||!lidless_run||pending!=0x80)return 61;
 selftest_reply(0,NULL,0);if(pending!=-1||!armed||!lidless_run)return 62;
 if(!selftest_status_reply(pipes[0],1,1)||stop_check)return 63;
 char pause[]="PAUSE";command(pause);
 if(!selftest_expect_frame(pipes[0],0x81,0,sent)||pending!=0x81||phase_paused)return 64;
 selftest_reply(0,NULL,0);if(pending!=-1||!phase_paused||paused_remaining<=0||!armed)return 65;
 if(!selftest_status_reply(pipes[0],3,0)||state!=3||!phase_paused||stop_check)return 66;
 char resume[]="RESUME 30 95 20 POT_CONFIRMED";command(resume);
 if(!selftest_expect_frame(pipes[0],0x85,5,sent)||pending!=0x85||!phase_paused||memcmp(sent+5,(unsigned char[]){0,30,95,1,20},5))return 67;
 selftest_reply(0,NULL,0);if(pending!=-1||phase_paused||!armed||!lidless_run)return 68;
 if(!selftest_status_reply(pipes[0],1,1)||!selftest_info_reply(pipes[0],0,12)||stop_check||cover!=1)return 69;
 // Settings-only must pass both MCU setters/ACKs without replacing the timer.
 int64_t previous_deadline=deadline;int previous_seconds=target_seconds;
 char settings[]="APPLY_SETTINGS 90 -25";command(settings);
 if(update_step!=2||pending!=-1||target_temp!=90||target_speed!=-25)return 86;
 advance_update();if(!selftest_expect_frame(pipes[0],0x87,1,sent)||sent[5]!=90||pending!=0x87||update_step!=3)return 87;
 selftest_reply(0,NULL,0);if(pending!=-1||update_step!=3)return 88;
 advance_update();if(!selftest_expect_frame(pipes[0],0x88,2,sent)||sent[5]!=2||sent[6]!=25||pending!=0x88||update_step!=0)return 89;
 selftest_reply(0,NULL,0);if(pending!=-1||target_seconds!=previous_seconds||deadline!=previous_deadline||!armed||!lidless_run)return 90;
 char stop[]="STOP";command(stop);
 if(!selftest_expect_frame(pipes[0],0x82,0,sent)||pending!=0x82||!stop_check||!armed)return 70;
 selftest_reply(0,NULL,0);if(pending!=-1||!stopped_ack||!stop_check||!armed)return 71;
 if(!selftest_status_reply(pipes[0],0,0)||armed||stop_check||deadline!=0)return 72;
 // A later normal start must re-enable its closed-lid interlock.
 char normal[]="START 60 95 -15 POT_CONFIRMED";command(normal);if(armed||pending!=-1)return 73;
 if(!selftest_info_reply(pipes[0],1,12)||cover!=0)return 74;
 command(normal);
 if(!selftest_expect_frame(pipes[0],0x80,210,sent)||!armed||lidless_run||sent[12]!=0x81)return 75;
 if(make_profile(PROFILE_NORMAL,60,95,-15,expected)!=210||memcmp(sent+5,expected,210))return 76;
 selftest_reply(0,NULL,0);if(pending!=-1||!armed||lidless_run)return 77;
 if(!selftest_status_reply(pipes[0],1,1))return 78;
 // Opening the lid in the subsequent normal run still sends the real STOP.
 if(selftest_info_reply(pipes[0],0,12)||!stop_check||pending!=0x82)return 79;
 if(!selftest_expect_frame(pipes[0],0x82,0,sent))return 80;
 selftest_reply(0,NULL,0);if(!selftest_status_reply(pipes[0],0,0)||armed||stop_check)return 81;
 close(pipes[0]);close(pipes[1]);fd=-1;
 event("selftest","PASS: CRC-valid unknown-MCU rejection, START ACK, PAUSE ACK/status3, RESUME ACK/status1, APPLY_SETTINGS87/88 ACKs preserve timer, STOP ACK/idle, next closed-lid normal start and lid-open stop; pipes only, no UART opened");return 0;
}
static int selftest_heat_only(void){
 int pipes[2];if(pipe(pipes))return 100;fd=pipes[1];
 statuses=3;infos=1;mcu_version=12;status_at=info_at=now_ms();cover=1;error_code=0;state=0;
 last_ih_status=last_ih_gear=last_motor_status=last_motor_raw=0;
 unsigned char sent[512];
 for(const char **line=(const char*[]){"START_OPEN 60 0 0 POT_CONFIRMED","START_OPEN 60 34 0 POT_CONFIRMED","START_OPEN 60 105 0 POT_CONFIRMED","START_OPEN 7201 100 0 POT_CONFIRMED","START_OPEN 60 100 0","START 60 100 0 POT_CONFIRMED",NULL};*line;line++){
  char invalid[100];snprintf(invalid,sizeof(invalid),"%s",*line);command(invalid);if(armed||pending!=-1)return 101;
 }
 char start[]="START_OPEN 60 100 0 POT_CONFIRMED";command(start);
 if(!selftest_expect_frame(pipes[0],0x80,210,sent)||sent[12]!=0x89||sent[37]!=100||sent[41]!=0||sent[42]!=0||!armed||!lidless_run)return 102;
 selftest_reply(0,NULL,0);if(!selftest_status_reply(pipes[0],1,0)||stop_check)return 103;
 int64_t previous_deadline=deadline;int previous_seconds=target_seconds;
 char settings[]="APPLY_SETTINGS 95 0";command(settings);
 if(update_step!=2||target_speed!=0)return 104;
 advance_update();if(!selftest_expect_frame(pipes[0],0x87,1,sent)||sent[5]!=95)return 105;
 selftest_reply(0,NULL,0);advance_update();
 if(!selftest_expect_frame(pipes[0],0x88,2,sent)||sent[5]!=0||sent[6]!=0)return 106;
 selftest_reply(0,NULL,0);if(deadline!=previous_deadline||target_seconds!=previous_seconds)return 107;
 char pause[]="PAUSE";command(pause);if(!selftest_expect_frame(pipes[0],0x81,0,sent))return 108;
 selftest_reply(0,NULL,0);if(!selftest_status_reply(pipes[0],3,0)||!phase_paused)return 109;
 char resume[]="RESUME 30 95 0 POT_CONFIRMED";command(resume);
 if(!selftest_expect_frame(pipes[0],0x85,5,sent)||memcmp(sent+5,(unsigned char[]){0,30,95,0,0},5))return 110;
 selftest_reply(0,NULL,0);if(!selftest_status_reply(pipes[0],1,0)||phase_paused)return 111;
 char apply[]="APPLY 20 90 0";command(apply);advance_update();
 if(!selftest_expect_frame(pipes[0],0x86,2,sent)||sent[5]!=0||sent[6]!=20)return 112;
 selftest_reply(0,NULL,0);advance_update();if(!selftest_expect_frame(pipes[0],0x87,1,sent)||sent[5]!=90)return 113;
 selftest_reply(0,NULL,0);advance_update();if(!selftest_expect_frame(pipes[0],0x88,2,sent)||sent[5]!=0||sent[6]!=0)return 114;
 selftest_reply(0,NULL,0);
 char stop[]="STOP";command(stop);if(!selftest_expect_frame(pipes[0],0x82,0,sent))return 115;
 selftest_reply(0,NULL,0);if(!selftest_status_reply(pipes[0],0,0)||armed||stop_check)return 116;
 // A stationary closed-lid start still uses the ordinary cover interlock.
 if(!selftest_info_reply(pipes[0],1,12))return 117;
 char closed[]="START 60 100 0 POT_CONFIRMED";command(closed);
 if(!selftest_expect_frame(pipes[0],0x80,210,sent)||sent[12]!=0x81||sent[41]!=0||sent[42]!=0||lidless_run)return 118;
 selftest_reply(0,NULL,0);if(!selftest_status_reply(pipes[0],1,0))return 119;
 if(selftest_info_reply(pipes[0],0,12)||!stop_check||!selftest_expect_frame(pipes[0],0x82,0,sent))return 120;
 selftest_reply(0,NULL,0);if(!selftest_status_reply(pipes[0],0,0)||armed||stop_check)return 121;
 close(pipes[0]);close(pipes[1]);fd=-1;
 event("selftest","PASS: heat-only START/START_OPEN, APPLY, APPLY_SETTINGS and RESUME encode direction0/speed0, timer preserved, no-op/heat/time/pot/cover guards and STOP ACK/idle; pipes only, no UART opened");return 0;
}
/* UART-free manual mode checks: original mode1 envelope, local-only command. */
static int selftest_manual(void){
 unsigned char p[210];
 const int boundaries[]={1,59,60,601,7199,7200};
 for(unsigned ix=0;ix<sizeof(boundaries)/sizeof(boundaries[0]);ix++)for(int temp=0;temp<=150;temp+=5){int seconds=boundaries[ix];
  if(temp>0&&temp<35)continue;
  for(int speed=-100;speed<=100;speed+=5){
   int expected=(speed!=0||temp>0)&&(abs(speed)<=60||temp<=80);
   if(profile_supports(PROFILE_MANUAL,seconds,temp,speed)!=expected)return 101;
   if(!expected)continue;
   if(make_profile(PROFILE_MANUAL,seconds,temp,speed,p)!=210||p[6]!=1||p[8]!=seconds/60||p[9]!=seconds%60||p[29]!=p[8]||p[30]!=p[9]||p[32]!=temp||p[36]!=motor_direction(speed)||p[37]!=abs(speed)||crc16(p,208)!=((unsigned)p[208]<<8|p[209]))return 102;
  }
 }
 if(profile_supports(PROFILE_MANUAL,7201,150,-40)||profile_supports(PROFILE_MANUAL,1,151,-40)||profile_supports(PROFILE_MANUAL,1,150,-65)||profile_supports(PROFILE_MANUAL,1,0,110))return 103;
 int pipes[2];if(pipe(pipes))return 104;fd=pipes[1];statuses=3;infos=1;mcu_version=12;status_at=info_at=now_ms();state=0;error_code=0;actual_temperature=22;last_ih_status=last_ih_gear=last_motor_status=last_motor_raw=0;cover=1;pot=1;pot_mapping_verified=1;
 char blocked[]="START_MANUAL 7200 150 -40 POT_CONFIRMED";command(blocked);if(armed||pending>=0)return 105;
 cover=0;char no_pot[]="START_MANUAL 7200 150 -40";pot_mapping_verified=0;command(no_pot);if(armed||pending>=0)return 106;
 char start[]="START_MANUAL 7200 150 -40 POT_CONFIRMED";command(start);if(!armed||pending!=0x80||profile_kind!=PROFILE_MANUAL||target_speed!=-40||target_temp!=150)return 107;
 close(fd);close(pipes[0]);fd=-1;puts("PASS manual envelope, mode1 bytes, CRC, closed-lid and pot-confirmation guards");return 0;
}
/* Pipe-only integration proof, returns before root/UART/device discovery. */
static int selftest_pot_check(void){
 unsigned char sent[512],sample[24];int checks=0;
 #define POT_ASSERT(value) do{if(!(value)){fprintf(stderr,"pot check selftest line%d\n",__LINE__);return 200;}checks++;}while(0)
 const int temperatures[]={0,26,39,40,42,60,80,100,150,180,255};
 for(unsigned heat=0;heat<sizeof(temperatures)/sizeof(temperatures[0]);heat++)
 for(int scenario=0;scenario<8;scenario++){
  int pipes[2];if(pipe(pipes))return 201;fd=pipes[1];
  armed=pot_check=stop_check=pending=failed=update_step=quitting=0;pending=-1;stopped_ack=0;
  queued[0]=0;statuses=8;infos=4;mcu_version=12;ih_version=4;motor_version=5;version_confirmations=2;
  state=0;error_code=0;cover=1;cover_mapping_verified=1;actual_temperature=temperatures[heat];
  last_ih_status=last_ih_gear=last_motor_status=last_motor_raw=0;status_at=info_at=now_ms();
  deadline=session_deadline=0;char check[]="CHECK_POT";command(check);
  POT_ASSERT(pot_check&&!armed&&deadline==0&&session_deadline==0);
  POT_ASSERT(selftest_expect_frame(pipes[0],0x80,210,sent));
  POT_ASSERT(sent[12]==0x81&&sent[11]==1&&sent[35]==1);
  for(int i=28;i<208;i++)POT_ASSERT(sent[5+i]==(i==30?1:0));
  char blocked[]="START_MANUAL 60 0 10 POT_CONFIRMED";command(blocked);POT_ASSERT(!armed&&pending==0x80);
  if(scenario==4){pot_due=now_ms();watch_run(now_ms());POT_ASSERT(!pot_window);}
  else{
   selftest_reply(scenario==5?1:0,NULL,0);
   if(scenario!=5){
    POT_ASSERT(pot_ack);
    POT_ASSERT(!send_op(0x20,NULL,0)&&selftest_expect_frame(pipes[0],0x20,0,sent));
    memset(sample,0,24);sample[0]=9;sample[4]=(unsigned char)temperatures[heat];
    selftest_reply(0x21,sample,24);POT_ASSERT(pot_check&&!stop_check&&!armed);
    if(scenario==1||scenario==7){
     pot_started=now_ms()-1500;pot_due=now_ms();pot_samples=30;pot_last_sample=now_ms()-(scenario==7?400:10);
     watch_run(now_ms());POT_ASSERT(pot_window==(scenario==1));
    }else{
     POT_ASSERT(!send_op(0x20,NULL,0)&&selftest_expect_frame(pipes[0],0x20,0,sent));
     memset(sample,0,24);sample[0]=scenario==3?1:9;sample[4]=(unsigned char)temperatures[heat];
     if(scenario==0||scenario==6){sample[0]=8;sample[11]=2;}
     if(scenario==2)sample[6]=1;
     selftest_reply(0x21,sample,24);
    }
   }
  }
  POT_ASSERT(stop_check&&pot_check&&!armed&&pending==0x82);
  POT_ASSERT(selftest_expect_frame(pipes[0],0x82,0,sent));
  if(scenario!=6)selftest_reply(0,NULL,0);else pending=-1;
  POT_ASSERT(!send_op(0x20,NULL,0)&&selftest_expect_frame(pipes[0],0x20,0,sent));
  memset(sample,0,24);sample[4]=(unsigned char)temperatures[heat];selftest_reply(0x21,sample,24);
  POT_ASSERT(scenario==6?pot_check&&stop_check:!pot_check&&!stop_check);
  POT_ASSERT(deadline==0&&session_deadline==0&&!armed);
  close(pipes[0]);close(pipes[1]);fd=-1;
 }
 pot_check=stop_check=0;pending=-1;char check[]="CHECK_POT";
 actual_temperature=-1;command(check);POT_ASSERT(!pot_check&&pending==-1);
 actual_temperature=26;ih_version=3;command(check);POT_ASSERT(!pot_check&&pending==-1);
 ih_version=4;version_confirmations=1;command(check);POT_ASSERT(!pot_check&&pending==-1);
 printf("{\"type\":\"selftest\",\"message\":\"PASS pot check: %d assertions;88 pipe scenarios; temperatures0..255, fixed zero phases, MCU/version/valid-temperature guards, fault2, valid window, output/state abort, missing/rejected ACK, stale telemetry, mandatory STOP ACK; no UART opened\"}\n",checks);
 #undef POT_ASSERT
 return 0;
}
/* Pipe-only motor interval regression: this entry never opens the device UART. */
static int selftest_fry_motor(void){
 int pipes[2];if(pipe(pipes))return 170;fd=pipes[1];unsigned char sent[512];
 statuses=3;infos=2;mcu_version=12;ih_version=4;motor_version=5;version_confirmations=2;
 status_at=info_at=now_ms();cover=0;error_code=0;state=1;armed=1;profile_kind=PROFILE_FRY;
 target_temp=180;target_seconds=30;target_speed=-10;actual_temperature=140;
 deadline=now_ms()+33000;session_deadline=now_ms()+1803000;
 int64_t original_deadline=deadline,original_session=session_deadline;
 char rest[]="FRY_MOTOR 0",move[]="FRY_MOTOR -10";
 command(rest);if(!selftest_expect_frame(pipes[0],0x88,2,sent)||sent[5]||sent[6])return 171;
 selftest_reply(0,NULL,0);
 command(move);if(!selftest_expect_frame(pipes[0],0x88,2,sent)||sent[5]!=2||sent[6]!=10)return 172;
 selftest_reply(0,NULL,0);
 if(deadline!=original_deadline||session_deadline!=original_session||target_temp!=180||target_seconds!=30||phase_paused||update_step)return 173;
 for(int guard=0;guard<12;guard++){
  status_at=info_at=now_ms();state=1;cover=0;armed=1;profile_kind=PROFILE_FRY;update_step=0;phase_paused=0;error_code=0;mcu_version=12;deadline=original_deadline;stop_check=0;
  switch(guard){case 0:state=3;break;case 1:state=9;break;case 2:cover=1;break;case 3:armed=0;break;case 4:profile_kind=PROFILE_NORMAL;break;case 5:update_step=2;break;case 6:phase_paused=1;break;case 7:error_code=20;break;case 8:mcu_version=11;break;case 9:status_at-=1501;break;case 10:info_at-=1201;break;case 11:deadline=now_ms()+STOP_RESERVE_MS;break;}
  command(move);if(pending>=0||queued[0])return 174+guard;
 }
 state=1;cover=0;armed=1;profile_kind=PROFILE_FRY;update_step=phase_paused=error_code=stop_check=0;mcu_version=12;deadline=original_deadline;status_at=info_at=now_ms();
 for(const char **s=(const char*[]){"FRY_MOTOR 10","FRY_MOTOR -20","FRY_MOTOR 0 extra","FRY_MOTOR","FRY_MOTOR 2147483648",NULL};*s;s++){char invalid[80];snprintf(invalid,sizeof(invalid),"%s",*s);command(invalid);if(pending>=0)return 186;}
 pending=0x20;command(move);if(strcmp(queued,move))return 187;
 request_stop("Stopp angefordert",0);if(queued[0]||!stop_check||!selftest_expect_frame(pipes[0],0x82,0,sent))return 188;
 selftest_reply(0,NULL,0);command(move);if(pending>=0)return 189;
 stop_check=0;stopped_ack=0;pending=-1;command(rest);if(!selftest_expect_frame(pipes[0],0x88,2,sent))return 190;
 selftest_reply(1,NULL,0);if(!stop_check||!selftest_expect_frame(pipes[0],0x82,0,sent))return 191;
 close(pipes[0]);close(pipes[1]);fd=-1;
 event("selftest","PASS frying motor-only 88 bytes, unchanged heat/deadlines, 12 state guards, invalid tokens, queued STOP priority and rejected ACK stop; pipes only, no UART opened");return 0;
}
int main(int argc,char **argv) {
 setvbuf(stdout,NULL,_IOLBF,0);
 if(argc==2&&!strcmp(argv[1],"--selftest-fry-motor"))return selftest_fry_motor();
 if(argc==5&&(!strcmp(argv[1],"--profile")||!strcmp(argv[1],"--manual-profile"))){char *end;long a=strtol(argv[2],&end,10);if(*end||a<1||a>7200)return 2;long t=strtol(argv[3],&end,10);if(*end||t<0||t>180)return 2;long s=strtol(argv[4],&end,10);if(*end||s< -100||s>100)return 2;unsigned char p[210];int kind=!strcmp(argv[1],"--manual-profile")?PROFILE_MANUAL:profile_for((int)a,(int)t,(int)s);if(make_profile(kind,(int)a,(int)t,(int)s,p)!=210)return 2;char out[421];hex(p,210,out);puts(out);return 0;}
if(argc==2&&!strcmp(argv[1],"--selftest-manual"))return selftest_manual();
if(argc==2&&!strcmp(argv[1],"--selftest"))return selftest();
 if(argc==2&&!strcmp(argv[1],"--selftest-lidless"))return selftest_lidless();
 if(argc==2&&!strcmp(argv[1],"--selftest-lidless-sequences"))return selftest_lidless_sequences();
 if(argc==2&&!strcmp(argv[1],"--selftest-heat-only"))return selftest_heat_only();
 if(argc==2&&!strcmp(argv[1],"--selftest-pot-check"))return selftest_pot_check();
 if(argc!=2||(strcmp(argv[1],"--probe")&&strcmp(argv[1],"--probe-fast")&&strcmp(argv[1],"--serve"))){fprintf(stderr,"Use --probe, --probe-fast, --serve or --selftest\n");return 2;}
 fast_probe=!strcmp(argv[1],"--probe-fast");probe=fast_probe||!strcmp(argv[1],"--probe");if(getuid()!=0){event("error","Root-Freigabe fuer KitchenOS erforderlich");return 3;}
 int lock=open("/data/local/tmp/kitchenos-control.lock",O_RDWR|O_CREAT|O_CLOEXEC,0600);if(lock<0||flock(lock,LOCK_EX|LOCK_NB)){event("error","Eine KitchenOS-Verbindung ist bereits aktiv");return 4;}
 if(stop_vendor()){event("error","Original-App konnte nicht pausiert werden");return 5;}
 int owner=other_owner();if(owner!=0){printf("{\"type\":\"error\",\"message\":\"UART bereits belegt oder Besitzerpruefung fehlgeschlagen\",\"ownerPid\":%d}\n",owner);return 6;}
 fd=open("/dev/ttyS2",O_RDWR|O_NOCTTY|O_NONBLOCK|O_CLOEXEC);if(fd<0){event("error","UART konnte nicht geoeffnet werden");return 7;}
 if(ioctl(fd,TIOCEXCL)<0){event("error","UART-Exklusivzugriff fehlgeschlagen");close(fd);return 8;}
 struct termios t;if(tcgetattr(fd,&prior)){event("error","UART-Konfiguration nicht lesbar");close(fd);return 9;}saved_termios=1;t=prior;cfmakeraw(&t);cfsetispeed(&t,B115200);cfsetospeed(&t,B115200);t.c_cflag|=CLOCAL|CREAD;t.c_cflag&=~(CSTOPB|PARENB|CRTSCTS);t.c_cflag=(t.c_cflag&~CSIZE)|CS8;t.c_cc[VMIN]=0;t.c_cc[VTIME]=0;
 if(tcsetattr(fd,TCSANOW,&t)){event("error","UART-Konfiguration fehlgeschlagen");close(fd);return 10;}
 signal(SIGTERM,on_signal);signal(SIGINT,on_signal);signal(SIGHUP,on_signal);signal(SIGPIPE,SIG_IGN);
 int64_t begin=now_ms(),last_owner=begin;ping_at=begin;char line[256];size_t ln=0;int stdin_live=!probe;
 printf("{\"type\":\"ready\",\"profileId\":\"%s\",\"lidlessSupported\":true,\"potCheckSupported\":true,\"message\":\"UART exklusiv verbunden; kein Lauf gestartet\"}\n",CONTROL_PROFILE_ID);

 while(!failed){
  int64_t now=now_ms();
  if(interrupted){if(owns_control()&&!stop_check)request_stop("App oder Verbindung beendet",1);else quitting=1;stdin_live=0;interrupted=0;}
  if(!probe&&stdin_live&&now-ping_at>1500){stdin_live=0;if(owns_control())request_stop("Bedienoberflaeche antwortet nicht",1);else quitting=1;}
  watch_run(now);
  if(stop_check&&now-stop_at>1800){if(stop_attempts<3){stop_at=now;pending=-1;send_op(stop_opcode(),NULL,0);stop_attempts++;}else{event("error","Stopp nicht physisch bestaetigt - Maschine am Netzschalter sichern");failed=1;}}
  if(quitting&&!owns_control()&&!stop_check)break;
  if(now-last_owner>500){last_owner=now;if(other_owner()!=0){event("error","Zweiter UART-Besitzer erkannt");if(owns_control())request_stop("UART-Konflikt",1);else{failed=1;break;}}}
  if(pending>=0&&now-sent_at>(pot_check&&!stop_check?180:650)){int op=pending;pending=-1;update_step=0;if(pot_check&&!stop_check)request_stop("Topfpruefung: Antwort fehlt",0);else if(op>=0x80&&op!=stop_opcode()&&owns_control())request_stop("Keine Befehlsbestaetigung",0);}
  if(pending<0&&queued[0]){char next[256];snprintf(next,sizeof(next),"%s",queued);queued[0]=0;if(now-queued_at<800)command(next);else event("error","Befehl verworfen: zu lange gewartet");}
  if(pending<0&&(!pot_check||stop_check||(now>=pot_poll_after&&pot_due-now>=25))){
   if(turbo_preparing&&turbo_prepare_ack&&turbo_saw_check&&state==10&&!stop_check)advance_turbo(now);else if(update_step)advance_update();else if(!pot_check&&now-info_at>500&&!stop_check)send_op(0x22,NULL,0);
   else if(status_due(now))send_op(0x20,NULL,0);
  }
  struct pollfd f[2]={{fd,POLLIN,0},{stdin_live?STDIN_FILENO:-1,POLLIN|POLLHUP,0}};int result=poll(f,2,transport_wait_ms(now_ms()));
  if(result<0&&errno!=EINTR){event("error","Transport-Poll fehlgeschlagen");if(owns_control())request_stop("Transportfehler",1);else failed=1;}
  if(f[0].revents&POLLIN){unsigned char data[512];ssize_t n=read(fd,data,sizeof(data));if(n>0){if(probe){char raw[1025];hex(data,(size_t)n,raw);printf("{\"type\":\"rxbytes\",\"raw\":\"%s\"}\n",raw);}receive_bytes(data,(size_t)n);}}
  if(stdin_live&&(f[1].revents&(POLLIN|POLLHUP))){char data[128];ssize_t n=read(STDIN_FILENO,data,sizeof(data));if(n<=0){stdin_live=0;if(owns_control())request_stop("Bedienverbindung geschlossen",1);else quitting=1;}else for(ssize_t i=0;i<n;i++){if(data[i]=='\n'){line[ln]=0;command(line);ln=0;}else if(data[i]!='\r'){if(ln<sizeof(line)-1)line[ln++]=data[i];else{ln=0;event("error","Befehl zu lang");}}}}
  if(probe&&now-begin>3000)break;
  if(!probe&&statuses<3&&now-begin>5000){event("error","Keine bestaetigte MCU-Kommunikation");failed=1;}
 }
 if(owns_control()&&!stop_check)request_stop("Transport wird nach Fehler beendet",1);
 if(owns_control()||stop_check)event("error","Kein bestaetigter Leerlauf - physischen Zustand pruefen");
 if(saved_termios)tcsetattr(fd,TCSANOW,&prior);ioctl(fd,TIOCNXCL);close(fd);close(lock);
 printf("{\"type\":\"closed\",\"statusSamples\":%d,\"infoSamples\":%d,\"confirmedIdle\":%s}\n",statuses,infos,(statuses>0&&(state==0||state==11)&&!owns_control()&&!stop_check&&last_ih_status==0&&last_ih_gear==0&&last_motor_status==0&&last_motor_raw==0)?"true":"false");
 return failed||(probe&&(statuses<3||infos<1))?1:0;
}
