/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.retinamodel;

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
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.SpikeSound;

/**
 * Models a single object motion cell that is excited by on or off activity within its classical receptive 
 * field but is inhibited by synchronous on or off activity in its extended RF,
 * such as that caused by a saccadic eye movement.
 * @author tobi
 */
@Description("Models object motion cell known mouse and salamander retina")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ObjectMotionCell extends EventFilter2D implements FrameAnnotater, Observer {

    private boolean showSubunits = getBoolean("showSubunits", true);
    private boolean showOutputCell = getBoolean("showOutputCell", true);
    private int subunitSubsamplingBits = getInt("subunitSubsamplingBits", 4); // each subunit is 2^n squared pixels
    private float subunitDecayTimeconstantMs = getFloat("subunitDecayTimeconstantMs", 2);
    private boolean enableSpikeSound = getBoolean("enableSpikeSound", true);
    private ObjectMotionCellModel approachCellModel = new ObjectMotionCellModel();
    private float synapticWeight = getFloat("synapticWeight", 30);
    private float maxSpikeRateHz = getFloat("maxSpikeRateHz", 100);
    private float centerExcitionToSurroundInhibitionRatio = getFloat("centerExcitationToSurroundInhibitionRatio", 1f);
    private int minUpdateIntervalUs = getInt("minUpdateIntervalUs", 10000);
    private boolean surroundSuppressionEnabled = getBoolean("surroundSuppressionEnabled", false);
    private Subunits subunits;
    private SpikeSound spikeSound = new SpikeSound();
    float inhibition = 0, centerExcition=0; // summed subunit input to object motion cell
    private TextRenderer renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 10), true, true);

    public ObjectMotionCell(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        setPropertyTooltip("showSubunits", "Enables showing subunit activity annotation over retina output");
        setPropertyTooltip("showOutputCell", "Enables showing approach cell activity annotation over retina output");
        setPropertyTooltip("subunitSubsamplingBits", "Each subunit integrates events from 2^n by 2^n pixels, where n=subunitSubsamplingBits");
        setPropertyTooltip("synapticWeight", "Subunit activity inputs to the approach neuron are weighted this much; use to adjust response magnitude");
        setPropertyTooltip("subunitDecayTimeconstantMs", "Subunit activity decays with this time constant in ms");
        setPropertyTooltip("enableSpikeSound", "Enables audio spike output from approach cell");
        setPropertyTooltip("maxSpikeRateHz", "Maximum spike rate of approach cell in Hz");
        setPropertyTooltip("centerExcitationToSurroundInhibitionRatio", "Inhibitory ON subunits are weighted by factor more than excitatory OFF subunit activity to the approach cell");
        setPropertyTooltip("minUpdateIntervalUs", "subunits activities are decayed to zero at least this often in us, even if they receive no input");
        setPropertyTooltip("surroundSuppressionEnabled", "subunits are suppressed by surrounding activity of same type; reduces response to global dimming");

    }
    private int lastApproachCellSpikeCheckTimestamp = 0;

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!(in.getEventPrototype() instanceof PolarityEvent)) {
            return in;
        }
        if(subunits==null) resetFilter();
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
//        System.out.println(String.format("spikeRate=%.1g \tonActivity=%.2f \toffActivity=%.1f", approachCellModel.spikeRate, inhibition, offExcitation));

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
        if (showOutputCell && approachCellModel.spikeRate > 0) {
            gl.glColor4f(1, 1, 1, .2f);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            float radius = chip.getMaxSize() * approachCellModel.spikeRate / maxSpikeRateHz / 2;
            glu.gluDisk(quad, 0, radius, 32, 1);
        }
        gl.glPopMatrix();

        if (showSubunits) {
            gl.glColor4f(1, 0, 0, .3f);
            gl.glRectf(-10, 0, -5, inhibition);
            gl.glColor4f(0, 1, 0, .3f);
            gl.glRectf(-20, 0, -15, centerExcition);
            renderer.begin3DRendering();
            renderer.setColor(0, 1, 0, .3f);
            renderer.draw3D("sur", -10, -3, 0, .4f);
            renderer.setColor(1, 0, 0, .3f);
            renderer.draw3D("cen", -20, -3, 0, .4f);
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

    // handles all subunits on and off
    private class Subunits {

        Subunit[][] subunits;
        int nx;
        int ny;
        int ntot;
        int lastUpdateTimestamp;

        public Subunits() {
            reset();
        }

        Subunit getCenterSubunit(){
            return subunits[nx>>1][ny>>1];
        }
        
        // updates appropriate subunit 
        synchronized public void update(PolarityEvent e) {
            // subsample retina address to clump retina input pixel blocks.
            int x = e.x >> subunitSubsamplingBits, y = e.y >> subunitSubsamplingBits;
            // all subunits are excited by any retina on or off activity
                    subunits[x][y].update(e);
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
                        subunits[x][y].decayBy(decayFactor);
                    }
                }
            }
        }
        float totalInhibition, totalExcitation;

        float computeInhibitionToOutputCell() {
            totalInhibition = 0;
            float inhibition = 0;
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    if(x==nx/2 && y==ny/2) continue; // don't include center
                    inhibition += subunits[x][y].computeInputToCell();
                }
            }
            inhibition /= (ntot-1);
            ObjectMotionCell.this.inhibition = synapticWeight*inhibition;
            return ObjectMotionCell.this.inhibition;
        }
        
        float computeExicitionToOutputCell(){
            centerExcition=centerExcitionToSurroundInhibitionRatio*synapticWeight*getCenterSubunit().computeInputToCell();
            return centerExcition;
        }

        synchronized private void reset() {
            inhibition = 0;
            centerExcition=0;
            nx = (chip.getSizeX() >> getSubunitSubsamplingBits());
            ny = (chip.getSizeY() >> getSubunitSubsamplingBits());
            if (nx < 1) {
                nx = 1;
            }
            if (ny < 1) {
                ny = 1; // always at least one subunit
            }
            ntot = nx * ny;
            subunits = new Subunit[nx][ny];
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    subunits[x][y] = new Subunit(x, y, subunits);
                }
            }
        }

        private void render(GL gl) {
            final float alpha = .2f;
            final float scaleRadius = .05f;
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    gl.glPushMatrix();
                    gl.glTranslatef(x << subunitSubsamplingBits, y << subunitSubsamplingBits, 5);
                    gl.glColor4f(1, 0, 0, alpha);
                    glu.gluDisk(quad, 0, scaleRadius * subunits[x][y].computeInputToCell(), 16, 1);
                    gl.glPopMatrix();
                }
            }
            renderer.begin3DRendering();
            renderer.setColor(0, 1, 0, 1);
            renderer.draw3D("Inhibitory ON subunits", 0, chip.getSizeY(), 0, .5f);
            renderer.setColor(1, 0, 0, 1);
            renderer.draw3D("Excitatory OFF subunits", chip.getSizeX()/2, chip.getSizeY(), 0,.5f);
            renderer.end3DRendering();


        }
    }

    // models one single subunit ON of OFF.
    // polarity is ignored here and only handled on update of approach cell
    private class Subunit {

        float vmem;
        int x, y;
        Subunit[][] mySubunits;

        public Subunit(int x, int y, Subunit[][] mySubunits) {
            this.x = x;
            this.y = y;
            this.mySubunits = mySubunits;
        }

        public void decayBy(float factor) {
            vmem *= factor;
        }

        public void update(PolarityEvent e) {
            vmem = vmem + 1;
        }

        /**
         * subunit input is pure rectification
         */
        public float computeInputToCell() {
            if (!surroundSuppressionEnabled) {
                if (vmem < 0) {
                    return 0; // actually it cannot be negative since it only gets excitation from DVS events
                } else {
                    return vmem;
                }
            } else { // surround inhibition
                // here we return the half-rectified local difference between ourselves and our neighbors
                int n = 0;
                float sum = 0;
                if (x + 1 < subunits.nx) {
                    sum += mySubunits[x + 1][y].vmem;
                    n++;
                }
                if (x - 1 >= 0) {
                    sum += mySubunits[x - 1][y].vmem;
                    n++;
                }
                if (y + 1 < subunits.ny) {
                    sum += mySubunits[x][y + 1].vmem;
                    n++;
                }
                if (y - 1 >= 0) {
                    sum += mySubunits[x][y - 1].vmem;
                    n++;
                }
                sum /= n;
                float result = vmem - sum;
                if (result < 0) {
                    return 0;
                } else {
                    return result; // half rectify result
                }
            }
//            return (float) Math.exp(vmem / synapticWeight / (1 << subunitSubsamplingBits));
        }
    }

    // models soma and integration and spiking of approach cell
    private class ObjectMotionCellModel {

        float spikeRate;
        int lastTimestamp = 0;
//        float threshold;
//        float refracPeriodMs;
        Random r = new Random();

        synchronized private boolean update(int timestamp) {
            // compute subunit input to us
            spikeRate = (subunits.computeExicitionToOutputCell()- subunits.computeInhibitionToOutputCell());
            int dtUs = timestamp - lastTimestamp;
            lastTimestamp = timestamp;
            if (spikeRate < 0) {
                spikeRate = 0;
            } else if (spikeRate > getMaxSpikeRateHz()) {
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
    public boolean isShowOutputCell() {
        return showOutputCell;
    }

    /**
     * @param showApproachCell the showApproachCell to set
     */
    public void setShowOutputCell(boolean showApproachCell) {
        this.showOutputCell = showApproachCell;
        putBoolean("showOutputCell", showApproachCell);
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
    public float getCenterExcitationToSurroundInhibitionRatio() {
        return centerExcitionToSurroundInhibitionRatio;
    }

    /**
     * @param onOffWeightRatio the onOffWeightRatio to set
     */
    public void setCenterExcitationToSurroundInhibitionRatio(float onOffWeightRatio) {
        this.centerExcitionToSurroundInhibitionRatio = onOffWeightRatio;
        putFloat("centerExcitationToSurroundInhibitionRatio", onOffWeightRatio);
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

    /**
     * @return the surroundSuppressionEnabled
     */
    public boolean isSurroundSuppressionEnabled() {
        return surroundSuppressionEnabled;
    }

    /**
     * @param surroundSuppressionEnabled the surroundSuppressionEnabled to set
     */
    public void setSurroundSuppressionEnabled(boolean surroundSuppressionEnabled) {
        this.surroundSuppressionEnabled = surroundSuppressionEnabled;
        putBoolean("surroundSuppressionEnabled", surroundSuppressionEnabled);
    }
}
