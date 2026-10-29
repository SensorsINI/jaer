package net.sf.jaer.eventprocessing.gnss;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.TreeMap;

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
import net.sf.jaer.eventprocessing.EventFilterSidecarWriter;
import net.sf.jaer.graphics.AEViewer;
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
(Kotlin, Apache-2.0). Android 7+. Exempt it from battery optimization so it
keeps running with the screen off.</p>
<hr>
<h3>2. Phone</h3>
<ol>
<li>Location on. In gpsdRelay allow precise location, <b>Allow all the time</b>.</li>
<li>Turn the Wi-Fi hotspot on. Join it from the laptop.</li>
<li>In gpsdRelay add a <b>TCP</b> server: IPv4 <code>0.0.0.0</code>, port <code>2947</code>.</li>
<li>On that row turn the switch <b>on</b> and <b>NMEA generation</b> on.</li>
<li>Press <b>play</b>. The notification must say <b>NMEA relay is running</b>. Set the switches before play; changing them restarts the listener.</li>
<li>Open Maps until it has a fix, then leave Maps. That starts the GPS chip. gpsdRelay keeps sending.</li>
</ol>
<hr>
<h3>3. jAER</h3>
<ol>
<li>Enable <b>NmeaGnssFilter</b> on one viewer, in <b>LIVE</b> or <b>WAITING</b>.</li>
<li><code>transport</code> = <b>TCP_CLIENT</b>, <code>hotspotGateway</code> on, <code>host</code> empty, <code>port</code> 2947. jAER dials the hotspot gateway.</li>
<li>Record when lat/lon appear. If the overlay says <b>connected, no NMEA</b>, repeat the phone steps.</li>
</ol>
<p>USB tethering: set <code>host</code> to the phone USB address and turn <code>hotspotGateway</code> off.
<code>TCP_SERVER</code> only if the phone connects here. <code>UDP</code> only if gpsdRelay sends UDP here.</p>
<hr>
<h3>4. Playback map</h3>
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
public class NmeaGnssFilter extends EventFilterSidecarWriter<GnssFix> implements FrameAnnotater {

    public enum Transport {
        TCP_CLIENT, TCP_SERVER, UDP
    }

    @Preferred
    private Transport transport = Transport.valueOf(getString("transport", Transport.TCP_CLIENT.name()));
    @Preferred
    private String host = getString("host", "");
    @Preferred
    private boolean hotspotGateway = getBoolean("hotspotGateway", true);
    @Preferred
    private int port = getInt("port", 2947);

    private volatile GnssFix live = new GnssFix();
    private volatile String netStatus = "GNSS: idle";
    private NmeaNetworkSource source;
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

    private float lineWidth = getFloat("lineWidth", 2.5f);

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
        setPropertyTooltip(net, "host", "Phone IP for TCP_CLIENT on a shared Wi-Fi. Leave empty on a phone hotspot.");
        setPropertyTooltip(net, "hotspotGateway",
                "On a phone Wi-Fi hotspot, dial the Wi-Fi gateway (the phone). gpsdRelay stays on 0.0.0.0.");
        setPropertyTooltip(net, "port", "TCP/UDP port the phone app listens on (default 2947, same as gpsd / gpsdRelay).");
        setPropertyTooltip(net, "doConnect", "Start or restart the NMEA socket (LIVE or WAITING, not playback).");
        setPropertyTooltip(net, "doDisconnect", "Close the NMEA socket.");
        setPropertyTooltip(disp, "fontSize", "Overlay text size in chip pixels; first use auto-fits to chip width.");
        setPropertyTooltip(map, "showMap", "Playback: north-up path of the GNSS sidecar, fitted to the chip.");
        setPropertyTooltip(map, "sogVectorLengthPx", "Chip-pixel length of the COG/SOG arrow at the recording's max SOG.");
        setPropertyTooltip(map, "lineWidth", "Chip-pixel width of the playback map's lines.");
        setPropertyTooltip(map, "mapFitMargin", "Fraction of chip width/height used when fitting NS or EW range.");
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
        // keep last fix
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
        log.info("GNSS cleanup: closing " + transport + " " + host + ":" + port);
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
        String status = netStatus;
        String fix = live.overlayText();
        maybeFitFontToChipWidth(new String[]{status, fix});
        // Bottom text band. WitMotionImuFilter stacks above textOverlayHeightPx().
        float y = textOverlayAnchorY();
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

    /** Chip Y of the first GNSS overlay line, measured from the bottom. */
    public float textOverlayAnchorY() {
        int sizeY = (chip != null) ? chip.getSizeY() : 0;
        return Math.max(fontSize * 2.4f, sizeY * 0.08f);
    }

    /** True when annotate will draw the GNSS status text. */
    public boolean isTextOverlayActive() {
        return isFilterEnabled() && isAnnotationEnabled();
    }

    /**
     * Chip-pixel height of the GNSS text block, drawn downward from {@link #textOverlayAnchorY()}.
     * {@link net.sf.jaer.eventprocessing.witmotion.WitMotionImuFilter} reads {@link #isTextOverlayActive()}
     * and lifts its own block above this anchor.
     */
    public float textOverlayHeightPx() {
        if (!isTextOverlayActive()) {
            return 0f;
        }
        return textBlockHeightPx(netStatus + "\n" + live.overlayText(), fontSize);
    }

