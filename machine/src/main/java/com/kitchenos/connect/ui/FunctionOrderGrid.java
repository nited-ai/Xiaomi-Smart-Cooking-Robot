package com.kitchenos.connect.ui;
import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;
import com.kitchenos.common.Ui;import java.util.*;

/** Live insertion preview: a round photo follows the finger while neighboring tiles make room. */
public final class FunctionOrderGrid extends android.widget.FrameLayout {
 public interface Tiles{View create(String id,int position);}
 public interface Dropped{void move(String id,int position);}
 private final List<String> saved,preview;private final Map<String,View> tiles=new LinkedHashMap<>();private final Dropped dropped;
 private String dragging;private boolean accepted;private int cellWidth,cellHeight;private final Paint gap=new Paint(3);private final int columns;
 private ScrollView dragScroll;private float dragX,dragWindowY;private int scrollStep;
 private final Runnable autoScroll=new Runnable(){public void run(){if(dragging==null||dragScroll==null||scrollStep==0)return;int before=dragScroll.getScrollY();dragScroll.scrollBy(0,scrollStep);if(before!=dragScroll.getScrollY()){int[] location=new int[2];getLocationOnScreen(location);insertAt(dragX,dragWindowY-location[1]);}postDelayed(this,24);}};
 public FunctionOrderGrid(Context context,List<String> ids,Tiles factory,Dropped dropped){
  this(context,ids,4,factory,dropped);
 }
 public FunctionOrderGrid(Context context,List<String> ids,int columns,Tiles factory,Dropped dropped){
  super(context);this.columns=Math.max(1,columns);saved=new ArrayList<>(ids);preview=new ArrayList<>(ids);this.dropped=dropped;setTag("functions.order-grid");setWillNotDraw(false);setClipChildren(false);
  for(int i=0;i<ids.size();i++){String id=ids.get(i);View tile=factory.create(id,i);tiles.put(id,tile);addView(tile);tile.setOnLongClickListener(v->begin(id));}
  setOnDragListener((v,event)->drag(event));
 }
 private int dp(int n){return Ui.dp(getContext(),n);}
 public List<String> previewOrder(){return new ArrayList<>(preview);}
 public boolean isPreviewing(){return dragging!=null;}
 public static final class RoundPhotoShadow extends View.DragShadowBuilder {
  private final View photo;
  public RoundPhotoShadow(View photo){super(photo);this.photo=photo;}
  @Override public void onProvideShadowMetrics(Point size,Point touch){int side=Math.max(1,Math.min(photo.getWidth(),photo.getHeight()));size.set(side,side);touch.set(side/2,side/2);}
  @Override public void onDrawShadow(Canvas canvas){int save=canvas.save();Path circle=new Path();circle.addCircle(photo.getWidth()/2f,photo.getHeight()/2f,Math.min(photo.getWidth(),photo.getHeight())/2f,Path.Direction.CW);canvas.clipPath(circle);photo.draw(canvas);canvas.restoreToCount(save);}
 }
 private boolean begin(String id){View tile=tiles.get(id);View photo=((ViewGroup)tile).getChildAt(0);getParent().requestDisallowInterceptTouchEvent(true);boolean started=tile.startDragAndDrop(ClipData.newPlainText("function",id),new RoundPhotoShadow(photo),id,0);if(!started)getParent().requestDisallowInterceptTouchEvent(false);return started;}
 private boolean drag(DragEvent event){
  Object state=event.getLocalState();if(!(state instanceof String)||!tiles.containsKey(state))return false;
  switch(event.getAction()){
   case DragEvent.ACTION_DRAG_STARTED:dragging=(String)state;accepted=false;preview.clear();preview.addAll(saved);tiles.get(dragging).setAlpha(0);invalidate();return true;
   case DragEvent.ACTION_DRAG_LOCATION:insertAt(event.getX(),event.getY());scrollAtEdge(event.getX(),event.getY());return true;
   case DragEvent.ACTION_DRAG_EXITED:stopScroll();return true;
   case DragEvent.ACTION_DROP:stopScroll();insertAt(event.getX(),event.getY());accepted=true;dropped.move(dragging,preview.indexOf(dragging));return true;
   case DragEvent.ACTION_DRAG_ENDED:stopScroll();if(!accepted){preview.clear();preview.addAll(saved);}else{saved.clear();saved.addAll(preview);}for(View tile:tiles.values())tile.setAlpha(1);dragging=null;getParent().requestDisallowInterceptTouchEvent(false);place(true);invalidate();return true;
   default:return true;
  }
 }
 private void scrollAtEdge(float x,float y){stopScroll();for(ViewParent parent=getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView){dragScroll=(ScrollView)parent;break;}if(dragScroll==null)return;int[] location=new int[2];getLocationOnScreen(location);dragX=x;dragWindowY=location[1]+y;Rect visible=new Rect();dragScroll.getGlobalVisibleRect(visible);scrollStep=dragWindowY<visible.top+dp(48)?-dp(10):dragWindowY>visible.bottom-dp(48)?dp(10):0;if(scrollStep!=0)postDelayed(autoScroll,24);}
 private void stopScroll(){removeCallbacks(autoScroll);scrollStep=0;dragScroll=null;}
 private void insertAt(float x,float y){if(cellWidth<=0||dragging==null)return;int column=Math.max(0,Math.min(columns-1,(int)(x/cellWidth))),row=Math.max(0,(int)(y/cellHeight));int index=Math.min(preview.size()-1,row*columns+column);if(index==preview.indexOf(dragging))return;preview.remove(dragging);preview.add(index,dragging);place(true);invalidate();}
 @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);cellWidth=width/columns;cellHeight=dp(158);for(View tile:tiles.values()){tile.measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));cellHeight=Math.max(cellHeight,tile.getMeasuredHeight()+dp(columns==5?14:0));}for(View tile:tiles.values())tile.measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(cellHeight,MeasureSpec.EXACTLY));setMeasuredDimension(width,resolveSize(((preview.size()+columns-1)/columns)*cellHeight,heightSpec));}
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(View tile:tiles.values())tile.layout(0,0,cellWidth,cellHeight);place(false);}
 private void place(boolean animate){for(int i=0;i<preview.size();i++){View tile=tiles.get(preview.get(i));float x=i%columns*cellWidth,y=i/columns*cellHeight;tile.animate().cancel();if(animate&&isAttachedToWindow())tile.animate().translationX(x).translationY(y).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();else{tile.setTranslationX(x);tile.setTranslationY(y);}}}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(dragging==null)return;int i=preview.indexOf(dragging);float cx=(i%columns+.5f)*cellWidth,cy=i/columns*cellHeight+dp(53);gap.setStyle(Paint.Style.FILL);gap.setColor(Ui.SOFT);canvas.drawCircle(cx,cy,dp(53),gap);gap.setStyle(Paint.Style.STROKE);gap.setStrokeWidth(dp(2));gap.setColor(Ui.ACCENT);canvas.drawCircle(cx,cy,dp(53),gap);}
 @Override protected void onDetachedFromWindow(){stopScroll();for(View tile:tiles.values())tile.animate().cancel();super.onDetachedFromWindow();}
}

