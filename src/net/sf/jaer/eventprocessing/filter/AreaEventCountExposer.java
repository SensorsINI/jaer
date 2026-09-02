/*
 * Copyright (C) SensorsINI / jAER.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.Font;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Reusable DVS accumulation / exposure helper used by AEPlayer, DavisAutoShooter,
 * and DvsFramer. Not intended as a user FilterChain filter; enclose it or call
 * {@link #addEvent(int, int, int)} from other code.
 * <p>
 * Same idea as PatchMatchFlow {@code SliceMethod.AreaEventNumber}: divide the
 * chip into a grid of areas and expose when any area reaches {@code eventCount}
 * (with optional min/max duration). Also supports constant duration and constant
 * total-count exposure.
 *
 * @author tobi
 * @see net.sf.jaer.graphics.AbstractAEPlayer
 * @see eu.seebetter.ini.chips.davis.DavisAutoShooter
 * @see net.sf.jaer.util.avioutput.DvsFramer
 */
@Description("Internal helper: expose a DVS frame by duration, total event count, or per-area event count. Do not add to the FilterChain; enclose or call from AEPlayer / DavisAutoShooter / DvsFramer.")
@Help("""
<html>
<body>
<h2>AreaEventCountExposer</h2>
<p>Accumulation helper (not a user FilterChain filter; enclose it).
<code>EventExposureMode</code> decides when a DVS &ldquo;frame&rdquo; is exposed:</p>
<ul>
<li><b>CountDuration</b> &mdash; accumulate for <code>durationUs</code>.</li>
<li><b>ConstantCount</b> &mdash; accumulate <code>eventCount</code> events in the whole scene.</li>
<li><b>AreaEventCount</b> &mdash; divide the chip into about <code>numAreas</code>
rectangular cells (default 32) and expose when <i>any</i> cell reaches
<code>eventCount</code> (default 1000). Optional min/max duration clamp the
slice like PatchMatchFlow AreaEventNumber.</li>
</ul>
<p>AEViewer playback uses this as a third packet-slicing mode (f/s change
<code>eventCount</code>). DavisAutoShooter and DvsFramer enclose an instance.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class AreaEventCountExposer extends EventFilter2D implements FrameAnnotater {

    public static final String EVENT_EXPOSED = "exposed";
    public static final String EVENT_EVENT_COUNT = "eventCount";
    public static final String EVENT_NUM_AREAS = "numAreas";
    public static final String EVENT_DURATION_US = "durationUs";
    public static final String EVENT_EXPOSURE_MODE = "eventExposureMode";
    /** @deprecated use {@link #EVENT_EXPOSURE_MODE} */
    @Deprecated
    public static final String EVENT_ACCUMULATION_METHOD = EVENT_EXPOSURE_MODE;

    public static final int EVENT_COUNT_DEFAULT = 1000;
    public static final int EVENT_COUNT_MIN = 1;
    public static final int EVENT_COUNT_MAX = 1_000_000;
    public static final int NUM_AREAS_DEFAULT = 32;
    public static final int DURATION_US_DEFAULT = 20_000;
    public static final int DURATION_MIN_US_DEFAULT = 0;
    public static final int DURATION_MAX_US_DEFAULT = 1_000_000;

    /**
     * How events are accumulated until a frame / slice is considered exposed.
     */
    public enum EventExposureMode {
        /** Fixed time interval ({@code durationUs}). */
        CountDuration,
        /** Fixed total event count ({@code eventCount}). */
        ConstantCount,
        /** Any spatial area reaches {@code eventCount} events. */
        AreaEventCount
    }

    private EventExposureMode eventExposureMode = parseMode(
            getString("eventExposureMode", getString("accumulationMethod", EventExposureMode.AreaEventCount.toString())));
    private int eventCount = getInt("eventCount", EVENT_COUNT_DEFAULT);
    private int numAreas = getInt("numAreas", NUM_AREAS_DEFAULT);
    private int durationUs = getInt("durationUs", DURATION_US_DEFAULT);
    private int durationMinUs = getInt("durationMinUs", DURATION_MIN_US_DEFAULT);
    private int durationMaxUs = getInt("durationMaxUs", DURATION_MAX_US_DEFAULT);
    private boolean showAreas = getBoolean("showAreas", false);
    private static final int SHOW_AREAS_DURATION_MS = 4000;
    private volatile boolean showAreasTemporarily = false;
    private Timer showAreasTimer;
    private TimerTask stopShowingAreasTask;

    private int[][] areaCounts = null;
    private int nax = 1, nay = 1;
    private int sizeX = 0, sizeY = 0;
    private int totalEvents = 0;
    private int maxAreaCount = 0;
    private int firstTimestampUs = 0;
    private int lastTimestampUs = 0;
    private boolean haveTimestamp = false;
    private boolean areaCountExceeded = false;
    private boolean exposed = false;

    public AreaEventCountExposer(AEChip chip) {
        super(chip);
        final String method = "Method", count = "Count", dur = "Duration", areas = "Areas";
        setPropertyTooltip(method, "eventExposureMode",
                "CountDuration: fixed time. ConstantCount: total events. AreaEventCount: any spatial cell reaches eventCount.");
        setPropertyTooltip(count, "eventCount",
                "Events to expose a ConstantCount slice, or events in any area for AreaEventCount (f/s scales this). Default 1000.");
        setPropertyTooltip(areas, "numAreas",
                "Target number of rectangular areas covering the chip (default 32). Actual grid is the nearest aspect-matched nax*nay.");
        setPropertyTooltip(dur, "durationUs", "Slice duration in us for CountDuration.");
        setPropertyTooltip(dur, "durationMinUs",
                "AreaEventCount: do not expose before this many us even if an area is full (0 = no minimum).");
        setPropertyTooltip(dur, "durationMaxUs",
                "AreaEventCount: expose after this many us even if no area is full (caps sparse scenes).");
        setPropertyTooltip(areas, "showAreas", "Draw the area grid and the hottest cell count.");
        setFilterEnabled(false);
    }

    private static EventExposureMode parseMode(String s) {
        try {
            return EventExposureMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return EventExposureMode.AreaEventCount;
        }
    }

    /**
     * Adds one DVS event at chip coordinates. Returns true if this event made
     * the accumulation exposed.
     */
    public synchronized boolean addEvent(int x, int y, int timestampUs) {
        if (exposed) {
            return true;
        }
        if (x < 0 || y < 0) {
            return false;
        }
        if (!haveTimestamp) {
            firstTimestampUs = timestampUs;
            haveTimestamp = true;
        }
        lastTimestampUs = timestampUs;
        totalEvents++;
        if (eventExposureMode == EventExposureMode.AreaEventCount) {
            allocateAreas();
            if (x >= sizeX || y >= sizeY) {
                return checkExposed();
            }
            int ax = (x * nax) / sizeX;
            int ay = (y * nay) / sizeY;
            if (ax < 0) {
                ax = 0;
            } else if (ax >= nax) {
                ax = nax - 1;
            }
            if (ay < 0) {
                ay = 0;
            } else if (ay >= nay) {
                ay = nay - 1;
            }
            int c = ++areaCounts[ax][ay];
            if (c > maxAreaCount) {
                maxAreaCount = c;
            }
            if (c >= eventCount) {
                areaCountExceeded = true;
            }
        }
        return checkExposed();
    }

    /**
     * Adds a cooked event, ignoring special / filtered / non-DVS samples.
     *
     * @return true if accumulation is now exposed
     */
    public boolean addEvent(BasicEvent e) {
        if (e == null || e.isSpecial() || e.isFilteredOut()) {
            return exposed;
        }
        if (e instanceof ApsDvsEvent && !((ApsDvsEvent) e).isDVSEvent()) {
            return exposed;
        }
        return addEvent(e.x, e.y, e.timestamp);
    }

    /**
     * Adds all polarity events in the packet. Does not reset if already exposed.
     *
     * @return true if exposed during or before this packet
     */
    public synchronized boolean addPacket(EventPacket<? extends BasicEvent> in) {
        if (in == null || in.isEmpty()) {
            return exposed;
        }
        for (BasicEvent e : in) {
            if (addEvent(e)) {
                return true;
            }
        }
        return exposed;
    }

    /**
     * Adds raw addresses using the chip extractor. Returns the index (inclusive)
     * of the event that exposed the slice, or {@code -1} if still accumulating.
     */
    public synchronized int addRawEvents(int[] addresses, int[] timestamps, int n, EventExtractor2D extractor) {
        if (addresses == null || timestamps == null || n <= 0 || extractor == null) {
            return exposed ? 0 : -1;
        }
        int lim = Math.min(n, Math.min(addresses.length, timestamps.length));
        for (int i = 0; i < lim; i++) {
            short x = extractor.getXFromAddress(addresses[i]);
            short y = extractor.getYFromAddress(addresses[i]);
            if (addEvent(x, y, timestamps[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Scale {@code eventCount} by {@code factor} (used by f/s). Always changes
     * the value by at least 1 when possible.
     */
    public void scaleEventCount(double factor) {
        int n = (int) Math.round(eventCount * factor);
        if (n == eventCount) {
            n = factor > 1 ? eventCount + 1 : eventCount - 1;
        }
        setEventCount(n);
    }

    /**
     * Clears counters for the next slice. Does not change method or thresholds.
     */
    public synchronized void resetAccumulation() {
        totalEvents = 0;
        maxAreaCount = 0;
        firstTimestampUs = 0;
        lastTimestampUs = 0;
        haveTimestamp = false;
        areaCountExceeded = false;
        exposed = false;
        if (areaCounts != null) {
            for (int[] row : areaCounts) {
                Arrays.fill(row, 0);
            }
        }
    }

    public synchronized boolean isExposed() {
        return exposed;
    }

    public int getTotalEvents() {
        return totalEvents;
    }

    public int getMaxAreaCount() {
        return maxAreaCount;
    }

    /** Actual columns in the allocated grid (may differ slightly from {@code numAreas}). */
    public int getAllocatedAreaColumns() {
        return nax;
    }

    /** Actual rows in the allocated grid. */
    public int getAllocatedAreaRows() {
        return nay;
    }

    public int getAllocatedAreaCount() {
        return nax * nay;
    }

    public int getDurationAccumulatedUs() {
        if (!haveTimestamp) {
            return 0;
        }
        return lastTimestampUs - firstTimestampUs;
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        addPacket(in);
        return in;
    }

    @Override
    public void resetFilter() {
        resetAccumulation();
    }

    @Override
    public void initFilter() {
        allocateAreas();
        resetAccumulation();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if ((!showAreas && !showAreasTemporarily) || chip == null) {
            return;
        }
        allocateAreas();
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        gl.glLineWidth(1f);
        gl.glColor4f(1, 1, 1, 0.35f);
        gl.glBegin(GL.GL_LINES);
        float dx = sizeX / (float) nax;
        float dy = sizeY / (float) nay;
        for (int i = 0; i <= nax; i++) {
            float x = i * dx;
            gl.glVertex2f(x, 0);
            gl.glVertex2f(x, sizeY);
        }
        for (int j = 0; j <= nay; j++) {
            float y = j * dy;
            gl.glVertex2f(0, y);
            gl.glVertex2f(sizeX, y);
        }
        gl.glEnd();
        TextRenderer tr = new TextRenderer(new Font("Monospaced", Font.PLAIN, 18));
        tr.setColor(1, 1, 1, 0.6f);
        tr.begin3DRendering();
        tr.draw3D(String.format("areas %dx%d  max %d/%d  tot %d  %s",
                nax, nay, maxAreaCount, eventCount, totalEvents, exposed ? "EXPOSED" : ""),
                2, 2, 0, 0.2f);
        tr.end3DRendering();
        gl.glPopMatrix();
    }

    @Override
    public void propertyChange(java.beans.PropertyChangeEvent evt) {
        super.propertyChange(evt);
        String n = evt.getPropertyName();
        if (Chip2D.EVENT_SIZE_SET.equals(n) || Chip2D.EVENT_SIZEX.equals(n) || Chip2D.EVENT_SIZEY.equals(n)) {
            synchronized (this) {
                areaCounts = null;
                allocateAreas();
            }
        }
    }

    public EventExposureMode getEventExposureMode() {
        return eventExposureMode;
    }

    public synchronized void setEventExposureMode(EventExposureMode eventExposureMode) {
        EventExposureMode old = this.eventExposureMode;
        this.eventExposureMode = eventExposureMode;
        putString("eventExposureMode", eventExposureMode.toString());
        resetAccumulation();
        getSupport().firePropertyChange(EVENT_EXPOSURE_MODE, old, this.eventExposureMode);
    }

    public int getEventCount() {
        return eventCount;
    }

    public synchronized void setEventCount(int eventCount) {
        int old = this.eventCount;
        if (eventCount < EVENT_COUNT_MIN) {
            eventCount = EVENT_COUNT_MIN;
        } else if (eventCount > EVENT_COUNT_MAX) {
            eventCount = EVENT_COUNT_MAX;
        }
        this.eventCount = eventCount;
        putInt("eventCount", eventCount);
        getSupport().firePropertyChange(EVENT_EVENT_COUNT, old, this.eventCount);
    }

    public int getNumAreas() {
        return numAreas;
    }

    public synchronized void setNumAreas(int numAreas) {
        int old = this.numAreas;
        if (numAreas < 1) {
            numAreas = 1;
        } else if (numAreas > 1024) {
            numAreas = 1024;
        }
        this.numAreas = numAreas;
        if (old != this.numAreas) {
            putInt("numAreas", numAreas);
            areaCounts = null;
            allocateAreas();
            getSupport().firePropertyChange(EVENT_NUM_AREAS, old, this.numAreas);
            showAreasTemporarily();
            if (chip != null && chip.getAeViewer() != null) {
                chip.getAeViewer().showActionText(String.format("Areas: %d (%dx%d)", this.numAreas, nax, nay));
            }
        }
    }

    public int getDurationUs() {
        return durationUs;
    }

    public synchronized void setDurationUs(int durationUs) {
        int old = this.durationUs;
        if (durationUs < 1) {
            durationUs = 1;
        }
        this.durationUs = durationUs;
        putInt("durationUs", durationUs);
        getSupport().firePropertyChange(EVENT_DURATION_US, old, this.durationUs);
    }

    public int getDurationMinUs() {
        return durationMinUs;
    }

    public void setDurationMinUs(int durationMinUs) {
        if (durationMinUs < 0) {
            durationMinUs = 0;
        }
        this.durationMinUs = durationMinUs;
        putInt("durationMinUs", durationMinUs);
    }

    public int getDurationMaxUs() {
        return durationMaxUs;
    }

    public void setDurationMaxUs(int durationMaxUs) {
        if (durationMaxUs < 1) {
            durationMaxUs = 1;
        }
        this.durationMaxUs = durationMaxUs;
        putInt("durationMaxUs", durationMaxUs);
    }

    public boolean isShowAreas() {
        return showAreas;
    }

    public void setShowAreas(boolean showAreas) {
        this.showAreas = showAreas;
        putBoolean("showAreas", showAreas);
        if (showAreas) {
            registerDisplayAnnotator(true);
        } else if (!showAreasTemporarily) {
            registerDisplayAnnotator(false);
        }
    }

    /**
     * Briefly draw the area grid (same idea as PatchMatchFlow AreaEventNumber).
     */
    public void showAreasTemporarily() {
        if (stopShowingAreasTask != null) {
            stopShowingAreasTask.cancel();
        }
        if (showAreasTimer == null) {
            showAreasTimer = new Timer("AreaEventCountExposer-areas", true);
        }
        stopShowingAreasTask = new TimerTask() {
            @Override
            public void run() {
                showAreasTemporarily = false;
                if (!AreaEventCountExposer.this.showAreas) {
                    registerDisplayAnnotator(false);
                }
            }
        };
        showAreasTemporarily = true;
        registerDisplayAnnotator(true);
        showAreasTimer.schedule(stopShowingAreasTask, SHOW_AREAS_DURATION_MS);
    }

    /**
     * Player-owned exposer is not on the FilterChain; register it on the current
     * DisplayMethod so the temporary grid is drawn. Enclosed instances already
     * annotate via ChipCanvas.
     */
    private void registerDisplayAnnotator(boolean add) {
        if (isEnclosed() || chip == null || chip.getCanvas() == null) {
            return;
        }
        DisplayMethod dm = chip.getCanvas().getDisplayMethod();
        if (dm == null) {
            return;
        }
        if (add) {
            if (!dm.getAnnotators().contains(this)) {
                dm.addAnnotator(this);
            }
        } else {
            dm.removeAnnotator(this);
        }
    }

    private void allocateAreas() {
        int sx = (chip != null && chip.getSizeX() > 0) ? chip.getSizeX() : 1;
        int sy = (chip != null && chip.getSizeY() > 0) ? chip.getSizeY() : 1;
        if (areaCounts != null && sizeX == sx && sizeY == sy && nax * nay > 0) {
            return;
        }
        sizeX = sx;
        sizeY = sy;
        int target = Math.max(1, numAreas);
        double aspect = (double) sx / (double) sy;
        int ny = Math.max(1, (int) Math.round(Math.sqrt(target / aspect)));
        int nx = Math.max(1, (int) Math.round((double) target / ny));
        if (nx < 1) {
            nx = 1;
        }
        nax = nx;
        nay = ny;
        areaCounts = new int[nax][nay];
    }

    /**
     * CountDuration: dt &gt;= durationUs. ConstantCount: totalEvents &gt;=
     * eventCount. AreaEventCount: (any area full OR dt &gt;= max) AND dt &gt;= min.
     */
    private boolean checkExposed() {
        if (exposed) {
            return true;
        }
        int dt = haveTimestamp ? lastTimestampUs - firstTimestampUs : 0;
        if (dt < 0) {
            // timestamp wrap or rewind: force a new slice
            markExposed();
            return true;
        }
        switch (eventExposureMode) {
            case CountDuration:
                if (dt >= durationUs) {
                    markExposed();
                }
                break;
            case ConstantCount:
                if (totalEvents >= eventCount) {
                    markExposed();
                }
                break;
            case AreaEventCount:
                boolean longEnough = dt >= durationMinUs;
                boolean areaOrTimeout = areaCountExceeded || dt >= durationMaxUs;
                if (longEnough && areaOrTimeout) {
                    markExposed();
                }
                break;
            default:
                break;
        }
        return exposed;
    }

    private void markExposed() {
        if (exposed) {
            return;
        }
        exposed = true;
        getSupport().firePropertyChange(EVENT_EXPOSED, false, true);
    }
}
