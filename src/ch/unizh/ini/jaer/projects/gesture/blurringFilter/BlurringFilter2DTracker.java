/*
 * Last updated on April 23, 2010, 11:40 AM
 *
 *  * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.blurringFilter;
import ch.unizh.ini.jaer.projects.gesture.blurringFilter.BlurringFilter2D.NeuronGroup;
import com.sun.opengl.util.GLUT;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.*;
import net.sf.jaer.graphics.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import javax.media.opengl.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Tracks moving objects. Modified from BlurringFilter2DTracker.java
 *
 * @author Jun Haeng Lee/Tobi Delbruck
 */
public class BlurringFilter2DTracker extends EventFilter2D implements FrameAnnotater,Observer,ClusterTrackerInterface /*, PreferenceChangeListener*/{
    // TODO split out the optical gryo stuff into its own subclass
    // TODO split out the Cluster object as it's own class.
    /**
     *
     * @return filter description
     */
    public static String getDescription (){
        return "Tracks moving hands, which means it tracks two object at most";
    }

    /**
     * Maximum numver of clusters to track
     */
    public static enum NUM_CLUSTERS{
        /**
         * single cluster
         */
        SINGLE,
        /**
         * a couple of clusters
         */
        COUPLE,
        /**
         * no limit
         */
        MANY
    }

    /**
     * global parameters
     */
    private int minimumClusterSizePixels = getPrefs().getInt("BluringFilter2DTracker.minimumClusterSizePixels",32);
    private int maximumClusterSizePixels = getPrefs().getInt("BluringFilter2DTracker.maximumClusterSizePixels",48);
    private float maximumClusterLifetimeMs = getPrefs().getFloat("BluringFilter2DTracker.maximumClusterLifetimeMs",10.0f);
    private float clusterRadiusLifetimeMs = getPrefs().getFloat("BluringFilter2DTracker.clusterRadiusLifetimeMs",200.0f);
    private NUM_CLUSTERS maxNumClusters = NUM_CLUSTERS.valueOf(getPrefs().get("BlurringFilter2D.maxNumClusters", NUM_CLUSTERS.SINGLE.toString()));


    /**
     * select motion detection
     */
    private int selectMotionLastingTimeMs = getPrefs().getInt("BluringFilter2DTracker.selectMotionLastingTimeMs",250);
    private int selectMotionDetectionAreaSizePixels = getPrefs().getInt("BluringFilter2DTracker.selectMotionDetectionAreaSizePixels",20);
    private int clusterLifeTimeMsInSelectMotion = getPrefs().getInt("BluringFilter2DTracker.clusterLifeTimeMsInSelectMotion",100);
    private int selectStayLastingTimeMs = getPrefs().getInt("BluringFilter2DTracker.selectStayLastingTimeMs",1000);


    /**
     * display parameters
     */
    private boolean pathsEnabled = getPrefs().getBoolean("BluringFilter2DTracker.pathsEnabled",true);
    private int pathLength = getPrefs().getInt("BluringFilter2DTracker.pathLength",100);
    private boolean showClusterNumber = getPrefs().getBoolean("BluringFilter2DTracker.showClusterNumber",false);
    private boolean showClusterVelocity = getPrefs().getBoolean("BluringFilter2DTracker.showClusterVelocity",false);
    private boolean showClusters = getPrefs().getBoolean("BluringFilter2DTracker.showClusters",false);
    private boolean showClusterMass = getPrefs().getBoolean("BluringFilter2DTracker.showClusterMass",false);
    private float velocityVectorScaling = getPrefs().getFloat("BluringFilter2DTracker.velocityVectorScaling",1.0f);

    /**
     * movement parameters
     */
    private int numVelocityPoints = getPrefs().getInt("BluringFilter2DTracker.numVelocityPoints",3);
    private boolean useVelocity = getPrefs().getBoolean("BluringFilter2DTracker.useVelocity",true); // enabling this enables both computation and rendering of cluster velocities

    /**
     * update parameters
     */
    private float velAngDiffDegToNotMerge = getPrefs().getFloat("BluringFilter2DTracker.velAngDiffDegToNotMerge",60.0f);
    private boolean enableMerge = getPrefs().getBoolean("BluringFilter2DTracker.enableMerge",false);
    
    /**
     * Trajectory persistance parameters
     */
    private boolean enableTrjPersLimit = getPrefs().getBoolean("BluringFilter2DTracker.enableTrjPersLimit",false);
    private int maxmumTrjPersTimeMs = getPrefs().getInt("BluringFilter2DTracker.maxmumTrjPersTimeMs",1500);

    /**
     * Subthreshold tracking
     */
    private boolean enableSubThTracking = getPrefs().getBoolean("BluringFilter2DTracker.enableSubThTracking",true);
    private int subThTrackingActivationTimeMs = getPrefs().getInt("BluringFilter2DTracker.subThTrackingActivationTimeMs",1500);
    private int stationaryLifeTimeMs = getPrefs().getInt("BluringFilter2DTracker.stationaryLifeTimeMs",3000);

    /**
     * The list of clusters.
     */
    protected java.util.List<Cluster> clusters = new LinkedList();
    /**
     * Blurring filter to getString clusters
     */
    public BlurringFilter2D bfilter;
    /**
     *  clusters to be destroyed
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
    /**
     * to scale rendering of cluster velocityPPT vector, velocityPPT is in pixels/tick=pixels/us so this gives 1 screen pixel per 1 pix/s actual vel
     */
    public final float VELOCITY_VECTOR_SCALING = 1e6f;

    /**
     * Creates a new instance of BlurringFilter2DTracker.
     * @param chip
     */
    public BlurringFilter2DTracker (AEChip chip){
        super(chip);
        this.chip = chip;
        initFilter();
        chip.addObserver(this);
        final String movement = "Movement", disp = "Display", 
                     global = "Global", update = "Update", trjlimit = "Trajectory persistance",
                     subThTracking = "Subthreshold tracking", selMotionDet = "SelectMotion detection";
        setPropertyTooltip(global,"minimumClusterSizePixels","minimum size of a squre Cluster.");
        setPropertyTooltip(global,"maximumClusterSizePixels","maximum size of a squre Cluster.");
        setPropertyTooltip(global,"maximumClusterLifetimeMs","upper limit of cluster lifetime. It increases by when the cluster is properly updated. Otherwise, it decreases. When the lifetime becomes zero, the cluster will be expired.");
        setPropertyTooltip(global,"clusterRadiusLifetimeMs","time constant of the cluster radius.");
        setPropertyTooltip(global,"maxNumClusters","the maximum allowed number of clusters");

        setPropertyTooltip(selMotionDet,"selectMotionDetectionAreaSizePixels","size of area to detect select motion.");
        setPropertyTooltip(selMotionDet,"clusterLifeTimeMsInSelectMotion","cluster life time to detect select motion.");
        setPropertyTooltip(selMotionDet,"selectMotionLastingTimeMs","time duration of select motion to be detected.");
        setPropertyTooltip(selMotionDet,"selectStayLastingTimeMs","time duration of stay to select.");

        setPropertyTooltip(disp,"pathsEnabled","draws paths of clusters over some window");
        setPropertyTooltip(disp,"pathLength","paths are at most this many packets long");
        setPropertyTooltip(disp,"showClusters","shows clusters");
        setPropertyTooltip(disp,"showClusterVelocity","annotates velocity in pixels/second");
        setPropertyTooltip(disp,"showClusterNumber","shows cluster ID number");
        setPropertyTooltip(disp,"showClusterMass","shows cluster mass");
        setPropertyTooltip(disp,"velocityVectorScaling","scaling of drawn velocity vectors");

        setPropertyTooltip(movement,"numVelocityPoints","the number of recent path points (one per packet of events) to use for velocity vector regression");
        setPropertyTooltip(movement,"useVelocity","uses measured cluster velocity to predict future position; vectors are scaled " + String.format("%.1f pix/pix/s",VELOCITY_VECTOR_SCALING / AEConstants.TICK_DEFAULT_US * 1e-6));

        setPropertyTooltip(update,"velAngDiffDegToNotMerge","relative angle in degrees of cluster velocity vectors to not merge overlapping clusters");
        setPropertyTooltip(update,"enableMerge","enable merging overlapping clusters");

        setPropertyTooltip(trjlimit,"enableTrjPersLimit","enable limiting the maximum persistance time of trajectory");
        setPropertyTooltip(trjlimit,"maxmumTrjPersTimeMs","maximum persistance time of trajectory in msec");
        
        setPropertyTooltip(subThTracking,"enableSubThTracking","enable subthreshold tracking.");
        setPropertyTooltip(subThTracking,"subThTrackingActivationTimeMs","goes to the subthreshold tracking mode after the life time of the cluster is larger than this amount of time.");
        setPropertyTooltip(subThTracking,"stationaryLifeTimeMs","subthreshold tracking will be expired when this amount of time is passed since the cluster location has not changed.");

        filterChainSetting();
    }

    /**
     * sets the BlurringFilter2D as a enclosed filter to find cluster
     */
    protected void filterChainSetting (){
        bfilter = new BlurringFilter2D(chip);
        bfilter.addObserver(this); // to getString us called during blurring filter iteration at least every updateIntervalUs
        setEnclosedFilter(bfilter);
    }

