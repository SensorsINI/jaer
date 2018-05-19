
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.minliu;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

import ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlow;
import com.jogamp.opengl.GLException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.TimeLimiter;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.ImageDisplay.Legend;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.TobiLogger;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Uses patch matching to measureTT local optical flow. <b>Not</b> gradient
 * based, but rather matches local features backwards in time.
 *
 * @author Tobi and Min, Jan 2016
 */
@Description("Computes optical flow with vector direction using block matching")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Speedo extends AbstractMotionFlow implements Observer, FrameAnnotater {

    /* LDSP is Large Diamond Search Pattern, and SDSP mens Small Diamond Search Pattern.
       LDSP has 9 points and SDSP consists of 5 points.
     */
    private static final int LDSP[][] = {{0, -2}, {-1, -1}, {1, -1}, {-2, 0}, {0, 0},
    {2, 0}, {-1, 1}, {1, 1}, {0, 2}};
    private static final int SDSP[][] = {{0, -1}, {-1, 0}, {0, 0}, {1, 0}, {0, 1}};

//    private int[][][] histograms = null;
    private int numSlices = 3; //getInt("numSlices", 3); // fix to 4 slices to compute error sign from min SAD result from t-2d to t-3d
    volatile private int numScales = getInt("numScales", 3); //getInt("numSlices", 3); // fix to 4 slices to compute error sign from min SAD result from t-2d to t-3d
    private String scalesToCompute = getString("scalesToCompute", ""); //getInt("numSlices", 3); // fix to 4 slices to compute error sign from min SAD result from t-2d to t-3d
    private int[] scalesToComputeArray = null; // holds array of scales to actually compute, for debugging
    private int[] scaleResultCounts = new int[numScales]; // holds counts at each scale for min SAD results
    /**
     * The computed average possible match distance from 0 motion
     */
    protected float avgPossibleMatchDistance;
    private static final int MIN_SLICE_EVENT_COUNT_FULL_FRAME = 50;
    private static final int MAX_SLICE_EVENT_COUNT_FULL_FRAME = 100000;

//    private int sx, sy;
    private int currentSliceIdx = 0; // the slice we are currently filling with events
    /**
     * time slice 2d histograms of (maybe signed) event counts slices = new
     * byte[numSlices][numScales][subSizeX][subSizeY] [slice][scale][x][y]
     */
    private byte[][][][] slices = null;
    private float[] sliceSummedSADValues = null; // tracks the total summed SAD differences between reference and past slices, to adjust the slice duration
    private int[] sliceSummedSADCounts = null; // tracks the total summed SAD differences between reference and past slices, to adjust the slice duration
    private int[] sliceStartTimeUs; // holds the time interval between reference slice and this slice
    private int[] sliceEndTimeUs; // holds the time interval between reference slice and this slice
    private byte[][][] currentSlice;
    private SADResult lastGoodSadResult = new SADResult(0, 0, 0, 0); // used for consistency check
    private int blockDimension = getInt("blockDimension", 23);
//    private float cost = getFloat("cost", 0.001f);
    private float maxAllowedSadDistance = getFloat("maxAllowedSadDistance", .5f);
    private float validPixOccupancy = getFloat("validPixOccupancy", 0.01f);  // threshold for valid pixel percent for one block
    private float weightDistance = getFloat("weightDistance", 0.95f);        // confidence value consists of the distance and the dispersion, this value set the distance value
    private static final int MAX_SKIP_COUNT = 1000;
    private int skipProcessingEventsCount = getInt("skipProcessingEventsCount", 0); // skip this many events for processing (but not for accumulating to bitmaps)
    private int skipCounter = 0;
    private boolean adaptiveEventSkipping = getBoolean("adaptiveEventSkipping", true);
    private float skipChangeFactor = (float) Math.sqrt(2); // by what factor to change the skip count if too slow or too fast
    private boolean outputSearchErrorInfo = false; // make user choose this slow down every time
    private boolean adaptiveSliceDuration = getBoolean("adaptiveSliceDuration", true);
    private boolean adaptiveSliceDurationLogging = false; // for debugging and analyzing control of slice event number/duration
    private TobiLogger adaptiveSliceDurationLogger = null;
    private int adaptiveSliceDurationPacketCount = 0;
    private boolean useSubsampling = getBoolean("useSubsampling", false);
    private int adaptiveSliceDurationMinVectorsToControl = getInt("adaptiveSliceDurationMinVectorsToControl", 10);
    private boolean showSliceBitMap = false; // Display the bitmaps
    private float adapativeSliceDurationProportionalErrorGain = 0.05f; // factor by which an error signal on match distance changes slice duration
    private int processingTimeLimitMs = getInt("processingTimeLimitMs", 100); // time limit for processing packet in ms to process OF events (events still accumulate). Overrides the system EventPacket timelimiter, which cannot be used here because we still need to accumulate and render the events.
    private int sliceMaxValue = getInt("sliceMaxValue", 7);
    private boolean rectifyPolarties = getBoolean("rectifyPolarties", false);
    private TimeLimiter timeLimiter = new TimeLimiter(); // private instance used to accumulate events to slices even if packet has timed out

    // results histogram for each packet
//    private int ANGLE_HISTOGRAM_COUNT = 16;
//    private int[] resultAngleHistogram = new int[ANGLE_HISTOGRAM_COUNT + 1];
    private int[][] resultHistogram = null;
//    private int resultAngleHistogramCount = 0, resultAngleHistogramMax = 0;
    private int resultHistogramCount;
    private volatile float avgMatchDistance = 0; // stores average match distance for rendering it
    private float histStdDev = 0, lastHistStdDev = 0;
    private float FSCnt = 0, DSCorrectCnt = 0;
    float DSAverageNum = 0, DSAveError[] = {0, 0};           // Evaluate DS cost average number and the error.
//    private float lastErrSign = Math.signum(1);
//    private final String outputFilename;
    private int sliceDeltaT;    //  The time difference between two slices used for velocity caluction. For constantDuration, this one is equal to the duration. For constantEventNumber, this value will change.
    private int MIN_SLICE_DURATION_US = 100;
    private int MAX_SLICE_DURATION_US = 300000;

    private boolean enableImuTimesliceLogging = false;
    private TobiLogger imuTimesliceLogger = null;

    public enum PatchCompareMethod {
        /*JaccardDistance,*/ /*HammingDistance*/
        SAD/*, EventSqeDistance*/
    };
    private PatchCompareMethod patchCompareMethod = null;

    public enum SearchMethod {
        FullSearch, DiamondSearch, CrossDiamondSearch
    };
    private SearchMethod searchMethod = SearchMethod.valueOf(getString("searchMethod", SearchMethod.DiamondSearch.toString()));

    private int sliceDurationUs = getInt("sliceDurationUs", 20000);
    private int sliceEventCount = getInt("sliceEventCount", 1000);
    private boolean rewindFlg = false; // The flag to indicate the rewind event.

    private boolean displayResultHistogram = getBoolean("displayResultHistogram", true);

    public enum SliceMethod {
        ConstantDuration, ConstantEventNumber, AreaEventNumber, ConstantIntegratedFlow, SpeedInput
    };
    private SliceMethod sliceMethod = SliceMethod.valueOf(getString("sliceMethod", SliceMethod.AreaEventNumber.toString()));
    // counting events into subsampled areas, when count exceeds the threshold in any area, the slices are rotated
    private int areaEventNumberSubsampling = getInt("areaEventNumberSubsampling", 5);
    private int[][] areaCounts = null;
    private boolean areaCountExceeded = false;
    //Parameters for SpeedInput SliceMethod
    private int speedDividend = getInt("SpeedDividend", 2500000);
    private int MIN_SPEED_DIVIDEND = 100000;
    private int MAX_SPEED_DIVIDEND = Integer.MAX_VALUE;
    private boolean endOfSpeedFile;
    private int calculatedFileSliceDuration;
    private float readFileSpeed;
    private int oldReadTimestamp, timestampPointer;
    private BufferedReader speedReader;
    private int[] timeStamps;
    private float[] speeds;
    private int SpeedInputTimeStamps = getInt("SpeedInputTimeStamps", 0);

    // nongreedy flow evaluation
    // the entire scene is subdivided into regions, and a bitmap of these regions distributed flow computation more fairly
    // by only servicing a region when sufficient fraction of other regions have been serviced first
    private boolean nonGreedyFlowComputingEnabled = getBoolean("nonGreedyFlowComputingEnabled", true);
    private boolean[][] nonGreedyRegions = null;
    private int nonGreedyRegionsNumberOfRegions, nonGreedyRegionsCount;
    /**
     * This fraction of the regions must be serviced for computing flow before
     * we reset the nonGreedyRegions map
     */
    private float nonGreedyFractionToBeServiced = getFloat("nonGreedyFractionToBeServiced", .5f);

    // timers and flags for showing filter properties temporarily
    private final int SHOW_STUFF_DURATION_MS = 4000;
    private volatile TimerTask stopShowingStuffTask = null;
    private boolean showBlockSizeAndSearchAreaTemporarily = false;
    private volatile boolean showAreaCountAreasTemporarily = false;

    private int eventCounter = 0;
    private int sliceLastTs = Integer.MAX_VALUE;

    private ImageDisplay sliceBitmapImageDisplay; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private JFrame sliceBitMapFrame = null;
    private Legend sliceBitmapLegend;

    /**
     * A PropertyChangeEvent with this value is fired when the slices has been
     * rotated. The oldValue is t-2d slice. The newValue is the t-d slice.
     */
    public static final String EVENT_NEW_SLICES = "eventNewSlices";

    TobiLogger sadValueLogger = new TobiLogger("sadvalues", "sadvalue,scale"); // TODO debug

    public Speedo(AEChip chip) {
        super(chip);

        setSliceDurationUs(getSliceDurationUs());   // 40ms is good for the start of the slice duration adatative since 4ms is too fast and 500ms is too slow.
        setDefaultScalesToCompute();

//        // Save the result to the file
//        Format formatter = new SimpleDateFormat("YYYY-MM-dd_hh-mm-ss");
//        // Instantiate a Date object
//        Date date = new Date();
        // Log file for the OF distribution's statistics
//        outputFilename = "PMF_HistStdDev" + formatter.format(date) + ".txt";
        String patchTT = "0a: Block matching";
//        String eventSqeMatching = "Event squence matching";
//        String preProcess = "Denoise";
        String metricConfid = "Confidence of current metric";
        try {
            patchCompareMethod = PatchCompareMethod.valueOf(getString("patchCompareMethod", PatchCompareMethod.SAD.toString()));
        } catch (IllegalArgumentException e) {
            patchCompareMethod = PatchCompareMethod.SAD;
        }

        chip.addObserver(this); // to allocate memory once chip size is known
        setPropertyTooltip(metricConfid, "maxAllowedSadDistance", "<html>SAD distance threshold for rejecting unresonable block matching result; <br> events with SAD distance larger than this value are rejected. <p>Lower value means it is harder to accept the event.");
        setPropertyTooltip(metricConfid, "validPixOccupancy", "<html>Threshold for valid pixel percent for each block; Range from 0 to 1. <p>If either matching block is less occupied than this fraction, no motion vector will be calculated.");
        setPropertyTooltip(metricConfid, "weightDistance", "<html>The confidence value consists of the distance and the dispersion; <br>weightDistance sets the weighting of the distance value compared with the dispersion value; Range from 0 to 1. <p>To count only e.g. hamming distance, set weighting to 1. <p> To count only dispersion, set to 0.");
        setPropertyTooltip(patchTT, "blockDimension", "linear dimenion of patches to match, in pixels");
        setPropertyTooltip(patchTT, "searchDistance", "search distance for matching patches, in pixels");
        setPropertyTooltip(patchTT, "patchCompareMethod", "method to compare two patches; SAD=sum of absolute differences, HammingDistance is same as SAD for binary bitmaps");
        setPropertyTooltip(patchTT, "searchMethod", "method to search patches");
        setPropertyTooltip(patchTT, "sliceDurationUs", "duration of bitmaps in us, also called sample interval, when ConstantDuration method is used");
        setPropertyTooltip(patchTT, "sliceEventCount", "number of events collected to fill a slice, when ConstantEventNumber method is used");
        setPropertyTooltip(patchTT, "sliceMethod", "<html>Method for determining time slice duration for block matching<ul>"
                + "<li>ConstantDuration: slices are fixed time duration"
                + "<li>ConstantEventNumber: slices are fixed event number"
                + "<li>AreaEventNumber: slices are fixed event number in any subsampled area defined by areaEventNumberSubsampling"
                + "<li>ConstantIntegratedFlow: slices are rotated when average speeds times delta time exceeds half the search distance"
                + "<li>SpeedInput: slice duration depends on speed input from file");
        setPropertyTooltip(patchTT, "areaEventNumberSubsampling", "<html>how to subsample total area to count events per unit subsampling blocks for AreaEventNumber method. <p>For example, if areaEventNumberSubsampling=5, <br> then events falling into 32x32 blocks of pixels are counted <br>to determine when they exceed sliceEventCount to make new slice");
        setPropertyTooltip(patchTT, "skipProcessingEventsCount", "skip this many events for processing (but not for accumulating to bitmaps)");
        setPropertyTooltip(patchTT, "adaptiveEventSkipping", "enables adaptive event skipping depending on free time left in AEViewer animation loop");
        setPropertyTooltip(patchTT, "adaptiveSliceDuration", "<html>Enables adaptive slice duration using feedback control, <br> based on average match search distance compared with total search distance. <p>If the match distance is too small, increaes duration or event count, and if too far, decreases duration or event count.<p>If using <i>AreaEventNumber</i> slice rotation method, don't increase count if actual duration is already longer than <i>sliceDurationUs</i>");
        setPropertyTooltip(patchTT, "nonGreedyFlowComputingEnabled", "<html>Enables fairer distribution of computing flow by areas; an area is only serviced after " + nonGreedyFractionToBeServiced + " fraction of areas have been serviced. <p> Areas are defined by the the area subsubsampling bit shift.<p>Enabling this option ignores event skipping, so use <i>processingTimeLimitMs</i> to ensure minimum frame rate");
        setPropertyTooltip(patchTT, "nonGreedyFractionToBeServiced", "An area is only serviced after " + nonGreedyFractionToBeServiced + " fraction of areas have been serviced. <p> Areas are defined by the the area subsubsampling bit shift.<p>Enabling this option ignores event skipping, so use the timeLimiter to ensure minimum frame rate");
        setPropertyTooltip(patchTT, "useSubsampling", "<html>Enables using both full and subsampled block matching; <p>when using adaptiveSliceDuration, enables adaptive slice duration using feedback controlusing difference between full and subsampled resolution slice matching");
        setPropertyTooltip(patchTT, "adaptiveSliceDurationMinVectorsToControl", "<html>Min flow vectors computed in packet to control slice duration, increase to reject control during idle periods");
        setPropertyTooltip(patchTT, "processingTimeLimitMs", "<html>time limit for processing packet in ms to process OF events (events still accumulate). <br> Set to 0 to disable. <p>Alternative to the system EventPacket timelimiter, which cannot be used here because we still need to accumulate and render the events");
        setPropertyTooltip(patchTT, "outputSearchErrorInfo", "enables displaying the search method error information");
        setPropertyTooltip(patchTT, "outlierMotionFilteringEnabled", "(Currently has no effect) discards first optical flow event that points in opposite direction as previous one (dot product is negative)");
        setPropertyTooltip(patchTT, "numSlices", "<html>Number of bitmaps to use.  <p>At least 3: 1 to collect on, and two more to match on. <br>If >3, then best match is found between last slice reference block and all previous slices.");
        setPropertyTooltip(patchTT, "numScales", "<html>Number of scales to search over for minimum SAD value; 1 for single full resolution scale, 2 for full + 2x2 subsampling, etc.");
        setPropertyTooltip(patchTT, "sliceMaxValue", "<html> the maximum value used to represent each pixel in the time slice:<br>1 for binary or signed binary slice, (in conjunction with rectifyEventPolarities==true), etc, <br>up to 127 by these byte values");
        setPropertyTooltip(patchTT, "rectifyPolarties", "<html> whether to rectify ON and OFF polarities to unsigned counts; true ignores polarity for block matching, false uses polarity with sliceNumBits>1");
        setPropertyTooltip(patchTT, "scalesToCompute", "Scales to compute, e.g. 1,2; blank for all scales. 0 is full resolution, 1 is subsampled 2x2, etc");
        setPropertyTooltip(patchTT, "showSlice", "Scales to compute, e.g. 1,2; blank for all scales. 0 is full resolution, 1 is subsampled 2x2, etc");
        setPropertyTooltip(patchTT, "defaults", "Sets reasonable defaults");
        setPropertyTooltip(patchTT, "enableImuTimesliceLogging", "Logs IMU and rate gyro");
        setPropertyTooltip(patchTT, "SpeedDividend", "This divided by the input speed gives the final SliceDurationUs");

        String patchDispTT = "0b: Block matching display";
        setPropertyTooltip(patchDispTT, "showSliceBitMap", "enables displaying the slices' bitmap");
        setPropertyTooltip(patchDispTT, "ppsScale", "scale of pixels per second to draw local motion vectors; global vectors are scaled up by an additional factor of " + GLOBAL_MOTION_DRAWING_SCALE);
        setPropertyTooltip(patchDispTT, "displayOutputVectors", "display the output motion vectors or not");
        setPropertyTooltip(patchDispTT, "displayResultHistogram", "display the output motion vectors histogram to show disribution of results for each packet. Only implemented for HammingDistance");

        getSupport().addPropertyChangeListener(AEViewer.EVENT_TIMESTAMPS_RESET, this);
        getSupport().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, this);
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_NON_MONOTONIC_TIMESTAMP, this);
        computeAveragePossibleMatchDistance();
    }

    // TODO debug
    public void doStartLogSadValues() {
        sadValueLogger.setEnabled(true);
    }
    // TODO debug

    public void doStopLogSadValues() {
        sadValueLogger.setEnabled(false);
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (cameraCalibration != null && cameraCalibration.isFilterEnabled()) {
            in = cameraCalibration.filterPacket(in);
        }
        setupFilter(in);
        checkArrays();
        if (processingTimeLimitMs > 0) {
            timeLimiter.setTimeLimitMs(processingTimeLimitMs);
            timeLimiter.restart();
        } else {
            timeLimiter.setEnabled(false);
        }

        for (int[] h : resultHistogram) {
            Arrays.fill(h, 0);
        }
        resultHistogramCount = 0;
//        Arrays.fill(resultAngleHistogram, 0);
//        resultAngleHistogramCount = 0;
//        resultAngleHistogramMax = Integer.MIN_VALUE;
        Arrays.fill(scaleResultCounts, 0);
        int minDistScale = 0;
        // following awkward block needed to deal with DVS/DAVIS and IMU/APS events
        // block STARTS
        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
        }

        nSkipped = 0;
        nProcessed = 0;

        while (i.hasNext()) {
            Object o = i.next();
            if (o == null) {
                log.warning("null event passed in, returning input packet");
                return in;
            }
            if ((o instanceof ApsDvsEvent) && ((ApsDvsEvent) o).isApsData()) {
                continue;
            }
            PolarityEvent ein = (PolarityEvent) o;

            if (!extractEventInfo(o)) {
                continue;
            }
            if (measureAccuracy || discardOutliersForStatisticalMeasurementEnabled) {
                if (imuFlowEstimator.calculateImuFlow(o)) {
                    continue;
                }
            }
            // block ENDS

            if (xyFilter()) {
                continue;
            }
            countIn++;

            // compute flow
            SADResult result = null;

            float[] sadVals = new float[numScales]; // TODO debug
            switch (patchCompareMethod) {
                case SAD:
                    boolean rotated = maybeRotateSlices();
                    if (rotated) {
                        adaptSliceDuration();
                    }
//                    if (ein.x >= subSizeX || ein.y > subSizeY) {
//                        log.warning("event out of range");
//                        continue;
//                    }
                    if (!accumulateEvent(ein)) { // maybe skip events here
                        break;
                    }
                    SADResult sliceResult;
                    minDistScale = 0;
                    for (int scale : scalesToComputeArray) {
                        if (scale >= numScales) {
                            log.warning("scale " + scale + " is out of range of " + numScales + "; fix scalesToCompute for example by clearing it");
                            break;
                        }
                        sliceResult = minSADDistance(ein.x, ein.y, slices[sliceIndex(1)], slices[sliceIndex(2)], scale); // from ref slice to past slice k+1, using scale 0,1,....
//                        sliceSummedSADValues[sliceIndex(scale + 2)] += sliceResult.sadValue; // accumulate SAD for this past slice
//                        sliceSummedSADCounts[sliceIndex(scale + 2)]++; // accumulate SAD count for this past slice
                        // sliceSummedSADValues should end up filling 2 values for 4 slices
                        if ((result == null) || (sliceResult.sadValue < result.sadValue)) {
                            result = sliceResult; // result holds the overall min sad result
                            minDistScale = scale;
                        }
                        sadVals[scale] = sliceResult.sadValue; // TODO debug

                    }
                    scaleResultCounts[minDistScale]++;
                    float dt = (sliceDeltaTimeUs(2) * 1e-6f);
                    if (result != null) {
                        result.vx = result.dx / dt; // hack, convert to pix/second
                        result.vy = result.dy / dt; // TODO clean up, make time for each slice, since could be different when const num events
                    }

                    break;
//                case JaccardDistance:
//                    maybeRotateSlices();
//                    if (!accumulateEvent(in)) {
//                        break;
//                    }
//                    result = minJaccardDistance(x, y, bitmaps[sliceIndex(2)], bitmaps[sliceIndex(1)]);
//                    float dtj=(sliceDeltaTimeUs(2) * 1e-6f);
//                    result.dx = result.dx / dtj;
//                    result.dy = result.dy / dtj;
//                    break;
            }
            if (result == null /*|| result.sadValue == Float.MAX_VALUE*/) {
                continue; // maybe some property change caused this
            }
            // reject values that are unreasonable
            if (isNotSufficientlyAccurate(result)) {
                continue;
            }
            vx = result.vx;
            vy = result.vy;
            v = (float) Math.sqrt((vx * vx) + (vy * vy));
            // TODO debug
            StringBuilder sadValsString = new StringBuilder();
            for (int k = 0; k < sadVals.length - 1; k++) {
                sadValsString.append(String.format("%f,", sadVals[k]));
            }
            sadValsString.append(String.format("%f", sadVals[sadVals.length - 1])); // very awkward to prevent trailing ,
            if (sadValueLogger.isEnabled()) { // TODO debug
                sadValueLogger.log(sadValsString.toString());
            }

            if (showSliceBitMap) {
                // TODO danger, drawing outside AWT thread
                drawMatching(result, ein, slices); // ein.x >> result.scale, ein.y >> result.scale, (int) result.dx >> result.scale, (int) result.dy >> result.scale, slices[sliceIndex(1)][result.scale], slices[sliceIndex(2)][result.scale], result.scale);
            }

//            if (filterOutInconsistentEvent(result)) {
//                continue;
//            }
            if (resultHistogram != null) {
                resultHistogram[result.xidx][result.yidx]++;
                resultHistogramCount++;
            }
//            if (result.dx != 0 || result.dy != 0) {
//                final int bin = (int) Math.round(ANGLE_HISTOGRAM_COUNT * (Math.atan2(result.dy, result.dx) + Math.PI) / (2 * Math.PI));
//                int v = ++resultAngleHistogram[bin];
//                resultAngleHistogramCount++;
//                if (v > resultAngleHistogramMax) {
//                    resultAngleHistogramMax = v;
//                }
//            }
            processGoodEvent();
            lastGoodSadResult.set(result);

        }

        if (rewindFlg) {
            rewindFlg = false;
            sliceLastTs = Integer.MAX_VALUE;

        }
         motionFlowStatistics.updatePacket(countIn, countOut, in.getLastTimestamp());
        adaptEventSkipping();

        return isDisplayRawInput() ? in : dirPacket;
    }

    public void doDefaults() {
        setSearchMethod(SearchMethod.DiamondSearch);
        setBlockDimension(21);
        setNumScales(2);
        setSearchDistance(4);
        setAdaptiveEventSkipping(true);
        setAdaptiveSliceDuration(true);
        setMaxAllowedSadDistance(.5f);
        setDisplayVectorsEnabled(true);
        setPpsScaleDisplayRelativeOFLength(true);
        setDisplayGlobalMotion(true);
        setPpsScale(.1f);
        setSliceMaxValue(7);
        setRectifyPolarties(true); // rectify to better handle cases of steadicam where pan/tilt flips event polarities
        setValidPixOccupancy(.01f); // at least this fraction of pixels from each block must both have nonzero values
        setSliceMethod(SliceMethod.AreaEventNumber);
        // compute nearest power of two over block dimension
        int ss = (int) (Math.log(blockDimension - 1) / Math.log(2));
        setAreaEventNumberSubsampling(ss);
        // set event count so that count=block area * sliceMaxValue/4;
        // i.e. set count to roll over when slice pixels from most subsampled scale are half full if they are half stimulated
        final int eventCount = (((blockDimension * blockDimension) * sliceMaxValue) / 2) >> (numScales - 1);
        setSliceEventCount(eventCount);
        setSliceDurationUs(50000); // set a bit smaller max duration in us to avoid instability where count gets too high with sparse input
    }

    private void adaptSliceDuration() {
        {
            // measure last hist to get control signal on slice duration
            // measures avg match distance.  weights the average so that long distances with more pixels in hist are not overcounted, simply
            // by having more pixels.
            float radiusSum = 0;
            int countSum = 0;

//            int maxRadius = (int) Math.ceil(Math.sqrt(2 * searchDistance * searchDistance));
//            int countSum = 0;
            final int totSD = searchDistance << (numScales - 1);
            for (int xx = -totSD; xx <= totSD; xx++) {
                for (int yy = -totSD; yy <= totSD; yy++) {
                    int count = resultHistogram[xx + totSD][yy + totSD];
                    if (count > 0) {
                        final float radius = (float) Math.sqrt((xx * xx) + (yy * yy));
                        countSum += count;
                        radiusSum += radius * count;
                    }
                }
            }

            if (countSum > 0) {
                avgMatchDistance = radiusSum / (countSum); // compute average match distance from reference block
            }
            if (adaptiveSliceDuration && (countSum > adaptiveSliceDurationMinVectorsToControl)) {
//            if (resultHistogramCount > 0) {

// following stats not currently used
//                double[] rstHist1D = new double[resultHistogram.length * resultHistogram.length];
//                int index = 0;
////                int rstHistMax = 0;
//                for (int[] resultHistogram1 : resultHistogram) {
//                    for (int element : resultHistogram1) {
//                        rstHist1D[index++] = element;
//                    }
//                }
//
//                Statistics histStats = new Statistics(rstHist1D);
//                // double histMax = Collections.max(Arrays.asList(ArrayUtils.toObject(rstHist1D)));
//                double histMax = histStats.getMax();
//                for (int m = 0; m < rstHist1D.length; m++) {
//                    rstHist1D[m] = rstHist1D[m] / histMax;
//                }
//                lastHistStdDev = histStdDev;
//
//                histStdDev = (float) histStats.getStdDev();
//                try (FileWriter outFile = new FileWriter(outputFilename,true)) {
//                            outFile.write(String.format(in.getFirstEvent().getTimestamp() + " " + histStdDev + "\r\n"));
//                            outFile.close();
//                } catch (IOException ex) {
//                    Logger.getLogger(PatchMatchFlow.class.getName()).log(Level.SEVERE, null, ex);
//                } catch (Exception e) {
//                    log.warning("Caught " + e + ". See following stack trace.");
//                    e.printStackTrace();
//                }
//                float histMean = (float) histStats.getMean();
// compute error signal.
// If err<0 it means the average match distance is larger than target avg match distance, so we need to reduce slice duration
// If err>0, it means the avg match distance is too short, so increse time slice
                final float err = avgPossibleMatchDistance / 2 - avgMatchDistance; // use target that is smaller than average possible to bound excursions to large slices better
//                final float err = ((searchDistance << (numScales - 1)) / 2) - avgMatchDistance;
//                final float lastErr = searchDistance / 2 - lastHistStdDev;
//                final double err = histMean - 1/ (rstHist1D.length * rstHist1D.length);
                float errSign = Math.signum(err);
//                float avgSad2 = sliceSummedSADValues[sliceIndex(4)] / sliceSummedSADCounts[sliceIndex(4)];
//                float avgSad3 = sliceSummedSADValues[sliceIndex(3)] / sliceSummedSADCounts[sliceIndex(3)];
//                float errSign = avgSad2 <= avgSad3 ? 1 : -1;

//                if(Math.abs(err) > Math.abs(lastErr)) {
//                    errSign = -errSign;
//                }
//                if(histStdDev >= 0.14) {
//                    if(lastHistStdDev > histStdDev) {
//                        errSign = -lastErrSign;
//                    } else {
//                        errSign = lastErrSign;
//                    }
//                    errSign = 1;
//                } else {
//                    errSign = (float) Math.signum(err);
//                }
//                lastErrSign = errSign;
// problem with following is that if sliceDurationUs gets really big, then of course the avgMatchDistance becomes small because
// of the biased-towards-zero search policy that selects the closest match
                switch (sliceMethod) {
                    case ConstantDuration:
                        int durChange = (int) (errSign * adapativeSliceDurationProportionalErrorGain * sliceDurationUs);
                        setSliceDurationUs(sliceDurationUs + durChange);
                        break;
                    case ConstantEventNumber:
                        break;
                    case AreaEventNumber:
                        if (errSign > 0 && sliceDeltaTimeUs(2) < getSliceDurationUs()) { // don't increase slice past the sliceDurationUs limit
                            // match too short, increase count
                            setSliceEventCount(Math.round(sliceEventCount * (1 + adapativeSliceDurationProportionalErrorGain)));
                        } else {
                            setSliceEventCount(Math.round(sliceEventCount * (1 - adapativeSliceDurationProportionalErrorGain)));
                        }
                        break;
                    case ConstantIntegratedFlow:
                        setSliceEventCount(eventCounter);
                        break;
                    case SpeedInput:
                }
                if (adaptiveSliceDurationLogger != null && adaptiveSliceDurationLogger.isEnabled()) {
                    if (!isDisplayGlobalMotion()) {
                        setDisplayGlobalMotion(true);
                    }
                    adaptiveSliceDurationLogger.log(String.format("%d\t%f\t%f\t%f\t%d\t%d", adaptiveSliceDurationPacketCount++, avgMatchDistance, err, motionFlowStatistics.getGlobalMotion().getGlobalSpeed().getMean(), sliceDurationUs, sliceEventCount));
                }
            }
        }
    }

    private EngineeringFormat engFmt = new EngineeringFormat();
    private TextRenderer textRenderer = null;

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        try {
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glBlendEquation(GL.GL_FUNC_ADD);
        } catch (GLException e) {
            e.printStackTrace();
        }
        if (displayResultHistogram && (resultHistogram != null)) {
            // draw histogram as shaded in 2d hist above color wheel
            // normalize hist
            int rhDim = resultHistogram.length; // 2*(searchDistance<<numScales)+1
            gl.glPushMatrix();
            final float scale = 30f / rhDim; // size same as the color wheel
            gl.glTranslatef(-35, .65f * chip.getSizeY(), 0);  // center above color wheel
            gl.glScalef(scale, scale, 1);
            gl.glColor3f(0, 0, 1);
            gl.glLineWidth(2f);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(rhDim, 0);
            gl.glVertex2f(rhDim, rhDim);
            gl.glVertex2f(0, rhDim);
            gl.glEnd();
            if (textRenderer == null) {
                textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 64));
            }
            int max = 0;
            for (int[] h : resultHistogram) {
                for (int vv : h) {
                    if (vv > max) {
                        max = vv;
                    }
                }
            }
            if (max == 0) {
                gl.glTranslatef(0, rhDim / 2, 0); // translate to UL corner of histogram
                textRenderer.begin3DRendering();
                textRenderer.draw3D("No data", 0, 0, 0, .07f);
                textRenderer.end3DRendering();
                gl.glPopMatrix();
            } else {
                final float maxRecip = 2f / max;
                gl.glPushMatrix();
                // draw hist values
                for (int xx = 0; xx < rhDim; xx++) {
                    for (int yy = 0; yy < rhDim; yy++) {
                        float g = maxRecip * resultHistogram[xx][yy];
                        gl.glColor3f(g, g, g);
                        gl.glBegin(GL2ES3.GL_QUADS);
                        gl.glVertex2f(xx, yy);
                        gl.glVertex2f(xx + 1, yy);
                        gl.glVertex2f(xx + 1, yy + 1);
                        gl.glVertex2f(xx, yy + 1);
                        gl.glEnd();
                    }
                }
                final int tsd = searchDistance << (numScales - 1);
                if (avgMatchDistance > 0) {
                    gl.glPushMatrix();
                    gl.glColor4f(1f, 0, 0, .5f);
                    gl.glLineWidth(5f);
                    DrawGL.drawCircle(gl, tsd + .5f, tsd + .5f, avgMatchDistance, 16);
                    gl.glPopMatrix();
                }
                if (avgPossibleMatchDistance > 0) {
                    gl.glPushMatrix();
                    gl.glColor4f(0, 1f, 0, .5f);
                    gl.glLineWidth(5f);
                    DrawGL.drawCircle(gl, tsd + .5f, tsd + .5f, avgPossibleMatchDistance / 2, 16); // draw circle at target match distance
                    gl.glPopMatrix();
                }
                // a bunch of cryptic crap to draw a string the same width as the histogram...
                gl.glPopMatrix();
                gl.glPopMatrix(); // back to original chip coordinates
                gl.glPushMatrix();
                textRenderer.begin3DRendering();
                String s = String.format("d=%.1f ms", 1e-3f * sliceDeltaT);
//            final float sc = TextRendererScale.draw3dScale(textRenderer, s, chip.getCanvas().getScale(), chip.getWidth(), .1f);
                // determine width of string in pixels and scale accordingly
                FontRenderContext frc = textRenderer.getFontRenderContext();
                Rectangle2D r = textRenderer.getBounds(s); // bounds in java2d coordinates, downwards more positive
                Rectangle2D rt = frc.getTransform().createTransformedShape(r).getBounds2D(); // get bounds in textrenderer coordinates
//            float ps = chip.getCanvas().getScale();
                float w = (float) rt.getWidth(); // width of text in textrenderer, i.e. histogram cell coordinates (1 unit = 1 histogram cell)
                float sc = subSizeX / w / 6; // scale to histogram width
                gl.glTranslatef(0, .65f * subSizeY, 0); // translate to UL corner of histogram
                textRenderer.draw3D(s, 0, 0, 0, sc);
                String s2 = String.format("skip: %d", skipProcessingEventsCount);
                textRenderer.draw3D(s2, 0, (float) (rt.getHeight()) * sc, 0, sc);
                String s3 = String.format("events: %d", sliceEventCount);
                textRenderer.draw3D(s3, 0, 2 * (float) (rt.getHeight()) * sc, 0, sc);
                StringBuilder sb = new StringBuilder("Scale counts: ");
                for (int c : scaleResultCounts) {
                    sb.append(String.format("%d ", c));
                }
                textRenderer.draw3D(sb.toString(), 0, (float) (3 * rt.getHeight()) * sc, 0, sc);
                String s4 = String.format("readSpeed: %f", speeds[timestampPointer]);
                textRenderer.draw3D(s4, 0, 4 * (float) (rt.getHeight()) * sc, 0, sc);
                String s5 = String.format("readTime: %d", timeStamps[timestampPointer]);
                textRenderer.draw3D(s5, 0, 5 * (float) (rt.getHeight()) * sc, 0, sc);
                textRenderer.end3DRendering();
                gl.glPopMatrix(); // back to original chip coordinates
//                log.info(String.format("processed %.1f%% (%d/%d)", 100 * (float) nProcessed / (nSkipped + nProcessed), nProcessed, (nProcessed + nSkipped)));

//                // draw histogram of angles around center of image
//                if (resultAngleHistogramCount > 0) {
//                    gl.glPushMatrix();
//                    gl.glTranslatef(subSizeX / 2, subSizeY / 2, 0);
//                    gl.glLineWidth(getMotionVectorLineWidthPixels());
//                    gl.glColor3f(1, 1, 1);
//                    gl.glBegin(GL.GL_LINES);
//                    for (int i = 0; i < ANGLE_HISTOGRAM_COUNT; i++) {
//                        float l = ((float) resultAngleHistogram[i] / resultAngleHistogramMax) * chip.getMinSize() / 2; // bin 0 is angle -PI
//                        double angle = ((2 * Math.PI * i) / ANGLE_HISTOGRAM_COUNT) - Math.PI;
//                        float dx = (float) Math.cos(angle) * l, dy = (float) Math.sin(angle) * l;
//                        gl.glVertex2f(0, 0);
//                        gl.glVertex2f(dx, dy);
//                    }
//                    gl.glEnd();
//                    gl.glPopMatrix();
//                }
            }
        }
        if (sliceMethod == SliceMethod.AreaEventNumber && showAreaCountAreasTemporarily) {
            int d = 1 << areaEventNumberSubsampling;
            gl.glLineWidth(2f);
            gl.glColor3f(1, 1, 1);
            gl.glBegin(GL.GL_LINES);
            for (int x = 0; x <= subSizeX; x += d) {
                gl.glVertex2f(x, 0);
                gl.glVertex2f(x, subSizeY);
            }
            for (int y = 0; y <= subSizeY; y += d) {
                gl.glVertex2f(0, y);
                gl.glVertex2f(subSizeX, y);
            }
            gl.glEnd();
        }

        if (sliceMethod == SliceMethod.ConstantIntegratedFlow && showAreaCountAreasTemporarily) {
            // TODO fill in what to draw
        }

        if (showBlockSizeAndSearchAreaTemporarily) {
            gl.glLineWidth(2f);
            gl.glColor3f(1, 0, 0);
            // show block size
            final int xx = subSizeX / 2, yy = subSizeY / 2, d = blockDimension / 2;
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(xx - d, yy - d);
            gl.glVertex2f(xx + d, yy - d);
            gl.glVertex2f(xx + d, yy + d);
            gl.glVertex2f(xx - d, yy + d);
            gl.glEnd();
            // show search area
            gl.glColor3f(0, 1, 0);
            final int sd = d + (searchDistance << (numScales - 1));
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(xx - sd, yy - sd);
            gl.glVertex2f(xx + sd, yy - sd);
            gl.glVertex2f(xx + sd, yy + sd);
            gl.glVertex2f(xx - sd, yy + sd);
            gl.glEnd();
        }
    }

    @Override
    public synchronized void resetFilter() {
        setSubSampleShift(0); // filter breaks with super's bit shift subsampling
        super.resetFilter();
        eventCounter = 0;
//        lastTs = Integer.MIN_VALUE;

        checkArrays();
        if (slices == null) {
            return;  // on reset maybe chip is not set yet
        }
        for (byte[][][] b : slices) {
            clearSlice(b);
        }

        currentSliceIdx = 0;  // start by filling slice 0
        currentSlice = slices[currentSliceIdx];

        sliceLastTs = Integer.MAX_VALUE;
        rewindFlg = true;
        if (adaptiveEventSkippingUpdateCounterLPFilter != null) {
            adaptiveEventSkippingUpdateCounterLPFilter.reset();
        }
        clearAreaCounts();
        clearNonGreedyRegions();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (!isFilterEnabled()) {
            return;
        }
        super.update(o, arg);
        if ((o instanceof AEChip) && (chip.getNumPixels() > 0)) {
            resetFilter();
        }
    }

    private LowpassFilter speedFilter = new LowpassFilter();

    /**
     * uses the current event to maybe rotate the slices
     *
     * @return true if slices were rotated
     */
    private boolean maybeRotateSlices() {
        int dt = ts - sliceLastTs;
        if (dt < 0 || rewindFlg) { // handle timestamp wrapping
//        System.out.println("rotated slices at ");
//        System.out.println("rotated slices with dt= "+dt);
            rotateSlices();
            eventCounter = 0;
            sliceDeltaT = dt;
            sliceLastTs = ts;
            return true;
        }

        switch (sliceMethod) {
            case ConstantDuration:
                if ((dt < sliceDurationUs)) {
                    return false;
                }
                break;
            case ConstantEventNumber:
                if (eventCounter < sliceEventCount) {
                    return false;
                }
                break;
            case AreaEventNumber:
                if (!areaCountExceeded && dt < MAX_SLICE_DURATION_US) {
                    return false;
                }
                break;
            case ConstantIntegratedFlow:
                speedFilter.setTauMs(sliceDeltaTimeUs(2) >> 10);
                final float meanGlobalSpeed = motionFlowStatistics.getGlobalMotion().meanGlobalSpeed;
                if (!Float.isNaN(meanGlobalSpeed)) {
                    speedFilter.filter(meanGlobalSpeed, ts);
                }
                final float filteredMeanGlobalSpeed = speedFilter.getValue();
                final float totalMovement = filteredMeanGlobalSpeed * dt * 1e-6f;
                if (Float.isNaN(meanGlobalSpeed)) { // we need to rotate slices somwhow even if there is no motion computed yet
                    if (eventCounter < sliceEventCount) {
                        return false;
                    }
                    if ((dt < sliceDurationUs)) {
                        return false;
                    }
                    break;
                }
                if (totalMovement < searchDistance / 2 && dt < sliceDurationUs) {
                    return false;
                }
            case SpeedInput:
                if (dt < getSpeedSliceDuration() && dt < MAX_SLICE_DURATION_US) {
                    return false;
                }
                break;
        }

        rotateSlices();

        /* Slices have been rotated */
        getSupport().firePropertyChange(PatchMatchFlow.EVENT_NEW_SLICES, slices[sliceIndex(1)], slices[sliceIndex(2)]);
        return true;

    }

    /**
     * Rotates slices by incrementing the slice pointer with rollover back to
     * zero, and sets currentSliceIdx and currentBitmap. Clears the new
     * currentBitmap. Thus the slice pointer increments. 0,1,2,0,1,2
     *
     */
    private void rotateSlices() {
        if (e != null) {
            sliceEndTimeUs[currentSliceIdx] = e.timestamp;
        }
        /*Thus if 0 is current index for current filling slice, then sliceIndex returns 1,2 for pointer =1,2.
        * Then if NUM_SLICES=3, after rotateSlices(),
        currentSliceIdx=NUM_SLICES-1=2, and sliceIndex(0)=2, sliceIndex(1)=0, sliceIndex(2)=1.
         */
        sliceSummedSADValues[currentSliceIdx] = 0; // clear out current collecting slice which becomes the oldest slice after rotation
        sliceSummedSADCounts[currentSliceIdx] = 0; // clear out current collecting slice which becomes the oldest slice after rotation
        currentSliceIdx--;
        if (currentSliceIdx < 0) {
            currentSliceIdx = numSlices - 1;
        }
        currentSlice = slices[currentSliceIdx];
        //sliceStartTimeUs[currentSliceIdx] = ts; // current event timestamp; set on first event to slice
        clearSlice(currentSlice);
        clearAreaCounts();
        eventCounter = 0;
        sliceDeltaT = ts - sliceLastTs;
        sliceLastTs = ts;
        if (imuTimesliceLogger != null && imuTimesliceLogger.isEnabled()) {
            imuTimesliceLogger.log(String.format("%d %d %.3f", ts, sliceDeltaT, imuFlowEstimator.getPanRateDps()));
        }
    }

    /**
     * Returns index to slice given pointer, with zero as current filling slice
     * pointer.
     *
     *
     * @param pointer how many slices in the past to index for. I.e.. 0 for
     * current slice (one being currently filled), 1 for next oldest, 2 for
     * oldest (when using NUM_SLICES=3).
     * @return index into bitmaps[]
     */
    private int sliceIndex(int pointer) {
        return (currentSliceIdx + pointer) % numSlices;
    }

    /**
     * returns slice delta time in us from reference slice
     *
     * @param pointer how many slices in the past to index for. I.e.. 0 for
     * current slice (one being currently filled), 1 for next oldest, 2 for
     * oldest (when using NUM_SLICES=3). Only meaningful for pointer>=2,
     * currently exactly only pointer==2 since we are using only 3 slices.
     *
     * Modified to compute the delta time using the average of start and end
     * timestamps of each slices, i.e. the slice time "midpoint" where midpoint
     * is defined by average of first and last timestamp.
     *
     */
    private int sliceDeltaTimeUs(int pointer) {
//        System.out.println("dt(" + pointer + ")=" + (sliceStartTimeUs[sliceIndex(1)] - sliceStartTimeUs[sliceIndex(pointer)]));
        int idxOlder = sliceIndex(pointer), idxYounger = sliceIndex(1);
        int tOlder = (sliceStartTimeUs[idxOlder] + sliceEndTimeUs[idxOlder]) / 2;
        int tYounger = (sliceStartTimeUs[idxYounger] + sliceEndTimeUs[idxYounger]) / 2;
        int dt = tYounger - tOlder;
        return dt;
    }

    private int nSkipped = 0, nProcessed = 0;

    /**
     * Accumulates the current event to the current slice
     *
     * @return true if subsequent processing should done, false if it should be
     * skipped for efficiency
     */
    synchronized private boolean accumulateEvent(PolarityEvent e) {
        if (eventCounter++ == 0) {
            sliceStartTimeUs[currentSliceIdx] = e.timestamp; // current event timestamp
        }
        for (int s = 0; s < numScales; s++) {
            final int xx = e.x >> s;
            final int yy = e.y >> s;
//            if (xx >= currentSlice[s].length || yy > currentSlice[s][xx].length) {
//                log.warning("event out of range");
//                return false;
//            }
            int cv = currentSlice[s][xx][yy];
            cv += rectifyPolarties ? 1 : (e.polarity == PolarityEvent.Polarity.On ? 1 : -1);
            if (cv > sliceMaxValue) {
                cv = sliceMaxValue;
            } else if (cv < -sliceMaxValue) {
                cv = -sliceMaxValue;
            }
            currentSlice[s][xx][yy] = (byte) cv;
        }
        if (sliceMethod == SliceMethod.AreaEventNumber) {
            if (areaCounts == null) {
                clearAreaCounts();
            }
            int c = ++areaCounts[e.x >> areaEventNumberSubsampling][e.y >> areaEventNumberSubsampling];
            if (c >= sliceEventCount) {
                areaCountExceeded = true;
//                int count=0, sum=0, sum2=0;
//                StringBuilder sb=new StringBuilder("Area counts:\n");
//                for(int[] i:areaCounts){
//                    for(int j:i){
//                        count++;
//                        sum+=j;
//                        sum2+=j*j;
//                        sb.append(String.format("%6d ",j));
//                    }
//                    sb.append("\n");
//                }
//                float m=(float)sum/count;
//                float s=(float)Math.sqrt((float)sum2/count-m*m);
//                sb.append(String.format("mean=%.1f, std=%.1f",m,s));
//                log.info("area count stats "+sb.toString());
            }
        }
        if (timeLimiter.isTimedOut()) {
            nSkipped++;
            return false;
        }
        if (nonGreedyFlowComputingEnabled) {
            // only process the event for flow if most of the other regions have already been processed
            int xx = e.x >> areaEventNumberSubsampling, yy = e.y >> areaEventNumberSubsampling;
            boolean didArea = nonGreedyRegions[xx][yy];
            if (!didArea) {
                nonGreedyRegions[xx][yy] = true;
                nonGreedyRegionsCount++;
                if (nonGreedyRegionsCount >= (int) (nonGreedyFractionToBeServiced * nonGreedyRegionsNumberOfRegions)) {
                    clearNonGreedyRegions();
                }
                nProcessed++;
                return true; // skip counter is ignored
            } else {
                nSkipped++;
                return false;
            }
        }
        if (skipProcessingEventsCount == 0) {
            nProcessed++;
            return true;
        }
        if (skipCounter++ < skipProcessingEventsCount) {
            nSkipped++;
            return false;
        }
        nProcessed++;
        skipCounter = 0;
        return true;
    }

