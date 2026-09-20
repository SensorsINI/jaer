/*
 * FlyMotion.java
 *
 * Fly-inspired per-eye wide-field (global) motion from DirectionSelectiveFlow.
 */
package ch.unizh.ini.jaer.chip.flyeye;

import java.awt.Color;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.rbodo.opticalflow.DirectionSelectiveFlow;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.FlyEyeEvent;
import net.sf.jaer.event.orientation.DvsOrientationEvent;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.filter.LowpassFilter2D;

/**
 * Rough model of fly global motion processing on the {@link FlyEye} panoramic
 * DVS128 pair. Local flow is computed by the inherited single-pass
 * {@link DirectionSelectiveFlow} (which already keys its lastTimesMap by
 * camera so independent left/right clocks never mix). This subclass only adds
 * the wide-field stage: each local flow vector is routed by its source eye
 * into a per-eye low-passed mean translational flow vector — loosely a
 * lobula-plate tangential-cell wide-field sum — and the two vectors are drawn
 * centered over their eyes on the merged panoramic display.
 * <p>
 * Left and right eye timestamps are never compared: each eye's lowpass filter
 * is driven only by its own eye's event times, so the filter also works with
 * independent (non-sync-cable) camera clocks.
 * <p>
 * On a single-camera chip all events aggregate into one vector drawn at the
 * chip center.
 *
 * @author tobi
 */
