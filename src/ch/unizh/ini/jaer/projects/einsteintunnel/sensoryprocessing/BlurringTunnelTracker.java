/*
 * Last updated on April 23, 2010, 11:40 AM
 *
 *  * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.ClusterInterface;
import net.sf.jaer.eventprocessing.tracking.ClusterPathPoint;
import net.sf.jaer.eventprocessing.tracking.ClusterTrackerInterface;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter2D;
import ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing.BlurringTunnelFilter.NeuronGroup;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Tracks moving objects. Modified from BlurringFilter2DTracker.java
 *
 * @author Jun Haeng Lee/Tobi Delbruck
 */
@Description("Tracks people in Einstein tunnel exhibit")
public class BlurringTunnelTracker extends EventFilter2D implements FrameAnnotater, Observer, ClusterTrackerInterface /*, PreferenceChangeListener*/ {
    // TODO split out the optical gryo stuff into its own subclass
    // TODO split out the Cluster object as it's own class.

    public int outputSubSample = 4;
    public int flowSubSample = 200;
    public int lastTimestamp = 0;
    public int flow = 0;
    public int flowSum = 0;
    public short[] xHistogram;
    public ClusterOSCInterface oscInterface1 = new ClusterOSCInterface();
    public ClusterOSCInterface oscInterface2 = new ClusterOSCInterface("192.168.1.103");

    public static final int RECEIVE_PORT = 9998;
    private DatagramSocket dsocket;

    /**
     * /**
     * The list of clusters.
     */
    protected java.util.List<Cluster> clusters = new LinkedList();
    /**
     * Blurring filter to getString clusters
     */
    protected BlurringTunnelFilter bfilter;
    /**
     * clusters to be destroyed
     */
    protected LinkedList<Cluster> pruneList = new LinkedList<Cluster>();
    /**
     * keeps track of absolute cluster number
     */
    protected int clusterCounter = 0;
    /**
     * random
     */
    protected Random random = new Random();
    private int numVelocityPoints = getPrefs().getInt("BlurringTunnelTracker.numVelocityPoints", 3);
    private boolean pathsEnabled = getPrefs().getBoolean("BlurringTunnelTracker.pathsEnabled", true);
    private int pathLength = getPrefs().getInt("BlurringTunnelTracker.pathLength", 100);
    private boolean useVelocity = getPrefs().getBoolean("BlurringTunnelTracker.useVelocity", true); // enabling this enables both computation and rendering of cluster velocities
    private boolean showClusters = getPrefs().getBoolean("BlurringTunnelTracker.showClusters", false);
    private float velAngDiffDegToNotMerge = getPrefs().getFloat("BlurringTunnelTracker.velAngDiffDegToNotMerge", 60.0f);
    private boolean enableMerge = getPrefs().getBoolean("BlurringTunnelTracker.enableMerge", false);
    private boolean showClusterNumber = getPrefs().getBoolean("BlurringTunnelTracker.showClusterNumber", false);
    private boolean showClusterVelocity = getPrefs().getBoolean("BlurringTunnelTracker.showClusterVelocity", false);
    private float velocityVectorScaling = getPrefs().getFloat("BlurringTunnelTracker.velocityVectorScaling", 1.0f);
    private final float VELOCITY_VECTOR_SCALING = 1e6f; // to scale rendering of cluster velocityPPT vector, velocityPPT is in pixels/tick=pixels/us so this gives 1 screen pixel per 1 pix/s actual vel
    private boolean showClusterMass = getPrefs().getBoolean("BlurringTunnelTracker.showClusterMass", false);
    private float maximumClusterLifetimeMs = getPrefs().getFloat("BlurringTunnelTracker.maximumClusterLifetimeMs", 50.0f);
    private float clusterRadiusLifetimeMs = getPrefs().getFloat("BlurringTunnelTracker.clusterRadiusLifetimeMs", 20.0f);
    private int minimumClusterSizePixels = getPrefs().getInt("BlurringTunnelTracker.minimumClusterSizePixels", 10);
    private float tauLPFMs = getPrefs().getFloat("BlurringTunnelTracker.tauLPFMs", 5.0f);
    private boolean oscEnabled = getPrefs().getBoolean("BlurringTunnelTracker.oscEnabled", true);
    private boolean sendClusterInfo = getPrefs().getBoolean("BlurringTunnelTracker.sendClusterInfo", true);
    private int minClusterAge = getPrefs().getInt("BlurringTunnelTracker.minClusterAge", 1000);
    private float activityDecayFactor = getPrefs().getFloat("BlurringTunnelTracker.activityDecayFactor", 0.9f);
    private float superPosIntFactor = getPrefs().getFloat("BlurringTunnelTracker.superPosIntFactor", 50f);
    private float maxSuperPos = getPrefs().getFloat("BlurringTunnelTracker.maxSuperPos", 100f);
    private boolean sendActivity = getPrefs().getBoolean("BlurringTunnelTracker.sendActivity", false);
    private float flowThreshold = getPrefs().getFloat("BlurringTunnelTracker.flowThreshold", 15f);
    private boolean getVVVVstate = getPrefs().getBoolean("BlurringTunnelTracker.getVVVVstate", false);

    private int inputCount = 0;
    private Timer resetTimer = new Timer();
    private TunnelStateMachine stateMachine = new TunnelStateMachine(this);

    /**
     * Creates a new instance of BlurringFilter2DTracker.
     *
     * @param chip
     */
    public BlurringTunnelTracker(AEChip chip) {
        super(chip);
        this.chip = chip;
        initFilter();
        chip.addObserver(this);
        final String sizing = "Sizing", movement = "Movement", lifetime = "Lifetime", disp = "Display",
                global = "Global", update = "Update", logging = "Logging", trjlimit = "Trajectory persistance limit", einstein = "Einstein-Tunnel";
        setPropertyTooltip(global, "minimumClusterSizePixels", "minimum size of a squre Cluster.");
        setPropertyTooltip(global, "maximumClusterLifetimeMs", "upper limit of cluster lifetime. It increases by when the cluster is properly updated. Otherwise, it decreases. When the lifetime becomes zero, the cluster will be expired.");
        setPropertyTooltip(global, "clusterRadiusLifetimeMs", "time constant of the cluster radius.");
        setPropertyTooltip(global, "trackSingleCluster", "track only one cluster");
        setPropertyTooltip(disp, "pathsEnabled", "draws paths of clusters over some window");
        setPropertyTooltip(disp, "pathLength", "paths are at most this many packets long");
        setPropertyTooltip(movement, "numVelocityPoints", "the number of recent path points (one per packet of events) to use for velocity vector regression");
        setPropertyTooltip(movement, "useVelocity", "uses measured cluster velocity to predict future position; vectors are scaled " + String.format("%.1f pix/pix/s", (VELOCITY_VECTOR_SCALING / AEConstants.TICK_DEFAULT_US) * 1e-6));
        setPropertyTooltip(movement, "tauLPFMs", "time constant of LPF");
        setPropertyTooltip(disp, "showClusters", "shows clusters");
        setPropertyTooltip(update, "velAngDiffDegToNotMerge", "relative angle in degrees of cluster velocity vectors to not merge overlapping clusters");
        setPropertyTooltip(update, "enableMerge", "enable merging overlapping clusters");
        setPropertyTooltip(disp, "showClusterVelocity", "annotates velocity in pixels/second");
        setPropertyTooltip(disp, "showClusterNumber", "shows cluster ID number");
        setPropertyTooltip(disp, "showClusterMass", "shows cluster mass");
        setPropertyTooltip(disp, "velocityVectorScaling", "scaling of drawn velocity vectors");
        setPropertyTooltip(einstein, "oscEnabled", "enables a OSC output of the tracked information");
        setPropertyTooltip(einstein, "minClusterAge", "how Old a Cluster has to be for OSC transfer");
        setPropertyTooltip(einstein, "sendClusterInfo", "should the informations on the cluster be send out");
        setPropertyTooltip(einstein, "sendActivity", "should a histogram of the x-activity be send out");
        setPropertyTooltip(einstein, "activityDecayFactor", "how fast should the x-activity histogram decay");
        setPropertyTooltip(einstein, "superPosIntFactor", "how fast the velocity should be integrated for the superPos");
        setPropertyTooltip(einstein, "maxSuperPos", "how fast the velocity should be integrated for the superPos");
        setPropertyTooltip(einstein, "flowThreshold", "threshold of average velocity to become left or right output");
        setPropertyTooltip(einstein, "getVVVVstate", "wait on vvvv machine to respond on osc message");

        filterChainSetting();
        initReceiver();
    }

