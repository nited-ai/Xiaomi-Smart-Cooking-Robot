package com.kitchenos.common;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.Base64;
import java.io.*;
import org.json.*;

/** Bounded image import: re-encode pixels only, stripping EXIF/location metadata. */
public final class PhotoPixels {
    public static final int MAX_BYTES=600000;
    public static File importPhoto(Context context,Uri uri)throws Exception{
        ByteArrayOutputStream source=new ByteArrayOutputStream();
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Das Foto konnte nicht geöffnet werden");byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){if(source.size()+n>20*1024*1024)throw new IOException("Das Foto ist zu groß");source.write(buffer,0,n);}
        }
        byte[] bytes=source.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<1||options.outHeight<1||(long)options.outWidth*options.outHeight>100000000)throw new IOException("Dieses Bildformat wird nicht unterstützt");
        int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>1600)sample*=2;options.inJustDecodeBounds=false;options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.RGB_565;
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new IOException("Das Foto konnte nicht gelesen werden");
        try{android.media.ExifInterface exif=new android.media.ExifInterface(new ByteArrayInputStream(bytes));int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix matrix=new Matrix();switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;case 8:matrix.setRotate(-90);break;}if(!matrix.isIdentity()){Bitmap upright=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(upright!=bitmap){bitmap.recycle();bitmap=upright;}}}catch(IOException ignored){}
        File directory=new File(context.getCacheDir(),"pantry-photos");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Das Foto konnte nicht gespeichert werden");
        File file=File.createTempFile("photo-",".jpg",directory);
        try{ByteArrayOutputStream encoded=new ByteArrayOutputStream();int quality=88;do{encoded.reset();bitmap.compress(Bitmap.CompressFormat.JPEG,quality,encoded);quality-=12;}while(encoded.size()>MAX_BYTES&&quality>=40);if(encoded.size()>MAX_BYTES)throw new IOException("Bitte ein kleineres Foto auswählen");try(OutputStream out=new FileOutputStream(file)){encoded.writeTo(out);}return file;}catch(Exception e){file.delete();throw e;}finally{bitmap.recycle();}
    }
    public static File restore(Context context,String path)throws IOException{
        File file=new File(path),directory=new File(context.getCacheDir(),"pantry-photos");if(!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())||!file.isFile()||file.length()>MAX_BYTES)throw new IOException("Foto nicht mehr verfügbar");return file;
    }
    public static JSONObject payload(File file)throws Exception{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("Foto zu groß");out.write(buffer,0,n);}return new JSONObject().put("mimeType","image/jpeg").put("data",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));}}
    private PhotoPixels(){}
}
