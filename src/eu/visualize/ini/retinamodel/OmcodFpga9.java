//-- Packages ----------------------------------------------------------------//
package eu.visualize.ini.retinamodel;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Observer;
import java.util.Random;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;

import eu.seebetter.ini.chips.davis.HotPixelFilter;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;
//****************************************************************************//
import net.sf.jaer.util.filter.LowpassFilter;

//-- Description -------------------------------------------------------------//
// Models multiple object motion cells that are excited by on or off activity
// within their classical receptive field but are inhibited by synchronous on or
// off activity in their extended RF, such as that caused by a saccadic eye
// movement. Also gives direction of movement of object
// @author diederik
@Description("Models object motion cells known from mouse and salamander retina")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
//****************************************************************************//

//-- Main class OmcodFpga --------------------------------------------------------//
public class OmcodFpga9 extends AbstractRetinaModelCell implements FrameAnnotater {

    private final OmcodFpgaModel OmcodFpgaModel = new OmcodFpgaModel();
    public RosNodePublisher RosNodePublisher = new RosNodePublisher();
    private final EventRateEstimator eventRateFilter;
    private final BackgroundActivityFilter backgroundActivityFilter;
    private final HotPixelFilter hotPixelFilter;
    private final FilterChain trackingFilterChain;
    private Subunits subunits;
    private int lastOmcodFpgaSpikeCheckTimestampUs;
    private int nxmax;
    private int nymax;
    private float IFthreshold;
    private boolean enableSpikeDraw;
    private int counter = 0;
    private int counterP = 0;
    private int lastIndex = 0;
    private float inhibitionValue;
    //private float startTime;
    //private float endTime;
    private float[][] excitationArray;
    private float[][] membraneStateArray;
    private float[][] netSynapticInputArray;
    private int[][] timeStampArray;
    private int[][] lastTimeStampArray;
    private int[][] dtUSarray;
    private int[][] timeStampSpikeArray;
    private int[][] lastTimeStampSpikeArray;
    private int[][] dtUSspikeArray;
    private int[][] nSpikesArray; // counts spikes since last rendering cycle
    private int[][] spikeRateHz; // spike rate
    private int[][] lastSpikedOMCTracker1; // save the OMC cells that last spiked
    private int[][] lastSpikedOMCTracker2; // save the OMC cells that last spiked
    private int[] lastSpikedOMC; // save the OMC cell that last spiked
    private int[][] lastSpikedOMCArray; // save the OMC cells that last spiked
    private float synapticWeight = getFloat("synapticWeight", 100f);
    private float vmemIncrease = getFloat("vmemIncrease", 100f);
    private float centerExcitationToSurroundInhibitionRatio = getFloat("centerExcitationToSurroundInhibitionRatio", 1.7f);
    private float subunitActivityBlobRadiusScale = getFloat("subunitActivityBlobRadiusScale", 0.022f);
    private float integrateAndFireThreshold = getFloat("integrateAndFireThreshold", 70f);
    private float increaseInThreshold = getFloat("increaseInThreshold", 10f);
    private float barsHeight = getFloat("barsHeight", 10f);
    private int showXcoord = getInt("showXcoord", 1);
    private int showYcoord = getInt("showYcoord", 1);
    private int Saturation = getInt("Saturation", 100);
    private boolean showSpecificOMCoutput = getBoolean("showSpecificOMCoutput", false);
    private boolean showAllOMCoutputs = getBoolean("showAllOMCoutputs", true);
    private int waitingTimeRstMs = getInt("waitingTimeRstMs", 500000);
    final int subsample = 4;
//------------------------------------------------------------------------------

//----------------------------------------------------------------------------//
//-- Initialise and ToolTip method -------------------------------------------//
//----------------------------------------------------------------------------//
    public OmcodFpga9(AEChip chip) {
        super(chip);
        this.enableSpikeDraw = false;
        this.nxmax = chip.getSizeX() >> subsample;
        this.nymax = chip.getSizeY() >> subsample;
        this.nSpikesArray = new int[nxmax][nymax]; // deleted -1 in all
        this.spikeRateHz = new int[nxmax][nymax];
        this.netSynapticInputArray = new float[nxmax][nymax];
        this.membraneStateArray = new float[nxmax][nymax];
        this.excitationArray = new float[nxmax][nymax];
        this.timeStampArray = new int[nxmax][nymax];
        this.lastTimeStampArray = new int[nxmax][nymax];
        this.dtUSarray = new int[nxmax][nymax];
        this.timeStampSpikeArray = new int[nxmax][nymax];
        this.lastTimeStampSpikeArray = new int[nxmax][nymax];
        this.dtUSspikeArray = new int[nxmax][nymax];
        this.lastSpikedOMC = new int[2];
        this.lastSpikedOMCArray = new int[2][2];

        trackingFilterChain = new FilterChain(chip);
        backgroundActivityFilter = new BackgroundActivityFilter(chip);
        hotPixelFilter = new HotPixelFilter(chip);
        eventRateFilter = new EventRateEstimator(chip);
        trackingFilterChain.add(backgroundActivityFilter);
        trackingFilterChain.add(hotPixelFilter);
        trackingFilterChain.add(eventRateFilter);
        setEnclosedFilterChain(trackingFilterChain); // labels enclosed filters as being enclosed
        setEnclosedFilterChain(trackingFilterChain);
        trackingFilterChain.reset();

        final String use = "1) Key Parameters", fix = "3) Fixed Parameters", disp = "2) Display", logging = "4) Logging";
        setPropertyTooltip(disp, "showSubunits", "Enables showing subunit activity annotation over retina output");
        setPropertyTooltip(disp, "showAllOMCoutputs", "Enables showing of all OMC outputs only");
        setPropertyTooltip(fix, "synapticWeight", "Subunit activity inputs to the objectMotion neuron are weighted this much; use to adjust response magnitude");
        setPropertyTooltip(fix, "vmemIncrease", "Increase in vmem per event received");
        setPropertyTooltip(disp, "enableSpikeSound", "Enables audio spike output from objectMotion cell");
        setPropertyTooltip(fix, "maxSpikeRateHz", "Maximum spike rate of objectMotion cell in Hz");
        setPropertyTooltip(fix, "centerExcitationToSurroundInhibitionRatio", "Inhibitory ON subunits are weighted by factor more than excitatory OFF subunit activity to the object motion cell");
        setPropertyTooltip(fix, "minUpdateIntervalUs", "subunits activities are decayed to zero at least this often in us, even if they receive no input");
        setPropertyTooltip(disp, "subunitActivityBlobRadiusScale", "The blobs represeting subunit activation are scaled by this factor");
        setPropertyTooltip(use, "integrateAndFireThreshold", "The ganglion cell will fire if the difference between excitation and inhibition overcomes this threshold");
        setPropertyTooltip(use, "increaseInThreshold", "increase in threshold of OMC neuron depending on activity");
        setPropertyTooltip(disp, "barsHeight", "set the magnitute of cen and sur if the inhibition and excitation are out of range");
        setPropertyTooltip(fix, "Saturation", "Set the maximum contribution of a single subunit, where it saturates");
        setPropertyTooltip(disp, "showXcoord", "decide which Object Motion Cell to show by selecting the X coordinate of the center");
        setPropertyTooltip(disp, "showYcoord", "decide which Object Motion Cell to show by selecting the Y coordinate of the center");
        setPropertyTooltip(disp, "showSpecificOMCoutput", "show specific OMC output");
        setPropertyTooltip(disp, "waitingTimeRstMs", "Reset tracker every set ms");
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Filter packet method ----------------------------------------------------//
//----------------------------------------------------------------------------//
    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        //if (!(in.getEventPrototype() instanceof PolarityEvent)) {
        //return
//        EventPacket temp = (EventPacket) eventRateFilter.filterPacket(dirFilter.filterPacket(backgroundActivityFilter.filterPacket(hotPixelFilter.filterPacket(in))));
        EventPacket temp = getEnclosedFilterChain().filterPacket(in);
        //if (in instanceof ApsDvsEventPacket) {
        checkOutputPacketEventType(getEnclosedFilterChain().filterPacket(in)); // make sure memory is allocated to avoid leak.
        // we don't use output packet but it is necesary to iterate over DVS events only
        //}
        //clearOutputPacket();
        if (subunits == null) {
            resetFilter();
        }
        if (temp == null) {
            return in;
        }
        float lastTime = 0;
        for (Object o : temp) {
            PolarityEvent e = (PolarityEvent) o;
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            subunits.update(e);
            int dt = e.timestamp - lastOmcodFpgaSpikeCheckTimestampUs;
            if (dt < 0) {
                lastOmcodFpgaSpikeCheckTimestampUs = e.timestamp;
                return in;
            }
            if (dt > minUpdateIntervalUs) {
                lastOmcodFpgaSpikeCheckTimestampUs = e.timestamp;
                OmcodFpgaModel.update(e.timestamp);
            }
            lastTime = e.timestamp;
        }
        PrintStream p; // declare a print stream object
        return in;
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Drawing method ----------------------------------------------------------//
//----------------------------------------------------------------------------//
    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBlendEquation(GL.GL_FUNC_ADD);
        gl.glPushMatrix();
        if ((getShowXcoord() < 0) || (getShowYcoord() < 0)
                || (getShowXcoord() > (nxmax - 1)) || (getShowYcoord() > (nymax - 1))) {
            setShowXcoord(0);
            setShowYcoord(0);
        }
        if (showSpecificOMCoutput) { // Show outputs of selected OMC firing
            gl.glPushMatrix();
            gl.glTranslatef((getShowXcoord() + 1) << subsample, (getShowYcoord() + 1) << subsample, 5);
            gl.glColor4f(1, 0, 0, 1f);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            float radius = (chip.getMaxSize() * spikeRateHz[getShowXcoord()][getShowYcoord()]) / maxSpikeRateHz / 2;
            glu.gluDisk(quad, 0, radius, 32, 1);
            gl.glPopMatrix();
            spikeRateHz[getShowXcoord()][getShowYcoord()] = 0;
        }
        // Green background to show where the inhibition is
        gl.glPushMatrix();
        gl.glColor4f(0, 1, 0, 0.1f);
        gl.glRectf((0 << subsample),
                (0 << subsample),
                ((nxmax) << subsample),
                ((nymax) << subsample));
        gl.glPopMatrix();
        // Red squares to show where the cells are
        for (int omcx = 1; omcx < (nxmax - 1); omcx += 2) {// 4 corners
            for (int omcy = 1; omcy < (nymax - 1); omcy += 2) {
                gl.glPushMatrix();
                gl.glColor4f(1, 0, 0, 0.1f); //4 side centers
                gl.glRectf((omcx << subsample),
                        (omcy << subsample),
                        ((omcx + 2) << subsample),
                        ((omcy + 2) << subsample));
                gl.glPopMatrix();
            }
        }

        if (showAllOMCoutputs) { // Dispaly outputs of OMC that fire
            if (enableSpikeDraw && (nSpikesArray[lastSpikedOMC[0]][lastSpikedOMC[1]] != 0)) {
                if (counter < (2 - 1)) {
                    counter++;
                } else {
                    counter = 0;
                }
                if (counter == 0) {
                    lastIndex = 2 - 1;
                } else {
                    lastIndex = counter - 1;
                }
                //Store all spiked cells
                for (int i = 0; i < 2; i++) {
                    if (i == 0) { //store x
                        lastSpikedOMCArray[i][counter] = lastSpikedOMC[0];
                    } else { //store y
                        lastSpikedOMCArray[i][counter] = lastSpikedOMC[1];
                    }
                }
                // Render all outputs
                gl.glPushMatrix();
                gl.glColor4f(1, 0, 0, 1f); // Violet outputs of OMCs
                gl.glRectf((lastSpikedOMC[0] << subsample),
                        (lastSpikedOMC[1] << subsample),
                        ((lastSpikedOMC[0] + 2) << subsample),
                        ((lastSpikedOMC[1] + 2) << subsample));
                gl.glPopMatrix();

                gl.glPushMatrix();
                renderer.begin3DRendering();
                renderer.setColor(12, 0, 1, .3f);
                renderer.draw3D("OMCspike( " + lastSpikedOMC[0] + " , " + lastSpikedOMC[1] + " )", -80, 20, 0, .4f);
                renderer.end3DRendering();
                enableSpikeDraw = false;
                gl.glPopMatrix();

                if ((lastOmcodFpgaSpikeCheckTimestampUs - lastTimeStampSpikeArray[lastSpikedOMC[0]][lastSpikedOMC[1]]) > (1000 * getWaitingTimeRstMs())) {//reset if long wait
                    for (int index = 0; index < 2; index++) {
                        for (int i = 0; i < 2; i++) {
                            if (i == 0) { //store x
                                lastSpikedOMCArray[i][index] = lastSpikedOMC[0];
                            } else { //store y
                                lastSpikedOMCArray[i][index] = lastSpikedOMC[1];
                            }
                        }
                    }
                }

                if (!(lastSpikedOMC[0] == lastSpikedOMCArray[0][counterP])) {
                    //Render Arrow
                    // motion vector points in direction of motion, *from* dir value (minus sign) which points in direction from prevous event
                    gl.glPushMatrix();
                    gl.glColor4f(1, 1, 0, 1f); // Violet outputs of OMCs
                    gl.glLineWidth(10);
                    DrawGL.drawVector(gl, (lastSpikedOMCArray[0][counterP] + 1) << subsample,
                            (lastSpikedOMCArray[1][counterP] + 1) << subsample,
                            ((lastSpikedOMC[0] + 1) << subsample) - ((lastSpikedOMCArray[0][counterP] + 1) << subsample),
                            ((lastSpikedOMC[1] + 1) << subsample) - ((lastSpikedOMCArray[1][counterP] + 1) << subsample),
                            1 << subsample, 1);
                    gl.glPopMatrix();
                }
                counterP = counter;
            }
            OmcodFpgaModel.resetSpikeCount();
        }
        if (showSubunits && showSpecificOMCoutput && !showAllOMCoutputs) { // Show subunits bars
            gl.glPushMatrix();
            gl.glColor4f(0, 1, 0, .3f);
            gl.glRectf(-10, 4, -5, 4 + (barsHeight * inhibitionValue));
            gl.glColor4f(1, 0, 0, .3f);
            gl.glRectf(-20, 4, -15, 4 + (barsHeight * excitationArray[getShowXcoord()][getShowYcoord()]));
            gl.glPopMatrix();
            gl.glPushMatrix();
            renderer.begin3DRendering();
            renderer.setColor(0, 1, 0, .3f);
            renderer.draw3D("sur", -10, 0, 0, .4f);
            renderer.setColor(1, 0, 0, .3f);
            renderer.draw3D("cen", -20, 0, 0, .4f);
            renderer.end3DRendering();
            subunits.render(gl);
            gl.glPopMatrix();
        }

        gl.glPushMatrix();

        renderer.begin3DRendering();

        renderer.setColor(1, 1, 0, .3f);
        renderer.draw3D("OMCshow( " + getShowXcoord() + " , " + getShowYcoord() + " )", -80, 30, 0, .4f); // x y width height

        renderer.setColor(1, 1, 0, .3f);
        renderer.draw3D("Avg Event Rate: " + eventRateFilter.getFilteredEventRate() + " Ev/s", -80, 40, 0, .4f); // x y width height

        renderer.setColor(1, 1, 0, .3f);
        renderer.draw3D("OMCex: " + excitationArray[getShowXcoord()][getShowYcoord()], -80, 50, 0, .4f); // x y width height

        renderer.setColor(1, 1, 0, .3f);
        renderer.draw3D("OMCini: " + inhibitionValue, -80, 60, 0, .4f); // x y width height

        renderer.setColor(1, 1, 0, .3f);
        renderer.draw3D("OMCmem: " + membraneStateArray[getShowXcoord()][getShowYcoord()], -80, 70, 0, .4f); // x y width height

        renderer.setColor(1, 1, 0, .3f);
        renderer.draw3D("IF Threshold: " + IFthreshold, -80, 80, 0, .4f); // x y width height

        renderer.end3DRendering();

        // render all the subunits now
        gl.glPopMatrix();
        gl.glPopMatrix();
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Reset subunits method ---------------------------------------------------//
//----------------------------------------------------------------------------//
    @Override
    public void resetFilter() {
        this.nxmax = chip.getSizeX() >> subsample;
        this.nymax = chip.getSizeY() >> subsample;

        this.nSpikesArray = new int[nxmax][nymax]; // deleted -1 in all
        this.spikeRateHz = new int[nxmax][nymax];
        this.netSynapticInputArray = new float[nxmax][nymax];
        this.membraneStateArray = new float[nxmax][nymax];
        this.excitationArray = new float[nxmax][nymax];
        this.timeStampArray = new int[nxmax][nymax];
        this.lastTimeStampArray = new int[nxmax][nymax];
        this.dtUSarray = new int[nxmax][nymax];
        this.timeStampSpikeArray = new int[nxmax][nymax];
        this.lastTimeStampSpikeArray = new int[nxmax][nymax];
        this.dtUSspikeArray = new int[nxmax][nymax];
        this.lastSpikedOMC = new int[2];
        this.lastSpikedOMCTracker1 = new int[2][2];
        this.lastSpikedOMCTracker1 = new int[2][2];
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                if (i == 0) {
                    lastSpikedOMCTracker1[i][j] = lastSpikedOMC[0];
                } else {
                    lastSpikedOMCTracker1[i][j] = lastSpikedOMC[1];
                }
            }
        }
        this.lastSpikedOMCArray = new int[2][2];
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                if (i == 0) {
                    lastSpikedOMCArray[i][j] = lastSpikedOMC[0];
                } else {
                    lastSpikedOMCArray[i][j] = lastSpikedOMC[1];
                }
            }
        }
        this.lastSpikedOMCTracker2 = new int[2][2];
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                if (i == 0) {
                    lastSpikedOMCTracker2[i][j] = lastSpikedOMC[0];
                } else {
                    lastSpikedOMCTracker2[i][j] = lastSpikedOMC[1];
                }
            }
        }
        subunits = new Subunits();
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Initialise filter method ------------------------------------------------//
//----------------------------------------------------------------------------//
    @Override
    public void initFilter() {
        resetFilter();
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//****************************************************************************//
//-- Subunits class ----------------------------------------------------------//
//----------------------------------------------------------------------------//
    // handles all subunits on and off
    private class Subunits {

        Subunit[][] subunits;
        int ntot;
        int lastUpdateTimestamp;
        FileOutputStream out; // declare a file output object
        PrintStream p; // declare a print stream object
//----------------------------------------------------------------------------//
//-- Reset Subunits method ---------------------------------------------------//
//----------------------------------------------------------------------------//

        public Subunits() {
            reset();
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Update events method ----------------------------------------------------//
//----------------------------------------------------------------------------//
        synchronized public void update(PolarityEvent e) {
            // subsample retina address to clump retina input pixel blocks.
            int x = e.x >> subsample, y = e.y >> subsample;
            if ((x < nxmax) && (y < nymax)) {
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
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Decay subunits method ---------------------------------------------------//
//----------------------------------------------------------------------------//
        void maybeDecayAll(BasicEvent e) {
            int dt = e.timestamp - lastUpdateTimestamp;
            if (dt < 0) {
                lastUpdateTimestamp = e.timestamp;
                return;
            }
            if (dt > minUpdateIntervalUs) {
                lastUpdateTimestamp = e.timestamp;
                //startTime = System.nanoTime();

                // update Neuron RGC
                for (int omcx = 0; omcx < (nymax - 1); omcx++) {
                    for (int omcy = 0; omcy < (nymax - 1); omcy++) {
                        membraneStateArray[omcx][omcy] *= 0.5;
                    }
                }

                // now update all subunits to RC decay activity toward zero
                for (int x = 0; x < nxmax; x++) {
                    for (int y = 0; y < nymax; y++) {
                        subunits[x][y].decayBy(0.5f);
                    }
                }
            }
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Inhibition Calculation method -------------------------------------------//
//----------------------------------------------------------------------------//
        float computeInhibitionToOutputCell() {
            // For all subunits, excluding the edge ones and the last ones (far right and bottom)
            // Find inhibition around center made of [omcx,omcy], [omcx+1,omcy+1], [omcx+1,omcy], [omcx,omcy+1]
            inhibitionValue = 0;
            for (int x = 0; x < (nxmax); x++) {
                for (int y = 0; y < (nymax); y++) {
                    // Multiply by 2
                    if ((2 * synapticWeight * subunits[x][y].computeInputToCell()) < Saturation) { // If squared subunit less than limit
                        inhibitionValue += synapticWeight * subunits[x][y].computeInputToCell() * 2;
                    } else {
                        inhibitionValue += Saturation;
                    }
                }
            }
            inhibitionValue /= ntot; // Divide by the number of subunits to normalise
            return inhibitionValue;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Excitation Calculation method -------------------------------------------//
//----------------------------------------------------------------------------//
        float computeExcitationToOutputCell(int omcx, int omcy) {
            // For all subunits, excluding the edge ones and the last ones (far right and bottom)
            // Select computation type
            excitationArray[omcx][omcy] = 0;

            // Average of 4 central cells
            for (int x = omcx; x <= (omcx + 1); x++) {
                for (int y = omcy; y <= (omcy + 1); y++) {
                    //if(Math.pow(subunits[x][y].computeInputToCell(),nonLinearityOrder) < Saturation){
                    //    excitationArray[omcx][omcy] += (float) Math.pow(subunits[x][y].computeInputToCell(),nonLinearityOrder);
                    //}
                    if ((2 * synapticWeight * subunits[x][y].computeInputToCell()) < Saturation) {
                        excitationArray[omcx][omcy] += synapticWeight * subunits[x][y].computeInputToCell() * 2;
                    } else {
                        excitationArray[omcx][omcy] += Saturation;
                    }
                }
            }
            excitationArray[omcx][omcy] /= 4;

            excitationArray[omcx][omcy] = centerExcitationToSurroundInhibitionRatio * excitationArray[omcx][omcy];
            return excitationArray[omcx][omcy];
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Reset method ------------------------------------------------------------//
//----------------------------------------------------------------------------//
        synchronized private void reset() {
            // Reset size
            ntot = (nxmax) * (nymax);
            subunits = new Subunit[nxmax][nymax];
            for (int x = 0; x < nxmax; x++) {
                for (int y = 0; y < nymax; y++) {
                    subunits[x][y] = new Subunit(x, y, subunits);
                }
            }
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Rendering of subunits method --------------------------------------------//
//----------------------------------------------------------------------------//
        private void render(GL2 gl) {
            if (showSubunits && showSpecificOMCoutput && !showAllOMCoutputs) {
                final float alpha = .2f;
                glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
                int off = (1 << (subsample)) / 2;
                for (int x = 0; x < (nxmax); x++) {
                    for (int y = 0; y < (nymax); y++) {
                        gl.glPushMatrix();
                        gl.glTranslatef((x << subsample) + off, (y << subsample) + off, 5);
                        if (((x == getShowXcoord()) && (y == getShowYcoord())) || ((x == (getShowXcoord() + 1)) && (y == (getShowYcoord() + 1)))
                                || ((x == getShowXcoord()) && (y == (getShowYcoord() + 1))) || ((x == (getShowXcoord() + 1)) && (y == getShowYcoord()))) {
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
    }
//-- end of Subunits class ---------------------------------------------------//
//****************************************************************************//

//****************************************************************************//
//-- Subunit class (to model single subunit and deal with vmem) --------------//
//----------------------------------------------------------------------------//
// polarity is ignored here and only handled on update of object Motion cell
    private class Subunit {

        float vmem;
        int x, y;
        Subunit[][] mySubunits;

//----------------------------------------------------------------------------//
//-- Constructor method ------------------------------------------------------//
//----------------------------------------------------------------------------//
        public Subunit(int x, int y, Subunit[][] mySubunits) {
            this.x = x;
            this.y = y;
            this.mySubunits = mySubunits;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Decay method ------------------------------------------------------------//
//----------------------------------------------------------------------------//
        public void decayBy(float factor) {
            vmem *= factor;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Update positive events method -------------------------------------------//
//----------------------------------------------------------------------------//
        public void updatepos(PolarityEvent e) {
            vmem += vmemIncrease;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Update negative events method -------------------------------------------//
//----------------------------------------------------------------------------//
        public void updateneg(PolarityEvent e) {
            vmem -= vmemIncrease;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Compute input to cell method --------------------------------------------//
//----------------------------------------------------------------------------//
        // subunit input is pure rectification
        public float computeInputToCell() {
            return vmem;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
    }
//-- end Subunit class -------------------------------------------------------//
//****************************************************************************//

//****************************************************************************//
//-- class OmcodFpgaModel (OMC model) --------------------------------------------//
//----------------------------------------------------------------------------//
// models soma and integration and spiking of objectMotion cell
    private class OmcodFpgaModel {

        Random r = new Random();
        private final LowpassFilter isiFilter = new LowpassFilter(300);
        private boolean initialized = false;
        boolean result = false;

//----------------------------------------------------------------------------//
//-- Update to check firing method -------------------------------------------//
//----------------------------------------------------------------------------//
        synchronized private boolean update(int timestamp) {
            // compute subunit input to us
            float inhibition = subunits.computeInhibitionToOutputCell();
            for (int omcx = 1; omcx < (nxmax - 1); omcx += 2) {// 4 corners
                for (int omcy = 1; omcy < (nymax - 1); omcy += 2) {
                    timeStampArray[omcx][omcy] = timestamp;
                    netSynapticInputArray[omcx][omcy] = (subunits.computeExcitationToOutputCell(omcx, omcy) - inhibition);
                    dtUSarray[omcx][omcy] = timeStampArray[omcx][omcy] - lastTimeStampArray[omcx][omcy];
                    if (dtUSarray[omcx][omcy] < 0) {
                        dtUSarray[omcx][omcy] = 0; // to handle negative dt
                    }
                    lastTimeStampArray[omcx][omcy] = timeStampArray[omcx][omcy];
                    membraneStateArray[omcx][omcy] += netSynapticInputArray[omcx][omcy] * dtUSarray[omcx][omcy] * 1e-6f;
                    if (eventRateFilter.getFilteredEventRate() > 100000) {
                        IFthreshold = integrateAndFireThreshold + increaseInThreshold;
                    } else if (eventRateFilter.getFilteredEventRate() < 500) {
                        IFthreshold = 10000; //Just very high if only noise is present
                    } else {
                        IFthreshold = integrateAndFireThreshold;
                    }
                    if (membraneStateArray[omcx][omcy] > IFthreshold) {
                        spike(timeStampArray[omcx][omcy], omcx, omcy);
                        membraneStateArray[omcx][omcy] = 0;
                        result = true;
                    } else if (membraneStateArray[omcx][omcy] < -10) {
                        membraneStateArray[omcx][omcy] = 0;
                        result = false;
                    } else {
                        result = false;
                    }

                }
            }
            //for central subunit, nasty coding!
            timeStampArray[3][3] = timestamp;
            netSynapticInputArray[3][3] = (subunits.computeExcitationToOutputCell(3, 3) - inhibition);
            dtUSarray[3][3] = timeStampArray[3][3] - lastTimeStampArray[3][3];
            if (dtUSarray[3][3] < 0) {
                dtUSarray[3][3] = 0; // to handle negative dt
            }
            lastTimeStampArray[3][3] = timeStampArray[3][3];
            membraneStateArray[3][3] += netSynapticInputArray[3][3] * dtUSarray[3][3] * 1e-6f;
            if (eventRateFilter.getFilteredEventRate() > 100000) {
                IFthreshold = integrateAndFireThreshold + increaseInThreshold;
            } else if (eventRateFilter.getFilteredEventRate() < 400) {
                IFthreshold = 10000; //Just very high if only noise is present
            } else {
                IFthreshold = integrateAndFireThreshold;
            }
            if (membraneStateArray[3][3] > IFthreshold) {
                spike(timeStampArray[3][3], 3, 3);
                membraneStateArray[3][3] = 0;
                result = true;
            } else if (membraneStateArray[3][3] < -10) {
                membraneStateArray[3][3] = 0;
                result = false;
            } else {
                result = false;
            }
            //endTime = System.nanoTime();
            //System.out.println(endTime - startTime);
            // end nasty coding!!
            return result;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Spike method ------------------------------------------------------------//
//----------------------------------------------------------------------------//
        void spike(int timestamp, int omcx, int omcy) {
            timeStampSpikeArray[omcx][omcy] = timestamp;
            enableSpikeDraw = true;
            lastSpikedOMC[0] = omcx;
            lastSpikedOMC[1] = omcy;
            if (enableSpikeSound) {
                if ((omcx == getShowXcoord()) && (omcy == getShowYcoord())) {
                    spikeSound.play();
                }
            }
            nSpikesArray[omcx][omcy]++;
            dtUSspikeArray[omcx][omcy] = timeStampSpikeArray[omcx][omcy] - lastTimeStampSpikeArray[omcx][omcy];
            if (initialized && (dtUSspikeArray[omcx][omcy] >= 0)) {
                float avgIsiUs = isiFilter.filter(dtUSspikeArray[omcx][omcy], timeStampSpikeArray[omcx][omcy]);
                spikeRateHz[omcx][omcy] = Math.round(1e6f / avgIsiUs);
            } else {
                initialized = true;
            }
            lastTimeStampSpikeArray[omcx][omcy] = timeStampSpikeArray[omcx][omcy];
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Reset method ------------------------------------------------------------//
//----------------------------------------------------------------------------//
        void reset() {
            for (int omcx = 0; omcx < (nymax - 1); omcx++) {
                for (int omcy = 0; omcy < (nymax - 1); omcy++) {
                    membraneStateArray[omcx][omcy] = 0;
                    netSynapticInputArray[omcx][omcy] = 0;
                    excitationArray[omcx][omcy] = 0;
                    nSpikesArray[omcx][omcy] = 0;
                    spikeRateHz[omcx][omcy] = 0;
                }
            }
            isiFilter.reset();
            inhibitionValue = 0;
            //medianCMFilter.reset();
            initialized = false;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Reset spike count method ------------------------------------------------//
//----------------------------------------------------------------------------//
        private void resetSpikeCount() {
            for (int omcx = 0; omcx < (nymax - 1); omcx++) {
                for (int omcy = 0; omcy < (nymax - 1); omcy++) {
                    nSpikesArray[omcx][omcy] = 0;
                }
            }
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
    }
//-- end class OmcodFpgaModel-------------------------------------------------//
//****************************************************************************//

//----------------------------------------------------------------------------//
//-- Set and Get methods -----------------------------------------------------//
//----------------------------------------------------------------------------//
    // @return the subunitActivityBlobRadiusScale
    public float getSubunitActivityBlobRadiusScale() {
        return subunitActivityBlobRadiusScale;
    }

    // @param subunitActivityBlobRadiusScale the subunitActivityBlobRadiusScale
    public void setSubunitActivityBlobRadiusScale(float subunitActivityBlobRadiusScale) {
        this.subunitActivityBlobRadiusScale = subunitActivityBlobRadiusScale;
        putFloat("subunitActivityBlobRadiusScale", subunitActivityBlobRadiusScale);
    }
//------------------------------------------------------------------------------
    // @return the integrateAndFireThreshold

    public float getIntegrateAndFireThreshold() {
        return integrateAndFireThreshold;
    }

    // @param integrateAndFireThreshold the integrateAndFireThreshold to set
    public void setIntegrateAndFireThreshold(float integrateAndFireThreshold) {
        this.integrateAndFireThreshold = integrateAndFireThreshold;
        putFloat("integrateAndFireThreshold", integrateAndFireThreshold);
    }
//------------------------------------------------------------------------------
    // @return the increaseInThreshold

    public float getIncreaseInThreshold() {
        return increaseInThreshold;
    }

    // @param increaseInThreshold the increaseInThreshold to set
    public void setIncreaseInThreshold(float increaseInThreshold) {
        this.increaseInThreshold = increaseInThreshold;
        putFloat("increaseInThreshold", increaseInThreshold);
    }
//------------------------------------------------------------------------------
    // @return the showXcoord

    public int getShowXcoord() {
        return showXcoord;
    }

    // @param showXcoord the showXcoord to set
    public void setShowXcoord(int showXcoord) {
        this.showXcoord = showXcoord;
        putInt("showXcoord", showXcoord);
        resetFilter();
        OmcodFpgaModel.reset();
    }
//------------------------------------------------------------------------------
    // @return the showYcoord

    public int getShowYcoord() {
        return showYcoord;
    }

    // @param showYcoord the showYcoord to set
    public void setShowYcoord(int showYcoord) {
        this.showYcoord = showYcoord;
        putInt("showYcoord", showYcoord);
        resetFilter();
        OmcodFpgaModel.reset();
    }
//------------------------------------------------------------------------------
    // @return the synapticWeight

    public float getSynapticWeight() {
        return synapticWeight;
    }

    // @param synapticWeight the synapticWeight to set
    public void setSynapticWeight(float synapticWeight) {
        this.synapticWeight = synapticWeight;
        putFloat("synapticWeight", synapticWeight);
    }
//------------------------------------------------------------------------------
    // @return the vmemIncrease

    public float getVmemIncrease() {
        return vmemIncrease;
    }

    // @param vmemIncrease the vmemIncrease to set
    public void setVmemIncrease(float vmemIncrease) {
        this.vmemIncrease = vmemIncrease;
        putFloat("vmemIncrease", vmemIncrease);
    }
//------------------------------------------------------------------------------
    // @return the barsHeight

    public float getBarsHeight() {
        return barsHeight;
    }

    // @param barsHeight the barsHeight to set
    public void setBarsHeight(float barsHeight) {
        this.barsHeight = barsHeight;
        putFloat("barsHeight", barsHeight);
    }
//------------------------------------------------------------------------------
    // @return the onOffWeightRatio

    public float getCenterExcitationToSurroundInhibitionRatio() {
        return centerExcitationToSurroundInhibitionRatio;
    }

    // @param onOffWeightRatio the onOffWeightRatio to set
    public void setCenterExcitationToSurroundInhibitionRatio(float onOffWeightRatio) {
        this.centerExcitationToSurroundInhibitionRatio = onOffWeightRatio;
        putFloat("centerExcitationToSurroundInhibitionRatio", onOffWeightRatio);
    }
//------------------------------------------------------------------------------
    // @return the Saturation

    public int getSaturation() {
        return Saturation;
    }

    // @param Saturation the Saturation to set
    public void setSaturation(int Saturation) {
        this.Saturation = Saturation;
        putInt("Saturation", Saturation);
    }
//------------------------------------------------------------------------------
    // @return the WaitingTimeRstMs

    public int getWaitingTimeRstMs() {
        return waitingTimeRstMs;
    }

    // @param WaitingTimeRstMs the WaitingTimeRstMs to set
    public void setWaitingTimeRstMs(int waitingTimeRstMs) {
        this.waitingTimeRstMs = waitingTimeRstMs;
        putInt("waitingTimeRstMs", waitingTimeRstMs);
    }
//------------------------------------------------------------------------------
    // @return the showSpecificOMCoutput

    public boolean isShowSpecificOMCoutput() {
        return showSpecificOMCoutput;
    }

    // @param showSpecificOMCoutput the showSpecificOMCoutput to set
    public void setShowSpecificOMCoutput(boolean showSpecificOMCoutput) {
        this.showSpecificOMCoutput = showSpecificOMCoutput;
        putBoolean("showSpecificOMCoutput", showSpecificOMCoutput);
    }
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
    // @return the showAllOMCoutputs

    public boolean isShowAllOMCoutputs() {
        return showAllOMCoutputs;
    }

    // @param showAllOMCoutputs the showAllOMCoutputs to set
    public void setShowAllOMCoutputs(boolean showAllOMCoutputs) {
        this.showAllOMCoutputs = showAllOMCoutputs;
        putBoolean("showAllOMCoutputs", showAllOMCoutputs);
    }
//------------------------------------------------------------------------------
//-- Class OmcodFpga -------------------------------------------------------------//
//****************************************************************************//
}
