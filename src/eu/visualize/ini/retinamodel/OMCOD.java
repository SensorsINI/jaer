//****************************************************************************//
//-- Packages ----------------------------------------------------------------//
//----------------------------------------------------------------------------//
package eu.visualize.ini.retinamodel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Observer;
import java.util.Random;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter;
//-- end packages ------------------------------------------------------------//
//****************************************************************************//


//****************************************************************************//
//-- Description -------------------------------------------------------------//
//----------------------------------------------------------------------------//
// Models multiple object motion cells that are excited by on or off activity
// within their classical receptive field but are inhibited by synchronous on or
// off activity in their extended RF, such as that caused by a saccadic eye
// movement. Also gives direction of movement of object
// @author diederik
//-- end description ---------------------------------------------------------//
//****************************************************************************//


@Description("Models object motion cells known from mouse and salamander retina")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)


//****************************************************************************//
//-- Main class OMCOD --------------------------------------------------------//
//----------------------------------------------------------------------------//
public class OMCOD extends AbstractRetinaModelCell implements FrameAnnotater, Observer {
    private final OMCODModel OMCODModel = new OMCODModel();
    private Subunits subunits;
    private int nxmax;
    private int nymax;
    private boolean enableSpikeDraw;
    private float[][] inhibitionArray;
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
//------------------------------------------------------------------------------    
    private float synapticWeight = getFloat("synapticWeight", 1f);
    private float centerExcitationToSurroundInhibitionRatio = getFloat("centerExcitationToSurroundInhibitionRatio", 0.4386f);
    private boolean surroundSuppressionEnabled = getBoolean("surroundSuppressionEnabled", false);
    private float subunitActivityBlobRadiusScale = getFloat("subunitActivityBlobRadiusScale", 0.004f);
    private float integrateAndFireThreshold = getFloat("integrateAndFireThreshold", 1f);
    private float nonLinearityOrder = getFloat("nonLinearityOrder", 2f);
    private boolean startLogging = getBoolean("startLogging", false);
    private boolean deleteLogging = getBoolean("deleteLogging", false);
    private float barsHeight = getFloat("barsHeight", 0.000020f);
    private int excludedEdgeSubunits = getInt("excludedEdgeSubunits", 0);
    private int showXcoord = getInt("showXcoord", 1);
    private int showYcoord = getInt("showYcoord", 1);
    private int tanhSaturation = getInt("tanhSaturation", 1);
    private boolean exponentialToTanh = getBoolean("exponentialToTanh", false);
//------------------------------------------------------------------------------
    
//----------------------------------------------------------------------------//
//-- Initialise and ToolTip method -------------------------------------------//
//----------------------------------------------------------------------------//
    public OMCOD(AEChip chip) {
        super(chip); 
        this.enableSpikeDraw = false;
        this.nxmax = chip.getSizeX() >> getSubunitSubsamplingBits();
        this.nymax = chip.getSizeY() >> getSubunitSubsamplingBits();
        this.nSpikesArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()]; // deleted -1 in all
        this.netSynapticInputArray = new float [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.membraneStateArray = new float [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.inhibitionArray = new float [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.excitationArray = new float [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.timeStampArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.lastTimeStampArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.dtUSarray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.timeStampSpikeArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.lastTimeStampSpikeArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.dtUSspikeArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        chip.addObserver(this);
//------------------------------------------------------------------------------
        setPropertyTooltip("showSubunits", "Enables showing subunit activity "
                + "annotation over retina output");
        setPropertyTooltip("showOutputCell", "Enables showing object motion cell "
                + "activity annotation over retina output");
        setPropertyTooltip("subunitSubsamplingBits", "Each subunit integrates "
                + "events from 2^n by 2^n pixels, where n=subunitSubsamplingBits");
        setPropertyTooltip("synapticWeight", "Subunit activity inputs to the "
                + "objectMotion neuron are weighted this much; use to adjust "
                + "response magnitude");
        setPropertyTooltip("subunitDecayTimeconstantMs", "Subunit activity "
                + "decays with this time constant in ms");
        setPropertyTooltip("enableSpikeSound", "Enables audio spike output from "
                + "objectMotion cell");
        setPropertyTooltip("maxSpikeRateHz", "Maximum spike rate of objectMotion "
                + "cell in Hz");
        setPropertyTooltip("centerExcitationToSurroundInhibitionRatio", 
                "Inhibitory ON subunits are weighted by factor more than "
                        + "excitatory OFF subunit activity to the object motion "
                        + "cell");
        setPropertyTooltip("minUpdateIntervalUs", "subunits activities are "
                + "decayed to zero at least this often in us, even if they "
                + "receive no input");
        setPropertyTooltip("surroundSuppressionEnabled", "subunits are "
                + "suppressed by surrounding activity of same type; reduces "
                + "response to global dimming");
        setPropertyTooltip("subunitActivityBlobRadiusScale", "The blobs "
                + "represeting subunit activation are scaled by this factor");
        setPropertyTooltip("integrateAndFireThreshold", "The ganglion cell will "
                + "fire if the difference between excitation and inhibition "
                + "overcomes this threshold");
        setPropertyTooltip("poissonFiringEnabled", "The ganglion cell fires "
                + "according to Poisson rate model for net synaptic input");
        setPropertyTooltip("nonLinearityOrder", "The non-linear order of the "
                + "subunits' value before the total sum");
        setPropertyTooltip("startLogging", "Start logging inhibition and "
                + "excitation");
        setPropertyTooltip("deleteLogging", "Delete the logging of inhibition "
                + "and excitation");
        setPropertyTooltip("barsHeight", "set the magnitute of cen and sur if "
                + "the inhibition and excitation are out of range");
        setPropertyTooltip("excludedEdgeSubunits", "Set the number of subunits "
                + "excluded from computation at the edge");
        setPropertyTooltip("tanhSaturation", "Set the maximum contribution of "
                + "a single subunit, where it saturates");
        setPropertyTooltip("exponentialToTanh", "Switch from exponential "
                + "non-linearity to exponential tangent");
        setPropertyTooltip("showXcoord", "decide which Object Motion Cell to "
                + "show by selecting the X coordinate of the center");
        setPropertyTooltip("showYcoord", "decide which Object Motion Cell to "
                + "show by selecting the Y coordinate of the center");
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
    
    private int lastOMCODSpikeCheckTimestamp = 0;

//----------------------------------------------------------------------------//
//-- Filter packet method ----------------------------------------------------//
//----------------------------------------------------------------------------//
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!(in.getEventPrototype() instanceof PolarityEvent)) {
            return in;
        }
        if (in instanceof ApsDvsEventPacket) {
            checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak. 
            // we don't use output packet but it is necesary to iterate over DVS events only
        }
        clearOutputPacket();
        if (subunits == null) {
            resetFilter();
        }
        for (Object o : in) {
            PolarityEvent e = (PolarityEvent) o;
            if (e.special) {
                continue;
            }
            subunits.update(e);
            int dt = e.timestamp - lastOMCODSpikeCheckTimestamp;
            if (dt < 0) {
                lastOMCODSpikeCheckTimestamp = e.timestamp;
                return in;
            }
            if (dt > minUpdateIntervalUs) {
                lastOMCODSpikeCheckTimestamp = e.timestamp;
                OMCODModel.update(e.timestamp);
            }
        }

        return in;
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
    
//----------------------------------------------------------------------------//
//-- Draw bars method --------------------------------------------------------//
//----------------------------------------------------------------------------//
    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        if ((getShowXcoord()<getExcludedEdgeSubunits()) || (getShowYcoord()<getExcludedEdgeSubunits()) 
                || (getShowXcoord()>nxmax-1-getExcludedEdgeSubunits()) || (getShowYcoord()>nymax-1-getExcludedEdgeSubunits())){
            setShowXcoord(excludedEdgeSubunits);
            setShowYcoord(excludedEdgeSubunits);
            gl.glTranslatef((getShowXcoord()+1) << getSubunitSubsamplingBits(), (getShowYcoord()+1) << getSubunitSubsamplingBits(), 5);
        }
        if (showOutputCell && (nSpikesArray[getShowXcoord()][getShowYcoord()]!=0)) {                
            gl.glColor4f(1, 1, 1, .2f);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            float radius = (chip.getMaxSize() * OMCODModel.spikeRateHz) / maxSpikeRateHz / 2;
            glu.gluDisk(quad, 0, radius, 32, 1);
            gl.glPopMatrix();
            OMCODModel.resetSpikeCount();
        }
        if(showOutputCell == false) {
            for(int omcx=getExcludedEdgeSubunits();omcx<(nxmax-1-getExcludedEdgeSubunits());omcx++) {
                for(int omcy=getExcludedEdgeSubunits();omcy<(nymax-1-getExcludedEdgeSubunits());omcy++) {
                    if (enableSpikeDraw||nSpikesArray[omcx][omcy]!=0) {                
                        gl.glColor4f(12, 0, 1, .3f);
                        gl.glRectf((omcx << getSubunitSubsamplingBits()), (omcy << getSubunitSubsamplingBits()), 
                                (omcx+2 << getSubunitSubsamplingBits()), (omcy+2 << getSubunitSubsamplingBits()));
                        renderer.setColor(12, 0, 1, .3f);
                        renderer.draw3D("OMC( "+omcx+" , "+omcy+" )", -45, 40, 0, .4f);
                        enableSpikeDraw = false;
                    }
                }
            }
            OMCODModel.resetSpikeCount();
        }
        if (showSubunits) {
            gl.glColor4f(0, 1, 0, .3f);
            gl.glRectf(-10, 4, -5, 4+barsHeight*inhibitionArray[getShowXcoord()][getShowYcoord()]);
            gl.glColor4f(1, 0, 0, .3f);
            gl.glRectf(-20, 4, -15, 4+barsHeight*excitationArray[getShowXcoord()][getShowYcoord()]);
            renderer.begin3DRendering();
            renderer.setColor(0, 1, 0, .3f);
            renderer.draw3D("sur", -10, 0, 0, .4f);
            renderer.setColor(1, 0, 0, .3f);
            renderer.draw3D("cen", -20, 0, 0, .4f);
            renderer.setColor(1, 1, 0, .3f);
            renderer.draw3D("OMCshow( "+getShowXcoord()+" , "+getShowYcoord()+" )", -55, 30, 0, .4f); // x y width height
            renderer.end3DRendering();
            // render all the subunits now
            subunits.render(gl);
        }
    }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//    
    
//----------------------------------------------------------------------------//
//-- Reset subunits method ---------------------------------------------------//
//----------------------------------------------------------------------------//
    @Override
    public void resetFilter() {
        this.nxmax = chip.getSizeX() >> getSubunitSubsamplingBits();
        this.nymax = chip.getSizeY() >> getSubunitSubsamplingBits();
        
        this.nSpikesArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()]; // deleted -1 in all
        this.netSynapticInputArray = new float [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.membraneStateArray = new float [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.inhibitionArray = new float [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.excitationArray = new float [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.timeStampArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.lastTimeStampArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.dtUSarray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.timeStampSpikeArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.lastTimeStampSpikeArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
        this.dtUSspikeArray = new int [nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
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
            int x = e.x >> getSubunitSubsamplingBits(), y = e.y >> getSubunitSubsamplingBits();
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
                // now update all subunits to RC decay activity toward zero
                float decayFactor = (float) Math.exp(-dt / (1000 * subunitDecayTimeconstantMs));
                for (int x = 0; x < nxmax; x++) {
                    for (int y = 0; y < nymax; y++) {
                        subunits[x][y].decayBy(decayFactor);
                    }
                }
            }
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
        
//----------------------------------------------------------------------------//
//-- Inhibition Calculation method -------------------------------------------//
//----------------------------------------------------------------------------//        
        float computeInhibitionToOutputCell(int omcx, int omcy) {
            // For all subunits, excluding the edge ones and the last ones (far right and bottom)
                    // Find inhibition around center made of [omcx,omcy], [omcx+1,omcy+1], [omcx+1,omcy], [omcx,omcy+1]
                    for (int x = getExcludedEdgeSubunits(); x < (nxmax-getExcludedEdgeSubunits()); x++) {
                        for (int y = getExcludedEdgeSubunits(); y < (nymax-getExcludedEdgeSubunits()); y++) {
//------------------------------------------------------------------------------
                            // Select computation type
                            if(!exponentialToTanh){// Use non-linear model (given the nonlinearity order)
                                if (((x != omcx) && (y != omcy)) || ((x != omcx+1) && (y != omcy+1)) 
                                        || ((x != omcx) && (y != omcy+1)) || ((x != omcx+1) && (y != omcy))) {
                                    inhibitionArray[omcx][omcy] += (float) Math.pow(subunits[x][y].computeInputToCell(),nonLinearityOrder);
                                }
                            }
                            else{ // Use tanh model (given saturation value): preferred method
                                if (((x != omcx) && (y != omcy)) || ((x != omcx+1) && (y != omcy+1)) 
                                        || ((x != omcx) && (y != omcy+1)) || ((x != omcx+1) && (y != omcy))) {
                                    inhibitionArray[omcx][omcy] += tanhSaturation*Math.tanh(subunits[x][y].computeInputToCell());
                                }                               
                            }
//------------------------------------------------------------------------------
                        }
                    }
//------------------------------------------------------------------------------
                    inhibitionArray[omcx][omcy] /= (ntot - 4); // Divide by the number of subunits to normalise
                    inhibitionArray[omcx][omcy] = synapticWeight * inhibitionArray[omcx][omcy]; // Give a synaptic weight (a simple scalar value)
//------------------------------------------------------------------------------
                    // Log inhibitionArray
                    if (startLogging == true){ 
                        try {
                            if(omcx==0 && omcy == 0){
                                // Create a new file output stream
                                FileOutputStream out = new FileOutputStream(new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\inhibitionArray1.txt"),true);
                               // Connect print stream to the output stream
                                p = new PrintStream(out);
                                p.print(inhibitionArray[omcx][omcy]);
                                p.print(", ");
                                p.print(omcx);
                                p.print(", ");
                                p.print(omcy);
                                p.print(", ");
                                p.println(lastUpdateTimestamp);
                                p.close();
                            }
                        } 
                        catch (Exception e) {
                            System.err.println("Error writing to file");
                    }
                } // Delete inhibitionArray
                if (deleteLogging == true){
                    File fout = new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\inhibitionArray.txt");
                    fout.delete();
                }
        return inhibitionArray[omcx][omcy];
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//        

//----------------------------------------------------------------------------//
//-- Excitation Calculation method -------------------------------------------//
//----------------------------------------------------------------------------//
        float computeExcitationToOutputCell(int omcx, int omcy) {
            // For all subunits, excluding the edge ones and the last ones (far right and bottom)
                    // Select computation type
                    if(!exponentialToTanh){// Use non-linear model (given the nonlinearity order)
                    // Average of 4 central cells
                        excitationArray[omcx][omcy] = (float)
                                ((centerExcitationToSurroundInhibitionRatio*Math.pow(synapticWeight * subunits[omcx][omcy].computeInputToCell(),nonLinearityOrder))+
                        (centerExcitationToSurroundInhibitionRatio*Math.pow(synapticWeight * subunits[omcx+1][omcy+1].computeInputToCell(),nonLinearityOrder))+
                        (centerExcitationToSurroundInhibitionRatio*Math.pow(synapticWeight * subunits[omcx+1][omcy].computeInputToCell(),nonLinearityOrder))+
                        (centerExcitationToSurroundInhibitionRatio*Math.pow(synapticWeight * subunits[omcx][omcy+1].computeInputToCell(),nonLinearityOrder)))/4;
                    } // Ignore surround
                    else{ // Use tanh model (given saturation value): preferred method
                        // Average of 4 central cells
                        excitationArray[omcx][omcy] = (float)
                                ((tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[omcx][omcy].computeInputToCell()))) + 
                        (tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[omcx+1][omcy+1].computeInputToCell()))) + 
                        (tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[omcx+1][omcy].computeInputToCell()))) + 
                        (tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[omcx][omcy+1].computeInputToCell())))) / 4;
                    } // Ignore surround            
//------------------------------------------------------------------------------
                    // Log excitationArray
                    if(startLogging == true){
                        try {
                            if(omcx==0 && omcy == 0){
                                // Create a new file output stream
                                FileOutputStream out = new FileOutputStream(new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\excitationArray1.txt"),true);
                               // Connect print stream to the output stream
                                p = new PrintStream(out);
                                p.print(excitationArray[omcx][omcy]);
                                p.print(", ");
                                p.print(omcx);
                                p.print(", ");
                                p.print(omcy);
                                p.print(", ");
                                p.println(lastUpdateTimestamp);
                                p.close();
                            }
                        } 
                        catch (Exception e) {
                            System.err.println("Error writing to file");
                        }
                    }
                    if (deleteLogging == true){
                        File fout = new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\excitationArray.txt");
                        fout.delete();
                    }
            return excitationArray[omcx][omcy];
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Reset method ------------------------------------------------------------//
//----------------------------------------------------------------------------//
        synchronized private void reset() {
            // Reset size
            ntot = (nxmax-getExcludedEdgeSubunits()) * (nymax-getExcludedEdgeSubunits());
            subunits = new Subunit[nxmax-2*getExcludedEdgeSubunits()][nymax-2*getExcludedEdgeSubunits()];
            for (int x = getExcludedEdgeSubunits(); x < nxmax-getExcludedEdgeSubunits(); x++) {
                for (int y = getExcludedEdgeSubunits(); y < nymax-getExcludedEdgeSubunits(); y++) {
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
            final float alpha = .2f;
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            int off = (1 << (getSubunitSubsamplingBits())) / 2;
            for (int x = getExcludedEdgeSubunits(); x < (nxmax-getExcludedEdgeSubunits()); x++) {
                for (int y = getExcludedEdgeSubunits(); y < (nymax-getExcludedEdgeSubunits()); y++) {
                    gl.glPushMatrix();
                    gl.glTranslatef((x << getSubunitSubsamplingBits()) + off, (y << getSubunitSubsamplingBits()) + off, 5);
                    if (((x == getShowXcoord()) && (y == getShowYcoord())) || ((x == getShowXcoord()+1) && (y == getShowYcoord()+1)) 
                            || ((x == getShowXcoord()) && (y == getShowYcoord()+1)) || ((x == getShowXcoord()+1) && (y == getShowYcoord()))) {
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
            vmem = vmem + 1;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Update negative events method -------------------------------------------//
//----------------------------------------------------------------------------//
        public void updateneg (PolarityEvent e) {
            vmem = vmem - 1;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
        
//----------------------------------------------------------------------------//
//-- Compute input to cell method --------------------------------------------//
//----------------------------------------------------------------------------//        
        // subunit input is pure rectification
        public float computeInputToCell() {
            if (!surroundSuppressionEnabled) {
                return vmem;
            } 
            else { 
                // Here we return the half-rectified local difference between ourselves and our neighbors
                int n = 0;
                float sum = 0;
                if ((x + 1) < nxmax) {
                    sum += mySubunits[x + 1][y].vmem;
                    n++;
                }
                if ((x - 1) >= 0) {
                    sum += mySubunits[x - 1][y].vmem;
                    n++;
                }
                if ((y + 1) < nymax) {
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
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
    }
//-- end Subunit class -------------------------------------------------------//
//****************************************************************************//
    
    
//****************************************************************************//
//-- class OMCODModel (OMC model) --------------------------------------------//
//----------------------------------------------------------------------------//
    // models soma and integration and spiking of objectMotion cell
    private class OMCODModel {
        Random r = new Random();
        private final LowpassFilter isiFilter = new LowpassFilter(300);
        private boolean initialized = false;
        float spikeRateHz = 0;
        boolean result = false;
        
//----------------------------------------------------------------------------//
//-- Update to check firing method -------------------------------------------//
//----------------------------------------------------------------------------//
        synchronized private boolean update(int timestamp) {
            // compute subunit input to us
            for(int omcx=getExcludedEdgeSubunits();omcx<(nxmax-1-getExcludedEdgeSubunits());omcx++) {
                for(int omcy=getExcludedEdgeSubunits();omcy<(nymax-1-getExcludedEdgeSubunits());omcy++) {
                    timeStampArray[omcx][omcy]= timestamp;
                    netSynapticInputArray[omcx][omcy] = (subunits.computeExcitationToOutputCell(omcx, omcy) - subunits.computeInhibitionToOutputCell(omcx, omcy));
                    dtUSarray[omcx][omcy] = timeStampArray[omcx][omcy] - lastTimeStampArray[omcx][omcy];
                    if (dtUSarray[omcx][omcy] < 0) {
                        dtUSarray[omcx][omcy] = 0; // to handle negative dt
                    }
                    lastTimeStampArray[omcx][omcy] = timeStampArray[omcx][omcy];
                    if (poissonFiringEnabled) {
                        float spikeRate = netSynapticInputArray[omcx][omcy];
                        if (spikeRate < 0) {
                        result = false;
                        }
                        if (spikeRate > maxSpikeRateHz) {
                            spikeRate = maxSpikeRateHz;
                        }
                        if (r.nextFloat() < (spikeRate * 1e-6f * dtUSarray[omcx][omcy])) {
                            spike(timeStampArray[omcx][omcy], omcx, omcy);
                            result = true;
                        } 
                        else {
                            result = false;
                        }
                    } 
                    else { // IF neuron
                        membraneStateArray[omcx][omcy] += netSynapticInputArray[omcx][omcy] * dtUSarray[omcx][omcy] * 1e-6f;
                        if (membraneStateArray[omcx][omcy] > integrateAndFireThreshold) {
                            spike(timeStampArray[omcx][omcy], omcx, omcy);
                            membraneStateArray[omcx][omcy] = 0;
                            result = true;
                        } 
                        else if (membraneStateArray[omcx][omcy] < -10) {
                            membraneStateArray[omcx][omcy] = 0;
                            result = false;
                        } 
                        else {
                            result = false;
                        }
                    }
                }
            }
       return result;
       }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
        
//----------------------------------------------------------------------------//
//-- Spike method ------------------------------------------------------------//
//----------------------------------------------------------------------------//
     void spike(int timestamp, int omcx, int omcy) {
        timeStampSpikeArray[omcx][omcy]=timestamp;
        enableSpikeDraw= true;
        if (enableSpikeSound) {
            if(omcx == getShowXcoord() && omcy == getShowYcoord()){
                spikeSound.play();
            }
        }
        nSpikesArray[omcx][omcy]++;
        dtUSspikeArray[omcx][omcy] = timeStampSpikeArray[omcx][omcy] - lastTimeStampSpikeArray[omcx][omcy];
        if (initialized && (dtUSspikeArray[omcx][omcy]>=0)) {
            float avgIsiUs = isiFilter.filter(dtUSspikeArray[omcx][omcy], timeStampSpikeArray[omcx][omcy]);
            spikeRateHz = 1e6f / avgIsiUs;
        } 
        else {
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
            for(int omcx=getExcludedEdgeSubunits();omcx<(nymax-1-getExcludedEdgeSubunits());omcx++) {
                for(int omcy=getExcludedEdgeSubunits();omcy<(nymax-1-getExcludedEdgeSubunits());omcy++) {
                    membraneStateArray[omcx][omcy] = 0;
                }
            }
            for(int omcx=getExcludedEdgeSubunits();omcx<(nymax-1-getExcludedEdgeSubunits());omcx++) {
                for(int omcy=getExcludedEdgeSubunits();omcy<(nymax-1-getExcludedEdgeSubunits());omcy++) {
                    netSynapticInputArray[omcx][omcy] = 0;
                }
            }
            for(int omcx=getExcludedEdgeSubunits();omcx<(nymax-1-getExcludedEdgeSubunits());omcx++) {
                for(int omcy=getExcludedEdgeSubunits();omcy<(nymax-1-getExcludedEdgeSubunits());omcy++) {
                    inhibitionArray[omcx][omcy] = 0;
                }
            }
            for(int omcx=getExcludedEdgeSubunits();omcx<(nymax-1-getExcludedEdgeSubunits());omcx++) {
                for(int omcy=getExcludedEdgeSubunits();omcy<(nymax-1-getExcludedEdgeSubunits());omcy++) {
                    excitationArray[omcx][omcy] = 0;
                }
            }
            for(int omcx=getExcludedEdgeSubunits();omcx<(nymax-1-getExcludedEdgeSubunits());omcx++) {
                for(int omcy=getExcludedEdgeSubunits();omcy<(nymax-1-getExcludedEdgeSubunits());omcy++) {
                    nSpikesArray[omcx][omcy] = 0;
                }
            }
            isiFilter.reset();
            initialized=false;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
        
//----------------------------------------------------------------------------//
//-- Reset spike count method ------------------------------------------------//
//----------------------------------------------------------------------------//        
        private void resetSpikeCount() {
            for(int omcx=getExcludedEdgeSubunits();omcx<(nymax-1-getExcludedEdgeSubunits());omcx++) {
                for(int omcy=getExcludedEdgeSubunits();omcy<(nymax-1-getExcludedEdgeSubunits());omcy++) {
                    nSpikesArray[omcx][omcy] = 0;
                }
            }
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
    }
//-- end class OMCODModel-----------------------------------------------------//    
//****************************************************************************//    

    
//----------------------------------------------------------------------------//
//-- Set and Get methods -----------------------------------------------------//
//----------------------------------------------------------------------------//
    // @return the subunitDecayTimeconstantMs
    public float getSubunitDecayTimeconstantMs() {
        return subunitDecayTimeconstantMs;
    }
//------------------------------------------------------------------------------
    @Override
    public int getSubunitSubsamplingBits() {
        return subunitSubsamplingBits;
    }
    // @param subunitSubsamplingBits the subunitSubsamplingBits to set
    @Override
    public synchronized void setSubunitSubsamplingBits(int subunitSubsamplingBits) {
    	this.subunitSubsamplingBits = subunitSubsamplingBits;
	putInt("subunitSubsamplingBits", subunitSubsamplingBits);
	resetFilter();
        OMCODModel.reset();
    }
//------------------------------------------------------------------------------
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
    // @return the showXcoord
    public int getShowXcoord() {
        return showXcoord;
    }
    // @param showXcoord the showXcoord to set
    public void setShowXcoord(int showXcoord) {
        this.showXcoord = showXcoord;
        putInt("showXcoord", showXcoord);
        resetFilter();
        OMCODModel.reset();
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
        OMCODModel.reset();
    }
//------------------------------------------------------------------------------
    // @return the nonLinearityOrder
    public float getNonLinearityOrder() {
        return nonLinearityOrder;
    }
    // @param nonLinearityOrder the nonLinearityOrder to set
    public void setNonLinearityOrder(float nonLinearityOrder) {
        this.nonLinearityOrder = nonLinearityOrder;
        putFloat("nonLinearityOrder", nonLinearityOrder);
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
    // @return the excludedEdgeSubunits
    public int getExcludedEdgeSubunits() {
        return excludedEdgeSubunits;
    }
    // @param excludedEdgeSubunits the excludedEdgeSubunits to set
    public void setExcludedEdgeSubunits(int excludedEdgeSubunits) {
        this.excludedEdgeSubunits = excludedEdgeSubunits;
        putFloat("excludedEdgeSubunits", excludedEdgeSubunits);
        resetFilter();
        OMCODModel.reset();
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
    // @return the tanhSaturation
    public int getTanhSaturation() {
        return tanhSaturation;
    }
    // @param tanhSaturation the tanhSaturation to set
    public void setTanhSaturation(int tanhSaturation) {
        this.tanhSaturation = tanhSaturation;
        putInt("tanhSaturation", tanhSaturation);
    }
//------------------------------------------------------------------------------
    // @return the deleteLogging
    public boolean isDeleteLogging() {
        return deleteLogging;
    }
    // @param deleteLogging the deleteLogging to set
    public void setDeleteLogging(boolean deleteLogging) {
        this.deleteLogging = deleteLogging;
        putBoolean("deleteLogging", deleteLogging);
    }
//------------------------------------------------------------------------------
    // @return the exponentialToTanh
    public boolean isExponentialToTanh() {
        return exponentialToTanh;
    }
    // @param exponentialToTanh the exponentialToTanh to set
    public void setExponentialToTanh(boolean exponentialToTanh) {
        this.exponentialToTanh = exponentialToTanh;
        putBoolean("exponentialToTanh", exponentialToTanh);
    }
//------------------------------------------------------------------------------
    // return the startLogging
    public boolean isStartLogging() {
        return startLogging;
    }
    // @param startLogging the startLogging to set
    public void setStartLogging(boolean startLogging) {
        this.startLogging = startLogging;
        putBoolean("startLogging", startLogging);
    }
//------------------------------------------------------------------------------
    // @return the surroundSuppressionEnabled
    public boolean isSurroundSuppressionEnabled() {
        return surroundSuppressionEnabled;
    }
    // @param surroundSuppressionEnabled the surroundSuppressionEnabled to set
    public void setSurroundSuppressionEnabled(boolean surroundSuppressionEnabled) {
        this.surroundSuppressionEnabled = surroundSuppressionEnabled;
        putBoolean("surroundSuppressionEnabled", surroundSuppressionEnabled);
    }
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
}
//-- Class OMCOD -------------------------------------------------------------//
//****************************************************************************//