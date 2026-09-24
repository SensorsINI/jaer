package net.sf.jaer.eventprocessing.witmotion;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.TreeMap;
import java.util.logging.Level;

import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.EventFilterSidecarWriter;
import net.sf.jaer.eventprocessing.gnss.NmeaGnssFilter;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.hardwareinterface.serial.witmotion.WitMotionIMU;
import net.sf.jaer.util.DrawGL;

/**
 * HWT906 overlay and sidecar recording. Timestamping matches
 * {@link EventFilterSidecarWriter}. Playback seeks the sidecar with the
 * AEDAT-4 playhead Unix time.
 */
@Description("WitMotion HWT906 IMU over USB serial; records a .witmotion.csv sidecar with the camera file")
@Help("""
<html>
<body>
<h2>WitMotionImuFilter</h2>
<p>Reads an HWT906 (or the same WitMotion <code>0x55</code> protocol) on a
USB serial port at 921600 baud. The COM number is discovered when the
filter connects; it changes if the adapter is plugged into another socket.
Close the WitMotion Windows app first so it does not hold the port.</p>
<p>While you record, writes <code><i>name</i>.witmotion.csv</code> next to the
AEDAT file, the same way <b>NmeaGnssFilter</b> writes <code>.gnss.csv</code>.
Each row is one IMU output cycle. <code>camera_us</code> is the latest camera
packet timestamp, and <code>aedat4_unix_us</code> is
<code>cameraTimestampToUnixUs(camera_us)</code>, the same Unix microseconds as
AEDAT-4 packets and the GNSS sidecar. Playback looks up that column with the
file playhead.</p>
<p>Enable this filter on <b>one</b> viewer when several cameras are muxed.
The port is opened only while the filter is enabled and the viewer is
<b>LIVE</b> or <b>WAITING</b>.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class WitMotionImuFilter extends EventFilterSidecarWriter<WitMotionSample> implements FrameAnnotater {

    @Preferred
    private String port = getString("port", "");
    @Preferred
    private int baudRate = getInt("baudRate", WitMotionIMU.DEFAULT_BAUD_RATE);
    /** Overlay font in chip pixels. First use auto-fits to chip width, same rule as NmeaGnssFilter. */
    @Preferred
    private float fontSize = getFloat("fontSize", defaultFontSize());
    private boolean fontSizeChecked = false;
    private boolean fontFitting = false;
    private static final String PREF_FONT_AUTO = "fontSizeAuto";
    private static final float FONT_CHAR_ADVANCE = 0.55f;
    private static final int FONT_OVERLAY_CHARS = 72;

    private volatile WitMotionSample live = new WitMotionSample();
    private volatile String status = "WitMotion: idle";
    private volatile WitMotionIMU imu;
    private int connectGeneration;

    /** Output cycle being assembled on the IMU thread. */
    private WitMotionSample cycle;
    private int cycleLastCode = -1;
    private boolean loggedFirstSample;

    public WitMotionImuFilter(AEChip chip) {
        super(chip);
        String serial = "Serial", disp = "Display";
        setPropertyTooltip(serial, "port", "COM port. Empty discovers the USB serial IMU at connect time.");
        setPropertyTooltip(serial, "baudRate", "Serial baud rate. HWT906 factory default is 921600.");
        setPropertyTooltip(serial, "doConnect", "Open the IMU (LIVE or WAITING, not playback).");
        setPropertyTooltip(serial, "doDisconnect", "Close the IMU serial port.");
        setPropertyTooltip(disp, "fontSize", "Overlay text size in chip pixels; first use auto-fits to chip width.");
    }

    @Override
    public synchronized EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        ensureSidecarListeners();
        noteCameraTimestamp(in);
        updateSidecarPlayback();
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        ensureSidecarListeners();
        if (!isPreferenceStored("fontSize")) {
            fontSize = defaultFontSize();
            fontSizeChecked = false;
        } else {
            fontSize = getFloat("fontSize", defaultFontSize());
            fontSizeChecked = false;
        }
    }

    @Override
    public synchronized void cleanup() {
        log.info("WitMotion cleanup");
        stopSidecarSource();
        closeSidecar();
        super.cleanup();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled() || !isAnnotationEnabled()) {
            return;
        }
        updateSidecarPlayback();
        String text = overlayText();
        maybeFitFontToChipWidth(text.split("\n", -1));
        float y = textOverlayAnchorY();
        NmeaGnssFilter gnss = activeGnssOverlay();
        if (gnss != null) {
            // Both renderers draw downward from the first baseline. Lift this block
            // by its own height so its last line sits one line above the GNSS baseline.
            float gap = DrawGL.lineAdvance(Math.max(fontSize, gnss.getFontSize()));
            y += NmeaGnssFilter.textBlockHeightPx(text, fontSize) + gap;
        }
        MultilineAnnotationTextRenderer.resetToYPositionPixels(y);
        MultilineAnnotationTextRenderer.setFontSize(fontSize);
        MultilineAnnotationTextRenderer.renderMultilineString(text);
    }

    private String overlayText() {
        WitMotionSample shown = live;
        return status + "\n" + (shown == null ? "" : shown.overlayText());
    }

    /** Same bottom anchor as {@link NmeaGnssFilter#textOverlayAnchorY()}. */
    private float textOverlayAnchorY() {
        int sizeY = (chip != null) ? chip.getSizeY() : 0;
        return Math.max(fontSize * 2.4f, sizeY * 0.08f);
    }

    private NmeaGnssFilter activeGnssOverlay() {
        if (chip == null || chip.getFilterChain() == null) {
            return null;
        }
        EventFilter2D found = chip.getFilterChain().findFilter(NmeaGnssFilter.class);
        if (found instanceof NmeaGnssFilter gnss && gnss.isTextOverlayActive()) {
            return gnss;
        }
        return null;
    }

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

    public void doConnect() {
        if (!isFilterEnabled()) {
            log.info("WitMotion Connect skipped: filter is not enabled");
            status = "WitMotion: enable the filter to connect";
            return;
        }
        if (!isLiveOrWaiting()) {
            log.info("WitMotion Connect skipped: need LIVE or WAITING");
            status = "WitMotion: connect only in LIVE or WAITING";
            return;
        }
        log.info("WitMotion Connect port='" + port + "' baud=" + baudRate);
        stopImu();
        startImuAsync();
    }

    public void doDisconnect() {
        log.info("WitMotion Disconnect");
        stopImu();
        status = "WitMotion: disconnected";
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        String old = this.port;
        this.port = port == null ? "" : port.trim();
        putString("port", this.port);
        getSupport().firePropertyChange("port", old, this.port);
    }

    public int getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(int baudRate) {
        int old = this.baudRate;
        if (baudRate < 4800) {
            baudRate = WitMotionIMU.DEFAULT_BAUD_RATE;
        }
        this.baudRate = baudRate;
        putInt("baudRate", baudRate);
        getSupport().firePropertyChange("baudRate", old, this.baudRate);
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

    private synchronized void startImuAsync() {
        if (!isFilterEnabled() || !isLiveOrWaiting()) {
            return;
        }
        final int gen = ++connectGeneration;
        final String requested = port;
        final int baud = baudRate;
        status = requested == null || requested.isEmpty()
                ? "WitMotion: discovering USB serial IMU"
                : "WitMotion: opening " + requested;
        Thread t = new Thread(() -> openImu(gen, requested, baud), "WitMotion-open");
        t.setDaemon(true);
        t.start();
    }

    private void openImu(int gen, String requested, int baud) {
        WitMotionIMU opened = null;
        try {
            if (requested == null || requested.isEmpty()) {
                opened = new WitMotionIMU();
            } else {
                opened = new WitMotionIMU(requested, baud);
            }
            synchronized (this) {
                if (gen != connectGeneration || !isFilterEnabled() || !isLiveOrWaiting()) {
                    opened.close();
                    return;
                }
                imu = opened;
                cycle = null;
                cycleLastCode = -1;
                loggedFirstSample = false;
                opened.subscribe(this::onMessage);
                opened = null;
            }
            WitMotionIMU livePort = imu;
            status = "WitMotion: " + (livePort == null ? "?" : livePort.getPortName() + " @ " + livePort.getBaudRate());
            log.info(status);
        } catch (Exception e) {
            status = "WitMotion: " + e.getMessage();
            log.log(Level.WARNING, "WitMotion open failed", e);
        } finally {
            if (opened != null) {
                opened.close();
            }
        }
    }

    private synchronized void stopImu() {
        connectGeneration++;
        WitMotionIMU current = imu;
        imu = null;
        cycle = null;
        cycleLastCode = -1;
        if (current != null) {
            current.close();
        }
    }

    private void onMessage(WitMotionIMU.ReceiveMessage msg) {
        if (msg == null || imu == null) {
            return;
        }
        int code = WitMotionSample.wireCode(msg);
        WitMotionSample completed = null;
        synchronized (this) {
            if (cycle != null && code <= cycleLastCode) {
                completed = cycle;
                cycle = null;
            }
            if (cycle == null) {
                cycle = new WitMotionSample();
            }
            cycle.apply(msg);
            cycleLastCode = code;
            live = cycle.copy();
        }
        if (completed != null && completed.hasMeasurement()) {
            stampAndWrite(completed);
        }
    }

    /**
     * Same clocks as {@code NmeaGnssFilter.onNmeaLine}: host millis, the latest
     * camera packet timestamp, and that timestamp converted to AEDAT-4 Unix µs.
     */
    private void stampAndWrite(WitMotionSample sample) {
        sample.receivedUnixMs = System.currentTimeMillis();
        sample.cameraUs = lastCameraTimestampUs();
        sample.aedat4UnixUs = aedat4UnixUsForCamera(sample.cameraUs);
        if (!loggedFirstSample) {
            loggedFirstSample = true;
            log.info("WitMotion sample " + sample.overlayText().replace('\n', ' ')
                    + " camera_us=" + sample.cameraUs + " aedat4_unix_us=" + sample.aedat4UnixUs);
        }
        writeSidecarRow(sample);
    }

    @Override
    protected String sidecarLogTag() {
        return "WitMotion";
    }

    @Override
    protected File sidecarFileFor(File recording) {
        return WitMotionSidecar.fileForRecording(recording);
    }

    @Override
    protected BufferedWriter tryOpenSidecar(File sidecar, File recording) throws IOException {
        return WitMotionSidecar.tryOpen(sidecar, recording);
    }

    @Override
    protected void closeSidecarStore(File sidecar, BufferedWriter writer) throws IOException {
        WitMotionSidecar.close(sidecar, writer);
    }

    @Override
    protected File relocateSidecarFile(File sidecar, File recording) throws IOException {
        return WitMotionSidecar.relocate(sidecar, recording);
    }

    @Override
    protected void appendSidecarRow(BufferedWriter writer, WitMotionSample row) throws IOException {
        WitMotionSidecar.writeRow(writer, row);
    }

    @Override
    protected TreeMap<Long, WitMotionSample> loadSidecarFile(File sidecar) throws IOException {
        return WitMotionSidecar.load(sidecar);
    }

    @Override
    protected long rowAedat4UnixUs(WitMotionSample row) {
        return row.aedat4UnixUs;
    }

    @Override
    protected void stopSidecarSource() {
        stopImu();
    }

    @Override
    protected boolean isSidecarSourceRunning() {
        return imu != null;
    }

    @Override
    protected void startSidecarSource() {
        startImuAsync();
    }

    @Override
    protected void onSidecarPlaybackEntered() {
        status = "WitMotion: playback sidecar";
    }

    @Override
    protected void onSidecarIdle() {
        status = "WitMotion: idle (LIVE/WAITING to connect)";
    }

    @Override
    protected void onSidecarLoaded(TreeMap<Long, WitMotionSample> map, File sidecar) {
        if (map.isEmpty()) {
            if (isSidecarPlayback()) {
                status = "WitMotion: no " + (sidecar == null ? "sidecar" : sidecar.getName());
            }
        } else {
            status = "WitMotion: " + map.size() + " sidecar samples";
            live = map.firstEntry().getValue();
        }
    }

    @Override
    protected void onPlaybackRow(WitMotionSample row) {
        live = row;
    }
}
