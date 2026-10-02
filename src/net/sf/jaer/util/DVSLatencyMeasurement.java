/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import com.google.common.collect.EvictingQueue;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.Description;
import net.sf.jaer.Preferred;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.eventprocessing.filter.XYTypeFilter;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.hardwareinterface.serial.witmotion.WinSerialPort;

/**
 * Tests DVS latency using Arduino DVSLatencyMeasurement.
 *
 * @author tobi, Feb 2021
 */
@Description("Measures DVS-computer latency using 2 LEDs controlled by Ardunino")
@Help("""
<html>
<body>
<h2>DVSLatencyMeasurement</h2>
<p>Measures DVS-to-PC latency with two LEDs driven by an Arduino sketch
(<code>DVSLatencyMeasurement</code>). One LED is the stimulus; the other can mark the
command. Encloses a denoiser and <code>XYTypeFilter</code> to isolate the LEDs.
Firmware: <a href="https://github.com/SensorsINI/DVSLatencyMeasurement">SensorsINI/DVSLatencyMeasurement</a>.</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Flash the sketch from
<a href="https://github.com/SensorsINI/DVSLatencyMeasurement">SensorsINI/DVSLatencyMeasurement</a>.
Connect USB serial. Set <code>serialPortName</code> and
<code>serialBaudRate</code> (the published sketch uses 115200).</li>
<li>Place two LEDs in the FOV, split by <code>xborder</code> (left vs right).</li>
<li>Enable this filter, then check the board with the LED buttons.
<code>flash</code> should blink the LED; <code>idle</code> should turn it off.
<code>flashOnce</code> and <code>turnOnBothLeds</code> are the other LED controls.</li>
<li>Set the enclosed <code>XYTypeFilter</code> ROI on the flashing LED.
Expand that filter so mouse events go to it, uncheck <code>lockSelections</code>,
press <code>flash</code>, and drag a box around the LED. Check <code>lockSelections</code>
again when the box is right, and leave <code>XYTypeFilter</code> enabled so events
outside the box are dropped.</li>
<li><code>masterMode</code>: PC timestamps the command vs DVS events
(<code>thresholdEventCount</code> to detect the LED).
<code>slaveMode</code>: the MCU measures latency.
<code>pingTest</code> is USB round-trip only.</li>
<li>Histogram: <code>histMin</code> / <code>histMax</code> / <code>histNumBins</code>,
<code>autoScaleHist</code>, <code>logLogScale</code>, <code>statsWindowLength</code>.
<code>logging</code> writes CSV.</li>
</ol>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DVSLatencyMeasurement extends EventFilter2DMouseAdaptor implements FrameAnnotater {

    /** Windows kernel32 port, or the closer for a Linux device file. */
    private AutoCloseable serialLink;
    private int serialBaudRate = getInt("serialBaudRate", 2000000); // note firmware is programmed for max 2Mbaud
    private String serialPortName = getString("serialPortName", "COM3");
    private DataOutputStream serialPortOutputStream = null;
    private DataInputStream serialPortInputStream = null;

    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f};

    private Point currentMousePoint = null;
    private TimeStats timeStats;

    /** Overlay font in chip pixels. First use auto-fits to chip width. */
    @Preferred
    private float fontSize = getFloat("fontSize", defaultFontSize());
    private boolean fontSizeChecked = false;
    private boolean fontFitting = false;
    private static final String PREF_FONT_AUTO = "fontSizeAuto";
    private static final float FONT_CHAR_ADVANCE = 0.55f;
    /** Latency summary is a long line; the first-use fit starts from this width. */
    private static final int FONT_OVERLAY_CHARS = 72;
    public boolean scaleHistogramsIncludingOverflow = getBoolean("scaleHistogramsIncludingOverflow", true);
    private int histNumBins = getInt("histNumBins", 300);
    protected int statsWindowLength = getInt("statsWindowLength", 300);
    protected boolean autoScaleHist = getBoolean("autoScaleHist", false);
    protected boolean logLogScale = getBoolean("logLogScale", false);
    protected int histMin = getInt("histMin", 0);
    protected int histMax = getInt("histMax", 30000);

    private EngineeringFormat fmt = new EngineeringFormat();

//    private RectangularClusterTracker tracker; // adjust it to detect LED cluster from either LED
    private SpatioTemporalCorrelationFilter noiseFilter; // adjust it to detect LED cluster from either LED
    private XYTypeFilter roiFilter; // adjust it to detect LED cluster from either LED
    private int lastClusterID = 0, lastTimestamp = 0;
    protected int thresholdEventCount = getInt("thresholdEventCount", 100);
    private int eventCount = 0;

//    protected boolean slaveMode = getBoolean("slaveMode", false);
//    private int led = 0;
    private Long lastToggleTimeNs = null;
    private long randomDelayNs = 0;
    private Random random = new Random();

    private TobiLogger tobiLogger;

    enum State {
        MasterRandomlyDelaying, MasterSendLedOn, MasterCountingEvents, Flash, PingTest, Idle, BothLedsOn, SlaveInitial, SlaveCountingEvents, SlaveWaitingForResponse
    }
    State state = State.Idle;

    public DVSLatencyMeasurement(AEChip chip) {
        super(chip);
        roiFilter = new XYTypeFilter(chip);
        noiseFilter = new SpatioTemporalCorrelationFilter(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(roiFilter);
        chain.add(noiseFilter);
        setEnclosedFilterChain(chain);
        setPropertyTooltip("serialPortName", "COM port (Windows) or /dev/ttyACM device (Linux) for the Teensy or Arduino");
        setPropertyTooltip("serialBaudRate", "Baud rate (default 115200), upper limit 12000000");
        setPropertyTooltip("autoScaleHist", "Automatically determine bounds for histogram of intervals");
        setPropertyTooltip("histMin", "Minimum interval");
        setPropertyTooltip("histMax", "Maximum interval");
        setPropertyTooltip("histNumBins", "Number of histogram bins");
        setPropertyTooltip("statsWindowLength", "Number of samples over which to compute statistics");
        setPropertyTooltip("turnOnBothLeds", "Turn on both LEDs flashing");
        setPropertyTooltip("flash", "Flash LED continuously");
        setPropertyTooltip("flashOnce", "Flash LED once");
        setPropertyTooltip("idle", "Turn off LED, go to idle mode");
        setPropertyTooltip("masterMode", "Measure latency with PC as master");
        setPropertyTooltip("logLogScale", "Use log-log histogram scale");
        setPropertyTooltip("pingTest", "Test only roundtrip latency to the Arduino");
        setPropertyTooltip("slaveMode", "uC measures the latency");
        setPropertyTooltip("logging", "Toggle ON/OFF logging of delta times to TobiLogger CSV file");
        setPropertyTooltip("xborder", "Left/Right LED X address discrimination border");
        setPropertyTooltip("thresholdEventCount", "How many events to detect LED");
        setPropertyTooltip("fontSize", "Overlay text size in chip pixels; first use auto-fits to chip width.");
        tobiLogger = new TobiLogger("DVSLatencyMeasurement", "DVSLatencyMeasurement");
        tobiLogger.setColumnHeaderLine("lastTimestamp(us),dt(us)");
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket<? extends BasicEvent> in) {

        getEnclosedFilterChain().filterPacket(in); // detect LED
        outer:
        for (BasicEvent b : in) {
            PolarityEvent e = (PolarityEvent) b;
            if (e.polarity == PolarityEvent.Polarity.Off) {
                continue;
            }
            // only count ON events from LED turning on
            eventCount++;
            if (eventCount > thresholdEventCount) {
                break;
            }
            if (state == State.SlaveWaitingForResponse) {
                try {
                    if (serialPortInputStream.available() < 4) {
                        continue;
                    }
                    break;
                } catch (IOException ex) {
                    log.warning("IOException trying to read the latency response: " + ex.toString());
                }
            }
        }
        switch (state) {
            case Idle:
            case Flash:
                break;
            case PingTest: {
                doToggleOnPingTest();
            }
            break;
            case MasterSendLedOn: {

                lastToggleTimeNs = System.nanoTime();
                randomDelayNs = 1000000L * (10L + random.nextInt(100));

                state = state.MasterRandomlyDelaying;
            }
            break;
            case MasterRandomlyDelaying: {
                long now = System.nanoTime();
                if (now - lastToggleTimeNs > randomDelayNs) {
                    sendByte('1');
                    eventCount = 0;
                    lastToggleTimeNs = now;
                    state = State.MasterCountingEvents;
                }
            }
            break;
            case MasterCountingEvents: {
                if (eventCount > thresholdEventCount) {
                    long now = System.nanoTime();
                    int deltaTimeUs = (int) (now - lastToggleTimeNs) / 1000;
                    timeStats.addSample(deltaTimeUs);
                    state = state.MasterSendLedOn;
                }
            }
            break;
            case SlaveInitial: {
                // We send the command 'm' to tell board that 
                // it is master and to flash LED. Then we collect events,
                // and when we detect the LED from DVS events, we tell the board 'd' for 
                // detected. Then we wait for the response which will be the us that elapsed
                // on the board
                //                    log.info("sent message to start flash in master mode");
                state = State.SlaveCountingEvents;
                eventCount = 0;
            }
            break;
            case SlaveCountingEvents: {
                if (eventCount > thresholdEventCount) {
                    sendByte('d');  // send ack that we saw the LED flash
//                       log.info("deteced LED, sent message to record delay and send it");
                    state = State.SlaveWaitingForResponse;
                }
            }
            break;
            case SlaveWaitingForResponse: {
                try {
                    if (serialPortInputStream.available() < 4) {
                        break;
                    }
//                        log.info("recieved response of 4 byte delay");
                    int available = serialPortInputStream.available();
                    byte[] bytes = new byte[available];
                    serialPortInputStream.read(bytes);
                    if (available > 4) {
                        log.warning("sent " + available + " bytes but should be only 4., ignoring value");
                        state = State.SlaveInitial;
                        break;
                    }
                    ByteBuffer buffer = ByteBuffer.allocate(available); // int is 4 bytes in java
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.put(bytes);
                    buffer.flip();//need flip 
                    int delayUs = buffer.getInt();
                    timeStats.addSample((int) delayUs);
//                        log.info(String.format("got slave cycle %d us", delayUs));
                    state = State.SlaveInitial;
                    break;
                } catch (IOException ex) {
                    log.warning("IOException trying to read the latency response: " + ex.toString());
                }
            }
            break;
        }

        if (in.getSize() > 0) {
            lastTimestamp = in.getLastTimestamp();
        }
        return in;
    }

    @Override
    synchronized public void resetFilter() {
        noiseFilter.resetFilter();
        if (timeStats != null) {
            timeStats.reset();
        }
    }

    @Override
    public void initFilter() {
        timeStats = new TimeStats();
        if (!isPreferenceStored("fontSize")) {
            fontSize = defaultFontSize();
            fontSizeChecked = false;
        } else {
            fontSize = getFloat("fontSize", defaultFontSize());
            fontSizeChecked = false;
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (noiseFilter != null) {
            noiseFilter.annotate(drawable);
        }
        if (timeStats == null || drawable == null || chip == null) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }
        gl.glColor3f(1, 1, 1);
        gl.glLineWidth(2);
        gl.glBegin(GL.GL_LINES);
        gl.glEnd();

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

    public void doIdle() {
        sendByte('0');
        state = State.Idle;
    }

    public void doFlash() {
        sendByte('f');
        state = State.Flash;
    }

    public void doFlashOnce() {
        sendByte('1');
        state = State.Flash;
    }

    public void doLedOn() {
        sendByte('2');
        state = State.Flash;
    }

    public void doSlaveMode() {
        sendByte('m');
        state = State.SlaveInitial;
    }

    public void doMasterMode() {
        state = State.MasterSendLedOn;
    }

    public void doWriteHistogramCSV() {
        if (tobiLogger.isEnabled()) {
            log.info("closing existing " + tobiLogger);
            tobiLogger.setEnabled(false);
        }
        synchronized (timeStats) {
            timeStats.writeHistogramCSV();
        }
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

    public void doToggleOnPingTest() {
        state = state.PingTest;
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
                while (serialPortInputStream.available() > 0 && state == State.PingTest) {
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

    public void doToggleOffPingTest() {
        state = state.Idle;
    }

    private void openSerial() throws IOException {
        closeSerial();
        StringBuilder sb = new StringBuilder("List of all available serial ports: ");
        final Set<String> availableSerialPorts = availableSerialPorts();
        if (availableSerialPorts.isEmpty()) {
            sb.append("none");
        } else {
            for (String s : availableSerialPorts) {
                sb.append(s).append(" ");
            }
        }
        log.info(sb.toString());
        if (!containsPort(availableSerialPorts, serialPortName)) {
            final String warningString = serialPortName + " is not in available " + sb.toString();
            log.warning(warningString);
            showWarningDialogInSwingThread(warningString, "Serial port not available");
            return;
        }

        if (isWindows()) {
            WinSerialPort port = new WinSerialPort(serialPortName, serialBaudRate);
            serialLink = port;
            serialPortOutputStream = new DataOutputStream(port.output());
            serialPortInputStream = new DataInputStream(port.input());
        } else {
            FileInputStream in = new FileInputStream(serialPortName);
            FileOutputStream out = new FileOutputStream(serialPortName);
            serialLink = () -> {
                try {
                    out.close();
                } finally {
                    in.close();
                }
            };
            serialPortOutputStream = new DataOutputStream(out);
            serialPortInputStream = new DataInputStream(in);
        }
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
                serialPortOutputStream.flush();
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
            serialPortOutputStream = null;
        }
        serialPortInputStream = null;
        if (serialLink != null) {
            try {
                serialLink.close();
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
            serialLink = null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Windows COM ports from the kernel serial map. Linux device nodes under /dev. */
    private static Set<String> availableSerialPorts() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (isWindows()) {
            names.addAll(WinSerialPort.listPortNames());
            return names;
        }
        File dev = new File("/dev");
        File[] nodes = dev.listFiles((dir, name) -> name.startsWith("ttyACM") || name.startsWith("ttyUSB"));
        if (nodes != null) {
            for (File node : nodes) {
                names.add(node.getAbsolutePath());
            }
        }
        return names;
    }

    private static boolean containsPort(Set<String> ports, String name) {
        if (name == null) {
            return false;
        }
        for (String port : ports) {
            if (port.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Heuristic font so a typical overlay line fills the chip width. First
     * annotate then measures with {@link DrawGL#fontSizeToFitWidth}.
     */
    private float defaultFontSize() {
        int sizeX = (chip != null) ? chip.getSizeX() : 0;
        if (sizeX <= 0) {
            return 6f;
        }
        float fs = (sizeX * 0.96f) / (FONT_OVERLAY_CHARS * FONT_CHAR_ADVANCE);
        return Math.max(DrawGL.MIN_FONT_SIZE, Math.min(fs, 48f));
    }

    /**
     * Pick a font that fills the chip width. Runs while the auto flag is set
     * (first use, or after Defaults). A user change of {@code fontSize} clears
     * the auto flag.
     */
    private void maybeFitFontToChipWidth(String[] lines) {
        if (fontSizeChecked) {
            return;
        }
        if (!getBoolean(PREF_FONT_AUTO, true)) {
            fontSizeChecked = true;
            return;
        }
        int sizeX = (chip != null) ? chip.getSizeX() : 0;
        if (sizeX <= 0 || lines == null || lines.length == 0) {
            return;
        }
        try {
            float start = Math.max(Math.max(24f, sizeX / 4f), fontSize);
            float fitted = DrawGL.fontSizeToFitWidth(start, lines, sizeX * 0.99f);
            fontFitting = true;
            try {
                setFontSize(fitted);
                putBoolean(PREF_FONT_AUTO, true);
            } finally {
                fontFitting = false;
            }
            fontSizeChecked = true;
        } catch (RuntimeException e) {
            // TextRenderer needs a current GL context; retry next frame
        }
    }

    public float getFontSize() {
        return fontSize;
    }

    public void setFontSize(float fontSize) {
        float old = this.fontSize;
        if (fontSize < DrawGL.MIN_FONT_SIZE) {
            fontSize = DrawGL.MIN_FONT_SIZE;
        }
        this.fontSize = fontSize;
        putFloat("fontSize", fontSize);
        if (!fontFitting) {
            putBoolean(PREF_FONT_AUTO, false);
            fontSizeChecked = true;
        }
        getSupport().firePropertyChange("fontSize", old, this.fontSize);
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
                log.warning(ex.toString());
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
            log.warning(ex.toString());
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
        if (chip == null || chip.getCanvas() == null) {
            return null;
        }
        ChipCanvas chipCanvas = chip.getCanvas();
        Object gl = chipCanvas.getCanvas();
        if (!(gl instanceof GLCanvas)) {
            return chipCanvas.getPixelFromMouseEvent(e);
        }
        synchronized (gl) { // getPixelFromMouseEvent calls OpenGL; do not overlap a paint
            return chipCanvas.getPixelFromMouseEvent(e);
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
        return state == State.PingTest;
    }

    /**
     * @param pingTest the pingTest to set
     */
    public void setPingTest(boolean pingTest) {
        state = State.PingTest;
    }

    /**
     * @return the thresholdEventCount
     */
    public int getThresholdEventCount() {
        return thresholdEventCount;
    }

    /**
     * @param thresholdEventCount the thresholdEventCount to set
     */
    public void setThresholdEventCount(int thresholdEventCount) {
        this.thresholdEventCount = thresholdEventCount;
        putInt("thresholdEventCount", thresholdEventCount);
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

        void addSample(int sampleUs) {
            int bin = getSampleBin(sampleUs);
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
            sum += sampleUs;
            sum2 += sampleUs * sampleUs;
            if (count > 2) {
                mean = (float) sum / count;
                std = (float) Math.sqrt(count * sum2 - sum * sum) / count;
                cov = std / mean;
            }
            synchronized (statsSamples) {
                statsSamples.add(sampleUs);
            }
            if (tobiLogger.isEnabled()) {
                tobiLogger.log(String.format("%d,%d", lastTimestamp, sampleUs));
            }
        }

        void computeStats() {
            if (statsSamples.isEmpty()) {
                return;
            }
            Integer[] samples = null;
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

            String status = String.format("State: %s\n%s", state.toString(), statsSummaryString());
            maybeFitFontToChipWidth(status.split("\n", -1));
            float labelY = -DrawGL.lineAdvance(fontSize);
            DrawGL.drawString(fontSize, 0, labelY, 0f, Color.white,
                    String.format("%ss", fmt.format(histMin * 1e-6f)));
            DrawGL.drawString(fontSize, chip.getSizeX(), labelY, 1f, Color.white,
                    String.format("%ss", fmt.format(histMax * 1e-6f)));

            if (currentMousePoint != null) {
                if (currentMousePoint.y <= 0) {
                    float sampleValue = ((float) currentMousePoint.x / chip.getSizeX()) * (1e-6f * histMax);
                    DrawGL.drawString(fontSize, currentMousePoint.x, labelY, 0.5f, Color.red,
                            String.format("%ss", fmt.format(sampleValue)));
                    gl.glLineWidth(3);
                    gl.glColor3fv(SELECT_COLOR, 0);
                    gl.glBegin(GL.GL_LINES);
                    gl.glVertex2f(currentMousePoint.x, 0);
                    gl.glVertex2f(currentMousePoint.x, chip.getSizeY());
                    gl.glEnd();
                }
            }
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .9f);
            MultilineAnnotationTextRenderer.setFontSize(fontSize);
            MultilineAnnotationTextRenderer.renderMultilineString(status);

        }

        public String statsSummaryString() {
            String meanS = fmt.format(mean * 1e-6f);
            String stdS = fmt.format(std * 1e-6);
            String medianS = fmt.format(median * 1e-6);
            return String.format("Latency: mean=%s+-%ss (N=%d), median=%ss (N=%d)",
                    meanS, stdS, count, medianS, statsSamples.size());
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
            synchronized (statsSamples) {
                statsSamples.clear();;
            }
            lessCount = 0;
            moreCount = 0;
            maxCount = 0;
            virgin = true;
            count = 0;
            sum = 0;
            sum2 = 0;
        }

        private float getBinWidthUs() {
            return (float) (histMax - histMin) / histNumBins;
        }

        private float getBinCenterUs(int bin) {
            float w = getBinWidthUs();
            return histMin + w * (bin + .5f);
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

        private void writeHistogramCSV() {
            String oldColumnHeader = tobiLogger.getColumnHeaderLine();
            String oldFileHeader = tobiLogger.getFileCommentString();
            tobiLogger.setColumnHeaderLine("bin,binCenter(us),count");
            String hs = String.format("histMin=%d us,histMax=%d us,nbins=%d", histMin, histMax, histNumBins);
            hs = hs + "\n";
            hs += statsSummaryString();
            tobiLogger.setFileCommentString(hs);
            tobiLogger.setEnabled(true);
            synchronized (this) {
                for (int i = 0; i < bins.length; i++) {
                    tobiLogger.log(String.format("%d,%f,%d", i,
                            getBinCenterUs(i),
                            bins[i]));
                }

            }
            tobiLogger.setEnabled(false);
            tobiLogger.showFolderInDesktop();
            tobiLogger.setFileCommentString(oldFileHeader);
            tobiLogger.setColumnHeaderLine(oldColumnHeader);
        }
    }
}
