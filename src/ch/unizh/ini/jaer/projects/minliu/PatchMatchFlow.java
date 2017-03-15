/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.minliu;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.util.awt.TextRenderer;

import ch.unizh.ini.jaer.projects.rbodo.opticalflow.AbstractMotionFlow;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.logging.Level;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.TimeLimiter;
import net.sf.jaer.eventprocessing.filter.Steadicam;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.TextRendererScale;

/**
 * Uses patch matching to measureTT local optical flow. <b>Not</b> gradient
 * based, but rather matches local features backwards in time.
 *
 * @author Tobi and Min, Jan 2016
 */
@Description("Computes optical flow with vector direction using binary block matching")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class PatchMatchFlow extends AbstractMotionFlow implements Observer, FrameAnnotater {

    /* LDSP is Large Diamond Search Pattern, and SDSP mens Small Diamond Search Pattern. 
       LDSP has 9 points and SDSP consists of 5 points.
     */
    private static final int LDSP[][] = {{0, -2}, {-1, -1}, {1, -1}, {-2, 0}, {0, 0},
    {2, 0}, {-1, 1}, {1, 1}, {0, 2}};
    private static final int SDSP[][] = {{0, -1}, {-1, 0}, {0, 0}, {1, 0}, {0, 1}};

    private int[][][] histograms = null;
    private static final int NUM_SLICES = 3;
//    private int sx, sy;
    private int tMinus2SliceIdx = 0, tMinus1SliceIdx = 1, currentSliceIdx = 2;
    private int[][] currentSlice = null, tMinus1Slice = null, tMinus2Slice = null;
//    private ArrayList<Integer[]>[][] spikeTrains = null;   // Spike trains for one block
//    private ArrayList<int[][]>[] histogramsAL = null;
//    private ArrayList<int[][]> currentAL = null, previousAL = null, previousMinus1AL = null; // One is for current, the second is for previous, the third is for the one before previous one
//    private BitSet[] histogramsBitSet = null;
//    private BitSet currentSli = null, tMinus1Sli = null, tMinus2Sli = null;
    private boolean[][][] bitmaps = null;
    private boolean[][] currentBitmap, tm1Bitmap, tm2Bitmap;
    private final SADResult tmpSadResult = new SADResult(0, 0, 0); // used to pass data back from min distance computation
    private int patchDimension = getInt("patchDimension", 9);
//    private int eventPatchDimension = getInt("eventPatchDimension", 3);
//    private int forwardEventNum = getInt("forwardEventNum", 10);
    private float cost = getFloat("cost", 0.001f);
    private float confidenceThreshold = getFloat("confidenceThreshold", .3f);
    private float validPixOccupancy = getFloat("validPixOccupancy", 0.01f);  // threshold for valid pixel percent for one block
    private float weightDistance = getFloat("weightDistance", 0.95f);        // confidence value consists of the distance and the dispersion, this value set the distance value
//    private int thresholdTime = getInt("thresholdTime", 1000000);
//    private int[][] lastFireIndex = null;  // Events are numbered in time order for every block. This variable is for storing the last event index fired on all blocks.
//    private int[][] eventSeqStartTs = null;
//    private boolean preProcessEnable = false;
    private int skipProcessingEventsCount = getInt("skipProcessingEventsCount", 0); // skip this many events for processing (but not for accumulating to bitmaps)
    private int skipCounter = 0;
    private boolean adaptiveEventSkipping = getBoolean("adaptiveEventSkipping", false);
    private float skipChangeFactor = 1.5f; // by what factor to change the skip count if too slow or too fast
    private boolean outputSearchErrorInfo = false; // make user choose this slow down every time
    private boolean adapativeSliceDuration = getBoolean("adapativeSliceDuration", false);
    private boolean showSliceBitMap = getBoolean("showSliceBitMap", false); // Display the bitmaps
    private float adapativeSliceDurationProportionalErrorGain = 0.01f; // factor by which an error signal on match distance changes slice duration
    private int processingTimeLimitMs = getInt("processingTimeLimitMs", 1000); // time limit for processing packet in ms to process OF events (events still accumulate). Overrides the system EventPacket timelimiter, which cannot be used here because we still need to accumulate and render the events.
    private TimeLimiter timeLimiter = new TimeLimiter();

    // results histogram for each packet
    private int[][] resultHistogram = null;
    private int resultHistogramCount;
    private float lastAvgMatchDistance, avgMatchDistance = 0; // stores average match distance for rendering it
    private float FSCnt = 0, DSCorrectCnt = 0;
    float DSAverageNum = 0, DSAveError[] = {0, 0};           // Evaluate DS cost average number and the error.

    public enum PatchCompareMethod {
        JaccardDistance, HammingDistance/*, SAD, EventSqeDistance*/
    };
    private PatchCompareMethod patchCompareMethod = PatchCompareMethod.valueOf(getString("patchCompareMethod", PatchCompareMethod.HammingDistance.toString()));

    public enum SearchMethod {
        FullSearch, DiamondSearch, CrossDiamondSearch
    };
    private SearchMethod searchMethod = SearchMethod.valueOf(getString("searchMethod", SearchMethod.DiamondSearch.toString()));

    private int sliceDurationUs = getInt("sliceDurationUs", 40000);
    private int sliceEventCount = getInt("sliceEventCount", 1000);
    private boolean rewindFlg = false; // The flag to indicate the rewind event.
    private FilterChain filterChain;
    private Steadicam cameraMotion;

    // calibration
    private boolean calibrating = false; // used to flag calibration state
    private int calibrationSampleCount = 0;
    private int NUM_CALIBRATION_SAMPLES_DEFAULT = 800; // 400 samples /sec
    protected int numCalibrationSamples = getInt("numCalibrationSamples", NUM_CALIBRATION_SAMPLES_DEFAULT);
    TextRenderer imuTextRenderer = null;
    private boolean showGrid = getBoolean("showGrid", true);
    private boolean displayResultHistogram = getBoolean("displayResultHistogram", true);

    public enum SliceMethod {
        ConstantDuration, ConstantEventNumber, AdaptationDuration
    };
    private SliceMethod sliceMethod = SliceMethod.valueOf(getString("sliceMethod", SliceMethod.ConstantDuration.toString()));
    private int eventCounter = 0;
    private int sliceLastTs = 0;

    ImageDisplay display; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private JFrame Frame = null;

    public PatchMatchFlow(AEChip chip) {
        super(chip);

        filterChain = new FilterChain(chip);
        cameraMotion = new Steadicam(chip);
        cameraMotion.setFilterEnabled(true);
        cameraMotion.setDisableRotation(true);
        cameraMotion.setDisableTranslation(true);
        // filterChain.add(cameraMotion);
        setEnclosedFilterChain(filterChain);

        String patchTT = "Block matching";
        String eventSqeMatching = "Event squence matching";
        String preProcess = "Denoise";
        String metricConfid = "Confidence of current metric";

        chip.addObserver(this); // to allocate memory once chip size is known
//        setPropertyTooltip(preProcess, "preProcessEnable", "enable this to denoise before data processing");
//        setPropertyTooltip(preProcess, "forwardEventNum", "Number of events have fired on the current block since last processing");
        setPropertyTooltip(metricConfid, "confidenceThreshold", "<html>Confidence threshold for rejecting unresonable value; Range from 0 to 1. <p>Higher value means it is harder to accept the event. <br>Set to 0 to accept all results.");
        setPropertyTooltip(metricConfid, "validPixOccupancy", "<html>Threshold for valid pixel percent for each block; Range from 0 to 1. <p>If either matching block is less occupied than this fraction, no motion vector will be calculated.");
        setPropertyTooltip(metricConfid, "weightDistance", "<html>The confidence value consists of the distance and the dispersion; <br>weightDistance sets the weighting of the distance value compared with the dispersion value; Range from 0 to 1. <p>To count only e.g. hamming distance, set weighting to 1. <p> To count only dispersion, set to 0.");
        setPropertyTooltip(patchTT, "patchDimension", "linear dimenion of patches to match, in pixels");
        setPropertyTooltip(patchTT, "searchDistance", "search distance for matching patches, in pixels");
        setPropertyTooltip(patchTT, "patchCompareMethod", "method to compare two patches");
        setPropertyTooltip(patchTT, "searchMethod", "method to search patches");
        setPropertyTooltip(patchTT, "sliceDurationUs", "duration of bitmaps in us, also called sample interval, when ConstantDuration method is used");
        setPropertyTooltip(patchTT, "ppsScale", "scale of pixels per second to draw local motion vectors; global vectors are scaled up by an additional factor of " + GLOBAL_MOTION_DRAWING_SCALE);
        setPropertyTooltip(patchTT, "sliceEventCount", "number of events collected to fill a slice, when ConstantEventNumber method is used");
        setPropertyTooltip(patchTT, "sliceMethod", "set method for determining time slice duration for block matching");
        setPropertyTooltip(patchTT, "skipProcessingEventsCount", "skip this many events for processing (but not for accumulating to bitmaps)");
        setPropertyTooltip(patchTT, "adaptiveEventSkipping", "enables adaptive event skipping depending on free time left in AEViewer animation loop");
        setPropertyTooltip(patchTT, "adapativeSliceDuration", "<html>enables adaptive slice duration using feedback control, <br> based on average match search distance compared with total search distance. <p>If the match is too close short, increaes duration, and if too far, decreases duration");
        setPropertyTooltip(patchTT, "processingTimeLimitMs", "<html>time limit for processing packet in ms to process OF events (events still accumulate). <p>Alternative to the system EventPacket timelimiter, which cannot be used here because we still need to accumulate and render the events");
        setPropertyTooltip(patchTT, "outputSearchErrorInfo", "enables displaying the search method error information");
        setPropertyTooltip(patchTT, "showSliceBitMap", "enables displaying the slices' bitmap");

//        setPropertyTooltip(eventSqeMatching, "cost", "The cost to translation one event to the other position");
//        setPropertyTooltip(eventSqeMatching, "thresholdTime", "The threshold value of interval time between the first event and the last event");
//        setPropertyTooltip(eventSqeMatching, "sliceEventCount", "number of collected events in each bitmap");
//        setPropertyTooltip(eventSqeMatching, "eventPatchDimension", "linear dimenion of patches to match, in pixels");
        setPropertyTooltip(dispTT, "displayOutputVectors", "display the output motion vectors or not");
        setPropertyTooltip(dispTT, "displayResultHistogram", "display the output motion vectors histogram to show disribution of results for each packet. Only implemented for HammingDistance");

        getSupport().addPropertyChangeListener(AEViewer.EVENT_TIMESTAMPS_RESET, this);
        getSupport().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, this);
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWIND, this);
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_NON_MONOTONIC_TIMESTAMP, this);
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        setupFilter(in);
        checkArrays();
        adaptEventSkipping();
        if (resultHistogram == null || resultHistogram.length != 2 * searchDistance + 1) {
            int dim = 2 * searchDistance + 1; // e.g. search distance 1, dim=3, 3x3 possibilties (including zero motion) 
            resultHistogram = new int[dim][dim];
            resultHistogramCount = 0;
        } else {
            if (adapativeSliceDuration && resultHistogramCount > 0) {
                // measure last hist to get control signal on slice duration
                float radiusSum = 0, countSum = 0;
                for (int x = -searchDistance; x <= searchDistance; x++) {
                    for (int y = -searchDistance; y <= searchDistance; y++) {
                        int count = resultHistogram[x + searchDistance][y + searchDistance];
                        if (count > 0) {
                            final float radius = (float) Math.sqrt(x * x + y * y);
                            countSum += count;
                            radiusSum += radius * count;
                        }
                    }
                }
                lastAvgMatchDistance = avgMatchDistance;
                avgMatchDistance = radiusSum / (countSum);

                double[] rstHist1D = new double[resultHistogram.length * resultHistogram.length];
                int index = 0;
                int rstHistMax = 0;
                for (int[] resultHistogram1 : resultHistogram) {
                    for (int n = 0; n < resultHistogram1.length; n++) {
                        rstHist1D[index++] = resultHistogram1[n];
                    }
                }

                Statistics histStats = new Statistics(rstHist1D);
                // double histMax = Collections.max(Arrays.asList(ArrayUtils.toObject(rstHist1D)));   
                double histMax = histStats.getMax();
                for (int m = 0; m < rstHist1D.length; m++) {
                    rstHist1D[m] = rstHist1D[m] / histMax;
                }
                double histStdDev = histStats.getStdDev();
                double histMean = histStats.getMean();

                // compute error signal. 
                // If err<0 it means the average match distance is larger than 1/2 search distance, so we need to reduce slice duration
                // If err>0, it means the avg match distance is too short, so increse time slice
                // TODO some bug in following
                final float err = (searchDistance / 2) - avgMatchDistance;
                final float lastErr = searchDistance / 2 - lastAvgMatchDistance;
//                final double err = histMean - 1/ (rstHist1D.length * rstHist1D.length); 
                float errSign = (float) Math.signum(err);

//                if(Math.abs(err) > Math.abs(lastErr)) {
//                    errSign = -errSign;
//                }
//                if(histStdDev >= 0.1) {
//                    errSign = 1;
//                } else if(avgMatchDistance >= searchDistance/2) {
//                    errSign = -1;
//                }
                int durChange = (int) (errSign * adapativeSliceDurationProportionalErrorGain * sliceDurationUs);
                setSliceDurationUs(sliceDurationUs + durChange);
            }
            // clear histograms for each packet so that we accumulate OF distribution for this packet
            for (int[] h : resultHistogram) {
                Arrays.fill(h, 0);
            }
        }
        resultHistogramCount = 0;
        timeLimiter.setTimeLimitMs(processingTimeLimitMs);
        timeLimiter.restart();

