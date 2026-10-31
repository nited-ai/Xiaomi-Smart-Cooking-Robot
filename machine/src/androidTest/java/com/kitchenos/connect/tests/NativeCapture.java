package com.kitchenos.connect.tests;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Native SurfaceFlinger PNG capture avoids emulator graphics-buffer imports in takeScreenshot. */
final class NativeCapture {
    static void save(Instrumentation test,File target)throws IOException{
        try(ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand("screencap -p"));OutputStream output=new FileOutputStream(target)){
            byte[] bytes=new byte[16384];int n,total=0;
            while((n=input.read(bytes))!=-1){total+=n;if(total>8388608)throw new IOException("Screenshot exceeded its bound");output.write(bytes,0,n);}
            if(total<8)throw new IOException("Native screenshot is empty");
        }
    }
    static Bitmap read(Instrumentation test)throws IOException{
        File temporary=File.createTempFile("screen-",".png",test.getTargetContext().getCacheDir());
        try{save(test,temporary);Bitmap bitmap=BitmapFactory.decodeFile(temporary.getAbsolutePath());if(bitmap==null)throw new IOException("Screenshot could not decode");return bitmap;}finally{temporary.delete();}
    }
    private NativeCapture(){}
}
