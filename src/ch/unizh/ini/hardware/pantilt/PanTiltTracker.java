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
import java.awt.Point;
import java.awt.geom.Point2D;
import java.beans.*;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Vector;
import java.util.logging.Logger;
import javax.media.opengl.GL;
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
    RectangularClusterTracker tracker;
    PanTilt panTilt;
    Calibrator calibrator=new Calibrator();

    public PanTiltTracker(AEChip chip) {
        super(chip);
        tracker = new RectangularClusterTracker(chip);
        setEnclosedFilter(tracker);
        panTilt = new PanTilt();
        loadCalibrationPrefs();
    }

    @Override
    public String getDescription() {
        return "Trackes a single moving object with the pan tilt unit";
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
                float[] pt = calibrator.getTransformedPanTltFromXY(xy);

                float pan = pt[0];
                float tilt = pt[1];
                try {
                    panTilt.setPanTilt(pan, tilt);
                } catch (HardwareInterfaceException e) {
                    log.warning(e.toString());
                }
                panTilt.setLaserOn(true);
           }else{
               panTilt.setLaserOn(false);
           }
        }else{
            panTilt.setLaserOn(false);
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

    private void saveCalibrationPrefs() {
        try {
            // Serialize to a byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput oos = new ObjectOutputStream(bos);
            oos.writeObject(calibrator.transform);
            oos.writeObject(calibrator.sampleVector);
            oos.close();
            // Get the bytes of the serialized object
            byte[] buf = bos.toByteArray();
            getPrefs().putByteArray("PanTiltTracker.calibration", buf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadCalibrationPrefs() {
        // Deserialize from a byte array
        try {
            byte[] bytes = getPrefs().getByteArray("PanTiltTracker.calibration", null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                calibrator.transform = (float[][]) in.readObject();
                calibrator.sampleVector = (Vector<PanTiltCalibrationPoint>) in.readObject();
                in.close();
                log.info("loaded existing transform from vision coordinates to pantilt coordinates");
            } else {
                calibrator = new Calibrator();
            }
        } catch (Exception e) {
            e.printStackTrace();
            calibrator = new Calibrator();
        }
    }

    /** Invokes the calibration GUI. Calibration values are stored persistently as preferences. 
     * Built automatically into filter parameter panel as an action.
     */
    public void doCalibrate() {
        if (calibrator == null) {
            calibrator = new Calibrator();
        }
        calibrator.calibrate();
    }

//    public void paintCalibrationPoints(Graphics g){
//        if(calibrator==null) return;
//    }
    class Calibrator implements PropertyChangeListener {

        transient PanTiltGUI gui;
        float[][] retinaSamples = null;
        float[][] panTiltSamples = null; // sampled pan tilt values for corners

        boolean calibrated = false;
        Vector<PanTiltCalibrationPoint> sampleVector = new Vector<PanTiltCalibrationPoint>();
        float[][] transform = new float[][]{{1, 0, 0}, {0, 1, 0}};
        float[] computedPanTilt = new float[2];

        public String toString() {
            StringBuffer sb = new StringBuffer();
            int rows = transform.length;
            int cols = transform[0].length;
            for (int i = 0; i < rows; i++) {
                sb.append('\n');
                for (int j = 0; j < cols; j++) {
                    sb.append(transform[i][j] + "  ");
                }
            }
            sb.append("");
            return sb.toString();
        }

        void paint(Graphics g) {
            final int r = 6;
            for (PanTiltCalibrationPoint p : sampleVector) {
                Point mp = gui.getMouseFromPanTilt(p.pt);
                g.drawOval(mp.x - r, mp.y - r, r * 2, r * 2);
            }
        }

        void addSample(PanTiltCalibrationPoint sample) {
            if (tracker.getNumClusters() > 0) {
                RectangularClusterTracker.Cluster c = tracker.getClusters().get(0);
                if (c.isVisible()) {
                    Point2D.Float pRet = c.getLocation();
                    sample.ret.x = pRet.x;
                    sample.ret.y = pRet.y;
                    sampleVector.add(sample);
                } else {
                    log.warning("cluster not visible for sample");
                }
            } else {
                log.warning("no cluster for sample, ignoring");
            }
        }

        float[][] getPanTiltSamples() {
            int n = getNumSamples();
            float[][] m = new float[2][n];
            for (int i = 0; i < n; i++) {
                m[0][i] = sampleVector.get(i).pt.x;
                m[1][i] = sampleVector.get(i).pt.y;
            }
            return m;
        }

        float[][] getRetinaSamples() {
            int n = getNumSamples();
            float[][] m = new float[3][n];
            for (int i = 0; i < n; i++) {
                m[0][i] = sampleVector.get(i).ret.x;
                m[1][i] = sampleVector.get(i).ret.y;
                m[2][i] = 1;
            }
            return m;
        }

        int getNumSamples() {
            return sampleVector.size();
        }

        void calibrate() {
            panTilt.acquire();
            if (gui == null) {
                gui = new PanTiltGUI(panTilt, PanTiltTracker.this);
                gui.getSupport().addPropertyChangeListener(this);
            }
            gui.setVisible(true);
        }

        /** comes here from GUI with pan tilt settings */
        public void propertyChange(PropertyChangeEvent evt) {
            // property changes carry information about pan tilt values for particular retinal locations of the pan tilt aim
            if (evt.getPropertyName().equals(PanTiltGUI.Message.AddSample.name())) {
                PanTiltCalibrationPoint p = (PanTiltCalibrationPoint) evt.getNewValue();
                addSample(p);
                computeCalibration();
            } else if (evt.getPropertyName().equals(PanTiltGUI.Message.ComputeCalibration.name())) {
                computeCalibration();
                log.info("computing calibration");
                saveCalibrationPrefs();
                panTilt.release();
            } else if (evt.getPropertyName().equals(PanTiltGUI.Message.ClearSamples.name())) {
                sampleVector.clear();
            }else if(evt.getPropertyName().equals(PanTiltGUI.Message.RevertCalibration.name())){
                loadCalibrationPrefs();
            } else if (evt.getPropertyName().equals(PanTiltGUI.Message.EraseLastSample.name())) {
                if (sampleVector.size() > 0) {
                    Object s = sampleVector.lastElement();
                    sampleVector.remove(s);
                }
            } else if (evt.getPropertyName().equals(PanTiltGUI.Message.ShowCalibration.name())) {
                showCalibration();
            } else {
                log.warning("bogus PropertyChangeEvent " + evt);
            }
        }

        void showCalibration() {
            Thread t = new Thread() {

                public void run() {
                    for (PanTiltCalibrationPoint p : sampleVector) {
                        log.info("showing " + p);
                        try {
                            panTilt.setPanTilt(p.pt.x, p.pt.y);
                        } catch (HardwareInterfaceException e) {
                        }
                        try {
                            sleep(500);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            };
            t.start();
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
//            if (getNumSamples() < 4) {
//                JOptionPane.showMessageDialog(gui, "Only captured " + getNumSamples() + ": need at least 3 non-singular points to calibrate", "Can't calibrate", JOptionPane.WARNING_MESSAGE);
//                return;
//            }
            log.info("calibration computing");
            panTiltSamples = getPanTiltSamples();
            retinaSamples = getRetinaSamples();
            float[][] cctrans = new float[3][3];
            float[][] ctrans = Matrix.transposeMatrix(retinaSamples);
            Matrix.multiply(retinaSamples, ctrans, cctrans);
            Matrix.invert(cctrans); // in place

            float[][] ctranscctrans = new float[getNumSamples()][3];
            Matrix.multiply(ctrans, cctrans, ctranscctrans);
            Matrix.multiply(panTiltSamples, ctranscctrans, transform);
            System.out.println("pantilt samples");
            Matrix.print(panTiltSamples);
            System.out.println("retina samples");
            Matrix.print(retinaSamples);
            System.out.println("transform from retina to pantilt");
            Matrix.print(transform);
        }

        private float[] getTransformedPanTltFromXY(float[] xy) {
            Matrix.multiply(transform, xy, computedPanTilt);
            return computedPanTilt;
        }
    }
    
       public float getJitterAmplitude() {
        return panTilt.getJitterAmplitude();
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    public void setJitterAmplitude(float jitterAmplitude) {
        panTilt.setJitterAmplitude(jitterAmplitude);
    }

    public float getJitterFreqHz() {
        return panTilt.getJitterFreqHz();
    }

    /** The frequency of the jitter
     * 
     * @param jitterFreqHz in Hz
     */
    public void setJitterFreqHz(float jitterFreqHz) {
        panTilt.setJitterFreqHz(jitterFreqHz);
    }
    
 
}