//        ApsDvsEventPacket in2 = (ApsDvsEventPacket) in;
//        Iterator itr = in2.fullIterator();   // Wfffsfe also need IMU data, so here we use the full iterator.
        for (Object o : in) { // to support pure DVS like DVS128
            BasicEvent ein = (BasicEvent) o;
            if (ein == null) {
                log.warning("null event passed in, returning input packet");
                return in;
            }

            if (!extractEventInfo(ein)) {
                continue;
            }
//            ApsDvsEvent apsDvsEvent = (ApsDvsEvent) ein;
//            if (apsDvsEvent.isImuSample()) {
//                IMUSample s = apsDvsEvent.getImuSample();
//                continue;
//            }
//            if (apsDvsEvent.isApsData()) {
//                continue;
//            }
            // inItr = in.inputIterator;
            if (measureAccuracy || discardOutliersForStatisticalMeasurementEnabled) {
                imuFlowEstimator.calculateImuFlow((ApsDvsEvent) inItr.next());
                setGroundTruth();
            }

            if (xyFilter()) {
                continue;
            }
            countIn++;

            // compute flow
            SADResult result = null;

//            int blockLocX = x / eventPatchDimension;
//            int blockLocY = y / eventPatchDimension;
            // Build the spike trains of every block, every block is consist of 3*3 pixels.
//            if (spikeTrains[blockLocX][blockLocY] == null) {
//                spikeTrains[blockLocX][blockLocY] = new ArrayList();
//            }
//            int spikeBlokcLength = spikeTrains[blockLocX][blockLocY].size();
//            int previousTsInterval = 0;
//            if (spikeBlokcLength == 0) {
//                previousTsInterval = ts;
//            } else {
//                previousTsInterval = ts - spikeTrains[blockLocX][blockLocY].get(spikeBlokcLength - 1)[0];
//            }
//            if (preProcessEnable || patchCompareMethod == PatchCompareMethod.EventSqeDistance) {
//                spikeTrains[blockLocX][blockLocY].add(new Integer[]{ts, type});
//            }
            switch (patchCompareMethod) {
                case HammingDistance:
                    maybeRotateSlices();
                    if (!accumulateEvent(in)) {
                        break;
                    }
//                    if (preProcessEnable) {
//                        // There are enough events fire on the specific block now.
//                        if ((spikeTrains[blockLocX][blockLocY].size() - lastFireIndex[blockLocX][blockLocY]) >= forwardEventNum) {
//                            lastFireIndex[blockLocX][blockLocY] = spikeTrains[blockLocX][blockLocY].size() - 1;
//                            result = minHammingDistance(x, y, tMinus2Sli, tMinus1Sli);
//                            result.dx = (result.dx / sliceDurationUs) * 1000000;
//                            result.dy = (result.dy / sliceDurationUs) * 1000000;
//                        }
//                    } else {
//                    result = minHammingDistance(x, y, tMinus2Sli, tMinus1Sli);
                    result = minHammingDistance(x, y, tm2Bitmap, tm1Bitmap);
                    if (showSliceBitMap) {
                        showBitmaps(x, y, (int) result.dx, (int) result.dy, tm1Bitmap, tm2Bitmap);
                    }
                    result.dx = (result.dx / sliceDurationUs) * 1000000; // hack, convert to pix/second
                    result.dy = (result.dy / sliceDurationUs) * 1000000;
//                    }

                    break;
//                case SAD:
//                    maybeRotateSlices();
//                    if (!accumulateEvent(in)) {
//                        break;
//                    }
////                    if (preProcessEnable) {
////                        // There're enough events fire on the specific block now
////                        if ((spikeTrains[blockLocX][blockLocY].size() - lastFireIndex[blockLocX][blockLocY]) >= forwardEventNum) {
////                            lastFireIndex[blockLocX][blockLocY] = spikeTrains[blockLocX][blockLocY].size() - 1;
////                            result = minSad(x, y, tMinus2Sli, tMinus1Sli);
////                            result.dx = (result.dx / sliceDurationUs) * 1000000;
////                            result.dy = (result.dy / sliceDurationUs) * 1000000;
////
////                        }
////                    } else {
////                    result = minSad(x, y, tMinus2Sli, tMinus1Sli);
//                    result = minSad(x, y, tm2Bitmap, tm1Bitmap);
//                    result.dx = (result.dx / sliceDurationUs) * 1000000;
//                    result.dy = (result.dy / sliceDurationUs) * 1000000;
////                    }
//                    break;
                case JaccardDistance:
                    maybeRotateSlices();
                    if (!accumulateEvent(in)) {
                        break;
                    }
//                    if (preProcessEnable) {
//                        // There're enough events fire on the specific block now
//                        if ((spikeTrains[blockLocX][blockLocY].size() - lastFireIndex[blockLocX][blockLocY]) >= forwardEventNum) {
//                            lastFireIndex[blockLocX][blockLocY] = spikeTrains[blockLocX][blockLocY].size() - 1;
//                            result = minJaccardDistance(x, y, tMinus2Sli, tMinus1Sli);
//                            result.dx = (result.dx / sliceDurationUs) * 1000000;
//                            result.dy = (result.dy / sliceDurationUs) * 1000000;
//                        }
//                    } else {
//                    result = minJaccardDistance(x, y, tMinus2Sli, tMinus1Sli);
                    result = minJaccardDistance(x, y, tm2Bitmap, tm1Bitmap);
                    result.dx = (result.dx / sliceDurationUs) * 1000000;
                    result.dy = (result.dy / sliceDurationUs) * 1000000;
//                    }
                    break;
//                case EventSqeDistance:
//                    if (previousTsInterval < 0) {
//                        spikeTrains[blockLocX][blockLocY].remove(spikeTrains[blockLocX][blockLocY].size() - 1);
//                        continue;
//                    }
//
//                    if (previousTsInterval >= thresholdTime) {
//                        float maxDt = 0;
//                        float[][] dataPoint = new float[9][2];
//                        if ((blockLocX >= 1) && (blockLocY >= 1) && (blockLocX <= 238) && (blockLocY <= 178)) {
//                            for (int ii = -1; ii < 2; ii++) {
//                                for (int jj = -1; jj < 2; jj++) {
//                                    float dt = ts - eventSeqStartTs[blockLocX + ii][blockLocY + jj];
//
//                                    // Remove the seq1 itself
//                                    if ((0 == ii) && (0 == jj)) {
//                                        // continue;
//                                        dt = 0;
//                                    }
//
//                                    dataPoint[((ii + 1) * 3) + (jj + 1)][0] = dt;
//                                    if (dt > maxDt) {
//                                    }
//                                }
//                            }
//                        }
//                        // result = minVicPurDistance(blockLocX, blockLocY);
//
//                        eventSeqStartTs[blockLocX][blockLocY] = ts;
//                        boolean allZeroFlg = true;
//                        for (int mm = 0; mm < 9; mm++) {
//                            for (int nn = 0; nn < 1; nn++) {
//                                if (dataPoint[mm][nn] != 0) {
//                                    allZeroFlg = false;
//                                }
//                            }
//                        }
//                        if (allZeroFlg) {
//                            continue;
//                        }
//                        KMeans cluster = new KMeans();
//                        cluster.setData(dataPoint);
//                        int[] initialValue = new int[3];
//                        initialValue[0] = 0;
//                        initialValue[1] = 4;
//                        initialValue[2] = 8;
//                        cluster.setInitialByUser(initialValue);
//                        cluster.cluster();
//                        ArrayList<ArrayList<Integer>> kmeansResult = cluster.getResult();
//                        float[][] classData = cluster.getClassData();
//                        int firstClusterIdx = -1, secondClusterIdx = -1, thirdClusterIdx = -1;
//                        for (int i = 0; i < 3; i++) {
//                            if (kmeansResult.get(i).contains(0)) {
//                                firstClusterIdx = i;
//                            }
//                            if (kmeansResult.get(i).contains(4)) {
//                                secondClusterIdx = i;
//                            }
//                            if (kmeansResult.get(i).contains(8)) {
//                                thirdClusterIdx = i;
//                            }
//                        }
//                        if ((kmeansResult.get(firstClusterIdx).size() == 3)
//                                && (kmeansResult.get(firstClusterIdx).size() == 3)
//                                && (kmeansResult.get(firstClusterIdx).size() == 3)
//                                && kmeansResult.get(firstClusterIdx).contains(1)
//                                && kmeansResult.get(firstClusterIdx).contains(2)) {
//                            result.dx = (-1 / (classData[secondClusterIdx][0] - classData[firstClusterIdx][0])) * 1000000 * 0.2f * eventPatchDimension;;
//                            result.dy = 0;
//                        }
//                        if ((kmeansResult.get(firstClusterIdx).size() == 3)
//                                && (kmeansResult.get(firstClusterIdx).size() == 3)
//                                && (kmeansResult.get(firstClusterIdx).size() == 3)
//                                && kmeansResult.get(thirdClusterIdx).contains(2)
//                                && kmeansResult.get(thirdClusterIdx).contains(5)) {
//                            result.dy = (-1 / (classData[thirdClusterIdx][0] - classData[secondClusterIdx][0])) * 1000000 * 0.2f * eventPatchDimension;;
//                            result.dx = 0;
//                        }
//                    }
//                    break;
            }
            if (result == null) {
                continue; // maybe some property change caused this
            }
            vx = result.dx;
            vy = result.dy;
            v = (float) Math.sqrt((vx * vx) + (vy * vy));

            // reject values that are unreasonable
            if (isNotSufficientlyAccurate(result)) {
                continue;
            }

            if (resultHistogram != null) {
                resultHistogram[result.xidx][result.yidx]++;
                resultHistogramCount++;
            }
            processGoodEvent();
        }

        if (rewindFlg) {
            rewindFlg = false;
            sliceLastTs = 0;

//            final int sx = chip.getSizeX(), sy = chip.getSizeY();
//            for (int i = 0; i < sx; i++) {
//                for (int j = 0; j < sy; j++) {
//                    if (spikeTrains != null && spikeTrains[i][j] != null) {
//                        spikeTrains[i][j] = null;
//                    }
//                    if (lastFireIndex != null) {
//                        lastFireIndex[i][j] = 0;
//                    }
//                    eventSeqStartTs[i][j] = 0;
//                }
//            }
        }
        motionFlowStatistics.updatePacket(countIn, countOut);
        return isDisplayRawInput() ? in : dirPacket;
    }

    private EngineeringFormat engFmt = new EngineeringFormat();
    private TextRenderer textRenderer = null;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        if (displayResultHistogram && resultHistogram != null) {
            GL2 gl = drawable.getGL().getGL2();
            // draw histogram as shaded in 2d hist above color wheel
            // normalize hist
            int max = 0;
            for (int[] h : resultHistogram) {
                for (int v : h) {
                    if (v > max) {
                        max = v;
                    }
                }
            }
            if (max == 0) {
                return;
            }
            final float maxRecip = 1f / max;
            int dim = resultHistogram.length; // 2*searchDistance+1
            gl.glPushMatrix();
            final float scale = 30 / (2 * searchDistance + 1); // size same as the color wheel
            gl.glTranslatef(-35, .65f * chip.getSizeY(), 0);  // center above color wheel
            gl.glScalef(scale, scale, 1);
            gl.glColor3f(0, 0, 1);
            gl.glLineWidth(2f);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(dim, 0);
            gl.glVertex2f(dim, dim);
            gl.glVertex2f(0, dim);
            gl.glEnd();
            for (int x = 0; x < dim; x++) {
                for (int y = 0; y < dim; y++) {
                    float g = maxRecip * resultHistogram[x][y];
                    gl.glColor3f(g, g, g);
                    gl.glBegin(GL2.GL_QUADS);
                    gl.glVertex2f(x, y);
                    gl.glVertex2f(x + 1, y);
                    gl.glVertex2f(x + 1, y + 1);
                    gl.glVertex2f(x, y + 1);
                    gl.glEnd();
                }
            }
            if (textRenderer == null) {
                textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 48));
            }
            if (avgMatchDistance > 0) {
                gl.glColor3f(1, 0, 0);
                gl.glLineWidth(5f);
                DrawGL.drawCircle(gl, searchDistance + .5f, searchDistance + .5f, avgMatchDistance, 16);
            }
            // a bunch of cryptic crap to draw a string the same width as the histogram...
            gl.glTranslatef(-dim / 2, dim / 2, 0); // translate to UL corner of histogram
            textRenderer.begin3DRendering();
            String s = String.format("d=%s ms", engFmt.format(1e-3f * sliceDurationUs));
