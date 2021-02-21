/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import ch.unizh.ini.jaer.projects.laser3d.HistogramData;
import ch.unizh.ini.jaer.projects.npp.RoShamBoCNN;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.awt.TextRenderer;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import gnu.io.NRSerialPort;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Tests DVS latency using Arduino DVSLatencyMeasurement.
 *
 * @author tobi, Feb 2021
 */
@Description("Measures DVS-computer latency using 2 LEDs controlled by Ardunino")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DVSLatencyMeasurement extends EventFilter2DMouseAdaptor implements FrameAnnotater {

    private RectangularClusterTracker tracker; // adjust it to detect LED cluster from either LED
    NRSerialPort serialPort = null;
    private int serialBaudRate = getInt("serialBaudRate", 115200);
    private String serialPortName = getString("serialPortName", "COM3");
    private DataOutputStream serialPortOutputStream = null;
    private DataInputStream serialPortInputStream = null;
    public boolean scaleHistogramsIncludingOverflow = getBoolean("scaleHistogramsIncludingOverflow", true);
    final private static float[] TEMPORAL_HIST_COLOR = {0, 0, .8f, .3f},
            SPATIAL_HIST_COLOR = {.6f, .4f, .2f, .6f},
            HIST_OVERFLOW_COLOR = {.8f, .3f, .2f, .6f};
    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f};

    private Point currentMousePoint = null;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    private TextRenderer renderer = null;
    private int histNumBins = getInt("histNumBins", 100);
    protected boolean autoScaleHist = getBoolean("autoScaleHist", true);
    protected int histMin = getInt("histMin", 0);
    protected int histMax = getInt("histMax", 30000);

    private TimeStats timeStats;
    private EngineeringFormat fmt = new EngineeringFormat();

    private int lastClusterID = 0;
    private int led = 0;
    private Long lastToggleTimeNs = null;

    public DVSLatencyMeasurement(AEChip chip) {
        super(chip);
        tracker = new RectangularClusterTracker(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(tracker);
        setEnclosedFilterChain(chain);
        setPropertyTooltip("serialPortName", "Name of serial port to send robot commands to");
        setPropertyTooltip("serialBaudRate", "Baud rate (default 115200), upper limit 12000000");

    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        getEnclosedFilterChain().filterPacket(in); // detect LED
        LinkedList<Cluster> clusters = tracker.getVisibleClusters();
        if (clusters.size() == 0) {
            if (led != 1) {
                doLed1On();
            }
        } else if (clusters.size() == 1) {
            Cluster c = clusters.getFirst();
            int id = c.getClusterNumber();
            if (id == lastClusterID) {
                doToggleLeds();
                lastClusterID = id;
            }
            lastClusterID = id;
        } else if (clusters.size() > 1) {
//            Cluster c = clusters.getLast();
//            int id = c.getClusterNumber();
//            if (id == lastClusterID) {
//                doToggleLeds();
//                lastClusterID = id;
//            }
        }
        return in;
    }

    @Override
    public void resetFilter() {
        tracker.resetFilter();
        timeStats.reset();
    }

    @Override
    public void initFilter() {
        timeStats = new TimeStats();
        if ((chip.getCanvas() != null) && (chip.getCanvas().getCanvas() != null)) {
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
            renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        tracker.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        canvas = chip.getCanvas();
        glCanvas = (GLCanvas) canvas.getCanvas();

        float csx = chip.getSizeX(), csy = chip.getSizeY();
        gl.glColor3f(1, 1, 1);
        timeStats.draw(gl);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (!yes) {
            closeSerial();
        } else {
            try {
                openSerial();
            } catch (Exception ex) {
                log.warning("caught exception enabling serial port when filter was enabled: " + ex.toString());
            }
        }
    }

    public void doTurnOffLeds() {
        sendByte('0');
        led = 0;
    }

    public void doLed1On() {
        sendByte('1');
        led = 1;
    }

    public void doLed2On() {
        sendByte('2');
        led = 2;
    }

    public void doToggleLeds() {
        if (led == 0) {
            doLed1On();
        } else if (led == 1) {
            doLed2On();
        } else {
            doLed1On();
        }
        long t = System.nanoTime();
        if (lastToggleTimeNs != null) {
            long dt = t - lastToggleTimeNs;
            timeStats.addSample((int) (dt / 1000));
        }
        lastToggleTimeNs = t;
    }

    private void sendByte(int cmd) {
        if (serialPortOutputStream != null) {
            try {
                serialPortOutputStream.write((byte) cmd);
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
    }

    private void openSerial() throws IOException {
        if (serialPort != null) {
            closeSerial();
        }
        StringBuilder sb = new StringBuilder("List of all available serial ports: ");
        final Set<String> availableSerialPorts = NRSerialPort.getAvailableSerialPorts();
        if (availableSerialPorts.isEmpty()) {
            sb.append("\nNo ports found, sorry.  If you are on linux, serial port support may suffer");
        } else {
            for (String s : availableSerialPorts) {
                sb.append(s).append(" ");
            }
        }
        log.info(sb.toString());
        if (!availableSerialPorts.contains(serialPortName)) {
            final String warningString = serialPortName + " is not in avaiable " + sb.toString();
            log.warning(warningString);
            showWarningDialogInSwingThread(warningString, "Serial port not available");
            return;
        }

        serialPort = new NRSerialPort(serialPortName, serialBaudRate);
        if (serialPort == null) {
            final String warningString = "null serial port returned when trying to open " + serialPortName + "; available " + sb.toString();
            log.warning(warningString);
            showWarningDialogInSwingThread(warningString, "Serial port not available");
            return;
        }
        serialPort.connect();
        serialPortOutputStream = new DataOutputStream(serialPort.getOutputStream());
        serialPortInputStream = new DataInputStream(serialPort.getInputStream());
        log.info("opened serial port " + serialPortName + " with baud rate=" + serialBaudRate);
    }

    private void closeSerial() {
        if (serialPortOutputStream != null) {
            try {
                serialPortOutputStream.write((byte) '0'); // rest; turn off servos
                serialPortOutputStream.close();
            } catch (IOException ex) {
                Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
            }
            serialPortOutputStream = null;
        }
        if ((serialPort != null) && serialPort.isConnected()) {
            serialPort.disconnect();
            serialPort = null;
        }
//        log.info("closed serial port");
    }

    /**
     * @return the serialBaudRate
     */
    public int getSerialBaudRate() {
        return serialBaudRate;
    }

    /**
     * @param serialBaudRate the serialBaudRate to set
     */
    public void setSerialBaudRate(int serialBaudRate) {
        try {
            this.serialBaudRate = serialBaudRate;
            putInt("serialBaudRate", serialBaudRate);
            openSerial();
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * @return the serialPortName
     */
    public String getSerialPortName() {
        return serialPortName;
    }

    /**
     * @param serialPortName the serialPortName to set
     */
    public void setSerialPortName(String serialPortName) {
        try {
            this.serialPortName = serialPortName;
            putString("serialPortName", serialPortName);
            openSerial();
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Sets the currentMousePoint and currentAddress[] array
     *
     * @param e
     */
    @Override
    public void mouseMoved(MouseEvent e) {
        currentMousePoint = getMousePoint(e);
    }

    private Point getMousePoint(MouseEvent e) {
        synchronized (glCanvas) { // sync here on opengl canvas because getPixelFromMouseEvent calls opengl and we don't want that during rendering
            return canvas.getPixelFromMouseEvent(e);
        }
    }

    /**
     * @return the histNumBins
     */
    public int getHistNumBins() {
        return histNumBins;
    }

    /**
     * @param histNumBins the histNumBins to set
     */
    synchronized public void setHistNumBins(int histNumBins) {
        if (histNumBins < 2) {
            histNumBins = 2;
        }
        this.histNumBins = histNumBins;
        putInt("histNumBins", histNumBins);
        timeStats.reset();
    }

    /**
     * @return the autoScaleHist
     */
    public boolean isAutoScaleHist() {
        return autoScaleHist;
    }

    /**
     * @param autoScaleHist the autoScaleHist to set
     */
    public void setAutoScaleHist(boolean autoScaleHist) {
        this.autoScaleHist = autoScaleHist;
        putBoolean("autoScaleHist", autoScaleHist);
    }

    /**
     * @return the histMin
     */
    public int getHistMin() {
        return histMin;
    }

    /**
     * @param histMin the histMin to set
     */
    public void setHistMin(int histMin) {
        int old = this.histMin;
        this.histMin = histMin;
        putInt("histMin", histMin);
        getSupport().firePropertyChange("histMin", old, histMin);
        timeStats.reset();
    }

    /**
     * @return the histMax
     */
    public int getHistMax() {
        return histMax;
    }

    /**
     * @param histMax the histMax to set
     */
    public void setHistMax(int histMax) {
        int old = this.histMax;
        this.histMax = histMax;
        putInt("histMax", histMax);
        getSupport().firePropertyChange("histMax", old, histMax);
        timeStats.reset();
    }

    private class TimeStats {

        int[] bins = new int[histNumBins];
        int lessCount = 0, moreCount = 0;
        int maxCount = 0;
        boolean virgin = true;
        float mean = 0, var = 0, std = 0, cov = 0;
        long sum = 0, sum2 = 0;
        int count;

        public TimeStats() {
        }

        void addSample(int sample) {
            int bin = getSampleBin(sample);
            if (bin < 0) {
                lessCount++;
                if (scaleHistogramsIncludingOverflow && (lessCount > maxCount)) {
                    maxCount = lessCount;
                }
            } else if (bin >= histNumBins) {
                moreCount++;
                if (scaleHistogramsIncludingOverflow && (moreCount > maxCount)) {
                    maxCount = moreCount;
                }
            } else {
                int v = ++bins[bin];
                if (v > maxCount) {
                    maxCount = v;
                }
            }
            count++;
            sum += sample;
            sum2 += sample * sample;
            if (count > 2) {
                mean = (float) sum / count;
                var = (float) sum2 / count - mean * mean;
                std = (float) Math.sqrt(var);
                cov = std / mean;
            }

        }

        /**
         * Draws the histogram
         *
         */
        void draw(GL2 gl) {
            float dx = (float) (chip.getSizeX() - 2) / (histNumBins + 2);
            float sy = (float) (chip.getSizeY() - 2) / maxCount;

            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(1, 1);
            gl.glVertex2f(chip.getSizeX() - 1, 1);
            gl.glEnd();

            if (lessCount > 0) {
                gl.glPushAttrib(GL2.GL_COLOR | GL.GL_LINE_WIDTH);
//                gl.glColor4fv(HIST_OVERFLOW_COLOR, 0);
                gl.glBegin(GL.GL_LINE_STRIP);

                float y = 1 + (sy * lessCount);
                float x1 = -dx, x2 = x1 + dx;
                gl.glVertex2f(x1, 1);
                gl.glVertex2f(x1, y);
                gl.glVertex2f(x2, y);
                gl.glVertex2f(x2, 1);
                gl.glEnd();
                gl.glPopAttrib();
            }
            if (moreCount > 0) {
                gl.glPushAttrib(GL2.GL_COLOR | GL.GL_LINE_WIDTH);
//                gl.glColor4fv(HIST_OVERFLOW_COLOR, 0);
                gl.glBegin(GL.GL_LINE_STRIP);

                float y = 1 + (sy * moreCount);
                float x1 = 1 + (dx * (histNumBins + 2)), x2 = x1 + dx;
                gl.glVertex2f(x1, 1);
                gl.glVertex2f(x1, y);
                gl.glVertex2f(x2, y);
                gl.glVertex2f(x2, 1);
                gl.glEnd();
                gl.glPopAttrib();
            }
            if (maxCount > 0) {
                gl.glPushAttrib(GL2.GL_COLOR | GL.GL_LINE_WIDTH);
                gl.glColor4fv(SPATIAL_HIST_COLOR, 0);
                gl.glBegin(GL.GL_LINE_STRIP);
                for (int i = 0; i < bins.length; i++) {
                    float y = 1 + (sy * bins[i]);
                    float x1 = 1 + (dx * i), x2 = x1 + dx;
                    gl.glVertex2f(x1, 1);
                    gl.glVertex2f(x1, y);
                    gl.glVertex2f(x2, y);
                    gl.glVertex2f(x2, 1);
                }
                gl.glEnd();
                gl.glPopAttrib();
            }

            if (currentMousePoint != null) {
                if (currentMousePoint.y <= 0) {
                    float sampleValue = ((float) currentMousePoint.x / chip.getSizeX()) * (1e-6f * histMax);
                    gl.glColor3fv(SELECT_COLOR, 0);
                    renderer.begin3DRendering();
                    renderer.draw3D(String.format("%ss", fmt.format(sampleValue)), currentMousePoint.x, -8, 0, .3f);
                    renderer.end3DRendering();
                    gl.glLineWidth(3);
                    gl.glColor3fv(SELECT_COLOR, 0);
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(currentMousePoint.x, 0);
                    gl.glVertex2f(currentMousePoint.x, chip.getSizeY());
                    gl.glEnd();
                }
            }
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .9f);
            MultilineAnnotationTextRenderer.setScale(.3f);
            String meanS = fmt.format(mean * 1e-6f);
            String stdS = fmt.format(std * 1e-6);
            MultilineAnnotationTextRenderer.renderMultilineString(String.format("Timing: mean=%s+-%s s",
                    meanS, stdS));

        }

        void draw(GL2 gl, float lineWidth, float[] color) {
            gl.glPushAttrib(GL2.GL_COLOR | GL.GL_LINE_WIDTH);
            gl.glLineWidth(lineWidth);
            gl.glColor4fv(color, 0);
            draw(gl);
            gl.glPopAttrib();
        }

        private void reset() {
            if ((bins == null) || (bins.length != histNumBins)) {
                bins = new int[histNumBins];
            } else {
                Arrays.fill(bins, 0);
            }
            lessCount = 0;
            moreCount = 0;
            maxCount = 0;
            virgin = true;
            count = 0;
            sum = 0;
            sum2 = 0;
            if (autoScaleHist) {
                histMax = 0;
                histMin = 100000;
            }
        }

        private int getSampleBin(int sample) {
            int bin = (int) Math.floor((histNumBins * ((float) sample - histMin)) / (histMax - histMin));
            if (autoScaleHist && bin < 0) {
                setHistMin(sample);
                reset();
                return 0;
            } else if (autoScaleHist && bin >= histNumBins) {
                setHistMax(sample);
                reset();
                return histNumBins;
            }
            return bin;
        }
    }
}
