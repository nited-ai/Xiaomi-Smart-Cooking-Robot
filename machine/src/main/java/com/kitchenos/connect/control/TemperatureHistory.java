package com.kitchenos.connect.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Passive sensor history. Never controls heating or clears an interlock. */
public final class TemperatureHistory {
    public static final int LIMIT=1800;
    public static final long FRESH_MS=1500;
    public static final class Point {
        public final long time;
        public final int temperature,target;
        public final boolean gap;
        Point(long time,int temperature,int target,boolean gap){this.time=time;this.temperature=temperature;this.target=target;this.gap=gap;}
    }
    private final ArrayList<Point> points=new ArrayList<>();
    private final List<Point> readonly=Collections.unmodifiableList(points);
    private long observedAt=-1,sampledAt=-1;
    private int temperature=-1,target;
    private boolean hot,heating,valid;
    public List<Point> points(){return readonly;}
    public boolean hot(){return hot;}
    public int temperature(){return temperature;}
    public int target(){return target;}
    public boolean fresh(long now){return valid&&observedAt>=0&&now>=observedAt&&now-observedAt<FRESH_MS;}
    public boolean heating(long now){return fresh(now)&&heating;}
    public void invalidate(){valid=false;heating=false;}
    public void observe(long now,long wall,int value,int requested,boolean trustworthy,boolean heatActive){
        if(!trustworthy||value<1||value>250){invalidate();return;}
        if(observedAt>=0&&now<observedAt)return;
        boolean gap=!valid||observedAt<0||now-observedAt>5000;
        observedAt=now;valid=true;temperature=value;hot=value>100;target=Math.max(0,Math.min(180,requested));heating=heatActive&&target>value+2;
        Point last=points.isEmpty()?null:points.get(points.size()-1);
        if(gap||last==null||now-sampledAt>=2000||last.target!=target){
            // Wall clock correction must not reverse a plotted time axis.
            long time=last==null?wall:Math.max(last.time+1,wall);
            points.add(new Point(time,value,target,gap));sampledAt=now;
            if(points.size()>LIMIT)points.remove(0);
            while(points.size()>1&&time-points.get(0).time>3600000)points.remove(0);
        }
    }
    /** Versioned bounded snapshot; restored observations are never live. */
    public String encode(){
        StringBuilder out=new StringBuilder("1,").append(hot?1:0).append(',').append(temperature).append('\n');
        for(Point p:points)out.append(p.time).append(',').append(p.temperature).append(',').append(p.target).append(',').append(p.gap?1:0).append('\n');
        return out.toString();
    }
    public void restore(String text){
        invalidate();observedAt=sampledAt=-1;points.clear();
        if(text==null||text.length()>160000)return;
        try{
            String[] lines=text.split("\n");String[] header=lines[0].split(",");
            if(!header[0].equals("1")||header.length!=3)return;
            int value=Integer.parseInt(header[2]);if(value< -1||value>250)return;
            temperature=value;hot=header[1].equals("1")||value>100;
            for(int i=Math.max(1,lines.length-LIMIT);i<lines.length;i++){
                String[] v=lines[i].split(",");if(v.length!=4)break;
                long time=Long.parseLong(v[0]);int temp=Integer.parseInt(v[1]),goal=Integer.parseInt(v[2]);
                if(time<0||temp<1||temp>250||goal<0||goal>180||!points.isEmpty()&&time<=points.get(points.size()-1).time)break;
                points.add(new Point(time,temp,goal,v[3].equals("1")));
            }
        }catch(RuntimeException ignored){/* Keep any recovered hot warning; never infer cool from damage. */}
    }
}