//    private void clearSlice(int idx) {
//        for (int[] a : histograms[idx]) {
//            Arrays.fill(a, 0);
//        }
//    }
    private float sumArray[][] = null;

    /**
     * Computes block matching image difference best match around point x,y
     * using blockDimension and searchDistance and scale
     *
     * @param x coordinate in subsampled space
     * @param y
     * @param prevSlice the slice over which we search for best match
     * @param curSlice the slice from which we get the reference block
     * @param subSampleBy the scale to compute this SAD on, 0 for full
     * resolution, 1 for 2x2 subsampled block bitmap, etc
     * @return SADResult that provides the shift and SAD value
     */
//    private SADResult minHammingDistance(int x, int y, BitSet prevSlice, BitSet curSlice) {
    private SADResult minSADDistance(int x, int y, byte[][][] curSlice, byte[][][] prevSlice, int subSampleBy) {
        SADResult result = new SADResult();
        float minSum = Float.MAX_VALUE, sum;

        float FSDx = 0, FSDy = 0, DSDx = 0, DSDy = 0;  // This is for testing the DS search accuracy.
        final int searchRange = (2 * searchDistance) + 1; // The maximum search distance in this subSampleBy slice
        if ((sumArray == null) || (sumArray.length != searchRange)) {
            sumArray = new float[searchRange][searchRange];
        } else {
            for (float[] row : sumArray) {
                Arrays.fill(row, Float.MAX_VALUE);
            }
        }

        if (outputSearchErrorInfo) {
            searchMethod = SearchMethod.FullSearch;
        } else {
            searchMethod = getSearchMethod();
        }

        switch (searchMethod) {
            case DiamondSearch:
                // SD = small diamond, LD=large diamond SP=search process
                /* The center of the LDSP or SDSP could change in the iteration process,
                       so we need to use a variable to represent it.
                       In the first interation, it's the Zero Motion Potion (ZMP).
                 */
                int xCenter = x,
                 yCenter = y;

                /* x offset of center point relative to ZMP, y offset of center point to ZMP.
                       x offset of center pointin positive number to ZMP, y offset of center point in positive number to ZMP.
                 */
                int dx,
                 dy,
                 xidx,
                 yidx; // x and y best match offsets in pixels, indices of these in 2d hist

                int minPointIdx = 0;      // Store the minimum point index.
                boolean SDSPFlg = false;  // If this flag is set true, then it means LDSP search is finished and SDSP search could start.

                /* If one block has been already calculated, the computedFlg will be set so we don't to do
                       the calculation again.
                 */
                boolean computedFlg[][] = new boolean[searchRange][searchRange];
                for (boolean[] row : computedFlg) {
                    Arrays.fill(row, false);
                }

                if (searchDistance == 1) { // LDSP search can only be applied for search distance >= 2.
                    SDSPFlg = true;
                }

                int iterationsLeft = searchRange * searchRange;
                while (!SDSPFlg) {
                    /* 1. LDSP search */
                    for (int pointIdx = 0; pointIdx < LDSP.length; pointIdx++) {
                        dx = (LDSP[pointIdx][0] + xCenter) - x;
                        dy = (LDSP[pointIdx][1] + yCenter) - y;

                        xidx = dx + searchDistance;
                        yidx = dy + searchDistance;

                        // Point to be searched is out of search area, skip it.
                        if ((xidx >= searchRange) || (yidx >= searchRange) || (xidx < 0) || (yidx < 0)) {
                            continue;
                        }

                        /* We just calculate the blocks that haven't been calculated before */
                        if (computedFlg[xidx][yidx] == false) {
                            sumArray[xidx][yidx] = sadDistance(x, y, dx, dy, curSlice, prevSlice, subSampleBy);
                            computedFlg[xidx][yidx] = true;
                            if (outputSearchErrorInfo) {
                                DSAverageNum++;
                            }
                            if (outputSearchErrorInfo) {
                                if (sumArray[xidx][yidx] != sumArray[xidx][yidx]) { // TODO huh?  this is never true, compares to itself
                                    log.warning("It seems that there're some bugs in the DS algorithm.");
                                }
                            }
                        }

                        if (sumArray[xidx][yidx] <= minSum) {
                            minSum = sumArray[xidx][yidx];
                            minPointIdx = pointIdx;
                        }
                    }

                    /* 2. Check the minimum value position is in the center or not. */
                    xCenter = xCenter + LDSP[minPointIdx][0];
                    yCenter = yCenter + LDSP[minPointIdx][1];
                    if (minPointIdx == 4) { // It means it's in the center, so we should break the loop and go to SDSP search.
                        SDSPFlg = true;
                    }
                    if (--iterationsLeft < 0) {
                        log.warning("something is wrong with diamond search; did not find min in SDSP search");
                        SDSPFlg = true;
                    }
                }

                /* 3. SDSP Search */
                for (int[] element : SDSP) {
                    dx = (element[0] + xCenter) - x;
                    dy = (element[1] + yCenter) - y;

                    xidx = dx + searchDistance;
                    yidx = dy + searchDistance;

                    // Point to be searched is out of search area, skip it.
                    if ((xidx >= searchRange) || (yidx >= searchRange) || (xidx < 0) || (yidx < 0)) {
                        continue;
                    }

                    /* We just calculate the blocks that haven't been calculated before */
                    if (computedFlg[xidx][yidx] == false) {
                        sumArray[xidx][yidx] = sadDistance(x, y, dx, dy, curSlice, prevSlice, subSampleBy);
                        computedFlg[xidx][yidx] = true;
                        if (outputSearchErrorInfo) {
                            DSAverageNum++;
                        }
                        if (outputSearchErrorInfo) {
                            if (sumArray[xidx][yidx] != sumArray[xidx][yidx]) {
                                log.warning("It seems that there're some bugs in the DS algorithm.");
                            }
                        }
                    }

                    if (sumArray[xidx][yidx] <= minSum) {
                        minSum = sumArray[xidx][yidx];
                        result.dx = -dx;  // minus is because result points to the past slice and motion is in the other direction
                        result.dy = -dy;
                        result.sadValue = minSum;
                    }

//                    // debug
//                    if(result.dx==-searchDistance && result.dy==-searchDistance){
//                        System.out.println(result);
//                    }
                }

                if (outputSearchErrorInfo) {
                    DSDx = result.dx;
                    DSDy = result.dy;
                }
                break;
            case FullSearch:
                for (dx = -searchDistance; dx <= searchDistance; dx++) {
                    for (dy = -searchDistance; dy <= searchDistance; dy++) {
                        sum = sadDistance(x, y, dx, dy, curSlice, prevSlice, subSampleBy);
                        sumArray[dx + searchDistance][dy + searchDistance] = sum;
                        if (sum < minSum) {
                            minSum = sum;
                            result.dx = -dx; // minus is because result points to the past slice and motion is in the other direction
                            result.dy = -dy;
                            result.sadValue = minSum;
                        }
                    }
                }
                if (outputSearchErrorInfo) {
                    FSCnt += 1;
                    FSDx = result.dx;
                    FSDy = result.dy;
                } else {
                    break;
                }
            case CrossDiamondSearch:
                break;
        }
        // compute the indices into 2d histogram of all motion vector results.
        // It's a bit complicated because of multiple scales.
        // Also, we want the indexes to be centered in the histogram array so that searches at full scale appear at the middle
        // of the array and not at 0,0 corner.
        // Suppose searchDistance=1 and numScales=2. Then the histogram has size 2*2+1=5.
        // Therefore the scale 0 results need to have offset added to them to center results in histogram that
        // shows results over all scales.

        result.scale = subSampleBy;
        // convert dx in search steps to dx in pixels including subsampling
        // compute index assuming no subsampling or centering
        result.xidx = result.dx + searchDistance;
        result.yidx = result.dy + searchDistance;
        // compute final dx and dy including subsampling
        result.dx = result.dx << subSampleBy;
        result.dy = result.dy << subSampleBy;
        // compute final index including subsampling and centering
        // idxCentering is shift needed to be applyed to store this result finally into the hist,
        final int idxCentering = (searchDistance << (numScales - 1)) - ((searchDistance) << subSampleBy); // i.e. for subSampleBy=0 and numScales=2, shift=1 so that full scale search is centered in 5x5 hist
        result.xidx = (result.xidx << subSampleBy) + idxCentering;
        result.yidx = (result.yidx << subSampleBy) + idxCentering;

//        if (result.xidx < 0 || result.yidx < 0 || result.xidx > maxIdx || result.yidx > maxIdx) {
//            log.warning("something wrong with result=" + result);
//            return null;
//        }
        if (outputSearchErrorInfo) {
            if ((DSDx == FSDx) && (DSDy == FSDy)) {
                DSCorrectCnt += 1;
            } else {
                DSAveError[0] += Math.abs(DSDx - FSDx);
                DSAveError[1] += Math.abs(DSDy - FSDy);
            }
            if (0 == (FSCnt % 10000)) {
                log.log(Level.INFO, "Correct Diamond Search times are {0}, Full Search times are {1}, accuracy is {2}, averageNumberPercent is {3}, averageError is ({4}, {5})",
                        new Object[]{DSCorrectCnt, FSCnt, DSCorrectCnt / FSCnt, DSAverageNum / (searchRange * searchRange * FSCnt), DSAveError[0] / FSCnt, DSAveError[1] / (FSCnt - DSCorrectCnt)});
            }
        }

//        if (tmpSadResult.xidx == searchRange-1 && tmpSadResult.yidx == searchRange-1) {
//            tmpSadResult.sadValue = 1; // reject results to top right that are likely result of ambiguous search
//        }
        return result;
    }

    /**
     * computes Hamming distance centered on x,y with patch of patchSize for
     * prevSliceIdx relative to curSliceIdx patch.
     *
     * @param xfull coordinate x in full resolution
     * @param yfull coordinate y in full resolution
     * @param dx the offset in pixels in the subsampled space of the past slice.
     * The motion vector is then *from* this position *to* the current slice.
     * @param dy
     * @param prevSlice
     * @param curSlice
     * @param subsampleBy the scale to search over
     * @return Distance value, max 1 when all pixels differ, min 0 when all the
     * same
     */
    private float sadDistance(final int xfull, final int yfull,
            final int dx, final int dy,
            final byte[][][] curSlice,
            final byte[][][] prevSlice,
            final int subsampleBy) {
        final int x = xfull >> subsampleBy;
        final int y = yfull >> subsampleBy;
        final int r = ((blockDimension) / 2);
        int w = subSizeX >> subsampleBy, h = subSizeY >> subsampleBy;
        int adx = dx > 0 ? dx : -dx; // abs val of dx and dy, to compute limits
        int ady = dy > 0 ? dy : -dy;

        // Make sure both ref block and past slice block are in bounds on all sides or there'll be arrayIndexOutOfBoundary exception.
        // Also we don't want to match ref block only on inner sides or there will be a bias towards motion towards middle
        if (x - r - adx < 0 || x + r + adx >= w
                || y - r - ady < 0 || y + r + ady >= h) {
            return 1; // tobi changed to 1 again // Float.MAX_VALUE; // return very large distance for this match so it is not selected
        }

        int validPixNumCurSlice = 0, validPixNumPrevSlice = 0; // The valid pixel number in the current block
        int nonZeroMatchCount = 0;
//        int saturatedPixNumCurSlice = 0, saturatedPixNumPrevSlice = 0; // The valid pixel number in the current block
        int sumDist = 0;
//        try {
        for (int xx = x - r; xx <= (x + r); xx++) {
            for (int yy = y - r; yy <= (y + r); yy++) {
//                if (xx < 0 || yy < 0 || xx >= w || yy >= h
//                        || xx + dx < 0 || yy + dy < 0 || xx + dx >= w || yy + dy >= h) {
////                    log.warning("out of bounds slice access; something wrong"); // TODO fix this check above
//                    continue;
//                }
                int currSliceVal = curSlice[subsampleBy][xx][yy]; // binary value on (xx, yy) for current slice
                int prevSliceVal = prevSlice[subsampleBy][xx + dx][yy + dy]; // binary value on (xx, yy) for previous slice at offset dx,dy in (possibly subsampled) slice
                int dist = (currSliceVal - prevSliceVal);
                if (dist < 0) {
                    dist = (-dist);
                }
                sumDist += dist;
//                if (currSlicePol != prevSlicePol) {
//                    hd += 1;
//                }

//                if (currSliceVal == sliceMaxValue || currSliceVal == -sliceMaxValue) {
//                    saturatedPixNumCurSlice++; // pixels that are not saturated
//                }
//                if (prevSliceVal == sliceMaxValue || prevSliceVal == -sliceMaxValue) {
//                    saturatedPixNumPrevSlice++;
//                }
                if (currSliceVal != 0) {
                    validPixNumCurSlice++; // pixels that are not saturated
                }
                if (prevSliceVal != 0) {
                    validPixNumPrevSlice++;
                }
                if (currSliceVal != 0 && prevSliceVal != 0) {
                    nonZeroMatchCount++; // pixels that both have events in them
                }
            }
        }
//        } catch (ArrayIndexOutOfBoundsException ex) {
//            log.warning(ex.toString());
//
//        }
        // debug
//        if(dx==-1 && dy==-1) return 0; else return Float.MAX_VALUE;

        // normalize by dimesion of subsampling, with idea that subsampling increases SAD
        //by sqrt(area) because of Gaussian distribution of SAD values
        sumDist = sumDist >> (subsampleBy << 1);
        final int blockDim = (2 * r) + 1;

        final int blockArea = (blockDim) * (blockDim); // TODO check math here for fraction correct with subsampling
        // TODD: NEXT WORK IS TO DO THE RESEARCH ON WEIGHTED HAMMING DISTANCE
        // Calculate the metric confidence value
        final int minValidPixNum = (int) (this.validPixOccupancy * blockArea);
//        final int maxSaturatedPixNum = (int) ((1 - this.validPixOccupancy) * blockArea);
        final float sadNormalizer = 1f / (blockArea * (rectifyPolarties ? 2 : 1) * sliceMaxValue);
        // if current or previous block has insufficient pixels with values or if all the pixels are filled up, then reject match
        if ((validPixNumCurSlice < minValidPixNum)
                || (validPixNumPrevSlice < minValidPixNum)
                || (nonZeroMatchCount < minValidPixNum) //                || (saturatedPixNumCurSlice >= maxSaturatedPixNum) || (saturatedPixNumPrevSlice >= maxSaturatedPixNum)
                ) {  // If valid pixel number of any slice is 0, then we set the distance to very big value so we can exclude it.
            return 1; // tobi changed to 1 to represent max distance // Float.MAX_VALUE;
        } else {
            /*
            retVal consists of the distance and the dispersion. dispersion is used to describe the spatial relationship within one block.
            Here we use the difference between validPixNumCurrSli and validPixNumPrevSli to calculate the dispersion.
            Inspired by paper "Measuring the spatial dispersion of evolutionist search process: application to Walksat" by Alain Sidaner.
             */
            final float finalDistance = sadNormalizer * ((sumDist * weightDistance) + (Math.abs(validPixNumCurSlice - validPixNumPrevSlice) * (1 - weightDistance)));
            return finalDistance;
        }
    }

    /**
     * Computes hamming weight around point x,y using blockDimension and
     * searchDistance
     *
     * @param x coordinate in subsampled space
     * @param y
     * @param prevSlice
     * @param curSlice
     * @return SADResult that provides the shift and SAD value
     */
