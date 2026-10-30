package com.kitchenos.connect.ui;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import com.kitchenos.common.Ui;

/** Touch and knob share the value; selection grows within a stable hit area. */
public final class ParameterDial extends View implements RotaryController.Adjustable {
    interface NumberFace { void draw(Canvas canvas); boolean touch(MotionEvent event); void changed(); void dispose(); default void confirmed(){} }
    private NumberFace numberFace;
    private boolean liveMode,liveFresh;
    void numberFace(NumberFace face){numberFace=face;setMinimumHeight(0);setBackground(null);invalidate();}
    void beginNumberEdit(){if(directBegin!=null)directBegin.run();else editing(true);}
    void commitNumberEdit(){if(directCommit!=null)directCommit.run();}
    public void liveState(boolean running,boolean fresh){if(liveMode!=running||liveFresh!=fresh){liveMode=running;liveFresh=fresh;if(numberFace!=null)numberFace.changed();invalidate();}}
    boolean liveMode(){return liveMode;} boolean liveFresh(){return liveFresh;}
    int currentReading(){return reading;}
    private static final long TEMPERATURE_HOLD_MS=2400,TEMPERATURE_FADE_MS=360;
    private long temperatureTargetUntil,temperatureFadeUntil;
    private final Runnable temperaturePreviewExpired=new Runnable(){@Override public void run(){faceChanged();invalidate();if(android.os.SystemClock.elapsedRealtime()<temperatureFadeUntil)postOnAnimation(this);}};
    /** Presentation only: each real target edit restarts the hold and crossfade. */
    void previewTemperatureTarget(){
        if(!label.equals("Temperatur"))return;
        temperatureTargetUntil=android.os.SystemClock.elapsedRealtime()+TEMPERATURE_HOLD_MS;
        temperatureFadeUntil=temperatureTargetUntil+TEMPERATURE_FADE_MS;
        removeCallbacks(temperaturePreviewExpired);postDelayed(temperaturePreviewExpired,TEMPERATURE_HOLD_MS);
        faceChanged();invalidate();
    }
    float temperatureLiveOpacity(){
        if(!label.equals("Temperatur")||temperatureFadeUntil==0)return 1f;
        long now=android.os.SystemClock.elapsedRealtime();
        if(now<=temperatureTargetUntil)return 0f;
        return Math.max(0f,Math.min(1f,(now-temperatureTargetUntil)/(float)TEMPERATURE_FADE_MS));
    }
    boolean measuredTemperature(){return label.equals("Temperatur")&&numberFace!=null&&temperatureLiveOpacity()>=1f;}
    String measuredTemperatureText(){return reading<0?"—":reading<35?"---":Integer.toString(reading);}
    private void faceChanged(){if(numberFace!=null)numberFace.changed();}
    public interface Changed { void changed(int value); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String label;
    private final int minimum,maximum;private int step;private boolean halfSpeeds;
    public void precision(int tick,boolean halves){if(step==tick&&halfSpeeds==halves)return;step=tick;halfSpeeds=halves;faceChanged();invalidate();}
    private int choiceCeiling=Integer.MAX_VALUE,profileMin,profileMax;
    private int value,turnDirection,countdown=-1,reading=-1,choiceMin,choiceMax;private long changeRevision;private boolean seeding;
    private float turnGain=1;
    private long lastTurnAt=-1;
    private boolean minuteKnob;
    private final SlowTimeKnob slowTimeKnob=new SlowTimeKnob();
    void minuteKnob(boolean enabled){if(minuteKnob==enabled)return;minuteKnob=enabled;slowTimeKnob.reset();lastTurnAt=-1;turnGain=1;}
    private boolean editing,known=true,dragged,touchPicker,homeLayout,circularLayout;
    public void touchPicker(boolean value){touchPicker=value;}
    /** Adjust the circular face directly by dragging around its rim. */
    public void circularDrag(){touchPicker=false;circularLayout=true;presentation=0;invalidate();}
    private Changed listener;private String[] labels;
    private int presentation;private Runnable directBegin,directCommit;private int gestureValue;
    public void presentation(int style){presentation=Math.max(0,Math.min(2,style));invalidate();}
    public int presentation(){return presentation;}
    public void onDirectEdit(Runnable begin,Runnable commit){directBegin=begin;directCommit=commit;}