    /**
     * merge clusters that are too close to each other and that have sufficiently similar velocities (if velocityRatioToNotMergeClusters).
    this must be done interatively, because merging 4 or more clusters feedforward can result in more clusters than
    you start with. each time we merge two clusters, we start over, until there are no more merges on iteration.
    for each cluster, if it is close to another cluster then merge them and start over.
     */
    private void mergeClusters (){
        if(clusters.size() < 2)
            return;
        
        boolean mergePending;
        Cluster c1 = null;
        Cluster c2 = null;
        do{
            mergePending = false;
            int nc = clusters.size();
            outer:
            for ( int i = 0 ; i < nc ; i++ ){
                c1 = clusters.get(i);
                if(c1.dead)
                    continue;
                for ( int j = i + 1 ; j < nc ; j++ ){
                    c2 = clusters.get(j); // getString the other cluster
                    if(c2.dead)
                        continue;
                    final boolean overlapping = c1.distanceTo(c2) < ( c1.getRadius() + c2.getRadius() );
                    boolean velSimilar = true; // start assuming velocities are similar
                    if ( overlapping && velAngDiffDegToNotMerge > 0 && c1.isVelocityValid() && c2.isVelocityValid() && c1.velocityAngleTo(c2) > velAngDiffDegToNotMerge * Math.PI / 180 ){
                        // if velocities valid for both and velocities are sufficiently different
                        velSimilar = false; // then flag them as different velocities
                    }
                    if ( overlapping && velSimilar ){
                        // if cluster is close to another cluster, merge them
                        // if distance is less than sum of radii merge them and if velAngle < threshold
                        mergePending = true;
                        break outer; // break out of the outer loop
                    }
                }
            }
            if ( mergePending && c1 != null && c2 != null){
                clusters.add(new Cluster(c1,c2));
                clusters.remove(c1);
                clusters.remove(c2);
            }
        } while ( mergePending );

    }

    @Override
    public void initFilter (){
        clusters.clear();
        clusterCounter = 0;
    }

    /**
     * Prunes out old clusters that don't have support or that should be purged for some other reason.
     */
    private void pruneClusters (){
        clusters.removeAll(pruneList);
        pruneList.clear();
    }

    /**
     * This method updates the list of clusters, pruning and
     * merging clusters and updating positions.
     * It also updates the optical gyro if enabled.
     *
     * @param t the global timestamp of the update.
     */
    private void updateClusterList (int t){
        if(enableMerge)
            mergeClusters();
        pruneClusters();
        updateClusterPaths(t);
    }

    /**
     * Processes the incoming events to track clusters.
     *
     * @param in
     * @return packet of BluringFilter2DTrackerEvent.
     */
    @Override
    public EventPacket<?> filterPacket (EventPacket<?> in){
        if ( in == null ){
            return null;
        }

        if ( enclosedFilter != null ){
            out = enclosedFilter.filterPacket(in);
        } else{
            out = in;
        }
        
        return out;
    }

    /**
     * the method that actually does the tracking
     * Tracking is done by selecting the right neuron groups for the next cluster.
     *
     * @param newNeuronGroup : a neuron group detected by BlurringFilter2D
     * @param initialAge 
     */
    protected void trackAGroup (NeuronGroup newNeuronGroup,int initialAge){
        if ( newNeuronGroup.getNumMemberNeurons() == 0 ){
            return;
        }

        // for input neuron group, see which cluster it is closest to and add it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
        Cluster closest = null;
        closest = getNearestCluster(newNeuronGroup); // find cluster that event falls within (or also within surround if scaling enabled)

        if ( closest != null ){
            closest.addGroup(newNeuronGroup, false);
        } else{ // start a new cluster
            clusters.add(new Cluster(newNeuronGroup,initialAge));
        }
    }

    /**
     * Returns total number of clusters.
     *
     * @return number of Cluster's in clusters list.
     */
    public int getNumClusters (){
        return clusters.size();
    }

    @Override
    public String toString (){
        String s = clusters != null ? Integer.toString(clusters.size()) : null;
        String s2 = "BluringFilter2DTracker with " + s + " clusters ";
        return s2;
    }

    /** 
     * finds the nearest cluster from the given neuron group
     * The found cluster will be updated using the neuron group.
     *
     * @param neuronGroup : a neuron group
     * @return closest cluster
     */
    public Cluster getNearestCluster (NeuronGroup neuronGroup){
        float minDistance = Float.MAX_VALUE;
        Cluster closest = null;

        for ( Cluster c:clusters ){
            float dx = c.distanceToX(neuronGroup);
            float dy = c.distanceToY(neuronGroup);
            float aveRadius = ( c.getRadius() + neuronGroup.getOutterRadiusPixels() ) / 2.0f;

            if ( !c.isUpdated() && dx < aveRadius && dy < aveRadius ){
                if ( dx + dy < minDistance ){
                    closest = c;
                    minDistance = dx + dy;
                }
            }
        }

        return closest;
    }

    /** Updates cluster path lists
     *
     * @param t the update timestamp
     */
    protected void updateClusterPaths (int t){
        // update paths of clusters
        for ( Cluster c:clusters ){
            if(!c.dead && c.isUpdated()){
                c.updatePath(t, 0);
                c.setUpdated(false);
            }
        }
    }

    /**
     * Cluster class
     */
    public class Cluster implements ClusterInterface{
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
         * velocityPPT of cluster in pixels/tick, where tick is timestamp tick (usually microseconds)
         */
        protected Point2D.Float velocityPPT = new Point2D.Float();

        /**
         * cluster velocityPPT in pixels/second
         */
        private Point2D.Float velocityPPS = new Point2D.Float();

        /**
         * used to flag invalid or uncomputable velocityPPT
         */
        private boolean velocityValid = false;

        /**
         * radius of the cluster in chip pixels
         */
        private float maxRadius;

        /**
         * cluster area
         */
        private Rectangle clusterArea = new Rectangle();

        /**
         * minimum cluster size
         */
        int minimumClusterSize = minimumClusterSizePixels;

        /**
         * true if the cluster is hitting any adge of the frame
         */
        protected boolean hitEdge = false;

        /**
         * vitality of the cluster. It increases as the cluster is updated, and decreases if it's not updated.
         */
        protected int vitality = 0;

        /**
         * true if the cluster is updated
         */
        protected boolean updated = false;

        /**
         *Rendered color of cluster.
         */
        protected Color color = null;

        /**
         *Number of neurons collected by this cluster.
         */
        protected int numNeurons = 0;

        /**
         *The "mass" of the cluster is the total membrane potential of member neurons.
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
         * assigned to be the absolute number of the cluster that has been created.
         */
        private int clusterNumber;

        /**
         * true if the cluster is dead
         */
        private boolean dead = false;

        /**
         * true if the cluster is moving (i.e, velocity is not zero)
         */
        private boolean onMoving = false;

        private int stationaryTime;

        /**
         * true if the subthreshold tracking mode is on
         */
        private boolean subThTrackingOn = false;

        /**
         * true if the subthreshold tracking mode is possible
         */
        private boolean subThTrackingModeIsPossible = false;

        /**
         * the most recent times in us when the subthreshold tracking mode started.
         */
        private int subThTrackingModeStartTimeUs;

        /**
         * if true, it tries to detect the select-motion in which the hand is staying a certain position making high event rate.
        */
        protected boolean detectingSelectMotion = false;

        /**
         * area of object such as icon
         */
        protected Rectangle.Float selectMotionObjectArea = new Rectangle.Float();

        /**
         * areat to detect select motion. If the location of the cluster gets out of it, this area is updated based on the cluster location
         */
        protected Rectangle selectMotionDetectionArea = new Rectangle();

        /**
         * if true, this cluster can be merged into another
         */
        protected boolean mergeable = true;

        /**
         * options for special purpose
         */
        static final int numOptions = 4;
        private int[] optionInt = new int[ numOptions ];
        private boolean[] optionBoolean = new boolean[ numOptions ];

        /**
         * cluster color
         */
        private float[] rgb = new float[ 4 ];
        
        /**
         * trajectory of the cluster
         */
        protected ArrayList<ClusterPathPoint> path = new ArrayList<ClusterPathPoint>();

        /**
         * velocity fitter
         */
        private VelocityFitter velocityFitter = new VelocityFitter(path, numVelocityPoints);


        @Override
        public int hashCode (){
            return clusterNumber;
        }

        @Override
        public boolean equals (Object obj){
            if ( this == obj ){
                return true;
            }
            if ( ( obj == null ) || ( obj.getClass() != this.getClass() ) ){
                return false;
            }
            // object must be Test at this point
            Cluster test = (Cluster)obj;
            return clusterNumber == test.clusterNumber;
        }

        /**
         * Constructs a default cluster.
         *
         */
        public Cluster (){
            float hue = random.nextFloat();
            Color c = Color.getHSBColor(hue,1f,1f);
            setColor(c);
            setClusterNumber(clusterCounter++);
            maxRadius = 0;
            clusterArea.setRect(0, 0, 0, 0);
            dead = false;
            for(int i = 0; i < numOptions; i++){
                optionBoolean[i] = false;
                optionInt[i] = 0;
            }
        }

        /** 
         * Constructs a cluster with the first neuron group
         * The numEvents, location, birthLocation, first and last timestamps are set.
         * @param ng the neuron group.
         * @param initialAge
         */
        public Cluster (NeuronGroup ng,int initialAge){
            this();
            location.setLocation(ng.location);
            birthLocation.setLocation(ng.location);
            lastUpdateTimestamp = ng.getLastEventTimestamp();
            firstUpdateTimestamp = ng.getLastEventTimestamp();
            numNeurons = ng.getNumMemberNeurons();
            mass = ng.getTotalMP();
            increaseVitality(initialAge);
            setRadius(ng, 0);
            hitEdge = ng.isHitEdge();
            if ( hitEdge ){
                vitality = (int)( 1000 * maximumClusterLifetimeMs );
            }

            updated = true;

//            System.out.println("Cluster_"+clusterNumber+" is created @"+firstUpdateTimestamp);
        }

