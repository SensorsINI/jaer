/*
 * SceneStabilizer.java (formerly MotionCompensator)
 *
 * Created on March 8, 2006, 9:41 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 8, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.tracking;

import net.sf.jaer.eventprocessing.label.*;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.filter.*;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.eventprocessing.FilterChain;

/**
 * This "optical Steadicam" tries to compensate global image motion by using global motion metrics to redirect output events and (optionally) also
a mechanical pantilt unit, shifting them according to motion of input.
Two methods can be used 1) the global translational flow computed from DirectionSelectiveFilter, or 2) the optical gyro outputs from OpticalGyro.
 *
 * @author tobi
 */
public class SceneStabilizer extends EventFilter2D implements FrameAnnotater {

    public static String getDescription() {
        return "Compenstates global scene translation and rotation to stabilize scene.";
    }

    /** Classses that compute scene shift.
     */
    public enum PositionComputer {

        OpticalGyro, DirectionSelectiveFilter
    };
    private PositionComputer positionComputer = PositionComputer.valueOf(getPrefs().get("SceneStabilizer.positionComputer", "OpticalGyro"));
    private float gainTranslation = getPrefs().getFloat("SceneStabilizer.gainTranslation", 1f);
    private float gainVelocity = getPrefs().getFloat("SceneStabilizer.gainVelocity", 1);
    private float gainPanTiltServos = getPrefs().getFloat("SceneStabilizer.gainPanTiltServos", 1);
    private DirectionSelectiveFilter dirFilter; // used when using optical flow
    private OpticalGyro clusterTracker; // used when tracking features
    private boolean feedforwardEnabled = getPrefs().getBoolean("SceneStabilizer.feedforwardEnabled", false);
    private boolean panTiltEnabled = getPrefs().getBoolean("SceneStabilizer.panTiltEnabled", false);
    private boolean electronicStabilizationEnabled = getPrefs().getBoolean("SceneStabilizer.electronicStabilizationEnabled", true);
    private Point2D.Float translation = new Point2D.Float();
    private HighpassFilter filterX = new HighpassFilter(), filterY = new HighpassFilter(), filterRotation = new HighpassFilter();
    private boolean flipContrast = false;
    private float rotation = 0;
    private final int SHIFT_LIMIT = 30;
    private float cornerFreqHz = getPrefs().getFloat("SceneStabilizer.cornerFreqHz", 0.1f);
    boolean evenMotion = true;
    private EventPacket ffPacket = null;
    private FilterChain filterChain;
    private boolean annotateEnclosedEnabled = getPrefs().getBoolean("SceneStabilizer.annotateEnclosedEnabled", true);
    private PanTilt panTilt = null;

    /** Creates a new instance of SceneStabilizer */
    public SceneStabilizer(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);

        dirFilter = new DirectionSelectiveFilter(chip);
        clusterTracker = new OpticalGyro(chip);
        dirFilter.setAnnotationEnabled(false);

        filterChain.add(dirFilter);

        clusterTracker = new OpticalGyro(chip);
        clusterTracker.setAnnotationEnabled(false); // annotation of cluster drawn in unshifted space and hard to see, clutters view.

        filterChain.add(clusterTracker);
        setEnclosedFilterChain(filterChain);

