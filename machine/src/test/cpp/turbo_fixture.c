/* Offline pipe tests. main never calls bridge_main, so no root probe or UART can open. */
#define main bridge_main
#include "../../main/cpp/bridge.c"
#undef main
#include <assert.h>
static int reader,checks;
#define CHECK(value) do{if(!(value)){fprintf(stderr,"FAIL line%d: %s\n",__LINE__,#value);return 1;}checks++;}while(0)
static void ready_fixture(void){
 if(fd>=0)close(fd);if(reader>=0)close(reader);int pair[2];assert(!pipe(pair));reader=pair[0];fd=pair[1];fcntl(reader,F_SETFL,O_NONBLOCK);
 seq=0;pending=-1;probe=armed=quitting=stop_check=stop_attempts=failed=stopped_ack=update_step=0;statuses=10;infos=10;cover_mapping_verified=1;pot_mapping_verified=0;cover=0;state=0;error_code=0;actual_temperature=20;last_ih_status=last_ih_gear=last_motor_status=last_motor_raw=0;queued[0]=0;profile_kind=0;session_deadline=deadline=0;status_at=info_at=ping_at=now_ms();
}
static void issue(const char *text){char command_line[256];snprintf(command_line,sizeof(command_line),"%s",text);command(command_line);}
static int output(unsigned char *out){return (int)read(reader,out,512);}
static void incoming(int op,const unsigned char *data,size_t size){unsigned char response[256];size_t n=frame(pending_seq,op,data,size,response);unsigned char temp=response[n-1];response[n-1]=response[n-2];response[n-2]=temp;receive_bytes(response,n);}
static void turbo_fixture(void){
 ready_fixture();turbo_context=turbo_preparing=turbo_prepare_ack=turbo_saw_check=turbo_saw_running=0;
 turbo_prepare_due=0;pot_check=phase_paused=lidless_run=0;rxn=0;
 mcu_version=12;ih_version=4;motor_version=5;version_confirmations=2;
}
static void status_reply(int next,int temp,int heater,int motor){
 unsigned char d[24]={0};d[0]=next;d[4]=temp;d[2]=heater;d[3]=heater;d[6]=motor;d[12]=motor;
 pending=0x20;pending_seq=++seq;incoming(0x21,d,24);
}
static void prepare(void){
 incoming(0,NULL,0);status_reply(9,20,0,0);status_reply(10,20,0,0);advance_turbo(now_ms());
}
int main(void){reader=-1;unsigned char wire[512],base[210];
 for(int seconds=1;seconds<=2;seconds++)for(int speed=100;speed<=120;speed+=10){
  turbo_fixture();char cmd[100];snprintf(cmd,sizeof(cmd),"START_TURBO %d 0 %d POT_CONFIRMED",seconds,speed);issue(cmd);
  CHECK(armed&&turbo_preparing&&turbo_context&&pending==0x89&&deadline==0&&remaining_seconds()==seconds);
  CHECK(output(wire)==7&&wire[2]==0x89);int64_t prep=turbo_prepare_due;CHECK(prep-start_at==9000);
  /* No profile until ACK and a complete readiness transition. */
  state=10;advance_turbo(now_ms());CHECK(output(wire)<0&&turbo_preparing);
  incoming(0,NULL,0);advance_turbo(now_ms());CHECK(output(wire)<0&&turbo_preparing);
  status_reply(9,20,0,0);CHECK(!stop_check&&turbo_saw_check);
  status_reply(10,20,0,0);advance_turbo(now_ms());CHECK(output(wire)==217&&wire[2]==0x80&&!turbo_preparing);
  CHECK(deadline-start_at==seconds*1000&&session_deadline==deadline);
  CHECK(wire[5]==7&&wire[11]==7&&wire[13]==0&&wire[14]==seconds&&wire[35]==seconds&&wire[37]==0&&wire[41]==1&&wire[42]==speed);
  CHECK(crc16(wire,215)==((unsigned)wire[215]<<8|wire[216]));CHECK(crc16(wire+5,208)==((unsigned)wire[213]<<8|wire[214]));
  CHECK(unhex(STRONG_HEX,base,210)==210);
  for(int i=0;i<208;i++)if(i!=0&&i!=6&&i!=8&&i!=9&&i!=29&&i!=30&&i!=32&&i!=36&&i!=37)CHECK(wire[5+i]==base[i]);
  incoming(0,NULL,0);status_reply(1,20,0,1);CHECK(!stop_check&&turbo_saw_running);
  unsigned char active[24]={1,0,1,0,20,0,1,0,0,0,0,0,120};pending=0x20;pending_seq=++seq;incoming(0x21,active,24);CHECK(!stop_check&&last_ih_status==1&&last_ih_gear==0);
  int64_t budget=deadline;
  for(const char **bad=(const char*[]){"PAUSE","RESUME 2 0 120 POT_CONFIRMED","APPLY 2 0 120","APPLY_SETTINGS 0 110","START_TURBO 2 0 120 POT_CONFIRMED",NULL};*bad;bad++){issue(*bad);CHECK(output(wire)<0&&!update_step&&deadline==budget&&!phase_paused);}
  status_at=info_at=deadline-1;watch_run(deadline-1);CHECK(!stop_check);
  watch_run(deadline);CHECK(stop_check&&pending==0x8a&&output(wire)==7&&wire[2]==0x8a&&!turbo_preparing);
  incoming(0,NULL,0);CHECK(stopped_ack);status_reply(10,20,0,0);CHECK(stop_check&&armed);
  status_reply(0,20,0,0);CHECK(!armed&&!stop_check&&!turbo_context);advance_turbo(now_ms());CHECK(output(wire)<0);
 }
 const char *bad[]={"START_TURBO 0 0 120 POT_CONFIRMED","START_TURBO 3 0 120 POT_CONFIRMED","START_TURBO 2 1 120 POT_CONFIRMED","START_TURBO 2 0 130 POT_CONFIRMED","START_TURBO 2 0 -120 POT_CONFIRMED","START_TURBO 2 0 105 POT_CONFIRMED","START_TURBO 2 0 120","START_TURBO 2 0 120 POT_CONFIRMED extra","START_TURBO 4294967298 0 120 POT_CONFIRMED","START 2 0 120 POT_CONFIRMED","START_MANUAL 2 0 120 POT_CONFIRMED","START_OPEN 2 0 120 POT_CONFIRMED",NULL};
 for(int i=0;bad[i];i++){turbo_fixture();issue(bad[i]);CHECK(!armed&&pending<0&&output(wire)<0);}
 for(int guard=0;guard<12;guard++){
  turbo_fixture();switch(guard){case 0:mcu_version=11;break;case 1:ih_version=3;break;case 2:motor_version=6;break;case 3:version_confirmations=1;break;case 4:cover=1;break;case 5:actual_temperature=60;break;case 6:last_ih_status=1;break;case 7:last_motor_raw=1;break;case 8:status_at-=500;break;case 9:info_at-=1200;break;case 10:error_code=2;break;case 11:pot_mapping_verified=1;pot=0;break;}
  issue("START_TURBO 2 0 120 POT_CONFIRMED");CHECK(!armed&&output(wire)<0);
 }
 /* Cancellation before ACK, during check, at readiness and during the pulse. */
 for(int phase=0;phase<4;phase++)for(int quit=0;quit<2;quit++){
  turbo_fixture();issue("START_TURBO 2 0 120 POT_CONFIRMED");CHECK(output(wire)==7);int old_seq=pending_seq;
  if(phase>0){incoming(0,NULL,0);status_reply(9,20,0,0);}if(phase>1)status_reply(10,20,0,0);
  if(phase>2){advance_turbo(now_ms());CHECK(output(wire)==217);old_seq=pending_seq;}
  issue(quit?"QUIT":"STOP");CHECK(stop_check&&!turbo_preparing&&output(wire)==7&&wire[2]==0x8a&&quitting==quit);
  /* Late old ACK cannot consume exit ACK or arm a profile. */
  unsigned char ack[7];frame(old_seq,0,NULL,0,ack);unsigned char h=ack[5];ack[5]=ack[6];ack[6]=h;receive_bytes(ack,7);CHECK(!stopped_ack&&pending==0x8a);
  incoming(0,NULL,0);status_reply(10,20,0,0);advance_turbo(now_ms());CHECK(output(wire)<0&&stop_check);
  status_reply(0,20,0,0);CHECK(!armed&&!turbo_context);status_reply(10,20,0,0);advance_turbo(now_ms());CHECK(output(wire)<0&&!armed);
 }
 for(int fault=0;fault<8;fault++){
  turbo_fixture();issue("START_TURBO 2 0 120 POT_CONFIRMED");CHECK(output(wire)==7);incoming(0,NULL,0);status_reply(9,20,0,0);
  switch(fault){case 0:status_at=info_at=turbo_prepare_due;watch_run(turbo_prepare_due);break;case 1:status_at=now_ms()-1501;watch_run(now_ms());break;case 2:info_at=now_ms()-1201;watch_run(now_ms());break;case 3:status_reply(9,60,0,0);break;case 4:status_reply(9,20,1,0);break;case 5:status_reply(9,20,0,1);break;case 6:cover=1;status_reply(9,20,0,0);break;case 7:error_code=1;status_reply(8,20,0,0);break;}
  CHECK(stop_check&&!turbo_preparing&&output(wire)==7&&wire[2]==0x8a);
 }
 turbo_fixture();issue("START_TURBO 1 0 100 POT_CONFIRMED");CHECK(output(wire)==7);prepare();CHECK(output(wire)==217);incoming(0,NULL,0);status_reply(1,20,0,1);status_reply(10,20,0,0);CHECK(stop_check&&output(wire)==7&&wire[2]==0x8a);
 /* CRC/sequence mismatch cannot create ready or accept the prepare ACK. */
 turbo_fixture();issue("START_TURBO 2 0 120 POT_CONFIRMED");CHECK(output(wire)==7);unsigned char corrupt[7];frame(pending_seq,0,NULL,0,corrupt);corrupt[5]^=1;receive_bytes(corrupt,7);CHECK(!turbo_prepare_ack&&pending==0x89);
 incoming(1,NULL,0);CHECK(stop_check&&!turbo_preparing&&output(wire)==7&&wire[2]==0x8a);
 /* An accepted profile that remains ready10 is no successful motor pulse. */
 turbo_fixture();issue("START_TURBO 1 0 120 POT_CONFIRMED");CHECK(output(wire)==7);prepare();CHECK(output(wire)==217);incoming(0,NULL,0);status_reply(10,20,0,0);CHECK(!turbo_saw_running&&!stop_check);
 int capture[2];CHECK(pipe(capture)==0);int saved_stdout=dup(STDOUT_FILENO);CHECK(saved_stdout>=0);fflush(stdout);CHECK(dup2(capture[1],STDOUT_FILENO)>=0);close(capture[1]);
 status_at=info_at=deadline;watch_run(deadline);fflush(stdout);CHECK(dup2(saved_stdout,STDOUT_FILENO)>=0);close(saved_stdout);
 char message[1024]={0};CHECK(read(capture[0],message,sizeof(message)-1)>0);close(capture[0]);CHECK(strstr(message,"Turbo: Motorstart nicht bestaetigt")!=NULL&&strstr(message,"Kochzeit beendet")==NULL);
 CHECK(stop_check&&output(wire)==7&&wire[2]==0x8a);incoming(0,NULL,0);status_reply(0,20,0,0);CHECK(!armed&&!turbo_context);
 /* Ready status with stale info must refresh read-only before a pulse. */
 turbo_fixture();issue("START_TURBO 2 0 120 POT_CONFIRMED");CHECK(output(wire)==7);incoming(0,NULL,0);status_reply(9,20,0,0);status_reply(10,20,0,0);info_at=now_ms()-201;advance_turbo(now_ms());CHECK(output(wire)==7&&wire[2]==0x22&&turbo_preparing);
 unsigned char info[20]={12,4,0,5};info[18]=0x80;incoming(0x23,info,20);advance_turbo(now_ms());CHECK(output(wire)==217&&!turbo_preparing);
 /* Wrong firmware discovered during prep, not just at the original start. */
 turbo_fixture();issue("START_TURBO 2 0 120 POT_CONFIRMED");CHECK(output(wire)==7);incoming(0,NULL,0);pending=0x22;pending_seq=++seq;info[0]=13;incoming(0x23,info,20);CHECK(stop_check&&output(wire)==7&&wire[2]==0x8a);
 /* STOP cancels the generic read-response queue before Strong was entered. */
 turbo_fixture();pending=0x20;pending_seq=1;issue("START_TURBO 2 0 120 POT_CONFIRMED");CHECK(queued[0]&&!armed&&output(wire)<0);issue("STOP");CHECK(!queued[0]&&output(wire)==7&&wire[2]==0x82);status_reply(10,20,0,0);advance_turbo(now_ms());CHECK(!armed&&output(wire)<0);
 /* Real monotonic time, missing pulse ACK: deadline cuts permission at 1s/2s.
  * Pipe transport only; fresh fixture telemetry avoids unrelated watchdogs. */
 for(int sec=1;sec<=2;sec++){
  turbo_fixture();char cmd[100];snprintf(cmd,sizeof(cmd),"START_TURBO %d 0 120 POT_CONFIRMED",sec);issue(cmd);CHECK(output(wire)==7);prepare();CHECK(output(wire)==217);int64_t began=start_at;
  while(now_ms()<deadline){status_at=info_at=now_ms();watch_run(now_ms());if(stop_check)break;usleep(1000);}
  status_at=info_at=now_ms();watch_run(now_ms());int64_t ended=now_ms();CHECK(stop_check&&pending==0x8a&&output(wire)==7&&wire[2]==0x8a);CHECK(ended-began>=sec*1000&&ended-began<=sec*1000+100);
  printf("{\"pulseMs\":%d,\"exitSentAfterMs\":%lld,\"unackedPulse\":true,\"transport\":\"pipe\"}\n",sec*1000,(long long)(ended-began));
 }
 close(reader);close(fd);printf("{\"checks\":%d,\"result\":\"PASS\",\"transport\":\"pipe only; no UART or physical device\"}\n",checks);return 0;
}