    private View accessory;
    public void accessory(View view){accessory=view;invalidate();}
    public void labels(String... values){labels=values.clone();invalidate();}
    private float previousAngle,remainder,downX,downY,visualFraction,selection;
    private android.animation.ValueAnimator transition,focusTransition;
    public ParameterDial(Activity a,String label,int minimum,int maximum,int step,int initial){
        super(a);this.label=label;this.minimum=minimum;this.maximum=maximum;this.step=step;
        profileMin=choiceMin=minimum;profileMax=choiceMax=maximum;value=Math.max(minimum,Math.min(maximum,initial));visualFraction=fraction();
        setMinimumHeight(Ui.dp(a,190));setContentDescription(label);setFocusable(true);setDefaultFocusHighlightEnabled(false);
    }
    public int minimum(){return choiceMin;} public int maximum(){return choiceMax;} public int tick(){return step;}
    public boolean accepts(int next){return next>=choiceMin&&next<=choiceMax&&(!label.equals("Temperatur")||next==0||next>=35&&next%step==0)&&(!label.equals("Stufe")||(halfSpeeds||next<=30?next%5==0:next%10==0));}
    public void reading(int next){if(reading!=next){reading=next;faceChanged();invalidate();}}
    public void ceiling(int maximumChoice){int next=Math.max(minimum,Math.min(maximum,maximumChoice));if(choiceCeiling==next)return;choiceCeiling=next;choiceMin=profileMin;choiceMax=Math.max(choiceMin,Math.min(profileMax,choiceCeiling));invalidate();}
    public void choices(int low,int high){profileMin=Math.max(minimum,low);profileMax=Math.max(profileMin,Math.min(maximum,high));choiceMin=profileMin;choiceMax=Math.max(choiceMin,Math.min(profileMax,choiceCeiling));if(value<profileMin||value>profileMax)seedValue(Math.max(profileMin,Math.min(profileMax,value)));}
    public int value(){return value;} public boolean known(){return known;} public boolean isEditing(){return editing;}
    /** Display-only timer state; never changes the selected duration or emits an edit. */
    public void countdown(int seconds){int next=Math.max(-1,seconds);if(countdown!=next){countdown=next;faceChanged();invalidate();}}
    public int displayedSeconds(){return countdown>=0?countdown:value;}
    public void homeLayout(boolean value){homeLayout=value;invalidate();}
    public float sizeFraction(){return 1f+(homeLayout?.04f:.12f)*selection;}
    public void unknown(){known=false;invalidate();}
    public void restore(int next){value=Math.max(minimum,Math.min(maximum,next));clearTemperaturePreview();visualFraction=fraction();known=true;faceChanged();invalidate();}
    public void onChanged(Changed listener){this.listener=listener;}
    public boolean isSeeding(){return seeding;}
    public long changeRevision(){return changeRevision;}
    public void noteUserChoice(){changeRevision++;}
    private void clearTemperaturePreview(){if(!label.equals("Temperatur"))return;temperatureTargetUntil=temperatureFadeUntil=0;removeCallbacks(temperaturePreviewExpired);}
    public void seedValue(int next){boolean before=seeding;seeding=true;try{clearTemperaturePreview();value(next);}finally{seeding=before;}}
    public void value(int next){known=true;if(label.equals("Temperatur")&&next>0&&next<35)next=next>value?35:0;int chosen=Math.max(choiceMin,Math.min(choiceMax,next));if(chosen==value){faceChanged();invalidate();return;}if(!seeding){changeRevision++;if(label.equals("Temperatur"))previewTemperatureTarget();}value=chosen;animateFraction();if(listener!=null)listener.changed(value);faceChanged();}
    @Override public void turn(int delta){
        if(!known){value(minimum);return;}
        if(value>choiceMax){if(delta<0)value(choiceMax);return;}
        for(int i=0;i<Math.min(240,Math.abs(delta));i++){
            if(label.equals("Temperatur")&&step==5){int next=delta>0?(value==0?35:((value/5)+1)*5):(value<=35?0:((value-1)/5)*5);value(next);}
            else if(label.equals("Stufe")&&!halfSpeeds&&maximum>30&&(value>=30&&delta>0||value>30&&delta<0))value(delta>0?((value/10)+1)*10:((value-1)/10)*10);
            else value(value+(delta>0?step:-step));
        }
    }
    @Override public void turnTimed(int delta,long eventTime){
        if(!label.equals("Dauer")){turn(delta);return;}
        if(minuteKnob){int seconds=slowTimeKnob.seconds(delta,eventTime);if(seconds!=0)value((int)Math.round(value/60.0)*60+seconds);return;}
        int direction=Integer.signum(delta);if(direction==0)return;
        long gap=eventTime-lastTurnAt;
        if(lastTurnAt<0||direction!=turnDirection||gap<=0||gap>=450)turnGain=1;
        else{float pace=350f/Math.max(35,gap),target=Math.max(1,Math.min(40,pace*pace));turnGain+=.35f*(target-turnGain);}
        lastTurnAt=eventTime;turnDirection=direction;
        if(!known){value(minimum);return;}
        value(value+direction*Math.round(turnGain)*Math.min(240,Math.abs(delta)));
    }
    @Override public void setSelected(boolean next){super.setSelected(next);animateSelection(next||editing);}
    @Override public void editing(boolean next){if(editing==next)return;if(next&&label.equals("Dauer")&&countdown>=0){int remaining=countdown;countdown=-1;seedValue(remaining);}editing=next;lastTurnAt=-1;turnGain=1;slowTimeKnob.reset();animateSelection(isSelected()||editing);faceChanged();if(!next&&numberFace!=null)numberFace.confirmed();invalidate();sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
    private String number(){if(measuredTemperature())return reading<0?"Messwert fehlt":measuredTemperatureText();if(labels!=null&&value>=minimum&&value-minimum<labels.length)return labels[value-minimum];if(!known)return "—";if(label.equals("Dauer"))return value>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",value/3600,value/60%60,value%60):String.format(java.util.Locale.ROOT,"%02d:%02d",value/60,value%60);if(label.equals("Temperatur"))return value==0?"Aus":String.valueOf(value);if(label.equals("Stufe"))return value==0?"Aus":String.format(java.util.Locale.GERMAN,"%.1f",value/10.0);return String.valueOf(value);}
    private float fraction(){if(label.equals("Temperatur"))return value<=35?0:(float)(value-35)/Math.max(1,maximum-35);if(label.equals("Stufe"))return (float)value/100;return maximum==minimum?0:(float)(value-minimum)/(maximum-minimum);}
    private boolean motion(){return isAttachedToWindow()&&android.animation.ValueAnimator.areAnimatorsEnabled();}
    private void animateSelection(boolean selected){
        if(focusTransition!=null)focusTransition.cancel();float end=selected?1:0;
        // Home's NumberFace owns its finger tracking and settling animation; it
        // never reads the circular dial's selection/fraction animation values.
        if(numberFace!=null||!motion()){selection=end;invalidate();return;}
        focusTransition=android.animation.ValueAnimator.ofFloat(selection,end);focusTransition.setDuration(180);focusTransition.setInterpolator(new android.view.animation.DecelerateInterpolator(2));focusTransition.addUpdateListener(a->{selection=(float)a.getAnimatedValue();invalidate();});focusTransition.start();
    }
    private void animateFraction(){if(transition!=null)transition.cancel();float end=fraction();if(numberFace!=null||!motion()){visualFraction=end;invalidate();return;}transition=android.animation.ValueAnimator.ofFloat(visualFraction,end);transition.setDuration(90);transition.setInterpolator(new android.view.animation.DecelerateInterpolator());transition.addUpdateListener(a->{visualFraction=(float)a.getAnimatedValue();invalidate();});transition.start();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(temperaturePreviewExpired);if(transition!=null)transition.cancel();if(focusTransition!=null)focusTransition.cancel();if(numberFace!=null)numberFace.dispose();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(numberFace!=null){numberFace.draw(canvas);return;}if(touchPicker&&!homeLayout&&!circularLayout){drawFields(canvas);return;}float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),r=(Math.min(w,h)/2-(homeLayout?10:14)*d)/(w>=h?1.12f:1f)*sizeFraction(),cx=w/2,cy=h/2;
        if(accessory!=null){float size=Math.max(.7f,Math.min(1.4f,r*.44f/(70*d)));accessory.setScaleX(size);accessory.setScaleY(size);accessory.setTranslationY(cy+r*.70f-(h-69*d));}
        boolean focused=isSelected()||editing;paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(focused?Ui.SOFT:Ui.CARD);
        // Soft offset shadow, drawn with simple rings to avoid a software layer per frame.
        for(int i=5;i>=1;i--){paint.setColor(Color.argb(3,0,0,0));canvas.drawCircle(cx,cy+3*d,r+i*d,paint);}paint.setColor(focused?Ui.SOFT:Ui.CARD);canvas.drawCircle(cx,cy,r,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth((focused?2:1)*d);paint.setColor(focused?Ui.INK:Ui.LINE);canvas.drawCircle(cx,cy,r,paint);
        if(known){
            float outer=r+9*d;RectF ring=new RectF(cx-outer,cy-outer,cx+outer,cy+outer);float portion=countdown>=0?(float)countdown/Math.max(1,value):visualFraction;portion=Math.max(0,Math.min(1,portion));
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth((focused?4:3)*d);
            if(label.equals("Stufe")){
                // Ten whole-level segments. Fractional levels and quick edits fade smoothly.
                for(int i=0;i<10;i++){float amount=Math.max(0,Math.min(1,portion*10-i));paint.setColor(Ui.LINE);paint.setAlpha(150);canvas.drawArc(ring,-90+i*36+4,28,false,paint);paint.setColor(Ui.ACCENT);paint.setAlpha(Math.round(255*amount));canvas.drawArc(ring,-90+i*36+4,28,false,paint);}paint.setAlpha(255);
            }else{
                if(portion>0){canvas.save();canvas.translate(0,3*d);for(int i=4;i>=1;i--){paint.setColor(0x08000000);paint.setStrokeWidth((4+i*2)*d);canvas.drawArc(ring,-90,portion*360,false,paint);}canvas.restore();}
                paint.setColor(Ui.ACCENT);paint.setStrokeWidth((focused?4:3)*d);if(portion>0)canvas.drawArc(ring,-90,portion*360,false,paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.ACCENT);canvas.drawCircle(cx,cy-outer,3*d,paint);
            double angle=Math.toRadians(-90+portion*360);float ex=cx+(float)Math.cos(angle)*outer,ey=cy+(float)Math.sin(angle)*outer;
            paint.setColor(Ui.CARD);canvas.drawCircle(ex,ey,6*d,paint);paint.setColor(label.equals("Dauer")?Ui.ACCENT:indicatorColor(label.equals("Temperatur")?value/180f:value/100f));canvas.drawCircle(ex,ey,4.5f*d,paint);
        }
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(isEnabled()?Ui.INK:Ui.MUTED);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        float scale=sizeFraction(),baseline=cy-(paint.ascent()+paint.descent())/2;
        if(label.equals("Dauer")&&known){
            int seconds=displayedSeconds();float offset=r*(seconds>=6000?.45f:.37f);paint.setTextSize(Math.min(66*d*scale,r*(seconds>=6000?.47f:.52f)));baseline=cy-(paint.ascent()+paint.descent())/2;
            canvas.drawText(String.format(java.util.Locale.ROOT,"%02d",seconds/60),cx-offset,baseline,paint);canvas.drawText(String.format(java.util.Locale.ROOT,"%02d",seconds%60),cx+offset,baseline,paint);
            canvas.drawText(":",cx,baseline-2*d,paint);paint.setTextSize(r*.18f);paint.setColor(Ui.MUTED);canvas.drawText("Min",cx-offset,cy+r*.40f,paint);canvas.drawText("Sek",cx+offset,cy+r*.40f,paint);
            InterfaceIcon clock=new InterfaceIcon("timer",Ui.MUTED);int size=Math.round(r*.34f),top=Math.round(cy+r*.70f-size/2f);clock.setBounds(Math.round(cx-size/2f),top,Math.round(cx+size/2f),top+size);clock.draw(canvas);
        }else{
            paint.setTextSize(Math.min(66*d*scale,r*.52f));if(labels!=null)while(paint.measureText(number())>r*1.65f&&paint.getTextSize()>18*d)paint.setTextSize(paint.getTextSize()-d);baseline=cy-(paint.ascent()+paint.descent())/2;canvas.drawText(number(),cx,baseline,paint);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(r*.30f);paint.setColor(Ui.MUTED);if(!label.equals("Stufe"))canvas.drawText(label.equals("Temperatur")?"°C":label,cx,cy+r*.70f-(paint.ascent()+paint.descent())/2,paint);
        }

    }
    private void drawFields(Canvas canvas){
        float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),cx=w/2,base=h*(presentation==2?.35f:.43f);
        boolean active=isSelected()||editing;paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(active?Ui.SOFT:Ui.CARD);
        RectF area=new RectF(4*d,6*d,w-4*d,h-6*d);canvas.drawRoundRect(area,22*d,22*d,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth((active?2:1)*d);paint.setColor(active?Ui.ACCENT:Ui.LINE);canvas.drawRoundRect(area,22*d,22*d,paint);paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(18*d);paint.setColor(Ui.MUTED);canvas.drawText(label.equals("Dauer")?"Zeit":label,cx,32*d,paint);
        int shown=label.equals("Dauer")?displayedSeconds():value;
        if(presentation==1){
            paint.setTextSize(21*d);paint.setColor(Ui.MUTED);
            if(label.equals("Dauer")){for(int offset:new int[]{-1,1}){int minute=Math.max(0,Math.min(120,shown/60+offset)),second=Math.max(0,Math.min(59,shown%60+offset));canvas.drawText(String.format(java.util.Locale.ROOT,"%02d",minute),cx-w*.23f,base+offset*56*d,paint);canvas.drawText(String.format(java.util.Locale.ROOT,"%02d",second),cx+w*.23f,base+offset*56*d,paint);}}
            else{int saved=value;for(int offset:new int[]{-1,1}){int candidate=neighborValue(offset);String text=label.equals("Temperatur")?(candidate==0?"Aus":String.valueOf(candidate)):label.equals("Stufe")?(candidate==0?"Aus":String.format(java.util.Locale.GERMAN,"%.1f",candidate/10.0)):String.valueOf(candidate);canvas.drawText(text,cx,base+offset*56*d,paint);}value=saved;}
        }
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setColor(isEnabled()?Ui.INK:Ui.MUTED);paint.setTextSize(Math.min((presentation==1?50:58)*d,w*.26f));
        if(label.equals("Dauer")){
            canvas.drawText(String.format(java.util.Locale.ROOT,"%02d",shown/60),cx-w*.23f,base,paint);canvas.drawText(":",cx,base-2*d,paint);canvas.drawText(String.format(java.util.Locale.ROOT,"%02d",shown%60),cx+w*.23f,base,paint);
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(17*d);paint.setColor(Ui.MUTED);float units=presentation==1?base+82*d:base+31*d;canvas.drawText("Min",cx-w*.23f,units,paint);canvas.drawText("Sek",cx+w*.23f,units,paint);
        }else{
            String text=number();paint.setTextSize(Math.min((presentation==1?50:62)*d,w*.30f));while(paint.measureText(text)>w-24*d&&paint.getTextSize()>20*d)paint.setTextSize(paint.getTextSize()-d);canvas.drawText(text,cx,base,paint);
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(22*d);paint.setColor(Ui.MUTED);if(label.equals("Temperatur"))canvas.drawText("°C",cx,presentation==1?base+82*d:base+31*d,paint);
        }
        if(presentation==2){
            float y=h-91*d;paint.setColor(Ui.LINE);canvas.drawRoundRect(new RectF(20*d,y-27*d,w-20*d,y+24*d),12*d,12*d,paint);paint.setColor(Ui.CARD);canvas.drawRoundRect(new RectF(21*d,y-26*d,w-21*d,y+23*d),11*d,11*d,paint);paint.setColor(Ui.ACCENT);paint.setTextSize(31*d);canvas.drawText("−",w*.28f,y+9*d,paint);canvas.drawText("+",w*.72f,y+9*d,paint);
        }else if(presentation==0){
            float y=h-88*d;paint.setColor(Ui.LINE);canvas.drawRoundRect(new RectF(24*d,y,w-24*d,y+4*d),2*d,2*d,paint);paint.setColor(label.equals("Dauer")?Ui.ACCENT:indicatorColor(label.equals("Temperatur")?value/180f:value/100f));float fraction=countdown>=0?(float)countdown/Math.max(1,value):fraction();canvas.drawRoundRect(new RectF(24*d,y,24*d+(w-48*d)*Math.max(0,Math.min(1,fraction)),y+4*d),2*d,2*d,paint);
        }
        if(accessory!=null){accessory.setScaleX(presentation==2?.72f:1);accessory.setScaleY(presentation==2?.72f:1);accessory.setTranslationY((presentation==2?35:25)*d);}
        if(label.equals("Dauer")){InterfaceIcon icon=new InterfaceIcon("timer",Ui.MUTED);int size=Math.round(33*d),top=Math.round(h-(presentation==1?28:44)*d-size/2f);icon.setBounds(Math.round(cx-size/2),top,Math.round(cx+size/2),top+size);icon.draw(canvas);}
    }
    private int neighborValue(int direction){
        if(label.equals("Temperatur"))return Math.max(choiceMin,Math.min(choiceMax,direction>0?(value==0?35:((value/5)+1)*5):(value<=35?0:((value-1)/5)*5)));
        int tick=label.equals("Stufe")&&(value>=30&&direction>0||value>30&&direction<0)?10:step;
        return Math.max(choiceMin,Math.min(choiceMax,value+direction*tick));
    }
    private boolean fieldTouch(MotionEvent event){
        if(!isEnabled())return false;float d=getResources().getDisplayMetrics().density;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=event.getX();downY=previousAngle=event.getY();gestureValue=value;dragged=false;remainder=0;getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(presentation!=1)return true;
                if(!dragged&&Math.abs(event.getY()-downY)<ViewConfiguration.get(getContext()).getScaledTouchSlop())return true;
                if(!dragged){dragged=true;if(directBegin!=null)directBegin.run();}
                remainder+=previousAngle-event.getY();previousAngle=event.getY();int ticks=(int)(remainder/(32*d));if(ticks!=0){if(label.equals("Dauer"))value(value+ticks*(downX<getWidth()/2f?60:1));else turn(ticks);remainder-=ticks*32*d;}return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if(presentation==2&&Math.abs(event.getY()-downY)<12*d&&event.getY()>getHeight()-120*d&&event.getY()<getHeight()-62*d){if(directBegin!=null)directBegin.run();turn(event.getX()<getWidth()/2f?-1:1);if(directCommit!=null)directCommit.run();return true;}
                if(dragged){if(directCommit!=null)directCommit.run();}else performClick();return true;
            case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);if(dragged)value(gestureValue);return true;
            default:return true;
        }
    }
    public static int indicatorColor(float fraction){
        float v=Math.max(0,Math.min(1,fraction));int[] colors={0xff54bf78,0xfff6c553,0xfff18639,0xffe2544d};float segment=v*3;int index=Math.min(2,(int)segment);float mix=segment-index;int from=colors[index],to=colors[index+1];return Color.rgb(Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*mix),Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*mix),Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*mix));
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(numberFace!=null)return numberFace.touch(e);
        if(touchPicker)return fieldTouch(e);
        if(!isEnabled())return false;float x=e.getX()-getWidth()/2f,y=e.getY()-getHeight()/2f,angle=(float)Math.atan2(y,x);
        if(e.getAction()==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);previousAngle=angle;remainder=0;dragged=false;downX=e.getX();downY=e.getY();return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE){
            if(Math.hypot(e.getX()-downX,e.getY()-downY)<ViewConfiguration.get(getContext()).getScaledTouchSlop())return true;
            if(!dragged){dragged=true;if(directBegin!=null)directBegin.run();else if(!editing)performClick();gestureValue=value;}
            float delta=angle-previousAngle;if(delta>Math.PI)delta-=2*Math.PI;if(delta< -Math.PI)delta+=2*Math.PI;previousAngle=angle;
            if(Math.hypot(x,y)<Math.min(getWidth(),getHeight())*.23f){remainder=0;return true;}
            // A single step needs a deliberate arc. Center crossings never jump values.
            remainder+=delta;float perTick=label.equals("Portionen")?.42f:label.equals("Dauer")?.10f:.22f;int ticks=(int)(remainder/perTick);
            if(ticks!=0){turn(ticks);remainder-=ticks*perTick;}return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);if(!dragged)performClick();else if(value!=gestureValue&&directCommit!=null)directCommit.run();return true;}
        if(e.getAction()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);if(dragged&&directBegin!=null)value(gestureValue);return true;}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.SeekBar");info.setText(label+": "+number()+(editing?". Bearbeitung aktiv.":""));info.setRangeInfo(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(0,minimum,maximum,value));info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){if(action==4096){turn(1);return true;}if(action==8192){turn(-1);return true;}return super.performAccessibilityAction(action,args);}
}
