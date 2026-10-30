package com.kitchenos.connect;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import com.kitchenos.connect.ui.ApplianceActivity;
import com.kitchenos.connect.kitchen.ImportPreparation;
import com.kitchenos.connect.kitchen.CloudSession;
import com.kitchenos.connect.kitchen.PhotoInbox;
import com.kitchenos.connect.kitchen.KitchenChatActivity;
import com.kitchenos.common.Recipe;
import javax.net.ssl.SSLServerSocket;

public class RecipeReceiverService extends Service {
    public static volatile RecipeReceiverService active;
    public static boolean requestStart(android.content.Context context){android.content.SharedPreferences preferences=context.getSharedPreferences("receiver",0);preferences.edit().putBoolean("enabled",true).commit();try{Intent intent=new Intent(context,RecipeReceiverService.class);if(android.os.Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);preferences.edit().remove("lastError").apply();return true;}catch(RuntimeException error){preferences.edit().putString("lastError","Der Rezeptempfang konnte nicht gestartet werden. Öffne diese Seite erneut und versuche es noch einmal.").apply();return false;}}
    public volatile DeviceIdentity identity;
    public volatile String error;
    private RecipeServer server;
    public PhoneRegistry phones;
    private LocalDeviceApi local;
    private MachineDiscovery discovery;
    private volatile boolean destroyed;
    @Override public void onCreate(){
        super.onCreate();active=this;
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(android.os.Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("recipes","Rezeptempfang",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,ApplianceActivity.class).putExtra("page","receive"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder n=android.os.Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"recipes"):new Notification.Builder(this);
        startForeground(8765,n.setContentTitle("KitchenOS Rezeptempfang").setContentText("Gekoppelte Handys können Rezepte senden").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(open).build());
        new Thread(()->{
            try{
                DeviceIdentity id=new DeviceIdentity(this);
                SSLServerSocket listener=(SSLServerSocket)id.tls.getServerSocketFactory().createServerSocket(8765);
                listener.setEnabledProtocols(new String[]{"TLSv1.2"});
                PhoneRegistry registry=new PhoneRegistry(this);LocalDeviceApi api=new LocalDeviceApi(this,registry,id.pin);
                RecipeServer created=new RecipeServer(listener,api,recipe->{String uid=new CloudSession(this).account().optString("uid");boolean saved=new RecipeStore(this,uid).save(recipe);if(saved)received(recipe);sendBroadcast(new Intent("com.kitchenos.connect.RECIPES_CHANGED").setPackage(getPackageName()),"com.kitchenos.connect.INTERNAL");return saved;},photo->{boolean saved=PhotoInbox.save(this,photo);if(saved)receivedPhoto(photo.getString("id"));return saved;});
                synchronized(this){if(destroyed){created.close();api.close();return;}server=created;phones=registry;local=api;identity=id;server.start();discovery=new MachineDiscovery(this,id.pin);}
            }catch(Exception e){error="Empfang konnte nicht gestartet werden ("+e.getClass().getSimpleName()+")";}
        },"KitchenOS-TLS-Setup").start();
    }
    private void receivedPhoto(String id){Intent target=new Intent(this,KitchenChatActivity.class).putExtra("photoId",id);PendingIntent open=PendingIntent.getActivity(this,id.hashCode(),target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);getSystemService(NotificationManager.class).notify(id.hashCode(),new Notification.Builder(this,"recipes").setContentTitle("Foto vom Handy empfangen").setContentText("Im Assistenten prüfen und senden").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(open).setAutoCancel(true).build());}
    private void received(Recipe recipe){
        Intent target=new Intent(this,ApplianceActivity.class).putExtra("page","recipes").putExtra("recipeId",recipe.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(this,recipe.id.hashCode(),target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder n=new Notification.Builder(this,"recipes");getSystemService(NotificationManager.class).notify(recipe.id.hashCode(),n.setContentTitle(recipe.title).setContentText("Rezept empfangen · Anleitung öffnen").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(open).setAutoCancel(true).build());
    }
    @Override public int onStartCommand(Intent i,int flags,int id){return START_NOT_STICKY;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public synchronized void onDestroy(){destroyed=true;if(server!=null)server.close();if(local!=null)local.close();if(discovery!=null)discovery.close();if(phones!=null)phones.closeInvitation();if(active==this)active=null;stopForeground(true);super.onDestroy();}
}
