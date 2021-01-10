/*
 * Copyright (C) 2019 tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.sf.jaer.eventprocessing.filter;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Pattern;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.TobiLogger;

/**
 * Superclass for all noise filters
 *
 * @author tobi
 */
public abstract class AbstractNoiseFilter extends EventFilter2D implements FrameAnnotater, RemoteControlled {

    protected boolean showFilteringStatistics = getBoolean("showFilteringStatistics", true);
    protected int totalEventCount = 0;
    protected int filteredOutEventCount = 0;
    /**
     * list of filtered out events
     */
    private ArrayList<BasicEvent> filteredOutEvents = new ArrayList();
    protected EngineeringFormat eng = new EngineeringFormat();

    /**
     * Used by some filters that implement this option
     */
    protected boolean filterHotPixels = getBoolean("filterHotPixels", true);
    protected boolean recordFilteredOutEvents = false;
    /**
     * Map from noise filters to drawing positions of noise filtering statistics
     * annotations
     */
    static protected HashMap<AbstractNoiseFilter, Integer> noiseStatDrawingMap = new HashMap<AbstractNoiseFilter, Integer>();
    protected int statisticsDrawingPosition = -10; // y coordinate we write ourselves to, start with -10 so we end up at 0 for first one (hack)
    final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;
    protected int MAX_DT_US = 200000;
    protected int MIN_DT_US = 10;

    /**
     * Correlation time in seconds
     */
    protected float correlationTimeS = getFloat("correlationTimeS", 25e-3f);

    /**
     * the amount to subsample x and y event location by in bit shifts when
     * writing to past event times map. This effectively increases the range of
     * support. E.g. setting subSamplingShift to 1 quadruples range because both
     * x and y are shifted right by one bit
     */
    protected int subsampleBy = getInt("subsampleBy", 0);

    /**
     * the time in timestamp ticks (1us at present) that a spike needs to be
     * supported by a prior event in the neighborhood by to pass through
     */
    protected boolean letFirstEventThrough = getBoolean("letFirstEventThrough", true);

    /**
     * Automatic control of filter correlation time
     */
    private NoiseFilterControl noiseFilterControl = null;

    protected final String TT_FILT_CONTROL = "1. Denoising control", TT_DISP = "2. Display", TT_ADAP = "3. Adaptive Filtering";

