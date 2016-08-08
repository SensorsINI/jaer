/*
 * SceneStabilizer.java (formerly MotionCompensator)
 *
 * Created on March 8, 2006, 9:41 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright 2006-2012 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.filter.HighpassFilter;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;

import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.concurrent.ArrayBlockingQueue;

import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;

/**
 * This "vestibular-ocular Steadicam" tries to compensate global image motion by
 * using vestibular and global motion metrics to redirect output events and
 * (optionally) also a mechanical pan-tilt unit, shifting them according to
 * motion of input. Three methods can be used 1) the global translational flow
 * computed from AbstractDirectionSelectiveFilter, or 2) the optical gyro
 * outputs from OpticalGyro, or 3) the integrated IMU on the camera if
 * available.
 *
 * @author tobi
 */
@Description("Compenstates global scene translation and rotation to stabilize scene like a SteadiCam, using built-in IMU or other methods of estimation of camera rotation.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Steadicam extends EventFilter2DMouseAdaptor implements FrameAnnotater, Observer, PropertyChangeListener {

    private boolean rewindFlg;

    /**
     * Classes that compute camera rotationRad estimate based on scene shift and
     * maybe rotationRad around the center of the scene.
     */
    public enum CameraRotationEstimator {

        VORSensor
    };
    private CameraRotationEstimator cameraRotationEstimator = null; //PositionComputer.valueOf(get("positionComputer", "OpticalGyro"));
//    private float gainTranslation = getFloat("gainTranslation", 1f);
//    private float gainVelocity = getFloat("gainVelocity", 1);
    private float gainPanTiltServos = getFloat("gainPanTiltServos", 1);
    private boolean feedforwardEnabled = getBoolean("feedforwardEnabled", false);
    private boolean panTiltEnabled = getBoolean("panTiltEnabled", false);
    private boolean electronicStabilizationEnabled = getBoolean("electronicStabilizationEnabled", true);
//    private boolean vestibularStabilizationEnabled = getBoolean("vestibularStabilizationEnabled", false);
    private Point2D.Float translation = new Point2D.Float();
    private HighpassFilter filterX = new HighpassFilter(), filterY = new HighpassFilter(), filterRotation = new HighpassFilter();
    private boolean flipContrast = getBoolean("flipContrast", false);
    boolean evenMotion = true;
    private FilterChain filterChain;
    private boolean annotateEnclosedEnabled = getBoolean("annotateEnclosedEnabled", true);
    private PanTilt panTilt = null;
    ArrayList<TransformAtTime> transformList = new ArrayList(); // holds list of transforms over update times commputed by enclosed filter update callbacks
    private TransformAtTime lastTransform = null, imageTransform = null;
//    private double[] angular, acceleration;
    private float panRate = 0, tiltRate = 0, rollRate = 0; // in deg/sec
    private float panOffset = getFloat("panOffset", 0), tiltOffset = getFloat("tiltOffset", 0), rollOffset = getFloat("rollOffset", 0);
//    private float upAccel = 0, rightAccel = 0, zAccel = 0; // in g in m/s^2
    private float panTranslationDeg = 0;
    private float tiltTranslationDeg = 0;
    private float rollDeg = 0;
    private float panDC = 0, tiltDC = 0, rollDC = 0;
    private float lensFocalLengthMm = getFloat("lensFocalLengthMm", 8.5f);
    HighpassFilter panTranslationFilter = new HighpassFilter();
    HighpassFilter tiltTranslationFilter = new HighpassFilter();
    HighpassFilter rollFilter = new HighpassFilter();
    private float highpassTauMsTranslation = getFloat("highpassTauMsTranslation", 1000);
    private float highpassTauMsRotation = getFloat("highpassTauMsRotation", 1000);
    float radPerPixel;
    private volatile boolean resetCalled = false;
    private int lastImuTimestamp = 0;
    private boolean initialized = false;
    private boolean addTimeStampsResetPropertyChangeListener = false;
    private int transformResetLimitDegrees = getInt("transformResetLimitDegrees", 45);
    // deal with leftover IMU data after timestamps reset
    private static final int FLUSH_COUNT = 10;
    private int flushCounter = 0;
    // calibration
    private boolean calibrating = false; // used to flag calibration state
    private int calibrationSampleCount = 0;
    private int NUM_CALIBRATION_SAMPLES_DEFAULT = 800; // 400 samples /sec
    protected int numCalibrationSamples = getInt("numCalibrationSamples", NUM_CALIBRATION_SAMPLES_DEFAULT);
    private CalibrationFilter panCalibrator, tiltCalibrator, rollCalibrator;
    TextRenderer imuTextRenderer = null;
    private boolean showTransformRectangle = getBoolean("showTransformRectangle", true);
    private boolean showGrid = getBoolean("showGrid", true);
    // transform control
    public boolean disableTranslation = getBoolean("disableTranslation", false);
    public boolean disableRotation = getBoolean("disableRotation", false);
    // array size vars, updated in update()
    private int sxm1;
    private int sym1;
    private int sx2, sy2;
    private boolean transformImageEnabled = getBoolean("transformImageEnabled", true);
    private int lastFrameNumber = 0;
    protected float imuLagMs = getFloat("imuLagMs", 1.8f);

    private boolean addedViewerPropertyChangeListener = false;
    ApsDvsEventPacket outputPacket = null;
    private Point centerOfRotation = null;
    private boolean centerOfRotationSelectionPending = false;

    /**
     * Creates a new instance of SceneStabilizer
     */
    public Steadicam(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        chip.addObserver(this); // to get pixel array size updates
        addObserver(this); // we add ourselves as observer so that our update() can be called during packet iteration periodically according to global FilterFrame update interval settting

        try {
            cameraRotationEstimator = CameraRotationEstimator.valueOf(getString("positionComputer", "OpticalGyro"));
        } catch (IllegalArgumentException e) {
            log.warning("bad preference " + getString("positionComputer", "OpticalGyro") + " for preferred PositionComputer, choosing default OpticalGyro");
            cameraRotationEstimator = CameraRotationEstimator.VORSensor;
            putString("positionComputer", "OpticalGyro");
        }

        setCameraRotationEstimator(cameraRotationEstimator); // init filter enabled states
        initFilter(); // init filters for motion compensation
        String transform = "Transform", pantilt = "Pan-Tilt", display = "Display", imu = "IMU";

        setPropertyTooltip("cameraRotationEstimator", "specifies which method is used to measure camera rotation");
//        setPropertyTooltip(pantilt, "gainTranslation", "gain applied to measured scene translation to affect electronic or mechanical output");
//        setPropertyTooltip(pantilt, "gainVelocity", "gain applied to measured scene velocity times the weighted-average cluster aqe to affect electronic or mechanical output");
//        setPropertyTooltip(pantilt, "gainPanTiltServos", "gain applied to translation for pan/tilt servo values");
        setPropertyTooltip("feedforwardEnabled", "enables optical flow motion computation on stabilized output of filter rather than input (only during use of DirectionSelectiveFilter)");
        setPropertyTooltip(pantilt, "panTiltEnabled", "enables use of pan/tilt servos for camera");
        setPropertyTooltip("electronicStabilizationEnabled", "stabilize by shifting events according to the PositionComputer");
        setPropertyTooltip(display, "flipContrast", "flips contrast of output events depending on x*y sign of motion - should maintain colors of edges");
//        setPropertyTooltip("cornerFreqHz", "sets highpass corner frequency in Hz for stabilization - frequencies smaller than this will not be stabilized and transform will return to zero on this time scale");
        setPropertyTooltip(display, "annotateEnclosedEnabled", "showing tracking or motion filter output annotation of output, for setting up parameters of enclosed filters");
        setPropertyTooltip(transform, "opticalGyroTauLowpassMs", "lowpass filter time constant in ms for optical gyro camera rotation measure");
        setPropertyTooltip(transform, "opticalGyroRotationEnabled", "enables rotation in transform");
        setPropertyTooltip(transform, "vestibularStabilizationEnabled", "use the gyro/accelometer to provide transform");
        setPropertyTooltip(imu, "zeroGyro", "zeros the gyro output. Sensor should be stationary for period of 1-2 seconds during zeroing");
        setPropertyTooltip(imu, "eraseGyroZero", "Erases the gyro zero values");
        setPropertyTooltip(imu, "numCalibrationSamples", "Number of calibration samples from IMU to average for offset correction");
        setPropertyTooltip(transform, "transformImageEnabled", "Transforms rendering of the APS image (note that the APS image data is unaffected; this is only for demo purposes)");

//        setPropertyTooltip("sampleIntervalMs", "sensor sample interval in ms, min 4ms, powers of two, e.g. 4,8,16,32...");
        setPropertyTooltip(transform, "highpassTauMsTranslation", "highpass filter time constant in ms to relax transform back to zero for translation (pan, tilt) components");
        setPropertyTooltip(transform, "highpassTauMsRotation", "highpass filter time constant in ms to relax transform back to zero for rotation (roll) component");
        setPropertyTooltip(transform, "lensFocalLengthMm", "sets lens focal length in mm to adjust the scaling from camera rotation to pixel space");
        setPropertyTooltip(imu, "zeroGyro", "zeros the gyro output. Sensor should be stationary for period of 1-2 seconds during zeroing");
        setPropertyTooltip(imu, "eraseGyroZero", "Erases the gyro zero values");
        setPropertyTooltip(transform, "transformResetLimitDegrees", "If transform translations exceed this limit in degrees the transform is automatically reset to 0");
        setPropertyTooltip(display, "showTransformRectangle", "Disable to not show the red transform square and red cross hairs");
        setPropertyTooltip(display, "showGrid", "Enabled to show a grid to allow judging the degree of stabilization");
        setPropertyTooltip(transform, "disableRotation", "Disables rotational part of transform");
        setPropertyTooltip(transform, "disableTranslation", "Disables translations part of transform");
        setPropertyTooltip(transform, "selectCenterOfRotation", "Select during mouse movement the center of rotation (to try out stabilization), then confirm with mouse click on the image the actual center of rotation (COR)");
        setPropertyTooltip(transform, "eraseCenterOfRotationSelection", "Clear center of rotation to reset it back to center of image");
        setPropertyTooltip(imu, "imuLagMs", "absolute delay/lag of IMU in ms; from Invense datasheet this delay is specified as 1.8ms");

        rollFilter.setTauMs(highpassTauMsRotation);
        panTranslationFilter.setTauMs(highpassTauMsTranslation);
        tiltTranslationFilter.setTauMs(highpassTauMsTranslation);
        panCalibrator = new CalibrationFilter();
        tiltCalibrator = new CalibrationFilter();
        rollCalibrator = new CalibrationFilter();

        int corx = getInt("centerOfRotationX", -1);
        int cory = getInt("centerOfRotationY", -1);
        if (corx != -1 && cory != -1) {
            centerOfRotation = new Point(corx, cory);
            log.info("loaded from preferences centerOfRotation=" + centerOfRotation);
        }
        setEnclosedFilterChain(filterChain);

    }

//    int lastImuTs=0; // debug
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
        if (!addedViewerPropertyChangeListener) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().addPropertyChangeListener(this);
//				chip.getAeViewer().getAePlayer().getSupport().addPropertyChangeListener(this); // TODO might be duplicated callback
                addedViewerPropertyChangeListener = true;
            }
        }
        if (!addTimeStampsResetPropertyChangeListener) {
            chip.getAeViewer().addPropertyChangeListener(AEViewer.EVENT_TIMESTAMPS_RESET, this);
            addTimeStampsResetPropertyChangeListener = true;
        }
        if (outputPacket == null) {
            outputPacket = new ApsDvsEventPacket(in.getEventClass());
        }
        transformList.clear(); // empty list of transforms to be applied
        // The call to enclosed filters issues callbacks to us periodically via updates that fills transform list, in case of enclosed filters.
        // this is not the case when using integrated IMU which generates IMUSamples in the event stream.
        getEnclosedFilterChain().filterPacket(in);
