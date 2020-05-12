/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.retinamodel;

import java.awt.Font;
import java.util.Observable;
import java.util.Random;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.awt.TextRenderer;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.SpikeSound;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Models vertical motion
 *
 * @author Diederik
 */
@Description("Models vertical object motion cell")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class VerticalObjectMotionCell extends EventFilter2D implements FrameAnnotater {

    private boolean showSubunits = getBoolean("showSubunits", true);
    private boolean showOutputCell = getBoolean("showOutputCell", true);
    private int subunitSubsamplingBits = getInt("subunitSubsamplingBits", 4); // each subunit is 2^n squared pixels
    private float subunitDecayTimeconstantMs = getFloat("subunitDecayTimeconstantMs", 2);
    private boolean enableSpikeSound = getBoolean("enableSpikeSound", true);
    private VerticalObjectMotionCellModel verticalObjectMotionCellModel = new VerticalObjectMotionCellModel();
    private float synapticWeight = getFloat("synapticWeight", 30);
    private float maxSpikeRateHz = getFloat("maxSpikeRateHz", 100);
    private float centerExcitionToSurroundInhibitionRatio = getFloat("centerExcitationToSurroundInhibitionRatio", 1f);
    private int minUpdateIntervalUs = getInt("minUpdateIntervalUs", 10000);
    private boolean surroundSuppressionEnabled = getBoolean("surroundSuppressionEnabled", false);
    private Subunits subunits;
    private SpikeSound spikeSound = new SpikeSound();
    float inhibition = 0, centerExcition = 0; // summed subunit input to object motion cell
    private TextRenderer renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 10), true, true);
    private float subunitActivityBlobRadiusScale = getFloat("subunitActivityBlobRadiusScale", 2f);
    private float integrateAndFireThreshold = getFloat("integrateAndFireThreshold", 1f);
    private boolean poissonFiringEnabled = getBoolean("poissonFiringEnabled", true);

    public VerticalObjectMotionCell(AEChip chip) {
        super(chip);
        setPropertyTooltip("showSubunits", "Enables showing subunit activity annotation over retina output");
        setPropertyTooltip("showOutputCell", "Enables showing object motion cell activity annotation over retina output");
        setPropertyTooltip("subunitSubsamplingBits", "Each subunit integrates events from 2^n by 2^n pixels, where n=subunitSubsamplingBits");
        setPropertyTooltip("synapticWeight", "Subunit activity inputs to the objectMotion neuron are weighted this much; use to adjust response magnitude");
        setPropertyTooltip("subunitDecayTimeconstantMs", "Subunit activity decays with this time constant in ms");
        setPropertyTooltip("enableSpikeSound", "Enables audio spike output from objectMotion cell");
        setPropertyTooltip("maxSpikeRateHz", "Maximum spike rate of objectMotion cell in Hz");
        setPropertyTooltip("centerExcitationToSurroundInhibitionRatio", "Inhibitory ON subunits are weighted by factor more than excitatory OFF subunit activity to the object motion cell");
        setPropertyTooltip("minUpdateIntervalUs", "subunits activities are decayed to zero at least this often in us, even if they receive no input");
        setPropertyTooltip("surroundSuppressionEnabled", "subunits are suppressed by surrounding activity of same type; reduces response to global dimming");
        setPropertyTooltip("subunitActivityBlobRadiusScale", "The blobs represeting subunit activation are scaled by this factor");
        setPropertyTooltip("integrateAndFireThreshold", "The ganglion cell will fire if the difference between excitation and inhibition overcomes this threshold");
        setPropertyTooltip("poissonFiringEnabled", "The ganglion cell fires according to Poisson rate model for net synaptic input");
    }
    private int lastVerticalObjectMotionCellSpikeCheckTimestamp = 0;

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!(in.getEventPrototype() instanceof PolarityEvent)) {
            return in;
        }
        if (in instanceof ApsDvsEventPacket) {
            checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak. we don't use output packet but it is necesary to iterate over DVS events only
        }
        clearOutputPacket();
        if (subunits == null) {
            resetFilter();
        }
        for (Object o : in) {
            PolarityEvent e = (PolarityEvent) o;
            if (e.isSpecial()) {
                continue;
            }
            subunits.update(e);
            int dt = e.timestamp - lastVerticalObjectMotionCellSpikeCheckTimestamp;
            if (dt < 0) {
                lastVerticalObjectMotionCellSpikeCheckTimestamp = e.timestamp;
                return in;
            }
            if (dt > minUpdateIntervalUs) {
                lastVerticalObjectMotionCellSpikeCheckTimestamp = e.timestamp;
                verticalObjectMotionCellModel.update(e.timestamp);
            }
        }
