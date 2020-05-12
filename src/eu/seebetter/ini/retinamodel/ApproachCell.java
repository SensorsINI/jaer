/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.retinamodel;

import java.util.Observable;
import java.util.Random;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import eu.visualize.ini.retinamodel.AbstractRetinaModelCell;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Models a single PV-5 approach cell discovered by Botond Roska group in Basel.
 * This cell responds to approaching (expanding, but not translating) dark
 * objects, such as perhaps a hungry bird diving on the mouse.
 *
 * This model is described in the paper below, Nature Neuroscience, 2009.
 *
 * Approach sensitivity in the retina processed by a multifunctional neural
 * circuit Thomas A Münch1,3,4, Rava Azeredo da Silveira2,4, Sandra Siegert1,
 * Tim James Viney1, Gautam B Awatramani1,3 & Botond Roska1 The detection of
 * approaching objects, such as looming predators, is necessary for survival.
 * Which neurons and circuits mediate this function? We combined genetic
 * labeling of cell types, two-photon microscopy, electrophysiology and
 * theoretical modeling to address this question. We identify an
 * approach-sensitive ganglion cell type in the mouse retina, resolve elements
 * of its afferent neural circuit, and describe how these confer approach
 * sensitivity on the ganglion cell. The circuit’s essential building block is a
 * rapid inhibitory pathway: it selectively suppresses responses to
 * non-approaching objects. This rapid inhibitory pathway, which includes AII
 * amacrine cells connected to bipolar cells through electrical synapses, was
 * previously described in the context of night-time vision. In the daytime
 * conditions of our experiments, the same pathway conveys signals in the
 * reverse direction. The dual use of a neural pathway in different
 * physiological conditions illustrates the efficiency with which several
 * functions can be accommodated in a single circuit.
 *
* 1Neural Circuit Laboratories, Friedrich Miescher Institute for Biomedical
 * Research, Basel, Switzerland. 2Department of Physics and Department of
 * Cognitive Studies, École Normale Supérieure, Paris, France. 3Present
 * addresses: Laboratory for Retinal Circuits and Optogenetics, Centre for
 * Integrative Neuroscience, Eberhard-Karls University Tübingen, Tübingen,
 * Germany (T.A.M.); Department of Anatomy and Neurobiology, Dalhousie
 * University, Halifax, Nova Scotia, Canada (G.B.A.). 4These authors contributed
 * equally to this work. Correspondence should be addressed to B.R.
 * (botond.roska@fmi.ch).
 *
* Computational model of approach sensitivity We incorporated the various
 * elements of the proposed composite receptive field of the PV-5 cell into a
 * computational model (Supplementary Fig. 5). The model PV-5 cell sums over a
 * large region covered by many push-pull subunits (Supplementary Figs. 5a,b)
 * that excite the PV-5 cell in response to local OFF inputs and inhibit it Peak
 * current (pA) CPP/ APB NBQX Control ** NS c n = 4 n = 4 n = 4 0 –20 –40 AII
 * amacrine cell: control –400 –200 0 200 400 –400 –200 0 200 400 Stimulus onset
 * Motion Stimulus onset Motion Approaching Lateral Lateral Receding a AII
 * amacrine cell: CPP/NBQX Approaching Lateral Lateral Receding b Velocity of
 * right edge (μm s–1) Velocity of left edge (μm s–1) –400 –200 0 200 400 –400
 * –200 0 200 400 Velocity of right edge (μm s–1) -40 0 Velocity of left edge
 * (μm s–1) pA Figure 8 The functional properties of AII amacrine cells are
 * consistent with the rapid inhibitory signal in PV-5 ganglion cells. (a,b)
 * Motion-response map of an AII amacrine cell in control conditions (a) and
 * with CPP/ NBQX (b). Map is analogous to that in Figure 1c. The recorded cell
 * was clamped to −60 mV. The radii of the disks are proportional to the peak
 * magnitudes of inward currents evoked by stimulus motion. The radii of the
 * dotted circles are proportional to the reduction of the excitatory currents
 * after the initial presentations of the black bar. The quadrant that
 * corresponds to approaching motion is shaded in light gray. (c) Average peak
 * magnitudes of excitatory currents in AII amacrine cells, in the lateral and
 * receding quadrants of the motion-response map, under different
 * pharmacological conditions (CPP/NBQX and 10 μM APB). Error bars, s.e.m.; ***P
 * < 0.001; NS, P ≥ 0.05. © 2009 Nature America, Inc. All rights reserved. 1314
 * VOLUME 12 | NUMBER 10 | october 2009 nature NEUR OSCIEN CE art ic l e s in
 * response to local ON inputs. The two processes—excitation and
 * inhibition—occur with similar dynamics (Supplementary Fig. 5c). As a result,
 * inhibition prevents responses to undesired stimuli (such as the laterally
 * moving object in Supplementary Fig. 5b). As a key element, signals from
 * subunits are rectified before being summed by the PV-5 cell (Supplementary
 * Fig. 5c). Because of this concave nonlinearity, strong local signals are
 * favored over weak diffuse ones. Thus, the model PV-5 cell responds to the
 * expanding edges of an approaching object even if the visual field undergoes
 * slow brightening so as to prevent overall dimming (such as in Fig. 2a). The
 * computational model reproduces the data (Figs. 1d and 2c) and closely follows
 * experimental traces for an array of input patterns and velocities.
 *
