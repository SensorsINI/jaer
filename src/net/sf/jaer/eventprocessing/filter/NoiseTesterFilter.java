/*
 * Copyright (C) 2020 tobid.
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

import com.google.common.collect.EvictingQueue;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import net.sf.jaer.Preferred;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventio.AEFileInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.graphics.ChipDataFilePreview;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.util.DATFileFilter;
import net.sf.jaer.util.DrawGL;
import org.apache.commons.math3.stat.descriptive.MultivariateSummaryStatistics;
import org.jdesktop.el.MethodNotFoundException;

/**
 * Filter for testing noise filters
 *
 * @author Tobi Delbruck, Shasha Guo, Oct-Jan 2020-2025
 */
@Description("Tests background BA denoising filters by injecting known noise and measuring how much signal and noise is filtered")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class NoiseTesterFilter extends AbstractNoiseFilter implements FrameAnnotater, RemoteControlled {

    /**
     * ******
     * Defines the noise filters statically. This array is used in initFilter()
     * to construct the filters
     *
     * @see #initFilter()
     */
    public static Class[] noiseFilterClasses = {null, BackgroundActivityFilter.class, SpatioTemporalCorrelationFilter.class, QuantizedSTCF.class, AgePolarityDenoiser.class, DoubleWindowFilter.class, MLPNoiseFilter.class};
    private AbstractNoiseFilter[] noiseFilters = null; // array of denoiseer instances, starting with null
//    private HashMap<AbstractNoiseFilter, Integer> noiseFilter2ColorMap = new HashMap(); // for rendering, holds int AWT color
    private AbstractNoiseFilter selectedNoiseFilter = null;

    public static final int MAX_NUM_RECORDED_EVENTS = 10_0000_0000;
    public static final float MAX_TOTAL_NOISE_RATE_HZ = 50e6f;
    public static final float RATE_LIMIT_HZ = 25; //per pixel, separately for leak and shot rates

    private FilterChain chain;

    private boolean disableAddingNoise = getBoolean("disableAddingNoise", false);
    @Preferred
    private float shotNoiseRateHz = getFloat("shotNoiseRateHz", .1f);
    protected boolean photoreceptorNoiseSimulation = getBoolean("photoreceptorNoiseSimulation", true);
    @Preferred
    private float leakNoiseRateHz = getFloat("leakNoiseRateHz", .1f);
    @Preferred
    private float noiseRateCoVDecades = getFloat("noiseRateCoVDecades", 0);
    private float leakJitterFraction = getFloat("leakJitterFraction", 0.1f); // fraction of interval to jitter leak events
    private float[] noiseRateArray = null;
    private float[] noiseRateIntervals = null; // stored by column, with y changing fastest
    private PriorityQueue<PolarityEvent> leakNoiseQueue = null; // stored by column, with y changing fastest

    private double poissonDtUs = 1;

    private float shotOffThresholdProb; // bounds for samppling Poisson noise, factor 0.5 so total rate is shotNoiseRateHz
    private float shotOnThresholdProb; // for shot noise sample both sides, for leak events just generate ON events
    private float leakOnThresholdProb; // bounds for samppling Poisson noise

    private PrerecordedNoise prerecordedNoise = null;

    private ROCHistory rocHistoryCurrent = null;
    private ArrayList<ROCHistory> rocHistoriesSaved = new ArrayList();
    private int rocHistoryLabelPosY = 0;
    private ROCSweep rocSweep;

    int[] colors = new int[6];
    int lastcolor = 0;

    private static String DEFAULT_CSV_FILENAME_BASE = "NoiseTesterFilter";
    private String csvFileName = getString("csvFileName", DEFAULT_CSV_FILENAME_BASE);
    private File csvFile = null;
    private BufferedWriter csvWriter = null;
    private int csvNumEventsWritten = 0, csvSignalCount = 0, csvNoiseCount = 0;
    private int[][] timestampImage = null; // image of last event timestamps
    private int[][] lastPolMap;
    private float[][] photoreceptorNoiseArray; // see https://github.com/SensorsINI/v2e/blob/565f6991daabbe0ad79d68b50d084d5dc82d6426/v2ecore/emulator_utils.py#L177

    /**
     * Chip dimensions in pixels MINUS ONE, set in initFilter()
     */
    private int sx = 0, sy = 0;

    private Integer lastTimestampPreviousPacket = null, firstSignalTimestmap = null; // use Integer Object so it can be null to signify no value yet
    private float TPR = 0;
    private float TPO = 0;
    private float TNR = 0;
    private float accuracy = 0;
    private float BR = 0;
    float inSignalRateHz = 0, inNoiseRateHz = 0, outSignalRateHz = 0, outNoiseRateHz = 0;

//    private EventPacket<ApsDvsEvent> signalAndNoisePacket = null;
    private final Random random = new Random();
    private EventPacket<PolarityEvent> signalAndNoisePacket = null;
    protected boolean resetCalled = true; // flag to reset on next event

//    private float annotateAlpha = getFloat("annotateAlpha", 0.5f);
    private DavisRenderer renderer = null;
    private boolean overlayPositives = getBoolean("overlayPositives", false);
    private boolean overlayNegatives = getBoolean("overlayNegatives", false);
    private boolean overlayTP = getBoolean("overlayTP", false);
    private boolean overlayTN = getBoolean("overlayTN", false);
    @Preferred
    private boolean overlayFP = getBoolean("overlayFP", false);
    @Preferred
    private boolean overlayFN = getBoolean("overlayFN", false);
    final float[] NOISE_COLOR = {1f, 0, 0, 1}, SIG_COLOR = {0, 1f, 0, 1};
    final int LABEL_OFFSET_PIX = 1; // how many pixels LABEL_OFFSET_PIX is the annnotation overlay, so we can see original signal/noise event and its label

    private boolean outputTrainingData = getBoolean("outputTrainingData", false);
    private boolean recordPureNoise = false;
    private boolean outputFilterStatistic = false;

    @Preferred
    private int rocHistoryLength = getInt("rocHistoryLength", 1);
    private final int LIST_LENGTH = 10000;

    private ArrayList<FilteredEventWithNNb> tpList = new ArrayList(LIST_LENGTH),
            fnList = new ArrayList(LIST_LENGTH),
            fpList = new ArrayList(LIST_LENGTH),
            tnList = new ArrayList(LIST_LENGTH); // output of classification
    private ArrayList<PolarityEvent> noiseList = new ArrayList<PolarityEvent>(LIST_LENGTH); // TODO make it lazy, when filter is enabled
    /**
     * How time is split up for Poisson sampling using bounds trick
     */
    public static final int POISSON_DIVIDER = 30;

    @Preferred
    private float correlationTimeS = getFloat("correlationTimeS", 20e-3f);
    private volatile boolean stopMe = false; // to interrupt if filterPacket takes too long
    // https://stackoverflow.com/questions/1109019/determine-if-a-java-application-is-in-debug-mode-in-eclipse
    private final boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("debug");
    ;
    private final long MAX_FILTER_PROCESSING_TIME_MS = 500000; // times out to avoid using up all heap
    private TextRenderer textRenderer = null;

    /**
     * ComboBoxModel that holds the noise filter classes
     */
    @Preferred
    private ComboBoxModel noiseFilterComboBoxModel = new DefaultComboBoxModel<Class<? extends AbstractNoiseFilter>>(noiseFilterClasses) {
        @Override
        public void setSelectedItem(Object noiseFilterClass) {
            super.setSelectedItem(noiseFilterClass);
            setSelectedNoiseFilter(noiseFilterClass);
        }

    };

    /**
     * Sets the current denoising class and the selectedNoiseFilter instance, or
     * null for none. Note this does not affect combobox if set from code.
     *
     * @param noiseFilterClass one of static list of noise filters, or null
     */
    synchronized private void setSelectedNoiseFilter(Object noiseFilterClass) {
        resetCalled = true; // make sure we iniitialize the timestamp maps on next packet for new filter    
        if (noiseFilterClass == null) {
            selectedNoiseFilter = null;
            for (AbstractNoiseFilter n : noiseFilters) {
                if (n != null) {
                    n.setFilterEnabled(false);
                    n.setControlsVisible(false);
                }
            }
        } else {
            for (AbstractNoiseFilter n : noiseFilters) {
                if (n == null) {
                    continue;
                } else if (n.getClass() == noiseFilterClass) {
                    selectedNoiseFilter = n;
                    rocHistoryCurrent = new ROCHistory(selectedNoiseFilter);
                    n.initFilter();
                    n.setFilterEnabled(true);
                    n.setControlsVisible(true);
                    rocSweep.init();
                    try {
                        setRocSweepPropertyName(getRocSweepPropertyName());
                    } catch (IntrospectionException | MethodNotFoundException ex) {
                        log.warning(String.format("Cannot set %s as the swept property for ROC sweep for selected method %s", getRocSweepPropertyName(), selectedNoiseFilter.getClass().getSimpleName()));
                    }
                } else {
                    n.setFilterEnabled(false);
                    n.setControlsVisible(false);
                }
            }

        }
        putString("selectedNoiseFilter", noiseFilterClass == null ? "(null)" : ((Class) noiseFilterClass).getName());
    }

    public String getSelectedNoiseFilterName() {
        return selectedNoiseFilter.getClass().getSimpleName();
    }

    public NoiseTesterFilter(AEChip chip) {
        super(chip);

        String denoiser = "0a. Denoisier algorithm";
        setPropertyTooltip(denoiser, "selectedNoiseFilterEnum", "Choose a noise filter to test");
        setPropertyTooltip(denoiser, "noiseFilterComboBoxModel", "Choose a noise filter to test");

        String noise = "0b. Noise control";
        setPropertyTooltip(noise, "disableAddingNoise", "Disable adding noise; use if labeled noise is present in the AEDAT, e.g. from v2e");
        setPropertyTooltip(noise, "shotNoiseRateHz", "rate per pixel of shot noise events");
        setPropertyTooltip(noise, "photoreceptorNoiseSimulation", "<html>Generate shot noise from simulated bandlimited photoreceptor noise.<p>The <i>shotNoiseRateHz</i> will only be a guide to the actual generated noise rate. ");
        setPropertyTooltip(noise, "noiseRateCoVDecades", "Coefficient of Variation of noise rates (shot and leak) in log normal distribution decades across pixel array");
        setPropertyTooltip(noise, "leakJitterFraction", "Jitter of leak noise events relative to the (FPN) interval, drawn from normal distribution");
        setPropertyTooltip(noise, "leakNoiseRateHz", "rate per pixel of leak noise events");
        setPropertyTooltip(noise, "openNoiseSourceRecording", "Open a pre-recorded AEDAT file as noise source.");
        setPropertyTooltip(noise, "closeNoiseSourceRecording", "Closes the pre-recorded noise input.");

        String rocSw = "0c: ROC sweep parameters";
        setPropertyTooltip(rocSw, "startROCSweep", "Starts sweeping a property over the marked (or entire) recoding and record the ROC points");
        setPropertyTooltip(rocSw, "stopROCSweep", "Stops ROC sweep");
        setPropertyTooltip(rocSw, "rocSweepStart", "starting value for sweep");
        setPropertyTooltip(rocSw, "rocSweepEnd", "ending value for sweep");
        setPropertyTooltip(rocSw, "rocSweepStep", "step size value for sweep");
        setPropertyTooltip(rocSw, "rocSweepLogStep", "<html>Selected: sweep property by factors of rocSweepStep<br>Unselected: sweep in linear steps of rocSweepStep");
        setPropertyTooltip(rocSw, "rocSweepPropertyName", "which property to sweep");

        String out = "5. Output";
        setPropertyTooltip(out, "closeCsvFile", "Closes the output CSV spreadsheet data file.");
        setPropertyTooltip(out, "openCsvFile", "Opens the output spreadsheet data file named csvFileName (see " + out + " section). Set switches there to determine output columns.");
        setPropertyTooltip(out, "csvFileName", "Enter a filename base here to open CSV output file (appending to it if it already exists). Information written determined by Output switches.");
        setPropertyTooltip(out, "outputTrainingData", "<html>Output data for training MLP. <p>Outputs CSV file that has a single row with most recent event information (timestamp and polarity) for 25x25 neighborhood of each event. <p>Each row thus has about 1000 columns.");
        setPropertyTooltip(out, "recordPureNoise", "Output pure noise data for training MLP.");
        setPropertyTooltip(out, "outputFilterStatistic", "Output analyzable data of a filter.");
//        setPropertyTooltip(ann, "annotateAlpha", "Sets the transparency for the annotated pixels. Only works for Davis renderer.");

        setPropertyTooltip(TT_DISP, "overlayPositives", "<html><p>Overlay positives (passed input events)<p>FPs (red) are noise in output.<p>TPs (green) are signal in output.");
        setPropertyTooltip(TT_DISP, "overlayNegatives", "<html><p>Overlay negatives (rejected input events)<p>FNs (green) are signal filtered out.<p>TNs (red) are noise filtered out.");
        setPropertyTooltip(TT_DISP, "overlayTP", "<html><p>Overlay TP in green <br>(signal events correctly classified)");
        setPropertyTooltip(TT_DISP, "overlayTN", "<html><p>Overlay TN in red <br>(noise events correctly classified)");
        setPropertyTooltip(TT_DISP, "overlayFP", "<html><p>Overlay FP in red <br>(noise events incorrectly classified as signal)");
        setPropertyTooltip(TT_DISP, "overlayFN", "<html><p>Overlay FN in green <br>(signal events incorrectly classified as noise)");
        setPropertyTooltip(TT_DISP, "rocHistoryLength", "Number of samples of ROC point to show.");
        // buttons
        setPropertyTooltip(TT_DISP, "doResetROCHistory", "Clears current ROC samples from display.");
        setPropertyTooltip(TT_DISP, "doClearSavedRocHistories", "Clears saved ROC curves");
        setPropertyTooltip(TT_DISP, "doSaveRocHistory", "Saves current ROC points to be displayed");
        setPropertyTooltip(TT_DISP, "doClearLastRocHistory", "Erase the last recording of ROC curve");

        setHideNonEnabledEnclosedFilters(true); // only show the enabled noise filter
        if (selectedNoiseFilter != null) {
            selectedNoiseFilter.setControlsVisible(true);
        }

        for (int k = 0; k < colors.length; k++) {
            float hue = (float) k / (colors.length - 1);
            int rgb = Color.HSBtoRGB(hue, 1, 1);
            colors[k] = rgb;
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        boolean wasEnabled = this.filterEnabled;
        this.filterEnabled = yes;
        if (yes) {
            resetFilter();
            for (EventFilter2D f : chain) {
                if (selectedNoiseFilter != null && selectedNoiseFilter == f) {
                    f.setFilterEnabled(yes);
                } else {
                    f.setSelected(!yes);
                }

            }
        } else {
            for (EventFilter2D f : chain) {
                f.setFilterEnabled(false);
            }
            if (renderer != null) {
                renderer.clearAnnotationMap();
            }

        }
        if (!isEnclosed()) {
            String key = prefsEnabledKey();
            getPrefs().putBoolean(key, this.filterEnabled);
        }
        support.firePropertyChange("filterEnabled", wasEnabled, this.filterEnabled);
    }

    private int rocSampleCounter = 0;
    private final int ROC_LABEL_TAU_INTERVAL = 30;

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        String s = null;
        if (!showFilteringStatistics) {
            return;
        }
        textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, getShowFilteringStatisticsFontSize()));
        final GLUT glut = new GLUT();

        GL2 gl = drawable.getGL().getGL2();

        // draw X for last packet TPR / TNR point
        float x = (1 - TNR) * sx;
        float y = TPR * sy;
        int L = 12;
        gl.glPushMatrix();
        gl.glColor4f(1, 1, 1, 1); // must set color before raster position (raster position is like glVertex)
        gl.glLineWidth(10);
        DrawGL.drawCross(gl, x, y, L, 0);
        gl.glPopMatrix();