//    private SADResult minJaccardDistance(int x, int y, BitSet prevSlice, BitSet curSlice) {
//    private SADResult minJaccardDistance(int x, int y, byte[][] prevSlice, byte[][] curSlice) {
//        float minSum = Integer.MAX_VALUE, sum = 0;
//        SADResult sadResult = new SADResult(0, 0, 0);
//        for (int dx = -searchDistance; dx <= searchDistance; dx++) {
//            for (int dy = -searchDistance; dy <= searchDistance; dy++) {
//                sum = jaccardDistance(x, y, dx, dy, prevSlice, curSlice);
//                if (sum <= minSum) {
//                    minSum = sum;
//                    sadResult.dx = dx;
//                    sadResult.dy = dy;
//                    sadResult.sadValue = minSum;
//                }
//            }
//        }
//
//        return sadResult;
//    }
    /**
     * computes Hamming distance centered on x,y with patch of patchSize for
     * prevSliceIdx relative to curSliceIdx patch.
     *
     * @param x coordinate in subSampled space
     * @param y
     * @param patchSize
     * @param prevSlice
     * @param curSlice
     * @return SAD value
     */
//    private float jaccardDistance(int x, int y, int dx, int dy, BitSet prevSlice, BitSet curSlice) {
    private float jaccardDistance(int x, int y, int dx, int dy, boolean[][] prevSlice, boolean[][] curSlice) {
        float M01 = 0, M10 = 0, M11 = 0;
        int blockRadius = blockDimension / 2;

        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
        if ((x < (blockRadius + dx)) || (x >= ((subSizeX - blockRadius) + dx)) || (x < blockRadius) || (x >= (subSizeX - blockRadius))
                || (y < (blockRadius + dy)) || (y >= ((subSizeY - blockRadius) + dy)) || (y < blockRadius) || (y >= (subSizeY - blockRadius))) {
            return 1; // changed back to 1 // Float.MAX_VALUE;
        }

        for (int xx = x - blockRadius; xx <= (x + blockRadius); xx++) {
            for (int yy = y - blockRadius; yy <= (y + blockRadius); yy++) {
                final boolean c = curSlice[xx][yy], p = prevSlice[xx - dx][yy - dy];
                if ((c == true) && (p == true)) {
                    M11 += 1;
                }
                if ((c == true) && (p == false)) {
                    M01 += 1;
                }
                if ((c == false) && (p == true)) {
                    M10 += 1;
                }
//                if ((curSlice.get((xx + 1) + ((yy) * subSizeX)) == true) && (prevSlice.get(((xx + 1) - dx) + ((yy - dy) * subSizeX)) == true)) {
//                    M11 += 1;
//                }
//                if ((curSlice.get((xx + 1) + ((yy) * subSizeX)) == true) && (prevSlice.get(((xx + 1) - dx) + ((yy - dy) * subSizeX)) == false)) {
//                    M01 += 1;
//                }
//                if ((curSlice.get((xx + 1) + ((yy) * subSizeX)) == false) && (prevSlice.get(((xx + 1) - dx) + ((yy - dy) * subSizeX)) == true)) {
//                    M10 += 1;
//                }
            }
        }
        float retVal;
        if (0 == (M01 + M10 + M11)) {
            retVal = 0;
        } else {
            retVal = M11 / (M01 + M10 + M11);
        }
        retVal = 1 - retVal;
        return retVal;
    }

