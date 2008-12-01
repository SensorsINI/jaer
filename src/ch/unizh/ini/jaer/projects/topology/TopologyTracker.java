/*
 * TopologyTracker.java
 *
 * contains the classes TopologyTracker and TopologyTracker.Monitor.
 * A TopologyTracker makes a guess of the adjacency matrix of the pixels
 * by tracking the events coming from the pixels.
 * The monitor is used to display the algorithms progress and numEventsProcessed state.
 *
 * Semester project Matthias Schrag, HS07
 */
package ch.unizh.ini.jaer.projects.topology;

import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.VectorFieldChart;
import net.sf.jaer.util.chart.VectorSeries;
import net.sf.jaer.util.chart.XYChart;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.chart.Series;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Vector;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Learns neighborhood pixel topology based on event timing. When this filter runs, it starts with random
 * connections between the input addresses and the the output. Temporal correlation between incoming events
 * gradually maps neighbors to neighbors, resulting in a (hopefully) coherent output map after a while.
 * 
 * @author Matthias Schrag, Kynan Eng, Tobi Delbruck
 */
public class TopologyTracker extends EventFilter2D implements Observer {

    TopologyTrackerControl resetButton = null;

    public static String getDescription() {
        return "Learns a topological mapping from input events using neighbor correlation";
    }
    protected static final int DEFAULT_NEIGHBORHOOD_SIZE = 4;
    protected static final int DEFAULT_MAX_SQUARED_NEIGHBORHOOD_DISTANCE = 1;
    protected static final float DEFAULT_LEARNING_WINDOW_CENTER = 5.0f; // 5 ms per default
    protected static final float DEFAULT_LEARNING_WINDOW_STANDARD_DEVIATION = 1.0f; // 1 ms standard deviation
    protected static final int MAX_SIZE_X = 80;
    protected static final int MAX_SIZE_Y = 80;
    protected static final int BUFFER_SIZE = 1000; // the number of events in event window
    protected static final int NO_LABEL = -1;
    protected static final int NO_TIMESTAMP = Integer.MIN_VALUE;
    protected static final byte NO_TYPE = -1;
    protected static final float INITIAL_WEIGHT = 1.0f;
    protected static final float BELL_CURVE_SIZE = 2.5f;
    protected static final int BELL_CURVE_RESOLUTION = 1000;
    protected static final float[] BELL_CURVE = new float[(int) (BELL_CURVE_SIZE * BELL_CURVE_RESOLUTION)];   // the Gaussian in range [ 0 .. 2.5sigma [
    /**
     * Event source data structures.
     */
    protected int sizeX,  sizeY; // needed for labelling
    protected int minX,  minY;   // needed to filter out subrange
    protected int maxX,  maxY;   // needed to filter out subrange
    /**
     * The event window with fixed events count <code>windowSize</code>.
     * An event consists of its source and timestamp, both stored in simultaneous arrays.
     * <code>eventsIndex</code> denotes the index of the numEventsProcessed event.
     */
    protected int[] eventsSource;    // the labels of the nodes in event window
    protected int[] eventsTimestamp;    // the timestamp of the events in event window
    protected byte[] eventsType;    // the type of the event: ON|OFF
    protected int eventWindowBegin = 1;
    protected int eventWindowEnd = BUFFER_SIZE;
    protected int eventIndex;   // the index of the inserted event
    protected long numEventsProcessed; // the number of events passed
    /**
     * Algorithm parameters.
     */
    protected int learningWindowShape;
    protected float learningWindowMean;
    protected float learningWindowStandardDeviation;
    protected int neighborhoodSize;
    protected boolean inhibit2ndOrderNeighbors;
    protected boolean symmetricPlasticityChange;
    protected float reinforcement;
    private boolean ignoreReset;
    private boolean mapEventsToLearnedTopologyEnabled;
    private boolean learningEnabled; // used to turn off learning for display of unlearned topology
    /**
     * Monitor parameters.
     */
    protected int maxSquaredNeighborDistance;
    protected boolean showStatus;
    protected boolean showFalseEdges;
    protected boolean onResetWriteStatsAndExit;
    /**
     * Algorithm data structures.
     */
    protected float[][] weights;  // the adjacency guess
    protected int[][] neighbors;  // the current neighbors guess
    protected float learningWindowBegin;
    protected float learningWindowEnd;
    /**
     * The monitor and stat data.
     */
    protected Monitor monitor;
    protected Vector<String> params;
    protected Vector<String> stat;
    protected ArrayList<Float> utilizationStat;
    protected long startTime;
    protected JFrame window;
    volatile private boolean resetFlag = true; // this flag is used to trigger a reallyDoReset in the filterPacket method, to avoid threading issues with asynchronous reallyDoReset and deadlock
    private boolean initialized = false;

