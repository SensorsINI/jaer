/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.poseestimation;

import java.awt.geom.Point2D;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.HighpassFilter;

import com.phidgets.Phidget;

import eu.seebetter.ini.chips.sbret10.IMUSample;
import eu.seebetter.ini.chips.sbret10.SBret10;

/**
 * Encapsulates the Phidgets Spatial unit for use by the VOR steadicam. This is
 * the 1056 - PhidgetSpatial 3/3/3: Compass 3-Axis, Gyroscope 3-Axis,
 * Accelerometer 3-Axis 5G
 *
 * @author tobi
 */
@Description("The Phidgets Spatial 9-DOF rate gyro, accelormeter, compass")
public class VORSensorForSteadicam extends EventFilter2D implements FrameAnnotater {

    private int sampleIntervalMs = getInt("sampleIntervalMs", 100);
    private double[] angular, acceleration;
    private float panRate = 0, tiltRate = 0, rollRate = 0; // in deg/sec
    private float panOffset,tiltOffset,rollOffset;
    private float upAccel = 0, rightAccel = 0, zAccel = 0; // in g in m/s^2
    private int lastAeTimestamp = 0;
    private int timestampUs = 0;
    private double lastTimestampDouble = 0; // in seconds
    private float panTranslationDeg = 0;
    private float tiltTranslationDeg = 0;
    private float rollDeg = 0;
    private float panDC = 0, tiltDC = 0, rollDC = 0;
//    public TransformAtTime transformAtTime = new TransformAtTime(timestampUs, new Point2D.Float(), rollDeg);
    private float lensFocalLengthMm = getFloat("lensFocalLengthMm",8.5f);
    HighpassFilter panTranslationFilter = new HighpassFilter();
    HighpassFilter tiltTranslationFilter = new HighpassFilter();
    HighpassFilter rollFilter = new HighpassFilter();
    private float highpassTauMsTranslation = getFloat("highpassTauMsTranslation", 1000);
    private float highpassTauMsRotation = getFloat("highpassTauMsRotation", 1000);
    float radPerPixel;
//    private ArrayBlockingQueue<PhidgetsSpatialEvent> spatialDataQueue = new ArrayBlockingQueue<PhidgetsSpatialEvent>(9 * 4); // each phidgets sample could be 9 (3 gyro + 3 accel + 3 compass) and we want room for 4 samples
    private volatile boolean resetCalled=false;
    private IMUSample imuSample=null;

    public VORSensorForSteadicam(AEChip chip) {
        super(chip);
        rollFilter.setTauMs(highpassTauMsRotation);
        panTranslationFilter.setTauMs(highpassTauMsTranslation);
        tiltTranslationFilter.setTauMs(highpassTauMsTranslation);
        log.info(Phidget.getLibraryVersion());
        setPropertyTooltip("sampleIntervalMs", "sensor sample interval in ms, min 4ms, powers of two, e.g. 4,8,16,32...");
        setPropertyTooltip("highpassTauMsTranslation", "highpass filter time constant in ms to relax transform back to zero for translation (pan, tilt) components");
        setPropertyTooltip("highpassTauMsRotation", "highpass filter time constant in ms to relax transform back to zero for rotation (roll) component");
        setPropertyTooltip("lensFocalLengthMm", "sets lens focal length in mm to adjust the scaling from camera rotation to pixel space");
        setPropertyTooltip("zeroGyro", "zeros the gyro output. Sensor should be stationary for period of 1-2 seconds during zeroing");
        setPropertyTooltip("eraseGyroZero", "Erases the gyro zero values");
    }

    @Override
    public synchronized void cleanup() {
        super.cleanup();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (in.getSize() > 0) {
            lastAeTimestamp = in.getLastTimestamp();
        }
        for (BasicEvent o : in) {
            if(o instanceof IMUSample){
                imuSample=(IMUSample)o;
            }
            maybeCallUpdateObservers(in, o.timestamp);
        } // call listeners if enough time has passed for update. This update should update the camera rotation values.

        return in;
    }
    int lastUpdateTimestamp = 0;

