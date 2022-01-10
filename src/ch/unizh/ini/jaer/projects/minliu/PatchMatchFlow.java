/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.minliu;

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
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
import ch.unizh.ini.jaer.projects.rbodo.opticalflow.MotionFlowStatistics;
import ch.unizh.ini.jaer.projects.rbodo.opticalflow.MotionFlowStatistics.GlobalMotion;
import com.jogamp.opengl.GLException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.TimeLimiter;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.ImageDisplay.Legend;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.TobiLogger;
import net.sf.jaer.util.filter.LowpassFilter;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Uses adaptive block matching optical flow (ABMOF) to measureTT local optical
 * flow. <b>Not</b> gradient based, but rather matches local features backwards
 * in time.
 *
 * @author Tobi and Min, Jan 2016
 */
@Description("<html>EDFLOW: Computes optical flow with vector direction using SFAST keypoint/corner detection and adaptive time slice block matching (ABMOF) as published in<br>"
        + "Liu, M., and Delbruck, T. (2018). <a href=\"http://bmvc2018.org/contents/papers/0280.pdf\">Adaptive Time-Slice Block-Matching Optical Flow Algorithm for Dynamic Vision Sensors</a>.<br> in BMVC 2018 (Nescatle upon Tyne)")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class PatchMatchFlow extends AbstractMotionFlow implements FrameAnnotater {

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
    private Integer[] scalesToComputeArray = null; // holds array of scales to actually compute, for debugging
    private int[] scaleResultCounts = new int[numScales]; // holds counts at each scale for min SAD results
    /**
     * The computed average possible match distance from 0 motion
     */
    protected float avgPossibleMatchDistance;
    private static final int MIN_SLICE_EVENT_COUNT_FULL_FRAME = 1000;
    private static final int MAX_SLICE_EVENT_COUNT_FULL_FRAME = 1000000;

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
    public static final int BLOCK_DIMENSION_DEFAULT = 7;
    private int blockDimension = getInt("blockDimension", BLOCK_DIMENSION_DEFAULT);    // This is the block dimension of the coarse scale.
//    private float cost = getFloat("cost", 0.001f);
    public static final float MAX_ALLOWABLE_SAD_DISTANCE_DEFAULT = .5f;
    private float maxAllowedSadDistance = getFloat("maxAllowedSadDistance", MAX_ALLOWABLE_SAD_DISTANCE_DEFAULT);
    public static final float VALID_PIXEL_OCCUPANCY_DEFAULT = 0.01f;
    private float validPixOccupancy = getFloat("validPixOccupancy", VALID_PIXEL_OCCUPANCY_DEFAULT);  // threshold for valid pixel percent for one block
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
    private boolean showBlockMatches = getBoolean("showBlockMatches", false); // Display the bitmaps
    private boolean showSlices = false; // Display the bitmaps
    private int showSlicesScale = 0; // Display the bitmaps
    private float adapativeSliceDurationProportionalErrorGain = getFloat("adapativeSliceDurationProportionalErrorGain", 0.05f); // factor by which an error signal on match distance changes slice duration
    private boolean adapativeSliceDurationUseProportionalControl = getBoolean("adapativeSliceDurationUseProportionalControl", false);
    private int processingTimeLimitMs = getInt("processingTimeLimitMs", 100); // time limit for processing packet in ms to process OF events (events still accumulate). Overrides the system EventPacket timelimiter, which cannot be used here because we still need to accumulate and render the events.
    public static final int SLICE_MAX_VALUE_DEFAULT = 15;
    private int sliceMaxValue = getInt("sliceMaxValue", SLICE_MAX_VALUE_DEFAULT);
    private boolean rectifyPolarties = getBoolean("rectifyPolarties", false);
    private int sliceDurationMinLimitUS = getInt("sliceDurationMinLimitUS", 100);
    private int sliceDurationMaxLimitUS = getInt("sliceDurationMaxLimitUS", 300000);
    private boolean outlierRejectionEnabled = getBoolean("outlierRejectionEnabled", false);
    private float outlierRejectionThresholdSigma = getFloat("outlierRejectionThresholdSigma", 2f);
    protected int outlierRejectionWindowSize = getInt("outlierRejectionWindowSize", 300);
    private MotionFlowStatistics outlierRejectionMotionFlowStatistics;

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

    private boolean enableImuTimesliceLogging = false;
    private TobiLogger imuTimesliceLogger = null;
    private volatile boolean resetOFHistogramFlag; // signals to reset the OF histogram after it is rendered

    private float cornerThr = getFloat("cornerThr", 0.2f);
    private boolean saveSliceGrayImage = false;
    private PrintWriter dvsWriter = null;

    private EngineeringFormat engFmt;
    private TextRenderer textRenderer = null;

    // These variables are only used by HW_ABMOF. 
    // HW_ABMOF send slice rotation flag so we need to indicate the real rotation timestamp
    // HW_ABMOF is only supported for davis346Zynq
    private int curretnRotatTs_HW = 0, tMinus1RotateTs_HW = 0, tMinus2RotateTs_HW = 0;
    private float deltaTsMs_HW = 0;
    private boolean HWABMOFEnabled = false;

    public enum CornerCircleSelection {
        InnerCircle, OuterCircle, OR, AND
    }
    private CornerCircleSelection cornerCircleSelection = CornerCircleSelection.valueOf(getString("cornerCircleSelection", CornerCircleSelection.OuterCircle.name())); // Tobi change to Outer which is the condition used for experimental results in paper

    protected static String DEFAULT_FILENAME = "jAER.txt";
    protected String lastFileName = getString("lastFileName", DEFAULT_FILENAME);

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
    public static final int SLICE_EVENT_COUNT_DEFAULT = 700;

    private int sliceEventCount = getInt("sliceEventCount", SLICE_EVENT_COUNT_DEFAULT);
    private boolean rewindFlg = false; // The flag to indicate the rewind event.

    private boolean displayResultHistogram = getBoolean("displayResultHistogram", true);

    public enum SliceMethod {
        ConstantDuration, ConstantEventNumber, AreaEventNumber, ConstantIntegratedFlow
    };
    private SliceMethod sliceMethod = SliceMethod.valueOf(getString("sliceMethod", SliceMethod.AreaEventNumber.toString()));
    // counting events into subsampled areas, when count exceeds the threshold in any area, the slices are rotated
    public static final int AREA_EVENT_NUMBER_SUBSAMPLING_DEFAULT = 5;
    private int areaEventNumberSubsampling = getInt("areaEventNumberSubsampling", AREA_EVENT_NUMBER_SUBSAMPLING_DEFAULT);
    private int[][] areaCounts = null;
    private int numAreas = 1;

    private boolean areaCountExceeded = false;

    // nongreedy flow evaluation
    // the entire scene is subdivided into regions, and a bitmap of these regions distributed flow computation more fairly
    // by only servicing a region when sufficient fraction of other regions have been serviced first
    private boolean nonGreedyFlowComputingEnabled = getBoolean("nonGreedyFlowComputingEnabled", false);
    private boolean[][] nonGreedyRegions = null;
    private int nonGreedyRegionsNumberOfRegions, nonGreedyRegionsCount;
    /**
     * This fraction of the regions must be serviced for computing flow before
     * we reset the nonGreedyRegions map
     */
    private float nonGreedyFractionToBeServiced = getFloat("nonGreedyFractionToBeServiced", .5f);

    // Print scale count's statics
    private boolean printScaleCntStatEnabled = getBoolean("printScaleCntStatEnabled", false);

    // timers and flags for showing filter properties temporarily
    private final int SHOW_STUFF_DURATION_MS = 4000;
    private volatile TimerTask stopShowingStuffTask = null;
    private boolean showBlockSizeAndSearchAreaTemporarily = false;
    private volatile boolean showAreaCountAreasTemporarily = false;

    private int eventCounter = 0;
    private int sliceLastTs = Integer.MAX_VALUE;

    private JFrame blockMatchingFrame[] = new JFrame[numScales];
    private JFrame blockMatchingFrameTarget[] = new JFrame[numScales];
    private ImageDisplay blockMatchingImageDisplay[] = new ImageDisplay[numScales];
    private ImageDisplay blockMatchingImageDisplayTarget[] = new ImageDisplay[numScales]; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private Legend blockMatchingDisplayLegend[] = new Legend[numScales];
    private Legend blockMatchingDisplayLegendTarget[] = new Legend[numScales];
    private static final String LEGEND_G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH = "G: search area\nR: ref block area\nB: best match";

    private JFrame sliceBitMapFrame = null;
    private ImageDisplay sliceBitmapImageDisplay; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private Legend sliceBitmapImageDisplayLegend;
    private static final String LEGEND_SLICES = "R: Slice t-d\nG: Slice t-2d";

    private JFrame timeStampBlockFrame = null;
    private ImageDisplay timeStampBlockImageDisplay; // makde a new ImageDisplay GLCanvas with default OpenGL capabilities
    private Legend timeStampBlockImageDisplayLegend;
    private static final String TIME_STAMP_BLOCK_LEGEND_SLICES = "R: Inner Circle\nB: Outer Circle\nG: Current event";
    private static final int circle1[][] = {{0, 1}, {1, 1}, {1, 0}, {1, -1},
    {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};
    private static final int circle2[][] = {{0, 2}, {1, 2}, {2, 1}, {2, 0},
    {2, -1}, {1, -2}, {0, -2}, {-1, -2},
    {-2, -1}, {-2, 0}, {-2, 1}, {-1, 2}};
    private static final int circle3[][] = {{0, 3}, {1, 3}, {2, 2}, {3, 1},
    {3, 0}, {3, -1}, {2, -2}, {1, -3},
    {0, -3}, {-1, -3}, {-2, -2}, {-3, -1},
    {-3, 0}, {-3, 1}, {-2, 2}, {-1, 3}};
    private static final int circle4[][] = {{0, 4}, {1, 4}, {2, 3}, {3, 2},
    {4, 1}, {4, 0}, {4, -1}, {3, -2},
    {2, -3}, {1, -4}, {0, -4}, {-1, -4},
    {-2, -3}, {-3, -2}, {-4, -1}, {-4, 0},
    {-4, 1}, {-3, 2}, {-2, 3}, {-1, 4}};

    int innerCircle[][] = circle1;
    int innerCircleSize = innerCircle.length;
    int xInnerOffset[] = new int[innerCircleSize];
    int yInnerOffset[] = new int[innerCircleSize];
    int innerTsValue[] = new int[innerCircleSize];

    int outerCircle[][] = circle2;
    int outerCircleSize = outerCircle.length;
    int yOuterOffset[] = new int[outerCircleSize];
    int xOuterOffset[] = new int[outerCircleSize];
    int outerTsValue[] = new int[outerCircleSize];

    private HWCornerPointRenderer keypointFilter = null;
    /**
     * A PropertyChangeEvent with this value is fired when the slices has been
     * rotated. The oldValue is t-2d slice. The newValue is the t-d slice.
     */
    public static final String EVENT_NEW_SLICES = "eventNewSlices";

    TobiLogger sadValueLogger = new TobiLogger("sadvalues", "sadvalue,scale"); // TODO debug

    private boolean calcOFonCornersEnabled = getBoolean("calcOFonCornersEnabled", true);
    protected boolean useEFASTnotSFAST = getBoolean("useEFASTnotSFAST", false);

    private final ApsFrameExtractor apsFrameExtractor;

    // Corner events array; only used for rendering.
    private boolean showCorners = getBoolean("showCorners", true);
    protected int cornerSize = getInt("cornerSize", 2);
    private ArrayList<BasicEvent> cornerEvents = new ArrayList(1000);

    public PatchMatchFlow(AEChip chip) {
        super(chip);
        this.engFmt = new EngineeringFormat();

        getEnclosedFilterChain().clear();
//        getEnclosedFilterChain().add(new SpatioTemporalCorrelationFilter(chip));
        keypointFilter = new HWCornerPointRenderer(chip);
        apsFrameExtractor = new ApsFrameExtractor(chip);
        apsFrameExtractor.setShowAPSFrameDisplay(false);
        getEnclosedFilterChain().add(apsFrameExtractor);
//        getEnclosedFilterChain().add(keypointFilter); // use for EFAST

        setSliceDurationUs(getSliceDurationUs());   // 40ms is good for the start of the slice duration adatative since 4ms is too fast and 500ms is too slow.
        setDefaultScalesToCompute();

//        // Save the result to the file
//        Format formatter = new SimpleDateFormat("YYYY-MM-dd_hh-mm-ss");
//        // Instantiate a Date object
//        Date date = new Date();
        // Log file for the OF distribution's statistics
//        outputFilename = "PMF_HistStdDev" + formatter.format(date) + ".txt";
//        String eventSqeMatching = "Event squence matching";
//        String preProcess = "Denoise";
        try {
            patchCompareMethod = PatchCompareMethod.valueOf(getString("patchCompareMethod", PatchCompareMethod.SAD.toString()));
        } catch (IllegalArgumentException e) {
            patchCompareMethod = PatchCompareMethod.SAD;
        }

        String hwTip = "0b: Hardware EDFLOW";
        setPropertyTooltip(hwTip, "HWABMOFEnabled", "Select to show output of hardware EDFLOW camera");

        String cornerTip = "0c: Corners/Keypoints";
        setPropertyTooltip(cornerTip, "showCorners", "Select to show corners (as red overlay)");
        setPropertyTooltip(cornerTip, "cornerThr", "Threshold difference for SFAST detection as fraction of maximum event count value; increase for fewer corners");
        setPropertyTooltip(cornerTip, "calcOFonCornersEnabled", "Calculate OF based on corners or not");
        setPropertyTooltip(cornerTip, "cornerCircleSelection", "Determines SFAST circles used for detecting the corner/keypoint");
        setPropertyTooltip(cornerTip, "useEFASTnotSFAST", "Use EFAST corner detector, not SFAST which is default");
        setPropertyTooltip(cornerTip, "cornerSize", "Dimension WxH of the drawn detector corners in chip pixels");

        String patchTT = "0a: Block matching";
        // move ppsScale to main top GUI since we use it a lot
        setPropertyTooltip(patchTT, "ppsScale", "<html>When <i>ppsScaleDisplayRelativeOFLength=false</i>, then this is <br>scale of screen pixels per px/s flow to draw local motion vectors; <br>global vectors are scaled up by an additional factor of " + GLOBAL_MOTION_DRAWING_SCALE + "<p>"
                + "When <i>ppsScaleDisplayRelativeOFLength=true</i>, then local motion vectors are scaled by average speed of flow");
        setPropertyTooltip(patchTT, "blockDimension", "Linear dimenion of patches to match on coarse scale, in pixels. Median and fine scale block sizes are scaled up approx by powers of 2.");
        setPropertyTooltip(patchTT, "searchDistance", "Search distance for matching patches, in pixels");
        setPropertyTooltip(patchTT, "patchCompareMethod", "method to compare two patches; SAD=sum of absolute differences, HammingDistance is same as SAD for binary bitmaps");
        setPropertyTooltip(patchTT, "searchMethod", "method to search patches");
        setPropertyTooltip(patchTT, "sliceDurationUs", "duration of bitmaps in us, also called sample interval, when ConstantDuration method is used");
        setPropertyTooltip(patchTT, "sliceEventCount", "number of events collected to fill a slice, when ConstantEventNumber method is used");
        setPropertyTooltip(patchTT, "sliceMethod", "<html>Method for determining time slice duration for block matching<ul>"
                + "<li>ConstantDuration: slices are fixed time duration"
                + "<li>ConstantEventNumber: slices are fixed event number"
                + "<li>AreaEventNumber: slices are fixed event number in any subsampled area defined by areaEventNumberSubsampling"
                + "<li>ConstantIntegratedFlow: slices are rotated when average speeds times delta time exceeds half the search distance");
        setPropertyTooltip(patchTT, "areaEventNumberSubsampling", "<html>how to subsample total area to count events per unit subsampling blocks for AreaEventNumber method. <p>For example, if areaEventNumberSubsampling=5, <br> then events falling into 32x32 blocks of pixels are counted <br>to determine when they exceed sliceEventCount to make new slice");
        setPropertyTooltip(patchTT, "adapativeSliceDurationProportionalErrorGain", "gain for proporportional change of duration or slice event number. typically 0.05f for bang-bang, and 0.5f for proportional control");
        setPropertyTooltip(patchTT, "adapativeSliceDurationUseProportionalControl", "If true, then use proportional error control. If false, use bang-bang control with sign of match distance error");
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
        setPropertyTooltip(patchTT, "defaults", "Sets reasonable defaults");
        setPropertyTooltip(patchTT, "enableImuTimesliceLogging", "Logs IMU and rate gyro");
        setPropertyTooltip(patchTT, "startRecordingForEDFLOW", "Start to record events and its OF result to a file which can be converted to a .bin file for EDFLOW.");
        setPropertyTooltip(patchTT, "stopRecordingForEDFLOW", "Stop to record events and its OF result to a file which can be converted to a .bin file for EDFLOW.");
        setPropertyTooltip(patchTT, "sliceDurationMinLimitUS", "The minimum value (us) of slice duration.");
        setPropertyTooltip(patchTT, "sliceDurationMaxLimitUS", "The maximum value (us) of slice duration.");
        setPropertyTooltip(patchTT, "outlierRejectionEnabled", "Enable outlier flow vector rejection");
        setPropertyTooltip(patchTT, "outlierRejectionThresholdSigma", "Flow vectors that are larger than this many sigma from global flow variation are discarded");
        setPropertyTooltip(patchTT, "outlierRejectionWindowSize", "Window in events for measurement of average flow for outlier rejection");

        String metricConfid = "0ab: Density checks";
        setPropertyTooltip(metricConfid, "maxAllowedSadDistance", "<html>SAD distance threshold for rejecting unresonable block matching result; <br> events with SAD distance larger than this value are rejected. <p>Lower value means it is harder to accept the event. <p> Distance is sum of absolute differences for this best match normalized by number of pixels in reference area.");
        setPropertyTooltip(metricConfid, "validPixOccupancy", "<html>Threshold for valid pixel percent for each block; Range from 0 to 1. <p>If either matching block is less occupied than this fraction, no motion vector will be calculated.");
        setPropertyTooltip(metricConfid, "weightDistance", "<html>The confidence value consists of the distance and the dispersion; <br>weightDistance sets the weighting of the distance value compared with the dispersion value; Range from 0 to 1. <p>To count only e.g. hamming distance, set weighting to 1. <p> To count only dispersion, set to 0.");

        String patchDispTT = "0b: Block matching display";
        setPropertyTooltip(patchDispTT, "showSlices", "enables displaying the entire bitmaps slices (the current slices)");
        setPropertyTooltip(patchDispTT, "showSlicesScale", "sets which scale of the slices to display");
        setPropertyTooltip(patchDispTT, "showBlockMatches", "enables displaying the individual block matches");
        setPropertyTooltip(patchDispTT, "displayOutputVectors", "display the output motion vectors or not");
        setPropertyTooltip(patchDispTT, "displayResultHistogram", "display the output motion vectors histogram to show disribution of results for each packet. Only implemented for HammingDistance");
        setPropertyTooltip(patchDispTT, "printScaleCntStatEnabled", "enables printing the statics of scale counts");

        getSupport().addPropertyChangeListener(AEViewer.EVENT_TIMESTAMPS_RESET, this);
        getSupport().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, this);
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_NON_MONOTONIC_TIMESTAMP, this);
        computeAveragePossibleMatchDistance();

        numInputTypes = 2;   // allocate timestamp map
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
        setupFilter(in);
        checkArrays();
        if (processingTimeLimitMs > 0) {
            timeLimiter.setTimeLimitMs(processingTimeLimitMs);
            timeLimiter.restart();
        } else {
            timeLimiter.setEnabled(false);
        }

        int minDistScale = 0;
        // following awkward block needed to deal with DVS/DAVIS and IMU/APS events
        // block STARTS
        Iterator i = null;
        if (in instanceof ApsDvsEventPacket) {
            i = ((ApsDvsEventPacket) in).fullIterator();
        } else {
            i = ((EventPacket) in).inputIterator();
        }

        cornerEvents.clear();
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
            if (isInvalidTimestamp()) {
                continue;
            }

            countIn++;

            // compute flow
            SADResult result = null;

            if (HWABMOFEnabled) // Only use it when there is hardware supported. Hardware is davis346Zynq
            {
                SADResult sliceResult = new SADResult();

                int data = ein.address & 0x7ff;

                // The OF result from the hardware has following procotol:
                // If the data is 0x7ff, it indicates that this result is an invalid result
                // if the data is 0x7fe, then it means slice rotated on this event,
                // in this case, only rotation information is included.
                // Other cases are valid OF data.
                // The valid OF data is represented in a compressed data format.
                // It is calculated by OF_x * (2 * maxSearchDistanceRadius + 1) + OF_y.
                // Therefore, simple decompress is required.
                if ((data & 0x7ff) == 0x7ff) {
                    continue;
                } else if ((data & 0x7ff) == 0x7fe) {
                    tMinus2RotateTs_HW = tMinus1RotateTs_HW;
                    tMinus1RotateTs_HW = curretnRotatTs_HW;
                    curretnRotatTs_HW = ts;

                    deltaTsMs_HW = (float) (tMinus1RotateTs_HW - tMinus2RotateTs_HW) / (float) 1000.0;
                    continue;
                } else {
                    final int searchDistanceHW = 3;     // hardcoded on hardware.             
                    final int maxSearchDistanceRadius = (4 + 2 + 1) * searchDistanceHW;
                    int OF_x = (data / (2 * maxSearchDistanceRadius + 1)) - maxSearchDistanceRadius;
                    int OF_y = (data % (2 * maxSearchDistanceRadius + 1)) - maxSearchDistanceRadius;

                    sliceResult.dx = -OF_x;
                    sliceResult.dy = OF_y;
                    sliceResult.vx = (float) (1e3 * sliceResult.dx / deltaTsMs_HW);
                    sliceResult.vy = (float) (1e3 * sliceResult.dy / deltaTsMs_HW);
                    result = sliceResult;
                    vx = result.vx;
                    vy = result.vy;
                    v = (float) Math.sqrt((vx * vx) + (vy * vy));

                    cornerEvents.add(e);
                }
            } else {
                float[] sadVals = new float[numScales]; // TODO debug
                int[] dxInitVals = new int[numScales];
                int[] dyInitVals = new int[numScales];

                int rotateFlg = 0;
                switch (patchCompareMethod) {
                    case SAD:
                        boolean rotated = maybeRotateSlices();
                        if (rotated) {
                            rotateFlg = 1;
                            adaptSliceDuration();
                            setResetOFHistogramFlag();
                            resetOFHistogram();
                            nCountPerSlicePacket = 0;      // Reset counter for next slice packet.
                        }
                        nCountPerSlicePacket++;
                        //                    if (ein.x >= subSizeX || ein.y > subSizeY) {
                        //                        log.warning("event out of range");
                        //                        continue;
                        //                    }

                        if (!accumulateEvent(ein)) { // maybe skip events here
                            if (dvsWriter != null) {
                                dvsWriter.println(String.format("%d %d %d %d %d %d %d %d %d", ein.timestamp, ein.x, ein.y, ein.polarity == PolarityEvent.Polarity.Off ? 0 : 1, 0x7, 0x7, 0, rotateFlg, 0));
                            }
                            break;
                        }

                        SADResult sliceResult = new SADResult();
                        minDistScale = 0;
                        boolean OFRetValidFlag = true;

                        // Sorts scalesToComputeArray[] in descending order
                        Arrays.sort(scalesToComputeArray, Collections.reverseOrder());

                        minDistScale = 0;
                        for (int scale : scalesToComputeArray) {
                            if (scale >= numScales) {
                                //                            log.warning("scale " + scale + " is out of range of " + numScales + "; fix scalesToCompute for example by clearing it");
                                //                            break;
                            }
                            int dx_init = ((result != null) && !isNotSufficientlyAccurate(sliceResult)) ? (sliceResult.dx >> scale) : 0;
                            int dy_init = ((result != null) && !isNotSufficientlyAccurate(sliceResult)) ? (sliceResult.dy >> scale) : 0;
                            //                        dx_init = 0;
                            //                        dy_init = 0;                            

                            // The reason why we inverse dx_init, dy_init i is the offset is pointing from previous slice to current slice.
                            // The dx_init, dy_init are from the corse scale's result, and it is used as the finer scale's initial guess.
                            sliceResult = minSADDistance(ein.x, ein.y, -dx_init, -dy_init, slices[sliceIndex(1)], slices[sliceIndex(2)], scale); // from ref slice to past slice k+1, using scale 0,1,....
                            //                        sliceSummedSADValues[sliceIndex(scale + 2)] += sliceResult.sadValue; // accumulate SAD for this past slice
                            //                        sliceSummedSADCounts[sliceIndex(scale + 2)]++; // accumulate SAD count for this past slice
                            // sliceSummedSADValues should end up filling 2 values for 4 slices 

                            sadVals[scale] = sliceResult.sadValue; // TODO debug 
                            dxInitVals[scale] = dx_init;
                            dyInitVals[scale] = dy_init;

                            if (sliceResult.sadValue >= this.maxAllowedSadDistance) {
                                OFRetValidFlag = false;
                                break;
                            } else {
                                if ((result == null) || (sliceResult.sadValue < result.sadValue)) {
                                    result = sliceResult; // result holds the overall min sad result
                                    minDistScale = scale;
                                }
                            }
                            //                        result=sliceResult; // TODO tobi: override the absolute minimum to always use the finest scale result, which has been guided by coarser scales
                        }
                        result = sliceResult;
                        float dt = (sliceDeltaTimeUs(2) * 1e-6f);
                        if (result != null) {
                            result.vx = result.dx / dt; // hack, convert to pix/second
                            result.vy = result.dy / dt; // TODO clean up, make time for each slice, since could be different when const num events
                        }
                        if (dvsWriter != null) {
                            dvsWriter.println(String.format("%d %d %d %d %d %d %d %d %d", ein.timestamp, ein.x, ein.y, ein.polarity == PolarityEvent.Polarity.Off ? 0 : 1, result.dx, result.dy, OFRetValidFlag ? 1 : 0, rotateFlg, 1));
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
                if (result == null || result.sadValue == Float.MAX_VALUE) {
                    continue; // maybe some property change caused this
                }
                // reject values that are unreasonable
                if (isNotSufficientlyAccurate(result)) {
                    continue;
                }

                scaleResultCounts[minDistScale]++;
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

                if (showBlockMatches) {
                    // TODO danger, drawing outside AWT thread
                    final SADResult thisResult = result;
                    final PolarityEvent thisEvent = ein;
                    final byte[][][][] thisSlices = slices;
                    //                SwingUtilities.invokeLater(new Runnable() {
                    //                    @Override
                    //                    public void run() {
                    drawMatching(thisResult, thisEvent, thisSlices, sadVals, dxInitVals, dyInitVals); // ein.x >> result.scale, ein.y >> result.scale, (int) result.dx >> result.scale, (int) result.dy >> result.scale, slices[sliceIndex(1)][result.scale], slices[sliceIndex(2)][result.scale], result.scale);
                    drawTimeStampBlock(thisEvent);
                    //                    }
                    //                });
                }
            }

            if (isOutlierFlowVector(result)) {
                countOutliers++;
                continue;
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
            if (resultHistogram != null) {
                resultHistogram[result.dx + computeMaxSearchDistance()][result.dy + computeMaxSearchDistance()]++;
                resultHistogramCount++;
            }
            lastGoodSadResult.set(result);

        }

        motionFlowStatistics.updatePacket(countIn, countOut, ts);
        outlierRejectionMotionFlowStatistics.updatePacket(countIn, countOut, ts);
//        float fracOutliers = (float) countOutliers / countIn;
//        System.out.println(String.format("Fraction of outliers: %.1f%%", 100 * fracOutliers));
        adaptEventSkipping();
        if (rewindFlg) {
            rewindFlg = false;

            for (byte[][][] b : slices) {
                clearSlice(b);
            }

            currentSliceIdx = 0;  // start by filling slice 0
            currentSlice = slices[currentSliceIdx];

            sliceLastTs = Integer.MAX_VALUE;

        }

        return isDisplayRawInput() ? in : dirPacket;
    }

    public void doDefaults() {
        setSearchMethod(SearchMethod.DiamondSearch);
        setBlockDimension(BLOCK_DIMENSION_DEFAULT);
        setNumScales(3);
        setSearchDistance(3);

        setAdaptiveEventSkipping(false);
        setSkipProcessingEventsCount(0);
        setProcessingTimeLimitMs(5000);
        setDisplayVectorsEnabled(true);
        setPpsScaleDisplayRelativeOFLength(false);
        setDisplayGlobalMotion(true);
        setRectifyPolarties(true); // rectify to better handle cases of steadicam where pan/tilt flips event polarities
        setPpsScale(.1f);
        setSliceMaxValue(SLICE_MAX_VALUE_DEFAULT);

        setValidPixOccupancy(VALID_PIXEL_OCCUPANCY_DEFAULT); // at least this fraction of pixels from each block must both have nonzero values
        setMaxAllowedSadDistance(MAX_ALLOWABLE_SAD_DISTANCE_DEFAULT);

        setSliceMethod(SliceMethod.AreaEventNumber);
        setAdaptiveSliceDuration(true);
        setSliceEventCount(SLICE_EVENT_COUNT_DEFAULT);

        // compute nearest power of two over block dimension
//        int ss = (int) (Math.log(blockDimension - 1) / Math.log(2)) + 1;
        setAreaEventNumberSubsampling(AREA_EVENT_NUMBER_SUBSAMPLING_DEFAULT); // set to paper value

//        // set event count so that count=block area * sliceMaxValue/4; 
//        // i.e. set count to roll over when slice pixels from most subsampled scale are half full if they are half stimulated
//        final int eventCount = (((blockDimension * blockDimension) * sliceMaxValue) / 2) >> (numScales - 1);
//        setSliceEventCount(eventCount);
        setSliceDurationMinLimitUS(1000);
        setSliceDurationMaxLimitUS(300000);
        setSliceDurationUs(50000); // set a bit smaller max duration in us to avoid instability where count gets too high with sparse input

        setShowCorners(true);
        setCalcOFonCornersEnabled(true);   // Enable corner detector
        setCornerCircleSelection(CornerCircleSelection.OuterCircle);
        setCornerThr(0.2f);

    }

    private void adaptSliceDuration() {
        // measure last hist to get control signal on slice duration
        // measures avg match distance.  weights the average so that long distances with more pixels in hist are not overcounted, simply
        // by having more pixels.
        if (rewindFlg) {
            return; // don't adapt during rewind or delay before playing again
        }
        float radiusSum = 0;
        int countSum = 0;

//            int maxRadius = (int) Math.ceil(Math.sqrt(2 * searchDistance * searchDistance));
//            int countSum = 0;
        final int totSD = computeMaxSearchDistance();
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
            final float err = avgMatchDistance / (avgPossibleMatchDistance); // use target that is smaller than average possible to bound excursions to large slices better
//            final float err = avgPossibleMatchDistance / 2 - avgMatchDistance; // use target that is smaller than average possible to bound excursions to large slices better
//                final float err = ((searchDistance << (numScales - 1)) / 2) - avgMatchDistance;
//                final float lastErr = searchDistance / 2 - lastHistStdDev;
//                final double err = histMean - 1/ (rstHist1D.length * rstHist1D.length);
            float errSign = Math.signum(err - 1);
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
                    if (adapativeSliceDurationUseProportionalControl) { // proportional
                        setSliceDurationUs(Math.round((1 / (1 + (err - 1) * adapativeSliceDurationProportionalErrorGain)) * sliceDurationUs));
                    } else { // bang bang
                        int durChange = (int) (-errSign * adapativeSliceDurationProportionalErrorGain * sliceDurationUs);
                        setSliceDurationUs(sliceDurationUs + durChange);
                    }
                    break;
                case ConstantEventNumber:
                case AreaEventNumber:
                    if (adapativeSliceDurationUseProportionalControl) { // proportional
                        setSliceEventCount(Math.round((1 / (1 + (err - 1) * adapativeSliceDurationProportionalErrorGain)) * sliceEventCount));
                    } else {
                        if (errSign < 0) { // match distance too short, increase duration
                            // match too short, increase count
                            setSliceEventCount(Math.round(sliceEventCount * (1 + adapativeSliceDurationProportionalErrorGain)));
                        } else if (errSign > 0) { // match too long, decrease duration
                            setSliceEventCount(Math.round(sliceEventCount * (1 - adapativeSliceDurationProportionalErrorGain)));
                        }
                    }
                    break;
                case ConstantIntegratedFlow:
                    setSliceEventCount(eventCounter);
            }
            if (adaptiveSliceDurationLogger != null && adaptiveSliceDurationLogger.isEnabled()) {
                if (!isDisplayGlobalMotion()) {
                    setDisplayGlobalMotion(true);
                }
                adaptiveSliceDurationLogger.log(String.format("%f\t%d\t%d\t%f\t%f\t%f\t%d\t%d", e.timestamp * 1e-6f,
                        adaptiveSliceDurationPacketCount++, nCountPerSlicePacket, avgMatchDistance, err,
                        motionFlowStatistics.getGlobalMotion().getGlobalSpeed().getMean(), sliceDeltaT, sliceEventCount));
            }
        }

    }

    private void setResetOFHistogramFlag() {
        resetOFHistogramFlag = true;
    }

    private void clearResetOFHistogramFlag() {
        resetOFHistogramFlag = false;
    }

    private void resetOFHistogram() {
        if (!resetOFHistogramFlag || resultHistogram == null) {
            return;
        }
        for (int[] h : resultHistogram) {
            Arrays.fill(h, 0);
        }
        resultHistogramCount = 0;
//        Arrays.fill(resultAngleHistogram, 0);
//        resultAngleHistogramCount = 0;
//        resultAngleHistogramMax = Integer.MIN_VALUE;

        // Print statics of scale count, only for debuuging.
        if (printScaleCntStatEnabled) {
            float sumScaleCounts = 0;
            for (int scale : scalesToComputeArray) {
                sumScaleCounts += scaleResultCounts[scale];
            }

            for (int scale : scalesToComputeArray) {
                System.out.println("Scale " + scale + " count percentage is: " + scaleResultCounts[scale] / sumScaleCounts);
            }
        }

        Arrays.fill(scaleResultCounts, 0);
        clearResetOFHistogramFlag();
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        // first draw corners
        if (showCorners) {
            gl.glLineWidth(getMotionVectorLineWidthPixels());
            gl.glColor4f(.5f, 0, 0, 0.5f);
            for (BasicEvent e : cornerEvents) {
                gl.glPushMatrix();
                DrawGL.drawCross(gl, e.x, e.y, getCornerSize(), 0);
                gl.glPopMatrix();
            }
        }
        super.annotate(drawable);
        try {
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glBlendEquation(GL.GL_FUNC_ADD);
        } catch (GLException e) {
            e.printStackTrace();
        }
        // then on top, draw the motion vectors

        if (displayResultHistogram && (resultHistogram != null)) {
            // draw histogram as shaded in 2d hist above color wheel
            // normalize hist
            int rhDim = resultHistogram.length; // this.computeMaxSearchDistance();
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
                final int tsd = computeMaxSearchDistance();
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
                    DrawGL.drawCircle(gl, tsd + .5f, tsd + .5f, avgPossibleMatchDistance, 16); // draw circle at target match distance
                    gl.glPopMatrix();
                }
                // a bunch of cryptic crap to draw a string the same width as the histogram...
                gl.glPopMatrix();
                gl.glPopMatrix(); // back to original chip coordinates
                gl.glPushMatrix();
                textRenderer.begin3DRendering();
                String s = String.format("dt=%.1f ms", 1e-3f * sliceDeltaT);
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
                String s2 = String.format("Skip: %d", skipProcessingEventsCount);
                textRenderer.draw3D(s2, 0, (float) (rt.getHeight()) * sc, 0, sc);
                String s3 = String.format("Slice events: %d", sliceEventCount);
                textRenderer.draw3D(s3, 0, 2 * (float) (rt.getHeight()) * sc, 0, sc);
                StringBuilder sb = new StringBuilder("Scale counts: ");
                for (int c : scaleResultCounts) {
                    sb.append(String.format("%d ", c));
                }
                textRenderer.draw3D(sb.toString(), 0, (float) (3 * rt.getHeight()) * sc, 0, sc);
                if (timeLimiter.isTimedOut()) {
                    String s4 = String.format("Timed out: skipped %,d events", nSkipped);
                    textRenderer.draw3D(s4, 0, 4 * (float) (rt.getHeight()) * sc, 0, sc);
                }
                if (outlierRejectionEnabled) {
                    String s5 = String.format("Outliers: %%%.0f", 100 * (float) countOutliers / countIn);
                    textRenderer.draw3D(s5, 0, 5 * (float) (rt.getHeight()) * sc, 0, sc);
                }
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
    public void initFilter() {
        super.initFilter();
        checkForEASTCornerDetectorEnclosedFilter();
        outlierRejectionMotionFlowStatistics = new MotionFlowStatistics(this.getClass().getSimpleName(), subSizeX, subSizeY, outlierRejectionWindowSize);
        outlierRejectionMotionFlowStatistics.setMeasureGlobalMotion(true);
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

//        currentSliceIdx = 0;  // start by filling slice 0
//        currentSlice = slices[currentSliceIdx];
//
//        sliceLastTs = Integer.MAX_VALUE;
        rewindFlg = true;
        if (adaptiveEventSkippingUpdateCounterLPFilter != null) {
            adaptiveEventSkippingUpdateCounterLPFilter.reset();
        }
        clearAreaCounts();
        clearNonGreedyRegions();
        setSliceEventCount(getInt("sliceEventCount", SLICE_EVENT_COUNT_DEFAULT));
    }

    private LowpassFilter speedFilter = new LowpassFilter();

    /**
     * uses the current event to maybe rotate the slices
     *
     * @return true if slices were rotated
     */
    private boolean maybeRotateSlices() {
        int dt = ts - sliceLastTs;
        if (dt < 0) { // handle timestamp wrapping
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
                // If dt is too small, we should rotate it later until it has enough accumulation time.
                if (!areaCountExceeded && dt < getSliceDurationMaxLimitUS() || (dt < getSliceDurationMinLimitUS())) {
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
                break;
        }

        rotateSlices();
        /* Slices have been rotated */
        getSupport().firePropertyChange(PatchMatchFlow.EVENT_NEW_SLICES, slices[sliceIndex(1)], slices[sliceIndex(2)]);
        return true;

    }

    void saveAPSImage() {
        byte[] grayImageBuffer = new byte[sizex * sizey];
        for (int y = 0; y < sizey; y++) {
            for (int x = 0; x < sizex; x++) {
                final int idx = x + (sizey - y - 1) * chip.getSizeX();
                float bufferValue = apsFrameExtractor.getRawFrame()[idx];
                grayImageBuffer[x + y * chip.getSizeX()] = (byte) (int) (bufferValue * 0.5f);
            }
        }
        final BufferedImage theImage = new BufferedImage(chip.getSizeX(), chip.getSizeY(), BufferedImage.TYPE_BYTE_GRAY);
        theImage.getRaster().setDataElements(0, 0, sizex, sizey, grayImageBuffer);

        final Date d = new Date();
        final String PNG = "png";
        final String fn = "ApsFrame-" + sliceEndTimeUs[sliceIndex(1)] + "." + PNG;
        // if user is playing a file, use folder that file lives in
        String userDir = Paths.get(".").toAbsolutePath().normalize().toString();

        File APSFrmaeDir = new File(userDir + File.separator + "APSFrames" + File.separator + getChip().getAeInputStream().getFile().getName());
        if (!APSFrmaeDir.exists()) {
            APSFrmaeDir.mkdirs();
        }
        File outputfile = new File(APSFrmaeDir + File.separator + fn);

        try {
            ImageIO.write(theImage, "png", outputfile);
        } catch (IOException ex) {
            Logger.getLogger(PatchMatchFlow.class.getName()).log(Level.SEVERE, null, ex);
        }
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
        saveSliceGrayImage = true;
//        if(e.timestamp == 213686212)
//        {
//            saveAPSImage();                    
//            saveSliceGrayImage = true;
//        }        
        if (isShowSlices() && !rewindFlg) {
            // TODO danger, drawing outside AWT thread
            final byte[][][][] thisSlices = slices;
//            log.info("making runnable to draw slices in EDT");
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    // will grab this instance. if called from AWT via e.g. slider, then can deadlock if we also invokeAndWait to draw something in ViewLoop
                    drawSlices(thisSlices);
                }
            });
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
    protected int sliceDeltaTimeUs(int pointer) {
//        System.out.println("dt(" + pointer + ")=" + (sliceStartTimeUs[sliceIndex(1)] - sliceStartTimeUs[sliceIndex(pointer)]));
        int idxOlder = sliceIndex(pointer), idxYounger = sliceIndex(1);
        int tOlder = (sliceStartTimeUs[idxOlder] + sliceEndTimeUs[idxOlder]) / 2;
        int tYounger = (sliceStartTimeUs[idxYounger] + sliceEndTimeUs[idxYounger]) / 2;
        int dt = tYounger - tOlder;
        return dt;
    }

    private int nSkipped = 0, nProcessed = 0, nCountPerSlicePacket = 0;

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
//            if (xx >= currentSlice[legendString].length || yy > currentSlice[legendString][xx].length) {
//                log.warning("event out of range");
//                return false;
//            }
            int cv = currentSlice[s][xx][yy];
            cv += rectifyPolarties ? 1 : (e.polarity == PolarityEvent.Polarity.On ? 1 : -1);
//            cv = cv << (numScales - 1 - legendString);
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
//                float legendString=(float)Math.sqrt((float)sum2/count-m*m);
//                sb.append(String.format("mean=%.1f, std=%.1f",m,legendString));
//                log.info("area count stats "+sb.toString());
            }
        }
        // detect if keypoint here
        boolean isEASTCorner = (useEFASTnotSFAST && ((e.getAddress() & 1) == 1));  // supported only HWCornerPointRender or HW EFAST is used
        boolean isBFASTCorner = PatchFastDetectorisFeature(e);
        boolean isCorner = (useEFASTnotSFAST && isEASTCorner) || (!useEFASTnotSFAST && isBFASTCorner);
        if (calcOFonCornersEnabled && !isCorner) {
            return false;
        } else {
            cornerEvents.add(e);
        }

        // now finally compute flow
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
     * @param dx_init initial offset
     * @param dy_init
     * @param prevSlice the slice over which we search for best match
     * @param curSlice the slice from which we get the reference block
     * @param subSampleBy the scale to compute this SAD on, 0 for full
     * resolution, 1 for 2x2 subsampled block bitmap, etc
     * @return SADResult that provides the shift and SAD value
     */