    public static float textBlockHeightPx(String text, float fontSize) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines * DrawGL.lineAdvance(fontSize);
    }

    public void doConnect() {
        if (!isFilterEnabled()) {
            log.info("GNSS Connect skipped: filter is not enabled");
            netStatus = "GNSS: enable the filter to connect";
            return;
        }
        if (!isLiveOrWaiting()) {
            log.info("GNSS Connect skipped: need LIVE or WAITING (playMode="
                    + (chip.getAeViewer() == null ? "none" : chip.getAeViewer().getPlayMode()) + ")");
            netStatus = "GNSS: connect only in LIVE or WAITING";
            return;
        }
        log.info("GNSS Connect " + transport + " " + host + ":" + port);
        stopSource();
        startSource();
    }

    public void doDisconnect() {
        log.info("GNSS Disconnect");
        stopSource();
        netStatus = "GNSS: disconnected";
    }

    private synchronized void startSource() {
        if (!isFilterEnabled()) {
            return;
        }
        if (!isLiveOrWaiting()) {
            log.info("GNSS not connecting: playMode="
                    + (chip.getAeViewer() == null ? "none" : chip.getAeViewer().getPlayMode())
                    + " (NMEA only while LIVE or WAITING)");
            return;
        }
        stopSource();
        loggedFirstFix = false;
        NmeaNetworkSource.Transport t = NmeaNetworkSource.Transport.valueOf(transport.name());
        source = new NmeaNetworkSource(t, host, port, this::onNmeaLine, s -> netStatus = s,
                this::isFilterEnabled, hotspotGateway);
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
        next.cameraUs = lastCameraTimestampUs();
        next.aedat4UnixUs = aedat4UnixUsForCamera(next.cameraUs);
        live = next;
        if (!loggedFirstFix && next.isValidFix()) {
            loggedFirstFix = true;
            log.info("GNSS fix " + next.overlayText());
        }
        writeSidecarRow(next);
    }

    @Override
    protected String sidecarLogTag() {
        return "GNSS";
    }

    @Override
    protected File sidecarFileFor(File recording) {
        return GnssSidecar.fileForRecording(recording);
    }

    @Override
    protected BufferedWriter tryOpenSidecar(File sidecar, File recording) throws IOException {
        return GnssSidecar.tryOpen(sidecar, recording);
    }

    @Override
    protected void closeSidecarStore(File sidecar, BufferedWriter writer) throws IOException {
        GnssSidecar.close(sidecar, writer);
    }

    @Override
    protected File relocateSidecarFile(File sidecar, File recording) throws IOException {
        return GnssSidecar.relocate(sidecar, recording);
    }

    @Override
    protected void appendSidecarRow(BufferedWriter writer, GnssFix row) throws IOException {
        GnssSidecar.writeRow(writer, row);
    }

    @Override
    protected TreeMap<Long, GnssFix> loadSidecarFile(File sidecar) throws IOException {
        return GnssSidecar.load(sidecar);
    }

    @Override
    protected long rowAedat4UnixUs(GnssFix row) {
        return row.aedat4UnixUs;
    }

    @Override
    protected void stopSidecarSource() {
        stopSource();
    }

    @Override
    protected boolean isSidecarSourceRunning() {
        return source != null && source.isAlive();
    }

    @Override
    protected void startSidecarSource() {
        startSource();
    }

    @Override
    protected void onSidecarPlaybackEntered() {
        netStatus = "GNSS: playback sidecar";
    }

    @Override
    protected void onSidecarPlaybackLeft() {
        mapReady = false;
    }

    @Override
    protected void onSidecarIdle() {
        netStatus = "GNSS: idle (LIVE/WAITING to connect)";
    }

    @Override
    protected void onSidecarLoaded(TreeMap<Long, GnssFix> map, File sidecar) {
        if (map.isEmpty()) {
            if (isSidecarPlayback()) {
                netStatus = "GNSS: no " + (sidecar == null ? "sidecar" : sidecar.getName());
            }
        } else {
            netStatus = "GNSS: " + map.size() + " sidecar fixes";
            live = map.firstEntry().getValue();
        }
        rebuildMap();
    }

    @Override
    protected void onPlaybackRow(GnssFix row) {
        live = row;
    }

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        Transport old = this.transport;
        this.transport = transport;
        putString("transport", transport.name());
        getSupport().firePropertyChange("transport", old, transport);
        if (isFilterEnabled() && isLiveOrWaiting()) {
            startSource();
        }
    }

    public boolean isHotspotGateway() {
        return hotspotGateway;
    }

    public void setHotspotGateway(boolean hotspotGateway) {
        boolean old = this.hotspotGateway;
        this.hotspotGateway = hotspotGateway;
        putBoolean("hotspotGateway", hotspotGateway);
        getSupport().firePropertyChange("hotspotGateway", old, hotspotGateway);
        if (isFilterEnabled() && isLiveOrWaiting()) {
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
        if (isFilterEnabled() && isLiveOrWaiting()) {
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
        if (isFilterEnabled() && isLiveOrWaiting()) {
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

    public float getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(float lineWidth) {
        float old = this.lineWidth;
        this.lineWidth = Math.max(1f, lineWidth);
        putFloat("lineWidth", this.lineWidth);
        getSupport().firePropertyChange("lineWidth", old, this.lineWidth);
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
        TreeMap<Long, GnssFix> map = sidecarPlayback();
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
        if (sidecarPlayback() == null || sidecarPlayback().isEmpty()) {
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
        gl.glLineWidth(lineWidth);
        gl.glColor4f(0.35f, 0.85f, 1f, 0.9f);
        gl.glBegin(GL.GL_LINE_STRIP);
        for (GnssFix f : sidecarPlayback().values()) {
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
                gl.glLineWidth(lineWidth);
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
