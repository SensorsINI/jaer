/*
 * TimestampSpread.java
 *
 * Copyright 2026 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.util;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;

/**
 * Summary of timestamp distribution in a contiguous slice of events.
 * Assumes events are roughly time-ordered; unique timestamps are counted
 * by consecutive differences (O(n), no hash set).
 */
public final class TimestampSpread {

    public static final TimestampSpread EMPTY = new TimestampSpread(0, 0, 0, 0, 0);

    public final int count;
    public final int uniqueTs;
    public final int spanUs;
    public final int minStepUs;
    public final int maxStepUs;

    public TimestampSpread(int count, int uniqueTs, int spanUs, int minStepUs, int maxStepUs) {
        this.count = count;
        this.uniqueTs = uniqueTs;
        this.spanUs = spanUs;
        this.minStepUs = minStepUs;
        this.maxStepUs = maxStepUs;
    }

    public static TimestampSpread compute(int[] timestamps, int start, int count) {
        if (count <= 0) {
            return EMPTY;
        }
        if (count == 1) {
            return new TimestampSpread(1, 1, 0, 0, 0);
        }
        int uniqueTs = 1;
        int minStep = Integer.MAX_VALUE;
        int maxStep = 0;
        for (int i = start + 1; i < start + count; i++) {
            final int ts = timestamps[i];
            final int prevTs = timestamps[i - 1];
            if (ts != prevTs) {
                final int step = ts - prevTs;
                if (step > 0) {
                    if (step < minStep) {
                        minStep = step;
                    }
                    if (step > maxStep) {
                        maxStep = step;
                    }
                }
                uniqueTs++;
            }
        }
        final int spanUs = timestamps[start + count - 1] - timestamps[start];
        if (minStep == Integer.MAX_VALUE) {
            minStep = 0;
        }
        return new TimestampSpread(count, uniqueTs, spanUs, minStep, maxStep);
    }

    /**
     * Sniff spread over displayed (DVS) events in a packet.
     * Uses the default {@link EventPacket#iterator()}, which skips filtered-out
     * events and, for {@code ApsDvsEventPacket}, yields only DVS events.
     * Does not write an output packet or mark events filtered-out.
     */
    public static TimestampSpread computeDisplayedEvents(EventPacket<? extends BasicEvent> packet) {
        if (packet == null || packet.isEmpty()) {
            return EMPTY;
        }
        int count = 0;
        int uniqueTs = 0;
        int minStep = Integer.MAX_VALUE;
        int maxStep = 0;
        int firstTs = 0;
        int lastTs = 0;
        int prevTs = 0;
        boolean first = true;
        for (BasicEvent e : packet) {
            final int ts = e.timestamp;
            count++;
            if (first) {
                firstTs = ts;
                prevTs = ts;
                uniqueTs = 1;
                first = false;
            } else {
                if (ts != prevTs) {
                    final int step = ts - prevTs;
                    if (step > 0) {
                        if (step < minStep) {
                            minStep = step;
                        }
                        if (step > maxStep) {
                            maxStep = step;
                        }
                    }
                    uniqueTs++;
                }
                prevTs = ts;
            }
            lastTs = ts;
        }
        if (count <= 0) {
            return EMPTY;
        }
        if (count == 1) {
            return new TimestampSpread(1, 1, 0, 0, 0);
        }
        if (minStep == Integer.MAX_VALUE) {
            minStep = 0;
        }
        return new TimestampSpread(count, uniqueTs, lastTs - firstTs, minStep, maxStep);
    }
}
