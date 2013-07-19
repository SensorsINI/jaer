/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.capocaccia.cne.jaer.cne2012.vor;

import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.HighpassFilter;

import com.phidgets.Phidget;
import com.phidgets.PhidgetException;
import com.phidgets.SpatialEventData;
import com.phidgets.SpatialPhidget;
import com.phidgets.event.AttachEvent;
import com.phidgets.event.AttachListener;
import com.phidgets.event.DetachEvent;
import com.phidgets.event.DetachListener;
import com.phidgets.event.ErrorEvent;
import com.phidgets.event.ErrorListener;
import com.phidgets.event.SpatialDataEvent;
import com.phidgets.event.SpatialDataListener;

/**
 * Encapsulates the Phidgets Spatial unit for use by the VOR steadicam. This is
 * the 1056 - PhidgetSpatial 3/3/3: Compass 3-Axis, Gyroscope 3-Axis,
 * Accelerometer 3-Axis 5G
 *
 * @author tobi
 */
@Description("The Phidgets Spatial 9-DOF rate gyro, accelormeter, compass")
public class PhidgetsVORSensor extends EventFilter2D implements FrameAnnotater, Observer {

    SpatialPhidget spatial = null;
    SpatialDataEvent lastSpatialData = null;
    private int sampleIntervalMs = getInt("sampleIntervalMs", 100);
    private double[] angular, acceleration;
    private float panRate = 0, tiltRate = 0, rollRate = 0; // in deg/sec
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
    float radPerPixel = 1;
    private ArrayBlockingQueue<PhidgetsSpatialEvent> spatialDataQueue = new ArrayBlockingQueue<PhidgetsSpatialEvent>(9 * 4); // each phidgets sample could be 9 (3 gyro + 3 accel + 3 compass) and we want room for 4 samples
    private volatile boolean resetCalled=false;

