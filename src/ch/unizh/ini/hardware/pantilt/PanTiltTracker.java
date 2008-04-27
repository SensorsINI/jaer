/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.hardware.pantilt;

import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.util.Matrix;
import ch.unizh.ini.caviar.eventprocessing.tracking.RectangularClusterTracker;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.beans.*;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.concurrent.locks.*;
import java.util.logging.Logger;
import javax.media.opengl.GLAutoDrawable;

/**
 * Demonstrates tracking object(s) and targeting them with the pan tilt unit. A laser pointer on the pan tilt
 * can show where it is aimed. Developed for Sardinia Capo Cacia Cognitive Neuromorphic Engineering Workshop, April 2008.
 * 
 * @author tobi
 */
public class PanTiltTracker extends EventFilter2D implements FrameAnnotater {

    private float gain = getPrefs().getFloat("PanTiltTracker.gain", 1);

    {
        setPropertyTooltip("gain", "gain from x,y to pan,tilt coordinates");
    }
    private float angle = getPrefs().getFloat("PanTiltTracker.angle", 0);

    {
        setPropertyTooltip("angle", "angle in degrees (CCW from x axis) between x,y and pan,tilt coordinates");
    }
    private int xShift = getPrefs().getInt("PanTiltTracker.xShift", 0);

    {
        setPropertyTooltip("xShift", "x in pixels added to x,y -> pan,tilt transform");
    }
    private int yShift = getPrefs().getInt("PanTiltTracker.yShift", 0);

    {
        setPropertyTooltip("yShift", "y in pixels added to x,y -> pan,tilt transform");
    }
    static Logger log = Logger.getLogger("PanTiltTracker");
    float[][] transform = new float[2][3];
    RectangularClusterTracker tracker;
    PanTilt panTilt;

    public PanTiltTracker(AEChip chip) {
        super(chip);
        tracker = new RectangularClusterTracker(chip);
        setEnclosedFilter(tracker);
        panTilt = new PanTilt();
        getTransformPrefs();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        tracker.filterPacket(in);
        if (panTilt.isLockOwned()) {
            return in;
        }
        if (tracker.getNumClusters() > 0) {
            RectangularClusterTracker.Cluster c = tracker.getClusters().get(0);
            if (c.isVisible()) {
                Point2D.Float p = c.getLocation();
                float[] xy = {p.x, p.y, 1};
                float[] pt = new float[2];
                Matrix.multiply(transform, xy, pt);

                float pan = pt[0];
                float tilt = pt[1];
                try {
                    panTilt.setPanTilt(pan, tilt);
                } catch (HardwareInterfaceException e) {
                    log.warning(e.toString());
                }
            }
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
    }

    private void putTransformPrefs() {
        try {
            // Serialize to a byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput oos = new ObjectOutputStream(bos);
            oos.writeObject(transform);
            oos.close();
            // Get the bytes of the serialized object
            byte[] buf = bos.toByteArray();
            getPrefs().putByteArray("PanTiltTracker.trans", buf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void getTransformPrefs() {
        // Deserialize from a byte array
        try {
            byte[] bytes = getPrefs().getByteArray("PanTiltTracker.trans", null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                transform = (float[][]) in.readObject();
                in.close();
            } else {
                initTransform();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (transform == null) {
                initTransform();
            }
        }
    }

    private void initTransform() {
        transform = new float[][]{{1, 0, 0}, {0, 1, 0}};
    }

    public float getGain() {
        return gain;
    }

    public void setGain(float gain) {
        this.gain = gain;
        getPrefs().putFloat("PanTiltTracker.gain", gain);
    }

    public float getAngle() {
        return angle;
    }

    public void setAngle(float angle) {
        this.angle = angle;
        getPrefs().putFloat("PanTiltTracker.angle", angle);
    }

    public int getXShift() {
        return xShift;
    }

    public void setXShift(int xShift) {
        this.xShift = xShift;
        getPrefs().putInt("PanTiltTracker.xShift", xShift);
    }

    public int getYShift() {
        return yShift;
    }

    public void setYShift(int yShift) {
        this.yShift = yShift;
        getPrefs().putInt("PanTiltTracker.yShift", yShift);
    }

    public void doCalibrate() {
        new Calibrator().calibrate();
    }

    private class Calibrator implements PropertyChangeListener {

        PanTiltGUI gui;
        final float[][] retinaSamples = {{0, chip.getSizeX(), 0, chip.getSizeX()}, {chip.getSizeY(), chip.getSizeY(), 0, 0}, {1, 1, 1, 1}}; // 4 corners of retina
        float[][] panTiltSamples = new float[2][4]; // sampled pan tilt values for corners
        boolean calibrated = false;

        Calibrator() {
            Matrix.print(transform);
        }

        void calibrate() {
            panTilt.acquire();
            gui = new PanTiltGUI(panTilt);
            gui.getSupport().addPropertyChangeListener(this);
            gui.setVisible(true);
        }

        void setSamples(int sample, Point2D.Float p) {
            panTiltSamples[0][sample] = p.x;
            panTiltSamples[1][sample] = p.y;
            if (tracker.getNumClusters() > 0) {
                RectangularClusterTracker.Cluster c = tracker.getClusters().get(0);
                if (c.isVisible()) {
                    Point2D.Float pRet = c.getLocation();
                    retinaSamples[0][sample] = pRet.x;
                    retinaSamples[1][sample] = pRet.y;
                }
            }
            log.info(String.format("Retina %.1f,%.1f\tPanTilt: %.2f,%.2f",retinaSamples[0][sample],retinaSamples[1][sample],panTiltSamples[0][sample],panTiltSamples[1][sample]));
        }

        /** comes here from GUI with pan tilt settings */
        public void propertyChange(PropertyChangeEvent evt) {
            Point2D.Float p = (Point2D.Float) evt.getNewValue();
            // property changes carry information about pan tilt values for particular retinal locations of the pan tilt aim
            if (evt.getPropertyName().equals("UL")) {
                setSamples(0, p);
            } else if (evt.getPropertyName().equals("UR")) {
                setSamples(1, p);
            } else if (evt.getPropertyName().equals("LL")) {
                setSamples(2, p);
            } else if (evt.getPropertyName().equals("LR")) {
                setSamples(3, p);
            } else if (evt.getPropertyName().equals("done")) {
                computeCalibration();
                log.info("computing calibration");
                putTransformPrefs();
                panTilt.release();
            } else {
                log.warning("bogus PropertyChangeEvent " + evt);
            }
        }

        private void computeCalibration() {
            log.info("calibration computing");
            Matrix.print(panTiltSamples);
            float[][] cctrans = new float[3][3];
            float[][] ctrans = Matrix.transposeMatrix(retinaSamples);
            Matrix.multiply(retinaSamples, ctrans, cctrans);
            Matrix.invert(cctrans); // in place
            float[][] ctranscctrans = new float[4][3];
            Matrix.multiply(ctrans, cctrans, ctranscctrans);
            Matrix.multiply(panTiltSamples, ctranscctrans, transform);
            Matrix.print(transform);
        }
    }
}
