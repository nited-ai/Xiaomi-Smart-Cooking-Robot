package com.kitchenos.connect.kitchen;

import android.content.*;
import android.net.Uri;
import android.security.keystore.*;
import android.util.Base64;
import com.kitchenos.connect.BuildConfig;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Hosted Firebase login in the system browser, PKCE-bound return; no embedded OAuth WebView. */
public final class CloudSession {
    private static final java.util.concurrent.ExecutorService CANCEL_IO=java.util.concurrent.Executors.newFixedThreadPool(2,work->{Thread thread=new Thread(work,"cloud-cancel");thread.setDaemon(true);return thread;});
    private static final ThreadLocal<android.os.CancellationSignal> REQUEST_CANCEL=new ThreadLocal<>();
    private static final ThreadLocal<Context> REQUEST_CONTEXT=new ThreadLocal<>();
    private static final ThreadLocal<Long> REQUEST_DEADLINE=new ThreadLocal<>();
    public JSONObject api(String path,JSONObject body,android.os.CancellationSignal cancellation)throws Exception{android.os.CancellationSignal previous=REQUEST_CANCEL.get();REQUEST_CANCEL.set(cancellation);Context previousContext=REQUEST_CONTEXT.get();Long previousDeadline=REQUEST_DEADLINE.get();REQUEST_CONTEXT.set(app);REQUEST_DEADLINE.set(android.os.SystemClock.elapsedRealtime()+120000);try{cancellation.throwIfCanceled();return api(path,body);}finally{if(previous==null)REQUEST_CANCEL.remove();else REQUEST_CANCEL.set(previous);if(previousContext==null)REQUEST_CONTEXT.remove();else REQUEST_CONTEXT.set(previousContext);if(previousDeadline==null)REQUEST_DEADLINE.remove();else REQUEST_DEADLINE.set(previousDeadline);}}
    private static int remainingRequestMillis(int requested)throws IOException{Long deadline=REQUEST_DEADLINE.get();if(deadline==null)return requested;long left=deadline-android.os.SystemClock.elapsedRealtime();if(left<=0)throw new SocketTimeoutException("Request deadline reached");return (int)Math.min(requested,left);}
    private static void attachCancellation(HttpURLConnection connection,android.os.CancellationSignal cancellation){if(cancellation!=null)cancellation.setOnCancelListener(()->CANCEL_IO.execute(connection::disconnect));}
    public static final class ApiException extends IOException {public final String code;public final int status;ApiException(String message,String code){this(message,code,0);}ApiException(String message,String code,int status){super(message);this.code=code;this.status=status;}}
    private static final String ALIAS="KitchenOSAccountStorage";
    private static final String DEVICE_KEY="KalomatikKitchenDeviceProof";
    private KeyPair deviceKey()throws Exception{synchronized(SESSION_LOCK){KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(!store.containsAlias(DEVICE_KEY)){KeyPairGenerator generator=KeyPairGenerator.getInstance("EC","AndroidKeyStore");generator.initialize(new KeyGenParameterSpec.Builder(DEVICE_KEY,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build());generator.generateKeyPair();}return new KeyPair(store.getCertificate(DEVICE_KEY).getPublicKey(),(PrivateKey)store.getKey(DEVICE_KEY,null));}}
    private String deviceSignature(String value)throws Exception{Signature signature=Signature.getInstance("SHA256withECDSA");signature.initSign(deviceKey().getPrivate());signature.update(value.getBytes(StandardCharsets.UTF_8));return Base64.encodeToString(signature.sign(),Base64.NO_WRAP);}

    private static final Object SESSION_LOCK=new Object();
    // Bind the memory entry to the exact encrypted preferences value. Logout, login,
    // token replacement and test/account stores invalidate it without a time window.
    private static final java.util.WeakHashMap<SharedPreferences,java.util.Map<String,String[]>> decoded=new java.util.WeakHashMap<>();
    private final Context app;
    private final String boundUid;
    public CloudSession(Context context){app=context.getApplicationContext();boundUid=account().optString("uid");}
    private static String base(){return BuildConfig.KITCHEN_API_BASE.replaceAll("/+$","");}
    public static boolean configured(){try{URI uri=new URI(BuildConfig.KITCHEN_API_BASE);return "https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null;}catch(Exception e){return false;}}
    private javax.crypto.SecretKey key()throws Exception{
        synchronized(SESSION_LOCK){KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(!store.containsAlias(ALIAS)){KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}return (javax.crypto.SecretKey)store.getKey(ALIAS,null);}
    }
    private JSONObject read(String name)throws Exception{synchronized(SESSION_LOCK){
        SharedPreferences prefs=app.getSharedPreferences("account",0);String saved=prefs.getString(name,"");
        java.util.Map<String,String[]> entries=decoded.get(prefs);
        if(saved.isEmpty()){if(entries!=null)entries.remove(name);return new JSONObject();}
        String[] cached=entries==null?null:entries.get(name);if(cached!=null&&saved.equals(cached[0]))return new JSONObject(cached[1]);
        String[] parts=saved.split("\\.");if(parts.length!=2)throw new IOException("Kontositzung kann nicht gelesen werden");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        String clear=new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);JSONObject value=new JSONObject(clear);
        if(entries==null){entries=new java.util.HashMap<>();decoded.put(prefs,entries);}entries.put(name,new String[]{saved,clear});return value;
    }}
    private void write(String name,JSONObject data)throws Exception{synchronized(SESSION_LOCK){
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());String stored=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+"."+Base64.encodeToString(cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);if(!app.getSharedPreferences("account",0).edit().putString(name,stored).commit())throw new IOException("Kontositzung konnte nicht gespeichert werden");
    }}
    public JSONObject account(){try{JSONObject s=read("session");return new JSONObject().put("uid",s.optString("uid")).put("email",s.optString("email"));}catch(Exception e){return new JSONObject();}}
    public void logout(){synchronized(SESSION_LOCK){android.content.SharedPreferences p=app.getSharedPreferences("account",0);p.edit().putLong("epoch",p.getLong("epoch",0)+1).remove("session").remove("pending").commit();decoded.remove(p);}}
    private static String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    public void login(android.app.Activity activity,String provider)throws Exception{
        if(!configured())throw new IOException("Die Kontoanbindung ist noch nicht freigeschaltet. Lokal kannst du KitchenOS bereits verwenden.");
        if(!java.util.Arrays.asList("google","microsoft","facebook","apple","email").contains(provider))throw new IllegalArgumentException("Unbekannte Anmeldung");
        String state=random(),verifier=random();synchronized(SESSION_LOCK){android.content.SharedPreferences p=app.getSharedPreferences("account",0);long epoch=p.getLong("epoch",0)+1;if(!p.edit().putLong("epoch",epoch).commit())throw new IOException("Anmeldung konnte nicht vorbereitet werden");write("pending",new JSONObject().put("state",state).put("verifier",verifier).put("at",System.currentTimeMillis()).put("epoch",epoch));}
        String challenge=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
        Uri url=Uri.parse(base()+"/auth/start").buildUpon().appendQueryParameter("provider",provider).appendQueryParameter("state",state).appendQueryParameter("code_challenge",challenge).appendQueryParameter("redirect_uri","kitchenos://account/callback").build();
        activity.startActivity(new Intent(Intent.ACTION_VIEW,url));
    }
    /** Appliance flow: scan a browser login on the phone, then redeem only on this device. */
    public JSONObject startDevice(String provider)throws Exception{
        if(!configured()||!java.util.Arrays.asList("google","microsoft","facebook","apple","email").contains(provider))throw new IOException("Kontoanbindung ist noch nicht freigeschaltet");
        String state=random(),verifier=random();
        String proof=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
        synchronized(SESSION_LOCK){android.content.SharedPreferences p=app.getSharedPreferences("account",0);long epoch=p.getLong("epoch",0)+1;if(!p.edit().putLong("epoch",epoch).commit())throw new IOException("Anmeldung konnte nicht vorbereitet werden");write("pending",new JSONObject().put("state",state).put("verifier",verifier).put("at",System.currentTimeMillis()).put("epoch",epoch));}
        try{
            String id=request(base()+"/v1/auth/device/start",new JSONObject().put("codeChallenge",proof).put("publicKey",Base64.encodeToString(deviceKey().getPublic().getEncoded(),Base64.NO_WRAP)),null).getString("deviceId");if(!id.matches("[A-Za-z0-9_-]{43}"))throw new IOException("Ungültige Geräteanmeldung");
            synchronized(SESSION_LOCK){JSONObject pending=read("pending");if(!state.equals(pending.optString("state")))throw new IOException("Anmeldung wurde inzwischen ersetzt");pending.put("deviceId",id);write("pending",pending);}
            Uri url=Uri.parse(base()+"/auth/start").buildUpon().appendQueryParameter("provider",provider).appendQueryParameter("state",state).appendQueryParameter("code_challenge",proof).appendQueryParameter("device_id",id).appendQueryParameter("redirect_uri","kitchenos://account/callback").build();
            return new JSONObject().put("state",state).put("url",url.toString());
        }catch(Exception error){cancelLogin(state);throw error;}
    }
    public boolean pollDevice(String state)throws Exception{
        JSONObject pending=read("pending");if(!state.equals(pending.optString("state")))throw new IOException("Anmeldung wurde abgebrochen oder ersetzt");
        JSONObject reply=request(base()+"/v1/auth/device/poll",new JSONObject().put("deviceId",pending.getString("deviceId")).put("codeVerifier",pending.getString("verifier")),null);
        if(reply.getString("status").equals("pending"))return false;
        if(!reply.getString("status").equals("approved"))throw new IOException("Ungültige Anmeldeantwort");
        complete(Uri.parse("kitchenos://account/callback").buildUpon().appendQueryParameter("state",state).appendQueryParameter("code",reply.getString("code")).build());return true;
    }
    public void cancelLogin(String state)throws Exception{synchronized(SESSION_LOCK){
        if(!state.equals(read("pending").optString("state")))return;
        android.content.SharedPreferences p=app.getSharedPreferences("account",0);long epoch=p.getLong("epoch",0)+1;
        JSONObject current=read("session");if(!current.optString("uid").isEmpty())write("session",current.put("epoch",epoch));
        if(!p.edit().putLong("epoch",epoch).remove("pending").commit())throw new IOException("Anmeldeabbruch konnte nicht gespeichert werden");
    }}
    public void complete(Uri callback)throws Exception{
        if(!configured()||!"kitchenos".equals(callback.getScheme())||!"account".equals(callback.getHost())||!"/callback".equals(callback.getPath()))throw new IOException("Ungültige Kontoantwort");
        JSONObject pending=read("pending");String expected=pending.optString("state"),received=callback.getQueryParameter("state"),code=callback.getQueryParameter("code");
        if(expected.isEmpty()||received==null||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),received.getBytes(StandardCharsets.US_ASCII))||System.currentTimeMillis()-pending.optLong("at",0)>900000||System.currentTimeMillis()<pending.optLong("at",0)||code==null||!code.matches("[A-Za-z0-9_-]{43}"))throw new IOException("Anmeldung ist abgelaufen oder gehört zu einer anderen Sitzung. Bitte erneut anmelden.");
        JSONObject exchange=request(base()+"/v1/auth/exchange",new JSONObject().put("code",code).put("codeVerifier",pending.getString("verifier")).put("deviceSignature",deviceSignature("kitchen-login-v1\n"+code+"\n"+pending.getString("verifier"))),null);
        String apiKey=exchange.getString("firebaseApiKey"),attestation=exchange.getString("appCheckToken");
        android.util.Log.i("KitchenOSAccount","Device approval redeemed; signing in to Firebase");
        JSONObject tokens=exchange("https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key="+URLEncoder.encode(apiKey,"UTF-8"),new JSONObject().put("token",exchange.getString("customToken")).put("returnSecureToken",true).toString().getBytes(StandardCharsets.UTF_8),"application/json",null,attestation);
        android.util.Log.i("KitchenOSAccount","Firebase sign-in accepted; verifying account binding");
        JSONObject user=exchange("https://identitytoolkit.googleapis.com/v1/accounts:lookup?key="+URLEncoder.encode(apiKey,"UTF-8"),new JSONObject().put("idToken",tokens.getString("idToken")).toString().getBytes(StandardCharsets.UTF_8),"application/json",null,attestation).getJSONArray("users").getJSONObject(0);
        if(!user.getString("localId").equals(exchange.getString("uid")))throw new IOException("Kontozuordnung konnte nicht bestätigt werden");
        synchronized(SESSION_LOCK){if(app.getSharedPreferences("account",0).getLong("epoch",0)!=pending.getLong("epoch")||!expected.equals(read("pending").optString("state")))throw new IOException("Anmeldung wurde inzwischen abgebrochen oder ersetzt");write("session",new JSONObject().put("uid",user.getString("localId")).put("email",user.optString("email")).put("idToken",tokens.getString("idToken")).put("refreshToken",tokens.getString("refreshToken")).put("apiKey",apiKey).put("appCheckToken",exchange.optString("appCheckToken")).put("appCheckExpiresAt",exchange.optLong("appCheckExpiresAt")).put("deviceKeyId",exchange.optString("deviceKeyId")).put("epoch",pending.getLong("epoch")).put("expiresAt",System.currentTimeMillis()+Long.parseLong(tokens.getString("expiresIn"))*1000));app.getSharedPreferences("account",0).edit().remove("pending").commit();}
    }
    private String token()throws Exception{
        JSONObject session=read("session");if(boundUid.isEmpty()||!boundUid.equals(session.optString("uid")))throw new IOException("Bitte zuerst anmelden. Dein Konto hat sich möglicherweise geändert.");
        if(session.optLong("expiresAt")>System.currentTimeMillis()+60000)return session.getString("idToken");
        // Firebase's documented refresh endpoint accepts form-encoded refresh tokens.
        String form="grant_type=refresh_token&refresh_token="+URLEncoder.encode(session.getString("refreshToken"),"UTF-8");
        JSONObject refreshed=exchange("https://securetoken.googleapis.com/v1/token?key="+URLEncoder.encode(session.getString("apiKey"),"UTF-8"),form.getBytes(StandardCharsets.UTF_8),"application/x-www-form-urlencoded",null,appCheck(""));
        if(!session.getString("uid").equals(refreshed.getString("user_id")))throw new IOException("Kontozuordnung hat sich geändert");
        synchronized(SESSION_LOCK){if(app.getSharedPreferences("account",0).getLong("epoch",0)!=session.getLong("epoch")||!session.getString("uid").equals(read("session").optString("uid")))throw new IOException("Konto wurde inzwischen gewechselt");session=read("session");session.put("idToken",refreshed.getString("id_token")).put("refreshToken",refreshed.getString("refresh_token")).put("expiresAt",System.currentTimeMillis()+Long.parseLong(refreshed.getString("expires_in"))*1000);write("session",session);return session.getString("idToken");}
    }
    public JSONObject api(String path,JSONObject body)throws Exception{if(!configured()||!path.matches("/v1/[a-z/-]+"))throw new IOException("Die Online-Funktion ist noch nicht freigeschaltet.");String bearer=token();return exchange(base()+path,body.toString().getBytes(StandardCharsets.UTF_8),"application/json",bearer,appCheck(bearer),1048576,path.equals("/v1/assistant/message")||path.equals("/v1/recipes/create")||path.equals("/v1/recipes/prepare")?110000:45000);}
    /** Existing Kalomatik account sync; authenticates this device as itself, never as the PWA. */
    public JSONObject planPull(long cursor)throws Exception{if(cursor<0||cursor>9007199254740991L)throw new IOException("Ungültiger Planungsstand");return calomatikSync(new JSONObject().put("action","pull").put("cursor",cursor));}
    public JSONObject calomatikSync(JSONObject input)throws Exception{
        if(!java.util.Arrays.asList("pull","push").contains(input.optString("action")))throw new IOException("Dieser Synchronisationsauftrag ist nicht freigegeben.");return syncExchange(input);
    }
    public JSONObject cookingMemorySync(JSONObject input)throws Exception{return CookingMemorySync.call(input,this::syncExchange);}
    private JSONObject syncExchange(JSONObject input)throws Exception{String bearer=token();JSONObject answer=exchange("https://europe-west1-nutrisnap-17e83.cloudfunctions.net/calomaticSync",new JSONObject().put("data",input).toString().getBytes(StandardCharsets.UTF_8),"application/json",bearer,appCheck(bearer),2097152,65000);if(!boundUid.equals(read("session").optString("uid")))throw new IOException("Konto wurde inzwischen gewechselt");JSONObject result=answer.optJSONObject("result");if(result==null)result=answer.optJSONObject("data");if(result==null||answer.has("error"))throw new IOException("Dein Kalomatik-Kochplan konnte nicht bestätigt werden.");return result;
    }

    public JSONObject recipeCover(String title,JSONArray ingredientNames)throws Exception{return imageCatalog(new JSONObject().put("style","dish-photo-v1").put("dish",title).put("ingredients",ingredientNames));}
    /** Same shared, normalized ingredient catalogue as Kalomatik; never sends amounts or pantry photos. */
    public JSONObject ingredientPortrait(JSONObject request)throws Exception{return ingredientPortrait(request,290000);}
    public JSONObject ingredientPortrait(JSONObject request,int timeout)throws Exception{if(!"ingredient-cutout-v3".equals(request.optString("style")))throw new IOException("Unbekanntes Zutatenbildformat");return imageCatalog(request,false,Math.max(1000,Math.min(290000,timeout)));}
    public JSONObject ingredientPortrait(String name)throws Exception{return imageCatalog(new JSONObject().put("style","ingredient-cutout-v3").put("name",name).put("amount",100).put("unit","g"));}
    public JSONObject recipeStep(String recipeId,int index)throws Exception{if(!recipeId.matches("[a-f0-9]{64}")||index<0||index>=30)throw new IOException("Ungültiger Bildbezug");return imageCatalog(new JSONObject().put("style","recipe-step-v1").put("recipeId",recipeId).put("index",index));}
    public JSONObject nativeRecipeStep(String dish,JSONArray ingredientNames,String actualStepInstruction)throws Exception{
        JSONObject preferences=new KitchenStore(app).snapshot().optJSONObject("preferences");
        if(preferences==null||!preferences.optBoolean("aiConsent"))throw new IOException("Für ein neues Schrittbild ist deine KI-Zustimmung erforderlich.");
        return imageCatalog(new JSONObject().put("style","native-recipe-step-v1").put("dish",dish).put("ingredients",ingredientNames).put("step",actualStepInstruction),true);
    }
    private JSONObject imageCatalog(JSONObject data)throws Exception{return imageCatalog(data,false);}
    private JSONObject imageCatalog(JSONObject data,boolean nativeRecipeConsent)throws Exception{return imageCatalog(data,nativeRecipeConsent,85000);}
    private JSONObject imageCatalog(JSONObject data,boolean nativeRecipeConsent,int timeout)throws Exception{String bearer=token(),attestation=appCheck(bearer);if(nativeRecipeConsent){JSONObject preferences=new KitchenStore(app).snapshot().optJSONObject("preferences");if(preferences==null||!preferences.optBoolean("aiConsent"))throw new IOException("Die KI-Zustimmung wurde inzwischen geändert.");}JSONObject answer=exchange("https://europe-west1-nutrisnap-17e83.cloudfunctions.net/resolveIngredientImage",new JSONObject().put("data",data).toString().getBytes(StandardCharsets.UTF_8),"application/json",bearer,attestation,2300000,timeout,nativeRecipeConsent);JSONObject result=answer.optJSONObject("result");if(result==null)result=answer.optJSONObject("data");if(result==null)throw new IOException("Rezeptbild ist noch nicht verfügbar");return result;}
    private String appCheck(String bearer)throws Exception{
        JSONObject session=read("session");if(!boundUid.equals(session.optString("uid")))throw new IOException("Konto wurde inzwischen gewechselt");
        if(session.optLong("appCheckExpiresAt")>System.currentTimeMillis()+60000&&!session.optString("appCheckToken").isEmpty())return session.getString("appCheckToken");
        String keyId=session.optString("deviceKeyId");if(!keyId.matches("[a-f0-9]{64}"))throw new IOException("Bitte diese Maschine erneut mit deinem Kalomatik-Konto verbinden");
        JSONObject nonce=request(base()+"/v1/device/challenge",new JSONObject().put("uid",boundUid).put("deviceKeyId",keyId),null);
        JSONObject refreshed=request(base()+"/v1/device/attest",new JSONObject().put("uid",boundUid).put("deviceKeyId",keyId).put("nonce",nonce.getString("nonce")).put("signature",deviceSignature("kitchen-attest-v1\n"+nonce.getString("nonce")+"\n"+keyId)),null);
        synchronized(SESSION_LOCK){JSONObject current=read("session");if(!boundUid.equals(current.optString("uid"))||current.optLong("epoch")!=session.optLong("epoch"))throw new IOException("Konto wurde inzwischen gewechselt");current.put("appCheckToken",refreshed.getString("appCheckToken")).put("appCheckExpiresAt",refreshed.getLong("appCheckExpiresAt"));write("session",current);}return refreshed.getString("appCheckToken");
    }
    public static JSONObject machineCatalog()throws Exception{if(!configured())throw new IOException("Online-Rezepte sind noch nicht verbunden");HttpURLConnection c=(HttpURLConnection)new URL(base()+"/v1/recipes/machine-catalog?controlProfileId="+com.kitchenos.connect.control.ControlProfile.ID).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setRequestProperty("Accept","application/json");try{if(c.getResponseCode()!=200)throw new IOException("Maschinenrezepte sind gerade nicht erreichbar");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>2097152)throw new IOException("Rezeptliste ist zu groß");out.write(buffer,0,n);}return new JSONObject(out.toString("UTF-8"));}}finally{c.disconnect();}}
    private static JSONObject request(String url,JSONObject body,String token)throws Exception{return exchange(url,body.toString().getBytes(StandardCharsets.UTF_8),"application/json",token);}
    private static JSONObject exchange(String url,byte[] body,String type,String token)throws Exception{return exchange(url,body,type,token,null);}
    private static JSONObject exchange(String url,byte[] body,String type,String token,String appCheck)throws Exception{return exchange(url,body,type,token,appCheck,1048576,45000);}
    private static JSONObject exchange(String url,byte[] body,String type,String token,String appCheck,int maxResponse,int timeout)throws Exception{
        return exchange(url,body,type,token,appCheck,maxResponse,timeout,false);
    }
    private static JSONObject exchange(String url,byte[] body,String type,String token,String appCheck,int maxResponse,int timeout,boolean nativeRecipeConsent)throws Exception{
        android.os.CancellationSignal cancellation=REQUEST_CANCEL.get();if(cancellation!=null)cancellation.throwIfCanceled();if(!url.startsWith("https://"))throw new IOException("Verschlüsselte Verbindung erforderlich");Context context=REQUEST_CONTEXT.get();if(context!=null){CloudConnection.requireOnline(context,new URI(url).getHost());cancellation.throwIfCanceled();}remainingRequestMillis(timeout);HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(remainingRequestMillis(10000));c.setReadTimeout(remainingRequestMillis(timeout));c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type",type);if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);if(appCheck!=null)c.setRequestProperty("X-Firebase-AppCheck",appCheck);if(nativeRecipeConsent)c.setRequestProperty("X-Kalomatik-Consent","v1");c.setFixedLengthStreamingMode(body.length);
        try{attachCancellation(c,cancellation);if(cancellation!=null)cancellation.throwIfCanceled();try(OutputStream out=c.getOutputStream()){out.write(body);}int code=c.getResponseCode();if(code!=200&&code!=201){String message=code==401?"Anmeldung ist nicht mehr gültig. Bitte erneut anmelden.":code==429?"Zu viele Anfragen. Bitte später erneut versuchen.":"Online-Anfrage konnte nicht abgeschlossen werden ("+code+").";String kind="";if(c.getErrorStream()!=null)try(InputStream in=c.getErrorStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1&&bytes.size()<8192)bytes.write(buffer,0,n);JSONObject error=new JSONObject(bytes.toString("UTF-8"));String detail=error.optString("error");if(error.opt("error") instanceof String&&detail.length()<=512&&!detail.isEmpty())message=detail;kind=error.optString("code");JSONObject callable=error.optJSONObject("error");if(callable!=null){String reason=callable.optString("message");if(!reason.isEmpty()&&reason.length()<=512)message=reason;kind=callable.optString("status",kind);}}catch(Exception ignored){}throw new ApiException(message,kind,code);}try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(cancellation!=null)cancellation.throwIfCanceled();remainingRequestMillis(timeout);if(bytes.size()+n>maxResponse)throw new IOException("Antwort ist zu groß");bytes.write(buffer,0,n);}return new JSONObject(bytes.toString("UTF-8"));}}finally{c.disconnect();if(cancellation!=null)cancellation.setOnCancelListener(null);}
    }
}