        /** Constructs a cluster by merging two clusters.
         *
         * @param one the first cluster
         * @param two the second cluster
         */
        public Cluster (Cluster one,Cluster two){
            this();

            Cluster older = one.clusterNumber < two.clusterNumber ? one : two;
            float leakyfactor = one.calMassLeakyfactor(two.lastUpdateTimestamp, 1f);
            float one_mass = one.mass;
            float two_mass = two.mass;

            clusterNumber = older.clusterNumber;

            if(leakyfactor > 1)
                two_mass /= leakyfactor;
            else
                one_mass *= leakyfactor;

            mass = one_mass + two_mass;
            numNeurons = one.numNeurons + two.numNeurons;

            // merge locations by average weighted by totalMP of events supporting each cluster
            location.x = ( one.location.x * one_mass + two.location.x * two_mass ) / ( mass );
            location.y = ( one.location.y * one_mass + two.location.y * two_mass ) / ( mass );

            lastUpdateTimestamp = one.lastUpdateTimestamp > two.lastUpdateTimestamp ? one.lastUpdateTimestamp : two.lastUpdateTimestamp;
            firstUpdateTimestamp = one.firstUpdateTimestamp < two.firstUpdateTimestamp ? one.firstUpdateTimestamp : two.firstUpdateTimestamp;
            path = older.path;
            birthLocation.setLocation(older.birthLocation);
            velocityFitter = older.velocityFitter;
            velocityPPT.setLocation(older.velocityPPT);
            velocityPPS.setLocation(older.velocityPPS);
            velocityValid = older.velocityValid;
            vitality = older.vitality;
            mergeable = one.mergeable | two.mergeable;

            maxRadius = one.mass > two.mass ? one.maxRadius : two.maxRadius;
            setColor(older.getColor());

            hitEdge = one.hasHitEdge() | two.hasHitEdge();
            subThTrackingOn = one.subThTrackingOn | two.subThTrackingOn;
            subThTrackingModeIsPossible = one.subThTrackingModeIsPossible | two.subThTrackingModeIsPossible;
            subThTrackingModeStartTimeUs = one.subThTrackingModeStartTimeUs > two.subThTrackingModeStartTimeUs ? one.subThTrackingModeStartTimeUs : two.subThTrackingModeStartTimeUs;

            for(int i = 0; i < numOptions; i++){
                optionBoolean[i] = one.optionBoolean[i] | two.optionBoolean[i];
                optionInt[i] =  older.optionInt[i];
            }
        }

        /**
         * calculates totalMP leaky factor
         * @param timeStamp
         * @return
         */
        private float calMassLeakyfactor(int timestamp, float timeConstantRatio){
            return (float) Math.exp(((float) lastUpdateTimestamp - timestamp) / (bfilter.getMPTimeConstantUs()*timeConstantRatio));
        }

        /** Draws this cluster using OpenGL.
         *
         * @param drawable area to draw this.
         */
        public void draw (GLAutoDrawable drawable){
            final float BOX_LINE_WIDTH = 2f; // in chip
            final float PATH_POINT_SIZE = 4f;
            final float VEL_LINE_WIDTH = 4f;
            GL gl = drawable.getGL();
            int x = (int)getLocation().x;
            int y = (int)getLocation().y;

            // set color and line width of cluster annotation
            getColor().getRGBComponents(rgb);
            gl.glColor3fv(rgb,0);
            gl.glLineWidth(BOX_LINE_WIDTH);

            // draw cluster rectangle
            drawBox(gl,x,y,(int)maxRadius);

            gl.glPointSize(PATH_POINT_SIZE);

            ArrayList<ClusterPathPoint> points = getPath();
            for ( Point2D.Float p:points ){
                gl.glBegin(GL.GL_POINTS);
                gl.glVertex2f(p.x,p.y);
                gl.glEnd();
            }

            // now draw velocityPPT vector
            if ( showClusterVelocity ){
                gl.glLineWidth(VEL_LINE_WIDTH);
                gl.glBegin(GL.GL_LINES);
                {
                    gl.glVertex2i(x,y);
                    gl.glVertex2f(x + getVelocityPPT().x * VELOCITY_VECTOR_SCALING * velocityVectorScaling,y + getVelocityPPT().y * VELOCITY_VECTOR_SCALING * velocityVectorScaling);
                }
                gl.glEnd();
            }
            // text annoations on clusters, setup
            final int font = GLUT.BITMAP_HELVETICA_18;
            gl.glColor3f(1,1,1);
            gl.glRasterPos3f(location.x,location.y,0);

            // annotate the cluster with hash ID
            if ( showClusterNumber ){
                chip.getCanvas().getGlut().glutBitmapString(font,String.format("#%d",hashCode()));
            }

            //annotate the cluster with the velocityPPT in pps
            if ( showClusterVelocity ){
                Point2D.Float velpps = getVelocityPPS();
                chip.getCanvas().getGlut().glutBitmapString(font,String.format("%.0f,%.0f pps",velpps.x,velpps.y));
            }
        }

        /** Returns true if the cluster center is outside the array 
         * @return true if cluster has hit edge
         */
        private boolean hasHitEdge (){
            return hitEdge;
        }

        /**
         * Cluster velocities in pixels/timestamp tick as a vector. Velocity values are set during cluster upate.
         *
         * @return the velocityPPT in pixels per timestamp tick.
         * @see #getVelocityPPS()
         */
        @Override
        public Point2D.Float getVelocityPPT (){
            return velocityPPT;
        }

        /** returns true if the cluster has been updated
         *
         * @return true if the cluster has been updated
         */
        public boolean isUpdated (){
            return updated;
        }

        /** set true if the cluster has been updated
         *
         * @param updated
         */
        public void setUpdated (boolean updated){
            this.updated = updated;
        }

        /**
         * returns true if this cluster is mergeable
         * @return
         */
        public boolean isMergeable() {
            return mergeable;
        }

        /**
         * sets mergeable
         * @param mergeable
         */
        public void setMergeable(boolean mergeable) {
            this.mergeable = mergeable;
        }

        /**
         * returns true if a request for selection motion detection is pended.
         * @return
         */
        public boolean isDetectingSelectMotion() {
            return detectingSelectMotion;
        }

        /**
         * ask to detect the select motion
         *
         * @param objectArea
         */
        public void detectSelectMotion(Rectangle.Float objectArea) {
            selectMotionObjectArea.setRect(objectArea);
            setSelectMotionDetectionArea();
            detectingSelectMotion = true;
        }

        /**
         * returns true if the cursor is within selectMotionDetectionArea
         *
         * @return
         */
        protected boolean IsWithinSelectMotionObjectArea(){
            return selectMotionObjectArea.contains(location);
        }

        /**
         * sets selectMotionDetectionArea
         */
        protected void setSelectMotionDetectionArea(){
            int x = (int) location.x - selectMotionDetectionAreaSizePixels/2;
            int y = (int) location.y - selectMotionDetectionAreaSizePixels/2;
            selectMotionDetectionArea = new Rectangle(x, y, selectMotionDetectionAreaSizePixels, selectMotionDetectionAreaSizePixels);
        }

        /**
         * returns true if the cursor is within selectMotionDetectionArea
         * @return
         */
        protected boolean IsWithinSelectMotionDetectionArea(){
            return selectMotionDetectionArea.contains(location);
        }

        /** returns the number of events collected by the cluster at each update
         *
         * @return numEvents
         */
        @Override
        public int getNumEvents (){
            return 1;
        }

        /**
         * The "totalMP" of the cluster is the totalMP of the NeuronGroup of the BlurringFilter2D.
         * @return the totalMP
         */
        @Override
        public float getMass (){
            return mass;
        }

        /**
         *
         * @return lastUpdateTimestamp
         */
        @Override
        public int getLastEventTimestamp (){
            return lastUpdateTimestamp;
        }

        /** 
         * updates cluster by one NeuronGroup
         * 
         * @param ng
         * @param virtualGroup
         */
        public void addGroup (NeuronGroup ng, boolean virtualGroup){
            float leakyfactor = calMassLeakyfactor(ng.getLastEventTimestamp(), 0.5f);
            float curMass = mass;
            float ngTotalMP = ng.getTotalMP();
            int timeInterval = ng.getLastEventTimestamp() - lastUpdateTimestamp;

            if(leakyfactor > 1)
                ngTotalMP /= leakyfactor;
            else
                curMass *= leakyfactor;

            numNeurons = ng.getNumMemberNeurons();
            mass = curMass + ngTotalMP;

            // averaging the location
            Point2D.Float prevLocation = new Point2D.Float(location.x, location.y);
            if(ng.location.x == 0 && ng.location.y == 0){
                // do not update the location since it's not a good information
                // TODO: find a bug to cause this problem
            } else {
                location.x = (location.x*curMass + ng.location.x*ngTotalMP)/mass;
                location.y = (location.y*curMass + ng.location.y*ngTotalMP)/mass;
            }

            // solves cluster overlapping
            for(Cluster cl:clusters){
                if(cl != this){
                    float distanceLimit = (maxRadius + cl.maxRadius)*0.4f;
                    if(distanceTo(cl) < distanceLimit){
                        location.setLocation(prevLocation);
                    }
                }
            }

            if(timeInterval > 0){
                increaseVitality(timeInterval);
                lastUpdateTimestamp = ng.getLastEventTimestamp();
            }
                
            if ( maxRadius == 0 ){
                birthLocation.setLocation(ng.location);
                firstUpdateTimestamp = ng.getLastEventTimestamp();
            }

            hitEdge = ng.isHitEdge();
            if ( hitEdge ){
                vitality = (int)( 1000 * maximumClusterLifetimeMs );
            }

            // sets the new radius of the cluster
            setRadius(ng, timeInterval);

            if(virtualGroup){
                if (getSubThTrackingTimeUs() > clusterLifeTimeMsInSelectMotion*1000){
                    optionInt[0] = 0;
                }
            } else {
                // checks if we have to activate the subthreshold tracking mode
                if(!subThTrackingModeIsPossible && getLifetime() >= subThTrackingActivationTimeMs * 1000)
                    subThTrackingModeIsPossible = true;

                // enable subthreshold tracking
                if(enableSubThTracking && subThTrackingModeIsPossible && !subThTrackingOn){
                    setSubThTrackingOn(true);
                    setMergeable(false);
                }

                subThTrackingModeStartTimeUs = ng.lastEventTimestamp;
            }

            if(detectingSelectMotion && !IsWithinSelectMotionObjectArea()){
                optionInt[0] = 0;
                optionInt[1] = 0;
                detectingSelectMotion = false;
            }

            // detect select motion
            if(detectingSelectMotion){
                // detects the select motion if the cluster is within the detection area
                if(IsWithinSelectMotionDetectionArea()){
                   if(optionInt[0] == 0){
                       optionInt[0] = lastUpdateTimestamp;
                   }else{
                       if(lastUpdateTimestamp - optionInt[0] > selectMotionLastingTimeMs*1000){
                           optionBoolean[0] = true;
                           detectingSelectMotion = false;
                           optionInt[0] = 0;
                       }
                   }

                   if(optionInt[1] == 0){
                       optionInt[1] = lastUpdateTimestamp;
                   } else {
                       if(lastUpdateTimestamp - optionInt[1] > selectStayLastingTimeMs*1000){
                           optionBoolean[0] = true;
                           detectingSelectMotion = false;
                           optionInt[1] = 0;
                       }
                   }
                   
                } else { // otherwise, sets a new detection area for select motion
                    optionInt[0] = lastUpdateTimestamp;
                    optionInt[1] = lastUpdateTimestamp;
                    setSelectMotionDetectionArea();
                }
            }// end of if(detectingSelectMotion)
        }