    protected void initReceiver() {
        try {
            dsocket = new DatagramSocket(RECEIVE_PORT);
            dsocket.setSoTimeout(10000);
        } catch (SocketException ex) {
            log.warning(TunnelStateMachine.class.getName() + ex.getMessage());
        }
    }

    /**
     * sets the BlurringFilter2D as a enclosed filter to find cluster
     */
    protected void filterChainSetting() {
        bfilter = new BlurringTunnelFilter(chip);
        bfilter.addObserver(this); // to getString us called during blurring filter iteration at least every updateIntervalUs
        setEnclosedFilter(bfilter);
    }

    /**
     * merge clusters that are too close to each other and that have
     * sufficiently similar velocities (if velocityRatioToNotMergeClusters).
     * this must be done interatively, because merging 4 or more clusters
     * feedforward can result in more clusters than you start with. each time we
     * merge two clusters, we start over, until there are no more merges on
     * iteration. for each cluster, if it is close to another cluster then merge
     * them and start over.
     */
    private void mergeClusters() {
        boolean mergePending;
        Cluster c1 = null;
        Cluster c2 = null;
        do {
            mergePending = false;
            int nc = clusters.size();
            outer:
            for (int i = 0; i < nc; i++) {
                c1 = clusters.get(i);
                if (c1.dead) {
                    continue;
                }
                for (int j = i + 1; j < nc; j++) {
                    c2 = clusters.get(j); // getString the other cluster
                    if (c2.dead) {
                        continue;
                    }
                    final boolean overlapping = c1.distanceTo(c2) < (c1.getMaxRadius() + c2.getMaxRadius());
                    boolean velSimilar = true; // start assuming velocities are similar
                    if (overlapping && (velAngDiffDegToNotMerge > 0) && c1.isVelocityValid() && c2.isVelocityValid() && (c1.velocityAngleTo(c2) > ((velAngDiffDegToNotMerge * Math.PI) / 180))) {
                        // if velocities valid for both and velocities are sufficiently different
                        velSimilar = false; // then flag them as different velocities
                    }
                    if (overlapping && velSimilar) {
                        // if cluster is close to another cluster, merge them
                        // if distance is less than sum of radii merge them and if velAngle < threshold
                        mergePending = true;
                        break outer; // break out of the outer loop
                    }
                }
            }
            if (mergePending && (c1 != null) && (c2 != null)) {
                clusters.add(new Cluster(c1, c2));
                clusters.remove(c1);
                clusters.remove(c2);
            }
        } while (mergePending);

    }

    @Override
    public void initFilter() {
        clusters.clear();
        clusterCounter = 0;
        setTimers();
    }

    private void setTimers() {
        //reset the computer every day at 4:00
        Calendar nextReset = new GregorianCalendar();
        nextReset.add(Calendar.DAY_OF_MONTH, 1);
        nextReset.set(Calendar.HOUR_OF_DAY, 4);
        nextReset.set(Calendar.MINUTE, 00);
        TimerTask resetTask = new Resetter();
        resetTimer.schedule(resetTask, nextReset.getTime());
    }

    /**
     * Prunes out old clusters that don't have support or that should be purged
     * for some other reason.
     */
    private void pruneClusters() {
        clusters.removeAll(pruneList);
        pruneList.clear();
    }

    /**
     * This method updates the list of clusters, pruning and merging clusters
     * and updating positions. It also updates the optical gyro if enabled.
     *
     * @param t the global timestamp of the update.
     */
    private void updateClusterList(int t) {
        if (enableMerge) {
            mergeClusters();
        }
        pruneClusters();
        updateClusterPaths(t);
    }

    /**
     * Processes the incoming events to track clusters.
     *
     * @param in
     * @return packet of BlurringTunnelTrackerEvent.
     */
    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (in == null) {
            return null;
        }

        if (enclosedFilter != null) {
            out = enclosedFilter.filterPacket(in);
        } else {
            out = in;
        }

        lastTimestamp = out.getLastTimestamp();

        if (oscEnabled) {
            if ((inputCount % outputSubSample) == 0) {
                sendOSC(out);
            }
            if ((inputCount % flowSubSample) == 0) {
                if (flowSum > flowThreshold) {
                    flow = 1;
                } else if (flowSum < -flowThreshold) {
                    flow = -1;
                } else {
                    flow = 0;
                }
                flowSum = 0;
            }
            if ((inputCount >= outputSubSample) && (inputCount >= flowSubSample)) {
                inputCount = 0;
            }
            inputCount++;
        }

