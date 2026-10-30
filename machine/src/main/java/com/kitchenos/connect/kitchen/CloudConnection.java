package com.kitchenos.connect.kitchen;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;

/** A bounded DNS check before starting a paid recipe request. Never runs on the UI thread. */
public final class CloudConnection {
    private CloudConnection(){}
    // Android's resolver can outlive an interrupt. Keep at most one such lookup alive.
    private static final ThreadPoolExecutor dns=new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1),task->{Thread thread=new Thread(task,"kitchen-dns");thread.setDaemon(true);return thread;});
    private static final String NETWORK="Die Maschine erreicht den Onlinedienst gerade nicht. Bitte prüfe WLAN und Internet und versuche es erneut.";
    public static void requireOnline(Context context,String host)throws IOException{
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps=manager==null?null:manager.getNetworkCapabilities(manager.getActiveNetwork());
        // Validation can be unavailable on otherwise usable networks; do not require it.
        if(caps==null||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))throw new IOException("Keine Internetverbindung. Bitte verbinde die Maschine unter Einstellungen → WLAN mit dem Internet.");
        resolve(()->InetAddress.getAllByName(host),5000);
    }
    private static void resolve(Callable<InetAddress[]> lookup,long timeout)throws IOException{
        Future<InetAddress[]> task;
        try{task=dns.submit(lookup);}catch(RejectedExecutionException busy){throw new IOException(NETWORK,busy);}
        try{if(task.get(timeout,TimeUnit.MILLISECONDS).length==0)throw new IOException(NETWORK);}
        catch(TimeoutException|ExecutionException error){throw new IOException(NETWORK,error);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Die Verbindungsprüfung wurde abgebrochen.",interrupted);}
        finally{task.cancel(true);dns.remove((Runnable)task);}
    }
    public static String message(Throwable error){
        for(Throwable cause=error;cause!=null;cause=cause.getCause()){
            if(cause instanceof UnknownHostException||cause instanceof ConnectException||cause instanceof NoRouteToHostException)return NETWORK;
            if(cause instanceof SocketTimeoutException)return "Der Onlinedienst antwortet gerade nicht rechtzeitig. Bitte versuche es erneut.";
        }
        return error.getMessage()==null?"Die KI-Aufbereitung ist gerade nicht verfügbar.":error.getMessage();
    }
}