        /**
         *
         * @param dx
         * @param dy
         * @return
         */
        public float distanceMetric (float dx,float dy){
            return ( ( dx > 0 ) ? dx : -dx ) + ( ( dy > 0 ) ? dy : -dy );
        }

        /** Measures distance in x direction, accounting for
         * predicted movement of cluster.
         *
         * @return distance in x direction of this cluster to the event.
         */
        private float distanceToX (NeuronGroup ng){
            int dt = ng.getLastEventTimestamp() - lastUpdateTimestamp;
            float currentLocationX = location.x;
            if ( useVelocity ){
                currentLocationX += velocityPPT.x * dt;
            }

            if ( currentLocationX < 0 ){
                currentLocationX = 0;
            } else if ( currentLocationX > chip.getSizeX() - 1 ){
                currentLocationX = chip.getSizeX() - 1;
            }

            return Math.abs(ng.getLocation().x - currentLocationX);
        }

        /** Measures distance in y direction, accounting for predicted movement of cluster
         *
         * @return distance in y direction of this cluster to the event
         */
        private float distanceToY (NeuronGroup ng){
            int dt = ng.getLastEventTimestamp() - lastUpdateTimestamp;
            float currentLocationY = location.y;
            if ( useVelocity ){
                currentLocationY += velocityPPT.y * dt;
            }

            if ( currentLocationY < 0 ){
                currentLocationY = 0;
            } else if ( currentLocationY > chip.getSizeY() - 1 ){
                currentLocationY = chip.getSizeY() - 1;
            }

            return Math.abs(ng.getLocation().y - currentLocationY);
        }

        /** Computes and returns distance to another cluster.
         * @param c
         * @return distance of this cluster to the other cluster in pixels.
         */
        protected final float distanceTo (Cluster c){
//            float dx = c.location.x - location.x;
//            float dy = c.location.y - location.y;
//            return distanceMetric(dx,dy);
            return (float) c.location.distance(location);
        }

        /** Computes and returns distance to a neuron group.
         * @param ng
         * @return
         */
        protected final float distanceTo (NeuronGroup ng){
//            float dx = ng.location.x - location.x;
//            float dy = ng.location.y - location.y;
//            return distanceMetric(dx,dy);
            return (float) ng.location.distance(location);
        }

        /** Computes and returns the angle of this cluster's velocities vector to another cluster's velocities vector.
         *
         * @param c the other cluster.
         * @return the angle in radians, from 0 to PI in radians. If either cluster has zero velocities, returns 0.
         */
        protected final float velocityAngleTo (Cluster c){
            float s1 = getSpeedPPS(), s2 = c.getSpeedPPS();
            if ( s1 == 0 || s2 == 0 ){
                return 0;
            }
            float dot = velocityPPS.x * c.velocityPPS.x + velocityPPS.y * c.velocityPPS.y;
            float angleRad = (float)Math.acos(dot / s1 / s2);
            return angleRad;
        }

        /** returns true if the given neuron group is inside the cluster
         *
         * @param ng
         * @return
         */
        private boolean doesCover (NeuronGroup ng){
            int dt = ng.getLastEventTimestamp() - lastUpdateTimestamp;
            double deltaDist = velocityPPT.distance(0, 0)* dt;
            double criterion = calRadius(this)*(1 + deltaDist/chip.getSizeX());

            boolean ret = false;
            if(distanceTo(ng) < criterion)
                ret = true;

            return ret;
        }

        /**
         * excute subthreshold tracking
         *
         * @param updateTimestamp
         * @param radius
         */
        public void doSubThTracking(int updateTimestamp, float radius){
            float xpos = location.x;
            float ypos = location.y;

            if(useVelocity){
                if(Math.abs(velocityPPS.x) > 10)
                    xpos += velocityPPS.x*(updateTimestamp - lastUpdateTimestamp)*1e-6f;

                if(Math.abs(velocityPPS.y) > 10)
                    ypos += velocityPPS.y*(updateTimestamp - lastUpdateTimestamp)*1e-6f;
            }

            Point2D.Float loc = new Point2D.Float(xpos, ypos);

            NeuronGroup ng = bfilter.getVirtualNeuronGroup(loc, radius, updateTimestamp);
            ng.lastEventTimestamp = updateTimestamp;

            // if the total MP of the virtual group is lower than threshold, use current location.
            float num = ng.getNumMemberNeurons()*0.02f;
            if(num < 3)
                num = 3f;
            
            if(ng.getTotalMP() < bfilter.getMPThreshold()*num)
                ng.location.setLocation(location);

            addGroup(ng, true);
        }


        /**
         * excute subthreshold tracking
         *
         * @param updateTimestamp
         * @param pos
         * @param radius
         */
        public void updateLocation(int updateTimestamp, Point2D.Float pos, float radius){
            NeuronGroup ng = bfilter.getVirtualNeuronGroup(pos, radius, updateTimestamp);
            ng.lastEventTimestamp = updateTimestamp;

            addGroup(ng, false);
        }

        private boolean trackClosestGroup(Collection<NeuronGroup> ngCollection, int defaultUpdateInterval){
            boolean ret = false;

            NeuronGroup closestGroup = getClosestGroup(ngCollection, this);

            // updates the cluster with a new group
            Cluster c = new Cluster(closestGroup, defaultUpdateInterval);

            if(getSubThTrackingTimeUs() > 1000*1000){
                setLocation(c.location);
                lastUpdateTimestamp = closestGroup.lastEventTimestamp;
                setUpdated(true);
            } else {
                float dist = (float) getVelocityPPT().distance(0, 0)*(closestGroup.lastEventTimestamp - lastUpdateTimestamp);
                if(distanceTo(c) < 10*dist){
                    setLocation(c.location);
                    lastUpdateTimestamp = closestGroup.lastEventTimestamp;
                    c.increaseVitality(lastUpdateTimestamp - getPath().get(getPath().size() - 2).t);
                    setUpdated(true);
                }
            }

            // if the closestGroup is uased for update of a cluster, remove it.
            if(updated){
                ngCollection.remove(closestGroup);
                ret = true;
            }

            return ret;
        }

        /** Returns measure of cluster radius, here the maxRadius.
         *
         * @return the maxRadius radius.
         */
        @Override
        public float getRadius (){
            return maxRadius;
        }

        /**
         * sets the radius of the cluster
         * @param ng
         * @param curMass
         * @param ngTotalMP
         * @param timeIntervalUs
         */
        private void setRadius (NeuronGroup ng, int timeIntervalUs){
            float mixingFactor = 1 - (float) Math.exp(-timeIntervalUs/(clusterRadiusLifetimeMs*1000));
            if(clusterArea.height < ng.getDimension().height)
                mixingFactor = 1;
            if (ng.isHitEdge())
                mixingFactor = 0;
            int height = (int) (clusterArea.height*(1-mixingFactor) + ng.getDimension().height*mixingFactor);
            if(height < minimumClusterSize)
                height = minimumClusterSize;
            else if(height > maximumClusterSizePixels)
                height = maximumClusterSizePixels;
            else{}

            mixingFactor = 1 - (float) Math.exp(-timeIntervalUs/(clusterRadiusLifetimeMs*1000));
            if(clusterArea.width < ng.getDimension().width)
                mixingFactor = 1;
            if (ng.isHitEdge())
                mixingFactor = 0;
            int width = (int) (clusterArea.width*(1-mixingFactor) + ng.getDimension().width*mixingFactor);
            if(width < (minimumClusterSize))
                width = minimumClusterSize;
            else if(width > maximumClusterSizePixels)
                width = maximumClusterSizePixels;
            else{}
            
            clusterArea.setRect((int) location.x - width/2, (int) location.y - height/2, width, height);

            if ( ng.isHitEdge() ){
                maxRadius = Math.max(height, width)/2;
            } else{
                maxRadius = (height + width)/4;
            }
        }

        /** getString the cluster location
         *
         * @return location
         */
        @Override
        final public Point2D.Float getLocation (){
            return location;
        }

        /** set the cluster location
         * 
         * @param loc
         */
        public void setLocation (Point2D.Float loc){
            location.x = loc.x;
            location.y = loc.y;
        }

        /**
         * returns cluster area
         * @return
         */
        public Rectangle getClusterArea() {
            return clusterArea;
        }

        /**
         * returns minimumClusterSize
         * @return
         */
        public int getMinimumClusterSize() {
            return minimumClusterSize;
        }

        /**
         * sets minimumClusterSize
         * @param minimumClusterSize
         */
        public void setMinimumClusterSize(int minimumClusterSize) {
            this.minimumClusterSize = minimumClusterSize;
        }

        /**
         * returns the average disparity of most recent num points
         * @param num
         * @return
         */
        public float getDisparity(int num){
            float ret = 0;
            float count = 0;

            for(int i = 0; i<num; i++){
                if(path.size() - 1 - i >= 0){
                    ret += path.get(path.size() - 1 - i).getStereoDisparity();
                    count++;
                } else
                    break;
            }

            return ret/count;
        }

        /** @return lifetime of cluster in timestamp ticks, measured as lastUpdateTimestamp-firstUpdateTimestamp. */
        final public int getLifetime (){
            return lastUpdateTimestamp - firstUpdateTimestamp;
        }