    public AbstractNoiseFilter(AEChip chip) {
        super(chip);
        noiseFilterControl = new NoiseFilterControl(chip);
        FilterChain enclosedFilterChain = new FilterChain(chip);
        enclosedFilterChain.add(noiseFilterControl);
        setEnclosedFilterChain(enclosedFilterChain);

        setPropertyTooltip(TT_DISP, "showFilteringStatistics", "Annotates screen with percentage of filtered out events, if filter implements this count");
        setPropertyTooltip(TT_FILT_CONTROL, "correlationTimeS", "Correlation time for noise filters that use this parameter");
        setPropertyTooltip(TT_FILT_CONTROL, "filterHotPixels", "Filter out hot pixels by not considering correlation with ourselves (i.e. self-exclusion of correlation).");
        setPropertyTooltip(TT_FILT_CONTROL, "subsampleBy", "Past events are spatially subsampled (address right shifted) by this many bits");
        setPropertyTooltip(TT_ADAP, "adaptiveFilteringEnabled", "Controls whether filter correlation time is automatically adapted.");
        setPropertyTooltip(TT_FILT_CONTROL, "letFirstEventThrough", "After reset, let's first event through; if false, first event from each pixel is blocked");
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);

    }

    /**
     * Use to filter out events, updates the list of such events
     *
     * @param e
     */
    protected void filterOut(BasicEvent e) {
        e.setFilteredOut(true);
        filteredOutEventCount++;
        if (recordFilteredOutEvents) {
            getFilteredOutEvents().add(e);
        }
    }

    /**
     * Subclasses should call this before filtering to clear the
     * filteredOutEventCount and filteredOutEvents
     *
     * @param in
     * @return
     */
    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<?> in) {
        getFilteredOutEvents().clear();
        filteredOutEventCount = 0;
        totalEventCount = 0;
        in = getEnclosedFilterChain().filterPacket(in);
        return in;
    }

    @Override
    public void resetFilter() {
        getNoiseFilterControl().resetFilter();
    }

    /**
     * @return the showFilteringStatistics
     */
    public boolean isShowFilteringStatistics() {
        return showFilteringStatistics;
    }

    /**
     * @param showFilteringStatistics the showFilteringStatistics to set
     */
    public void setShowFilteringStatistics(boolean showFilteringStatistics) {
        this.showFilteringStatistics = showFilteringStatistics;
        putBoolean("showFilteringStatistics", showFilteringStatistics);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!showFilteringStatistics) {
            return;
        }
        findUnusedDawingY();
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(.2f, .2f, .8f); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, statisticsDrawingPosition, 0);
        final float filteredOutPercent = 100 * (float) filteredOutEventCount / totalEventCount;
        String s = null;
        s = String.format("%s: filtered out %%%6.1f",
                infoString(),
                filteredOutPercent);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
        gl.glPopMatrix();
    }

    protected void findUnusedDawingY() {
        // find a y posiiton not used yet for us
        if (noiseStatDrawingMap.get(this) == null) {
            for (int y : noiseStatDrawingMap.values()) {
                if (y > statisticsDrawingPosition) {
                    statisticsDrawingPosition = y;
                }
            }
            statisticsDrawingPosition = statisticsDrawingPosition + 20; // room for 2 lines per filter
            noiseStatDrawingMap.put(this, statisticsDrawingPosition);
        }
    }

    private String USAGE = "Need at least 2 arguments: noisefilter <command> <args>\nCommands are: specific to the filter\n";

    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s", 3);
        if (tok.length < 2) {
            return USAGE;
        }

        log.info("Received Command:" + input);
        return USAGE;
    }

    public String setParameters(RemoteControlCommand command, String input) {
        String[] tok = input.split("\\s", 3);
        if (tok.length < 2) {
            return USAGE;
        }

        return USAGE;
    }

    /**
     * Returns lastTimesMap if there is one, or null if filter does not use it
     */
    public abstract int[][] getLastTimesMap();

    /**
     * Sets the noise filter correlation time. Empty method that can be
     * overridden
     *
     * @param dtS time in seconds
     */
    public void setCorrelationTimeS(float dtS) {
        float old = this.correlationTimeS;
        this.correlationTimeS = dtS;
        putFloat("correlationTimeS", correlationTimeS);
        getSupport().firePropertyChange("correlationTimeS", old, this.correlationTimeS);

    }

    public float getCorrelationTimeS() {
        return this.correlationTimeS;
    }

    public float getMinCorrelationTimeS() {
        return MIN_DT_US * 1e-6f;
    }

    public float getMaxCorrelationTimeS() {
        return MAX_DT_US * 1e-6f;
    }

    public int getSubsampleBy() {
        return subsampleBy;
    }

    /**
     * Sets the number of bits to subsample by when storing events into the map
     * of past events. Increasing this value will increase the number of events
     * that pass through and will also allow passing events from small sources
     * that do not stimulate every pixel.
     *
     * @param subsampleBy the number of bits, 0 means no subsampling, 1 means
     * cut event time map resolution by a factor of two in x and in y
     */
    synchronized public void setSubsampleBy(int subsampleBy) {
        if (subsampleBy < 0) {
            subsampleBy = 0;
        } else if (subsampleBy > 4) {
            subsampleBy = 4;
        }
        this.subsampleBy = subsampleBy;
        putInt("subsampleBy", subsampleBy);
        initFilter();
    }

    /**
     * @return the letFirstEventThrough
     */
    public boolean isLetFirstEventThrough() {
        return letFirstEventThrough;
    }

    /**
     * @param letFirstEventThrough the letFirstEventThrough to set
     */
    public void setLetFirstEventThrough(boolean letFirstEventThrough) {
        this.letFirstEventThrough = letFirstEventThrough;
        putBoolean("letFirstEventThrough", letFirstEventThrough);
    }

    /**
     * @return the filteredOutEvents
     */
    public ArrayList<BasicEvent> getFilteredOutEvents() {
        return filteredOutEvents;
    }

    /**
     * NoiseTesterFilter sets this boolean true to record filtered out events to
     * the filteredOutEvents ArrayList. Set false by default to save time and
     * memory.
     *
     * @param recordFilteredOutEvents the recordFilteredOutEvents to set
     */
    public void setRecordFilteredOutEvents(boolean recordFilteredOutEvents) {
        this.recordFilteredOutEvents = recordFilteredOutEvents;
        getFilteredOutEvents().clear(); // make sure to clear the list
    }

    /**
     * @return the filterHotPixels
     */
    public boolean isFilterHotPixels() {
        return filterHotPixels;
    }

    /**
     * @param filterHotPixels the filterHotPixels to set
     */
    public void setFilterHotPixels(boolean filterHotPixels) {
        this.filterHotPixels = filterHotPixels;
        putBoolean("filterHotPixels", filterHotPixels);
    }

    /**
     * Returns short info string with key control parameters and short name.
     * Subclasses should override.
     *
     * @return the info string
     */
    public String infoString() {
        String s = getClass().getSimpleName();
        s = s.replaceAll("[a-z]", "");
        s = s + ": dT=" + eng.format(getCorrelationTimeS()) + "s";
        return s;
    }

    public boolean isAdaptiveFilteringEnabled() {
        return noiseFilterControl.isAdaptiveFilteringEnabled();
    }

    public void setAdaptiveFilteringEnabled(boolean adaptiveFilteringEnabled) {
        noiseFilterControl.setAdaptiveFilteringEnabled(adaptiveFilteringEnabled);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); //To change body of generated methods, choose Tools | Templates.
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
            resetFilter();
        } else if (evt.getPropertyName() == AEViewer.EVENT_CHIP) {
            resetFilter();
        }

    }

    /**
     * Implements the AbstractNoiseFilter control of the correlation time
     * control of BA denoising entropy reduction
     *
     * @author tobid, Jan 2020
     */
    public class NoiseFilterControl extends EventFilter2D {

        private int activityBinDimBits = getInt("activityBinDimBits", 4);
        private int[][] activityHistInput, activityHistFiltered;
        private int binDim, nBinsX, nBinsY, nBinsTotal;
        private float entropyInput = 0, entropyFiltered = 0;
        private float entropyReduction;
        private boolean adaptiveFilteringEnabled = getBoolean("adaptiveFilteringEnabled", false);
        private float entropyReductionHighLimit = getFloat("entropyReductionHighLimit", 1f);
        private float entropyReductionLowLimit = getFloat("entropyReductionLowLimit", .5f);
        private float dtChangeFraction = getFloat("dtChangeFraction", 0.01f);
        private TobiLogger tobiLogger = null;
        private final float LOG2_FACTOR = (float) (1 / Math.log(2));
        private float controlIntervalS = getFloat("controlIntervalS", 0.1f);
        private int lastControlActionTimestamp = Integer.MIN_VALUE, nextControlActionTimestep = lastControlActionTimestamp + (int) (1e6f * controlIntervalS);

        public NoiseFilterControl(AEChip chip) {
            super(chip);
            setPropertyTooltip(TT_ADAP, "adaptiveFilteringEnabled", "enables adaptive control of dt to achieve a target entropyReduction between two limits");
            setPropertyTooltip(TT_ADAP, "entropyReductionLowLimit", "if entropy reduction from filtering is below this limit, decrease dt");
            setPropertyTooltip(TT_ADAP, "entropyReductionHighLimit", "if entropy reduction from filtering is above this limit, increase dt");
            setPropertyTooltip(TT_ADAP, "dtChangeFraction", "fraction by which dt is increased/decreased per packet if entropyReduction is too low/high");
            setPropertyTooltip(TT_ADAP, "dtChangeFraction", "fraction by which dt is increased/decreased per packet if entropyReduction is too low/high");
            setPropertyTooltip(TT_ADAP, "controlIntervalS", "minimum time interval in seconds between control actions (min for efficiency; control is only performed at most once per event packet)");
            setPropertyTooltip(TT_DISP, "showFilteringStatistics", "annotate display with statistics");
            setPropertyTooltip(TT_ADAP, "activityBinDimBits", "2^this is the size of rectangular blocks that histogram event activity for measuring entropy (structure) to evaluate effectiveness of filtering");
            setPropertyTooltip(TT_DISP, "LogControl", "write CSV with control data");
        }

        /**
         * Should call this on each event before denoising.
         *
         * @param e
         */
        private void processInputEvent(BasicEvent e) {
            if (!adaptiveFilteringEnabled) {
                return;
            }
            activityHistInput[e.x >> activityBinDimBits][e.y >> activityBinDimBits]++;
        }

        /**
         * Call on each event after denoising
         *
         * @param e
         */
        private void processOutputEvent(BasicEvent e) {
            if (!adaptiveFilteringEnabled) {
                return;
            }
            activityHistFiltered[e.x >> activityBinDimBits][e.y >> activityBinDimBits]++;
        }

        /**
         * Will automatically be called BEFORE the noise filter processes the
         * packet
         *
         * @param in
         * @return same packet
         */
        @Override
        public EventPacket<?> filterPacket(EventPacket<?> in) {
            if (in == null || in.isEmpty()) {
                return in;
            }
            if (!adaptiveFilteringEnabled) {
                return in;
            }
            for (BasicEvent e : in) {
                processInputEvent(e);
            }

            float lastTau = getCorrelationTimeS();
            resetActivityHistograms();

            return in;
        }

        protected void performControl(EventPacket<? extends BasicEvent> in) {
            if (!adaptiveFilteringEnabled) {
                return;
            }
            for (BasicEvent e : in) {
                processOutputEvent(e);
            }
            // compute entropies of activities in input and filtered activity histograms
            int sumInput = 0;
            for (int[] i : activityHistInput) {
                for (int ii : i) {
                    sumInput += ii;
                }
            }
            int sumFiltered = 0;
            for (int[] i : activityHistFiltered) {
                for (int ii : i) {
                    sumFiltered += ii;
                }
            }
            entropyInput = 0;
            if (sumInput > 0) {
                for (int[] i : activityHistInput) {
                    for (int ii : i) {
                        final float p = (float) ii / sumInput;
                        if (p > 0) {
                            entropyInput += p * (float) Math.log(p);
                        }
                    }
                }
            }
            entropyFiltered = 0;
            if (sumFiltered > 0) {
                for (int[] i : activityHistFiltered) {
                    for (int ii : i) {
                        final float p = (float) ii / sumFiltered;
                        if (p > 0) {
                            entropyFiltered += p * (float) Math.log(p);
                        }
                    }
                }
            }
            entropyFiltered = -LOG2_FACTOR * entropyFiltered;
            entropyInput = -LOG2_FACTOR * entropyInput;
            entropyReduction = entropyInput - entropyFiltered;
            float olddt = getCorrelationTimeS();
            if (entropyReduction > entropyReductionHighLimit) {
                float newdt = (olddt * (1 + dtChangeFraction));
                setCorrelationTimeS(newdt); // increase dt to force less correlation

            } else if (entropyReduction < entropyReductionLowLimit) {
                float newdt = (olddt * (1 - dtChangeFraction));
                setCorrelationTimeS(newdt); // decrease dt to force more correlation

            }
            if (tobiLogger != null && tobiLogger.isEnabled()) { // record control action for paper experiments
                float syntheticNoiseRateHzPerPixel = Float.NaN;
                if (getEnclosingFilter() != null && getEnclosingFilter() instanceof NoiseTesterFilter) {
                    NoiseTesterFilter ntf = (NoiseTesterFilter) getEnclosingFilter();
                    syntheticNoiseRateHzPerPixel = ntf.getLeakNoiseRateHz() + ntf.getShotNoiseRateHz();
                }
                String s = String.format("%d,%f,%f,%f,%f,%f,%f,%g,%g", in.getLastTimestamp(), syntheticNoiseRateHzPerPixel, entropyReductionLowLimit, entropyReductionHighLimit, entropyInput, entropyFiltered, entropyReduction, olddt, getCorrelationTimeS());
                tobiLogger.log(s);
            }
        }

        public void annotate(GLAutoDrawable drawable) {
            if (!showFilteringStatistics) {
                return;
            }
            findUnusedDawingY();
            GL2 gl = drawable.getGL().getGL2();
            gl.glPushMatrix();
            final GLUT glut = new GLUT();
            gl.glColor3f(.2f, .2f, .8f); // must set color before raster position (raster position is like glVertex)
            gl.glRasterPos3f(0, statisticsDrawingPosition, 0);
            final float filteredOutPercent = 100 * (float) filteredOutEventCount / totalEventCount;
            String s = null;
            if (adaptiveFilteringEnabled) {
                s = String.format("%s: dt=%.1fms filOut=%%%.1f entropy bef/aft/reduc=%.1f/%.1f/%.1f",
                        infoString(), getCorrelationTimeS() * 1e-3f, filteredOutPercent, entropyInput, entropyFiltered, entropyReduction);
            } else {
                s = String.format("%s: filtered out %%%6.1f",
                        infoString(),
                        filteredOutPercent);
            }
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
            gl.glPopMatrix();
        }

        private void resetActivityHistograms() {
            if (!adaptiveFilteringEnabled) {
                return;
            }
            for (int[] i : activityHistInput) {
                Arrays.fill(i, 0);
            }
            for (int[] i : activityHistFiltered) {
                Arrays.fill(i, 0);
            }
        }

        /**
         * @return the activityBinDimBits
         */
        public int getActivityBinDimBits() {
            return activityBinDimBits;
        }

        /**
         * @param activityBinDimBits the activityBinDimBits to set
         */
        synchronized public void setActivityBinDimBits(int activityBinDimBits) {
            int old = this.activityBinDimBits;
            this.activityBinDimBits = activityBinDimBits;
            if (old != activityBinDimBits) {
                initFilter();
            }
            putInt("activityBinDimBits", activityBinDimBits);
        }

        /**
         * @return the adaptiveFilteringEnabled
         */
        public boolean isAdaptiveFilteringEnabled() {
            return adaptiveFilteringEnabled;
        }

        /**
         * @param adaptiveFilteringEnabled the adaptiveFilteringEnabled to set
         */
        public void setAdaptiveFilteringEnabled(boolean adaptiveFilteringEnabled) {
            this.adaptiveFilteringEnabled = adaptiveFilteringEnabled;
            putBoolean("adaptiveFilteringEnabled", adaptiveFilteringEnabled);
        }

        /**
         * @return the entropyReductionHighLimit
         */
        public float getEntropyReductionHighLimit() {
            return entropyReductionHighLimit;
        }

        /**
         * @param entropyReductionHighLimit the entropyReductionHighLimit to set
         */
        public void setEntropyReductionHighLimit(float entropyReductionHighLimit) {
            if (entropyReductionHighLimit < entropyReductionLowLimit) {
                entropyReductionHighLimit = entropyReductionLowLimit;
            }
            this.entropyReductionHighLimit = entropyReductionHighLimit;
            putFloat("entropyReductionHighLimit", entropyReductionHighLimit);
        }

        /**
         * @return the entropyReductionLowLimit
         */
        public float getEntropyReductionLowLimit() {
            return entropyReductionLowLimit;
        }

        /**
         * @param entropyReductionLowLimit the entropyReductionLowLimit to set
         */
        public void setEntropyReductionLowLimit(float entropyReductionLowLimit) {
            if (entropyReductionLowLimit > entropyReductionHighLimit) {
                entropyReductionLowLimit = entropyReductionHighLimit;
            }
            this.entropyReductionLowLimit = entropyReductionLowLimit;
            putFloat("entropyReductionLowLimit", entropyReductionLowLimit);
        }

        /**
         * @return the dtChangeFraction
         */
        public float getDtChangeFraction() {
            return dtChangeFraction;
        }

        /**
         * @param dtChangeFraction the dtChangeFraction to set
         */
        public void setDtChangeFraction(float dtChangeFraction) {
            this.dtChangeFraction = dtChangeFraction;
            putFloat("dtChangeFraction", dtChangeFraction);
        }

        @Override
        public void resetFilter() {
            lastControlActionTimestamp = Integer.MIN_VALUE;
            nextControlActionTimestep = lastControlActionTimestamp + (int) (1e6f * controlIntervalS);
        }

        @Override
        public void initFilter() {
            binDim = 1 << activityBinDimBits;
            nBinsX = chip.getSizeX() / binDim;
            nBinsY = chip.getSizeY() / binDim;
            nBinsTotal = nBinsX * nBinsY;
            activityHistInput = new int[nBinsX + 1][nBinsY + 1];
            activityHistFiltered = new int[nBinsX + 1][nBinsY + 1];
        }

        /**
         * @return the controlIntervalS
         */
        public float getControlIntervalS() {
            return controlIntervalS;
        }

        /**
         * @param controlIntervalS the controlIntervalS to set
         */
        public void setControlIntervalS(float controlIntervalS) {
            this.controlIntervalS = controlIntervalS;
            putFloat("controlIntervalS", controlIntervalS);
            nextControlActionTimestep = lastControlActionTimestamp + (int) (1e6f * controlIntervalS);
        }

        // to record control actions for adaptive dT
        public void doToggleOnLogControl() {
            if (tobiLogger != null) {
                tobiLogger.setEnabled(false);
                tobiLogger.setEnabled(true);
            } else {
                tobiLogger = new TobiLogger("NoiseFilterControl-log.csv", "NoiseFilterControl control logging");
                tobiLogger.setColumnHeaderLine("timeMs,lastTimestamp,syntheticNoiseRateHzPerPixel,entropyReductionLowLimit,entropyReductionHighLimit,entropyInput,entropyFiltered,entropyReduction,newdt");
                tobiLogger.setEnabled(true);
            }
        }

        public void doToggleOffLogControl() {
            if (tobiLogger != null) {
                tobiLogger.setEnabled(false);
            }
        }
    }

    /**
     * @return the noiseFilterControl
     */
    public NoiseFilterControl getNoiseFilterControl() {
        return noiseFilterControl;
    }

    /**
     * @param noiseFilterControl the noiseFilterControl to set
     */
    public void setNoiseFilterControl(NoiseFilterControl noiseFilterControl) {
        this.noiseFilterControl = noiseFilterControl;
    }

}
