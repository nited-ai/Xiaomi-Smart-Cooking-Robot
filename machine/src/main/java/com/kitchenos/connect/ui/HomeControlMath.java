package com.kitchenos.connect.ui;

/** Fine wheel travel with bounded acceleration for deliberate fast movement. */
public final class HomeControlMath {
    private static final float DISTANCE_PER_STEP=75f;
    public static final float DIRECT_ROW_DP=75f;
    private HomeControlMath(){}
    public static int dragIndex(int start,int maximum,float displacement){
        if(maximum<=0)return 0;int direction=displacement>=0?1:-1;
        return Math.floorMod(start+direction*dragStrength(maximum,Math.abs(displacement)),maximum+1);
    }
    public static int dragStrength(int maximum,float distance){
        return (int)dragPosition(maximum,distance);
    }
    /** Fractional wheel travel; quantize only the selected value, never the drawing. */
    public static float dragPosition(int maximum,float distance){
        if(maximum<=0)return 0;
        return Math.min(maximum,Math.abs(distance)/DISTANCE_PER_STEP);
    }
    /** Speed selects a direction from zero, so its full range must fit that side of the screen. */
    public static float speedPosition(int maximum,float distance,float room){
        if(maximum<=0)return 0;
        float travel=Math.min(DISTANCE_PER_STEP*maximum,Math.max(80,room));
        distance=Math.abs(distance);
        return distance>=travel?maximum:distance/travel*maximum;
    }
    /** Select the number actually nearest the center, also across a cyclic seam. */
    public static int wheelIndex(int start,int maximum,float steps){
        return maximum<=0?0:Math.floorMod(start+Math.round(steps),maximum+1);
    }
    /** The same cyclic range is used for time-wheel selection and its visible neighbors. */
    public static int cyclicValue(int position,int minimum,int maximum){
        return minimum+Math.floorMod(position-minimum,Math.max(1,maximum-minimum+1));
    }
    /** Slow near each centered value, retaining continuous movement and the same selection boundary. */
    public static float detentPosition(float position){
        double phase=position-Math.floor(position);
        return position-(float)(.65*Math.sin(2*Math.PI*phase)/(2*Math.PI));
    }
    /** Local, continuous center latch; outside its shoulder direct finger travel is unchanged. */
    public static float directDetentPosition(float position){
        float center=Math.round(position),offset=position-center,distance=Math.abs(offset);
        final float latch=.06f,shoulder=.28f;
        if(distance>=shoulder)return position;
        if(distance<=latch)return center;
        // Cubic Hermite: zero slope at the latch, unit slope where direct travel resumes.
        float t=(distance-latch)/(shoulder-latch),t2=t*t,t3=t2*t;
        float eased=shoulder*(3*t2-2*t3)+(shoulder-latch)*(t3-t2);
        return center+Math.copySign(eased,offset);
    }
    /** Bounded direct travel consumes end-stop overscroll, so reversing responds immediately. */
    public static final class DirectDrag {
        private float anchor,minimum,maximum;
        public void reset(float start,float minimum,float maximum){this.minimum=minimum;this.maximum=maximum;anchor=Math.max(minimum,Math.min(maximum,start));}
        public float move(float steps){float candidate=anchor+steps,position=Math.max(minimum,Math.min(maximum,candidate));if(candidate!=position)anchor=position-steps;return position;}
    }
    /** Stop at zero once per crossing; only additional physical travel can pass it. */
    public static final class ZeroStopDrag {
        public static final float RELEASE_DP=75;
        private float anchor,position,minimum,maximum,period,stopFinger;
        private int entryDirection;
        private boolean holding;
        /** period > 0 repeats zero at cyclic seams; 0 is signed zero; negative disables. */
        public void reset(float start,float minimum,float maximum,float period){
            this.minimum=minimum;this.maximum=maximum;this.period=period;
            position=anchor=Math.max(minimum,Math.min(maximum,start));holding=false;entryDirection=0;
        }
        private float clamp(float value){return Math.max(minimum,Math.min(maximum,value));}
        private float crossedZero(float from,float to){
            if(period<0||from==to)return Float.NaN;
            float zero=0;
            if(period>0)zero=(to>from?(float)Math.floor(from/period)+1:(float)Math.ceil(from/period)-1)*period;
            else if(from==0)return Float.NaN;
            return to>from&&from<zero&&to>=zero||to<from&&from>zero&&to<=zero?zero:Float.NaN;
        }
        public float move(float steps,float fingerDp){
            if(holding){
                float travel=fingerDp-stopFinger;
                if(travel*entryDirection>=0&&Math.abs(travel)<=RELEASE_DP){anchor=position-steps;return position;}
                // Reversal is immediately fine. Continuing must first pay the 75dp resistance.
                float extra=travel*entryDirection<0?travel:travel-entryDirection*RELEASE_DP;
                float candidate=clamp(position+extra/DIRECT_ROW_DP),zero=crossedZero(position,candidate);
                holding=!Float.isNaN(zero);
                if(holding){entryDirection=candidate>position?1:-1;stopFinger=fingerDp;}
                position=holding?zero:candidate;anchor=position-steps;return position;
            }
            float candidate=clamp(anchor+steps),zero=crossedZero(position,candidate);
            if(!Float.isNaN(zero)){
                entryDirection=candidate>position?1:-1;position=zero;anchor=zero-steps;
                stopFinger=fingerDp;holding=true;return position;
            }
            if(candidate!=anchor+steps)anchor=candidate-steps;
            return position=candidate;
        }
        public boolean holding(){return holding;}
        /** Prediction must not carry the drawing across a zero the finger has not passed. */
        public float constrainVisual(float visual){
            if(holding)return position;
            float bounded=clamp(visual),zero=crossedZero(position,bounded);
            return Float.isNaN(zero)?bounded:zero;
        }
    }
    /** A deliberate third-step pull commits one detent; unchanged input never commits another. */
    public static final class DetentDrag {
        private static final float THRESHOLD=1f/3;
        private float previous,pending,preview;
        private int center,minimum,maximum;
        public void reset(int start,int minimum,int maximum){
            this.minimum=minimum;this.maximum=maximum;center=Math.max(minimum,Math.min(maximum,start));
            previous=pending=preview=0;
        }
        public void move(float steps){
            float delta=steps-previous;previous=steps;if(delta==0)return;
            pending+=delta;
            // Discard outward travel at an end stop so reversing immediately works again.
            if(center==minimum&&pending<0||center==maximum&&pending>0){pending=preview=0;return;}
            int crossed=(int)(pending/THRESHOLD);
            if(crossed!=0){
                center=Math.max(minimum,Math.min(maximum,center+crossed));
                pending-=crossed*THRESHOLD;
                if(center==minimum&&pending<0||center==maximum&&pending>0)pending=0;
                // Complete this move at the center. Retain excess distance for the next real sample.
                preview=0;
            }else preview=Math.max(-THRESHOLD,Math.min(THRESHOLD,preview+delta));
        }
        public int center(){return center;}
        public float previewOffset(){return preview;}
        /** An autonomous snap ends centered; later real travel starts its preview from there. */
        public void settled(){preview=0;}
    }
    /** Integrate only new finger travel: changing speed never rescales earlier travel. */
    public static final class WheelDrag {
        private final float distancePerStep,maximumGain;
        private float previous,steps,filteredSpeed,extraSteps;
        private int direction;
        private long sampleAt,motionAt,fineUntil;
        public WheelDrag(){this(DISTANCE_PER_STEP,6);}
        private WheelDrag(float distancePerStep,float maximumGain){this.distancePerStep=distancePerStep;this.maximumGain=maximumGain;}
        /** Calm preview wheels require longer travel and bounded gain during quick swipes. */
        public static WheelDrag deliberate(){return new WheelDrag(DISTANCE_PER_STEP*3,2);}
        /** Direct fine travel, with a modest capped gain for deliberate fast swipes. */
        public static WheelDrag direct(){return new WheelDrag(DIRECT_ROW_DP,2.75f);}
        public float distancePerStep(){return distancePerStep;}
        public void reset(long eventTime){previous=steps=filteredSpeed=extraSteps=0;direction=0;sampleAt=motionAt=eventTime;fineUntil=0;}
        public float move(float displacement,long eventTime){
            if(eventTime<=sampleAt)return steps;
            sampleAt=eventTime;
            float delta=displacement-previous;
            if(delta==0){filteredSpeed=0;return steps;}
            long elapsed=eventTime-motionAt;motionAt=eventTime;previous=displacement;
            int nextDirection=delta>0?1:-1;
            float speed=Math.min(2500,Math.abs(delta)*1000/elapsed);
            // Chip holds can end in a short burst of catch-up packets. Keep that burst fine.
            if(direction!=0&&elapsed>=90)fineUntil=eventTime+160;
            // Slowing down or reversing restores fine control immediately.
            if(eventTime<fineUntil||direction!=0&&direction!=nextDirection)filteredSpeed=0;
            else if(speed<filteredSpeed)filteredSpeed=speed;
            else filteredSpeed+=(speed-filteredSpeed)*(1-(float)Math.exp(-elapsed/100f));
            direction=nextDirection;
            float ramp=Math.max(0,Math.min(1,(filteredSpeed-350)/750));
            float gain=1+(maximumGain-1)*ramp*ramp*(3-2*ramp);
            // Preserve exact fine tracking; integrate only the extra travel from fast movement.
            extraSteps+=delta*(gain-1)/distancePerStep;
            steps=displacement/distancePerStep+extraSteps;
            return steps;
        }
    }
    /** Equivalent cyclic value closest to the finger, including the 59/0 seam. */
    public static float nearestPosition(float from,int target,int count){return count<=1?target:target+Math.round((from-target)/count)*count;}
    public static int reachedIncrement(int delta){int result=-1;for(int value:new int[]{1,5,10,20,30})if(value<=delta)result=value;return result;}
}