//    private SADResult minVicPurDistance(int blockX, int blockY) {
//        ArrayList<Integer[]> seq1 = new ArrayList(1);
//        SADResult sadResult = new SADResult(0, 0, 0);
//
//        int size = spikeTrains[blockX][blockY].size();
//        int lastTs = spikeTrains[blockX][blockY].get(size - forwardEventNum)[0];
//        for (int i = size - forwardEventNum; i < size; i++) {
//            seq1.add(spikeTrains[blockX][blockY].get(i));
//        }
//
////        if(seq1.get(2)[0] - seq1.get(0)[0] > thresholdTime) {
////            return sadResult;
////        }
//        double minium = Integer.MAX_VALUE;
//        for (int i = -1; i < 2; i++) {
//            for (int j = -1; j < 2; j++) {
//                // Remove the seq1 itself
//                if ((0 == i) && (0 == j)) {
//                    continue;
//                }
//                ArrayList<Integer[]> seq2 = new ArrayList(1);
//
//                if ((blockX >= 2) && (blockY >= 2)) {
//                    ArrayList<Integer[]> tmpSpikes = spikeTrains[blockX + i][blockY + j];
//                    if (tmpSpikes != null) {
//                        for (int index = 0; index < tmpSpikes.size(); index++) {
//                            if (tmpSpikes.get(index)[0] >= lastTs) {
//                                seq2.add(tmpSpikes.get(index));
//                            }
//                        }
//
//                        double dis = vicPurDistance(seq1, seq2);
//                        if (dis < minium) {
//                            minium = dis;
//                            sadResult.dx = -i;
//                            sadResult.dy = -j;
//
//                        }
//                    }
//
//                }
//
//            }
//        }
//        lastFireIndex[blockX][blockY] = spikeTrains[blockX][blockY].size() - 1;
//        if ((sadResult.dx != 1) || (sadResult.dy != 0)) {
//            // sadResult = new SADResult(0, 0, 0);
//        }
//        return sadResult;
//    }
//    private double vicPurDistance(ArrayList<Integer[]> seq1, ArrayList<Integer[]> seq2) {
//        int sum1Plus = 0, sum1Minus = 0, sum2Plus = 0, sum2Minus = 0;
//        Iterator itr1 = seq1.iterator();
//        Iterator itr2 = seq2.iterator();
//        int length1 = seq1.size();
//        int length2 = seq2.size();
//        double[][] distanceMatrix = new double[length1 + 1][length2 + 1];
//
//        for (int h = 0; h <= length1; h++) {
//            for (int k = 0; k <= length2; k++) {
//                if (h == 0) {
//                    distanceMatrix[h][k] = k;
//                    continue;
//                }
//                if (k == 0) {
//                    distanceMatrix[h][k] = h;
//                    continue;
//                }
//
//                double tmpMin = Math.min(distanceMatrix[h][k - 1] + 1, distanceMatrix[h - 1][k] + 1);
//                double event1 = seq1.get(h - 1)[0] - seq1.get(0)[0];
//                double event2 = seq2.get(k - 1)[0] - seq2.get(0)[0];
//                distanceMatrix[h][k] = Math.min(tmpMin, distanceMatrix[h - 1][k - 1] + (cost * Math.abs(event1 - event2)));
//            }
//        }
//
//        while (itr1.hasNext()) {
//            Integer[] ii = (Integer[]) itr1.next();
//            if (ii[1] == 1) {
//                sum1Plus += 1;
//            } else {
//                sum1Minus += 1;
//            }
//        }
//
//        while (itr2.hasNext()) {
//            Integer[] ii = (Integer[]) itr2.next();
//            if (ii[1] == 1) {
//                sum2Plus += 1;
//            } else {
//                sum2Minus += 1;
//            }
//        }
//
//        // return Math.abs(sum1Plus - sum2Plus) + Math.abs(sum1Minus - sum2Minus);
//        return distanceMatrix[length1][length2];
//    }
//    /**
//     * Computes min SAD shift around point x,y using blockDimension and
//     * searchDistance
//     *
//     * @param x coordinate in subsampled space
//     * @param y
//     * @param prevSlice
//     * @param curSlice
//     * @return SADResult that provides the shift and SAD value
//     */
//    private SADResult minSad(int x, int y, BitSet prevSlice, BitSet curSlice) {
//        // for now just do exhaustive search over all shifts up to +/-searchDistance
//        SADResult sadResult = new SADResult(0, 0, 0);
//        float minSad = 1;
//        for (int dx = -searchDistance; dx <= searchDistance; dx++) {
//            for (int dy = -searchDistance; dy <= searchDistance; dy++) {
//                float sad = sad(x, y, dx, dy, prevSlice, curSlice);
//                if (sad <= minSad) {
//                    minSad = sad;
//                    sadResult.dx = dx;
//                    sadResult.dy = dy;
//                    sadResult.sadValue = minSad;
//                }
//            }
//        }
//        return sadResult;
//    }
//    /**
//     * computes SAD centered on x,y with shift of dx,dy for prevSliceIdx
//     * relative to curSliceIdx patch.
//     *
//     * @param x coordinate x in subSampled space
//     * @param y coordinate y in subSampled space
//     * @param dx block shift of x
//     * @param dy block shift of y
//     * @param prevSliceIdx
//     * @param curSliceIdx
//     * @return SAD value
//     */
//    private float sad(int x, int y, int dx, int dy, BitSet prevSlice, BitSet curSlice) {
//        int blockRadius = blockDimension / 2;
//        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
//        if ((x < (blockRadius + dx)) || (x >= ((subSizeX - blockRadius) + dx)) || (x < blockRadius) || (x >= (subSizeX - blockRadius))
//                || (y < (blockRadius + dy)) || (y >= ((subSizeY - blockRadius) + dy)) || (y < blockRadius) || (y >= (subSizeY - blockRadius))) {
//            return Float.MAX_VALUE;
//        }
//
//        float sad = 0, retVal = 0;
//        float validPixNumCurrSli = 0, validPixNumPrevSli = 0; // The valid pixel number in the current block
//        for (int xx = x - blockRadius; xx <= (x + blockRadius); xx++) {
//            for (int yy = y - blockRadius; yy <= (y + blockRadius); yy++) {
//                boolean currSlicePol = curSlice.get((xx + 1) + ((yy) * subSizeX)); // binary value on (xx, yy) for current slice
//                boolean prevSlicePol = prevSlice.get(((xx + 1) - dx) + ((yy - dy) * subSizeX)); // binary value on (xx, yy) for previous slice
//
//                int imuWarningDialog = (currSlicePol ? 1 : 0) - (prevSlicePol ? 1 : 0);
//                if (currSlicePol == true) {
//                    validPixNumCurrSli += 1;
//                }
//                if (prevSlicePol == true) {
//                    validPixNumPrevSli += 1;
//                }
//                if (imuWarningDialog <= 0) {
//                    imuWarningDialog = -imuWarningDialog;
//                }
//                sad += imuWarningDialog;
//            }
//        }
//
//        // Calculate the metric confidence value
//        float validPixNum = this.validPixOccupancy * (((2 * blockRadius) + 1) * ((2 * blockRadius) + 1));
//        if ((validPixNumCurrSli <= validPixNum) || (validPixNumPrevSli <= validPixNum)) {  // If valid pixel number of any slice is 0, then we set the distance to very big value so we can exclude it.
//            retVal = 1;
//        } else {
//            /*
//            retVal is consisted of the distance and the dispersion, dispersion is used to describe the spatial relationship within one block.
//            Here we use the difference between validPixNumCurrSli and validPixNumPrevSli to calculate the dispersion.
//            Inspired by paper "Measuring the spatial dispersion of evolutionist search process: application to Walksat" by Alain Sidaner.
//             */
//            retVal = ((sad * weightDistance) + (Math.abs(validPixNumCurrSli - validPixNumPrevSli) * (1 - weightDistance))) / (((2 * blockRadius) + 1) * ((2 * blockRadius) + 1));
//        }
//        return retVal;
//    }
    private class SADResult {

        int dx, dy; // best match offset in pixels to reference block from past slice block, i.e. motion vector points in this direction
        float vx, vy; // optical flow in pixels/second corresponding to this match
        float sadValue; // sum of absolute differences for this best match normalized by number of pixels in reference area
        int xidx, yidx; // x and y indices into 2d matrix of result. 0,0 corresponds to motion SW. dx, dy may be negative, like (-1, -1) represents SW.
        // However, for histgram index, it's not possible to use negative number. That's the reason for intrducing xidx and yidx.
//        boolean minSearchedFlg = false;  // The flag indicates that this minimum have been already searched before.
        int scale;

        /**
         * Allocates new results initialized to zero
         */
        public SADResult() {
            this(0, 0, 0, 0);
        }

        public SADResult(int dx, int dy, float sadValue, int scale) {
            this.dx = dx;
            this.dy = dy;
            this.sadValue = sadValue;
        }

        public void set(SADResult s) {
            this.dx = s.dx;
            this.dy = s.dy;
            this.sadValue = s.sadValue;
            this.xidx = s.xidx;
            this.yidx = s.yidx;
            this.scale = s.scale;
        }

        @Override
        public String toString() {
            return String.format("(dx,dy=%5d,%5d), (vx,vy=%.1f,%.1f pps), SAD=%f, scale=%d", dx, dy, vx, vy, sadValue, scale);
        }

    }

    private class Statistics {

        double[] data;
        int size;

        public Statistics(double[] data) {
            this.data = data;
            size = data.length;
        }

        double getMean() {
            double sum = 0.0;
            for (double a : data) {
                sum += a;
            }
            return sum / size;
        }

        double getVariance() {
            double mean = getMean();
            double temp = 0;
            for (double a : data) {
                temp += (a - mean) * (a - mean);
            }
            return temp / size;
        }

        double getStdDev() {
            return Math.sqrt(getVariance());
        }

        public double median() {
            Arrays.sort(data);

            if ((data.length % 2) == 0) {
                return (data[(data.length / 2) - 1] + data[data.length / 2]) / 2.0;
            }
            return data[data.length / 2];
        }

        public double getMin() {
            Arrays.sort(data);

            return data[0];
        }

        public double getMax() {
            Arrays.sort(data);

            return data[data.length - 1];
        }
    }

    /**
     * @return the blockDimension
     */
    public int getBlockDimension() {
        return blockDimension;
    }

    /**
     * @param blockDimension the blockDimension to set
     */
    synchronized public void setBlockDimension(int blockDimension) {
        int old = this.blockDimension;
        // enforce odd value
        if ((blockDimension & 1) == 0) { // even
            if (blockDimension > old) {
                blockDimension++;
            } else {
                blockDimension--;
            }
        }
        // clip final value
        if (blockDimension < 1) {
            blockDimension = 1;
        } else if (blockDimension > 63) {
            blockDimension = 63;
        }
        this.blockDimension = blockDimension;
        getSupport().firePropertyChange("blockDimension", old, blockDimension);
        putInt("blockDimension", blockDimension);
        showBlockSizeAndSearchAreaTemporarily();
    }

    /**
     * @return the sliceMethod
     */
    public SliceMethod getSliceMethod() {
        return sliceMethod;
    }

    /**
     * @param sliceMethod the sliceMethod to set
     */
    synchronized public void setSliceMethod(SliceMethod sliceMethod) {
        SliceMethod old = this.sliceMethod;
        this.sliceMethod = sliceMethod;
        putString("sliceMethod", sliceMethod.toString());
        if (sliceMethod == SliceMethod.AreaEventNumber || sliceMethod == SliceMethod.ConstantIntegratedFlow) {
            showAreasForAreaCountsTemporarily();
        } else if (sliceMethod == SliceMethod.SpeedInput) {
            //SpeedInputFile is set
            setSpeedInputFile();

        }
//        if(sliceMethod==SliceMethod.ConstantIntegratedFlow){
//            setDisplayGlobalMotion(true);
//        }

        getSupport().firePropertyChange("sliceMethod", old, this.sliceMethod);
    }

    public PatchCompareMethod getPatchCompareMethod() {
        return patchCompareMethod;
    }

    synchronized public void setPatchCompareMethod(PatchCompareMethod patchCompareMethod) {
        this.patchCompareMethod = patchCompareMethod;
        putString("patchCompareMethod", patchCompareMethod.toString());
    }

    /**
     *
     * @return the search method
     */
    public SearchMethod getSearchMethod() {
        return searchMethod;
    }

    /**
     *
     * @param searchMethod the method to be used for searching
     */
    synchronized public void setSearchMethod(SearchMethod searchMethod) {
        SearchMethod old = this.searchMethod;
        this.searchMethod = searchMethod;
        putString("searchMethod", searchMethod.toString());
        getSupport().firePropertyChange("searchMethod", old, this.searchMethod);
    }

    private void computeAveragePossibleMatchDistance() {
        int n = 0;
        double s = 0;
        for (int xx = -searchDistance; xx <= searchDistance; xx++) {
            for (int yy = -searchDistance; yy <= searchDistance; yy++) {
                n++;
                s += Math.sqrt((xx * xx) + (yy * yy));
            }
        }
        double d = s / n; // avg for one scale
        double s2 = 0;
        for (int i = 0; i < numScales; i++) {
            s2 += d * (1 << i);
        }
        double d2 = s2 / numScales;
        log.info(String.format("searchDistance=%d numScales=%d: avgPossibleMatchDistance=%.1f", searchDistance, numScales, avgPossibleMatchDistance));
        avgPossibleMatchDistance = (float) d2;
    }

    @Override
    synchronized public void setSearchDistance(int searchDistance) {
        int old = this.searchDistance;

        if (searchDistance > 12) {
            searchDistance = 12;
        } else if (searchDistance < 1) {
            searchDistance = 1; // limit size
        }
        this.searchDistance = searchDistance;
        putInt("searchDistance", searchDistance);
        getSupport().firePropertyChange("searchDistance", old, searchDistance);
        resetFilter();
        showBlockSizeAndSearchAreaTemporarily();
        computeAveragePossibleMatchDistance();
    }

    /**
     * @return the sliceDurationUs
     */
    public int getSliceDurationUs() {
        return sliceDurationUs;
    }

    /**
     * @param sliceDurationUs the sliceDurationUs to set
     */
    public void setSliceDurationUs(int sliceDurationUs) {
        int old = this.sliceDurationUs;
        if (sliceDurationUs < MIN_SLICE_DURATION_US) {
            sliceDurationUs = MIN_SLICE_DURATION_US;
        } else if (sliceDurationUs > MAX_SLICE_DURATION_US) {
            sliceDurationUs = MAX_SLICE_DURATION_US; // limit it to one second
        }
        this.sliceDurationUs = sliceDurationUs;

        /* If the slice duration is changed, reset FSCnt and DScorrect so we can get more accurate evaluation result */
        FSCnt = 0;
        DSCorrectCnt = 0;
        putInt("sliceDurationUs", sliceDurationUs);
        getSupport().firePropertyChange("sliceDurationUs", old, this.sliceDurationUs);
    }

    /**
     * @return the sliceEventCount
     */
    public int getSliceEventCount() {
        return sliceEventCount;
    }

    /**
     * @param sliceEventCount the sliceEventCount to set
     */
    public void setSliceEventCount(int sliceEventCount) {
        int old = this.sliceEventCount;
        if (sliceEventCount < MIN_SLICE_EVENT_COUNT_FULL_FRAME) {
            sliceEventCount = MIN_SLICE_EVENT_COUNT_FULL_FRAME;
        } else if (sliceEventCount > MAX_SLICE_EVENT_COUNT_FULL_FRAME) {
            sliceEventCount = MAX_SLICE_EVENT_COUNT_FULL_FRAME;
        }
        this.sliceEventCount = sliceEventCount;
        putInt("sliceEventCount", sliceEventCount);
        getSupport().firePropertyChange("sliceEventCount", old, this.sliceEventCount);
    }

    public float getMaxAllowedSadDistance() {
        return maxAllowedSadDistance;
    }

    public void setMaxAllowedSadDistance(float maxAllowedSadDistance) {
        float old = this.maxAllowedSadDistance;
        if (maxAllowedSadDistance < 0) {
            maxAllowedSadDistance = 0;
        } else if (maxAllowedSadDistance > 1) {
            maxAllowedSadDistance = 1;
        }
        this.maxAllowedSadDistance = maxAllowedSadDistance;
        putFloat("maxAllowedSadDistance", maxAllowedSadDistance);
        getSupport().firePropertyChange("maxAllowedSadDistance", old, this.maxAllowedSadDistance);
    }

    public float getValidPixOccupancy() {
        return validPixOccupancy;
    }

    public void setValidPixOccupancy(float validPixOccupancy) {
        float old = this.validPixOccupancy;
        if (validPixOccupancy < 0) {
            validPixOccupancy = 0;
        } else if (validPixOccupancy > 1) {
            validPixOccupancy = 1;
        }
        this.validPixOccupancy = validPixOccupancy;
        putFloat("validPixOccupancy", validPixOccupancy);
        getSupport().firePropertyChange("validPixOccupancy", old, this.validPixOccupancy);
    }

    public float getWeightDistance() {
        return weightDistance;
    }

    public void setWeightDistance(float weightDistance) {
        if (weightDistance < 0) {
            weightDistance = 0;
        } else if (weightDistance > 1) {
            weightDistance = 1;
        }
        this.weightDistance = weightDistance;
        putFloat("weightDistance", weightDistance);
    }