//    private SADResult minHammingDistance(int x, int y, BitSet prevSlice, BitSet curSlice) {
    private SADResult minSADDistance(int x, int y, int dx_init, int dy_init, byte[][][] curSlice, byte[][][] prevSlice, int subSampleBy) {
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

        final int xsub = (x >> subSampleBy) + dx_init;
        final int ysub = (y >> subSampleBy) + dy_init;
        final int r = ((blockDimension) / 2) << (numScales - 1 - subSampleBy);
        int w = subSizeX >> subSampleBy, h = subSizeY >> subSampleBy;

        // Make sure both ref block and past slice block are in bounds on all sides or there'll be arrayIndexOutOfBoundary exception.
        // Also we don't want to match ref block only on inner sides or there will be a bias towards motion towards middle
        if (xsub - r - searchDistance < 0 || xsub + r + searchDistance >= 1 * w
                || ysub - r - searchDistance < 0 || ysub + r + searchDistance >= 1 * h) {
            result.sadValue = Float.MAX_VALUE; // return very large distance for this match so it is not selected
            result.scale = subSampleBy;
            return result;
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
                            sumArray[xidx][yidx] = sadDistance(x, y, dx_init + dx, dy_init + dy, curSlice, prevSlice, subSampleBy);
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
                        sumArray[xidx][yidx] = sadDistance(x, y, dx_init + dx, dy_init + dy, curSlice, prevSlice, subSampleBy);
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
                        result.dx = -dx - dx_init;  // minus is because result points to the past slice and motion is in the other direction
                        result.dy = -dy - dy_init;
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
                if ((e.timestamp) == 81160149) {
                    System.out.printf("Scale %d with Refblock is: \n", subSampleBy);
                    int xscale = (x >> subSampleBy);
                    int yscale = (y >> subSampleBy);
                    for (int xx = xscale - r; xx <= (xscale + r); xx++) {
                        for (int yy = yscale - r; yy <= (yscale + r); yy++) {
                            System.out.printf("%d\t", curSlice[subSampleBy][xx][yy]);
                        }
                        System.out.printf("\n");
                    }
                    System.out.printf("\n");

                    System.out.printf("Tagblock is: \n");
                    for (int xx = xscale + dx_init - r - searchDistance; xx <= (xscale + dx_init + r + searchDistance); xx++) {
                        for (int yy = yscale + dy_init - r - searchDistance; yy <= (yscale + dy_init + r + searchDistance); yy++) {
                            System.out.printf("%d\t", prevSlice[subSampleBy][xx][yy]);
                        }
                        System.out.printf("\n");
                    }
                }

                for (dx = -searchDistance; dx <= searchDistance; dx++) {
                    for (dy = -searchDistance; dy <= searchDistance; dy++) {
                        sum = sadDistance(x, y, dx_init + dx, dy_init + dy, curSlice, prevSlice, subSampleBy);
                        sumArray[dx + searchDistance][dy + searchDistance] = sum;
                        if (sum < minSum) {
                            minSum = sum;
                            result.dx = -dx - dx_init; // minus is because result points to the past slice and motion is in the other direction
                            result.dy = -dy - dy_init;
                            result.sadValue = minSum;
                        }
                    }
                }

//                System.out.printf("result is %s: \n", result.toString());
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
        result.xidx = (result.dx + dx_init) + searchDistance;
        result.yidx = (result.dy + dy_init) + searchDistance;
        // compute final dx and dy including subsampling
        result.dx = (result.dx) << subSampleBy;
        result.dy = (result.dy) << subSampleBy;
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
        final int r = ((blockDimension) / 2) << (numScales - 1 - subsampleBy);
//        int w = subSizeX >> subsampleBy, h = subSizeY >> subsampleBy;
//        int adx = dx > 0 ? dx : -dx; // abs val of dx and dy, to compute limits
//        int ady = dy > 0 ? dy : -dy;
//
//        // Make sure both ref block and past slice block are in bounds on all sides or there'll be arrayIndexOutOfBoundary exception.
//        // Also we don't want to match ref block only on inner sides or there will be a bias towards motion towards middle
//        if (x - r - adx < 0 || x + r + adx >= w
//                || y - r - ady < 0 || y + r + ady >= h) {
//            return 1; // tobi changed to 1 again // Float.MAX_VALUE; // return very large distance for this match so it is not selected
//        }

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

        sumDist = sumDist;
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
//            seq1.appendCopy(spikeTrains[blockX][blockY].get(i));
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
//                                seq2.appendCopy(tmpSpikes.get(index));
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
            return String.format("(dx,dy=%5d,%5d), (vx,vy=%.1f,%.1f px/s), SAD=%f, scale=%d", dx, dy, vx, vy, sadValue, scale);
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

    private int computeMaxSearchDistance() {
        int sumScales = 0;
        for (int i = 0; i < numScales; i++) {
            sumScales += (1 << i);
        }
        return searchDistance * sumScales;
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
        avgPossibleMatchDistance = (float) d2;
        log.info(String.format("searchDistance=%d numScales=%d: avgPossibleMatchDistance=%.1f", searchDistance, numScales, avgPossibleMatchDistance));
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
        if (sliceDurationUs < getSliceDurationMinLimitUS()) {
            sliceDurationUs = getSliceDurationMinLimitUS();
        } else if (sliceDurationUs > getSliceDurationMaxLimitUS()) {
            sliceDurationUs = getSliceDurationMaxLimitUS(); // limit it to one second
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
        final int div = sliceMethod == SliceMethod.AreaEventNumber ? numAreas : 1;
        final int old = this.sliceEventCount;
        if (sliceEventCount < MIN_SLICE_EVENT_COUNT_FULL_FRAME / div) {
            sliceEventCount = MIN_SLICE_EVENT_COUNT_FULL_FRAME / div;
        } else if (sliceEventCount > MAX_SLICE_EVENT_COUNT_FULL_FRAME / div) {
            sliceEventCount = MAX_SLICE_EVENT_COUNT_FULL_FRAME / div;
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
                        int nx = (subSizeX >> s) + 1 + blockDimension, ny = (subSizeY >> s) + 1 + blockDimension;
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
//        if (lastTimesMap != null) {
//            lastTimesMap = null; // save memory
//        }
        // 
        int rhDim = 2 * computeMaxSearchDistance() + 1; // e.g. coarse to fine search strategy
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

    public boolean isShowBlockMatches() {
        return showBlockMatches;
    }

    /**
     * @param showBlockMatches
     * @param showBlockMatches the option of displaying bitmap
     */
    synchronized public void setShowBlockMatches(boolean showBlockMatches) {
        boolean old = this.showBlockMatches;
        this.showBlockMatches = showBlockMatches;
        putBoolean("showBlockMatches", showBlockMatches);
        getSupport().firePropertyChange("showBlockMatches", old, this.showBlockMatches);
    }

    public boolean isShowSlices() {
        return showSlices;
    }

    /**
     * @param showSlices
     * @param showSlices the option of displaying bitmap
     */
    synchronized public void setShowSlices(boolean showSlices) {
        boolean old = this.showSlices;
        this.showSlices = showSlices;
        getSupport().firePropertyChange("showSlices", old, this.showSlices);
        putBoolean("showSlices", showSlices);
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
                adaptiveSliceDurationLogger.setColumnHeaderLine("eventTsSec\tpacketNumber\tpacketEvenNumber\tavgMatchDistance\tmatchRadiusError\tglobalTranslationSpeedPPS\tsliceDurationUs\tsliceEventCount");
                adaptiveSliceDurationLogger.setSeparator("\t");
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
    synchronized private void drawMatching(SADResult result, PolarityEvent ein, byte[][][][] slices, float[] sadVals, int[] dxInitVals, int[] dyInitVals) {
//    synchronized private void drawMatching(int x, int y, int dx, int dy, byte[][] refBlock, byte[][] searchBlock, int subSampleBy) {
        for (int dispIdx = 0; dispIdx < numScales; dispIdx++) {
            int x = ein.x >> dispIdx, y = ein.y >> dispIdx;
            int dx = (int) result.dx >> dispIdx, dy = (int) result.dy >> dispIdx;
            byte[][] refBlock = slices[sliceIndex(1)][dispIdx], searchBlock = slices[sliceIndex(2)][dispIdx];
            int subSampleBy = dispIdx;
            Legend sadLegend = null;

            final int refRadius = (blockDimension / 2) << (numScales - 1 - dispIdx);
            int dimNew = refRadius * 2 + 1 + (2 * (searchDistance));
            if (blockMatchingFrame[dispIdx] == null) {
                String windowName = "Ref Block " + dispIdx;
                blockMatchingFrame[dispIdx] = new JFrame(windowName);
                blockMatchingFrame[dispIdx].setLayout(new BoxLayout(blockMatchingFrame[dispIdx].getContentPane(), BoxLayout.Y_AXIS));
                blockMatchingFrame[dispIdx].setPreferredSize(new Dimension(600, 600));
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                blockMatchingImageDisplay[dispIdx] = ImageDisplay.createOpenGLCanvas();
                blockMatchingImageDisplay[dispIdx].setBorderSpacePixels(10);
                blockMatchingImageDisplay[dispIdx].setImageSize(dimNew, dimNew);
                blockMatchingImageDisplay[dispIdx].setSize(200, 200);
                blockMatchingImageDisplay[dispIdx].setGrayValue(0);
                blockMatchingDisplayLegend[dispIdx] = blockMatchingImageDisplay[dispIdx].addLegend(LEGEND_G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
                panel.add(blockMatchingImageDisplay[dispIdx]);

                blockMatchingFrame[dispIdx].getContentPane().add(panel);
                blockMatchingFrame[dispIdx].pack();
                blockMatchingFrame[dispIdx].addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        setShowBlockMatches(false);
                    }
                });
            }
            if (!blockMatchingFrame[dispIdx].isVisible()) {
                blockMatchingFrame[dispIdx].setVisible(true);
            }

            if (blockMatchingFrameTarget[dispIdx] == null) {
                String windowName = "Target Block " + dispIdx;
                blockMatchingFrameTarget[dispIdx] = new JFrame(windowName);
                blockMatchingFrameTarget[dispIdx].setLayout(new BoxLayout(blockMatchingFrameTarget[dispIdx].getContentPane(), BoxLayout.Y_AXIS));
                blockMatchingFrameTarget[dispIdx].setPreferredSize(new Dimension(600, 600));
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                blockMatchingImageDisplayTarget[dispIdx] = ImageDisplay.createOpenGLCanvas();
                blockMatchingImageDisplayTarget[dispIdx].setBorderSpacePixels(10);
                blockMatchingImageDisplayTarget[dispIdx].setImageSize(dimNew, dimNew);
                blockMatchingImageDisplayTarget[dispIdx].setSize(200, 200);
                blockMatchingImageDisplayTarget[dispIdx].setGrayValue(0);
                blockMatchingDisplayLegendTarget[dispIdx] = blockMatchingImageDisplayTarget[dispIdx].addLegend(LEGEND_G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
                panel.add(blockMatchingImageDisplayTarget[dispIdx]);

                blockMatchingFrameTarget[dispIdx].getContentPane().add(panel);
                blockMatchingFrameTarget[dispIdx].pack();
                blockMatchingFrameTarget[dispIdx].addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        setShowBlockMatches(false);
                    }
                });
            }
            if (!blockMatchingFrameTarget[dispIdx].isVisible()) {
                blockMatchingFrameTarget[dispIdx].setVisible(true);
            }
            final int radius = (refRadius) + searchDistance;

            float scale = 1f / getSliceMaxValue();
            try {
                //            if ((x >= radius) && ((x + radius) < subSizeX)
                //                    && (y >= radius) && ((y + radius) < subSizeY))
                {

                    if (dimNew != blockMatchingImageDisplay[dispIdx].getWidth()) {
                        dim = dimNew;
                        blockMatchingImageDisplay[dispIdx].setImageSize(dimNew, dimNew);
                        blockMatchingImageDisplay[dispIdx].clearLegends();
                        blockMatchingDisplayLegend[dispIdx] = blockMatchingImageDisplay[dispIdx].addLegend(LEGEND_G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
                    }

                    if (dimNew != blockMatchingImageDisplayTarget[dispIdx].getWidth()) {
                        dim = dimNew;
                        blockMatchingImageDisplayTarget[dispIdx].setImageSize(dimNew, dimNew);
                        blockMatchingImageDisplayTarget[dispIdx].clearLegends();
                        blockMatchingDisplayLegendTarget[dispIdx] = blockMatchingImageDisplayTarget[dispIdx].addLegend(LEGEND_G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
                    }
                    //        TextRenderer textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 12));

                    if (blockMatchingDisplayLegend[dispIdx] != null) {
                        blockMatchingDisplayLegend[dispIdx].setLegendString("R: ref block area"
                                + "\nScale: "
                                + subSampleBy
                                + "\nSAD: "
                                + engFmt.format(sadVals[dispIdx])
                                + "\nTimestamp: " + ein.timestamp);
                    }
                    if (blockMatchingDisplayLegendTarget[dispIdx] != null) {
                        blockMatchingDisplayLegendTarget[dispIdx].setLegendString("G: search area"
                                + "\nx: " + ein.x
                                + "\ny: " + ein.y
                                + "\ndx_init: " + dxInitVals[dispIdx]
                                + "\ndy_init: " + dyInitVals[dispIdx]);
                    }

                    /* Reset the image first */
                    blockMatchingImageDisplay[dispIdx].clearImage();
                    blockMatchingImageDisplayTarget[dispIdx].clearImage();

                    /* Rendering the reference patch in t-imuWarningDialog slice, it's on the center with color red */
                    for (int i = searchDistance; i < (refRadius * 2 + 1 + searchDistance); i++) {
                        for (int j = searchDistance; j < (refRadius * 2 + 1 + searchDistance); j++) {
                            float[] f = blockMatchingImageDisplay[dispIdx].getPixmapRGB(i, j);
                            // Scale the pixel value to make it brighter for finer scale slice.
                            f[0] = (1 << (numScales - 1 - dispIdx)) * scale * Math.abs(refBlock[((x - (refRadius)) + i) - searchDistance][((y - (refRadius)) + j) - searchDistance]);
                            blockMatchingImageDisplay[dispIdx].setPixmapRGB(i, j, f);
                        }
                    }

                    /* Rendering the area within search distance in t-2d slice, it's full of the whole search area with color green */
                    for (int i = 0; i < ((2 * radius) + 1); i++) {
                        for (int j = 0; j < ((2 * radius) + 1); j++) {
                            float[] f = blockMatchingImageDisplayTarget[dispIdx].getPixmapRGB(i, j);
                            f[1] = scale * Math.abs(searchBlock[(x - dxInitVals[dispIdx] - radius) + i][(y - dyInitVals[dispIdx] - radius) + j]);
                            blockMatchingImageDisplayTarget[dispIdx].setPixmapRGB(i, j, f);
                        }
                    }

                    /* Rendering the best matching patch in t-2d slice, it's on the shifted position related to the center location with color blue */
//                    for (int i = searchDistance + dx; i < (blockDimension + searchDistance + dx); i++) {
//                        for (int j = searchDistance + dy; j < (blockDimension + searchDistance + dy); j++) {
//                            float[] f = blockMatchingImageDisplayTarget[dispIdx].getPixmapRGB(i, j);
//                            f[2] = scale * Math.abs(searchBlock[((x - (blockDimension / 2)) + i) - searchDistance][((y - (blockDimension / 2)) + j) - searchDistance]);
//                            blockMatchingImageDisplayTarget[dispIdx].setPixmapRGB(i, j, f);
//                        }
//                    }               
                }
            } catch (ArrayIndexOutOfBoundsException e) {

            }

            blockMatchingImageDisplay[dispIdx].repaint();
            blockMatchingImageDisplayTarget[dispIdx].repaint();
        }
    }

    synchronized private void drawTimeStampBlock(PolarityEvent ein) {
        int dim = 11;
        int sliceScale = 2;
        int eX = ein.x >> sliceScale, eY = ein.y >> sliceScale, eType = ein.type;

        if (timeStampBlockFrame == null) {
            String windowName = "FASTCornerBlock";
            timeStampBlockFrame = new JFrame(windowName);
            timeStampBlockFrame.setLayout(new BoxLayout(timeStampBlockFrame.getContentPane(), BoxLayout.Y_AXIS));
            timeStampBlockFrame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            timeStampBlockImageDisplay = ImageDisplay.createOpenGLCanvas();
            timeStampBlockImageDisplay.setBorderSpacePixels(10);
            timeStampBlockImageDisplay.setImageSize(dim, dim);
            timeStampBlockImageDisplay.setSize(200, 200);
            timeStampBlockImageDisplay.setGrayValue(0);
            timeStampBlockImageDisplayLegend = timeStampBlockImageDisplay.addLegend(LEGEND_G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
            panel.add(timeStampBlockImageDisplay);

            timeStampBlockFrame.getContentPane().add(panel);
            timeStampBlockFrame.pack();
            timeStampBlockFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    setShowSlices(false);
                }
            });
        }
        if (!timeStampBlockFrame.isVisible()) {
            timeStampBlockFrame.setVisible(true);
        }

        timeStampBlockImageDisplay.clearImage();

        for (int i = 0; i < innerCircleSize; i++) {
            xInnerOffset[i] = innerCircle[i][0];
            yInnerOffset[i] = innerCircle[i][1];
            innerTsValue[i] = lastTimesMap[eX + xInnerOffset[i]][eY + yInnerOffset[i]][type];
            innerTsValue[i] = slices[sliceIndex(1)][sliceScale][eX + xInnerOffset[i]][eY + yInnerOffset[i]];
            if (innerTsValue[i] == 0x80000000) {
                innerTsValue[i] = 0;
            }
        }

        for (int i = 0; i < outerCircleSize; i++) {
            xOuterOffset[i] = outerCircle[i][0];
            yOuterOffset[i] = outerCircle[i][1];
            outerTsValue[i] = lastTimesMap[eX + xOuterOffset[i]][eY + yOuterOffset[i]][type];
            outerTsValue[i] = slices[sliceIndex(1)][sliceScale][eX + xOuterOffset[i]][eY + yOuterOffset[i]];
            if (outerTsValue[i] == 0x80000000) {
                outerTsValue[i] = 0;
            }
        }

        List innerList = Arrays.asList(ArrayUtils.toObject(innerTsValue));
        int innerMax = (int) Collections.max(innerList);
        int innerMin = (int) Collections.min(innerList);
        float innerScale = 1f / (innerMax - innerMin);

        List outerList = Arrays.asList(ArrayUtils.toObject(outerTsValue));
        int outerMax = (int) Collections.max(outerList);
        int outerMin = (int) Collections.min(outerList);
        float outerScale = 1f / (outerMax - outerMin);

        float scale = 1f / getSliceMaxValue();
        timeStampBlockImageDisplay.setPixmapRGB(dim / 2, dim / 2, 0, lastTimesMap[eX][eY][type] * scale, 0);
        timeStampBlockImageDisplay.setPixmapRGB(dim / 2, dim / 2, 0, slices[sliceIndex(1)][sliceScale][eX][eY] * scale, 0);

        for (int i = 0; i < innerCircleSize; i++) {
            timeStampBlockImageDisplay.setPixmapRGB(xInnerOffset[i] + dim / 2, yInnerOffset[i] + dim / 2, innerScale * (innerTsValue[i] - innerMin), 0, 0);
            timeStampBlockImageDisplay.setPixmapRGB(xInnerOffset[i] + dim / 2, yInnerOffset[i] + dim / 2, scale * (innerTsValue[i]), 0, 0);
        }
        for (int i = 0; i < outerCircleSize; i++) {
            timeStampBlockImageDisplay.setPixmapRGB(xOuterOffset[i] + dim / 2, yOuterOffset[i] + dim / 2, 0, 0, outerScale * (outerTsValue[i] - outerMin));
            timeStampBlockImageDisplay.setPixmapRGB(xOuterOffset[i] + dim / 2, yOuterOffset[i] + dim / 2, 0, 0, scale * (outerTsValue[i]));
        }

        if (timeStampBlockImageDisplayLegend != null) {
            timeStampBlockImageDisplayLegend.setLegendString(TIME_STAMP_BLOCK_LEGEND_SLICES);
        }

        timeStampBlockImageDisplay.repaint();
    }

    private void drawSlices(byte[][][][] slices) {
//        log.info("drawing slices");
        if (sliceBitMapFrame == null) {
            String windowName = "Slices";
            sliceBitMapFrame = new JFrame(windowName);
            sliceBitMapFrame.setLayout(new BoxLayout(sliceBitMapFrame.getContentPane(), BoxLayout.Y_AXIS));
            sliceBitMapFrame.setPreferredSize(new Dimension(600, 600));
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            sliceBitmapImageDisplay = ImageDisplay.createOpenGLCanvas();
            sliceBitmapImageDisplay.setBorderSpacePixels(10);
            sliceBitmapImageDisplay.setImageSize(sizex >> showSlicesScale, sizey >> showSlicesScale);
            sliceBitmapImageDisplay.setSize(200, 200);
            sliceBitmapImageDisplay.setGrayValue(0);
            sliceBitmapImageDisplayLegend = sliceBitmapImageDisplay.addLegend(LEGEND_G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
            panel.add(sliceBitmapImageDisplay);

            sliceBitMapFrame.getContentPane().add(panel);
            sliceBitMapFrame.pack();
            sliceBitMapFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    setShowSlices(false);
                }
            });
        }
        if (!sliceBitMapFrame.isVisible()) {
            sliceBitMapFrame.setVisible(true);
        }

        int dimNewX = sizex >> showSlicesScale;
        int dimNewY = sizey >> showSlicesScale;
        if (dimNewX != sliceBitmapImageDisplay.getWidth() || dimNewY != sliceBitmapImageDisplay.getHeight()) {
            sliceBitmapImageDisplay.setImageSize(dimNewX, dimNewY);
            sliceBitmapImageDisplay.clearLegends();
            sliceBitmapImageDisplayLegend = sliceBitmapImageDisplay.addLegend(LEGEND_G_SEARCH_AREA_R_REF_BLOCK_AREA_B_BEST_MATCH, 0, dim);
        }

        float scale = 1f / getSliceMaxValue();
        sliceBitmapImageDisplay.clearImage();
        int d1 = sliceIndex(1), d2 = sliceIndex(2);
        if (showSlicesScale >= numScales) {
            showSlicesScale = numScales - 1;
        }

        int imageSizeX = sizex >> showSlicesScale;
        int imageSizeY = sizey >> showSlicesScale;
        byte[] grayImageBuffer = new byte[imageSizeX * imageSizeY];
        for (int slice = 1; slice <= 2; slice++) {
            for (int x = 0; x < imageSizeX; x++) {
                for (int y = 0; y < imageSizeY; y++) {
                    int pixelValue1 = slices[d1][showSlicesScale][x][y];
                    int pixelValue2 = slices[d2][showSlicesScale][x][y];
                    sliceBitmapImageDisplay.setPixmapRGB(x, y, scale * pixelValue1, scale * pixelValue2, 0);
                    // The minimum of byte is ox0(0), the maximum is 0xFF(-1); 
                    // It is from 0 to 127 and then -128 to -1;
                    int imagePixelVal = (int) (pixelValue1 * 255.0f / getSliceMaxValue() + 128) + (int) (pixelValue2 * 255.0f / getSliceMaxValue());
                    if (imagePixelVal == 0) {
                        imagePixelVal = 0;  // Background
                    }
                    grayImageBuffer[(imageSizeY - 1 - y) * imageSizeX + x] = (byte) imagePixelVal;
                }
            }
        }
        final BufferedImage theImage = new BufferedImage(imageSizeX, imageSizeY, BufferedImage.TYPE_BYTE_GRAY);
        theImage.getRaster().setDataElements(0, 0, imageSizeX, imageSizeY, grayImageBuffer);

        if (saveSliceGrayImage) {
            final Date d = new Date();
            final String PNG = "png";
//            final String fn = "EventSlice-" + AEDataFile.DATE_FORMAT.format(d) + "." + PNG;
            final String fn = "EventSlice-" + sliceEndTimeUs[d1] + "." + PNG;
            // if user is playing a file, use folder that file lives in
            String userDir = Paths.get(".").toAbsolutePath().normalize().toString();
            File eventSliceDir = new File(userDir + File.separator + "EventSlices" + File.separator + getChip().getAeInputStream().getFile().getName());
            if (!eventSliceDir.exists()) {
                eventSliceDir.mkdirs();
            }
            File outputfile = new File(eventSliceDir + File.separator + fn);

            try {
                ImageIO.write(theImage, "png", outputfile);
            } catch (IOException ex) {
                Logger.getLogger(PatchMatchFlow.class.getName()).log(Level.SEVERE, null, ex);
            }
            saveSliceGrayImage = false;
        }

        if (sliceBitmapImageDisplayLegend != null) {
            sliceBitmapImageDisplayLegend.setLegendString(LEGEND_SLICES);
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
                scalesToComputeArray = new Integer[n];
                int i = 0;
                while (st.hasMoreTokens()) {
                    try {
                        int scale = Integer.parseInt(st.nextToken());
                        scalesToComputeArray[i++] = scale;
                    } catch (NumberFormatException e) {
                        log.warning("bad string in scalesToCompute field, use blank or 0,2 for example");
                        setDefaultScalesToCompute();
                    }
                }
            }
        }
    }

    private void setDefaultScalesToCompute() {
        scalesToComputeArray = new Integer[numScales];
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
            int nax = 1 + (subSizeX >> areaEventNumberSubsampling), nay = 1 + (subSizeY >> areaEventNumberSubsampling);
            numAreas = nax * nay;
            areaCounts = new int[nax][nay];
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
                imuTimesliceLogger.setColumnHeaderLine("systemtime(ms) timestamp(us) timeslice(us) rate(deg/s)");
                imuTimesliceLogger.setSeparator(" ");
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
     * @return the adapativeSliceDurationProportionalErrorGain
     */
    public float getAdapativeSliceDurationProportionalErrorGain() {
        return adapativeSliceDurationProportionalErrorGain;
    }

    /**
     * @param adapativeSliceDurationProportionalErrorGain the
     * adapativeSliceDurationProportionalErrorGain to set
     */
    public void setAdapativeSliceDurationProportionalErrorGain(float adapativeSliceDurationProportionalErrorGain) {
        this.adapativeSliceDurationProportionalErrorGain = adapativeSliceDurationProportionalErrorGain;
        putFloat("adapativeSliceDurationProportionalErrorGain", adapativeSliceDurationProportionalErrorGain);
    }

    /**
     * @return the adapativeSliceDurationUseProportionalControl
     */
    public boolean isAdapativeSliceDurationUseProportionalControl() {
        return adapativeSliceDurationUseProportionalControl;
    }

    /**
     * @param adapativeSliceDurationUseProportionalControl the
     * adapativeSliceDurationUseProportionalControl to set
     */
    public void setAdapativeSliceDurationUseProportionalControl(boolean adapativeSliceDurationUseProportionalControl) {
        this.adapativeSliceDurationUseProportionalControl = adapativeSliceDurationUseProportionalControl;
        putBoolean("adapativeSliceDurationUseProportionalControl", adapativeSliceDurationUseProportionalControl);
    }

    public boolean isPrintScaleCntStatEnabled() {
        return printScaleCntStatEnabled;
    }

    public void setPrintScaleCntStatEnabled(boolean printScaleCntStatEnabled) {
        this.printScaleCntStatEnabled = printScaleCntStatEnabled;
        putBoolean("printScaleCntStatEnabled", printScaleCntStatEnabled);
    }

    /**
     * @return the showSlicesScale
     */
    public int getShowSlicesScale() {
        return showSlicesScale;
    }

    /**
     * @param showSlicesScale the showSlicesScale to set
     */
    public void setShowSlicesScale(int showSlicesScale) {
        if (showSlicesScale < 0) {
            showSlicesScale = 0;
        } else if (showSlicesScale > numScales - 1) {
            showSlicesScale = numScales - 1;
        }
        this.showSlicesScale = showSlicesScale;
    }

    public int getSliceDurationMinLimitUS() {
        return sliceDurationMinLimitUS;
    }

    public void setSliceDurationMinLimitUS(int sliceDurationMinLimitUS) {
        this.sliceDurationMinLimitUS = sliceDurationMinLimitUS;
        putInt("sliceDurationMinLimitUS", sliceDurationMinLimitUS);
    }

    public int getSliceDurationMaxLimitUS() {
        return sliceDurationMaxLimitUS;
    }

    public void setSliceDurationMaxLimitUS(int sliceDurationMaxLimitUS) {
        this.sliceDurationMaxLimitUS = sliceDurationMaxLimitUS;
        putInt("sliceDurationMaxLimitUS", sliceDurationMaxLimitUS);
    }

    public boolean isShowCorners() {
        return showCorners;
    }

    public void setShowCorners(boolean showCorners) {
        boolean old=this.showCorners;
        this.showCorners = showCorners;
        putBoolean("showCorners", showCorners);
        getSupport().firePropertyChange("showCorners", old, this.showCorners);
    }

    public boolean isHWABMOFEnabled() {
        return HWABMOFEnabled;
    }

    public void setHWABMOFEnabled(boolean HWABMOFEnabled) {
        this.HWABMOFEnabled = HWABMOFEnabled;
    }

    public boolean isCalcOFonCornersEnabled() {
        return calcOFonCornersEnabled;
    }

    public void setCalcOFonCornersEnabled(boolean calcOFonCornersEnabled) {
        boolean old=this.calcOFonCornersEnabled;
        this.calcOFonCornersEnabled = calcOFonCornersEnabled;
        putBoolean("calcOFonCornersEnabled", calcOFonCornersEnabled);
        getSupport().firePropertyChange("calcOFonCornersEnabled", old, this.calcOFonCornersEnabled);
    }

    public float getCornerThr() {
        return cornerThr;
    }

    public void setCornerThr(float cornerThr) {
        this.cornerThr = cornerThr;
        if (this.cornerThr > 1) {
            this.cornerThr = 1;
        }
        putFloat("cornerThr", cornerThr);
    }

    public CornerCircleSelection getCornerCircleSelection() {
        return cornerCircleSelection;
    }

    public void setCornerCircleSelection(CornerCircleSelection cornerCircleSelection) {
        CornerCircleSelection old = this.cornerCircleSelection;
        this.cornerCircleSelection = cornerCircleSelection;
        putString("cornerCircleSelection", cornerCircleSelection.toString());
        getSupport().firePropertyChange("cornerCircleSelection", old, this.cornerCircleSelection);
    }

    synchronized public void doStartRecordingForEDFLOW() {
        JFileChooser c = new JFileChooser(lastFileName);
        c.setFileFilter(new FileFilter() {

            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".txt");
            }

            public String getDescription() {
                return "text file";
            }
        });
        c.setSelectedFile(new File(lastFileName));
        int ret = c.showSaveDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String basename = c.getSelectedFile().toString();
        if (basename.toLowerCase().endsWith(".txt")) {
            basename = basename.substring(0, basename.length() - 4);
        }
        lastFileName = basename;
        putString("lastFileName", lastFileName);
        String fn = basename + "-OFResult.txt";
        try {
            dvsWriter = new PrintWriter(new File(fn));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(PatchMatchFlow.class.getName()).log(Level.SEVERE, null, ex);
        }
        dvsWriter.println("# created " + new Date().toString());
        dvsWriter.println("# source-file: " + (chip.getAeInputStream() != null ? chip.getAeInputStream().getFile().toString() : "(live input)"));
        dvsWriter.println("# dvs-events: One event per line:  timestamp(us) x y polarity(0=off,1=on) dx dy OFRetValid rotateFlg SFAST");
    }

    synchronized public void doStopRecordingForEDFLOW() {
        dvsWriter.close();
        dvsWriter = null;
    }

    // This is the BFAST (or SFAST in paper) corner dector. EFAST refer to HWCornerPointRender
    boolean PatchFastDetectorisFeature(PolarityEvent ein) {
        boolean found_streak = false;
        boolean found_streak_inner = false, found_streak_outer = false;

        int innerI = 0, outerI = 0, innerStreakSize = 0, outerStreakSize = 0;
        int scale = numScales - 1;
        int pix_x = ein.x >> scale;
        int pix_y = ein.y >> scale;

        byte featureSlice[][] = slices[sliceIndex(1)][scale];
        int circle3_[][] = innerCircle;
        int circle4_[][] = outerCircle;
        final int innerSize = circle3_.length;
        final int outerSize = circle4_.length;

        int innerStartX = 0, innerEndX = 0, innerStartY = 0, innerEndY = 0;
        int outerStartX, outerEndX, outerStartY, outerEndY;

        // only check if not too close to border
        if (pix_x < 4 || pix_x >= (getChip().getSizeX() >> scale) - 4
                || pix_y < 4 || pix_y >= (getChip().getSizeY() >> scale) - 4) {
            found_streak = false;
            return found_streak;
        }

        found_streak_inner = false;
        boolean exit_inner_loop = false;

        int centerValue = 0;
        int xInnerOffset[] = new int[innerCircleSize];
        int yInnerOffset[] = new int[innerCircleSize];
//        int innerTsValue[] = new int[innerCircleSize];
        for (int i = 0; i < innerCircleSize; i++) {
            xInnerOffset[i] = innerCircle[i][0];
            yInnerOffset[i] = innerCircle[i][1];
            innerTsValue[i] = featureSlice[pix_x + xInnerOffset[i]][pix_y + yInnerOffset[i]];
        }

        int xOuterOffset[] = new int[outerCircleSize];
        int yOuterOffset[] = new int[outerCircleSize];
//        int outerTsValue[] = new int[outerCircleSize];         
        for (int i = 0; i < outerCircleSize; i++) {
            xOuterOffset[i] = outerCircle[i][0];
            yOuterOffset[i] = outerCircle[i][1];
            outerTsValue[i] = featureSlice[pix_x + xOuterOffset[i]][pix_y + yOuterOffset[i]];
        }

        isFeatureOutterLoop:
        for (int i = 0; i < innerSize; i++) {
            FastDetectorisFeature_label2:
            for (int streak_size = innerCircleSize - 1; streak_size >= 2; streak_size = streak_size - 1) {
                // check that streak event is larger than neighbor
                if (Math.abs(featureSlice[pix_x + circle3_[i][0]][pix_y + circle3_[i][1]] - centerValue) < Math.abs(featureSlice[pix_x + circle3_[(i - 1 + innerSize) % innerSize][0]][pix_y + circle3_[(i - 1 + innerSize) % innerSize][1]] - centerValue)) {
                    continue;
                }

                // check that streak event is larger than neighbor
                if (Math.abs(featureSlice[pix_x + circle3_[(i + streak_size - 1) % innerSize][0]][pix_y + circle3_[(i + streak_size - 1) % innerSize][1]] - centerValue) < Math.abs(featureSlice[pix_x + circle3_[(i + streak_size) % innerSize][0]][pix_y + circle3_[(i + streak_size) % innerSize][1]] - centerValue)) {
                    continue;
                }

                // find the smallest timestamp in corner min_t
                double min_t = Math.abs(featureSlice[pix_x + circle3_[i][0]][pix_y + circle3_[i][1]] - centerValue);
                FastDetectorisFeature_label1:
                for (int j = 1; j < streak_size; j++) {
                    final double tj = Math.abs(featureSlice[pix_x + circle3_[(i + j) % innerSize][0]][pix_y + circle3_[(i + j) % innerSize][1]] - centerValue);
                    if (tj < min_t) {
                        min_t = tj;
                    }
                }

                //check if corner timestamp is higher than corner
                boolean did_break = false;
                double max_t = featureSlice[pix_x + circle3_[(i + streak_size) % innerSize][0]][pix_y + circle3_[(i + streak_size) % innerSize][1]];
                FastDetectorisFeature_label0:
                for (int j = streak_size; j < innerSize; j++) {
                    final double tj = Math.abs(featureSlice[pix_x + circle3_[(i + j) % innerSize][0]][pix_y + circle3_[(i + j) % innerSize][1]] - centerValue);

                    if (tj > max_t) {
                        max_t = tj;
                    }
                    if (tj >= min_t - cornerThr * getSliceMaxValue()) {
                        did_break = true;
                        break;
                    }
                }
                // The maximum value of the non-streak is on the border, remove it.
                if (!did_break) {
                    if ((max_t >= 7) && (max_t == featureSlice[pix_x + circle3_[(i + streak_size) % innerSize][0]][pix_y + circle3_[(i + streak_size) % innerSize][1]]
                            || max_t == featureSlice[pix_x + circle3_[(i + innerSize - 1) % innerSize][0]][pix_y + circle3_[(i + innerSize - 1) % innerSize][1]])) {
//                        did_break = true;
                    }
                }

                if (!did_break) {
                    innerI = i;
                    innerStreakSize = streak_size;
                    innerStartX = innerCircle[innerI % innerSize][0];
                    innerEndX = innerCircle[(innerI + innerStreakSize - 1) % innerSize][0];
                    innerStartY = innerCircle[innerI % innerSize][1];
                    innerEndY = innerCircle[(innerI + innerStreakSize - 1) % innerSize][1];
                    int condDiff = (streak_size % 2 == 1) ? 0 : 1;  // If streak_size is even, then set it to 1. Otherwise 0.                    
                    if ((streak_size == innerCircleSize - 1) || Math.abs(innerStartX - innerEndX) <= condDiff || Math.abs(innerStartY - innerEndY) <= condDiff //                            || featureSlice[pix_x + innerStartX][pix_y + innerEndX] < 12
                            //                            || featureSlice[pix_x + innerEndX][pix_y + innerEndY] < 12
                            ) {
                        found_streak_inner = false;
                    } else {
                        found_streak_inner = true;
                    }
                    exit_inner_loop = true;
                    break;
                }
            }

            if (found_streak_inner || exit_inner_loop) {
                break;
            }
        }

        found_streak_outer = false;

//        if (found_streak) 
        {
            found_streak_outer = false;
            boolean exit_outer_loop = false;

            FastDetectorisFeature_label6:
            for (int streak_size = outerCircleSize - 1; streak_size >= 3; streak_size--) {
                FastDetectorisFeature_label5:
                for (int i = 0; i < outerSize; i++) {
                    // check that first event is larger than neighbor
                    if (Math.abs(featureSlice[pix_x + circle4_[i][0]][pix_y + circle4_[i][1]] - centerValue) < Math.abs(featureSlice[pix_x + circle4_[(i - 1 + outerSize) % outerSize][0]][pix_y + circle4_[(i - 1 + outerSize) % outerSize][1]] - centerValue)) {
                        continue;
                    }

                    // check that streak event is larger than neighbor
                    if (Math.abs(featureSlice[pix_x + circle4_[(i + streak_size - 1) % outerSize][0]][pix_y + circle4_[(i + streak_size - 1) % outerSize][1]] - centerValue) < Math.abs(featureSlice[pix_x + circle4_[(i + streak_size) % outerSize][0]][pix_y + circle4_[(i + streak_size) % outerSize][1]] - centerValue)) {
                        continue;
                    }

                    double min_t = Math.abs(featureSlice[pix_x + circle4_[i][0]][pix_y + circle4_[i][1]] - centerValue);
                    FastDetectorisFeature_label4:
                    for (int j = 1; j < streak_size; j++) {
                        final double tj = Math.abs(featureSlice[pix_x + circle4_[(i + j) % outerSize][0]][pix_y + circle4_[(i + j) % outerSize][1]] - centerValue);
                        if (tj < min_t) {
                            min_t = tj;
                        }
                    }

                    boolean did_break = false;
                    double max_t = featureSlice[pix_x + circle4_[(i + streak_size) % outerSize][0]][pix_y + circle4_[(i + streak_size) % outerSize][1]];
                    float thr = cornerThr * getSliceMaxValue();
                    if (streak_size >= 9) {
                        thr += 1;
                    }
                    FastDetectorisFeature_label3:
                    for (int j = streak_size; j < outerSize; j++) {
                        final double tj = Math.abs(featureSlice[pix_x + circle4_[(i + j) % outerSize][0]][pix_y + circle4_[(i + j) % outerSize][1]] - centerValue);
                        if (tj > max_t) {
                            max_t = tj;
                        }
                        if (tj >= min_t - thr) {
                            did_break = true;
                            break;
                        }
                    }

                    if (!did_break) {
                        if (streak_size == 9 && (max_t >= 7)) {
                            int tmp = 0;
                        }
                    }

                    if (!did_break) {
                        outerI = i;
                        outerStreakSize = streak_size;
                        outerStartX = outerCircle[outerI % outerSize][0];
                        outerEndX = outerCircle[(outerI + outerStreakSize - 1) % outerSize][0];
                        outerStartY = outerCircle[outerI % outerSize][1];
                        outerEndY = outerCircle[(outerI + outerStreakSize - 1) % outerSize][1];
                        int condDiff = (streak_size % 2 == 1) ? 0 : 1;  // If streak_size is even, then set it to 1. Otherwise 0.
                        if ((streak_size == outerCircleSize - 1) || Math.abs(outerStartX - outerEndX) <= condDiff || Math.abs(outerStartY - outerEndY) <= condDiff //                                || featureSlice[pix_x + outerStartX][pix_y + outerStartY] < 12
                                //                                || featureSlice[pix_x + outerEndX][pix_y + outerEndX] < 12
                                ) {
                            found_streak_outer = false;
                        } else {
                            found_streak_outer = true;
                        }
                        if (min_t - max_t != 15 || streak_size != 10) {
//                            found_streak_outer = false; 
                        }
                        exit_outer_loop = true;
                        break;
                    }
                }
                if (found_streak_outer || exit_outer_loop) {
                    break;
                }
            }
        }

        switch (cornerCircleSelection) {
            case InnerCircle:
                found_streak = found_streak_inner;
                break;
            case OuterCircle:
                found_streak = found_streak_outer;
                break;
            case OR:
                found_streak = found_streak_inner || found_streak_outer;
                break;
            case AND:
                found_streak = found_streak_inner && found_streak_outer;
                break;
            default:
                found_streak = found_streak_inner && found_streak_outer;
                break;
        }
        return found_streak;
    }

    /**
     * @return the outlierRejectionEnabled
     */
    public boolean isOutlierRejectionEnabled() {
        return outlierRejectionEnabled;
    }

    /**
     * @param outlierRejectionEnabled the outlierRejectionEnabled to set
     */
    public void setOutlierRejectionEnabled(boolean outlierRejectionEnabled) {
        this.outlierRejectionEnabled = outlierRejectionEnabled;
        putBoolean("outlierRejectionEnabled", outlierRejectionEnabled);
    }

    /**
     * @return the outlierRejectionThresholdSigma
     */
    public float getOutlierRejectionThresholdSigma() {
        return outlierRejectionThresholdSigma;
    }

    /**
     * @param outlierRejectionThresholdSigma the outlierRejectionThresholdSigma
     * to set
     */
    public void setOutlierRejectionThresholdSigma(float outlierRejectionThresholdSigma) {
        this.outlierRejectionThresholdSigma = outlierRejectionThresholdSigma;
        putFloat("outlierRejectionThresholdSigma", outlierRejectionThresholdSigma);
    }

    private boolean isOutlierFlowVector(PatchMatchFlow.SADResult result) {
        if (!outlierRejectionEnabled) {
            return false;
        }
        GlobalMotion gm = outlierRejectionMotionFlowStatistics.getGlobalMotion();
        // update global flow here before outlier rejection, otherwise stats are not updated and std shrinks to zero
        gm.update(vx, vy, v, (x << getSubSampleShift()), (y << getSubSampleShift()));
        float speed = (float) Math.sqrt(result.vx * result.vx + result.vy * result.vy);
        // if the current vector speed is too many stds outside the mean speed then it is an outlier
        if (Math.abs(speed - gm.meanGlobalSpeed)
                > outlierRejectionThresholdSigma * gm.sdGlobalSpeed) {
            return true;
        }
//        if((Math.abs(result.vx-gm.meanGlobalVx)>outlierRejectionThresholdSigma*gm.sdGlobalVx) 
//            || (Math.abs(result.vy-gm.meanGlobalVy)>outlierRejectionThresholdSigma*gm.sdGlobalVy)) 
//            return true;
        return false;
    }

    /**
     * @return the useEFASTnotSFAST
     */
    public boolean isUseEFASTnotSFAST() {
        return useEFASTnotSFAST;
    }

    /**
     * @param useEFASTnotSFAST the useEFASTnotSFAST to set
     */
    public void setUseEFASTnotSFAST(boolean useEFASTnotSFAST) {
        this.useEFASTnotSFAST = useEFASTnotSFAST;
        putBoolean("useEFASTnotSFAST", useEFASTnotSFAST);
        checkForEASTCornerDetectorEnclosedFilter();
    }

    private void checkForEASTCornerDetectorEnclosedFilter() {
        if (useEFASTnotSFAST) {
            // add enclosed filter if not there
            if (keypointFilter == null) {
                keypointFilter = new HWCornerPointRenderer(chip);
            }
            if (!getEnclosedFilterChain().contains(keypointFilter)) {
                getEnclosedFilterChain().add(keypointFilter); // use for EFAST
            }
            keypointFilter.setFilterEnabled(isFilterEnabled());
            if (getChip().getAeViewer().getFilterFrame() != null) {
                getChip().getAeViewer().getFilterFrame().rebuildContents();
            }
        } else {
            if (keypointFilter != null && getEnclosedFilterChain().contains(keypointFilter)) {
                getEnclosedFilterChain().remove(keypointFilter);
                if (getChip().getAeViewer().getFilterFrame() != null) {
                    getChip().getAeViewer().getFilterFrame().rebuildContents();
                }
            }
        }
    }

    /**
     * @return the outlierRejectionWindowSize
     */
    public int getOutlierRejectionWindowSize() {
        return outlierRejectionWindowSize;
    }

    /**
     * @param outlierRejectionWindowSize the outlierRejectionWindowSize to set
     */
    public void setOutlierRejectionWindowSize(int outlierRejectionWindowSize) {
        this.outlierRejectionWindowSize = outlierRejectionWindowSize;
        putInt("outlierRejectionWindowSize", outlierRejectionWindowSize);
        outlierRejectionMotionFlowStatistics.setWindowSize(outlierRejectionWindowSize);
    }

    /**
     * @return the cornerSize
     */
    public int getCornerSize() {
        return cornerSize;
    }

    /**
     * @param cornerSize the cornerSize to set
     */
    public void setCornerSize(int cornerSize) {
        this.cornerSize = cornerSize;
    }

}
