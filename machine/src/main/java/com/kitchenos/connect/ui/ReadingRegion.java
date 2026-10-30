package com.kitchenos.connect.ui;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import com.kitchenos.common.Ui;

/** Knob reader for an existing long instruction. Does not activate UI actions or hardware. */
public final class ReadingRegion implements RotaryController.Adjustable {
    private final View instruction;
    private final ScrollView scroll;
    private final TextView hint;
    private final Drawable originalForeground;
    private final CharSequence originalDescription;
    private boolean selected,reading;
    private int targetY;
    ReadingRegion(View instruction,ScrollView scroll){
        if(instruction==null||scroll==null)throw new IllegalArgumentException("Anleitung und Scrollbereich fehlen");
        this.instruction=instruction;this.scroll=scroll;originalForeground=instruction.getForeground();originalDescription=instruction.getContentDescription();
        hint=new TextView(instruction.getContext());hint.setTextSize(16);hint.setPadding(0,Ui.dp(instruction.getContext(),8),0,Ui.dp(instruction.getContext(),12));update();
    }
    /** Host should put this below the instruction for persistent visible reading feedback. */
    public TextView hintView(){return hint;}
    public boolean isReading(){return reading;}
    void selected(boolean value){selected=value;if(!value)reading=false;update();}
    @Override public void editing(boolean value){reading=value;targetY=scroll.getScrollY();update();}
    private Rect bounds(){Rect rect=new Rect();instruction.getDrawingRect(rect);scroll.offsetDescendantRectToMyCoords(instruction,rect);return rect;}
    void revealStart(){if(instruction.isAttachedToWindow())scroll.smoothScrollTo(0,Math.max(0,bounds().top-Ui.dp(instruction.getContext(),16)));}
    @Override public void turn(int delta){
        if(!reading||!instruction.isShown()||scroll.getHeight()<=0)return;
        Rect rect=bounds();int padding=Ui.dp(instruction.getContext(),16),first=Math.max(0,rect.top-padding),last=Math.max(first,rect.bottom-scroll.getHeight()+padding);
        int stride=Math.max(Ui.dp(instruction.getContext(),96),(int)(scroll.getHeight()*.45f));
        targetY=Math.max(first,Math.min(last,targetY+delta*stride));scroll.smoothScrollTo(0,targetY);
    }
    private void update(){
        hint.setText(reading?"Lesemodus · drehen zum Lesen · drücken für die Auswahl":selected?"Drücken zum Lesen · drehen für die nächste Auswahl":"Anleitung mit dem Drehknopf lesen");
        hint.setTextColor(reading?Ui.ACCENT:Ui.MUTED);
        if(selected||reading){GradientDrawable outline=new GradientDrawable();outline.setColor(0x00000000);outline.setCornerRadius(Ui.dp(instruction.getContext(),10));outline.setStroke(Ui.dp(instruction.getContext(),reading?3:1),reading?Ui.ACCENT:Ui.LINE);instruction.setForeground(outline);}
        else instruction.setForeground(originalForeground);
        instruction.setContentDescription(selected||reading?"Anleitung lesen. "+hint.getText():originalDescription);
        instruction.invalidate();
    }
}
