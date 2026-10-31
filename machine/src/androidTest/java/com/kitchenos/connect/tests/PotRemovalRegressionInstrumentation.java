package com.kitchenos.connect.tests;

import android.app.*;
import android.os.*;
import android.content.Context;
import com.kitchenos.connect.control.*;
import com.kitchenos.connect.ui.HomeVesselSignal;
import java.io.*;
import java.lang.reflect.*;
import org.json.JSONObject;

/** Regression replay through the real parser/controller; no UART or device process. */
public final class PotRemovalRegressionInstrumentation extends Instrumentation {
    private ControlClient client;
    private final StringWriter commands=new StringWriter();
    private final StringBuilder report=new StringBuilder();
    private int samples=10,checks,temp=26,cover=1;
    private long diagnosticId;
    private boolean owned,staleInfo;
    private Object monitor=new Object();
    interface Work {void run() throws Exception;}
    private static Field field(String name)throws Exception{Field f=ControlClient.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private void ui(Work work){Throwable[] fail={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){fail[0]=e;}});if(fail[0]!=null)throw new AssertionError(fail[0]);}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;report.append("PASS ").append(label).append('\n');}
    private void packet(JSONObject value)throws Exception{Method m=ControlClient.class.getDeclaredMethod("message",String.class,long.class);m.setAccessible(true);m.invoke(client,value.toString(),SystemClock.elapsedRealtime());}
    private void sample(int grams)throws Exception{ui(()->{client.state.infoAt=staleInfo?-1:SystemClock.elapsedRealtime();packet(new JSONObject().put("type","status").put("state",owned?1:0).put("temperature",temp).put("coverOpen",cover).put("potPresent",-1).put("error",0).put("samples",++samples).put("motorRaw",owned?30:0).put("weightRaw",grams).put("elapsed",0).put("coverMappingVerified",true).put("potMappingVerified",false).put("armed",owned).put("ihStatus",owned?1:0).put("ihGear",owned?80:0).put("motorStatus",owned?1:0));});}
    private void feed(int grams,int millis)throws Exception{long end=SystemClock.elapsedRealtime()+millis;do{sample(grams);SystemClock.sleep(40);}while(SystemClock.elapsedRealtime()<end);sample(grams);}
    private int sent(){int n=0;for(String line:commands.toString().split("\n"))if(line.equals("CHECK_POT"))n++;return n;}
    private void reset()throws Exception{ui(()->{Constructor<ControlClient> c=ControlClient.class.getDeclaredConstructor(Context.class);c.setAccessible(true);client=c.newInstance(getTargetContext());field("writer").set(client,new BufferedWriter(commands));commands.getBuffer().setLength(0);field("automaticPotPending").setBoolean(client,false);client.state.potCheck.closedInterlock();ControlState s=client.state;s.foreground=s.rootVerified=s.connected=s.coverVerified=s.potCheckSupported=s.lidlessSupported=s.idleConfirmed=true;s.deviceFaultLatched=false;s.lastDeviceError=0;s.mcuVersion=12;s.ihVersion=4;s.motorVersion=5;s.potVersionSamples=2;client.potMonitoring(monitor,true);});temp=26;cover=1;owned=false;feed(1850,1000);}
    private void result(String outcome)throws Exception{ui(()->{diagnosticId++;packet(new JSONObject().put("type","potCheck").put("stage","checking").put("id",diagnosticId));packet(new JSONObject().put("type","stopped").put("diagnostic",true).put("potCheckId",diagnosticId));packet(new JSONObject().put("type","potCheck").put("stage","complete").put("id",diagnosticId).put("outcome",outcome).put("confirmedIdle",true).put("startAcked",true).put("phaseSamples",32).put("error",outcome.equals("missing")?2:0));});}
    private void closedReplies()throws Exception{ui(()->{for(int i=0;i<2;i++)packet(new JSONObject().put("type","info").put("version",12).put("ihVersion",4).put("motorVersion",5).put("switches",128));});}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Throwable failure=null;try{
        if(!Build.HARDWARE.contains("ranchu"))throw new IllegalStateException("Emulator only; memory transport required");
        for(int heat:new int[]{0,39,40,42,60,80,100,150,180,255}){
            reset();temp=heat;feed(1850,850);feed(0,2200);
            check(client.state.potCheck.required()&&sent()==1,heat+"C removal dispatches immediately after settling without cooling");
            check(!client.state.safe(SystemClock.elapsedRealtime(),true)&&!client.state.potMissing(),"Diagnostic blocks cooking without treating weight as a pot sensor");
            check(!HomeVesselSignal.from(client.state,SystemClock.elapsedRealtime()).description().contains("abkühlen"),"Warm diagnostic never asks to cool");
            feed(0,250);check(sent()==1,"Pending diagnostic cannot be duplicated");
            result("missing");sample(0);check(client.state.potMissing(),heat+"C missing receipt is applied");
            feed(1850,2300);check(sent()==2,heat+"C open return starts a new diagnostic without cooling");
            result("clear_window");sample(1850);check(!client.state.potMissing()&&!client.state.potCheck.required(),heat+"C stopped clear result restores vessel");
            check(client.state.safe(SystemClock.elapsedRealtime(),true),heat+"C successful return releases pot-related start block");
        }
        reset();owned=true;temp=95;feed(1850,250);owned=false;feed(0,2200);
        check(client.state.potCheck.required()&&sent()==1,"Removal immediately after hot cooking retains reference and starts diagnostic");
        reset();SystemClock.sleep(650);feed(0,1000);check(sent()==0,"Telemetry gap starts a new full quiet window");feed(0,1300);
        check(sent()==1,"A 650ms telemetry gap cannot erase removal");
        reset();staleInfo=true;feed(0,1300);check(sent()==0,"Missing info cannot submit a diagnostic");staleInfo=false;feed(0,1000);check(sent()==0,"Info recovery restarts the full quiet window");feed(0,1300);check(sent()==1,"Fresh data after interruption still compares against the old load");
        reset();temp=42;feed(2147,2300);check(!client.state.potCheck.required()&&sent()==0,"Hot lid/cup-only change does not request a check");
        reset();temp=42;ui(()->client.potTouch(monitor,true));feed(0,2200);check(!client.state.potCheck.required(),"Held touch cannot become a warm removal");ui(()->client.potTouch(monitor,false));feed(0,1000);check(!client.state.potCheck.required(),"Touch release restarts settling");feed(0,1300);check(client.state.potCheck.required()&&sent()==1,"Settled warm removal after touch starts diagnostic");result("missing");
        cover=0;feed(1850,100);closedReplies();check(!client.state.potCheck.required()&&sent()==1,"Two closed interlock replies resolve a warm return passively");
        check(!commands.toString().contains("START")&&!commands.toString().contains("APPLY"),"No cooking commands are emitted");
    }catch(Throwable e){failure=e;}Bundle result=new Bundle();result.putString("stream",report+(failure==null?"\n"+checks+" pot-removal regression checks passed. Memory transport only.\n":"FAIL "+failure+"\n"));finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