    public PhidgetsVORSensor(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        rollFilter.setTauMs(highpassTauMsRotation);
        panTranslationFilter.setTauMs(highpassTauMsTranslation);
        tiltTranslationFilter.setTauMs(highpassTauMsTranslation);
        log.info(Phidget.getLibraryVersion());
        setPropertyTooltip("sampleIntervalMs", "sensor sample interval in ms, min 4ms, powers of two, e.g. 4,8,16,32...");
        setPropertyTooltip("highpassTauMsTranslation", "highpass filter time constant in ms to relax transform back to zero for translation (pan, tilt) components");
        setPropertyTooltip("highpassTauMsRotation", "highpass filter time constant in ms to relax transform back to zero for rotation (roll) component");
        setPropertyTooltip("lensFocalLengthMm", "sets lens focal length in mm to adjust the scaling from camera rotation to pixel space");
        setPropertyTooltip("zeroGyro", "zeros the gyro output. Sensor should be stationary for period of 1-2 seconds during zeroing");
        try {
            spatial = new SpatialPhidget();
        } catch (PhidgetException e) {
            e.printStackTrace();
            log.log(Level.WARNING, "{0}: gyro will not be available", e.toString());
        }

        spatial.addAttachListener(new AttachListener() {

            @Override
            public void attached(AttachEvent ae) {
                log.log(Level.INFO, "attachment of {0}", ae);
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Serial: ").append(spatial.getSerialNumber()).append("\n");
                    sb.append("Accel Axes: ").append(spatial.getAccelerationAxisCount()).append("\n");
                    sb.append("Gyro Axes: ").append(spatial.getGyroAxisCount()).append("\n");
                    sb.append("Compass Axes: ").append(spatial.getCompassAxisCount()).append("\n");
                    log.info(sb.toString());
                    ((SpatialPhidget) ae.getSource()).setDataRate(sampleIntervalMs); //set lastSpatialData sample interval
                } catch (PhidgetException pe) {
                    log.log(Level.WARNING, "Problem setting data rate: {0}", pe.toString());
                }
            }
        });
        spatial.addDetachListener(new DetachListener() {

            @Override
            public void detached(DetachEvent ae) {
                log.log(Level.INFO, "detachment of {0}", ae);
                // do not close since then we will not get attachment events anymore
                resetFilter();
            }
        });
        spatial.addErrorListener(new ErrorListener() {

            @Override
            public void error(ErrorEvent ee) {
                log.warning(ee.toString());
            }
        });
        spatial.addSpatialDataListener(new SpatialDataListener() {

            @Override
            public void data(SpatialDataEvent sde) {
                if (sde.getData().length == 0) {
                    log.warning("empty data");
                    return;
                }
                lastSpatialData = sde;
                SpatialEventData sed = sde.getData()[sde.getData().length - 1];
                angular = sed.getAngularRate();
                panRate = (float) angular[0];
                tiltRate = -(float) angular[1];
                rollRate = (float) angular[2];
                acceleration = sed.getAcceleration();
                upAccel = (float) acceleration[0];
                rightAccel = (float) acceleration[1];
                zAccel = (float) acceleration[2];
//                float dt = (float) (sed.getTime() - lastTimestampDouble);
                timestampUs = (sed.getTimeSeconds() * 1000000) + sed.getTimeMicroSeconds();
//                panDC += panRate * dt;
//                tiltDC += tiltRate * dt;
//                rollDC += rollRate * dt;
//
//                panTranslationDeg = panTranslationFilter.filter(panDC, timestampUs);
//                tiltTranslationDeg = tiltTranslationFilter.filter(tiltDC, timestampUs);
//                rollDeg = rollFilter.filter(rollDC, timestampUs);
//
//                // computute transform in TransformAtTime units here.
//                // Use the lens focal length and camera resolution.
//
//                transformAtTime.set(timestampUs, (float) (Math.PI / 180 * panTranslationDeg) / radPerPixel, (float) (Math.PI / 180 * tiltTranslationDeg) / radPerPixel, -rollDeg * (float) Math.PI / 180);
//                lastTimestampDouble = sed.getTime();

                if (isFilterEnabled() && (spatialDataQueue.remainingCapacity() >= 6)) {
                    try {
                        spatialDataQueue.add(new PhidgetsSpatialEvent(timestampUs, panRate, PhidgetsSpatialEvent.SpatialDataType.YawRight));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(timestampUs, rollRate, PhidgetsSpatialEvent.SpatialDataType.RollClockwise));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(timestampUs, tiltRate, PhidgetsSpatialEvent.SpatialDataType.PitchUp));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(timestampUs, upAccel, PhidgetsSpatialEvent.SpatialDataType.AccelUp));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(timestampUs, rightAccel, PhidgetsSpatialEvent.SpatialDataType.AccelRight));
                        spatialDataQueue.add(new PhidgetsSpatialEvent(timestampUs, zAccel, PhidgetsSpatialEvent.SpatialDataType.AccelTowards));
                    } catch (IllegalStateException e) {
                        log.warning("queue full, couldn't write PhidgetsSpatialEvent with timestamp=" + timestampUs);
                    }
                }
            }
        });

        // open the device anytime
        try {
            spatial.openAny(); // starts a thread(?) to open any spatial that is plugged in now or later
        } catch (PhidgetException ex) {
            log.warning(ex.toString());
        }
    }

    @Override
    public synchronized void cleanup() {
        super.cleanup();
        try {
            if(spatial!=null) {
				spatial.close();
			}
        } catch (PhidgetException ex) {
            Logger.getLogger(PhidgetsVORSensor.class.getName()).log(Level.SEVERE, null, ex);
        }
        spatial = null;
    }

    public void doZeroGyro() {
        try {
            zeroGyro();
        } catch (PhidgetException ex) {
            log.warning(ex.toString());
        }
    }

    // delegated methods
    public void zeroGyro() throws PhidgetException {
        if ((spatial != null) && spatial.isAttached()) {
            spatial.zeroGyro();
        }
    }

    public void setCompassCorrectionParameters(double magField, double offset0, double offset1, double offset2, double gain0, double gain1, double gain2, double T0, double T1, double T2, double T3, double T4, double T5) throws PhidgetException {
        spatial.setCompassCorrectionParameters(magField, offset0, offset1, offset2, gain0, gain1, gain2, T0, T1, T2, T3, T4, T5);
    }

    public void resetCompassCorrectionParameters() throws PhidgetException {
        spatial.resetCompassCorrectionParameters();
    }

    public final void removeSpatialDataListener(SpatialDataListener l) {
        spatial.removeSpatialDataListener(l);
    }

    public double getMagneticFieldMin(int index) throws PhidgetException {
        return spatial.getMagneticFieldMin(index);
    }

    public double getMagneticFieldMax(int index) throws PhidgetException {
        return spatial.getMagneticFieldMax(index);
    }

    public double getMagneticField(int index) throws PhidgetException {
        return spatial.getMagneticField(index);
    }

    public int getGyroAxisCount() throws PhidgetException {
        return spatial.getGyroAxisCount();
    }

    // slider doesn't work well with propertyChange callbacks
