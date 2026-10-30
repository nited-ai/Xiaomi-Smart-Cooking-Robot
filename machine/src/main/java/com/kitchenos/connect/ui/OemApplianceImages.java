package com.kitchenos.connect.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;

/** Legacy accessory API. The public edition uses original schematic fallbacks. */
public final class OemApplianceImages {
    public enum Part {
        DEVICE("device.png", "Gerät"), BOWL("bowl.jpg", "Mixtopf"),
        LID("lid.png", "Deckel"), STEAMER("steamer.png", "Dampfaufsatz"),
        BASKET("simmering-basket.png", "Gareinsatz"), BLADE("blade.png", "Messer"),
        STIRRER("stirrer.png", "Rühraufsatz"), SPATULA("spatula.png", "Schaber"),
        MEASURING_CUP("measuring-cup.png", "Messbecher"),
        MEASURING_SPOONS("measuring-spoons.png", "Messlöffel-Set");
        public final String file, label;
        Part(String file, String label) { this.file=file; this.label=label; }
    }
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(12*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value) { return value.getAllocationByteCount(); }
    };
    private OemApplianceImages() {}

    /** Every image is a real C3 reference; unsupported tools deliberately return no match. */
    public static Part forTool(String id) {
        if(id==null) return null;
        switch(id) {
            case "device": case "xiaomi-c3": case "machine": return Part.DEVICE;
            case "bowl": case "mixing-bowl": return Part.BOWL;
            case "lid": case "bowl-lid": return Part.LID;
            case "steamer": return Part.STEAMER;
            case "steam-basket": case "basket": return Part.BASKET;
            case "mixing-blade": return Part.BLADE;
            case "stirring-attachment": return Part.STIRRER;
            case "spatula": return Part.SPATULA;
            case "measuring-cup": return Part.MEASURING_CUP;
            case "measuring-spoons": return Part.MEASURING_SPOONS;
            default: return null;
        }
    }
    /** Kept for migrating existing accessory views without using their schematic drawings. */
    public static Part fromVessel(ApplianceVessel.Part part) {
        if(part==null) return Part.BOWL;
        switch(part) {
            case LID:return Part.LID; case STEAMER:return Part.STEAMER;
            case BASKET:return Part.BASKET; case KNIFE:return Part.BLADE;
            case STIRRER:return Part.STIRRER; case SPATULA:return Part.SPATULA;
            case CLOSED_BOWL:return Part.DEVICE; default:return Part.BOWL;
        }
    }
    public static ImageView view(Context context, Part part) {
        ImageView image=new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        int pad=Math.round(3*context.getResources().getDisplayMetrics().density);
        image.setPadding(pad,pad,pad,pad);
        apply(image,part);
        return image;
    }
    public static ImageView view(Context context,ApplianceVessel.Part part) {
        return view(context,fromVessel(part));
    }
    public static void apply(ImageView image,Part part) {
        if(part==null)throw new IllegalArgumentException("Unknown OEM accessory");
        Bitmap bitmap=CACHE.get(part.file);
        if(bitmap==null) {
            try(InputStream stream=image.getContext().getAssets().open("oem/"+part.file)) {
                BitmapFactory.Options options=new BitmapFactory.Options();
                // Largest source is 1664 px. Decode to <=832 px for the appliance's accessory tiles.
                options.inSampleSize=part==Part.MEASURING_SPOONS?2:1;
                bitmap=BitmapFactory.decodeStream(stream,null,options);
                if(bitmap!=null) CACHE.put(part.file,bitmap);
            } catch(java.io.IOException ignored) { /* OEM artwork is excluded from the public source edition. */ }
        }
        boolean schematic=bitmap==null;
        if(schematic){bitmap=PublicArtwork.accessory(part);if(bitmap==null){image.setImageDrawable(null);image.setContentDescription(part.label+" · Abbildung nicht enthalten");return;}}
        image.setImageBitmap(bitmap);
        if(com.kitchenos.common.Ui.dark&&(part==Part.BLADE||part==Part.STIRRER||part==Part.LID||part==Part.STEAMER||part==Part.MEASURING_CUP)){int ink=com.kitchenos.common.Ui.INK;image.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{0,0,0,0,android.graphics.Color.red(ink),0,0,0,0,android.graphics.Color.green(ink),0,0,0,0,android.graphics.Color.blue(ink),-.3333f,-.3333f,-.3333f,0,255}));}else image.clearColorFilter();
        image.setImageAlpha(255);
        image.setAlpha(1f);
        image.setContentDescription(part.label+(schematic?" · schematische Darstellung":""));
    }
}
