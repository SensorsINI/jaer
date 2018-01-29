/*
 * StdpFeatureLearningIII.java
 *
 * Created on March 4, 2013
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
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.eventio.AEFileInputStreamInterface;

@Description("Learns patterns in 2 layer Feedforward Neural Network around Rectangular Cluster Tracker")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class StdpFeatureLearningIII extends RectangularClusterTracker implements Observer, FrameAnnotater, PropertyChangeListener {

    // Controls
    protected int fireThres = getPrefs().getInt("StdpFeatureLearningIII.fireThres", 40000);

    {
        setPropertyTooltip("fireThres", "Threshold directly affecting selectivity of neuron");
    }

    protected int tLTP = getPrefs().getInt("StdpFeatureLearningIII.tLTP", 2000);

    {
        setPropertyTooltip("tLTP", "Size of temporal cluster to learn with a single neuron (us)");
    }

    protected int tRefrac = getPrefs().getInt("StdpFeatureLearningIII.tRefrac", 10000);

    {
        setPropertyTooltip("tRefrac", "Refractory period of neuron (us)");
    }

    protected int tInhibit = getPrefs().getInt("StdpFeatureLearningIII.tInhibit", 1500);

    {
        setPropertyTooltip("tInhibit", "Refractory period of neuron (us)");
    }

    protected int tauLeak = getPrefs().getInt("StdpFeatureLearningIII.tauLeak", 5000);

    {
        setPropertyTooltip("tauLeak", "Leak time constant (us)");
    }

    protected int wMin = getPrefs().getInt("StdpFeatureLearningIII.wMin", 1);

    {
        setPropertyTooltip("wMin", "Minimum weight");
    }

    protected int wMax = getPrefs().getInt("StdpFeatureLearningIII.wMax", 1000);

    {
        setPropertyTooltip("wMax", "Maximum weight");
    }

    protected int wInitMean = getPrefs().getInt("StdpFeatureLearningIII.wInitMean", 800);

    {
        setPropertyTooltip("wInitMean", "Initial weight mean");
    }

    protected int wInitSTD = getPrefs().getInt("StdpFeatureLearningIII.wInitSTD", 160);

    {
        setPropertyTooltip("wInitSTD", "Initial weight standard deviation");
    }

    protected int alphaPlus = getPrefs().getInt("StdpFeatureLearningIII.alphaPlus", 100);

    {
        setPropertyTooltip("alphaPlus", "Weight increment");
    }

    protected int alphaMinus = getPrefs().getInt("StdpFeatureLearningIII.alphaMinus", 50);

    {
        setPropertyTooltip("alphaMinus", "Weight decrement");
    }

    protected int betaPlus = getPrefs().getInt("StdpFeatureLearningIII.betaPlus", 0);

    {
        setPropertyTooltip("betaPlus", "Weight increment damping factor");
    }

    protected int betaMinus = getPrefs().getInt("StdpFeatureLearningIII.betaMinus", 0);

    {
        setPropertyTooltip("betaMinus", "Weight decrement damping factor");
    }

    protected boolean keepWeightsOnRewind = getPrefs().getBoolean("StdpFeatureLearningIII.keepWeightsOnRewind", true);

    {
        setPropertyTooltip("keepWeightsOnRewind", "Resets everything on loop in Playback of file except for synapse weights");
    }

    protected boolean displayNeuronStatistics = getPrefs().getBoolean("StdpFeatureLearningIII.displayNeuronStatistics", true);

    {
        setPropertyTooltip("displayNeuronStatistics", "Displays each Neurons weight matrix statistics: Mean, STD, Min, Max");
    }

    protected boolean fireMaxOnlyOnceOnSpike = getPrefs().getBoolean("StdpFeatureLearningIII.fireMaxOnlyOnceOnSpike", false);

    {
        setPropertyTooltip("fireMaxOnlyOnceOnSpike", "Input spike can only trigger at most one neuron to fire");
    }

    protected boolean displayCombinedPolarity = getPrefs().getBoolean("StdpFeatureLearningIII.displayCombinedPolarity", true);

    {
        setPropertyTooltip("displayCombinedPolarity", "Display Combined Polarities in Neuron Weight Matrix");
    }

    protected boolean neuronFireHistogram = getPrefs().getBoolean("StdpFeatureLearningIII.neuronFireHistogram", true);

    {
        setPropertyTooltip("neuronFireHistogram", "Draw a box around firing neuron with color corresponding to number of times its fired in given packet");
    }

    // Input
    private int xPixels;        // Number of pixels in x direction for input
    private int yPixels;        // Number of pixels in y direction for input
    private int numPolarities;  // Indicates total number of polarities of pixels

    // Neurons
    private int neuronsPerCluster;          // Number of Neurons in each cluster
    private int numClusters;                // Total number of clusters
    private int[] clusterID;                // Clusters ID number, used to distinguish between clusters that persist through time or new ones
    private boolean[] clusterActive;        // Indicates whether cluster is currently active
    private int[] xStart;                   // (0,0) Coordinate pixel for [cluster]
    private int[] yStart;                   // (0,0) Coordinate pixel for [cluster]
    private float[][] neuronPotential;      // Neuron Potential [cluster][neuron]
    private float[][][][][] synapseWeights; // Synaptic weights of [cluster][neuron][polarity][x][y]
    private int[][][][] pixelSpikeTiming;   // Last spike time of [cluster][polarity][x][y]
    private int[][] neuronFireTiming;       // Last fire time of [cluster][neuron]
    private int[][] neuronSpikeTiming;      // Last spike time of [cluster][neuron]
    private boolean[] neuronSpikeTimingInit;// Indicates whether variable has been initialized or needs to be reset for [cluster]
    private int[] t0;                       // Timestamp at which neurons are over lateral inhibition in [cluster]
    private int[] nextNeuronToUpdate;       // Helps indicates neuron in which to start update, is generally neuron next to one which fired last [cluster]
    private boolean[] fireInhibitor;        // Inhibits all other neurons in group from firing [group]
    private int[][] neuronFire;             // Indicates number of times a particular neuron has fired for a given time stamp or packet [cluster][neuron]
    private int[] numNeuronFire;            // Indicates number of times the neurons have fired for a given time stamp or packet [cluster]
    private boolean rewind;                 // Indicates rewind in playback

    // Listeners
    private boolean viewerPropertyChangeListenerInit; // Indicates that listener for viewer changes has been initialized - used to detect open file and rewind

    // Display Variables
    private GLU glu = null;                 // OpenGL Utilities
    private JFrame neuronFrame = null;      // Frame displaying neuron weight matrix
    private GLCanvas neuronCanvas = null;   // Canvas on which neuronFrame is drawn

    /**
     * Constructor
     *
     * @param chip Called with AEChip properties
     */
    public StdpFeatureLearningIII(AEChip chip) {
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
        super.initFilter();

        xPixels = 16;
        yPixels = xPixels;
        numPolarities = 2;

        neuronsPerCluster = 4;
        numClusters = getMaxNumClusters();
        clusterID = new int[numClusters];
        clusterActive = new boolean[numClusters];
        xStart = new int[numClusters];
        yStart = new int[numClusters];
        neuronPotential = new float[numClusters][neuronsPerCluster];
        synapseWeights = new float[numClusters][neuronsPerCluster][numPolarities][xPixels][yPixels];
        pixelSpikeTiming = new int[numClusters][numPolarities][xPixels][yPixels];
        neuronFireTiming = new int[numClusters][neuronsPerCluster];
        neuronSpikeTiming = new int[numClusters][neuronsPerCluster];
        neuronSpikeTimingInit = new boolean[numClusters];
        neuronFire = new int[numClusters][neuronsPerCluster];

        t0 = new int[numClusters];
        nextNeuronToUpdate = new int[numClusters];
        fireInhibitor = new boolean[numClusters];
        numNeuronFire = new int[numClusters];

        viewerPropertyChangeListenerInit = false;

        rewind = false;

        resetFilter();
    } // END METHOD

    /**
     * Called on filter reset which happens on creation of filter, on reset
     * button press, and on rewind Initializes all variables which aren't final
     * to their default values Note that synapseWeights are either initialized
     * to wInit parameters or left as they were previously
     */
    @Override
    public synchronized void resetFilter() {
        super.resetFilter();

        // Reset all cluster neuron variables
        for (int c = 0; c < numClusters; c++) {
            clusterActive[c] = false;
            clusterID[c] = c;
            resetCluster(c);
        }

        // Keep current weights on rewind if keepWeightsOnRewind is enabled
        // Otherwise initialize random weights according to parameters
        if (((keepWeightsOnRewind == true) && (rewind == true)) == false) {
            Random r = new Random(); // Used to generate random number for initial synapse weight
            for (int c = 0; c < numClusters; c++) {
                for (int n = 0; n < neuronsPerCluster; n++) {
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
                                synapseWeights[c][n][p][x][y] = (float) wInit;
                            } // END LOOP - All synapses
                        }
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
     * Resets all internal neuron variables of individual cluster used in STDP
     * Learning Called on reset or whenever a new cluster is found
     */
    private void resetCluster(int c) {
        neuronSpikeTimingInit[c] = false;

        t0[c] = 0;
        nextNeuronToUpdate[c] = 0;
        fireInhibitor[c] = false;
        numNeuronFire[c] = 0;

        for (int n = 0; n < neuronsPerCluster; n++) {
            neuronPotential[c][n] = 0;
            neuronFireTiming[c][n] = 0;
            neuronFire[c][n] = 0;
        } // END LOOP - Neurons

        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    pixelSpikeTiming[c][p][x][y] = 0;
                }
            }
        }
    } // END METHOD

    /**
     * Receives Packets of information and passes it onto processing
     *
     * @param in Input events can be null or empty.
     * @return The filtered events to be rendered
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        super.filterPacket(in);

        if (viewerPropertyChangeListenerInit == false) {
            chip.getAeViewer().addPropertyChangeListener(this);
            chip.getAeViewer().getAePlayer().getSupport().addPropertyChangeListener(this); // TODO might be duplicated callback
            viewerPropertyChangeListenerInit = true;
        }
        // Update cluster information once per packet
        // That is, update x and yStart variables and reset cluster information for clusters which aren't persistent
        // And determine which clusters are active for learning or initialization
        updateClusters();

        // Event Iterator - Write only relevant events inside xPixels by yPixels window to out
        // Apply STDP Rule
        for (Object o : in) {
            // Cast to PolarityEvent since we are interested in timestamp and polarity of spike
            PolarityEvent e = (PolarityEvent) o;
            // Assume that current event's timestamp is initial neuronSpikeTiming for all clusters that need to reset
            for (int c = 0; c < numClusters; c++) {
                if (clusterActive[c] == true) {
                    if (neuronSpikeTimingInit[c] == false) {
                        for (int n = 0; n < neuronsPerCluster; n++) {
                            neuronSpikeTiming[c][n] = e.timestamp;
                        }
                        neuronSpikeTimingInit[c] = true;
                    } else {
                        // Check what clusters the events belong to
                        if ((e.x >= xStart[c]) && (e.x < (xPixels + xStart[c]))
                                && (e.y >= yStart[c]) && (e.y < (yPixels + yStart[c]))) {
                            applySTDP(c, e.getTimestamp(), e.getX() - xStart[c], e.getY() - yStart[c], e.getType());
                        }
                    } // END IF
                } // END IF - Cluster Active
            } // END LOOP - numClusters
        } // END LOOP - Event Iterator

        // Draw Neuron Weight Matrix
        checkNeuronFrame();
        neuronCanvas.repaint();

        // Reset neuronFire count and array as well as clusterActive (to be reactivated next updateClusters())
        for (int c = 0; c < numClusters; c++) {
            numNeuronFire[c] = 0;
            clusterActive[c] = false;
            for (int n = 0; n < neuronsPerCluster; n++) {
                neuronFire[c][n] = 0;
            } // END LOOP
        } // END LOOP

        // Output Filtered Events
        return in;
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
     * Updates cluster internal variables based on new cluster information
     * Called by filterPacket every time new event packet comes in Updates
     * xStart and yStart variables for all clusters Resets cluster internal STDP
     * variables if new clusters are available
     */
    private void updateClusters() {
        Cluster[] tempCluster = new Cluster[numClusters];       // Holds visible clusters in clusters
        boolean[] tempClusterFound = new boolean[numClusters];  // Indicates if visible cluster ID in clusters has been found in clusterID
        boolean[] clusterFound = new boolean[numClusters];      // Indicates if clusterID has been found and updated

        // Initialize variables
        for (int c = 0; c < numClusters; c++) {
            tempClusterFound[c] = false;
            clusterFound[c] = false;
        } // END LOOP

        // Store all visible cluster IDs in an array
        // Store the number of available clusters
        int idx = 0;
        for (Cluster c : clusters) {
            if (c.isVisible()) {
                tempCluster[idx] = c;
                if (idx < numClusters) {
                    idx++;
                } else {
                    break;
                }
            }
        } // END LOOP

        // Search through visible clusters to see if it already exists in clusterID
        // Tag these clusters and update them
        for (int i = 0; i < idx; i++) {
            for (int cID = 0; cID < numClusters; cID++) {
                if (clusterID[cID] == tempCluster[i].getClusterNumber()) {
                    tempClusterFound[i] = true;
                    clusterFound[cID] = true;
                    xStart[cID] = (int) (tempCluster[i].getLocation().x - (xPixels / 2f));
                    yStart[cID] = (int) (tempCluster[i].getLocation().y - (yPixels / 2f));
                    clusterActive[cID] = true;
                } // END IF
            } // END LOOP - numClusters
        } // END LOOP - Visible Clusters

        // Search through visible clusters
        // If it has not been tagged
        // Then place it at first untagged location of clusterID and update/reset it
        // Mark as tagged
        for (int i = 0; i < idx; i++) {
            if (tempClusterFound[i] == false) {
                for (int cID = 0; cID < numClusters; cID++) {
                    if (clusterFound[cID] == false) {
                        tempClusterFound[i] = true;
                        clusterFound[cID] = true;
                        clusterID[cID] = tempCluster[i].getClusterNumber();
                        xStart[cID] = (int) (tempCluster[i].getLocation().x - (xPixels / 2f));
                        yStart[cID] = (int) (tempCluster[i].getLocation().y - (yPixels / 2f));
                        clusterActive[cID] = true;
                        resetCluster(cID);
                        break;
                    } // END IF - clusterFound
                } // END LOOP - numClusters
            } // END IF - tempClusterFound
        } // END LOOP - Visible Clusters

    } // END METHOD

    /**
     * Applies STDP Learning Rule
     *
     * @param e Polarity Events which are considered input spikes into the
     * neurons
     * @param e Polarity Events which are considered input spikes into the
     * neurons
     * @param e Polarity Events which are considered input spikes into the
     * neurons
     * @param e Polarity Events which are considered input spikes into the
     * neurons
     * @param e Polarity Events which are considered input spikes into the
     * neurons
     * @param e Polarity Events which are considered input spikes into the
     * neurons
     */
    private void applySTDP(int c, int ts, int x, int y, int polarity) {
        // If Neurons aren't inhibited
        if (ts >= t0[c]) {
            // Update all Neuron Integration states
            for (int nIdx = 0; nIdx < neuronsPerCluster; nIdx++) {
                // Start update from neuron next to the one that fired last
                int n = (nextNeuronToUpdate[c] + nIdx) % neuronsPerCluster;
                // Make sure neuron is not in its refractory period
                if (ts >= (neuronFireTiming[c][n] + tRefrac)) {
                    boolean potentialAboveThres = updateNeuronIntegrationState(c, n, ts, polarity, x, y);
                    // Only update synapses if fireInhibitor is disabled
                    // fireInhibitor will only be enabled if fireMaxOnlyOnceOnSpike is on
                    // and a neuron has already fired for the given input spike / event
                    if (fireInhibitor[c] == false) {
                        // If Neuron Fires Then
                        if (potentialAboveThres == true) {
                            // Indicate number of times neuron has fired in this particular data packet
                            neuronFire[c][n]++;
                            numNeuronFire[c]++;
                            // Update synapse weights of these neurons
                            updateSynapseWeights(c, n, ts);
                            // Inhibit all neurons
                            t0[c] = ts + tInhibit;
                            // Update neuron fire timing maps
                            neuronFireTiming[c][n] = ts;
                            // Update which neuron to start updating on next spike
                            nextNeuronToUpdate[c] = n + 1;
                            // If we allow neuron to fire only once per spike, then finish updating potentials for all neurons and inhibit firing
                            if (fireMaxOnlyOnceOnSpike == true) {
                                fireInhibitor[c] = true;
                            }
                        } // END IF - Fire
                    }
                } // END IF - Refractory period
            } // END LOOP - Neurons
        } // END IF - Inhibition
        // Make sure fireInhibitor is turned off after all neurons have been updated
        fireInhibitor[c] = false;
        // Update pixel spike timing maps
        pixelSpikeTiming[c][polarity][x][y] = ts;
    } // END METHOD

    /**
     * Updates Neuron Integration State every time there is a spike, tells
     * whether neuron fires
     *
     * @param group Current neuron group
     * @param neuron Current neuron
     * @param ts Current time stamp
     * @param polarity Current Polarity - On or Off
     * @param x X address of pixel / spike
     * @param y Y address of pixel / spike
     * @return boolean indicating whether neuron has fired
     */
    private boolean updateNeuronIntegrationState(int c, int neuron, int ts, int polarity, int x, int y) {
        // Neuron Update equation
        double temp = -(ts - neuronSpikeTiming[c][neuron]) / (double) tauLeak;
        neuronPotential[c][neuron] = (neuronPotential[c][neuron] * (float) Math.exp(temp)) + synapseWeights[c][neuron][polarity][x][y];
        neuronSpikeTiming[c][neuron] = ts;
        // If updated potential is above firing threshold, then fire and reset
        if (neuronPotential[c][neuron] >= fireThres) {
            neuronPotential[c][neuron] = 0;
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
    private void updateSynapseWeights(int c, int neuron, int ts) {
        // Update synapses for all polarities and pixels depending on STDP Rule
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    // LTP - Long Term Potentiation
                    if ((ts - pixelSpikeTiming[c][p][x][y]) <= tLTP) {
                        synapseWeights[c][neuron][p][x][y] = synapseWeights[c][neuron][p][x][y]
                                + (alphaPlus * (float) Math.exp((-betaPlus
                                        * (synapseWeights[c][neuron][p][x][y] - wMin)) / (double) (wMax - wMin)));
                        // Cut off at wMax
                        if (synapseWeights[c][neuron][p][x][y] > wMax) {
                            synapseWeights[c][neuron][p][x][y] = wMax;
                            // LTD - Long Term Depression
                        }
                    } else {
                        synapseWeights[c][neuron][p][x][y] = synapseWeights[c][neuron][p][x][y]
                                - (alphaMinus * (float) Math.exp((-betaMinus
                                        * (wMax - synapseWeights[c][neuron][p][x][y])) / (double) (wMax - wMin)));
                        // Cut off at wMin
                        if (synapseWeights[c][neuron][p][x][y] < wMin) {
                            synapseWeights[c][neuron][p][x][y] = wMin;
                        }
                    } // END IF - STDP Rule
                } // END IF - All synapses
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
        neuronFrame.setPreferredSize(new Dimension(800, 200));
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
                int neuronPadding = 5;
                int clusterPadding = 10;
                int xPixelsPerNeuron = xPixels + neuronPadding;
                int yPixelsPerNeuron = (yPixels * numPolarities) + neuronPadding;
                int xPixelsPerCluster = ((xPixelsPerNeuron * neuronsPerCluster) - neuronPadding) + clusterPadding;
                int yPixelsPerCluster = (yPixelsPerNeuron - neuronPadding) + clusterPadding;
                int pixelsPerRow = xPixelsPerCluster - clusterPadding;
                int pixelsPerColumn = (yPixelsPerCluster * numClusters) - clusterPadding;

                // Draw in canvas
                GL2 gl = drawable.getGL().getGL2();
                // Creates and scales drawing matrix so that each integer unit represents any given pixel
                gl.glLoadIdentity();
                gl.glScalef(drawable.getSurfaceWidth() / (float) pixelsPerRow,
                        drawable.getSurfaceHeight() / (float) pixelsPerColumn, 1);
                // Sets the background color for when glClear is called
                gl.glClearColor(0, 0, 0, 0);
                gl.glClear(GL.GL_COLOR_BUFFER_BIT);

                int clusterOffset;
                int neuronOffset;

                // Draw all Neurons
                for (int c = 0; c < numClusters; c++) {
                    // Adjust x Group Offset
                    clusterOffset = c * yPixelsPerCluster;
                    // Draw weight matrix
                    for (int n = 0; n < neuronsPerCluster; n++) {
                        // Adjust x and y Neuron Offsets
                        neuronOffset = n * xPixelsPerNeuron;
                        for (int x = 0; x < xPixels; x++) {
                            for (int y = 0; y < yPixels; y++) {
                                // Handle Polarity cases independently, not through a for loop
                                float wOFF = (synapseWeights[c][n][0][x][y] - wMin) / (wMax - wMin);
                                float wON = (synapseWeights[c][n][1][x][y] - wMin) / (wMax - wMin);
                                if (displayCombinedPolarity == true) {
                                    gl.glColor3f(wON, 0, wOFF);
                                    gl.glRectf(neuronOffset + x, clusterOffset + y + yPixels,
                                            neuronOffset + x + 1, clusterOffset + y + yPixels + 1);
                                } else if (displayCombinedPolarity == false) {
                                    gl.glColor3f(wOFF, wOFF, 0);
                                    gl.glRectf(neuronOffset + x, clusterOffset + y,
                                            neuronOffset + x + 1, clusterOffset + y + 1);
                                    gl.glColor3f(0, wON, wON);
                                    gl.glRectf(neuronOffset + x, clusterOffset + y + yPixels,
                                            neuronOffset + x + 1, clusterOffset + y + yPixels + 1);
                                } // END IF - Display Polarity
                            } // END LOOP - Y
                        } // END LOOP - X

                        // Display Neuron Statistics - Mean, STD, Min, Max
                        if (displayNeuronStatistics == true) {
                            final int font = GLUT.BITMAP_HELVETICA_12;
                            GLUT glut = chip.getCanvas().getGlut();
                            gl.glColor3f(1, 1, 1);
                            // Neuron info
                            gl.glRasterPos3f(neuronOffset, clusterOffset, 0);
                            glut.glutBitmapString(font, String.format("M %.2f", getNeuronMeanWeight(c, n)));
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
                            float color = neuronFire[c][n] / (float) numNeuronFire[c];
                            gl.glPushMatrix();
                            gl.glLineWidth(1f);
                            gl.glBegin(GL.GL_LINE_LOOP);
                            gl.glColor3f(color, color, color);
                            gl.glVertex2f(neuronOffset, clusterOffset);
                            gl.glVertex2f(neuronOffset, clusterOffset + (yPixels * 2));
                            gl.glVertex2f(neuronOffset + xPixels, clusterOffset + (yPixels * 2));
                            gl.glVertex2f(neuronOffset, clusterOffset + (yPixels * 2));
                            gl.glEnd();
                            gl.glPopMatrix();
                        } // END IF

                    } // END LOOP - Neuron
                } // END LOOP - GroupX

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
            } // END METHOD

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
     * Called when filter is turned off makes sure neuronFrame gets turned off
     */
    @Override
    public synchronized void cleanup() {
        if (neuronFrame != null) {
            neuronFrame.dispose();
        }
    } // END METHOD

    /**
     * Resets the filter Hides neuronFrame if filter is not enabled
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
     * Annotation or drawing method
     *
     * @param drawable OpenGL Rendering Object
     */
    @Override
    public synchronized void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);

        if (!isAnnotationEnabled()) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }

        // Draw Box around relevant pixels with label of cluster number
        for (int c = 0; c < numClusters; c++) {
            //if (clusterActive[c] == true) {
            // Box
            gl.glPushMatrix();
            gl.glLineWidth(1f);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glColor3f(1f, 1f, 1f);
            gl.glVertex2f(xStart[c] - 1, yStart[c] - 1);
            gl.glVertex2f(xStart[c] + xPixels, yStart[c] - 1);
            gl.glVertex2f(xStart[c] + xPixels, yStart[c] + yPixels);
            gl.glVertex2f(xStart[c] - 1, yStart[c] + yPixels);
            gl.glEnd();
            gl.glPopMatrix();
            // Label
            final int font = GLUT.BITMAP_HELVETICA_18;
            GLUT glut = chip.getCanvas().getGlut();
            gl.glColor3f(1f, 1f, 1f);
            gl.glRasterPos3f(xStart[c], yStart[c], 0);
            glut.glutBitmapString(font, String.format("%d", c));
            //}
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
        super.update(o, arg);
        if ((arg != null) && ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY))) {
            initFilter();
        }
    } // END METHOD

    /**
     * Overrides setMaxNumClusters to make sure that all variables and arrays
     * are reallocated
     *
     * @param maxNumClusters Maximum number of visible clusters
     */
    @Override
    public void setMaxNumClusters(final int maxNumClusters) {
        super.setMaxNumClusters(maxNumClusters);
        // Set numCluster and all its variable
        initFilter();
    }

    /**
     * Returns Mean of Neuron's weights
     *
     * @param neuron Current neuron
     * @return mean
     */
    public float getNeuronMeanWeight(int c, int neuron) {
        float mean = 0;
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    mean += synapseWeights[c][neuron][p][x][y];
                }
            }
        }
        return mean / (numPolarities * xPixels * yPixels);
    } // END METHOD

    /**
     * Returns Standard Deviation of Neuron's weights
     *
     * @param group Current group
     * @param neuron Current neuron
     * @return standard deviation
     */
    public float getNeuronSTDWeight(int c, int neuron) {
        float mean = getNeuronMeanWeight(c, neuron);
        float var = 0;
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    var += (mean - synapseWeights[c][neuron][p][x][y]) * (mean - synapseWeights[c][neuron][p][x][y]);
                }
            }
        }
        double std = Math.sqrt(var / (double) (numPolarities * xPixels * yPixels));
        return (float) std;
    } // END METHOD

    /**
     * Returns Minimum of Neuron's weights
     *
     * @param group Current group
     * @param neuron Current neuron
     * @return minimum
     */
    public float getNeuronMinWeight(int c, int neuron) {
        float min = synapseWeights[c][neuron][0][0][0];
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    if (min > synapseWeights[c][neuron][p][x][y]) {
                        min = synapseWeights[c][neuron][p][x][y];
                    }
                }
            }
        }
        return min;
    } // END METHOD

    /**
     * Returns Maximum of Neuron's weights
     *
     * @param group Current group
     * @param neuron Current neuron
     * @return maximum
     */
    public float getNeuronMaxWeight(int c, int neuron) {
        float max = synapseWeights[c][neuron][0][0][0];
        for (int p = 0; p < numPolarities; p++) {
            for (int x = 0; x < xPixels; x++) {
                for (int y = 0; y < yPixels; y++) {
                    if (max < synapseWeights[c][neuron][p][x][y]) {
                        max = synapseWeights[c][neuron][p][x][y];
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
        getPrefs().putInt("StdpFeatureLearningIII.fireThres", fireThres);
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
        getPrefs().putInt("StdpFeatureLearningIII.tLTP", tLTP);
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
        getPrefs().putInt("StdpFeatureLearningIII.tRefrac", tRefrac);
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
        getPrefs().putInt("StdpFeatureLearningIII.tInhibit", tInhibit);
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
        getPrefs().putInt("StdpFeatureLearningIII.tauLeak", tauLeak);
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
        getPrefs().putInt("StdpFeatureLearningIII.wMin", wMin);
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
        getPrefs().putInt("StdpFeatureLearningIII.wMax", wMax);
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
        getPrefs().putInt("StdpFeatureLearningIII.wInitMean", wInitMean);
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
        getPrefs().putInt("StdpFeatureLearningIII.wInitSTD", wInitSTD);
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
        getPrefs().putInt("StdpFeatureLearningIII.alphaPlus", alphaPlus);
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
        getPrefs().putInt("StdpFeatureLearningIII.alphaMinus", alphaMinus);
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
        getPrefs().putInt("StdpFeatureLearningIII.betaPlus", betaPlus);
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
        getPrefs().putInt("StdpFeatureLearningIII.betaMinus", betaMinus);
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

