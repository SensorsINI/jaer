package net.sf.jaer.eventprocessing.witmotion;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
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
<b>LIVE</b> or <b>WAITING</b>. <b>updateRate</b> is written to the sensor on
open and whenever you change it (1–200 Hz).</p>
<p><b>showHud</b> draws a transparent attitude indicator (horizon, roll arc,
pitch ladder) with a north-up compass above it. N stays at the top and the
arrow is the heading. Positive pitch moves the horizon down. Use it to check
axis signs and calibration. Turn it off when you only want the text readout.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class WitMotionImuFilter extends EventFilterSidecarWriter<WitMotionSample> implements FrameAnnotater {

    /** HWT906 output rates from 1 Hz through 200 Hz. */
    public enum UpdateRate {
        HZ_1(1), HZ_2(2), HZ_5(5), HZ_10(10), HZ_20(20), HZ_50(50), HZ_100(100), HZ_125(125), HZ_200(200);

        private final double hz;

        UpdateRate(double hz) {
            this.hz = hz;
        }

        public double hz() {
            return hz;
        }

        @Override
        public String toString() {
            return (int) hz + " Hz";
        }
    }

    @Preferred
    private String port = getString("port", "");
    @Preferred
    private int baudRate = getInt("baudRate", WitMotionIMU.DEFAULT_BAUD_RATE);
    @Preferred
    private UpdateRate updateRate = storedUpdateRate();
    /** Overlay font in chip pixels. First use auto-fits to chip width, same rule as NmeaGnssFilter. */
    @Preferred
    private float fontSize = getFloat("fontSize", defaultFontSize());
    private boolean fontSizeChecked = false;
    private boolean fontFitting = false;
    private static final String PREF_FONT_AUTO = "fontSizeAuto";
    private static final float FONT_CHAR_ADVANCE = 0.55f;
    private static final int FONT_OVERLAY_CHARS = 72;
    /** Pitch degrees from center to the attitude-indicator rim. */
    private static final float HUD_PITCH_FULL_SCALE_DEG = 35f;

    @Preferred
    private boolean showHud = getBoolean("showHud", true);

    private volatile WitMotionSample live = new WitMotionSample();
    private volatile String status = "WitMotion: idle";
    private volatile WitMotionIMU imu;
    private int connectGeneration;

    /**
     * Last output-message code. A code that does not increase starts a new
     * cycle. Above 20 Hz the HWT906 omits some message types from a cycle, so
     * {@link #live} keeps the previous value of any field that did not arrive.
     */
    private int cycleLastCode = -1;
    private boolean loggedFirstSample;

    public WitMotionImuFilter(AEChip chip) {
        super(chip);
        String serial = "Serial", disp = "Display";
        setPropertyTooltip(serial, "port", "COM port. Empty discovers the USB serial IMU at connect time.");
        setPropertyTooltip(serial, "baudRate", "Serial baud rate. HWT906 factory default is 921600.");
        setPropertyTooltip(serial, "updateRate",
                "IMU output rate written on open and when changed. 1, 2, 5, 10, 20, 50, 100, 125, or 200 Hz.");
        setPropertyTooltip(serial, "doConnect", "Open the IMU (LIVE or WAITING, not playback).");
        setPropertyTooltip(serial, "doDisconnect", "Close the IMU serial port.");
        setPropertyTooltip(disp, "fontSize", "Overlay text size in chip pixels; first use auto-fits to chip width.");
        setPropertyTooltip(disp, "showHud",
                "Transparent horizon and north-up compass. Roll tilts the horizon, positive pitch moves it down, "
                        + "N stays at the top, and the arrow shows heading.");
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
        if (showHud) {
            drawHud(drawable);
        }
        String text = overlayText();
        maybeFitFontToChipWidth(text.split("\n", -1));
        float advance = DrawGL.lineAdvance(fontSize);
        float block = NmeaGnssFilter.textBlockHeightPx(text, fontSize);
        // Renderer draws downward from the first baseline. Place the last line
        // (Gyro) on the bottom edge, with room for descenders.
        float y = Math.max(1f, fontSize * 0.35f) + Math.max(0f, block - advance);
        NmeaGnssFilter gnss = activeGnssOverlay();
        if (gnss != null) {
            float gap = DrawGL.lineAdvance(Math.max(fontSize, gnss.getFontSize()));
            y = gnss.textOverlayAnchorY() + gap + Math.max(0f, block - advance);
        }
        MultilineAnnotationTextRenderer.resetToYPositionPixels(y);
        MultilineAnnotationTextRenderer.setFontSize(fontSize);
        MultilineAnnotationTextRenderer.renderMultilineString(text);
    }

    public boolean isShowHud() {
        return showHud;
    }

    public void setShowHud(boolean showHud) {
        boolean old = this.showHud;
        this.showHud = showHud;
        putBoolean("showHud", showHud);
        getSupport().firePropertyChange("showHud", old, showHud);
    }

    /**
     * Attitude ball with a compass above it. Both are translucent so camera
     * events stay visible. Missing angles draw as level with N at the top.
     */
    private void drawHud(GLAutoDrawable drawable) {
        if (chip == null) {
            return;
        }
        int sx = chip.getSizeX();
        int sy = chip.getSizeY();
        if (sx < 8 || sy < 8) {
            return;
        }
        WitMotionSample shown = live;
        double roll = shown == null || Double.isNaN(shown.roll) ? 0 : shown.roll;
        double pitch = shown == null || Double.isNaN(shown.pitch) ? 0 : shown.pitch;
        double yaw = shown == null || Double.isNaN(shown.yaw) ? 0 : shown.yaw;

        float unit = Math.min(sx, sy);
        float margin = unit * 0.03f;
        float gap = unit * 0.02f;
        float compassR = Math.min(unit * 0.15f, 72f);
        float aiR = compassR * 1.05f;
        float rollArcR = aiR * 1.28f;
        float stack = margin + 2f * compassR + gap + rollArcR + aiR;
        float maxStack = sy * 0.78f;
        if (stack > maxStack && stack > 0f) {
            float scale = maxStack / stack;
            margin *= scale;
            gap *= scale;
            compassR *= scale;
            aiR *= scale;
            rollArcR *= scale;
        }
        float cx = sx * 0.5f;
        float compassCy = sy - margin - compassR;
        float aiCy = compassCy - compassR - gap - rollArcR;

        GL2 gl = drawable.getGL().getGL2();
        gl.glPushAttrib(GL2.GL_ENABLE_BIT | GL2.GL_CURRENT_BIT | GL2.GL_LINE_BIT);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        float lw = Math.max(1f, aiR / 36f);
        gl.glLineWidth(lw);
        drawCompass(gl, cx, compassCy, compassR, yaw, lw);
        drawAttitude(gl, cx, aiCy, aiR, rollArcR, roll, pitch, lw);
        gl.glPopAttrib();
    }

    private void drawCompass(GL2 gl, float cx, float cy, float r, double yawDeg, float lw) {
        gl.glColor4f(0.02f, 0.03f, 0.06f, 0.28f);
        fillDisk(gl, cx, cy, r);
        gl.glColor4f(1f, 1f, 1f, 0.8f);
        gl.glLineWidth(lw);
        strokeCircle(gl, cx, cy, r, 48);
        gl.glBegin(GL.GL_LINES);
        for (int deg = 0; deg < 360; deg += 30) {
            double a = Math.toRadians(90 - deg);
            float outer = r * 0.96f;
            float inner = deg == 0 ? r * 0.72f : r * 0.86f;
            gl.glVertex2f(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner);
            gl.glVertex2f(cx + (float) Math.cos(a) * outer, cy + (float) Math.sin(a) * outer);
        }
        gl.glEnd();
        // North-up: N stays at the top. HWT906 yaw is 180° from this rose, so the arrow uses yaw+180.
        double na = Math.toRadians(90 + yawDeg + 180);
        float c = (float) Math.cos(na);
        float s = (float) Math.sin(na);
        float tip = r * 0.58f;
        float base = r * 0.18f;
        float halfW = r * 0.11f;
        gl.glColor4f(0.35f, 0.78f, 0.95f, 0.92f);
        gl.glBegin(GL.GL_TRIANGLES);
        gl.glVertex2f(cx + c * tip, cy + s * tip);
        gl.glVertex2f(cx + c * base - s * halfW, cy + s * base + c * halfW);
        gl.glVertex2f(cx + c * base + s * halfW, cy + s * base - c * halfW);
        gl.glEnd();
        float font = Math.max(6f, r * 0.28f);
        DrawGL.drawString(font, cx, cy + r * 0.52f, 0.5f, new Color(1f, 0.92f, 0.25f, 0.95f), "N");
    }

    private void drawAttitude(GL2 gl, float cx, float cy, float radius, float rollArcR,
            double rollDeg, double pitchDeg, float lw) {
        float pxPerDeg = radius / HUD_PITCH_FULL_SCALE_DEG;
        float horizonY = (float) (-pitchDeg * pxPerDeg);
        gl.glColor4f(0.45f, 0.72f, 0.95f, 0.34f);
        if (horizonY <= -radius) {
            fillDisk(gl, cx, cy, radius);
        } else if (horizonY < radius) {
            fillHorizonSegment(gl, cx, cy, radius, rollDeg, horizonY, true);
        }
        gl.glColor4f(0.55f, 0.38f, 0.14f, 0.34f);
        if (horizonY >= radius) {
            fillDisk(gl, cx, cy, radius);
        } else if (horizonY > -radius) {
            fillHorizonSegment(gl, cx, cy, radius, rollDeg, horizonY, false);
        }
        gl.glColor4f(1f, 1f, 1f, 0.9f);
        gl.glLineWidth(lw * 1.4f);
        strokeCircle(gl, cx, cy, radius, 48);
        if (Math.abs(horizonY) < radius) {
            float half = (float) Math.sqrt(radius * radius - horizonY * horizonY);
            gl.glBegin(GL.GL_LINES);
            vertexRolled(gl, cx, cy, rollDeg, -half, horizonY);
            vertexRolled(gl, cx, cy, rollDeg, half, horizonY);
            gl.glEnd();
        }
        drawPitchLadder(gl, cx, cy, radius, rollDeg, pitchDeg, pxPerDeg);
        drawRollScale(gl, cx, cy, rollArcR, radius, rollDeg, lw);
        drawAircraftSymbol(gl, cx, cy, radius);
    }

    /** Sky is {@code y >= horizonY} in the unrolled horizon frame. */
    private void fillHorizonSegment(GL2 gl, float cx, float cy, float radius, double rollDeg,
            float horizonY, boolean sky) {
        float k = Math.max(-1f, Math.min(1f, horizonY / radius));
        double t0 = Math.asin(k);
        double t1 = Math.PI - t0;
        double start = sky ? t0 : t1;
        double end = sky ? t1 : t0 + 2 * Math.PI;
        int n = Math.max(8, (int) Math.ceil((end - start) / (Math.PI / 24)));
        gl.glBegin(GL.GL_TRIANGLE_FAN);
        for (int i = 0; i <= n; i++) {
            double t = start + (end - start) * i / n;
            vertexRolled(gl, cx, cy, rollDeg, (float) (radius * Math.cos(t)), (float) (radius * Math.sin(t)));
        }
        gl.glEnd();
    }

    private void drawPitchLadder(GL2 gl, float cx, float cy, float radius, double rollDeg,
            double pitchDeg, float pxPerDeg) {
        float font = Math.max(2f, radius * 0.16f / 3f);
        Color label = new Color(1f, 1f, 1f, 0.9f);
        for (int mark = -30; mark <= 30; mark += 10) {
            if (mark == 0) {
                continue;
            }
            float y = (float) ((mark - pitchDeg) * pxPerDeg);
            if (Math.abs(y) > radius * 0.92f) {
                continue;
            }
            float half = radius * (Math.abs(mark) >= 20 ? 0.42f : 0.26f);
            gl.glColor4f(1f, 1f, 1f, 0.8f);
            gl.glBegin(GL.GL_LINES);
            vertexRolled(gl, cx, cy, rollDeg, -half, y);
            vertexRolled(gl, cx, cy, rollDeg, half, y);
            gl.glEnd();
            String text = Integer.toString(Math.abs(mark));
            float[] left = rolled(cx, cy, rollDeg, -half - font * 0.2f, y);
            float[] right = rolled(cx, cy, rollDeg, half + font * 0.2f, y);
            DrawGL.drawString(font, left[0], left[1] - font * 0.35f, 1f, label, text);
            DrawGL.drawString(font, right[0], right[1] - font * 0.35f, 0f, label, text);
        }
    }

    private void drawRollScale(GL2 gl, float cx, float cy, float rollArcR, float aiR, double rollDeg, float lw) {
        gl.glColor4f(1f, 1f, 1f, 0.85f);
        gl.glLineWidth(lw);
        gl.glBegin(GL.GL_LINE_STRIP);
        for (int deg = -50; deg <= 50; deg += 2) {
            double a = Math.toRadians(90 - deg);
            gl.glVertex2f(cx + (float) Math.cos(a) * rollArcR, cy + (float) Math.sin(a) * rollArcR);
        }
        gl.glEnd();
        gl.glBegin(GL.GL_LINES);
        for (int deg = -45; deg <= 45; deg += 15) {
            double a = Math.toRadians(90 - deg);
            float outer = rollArcR;
            float inner = rollArcR - (deg % 30 == 0 ? aiR * 0.12f : aiR * 0.07f);
            gl.glVertex2f(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner);
            gl.glVertex2f(cx + (float) Math.cos(a) * outer, cy + (float) Math.sin(a) * outer);
        }
        gl.glEnd();
        float font = Math.max(2f, aiR * 0.16f / 3f);
        Color label = new Color(1f, 1f, 1f, 0.9f);
        int[] marks = aiR < 36f ? new int[] {-30, 0, 30} : new int[] {-45, -30, -15, 0, 15, 30, 45};
        for (int deg : marks) {
            double a = Math.toRadians(90 - deg);
            float lr = rollArcR + font * 0.85f;
            float x = cx + (float) Math.cos(a) * lr;
            float y = cy + (float) Math.sin(a) * lr;
            DrawGL.drawString(font, x, y - font * 0.35f, 0.5f, label, Integer.toString(deg));
        }
        double pa = Math.toRadians(90 - rollDeg);
        float pr = rollArcR - aiR * 0.02f;
        float px = cx + (float) Math.cos(pa) * pr;
        float py = cy + (float) Math.sin(pa) * pr;
        float ix = (float) Math.cos(pa);
        float iy = (float) Math.sin(pa);
        float hx = -iy;
        float hy = ix;
        float depth = aiR * 0.10f;
        float wing = aiR * 0.07f;
        gl.glColor4f(1f, 1f, 1f, 0.95f);
        gl.glBegin(GL.GL_TRIANGLES);
        gl.glVertex2f(px, py);
        gl.glVertex2f(px - ix * depth + hx * wing, py - iy * depth + hy * wing);
        gl.glVertex2f(px - ix * depth - hx * wing, py - iy * depth - hy * wing);
        gl.glEnd();
    }

    private void drawAircraftSymbol(GL2 gl, float cx, float cy, float radius) {
        float w = radius * 0.55f;
        float gap = radius * 0.08f;
        float nose = radius * 0.16f;
        float half = radius * 0.13f;
        gl.glColor4f(1f, 0.95f, 0.15f, 0.95f);
        gl.glLineWidth(Math.max(1.5f, radius / 28f));
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(cx - w, cy);
        gl.glVertex2f(cx - gap, cy);
        gl.glVertex2f(cx + gap, cy);
        gl.glVertex2f(cx + w, cy);
        gl.glEnd();
        gl.glBegin(GL.GL_TRIANGLES);
        gl.glVertex2f(cx, cy + nose);
        gl.glVertex2f(cx - half, cy - nose * 0.15f);
        gl.glVertex2f(cx + half, cy - nose * 0.15f);
        gl.glEnd();
    }

    private static void fillDisk(GL2 gl, float cx, float cy, float radius) {
        gl.glBegin(GL.GL_TRIANGLE_FAN);
        gl.glVertex2f(cx, cy);
        int n = 48;
        for (int i = 0; i <= n; i++) {
            double t = 2 * Math.PI * i / n;
            gl.glVertex2f(cx + (float) (radius * Math.cos(t)), cy + (float) (radius * Math.sin(t)));
        }
        gl.glEnd();
    }

    private static void strokeCircle(GL2 gl, float cx, float cy, float radius, int n) {
        gl.glBegin(GL.GL_LINE_LOOP);
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n;
            gl.glVertex2f(cx + (float) (radius * Math.cos(t)), cy + (float) (radius * Math.sin(t)));
        }
        gl.glEnd();
    }

    private static void vertexRolled(GL2 gl, float cx, float cy, double rollDeg, float x, float y) {
        float[] p = rolled(cx, cy, rollDeg, x, y);
        gl.glVertex2f(p[0], p[1]);
    }

    /** Horizon-frame point rotated so positive roll drops the right wing. */
    private static float[] rolled(float cx, float cy, double rollDeg, float x, float y) {
        double a = Math.toRadians(-rollDeg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new float[] {
            (float) (cx + x * c - y * s),
            (float) (cy + x * s + y * c)
        };
    }

    private String overlayText() {
        WitMotionSample shown = live;
        return status + "\n" + (shown == null ? "" : shown.overlayText());
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

    public UpdateRate getUpdateRate() {
        return updateRate;
    }

    public void setUpdateRate(UpdateRate updateRate) {
        if (updateRate == null) {
            return;
        }
        UpdateRate old = this.updateRate;
        this.updateRate = updateRate;
        putString("updateRate", updateRate.name());
        getSupport().firePropertyChange("updateRate", old, updateRate);
        applyUpdateRate();
    }

    private UpdateRate storedUpdateRate() {
        try {
            return UpdateRate.valueOf(getString("updateRate", UpdateRate.HZ_10.name()));
        } catch (IllegalArgumentException e) {
            return UpdateRate.HZ_10;
        }
    }

    private void applyUpdateRate() {
        WitMotionIMU port = imu;
        if (port == null) {
            return;
        }
        try {
            port.setUpdateRate(updateRate.hz());
            log.info("WitMotion update rate " + (int) updateRate.hz() + " Hz");
        } catch (Exception e) {
            log.log(Level.WARNING, "WitMotion set update rate failed", e);
            status = "WitMotion: rate " + e.getMessage();
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
                cycleLastCode = -1;
                loggedFirstSample = false;
                opened.subscribe(this::onMessage);
                opened = null;
            }
            WitMotionIMU livePort = imu;
            status = "WitMotion: " + (livePort == null ? "?" : livePort.getPortName() + " @ " + livePort.getBaudRate());
            log.info(status);
            applyUpdateRate();
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
            if (cycleLastCode >= 0 && code <= cycleLastCode && live.hasMeasurement()) {
                completed = live.copy();
            }
            cycleLastCode = code;
            live.apply(msg);
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
