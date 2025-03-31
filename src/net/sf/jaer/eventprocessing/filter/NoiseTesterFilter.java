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
import eu.seebetter.ini.chips.davis.HotPixelFilter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
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
import net.sf.jaer.util.ClassChooserDialog;
import net.sf.jaer.util.ClassNameWithDescriptionAndDevelopmentStatus;
import net.sf.jaer.util.DATFileFilter;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.ShowFolderSaveConfirmation;
import net.sf.jaer.util.SubclassFinder;
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
    public static Class[] defaultNoiseFilterClasses = {
        HotPixelFilter.class,
        BackgroundActivityFilter.class,
        SpatioTemporalCorrelationFilter.class,
        //        QuantizedSTCF.class,
        AgePolarityDenoiser.class,
        //        MultiEventAgePolarityDenoiser.class,
        //        LinearCorrelationDenoiser.class,
        DoubleWindowFilter.class,
        MLPNoiseFilter.class
    };

    public static Class[] noiseFilterClasses = defaultNoiseFilterClasses;

    private AbstractNoiseFilter[] noiseFilters = null; // array of denoiseer instances, starting with null
//    private HashMap<AbstractNoiseFilter, Integer> noiseFilter2ColorMap = new HashMap(); // for rendering, holds int AWT color
    private AbstractNoiseFilter selectedNoiseFilter = null;
    private boolean enableMultipleMethods = getBoolean("enableMultipleMethods", false);

    public static final int MAX_NUM_RECORDED_EVENTS = 10_0000_0000;
    public static final float MAX_TOTAL_NOISE_RATE_HZ = 50e6f;
    public static final float RATE_LIMIT_HZ = 25; //per pixel, separately for leak and shot rates

    private FilterChain chain;

    private boolean disableAddingNoise = getBoolean("disableAddingNoise", false);
    private boolean disableDenoising = false;
    private boolean disableSignal = false;
    private String noiseSummaryString = null;
    @Preferred
    private float shotNoiseRateHz = getFloat("shotNoiseRateHz", 5f);
    protected boolean photoreceptorNoiseSimulation = getBoolean("photoreceptorNoiseSimulation", true);
    @Preferred
    private float leakNoiseRateHz = getFloat("leakNoiseRateHz", .3f);
    @Preferred
    private float noiseRateCoVDecades = getFloat("noiseRateCoVDecades", 0.5f);
    private float leakJitterFraction = getFloat("leakJitterFraction", 0.2f); // fraction of interval to jitter leak events
    private float[] noiseRateArray = null;
    private float[] noiseRateIntervals = null; // stored by column, with y changing fastest
    private PriorityQueue<SignalNoiseEvent> leakNoiseQueue = null; // stored by column, with y changing fastest

    private double poissonDtUs = 1;

    private float shotOffThresholdProb; // bounds for samppling Poisson noise, factor 0.5 so total rate is shotNoiseRateHz
    private float shotOnThresholdProb; // for shot noise sample both sides, for leak events just generate ON events
    private float leakOnThresholdProb; // bounds for samppling Poisson noise

    private PrerecordedNoise prerecordedNoise = null;

    private ROCHistory rocHistoryCurrent = new ROCHistory(null);
    private ArrayList<ROCHistory> rocHistoriesSaved = new ArrayList();
    private ArrayList<ROCSweep> rocSweeps = new ArrayList();
    private int rocHistoryLabelPosY = 0;
    private ROCSweep rocSweep = new ROCSweep(null);

    int[] colors = new int[6];
    int lastcolor = 0;

    private static String DEFAULT_CSV_FILENAME_BASE = "NoiseTesterFilter";
    private String csvFileName = getString("csvFileName", DEFAULT_CSV_FILENAME_BASE);
    private File csvFile = null;
    private BufferedWriter csvWriter = null;
    private int csvNumEventsWritten = 0, csvSignalCount = 0, csvNoiseCount = 0;
    private int[][] timestampImage = null; // image of last event timestamps
    private byte[][] polImage;
//    private float[][] photoreceptorNoiseArray; // see https://github.com/SensorsINI/v2e/blob/565f6991daabbe0ad79d68b50d084d5dc82d6426/v2ecore/emulator_utils.py#L177

    /**
     * Chip dimensions in pixels MINUS ONE, set in initFilter()
     */
    private int sx = 0, sy = 0;

    private float TPR = 0;
    private float FPR = 0;
    private float TNR = 0;
    private float FNR = 0;

    private float TPO = 0;
    private float accuracy = 0;
    private float BR = 0;
    float inSignalRateHz = 0, inNoiseRateHz = 0, outSignalRateHz = 0, outNoiseRateHz = 0;
    float inSNR = Float.NaN, outSNR = Float.NaN;

//    private EventPacket<ApsDvsEvent> signalAndNoisePacket = null;
    private final Random random = new Random();

    protected boolean resetCalled = true; // flag to reset on next event
    private int timestampAfterReset;  // used for initializing timestampImage and to prevent collecting statistics until correlationTimeS has passed since reset or rewind6yd
    private Integer lastTimestampPreviousPacket = null, firstSignalTimestmapAfterReset = null, firstSignalEventTimestampThisPacket = null; // use Integer Object so it can be null to signify no value yet

