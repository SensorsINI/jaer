/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.retinamodel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Random;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Models a single object motion cell that is excited by on or off activity
 * within its classical receptive field but is inhibited by synchronous on or
 * off activity in its extended RF, such as that caused by a saccadic eye
 * movement.
 *
 * @author tobi and diederik
 */
@Description("Models object motion cell known from mouse and salamander retina")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ObjectMotionCell extends AbstractRetinaModelCell implements FrameAnnotater {
    private ObjectMotionCellModel objectMotionCellModel = new ObjectMotionCellModel();
    private float synapticWeight = getFloat("synapticWeight", 1f);
    private float centerExcitationToSurroundInhibitionRatio = getFloat("centerExcitationToSurroundInhibitionRatio", 0.4386f);
    private boolean surroundSuppressionEnabled = getBoolean("surroundSuppressionEnabled", false);
    private Subunits subunits;
    float inhibition = 0, centerExcitation = 0; // summed subunit input to object motion cell
    private float subunitActivityBlobRadiusScale = getFloat("subunitActivityBlobRadiusScale", 0.004f);
    private float integrateAndFireThreshold = getFloat("integrateAndFireThreshold", 1f);
    private float nonLinearityOrder = getFloat("nonLinearityOrder", 2f);
    private boolean startLogging = getBoolean("startLogging", false);
    private boolean deleteLogging = getBoolean("deleteLogging", false);
    private float barsHeight = getFloat("barsHeight", 0.000020f);
    private int excludedEdgeSubunits = getInt("excludedEdgeSubunits", 1);
    private int tanhSaturation = getInt("tanhSaturation", 1);
    private boolean exponentialToTanh = getBoolean("exponentialToTanh", false);

        public ObjectMotionCell(AEChip chip) {
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
        setPropertyTooltip("nonLinearityOrder", "The non-linear order of the subunits' value before the total sum");
        setPropertyTooltip("startLogging", "Start logging inhibition and excitation");
        setPropertyTooltip("deleteLogging", "Delete the logging of inhibition and excitation");
        setPropertyTooltip("barsHeight", "set the magnitute of cen and sur if the inhibition and excitation are out of range");
        setPropertyTooltip("excludedEdgeSubunits", "Set the number of subunits excluded from computation at the edge");
        setPropertyTooltip("tanhSaturation", "Set the maximum contribution of a single subunit, where it saturates");
        setPropertyTooltip("exponentialToTanh", "Switch from exponential non-linearity to exponential tangent");
    }
    private int lastObjectMotionCellSpikeCheckTimestamp = 0;

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
            int dt = e.timestamp - lastObjectMotionCellSpikeCheckTimestamp;
            if (dt < 0) {
                lastObjectMotionCellSpikeCheckTimestamp = e.timestamp;
                return in;
            }
            if (dt > minUpdateIntervalUs) {
                lastObjectMotionCellSpikeCheckTimestamp = e.timestamp;
                objectMotionCellModel.update(e.timestamp);
            }
        }