        /**
         * Updates path (historical) information for this cluster,
         * including cluster velocityPPT.
         * @param t
         * @param disparity
         */
        final public void updatePath (int t, float disparity){
            if ( !pathsEnabled ){
                return;
            }

            // creates a new path point
            ClusterPathPoint cpp = new ClusterPathPoint(location.x, location.y, t, (int) (mass*10));
            // updates disparity
            cpp.setStereoDisparity((float) disparity);
            // updates path location
            path.add(cpp);

            // velocity
            updateVelocity();

            // checks movement
            if(velocityValid){
                if(velocityPPS.distance(0, 0) > 1){
                    if(!onMoving){
                        onMoving = true;
                    }
                } else {
                    if(onMoving){
                        onMoving = false;
                        stationaryTime = t;
                    } else {
                        if(t - stationaryTime > stationaryLifeTimeMs*1000){
                            dead = true;
                        }
                    }
                }
            }


            /**
             * removes the oldest point if the size is over
             */
            if ( path.size() > getPathLength() ){
                path.remove(0);
            }
            
        }

        /** Updates velocities of cluster.
         *
         * @param t current timestamp.
         */
        private void updateVelocity (){
            velocityFitter.update();
            if ( velocityFitter.valid ){
                velocityPPS.setLocation((float)velocityFitter.getXVelocity(), (float)velocityFitter.getYVelocity());
                velocityPPT.setLocation((float)( velocityFitter.getXVelocity() / VELPPS_SCALING ), (float)( velocityFitter.getYVelocity() / VELPPS_SCALING ));
                velocityValid = true;
            } else{
                velocityValid = false;
            }
        }

        @Override
        public String toString() {
            return String.format("Cluster #=%d, location = (%d, %d), mass = %.2f, ageUs = %d, lifeTime = %d",
                    clusterNumber,
                    (int) location.x, (int) location.y,
                    mass, vitality, getLifetime());
        }

        @Override
        public ArrayList<ClusterPathPoint> getPath (){
            return path;
        }

        /**
         *
         * @return color
         */
        public Color getColor (){
            return color;
        }

        /**
         *
         * @param color
         */
        public void setColor (Color color){
            this.color = color;
        }

        /** Returns velocities of cluster in pixels per second.
         *
         * @return averaged velocities of cluster in pixels per second.
         * <p>
         * The method of measuring velocities is based on a linear regression of a number of previous cluter locations.
         * @see #getVelocityPPT()
         *
         */
        @Override
        public Point2D.Float getVelocityPPS (){
            return velocityPPS;
            /* old method for velocities estimation is as follows
             * The velocities is instantaneously
             * computed from the movement of the cluster caused by the last event, then this velocities is mixed
             * with the the old velocities by the mixing factor. Thus the mixing factor is appplied twice: once for moving
             * the cluster and again for changing the velocities.
             * */
        }

        /** Computes and returns speed of cluster in pixels per second.
         *
         * @return speed in pixels per second.
         */
        @Override
        public float getSpeedPPS (){
            return (float)Math.sqrt(velocityPPS.x * velocityPPS.x + velocityPPS.y * velocityPPS.y);
        }

        /** Computes and returns speed of cluster in pixels per timestamp tick.
         *
         * @return speed in pixels per timestamp tick.
         */
        public float getSpeedPPT (){
            return (float)Math.sqrt(velocityPPT.x * velocityPPT.x + velocityPPT.y * velocityPPT.y);
        }

        /** returns the cluster number
         *
         * @return
         */
        public int getClusterNumber (){
            return clusterNumber;
        }

        /** set the cluster number
         * 
         * @param clusterNumber
         */
        public void setClusterNumber (int clusterNumber){
            this.clusterNumber = clusterNumber;
        }

        /** 
         *  returns the vitality of the cluster.
         * @return 
         */
        public int getVitality (){
            return vitality;
        }

        /** increases the vitality of the cluster.
         * it increases twice faster than it decreases.
         * @param deltaVitality
         * @return
         */
        public int increaseVitality (int deltaVitality){
            if ( deltaVitality > 0 ){
                vitality += 2 * deltaVitality;
            } else{
                vitality += deltaVitality;
            }

            if ( vitality > (int)( 1000 * maximumClusterLifetimeMs ) ){
                vitality = (int)( 1000 * maximumClusterLifetimeMs );
            }

            return vitality;
        }

        /** returns true if the cluster age is greater than 1.
         * So, the cluster is visible after it has been updated at least once after created.
         * @return true if the cluster age is greater than 1
         */
        @Override
        public boolean isVisible (){
            if ( getVitality() > 0 ){
                return true;
            } else{
                return false;
            }
        }

        /**
         * returns true if the cluster is dead
         * @return
         */
        public boolean isDead() {
            return dead;
        }

        /**
         * returns true if the subthreshold tracking mode is on
         * @return
         */
        public boolean isSubThTrackingOn() {
            return subThTrackingOn;
        }

        /**
         * sets subThTrackingOn
         * @param subThTrackingOn
         */
        public void setSubThTrackingOn(boolean subThTrackingOn) {
            this.subThTrackingOn = subThTrackingOn;
        }

        /**
         * returns true if the subthreshold tracking mode is possible
         * @return
         */
        public boolean isSubThTrackingModeIsPossible() {
            return subThTrackingModeIsPossible;
        }

        /**
         * returns elasped time since the subthreshold mode started
         * @return
         */
        public int getSubThTrackingTimeUs(){
            return lastUpdateTimestamp - subThTrackingModeStartTimeUs;
        }

        /**
         * set optionBoolean
         * @param pos
         * @param val
         */
        public void setOptionBoolean(int pos, boolean val) {
            if(pos >= 0 && pos < numOptions)
                optionBoolean[pos] = val;
        }

        /**
         * returns optionBoolean
         * @param pos
         * @return
         */
        public boolean getOptionBoolean(int pos) {
            if(pos >= 0 && pos < numOptions)
                return optionBoolean[pos];
            else
                return false;
        }

        /**
         * set optionInt
         * @param pos
         * @param val
         */
        public void setOptionInt(int pos, int val) {
            if(pos >= 0 && pos < numOptions)
                optionInt[pos] = val;
        }

        /**
         * returns optionInt
         * @param pos
         * @return
         */
        public int getOptionInt(int pos) {
            if(pos >= 0 && pos < numOptions)
                return optionInt[pos];
            else
                return 0;
        }

        /**
         * resets option fields
         */
        public void resetOptions(){
            for(int i=0; i<numOptions; i++){
                optionBoolean[i] = false;
                optionInt[i] = 0;
            }
        }



        /**
         * Slightly modified from RollingVelocityFilter by Tobi
         * @author Tobi, Jun Haeng
         */
        private class VelocityFitter{
            private static final int LENGTH_DEFAULT = 5;
            private int length = LENGTH_DEFAULT;
            
            private ArrayList<ClusterPathPoint> points;
            private double xVelocityPPS = 0, yVelocityPPS = 0;
            private boolean valid = false;

            private LowpassFilter xlpf = new LowpassFilter();
            private LowpassFilter ylpf = new LowpassFilter();

            /** Creates a new instance of RollingLinearRegression */
            public VelocityFitter (ArrayList<ClusterPathPoint> points,int length){
                this.points = points;
                this.length = length;
                xlpf.setTauMs(50);
                ylpf.setTauMs(50);
            }

            @Override
            public String toString (){
                return String.format("RollingVelocityFitter: \n" + "valid=%s\n"
                        + "xVel=%e, yVel=%e\n",
                        valid,
                        xVelocityPPS,yVelocityPPS);

            }

            /**
             * Updates estimated velocityPPT based on last point in path. If velocityPPT cannot be estimated
            it is not updated.
             * @param t current timestamp.
             */
            private synchronized void update (){
                int size = points.size();
                if ( size < 1 ){
                    return;
                }

                int n = size > length ? length : size;  // n grows to max length

                double st = 0, sx = 0, sy = 0, stt = 0, sxt = 0, syt = 0, den = 1, dt = 0;
                int refTime = points.get(size - n).t;
                for(int i=1; i<=n; i++ ){
                    ClusterPathPoint p = points.get(size - i);
                    dt = (double) (p.t - refTime);
                    st += dt;
                    sx += p.x;
                    sy += p.y;
                    stt += dt * dt;
                    sxt += p.x * dt;
                    syt += p.y * dt;
                }
                den = ( n * stt - st * st )/1e6;

                if ( n >= length && den != 0 ){
                    valid = true;
                    xVelocityPPS = ( n * sxt - st * sx ) / den;
                    yVelocityPPS = ( n * syt - st * sy ) / den;

                    // first low-pass filtering
                    ClusterPathPoint p = points.get(size - 1); // takes the last point
                    xVelocityPPS = xlpf.filter((float) xVelocityPPS, p.t);
                    yVelocityPPS = ylpf.filter((float) yVelocityPPS, p.t);

                    // 2nd lowpass filtering with cluster mass
                    ClusterPathPoint pp = points.get(points.size() - 2); // takes the second last point
                    if(pp.velocityPPT != null){
                        float leakyfactor = (float) Math.exp((pp.t - p.t) / (bfilter.getMPTimeConstantUs()));
                        float prevMass = pp.getNEvents();
                        float currMass = p.getNEvents();

                        if(leakyfactor > 1)
                            currMass /= leakyfactor;
                        else
                            prevMass *= leakyfactor;

                        float totalMass = currMass + prevMass;

                        xVelocityPPS = (pp.velocityPPT.x*VELPPS_SCALING*prevMass + xVelocityPPS*currMass)/totalMass;
                        yVelocityPPS = (pp.velocityPPT.y*VELPPS_SCALING*prevMass + yVelocityPPS*currMass)/totalMass;
                    }

                    p.velocityPPT = new Point2D.Float((float)xVelocityPPS/VELPPS_SCALING,(float)yVelocityPPS/VELPPS_SCALING);
                } else{
                    valid = false;
                }
            }

            int getLength (){
                return length;
            }

            /** Sets the window length.  Clears the accumulated data.
             * @param length the number of points to fit
             * @see #LENGTH_DEFAULT
             */
            synchronized void setLength (int length){
                this.length = length;
            }

            public double getXVelocity (){
                return xVelocityPPS;
            }

            public double getYVelocity (){
                return yVelocityPPS;
            }

            /** Returns true if the last estimate resulted in a valid measurement
             * (false when e.g. there are only two identical measurements)
             */
            public boolean isValid (){
                return valid;
            }