//            if (avgRocSample != null) { // draw big thick X at avg ROC point
//                int L = 12;
//                gl.glColor4f(.9f, .9f, .2f, .7f); // must set color before raster position (raster position is like glVertex)
//                gl.glLineWidth(8);
//                float x = avgRocSample.x;
//                float y = avgRocSample.y;
//                gl.glPushMatrix();
//                DrawGL.drawCross(gl, x, y, L, (float) Math.PI / 4);
//                gl.glPopMatrix();
//            }
        rocHistoryLabelPosY = (int) (.7 * chip.getSizeY());
        for (ROCHistory h : rocHistoriesSaved) {
            h.draw(gl);
//            float auc = h.computeAUC();
//            log.info(String.format("AUC=%.3f for %s", auc, h.toString()));
        }
        rocHistoryCurrent.draw(gl);
        if (rocSweep != null && rocSweep.running) {
            gl.glPushMatrix();
            gl.glColor4f(1, 1, 1, 1);
            DrawGL.drawStringDropShadow(getShowFilteringStatisticsFontSize(),
                    chip.getSizeX(), rocHistoryLabelPosY, 1,
                    Color.white, rocSweep.toString());
            rocHistoryLabelPosY -= (2 * getShowFilteringStatisticsFontSize());

            gl.glPopMatrix();
        }

        gl.glPushMatrix();
        gl.glColor3f(.2f, .2f, .8f); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, sy * .9f, 0);
        String overlayString = "Overlay ";
        if (overlayNegatives) {
            overlayString += "negatives: FN (green), TN (red)";
        }
        if (overlayPositives) {
            overlayString += "positives: TP (green), FP (red)";
        }
        if (overlayFN) {
            overlayString += " FN (green) ";
        }
        if (overlayFP) {
            overlayString += " FP (red) ";
        }
        if (overlayTN) {
            overlayString += " TN (red) ";
        }
        if (overlayTP) {
            overlayString += " TP (green) ";
        }
        if (prerecordedNoise != null) {
            s = String.format("NTF: Pre-recorded noise from %s with %,d events", prerecordedNoise.file.getName(), prerecordedNoise.recordedNoiseFileNoisePacket.getSize());
        } else {
            s = String.format("NTF: Synthetic noise: CoV %s dec, Leak %sHz+/-%s jitter, Shot %sHz. %s", eng.format(noiseRateCoVDecades), eng.format(leakNoiseRateHz), eng.format(leakJitterFraction), eng.format(shotNoiseRateHz),
                    overlayString);
        }
//        int font=GLUT.BITMAP_TIMES_ROMAN_24;
        DrawGL.drawString(getShowFilteringStatisticsFontSize(), 0, getAnnotationRasterYPosition(), 0, Color.white, s);
//        glut.glutBitmapString(font, s);
//        gl.glRasterPos3f(0, getAnnotationRasterYPosition("NTF"), 0);
        s = String.format("TPR=%s%% FPR=%s%% TNR=%s%% dT=%.2fus", eng.format(100 * TPR), eng.format(100 * (1 - TNR)), eng.format(100 * TNR), poissonDtUs);
//        glut.glutBitmapString(font, s);
        DrawGL.drawString(getShowFilteringStatisticsFontSize(), 0, getAnnotationRasterYPosition("NTF"), 0, Color.white, s);
//        gl.glRasterPos3f(0, getAnnotationRasterYPosition("NTF") + 10, 0);
        s = String.format("In sigRate=%s noiseRate=%s, Out sigRate=%s noiseRate=%s Hz", eng.format(inSignalRateHz), eng.format(inNoiseRateHz), eng.format(outSignalRateHz), eng.format(outNoiseRateHz));
        DrawGL.drawString(getShowFilteringStatisticsFontSize(), 0, getAnnotationRasterYPosition("NTF") + 10, 0, Color.white, s);
//        glut.glutBitmapString(font, s);
        gl.glPopMatrix();