        return out;
    }

    public void sendOSC(EventPacket<BasicEvent> in) {
        if (sendActivity) {
            for (BasicEvent e : in) {
                xHistogram[e.x] += 1;
            }
            oscInterface1.sendActivity(xHistogram);
            oscInterface2.sendActivity(xHistogram);
            for (int i = 0; i < xHistogram.length; i++) {
                xHistogram[i] = (short) (xHistogram[i] * activityDecayFactor);
            }

        }
        if (sendClusterInfo) {
            ListIterator clusterItr = clusters.listIterator();
            while (clusterItr.hasNext()) {
                Cluster clusterToSend = (Cluster) clusterItr.next();
                if ((lastTimestamp - clusterToSend.firstUpdateTimestamp) > minClusterAge) {
                    clusterToSend.validForOSC = true;
                    flowSum += clusterToSend.getVelocity().x;
                    oscInterface1.sendCluster(clusterToSend);
                    oscInterface2.sendCluster(clusterToSend);
                }
            }

            oscInterface1.sendFlow(flow);
            oscInterface2.sendFlow(flow);
        }
        if (getVVVVstate) {
            //	    byte[] buffer = new byte[2048];
            //	    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            //	    try {
            //		dsocket.receive(packet);
            //	    } catch (IOException ex) {
            //		Logger.getLogger(BlurringTunnelTracker.class.getName()).log(Level.WARNING, null, ex);
            //	    }
            //	    String msg = new String(buffer, 0, packet.getLength());
            //	    //System.out.println(packet.getAddress().getHostName() + ": "+msg);
            //	    if(msg.equals("swoosh"))stateMachine.setSwooshState();
            //	    else if(stateMachine.state == TunnelStateMachine.stateTypes.SWOOSH) stateMachine.setDefaultState();
        }
    }

    /**
     * the method that actually does the tracking Tracking is done by selecting
     * the right neuron groups for the next cluster.
     *
     * @param newNeuronGroup : a neuron group detected by BlurringFilter2D
     * @param initialAge
     */
    protected void track(NeuronGroup newNeuronGroup, int initialAge) {
        if (newNeuronGroup.getNumMemberNeurons() == 0) {
            return;
        }

        // for input neuron group, see which cluster it is closest to and append it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
        Cluster closest = null;
        closest = getNearestCluster(newNeuronGroup); // find cluster that event falls within (or also within surround if scaling enabled)

        if (closest != null) {
            closest.addGroup(newNeuronGroup);
        } else { // start a new cluster
            clusters.add(new Cluster(newNeuronGroup, initialAge));
        }
    }

    /**
     * Returns total number of clusters.
     *
     * @return number of Cluster's in clusters list.
     */
    public int getNumClusters() {
        return clusters.size();
    }

    @Override
    public String toString() {
        String s = clusters != null ? Integer.toString(clusters.size()) : null;
        String s2 = "BlurringTunnelTracker with " + s + " clusters ";
        return s2;
    }

    /**
     * finds the nearest cluster from the given neuron group The found cluster
     * will be updated using the neuron group.
     *
     * @param neuronGroup : a neuron group
     * @return closest cluster
     */
    public Cluster getNearestCluster(NeuronGroup neuronGroup) {
        float minDistance = Float.MAX_VALUE;
        Cluster closest = null;

        for (Cluster c : clusters) {
            float dx = c.distanceToX(neuronGroup);
            float dy = c.distanceToY(neuronGroup);
            float aveRadius = (c.getMaxRadius() + neuronGroup.getOutterRadiusPixels()) / 2.0f;

            if (!c.isUpdated() && (dx < aveRadius) && (dy < aveRadius)) {
                if ((dx + dy) < minDistance) {
                    closest = c;
                    minDistance = dx + dy;
                }
            }
        }

        return closest;
    }

    /**
     * Updates cluster path lists
     *
     * @param t the update timestamp
     */
    protected void updateClusterPaths(int t) {
        // update paths of clusters
        for (Cluster c : clusters) {
            if (c.dead) {
                continue;
            }
            c.updatePath(t, 0);
            c.setUpdated(false);
        }
    }

    /**
     * Cluster class
     */
    public class Cluster implements ClusterInterface {

        /**
         * scaling factor for velocity in PPS
         */
        final float VELPPS_SCALING = 1e6f / AEConstants.TICK_DEFAULT_US;

        /**
         * location in chip pixels
         */
        public Point2D.Float location = new Point2D.Float();

        /**
         * birth location of cluster
         */
        private Point2D.Float birthLocation = new Point2D.Float();

        /**
         * velocityPPT of cluster in pixels/tick, where tick is timestamp tick
         * (usually microseconds)
         */
        protected Point2D.Float velocityPPT = new Point2D.Float();

        /**
         * cluster velocityPPT in pixels/second
         */
        private Point2D.Float velocityPPS = new Point2D.Float();

        /**
         * anticipated position by integration of the velocity
         */
        private Point2D.Float superPos = new Point2D.Float();

        /**
         * used to flag invalid or uncomputable velocityPPT
         */
        private boolean velocityValid = false;

        /**
         * used to flag invalid or uncomputable velocityPPT
         */
        public boolean validForOSC = false;

        /**
         * radius of the cluster in chip pixels
         */
        private float innerRadius, outterRadius, maxRadius;

        /**
         * cluster area
         */
        private Rectangle clusterArea = new Rectangle();

        /**
         * true if the cluster is hitting any adge of the frame
         */
        protected boolean hitEdge = false;

        /**
         * dynamic age of the cluster. It increases as the cluster is updated,
         * and decreases if it's not updated.
         */
        protected int ageUs = 0;

        /**
         * true if the cluster is updated
         */
        protected boolean updated = false;

        /**
         * Rendered color of cluster.
         */
        protected Color color = null;

        /**
         * Number of neurons collected by this cluster.
         */
        protected int numNeurons = 0;

        /**
         * The "mass" of the cluster is the total membrane potential of member
         * neurons.
         */
        protected float mass;

        /**
         * timestamp of the last updates
         */
        protected int lastUpdateTimestamp;

        /**
         * timestamp of the first updates
         */
        protected int firstUpdateTimestamp;

        /**
         * assigned to be the absolute number of the cluster that has been
         * created.
         */
        private int clusterNumber;

        /**
         * true if the cluster is dead
         */
        private boolean dead = false;

        /**
         * cluster color
         */
        private float[] rgb = new float[4];

        /**
         * trajectory of the cluster
         */
        protected ArrayList<ClusterPathPoint> path = new ArrayList<ClusterPathPoint>(getPathLength());

        /**
         *
         */
        private RollingVelocityFitter velocityFitter = new RollingVelocityFitter(path, numVelocityPoints);

        @Override
        public int hashCode() {
            return clusterNumber;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if ((obj == null) || (obj.getClass() != this.getClass())) {
                return false;
            }
            // object must be Test at this point
            Cluster test = (Cluster) obj;
            return clusterNumber == test.clusterNumber;
        }

        /**
         * Constructs a default cluster.
         *
         */
        public Cluster() {
            float hue = random.nextFloat();
            Color c = Color.getHSBColor(hue, 1f, 1f);
            setColor(c);
            setClusterNumber(clusterCounter++);
            maxRadius = 0;
            clusterArea.setBounds(0, 0, 0, 0);
            dead = false;
        }

        /**
         * Constructs a cluster with the first neuron group The numEvents,
         * location, birthLocation, first and last timestamps are set.
         *
         * @param ng the neuron group.
         * @param initialAge
         */
        public Cluster(NeuronGroup ng, int initialAge) {
            this();
            location = ng.getLocation();
            superPos = (Point2D.Float) ng.getLocation().clone();
            birthLocation = ng.getLocation();
            lastUpdateTimestamp = ng.getLastEventTimestamp();
            firstUpdateTimestamp = ng.getLastEventTimestamp();
            numNeurons = ng.getNumMemberNeurons();
            mass = ng.getTotalMP();
            increaseAgeUs(initialAge);
            setRadius(ng, 0f, mass, 0);
            hitEdge = ng.isHitEdge();
            if (hitEdge) {
                ageUs = (int) (1000 * maximumClusterLifetimeMs);
            }

            //            System.out.println("Cluster_"+clusterNumber+" is created @"+firstUpdateTimestamp);
        }

        /**
         * Constructs a cluster by merging two clusters. All parameters of the
         * resulting cluster should be reasonable combinations of the source
         * cluster parameters. For example, the merged location values are
         * weighted by the totalMP of events that have supported each source
         * cluster, so that older clusters weigh more heavily in the resulting
         * cluster location. Subtle bugs or poor performance can result from not
         * properly handling the merging of parameters.
         *
         * @param one the first cluster
         * @param two the second cluster
         */
        public Cluster(Cluster one, Cluster two) {
            this();

            Cluster older = one.clusterNumber < two.clusterNumber ? one : two;
            float leakyfactor = one.calMassLeakyfactor(two.lastUpdateTimestamp);
            float one_mass = one.mass;
            float two_mass = two.mass;

            clusterNumber = older.clusterNumber;

            if (leakyfactor > 1) {
                two_mass /= leakyfactor;
            } else {
                one_mass *= leakyfactor;
            }

            mass = one_mass + two_mass;
            numNeurons = one.numNeurons + two.numNeurons;

            // merge locations by average weighted by totalMP of events supporting each cluster
            location.x = ((one.location.x * one_mass) + (two.location.x * two_mass)) / (mass);
            location.y = ((one.location.y * one_mass) + (two.location.y * two_mass)) / (mass);
            superPos = (Point2D.Float) location.clone();

            lastUpdateTimestamp = one.lastUpdateTimestamp > two.lastUpdateTimestamp ? one.lastUpdateTimestamp : two.lastUpdateTimestamp;
            firstUpdateTimestamp = one.firstUpdateTimestamp < two.firstUpdateTimestamp ? one.firstUpdateTimestamp : two.firstUpdateTimestamp;
            path = older.path;
            birthLocation.x = older.birthLocation.x;
            birthLocation.y = older.birthLocation.y;
            velocityFitter = older.velocityFitter;
            velocityPPT.x = older.velocityPPT.x;
            velocityPPT.y = older.velocityPPT.y;
            velocityPPS.x = older.velocityPPS.x;
            velocityPPS.y = older.velocityPPS.y;
            velocityValid = older.velocityValid;
            ageUs = older.ageUs;

            innerRadius = one.mass > two.mass ? one.innerRadius : two.innerRadius;
            outterRadius = one.mass > two.mass ? one.outterRadius : two.outterRadius;
            maxRadius = one.mass > two.mass ? one.maxRadius : two.maxRadius;
            setColor(older.getColor());

            hitEdge = one.hasHitEdge() | two.hasHitEdge();

            //            System.out.println(older.getClusterNumber());
        }

        /**
         * calculates totalMP leaky factor
         *
         * @param timeStamp
         * @return
         */
        private float calMassLeakyfactor(int timestamp) {
            return (float) Math.exp(((float) lastUpdateTimestamp - timestamp) / bfilter.getMPTimeConstantUs());
        }

        /**
         * Draws this cluster using OpenGL.
         *
         * @param drawable area to draw this.
         */
        public void draw(GLAutoDrawable drawable) {
            final float BOX_LINE_WIDTH = 2f; // in chip
            final float PATH_POINT_SIZE = 4f;
            final float SUPER_POINT_SIZE = 8f;
            final float VEL_LINE_WIDTH = 4f;
            GL2 gl = drawable.getGL().getGL2();
            int x = (int) getLocation().x;
            int y = (int) getLocation().y;

            // set color and line width of cluster annotation
            getColor().getRGBComponents(rgb);
            gl.glColor3fv(rgb, 0);
            gl.glLineWidth(BOX_LINE_WIDTH);

            // draw cluster rectangle
            drawBox(gl, x, y, (int) maxRadius);

            gl.glPointSize(PATH_POINT_SIZE);

            ArrayList<ClusterPathPoint> points = getPath();
            for (Point2D.Float p : points) {
                gl.glBegin(GL.GL_POINTS);
                gl.glVertex2f(p.x, p.y);
                gl.glEnd();
            }

            // now draw velocityPPT vector
            if (showClusterVelocity) {
                gl.glLineWidth(VEL_LINE_WIDTH);
                gl.glBegin(GL.GL_LINES);
                {
                    gl.glVertex2i(x, y);
                    gl.glVertex2f(x + (getVelocity().x * VELOCITY_VECTOR_SCALING * velocityVectorScaling), y + (getVelocity().y * VELOCITY_VECTOR_SCALING * velocityVectorScaling));
                }
                gl.glEnd();
            }
            // text annoations on clusters, setup
            final int font = GLUT.BITMAP_HELVETICA_18;
            if (validForOSC) {
                gl.glColor3f(0, 1, 0);
            } else {
                gl.glColor3f(1, 1, 1);
            }
            gl.glRasterPos3f(location.x, location.y, 0);

            // annotate the cluster with hash ID
            if (showClusterNumber) {
                chip.getCanvas().getGlut().glutBitmapString(font, String.format("#%d", hashCode()));
            }

            //annotate the cluster with the velocityPPT in pps
            if (showClusterVelocity) {
                Point2D.Float velpps = getVelocity();
                chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0f,%.0f pps", velpps.x, velpps.y));
            }

            //show superPos
            gl.glColor3f(1, 0, 0);
            gl.glPointSize(SUPER_POINT_SIZE);
            gl.glBegin(GL.GL_POINTS);
            gl.glVertex2f(superPos.x, superPos.y);
            gl.glEnd();
        }

        /**
         * Returns true if the cluster center is outside the array
         *
         * @return true if cluster has hit edge
         */
        private boolean hasHitEdge() {
            return hitEdge;
        }

        /**
         * returns true if the cluster has been updated
         *
         * @return true if the cluster has been updated
         */
        public boolean isUpdated() {
            return updated;
        }

        /**
         * set true if the cluster has been updated
         *
         * @param updated
         */
        public void setUpdated(boolean updated) {
            this.updated = updated;
        }

        /**
         * returns the number of events collected by the cluster at each update
         *
         * @return numEvents
         */
        @Override
        public int getNumEvents() {
            return 1;
        }

        /**
         * The "totalMP" of the cluster is the totalMP of the NeuronGroup of the
         * BlurringFilter2D.
         *
         * @return the totalMP
         */
        @Override
        public float getMass() {
            return mass;
        }

        /**
         *
         * @return lastUpdateTimestamp
         */
        @Override
        public int getLastEventTimestamp() {
            return lastUpdateTimestamp;
        }

        /**
         * updates cluster by one NeuronGroup
         *
         * @param ng
         */
        public void addGroup(NeuronGroup ng) {
            float leakyfactor = calMassLeakyfactor(ng.getLastEventTimestamp());
            float curMass = mass;
            float ngTotalMP = ng.getTotalMP();
            int timeInterval = ng.getLastEventTimestamp() - lastUpdateTimestamp;

            if (leakyfactor > 1) {
                ngTotalMP /= leakyfactor;
            } else {
                curMass *= leakyfactor;
            }

            numNeurons = ng.getNumMemberNeurons();
            mass = curMass + ngTotalMP;

            // averaging
            location.x = ((location.x * curMass) + (ng.getLocation().x * ngTotalMP)) / (mass);
            location.y = ((location.y * curMass) + (ng.getLocation().y * ngTotalMP)) / (mass);

            if (ng.getLastEventTimestamp() > lastUpdateTimestamp) {
                increaseAgeUs(ng.getLastEventTimestamp() - lastUpdateTimestamp);
                lastUpdateTimestamp = ng.getLastEventTimestamp();
            }

            if (maxRadius == 0) {
                birthLocation = ng.getLocation();
                firstUpdateTimestamp = ng.getLastEventTimestamp();
            }

            hitEdge = ng.isHitEdge();
            if (hitEdge) {
                ageUs = (int) (1000 * maximumClusterLifetimeMs);
            }

            setRadius(ng, curMass, ngTotalMP, timeInterval);
        }

        /**
         *
         * @param dx
         * @param dy
         * @return
         */
        public float distanceMetric(float dx, float dy) {
            return ((dx > 0) ? dx : -dx) + ((dy > 0) ? dy : -dy);
        }

        /**
         * Measures distance in x direction, accounting for predicted movement
         * of cluster.
         *
         * @return distance in x direction of this cluster to the event.
         */
        private float distanceToX(NeuronGroup ng) {
            int dt = ng.getLastEventTimestamp() - lastUpdateTimestamp;
            float currentLocationX = location.x;
            if (useVelocity) {
                currentLocationX += velocityPPT.x * dt;
            }

            if (currentLocationX < 0) {
                currentLocationX = 0;
            } else if (currentLocationX > (chip.getSizeX() - 1)) {
                currentLocationX = chip.getSizeX() - 1;
            }

            return Math.abs(ng.getLocation().x - currentLocationX);
        }

        /**
         * Measures distance in y direction, accounting for predicted movement
         * of cluster
         *
         * @return distance in y direction of this cluster to the event
         */
        private float distanceToY(NeuronGroup ng) {
            int dt = ng.getLastEventTimestamp() - lastUpdateTimestamp;
            float currentLocationY = location.y;
            if (useVelocity) {
                currentLocationY += velocityPPT.y * dt;
            }

            if (currentLocationY < 0) {
                currentLocationY = 0;
            } else if (currentLocationY > (chip.getSizeY() - 1)) {
                currentLocationY = chip.getSizeY() - 1;
            }

            return Math.abs(ng.getLocation().y - currentLocationY);
        }

        /**
         * Computes and returns distance to another cluster.
         *
         * @param c
         * @return distance of this cluster to the other cluster in pixels.
         */
        protected final float distanceTo(Cluster c) {
            // TODO doesn't use predicted location of clusters, only present locations
            float dx = c.location.x - location.x;
            float dy = c.location.y - location.y;
            return distanceMetric(dx, dy);
        }

        /**
         * Computes and returns the angle of this cluster's velocities vector to
         * another cluster's velocities vector.
         *
         * @param c the other cluster.
         * @return the angle in radians, from 0 to PI in radians. If either
         * cluster has zero velocities, returns 0.
         */
        protected final float velocityAngleTo(Cluster c) {
            float s1 = getSpeedPPS(), s2 = c.getSpeedPPS();
            if ((s1 == 0) || (s2 == 0)) {
                return 0;
            }
            float dot = (velocityPPS.x * c.velocityPPS.x) + (velocityPPS.y * c.velocityPPS.y);
            float angleRad = (float) Math.acos(dot / s1 / s2);
            return angleRad;
        }

        /**
         * returns true if the given neuron group is inside the cluster
         *
         * @param ng
         * @return
         */
        private boolean doesCover(NeuronGroup ng, float marginPixel) {
            int dt = ng.getLastEventTimestamp() - lastUpdateTimestamp;
            float minX = clusterArea.x - marginPixel;
            float minY = clusterArea.y - marginPixel;
            if (useVelocity) {
                minX += velocityPPT.x * dt;
                minY += velocityPPT.y * dt;
            }

            float maxX = minX + clusterArea.width + (2 * marginPixel);
            float maxY = minY + clusterArea.height + (2 * marginPixel);

            if ((((minX <= ng.minX) && (ng.minX <= maxX)) || ((ng.minX <= minX) && (minX <= ng.maxX)))
                    && (((minY <= ng.minY) && (ng.minY <= maxY)) || ((ng.minY <= minY) && (minY <= ng.maxY)))) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns measure of cluster radius, here the maxRadius.
         *
         * @return the maxRadius radius.
         */
        @Override
        public float getRadius() {
            return maxRadius;
        }

        /**
         * returns the inner radius of the cluster
         *
         * @return innerRadius
         */
        public final float getInnerRadius() {
            return innerRadius;
        }

        /**
         * returns the outter radius of the cluster
         *
         * @return outterRadius
         */
        public final float getOutterRadius() {
            return outterRadius;
        }

        /**
         * returns the max radius of the cluster
         *
         * @return maxRadius
         */
        public final float getMaxRadius() {
            return maxRadius;
        }

        /**
         * the radius of a cluster is the distance in pixels from the cluster
         * center that is the putative model size. If highwayPerspectiveEnabled
         * is true, then the radius is set to a fixed size depending on the
         * defaultClusterRadius and the perspective location of the cluster and
         * r is ignored. The aspect ratio parameters radiusX and radiusY of the
         * cluster are also set.
         *
         * @param r the radius in pixels
         */
        private void setRadius(NeuronGroup ng, float curMass, float ngTotalMP, int timeIntervalUs) {
            float totalMass = curMass + ngTotalMP;
            innerRadius = ((innerRadius * curMass) + (ng.getInnerRadiusPixels() * ngTotalMP)) / totalMass;
            outterRadius = ((outterRadius * curMass) + (ng.getOutterRadiusPixels() * ngTotalMP)) / totalMass;

            float mixingFactor = 1 - (float) Math.exp(-timeIntervalUs / (clusterRadiusLifetimeMs * 1000));
            if (clusterArea.height < ng.getDimension().height) {
                mixingFactor = 1;
            }
            if (ng.isHitEdge()) {
                mixingFactor = 0;
            }
            float height = ((clusterArea.height * (1 - mixingFactor)) + (ng.getDimension().height * mixingFactor));
            if (height < minimumClusterSizePixels) {
                height = minimumClusterSizePixels;
            }

            mixingFactor = 1 - (float) Math.exp(-timeIntervalUs / (clusterRadiusLifetimeMs * 1000));
            if (clusterArea.width < ng.getDimension().width) {
                mixingFactor = 1;
            }
            if (ng.isHitEdge()) {
                mixingFactor = 0;
            }
            float width = ((clusterArea.width * (1 - mixingFactor)) + (ng.getDimension().width * mixingFactor));
            if (width < minimumClusterSizePixels) {
                width = minimumClusterSizePixels;
            }

            clusterArea.setBounds((int) (location.x - (width / 2f)), (int) (location.y - (height / 2f)), (int) (width + 0.5), (int) (height + 0.5));

            if (ng.isHitEdge()) {
                maxRadius = Math.max(height, width) / 2;
            } else {
                maxRadius = (height + width) / 4;
            }
        }

        /**
         * getString the cluster location
         *
         * @return location
         */
        @Override
        final public Point2D.Float getLocation() {
            return location;
        }

        /**
         * set the cluster location
         *
         * @param loc
         */
        public void setLocation(Point2D.Float loc) {
            location = loc;
        }

        /**
         * @return lifetime of cluster in timestamp ticks, measured as
         * lastUpdateTimestamp-firstUpdateTimestamp.
         */
        final public int getLifetime() {
            return lastUpdateTimestamp - firstUpdateTimestamp;
        }

        /**
         * Updates path (historical) information for this cluster, including
         * cluster velocityPPT.
         *
         * @param t current timestamp.
         */
        final public void updatePath(int t, int disparity) {
            if (!pathsEnabled) {
                return;
            }

            ClusterPathPoint newPath;

            // low pass filtering based on time constant
            if (path.size() > 3) {
                LowpassFilter2D lpf = new LowpassFilter2D();
                lpf.setTauMs(tauLPFMs);
                Point2D.Float pt = lpf.filter(path.get(path.size() - 1).x, path.get(path.size() - 1).y, path.get(path.size() - 1).getT());
                pt = lpf.filter(location.x, location.y, t);

                newPath = ClusterPathPoint.createPoint(pt.x, pt.y, t);
            } else {
                newPath = ClusterPathPoint.createPoint(location.x, location.y, t);
            }

            newPath.setStereoDisparity(disparity);
            path.add(newPath);
            //            System.out.println("Added Path ("+location.x + ", "+location.y+") @"+t);
            if (path.size() > getPathLength()) {
                path.remove(path.get(0));
            }
            updateVelocity();
        }

        /**
         * Updates velocities of cluster.
         *
         * @param t current timestamp.
         */
        private void updateVelocity() {
            velocityFitter.update();
            if (velocityFitter.valid) {
                velocityPPT.x = (float) velocityFitter.getXVelocity();
                velocityPPT.y = (float) velocityFitter.getYVelocity();
                velocityPPS.x = (float) (velocityFitter.getXVelocity() * VELPPS_SCALING);
                velocityPPS.y = (float) (velocityFitter.getYVelocity() * VELPPS_SCALING);
                velocityValid = true;
            } else {
                velocityValid = false;
            }
            superPos.x += (superPosIntFactor * velocityPPS.x) / 1000;
            superPos.y += (superPosIntFactor * velocityPPS.y) / 1000;
            if ((superPos.x - location.x) > maxSuperPos) {
                superPos.x = location.x + maxSuperPos;
            }
            if ((location.x - superPos.x) > maxSuperPos) {
                superPos.x = location.x - maxSuperPos;
            }
            if ((superPos.y - location.y) > maxSuperPos) {
                superPos.y = location.y + maxSuperPos;
            }
            if ((location.y - superPos.y) > maxSuperPos) {
                superPos.y = location.y - maxSuperPos;
            }
        }

        @Override
        public String toString() {
            return String.format("Cluster #=%d, location = (%d, %d), mass = %.2f, ageUs = %d, lifeTime = %d",
                    clusterNumber,
                    (int) location.x, (int) location.y,
                    mass, ageUs, getLifetime());
        }

        @Override
        public ArrayList<ClusterPathPoint> getPath() {
            return path;
        }

        /**
         *
         * @return color
         */
        public Color getColor() {
            return color;
        }

        /**
         *
         * @param color
         */
        public void setColor(Color color) {
            this.color = color;
        }

        /**
         * Returns velocities of cluster in pixels per second.
         *
         * @return averaged velocities of cluster in pixels per second.
         * <p>
         * The method of measuring velocities is based on a linear regression of
         * a number of previous cluter locations.
         * @see #getVelocityPPT()
         *
         */
        @Override
        public Point2D.Float getVelocity() {
            return velocityPPS;
            /* old method for velocities estimation is as follows
			 * The velocities is instantaneously
			 * computed from the movement of the cluster caused by the last event, then this velocities is mixed
			 * with the the old velocities by the mixing factor. Thus the mixing factor is appplied twice: once for moving
			 * the cluster and again for changing the velocities.
			 * */
        }

        /**
         * Computes and returns speed of cluster in pixels per second.
         *
         * @return speed in pixels per second.
         */
        @Override
        public float getSpeedPPS() {
            return (float) Math.sqrt((velocityPPS.x * velocityPPS.x) + (velocityPPS.y * velocityPPS.y));
        }

        /**
         * Computes and returns speed of cluster in pixels per timestamp tick.
         *
         * @return speed in pixels per timestamp tick.
         */
        public float getSpeedPPT() {
            return (float) Math.sqrt((velocityPPT.x * velocityPPT.x) + (velocityPPT.y * velocityPPT.y));
        }

        /**
         * returns the cluster number
         *
         * @return
         */
        public int getClusterNumber() {
            return clusterNumber;
        }

        /**
         * returns the superPos
         *
         * @return
         */
        public Point2D.Float getSuperPos() {
            return superPos;
        }

        /**
         * set the cluster number
         *
         * @param clusterNumber
         */
        public void setClusterNumber(int clusterNumber) {
            this.clusterNumber = clusterNumber;
        }

        /**
         * getString the age of cluster.
         *
         * @return age of cluster in us
         */
        public int getAgeUs() {
            return ageUs;
        }

        /**
         * increases the age of cluster. Age increases twice faster than it
         * decreases.
         *
         * @param deltaAge
         * @return
         */
        public int increaseAgeUs(int deltaAge) {
            if (deltaAge > 0) {
                ageUs += 2 * deltaAge;
            } else {
                ageUs += deltaAge;
            }

            if (ageUs > (int) (1000 * maximumClusterLifetimeMs)) {
                ageUs = (int) (1000 * maximumClusterLifetimeMs);
            }

            return ageUs;
        }

        /**
         * returns true if the cluster age is greater than 1. So, the cluster is
         * visible after it has been updated at least once after created.
         *
         * @return true if the cluster age is greater than 1
         */
        @Override
        public boolean isVisible() {
            if (getAgeUs() > 0) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * returns true if the cluster is dead
         *
         * @return
         */
        public boolean isDead() {
            return dead;
        }

        @Override
        public float getArea() {
            return getRadius() * getRadius() * 4;
        }

        /**
         * Does a moving or rolling linear regression (a linear fit) on updated
         * ClusterPathPoint data. The new data point replaces the oldest data
         * point. Summary statistics holds the rollling values and are updated
         * by subtracting the oldest point and adding the newest one. From
         * <a href="http://en.wikipedia.org/wiki/Ordinary_least_squares#Summarizing_the_data">Wikipedia
         * article on Ordinary least squares</a>.
         * <p>
         * If velocityPPT cannot be estimated (e.g. due to only 2 identical
         * points) it is not updated.
         *
         * @author tobi
         */
        private class RollingVelocityFitter {

            private static final int LENGTH_DEFAULT = 5;
            private int length = LENGTH_DEFAULT;
            private double st = 0, sx = 0, sy = 0, stt = 0, sxt = 0, syt = 0, den = 1; // summary stats
            private ArrayList<ClusterPathPoint> points;
            private double xVelocityPPT = 0, yVelocityPPT = 0;
            private boolean valid = false;
            private int nPoints = 0;

            /**
             * Creates a new instance of RollingLinearRegression
             */
            public RollingVelocityFitter(ArrayList<ClusterPathPoint> points, int length) {
                this.points = points;
                this.length = length;
            }

            @Override
            public String toString() {
                return String.format("RollingVelocityFitter: \n" + "valid=%s nPoints=%d\n"
                        + "xVel=%e, yVel=%e\n"
                        + "st=%f sx=%f sy=%f, sxt=%f syt=%f den=%f",
                        valid, nPoints,
                        xVelocityPPT, yVelocityPPT,
                        st, sx, sy, sxt, syt, den);

            }

            /**
             * Updates estimated velocityPPT based on last point in path. If
             * velocityPPT cannot be estimated it is not updated.
             *
             * @param t current timestamp.
             */
            private synchronized void update() {
                int n = points.size();
                if (n < 1) {
                    return;
                }
                ClusterPathPoint p = points.get(n - 1); // take last point
                if (p.getNEvents() == 0) {
                    return;
                }
                nPoints++;
                if (n > length) {
                    removeOldestPoint(); // discard data beyond range length
                }
                n = n > length ? length : n;  // n grows to max length
                float dt = p.getT() - firstUpdateTimestamp; // t is time since cluster formed, limits absolute t for numerics
                st += dt;
                sx += p.x;
                sy += p.y;
                stt += dt * dt;
                sxt += p.x * dt;
                syt += p.y * dt;
                //                if(n<length) return; // don't estimate velocityPPT until we have all necessary points, results very noisy and send cluster off to infinity very often, would give NaN
                den = ((n * stt) - (st * st));
                if ((n >= length) && (den != 0)) {
                    valid = true;
                    xVelocityPPT = ((n * sxt) - (st * sx)) / den;
                    if (Math.abs(xVelocityPPT) < 1e-7) {
                        xVelocityPPT = 0;  // set velocities zero if it's under the precision of float type
                    }
                    yVelocityPPT = ((n * syt) - (st * sy)) / den;
                    if (Math.abs(yVelocityPPT) < 1e-7) {
                        yVelocityPPT = 0;  // set velocities zero if it's under the precision of float type
                    }
                    p.velocity = new Point2D.Float((float) xVelocityPPT*1e6f, (float) yVelocityPPT*1e6f);
                } else {
                    valid = false;
                }
                //                System.out.println(this.toString());
            }

            private void removeOldestPoint() {
                // takes away from summary states the oldest point
                ClusterPathPoint p = points.get(points.size() - length - 1);
                // if points has 5 points (0-4), length=3, then remove points(5-3-1)=points(1) leaving 2-4 which is correct
                float dt = p.getT() - firstUpdateTimestamp;
                st -= dt;
                sx -= p.x;
                sy -= p.y;
                stt -= dt * dt;
                sxt -= p.x * dt;
                syt -= p.y * dt;
            }

            int getLength() {
                return length;
            }

            /**
             * Sets the window length. Clears the accumulated data.
             *
             * @param length the number of points to fit
             * @see #LENGTH_DEFAULT
             */
            synchronized void setLength(int length) {
                this.length = length;
            }

            public double getXVelocity() {
                return xVelocityPPT;
            }

            public double getYVelocity() {
                return yVelocityPPT;
            }

            /**
             * Returns true if the last estimate resulted in a valid measurement
             * (false when e.g. there are only two identical measurements)
             */
            public boolean isValid() {
                return valid;
            }

            public void setValid(boolean valid) {
                this.valid = valid;
            }
        } // rolling velocityPPT fitter

        /**
         * Returns birth location of cluster: initially the first event and
         * later, after cluster becomes visible, it is the location when it
         * becomes visible, which is presumably less noisy.
         *
         * @return x,y location.
         */
        public Point2D.Float getBirthLocation() {
            return birthLocation;
        }

        /**
         * Returns first timestamp of cluster.
         *
         * @return timestamp of birth location.
         */
        public int getBirthTime() {
            return firstUpdateTimestamp;
        }

        /**
         * set birth location of the cluster
         *
         * @param birthLocation
         */
        public void setBirthLocation(Point2D.Float birthLocation) {
            this.birthLocation = birthLocation;
        }

        /**
         * This flog is set true after a velocityPPT has been computed for the
         * cluster. This may take several packets.
         *
         * @return true if valid.
         */
        public boolean isVelocityValid() {
            return velocityValid;
        }

        /**
         * set validity of velocity
         *
         * @param velocityValid
         */
        public void setVelocityValid(boolean velocityValid) {
            this.velocityValid = velocityValid;
        }
    } // end of Cluster

    /**
     * returns clusters
     *
     */
    @Override
    public java.util.List<Cluster> getClusters() {
        return clusters;
    }

    /**
     * @param x x location of pixel
     * @param y y location
     * @param fr the frame data
     * @param channel the RGB channel number 0-2
     * @param brightness the brightness 0-1
     */
    private void colorPixel(final int x, final int y, final float[][][] fr, int channel, Color color) {
        if ((y < 0) || (y > (fr.length - 1)) || (x < 0) || (x > (fr[0].length - 1))) {
            return;
        }
        float[] rgb = color.getRGBColorComponents(null);
        float[] f = fr[y][x];
        System.arraycopy(rgb, 0, f, 0, 3);
    }

    @Override
    public void resetFilter() {
        getEnclosedFilter().resetFilter();
        clusters.clear();
        clusterCounter = 0;
    }

    /**
     * @return @see #setPathsEnabled
     */
    public boolean isPathsEnabled() {
        return pathsEnabled;
    }

    /**
     * Enable cluster history paths. The path of each cluster is stored as a
     * list of points at the end of each cluster list update. This option is
     * required (and set true) if useVelocity is set true.
     *
     * @param pathsEnabled true to show the history of the cluster locations on
     * each packet.
     */
    public void setPathsEnabled(boolean pathsEnabled) {
        getSupport().firePropertyChange("pathsEnabled", this.pathsEnabled, pathsEnabled);
        this.pathsEnabled = pathsEnabled;
        getPrefs().putBoolean("BlurringTunnelTracker.pathsEnabled", pathsEnabled);
    }

    /**
     * Processes the events if the Observable is an EventFilter2D.
     *
     * @param o
     * @param arg an UpdateMessage if caller is notify from EventFilter2D.
     */
    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof BlurringTunnelFilter) {
            NeuronGroup tmpNeuronGroup = null;
            Collection<NeuronGroup> ngCollection = bfilter.getNeuronGroups();
            HashSet<NeuronGroup> ngListForPrune = new HashSet<NeuronGroup>();
            UpdateMessage msg = (UpdateMessage) arg;

            int defaultUpdateInterval = (int) Math.min(msg.packet.getDurationUs(), 1000 * chip.getFilterChain().getUpdateIntervalMs());

            for (Cluster c : clusters) {
                tmpNeuronGroup = null;
                Iterator itr = ngCollection.iterator();
                int updateInterval = msg.timestamp - c.getPath().get(c.getPath().size() - 1).getT();

                if (!c.dead) {
                    while (itr.hasNext()) {
                        NeuronGroup ng = (NeuronGroup) itr.next();

                        if (c.doesCover(ng, bfilter.halfReceptiveFieldSizePixels) && !ng.isMatched()) { // If there are multiple neuron groups under coverage of this cluster, merge all groups into one
                            if (tmpNeuronGroup == null) {
                                tmpNeuronGroup = ng;
                                ng.setMatched(true);
                            } else {
                                tmpNeuronGroup.merge(ng);
                                ngListForPrune.add(ng);
                            }
                        }
                    }
                }
                if (tmpNeuronGroup != null) {
                    c.addGroup(tmpNeuronGroup);
                    c.setUpdated(true);
                    ngListForPrune.add(tmpNeuronGroup);
                } else {
                    c.increaseAgeUs(-updateInterval);
                }

                // clean up the used neuron groups
                ngCollection.removeAll(ngListForPrune);
                ngListForPrune.clear();

                if ((c.getAgeUs() <= 0) || c.dead) {
                    if (!c.dead) {
                        c.dead = true;
                    } else {
                        pruneList.add(c);
                    }
                }
            }

            // Create cluster for the rest neuron groups
            if (!ngCollection.isEmpty()) {
                for (NeuronGroup ng : ngCollection) {
                    track(ng, defaultUpdateInterval);
                }
            }

            updateClusterList(msg.timestamp);
            callUpdateObservers(msg.packet, msg.timestamp); // callback to update() of any listeners on us, e.g. VirtualDrummer

        } else if (o instanceof AEChip) {
            initFilter();
        }
    }

    /**
     *
     * @param gl
     * @param x
     * @param y
     * @param radius
     */
    protected void drawBox(GL2 gl, int x, int y, int radius) {
        gl.glPushMatrix();
        gl.glTranslatef(x, y, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(-radius, -radius);
            gl.glVertex2i(+radius, -radius);
            gl.glVertex2i(+radius, +radius);
            gl.glVertex2i(-radius, +radius);
        }
        gl.glEnd();
        gl.glPopMatrix();
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled()) {
            return;

        }
        GL2 gl = drawable.getGL().getGL2(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
        if (gl == null) {
            log.warning("null GL in BlurringTunnelTracker.annotate");
            return;
        }
        gl.glPushMatrix();
        try {
            for (int i = 0; i < clusters.size(); i++) {
                Cluster c = clusters.get(i);
                if (showClusters && c.isVisible()) {
                    c.draw(drawable);
                }
                // text annoations on clusters, setup
                final int font = GLUT.BITMAP_HELVETICA_18;
                gl.glColor3f(1, 1, 1);
                gl.glRasterPos3f(chip.getSizeX() / 2, chip.getSizeY() + 10, 0);

                // annotate the cluster with hash ID
                if (sendClusterInfo) {
                    chip.getCanvas().getGlut().glutBitmapString(font, String.format("Flow: %d", flow));
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }

    /**
     * Use cluster velocityPPT to estimate the location of cluster. This is
     * useful to select neuron groups to take into this cluster.
     *
     * @param useVelocity
     * @see #setPathsEnabled(boolean)
     */
    public void setUseVelocity(boolean useVelocity) {
        if (useVelocity) {
            setPathsEnabled(true);
        }
        getSupport().firePropertyChange("useVelocity", this.useVelocity, useVelocity);
        this.useVelocity = useVelocity;
        getPrefs().putBoolean("BlurringTunnelTracker.useVelocity", useVelocity);
    }

    /**
     *
     * @return
     */
    public boolean isUseVelocity() {
        return useVelocity;
    }

    /**
     * returns true of the cluster is visible on the screen
     *
     * @return
     */
    public boolean isShowClusters() {
        return showClusters;
    }

    /**
     * Sets annotation visibility of clusters that are not "visible"
     *
     * @param showClusters
     */
    public void setShowClusters(boolean showClusters) {
        this.showClusters = showClusters;
        getPrefs().putBoolean("BlurringTunnelTracker.showClusters", showClusters);
    }

    /**
     * returns path length
     *
     * @return
     */
    public int getPathLength() {
        return pathLength;
    }

    /**
     * Sets the maximum number of path points recorded for each cluster. The
     * {@link Cluster#path} list of points is adjusted to be at most
     * <code>pathLength</code> long.
     *
     * @param pathLength the number of recorded path points. If <2, set to 2.
     */
    synchronized public void setPathLength(int pathLength) {
        if (pathLength < 2) {
            pathLength = 2;
        }
        int old = this.pathLength;
        this.pathLength = pathLength;
        getPrefs().putInt("BlurringTunnelTracker.pathLength", pathLength);
        getSupport().firePropertyChange("pathLength", old, pathLength);
        if (numVelocityPoints > pathLength) {
            setNumVelocityPoints(pathLength);
        }
    }

    /**
     * @return the velAngDiffDegToNotMerge
     */
    public float getVelAngDiffDegToNotMerge() {
        return velAngDiffDegToNotMerge;
    }

    /**
     * @param velAngDiffDegToNotMerge the velAngDiffDegToNotMerge to set
     */
    public void setVelAngDiffDegToNotMerge(float velAngDiffDegToNotMerge) {
        if (velAngDiffDegToNotMerge < 0) {
            velAngDiffDegToNotMerge = 0;
        } else if (velAngDiffDegToNotMerge > 180) {
            velAngDiffDegToNotMerge = 180;
        }
        this.velAngDiffDegToNotMerge = velAngDiffDegToNotMerge;
        getPrefs().putFloat("BlurringTunnelTracker.velAngDiffDegToNotMerge", velAngDiffDegToNotMerge);
    }

    /**
     * returns enableMerge
     *
     * @return
     */
    public boolean isEnableMerge() {
        return enableMerge;
    }

    /**
     * sets enableMerge
     *
     * @param enableMerge
     */
    public void setEnableMerge(boolean enableMerge) {
        this.enableMerge = enableMerge;
        getPrefs().putBoolean("BlurringTunnelTracker.enableMerge", enableMerge);
    }

    /**
     * @return the showClusterNumber
     */
    public boolean isShowClusterNumber() {
        return showClusterNumber;
    }

    /**
     * @param showClusterNumber the showClusterNumber to set
     */
    public void setShowClusterNumber(boolean showClusterNumber) {
        this.showClusterNumber = showClusterNumber;
        getPrefs().putBoolean("BlurringTunnelTracker.showClusterNumber", showClusterNumber);
    }

    /**
     * @return the showClusterVelocity
     */
    public boolean isShowClusterVelocity() {
        return showClusterVelocity;
    }

    /**
     * @param showClusterVelocity the showClusterVelocity to set
     */
    public void setShowClusterVelocity(boolean showClusterVelocity) {
        this.showClusterVelocity = showClusterVelocity;
        getPrefs().putBoolean("BlurringTunnelTracker.showClusterVelocity", showClusterVelocity);
    }

    /**
     * @return the velocityVectorScaling
     */
    public float getVelocityVectorScaling() {
        return velocityVectorScaling;
    }

    /**
     * @param velocityVectorScaling the velocityVectorScaling to set
     */
    public void setVelocityVectorScaling(float velocityVectorScaling) {
        this.velocityVectorScaling = velocityVectorScaling;
        getPrefs().putFloat("BlurringTunnelTracker.velocityVectorScaling", velocityVectorScaling);
    }

    /**
     *
     * @return
     */
    public float getMaximumClusterLifetimeMs() {
        return maximumClusterLifetimeMs;
    }

    /**
     *
     * @param maximumClusterLifetimeMs
     */
    public void setMaximumClusterLifetimeMs(float maximumClusterLifetimeMs) {
        float old = this.maximumClusterLifetimeMs;
        this.maximumClusterLifetimeMs = maximumClusterLifetimeMs;
        getPrefs().putFloat("BlurringTunnelTracker.maximumClusterLifetimeMs", maximumClusterLifetimeMs);
        getSupport().firePropertyChange("maximumClusterLifetimeMs", old, this.maximumClusterLifetimeMs);
    }

    /**
     * @return the showClusterMass
     */
    public boolean isShowClusterMass() {
        return showClusterMass;
    }

    /**
     * @param showClusterMass the showClusterMass to set
     */
    public void setShowClusterMass(boolean showClusterMass) {
        this.showClusterMass = showClusterMass;
        getPrefs().putBoolean("BlurringTunnelTracker.showClusterMass", showClusterMass);
    }

    /**
     * returns clusterRadiusLifetimeMs
     *
     * @return
     */
    public float getClusterRadiusLifetimeMs() {
        return clusterRadiusLifetimeMs;
    }

    /**
     * sets clusterRadiusLifetimeMs
     *
     * @param clusterRadiusLifetimeMs
     */
    public void setClusterRadiusLifetimeMs(float clusterRadiusLifetimeMs) {
        float old = maximumClusterLifetimeMs;
        this.clusterRadiusLifetimeMs = clusterRadiusLifetimeMs;
        getPrefs().putFloat("BlurringTunnelTracker.clusterRadiusLifetimeMs", clusterRadiusLifetimeMs);
        getSupport().firePropertyChange("clusterRadiusLifetimeMs", old, this.clusterRadiusLifetimeMs);
    }

    /**
     * returns minimumClusterSizePixels
     *
     * @return
     */
    public int getMinimumClusterSizePixels() {
        return minimumClusterSizePixels;
    }

    /**
     * sets minimumClusterSizePixels
     *
     * @param minimumClusterSizePixels
     */
    public void setMinimumClusterSizePixels(int minimumClusterSizePixels) {
        int old = this.minimumClusterSizePixels;
        this.minimumClusterSizePixels = minimumClusterSizePixels;
        getPrefs().putInt("BlurringTunnelTracker.minimumClusterSizePixels", minimumClusterSizePixels);
        getSupport().firePropertyChange("minimumClusterSizePixels", old, this.minimumClusterSizePixels);
    }

    /**
     * @see #setNumVelocityPoints(int)
     *
     * @return number of points used to estimate velocities.
     */
    public int getNumVelocityPoints() {
        return numVelocityPoints;
    }

    /**
     * Sets the number of path points to use to estimate cluster velocities.
     *
     * @param velocityPoints the number of points to use to estimate velocities.
     * Bounded above to number of path points that are stored.
     * @see #setPathLength(int)
     * @see #setPathsEnabled(boolean)
     */
    public void setNumVelocityPoints(int velocityPoints) {
        if (velocityPoints >= pathLength) {
            velocityPoints = pathLength;
        }
        int old = numVelocityPoints;
        numVelocityPoints = velocityPoints;
        getPrefs().putInt("BlurringTunnelTracker.numVelocityPoints", velocityPoints);
        getSupport().firePropertyChange("velocityPoints", old, numVelocityPoints);
    }

    public float getTauLPFMs() {
        return tauLPFMs;
    }

    public void setTauLPFMs(float tauLPFMs) {
        this.tauLPFMs = tauLPFMs;
        float old = this.tauLPFMs;
        this.tauLPFMs = tauLPFMs;
        getPrefs().putFloat("BlurringTunnelTracker.tauLPFMs", tauLPFMs);
        getSupport().firePropertyChange("tauLPFMs", old, this.tauLPFMs);
    }

    public boolean getOscEnabled() {
        return oscEnabled;
    }

    public void setOscEnabled(boolean oscEnabled) {
        this.oscEnabled = oscEnabled;
        getPrefs().putBoolean("BlurringTunnelTracker.oscEnabled", oscEnabled);
    }

    public int getMinClusterAge() {
        return minClusterAge;
    }

    public void setMinClusterAge(int minClusterAge) {
        int old = this.minClusterAge;
        this.minClusterAge = minClusterAge;
        getPrefs().putInt("BlurringTunnelTracker.minClusterAge", minClusterAge);
        getSupport().firePropertyChange("minClusterAge", old, this.minClusterAge);
    }

    public boolean getSendClusterInfo() {
        return sendClusterInfo;
    }

    public void setSendClusterInfo(boolean sendClusterInfo) {
        this.sendClusterInfo = sendClusterInfo;
        getPrefs().putBoolean("BlurringTunnelTracker.sendPosition", sendClusterInfo);
    }

    public boolean getSendActivity() {
        return sendActivity;
    }

    public void setSendActivity(boolean sendActivity) {
        this.sendActivity = sendActivity;
        getPrefs().putBoolean("BlurringTunnelTracker.sendActivity", sendActivity);
    }

    public boolean getGetVVVVstate() {
        return getVVVVstate;
    }

    public void setGetVVVVstate(boolean getVVVVstate) {
        this.getVVVVstate = getVVVVstate;
        getPrefs().putBoolean("BlurringTunnelTracker.getVVVVstate", getVVVVstate);
    }

    public float getActivityDecayFactor() {
        return activityDecayFactor;
    }

    public void setActivityDecayFactor(float activityDecayFactor) {
        float old = this.activityDecayFactor;
        this.activityDecayFactor = activityDecayFactor;
        getPrefs().putFloat("BlurringTunnelTracker.activityDecayFactor", activityDecayFactor);
        getSupport().firePropertyChange("activityDecayFactor", old, this.activityDecayFactor);
    }

    public float getSuperPosIntFactor() {
        return superPosIntFactor;
    }

    public void setSuperPosIntFactor(float superPosIntFactor) {
        float old = this.superPosIntFactor;
        this.superPosIntFactor = superPosIntFactor;
        getPrefs().putFloat("BlurringTunnelTracker.superPosIntFactor", superPosIntFactor);
        getSupport().firePropertyChange("superPosIntFactor", old, this.superPosIntFactor);
    }

    public float getMaxSuperPos() {
        return maxSuperPos;
    }

    public void setMaxSuperPos(float maxSuperPos) {
        float old = this.maxSuperPos;
        this.maxSuperPos = maxSuperPos;
        getPrefs().putFloat("BlurringTunnelTracker.maxSuperPos", maxSuperPos);
        getSupport().firePropertyChange("maxSuperPos", old, this.maxSuperPos);
    }

    public float getFlowThreshold() {
        return flowThreshold;
    }

    public void setFlowThreshold(float flowThreshold) {
        float old = this.flowThreshold;
        this.flowThreshold = flowThreshold;
        getPrefs().putFloat("BlurringTunnelTracker.flowThreshold", flowThreshold);
        getSupport().firePropertyChange("flowThreshold", old, this.flowThreshold);
    }
}
