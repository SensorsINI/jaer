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
package org.capocaccia.cne.jaer.cne2012.vor;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.label.DirectionSelectiveFilter;
import net.sf.jaer.eventprocessing.processortype.Application;
import net.sf.jaer.eventprocessing.tracking.OpticalGyro;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.filter.HighpassFilter;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import ch.unizh.ini.jaer.projects.poseestimation.TransformAtTime;
import ch.unizh.ini.jaer.projects.poseestimation.VORSensorForSteadicam;

/**
 * This "vestibular-ocular Steadicam" tries to compensate global image motion by
 * using vestibular and global motion metrics to redirect output events and
 * (optionally) also a mechanical pantilt unit, shifting them according to
 * motion of input. Three methods can be used 1) the global translational flow
 * computed from DirectionSelectiveFilter, or 2) the optical gyro outputs from
 * OpticalGyro, or 3) (the best method) using a Phidgets gyro unit (the 9-DOF unit).
 *
 * @author tobi
 */
@Description("Compenstates global scene translation and rotation to stabilize scene like a SteadiCam.")
public class Steadicam extends EventFilter2D implements FrameAnnotater, Application, Observer {

    /**
     * Classes that compute camera rotation estimate based on scene shift and
     * maybe rotation around the center of the scene.
     */
    public enum CameraRotationEstimator {

        OpticalGyro, DirectionSelectiveFilter, VORSensor
    };
    private CameraRotationEstimator cameraRotationEstimator = null; //PositionComputer.valueOf(get("positionComputer", "OpticalGyro"));
    private float gainTranslation = getFloat("gainTranslation", 1f);
    private float gainVelocity = getFloat("gainVelocity", 1);
    private float gainPanTiltServos = getFloat("gainPanTiltServos", 1);
    private DirectionSelectiveFilter dirFilter; // used when using optical flow
    private OpticalGyro opticalGyro; // used when tracking features
    private boolean feedforwardEnabled = getBoolean("feedforwardEnabled", false);
    private boolean panTiltEnabled = getBoolean("panTiltEnabled", false);
    private boolean electronicStabilizationEnabled = getBoolean("electronicStabilizationEnabled", true);
//    private boolean vestibularStabilizationEnabled = getBoolean("vestibularStabilizationEnabled", false);
    private Point2D.Float translation = new Point2D.Float();
    private HighpassFilter filterX = new HighpassFilter(), filterY = new HighpassFilter(), filterRotation = new HighpassFilter();
    private boolean flipContrast = false;
    private float rotation = 0;
    private final int SHIFT_LIMIT = 30;
    private float cornerFreqHz = getFloat("cornerFreqHz", 0.1f);
    boolean evenMotion = true;
    private EventPacket ffPacket = null;
    private FilterChain filterChain;
    private boolean annotateEnclosedEnabled = getBoolean("annotateEnclosedEnabled", true);
    private PanTilt panTilt = null;
    ArrayList<TransformAtTime> transformList = new ArrayList(); // holds list of transforms over update times commputed by enclosed filter update callbacks
    int sx2, sy2;
    VORSensorForSteadicam vorSensor = null;
    TransformAtTime lastTransform = null;

    /**
     * Creates a new instance of SceneStabilizer
     */
    public Steadicam(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);

        // if dirFilter is used to compute lastTransform, opticalGyro is still used to lastTransform the events
        dirFilter = new DirectionSelectiveFilter(chip);
        dirFilter.setAnnotationEnabled(false);
        dirFilter.addObserver(this);
        filterChain.add(dirFilter);

        opticalGyro = new OpticalGyro(chip);
        opticalGyro.setAnnotationEnabled(false); // annotation of cluster drawn in unshifted space and hard to see, clutters view.
        opticalGyro.addObserver(this);
        filterChain.add(opticalGyro);

        vorSensor = new VORSensorForSteadicam(chip);
        vorSensor.addObserver(this);
        filterChain.add(vorSensor);

        setEnclosedFilterChain(filterChain);

        try {
            cameraRotationEstimator = CameraRotationEstimator.valueOf(getString("positionComputer", "OpticalGyro"));
        } catch (IllegalArgumentException e) {
            log.warning("bad preference " + getString("positionComputer", "OpticalGyro") + " for preferred PositionComputer, choosing default OpticalGyro");
            cameraRotationEstimator = CameraRotationEstimator.OpticalGyro;
            putString("positionComputer", "OpticalGyro");
        }