//    private int totalFlowEvents=0, filteredOutFlowEvents=0;
//    private boolean filterOutInconsistentEvent(SADResult result) {
//        if (!isOutlierMotionFilteringEnabled()) {
//            return false;
//        }
//        totalFlowEvents++;
//        if (lastGoodSadResult == null) {
//            return false;
//        }
//        if (result.dx * lastGoodSadResult.dx + result.dy * lastGoodSadResult.dy >= 0) {
//            return false;
//        }
//        filteredOutFlowEvents++;
//        return true;
//    }
    synchronized private void checkArrays() {

        if (subSizeX == 0 || subSizeY == 0) {
            return; // don't do on init when chip is not known yet
        }
//        numSlices = getInt("numSlices", 3); // since resetFilter is called in super before numSlices is even initialized
        if (slices == null || slices.length != numSlices
                || slices[0] == null || slices[0].length != numScales) {
            if (numScales > 0 && numSlices > 0) { // deal with filter reconstruction where these fields are not set
                slices = new byte[numSlices][numScales][][];
                for (int n = 0; n < numSlices; n++) {
                    for (int s = 0; s < numScales; s++) {
                        int nx = (subSizeX >> s) + 1, ny = (subSizeY >> s) + 1;
                        if (slices[n][s] == null || slices[n][s].length != nx
                                || slices[n][s][0] == null || slices[n][s][0].length != ny) {
                            slices[n][s] = new byte[nx][ny];
                        }
                    }
                }
                currentSliceIdx = 0;  // start by filling slice 0
                currentSlice = slices[currentSliceIdx];

                sliceLastTs = Integer.MAX_VALUE;
                sliceStartTimeUs = new int[numSlices];
                sliceEndTimeUs = new int[numSlices];
                sliceSummedSADValues = new float[numSlices];
                sliceSummedSADCounts = new int[numSlices];
            }
//            log.info("allocated slice memory");
        }
        if (lastTimesMap != null) {
            lastTimesMap = null; // save memory
        }
        int rhDim = (2 * (searchDistance << (numScales - 1))) + 1; // e.g. search distance 1, dim=3, 3x3 possibilties (including zero motion)
        if ((resultHistogram == null) || (resultHistogram.length != rhDim)) {
            resultHistogram = new int[rhDim][rhDim];
            resultHistogramCount = 0;
        }
        checkNonGreedyRegionsAllocated();
    }

    /**
     *
     * @param distResult
     * @return the confidence of the result. True means it's not good and should
     * be rejected, false means we should accept it.
     */
    private synchronized boolean isNotSufficientlyAccurate(SADResult distResult) {
        boolean retVal = super.accuracyTests();  // check accuracy in super, if reject returns true

        // additional test, normalized blaock distance must be small enough
        // distance has max value 1
        if (distResult.sadValue >= maxAllowedSadDistance) {
            retVal = true;
        }

        return retVal;
    }

    /**
     * @return the skipProcessingEventsCount
     */
    public int getSkipProcessingEventsCount() {
        return skipProcessingEventsCount;
    }

    /**
     * @param skipProcessingEventsCount the skipProcessingEventsCount to set
     */
    public void setSkipProcessingEventsCount(int skipProcessingEventsCount) {
        int old = this.skipProcessingEventsCount;
        if (skipProcessingEventsCount < 0) {
            skipProcessingEventsCount = 0;
        }
        if (skipProcessingEventsCount > MAX_SKIP_COUNT) {
            skipProcessingEventsCount = MAX_SKIP_COUNT;
        }
        this.skipProcessingEventsCount = skipProcessingEventsCount;
        getSupport().firePropertyChange("skipProcessingEventsCount", old, this.skipProcessingEventsCount);
        putInt("skipProcessingEventsCount", skipProcessingEventsCount);
    }

    /**
     * @return the displayResultHistogram
     */
    public boolean isDisplayResultHistogram() {
        return displayResultHistogram;
    }

    /**
     * @param displayResultHistogram the displayResultHistogram to set
     */
    public void setDisplayResultHistogram(boolean displayResultHistogram) {
        this.displayResultHistogram = displayResultHistogram;
        putBoolean("displayResultHistogram", displayResultHistogram);
    }

    /**
     * @return the adaptiveEventSkipping
     */
    public boolean isAdaptiveEventSkipping() {
        return adaptiveEventSkipping;
    }

    /**
     * @param adaptiveEventSkipping the adaptiveEventSkipping to set
     */
    synchronized public void setAdaptiveEventSkipping(boolean adaptiveEventSkipping) {
        boolean old = this.adaptiveEventSkipping;
        this.adaptiveEventSkipping = adaptiveEventSkipping;
        putBoolean("adaptiveEventSkipping", adaptiveEventSkipping);
        if (adaptiveEventSkipping && adaptiveEventSkippingUpdateCounterLPFilter != null) {
            adaptiveEventSkippingUpdateCounterLPFilter.reset();
        }
        getSupport().firePropertyChange("adaptiveEventSkipping", old, this.adaptiveEventSkipping);
    }

    public boolean isOutputSearchErrorInfo() {
        return outputSearchErrorInfo;
    }

    public boolean isShowSliceBitMap() {
        return showSliceBitMap;
    }

    /**
     * @param showSliceBitMap the option of displaying bitmap
     */
    synchronized public void setShowSliceBitMap(boolean showSliceBitMap) {
        boolean old = this.showSliceBitMap;
        this.showSliceBitMap = showSliceBitMap;
        getSupport().firePropertyChange("showSliceBitMap", old, this.showSliceBitMap);
    }

    synchronized public void setOutputSearchErrorInfo(boolean outputSearchErrorInfo) {
        this.outputSearchErrorInfo = outputSearchErrorInfo;
        if (!outputSearchErrorInfo) {
            searchMethod = SearchMethod.valueOf(getString("searchMethod", SearchMethod.FullSearch.toString()));  // make sure method is reset
        }
    }

    private LowpassFilter adaptiveEventSkippingUpdateCounterLPFilter = null;
    private int adaptiveEventSkippingUpdateCounter = 0;

    private void adaptEventSkipping() {
        if (!adaptiveEventSkipping) {
            return;
        }
        if (chip.getAeViewer() == null) {
            return;
        }
        int old = skipProcessingEventsCount;
        if (chip.getAeViewer().isPaused() || chip.getAeViewer().isSingleStep()) {
            skipProcessingEventsCount = 0;
            getSupport().firePropertyChange("skipProcessingEventsCount", old, this.skipProcessingEventsCount);
        }
        if (adaptiveEventSkippingUpdateCounterLPFilter == null) {
            adaptiveEventSkippingUpdateCounterLPFilter = new LowpassFilter(chip.getAeViewer().getFrameRater().FPS_LOWPASS_FILTER_TIMECONSTANT_MS);
        }
        final float averageFPS = chip.getAeViewer().getFrameRater().getAverageFPS();
        final int frameRate = chip.getAeViewer().getDesiredFrameRate();
        boolean skipMore = averageFPS < (int) (0.75f * frameRate);
        boolean skipLess = averageFPS > (int) (0.25f * frameRate);
        float newSkipCount = skipProcessingEventsCount;
        if (skipMore) {
            newSkipCount = adaptiveEventSkippingUpdateCounterLPFilter.filter(1 + (skipChangeFactor * skipProcessingEventsCount), 1000 * (int) System.currentTimeMillis());
        } else if (skipLess) {
            newSkipCount = adaptiveEventSkippingUpdateCounterLPFilter.filter((skipProcessingEventsCount / skipChangeFactor) - 1, 1000 * (int) System.currentTimeMillis());
        }
        skipProcessingEventsCount = Math.round(newSkipCount);
        if (skipProcessingEventsCount > MAX_SKIP_COUNT) {
            skipProcessingEventsCount = MAX_SKIP_COUNT;
        } else if (skipProcessingEventsCount < 0) {
            skipProcessingEventsCount = 0;
        }
        getSupport().firePropertyChange("skipProcessingEventsCount", old, this.skipProcessingEventsCount);

    }

    /**
     * @return the adaptiveSliceDuration
     */
    public boolean isAdaptiveSliceDuration() {
        return adaptiveSliceDuration;
    }

    /**
     * @param adaptiveSliceDuration the adaptiveSliceDuration to set
     */
    synchronized public void setAdaptiveSliceDuration(boolean adaptiveSliceDuration) {
        boolean old = this.adaptiveSliceDuration;
        this.adaptiveSliceDuration = adaptiveSliceDuration;
        putBoolean("adaptiveSliceDuration", adaptiveSliceDuration);
        if (adaptiveSliceDurationLogging) {
            if (adaptiveSliceDurationLogger == null) {
                adaptiveSliceDurationLogger = new TobiLogger("PatchMatchFlow-SliceDurationControl", "slice duration or event count control logging");
                adaptiveSliceDurationLogger.setHeaderLine("systemTimeMs\tpacketNumber\tavgMatchDistance\tmatchRadiusError\tglobalTranslationSpeedPPS\tsliceDurationUs\tsliceEventCount");
            }
            adaptiveSliceDurationLogger.setEnabled(adaptiveSliceDuration);
        }
        getSupport().firePropertyChange("adaptiveSliceDuration", old, this.adaptiveSliceDuration);
    }

    /**
     * @return the processingTimeLimitMs
     */
    public int getProcessingTimeLimitMs() {
        return processingTimeLimitMs;
    }

    /**
     * @param processingTimeLimitMs the processingTimeLimitMs to set
     */
    public void setProcessingTimeLimitMs(int processingTimeLimitMs) {
        this.processingTimeLimitMs = processingTimeLimitMs;
        putInt("processingTimeLimitMs", processingTimeLimitMs);
    }

    /**
     * clears all scales for a particular time slice
     *
     * @param slice [scale][x][y]
     */
    private void clearSlice(byte[][][] slice) {
        for (byte[][] scale : slice) { // for each scale
            for (byte[] row : scale) { // for each col
                Arrays.fill(row, (byte) 0); // fill col
            }
        }
    }

    private int dim = blockDimension + (2 * searchDistance);

    protected static final String G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH = "G: search area\nR: ref block area\nB: best match";

    /**
     * Draws the block matching bitmap
     *
     * @param x
     * @param y
     * @param dx
     * @param dy
     * @param refBlock
     * @param searchBlock
     * @param subSampleBy
     */
    synchronized private void drawMatching(SADResult result, PolarityEvent ein, byte[][][][] slices) {
//    synchronized private void drawMatching(int x, int y, int dx, int dy, byte[][] refBlock, byte[][] searchBlock, int subSampleBy) {
        int x = ein.x >> result.scale, y = ein.y >> result.scale;
        int dx = (int) result.dx >> result.scale, dy = (int) result.dy >> result.scale;
        byte[][] refBlock = slices[sliceIndex(1)][result.scale], searchBlock = slices[sliceIndex(2)][result.scale];
        int subSampleBy = result.scale;
        Legend sadLegend = null;

        int dimNew = blockDimension + (2 * (searchDistance));
        if (sliceBitMapFrame == null) {
            String windowName = "Slice bitmaps";
            sliceBitMapFrame = new JFrame(windowName);
            sliceBitMapFrame.setLayout(new BoxLayout(sliceBitMapFrame.getContentPane(), BoxLayout.Y_AXIS));
            sliceBitMapFrame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            sliceBitmapImageDisplay = ImageDisplay.createOpenGLCanvas();
            sliceBitmapImageDisplay.setBorderSpacePixels(10);
            sliceBitmapImageDisplay.setImageSize(dimNew, dimNew);
            sliceBitmapImageDisplay.setSize(200, 200);
            sliceBitmapImageDisplay.setGrayValue(0);
            sliceBitmapLegend = sliceBitmapImageDisplay.addLegend(G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
            panel.add(sliceBitmapImageDisplay);

            sliceBitMapFrame.getContentPane().add(panel);
            sliceBitMapFrame.pack();
            sliceBitMapFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    setShowSliceBitMap(false);
                }
            });
        }
        if (!sliceBitMapFrame.isVisible()) {
            sliceBitMapFrame.setVisible(true);
        }
        final int radius = (blockDimension / 2) + searchDistance;

        float scale = 1f / getSliceMaxValue();
        try {
            if ((x >= radius) && ((x + radius) < subSizeX)
                    && (y >= radius) && ((y + radius) < subSizeY)) {

                if (dimNew != sliceBitmapImageDisplay.getWidth()) {
                    dim = dimNew;
                    sliceBitmapImageDisplay.setImageSize(dimNew, dimNew);
                    sliceBitmapImageDisplay.clearLegends();
                    sliceBitmapLegend = sliceBitmapImageDisplay.addLegend(G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
                }

//        TextRenderer textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 12));

                /* Reset the image first */
                sliceBitmapImageDisplay.clearImage();
                /* Rendering the reference patch in t-imuWarningDialog slice, it's on the center with color red */
                for (int i = searchDistance; i < (blockDimension + searchDistance); i++) {
                    for (int j = searchDistance; j < (blockDimension + searchDistance); j++) {
                        float[] f = sliceBitmapImageDisplay.getPixmapRGB(i, j);
                        f[0] = scale * Math.abs(refBlock[((x - (blockDimension / 2)) + i) - searchDistance][((y - (blockDimension / 2)) + j) - searchDistance]);
                        sliceBitmapImageDisplay.setPixmapRGB(i, j, f);
                    }
                }

                /* Rendering the area within search distance in t-2d slice, it's full of the whole search area with color green */
                for (int i = 0; i < ((2 * radius) + 1); i++) {
                    for (int j = 0; j < ((2 * radius) + 1); j++) {
                        float[] f = sliceBitmapImageDisplay.getPixmapRGB(i, j);
                        f[1] = scale * Math.abs(searchBlock[(x - radius) + i][(y - radius) + j]);
                        sliceBitmapImageDisplay.setPixmapRGB(i, j, f);
                    }
                }

                /* Rendering the best matching patch in t-2d slice, it's on the shifted position related to the center location with color blue */
                for (int i = searchDistance + dx; i < (blockDimension + searchDistance + dx); i++) {
                    for (int j = searchDistance + dy; j < (blockDimension + searchDistance + dy); j++) {
                        float[] f = sliceBitmapImageDisplay.getPixmapRGB(i, j);
                        f[2] = scale * Math.abs(searchBlock[((x - (blockDimension / 2)) + i) - searchDistance][((y - (blockDimension / 2)) + j) - searchDistance]);
                        sliceBitmapImageDisplay.setPixmapRGB(i, j, f);
                    }
                }
                if (sliceBitmapLegend != null) {
                    sliceBitmapLegend.s
                            = G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH
                            + "\nScale: "
                            + subSampleBy
                            + "\nSAD: "
                            + engFmt.format(result.sadValue);
                }
            }
        } catch (ArrayIndexOutOfBoundsException a) {

        }

        sliceBitmapImageDisplay.repaint();
    }

