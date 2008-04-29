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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.beans.*;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Vector;
import java.util.concurrent.locks.*;
import java.util.logging.Logger;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JOptionPane;

/**
 * Demonstrates tracking object(s) and targeting them with the pan tilt unit. A laser pointer on the pan tilt
 * can show where it is aimed. Developed for Sardinia Capo Cacia Cognitive Neuromorphic Engineering Workshop, April 2008.
 * Includes a 4 point calibration based on an interactive GUI.
 * 
 * @author tobi, Ken Knoblauch
 */
public class PanTiltTracker extends EventFilter2D implements FrameAnnotater {

    static Logger log = Logger.getLogger("PanTiltTracker");
    float[][] transform = new float[2][3];
    RectangularClusterTracker tracker;
    PanTilt panTilt;
    Calibrator calibrator;

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
                log.info("loaded existing transform from vision coordinates to pantilt coordinates");
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
        log.info("initializing transform from vision coordinates to pantilt coordinates");
        transform = new float[][]{{1, 0, 0}, {0, 1, 0}};
    }

    /** Invokes the calibration GUI. Calibration values are stored persistently as preferences. 
     * Built automatically into filter parameter panel as an action.
     */
    public void doCalibrate() {
        if(calibrator==null) calibrator=new Calibrator();
        calibrator.calibrate();
    }
    
//    public void paintCalibrationPoints(Graphics g){
//        if(calibrator==null) return;
//    }

    private class Calibrator implements PropertyChangeListener {

        PanTiltGUI gui;
        float[][] retinaSamples=null;
        float[][] panTiltSamples=null; // sampled pan tilt values for corners
        boolean calibrated = false;
        Samples samples = new Samples();

        Calibrator() {
            Matrix.print(transform);
        }
        
        private class Samples {

            class Sample {

                Point2D.Float ret, pt;

                Sample(Point2D.Float ret, Point2D.Float pt) {
                    this.ret = ret;
                    this.pt = pt;
                }
            }
            Vector<Sample> samples = new Vector<Sample>();

            void addSample(Point2D.Float ptSample) {
                if (tracker.getNumClusters() > 0) {
                    RectangularClusterTracker.Cluster c = tracker.getClusters().get(0);
                    if (c.isVisible()) {
                        Point2D.Float pRet = c.getLocation();
                        samples.add(new Sample(pRet, ptSample));
                    }else{
                        log.warning("cluster not visible for sample");
                    }
                }else{
                    log.warning("no cluster for sample, ignoring");
                }
            }

            float[][] getPanTiltSamples() {
                int n = getNumSamples();
                float[][] m = new float[2][n];
                for (int i = 0; i < n; i++) {
                    m[0][i] = samples.get(i).pt.x;
                    m[1][i] = samples.get(i).pt.y;
                }
                return m;
            }

            float[][] getRetinaSamples() {
                int n = getNumSamples();
                float[][] m = new float[2][n];
                for (int i = 0; i < n; i++) {
                    m[0][i] = samples.get(i).ret.x;
                    m[1][i] = samples.get(i).ret.y;
                }
                return m;
            }

            int getNumSamples() {
                return samples.size();
            }
        }

        void calibrate() {
            panTilt.acquire();
            gui = new PanTiltGUI(panTilt);
            gui.getSupport().addPropertyChangeListener(this);
            gui.setVisible(true);
        }

        /** comes here from GUI with pan tilt settings */
        public void propertyChange(PropertyChangeEvent evt) {
            Point2D.Float p = (Point2D.Float) evt.getNewValue();
            // property changes carry information about pan tilt values for particular retinal locations of the pan tilt aim
            if (evt.getPropertyName().equals(PanTiltGUI.Message.AddSample.name())) {
                samples.addSample(p);
            } else if (evt.getPropertyName().equals(PanTiltGUI.Message.ComputeCalibration)) {
                computeCalibration();
                log.info("computing calibration");
                putTransformPrefs();
                panTilt.release();
            } else {
                log.warning("bogus PropertyChangeEvent " + evt);
            }
        }

        /** 
         * pantiltvalues=transform*retinavalues; P=TR.
         * we want to find T.
         * transform is a 2x3 matrix
         * retinavalues is a 3xn matrix
         * pantiltvalues is a 2xn matrix
         * 
         * This routine finds the least squares fit from the retina to pantilt coordinates.
         */
        private void computeCalibration() {
            if(samples.getNumSamples()<4){
                JOptionPane.showMessageDialog(gui, "Only captured "+samples.getNumSamples()+": need at least 3 non-singular points to calibrate","Can't calibrate", JOptionPane.WARNING_MESSAGE);
                return;
            }
            log.info("calibration computing");
            panTiltSamples=samples.getPanTiltSamples();
            retinaSamples=samples.getRetinaSamples();
            float[][] cctrans = new float[3][3];
            float[][] ctrans = Matrix.transposeMatrix(retinaSamples);
            Matrix.multiply(retinaSamples, ctrans, cctrans);
            Matrix.invert(cctrans); // in place
            float[][] ctranscctrans = new float[samples.getNumSamples()][3];
            Matrix.multiply(ctrans, cctrans, ctranscctrans);
            Matrix.multiply(panTiltSamples, ctranscctrans, transform);
            System.out.println("pantilt samples");
            Matrix.print(panTiltSamples);
            System.out.println("retina samples");
            Matrix.print(retinaSamples);
            System.out.println("transform from retina to pantilt");
            Matrix.print(transform);
        }
    }
}