        setCameraRotationEstimator(cameraRotationEstimator); // init filter enabled states
        initFilter(); // init filters for motion compensation
        setPropertyTooltip("cameraRotationEstimator", "specifies which method is used to measure camera rotation estimate");
        setPropertyTooltip("gainTranslation", "gain applied to measured scene translation to affect electronic or mechanical output");
        setPropertyTooltip("gainVelocity", "gain applied to measured scene velocity times the weighted-average cluster aqe to affect electronic or mechanical output");
        setPropertyTooltip("gainPanTiltServos", "gain applied to translation for pan/tilt servo values");
        setPropertyTooltip("feedforwardEnabled", "enables motion computation on stabilized output of filter rather than input (only during use of DirectionSelectiveFilter)");
        setPropertyTooltip("panTiltEnabled", "enables use of pan/tilt servos for camera");
        setPropertyTooltip("electronicStabilizationEnabled", "stabilize by shifting events according to the PositionComputer");
        setPropertyTooltip("flipContrast", "flips contrast of output events depending on x*y sign of motion - should maintain colors of edges");
        setPropertyTooltip("cornerFreqHz", "sets highpass corner frequency in Hz for stabilization - frequencies smaller than this will not be stabilized and transform will return to zero on this time scale");
        setPropertyTooltip("annotateEnclosedEnabled", "showing tracking or motion filter output annotation of output, for setting up parameters of enclosed filters");
        setPropertyTooltip("opticalGyroTauLowpassMs", "lowpass filter time constant in ms for optical gyro camera rotation measure");
        setPropertyTooltip("opticalGyroRotationEnabled", "enables rotation in transform");
        setPropertyTooltip("vestibularStabilizationEnabled", "use the gyro/accelometer to provide transform");
    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        sx2 = chip.getSizeX() / 2;
        sy2 = chip.getSizeY() / 2;
        checkOutputPacketEventType(in);
        transformList.clear(); // empty list of transforms to be applied
        getEnclosedFilterChain().filterPacket(in); // issues callbacks to us periodically via update based on

        if (isElectronicStabilizationEnabled()) {
            int sizex = chip.getSizeX() - 1;
            int sizey = chip.getSizeY() - 1;
            checkOutputPacketEventType(in);
            int n = in.getSize();
            short nx, ny;
            OutputEventIterator outItr = out.outputIterator();
            // TODO compute evenMotion boolean from opticalGyro
            Iterator<TransformAtTime> transformItr = transformList.iterator();
            if (transformItr.hasNext()) {
                lastTransform = transformItr.next();
//                System.out.println(lastTransform.toString());
            }
            for (Object o : in) {
                PolarityEvent ev = (PolarityEvent) o;
                if ((lastTransform != null) && (ev.timestamp > lastTransform.timestamp)) {
                    if (transformItr.hasNext()) {
                        lastTransform = transformItr.next();
//                        System.out.println(lastTransform.toString());
                    }
                }
                transformEvent(ev, lastTransform);

                if ((ev.x > sizex) || (ev.x < 0) || (ev.y > sizey) || (ev.y < 0)) {
                    continue;
                }
                if (!flipContrast) {
                    outItr.nextOutput().copyFrom(ev);
                } else {
                    if (evenMotion) {
                        ev.type = (byte) (1 - ev.type); // don't let contrast flip when direction changes, try to stabilze contrast  by flipping it as well
                        ev.polarity = ev.polarity == PolarityEvent.Polarity.On ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                    }
                    outItr.nextOutput().copyFrom(ev);
                }
            }
        }

        if (isPanTiltEnabled()) { // mechanical pantilt
            try {
                // mechanical pantilt
                // assume that pan of 1 takes us 180 degrees and that the sensor has 45 deg FOV,
                // then 1 pixel will require only 45/180/size pan
                final float lensFocalLengthMm = 8;
                final float factor = (float) (chip.getPixelWidthUm() / 1000 / lensFocalLengthMm / Math.PI);
                panTilt.setPanTiltValues(.5f - (translation.x * getGainPanTiltServos() * factor), .5f + (translation.y * getGainPanTiltServos() * factor));
            } catch (HardwareInterfaceException ex) {
                log.warning("setting pantilt: " + ex);
                panTilt.close();
            }
        }

