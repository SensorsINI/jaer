package ch.unizh.ini.jaer.projects.rbodo.opticalflow;

import ch.unizh.ini.jaer.projects.davis.calibration.SingleCameraCalibration;
import com.jmatio.io.MatFileReader;
import com.jmatio.io.MatFileWriter;
import com.jmatio.types.MLDouble;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.util.gl2.GLUT;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.orientation.ApsDvsMotionOrientationEvent;
import net.sf.jaer.event.orientation.DvsMotionOrientationEvent;
import net.sf.jaer.event.orientation.MotionOrientationEventInterface;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.TobiLogger;
import net.sf.jaer.util.WarningDialogWithDontShowPreference;
import net.sf.jaer.util.filter.LowpassFilter3D;
import net.sf.jaer.util.filter.LowpassFilter3D.Point3D;

/**
 * Abstract base class for motion flow filters. The filters that extend this
 * class use different methods to compute the optical flow vectors and override
 * the filterPacket method in this class. Several methods were taken from
 * AbstractDirectionSelectiveFilter and Steadicam and slightly modified.
 *
 * @author rbodo
 */
@Description("Abstract base class for motion optical flow.")
@DevelopmentStatus(DevelopmentStatus.Status.Abstract)
abstract public class AbstractMotionFlowIMU extends EventFilter2D implements Observer, FrameAnnotater, PropertyChangeListener {

    // Observed motion flow.
    public static float vx, vy, v;

    int numInputTypes;

    /**
     * Basic event information. Type is event polarity value, 0=OFF and 1=ON,
     * typically but not always
     */
    public int x, y, ts, type, lastTs;

    /**
     * (Subsampled) chip sizes.
     */
    protected int sizex, sizey, subSizeX, subSizeY;

    /**
     * Subsampling
     */
    private int subSampleShift = getInt("subSampleShift", 0);
    protected boolean[][] subsampledPixelIsSet;

    // Map of input orientation event times 
    // [x][y][type] where type is mixture of orienation and polarity.
    protected int[][][] lastTimesMap;

    // xyFilter.
    private int xMin = getInt("xMin", 0);
    private static final int DEFAULT_XYMAX = 1000;
    private int xMax = getInt("xMax", DEFAULT_XYMAX); // (tobi) set to large value to make sure any chip will have full area processed by default
    private int yMin = getInt("yMin", 0);
    private int yMax = getInt("yMax", DEFAULT_XYMAX);

    // Display
    private boolean displayVectorsEnabled = getBoolean("displayVectorsEnabled", true);
    private boolean displayVectorsAsUnitVectors = getBoolean("displayVectorsAsUnitVectors", false);
    private boolean displayVectorsAsColorDots = getBoolean("displayVectorsAsColorDots", false);
    private boolean displayZeroLengthVectorsEnabled = getBoolean("displayZeroLengthVectorsEnabled", true);
    private boolean displayRawInput = getBoolean("displayRawInput", true);
    private boolean displayColorWheelLegend = getBoolean("displayColorWheelLegend", true);

    private float ppsScale = getFloat("ppsScale", 0.1f);
    private boolean ppsScaleDisplayRelativeOFLength = getBoolean("ppsScaleDisplayRelativeOFLength", false);

    // A pixel can fire an event only after this period. Used for smoother flow
    // and speedup.
    private int refractoryPeriodUs = getInt("refractoryPeriodUs", 0);

    // Global translation, rotation and expansion.
    private boolean displayGlobalMotion = getBoolean("displayGlobalMotion", true);

    protected EngineeringFormat engFmt = new EngineeringFormat();

    /**
     * The output events, also used for rendering output events.
     */
    protected EventPacket dirPacket;
    /**
     * The output packet iterator
     */
    protected OutputEventIterator outItr;
    /**
     * The current input event
     */
    protected PolarityEvent e;
    /**
     * The current output event
     */
    protected ApsDvsMotionOrientationEvent eout;

    /**
     * Use IMU gyro values to estimate motion flow.
     */
    public ImuFlowEstimator imuFlowEstimator;

    // Focal length of camera lens in mm needed to convert rad/s to pixel/s.
    // Conversion factor is atan(pixelWidth/focalLength).
    private float lensFocalLengthMm = 4.5f;

    // Focal length of camera lens needed to convert rad/s to pixel/s.
    // Conversion factor is atan(pixelWidth/focalLength).
    private float radPerPixel;

    private boolean addedViewerPropertyChangeListener = false; // TODO promote these to base EventFilter class
    private boolean addTimeStampsResetPropertyChangeListener = false;

    // Performing statistics and logging results. lastLoggingFolder starts off 
    // at user.dir which is startup folder "host/java" where .exe launcher lives
    private String loggingFolder = getPrefs().get("DataLogger.loggingFolder", System.getProperty("user.dir"));
    public boolean measureAccuracy = getBoolean("measureAccuracy", true);
    boolean measureProcessingTime = getBoolean("measureProcessingTime", false);
    public int countIn, countOut;
    protected MotionFlowStatistics motionFlowStatistics;

    double[][] vxGTframe, vyGTframe, tsGTframe;
    public float vxGT, vyGT, vGT;
    private boolean importedGTfromMatlab;

    // Discard events that are considerably faster than average
    private float avgSpeed = 0;
    boolean speedControlEnabled = getBoolean("speedControlEnabled", true);
    private float speedMixingFactor = getFloat("speedMixingFactor", 1e-3f);
    private float excessSpeedRejectFactor = getFloat("excessSpeedRejectFactor", 2f);

    // Motion flow vectors can be filtered out if the angle between the observed 
    // optical flow and ground truth is greater than a certain threshold.
    // At the moment, this option is not included in the jAER filter settings
    // and defaults to false.
    protected boolean discardOutliersForStatisticalMeasurementEnabled = false;
    // Threshold angle in degree. Discard measured optical flow vector if it 
    // deviates from ground truth by more than discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg.
    protected float discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg = 10f;

    // outlier filtering that only allows through motion events that agree with at least N most-recent neigbor events 
    // in max angle difference
//    private boolean outlierMotionFilteringEnabled = getBoolean("outlierMotionFilteringEnabled", false);
    protected float outlierMotionFilteringMaxAngleDifferenceDeg = getFloat("outlierMotionFilteringMaxAngleDifferenceDeg", 30f);
    protected int outlierMotionFilteringSubsampleShift = getInt("outlierMotionFilteringSubsampleShift", 1);
    protected int outlierMotionFilteringMinSameAngleInNeighborhood = getInt("outlierMotionFilteringMinSameAngleInNeighborhood", 2);
    protected int[][] outlierMotionFilteringLastAngles = null;
    private float motionVectorLineWidthPixels = getFloat("motionVectorLineWidthPixels", 4);

    // MotionField that aggregates motion
    protected MotionField motionField = new MotionField();

    /**
     * Relative scale of displayed global flow vector
     */
    protected static final float GLOBAL_MOTION_DRAWING_SCALE = 1;

    /**
     * Used for logging motion vector events to a text log file
     */
    protected TobiLogger motionVectorEventLogger = null;

    final String filterClassName;

    private boolean exportedFlowToMatlab;
    private double[][] vxOut = null;
    private double[][] vyOut = null;

    public Iterator inItr;

    protected static String dispTT = "Display";
    protected static String measureTT = "Measure";
    protected static String imuTT = "IMU";
    protected static String smoothingTT = "Smoothing";
    protected static String motionFieldTT = "Motion field";

    protected SingleCameraCalibration cameraCalibration = null;
    int uidx;
    protected boolean useColorForMotionVectors = getBoolean("useColorForMotionVectors", true);