//        System.out.println(String.format("spikeRate=%.1g \tonActivity=%.2f \toffActivity=%.1f", objectMotionCellModel.spikeRate, inhibition, offExcitation));
        return in;
    }

       @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();

        gl.glPushMatrix();
        gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 10);
        if (showOutputCell && (objectMotionCellModel.nSpikes > getIntegrateAndFireThreshold())) {
            gl.glColor4f(1, 1, 1, .2f);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            float radius = (chip.getMaxSize() * objectMotionCellModel.spikeRateHz) / maxSpikeRateHz / 2;
            glu.gluDisk(quad, 0, radius, 32, 1);
            objectMotionCellModel.resetSpikeCount();
        }
        gl.glPopMatrix();
        if (showSubunits) {
            gl.glColor4f(0, 1, 0, .3f);
            gl.glRectf(-10, 0, -5, barsHeight*inhibition);
            gl.glColor4f(1, 0, 0, .3f);
            gl.glRectf(-20, 0, -15, barsHeight*centerExcitation);
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
    public void resetFilter() {
        subunits = new Subunits();
    }

    @Override
    public void initFilter() {
        resetFilter();
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

        FileOutputStream out; // declare a file output object
        PrintStream p; // declare a print stream object

        public Subunits() {
            reset();
        }

        synchronized public void update(PolarityEvent e) {
            // subsample retina address to clump retina input pixel blocks.
            int x = e.x >> subunitSubsamplingBits, y = e.y >> subunitSubsamplingBits;
            if ((x < nx) && (y < ny)) {
                switch (e.polarity) {
                    case Off: // these subunits are excited by OFF events and in turn excite the approach cell
                        subunits[x][y].updatepos(e);
                        break;
                    case On: // these are excited by ON activity and in turn inhibit the approach cell
                        subunits[x][y].updatepos(e);
                        break;
                        // all subunits are excited by any retina on or off activity
                }
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
//            float inhibition = 0;
            for (int x = excludedEdgeSubunits; x < (nx-excludedEdgeSubunits); x++) {
                for (int y = excludedEdgeSubunits; y < (ny-excludedEdgeSubunits); y++) {
                    if (((x == (nx / 2)) && (y == (ny / 2))) || ((x == ((nx / 2) - 1)) && (y == (ny / 2))) || ((x == ((nx / 2) - 1)) && (y == ((ny / 2) - 1))) || ((x == (nx / 2)) && (y == ((ny / 2) - 1)))) {
                        continue; // don't include center
                    }
                    if(exponentialToTanh == false){
                        inhibition += (float) Math.pow(subunits[x][y].computeInputToCell(),nonLinearityOrder);
                    }else{
                        inhibition += tanhSaturation*Math.tanh(subunits[x][y].computeInputToCell());
                    }
            }}
            inhibition /= (ntot - 4);
            inhibition = synapticWeight * inhibition;
            if (startLogging == true){
                try {
                    // Create a new file output stream
                    FileOutputStream out = new FileOutputStream(new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\inhibition.txt"),true);
                    // Connect print stream to the output stream
                    p = new PrintStream(out);
                    p.print(inhibition);
                    p.print(", ");
                    p.println(lastUpdateTimestamp);
                    p.close();
                } catch (Exception e) {
                    System.err.println("Error writing to file");
                }
            }
              if (deleteLogging == true){
                File fout = new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\inhibition.txt");
                fout.delete();
              }
            return inhibition;
        }

        float computeExcitationToOutputCell() {
//            float centerExcitation = 0;
            if ((nx == 2) || (nx == 1) || (ny == 2) || (ny == 1)) {
                centerExcitation = centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[nx / 2][ny / 2].computeInputToCell();
            } else {
                if(exponentialToTanh == false){
                   centerExcitation = (float)(Math.pow((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[nx / 2][ny / 2].computeInputToCell()),nonLinearityOrder) + Math.pow((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[(nx / 2) - 1][ny / 2].computeInputToCell()),nonLinearityOrder) + Math.pow((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[(nx / 2) - 1][(ny / 2) - 1].computeInputToCell()),nonLinearityOrder) + Math.pow((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[nx / 2][(ny / 2) - 1].computeInputToCell()),nonLinearityOrder)) / 4;//average of 4 central cells
                }else{
                   centerExcitation = (float)((tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[nx / 2][ny / 2].computeInputToCell()))) + (tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[(nx / 2) - 1][ny / 2].computeInputToCell()))) + (tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[(nx / 2) - 1][(ny / 2) - 1].computeInputToCell()))) + (tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[nx / 2][(ny / 2) - 1].computeInputToCell())))) / 4;//average of 4 central cells
                }

            }
            if (startLogging == true){
                try {
                    // Create a new file output stream.
                    FileOutputStream out = new FileOutputStream(new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\excitation.txt"), true);
                    // Connect print stream to the output stream
                    p = new PrintStream(out);
                    p.print(centerExcitation);
                    p.print(", ");
                    p.println(lastUpdateTimestamp);
                    p.close();
                    out.close();
                } catch (Exception e) {
                    System.err.println("Error writing to file");
                }
            }

            if (deleteLogging == true){
              File fout = new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\excitation.txt");
              fout.delete();
            }

            return centerExcitation;
        }

        synchronized private void reset() {
            inhibition = 0;
            centerExcitation = 0;
            nx = (chip.getSizeX() >> getSubunitSubsamplingBits());
            ny = (chip.getSizeY() >> getSubunitSubsamplingBits());
            if (nx < 1) {
                nx = 1;
            }
            if (ny < 1) {
                ny = 1; // always at least one subunit
            }
            ntot = (nx-excludedEdgeSubunits) * (ny-excludedEdgeSubunits);
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
            for (int x = excludedEdgeSubunits; x < (nx-excludedEdgeSubunits); x++) {
                for (int y = excludedEdgeSubunits; y < (ny-excludedEdgeSubunits); y++) {
                    gl.glPushMatrix();
                    gl.glTranslatef((x << subunitSubsamplingBits) + off, (y << subunitSubsamplingBits) + off, 5);
                    if (((x == (nx / 2)) && (y == (ny / 2))) || ((x == ((nx / 2) - 1)) && (y == (ny / 2))) || ((x == ((nx / 2) - 1)) && (y == ((ny / 2) - 1))) || ((x == (nx / 2)) && (y == ((ny / 2) - 1)))) {
                        gl.glColor4f(1, 0, 0, alpha);
                    } else {
                        gl.glColor4f(0, 1, 0, alpha);
                    }
                    glu.gluDisk(quad, 0, subunitActivityBlobRadiusScale * subunits[x][y].computeInputToCell(), 16, 1);
                    gl.glPopMatrix();
                }
            }
            renderer.begin3DRendering();
            renderer.setColor(1, 0, 0, 1);
            renderer.draw3D("Center", 0, chip.getSizeY(), 0, .5f);
            renderer.setColor(0, 1, 0, 1);
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

        public void updatepos(PolarityEvent e) {
            vmem = vmem + 1;
        }

        public void updateneg (PolarityEvent e) {
            vmem = vmem - 1;
        }

        /**
         * subunit input is pure rectification
         */
        public float computeInputToCell() {
            if (!surroundSuppressionEnabled) {
//                if (vmem < 0) {
//                    return 0; // actually it cannot be negative since it only gets excitation from DVS events
//                } else {
                    return vmem;

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
        }
    }

    // models soma and integration and spiking of objectMotion cell
    private class ObjectMotionCellModel {

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
            float netSynapticInput = (subunits.computeExcitationToOutputCell() - subunits.computeInhibitionToOutputCell());
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
     * @return the subunitDecayTimeconstantMs
     */
    public float getSubunitDecayTimeconstantMs() {
        return subunitDecayTimeconstantMs;
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
     *
     * @return the nonLinearityOrder
     *
     */
    public float getNonLinearityOrder() {
        return nonLinearityOrder;
    }

    /**
     *
     * @param nonLinearityOrder the nonLinearityOrder to set
     *
     */
    public void setNonLinearityOrder(float nonLinearityOrder) {
        this.nonLinearityOrder = nonLinearityOrder;
        putFloat("nonLinearityOrder", nonLinearityOrder);
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
     * @return the barsHeight
     */
    public float getBarsHeight() {
        return barsHeight;
    }

    /**
     * @param barsHeight the barsHeight to set
     */
    public void setBarsHeight(float barsHeight) {
        this.barsHeight = barsHeight;
        putFloat("barsHeight", barsHeight);
    }

    /**
     * @return the excludedEdgeSubunits
     */
    public int getExcludedEdgeSubunits() {
        return excludedEdgeSubunits;
    }

    /**
     * @param excludedEdgeSubunits the excludedEdgeSubunits to set
     */
    public void setExcludedEdgeSubunits(int excludedEdgeSubunits) {
        this.excludedEdgeSubunits = excludedEdgeSubunits;
        putFloat("excludedEdgeSubunits", excludedEdgeSubunits);
    }


    /**
     * @return the onOffWeightRatio
     */
    public float getCenterExcitationToSurroundInhibitionRatio() {
        return centerExcitationToSurroundInhibitionRatio;
    }

    /**
     * @param onOffWeightRatio the onOffWeightRatio to set
     */
    public void setCenterExcitationToSurroundInhibitionRatio(float onOffWeightRatio) {
        this.centerExcitationToSurroundInhibitionRatio = onOffWeightRatio;
        putFloat("centerExcitationToSurroundInhibitionRatio", onOffWeightRatio);
    }

    /**
     * @return the tanhSaturation
     */
    public int getTanhSaturation() {
        return tanhSaturation;
    }

    /**
     * @param tanhSaturation the tanhSaturation to set
     */
    public void setTanhSaturation(int tanhSaturation) {
        this.tanhSaturation = tanhSaturation;
        putInt("tanhSaturation", tanhSaturation);
    }


        /**
     * @return the deleteLogging
     */
    public boolean isDeleteLogging() {
        return deleteLogging;
    }

    /**
     * @param deleteLogging the deleteLogging to set
     */
    public void setDeleteLogging(boolean deleteLogging) {
        this.deleteLogging = deleteLogging;
        putBoolean("deleteLogging", deleteLogging);
    }

            /**
     * @return the exponentialToTanh
     */
    public boolean isExponentialToTanh() {
        return exponentialToTanh;
    }

    /**
     * @param exponentialToTanh the exponentialToTanh to set
     */
    public void setExponentialToTanh(boolean exponentialToTanh) {
        this.exponentialToTanh = exponentialToTanh;
        putBoolean("exponentialToTanh", exponentialToTanh);
    }


        /**
     * @return the startLogging
     */
    public boolean isStartLogging() {
        return startLogging;
    }

    /**
     * @param startLogging the startLogging to set
     */
    public void setStartLogging(boolean startLogging) {
        this.startLogging = startLogging;
        putBoolean("startLogging", startLogging);
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
