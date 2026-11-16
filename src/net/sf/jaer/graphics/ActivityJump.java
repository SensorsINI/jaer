package net.sf.jaer.graphics;

/**
 * Jump targets on a log-relative event-rate sparkline.
 * <p>
 * A rise counts when a bin is at least {@code threshold} above the minimum of
 * the previous {@link #LOOKBACK_BINS} bins, and the bin before was not already
 * that high. The landing time is the start of the bin before that rise, so
 * forward playback reaches the change from the quiet side.
 */
public final class ActivityJump {

    public static final String PREF_KEY = "AEViewer.activityJumpThreshold";
    /** Rise on the 0–1 sparkline. 0.20 is a clearly visible step. */
    public static final float THRESHOLD_DEFAULT = 0.20f;
    public static final float THRESHOLD_MIN = 0.01f;
    public static final float THRESHOLD_MAX = 1f;
    /**
     * Bins used as the quiet baseline. A step that stays high does not make
     * another jump until the baseline itself is high.
     */
    static final int LOOKBACK_BINS = 2;
    /**
     * A Shift+J this soon after another Shift+J skips the rise just landed on.
     * Playback has usually moved past that landing, so one step back returns to it.
     */
    public static final long JUMP_BACK_REPEAT_MS = 1000L;

    private ActivityJump() {
    }

    public static float clampThreshold(float t) {
        if (Float.isNaN(t) || t < THRESHOLD_MIN) {
            return THRESHOLD_MIN;
        }
        if (t > THRESHOLD_MAX) {
            return THRESHOLD_MAX;
        }
        return t;
    }

    /**
     * @param rates log-relative bins, equal time, or null
     * @param startUs timeline origin in the same clock as playback seek
     * @param durationUs span covered by {@code rates}
     * @param fromUs current playhead
     * @param threshold minimum sparkline rise, clamped to {@link #THRESHOLD_MIN}–{@link #THRESHOLD_MAX}
     * @param forward later than {@code fromUs} when true, earlier when false
     * @return landing timestamp, or null when this trace has no such rise
     */
    public static Long landingUs(float[] rates, long startUs, long durationUs, long fromUs,
            float threshold, boolean forward) {
        return landingUs(rates, startUs, durationUs, fromUs, threshold, forward, 1);
    }

    /**
     * @param backSteps earlier rises to pass when {@code forward} is false.
     *        1 is the nearest landing before {@code fromUs}. 2 skips that one,
     *        which is the rise a jump-back just landed on. If fewer rises exist,
     *        the earliest one is returned.
     */
    public static Long landingUs(float[] rates, long startUs, long durationUs, long fromUs,
            float threshold, boolean forward, int backSteps) {
        if (rates == null || rates.length < 2 || durationUs <= 0L) {
            return null;
        }
        float th = clampThreshold(threshold);
        int n = rates.length;
        int steps = forward ? 1 : Math.max(1, backSteps);
        long[] recent = new long[steps];
        int nRecent = 0;
        boolean prevHigh = false;
        for (int i = 1; i < n; i++) {
            boolean high = rise(rates, i) >= th;
            if (high && !prevHigh) {
                // Start of the quiet bin before the rise. Compared by time, not
                // bin index: a seek often stops a few microseconds short of the
                // boundary and would still fall in the previous bin.
                long landing = timeOfBin(i - 1, startUs, durationUs, n);
                if (forward) {
                    if (landing > fromUs) {
                        return landing;
                    }
                } else if (landing < fromUs) {
                    if (nRecent < steps) {
                        recent[nRecent++] = landing;
                    } else {
                        System.arraycopy(recent, 1, recent, 0, steps - 1);
                        recent[steps - 1] = landing;
                    }
                }
            }
            prevHigh = high;
        }
        if (!forward && nRecent > 0) {
            return recent[0];
        }
        return null;
    }

    /** True when another Shift+J within {@link #JUMP_BACK_REPEAT_MS} should skip one rise. */
    public static boolean repeatJumpBack(long lastJumpBackMs) {
        if (lastJumpBackMs <= 0L) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - lastJumpBackMs;
        return elapsed >= 0L && elapsed <= JUMP_BACK_REPEAT_MS;
    }

    public static String formatAt(long timeUs, long originUs) {
        double sec = (timeUs - originUs) * 1e-6;
        if (sec < 0d) {
            sec = 0d;
        }
        return String.format("Activity %.3f s", sec);
    }

    public static String noneMessage(boolean forward, float threshold) {
        String which = forward ? "No later activity increase" : "No earlier activity increase";
        return String.format("%s (sparkline rise %.2f)", which, clampThreshold(threshold));
    }

    static float rise(float[] rates, int i) {
        int from = Math.max(0, i - LOOKBACK_BINS);
        float baseline = rates[from];
        for (int k = from + 1; k < i; k++) {
            if (rates[k] < baseline) {
                baseline = rates[k];
            }
        }
        return rates[i] - baseline;
    }

    static long timeOfBin(int bin, long startUs, long durationUs, int nBins) {
        if (bin <= 0) {
            return startUs;
        }
        if (bin >= nBins) {
            bin = nBins - 1;
        }
        return startUs + (bin * durationUs) / nBins;
    }
}
