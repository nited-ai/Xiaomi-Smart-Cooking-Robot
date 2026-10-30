package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.*;
import android.widget.*;
import com.kitchenos.common.Ui;

/** Persistent local pages with passive previews at function boundaries. Never starts cooking. */
public final class FunctionWorkspacePager extends FrameLayout {
    public interface Neighbours { boolean available(); Transition preview(int direction); }
    public static final class Transition {
        final View view;final Runnable commit;
        public Transition(View view,Runnable commit){this.view=view;this.commit=commit;}
    }
    private Neighbours neighbours;private Transition crossing;
    private Runnable pageChanged=()->{};
    public void onPageChanged(Runnable action){pageChanged=action;}
    public void neighbours(Neighbours value){neighbours=value;updateArrows();}
    private View[] pages;private final View preparation,controls;private View middle;private boolean controlsAvailable=true;private boolean directControls;
    private final ImageButton previous,next;
    private int selected,target;private float downX,downY,offset;private boolean dragging,vertical,settling;
    private ValueAnimator animator;
    private final java.util.List<View> overlays=new java.util.ArrayList<>();
    public FunctionWorkspacePager(Context context,View preparation,View controls){
        super(context);setTag("home.function.pager");setClipChildren(true);setClipToPadding(true);
        this.preparation=preparation;this.controls=controls;
        pages=new View[]{preparation,controls};for(View p:pages){p.setBackgroundColor(Ui.BG);addView(p,new LayoutParams(-1,-1));}
        previous=arrow("back","Zur Vorbereitung",0);next=arrow("next","Zur Bedienung",1);show(1,false);
    }
    private ImageButton arrow(String icon,String label,int page){ImageButton b=new ImageButton(getContext());b.setImageDrawable(new InterfaceIcon(icon,Ui.MUTED));b.setBackgroundColor(ColorTransparent());b.setContentDescription(label);b.setTag("home.function.page."+page);b.setPadding(dp(16),dp(16),dp(16),dp(16));LayoutParams lp=new LayoutParams(dp(64),dp(64),(page==0?Gravity.LEFT:Gravity.RIGHT)|Gravity.CENTER_VERTICAL);addView(b,lp);b.setOnClickListener(v->{if(!settling&&!dragging&&begin(page==0?-1:1))settle(true);});return b;}
    private int ColorTransparent(){return android.graphics.Color.TRANSPARENT;}
    private int dp(int n){return Ui.dp(getContext(),n);}
    public int page(){return selected;}
    public void directControls(boolean value){directControls=value;if(value)show(1,false);updateArrows();}
    private int firstPage(){return directControls?1:0;}
    private int lastPage(){return pages.length-1-(controlsAvailable?0:1);}
    public void middlePage(View view){
        if(middle==view)return;
        if(animator!=null){ValueAnimator old=animator;animator=null;old.cancel();}finish();
        if(middle!=null)removeView(middle);middle=view;
        pages=view==null?new View[]{preparation,controls}:new View[]{preparation,view,controls};
        if(view!=null){view.setBackgroundColor(Ui.BG);addView(view,new LayoutParams(-1,-1));}
        controlsAvailable=true;selected=target=0;previous.bringToFront();next.bringToFront();for(View v:overlays)v.bringToFront();finish();
    }
    public void controlsAvailable(boolean available){if(controlsAvailable==available)return;if(available){controlsAvailable=true;updateArrows();return;}if(animator!=null){ValueAnimator old=animator;animator=null;old.cancel();}controlsAvailable=false;selected=Math.min(selected,lastPage());target=selected;finish();}
    public float dragOffset(){return offset;}
    public void overlay(View view,LayoutParams params){overlays.add(view);addView(view,params);}
    public void show(int page,boolean animate){if(page<firstPage()||page>lastPage())return;if(animator!=null){ValueAnimator old=animator;animator=null;old.cancel();finish();}else if(dragging||crossing!=null)finish();if(page==selected){finish();return;}target=page;prepare();if(animate)settle(true);else{selected=page;finish();}}
    private boolean begin(int direction){
        target=selected+direction;
        if(target<firstPage()||target>lastPage()){if(neighbours==null||!neighbours.available())return false;crossing=neighbours.preview(direction);if(crossing==null)return false;crossing.view.setBackgroundColor(Ui.BG);addView(crossing.view,new LayoutParams(-1,-1));}
        prepare();return true;
    }
    private View incoming(){return crossing==null?pages[target]:crossing.view;}
    private void prepare(){pages[selected].setVisibility(VISIBLE);incoming().setVisibility(VISIBLE);for(View p:pages)p.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);incoming().setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);incoming().bringToFront();previous.bringToFront();next.bringToFront();for(View v:overlays)v.bringToFront();position(offset);}
    private void position(float x){offset=x;int width=getWidth();float sign=target>selected?-1:1;pages[selected].setTranslationX(x);incoming().setTranslationX(x-sign*width);}
    public void updateArrows(){boolean adjacent=neighbours!=null&&neighbours.available();previous.setEnabled(selected>firstPage()||adjacent);next.setEnabled(selected<lastPage()||adjacent);previous.setAlpha(previous.isEnabled()?1:.2f);next.setAlpha(next.isEnabled()?1:.2f);previous.setContentDescription(selected==firstPage()?"Vorige Funktion":middle!=null&&selected==2?"Zur Programmauswahl":"Zur Vorbereitung");next.setContentDescription(selected>=lastPage()?"Nächste Funktion":middle!=null&&selected==0?"Zur Programmauswahl":"Zur Bedienung");}
    private void finish(){settling=dragging=false;offset=0;if(crossing!=null){removeView(crossing.view);crossing=null;}for(int i=0;i<pages.length;i++){pages[i].setTranslationX(0);pages[i].setVisibility(i==selected?VISIBLE:INVISIBLE);pages[i].setImportantForAccessibility(i==selected?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}updateArrows();getParentDisallow(false);pageChanged.run();}
    private void settle(boolean commit){settling=true;float end=commit?(target>selected?-getWidth():getWidth()):0;animator=ValueAnimator.ofFloat(offset,end);animator.setDuration(ValueAnimator.areAnimatorsEnabled()?180:0);animator.setInterpolator(new android.view.animation.DecelerateInterpolator());animator.addUpdateListener(a->position((float)a.getAnimatedValue()));animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(animator!=a)return;Transition nextFunction=commit?crossing:null;if(commit&&crossing==null)selected=target;animator=null;finish();if(nextFunction!=null&&neighbours!=null&&neighbours.available())nextFunction.commit.run();}});animator.start();}
    private void getParentDisallow(boolean value){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(value);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN&&settling){for(View v:overlays)if(v.getVisibility()==VISIBLE&&event.getX()>=v.getLeft()&&event.getX()<=v.getRight()&&event.getY()>=v.getTop()&&event.getY()<=v.getBottom()){ValueAnimator old=animator;animator=null;if(old!=null)old.cancel();finish();break;}}
        if(settling)return true;
        if(action==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();dragging=vertical=false;getParentDisallow(true);}
        if(action==MotionEvent.ACTION_MOVE){float dx=event.getX()-downX,dy=event.getY()-downY;
            if(!dragging&&!vertical){int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();if(Math.abs(dy)>slop&&Math.abs(dy)>Math.abs(dx)*1.25f)vertical=true;else if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.25f){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();if(begin(dx<0?1:-1)){dragging=true;getParentDisallow(true);}else vertical=true;}}
            if(dragging){float signed=target>selected?Math.min(0,dx):Math.max(0,dx);position(Math.max(-getWidth(),Math.min(getWidth(),signed)));return true;}
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(dragging){settle(action==MotionEvent.ACTION_UP&&Math.abs(offset)>getWidth()*.22f);return true;}getParentDisallow(false);}
        // Keep receiving the gesture even over blank space or a read-only notice.
        // Those children do not consume DOWN, but must still allow function swipes.
        return super.dispatchTouchEvent(event)||action==MotionEvent.ACTION_DOWN;
    }
    @Override protected void onDetachedFromWindow(){if(animator!=null){ValueAnimator old=animator;animator=null;old.cancel();}finish();super.onDetachedFromWindow();}
}