//    /**
//     * @return the numSlices
//     */
//    public int getNumSlices() {
//        return numSlices;
//    }
//
//    /**
//     * @param numSlices the numSlices to set
//     */
//    synchronized public void setNumSlices(int numSlices) {
//        if (numSlices < 3) {
//            numSlices = 3;
//        } else if (numSlices > 8) {
//            numSlices = 8;
//        }
//        this.numSlices = numSlices;
//        putInt("numSlices", numSlices);
//    }
    /**
     * @return the sliceNumBits
     */
    public int getSliceMaxValue() {
        return sliceMaxValue;
    }

    /**
     * @param sliceMaxValue the sliceMaxValue to set
     */
    public void setSliceMaxValue(int sliceMaxValue) {
        int old = this.sliceMaxValue;
        if (sliceMaxValue < 1) {
            sliceMaxValue = 1;
        } else if (sliceMaxValue > 127) {
            sliceMaxValue = 127;
        }
        this.sliceMaxValue = sliceMaxValue;
        putInt("sliceMaxValue", sliceMaxValue);
        getSupport().firePropertyChange("sliceMaxValue", old, this.sliceMaxValue);
    }

    /**
     * @return the rectifyPolarties
     */
    public boolean isRectifyPolarties() {
        return rectifyPolarties;
    }

    /**
     * @param rectifyPolarties the rectifyPolarties to set
     */
    public void setRectifyPolarties(boolean rectifyPolarties) {
        boolean old = this.rectifyPolarties;
        this.rectifyPolarties = rectifyPolarties;
        putBoolean("rectifyPolarties", rectifyPolarties);
        getSupport().firePropertyChange("rectifyPolarties", old, this.rectifyPolarties);
    }

    /**
     * @return the useSubsampling
     */
    public boolean isUseSubsampling() {
        return useSubsampling;
    }

    /**
     * @param useSubsampling the useSubsampling to set
     */
    public void setUseSubsampling(boolean useSubsampling) {
        this.useSubsampling = useSubsampling;
    }

    /**
     * @return the numScales
     */
    public int getNumScales() {
        return numScales;
    }

    /**
     * @param numScales the numScales to set
     */
    synchronized public void setNumScales(int numScales) {
        int old = this.numScales;
        if (numScales < 1) {
            numScales = 1;
        } else if (numScales > 4) {
            numScales = 4;
        }
        this.numScales = numScales;
        putInt("numScales", numScales);
        setDefaultScalesToCompute();
        scaleResultCounts = new int[numScales];
        showBlockSizeAndSearchAreaTemporarily();
        computeAveragePossibleMatchDistance();
        getSupport().firePropertyChange("numScales", old, this.numScales);
    }

    /**
     * Computes pooled (summed) value of slice at location xx, yy, in subsampled
     * region around this point
     *
     * @param slice
     * @param x
     * @param y
     * @param subsampleBy pool over 1<<subsampleBy by 1<<subsampleBy area to sum
     * up the slice values @return
     */
    private int pool(byte[][] slice, int x, int y, int subsampleBy) {
        if (subsampleBy == 0) {
            return slice[x][y];
        } else {
            int n = 1 << subsampleBy;
            int sum = 0;
            for (int xx = x; xx < x + n + n; xx++) {
                for (int yy = y; yy < y + n + n; yy++) {
                    if (xx >= subSizeX || yy >= subSizeY) {
//                        log.warning("should not happen that xx="+xx+" or yy="+yy);
                        continue; // TODO remove this check when iteration avoids this sum explictly
                    }
                    sum += slice[xx][yy];
                }
            }
            return sum;
        }
    }

    /**
     * @return the scalesToCompute
     */
    public String getScalesToCompute() {
        return scalesToCompute;
    }

    /**
     * @param scalesToCompute the scalesToCompute to set
     */
    synchronized public void setScalesToCompute(String scalesToCompute) {
        this.scalesToCompute = scalesToCompute;
        if (scalesToCompute == null || scalesToCompute.isEmpty()) {

            setDefaultScalesToCompute();
        } else {
            StringTokenizer st = new StringTokenizer(scalesToCompute, ", ", false);
            int n = st.countTokens();
            if (n == 0) {
                setDefaultScalesToCompute();
            } else {
                scalesToComputeArray = new int[n];
                int i = 0;
                while (st.hasMoreTokens()) {
                    try {
                        int scale = Integer.parseInt(st.nextToken());
                        scalesToComputeArray[i++] = scale;
                    } catch (NumberFormatException nfE) {
                        log.warning("bad string in scalesToCompute field, use blank or 0,2 for example");
                        setDefaultScalesToCompute();
                    }
                }
            }
        }
    }

    private void setDefaultScalesToCompute() {
        scalesToComputeArray = new int[numScales];
        for (int i = 0; i < numScales; i++) {
            scalesToComputeArray[i] = i;
        }
    }

    /**
     * @return the areaEventNumberSubsampling
     */
    public int getAreaEventNumberSubsampling() {
        return areaEventNumberSubsampling;
    }

    /**
     * @param areaEventNumberSubsampling the areaEventNumberSubsampling to set
     */
    synchronized public void setAreaEventNumberSubsampling(int areaEventNumberSubsampling) {
        int old = this.areaEventNumberSubsampling;
        if (areaEventNumberSubsampling < 3) {
            areaEventNumberSubsampling = 3;
        } else if (areaEventNumberSubsampling > 7) {
            areaEventNumberSubsampling = 7;
        }
        this.areaEventNumberSubsampling = areaEventNumberSubsampling;
        putInt("areaEventNumberSubsampling", areaEventNumberSubsampling);
        showAreasForAreaCountsTemporarily();
        clearAreaCounts();
        if (sliceMethod != SliceMethod.AreaEventNumber) {
            log.warning("AreaEventNumber method is not currently selected as sliceMethod");
        }
        getSupport().firePropertyChange("areaEventNumberSubsampling", old, this.areaEventNumberSubsampling);
    }

    private void showAreasForAreaCountsTemporarily() {
        if (stopShowingStuffTask != null) {
            stopShowingStuffTask.cancel();
        }
        stopShowingStuffTask = new TimerTask() {
            @Override
            public void run() {
                showBlockSizeAndSearchAreaTemporarily = false; // in case we are canceling a task that would clear this
                showAreaCountAreasTemporarily = false;
            }
        };
        Timer showAreaCountsAreasTimer = new Timer();
        showAreaCountAreasTemporarily = true;
        showAreaCountsAreasTimer.schedule(stopShowingStuffTask, SHOW_STUFF_DURATION_MS);
    }

    private void showBlockSizeAndSearchAreaTemporarily() {
        if (stopShowingStuffTask != null) {
            stopShowingStuffTask.cancel();
        }
        stopShowingStuffTask = new TimerTask() {
            @Override
            public void run() {
                showAreaCountAreasTemporarily = false; // in case we are canceling a task that would clear this
                showBlockSizeAndSearchAreaTemporarily = false;
            }
        };
        Timer showBlockSizeAndSearchAreaTimer = new Timer();
        showBlockSizeAndSearchAreaTemporarily = true;
        showBlockSizeAndSearchAreaTimer.schedule(stopShowingStuffTask, SHOW_STUFF_DURATION_MS);
    }

    private void clearAreaCounts() {
        if (sliceMethod != SliceMethod.AreaEventNumber) {
            return;
        }
        if (areaCounts == null || areaCounts.length != 1 + (subSizeX >> areaEventNumberSubsampling)) {
            areaCounts = new int[1 + (subSizeX >> areaEventNumberSubsampling)][1 + (subSizeY >> areaEventNumberSubsampling)];
        } else {
            for (int[] i : areaCounts) {
                Arrays.fill(i, 0);
            }
        }
        areaCountExceeded = false;
    }

    private void clearNonGreedyRegions() {
        if (!nonGreedyFlowComputingEnabled) {
            return;
        }
        checkNonGreedyRegionsAllocated();
        nonGreedyRegionsCount = 0;
        for (boolean[] i : nonGreedyRegions) {
            Arrays.fill(i, false);
        }
    }

    private void checkNonGreedyRegionsAllocated() {
        if (nonGreedyRegions == null || nonGreedyRegions.length != 1 + (subSizeX >> areaEventNumberSubsampling)) {
            nonGreedyRegionsNumberOfRegions = (1 + (subSizeX >> areaEventNumberSubsampling)) * (1 + (subSizeY >> areaEventNumberSubsampling));
            nonGreedyRegions = new boolean[1 + (subSizeX >> areaEventNumberSubsampling)][1 + (subSizeY >> areaEventNumberSubsampling)];
            nonGreedyRegionsNumberOfRegions = (1 + (subSizeX >> areaEventNumberSubsampling)) * (1 + (subSizeY >> areaEventNumberSubsampling));
        }
    }

    public int getSliceDeltaT() {
        return sliceDeltaT;
    }

    /**
     * @return the enableImuTimesliceLogging
     */
    public boolean isEnableImuTimesliceLogging() {
        return enableImuTimesliceLogging;
    }

    /**
     * @param enableImuTimesliceLogging the enableImuTimesliceLogging to set
     */
    public void setEnableImuTimesliceLogging(boolean enableImuTimesliceLogging) {
        this.enableImuTimesliceLogging = enableImuTimesliceLogging;
        if (enableImuTimesliceLogging) {
            if (imuTimesliceLogger == null) {
                imuTimesliceLogger = new TobiLogger("imuTimeslice.txt", "IMU rate gyro deg/s and patchmatch timeslice duration in ms");
                imuTimesliceLogger.setHeaderLine("systemtime(ms) timestamp(us) timeslice(us) rate(deg/s)");
            }
        }
        imuTimesliceLogger.setEnabled(enableImuTimesliceLogging);
    }

    /**
     * @return the nonGreedyFlowComputingEnabled
     */
    public boolean isNonGreedyFlowComputingEnabled() {
        return nonGreedyFlowComputingEnabled;
    }

    /**
     * @param nonGreedyFlowComputingEnabled the nonGreedyFlowComputingEnabled to
     * set
     */
    synchronized public void setNonGreedyFlowComputingEnabled(boolean nonGreedyFlowComputingEnabled) {
        boolean old = this.nonGreedyFlowComputingEnabled;
        this.nonGreedyFlowComputingEnabled = nonGreedyFlowComputingEnabled;
        putBoolean("nonGreedyFlowComputingEnabled", nonGreedyFlowComputingEnabled);
        if (nonGreedyFlowComputingEnabled) {
            clearNonGreedyRegions();
        }
        getSupport().firePropertyChange("nonGreedyFlowComputingEnabled", old, nonGreedyFlowComputingEnabled);
    }

    /**
     * @return the nonGreedyFractionToBeServiced
     */
    public float getNonGreedyFractionToBeServiced() {
        return nonGreedyFractionToBeServiced;
    }

    /**
     * @param nonGreedyFractionToBeServiced the nonGreedyFractionToBeServiced to
     * set
     */
    public void setNonGreedyFractionToBeServiced(float nonGreedyFractionToBeServiced) {
        this.nonGreedyFractionToBeServiced = nonGreedyFractionToBeServiced;
        putFloat("nonGreedyFractionToBeServiced", nonGreedyFractionToBeServiced);
    }

    /**
     * @return the SpeedDividend
     */
    public int getSpeedDividend() {
        return speedDividend;
    }

    public void setSpeedDividend(int speedDividend) {
        int old = this.speedDividend;
        this.speedDividend = speedDividend;
        if (speedDividend < MIN_SPEED_DIVIDEND) {
            speedDividend = MIN_SPEED_DIVIDEND;
        } else if (speedDividend > MAX_SPEED_DIVIDEND) {
            speedDividend = MAX_SPEED_DIVIDEND; // limit it to one second
        }
        this.speedDividend = speedDividend;

        putInt("SpeedDividend", speedDividend);
        getSupport().firePropertyChange("SpeedDividend", old, this.speedDividend);
    }

    public void setSpeedInputFile() {
        log.info("Setting Speed Input File");
        String filename = null, filepath = null;
        final JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(new File(getString("lastFile", System.getProperty("user.dir"))));  // defaults to startup runtime folder
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setSelectedFile(new File(getString("lastFile", System.getProperty("user.dir"))));
        fc.setDialogTitle("Select file to read recorded speed from");
        int ret = fc.showOpenDialog(chip.getAeViewer() != null && chip.getAeViewer().getFilterFrame() != null ? chip.getAeViewer().getFilterFrame() : null);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            putString("lastFile", file.toString());
            try {
                speedReader = new BufferedReader(new FileReader(file.toString()));
                speedReader.readLine(); //skip header
                int speedFileSize = getSpeedFileSize(file);

                timeStamps = new int[speedFileSize];
                speeds = new float[speedFileSize];
                String[] nextSpeedLine;

                for (int i = 0; i < speedFileSize - 1; i++) {
                    nextSpeedLine = speedReader.readLine().split("\t");
                    timeStamps[i] = Integer.parseInt(nextSpeedLine[0]);
                    speeds[i] = Float.parseFloat(nextSpeedLine[1]);
                }

                speedReader.close();

            } catch (FileNotFoundException ex) {
                Logger.getLogger(PatchMatchFlow.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(PatchMatchFlow.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            log.info("Cancelled, no Input file selected");
        }

    }

    public int getSpeedFileSize(File file) {
        int size = 0;

        try {
            BufferedReader tempReader = new BufferedReader(new FileReader(file.toString()));
            String nextLine = tempReader.readLine(); //skip header

            for (size = 0; nextLine != null; size++) {
                nextLine = tempReader.readLine();
            }

            tempReader.close();

        } catch (FileNotFoundException ex) {
            Logger.getLogger(PatchMatchFlow.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(PatchMatchFlow.class.getName()).log(Level.SEVERE, null, ex);
        }

        return size;
    }

   
    public int getSpeedSliceDuration() {
        if (speeds == null) {
            setSpeedInputFile();
            calculatedFileSliceDuration = this.sliceDurationUs;
        }
        
        if (ts > timeStamps[timestampPointer] || ts < oldReadTimestamp) {
            timestampPointer = binarySearch(timeStamps, ts);
            readFileSpeed = speeds[timestampPointer];
            
            if(timestampPointer!=0){
                oldReadTimestamp = timeStamps[timestampPointer-1];
            } else {
                oldReadTimestamp = timeStamps[0]-this.sliceDurationUs;
            }

            if (readFileSpeed != 0) {
                calculatedFileSliceDuration = (int) (speedDividend / readFileSpeed);
            } else {
                calculatedFileSliceDuration = this.sliceDurationUs;
            }
        }
        return calculatedFileSliceDuration;
    }

    /**
     * Does a binary search on a sorted array.
     * Array must be sorted!
     * @param a: sorted array to be searched in
     * @param key: key to be searched for in {@param a}
     * @return the index of {@param key} if it exists in the array,
     *         otherwise the index of the next bigger element in the array
     *         or the last index of the array if all elements are smaller than
     *         the key
     */
    private int binarySearch(int[] a, int key) {
        int low = 0;
        int high = a.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = a[mid];

            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid; // key found
            }
        }
        return Math.min(low, a.length-1);  // key not found.
    }
}