    public AbstractMotionFlowIMU(AEChip chip) {
        super(chip);
        addObservers(chip);
        imuFlowEstimator = new ImuFlowEstimator();
        dirPacket = new EventPacket(ApsDvsMotionOrientationEvent.class);
        filterClassName = getClass().getSimpleName();
        motionFlowStatistics = new MotionFlowStatistics(filterClassName, subSizeX, subSizeY);
        setMeasureAccuracy(getBoolean("measureAccuracy", true));
        setMeasureProcessingTime(getBoolean("measureProcessingTime", false));
        setDisplayGlobalMotion(getBoolean("displayGlobalMotion", true));// these setters set other flags, so call them to set these flags to default values

        FilterChain chain = new FilterChain(chip);
        try {
            cameraCalibration = new SingleCameraCalibration(chip);
            cameraCalibration.setRealtimePatternDetectionEnabled(false);
            cameraCalibration.setFilterEnabled(false);

            getSupport().addPropertyChangeListener(SingleCameraCalibration.EVENT_NEW_CALIBRATION, this);
            chain.add(cameraCalibration);
        } catch (Exception e) {
            log.warning("could not add calibration for DVS128");
        }
        setEnclosedFilterChain(chain);

        // Labels for setPropertyTooltip.
        setPropertyTooltip("startLoggingMotionVectorEvents", "starts saving motion vector events to a human readable file");
        setPropertyTooltip("stopLoggingMotionVectorEvents", "stops logging motion vector events to a human readable file");
        setPropertyTooltip("printStatistics", "<html> Prints to console as log output a single instance of statistics collected since <b>measureAccuracy</b> was selected. (These statistics are reset when the filter is reset, e.g. at rewind.)");
        setPropertyTooltip(measureTT, "measureAccuracy", "<html> Writes a txt file with various motion statistics, by comparing the ground truth <br>(either estimated online using an embedded IMUFlow or loaded from file) <br> with the measured optical flow events.  <br>This measurment function is called for every event to assign the local ground truth<br> (vxGT,vyGT) at location (x,y) a value from the imported ground truth field (vxGTframe,vyGTframe).");
        setPropertyTooltip(measureTT, "measureProcessingTime", "writes a text file with timestamp filename with the packet's mean processing time of an event. Processing time is also logged to console.");
        setPropertyTooltip(measureTT, "loggingFolder", "directory to store logged data files");
        setPropertyTooltip(dispTT, "ppsScale", "<html>When <i>ppsScaleDisplayRelativeOFLength=false</i>, then this is <br>scale of pixels per second to draw local motion vectors; <br>global vectors are scaled up by an additional factor of " + GLOBAL_MOTION_DRAWING_SCALE + "<p>"
                + "When <i>ppsScaleDisplayRelativeOFLength=true</i>, then local motion vectors are scaled by average speed of flow");
        setPropertyTooltip(dispTT, "ppsScaleDisplayRelativeOFLength", "<html>Display flow vector lengths relative to global average speed");
        setPropertyTooltip(dispTT, "displayVectorsEnabled", "shows local motion vector evemts as arrows");
        setPropertyTooltip(dispTT, "displayVectorsAsColorDots", "shows local motion vector events as color dots, rather than arrows");
        setPropertyTooltip(dispTT, "displayVectorsAsUnitVectors", "shows local motion vector events with unit vector length");
        setPropertyTooltip(dispTT, "displayZeroLengthVectorsEnabled", "shows local motion vector evemts even if they indicate zero motion (stationary features)");
        setPropertyTooltip(dispTT, "displayColorWheelLegend", "Plots a color wheel to show flow direction colors.");
        setPropertyTooltip(dispTT, "displayGlobalMotion", "shows global tranlational, rotational, and expansive motion. These vectors are scaled by ppsScale * " + GLOBAL_MOTION_DRAWING_SCALE + " pixels/second per chip pixel");
        setPropertyTooltip(dispTT, "displayRawInput", "shows the input events, instead of the motion types");
        setPropertyTooltip(dispTT, "xMin", "events with x-coordinate below this are filtered out.");
        setPropertyTooltip(dispTT, "xMax", "events with x-coordinate above this are filtered out.");
        setPropertyTooltip(dispTT, "yMin", "events with y-coordinate below this are filtered out.");
        setPropertyTooltip(dispTT, "yMax", "events with y-coordinate above this are filtered out.");
        setPropertyTooltip(dispTT, "motionVectorLineWidthPixels", "line width to draw motion vectors");
        setPropertyTooltip(dispTT, "useColorForMotionVectors", "display the output motion vectors in color");
        setPropertyTooltip(smoothingTT, "subSampleShift", "shift subsampled timestamp map stores by this many bits");
        setPropertyTooltip(smoothingTT, "refractoryPeriodUs", "compute no flow vector if a flow vector has already been computed within this period at the same location.");
        setPropertyTooltip(smoothingTT, "speedControlEnabled", "enables filtering of excess speeds");
        setPropertyTooltip(smoothingTT, "speedControl_ExcessSpeedRejectFactor", "local speeds this factor higher than average are rejected as non-physical");
        setPropertyTooltip(smoothingTT, "speedControl_speedMixingFactor", "speeds computed are mixed with old values with this factor");
//        setPropertyTooltip(imuTT, "discardOutliersForStatisticalMeasurementEnabled", "discard measured local motion vector if it deviates from IMU estimate");
//        setPropertyTooltip(imuTT, "discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg", "threshold angle in degree. Discard measured optical flow vector if it deviates from IMU-estimate by more than discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg");
        setPropertyTooltip(imuTT, "lensFocalLengthMm", "lens focal length in mm. Used for computing the IMU flow from pan and tilt camera rotations. 4.5mm is focal length for dataset data.");
        setPropertyTooltip(imuTT, "calibrationSamples", "number of IMU samples to average over for measuring IMU offset.");
        setPropertyTooltip(imuTT, "startIMUCalibration", "<html> Starts estimating the IMU offsets based on next calibrationSamples samples. Should be used only with stationary recording to store these offsets in the preferences. <p> <b>measureAccuracy</b> must be selected as well to actually do the calibration.");
        setPropertyTooltip(imuTT, "eraseIMUCalibration", "Erases the IMU offsets to zero. Can be used to observe effect of these offsets on a stationary recording in the IMUFlow filter.");
        setPropertyTooltip(imuTT, "importGTfromMatlab", "Allows importing two 2D-arrays containing the x-/y- components of the motion flow field used as ground truth.");
        setPropertyTooltip(imuTT, "resetGroundTruth", "Resets the ground truth optical flow that was imported from matlab. Used in the measureAccuracy option.");
        setPropertyTooltip(imuTT, "selectLoggingFolder", "Allows selection of the folder to store the measured accuracies and optical flow events.");
//        setPropertyTooltip(motionFieldTT, "motionFieldMixingFactor", "Flow events are mixed with the motion field with this factor. Use 1 to replace field content with each event, or e.g. 0.01 to update only by 1%.");
        setPropertyTooltip(motionFieldTT, "displayMotionField", "computes and shows the average motion field (see MotionField section)");
        setPropertyTooltip(motionFieldTT, "motionFieldSubsamplingShift", "The motion field is computed at this subsampled resolution, e.g. 1 means 1 motion field vector for each 2x2 pixel area.");
        setPropertyTooltip(motionFieldTT, "maxAgeUs", "Maximum age of motion field value for display and for unconditionally replacing with latest flow event");
        setPropertyTooltip(motionFieldTT, "minSpeedPpsToDrawMotionField", "Motion field locations where speed in pixels/second is less than this quantity are not drawn");
        setPropertyTooltip(motionFieldTT, "consistentWithNeighbors", "Motion field value must be consistent with several neighbors if this option is selected.");
        setPropertyTooltip(motionFieldTT, "consistentWithCurrentAngle", "Motion field value is only updated if flow event angle is in same half plane as current estimate, i.e. has non-negative dot product.");
        setPropertyTooltip(motionFieldTT, "motionFieldTimeConstantMs", "Motion field low pass filter time constant in ms.");
        setPropertyTooltip(motionFieldTT, "displayMotionFieldColorBlobs", "Shows color blobs for motion field as well as the flow vector arrows");
        setPropertyTooltip(motionFieldTT, "displayMotionFieldUsingColor", "Shows motion field flow vectors in color; otherwise shown as monochrome color arrows");
        setPropertyTooltip(motionFieldTT, "motionFieldDiffusionEnabled", "Enables an event-driven diffusive averaging of motion field values");
        setPropertyTooltip(motionFieldTT, "decayTowardsZeroPeridiclly", "Decays motion field values periodically (with update interval of the time constant) towards zero velocity, i.e. enforce zero flow prior");
        File lf = new File(loggingFolder);
        if (!lf.exists() || !lf.isDirectory()) {
            log.log(Level.WARNING, "loggingFolder {0} doesn't exist or isn't a directory, defaulting to {1}", new Object[]{lf, lf});
            setLoggingFolder(System.getProperty("user.dir"));
        }
    }

