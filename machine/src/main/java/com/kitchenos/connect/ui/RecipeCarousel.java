package com.kitchenos.connect.ui;

import android.animation.*;
import android.content.Context;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import com.kitchenos.common.Ui;

/** Three live cards regardless of catalogue size. Horizontal gestures leave vertical page scrolling intact. */
public final class RecipeCarousel extends FrameLayout {
    public interface Cards {
        View create(int index);
        void selected(int index,View active);
        default void inactive(View card){}
    }
    private final int count,slop;
    private final Cards cards;
    private final float cardFraction;
    private int position;
    private float downX,downY,downOffset,offset;
    private boolean dragging,vertical;
    private ValueAnimator animation;
    private final CarouselMotion motion;
    private final java.util.LinkedHashMap<View,Integer> pooled=new java.util.LinkedHashMap<>();

    public RecipeCarousel(Context context,int count,int initial,Cards cards){
        this(context,count,initial,.70f,cards);
    }
    public RecipeCarousel(Context context,int count,int initial,float cardFraction,Cards cards){
        super(context);this.count=count;this.cards=cards;this.cardFraction=Math.max(.35f,Math.min(1,cardFraction));position=Math.max(0,Math.min(initial,count-1));
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        motion=new CarouselMotion(this,-18,()->count>1&&getWidth()>0&&animation==null&&!dragging,this::drift);
        motion.enabled(count>1);
        setTag("home.carousel");rebuild();
    }
    public int position(){return position;}
    private int dp(int value){return Ui.dp(getContext(),value);}
    private float stride(){return count>1?getWidth()*cardFraction+dp(12):getWidth();}
    public void setAutoScrollEnabled(boolean enabled){motion.enabled(enabled&&count>1);}
    private void drift(float pixels){
        offset+=pixels;float step=stride();
        // Rebase while the replaced outer card is fully outside the viewport.
        if(Math.abs(offset)>=step/2){int direction=offset<0?1:-1;position=Math.floorMod(position+direction,count);rebuild(offset+direction*step);}
        place();
    }
    private void rebuild(){rebuild(0);}
    private void rebuild(float carry){
        removeAllViews();offset=carry;
        java.util.Set<View> used=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int first=count>1?-1:0,last=count>1?1:0;
        View active=null;
        for(int slot=first;slot<=last;slot++){
            int index=Math.floorMod(position+slot,count);View card=null;for(java.util.Map.Entry<View,Integer> entry:pooled.entrySet())if(entry.getValue()==index&&!used.contains(entry.getKey())){card=entry.getKey();break;}if(card==null){card=cards.create(index);pooled.put(card,index);}used.add(card);
            card.setTag(null);card.setOnClickListener(null);card.setSelected(false);card.setEnabled(slot==0);cards.inactive(card);card.setTag(com.kitchenos.connect.R.id.rotary_target,null);
            card.setImportantForAccessibility(slot==0?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            card.setFocusable(false);card.setClickable(false);
            // Reuse the photographed card's texture while alpha/scale/translation change.
            card.setLayerType(View.LAYER_TYPE_HARDWARE,null);
            addView(card,new FrameLayout.LayoutParams(-1,-1));
            if(slot==0)active=card;
        }
        for(java.util.Iterator<View> old=pooled.keySet().iterator();pooled.size()>6&&old.hasNext();)if(!used.contains(old.next()))old.remove();
        requestLayout();cards.selected(position,active);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width,height);
        int cardWidth=count>1?Math.round(width*cardFraction):width;
        for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cardWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        for(int i=0;i<getChildCount();i++){
            View card=getChildAt(i);int left=(getWidth()-card.getMeasuredWidth())/2;
            card.layout(left,0,left+card.getMeasuredWidth(),getHeight());
        }
        place();
    }
    private void place(){
        for(int i=0;i<getChildCount();i++){
            View card=getChildAt(i);float x=(count>1?i-1:0)*stride()+offset;
            float distance=Math.min(1,Math.abs(x)/Math.max(1,stride()));
            card.setTranslationX(x);card.setScaleX(1-.035f*distance);card.setScaleY(1-.08f*distance);card.setAlpha(1-.25f*distance);
        }
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(count<2)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();downOffset=offset;dragging=false;vertical=false;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&!vertical){
            float dx=Math.abs(event.getX()-downX),dy=Math.abs(event.getY()-downY);
            if(dy>slop&&dy>dx){vertical=true;return false;}
            if(dx>slop&&dx>dy*1.25f){
                if(animation!=null)return false;
                dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;
            }
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(count<2)return super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();downOffset=offset;return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&dragging){offset=Math.max(-stride(),Math.min(stride(),downOffset+event.getX()-downX));place();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            if(dragging){boolean advance=event.getActionMasked()!=MotionEvent.ACTION_CANCEL&&Math.abs(offset)>Math.min(dp(64),getWidth()*.16f);settle(advance?(offset<0?1:-1):0);}
            dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
        }
        return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        motion.touch(event.getActionMasked());
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&animation!=null)animation.cancel();
        return super.dispatchTouchEvent(event);
    }
    public void move(int direction){motion.interact();if(count>1&&animation==null)settle(direction>0?1:-1);}
    private void settle(int direction){
        if(animation!=null)return;
        ValueAnimator next=ValueAnimator.ofFloat(offset,-direction*stride());animation=next;
        next.setDuration(230);next.setInterpolator(new android.view.animation.DecelerateInterpolator());
        next.addUpdateListener(value->{offset=(float)value.getAnimatedValue();place();});
        next.addListener(new AnimatorListenerAdapter(){
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator value){cancelled=true;}
            @Override public void onAnimationEnd(Animator value){animation=null;if(!cancelled){position=Math.floorMod(position+direction,count);rebuild();}}
        });next.start();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();motion.refresh();}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);motion.refresh();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(motion!=null)motion.refresh();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(motion!=null)motion.refresh();}
    @Override protected void onDetachedFromWindow(){motion.stop();if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.HorizontalScrollView");info.setScrollable(count>1);
        if(count>1){info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
    }
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){
        if(count>1&&(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)){move(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1);return true;}
        return super.performAccessibilityAction(action,args);
    }
}