@Description("Fly-like global motion: per-eye wide-field flow vectors from local optical flow (FlyEye panoramic DVS128 pair)")
@Help("""
<html>
<body>
<h2>FlyMotion</h2>

<p>Rough model of fly wide-field motion sensing for the <b>FlyEye</b> panoramic
DVS128 pair. Local optical flow is computed by direction-selective
spatio-temporal correlation (inherited from <code>DirectionSelectiveFlow</code>,
driven by the enclosed <code>SimpleOrientationFilter</code>). Each local flow
vector is then summed into its source eye's low-passed mean flow vector
&mdash; loosely a lobula-plate tangential-cell wide-field response. The two
global vectors are drawn centered over the left and right eyes, labeled
<b>L</b>/<b>R</b> with speed in px/s.</p>

<hr>
<h3>Basic use</h3>
<ol>
<li>Select the <b>FlyEye</b> chip and play a recording or the live cameras,
then enable FlyMotion.</li>
<li><code>eyeFlowTauMs</code> sets the integration time of each eye's global
vector (longer &rarr; smoother, slower response).</li>
<li><code>displayVectorsEnabled</code> overlays a random subsample
(<code>displayVectorsFraction</code>) of the local flow vectors;
<code>localFlowVectorBrightness</code> fades them so the eye vectors stay
readable. Vector hue encodes direction (see the color wheel legend).</li>
<li><code>displayVectorsPpsScale</code> sets drawn vector length in pixels per
px/s of flow; the eye vectors are additionally magnified so they remain
visible.</li>
<li><code>minDtThreshold</code> (Dir. Selective) rejects flow slower than
1&nbsp;pixel per that many &micro;s.</li>
</ol>

<p>Left and right camera clocks are never compared, so unsynchronized cameras
work. On a single-camera chip all events aggregate into one vector drawn at
the chip center.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FlyMotion extends DirectionSelectiveFlow {

    private static final String FLY_TT = "Fly: global eye flow";

    private static final int LEFT = 0, RIGHT = 1;
    /** Per-eye lowpass of local flow (vx, vy) in px/s. Index {@link #LEFT} doubles as the mono aggregate on non-FlyEye chips. */
    private final LowpassFilter2D[] eyeFlowFilter = {new LowpassFilter2D(), new LowpassFilter2D()};

    private float eyeFlowTauMs = getFloat("eyeFlowTauMs", 100);
    private boolean showEyeFlowVectors = getBoolean("showEyeFlowVectors", true);

    public FlyMotion(AEChip chip) {
        super(chip);
        // SingleCameraCalibration (enclosed by AbstractMotionFlowIMU for flow
        // benchmarking with a single DAVIS lens) is inapplicable to the FlyEye
        // panoramic stitch; evict it from the enclosed chain and GUI.
        if (cameraCalibration != null) {
            getEnclosedFilterChain().remove(cameraCalibration);
            cameraCalibration = null;
        }
        // First-run FlyEye-tuned display defaults, applied only if the user
        // never set the pref. The panorama-wide single global vector mixes
        // the two eyes, so it stays off; the local-vector overlay is tamed by
        // subsampling and fading so the eye vectors stay readable.
        if (!preferenceExists("displayGlobalMotion")) {
            setDisplayGlobalMotion(false);
        }
        if (!preferenceExists("displayVectorsFraction")) {
            setDisplayVectorsFraction(0.1f);
        }
        if (!preferenceExists("displayVectorsPpsScale")) {
            setDisplayVectorsPpsScale(0.05f);
        }
        if (!preferenceExists("localFlowVectorBrightness")) {
            setLocalFlowVectorBrightness(0.4f);
        }
        if (!preferenceExists("motionVectorLineWidthPixels")) {
            setMotionVectorLineWidthPixels(3.8f);
        }
        if (!preferenceExists("displayZeroLengthVectorsEnabled")) {
            setDisplayZeroLengthVectorsEnabled(false);
        }
        for (LowpassFilter2D f : eyeFlowFilter) {
            f.setTauMs(eyeFlowTauMs);
        }
        setPropertyTooltip(FLY_TT, "eyeFlowTauMs", "time constant (ms) of the per-eye lowpass of local flow vectors; loosely a tangential-cell membrane/integration time");
        setPropertyTooltip(FLY_TT, "showEyeFlowVectors", "draw each eye's global flow vector centered over that eye");
        // The combined panorama-wide global motion is suppressed
        // (isCombinedGlobalMotionEnabled=false); hide its inert controls.
        hideProperty("displayGlobalMotion");
        hideProperty("displayGlobalMotionAngleHistogram");
        hideProperty("ppsScaleDisplayRelativeOFLength");
        // xMin/xMax/yMin/yMax crop the processed region; that was only for
        // flow benchmarking, inapplicable here. Beans names keep the two
        // leading capitals (Introspector.decapitalize), hence "XMin" etc.
        hideProperty("XMin");
        hideProperty("XMax");
        hideProperty("YMin");
        hideProperty("YMax");
    }

    /**
     * The per-eye vectors replace the chip-wide combined global motion, which
     * would mix the two eyes; suppress its computation and annotation.
     */
    @Override
    protected boolean isCombinedGlobalMotionEnabled() {
        return false;
    }

    /**
     * After the inherited local-flow output, accumulate this event's flow
     * into its source eye's wide-field vector. {@code vx, vy} (px/s) and
     * {@code ts} (us, this eye's clock) are set by the super call path.
     */
    @Override
    protected synchronized void writeOutputEvent(byte motionDir, Object ein) {
        super.writeOutputEvent(motionDir, ein);
        eyeFlowFilter[eyeIndex(ein)].filter(vx, vy, ts);
    }

    /** {@link #LEFT} or {@link #RIGHT}; untagged (single-camera) events map to {@link #LEFT}. */
    private int eyeIndex(Object ein) {
        if (ein instanceof FlyEyeEvent fe) {
            return fe.camera == FlyEyeEvent.Camera.RIGHT ? RIGHT : LEFT;
        }
        // SimpleOrientationFilter output: DvsOrientationEvent.copyFrom preserves the FlyEye camera tag
        if (ein instanceof DvsOrientationEvent de && de.camera == 1) {
            return RIGHT;
        }
        return LEFT;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        if (!showEyeFlowVectors || !isFilterEnabled()) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }
        final int cy = chip.getSizeY() / 2;
        if (chip instanceof FlyEye) {
            // Left eye occupies panoramic x [0, W); right eye [sizeX - W, sizeX)
            drawEyeFlowVector(gl, LEFT, FlyEyeGeometry.NATIVE_W / 2, cy, "L");
            drawEyeFlowVector(gl, RIGHT, chip.getSizeX() - (FlyEyeGeometry.NATIVE_W / 2), cy, "R");
        } else {
            drawEyeFlowVector(gl, LEFT, chip.getSizeX() / 2, cy, "");
        }
    }

    private void drawEyeFlowVector(GL2 gl, int eye, int x, int y, String label) {
        if (!eyeFlowFilter[eye].isInitialized()) {
            return; // no flow events from this eye yet (or since reset)
        }
        final java.awt.geom.Point2D.Float p = eyeFlowFilter[eye].getValue2D();
        final float fvx = p.x;
        final float fvy = p.y;
        final float speed = (float) Math.sqrt((fvx * fvx) + (fvy * fvy));
        final float[] rgba = motionColor(fvx, fvy, 1, 1);
        // Center the vector on the eye center, like the local flow vectors.
        // Scaled up like the combined global flow arrow so it stays visible.
        final float scale = getDisplayVectorsPpsScale() * GLOBAL_MOTION_DRAWING_SCALE;
        final float dx = fvx * scale, dy = fvy * scale;
        final float x0 = x - (dx / 2), y0 = y - (dy / 2);
        final float w = Math.min(2 * getMotionVectorLineWidthPixels(), 6);
        // 2x the local-vector tips so the eye vectors' direction reads at a glance;
        // DrawGL still caps the tip at 1/3 of the shaft for short vectors.
        final float headLen = 2 * VECTOR_HEAD_LENGTH_PIXELS;
        // Every DrawGL call translates the modelview matrix; each one needs
        // its own push/pop (a shared pair drew the second pass at doubled
        // coordinates: the stray black arrow at top center).
        // Black halo first so marker and vector read over the event/vector storm.
        gl.glColor4f(0, 0, 0, 1);
        gl.glLineWidth(w + 3);
        gl.glPushMatrix();
        DrawGL.drawVector(gl, x0, y0, dx, dy, headLen, 1);
        gl.glPopMatrix();
        gl.glColor4f(rgba[0], rgba[1], rgba[2], 1);
        gl.glLineWidth(w);
        gl.glPushMatrix();
        DrawGL.drawVector(gl, x0, y0, dx, dy, headLen, 1);
        gl.glPopMatrix();
        // Eye-center marker: small white circle on a black ring.
        gl.glColor4f(0, 0, 0, 1);
        gl.glLineWidth(4);
        gl.glPushMatrix();
        DrawGL.drawCircle(gl, x, y, 2, 16);
        gl.glPopMatrix();
        gl.glColor4f(1, 1, 1, 1);
        gl.glLineWidth(1.5f);
        gl.glPushMatrix();
        DrawGL.drawCircle(gl, x, y, 2, 16);
        gl.glPopMatrix();
        DrawGL.drawStringDropShadow(getFontSize(), x, y - 8, .5f, Color.white,
                String.format("%s %.0f px/s", label, speed));
    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter();
        if (eyeFlowFilter == null) {
            return; // called from superclass constructor before our fields exist
        }
        for (LowpassFilter2D f : eyeFlowFilter) {
            f.reset();
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --eyeFlowTauMs--">
    public float getEyeFlowTauMs() {
        return eyeFlowTauMs;
    }

    synchronized public void setEyeFlowTauMs(float eyeFlowTauMs) {
        if (eyeFlowTauMs < 1) {
            eyeFlowTauMs = 1;
        }
        this.eyeFlowTauMs = eyeFlowTauMs;
        putFloat("eyeFlowTauMs", eyeFlowTauMs);
        for (LowpassFilter2D f : eyeFlowFilter) {
            f.setTauMs(eyeFlowTauMs);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --showEyeFlowVectors--">
    public boolean isShowEyeFlowVectors() {
        return showEyeFlowVectors;
    }

    public void setShowEyeFlowVectors(boolean showEyeFlowVectors) {
        this.showEyeFlowVectors = showEyeFlowVectors;
        putBoolean("showEyeFlowVectors", showEyeFlowVectors);
    }
    // </editor-fold>
}
