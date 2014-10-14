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


@Description("Models object motion cell known from mouse and salamander retina")
//@DevelopmentStatus(DevelopmentStatus.Status.Experimental)


//****************************************************************************//
//-- Main class OMCOD --------------------------------------------------------//
//----------------------------------------------------------------------------//
public class OMCOD extends AbstractRetinaModelCell implements FrameAnnotater, Observer {
    private final OMCODModel OMCODModel = new OMCODModel();
    private Subunits subunits;
    private final int nxmax;
    private final int nymax;
    private final float[][] inhibitionArray;
    private final float[][] excitationArray;
    private final float[][] membraneStateArray;
    private final float[][] netSynapticInputArray;
    private final int[][] nSpikesArray; // counts spikes since last rendering cycle
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
    private int excludedEdgeSubunits = getInt("excludedEdgeSubunits", 1);
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
        this.nxmax = chip.getSizeX() >> getSubunitSubsamplingBits();
        this.nymax = chip.getSizeY() >> getSubunitSubsamplingBits();
        this.nSpikesArray = new int [nxmax-1][nymax-1];
        this.netSynapticInputArray = new float [nxmax-1][nymax-1];
        this.membraneStateArray = new float [nxmax-1][nymax-1];
        this.inhibitionArray = new float [nxmax-1][nymax-1];
        this.excitationArray = new float [nxmax-1][nymax-1];
        chip.addObserver(this);
//------------------------------------------------------------------------------
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
        setPropertyTooltip("showXcoord", "decide which Object Motion Cell to show by selecting the X coordinate of the center");
        setPropertyTooltip("showYcoord", "decide which Object Motion Cell to show by selecting the Y coordinate of the center");
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
            checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak. we don't use output packet but it is necesary to iterate over DVS events only
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
//        System.out.println(String.format("spikeRate=%.1g \tonActivity=%.2f \toffActivity=%.1f", OMCODModel.spikeRate, inhibition, offExcitation));
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
        gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 10);
        if ((showXcoord<0) || (showYcoord<0) || (showXcoord>nxmax-1) || (showYcoord>nymax-1)){
            showXcoord = 1;
            showYcoord = 1;
        }
        if (showOutputCell && (nSpikesArray[showXcoord][showYcoord]!=0)) {
            gl.glColor4f(1, 1, 1, .2f);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            float radius = (chip.getMaxSize() * OMCODModel.spikeRateHz) / maxSpikeRateHz / 2;
            glu.gluDisk(quad, 0, radius, 32, 1);
            OMCODModel.resetSpikeCount();
        }
        gl.glPopMatrix();
        if (showSubunits) {
            gl.glColor4f(0, 1, 0, .3f);
            gl.glRectf(-10, 0, -5, barsHeight*inhibitionArray[showXcoord][showYcoord]);
            gl.glColor4f(1, 0, 0, .3f);
            gl.glRectf(-20, 0, -15, barsHeight*excitationArray[showXcoord][showYcoord]);
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
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//    
    
//----------------------------------------------------------------------------//
//-- Reset subunits method ---------------------------------------------------//
//----------------------------------------------------------------------------//
    @Override
    public void resetFilter() {
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
        int nx;
        int ny;
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
                for (int x = 0; x < nx; x++) {
                    for (int y = 0; y < ny; y++) {
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
        float[][] computeInhibitionToOutputCell() {
            // For all subunits, excluding the edge ones and the last ones (far right and bottom)
            for (int nsx = excludedEdgeSubunits; nsx < (nx-1-excludedEdgeSubunits); nsx++) {
                for (int nsy = excludedEdgeSubunits; nsy < (nx-1-excludedEdgeSubunits); nsy++) {
//------------------------------------------------------------------------------
                    // Find inhibition around center made of [nsx,nsy], [nsx+1,nsy+1], [nsx+1,nsy], [nsx,nsy+1]
                    for (int x = excludedEdgeSubunits; x < (nx-excludedEdgeSubunits); x++) {
                        for (int y = excludedEdgeSubunits; y < (ny-excludedEdgeSubunits); y++) {
//------------------------------------------------------------------------------
                            // Select computation type
                            if(!exponentialToTanh){// Use non-linear model (given the nonlinearity order)
                                if (((x == nsx) && (y == nsy)) || ((x == nsx+1) && (y == nsy+1)) || ((x == nsx) && (y == nsy+1)) || ((x == nsx+1) && (y == nsy))) {
                                } // Ignore center
                                else {
                                    inhibitionArray[nsx][nsy] += (float) Math.pow(subunits[x][y].computeInputToCell(),nonLinearityOrder);
                                }
                            }
                            else{ // Use tanh model (given saturation value): preferred method
                                if (((x == nsx) && (y == nsy)) || ((x == nsx+1) && (y == nsy+1)) || ((x == nsx) && (y == nsy+1)) || ((x == nsx+1) && (y == nsy))) {
                                } // Ignore center
                                else {
                                    inhibitionArray[nsx][nsy] += tanhSaturation*Math.tanh(subunits[x][y].computeInputToCell());
                                }                               
                            }
//------------------------------------------------------------------------------
                        }
                    }
//------------------------------------------------------------------------------
                    inhibitionArray[nsx][nsy] /= (ntot - 4); // Divide by the number of subunits to normalise
                    inhibitionArray[nsx][nsy] = synapticWeight * inhibitionArray[nsx][nsy]; // Give a synaptic weight (a simple scalar value)
//------------------------------------------------------------------------------
                    // Log inhibitionArray
                    if (startLogging == true){ 
                        try {
                        // Create a new file output stream
                        FileOutputStream out = new FileOutputStream(new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\inhibitionArray.txt"),true);
                        // Connect print stream to the output stream
                        p = new PrintStream(out);
                        p.print(inhibitionArray);
                        p.print(", ");
                        p.println(lastUpdateTimestamp);
                        p.close();
                    } 
                    catch (Exception e) {
                        System.err.println("Error writing to file");
                    }
                } // Delete inhibitionArray
                if (deleteLogging == true){
                    File fout = new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\inhibitionArray.txt");
                    fout.delete();
                }
//------------------------------------------------------------------------------
            }
        }
        return inhibitionArray;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//        

//----------------------------------------------------------------------------//
//-- Excitation Calculation method -------------------------------------------//
//----------------------------------------------------------------------------//
        float[][] computeExcitationToOutputCell() {
            // For all subunits, excluding the edge ones and the last ones (far right and bottom)
            for (int nsx = excludedEdgeSubunits; nsx < (nx-1-excludedEdgeSubunits); nsx++) {
                for (int nsy = excludedEdgeSubunits; nsy < (nx-1-excludedEdgeSubunits); nsy++) {
//------------------------------------------------------------------------------
                    // Find excitation of center made of [nsx,nsy], [nsx+1,nsy+1], [nsx+1,nsy], [nsx,nsy+1]
                    for (int x = excludedEdgeSubunits; x < (nx-excludedEdgeSubunits); x++) {
                        for (int y = excludedEdgeSubunits; y < (ny-excludedEdgeSubunits); y++) {
//------------------------------------------------------------------------------
                            // Select computation type
                            if(!exponentialToTanh){// Use non-linear model (given the nonlinearity order)
                                if (((x == nsx) && (y == nsy)) || ((x == nsx+1) && (y == nsy+1)) || ((x == nsx) && (y == nsy+1)) || ((x == nsx+1) && (y == nsy))) {
                                    // Average of 4 central cells
                                    excitationArray[nsx][nsy] = (float)((centerExcitationToSurroundInhibitionRatio*Math.pow(synapticWeight * subunits[x][y].computeInputToCell(),nonLinearityOrder))+
                                    (centerExcitationToSurroundInhibitionRatio*Math.pow(synapticWeight * subunits[x+1][y+1].computeInputToCell(),nonLinearityOrder))+
                                    (centerExcitationToSurroundInhibitionRatio*Math.pow(synapticWeight * subunits[x+1][y].computeInputToCell(),nonLinearityOrder))+
                                    (centerExcitationToSurroundInhibitionRatio*Math.pow(synapticWeight * subunits[x][y+1].computeInputToCell(),nonLinearityOrder)))/4;
                                }
                                else {
                                } // Ignore surround
                            }
                            else{ // Use tanh model (given saturation value): preferred method
                                if (((x == nsx) && (y == nsy)) || ((x == nsx+1) && (y == nsy+1)) || ((x == nsx) && (y == nsy+1)) || ((x == nsx+1) && (y == nsy))) { 
                                    // Average of 4 central cells
                                    excitationArray[nsx][nsy] = (float)((tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[x][y].computeInputToCell()))) + 
                                            (tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[x+1][y+1].computeInputToCell()))) + 
                                            (tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[x+1][y].computeInputToCell()))) + 
                                            (tanhSaturation*Math.tanh((centerExcitationToSurroundInhibitionRatio * synapticWeight * subunits[x][y+1].computeInputToCell())))) / 4;
                                }
                                else {
                                } // Ignore surround            
                            }
//------------------------------------------------------------------------------
                            // Log excitationArray
                            if(startLogging == true){
                                try {
                                    // Create a new file output stream.
                                    FileOutputStream out = new FileOutputStream(new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\excitationArray.txt"), true);
                                    // Connect print stream to the output stream
                                    p = new PrintStream(out);
                                    p.print(excitationArray);
                                    p.print(", ");
                                    p.println(lastUpdateTimestamp);
                                    p.close();
                                    out.close();
                                } 
                                catch (Exception e) {
                                    System.err.println("Error writing to file");
                                }
                            }
                            if (deleteLogging == true){
                                File fout = new File("C:\\Users\\Diederik Paul Moeys\\Desktop\\excitationArray.txt");
                                    fout.delete();
                            }
//------------------------------------------------------------------------------
                        }
                    }
//------------------------------------------------------------------------------
                }
            }
            return excitationArray;
        }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//

//----------------------------------------------------------------------------//
//-- Reset method ------------------------------------------------------------//
//----------------------------------------------------------------------------//
        synchronized private void reset() {
            // Reset arrays
            float[][] inhibitionArray = new float [nxmax-1][nymax-1];
            for(int i=0;i<inhibitionArray.length;i++) {
                for(int j=0;j<inhibitionArray.length;j++) {
                    inhibitionArray[i][j] = 0;
                }
            }
            float[][] excitationArray = new float [nxmax-1][nymax-1];
            for(int i=0;i<excitationArray.length;i++) {
                for(int j=0;j<excitationArray.length;j++) {
                    excitationArray[i][j] = 0;
                }
            }
            // Reset size
            nx = (chip.getSizeX() >> getSubunitSubsamplingBits());
            ny = (chip.getSizeY() >> getSubunitSubsamplingBits());
            if (nx < 4) {
                nx = 4;
            }
            if (ny < 4) {
                ny = 4; // Always at least 4 subunits or computation does not make sense
            }
            ntot = (nx-excludedEdgeSubunits) * (ny-excludedEdgeSubunits);
            subunits = new Subunit[nx][ny];		
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
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
            int off = (1 << (subunitSubsamplingBits)) / 2;
            for (int x = excludedEdgeSubunits; x < (nx-excludedEdgeSubunits); x++) {
                for (int y = excludedEdgeSubunits; y < (ny-excludedEdgeSubunits); y++) {
                    gl.glPushMatrix();
                    gl.glTranslatef((x << subunitSubsamplingBits) + off, (y << subunitSubsamplingBits) + off, 5);
                    if (((x == showXcoord) && (y == showYcoord)) || ((x == showXcoord+1) && (y == showYcoord+1)) || ((x == showXcoord) && (y == showYcoord+1)) || ((x == showXcoord+1) && (y == showYcoord))) {
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
        int lastTimestamp = 0;
        Random r = new Random();
        private final LowpassFilter isiFilter = new LowpassFilter(300);
        private int lastSpikeTimestamp = 0;
        private boolean initialized = false;
        float spikeRateHz = 0;
        boolean result = false;
        
//----------------------------------------------------------------------------//
//-- Update to check firing method -------------------------------------------//
//----------------------------------------------------------------------------//
        synchronized private boolean update(int timestamp) {
            // compute subunit input to us
            for(int nsx=0;nsx<(nxmax-1);nsx++) {
                for(int nsy=0;nsy<(nymax-1);nsy++) {
                    netSynapticInputArray[nsx][nsy] = (subunits.computeExcitationToOutputCell()[nsx][nsy] - subunits.computeInhibitionToOutputCell()[nsx][nsy]);
                    int dtUs = timestamp - lastTimestamp;
                    if (dtUs < 0) {
                        dtUs = 0; // to handle negative dt
                    }
                    lastTimestamp = timestamp;
                    if (poissonFiringEnabled) {
                        float spikeRate = netSynapticInputArray[nsx][nsy];
                        if (spikeRate < 0) {
                        result = false;
                        }
                        if (spikeRate > maxSpikeRateHz) {
                            spikeRate = maxSpikeRateHz;
                        }
                        if (r.nextFloat() < (spikeRate * 1e-6f * dtUs)) {
                            spike(timestamp, nsx, nsy);
                            result = true;
                        } 
                        else {
                            result = false;
                        }
                    } 
                    else { // IF neuron
                        membraneStateArray[nsx][nsy] += netSynapticInputArray[nsx][nsy] * dtUs * 1e-6f;
                        if (membraneStateArray[nsx][nsy] > integrateAndFireThreshold) {
                            spike(timestamp, nsx, nsy);
                            membraneStateArray[nsx][nsy] = 0;
                            result = true;
                        } 
                        else if (membraneStateArray[nsx][nsy] < -10) {
                            membraneStateArray[nsx][nsy] = 0;
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
     void spike(int timestamp, int x, int y) {
        if (enableSpikeSound) {
            spikeSound.play();
        }
        nSpikesArray[x][y]++;
        int dtUs = timestamp - lastSpikeTimestamp;
        if (initialized && (dtUs>=0)) {
            float avgIsiUs = isiFilter.filter(dtUs, timestamp);
            spikeRateHz = 1e6f / avgIsiUs;
        } 
        else {
            initialized = true;
        }
        lastSpikeTimestamp = timestamp;
     }
//----------------------------------------------------------------------------//
//----------------------------------------------------------------------------//
     
//----------------------------------------------------------------------------//
//-- Reset method ------------------------------------------------------------//
//----------------------------------------------------------------------------//
        void reset() {
            for(int nsx=0;nsx<(nymax-1);nsx++) {
                for(int nsy=0;nsy<(nymax-1);nsy++) {
                    membraneStateArray[nsx][nsy] = 0;
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
            for(int nsx=0;nsx<nxmax-1;nsx++) {
                for(int nsy=0;nsy<nymax-1;nsy++) {
                    nSpikesArray[nsx][nsy] = 0;
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