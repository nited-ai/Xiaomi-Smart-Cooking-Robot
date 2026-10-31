package com.kitchenos.connect.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import com.kitchenos.connect.control.*;
import com.kitchenos.connect.ui.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;

/** Real Home/controller path with memory-only transport. Refuses physical devices. */
public final class PotAutomationInstrumentation extends Instrumentation {
    private static final class MemoryProcess extends java.lang.Process {
        public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
        public InputStream getInputStream(){return new ByteArrayInputStream(new byte[0]);}
        public InputStream getErrorStream(){return new ByteArrayInputStream(new byte[0]);}
        public int waitFor(){return 0;}public int exitValue(){return 0;}public void destroy(){}
    }
    private Activity activity;private HomeNumberControls home;private HomeVesselWebGLView vessel;private ControlClient client,previous;
    private final StringWriter commands=new StringWriter();private final StringBuilder report=new StringBuilder();private int checks,sequence=100;private long diagnosticId;
    private int cover=1,temp=26,ih,motor;private boolean owned;
    interface Work{void run()throws Exception;}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private Object get(Object owner,String name)throws Exception{return field(owner.getClass(),name).get(owner);}
    private void ui(Work work){Throwable[] failure={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;report.append("PASS ").append(label).append('\n');}
    private int sent(){int n=0;for(String s:commands.toString().split("\n"))if(s.trim().equals("CHECK_POT"))n++;return n;}
    private void message(JSONObject packet)throws Exception{Method method=ControlClient.class.getDeclaredMethod("message",String.class,long.class);method.setAccessible(true);method.invoke(client,packet.toString(),SystemClock.elapsedRealtime());}
    private JSONObject status(int grams,int state,boolean diagnostic)throws Exception{
        return new JSONObject().put("type","status").put("diagnostic",diagnostic).put("potCheckId",diagnosticId).put("state",state).put("temperature",temp).put("coverOpen",cover).put("potPresent",-1).put("error",0).put("samples",++sequence).put("motorRaw",0).put("weightRaw",grams).put("elapsed",0).put("coverMappingVerified",true).put("potMappingVerified",false).put("armed",owned).put("ihStatus",ih).put("ihGear",0).put("motorStatus",motor);
    }
    private void sample(int grams)throws Exception{ui(()->{client.state.infoAt=SystemClock.elapsedRealtime();message(status(grams,0,false));});}
    private void feed(int grams,int duration)throws Exception{
        long start=-1,last=-1,deadline=SystemClock.elapsedRealtime()+duration+15000;
        while(true){
            sample(grams);long now=SystemClock.elapsedRealtime();
            // A gap of 500 ms resets the product's quiet window. Keep the
            // emulator's synthetic telemetry continuous for the requested span.
            if(last<0||now-last>=450)start=now;
            last=now;
            if(now-start>=duration)return;
            if(now>=deadline)throw new AssertionError("Emulator could not supply a continuous weight stream");
            SystemClock.sleep(50);
        }
    }
    private void begin()throws Exception{ui(()->{diagnosticId++;message(new JSONObject().put("type","potCheck").put("stage","checking").put("id",diagnosticId));message(status(client.state.weightRaw,9,true));});}
    private void finish(String outcome)throws Exception{ui(()->{message(status(client.state.weightRaw,0,true));message(new JSONObject().put("type","stopped").put("diagnostic",true).put("potCheckId",diagnosticId));message(new JSONObject().put("type","potCheck").put("stage","complete").put("id",diagnosticId).put("outcome",outcome).put("confirmedIdle",true).put("startAcked",!outcome.equals("unknown")).put("phaseSamples",32).put("error",outcome.equals("missing")?2:0));});}
    private void capture(String name)throws Exception{File dir=new File(getTargetContext().getExternalFilesDir(null),"pot-auto");dir.mkdirs();NativeCapture.save(this,new File(dir,name+".png"));}
    private void menuRoundTrip(int grams)throws Exception{
        ui(()->{Method go=ApplianceActivity.class.getDeclaredMethod("go",String.class);go.setAccessible(true);go.invoke(activity,"settings");
            check(get(activity,"page").equals("settings")&&!client.state.foreground&&get(client,"potMonitor")==null,"Settings navigation suspends Home observation");
            client.state.ended();});
        feed(grams,700); // The old display must not adopt these hidden samples.
        ui(()->{ControlState s=client.state;s.begin();s.foreground=s.rootVerified=s.connected=s.coverVerified=s.idleConfirmed=s.potCheckSupported=true;s.potVersionSamples=0;s.mcuVersion=12;s.ihVersion=4;s.motorVersion=5;
            Method go=ApplianceActivity.class.getDeclaredMethod("go",String.class);go.setAccessible(true);go.invoke(activity,"home");
            home=(HomeNumberControls)get(activity,"homeNumbers");vessel=(HomeVesselWebGLView)home.findViewWithTag("home.bowl");field(HomeNumberControls.class,"nextIdleConnect").setLong(home,Long.MAX_VALUE);});
        feed(grams,700); // Reconnected but still awaiting both version replies.
        ui(()->client.state.potVersionSamples=2);
    }
    private void closedStartup(Context context,Constructor<ControlClient> constructor)throws Exception{
        client=constructor.newInstance(context);field(ControlClient.class,"writer").set(client,new BufferedWriter(commands));
        ControlState closed=client.state;closed.foreground=closed.rootVerified=closed.connected=closed.coverVerified=true;closed.mcuVersion=12;closed.deviceFaultLatched=false;
        cover=0;sample(2147);
        ui(()->message(new JSONObject().put("type","info").put("version",12).put("ihVersion",4).put("motorVersion",5).put("switches",128)));
        check(closed.potCheck.required(),"One startup interlock reply does not resolve the initial request");
        ui(()->message(new JSONObject().put("type","info").put("version",12).put("ihVersion",4).put("motorVersion",5).put("switches",128)));
        check(!closed.potCheck.required()&&!(boolean)get(client,"automaticPotPending")&&sent()==0,"Closed startup resolves passively after two replies without CHECK_POT");
        cover=1;
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Throwable failure=null;try{
        if(!Build.HARDWARE.contains("ranchu"))throw new IllegalStateException("Owned emulator only");
        Context context=getTargetContext();context.getSharedPreferences("setup",0).edit().putBoolean("seen",true).commit();
        previous=(ControlClient)field(ControlClient.class,"instance").get(null);Constructor<ControlClient> constructor=ControlClient.class.getDeclaredConstructor(Context.class);constructor.setAccessible(true);closedStartup(context,constructor);client=constructor.newInstance(context);
        field(ControlClient.class,"writer").set(client,new BufferedWriter(commands));field(ControlClient.class,"process").set(client,new MemoryProcess());field(ControlClient.class,"instance").set(null,client);
        ControlState s=client.state;s.foreground=s.rootVerified=s.connected=s.coverVerified=s.idleConfirmed=s.potCheckSupported=true;s.potVerified=false;s.pot=-1;s.coverOpen=1;s.state=0;s.error=0;s.samples=sequence;s.temperature=26;s.weightRaw=2147;s.statusAt=s.infoAt=SystemClock.elapsedRealtime();s.mcuVersion=12;s.ihVersion=4;s.motorVersion=5;s.potVersionSamples=2;s.deviceFaultLatched=false;s.lastDeviceError=0;
        ActivityMonitor monitor=addMonitor(ApplianceActivity.class.getName(),null,false);context.startActivity(new Intent(context,ApplianceActivity.class).putExtra("page","home").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));activity=waitForMonitorWithTimeout(monitor,60000);removeMonitor(monitor);if(activity==null)throw new AssertionError("Home launch timed out");
        ui(()->{home=(HomeNumberControls)get(activity,"homeNumbers");vessel=(HomeVesselWebGLView)home.findViewWithTag("home.bowl");field(HomeNumberControls.class,"nextIdleConnect").setLong(home,Long.MAX_VALUE);client.potMonitoring(home,true);});
        check(s.potCheck.required(),"Fresh app requires an initial vessel check before cooking");
        ui(()->client.potMonitoring(home,false));feed(0,2200);check(sent()==0,"Startup check waits while Home is hidden");
        ui(()->client.potMonitoring(home,true));temp=100;motor=1;feed(0,2200);check(sent()==0,"Hot startup check waits for confirmed zero motor output");motor=0;
        ui(()->client.potTouch(home,true));feed(0,2200);check(sent()==0,"Startup check waits while screen is held");ui(()->client.potTouch(home,false));
        feed(0,1000);check(sent()==0,"Constant zero startup weight still needs a full quiet window");
        ui(()->{s.ended();s.begin();s.foreground=s.rootVerified=s.connected=s.coverVerified=s.idleConfirmed=s.potCheckSupported=true;s.potVersionSamples=2;s.mcuVersion=12;s.ihVersion=4;s.motorVersion=5;((PotWeightTrigger)get(client,"potWeight")).reset();});
        feed(0,1000);check(sent()==0,"Unsent startup request survives reconnect and restarts settling");feed(0,1300);
        check(sent()==1,"Startup without any weight change submits exactly one CHECK_POT");begin();finish("missing");sample(0);
        ui(()->check(vessel.presentation().lid==HomeVesselState.Lid.POT_MISSING,"Starting without a pot reaches the missing-pot display"));
        feed(0,2300);check(sent()==1,"Unchanged absent startup vessel does not repeat diagnostic");
        feed(1850,2300);check(sent()==2,"Inserting an open pot after startup triggers the existing return check");begin();finish("unknown");feed(1850,2300);
        check(sent()==2&&s.potCheck.missing(),"Unknown startup return is not retried and retains missing display");
        ui(()->check(client.checkPot(),"Explicit return check remains possible"));begin();finish("clear_window");sample(1850);
        check(!s.potCheck.missing()&&!s.potCheck.required()&&!s.potVerified,"Completed startup return clears missing without inventing a pot sensor");
        // Establish a checked baseline for the unchanged weight-change regression suite.
        commands.getBuffer().setLength(0);
        feed(2147,1100);feed(1850,2300);check(sent()==0,"Removing lid/cup alone does not submit a diagnostic");
        feed(0,1000);check(sent()==0,"Bare vessel removal waits for two full seconds of quiet");feed(0,1800);check(sent()==1,"Settled1850g removal automatically submits exactly one CHECK_POT");
        begin();check(s.potCheck.required()&&!s.safe(SystemClock.elapsedRealtime(),true),"Checking blocks cooking without owning a cook");
        ui(()->check(!home.startButton().isClickable()&&home.startButton().getText().length()==0,"Automatic diagnostic does not display a cooking Stop button"));
        finish("missing");sample(0);ui(()->check(vessel.presentation().lid==HomeVesselState.Lid.POT_MISSING,"Correlated missing result greys the real Home vessel"));capture("01-confirmed-missing");
        // Screenshot compression pauses this memory transport; re-establish fresh
        // absent telemetry before simulating a return (real UART keeps streaming).
        feed(0,950);feed(1850,700);check(sent()==1,"Return load does not immediately start another check");ui(()->check(s.potCheck.missing()&&vessel.presentation().lid==HomeVesselState.Lid.POT_MISSING,"Returning weight cannot restore the vessel before its new result"));
        feed(1850,1500);check(sent()==2,"Settled return automatically submits one new diagnostic");begin();ui(()->check(vessel.presentation().lid==HomeVesselState.Lid.POT_MISSING,"Vessel remains grey throughout return checking"));capture("02-return-checking");
        finish("unknown");feed(1850,2500);check(sent()==2&&s.potCheck.missing()&&s.potCheck.required(),"Unknown outcome retains grey state and never retries the same load");
        ui(()->check(client.checkPot(),"Explicit check remains available after unknown outcome"));begin();finish("clear_window");sample(1850);ui(()->check(vessel.presentation().lid==HomeVesselState.Lid.OPEN&&!s.potCheck.missing()&&!s.potVerified,"Only a stopped correlated clear window restores the open vessel without a sensor claim"));capture("03-confirmed-return");
        feed(1850,950);cover=0;feed(0,2200);check(sent()==3&&s.potCheck.required(),"Closed lid defers a stable weight request");
        ui(()->{s.ended();s.begin();s.foreground=s.rootVerified=s.connected=s.coverVerified=s.idleConfirmed=s.potCheckSupported=true;s.potVersionSamples=2;s.mcuVersion=12;s.ihVersion=4;s.motorVersion=5;((PotWeightTrigger)get(client,"potWeight")).reset();});
        feed(0,1000);check(sent()==3,"Unsent request survives reconnect but waits for new quiet telemetry");feed(0,1800);cover=1;sample(0);check(sent()==4,"Opening the lid permits the settled request even after reconnect");begin();finish("missing");
        ui(()->client.potMonitoring(home,false));feed(1850,2300);check(sent()==4,"Hidden Home cannot initiate an automatic diagnostic");ui(()->client.potMonitoring(home,true));feed(1850,1000);check(sent()==4&&s.potCheck.missing(),"Returning Home waits for a new quiet window and cannot claim presence");
        feed(1850,1800);check(sent()==5&&s.potCheck.missing(),"A vessel inserted while Home was hidden triggers a deferred return diagnostic");begin();finish("clear_window");sample(1850);
        ui(()->home.windowTouch(MotionEvent.ACTION_DOWN));feed(0,2400);check(sent()==5,"Held screen pressure suppresses automatic start");ui(()->home.windowTouch(MotionEvent.ACTION_UP));feed(0,1000);check(sent()==5,"Release starts a new full quiet window");feed(0,1800);check(sent()==6,"Automatic checking resumes once release is settled");begin();
        ui(()->((GuidedControlPanel)get(activity,"cookingPanel")).pressHomeKnob());check(commands.toString().contains("STOP\n"),"Physical knob can stop the automatic diagnostic");finish("unknown");
        begin();finish("missing");cover=0;feed(1850,900);
        ui(()->message(new JSONObject().put("type","info").put("version",12).put("ihVersion",4).put("motorVersion",5).put("switches",128)));
        check(s.potCheck.missing(),"One closed-interlock reply cannot erase the missing result");
        ui(()->message(new JSONObject().put("type","info").put("version",12).put("ihVersion",4).put("motorVersion",5).put("switches",128)));
        ui(()->check(!s.potCheck.missing()&&!s.potCheck.required()&&!s.potVerified&&vessel.presentation().lid==HomeVesselState.Lid.CLOSED&&sent()==6,"Two fresh closed-interlock replies restore the closed vessel without an active diagnostic"));
        cover=1;feed(1850,2300);menuRoundTrip(1850);feed(1850,2300);check(sent()==6,"Real settings/Home navigation with unchanged weight does not check again");
        menuRoundTrip(0);feed(0,1000);check(sent()==6,"Menu removal waits after fresh versions return");feed(0,1800);
        check(sent()==7,"Real settings/Home navigation detects a pot removed during the menu gap");begin();finish("missing");sample(0);
        ui(()->check(vessel.presentation().lid==HomeVesselState.Lid.POT_MISSING,"Rebuilt Home shows the confirmed menu removal"));
        menuRoundTrip(1850);feed(1850,3500);check(sent()==8,"Real menu return also detects an inserted pot");begin();finish("unknown");
        menuRoundTrip(1850);feed(1850,2300);check(sent()==8&&s.potCheck.required()&&s.potCheck.missing(),"Menu navigation never retries an unknown receipt at unchanged weight");
        cover=0;feed(1850,100);ui(()->{for(int i=0;i<2;i++)message(new JSONObject().put("type","info").put("version",12).put("ihVersion",4).put("motorVersion",5).put("switches",128));});
        temp=100;cover=1;feed(0,3500);
        check(sent()==9&&s.potCheck.required()&&!s.potMissing(),"Hot removal at100C dispatches without any cooling interval");begin();
        ui(()->check(!home.startButton().isClickable()&&!home.startButton().getText().toString().contains("abkühlen"),"Hot diagnostic blocks concurrent cooking without an instruction to cool"));
        capture("04-hot-removal-checking");finish("missing");sample(0);
        ui(()->check(vessel.presentation().lid==HomeVesselState.Lid.POT_MISSING,"Hot removal reaches confirmed grey vessel"));
        feed(1850,3500);check(sent()==10,"Hot return at100C dispatches without cooling");begin();finish("clear_window");sample(1850);
        ui(()->check(vessel.presentation().lid==HomeVesselState.Lid.OPEN&&!s.potCheck.required(),"Hot stopped return restores vessel and removes pot-related start block"));capture("05-hot-return-confirmed");
        check(!commands.toString().contains("START")&&!commands.toString().contains("APPLY")&&!commands.toString().contains("RESUME")&&!s.ownRun&&s.acceptedStarts==0,"Every test uses memory transport and never submits a cooking command");
    }catch(Throwable e){failure=e;}finally{if(activity!=null)runOnMainSync(()->activity.finish());try{field(ControlClient.class,"instance").set(null,previous);}catch(Exception ignored){}}
        Bundle result=new Bundle();result.putString("stream",report+(failure==null?"\n"+checks+" automatic-pot checks passed. Memory transport only.\n":"FAIL "+failure+"\n"));finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
