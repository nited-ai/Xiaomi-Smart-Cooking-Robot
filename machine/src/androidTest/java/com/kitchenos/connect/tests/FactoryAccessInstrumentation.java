package com.kitchenos.connect.tests;

import android.app.Instrumentation;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.os.*;
import com.kitchenos.connect.control.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Dedicated-emulator transport checks. Never opens the appliance UART. */
public final class FactoryAccessInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args){arguments=args;start();}
    private int assertions;
    private void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    @Override public void onStart(){
        Bundle result=new Bundle();Context app=getTargetContext();File file=new File(app.getFilesDir(),"factory-access.json");byte[] original=null;boolean snapshotTaken=false;
        try {
            java.lang.Process check=Runtime.getRuntime().exec(new String[]{"getprop","ro.kernel.qemu"});
            String qemu=new BufferedReader(new InputStreamReader(check.getInputStream())).readLine();
            if(!"1".equals(qemu)||new File("/dev/ttyS2").exists()||arguments!=null&&"true".equals(arguments.getString("simulateGuardFailure")))throw new IllegalStateException("Dedicated emulator required; refusing appliance execution");
            if(file.isFile())try(FileInputStream in=new FileInputStream(file)){original=read(in);}
            snapshotTaken=true;
            boolean required=arguments!=null&&"true".equals(arguments.getString("requireFactory"));
            if(required){check(original!=null,"Provisioned session required");ControlAccess.verifyIdentity(app,ControlAccess.readSession(app));check(true,"App-UID root identity accepted");}
            checkActiveGuard(app);
            file.delete();check(ControlAccess.readSession(app)==null,"Absent configuration preserves Magisk selection");
            writePrivate(app,"{}".getBytes(StandardCharsets.UTF_8));
            try{ControlAccess.readSession(app);throw new AssertionError("Invalid config accepted");}catch(IOException expected){check(true,"Malformed config rejected");}
            int uid=android.os.Process.myUid();String socket="xcr.access."+uid+".fedcba9876543210";
            FactoryAccessSession session=new FactoryAccessSession(socket,new String(new char[64]).replace('\0','b'),System.currentTimeMillis()/1000+60,uid,System.currentTimeMillis()/1000);
            LocalServerSocket impostor=new LocalServerSocket(socket);CountDownLatch done=new CountDownLatch(1);int[] received={-2};
            new Thread(()->{try(LocalSocket client=impostor.accept()){client.setSoTimeout(3000);received[0]=client.getInputStream().read();}catch(Exception e){received[0]=-3;}finally{done.countDown();}},"FactoryAccess-Impostor").start();
            try{ControlAccess.verifyIdentity(app,session);throw new AssertionError("Non-root socket accepted");}catch(IOException expected){check(true,"Non-root peer rejected");}
            check(done.await(4,TimeUnit.SECONDS),"Impostor connection closed");check(received[0]==-1,"No token sent to non-root peer");impostor.close();
            ComponentName receiver=new ComponentName(app.getPackageName(),"com.kitchenos.connect.control.AccessBootstrapReceiver");
            check("android.permission.DUMP".equals(app.getPackageManager().getReceiverInfo(receiver,0).permission),"Provisioning requires DUMP");
            check(app.checkSelfPermission("android.permission.DUMP")==PackageManager.PERMISSION_DENIED,"Ordinary app cannot authorize provisioning");
            if(original!=null)writePrivate(app,original);
            else file.delete();
            if(required){ControlAccess.verifyIdentity(app,ControlAccess.readSession(app));check(true,"Original authorized session preserved");}
            if(required&&"true".equals(arguments.getString("exerciseChild"))){
                // Dedicated emulator only: missing UART must fail before any MCU traffic.
                java.lang.Process child=ControlAccess.open(app,"PROBE");ExecutorService worker=Executors.newSingleThreadExecutor();
                try{
                    Future<String> output=worker.submit(()->new String(read(child.getInputStream()),StandardCharsets.UTF_8));
                    String text=output.get(12,TimeUnit.SECONDS);
                    check(child.waitFor(2,TimeUnit.SECONDS)&&child.exitValue()==7,"Actual Android bridge exits with missing-UART code");
                    check(text.contains("UART")&&text.contains("\"type\":\"access-exit\""),"Socket Process preserves child output and exit record");
                }finally{child.destroy();worker.shutdownNow();}
            }
            result.putString("result","PASS");result.putInt("assertions",assertions);finish(-1,result);
        }catch(Throwable error){result.putString("result","FAIL: "+error.getClass().getSimpleName()+": "+error.getMessage());finish(0,result);}
        finally{if(snapshotTaken)try{if(original!=null)writePrivate(app,original);else file.delete();}catch(Exception ignored){}}
    }
    private static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];for(int n;(n=in.read(bytes))!=-1;){out.write(bytes,0,n);if(out.size()>8192)throw new IOException("Config too large");}return out.toByteArray();}
    private static void writePrivate(Context app,byte[] bytes)throws Exception{
        try(FileOutputStream out=app.openFileOutput("factory-access.json",Context.MODE_PRIVATE)){out.write(bytes);}
        android.system.Os.chmod(new File(app.getFilesDir(),"factory-access.json").getAbsolutePath(),0600);
    }
    private void checkActiveGuard(Context app)throws Exception{
        java.lang.reflect.Field singleton=ControlClient.class.getDeclaredField("instance");singleton.setAccessible(true);
        Object previous=singleton.get(null);
        java.lang.reflect.Constructor<ControlClient> constructor=ControlClient.class.getDeclaredConstructor(Context.class);constructor.setAccessible(true);
        ControlClient fake=constructor.newInstance(app);
        try{
            runOnMainSync(()->{try{singleton.set(null,fake);fake.state.connected=true;}catch(Exception e){throw new RuntimeException(e);}});
            check(!ControlClient.canReconfigureAccess(),"Connected controller blocks reconfiguration");
            String command="am broadcast --user 0 --receiver-foreground -a com.kitchenos.connect.CONFIGURE_FACTORY_ACCESS -n com.kitchenos.connect/.control.AccessBootstrapReceiver --ez clear true";
            String reply;try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))){reply=new String(read(input),StandardCharsets.UTF_8);}
            check(reply.contains("Broadcast completed: result=409"),"Protected receiver refuses clear during control");
            runOnMainSync(()->{fake.state.connected=false;fake.state.uncertainRun=true;});
            check(!ControlClient.canReconfigureAccess(),"Uncertain control blocks reconfiguration");
            runOnMainSync(()->{fake.state.uncertainRun=false;fake.state.rootChecking=true;});
            check(!ControlClient.canReconfigureAccess(),"Identity check blocks reconfiguration");
            runOnMainSync(()->{fake.state.rootChecking=false;fake.state.recovering=true;});
            check(!ControlClient.canReconfigureAccess(),"Recovery blocks reconfiguration");
            runOnMainSync(()->fake.state.recovering=false);
            check(ControlClient.canReconfigureAccess(),"Inactive client permits owner reconfiguration");
        }finally{runOnMainSync(()->{try{singleton.set(null,previous);}catch(Exception e){throw new RuntimeException(e);}});}
    }
}
