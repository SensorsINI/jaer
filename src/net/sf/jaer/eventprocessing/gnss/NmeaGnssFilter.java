package net.sf.jaer.eventprocessing.gnss;

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
    private TreeMap<Integer, GnssFix> playback;
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

    public NmeaGnssFilter(AEChip chip) {
        super(chip);
        String net = "Network", disp = "Display";
        setPropertyTooltip(net, "transport",
                "TCP_CLIENT: connect to phone (gpsdRelay TCP server). TCP_SERVER: phone connects here. UDP: bind port.");
        setPropertyTooltip(net, "host", "Phone IP for TCP_CLIENT (empty waits). Same Wi‑Fi or phone hotspot.");
        setPropertyTooltip(net, "port", "TCP/UDP port the phone app listens on (default 2947, same as gpsd / gpsdRelay).");
        setPropertyTooltip(net, "doConnect", "Start or restart the NMEA socket.");
        setPropertyTooltip(net, "doDisconnect", "Close the NMEA socket.");
        setPropertyTooltip(disp, "fontSize", "Overlay text size in chip pixels; first use auto-fits to chip width.");
    }

    @Override
    public synchronized EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        ensureListeners();
        if (in != null && !in.isEmpty()) {
            lastCameraUs = in.getLastTimestamp();
        }
        if (playbackMode && playback != null && !playback.isEmpty()) {
            Map.Entry<Integer, GnssFix> e = playback.floorEntry(lastCameraUs);
            if (e != null) {
                live = e.getValue();
            }
        }
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
        String status = netStatus;
        String fix = live.overlayText();
        maybeFitFontToChipWidth(new String[]{status, fix});
        float y = Math.max(fontSize * 2.4f, chip.getSizeY() * 0.08f);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(y);
        MultilineAnnotationTextRenderer.setFontSize(fontSize);
        MultilineAnnotationTextRenderer.renderMultilineString(status + "\n" + fix);
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
            TreeMap<Integer, GnssFix> map = GnssSidecar.load(side);
            playback = map;
            if (map.isEmpty()) {
                if (playbackMode) {
                    netStatus = "GNSS: no " + (side == null ? "sidecar" : side.getName());
                }
            } else {
                netStatus = "GNSS: " + map.size() + " sidecar fixes";
                live = map.firstEntry().getValue();
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "GNSS sidecar load: " + e, e);
        }
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
}