    synchronized public void doSelectLoggingFolder() {
        if (loggingFolder == null || loggingFolder.isEmpty()) {
            loggingFolder = System.getProperty("user.dir");
        }
        JFileChooser chooser = new JFileChooser(loggingFolder);
        chooser.setDialogTitle("Choose data logging folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        if (chooser.showOpenDialog(getChip().getAeViewer().getFilterFrame()) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (f != null && f.isDirectory()) {
                setLoggingFolder(f.toString());
                log.log(Level.INFO, "Selected data logging folder {0}", loggingFolder);
            } else {
                log.log(Level.WARNING, "Tried to select invalid logging folder named {0}", f);
            }
        }
    }

    public final void addObservers(AEChip chip) {
        chip.addObserver(this);
    }

    public final void maybeAddListeners(AEChip chip) {
        if (chip.getAeViewer() != null) {
            if (!addedViewerPropertyChangeListener) {
                chip.getAeViewer().addPropertyChangeListener(this);
                addedViewerPropertyChangeListener = true;
            }
            if (!addTimeStampsResetPropertyChangeListener) {
                chip.getAeViewer().addPropertyChangeListener(AEViewer.EVENT_TIMESTAMPS_RESET, this);
                addTimeStampsResetPropertyChangeListener = true;
            }
        }
    }

    // Allows importing two 2D-arrays containing the x-/y- components of the 
    // motion flow field used as ground truth.
    synchronized public void doImportGTfromMatlab() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose ground truth file");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        if (chooser.showOpenDialog(chip.getAeViewer().getFilterFrame()) == JFileChooser.APPROVE_OPTION) {
            try {
                vxGTframe = ((MLDouble) (new MatFileReader(chooser.getSelectedFile().getPath())).getMLArray("vxGT")).getArray();
                vyGTframe = ((MLDouble) (new MatFileReader(chooser.getSelectedFile().getPath())).getMLArray("vyGT")).getArray();
                tsGTframe = ((MLDouble) (new MatFileReader(chooser.getSelectedFile().getPath())).getMLArray("ts")).getArray();
                importedGTfromMatlab = true;
                log.info("Imported ground truth file");
            } catch (IOException ex) {
                log.log(Level.SEVERE, null, ex);
            }
        }
    }

    // Allows exporting flow vectors that were accumulated between tmin and tmax
    // to a mat-file which can be processed in MATLAB.
    public void exportFlowToMatlab(final int tmin, final int tmax) {
        if (!exportedFlowToMatlab) {
            int firstTs = dirPacket.getFirstTimestamp();
            if (firstTs > tmin && firstTs < tmax) {
                if (vxOut == null) {
                    vxOut = new double[sizey][sizex];
                    vyOut = new double[sizey][sizex];
                }
                for (Object o : dirPacket) {
                    ApsDvsMotionOrientationEvent ev = (ApsDvsMotionOrientationEvent) o;
                    if (ev.hasDirection) {
                        vxOut[ev.y][ev.x] = ev.velocity.x;
                        vyOut[ev.y][ev.x] = ev.velocity.y;
                    }
                }
            }
            if (firstTs > tmax && vxOut != null) {
                ArrayList list = new ArrayList();
                list.add(new MLDouble("vx", vxOut));
                list.add(new MLDouble("vy", vyOut));
                try {
                    MatFileWriter matFileWriter = new MatFileWriter(loggingFolder + "/flowExport.mat", list);
                } catch (IOException ex) {
                    log.log(Level.SEVERE, null, ex);
                }
                log.log(Level.INFO, "Exported motion flow to {0}/flowExport.mat", loggingFolder);
                exportedFlowToMatlab = true;
                vxOut = null;
                vyOut = null;
            }
        }
    }
    
    void resetGroundTruth() {
        importedGTfromMatlab = false;
        vxGTframe = null;
        vyGTframe = null;
        tsGTframe = null;
        vxGT = 0;
        vyGT = 0;
        vGT = 0;
    }

    synchronized public void doResetGroundTruth() {
        resetGroundTruth();
    }

    /**
     * @return the useColorForMotionVectors
     */
    public boolean isUseColorForMotionVectors() {
        return useColorForMotionVectors;
    }

    /**
     * @param useColorForMotionVectors the useColorForMotionVectors to set
     */
    public void setUseColorForMotionVectors(boolean useColorForMotionVectors) {
        this.useColorForMotionVectors = useColorForMotionVectors;
        putBoolean("useColorForMotionVectors", useColorForMotionVectors);
    }

    // <editor-fold defaultstate="collapsed" desc="ImuFlowEstimator Class">    
    public class ImuFlowEstimator {

        // Motion flow from IMU gyro values.
        private float vx;
        private float vy;
        private float v;

        // Highpass filters for angular rates.   
        private float panRateDps, tiltRateDps, rollRateDps; // In deg/s

        // Calibration
        private boolean calibrating = false; // used to flag cameraCalibration state
        private int calibrationSamples = getInt("calibrationSamples", 100); // number of samples, typically they come at 1kHz
        private final Measurand panCalibrator, tiltCalibrator, rollCalibrator;
        private float panOffset;
        private float tiltOffset;
        private float rollOffset;
        private boolean calibrated = false;

        // Deal with leftover IMU data after timestamps reset
        private static final int FLUSH_COUNT = 1;
        private int flushCounter;

        protected ImuFlowEstimator() {
            panCalibrator = new Measurand();
            tiltCalibrator = new Measurand();
            rollCalibrator = new Measurand();
            // Some initial IMU cameraCalibration values.
            // Will be overwritten when calibrating IMU
            panOffset = getFloat("panOffset", 0);
            tiltOffset = getFloat("tiltOffset", 0);
            rollOffset = getFloat("rollOffset", 0); // tobi set back to zero rather than using hard coded values.
            if (panOffset != 0 || rollOffset != 0 || tiltOffset != 0) {
                log.warning("using existing calibration (offset) IMU rate gyro values stored in preferences: " + this.toString());
                calibrated = true;
            }
            reset();
        }

        protected final synchronized void reset() {
            flushCounter = FLUSH_COUNT;
            panRateDps = 0;
            tiltRateDps = 0;
            rollRateDps = 0;
            radPerPixel = (float) Math.atan(chip.getPixelWidthUm() / (1000 * lensFocalLengthMm));
            vx = 0;
            vy = 0;
            v = 0;
        }

        float getVx() {
            return vx;
        }

        float getVy() {
            return vy;
        }

        float getV() {
            return v;
        }

        boolean isCalibrationSet() {
            return calibrated;
        }

        /**
         * Calculate GT motion flow from IMU sample.
         *
         * @param o event
         * @return true if event is an IMU event, so enclosing loop can continue
         * to next event, skipping flow processing of this IMU event. Return
         * false if event is not IMU event.
         */
        public boolean calculateImuFlow(Object o) {
            if (!(o instanceof ApsDvsEvent)) {
                return true; // continue processing this event outside
            }
            ApsDvsEvent e = (ApsDvsEvent) o;
            if (e.isImuSample()) {
                IMUSample imuSample = e.getImuSample();
                float panRateDpsSample = imuSample.getGyroYawY(); // update pan tilt roll state from IMU
                float tiltRateDpsSample = imuSample.getGyroTiltX();
                float rollRateDpsSample = imuSample.getGyroRollZ();
                if (calibrating) {
                    if (panCalibrator.getN() > getCalibrationSamples()) {
                        calibrating = false;
                        panOffset = (float) panCalibrator.getMean();
                        tiltOffset = (float) tiltCalibrator.getMean();
                        rollOffset = (float) rollCalibrator.getMean();
                        log.info(String.format("calibration finished. %d samples averaged"
                                + " to (pan,tilt,roll)=(%.3f,%.3f,%.3f)", getCalibrationSamples(), panOffset, tiltOffset, rollOffset));
                        calibrated = true;
                        putFloat("panOffset", panOffset);
                        putFloat("tiltOffset", tiltOffset);
                        putFloat("rollOffset", rollOffset);
                    } else {
                        panCalibrator.addValue(panRateDpsSample);
                        tiltCalibrator.addValue(tiltRateDpsSample);
                        rollCalibrator.addValue(rollRateDpsSample);
                    }
                    return false;
                }
                panRateDps = panRateDpsSample - panOffset;
                tiltRateDps = tiltRateDpsSample - tiltOffset;
                rollRateDps = rollRateDpsSample - rollOffset;

                return true;
            }
            if (importedGTfromMatlab) {
                if (ts >= tsGTframe[0][0] && ts < tsGTframe[0][1]) {
                    vx = (float) vxGTframe[y][x];
                    vy = (float) vyGTframe[y][x];
                } else {
                    vx = 0;
                    vy = 0;
                }
            } else {
                // otherwise, if not IMU sample, use last IMU sample to compute GT flow from last IMU sample and event location
                // then transform, then move them back to their origin.
                // 
                // The flow is computed from trigonometry assuming a pinhole lens. This lens projects points at larger angular distance with 
                // smaller pixel spacing. i.e. the math is the following. 
                // If the distance of the pixel from the middle of the image is l, the angle to the point is w, the focal length is f, then
                // tan(w)=l/f, 
                // and dl/dt=f dw/dt (sec^2(w))
                int nx = e.x - sizex / 2; // TODO assumes principal point is at center of image
                int ny = e.y - sizey / 2;
//                panRateDps=0; tiltRateDps=0; // debug
                final float rrrad = -(float) (rollRateDps * Math.PI / 180);
                final float radfac = (float) (Math.PI / 180);
                final float pixfac = radfac / radPerPixel;
                final float pixdim = chip.getPixelWidthUm() * 1e-3f;
                final float thetax = (float) Math.atan2(nx * pixdim, lensFocalLengthMm);
                final float secx = (float) (1f / Math.cos(thetax));
                final float xprojfac = (float) (secx * secx);
                final float thetay = (float) Math.atan2(ny * pixdim, lensFocalLengthMm);
                final float secy = (float) (1f / Math.cos(thetay));
                final float yprojfac = (float) (secy * secy);

                vx = -(float) (-ny * rrrad + panRateDps * pixfac) * xprojfac;
                vy = -(float) (nx * rrrad - tiltRateDps * pixfac) * yprojfac;
                v = (float) Math.sqrt(vx * vx + vy * vy);
            }
            return false;
        }

        /**
         * @return the calibrationSamples
         */
        public int getCalibrationSamples() {
            return calibrationSamples;
        }

        /**
         * @param calibrationSamples the calibrationSamples to set
         */
        public void setCalibrationSamples(int calibrationSamples) {
            this.calibrationSamples = calibrationSamples;
        }

        @Override
        public String toString() {
            return "ImuFlowEstimator{" + "panOffset=" + panOffset + ", tiltOffset=" + tiltOffset + ", rollOffset=" + rollOffset + ", flushCounter=" + flushCounter + '}';
        }

        private void eraseIMUCalibration() {
            panOffset = 0;
            tiltOffset = 0;
            rollOffset = 0;
            calibrated = false;
            putFloat("panOffset", panOffset);
            putFloat("tiltOffset", tiltOffset);
            putFloat("rollOffset", rollOffset);

            log.info("IMU calibration erased (all offsets set to zero)");
        }

        /**
         *
         * @return the panRateDps in deg/s
         */
        public float getPanRateDps() {
            return panRateDps;
        }

        /**
         * @return the tiltRateDps in deg/s
         */
        public float getTiltRateDps() {
            return tiltRateDps;
        }

        /**
         * @return the rollRateDps in deg/s
         */
        public float getRollRateDps() {
            return rollRateDps;
        }

    }
    // </editor-fold>

    @Override
    public abstract EventPacket filterPacket(EventPacket in);

    synchronized void allocateMap() {

        if (subSizeY * subSizeX * numInputTypes == 0) {
            return;
        }

        if (subsampledPixelIsSet == null) {
            subsampledPixelIsSet = new boolean[subSizeX][subSizeY];
        }

        for (boolean[] a : subsampledPixelIsSet) {
            Arrays.fill(a, false);
        }
        lastTimesMap = new int[subSizeX][subSizeY][numInputTypes];

        for (int[][] a : lastTimesMap) {
            for (int[] b : a) {
                Arrays.fill(b, Integer.MIN_VALUE);
            }
        }
        motionFlowStatistics.getGlobalMotion().reset(subSizeX, subSizeY);
//        log.info("Reset filter storage after parameter change or reset.");
    }

    @Override
    public synchronized void resetFilter() {
        sizex = chip.getSizeX();
        sizey = chip.getSizeY();
        subSizeX = sizex >> subSampleShift;
        subSizeY = sizey >> subSampleShift;
        motionFlowStatistics.reset(subSizeX, subSizeY);
        imuFlowEstimator.reset();
        exportedFlowToMatlab = false;
        motionField.reset();
        allocateMap();
        if ("DirectionSelectiveFlow".equals(filterClassName) && getEnclosedFilter() != null) {
            getEnclosedFilter().resetFilter();
        }
        setXMax(chip.getSizeX());
        setYMax(chip.getSizeY());
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (chip.getNumPixels() > 0) {
            initFilter();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (this.filterEnabled) {
            switch (evt.getPropertyName()) {
                case AEViewer.EVENT_TIMESTAMPS_RESET:
                    resetFilter();
                    break;
                case AEInputStream.EVENT_REWOUND:
                case AEInputStream.EVENT_NON_MONOTONIC_TIMESTAMP:
                case AEInputStream.EVENT_REPOSITIONED:
                    log.info(evt.toString() + ": resetting filter after printing collected statistics if measurement enabled");
                    if (measureAccuracy || measureProcessingTime) {
                        doPrintStatistics();
                    }
                    resetFilter();
                    break;
                case AEViewer.EVENT_FILEOPEN:
                    log.info("File Open");
//                    AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
//                    AEFileInputStream in = (player.getAEInputStream());
//                    in.getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND,this);
//                    in.getSupport().addPropertyChangeListener(AEInputStream.EVENT_NON_MONOTONIC_TIMESTAMP,this);
                    // Treat FileOpen same as a rewind
                    resetFilter();
                    break;
            }
        }
    }

    /**
     * Plots a single motion vector which is the number of pixels per second
     * times scaling. Color vectors by angle to x-axis.
     *
     * @param gl the OpenGL context
     * @param e the event
     */
    protected void drawMotionVector(GL2 gl, MotionOrientationEventInterface e) {
        float[] rgb = null;
        if (useColorForMotionVectors) {
            rgb = motionColor(e);
        } else {
            rgb = new float[]{0, 0, 1};
        }
        gl.glColor3fv(rgb, 0);
        float scale = ppsScale;
        if (ppsScaleDisplayRelativeOFLength && displayGlobalMotion) {
            scale = 100 * ppsScale / motionFlowStatistics.getGlobalMotion().meanGlobalSpeed;
        }
        if (displayVectorsEnabled) {
            gl.glPushMatrix();
            gl.glLineWidth(motionVectorLineWidthPixels);
            // start arrow from event
//        DrawGL.drawVector(gl, e.getX() + .5f, e.getY() + .5f, e.getVelocity().x, e.getVelocity().y, motionVectorLineWidthPixels, ppsScale);
            // center arrow on location, rather that start from event location
            float dx, dy;
            dx = e.getVelocity().x * scale;
            dy = e.getVelocity().y * scale;
            if (displayVectorsAsUnitVectors) {
                float s = 100 * scale / (float) Math.sqrt(dx * dx + dy * dy);
                dx *= s;
                dy *= s;
            }

            float x0 = e.getX() - (dx / 2) + .5f, y0 = e.getY() - (dy / 2) + .5f;
            DrawGL.drawVector(gl, x0, y0, dx, dy, motionVectorLineWidthPixels, 1);
            gl.glPopMatrix();
        }
        if (displayVectorsAsColorDots) {
            gl.glPointSize(motionVectorLineWidthPixels * 5);
            gl.glEnable(GL2.GL_POINT_SMOOTH);
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2f(e.getX(), e.getY());
            gl.glEnd();
        }
    }

    protected float[] motionColor(float angle) {
        return motionColor((float) Math.cos(angle), (float) Math.sin(angle), 1, 1);
    }

    protected float[] motionColor(float angle, float saturation, float brightness) {
        return motionColor((float) Math.cos(angle), (float) Math.sin(angle), saturation, brightness);
    }

    protected float[] motionColor(MotionOrientationEventInterface e1) {
        return motionColor(e1.getVelocity().x, e1.getVelocity().y, 1, .5f); // use brightness 0.5 to match color wheel and render full gamut
    }

    protected float[] motionColor(float x, float y, float saturation, float brightness) {
        float angle01 = (float) (Math.atan2(y, x) / (2 * Math.PI) + 0.5);
        // atan2 returns -pi to +pi, so dividing by 2*pi gives -.5 to +.5. Adding .5 gives range 0 to 1.
//                    angle01=.5f; // debug
        int rgbValue = Color.HSBtoRGB(angle01, saturation, brightness);
        Color color = new Color(rgbValue);
        float[] rgb = color.getRGBComponents(null);
        return rgb;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }

        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }

        checkBlend(gl);

        if (isDisplayGlobalMotion()) {
            gl.glLineWidth(4f);
            gl.glColor3f(1, 1, 1);

            // Draw global translation vector
            gl.glPushMatrix();
            DrawGL.drawVector(gl, sizex / 2, sizey / 2,
                    motionFlowStatistics.getGlobalMotion().meanGlobalVx,
                    motionFlowStatistics.getGlobalMotion().meanGlobalVy,
                    4, ppsScale * GLOBAL_MOTION_DRAWING_SCALE);
            gl.glRasterPos2i(2, 10);
            String flowMagPps = engFmt.format(motionFlowStatistics.getGlobalMotion().meanGlobalTrans);
            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_18,
                    String.format("glob. trans.=%s pps (local: %s)", flowMagPps, ppsScaleDisplayRelativeOFLength ? "rel." : "abs."));
            gl.glPopMatrix();
