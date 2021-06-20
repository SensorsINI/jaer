/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.DisplayMethod2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.SpikeSound;
import net.sf.jaer.util.TextRendererScale;
import net.sf.jaer.util.filter.LowpassFilter;

import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisVideoContrastController;
import java.awt.Color;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseROI;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.TobiLogger;

/**
 * Collects and displays statistics for a selected range of pixels / cells.
 *
 * @author tobi
 *
 * This is part of jAER <a
 *         href="http://jaerproject.net/">jaerproject.net</a>, licensed under the LGPL (<a
 *         href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/
 * GNU_Lesser_General_Public_License</a>.
 */
@Description("Collects and displays statistics for a selected range of pixels / cells")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class CellStatsProber extends EventFilter2DMouseROI implements FrameAnnotater, MouseListener, MouseMotionListener, Observer,
        PropertyChangeListener {

    public static DevelopmentStatus getDevelopementStatus() {
        return DevelopmentStatus.Beta;
    }

    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    // private JMenu popupMenu;
    int startx, starty, endx, endy;
    Point startPoint = null, endPoint = null, clickedPoint = null;
    // private GLUT glut = new GLUT();
    private Stats stats = new Stats();
    private boolean rateEnabled = getBoolean("rateEnabled", true);
    private boolean isiHistEnabled = getBoolean("isiHistEnabled", true);
    private float histogramOpacity = getFloat("histogramOpacity", .5f);
    private boolean showRateDistribution = getBoolean("showRateDistribution", false);
    private boolean freqHistEnabled = getBoolean("freqHistEnabled", false);
    private boolean separateEventTypes = getBoolean("separateEventTypes", true);
    private boolean logISIEnabled = getBoolean("logISIEnabled", true);
    private boolean logProbEnabled = getBoolean("logProbEnabled", false);
    private boolean spikeSoundEnabled = getBoolean("spikeSoundEnabled", false);
    private boolean scaleHistogramsIncludingOverflow = getBoolean("scaleHistogramsIncludingOverflow", true);
    SpikeSound spikeSound = null;
    private TextRenderer renderer = null;
    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f};
    EngineeringFormat engFmt = new EngineeringFormat();
    private boolean resetOnBiasChange = getBoolean("resetOnBiasChange", true);
    private boolean addedBiasgenPropChangeListener = false;
    private boolean countDVSEventsBetweenExternalPinEvents = getBoolean("countDVSEventsBetweenExternalPinEvents", false);
    private boolean accumulateEventsOnEachPhase = getBoolean("accumulateEventsOnEachPhase", false);
    private TobiLogger histogramCsvWriter = null;
    private int mouseHistAddress;

    public CellStatsProber(AEChip chip) {
        super(chip);
        if ((chip.getCanvas() != null) && (chip.getCanvas().getCanvas() != null)) {
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
            renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
        }
        chip.addObserver(this);
        final String h = "ISIs", e = "Event rate", l = "Latency", c = "Count", g = "General";
        setPropertyTooltip(h, "isiHistEnabled", "enable histogramming interspike intervals (or frequency histograms if freqHistEnbled also selected)");
        setPropertyTooltip(h, "freqHistEnabled", "isiHist also enabled, then show frequency histogram instead of ISI histogram");
        setPropertyTooltip(h, "isiMinUs", "min ISI in us");
        setPropertyTooltip(h, "isiMaxUs", "max ISI in us");
        setPropertyTooltip(h, "isiAutoScalingEnabled", "autoscale bounds for ISI histogram");
        setPropertyTooltip(h, "isiNumBins", "number of bins in the ISI");
        setPropertyTooltip(h, "showAverageISIHistogram", "shows the average of the individual ISI histograms.");
        setPropertyTooltip(h, "showIndividualISIHistograms", "show the ISI histograms of all the cells in the selection. ");
        setPropertyTooltip(h, "showRateDistribution", "show the histogram individual time-averaged event rates of all the cells in the ROI. ");
        setPropertyTooltip(h, "logISIEnabled", "histograms have logarithmically spaced bins rather than linearly spaced bins");
        setPropertyTooltip(h, "logProbEnabled", "probabilities on log scale");

        setPropertyTooltip(e, "rateEnabled", "show measured individual average event rate for selected region in Hz");
        setPropertyTooltip(e, "rateTauMs", "lowpass filter time constant in ms for measuring event rate");
        setPropertyTooltip(g, "spikeSoundEnabled", "enable playing spike sound whenever the selected region has events");
        setPropertyTooltip(h, "individualISIsEnabled", "enables individual ISI statistics for each cell in selection. Disabling lumps all cells into one for ISI computation.");
        setPropertyTooltip(h, "separateEventTypes", "Separate average histogram into individual event types for each pixel. If unchecked, then all event types for a pixel are lumped together for ISIs.");
        setPropertyTooltip(h, "scaleHistogramsIncludingOverflow", "Scales histograms to include overflows for ISIs that are outside of range");
        setPropertyTooltip(h, "histogramOpacity", "Opacity (brightness) of histograms");
        setPropertyTooltip(l, "showLatencyHistogramToExternalInputEvents", "(not yet implemented) Shows a histogram of latencies of each pixel in selected region relative to external input event address generated by input to external input pin"); // TODO
        setPropertyTooltip(l, "externalInputEventAddress",
                "int32 address of external input events; see e.g. DavisChip.EXTERNAL_INPUT_EVENT_ADDR for this address");
        setPropertyTooltip(c, "countDVSEventsBetweenExternalPinEvents", "counts events of ON and OFF polarity between rising and falling phases of stimulus based on special events from external input pin");
        setPropertyTooltip(c, "accumulateEventsOnEachPhase", "accumulates events to renderer of ON and OFF polarity between rising and falling stimulation");
        setPropertyTooltip(h, "writeHistogramCSV", "writes CSV file for histogram");
        chip.getSupport().addPropertyChangeListener(this);
    }

    public void displayStats(GLAutoDrawable drawable) {
        if ((drawable == null) || (chip.getCanvas() == null)) {
            return;
        }
        canvas = chip.getCanvas();
        glCanvas = (GLCanvas) canvas.getCanvas();
        int sx = chip.getSizeX(), sy = chip.getSizeY();
        GL2 gl = drawable.getGL().getGL2();
        stats.drawStats(drawable);
        stats.play();

    }

    public void doWriteHistogramCSV() {
        stats.writeHistogramCSV();
    }

    public void showContextMenu() {
    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        checkOutputPacketEventType(in); // added to prevent memory leak when iterating ApsDVSEventPacket - tobi
        stats.collectStats(in);
        return in; // should this be out rather than in for scheme for iterating dvs or apsdvs event packets? - tobi
    }

    @Override
    synchronized public void resetFilter() {
        // selection = null;
        stats.reset();
        if (isIsiAutoScalingEnabled()) {
            setIsiMaxUs(0);
        }
        stats.eventCountAfterExternalPinEvents.reset();
    }

    @Override
    public void initFilter() {
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable); // draw ROI 

        if (canvas.getDisplayMethod() instanceof DisplayMethod2D) {
            // chipRendererDisplayMethod = (ChipRendererDisplayMethod) canvas.getDisplayMethod();
            displayStats(drawable);
        }
    }

    /**
     * @return the rateEnabled
     */
    public boolean isRateEnabled() {
        return rateEnabled;
    }

    /**
     * @param rateEnabled the rateEnabled to set
     */
    public void setRateEnabled(boolean rateEnabled) {
        this.rateEnabled = rateEnabled;
        putBoolean("rateEnabled", rateEnabled);
    }

    /**
     * @param isiHistEnabled the isiHistEnabled to set
     */
    public void setIsiHistEnabled(boolean isiHistEnabled) {
        boolean old = this.isiHistEnabled;
        this.isiHistEnabled = isiHistEnabled;
        putBoolean("isiHistEnabled", isiHistEnabled);
        if (isiHistEnabled) {
            setShowLatencyHistogramToExternalInputEvents(false);
        }
        getSupport().firePropertyChange("isiHistEnabled", old, this.isiHistEnabled);
    }

    /**
     * @return the isiHistEnabled
     */
    public boolean isIsiHistEnabled() {
        return isiHistEnabled;
    }

    /**
     * @return the spikeSoundEnabled
     */
    public boolean isSpikeSoundEnabled() {
        return spikeSoundEnabled;
    }

    /**
     * @param spikeSoundEnabled the spikeSoundEnabled to set
     */
    public void setSpikeSoundEnabled(boolean spikeSoundEnabled) {
        this.spikeSoundEnabled = spikeSoundEnabled;
        putBoolean("spikeSoundEnabled", spikeSoundEnabled);
    }

    private int min(int a, int b) {
        return a < b ? a : b;
    }

    private int max(int a, int b) {
        return a > b ? a : b;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        super.mouseMoved(e);
        currentMousePoint = canvas.getPixelFromMouseEvent(e);
        mouseHistAddress = stats.getHistIdx(currentMousePoint.x, currentMousePoint.y);
    }

    public void setRateTauMs(float tauMs) {
        stats.setRateTauMs(tauMs);
    }

    public float getRateTauMs() {
        return stats.getRateTauMs();
    }

    public void setIsiNumBins(int isiNumBins) {
        stats.setIsiNumBins(isiNumBins);
    }

    public void setIsiMinUs(int isiMinUs) {
        stats.setIsiMinUs(isiMinUs);
    }

    public void setIsiMaxUs(int isiMaxUs) {
        stats.setIsiMaxUs(isiMaxUs);
    }

    public void setIsiAutoScalingEnabled(boolean isiAutoScalingEnabled) {
        stats.setIsiAutoScalingEnabled(isiAutoScalingEnabled);
    }

    public boolean isIsiAutoScalingEnabled() {
        return stats.isIsiAutoScalingEnabled();
    }

    public void setIndividualISIsEnabled(boolean individualISIsEnabled) {
        stats.setIndividualISIsEnabled(individualISIsEnabled);
    }

    public boolean isIndividualISIsEnabled() {
        return stats.isIndividualISIsEnabled();
    }

    public int getIsiNumBins() {
        return stats.getIsiNumBins();
    }

    public int getIsiMinUs() {
        return stats.getIsiMinUs();
    }

    public int getIsiMaxUs() {
        return stats.getIsiMaxUs();
    }

    public void setShowAverageISIHistogram(boolean showAverageISIHistogram) {
        stats.setShowAverageISIHistogram(showAverageISIHistogram);
    }

    public boolean isShowAverageISIHistogram() {
        return stats.isShowAverageISIHistogram();
    }

    public void setShowIndividualISIHistograms(boolean showIndividualISIHistograms) {
        stats.setShowIndividualISIHistograms(showIndividualISIHistograms);
    }

    public boolean isShowIndividualISIHistograms() {
        return stats.isShowIndividualISIHistograms();
    }

    public boolean isShowLatencyHistogramToExternalInputEvents() {
        return stats.isShowLatencyHistogramToExternalInputEvents();
    }

    public void setShowLatencyHistogramToExternalInputEvents(boolean showLatencyHistogramToExternalInputEvents) {
        stats.setShowLatencyHistogramToExternalInputEvents(showLatencyHistogramToExternalInputEvents);
    }

    public int getExternalInputEventAddress() {
        return stats.getExternalInputEventAddress();
    }

    public void setExternalInputEventAddress(int externalInputEventAddress) {
        stats.setExternalInputEventAddress(externalInputEventAddress);
    }

    @Override
    synchronized public void update(Observable o, Object arg) {
        if (!addedBiasgenPropChangeListener && (chip.getBiasgen() != null)) {
            chip.getBiasgen().getSupport().addPropertyChangeListener(this);
            addedBiasgenPropChangeListener = true;
        }
    }

    // /**
    // * @return the separateEventTypes
    // */
    // public boolean isSeparateEventTypes() {
    // return separateEventTypes;
    // }
    //
    // /**
    // * @param separateEventTypes the separateEventTypes to set
    // */
    // public void setSeparateEventTypes(boolean separateEventTypes) {
    // this.separateEventTypes = separateEventTypes;
    // putBoolean("separateEventTypes", separateEventTypes);
    // }
    /**
     * @return the logISIEnabled
     */
    public boolean isLogISIEnabled() {
        return logISIEnabled;
    }

    /**
     * @param logISIEnabled the logISIEnabled to set
     */
    public void setLogISIEnabled(boolean logISIEnabled) {
        boolean old = this.logISIEnabled;
        if (logISIEnabled != old) {
            stats.reset();
        }
        this.logISIEnabled = logISIEnabled;
        putBoolean("logISIEnabled", logISIEnabled);

    }

    /**
     * @return the logProbEnabled
     */
    public boolean isLogProbEnabled() {
        return logProbEnabled;
    }

    /**
     * @param logProbEnabled the logProbEnabled to set
     */
    public void setLogProbEnabled(boolean logProbEnabled) {
        boolean old = this.logProbEnabled;

        this.logProbEnabled = logProbEnabled;
        putBoolean("logProbEnabled", logProbEnabled);

    }

    /**
     * @return the scaleHistogramsIncludingOverflow
     */
    public boolean isScaleHistogramsIncludingOverflow() {
        return scaleHistogramsIncludingOverflow;
    }

    /**
     * @param scaleHistogramsIncludingOverflow the
     * scaleHistogramsIncludingOverflow to set
     */
    public void setScaleHistogramsIncludingOverflow(boolean scaleHistogramsIncludingOverflow) {
        this.scaleHistogramsIncludingOverflow = scaleHistogramsIncludingOverflow;
        putBoolean("scaleHistogramsIncludingOverflow", scaleHistogramsIncludingOverflow);

    }

    private static enum Phase {
        Uninitalized, Rising, Falling
    };

    private class Stats {

        /**
         * Histogram RBBA color
         */
        private final float[] GLOBAL_HIST_COLOR = {0, .5f, .5f, 1},
                RATE_HIST_COLOR = {.5f, .5f, 0f, 1},
                INDIV_HIST_COLOR = {0, .0f, 1f, 1},
                HIST_OVERFLOW_COLOR = {.6f, .4f, .4f, 1};
        private int isiMinUs = getInt("isiMinUs", 10), isiMaxUs = getInt("isiMaxUs", 100000), isiNumBins = getInt("isiNumBins", 100);
        private float freqMinHz = 1e6f / isiMaxUs, freqMaxHz = 1e6f / isiMinUs;
        private double logIsiMin = isiMinUs <= 0 ? 0 : Math.log10(isiMinUs), logIsiMax = Math.log10(isiMaxUs);
        private double logFreqMin = Math.log10(freqMinHz), logFreqMax = Math.log10(freqMaxHz);
        // private int[] bins = new int[isiNumBins];
        // private int lessCount = 0, moreCount = 0;
        // private int maxCount = 0;
        private boolean isiAutoScalingEnabled = getBoolean("isiAutoScalingEnabled", false);
        private boolean individualISIsEnabled = getBoolean("individualISIsEnabled", true);
        private boolean showAverageISIHistogram = getBoolean("showAverageISIHistogram", true);
        private boolean showIndividualISIHistograms = getBoolean("showIndividualISIHistograms", false);
        private boolean showLatencyHistogramToExternalInputEvents = getBoolean("showLatencyHistogramToExternalInputEvents", false);
        private int externalInputEventAddress = getInt("externalInputEventAddress", DavisChip.EXTERNAL_INPUT_EVENT_ADDR);
        private EventCountsAfterInputPinEvents eventCountAfterExternalPinEvents = new EventCountsAfterInputPinEvents();

        public LowpassFilter getRateFilter() {
            return rateFilter;
        }

        private LowpassFilter rateFilter = new LowpassFilter();

        {
            rateFilter.setTauMs(getFloat("rateTauMs", 10));
        }
        boolean initialized = false;
        float instantaneousRate = 0, filteredRatePerPixel = 0;
        private int lastEventTimestamp = 0, lastRateMeasurementTimestamp = 0;
        int eventCount = 0; // last update event count

        private HashMap<Integer, IsiOrFreqHist> histMap = new HashMap();
        IsiOrFreqHist globalHist = new IsiOrFreqHist(-1, -1);
        IsiOrFreqHist rateHist = new IsiOrFreqHist(-1, -1);
        IsiOrFreqHist[] averageTypeHistograms = null;
        private int nPixels = 0;
        private int lastExternalInputEventTimestamp = 0;

        /**
         * Histogram index computed from pixel address x and y
         *
         * @param x
         * @param y
         * @return idx
         */
        private int getHistIdx(int x, int y) {
            return y + chip.getSizeY() * x;
        }

        synchronized public void collectStats(EventPacket<BasicEvent> in) {
            if (roiSelecting) {
                histMap.clear();
                globalHist.reset();
                rateHist.reset();
                return;
            }
            nPixels = roiRect == null ? chip.getNumPixels() : (roiRect.width) * (roiRect.height);
            stats.eventCount = 0;
            for (BasicEvent e : in) {
                if (showLatencyHistogramToExternalInputEvents && (e.address == externalInputEventAddress)) {
                    lastExternalInputEventTimestamp = e.timestamp;
                }
                if (countDVSEventsBetweenExternalPinEvents) {
                    eventCountAfterExternalPinEvents.addEvent(e);
                }
                if (e.isSpecial()) {
                    continue;
                }
                if (e.isFilteredOut()) {
                    continue; // added to deal with filteredOut events at the end of packets, which are not filtered out
                    // by the EventPacket inputIterator()
                }
                // if event inside ROI, add it it its histgram, maybe
                if (insideRoi(e)) {
                    stats.eventCount++;
                    lastEventTimestamp = e.timestamp;
                    if ((isiHistEnabled || showRateDistribution) && individualISIsEnabled) {
                        int histAddr = getHistIdx(e.x, e.y);
                        IsiOrFreqHist h = histMap.get(histAddr);
                        if (h == null) {
                            try {
                                h = new IsiOrFreqHist(e.x, e.y);
                                histMap.put(histAddr, h);
                            } catch (OutOfMemoryError ex) {
                                log.warning(String.format("Out of heap memory, try a smaller ROI?: See https://stackoverflow.com/questions/511013/how-to-handle-outofmemoryerror-in-java (%s)", ex.toString()));
                                histMap.clear();
                                doClearROI();
                                roiSelecting = true; // to disable stats
//                                setFilterEnabled(false);
                                return;
                            }
                            // System.out.println("added hist for "+e);
                        }
                        h.addEvent(e);
                    } else if (isiHistEnabled && !individualISIsEnabled) { // treat all pixels like one lumped together, usually not correct to do this
                        globalHist.addEvent(e);
                    }
                    if (countDVSEventsBetweenExternalPinEvents) {
                        eventCountAfterExternalPinEvents.addEvent((PolarityEvent) e); // TODO check if counting APS events
                    }

                }
            }
            if (stats.eventCount > 0) {
                computeAverageEps();
            }
            // assemble global summed hist from indiv pixel histograms
            if ((isiHistEnabled || showRateDistribution) && individualISIsEnabled) {
                globalHist.reset();
                rateHist.reset();
                // consider all pixel histograms
                for (IsiOrFreqHist h : histMap.values()) {
                    if (h.maxCount == 0) {
                        continue; // no ISI yet, just one event
                    }
                    globalHist.lessCount += h.lessCount;
                    globalHist.moreCount += h.moreCount;
                    for (int i = 0; i < isiNumBins; i++) {
                        globalHist.bins[i] += h.bins[i];
                        if (globalHist.bins[i] > globalHist.maxCount) {
                            globalHist.maxCount = globalHist.bins[i];
                        }
                    }
                    if (scaleHistogramsIncludingOverflow && (globalHist.lessCount > globalHist.maxCount)) {
                        globalHist.maxCount = globalHist.lessCount;
                    }
                    if (scaleHistogramsIncludingOverflow && (globalHist.moreCount > globalHist.maxCount)) {
                        globalHist.maxCount = globalHist.moreCount;
                    }

                    // rates
                    if (showRateDistribution) {
                        int bin = getFreqBin(h.avgRateHz);
                        rateHist.incrementBin(bin);
                    }
                }
            }
        }

        public void setRateTauMs(float tauMs) {
            rateFilter.setTauMs(tauMs);
            putFloat("rateTauMs", tauMs);
        }

        public float getRateTauMs() {
            return rateFilter.getTauMs();
        }

        /**
         * @return the showAverageISIHistogram
         */
        public boolean isShowAverageISIHistogram() {
            return showAverageISIHistogram;
        }

        /**
         * @param showAverageISIHistogram the showAverageISIHistogram to set
         */
        public void setShowAverageISIHistogram(boolean showAverageISIHistogram) {
            this.showAverageISIHistogram = showAverageISIHistogram;
            putBoolean("showAverageISIHistogram", showAverageISIHistogram);
        }

        /**
         * @return the showIndividualISIHistograms
         */
        public boolean isShowIndividualISIHistograms() {
            return showIndividualISIHistograms;
        }

        /**
         * @param showLatencyHistogramToExternalInputEvents the
         * showLatencyHistogramToExternalInputEvents to set
         */
        public void setShowLatencyHistogramToExternalInputEvents(boolean showLatencyHistogramToExternalInputEvents) {
            boolean old = this.showLatencyHistogramToExternalInputEvents;
            this.showLatencyHistogramToExternalInputEvents = showLatencyHistogramToExternalInputEvents;
            putBoolean("showLatencyHistogramToExternalInputEvents", showLatencyHistogramToExternalInputEvents);
            if (showLatencyHistogramToExternalInputEvents) {
                setIsiHistEnabled(false);
            }
            getSupport().firePropertyChange("showLatencyHistogramToExternalInputEvents", old, this.showLatencyHistogramToExternalInputEvents);
        }

        /**
         * @param showIndividualISIHistograms the showIndividualISIHistograms to
         * set
         */
        public void setShowIndividualISIHistograms(boolean showIndividualISIHistograms) {
            this.showIndividualISIHistograms = showIndividualISIHistograms;
            putBoolean("showIndividualISIHistograms", showIndividualISIHistograms);
        }

        /**
         * @return the showLatencyHistogramToExternalInputEvents
         */
        public boolean isShowLatencyHistogramToExternalInputEvents() {
            return showLatencyHistogramToExternalInputEvents;
        }

        /**
         * @return the externalInputEventAddress
         */
        public int getExternalInputEventAddress() {
            return externalInputEventAddress;
        }

        /**
         * @param externalInputEventAddress the externalInputEventAddress to set
         */
        public void setExternalInputEventAddress(int externalInputEventAddress) {
            this.externalInputEventAddress = externalInputEventAddress;
            putInt("externalInputEventAddress", externalInputEventAddress);
        }

        private void writeHistogramCSV() {
            if (!isiHistEnabled) {
                showWarningDialogInSwingThread("Enable isiHistEnabled", "ISIHist not enabled");
                return;
            }
            if (histogramCsvWriter == null) {
                histogramCsvWriter = new TobiLogger("CellStatsProberHistogram.csv", null);
                histogramCsvWriter.setFileCommentString(String.format("histogram logISIEnabled=%s freqHistEnabled=%s", logISIEnabled, freqHistEnabled));
            }
            String binString = null;
            if (freqHistEnabled) {
                binString = "Freq(Hz)";
            } else {
                binString = "ISI(s)";
            }
            histogramCsvWriter.setColumnHeaderLine(binString + ",Count");
            histogramCsvWriter.setEnabled(true); // open file, write header
            for (int i = 0; i < isiNumBins; i++) {
                float bin = getIsiOrFreqBinCenterValue(i);

                histogramCsvWriter.log(String.format("%e,%d", bin, globalHist.bins[i]));
            }
            histogramCsvWriter.setEnabled(false);
            histogramCsvWriter.showFolderInDesktop();
        }

        /**
         * Single histogram of either ISIs or frequency of firing
         */
        private class IsiOrFreqHist {

            int x = -1, y = -1, address = -1;
            int[] bins = new int[isiNumBins];
            int lessCount = 0, moreCount = 0;
            int maxCount = 0; // set >0 when there are ISIs
            int lastT = 0;
            boolean virgin = true; // true on init, false on first event (but still has no ISI after first event)
            LowpassFilter rateFilter = new LowpassFilter(getRateTauMs());
            float avgRateHz = 0;

            public IsiOrFreqHist(int x, int y) {
                this.x = x;
                this.y = y;
                int histAddr = getHistIdx(x, y);
                address = histAddr;
            }

            @Override
            public String toString() {
                return String.format("ISIHist: avgFreq=%sHz virgin=%s maxCount=%d lessCount=%d moreCount=%d isiMaxUs=%d isiMinUs=%d",
                        virgin, engFmt.format(avgRateHz), maxCount, lessCount, moreCount, isiMaxUs, isiMinUs);
            }

            void addEvent(BasicEvent e) {
                if (virgin) {
                    lastT = e.timestamp;
                    lastEventTimestamp = lastT;
                    virgin = false;
                    rateFilter.reset();
                    return;
                }
                int isi;
                if (showLatencyHistogramToExternalInputEvents) {
                    isi = e.timestamp - lastExternalInputEventTimestamp;
                } else {
                    isi = e.timestamp - lastT;
                }
                if (isi < 0) {
                    lastT = e.timestamp; // handle wrapping
                    return;
                }
                float freqHz = isi == 0 ? Float.MAX_VALUE : 1e6f / isi;
                avgRateHz = rateFilter.filter(freqHz, e.timestamp);

                if (isiAutoScalingEnabled) {
                    if (isi > isiMaxUs) {
                        setIsiMaxUs(isi);
                    } else if (isi < isiMinUs) {
                        setIsiMinUs(isi);
                    }
                }
                int bin = getIsiOrFreqBinNumber(isi);
                incrementBin(bin);
                lastT = e.timestamp;
//                System.out.println(String.format("added isi=%10d",isi));
            }

            private void incrementBin(int bin) {
                if (bin < 0) {
                    lessCount++;
                    if (scaleHistogramsIncludingOverflow && (lessCount > maxCount)) {
                        maxCount = lessCount;
                    }
                } else if (bin >= isiNumBins) {
                    moreCount++;
                    if (scaleHistogramsIncludingOverflow && (moreCount > maxCount)) {
                        maxCount = moreCount;
                    }
                } else {
                    int v = ++bins[bin];
                    if (v > maxCount) {
                        maxCount = v;
                    }
                }
            }

            /**
             * Draws the histogram
             *
             */
            void draw(GL2 gl) {
                if (maxCount == 0) {
                    return;
                }

                float dx = (float) (chip.getSizeX() - 2) / (isiNumBins + 2);
                float sy = (float) (chip.getSizeY() - 2) / (logProbEnabled ? (float) Math.log10(maxCount) : maxCount);

                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(1, 1);
                gl.glVertex2f(chip.getSizeX() - 1, 1);
                gl.glEnd();

                if (lessCount > 0) {
                    gl.glPushAttrib(GL2ES3.GL_COLOR | GL.GL_LINE_WIDTH);
                    gl.glColor4fv(HIST_OVERFLOW_COLOR, 0);
                    gl.glBegin(GL.GL_LINE_STRIP);

                    float y = 1 + (sy * lessCount);
                    float x1 = -dx, x2 = x1 + dx;
                    gl.glVertex2f(x1, 1);
                    gl.glVertex2f(x1, y);
                    gl.glVertex2f(x2, y);
                    gl.glVertex2f(x2, 1);
                    gl.glEnd();
                    gl.glPopAttrib();
                }
                if (moreCount > 0) {
                    gl.glPushAttrib(GL2ES3.GL_COLOR | GL.GL_LINE_WIDTH);
                    gl.glColor4fv(HIST_OVERFLOW_COLOR, 0);
                    gl.glBegin(GL.GL_LINE_STRIP);

                    float y = 1 + (sy * moreCount);
                    float x1 = 1 + (dx * (isiNumBins + 2)), x2 = x1 + dx;
                    gl.glVertex2f(x1, 1);
                    gl.glVertex2f(x1, y);
                    gl.glVertex2f(x2, y);
                    gl.glVertex2f(x2, 1);
                    gl.glEnd();
                    gl.glPopAttrib();
                }
                if (maxCount > 0) {
                    gl.glBegin(GL.GL_LINE_STRIP);
                    for (int i = 0; i < bins.length; i++) {
                        if (bins[i] == 0 && logProbEnabled) {
                            continue;
                        }
                        float y = logProbEnabled ? (1 + sy * (float) Math.log10(bins[i])) : 1 + (sy * bins[i]);
                        float x1 = 1 + (dx * i), x2 = x1 + dx;
                        gl.glVertex2f(x1, 1);
                        gl.glVertex2f(x1, y);
                        gl.glVertex2f(x2, y);
                        gl.glVertex2f(x2, 1);
                    }
                    gl.glEnd();
                }
            }

            void draw(GL2 gl, float lineWidth, float[] color) {
                gl.glPushAttrib(GL2ES3.GL_COLOR | GL.GL_LINE_WIDTH);
                gl.glLineWidth(lineWidth);
                gl.glColor4fv(color, 0);
                draw(gl);
                gl.glPopAttrib();
            }

            private void reset() {
                if ((bins == null) || (bins.length != isiNumBins)) {
                    bins = new int[isiNumBins];
                } else {
                    Arrays.fill(globalHist.bins, 0);
                    Arrays.fill(rateHist.bins, 0);
                }
                lessCount = 0;
                moreCount = 0;
                maxCount = 0;
                virgin = true;
                rateFilter.reset();
            }

        }

        @Override
        public String toString() {
            return String.format("ROI %6d pix, tot. %10d ev, %15s eps tot, %15s eps/pix", nPixels, eventCount, engFmt.format(filteredRatePerPixel * nPixels), engFmt.format(filteredRatePerPixel));
        }

        /**
         * Computes the instantaneous and lowpass filtered event rate in ROI
         */
        private void computeAverageEps() {
            if (!rateEnabled) {
                return;
            }
            final float maxRate = 10e6f;
            if (!initialized) {
                initialized = true;
                return; // no rate possible yet
            }
            int dt = lastEventTimestamp - lastRateMeasurementTimestamp;
            if (dt < 0) {
                initialized = false;
                return; // nonmonotonic, return
            }
            if (dt == 0) {
                instantaneousRate = maxRate; // if the time interval is zero, use the max rate
            } else {
                instantaneousRate = (1e6f * eventCount) / (dt * AEConstants.TICK_DEFAULT_US);
            }
            filteredRatePerPixel = rateFilter.filter(instantaneousRate / nPixels, lastEventTimestamp);
            lastRateMeasurementTimestamp = lastEventTimestamp;
        }

        synchronized private void drawStats(GLAutoDrawable drawable) {
            renderer.begin3DRendering();
            // renderer.beginRendering(drawable.getWidth(), drawable.getHeight());
            // optionally set the color
            renderer.setColor(.2f, .2f, 1, 1f);
            float scale = .3f;
            if (rateEnabled) {
                scale = TextRendererScale.draw3dScale(renderer, toString(), getChip().getCanvas().getScale(), chip.getSizeX(), .9f);
                int ypos = chip.getSizeY() - 16;
                if (ypos < (chip.getSizeY() / 2)) {
                    ypos = chip.getSizeY() / 2;
                }
                renderer.draw3D(toString(), 1, ypos, 0, scale); // TODO fix string n lines
            }
            renderer.end3DRendering();
            if (countDVSEventsBetweenExternalPinEvents) {
                MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .4f);
                MultilineAnnotationTextRenderer.setScale(.25f);
                MultilineAnnotationTextRenderer.setColor(Color.yellow);
                MultilineAnnotationTextRenderer.renderMultilineString(eventCountAfterExternalPinEvents.toString());
            }
            // ... more draw commands, color changes, etc.

            if (roiSelecting) {
                return;
            }
            GL2 gl = drawable.getGL().getGL2();
            String glExt = gl.glGetString(GL.GL_EXTENSIONS);
            boolean hasBlend = false;
            if (glExt.indexOf("GL_EXT_blend_color") != -1) {
                try {
                    gl.glEnable(GL.GL_BLEND);
                    gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE);  // https://www.khronos.org/registry/OpenGL-Refpages/es1.1/xhtml/glBlendFunc.xml
                    gl.glBlendEquation(GL.GL_FUNC_ADD);
                    hasBlend = true;
                } catch (GLException e) {
                    log.warning("tried to use glBlend which is supposed to be available but got following exception");
                    gl.glDisable(GL.GL_BLEND);
                    e.printStackTrace();
                    hasBlend = false;
                }
            }

            // draw hist
            if (isiHistEnabled || showLatencyHistogramToExternalInputEvents) {
                renderer.begin3DRendering();
                if (!freqHistEnabled) {
                    renderer.draw3D(String.format("%ss", engFmt.format(1e-6f * isiMinUs)), -1, -6, 0, scale);
                    renderer.draw3D(String.format("%ss", engFmt.format(1e-6f * isiMaxUs)), chip.getSizeX() - 8, -6, 0, scale);
                } else {
                    renderer.draw3D(String.format("%sHz", engFmt.format(1e6f / isiMaxUs)), -1, -6, 0, scale);
                    renderer.draw3D(String.format("%sHz", engFmt.format(1e6f / isiMinUs)), chip.getSizeX() - 8, -6, 0, scale);
                }
                renderer.draw3D(logISIEnabled ? "log" : "linear", -15, -6, 0, scale);
                renderer.end3DRendering();
                gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
                gl.glPushMatrix();
                gl.glTranslatef(-5, chip.getSizeY() / 2, 0);
                gl.glRotatef(90, 0, 0, 1);
                renderer.begin3DRendering();
                renderer.draw3D(logProbEnabled ? "log" : "linear", 0, 0, 0, scale);
                renderer.end3DRendering();
                gl.glPopMatrix();
//                // ticks
//                if(logISIEnabled){
//                    int n=(int)Math.floor(logIsiMax-logIsiMin);
//                    double space=(chip.getSizeX())
//                    for(int i=0;i<n;i++){
//                        
//                    }
//                }
                renderer.flush();

                {
                    if (individualISIsEnabled) {
                        INDIV_HIST_COLOR[3] = histogramOpacity;
                        if (showIndividualISIHistograms) {
                            int n = 0;
                            for (IsiOrFreqHist h : histMap.values()) {
                                if (h.maxCount > 0) {
                                    n++;
                                }
                            }

                            gl.glPushMatrix();
                            gl.glLineWidth(1);
                            gl.glColor4fv(INDIV_HIST_COLOR, 0);
                            for (IsiOrFreqHist h : histMap.values()) {
                                if (h.maxCount == 0) {
                                    continue;
                                }
                                gl.glPushMatrix();
                                // if n=10 and sy=128 then scale=1/10 scale so that all n fit
                                // in viewpoort of chip, each one is scaled to chip size y
                                gl.glScalef(1, 1f / n, 1);
                                if (!(mouseHistAddress == h.address)) {
                                    h.draw(gl);
                                } else {
                                    h.draw(gl, 1, SELECT_COLOR);
//                                    renderer.begin3DRendering();
//                                    renderer.setColor(SELECT_COLOR[0], SELECT_COLOR[1], SELECT_COLOR[2], SELECT_COLOR[3]);
                                    String s = h.toString();
                                    log.info(s);
//                                    renderer.draw3D(s, currentMousePoint.x, currentMousePoint.y, 0, scale);
//                                    renderer.end3DRendering();

                                }
                                gl.glPopMatrix();
                                gl.glTranslatef(0, (float) chip.getSizeY() / n, 0);
                            }
                            gl.glPopMatrix();
                        }
                        if (showAverageISIHistogram) {
                            gl.glPushMatrix();
                            GLOBAL_HIST_COLOR[3] = histogramOpacity;
                            globalHist.draw(gl, 2, GLOBAL_HIST_COLOR);
                            gl.glPopMatrix();
                        }
                        if (showRateDistribution) {
                            gl.glPushMatrix();
                            RATE_HIST_COLOR[3] = histogramOpacity;
                            rateHist.draw(gl, 2, RATE_HIST_COLOR);
                            gl.glPopMatrix();
                        }

                    }
                }
                if (currentMousePoint != null) {
                    if (currentMousePoint.y <= 0) {
                        String s = null;
                        float x;
                        if (!freqHistEnabled) {
                            if (!logISIEnabled) {
                                x = (((float) currentMousePoint.x / chip.getSizeX()) * (1e-6f * ((stats.isiMaxUs - stats.isiMinUs)
                                        + stats.isiMinUs)));
                            } else {
                                x = (float) (1e-6 * Math.pow(10,
                                        (((float) currentMousePoint.x / chip.getSizeX())
                                        * (stats.logIsiMax - stats.logIsiMin))
                                        + stats.logIsiMin));
                            }
                            s = engFmt.format(x) + "s";
                        } else {
                            if (!logISIEnabled) {
                                x = (((float) currentMousePoint.x / chip.getSizeX())
                                        * (stats.freqMaxHz - stats.freqMinHz))
                                        + stats.freqMinHz;
                            } else {
                                x = (float) ((((float) currentMousePoint.x / chip.getSizeX())
                                        * (stats.logFreqMax - stats.logFreqMin))
                                        + stats.logFreqMin);
                                x = (float) Math.pow(10, x);
                            }
                            s = engFmt.format(x) + "Hz";
                        }
                        renderer.begin3DRendering();
                        renderer.setColor(SELECT_COLOR[0], SELECT_COLOR[1], SELECT_COLOR[2], SELECT_COLOR[3]);
                        renderer.draw3D(s, currentMousePoint.x, -12, 0, scale);
                        renderer.end3DRendering();
                        gl.glLineWidth(3);
                        gl.glColor3fv(SELECT_COLOR, 0);
                        gl.glBegin(GL.GL_LINES);
                        gl.glVertex2f(currentMousePoint.x, 0);
                        gl.glVertex2f(currentMousePoint.x, chip.getSizeY());
                        gl.glEnd();
                    }
                }
            }
            if (hasBlend) {
                try {
                    gl.glDisable(GL.GL_BLEND);
                } catch (GLException e) {
                }

            }
        }

        private void play() {
            if (!spikeSoundEnabled) {
                return;
            }
            if (spikeSound == null) {
                spikeSound = new SpikeSound();
            }
            if (eventCount > 0) {
                spikeSound.play();
            }
        }

        /**
         * @return the individualISIsEnabled
         */
        public boolean isIndividualISIsEnabled() {
            return individualISIsEnabled;
        }

        /**
         * @param individualISIsEnabled the individualISIsEnabled to set
         */
        public void setIndividualISIsEnabled(boolean individualISIsEnabled) {
            this.individualISIsEnabled = individualISIsEnabled;
            putBoolean("individualISIsEnabled", individualISIsEnabled);
        }

        /**
         * @return the isiMinUs
         */
        public int getIsiMinUs() {
            return isiMinUs;
        }

        /**
         * @param isiMinUs the isiMinUs to set
         */
        synchronized public void setIsiMinUs(int isiMinUs) {
            if (this.isiMinUs == isiMinUs) {
                return;
            }
            int old = this.isiMinUs;
            if (isiMinUs < 1) {
                isiMinUs = 1; // max freq is 1MHz for a pixel
            }
            this.isiMinUs = isiMinUs;
            logIsiMin = isiMinUs <= 0 ? 0 : Math.log10(isiMinUs);
            logIsiMax = Math.log10(isiMaxUs);
            freqMaxHz = 1e6f / isiMinUs;
            logFreqMax = Math.log10(freqMaxHz);
            if (isiAutoScalingEnabled) {
                getSupport().firePropertyChange("isiMinUs", old, isiMinUs);
            } else {
                putInt("isiMinUs", isiMinUs);
            }
            reset();
        }

        /**
         * @return the isiMaxUs
         */
        public int getIsiMaxUs() {
            return isiMaxUs;
        }

        /**
         * @param isiMaxUs the isiMaxUs to set
         */
        synchronized public void setIsiMaxUs(int isiMaxUs) {
            if (this.isiMaxUs == isiMaxUs) {
                return;
            }
            int old = this.isiMaxUs;
            this.isiMaxUs = isiMaxUs;
            logIsiMax = Math.log10(isiMaxUs);
            freqMinHz = 1e6f / isiMaxUs;
            logFreqMin = Math.log10(freqMinHz);
            if (isiAutoScalingEnabled) {
                getSupport().firePropertyChange("isiMaxUs", old, isiMaxUs);
            } else {
                putInt("isiMaxUs", isiMaxUs);
            }
            reset();
        }

        synchronized private void reset() {
            globalHist.reset();
            histMap.clear();
            stats.eventCountAfterExternalPinEvents.reset();
            rateFilter.reset();
            lastEventTimestamp = 0;
            lastRateMeasurementTimestamp = 0;
        }

        /**
         * Returns the value of the middle of the histogram bin in either Hz or
         * s depending on flag
         *
         * @param i the bin
         * @return the frequency of ISI
         */
        private float getIsiOrFreqBinCenterValue(int i) {
            if (!freqHistEnabled) { // ISI histogram
                if (!logISIEnabled) {
                    int binSize = (isiMaxUs - isiMinUs) / isiNumBins;
                    if (binSize <= 0) {
                        return -1;
                    }
                    return 1e-6f * ((i + .5f) * binSize + isiMinUs);
                } else {
                    double binSize = (logIsiMax - logIsiMin) / isiNumBins;
                    if (binSize <= 0) {
                        return -1;
                    }
                    return (float) (1e-6f * Math.pow(10, (i + .5) * binSize + logIsiMin));
                }
            } else { // frequency histogram
                if (!logISIEnabled) {
                    float binSize = (freqMaxHz - freqMinHz) / isiNumBins;
                    if (binSize <= 0) {
                        return -1;
                    }
                    return (float) (i + .5f) * binSize + freqMinHz;
                } else {
                    double binSize = (logFreqMax - logFreqMin) / isiNumBins;
                    if (binSize <= 0) {
                        return -1;
                    }
                    return (float) Math.pow(10, (i + .5f) * binSize + logFreqMin);
                }
            }
        }

        /**
         * Returns the bin number given isi in us
         *
         * @param isi the isi in us
         * @return the bin number
         */
        private int getIsiOrFreqBinNumber(int isi) {
            if (isi < isiMinUs) {
                return -1;
            } else if (isi > isiMaxUs) {
                return isiNumBins;
            } else {
                if (!freqHistEnabled) {
                    return getIsiBin(isi);
                } else { // frequency histogram
                    float freqHz = isi <= isiMinUs ? freqMaxHz : 1e6f / isi;
                    return getFreqBin(freqHz);
                }
            }
        }

        private int getIsiBin(int isi) {
            if (!logISIEnabled) {
                int binSize = (isiMaxUs - isiMinUs) / isiNumBins;
                if (binSize <= 0) {
                    return -1;
                }
                return (isi - isiMinUs) / binSize;
            } else {
                double logISI = isi <= 0 ? 0 : Math.log10(isi);
                double binSize = (logIsiMax - logIsiMin) / isiNumBins;
                if (binSize <= 0) {
                    return -1;
                }
                return (int) Math.floor((logISI - logIsiMin) / binSize);
            }
        }

        private int getFreqBin(float freqHz) {
            if (!logISIEnabled) {
                float binSize = (freqMaxHz - freqMinHz) / isiNumBins;
                if (binSize <= 0) {
                    return -1;
                }
                return (int) Math.floor((freqHz - freqMinHz) / binSize);
            } else {
                double logFreqHz = Math.log10(freqHz);
                double binSize = (logFreqMax - logFreqMin) / isiNumBins;
                if (binSize <= 0) {
                    return -1;
                }
                return (int) Math.floor((logFreqHz - logFreqMin) / binSize);
            }
        }

        /**
         * @return the isiNumBins
         */
        public int getIsiNumBins() {
            return isiNumBins;
        }

        /**
         * @param isiNumBins the isiNumBins to set
         */
        synchronized public void setIsiNumBins(int isiNumBins) {
            if (isiNumBins == this.isiNumBins) {
                return;
            }
            int old = this.isiNumBins;
            if (isiNumBins < 1) {
                isiNumBins = 1;
            } else if (isiNumBins > 1000) {
                log.warning("too many bins, limit is 1000");
                isiNumBins = 1000;
            }
            this.isiNumBins = isiNumBins;
            reset();
            putInt("isiNumBins", isiNumBins);
            getSupport().firePropertyChange("isiNumBins", old, isiNumBins);
        }

        /**
         * @return the isiAutoScalingEnabled
         */
        public boolean isIsiAutoScalingEnabled() {
            return isiAutoScalingEnabled;
        }

        /**
         * @param isiAutoScalingEnabled the isiAutoScalingEnabled to set
         */
        synchronized public void setIsiAutoScalingEnabled(boolean isiAutoScalingEnabled) {
            this.isiAutoScalingEnabled = isiAutoScalingEnabled;
            putBoolean("isiAutoScalingEnabled", isiAutoScalingEnabled);
        }

        private class EventCountsAfterInputPinEvents {

            int onRisingCount = 0;
            int offRisingCount = 0;
            int onFallingCount = 0;
            int offFallingCount = 0;
            private Phase phase = Phase.Uninitalized;
            int numRisingEdges = 0;
            int numFallingEdges = 0;
            float onEventsPerRisingPhasePerPixel = Float.NaN;
            float offEventsPerRisingPhasePerPixel = Float.NaN;
            float onEventsPerFallingPhasePerPixel = Float.NaN;
            float offEventsPerFallingPhasePerPixel = Float.NaN;
            int lastTimestamp = 0;
            int lowPeriod = 0, highPeriod = 0;
            int lastRisingTimstamp = 0, lastFallingTimestamp = 0;

            void reset() {
                numRisingEdges = 0;
                numFallingEdges = 0;
                onRisingCount = 0;
                offRisingCount = 0;
                onFallingCount = 0;
                offFallingCount = 0;
                setPhase(Phase.Uninitalized);
                onEventsPerRisingPhasePerPixel = Float.NaN;
                offEventsPerRisingPhasePerPixel = Float.NaN;
                onEventsPerFallingPhasePerPixel = Float.NaN;
                offEventsPerFallingPhasePerPixel = Float.NaN;
                lowPeriod = 0;
                highPeriod = 0;
            }

            void addEvent(BasicEvent b) {
                PolarityEvent e = (PolarityEvent) b; // TODO assumes DVS polarity event
                lastTimestamp = b.timestamp;
                if (highPeriod > 0 && lowPeriod > 0) { // if we've already detected a low and high period on sync
                    int dtfalling = lastTimestamp - lastFallingTimestamp, dtrising = lastTimestamp - lastRisingTimstamp;
                    if (dtfalling > 0 && dtrising > 0) { // we're after both edges 
                        if (dtfalling < dtrising) { // last edge falling
                            if (dtfalling <= lowPeriod / 2) {
                                setPhase(Phase.Falling);
                            } else {
                                setPhase(Phase.Rising);
                            }
                        } else { // last edge rising
                            if (dtrising <= highPeriod / 2) {
                                setPhase(Phase.Rising);
                            } else {
                                setPhase(Phase.Falling);
                            }
                        }
                    } else {
                        setPhase(Phase.Uninitalized);
                    }
                } else {
                    setPhase(Phase.Uninitalized);
                }

                if (e.isSpecial()) {
                    switch (e.address) {
                        case DavisChip.EXTERNAL_INPUT_ADDR_RISING:
                            numRisingEdges++;
                            if (numFallingEdges >= 1) {
                                lowPeriod = lastTimestamp - lastFallingTimestamp;
                            }
                            lastRisingTimstamp = lastTimestamp;
                            return;
                        case DavisChip.EXTERNAL_INPUT_EVENT_ADDR_FALLING:
                            numFallingEdges++;
                            if (numRisingEdges >= 1) {
                                highPeriod = lastTimestamp - lastRisingTimstamp;
                            }
                            lastFallingTimestamp = lastTimestamp;
                            return;
                        default:
                            log.fine("special event with address=" + e.address + ", which is not a valid Rising or Falling edge external input pin event");
                    }
                    return;
                }

                if (e.isFilteredOut()
                        || !insideRoi(e) || phase == Phase.Uninitalized) {
                    return;
                }
                switch (e.polarity) {
                    case On:
                        if (phase == Phase.Rising) {
                            onRisingCount++;
                        } else {
                            onFallingCount++;
                        }
                        break;
                    case Off:
                        if (phase == Phase.Rising) {
                            offRisingCount++;
                        } else {
                            offFallingCount++;
                        }
                        break;
                }
            }

            @Override
            public String toString() {
                int n = roiRect == null ? chip.getNumPixels() : roiRect.height * roiRect.width;
                if (numRisingEdges >= 2) {
                    onEventsPerRisingPhasePerPixel = (float) onRisingCount / n / (numRisingEdges - 1);
                    offEventsPerRisingPhasePerPixel = (float) offRisingCount / n / (numRisingEdges - 1);
                }
                if (numFallingEdges > 2) {
                    onEventsPerFallingPhasePerPixel = (float) onFallingCount / n / (numFallingEdges - 1);
                    offEventsPerFallingPhasePerPixel = (float) offFallingCount / n / (numFallingEdges - 1);
                }
                return String.format("Current phase: %s\n%d Rising edges, %d Falling edges\n"
                        + "Rising phase:  %d ON events, %d OFF events (%s ON/rise/pix, %s OFF/rise/pix)\n"
                        + "Falling phase: %d ON events, %d OFF events (%s ON/fall/pix, %s OFF/fall/pix)",
                        phase.toString(),
                        numRisingEdges, numFallingEdges,
                        onRisingCount, offRisingCount, engFmt.format(onEventsPerRisingPhasePerPixel), engFmt.format(offEventsPerRisingPhasePerPixel),
                        onFallingCount, offFallingCount, engFmt.format(onEventsPerFallingPhasePerPixel), engFmt.format(offEventsPerFallingPhasePerPixel)
                );
            }

            /**
             * @return the phase
             */
            public Phase getPhase() {
                return phase;
            }

            /**
             * @param phase the phase to set
             */
            public void setPhase(Phase phase) {
                Phase old = this.phase;
                this.phase = phase;
                if (old != this.phase) {
                    maybeSetRendererAccumulation();
                }
            }

            void maybeSetRendererAccumulation() {
                if (chip.getRenderer() == null) {
                    return;
                }
                AEChipRenderer renderer = chip.getRenderer();
                if (!accumulateEventsOnEachPhase) {
                    renderer.setAccumulateEnabled(false);
                    return;
                }
                switch (phase) {
                    case Rising:
                    case Falling:
                        renderer.resetFrame(renderer.getGrayValue());
                        renderer.setAccumulateEnabled(true);
                        break;
                    case Uninitalized:
                        renderer.setAccumulateEnabled(false);
                        break;
                }
            }

        }

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (isFilterEnabled() && resetOnBiasChange && (evt.getSource() instanceof AEChip)
                && !evt.getPropertyName().equals(DavisChip.PROPERTY_FRAME_RATE_HZ)
                && !evt.getPropertyName().equals(DavisVideoContrastController.AGC_VALUES)
                && !evt.getPropertyName().equals(DavisChip.PROPERTY_MEASURED_EXPOSURE_MS)) {
            resetFilter();
            log.info("reset filter on " + evt.toString());
        }
    }

    /**
     * @return the countDVSEventsBetweenExternalPinEvents
     */
    public boolean isCountDVSEventsBetweenExternalPinEvents() {
        return countDVSEventsBetweenExternalPinEvents;
    }

    /**
     * @param countDVSEventsBetweenExternalPinEvents the
     * countDVSEventsBetweenExternalPinEvents to set
     */
    public void setCountDVSEventsBetweenExternalPinEvents(boolean countDVSEventsBetweenExternalPinEvents) {
        this.countDVSEventsBetweenExternalPinEvents = countDVSEventsBetweenExternalPinEvents;
        putBoolean("countDVSEventsBetweenExternalPinEvents", countDVSEventsBetweenExternalPinEvents);
    }

    /**
     * @return the accumulateEventsOnEachPhase
     */
    public boolean isAccumulateEventsOnEachPhase() {
        return accumulateEventsOnEachPhase;
    }

    /**
     * @param accumulateEventsOnEachPhase the accumulateEventsOnEachPhase to set
     */
    public void setAccumulateEventsOnEachPhase(boolean accumulateEventsOnEachPhase) {
        this.accumulateEventsOnEachPhase = accumulateEventsOnEachPhase;
        putBoolean("accumulateEventsOnEachPhase", accumulateEventsOnEachPhase);
    }

    /**
     * @return the freqHistEnabled
     */
    public boolean isFreqHistEnabled() {
        return freqHistEnabled;
    }

    /**
     * @param freqHistEnabled the freqHistEnabled to set
     */
    public void setFreqHistEnabled(boolean freqHistEnabled) {
        boolean old = this.freqHistEnabled;
        this.freqHistEnabled = freqHistEnabled;
        putBoolean("freqHistEnabled", freqHistEnabled);
        if (old != freqHistEnabled) {
            resetFilter();
        }
        getSupport().firePropertyChange("freqHistEnabled", old, freqHistEnabled);
    }

    /**
     * @return the showRateDistribution
     */
    public boolean isShowRateDistribution() {
        return showRateDistribution;
    }

    /**
     * @param showRateDistribution the showRateDistribution to set
     */
    public void setShowRateDistribution(boolean showRateDistribution) {
        this.showRateDistribution = showRateDistribution;
        putBoolean("showRateDistribution", showRateDistribution);
    }

    /**
     * @return the histogramOpacity
     */
    public float getHistogramOpacity() {
        return histogramOpacity;
    }

    /**
     * @param histogramOpacity the histogramOpacity to set
     */
    public void setHistogramOpacity(float histogramOpacity) {
        if (histogramOpacity > 1) {
            histogramOpacity = 1;
        }
        this.histogramOpacity = histogramOpacity;
        putFloat("histogramOpacity", histogramOpacity);
    }

}