//            final float sc = TextRendererScale.draw3dScale(textRenderer, s, chip.getCanvas().getScale(), chip.getSizeX(), .1f);
            // determine width of string in pixels and scale accordingly
            FontRenderContext frc = textRenderer.getFontRenderContext();
            Rectangle2D r = textRenderer.getBounds(s); // bounds in java2d coordinates, downwards more positive
            Rectangle2D rt = frc.getTransform().createTransformedShape(r).getBounds2D(); // get bounds in textrenderer coordinates
//            float ps = chip.getCanvas().getScale();
            float w = (float) rt.getWidth(); // width of text in textrenderer, i.e. histogram cell coordinates (1 unit = 1 histogram cell)
            float sc = dim / w; // scale to histogram width
            textRenderer.draw3D(s, 0, 0, 0, sc);
            textRenderer.end3DRendering();
            String s2 = String.format("skip %d", skipProcessingEventsCount);
            gl.glTranslatef(0f, (float) (rt.getHeight()) * sc, 0);
            textRenderer.begin3DRendering();
            textRenderer.draw3D(s2, 0, 0, 0, sc);
            textRenderer.end3DRendering();
            gl.glPopMatrix();

        }
    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter();
        eventCounter = 0;
        lastTs = Integer.MIN_VALUE;

        if ((histograms == null) || (histograms.length != subSizeX) || (histograms[0].length != subSizeY)) {
            if ((NUM_SLICES == 0) && (subSizeX == 0) && (subSizeX == 0)) {
                return;
            }
            histograms = new int[NUM_SLICES][subSizeX][subSizeY];
        }
        for (int[][] a : histograms) {
            for (int[] b : a) {
                Arrays.fill(b, 0);
            }
        }
