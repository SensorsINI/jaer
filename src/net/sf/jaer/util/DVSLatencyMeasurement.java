/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import ch.unizh.ini.jaer.projects.laser3d.HistogramData;
import ch.unizh.ini.jaer.projects.npp.RoShamBoCNN;
import com.google.common.collect.EvictingQueue;
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
import net.sf.jaer.eventprocessing.filter.NoiseTesterFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import sun.tools.java.ClassDefinition;

/**
 * Tests DVS latency using Arduino DVSLatencyMeasurement.
 *
 * @author tobi, Feb 2021
 */
@Description("Measures DVS-computer latency using 2 LEDs controlled by Ardunino")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DVSLatencyMeasurement extends EventFilter2DMouseAdaptor implements FrameAnnotater {

    NRSerialPort serialPort = null;
    private int serialBaudRate = getInt("serialBaudRate", 115200);
    private String serialPortName = getString("serialPortName", "COM3");
    private DataOutputStream serialPortOutputStream = null;
    private DataInputStream serialPortInputStream = null;

    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f};

    private Point currentMousePoint = null;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    private TextRenderer renderer = null;

    private TimeStats timeStats;
    public boolean scaleHistogramsIncludingOverflow = getBoolean("scaleHistogramsIncludingOverflow", true);
    private int histNumBins = getInt("histNumBins", 300);
    protected int statsWindowLength = getInt("statsWindowLength", 300);
    protected boolean autoScaleHist = getBoolean("autoScaleHist", false);
    protected boolean logLogScale = getBoolean("logLogScale", false);
    protected int histMin = getInt("histMin", 0);
    protected int histMax = getInt("histMax", 30000);

    protected boolean pingTest = false;

    private EngineeringFormat fmt = new EngineeringFormat();

    private RectangularClusterTracker tracker; // adjust it to detect LED cluster from either LED
    private int lastClusterID = 0, lastTimestamp = 0;
    private int led = 0;

    private Long lastToggleTimeNs = null;

    private TobiLogger tobiLogger;

    public DVSLatencyMeasurement(AEChip chip) {
        super(chip);
        tracker = new RectangularClusterTracker(chip);
        tracker.setMaxNumClusters(2);
        tracker.setClusterMassDecayTauUs(100);
        tracker.setPathsEnabled(false);
        FilterChain chain = new FilterChain(chip);
        chain.add(tracker);
        setEnclosedFilterChain(chain);
        setPropertyTooltip("serialPortName", "Name of serial port to send Arduino Nano commands to");
        setPropertyTooltip("serialBaudRate", "Baud rate (default 115200), upper limit 12000000");
        setPropertyTooltip("autoScaleHist", "Automatically determine bounds for histogram of intervals");
        setPropertyTooltip("histMin", "Minimum interval");
        setPropertyTooltip("histMax", "Maximum interval");
        setPropertyTooltip("histNumBins", "Number of histogram bins");
        setPropertyTooltip("statsWindowLength", "Number of samples over which to compute statistics");
        setPropertyTooltip("led1On", "Turn on LED 1");
        setPropertyTooltip("led2On", "Turn on LED 2");
        setPropertyTooltip("toggleLeds", "Toggle betweeen LEDs");
        setPropertyTooltip("turnOffLeds", "Turn off both LEDs");
        setPropertyTooltip("logLogScale", "Use log-log histogram scale");
        setPropertyTooltip("pingTest", "Test only roundtrip latency to the Arduino");
        setPropertyTooltip("logging", "Toggle ON/OFF logging of delta times to TobiLogger CSV file");
        tobiLogger = new TobiLogger("DVSLatencyMeasurement", "DVSLatencyMeasurement");
        tobiLogger.setColumnHeaderLine("lastTimestamp(us),dt(us)");
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (!pingTest) {
            getEnclosedFilterChain().filterPacket(in); // detect LED
            LinkedList<Cluster> clusters = tracker.getVisibleClusters();
            if (clusters.size() == 0) { // if no LED detected, start flashing LED1
                if (led != 1) {
                    doLed1On();
                }
            } else if (clusters.size() == 1) { // if only 1 cluster, then if same as last one, swap LEDs
                Cluster c = clusters.getFirst();
                int id = c.getClusterNumber();
                if (id == lastClusterID) {
                    doToggleLeds();
                    lastClusterID = id;
                }
                lastClusterID = id;
            } else if (clusters.size() > 1) { // both clusters still active
//            Cluster c = clusters.getLast();
//            int id = c.getClusterNumber();
//            if (id == lastClusterID) {
//                doToggleLeds();
//                lastClusterID = id;
//            }
            }
        } else {
            doPingTest();
        }
        if (in.getSize() > 0) {
            lastTimestamp = in.getLastTimestamp();
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

    public void doToggleOnLogging() {
        tobiLogger.setEnabled(true);
    }

    public void doToggleOffLogging() {
        tobiLogger.setEnabled(false);
        tobiLogger.showFolderInDesktop();
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

    public void doPingTest() {
        long start = System.nanoTime();
        sendByte('p');
        if (serialPortInputStream != null) {
            try {
                while (serialPortInputStream.available() == 0
                        && !Thread.interrupted()
                        && System.nanoTime() - start < 100000000L);// wait for a byte
                if (serialPortInputStream.available() == 0) {
                    log.warning("timeout for ping");
                    return;
                }
                while(serialPortInputStream.available()>0){
                    int c = serialPortInputStream.read(); // drain buffer
//                System.out.print((char) c);
                }
                long end = System.nanoTime();
                timeStats.addSample((int) (end - start) / 1000);
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
        // drain serial port chars from Arduino startup
        try {
            sendByte('p'); // stimulute a pong response, should result in at least a p return
            log.info("Draining startup serial port output from Arduino:");
            long start = System.currentTimeMillis();
            while (!Thread.interrupted() && System.currentTimeMillis() - start < 900L) {
                while (serialPortInputStream.available() > 0) {
                    int c = serialPortInputStream.read();
                    System.out.print((char) c);
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                }
            }
        } catch (IOException ex) {
            log.warning(ex.toString());
        }

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
     * @return the logLogScale
     */
    public boolean isLogLogScale() {
        return logLogScale;
    }

    /**
     * @param logLogScale the logLogScale to set
     */
    public void setLogLogScale(boolean logLogScale) {
        this.logLogScale = logLogScale;
        putBoolean("logLogScale", logLogScale);
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
        if (histMin > histMax) {
            histMin = histMax;
        }
        int old = this.histMin;
        this.histMin = histMin;
        putInt("histMin", histMin);
        getSupport().firePropertyChange("histMin", old, histMin);
        if (old != this.histMin) {
            timeStats.reset();
        }
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
        if (histMax < histMin) {
            histMax = histMin;
        }
        int old = this.histMax;
        this.histMax = histMax;
        putInt("histMax", histMax);
        getSupport().firePropertyChange("histMax", old, histMax);
        if (old != this.histMax) {
            timeStats.reset();
        }
    }

    /**
     * @return the statsWindowLength
     */
    public int getStatsWindowLength() {
        return statsWindowLength;
    }

    /**
     * @param statsWindowLength the statsWindowLength to set
     */
    synchronized public void setStatsWindowLength(int statsWindowLength) {
        int old = this.statsWindowLength;
        this.statsWindowLength = statsWindowLength;
        putInt("statsWindowLength", statsWindowLength);
        if (old != this.statsWindowLength) {
            timeStats.statsSamples = EvictingQueue.create(statsWindowLength);
        }
    }

    /**
     * @return the pingTest
     */
    public boolean isPingTest() {
        return pingTest;
    }

    /**
     * @param pingTest the pingTest to set
     */
    public void setPingTest(boolean pingTest) {
        this.pingTest = pingTest;
    }

    private class TimeStats {

        final private float[] TEMPORAL_HIST_COLOR = {0, 0, .8f, .3f},
                SPATIAL_HIST_COLOR = {.6f, .4f, .2f, .6f},
                HIST_OVERFLOW_COLOR = {.8f, .3f, .2f, .6f};
        int[] bins = new int[histNumBins];
        int lessCount = 0, moreCount = 0;
        int maxCount = 0;
        boolean virgin = true;
        float mean = 0, std = 0, cov = 0, median;
        long sum = 0, sum2 = 0;
        int count;
        EvictingQueue<Integer> statsSamples = EvictingQueue.create(getStatsWindowLength());

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
                std = (float) Math.sqrt(count * sum2 - sum * sum) / count;
                cov = std / mean;
            }
            synchronized(statsSamples){
                statsSamples.add(sample);
            }
            if (tobiLogger.isEnabled()) {
                tobiLogger.log(String.format("%d,%d", lastTimestamp, sample));
            }
        }

        void computeStats() {
            if (statsSamples.isEmpty()) {
                return;
            }
            Integer[] samples =null;
            synchronized (statsSamples) {
                samples = (Integer[]) statsSamples.toArray(new Integer[0]);
            }
            Arrays.sort(samples);
            median = samples[samples.length / 2];
        }

        /**
         * Draws the histogram
         *
         */
        void draw(GL2 gl) {
            computeStats();
            float dx = (float) (chip.getSizeX() - 2) / (histNumBins + 2);
            float sy = (float) (chip.getSizeY() - 2) / (logLogScale == false ? maxCount : (float) Math.log10(maxCount));

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
                    if (logLogScale && bins[i] == 0) {
                        continue;
                    }
                    float y = 1 + (sy * (!logLogScale ? bins[i] : (float) Math.log10(bins[i])));
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
            String medianS = fmt.format(median * 1e-6);
            MultilineAnnotationTextRenderer.renderMultilineString(String.format("Timing: mean=%s+-%s s (N=%d), median=%s s (N=%d)",
                    meanS, stdS, count, medianS, statsWindowLength));

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
        }

        private int getSampleBin(int sample) {
            if (autoScaleHist && sample < histMin) {
                setHistMin(sample);
                reset();
                return 0;
            } else if (autoScaleHist && sample >= histMax) {
                setHistMax(sample);
                reset();
                return histNumBins - 1;
            }
            int bin = (int) Math.floor((histNumBins * ((float) sample - histMin)) / (histMax - histMin));

            return bin;
        }
    }
}