//            System.out.println(String.format("%5.3f\t%5.2f",ts*1e-6f, motionFlowStatistics.getGlobalMotion().meanGlobalTrans));  // debug

            // draw quartiles statistics ellipse
            gl.glPushMatrix();
            gl.glTranslatef(sizex / 2 + motionFlowStatistics.getGlobalMotion().meanGlobalVx * ppsScale,
                    sizey / 2 + motionFlowStatistics.getGlobalMotion().meanGlobalVy * ppsScale,
                    0);
            DrawGL.drawEllipse(gl, 0, 0, (float) motionFlowStatistics.getGlobalMotion().sdGlobalVx * ppsScale,
                    (float) motionFlowStatistics.getGlobalMotion().sdGlobalVy * ppsScale,
                    0, 16);
            gl.glPopMatrix();

// Draw global rotation vector as line left/right
            gl.glPushMatrix();
            DrawGL.drawLine(gl,
                    sizex / 2,
                    sizey * 3 / 4,
                    (float) (-motionFlowStatistics.getGlobalMotion().getGlobalRotation().getMean()),
                    0, ppsScale * GLOBAL_MOTION_DRAWING_SCALE);
            gl.glPopMatrix();

            // Draw global expansion as circle with radius proportional to 
            // expansion metric, smaller for contraction, larger for expansion
            gl.glPushMatrix();
            DrawGL.drawCircle(gl, sizex / 2, sizey / 2, ppsScale * GLOBAL_MOTION_DRAWING_SCALE
                    * (1 + motionFlowStatistics.getGlobalMotion().meanGlobalExpansion), 15);
            gl.glPopMatrix();

            // draw scale bar vector at bottom
            gl.glPushMatrix();
            final float speed = motionFlowStatistics.getGlobalMotion().meanGlobalSpeed;
            DvsMotionOrientationEvent e = new DvsMotionOrientationEvent();
            e.setVelocity(speed, 0);
            final int px = 10, py = -3;

            e.setX((short) px);
            e.setY((short) py);
            drawMotionVector(gl, e);
