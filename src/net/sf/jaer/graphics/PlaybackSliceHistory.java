/*
 * Copyright (C) SensorsINI / jAER.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.graphics;

import java.util.ArrayList;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Session-long index of forward playback slices for jog-back.
 * <p>
 * Does not store view packets (those would cap history at a few hundred slices).
 * Each bookmark is stream position + AreaEventCount leftover after that slice.
 * Jog-back moves the cursor and restores the state <em>before</em> the target
 * slice; the player re-reads that one slice forward. Reverse
 * {@code AreaEventCount} file reads cannot reconstruct view slices
 * ({@code dt < 0} cuts almost immediately).
 */
public final class PlaybackSliceHistory {

    /**
     * Stream/leftover state after a slice (or the session origin before slice 0).
     */
    public static final class Bookmark {
        public final AEPacketRaw leftover;
        public final long positionAfter;
        public final int currentStartTimestamp;

        Bookmark(AEPacketRaw leftover, long positionAfter, int currentStartTimestamp) {
            this.leftover = leftover;
            this.positionAfter = positionAfter;
            this.currentStartTimestamp = currentStartTimestamp;
        }
    }

    /** Outcome of {@link PlaybackSliceHistory#rewind(int)}. */
    public static final class RewindResult {
        /** Restore this, then read one forward slice to display the target. */
        public final Bookmark stateBefore;
        public final int stepsTaken;
        public final boolean atOldest;
        public final int cursor;

        RewindResult(Bookmark stateBefore, int stepsTaken, boolean atOldest, int cursor) {
            this.stateBefore = stateBefore;
            this.stepsTaken = stepsTaken;
            this.atOldest = atOldest;
            this.cursor = cursor;
        }
    }

    private final ArrayList<Bookmark> bookmarks = new ArrayList<>();
    private int cursor = -1;
    private boolean originSet;
    private Bookmark origin = new Bookmark(null, 0L, 0);

    public synchronized void setJogPacketCount(int jogPacketCount) {
        // Capacity is the session, not jog N. Kept so AEPlayer prefs wiring stays valid.
    }

    public synchronized int size() {
        return bookmarks.size();
    }

    public synchronized int cursor() {
        return cursor;
    }

    public synchronized boolean hasOrigin() {
        return originSet;
    }

    public synchronized long originPosition() {
        return origin.positionAfter;
    }

    /**
     * Start a new history at the current playhead (file open, rewind, seek,
     * accumulation-method change).
     */
    public synchronized void resetOrigin(long position, int currentStartTimestamp, AEPacketRaw leftover) {
        bookmarks.clear();
        cursor = -1;
        originSet = true;
        origin = new Bookmark(copyLeftover(leftover), position, currentStartTimestamp);
    }

    public synchronized void clear() {
        bookmarks.clear();
        cursor = -1;
        originSet = false;
        origin = new Bookmark(null, 0L, 0);
    }

    /**
     * Record state after a newly displayed forward slice. Drops any bookmarks
     * after the cursor (play-forward after jog-back).
     */
    public synchronized void push(AEPacketRaw leftover, long positionAfter, int currentStartTimestamp) {
        if (!originSet) {
            originSet = true;
            origin = new Bookmark(null, 0L, currentStartTimestamp);
        }
        while (bookmarks.size() > cursor + 1) {
            bookmarks.remove(bookmarks.size() - 1);
        }
        bookmarks.add(new Bookmark(copyLeftover(leftover), positionAfter, currentStartTimestamp));
        cursor = bookmarks.size() - 1;
    }

    /**
     * Move the cursor back {@code steps} slices (not past slice 0). Caller
     * restores {@link RewindResult#stateBefore} and re-reads one forward slice.
     */
    public synchronized RewindResult rewind(int steps) {
        if (bookmarks.isEmpty() || cursor < 0) {
            return new RewindResult(null, 0, true, cursor);
        }
        int take = steps < 0 ? 0 : steps;
        int target = cursor - take;
        if (target < 0) {
            target = 0;
        }
        int taken = cursor - target;
        cursor = target;
        return new RewindResult(stateBefore(target), taken, target == 0, cursor);
    }

    public synchronized Bookmark peekNewest() {
        if (cursor < 0 || cursor >= bookmarks.size()) {
            return null;
        }
        return bookmarks.get(cursor);
    }

    private Bookmark stateBefore(int sliceIndex) {
        if (sliceIndex <= 0) {
            return origin;
        }
        return bookmarks.get(sliceIndex - 1);
    }

    static AEPacketRaw copyLeftover(AEPacketRaw src) {
        if (src == null || src.getNumEvents() <= 0) {
            return null;
        }
        return copyPacket(src);
    }

    /**
     * Independent copy, or {@code null} if {@code src} is null. Empty source
     * becomes an empty packet (not null).
     */
    public static AEPacketRaw copyPacket(AEPacketRaw src) {
        if (src == null) {
            return null;
        }
        int n = src.getNumEvents();
        int[] addr = src.getAddresses();
        int[] ts = src.getTimestamps();
        if (n <= 0 || addr == null || ts == null) {
            return new AEPacketRaw(0);
        }
        int len = Math.min(n, Math.min(addr.length, ts.length));
        AEPacketRaw dest = new AEPacketRaw(len);
        System.arraycopy(addr, 0, dest.getAddresses(), 0, len);
        System.arraycopy(ts, 0, dest.getTimestamps(), 0, len);
        dest.setNumEvents(len);
        return dest;
    }
}