//        System.outputPacket.println("new steadicam input packet "+in);
        if (electronicStabilizationEnabled) { // here we stabilize by using the measured camera rotationRad to counter-transform the events
            // transform events in place, no need to copy to output packet
//            checkOutputPacketEventType(in);
//            OutputEventIterator outItr = getOutputPacket().outputIterator();// the transformed events output packet
            // TODO compute evenMotion boolean from opticalGyro
            Iterator<TransformAtTime> transformItr = transformList.iterator(); // this list is filled by the enclosed filters
//            int i=-1;
            sx2 = chip.getSizeX() / 2;
            sy2 = chip.getSizeY() / 2;
            int corx = centerOfRotation == null ? this.sx2 : centerOfRotation.x;
            int cory = centerOfRotation == null ? this.sy2 : centerOfRotation.y;
            sxm1 = chip.getSizeX() - 1;
            sym1 = chip.getSizeY() - 1;

            OutputEventIterator outItr = outputPacket.outputIterator();
            if (!(in instanceof ApsDvsEventPacket)) {
                log.warning("input packet is not an ApsDvsEventPacket, disabling filter");
                setFilterEnabled(false);
                return in;
            }
            ApsDvsEventPacket in2 = (ApsDvsEventPacket) in;
            Iterator itr = in2.fullIterator();
            while (itr.hasNext()) {
                Object o = itr.next();
                if (o == null) {
                    log.warning("null event passed in, returning input packet");
                    return in;
                }
//                i++;

                ApsDvsEvent ev = (ApsDvsEvent) o;
                switch (cameraRotationEstimator) {
                    case VORSensor:
                        if (ev.isImuSample()) {
                            IMUSample s = ev.getImuSample(); // because of imuLagMs this IMU sample should actually be applied to samples from the past
                            // to achieve this backwards application of the IMU samples we hold the older events in a FIFO and pop events from the FIFO until 
                            // the event timestamp catches up to the current IMUSample timestamp - imuLagMs.

//                            System.out.println("steadicam dt IMU="+(s.getTimestampUs()-lastImuTs));
//                            lastImuTs=s.getTimestampUs();
                            lastTransform = updateTransform(s);
                            if (transformImageEnabled && lastTransform != null && chip instanceof DavisChip && chip.getAeViewer() != null && chip.getCanvas() != null && chip.getCanvas().getDisplayMethod() instanceof ChipRendererDisplayMethodRGBA) {
                                DavisChip apsDvsChip = (DavisChip) chip;
                                int frameStartTimestamp = apsDvsChip.getFrameExposureStartTimestampUs();
                                int frameEndTimestamp = apsDvsChip.getFrameExposureEndTimestampUs();
                                int frameCounter = apsDvsChip.getFrameCount();
                                if (frameEndTimestamp >= frameStartTimestamp && lastTransform.timestamp >= frameEndTimestamp && frameCounter > lastFrameNumber) {
                                    // if a frame has been read outputPacket, then save the last transform to apply to rendering this frame
                                    imageTransform = lastTransform;
                                    lastFrameNumber = frameCounter; // only set transfrom once per frame, as soon as we have a tranform for it.
                                    ChipRendererDisplayMethodRGBA displayMethod = (ChipRendererDisplayMethodRGBA) chip.getCanvas().getDisplayMethod(); // TODO not ideal (tobi)
                                    displayMethod.setImageTransform(lastTransform.translationPixels, lastTransform.rotationRad);
                                    // immediately set this to be the transform, assuming that next rendering cycle will draw this new frame
                                }
                            }
                            continue; // next event
                        }

                        break;
                    default:
                        lastTransform = transformItr.next();
                }
                pushEvent(ev);
//                System.outputPacket.print(">");

                ApsDvsEvent be = null;
                while ((be = peekEvent()) != null && (be.timestamp <= ev.timestamp - imuLagMs * 1000 || be.timestamp > ev.timestamp)) {
                    be = popEvent();
//                    System.outputPacket.print("<");
                    if (!(be.isImuSample())) {
                        if (lastTransform != null) {

                            // apply transform Re+T. First center events from middle of array at 0,0, then transform, then move them back to their origin
                            int nx = be.x - corx, ny = be.y - cory;
                            be.x = (short) ((((lastTransform.cosAngle * nx) - (lastTransform.sinAngle * ny)) + lastTransform.translationPixels.x) + corx);
                            be.y = (short) (((lastTransform.sinAngle * nx) + (lastTransform.cosAngle * ny) + lastTransform.translationPixels.y) + cory);
                            be.address = chip.getEventExtractor().getAddressFromCell(be.x, be.y, be.getType()); // so event is logged properly to disk
                        }

                        if ((be.x > sxm1) || (be.x < 0) || (be.y > sym1) || (be.y < 0)) {
                            be.setFilteredOut(true); // TODO this gradually fills the packet with filteredOut events, which are never seen afterwards because the iterator filters them outputPacket in the reused packet.
                            continue; // discard events outside chip limits for now, because we can't render them presently, although they are valid events
                        } else {
                            be.setFilteredOut(false);
                        }
                        // deal with flipping contrast of output event depending on direction of motion, to make things appear the same regardless of camera rotationRad

                        if (flipContrast) {
                            if (evenMotion) {
                                be.type = (byte) (1 - be.type); // don't let contrast flip when direction changes, try to stabilze contrast  by flipping it as well
                                be.polarity = be.polarity == PolarityEvent.Polarity.On ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                            }
                        }
                    }
                    outItr.nextOutput().copyFrom(be);

                }
            } // event iterator
//            if(transformImageEnabled && lastTransform!=null && chip.getAeViewer()!=null && chip.getCanvas()!=null && chip.getCanvas().getDisplayMethod() instanceof ChipRendererDisplayMethodRGBA){
//                ChipRendererDisplayMethodRGBA displayMethod=(ChipRendererDisplayMethodRGBA)chip.getCanvas().getDisplayMethod(); // TODO not ideal (tobi)
//                displayMethod.setImageTransform(lastTransform.translationPixels,lastTransform.rotationRad);
//            }//            if(transformImageEnabled && lastTransform!=null && chip.getAeViewer()!=null && chip.getCanvas()!=null && chip.getCanvas().getDisplayMethod() instanceof ChipRendererDisplayMethodRGBA){
//                ChipRendererDisplayMethodRGBA displayMethod=(ChipRendererDisplayMethodRGBA)chip.getCanvas().getDisplayMethod(); // TODO not ideal (tobi)
//                displayMethod.setImageTransform(lastTransform.translationPixels,lastTransform.rotationRad);
//            }

        if(rewindFlg) {
            initialized = false;
            rewindFlg = false;
        }
        } // electronicStabilizationEnabled

        if (isPanTiltEnabled()) { // mechanical pantilt
            try {
                // mechanical pantilt
                // assume that pan of 1 takes us 180 degrees and that the sensor has 45 deg FOV,
                // then 1 pixel will require only 45/180/size pan
                final float factor = (float) (chip.getPixelWidthUm() / 1000 / lensFocalLengthMm / Math.PI);
                panTilt.setPanTiltValues(.5f - (translation.x * getGainPanTiltServos() * factor), .5f + (translation.y * getGainPanTiltServos() * factor));
            } catch (HardwareInterfaceException ex) {
                log.warning("setting pantilt: " + ex);
                panTilt.close();
            }
        }
        return outputPacket;
    }

    final int INIITAL_QUEUE_SIZE = 10000;
    ArrayBlockingQueue<ApsDvsEvent> eventQueue = new ArrayBlockingQueue<ApsDvsEvent>(INIITAL_QUEUE_SIZE);

    private void pushEvent(ApsDvsEvent ev) {
        ApsDvsEvent ne = new ApsDvsEvent();
        ne.copyFrom(ev);
        if (!eventQueue.offer(ne)) {
            // increase queue size
            ArrayBlockingQueue<ApsDvsEvent> newQueue = new ArrayBlockingQueue<ApsDvsEvent>(eventQueue.size() * 2);
            log.info("increased event queue to " + newQueue.remainingCapacity() + " events");
            newQueue.addAll(eventQueue);
            eventQueue = newQueue;
            eventQueue.offer(ne);
        };
    }

    private ApsDvsEvent popEvent() {
        return eventQueue.poll();
    }

    private ApsDvsEvent peekEvent() {
        return eventQueue.peek();
    }

    /**
     * Called back here during packet iteration to update transform
     *
     * @param o
     * @param arg
     */
    @Override
    public void update(Observable o, Object arg) { // called by enclosed filter to update event stream on the fly, using intermediate data
        if (arg instanceof UpdateMessage) {
            computeTransform((UpdateMessage) arg); // gets the lastTransform from the enclosed filter
        }
    }

    /**
     * Computes transform using current gyro outputs based on timestamp supplied
     * and returns a TransformAtTime object. Should be called by update in
     * enclosing processor.
     *
     * @param timestamp the timestamp in us.
     * @return the transform object representing the camera rotationRad
     */
    synchronized public TransformAtTime updateTransform(IMUSample imuSample) {

        if (resetCalled) {
            log.info("reset called, panDC" + panDC + " panTranslationFilter=" + panTranslationFilter);
            resetCalled = false;
        }
        if (imuSample == null) {
            return null;
        }
        if (flushCounter-- >= 0) {
            return null;  // flush some samples if the timestamps have been reset and we need to discard some samples here
        }//        System.outputPacket.println(imuSample.toString());
        int timestamp = imuSample.getTimestampUs();
        float dtS = (timestamp - lastImuTimestamp) * 1e-6f;
        lastImuTimestamp = timestamp;
        if (!initialized) {
            initialized = true;
            return null;
        }
        panRate = imuSample.getGyroYawY();
        tiltRate = imuSample.getGyroTiltX();
        rollRate = imuSample.getGyroRollZ();
        if (calibrating) {
            calibrationSampleCount++;
            if (calibrationSampleCount > numCalibrationSamples) {
                calibrating = false;
                panOffset = panCalibrator.computeAverage();
                tiltOffset = tiltCalibrator.computeAverage();
                rollOffset = rollCalibrator.computeAverage();
                putFloat("panOffset", panOffset);
                putFloat("tiltOffset", tiltOffset);
                putFloat("rollOffset", rollOffset);
                log.info(String.format("calibration finished. %d samples averaged to (pan,tilt,roll)=(%.3f,%.3f,%.3f)", numCalibrationSamples, panOffset, tiltOffset, rollOffset));
            } else {
                panCalibrator.addSample(panRate);
                tiltCalibrator.addSample(tiltRate);
                rollCalibrator.addSample(rollRate);
            }
            return null;
        }
//        zAccel = imuSample.getAccelZ();
//        upAccel = imuSample.getAccelY();
//        rightAccel = imuSample.getAccelX();

        panDC += getPanRate() * dtS;
        tiltDC += getTiltRate() * dtS;
        rollDC += getRollRate() * dtS;

        panTranslationDeg = panTranslationFilter.filter(panDC, timestamp);
        tiltTranslationDeg = tiltTranslationFilter.filter(tiltDC, timestamp);
        rollDeg = rollFilter.filter(rollDC, timestamp);

        // check limits, make limit for rotationRad a lot higher to avoid reset on big rolls, which are different than pans and tilts
        if ((Math.abs(panTranslationDeg) > transformResetLimitDegrees) || (Math.abs(tiltTranslationDeg) > transformResetLimitDegrees) || (Math.abs(rollDeg) > (transformResetLimitDegrees * 3))) {
            panDC = 0;
            tiltDC = 0;
            rollDC = 0;

            panTranslationDeg = 0;
            tiltTranslationDeg = 0;
            rollDeg = 0;
            panTranslationFilter.reset();
            tiltTranslationFilter.reset();
            rollFilter.reset();
            log.info("transform reset limit reached, transform reset to zero");
        }

        if (flipContrast) {
            if (Math.abs(panRate) > Math.abs(tiltRate)) {
                evenMotion = panRate > 0; // used to flip contrast
            } else {
                evenMotion = tiltRate > 0;
            }
        }

        if (disableRotation) {
            rollDeg = 0;
        }
        if (disableTranslation) {
            panTranslationDeg = 0;
            tiltTranslationDeg = 0;
        }

        // computute transform in TransformAtTime units here.
        // Use the lens focal length and camera resolution.
        TransformAtTime tr = new TransformAtTime(timestamp,
                new Point2D.Float(
                        (float) ((Math.PI / 180) * panTranslationDeg) / radPerPixel,
                        (float) ((Math.PI / 180) * tiltTranslationDeg) / radPerPixel),
                (-rollDeg * (float) Math.PI) / 180);
        return tr;
    }

    // done inline in filterPacket method