//            DrawGL.drawVector(gl, 10, -3, speed, 0, 4, ppsScale);
            gl.glRasterPos2f(px + 100 * ppsScale, py); // use same scaling
            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("%.1f pps avg. speed and OF vector scale", speed));
            gl.glPopMatrix();

        }

        // Draw individual motion vectors
        if (dirPacket != null && (displayVectorsEnabled || displayVectorsAsColorDots)) {
            gl.glLineWidth(2f);
//            boolean timeoutEnabled = dirPacket.isTimeLimitEnabled();
//            dirPacket.setTimeLimitEnabled(false);
            for (Object o : dirPacket) {
                MotionOrientationEventInterface ei = (MotionOrientationEventInterface) o;
                // If we passAllEvents then the check is needed to not annotate 
                // the events without a real direction.
                if (ei.isHasDirection()) {
                    drawMotionVector(gl, ei);
                } else if (displayZeroLengthVectorsEnabled) {
                    gl.glPushMatrix();
                    gl.glTranslatef(ei.getX(), ei.getY(), 0);
                    gl.glPointSize(motionVectorLineWidthPixels * 2);
                    gl.glColor3f(1, 1, 1);
                    gl.glBegin(GL.GL_POINTS);
                    gl.glVertex2i(0, 0);
                    gl.glEnd();
                    gl.glPopMatrix();
                }
            }
//            dirPacket.setTimeLimitEnabled(timeoutEnabled);
        }

        if (displayColorWheelLegend) {
            final int segments = 16;
            final float scale = 15;
            gl.glPushMatrix();
            gl.glTranslatef(-20, chip.getSizeY() / 2, 0);
            gl.glScalef(scale, scale, 1);
            for (float val01 = 0; val01 < 1; val01 += 1f / segments) {
                float[] rgb = motionColor((float) ((val01 - .5f) * 2 * Math.PI), 1f, .5f);
                gl.glColor3fv(rgb, 0);
//                gl.glLineWidth(motionVectorLineWidthPixels);
//                final double angleRad = 2*Math.PI*(val01-.5f);
//                DrawGL.drawVector(gl, 0,0,(float)Math.cos(angleRad), (float)Math.sin(angleRad),.3f,2);
                final float angle0 = (val01 - .5f) * 2 * (float) Math.PI;
                final float angle1 = ((val01 - .5f) + 1f / segments) * 2 * (float) Math.PI;
                gl.glBegin(GL.GL_TRIANGLES);
                gl.glColor4f(rgb[0], rgb[1], rgb[2], 1f);
                gl.glVertex3d(0, 0, 0);
                gl.glVertex3d(Math.cos(angle0), Math.sin(angle0), 0);
                gl.glVertex3d(Math.cos(angle1), Math.sin(angle1), 0);
                gl.glEnd();
            }
            gl.glPopMatrix();
        }

        gl.glLineWidth(2f);
        gl.glColor3f(1, 1, 1);

        // Display statistics
        if (measureProcessingTime) {
            gl.glPushMatrix();
            gl.glRasterPos2i(chip.getSizeX(), 0);
            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_18,
                    String.format("%4.2f +/- %5.2f us", new Object[]{
                motionFlowStatistics.processingTime.getMean(),
                motionFlowStatistics.processingTime.getStandardDeviation()}));
            gl.glPopMatrix();
        }

        if (measureAccuracy) {
            gl.glPushMatrix();
            final int offset = -10;
            gl.glRasterPos2i(chip.getSizeX() / 2, offset);
            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_18,
                    motionFlowStatistics.endpointErrorAbs.graphicsString("AEE(abs):", "pps"));
            gl.glPopMatrix();
            gl.glPushMatrix();
            gl.glRasterPos2i(chip.getSizeX() / 2, 2 * offset);
            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_18,
                    motionFlowStatistics.endpointErrorRel.graphicsString("AEE(rel):", "%"));
            gl.glPopMatrix();
            gl.glPushMatrix();
            gl.glRasterPos2i(chip.getSizeX() / 2, 3 * offset);
            chip.getCanvas().getGlut().glutBitmapString(GLUT.BITMAP_HELVETICA_18,
                    motionFlowStatistics.angularError.graphicsString("AAE:", "deg"));
            gl.glPopMatrix();
        }

        motionField.draw(gl);

    }

    synchronized public void setupFilter(EventPacket in) {
        maybeAddListeners(chip);
        inItr = in.iterator();
        outItr = dirPacket.outputIterator();
        subsampledPixelIsSet = new boolean[subSizeX][subSizeY];
        countIn = 0;
        countOut = 0;
        if (measureProcessingTime) {
            motionFlowStatistics.processingTime.startTime = System.nanoTime();
        }
        motionField.checkArrays();
        getEnclosedFilterChain().filterPacket(in);
    }

    /**
     * @return true if ... 1) the event lies outside the chip. 2) the event's
     * subsampled address falls on a pixel location that has already had an
     * event within this packet. We don't want to process or render it.
     * Important: This prevents events to acccumulate at the same pixel
     * location, which is an essential part of the Lucas-Kanade method.
     * Therefore, subsampling should be avoided in LucasKanadeFlow when the goal
     * is to optimize accuracy.
     * @param d equals the spatial search distance plus some extra spacing
     * needed for applying finite differences to calculate gradients.
     */
    protected synchronized boolean isInvalidAddress(int d) {
        if (x >= d && y >= d && x < subSizeX - d && y < subSizeY - d) {
            if (subSampleShift > 0 && !subsampledPixelIsSet[x][y]) {
                subsampledPixelIsSet[x][y] = true;
            }
            return false;
        }
        return true;
    }

    // Returns true if the event lies outside certain spatial bounds.
    protected boolean xyFilter() {
        return x < xMin || x >= xMax || y < yMin || y >= yMax;
    }

    /**
     * returns true if timestamp is invalid
     *
     * @return true if invalid timestamp, older than refractoryPeriodUs ago
     */
    protected synchronized boolean isInvalidTimestamp() {
        lastTs = lastTimesMap[x][y][type];
        lastTimesMap[x][y][type] = ts;
        if (ts < lastTs) {
            log.warning(String.format("invalid timestamp ts=%d < lastTs=%d, resetting filter", ts, lastTs));
            resetFilter(); // For NonMonotonicTimeException.
        }
        return ts < lastTs + refractoryPeriodUs;
    }

    /**
     * extracts the event into to fields e, x,y,ts,type. x and y are in
     * subsampled address space
     *
     * @param ein input event
     * @return true if result is in-bounds, false if not, which can occur when
     * the camera cameraCalibration magnifies the address beyond the sensor
     * coordinates
     */
    protected synchronized boolean extractEventInfo(Object ein) {
        e = (PolarityEvent) ein;
        // If camera calibrated, undistort pixel locations

        x = e.x >> subSampleShift;
        y = e.y >> subSampleShift;
        ts = e.getTimestamp();
        type = e.getPolarity() == PolarityEvent.Polarity.Off ? 0 : 1;
        return true;
    }

    /**
     * Takes output event eout and logs it
     *
     */
    synchronized public void processGoodEvent() {
        // Copy the input event to a new output event and add the computed optical flow properties
        eout = (ApsDvsMotionOrientationEvent) outItr.nextOutput();
        eout.copyFrom(e);
        eout.x = (short) (x << subSampleShift);
        eout.y = (short) (y << subSampleShift);
        eout.velocity.x = vx;
        eout.velocity.y = vy;
        eout.speed = v;
        eout.hasDirection = v != 0;
        if (v != 0) {
            countOut++;
        }
        if (measureAccuracy) {
            vxGT = imuFlowEstimator.getVx();
            vyGT = imuFlowEstimator.getVy();
            vGT = imuFlowEstimator.getV();
            getMotionFlowStatistics().update(vx, vy, v, vxGT, vyGT, vGT);
        }

        if (displayGlobalMotion || ppsScaleDisplayRelativeOFLength) {
            motionFlowStatistics.getGlobalMotion().update(vx, vy, v, eout.x, eout.y);
        }
        if (motionVectorEventLogger != null && motionVectorEventLogger.isEnabled()) {
            String s = String.format("%d %d %d %d %.3g %.3g %.3g %d", eout.timestamp, eout.x, eout.y, eout.type, eout.velocity.x, eout.velocity.y, eout.speed, eout.hasDirection ? 1 : 0);
            motionVectorEventLogger.log(s);
        }
        motionField.update(ts, x, y, vx, vy, v);
    }

    /**
     * Returns true if motion event passes accuracy tests
     *
     * @return true if event is accurate enough, false if it should be rejected.
     */
    synchronized public boolean accuracyTests() {
        // 1.) Filter out events with speed high above average.
        // 2.) Filter out events whose velocity deviates from IMU estimate by a 
        // certain degree.
        return speedControlEnabled && isSpeeder() || discardOutliersForStatisticalMeasurementEnabled
                && Math.abs(motionFlowStatistics.angularError.calculateError(vx, vy, v, vxGT, vyGT, vGT))
                > discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg;
    }

    // <editor-fold defaultstate="collapsed" desc="Speed Control">
    protected boolean isSpeeder() {
        // Discard events if velocity is too far above average
        avgSpeed = (1 - speedMixingFactor) * avgSpeed + speedMixingFactor * v;
        return v > avgSpeed * excessSpeedRejectFactor;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="IMUCalibration Start and Reset buttons">
    synchronized public void doStartIMUCalibration() {
        imuFlowEstimator.calibrating = true;
        imuFlowEstimator.calibrated = false;
        imuFlowEstimator.panCalibrator.clear();
        imuFlowEstimator.tiltCalibrator.clear();
        imuFlowEstimator.rollCalibrator.clear();
        if (measureAccuracy) {
            log.info("IMU calibration started");
        } else {
            log.warning("IMU calibration flagged, but will not start until measureAccuracy is selected");
        }
    }

    synchronized public void doEraseIMUCalibration() {
        imuFlowEstimator.eraseIMUCalibration();
    }
    // </editor-fold>

    WarningDialogWithDontShowPreference imuWarningDialog;

    // <editor-fold defaultstate="collapsed" desc="Statistics logging trigger button">
    synchronized public void doPrintStatistics() {
        log.log(Level.INFO, "{0}\n{1}", new Object[]{this.getClass().getSimpleName(), motionFlowStatistics.toString()});
        if (!imuFlowEstimator.isCalibrationSet()) {
            log.warning("IMU has not been calibrated yet! Load a file with no camera motion and hit the StartIMUCalibration button");

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (imuWarningDialog != null) {
                        imuWarningDialog.setVisible(false);
                        imuWarningDialog.dispose();
                    }
                    imuWarningDialog = new WarningDialogWithDontShowPreference(null, false, "Uncalibrated IMU",
                            "<html>IMU has not been calibrated yet! <p>Load a file with no camera motion and hit the StartIMUCalibration button");
                    imuWarningDialog.setVisible(true);

                }
            });
        } else if (imuWarningDialog != null) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (imuWarningDialog != null) {
                        imuWarningDialog.setVisible(false);
                        imuWarningDialog.dispose();
                    }
                }
            });
        }
    }
    // </editor-fold>

    synchronized public void doStartLoggingMotionVectorEvents() {
        if (motionVectorEventLogger != null && motionVectorEventLogger.isEnabled()) {
            log.info("logging already started");
            return;
        }
        String filename = null, filepath = null;
        final JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(new File(getString("lastFile", System.getProperty("user.dir"))));  // defaults to startup runtime folder
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setSelectedFile(new File(getString("lastFile", System.getProperty("user.dir"))));
        fc.setDialogTitle("Select folder and base file name for the logged motion vector event data");
        int ret = fc.showOpenDialog(chip.getAeViewer() != null && chip.getAeViewer().getFilterFrame() != null ? chip.getAeViewer().getFilterFrame() : null);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            putString("lastFile", file.toString());
            motionVectorEventLogger = new TobiLogger(file.getPath(), "Motion vector events output from normal optical flow method");
            motionVectorEventLogger.setNanotimeEnabled(false);
            motionVectorEventLogger.setHeaderLine("system_time(ms) timestamp(us) x y type vx(pps) vy(pps) speed(pps) validity");
            motionVectorEventLogger.setEnabled(true);
        } else {
            log.info("Cancelled logging motion vectors");
        }
    }

    synchronized public void doStopLoggingMotionVectorEvents() {
        if (motionVectorEventLogger == null) {
            return;
        }
        motionVectorEventLogger.setEnabled(false);
        motionVectorEventLogger = null;
    }

    protected void logMotionVectorEvents(EventPacket ep) {
        if (motionVectorEventLogger == null) {
            return;
        }

        for (Object o : ep) {

        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --speedControlEnabled--">
    public boolean isSpeedControlEnabled() {
        return speedControlEnabled;
    }

    public void setSpeedControlEnabled(boolean speedControlEnabled) {
        support.firePropertyChange("speedControlEnabled", this.speedControlEnabled, speedControlEnabled);
        this.speedControlEnabled = speedControlEnabled;
        putBoolean("speedControlEnabled", speedControlEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --speedControl_speedMixingFactor--">
    public float getSpeedControl_SpeedMixingFactor() {
        return speedMixingFactor;
    }

    public void setSpeedControl_SpeedMixingFactor(float speedMixingFactor) {
        if (speedMixingFactor > 1) {
            speedMixingFactor = 1;
        } else if (speedMixingFactor < Float.MIN_VALUE) {
            speedMixingFactor = Float.MIN_VALUE;
        }
        this.speedMixingFactor = speedMixingFactor;
        putFloat("speedMixingFactor", speedMixingFactor);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --speedControl_excessSpeedRejectFactor--">
    public float getSpeedControl_ExcessSpeedRejectFactor() {
        return excessSpeedRejectFactor;
    }

    public void setSpeedControl_ExcessSpeedRejectFactor(float excessSpeedRejectFactor) {
        this.excessSpeedRejectFactor = excessSpeedRejectFactor;
        putFloat("excessSpeedRejectFactor", excessSpeedRejectFactor);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg--">
//    public float getEpsilon() {
//        return discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg;
//    }
//
//    synchronized public void setEpsilon(float discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg) {
//        if (discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg > 180) {
//            discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg = 180;
//        }
//        this.discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg = discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg;
//        putFloat("discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg", discardOutliersForStatisticalMeasurementMaxAngleDifferenceDeg);
//    }
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --discardOutliersForStatisticalMeasurementEnabled--">
//    public boolean getDiscardOutliersEnabled() {
//        return this.discardOutliersForStatisticalMeasurementEnabled;
//    }
//
//    public void setDiscardOutliersEnabled(final boolean discardOutliersForStatisticalMeasurementEnabled) {
//        support.firePropertyChange("discardOutliersForStatisticalMeasurementEnabled", this.discardOutliersForStatisticalMeasurementEnabled, discardOutliersForStatisticalMeasurementEnabled);
//        this.discardOutliersForStatisticalMeasurementEnabled = discardOutliersForStatisticalMeasurementEnabled;
//        putBoolean("discardOutliersForStatisticalMeasurementEnabled", discardOutliersForStatisticalMeasurementEnabled);
//    }
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --loggingFolder--">
    public String getLoggingFolder() {
        return loggingFolder;
    }

    private void setLoggingFolder(String loggingFolder) {
        getSupport().firePropertyChange("loggingFolder", this.loggingFolder, loggingFolder);
        this.loggingFolder = loggingFolder;
        getPrefs().put("DataLogger.loggingFolder", loggingFolder);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --measureAccuracy--">
    public synchronized boolean isMeasureAccuracy() {
        return measureAccuracy;
    }

    public synchronized void setMeasureAccuracy(boolean measureAccuracy) {
        support.firePropertyChange("measureAccuracy", this.measureAccuracy, measureAccuracy);
        this.measureAccuracy = measureAccuracy;
        putBoolean("measureAccuracy", measureAccuracy);
        motionFlowStatistics.setMeasureAccuracy(measureAccuracy);
        if (measureAccuracy) {
            //setMeasureProcessingTime(false);
            resetFilter();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --measureProcessingTime--">
    public synchronized boolean isMeasureProcessingTime() {
        return measureProcessingTime;
    }

    public synchronized void setMeasureProcessingTime(boolean measureProcessingTime) {
        motionFlowStatistics.setMeasureProcessingTime(measureProcessingTime);
        if (measureProcessingTime) {
            setRefractoryPeriodUs(1);
            //support.firePropertyChange("measureAccuracy",this.measureAccuracy,false);
            //this.measureAccuracy = false;
            resetFilter();
            this.measureProcessingTime = measureProcessingTime;
            //motionFlowStatistics.processingTime.openLog(loggingFolder);
        } //else motionFlowStatistics.processingTime.closeLog(loggingFolder,searchDistance);
        support.firePropertyChange("measureProcessingTime", this.measureProcessingTime, measureProcessingTime);
        this.measureProcessingTime = measureProcessingTime;
        putBoolean("measureProcessingTime", measureProcessingTime);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --displayGlobalMotion--">
    public boolean isDisplayGlobalMotion() {
        return displayGlobalMotion;
    }

    public void setDisplayGlobalMotion(boolean displayGlobalMotion) {
        boolean old = this.displayGlobalMotion;
        motionFlowStatistics.setMeasureGlobalMotion(displayGlobalMotion);
        this.displayGlobalMotion = displayGlobalMotion;
        putBoolean("displayGlobalMotion", displayGlobalMotion);
        if (displayGlobalMotion) {
            resetFilter();
        }
        getSupport().firePropertyChange("displayGlobalMotion", old, displayGlobalMotion);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --displayVectorsEnabled--">
    public boolean isDisplayVectorsEnabled() {
        return displayVectorsEnabled;
    }

    public void setDisplayVectorsEnabled(boolean displayVectorsEnabled) {
        boolean old = this.displayVectorsEnabled;
        this.displayVectorsEnabled = displayVectorsEnabled;
        putBoolean("displayVectorsEnabled", displayVectorsEnabled);
        getSupport().firePropertyChange("displayVectorsEnabled", old, displayVectorsEnabled);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ppsScale--">
    public float getPpsScale() {
        return ppsScale;
    }

    /**
     * scale for drawn motion vectors, pixels per second per pixel
     *
     * @param ppsScale
     */
    public void setPpsScale(float ppsScale) {
        float old = this.ppsScale;
        this.ppsScale = ppsScale;
        putFloat("ppsScale", ppsScale);
        getSupport().firePropertyChange("ppsScale", old, this.ppsScale);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --showRawInputEnable--">
    public boolean isDisplayRawInput() {
        return displayRawInput;
    }

    public void setDisplayRawInput(boolean displayRawInput) {
        this.displayRawInput = displayRawInput;
        putBoolean("displayRawInput", displayRawInput);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --subSampleShift--">
    public int getSubSampleShift() {
        return subSampleShift;
    }

    /**
     * Sets the number of spatial bits to subsample events times by. Setting
     * this equal to 1, for example, subsamples into an event time map with
     * halved spatial resolution, aggregating over more space at coarser
     * resolution but increasing the search range by a factor of two at no
     * additional cost
     *
     * @param subSampleShift the number of bits, 0 means no subsampling
     */
    synchronized public void setSubSampleShift(int subSampleShift) {
        if (subSampleShift < 0) {
            subSampleShift = 0;
        } else if (subSampleShift > 4) {
            subSampleShift = 4;
        }
        this.subSampleShift = subSampleShift;
        putInt("subSampleShift", subSampleShift);
        subSizeX = sizex >> subSampleShift;
        subSizeY = sizey >> subSampleShift;
        allocateMap();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --refractoryPeriodUs--">
    public int getRefractoryPeriodUs() {
        return refractoryPeriodUs;
    }

    public void setRefractoryPeriodUs(int refractoryPeriodUs) {
        support.firePropertyChange("refractoryPeriodUs", this.refractoryPeriodUs, refractoryPeriodUs);
        this.refractoryPeriodUs = refractoryPeriodUs;
        putInt("refractoryPeriodUs", refractoryPeriodUs);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --xMin--">
    public int getXMin() {
        return xMin;
    }

    public void setXMin(int xMin) {
        if (xMin > xMax) {
            xMin = xMax;
        }
        this.xMin = xMin;
        putInt("xMin", xMin);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --xMax--">
    public int getXMax() {
        return xMax;
    }

    public void setXMax(int xMax) {
        int old = this.xMax;
        if (xMax > subSizeX) {
            xMax = subSizeX;
        }
        this.xMax = xMax;
        putInt("xMax", xMax);
        getSupport().firePropertyChange("xMax", old, this.xMax);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --yMin--">
    public int getYMin() {
        return yMin;
    }

    public void setYMin(int yMin) {
        if (yMin > yMax) {
            yMin = yMax;
        }
        this.yMin = yMin;
        putInt("yMin", yMin);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --yMax--">
    public int getYMax() {
        return yMax;
    }

    public void setYMax(int yMax) {
        int old = this.yMax;
        if (yMax > subSizeY) {
            yMax = subSizeY;
        }
        this.yMax = yMax;
        putInt("yMax", yMax);
        getSupport().firePropertyChange("yMax", old, this.yMax);
    }
    // </editor-fold>

    /**
     * @return the lensFocalLengthMm
     */
    public float getLensFocalLengthMm() {
        return lensFocalLengthMm;
    }

    /**
     * @param aLensFocalLengthMm the lensFocalLengthMm to set
     */
    public void setLensFocalLengthMm(float aLensFocalLengthMm) {
        lensFocalLengthMm = aLensFocalLengthMm;
        radPerPixel = (float) Math.atan(chip.getPixelWidthUm() / (1000 * lensFocalLengthMm));
    }

    protected boolean outlierMotionFilteringKeepThisEvent(MotionOrientationEventInterface e) {
        if (outlierMotionFilteringLastAngles == null) {
            outlierMotionFilteringLastAngles = new int[chip.getSizeX()][chip.getSizeY()];
        }
        return true;

    }

    /**
     * returns unsigned angle difference that is in range 0-180
     *
     * @param a
     * @param b
     * @return unsigned angle difference that is in range 0-180
     */
    private int angleDiff(int a, int b) {
        int d = Math.abs(a - b) % 360;
        int r = d > 180 ? 360 - d : d;
        return r;
    }

    /**
     * Encapsulates the computed motion field that is estimated from the flow
     * events, with outlier rejection etc.
     *
     */
    protected class MotionField {

        int sx = 0, sy = 0; // size of arrays
        private LowpassFilter3D[][] velocities;
//        private LowpassFilter[][] speeds;
        private int[][] lastTs;
        private int lastDecayTimestamp = Integer.MAX_VALUE;
        private int motionFieldSubsamplingShift = getInt("motionFieldSubsamplingShift", 3);
//        private float motionFieldMixingFactor = getFloat("motionFieldMixingFactor", 1e-1f);
        private int motionFieldTimeConstantMs = getInt("motionFieldTimeConstantMs", 100);
        private int maxAgeUs = getInt("motionFieldMaxAgeUs", 100000);
        private float minSpeedPpsToDrawMotionField = getFloat("minVelocityPps", 1);
        private boolean consistentWithNeighbors = getBoolean("motionFieldConsistentWithNeighbors", false);
        private boolean consistentWithCurrentAngle = getBoolean("motionFieldConsistentWithCurrentAngle", false);
        private boolean displayMotionField = getBoolean("displayMotionField", false);
        private boolean displayMotionFieldColorBlobs = getBoolean("displayMotionFieldColorBlobs", false);
        ;
        private boolean displayMotionFieldUsingColor = getBoolean("displayMotionFieldUsingColor", true);
        ;
        private boolean motionFieldDiffusionEnabled = getBoolean("motionFieldDiffusionEnabled", false);
        private boolean decayTowardsZeroPeridiclly = getBoolean("decayTowardsZeroPeridiclly", false);

        public MotionField() {
        }

        public void checkArrays() {
            if (chip.getNumPixels() == 0) {
                return;
            }

            int newsx = (chip.getSizeX() >> motionFieldSubsamplingShift);
            int newsy = (chip.getSizeY() >> motionFieldSubsamplingShift);

            if (newsx == 0 || newsy == 0) {
                return; // not yet
            }
            if (sx == 0 || sy == 0 || sx != newsx || sy != newsy || lastTs == null || lastTs.length != sx
                    || velocities == null || velocities.length != sx) {
                sx = newsx;
                sy = newsy;
                lastTs = new int[sx][sy];
                velocities = new LowpassFilter3D[sx][sy];
//                speeds = new LowpassFilter[sx][sy];
                for (x = 0; x < sx; x++) {
                    for (y = 0; y < sy; y++) {
                        velocities[x][y] = new LowpassFilter3D(motionFieldTimeConstantMs);
//                    speeds[x][y]=new LowpassFilter(motionFieldTimeConstantMs);
                    }
                }
            }
        }

        public void reset() {
            if (chip.getNumPixels() == 0) {
                return;
            }
            lastDecayTimestamp = Integer.MIN_VALUE;

            checkArrays();
            for (int[] a : lastTs) {
                Arrays.fill(a, Integer.MAX_VALUE);
            }
            for (LowpassFilter3D[] a : velocities) {
                for (LowpassFilter3D f : a) {
                    f.reset();
                    f.setInitialized(true); // start out with zero motion
                }
            }
//            for (LowpassFilter[] a : speeds) {
//                for (LowpassFilter f : a) {
//                    f.reset();
//                }
//            }
        }

        /**
         * Decays all values towards zero
         *
         * @param timestamp current time in us
         */
        public void decayAllTowardsZero(int timestamp) {
            for (LowpassFilter3D[] a : velocities) {
                for (LowpassFilter3D f : a) {
                    f.filter(0, 0, 0, timestamp);
                }
            }
        }

        /**
         * updates motion field
         *
         * @param timestamp in us
         * @param x1 location pixel x before subsampling
         * @param y1
         * @param vx flow vx, pps
         * @param vy
         */
        synchronized public void update(int timestamp, int x, int y, float vx, float vy, float speed) {
            if (!displayMotionField) {
                return;
            }
            int dtDecay = timestamp - lastDecayTimestamp;
            if (decayTowardsZeroPeridiclly && dtDecay > motionFieldTimeConstantMs * 1000 || dtDecay < 0) {
                decayAllTowardsZero(timestamp);
                lastDecayTimestamp = timestamp;
            }
            int x1 = x >> motionFieldSubsamplingShift, y1 = y >> motionFieldSubsamplingShift;
            if (x1 < 0 || x1 >= velocities.length || y1 < 0 || y1 >= velocities[0].length) {
                return;
            }
            if (checkConsistent(timestamp, x1, y1, vx, vy)) {
                velocities[x1][y1].filter(vx, vy, speed, timestamp);
                if (motionFieldDiffusionEnabled) {
                    // diffuse by average of neighbors and ourselves
                    int n = 0;
                    float dvx = 0, dvy = 0, dvs = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        int x2 = x1 + dx;
                        if (x2 >= 0 && x2 < velocities.length) {
                            for (int dy = -1; dy <= 1; dy++) {
                                int y2 = y1 + dy;
                                if (dx == 0 && dy == 0) {
                                    continue; // don't count ourselves
                                }
                                if (y2 >= 0 && y2 < velocities[0].length) {
                                    n++;
                                    Point3D p = velocities[x2][y2].getValue3D();
                                    dvx += p.x;
                                    dvy += p.y;
                                    dvs += p.z;
                                }
                            }
                        }
                    }
                    float r = 1f / n; // recip of sum to compute average
                    LowpassFilter3D v = velocities[x1][y1];
                    Point3D c = v.getValue3D();
                    v.setInternalValue3D(.5f * (c.x + r * dvx), .5f * (c.y + r * dvy), .5f * (c.z + r * dvs));
                }
            }
            lastTs[x1][y1] = ts;
        }

        /**
         * Checks if new flow event is consistent sufficiently with motion field
         *
         * @param timestamp in us
         * @param x1 location pixel x after subsampling
         * @param y1
         * @param vx flow vx, pps
         * @param vy
         * @return true if sufficiently consistent
         */
        private boolean checkConsistent(int timestamp, int x1, int y1, float vx, float vy) {
            int dt = timestamp - lastTs[x1][y1];
            if (dt > maxAgeUs) {
                return false;
            }
            boolean thisAngleConsistentWithCurrentAngle = true;
            if (consistentWithCurrentAngle) {
                Point3D p = velocities[x1][y1].getValue3D();
                float dot = vx * p.x + vy * p.y;
                thisAngleConsistentWithCurrentAngle = (dot >= 0);
            }
            boolean thisAngleConsistentWithNeighbors = true;
            if (consistentWithNeighbors) {
                int countConsistent = 0, count = 0;
                final int[] xs = {-1, 0, 1, 0}, ys = {0, 1, 0, -1}; // 4 neigbors
                int nNeighbors = xs.length;
                for (int i = 0; i < nNeighbors; i++) {
                    int x = xs[i], y = ys[i];
                    int x2 = x1 + x, y2 = y1 + y;
                    if (x2 < 0 || x2 >= velocities.length || y2 < 0 || y2 >= velocities[0].length) {
                        continue;
                    }
                    count++;
                    Point3D p = velocities[x2][y2].getValue3D();
                    float dot2 = vx * p.x + vy * p.y;
                    if (dot2 >= 0) {
                        countConsistent++;
                    }
                }
                if (countConsistent > count / 2) {
                    thisAngleConsistentWithNeighbors = true;
                } else {
                    thisAngleConsistentWithNeighbors = false;
                }
            }
            return thisAngleConsistentWithCurrentAngle && thisAngleConsistentWithNeighbors;
        }

        public void draw(GL2 gl) {
            if (!displayMotionField || velocities == null) {
                return;
            }
            try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                gl.glBlendEquation(GL.GL_FUNC_ADD);
            } catch (GLException e) {
                e.printStackTrace();
            }
            float shift = ((1 << motionFieldSubsamplingShift) * .5f);
            final float saturationSpeedScaleInversePixels = ppsScale * 0.1f; // this length of vector in pixels makes full brightness
            for (int ix = 0; ix < sx; ix++) {
                float x = (ix << motionFieldSubsamplingShift) + shift;
                for (int iy = 0; iy < sy; iy++) {
                    final float y = (iy << motionFieldSubsamplingShift) + shift;
                    final int dt = ts - lastTs[ix][iy]; // use last timestamp of any event that is processed by extractEventInfo
                    gl.glColor4f(0, 0, 1, 1f);
                    final float dotRadius = .75f;
                    gl.glRectf(x - dotRadius, y - dotRadius, x + dotRadius, y + dotRadius);
                    if (dt > maxAgeUs || dt < 0) {
                        continue;
                    }
                    final float speed = velocities[ix][iy].getValue3D().z;
                    if (speed < minSpeedPpsToDrawMotionField) {
                        continue;
                    }
                    final Point3D p = velocities[ix][iy].getValue3D();
                    final float vx = p.x, vy = p.y;
                    float brightness = p.z * saturationSpeedScaleInversePixels;
                    if (brightness > 1) {
                        brightness = 1;
                    }
                    // TODO use motionColor()
                    float[] rgb;
                    if (displayMotionFieldUsingColor) {
                        rgb = motionColor(vx, vy, 1, 1);
                    } else {
                        rgb = new float[]{0, 0, 1};
                    }

                    gl.glColor4f(rgb[0], rgb[1], rgb[2], 1f);
                    gl.glLineWidth(motionVectorLineWidthPixels);
//                    gl.glColor4f(angle, 1 - angle, 1 / (1 + 10 * angle), .5f);
                    gl.glPushMatrix();
                    DrawGL.drawVector(gl, x, y, vx * ppsScale, vy * ppsScale, motionVectorLineWidthPixels, 1);
                    gl.glPopMatrix();
                    if (displayMotionFieldColorBlobs) {
                        gl.glColor4f(rgb[0], rgb[1], rgb[2], .01f);
                        final float s = shift / 4;
                        // draw a blurred square showing motion field direction
                        // TODO add brightness to show magnitude somehow
                        for (float dxx = -shift; dxx < shift; dxx += s) {
                            for (float dyy = -shift; dyy < shift; dyy += s) {
                                gl.glRectf(x - shift + dxx, y - shift + dyy, x + shift + dxx, y + shift + dyy);
                            }
                        }
                    }
                }
            }

        }

        private boolean isDisplayMotionField() {
            return displayMotionField;
        }

        private void setDisplayMotionField(boolean yes) {
            displayMotionField = yes;
            putBoolean("displayMotionField", yes);
        }

        /**
         * @return the motionFieldSubsamplingShift
         */
        public int getMotionFieldSubsamplingShift() {
            return motionFieldSubsamplingShift;
        }

        /**
         * @param motionFieldSubsamplingShift the motionFieldSubsamplingShift to
         * set
         */
        synchronized public void setMotionFieldSubsamplingShift(int motionFieldSubsamplingShift) {
            if (motionFieldSubsamplingShift < 0) {
                motionFieldSubsamplingShift = 0;
            } else if (motionFieldSubsamplingShift > 5) {
                motionFieldSubsamplingShift = 5;
            }
            this.motionFieldSubsamplingShift = motionFieldSubsamplingShift;
            putInt("motionFieldSubsamplingShift", motionFieldSubsamplingShift);
            reset();
        }

//        /**
//         * @return the motionFieldMixingFactor
//         */
//        public float getMotionFieldMixingFactor() {
//            return motionFieldMixingFactor;
//        }
//
//        /**
//         * @param motionFieldMixingFactor the motionFieldMixingFactor to set
//         */
//        public void setMotionFieldMixingFactor(float motionFieldMixingFactor) {
//            if (motionFieldMixingFactor < 1e-6f) {
//                this.motionFieldMixingFactor = 1e-6f;
//            } else if (motionFieldMixingFactor > 1) {
//                this.motionFieldMixingFactor = 1;
//            }
//            this.motionFieldMixingFactor = motionFieldMixingFactor;
//            putFloat("motionFieldMixingFactor", motionFieldMixingFactor);
//        }
        /**
         * @return the maxAgeUs
         */
        public int getMaxAgeUs() {
            return maxAgeUs;
        }

        /**
         * @param maxAgeUs the maxAgeUs to set
         */
        public void setMaxAgeUs(int maxAgeUs) {
            this.maxAgeUs = maxAgeUs;
            putInt("motionFieldMaxAgeUs", maxAgeUs);
        }

        /**
         * @return the consistentWithNeighbors
         */
        public boolean isConsistentWithNeighbors() {
            return consistentWithNeighbors;
        }

        /**
         * @param consistentWithNeighbors the consistentWithNeighbors to set
         */
        public void setConsistentWithNeighbors(boolean consistentWithNeighbors) {
            this.consistentWithNeighbors = consistentWithNeighbors;
            putBoolean("motionFieldConsistentWithNeighbors", consistentWithNeighbors);
        }

        /**
         * @return the consistentWithCurrentAngle
         */
        public boolean isConsistentWithCurrentAngle() {
            return consistentWithCurrentAngle;
        }

        /**
         * @param consistentWithCurrentAngle the consistentWithCurrentAngle to
         * set
         */
        public void setConsistentWithCurrentAngle(boolean consistentWithCurrentAngle) {
            this.consistentWithCurrentAngle = consistentWithCurrentAngle;
            putBoolean("motionFieldConsistentWithCurrentAngle", consistentWithCurrentAngle);
        }

        /**
         * @return the minSpeedPpsToDrawMotionField
         */
        public float getMinSpeedPpsToDrawMotionField() {
            return minSpeedPpsToDrawMotionField;
        }

        /**
         * @param minSpeedPpsToDrawMotionField the minSpeedPpsToDrawMotionField
         * to set
         */
        public void setMinSpeedPpsToDrawMotionField(float minSpeedPpsToDrawMotionField) {
            this.minSpeedPpsToDrawMotionField = minSpeedPpsToDrawMotionField;
            putFloat("minSpeedPpsToDrawMotionField", minSpeedPpsToDrawMotionField);
        }

        /**
         * @return the motionFieldTimeConstantMs
         */
        public int getMotionFieldTimeConstantMs() {
            return motionFieldTimeConstantMs;
        }

        /**
         * @param motionFieldTimeConstantMs the motionFieldTimeConstantMs to set
         */
        public void setMotionFieldTimeConstantMs(int motionFieldTimeConstantMs) {
            this.motionFieldTimeConstantMs = motionFieldTimeConstantMs;
            putInt("motionFieldTimeConstantMs", motionFieldTimeConstantMs);
            setTimeConstant(motionFieldTimeConstantMs);
        }

        private void setTimeConstant(int motionFieldTimeConstantMs) {
            if (sx == 0 || sy == 0) {
                return;
            }
            for (LowpassFilter3D[] a : velocities) {
                for (LowpassFilter3D f : a) {
                    f.setTauMs(motionFieldTimeConstantMs);
                }
            }
//            for (LowpassFilter[] a : speeds) {
//                for (LowpassFilter f : a) {
//                    f.setTauMs(motionFieldTimeConstantMs);
//                }
//            }
        }

        /**
         * @return the displayMotionFieldColorBlobs
         */
        public boolean isDisplayMotionFieldColorBlobs() {
            return displayMotionFieldColorBlobs;
        }

        /**
         * @param displayMotionFieldColorBlobs the displayMotionFieldColorBlobs
         * to set
         */
        public void setDisplayMotionFieldColorBlobs(boolean displayMotionFieldColorBlobs) {
            this.displayMotionFieldColorBlobs = displayMotionFieldColorBlobs;
            putBoolean("displayMotionFieldColorBlobs", displayMotionFieldColorBlobs);
        }

        /**
         * @return the displayMotionFieldUsingColor
         */
        public boolean isDisplayMotionFieldUsingColor() {
            return displayMotionFieldUsingColor;
        }

        /**
         * @param displayMotionFieldUsingColor the displayMotionFieldUsingColor
         * to set
         */
        public void setDisplayMotionFieldUsingColor(boolean displayMotionFieldUsingColor) {
            this.displayMotionFieldUsingColor = displayMotionFieldUsingColor;
            putBoolean("displayMotionFieldUsingColor", displayMotionFieldUsingColor);
        }

        /**
         * @return the motionFieldDiffusionEnabled
         */
        public boolean isMotionFieldDiffusionEnabled() {
            return motionFieldDiffusionEnabled;
        }

        /**
         * @param motionFieldDiffusionEnabled the motionFieldDiffusionEnabled to
         * set
         */
        public void setMotionFieldDiffusionEnabled(boolean motionFieldDiffusionEnabled) {
            this.motionFieldDiffusionEnabled = motionFieldDiffusionEnabled;
            putBoolean("motionFieldDiffusionEnabled", motionFieldDiffusionEnabled);
        }

        /**
         * @return the decayTowardsZeroPeridiclly
         */
        public boolean isDecayTowardsZeroPeridiclly() {
            return decayTowardsZeroPeridiclly;
        }

        /**
         * @param decayTowardsZeroPeridiclly the decayTowardsZeroPeridiclly to
         * set
         */
        public void setDecayTowardsZeroPeridiclly(boolean decayTowardsZeroPeridiclly) {
            this.decayTowardsZeroPeridiclly = decayTowardsZeroPeridiclly;
            putBoolean("decayTowardsZeroPeridiclly", decayTowardsZeroPeridiclly);
        }

    } // MotionField

    public boolean isDisplayMotionField() {
        return motionField.isDisplayMotionField();
    }

    public void setDisplayMotionField(boolean yes) {
        motionField.setDisplayMotionField(yes);
    }

    public int getMotionFieldSubsamplingShift() {
        return motionField.getMotionFieldSubsamplingShift();
    }

    public void setMotionFieldSubsamplingShift(int motionFieldSubsamplingShift) {
        motionField.setMotionFieldSubsamplingShift(motionFieldSubsamplingShift);
    }

//    public float getMotionFieldMixingFactor() {
//        return motionField.getMotionFieldMixingFactor();
//    }
//
//    public void setMotionFieldMixingFactor(float motionFieldMixingFactor) {
//        motionField.setMotionFieldMixingFactor(motionFieldMixingFactor);
//    }
    public int getMotionFieldTimeConstantMs() {
        return motionField.getMotionFieldTimeConstantMs();
    }

    public void setMotionFieldTimeConstantMs(int motionFieldTimeConstantMs) {
        motionField.setMotionFieldTimeConstantMs(motionFieldTimeConstantMs);
    }

    public int getMaxAgeUs() {
        return motionField.getMaxAgeUs();
    }

    public void setMaxAgeUs(int maxAgeUs) {
        motionField.setMaxAgeUs(maxAgeUs);
    }

    public boolean isConsistentWithNeighbors() {
        return motionField.isConsistentWithNeighbors();
    }

    public void setConsistentWithNeighbors(boolean consistentWithNeighbors) {
        motionField.setConsistentWithNeighbors(consistentWithNeighbors);
    }

    public boolean isConsistentWithCurrentAngle() {
        return motionField.isConsistentWithCurrentAngle();
    }

    public void setConsistentWithCurrentAngle(boolean consistentWithCurrentAngle) {
        motionField.setConsistentWithCurrentAngle(consistentWithCurrentAngle);
    }

    public float getMinSpeedPpsToDrawMotionField() {
        return motionField.getMinSpeedPpsToDrawMotionField();
    }

    public void setMinSpeedPpsToDrawMotionField(float minSpeedPpsToDrawMotionField) {
        motionField.setMinSpeedPpsToDrawMotionField(minSpeedPpsToDrawMotionField);
    }

    /**
     * Returns the object holding flow statistics.
     *
     * @return the motionFlowStatistics
     */
    public MotionFlowStatistics getMotionFlowStatistics() {
        return motionFlowStatistics;
    }

    public int getCalibrationSamples() {
        return imuFlowEstimator.getCalibrationSamples();
    }

    public void setCalibrationSamples(int calibrationSamples) {
        imuFlowEstimator.setCalibrationSamples(calibrationSamples);
    }

    public boolean isDisplayColorWheelLegend() {
        return this.displayColorWheelLegend;
    }

    public void setDisplayColorWheelLegend(boolean displayColorWheelLegend) {
        this.displayColorWheelLegend = displayColorWheelLegend;
        putBoolean("displayColorWheelLegend", displayColorWheelLegend);
    }

    /**
     * @return the motionVectorLineWidthPixels
     */
    public float getMotionVectorLineWidthPixels() {
        return motionVectorLineWidthPixels;
    }

    /**
     * @param motionVectorLineWidthPixels the motionVectorLineWidthPixels to set
     */
    public void setMotionVectorLineWidthPixels(float motionVectorLineWidthPixels) {
        if (motionVectorLineWidthPixels < .1f) {
            motionVectorLineWidthPixels = .1f;
        } else if (motionVectorLineWidthPixels > 10) {
            motionVectorLineWidthPixels = 10;
        }
        this.motionVectorLineWidthPixels = motionVectorLineWidthPixels;
        putFloat("motionVectorLineWidthPixels", motionVectorLineWidthPixels);
    }

    /**
     * @return the displayZeroLengthVectorsEnabled
     */
    public boolean isDisplayZeroLengthVectorsEnabled() {
        return displayZeroLengthVectorsEnabled;
    }

    /**
     * @param displayZeroLengthVectorsEnabled the
     * displayZeroLengthVectorsEnabled to set
     */
    public void setDisplayZeroLengthVectorsEnabled(boolean displayZeroLengthVectorsEnabled) {
        this.displayZeroLengthVectorsEnabled = displayZeroLengthVectorsEnabled;
        putBoolean("displayZeroLengthVectorsEnabled", displayZeroLengthVectorsEnabled);
    }

//    /**
//     * @return the outlierMotionFilteringEnabled
//     */
//    public boolean isOutlierMotionFilteringEnabled() {
//        return outlierMotionFilteringEnabled;
//    }
//
//    /**
//     * @param outlierMotionFilteringEnabled the outlierMotionFilteringEnabled to
//     * set
//     */
//    public void setOutlierMotionFilteringEnabled(boolean outlierMotionFilteringEnabled) {
//        this.outlierMotionFilteringEnabled = outlierMotionFilteringEnabled;
//    }
    /**
     * @return the displayVectorsAsColorDots
     */
    public boolean isDisplayVectorsAsColorDots() {
        return displayVectorsAsColorDots;
    }

    /**
     * @param displayVectorsAsColorDots the displayVectorsAsColorDots to set
     */
    public void setDisplayVectorsAsColorDots(boolean displayVectorsAsColorDots) {
        this.displayVectorsAsColorDots = displayVectorsAsColorDots;
        putBoolean("displayVectorsAsColorDots", displayVectorsAsColorDots);
    }

    public boolean isDisplayMotionFieldColorBlobs() {
        return motionField.isDisplayMotionFieldColorBlobs();
    }

    public void setDisplayMotionFieldColorBlobs(boolean displayMotionFieldColorBlobs) {
        motionField.setDisplayMotionFieldColorBlobs(displayMotionFieldColorBlobs);
    }

    public boolean isDisplayMotionFieldUsingColor() {
        return motionField.isDisplayMotionFieldUsingColor();
    }

    public void setDisplayMotionFieldUsingColor(boolean displayMotionFieldUsingColor) {
        motionField.setDisplayMotionFieldUsingColor(displayMotionFieldUsingColor);
    }

    public boolean isMotionFieldDiffusionEnabled() {
        return motionField.isMotionFieldDiffusionEnabled();
    }

    public void setMotionFieldDiffusionEnabled(boolean motionFieldDiffusionEnabled) {
        motionField.setMotionFieldDiffusionEnabled(motionFieldDiffusionEnabled);
    }

    public boolean isDecayTowardsZeroPeridiclly() {
        return motionField.isDecayTowardsZeroPeridiclly();
    }

    public void setDecayTowardsZeroPeridiclly(boolean decayTowardsZeroPeridiclly) {
        motionField.setDecayTowardsZeroPeridiclly(decayTowardsZeroPeridiclly);
    }

    /**
     * @return the displayVectorsAsUnitVectors
     */
    public boolean isDisplayVectorsAsUnitVectors() {
        return displayVectorsAsUnitVectors;
    }

    /**
     * @param displayVectorsAsUnitVectors the displayVectorsAsUnitVectors to set
     */
    public void setDisplayVectorsAsUnitVectors(boolean displayVectorsAsUnitVectors) {
        this.displayVectorsAsUnitVectors = displayVectorsAsUnitVectors;
        putBoolean("displayVectorsAsUnitVectors", displayVectorsAsUnitVectors);
    }

    /**
     * @return the ppsScaleDisplayRelativeOFLength
     */
    public boolean isPpsScaleDisplayRelativeOFLength() {
        return ppsScaleDisplayRelativeOFLength;
    }

    /**
     * @param ppsScaleDisplayRelativeOFLength the
     * ppsScaleDisplayRelativeOFLength to set
     */
    public void setPpsScaleDisplayRelativeOFLength(boolean ppsScaleDisplayRelativeOFLength) {
        boolean old = this.ppsScaleDisplayRelativeOFLength;
        this.ppsScaleDisplayRelativeOFLength = ppsScaleDisplayRelativeOFLength;
        putBoolean("ppsScaleDisplayRelativeOFLength", ppsScaleDisplayRelativeOFLength);
        getSupport().firePropertyChange("ppsScaleDisplayRelativeOFLength", old, ppsScaleDisplayRelativeOFLength);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (cameraCalibration != null) {
            cameraCalibration.setFilterEnabled(false); // disable camera cameraCalibration; force user to enable it every time
        }
    }

}