//    private float annotateAlpha = getFloat("annotateAlpha", 0.5f);
    private DavisRenderer renderer = null;
    private boolean overlayPositives = getBoolean("overlayPositives", false);
    private boolean overlayNegatives = getBoolean("overlayNegatives", false);
    private boolean overlayTP = getBoolean("overlayTP", false);
    private boolean overlayTN = getBoolean("overlayTN", false);
    private float overlayAlpha = getFloat("overlayAlpha", .5f);
    @Preferred
    private boolean overlayFP = getBoolean("overlayFP", false);
    @Preferred
    private boolean overlayFN = getBoolean("overlayFN", false);
    final float[] NOISE_COLOR = {1f, 0, 0, .3f}, SIG_COLOR = {0, 1f, 0, .3f};
    final int LABEL_OFFSET_PIX = 1; // how many pixels LABEL_OFFSET_PIX is the annnotation overlay, so we can see original signal/noise event and its label

    private boolean outputTrainingData = false;
    private boolean recordPureNoise = false;
    private boolean outputFilterStatistic = false;

    @Preferred
    private int rocHistoryLength = getInt("rocHistoryLength", 8);
    private final int LIST_LENGTH = 10000;

    // objects for denoising and keeping track of signal and noise events
    private SignalNoisePacket signalNoisePacket = new SignalNoisePacket(SignalNoiseEvent.class);
    final private SignalNoisePacket signalPacket = new SignalNoisePacket(SignalNoiseEvent.class);

    // original NTF arrays for tracking signal and noise events
    private EventPacket<PolarityEvent> signalAndNoisePacket = null;
    ;
    private final ArrayList<PolarityEvent> noiseList = new ArrayList<>(LIST_LENGTH); // TODO make it lazy, when filter is enabled
    /**
     * How time is split up for Poisson sampling using bounds trick
     */
    public static final int POISSON_DIVIDER = 30;

    private final Timer stopper = new Timer("NoiseTesterFilter.Stopper", true);
    private volatile boolean stopMe = false; // to interrupt if filterPacket takes too long
    private TimerTask stopperTask = null;
    // https://stackoverflow.com/questions/1109019/determine-if-a-java-application-is-in-debug-mode-in-eclipse\
    // isDebug is only set true on windows systems, not linux
    private final boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("debug");
    private int maxProcessingTimeLimitMs = getInt("maxProcessingTimeLimitMs", 500); // times out to avoid using up all heap

    /**
     * ComboBoxModel that holds the noise filter classes
     */
    @Preferred
    private ComboBoxModel noiseFilterComboBoxModel = null; // construct after prefs exist in constructor

    @Preferred
    private ComboBoxModel<String> rocSweepParameterComboBoxModel = new DefaultComboBoxModel<String>() {
        @Override
        public void setSelectedItem(Object propName) {
            try {
                super.setSelectedItem(propName);
                setRocSweepPropertyName((String) propName);
            } catch (IntrospectionException | MethodNotFoundException ex) {
                log.severe(ex.toString());
            }
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
//                    n.initFilter();
                    n.setFilterEnabled(true);
                    n.setControlsVisible(true);
                    if (n.getFilterPanel() != null) {
                        n.getFilterPanel().setVisible(true);
                    }

                    rocSweep = new ROCSweep(n);
                    constructRocSweepParameterComboBoxModel();

                } else {
                    n.setFilterEnabled(false);
                    n.setControlsVisible(false);
                    if (n.getFilterPanel() != null) {
                        n.getFilterPanel().setVisible(false);
                    }
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

        String denoiser = "0a. Denoisier class selection";
        setPropertyTooltip(denoiser, "selectedNoiseFilterEnum", "Choose a noise filter to test");
        setPropertyTooltip(denoiser, "noiseFilterComboBoxModel", "Choose a noise filter to test");
        setPropertyTooltip(denoiser, "enableMultipleMethods", "Enable chain of denoising methods, in order top to bottom that are enabled.");
        setPropertyTooltip(denoiser, "maxProcessingTimeLimitMs", "Maximum time limit in ms for processing each packet; to deal with problems with high noise rates and complex algorithms that may appear frozen.");

        String noise = "0b. Synethetic noise control";
        setPropertyTooltip(noise, "disableAddingNoise", "Disable adding noise; use if labeled noise is present in the AEDAT, e.g. from v2e");
        setPropertyTooltip(noise, "disableSignal", "Disable signal events, use to set parameters for e.g. HotPixelFilter or to study pure noise FPR");
        setPropertyTooltip(noise, "disableDenoising", "Disable denoising temporarily (not stored in preferences)");
        setPropertyTooltip(noise, "shotNoiseRateHz", "Mean rate per pixel in Hz of shot noise events");
        setPropertyTooltip(noise, "photoreceptorNoiseSimulation", "<html>Generate shot noise from simulated bandlimited photoreceptor noise.<p>The <i>shotNoiseRateHz</i> will only be a guide to the actual generated noise rate. ");
        setPropertyTooltip(noise, "noiseRateCoVDecades", "<html>Coefficient of Variation of noise rates (shot and leak) in log normal distribution decades across pixel array.<p>0.5 decade is realistic for DAVIS cameras.<p>Change this property resamples the noise rates.");
        setPropertyTooltip(noise, "leakJitterFraction", "<html>Jitter of leak noise events relative to the (FPN) interval, drawn from normal distribution.<p>0.1 to 0.2 is realistic for DAVIS cameras.");
        setPropertyTooltip(noise, "leakNoiseRateHz", "Rate per pixel in Hz of leak noise events. 0.1 to 0.2Hz is realistic for DAVIS cameras.");
        setPropertyTooltip(noise, "useNoiseRecording", "Open a pre-recorded AEDAT file as noise source.");
        setPropertyTooltip(noise, "doCloseNoise", "Closes the pre-recorded noise input.");

        String rocSw = "0c: ROC sweep parameters";
        setPropertyTooltip(rocSw, "ROCSweep", "Toggles ON/OFF sweeping a property over the marked (or entire) recoding and record the ROC points");
        setPropertyTooltip(rocSw, "doStopROC", "Stops ROC sweep");
        setPropertyTooltip(rocSw, "rocSweepStart", "Starting value for sweep");
        setPropertyTooltip(rocSw, "rocSweepEnd", "Ending value for sweep");
        setPropertyTooltip(rocSw, "rocSweepStep", "Step size value for sweep. Multiplicative (e.g. 1.4) for rocSweepLogStep, additive (e.g. 0.25) for !rocSweepLogStep.");
        setPropertyTooltip(rocSw, "rocSweepLogStep", "<html>Selected: sweep property by factors of rocSweepStep<br>Unselected: sweep in linear steps of rocSweepStep");
//        setPropertyTooltip(rocSw, "rocSweepPropertyName", "Which property of the selected denoiser to sweep");
        setPropertyTooltip(rocSw, "rocSweepParameterComboBoxModel", "Which property of the selected denoiser to sweep");

        String out = "5. Output";
        setPropertyTooltip(out, "saveMLPTraining", "Toggles ON/OFF the MLP training output spreadsheet data file named csvFileName (see " + out + " section). Set switches there to determine output columns.");
        setPropertyTooltip(out, "csvFileName", "<html>Enter a filename base here to set MLP training CSV output filename <br>(appending to it if it already exists). <p>Information written is determined by Output switches.<p>Use <i>Save MLP Training</i> button start writing out data.");
        setPropertyTooltip(out, "outputTrainingData", "<html>Output data for training MLP. <p>Outputs CSV file that has a single row with most recent event information (timestamp and polarity) for 25x25 neighborhood of each event. <p>Each row thus has about 1000 columns.");
        setPropertyTooltip(out, "recordPureNoise", "Output pure noise data for training MLP.");
        setPropertyTooltip(out, "outputFilterStatistic", "Output analyzable data of a filter.");
        setPropertyTooltip(out, "doExportROCs", "Opens a dialog to export ROC sweeps data to multiple CSV files in a folder you choose with your chosen base filename");
//        setPropertyTooltip(ann, "annotateAlpha", "Sets the transparency for the annotated pixels. Only works for Davis renderer.");

        setPropertyTooltip(TT_DISP, "overlayPositives", "<html><p>Overlay positives (passed input events)<p>FPs (red) are noise in output.<p>TPs (green) are signal in output.");
        setPropertyTooltip(TT_DISP, "overlayNegatives", "<html><p>Overlay negatives (rejected input events)<p>FNs (green) are signal filtered out.<p>TNs (red) are noise filtered out.");
        setPropertyTooltip(TT_DISP, "overlayTP", "<html><p>Overlay TP in green <br>(signal events correctly classified)");
        setPropertyTooltip(TT_DISP, "overlayTN", "<html><p>Overlay TN in red <br>(noise events correctly classified)");
        setPropertyTooltip(TT_DISP, "overlayFP", "<html><p>Overlay FP in red <br>(noise events incorrectly classified as signal)");
        setPropertyTooltip(TT_DISP, "overlayFN", "<html><p>Overlay FN in green <br>(signal events incorrectly classified as noise)");
        setPropertyTooltip(TT_DISP, "overlayAlpha", "The alpha (opacity) of the overlaid classification colors");
        setPropertyTooltip(TT_DISP, "rocHistoryLength", "Number of samples of ROC point to show. The average over these samples is shown as the large cross.");

        // buttons
//        setPropertyTooltip(TT_DISP, "doResetROCHistory", "Clears current ROC samples from display.");
        setPropertyTooltip(TT_DISP, "doChooseDenoisers", "<html>Shows a dialog to choose the denoisers to benchmark.<p>These are subclasses of AbstractNoiseFilter.");
        setPropertyTooltip(TT_DISP, "doClearROCs", "Clears all saved ROC curves and current samples from packets");
        setPropertyTooltip(TT_DISP, "doClearLastROC", "Erase the last recording of ROC curve");
        setPropertyTooltip(TT_FILT_CONTROL, "doResampleFPN", "<html>Resample the fixed pattern noise of hot pixels (leak and shot noise variation)<br> that models DVS event threshold and junction leakage fixed pattern noise variation.<p> Resampling also occurs when noise COV is modified.");

        for (int k = 0; k < colors.length; k++) {
            float hue = (float) k / (colors.length - 1);
            int rgb = Color.HSBtoRGB(hue, 1, 1);
            colors[k] = rgb;
        }

        hideProperty("correlationTimeS");   // don't set in enclosed denoisers because they might need to differ

        StringBuilder sb = new StringBuilder("Selected preferred AbstractNoiseFilter denoisers: ");
        ArrayList<String> defaultNoiseFilterClassNames = new ArrayList();
        for (Class cl : defaultNoiseFilterClasses) {
            defaultNoiseFilterClassNames.add(cl.getName());
        }

        try {
            ArrayList<String> preferredDenoiserClassNames = (ArrayList<String>) getObject("preferredDenoiserClassNames", defaultNoiseFilterClassNames);
            noiseFilterClasses = new Class[preferredDenoiserClassNames.size()];
            int i = 0;
            for (String clName : preferredDenoiserClassNames) {
                Class cl = Class.forName(clName);
                noiseFilterClasses[i++] = cl;
                sb.append(cl.getSimpleName()).append(" ");
            }
        } catch (ClassNotFoundException ex) {
            log.warning("Could not construct preferred list of denoiser methods: " + ex);
            noiseFilterClasses = defaultNoiseFilterClasses;
        }
        log.info(sb.toString());

        noiseFilterComboBoxModel = new DefaultComboBoxModel<Class<? extends AbstractNoiseFilter>>(noiseFilterClasses) {
            @Override
            public void setSelectedItem(Object noiseFilterClass) {
                super.setSelectedItem(noiseFilterClass);
                setSelectedNoiseFilter(noiseFilterClass);
            }

        };
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

        GL2 gl = drawable.getGL().getGL2();

        rocHistoryLabelPosY = (int) (.7 * chip.getSizeY());
        for (ROCHistory h : rocHistoriesSaved) {
            h.draw(gl);
        }
        for (ROCSweep s : rocSweeps) {
            s.draw(gl);
        }
        rocHistoryCurrent.draw(gl);
        if (isInitializingPreviousEvents(firstSignalEventTimestampThisPacket)) {
            gl.glPushMatrix();
            gl.glColor4f(1, 1, 1, 1);
            String s = "Initializing previous events";
            float timeleft = (correlationTimeS);
            if (firstSignalEventTimestampThisPacket != null) {
                timeleft = (correlationTimeS - 1e-6f * (firstSignalEventTimestampThisPacket - timestampAfterReset));
                s = String.format("Init events for correlationTimeS of %ss", eng.format(correlationTimeS));
            }
            Rectangle2D r = DrawGL.drawStringDropShadow(getShowFilteringStatisticsFontSize(),
                    0, sy / 2, 0f,
                    Color.white, s);
            float frac = timeleft / correlationTimeS;
            float tw = (float) (r.getWidth()), th = (float) (r.getHeight());
            gl.glLineWidth(7);
            DrawGL.drawLine(gl, 0, sy / 2 - th, frac * (sx), 0, 1);

            gl.glPopMatrix();
            return;
        }
        if (rocSweep != null && rocSweep.running) {
            gl.glPushMatrix();
            gl.glColor4f(1, 1, 1, 1);
            DrawGL.drawStringDropShadow(getShowFilteringStatisticsFontSize(),
                    chip.getSizeX(), rocHistoryLabelPosY, 1,
                    Color.white, rocSweep.toString());
            rocHistoryLabelPosY -= (2 * getShowFilteringStatisticsFontSize());

            gl.glPopMatrix();
        }

        // draw X for last packet TPR / TNR point
        float x = (1 - TNR) * sx;
        float y = TPR * sy;
        int L = 8;
        gl.glPushMatrix();
        gl.glColor4f(.8f, .8f, .8f, .3f); // must set color before raster position (raster position is like glVertex)
        gl.glLineWidth(10);
        DrawGL.drawCross(gl, x, y, L, 0);
//        DrawGL.drawString(getShowFilteringStatisticsFontSize(), x, y, 0, Color.gray, "last");
        gl.glPopMatrix();

        if (isShowFilteringStatistics()) {
            // draw overlays of TP/FP etc
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
                noiseSummaryString = String.format("NTF: Pre-recorded noise from %s with %,d events", prerecordedNoise.file.getName(), prerecordedNoise.recordedNoiseFileNoisePacket.getSize());
            } else {
                noiseSummaryString = String.format("NTF: Synthetic noise: CoV %s dec, Leak %sHz+/-%s jitter, Shot %sHz. %s", eng.format(noiseRateCoVDecades), eng.format(leakNoiseRateHz), eng.format(leakJitterFraction), eng.format(shotNoiseRateHz),
                        overlayString);
            }
            DrawGL.drawString(getShowFilteringStatisticsFontSize(), 0, getAnnotationRasterYPosition(), 0, Color.white, noiseSummaryString);

            String s = null;
            s = String.format("TPR=%-6s%% FPR=%-6s%% TNR=%-6s%% FNR=%-6s%% dT=%.2fus", eng.format(100 * TPR), eng.format(100 * (FPR)), eng.format(100 * TNR), eng.format(100 * FNR), poissonDtUs);
            DrawGL.drawString(getShowFilteringStatisticsFontSize(), 0, getAnnotationRasterYPosition("NTF"), 0, Color.white, s);
//            s = String.format("In sigRate=%-7s noiseRate=%-7s, Out sigRate=%-7s noiseRate=%-7s Hz", eng.format(inSignalRateHz), eng.format(inNoiseRateHz), eng.format(outSignalRateHz), eng.format(outNoiseRateHz));
            s = String.format("In: S/N=%-7s/%-7s Hz, SNR=%-7s ; Out: S/N=%-7s/%-7s Hz, SNR=%-7s", 
                    eng.format(inSignalRateHz), eng.format(inNoiseRateHz), eng.format(inSNR),
                    eng.format(outSignalRateHz), eng.format(outNoiseRateHz), eng.format(outSNR));
            DrawGL.drawString(getShowFilteringStatisticsFontSize(), 0, getAnnotationRasterYPosition("NTF") + 10, 0, Color.white, s);
            gl.glPopMatrix();
        }

        float sp = sy / 10;
        Rectangle2D bounds = null;
        if (isDisableAddingNoise()) {
            bounds = DrawGL.drawStringDropShadow(getShowFilteringStatisticsFontSize() * 2, sx / 2, sy / 2 + sp, .5f, Color.white, "disableAddingNoise=true");
        }
        if (isDisableDenoising()) {
            bounds = DrawGL.drawStringDropShadow(getShowFilteringStatisticsFontSize() * 2, sx / 2, sy / 2 - (float) (bounds != null ? bounds.getHeight() : 0), .5f, Color.white, "disableDenoising=true");
        }
        if (isDisableSignal()) {
            bounds = DrawGL.drawStringDropShadow(getShowFilteringStatisticsFontSize() * 2, sx / 2, sy / 2 - (float) (bounds != null ? bounds.getHeight() : 0), .5f, Color.white, "disableSignal=true");
        }

    }

    private void annotateNoiseFilteringEvents(ArrayList<BasicEvent> outSig, ArrayList<BasicEvent> outNoise) {
        if (renderer == null) {
            return;
        }
        NOISE_COLOR[3] = getOverlayAlpha();
        SIG_COLOR[3] = getOverlayAlpha();
        for (BasicEvent e : outSig) {
            renderer.setAnnotateColorRGBA(e.x + LABEL_OFFSET_PIX >= sx ? e.x : e.x + LABEL_OFFSET_PIX, e.y - LABEL_OFFSET_PIX < 0 ? e.y : e.y - LABEL_OFFSET_PIX, SIG_COLOR);
        }
        for (BasicEvent e : outNoise) {
            renderer.setAnnotateColorRGBA(e.x + LABEL_OFFSET_PIX >= sx ? e.x : e.x + LABEL_OFFSET_PIX, e.y - LABEL_OFFSET_PIX < 0 ? e.y : e.y - LABEL_OFFSET_PIX, NOISE_COLOR);
        }
    }

    private void annotateNoiseFilteringEvents(ArrayList<BasicEvent> events, float[] color) {
        if (renderer == null) {
            return;
        }
        for (BasicEvent e : events) {
            renderer.setAnnotateColorRGBA(e.x + LABEL_OFFSET_PIX >= sx ? e.x : e.x + LABEL_OFFSET_PIX, e.y - LABEL_OFFSET_PIX < 0 ? e.y : e.y - LABEL_OFFSET_PIX, color);
        }
    }

    private boolean isInitializingPreviousEvents(Integer eventTimestamp) {
        if (eventTimestamp == null || eventTimestamp - timestampAfterReset < correlationTimeS * 1e6f) {
            return true;
        } else {
            return false;
        }
    }

    private class BackwardsTimestampException extends Exception {

        public BackwardsTimestampException(String string) {
            super(string);
        }

    }

    private boolean checkStopMe(String where) {
        if (stopMe) {
            log.severe(String.format("After processing step %s \n: took longer than maxProcessingTimeLimitMs=%,dms, disabling NoiseTesterFilter", where, maxProcessingTimeLimitMs));
            setFilterEnabled(false);
            return true;
        }
        return false;
    }

    /**
     * UNUSED now Finds the intersection of events in a that are in b. Assumes
     * packets are non-monotonic in timestamp ordering. Handles duplicates. Each
     * duplicate is matched once. The matching is by event .equals() method.
     *
     * @param a ArrayList<PolarityEvent> of a
     * @param b likewise, but is list of events with NNb bits in byte
     * @param intersect the target list to fill with intersections, include NNb
     * bits
     * @return count of intersections
     */
    private int countIntersect(ArrayList<PolarityEvent> a, ArrayList<BasicEvent> b, ArrayList<BasicEvent> intersect) {
        intersect.clear();
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int count = 0;

        int i = 0, j = 0;
        final int na = a.size(), nb = b.size();
        while (i < na && j < nb) {
            if (a.get(i).timestamp < b.get(j).timestamp) {
                i++;
            } else if (b.get(j).timestamp < a.get(i).timestamp) {
                j++;
            } else {
                // If timestamps equal, it mmight be identical events or maybe not
                // and there might be several events with identical timestamps.
                // We MUST match all a with all b.
                // We don't want to increment both pointers or we can miss matches.
                // We do an inner double loop for exhaustive matching as long as the timestamps
                // are identical. 
                int i1 = i, j1 = j;
                while (i1 < na && j1 < nb && a.get(i1).timestamp == b.get(j1).timestamp) {
                    boolean match = false;
                    while (j1 < nb && i1 < na && a.get(i1).timestamp == b.get(j1).timestamp) {
                        if (a.get(i1).equals(b.get(j1))) {
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
        if (in != null && !in.isEmpty()) {
            firstSignalEventTimestampThisPacket = in.getFirstTimestamp();
        }

        int TP = 0; // filter take real events as real events. the number of events
        int TN = 0; // filter take noise events as noise events
        int FP = 0; // filter take noise events as real events
        int FN = 0; // filter take real events as noise events

        if (in == null || in.isEmpty()) {
//            log.warning("empty packet, cannot inject noise");
            return in;
        }

        // set a stopMe boolean false that is set true by a scheduled Timer task that executes maxProcessingTimeLimitMs after event processing starts.
        // we check the stopMe flag periodically with checkStopMe() to disable NTF if something takes too long to process
        stopMe = false;
        if (!isDebug) {
            if (stopperTask != null) {
                stopperTask.cancel();
                stopperTask = null;
            }
            stopperTask = new TimerTask() {
                @Override
                public void run() {
//                    if (System.currentTimeMillis() - scheduledExecutionTime()
//                            >= maxProcessingTimeLimitMs) {
//                        return;  // Too late; skip this execution.
//                    }
                    stopMe = true;
                }
            };
            stopper.schedule(stopperTask, maxProcessingTimeLimitMs);
        }
        BasicEvent firstE = in.getFirstEvent();
        if (firstSignalTimestmapAfterReset == null) {
            firstSignalTimestmapAfterReset = firstE.timestamp;
        }
        float deltaTimeS = lastTimestampPreviousPacket != null ? 1e-6f * (in.getLastTimestamp() - lastTimestampPreviousPacket) : Float.NaN;

        if (resetCalled) {
            resetCalled = false;
            timestampAfterReset = in.getFirstTimestamp(); // we use getLastTimestamp because getFirstTimestamp contains event from BEFORE the rewind :-( Or at least it used to, fixed now I think (Tobi)
            initializeLastTimesMapForNoiseRate(shotNoiseRateHz + leakNoiseRateHz, timestampAfterReset);
            // initialize filters with lastTimesMap to Poisson waiting times
            log.fine("initializing timestamp maps with Poisson process waiting times");
            for (AbstractNoiseFilter f : noiseFilters) {
                if (f == null || !f.isFilterEnabled()) {
                    continue;
                }
                f.initializeLastTimesMapForNoiseRate(shotNoiseRateHz + leakNoiseRateHz, timestampAfterReset); // TODO move to filter so that each filter can initialize its own map
            }
            initializeLeakStates(in.getFirstTimestamp());
        }

        // add noise into signalList to get the outputPacketWithNoiseAdded, track noise in noiseList
        signalPacket.copySignalEventsFrom(in);
        signalPacket.countClassifications(false);
        inSignalRateHz = (signalNoisePacket.signalCount) / deltaTimeS;
        inNoiseRateHz = (signalNoisePacket.noiseCount) / deltaTimeS;
        inSNR=(float)signalNoisePacket.signalCount/signalNoisePacket.noiseCount;
        if (!isDisableAddingNoise()) {
            addNoise(signalPacket, signalNoisePacket, noiseList, shotNoiseRateHz, leakNoiseRateHz);
        } else {
            addNoise(signalPacket, signalNoisePacket, noiseList, 0, 0);
        }

//        signalNoisePacket.countClassifications(false);  // debug
        if (outputTrainingData && csvWriter != null) {

            for (SignalNoiseEvent event : signalNoisePacket) { // denoising disabled so this gets all DVS events
                try {
                    int ts = event.timestamp;
                    byte type = event.getPolarity() == PolarityEvent.Polarity.Off ? (byte) -1 : (byte) 1;
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
                            byte pol = 0;
                            if ((x + indx >= 0) && (x + indx < sx) && (y + indy >= 0) && (y + indy < sy)) {
                                absTs = timestampImage[x + indx][y + indy];
                                pol = polImage[x + indx][y + indy];
                            }
                            absTstring.append(absTs + ",");
                            polString.append(pol + ",");

                        }
                    }
                    if (recordPureNoise) { // if pure noise, labels must reversed otherwise the events will be labeled as signal
                        if (event.isLabeledAsSignal()) {
                            csvWriter.write(String.format("%d,%d,%d,%d,%d,%s%s%d\n",
                                    type, event.x, event.y, event.timestamp, 0, absTstring, polString, firstE.timestamp)); // 1 means signal
                        } else {
                            csvWriter.write(String.format("%d,%d,%d,%d,%d,%s%s%d\n",
                                    type, event.x, event.y, event.timestamp, 1, absTstring, polString, firstE.timestamp)); // 0 means noise
                            csvNoiseCount++;
                            csvNumEventsWritten++;
                        }
                    } else {
                        if (event.isLabeledAsSignal()) {
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
                    polImage[x][y] = type;
                    if (csvNumEventsWritten % 100000 == 0) {
                        log.info(String.format("Wrote %,d events to %s", csvNumEventsWritten, csvFileName));
                    }
                } catch (IOException e) {
                    doToggleOffSaveMLPTraining();
                }
            }
        }

        if (selectedNoiseFilter != null && !disableDenoising) {
//            signalNoisePacket.countClassifications(false); // TODO debug
            signalNoisePacket = (SignalNoisePacket) getEnclosedFilterChain().filterPacket(signalNoisePacket);
//            signalNoisePacket.countClassifications(false); // TODO debug
            // if selectedNoiseFilter is null, nothing happens here
            if (isInitializingPreviousEvents(in.getFirstTimestamp())) {
                // we let the first packet be filtered to populated the history with
                // signal events
                if (renderer != null) {
                    renderer.clearAnnotationMap();
                }
                return signalNoisePacket;
            }

            // now we sort out the mess
            boolean collectLabeledEvents = overlayFN || overlayFP | overlayNegatives
                    | overlayPositives | overlayTN | overlayTP;
            signalNoisePacket.countClassifications(collectLabeledEvents);
            TP = signalNoisePacket.tpCount;
            TN = signalNoisePacket.tnCount;
            FP = signalNoisePacket.fpCount;
            FN = signalNoisePacket.fnCount;

            assert (TN + FP == noiseList.size()) : String.format("TN (%d) + FP (%d) = %d != noiseList (%d)", TN, FP, TN + FP, noiseList.size());
            totalEventCount = signalNoisePacket.getSize();
            int outputEventCount = signalNoisePacket.tpCount + signalNoisePacket.fpCount;
            filteredOutEventCount = totalEventCount - outputEventCount;

            assert TP + FP == outputEventCount : String.format("TP (%d) + FP (%d) = %d != outputEventCount (%d)", TP, FP, TP + FP, outputEventCount);
            assert TP + TN + FP + FN == totalEventCount : String.format("TP (%d) + TN (%d) + FP (%d) + FN (%d) = %d != totalEventCount (%d)", TP, TN, FP, FN, TP + TN + FP + FN, totalEventCount);
            assert TN + FN == filteredOutEventCount : String.format("TN (%d) + FN (%d) = %d  != filteredOutEventCount (%d)", TN, FN, TN + FN, filteredOutEventCount);

            TPR = TP + FN == 0 ? 0f : (float) (TP * 1.0 / (TP + FN)); // percentage of true positive events. that's output real events out of all real events
            FPR = 1 - TPR;

            TNR = TN + FP == 0 ? 0f : (float) (TN * 1.0 / (TN + FP));
            FNR = 1 - TNR;

            TPO = TP + FP == 0 ? 0f : (float) (TP * 1.0 / (TP + FP)); // percentage of real events in the filter's output
            accuracy = (float) ((TP + TN) * 1.0 / (TP + TN + FP + FN));

            BR = TPR + TPO == 0 ? 0f : (float) (2 * TPR * TPO / (TPR + TPO)); // wish to norm to 1. if both TPR and TPO is 1. the value is 1
        }
        outSignalRateHz = ((TP)) / deltaTimeS;
        outNoiseRateHz = ((FP)) / deltaTimeS;
        outSNR=(float)TP/FP;

        if (!isDebug) {
            boolean ranStopper = stopperTask.cancel();
        }

        if (outputFilterStatistic && csvWriter != null) {
            try {
                csvWriter.write(String.format("%d,%d,%d,%d,%f,%f,%f,%d,%f,%f,%f,%f\n",
                        TP, TN, FP, FN, TPR, TNR, BR, firstE.timestamp,
                        inSignalRateHz, inNoiseRateHz, outSignalRateHz, outNoiseRateHz));
            } catch (IOException e) {
                doToggleOffSaveMLPTraining();
            }
        }

        if (renderer != null) {
            renderer.clearAnnotationMap();
        }
        if (overlayPositives) {
            annotateNoiseFilteringEvents(signalNoisePacket.tpList, signalNoisePacket.fpList);
        }
        if (overlayNegatives) {
            annotateNoiseFilteringEvents(signalNoisePacket.fnList, signalNoisePacket.tnList);
        }
        NOISE_COLOR[3] = getOverlayAlpha();
        SIG_COLOR[3] = getOverlayAlpha();
        if (overlayTP) {
            annotateNoiseFilteringEvents(signalNoisePacket.tpList, SIG_COLOR);
        }
        if (overlayTN) {
            annotateNoiseFilteringEvents(signalNoisePacket.tnList, NOISE_COLOR);
        }
        if (overlayFP) {
            annotateNoiseFilteringEvents(signalNoisePacket.fpList, NOISE_COLOR);
        }
        if (overlayFN) {
            annotateNoiseFilteringEvents(signalNoisePacket.fnList, SIG_COLOR);
        }

        rocHistoryCurrent.addSample(1 - TNR, TPR);

        lastTimestampPreviousPacket = signalNoisePacket.getLastTimestamp();
        return signalNoisePacket;
//        } catch (BackwardsTimestampException ex) {
//            Logger.getLogger(NoiseTesterFilter.class.getName()).log(Level.SEVERE, null, ex);
//            return in;
//        }

    }

    /**
     * Adds the recorded or synthetic noise to packet.
     *
     * @param in the input packet
     * @param signalNoisePacket the packet with noise added
     * @param generatedNoise list of noise events
     * @param shotNoiseRateHz noise rate for shot noise, per pixel
     * @param leakNoiseRateHz noise rate for leak noise, per pixel
     */
    private void addNoise(SignalNoisePacket signalPacket, SignalNoisePacket signalNoisePacket, ArrayList<PolarityEvent> generatedNoise, float shotNoiseRateHz, float leakNoiseRateHz) {

        // we need at least 1 event to be able to inject noise before it
        if ((signalPacket.isEmpty())) {
            log.warning("no input events in this packet, cannot inject noise because there is no end event");
            signalNoisePacket.copySignalEventsFrom(signalPacket);
            return;
        }

        // save input packet
        signalNoisePacket.clear();
        generatedNoise.clear();
        // make the itertor to save events with added noise events
        OutputEventIterator<SignalNoiseEvent> outItr = (OutputEventIterator) signalNoisePacket.outputIterator();
        if (prerecordedNoise == null && leakNoiseRateHz == 0 && shotNoiseRateHz == 0) {
            signalNoisePacket.copySignalEventsFrom(signalPacket);
            return; // no noise, just return which returns the copy from filterPacket
        }

        int firstTsThisPacket = signalPacket.getFirstTimestamp();
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
        int lastEventTs = signalPacket.getFirstTimestamp();
        for (SignalNoiseEvent ie : signalPacket) {
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
        if (dvsEventCounter == 0 && (signalPacket instanceof ApsDvsEventPacket)) {
            Iterator itr = ((ApsDvsEventPacket) signalPacket).fullIterator();
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

//        signalNoisePacket.countClassifications(false); // TODO debug
        if (isDisableSignal()) {
            signalNoisePacket.removeSignalEvents();
        }
    }

    private void insertNoiseEvents(int lastPacketTs, int firstTsThisPacket, OutputEventIterator<SignalNoiseEvent> outItr, List<PolarityEvent> generatedNoise) {
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
    private int sampleNoiseEvent(int ts, OutputEventIterator<SignalNoiseEvent> outItr, List<PolarityEvent> noiseList, float shotOffThresholdProb, float shotOnThresholdProb, float leakOnThresholdProb) {
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
                SignalNoiseEvent le = leakNoiseQueue.peek();
                while (le != null && ts >= le.timestamp) {
                    le = leakNoiseQueue.poll();
                    le.timestamp = ts;
                    SignalNoiseEvent eout = outItr.nextOutput();
                    eout.copyFrom(le);
                    eout.labelAsSignal(false);
                    eout.unclassify();
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
                SignalNoiseEvent ecopy = outItr.nextOutput(); // get the next event from output packet
                if (ecopy instanceof ApsDvsEvent) {
                    ((ApsDvsEvent) ecopy).setReadoutType(ApsDvsEvent.ReadoutType.DVS);
                }
                ecopy.copyFrom(e); // copy its fields from the noise event
                ecopy.timestamp = ts; // update the timestamp to the current timestamp
                ecopy.labelAsSignal(false);
                ecopy.unclassify();
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
            SignalNoiseEvent pe = new SignalNoiseEvent();
            pe.x = xy.x;
            pe.y = xy.y;
            pe.polarity = Polarity.On;
            int t = random.nextInt((int) (1e6f / leakNoiseRateHz));
            pe.timestamp = ts + t;
            pe.labelAsSignal(false);
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
    private void createOrUpdateNoiseVariance() {

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
    private void injectShotNoiseEvent(int ts, PolarityEvent.Polarity pol, OutputEventIterator<SignalNoiseEvent> outItr, List<PolarityEvent> noiseList) {
        SignalNoiseEvent e = outItr.nextOutput();
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
        e.type = (pol == Polarity.Off ? (byte) 0 : (byte) 1);
        e.labelAsSignal(false);  // this is noise event
        e.unclassify();
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
        firstSignalEventTimestampThisPacket = null;
        firstSignalTimestmapAfterReset = null;
        resetCalled = true;
        getEnclosedFilterChain().reset();
        if (prerecordedNoise != null) {
            prerecordedNoise.rewind();
        }
        for (int[] arrayRow : timestampImage) {
            Arrays.fill(arrayRow, DEFAULT_TIMESTAMP);
        }
//        rocHistoryCurrent.reset(); // do not reset, otherwise the saved point that is average of these samples is cleared
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

        if (chain == null) {
            chain = new FilterChain(chip);

//            noiseFilterClasses = findDenoiserClasses();
            // construct the noise filters
            noiseFilters = new AbstractNoiseFilter[noiseFilterClasses.length];
            int i = 0;
            ArrayList<Class> evictedFilters = new ArrayList();
            for (Class cl : noiseFilterClasses) {
                try {
                    Constructor con = cl.getConstructor(AEChip.class);
                    noiseFilters[i] = (AbstractNoiseFilter) con.newInstance(chip);
                    i++;
                } catch (ClassCastException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    String s = String.format("Could not construct instance of AbstractNoiseFilter %s;\ngot %s", cl.getSimpleName(), ex.toString());
                    log.severe(s);
                    evictedFilters.add(cl);
                }
            }
            if (!evictedFilters.isEmpty()) {
                showWarningDialogInSwingThread(String.format("NoiseTesterFilter could not construct instance(s) of %s", evictedFilters.toString()), "NoiseTesterFilter");
                ArrayList<Class> tmp = new ArrayList();
                for (Class cl : noiseFilterClasses) {
                    if (!evictedFilters.contains(cl)) {
                        tmp.add(cl);
                    }
                }
                noiseFilterClasses = new Class[tmp.size()];
                int k = 0;
                for (Class cl : tmp) {
                    noiseFilterClasses[k++] = cl;
                }
            }

            for (AbstractNoiseFilter n : noiseFilters) {
                if (n == null) {
                    continue;
                }
//                n.initFilter(); // already done by FilterChain
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

            // noise filter combo box
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
        polImage = new byte[chip.getSizeX()][chip.getSizeY()];

        timestampImage = new int[sx + 1][sy + 1];
        leakNoiseQueue = new PriorityQueue(chip.getNumPixels());
        signalAndNoisePacket = new EventPacket(ApsDvsEvent.class);
//        setSelectedNoiseFilterEnum(selectedNoiseFilterEnum);
        computeProbs();
        createOrUpdateNoiseVariance();
        //        setAnnotateAlpha(annotateAlpha);
        fixRendererAnnotationLayerShowing(); // make sure renderer is properly set up.

    }

    private void constructRocSweepParameterComboBoxModel() {
        // ROC sweep parameter combo box
        try {
            // build combo box for selecting swept parameter
            BeanInfo info = Introspector.getBeanInfo((Class) selectedNoiseFilter.getClass());
            PropertyDescriptor[] props = info.getPropertyDescriptors();
            ArrayList<String> propArray = new ArrayList();
            for (PropertyDescriptor p : props) {
                if (p.getName().equals("showFilteringStatisticsFontSize")) {
                    continue;
                }
                if (p.getPropertyType() == Float.TYPE || p.getPropertyType() == Integer.TYPE) {
                    propArray.add(p.getName());
                }
            }

            // remove the minX and maxX if X exists, since these set bounds on sliders
            ArrayList<String> removeList = new ArrayList();
            for (String s : propArray) {
                String upper = s.substring(0, 1).toUpperCase();
                String remainder = s.substring(1);
                String maxName = "max" + upper + remainder, minName = "min" + upper + remainder;
                if (propArray.contains(maxName) && propArray.contains(minName)) {
                    log.fine("not including slider parameters " + maxName + " and " + minName + " for property " + s);
                    removeList.add(maxName);
                    removeList.add(minName);
                }
                if (selectedNoiseFilter.isPropertyHidden(s)) {
                    log.fine("not including hidden parameter " + s);
                    removeList.add(s);
                }

            }
            propArray.removeAll(removeList);

            DefaultComboBoxModel model = (DefaultComboBoxModel) rocSweepParameterComboBoxModel;
            model.removeAllElements();
            model.addAll(propArray);
            model.setSelectedItem(getRocSweepPropertyName());
            try {
                setRocSweepPropertyName(getRocSweepPropertyName());
            } catch (IntrospectionException | MethodNotFoundException ex) {
                log.warning(String.format("Cannot set %s as the swept property for ROC sweep for selected method %s", getRocSweepPropertyName(), selectedNoiseFilter.getClass().getSimpleName()));
            }

        } catch (IntrospectionException e) {
            log.warning("could not make combo box for selecting swept parameter: " + e.toString());
        }
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
//        doToggleOnSaveMLPTraining();
    }

    synchronized public void doToggleOffSaveMLPTraining() {
        if (csvFile != null) {
            try {
                log.fine("closing MLPF CSV output file" + csvFile.getAbsoluteFile());
                csvWriter.close();
                float snr = (float) csvSignalCount / (float) csvNoiseCount;
                String m = String.format("closed CSV output file %s with %,d events (%,d signal events, %,d noise events, SNR=%.3g", csvFile, csvNumEventsWritten, csvSignalCount, csvNoiseCount, snr);
                showPlainMessageDialogInSwingThread(m, "CSV file closed");
                showFolderInDesktop(csvFile);
                log.info(m);
            } catch (IOException e) {
                log.warning("could not close CSV output file " + csvFile.getAbsoluteFile() + ": caught " + e.toString());
            } finally {
                csvFile = null;
                csvWriter = null;
                setDisableDenoising(false);
                setOutputTrainingData(false);
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

    synchronized public void doToggleOnSaveMLPTraining() {
        csvNumEventsWritten = 0;
        csvSignalCount = 0;
        csvNoiseCount = 0;
        String fn = csvFileName + ".csv";
        csvFile = new File(fn);
        boolean exists = csvFile.exists();
        log.info(String.format("opening %s for CSV training output", csvFile.getAbsoluteFile()));
        try {
            csvWriter = new BufferedWriter(new FileWriter(csvFile, true), 100_000_000);
            if (!exists) { // write header
                log.info("file " + csvFile.getAbsoluteFile() + " did not exist, so writing header");
                if (outputTrainingData) {
                    log.info("writing header for MLPF training file");
                    csvWriter.write(String.format("#MLPF training data\n# type, event.x, event.y, event.timestamp,signal/noise(1/0), nnbTimestamp(25*25), nnbPolarity(25*25), packetFirstEventTimestamp\n"));
                } else if (outputFilterStatistic) {
                    log.info("writing header for filter accuracy statistics file");
                    csvWriter.write(String.format("TP,TN,FP,FN,TPR,TNR,BR,firstE.timestamp,"
                            + "inSignalRateHz,inNoiseRateHz,outSignalRateHz,outNoiseRateHz\n"));
                }
            }
            setDisableDenoising(true);
            setOutputTrainingData(true);
        } catch (IOException ex) {
            log.warning(String.format("could not open %s for output; caught %s", csvFile.getAbsoluteFile(), ex.toString()));
        } finally {
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
        boolean old = this.outputTrainingData;
        this.outputTrainingData = outputTrainingData;
        getSupport().firePropertyChange("outputTrainingData", old, outputTrainingData);
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
        setHideNonEnabledEnclosedFilters(!isEnableMultipleMethods()); // only show the enabled noise filter
        if (selectedNoiseFilter != null) {
            setSelectedNoiseFilter(selectedNoiseFilter.getClass());
        }
        getSupport().addPropertyChangeListener("enableMultipleMethods", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                setHideNonEnabledEnclosedFilters(!isEnableMultipleMethods());
            }
        });

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
                    doToggleOffSaveMLPTraining();
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
        if (rocHistoryLength > 256) {
            rocHistoryLength = 256;
        } else if (rocHistoryLength < 1) {
            rocHistoryLength = 1;
        }
        this.rocHistoryLength = rocHistoryLength;
        putInt("rocHistoryLength", rocHistoryLength);
        rocHistoryCurrent.setLength(rocHistoryLength);
        getSupport().firePropertyChange("rocHistoryLength", old, this.rocHistoryLength);
    }

    @Preferred
    synchronized public void doChooseDenoisers() {
        ArrayList<String> defaultDenoiserClassNames = new ArrayList();
        for (Class cl : defaultNoiseFilterClasses) {
            defaultDenoiserClassNames.add(cl.getName());
        }
        ArrayList<String> currentDenoiserClassNames = new ArrayList();
        for (Class cl : noiseFilterClasses) {
            currentDenoiserClassNames.add(cl.getName());
        }
        ClassChooserDialog chooser = new ClassChooserDialog(chip.getFilterFrame(),
                AbstractNoiseFilter.class,
                currentDenoiserClassNames,
                defaultDenoiserClassNames);
        chooser.setLocationRelativeTo(chip.getFilterFrame());
        chooser.setVisible(true);
        if (chooser.getReturnStatus() == ClassChooserDialog.RET_OK) {
            ArrayList<String> newClassNames = chooser.getList();
            ArrayList<String> newClassnamesPlainString = new ArrayList();
            for (Object o : newClassNames) {
                if (o instanceof String s) {
                    newClassnamesPlainString.add(s);
                } else if (o instanceof ClassNameWithDescriptionAndDevelopmentStatus s) {
                    newClassnamesPlainString.add(s.getClassName());
                }
            }
            ArrayList<Class> newClassList = new ArrayList();
            for (String denoiserClassname : newClassnamesPlainString) {
                if (denoiserClassname.equals(NoiseTesterFilter.class.getName())) {
                    continue; // don't add NTF to denoiser list!
                }
                try {
                    Class cl = Class.forName(denoiserClassname);

                    newClassList.add(cl);
                } catch (ClassNotFoundException ex) {
                    log.warning("Cannot find class for " + denoiserClassname);
                }
            }
            Class[] denoiserClassArray = new Class[newClassList.size()];
            int i = 0;
            for (Class cl : newClassList) {
                denoiserClassArray[i++] = cl;
            }
            log.info("Found " + denoiserClassArray.length + " denoisers");
            noiseFilterClasses = denoiserClassArray;

            noiseFilterComboBoxModel = new DefaultComboBoxModel<Class<? extends AbstractNoiseFilter>>(noiseFilterClasses) {
                @Override
                public void setSelectedItem(Object noiseFilterClass) {
                    super.setSelectedItem(noiseFilterClass);
                    setSelectedNoiseFilter(noiseFilterClass);
                }

            };
            try {
                putObject("preferredDenoiserClassNames", newClassnamesPlainString);
                if (chip.getFilterFrame() == null) {
                    log.warning(chip + " has no FilterFrame, cannot renew contents");
                } else {
                    chip.getFilterFrame().renewContents();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Preferred
    synchronized public void doToggleOnROCSweep() {
        rocSweep = new ROCSweep(selectedNoiseFilter);
        rocSweep.start();
    }

    @Preferred
    synchronized public void doToggleOffROCSweep() {
        rocSweep.stop();
    }

    @Preferred
    synchronized public void doResampleFPN() {
        computeProbs();
        createOrUpdateNoiseVariance();
    }

    @Preferred
    synchronized public void doExportROCs() {
        try {
            rocSweep.saveToCSVs();
        } catch (FileNotFoundException ex) {
            log.severe(ex.toString());
            JOptionPane.showMessageDialog(chip.getFilterFrame(), "Error saving CSVs: " + ex.toString(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    synchronized public void doToggleOffNoiseRecording() {
        if (prerecordedNoise != null) {
            log.info("clearing recoerded noise input data");
            prerecordedNoise = null;
        }
    }

    synchronized public void doToggleOnUseNoiseRecording() {
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
        createOrUpdateNoiseVariance();
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
     * @return the overlayAlpha
     */
    public float getOverlayAlpha() {
        return overlayAlpha;
    }

    /**
     * @param overlayAlpha the overlayAlpha to set
     */
    public void setOverlayAlpha(float overlayAlpha) {
        if (overlayAlpha < 0) {
            overlayAlpha = 0;
        } else if (overlayAlpha > 1) {
            overlayAlpha = 1;
        }
        this.overlayAlpha = overlayAlpha;
        putFloat("overlayAlpha", overlayAlpha);
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
        private String rocSweepPropertyName = null; // not stored in properties
        float currentValue;
        boolean running = false;
        boolean firstStep = true;  // cleared afer initial sweep value is checked
        Method setter = null, getter = null;
        boolean floatType = true;
//        ROCHistory rocHistorySummary = null;
        MultivariateSummaryStatistics stats = new MultivariateSummaryStatistics(2, false);
        float startingValue = .1f;
        int stepsCompleted = 0;
        ArrayList<ROCSample> samples = new ArrayList();

        private HashMap<String, ROCSweepParams> rocSweepParamesHashMap = null;
        private boolean installedPropertyChangeListeners = false;
        private int savedRocHistoryLength = getRocHistoryLength();
        private long estimateNumSteps;
        private float auc = Float.NaN;
        private Boolean clockwise = null;
        private Color color;
        private String noiseFilterInfo = "(null)";
        private String noiseFilterName = "(null)";

        private void draw(GL2 gl) {

            // draw each sample as a square point
            for (ROCSample rocSample : samples) {
                rocSample.draw(gl, color, 12);
            }
            if (noiseFilterInfo != null) {
                DrawGL.drawString(getShowFilteringStatisticsFontSize(),
                        chip.getSizeX(), rocHistoryLabelPosY, 1, color,
                        noiseFilterInfo + String.format(" AUC=%.3f", auc));
            }
            rocHistoryLabelPosY -= 1.8f * getShowFilteringStatisticsFontSize();
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
            for (ROCSample rocSample : samples) {
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

        public ROCSweep(AbstractNoiseFilter noiseFilter) {
            if (noiseFilter != null) {
                noiseFilterInfo = noiseFilter.infoString();
                noiseFilterName = noiseFilter.getClass().getSimpleName();
            }
            if (chip.getAeViewer() != null && !installedPropertyChangeListeners) {
                chip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
                chip.getAeViewer().getSupport().addPropertyChangeListener(AEInputStream.EVENT_INIT, this);
                installedPropertyChangeListeners = true;
            }
            running = false;
            firstStep = true;
            currentValue = stepSign() > 0 ? rocSweepStart : rocSweepEnd;
            try {
                rocSweepParamesHashMap = (HashMap<String, ROCSweepParams>) getObject("rocSweepParamesHashMap", new HashMap());
            } catch (ClassCastException | NullPointerException e) {
                log.log(Level.INFO, "No existing ROCSweepParams: {0}", e.toString());
            }
            loadParams();
            reset();
            computeEstimatedNumSteps();

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

        private void loadParams() {
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
                    } catch (IntrospectionException | org.jdesktop.el.MethodNotFoundException ex) {
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
                return String.format("ROC Sweep: %s start:%s end:%s step:%s currentValue:%s (step %d/%d)",
                        rocSweepLogStep ? "log" : "linear",
                        eng.format(rocSweepStart), eng.format(rocSweepEnd), eng.format(rocSweepStep), eng.format(currentValue),
                        stepsCompleted, estimateNumSteps);
            } else {
                return "ROC sweep (not running)";
            }
        }

        final void init() {
        }

        void saveToCSVs() throws FileNotFoundException {
            if (!SwingUtilities.isEventDispatchThread()) {
                log.warning("can only be called from Swing/AWT thread");
                return;
            }
            if (rocSweeps.isEmpty()) {
                JOptionPane.showMessageDialog(chip.getFilterFrame(), "No ROC sweeps to save");
                return;
            }
            // TODO 
            String lastFolder = getString("lastROCCSVFolder", System.getProperty("user.dir"));
            JFileChooser fileChooser = new JFileChooser("Export ROC curves to CSV files");
            fileChooser.setApproveButtonToolTipText("Select folder and base name for CSV files (not including extension)");
            fileChooser.setToolTipText("Browse to desired folder and enter base name for CSV files (not including extension)");
            File sf = new File(lastFolder);
            fileChooser.setCurrentDirectory(sf);
//            fileChooser.setSelectedFile(sf);
            fileChooser.setMultiSelectionEnabled(false);
            int retValue = fileChooser.showSaveDialog(getChip().getFilterFrame());
            if (retValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                String folder = selectedFile.getParent();
                putString("lastROCCSVFolder", folder);
                ArrayList<String> filenames = new ArrayList();
                int filenum = 0;
                outer:
                for (ROCSweep sweep : rocSweeps) {
                    final String prefix = selectedFile.getName();
                    final String simpleName = sweep.noiseFilterName;
                    final String propName = sweep.rocSweepPropertyName;
                    Path path = Path.of(folder, prefix + "-ROCSweep-" + simpleName + "-" + propName + "-" + filenum + ".csv");
                    File outputfile = path.toFile();
                    if (outputfile.exists()) {
                        int overwrite = JOptionPane.showConfirmDialog(fileChooser, outputfile.toString() + " exists, overwrite?");
                        if (overwrite == JOptionPane.NO_OPTION) {
                            continue;
                        } else if (overwrite == JOptionPane.CANCEL_OPTION) {
                            break outer;
                        }
                    }
                    try (PrintWriter writer = new PrintWriter(outputfile)) {
                        writer.println(String.format("# ROC sweep of parameter %s for filter %s", propName, simpleName));
                        writer.println("# created " + new Date().toString());
                        writer.println("# NoiseTesterFilter noise: " + noiseSummaryString);
                        writer.println("# source-file: " + (chip.getAeInputStream() != null ? chip.getAeInputStream().getFile().toString() : "(live input)"));
                        if (chip.getAeInputStream() != null) {
                            String marks = String.format("# Marks: IN=%,d OUT=%,d", chip.getAeInputStream().getMarkInPosition(), chip.getAeInputStream().getMarkOutPosition());
                            writer.println(marks);
                        }
                        writer.println("# Computed ROC Sweep AUC=" + sweep.auc);
                        writer.println(String.format("%s, FPR, TPR", propName));
                        for (ROCSample s : sweep.samples) {
                            writer.println(String.format("%f,%f,%f", s.param, s.x, s.y));
                        }
                    }
                    filenames.add(path.toString());
                    filenum++;
                }
                if (!filenames.isEmpty()) {
                    StringBuilder sb = new StringBuilder("<html>Saved CSV files");
                    for (String s : filenames) {
                        sb.append("<br>").append(s);
                    }
                    ShowFolderSaveConfirmation d
                            = new ShowFolderSaveConfirmation(
                                    chip.getFilterFrame(),
                                    selectedFile,
                                    sb.toString());
                    d.setVisible(true);
                }
            }
        }

        void start() {
            if (chip.getAeViewer().getPlayMode() == AEViewer.PlayMode.PLAYBACK) {
                chip.getAeViewer().getAePlayer().rewind();
            }
            rocHistoryCurrent.reset();
            savedRocHistoryLength = getRocHistoryLength(); // to restore on stop()
            rocHistoryLength = 1000; //sufficient for whole marked section of recording
//            rocHistorySummary = new ROCHistory(selectedNoiseFilter);
//            rocHistorySummary.summary = true;
            color = selectedNoiseFilter == null ? Color.white : new Color(colors[lastcolor % colors.length]);
            lastcolor += 1;

            rocSweeps.add(this);

            running = true;
            stepsCompleted = 0;
            getStartingValue();
        }

        private void getStartingValue() {
            if (getter != null) {
                try {
                    Object o = getter.invoke(selectedNoiseFilter);
                    if (o instanceof Float) {
                        startingValue = (float) o;
                    } else if (o instanceof Integer) {
                        int i = (int) o;
                        startingValue = (float) i;
                    } else {
                        log.warning("can't restore starting value of swept parameter, return value is not int or float. Returned object is " + o);
                    }
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    Logger.getLogger(NoiseTesterFilter.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        private void restoreStartingSweptValue() {
            if (setter != null) {
                try {
                    if (setter.getParameterTypes()[0] == Float.TYPE) {
                        setter.invoke(selectedNoiseFilter, startingValue);
                    } else if (setter.getParameterTypes()[0] == Integer.TYPE) {
                        int i = Math.round(startingValue);
                        setter.invoke(selectedNoiseFilter, i);
                    } else {
                        log.warning("cannot restore starting value of swept parameter; setter argument type is not float or int");
                    }
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    Logger.getLogger(NoiseTesterFilter.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        void stop() {
            if (running) {
                restoreStartingSweptValue();
            }
            running = false;
            setRocHistoryLength(savedRocHistoryLength);
            getSupport().firePropertyChange("doToggleOffROCSweep", true, false);
        }

        void endAndSave() {
            stop();
            rocHistoryCurrent.reset();
        }

        private void reset() {
            currentValue = rocSweepStart;
        }

        boolean step() {
            if (!running) {
                return false;
            }
            // first save summary statistic for last step in private rocHistorySummary
            ROCSample avg = rocHistoryCurrent.computeAvg();
            avg.param = currentValue;
            if (!Float.isNaN(avg.x) && !Float.isNaN(avg.y)) {
                samples.add(avg);
                computeAUC();
            }
            rocHistoryCurrent.reset();

            boolean done = updateCurrentValue();
            if (!done) {
                stepsCompleted++;
            }
            return done;
        }

        private int stepSign() {
            if (rocSweepEnd > rocSweepStart) {
                return +1;
            } else {
                return -1;
            }
        }

        float computeAUC() {
            this.auc = 0;
            int n = samples.size();
            float lastx = 0, lasty = 0;
            if (n < 2) {
                return Float.NaN;
            }
            ROCSample first = null;
            ROCSample last = null;
            for (ROCSample s : samples) {
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

        private void computeEstimatedNumSteps() {
            if (rocSweepLogStep) {
                estimateNumSteps = Math.round(Math.abs(Math.log(rocSweepEnd / rocSweepStart) / Math.log(rocSweepStep)));
            } else {
                estimateNumSteps = Math.round(Math.abs(rocSweepEnd - rocSweepStart) / rocSweepStep);
            }
        }

        protected boolean updateCurrentValue() {
            // now update swept variable
            float old = currentValue;
            if (!firstStep) {
                if (rocSweepLogStep) {
                    currentValue = stepSign() > 0 ? currentValue * rocSweepStep : currentValue / rocSweepStep;
                } else {
                    currentValue += rocSweepStep * stepSign();
                }
            }
            firstStep = false;
            setCurrentSweptValue(currentValue);

            log.info(String.format("ROCSweep increased currentValue of %s from %s -> %s", getRocSweepPropertyName(),
                    eng.format(old), eng.format(currentValue)));
            boolean done = stepSign() > 0 ? currentValue > rocSweepEnd : currentValue < rocSweepEnd;
            return done;
        }

        private void setCurrentSweptValue(float value) {
            if (setter != null) {
                try {
                    if (floatType) {
                        setter.invoke(selectedNoiseFilter, value);
                    } else {
                        setter.invoke(selectedNoiseFilter, (int) Math.round(value));
                    }
                } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ex) {
                    log.warning(String.format("Could not set current value %.4f using setter %s: got %s", value, setter, ex.toString()));
                }
            } else {
                showWarningDialogInSwingThread(String.format("<html>NoiseTesterFilter: %s has no sweeepable property %s, <p>Check rocSweepParameter.", getSelectedNoiseFilterName(), getRocSweepPropertyName()), "ROC sweep property error");
                stop();
            }
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
            if (!running && rocSweepPropertyName != null) {
                setCurrentSweptValue(rocSweepStart);
            }
            putFloat("rocSweepStart", rocSweepStart);
            storeParams();
            getSupport().firePropertyChange("rocSweepStart", old, this.rocSweepStart);
            computeEstimatedNumSteps();
            final String s = String.format("Sweep start: %s - About %d steps", eng.format(rocSweepStart), Math.round(estimateNumSteps));
            log.info(s);
            getFilterPanel().displayTooltip("rocSweepStart", s);
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
            if (!running && rocSweepPropertyName != null) {
                setCurrentSweptValue(rocSweepEnd);
            }
            putFloat("rocSweepEnd", rocSweepEnd);
            storeParams();
            getSupport().firePropertyChange("rocSweepEnd", old, this.rocSweepEnd);
            computeEstimatedNumSteps();
            final String s = String.format("Sweep end: %s - About %d steps", eng.format(rocSweepEnd), Math.round(estimateNumSteps));
            log.info(s);
            getFilterPanel().displayTooltip("rocSweepEnd", s);
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
            computeEstimatedNumSteps();
            final String s = String.format("About %d steps", Math.round(estimateNumSteps));
            log.info(s);
            getFilterPanel().displayTooltip("rocSweepLogStep", s);
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
            computeEstimatedNumSteps();
            final String s = String.format("Step %s: about %d steps", eng.format(rocSweepStep), Math.round(estimateNumSteps));
            log.info(s);
            getFilterPanel().displayTooltip("rocSweepEnd", s);
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
                    if (setter == null) {
                        log.warning(String.format("Null setter for property " + p));
                        continue;
                    }
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
                log.info(String.format("Found setter %s for property named %s", setter.toGenericString(), rocSweepPropertyName));
                storeParams();
//                getSupport().firePropertyChange("rocSweepPropertyName", old, this.rocSweepPropertyName); // no setter now, everything through comboboxmodel
            } else if (rocSweepPropertyName != null) {
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

    // following two methods are private because the roc sweep property is set by the rocSweepParameterComboBoxModel
    // they are only here for convenience
    private String getRocSweepPropertyName() {
        return rocSweep.getRocSweepPropertyName();
    }

    synchronized private void setRocSweepPropertyName(String rocSweepPropertyName) throws IntrospectionException {
        rocSweep.setRocSweepPropertyName(rocSweepPropertyName);
    }

    private class ROCHistory {

        private EvictingQueue<ROCSample> rocHistoryList = EvictingQueue.create(rocHistoryLength);
        private Float lastTau = null;
        GLUT glut = new GLUT();
        private final int SAMPLE_INTERVAL_NO_CHANGE = 30;
        private int legendDisplayListId = 0;
        private ROCSample[] legendROCs = null;
        private ROCSample avgRocSample = null; // avg over entire rocHistoryLength
//        private AbstractNoiseFilter noiseFilter = null;
        String noiseFilterName = null;
        String noiseFilterInfo = null;
        final int DEFAULT_PT_SIZE = 3;
        private int ptSize = DEFAULT_PT_SIZE;
        private Color color = Color.white;
        float auc = 0;
        boolean summary = false; // flag for a roc sweep set of points
        Boolean clockwise = null;  // flag that says last point has higher tpr/fpr, i.e. the sweep increased the signal and noise rate
        String rocSweepPropertyName = null;

        public ROCHistory(AbstractNoiseFilter noiseFilter) {
            if (noiseFilter != null) {
                this.noiseFilterName = noiseFilter.getClass().getSimpleName();
                this.noiseFilterInfo = noiseFilter.infoString();
            }
        }

        public String toString() {
            return String.format("ROCHistory for filter %s with %,d points", rocHistoryList.size());
        }

        ROCSample createSample(float x, float y, float tau, boolean labeled) {
            return new ROCSample(x, y, tau, labeled, this);
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
            ROCSample avg = new ROCSample(avgFpr, avgTpr, getCorrelationTimeS(), false, this);
            return avg;
        }

        float computeAUC() {
            this.auc = 0;
            int n = rocHistoryList.size();
            float lastx = 0, lasty = 0;
            if (rocHistoryList.size() < 2) {
                return Float.NaN;
            }
            ROCSample first = null;
            ROCSample last = null;
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

        void setLength(int length) {
            rocHistoryList.clear();
            if (length != rocHistoryList.remainingCapacity()) {
                rocHistoryList = EvictingQueue.create(length);
            }
        }

        private void reset() {
            rocHistoryList.clear();
            lastTau = null;
        }

        void prune() {
            EvictingQueue<ROCSample> tmp = EvictingQueue.create(rocHistoryList.size());
            for (ROCSample s : rocHistoryList) {
                tmp.add(s);
            }
            rocHistoryList = tmp;
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

        void addSample(float fpr, float tpr) {
            rocHistoryList.add(new ROCSample(fpr, tpr, 0, false, this));
            avgRocSample = computeAvg();
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
            if (noiseFilterInfo != null) {
                DrawGL.drawString(getShowFilteringStatisticsFontSize(),
                        chip.getSizeX(), rocHistoryLabelPosY, 1, color,
                        noiseFilterInfo + String.format(" AUC=%.3f", auc));
            }
            rocHistoryLabelPosY -= 1.8f * getShowFilteringStatisticsFontSize();
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

            } else if (avgRocSample != null) {
                // draw avg over the samples
                // draw X for last packet TPR / TNR point
                float x = avgRocSample.x * sx;
                float y = avgRocSample.y * sy;
                float[] rgb = color.getRGBComponents(null);

                int L = 12;
                gl.glPushMatrix();
                gl.glColor3fv(rgb, 0);
                gl.glLineWidth(10);
                DrawGL.drawCross(gl, x, y, L, 0);
                gl.glPopMatrix();
                gl.glPushMatrix();
                int fs = getShowFilteringStatisticsFontSize();
                DrawGL.drawString(fs, x + L, y - fs / 2, 0, Color.gray, "avg");
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
    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        double noiseIntervalS = 1 / noiseRateHz;
        for (final int[] arrayRow : timestampImage) {
            for (int i = 0; i < arrayRow.length; i++) {
                final double p = random.nextDouble();
                final double t = -noiseIntervalS * Math.log(1 - p);
                final int tUs = (int) (1000000 * t);
                arrayRow[i] = lastTimestampUs - tUs;
            }
        }
        for (final byte[] arrayRow : polImage) {
            for (int i = 0; i < arrayRow.length; i++) {
                final boolean b = random.nextBoolean();
                arrayRow[i] = b ? (byte) 1 : (byte) -1;
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
    synchronized public void setDisableAddingNoise(boolean disableAddingNoise) {
        boolean old = this.disableAddingNoise;
        this.disableAddingNoise = disableAddingNoise;
        resetFilter(); // reset since this affects filter and can cause apparent deadlock
        putBoolean("disableAddingNoise", disableAddingNoise);
        getSupport().firePropertyChange("disableAddingNoise", old, this.disableAddingNoise);
    }

    @Preferred
    public void doClearROCs() {
        rocHistoriesSaved.clear();
        rocSweeps.clear();
        rocHistoryCurrent.reset();
    }

    @Preferred
    public void doClearLastROC() {
        if (!rocSweeps.isEmpty()) {
            rocSweeps.removeLast();
        }
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

    /**
     * @return the enableMultipleMethods
     */
    public boolean isEnableMultipleMethods() {
        return enableMultipleMethods;
    }

    /**
     * @param enableMultipleMethods the enableMultipleMethods to set
     */
    public void setEnableMultipleMethods(boolean enableMultipleMethods) {
        boolean oldEnableMultipleMethods = this.enableMultipleMethods;
        this.enableMultipleMethods = enableMultipleMethods;
        getSupport().firePropertyChange("enableMultipleMethods", oldEnableMultipleMethods, enableMultipleMethods);
        putBoolean("enableMultipleMethods", enableMultipleMethods);
    }

    /**
     * @return the disableDenoising
     */
    public boolean isDisableDenoising() {
        return disableDenoising;
    }

    /**
     * @return the disableSignal
     */
    public boolean isDisableSignal() {
        return disableSignal;
    }

    /**
     * @param disableSignal the disableSignal to set
     */
    public void setDisableSignal(boolean disableSignal) {
        this.disableSignal = disableSignal;
    }

    /**
     * @param disableDenoising the disableDenoising to set
     */
    public void setDisableDenoising(boolean disableDenoising) {
        boolean old = this.disableDenoising;
        this.disableDenoising = disableDenoising;
        getSupport().firePropertyChange("disableDenoising", old, disableDenoising);
    }

    /**
     * @return the rocSweepParameterComboBoxModel
     */
    public ComboBoxModel getRocSweepParameterComboBoxModel() {
        return rocSweepParameterComboBoxModel;
    }

    /**
     * @param rocSweepParameterComboBoxModel the rocSweepParameterComboBoxModel
     * to set
     */
    public void setRocSweepParameterComboBoxModel(ComboBoxModel rocSweepParameterComboBoxModel) {
        this.rocSweepParameterComboBoxModel = rocSweepParameterComboBoxModel;
    }

    /**
     * @return the maxProcessingTimeLimitMs
     */
    public int getMaxProcessingTimeLimitMs() {
        return maxProcessingTimeLimitMs;
    }

    /**
     * @param maxProcessingTimeLimitMs the maxProcessingTimeLimitMs to set
     */
    public void setMaxProcessingTimeLimitMs(int maxProcessingTimeLimitMs) {
        if (maxProcessingTimeLimitMs < 5) {
            maxProcessingTimeLimitMs = 5;
        }
        this.maxProcessingTimeLimitMs = maxProcessingTimeLimitMs;
        putInt("maxProcessingTimeLimitMs", maxProcessingTimeLimitMs);
    }

    class ROCSample {

        float x;
        float y;
        float param;
        int size;

        /**
         * Create new sample
         *
         * @param x FPR *sx position on plot
         * @param y TPR *sy position on plot in fraction of chip
         * @param param associated correlation time or threshold
         * @param labeled true to label this sample, false for global values or
         * last sample
         */
        ROCSample(float x, float y, float param, boolean labeled, final ROCHistory outer) {
            this.size = outer.getPtSize();
            this.x = x;
            this.y = y;
            this.param = param;
        }

        /**
         * Draw point using color derived from param correlation interval
         * parameter
         *
         * @param gl
         */
        void draw(GL2 gl) {
            float hue = (float) (Math.log10(param) / 2 + 1.5); //. hue is 1 for param=0.1s and is 0 for param = 1ms
            Color c = Color.getHSBColor(hue, 1f, hue);
            draw(gl, c, size);
        }

        /**
         * Draw point with a fixed color and size
         *
         * @param gl
         * @param c the color
         * @param size the size in (chip) pixels
         */
        void draw(GL2 gl, Color c, int size) {
            float[] rgb = c.getRGBComponents(null);
            float[] rgba = new float[]{rgb[0], rgb[1], rgb[2], .2f};
            gl.glColor4fv(rgba, 0);
            gl.glLineWidth(1);
            gl.glPushMatrix();
            DrawGL.drawBox(gl, x * sx, y * sy, size, size, 0);
            gl.glPopMatrix();
        }
    }

}