//        if (histogramsBitSet == null) {
//            histogramsBitSet = new BitSet[NUM_SLICES];
//        }
//
//        for (int ii = 0; ii < NUM_SLICES; ii++) {
//            histogramsBitSet[ii] = new BitSet(subSizeX * subSizeY);
//        }

        if (bitmaps == null || bitmaps[0].length != subSizeX || bitmaps[0][0].length != subSizeY) {
            bitmaps = new boolean[NUM_SLICES][subSizeX][subSizeY];
        } else {
            for (boolean[][] b : bitmaps) {
                clearBitmap(b);
            }
        }

//        // Initialize 3 ArrayList's histogram, every pixel has three patches: current, previous and previous-1
//        if (histogramsAL == null) {
//            histogramsAL = new ArrayList[3];
//        }
//        if ((spikeTrains == null) & (subSizeX != 0) & (subSizeY != 0)) {
//            spikeTrains = new ArrayList[subSizeX][subSizeY];
//        }
//        if (patchDimension != 0) {
//            int colPatchCnt = subSizeX / patchDimension;
//            int rowPatchCnt = subSizeY / patchDimension;
//
////            for (int ii = 0; ii < NUM_SLICES; ii++) {
////                histogramsAL[ii] = new ArrayList();
////                for (int jj = 0; jj < (colPatchCnt * rowPatchCnt); jj++) {
////                    int[][] patch = new int[patchDimension][patchDimension];
////                    histogramsAL[ii].add(patch);
////                }
////            }
//        }
        tMinus2SliceIdx = 0;
        tMinus1SliceIdx = 1;
        currentSliceIdx = 2;
        assignSliceReferences();

        sliceLastTs = 0;
        rewindFlg = true;
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

    /**
     * uses the current event to maybe rotate the slices
     */
    private void maybeRotateSlices() {
        int dt = ts - sliceLastTs;

        switch (sliceMethod) {
            case ConstantDuration:
                if (rewindFlg) {
                    return;
                }
                if ((dt < sliceDurationUs) || (dt < 0)) {
                    return;
                }
                break;
            case ConstantEventNumber:
                if (eventCounter++ < sliceEventCount) {
                    return;
                }
            case AdaptationDuration:
                log.warning("The adaptation method is not supported yet.");
                return;
        }

        /* The index cycle is " current idx -> t1 idx -> t2 idx -> current idx".
           Change the index, the change should like this:
           next t2 = previous t1 = histogram(previous t2 idx + 1);
           next t1 = previous current = histogram(previous t1 idx + 1);
         */
        currentSliceIdx = (currentSliceIdx + 1) % NUM_SLICES;
        tMinus1SliceIdx = (tMinus1SliceIdx + 1) % NUM_SLICES;
        tMinus2SliceIdx = (tMinus2SliceIdx + 1) % NUM_SLICES;
        sliceEventCount = 0;
        sliceLastTs = ts;
        assignSliceReferences();
    }

    private int updateAdaptDuration() {
        return 1000;
    }

    private void assignSliceReferences() {
        currentSlice = histograms[currentSliceIdx];
        tMinus1Slice = histograms[tMinus1SliceIdx];
        tMinus2Slice = histograms[tMinus2SliceIdx];

//        currentSli = histogramsBitSet[currentSliceIdx];
//        tMinus1Sli = histogramsBitSet[tMinus1SliceIdx];
//        tMinus2Sli = histogramsBitSet[tMinus2SliceIdx];
//        currentSli.clear();
        currentBitmap = bitmaps[currentSliceIdx];
        tm1Bitmap = bitmaps[tMinus1SliceIdx];
        tm2Bitmap = bitmaps[tMinus2SliceIdx];
        clearBitmap(currentBitmap);

    }

    /**
     * Accumulates the current event to the current slice
     *
     * @return true if subsequent processing should done, false if it should be
     * skipped for efficiency
     */
    private boolean accumulateEvent(EventPacket in) {
        switch (patchCompareMethod) {
//            case SAD:
            case JaccardDistance:
                currentSlice[x][y] += e.getPolaritySignum();
                break;
            case HammingDistance:
//                currentSli.set((x + 1) + (y * subSizeX));  // All events wheather 0 or 1 will be set in the BitSet Slice
                currentBitmap[x][y] = true;
        }
        if (timeLimiter.isTimedOut()) {
            return false;
        }
        if (skipProcessingEventsCount == 0) {
            return true;
        }
        if (skipCounter++ < skipProcessingEventsCount) {
            return false;
        }
        skipCounter = 0;
        return true;
    }

    private void clearSlice(int idx) {
        for (int[] a : histograms[idx]) {
            Arrays.fill(a, 0);
        }
    }

    /**
     * Computes hamming eight around point x,y using patchDimension and
     * searchDistance
     *
     * @param x coordinate in subsampled space
     * @param y
     * @param prevSlice
     * @param curSlice
     * @return SADResult that provides the shift and SAD value
     */