            public void setValid (boolean valid){
                this.valid = valid;
            }
        } // rolling velocityPPT fitter

        /** Returns birth location of cluster: initially the first event and later, after cluster
         * becomes visible, it is the location when it becomes visible, which is presumably less noisy.
         *
         * @return x,y location.
         */
        public Point2D.Float getBirthLocation (){
            return birthLocation;
        }

        /** Returns first timestamp of cluster.
         *
         * @return timestamp of birth location.
         */
        public int getBirthTime (){
            return firstUpdateTimestamp;
        }

        /** set birth location of the cluster
         *
         * @param birthLocation
         */
        public void setBirthLocation (Point2D.Float birthLocation){
            this.birthLocation.setLocation(birthLocation);
        }

        /** This flog is set true after a velocityPPT has been computed for the cluster.
         * This may take several packets.

        @return true if valid.
         */
        public boolean isVelocityValid (){
            return velocityValid;
        }

        /** set validity of velocity
         *
         * @param velocityValid
         */
        public void setVelocityValid (boolean velocityValid){
            this.velocityValid = velocityValid;
        }
    } // end of Cluster

    /** returns clusters
     *
     */
    @Override
    public java.util.List<Cluster> getClusters (){
        return clusters;
    }

    /** @param x x location of pixel
     *@param y y location
     *@param fr the frame data
     *@param channel the RGB channel number 0-2
     *@param brightness the brightness 0-1
     */
    private void colorPixel (final int x,final int y,final float[][][] fr,int channel,Color color){
        if ( y < 0 || y > fr.length - 1 || x < 0 || x > fr[0].length - 1 ){
            return;
        }
        float[] rgb = color.getRGBColorComponents(null);
        float[] f = fr[y][x];
        System.arraycopy(rgb, 0, f, 0, 3);
    }

    @Override
    public void resetFilter (){
        getEnclosedFilter().resetFilter();
        clusters.clear();
        clusterCounter = 0;
        try{
            chip.getAeViewer().zeroTimestamps();
        }catch(Exception e){
            log.warning(e.toString());
        }
    }

    /**
     * @return
     * @see #setPathsEnabled
     */
    public boolean isPathsEnabled (){
        return pathsEnabled;
    }

    /**
     * Enable cluster history paths. The path of each cluster is stored as a list of points at the end of each cluster list update.
     * This option is required (and set true) if useVelocity is set true.
     *
     * @param pathsEnabled true to show the history of the cluster locations on each packet.
     */
    public void setPathsEnabled (boolean pathsEnabled){
        getSupport().firePropertyChange("pathsEnabled",this.pathsEnabled,pathsEnabled);
        this.pathsEnabled = pathsEnabled;
        getPrefs().putBoolean("BluringFilter2DTracker.pathsEnabled",pathsEnabled);
    }

    /** Processes the events if the Observable is an EventFilter2D.
     *
     * @param o
     * @param arg an UpdateMessage if caller is notify from EventFilter2D.
     */
    @Override
    public void update (Observable o,Object arg){
        if ( o instanceof BlurringFilter2D ){
            UpdateMessage msg = (UpdateMessage)arg;
            updateCore(msg);

            // callback to update() of any listeners on us, e.g. VirtualDrummer
            callUpdateObservers(msg.packet, msg.timestamp); 

        } else if ( o instanceof AEChip ){
            initFilter();
        }
    }

    /**
     * core of update process
     * this part is separated from update method to be overridden later
     * @param msg
     */
    protected void updateCore(UpdateMessage msg){
        Collection<NeuronGroup> ngCollection = bfilter.getNeuronGroups();

        // creates distance mapping table between clusters and neuron groups
        HashMap<NeuronGroup, HashMap> distMappingTable = new HashMap<NeuronGroup, HashMap>();
        for ( NeuronGroup ng : ngCollection ){
            HashMap<Cluster, Float> distanceMap = new HashMap<Cluster, Float>();

            for( Cluster c : clusters ){
                if(c.doesCover(ng)){
                    distanceMap.put(c, c.distanceTo(ng));
                    ng.setMatched(true);
                }
            }
            distMappingTable.put(ng, distanceMap);
        }


        // creates a mapping table between cluster and neuron group
        HashMap<Cluster, NeuronGroup> crossMapping = new HashMap<Cluster, NeuronGroup>();
        for( Cluster c : clusters )
            crossMapping.put(c, null);
        
        // fills the mapping table based on the distance table
        HashSet<NeuronGroup> ngListForPrune = new HashSet<NeuronGroup> ();
        for ( NeuronGroup ng : ngCollection ){
            HashMap<Cluster, Float> distanceMap = distMappingTable.get(ng);
            if(distanceMap.size() > 0){
                // selects the closest one
                float distance = chip.getSizeX();
                Cluster closestCluster = null;

                Iterator<Cluster> itr = distanceMap.keySet().iterator();
                while(itr.hasNext()){
                    Cluster cl = itr.next();
                    if(distanceMap.get(cl).floatValue() < distance)
                        closestCluster = cl;
                }

                if(crossMapping.get(closestCluster) == null)
                    crossMapping.put(closestCluster, ng);
                else
                    crossMapping.get(closestCluster).merge(ng);

                ngListForPrune.add(ng);
            }
        }

        // updates clusters based on the mapping table
        int defaultUpdateInterval = (int) Math.min(msg.packet.getDurationUs(), 1000 * chip.getFilterChain().getUpdateIntervalMs());
        for ( Cluster c:clusters ){
            int updateInterval = 0;
            if(c.getPath().isEmpty())
                updateInterval = defaultUpdateInterval;
            else
                updateInterval = msg.timestamp - c.getPath().get(c.getPath().size()-1).t;


            if(!c.dead){
                NeuronGroup tmpNg = crossMapping.get(c);

                // if we have a newbie, update the cluster
                if ( tmpNg != null ){
                    c.updateLocation(msg.timestamp, tmpNg.location, Math.max(tmpNg.getOutterRadiusPixels(), calRadius(c)));
                    c.setUpdated(true);
                } else{
                    // do subthreshold tracking
                    c.doSubThTracking(msg.timestamp, calRadius(c));
                    if(enableSubThTracking && c.isSubThTrackingOn()){
                        c.setUpdated(true);
                    } else {
                        // if it's not in the subthreshold tracking mode, the cluster looses its vitality
                        c.increaseVitality(-updateInterval);
                        c.setUpdated(false);
                    }
                }
            }
            
            // determines cluster' vitality and gets rid of stale clusters
            if(enableTrjPersLimit && c.getLifetime() > maxmumTrjPersTimeMs*1000)
                c.vitality = -1;

            if ( c.getVitality() <= 0 || c.dead){
                if(!c.dead){
                    c.dead = true;
                }else{
                    pruneList.add(c);
                }
            }
        }

        // clean up the used neuron groups
        ngCollection.removeAll(ngListForPrune);
        ngListForPrune.clear();

        // Creates cluster for the rest neuron groups
        if ( !ngCollection.isEmpty() ){
            switch(maxNumClusters){
                case SINGLE: // if we track only one cluster
                    if(clusters.size() == 0){
                        trackLargestGroup(ngCollection, 0, defaultUpdateInterval);
                    } else {
                        if(!clusters.get(0).isUpdated())
                            clusters.get(0).trackClosestGroup(ngCollection, defaultUpdateInterval);
                    }
                    break;
                case COUPLE:
                    if(clusters.size() == 2){
                        Cluster c1 = clusters.get(0);
                        Cluster c2 = clusters.get(1);
                        
                        if(!c1.isUpdated())
                            c1.trackClosestGroup(ngCollection, defaultUpdateInterval);

                        // if c1 and c2 are overlapped, splits them
                        if(c1.distanceTo(c2) < (c1.maxRadius + c2.maxRadius)*0.5f){
                            NeuronGroup ng1 = getClosestGroup(ngCollection, c1);
                            NeuronGroup ng2 = getClosestGroup(ngCollection, c2);
                            if(c1.distanceTo(ng1) < c2.distanceTo(ng2))
                                c1.updateLocation(msg.timestamp, ng1.location, Math.max(ng1.getOutterRadiusPixels(), calRadius(c1)));
                            else
                                c2.updateLocation(msg.timestamp, ng2.location, Math.max(ng2.getOutterRadiusPixels(), calRadius(c2)));
                        }
                    } else {
                        if(trackLargestGroup(ngCollection, 1, defaultUpdateInterval)){
                            if(clusters.size() < 2 && ngCollection.size() > 0)
                                trackLargestGroup(ngCollection, 1, defaultUpdateInterval);
                        }
                    }
                    break;
                default:
                    for ( NeuronGroup ng:ngCollection ){
                        trackAGroup(ng, defaultUpdateInterval);
                    }
                    break;
            }
        }

        ngCollection.clear();

        // updates cluster list
        updateClusterList(msg.timestamp);

    }

    /**
     * calculates 
     * @param c
     * @return
     */
    protected float calRadius(Cluster c){
        float ret = c.getRadius();

        for(Cluster cl:clusters){
            if(cl != c){
                float radiusSum = c.maxRadius+cl.maxRadius;
                if(c.distanceTo(cl) < radiusSum){
                    float tmpRadius = c.distanceTo(cl)*c.maxRadius/radiusSum;
                    if(tmpRadius < ret)
                        ret = tmpRadius;
                }
            }
        }

        if(ret < 5)
            ret = 5;

        return ret;
    }

    private NeuronGroup getClosestGroup(Collection<NeuronGroup> ngCollection, Cluster cl){
        float dist = chip.getSizeX();
        NeuronGroup ret = null;

        // select the max size neuron group
        for ( NeuronGroup ng:ngCollection ){
            if ( cl.distanceTo(ng) < dist ){
                dist = cl.distanceTo(ng);
                ret = ng;
            }
        }

        return ret;
    }