//        nnbHistograms.draw(gl);  shows neighbor distributions, not informative
    }

    private void annotateNoiseFilteringEvents(ArrayList<FilteredEventWithNNb> outSig, ArrayList<FilteredEventWithNNb> outNoise) {
        if (renderer == null) {
            return;
        }
        for (FilteredEventWithNNb e : outSig) {
            renderer.setAnnotateColorRGBA(e.e.x + LABEL_OFFSET_PIX >= sx ? e.e.x : e.e.x + LABEL_OFFSET_PIX, e.e.y - LABEL_OFFSET_PIX < 0 ? e.e.y : e.e.y - LABEL_OFFSET_PIX, SIG_COLOR);
        }
        for (FilteredEventWithNNb e : outNoise) {
            renderer.setAnnotateColorRGBA(e.e.x + LABEL_OFFSET_PIX >= sx ? e.e.x : e.e.x + LABEL_OFFSET_PIX, e.e.y - LABEL_OFFSET_PIX < 0 ? e.e.y : e.e.y - LABEL_OFFSET_PIX, NOISE_COLOR);
        }
    }

    private void annotateNoiseFilteringEvents(ArrayList<FilteredEventWithNNb> events, float[] color) {
        if (renderer == null) {
            return;
        }
        for (FilteredEventWithNNb e : events) {
            renderer.setAnnotateColorRGBA(e.e.x + LABEL_OFFSET_PIX >= sx ? e.e.x : e.e.x + LABEL_OFFSET_PIX, e.e.y - LABEL_OFFSET_PIX < 0 ? e.e.y : e.e.y - LABEL_OFFSET_PIX, color);
        }
    }

    private class BackwardsTimestampException extends Exception {

        public BackwardsTimestampException(String string) {
            super(string);
        }

    }

    private SignalAndNoiseList createEventList(EventPacket<PolarityEvent> p, boolean splitNoiseBySpecialEvents) throws BackwardsTimestampException {
        ArrayList<PolarityEvent> signalList = new ArrayList(p.getSize());
        ArrayList<PolarityEvent> noiseList = new ArrayList(p.getSize());
        SignalAndNoiseList snl = new SignalAndNoiseList(signalList, noiseList);
        PolarityEvent previousEvent = null;
        for (PolarityEvent e : p) {
            if (previousEvent != null && (e.timestamp < previousEvent.timestamp)) {
                throw new BackwardsTimestampException(String.format("timestamp %d is earlier than previous %d", e.timestamp, previousEvent.timestamp));
            }
            if (splitNoiseBySpecialEvents) {
                if (e.isSpecial()) {
                    noiseList.add(e);
                } else {
                    signalList.add(e);
                }
            } else {
                signalList.add(e);
            }
            previousEvent = e;
        }
        return snl;
    }

    private class SignalAndNoiseList { // holds return from createEventList

        ArrayList<PolarityEvent> signalList;
        ArrayList<PolarityEvent> noiseList;

        public SignalAndNoiseList(ArrayList<PolarityEvent> signalList, ArrayList<PolarityEvent> noiseList) {
            this.signalList = signalList;
            this.noiseList = noiseList;
        }

    }

    private SignalAndNoiseList createEventList(List<PolarityEvent> p) throws BackwardsTimestampException {
        ArrayList<PolarityEvent> signalList = new ArrayList(p.size());
        SignalAndNoiseList snl = new SignalAndNoiseList(signalList, noiseList);
        PolarityEvent pe = null;
        for (PolarityEvent e : p) {
            if (pe != null && (e.timestamp < pe.timestamp)) {
                throw new BackwardsTimestampException(String.format("timestamp %d is earlier than previous %d", e.timestamp, pe.timestamp));
            }
            if (e.isSpecial()) {
                noiseList.add(e);
            } else {
                signalList.add(e);
            }
            pe = e;
        }
        return snl;
    }

    private final boolean checkStopMe(String where) {
        if (stopMe) {
            log.severe(where + "\n: Processing took longer than " + MAX_FILTER_PROCESSING_TIME_MS + "ms, disabling filter");
            setFilterEnabled(false);
            return true;
        }
        return false;
    }

    /**
     * Finds the intersection of events in a that are in b. Assumes packets are
     * non-monotonic in timestamp ordering. Handles duplicates. Each duplicate
     * is matched once. The matching is by event .equals() method.
     *
     * @param a ArrayList<PolarityEvent> of a
     * @param b likewise, but is list of events with NNb bits in byte
     * @param intersect the target list to fill with intersections, include NNb
     * bits
     * @return count of intersections
     */
    private int countIntersect(ArrayList<PolarityEvent> a, ArrayList<FilteredEventWithNNb> b, ArrayList<FilteredEventWithNNb> intersect) {
        intersect.clear();
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int count = 0;

        // TODO test case
//        a = new ArrayList();
//        b = new ArrayList();
//        a.add(new PolarityEvent(4, (short) 0, (short) 0));
//        a.add(new PolarityEvent(4, (short) 0, (short) 0));
//        a.add(new PolarityEvent(4, (short) 1, (short) 0));
//        a.add(new PolarityEvent(4, (short) 2, (short) 0));
        ////        a.add(new PolarityEvent(2, (short) 0, (short) 0));
////        a.add(new PolarityEvent(10, (short) 0, (short) 0));
//
//        b.add(new PolarityEvent(2, (short) 0, (short) 0));
//        b.add(new PolarityEvent(2, (short) 0, (short) 0));
//        b.add(new PolarityEvent(4, (short) 0, (short) 0));
//        b.add(new PolarityEvent(4, (short) 0, (short) 0));
//        b.add(new PolarityEvent(4, (short) 1, (short) 0));
//        b.add(new PolarityEvent(10, (short) 0, (short) 0));
        int i = 0, j = 0;
        final int na = a.size(), nb = b.size();
        while (i < na && j < nb) {
            if (a.get(i).timestamp < b.get(j).e.timestamp) {
                i++;
            } else if (b.get(j).e.timestamp < a.get(i).timestamp) {
                j++;
            } else {
                // If timestamps equal, it mmight be identical events or maybe not
                // and there might be several events with identical timestamps.
                // We MUST match all a with all b.
                // We don't want to increment both pointers or we can miss matches.
                // We do an inner double loop for exhaustive matching as long as the timestamps
                // are identical. 
                int i1 = i, j1 = j;
                while (i1 < na && j1 < nb && a.get(i1).timestamp == b.get(j1).e.timestamp) {
                    boolean match = false;
                    while (j1 < nb && i1 < na && a.get(i1).timestamp == b.get(j1).e.timestamp) {
                        if (a.get(i1).equals(b.get(j1).e)) {
                            count++;
                            intersect.add(b.get(j1)); // TODO debug
                            // we have a match, so use up the a element
                            i1++;
                            match = true;
                        }
                        j1++;
                    }
                    if (!match) {
                        i1++; // 
                    }
                    j1 = j; // reset j to start of matching ts region
                }
                i = i1; // when done, timestamps are different or we reached end of either or both arrays
                j = j1;
            }
        }
//        System.out.println("%%%%%%%%%%%%%%");
//        printarr(a, "a");
//        printarr(b, "b");
//        printarr(intersect, "intsct");
        return count;
    }

    // TODO test case
    private void printarr(ArrayList<PolarityEvent> a, String n) {
        final int MAX = 30;
        if (a.size() > MAX) {
            System.out.printf("--------\n%s[%d]>%d\n", n, a.size(), MAX);
            return;
        }
        System.out.printf("%s[%d] --------\n", n, a.size());
        for (int i = 0; i < a.size(); i++) {
            PolarityEvent e = a.get(i);
            System.out.printf("%s[%d]=[%d %d %d %d]\n", n, i, e.timestamp, e.x, e.y, (e instanceof PolarityEvent) ? ((PolarityEvent) e).getPolaritySignum() : 0);
        }
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {

        totalEventCount = 0; // from super, to measure filtering
        filteredOutEventCount = 0;

        int TP = 0; // filter take real events as real events. the number of events
        int TN = 0; // filter take noise events as noise events
        int FP = 0; // filter take noise events as real events
        int FN = 0; // filter take real events as noise events

        if (in == null || in.isEmpty()) {
//            log.warning("empty packet, cannot inject noise");
            return in;
        }

        stopMe = false;
        Timer stopper = null;
        if (!isDebug) {
            stopper = new Timer("NoiseTesterFilter.Stopper", true);
            stopper.schedule(new TimerTask() {
                @Override
                public void run() {
                    stopMe = true;
                }
            }, MAX_FILTER_PROCESSING_TIME_MS);
        }
        BasicEvent firstE = in.getFirstEvent();
        if (firstSignalTimestmap == null) {
            firstSignalTimestmap = firstE.timestamp;
        }
        if (resetCalled) {
            resetCalled = false;
            int ts = in.getLastTimestamp(); // we use getLastTimestamp because getFirstTimestamp contains event from BEFORE the rewind :-( Or at least it used to, fixed now I think (Tobi)
            initializeLastTimesMapForNoiseRate(shotNoiseRateHz + leakNoiseRateHz, ts);
            // initialize filters with lastTimesMap to Poisson waiting times
            log.fine("initializing timestamp maps with Poisson process waiting times");
            for (AbstractNoiseFilter f : noiseFilters) {
                if (f == null) {
                    continue;
                }
                f.initializeLastTimesMapForNoiseRate(shotNoiseRateHz + leakNoiseRateHz, ts); // TODO move to filter so that each filter can initialize its own map
                for (int[] i : lastPolMap) {
                    Arrays.fill(i, 0);
                }
            }
            initializeLeakStates(in.getFirstTimestamp());
        }

        // copy input events to inList
        ArrayList<PolarityEvent> signalList = null, noiseList = null;
        try {
            SignalAndNoiseList snl = createEventList((EventPacket<PolarityEvent>) in, true); // maybe split sig and noise by labeled special events
            signalList = snl.signalList;
            noiseList = snl.noiseList;
            if (!noiseList.isEmpty()) {
                if (!isDisableAddingNoise()) {
                    setDisableAddingNoise(true);
                    log.warning(String.format("disabled adding noise because incoming packet has %,d labeled noise events", noiseList.size()));
                    showWarningDialogInSwingThread("Disabled synthetic noise because incoming packet has labeled noise", "NoiseTesterFilter");
                }
            }
        } catch (BackwardsTimestampException ex) {
            log.warning(String.format("%s: skipping nonmonotonic packet [%s]", ex, in));
            return in;
        }

        assert signalList.size() == in.getSizeNotFilteredOut() : String.format("signalList size (%d) != in.getSizeNotFilteredOut() (%d)", signalList.size(), in.getSizeNotFilteredOut());

        // add noise into signalList to get the outputPacketWithNoiseAdded, track noise in noiseList
        if (isDisableAddingNoise()) {
            signalAndNoisePacket = (EventPacket<PolarityEvent>) in; // just make the signal+noise packet be the input packet since there is already labeled noise there
            // the noise events are already in noiseList from above
        } else {
            noiseList.clear();
            addNoise((EventPacket<? extends PolarityEvent>) in, signalAndNoisePacket, noiseList, shotNoiseRateHz, leakNoiseRateHz);
        }
        // we need to copy the augmented event packet to a HashSet for use with Collections
        ArrayList<PolarityEvent> signalPlusNoiseList;
        try {
            SignalAndNoiseList snl = createEventList((EventPacket<PolarityEvent>) signalAndNoisePacket, false); // don't split here
            signalPlusNoiseList = snl.signalList;

            if (outputTrainingData && csvWriter != null) {

                for (PolarityEvent event : signalPlusNoiseList) {
                    try {
                        int ts = event.timestamp;
                        int type = event.getPolarity() == PolarityEvent.Polarity.Off ? -1 : 1;
                        final int x = (event.x >> subsampleBy), y = (event.y >> subsampleBy);
                        int patchsize = 25;
                        int radius = (patchsize - 1) / 2;
                        if ((x < 0) || (x > sx) || (y < 0) || (y > sy)) {
                            continue;
                        }

                        StringBuilder absTstring = new StringBuilder();
                        StringBuilder polString = new StringBuilder();
                        for (int indx = -radius; indx <= radius; indx++) {
                            for (int indy = -radius; indy <= radius; indy++) {
                                int absTs = 0;
                                int pol = 0;
                                if ((x + indx >= 0) && (x + indx < sx) && (y + indy >= 0) && (y + indy < sy)) {
                                    absTs = timestampImage[x + indx][y + indy];
                                    pol = lastPolMap[x + indx][y + indy];
                                }
                                absTstring.append(absTs + ",");
                                polString.append(pol + ",");

                            }
                        }
                        if (recordPureNoise) { // if pure noise, labels must reversed otherwise the events will be labeled as signal
                            if (signalList.contains(event)) {
                                csvWriter.write(String.format("%d,%d,%d,%d,%d,%s%s%d\n",
                                        type, event.x, event.y, event.timestamp, 0, absTstring, polString, firstE.timestamp)); // 1 means signal
                            } else {
                                csvWriter.write(String.format("%d,%d,%d,%d,%d,%s%s%d\n",
                                        type, event.x, event.y, event.timestamp, 1, absTstring, polString, firstE.timestamp)); // 0 means noise
                                csvNoiseCount++;
                                csvNumEventsWritten++;
                            }
                        } else {
                            if (signalList.contains(event)) {
                                csvWriter.write(String.format("%d,%d,%d,%d,%d,%s%s%d\n",
                                        type, event.x, event.y, event.timestamp, 1, absTstring, polString, firstE.timestamp)); // 1 means signal
                                csvSignalCount++;
                                csvNumEventsWritten++;
                            } else {
                                csvWriter.write(String.format("%d,%d,%d,%d,%d,%s%s%d\n",
                                        type, event.x, event.y, event.timestamp, 0, absTstring, polString, firstE.timestamp)); // 0 means noise
                                csvNoiseCount++;
                                csvNumEventsWritten++;
                            }
                        }
                        timestampImage[x][y] = ts;
                        lastPolMap[x][y] = type;
                        if (csvNumEventsWritten % 100000 == 0) {
                            log.info(String.format("Wrote %,d events to %s", csvNumEventsWritten, csvFileName));
                        }
                    } catch (IOException e) {
                        doCloseCsvFile();
                    }
                }
            }

            // filter the augmented packet
            // make sure to record events, turned off by default for normal use
            for (EventFilter2D f : getEnclosedFilterChain()) {
                ((AbstractNoiseFilter) f).setRecordFilteredOutEvents(true);
            }
            EventPacket<PolarityEvent> passedSignalAndNoisePacket = signalAndNoisePacket;
            if (selectedNoiseFilter != null) {
                passedSignalAndNoisePacket
                        = (EventPacket<PolarityEvent>) selectedNoiseFilter.filterPacket(signalAndNoisePacket);

                ArrayList<FilteredEventWithNNb> negativeList = selectedNoiseFilter.getNegativeEvents();
                ArrayList<FilteredEventWithNNb> positiveList = selectedNoiseFilter.getPositiveEvents();

                // make a list of the output packet, which has noise filtered out by selected filter
                SignalAndNoiseList snlpassed = createEventList(passedSignalAndNoisePacket, false); // don't split events here
                ArrayList<PolarityEvent> passedSignalAndNoiseList = snlpassed.signalList;

                assert (signalList.size() + noiseList.size() == signalPlusNoiseList.size());

                // now we sort out the mess
                TP = countIntersect(signalList, positiveList, tpList);   // True positives: Signal that was correctly retained by filtering
                if (checkStopMe("after TP")) {
                    return in;
                }

                FN = countIntersect(signalList, negativeList, fnList);            // False negatives: Signal that was incorrectly removed by filter.
                if (checkStopMe("after FN")) {
                    return in;
                }
                FP = countIntersect(noiseList, positiveList, fpList);    // False positives: Noise that is incorrectly passed by filter
                if (checkStopMe("after FP")) {
                    return in;
                }
                TN = countIntersect(noiseList, negativeList, tnList);             // True negatives: Noise that was correctly removed by filter
                if (checkStopMe("after TN")) {
                    return in;
                }

//            if (TN + FP != noiseList.size()) {
//                System.err.println(String.format("TN (%d) + FP (%d) = %d != noiseList (%d)", TN, FP, TN + FP, noiseList.size()));
//                printarr(signalList, "signalList");
//                printarr(noiseList, "noiseList");
//                printarr(passedSignalAndNoiseList, "passedSignalAndNoiseList");
//                printarr(signalAndNoiseList, "signalAndNoiseList");
//            }
                assert (TN + FP == noiseList.size()) : String.format("TN (%d) + FP (%d) = %d != noiseList (%d)", TN, FP, TN + FP, noiseList.size());
                totalEventCount = signalPlusNoiseList.size();
                int outputEventCount = passedSignalAndNoiseList.size();
                filteredOutEventCount = totalEventCount - outputEventCount;

//            if (TP + FP != outputEventCount) {
//                System.err.printf("@@@@@@@@@ TP (%d) + FP (%d) = %d != outputEventCount (%d)", TP, FP, TP + FP, outputEventCount);
//                printarr(signalList, "signalList");
//                printarr(noiseList, "noiseList");
//                printarr(passedSignalAndNoiseList, "passedSignalAndNoiseList");
//                printarr(signalAndNoiseList, "signalAndNoiseList");
//            }
                assert TP + FP == outputEventCount : String.format("TP (%d) + FP (%d) = %d != outputEventCount (%d)", TP, FP, TP + FP, outputEventCount);
//            if (TP + TN + FP + FN != totalEventCount) {
//                System.err.printf("***************** TP (%d) + TN (%d) + FP (%d) + FN (%d) = %d != totalEventCount (%d)", TP, TN, FP, FN, TP + TN + FP + FN, totalEventCount);
//                printarr(signalList, "signalList");
//                printarr(noiseList, "noiseList");
//                printarr(signalAndNoiseList, "signalAndNoiseList");
//                printarr(passedSignalAndNoiseList, "passedSignalAndNoiseList");
//            }
                assert TP + TN + FP + FN == totalEventCount : String.format("TP (%d) + TN (%d) + FP (%d) + FN (%d) = %d != totalEventCount (%d)", TP, TN, FP, FN, TP + TN + FP + FN, totalEventCount);
                assert TN + FN == filteredOutEventCount : String.format("TN (%d) + FN (%d) = %d  != filteredOutEventCount (%d)", TN, FN, TN + FN, filteredOutEventCount);

//        System.out.printf("every packet is: %d %d %d %d %d, %d %d %d: %d %d %d %d\n", inList.size(), newInList.size(), outList.size(), outRealList.size(), outNoiseList.size(), outInitList.size(), outInitRealList.size(), outInitNoiseList.size(), TP, TN, FP, FN);
                TPR = TP + FN == 0 ? 0f : (float) (TP * 1.0 / (TP + FN)); // percentage of true positive events. that's output real events out of all real events
                TPO = TP + FP == 0 ? 0f : (float) (TP * 1.0 / (TP + FP)); // percentage of real events in the filter's output

                TNR = TN + FP == 0 ? 0f : (float) (TN * 1.0 / (TN + FP));
                accuracy = (float) ((TP + TN) * 1.0 / (TP + TN + FP + FN));

                BR = TPR + TPO == 0 ? 0f : (float) (2 * TPR * TPO / (TPR + TPO)); // wish to norm to 1. if both TPR and TPO is 1. the value is 1
//        System.out.printf("shotNoiseRateHz and leakNoiseRateHz is %.2f and %.2f\n", shotNoiseRateHz, leakNoiseRateHz);
            }
            if (stopper != null) {
                stopper.cancel();
            }

            if (lastTimestampPreviousPacket != null) {
                int deltaTime = in.getLastTimestamp() - lastTimestampPreviousPacket;
                inSignalRateHz = (1e6f * in.getSize()) / deltaTime;
                inNoiseRateHz = (1e6f * noiseList.size()) / deltaTime;
                outSignalRateHz = (1e6f * TP) / deltaTime;
                outNoiseRateHz = (1e6f * FP) / deltaTime;
            }

            if (outputFilterStatistic && csvWriter != null) {
                try {
                    csvWriter.write(String.format("%d,%d,%d,%d,%f,%f,%f,%d,%f,%f,%f,%f\n",
                            TP, TN, FP, FN, TPR, TNR, BR, firstE.timestamp,
                            inSignalRateHz, inNoiseRateHz, outSignalRateHz, outNoiseRateHz));
                } catch (IOException e) {
                    doCloseCsvFile();
                }
            }

            if (renderer != null) {
                renderer.clearAnnotationMap();
            }
            if (overlayPositives) {
                annotateNoiseFilteringEvents(tpList, fpList);
            }
            if (overlayNegatives) {
                annotateNoiseFilteringEvents(fnList, tnList);
            }
            if (overlayTP) {
                annotateNoiseFilteringEvents(tpList, SIG_COLOR);
            }
            if (overlayTN) {
                annotateNoiseFilteringEvents(tnList, NOISE_COLOR);
            }
            if (overlayFP) {
                annotateNoiseFilteringEvents(fpList, NOISE_COLOR);
            }
            if (overlayFN) {
                annotateNoiseFilteringEvents(fnList, SIG_COLOR);
            }

            rocHistoryCurrent.addSample(1 - TNR, TPR, getCorrelationTimeS());

            lastTimestampPreviousPacket = in.getLastTimestamp();
            return passedSignalAndNoisePacket;
        } catch (BackwardsTimestampException ex) {
            Logger.getLogger(NoiseTesterFilter.class.getName()).log(Level.SEVERE, null, ex);
            return in;
        }
    }

    /**
     * Adds the recorded or synthetic noise to packet.
     *
     * @param in the input packet
     * @param augmentedPacket the packet with noise added
     * @param generatedNoise list of noise events
     * @param shotNoiseRateHz noise rate for shot noise, per pixel
     * @param leakNoiseRateHz noise rate for leak noise, per pixel
     */
    private void addNoise(EventPacket<? extends PolarityEvent> in, EventPacket<? extends PolarityEvent> augmentedPacket, ArrayList<PolarityEvent> generatedNoise, float shotNoiseRateHz, float leakNoiseRateHz) {

        // we need at least 1 event to be able to inject noise before it
        if ((in.isEmpty())) {
            log.warning("no input events in this packet, cannot inject noise because there is no end event");
            return;
        }

        // save input packet
        augmentedPacket.clear();
        generatedNoise.clear();
        // make the itertor to save events with added noise events
        OutputEventIterator<ApsDvsEvent> outItr = (OutputEventIterator<ApsDvsEvent>) augmentedPacket.outputIterator();
        if (prerecordedNoise == null && leakNoiseRateHz == 0 && shotNoiseRateHz == 0) {
            for (PolarityEvent ie : in) {
                outItr.nextOutput().copyFrom(ie);
            }
            return; // no noise, just return which returns the copy from filterPacket
        }

        int firstTsThisPacket = in.getFirstTimestamp();
        // insert noise between last event of last packet and first event of current packet
        // but only if there waa a previous packet and we are monotonic
        if (lastTimestampPreviousPacket != null) {
            if (firstTsThisPacket < lastTimestampPreviousPacket) {
                log.warning(String.format("non-monotonic timestamp: Resetting filter. (first event %d is smaller than previous event %d by %d)",
                        firstTsThisPacket, lastTimestampPreviousPacket, firstTsThisPacket - lastTimestampPreviousPacket));
                resetFilter();
                return;
            }
            // we had some previous event
            int lastPacketTs = lastTimestampPreviousPacket; // 1us more than timestamp of the last event in the last packet
            insertNoiseEvents(lastPacketTs, firstTsThisPacket, outItr, generatedNoise);
            checkStopMe(String.format("after insertNoiseEvents at start of packet over interval of %ss",
                    eng.format(1e-6f * (lastPacketTs - firstTsThisPacket))));
        }

        // insert noise between events of this packet after the first event, record their timestamp
        // if there are no DVS events, then the iteration will not work. 
        // In this case, we assume there are only IMU or APS events and insert noise events between them, because devices 
        // typically do not include some special "clock" event to pass time.
        int preEts = 0;

        int dvsEventCounter = 0;
        int lastEventTs = in.getFirstTimestamp();
        for (PolarityEvent ie : in) {
            dvsEventCounter++;
            // if it is the first event or any with first event timestamp then just copy them
            if (ie.timestamp == firstTsThisPacket) {
                outItr.nextOutput().copyFrom(ie);
                continue;
            }
            // save the previous timestamp and get the next one, and then inject noise between them
            preEts = lastEventTs;
            lastEventTs = ie.timestamp;
            insertNoiseEvents(preEts, lastEventTs, outItr, generatedNoise);
            outItr.nextOutput().copyFrom(ie);
        }
        if (dvsEventCounter == 0 && (in instanceof ApsDvsEventPacket)) {
            Iterator itr = ((ApsDvsEventPacket) in).fullIterator();
            while (itr.hasNext()) {
                PolarityEvent ie = (PolarityEvent) (itr.next());
                // if it is the first event or any with first event timestamp then just copy them
                if (ie.timestamp == firstTsThisPacket) {
                    outItr.nextOutput().copyFrom(ie);
                    continue;
                }
                // save the previous timestamp and get the next one, and then inject noise between them
                preEts = lastEventTs;
                lastEventTs = ie.timestamp;
                insertNoiseEvents(preEts, lastEventTs, outItr, generatedNoise);
                outItr.nextOutput().copyFrom(ie);
            }
        }
    }

    private void insertNoiseEvents(int lastPacketTs, int firstTsThisPacket, OutputEventIterator<ApsDvsEvent> outItr, List<PolarityEvent> generatedNoise) {
//         check that we don't have too many events, packet will get too large
        int tstepUs = (firstTsThisPacket - lastPacketTs);
        if (tstepUs > 100_0000) {
            stopMe = true;
            checkStopMe("timestep longer than 100ms for inserting noise events, disabling filter");
            return;
        }
        final int checkStopInterval = 100000;
        int checks = 0;
        for (double ts = lastPacketTs; ts < firstTsThisPacket; ts += poissonDtUs) {
            // note that poissonDtUs is float but we truncate the actual timestamp to int us value here.
            // It's OK if there are events with duplicate timestamps (there are plenty in input already).
            int count = sampleNoiseEvent((int) ts, outItr, generatedNoise, shotOffThresholdProb, shotOnThresholdProb, leakOnThresholdProb); // note noise injection updates ts to make sure monotonic
            if (checks++ > checkStopInterval) {
                if (checkStopMe("sampling noise events")) {
                    break;
                }
            }
        }
    }

    /**
     * Samples a single noise event
     *
     * @param ts current timestamp
     * @param outItr the output iterator we add event to
     * @param noiseList the noise list we add noise event to
     * @param shotOffThresholdProb the sampling threshold
     * @param shotOnThresholdProb
     * @param leakOnThresholdProb
     * @return count of number of noise events generated
     */
    private int sampleNoiseEvent(int ts, OutputEventIterator<ApsDvsEvent> outItr, List<PolarityEvent> noiseList, float shotOffThresholdProb, float shotOnThresholdProb, float leakOnThresholdProb) {
        int count = 0;
        if (prerecordedNoise == null) { // sample 'ideal' shot noise
            final double randomnum = random.nextDouble();
            if (randomnum < shotOffThresholdProb) {
                injectShotNoiseEvent(ts, PolarityEvent.Polarity.Off, outItr, noiseList);
                count++;
            } else if (randomnum > shotOnThresholdProb) {
                injectShotNoiseEvent(ts, PolarityEvent.Polarity.On, outItr, noiseList);
                count++;
            }
            if (leakNoiseRateHz > 0) {
                PolarityEvent le = leakNoiseQueue.peek();
                while (le != null && ts >= le.timestamp) {
                    le = leakNoiseQueue.poll();
                    le.timestamp = ts;
                    ApsDvsEvent eout = (ApsDvsEvent) outItr.nextOutput();
                    eout.copyFrom(le);
                    // cryptic next line uses the AEChip's event extractor to compute the 'true' raw AER address for this event assuming word parallel format.
                    // this raw address is needed for CellStatsProber histograms
                    // TODO consider using object hash code
                    eout.address = ((TypedEventExtractor) chip.getEventExtractor()).reconstructDefaultRawAddressFromEvent(eout);
                    eout.setReadoutType(ApsDvsEvent.ReadoutType.DVS);
                    noiseList.add(eout);
                    // generate next leak event for this pixel
                    int idx = getIdxFromXY(le.x, le.y);

                    le.timestamp = le.timestamp + (int) (1e6f / (leakNoiseRateHz * noiseRateArray[idx] * (1 - getLeakJitterFraction() * random.nextGaussian())));
                    leakNoiseQueue.add(le);
                    le = leakNoiseQueue.peek();
                }

            }
//            if (random.nextDouble() < leakOnThresholdProb) { // TODO replace with periodic leak noise model with log normal rate and jitter
//                injectShotNoiseEvent(ts, PolarityEvent.Polarity.On, outItr, noiseList);
//                count++;
//            }
        } else { // inject prerecorded noise event
            ArrayList<PolarityEvent> noiseEvents = prerecordedNoise.nextEvents(ts); // these have timestamps of the prerecorded noise
            for (PolarityEvent e : noiseEvents) {
                count++;
                PolarityEvent ecopy = outItr.nextOutput(); // get the next event from output packet
                if (ecopy instanceof ApsDvsEvent) {
                    ((ApsDvsEvent) ecopy).setReadoutType(ApsDvsEvent.ReadoutType.DVS);
                }
                ecopy.copyFrom(e); // copy its fields from the noise event
                ecopy.timestamp = ts; // update the timestamp to the current timestamp
                noiseList.add(ecopy); // add it to the list of noise events we keep for analysis
            }
        }
        return count;
    }

    /**
     * Initializes the leak noise event states
     *
     * @param ts The current timestamp, leak event timestamps in future from ts
     * are added to queue
     */
    private void initializeLeakStates(int ts) {
        if (leakNoiseRateHz <= 0) {
            return;
        }
        leakNoiseQueue.clear();
        for (int i = 0; i < chip.getNumPixels(); i++) {
            XYPt xy = getXYFromIdx(i);
            PolarityEvent pe = new PolarityEvent();
            pe.x = xy.x;
            pe.y = xy.y;
            pe.polarity = Polarity.On;
            int t = random.nextInt((int) (1e6f / leakNoiseRateHz));
            pe.timestamp = ts + t;
            leakNoiseQueue.add(pe);
        }
    }

    /**
     * Creates array of shot noise rates multipliers drawn from log normal
     * distribution. The final linear array of rates are integrated to form a
     * line of length 1 where each segment length is proportional to its rate
     * multiplier. A random float in range 0 to 1 then selects a pixel with
     * frequency according to the length of that segment. Each sample requires
     * search with cost log2(N) where N is the number of pixel, i.e.
     * log2(90k)=17 steps per sample.
     */
    private void maybeCreateOrUpdateNoiseCoVArray() {

        if (noiseRateArray == null) {
            noiseRateArray = new float[chip.getNumPixels()];
            noiseRateIntervals = new float[(sx + 1) * (sy + 1)];
        }
        // fill float[][] with random normal dist values
        int idx = 0;
        double summedIntvls = 0;
        for (int i = 0; i < noiseRateArray.length; i++) {
            float randomVarMult = (float) Math.exp(random.nextGaussian() * noiseRateCoVDecades * Math.log(10));
            noiseRateArray[i] = randomVarMult;
            noiseRateIntervals[idx++] = randomVarMult;
            summedIntvls += randomVarMult;
        }
        double f = 1 / summedIntvls;
        for (int i = 0; i < noiseRateIntervals.length; i++) {
            noiseRateIntervals[i] *= f; // store normalized intervals for indiv rates, the higher the pixel's rate, the longer its interval
        }
        // now compute the integrated intervals
        for (int i = 1; i < noiseRateIntervals.length; i++) {
            noiseRateIntervals[i] += noiseRateIntervals[i - 1]; // store normalized intervals for indiv rates, the higher the pixel's rate, the longer its interval
        }
    }

    private class XYPt {

        short x, y;
    }
    private XYPt xyPt = new XYPt();

    private XYPt getXYFromIdx(int idx) {
        xyPt.y = (short) (idx % (sy + 1)); // y changes fastest
        xyPt.x = (short) (idx / (sy + 1));
        return xyPt;
    }

    private int getIdxFromXY(int x, int y) {
        return x * (sy + 1) + y;
    }

    private XYPt sampleRandomShotNoisePixelXYAddress() {
        float f = random.nextFloat();
        // find location of f in list of intervals

        int idx = search(f, noiseRateIntervals);

        return getXYFromIdx(idx);
    }

    /**
     * Searches for searchnum within sorted list of nums and returns the index
     *
     * @param searchnum
     * @param nums
     * @return index
     */
    private static int search(float searchnum, float[] nums) {
        int low = 0;
        int high = nums.length - 1;
        int mid = (low + high) / 2;
        while (low < high) {
            if (nums[mid] < searchnum) {
                if (nums[mid + 1] > searchnum) {
                    return mid;
                } else {
                    low = mid + 1;
                }
            } else {
                high = mid - 1;
            }
            mid = (low + high) / 2;
        }
        return mid;
    }

    /**
     * Inject a noise event
     *
     * @param ts timestamp
     * @param pol polarity of noise event
     * @param outItr the output iterator to add event to
     * @param noiseList the noiseList to add event to
     */
    private void injectShotNoiseEvent(int ts, PolarityEvent.Polarity pol, OutputEventIterator<ApsDvsEvent> outItr, List<PolarityEvent> noiseList) {
        ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
        e.setSpecial(false);
        if (noiseRateCoVDecades < Float.MIN_VALUE) { // if no variance in rates, just sample random pixel uniformly
            e.x = (short) random.nextInt(sx + 1);
            e.y = (short) random.nextInt(sy + 1);
        } else {
            XYPt p = sampleRandomShotNoisePixelXYAddress();
            e.x = p.x;
            e.y = p.y;
        }
        e.timestamp = ts;
        e.polarity = pol;
        // cryptic next line uses the AEChip's event extractor to compute the 'true' raw AER address for this event assuming word parallel format.
        // this raw address is needed for CellStatsProber histograms
        // TODO consider using object hash code
        e.address = ((TypedEventExtractor) chip.getEventExtractor()).reconstructDefaultRawAddressFromEvent(e);
        e.setReadoutType(ApsDvsEvent.ReadoutType.DVS);
        noiseList.add(e);
    }

    @Override
    synchronized public void resetFilter() {
        lastTimestampPreviousPacket = null;
        firstSignalTimestmap = null;
        resetCalled = true;
        getEnclosedFilterChain().reset();
        if (prerecordedNoise != null) {
            prerecordedNoise.rewind();
        }
        for (int[] arrayRow : timestampImage) {
            Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
        }
    }

    /**
     * The rate per pixel results in overall noise rate for entire sensor that
     * is product of pixel rate and number of pixels. We compute this overall
     * noise rate to determine the Poisson sample interval that is much smaller
     * than this to enable simple Poisson noise sampling. Compute time step that
     * is 10X less than the overall mean interval for noise. dt is the time
     * interval such that if we sample a random value 0-1 every dt us, the the
     * overall noise rate will be correct.
     */
    private void computeProbs() {

        int npix = (chip.getSizeX() * chip.getSizeY());
        double tmp = (1.0 / ((leakNoiseRateHz + shotNoiseRateHz) * npix)); // this value is very small
        poissonDtUs = ((tmp / POISSON_DIVIDER) * 1000000); // 1s = 1000000 us // POISSON_DIVIDER here to ensure that prob(>1 event per sample is low
        final float minPoissonDtUs = 1f / (1e-6f * MAX_TOTAL_NOISE_RATE_HZ);
        if (prerecordedNoise != null) {
            log.warning("Prerecoded noise input: clipping max noise rate to MAX_TOTAL_NOISE_RATE_HZ=" + eng.format(MAX_TOTAL_NOISE_RATE_HZ) + "Hz");
            poissonDtUs = minPoissonDtUs;
        } else if (poissonDtUs < minPoissonDtUs) {
            log.warning("clipping max noise rate to MAX_TOTAL_NOISE_RATE_HZ=" + eng.format(MAX_TOTAL_NOISE_RATE_HZ) + "Hz");
            poissonDtUs = minPoissonDtUs;
        }
        shotOffThresholdProb = (float) (0.5f * (poissonDtUs * 1e-6f * npix) * shotNoiseRateHz); // bounds for sampling Poisson noise, factor 0.5 so total rate is shotNoiseRateHz
        shotOnThresholdProb = (float) (1 - shotOffThresholdProb); // for shot noise sample both sides, for leak events just generate ON events
        leakOnThresholdProb = (float) ((poissonDtUs * 1e-6f * npix) * leakNoiseRateHz); // bounds for sampling Poisson noise
    }

    @Override
    public void initFilter() {
        rocSweep = new ROCSweep();

        if (chain == null) {
            chain = new FilterChain(chip);

            // construct the noise filters
            noiseFilters = new AbstractNoiseFilter[noiseFilterClasses.length];
            int i = 0;
            for (Class cl : noiseFilterClasses) {
                if (cl == null) {
                    noiseFilters[i] = null;  // noop filter
                    i++;
                    continue;
                }
                try {
                    Constructor con = cl.getConstructor(AEChip.class);
                    noiseFilters[i] = (AbstractNoiseFilter) con.newInstance(chip);
                    i++;
                } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    String s = String.format("Could not construct instance of AbstractNoiseFilter %s;\ngot %s", cl.getSimpleName(), ex.toString());
                    log.severe(s);
                    throw new Error(s);
                }
            }

            for (AbstractNoiseFilter n : noiseFilters) {
                if (n == null) {
                    continue;
                }
                n.initFilter();
                chain.add(n);
                getSupport().addPropertyChangeListener(n);
                n.getSupport().addPropertyChangeListener(this); // make sure we are synchronized both ways for all filter parameters
            }
            setEnclosedFilterChain(chain);
            if (getChip().getAeViewer() != null) {
                getChip().getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
                getChip().getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_CHIP, this);
                getChip().getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, this);
            }
            if (chip.getRemoteControl() != null) {
                log.info("adding RemoteControlCommand listener to AEChip\n");
                chip.getRemoteControl().addCommandListener(this, "setNoiseFilterParameters", "set correlation time or distance.");
            }
            if (chip.getRenderer() instanceof DavisRenderer) {
                renderer = (DavisRenderer) chip.getRenderer();
            }
            String initialFilterName = getString("selectedNoiseFilter", "(null)");
            if (initialFilterName.equals("(null)")) {
                initialFilterName = null;
                noiseFilterComboBoxModel.setSelectedItem(null);  // also sets the filter
                rocHistoryCurrent = new ROCHistory(null);
            } else {
                try {
                    Class c = Class.forName(initialFilterName);
                    noiseFilterComboBoxModel.setSelectedItem(c);  // also sets the filter
                    rocHistoryCurrent = new ROCHistory(selectedNoiseFilter);

                } catch (ClassNotFoundException ex) {
                    log.severe(String.format("Could not set initial noise filter to %s: %s", initialFilterName, ex.toString()));
                }
            }
        }

        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;

        timestampImage = new int[chip.getSizeX()][chip.getSizeY()];
        lastPolMap = new int[chip.getSizeX()][chip.getSizeY()];
        photoreceptorNoiseArray = new float[chip.getSizeX()][chip.getSizeY()];

        timestampImage = new int[sx + 1][sy + 1];
        leakNoiseQueue = new PriorityQueue(chip.getNumPixels());
        signalAndNoisePacket = new EventPacket<>(ApsDvsEvent.class);