    /**
     * Create a new TopologyTracker
     */
    public TopologyTracker(AEChip chip) {
        super(chip);
        this.chip = chip;

        /* read alogorithm parameters */
        neighborhoodSize = getPrefs().getInt("TopologyTracker.neighborhoodSize", DEFAULT_NEIGHBORHOOD_SIZE);
        setPropertyTooltip("neighborhoodSize", "Number of neighbors a single pixel can have.");
        maxSquaredNeighborDistance = getPrefs().getInt("TopologyTracker.maxSquaredNeighborDistance", DEFAULT_MAX_SQUARED_NEIGHBORHOOD_DISTANCE);
        setPropertyTooltip("maxSquaredNeighborDistance", "[monitor] Maximum squared distance between two neighbors. If neighborhoodSize is 4: choose 1; if 8: choose 2.");

        learningWindowShape = getPrefs().getInt("TopologyTracker.learningWindowShape", 2);
        setPropertyTooltip("learningWindowShape", "Shape of the learning window [0=Gaussian 1=Triangle 2=Rectangle].");
        learningWindowMean = getPrefs().getFloat("TopologyTracker.learningWindowMean", DEFAULT_LEARNING_WINDOW_CENTER);
        setPropertyTooltip("learningWindowMean", "Center of the learning window [ms].");
        learningWindowStandardDeviation = getPrefs().getFloat("TopologyTracker.learningWindowStandardDeviation", DEFAULT_LEARNING_WINDOW_STANDARD_DEVIATION);
        setPropertyTooltip("learningWindowStandardDeviation", "Width of the learning window [ms].");
        updateLearningWindow();

        symmetricPlasticityChange = getPrefs().getBoolean("TopologyTracker.symmetricPlasticityChange", true);
        setPropertyTooltip("symmetricPlasticityChange", "Update also symmetric edge in adjacency matrix.");
        inhibit2ndOrderNeighbors = getPrefs().getBoolean("TopologyTracker.inhibit2ndOrderNeighbors", false);
        setPropertyTooltip("inhibit2ndOrderNeighbors", "Check before weight update if potential neighbor is guessed to be a 2nd-order neighbor.");
        reinforcement = getPrefs().getFloat("TopologyTracker.reinforcement", 0.0f);
        setPropertyTooltip("reinforcement", "Reinforce edge weights of neighbors in current guess.");
        ignoreReset = getPrefs().getBoolean("TopologyTracker.ignoreReset", false);
        setPropertyTooltip("ignoreReset", "Do not reset the filter upon reset events (but eventially write stats).");

        showStatus = getPrefs().getBoolean("TopologyTracker.showStatus", true);
        setPropertyTooltip("showStatus", "[monitor] Show the algorithms status in a frame.");
        showFalseEdges = getPrefs().getBoolean("TopologyTracker.showFalseEdges", true); // start with display of false edges
        setPropertyTooltip("showFalseEdges", "[monitor] Show the false edges in a diagram.");
        onResetWriteStatsAndExit = getPrefs().getBoolean("TopologyTracker.onResetWriteStatsAndExit", false);
        setPropertyTooltip("onResetWriteStatsAndExit", "[stat] Log stat data to file.");

        mapEventsToLearnedTopologyEnabled = getPrefs().getBoolean("TopologyTracker.mapEventsToLearnedTopologyEnabled", true);
        {
            setPropertyTooltip("mapEventsToLearnedTopologyEnabled", "[monitor] remap output events to learned topology");
        }

        learningEnabled = getPrefs().getBoolean("TopologyTracker.learningEnabled", true);
        {
            setPropertyTooltip("learningEnabled", "[monitor] enables learning, use to freeze state");
        }

        chip.addObserver(this);
        monitor = new Monitor();
        makeStatusWindow();
        resetButton = new TopologyTrackerControl(this);
    }

    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        synchronized (monitor) {
            super.setFilterEnabled(yes);
            resetButton.setVisible(yes);
            if(!yes){
                initialized=false;
                freeMemory();
            }
        }
    }

    public void initFilter() {
        //return; // to avoid calling the expensive init, we do this in the filterPacket if needed
    }

    /**
     * this should allocate and initialize memory:
     * it may be called when the chip e.g. size parameters are changed after creation of the filter
     */
    private void initFilter(int sx, int sy) {
        /* set up constants */
        if (sx * sy == 0) {
            return; // no chip yet
//       log.info("initializing for chip=" + getChip());
//       initFilter(chip.getSizeX(), chip.getSizeY());       
        }
 
        if(sx>MAX_SIZE_X) sx=MAX_SIZE_X;
        if(sy>MAX_SIZE_Y) sy=MAX_SIZE_Y;
        sizeX=sx;
        sizeY=sy;
        double x = 0.0f;
        for (int i = 0; i < BELL_CURVE.length; i++) {
            BELL_CURVE[i] = (float) (Math.exp(-x * x / 2) / Math.sqrt(2 * Math.PI));
            x += 1.0f / BELL_CURVE_RESOLUTION;
        }

        /* set range */
//        if (sx > MAX_SIZE_X) {
            minX = (chip.getSizeX()-sizeX) / 2;
//            sizeX = MAX_SIZE_X; // limit sizeX
//        } else {
//            sizeX=sx;
//            minX = 0;
//        }
        maxX = minX + sizeX;
//        if (sy > MAX_SIZE_Y) {
            minY = (chip.getSizeY() - sizeY) / 2;
//            sizeY = MAX_SIZE_Y; // limit sizeY
//        } else {
//            sizeY=sy;
//            minY = 0;
//        }
        maxY = minY + sizeY;

        /* create model */
        int n = sizeX * sizeY;
        try {
            weights = new float[n][n]; // TODO HUGE array!!!
            neighbors = new int[n][neighborhoodSize + 1];    // last element is dummy
            // create event arrays
            eventsSource = new int[BUFFER_SIZE];
            eventsTimestamp = new int[BUFFER_SIZE];
            eventsType = new byte[BUFFER_SIZE];
        } catch (OutOfMemoryError e) {
            System.gc();
            log.warning(e.toString() + ": not enough memory for neighbors array with sizeX="+sizeX+" sizeY="+sizeY+", reducing sizeX and sizeY");
            initFilter((3 * sizeX) / 4, (3 * sizeY) / 4); // recurse
        }
        if (ignoreReset) {
            reallyDoReset();   // ignore reallyDoReset events, so do it now...
        }
        initialized = true;
        log.info(String.format("initialized with region sized sx,sy=%d,%d",sizeX,sizeY));
    }

    /**
     * should reallyDoReset the filter to initial state
     */
    @Override
    public void resetFilter() {
        resetFlag = true;
    }
    
    synchronized private void freeMemory(){
        weights=null;
        neighbors=null;
        eventsSource=null;
        eventsTimestamp=null;
        eventsType=null;
        System.gc();
    }

    private void maybeDoReset() {
        if (!resetFlag) {
            return;
        }
        resetFlag = false;
        /* write stats */
        if (onResetWriteStatsAndExit) {
            monitor.writeStat();
        }
        if (ignoreReset) {
            return;
        // body
        }
        reallyDoReset();
    }

    /**
     * Reset the filter.
     */
    private void reallyDoReset() {
        Arrays.fill(eventsSource, NO_LABEL);
        Arrays.fill(eventsTimestamp, NO_TIMESTAMP);
        Arrays.fill(eventsType, NO_TYPE);
        eventIndex = 0;
        Random random = new Random();
        int n = sizeX * sizeY;
        for (int i = 0; i < weights.length; i++) {
            /* init weight */
            Arrays.fill(weights[i], INITIAL_WEIGHT);
            /* init neighbors randomly */
            for (int rank = 0; rank < neighborhoodSize; rank++) {
                int j;
                int r;
                do {
                    j = random.nextInt(n - 1);
                    if (j >= i) {
                        j++;   // random neighbor (without node itself)
                    }
                    for (r = 0; r < rank && neighbors[i][r] != j; r++) {
                    }    // check for collision
                } while (r < rank); // repeat while collision
                neighbors[i][rank] = j;
            }
        }
        monitor.init();
    }

    /**
     * should return the filter state in some useful form
     * @deprecated - no one uses this
     */
    public Object getFilterState() {
        return null;
    }

    /**
     * Calculates the learning window begin and end.
     */
    protected void updateLearningWindow() {
        float halfWidth = learningWindowStandardDeviation;
        switch (learningWindowShape) {
            case 0:
                halfWidth = learningWindowStandardDeviation * BELL_CURVE_SIZE;
                break; // Gaussian (for 99.7%)
            case 1:
                halfWidth = learningWindowStandardDeviation * (float) Math.sqrt(6);
                break; // Triangle
            case 2:
                halfWidth = learningWindowStandardDeviation * (float) Math.sqrt(3);
                break; // Rectangle
            default:
        }
        learningWindowBegin = learningWindowMean - halfWidth;
        learningWindowEnd = learningWindowMean + halfWidth;
    }

    /**
     * Core method:
     * add new events to queue, learn topology
     */
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
            return in;
        }
        if (!initialized) {
            initFilter(chip.getSizeX(), chip.getSizeY());
        }
        maybeDoReset();
        makeStatusWindow();
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        assert in != null;

        monitor.enter(in.getSize(), in.getLastTimestamp() - in.getFirstTimestamp());    // begin monitored sequence
        OutputEventIterator outItr = null;
        if (mapEventsToLearnedTopologyEnabled) {
            checkOutputPacketEventType(in.getEventClass());
            outItr = out.outputIterator();
        }
        for (BasicEvent event : in) {
            if (event.x < minX || event.x >= maxX || event.y < minY || event.y >= maxY) {
                continue;   // treat only events inside borders
//            long start = System.nanoTime();
            /* create event (timestamp, label, type) */
            }
            int t = event.timestamp;
            int i = (event.x - minX) * sizeY + (event.y - minY);
            byte type = (event instanceof TypedEvent) ? ((TypedEvent) event).type : NO_TYPE;
            numEventsProcessed++;

            if (learningEnabled) {
                /* learn topology */
//                int endIndex = (eventIndex + eventWindowEnd) % BUFFER_SIZE;
                // iterate over previous events...
                for (int index = eventWindowBegin; index < eventWindowEnd; index++) {
                    int current = (eventIndex + index) % BUFFER_SIZE;
                    if (eventsType[current] != type) {
                        continue;   // weight change only if events have same type
                    // set aliases
                    }
                    int timestamp = eventsTimestamp[current];
                    float dt = (t - timestamp) / 1000.0f;  // convert from microseconds to milliseconds
                    if (dt < learningWindowBegin) {
                        continue;
                    }
                    if (dt >= learningWindowEnd) {
                        break;
                    }
                    int j = eventsSource[current];
                    if (i == j) {
                        continue;
                    }
                    /* adapt weight */
                    float weightChange = calculateWeightChange(weights[i][j], dt);
                    // check if higher order neighbor
                    if (inhibit2ndOrderNeighbors) {
                        for (int rank = 0; rank < neighborhoodSize; rank++) {
                            int direct = neighbors[i][rank];
                            for (int r = 0; rank < neighborhoodSize; rank++) {
                                if (neighbors[direct][r] == j) {
                                    weightChange = 0.0f;
                                }
                            }
                        }
                    }
                    adaptWeight(i, j, weightChange);
                    if (symmetricPlasticityChange) {
                        adaptWeight(j, i, weightChange);
                    }
                    // reinforce winners
                    if (reinforcement > 0) {
                        for (int rank = 0; rank < neighborhoodSize; rank++) {
                            int neighbor = neighbors[i][rank];
                            weights[i][neighbor] += reinforcement;  // adapt weight; does not disturb ranking of neighbors
                        }
                    }
                } // TODO: adapt eventWindowSize to real time performance

                /* replace oldest event by numEventsProcessed event */
                eventIndex = (eventIndex - 1 + BUFFER_SIZE) % BUFFER_SIZE;  // eventIndex-- (BUFFER_SIZE)
                eventsTimestamp[eventIndex] = t;
                eventsSource[eventIndex] = i;
                eventsType[eventIndex] = type;
            } // learningEnabled

            if (mapEventsToLearnedTopologyEnabled) {
                for (int ind = 0; ind < neighborhoodSize; ind++) {
                    PolarityEvent eo = (PolarityEvent) outItr.nextOutput();
                    eo.copyFrom(event);
                    int n = neighbors[i][ind]; // neighbor
                    eo.x = (short) (n / sizeY + minX);
                    eo.y = (short) (n % sizeY + minY);
                }
            }
        }

        monitor.exit();
        if (!mapEventsToLearnedTopologyEnabled) {
            return in;
        } else {
            return out;
        }
    }

    public int getNeighborhoodSize() {
        return neighborhoodSize;
    }

    synchronized public void setNeighborhoodSize(int value) {
        if (value != neighborhoodSize) {
            initialized = false;
        }
        getPrefs().putInt("TopologyTracker.neighborhoodSize", value);
        support.firePropertyChange("neighborhoodSize", neighborhoodSize, value);
        neighborhoodSize = value;
    }

    public int getLearningWindowShape() {
        return learningWindowShape;
    }

    synchronized public void setLearningWindowShape(int value) {
        getPrefs().putInt("TopologyTracker.learningWindowShape", value);
        support.firePropertyChange("learningWindowShape", learningWindowShape, value);
        learningWindowShape = value;
        updateLearningWindow();
    }

    public float getLearningWindowMean() {
        return learningWindowMean;
    }

    synchronized public void setLearningWindowMean(float value) {
        getPrefs().putFloat("TopologyTracker.learningWindowMean", value);
        support.firePropertyChange("learningWindowMean", learningWindowMean, value);
        learningWindowMean = value;
        updateLearningWindow();
    }

    public float getLearningWindowStandardDeviation() {
        return learningWindowStandardDeviation;
    }

    synchronized public void setLearningWindowStandardDeviation(float value) {
        getPrefs().putFloat("TopologyTracker.learningWindowStandardDeviation", value);
        support.firePropertyChange("learningWindowStandardDeviation", learningWindowStandardDeviation, value);
        learningWindowStandardDeviation = value;
        updateLearningWindow();
    }

    public boolean getInhibit2ndOrderNeighbors() {
        return inhibit2ndOrderNeighbors;
    }

    synchronized public void setInhibit2ndOrderNeighbors(boolean value) {
        getPrefs().putBoolean("TopologyTracker.inhibit2ndOrderNeighbors", value);
        support.firePropertyChange("inhibit2ndOrderNeighbors", inhibit2ndOrderNeighbors, value);
        inhibit2ndOrderNeighbors = value;
    }

    public boolean getSymmetricPlasticityChange() {
        return symmetricPlasticityChange;
    }

    public void setSymmetricPlasticityChange(boolean value) {
        getPrefs().putBoolean("TopologyTracker.symmetricPlasticityChange", value);
        support.firePropertyChange("symmetricPlasticityChange", symmetricPlasticityChange, value);
        symmetricPlasticityChange = value;
    }

    public float getReinforcement() {
        return reinforcement;
    }

    synchronized public void setIgnoreReset(boolean value) {
        getPrefs().putBoolean("TopologyTracker.ignoreReset", value);
        support.firePropertyChange("ignoreReset", ignoreReset, value);
        ignoreReset = value;
    }

    public boolean getIgnoreReset() {
        return ignoreReset;
    }

    public void setReinforcement(float value) {
        getPrefs().putFloat("TopologyTracker.reinforcement", value);
        support.firePropertyChange("reinforcement", reinforcement, value);
        reinforcement = value;
    }

    public boolean isShowStatus() {
        return showStatus;
    }

    public void setShowStatus(boolean value) {
        getPrefs().putBoolean("TopologyTracker.showStatus", value);
        support.firePropertyChange("showStatus", showStatus, value);
        showStatus = value;
        if (window != null) {
            window.setVisible(showStatus); // will get set visible next time around loop
        }
    }

    public boolean isShowFalseEdges() {
        return showFalseEdges;
    }

    public void setShowFalseEdges(boolean value) {
        getPrefs().putBoolean("TopologyTracker.showFalseEdges", value);
        support.firePropertyChange("showFalseEdges", showFalseEdges, value);
        showFalseEdges = value;
        if (showFalseEdges) {
            for (int i = 0; i < neighbors.length; i++) {
                monitor.neighborhoodChanged(i, neighbors[i]);
            }
        }
    }

    public boolean isOnResetWriteStatsAndExit() {
        return onResetWriteStatsAndExit;
    }

    public void setOnResetWriteStatsAndExit(boolean value) {
        getPrefs().putBoolean("TopologyTracker.onResetWriteStatsAndExit", value);
        support.firePropertyChange("onResetWriteStatsAndExit", onResetWriteStatsAndExit, value);
        onResetWriteStatsAndExit = value;
    }

    public void update(Observable o, Object arg) {
        initialized = false;
    }

    /**
     * Change the weight(i,j) by a certain non-negative <code>amount</code>.
     *
     * Guess for neighbors (neighbors with highest weights) is recalculated.
     */
    private void adaptWeight(int i, int j, float amount) {
        /* precondition */
        assert 0 <= i && i < weights.length;    // i valid node label
        assert 0 <= j && j < weights[i].length; // j valid node label
        assert amount >= 0;                     // amount non-negative

        /* raise weight by amount */
        weights[i][j] += amount;

        /* reestablish ranking among guessed neighbors of i */
        int[] neighbor = neighbors[i];
        float[] weight = weights[i];
        int rank;
        int higher, lower;
        neighbor[neighborhoodSize] = j;  // dummy at end
        for (rank = 0; neighbor[rank] != j; rank++) {
        }   // find node j in neighbors
        // re-sort neighbors in ranking
        while (rank > 0 && weight[(lower = neighbor[rank - 1])] < weight[(higher = neighbor[rank])]) {
            neighbor[rank - 1] = higher;
            neighbor[rank] = lower;    // swap rank of competing neighbors
            rank--;
        }   // post: forall j: weight[neighbor[j-1]] >= weight[neighbor[j]]
        if (rank < neighborhoodSize) { // if neighbor is new...
            monitor.neighborChanged(i, j, neighbor, rank);  // ...monitor correctness of new guess
            if (showStatus && showFalseEdges) {
                monitor.neighborhoodChanged(i, neighbor);
            }
        }
    }

    /**
     * Calculate a weight change depending on timestamp differences
     */
    private float calculateWeightChange(float weight, float dt) {
        if (dt < 0) {
            return 0.0f;
        }
        float halfWidth = learningWindowMean - learningWindowBegin;
        switch (learningWindowShape) {
            case 0: // Gaussian
                float x = Math.abs((dt - learningWindowMean) / learningWindowStandardDeviation);
                int i = (int) (x * BELL_CURVE_RESOLUTION);
                if (i < BELL_CURVE.length) {
                    return BELL_CURVE[i] / learningWindowStandardDeviation;
                } else {
                    return 0.0f;   // round to zero for dt - mu >= BELL_CURVE_SIZE*sigma (==2.5*sigma)
                }
            case 1: // Triangle
                if (dt < learningWindowMean) {
                    return (dt - learningWindowBegin) / halfWidth / halfWidth;
                } else {
                    return (learningWindowEnd - dt) / halfWidth / halfWidth;
                }
            case 2: // Rectangle
                return 0.5f / halfWidth;
            default:
                return 0.0f;
        }
    }

    private void makeStatusWindow() throws HeadlessException {
        if (!isFilterEnabled() | !isShowStatus()) {
            return;
        }
        if (window != null) {
            if (!window.isVisible()) {
                window.setVisible(true);
            }
            return;
        }
        window = new JFrame("Topology Learning Progress");
        //window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.setPreferredSize(new java.awt.Dimension(500, 300));
//        window.setLocation(200, 100); // TODO
        window.setLayout(new BorderLayout());
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 1));
        panel.add(monitor.progressChart);
        panel.add(monitor.utilizationChart);
        window.getContentPane().add(BorderLayout.CENTER, panel);
        window.getContentPane().add(BorderLayout.EAST, monitor.vectorChart);
        window.pack();
        window.setVisible(showStatus);
    }

    public boolean isMapEventsToLearnedTopologyEnabled() {
        return mapEventsToLearnedTopologyEnabled;
    }

    synchronized public void setMapEventsToLearnedTopologyEnabled(boolean mapEventsToLearnedTopologyEnabled) {
        support.firePropertyChange("mapEventsToLearnedTopologyEnabled", this.mapEventsToLearnedTopologyEnabled, mapEventsToLearnedTopologyEnabled);
        this.mapEventsToLearnedTopologyEnabled = mapEventsToLearnedTopologyEnabled;
        getPrefs().putBoolean("TopologyTracker.mapEventsToLearnedTopologyEnabled", mapEventsToLearnedTopologyEnabled);
    }

    public boolean isLearningEnabled() {
        return learningEnabled;
    }

    synchronized public void setLearningEnabled(boolean learningEnabled) {
        support.firePropertyChange("learningEnabled", this.learningEnabled, learningEnabled);
        this.learningEnabled = learningEnabled;
        getPrefs().putBoolean("TopologyTracker.learningEnabled", learningEnabled);
    }

    /**
     * The Monitor class.
     *
     * A monitor knows the structure of the pixels in ahead and can therefore display the progress.
     *
     * Remark: The outer class (TopologyTracker) does not access any non-public fields/methods.
     */
    public class Monitor {

        public final int DISPLAY_FREQUENCY = 30;
        private int inputDuration;  // numEventsProcessed time span of input timestamps
        private int referenceTime;  // numEventsProcessed begin time in microseconds
        private int displayTime = Integer.MIN_VALUE;
        private float correctNeighbors;
        private float totalNeighbors;
        private float currentBest;
        private float outstanding;
        /** The monitor's view */
        public float time = 0;
        public Series progress;
        public Series utilization;
        public VectorSeries[] rankedErrors;
        private Category progressCurve;
        private Category utilizationCurve;
//        private Category windowSizeCurve;
        private Category[] rankedErrorVectors;
        private Axis timeAxis;
        private Axis xAxis;
        private Axis yAxis;
        /** The charts */
        public XYChart progressChart;
        public XYChart utilizationChart;
        public VectorFieldChart vectorChart;

        /**
         * Create a new Monitor
         */
        public Monitor() {
            progress = new Series(2);
            utilization = new Series(2);
            rankedErrors = new VectorSeries[neighborhoodSize];

            timeAxis = new Axis(-1000, 0);  // display 1000 event packets
            timeAxis.setTitle("t");
            timeAxis.setUnit("ms");
            Axis ratio = new Axis(0, 1);
            ratio.setUnit("%");
            ratio.setTitle("");
            xAxis = new Axis();
            yAxis = new Axis();

            progressCurve = new Category(progress, new Axis[]{timeAxis, ratio});
            progressCurve.setColor(new float[]{0.0f, 0.0f, 1.0f});
            utilizationCurve = new Category(utilization, new Axis[]{timeAxis, ratio});
            utilizationCurve.setColor(new float[]{0.8f, 0.8f, 0.8f});
            rankedErrorVectors = new Category[rankedErrors.length];

            progressChart = new XYChart("Progress");
            progressChart.addCategory(progressCurve);
            utilizationChart = new XYChart("Utilization");
            utilizationChart.addCategory(utilizationCurve);
            vectorChart = new VectorFieldChart("Errors");

            progressChart.setToolTipText("Shows the learning progress: the number of correct neighbors divided by the total number");
            utilizationChart.setToolTipText("Shows the CPU utilization of of the processing: the time spent processing divided by the real duration");
            vectorChart.setToolTipText("Shows bad topology mappings");
        }

        public void init() {
            // init statistics
            params = new Vector<String>();
            stat = new Vector<String>();
            utilizationStat = new ArrayList<Float>();
            startTime = System.currentTimeMillis();
            vectorChart.clear();

            // remember sizeX and sizeY are unknown upon construction: set it now
            xAxis.setRange(-0.5, sizeX - 0.5);
            yAxis.setRange(-0.5, sizeY - 0.5);
            for (int i = 0; i < rankedErrors.length; i++) {
                rankedErrors[i] = new VectorSeries(sizeX, sizeY);
            }
            for (int i = 0; i < rankedErrors.length; i++) {
                rankedErrorVectors[i] = new Category(rankedErrors[i], new Axis[]{xAxis, yAxis});
                rankedErrorVectors[i].setColor(new float[]{1.0f, 0.0f, 0.0f});

            }
            for (Category c : rankedErrorVectors) {
                vectorChart.addCategory(c);
            }

            totalNeighbors = neighborhoodSize * (sizeX - 1) * (sizeY - 1);
            correctNeighbors = 0;
            for (int i = 0; i < neighbors.length; i++) {
                int last = neighborhoodSize;
                /* adapt neighborhood size at borders */
                int x = i / sizeY;
                int y = i % sizeY;
                if (x == 0 || x == sizeY - 1) {
                    last = (last / 2) + 1;
                }
                if (y == 0 || y == sizeY - 1) {
                    last = (last / 2) + 1;
                /* check for accidental initial correct guesses */
                }
                for (int rank = 0; rank < last; rank++) {
                    if (adjacent(i, neighbors[i][rank])) {
                        correctNeighbors++;
                    }
                }
            }
            outstanding = 1.0f;

            displayTime = (int) (System.nanoTime() / 1000);
        }

        /**
         * Begin monitoring sequence.
         */
        public void enter(int inputSize, int inputDuration) {
            this.inputDuration = inputDuration;
            referenceTime = (int) (System.nanoTime() / 1000);  // start timer...
        }

        /**
         * End monitoring sequence. 
         */
        synchronized public void exit() {
            /* calculate parameters */
            int now = (int) (System.nanoTime() / 1000);
            float utilizationSample = (float) (now - referenceTime) / (float) inputDuration;

            /* update display data */
            time += 1;
            this.utilization.add(time, utilizationSample);
            this.progress.add(time, correctNeighbors / totalNeighbors);
            progressCurve.getDataTransformation()[12] = -time;  // hack: shift progress curve back
            utilizationCurve.getDataTransformation()[12] = -time;  // hack: shift utilization curve back

            /* update display if needed */
            if (now >= displayTime) {
//                log.info(String.format("utilization=%f\t progress=%f",utilizationSample,correctNeighbors / totalNeighbors));
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        progressChart.display();
                        utilizationChart.display();
                        if (showFalseEdges) {
                            vectorChart.display();
                        }
                    }
                });

                displayTime += 1000000 / DISPLAY_FREQUENCY;
            }

            /* check for numEventsProcessed best value */
            if (correctNeighbors > currentBest) {
                currentBest = correctNeighbors;
                if (onResetWriteStatsAndExit) {
                    if (outstanding == 1.0f) {
                        params.add(""); // data set
                        params.add(learningWindowShape == 0 ? "Gaussian" : learningWindowShape == 1 ? "Triangle" : "Rectangle");
                        params.add(String.valueOf(learningWindowMean));
                        params.add(String.valueOf(learningWindowStandardDeviation));
                        params.add(""); // reinforcement
                        params.add(String.valueOf(neighborhoodSize));
                        params.add(inhibit2ndOrderNeighbors ? "On" : "Off");
                        outstanding /= 2;
                    } else if (1.0f - currentBest / totalNeighbors <= outstanding) {
                        stat.add(String.valueOf(numEventsProcessed));
                        outstanding /= 2;
                    }
                    if (utilizationStat != null) {
                        utilizationStat.add(utilizationSample);
                    }
                }
            }
        }

        /**
         * Write the stat data to 'topologyLearningStats.csv'.
         */
        public void writeStat() {
            if (params.isEmpty()) {
                return;  // nothing to write
            }
            try {
                String fieldSeparator = ",";
                BufferedWriter statFile = new BufferedWriter(new FileWriter("topologyLearningStats.csv", true));
                for (String param : params) {
                    statFile.write(param + fieldSeparator);
                }
                statFile.write(100.0f - monitor.currentBest / monitor.totalNeighbors * 100.0f + "%");
                for (String date : stat) {
                    statFile.write(fieldSeparator + date);
                }
                statFile.newLine();
                statFile.flush();
                if (utilizationStat != null) {
                    statFile = new BufferedWriter(new FileWriter("utilization.csv", false));
                    for (Float util : utilizationStat) {
                        statFile.write(String.valueOf(util));
                        statFile.newLine();
                    }
                    statFile.flush();
                }
                System.out.println(String.valueOf((System.currentTimeMillis() - startTime)) + " ms.");

                System.exit(0);
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }

        /**
         * Checks if two nodes are adjacent, returns false if identical.
         */
        private boolean adjacent(int i, int j) {
            if (i == j) {
                return false;
            }
            int dx = j / sizeY - i / sizeY;
            int dy = j % sizeY - i % sizeY;
            return dx * dx + dy * dy <= maxSquaredNeighborDistance;
        }

        /**
         * Event handler on changed neighbor.
         * Nodes at the border ignore inexistent higher ranked neighbors.
         */
        public void neighborChanged(int i, int newNeighbor, int[] neighbors, int rank) {
            assert newNeighbor == neighbors[rank];
            int last = neighborhoodSize;
//            /* adapt neighborhood size at borders */
//            int x = i / sizeY;
//            int y = i % sizeY;
//            if (x == 0 || x == sizeY-1) last = (last / 2) + 1;
//            if (y == 0 || y == sizeY-1) last = (last / 2) + 1;
//            if (rank >= last) return;
            /* TODO: at the moment neighbors of border nodes do not have to be inside neigbors[0..last-1]
            but only inside neighbors[0..neighborhoodSize-1] */
            /* check for changes in correctness */
            if (adjacent(i, newNeighbor)) {
                if (!adjacent(i, neighbors[last])) {
                    correctNeighbors++;
                }
            } else if (adjacent(i, neighbors[last])) {
                if (!adjacent(i, newNeighbor)) {
                    correctNeighbors--;
                }
            }
        }

        /**
         * Event handler on changed neighbor ranking.
         */
        private void neighborhoodChanged(int i, int[] neighbors) {
            int last = neighborhoodSize;
            int xi = i / sizeY;
            int yi = i % sizeY;
            if (xi == 0 || xi == sizeY - 1) {
                last = (last / 2) + 1;
            }
            if (yi == 0 || yi == sizeY - 1) {
                last = (last / 2) + 1;
            }
            for (int rank = 0; rank < last; rank++) {
                int j = neighbors[rank];
                int dx = j / sizeY - i / sizeY;
                int dy = j % sizeY - i % sizeY;
                if (dx * dx + dy * dy <= maxSquaredNeighborDistance) {
                    j = i; // hide correct edges
                // calculate (x,y) position of j
                }
                int xj = j / sizeY;
                int yj = j % sizeY;
                // set vector target to j
                rankedErrors[rank].set(i, xj, yj);
            }
        }
    }//    public void annotate(float[][][] frame) {