    /**
     * tracks the largest group
     *
     * @param ngCollection
     * @param refNumClusterForNewCreation
     * @param defaultUpdateInterval
     * @return
     */
    private boolean trackLargestGroup(Collection<NeuronGroup> ngCollection, int refNumClusterForNewCreation, int defaultUpdateInterval){
        boolean ret = false;

        int maxSize = 0;
        NeuronGroup maxGroup = null;

        // select the max size neuron group
        for ( NeuronGroup ng:ngCollection ){
            if ( ng.getNumMemberNeurons() > maxSize ){
                maxSize = ng.getNumMemberNeurons();
                maxGroup = ng;
            }
        }

        // if tere is no cluster found, find the largest group for a new cluster
        if(clusters.size() <= refNumClusterForNewCreation){
            clusters.add(new Cluster(maxGroup, defaultUpdateInterval));
            ngCollection.remove(maxGroup);
            ret = true;
        } else {
            // otherwise, replace the location of the cluster with that of the largest group
            Cluster cl = null;
            int pos = 0;
            for(int i = 0; i< clusters.size(); i++){
                if(!clusters.get(i).isUpdated()){
                    cl = clusters.get(i);
                    pos = i;
                    break;
                }
            }
            if(cl == null)
                return false;

            // finds the closest cluster to the maxGroup
            float minDist = cl.distanceTo(maxGroup);
            for(int i = pos+1; i< clusters.size(); i++){
                Cluster c = clusters.get(i);
                if(c.isUpdated()) continue; // skips updated clusters

                if(c.distanceTo(maxGroup) < minDist){
                    cl = c;
                    minDist = c.distanceTo(maxGroup);
                }
            }

            // updates the cluster with a new group
            Cluster c = new Cluster(maxGroup, defaultUpdateInterval);

            if(cl.getSubThTrackingTimeUs() > 1000*1000){
                cl.setLocation(c.location);
                cl.lastUpdateTimestamp = maxGroup.lastEventTimestamp;
                cl.setUpdated(true);
            } else {
                float dist = (float) cl.getVelocityPPT().distance(0, 0)*(maxGroup.lastEventTimestamp - cl.lastUpdateTimestamp);
                if(cl.distanceTo(c) < 10*dist){
                    cl.setLocation(c.location);
                    cl.lastUpdateTimestamp = maxGroup.lastEventTimestamp;
                    c.increaseVitality(cl.lastUpdateTimestamp - cl.getPath().get(cl.getPath().size() - 2).t);
                    cl.setUpdated(true);
                }
            }

            // if the maxGroup is uased for update of a cluster, remove it.
            if(cl.updated){
                ngCollection.remove(maxGroup);
                ret = true;
            }
        }

        return ret;
    }

