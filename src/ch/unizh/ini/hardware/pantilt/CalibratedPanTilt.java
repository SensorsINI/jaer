/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.hardware.pantilt;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.tracking.RectangularClusterTracker;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.caviar.hardwareinterface.ServoInterface;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.io.*;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Vector;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

/**
 * This filter enables calibrated control of a pan tilt laser pointer. CalibratedPanTilt has a method to set the pan tilt
 * to aim at an x,y point in the retinal view. CalibratedPanTilt only processes incoming events during calibration, when
 * it uses its own RectangularClusterTracker to track the jittering laser point spot for aiming calibration.
 * 
 * @author Tobi Delbruck
 */
public class CalibratedPanTilt extends EventFilter2D implements FrameAnnotater, PanTiltInterface, LaserOnOffControl {

    RectangularClusterTracker tracker;
    private PanTilt panTiltHardware;
    private PanTiltCalibrator calibrator=new PanTiltCalibrator(this);

    /** Constructs instance of the new 'filter' CalibratedPanTilt. The only time events are actually used
     * is during calibration. The PanTilt hardware interface is also constructed.
     * @param chip
     */
    public CalibratedPanTilt(AEChip chip) {
        super(chip);
        tracker = new RectangularClusterTracker(chip);
        setEnclosedFilter(tracker);
        panTiltHardware = new PanTilt();
    }
    
    /** Sets the pan and tilt to aim at a particular calibrated x,y visual direction
     @param x the x pixel
     @param y the y pixel
     */
    public void setPanTiltVisualAim(float x, float y) throws HardwareInterfaceException{
        float[] xy={x,y,1};
        float[] pt=calibrator.getTransformedPanTiltFromXY(xy);
        getPanTiltHardware().setPanTiltValues(pt[0], pt[1]);
    }

    @Override
    public String getDescription() {
        return "Controls a pantilt unit to aim at a calibrated visual location";
    }

    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        if(calibrator==null || !calibrator.isCalibrating()) return in;
        tracker.filterPacket(in);
        if (panTiltHardware.isLockOwned()) {
            return in;
        }
        if (tracker.getNumClusters() > 0) {
            RectangularClusterTracker.Cluster c = tracker.getClusters().get(0);
            if (c.isVisible()) {
                Point2D.Float p = c.getLocation();
                float[] xy = {p.x, p.y, 1};
                float[] pt = calibrator.getTransformedPanTiltFromXY(xy);

                float pan = pt[0];
                float tilt = pt[1];
                try {
                    panTiltHardware.setPanTiltValues(pan, tilt);
                } catch (HardwareInterfaceException e) {
                    log.warning(e.toString());
                }
                panTiltHardware.setLaserOn(true);
           }else{
               panTiltHardware.setLaserOn(false);
           }
        }else{
            panTiltHardware.setLaserOn(false);
        }
        return in;
    }

    
    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
        tracker.resetFilter();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;
        }
        tracker.annotate(drawable);

        GL gl = drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner

        if (gl == null) {
            log.warning("null GL");
            return;
        }
        final float BOX_LINE_WIDTH = 3f; // in pixels

        gl.glColor3f(1, 0, 0);
        gl.glLineWidth(BOX_LINE_WIDTH);

        final int sx = 2,  sy = 2;
        for (PanTiltCalibrationPoint p : calibrator.sampleVector) {
            gl.glPushMatrix();
            final int x = (int) p.ret.x,  y = (int) p.ret.y;
            gl.glBegin(GL.GL_LINE_LOOP);
            {
                gl.glVertex2i(x - sx, y - sy);
                gl.glVertex2i(x + sx, y - sy);
                gl.glVertex2i(x + sx, y + sy);
                gl.glVertex2i(x - sx, y + sy);
            }
            gl.glEnd();
            gl.glPopMatrix();
        }
    }

    private void drawBox(GL gl, int x, int y, int sx, int sy) {
    }

    /** Invokes the calibration GUI. Calibration values are stored persistently as preferences. 
     * Built automatically into filter parameter panel as an action.
     */
    public void doCalibrate() {
        if (calibrator == null) {
            calibrator = new PanTiltCalibrator(this);
        }
        calibrator.calibrate();
    }

    public PanTilt getPanTiltHardware() {
        return panTiltHardware;
    }

    public void setPanTiltHardware(PanTilt panTilt) {
        this.panTiltHardware=panTilt;
    }
    
       public float getJitterAmplitude() {
        return panTiltHardware.getJitterAmplitude();
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    public void setJitterAmplitude(float jitterAmplitude) {
        panTiltHardware.setJitterAmplitude(jitterAmplitude);
    }

    public float getJitterFreqHz() {
        return panTiltHardware.getJitterFreqHz();
    }

    /** The frequency of the jitter
     * 
     * @param jitterFreqHz in Hz
     */
    public void setJitterFreqHz(float jitterFreqHz) {
        panTiltHardware.setJitterFreqHz(jitterFreqHz);
    }

    public void acquire() {
        getPanTiltHardware().acquire();
    }

    public float[] getPanTiltValues() {
        return getPanTiltHardware().getPanTiltValues();
    }

    public boolean isLockOwned() {
        return getPanTiltHardware().isLockOwned();
    }

    public void release() {
        getPanTiltHardware().release();
    }

    public void startJitter() {
        getPanTiltHardware().startJitter();
    }

    public void stopJitter() {
        getPanTiltHardware().stopJitter();
    }

    /** Sets the pan and tilt servo values
     @param pan 0 to 1 value
     @param tilt 0 to 1 value
     */
    public void setPanTiltValues(float pan, float tilt) throws HardwareInterfaceException {
        getPanTiltHardware().setPanTiltValues(pan, tilt);
    }

    public void setServoInterface(ServoInterface servo) {
       getPanTiltHardware().setServoInterface(servo);
    }

    public ServoInterface getServoInterface() {
        return getPanTiltHardware().getServoInterface();
    }

    public void setLaserEnabled(boolean yes) {
        getPanTiltHardware().setLaserEnabled(yes);
    }

    public PanTiltCalibrator getCalibrator() {
        return calibrator;
    }
    
 
}
