package net.sf.jaer.eventprocessing.witmotion;

import java.beans.PropertyChangeEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Map;
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
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.hardwareinterface.serial.witmotion.WitMotionIMU;

/**
 * HWT906 overlay and sidecar recording. Timestamping matches
 * {@link net.sf.jaer.eventprocessing.gnss.NmeaGnssFilter}: each completed IMU
 * cycle stores {@code camera_us} from the latest camera packet and
 * {@code aedat4_unix_us} from
 * {@link Aedat4FileOutputStream#cameraTimestampToUnixUs(int, int)}. Playback
 * seeks the sidecar with the AEDAT-4 playhead Unix time.
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
public class WitMotionImuFilter extends EventFilter2D implements FrameAnnotater {

    @Preferred
    private String port = getString("port", "");
    @Preferred
    private int baudRate = getInt("baudRate", WitMotionIMU.DEFAULT_BAUD_RATE);
    @Preferred
    private float fontSize = getFloat("fontSize", 8f);

    private volatile WitMotionSample live = new WitMotionSample();
    private volatile String status = "WitMotion: idle";
    private volatile int lastCameraUs;
    private volatile WitMotionIMU imu;
    private int connectGeneration;
    private boolean listenersAdded;
    private BufferedWriter sidecarWriter;
    private File sidecarFile;
    private TreeMap<Long, WitMotionSample> playback;
    /** True when sidecar keys are AEDAT-4 packet Unix µs, not host receive ms. */
    private boolean playbackByAedat4Unix;
    private boolean playbackMode;

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
        setPropertyTooltip(disp, "fontSize", "Overlay text size in chip pixels.");
    }

    @Override
    public synchronized EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        ensureListeners();
        if (in != null && !in.isEmpty()) {
            lastCameraUs = in.getLastTimestamp();
        }
        updatePlaybackSample();
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        ensureListeners();
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            ensureListeners();
            syncToPlayMode();
        } else {
            stopImu();
            closeSidecar();
        }
    }

    @Override
    public synchronized void cleanup() {
        log.info("WitMotion cleanup");
        stopImu();
        closeSidecar();
        super.cleanup();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled() || !isAnnotationEnabled()) {
            return;
        }
        updatePlaybackSample();
        WitMotionSample shown = live;
        String text = status + "\n" + (shown == null ? "" : shown.overlayText());
        float y = Math.max(fontSize * 2.4f, chip.getSizeY() * 0.08f);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(y);
        MultilineAnnotationTextRenderer.setFontSize(fontSize);
        MultilineAnnotationTextRenderer.renderMultilineString(text);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        String n = evt.getPropertyName();
        if (AEViewer.EVENT_RECORDING_STARTED.equals(n) && evt.getNewValue() instanceof File) {
            openSidecar((File) evt.getNewValue());
        } else if (AEViewer.EVENT_RECORDING_STOPPED.equals(n)) {
            File dest = evt.getNewValue() instanceof File ? (File) evt.getNewValue() : null;
            closeSidecarFollowing(dest);
        } else if (AEViewer.EVENT_RECORDING_RENAMED.equals(n)
                && evt.getNewValue() instanceof File destRec) {
            File destSide = WitMotionSidecar.fileForRecording(destRec);
            if (sidecarFile != null && destSide != null && sidecarFile.isFile()
                    && !sidecarFile.getAbsoluteFile().equals(destSide.getAbsoluteFile())) {
                closeSidecarFollowing(destRec);
            }
        } else if (AEViewer.EVENT_FILEOPEN.equals(n) || AbstractAEPlayer.EVENT_FILEOPEN.equals(n)) {
            loadPlaybackSidecar();
        } else if (AEViewer.EVENT_PLAYMODE.equals(n)) {
            syncToPlayMode();
        }
    }

    public void doConnect() {
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
        if (fontSize < 2f) {
            fontSize = 2f;
        }
        this.fontSize = fontSize;
        putFloat("fontSize", fontSize);
        getSupport().firePropertyChange("fontSize", old, this.fontSize);
    }

    private void ensureListeners() {
        if (listenersAdded) {
            return;
        }
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return;
        }
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_RECORDING_STARTED, this);
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_RECORDING_STOPPED, this);
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_RECORDING_RENAMED, this);
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, this);
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_PLAYMODE, this);
        if (v.getAePlayer() != null) {
            v.getAePlayer().getSupport().addPropertyChangeListener(AbstractAEPlayer.EVENT_FILEOPEN, this);
        }
        listenersAdded = true;
        syncToPlayMode();
        loadPlaybackSidecar();
    }

    private void syncToPlayMode() {
        AEViewer v = chip.getAeViewer();
        playbackMode = v != null && v.getPlayMode() == AEViewer.PlayMode.PLAYBACK;
        if (!isFilterEnabled()) {
            stopImu();
            return;
        }
        if (playbackMode) {
            stopImu();
            status = "WitMotion: playback sidecar";
            loadPlaybackSidecar();
            return;
        }
        playback = null;
        playbackByAedat4Unix = false;
        if (isLiveOrWaiting()) {
            if (imu == null) {
                startImuAsync();
            }
        } else {
            stopImu();
            status = "WitMotion: idle (LIVE/WAITING to connect)";
        }
    }

    private boolean isLiveOrWaiting() {
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return false;
        }
        AEViewer.PlayMode m = v.getPlayMode();
        return m == AEViewer.PlayMode.LIVE || m == AEViewer.PlayMode.WAITING;
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
        sample.cameraUs = lastCameraUs;
        sample.aedat4UnixUs = aedat4UnixUsForCamera(sample.cameraUs);
        if (!loggedFirstSample) {
            loggedFirstSample = true;
            log.info("WitMotion sample " + sample.overlayText().replace('\n', ' ')
                    + " camera_us=" + sample.cameraUs + " aedat4_unix_us=" + sample.aedat4UnixUs);
        }
        BufferedWriter w = sidecarWriter;
        if (w == null) {
            return;
        }
        try {
            synchronized (this) {
                if (sidecarWriter != null) {
                    WitMotionSidecar.writeRow(sidecarWriter, sample);
                }
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "WitMotion sidecar write: " + e, e);
        }
    }

    private synchronized void openSidecar(File recording) {
        closeSidecar();
        File side = WitMotionSidecar.fileForRecording(recording);
        try {
            BufferedWriter w = WitMotionSidecar.tryOpen(side, recording);
            if (w == null) {
                log.info("WitMotion sidecar already open for " + side);
                return;
            }
            sidecarFile = side;
            sidecarWriter = w;
            log.info("WitMotion sidecar opened " + side.getAbsolutePath());
        } catch (IOException e) {
            log.warning("WitMotion sidecar open failed: " + e);
        }
    }

    private synchronized void closeSidecar() {
        closeSidecarFollowing(null);
    }

    /**
     * Close the sidecar writer, then rename it beside {@code destRecording}
     * (Save As), or delete it if that take was discarded.
     */
    private synchronized void closeSidecarFollowing(File destRecording) {
        File side = sidecarFile;
        boolean wasOpen = sidecarWriter != null;
        try {
            WitMotionSidecar.close(sidecarFile, sidecarWriter);
        } catch (IOException e) {
            log.warning("WitMotion sidecar close failed: " + e);
        }
        sidecarWriter = null;
        sidecarFile = null;
        if (side == null) {
            return;
        }
        if (destRecording == null) {
            if (wasOpen) {
                log.info("WitMotion sidecar closed " + side.getAbsolutePath());
            }
            return;
        }
        if (destRecording.isFile()) {
            try {
                File moved = WitMotionSidecar.relocate(side, destRecording);
                if (moved != null && moved.isFile()
                        && !moved.getAbsoluteFile().equals(side.getAbsoluteFile())) {
                    log.info("WitMotion sidecar renamed " + side.getAbsolutePath()
                            + " -> " + moved.getAbsolutePath());
                } else if (wasOpen) {
                    log.info("WitMotion sidecar closed " + side.getAbsolutePath());
                }
            } catch (IOException e) {
                log.warning("WitMotion sidecar rename failed " + side + " -> "
                        + WitMotionSidecar.fileForRecording(destRecording) + ": " + e);
            }
            return;
        }
        if (side.isFile()) {
            boolean deleted = side.delete();
            if (deleted) {
                log.info("WitMotion sidecar deleted (recording discarded) " + side.getAbsolutePath());
            } else {
                log.warning("WitMotion sidecar close " + side.getAbsolutePath()
                        + " (could not delete leftover sidecar)");
            }
        } else if (wasOpen) {
            log.info("WitMotion sidecar closed " + side.getAbsolutePath());
        }
    }

    private void loadPlaybackSidecar() {
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return;
        }
        AEFileInputStreamInterface in = v.getAeFileInputStream();
        if (in == null) {
            return;
        }
        File rec = in.getFile();
        File side = WitMotionSidecar.fileForRecording(rec);
        try {
            TreeMap<Long, WitMotionSample> map = WitMotionSidecar.load(side);
            playback = map;
            playbackByAedat4Unix = false;
            if (!map.isEmpty()) {
                playbackByAedat4Unix = map.firstEntry().getValue().aedat4UnixUs > 0;
            }
            if (map.isEmpty()) {
                if (playbackMode) {
                    status = "WitMotion: no " + (side == null ? "sidecar" : side.getName());
                }
            } else {
                status = "WitMotion: " + map.size() + " sidecar samples";
                live = map.firstEntry().getValue();
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "WitMotion sidecar load: " + e, e);
        }
    }

    /**
     * New sidecars are keyed by AEDAT-4 packet Unix µs. Older files that only
     * have host {@code unix_ms} keep slider-fraction mapping onto that span.
     */
    private void updatePlaybackSample() {
        if (!playbackMode || playback == null || playback.isEmpty()) {
            return;
        }
        long t0 = playback.firstKey();
        long t1 = playback.lastKey();
        long unix;
        if (playbackByAedat4Unix) {
            unix = playheadUnixUs();
        } else {
            float f = 0f;
            AEViewer v = chip.getAeViewer();
            AEFileInputStreamInterface in = v != null ? v.getAeFileInputStream() : null;
            if (in != null) {
                f = in.getPlaybackSliderFraction();
            }
            if (f < 0f) {
                f = 0f;
            } else if (f > 1f) {
                f = 1f;
            }
            unix = t0 + (long) (f * (t1 - t0));
        }
        Map.Entry<Long, WitMotionSample> e = playback.floorEntry(unix);
        if (e == null) {
            e = playback.ceilingEntry(unix);
        }
        if (e != null) {
            live = e.getValue();
        }
    }

    private long playheadUnixUs() {
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return 0;
        }
        AEFileInputStreamInterface in = v.getAeFileInputStream();
        if (in instanceof Aedat4FileInputStream a4) {
            return a4.getBaseUnixUs() + a4.getPositionTimestampUs();
        }
        if (in == null) {
            return 0;
        }
        return in.getAbsoluteStartingTimeMs() * 1000L + in.getPositionTimestampUs();
    }

    private long aedat4UnixUsForCamera(int cameraTimestampUs) {
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return 0;
        }
        Aedat4FileOutputStream out = v.getAedat4RecordingOutputStream();
        if (out == null) {
            return 0;
        }
        return out.cameraTimestampToUnixUs(cameraTimestampUs, v.getAedat4RecordingTrackIndex());
    }
}