    /**
     * Computes transform using current gyro outputs based on timestamp supplied
     * and returns a TransformAtTime object. Should be called by update in
     * enclosing processor.
     *
     * @param timestamp the timestamp in us.
     * @return the transform object representing the camera rotation
     */
    synchronized public TransformAtTime computeTransform(int timestamp) {
        if(resetCalled){
            log.info("reset called, panDC"+panDC+" panTranslationFilter="+panTranslationFilter);
            resetCalled=false;
        }
        float dtS = (timestamp - lastUpdateTimestamp) * 1e-6f;
        if (chip.getClass() == DVS128Phidget.class) {
            panRate =  (float)((DVS128Phidget)chip).getGyro()[0];
            tiltRate = -(float)((DVS128Phidget)chip).getGyro()[1];
            rollRate = (float)((DVS128Phidget)chip).getGyro()[2];
            timestampUs = ((DVS128Phidget)chip).getTimeUs();
        }else if (chip.getClass() == SBret10.class) {
            //IMUSample imuSample=((SBret10)chip).getImuSample();// TODO problem with this call is that this is the latest (last) sample of the IMU, which was set during packet extraction. So it is the last IMU sample from the EventPacket that the entire rendering cycle is processing. It's not the sample we want, which is the one at this time inside the packet. It's a problem of having the data external to the AEPacket.
            if(imuSample==null) {
				return null;
			}
        dtS = (imuSample.getTimestampUs() - lastUpdateTimestamp) * 1e-6f;
        lastUpdateTimestamp = imuSample.getTimestampUs();
            panRate=imuSample.getGyroYawY();
            tiltRate=imuSample.getGyroTiltX();
            rollRate=imuSample.getGyroRollZ();
            zAccel=imuSample.getAccelZ();
            upAccel=imuSample.getAccelY();
            rightAccel=imuSample.getAccelX();
        }
        panDC += getPanRate() * dtS;
        tiltDC += getTiltRate() * dtS;
        rollDC += getRollRate() * dtS;

        panTranslationDeg = panTranslationFilter.filter(panDC, timestamp);
        tiltTranslationDeg = tiltTranslationFilter.filter(tiltDC, timestamp);
        rollDeg = rollFilter.filter(rollDC, timestamp);
//        panTranslationDeg = panTranslationFilter.filter(panDC, timestampUs);
//        tiltTranslationDeg = tiltTranslationFilter.filter(tiltDC, timestampUs);
//        rollDeg = rollFilter.filter(rollDC, timestampUs);

//        float lim=20;
//        panTranslationDeg=clip(panTranslationDeg,lim);
//        tiltTranslationDeg=clip(tiltTranslationDeg,lim);
//        rollDeg=clip(rollDeg,lim);

        // computute transform in TransformAtTime units here.
        // Use the lens focal length and camera resolution.

        TransformAtTime tr = new TransformAtTime(timestamp,
                new Point2D.Float(
                (float) ((Math.PI / 180) * panTranslationDeg) / radPerPixel,
                (float) ((Math.PI / 180) * tiltTranslationDeg) / radPerPixel),
                (-rollDeg * (float) Math.PI) / 180);
//        transformAtTime = tr;
        return tr;
    }

    public void doEraseGyroZero(){
        panOffset=0;
        tiltOffset=0;
        rollOffset=0;
    }

    public void doZeroGyro() {
        if (chip.getClass() == DVS128Phidget.class) {
            ((DVS128Phidget) chip).doZeroGyro();
        }else if(chip.getClass()==SBret10.class){
            panOffset=panRate; // TODO offsets should really be some average over some samples
            tiltOffset=tiltRate;
            rollOffset=rollRate;
        }

    }
    @Override
    synchronized public void resetFilter() {
        resetCalled=true;
//        spatialDataQueue.clear();
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
        radPerPixel = (float) Math.atan((getChip().getPixelWidthUm() * 1e-3f) /lensFocalLengthMm);

    }

    @Override
    public void initFilter() {
    }
    GLU glu = null;
    GLUquadric expansionQuad;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (chip.getClass() != DVS128Phidget.class) {
            return;
        }

        GL2 gl = drawable.getGL().getGL2();
        // draw rate gyro outputs
        gl.glPushMatrix();
        gl.glColor3f(1, 0, 0);
        gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(0, 0);
        gl.glVertex2f(getPanRate(), getTiltRate());
        gl.glEnd();
        gl.glPopMatrix();

        // draw roll gryo as line left/right
        gl.glPushMatrix();
        gl.glTranslatef(chip.getSizeX() / 2, (chip.getSizeY() * 3) / 4, 0);
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(0, 0);
        gl.glVertex2f(getRollRate(), 0);
        gl.glEnd();
        gl.glPopMatrix();

        int sx2 = chip.getSizeX() / 2, sy2 = chip.getSizeY() / 2;

//        // draw transform
//        gl.glPushMatrix();
//        gl.glTranslatef(transformAtTime.translation.x + sx2, transformAtTime.translation.y + sy2, 0);
//        gl.glRotatef((float) (transformAtTime.rotation * 180 / Math.PI), 0, 0, 1);
//        gl.glLineWidth(2f);
//        gl.glColor3f(1, 0, 0);
//        gl.glBegin(GL.GL_LINE_LOOP);
//        // rectangle around transform
//        gl.glVertex2f(-sx2, -sy2);
//        gl.glVertex2f(sx2, -sy2);
//        gl.glVertex2f(sx2, sy2);
//        gl.glVertex2f(-sx2, sy2);
//        gl.glEnd();
//        gl.glPopMatrix();
    }

    /**
     * @return the angular
     */
    public double[] getAngular() {
        return angular;
    }

    /**
     * @return the acceleration
     */
    public double[] getAcceleration() {
        return acceleration;
    }

    /**
     * @return the panRate
     */
    public float getPanRate() {
        if (chip.getClass() == DVS128Phidget.class) {
            panRate =  (float)((DVS128Phidget)chip).getGyro()[0];
        }
        return panRate-panOffset;
    }

    /**
     * @return the tiltRate
     */
    public float getTiltRate() {
        if (chip.getClass() == DVS128Phidget.class) {
            tiltRate = (float)(-((DVS128Phidget)chip).getGyro()[1]);
        }
        return tiltRate-tiltOffset;
    }

    /**
     * @return the rollRate
     */
    public float getRollRate() {
        if (chip.getClass() == DVS128Phidget.class) {
            rollRate = (float)((DVS128Phidget)chip).getGyro()[2];
        }
        return rollRate-rollOffset;
    }

    /**
     * @return the upAccel
     */
    public float getUpAccel() {
        return upAccel;
    }

    /**
     * @return the rightAccel
     */
    public float getRightAccel() {
        return rightAccel;
    }

    /**
     * @return the zAccel
     */
    public float getzAccel() {
        return zAccel;
    }

    /**
     * @return the timestamp
     */
    public int getTimestamp() {
        return timestampUs;
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
        putFloat("lensFocalLengthMm",lensFocalLengthMm);
         radPerPixel = (float) Math.asin((getChip().getPixelWidthUm() * 1e-3f) /lensFocalLengthMm);
   }
}
