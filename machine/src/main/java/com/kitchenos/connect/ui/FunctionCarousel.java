package com.kitchenos.connect.ui;

import android.animation.*;
import android.content.Context;
import android.content.ClipData;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.kitchenos.common.Ui;
import java.util.*;

/** Selected functions: centered for up to four, otherwise an endless loop with side previews. */
public final class FunctionCarousel extends FrameLayout {
    public interface Tiles { View create(int index); }
    public interface Reordered { void save(List<Integer> order); }
    private Reordered reordered;
    private final List<Integer> order=new ArrayList<>(),savedOrder=new ArrayList<>();
    private final Map<View,Integer> tileIds=new HashMap<>();
    private final Map<View,Float> slideFrom=new HashMap<>();
    private ValueAnimator slide;private float slideProgress=1;
    private boolean reordering,dropAccepted,dragInside;private int lifted=-1;private float dragX;private long edgeAt;
    private final Paint placeholder=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final class FunctionDrag {
        final FunctionCarousel owner;final int id;
        FunctionDrag(FunctionCarousel owner,int id){this.owner=owner;this.id=id;}
    }
    private final int count,logicalCount,slop;private final boolean compact;private final Tiles tiles;
    private final Map<Integer,View> pool=new HashMap<>();private final Map<View,Integer> slots=new LinkedHashMap<>();
    private int position;private float stride,peek,offset,downX,downY,lastX;
    private boolean dragging,vertical,autoScroll=true;private ValueAnimator animation;
    private VelocityTracker tracker;private int pointerId;private long touchAt,lastMotionAt;
    private float velocity,carriedVelocity;
    private final int minimumFling,maximumFling;
    private static final float COAST_SECONDS=1.15f;
    private static final float AUTO_DP_PER_SECOND=-12f;
    private final CarouselMotion motion;
    public FunctionCarousel(Context context,int count,int initial,Tiles tiles){
        super(context);if(count<1)throw new IllegalArgumentException("At least one function is required");logicalCount=count;for(int i=0;i<count;i++)order.add(i);compact=count<=4;this.count=compact?count:count*((14+count-1)/count);this.tiles=index->tiles.create(index%logicalCount);position=compact?0:Math.floorMod(initial,count);slop=ViewConfiguration.get(context).getScaledTouchSlop();
        ViewConfiguration config=ViewConfiguration.get(context);minimumFling=config.getScaledMinimumFlingVelocity();maximumFling=Math.min(config.getScaledMaximumFlingVelocity(),dp(2400));
        motion=new CarouselMotion(this,AUTO_DP_PER_SECOND,()->!compact&&stride>0&&animation==null&&!dragging&&!reordering&&(autoScroll||velocity!=0),this::drift);
        motion.frameStep(this::frame);motion.continuousAfterTouch();
        setTag("functions.carousel");setClipChildren(true);setWillNotDraw(false);setOnDragListener((v,e)->functionDrag(e));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);rebuild();
    }
    private java.util.function.IntConsumer positionChanged;
    public void onPositionChanged(java.util.function.IntConsumer listener){positionChanged=listener;}
    public int position(){return order.get(position%logicalCount);}
    public boolean isReordering(){return reordering;}
    public List<Integer> previewOrder(){return new ArrayList<>(order);}
    public void onReordered(Reordered listener){reordered=listener;for(Map.Entry<View,Integer> tile:tileIds.entrySet())enableReorder(tile.getKey(),tile.getValue());}
    private void enableReorder(View tile,int id){tile.setOnLongClickListener(reordered==null?null:v->beginReorder(tile,id));}
    private int dp(int value){return Ui.dp(getContext(),value);}
    public void setAutoScrollEnabled(boolean enabled){autoScroll=enabled;motion.refresh();}
    private float baseSpeed(){return autoScroll&&!compact?AUTO_DP_PER_SECOND*getResources().getDisplayMetrics().density:0;}
    private void frame(float seconds){
        float decay=(float)Math.exp(-seconds/COAST_SECONDS);
        drift(baseSpeed()*seconds+velocity*COAST_SECONDS*(1-decay));
        velocity*=decay;if(Math.abs(velocity)<getResources().getDisplayMetrics().density)velocity=0;
    }
    private void drift(float pixels){offset+=pixels;if(stride>0&&Math.abs(offset)>=stride){int steps=-(int)(offset/stride);position=Math.floorMod(position+steps,count);rebuild(offset+steps*stride);if(positionChanged!=null)positionChanged.accept(position());}place();}
    private void rebuild(){rebuild(0);}
    private void rebuild(float carry){removeAllViews();slots.clear();offset=carry;
        // Pooled neighbors follow a full-width drag without exposing an empty edge.
        for(int slot=compact?0:-5;slot<=(compact?count-1:8);slot++){int slotIndex=Math.floorMod(position+slot,count),index=slotIndex/logicalCount*logicalCount+order.get(slotIndex%logicalCount);View tile=pool.get(index);if(tile==null){tile=tiles.create(index);pool.put(index,tile);tileIds.put(tile,index%logicalCount);enableReorder(tile,index%logicalCount);}slots.put(tile,slot);addView(tile,new LayoutParams(1,-2));}
        requestLayout();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);peek=dp(34);stride=Math.max(1,(width-2*peek)/4f);if(compact)peek=(width-count*stride)/2f;int tileWidth=Math.round(stride),imageSize=Math.max(1,tileWidth-dp(12)),height=imageSize+dp(76);
        for(View tile:slots.keySet()){if(tile instanceof LinearLayout){View image=((LinearLayout)tile).getChildAt(0);image.getLayoutParams().width=image.getLayoutParams().height=imageSize;}tile.measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));}setMeasuredDimension(width,resolveSize(height,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(View tile:slots.keySet())tile.layout(0,0,tile.getMeasuredWidth(),getHeight());place();}
    private void place(){
        for(Map.Entry<View,Integer> entry:slots.entrySet()){
            View tile=entry.getKey();float x=peek+entry.getValue()*stride+offset+slideFrom.getOrDefault(tile,0f)*(1-slideProgress);
            tile.setTranslationX(x);boolean visible=x<getWidth()&&x+tile.getWidth()>0;
            tile.setAlpha(reordering&&tileIds.get(tile)==lifted?0:1);
            tile.setVisibility(visible?VISIBLE:INVISIBLE);tile.setImportantForAccessibility(visible&&tile.getAlpha()>0?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        invalidate();
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(reordering)return true;
        if(compact)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=lastX=event.getX();downY=event.getY();dragging=vertical=false;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&!vertical){float dx=Math.abs(event.getX()-downX),dy=Math.abs(event.getY()-downY);if(dy>slop&&dy>dx){vertical=true;return false;}if(dx>slop&&dx>dy*1.25f&&animation==null){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}}return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(reordering)return true;
        if(compact)return super.onTouchEvent(event);
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){downX=lastX=event.getX();downY=event.getY();return true;}
        if(action==MotionEvent.ACTION_MOVE&&dragging){drift(event.getX()-lastX);lastX=event.getX();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(dragging&&action==MotionEvent.ACTION_UP){
                // Apply any final travel before preserving the finger's release velocity.
                drift(event.getX()-lastX);
                if(tracker!=null){tracker.computeCurrentVelocity(1000,maximumFling);float release=event.getEventTime()-lastMotionAt<=120?tracker.getXVelocity(pointerId):0;
                    if(Math.abs(release)>=minimumFling){float retained=carriedVelocity*(float)Math.exp(-(event.getEventTime()-touchAt)/1000f)*.75f;if(Math.signum(release)==Math.signum(retained))release+=retained;velocity=Math.max(-maximumFling,Math.min(maximumFling,release))-baseSpeed();}
                    android.util.Log.d("KitchenOSCarousel","Funktionsreihe freigegeben: "+release+" px/s; Momentum "+velocity+" px/s");
                }
            }else if(action==MotionEvent.ACTION_CANCEL)velocity=0;
            dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
        }return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(compact)return super.dispatchTouchEvent(event);
        int action=event.getActionMasked();motion.touch(action);
        if(action==MotionEvent.ACTION_DOWN){if(animation!=null)animation.cancel();carriedVelocity=velocity+baseSpeed();velocity=0;touchAt=lastMotionAt=event.getEventTime();pointerId=event.getPointerId(0);if(tracker!=null)tracker.recycle();tracker=VelocityTracker.obtain();}
        // Like native scroll views, compute release speed from the moving samples before UP.
        if(tracker!=null&&action!=MotionEvent.ACTION_UP)tracker.addMovement(event);
        if(action==MotionEvent.ACTION_MOVE)lastMotionAt=event.getEventTime();
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP){dragging=false;velocity=carriedVelocity=0;}
        boolean handled=super.dispatchTouchEvent(event);
        if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&tracker!=null){tracker.recycle();tracker=null;}
        return handled;
    }
    private boolean beginReorder(View tile,int id){
        if(reordered==null||reordering||stride<=0)return false;
        velocity=carriedVelocity=0;if(animation!=null)animation.cancel();motion.interact();
        View photo=tile instanceof LinearLayout&&((LinearLayout)tile).getChildCount()>0?((LinearLayout)tile).getChildAt(0):tile;
        getParent().requestDisallowInterceptTouchEvent(true);
        boolean started=tile.startDragAndDrop(ClipData.newPlainText("function",Integer.toString(id)),new LiftedPhoto(photo),new FunctionDrag(this,id),0);
        android.util.Log.d("KitchenOSCarousel","Function pickup started="+started+" id="+id);
        if(started)performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);else getParent().requestDisallowInterceptTouchEvent(false);
        return started;
    }
    private boolean functionDrag(DragEvent event){
        if(!(event.getLocalState() instanceof FunctionDrag)||((FunctionDrag)event.getLocalState()).owner!=this)return false;
        switch(event.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:
                if(reordered==null)return false;
                savedOrder.clear();savedOrder.addAll(order);lifted=((FunctionDrag)event.getLocalState()).id;
                android.util.Log.d("KitchenOSCarousel","Function drag accepted");reordering=true;dropAccepted=false;dragInside=true;edgeAt=0;dragX=downX;place();return true;
            case DragEvent.ACTION_DRAG_ENTERED:dragInside=true;edgeAt=0;return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                dragInside=true;dragX=event.getX();insertAt(dragX);removeCallbacks(edgeScroll);postOnAnimation(edgeScroll);return true;
            case DragEvent.ACTION_DRAG_EXITED:dragInside=false;edgeAt=0;removeCallbacks(edgeScroll);return true;
            case DragEvent.ACTION_DROP:dragX=event.getX();insertAt(dragX);dropAccepted=true;return true;
            case DragEvent.ACTION_DRAG_ENDED:finishReorder(dropAccepted&&event.getResult());return true;
            default:return true;
        }
    }
    private final Runnable edgeScroll=new Runnable(){public void run(){
        if(!reordering||!dragInside||!isShown()||!hasWindowFocus())return;
        long now=SystemClock.uptimeMillis();float dt=edgeAt==0?0:Math.min(.05f,(now-edgeAt)/1000f);edgeAt=now;
        float band=Math.min(getWidth()/4f,dp(88)),strength=dragX<band?(band-dragX)/band:dragX>getWidth()-band?-(dragX-(getWidth()-band))/band:0;
        if(!compact&&strength!=0){drift(Math.max(-1,Math.min(1,strength))*dp(440)*dt);insertAt(dragX);}
        if(strength!=0)postOnAnimation(this);else edgeAt=0;
    }};
    private void insertAt(float x){
        if(!reordering||stride<=0)return;
        int slot=(int)Math.floor((x-peek-offset)/stride),destination=compact?Math.max(0,Math.min(logicalCount-1,slot)):Math.floorMod(position+slot,logicalCount),current=order.indexOf(lifted);
        if(destination==current)return;
        Map<View,Float> before=new HashMap<>();for(View tile:slots.keySet())before.put(tile,tile.getX()-offset);
        order.remove(current);order.add(destination,lifted);rebuild(offset);slideFrom.clear();
        for(Map.Entry<View,Integer> entry:slots.entrySet())if(before.containsKey(entry.getKey())){
            float delta=before.get(entry.getKey())-(peek+entry.getValue()*stride);
            // A pooled copy wrapping off-screen must not fly through the row.
            if(Math.abs(delta)<stride*2)slideFrom.put(entry.getKey(),delta);
        }
        if(slide!=null)slide.cancel();slideProgress=0;slide=ValueAnimator.ofFloat(0,1);slide.setDuration(180);slide.setInterpolator(new android.view.animation.DecelerateInterpolator());
        slide.addUpdateListener(value->{slideProgress=(float)value.getAnimatedValue();place();});slide.start();place();
    }
    private void finishReorder(boolean accepted){
        if(!reordering)return;
        android.util.Log.d("KitchenOSCarousel","Function drag ended accepted="+accepted);
        removeCallbacks(edgeScroll);edgeAt=0;dragInside=false;
        if(!accepted){order.clear();order.addAll(savedOrder);}
        reordering=false;lifted=-1;dropAccepted=false;velocity=carriedVelocity=0;
        if(slide!=null)slide.cancel();slideProgress=1;slideFrom.clear();rebuild(offset);place();
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        motion.touch(MotionEvent.ACTION_CANCEL);motion.interact();
        if(accepted&&reordered!=null&&!order.equals(savedOrder))reordered.save(new ArrayList<>(order));
        if(positionChanged!=null)positionChanged.accept(position());motion.refresh();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!reordering)return;
        for(Map.Entry<View,Integer> entry:slots.entrySet())if(tileIds.get(entry.getKey())==lifted){
            float x=peek+(entry.getValue()+.5f)*stride+offset,r=Math.max(1,(stride-dp(12))/2);placeholder.setColor(Ui.SOFT);canvas.drawCircle(x,r,r,placeholder);
        }
    }
    private static final class LiftedPhoto extends View.DragShadowBuilder {
        private final View photo;private final int padding;private final Paint shadow=new Paint(Paint.ANTI_ALIAS_FLAG);
        LiftedPhoto(View photo){super(photo);this.photo=photo;padding=Ui.dp(photo.getContext(),12);}
        @Override public void onProvideShadowMetrics(Point size,Point touch){int side=Math.round(Math.min(photo.getWidth(),photo.getHeight())*1.06f)+padding*2;size.set(side,side);touch.set(side/2,side/2);}
        @Override public void onDrawShadow(Canvas canvas){
            float side=Math.min(photo.getWidth(),photo.getHeight()),radius=side*1.06f/2;
            shadow.setColor(0x66000000);canvas.drawCircle(padding+radius,padding+radius+padding/3f,radius,shadow);
            canvas.translate(padding,padding);canvas.scale(1.06f,1.06f);Path circle=new Path();circle.addCircle(side/2,side/2,side/2,Path.Direction.CW);canvas.clipPath(circle);photo.draw(canvas);
        }
    }
    public void move(int steps){if(compact||reordering)return;velocity=carriedVelocity=0;motion.interact();if(animation==null)settle(Math.max(-4,Math.min(4,steps)));}
    private void settle(int steps){if(animation!=null||stride<=0)return;ValueAnimator next=ValueAnimator.ofFloat(offset,-steps*stride);animation=next;next.setDuration(190);next.setInterpolator(new android.view.animation.DecelerateInterpolator());next.addUpdateListener(value->{offset=(float)value.getAnimatedValue();place();});next.addListener(new AnimatorListenerAdapter(){boolean cancelled;@Override public void onAnimationCancel(Animator value){cancelled=true;}@Override public void onAnimationEnd(Animator value){animation=null;if(!cancelled){position=Math.floorMod(position+steps,count);rebuild();if(positionChanged!=null)positionChanged.accept(position());}}});next.start();}
    /** Only explicit knob navigation reveals a neighbor; native focus restoration must never advance the loop. */
    public void reveal(View tile){if(compact||reordering)return;velocity=carriedVelocity=0;if(animation!=null)return;Integer slot=slots.get(tile);if(slot!=null&&(slot<0||slot>3))move(slot<0?-1:1);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();motion.refresh();}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(!hasFocus)cancelCoast();motion.refresh();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)cancelCoast();if(motion!=null)motion.refresh();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(!isShown())cancelCoast();if(motion!=null)motion.refresh();}
    private void cancelCoast(){if(reordering)finishReorder(false);velocity=carriedVelocity=0;dragging=vertical=false;if(tracker!=null){tracker.recycle();tracker=null;}}
    @Override protected void onDetachedFromWindow(){motion.stop();cancelCoast();if(animation!=null)animation.cancel();if(slide!=null)slide.cancel();super.onDetachedFromWindow();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.HorizontalScrollView");info.setScrollable(!compact);if(compact)return;info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){if(!compact&&(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){move(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1);return true;}return super.performAccessibilityAction(action,args);}
}
