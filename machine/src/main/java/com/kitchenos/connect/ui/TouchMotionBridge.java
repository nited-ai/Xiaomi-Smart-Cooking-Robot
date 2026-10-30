package com.kitchenos.connect.ui;

/**
 * Display-only bridge for missing slow-drag coordinates. Positions are in dp.
 * It cannot distinguish a real stop from a controller hold. Never use its output to commit
 * a value, trigger haptics, or send a machine command; those must use measured input.
 */
public final class TouchMotionBridge {
    public static final float MAX_LEAD_DP = 12;
    private static final long COAST_MS = 220, END_MS = 360;
    // Retain >=120 ms even at 200 Hz; sample count must not change the braking window.
    private final float[] positions = new float[32];
    private final long[] times = new long[32];
    private int count, direction;
    private long sampleAt, motionAt, frameAt;
    private float raw, velocity, rendered;
    private boolean active, braking, adaptiveBraking = true;

    /** Preview comparison with the previously evaluated constant-horizon bridge. */
    public void adaptiveBraking(boolean enabled) { adaptiveBraking = enabled; braking = false; }

    public void reset(float position, long time) {
        raw = rendered = position;
        sampleAt = motionAt = frameAt = time;
        velocity = 0; direction = 0; count = 1; active = true; braking = false;
        positions[0] = position; times[0] = time;
    }

    /** Only real input samples, including MotionEvent history, belong here. */
    public void sample(float position, long time) {
        if (!active || !Float.isFinite(position) || time <= sampleAt) return;
        sampleAt = time;
        float delta = position - raw;
        if (delta == 0) return; // Repeated packets must not refresh the prediction horizon.
        int nextDirection = delta > 0 ? 1 : -1;
        boolean reversed = direction != 0 && direction != nextDirection;
        long gap = time - motionAt;
        if (reversed || gap > END_MS) {
            count = 0; velocity = 0; braking = adaptiveBraking && reversed;
            // A measured reversal takes priority over the old predicted direction.
            if (reversed) rendered = position;
        }
        raw = position; motionAt = time; direction = nextDirection;
        if (count == positions.length) {
            System.arraycopy(positions, 1, positions, 0, count - 1);
            System.arraycopy(times, 1, times, 0, count - 1);
            count--;
        }
        positions[count] = position; times[count++] = time;
        int first = 0;
        while (first < count - 3 && time - times[first + 1] >= 120) first++;
        long span = time - times[first];
        // Require a short history. A single jump or regrip is insufficient evidence.
        long trainingGap = adaptiveBraking ? 180 : 90;
        if (count - first >= 3 && span >= 60 && gap <= trainingGap) {
            float estimate = (position - positions[first]) / span;
            int recentFirst = Math.max(first, count - 3);
            while (recentFirst > first && time - times[recentFirst] < 70) recentFirst--;
            long recentSpan = time - times[recentFirst];
            if (adaptiveBraking && recentSpan >= 60) {
                float recent = (position - positions[recentFirst]) / recentSpan;
                // Two real intervals establish a slowdown. Allow one pixel of rounding
                // noise so alternating 1/2-pixel reports do not repeatedly brake the wheel.
                float uncertainty = 1f / recentSpan;
                if (Math.abs(recent) + uncertainty < Math.abs(velocity) * .9f) braking = true;
                else if (Math.abs(recent) > Math.abs(velocity) * 1.25f + uncertainty) braking = false;
                if (braking) estimate = recent;
            }
            velocity = estimate;
        } else if (gap > trainingGap) {
            // Do not interpret a catch-up burst as a fresh high-speed movement.
            count = 1; positions[0] = position; times[0] = time;
        }
    }

    private float target(long now) {
        if (!active) return raw;
        float age = Math.max(0, now - motionAt);
        float travelTime;
        if (Math.abs(velocity) > .16f) {
            // The long bridge is for slow movement; quick swipes predict at most 40 ms.
            travelTime = Math.min(age, 40);
        } else {
            float coast = braking ? 70 : COAST_MS, end = braking ? 160 : END_MS;
            float taper = Math.max(0, Math.min(end - coast, age - coast));
            travelTime = Math.min(age, coast) + taper - taper * taper / (2 * (end - coast));
        }
        return raw + clamp(velocity * travelTime, MAX_LEAD_DP);
    }

    /** Call once per display frame. A 24-ms reconciliation avoids a jump on recovery. */
    public float frame(long now) {
        if (!active) return raw;
        if (now <= frameAt) return rendered;
        long elapsed = Math.min(48, now - frameAt); frameAt = now;
        float goal = target(now);
        float correction = (goal - rendered) * (1 - (float) Math.exp(-elapsed / 24f));
        // If the first recovery packet is still behind the prediction, let the measured
        // position catch up. Only a measured reversal may reverse the drawing.
        if (direction == 0 || correction * direction >= 0) rendered += correction;
        rendered = raw + clamp(rendered - raw, MAX_LEAD_DP);
        return rendered;
    }

    public float measured() { return raw; }
    public boolean needsFrame(long now) {
        float remaining = target(now) - rendered;
        long horizon = Math.abs(velocity) > .16f ? 40 : braking ? 160 : END_MS;
        return active && (velocity != 0 && now - motionAt < horizon || Math.abs(remaining) > .001f && remaining * direction >= 0);
    }
    public void finish() { active = false; velocity = 0; rendered = raw; }
    private static float clamp(float value, float limit) { return Math.max(-limit, Math.min(limit, value)); }
}
