/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.retinamodel;

import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLException;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.SpikeSound;

/**
 * Models a single approach cell discovered by Botond Roska group in Basel. This
 * cell responds to approaching (expanding, but not translating) dark objects,
 * such as perhaps a hungry bird diving on the mouse.
 *
 * From Botond: The important point is NO delay.
 *
 * Small subunits, that make OFF excitation and ON inhibition input to the
 * approach cell.
 *
 * Importantly both subunits have an expansive nonlinearity such that the ON
 * subunit does not respond when there is a darkening input signal and its
 * response increases nonlinearly with contrast. (you can implement as
 * nonlinearity that has two segments, zero up to a positive number and then
 * linear or below zero it is zero and above zero some exponential. Same
 * nonlinearity for OFF subunits.
 *
 * Ganglion cell is much larger than the subunits and sums them together.
 *
 * This way when there is lateral motion the ON inhibition and OFF excitation
 * sums together to zero response (because of the lack of delay) but when there
 * is approach motion of a black object then there is only excitation.
 *
 * The importance of the nonlinearity is that this way the system will respond
 * when there is an approaching object.
 *
 * @author tobi
 */
@Description("Models approach cell discovered by Botond Roska group")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ApproachCell extends EventFilter2D implements FrameAnnotater, Observer {

    private boolean showSubunits = getBoolean("showSubunits", true);
    private boolean showApproachCell = getBoolean("showApproachCell", true);
    private int subunitSubsamplingBits = getInt("subunitSubsamplingBits", 4); // each subunit is 2^n squared pixels
    private float subunitDecayTimeconstantMs = getFloat("subunitDecayTimeconstantMs", 60);
    private boolean enableSpikeSound = getBoolean("enableSpikeSound", true);
    private ApproachCellModel approachCellModel = new ApproachCellModel();
    private float synapticWeight = getFloat("synapticWeight", 18);
    private float maxSpikeRateHz = getFloat("maxSpikeRateHz", 100);
    private float onOffWeightRatio = getFloat("onOffWeightRatio", 1.2f);
    private int minUpdateIntervalUs = getInt("minUpdateIntervalUs", 10000);
//    private SubSampler subSampler=new SubSampler(chip);
    private Subunits subunits;
    private SpikeSound spikeSound = new SpikeSound();
    float onInhibition = 0, offExcitation = 0; // summed subunit input to approach cell
    private TextRenderer renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 10), true, true);

    public ApproachCell(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        setPropertyTooltip("showSubunits", "Enables showing subunit activity annotation over retina output");
        setPropertyTooltip("showApproachCell", "Enables showing approach cell activity annotation over retina output");
        setPropertyTooltip("subunitSubsamplingBits", "Each subunit integrates events from 2^n by 2^n pixels, where n=subunitSubsamplingBits");
        setPropertyTooltip("synapticWeight", "Subunit activity inputs to the approach neuron are weighted this much; use to adjust response magnitude");
        setPropertyTooltip("subunitDecayTimeconstantMs", "Subunit activity decays with this time constant in ms");
        setPropertyTooltip("enableSpikeSound", "Enables audio spike output from approach cell");
        setPropertyTooltip("maxSpikeRateHz", "Maximum spike rate of approach cell in Hz");
        setPropertyTooltip("onOffWeightRatio", "Inhibitory ON subunits are weighted by factor more than excitatory OFF subunit activity to the approach cell");
        setPropertyTooltip("minUpdateIntervalUs", "subunits activities are decayed to zero at least this often in us, even if they receive no input");

    }
    private int lastApproachCellSpikeCheckTimestamp = 0;

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!(in.getEventPrototype() instanceof PolarityEvent)) {
            return in;
        }
        for (Object o : in) {
            PolarityEvent e = (PolarityEvent) o;

            subunits.update(e);
            int dt = e.timestamp - lastApproachCellSpikeCheckTimestamp;
            if (dt < 0) {
                lastApproachCellSpikeCheckTimestamp = e.timestamp;
                return in;
            }
            if (dt > minUpdateIntervalUs) {
                lastApproachCellSpikeCheckTimestamp = e.timestamp;
                approachCellModel.update(e.timestamp);
            }
        }
