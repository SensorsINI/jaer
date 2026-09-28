/*
 * AcquisitionProcessingStats.java
 *
 * Sliding-window mean and std of the gap between filter-chain runs.
 */
package net.sf.jaer.eventprocessing;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import net.sf.jaer.util.EngineeringFormat;

/**
 * Inter-packet interval of USB-thread filter-chain runs. Samples are the last
 * {@link #WINDOW} gaps, in the same {@link DescriptiveStatistics} window used
 * by optical-flow {@code Measurand}. The displayed rate is {@code 1/mean}.
 * Updated on the USB thread. {@link #line()} is safe to read from the OpenGL thread.
 */
public final class AcquisitionProcessingStats {

    static final String LABEL = "Low-latency mode";
    /** Shown when the chain is not running any filter, so no rate samples exist. */
    public static final String ENABLE_FILTERS = LABEL + " - enable filters to see stats";
    /** FIR length. Oldest gaps drop out of the mean and standard deviation. */
    static final int WINDOW = 100;

    private final EngineeringFormat eng = new EngineeringFormat();
    private final DescriptiveStatistics intervals = new DescriptiveStatistics(WINDOW);
    private boolean hasLast;
    private long lastCycleNs;
    private volatile String line = LABEL;

    public AcquisitionProcessingStats() {
        eng.setPrecision(1);
    }

    public synchronized void reset() {
        intervals.clear();
        hasLast = false;
        lastCycleNs = 0;
        line = LABEL;
    }

    /**
     * One filter-chain run, timestamped on the USB thread. The first call only
     * marks the start of the next gap.
     *
     * @return true when a gap was recorded
     */
    public synchronized boolean noteCycle(long nowNs) {
        boolean recorded = false;
        if (hasLast) {
            long dt = nowNs - lastCycleNs;
            if (dt > 0) {
                intervals.addValue(dt * 1e-9);
                recorded = true;
                line = formatLine();
            }
        }
        hasLast = true;
        lastCycleNs = nowNs;
        return recorded;
    }

    public String line() {
        return line;
    }

    private String formatLine() {
        long n = intervals.getN();
        if (n < 1) {
            return LABEL;
        }
        double mean = intervals.getMean();
        if (!(mean > 0) || Double.isNaN(mean)) {
            return LABEL;
        }
        String intervalText = engTrim(mean);
        String rateText = engTrim(1.0 / mean);
        if (n < 2) {
            return LABEL + "  " + intervalText + " s  " + rateText + " /s";
        }
        return LABEL + "  " + intervalText + " ± " + engTrim(intervals.getStandardDeviation())
                + " s  " + rateText + " /s";
    }

    private String engTrim(double x) {
        String s = eng.format(x);
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        return s.trim();
    }
}
