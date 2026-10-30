package com.kitchenos.connect.kitchen;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import org.json.JSONObject;
import com.kitchenos.common.PhotoPixels;
/** Appliance facade for the shared, metadata-stripping photo codec. */
public final class PhotoAttachment {
    public static final int MAX_BYTES=PhotoPixels.MAX_BYTES;
    public static File importPhoto(Context context,Uri uri)throws Exception{return PhotoPixels.importPhoto(context,uri);}
    public static File restore(Context context,String path)throws IOException{return PhotoPixels.restore(context,path);}
    public static JSONObject payload(File file)throws Exception{return PhotoPixels.payload(file);}
    private PhotoAttachment(){}
}