//    private final void transformEvent(BasicEvent e, TransformAtTime transform) {
//        int sx2=centerOfRotation==null?this.sx2:centerOfRotation.x;
//        int sy2=centerOfRotation==null?this.sy2:centerOfRotation.y;
//        
//        e.x -= sx2;
//        e.y -= sy2;
//        short newx = (short) Math.round((((transform.cosAngle * e.x) - (transform.sinAngle * e.y)) + transform.translationPixels.x));
//        short newy = (short) Math.round(((transform.sinAngle * e.x) + (transform.cosAngle * e.y) + transform.translationPixels.y));
//        e.x = (short) (newx + sx2);
//        e.y = (short) (newy + sy2);
//        e.address = chip.getEventExtractor().getAddressFromCell(e.x, e.y, e.getType()); // so event is logged properly to disk
//    }
    synchronized public void doEraseGyroZero() {
        panOffset = 0;
        tiltOffset = 0;
        rollOffset = 0;
        putFloat("panOffset", 0);
        putFloat("tiltOffset", 0);
        putFloat("rollOffset", 0);
        log.info("calibration erased");
    }

    synchronized public void doZeroGyro() {
        calibrating = true;
        calibrationSampleCount = 0;
        panCalibrator.reset();
        tiltCalibrator.reset();
        rollCalibrator.reset();
        log.info("calibration started");

//        panOffset = panRate; // TODO offsets should really be some average over some samples
//        tiltOffset = tiltRate;
//        rollOffset = rollRate;
    }

    public void doSelectCenterOfRotation() {
        centerOfRotationSelectionPending = true;
        log.info("select a center point by a mouse click");
    }

    public void doEraseCenterOfRotationSelection() {
        centerOfRotation = null;
        putInt("centerOfRotationX", -1);
        putInt("centerOfRotationY", -1);
    }

    /**
     * Called by update on enclosed filter updates.
     * <p>
     * Using AbstractDirectionSelectiveFilter, the lastTransform is computed by
     * pure integration of the motion signal followed by a high-pass filter to
     * remove long term DC offsets.
     * <p>
     * Using OpticalGyro, the lastTransform is computed by the optical gyro
     * which tracks clusters and measures scene translationPixels (and possibly
     * rotationRad) from a consensus of the tracked clusters.
     * <p>
     * Using PhidgetsVORSensor, lastTransform is computed by PhidgetsVORSensor
     * using rate gyro sensors.
     *
     *
     * @param in the input event packet.
     */
    private void computeTransform(UpdateMessage msg) { // only used in AbstractDirectionSelectiveFilter and OpticalGyro. IMU transform is applied inline in filterPacket
        float shiftx = 0, shifty = 0;
        float rot = 0;
        Point2D.Float trans = new Point2D.Float();
    }

    /**
     * @return the panRate
     */
    public float getPanRate() {
        return panRate - panOffset;
    }

    /**
     * @return the tiltRate
     */
    public float getTiltRate() {
        return tiltRate - tiltOffset;
    }

    /**
     * @return the rollRate
     */
    public float getRollRate() {
        return rollRate - rollOffset;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (calibrating) {
            if (imuTextRenderer == null) {
                imuTextRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
            }
            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1, 1, 1, 1);
            final String saz = String.format("Don't move sensor (Calibrating %d/%d)", calibrationSampleCount, numCalibrationSamples);
            Rectangle2D rect = imuTextRenderer.getBounds(saz);
            final float scale = .25f;
            imuTextRenderer.draw3D(saz, (chip.getSizeX() / 2) - (((float) rect.getWidth() * scale) / 2), chip.getSizeY() / 2, 0, scale); //
            imuTextRenderer.end3DRendering();
        }

        GL2 gl = null;
        if (showGrid || showTransformRectangle) {
            gl = drawable.getGL().getGL2();
        }

        if (gl == null) {
            return;
        }
        if (showTransformRectangle && (lastTransform != null) && isElectronicStabilizationEnabled()) {
            // draw transform
            gl.glPushMatrix();

            gl.glLineWidth(1f);
            gl.glColor3f(1, 0, 0);

            // translate and rotate
            gl.glTranslatef(lastTransform.translationPixels.x + sx2, lastTransform.translationPixels.y + sy2, 0);
            gl.glRotatef((float) ((lastTransform.rotationRad * 180) / Math.PI), 0, 0, 1);

            // draw xhairs on frame to help show locations of objects and if they have moved.
            gl.glBegin(GL.GL_LINES); // sequence of individual segments, in pairs of vertices
            gl.glVertex2f(0, 0);  // start at origin
            gl.glVertex2f(sx2, 0);  // outputPacket to right
            gl.glVertex2f(0, 0);  // origin
            gl.glVertex2f(-sx2, 0); // outputPacket to left
            gl.glVertex2f(0, 0);  // origin
            gl.glVertex2f(0, sy2); // up
            gl.glVertex2f(0, 0);  // origin
            gl.glVertex2f(0, -sy2); // down
            gl.glEnd();

            // rectangle around transform
            gl.glTranslatef(-sx2, -sy2, 0); // lower left corner
            gl.glBegin(GL.GL_LINE_LOOP); // loop of vertices
            gl.glVertex2f(0, 0); // lower left corner
            gl.glVertex2f(sx2 * 2, 0); // lower right
            gl.glVertex2f(2 * sx2, 2 * sy2); // upper right
            gl.glVertex2f(0, 2 * sy2); // upper left
            gl.glVertex2f(0, 0); // back of lower left
            gl.glEnd();
            gl.glPopMatrix();
        }

        if (showGrid) {
            gl.glLineWidth(1f);
            gl.glColor3f(0, 0, 1);
            final int s = chip.getMaxSize() / 8;
            final int n = chip.getMaxSize() / s;
            gl.glBegin(GL.GL_LINES);
            for (int i = 0; i < n; i++) {
                final int x = i * s;
                gl.glVertex2i(x, 0);
                gl.glVertex2i(x, sy2 * 2);
            }
            for (int i = 0; i < n; i++) {
                final int y = i * s;
                gl.glVertex2i(0, y);
                gl.glVertex2i(sx2 * 2, y);
            }
            gl.glEnd();
        }
        
        if(centerOfRotation!=null){
            gl.glLineWidth(4f);
            gl.glColor3f(1, 0, 0);
            final int L=4;

            // draw xhairs on frame to help show locations of objects and if they have moved.
            gl.glBegin(GL.GL_LINES); // sequence of individual segments, in pairs of vertices
            gl.glVertex2f(centerOfRotation.x-L, centerOfRotation.y);
            gl.glVertex2f(centerOfRotation.x+L, centerOfRotation.y);
            gl.glVertex2f(centerOfRotation.x, centerOfRotation.y-L);
            gl.glVertex2f(centerOfRotation.x, centerOfRotation.y+L);
            gl.glEnd();
        }

    }

