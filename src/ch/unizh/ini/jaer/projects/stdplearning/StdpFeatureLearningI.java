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

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.logging.Level;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.GLU;
import javax.swing.JFrame;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.eventio.AEFileInputStreamInterface;

@Description("Learns patterns in 2 layer Feedforward Neural Network")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class StdpFeatureLearningI extends EventFilter2D implements Observer, FrameAnnotater, PropertyChangeListener {

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

    protected boolean keepWeightsOnRewind = getPrefs().getBoolean("StdpFeatureLearningI.keepWeightsOnRewind", true);

    {
        setPropertyTooltip("keepWeightsOnRewind", "Resets everything on loop in Playback of file except for synapse weights");
    }

    protected boolean displayNeuronStatistics = getPrefs().getBoolean("StdpFeatureLearningI.displayNeuronStatistics", true);

    {
        setPropertyTooltip("displayNeuronStatistics", "Displays each Neurons weight matrix statistics: Mean, STD, Min, Max");
    }

    protected boolean subSample = getPrefs().getBoolean("StdpFeatureLearningI.subSample", false);

    {
        setPropertyTooltip("subSample", "Make input be subsampled image");
    }

    protected boolean fireMaxOnlyOnceOnSpike = getPrefs().getBoolean("StdpFeatureLearningI.fireMaxOnlyOnceOnSpike", false);

    {
        setPropertyTooltip("fireMaxOnlyOnceOnSpike", "Input spike can only trigger at most one neuron to fire");
    }

    protected boolean displayCombinedPolarity = getPrefs().getBoolean("StdpFeatureLearningI.displayCombinedPolarity", true);

    {
        setPropertyTooltip("displayCombinedPolarity", "Display Combined Polarities in Neuron Weight Matrix");
    }

    protected boolean neuronFireHistogram = getPrefs().getBoolean("StdpFeatureLearningI.neuronFireHistogram", true);

    {
        setPropertyTooltip("neuronFireHistogram", "Draw a box around firing neuron with color corresponding to number of times its fired in given packet");
    }

    // Input
    private int xPixels;        // Number of pixels in x direction for input
    private int yPixels;        // Number of pixels in y direction for input
    private int xStart;         // Indicates x position of pixel (0,0)
    private int yStart;         // Indicates x position of pixel (0,0)
    private int numPolarities;  // Indicates total number of polarities of pixels

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
    private int[] neuronFire;               // Indicates number of times a particular neuron has fired for a given time stamp or packet
    private int numNeuronFire;              // Indicates number of times the neurons have fired for a given time stamp or packet
    private boolean rewind = false;         // Indicates rewind in playback

    // Display Variables
    private GLU glu = null;                 // OpenGL Utilities
    private JFrame neuronFrame = null;      // Frame displaying neuron weight matrix
    private GLCanvas neuronCanvas = null;   // Canvas on which neuronFrame is drawn

    // Listeners
    private boolean viewerPropertyChangeListenerInit; // Indicates that listener for viewer changes has been initialized

    /**
     * Constructor
     *
     * @param chip Called with AEChip properties
     */
    public StdpFeatureLearningI(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
    } // END CONSTRUCTOR

    /**
     * Called on creation Initializes all 'final' size variables and declares
     * arrays resetFilter call at end of method actually initializes all values
     */
    @Override
    public void initFilter() {
        xPixels = 16;
        yPixels = xPixels;
        xStart = (chip.getSizeX() / 2) - (xPixels / 2);
        yStart = (chip.getSizeY() / 2) - (yPixels / 2);
        numPolarities = 2;

        neuronsL1 = 48;
        neuronPotential = new float[neuronsL1];
        synapseWeights = new float[neuronsL1][numPolarities][xPixels][yPixels];
        pixelSpikeTiming = new int[numPolarities][xPixels][yPixels];
        neuronFireTiming = new int[neuronsL1];
        neuronSpikeTiming = new int[neuronsL1];
        neuronFire = new int[neuronsL1];

        viewerPropertyChangeListenerInit = false;

        resetFilter();
    } // END METHOD

    /**
     * Called on filter reset which happens on creation of filter, on reset
     * button press, and on rewind Initializes all variables which aren't final
     * to their default values Note that synapseWeights are either initialized
     * to wInit parameters or left as they were previously
     */
    @Override
    synchronized public void resetFilter() {
        neuronSpikeTimingInit = false;
        t0 = 0;
        nextNeuronToUpdate = 0;
        fireInhibitor = false;
        numNeuronFire = 0;

        for (int n = 0; n < neuronsL1; n++) {
            neuronPotential[n] = 0;
            neuronFireTiming[n] = 0;
            neuronFire[n] = 0;
        } // END LOOP - Neurons

        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    pixelSpikeTiming[p][x][y] = 0;
                }
            }
        }

        // Keep current weights on rewind if keepWeightsOnRewind is enabled
        // Otherwise initialize random weights according to parameters
        if (((keepWeightsOnRewind == true) && (rewind == true)) == false) {
            Random r = new Random(); // Used to generate random number for initial synapse weight
            for (int n = 0; n < neuronsL1; n++) {
                for (int p = 0; p < numPolarities; p++) {
                    for (int x = 0; x < xPixels; x++) {
                        for (int y = 0; y < yPixels; y++) {
                            // wInit is Gaussian distributed
                            double wInit = (r.nextGaussian() * wInitSTD) + wInitMean;
                            if (wInit < wMin) {
                                wInit = wMin;
                            }
                            if (wInit > wMax) {
                                wInit = wMax;
                            }
                            synapseWeights[n][p][x][y] = (float) wInit;
                        } // END LOOP - All synapses
                    }
                }
            }
        } // END IF - Synapse Weight Initialization

        // Make sure rewind flag is turned off at end
        if (rewind == true) {
            rewind = false;
        }
    } // END METHOD

    /**
     * Called when filter is turned off makes sure neuronFrame gets turned off
     */
    @Override
    public synchronized void cleanup() {
        if (neuronFrame != null) {
            neuronFrame.dispose();
        }
    } // END METHOD

    /**
     * Resets the filter Hides neuronFrame is filter is not enabled
     *
     * @param yes true to reset
     */
    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (!isFilterEnabled()) {
            hideNeuronFrame();
        }
    } // END METHOD

    /**
     * Called when objects being observed change and send a message Re
     * initialize filter if camera pixel size has changed
     *
     * @param o Object that has changed
     * @param arg Message object has sent about change
     */
    @Override
    public void update(Observable o, Object arg) {
        if ((arg != null) && ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY))) {
            initFilter();
        }
    } // END METHOD

    /**
     * Check for property changes coming from AEViewer and AEFileInputStream
     * Every FileOpen create new FileInputStream listener which would detect
     * when playback is looped
     *
     * @param evt event coming from AEViewer or AEFileInputStream
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof AEFileInputStream) {
            if (evt.getPropertyName().equals(AEInputStream.EVENT_REWIND)) {
                rewind = true;
            } else if (evt.getPropertyName().equals(AEInputStream.EVENT_WRAPPED_TIME)) {
                rewind = true;
            } // END IF
        } else if (evt.getSource() instanceof AEViewer) {
            if (evt.getPropertyName().equals(AEViewer.EVENT_FILEOPEN)) {
                log.info("File Open");
                AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
                AEFileInputStreamInterface in = (player.getAEInputStream());
                in.getSupport().addPropertyChangeListener(this);
                // Treat FileOpen same as a rewind
                rewind = true;
                resetFilter();
            } // END IF
        } // END IF
    } // END IF

    /**
     * Annotation or drawing method
     *
     * @param drawable OpenGL Rendering Object
     */
    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isAnnotationEnabled()) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }

        // Draw Box around relevant pixels
        gl.glPushMatrix();
        gl.glLineWidth(1f);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glColor3f(1f, 0.1f, 0.1f);
        gl.glVertex2f(xStart - 1, yStart - 1);
        gl.glVertex2f(xStart + xPixels, yStart - 1);
        gl.glVertex2f(xStart + xPixels, yStart + yPixels);
        gl.glVertex2f(xStart - 1, yStart + yPixels);
        gl.glEnd();
        gl.glPopMatrix();
    } // END METHOD

    /**
     * Receives Packets of information and passes it onto processing
     *
     * @param in Input events can be null or empty.
     * @return The filtered events to be rendered
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        // Check for empty packet
        if (in == null) {
            return null;
        }
        // Check that filtering is in fact enabled
        if (!filterEnabled) {
            return in;
        }
        // If necessary, pre filter input packet
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        // Add listener to viewer which in turn will add listener to Input Files for control on rewind
        // Do this in filterPacket to make sure that AEViewer is already initialized
        if (viewerPropertyChangeListenerInit == false) {
            chip.getAeViewer().addPropertyChangeListener(this);
            chip.getAeViewer().getAePlayer().getSupport().addPropertyChangeListener(this); // TODO might be duplicated callback
            viewerPropertyChangeListenerInit = true;
        }
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
                double subSampleByX = Math.log((double) chip.getSizeX() / xPixels) / Math.log(2);
                double subSampleByY = Math.log((double) chip.getSizeY() / yPixels) / Math.log(2);
                e.x = (short) ((e.x >>> (int) subSampleByX) + xStart);
                e.y = (short) ((e.y >>> (int) subSampleByY) + yStart);
            } // END IF
            if (((e.x >= xStart) && (e.x < (xStart + xPixels)) && (e.y >= yStart) && (e.y < (yStart + yPixels)))) {
                // Assume that first ever event's timestamp is initial neuronSpikeTiming for all neurons
                if (neuronSpikeTimingInit == false) {
                    for (int n = 0; n < neuronsL1; n++) {
                        neuronSpikeTiming[n] = e.timestamp;
                    }
                    neuronSpikeTimingInit = true;
                } else {
                    applySTDP(e);
                } // END IF
                // Sets out variable
                outItr.nextOutput().copyFrom(e);
            } // END IF
        } // END FOR

        // Draw Neuron Weight Matrix
        checkNeuronFrame();
        neuronCanvas.repaint();

        // Reset neuronFire count and array
        numNeuronFire = 0;
        for (int n = 0; n < neuronsL1; n++) {
            neuronFire[n] = 0;
        }

        // Output Filtered Events
        // NOTE: If subsample is enabled, out will include subsampled events but address has not been modified
        //       This should only matter when recording out from this filter, which should not be important
        return out;
    } // END METHOD

    /**
     * Applies STDP Learning Rule
     *
     * @param e Polarity Events which are considered input spikes into the
     * neurons
     */
    private void applySTDP(PolarityEvent e) {
        int ts = e.timestamp;
        int x = e.x - xStart;
        int y = e.y - yStart;
        int polarity = e.getType();
        // If Neurons aren't inhibited
        if (ts >= t0) {
            // Update all Neuron Integration states
            for (int nIdx = 0; nIdx < neuronsL1; nIdx++) {
                // Start update from neuron next to the one that fired last
                int n = (nextNeuronToUpdate + nIdx) % neuronsL1;
                // Make sure neuron is not in its refractory period
                if (ts >= (neuronFireTiming[n] + tRefrac)) {
                    boolean potentialAboveThres = updateNeuronIntegrationState(n, ts, polarity, x, y);
                    // Only update synapses if fireInhibitor is disabled
                    // fireInhibitor will only be enabled if fireMaxOnlyOnceOnSpike is on
                    // and a neuron has already fired for the given input spike / event
                    if (fireInhibitor == false) {
                        // If Neuron Fires Then
                        if (potentialAboveThres == true) {
                            // Indicate number of times neuron has fired in this particular data packet
                            neuronFire[n]++;
                            numNeuronFire++;
                            // Update synapse weights of these neurons
                            updateSynapseWeights(n, ts);
                            // Inhibit all neurons
                            t0 = ts + tInhibit;
                            // Update neuron fire timing maps
                            neuronFireTiming[n] = ts;
                            // Update which neuron to start updating on next spike
                            nextNeuronToUpdate = n + 1;
                            // If we allow neuron to fire only once per spike, then finish updating potentials for all neurons and inhibit firing
                            if (fireMaxOnlyOnceOnSpike == true) {
                                fireInhibitor = true;
                            }
                        } // END IF - Fire
                    }
                } // END IF - Refractory period
            } // END LOOP - Neurons
        } // END IF - Inhibition
        // Make sure fireInhibitor is turned off after all neurons have been updated
        fireInhibitor = false;
        // Update pixel spike timing maps
        pixelSpikeTiming[polarity][x][y] = ts;
    } // END METHOD

    /**
     * Updates Neuron Integration State every time there is a spike, tells
     * whether neuron fires
     *
     * @param neuron Current neuron
     * @param ts Current time stamp
     * @param polarity Current Polarity - On or Off
     * @param x X address of pixel / spike
     * @param y Y address of pixel / spike
     * @return boolean indicating whether neuron has fired
     */
    private boolean updateNeuronIntegrationState(int neuron, int ts, int polarity, int x, int y) {
        // Neuron Update equation
        double temp = -(ts - neuronSpikeTiming[neuron]) / (double) tauLeak;
        neuronPotential[neuron] = (neuronPotential[neuron] * (float) Math.exp(temp)) + synapseWeights[neuron][polarity][x][y];
        neuronSpikeTiming[neuron] = ts;
        // If updated potential is above firing threshold, then fire and reset
        if (neuronPotential[neuron] >= fireThres) {
            neuronPotential[neuron] = 0;
            return true;
        } else {
            return false;
        } // END IF
    } // END METHOD

    /**
     * Updates Weights of synapses connecting pixels to the neurons according to
     * STDP Learning Rule
     *
     * @param neuron Current firing neuron
     * @param ts Current spike time stamp
     */
    private void updateSynapseWeights(int neuron, int ts) {
        // Update synapses for all polarities and pixels depending on STDP Rule
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    // LTP - Long Term Potentiation
                    if ((ts - pixelSpikeTiming[p][x][y]) <= tLTP) {
                        synapseWeights[neuron][p][x][y] = synapseWeights[neuron][p][x][y]
                                + (alphaPlus * (float) Math.exp((-betaPlus
                                        * (synapseWeights[neuron][p][x][y] - wMin)) / (double) (wMax - wMin)));
                        // Cut off at wMax
                        if (synapseWeights[neuron][p][x][y] > wMax) {
                            synapseWeights[neuron][p][x][y] = wMax;
                            // LTD - Long Term Depression
                        }
                    } else {
                        synapseWeights[neuron][p][x][y] = synapseWeights[neuron][p][x][y]
                                - (alphaMinus * (float) Math.exp((-betaMinus
                                        * (wMax - synapseWeights[neuron][p][x][y])) / (double) (wMax - wMin)));
                        // Cut off at wMin
                        if (synapseWeights[neuron][p][x][y] < wMin) {
                            synapseWeights[neuron][p][x][y] = wMin;
                        }
                    } // END IF - STDP Rule
                } // END IF
            }
        }
    } // END METHOD

    /**
     * Checks that Neuron Weight Matrix is always being displayed Creates it if
     * it is not
     */
    void checkNeuronFrame() {
        if ((neuronFrame == null) || ((neuronFrame != null) && !neuronFrame.isVisible())) {
            createNeuronFrame();
        }
    } // END METHOD

    /**
     * Hides neuronFrame
     */
    void hideNeuronFrame() {
        if (neuronFrame != null) {
            neuronFrame.setVisible(false);
        }
    } // END METHOD

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
                if (synapseWeights == null) {
                    return;
                }

                // Prepare drawing canvas
                int padding = 5;
                int neuronsPerRow = 8;
                int neuronsPerColumn = (int) Math.ceil(neuronsL1 / (double) neuronsPerRow);

                // Draw in canvas
                GL2 gl = drawable.getGL().getGL2();
                // Creates and scales drawing matrix so that each integer unit represents any given pixel
                gl.glLoadIdentity();
                gl.glScalef(drawable.getSurfaceWidth() / (float) (((xPixels + padding) * neuronsPerRow) - padding),
                        drawable.getSurfaceHeight() / (float) ((((yPixels * numPolarities) + padding) * neuronsPerColumn) - padding), 1);
                // Sets the background color for when glClear is called
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);

                // Draw all Neurons
                int xOffset = 0; // Offset for starting point of individual neurons
                int yOffset = 0;
                for (int n = 0; n < neuronsL1; n++) {
                    for (int x = 0; x < xPixels; x++) {
                        for (int y = 0; y < yPixels; y++) {
                            // Handle Polarity cases independently, not through a for loop
                            float wOFF = (synapseWeights[n][0][x][y] - wMin) / (wMax - wMin);
                            float wON = (synapseWeights[n][1][x][y] - wMin) / (wMax - wMin);
                            if (displayCombinedPolarity == true) {
                                gl.glColor3f(wON, 0, wOFF);
                                gl.glRectf(xOffset + x, yOffset + y + yPixels, xOffset + x + 1, yOffset + y + yPixels + 1);
                            } else if (displayCombinedPolarity == false) {
                                gl.glColor3f(wOFF, wOFF, 0);
                                gl.glRectf(xOffset + x, yOffset + y, xOffset + x + 1, yOffset + y + 1);
                                gl.glColor3f(0, wON, wON);
                                gl.glRectf(xOffset + x, yOffset + y + yPixels, xOffset + x + 1, yOffset + y + yPixels + 1);
                            } // END IF - Display Polarity
                        } // END LOOP - Y
                    } // END LOOP - X

                    // Display Neuron Statistics - Mean, STD, Min, Max
                    if (displayNeuronStatistics == true) {
                        final int font = GLUT.BITMAP_HELVETICA_12;
                        GLUT glut = chip.getCanvas().getGlut();
                        gl.glColor3f(1, 1, 1);
                        // Neuron info
                        gl.glRasterPos3f(xOffset, yOffset, 0);
                        glut.glutBitmapString(font, String.format("M %.2f", getNeuronMeanWeight(n)));
                        //gl.glRasterPos3f(xOffset, yOffset+4, 0);
                        //glut.glutBitmapString(font, String.format("S %.2f", getNeuronSTDWeight(n)));
                        //gl.glRasterPos3f(xOffset, yOffset+8, 0);
                        //glut.glutBitmapString(font, String.format("- %.2f", getNeuronMinWeight(n)));
                        //gl.glRasterPos3f(xOffset, yOffset+12, 0);
                        //glut.glutBitmapString(font, String.format("+ %.2f", getNeuronMaxWeight(n)));
                    } // END IF

                    // Draw Box around firing neuron with color corresponding to
                    // Neuron Firing Rate in given Packet
                    if (neuronFireHistogram == true) {
                        float color = neuronFire[n] / (float) numNeuronFire;
                        gl.glPushMatrix();
                        gl.glLineWidth(1f);
                        gl.glBegin(GL.GL_LINE_LOOP);
                        gl.glColor3f(color, color, color);
                        gl.glVertex2f(xOffset, yOffset);
                        gl.glVertex2f(xOffset, yOffset + (yPixels * 2));
                        gl.glVertex2f(xOffset + xPixels, yOffset + (yPixels * 2));
                        gl.glVertex2f(xOffset, yOffset + (yPixels * 2));
                        gl.glEnd();
                        gl.glPopMatrix();
                    } // END IF

                    // Adjust x and y Offsets
                    xOffset += xPixels + padding;
                    if ((n % neuronsPerRow) == (neuronsPerRow - 1)) {
                        xOffset = 0;
                        yOffset += (yPixels * numPolarities) + padding;
                    } // END IF
                } // END LOOP - Neuron

                // Log error if there is any in OpenGL
                int error = gl.glGetError();
                if (error != GL.GL_NO_ERROR) {
                    if (glu == null) {
                        glu = new GLU();
                    }
                    log.log(Level.WARNING, "GL error number {0} {1}", new Object[]{error, glu.gluErrorString(error)});
                } // END IF
            } // END METHOD - Display

            // Called by the drawable during the first repaint after the component has been resized
            // Adds a border to canvas by adding perspective to it and then flattening out image
            @Override
            synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL2 gl = drawable.getGL().getGL2();
                final int border = 10;
                gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
                gl.glLoadIdentity();
                gl.glOrtho(-border, drawable.getSurfaceWidth() + border, -border, drawable.getSurfaceHeight() + border, 10000, -10000);
                gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
                gl.glViewport(0, 0, width, height);
            }

            @Override
            public void dispose(GLAutoDrawable arg0) {
                // TODO Auto-generated method stub

            }
        }); // END SCOPE - GLEventListener

        // Add neuronCanvas to neuronFrame
        neuronFrame.getContentPane().add(neuronCanvas);
        // Causes window to be sized to fit the preferred size and layout of its subcomponents
        neuronFrame.pack();
        neuronFrame.setVisible(true);
    } // END METHOD

    /**
     * Returns Mean of Neuron's weights
     *
     * @param neuron Current neuron
     * @return mean
     */
    public float getNeuronMeanWeight(int neuron) {
        float mean = 0;
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    mean += synapseWeights[neuron][p][x][y];
                }
            }
        }
        return mean / (numPolarities * xPixels * yPixels);
    } // END METHOD

    /**
     * Returns Standard Deviation of Neuron's weights
     *
     * @param neuron Current neuron
     * @return standard deviation
     */
    public float getNeuronSTDWeight(int neuron) {
        float mean = getNeuronMeanWeight(neuron);
        float var = 0;
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    var += (mean - synapseWeights[neuron][p][x][y]) * (mean - synapseWeights[neuron][p][x][y]);
                }
            }
        }
        double std = Math.sqrt(var / (double) (numPolarities * xPixels * yPixels));
        return (float) std;
    } // END METHOD

    /**
     * Returns Minimum of Neuron's weights
     *
     * @param neuron Current neuron
     * @return minimum
     */
    public float getNeuronMinWeight(int neuron) {
        float min = synapseWeights[neuron][0][0][0];
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    if (min > synapseWeights[neuron][p][x][y]) {
                        min = synapseWeights[neuron][p][x][y];
                    }
                }
            }
        }
        return min;
    } // END METHOD

    /**
     * Returns Maximum of Neuron's weights
     *
     * @param neuron Current neuron
     * @return maximum
     */
    public float getNeuronMaxWeight(int neuron) {
        float max = synapseWeights[neuron][0][0][0];
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    if (max < synapseWeights[neuron][p][x][y]) {
                        max = synapseWeights[neuron][p][x][y];
                    }
                }
            }
        }
        return max;
    }

    public int getFireThres() {
        return fireThres;
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
        return tLTP;
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
        return tRefrac;
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
        return tInhibit;
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
        return tauLeak;
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
        return wMin;
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
        return wMax;
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
        return wInitMean;
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
        return wInitSTD;
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
        return alphaPlus;
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
        return alphaMinus;
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
        return betaPlus;
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
        return betaMinus;
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

    public boolean isKeepWeightsOnRewind() {
        return keepWeightsOnRewind;
    }

    synchronized public void setKeepWeightsOnRewind(boolean keepWeightsOnRewind) {
        this.keepWeightsOnRewind = keepWeightsOnRewind;
    }
    // END keepWeightsOnRewind

    public boolean isDisplayNeuronStatistics() {
        return displayNeuronStatistics;
    }

    synchronized public void setDisplayNeuronStatistics(boolean displayNeuronStatistics) {
        this.displayNeuronStatistics = displayNeuronStatistics;
    }
    // END displayNeuronStatistics

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

    public boolean isNeuronFireHistogram() {
        return neuronFireHistogram;
    }

    synchronized public void setNeuronFireHistogram(boolean neuronFireHistogram) {
        this.neuronFireHistogram = neuronFireHistogram;
    }
    // END displayCombinedPolarity

} // END CLASS - StdpFeatureLearningI

