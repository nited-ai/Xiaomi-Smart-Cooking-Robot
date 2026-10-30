package com.kitchenos.connect.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import java.util.ArrayList;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.kitchen.KitViews;

/** A screen-local launcher. Dragging never invokes its click action. */
public final class FloatingAssistant extends FrameLayout {
    public static final int PLATE_DP=190,HEIGHT_DP=190;
    private final SharedPreferences position;
    private final int slop;
    private float fractionX,fractionY,downX,downY,startX,startY;
    private boolean dragging;
    private int pointer=-1;
    private View protectedFooter,protectedControls,choiceSpace;
    private final ArrayList<View> protectedChoices=new ArrayList<>();
    private boolean placedByUser;
    private int initialTop=-1;

    public FloatingAssistant(Activity context,Runnable open){
        super(context);
        position=context.getSharedPreferences("assistant-position",Context.MODE_PRIVATE);
        fractionX=position.getFloat("x",1f);fractionY=position.getFloat("y",1f);
        placedByUser=position.contains("x");
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        setTag("assistant");setContentDescription("Frag Kalomatik. Zum Verschieben ziehen.");
        setClickable(true);setFocusable(true);setClipChildren(false);
        android.graphics.drawable.StateListDrawable focus=new android.graphics.drawable.StateListDrawable();
        focus.addState(new int[]{android.R.attr.state_selected},KitViews.shape(context,0,32,Ui.ACCENT));
        focus.addState(new int[]{android.R.attr.state_focused},KitViews.shape(context,0,32,Ui.ACCENT));
        focus.addState(new int[]{},new android.graphics.drawable.ColorDrawable(0));setBackground(focus);
        AssistantAvatar avatar=new AssistantAvatar(context);
        addView(avatar,new FrameLayout.LayoutParams(-1,-1,Gravity.TOP));
        setOnClickListener(v->open.run());
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(!dragging)place();});
    }

    /** Keep fixed cooking actions reachable even when the plate is moved downward. */
    public void protectFooter(View footer){protectedFooter=footer;}
    public void protectControls(View controls){protectedControls=controls;}
    /** Keep the real choice tiles/readable introduction clear without reserving the whole grid. */
    public void protectChoices(View root,View space){choiceSpace=space;protectedChoices.clear();collectChoices(root);}
    private void collectChoices(View view){if(view==null)return;Object tag=view.getTag();if(tag instanceof String&&(((String)tag).startsWith("mode.")||((String)tag).startsWith("function.slot.")||tag.equals("functions.copy"))){protectedChoices.add(view);return;}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collectChoices(((ViewGroup)view).getChildAt(i));}
    private boolean coversChoice(float x,float y){Rect plate=new Rect((int)x,(int)y,(int)x+getWidth(),(int)y+getHeight()),bounds=new Rect();int[] origin=new int[2];((View)getParent()).getLocationOnScreen(origin);for(View choice:protectedChoices)if(choice.isShown()&&choice.getGlobalVisibleRect(bounds)){bounds.offset(-origin[0],-origin[1]);if(Rect.intersects(plate,bounds))return true;}return false;}
    /** The first weighing visit starts clear of the scale's confirm/ingredient area. */
    public void initialTop(int pixels){initialTop=pixels;}
    private float inset(){return Ui.dp(getContext(),8);}
    private float maxX(){return Math.max(inset(),((View)getParent()).getWidth()-getWidth()-inset());}
    private float baseMaxY(){return Math.max(inset(),((View)getParent()).getHeight()-inset()-getHeight());}
    private float maxY(){
        View parent=(View)getParent();float bottom=parent.getHeight()-inset();
        if(protectedFooter!=null&&protectedFooter.isShown()&&protectedFooter.getHeight()>0){
            int[] host=new int[2],footer=new int[2];parent.getLocationOnScreen(host);protectedFooter.getLocationOnScreen(footer);
            bottom=Math.min(bottom,footer[1]-host[1]-inset());
        }
        return Math.max(inset(),bottom-getHeight());
    }
    private float clamp(float value,float max){return Math.max(inset(),Math.min(max,value));}
    private void safePosition(float x,float y){x=clamp(x,maxX());y=clamp(y,maxY());if(protectedControls!=null&&protectedControls.isShown()){Rect block=new Rect();int[] origin=new int[2];((View)getParent()).getLocationOnScreen(origin);if(protectedControls.getGlobalVisibleRect(block)){block.offset(-origin[0],-origin[1]);Rect plate=new Rect((int)x,(int)y,(int)x+getWidth(),(int)y+getHeight());if(Rect.intersects(block,plate)){float up=block.top-getHeight()-inset(),down=block.bottom+inset();if(up>=inset()&&(down>maxY()||Math.abs(y-up)<=Math.abs(down-y)))y=up;else if(down<=maxY())y=down;}}}if(getVisibility()==VISIBLE&&choiceSpace!=null&&coversChoice(x,y)){Rect space=new Rect();int[] origin=new int[2];((View)getParent()).getLocationOnScreen(origin);if(choiceSpace.getGlobalVisibleRect(space)){space.offset(-origin[0],-origin[1]);float right=clamp(space.right-getWidth(),maxX()),top=clamp(Math.max(space.top,Math.min(y,space.bottom-getHeight())),maxY());if(!coversChoice(right,top)){x=right;y=top;}}}setX(x);setY(y);}
    private void place(){if(!(getParent() instanceof View))return;safePosition(inset()+Math.max(0,Math.min(1,fractionX))*(maxX()-inset()),!placedByUser&&initialTop>=0?clamp(initialTop,maxY()):clamp(inset()+Math.max(0,Math.min(1,fractionY))*(baseMaxY()-inset()),maxY()));}
    /** Open-chat dragging shares the same persisted screen position as the closed plate. */
    public void rememberAt(float x,float y){if(!(getParent() instanceof View))return;safePosition(x,y);fractionX=maxX()>inset()?(getX()-inset())/(maxX()-inset()):0;fractionY=baseMaxY()>inset()?(getY()-inset())/(baseMaxY()-inset()):0;save();}
    private void save(){placedByUser=true;position.edit().putFloat("x",fractionX).putFloat("y",fractionY).apply();}
    public boolean contains(float screenX,float screenY){Rect bounds=new Rect();return isShown()&&getGlobalVisibleRect(bounds)&&bounds.contains((int)screenX,(int)screenY);}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(changed==this&&visibility==VISIBLE)post(this::place);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);post(this::place);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnScrollChangedListener(scrollPosition);((View)getParent()).addOnLayoutChangeListener(parentLayout);if(protectedFooter!=null)protectedFooter.addOnLayoutChangeListener(parentLayout);post(this::place);}
    @Override protected void onDetachedFromWindow(){if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnScrollChangedListener(scrollPosition);if(getParent() instanceof View)((View)getParent()).removeOnLayoutChangeListener(parentLayout);if(protectedFooter!=null)protectedFooter.removeOnLayoutChangeListener(parentLayout);super.onDetachedFromWindow();}
    private final android.view.ViewTreeObserver.OnScrollChangedListener scrollPosition=()->{if(!dragging&&isShown())place();};
    private final OnLayoutChangeListener parentLayout=(v,l,t,r,b,ol,ot,or,ob)->{if(!dragging)place();};
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return true;}
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                pointer=event.getPointerId(0);downX=event.getRawX();downY=event.getRawY();startX=getX();startY=getY();dragging=false;setPressed(true);getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(pointer<0||event.getPointerId(0)!=pointer)return true;
                float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                if(!dragging&&Math.hypot(dx,dy)>slop){dragging=true;setPressed(false);}
                if(dragging){safePosition(startX+dx,startY+dy);}return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);getParent().requestDisallowInterceptTouchEvent(false);
                if(dragging){fractionX=maxX()>inset()?(getX()-inset())/(maxX()-inset()):0;fractionY=baseMaxY()>inset()?(getY()-inset())/(baseMaxY()-inset()):0;save();}
                else if(pointer>=0)performClick();
                pointer=-1;dragging=false;return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                pointer=-1;dragging=false;setPressed(false);place();getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return true;
        }
    }
}