//    public float getGainTranslation() {
//        return gainTranslation;
//    }
//
//    public void setGainTranslation(float gain) {
//        if (gain < 0) {
//            gain = 0;
//        } else if (gain > 100) {
//            gain = 100;
//        }
//        this.gainTranslation = gain;
//        putFloat("gainTranslation", gain);
//    }
//    /**
//     * @return the gainVelocity
//     */
//    public float getGainVelocity() {
//        return gainVelocity;
//    }
//
//    /**
//     * @param gainVelocity the gainVelocity to set
//     */
//    public void setGainVelocity(float gainVelocity) {
//        this.gainVelocity = gainVelocity;
//        putFloat("gainVelocity", gainVelocity);
//    }
//    public void setCornerFreqHz(float freq) {
//        cornerFreqHz = freq;
//        filterX.set3dBFreqHz(freq);
//        filterY.set3dBFreqHz(freq);
//        filterRotation.set3dBFreqHz(freq);
//        putFloat("cornerFreqHz", freq);
//    }
//
//    public float getCornerFreqHz() {
//        return cornerFreqHz;
//    }
    @Override
    synchronized public void resetFilter() {
        resetCalled = true;
        panRate = 0;
        tiltRate = 0;
        rollRate = 0;
        panDC = 0;
        tiltDC = 0;
        rollDC = 0;
        rollDeg = 0;
        panTranslationFilter.reset();
        tiltTranslationFilter.reset();
        rollFilter.reset();
        radPerPixel = (float) Math.atan((getChip().getPixelWidthUm() * 1e-3f) / lensFocalLengthMm);
        filterX.setInternalValue(0);
        filterY.setInternalValue(0);
        filterRotation.setInternalValue(0);
        translation.x = 0;
        translation.y = 0;
        lastTransform = null;
        if (isPanTiltEnabled()) {
            try {
                panTilt.setPanTiltValues(.5f, .5f);
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
                panTilt.close();
            }
        }
        eventQueue.clear();
        rewindFlg = true;
    }

    @Override
    public void initFilter() {
//        panTilt = PanTilt.getLastInstance();
        resetFilter();
    }

    public boolean isFlipContrast() {
        return flipContrast;
    }

    public void setFlipContrast(boolean flipContrast) {
        this.flipContrast = flipContrast;
        putBoolean("flipContrast", flipContrast);
    }

    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        setCameraRotationEstimator(cameraRotationEstimator); // reflag enabled/disabled state of motion computation
        getEnclosedFilterChain().reset();
        if (!yes) {
            setPanTiltEnabled(false); // turn off servos, close interface
            if (chip.getAeViewer() != null && chip.getCanvas() != null && chip.getCanvas().getDisplayMethod() instanceof ChipRendererDisplayMethodRGBA) {
                ChipRendererDisplayMethodRGBA displayMethod = (ChipRendererDisplayMethodRGBA) chip.getCanvas().getDisplayMethod(); // TODO not ideal (tobi)
                displayMethod.setImageTransform(new Point2D.Float(0, 0), 0);
            }
        } else {
            resetFilter(); // reset on enabled to prevent large timestep anomalies
        }
    }

    public boolean isFeedforwardEnabled() {
        return feedforwardEnabled;
    }

    /**
     * true to apply current shift values to input packet events. This does a
     * kind of feedback compensation
     */
    public void setFeedforwardEnabled(boolean feedforwardEnabled) {
        this.feedforwardEnabled = feedforwardEnabled;
        putBoolean("feedforwardEnabled", feedforwardEnabled);
    }