*
 * Computational model of approach sensitivity We incorporated the various
 * elements of the proposed composite receptive field of the PV-5 cell into a
 * computational model (Supplementary Fig. 5). The model PV-5 cell sums over a
 * large region covered by many push-pull subunits (Supplementary Figs. 5a,b)
 * that excite the PV-5 cell in response to local OFF inputs and inhibit it in
 * response to local ON inputs. The two processes—excitation and
 * inhibition—occur with similar dynamics (Supplementary Fig. 5c). As a result,
 * inhibition prevents responses to undesired stimuli (such as the laterally
 * moving object in Supplementary Fig. 5b). As a key element, signals from
 * subunits are rectified before being summed by the PV-5 cell (Supplementary
 * Fig. 5c). Because of this concave nonlinearity, strong local signals are
 * favored over weak diffuse ones. Thus, the model PV-5 cell responds to the
 * expanding edges of an approaching object even if the visual field undergoes
 * slow brightening so as to prevent overall dimming (such as in Fig. 2a). The
 * computational model reproduces the data (Figs. 1d and 2c) and closely follows
 * experimental traces for an array of input patterns and velocities.
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
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ApproachCell extends AbstractRetinaModelCell {

    private boolean showApproachCell = getBoolean("showApproachCell", true);
    private ApproachCellModel approachCellModel = new ApproachCellModel();
    private float synapticWeight = getFloat("synapticWeight", 30);
    private float onOffWeightRatio = getFloat("onOffWeightRatio", 1.2f);
    private boolean surroundSuppressionEnabled = getBoolean("surroundSuppressionEnabled", false);
//    private SubSampler subSampler=new SubSampler(chip);
    private Subunits subunits;
    float onInhibition = 0, offExcitation = 0; // summed subunit input to approach cell
    private float integrateAndFireThreshold = getFloat("integrateAndFireThreshold", 1f);

    public ApproachCell(AEChip chip) {
        super(chip);
        setPropertyTooltip("showSubunits", "Enables showing subunit activity annotation over retina output");
        setPropertyTooltip("showApproachCell", "Enables showing approach cell activity annotation over retina output");
        setPropertyTooltip("subunitSubsamplingBits", "Each subunit integrates events from 2^n by 2^n pixels, where n=subunitSubsamplingBits");
        setPropertyTooltip("synapticWeight", "Subunit activity inputs to the approach neuron are weighted this much; use to adjust response magnitude");
        setPropertyTooltip("subunitDecayTimeconstantMs", "Subunit activity decays with this time constant in ms");
        setPropertyTooltip("enableSpikeSound", "Enables audio spike output from approach cell");
        setPropertyTooltip("maxSpikeRateHz", "Maximum spike rate of approach cell in Hz for Poisson firing model");
        setPropertyTooltip("onOffWeightRatio", "Inhibitory ON subunits are weighted by factor more than excitatory OFF subunit activity to the approach cell");
        setPropertyTooltip("minUpdateIntervalUs", "subunits activities are decayed to zero at least this often in us, even if they receive no input");
        setPropertyTooltip("surroundSuppressionEnabled", "subunits are suppressed by surrounding activity of same type; reduces response to global dimming");
        setPropertyTooltip("integrateAndFireThreshold", "The ganglion cell will fire if the difference between excitation and inhibition overcomes this threshold; only has effect if poissonFiringEnabled=false.");
        setPropertyTooltip("poissonFiringEnabled", "The ganglion cell fires according to Poisson rate model for net synaptic input");

    }
    private int lastApproachCellSpikeCheckTimestamp = 0;

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!(in.getEventPrototype() instanceof PolarityEvent)) {
            return in;
        }
        checkOutputPacketEventType(in);
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
        approachCellModel.reset();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }
    GLU glu = null;
    GLUquadric quad = null;

    boolean hasBlendChecked = false;
    boolean hasBlend = false;

    @Override
    public void annotate(GLAutoDrawable drawable) {
    	if (glu == null) {
    		glu = new GLU();
    	    quad = glu.gluNewQuadric();
    	}

        GL2 gl = drawable.getGL().getGL2();
        checkBlend(gl);
        gl.glPushMatrix();
        gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 10);
        if (showApproachCell && (approachCellModel.nSpikes > integrateAndFireThreshold)) {
            gl.glColor4f(0, 0, 1, .2f);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            //float radius = (chip.getMaxSize() * approachCellModel.spikeRateHz) / maxSpikeRateHz / 2;
            float radius = ((chip.getMaxSize() * approachCellModel.spikeRateHz) * 3)/100;
            glu.gluDisk(quad, 0, radius, 32, 1);
            approachCellModel.resetSpikeCount();
        }
        gl.glPopMatrix();

        if (showSubunits) {
            //float OnInhibitionShow= float(Math.log(10)(double(onInhibition)));
            gl.glColor4f(0, 1, 0, .3f);
            gl.glRectf(-10, 0, -5, onInhibition);
            gl.glColor4f(1, 0, 0, .3f);
            gl.glRectf(-20, 0, -15, offExcitation);
            renderer.begin3DRendering();
            renderer.setColor(0, 1, 0, .3f);
            renderer.draw3D("on", -10, -3, 0, .4f);
            renderer.setColor(1, 0, 0, .3f);
            renderer.draw3D("off", -20, -3, 0, .4f);
            renderer.end3DRendering();
            // render all the subunits now
            subunits.render(gl);
        }

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
            if ((x >= nx) || (y >= ny)) {
                return;
            }
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
        float totalInhibition, totalExcitation;

        float computeOnInhibition() {
            totalInhibition = 0;
            float onInhibition = 0;
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    onInhibition += onSubunits[x][y].computeInputToApproachCell();
                }
            }
            onInhibition /= ntot;
            ApproachCell.this.onInhibition = onOffWeightRatio * synapticWeight * onInhibition;
            return ApproachCell.this.onInhibition;
        }

        float computeOffExcitation() {
            float offExcitation = 0;
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    offExcitation += offSubunits[x][y].computeInputToApproachCell();
                }
            }
            offExcitation /= ntot;
            ApproachCell.this.offExcitation = synapticWeight * offExcitation;
            return ApproachCell.this.offExcitation;
        }

        synchronized private void reset() {
            onInhibition = 0;
            offExcitation = 0;
            nx = (chip.getSizeX() >> subunitSubsamplingBits);
            ny = (chip.getSizeY() >> subunitSubsamplingBits);
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
                    onSubunits[x][y] = new Subunit(x, y, onSubunits);
                }
            }
            offSubunits = new Subunit[nx][ny];
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    offSubunits[x][y] = new Subunit(x, y, offSubunits);
                }
            }
        }

        private void render(GL2 gl) {
            final float alpha = .2f;
            final float scaleRadius = .0001f;
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    gl.glPushMatrix();
                    final int shift = 1 << (subunitSubsamplingBits - 1);
                    gl.glTranslatef(shift + (x << subunitSubsamplingBits), shift + (y << subunitSubsamplingBits), 5);
                    gl.glColor4f(0, 1, 0, alpha);
                    //glu.gluDisk(quad, 0, (float)Math.log10((double)scaleRadius *synapticWeight* offSubunits[x][y].computeInputToApproachCell()), 16, 1);
                    glu.gluDisk(quad, 0, scaleRadius *synapticWeight*  onSubunits[x][y].computeInputToApproachCell(), 16, 1);
                    gl.glColor4f(1, 0, 0, alpha);
                    glu.gluDisk(quad, 0, scaleRadius *synapticWeight*  offSubunits[x][y].computeInputToApproachCell(), 16, 1);
                    //glu.gluDisk(quad, 0, (float)Math.log10((double)scaleRadius *synapticWeight* onSubunits[x][y].computeInputToApproachCell()), 16, 1);
                    gl.glPopMatrix();
                }
            }
            renderer.begin3DRendering();
            renderer.setColor(0, 1, 0, 1);
            renderer.draw3D("Inhibitory ON subunits", 0, chip.getSizeY(), 0, .5f);
            renderer.setColor(1, 0, 0, 1);
            renderer.draw3D("Excitatory OFF subunits", chip.getSizeX() / 2, chip.getSizeY(), 0, .5f);
            renderer.end3DRendering(); // annotation on top

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
        public float computeInputToApproachCell() {
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

    // models soma and integration and spiking of approach cell
    private class ApproachCellModel {

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

        /**
         * Returns true if neuron spikes this time interval
         */
        synchronized private boolean update(int timestamp) {
            // compute subunit input to us
            float netSynapticInput = (subunits.computeOffExcitation() - subunits.computeOnInhibition());
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
     * @return the integrateAndFireThreshold
     */
    public float getIntegrateAndFireThreshold() {
        return integrateAndFireThreshold;
    }

    /**
     * @param integrateAndFireThreshold the integrateAndFireThreshold to set
     */
    public void setIntegrateAndFireThreshold(float integrateAndFireThreshold) {
        this.integrateAndFireThreshold = integrateAndFireThreshold;
        putFloat("integrateAndFireThreshold", integrateAndFireThreshold);
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
     * @return the subunitDecayTimeconstantMs
     */
    public float getSubunitDecayTimeconstantMs() {
        return subunitDecayTimeconstantMs;
    }

    /**
     * @param subunitDecayTimeconstantMs the subunitDecayTimeconstantMs to set
     */
    @Override
	public void setSubunitDecayTimeconstantMs(float subunitDecayTimeconstantMs) {
        this.subunitDecayTimeconstantMs = subunitDecayTimeconstantMs;
        putFloat("subunitDecayTimeconstantMs", subunitDecayTimeconstantMs);
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