//        System.out.println(String.format("spikeRate=%.1g \tonActivity=%.2f \toffActivity=%.1f", approachCellModel.spikeRate, onInhibition, offExcitation));

        return in;
    }

    @Override
    public void resetFilter() {
        subunits = new Subunits();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }
    GLU glu = new GLU();
    GLUquadric quad = glu.gluNewQuadric();
    boolean hasBlendChecked = false;
    boolean hasBlend = false;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        if (!hasBlendChecked) {
            hasBlendChecked = true;
            String glExt = gl.glGetString(GL.GL_EXTENSIONS);
            if (glExt.indexOf("GL_EXT_blend_color") != -1) {
                hasBlend = true;
            }
        }
        if (hasBlend) {
            try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                gl.glBlendEquation(GL.GL_FUNC_ADD);
            } catch (GLException e) {
                e.printStackTrace();
                hasBlend = false;
            }
        }
        gl.glPushMatrix();
        gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 10);
        if (showApproachCell && approachCellModel.spikeRate > 0) {
            gl.glColor4f(1, 1, 1, .2f);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            float radius=chip.getMaxSize()*approachCellModel.spikeRate/maxSpikeRateHz/2;
            glu.gluDisk(quad, 0, radius, 32, 1);
        }
        gl.glPopMatrix();

        if (showSubunits) {
            gl.glColor4f(0, 1, 0, .3f);
            gl.glRectf(-10, 0, -5, onInhibition);
            gl.glColor4f(1, 0, 0, .3f);
            gl.glRectf(-20, 0, -15, offExcitation);
            renderer.begin3DRendering();
            renderer.setColor(1, 1, 1, .3f);
            renderer.draw3D("on", -10, -3, 0, .4f);
            renderer.draw3D("off", -20, -3, 0, .4f);
            renderer.end3DRendering();
            // render all the subunits now
            subunits.render(gl);
        }

    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg != null && (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY) && chip.getNumPixels() > 0) {
            initFilter();
        }
    }

    /**
     * @return the minUpdateIntervalUs
     */
    public int getMinUpdateIntervalUs() {
        return minUpdateIntervalUs;
    }

    /**
     * @param minUpdateIntervalUs the minUpdateIntervalUs to set
     */
    public void setMinUpdateIntervalUs(int minUpdateIntervalUs) {
        this.minUpdateIntervalUs = minUpdateIntervalUs;
        putInt("minUpdateIntervalUs", minUpdateIntervalUs);
    }

    // handles all subunits on and off
    private class Subunits {

        Subunit[][] onSubunits, offSubunits;
        int nx;
        int ny;
        int ntot;
        int lastUpdateTimestamp;

        public Subunits() {
            reset();
        }

        // updates appropriate subunit 
        synchronized public void update(PolarityEvent e) {
            // subsample retina address to clump retina input pixel blocks.
            int x = e.x >> subunitSubsamplingBits, y = e.y >> subunitSubsamplingBits;
            // on subunits are updated by ON events, off by OFF events
            switch (e.polarity) {
                case Off: // these subunits are excited by OFF events and in turn excite the approach cell
                    offSubunits[x][y].update(e); // ad event to subunit
                    break;
                case On: // these are excited by ON activity and in turn inhibit the approach cell
                    onSubunits[x][y].update(e);
            }
            maybeDecayAll(e);
        }

        void maybeDecayAll(BasicEvent e) {
            int dt = e.timestamp - lastUpdateTimestamp;
            if (dt < 0) {
                lastUpdateTimestamp = e.timestamp;
                return;
            }
            if (dt > minUpdateIntervalUs) {
                lastUpdateTimestamp = e.timestamp;
                // now update all subunits to RC decay activity toward zero
                float decayFactor = (float) Math.exp(-dt / (1000 * subunitDecayTimeconstantMs));
                for (int x = 0; x < nx; x++) {
                    for (int y = 0; y < ny; y++) {
                        onSubunits[x][y].decayBy(decayFactor);
                    }
                }
                for (int x = 0; x < nx; x++) {
                    for (int y = 0; y < ny; y++) {
                        offSubunits[x][y].decayBy(decayFactor);
                    }
                }
            }
        }

        float computeOnInhibition() {
            float onInhibition = 0;
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    onInhibition += onSubunits[x][y].computeInputToApproachCell();
                }
            }
            onInhibition /= ntot;
            ApproachCell.this.onInhibition = onInhibition;
            return onInhibition;
        }

        float computeOffExcitation() {
            float offExcitation = 0;
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    offExcitation += offSubunits[x][y].computeInputToApproachCell();
                }
            }
            offExcitation /= ntot;
            ApproachCell.this.offExcitation = offExcitation;
            return offExcitation;
        }

        synchronized private void reset() {
            onInhibition = 0;
            offExcitation = 0;
            nx = (chip.getSizeX() >> getSubunitSubsamplingBits());
            ny = (chip.getSizeY() >> getSubunitSubsamplingBits());
            if (nx < 1) {
                nx = 1;
            }
            if (ny < 1) {
                ny = 1; // always at least one subunit
            }
            ntot = nx * ny;
            onSubunits = new Subunit[nx][ny];
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    onSubunits[x][y] = new Subunit();
                }
            }
            offSubunits = new Subunit[nx][ny];
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    offSubunits[x][y] = new Subunit();
                }
            }
        }

        private void render(GL gl) {
            final float alpha = .2f;
            final float scaleRadius=.05f;
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    gl.glPushMatrix();
                    gl.glTranslatef(x << subunitSubsamplingBits, y << subunitSubsamplingBits, 5);
                    gl.glColor4f(1, 0, 0, alpha);
                    glu.gluDisk(quad, 0, scaleRadius*offSubunits[x][y].computeInputToApproachCell(), 16, 1);
                    gl.glColor4f(0, 1, 0, alpha);
                    glu.gluDisk(quad, 0, scaleRadius*onSubunits[x][y].computeInputToApproachCell(), 16, 1);
                    gl.glPopMatrix();
                }
            }

        }
    }

    // models one single subunit ON of OFF.
    // polarity is ignored here and only handled on update of approach cell
    private class Subunit {

        float vmem;

        public void decayBy(float factor) {
            vmem *= factor;
        }

        public void update(PolarityEvent e) {
            vmem = vmem + 1;
        }

        /**
         * subunit input is exponential function of vmem
         */
        public float computeInputToApproachCell() {
            if(vmem<0) return 0;
            else return vmem;
//            return (float) Math.exp(vmem / synapticWeight / (1 << subunitSubsamplingBits));
        }
    }

    // models soma and integration and spiking of approach cell
    private class ApproachCellModel {

        float spikeRate;
        int lastTimestamp = 0;
//        float threshold;
//        float refracPeriodMs;
        Random r = new Random();

        synchronized private boolean update(int timestamp) {
            // compute subunit input to us
            spikeRate = synapticWeight*(subunits.computeOffExcitation() - onOffWeightRatio * subunits.computeOnInhibition());
            int dtUs = timestamp - lastTimestamp;
            lastTimestamp = timestamp;
            if (spikeRate > getMaxSpikeRateHz()) {
                spikeRate = getMaxSpikeRateHz();
            }
            if (r.nextFloat() < spikeRate * 1e-6f * dtUs) {
                if (enableSpikeSound) {
                    spikeSound.play();
                }
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * @return the showSubunits
     */
    public boolean isShowSubunits() {
        return showSubunits;
    }

    /**
     * @param showSubunits the showSubunits to set
     */
    public void setShowSubunits(boolean showSubunits) {
        this.showSubunits = showSubunits;
        putBoolean("showSubunits", showSubunits);
    }

    /**
     * @return the showApproachCell
     */
    public boolean isShowApproachCell() {
        return showApproachCell;
    }

    /**
     * @param showApproachCell the showApproachCell to set
     */
    public void setShowApproachCell(boolean showApproachCell) {
        this.showApproachCell = showApproachCell;
        putBoolean("showApproachCell", showApproachCell);
    }

    /**
     * @return the subunitSubsamplingBits
     */
    public int getSubunitSubsamplingBits() {
        return subunitSubsamplingBits;
    }

    /**
     * @param subunitSubsamplingBits the subunitSubsamplingBits to set
     */
    synchronized public void setSubunitSubsamplingBits(int subunitSubsamplingBits) {
        this.subunitSubsamplingBits = subunitSubsamplingBits;
        putInt("subunitSubsamplingBits", subunitSubsamplingBits);
        resetFilter();
    }

    /**
     * @return the subunitDecayTimeconstantMs
     */
    public float getSubunitDecayTimeconstantMs() {
        return subunitDecayTimeconstantMs;
    }

    /**
     * @param subunitDecayTimeconstantMs the subunitDecayTimeconstantMs to set
     */
    public void setSubunitDecayTimeconstantMs(float subunitDecayTimeconstantMs) {
        this.subunitDecayTimeconstantMs = subunitDecayTimeconstantMs;
        putFloat("subunitDecayTimeconstantMs", subunitDecayTimeconstantMs);
    }

    /**
     * @return the enableSpikeSound
     */
    public boolean isEnableSpikeSound() {
        return enableSpikeSound;
    }

    /**
     * @param enableSpikeSound the enableSpikeSound to set
     */
    public void setEnableSpikeSound(boolean enableSpikeSound) {
        this.enableSpikeSound = enableSpikeSound;
        putBoolean("enableSpikeSound", enableSpikeSound);
    }

    /**
     * @return the synapticWeight
     */
    public float getSynapticWeight() {
        return synapticWeight;
    }

    /**
     * @param synapticWeight the synapticWeight to set
     */
    public void setSynapticWeight(float synapticWeight) {
        this.synapticWeight = synapticWeight;
        putFloat("synapticWeight", synapticWeight);
    }

    /**
     * @return the maxSpikeRateHz
     */
    public float getMaxSpikeRateHz() {
        return maxSpikeRateHz;
    }

    /**
     * @param maxSpikeRateHz the maxSpikeRateHz to set
     */
    public void setMaxSpikeRateHz(float maxSpikeRateHz) {
        this.maxSpikeRateHz = maxSpikeRateHz;
        putFloat("maxSpikeRateHz", maxSpikeRateHz);
    }

    /**
     * @return the onOffWeightRatio
     */
    public float getOnOffWeightRatio() {
        return onOffWeightRatio;
    }

    /**
     * @param onOffWeightRatio the onOffWeightRatio to set
     */
    public void setOnOffWeightRatio(float onOffWeightRatio) {
        this.onOffWeightRatio = onOffWeightRatio;
        putFloat("onOffWeightRatio", onOffWeightRatio);
    }
}