//        setSelectedNoiseFilterEnum(selectedNoiseFilterEnum);
        computeProbs();
        maybeCreateOrUpdateNoiseCoVArray();
        //        setAnnotateAlpha(annotateAlpha);
        fixRendererAnnotationLayerShowing(); // make sure renderer is properly set up.

    }

    /**
     * @return the shotNoiseRateHz
     */
    public float getShotNoiseRateHz() {
        return shotNoiseRateHz;
    }

    /**
     * @param shotNoiseRateHz the shotNoiseRateHz to set
     */
    synchronized public void setShotNoiseRateHz(float shotNoiseRateHz) {
        float old = this.shotNoiseRateHz;
        if (shotNoiseRateHz < 0) {
            shotNoiseRateHz = 0;
        }
        if (shotNoiseRateHz > RATE_LIMIT_HZ) {
            log.warning("high leak rates will hang the filter and consume all memory");
            shotNoiseRateHz = RATE_LIMIT_HZ;
        }

        putFloat("shotNoiseRateHz", shotNoiseRateHz);
        this.shotNoiseRateHz = shotNoiseRateHz;
        getSupport().firePropertyChange("shotNoiseRateHz", old, this.shotNoiseRateHz);
        computeProbs();
    }

    /**
     * @return the leakNoiseRateHz
     */
    public float getLeakNoiseRateHz() {
        return leakNoiseRateHz;
    }

    /**
     * @param leakNoiseRateHz the leakNoiseRateHz to set
     */
    synchronized public void setLeakNoiseRateHz(float leakNoiseRateHz) {
        float old = this.leakNoiseRateHz;
        if (leakNoiseRateHz < 0) {
            leakNoiseRateHz = 0;
        }
        if (leakNoiseRateHz > RATE_LIMIT_HZ) {
            log.warning("high leak rates will hang the filter and consume all memory");
            leakNoiseRateHz = RATE_LIMIT_HZ;
        }

        this.leakNoiseRateHz = leakNoiseRateHz;
        putFloat("leakNoiseRateHz", leakNoiseRateHz);
        getSupport().firePropertyChange("leakNoiseRateHz", old, this.leakNoiseRateHz);
        computeProbs();
    }

    /**
     * @return the csvFileName
     */
    public String getCsvFilename() {
        return csvFileName;
    }

    /**
     * @param csvFileName the csvFileName to set
     */
    public void setCsvFilename(String csvFileName) {
        if (csvFileName.toLowerCase().endsWith(".csv")) {
            csvFileName = csvFileName.substring(0, csvFileName.length() - 4);
        }

        putString("csvFileName", csvFileName);
        getSupport().firePropertyChange("csvFileName", this.csvFileName, csvFileName);
        this.csvFileName = csvFileName;
        doOpenCsvFile();
    }

    synchronized public void doCloseCsvFile() {
        if (csvFile != null) {
            try {
                log.fine("closing CSV output file" + csvFile);
                csvWriter.close();
                float snr = (float) csvSignalCount / (float) csvNoiseCount;
                String m = String.format("closed CSV output file %s with %,d events (%,d signal events, %,d noise events, SNR=%.3g", csvFile, csvNumEventsWritten, csvSignalCount, csvNoiseCount, snr);
                showPlainMessageDialogInSwingThread(m, "CSV file closed");
                showFolderInDesktop(csvFile);
                log.info(m);
            } catch (IOException e) {
                log.warning("could not close CSV output file " + csvFile + ": caught " + e.toString());
            } finally {
                csvFile = null;
                csvWriter = null;
            }
        }
    }

    private void showFolderInDesktop(File file) {
        if (!Desktop.isDesktopSupported()) {
            log.warning("Sorry, desktop operations are not supported");
            return;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (file != null && file.exists()) {
                desktop.open(file.getAbsoluteFile().getParentFile());
            } else {
                log.warning(String.format("File %s does not exist, cannot show folder for it", file));
            }
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }

    synchronized public void doOpenCsvFile() {
        csvNumEventsWritten = 0;
        csvSignalCount = 0;
        csvNoiseCount = 0;
        String fn = csvFileName + ".csv";
        csvFile = new File(fn);
        boolean exists = csvFile.exists();
        log.info(String.format("opening %s for output", fn));
        try {
            csvWriter = new BufferedWriter(new FileWriter(csvFile, true));
            if (!exists) { // write header
                log.info("file did not exist, so writing header");
                if (outputTrainingData) {
                    log.info("writing header for MLPF training file");
                    csvWriter.write(String.format("#MLPF training data\n# type, event.x, event.y, event.timestamp,signal/noise(1/0), nnbTimestamp(25*25), nnbPolarity(25*25), packetFirstEventTimestamp\n"));
                } else if (outputFilterStatistic) {
                    log.info("writing header for filter accuracy statistics file");
                    csvWriter.write(String.format("TP,TN,FP,FN,TPR,TNR,BR,firstE.timestamp,"
                            + "inSignalRateHz,inNoiseRateHz,outSignalRateHz,outNoiseRateHz\n"));
                }
            }
        } catch (IOException ex) {
            log.warning(String.format("could not open %s for output; caught %s", fn, ex.toString()));
        }
    }

    /**
     * @return the outputTrainingData
     */
    public boolean isOutputTrainingData() {
        return outputTrainingData;
    }

    /**
     * @param outputTrainingData the outputTrainingData to set
     */
    public void setOutputTrainingData(boolean outputTrainingData) {
        this.outputTrainingData = outputTrainingData;
        putBoolean("outputTrainingData", outputTrainingData);
    }

    /**
     * @return the recordPureNoise
     */
    public boolean isRecordPureNoise() {
        return recordPureNoise;
    }

    /**
     * @param recordPureNoise the recordPureNoise to set
     */
    public void setRecordPureNoise(boolean recordPureNoise) {
        this.recordPureNoise = recordPureNoise;
    }

    /**
     * @return the outputFilterStatistic
     */
    public boolean isOutputFilterStatistic() {
        return outputFilterStatistic;
    }

    /**
     * @param outputFilterStatistic the outputFilterStatistic to set
     */
    public void setOutputFilterStatistic(boolean outputFilterStatistic) {
        this.outputFilterStatistic = outputFilterStatistic;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); //To change body of generated methods, choose Tools | Templates.
        if (evt.getPropertyName() == AEInputStream.EVENT_REWOUND) {
//            log.info(String.format("got rewound event %s, setting reset on next packet", evt));
            resetCalled = true;
        } else if (evt.getPropertyName() == AEViewer.EVENT_FILEOPEN) {
            if (prerecordedNoise != null) {
                prerecordedNoise.rewind();
            }
        }
    }

    @Override
    public void initGUI() {
        if (selectedNoiseFilter != null) {
            selectedNoiseFilter.setControlsVisible(true);
        }
    }

    private String USAGE = "Need at least 2 arguments: noisefilter <command> <args>\nCommands are: setNoiseFilterParameters <csvFilename> xx <shotNoiseRateHz> xx <leakNoiseRateHz> xx and specific to the filter\n";

    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        // parse command and set parameters of NoiseTesterFilter, and pass command to specific filter for further processing
        // e.g. 
        // setNoiseFilterParameters csvFilename 10msBAFdot_500m_0m_300num0 shotNoiseRateHz 0.5 leakNoiseRateHz 0 dt 300 num 0
        String[] tok = input.split("\\s");
        if (tok.length < 2) {
            return USAGE;
        } else {
            for (int i = 1; i < tok.length; i++) {
                if (tok[i].equals("csvFilename")) {
                    setCsvFilename(tok[i + 1]);
                } else if (tok[i].equals("shotNoiseRateHz")) {
                    setShotNoiseRateHz(Float.parseFloat(tok[i + 1]));
                    log.info(String.format("setShotNoiseRateHz %f", shotNoiseRateHz));
                } else if (tok[i].equals("leakNoiseRateHz")) {
                    setLeakNoiseRateHz(Float.parseFloat(tok[i + 1]));
                    log.info(String.format("setLeakNoiseRateHz %f", leakNoiseRateHz));
                } else if (tok[i].equals("closeFile")) {
                    doCloseCsvFile();
                    log.info(String.format("closeFile %s", csvFileName));
                }
            }
            log.info("Received Command:" + input);
            String out = selectedNoiseFilter.setParameters(command, input);
            log.info("Execute Command:" + input);
            return out;
        }
    }

    @Override
    public void setCorrelationTimeS(float dtS) {
        super.setCorrelationTimeS(dtS);
        if (selectedNoiseFilter != null) {
            selectedNoiseFilter.setCorrelationTimeS(dtS);
        }
    }

    @Override
    public void setAdaptiveFilteringEnabled(boolean adaptiveFilteringEnabled) {
        super.setAdaptiveFilteringEnabled(adaptiveFilteringEnabled);
        for (AbstractNoiseFilter f : noiseFilters) {
            if (f == null) {
                continue;
            }
            f.setAdaptiveFilteringEnabled(adaptiveFilteringEnabled);
        }
    }

    @Override
    public synchronized void setSubsampleBy(int subsampleBy) {
        super.setSubsampleBy(subsampleBy);
        for (AbstractNoiseFilter f : noiseFilters) {
            if (f == null) {
                continue;
            }
            f.setSubsampleBy(subsampleBy);
        }
    }

    @Override
    public void setFilterHotPixels(boolean filterHotPixels) {
        super.setFilterHotPixels(filterHotPixels); //To change body of generated methods, choose Tools | Templates.
        for (AbstractNoiseFilter f : noiseFilters) {
            if (f == null) {
                continue;
            }
            f.setFilterHotPixels(filterHotPixels);
        }
    }

    @Override
    public void setLetFirstEventThrough(boolean letFirstEventThrough) {
        super.setLetFirstEventThrough(letFirstEventThrough); //To change body of generated methods, choose Tools | Templates.
        for (AbstractNoiseFilter f : noiseFilters) {
            if (f == null) {
                continue;
            }
            f.setLetFirstEventThrough(letFirstEventThrough);
        }
    }

    @Override
    public synchronized void setSigmaDistPixels(int sigmaDistPixels) {
        super.setSigmaDistPixels(sigmaDistPixels); //To change body of generated methods, choose Tools | Templates.
        for (AbstractNoiseFilter f : noiseFilters) {
            if (f == null) {
                continue;
            }
            f.setSigmaDistPixels(sigmaDistPixels);
        }
    }

    /**
     * Sets renderer to show annotation layer
     */
    private void fixRendererAnnotationLayerShowing() {
        if (renderer != null) {
            renderer.setDisplayAnnotation(overlayNegatives || overlayPositives
                    || overlayFN || overlayFP || overlayTP || overlayTN);
        }
    }

    /**
     * @return the overlayPositives
     */
    public boolean isOverlayPositives() {
        return overlayPositives;
    }

    /**
     * @return the overlayNegatives
     */
    public boolean isOverlayNegatives() {
        return overlayNegatives;
    }

    /**
     * @param overlayNegatives the overlayNegatives to set
     */
    public void setOverlayNegatives(boolean overlayNegatives) {
        boolean oldOverlayNegatives = this.overlayNegatives;
        this.overlayNegatives = overlayNegatives;
        putBoolean("overlayNegatives", overlayNegatives);
        getSupport().firePropertyChange("overlayNegatives", oldOverlayNegatives, overlayNegatives);
        fixRendererAnnotationLayerShowing();
    }

    /**
     * @param overlayPositives the overlayPositives to set
     */
    public void setOverlayPositives(boolean overlayPositives) {
        boolean oldOverlayPositives = this.overlayPositives;
        this.overlayPositives = overlayPositives;
        putBoolean("overlayPositives", overlayPositives);
        getSupport().firePropertyChange("overlayPositives", oldOverlayPositives, overlayPositives);
        fixRendererAnnotationLayerShowing();
    }

    /**
     * @return the rocHistory
     */
    public int getRocHistoryLength() {
        return rocHistoryLength;
    }

    /**
     * @param rocHistory the rocHistory to set
     */
    synchronized public void setRocHistoryLength(int rocHistoryLength) {
        int old = this.rocHistoryLength;
        if (rocHistoryLength > 10000) {
            rocHistoryLength = 10000;
        }
        this.rocHistoryLength = rocHistoryLength;
        putInt("rocHistoryLength", rocHistoryLength);
        rocHistoryCurrent.reset();
        getSupport().firePropertyChange("rocHistoryLength", old, this.rocHistoryLength);
    }

    @Preferred
    synchronized public void doResetROCHistory() {
        rocHistoryCurrent.reset();
    }

    @Preferred
    synchronized public void doStartROCSweep() {
        rocSweep.start();
    }

    @Preferred
    synchronized public void doStopROCSweep() {
        rocSweep.stop();
    }

    synchronized public void doCloseNoiseSourceRecording() {
        if (prerecordedNoise != null) {
            log.info("clearing recoerded noise input data");
            prerecordedNoise = null;
        }
    }

    synchronized public void doOpenNoiseSourceRecording() {
        JFileChooser fileChooser = new JFileChooser();
        ChipDataFilePreview preview = new ChipDataFilePreview(fileChooser, getChip());
        // from book swing hacks
        fileChooser.addPropertyChangeListener(preview);
        fileChooser.setAccessory(preview);
        String chosenPrerecordedNoiseFilePath = getString("chosenPrerecordedNoiseFilePath", "");
        // get the last folder
        DATFileFilter datFileFilter = new DATFileFilter();
        fileChooser.addChoosableFileFilter(datFileFilter);
        File sf = new File(chosenPrerecordedNoiseFilePath);
        fileChooser.setCurrentDirectory(sf);
        fileChooser.setSelectedFile(sf);
        try {
            int retValue = fileChooser.showOpenDialog(getChip().getAeViewer().getFilterFrame());
            if (retValue == JFileChooser.APPROVE_OPTION) {
                chosenPrerecordedNoiseFilePath = fileChooser.getSelectedFile().toString();
                putString("chosenPrerecordedNoiseFilePath", chosenPrerecordedNoiseFilePath);
                try {
                    prerecordedNoise = new PrerecordedNoise(fileChooser.getSelectedFile());
                    if (leakNoiseRateHz + shotNoiseRateHz <= 0) {
                        showWarningDialogInSwingThread("Set leakNoiseRateHz + shotNoiseRateHz to the approx. the prerecorded noise rate for correct operation", "Noise rate not set");
                    }
                    computeProbs(); // set poissonDtUs after we construct prerecordedNoise so it is set properly
                } catch (IOException ex) {
                    log.warning(String.format("Exception trying to open data file: " + ex));
                }
            } else {
                preview.showFile(null);
            }
        } catch (GLException e) {
            log.warning(e.toString());
            preview.showFile(null);
        }
    }

    private enum Classification {
        None, TP, FP, TN, FN
    }

    /**
     * @return the noiseRateCoVDecades
     */
    public float getNoiseRateCoVDecades() {
        return noiseRateCoVDecades;
    }

    /**
     * @param noiseRateCoVDecades the noiseRateCoVDecades to set
     */
    public void setNoiseRateCoVDecades(float noiseRateCoVDecades) {
        this.noiseRateCoVDecades = noiseRateCoVDecades;
        putFloat("noiseRateCoVDecades", noiseRateCoVDecades);
        maybeCreateOrUpdateNoiseCoVArray();
    }

    /**
     * @return the leakJitterFraction
     */
    public float getLeakJitterFraction() {
        return leakJitterFraction;
    }

    /**
     * @param leakJitterFraction the leakJitterFraction to set
     */
    public void setLeakJitterFraction(float leakJitterFraction) {
        this.leakJitterFraction = leakJitterFraction;
    }

    /**
     * @return the overlayTP
     */
    public boolean isOverlayTP() {
        return overlayTP;
    }

    /**
     * @param overlayTP the overlayTP to set
     */
    public void setOverlayTP(boolean overlayTP) {
        this.overlayTP = overlayTP;
        fixRendererAnnotationLayerShowing();
    }

    /**
     * @return the overlayTN
     */
    public boolean isOverlayTN() {
        return overlayTN;
    }

    /**
     * @param overlayTN the overlayTN to set
     */
    public void setOverlayTN(boolean overlayTN) {
        this.overlayTN = overlayTN;
        fixRendererAnnotationLayerShowing();
    }

    /**
     * @return the overlayFP
     */
    public boolean isOverlayFP() {
        return overlayFP;
    }

    /**
     * @param overlayFP the overlayFP to set
     */
    public void setOverlayFP(boolean overlayFP) {
        this.overlayFP = overlayFP;
        fixRendererAnnotationLayerShowing();
    }

    /**
     * @return the overlayFN
     */
    public boolean isOverlayFN() {
        return overlayFN;
    }

    /**
     * @param overlayFN the overlayFN to set
     */
    public void setOverlayFN(boolean overlayFN) {
        this.overlayFN = overlayFN;
        fixRendererAnnotationLayerShowing();
    }

    /**
     * Tracks statistics of neighbors for false and true positives and negatives
     */
    private class NNbHistograms {

        NnbHistogram tpHist, fpHist, tnHist, fnHist;
        NnbHistogram[] histograms;

        private class NnbHistogram {

            Classification classification = Classification.None;
            final int[] nnbcounts = new int[8]; // bit frequencies around us, 8 neighbors
            final int[] byteHist = new int[256];  // array of frequency of patterns versus the pattern index
            int count; // total # events
            final float[] prob = new float[256];

            public NnbHistogram(Classification classification) {
                this.classification = classification;
            }

            void reset() {
                Arrays.fill(nnbcounts, 0);
                Arrays.fill(byteHist, 0);
                count = 0;
            }

            void addEvent(byte nnb) {
                count++;
                byteHist[0xff & nnb]++; // make sure we get unsigned byte
                if (nnb == 0) {
                    return;
                }
                for (int i = 0; i < 8; i++) {
                    if ((((nnb & 0xff) >> i) & 1) != 0) {
                        nnbcounts[i]++;
                    }
                }
            }

            String computeProbabilities() {
                for (int i = 0; i < 256; i++) {
                    prob[i] = (float) byteHist[i] / count;
                }
                // https://stackoverflow.com/questions/951848/java-array-sort-quick-way-to-get-a-sorted-list-of-indices-of-an-array
                final Integer[] ids = new Integer[256];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = i;
                }
                Arrays.sort(ids, new Comparator<Integer>() {
                    @Override
                    public int compare(final Integer o1, final Integer o2) {
                        return -Float.compare(prob[o1], prob[o2]);
                    }
                });
                StringBuilder sb = new StringBuilder(classification + " probabilities (count=" + count + ")");
                final int perline = 4;
                if (count > 0) {
                    for (int i = 0; i < ids.length; i++) {
                        if (prob[ids[i]] < 0.005f) {
                            break;
                        }
                        if (i % perline == 0) {
                            sb.append(String.format("\n%3d-%3d: ", i, i + perline - 1));
                        }
                        String binstring = String.format("%8s", Integer.toBinaryString(ids[i] & 0xFF)).replace(' ', '0');
                        sb.append(String.format("%4.1f%%:%s, ", 100 * prob[ids[i]], binstring));
                    }
                }
                sb.append("\n");
//                log.info(sb.toString());
//                float[] sortedProb = Arrays.copyOf(prob, 0);
//                Arrays.sort(sortedProb);
//                ArrayUtils.reverse(prob);
                return sb.toString();
            }

            public String toString() {
                return computeProbabilities();
//                StringBuilder sb = new StringBuilder(classification.toString() + " neighbors: [");
//                for (int i : nnbcounts) {
//                    sb.append(i + " ");
//                }
//                sb.append("]\n");
//                sb.append("counts: ");
//                final int perline = 32;
//                for (int i = 0; i < byteHist.length; i++) {
//                    if (i % perline == 0) {
//                        sb.append("\n");
//                    }
//                    sb.append(byteHist[i] + " ");
//                }
//                sb.append("\n");
//                return sb.toString();
            }

            void draw(GL2 gl) {
                int sum = 0;
                for (int i = 0; i < nnbcounts.length; i++) {
                    sum += nnbcounts[i];
                }
                if (sum == 0) {
                    sum = 1;
                }
                gl.glPushMatrix();
                for (int i = 0; i < nnbcounts.length; i++) {
                    if (nnbcounts[i] == 0) {
                        continue;
                    }
                    int ind = i < 4 ? i : i + 1;
                    int y = ind % 3, x = ind / 3;
                    float b = (float) nnbcounts[i] / sum;
                    gl.glColor3f(b, b, b);
                    gl.glRectf(x, y, x + 1, y + 1);
                }
                gl.glPopMatrix();

            }
        }

        public NNbHistograms() {
            tpHist = new NnbHistogram(Classification.TP);
            fpHist = new NnbHistogram(Classification.FP);
            tnHist = new NnbHistogram(Classification.TN);
            fnHist = new NnbHistogram(Classification.FN);

            histograms = new NnbHistogram[]{tpHist, fpHist, tnHist, fnHist};
        }

        void reset() {
            tpHist.reset();
            fpHist.reset();
            tnHist.reset();
            fnHist.reset();
        }

        void addEvent(Classification classification, byte nnb) {
            switch (classification) {
                case TP:
                    tpHist.addEvent(nnb);
                    break;
                case FP:
                    fpHist.addEvent(nnb);
                    break;
                case TN:
                    tnHist.addEvent(nnb);
                    break;
                case FN:
                    fnHist.addEvent(nnb);
                    break;
            }
        }

        void draw(GL2 gl) {
            int sc = 5;
            int x = -20;
            int y = 0;
            gl.glPushMatrix();
            gl.glTranslatef(-sc * 6, 0, 0);
            gl.glScalef(sc, sc, 1);
            for (int i = 0; i < histograms.length; i++) {
                histograms[i].draw(gl);
                gl.glTranslatef(0, 4, 0);
            }
            gl.glPopMatrix();

        }

        @Override
        public String toString() {
            return "NNbHistograms{\n" + "tpHist=" + tpHist + "\n fpHist=" + fpHist + "\n tnHist=" + tnHist + "\n fnHist=" + fnHist + "}\n";
        }

    }

    private class ROCSweep implements PropertyChangeListener {

        private float rocSweepStart = getFloat("rocSweepStart", 1e-3f);
        private float rocSweepEnd = getFloat("rocSweepEnd", 1f);
        private boolean rocSweepLogStep = getBoolean("rocSweepLogStep", true);
        private float rocSweepStep = rocSweepLogStep ? getFloat("rocSweepLogStepFactor", 1.5f) : getFloat("rocSweepStepLinear", 1e-2f);
        private String rocSweepPropertyName = getString("rocSweepPropertyName", "correlationTimeS");
        float currentValue = rocSweepStart;
        boolean running = false;
        Method setter = null, getter = null;
        boolean floatType = true;
        ROCHistory rocHistorySummary = null;
        MultivariateSummaryStatistics stats = new MultivariateSummaryStatistics(2, false);
        float startingValue = .1f;

        private HashMap<String, ROCSweepParams> rocSweepParamesHashMap = null;
        private boolean installedPropertyChangeListeners = false;

        /**
         * Class to hold parameters for HashMap to be stored in preferences
         */
        private static class ROCSweepParams implements Serializable {

            private String noiseFilterClassName;
            float rocSweepStart;
            float rocSweepEnd;
            float rocSweepStep;
            boolean rocSweepLogStep;
            String rocSweepPropertyName;

            public ROCSweepParams(String noiseFilterClassName, float rocSweepStart, float rocSweepEnd, float rocSweepStep, boolean rocSweepLogStep, String rocSweepPropertyName) {
                this.noiseFilterClassName = noiseFilterClassName;
                this.rocSweepStart = rocSweepStart;
                this.rocSweepEnd = rocSweepEnd;
                this.rocSweepStep = rocSweepStep;
                this.rocSweepLogStep = rocSweepLogStep;
                this.rocSweepPropertyName = rocSweepPropertyName;
            }
        }

        public ROCSweep() {
            init();
        }

        void storeParams() {
            if (selectedNoiseFilter == null) {
                return;
            }
            String name = selectedNoiseFilter.getClass().getName();
            ROCSweepParams p = new ROCSweepParams(name, rocSweepStart, rocSweepEnd, rocSweepStep, rocSweepLogStep, rocSweepPropertyName);
            rocSweepParamesHashMap.put(name, p);
            putObject("rocSweepParamesHashMap", rocSweepParamesHashMap);
        }

        void loadParams() {
            if (selectedNoiseFilter == null) {
                return;
            }
            String name = selectedNoiseFilter.getClass().getName();
            try {
                rocSweepParamesHashMap = (HashMap<String, ROCSweepParams>) getObject("rocSweepParamesHashMap", new HashMap());
                ROCSweepParams p = rocSweepParamesHashMap.get(name);
                if (p != null) {
                    setRocSweepStart(p.rocSweepStart);
                    setRocSweepEnd(p.rocSweepEnd);
                    setRocSweepStep(p.rocSweepStep);
                    setRocSweepLogStep(p.rocSweepLogStep);
                    try {
                        setRocSweepPropertyName(p.rocSweepPropertyName);
                    } catch (IntrospectionException ex) {
                        log.warning("Cannot set swept property name to " + p.rocSweepPropertyName + ": " + ex.toString());
                    }
                }
            } catch (ClassCastException | NullPointerException e) {
                log.log(Level.INFO, "No existing ROCSweepParams: {0}", e.toString());
            }
        }

        @Override
        public String toString() {
            if (running) {
                return String.format("ROC Sweep: start:%s end:%s currentValue=%s", eng.format(rocSweepStart), eng.format(rocSweepEnd), eng.format(currentValue));
            } else {
                return "ROC sweep (not running)";
            }
        }

        final void init() {
            if (chip.getAeViewer() != null && !installedPropertyChangeListeners) {
                chip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
                chip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_INIT, this);
                installedPropertyChangeListeners = true;
            }
            running = false;
            try {
                rocSweepParamesHashMap = (HashMap<String, ROCSweepParams>) getObject("rocSweepParamesHashMap", new HashMap());
            } catch (ClassCastException | NullPointerException e) {
                log.log(Level.INFO, "No existing ROCSweepParams: {0}", e.toString());
            }
            loadParams();
            reset();
        }

        void start() {
            if (chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.PLAYBACK) {
                chip.getAeViewer().getAePlayer().rewind();
            }
            reset();
            rocHistoryCurrent.reset();
            setRocHistoryLength(1000); //sufficient for whole marked section of recording
            rocHistorySummary = new ROCHistory(selectedNoiseFilter);
            rocHistorySummary.summary = true;
            Color color = selectedNoiseFilter == null ? Color.white : new Color(colors[lastcolor % colors.length]);
            lastcolor += 1;
            int ptSize = 8;
            rocHistorySummary.setColor(color);
            rocHistorySummary.setPtSize(ptSize);
            rocHistoriesSaved.add(rocHistorySummary);
            running = true;
            getStartingValue();
        }

        private void getStartingValue() {
            if (getter != null) {
                try {
                    startingValue = (float) getter.invoke(selectedNoiseFilter);
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    Logger.getLogger(NoiseTesterFilter.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        private void restoreStartingValue() {
            if (setter != null) {
                try {
                    setter.invoke(selectedNoiseFilter, startingValue);
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    Logger.getLogger(NoiseTesterFilter.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        void stop() {
            if (running) {
                restoreStartingValue();
            }
            running = false;
            setRocHistoryLength(1); //sufficient for whole marked section of recording
        }

        void endAndSave() {
            stop();
            rocHistoryCurrent.reset();
        }

        void reset() {
            currentValue = rocSweepStart;
        }

        boolean step() {
            if (!running) {
                return false;
            }
            // first save summary statistic for last step in private rocHistorySummary
            ROCHistory.ROCSample avg = rocHistoryCurrent.computeAvg();
            if (!Float.isNaN(avg.x) && !Float.isNaN(avg.y)) {
                rocHistorySummary.addSample(avg.x, avg.y, currentValue);
                rocHistorySummary.computeAUC();
            }
            rocHistoryCurrent.reset();

            boolean done = updateCurrentValue();

            return done;
        }

        protected boolean updateCurrentValue() {
            // now update swept variable
            float old = currentValue;
            if (rocSweepLogStep) {
                currentValue *= rocSweepStep;
            } else {
                currentValue += rocSweepStep;
            }
            if (setter != null) {
                try {
                    if (floatType) {
                        setter.invoke(selectedNoiseFilter, currentValue);
                    } else {
                        setter.invoke(selectedNoiseFilter, (int) Math.round(currentValue));
                    }
                } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ex) {
                    log.warning(String.format("Could not set current value %.4f using setter %s: got %s", currentValue, setter, ex.toString()));
                }
            } else {
                log.warning("No setter for sweeep parameter rocSweepPropertyName " + getRocSweepPropertyName());
            }
            log.info(String.format("ROCSweep increased currentValue of %s from %s -> %s", getRocSweepPropertyName(),
                    eng.format(old), eng.format(currentValue)));
            boolean done = currentValue > rocSweepEnd;
            return done;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt
        ) {
            switch (evt.getPropertyName()) {
                case AEInputStream.EVENT_REWOUND:
                    if (step()) {
                        log.info("sweep ended");
                        endAndSave();
                    }
                    break;
                case AEInputStream.EVENT_INIT:
                    stop();
                    break;
            }
        }

        /**
         * @return the rocSweepStart
         */
        public float getRocSweepStart() {
            return rocSweepStart;
        }

        /**
         * @param rocSweepStart the rocSweepStart to set
         */
        public void setRocSweepStart(float rocSweepStart) {
            float old = this.rocSweepStart;
            this.rocSweepStart = rocSweepStart;
            putFloat("rocSweepStart", rocSweepStart);
            storeParams();
            getSupport().firePropertyChange("rocSweepStart", old, this.rocSweepStart);
        }

        /**
         * @return the rocSweepEnd
         */
        public float getRocSweepEnd() {
            return rocSweepEnd;
        }

        /**
         * @param rocSweepEnd the rocSweepEnd to set
         */
        public void setRocSweepEnd(float rocSweepEnd) {
            float old = this.rocSweepEnd;
            this.rocSweepEnd = rocSweepEnd;
            putFloat("rocSweepEnd", rocSweepEnd);
            storeParams();
            getSupport().firePropertyChange("rocSweepEnd", old, this.rocSweepEnd);
        }

        /**
         * @return the rocSweepLogStep
         */
        public boolean isRocSweepLogStep() {
            return rocSweepLogStep;
        }

        /**
         * @param rocSweepLogStep the rocSweepLogStep to set
         */
        public void setRocSweepLogStep(boolean rocSweepLogStep) {
            boolean old = this.rocSweepLogStep;
            this.rocSweepLogStep = rocSweepLogStep;
            putBoolean("rocSweepLogStep", rocSweepLogStep);
            storeParams();
            getSupport().firePropertyChange("rocSweepLogStep", old, this.rocSweepLogStep);
        }

        /**
         * @return the rocSweepStep
         */
        public float getRocSweepStep() {
            return rocSweepStep;
        }

        /**
         * @param rocSweepStep the rocSweepStep to set
         */
        public void setRocSweepStep(float rocSweepStep) {
            float old = this.rocSweepStep;
            this.rocSweepStep = rocSweepStep;
            String k = rocSweepLogStep ? "rocSweepStepLog" : "rocSweepStepLinear";
            putFloat(k, rocSweepStep);
            storeParams();
            getSupport().firePropertyChange("rocSweepStep", old, this.rocSweepStep);
        }

        /**
         * @return the rocSweepPropertyName
         */
        public String getRocSweepPropertyName() {
            return rocSweepPropertyName;
        }

        /**
         * @param rocSweepPropertyName the rocSweepPropertyName to set
         */
        @Preferred
        public void setRocSweepPropertyName(String rocSweepPropertyName) throws IntrospectionException {
            String old = this.rocSweepPropertyName;
            this.rocSweepPropertyName = rocSweepPropertyName;
            if (selectedNoiseFilter == null) {
                return;
            }

            BeanInfo info;
            info = Introspector.getBeanInfo(selectedNoiseFilter.getClass());
            PropertyDescriptor[] props = info.getPropertyDescriptors();
            setter = null;
            for (PropertyDescriptor p : props) {
                if (p.getName().equals(rocSweepPropertyName)) {
                    setter = p.getWriteMethod();
                    Class[] types = setter.getParameterTypes();
                    if (types.length != 1) {
                        log.warning(String.format("setter %s does not have single argument", setter));
                        setter = null;
                    }
                    Class c = types[0];
                    if (c == float.class) {
                        floatType = true;
                    } else if (c == int.class) {
                        floatType = false;
                    } else {
                        log.warning(String.format("argument tupe of setter %s is not Float or Integer", setter));
                        setter = null;
                    }
                    getter = p.getReadMethod();
                    break;
                }
            }
            if (setter != null) {
                putString("rocSweepPropertyName", rocSweepPropertyName);
                log.info(String.format("Found setter %s for property named %s", setter.toGenericString(), rocSweepPropertyName));
                storeParams();
                getSupport().firePropertyChange("rocSweepPropertyName", old, this.rocSweepPropertyName);
            } else {
                throw new MethodNotFoundException(String.format("MethodNotFoundException: property %s is not in %s",
                        rocSweepPropertyName, selectedNoiseFilter.getClass().getSimpleName()));
            }
        }

    }

    public float getRocSweepStart() {
        return rocSweep.getRocSweepStart();
    }

    public void setRocSweepStart(float rocSweepStart) {
        rocSweep.setRocSweepStart(rocSweepStart);
    }

    public float getRocSweepEnd() {
        return rocSweep.getRocSweepEnd();
    }

    public void setRocSweepEnd(float rocSweepEnd) {
        rocSweep.setRocSweepEnd(rocSweepEnd);
    }

    public boolean isRocSweepLogStep() {
        return rocSweep.isRocSweepLogStep();
    }

    public void setRocSweepLogStep(boolean rocSweepLogStep) {
        rocSweep.setRocSweepLogStep(rocSweepLogStep);
    }

    public float getRocSweepStep() {
        return rocSweep.getRocSweepStep();
    }

    public void setRocSweepStep(float rocSweepStep) {
        rocSweep.setRocSweepStep(rocSweepStep);
    }

    public String getRocSweepPropertyName() {
        return rocSweep.getRocSweepPropertyName();
    }

    synchronized public void setRocSweepPropertyName(String rocSweepPropertyName) throws IntrospectionException {
        rocSweep.setRocSweepPropertyName(rocSweepPropertyName);
    }

    private class ROCHistory {

        private EvictingQueue<ROCSample> rocHistoryList = EvictingQueue.create(rocHistoryLength);
        private Float lastTau = null;
        private final double LOG10_CHANGE_TO_ADD_LABEL = .2;
        GLUT glut = new GLUT();
        private int counter = 0;
        private final int SAMPLE_INTERVAL_NO_CHANGE = 30;
        private int legendDisplayListId = 0;
        private ROCSample[] legendROCs = null;
        private ROCSample avgRocSample = null; // avg over entire rocHistoryLength
        private boolean fadedAndLabeled = false;
        private AbstractNoiseFilter noiseFilter = null;
        final int DEFAULT_PT_SIZE = 3;
        private int ptSize = DEFAULT_PT_SIZE;
        private Color color = Color.white;
        private String label = "";
        float auc = 0;
        boolean summary = false; // flag for a roc sweep set of points
        Boolean clockwise = null;  // flag that says last point has higher tpr/fpr, i.e. the sweep increased the signal and noise rate

        public ROCHistory(AbstractNoiseFilter noiseFilter) {
            this.noiseFilter = noiseFilter;
        }

        public String toString() {
            return String.format("ROCHistory with %,d points", rocHistoryList.size());
        }

        ROCSample createAbsolutePosition(float x, float y, float tau, boolean labeled) {
            return new ROCSample(x, y, tau, labeled);
        }

        ROCSample computeAvg() {
            float sumFpr = 0, sumTpr = 0;
            for (ROCSample s : rocHistoryList) {
                sumFpr += s.x;
                sumTpr += s.y; // note in chip px units,  dumb I know
            }
            int n = rocHistoryList.size();
            float avgFpr = sumFpr / n;
            float avgTpr = sumTpr / n;
            ROCSample avg = new ROCSample(avgFpr, avgTpr, getCorrelationTimeS(), false);
            return avg;
        }

        float computeAUC() {
            this.auc = 0;
            int n = rocHistoryList.size();
            float lastx = 0, lasty = 0;
            if (rocHistoryList.size() < 2) {
                return Float.NaN;
            }
            ROCSample first = null, last = null;
            for (ROCSample s : rocHistoryList) {
                if (Float.isNaN(s.x * s.y)) {
                    continue;
                }
                final float x = s.x, y = s.y;
                if (first == null) {
                    first = s;
                    lastx = x;
                    lasty = y;
                    last = s;
                    continue; // don't get area from first point, compute this at end once we know direction
                }
                float a = (float) Math.abs((x - lastx) * ((y + lasty) / 2));
                auc += a;
                lastx = x;
                lasty = y;
                last = s;
            }
            if (first != null && last != null) {
                if (first.x < last.x && first.y < last.y) {
                    clockwise = true;
                } else if (last.x < first.x && last.y < first.y) {
                    clockwise = false;
                }
            }
            if (clockwise != null) {
                if (clockwise) {
                    float lasta = (1 - last.x) * ((1 + last.y) / 2);
                    auc += lasta;
                    float firsta = (first.x - 0) * (first.y / 2);
                    auc += firsta;
                } else { // counterclockwise
                    float lasta = (1 - first.x) * ((1 + first.y) / 2);
                    auc += lasta;
                    float firsta = (last.x - 0) * (last.y / 2);
                    auc += firsta;
                }
            }
            return auc;
        }

        class ROCSample {

            float x, y, tau;
            boolean labeled = false;

            /**
             * Create new sample
             *
             * @param x FPR *sx position on plot
             * @param y TPR *sy position on plot in fraction of chip
             * @param tau associated correlation time or threshold
             * @param labeled true to label this sample, false for global values
             * or last sample
             */
            private ROCSample(float x, float y, float tau, boolean labeled) {
                this.x = x;
                this.y = y;
                this.tau = tau;
                this.labeled = labeled;
            }

            /**
             * Draw point using color derived from tau correlation interval
             * parameter
             *
             * @param gl
             */
            private void draw(GL2 gl) {
                float hue = (float) (Math.log10(tau) / 2 + 1.5); //. hue is 1 for tau=0.1s and is 0 for tau = 1ms 
                Color c = Color.getHSBColor(hue, 1f, hue);
                draw(gl, c, getPtSize());
            }

            /**
             * Draw point with a fixed color and size
             *
             * @param gl
             * @param c the color
             * @param size the size in (chip) pixels
             */
            private void draw(GL2 gl, Color c, int size) {
                float[] rgb = c.getRGBComponents(null);
                gl.glColor3fv(rgb, 0);
                gl.glLineWidth(1);
                gl.glPushMatrix();
                DrawGL.drawBox(gl, x * sx, y * sy, size, size, 0);
                gl.glPopMatrix();
                if (labeled) {
//                    gl.glTranslatef(5 * L, - 3 * L, 0);
                    gl.glRasterPos3f(x + size, y, 0);
//                    gl.glRotatef(-45, 0, 0, 1); // can't rotate bitmaps, must use stroke and glScalef
                    String s = String.format("%ss", eng.format(tau));
                    glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, s);
//                    glut.glutStrokeString(GLUT.STROKE_ROMAN, s);
                }
            }
        }

        private void reset() {
            rocHistoryList = EvictingQueue.create(rocHistoryLength);
            lastTau = null;
        }

        /**
         * @return the ptSize
         */
        public int getPtSize() {
            return ptSize;
        }

        /**
         * @param ptSize the ptSize to set
         */
        public void setPtSize(int ptSize) {
            this.ptSize = ptSize;
        }

        /**
         * @return the color
         */
        public Color getColor() {
            return color;
        }

        /**
         * @param color the color to set
         */
        public void setColor(Color color) {
            this.color = color;
        }

        void addSample(float fpr, float tpr, float tau) {
            boolean labelIt = lastTau == null
                    || Math.abs(Math.log10(tau / lastTau)) > .1
                    || ++counter >= SAMPLE_INTERVAL_NO_CHANGE;
            labelIt = false;// tobi removec this clutter
            rocHistoryList.add(new ROCSample(fpr, tpr, tau, false));
            avgRocSample = computeAvg();
            if (labelIt) {
                lastTau = tau;
            }
            if (counter > SAMPLE_INTERVAL_NO_CHANGE) {
                counter = 0;
            }
            if (selectedNoiseFilter != null) {
                label = selectedNoiseFilter.infoString();
            }
        }

        private void drawLegend(GL2 gl) {
            if (legendDisplayListId == 0) {
                int NLEG = 8;
                legendROCs = new ROCSample[NLEG];
                for (int i = 0; i < NLEG; i++) {
                    ROCSample r = createAbsolutePosition(sx + 5, i * 12 + 20, (float) Math.pow(10, -3 + 2f * i / (NLEG - 1)), true);
                    legendROCs[i] = r;
                }
                legendDisplayListId = gl.glGenLists(1);
                gl.glNewList(legendDisplayListId, GL2.GL_COMPILE);
                { // TODO make a real list
                }
                gl.glEndList();
            }
            gl.glPushMatrix();
//            gl.glCallList(legendDisplayListId);
            for (ROCSample r : legendROCs) {
                r.draw(gl);
            }
            gl.glPopMatrix();
        }

        void draw(GL2 gl) {
            if (selectedNoiseFilter == null) {
                return;
            }
            drawAxes(gl);
            // draw each sample as a square point

            for (ROCSample rocSample : rocHistoryList) {
                rocSample.draw(gl, color, ptSize);
            }
            if (noiseFilter != null) {
                DrawGL.drawString(getShowFilteringStatisticsFontSize(),
                        chip.getSizeX(), rocHistoryLabelPosY, 1, color,
                        label + String.format(" AUC=%.3f", auc));
            }
            rocHistoryLabelPosY -= getShowFilteringStatisticsFontSize();
            if (summary) {
                float[] rgb = color.getRGBComponents(null);
                gl.glColor3fv(rgb, 0);
                gl.glLineWidth(1);
                gl.glPushMatrix();
                gl.glBegin(GL.GL_LINE_STRIP);
                if (clockwise != null) {
                    if (clockwise) {
                        gl.glVertex2f(0, 0);
                    } else {
                        gl.glVertex2f(sx, sy);
                    }
                }
                for (ROCSample rocSample : rocHistoryList) {
                    gl.glVertex2f(rocSample.x * sx, rocSample.y * sy);
                }
                if (clockwise != null) {
                    if (clockwise) {
                        gl.glVertex2f(sx, sy);
                    } else {
                        gl.glVertex2f(0, 0);
                    }
                }
                gl.glEnd();
                gl.glPopMatrix();

            }

        }

        private void drawAxes(GL2 gl) {
            // draw axes
            gl.glPushMatrix();
            gl.glColor4f(1, 1, 1, 1); // must set color before raster position (raster position is like glVertex)
            gl.glLineWidth(2);
            gl.glBegin(GL.GL_LINE_STRIP);
            gl.glVertex2f(0, sy);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(sx, 0);
            gl.glEnd();
//            gl.glRasterPos3f(sx / 2 - 30, -10, 0);
//            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, "FPR");
            DrawGL.drawString(getShowFilteringStatisticsFontSize(), sx / 2, -10, .5f, Color.white, "FPR");
//            gl.glPushMatrix();
//            gl.glTranslatef(sx / 2, -10, 0);
//            gl.glScalef(.1f, .1f, 1);
//            glut.glutStrokeString(GLUT.STROKE_ROMAN, "FPR");
//            gl.glPopMatrix();
//            gl.glRasterPos3f(-30, sy / 2, 0);
//            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, "TPR");
            DrawGL.drawString(getShowFilteringStatisticsFontSize(), -1, sy / 2, 1, Color.white, "TPR");
            gl.glPopMatrix();
        }

    }

    // recorded noise to be used as input
    private class PrerecordedNoise {

        EventPacket<PolarityEvent> recordedNoiseFileNoisePacket = null;
        Iterator<PolarityEvent> itr = null;
        PolarityEvent noiseFirstEvent, noiseNextEvent;
        int noiseFirstTs;
        Integer signalMostRecentTs;
        private ArrayList<PolarityEvent> returnedEvents = new ArrayList();
        int noiseEventCounter = 0;
        File file;

        private PrerecordedNoise(File chosenPrerecordedNoiseFilePath) throws IOException {
            file = chosenPrerecordedNoiseFilePath;
            AEFileInputStream recordedNoiseAeFileInputStream = new AEFileInputStream(file, getChip());
            AEPacketRaw rawPacket = recordedNoiseAeFileInputStream.readPacketByNumber(MAX_NUM_RECORDED_EVENTS);
            recordedNoiseAeFileInputStream.close();

            EventPacket<PolarityEvent> inpack = getChip().getEventExtractor().extractPacket(rawPacket);
            EventPacket<PolarityEvent> recordedNoiseFileNoisePacket = new EventPacket(PolarityEvent.class);
            OutputEventIterator outItr = recordedNoiseFileNoisePacket.outputIterator();
            for (PolarityEvent p : inpack) {
                outItr.nextOutput().copyFrom(p);
            }
            this.recordedNoiseFileNoisePacket = recordedNoiseFileNoisePacket;
            itr = recordedNoiseFileNoisePacket.inputIterator();
            noiseFirstEvent = recordedNoiseFileNoisePacket.getFirstEvent();
            noiseFirstTs = recordedNoiseFileNoisePacket.getFirstTimestamp();

            this.noiseNextEvent = this.noiseFirstEvent;
            computeProbs(); // set noise sample rate via poissonDtUs
            log.info(String.format("Loaded %s pre-recorded events with duration %ss from %s", eng.format(recordedNoiseFileNoisePacket.getSize()), eng.format(1e-6f * recordedNoiseAeFileInputStream.getDurationUs()), chosenPrerecordedNoiseFilePath));
        }

        ArrayList<PolarityEvent> nextEvents(int ts) {
            returnedEvents.clear();
            if (signalMostRecentTs == null) {
                signalMostRecentTs = ts;
                return returnedEvents; // no events at first, just get the timestamap
            }
            if (ts < signalMostRecentTs) { // time went backwards, rewind noise events
                rewind();
                return nextEvents(ts);
            }
            while (noiseNextEvent.timestamp - noiseFirstTs < ts - signalMostRecentTs) {
                returnedEvents.add(noiseNextEvent);
                noiseEventCounter++;
                if (itr.hasNext()) {
                    noiseNextEvent = itr.next();
                } else {
                    rewind();
                    return returnedEvents;
                }
            }
            return returnedEvents;
        }

        private void rewind() {
            log.info(String.format("rewinding noise events after %,d events", noiseEventCounter));
            this.itr = recordedNoiseFileNoisePacket.inputIterator();
            noiseNextEvent = noiseFirstEvent;
            signalMostRecentTs = null;
            noiseEventCounter = 0;
        }
    }

    /**
     * By default empty method (which logs warning if called) that initializes
     * filter to produce proper statistics for noise filtering by filling past
     * events history with past events according to Poisson times of noise
     * events
     *
     * @param noiseRateHz rate in Hz
     * @param lastTimestampUs the last timestamp; waiting times are created
     * before this time
     */
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        Random random = new Random();
        for (final int[] arrayRow : timestampImage) {
            for (int i = 0; i < arrayRow.length; i++) {
                final double p = random.nextDouble();
                final double t = -noiseRateHz * Math.log(1 - p);
                final int tUs = (int) (1000000 * t);
                arrayRow[i] = lastTimestampUs - tUs;
            }
        }
        for (final int[] arrayRow : lastPolMap) {
            for (int i = 0; i < arrayRow.length; i++) {
                final boolean b = random.nextBoolean();
                arrayRow[i] = b ? 1 : -1;
            }
        }
    }

    /**
     * @return the photoreceptorNoiseSimulation
     */
    public boolean isPhotoreceptorNoiseSimulation() {
        return photoreceptorNoiseSimulation;
    }

    /**
     * @param photoreceptorNoiseSimulation the photoreceptorNoiseSimulation to
     * set
     */
    public void setPhotoreceptorNoiseSimulation(boolean photoreceptorNoiseSimulation) {
        this.photoreceptorNoiseSimulation = photoreceptorNoiseSimulation;
        putBoolean("photoreceptorNoiseSimulation", photoreceptorNoiseSimulation);
    }

    private float compute_vn_from_log_rate_per_hz(float thr, float x) {
//        # y = log10(thr/Vn)
//        # x = log10(Rn/f3db)
//        # see the plot Fig. 3 from Graca, Rui, and Tobi Delbruck. 2021. “Unraveling the Paradox of Intensity-Dependent DVS Pixel Noise.” arXiv [eess.SY]. arXiv. http://arxiv.org/abs/2109.08640.
//        # the fit is computed in media/noise_event_rate_simulation.xlsx spreadsheet
        float y = (float) (-0.0026f * Math.pow(x, 3) - 0.036f * x * x - 0.1949f * x + 0.321f);//
        float thr_per_vn = (float) Math.pow(y, 10);//  # to get thr/vn;
        float vn = thr / thr_per_vn;//  # compute necessary vn to give us this noise rate per pixel at this pixel bandwidth
        return vn;
    }

    private float compute_photoreceptor_noise_voltage(float shot_noise_rate_hz, float f3db, float sample_rate_hz, float pos_thr, float neg_thr, float sigma_thr) {
//    """
//     Computes the necessary photoreceptor noise voltage to result in observed shot noise rate at low light intensity.
//     This computation relies on the known f3dB photoreceptor lowpass filter cutoff frequency and the known (nominal) event threshold.
//     emulator.py injects Gaussian distributed noise to the photoreceptor that should in principle generate the desired shot noise events.
//
//     See the file media/noise_event_rate_simulation.xlsx for the simulation data and curve fit.
//
//    Parameters
//    -----------
//     shot_noise_rate_hz: float
//        the desired pixel shot noise rate in hz
//     f3db: float
//        the 1st-order IIR RC lowpass filter cutoff frequency in Hz
//     sample_rate_hz: float
//        the sample rate (up-sampled frame rate) before IIR lowpassing the noise
//     pos_thr:float
//        on threshold in ln units
//     neg_thr:float
//        off threshold in ln units. The on and off thresholds are averaged to obtain a single threshold.
//     sigma_thr: float
//        the std deviations of the thresholds
//
//    Returns
//    -----------
//    float
//         Noise signal Gaussian RMS value in log_e units, to be added as Gaussian source directly to log photoreceptor output signal
//    """

//        Random r = new Random();
        float rate_per_bw = (shot_noise_rate_hz / f3db) / 2; // simulation data are on ON event rates, divide by 2 here to end up with correct total rate
        if (rate_per_bw > 0.5) {
//        logger.warning(f'shot noise rate per hz of bandwidth is larger than 0.1 (rate_hz={shot_noise_rate_hz} Hz, 3dB bandwidth={f3db} Hz)');
        }
        float x = (float) Math.log10(rate_per_bw);
        if (x < -5.0) {
            log.warning("desired noise rate of  is too low to accurately compute a threshold value");
        } else if (x > 0.0) {
            log.warning("desired noise rate of  is too large to accurately compute a threshold value");
        }

        float thr = (pos_thr + neg_thr) / 2;
        float vn = compute_vn_from_log_rate_per_hz(thr, x);

        return vn;
    }

    /**
     * @return the disableAddingNoise
     */
    public boolean isDisableAddingNoise() {
        return disableAddingNoise;
    }

    /**
     * @param disableAddingNoise the disableAddingNoise to set
     */
    public void setDisableAddingNoise(boolean disableAddingNoise) {
        boolean old = this.disableAddingNoise;
        this.disableAddingNoise = disableAddingNoise;
        putBoolean("disableAddingNoise", disableAddingNoise);
        getSupport().firePropertyChange("disableAddingNoise", old, this.disableAddingNoise);
        resetFilter(); // reset since this affects filter and can cause apparent deadlock
    }

    @Preferred
    public void doSaveRocHistory() {
        rocHistoriesSaved.add(rocHistoryCurrent);
        rocHistoryCurrent = new ROCHistory(selectedNoiseFilter);
    }

    @Preferred
    public void doClearSavedRocHistories() {
        ROCHistory h = rocHistoriesSaved.getLast();
        rocHistoriesSaved.remove(h);
    }

    @Preferred
    public void doClearLastRocHistory() {
        ROCHistory r = rocHistoriesSaved.getLast();
        rocHistoriesSaved.removeLast();
    }

    @Override
    public void setShowFilteringStatisticsFontSize(int size) {
        super.setShowFilteringStatisticsFontSize(size);
        for (AbstractNoiseFilter f : noiseFilters) {
            if (f == null) {
                continue;
            }
            f.setShowFilteringStatisticsFontSize(size);

        }
    }

    /**
     * @return the noiseFilterComboBoxModel
     */
    public ComboBoxModel<Class> getNoiseFilterComboBoxModel() {
        return noiseFilterComboBoxModel;
    }

    /**
     * @param noiseFilterComboBoxModel the noiseFilterComboBoxModel to set
     */
    public void setNoiseFilterComboBoxModel(ComboBoxModel<Class> noiseFilterComboBoxModel) {
        this.noiseFilterComboBoxModel = noiseFilterComboBoxModel;
    }
}
