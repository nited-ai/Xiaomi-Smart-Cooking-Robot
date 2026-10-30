package com.kitchenos.connect.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** Original schematic fallbacks for the source edition. Never a product photograph. */
final class PublicArtwork {
    private PublicArtwork() {}
    static Bitmap vessel(ApplianceVessel.Part part) {
        Bitmap bitmap=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888);
        ApplianceVessel drawing=new ApplianceVessel(part);
        drawing.setBounds(0,0,600,400);
        drawing.draw(new Canvas(bitmap));
        return bitmap;
    }
    static Bitmap cup() {
        Bitmap bitmap=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(126,151,143));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);
        canvas.drawRoundRect(40,25,120,135,12,12,paint);
        canvas.drawLine(55,55,80,55,paint);
        canvas.drawLine(55,80,80,80,paint);
        canvas.drawLine(55,105,80,105,paint);
        return bitmap;
    }
    static Bitmap layer(String name) {
        return name.equals("cup")?cup():vessel(name.equals("lid")?ApplianceVessel.Part.LID:ApplianceVessel.Part.BOWL);
    }
    static Bitmap accessory(OemApplianceImages.Part part) {
        switch(part) {
            case LID:return vessel(ApplianceVessel.Part.LID);
            case STEAMER:return vessel(ApplianceVessel.Part.STEAMER);
            case BASKET:return vessel(ApplianceVessel.Part.BASKET);
            case BLADE:return vessel(ApplianceVessel.Part.KNIFE);
            case STIRRER:return vessel(ApplianceVessel.Part.STIRRER);
            case SPATULA:return vessel(ApplianceVessel.Part.SPATULA);
            case MEASURING_CUP:return cup();
            case MEASURING_SPOONS:return null;
            default:return vessel(ApplianceVessel.Part.BOWL);
        }
    }
}
