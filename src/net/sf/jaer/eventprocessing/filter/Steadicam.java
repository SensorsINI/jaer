/*
 * Steadicam.java (formerly SceneStabilizer / MotionCompensator)
 *
 * Copyright 2006-2012 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.HighpassFilter;

/**
 * Electronic SteadiCam: derotates global scene translation and rotation using
 * the camera IMU rate gyros. Events (and optionally APS image rendering) are
 * counter-transformed from integrated, high-pass-filtered pan/tilt/roll.
 * <p>
 * jAER 3.0 typed path: {@link #processImu} updates the transform;
 * {@link #processPolarity} warps DVS events. Legacy mixed
 * {@link ApsDvsEventPacket} still supports {@code imuLagMs&gt;0} via an event FIFO.
 *
 * @author tobi
 */
@Description("Stabilizes/derotates the scene using the built-in IMU rate gyros (electronic SteadiCam)")
@Help("""
<html>
<body>
<h2>Steadicam</h2>
<p>Electronic image stabilization: integrates the camera IMU <b>rate gyros</b>, high-pass
filters pan/tilt/roll, and counter-warps DVS events (and optionally APS rendering) so a
rotating/translating camera looks still. Needs a DAVIS, DVXplorer, or other chip
that provides <code>IMUSample</code>s on a typed <code>ImuPacket</code> stream.</p>
<p>Source paper:
<a href="https://ieeexplore.ieee.org/document/6865714">Integration of dynamic vision sensor
with inertial measurement unit for electronically stabilized event-based vision</a>
(Delbruck, Villanueva &amp; Longinotti, <i>IEEE Int. Symp. Circuits and Systems (ISCAS)</i>,
2014, pp.&nbsp;2636&ndash;2639).</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Use a live camera or recording that includes IMU gyro samples.</li>
<li>Hold the camera still for 1&ndash;2&nbsp;s and click <code>doZeroGyro</code> (averages
<code>numCalibrationSamples</code> to subtract gyro bias). <code>doEraseGyroZero</code>
clears the offset.</li>
<li>Set <code>lensFocalLengthMm</code> to your lens so rotation maps to the correct number
of pixels.</li>
<li>Check <b>Enabled</b> and <code>electronicStabilizationEnabled</code>. Use
<code>showGrid</code> / <code>showTransformRectangle</code> to judge residual motion.</li>
</ol>
<h3>Tuning</h3>
<ul>
<li><code>highpassTauMsTranslation</code> / <code>highpassTauMsRotation</code> &mdash;
relax the transform back to identity (longer keeps more of a slow pan).</li>
<li><code>disableTranslation</code> / <code>disableRotation</code> &mdash; stabilize only
one component.</li>
<li><code>doSelectCenterOfRotation</code> then click the image if the optical center is not
the array center; <code>doEraseCenterOfRotationSelection</code> restores the default.</li>
<li><code>transformImageEnabled</code> &mdash; warp APS display only (pixel data in the
packet are unchanged).</li>
<li><code>transformResetLimitDegrees</code> &mdash; snap the transform to zero if pan/tilt
grow too large.</li>
</ul>
<p>This is not visual odometry: it only derotates IMU-measured rotation/translation of the
camera, not scene motion.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class Steadicam extends EventFilter2DMouseAdaptor implements FrameAnnotater, PropertyChangeListener {

    private boolean electronicStabilizationEnabled = getBoolean("electronicStabilizationEnabled", true);
    private boolean flipContrast = getBoolean("flipContrast", false);
    boolean evenMotion = true;
    private TransformAtTime lastTransform = null, imageTransform = null;
    private float panRate = 0, tiltRate = 0, rollRate = 0; // deg/s
    private float panOffset = getFloat("panOffset", 0), tiltOffset = getFloat("tiltOffset", 0), rollOffset = getFloat("rollOffset", 0);
    private float panTranslationDeg = 0;
    private float tiltTranslationDeg = 0;
    private float rollDeg = 0;
    private float panDC = 0, tiltDC = 0, rollDC = 0;
    @Preferred
    private float lensFocalLengthMm = getFloat("lensFocalLengthMm", 8.5f);
    HighpassFilter panTranslationFilter = new HighpassFilter();
    HighpassFilter tiltTranslationFilter = new HighpassFilter();
    HighpassFilter rollFilter = new HighpassFilter();
    @Preferred
    private float highpassTauMsTranslation = getFloat("highpassTauMsTranslation", 1000);
    @Preferred
    private float highpassTauMsRotation = getFloat("highpassTauMsRotation", 1000);
    float radPerPixel;
    private volatile boolean resetCalled = false;
    private int lastTransformUpdateTimestamp = 0;
    /** Microsecond clock for {@link HighpassFilter} (int us). Not the MIPI/DVS tick. */
    private long hpTimeUs = 0;
    private boolean initialized = false;
    private int transformResetLimitDegrees = getInt("transformResetLimitDegrees", 45);
    private static final int FLUSH_COUNT = 10;
    private int flushCounter = 0;
    private boolean calibrating = false;
    private int calibrationSampleCount = 0;
    private static final int NUM_CALIBRATION_SAMPLES_DEFAULT = 800;
    protected int numCalibrationSamples = getInt("numCalibrationSamples", NUM_CALIBRATION_SAMPLES_DEFAULT);
    private CalibrationFilter panCalibrator, tiltCalibrator, rollCalibrator;
    TextRenderer imuTextRenderer = null;
    @Preferred
    private boolean showTransformRectangle = getBoolean("showTransformRectangle", true);
    @Preferred
    private boolean showGrid = getBoolean("showGrid", true);
    public boolean disableTranslation = getBoolean("disableTranslation", false);
    public boolean disableRotation = getBoolean("disableRotation", false);
    private int sxm1;
    private int sym1;
    private int sx2, sy2;
    private boolean transformImageEnabled = getBoolean("transformImageEnabled", true);
    private int lastFrameNumber = 0;
    protected float imuLagMs = getFloat("imuLagMs", 0);

    ApsDvsEventPacket outputPacket = null;
    private Point centerOfRotation = null;
    private boolean centerOfRotationSelectionPending = false;
    private boolean rewindFlg;

    public Steadicam(AEChip chip) {
        super(chip);
        initFilter();
        String transform = "Transform", display = "Display", imu = "IMU";

        setPropertyTooltip("electronicStabilizationEnabled", "stabilize by shifting events according to IMU gyros");
        setPropertyTooltip(display, "flipContrast", "flips contrast of output events depending on direction of motion");
        setPropertyTooltip(imu, "zeroGyro", "zeros the gyro output; keep sensor still for 1–2 s");
        setPropertyTooltip(imu, "eraseGyroZero", "Erases the gyro zero values");
        setPropertyTooltip(imu, "numCalibrationSamples", "Number of IMU samples to average for offset correction");
        setPropertyTooltip(transform, "transformImageEnabled", "Warps APS image rendering (display only; APS data unchanged)");
        setPropertyTooltip(transform, "highpassTauMsTranslation", "highpass time constant (ms) relaxing pan/tilt transform to zero");
        setPropertyTooltip(transform, "highpassTauMsRotation", "highpass time constant (ms) relaxing roll transform to zero");
        setPropertyTooltip(transform, "lensFocalLengthMm", "lens focal length (mm) for scaling rotation to pixels");
        setPropertyTooltip(transform, "transformResetLimitDegrees", "reset transform to 0 if pan/tilt exceed this many degrees");
        setPropertyTooltip(display, "showTransformRectangle", "show the red transform rectangle and cross hairs");
        setPropertyTooltip(display, "showGrid", "show a grid to judge stabilization");
        setPropertyTooltip(transform, "disableRotation", "disable rotational part of transform");
        setPropertyTooltip(transform, "disableTranslation", "disable translational part of transform");
        setPropertyTooltip(transform, "selectCenterOfRotation", "click on the image to set center of rotation");
        setPropertyTooltip(transform, "eraseCenterOfRotationSelection", "reset center of rotation to image center");
        setPropertyTooltip(imu, "imuLagMs", "IMU lag (ms); >0 uses event FIFO on legacy mixed packets only");

        rollFilter.setTauMs(highpassTauMsRotation);
        panTranslationFilter.setTauMs(highpassTauMsTranslation);
        tiltTranslationFilter.setTauMs(highpassTauMsTranslation);
        panCalibrator = new CalibrationFilter();
        tiltCalibrator = new CalibrationFilter();
        rollCalibrator = new CalibrationFilter();

        int corx = getInt("centerOfRotationX", -1);
        int cory = getInt("centerOfRotationY", -1);
        if (corx != -1 && cory != -1) {
            centerOfRotation = new Point(corx, cory);
            log.info("loaded from preferences centerOfRotation=" + centerOfRotation);
        }
    }

    @Override
    public boolean accepts(PacketType type) {
        return type == PacketType.POLARITY || type == PacketType.FRAME || (type != null && type.isImu());
    }

    @Override
    synchronized public ImuPacket processImu(ImuPacket in) {
        if (!electronicStabilizationEnabled || in == null) {
            return in;
        }
        for (int i = 0; i < in.getSize(); i++) {
            IMUSample s = in.get(i);
            if (s == null) {
                continue;
            }
            lastTransform = updateTransform(s);
            maybeApplyImageTransform();
        }
        return in;
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (in instanceof ApsDvsEventPacket) {
            return filterPacketLegacyMixed((ApsDvsEventPacket) in);
        }
        return stabilizePolarityPacket(in);
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> processPolarity(EventPacket<? extends BasicEvent> in) {
        return stabilizePolarityPacket(in);
    }

    @Override
    public FramePacket processFrame(FramePacket in) {
        return in;
    }

    private void maybeApplyImageTransform() {
        if (!transformImageEnabled || lastTransform == null || !(chip instanceof DavisChip)
                || chip.getAeViewer() == null || chip.getCanvas() == null
                || !(chip.getCanvas().getDisplayMethod() instanceof ChipRendererDisplayMethodRGBA)) {
            return;
        }
        DavisChip apsDvsChip = (DavisChip) chip;
        int frameStartTimestamp = apsDvsChip.getFrameExposureStartTimestampUs();
        int frameEndTimestamp = apsDvsChip.getFrameExposureEndTimestampUs();
        int frameCounter = apsDvsChip.getFrameCount();
        if (frameEndTimestamp >= frameStartTimestamp && lastTransform.timestamp >= frameEndTimestamp && frameCounter > lastFrameNumber) {
            imageTransform = lastTransform;
            lastFrameNumber = frameCounter;
            ChipRendererDisplayMethodRGBA displayMethod = (ChipRendererDisplayMethodRGBA) chip.getCanvas().getDisplayMethod();
            // Display GL is CCW-positive; rotationRad is CW (camera / IMU convention).
            displayMethod.setImageTransform(lastTransform.translationPixels, -lastTransform.rotationRad);
        }
    }

    private EventPacket stabilizePolarityPacket(EventPacket in) {
        if (!electronicStabilizationEnabled) {
            return in;
        }
        sx2 = chip.getSizeX() / 2;
        sy2 = chip.getSizeY() / 2;
        int corx = centerOfRotation == null ? this.sx2 : centerOfRotation.x;
        int cory = centerOfRotation == null ? this.sy2 : centerOfRotation.y;
        sxm1 = chip.getSizeX() - 1;
        sym1 = chip.getSizeY() - 1;

        if (lastTransform != null) {
            for (Object o : in) {
                if (o instanceof PolarityEvent) {
                    applyTransform((PolarityEvent) o, corx, cory);
                }
            }
        }
        if (rewindFlg) {
            initialized = false;
            rewindFlg = false;
        }
        return in;
    }

    private void applyTransform(PolarityEvent be, int corx, int cory) {
        int nx = be.x - corx, ny = be.y - cory;
        // Clockwise about +Z (y up), same sign as getGyroRollZ() / TransformAtTime.
        // Camera CW makes the scene appear CCW; this warp derotates it.
        be.x = (short) ((((lastTransform.cosAngle * nx) + (lastTransform.sinAngle * ny)) + lastTransform.translationPixels.x) + corx);
        be.y = (short) (((-(lastTransform.sinAngle * nx) + (lastTransform.cosAngle * ny)) + lastTransform.translationPixels.y) + cory);
        be.address = chip.getEventExtractor().getAddressFromCell(be.x, be.y, be.getType());
        if ((be.x > sxm1) || (be.x < 0) || (be.y > sym1) || (be.y < 0)) {
            be.setFilteredOut(true);
        } else {
            be.setFilteredOut(false);
        }
        if (flipContrast && evenMotion) {
            be.type = (byte) (1 - be.type);
            be.polarity = be.polarity == PolarityEvent.Polarity.On ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
        }
    }

    /** Legacy mixed APS+DVS+IMU packet path (supports imuLagMs FIFO). */
    private EventPacket filterPacketLegacyMixed(ApsDvsEventPacket in) {
        if (outputPacket == null) {
            outputPacket = new ApsDvsEventPacket(in.getEventClass());
        }
        if (!electronicStabilizationEnabled) {
            return in;
        }
        sx2 = chip.getSizeX() / 2;
        sy2 = chip.getSizeY() / 2;
        int corx = centerOfRotation == null ? this.sx2 : centerOfRotation.x;
        int cory = centerOfRotation == null ? this.sy2 : centerOfRotation.y;
        sxm1 = chip.getSizeX() - 1;
        sym1 = chip.getSizeY() - 1;

        OutputEventIterator outItr = outputPacket.outputIterator();
        Iterator itr = in.fullIterator();
        while (itr.hasNext()) {
            Object o = itr.next();
            if (o == null) {
                log.warning("null event passed in, returning input packet");
                return in;
            }
            ApsDvsEvent ev = (ApsDvsEvent) o;
            if (ev.isImuSample()) {
                lastTransform = updateTransform(ev.getImuSample());
                maybeApplyImageTransform();
            }
            pushEvent(ev);
            ApsDvsEvent be;
            while ((be = peekEvent()) != null && (be.timestamp <= ev.timestamp - imuLagMs * 1000 || be.timestamp > ev.timestamp)) {
                be = popEvent();
                if (!be.isImuSample()) {
                    if (lastTransform != null) {
                        applyTransform(be, corx, cory);
                    }
                    if (be.isFilteredOut()) {
                        continue;
                    }
                }
                outItr.nextOutput().copyFrom(be);
            }
        }
        if (rewindFlg) {
            initialized = false;
            rewindFlg = false;
        }
        return outputPacket;
    }

    private static final int INITIAL_QUEUE_SIZE = 1000;
    private ArrayBlockingQueue<ApsDvsEvent> eventQueue = new ArrayBlockingQueue<>(INITIAL_QUEUE_SIZE);
    private ApsDvsEvent heldEvent = null;

    private void pushEvent(ApsDvsEvent ev) {
        if (imuLagMs == 0) {
            heldEvent = ev;
            return;
        }
        ApsDvsEvent ne = new ApsDvsEvent();
        ne.copyFrom(ev);
        if (!eventQueue.offer(ne)) {
            ArrayBlockingQueue<ApsDvsEvent> newQueue = new ArrayBlockingQueue<>(eventQueue.size() * 2);
            log.info("increased event queue to " + newQueue.remainingCapacity() + " events");
            newQueue.addAll(eventQueue);
            eventQueue = newQueue;
            eventQueue.offer(ne);
        }
    }

    private ApsDvsEvent popEvent() {
        if (imuLagMs == 0) {
            ApsDvsEvent re = heldEvent;
            heldEvent = null;
            return re;
        }
        return eventQueue.poll();
    }

    private ApsDvsEvent peekEvent() {
        if (imuLagMs == 0) {
            return heldEvent;
        }
        return eventQueue.peek();
    }

    synchronized public TransformAtTime updateTransform(IMUSample imuSample) {
        if (resetCalled) {
            log.info("reset called, panDC=" + panDC + " panTranslationFilter=" + panTranslationFilter);
            resetCalled = false;
        }
        if (imuSample == null) {
            return null;
        }
        if (flushCounter-- >= 0) {
            return null;
        }
        int timestamp = imuSample.getTimestampUs();
        int dtUs = timestamp - lastTransformUpdateTimestamp;
        lastTransformUpdateTimestamp = timestamp;
        if (!initialized) {
            initialized = true;
            return null;
        }
        if (dtUs <= 0 || dtUs > 50_000) {
            return lastTransform;
        }
        float dtS = dtUs * 1e-6f;
        panRate = imuSample.getGyroYawY();
        tiltRate = imuSample.getGyroTiltX();
        rollRate = imuSample.getGyroRollZ();
        if (calibrating) {
            calibrationSampleCount++;
            if (calibrationSampleCount > numCalibrationSamples) {
                calibrating = false;
                panOffset = panCalibrator.computeAverage();
                tiltOffset = tiltCalibrator.computeAverage();
                rollOffset = rollCalibrator.computeAverage();
                putFloat("panOffset", panOffset);
                putFloat("tiltOffset", tiltOffset);
                putFloat("rollOffset", rollOffset);
                log.info(String.format("calibration finished. %d samples averaged to (pan,tilt,roll)=(%.3f,%.3f,%.3f)",
                        numCalibrationSamples, panOffset, tiltOffset, rollOffset));
            } else {
                panCalibrator.addSample(panRate);
                tiltCalibrator.addSample(tiltRate);
                rollCalibrator.addSample(rollRate);
            }
            return null;
        }

        panDC += getPanRate() * dtS;
        tiltDC += getTiltRate() * dtS;
        rollDC += getRollRate() * dtS;

        hpTimeUs += dtUs;
        panTranslationDeg = panTranslationFilter.filter(panDC, hpTimeUs);
        tiltTranslationDeg = tiltTranslationFilter.filter(tiltDC, hpTimeUs);
        rollDeg = rollFilter.filter(rollDC, hpTimeUs);

        if ((Math.abs(panTranslationDeg) > transformResetLimitDegrees)
                || (Math.abs(tiltTranslationDeg) > transformResetLimitDegrees)
                || (Math.abs(rollDeg) > (transformResetLimitDegrees * 3))) {
            panDC = 0;
            tiltDC = 0;
            rollDC = 0;
            panTranslationDeg = 0;
            tiltTranslationDeg = 0;
            rollDeg = 0;
            panTranslationFilter.reset();
            tiltTranslationFilter.reset();
            rollFilter.reset();
            log.info("transform reset limit reached, transform reset to zero");
        }

        if (flipContrast) {
            if (Math.abs(panRate) > Math.abs(tiltRate)) {
                evenMotion = panRate > 0;
            } else {
                evenMotion = tiltRate > 0;
            }
        }

        if (disableRotation) {
            rollDeg = 0;
        }
        if (disableTranslation) {
            panTranslationDeg = 0;
            tiltTranslationDeg = 0;
        }

        // Same sign as getGyroRollZ(): positive CW from the camera viewpoint.
        // applyTransform uses a CW matrix (y up); OpenGL callers negate for CCW.
        return new TransformAtTime(timestamp,
                new Point2D.Float(
                        (float) ((Math.PI / 180) * panTranslationDeg) / radPerPixel,
                        (float) ((Math.PI / 180) * tiltTranslationDeg) / radPerPixel),
                (rollDeg * (float) Math.PI) / 180);
    }

    synchronized public void doEraseGyroZero() {
        panOffset = 0;
        tiltOffset = 0;
        rollOffset = 0;
        putFloat("panOffset", 0);
        putFloat("tiltOffset", 0);
        putFloat("rollOffset", 0);
        log.info("calibration erased");
    }

    @Preferred
    synchronized public void doZeroGyro() {
        calibrating = true;
        calibrationSampleCount = 0;
        panCalibrator.reset();
        tiltCalibrator.reset();
        rollCalibrator.reset();
        log.info("calibration started");
    }

    public void doSelectCenterOfRotation() {
        centerOfRotationSelectionPending = true;
        log.info("select a center point by a mouse click");
    }

    public void doEraseCenterOfRotationSelection() {
        centerOfRotation = null;
        putInt("centerOfRotationX", -1);
        putInt("centerOfRotationY", -1);
    }

    public float getPanRate() {
        return panRate - panOffset;
    }

    public float getTiltRate() {
        return tiltRate - tiltOffset;
    }

    public float getRollRate() {
        return rollRate - rollOffset;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (calibrating) {
            if (imuTextRenderer == null) {
                imuTextRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
            }
            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1, 1, 1, 1);
            final String saz = String.format("Don't move sensor (Calibrating %d/%d)", calibrationSampleCount, numCalibrationSamples);
            Rectangle2D rect = imuTextRenderer.getBounds(saz);
            final float scale = .25f;
            imuTextRenderer.draw3D(saz, (chip.getSizeX() / 2) - (((float) rect.getWidth() * scale) / 2), chip.getSizeY() / 2, 0, scale);
            imuTextRenderer.end3DRendering();
        }

        GL2 gl = null;
        if (showGrid || showTransformRectangle) {
            gl = drawable.getGL().getGL2();
        }
        if (gl == null) {
            return;
        }
        if (showTransformRectangle && (lastTransform != null) && isElectronicStabilizationEnabled()) {
            gl.glPushMatrix();
            gl.glLineWidth(1f);
            gl.glColor3f(1, 0, 0);
            gl.glTranslatef(lastTransform.translationPixels.x + sx2, lastTransform.translationPixels.y + sy2, 0);
            gl.glRotatef((float) ((-lastTransform.rotationRad * 180) / Math.PI), 0, 0, 1);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(sx2, 0);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(-sx2, 0);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(0, sy2);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(0, -sy2);
            gl.glEnd();
            gl.glTranslatef(-sx2, -sy2, 0);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(sx2 * 2, 0);
            gl.glVertex2f(2 * sx2, 2 * sy2);
            gl.glVertex2f(0, 2 * sy2);
            gl.glVertex2f(0, 0);
            gl.glEnd();
            gl.glPopMatrix();
        }

        if (showGrid) {
            gl.glLineWidth(1f);
            gl.glColor3f(0, 0, 1);
            final int s = chip.getMaxSize() / 8;
            final int n = chip.getMaxSize() / s;
            gl.glBegin(GL.GL_LINES);
            for (int i = 0; i < n; i++) {
                final int x = i * s;
                gl.glVertex2i(x, 0);
                gl.glVertex2i(x, sy2 * 2);
            }
            for (int i = 0; i < n; i++) {
                final int y = i * s;
                gl.glVertex2i(0, y);
                gl.glVertex2i(sx2 * 2, y);
            }
            gl.glEnd();
        }

        if (centerOfRotation != null) {
            gl.glLineWidth(4f);
            gl.glColor3f(1, 0, 0);
            final int L = 4;
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(centerOfRotation.x - L, centerOfRotation.y);
            gl.glVertex2f(centerOfRotation.x + L, centerOfRotation.y);
            gl.glVertex2f(centerOfRotation.x, centerOfRotation.y - L);
            gl.glVertex2f(centerOfRotation.x, centerOfRotation.y + L);
            gl.glEnd();
        }
    }

    @Override
    synchronized public void resetFilter() {
        resetCalled = true;
        panRate = 0;
        tiltRate = 0;
        rollRate = 0;
        panDC = 0;
        tiltDC = 0;
        rollDC = 0;
        rollDeg = 0;
        panTranslationFilter.reset();
        tiltTranslationFilter.reset();
        rollFilter.reset();
        radPerPixel = (float) Math.atan((getChip().getPixelWidthUm() * 1e-3f) / lensFocalLengthMm);
        lastTransform = null;
        lastTransformUpdateTimestamp = 0;
        hpTimeUs = 0;
        initialized = false;
        eventQueue.clear();
        rewindFlg = true;
    }

    @Override
    public void initFilter() {
        resetFilter();
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().getSupport().addPropertyChangeListener(this);
        }
    }

    public boolean isFlipContrast() {
        return flipContrast;
    }

    public void setFlipContrast(boolean flipContrast) {
        this.flipContrast = flipContrast;
        putBoolean("flipContrast", flipContrast);
    }

    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (!yes) {
            if (chip.getAeViewer() != null && chip.getCanvas() != null
                    && chip.getCanvas().getDisplayMethod() instanceof ChipRendererDisplayMethodRGBA) {
                ChipRendererDisplayMethodRGBA displayMethod = (ChipRendererDisplayMethodRGBA) chip.getCanvas().getDisplayMethod();
                displayMethod.setImageTransform(new Point2D.Float(0, 0), 0);
            }
        } else {
            resetFilter();
        }
    }

    public boolean isElectronicStabilizationEnabled() {
        return electronicStabilizationEnabled;
    }

    public void setElectronicStabilizationEnabled(boolean electronicStabilizationEnabled) {
        this.electronicStabilizationEnabled = electronicStabilizationEnabled;
        putBoolean("electronicStabilizationEnabled", electronicStabilizationEnabled);
    }

    public float getHighpassTauMsTranslation() {
        return highpassTauMsTranslation;
    }

    public void setHighpassTauMsTranslation(float highpassTauMs) {
        this.highpassTauMsTranslation = highpassTauMs;
        putFloat("highpassTauMsTranslation", highpassTauMs);
        panTranslationFilter.setTauMs(highpassTauMs);
        tiltTranslationFilter.setTauMs(highpassTauMs);
    }

    public float getHighpassTauMsRotation() {
        return highpassTauMsRotation;
    }

    public void setHighpassTauMsRotation(float highpassTauMs) {
        this.highpassTauMsRotation = highpassTauMs;
        putFloat("highpassTauMsRotation", highpassTauMs);
        rollFilter.setTauMs(highpassTauMs);
    }

    public float getLensFocalLengthMm() {
        return lensFocalLengthMm;
    }

    public void setLensFocalLengthMm(float lensFocalLengthMm) {
        this.lensFocalLengthMm = lensFocalLengthMm;
        putFloat("lensFocalLengthMm", lensFocalLengthMm);
        radPerPixel = (float) Math.asin((getChip().getPixelWidthUm() * 1e-3f) / lensFocalLengthMm);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == AEViewer.EVENT_TIMESTAMPS_RESET) {
            resetFilter();
            flushCounter = FLUSH_COUNT;
        } else if (evt.getPropertyName().equals(AEInputStream.EVENT_REWOUND)) {
            resetFilter();
            flushCounter = FLUSH_COUNT;
        } else if (evt.getPropertyName().equals(AEViewer.EVENT_FILEOPEN)) {
            log.info("File Open");
            AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
            AEFileInputStreamInterface in = (player.getAEInputStream());
            in.getSupport().addPropertyChangeListener(this);
            resetFilter();
            flushCounter = FLUSH_COUNT;
        }
    }

    public int getTransformResetLimitDegrees() {
        return transformResetLimitDegrees;
    }

    public void setTransformResetLimitDegrees(int transformResetLimitDegrees) {
        this.transformResetLimitDegrees = transformResetLimitDegrees;
        putInt("transformResetLimitDegrees", transformResetLimitDegrees);
    }

    public boolean isShowTransformRectangle() {
        return showTransformRectangle;
    }

    public void setShowTransformRectangle(boolean showTransformRectangle) {
        this.showTransformRectangle = showTransformRectangle;
        putBoolean("showTransformRectangle", showTransformRectangle);
    }

    public boolean isDisableTranslation() {
        return disableTranslation;
    }

    public void setDisableTranslation(boolean disableTranslation) {
        this.disableTranslation = disableTranslation;
        putBoolean("disableTranslation", disableTranslation);
    }

    public boolean isDisableRotation() {
        return disableRotation;
    }

    public void setDisableRotation(boolean disableRotation) {
        this.disableRotation = disableRotation;
        putBoolean("disableRotation", disableRotation);
    }

    private class CalibrationFilter {
        int count = 0;
        float sum = 0;

        void reset() {
            count = 0;
            sum = 0;
        }

        void addSample(float sample) {
            sum += sample;
            count++;
        }

        float computeAverage() {
            return sum / count;
        }
    }

    public TransformAtTime getLastTransform() {
        return lastTransform;
    }

    public TransformAtTime getImageTransform() {
        return imageTransform;
    }

    public boolean isTransformImageEnabled() {
        return transformImageEnabled;
    }

    public void setTransformImageEnabled(boolean transformImageEnabled) {
        this.transformImageEnabled = transformImageEnabled;
        putBoolean("transformImageEnabled", transformImageEnabled);
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
        putBoolean("showGrid", showGrid);
    }

    public float getImuLagMs() {
        return imuLagMs;
    }

    public void setImuLagMs(float imuLagMs) {
        this.imuLagMs = imuLagMs;
        putFloat("imuLagMs", imuLagMs);
    }

    public int getNumCalibrationSamples() {
        return numCalibrationSamples;
    }

    public void setNumCalibrationSamples(int numCalibrationSamples) {
        this.numCalibrationSamples = numCalibrationSamples;
        putInt("numCalibrationSamples", numCalibrationSamples);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (isDontProcessMouse() || !centerOfRotationSelectionPending) {
            return;
        }
        Point p = getMousePixel(e);
        centerOfRotation = p;
        log.info("selected center of rotation as " + centerOfRotation);
        putInt("centerOfRotationX", p.x);
        putInt("centerOfRotationY", p.y);
        centerOfRotationSelectionPending = false;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (isDontProcessMouse() || !centerOfRotationSelectionPending) {
            return;
        }
        centerOfRotation = getMousePixel(e);
    }
}