//    public int getMinSampleIntervalMs() throws PhidgetException {
//        if (spatial.isAttached()) {
//            return spatial.getDataRateMax();
//        } else {
//            return 4;
//        }
//    }
//
//    public int getMaxSampleIntervalMs() throws PhidgetException {
//        if (spatial.isAttached()) {
//            return spatial.getDataRateMin();
//        } else {
//            return 1000;
//        }
//    }
    public int getSampleIntervalMs() throws PhidgetException {
        if (spatial.isAttached()) {
            this.sampleIntervalMs = spatial.getDataRate();
            return sampleIntervalMs;
        } else {
            return sampleIntervalMs;
        }
    }
    private final int[] INT = {4, 8, 16, 32, 64, 128, 256, 512, 1024};

    public void setSampleIntervalMs(int ms) {
        int old = this.sampleIntervalMs;
        try {

            for (int element : INT) {
                if (ms <= element) {
                    ms = element;
                    break;
                }
            }
            if (spatial.isAttached()) {
                spatial.setDataRate(ms);
            }
            this.sampleIntervalMs = ms;
            getSupport().firePropertyChange("sampleIntervalMs", old, this.sampleIntervalMs);
            putInt("sampleIntervalMs", ms);
            log.info("set sample interval to " + ms + " ms");
        } catch (PhidgetException ex) {
            log.log(Level.WARNING, "can''t set interval {0}: {1}", new Object[]{this.sampleIntervalMs, ex.toString()});
        }
    }

    public int getCompassAxisCount() throws PhidgetException {
        return spatial.getCompassAxisCount();
    }

    public double getAngularRateMin(int index) throws PhidgetException {
        return spatial.getAngularRateMin(index);
    }

    public double getAngularRateMax(int index) throws PhidgetException {
        return spatial.getAngularRateMax(index);
    }

    public double getAngularRate(int index) throws PhidgetException {
        return spatial.getAngularRate(index);
    }

    public double getAccelerationMin(int index) throws PhidgetException {
        return spatial.getAccelerationMin(index);
    }

    public double getAccelerationMax(int index) throws PhidgetException {
        return spatial.getAccelerationMax(index);
    }

    public int getAccelerationAxisCount() throws PhidgetException {
        return spatial.getAccelerationAxisCount();
    }

    public double getAcceleration(int index) throws PhidgetException {
        return spatial.getAcceleration(index);
    }
    public final void addSpatialDataListener(SpatialDataListener l) {
        spatial.addSpatialDataListener(l);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (in.getSize() > 0) {
            lastAeTimestamp = in.getLastTimestamp();
        }
//        System.out.println("VORsensor processing " + in);
        if((out==null) || (out.getEventClass()!=PhidgetsSpatialEvent.class)){
            if(in instanceof ApsDvsEventPacket){
                out=new ApsDvsEventPacket(PhidgetsSpatialEvent.class);
            }else if(in instanceof EventPacket){
                out=new EventPacket(PhidgetsSpatialEvent.class);
            }else {
                throw new RuntimeException("bad input event packet class: "+in.toString());
            }
        }
//        checkOutputPacketEventType(PhidgetsSpatialEvent.class);
        OutputEventIterator outItr = out.outputIterator();
        PhidgetsSpatialEvent spatialEvent = null;
        // tricky
        // as we iterate over input events, check for new Phidgets data.
        // If we have some, write it out as PhidgetsSpatialEvents
        // Also write out the regular input data into the output packet.
        //
        // if we iterate over a very long packet (e.g. taken at low rendering rate) we need to
        // make sure that transform updates are still applied during the packet.
        for (BasicEvent o : in) {
            for (spatialEvent = spatialDataQueue.poll(); spatialEvent != null; spatialEvent = spatialDataQueue.poll()) {
                PhidgetsSpatialEvent oe = (PhidgetsSpatialEvent) outItr.nextOutput();
                oe.copyFrom(spatialEvent);
            }
            // periodically update transform so that whatever is using this sensor output gets a new transform in their list that is updated during
            // the enclosing filter processing of the packet.
            maybeCallUpdateObservers(in, o.timestamp); // call listeners if enough time has passed for update. This update should update the camera rotation values.
            outItr.nextOutput().copyFrom(o);
        }
        return out;
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
        lastUpdateTimestamp = timestamp;
        panDC += panRate * dtS;
        tiltDC += tiltRate * dtS;
        rollDC += rollRate * dtS;

        panTranslationDeg = panTranslationFilter.filter(panDC, timestampUs);
        tiltTranslationDeg = tiltTranslationFilter.filter(tiltDC, timestampUs);
        rollDeg = rollFilter.filter(rollDC, timestampUs);

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

    @Override
    synchronized public void resetFilter() {
        resetCalled=true;
        spatialDataQueue.clear();
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
    }

    @Override
    public void initFilter() {
    }
    GLU glu = null;
    GLUquadric expansionQuad;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (lastSpatialData == null) {
            return;
        }
        if (lastSpatialData.getData().length < 1) {
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
        gl.glVertex2d(getPanRate(), getTiltRate());
        gl.glEnd();
        gl.glPopMatrix();

        // draw roll gryo as line left/right
        gl.glPushMatrix();
        gl.glTranslatef(chip.getSizeX() / 2, (chip.getSizeY() * 3) / 4, 0);
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2i(0, 0);
        gl.glVertex2d(getRollRate(), 0);
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
        return panRate;
    }

    /**
     * @return the tiltRate
     */
    public float getTiltRate() {
        return tiltRate;
    }

    /**
     * @return the rollRate
     */
    public float getRollRate() {
        return rollRate;
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

    @Override
    public void update(Observable o, Object arg) {
        radPerPixel = (float) Math.asin((getChip().getPixelWidthUm() * 1e-3f) / lensFocalLengthMm); // updated after chip is set for this filter
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
