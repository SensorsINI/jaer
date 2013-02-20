/*
 * StdpFeatureLearningI.java
 * 
 * Created on February 14, 2013
 * 
 * Implements 'Extraction of Temporally correlated features from dynamic vision 
 * sensors with spike-timing-dependent-plasticity' Paper in DVS
 * 
 * @author Haza
 *
 */
package ch.unizh.ini.jaer.projects.stdplearning;

import com.sun.opengl.util.GLUT;
import java.awt.Dimension;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.logging.Level;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.GLU;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer.PlayMode;
import net.sf.jaer.graphics.FrameAnnotater;

@Description("Learns patterns in 2 layer Feedforward Neural Network")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class StdpFeatureLearningI extends EventFilter2D implements Observer, FrameAnnotater {

    // Controls
    protected int fireThres = getPrefs().getInt("StdpFeatureLearningI.fireThres", 40000);
    {
        setPropertyTooltip("fireThres", "Threshold directly affecting selectivity of neuron");
    }

    protected int tLTP = getPrefs().getInt("StdpFeatureLearningI.tLTP", 2000);
    {
        setPropertyTooltip("tLTP", "Size of temporal cluster to learn with a single neuron (us)");
    }

    protected int tRefrac = getPrefs().getInt("StdpFeatureLearningI.tRefrac", 10000);
    {
        setPropertyTooltip("tRefrac", "Refractory period of neuron (us)");
    }

    protected int tInhibit = getPrefs().getInt("StdpFeatureLearningI.tInhibit", 1500);
    {
        setPropertyTooltip("tInhibit", "Refractory period of neuron (us)");
    }
    
    protected int tauLeak = getPrefs().getInt("StdpFeatureLearningI.tauLeak", 5000);
    {
        setPropertyTooltip("tauLeak", "Leak time constant (us)");
    }

    protected int wMin = getPrefs().getInt("StdpFeatureLearningI.wMin", 1);
    {
        setPropertyTooltip("wMin", "Minimum weight");
    }

    protected int wMax = getPrefs().getInt("StdpFeatureLearningI.wMax", 1000);
    {
        setPropertyTooltip("wMax", "Maximum weight");
    }
    
    protected int wInitMean = getPrefs().getInt("StdpFeatureLearningI.wInitMean", 800);
    {
        setPropertyTooltip("wInitMean", "Initial weight mean");
    }

    protected int wInitSTD = getPrefs().getInt("StdpFeatureLearningI.wInitSTD", 160);
    {
        setPropertyTooltip("wInitSTD", "Initial weight standard deviation");
    }

    protected int alphaPlus = getPrefs().getInt("StdpFeatureLearningI.alphaPlus", 100);
    {
        setPropertyTooltip("alphaPlus", "Weight increment");
    }

    protected int alphaMinus = getPrefs().getInt("StdpFeatureLearningI.alphaMinus", 50);
    {
        setPropertyTooltip("alphaMinus", "Weight decrement");
    }

    protected int betaPlus = getPrefs().getInt("StdpFeatureLearningI.betaPlus", 0);
    {
        setPropertyTooltip("betaPlus", "Weight increment damping factor");
    }

    protected int betaMinus = getPrefs().getInt("StdpFeatureLearningI.betaMinus", 0);
    {
        setPropertyTooltip("betaMinus", "Weight decrement damping factor");
    }

    protected boolean resetOnLoop = getPrefs().getBoolean("StdpFeatureLearningI.resetOnLoop", true);
    {
        setPropertyTooltip("resetOnLoop", "Resets on loop in Playback of file");
    }

    protected boolean displayNeuronWeightMean = getPrefs().getBoolean("StdpFeatureLearningI.displayNeuronWeightMean", false);
    {
        setPropertyTooltip("displayNeuronWeightMean", "Displays each Neurons weight matrix mean");
    }

    protected boolean subSample = getPrefs().getBoolean("StdpFeatureLearningI.subSample", false);
    {
        setPropertyTooltip("subSample", "Make input be subsampled image");
    }

    protected boolean fireMaxOnlyOnceOnSpike = getPrefs().getBoolean("StdpFeatureLearningI.fireMaxOnlyOnceOnSpike", false);
    {
        setPropertyTooltip("fireMaxOnlyOnceOnSpike", "Input spike can only trigger at most one neuron to fire");
    }

    protected boolean displayCombinedPolarity = getPrefs().getBoolean("StdpFeatureLearningI.displayCombinedPolarity", false);
    {
        setPropertyTooltip("displayCombinedPolarity", "Display Combined Polarities in Neuron Weight Matrix");
    }
    
    // Input
    int xPixels;        // Number of pixels in x direction for input
    int yPixels;        // Number of pixels in y direction for input
    int xStart;         // Indicates x position of pixel (0,0)
    int yStart;         // Indicates x position of pixel (0,0)
    int numPolarities;  // Indicates total number of polarities of pixels

    // Neurons
    private int neuronsL1;                  // Number of neurons pixels are projecting to - Layer 1
    private float[] neuronPotential;        // Neuron Potential
    private float[][][][] synapseWeights;   // Synaptic weights of [neuron][polarity][x][y]
    private int[][][] pixelSpikeTiming;     // Last spike time of [polarity][x][y]
    private int[] neuronFireTiming;         // Last fire time of [neuron]
    private int[] neuronSpikeTiming;        // Last spike time of [neuron]
    private boolean neuronSpikeTimingInit;  // Indicates whether variable has been initialized
    private int t0;                         // Timestamp at which neurons are over lateral inhibition
    private int nextNeuronToUpdate;         // Helps indicates neuron in which to start update, is generally neuron next to one which fired last
    private boolean fireInhibitor;          // Inhibits all other neurons from firing 

    // Display Variables 
    GLU glu = null;                 // OpenGL Utilities
    JFrame neuronFrame = null;      // Frame displaying neuron weight matrix
    GLCanvas neuronCanvas = null;   // Canvas on which neuronFrame is drawn

    /** 
     * Constructor 
     * @param chip Called with AEChip properties
     */
    public StdpFeatureLearningI(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
    } // END CONSTRUCTOR

    /**
     * Called on creation
     * Initializes all the variables
     */    
    @Override
    public void initFilter() {
        xPixels = 64;
        yPixels = xPixels;
        xStart = chip.getSizeX()/2 - xPixels/2;
        yStart = chip.getSizeY()/2 - yPixels/2;
        numPolarities = 2;
        neuronsL1 = 48;
        neuronPotential = new float[neuronsL1];
        synapseWeights = new float[neuronsL1][numPolarities][xPixels][yPixels];
        pixelSpikeTiming = new int[numPolarities][xPixels][yPixels];
        neuronFireTiming = new int[neuronsL1];
        neuronSpikeTiming = new int[neuronsL1];
        neuronSpikeTimingInit = false;
        t0 = 0;
        nextNeuronToUpdate = 0;
        fireInhibitor = false;
                
        Random r = new Random(); // Used to generate random number for initial synapse weight
        for (int n=0; n<neuronsL1; n++) {
            neuronPotential[n] = 0;
            neuronFireTiming[n] = 0;
            for (int p = 0; p<numPolarities; p++) {
                for (int x=0; x<xPixels; x++) {
                    for (int y=0; y<yPixels; y++) {
                        if (n==0) 
                            pixelSpikeTiming[p][x][y] = 0;
                        // wInit is Gaussian distributed 
                        double wInit = r.nextGaussian()*wInitSTD+wInitMean;
                        if (wInit < wMin) 
                            wInit = wMin;
                        if (wInit > wMax) 
                            wInit = wMax;
                        synapseWeights[n][p][x][y] = (float) wInit;
                    } // END LOOP - yPixels
                } // END LOOP - xPixels
            } // END LOOP - polarities
        } // END LOOP - neuronsL1
    } // END METHOD

    /**
     * Called on filter reset
     * If resetFilter is called because of loop in playback, then don't reset 
     * biases unless resetOnLoop is enabled
     */    
    @Override
    synchronized public void resetFilter() {
        PlayMode playMode = chip.getAeViewer().getPlayMode();
        if ((resetOnLoop == false && playMode != PlayMode.PLAYBACK) == false) 
            initFilter();
    } // END METHOD

    /**
     * Called when objects being observed change and send a message
     * Re initialize filter if camera pixel size has changed
     * @param o Object that has changed
     * @param arg Message object has sent about change
     */    
    @Override
    public void update(Observable o, Object arg) {
        if (arg != null && (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY)) 
            initFilter();
    } // END METHOD

    /** 
     * Annotation or drawing method
     * @param drawable OpenGL Rendering Object
     */
    @Override
    public void annotate (GLAutoDrawable drawable) {
        if (!isAnnotationEnabled()) 
            return;
        GL gl = drawable.getGL();
        if (gl == null) 
            return;

        // Draw Box around relevant pixels
        gl.glPushMatrix();
        gl.glLineWidth(1f);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glColor3f(1f,0.1f,0.1f);
        gl.glVertex2f(xStart-1,yStart-1);
        gl.glVertex2f(xStart+xPixels,yStart-1);
        gl.glVertex2f(xStart+xPixels,yStart+yPixels);
        gl.glVertex2f(xStart-1,yStart+yPixels);
        gl.glEnd();
        gl.glPopMatrix();
    } // END METHOD

    /**
     * Receives Packets of information and passes it onto processing
     * @param in Input events can be null or empty.
     * @return The filtered events to be rendered
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        // Check for empty packet
        if(in==null) 
            return null;
        // Check that filtering is in fact enabled
        if(!filterEnabled) 
            return in;
        // If necessary, pre filter input packet 
        if(enclosedFilter!=null) 
            in=enclosedFilter.filterPacket(in);
        // Set output package out contents to be same class as in
        checkOutputPacketEventType(in);
        // Pre allocated Output Event Iterator used to set final out package 
        OutputEventIterator outItr = out.outputIterator();

        // Event Iterator - Write only relevant events inside xPixels by yPixels window to out
        // Apply STDP Rule
        for (Object o : in) {
            // Cast to PolarityEvent since we are interested in timestamp and polarity of spike
            PolarityEvent e = (PolarityEvent) o;
            // Subsample by stripping off necessary LSBs of events and shifting them to fit into input pixel window
            if (subSample == true) {
                double subSampleByX = Math.log((double)chip.getSizeX()/xPixels)/Math.log(2); 
                double subSampleByY = Math.log((double)chip.getSizeY()/yPixels)/Math.log(2); 
                e.x = (short) ((e.x >>> (int) subSampleByX) + xStart);
                e.y = (short) ((e.y >>> (int) subSampleByY) + yStart);
            }
            if ((e.x >= xStart && e.x < xStart + xPixels && e.y >= yStart && e.y < yStart + yPixels)) {
                // Assume that first ever event's timestamp is initial neuronSpikeTiming
                if (neuronSpikeTimingInit == false) {
                    for (int n=0; n<neuronsL1; n++) 
                        neuronSpikeTiming[n] = e.timestamp;
                    neuronSpikeTimingInit = true;
                } else {
                    applySTDP(e);
                    // Sets out variable
                    outItr.nextOutput().copyFrom(e);
                } // END IF
            } // END IF
        } // END FOR
        
        // Draw Neuron Weight Matrix
        checkNeuronFrame();
        neuronCanvas.repaint();

        // Output Filtered Events
        // NOTE: If subsample is enabled, out will include subsampled events but address has not been modified 
        //       This should only matter when recording out from this filter, which should not be important
        return out;
    } // END METHOD

    /**
     * Applies STDP Learning Rule
     * @param e Polarity Events which are considered input spikes into the neurons
     */
    private void applySTDP(PolarityEvent e) {
        int ts = e.timestamp;
        int x = e.x - xStart;
        int y = e.y - yStart;
        int polarity = e.getType();
        // If Neurons aren't inhibited
        if (ts >= t0) {
            // Update all Neuron Integration states
            for (int nIdx=0; nIdx<neuronsL1; nIdx++) {
                // Start update from neuron next to the one that fired last
                int n = (nextNeuronToUpdate + nIdx) % neuronsL1;
                // Make sure neuron is not in its refractory period
                if (ts >= neuronFireTiming[n]+tRefrac) {
                    boolean fire = updateNeuronIntegrationState(n, ts, polarity, x, y);
                    // Only update synapses if fireInhibitor is disabled
                    // fireInhibitor will only be enabled if fireMaxOnlyOnceOnSpike is on 
                    // and a neuron has already fired for the given input spike / event
                    if (fireInhibitor == false)
                        // If Neuron Fires Then 
                        if (fire == true) {
                            // Update synapse weights of these neurons
                            updateSynapseWeights(n, ts);
                            // Inhibit all neurons
                            t0 = ts + tInhibit;
                            // Update neuron fire timing maps
                            neuronFireTiming[n] = ts;
                            if (fireMaxOnlyOnceOnSpike == true) 
                                fireInhibitor = true;
                        } // END IF - Fire 
                } // END IF - Refractory period
            } // END LOOP - Neurons
        } // END IF - Inhibition
        // Make sure fireInhibitor is turned off after all neurons have been updated
        fireInhibitor = false;
        // Update pixel spike timing maps
        pixelSpikeTiming[polarity][x][y] = ts;
        nextNeuronToUpdate++;
    } // END METHOD
    
    /**
     * Updates Neuron Integration State every time there is a spike, tells whether neuron fires
     * @param neuron Current neuron
     * @param ts Current time stamp
     * @param polarity Current Polarity - On or Off
     * @param x X address of pixel / spike
     * @param y Y address of pixel / spike
     * @return boolean indicating whether neuron has fired 
     */
    private boolean updateNeuronIntegrationState(int neuron, int ts, int polarity, int x, int y) {
        // Neuron Update equation
        double temp = - (ts - neuronSpikeTiming[neuron]) / tauLeak;
        neuronPotential[neuron] = neuronPotential[neuron] * (float) Math.exp(temp) + synapseWeights[neuron][polarity][x][y];  
        neuronSpikeTiming[neuron] = ts;
        // If updated potential is above firing threshold, then fire and reset
        if (neuronPotential[neuron] >= fireThres) {
            neuronPotential[neuron] = 0;
            return true;
        } else {
            return false;
        } // END IF
    } // END METHOD

    @Override
    public synchronized void cleanup() {
        super.cleanup();
        if(neuronFrame!=null) neuronFrame.dispose();
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if(!isFilterEnabled()){
            hideNeuronFrame();
        }
    }
    
    
    
    /**
     * Updates Weights of synapses connecting pixels to the neurons according to STDP Learning Rule
     * @param neuron Current firing neuron
     * @param ts Current spike time stamp
     */
    private void updateSynapseWeights(int neuron, int ts) {
        // Update synapses for all polarities and pixels depending on STDP Rule
        for (int p=0; p<numPolarities; p++)
            for (int x=0; x<xPixels; x++)
                for (int y=0; y<yPixels; y++)
                    // LTP - Long Term Potentiation
                    if (ts-pixelSpikeTiming[p][x][y]<=tLTP) {
                        synapseWeights[neuron][p][x][y] = synapseWeights[neuron][p][x][y] + 
                                alphaPlus * (float) Math.exp(-betaPlus * 
                                (synapseWeights[neuron][p][x][y] - wMin) / (wMax - wMin));
                        // Cut off at wMax
                        if (synapseWeights[neuron][p][x][y] > wMax) 
                            synapseWeights[neuron][p][x][y] = wMax;
                    // LTD - Long Term Depression
                    } else {
                        synapseWeights[neuron][p][x][y] = synapseWeights[neuron][p][x][y] - 
                                alphaMinus * (float) Math.exp(-betaMinus * 
                                (wMax - synapseWeights[neuron][p][x][y]) / (wMax - wMin));
                        // Cut off at wMin
                        if (synapseWeights[neuron][p][x][y] < wMin) 
                            synapseWeights[neuron][p][x][y] = wMin;
                    } // END IF - STDP Rule
    } // END METHOD
    

    /**
     * Checks that Neuron Weight Matrix is always being displayed
     * Creates it if it is not
     */
    void checkNeuronFrame() {
        if (neuronFrame == null || (neuronFrame != null && !neuronFrame.isVisible())) 
            createNeuronFrame();
    } // END METHOD

    void hideNeuronFrame(){
        if(neuronFrame!=null) neuronFrame.setVisible(false);
    }
    
    /**
     * Creates Neuron Weight Matrix Frame
     */
    void createNeuronFrame() {
        // Initializes neuronFrame
        neuronFrame = new JFrame("Neuron Synapse Weight Matrix");
        neuronFrame.setPreferredSize(new Dimension(200, 400));
        // Creates drawing canvas
        neuronCanvas = new GLCanvas();
        // Adds listeners to canvas so that it will be updated as needed
        neuronCanvas.addGLEventListener(new GLEventListener() {
            // Called by the drawable immediately after the OpenGL context is initialized
            @Override
            public void init(GLAutoDrawable drawable) {
            
            }

            // Called by the drawable to initiate OpenGL rendering by the client
            // Used to draw and update canvas
            @Override
            synchronized public void display(GLAutoDrawable drawable) {
                if (synapseWeights == null) 
                    return;

                // Prepare drawing canvas
                int padding = 5;
                int neuronsPerRow = 8;
                int neuronsPerColumn = (int) Math.ceil((double)neuronsL1/(double)neuronsPerRow);
                
                // Draw in canvas
                GL gl = drawable.getGL();
                // Creates and scales drawing matrix so that each integer unit represents any given pixel
                gl.glLoadIdentity();
                gl.glScalef(drawable.getWidth() / (float) ((xPixels+padding)*neuronsPerRow-padding), 
                        drawable.getHeight() / (float) ((yPixels*numPolarities+padding)*neuronsPerColumn-padding), 1);
                // Sets the background color for when glClear is called
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);
                
                // Draw all Neurons
                int xOffset = 0; // Offset for starting point of individual neurons
                int yOffset = 0;
                for (int n=0; n<neuronsL1; n++) {
                    for (int p=0; p<numPolarities; p++) {
                        for (int x = 0; x<xPixels; x++) {
                            for (int y=0; y<yPixels; y++) {
                                // Either Combines polarities into single pixel or separates them into ON and OFF
                                if (displayCombinedPolarity == true && p==0) {
                                    float wON = (synapseWeights[n][0][x][y] - wMin) / (wMax - wMin);
                                    float wOFF = (synapseWeights[n][1][x][y] - wMin) / (wMax - wMin);
                                    //float wCombined = (wON + wOFF) / 2;
                                    //gl.glColor3f(wON, wCombined, wOFF);
                                    gl.glColor3f(wON, 0, wOFF);
                                    gl.glRectf(xOffset+x, yOffset+y + p*yPixels, xOffset+x + 1, yOffset+y + p*yPixels + 1);
                                } else if (displayCombinedPolarity == false) {
                                    // Normalized weight for synapse betyween wMin and wMax
                                    float w = (synapseWeights[n][p][x][y] - wMin) / (wMax - wMin);
                                    if (p==0) 
                                        gl.glColor3f(w, w, 0);
                                    else  
                                        gl.glColor3f(0, w, w);
                                    gl.glRectf(xOffset+x, yOffset+y + p*yPixels, xOffset+x + 1, yOffset+y + p*yPixels + 1);
                                }
                            } // END LOOP - Y
                        } // END LOOP - X
                    } // END LOOP - Polarity
                    if (displayNeuronWeightMean == true) {
                        final int font = GLUT.BITMAP_HELVETICA_12;
                        GLUT glut = chip.getCanvas().getGlut();
                        gl.glColor3f(1, 1, 1);
                        gl.glRasterPos3f(xOffset, yOffset, 0);
                        // Neuron info
                        glut.glutBitmapString(font, String.format("%.2f", getNeuronMeanWeight(n)));
                    }
                    // Adjust x and y Offsets
                    xOffset += xPixels+padding; 
                    if (n%neuronsPerRow == neuronsPerRow-1) {
                        xOffset = 0;
                        yOffset += yPixels*numPolarities+padding;
                    } // END IF
                } // END LOOP - Neuron
                
                // Log error if there is any in OpenGL
                int error = gl.glGetError();
                if (error != GL.GL_NO_ERROR) {
                    if (glu == null) 
                        glu = new GLU();
                    log.log(Level.WARNING, "GL error number {0} {1}", new Object[]{error, glu.gluErrorString(error)});
                } // END IF
            } // END METHOD

            // Called by the drawable during the first repaint after the component has been resized 
            @Override
            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL gl = drawable.getGL();
                final int B = 10;
                gl.glMatrixMode(GL.GL_PROJECTION);
                gl.glLoadIdentity(); 
                gl.glOrtho(-B, drawable.getWidth() + B, -B, drawable.getHeight() + B, 10000, -10000);
                gl.glMatrixMode(GL.GL_MODELVIEW);
                gl.glViewport(0, 0, width, height);
            }

        // Called by drawable when display mode or display device has changed
        @Override
            public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
 
            } // END METHOD
        }); // END SCOPE - GLEventListener
        
        // Add neuronCanvas to neuronFrame
        neuronFrame.getContentPane().add(neuronCanvas);
        // Causes window to be sized to fit the preferred size and layout of its subcomponents
        neuronFrame.pack();
        neuronFrame.setVisible(true);
    } // END METHOD

    public float getNeuronMeanWeight(int neuron) {
        float mean = 0;
        for (int p=0; p<numPolarities; p++)
            for (int x=0; x<xPixels; x++)
                for (int y=0; y<yPixels; y++)
                    mean += synapseWeights[neuron][p][x][y];
        return mean/(numPolarities*xPixels*yPixels);
    }
                    
    public int getFireThres() {
        return this.fireThres;
    }
    public void setFireThres(final int fireThres) {
        getPrefs().putInt("StdpFeatureLearningI.fireThres", fireThres);
        getSupport().firePropertyChange("fireThres", this.fireThres, fireThres);
        this.fireThres = fireThres;
    }
    public int getFireThresMin() {
        return 1;
    }
    public int getFireThresMax() {
        return 100000;
    }
    // END fireThres
    
    public int getTLTP() {
        return this.tLTP;
    }
    public void setTLTP(final int tLTP) {
        getPrefs().putInt("StdpFeatureLearningI.tLTP", tLTP);
        getSupport().firePropertyChange("tLTP", this.tLTP, tLTP);
        this.tLTP = tLTP;
    }
    public int getTLTPMin() {
        return 1;
    }
    public int getTLTPMax() {
        return 100000;
    }
    // END tLTP
    
    public int getTRefrac() {
        return this.tRefrac;
    }
    public void setTRefrac(final int tRefrac) {
        getPrefs().putInt("StdpFeatureLearningI.tRefrac", tRefrac);
        getSupport().firePropertyChange("tRefrac", this.tRefrac, tRefrac);
        this.tRefrac = tRefrac;
    }
    public int getTRefracMin() {
        return 1;
    }
    public int getTRefracMax() {
        return 100000;
    }
    // END tRefrac
    
    public int getTInhibit() {
        return this.tInhibit;
    }
    public void setTInhibit(final int tInhibit) {
        getPrefs().putInt("StdpFeatureLearningI.tInhibit", tInhibit);
        getSupport().firePropertyChange("tInhibit", this.tInhibit, tInhibit);
        this.tInhibit = tInhibit;
    }
    public int getTInhibitMin() {
        return 1;
    }
    public int getTInhibitMax() {
        return 100000;
    }
    // END tRefrac

    public int getTauLeak() {
        return this.tauLeak;
    }
    public void setTauLeak(final int tauLeak) {
        getPrefs().putInt("StdpFeatureLearningI.tauLeak", tauLeak);
        getSupport().firePropertyChange("tauLeak", this.tauLeak, tauLeak);
        this.tauLeak = tauLeak;
    }
    public int getTauLeakMin() {
        return 1;
    }
    public int getTauLeakMax() {
        return 100000;
    }
    // END tauLeak
    
    public int getWMin() {
        return this.wMin;
    }
    public void setWMin(final int wMin) {
        getPrefs().putInt("StdpFeatureLearningI.wMin", wMin);
        getSupport().firePropertyChange("wMin", this.wMin, wMin);
        this.wMin = wMin;
    }
    public int getWMinMin() {
        return 1;
    }
    public int getWMinMax() {
        return 10000;
    }
    // END wMin
    
    public int getWMax() {
        return this.wMax;
    }
    public void setWMax(final int wMax) {
        getPrefs().putInt("StdpFeatureLearningI.wMax", wMax);
        getSupport().firePropertyChange("wMax", this.wMax, wMax);
        this.wMax = wMax;
    }
    public int getWMaxMin() {
        return 1;
    }
    public int getWMaxMax() {
        return 10000;
    }
    // END wMax

    public int getWInitMean() {
        return this.wInitMean;
    }
    public void setWInitMean(final int wInitMean) {
        getPrefs().putInt("StdpFeatureLearningI.wInitMean", wInitMean);
        getSupport().firePropertyChange("wInitMean", this.wInitMean, wInitMean);
        this.wInitMean = wInitMean;
    }
    public int getWInitMeanMin() {
        return 1;
    }
    public int getWInitMeanMax() {
        return 10000;
    }
    // END wInitMean
    
    public int getWInitSTD() {
        return this.wInitSTD;
    }
    public void setWInitSTD(final int wInitSTD) {
        getPrefs().putInt("StdpFeatureLearningI.wInitSTD", wInitSTD);
        getSupport().firePropertyChange("wInitSTD", this.wInitSTD, wInitSTD);
        this.wInitSTD = wInitSTD;
    }
    public int getWInitSTDMin() {
        return 1;
    }
    public int getWInitSTDMax() {
        return 10000;
    }
    // END wInitSTD

    public int getAlphaPlus() {
        return this.alphaPlus;
    }
    public void setAlphaPlus(final int alphaPlus) {
        getPrefs().putInt("StdpFeatureLearningI.alphaPlus", alphaPlus);
        getSupport().firePropertyChange("alphaPlus", this.alphaPlus, alphaPlus);
        this.alphaPlus = alphaPlus;
    }
    public int getAlphaPlusMin() {
        return 0;
    }
    public int getAlphaPlusMax() {
        return 1000;
    }
    // END alphaPlus

    public int getAlphaMinus() {
        return this.alphaMinus;
    }
    public void setAlphaMinus(final int alphaMinus) {
        getPrefs().putInt("StdpFeatureLearningI.alphaMinus", alphaMinus);
        getSupport().firePropertyChange("alphaMinus", this.alphaMinus, alphaMinus);
        this.alphaMinus = alphaMinus;
    }
    public int getAlphaMinusMin() {
        return 0;
    }
    public int getAlphaMinusMax() {
        return 1000;
    }
    // END alphaMinus

    public int getBetaPlus() {
        return this.betaPlus;
    }
    public void setBetaPlus(final int betaPlus) {
        getPrefs().putInt("StdpFeatureLearningI.betaPlus", betaPlus);
        getSupport().firePropertyChange("betaPlus", this.betaPlus, betaPlus);
        this.betaPlus = betaPlus;
    }
    public int getBetaPlusMin() {
        return 0;
    }
    public int getBetaPlusMax() {
        return 1000;
    }
    // END betaPlus

    public int getBetaMinus() {
        return this.betaMinus;
    }
    public void setBetaMinus(final int betaMinus) {
        getPrefs().putInt("StdpFeatureLearningI.betaMinus", betaMinus);
        getSupport().firePropertyChange("betaMinus", this.betaMinus, betaMinus);
        this.betaMinus = betaMinus;
    }
    public int getBetaMinusMin() {
        return 0;
    }
    public int getBetaMinusMax() {
        return 1000;
    }
    // END betaMinus
    
    public boolean isResetOnLoop() {
        return resetOnLoop;
    }
    synchronized public void setResetOnLoop(boolean resetOnLoop) {
        this.resetOnLoop = resetOnLoop;
    }
    // END resetOnLoop

    public boolean isDisplayNeuronWeightMean() {
        return displayNeuronWeightMean;
    }
    synchronized public void setDisplayNeuronWeightMean(boolean displayNeuronWeightMean) {
        this.displayNeuronWeightMean = displayNeuronWeightMean;
    }
    // END displayNeuronWeightMean

    public boolean isSubSample() {
        return subSample;
    }
    synchronized public void setSubSample(boolean subSample) {
        this.subSample = subSample;
    }
    // END subSample
    
    public boolean isFireMaxOnlyOnceOnSpike() {
        return fireMaxOnlyOnceOnSpike;
    }
    synchronized public void setFireMaxOnlyOnceOnSpike(boolean fireMaxOnlyOnceOnSpike) {
        this.fireMaxOnlyOnceOnSpike = fireMaxOnlyOnceOnSpike;
    }
    // END fireMaxOnlyOnceOnSpike

    public boolean isDisplayCombinedPolarity() {
        return displayCombinedPolarity;
    }
    synchronized public void setDisplayCombinedPolarity(boolean displayCombinedPolarity) {
        this.displayCombinedPolarity = displayCombinedPolarity;
    }
    // END displayCombinedPolarity

} // END CLASS