//    private SADResult minHammingDistance(int x, int y, BitSet prevSlice, BitSet curSlice) {
    private SADResult minHammingDistance(int x, int y, boolean[][] prevSlice, boolean[][] curSlice) {
        float minSum = Integer.MAX_VALUE, minSum1 = Integer.MAX_VALUE, sum = 0;

        float FSDx = 0, FSDy = 0, DSDx = 0, DSDy = 0;  // This is for testing the DS search accuracy.       
        int searchRange = 2 * searchDistance + 1; // The maxium search index, for xidx and yidx.
        float sumArray1[][] = new float[2 * searchDistance + 1][2 * searchDistance + 1];
        for (float[] row : sumArray1) {
            Arrays.fill(row, Integer.MAX_VALUE);
        }

        if (outputSearchErrorInfo) {
            searchMethod = SearchMethod.FullSearch;
        } else {
            searchMethod = getSearchMethod();
        }

        switch (searchMethod) {
            case FullSearch:
                for (int dx = -searchDistance; dx <= searchDistance; dx++) {
                    for (int dy = -searchDistance; dy <= searchDistance; dy++) {
                        sum = hammingDistance(x, y, dx, dy, prevSlice, curSlice);
                        sumArray1[dx + searchDistance][dy + searchDistance] = sum;
                        if (sum <= minSum1) {
                            if(sum == minSum1 && minSum1 != Integer.MAX_VALUE) {
                                tmpSadResult.minSearchedFlg = true;
                            } else {
                                tmpSadResult.minSearchedFlg = false;                                
                            }
                            minSum1 = sum;
                            tmpSadResult.dx = dx;
                            tmpSadResult.dy = dy;
                            tmpSadResult.sadValue = minSum1;
                        }
                    }
                }
                if(tmpSadResult.minSearchedFlg) {
                    tmpSadResult.sadValue = Integer.MAX_VALUE;     // This minimum is not a unique minimum, we should reject this event.
                }
                if (outputSearchErrorInfo) {
                    FSCnt += 1;
                    FSDx = tmpSadResult.dx;
                    FSDy = tmpSadResult.dy;
                } else {
                    break;
                }
            case DiamondSearch:
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
                 yidx;

                int minPointIdx = 0;      // Store the minimum point index.
                boolean SDSPFlg = false;  // If this flag is set true, then it means LDSP search is finished and SDSP search could start.

                /* If one block has been already calculated, the computedFlg will be set so we don't to do 
                       the calculation again.
                 */
                boolean computedFlg[][] = new boolean[2 * searchDistance + 1][2 * searchDistance + 1];
                for (boolean[] row : computedFlg) {
                    Arrays.fill(row, false);
                }
                float sumArray[][] = new float[2 * searchDistance + 1][2 * searchDistance + 1];
                for (float[] row : sumArray) {
                    Arrays.fill(row, Integer.MAX_VALUE);
                }

                if (searchDistance == 1) { // LDSP search can only be applied for search distance >= 2.
                    SDSPFlg = true;
                }

                while (!SDSPFlg) {
                    /* 1. LDSP search */
                    for (int pointIdx = 0; pointIdx < LDSP.length; pointIdx++) {
                        dx = LDSP[pointIdx][0] + xCenter - x;
                        dy = LDSP[pointIdx][1] + yCenter - y;

                        xidx = dx + searchDistance;
                        yidx = dy + searchDistance;

                        // Point to be searched is out of search area, skip it. 
                        if (xidx >= searchRange || yidx >= searchRange || xidx < 0 || yidx < 0) {
                            continue;
                        }

                        /* We just calculate the blocks that haven't been calculated before */
                        if (computedFlg[xidx][yidx] == false) {
                            sumArray[xidx][yidx] = hammingDistance(x, y, dx, dy, prevSlice, curSlice);
                            computedFlg[xidx][yidx] = true;
                            if (outputSearchErrorInfo) {
                                DSAverageNum++;
                            }
                            if (outputSearchErrorInfo) {
                                if (sumArray[xidx][yidx] != sumArray1[xidx][yidx]) {
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
                    } else {
                        SDSPFlg = false;
                    }
                }

                /* 3. SDSP Search */
                for (int pointIdx = 0; pointIdx < SDSP.length; pointIdx++) {
                    dx = SDSP[pointIdx][0] + xCenter - x;
                    dy = SDSP[pointIdx][1] + yCenter - y;

                    xidx = dx + searchDistance;
                    yidx = dy + searchDistance;

                    // Point to be searched is out of search area, skip it. 
                    if (xidx >= searchRange || yidx >= searchRange || xidx < 0 || yidx < 0) {
                        continue;
                    }

                    /* We just calculate the blocks that haven't been calculated before */
                    if (computedFlg[xidx][yidx] == false) {
                        sumArray[xidx][yidx] = hammingDistance(x, y, dx, dy, prevSlice, curSlice);
                        computedFlg[xidx][yidx] = true;
                        if (outputSearchErrorInfo) {
                            DSAverageNum++;
                        }
                        if (outputSearchErrorInfo) {
                            if (sumArray[xidx][yidx] != sumArray1[xidx][yidx]) {
                                log.warning("It seems that there're some bugs in the DS algorithm.");
                            }
                        }
                    }

                    if (sumArray[xidx][yidx] <= minSum) {
                        minSum = sumArray[xidx][yidx];
                        tmpSadResult.dx = dx;
                        tmpSadResult.dy = dy;
                        tmpSadResult.sadValue = minSum;
                    }
                }

                if (outputSearchErrorInfo) {
                    DSDx = tmpSadResult.dx;
                    DSDy = tmpSadResult.dy;
                }
                break;
            case CrossDiamondSearch:
                break;
        }
        tmpSadResult.xidx = (int) tmpSadResult.dx + searchDistance;
        tmpSadResult.yidx = (int) tmpSadResult.dy + searchDistance; // what a hack....

        if (outputSearchErrorInfo) {
            if (DSDx == FSDx && DSDy == FSDy) {
                DSCorrectCnt += 1;
            } else {
                DSAveError[0] += Math.abs(DSDx - FSDx);
                DSAveError[1] += Math.abs(DSDy - FSDy);
            }
            if (0 == FSCnt % 10000) {
                log.log(Level.INFO, "Correct Diamond Search times are {0}, Full Search times are {1}, accuracy is {2}, averageNumberPercent is {3}, averageError is ({4}, {5})",
                        new Object[]{DSCorrectCnt, FSCnt, DSCorrectCnt / FSCnt, DSAverageNum / (searchRange * searchRange * FSCnt), DSAveError[0] / FSCnt, DSAveError[1] / (FSCnt - DSCorrectCnt)});
            }
        }

        return tmpSadResult;
    }

    /**
     * computes Hamming distance centered on x,y with patch of patchSize for
     * prevSliceIdx relative to curSliceIdx patch.
     *
     * @param x coordinate x in subSampled space
     * @param y coordinate y in subSampled space
     * @param dx
     * @param dy
     * @param prevSlice
     * @param curSlice
     * @return Hamming Distance value, max 1 when all bits differ, min 0 when
     * all the same
     */
//    private float hammingDistance(int x, int y, int dx, int dy, BitSet prevSlice, BitSet curSlice) {
    private float hammingDistance(int x, int y, int dx, int dy, boolean[][] prevSlice, boolean[][] curSlice) {
        float retVal = 0;
        int hd = 0;
        final int blockRadius = patchDimension / 2;
        float validPixNumCurrSli = 0, validPixNumPrevSli = 0; // The valid pixel number in the current block

        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
        if ((x < (blockRadius + dx)) || (x >= ((subSizeX - blockRadius) + dx)) || (x < blockRadius) || (x >= (subSizeX - blockRadius))
                || (y < (blockRadius + dy)) || (y >= ((subSizeY - blockRadius) + dy)) || (y < blockRadius) || (y >= (subSizeY - blockRadius))) {
            return 1; // TODO why return 1 here?  
        }

        for (int xx = x - blockRadius; xx <= (x + blockRadius); xx++) {
            for (int yy = y - blockRadius; yy <= (y + blockRadius); yy++) {
//                boolean currSlicePol = curSlice.get((xx + 1) + ((yy) * subSizeX)); // binary value on (xx, yy) for current slice
//                boolean prevSlicePol = prevSlice.get(((xx + 1) - dx) + ((yy - dy) * subSizeX)); // binary value on (xx, yy) for previous slice
                boolean currSlicePol = curSlice[xx][yy]; // binary value on (xx, yy) for current slice
                boolean prevSlicePol = prevSlice[xx - dx][yy - dy]; // binary value on (xx, yy) for previous slice
                if (currSlicePol != prevSlicePol) {
                    hd += 1;
                }
                if (currSlicePol == true) {
                    validPixNumCurrSli++;
                }
                if (prevSlicePol == true) {
                    validPixNumPrevSli++;
                }
            }
        }

        // TODD: NEXT WORK IS TO DO THE RESEARCH ON WEIGHTED HAMMING DISTANCE
        // Calculate the metric confidence value
        float validPixNum = this.validPixOccupancy * (((2 * blockRadius) + 1) * ((2 * blockRadius) + 1));
        if ((validPixNumCurrSli <= validPixNum) || (validPixNumPrevSli <= validPixNum)) {  // If valid pixel number of any slice is 0, then we set the distance to very big value so we can exclude it.
            return 1;
        } else {
            /*
            retVal consists of the distance and the dispersion. dispersion is used to describe the spatial relationship within one block.
            Here we use the difference between validPixNumCurrSli and validPixNumPrevSli to calculate the dispersion.
            Inspired by paper "Measuring the spatial dispersion of evolutionist search process: application to Walksat" by Alain Sidaner.
             */
            return ((hd * weightDistance) + (Math.abs(validPixNumCurrSli - validPixNumPrevSli) * (1 - weightDistance))) / (((2 * blockRadius) + 1) * ((2 * blockRadius) + 1));
        }
    }

    /**
     * Computes hamming weight around point x,y using patchDimension and
     * searchDistance
     *
     * @param x coordinate in subsampled space
     * @param y
     * @param prevSlice
     * @param curSlice
     * @return SADResult that provides the shift and SAD value
     */
//    private SADResult minJaccardDistance(int x, int y, BitSet prevSlice, BitSet curSlice) {
    private SADResult minJaccardDistance(int x, int y, boolean[][] prevSlice, boolean[][] curSlice) {
        float minSum = Integer.MAX_VALUE, sum = 0;
        SADResult sadResult = new SADResult(0, 0, 0);
        for (int dx = -searchDistance; dx <= searchDistance; dx++) {
            for (int dy = -searchDistance; dy <= searchDistance; dy++) {
                sum = jaccardDistance(x, y, dx, dy, prevSlice, curSlice);
                if (sum <= minSum) {
                    minSum = sum;
                    sadResult.dx = dx;
                    sadResult.dy = dy;
                    sadResult.sadValue = minSum;
                }
            }
        }

        return sadResult;
    }

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
        float retVal = 0;
        float M01 = 0, M10 = 0, M11 = 0;
        int blockRadius = patchDimension / 2;

        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
        if ((x < (blockRadius + dx)) || (x >= ((subSizeX - blockRadius) + dx)) || (x < blockRadius) || (x >= (subSizeX - blockRadius))
                || (y < (blockRadius + dy)) || (y >= ((subSizeY - blockRadius) + dy)) || (y < blockRadius) || (y >= (subSizeY - blockRadius))) {
            return 1;
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
//     * Computes min SAD shift around point x,y using patchDimension and
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
//        int blockRadius = patchDimension / 2;
//        // Make sure 0<=xx+dx<subSizeX, 0<=xx<subSizeX and 0<=yy+dy<subSizeY, 0<=yy<subSizeY,  or there'll be arrayIndexOutOfBoundary exception.
//        if ((x < (blockRadius + dx)) || (x >= ((subSizeX - blockRadius) + dx)) || (x < blockRadius) || (x >= (subSizeX - blockRadius))
//                || (y < (blockRadius + dy)) || (y >= ((subSizeY - blockRadius) + dy)) || (y < blockRadius) || (y >= (subSizeY - blockRadius))) {
//            return 1;
//        }
//
//        float sad = 0, retVal = 0;
//        float validPixNumCurrSli = 0, validPixNumPrevSli = 0; // The valid pixel number in the current block
//        for (int xx = x - blockRadius; xx <= (x + blockRadius); xx++) {
//            for (int yy = y - blockRadius; yy <= (y + blockRadius); yy++) {
//                boolean currSlicePol = curSlice.get((xx + 1) + ((yy) * subSizeX)); // binary value on (xx, yy) for current slice
//                boolean prevSlicePol = prevSlice.get(((xx + 1) - dx) + ((yy - dy) * subSizeX)); // binary value on (xx, yy) for previous slice
//
//                int d = (currSlicePol ? 1 : 0) - (prevSlicePol ? 1 : 0);
//                if (currSlicePol == true) {
//                    validPixNumCurrSli += 1;
//                }
//                if (prevSlicePol == true) {
//                    validPixNumPrevSli += 1;
//                }
//                if (d <= 0) {
//                    d = -d;
//                }
//                sad += d;
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

        float dx, dy;
        float sadValue;
        int xidx, yidx; // x and y indices into 2d matrix of result. 0,0 corresponds to motion SW. dx, dy may be negative, like (-1, -1) represents SW. 
        // However, for histgram index, it's not possible to use negative number. That's the reason for intrducing xidx and yidx.
        boolean minSearchedFlg = false;  // The flag indicates that this minimum have been already searched before. 
        public SADResult(float dx, float dy, float sadValue) {
            this.dx = dx;
            this.dy = dy;
            this.sadValue = sadValue;
        }

        @Override
        public String toString() {
            return String.format("dx,dy=%d,%5 SAD=%d", dx, dy, sadValue);
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

            if (data.length % 2 == 0) {
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
     * @return the patchDimension
     */
    public int getPatchDimension() {
        return patchDimension;
    }

    /**
     * @param patchDimension the patchDimension to set
     */
    public void setPatchDimension(int patchDimension) {
        int old = this.patchDimension;
        // enforce odd value
        if ((patchDimension & 1) == 0) { // even
            if (patchDimension > old) {
                patchDimension++;
            } else {
                patchDimension--;
            }
        }
        // clip final value
        if (patchDimension < 1) {
            patchDimension = 1;
        } else if (patchDimension > 63) {
            patchDimension = 63;
        }
        this.patchDimension = patchDimension;
        support.firePropertyChange("patchDimension", old, patchDimension);
        putInt("patchDimension", patchDimension);
    }

//    public int getEventPatchDimension() {
//        return eventPatchDimension;
//    }
//
//    public void setEventPatchDimension(int eventPatchDimension) {
//        this.eventPatchDimension = eventPatchDimension;
//        putInt("eventPatchDimension", eventPatchDimension);
//
//    }
//    public int getForwardEventNum() {
//        return forwardEventNum;
//    }
//
//    public void setForwardEventNum(int forwardEventNum) {
//        this.forwardEventNum = forwardEventNum;
//        putInt("forwardEventNum", forwardEventNum);
//    }
//    public float getCost() {
//        return cost;
//    }
//
//    public void setCost(float cost) {
//        this.cost = cost;
//        putFloat("cost", cost);
//    }
//    public int getThresholdTime() {
//        return thresholdTime;
//    }
//
//    public void setThresholdTime(int thresholdTime) {
//        this.thresholdTime = thresholdTime;
//        putInt("thresholdTime", thresholdTime);
//    }
    /**
     * @return the sliceMethod
     */
    public SliceMethod getSliceMethod() {
        return sliceMethod;
    }

    /**
     * @param sliceMethod the sliceMethod to set
     */
    public void setSliceMethod(SliceMethod sliceMethod) {
        this.sliceMethod = sliceMethod;
        putString("sliceMethod", sliceMethod.toString());
    }

    public PatchCompareMethod getPatchCompareMethod() {
        return patchCompareMethod;
    }

    public void setPatchCompareMethod(PatchCompareMethod patchCompareMethod) {
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
    public void setSearchMethod(SearchMethod searchMethod) {
        this.searchMethod = searchMethod;
        putString("searchMethod", searchMethod.toString());
    }

    @Override
    public synchronized void setSearchDistance(int searchDistance) {
        int old = this.searchDistance;

        if (searchDistance > 12) {
            searchDistance = 12;
        } else if (searchDistance < 1) {
            searchDistance = 1; // limit size
        }
        this.searchDistance = searchDistance;
        putInt("searchDistance", searchDistance);
        support.firePropertyChange("searchDistance", old, searchDistance);
        resetFilter();
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
        if (sliceDurationUs < 4000) {
            sliceDurationUs = 4000;
        } else if (sliceDurationUs > 500000) {
            sliceDurationUs = 500000; // limit it to one second
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
        this.sliceEventCount = sliceEventCount;
        putInt("sliceEventCount", sliceEventCount);
    }

//    public boolean isPreProcessEnable() {
//        return preProcessEnable;
//    }
//
//    public void setPreProcessEnable(boolean preProcessEnable) {
//        this.preProcessEnable = preProcessEnable;
//        putBoolean("preProcessEnable", preProcessEnable);
//    }
    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(float confidenceThreshold) {
        if (confidenceThreshold < 0) {
            confidenceThreshold = 0;
        } else if (confidenceThreshold > 1) {
            confidenceThreshold = 1;
        }
        this.confidenceThreshold = confidenceThreshold;
        putFloat("confidenceThreshold", confidenceThreshold);
    }

    public float getValidPixOccupancy() {
        return validPixOccupancy;
    }

    public void setValidPixOccupancy(float validPixOccupancy) {
        if (validPixOccupancy < 0) {
            validPixOccupancy = 0;
        } else if (validPixOccupancy > 1) {
            validPixOccupancy = 1;
        }
        this.validPixOccupancy = validPixOccupancy;
        putFloat("validPixOccupancy", validPixOccupancy);
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

    private void checkArrays() {
//        if (lastFireIndex == null) {
//            lastFireIndex = new int[chip.getSizeX()][chip.getSizeY()];
//        }
//        if (eventSeqStartTs == null) {
//            eventSeqStartTs = new int[chip.getSizeX()][chip.getSizeY()];
//        }
    }

    /**
     *
     * @param distResult
     * @return the confidence of the result. True means it's not good and should
     * be rejected, false means we should accept it.
     */
    public synchronized boolean isNotSufficientlyAccurate(SADResult distResult) {
        boolean retVal = super.accuracyTests();  // check accuracy in super, if reject returns true

        // additional test, normalized blaock distance must be small enough 
        // distance has max value 1
        if (distResult.sadValue >= (1 - confidenceThreshold)) {
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
        if (skipProcessingEventsCount > 300) {
            skipProcessingEventsCount = 300;
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
    public void setAdaptiveEventSkipping(boolean adaptiveEventSkipping) {
        this.adaptiveEventSkipping = adaptiveEventSkipping;
        putBoolean("adaptiveEventSkipping", adaptiveEventSkipping);
    }

    public boolean isOutputSearchErrorInfo() {
        return outputSearchErrorInfo;
    }

    public boolean isShowSliceBitMap() {
        return showSliceBitMap;
    }

    /**
     *
     * @param showSliceBitMpa the option of displaying bitmap
     */
    public void setShowSliceBitMap(boolean showSliceBitMap) {
        boolean old = this.showSliceBitMap;
        this.showSliceBitMap = showSliceBitMap;
        getSupport().firePropertyChange("showSliceBitMap", old, this.showSliceBitMap);
        putBoolean("showSliceBitMap", showSliceBitMap);
    }

    synchronized public void setOutputSearchErrorInfo(boolean outputSearchErrorInfo) {
        this.outputSearchErrorInfo = outputSearchErrorInfo;
        if (!outputSearchErrorInfo) {
            searchMethod = SearchMethod.valueOf(getString("searchMethod", SearchMethod.FullSearch.toString()));  // make sure method is reset
        }
    }

    private int adaptiveEventSkippingUpdateIntervalPackets = 10;
    private int adaptiveEventSkippingUpdateCounter = 0;

    private void adaptEventSkipping() {
        if (!adaptiveEventSkipping) {
            return;
        }
        if (chip.getAeViewer() == null) {
            return;
        }
        if (adaptiveEventSkippingUpdateCounter++ < adaptiveEventSkippingUpdateIntervalPackets) {
            return;
        }
        adaptiveEventSkippingUpdateCounter = 0;
        final float averageFPS = chip.getAeViewer().getFrameRater().getAverageFPS();
        final int frameRate = chip.getAeViewer().getFrameRate();
        boolean skipMore = averageFPS < (int) (0.75f * frameRate);
        boolean skipLess = averageFPS > (int) (0.25f * frameRate);
        if (skipMore) {
            setSkipProcessingEventsCount(Math.round(skipChangeFactor * skipProcessingEventsCount + 1));
        } else if (skipLess) {
            setSkipProcessingEventsCount(Math.round(skipProcessingEventsCount / (skipChangeFactor) - 1));
        }
    }

    /**
     * @return the adapativeSliceDuration
     */
    public boolean isAdapativeSliceDuration() {
        return adapativeSliceDuration;
    }

    /**
     * @param adapativeSliceDuration the adapativeSliceDuration to set
     */
    public void setAdapativeSliceDuration(boolean adapativeSliceDuration) {
        this.adapativeSliceDuration = adapativeSliceDuration;
        putBoolean("adapativeSliceDuration", adapativeSliceDuration);
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt); // resets filter on rewind, etc
    }

    private void clearBitmap(boolean[][] bitmap) {
        for (boolean[] b : bitmap) {
            Arrays.fill(b, false);
        }
    }

    private int dim = patchDimension + 2 * searchDistance;

    private void showBitmaps(int x, int y, int dx, int dy, boolean[][] tm1Bitmap, boolean[][] tm2Bitmap) {
        int dimNew = patchDimension + 2 * searchDistance;
        if (Frame == null) {
            String windowName = "Slice bitmaps";
            Frame = new JFrame(windowName);
            Frame.setLayout(new BoxLayout(Frame.getContentPane(), BoxLayout.Y_AXIS));
            Frame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            display = ImageDisplay.createOpenGLCanvas();
            display.setBorderSpacePixels(10);
            display.setImageSize(dimNew, dimNew);
            display.setSize(200, 200);
            panel.add(display);

            Frame.getContentPane().add(panel);
            Frame.pack();
            Frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    setShowSliceBitMap(false);
                }
            });
        }
        if (!Frame.isVisible()) {
            Frame.setVisible(true);
        }
        if (dimNew != dim) {
            dim = dimNew;
            display.setImageSize(dimNew, dimNew);
        }
        final int dim2 = patchDimension / 2 + searchDistance;

//        GL2 gl = display.getGL().getGL2();
//        gl.glPushMatrix();
        if ((x >= dim2) && (x + dim2 < subSizeX)
                && (y >= dim2) && (y + dim2 < subSizeY)) {
            /* Rendering the reference patch in t-d slice, it's on the bottom-left corner */
            for (int i = 0; i < patchDimension; i++) {
                for (int j = 0; j < patchDimension; j++) {
                    float[] f=display.getPixmapRGB(i, j);
                    f[0]=tm1Bitmap[x - patchDimension / 2 + i][y - patchDimension / 2 + j] ? 1 : 0;
                    display.setPixmapRGB(i, j,f);
                }
            }

            /* Rendering the area within search distance in t-2d slice, it's on the middle */
            for (int i = 0; i < dim2; i++) {
                for (int j = 0; j < dim2 ; j++) {
                    display.setPixmapRGB(i, j, 0,tm2Bitmap[x - dim2 + i ][y -dim2 + j] ? 1 : 0,0);
                }
            }

            /* Rendering the best matching patch in t-d slice, it's on the up-right corner */
            for (int i = 0; i < patchDimension ; i++) {
                for (int j = 0; j < patchDimension ; j++) {
                    display.setPixmapRGB(i, j, 0,0,tm2Bitmap[x - patchDimension / 2 + dx + i][y - patchDimension / 2 + dy + j] ? 1 : 0);
                }
            }
        }

//        gl.glPopMatrix();
        display.repaint();
    }
}
