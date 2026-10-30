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
int main(void){reader=-1;unsigned char wire[512],data[24]={0};
 ready_fixture();issue("START 660 100 -5 POT_CONFIRMED");CHECK(armed&&profile_kind==PROFILE_NORMAL);CHECK(output(wire)==217&&wire[2]==0x80&&wire[5+8]==11&&wire[5+9]==0&&wire[5+29]==11);CHECK(deadline-start_at==663000&&session_deadline-start_at==7203000);
 incoming(0,NULL,0);state=1;session_deadline--;int64_t budget=session_deadline;issue("APPLY 600 95 -10");CHECK(update_step==1&&session_deadline==budget&&deadline<=now_ms()+603000);update_step=0;pending=-1;issue("APPLY 10 0 100");CHECK(pending==-1&&!update_step&&profile_kind==PROFILE_NORMAL);issue("APPLY 7200 95 -5");CHECK(!update_step&&session_deadline==budget); // elapsed budget never renews
 state=3;issue("RESUME 600 95 -10 POT_CONFIRMED");CHECK(output(wire)==12&&wire[2]==0x85&&wire[5]==10&&wire[6]==0);CHECK(session_deadline==budget);incoming(0,NULL,0);
 issue("STOP");CHECK(stop_check&&output(wire)==7&&wire[2]==0x82&&!update_step);incoming(0,NULL,0);CHECK(stopped_ack);
 pending=0x20;pending_seq=77;data[0]=11;data[4]=20;data[12]=1;incoming(0x21,data,24);CHECK(armed&&stop_check);pending=0x20;pending_seq=78;data[12]=0;incoming(0x21,data,24);CHECK(!armed&&!stop_check&&deadline==0);
 ready_fixture();issue("START 10 0 100 POT_CONFIRMED");CHECK(armed&&profile_kind==PROFILE_MOTOR&&output(wire)==217);incoming(0,NULL,0);state=1;issue("APPLY 10 0 20");CHECK(update_step==1&&profile_kind==PROFILE_MOTOR);update_step=0;issue("APPLY 10 35 20");CHECK(!update_step&&profile_kind==PROFILE_MOTOR);
 pending=0x20;pending_seq=22;memset(data,0,24);data[0]=1;data[4]=60;incoming(0x21,data,24);CHECK(stop_check&&output(wire)==7&&wire[2]==0x82);
 ready_fixture();actual_temperature=60;issue("START 10 0 100 POT_CONFIRMED");CHECK(!armed&&output(wire)<0);
 ready_fixture();issue("START 1800 180 -10 POT_CONFIRMED");CHECK(armed&&profile_kind==PROFILE_FRY&&output(wire)==217);CHECK(wire[5+6]==5&&wire[5+32]==180&&wire[5+33]==120&&wire[5+41]==128);CHECK(deadline-start_at==1803000&&session_deadline==deadline);incoming(0,NULL,0);state=1;issue("APPLY 10 95 -10");CHECK(!update_step);issue("APPLY 10 150 -10");CHECK(update_step==1);issue("STOP");CHECK(!update_step&&stop_check&&output(wire)==7);
 for(const char **bad=(const char*[]){"MOTOR_TEST 100 WATER_CONFIRMED","HIGH_TEST 180 SETUP_CONFIRMED","POWER_TEST 120 POT_CONFIRMED","START 4294967301 0 5 POT_CONFIRMED","START 5 0 4294967301 POT_CONFIRMED","START 7201 0 5 POT_CONFIRMED","START 301 0 100 POT_CONFIRMED","START 5 0 -100 POT_CONFIRMED","START 5 180 100 POT_CONFIRMED",NULL};*bad;bad++){ready_fixture();issue(*bad);CHECK(!armed&&pending==-1&&output(wire)<0);}
 ready_fixture();info_at=now_ms()-300;issue("START 10 0 100 POT_CONFIRMED");CHECK(!armed&&queued[0]&&pending==0x22&&output(wire)==7);issue("STOP");CHECK(!queued[0]&&stop_check);
 ready_fixture();last_ih_status=1;issue("START 10 0 5 POT_CONFIRMED");CHECK(!armed&&output(wire)<0);
 ready_fixture();issue("START 1 0 5 POT_CONFIRMED");CHECK(armed&&output(wire)==217);incoming(0,NULL,0);state=1;issue("PAUSE");CHECK(output(wire)==7&&pending==0x81&&!phase_paused);incoming(0,NULL,0);CHECK(phase_paused);int64_t now=now_ms();deadline=now-1;watch_run(now);CHECK(!stop_check);session_deadline=now-1;watch_run(now);CHECK(stop_check&&output(wire)==7);
 ready_fixture();issue("START 60 0 100 POT_CONFIRMED");CHECK(output(wire)==217);incoming(0,NULL,0);state=1;int64_t unchanged_deadline=deadline,unchanged_budget=session_deadline;issue("APPLY_SETTINGS 0 90");CHECK(update_step==2&&deadline==unchanged_deadline&&session_deadline==unchanged_budget);advance_update();CHECK(output(wire)==8&&wire[2]==0x87&&wire[5]==0);incoming(0,NULL,0);advance_update();CHECK(output(wire)==9&&wire[2]==0x88&&wire[5]==1&&wire[6]==90);incoming(0,NULL,0);CHECK(!update_step&&pending==-1&&deadline==unchanged_deadline);
 issue("APPLY_SETTINGS 0 80");advance_update();CHECK(output(wire)==8&&wire[2]==0x87);issue("STOP");CHECK(output(wire)==7&&wire[2]==0x82&&!update_step);CHECK(deadline==unchanged_deadline&&session_deadline==unchanged_budget);
 ready_fixture();CHECK(status_interval_ms()==50);int64_t at=status_at;CHECK(transport_wait_ms(at+40)==10&&transport_wait_ms(at+50)==0);CHECK(!status_due(at+49)&&status_due(at+50));
 armed=1;CHECK(status_interval_ms()==100);CHECK(!status_due(at+100)&&status_due(at+101)&&transport_wait_ms(at+99)==20);armed=0;state=1;CHECK(status_interval_ms()==100);state=11;CHECK(status_interval_ms()==50);
 last_motor_raw=5;CHECK(status_interval_ms()==100);last_motor_raw=0;last_ih_gear=1;CHECK(status_interval_ms()==100);last_ih_gear=0;error_code=1;CHECK(status_interval_ms()==100);error_code=0;
 probe=1;CHECK(status_interval_ms()==100);fast_probe=1;CHECK(status_interval_ms()==50);stop_check=1;CHECK(status_interval_ms()==100);
 close(reader);close(fd);printf("{\"checks\":%d,\"result\":\"PASS\",\"transport\":\"pipe only\"}\n",checks);return 0;
}