        setPositionComputer(positionComputer); // init filter enabled states
        initFilter(); // init filters for motion compensation
        setPropertyTooltip("positionComputer", "specifies which method is used to measure scene motion");
        setPropertyTooltip("gainTranslation", "gain applied to measured scene translation to affect electronic or mechanical output");
        setPropertyTooltip("gainVelocity", "gain applied to measured scene velocity times the weighted-average cluster aqe to affect electronic or mechanical output");
        setPropertyTooltip("gainPanTiltServos", "gain applied to translation for pan/tilt servo values");
        setPropertyTooltip("feedforwardEnabled", "enables motion computation on stabilized output of filter rather than input (only during use of DirectionSelectiveFilter)");
        setPropertyTooltip("panTiltEnabled", "enables use of pan/tilt servos for camera");
        setPropertyTooltip("electronicStabilizationEnabled", "stabilize by shifting events according to the PositionComputer");
        setPropertyTooltip("flipContrast", "flips contrast of output events depending on x*y sign of motion - should maintain colors of edges");
        setPropertyTooltip("cornerFreqHz", "sets highpass corner frequency in Hz for stabilization - frequencies smaller than this will not be stabilized and transform will return to zero on this time scale");
        setPropertyTooltip("annotateEnclosedEnabled", "showing tracking or motion filter output annotation of output, for setting up parameters of enclosed filters");
    }

    public EventPacket filterPacket(EventPacket in) {
        if (in == null) {
            return null;
        }
        if (!filterEnabled) {
            return in;
        }
        checkOutputPacketEventType(in);
//        int lastTimeStamp=in.getLastTimestamp();
        if (feedforwardEnabled && isElectronicStabilizationEnabled()) {
            if (ffPacket == null) {
                ffPacket = new EventPacket(in.getEventClass());
            }
            int sx = chip.getSizeX(), sy = chip.getSizeY();
            int dx = Math.round(translation.x), dy = Math.round(translation.y); // TODO wrong transform, leaves out rotation
            OutputEventIterator oi = ffPacket.outputIterator();
            for (Object o : in) {
                PolarityEvent e = (PolarityEvent) o;
                int x = (e.x + dx);
                if (x < 0 | x >= sx) {
                    continue;
                }
                int y = (e.y + dy);
                if (y < 0 || y >= sy) {
                    continue;
                }
                PolarityEvent oe = (PolarityEvent) oi.nextOutput();
                oe.copyFrom(e);
            }
            in = ffPacket;
        }
        getEnclosedFilterChain().filterPacket(in);
//        switch(positionComputer){
//            case DirectionSelectiveFilter:
//                dir=enclosedFilter.filterPacket(in);
//                break;
//            case OpticalGyro:
//                dir=clusterTracker.filterPacket(in);
//        }

        computeTransform(in);

        if (isElectronicStabilizationEnabled()) {
//            int dx=Math.round(translation.x), dy=Math.round(translation.y);
            int sizex = chip.getSizeX() - 1;
            int sizey = chip.getSizeY() - 1;
            checkOutputPacketEventType(in);
            int n = in.getSize();
            short nx, ny;
            OutputEventIterator outItr = out.outputIterator();
            // TODO compute evenMotion boolean from clusterTracker
            for (Object o : in) {
                PolarityEvent ev = (PolarityEvent) o;
                clusterTracker.transformEvent(ev, gainTranslation, gainVelocity);

                if (ev.x > sizex || ev.x < 0 || ev.y > sizey || ev.y < 0) {
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
                panTilt.setPanTiltValues(.5f - translation.x * getGainPanTiltServos() * factor, .5f + translation.y * getGainPanTiltServos() * factor);
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

    /** Using DirectionSelectiveFilter, the transform is computed by pure
    integration of the motion signal followed by a highpass filter to remove long term DC offsets.
     * <p>
    Using OpticalGyro, the transform is computed by the optical gyro which tracks clusters and measures
    scene translation (and possibly rotation) from a consensus of the tracked clusters.

    @param in the input event packet.
     */
    private void computeTransform(EventPacket in) {
        float shiftx = 0, shifty = 0;
        switch (positionComputer) {
            case DirectionSelectiveFilter:
                Point2D.Float f = dirFilter.getTranslationVector(); // this is 'instantaneous' motion signal (as filtered by DirectionSelectiveFilter)
                int t = in.getLastTimestamp();
                float dtSec = in.getDurationUs() * 1e-6f; // duration of this slice
                if (Math.abs(f.x) > Math.abs(f.y)) {
                    evenMotion = f.x > 0; // used to flip contrast
                } else {
                    evenMotion = f.y > 0;
                }
                shiftx += -(float) (gainTranslation * f.x * dtSec); // this is integrated shift
                shifty += -(float) (gainTranslation * f.y * dtSec);
                translation.x = (filterX.filter(shiftx, t)); // these are highpass filtered shifts
                translation.y = (filterY.filter(shifty, t));
                break;
            case OpticalGyro:
//                Point2D.Float trans=clusterTracker.getOpticalGyroTranslation();
//                Point2D.Float velPPS=clusterTracker.getOpticalGyro().getVelocityPPT();
//                int deltaTime=clusterTracker.getOpticalGyro().getAverageClusterAge();
//                translation.x=filterX.filter(-trans.x,in.getLastTimestamp())+gainVelocity*velPPS.x*deltaTime/1e6f/AEConstants.TICK_DEFAULT_US;
//                translation.y = filterY.filter(-trans.y,in.getLastTimestamp()) + gainVelocity * velPPS.y * deltaTime / 1e6f / AEConstants.TICK_DEFAULT_US; // shift is negative of gyro value.
                translation = clusterTracker.getOpticalGyroTranslation();
                rotation = clusterTracker.getOpticalGyroRotation();
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

    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        GL gl = drawable.getGL();
        if (gl == null) {
            return;
        }

        if (annotateEnclosedEnabled) { // show motion or feature tracking output, transformed accordingly
            if (!isElectronicStabilizationEnabled()) {
                clusterTracker.annotate(drawable); // using mechanical
            } else { // transform cluster tracker annotation to draw on top of transformed scene
                gl.glPushMatrix();
                gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
                gl.glRotatef((float) (rotation * 180 / Math.PI), 0, 0, 1);
                gl.glTranslatef(translation.x - chip.getSizeX() / 2, translation.y - chip.getSizeY() / 2, 0);
                clusterTracker.annotate(drawable);
                gl.glPopMatrix();
            }
        }
        if (isElectronicStabilizationEnabled()) { // draw translation frame
//            gl.glPushMatrix();
//            gl.glColor3f(1,0,0);
//            gl.glTranslatef(chip.getSizeX()/2,chip.getSizeY()/2,0);
//            gl.glLineWidth(.3f);
//            gl.glBegin(GL.GL_LINES);
//            gl.glVertex2f(0,0);
//            gl.glVertex2f(translation.x,translation.y); // vector from center to translation
//            gl.glEnd();
//            gl.glPopMatrix();


            gl.glPushMatrix(); // go back to origin
            gl.glLineWidth(.3f);
            gl.glColor3f(1, 0, 0);
            gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
            gl.glRotatef((float) (rotation * 180 / Math.PI), 0, 0, 1);
            gl.glTranslatef(translation.x - chip.getSizeX() / 2, translation.y - chip.getSizeY() / 2, 0);
            gl.glBegin(GL.GL_LINE_LOOP); // draw box of translation
            gl.glVertex2f(0, 0);
            gl.glVertex2f(chip.getSizeX(), 0);
            gl.glVertex2f(chip.getSizeX(), chip.getSizeY());
            gl.glVertex2f(0, chip.getSizeY());
            gl.glEnd();
            gl.glPopMatrix();

            gl.glPushMatrix();
            // xhairs - these are drawn in unshifted frame to allow monitoring of stability of image
            gl.glBegin(GL.GL_LINES);
            gl.glColor3f(0, 0, 1);
            // vert xhair line
            gl.glVertex2f(chip.getSizeX() / 2, 0);
            gl.glVertex2f(chip.getSizeX() / 2, chip.getSizeY());

            // horiz xhair line
            gl.glVertex2f(0, chip.getSizeY() / 2);
            gl.glVertex2f(chip.getSizeX(), chip.getSizeY() / 2);

            gl.glEnd();

            gl.glPopMatrix();
        }
    }

    public void annotate(Graphics2D g) {
    }

    public void annotate(float[][][] frame) {
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
        getPrefs().putFloat("SceneStabilizer.gainTranslation", gain);
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
        getPrefs().putFloat("SceneStabilizer.gainVelocity", gainVelocity);
    }

    public void setCornerFreqHz(float freq) {
        cornerFreqHz = freq;
        filterX.set3dBFreqHz(freq);
        filterY.set3dBFreqHz(freq);
        filterRotation.set3dBFreqHz(freq);
        getPrefs().putFloat("SceneStabilizer.cornerFreqHz", freq);
    }

    public float getCornerFreqHz() {
        return cornerFreqHz;
    }

    public Object getFilterState() {
        return null;
    }

    public void resetFilter() {
        dirFilter.resetFilter();
        clusterTracker.resetFilter();
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
    public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        setPositionComputer(positionComputer); // reflag enabled/disabled state of motion computation
    }

    public boolean isFeedforwardEnabled() {
        return feedforwardEnabled;
    }

    /** true to apply current shift values to input packet events. This does a kind of feedback compensation
     */
    public void setFeedforwardEnabled(boolean feedforwardEnabled) {
        this.feedforwardEnabled = feedforwardEnabled;
        getPrefs().putBoolean("SceneStabilizer.feedforwardEnabled", feedforwardEnabled);
    }

//    public boolean isRotationEnabled(){
//        return rotationEnabled;
//    }
//
//    public void setRotationEnabled(boolean rotationEnabled){
//        this.rotationEnabled=rotationEnabled;
//        getPrefs().putBoolean("SceneStabilizer.rotationEnabled",rotationEnabled);
//    }
    /** Method used to compute shift.
     * @return the positionComputer
     */
    public PositionComputer getPositionComputer() {
        return positionComputer;
    }

    /**
    Chooses how the current position of the scene is compuated.
     * @param positionComputer the positionComputer to set
     */
    synchronized public void setPositionComputer(PositionComputer positionComputer) {
        this.positionComputer = positionComputer;
        getPrefs().put("SceneStabilizer.positionComputer", positionComputer.toString());
        switch (positionComputer) {
            case DirectionSelectiveFilter:
                dirFilter.setFilterEnabled(true);
                clusterTracker.setFilterEnabled(false);
                break;
            case OpticalGyro:
                clusterTracker.setFilterEnabled(true);
                dirFilter.setFilterEnabled(false);
        }
    }

    /** The global translational shift applied to output, computed by enclosed FilterChain.
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
        getPrefs().putBoolean("SceneStabilizer.annotateEnclosedEnabled", annotateEnclosedEnabled);
    }

    /**
     * @return the panTiltEnabled
     */
    public boolean isPanTiltEnabled() {
        return panTiltEnabled;
    }

    /** Enables use of pan/tilt servo controller for camera for mechanical stabilization.

     * @param panTiltEnabled the panTiltEnabled to set
     */
    public void setPanTiltEnabled(boolean panTiltEnabled) {
        this.panTiltEnabled = panTiltEnabled;
        getPrefs().putBoolean("SceneStabilizer.panTiltEnabled", panTiltEnabled);
        if (!panTiltEnabled) {
            try {
                panTilt.setPanTiltValues(.5f, .5f);
                panTilt.close();
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
     * @param electronicStabilizationEnabled the electronicStabilizationEnabled to set
     */
    public void setElectronicStabilizationEnabled(boolean electronicStabilizationEnabled) {
        this.electronicStabilizationEnabled = electronicStabilizationEnabled;
        getPrefs().putBoolean("SceneStabilizer.electronicStabilizationEnabled", electronicStabilizationEnabled);
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
        getPrefs().putFloat("SceneStabilizer.gainPanTiltServos", gainPanTiltServos);
    }
}