    /**
     * draws a box for cluster
     *
     * @param gl
     * @param x
     * @param y
     * @param radius
     */
    protected void drawBox (GL gl,int x,int y,int radius){
        gl.glPushMatrix();
        gl.glTranslatef(x,y,0);
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(-radius,-radius);
            gl.glVertex2i(+radius,-radius);
            gl.glVertex2i(+radius,+radius);
            gl.glVertex2i(-radius,+radius);
        }
        gl.glEnd();
        gl.glPopMatrix();
    }

    @Override
    synchronized public void annotate (GLAutoDrawable drawable){
        if ( !isFilterEnabled() ){
            return;

        }
        GL gl = drawable.getGL(); // when we getString this we are already set up with scale 1=1 pixel, at LL corner
        if ( gl == null ){
            log.warning("null GL in BluringFilter2DTracker.annotate");
            return;
        }
        gl.glPushMatrix();
        try{
            for (int i=0; i<clusters.size(); i++ ){
                Cluster c = clusters.get(i);
                if ( showClusters && c.isVisible() ){
                    c.draw(drawable);
                }
            }
        } catch ( java.util.ConcurrentModificationException e ){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }

    /** Use cluster velocityPPT to estimate the location of cluster.
     * This is useful to select neuron groups to take into this cluster.
     * @param useVelocity
     * @see #setPathsEnabled(boolean)
     */
    public void setUseVelocity (boolean useVelocity){
        if ( useVelocity ){
            setPathsEnabled(true);
        }
        getSupport().firePropertyChange("useVelocity",this.useVelocity,useVelocity);
        this.useVelocity = useVelocity;
        getPrefs().putBoolean("BluringFilter2DTracker.useVelocity",useVelocity);
    }

    /**
     *
     * @return
     */
    public boolean isUseVelocity (){
        return useVelocity;
    }

    /** returns true of the cluster is visible on the screen
     * 
     * @return
     */
    public boolean isShowClusters (){
        return showClusters;
    }

    /**Sets annotation visibility of clusters that are not "visible"
     * @param showClusters
     */
    public void setShowClusters (boolean showClusters){
        this.showClusters = showClusters;
        getPrefs().putBoolean("BluringFilter2DTracker.showClusters",showClusters);
    }

    /** returns path length
     *
     * @return
     */
    public int getPathLength (){
        return pathLength;
    }

    /** Sets the maximum number of path points recorded for each cluster. The {@link Cluster#path} list of points is adjusted
     * to be at most <code>pathLength</code> long.
     *
     * @param pathLength the number of recorded path points. If <2, set to 2.
     */
    synchronized public void setPathLength (int pathLength){
        if ( pathLength < 2 ){
            pathLength = 2;
        }
        int old = this.pathLength;
        this.pathLength = pathLength;
        getPrefs().putInt("BluringFilter2DTracker.pathLength",pathLength);
        getSupport().firePropertyChange("pathLength",old,pathLength);
        if ( numVelocityPoints > pathLength ){
            setNumVelocityPoints(pathLength);
        }
    }

    /**
     * @return the velAngDiffDegToNotMerge
     */
    public float getVelAngDiffDegToNotMerge (){
        return velAngDiffDegToNotMerge;
    }

    /**
     * @param velAngDiffDegToNotMerge the velAngDiffDegToNotMerge to set
     */
    public void setVelAngDiffDegToNotMerge (float velAngDiffDegToNotMerge){
        if ( velAngDiffDegToNotMerge < 0 ){
            velAngDiffDegToNotMerge = 0;
        } else if ( velAngDiffDegToNotMerge > 180 ){
            velAngDiffDegToNotMerge = 180;
        }
        this.velAngDiffDegToNotMerge = velAngDiffDegToNotMerge;
        getPrefs().putFloat("BluringFilter2DTracker.velAngDiffDegToNotMerge",velAngDiffDegToNotMerge);
    }

    /**
     * returns enableMerge
     * @return
     */
    public boolean isEnableMerge() {
        return enableMerge;
    }

    /**
     * sets enableMerge
     * @param enableMerge
     */
    public void setEnableMerge(boolean enableMerge) {
        this.enableMerge = enableMerge;
        getPrefs().putBoolean("BluringFilter2DTracker.enableMerge",enableMerge);
    }


    /**
     * @return the showClusterNumber
     */
    public boolean isShowClusterNumber (){
        return showClusterNumber;
    }

    /**
     * @param showClusterNumber the showClusterNumber to set
     */
    public void setShowClusterNumber (boolean showClusterNumber){
        this.showClusterNumber = showClusterNumber;
        getPrefs().putBoolean("BluringFilter2DTracker.showClusterNumber",showClusterNumber);
    }

    /**
     * @return the showClusterVelocity
     */
    public boolean isShowClusterVelocity (){
        return showClusterVelocity;
    }

    /**
     * @param showClusterVelocity the showClusterVelocity to set
     */
    public void setShowClusterVelocity (boolean showClusterVelocity){
        this.showClusterVelocity = showClusterVelocity;
        getPrefs().putBoolean("BluringFilter2DTracker.showClusterVelocity",showClusterVelocity);
    }

    /**
     * @return the velocityVectorScaling
     */
    public float getVelocityVectorScaling (){
        return velocityVectorScaling;
    }

    /**
     * @param velocityVectorScaling the velocityVectorScaling to set
     */
    public void setVelocityVectorScaling (float velocityVectorScaling){
        this.velocityVectorScaling = velocityVectorScaling;
        getPrefs().putFloat("BluringFilter2DTracker.velocityVectorScaling",velocityVectorScaling);
    }

    /**
     *
     * @return
     */
    public float getMaximumClusterLifetimeMs (){
        return maximumClusterLifetimeMs;
    }

    /**
     *
     * @param maximumClusterLifetimeMs
     */
    public void setMaximumClusterLifetimeMs (float maximumClusterLifetimeMs){
        float old = this.maximumClusterLifetimeMs;
        this.maximumClusterLifetimeMs = maximumClusterLifetimeMs;
        getPrefs().putFloat("BluringFilter2DTracker.maximumClusterLifetimeMs",maximumClusterLifetimeMs);
        getSupport().firePropertyChange("maximumClusterLifetimeMs",old,this.maximumClusterLifetimeMs);
    }

    /**
     * @return the showClusterMass
     */
    public boolean isShowClusterMass (){
        return showClusterMass;
    }

    /**
     * @param showClusterMass the showClusterMass to set
     */
    public void setShowClusterMass (boolean showClusterMass){
        this.showClusterMass = showClusterMass;
        getPrefs().putBoolean("BluringFilter2DTracker.showClusterMass",showClusterMass);
    }

    /**
     * returns clusterRadiusLifetimeMs
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
        float old = this.maximumClusterLifetimeMs;
        this.clusterRadiusLifetimeMs = clusterRadiusLifetimeMs;
        getPrefs().putFloat("BluringFilter2DTracker.clusterRadiusLifetimeMs",clusterRadiusLifetimeMs);
        getSupport().firePropertyChange("clusterRadiusLifetimeMs",old,this.clusterRadiusLifetimeMs);
    }

    /**
     * returns minimumClusterSizePixels
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
        getPrefs().putInt("BluringFilter2DTracker.minimumClusterSizePixels",minimumClusterSizePixels);
        getSupport().firePropertyChange("minimumClusterSizePixels",old,this.minimumClusterSizePixels);
    }

    /**
     * returns maximumClusterSizePixels
     * @return
     */
    public int getMaximumClusterSizePixels() {
        return maximumClusterSizePixels;
    }

    /**
     * sets maximumClusterSizePixels
     *
     * @param maximumClusterSizePixels
     */
    public void setMaximumClusterSizePixels(int maximumClusterSizePixels) {
        int old = this.maximumClusterSizePixels;
        this.maximumClusterSizePixels = maximumClusterSizePixels;
        getPrefs().putInt("BluringFilter2DTracker.maximumClusterSizePixels",maximumClusterSizePixels);
        getSupport().firePropertyChange("maximumClusterSizePixels",old,this.maximumClusterSizePixels);
    }

    /** @see #setNumVelocityPoints(int)
     *
     * @return number of points used to estimate velocities.
     */
    public int getNumVelocityPoints (){
        return numVelocityPoints;
    }

    /** Sets the number of path points to use to estimate cluster velocities.
     *
     * @param velocityPoints the number of points to use to estimate velocities.
     * Bounded above to number of path points that are stored.
     * @see #setPathLength(int)
     * @see #setPathsEnabled(boolean)
     */
    public void setNumVelocityPoints (int velocityPoints){
        if ( velocityPoints >= pathLength ){
            velocityPoints = pathLength;
        }
        int old = this.numVelocityPoints;
        this.numVelocityPoints = velocityPoints;
        getPrefs().putInt("BluringFilter2DTracker.numVelocityPoints",velocityPoints);
        getSupport().firePropertyChange("velocityPoints",old,this.numVelocityPoints);
    }

    /**
     * returns the maximum allowed number of clusters
     *
     * @return
     */
    public NUM_CLUSTERS getMaxNumClusters() {
        return maxNumClusters;
    }

    /**
     * sets the maximum allowed number of clusters
     * @param maxNumClusters
     */
    public void setMaxNumClusters(NUM_CLUSTERS maxNumClusters) {
        switch(maxNumClusters){
            case SINGLE:
                if(clusters.size() > 1){
                    Cluster biggestCluster = null;
                    int lifeTime = 0;
                    for ( Cluster cl:clusters ){
                        if ( cl.getLifetime() > lifeTime ){
                            if(biggestCluster != null)
                                pruneList.add(biggestCluster);

                            lifeTime = cl.getLifetime();
                            biggestCluster = cl;
                        } else {
                            pruneList.add(cl);
                        }
                    }
                }
                break;
            case COUPLE:
                if(clusters.size() > 2){
                    Cluster biggestCluster = null;
                    Cluster secondBiggestCluster = null;
                    int lifeTime = 0;
                    int secondlifeTime = 0;
                    for ( Cluster cl:clusters ){
                        if ( cl.getLifetime() > lifeTime ){
                            if(biggestCluster != null){
                                if(secondBiggestCluster != null)
                                    pruneList.add(secondBiggestCluster);
                                
                                secondBiggestCluster = biggestCluster;
                                secondlifeTime = lifeTime;
                            }

                            lifeTime = cl.getLifetime();
                            biggestCluster = cl;
                        } else if ( cl.getLifetime() > secondlifeTime ){
                            if(secondBiggestCluster != null){
                                pruneList.add(secondBiggestCluster);
                            }

                            secondlifeTime = cl.getLifetime();
                            secondBiggestCluster = cl;
                        } else {
                            pruneList.add(cl);
                        }
                    }
                }
                break;
            default:
                break;
        }

        pruneClusters();

        this.maxNumClusters = maxNumClusters;
        getPrefs().put("BlurringFilter2D.maxNumClusters", maxNumClusters.toString());
    }

    /**
     * returns true if trajectory persistance limit is on
     * @return
     */
    public boolean isEnableTrjPersLimit() {
        return enableTrjPersLimit;
    }

    /**
     * sets enableTrjPersLimit
     * @param enableTrjPersLimit
     */
    public void setEnableTrjPersLimit(boolean enableTrjPersLimit) {
        boolean old = this.enableTrjPersLimit;
        this.enableTrjPersLimit = enableTrjPersLimit;
        getPrefs().putBoolean("BluringFilter2DTracker.enableTrjPersLimit",enableTrjPersLimit);
        getSupport().firePropertyChange("enableTrjPersLimit",old,this.enableTrjPersLimit);
    }

    /**
     * returns maximum trajectory persistance time
     * @return
     */
    public int getMaxmumTrjPersTimeMs() {
        return maxmumTrjPersTimeMs;
    }

    /**
     * sets the maximum trajectory persistance time
     * @param maxmumTrjPersTimeMs
     */
    public void setMaxmumTrjPersTimeMs(int maxmumTrjPersTimeMs) {
        int old = this.maxmumTrjPersTimeMs;
        this.maxmumTrjPersTimeMs = maxmumTrjPersTimeMs;
        getPrefs().putInt("BluringFilter2DTracker.maxmumTrjPersTimeMs",maxmumTrjPersTimeMs);
        getSupport().firePropertyChange("maxmumTrjPersTimeMs",old,this.maxmumTrjPersTimeMs);
    }

    /**
     * return true if the subthreshold tracking mode is on
     * @return
     */
    public boolean isEnableSubThTracking() {
        return enableSubThTracking;
    }

    /**
     * sets enableSubThTracking
     * @param enableSubThTracking
     */
    public void setEnableSubThTracking(boolean enableSubThTracking) {
        boolean old = this.enableSubThTracking;
        this.enableSubThTracking = enableSubThTracking;
        getPrefs().putBoolean("BluringFilter2DTracker.enableSubThTracking",enableSubThTracking);
        getSupport().firePropertyChange("enableSubThTracking",old,this.enableSubThTracking);
    }

    /**
     * returns the activation time of subthreshold mode in ms
     * @return
     */
    public int getSubThTrackingActivationTimeMs() {
        return subThTrackingActivationTimeMs;
    }

    /**
     * sets the activation time of subthreshold mode in ms
     * @param subThTrackingActivationTimeMs
     */
    public void setSubThTrackingActivationTimeMs(int subThTrackingActivationTimeMs) {
        int old = this.subThTrackingActivationTimeMs;
        this.subThTrackingActivationTimeMs = subThTrackingActivationTimeMs;
        getPrefs().putInt("BluringFilter2DTracker.subThTrackingActivationTimeMs",subThTrackingActivationTimeMs);
        getSupport().firePropertyChange("subThTrackingActivationTimeMs",old,this.subThTrackingActivationTimeMs);
    }

    /**
     * returns stationaryLifeTimeMs
     * @return
     */
    public int getStationaryLifeTimeMs() {
        return stationaryLifeTimeMs;
    }

    /**
     * sets stationaryLifeTimeMs
     *
     * @param stationaryLifeTimeMs
     */
    public void setStationaryLifeTimeMs(int stationaryLifeTimeMs) {
        int old = this.stationaryLifeTimeMs;
        this.stationaryLifeTimeMs = stationaryLifeTimeMs;
        getPrefs().putInt("BluringFilter2DTracker.stationaryLifeTimeMs",stationaryLifeTimeMs);
        getSupport().firePropertyChange("stationaryLifeTimeMs",old,this.stationaryLifeTimeMs);

    }

    /**
     * returns selectMotionDetectionAreaSizePixels
     * @return
     */
    public int getSelectMotionDetectionAreaSizePixels() {
        return selectMotionDetectionAreaSizePixels;
    }

    /**
     * setss selectMotionDetectionAreaSizePixels
     *
     * @param selectMotionDetectionAreaSizePixels
     */
    public void setSelectMotionDetectionAreaSizePixels(int selectMotionDetectionAreaSizePixels) {
        int old = this.selectMotionDetectionAreaSizePixels;
        this.selectMotionDetectionAreaSizePixels = selectMotionDetectionAreaSizePixels;
        getPrefs().putInt("BluringFilter2DTracker.selectMotionDetectionAreaSizePixels", selectMotionDetectionAreaSizePixels);
        getSupport().firePropertyChange("selectMotionDetectionAreaSizePixels",old,this.selectMotionDetectionAreaSizePixels);
    }

    /**
     * returns clusterLifeTimeMsInSelectMotion
     *
     * @return
     */
    public int getClusterLifeTimeMsInSelectMotion() {
        return clusterLifeTimeMsInSelectMotion;
    }

    /**
     * sets clusterLifeTimeMsInSelectMotion
     *
     * @param clusterLifeTimeMsInSelectMotion
     */
    public void setClusterLifeTimeMsInSelectMotion(int clusterLifeTimeMsInSelectMotion) {
        int old = this.clusterLifeTimeMsInSelectMotion;
        this.clusterLifeTimeMsInSelectMotion = clusterLifeTimeMsInSelectMotion;
        getPrefs().putInt("BluringFilter2DTracker.clusterLifeTimeMsInSelectMotion", clusterLifeTimeMsInSelectMotion);
        getSupport().firePropertyChange("clusterLifeTimeMsInSelectMotion",old,this.clusterLifeTimeMsInSelectMotion);
    }

    /**
     * returns selectMotionLastingTimeMs
     *
     * @return
     */
    public int getSelectMotionLastingTimeMs() {
        return selectMotionLastingTimeMs;
    }

    /**
     * sets selectMotionLastingTimeMs
     *
     * @param selectMotionLastingTimeMs
     */
    public void setSelectMotionLastingTimeMs(int selectMotionLastingTimeMs) {
        int old = this.selectMotionLastingTimeMs;
        this.selectMotionLastingTimeMs = selectMotionLastingTimeMs;
        getPrefs().putInt("BluringFilter2DTracker.selectMotionLastingTimeMs", selectMotionLastingTimeMs);
        getSupport().firePropertyChange("selectMotionLastingTimeMs",old,this.selectMotionLastingTimeMs);
    }

    /**
     * returns selectStayLastingTimeMs
     *
     * @return
     */
    public int getSelectStayLastingTimeMs() {
        return selectStayLastingTimeMs;
    }

    /**
     * sets selectStayLastingTimeMs
     * 
     * @param selectStayLastingTimeMs
     */
    public void setSelectStayLastingTimeMs(int selectStayLastingTimeMs) {
        int old = this.selectStayLastingTimeMs;
        this.selectStayLastingTimeMs = selectStayLastingTimeMs;
        getPrefs().putInt("BluringFilter2DTracker.selectStayLastingTimeMs", selectStayLastingTimeMs);
        getSupport().firePropertyChange("selectStayLastingTimeMs",old,this.selectStayLastingTimeMs);
    }
}