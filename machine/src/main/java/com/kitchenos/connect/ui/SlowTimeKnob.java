package com.kitchenos.connect.ui;

/** Minute detents with a restrained ramp for the one-to-eight-hour program. */
final class SlowTimeKnob {
    private long previous=-1;
    private int direction;
    private float gain=1;

    void reset(){previous=-1;direction=0;gain=1;}

    int seconds(int delta,long time){
        int nextDirection=Integer.signum(delta);
        if(nextDirection==0)return 0;
        long gap=time-previous;
        if(previous<0||nextDirection!=direction||gap<=0||gap>=450)gain=1;
        else{
            float pace=180f/Math.max(35,gap);
            float target=Math.max(1,Math.min(5,pace*pace));
            gain+=.20f*(target-gain);
        }
        previous=time;direction=nextDirection;
        return nextDirection*60*Math.round(gain)*Math.min(240,Math.abs(delta));
    }
}
