/* Offline pipe transport only: this main cannot open the appliance UART. */
#define main bridge_main
#include "../../main/cpp/bridge.c"
#undef main
#include <assert.h>
static int reader=-1,checks;
#define CHECK(value) do{if(!(value)){fprintf(stderr,"FAIL line%d: %s\n",__LINE__,#value);return 1;}checks++;}while(0)
static void ready(void){
 if(fd>=0)close(fd);if(reader>=0)close(reader);int pair[2];assert(!pipe(pair));reader=pair[0];fd=pair[1];fcntl(reader,F_SETFL,O_NONBLOCK);
 seq=0;pending=-1;probe=armed=quitting=stop_check=stop_attempts=failed=stopped_ack=update_step=0;statuses=infos=10;cover_mapping_verified=1;pot_mapping_verified=0;cover=state=error_code=0;actual_temperature=55;last_ih_status=last_ih_gear=last_motor_status=last_motor_raw=0;queued[0]=0;profile_kind=0;session_deadline=deadline=0;status_at=info_at=ping_at=now_ms();pot_check=phase_paused=lidless_run=rxn=turbo_context=turbo_preparing=0;
}
static void issue(const char *text){char line[256];snprintf(line,sizeof(line),"%s",text);command(line);}
static int output(unsigned char *out){return (int)read(reader,out,512);}
static void incoming(int op,const unsigned char *data,size_t size){unsigned char response[256];size_t n=frame(pending_seq,op,data,size,response);unsigned char temp=response[n-1];response[n-1]=response[n-2];response[n-2]=temp;receive_bytes(response,n);}
static void status_reply(int temp,int heater){unsigned char d[24]={0};d[0]=1;d[4]=temp;d[2]=heater;d[3]=heater;d[6]=1;d[12]=100;pending=0x20;pending_seq=++seq;incoming(0x21,d,24);}
int main(void){unsigned char wire[512];
 for(int actual=55;actual<=81;actual++)for(int temp=55;temp<=95;temp+=5)for(int speed=-100;speed<=100;speed+=5){
  ready();actual_temperature=actual;char cmd[100];snprintf(cmd,sizeof(cmd),"START_MANUAL 300 %d %d POT_CONFIRMED",temp,speed);issue(cmd);
  int allowed=(temp<=80&&actual<=80)||abs(speed)<=60;
  CHECK(!!armed==allowed);
  if(allowed){CHECK(output(wire)==217&&wire[2]==0x80&&wire[37]==temp&&wire[42]==abs(speed)&&wire[41]==motor_direction(speed));CHECK(crc16(wire,215)==((unsigned)wire[215]<<8|wire[216]));}
  else CHECK(output(wire)<0&&pending<0);
 }
 ready();issue("START_MANUAL 300 55 100 POT_CONFIRMED");CHECK(output(wire)==217);incoming(0,NULL,0);status_reply(80,1);CHECK(armed&&!stop_check);status_reply(81,1);CHECK(stop_check&&output(wire)==7&&wire[2]==0x82);
 ready();actual_temperature=95;issue("START_MANUAL 300 95 60 POT_CONFIRMED");CHECK(output(wire)==217);incoming(0,NULL,0);status_reply(95,1);CHECK(armed&&!stop_check);
 ready();issue("START_MANUAL 300 55 40 POT_CONFIRMED");CHECK(output(wire)==217);incoming(0,NULL,0);status_reply(55,1);int64_t budget=session_deadline;issue("APPLY_SETTINGS 55 100");CHECK(update_step==2&&target_speed==100&&target_temp==55&&session_deadline==budget&&!stop_check);
 for(int guard=0;guard<7;guard++){
  ready();switch(guard){case 0:cover=1;break;case 1:actual_temperature=-1;break;case 2:last_ih_gear=1;break;case 3:status_at-=1501;break;case 4:info_at-=1201;break;case 5:error_code=2;break;case 6:pot_mapping_verified=1;pot=0;break;}
  issue("START_MANUAL 300 55 100 POT_CONFIRMED");CHECK(!armed&&output(wire)<0);
 }
 ready();issue("START_MANUAL 300 55 100");CHECK(!armed&&output(wire)<0);
 ready();issue("START_MANUAL 300 81 100 POT_CONFIRMED");CHECK(!armed&&output(wire)<0);
 ready();actual_temperature=60;issue("START 60 0 100 POT_CONFIRMED");CHECK(!armed&&output(wire)<0);
 ready();issue("START_MANUAL 300 55 100 POT_CONFIRMED");CHECK(output(wire)==217);incoming(0,NULL,0);status_reply(55,1);watch_run(status_at+1501);CHECK(stop_check&&output(wire)==7&&wire[2]==0x82);
 close(reader);close(fd);printf("{\"checks\":%d,\"result\":\"PASS\",\"transport\":\"pipe only; no UART\"}\n",checks);return 0;
}
