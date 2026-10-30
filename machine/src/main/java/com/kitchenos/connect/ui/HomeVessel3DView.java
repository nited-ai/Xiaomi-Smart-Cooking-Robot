package com.kitchenos.connect.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import com.kitchenos.common.Ui;
import com.kitchenos.connect.control.ControlState;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;

/** Native projection of an editable 3D mesh. No WebView, raster illusion or device commands.
 * Canvas uses Android's hardware display list; only observed motor activity schedules rotation.
 * Animation speed is illustrative, never an RPM measurement. */
public final class HomeVessel3DView extends View {
    private static volatile List<Polygon> model;
    private static final java.util.concurrent.Executor LOAD=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"vessel-mesh");t.setDaemon(true);return t;});
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
    private final ArrayList<Projected> polygons=new ArrayList<>();
    private final RectF cupBounds=new RectF();
    private HomeVesselState state=HomeVesselState.from(new ControlState(),0);
    private boolean reverse,stirrer,running,fresh,foreground,visibleMode;private int temperature;
    private long sampleAt=-1,infoAt=-1,lastFrame,cueAt=-10000;private float angle,lidLift=.25f,cupLift=1;
    private ValueAnimator lidMotion,cupMotion;
    private static final class Polygon {
        final String part;final float[][] xyz;final float[] color;final float nx,ny,nz;
        Polygon(String part,float[][] xyz,float[] color){this.part=part;this.xyz=xyz;this.color=color;
            float ax=xyz[1][0]-xyz[0][0],ay=xyz[1][1]-xyz[0][1],az=xyz[1][2]-xyz[0][2];
            float bx=xyz[2][0]-xyz[0][0],by=xyz[2][1]-xyz[0][1],bz=xyz[2][2]-xyz[0][2];
            float x=ay*bz-az*by,y=az*bx-ax*bz,z=ax*by-ay*bx,length=(float)Math.sqrt(x*x+y*y+z*z);
            nx=length==0?0:x/length;ny=length==0?1:y/length;nz=length==0?0:z/length;}
    }
    private static final class Projected {final Polygon source;final float[] x,y;float depth,light;Projected(Polygon p){source=p;x=new float[p.xyz.length];y=new float[p.xyz.length];}}
    public HomeVessel3DView(Context context){super(context);setTag("home.bowl");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        // The mesh's display list still replays >1,300 paths on every window frame.
        // Keep its texture between actual vessel changes, so scrolling siblings do
        // not pay that cost. Existing state/animation invalidations refresh it.
        setLayerType(LAYER_TYPE_HARDWARE,null);describe();
        if(model!=null)install();else LOAD.execute(()->{try{load(context.getApplicationContext());post(()->{install();invalidate();});}catch(Exception error){android.util.Log.e("KitchenOSVessel","3D mesh unavailable",error);}});
    }
    private static synchronized void load(Context context)throws Exception{
        if(model!=null)return;ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=context.getAssets().open("visuals/home-vessel-3d/vessel.mesh.json")){byte[] chunk=new byte[8192];for(int n;(n=in.read(chunk))!=-1;)bytes.write(chunk,0,n);}
        JSONArray parts=new JSONObject(bytes.toString("UTF-8")).getJSONArray("parts");ArrayList<Polygon> result=new ArrayList<>();
        for(int i=0;i<parts.length();i++){JSONObject part=parts.getJSONObject(i);JSONArray rgb=part.getJSONArray("color"),faces=part.getJSONArray("faces");float[] color=new float[4];for(int j=0;j<4;j++)color[j]=(float)rgb.getDouble(j);
            for(int j=0;j<faces.length();j++){JSONArray face=faces.getJSONArray(j);float[][] xyz=new float[face.length()][3];for(int k=0;k<face.length();k++)for(int q=0;q<3;q++)xyz[k][q]=(float)face.getJSONArray(k).getDouble(q);result.add(new Polygon(part.getString("name"),xyz,color));}}
        model=Collections.unmodifiableList(result);
    }
    private void install(){if(!polygons.isEmpty()||model==null)return;for(Polygon p:model)polygons.add(new Projected(p));}
    public boolean isModelReady(){return !polygons.isEmpty();}
    public HomeVesselState presentation(){return state;}
    public void mode(boolean reverse,boolean stirrer,boolean visible){if(this.reverse==reverse&&this.stirrer==stirrer&&visibleMode==visible)return;this.reverse=reverse;this.stirrer=stirrer;visibleMode=visible;describe();invalidate();}
    public void update(ControlState live,long now){
        update(live,now,null);
    }
    public void update(ControlState live,long now,HomeCupState cup){
        HomeVesselState next=HomeVesselState.from(live,now,cup);boolean changed=next.lid!=state.lid||next.cupInsertionCue!=state.cupInsertionCue||next.cupWeightDetected!=state.cupWeightDetected;
        if(next.lid!=state.lid)animate(true,next.lid==HomeVesselState.Lid.OPEN?1:next.lid==HomeVesselState.Lid.CLOSED?0:.25f);
        if(next.cupInsertionCue!=state.cupInsertionCue){animate(false,next.cupInsertionCue?0:1);if(next.cupInsertionCue)cueAt=now;}
        state=next;foreground=live.foreground;boolean nextFresh=foreground&&live.fresh(now);changed|=nextFresh!=fresh;fresh=nextFresh;sampleAt=live.statusAt;infoAt=live.infoAt;
        boolean motor=fresh&&live.ownRun&&live.motorStatus!=0&&!live.stopping&&live.state!=3;
        changed|=motor!=running||temperature!=live.temperature;running=motor;temperature=live.temperature;
        if(changed){describe();invalidate();}
    }
    public void startHint(){cueAt=SystemClock.elapsedRealtime();invalidate();}
    private boolean motion(){return isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown()&&ValueAnimator.areAnimatorsEnabled();}
    private void animate(boolean lid,float target){ValueAnimator previous=lid?lidMotion:cupMotion;if(previous!=null)previous.cancel();if(!motion()){if(lid)lidLift=target;else cupLift=target;return;}
        ValueAnimator a=ValueAnimator.ofFloat(lid?lidLift:cupLift,target);a.setDuration(lid?650:850);a.setInterpolator(new android.view.animation.DecelerateInterpolator());a.addUpdateListener(v->{if(lid)lidLift=(float)v.getAnimatedValue();else cupLift=(float)v.getAnimatedValue();invalidate();});if(lid)lidMotion=a;else cupMotion=a;a.start();}
    private void describe(){setContentDescription("3D-Mixtopf. "+state.hint()+". "+(reverse?"Messer rückwärts zum Rühren":"Messer vorwärts zum Zerkleinern")+(stirrer?". Rühraufsatz vom Benutzer als eingesetzt ausgewählt":". Ohne zusätzlich ausgewählten Rühraufsatz")+(state.cupInsertionCue?"":". Messbecher antippen, um das Einsetzen zu bestätigen."));}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);long now=SystemClock.elapsedRealtime();
        boolean live=fresh&&foreground&&now>=sampleAt&&now-sampleAt<500&&now>=infoAt&&now-infoAt<1200;
        boolean spin=motion()&&running&&live&&state.lid==HomeVesselState.Lid.CLOSED;
        if(spin&&lastFrame>0)angle+=(reverse?-1:1)*Math.min(80,now-lastFrame)*.0018f;lastFrame=now;
        float scale=Math.min(getWidth()/3.4f,getHeight()/3.05f),cx=getWidth()*.53f,cy=getHeight()*.56f;
        paint.setShader(new RadialGradient(cx,cy+scale*1.15f,scale*1.1f,new int[]{0x55000000,0x00000000},null,Shader.TileMode.CLAMP));canvas.drawOval(cx-scale*1.4f,cy+scale*.9f,cx+scale*1.4f,cy+scale*1.35f,paint);paint.setShader(null);
        if(live&&temperature>=40){float warmth=Math.min(1,(temperature-35)/145f);paint.setShader(new RadialGradient(cx,cy+scale*.6f,scale,new int[]{Color.argb((int)(65*warmth),238,101,66),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawCircle(cx,cy+scale*.6f,scale,paint);paint.setShader(null);}
        float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);
        for(Projected p:polygons){Polygon f=p.source;boolean tool=f.part.equals("blade")||f.part.equals("stirrer");
            if(f.part.equals("stirrer")&&!stirrer){p.depth=Float.NEGATIVE_INFINITY;continue;}
            float lift=f.part.startsWith("lid")?lidLift*.55f:f.part.startsWith("cup")?lidLift*.55f+cupLift*.35f:0;
            p.depth=0;for(int i=0;i<f.xyz.length;i++){float x=f.xyz[i][0],y=f.xyz[i][1]+lift-1.27f,z=f.xyz[i][2];if(tool){float xx=x*cs-z*sn;z=x*sn+z*cs;x=xx;}
                float xx=x*.976f-z*.218f,zz=z*.976f+x*.218f,yy=y*.976f-zz*.218f,depth=zz*.976f+y*.218f,perspective=1/(1-depth*.045f);
                p.x[i]=cx+xx*scale*perspective;p.y[i]=cy-yy*scale*perspective;p.depth+=depth/f.xyz.length;}
            float nx=tool?f.nx*cs-f.nz*sn:f.nx,nz=tool?f.nx*sn+f.nz*cs:f.nz;
            float diffuse=Math.abs(-nx*.48f+f.ny*.72f+nz*.50f);p.light=.40f+.57f*diffuse;
        }
        polygons.sort((a,b)->Float.compare(a.depth,b.depth));boolean cue=now-cueAt<4000||isSelected()&&!state.cupInsertionCue&&state.lid==HomeVesselState.Lid.CLOSED;
        cupBounds.setEmpty();for(Projected p:polygons)if(p.source.part.startsWith("cup"))for(int i=0;i<p.x.length;i++){if(cupBounds.isEmpty())cupBounds.set(p.x[i],p.y[i],p.x[i]+1,p.y[i]+1);else cupBounds.union(p.x[i],p.y[i]);}
        for(Projected p:polygons){if(p.depth==Float.NEGATIVE_INFINITY)continue;Polygon f=p.source;float[] rgb=f.color;int r=(int)(rgb[0]*255),g=(int)(rgb[1]*255),b=(int)(rgb[2]*255);float alpha=rgb[3];
            if(f.part.equals("blade")){int color=reverse?0xff63c49a:0xffef655a;r=Color.red(color);g=Color.green(color);b=Color.blue(color);}
            if(f.part.equals("bowl")&&!visibleMode)alpha=.48f;
            if(state.lid==HomeVesselState.Lid.POT_MISSING)alpha*=.23f;
            if(cue&&f.part.startsWith("cup")){r=238;g=101;b=66;alpha=Math.max(alpha,.45f+(float)Math.sin(now*.008f)*.18f);}
            paint.setColor(Color.argb((int)(255*alpha),Math.min(255,(int)(r*p.light)),Math.min(255,(int)(g*p.light)),Math.min(255,(int)(b*p.light))));paint.setStyle(Paint.Style.FILL);
            path.rewind();path.moveTo(p.x[0],p.y[0]);for(int i=1;i<p.x.length;i++)path.lineTo(p.x[i],p.y[i]);path.close();canvas.drawPath(path,paint);
        }
        if(visibleMode){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,scale*.025f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(reverse?0xff63c49a:0xffef655a);
            RectF orbit=new RectF(cx-scale*.65f,cy+scale*.22f,cx+scale*.65f,cy+scale*.60f);canvas.drawArc(orbit,reverse?30:150,reverse?245:-245,false,paint);
            float end=(reverse?275:-95)*(float)Math.PI/180,x=orbit.centerX()+orbit.width()/2*(float)Math.cos(end),y=orbit.centerY()+orbit.height()/2*(float)Math.sin(end);canvas.drawLine(x,y,x+(reverse?1:-1)*scale*.09f,y-scale*.06f,paint);paint.setStyle(Paint.Style.FILL);
        }
        paint.setColor(state.lid==HomeVesselState.Lid.OPEN||cue?Ui.ACCENT:Ui.MUTED);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Ui.dp(getContext(),13));paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        String hint=!live?"Deckelstatus unbekannt":state.hint();canvas.drawText(hint,getWidth()/2f,getHeight()-Ui.dp(getContext(),3),paint);
        if(spin||motion()&&cue)postInvalidateDelayed(40);
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);lastFrame=0;}
    @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){RectF hit=new RectF(cupBounds);hit.inset(-Ui.dp(getContext(),12),-Ui.dp(getContext(),12));if(state.lid!=HomeVesselState.Lid.CLOSED||!hit.contains(event.getX(),event.getY()))return false;}return super.onTouchEvent(event);}
    @Override protected void onDetachedFromWindow(){if(lidMotion!=null)lidMotion.cancel();if(cupMotion!=null)cupMotion.cancel();lastFrame=0;super.onDetachedFromWindow();}
}