//    public boolean isRotationEnabled(){
//        return rotationEnabled;
//    }
//
//    public void setRotationEnabled(boolean rotationEnabled){
//        this.rotationEnabled=rotationEnabled;
//        putBoolean("rotationEnabled",rotationEnabled);
//    }
    /**
     * Method used to compute shift.
     *
     * @return the positionComputer
     */
    public CameraRotationEstimator getCameraRotationEstimator() {
        return cameraRotationEstimator;
    }

    /**
     * Chooses how the current position of the scene is computed.
     *
     * @param positionComputer the positionComputer to set
     */
    synchronized public void setCameraRotationEstimator(CameraRotationEstimator positionComputer) {
        this.cameraRotationEstimator = positionComputer;
        putString("positionComputer", positionComputer.toString());
        switch (positionComputer) {
            case VORSensor:
        }
    }

    /**
     * The global translational shift applied to output, computed by enclosed
     * FilterChain.
     *
     * @return the x,y shift
     */
    public Point2D.Float getShift() {
        return translation;
    }

    /**
     * @param shift the shift to set
     */
    public void setShift(Point2D.Float shift) {
        this.translation = shift;
    }

    /**
     * @return the annotateEnclosedEnabled
     */
    public boolean isAnnotateEnclosedEnabled() {
        return annotateEnclosedEnabled;
    }

    /**
     * @param annotateEnclosedEnabled the annotateEnclosedEnabled to set
     */
    public void setAnnotateEnclosedEnabled(boolean annotateEnclosedEnabled) {
        this.annotateEnclosedEnabled = annotateEnclosedEnabled;
        putBoolean("annotateEnclosedEnabled", annotateEnclosedEnabled);
    }

    /**
     * @return the panTiltEnabled
     */
    public boolean isPanTiltEnabled() {
        return panTiltEnabled;
    }

    /**
     * Enables use of pan/tilt servo controller for camera for mechanical
     * stabilization.
     *
     * @param panTiltEnabled the panTiltEnabled to set
     */
    public void setPanTiltEnabled(boolean panTiltEnabled) {
        this.panTiltEnabled = panTiltEnabled;
        putBoolean("panTiltEnabled", panTiltEnabled);
        if (!panTiltEnabled) {
            try {
                if ((panTilt != null) && (panTilt.getServoInterface() != null) && panTilt.getServoInterface().isOpen()) {
                    panTilt.getServoInterface().disableAllServos();
                    panTilt.close();
                }
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
                panTilt.close();
            }
        }
    }

    /**
     * @return the electronicStabilizationEnabled
     */
    public boolean isElectronicStabilizationEnabled() {
        return electronicStabilizationEnabled;
    }

    /**
     * @param electronicStabilizationEnabled the electronicStabilizationEnabled
     * to set
     */
    public void setElectronicStabilizationEnabled(boolean electronicStabilizationEnabled) {
        this.electronicStabilizationEnabled = electronicStabilizationEnabled;
        putBoolean("electronicStabilizationEnabled", electronicStabilizationEnabled);
    }

    /**
     * @return the gainPanTiltServos
     */
    public float getGainPanTiltServos() {
        return gainPanTiltServos;
    }

    /**
     * @param gainPanTiltServos the gainPanTiltServos to set
     */
    public void setGainPanTiltServos(float gainPanTiltServos) {
        this.gainPanTiltServos = gainPanTiltServos;
        putFloat("gainPanTiltServos", gainPanTiltServos);
    }

    /**
     * @return the highpassTauMs
     */
    public float getHighpassTauMsTranslation() {
        return highpassTauMsTranslation;
    }

    /**
     * @param highpassTauMs the highpassTauMs to set
     */
    public void setHighpassTauMsTranslation(float highpassTauMs) {
        this.highpassTauMsTranslation = highpassTauMs;
        putFloat("highpassTauMsTranslation", highpassTauMs);
        panTranslationFilter.setTauMs(highpassTauMs);
        tiltTranslationFilter.setTauMs(highpassTauMs);
    }

    /**
     * @return the highpassTauMs
     */
    public float getHighpassTauMsRotation() {
        return highpassTauMsRotation;
    }

    /**
     * @param highpassTauMs the highpassTauMs to set
     */
    public void setHighpassTauMsRotation(float highpassTauMs) {
        this.highpassTauMsRotation = highpassTauMs;
        putFloat("highpassTauMsRotation", highpassTauMs);
        rollFilter.setTauMs(highpassTauMs);
    }

    private float clip(float f, float lim) {
        if (f > lim) {
            f = lim;
        } else if (f < -lim) {
            f = -lim;
        }
        return f;
    }

    /**
     * @return the lensFocalLengthMm
     */
    public float getLensFocalLengthMm() {
        return lensFocalLengthMm;
    }

    /**
     * @param lensFocalLengthMm the lensFocalLengthMm to set
     */
    public void setLensFocalLengthMm(float lensFocalLengthMm) {
        this.lensFocalLengthMm = lensFocalLengthMm;
        putFloat("lensFocalLengthMm", lensFocalLengthMm);
        radPerPixel = (float) Math.asin((getChip().getPixelWidthUm() * 1e-3f) / lensFocalLengthMm);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == AEViewer.EVENT_TIMESTAMPS_RESET) {
            resetFilter();
            flushCounter = FLUSH_COUNT;
        } else if (evt.getPropertyName().equals(AEInputStream.EVENT_REWIND)) {
            resetFilter();
            flushCounter = FLUSH_COUNT;
        } else if (evt.getPropertyName().equals(AEViewer.EVENT_FILEOPEN)) {
            log.info("File Open");
            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
            AEFileInputStream in = (player.getAEInputStream());
            in.getSupport().addPropertyChangeListener(this);
            // Treat FileOpen same as a rewind
            resetFilter();
            flushCounter = FLUSH_COUNT;
        } // END IF
    }

    /**
     * @return the transformResetLimitDegrees
     */
    public int getTransformResetLimitDegrees() {
        return transformResetLimitDegrees;
    }

    /**
     * @param transformResetLimitDegrees the transformResetLimitDegrees to set
     */
    public void setTransformResetLimitDegrees(int transformResetLimitDegrees) {
        this.transformResetLimitDegrees = transformResetLimitDegrees;
        putInt("transformResetLimitDegrees", transformResetLimitDegrees);
    }

    /**
     * @return the showTransformRectangle
     */
    public boolean isShowTransformRectangle() {
        return showTransformRectangle;
    }

    /**
     * @param showTransformRectangle the showTransformRectangle to set
     */
    public void setShowTransformRectangle(boolean showTransformRectangle) {
        this.showTransformRectangle = showTransformRectangle;
        putBoolean("showTransformRectangle", showTransformRectangle);
    }

    /**
     * @return the disableTranslation
     */
    public boolean isDisableTranslation() {
        return disableTranslation;
    }

    /**
     * @param disableTranslation the disableTranslation to set
     */
    public void setDisableTranslation(boolean disableTranslation) {
        this.disableTranslation = disableTranslation;
        putBoolean("disableTranslation", disableTranslation);
    }

    /**
     * @return the disableRotation
     */
    public boolean isDisableRotation() {
        return disableRotation;
    }

    /**
     * @param disableRotation the disableRotation to set
     */
    public void setDisableRotation(boolean disableRotation) {
        this.disableRotation = disableRotation;
        putBoolean("disableRotation", disableRotation);
    }

    private class CalibrationFilter {

        int count = 0;
        float sum = 0;

        void reset() {
            count = 0;
            sum = 0;
        }

        void addSample(float sample) {
            sum += sample;
            count++;
        }

        float computeAverage() {
            return sum / count;
        }
    }

    /**
     * Returns the last event transform that was computed.
     *
     * @return the lastTransform that was computed
     */
    public TransformAtTime getLastTransform() {
        return lastTransform;
    }

    /**
     * Returns the transform applicable to last image acquired.
     *
     * @return the image transform
     */
    public TransformAtTime getImageTransform() {
        return imageTransform;
    }

    /**
     * @return the transformImageEnabled
     */
    public boolean isTransformImageEnabled() {
        return transformImageEnabled;
    }

    /**
     * @param transformImageEnabled the transformImageEnabled to set
     */
    public void setTransformImageEnabled(boolean transformImageEnabled) {
        this.transformImageEnabled = transformImageEnabled;
        putBoolean("transformImageEnabled", transformImageEnabled);
    }

    /**
     * @return the showGrid
     */
    public boolean isShowGrid() {
        return showGrid;
    }

    /**
     * @param showGrid the showGrid to set
     */
    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        putBoolean("showGrid", showGrid);
    }

    /**
     * @return the imuLagMs
     */
    public float getImuLagMs() {
        return imuLagMs;
    }

    /**
     * @param imuLagMs the imuLagMs to set
     */
    public void setImuLagMs(float imuLagMs) {
        this.imuLagMs = imuLagMs;
        putFloat("imuLagMs", imuLagMs);
    }

    /**
     * @return the numCalibrationSamples
     */
    public int getNumCalibrationSamples() {
        return numCalibrationSamples;
    }

    /**
     * @param numCalibrationSamples the numCalibrationSamples to set
     */
    public void setNumCalibrationSamples(int numCalibrationSamples) {
        this.numCalibrationSamples = numCalibrationSamples;
        putInt("numCalibrationSamples", numCalibrationSamples);
    }

    /**
     * Sets the clickedPoint field
     *
     * @param e
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        if (!centerOfRotationSelectionPending) {
            return;
        }
        Point p = getMousePixel(e);
        centerOfRotation = p;
        log.info("selected center of rotation as " + centerOfRotation);
        putInt("centerOfRotationX", p.x);
        putInt("centerOfRotationY", p.y);
        centerOfRotationSelectionPending=false;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!centerOfRotationSelectionPending) {
            return;
        }
        Point p = getMousePixel(e);
        centerOfRotation = p;
    }
    
    
}