//        System.out.println(String.format("spikeRate=%.1g \tonActivity=%.2f \toffActivity=%.1f", objectMotionCellModel.spikeRate, inhibition, offExcitation));
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
        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        gl.glPushMatrix();
        gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 10);
        if (showOutputCell && (verticalObjectMotionCellModel.nSpikes > getIntegrateAndFireThreshold())) {
            gl.glColor4f(1, 1, 1, .2f);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            float radius = (chip.getMaxSize() * verticalObjectMotionCellModel.spikeRateHz) / maxSpikeRateHz / 2;
            glu.gluDisk(quad, 0, radius, 32, 1);
            verticalObjectMotionCellModel.resetSpikeCount();
        }
        gl.glPopMatrix();

        if (showSubunits) {
            gl.glColor4f(0, 1, 0, .3f);
            gl.glRectf(-10, 0, -5, inhibition);
            gl.glColor4f(1, 0, 0, .3f);
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

    /**
     * @return the subunitActivityBlobRadiusScale
     */
    public float getSubunitActivityBlobRadiusScale() {
        return subunitActivityBlobRadiusScale;
    }

    /**
     * @param subunitActivityBlobRadiusScale the subunitActivityBlobRadiusScale
     * to set
     */
    public void setSubunitActivityBlobRadiusScale(float subunitActivityBlobRadiusScale) {
        this.subunitActivityBlobRadiusScale = subunitActivityBlobRadiusScale;
        putFloat("subunitActivityBlobRadiusScale", subunitActivityBlobRadiusScale);
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

        /*      Subunit getCenterSubunit(){
         for (int x = nx/2; x < nx+2; x++) {
         for (int y = nx/2; y < ny+2; y++) {
         return subunits[x][y];
         }
         }
         }*/
        // updates appropriate subunit
        synchronized public void update(PolarityEvent e) {
            // subsample retina address to clump retina input pixel blocks.
            int x = e.x >> subunitSubsamplingBits, y = e.y >> subunitSubsamplingBits;
            if ((x < nx) && (y < ny)) {
                // all subunits are excited by any retina on or off activity
                subunits[x][y].update(e);
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
                        subunits[x][y].decayBy(decayFactor);
                    }
                }
            }
        }
        float totalInhibition, totalExcitation;

        float computeInhibitionToOutputCell() {
            totalInhibition = 0;
            float inhibition = 0;
            for (int x = 0; x < nx; x++) {//need of exception if 2x2 or 1x1 array
                for (int y = 0; y < ny; y++) {
                    if ((x == (nx / 2)) || (x == ((nx / 2) - 1))) {
                        continue; // don't include center
                    }
                    inhibition += subunits[x][y].computeInputToCell();
                }
            }
            inhibition /= (ntot - (nx*2));
            VerticalObjectMotionCell.this.inhibition = synapticWeight * inhibition;
            return VerticalObjectMotionCell.this.inhibition;
        }

        float computeExicitionToOutputCell() {
            if ((nx == 2) || (nx == 1)) {//exception to be handled better
                centerExcition = centerExcitionToSurroundInhibitionRatio * synapticWeight * subunits[nx / 2][ny / 2].computeInputToCell();
            } else {
            for (int x = ((nx/2)-1); x <= (nx/2); x++) {//need of exception if 2x2 or 1x1 array
                for (int y = 0; y < ny; y++) {
                    centerExcition += centerExcitionToSurroundInhibitionRatio * synapticWeight * subunits[x][y].computeInputToCell();
                }
            }
                centerExcition /= (nx*2);
            }
            return centerExcition;//dividi
        }

        synchronized private void reset() {
            inhibition = 0;
            centerExcition = 0;
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

        private void render(GL2 gl) {
            final float alpha = .2f;
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            int off = (1 << (subunitSubsamplingBits)) / 2;
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    gl.glPushMatrix();
                    gl.glTranslatef((x << subunitSubsamplingBits) + off, (y << subunitSubsamplingBits) + off, 5);
                    if ((x == (nx / 2)) || (x == ((nx / 2) - 1))) {
                    gl.glColor4f(1, 0, 0, alpha);
                    } else {
                        gl.glColor4f(0, 1, 0, alpha);
                    }
                    glu.gluDisk(quad, 0, subunitActivityBlobRadiusScale *synapticWeight* subunits[x][y].computeInputToCell(), 16, 1);
                    gl.glPopMatrix();
                }
            }
            renderer.begin3DRendering();
            renderer.setColor(0, 1, 0, 1);
            renderer.draw3D("Center", 0, chip.getSizeY(), 0, .5f);
            renderer.setColor(1, 0, 0, 1);
            renderer.draw3D("Surround", chip.getSizeX() / 2, chip.getSizeY(), 0, .5f);
            renderer.end3DRendering();


        }
    }

    // models one single subunit ON or OFF.
    // polarity is ignored here and only handled on update of objectMotion cell
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
                if ((x + 1) < subunits.nx) {
                    sum += mySubunits[x + 1][y].vmem;
                    n++;
                }
                if ((x - 1) >= 0) {
                    sum += mySubunits[x - 1][y].vmem;
                    n++;
                }
                if ((y + 1) < subunits.ny) {
                    sum += mySubunits[x][y + 1].vmem;
                    n++;
                }
                if ((y - 1) >= 0) {
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

    // models soma and integration and spiking of objectMotion cell
    private class VerticalObjectMotionCellModel {

        int lastTimestamp = 0;
//        float threshold;
//        float refracPeriodMs;
        Random r = new Random();
        float membraneState = 0;
        int nSpikes = 0; // counts spikes since last rendering cycle
        private LowpassFilter isiFilter = new LowpassFilter(300);
        private int lastSpikeTimestamp = 0;
        private boolean initialized = false;
        float spikeRateHz = 0;


        synchronized private boolean update(int timestamp) {
            // compute subunit input to us
            float netSynapticInput = (subunits.computeExicitionToOutputCell() - subunits.computeInhibitionToOutputCell());
           int dtUs = timestamp - lastTimestamp;
            if (dtUs < 0) {
                dtUs = 0; // to handle negative dt
            }
            lastTimestamp = timestamp;
            if (poissonFiringEnabled) {
                float spikeRate = netSynapticInput;
                if (spikeRate < 0) {
                    return false;
                }
                if (spikeRate > maxSpikeRateHz) {
                    spikeRate = maxSpikeRateHz;
                }
                if (r.nextFloat() < (spikeRate * 1e-6f * dtUs)) {
                    spike(timestamp);
                    return true;
                } else {
                    return false;
                }
            } else { // IF neuron
                membraneState += netSynapticInput * dtUs * 1e-6f;
                if (membraneState > integrateAndFireThreshold) {
                    spike(timestamp);
                    membraneState = 0;
                    return true;
                } else if (membraneState < -10) {
                    membraneState = 0;
                    return false;
                } else {
                    return false;
                }
            }
        }
     void spike(int timestamp) {
            if (enableSpikeSound) {
                spikeSound.play();
            }
            nSpikes++;
            int dtUs = timestamp - lastSpikeTimestamp;
            if (initialized && (dtUs>=0)) {
                float avgIsiUs = isiFilter.filter(dtUs, timestamp);
                spikeRateHz = 1e6f / avgIsiUs;
            } else {
                initialized = true;
            }
            lastSpikeTimestamp = timestamp;
        }

        void reset() {
            membraneState = 0;
            isiFilter.reset();
            initialized=false;
        }

        private void resetSpikeCount() {
            nSpikes = 0;
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
     * @return the showobjectMotionCell
     */
    public boolean isShowOutputCell() {
        return showOutputCell;
    }

    /**
     * @param showobjectMotionCell the showobjectMotionCell to set
     */
    public void setShowOutputCell(boolean showObjectMotionCell) {
        this.showOutputCell = showObjectMotionCell;
        putBoolean("showOutputCell", showObjectMotionCell);
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
     *
     * @return the integrateAndFireThreshold
     *
     */
    public float getIntegrateAndFireThreshold() {
        return integrateAndFireThreshold;
    }

    /**
     *
     * @param integrateAndFireThreshold the integrateAndFireThreshold to set
     *
     */
    public void setIntegrateAndFireThreshold(float integrateAndFireThreshold) {
        this.integrateAndFireThreshold = integrateAndFireThreshold;
        putFloat("integrateAndFireThreshold", integrateAndFireThreshold);
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
        /**
     * @return the poissonFiringEnabled
     */
    public boolean isPoissonFiringEnabled() {
        return poissonFiringEnabled;
    }

    /**
     * @param poissonFiringEnabled the poissonFiringEnabled to set
     */
    public void setPoissonFiringEnabled(boolean poissonFiringEnabled) {
        this.poissonFiringEnabled = poissonFiringEnabled;
        putBoolean("poissonFiringEnabled", poissonFiringEnabled);
    }
}