//    }
//
//    public void annotate(Graphics2D g) {
//    }
//
//    public void annotate(GLAutoDrawable drawable) {
//        if(!isFilterEnabled()) return;
//        // blend may not be available depending on graphics mode or opengl version.
////        GL gl=drawable.getGL();
////        if(!hasBlendChecked){
////            hasBlendChecked=true;
////            String glExt=gl.glGetString(GL.GL_EXTENSIONS);
////            if(glExt.indexOf("GL_EXT_blend_color")!=-1) hasBlend=true;
////        }
////        if(hasBlend){
////            try{
////                gl.glEnable(GL.GL_BLEND);
////                gl.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA);
////                gl.glBlendEquation(GL.GL_FUNC_ADD);
////            }catch(GLException e){
////                e.printStackTrace();
////                hasBlend=false;
////            }
////        }
////        gl.glPushMatrix();
////        {
////            gl.glTranslatef(position.x,position.y,0);
////            
////            // draw disk
////            if(!trackingQualityDetector.isTrackingOK()){
////                gl.glColor4f(1,0,0,.3f);
////            }else{
////                gl.glColor4f(0,0,1,.3f);
////            }
////            glu.gluQuadricDrawStyle(eyeQuad,GLU.GLU_FILL);
////            glu.gluDisk(eyeQuad,pupilRadius-rim,pupilRadius+rim,16,1);
////            
////            // draw pupil rim
////            gl.glColor4f(0,0,1,.7f);
////            glu.gluQuadricDrawStyle(eyeQuad,GLU.GLU_FILL);
////            glu.gluDisk(eyeQuad,pupilRadius-1,pupilRadius+1,16,1);
////            
////            // draw iris disk and rim
////            // draw disk
////            if(!trackingQualityDetector.isTrackingOK()){
////                gl.glColor4f(1,0,0,.3f);
////            }else{
////                gl.glColor4f(0,0,1,.3f);
////            }
////            glu.gluQuadricDrawStyle(eyeQuad,GLU.GLU_FILL);
////            glu.gluDisk(eyeQuad,irisRadius-rim,irisRadius+rim,16,1);
////            
////            gl.glColor4f(0,0,1,.7f);
////            glu.gluQuadricDrawStyle(eyeQuad,GLU.GLU_FILL);
////            glu.gluDisk(eyeQuad,irisRadius-1,irisRadius+1,16,1);
////            
//////            // text annotations
//////            //Write down the frequence and amplitude
//////            int font = GLUT.BITMAP_HELVETICA_18;
//////            gl.glColor3f(1,0,0);
//////            gl.glTranslatef(0,0,0);
//////            gl.glRasterPos3f(1,4,0);
//////            chip.getCanvas().getGlut().glutBitmapString(font, String.format("fractionOutside=%.3f",1-blinkDetector.fractionInside));
////        }
////        gl.glPopMatrix();
////        
////        // show quality as bar that shows event-averaged fraction of events inside the model.
////        // the red part shows eventInsideRatio is less than threshold, green shows good tracking
////        gl.glLineWidth(5);
////        gl.glBegin(GL.GL_LINES);
////        {
////            final float SCREEN_FRAC_THRESHOLD_QUALITY=0.1f;
////            if(!trackingQualityDetector.isTrackingOK()){
////                // tracking bad, draw actual quality in red only
////                gl.glColor3f(1,0,0);
////                gl.glVertex2f(0,1);
////                gl.glVertex2f(trackingQualityDetector.quality*chip.getSizeX()*SCREEN_FRAC_THRESHOLD_QUALITY,1);
////            }else{
////                // tracking is good, draw quality in red up to threshold, then in green for excess
////                gl.glColor3f(1,0,0); // red bar up to qualityThreshold
////                gl.glVertex2f(0,1);
////                float f=qualityThreshold*chip.getSizeX()*SCREEN_FRAC_THRESHOLD_QUALITY;
////                gl.glVertex2f(f,1); // 0 to threshold in green
////                gl.glColor3f(0,1,0); // green for rest of bar
////                gl.glVertex2f(f,1); // threshold to quality in green
////                gl.glVertex2f((qualityThreshold+trackingQualityDetector.quality)*chip.getSizeX()*SCREEN_FRAC_THRESHOLD_QUALITY,1);
////            }
////        }
////        gl.glEnd();
////        
////        if(isShowGazeEnabled()){
////            gl.glPushMatrix();
////            {
////                float gazeX=statComputer.getGazeX()*chip.getSizeX();
////                float gazeY=statComputer.getGazeY()*chip.getSizeY();
////                gl.glTranslatef(gazeX,gazeY,0);
////                gl.glColor4f(0,1,0,.5f);
////                glu.gluQuadricDrawStyle(eyeQuad,GLU.GLU_FILL);
////                glu.gluDisk(eyeQuad,0,5,16,1);
////            }
////            gl.glPopMatrix();
////            if(targetFrame!=null){
////                target.display();
////            }
////        }
////        if(hasBlend) gl.glDisable(GL.GL_BLEND);
//        
//    }
}

