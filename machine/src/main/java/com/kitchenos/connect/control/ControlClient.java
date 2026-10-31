package com.kitchenos.connect.control;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** App-local foreground control. No receiver, service or network endpoint can start it. */
public final class ControlClient {
    public interface Listener { void changed(); }
    private static ControlClient instance;
    public static synchronized ControlClient get(Context context){if(instance==null)instance=new ControlClient(context.getApplicationContext());return instance;}
    public static synchronized boolean hasActiveSession(){return instance!=null&&(instance.state.busy()||instance.state.uncertainRun||instance.program.active());}
    public static synchronized boolean canReconfigureAccess(){return !hasActiveSession()&&(instance==null||!instance.state.rootChecking&&!instance.state.recovering);}
    public static synchronized void invalidateAccess(){if(instance!=null){instance.state.rootVerified=false;instance.changed();}}
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Object io=new Object();
    public final ControlState state=new ControlState();
    public final WaterHeating waterHeating=new WaterHeating();
    public final EggCooking eggCooking=new EggCooking();
    private final HeatingBoost heatingBoost=new HeatingBoost();
    public final TemperatureHistory temperatureHistory=new TemperatureHistory();
    private final HotEndCue hotEndCue=new HotEndCue();
    private int hotEndSounds;
    private long hotEndAt=-1;
    public long hotEndAt(){return hotEndAt;}
    private long temperatureSavedAt=-1;
    public final CookingReview cookingReview=new CookingReview();
    private Listener listener;
    private final java.util.ArrayList<Listener> observers=new java.util.ArrayList<>();
    private final RunBudget runBudget=new RunBudget();
    public final LidInterruption lidInterruption=new LidInterruption();
    private ControlValues submittedValues;
    private final PotWeightTrigger potWeight=new PotWeightTrigger();
    private Object potMonitor;
    // One request per app process, also when startup weight never changes.
    // Reconnects preserve an unsent request; a write attempt consumes it.
    private boolean automaticPotPending=true;
    private int closedPotSamples;
    private String potObservation="";
    private int activeProfile;
    private boolean programDispatch;
    public final FunctionProgramRunner program=new FunctionProgramRunner(state,new FunctionProgramRunner.Transport(){
        public boolean start(ControlValues values){programDispatch=true;try{return execute("START",values,true);}finally{programDispatch=false;}}
        public boolean adjust(ControlValues values){programDispatch=true;try{return execute("APPLY_SETTINGS",values,true);}finally{programDispatch=false;}}
        public boolean stir(int speed){return setFryingMotor(speed);}
        public void stop(){program.cancel("Programm angehalten");requestStop(true);}
    });
    public boolean startProgram(FunctionProgram plan){boolean sent=program.start(plan,SystemClock.elapsedRealtime());changed();return sent;}
    /** Automatic frying interval only. User PAUSE/STOP and heating remain separate. */
    private boolean setFryingMotor(int speed){
        drainReceived();long now=SystemClock.elapsedRealtime();
        if(!program.active()||activeProfile!=ControlProfile.FRY||(speed!=0&&speed!=-10)||state.state!=1||!state.allows("APPLY_SETTINGS",now)||runBudgetRemaining()<=0||state.mcuVersion!=12||state.ihVersion!=4||state.motorVersion!=5)return false;
        if(!write("FRY_MOTOR "+speed))return false;
        state.submitted("FRY_MOTOR");requestedSpeed=speed;
        android.util.Log.i("KitchenOSControl","FRY_MOTOR gesendet: "+speed+"; Temperatur und Abschaltzeit unverändert");
        changed();return true;
    }
    private boolean activeLidless;
    private int requestedTemperature=-1,requestedSpeed,auditTemperature=-1,auditIhStatus=-1,auditIhGear=-1;
    private long auditAt;
    private long startSubmittedAt=-1;private long connectionEpoch;
    private long connectionStartedAt=-1;
    private long nextRecoveryAt;
    private final java.util.ArrayList<Received> received=new java.util.ArrayList<>();
    private boolean draining,notifyAfterDrain;
    private static final class Received {
        final String line;final long at,epoch;
        Received(String line,long at,long epoch){this.line=line;this.at=at;this.epoch=epoch;}
    }
    private final Runnable deliverReceived=this::drainReceived;
    private void enqueueReceived(String line,long at,long epoch){synchronized(received){boolean empty=received.isEmpty();received.add(new Received(line,at,epoch));if(empty)main.post(deliverReceived);}}
    /** Consume already received events in order before evaluating an explicit local action.
     * Receipt times remain unchanged; this never waits for or queues a future START. */
    public void drainReceived(){
        if(draining)return;
        java.util.ArrayList<Received> batch;
        synchronized(received){if(received.isEmpty())return;batch=new java.util.ArrayList<>(received);received.clear();}
        draining=true;
        try{for(Received event:batch)messageIfCurrent(event.line,event.at,event.epoch);}
        finally{draining=false;if(notifyAfterDrain){notifyAfterDrain=false;changed();}}
    }
    private Process process;
    private BufferedWriter writer;
    private volatile boolean cancelled;
    private final StringBuilder events=new StringBuilder();
    private ControlClient(Context app){this.app=app;temperatureHistory.restore(app.getSharedPreferences("temperature-history",0).getString("snapshot",""));state.potCheck.require();state.lastDeviceError=app.getSharedPreferences("control-errors",0).getInt("lastError",0);state.deviceFaultLatched=state.lastDeviceError!=0;}
    public void listen(Listener next){listener=next;changed();}
    /** Passive foreground displays may observe telemetry without replacing the controller. */
    public void addObserver(Listener next){if(!observers.contains(next))observers.add(next);}
    public void removeObserver(Listener previous){observers.remove(previous);}
    public void foreground(boolean value){state.foreground=value;if(!value){potWeight.pause();disconnect();}changed();}
    public void potMonitoring(Object owner,boolean active){if(active){if(potMonitor!=owner){potWeight.pause();potMonitor=owner;}}else if(potMonitor==owner){potMonitor=null;potWeight.touch(false);potWeight.pause();}}
    public void potTouch(Object owner,boolean down){if(potMonitor==owner)potWeight.touch(down);}
    public String events(){return events.toString();}
    private void changed(){if(draining){notifyAfterDrain=true;return;}
        boolean idle=state.idleConfirmed&&!state.ownRun&&!state.stopping&&!state.uncertainRun&&state.pendingAction.isEmpty();
        if(hotEndCue.observe(state.acceptedStarts,program.active()||lidInterruption.active(),idle,temperatureHistory.temperature(),state.connected&&temperatureHistory.fresh(SystemClock.elapsedRealtime()))){
            hotEndSounds++;
            hotEndAt=SystemClock.elapsedRealtime();
            // Never block telemetry or alter the user's volume/ringer settings.
            main.post(()->{try{android.media.AudioManager audio=(android.media.AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
                if(audio==null||audio.getRingerMode()!=android.media.AudioManager.RINGER_MODE_NORMAL)return;
                android.media.ToneGenerator tone=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,45);
                try{tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,180);main.postDelayed(tone::release,450);}catch(RuntimeException e){tone.release();}
            }catch(RuntimeException e){android.util.Log.w("KitchenOSHeat","Hinweiston nicht verfügbar",e);}});
        }
        if(listener!=null)listener.changed();for(Listener observer:new java.util.ArrayList<>(observers))observer.changed();}
    private void note(String text){state.lastEvent=text;events.insert(0,text+"\n");if(events.length()>1800)events.setLength(1800);android.util.Log.i("KitchenOSControl",text);changed();}
    public void checkRoot(){
        if(state.rootChecking||state.busy())return;
        state.rootChecking=true;state.rootVerified=false;note("Checking root identity only; appliance control stays closed …");
        new Thread(()->{
            boolean verified=false;String failure="Root access missing. Renew the factory session or check this app's Magisk grant.";Process check=null;
            try{
                check=ControlAccess.open(app,"ID");
                if(!check.waitFor(25,TimeUnit.SECONDS))throw new IOException("Root identity check timed out");
                StringBuilder output=new StringBuilder();try(BufferedReader input=new BufferedReader(new InputStreamReader(check.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=input.readLine())!=null&&output.length()<8192)output.append(line).append('\n');}
                verified=check.exitValue()==0&&java.util.regex.Pattern.compile("(?m)^uid=0\\(root\\)(?:\\s|$)").matcher(output).find();
            }catch(Exception e){failure="Root identity was not confirmed. Renew the factory session or check this app's Magisk grant.";}
            finally{if(check!=null){check.destroy();try{check.getInputStream().close();}catch(IOException ignored){}}}
            final boolean success=verified;final String error=failure;
            main.post(()->{state.rootChecking=false;state.rootVerified=success;if(!success)state.userIssue="Device access is not ready. Check the installation guide and session status.";note(success?"Root identity confirmed for this app. Appliance control is still closed.":error);});
        },"KitchenOS-Root-Identity").start();
    }
    public void connect(){
        if(!state.foreground||!state.rootVerified||state.rootChecking||state.busy()||state.uncertainRun||state.deviceFaultLatched)return;
        synchronized(io){if(process!=null)return;}
        final long epoch=++connectionEpoch;connectionStartedAt=SystemClock.elapsedRealtime();cancelled=false;closedPotSamples=0;potWeight.pause();lidInterruption.clear();state.begin();note("Steuerverbindung wird vorbereitet …");
        new Thread(()->{
            int exit=-1;boolean reachedReady=false;
            try{
                File binary=new File(app.getApplicationInfo().nativeLibraryDir,"libkitchenos_bridge.so");
                if(!binary.isFile())throw new IOException("Native Steuerung fehlt im APK");
                Process created=ControlAccess.open(app,"SERVE");
                synchronized(io){process=created;writer=new BufferedWriter(new OutputStreamWriter(created.getOutputStream(),StandardCharsets.UTF_8));if(cancelled){writer.write("QUIT\n");writer.flush();}}
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(created.getInputStream(),StandardCharsets.UTF_8))){
                    String line;while((line=reader.readLine())!=null){final long receivedAt=SystemClock.elapsedRealtime();if(line.contains("\"type\":\"ready\""))reachedReady=true;enqueueReceived(line,receivedAt,epoch);}
                }
                exit=created.waitFor();
            }catch(Exception e){main.post(()->{if(epoch!=connectionEpoch)return;state.userIssue="Die Gerätesteuerung konnte nicht geöffnet werden. Bitte die App neu öffnen oder die Installation prüfen.";note("Steuerverbindung konnte nicht geöffnet werden. Root-Zugang, Installation und Belegung durch die Test-App prüfen.");});}
            finally{synchronized(io){if(writer!=null)try{writer.close();}catch(IOException ignored){}writer=null;process=null;}}
            final int result=exit;final boolean hadReady=reachedReady;
            main.post(()->{
                if(epoch!=connectionEpoch)return;state.ended();lidInterruption.clear();
                if(!hadReady&&result!=0)state.rootVerified=false;
                note(state.uncertainRun?"Stoppbestätigung fehlt. Maschinenzustand wird automatisch erneut gelesen.":"Steuerverbindung beendet ("+result+").");
            });
        },"KitchenOS-Control-Bridge").start();
    }
    private static String quote(String s){return "'"+s.replace("'","'\"'\"'")+"'";}
    private void messageIfCurrent(String line,long receivedAt,long epoch){if(epoch==connectionEpoch)message(line,receivedAt);}
    private void message(String line,long receivedAt){
        try{
            JSONObject message=new JSONObject(line);String type=message.getString("type");int priorRemaining=state.displayRemainingSeconds();long priorAccepted=state.acceptedCommandSequence;int priorCover=state.coverOpen;boolean loweringAck=type.equals("ack")&&heatingBoost.loweringInFlight()&&(message.optInt("opcode")==0x87||message.optInt("opcode")==0x88);boolean waterLoweringAck=type.equals("ack")&&waterHeating.loweringInFlight()&&(message.optInt("opcode")==0x87||message.optInt("opcode")==0x88);if(type.equals("ready")&&!ControlProfile.ID.equals(message.optString("profileId"))){state.userIssue="Die Gerätesteuerung passt nicht zu dieser App-Version. Bitte Installation prüfen.";note(state.userIssue);disconnect();return;}if(type.equals("error"))state.userIssue=message.optString("message").contains("UART")?"Die Maschine ist noch durch eine andere Koch-App belegt. Bitte diese schließen und erneut versuchen.":"Die Maschine hat die Aktion nicht angenommen. Bitte Topf, Deckel und Verbindung prüfen.";if(type.equals("ack")&&!message.optBoolean("success"))state.userIssue="Die Maschine hat die Einstellungen nicht angenommen. Bitte erneut versuchen.";if(type.equals("status"))for(String output:new String[]{"ihStatus","ihGear","motorStatus"})message.getInt(output);if(type.equals("stopping")&&!message.optBoolean("diagnostic",false)&&!"Kochzeit beendet".equals(message.optString("message")))cookingReview.stopRequested();state.accept(message,receivedAt);
            if(loweringAck){if(!message.optBoolean("success")){heatingBoost.cancel();requestStop("Heizsollwert konnte nicht zurückgenommen werden. Stopp angefordert.");}else if(message.optInt("opcode")==0x88)heatingBoost.lowered();}
            if(waterLoweringAck){if(!message.optBoolean("success"))requestStop("Wasser-Sollwert konnte nicht zurückgenommen werden. Stopp angefordert.");else if(message.optInt("opcode")==0x88){waterHeating.lowered(SystemClock.elapsedRealtime());android.util.Log.i("KitchenOSHeat","Wasser-Sollwert bestätigt; 20 Sekunden Nachheizphase begonnen");}}
            if(eggCooking.rebasing()&&type.equals("ack")){
                if(!message.optBoolean("success"))requestStop("Eier-Garzeit konnte nicht an die Gerätesteuerung übergeben werden. Stopp angefordert.");
                else if(state.acceptedCommandSequence!=priorAccepted)eggCooking.rebaseAccepted();
            }
            if(type.equals("error")&&heatingBoost.loweringInFlight())requestStop("Heizsollwert-Rücknahme fehlgeschlagen. Stopp angefordert.");
            if(type.equals("error")&&waterHeating.loweringInFlight())requestStop("Wasser-Sollwert-Rücknahme fehlgeschlagen. Stopp angefordert.");
            if(type.equals("stopping")||type.equals("stopped")||type.equals("closed")||type.equals("error"))heatingBoost.cancel();
            if(type.equals("stopping")&&!message.optBoolean("diagnostic",false))auditStop("bridge: "+message.optString("message"),receivedAt,false);
            if(type.equals("ack")&&state.acceptedCommandSequence!=priorAccepted&&submittedValues!=null){if(submittedValues.turbo||submittedValues.waterHeating)lidInterruption.clear();else lidInterruption.accepted(submittedValues);if(state.stopping)lidInterruption.cancelContinuation();}
            if(type.equals("stopping")&&!message.optString("message").startsWith("Schutzstopp:")&&!message.optString("message").equals("Deckel-/Topfsignal geaendert")){if(message.optString("message").equals("Stopp angefordert"))lidInterruption.cancelContinuation();else lidInterruption.clear();}
            if(!program.active()&&!waterHeating.active())lidInterruption.observe(state,type,priorRemaining);
            if(eggCooking.observe(state,type,SystemClock.elapsedRealtime(),lidInterruption.active()))requestStop("Eier-Garzeit ab 100 °C erreicht. Stoppbestätigung wird abgewartet.");
            if(eggCooking.needsRebase()){
                if(execute("APPLY",ControlValues.eggsAtBoil(eggCooking.selectedSeconds()),false))eggCooking.rebasing(state.commandSequence,SystemClock.elapsedRealtime());
                else requestStop("Eier-Garzeit konnte nicht ab 100 °C gesetzt werden. Stopp angefordert.");
            }
            if(waterHeating.observe(state,type,SystemClock.elapsedRealtime()))requestStop("Zieltemperatur erreicht und 20 Sekunden gehalten. Stoppbestätigung wird abgewartet.");
            if(waterHeating.shouldLower(state,type,SystemClock.elapsedRealtime())){
                ControlValues normal=submittedValues.withoutHeatBoost();
                if(execute("APPLY_SETTINGS",normal,false)){waterHeating.lowering(state.commandSequence,SystemClock.elapsedRealtime());android.util.Log.i("KitchenOSHeat","Wasser erreicht Ziel; interner Sollwert auf "+normal.temperature+"C zurückgenommen");}
                else requestStop("Wasser-Sollwert konnte nicht zurückgenommen werden. Stopp angefordert.");
            }
            if(waterHeating.tooHot(state,type,SystemClock.elapsedRealtime()))requestStop("Wasser liegt zu weit oder zu lange über dem Ziel. Schutzstopp angefordert.");
            if(heatingBoost.shouldLower(state,type,SystemClock.elapsedRealtime())){
                ControlValues normal=heatingBoost.requested().withoutHeatBoost();
                if(execute("APPLY_SETTINGS",normal,false)){heatingBoost.lowering(normal);android.util.Log.i("KitchenOSHeat","Ziel erreicht; interner Heizsollwert auf "+normal.temperature+"C zurückgenommen");}
                else requestStop("Heizsollwert konnte nicht zurückgenommen werden. Stopp angefordert.");
            }
            if(heatingBoost.tooHot(state,type,SystemClock.elapsedRealtime()))requestStop("Temperatur liegt länger als 30 Sekunden mehr als 5 °C über dem Ziel. Schutzstopp angefordert.");
            if(lidInterruption.canClearLidFault(state,receivedAt)&&app.getSharedPreferences("control-errors",0).edit().putInt("lastError",0).commit()){
                state.deviceFaultLatched=false;state.lastDeviceError=0;state.userIssue=state.visualIssue="";
                android.util.Log.i("KitchenOSControl","Bekannte Deckelunterbrechung: STOP und Nullausgänge bestätigt; wartet auf ausdrückliches Weiterkochen");
            }

            if(lidInterruption.cancelled()&&lidInterruption.confirmedIdle(state,receivedAt)&&!state.deviceFaultLatched)lidInterruption.clear();
            if((type.equals("info")||type.equals("status"))&&state.coverOpen!=priorCover)android.util.Log.i("KitchenOSCover","signal="+type+"; cover="+state.coverOpen+"; switches="+state.switches+"; state="+state.state+"; fresh="+state.fresh(SystemClock.elapsedRealtime())+"; statusAgeMs="+(SystemClock.elapsedRealtime()-state.statusAt)+"; infoAgeMs="+(SystemClock.elapsedRealtime()-state.infoAt)+"; dispatchDelayMs="+(SystemClock.elapsedRealtime()-receivedAt)+"; epoch="+connectionEpoch);
            // Retain the short diagnostic receipt and zero-output observations
            // in logcat so a real UI comparison can be audited after STOP.
            if(type.equals("potCheck")||message.optBoolean("diagnostic",false))android.util.Log.i("KitchenOSPot",message.toString());
            if(type.equals("potCheck")&&message.optString("stage").equals("complete"))potWeight.reference(state.weightRaw,state.samples,state.statusAt);
            if(type.equals("status")||type.equals("info"))observePotInterlock(type.equals("info"));
            if(type.equals("status"))observePotWeight(receivedAt);
            if(type.equals("status")){
                boolean wasHot=temperatureHistory.hot();
                boolean trustworthy=state.connected&&!state.potMissing()&&state.error==0&&SystemClock.elapsedRealtime()-receivedAt<TemperatureHistory.FRESH_MS;
                temperatureHistory.observe(receivedAt,System.currentTimeMillis(),state.temperature,state.ownRun?requestedTemperature:0,trustworthy,state.ownRun&&!state.stopping&&state.ihStatus!=0);
                if(wasHot!=temperatureHistory.hot()||temperatureSavedAt<0||receivedAt-temperatureSavedAt>=10000){
                    temperatureSavedAt=receivedAt;app.getSharedPreferences("temperature-history",0).edit().putString("snapshot",temperatureHistory.encode()).apply();
                }
            }
            String telemetry=ControlTelemetry.frame(type,message.optString("raw",null),receivedAt,requestedTemperature,requestedSpeed);
            if(telemetry!=null)android.util.Log.i("KitchenOSTelemetry",telemetry);
            if(type.equals("status")&&state.ownRun&&receivedAt-auditAt>=1000&&(state.temperature!=auditTemperature||state.ihStatus!=auditIhStatus||state.ihGear!=auditIhGear)){
                auditAt=receivedAt;auditTemperature=state.temperature;auditIhStatus=state.ihStatus;auditIhGear=state.ihGear;
                android.util.Log.i("KitchenOSHeat","Angefordert="+requestedTemperature+"C; Ist="+state.temperature+"C; IH="+state.ihStatus+"/"+state.ihGear);
            }
            if(state.deviceFaultLatched&&app.getSharedPreferences("control-errors",0).getInt("lastError",0)!=state.lastDeviceError)app.getSharedPreferences("control-errors",0).edit().putInt("lastError",state.lastDeviceError).commit();
            if(type.equals("ready")){
                note("Exklusive Verbindung hergestellt. Live-Daten werden gelesen; kein Lauf gestartet.");
                if(cancelled||!state.foreground)disconnect();
            }else if(type.equals("status")||type.equals("info")||type.equals("potCheck")){changed();}
            else if(type.equals("ack")){note("Geräteantwort: "+(message.getBoolean("success")?"angenommen":"abgelehnt")+" (0x"+Integer.toHexString(message.getInt("opcode"))+")");}
            else if(type.equals("sent")){note("Steuerbefehl gesendet; Bestätigung und Live-Status abwarten.");}
            else if(type.equals("closed")){note(state.idleConfirmed?"Leerlauf beim Trennen bestätigt.":"Verbindung geschlossen; Leerlauf nicht bestätigt.");}
            else{String text=message.optString("message","");if(!text.isEmpty())note(text);else changed();}
        }catch(JSONException e){
            if(state.connected){state.statusAt=state.infoAt=-1;note("Ungültige Daten der Steuerverbindung. Verbindung wird beendet.");disconnect();}
            else if(!line.trim().isEmpty())note("Steuerungszugang noch nicht bestätigt. Magisk-Freigabe und Geräteverbindung prüfen.");
        }
    }
    public void tick(){
        drainReceived();
        long now=SystemClock.elapsedRealtime();
        recoverAutomatically(now);
        if(state.foreground&&state.connected&&!state.closing&&!state.ownRun&&!state.stopping&&!state.uncertainRun&&!state.deviceFaultLatched&&!state.potCheck.active()&&state.pendingAction.isEmpty()&&!program.active()&&!lidInterruption.active()&&connectionStartedAt>=0&&now-connectionStartedAt>3000&&(state.state==0||state.state==11)&&state.ihStatus==0&&state.ihGear==0&&state.motorStatus==0&&state.motorRaw==0&&(now-state.statusAt>2000||now-state.infoAt>2000)){
            note("Live-Daten im Leerlauf ausgeblieben; Leseverbindung wird erneuert. Kein Start vorgemerkt.");disconnect();return;
        }
        if(!state.pendingAction.equals("START"))startSubmittedAt=-1;
        if(state.connected&&!state.stopping&&startSubmittedAt>=0&&now-startSubmittedAt>=(activeProfile==ControlProfile.TURBO?11000:3000)){
            state.userIssue="Startbestätigung fehlt. Der Lauf wird sicherheitshalber gestoppt.";
            program.cancel(state.userIssue);requestStop(state.userIssue);
        }
        program.tick(now);
        if(waterHeating.loweringTimedOut(now))requestStop("Wasser-Sollwert-Rücknahme nicht rechtzeitig bestätigt. Stopp angefordert.");
        if(waterHeating.holdTimedOut(now))requestStop("Wasser-Nachheizphase ohne gültige Zielmeldung zu lang. Stopp angefordert.");
        if(eggCooking.preheatTimedOut(now))requestStop("Eier: 100 °C nach acht Minuten nicht bestätigt. Stopp angefordert.");
        if(eggCooking.rebaseTimedOut(now))requestStop("Eier-Garzeit vom Gerät nicht rechtzeitig bestätigt. Stopp angefordert.");
        if(state.foreground&&state.connected&&!state.closing){if((state.ownRun||state.pendingAction.equals("START"))&&runBudget.expired(now))requestStop(false);write("PING");}
        changed();
    }
    private void recoverAutomatically(long now){
        if(!state.foreground||state.rootChecking||state.recovering||(!state.uncertainRun&&!state.deviceFaultLatched)||now<nextRecoveryAt)return;
        // A live transport must finish its own STOP before a second reader opens.
        if(state.connected){if(!state.closing&&!state.ownRun&&!state.stopping&&!state.potCheck.active()&&state.pendingAction.isEmpty())disconnect();return;}
        if(state.busy())return;
        synchronized(io){if(process!=null)return;}
        if(!state.rootVerified){nextRecoveryAt=now+10000;checkRoot();return;}
        final long epoch=++connectionEpoch;
        nextRecoveryAt=now+10000;state.recovering=true;
        note("Maschinenzustand wird automatisch gelesen; kein Kochbefehl.");
        new Thread(()->{
            IdleRecovery evidence=new IdleRecovery();int exit=-1;Process probe=null;
            Runnable timeout=null;
            try{
                File binary=new File(app.getApplicationInfo().nativeLibraryDir,"libkitchenos_bridge.so");
                probe=ControlAccess.open(app,"PROBE");
                final Process running=probe;timeout=()->running.destroy();main.postDelayed(timeout,8000);
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(probe.getInputStream(),StandardCharsets.UTF_8))){
                    String line;while((line=reader.readLine())!=null)evidence.accept(line,SystemClock.elapsedRealtime());
                }
                exit=probe.waitFor();
            }catch(Exception ignored){/* No evidence means the interlock remains set. */}
            finally{if(timeout!=null)main.removeCallbacks(timeout);if(probe!=null)probe.destroy();}
            final int result=exit;
            main.post(()->finishRecovery(epoch,result,evidence));
        },"KitchenOS-Idle-Recovery").start();
    }
    private void finishRecovery(long epoch,int result,IdleRecovery evidence){
        if(epoch!=connectionEpoch)return;
        state.recovering=false;
        if(state.foreground&&result==0&&evidence.confirmed(SystemClock.elapsedRealtime())&&app.getSharedPreferences("control-errors",0).edit().putInt("lastError",0).commit()){
            state.uncertainRun=state.ownRun=state.stopping=state.everOwnedRun=false;
            state.deviceFaultLatched=false;state.lastDeviceError=0;state.pendingAction="";
            state.idleConfirmed=true;state.userIssue=state.visualIssue="";
            program.cancel("Lauf beendet");lidInterruption.clear();
            note("Stillstand automatisch bestätigt: frische Rückmeldungen, Motor und Heizung aus. Kein Lauf gestartet.");
            connect();
        }else{
            state.userIssue=evidence.activeOutputs(SystemClock.elapsedRealtime())?"Motor oder Heizung noch aktiv. Maschine vor Ort stoppen. Zustand wird weiter automatisch gelesen.":"Maschinenzustand noch nicht bestätigt. Automatische Abfrage wird wiederholt.";
            note(state.userIssue);
        }
    }
    private void observePotInterlock(boolean info){
        long now=SystemClock.elapsedRealtime();
        boolean closed=state.foreground&&state.rootVerified&&state.fresh(now)&&state.mcuVersion==12&&state.coverVerified&&state.coverOpen==0&&state.error==0&&!state.deviceFaultLatched&&!state.ownRun&&!state.stopping&&!state.closing&&!state.uncertainRun&&!state.potCheck.active()&&state.pendingAction.isEmpty()&&(state.state==0||state.state==11)&&state.ihStatus==0&&state.ihGear==0&&state.motorStatus==0&&state.motorRaw==0;
        if(!closed){closedPotSamples=0;return;}
        if(!info)return;
        closedPotSamples=Math.min(2,closedPotSamples+1);
        if(closedPotSamples>=2&&(state.potCheck.required()||state.potCheck.missing())){
            state.potCheck.closedInterlock();automaticPotPending=false;potWeight.reference(state.weightRaw,state.samples,state.statusAt);
            android.util.Log.i("KitchenOSPot","Two fresh closed-interlock replies restore vessel/lid view; no diagnostic sent");
        }
    }
    private void observePotWeight(long now){
        // A diagnostic consumes its comparison, including unknown/failed results.
        if(state.uncertainRun||state.deviceFaultLatched||state.potCheck.active()||state.pendingAction.equals("CHECK_POT")){
            potObservation("diagnostic or unresolved fault");
            potWeight.reset();return;
        }
        // No moving/heating samples enter the baseline. Keep the last stationary
        // reference so removal immediately after STOP is still observable.
        if(program.active()||state.ownRun||state.stopping||!state.pendingAction.isEmpty()){
            potObservation("cooking; reference parked");potWeight.pause();return;
        }
        if(potMonitor==null||!state.foreground){potWeight.pause();return;}
        // Weight only requests a check; retained heat does not prevent detection.
        // Active diagnostics still require fresh idle data and zero outputs.
        boolean eligible=!program.active()&&potMonitor!=null&&state.foreground&&state.rootVerified&&state.potCheckSupported&&!state.potVerified&&state.potVersionSamples>=2&&state.mcuVersion==12&&state.ihVersion==4&&state.motorVersion==5&&state.fresh(SystemClock.elapsedRealtime())&&!state.ownRun&&!state.stopping&&!state.closing&&!state.uncertainRun&&!state.deviceFaultLatched&&!state.potCheck.active()&&state.pendingAction.isEmpty()&&(state.state==0||state.state==11)&&state.error==0&&state.ihStatus==0&&state.ihGear==0&&state.motorStatus==0&&state.motorRaw==0;
        if(potWeight.observe(state.weightRaw,state.samples,state.statusAt,eligible)){
            automaticPotPending=true;state.potCheck.require();android.util.Log.i("KitchenOSPot","Settled raw change >="+PotWeightTrigger.CHANGE_GRAMS+"g; diagnostic required");
        }
        potObservation(!eligible?"telemetry/idle guards; reference parked":automaticPotPending&&state.coverOpen!=1?"check deferred until lid open":"observing settled weight");
        // Keep an unsent request across an idle bridge reconnect. It must settle
        // again on fresh data; once submitted, an unknown result never auto-retries.
        if(ControlAvailability.POT_CHECK_AUTO&&automaticPotPending&&eligible&&potWeight.settled()&&state.coverVerified&&state.coverOpen==1)checkPot();
    }
    private void potObservation(String phase){
        if(phase.equals(potObservation))return;potObservation=phase;
        long now=SystemClock.elapsedRealtime();
        android.util.Log.i("KitchenOSPot","observation="+phase+"; temperature="+state.temperature+"; weightRaw="+state.weightRaw+"; cover="+state.coverOpen+"; statusAgeMs="+(now-state.statusAt)+"; infoAgeMs="+(now-state.infoAt));
    }
    /** Explicit local diagnostic; this is never a cook submission or start receipt. */
    public boolean canCheckPot(){
        long now=SystemClock.elapsedRealtime();
        if(state.potVersionSamples<2)return false;
        return !program.active()&&state.potCheckSupported&&state.foreground&&state.rootVerified&&state.fresh(now)&&state.coverVerified&&state.coverOpen==1&&!state.ownRun&&!state.stopping&&!state.closing&&!state.uncertainRun&&!state.deviceFaultLatched&&!state.potCheck.active()&&state.pendingAction.isEmpty()&&state.error==0&&(state.state==0||state.state==11)&&state.temperature>=0&&state.mcuVersion==12&&state.ihVersion==4&&state.motorVersion==5&&state.ihStatus==0&&state.ihGear==0&&state.motorStatus==0&&state.motorRaw==0;
    }
    public boolean checkPot(){
        if(!canCheckPot())return false;
        closedPotSamples=0;
        // A failed flush is ambiguous too; never automatically resubmit it.
        automaticPotPending=false;
        if(!write("CHECK_POT"))return false;
        android.util.Log.i("KitchenOSPot","CHECK_POT sent; temperature="+state.temperature+"; coverOpen="+state.coverOpen+"; MCU="+state.mcuVersion+"/"+state.ihVersion+"/"+state.motorVersion);
        state.potCheck.require();state.submitted("CHECK_POT");state.idleConfirmed=false;potWeight.consumed();changed();return true;
    }
    public long runBudgetRemaining(){return runBudget.remaining(SystemClock.elapsedRealtime());}
    public int activeProfile(){return activeProfile;}
    public void attachmentChanged(){if(state.userIssue.equals(com.kitchenos.connect.ui.AccessorySelection.UNKNOWN)||state.userIssue.equals(com.kitchenos.connect.ui.AccessorySelection.LIMIT)||state.userIssue.equals(com.kitchenos.connect.ui.AccessorySelection.COVER_LIMIT))state.userIssue="";changed();}
    public boolean canContinueAfterLid(int seconds){return lidInterruption.ready(state,SystemClock.elapsedRealtime())&&(!eggCooking.suspended()||!eggCooking.startedCooking()||eggCooking.remainingSeconds(SystemClock.elapsedRealtime())>0)&&runBudget.permits(seconds,SystemClock.elapsedRealtime());}
    public boolean continueAfterLid(ControlValues values){return lidInterruption.ready(state,SystemClock.elapsedRealtime())&&execute("START",values,true,true);}
    public void endLidInterruption(){if(lidInterruption.active()&&!state.ownRun&&!state.stopping&&state.idleConfirmed){lidInterruption.clear();changed();}}
    public boolean execute(String action,ControlValues values,boolean potConfirmed){return execute(action,values,potConfirmed,false);}
    private boolean execute(String action,ControlValues values,boolean potConfirmed,boolean continuingLid){
        if(program.active()&&!programDispatch)return false;
        String accessoryIssue=com.kitchenos.connect.ui.AccessorySelection.issue(app,values.speed,values.temperature);if(!accessoryIssue.isEmpty()){state.userIssue=accessoryIssue;note(accessoryIssue);return false;}
        if(values.lidless&&!ControlAvailability.LIDLESS){state.userIssue=ControlAvailability.LIDLESS_REASON;note(state.userIssue);return false;}
        boolean starting=action.equals("START");
        if(!starting&&(values.turbo||activeProfile==ControlProfile.TURBO)){state.userIssue="Turbo läuft als einzelner Impuls. Zuerst stoppen und neu starten.";return false;}
        if(values.turbo&&(state.mcuVersion!=12||state.ihVersion!=4||state.motorVersion!=5)){state.userIssue="Turbo benötigt die geprüfte Gerätesteuerung 12/4/5.";return false;}
        if(!starting&&values.lidless!=activeLidless){state.userIssue="Für einen Wechsel des Deckelmodus zuerst stoppen und neu starten.";note(state.userIssue);return false;}
        if(!state.allows(action,SystemClock.elapsedRealtime(),values.lidless)){note("Aktion gesperrt: frische Gerätedaten, Deckel und Betriebszustand prüfen.");return false;}
        int profile=starting?(values.turbo?ControlProfile.TURBO:values.manual?ControlProfile.MANUAL:ControlProfile.profile(values.seconds,values.temperature,values.speed)):activeProfile;
        if(values.lidless&&profile!=ControlProfile.NORMAL){state.userIssue="Ohne Deckel kochen: höchstens 100 °C und Stufe 3.";note(state.userIssue);return false;}
        if(!ControlProfile.supportsIn(profile,values.seconds,values.temperature,values.speed)){state.userIssue="Für diese Änderung zuerst stoppen und mit dem passenden Kochprofil neu starten.";note(state.userIssue);return false;}
        if((profile==ControlProfile.TURBO||profile==ControlProfile.MOTOR)&&(state.temperature<0||state.temperature>=60||profile==ControlProfile.TURBO&&(state.ihStatus!=0||state.ihGear!=0))){state.userIssue="Für schnelle Messerstufen muss der Topf unter 60 °C abgekühlt sein. Die Heizung bleibt aus.";note(state.userIssue);return false;}
        if(profile==ControlProfile.MANUAL&&Math.abs((long)values.speed)>60&&(state.temperature<0||state.temperature>80)){state.userIssue="Über 80 °C höchstens Stufe 6 wählen. Für höhere Stufen abkühlen lassen.";note(state.userIssue);return false;}
        if(continuingLid&&(profile!=activeProfile||!runBudget.permits(values.seconds,SystemClock.elapsedRealtime()))){state.userIssue="Die verbleibende Laufzeit passt nicht mehr in das Zeitbudget. Zeit verkürzen oder diesen Lauf beenden.";changed();return false;}
        String command;
        int deviceTarget=HeatingBoost.target(values,profile,state.temperature);
        String boostedAccessoryIssue=com.kitchenos.connect.ui.AccessorySelection.issue(app,values.speed,deviceTarget);
        if(!boostedAccessoryIssue.isEmpty()){state.userIssue=boostedAccessoryIssue;note(boostedAccessoryIssue);return false;}
        try{command=values.withTemperature(deviceTarget).command(action,potConfirmed);}catch(IllegalArgumentException e){note(e.getMessage());return false;}
        if((action.equals("APPLY")||action.equals("RESUME"))&&!runBudget.permits(values.seconds,SystemClock.elapsedRealtime())){state.userIssue="Die Änderung überschreitet die verbleibende Zeit dieses Laufs. Zuerst stoppen und einen neuen Lauf starten.";note(state.userIssue);return false;}
        if(!write(command)){state.userIssue="Die Verbindung ist unterbrochen. Bitte erneut versuchen.";return false;}
        android.util.Log.i("KitchenOSControl","Einstellungen gesendet: "+action+"; Ziel="+values.temperature+"C; Intern="+deviceTarget+"C; Stufe="+values.speed+"; Zeit="+values.seconds+"s; Ist="+state.temperature+"C; IH="+state.ihStatus+"/"+state.ihGear);
        requestedTemperature=values.temperature;requestedSpeed=values.speed;
        heatingBoost.sent(values,deviceTarget,state.commandSequence+1);
        if(starting)waterHeating.begin(values.waterHeating,values.temperature,state.commandSequence+1);else if(values.waterHeating)waterHeating.target(values.temperature);
        if(starting){
            if(values.eggSeconds>0){if(continuingLid&&eggCooking.suspended()){eggCooking.adjust(values.eggSeconds);eggCooking.resume(state.commandSequence+1,SystemClock.elapsedRealtime());}else eggCooking.begin(values.eggSeconds,state.commandSequence+1,SystemClock.elapsedRealtime());}
            else eggCooking.cancel();
        }else if(action.equals("APPLY")&&eggCooking.active())eggCooking.adjust(values.eggSeconds);
        state.userIssue="";cookingReview.submitted(action,values);if(starting){activeProfile=profile;activeLidless=values.lidless;if(!continuingLid)runBudget.begin(SystemClock.elapsedRealtime(),profile);lidInterruption.clear();}
        // Block a second start while the backend's reply is still in flight.
        submittedValues=values;state.submitted(action);state.displayClock.submitted(action,values.seconds,state);if(action.equals("START")){state.idleConfirmed=false;startSubmittedAt=SystemClock.elapsedRealtime();}
        changed();return true;
    }
    public void pause(){if(activeProfile==ControlProfile.TURBO&&(state.ownRun||state.pendingAction.equals("START"))){requestStop("Turbo angehalten");return;}if(state.allows("PAUSE",SystemClock.elapsedRealtime(),activeLidless)){if(write("PAUSE")){cookingReview.localStop();state.submitted("PAUSE");}}else note("Pause ist im aktuellen Zustand nicht freigegeben.");changed();}
    public void stop(){program.cancel("Programm angehalten");requestStop(true);}
    private void requestStop(boolean local){requestStop(local?"Stopp am Display angefordert. Bestätigung wird abgewartet.":"Gesamtbudget erreicht: Stopp angefordert. Bestätigung wird abgewartet.");}
    private void requestStop(String reason){waterHeating.cancel();eggCooking.cancel();heatingBoost.cancel();lidInterruption.cancelContinuation();if(state.connected&&!state.stopping){if(write("STOP")){cookingReview.stopRequested();state.stopping=true;auditStop(reason,SystemClock.elapsedRealtime(),true);note(reason);}}}
    /** Record the exact UI/program caller only after STOP has been sent. */
    private void auditStop(String reason,long receivedAt,boolean caller){
        long now=SystemClock.elapsedRealtime();
        String detail="reason="+reason+"; state="+state.state+"; pending="+state.pendingAction+"; target="+requestedTemperature+"; temperature="+state.temperature+"; motor="+state.motorRaw+"; remaining="+state.displayRemainingSeconds()+"; statusAgeMs="+(now-state.statusAt)+"; infoAgeMs="+(now-state.infoAt)+"; dispatchDelayMs="+(now-receivedAt)+"; epoch="+connectionEpoch;
        android.util.Log.i("KitchenOSStop",detail+(caller?"\n"+android.util.Log.getStackTraceString(new Throwable("Local STOP caller")):""));
    }
    public void disconnect(){
        heatingBoost.cancel();eggCooking.cancel();lidInterruption.clear();program.cancel("Programm unterbrochen");
        if(state.recovering||state.closing||!state.busy())return;if(state.ownRun||state.pendingAction.equals("START"))cookingReview.localStop();cancelled=true;state.closing=true;write("QUIT");changed();
    }
    public void acknowledgePhysicalCheck(){acknowledgePhysicalCheck(false);}
    public void acknowledgePhysicalCheck(boolean lidless){
        if(!state.canInspect(SystemClock.elapsedRealtime(),lidless))return;
        if(!app.getSharedPreferences("control-errors",0).edit().putInt("lastError",0).commit()){note("Fehlerbestätigung konnte nicht gespeichert werden. Steuerung bleibt gesperrt.");return;}
        state.uncertainRun=false;state.ownRun=false;state.everOwnedRun=false;if(!state.connected)state.idleConfirmed=false;
        state.deviceFaultLatched=false;state.lastDeviceError=0;
        state.userIssue="";
        note(state.connected?"Maschinenzustand vor Ort geprüft; bestätigter Leerlauf liegt vor. Kein Lauf gestartet.":"Maschinenzustand vom Bediener geprüft. Für einen neuen Lauf erneut verbinden.");
    }
    private boolean write(String command){
        synchronized(io){try{if(writer==null)return false;writer.write(command);writer.newLine();writer.flush();return true;}
            catch(IOException e){cancelled=true;state.closing=true;note("Steuerverbindung unterbrochen. Gerät und Stoppbestätigung prüfen.");return false;}}
    }
}
