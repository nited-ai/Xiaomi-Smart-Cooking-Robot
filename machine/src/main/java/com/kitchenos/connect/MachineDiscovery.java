package com.kitchenos.connect;
import android.content.Context;
import android.net.*;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.*;

/** Discovery is only an address hint; phones still verify the pinned TLS certificate. */
public final class MachineDiscovery {
    private final NsdManager manager;
    private final ConnectivityManager connectivity;
    private final WifiManager wifi;
    private final String name;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private boolean observing;
    private String advertised;
    private Registration registration;
    private final Runnable refresh=this::publish;
    private final ConnectivityManager.NetworkCallback network=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network value){changed();}
        @Override public void onLost(Network value){changed();}
        @Override public void onLinkPropertiesChanged(Network value,LinkProperties properties){changed();}
    };
    public MachineDiscovery(Context context,String pin){
        manager=context.getSystemService(NsdManager.class);
        connectivity=context.getSystemService(ConnectivityManager.class);
        wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        name="Kalomatik-"+pin.substring(0,12);
        try{connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),network);observing=true;}catch(RuntimeException ignored){}
        main.post(refresh);
    }
    private void changed(){if(!closed){main.removeCallbacks(refresh);main.postDelayed(refresh,750);}}
    private void publish(){
        if(closed)return;
        int ip=wifi.getConnectionInfo().getIpAddress();
        String address=ip==0?"":String.format(java.util.Locale.ROOT,"%d.%d.%d.%d",ip&255,ip>>8&255,ip>>16&255,ip>>24&255);
        if(address.equals(advertised)&&registration!=null)return;
        advertised=address;if(registration!=null)registration.close();
        NsdServiceInfo service=new NsdServiceInfo();service.setServiceType("_kitchenos._tcp.");service.setServiceName(name);service.setPort(8765);
        // This address hint still requires the QR-pinned TLS certificate.
        if(!address.isEmpty())service.setAttribute("ipv4",address);
        registration=new Registration();
        try{manager.registerService(service,NsdManager.PROTOCOL_DNS_SD,registration);}catch(RuntimeException ignored){registration=null;}
    }
    public void close(){
        closed=true;main.removeCallbacks(refresh);
        if(observing){observing=false;try{connectivity.unregisterNetworkCallback(network);}catch(RuntimeException ignored){}}
        main.post(()->{if(registration!=null){registration.close();registration=null;}});
    }
    private final class Registration implements NsdManager.RegistrationListener {
        private boolean registered,retired;
        synchronized void close(){retired=true;if(registered){registered=false;try{manager.unregisterService(this);}catch(RuntimeException ignored){}}}
        public synchronized void onServiceRegistered(NsdServiceInfo info){registered=true;if(retired||closed)close();}
        public void onRegistrationFailed(NsdServiceInfo info,int error){android.util.Log.w("KalomatikDiscovery","Advertisement failed: "+error);}
        public synchronized void onServiceUnregistered(NsdServiceInfo info){registered=false;}
        public void onUnregistrationFailed(NsdServiceInfo info,int error){}
    }
}
