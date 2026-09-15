/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.Point;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Random;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.FrameAnnotater;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Preferred;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.util.DrawGL;

/**
 * Filters out high-firing input using probabilistic depressing synaptic
 * connection. Works particularly well for high firing rate flickering lighting
 * sources with the DVS. The depression state saturates, so a bursting input
 * recovers between bursts more rapidly than one that steadily fires at the same
 * average rate.
 *
 * @author tobi
 *
 * This is part of jAER
 * <a href="http://jaerproject.net/">jaerproject.net</a>, licensed under the
 * LGPL
 * (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("Filters out rapidly firing input using depressing probabalistic synapse model")
@Help("""
<html>
<body>
<h2>DepressingSynapseFilter</h2>
<p>Each pixel has a <b>depressing synapse</b>: an event is transmitted with probability
equal to the current synaptic weight, then the weight is reduced. Weight recovers with
time constant <code>tauS</code>. Steady high rates (flicker, hot pixels) are suppressed;
bursts recover between volleys faster than a constant-rate source at the same mean.</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Check <b>Enabled</b> (typical place: after or instead of a BA denoiser for flicker).</li>
<li>Increase <code>weight</code> to knock down more spikes inside a <code>tauS</code> window.</li>
<li>Increase <code>tauS</code> to keep depression longer (stronger suppression of sustained firing).</li>
</ol>
<p><code>showStateAtMouse</code> overlays the weight at the cursor (costly).
<code>showBlockingProbabilityMask</code> overlays a transparent color mask that is brighter
where depression state is closer to 1 (low pass probability); not stored in preferences.
<code>overlayColor</code> sets that mask color (stored).
<code>saveState</code> / <code>loadState</code> / <code>clearState</code> persist or reset
per-pixel depression.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DepressingSynapseFilter extends AbstractNoiseFilter implements FrameAnnotater {

    private static Random random = new Random();
    private Neurons neurons;
    @Preferred
    private float tauS = getFloat("tauS", 100e-3f); // recovery time constant in seconds
    private float tauUs = tauS * 1e6f;
    @Preferred
    private float weight = prefs().getFloat("DepressingSynapseFilter.weight", .001f); // weight of each input spike on synapse
    private boolean showStateAtMouse = getBoolean("showStateAtMouse", true);
    /** Overlay of per-pixel depression; session-only, not stored in preferences. */
    private boolean showBlockingProbabilityMask = false;
    /** True only after we enabled the DavisRenderer annotation pixmap for this overlay. */
    private boolean blockingMaskOverlayActive = false;
    @Preferred
    private Color overlayColor = new Color(getInt("overlayColor", Color.BLUE.getRGB()));
    private float overlayR, overlayG, overlayB;
    private final float[] blockingMaskRgba = new float[4];

    public static DevelopmentStatus getDevelopementStatus() {
        return DevelopmentStatus.Beta;
    }

    public DepressingSynapseFilter(AEChip chip) {
        super(chip); // as usual when we're called here we don't have array size yet. Defer memory allocation to running filter.
        String cat = "DepressingSynapseFilter";
        setPropertyTooltip(cat, "tauS", "recovery time constant in seconds of depressing synapse; recovers full strength with this 1st order time constant; increase to make filtering last longer");
        setPropertyTooltip(cat, "weight", "weight of each incoming spike on depressing synapse; increase to reduce number of spikes within tauS window to filter out events.");
        setPropertyTooltip(cat, "showStateAtMouse", "Shows synaptic adaptation state at mouse position (expensive)");
        setPropertyTooltip(cat, "showBlockingProbabilityMask", "<html>Overlay a transparent color mask that is brighter where synaptic depression is closer to 1 (low probability of passing events).<p>Not stored in preferences.");
        setPropertyTooltip(cat, "overlayColor", "Color of the blocking-probability mask overlay (stored in preferences). Brighter where depression is closer to 1.");
        setPropertyTooltip(cat, "saveState", "Saves synaptic state to disk");
        setPropertyTooltip(cat, "loadState", "Loads synaptic state from disk");
        setPropertyTooltip(cat, "clearState", "Clears the synaptic depression state of all synapses");
        hideProperty("correlationTimeS");
        hideProperty("antiCasualEnabled");
        hideProperty("filterHotPixels");
        hideProperty("letFirstEventThrough");
        hideProperty("sigmaDistPixels");
        hideProperty("subsampleBy");
        updateOverlayRgb();
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in); // sets up statistics
        for (BasicEvent e : in) {
            if (!(e instanceof TypedEvent)) {
                throw new RuntimeException("event type must be TypedEvent, got event " + e);
            }
            totalEventCount++; // for statistics
            if (!neurons.stimulate((TypedEvent) e)) {
                filterOut(e);
            } else {
                filterIn(e);
            }
        }
        return in;
    }

    @Override
    synchronized public void resetFilter() {
        if (neurons != null) {
            neurons.initialize(this);
        }
    }

    @Override
    public void initFilter() {
        super.initFilter();
        checkNeuronAllocation();
        removeNoiseFilterControl();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        if (!showBlockingProbabilityMask && !showStateAtMouse) {
            return;
        }
        if (neurons == null) {
            return;
        }
        if (showBlockingProbabilityMask) {
            overlayBlockingProbabilityMask(drawable);
        }
        if (!showStateAtMouse) {
            return;
        }
        Point p = chip.getCanvas().getMousePixel();
        if (p == null || !chip.getCanvas().wasMousePixelInsideChipBounds()) {
            return;
        }
        neurons.display(drawable, p);
    }

    /**
     * Uses {@link DavisRenderer} annotation pixmap when the current display
     * method draws it ({@link ChipRendererDisplayMethodRGBA}); otherwise draws
     * blended quads.
     */
    private void overlayBlockingProbabilityMask(GLAutoDrawable drawable) {
        DavisRenderer pixmapRenderer = annotationPixmapRenderer();
        if (pixmapRenderer != null) {
            blockingMaskOverlayActive = true;
            neurons.fillBlockingProbabilityAnnotation(pixmapRenderer);
            return;
        }
        neurons.drawBlockingProbabilityMask(drawable);
    }

    /**
     * Annotation pixmap is filled on {@link DavisRenderer} and uploaded by
     * {@link ChipRendererDisplayMethodRGBA}. Other display methods have no such
     * overlay layer.
     */
    private DavisRenderer annotationPixmapRenderer() {
        if (chip == null || chip.getCanvas() == null) {
            return null;
        }
        if (!(chip.getCanvas().getDisplayMethod() instanceof ChipRendererDisplayMethodRGBA)) {
            return null;
        }
        if (chip.getRenderer() instanceof DavisRenderer davisRenderer) {
            return davisRenderer;
        }
        return null;
    }

    private void disableBlockingMaskOverlay() {
        if (!blockingMaskOverlayActive) {
            return;
        }
        blockingMaskOverlayActive = false;
        DavisRenderer pixmapRenderer = annotationPixmapRenderer();
        if (pixmapRenderer == null) {
            return;
        }
        pixmapRenderer.setDisplayAnnotation(false);
        pixmapRenderer.resetAnnotationFrame(0);
    }

    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (!yes) {
            disableBlockingMaskOverlay();
        }
    }

    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
    }
    
   

    private void checkNeuronAllocation() {
        if (chip.getNumCells() == 0) {
            return;
        }
        if ((neurons == null) || (neurons.getNumCells() != chip.getNumCells())) {
            neurons = new Neurons(this);
        }
    }

    static class Neurons implements Serializable {

        private final float max = 1; // max state
        Neuron[][][] cells;
        private int numCells;
        transient DepressingSynapseFilter filter;

        public Neurons(DepressingSynapseFilter filter) {
            this.filter = filter;
            AEChip chip = filter.getChip();
            numCells = chip.getNumCells();
            cells = new Neuron[chip.getSizeX()][chip.getSizeY()][chip.getNumCellTypes()];
            // fill array
            for (int i = 0; i < cells.length; i++) {
                for (int j = 0; j < cells[i].length; j++) {
                    for (int k = 0; k < cells[i][j].length; k++) {
                        cells[i][j][k] = new Neuron();
                    }
                }
            }
        }

        void reset() {
            for (Neuron[][] cell : cells) {
                for (int j = 0; j < cell.length; j++) {
                    for (Neuron n : cell[j]) {
                        if (n != null) {
                            n.reset();
                        }
                    }
                }
            }
        }

        void initialize(DepressingSynapseFilter filter) {
            for (Neuron[][] cell : cells) {
                for (int j = 0; j < cell.length; j++) {
                    for (Neuron n : cell[j]) {
                        if (n != null) {
                            n.initialize(filter);
                        }
                    }
                }
            }
        }
        // returns true if incoming spike caused outgoing spike

        boolean stimulate(TypedEvent e) {
            return cells[e.x][e.y][e.type].stimulate(e.timestamp);
        }

        private int getNumCells() {
            return numCells;
        }

        private void display(GLAutoDrawable drawable, Point p) {
            Neuron[] n = cells[p.x][p.y];
            float s1 = n[0].getState(), s2 = n[1].getState();
            float avg = (s1 + s2) / 2;
            String s = String.format("%5.3f", avg);
            Rectangle2D rect = DrawGL.drawString(filter.getShowFilteringStatisticsFontSize(), p.x, p.y, .5f, Color.white, s);
            GL2 gl = drawable.getGL().getGL2();
            gl.glRectf(p.x, p.y - 2, p.x + ((float) rect.getWidth() * avg * .7f), p.y - 1);

        }

        private float maxState(Neuron[] n) {
            float m = 0;
            for (Neuron neuron : n) {
                float s = neuron.getState();
                if (s > m) {
                    m = s;
                }
            }
            return m;
        }

        /**
         * Writes depression into the DavisRenderer annotation pixmap. The
         * display method blends with SRC_COLOR, so RGB must stay strong (the
         * picked overlay color at s=1) or the overlay is nearly black.
         */
        private void fillBlockingProbabilityAnnotation(DavisRenderer renderer) {
            renderer.setDisplayAnnotation(true);
            float[] rgba = filter.blockingMaskRgba;
            for (int x = 0; x < cells.length; x++) {
                for (int y = 0; y < cells[x].length; y++) {
                    float s = maxState(cells[x][y]);
                    if (s < 1e-3f) {
                        rgba[0] = 0;
                        rgba[1] = 0;
                        rgba[2] = 0;
                        rgba[3] = 0;
                    } else {
                        // Lift modest depression so SRC_COLOR (dest' = src*src) stays visible.
                        float u = 0.55f + 0.45f * s;
                        rgba[0] = filter.overlayR * u;
                        rgba[1] = filter.overlayG * u;
                        rgba[2] = filter.overlayB * u;
                        rgba[3] = 1f;
                    }
                    renderer.setAnnotateColorRGBA(x, y, rgba);
                }
            }
        }

        /**
         * Fallback overlay when the display method has no annotation pixmap.
         */
        private void drawBlockingProbabilityMask(GLAutoDrawable drawable) {
            GL2 gl = drawable.getGL().getGL2();
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glBlendEquation(GL.GL_FUNC_ADD);
            for (int x = 0; x < cells.length; x++) {
                for (int y = 0; y < cells[x].length; y++) {
                    float s = maxState(cells[x][y]);
                    if (s < 1e-3f) {
                        continue;
                    }
                    gl.glColor4f(filter.overlayR, filter.overlayG, filter.overlayB, 0.2f + 0.7f * s);
                    gl.glRectf(x, y, x + 1, y + 1);
                }
            }
            gl.glDisable(GL.GL_BLEND);
        }

        private void saveState() {
            try {
                String name = this.getClass().getSimpleName() + "-state.dat";
                FileOutputStream fos = new FileOutputStream(name);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(cells);
                fos.close();
                log.info("wrote " + cells + " to file " + name);
            } catch (Exception e) {
                log.warning(e.toString());
                e.printStackTrace();
            }
        }

        private void loadState(DepressingSynapseFilter filter) {
            try {
                this.filter = filter;
                String name = this.getClass().getSimpleName() + "-state.dat";
                FileInputStream fis = new FileInputStream(name);
                ObjectInputStream ois = new ObjectInputStream(fis);
                cells = (Neuron[][][]) ois.readObject();
                fis.close();
                initialize(filter);
                log.info("read " + cells + " from file " + name);
            } catch (Exception e) {
                log.warning(e.toString());
                e.printStackTrace();
            }
        }

        private class Neuron implements Serializable {

            private boolean initialized = false; // flag to init delta time correctly
            private float state = 0; // internal state, clips to 0:1 and the larger the value, the more depressed is the synapse. The probability to transmit is linear in (1-state).
            private int lastt = 0; // time of last spike input

            // returns true if neuron spike caused by input spike at time t in us
            boolean stimulate(int t) {
                if (!initialized) {
                    lastt = t;
                    initialized = true;
                    return true;
                }
                if (t < lastt) {
                    reset();
                    return true;
                }
                int dt = t - lastt;
                float delta = dt / filter.tauUs;
                float exp = delta > 20 ? 0 : (float) Math.exp(-delta);
                float newstate = (state * exp) + filter.weight;
                if (newstate > max) {
                    newstate = max;
                }
                boolean spike = random.nextFloat() > state; // spike goes through based on decayed state
                state = newstate;
                lastt = t;
                return spike;
            }

            void reset() {
                initialized = false;
                state = 0;
            }

            void initialize(DepressingSynapseFilter filter) {
                initialized = false;
                Neurons.this.filter = filter; // deserialization BS
            }

            float getState() {
                return state;
            }
        }
    }

    /**
     * @return the tauS
     */
    public float getTauS() {
        return tauS;
    }

    /**
     * @param tauS recovery time constant in seconds
     */
    public void setTauS(float tauS) {
        float old = this.tauS;
        if (tauS < 1e-6f) {
            tauS = 1e-6f;
        }
        this.tauS = tauS;
        putFloat("tauS", tauS);
        tauUs = tauS * 1e6f;
        getSupport().firePropertyChange("tauS", old, this.tauS);
    }

    @Override
    public String infoString() {
        return String.format("%s: tau=%ss w=%s", camelCaseClassname(), eng.format(tauS), eng.format(weight));
    }

    /**
     * @return the weight
     */
    public float getWeight() {
        return weight;
    }

    /**
     * @param weight the weight to set
     */
    public void setWeight(float weight) {
        this.weight = weight;
        prefs().putFloat("DepressingSynapseFilter.weight", weight);
    }

    /**
     * @return the showStateAtMouse
     */
    public boolean isShowStateAtMouse() {
        return showStateAtMouse;
    }

    /**
     * @param showStateAtMouse the showStateAtMouse to set
     */
    public void setShowStateAtMouse(boolean showStateAtMouse) {
        this.showStateAtMouse = showStateAtMouse;
        putBoolean("showStateAtMouse", showStateAtMouse);
    }

    /**
     * @return the showBlockingProbabilityMask
     */
    public boolean isShowBlockingProbabilityMask() {
        return showBlockingProbabilityMask;
    }

    /**
     * Session-only overlay; not stored in preferences.
     *
     * @param showBlockingProbabilityMask the showBlockingProbabilityMask to set
     */
    public void setShowBlockingProbabilityMask(boolean showBlockingProbabilityMask) {
        boolean old = this.showBlockingProbabilityMask;
        this.showBlockingProbabilityMask = showBlockingProbabilityMask;
        getSupport().firePropertyChange("showBlockingProbabilityMask", old, showBlockingProbabilityMask);
        if (!showBlockingProbabilityMask) {
            disableBlockingMaskOverlay();
        }
    }

    /**
     * @return the overlayColor
     */
    public Color getOverlayColor() {
        return overlayColor;
    }

    /**
     * Stored overlay color for {@link #showBlockingProbabilityMask}.
     *
     * @param overlayColor the overlayColor to set
     */
    public void setOverlayColor(Color overlayColor) {
        Color old = this.overlayColor;
        if (overlayColor == null) {
            overlayColor = Color.BLUE;
        }
        this.overlayColor = overlayColor;
        putInt("overlayColor", overlayColor.getRGB());
        updateOverlayRgb();
        getSupport().firePropertyChange("overlayColor", old, this.overlayColor);
    }

    private void updateOverlayRgb() {
        overlayR = overlayColor.getRed() / 255f;
        overlayG = overlayColor.getGreen() / 255f;
        overlayB = overlayColor.getBlue() / 255f;
    }

    synchronized public void doSaveState() {
        if (neurons != null) {
            neurons.saveState();
        }

    }

    synchronized public void doLoadState() {
        checkNeuronAllocation();
        if (neurons != null) {
            neurons.loadState(this);
        }
    }

    synchronized public void doClearState() {
        if (neurons != null) {
            neurons.reset();
        }
    }
}
