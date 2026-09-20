package net.sf.jaer.eventprocessing.gnss;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
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
import net.sf.jaer.util.DrawGL;

/**
 * GNSS overlay and sidecar recording from a phone NMEA stream over TCP or UDP
 * (not Bluetooth serial / COM).
 */
@Description("GNSS (lat/lon/COG/SOG) from phone NMEA over TCP/UDP; records a .gnss.csv sidecar with the camera file")
@Help("""
<html>
<body>
<h2>NmeaGnssFilter</h2>
<p>Shows the phone GNSS fix (lat/lon, course, speed) on the camera display.
While you record, writes <code><i>name</i>.gnss.csv</code> next to the AEDAT
file. Playback reloads that sidecar. Stream is <b>NMEA over Wi-Fi TCP/UDP</b>,
not Bluetooth and not a COM port.</p>
<p>This dialog opens from the filter panel <b>?</b> (or F1 while the panel has
focus). Links are clickable.</p>
<hr>
<h3>1. Install gpsdRelay (try this first)</h3>
<p><b>gpsdRelay is not on the Play Store.</b> It lives on
<a href="https://f-droid.org/">F-Droid</a> (an independent Android catalog).
You install an <code>.apk</code> file (sideload). Android will ask to allow
installs from that source (browser, Files, or F-Droid).</p>
<p>Pick one:</p>
<ol>
<li><b>F-Droid client (gets updates):</b> on the phone open
<a href="https://f-droid.org/">https://f-droid.org/</a>, download the
F-Droid APK, install it, then in F-Droid install
<a href="https://f-droid.org/packages/io.github.project_kaat.gpsdrelay/">gpsdRelay</a>.</li>
<li><b>Single APK:</b> download
<a href="https://github.com/project-kaat/gpsdRelay/releases/latest">gpsdRelay-v2.2.apk</a>
(GitHub Releases; same file is on the F-Droid package page), copy to the phone
(USB / Drive), tap it, allow unknown apps, install. No auto-updates.</li>
</ol>
<p>Source code:
<a href="https://github.com/project-kaat/gpsdRelay">github.com/project-kaat/gpsdRelay</a>
(Kotlin, Apache-2.0). Android 7+. Grant <b>precise location</b>. Optionally
exempt the app from battery optimization so it keeps running with the screen
off.</p>
<hr>
<h3>2. Start a TCP server in gpsdRelay (not UDP)</h3>
<p>In gpsdRelay the IPv4 field defaults to <code>0.0.0.0</code>. Leave it.
That means listen on all interfaces, not “pick a 192.168 address.” You only
choose the <b>port</b> (jAER default <code>2947</code>, gpsd's port). The 192.168 address goes in
<b>jAER</b> <code>host</code> after the laptop has joined the hotspot: it is
the phone's real IP (gateway), which Android assigned. Do not type
<code>0.0.0.0</code> into jAER.</p>
<p>If gpsdRelay offers generated vs relayed NMEA, enable <b>generation</b>
unless you know the chip still emits sentences.</p>
<hr>
<h3>3. Network in the field (no mesh Wi-Fi)</h3>
<p>Yes: turn on the phone <b>Wi-Fi hotspot</b>, join that SSID from the laptop.
The phone is the access point. GNSS still uses the phone radios (GPS/Galileo);
the hotspot is only the NMEA link to the PC. Cellular data is not required.</p>
<ol>
<li>Phone: Hotspot &amp; tethering &rarr; Wi-Fi hotspot on. Set a password.</li>
<li>Laptop: connect to that Wi-Fi. Ignore “no internet” if shown.</li>
<li>Phone IP is the hotspot gateway: on the laptop, the default gateway of
that Wi-Fi adapter (often <code>192.168.43.1</code> or
<code>192.168.137.1</code>; gpsdRelay may show it). Put that in
<code>host</code>.</li>
<li>USB tethering is an alternative if Wi-Fi hotspot is flaky; then
<code>host</code> is the phone's USB-LAN address (Windows often
<code>192.168.42.129</code> / <code>192.168.137.1</code>).</li>
</ol>
<p>Keep gpsdRelay running after the hotspot is on so it is listening when
jAER connects.</p>
<hr>
<h3>4. Connect jAER</h3>
<ol>
<li>Add <b>NmeaGnssFilter</b> and enable it.</li>
<li><code>transport</code> = <b>TCP_CLIENT</b>.</li>
<li><code>host</code> = phone IP (hotspot gateway).</li>
<li><code>port</code> = gpsdRelay TCP port. Use <code>doConnect</code> if you
change host/port after enabling.</li>
<li>When lat/lon appear, record as usual. Muxed cameras: enable this filter on
<b>one</b> viewer only.</li>
</ol>
<p>Use <code>TCP_SERVER</code> only if the phone is the TCP client.
<code>UDP</code> only if you later set gpsdRelay to send UDP to the laptop.</p>
<hr>
<h3>5. Playback map</h3>
<p>With a <code>.gnss.csv</code> sidecar, enable <code>showMap</code> (Map
section). The track is north-up, centered on the chip, scaled so the longer of
NS/EW range fits in the pixel array. The current sample draws COG/SOG as a
vector whose max-SOG length is <code>sogVectorLengthPx</code>. The arrow
origin is the current sidecar sample. New sidecars store
<code>aedat4_unix_us</code> (<code>toUnixUs(camera_us)</code>), the same Unix µs as
AEDAT-4 packets. Playback looks up that column with the file playhead Unix time.
Older CSVs without that column map the slider fraction onto <code>unix_ms</code>.
Scale bars show map metres and
SOG in m/s.</p>
<hr>
<h3>Fallbacks</h3>
<ul>
<li><a href="https://play.google.com/store/apps/details?id=com.peterhohsy.nmeatools">NMEA Tools</a>
on the Play Store if you do not want to sideload.</li>
<li>GNSS Master for an external USB/BLE receiver, not the phone chip.</li>
</ul>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class NmeaGnssFilter extends EventFilter2D implements FrameAnnotater {

    public enum Transport {
        TCP_CLIENT, TCP_SERVER, UDP
    }

    @Preferred
    private Transport transport = Transport.valueOf(getString("transport", Transport.TCP_CLIENT.name()));
    @Preferred
    private String host = getString("host", "");
    @Preferred
    private int port = getInt("port", 2947);

    private volatile GnssFix live = new GnssFix();
    private volatile String netStatus = "GNSS: idle";
    private volatile int lastCameraUs;
    private NmeaNetworkSource source;
    private boolean listenersAdded;
    private BufferedWriter sidecarWriter;
    private File sidecarFile;
    private TreeMap<Long, GnssFix> playback;
    /** True when sidecar keys are AEDAT-4 packet Unix µs, not host receive ms. */
    private boolean playbackByAedat4Unix;
    private boolean playbackMode;
    private volatile boolean loggedFirstFix;

    /** Overlay font in chip pixels. First use auto-fits to chip width. */
    @Preferred
    private float fontSize = getFloat("fontSize", defaultFontSize());
    private boolean fontSizeChecked = false;
    private boolean fontFitting = false;
    private static final String PREF_FONT_AUTO = "fontSizeAuto";
    private static final float FONT_CHAR_ADVANCE = 0.55f;
    private static final int FONT_OVERLAY_CHARS = 72;

    @Preferred
    private boolean showMap = getBoolean("showMap", true);
    private float sogVectorLengthPx = getFloat("sogVectorLengthPx", 40f);
    private float mapFitMargin = getFloat("mapFitMargin", 0.88f);

    private double mapMidLat;
    private double mapMidLon;
    private double mapCenterE;
    private double mapCenterN;
    private double mapPxPerM;
    private double mapMaxSogKn;
    private int mapPointCount;
    private boolean mapReady;

    public NmeaGnssFilter(AEChip chip) {
        super(chip);
        String net = "Network", disp = "Display", map = "Map";
        setPropertyTooltip(net, "transport",
                "TCP_CLIENT: connect to phone (gpsdRelay TCP server). TCP_SERVER: phone connects here. UDP: bind port.");
        setPropertyTooltip(net, "host", "Phone IP for TCP_CLIENT (empty waits). Same Wi‑Fi or phone hotspot.");
        setPropertyTooltip(net, "port", "TCP/UDP port the phone app listens on (default 2947, same as gpsd / gpsdRelay).");
        setPropertyTooltip(net, "doConnect", "Start or restart the NMEA socket.");
        setPropertyTooltip(net, "doDisconnect", "Close the NMEA socket.");
        setPropertyTooltip(disp, "fontSize", "Overlay text size in chip pixels; first use auto-fits to chip width.");
        setPropertyTooltip(map, "showMap", "Playback: north-up path of the GNSS sidecar, fitted to the chip.");
        setPropertyTooltip(map, "sogVectorLengthPx", "Chip-pixel length of the COG/SOG arrow at the recording's max SOG.");
        setPropertyTooltip(map, "mapFitMargin", "Fraction of chip width/height used when fitting NS or EW range.");
    }

    @Override
    public synchronized EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        ensureListeners();
        if (in != null && !in.isEmpty()) {
            lastCameraUs = in.getLastTimestamp();
        }
        updatePlaybackFix();
        return in;
    }

    @Override
    public void resetFilter() {
        // keep last fix
    }

    @Override
    public void initFilter() {
        ensureListeners();
        if (!isPreferenceStored("fontSize")) {
            fontSize = defaultFontSize();
            fontSizeChecked = false;
        } else {
            fontSize = getFloat("fontSize", defaultFontSize());
            fontSizeChecked = false;
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            ensureListeners();
            syncTransportToPlayMode();
        } else {
            stopSource();
            closeSidecar();
        }
    }

    @Override
    public synchronized void cleanup() {
        log.info("GNSS cleanup: closing " + transport + " " + host + ":" + port);
        stopSource();
        closeSidecar();
        super.cleanup();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled() || !isAnnotationEnabled()) {
            return;
        }
        updatePlaybackFix();
        String status = netStatus;
        String fix = live.overlayText();
        maybeFitFontToChipWidth(new String[]{status, fix});
        float y = Math.max(fontSize * 2.4f, chip.getSizeY() * 0.08f);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(y);
        MultilineAnnotationTextRenderer.setFontSize(fontSize);
        MultilineAnnotationTextRenderer.renderMultilineString(status + "\n" + fix);
        if (showMap) {
            drawMap(drawable);
        }
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        String n = evt.getPropertyName();
        if (AEViewer.EVENT_RECORDING_STARTED.equals(n) && evt.getNewValue() instanceof File) {
            openSidecar((File) evt.getNewValue());
        } else if (AEViewer.EVENT_RECORDING_STOPPED.equals(n)) {
            closeSidecar();
        } else if (AEViewer.EVENT_FILEOPEN.equals(n) || AbstractAEPlayer.EVENT_FILEOPEN.equals(n)) {
            loadPlaybackSidecar();
        } else if (AEViewer.EVENT_PLAYMODE.equals(n)) {
            syncTransportToPlayMode();
        }
    }

    public void doConnect() {
        log.info("GNSS Connect " + transport + " " + host + ":" + port);
        stopSource();
        startSource();
    }

    public void doDisconnect() {
        log.info("GNSS Disconnect");
        stopSource();
        netStatus = "GNSS: disconnected";
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
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, this);
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_PLAYMODE, this);
        if (v.getAePlayer() != null) {
            v.getAePlayer().getSupport().addPropertyChangeListener(AbstractAEPlayer.EVENT_FILEOPEN, this);
        }
        listenersAdded = true;
        syncTransportToPlayMode();
        loadPlaybackSidecar();
    }

    private void syncTransportToPlayMode() {
        AEViewer v = chip.getAeViewer();
        playbackMode = v != null && v.getPlayMode() == AEViewer.PlayMode.PLAYBACK;
        if (!isFilterEnabled()) {
            return;
        }
        if (playbackMode) {
            stopSource();
            netStatus = "GNSS: playback sidecar";
            loadPlaybackSidecar();
        } else {
            playback = null;
            playbackByAedat4Unix = false;
            mapReady = false;
            if (source == null || !source.isAlive()) {
                startSource();
            }
        }
    }

    private synchronized void startSource() {
        if (playbackMode) {
            log.info("GNSS not connecting: playback mode (sidecar overlay)");
            return;
        }
        stopSource();
        loggedFirstFix = false;
        NmeaNetworkSource.Transport t = NmeaNetworkSource.Transport.valueOf(transport.name());
        source = new NmeaNetworkSource(t, host, port, this::onNmeaLine, s -> netStatus = s);
        source.start();
        netStatus = "GNSS: starting " + transport;
        log.info("GNSS starting " + transport + " " + host + ":" + port);
    }

    private synchronized void stopSource() {
        if (source != null) {
            source.stop();
            source = null;
        }
    }

    private void onNmeaLine(String line) {
        GnssFix next = live.copy();
        if (!NmeaParser.apply(line, next)) {
            return;
        }
        next.cameraUs = lastCameraUs;
        next.aedat4UnixUs = aedat4UnixUsForCamera(lastCameraUs);
        live = next;
        if (!loggedFirstFix && next.isValidFix()) {
            loggedFirstFix = true;
            log.info("GNSS fix " + next.overlayText());
        }
        BufferedWriter w = sidecarWriter;
        if (w != null) {
            try {
                synchronized (this) {
                    if (sidecarWriter != null) {
                        GnssSidecar.writeRow(sidecarWriter, next);
                    }
                }
            } catch (IOException e) {
                log.log(Level.WARNING, "GNSS sidecar write: " + e, e);
            }
        }
    }

    private synchronized void openSidecar(File recording) {
        closeSidecar();
        File side = GnssSidecar.fileForRecording(recording);
        try {
            BufferedWriter w = GnssSidecar.tryOpen(side, recording);
            if (w == null) {
                log.info("GNSS sidecar already open for " + side);
                return;
            }
            sidecarFile = side;
            sidecarWriter = w;
            log.info("GNSS sidecar opened " + side.getAbsolutePath());
        } catch (IOException e) {
            log.warning("GNSS sidecar open failed: " + e);
        }
    }

    private synchronized void closeSidecar() {
        File side = sidecarFile;
        boolean wasOpen = sidecarWriter != null;
        try {
            GnssSidecar.close(sidecarFile, sidecarWriter);
        } catch (IOException e) {
            log.warning("GNSS sidecar close failed: " + e);
        }
        sidecarWriter = null;
        sidecarFile = null;
        if (wasOpen && side != null) {
            log.info("GNSS sidecar closed " + side.getAbsolutePath());
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
        File side = GnssSidecar.fileForRecording(rec);
        try {
            TreeMap<Long, GnssFix> map = GnssSidecar.load(side);
            playback = map;
            playbackByAedat4Unix = false;
            if (!map.isEmpty()) {
                GnssFix first = map.firstEntry().getValue();
                playbackByAedat4Unix = first.aedat4UnixUs > 0;
            }
            if (map.isEmpty()) {
                if (playbackMode) {
                    netStatus = "GNSS: no " + (side == null ? "sidecar" : side.getName());
                }
            } else {
                netStatus = "GNSS: " + map.size() + " sidecar fixes";
                live = map.firstEntry().getValue();
            }
            rebuildMap();
        } catch (IOException e) {
            log.log(Level.WARNING, "GNSS sidecar load: " + e, e);
        }
    }

    /**
     * New sidecars are keyed by AEDAT-4 packet Unix µs; look up the playhead
     * the same way. Older CSVs (receive {@code unix_ms} only) keep slider-fraction
     * mapping onto that span.
     */
    private void updatePlaybackFix() {
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
        Map.Entry<Long, GnssFix> e = playback.floorEntry(unix);
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

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        Transport old = this.transport;
        this.transport = transport;
        putString("transport", transport.name());
        getSupport().firePropertyChange("transport", old, transport);
        if (isFilterEnabled() && !playbackMode) {
            startSource();
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        String old = this.host;
        this.host = host == null ? "" : host.trim();
        putString("host", this.host);
        getSupport().firePropertyChange("host", old, this.host);
        if (isFilterEnabled() && !playbackMode) {
            startSource();
        }
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        int old = this.port;
        int p = Math.max(1, Math.min(65535, port));
        this.port = p;
        putInt("port", p);
        getSupport().firePropertyChange("port", old, p);
        if (isFilterEnabled() && !playbackMode) {
            startSource();
        }
    }

    public boolean isShowMap() {
        return showMap;
    }

    public void setShowMap(boolean showMap) {
        boolean old = this.showMap;
        this.showMap = showMap;
        putBoolean("showMap", showMap);
        getSupport().firePropertyChange("showMap", old, showMap);
    }

    public float getSogVectorLengthPx() {
        return sogVectorLengthPx;
    }

    public void setSogVectorLengthPx(float sogVectorLengthPx) {
        float old = this.sogVectorLengthPx;
        this.sogVectorLengthPx = Math.max(4f, sogVectorLengthPx);
        putFloat("sogVectorLengthPx", this.sogVectorLengthPx);
        getSupport().firePropertyChange("sogVectorLengthPx", old, this.sogVectorLengthPx);
    }

    public float getMapFitMargin() {
        return mapFitMargin;
    }

    public void setMapFitMargin(float mapFitMargin) {
        float old = this.mapFitMargin;
        this.mapFitMargin = Math.max(0.2f, Math.min(1f, mapFitMargin));
        putFloat("mapFitMargin", this.mapFitMargin);
        getSupport().firePropertyChange("mapFitMargin", old, this.mapFitMargin);
        rebuildMap();
    }

    private void rebuildMap() {
        mapReady = false;
        mapPointCount = 0;
        mapMaxSogKn = 0;
        TreeMap<Long, GnssFix> map = playback;
        if (map == null || map.isEmpty() || chip == null) {
            return;
        }
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;
        for (GnssFix f : map.values()) {
            if (!f.hasPosition()) {
                continue;
            }
            minLat = Math.min(minLat, f.latDeg);
            maxLat = Math.max(maxLat, f.latDeg);
            minLon = Math.min(minLon, f.lonDeg);
            maxLon = Math.max(maxLon, f.lonDeg);
            if (!Double.isNaN(f.sogKnots) && f.sogKnots > mapMaxSogKn) {
                mapMaxSogKn = f.sogKnots;
            }
            mapPointCount++;
        }
        if (mapPointCount == 0 || Double.isInfinite(minLat)) {
            return;
        }
        mapMidLat = 0.5 * (minLat + maxLat);
        mapMidLon = 0.5 * (minLon + maxLon);
        double minE = Double.POSITIVE_INFINITY, maxE = Double.NEGATIVE_INFINITY;
        double minN = Double.POSITIVE_INFINITY, maxN = Double.NEGATIVE_INFINITY;
        for (GnssFix f : map.values()) {
            if (!f.hasPosition()) {
                continue;
            }
            double e = eastM(f.lonDeg);
            double n = northM(f.latDeg);
            minE = Math.min(minE, e);
            maxE = Math.max(maxE, e);
            minN = Math.min(minN, n);
            maxN = Math.max(maxN, n);
        }
        double spanE = Math.max(1e-3, maxE - minE);
        double spanN = Math.max(1e-3, maxN - minN);
        mapCenterE = 0.5 * (minE + maxE);
        mapCenterN = 0.5 * (minN + maxN);
        float sx = chip.getSizeX();
        float sy = chip.getSizeY();
        if (sx <= 0 || sy <= 0) {
            return;
        }
        float usableX = Math.max(8f, sx * mapFitMargin);
        float usableY = Math.max(8f, sy * mapFitMargin);
        mapPxPerM = Math.min(usableX / spanE, usableY / spanN);
        if (mapMaxSogKn < 1e-3) {
            mapMaxSogKn = 1;
        }
        mapReady = true;
    }

    private double eastM(double lonDeg) {
        return (lonDeg - mapMidLon) * Math.cos(Math.toRadians(mapMidLat)) * 111_320.0;
    }

    private double northM(double latDeg) {
        return (latDeg - mapMidLat) * 111_132.0;
    }

    private float mapX(double eastM) {
        return (float) (chip.getSizeX() * 0.5 + (eastM - mapCenterE) * mapPxPerM);
    }

    private float mapY(double northM) {
        return (float) (chip.getSizeY() * 0.5 + (northM - mapCenterN) * mapPxPerM);
    }

    private void drawMap(GLAutoDrawable drawable) {
        if (playback == null || playback.isEmpty()) {
            return;
        }
        if (!mapReady) {
            rebuildMap();
        }
        if (!mapReady) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushAttrib(GL2.GL_ENABLE_BIT | GL2.GL_CURRENT_BIT | GL2.GL_LINE_BIT | GL2.GL_POINT_BIT);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glLineWidth(1.5f);
        gl.glColor4f(0.35f, 0.85f, 1f, 0.9f);
        gl.glBegin(GL.GL_LINE_STRIP);
        for (GnssFix f : playback.values()) {
            if (!f.hasPosition()) {
                continue;
            }
            gl.glVertex2f(mapX(eastM(f.lonDeg)), mapY(northM(f.latDeg)));
        }
        gl.glEnd();

        GnssFix cur = live;
        if (cur != null && cur.hasPosition()) {
            float x = mapX(eastM(cur.lonDeg));
            float y = mapY(northM(cur.latDeg));
            float r = Math.max(0.8f, Math.min(chip.getSizeX(), chip.getSizeY()) * 0.0056f);
            gl.glColor3f(1f, 0.4f, 0.05f);
            gl.glBegin(GL2.GL_TRIANGLE_FAN);
            gl.glVertex2f(x, y);
            int nSeg = 20;
            for (int i = 0; i <= nSeg; i++) {
                double a = i * (2.0 * Math.PI / nSeg);
                gl.glVertex2f(x + (float) (r * Math.cos(a)), y + (float) (r * Math.sin(a)));
            }
            gl.glEnd();
            gl.glColor3f(0.15f, 0.08f, 0f);
            gl.glLineWidth(1.5f);
            gl.glPushMatrix();
            DrawGL.drawCircle(gl, x, y, r, nSeg);
            gl.glPopMatrix();
            if (!Double.isNaN(cur.cogTrueDeg) && mapMaxSogKn > 0) {
                double sog = Double.isNaN(cur.sogKnots) ? 0 : cur.sogKnots;
                float len = (float) (sogVectorLengthPx * (sog / mapMaxSogKn));
                if (len < r * 2.2f) {
                    len = r * 2.2f;
                }
                double rad = Math.toRadians(cur.cogTrueDeg);
                float dx = (float) (Math.sin(rad) * len);
                float dy = (float) (Math.cos(rad) * len);
                gl.glColor3f(1f, 0.35f, 0.15f);
                gl.glLineWidth(2.5f);
                gl.glPushMatrix();
                DrawGL.drawVector(gl, x, y, dx, dy, Math.max(3f, len * 0.25f), 1f);
                gl.glPopMatrix();
            }
        }

        drawScaleBars(gl);
        gl.glPopAttrib();
    }

    private void drawScaleBars(GL2 gl) {
        float sx = chip.getSizeX();
        float sy = chip.getSizeY();
        float x0 = sx * 0.04f;
        float y0 = Math.max(fontSize * 4.2f, sy * 0.14f);
        double targetPx = Math.min(sx, sy) * 0.22;
        double targetM = targetPx / Math.max(1e-9, mapPxPerM);
        double niceM = niceNumber(targetM);
        float barPx = (float) (niceM * mapPxPerM);
        gl.glLineWidth(2f);
        gl.glColor3f(1f, 1f, 1f);
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(x0, y0);
        gl.glVertex2f(x0 + barPx, y0);
        gl.glVertex2f(x0, y0 - 3);
        gl.glVertex2f(x0, y0 + 3);
        gl.glVertex2f(x0 + barPx, y0 - 3);
        gl.glVertex2f(x0 + barPx, y0 + 3);
        gl.glEnd();
        String mapLabel = niceM >= 1000 ? String.format("%.1f km", niceM / 1000) : String.format("%.0f m", niceM);
        DrawGL.drawString(Math.max(DrawGL.MIN_FONT_SIZE, fontSize * 0.7f), x0, y0 + 5, 0, Color.white, "N  " + mapLabel);

        float y1 = y0 + fontSize * 1.6f;
        gl.glColor3f(1f, 0.35f, 0.15f);
        gl.glPushMatrix();
        DrawGL.drawVector(gl, x0, y1, sogVectorLengthPx, 0, Math.max(3f, sogVectorLengthPx * 0.25f), 1f);
        gl.glPopMatrix();
        DrawGL.drawString(Math.max(DrawGL.MIN_FONT_SIZE, fontSize * 0.7f), x0, y1 + 5, 0, Color.white,
                String.format("SOG %.1f m/s", mapMaxSogKn * GnssFix.KNOTS_TO_MPS));
    }

    private static double niceNumber(double x) {
        if (!(x > 0) || Double.isInfinite(x)) {
            return 1;
        }
        double exp = Math.pow(10, Math.floor(Math.log10(x)));
        double f = x / exp;
        double nf = f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10;
        return nf * exp;
    }
}
