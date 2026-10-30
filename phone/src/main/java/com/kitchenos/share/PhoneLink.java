package com.kitchenos.share;

import android.content.*;
import android.net.*;
import android.net.nsd.*;
import android.os.SystemClock;
import com.kitchenos.common.Pairing;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;
import java.net.InetAddress;
import java.net.Inet4Address;
import android.os.Build;

/** Pinned local transport. Discovery never establishes trust by itself. Call on a worker. */
public final class PhoneLink {
    public static final class Rejected extends IOException {
        public Rejected(){super("Kopplung abgelaufen oder entfernt. Bitte einen neuen Code an der Maschine öffnen.");}
    }
    public static JSONObject call(Pairing pairing,Network network,String endpoint,JSONObject body)throws Exception{
        return call(pairing,network,endpoint,body,8000,10000);
    }
    private static JSONObject call(Pairing pairing,Network network,String endpoint,JSONObject body,int connectTimeout,int readTimeout)throws Exception{
        HttpsURLConnection connection=pairing.connection("/v1/device/"+endpoint,network);
        connection.setConnectTimeout(connectTimeout);connection.setReadTimeout(readTimeout);
        try{
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream output=connection.getOutputStream()){output.write(bytes);}
            int status=connection.getResponseCode();
            if(status==401)throw new Rejected();
            if(status!=200&&status!=201)throw new IOException(status==409?"Dieser Schritt ist gerade nicht verfügbar. Prüfe die Einrichtung auf der Maschine.":"Die Maschine hat die Eingabe nicht angenommen ("+status+").");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream result=new ByteArrayOutputStream()){
                byte[] buffer=new byte[2048];int count;
                while((count=input.read(buffer))!=-1){if(result.size()+count>65536)throw new IOException("Antwort zu groß");result.write(buffer,0,count);}
                return new JSONObject(result.toString("UTF-8"));
            }
        }finally{connection.disconnect();}
    }
    private static String random(){return java.util.UUID.randomUUID().toString().replace("-","");}
    public static Pairing enroll(Context context,Pairing invitation,Network network,String name)throws Exception{
        SharedPreferences preferences=context.getSharedPreferences("phone-credentials",0);
        String key=invitation.pin;
        String id=preferences.getString(key+".id",""),token=preferences.getString(key+".token","");
        if(id.isEmpty()||token.isEmpty()){
            id=random();token=random();
            // Persist before POST, so a lost acknowledgement can be retried idempotently.
            if(!preferences.edit().putString(key+".id",id).putString(key+".token",token).commit())throw new IOException("Kopplung konnte nicht gespeichert werden.");
        }
        JSONObject answer=call(invitation,network,"enroll",new JSONObject().put("id",id).put("token",token).put("name",name));
        if(!answer.optBoolean("paired")||!id.equals(answer.optString("id"))||!key.equals(answer.optString("device")))throw new IOException("Kopplung nicht bestätigt.");
        Pairing paired=new Pairing(invitation.host,token,key);save(context,paired);return paired;
    }
    public static void save(Context context,Pairing pairing)throws IOException{
        if(!context.getSharedPreferences("pairing",0).edit().putString("uri",pairing.uri()).commit())throw new IOException("Verbindung konnte nicht gespeichert werden.");
    }
    public static Network wifi(Context context){
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);
        Network active=manager.getActiveNetwork();NetworkCapabilities current=manager.getNetworkCapabilities(active);
        if(localWifi(current))return active;
        Network fallback=null;
        for(Network network:manager.getAllNetworks()){
            NetworkCapabilities capabilities=manager.getNetworkCapabilities(network);
            if(localWifi(capabilities)){
                if(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))return network;
                fallback=network;
            }
        }
        return fallback;
    }
    public static boolean localWifi(NetworkCapabilities capabilities){
        // VPNs inherit their underlying WIFI transport. They cannot be used as
        // the local link to a machine, even if INTERNET/VALIDATED are present.
        return capabilities!=null&&capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                &&!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                &&capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }
    private static void verify(Pairing pairing,Network network)throws Exception{
        if(!pairing.pin.equals(call(pairing,network,"status",new JSONObject(),1800,1800).optString("device")))throw new IOException("Andere Maschine gefunden.");
    }
    /** DNS-SD is an address hint, never proof of identity. Keep every supported address. */
    public static List<String> addresses(NsdServiceInfo service){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        byte[] hint=service.getAttributes().get("ipv4");
        if(hint!=null){String host=new String(hint,StandardCharsets.US_ASCII);if(host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))result.add(host);}
        List<InetAddress> hosts=Build.VERSION.SDK_INT>=34?service.getHostAddresses():Collections.singletonList(service.getHost());
        for(InetAddress host:hosts)if(host instanceof Inet4Address)result.add(host.getHostAddress());
        return new ArrayList<>(result);
    }
    private static String serviceKey(NsdServiceInfo service){return service.getServiceName()+"@"+(Build.VERSION.SDK_INT>=33?service.getNetwork():"legacy");}
    public static Pairing resolve(Context context,Pairing known,long timeout)throws Exception{
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IllegalStateException("Network on main thread");
        final long deadline=SystemClock.elapsedRealtime()+Math.min(timeout,90000);
        NsdManager manager=context.getSystemService(NsdManager.class);
        BlockingQueue<String> addresses=new LinkedBlockingQueue<>(16);
        AtomicBoolean closed=new AtomicBoolean(),resolving=new AtomicBoolean();
        Map<String,NsdServiceInfo> services=new ConcurrentHashMap<>();
        Map<String,NsdManager.ServiceInfoCallback> callbacks=new ConcurrentHashMap<>();
        String prefix="Kalomatik-"+known.pin.substring(0,12);
        java.util.function.Consumer<NsdServiceInfo> update=service->{
            if(closed.get()||service.getPort()!=8765)return;
            List<String> candidates=addresses(service);
            android.util.Log.i("KalomatikDiscovery","Address update: ipv4="+candidates.size()+", legacy="+(service.getHost()==null?"none":service.getHost().getClass().getSimpleName()));
            for(String address:candidates)addresses.offer(address);
        };
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String type){}
            public void onDiscoveryStopped(String type){}
            public void onStartDiscoveryFailed(String type,int error){closed.set(true);}
            public void onStopDiscoveryFailed(String type,int error){}
            public void onServiceLost(NsdServiceInfo info){
                String key=serviceKey(info);services.remove(key);
                synchronized(callbacks){NsdManager.ServiceInfoCallback callback=callbacks.remove(key);if(Build.VERSION.SDK_INT>=34&&callback!=null)try{manager.unregisterServiceInfoCallback(callback);}catch(RuntimeException ignored){}}
            }
            public void onServiceFound(NsdServiceInfo info){
                if(closed.get()||!info.getServiceName().startsWith(prefix))return;
                String key=serviceKey(info);services.put(key,info);
                if(Build.VERSION.SDK_INT>=34){
                    NsdManager.ServiceInfoCallback callback=new NsdManager.ServiceInfoCallback(){
                        public void onServiceUpdated(NsdServiceInfo service){update.accept(service);}
                        public void onServiceLost(){}
                        public void onServiceInfoCallbackUnregistered(){}
                        public void onServiceInfoCallbackRegistrationFailed(int error){callbacks.remove(key,this);android.util.Log.w("KalomatikDiscovery","Address subscription failed: "+error);}
                    };
                    synchronized(callbacks){if(!closed.get()&&callbacks.putIfAbsent(key,callback)==null)try{manager.registerServiceInfoCallback(info,Runnable::run,callback);}catch(RuntimeException error){callbacks.remove(key,callback);}}
                }
            }
        };
        try{
            manager.discoverServices("_kitchenos._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);
            Map<String,Long> attempted=new HashMap<>();Set<String> candidates=new LinkedHashSet<>();candidates.add(known.host);
            long nextResolve=0;Network previous=null;
            while(!closed.get()&&SystemClock.elapsedRealtime()<deadline){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                Network network=wifi(context);
                if(network==null){Thread.sleep(300);continue;}
                if(!network.equals(previous)){previous=network;attempted.clear();}
                String address;while((address=addresses.poll())!=null)if(candidates.size()<16)candidates.add(address);
                long now=SystemClock.elapsedRealtime();
                // Older Android has one-shot resolution. Retry incomplete/stale answers as well.
                if(now>=nextResolve&&!services.isEmpty()&&resolving.compareAndSet(false,true)){
                    NsdServiceInfo info=services.values().iterator().next();nextResolve=now+3000;
                    if(Build.VERSION.SDK_INT>=34&&callbacks.containsKey(serviceKey(info)))resolving.set(false);
                    else try{manager.resolveService(info,new NsdManager.ResolveListener(){
                        public void onResolveFailed(NsdServiceInfo service,int error){resolving.set(false);}
                        public void onServiceResolved(NsdServiceInfo service){resolving.set(false);update.accept(service);}
                    });}catch(RuntimeException error){resolving.set(false);}
                }
                for(String candidate:new ArrayList<>(candidates)){
                    now=SystemClock.elapsedRealtime();if(now>=deadline)break;
                    if(now-attempted.getOrDefault(candidate,Long.MIN_VALUE/2)<4000)continue;
                    attempted.put(candidate,now);
                    try{Pairing found=new Pairing(candidate,known.token,known.pin);verify(found,network);save(context,found);android.util.Log.i("KalomatikDiscovery","Pinned machine verified on Wi-Fi");return found;}
                    catch(Rejected rejected){throw rejected;}catch(Exception wrongOrUnreachable){android.util.Log.d("KalomatikDiscovery","Candidate unavailable: "+wrongOrUnreachable.getClass().getSimpleName());}
                }
                Thread.sleep(200);
            }
            throw new IOException("Maschine noch nicht gefunden. Beide Geräte müssen im selben WLAN sein; Gastnetz-Isolierung darf die Verbindung nicht blockieren. Bei falschem Passwort einen neuen Code an der Maschine öffnen.");
        }finally{closed.set(true);try{manager.stopServiceDiscovery(listener);}catch(Exception ignored){}
            synchronized(callbacks){if(Build.VERSION.SDK_INT>=34)for(NsdManager.ServiceInfoCallback callback:callbacks.values())try{manager.unregisterServiceInfoCallback(callback);}catch(Exception ignored){}}
        }
    }
}
