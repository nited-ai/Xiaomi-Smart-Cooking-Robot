package com.kitchenos.connect.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.util.Base64;
import android.widget.EditText;
import com.kitchenos.common.Pairing;
import com.kitchenos.connect.kitchen.*;
import java.io.ByteArrayOutputStream;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Tap to dictate, tap again to finish. Audio is bounded, memory-only and never auto-sent as chat. */
public final class AssistantVoiceInput {
    public static final String DISCLOSURE="Zum Diktieren wird eine kurze Aufnahme (höchstens 15 Sekunden) an Kalomatiks Dienst und Groq zur Spracherkennung gesendet. KitchenOS speichert keine Audiodatei. Der erkannte Text wird vorübergehend beim Dienst gespeichert und in deinen Entwurf eingefügt. Erst mit Senden geht er als Nachricht an den Assistenten.";
    private final Activity activity;private final Context app;private final EditText editor;private final Consumer<String> feedback;private final Consumer<Boolean> state;
    private final AssistantConversation session;private final SharedPreferences consent;private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean recording,canceled;private boolean closed,transcribing;
    public AssistantVoiceInput(Activity activity,EditText editor,Consumer<String> feedback,Consumer<Boolean> state){
        this.activity=activity;app=activity.getApplicationContext();this.editor=editor;this.feedback=feedback;this.state=state;session=AssistantConversation.get(app);
        try{consent=app.getSharedPreferences("dictation-consent-"+Pairing.fingerprint(session.owner().getBytes(StandardCharsets.UTF_8)),0);}catch(Exception error){throw new IllegalStateException(error);}
    }
    public static void revoke(Context context){try{String uid=new CloudSession(context).account().optString("uid");context.getSharedPreferences("dictation-consent-"+Pairing.fingerprint(uid.getBytes(StandardCharsets.UTF_8)),0).edit().remove("allowed").commit();}catch(Exception error){throw new IllegalStateException(error);}}
    public boolean allowed(){return consent.getBoolean("allowed",false);}
    public void allow(){if(session.sameOwner())consent.edit().putBoolean("allowed",true).commit();}
    public boolean recording(){return recording;}
    public boolean transcribing(){return transcribing;}
    public void toggle(){
        if(closed)return;if(recording){recording=false;return;}if(transcribing||session.dictating())return;
        if(session.owner().isEmpty()){feedback.accept("Melde dich zum Diktieren mit deinem Kalomatik-Konto an. Dein Entwurf bleibt erhalten.");return;}
        if(!allowed())return;
        if(!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)){feedback.accept("Dieses Gerät stellt kein Mikrofon bereit. Du kannst deine Nachricht weiterhin eintippen.");return;}
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},2107);feedback.accept("Erlaube den Mikrofonzugriff und tippe danach erneut auf das Mikrofon.");return;}
        String previous=editor.getText().toString();int from=Math.max(0,editor.getSelectionStart()),to=Math.max(from,editor.getSelectionEnd());
        if(!session.beginDictation())return;recording=true;canceled=false;state.accept(true);
        CloudSession cloud=new CloudSession(app);new Thread(()->capture(cloud,previous,from,to),"assistant-dictation").start();
    }
    private void capture(CloudSession cloud,String previous,int from,int to){
        AudioRecord source=null;String result=null,error=null;
        try{
            int minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(minimum<0)throw new IllegalStateException();
            source=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum,8192));
            if(source.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException();source.startRecording();
            ByteArrayOutputStream pcm=new ByteArrayOutputStream();short[] buffer=new short[2048];long until=SystemClock.elapsedRealtime()+15000;
            while(recording&&SystemClock.elapsedRealtime()<until&&pcm.size()<480000){int count=source.read(buffer,0,Math.min(buffer.length,(480000-pcm.size())/2),AudioRecord.READ_NON_BLOCKING);if(count<0)throw new IllegalStateException();for(int i=0;i<count;i++){pcm.write(buffer[i]&255);pcm.write((buffer[i]>>8)&255);}if(count==0)SystemClock.sleep(20);}
            source.stop();source.release();source=null;recording=false;
            if(canceled)return;
            if(pcm.size()<16000){error="Das Diktat war zu kurz. Dein bisheriger Text bleibt erhalten.";return;}
            byte[] audio=wav(pcm.toByteArray());transcribing=true;main.post(()->{if(!closed)state.accept(true);});
            if(!allowed()||!session.sameOwner())throw new IllegalStateException();
            JSONObject response=cloud.api("/v1/assistant/transcribe",new JSONObject().put("requestId",UUID.randomUUID().toString()).put("consent",true).put("audio",new JSONObject().put("mimeType","audio/wav").put("data",Base64.encodeToString(audio,Base64.NO_WRAP))));
            result=response.optString("text");if(result.isEmpty())error="Ich habe keine Sprache verstanden. Dein bisheriger Text bleibt erhalten.";
        }catch(Exception failure){error="Das Diktat konnte nicht erkannt werden. Dein bisheriger Text bleibt erhalten.";}
        finally{
            recording=false;if(source!=null){try{source.stop();}catch(Exception ignored){}source.release();}
            String recognized=result,notice=canceled?null:error;main.post(()->{transcribing=false;session.endDictation(canceled?null:recognized,previous,from,to,notice);if(!closed)state.accept(false);});
        }
    }
    public static byte[] wav(byte[] pcm){ByteBuffer data=ByteBuffer.allocate(44+pcm.length).order(ByteOrder.LITTLE_ENDIAN);data.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+pcm.length).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);return data.array();}
    public void close(){closed=true;if(recording){canceled=true;recording=false;}}
}