        if (isElectronicStabilizationEnabled()) {
            return out;
        } else {
            return in;
        }
    }

    public void transformEvent(BasicEvent e, TransformAtTime transform) {
        if (transform == null) {
            return;
        }
//        lastTransform.cosAngle=0; lastTransform.sinAngle=1;
        e.x -= sx2;
        e.y -= sy2;
        short newx = (short) Math.round((((transform.cosAngle * e.x) - (transform.sinAngle * e.y)) + transform.translation.x));
        short newy = (short) Math.round(((transform.sinAngle * e.x) + (transform.cosAngle * e.y) + transform.translation.y));
        e.x = (short) (newx + sx2);
        e.y = (short) (newy + sy2);
        e.address = chip.getEventExtractor().getAddressFromCell(e.x, e.y, e.getType()); // so event is logged properly to disk
    }

    /**
     * Called by update on enclosed filter updates. <p> Using
     * DirectionSelectiveFilter, the lastTransform is computed by pure
     * integration of the motion signal followed by a high-pass filter to remove
     * long term DC offsets. <p> Using OpticalGyro, the lastTransform is
     * computed by the optical gyro which tracks clusters and measures scene
     * translation (and possibly rotation) from a consensus of the tracked
     * clusters. <p> Using PhidgetsVORSensor, lastTransform is computed by PhidgetsVORSensor
     * using rate gyro sensors.
     *
     *
     * @param in the input event packet.
     */
    private void computeTransform(UpdateMessage msg) {
        float shiftx = 0, shifty = 0;
        float rot = 0;
        Point2D.Float trans = new Point2D.Float();
        switch (cameraRotationEstimator) {
            case DirectionSelectiveFilter:
                Point2D.Float f = dirFilter.getTranslationVector(); // this is 'instantaneous' motion vector in PPS units (as filtered by DirectionSelectiveFilter)
                int t = msg.timestamp;
                int dtUs = (t - msg.packet.getFirstTimestamp()); // duration of this slice
                if (Math.abs(f.x) > Math.abs(f.y)) {
                    evenMotion = f.x > 0; // used to flip contrast
                } else {
                    evenMotion = f.y > 0;
                }
                shiftx += -(gainTranslation * f.x * dtUs * 1e-6f); // this is integrated shift
                shifty += -(gainTranslation * f.y * dtUs * 1e-6f);
                trans.x = (filterX.filter(shiftx, t)); // these are highpass filtered shifts
                trans.y = (filterY.filter(shifty, t));
                transformList.add(new TransformAtTime(msg.timestamp, trans, rot)); // this list is applied during output lastTransform of the event stream
                break;
            case OpticalGyro:
//                Point2D.Float trans=opticalGyro.getOpticalGyroTranslation();
//                Point2D.Float velPPS=opticalGyro.getOpticalGyro().getVelocityPPT();
//                int deltaTime=opticalGyro.getOpticalGyro().getAverageClusterAge();
//                translation.x=filterX.filter(-trans.x,in.getLastTimestamp())+gainVelocity*velPPS.x*deltaTime/1e6f/AEConstants.TICK_DEFAULT_US;
//                translation.y = filterY.filter(-trans.y,in.getLastTimestamp()) + gainVelocity * velPPS.y * deltaTime / 1e6f / AEConstants.TICK_DEFAULT_US; // shift is negative of gyro value.
                trans.setLocation(opticalGyro.getOpticalGyroTranslation());
                rot = opticalGyro.getOpticalGyroRotation();
                Point2D.Float v = opticalGyro.getVelocityPPt();
                if (Math.abs(v.x) > Math.abs(v.y)) {
                    evenMotion = v.x > 0; // used to flip contrast
                } else {
                    evenMotion = v.y > 0;
                }
                transformList.add(new TransformAtTime(msg.timestamp, trans, rot)); // this list is applied during output lastTransform of the event stream
                break;
            case VORSensor:
                // compute the current lastTransform based on rate gyro signals
                TransformAtTime tr=vorSensor.computeTransform(msg.timestamp);
                evenMotion=vorSensor.getPanRate()*vorSensor.getTiltRate()>0;
//                System.out.println("added transform "+tr);
                transformList.add(tr);

        }
    }

    float limit(float nsx) {
        if (nsx > SHIFT_LIMIT) {
            nsx = SHIFT_LIMIT;
        } else if (nsx < -SHIFT_LIMIT) {
            nsx = -SHIFT_LIMIT;
        }
        return nsx;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }

        if (annotateEnclosedEnabled) { // show motion or feature tracking output, transformed accordingly
            if (!isElectronicStabilizationEnabled()) {
                opticalGyro.annotate(drawable); // using mechanical
            } else { // lastTransform cluster tracker annotation to draw on top of transformed scene
                gl.glPushMatrix();
                gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
                // use most recent optical gyro lastTransform
                if (lastTransform != null) {
                    rotation = lastTransform.rotation;
                    translation = lastTransform.translation;
                }
                gl.glRotatef((float) ((rotation * 180) / Math.PI), 0, 0, 1);
                gl.glTranslatef(translation.x - (chip.getSizeX() / 2), translation.y - (chip.getSizeY() / 2), 0);
                opticalGyro.annotate(drawable);
                gl.glPopMatrix();
            }
        }
        if ((lastTransform != null) && isElectronicStabilizationEnabled()) { // draw translation frame
            // draw transform
            gl.glPushMatrix();
            gl.glTranslatef(lastTransform.translation.x + sx2, lastTransform.translation.y + sy2, 0);
            gl.glRotatef((float) ((lastTransform.rotation * 180) / Math.PI), 0, 0, 1);
            gl.glLineWidth(2f);
            gl.glColor3f(1, 0, 0);
            gl.glBegin(GL.GL_LINE_LOOP);
            // rectangle around transform
            gl.glVertex2f(-sx2, -sy2);
            gl.glVertex2f(sx2, -sy2);
            gl.glVertex2f(sx2, sy2);
            gl.glVertex2f(-sx2, sy2);
            gl.glEnd();
            gl.glPopMatrix();

        }
    }

    public float getGainTranslation() {
        return gainTranslation;
    }

    public void setGainTranslation(float gain) {
        if (gain < 0) {
            gain = 0;
        } else if (gain > 100) {
            gain = 100;
        }
        this.gainTranslation = gain;
        putFloat("gainTranslation", gain);
    }

    /**
     * @return the gainVelocity
     */
    public float getGainVelocity() {
        return gainVelocity;
    }

    /**
     * @param gainVelocity the gainVelocity to set
     */
    public void setGainVelocity(float gainVelocity) {
        this.gainVelocity = gainVelocity;
        putFloat("gainVelocity", gainVelocity);
    }

    public void setCornerFreqHz(float freq) {
        cornerFreqHz = freq;
        filterX.set3dBFreqHz(freq);
        filterY.set3dBFreqHz(freq);
        filterRotation.set3dBFreqHz(freq);
        putFloat("cornerFreqHz", freq);
    }

    public float getCornerFreqHz() {
        return cornerFreqHz;
    }

    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
        dirFilter.resetFilter();
        opticalGyro.resetFilter();
        vorSensor.resetFilter();
        setCornerFreqHz(cornerFreqHz);
        filterX.setInternalValue(0);
        filterY.setInternalValue(0);
        filterRotation.setInternalValue(0);
        translation.x = 0;
        translation.y = 0;
        if (isPanTiltEnabled()) {
            try {
                panTilt.setPanTiltValues(.5f, .5f);
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
                panTilt.close();
            }
        }
    }

    @Override
    public void initFilter() {
        panTilt = new PanTilt();
        resetFilter();
    }

    public boolean isFlipContrast() {
        return flipContrast;
    }

    public void setFlipContrast(boolean flipContrast) {
        this.flipContrast = flipContrast;
    }

    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        setCameraRotationEstimator(cameraRotationEstimator); // reflag enabled/disabled state of motion computation
        getEnclosedFilterChain().reset();
        if (!yes) {
            setPanTiltEnabled(false); // turn off servos, close interface
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
            case DirectionSelectiveFilter:
                dirFilter.setFilterEnabled(true);
                opticalGyro.setFilterEnabled(false);
                vorSensor.setFilterEnabled(false);
                break;
            case OpticalGyro:
                opticalGyro.setFilterEnabled(true);
                dirFilter.setFilterEnabled(false);
                vorSensor.setFilterEnabled(false);
                break;
            case VORSensor:
                opticalGyro.setFilterEnabled(false);
                dirFilter.setFilterEnabled(false);
                vorSensor.setFilterEnabled(true);
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

    @Override
    public void update(Observable o, Object arg) { // called by enclosed tracker to update event stream on the fly, using intermediate tracking data
        if (arg instanceof UpdateMessage) {
            UpdateMessage msg = (UpdateMessage) arg;
            computeTransform(msg); // gets the lastTransform from the enclosed filter
        }
    }

    public void setOpticalGyroRotationEnabled(boolean opticalGyroRotationEnabled) {
        opticalGyro.setOpticalGyroRotationEnabled(opticalGyroRotationEnabled);
    }

    public boolean isOpticalGyroRotationEnabled() {
        return opticalGyro.isOpticalGyroRotationEnabled();
    }

    public void setOpticalGyroTauLowpassMs(float opticalGyroTauLowpassMs) {
        opticalGyro.setOpticalGyroTauLowpassMs(opticalGyroTauLowpassMs);
    }

    public float getOpticalGyroTauLowpassMs() {
        return opticalGyro.getOpticalGyroTauLowpassMs();
    }

    public void doZeroGyro() {
        vorSensor.doZeroGyro();
    }


}
