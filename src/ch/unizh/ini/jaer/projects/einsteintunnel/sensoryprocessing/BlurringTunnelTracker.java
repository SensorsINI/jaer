/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;

import ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing.BlurringTunnelFilter.CellGroup;
import com.sun.opengl.util.GLUT;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.*;
import net.sf.jaer.graphics.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.media.opengl.*;
import net.sf.jaer.event.EventPacket;
/**
 * Tracks moving objects. Modified from BlurringFilter2DTracker.java
 *
 * @author Jun Haeng Lee/Tobi Delbruck customized for Einstein passage project by braendch
 */
public class BlurringTunnelTracker extends EventFilter2D implements FrameAnnotater,Observer,ClusterTrackerInterface /*, PreferenceChangeListener*/{

    /**
     *
     * @return filter description
     */
    public static String getDescription (){
        return "Tracker designed to track (groups of) people in a tunnel for the Einstein passage project.";
    }

    public int csx, csy, maxHistogramX, packetCounter;
    public int commandPort = 20021;
    public int maxClusters = 200;
    public int dsx = 504;
    public int dsy = 80;
    public short[] xHistogram;

    public DatagramSocket socket;
    public InetAddress address;

    /**
     * The list of clusters.
     */
    protected java.util.List<Cluster> clusters = new LinkedList();
    /**
     * Blurring filter to get clusters
     */
    protected BlurringTunnelFilter bfilter;
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
    private int numVelocityPoints = getPrefs().getInt("BlurringTunnelTracker.numVelocityPoints",15);
    private int minCellsPerCluster = getPrefs().getInt("BlurringTunnelTracker.minCellsPerCluster",20);
    private boolean pathsEnabled = getPrefs().getBoolean("BlurringTunnelTracker.pathsEnabled",true);
    private boolean udpEnabled = getPrefs().getBoolean("BlurringTunnelTracker.udpEnabled",true);
    private float activityDecayFactor = getPrefs().getFloat("BlurringTunnelTracker.activityDecayFactor",0.9f);
    private boolean sendPosition = getPrefs().getBoolean("BlurringTunnelTracker.sendPosition",true);
    private boolean sendVelocity = getPrefs().getBoolean("BlurringTunnelTracker.sendVelocity",false);
    private boolean sendSize = getPrefs().getBoolean("BlurringTunnelTracker.sendSize",false);
    private boolean sendActivity = getPrefs().getBoolean("BlurringTunnelTracker.sendActivity",false);
    private int pathLength = getPrefs().getInt("BlurringTunnelTracker.pathLength", 50);
    private boolean useVelocity = getPrefs().getBoolean("BlurringTunnelTracker.useVelocity",true); // enabling this enables both computation and rendering of cluster velocities
    private boolean showClusters = getPrefs().getBoolean("BlurringTunnelTracker.showClusters",true);
    private float velAngDiffDegToNotMerge = getPrefs().getFloat("BlurringTunnelTracker.velAngDiffDegToNotMerge",60.0f);
    private boolean showClusterNumber = getPrefs().getBoolean("BlurringTunnelTracker.showClusterNumber",false);
    private boolean showClusterVelocity = getPrefs().getBoolean("BlurringTunnelTracker.showClusterVelocity",false);
    private float velocityVectorScaling = getPrefs().getFloat("BlurringTunnelTracker.velocityVectorScaling",1.0f);
    private final float VELOCITY_VECTOR_SCALING = 1e6f; // to scale rendering of cluster velocityPPT vector, velocityPPT is in pixels/tick=pixels/us so this gives 1 screen pixel per 1 pix/s actual vel
    private boolean showClusterMass = getPrefs().getBoolean("BlurringTunnelTracker.showClusterMass",false);
    private float maximumClusterLifetimeMs = getPrefs().getFloat("BlurringTunnelTracker.maximumClusterLifetimeMs",100.0f);

    /**
     * Creates a new instance of BlurringTunnelTracker.
     * @param chip
     */
    public BlurringTunnelTracker (AEChip chip){
        super(chip);
        this.chip = chip;
        chip.addObserver(this);
        final String movement = "Movement", disp = "Display", global = "Global", output = "Output", update = "Update", logging = "Logging";
        setPropertyTooltip(global,"maximumClusterLifetimeMs","upper limit of cluster lifetime. It increases by when the cluster is properly updated. Otherwise, it decreases. When the lifetime becomes zero, the cluster will be expired.");
        setPropertyTooltip(global,"minCellsPerCluster","the minimal amount of active cells in a cell group to be tracked");
        setPropertyTooltip(output,"udpEnabled","creates UDP output of the observed activities and cluster ");
        setPropertyTooltip(output,"sendActivity","should a histogram of the x-activity be send out");
        setPropertyTooltip(output,"activityDecayFactor","how fast should the x-activity histogram decay");
        setPropertyTooltip(output,"sendPosition","should the cluster position be send out");
        setPropertyTooltip(output,"sendVelocity","should the cluster velocity be send out");
        setPropertyTooltip(output,"sendSize","should the cluster size be send out");
        setPropertyTooltip(disp,"pathsEnabled","draws paths of clusters over some window");
        setPropertyTooltip(disp,"pathLength","paths are at most this many packets long");
        setPropertyTooltip(movement,"numVelocityPoints","the number of recent path points (one per packet of events) to use for velocity vector regression");
        setPropertyTooltip(movement,"useVelocity","uses measured cluster velocity to predict future position; vectors are scaled " + String.format("%.1f pix/pix/s",VELOCITY_VECTOR_SCALING / AEConstants.TICK_DEFAULT_US * 1e-6));
        setPropertyTooltip(disp,"showClusters","shows clusters");
        setPropertyTooltip(update,"velAngDiffDegToNotMerge","relative angle in degrees of cluster velocity vectors to not merge overlapping clusters");
        setPropertyTooltip(disp,"showClusterVelocity","annotates velocity in pixels/second");
        setPropertyTooltip(disp,"showClusterNumber","shows cluster ID number");
        setPropertyTooltip(disp,"showClusterMass","shows cluster mass");
        setPropertyTooltip(disp,"velocityVectorScaling","scaling of drawn velocity vectors");

        filterChainSetting();
        initFilter();
    }

    /**
     * sets the BlurringTunnelFilter as a enclosed filter to find cluster
     */
    protected void filterChainSetting (){
        bfilter = new BlurringTunnelFilter(chip);
        bfilter.addObserver(this); // to get us called during blurring filter iteration at least every updateIntervalUs
        setEnclosedFilter(bfilter);
    }

    /**
     * merge clusters that are too close to each other and that have sufficiently similar velocities (if velocityRatioToNotMergeClusters).
    this must be done interatively, because merging 4 or more clusters feedforward can result in more clusters than
    you start with. each time we merge two clusters, we start over, until there are no more merges on iteration.
    for each cluster, if it is close to another cluster then merge them and start over.
     */
    private void mergeClusters (){
        boolean mergePending;
        Cluster c1 = null;
        Cluster c2 = null;
        do{
            mergePending = false;
            int nc = clusters.size();
            outer:
            for ( int i = 0 ; i < nc ; i++ ){
                c1 = clusters.get(i);
                for ( int j = i + 1 ; j < nc ; j++ ){
                    c2 = clusters.get(j); // get the other cluster
                    final boolean overlapping = c1.distanceTo(c2) < ( c1.getMaxRadius() + c2.getMaxRadius() );
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
            if ( mergePending && c1 != null && c2 != null ){
//                System.out.print("Cluster_"+c1.getClusterNumber()+"("+c1.firstEventTimestamp+") and cluster_"+c2.getClusterNumber()+"("+c2.firstEventTimestamp+ ") are merged to ");
                clusters.add(new Cluster(c1,c2));
                clusters.remove(c1);
                clusters.remove(c2);

//                System.out.print("Age of "+clusters.size()+" clusters after merging : ");
//                for (Cluster c : clusters) {
//                    System.out.print("cluster("+c.getClusterNumber()+")-"+c.getAgeUpdates()+", ");
//                }
//                System.out.println("");
            }
        } while ( mergePending );

    }

    public void initFilter (){
        resetFilter();
    }

    /**
     * Prunes out old clusters that don't have support or that should be purged for some other reason.
     */
    private void pruneClusters (){
//        System.out.println(pruneList.size()+ " clusters are removed");
        clusters.removeAll(pruneList);
        pruneList.clear();
    }

    /** This method updates the list of clusters, pruning and
     * merging clusters and updating positions.
     * It also updates the optical gyro if enabled.
     *
     * @param t the global timestamp of the update.
     */
    private void updateClusterList (int t){
        mergeClusters();
        pruneClusters();
        updateClusterPaths(t);
    }

    /** Processes the incoming events to track clusters.
     *
     * @param in
     * @return packet of BlurringFilter2DTrackerEvent.
     */
    public EventPacket<?> filterPacket (EventPacket<?> in){
        if ( in == null ){
            return null;
        }

        if ( enclosedFilter != null ){
            out = enclosedFilter.filterPacket(in);
        } else{
            out = in;
        }

        update(this,new UpdateMessage(this,out,out.getLastTimestamp()));
        if(udpEnabled)sendOutput(in);

        return out;
    }

    public void sendOutput(EventPacket<?> in){
        byte[] message = new byte[maxClusters*8+4+4];
        if(sendActivity){
            for(BasicEvent e:in){
                xHistogram[e.x*dsx/csx] += 1;
            }
            //sequence #
            message[0]=(byte)((packetCounter & 0xff000000)>>>24);
            message[1]=(byte)((packetCounter & 0x00ff0000)>>>16);
            message[2]=(byte)((packetCounter & 0x0000ff00)>>>8);
            message[3]=(byte)((packetCounter & 0x000000ff));
            //msg
            message[4]=(byte)'h';
            message[5]=(byte)'i';
            message[6]=(byte)'s';
            message[7]=(byte)'t';
            //no nrc
            message[8]=0;
            message[9]=0;
            //data
            for (int i=0; i<dsx; i++){
                message[10+2*i]=(byte)((xHistogram[i] & 0xff00>>>8));
                message[11+2*i]=(byte)((xHistogram[i] & 0x00ff));
            }
            try {
                sendPacket(message);
            } catch (IOException ex) {
                Logger.getLogger(SensorsToUDP.class.getName()).log(Level.SEVERE, null, ex);
            }

            for(int i = 0; i<xHistogram.length; i++){
                xHistogram[i] = (short)(xHistogram[i]*activityDecayFactor);
            }
            packetCounter += 1;
        }
        if(sendPosition && clusters.size()>0){
            //sequence #
            message[0]=(byte)((packetCounter & 0xff000000)>>>24);
            message[1]=(byte)((packetCounter & 0x00ff0000)>>>16);
            message[2]=(byte)((packetCounter & 0x0000ff00)>>>8);
            message[3]=(byte)((packetCounter & 0x000000ff));
            //msg
            message[4]=(byte)'p';
            message[5]=(byte)'o';
            message[6]=(byte)'s';
            message[7]=(byte)'n';
            //number of clusters
            message[8]=(byte)((clusters.size() & 0x0000ff00)>>>8);
            message[9]=(byte)(clusters.size() & 0x000000ff);
            //data
            for(int i=0; i<clusters.size(); i++){
                //System.out.println("location: "+clusters.get(i).getLocation().x+"  NrCells: "+clusters.get(i).numCells);
                int offset=8*i;
                //x position
                int xInt = Float.floatToRawIntBits(clusters.get(i).getLocation().x*dsx/csx);
                message[10+offset]=(byte)((xInt & 0xff000000)>>>24);
                message[11+offset]=(byte)((xInt & 0x00ff0000)>>>16);
                message[12+offset]=(byte)((xInt & 0x0000ff00)>>>8);
                message[13+offset]=(byte)((xInt & 0x000000ff));
                //y position
                int yInt = Float.floatToRawIntBits(clusters.get(i).getLocation().y*dsy/csy);
                message[14+offset]=(byte)((yInt & 0xff000000)>>>24);
                message[15+offset]=(byte)((yInt & 0x00ff0000)>>>16);
                message[16+offset]=(byte)((yInt & 0x0000ff00)>>>8);
                message[17+offset]=(byte)((yInt & 0x000000ff));
            }
            try {
                sendPacket(message);
            } catch (IOException ex) {
                Logger.getLogger(SensorsToUDP.class.getName()).log(Level.SEVERE, null, ex);
            }
            packetCounter += 1;
        }
        if(sendVelocity && clusters.size()>0){
            //sequence #
            message[0]=(byte)((packetCounter & 0xff000000)>>>24);
            message[1]=(byte)((packetCounter & 0x00ff0000)>>>16);
            message[2]=(byte)((packetCounter & 0x0000ff00)>>>8);
            message[3]=(byte)((packetCounter & 0x000000ff));
            //msg
            message[4]=(byte)'v';
            message[5]=(byte)'e';
            message[6]=(byte)'l';
            message[7]=(byte)'o';
            //number of clusters
            message[8]=(byte)((clusters.size() & 0x0000ff00)>>>8);
            message[9]=(byte)(clusters.size() & 0x000000ff);
            //data
            for(int i=0; i<clusters.size(); i++){
                int offset=8*i;
                //x velocity
                int xInt = Float.floatToRawIntBits(clusters.get(i).velocityPPS.x);
                message[10+offset]=(byte)((xInt & 0xff000000)>>>24);
                message[11+offset]=(byte)((xInt & 0x00ff0000)>>>16);
                message[12+offset]=(byte)((xInt & 0x0000ff00)>>>8);
                message[13+offset]=(byte)((xInt & 0x000000ff));
                //y velocity
                int yInt = Float.floatToRawIntBits(clusters.get(i).velocityPPS.y);
                message[14+offset]=(byte)((yInt & 0xff000000)>>>24);
                message[15+offset]=(byte)((yInt & 0x00ff0000)>>>16);
                message[16+offset]=(byte)((yInt & 0x0000ff00)>>>8);
                message[17+offset]=(byte)((yInt & 0x000000ff));
            }
            try {
                sendPacket(message);
            } catch (IOException ex) {
                Logger.getLogger(SensorsToUDP.class.getName()).log(Level.SEVERE, null, ex);
            }
            packetCounter += 1;
        }
        if(sendSize && clusters.size()>0){
            //sequence #
            message[0]=(byte)((packetCounter & 0xff000000)>>>24);
            message[1]=(byte)((packetCounter & 0x00ff0000)>>>16);
            message[2]=(byte)((packetCounter & 0x0000ff00)>>>8);
            message[3]=(byte)((packetCounter & 0x000000ff));
            //msg
            message[4]=(byte)'s';
            message[5]=(byte)'i';
            message[6]=(byte)'z';
            message[7]=(byte)'e';
            //number of clusters
            message[8]=(byte)((clusters.size() & 0x0000ff00)>>>8);
            message[9]=(byte)(clusters.size() & 0x000000ff);
            //data
            for(int i=0; i<clusters.size(); i++){
                int offset=4*i;
                //size
                int xInt = clusters.get(i).numEvents;
                message[10+offset]=(byte)((xInt & 0xff000000)>>>24);
                message[11+offset]=(byte)((xInt & 0x00ff0000)>>>16);
                message[12+offset]=(byte)((xInt & 0x0000ff00)>>>8);
                message[13+offset]=(byte)((xInt & 0x000000ff));
            }
            try {
                sendPacket(message);
            } catch (IOException ex) {
                Logger.getLogger(SensorsToUDP.class.getName()).log(Level.SEVERE, null, ex);
            }
            packetCounter += 1;
        }
    }

    public void sendPacket(byte[] message) throws IOException {
        DatagramPacket packet = new DatagramPacket(message, message.length, address, commandPort);
        socket.send(packet);
    }

    /** the method that actually does the tracking
     * Tracking is done by selecting the right cell groups for the next cluster.
     *
     * @param cellGroup : a cell group detected by BlurringFilter2D
     */
    protected void track (CellGroup cellGroup,int initialAge){
        if ( cellGroup.getNumMemberCells() < minCellsPerCluster ){
            return;
        }

        // for input cell group, see which cluster it is closest to and add it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
        Cluster closest = null;
        closest = getNearestCluster(cellGroup); // find cluster that event falls within (or also within surround if scaling enabled)

        if ( closest != null ){
            closest.addGroup(cellGroup);
        } else{ // start a new cluster
            clusters.add(new Cluster(cellGroup,initialAge));
        }
    }

    /** Returns total number of clusters.
     *
     * @return number of Cluster's in clusters list.
     */
    public int getNumClusters (){
        return clusters.size();
    }

    @Override
    public String toString (){
        String s = clusters != null ? Integer.toString(clusters.size()) : null;
        String s2 = "BlurringTunnelTracker with " + s + " clusters ";
        return s2;
    }

    /** find the nearest cluster from the given cell group
     * The found cluster will be updated using the cell group.
     *
     * @param cg : a cell group
     * @return closest cluster
     */
    public Cluster getNearestCluster (CellGroup cg){
        float minDistance = Float.MAX_VALUE;
        Cluster closest = null;

        for ( Cluster c:clusters ){
            float dx = c.distanceToX(cg);
            float dy = c.distanceToY(cg);
            float aveRadius = ( c.getMaxRadius() + cg.getOutterRadiusPixels() ) / 2.0f;

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
    private void updateClusterPaths (int t){
        // update paths of clusters
        for ( Cluster c:clusters ){
            c.updatePath(t);
            c.setUpdated(false);
        }
    }
    /**
     * Cluster class
     */
    public class Cluster implements ClusterInterface{
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
         * in chip chip pixels
         */
        private float innerRadius, outterRadius, maxRadius;
        /**
         * true if the cluster is hitting any adge of the frame
         */
        protected boolean hitEdge = false;
        /**
         * dynamic age of the cluster. It increases as the cluster is updated, and decreases if it's not updated.
         */
        protected int ageUs = 0;
        /**
         * true if the cluster is updated
         */
        protected boolean updated = false;
        /**
         *Rendered color of cluster.
         */
        protected Color color = null;
        /**
         *Number of events collected by this cluster.
         */
        protected int numEvents = 0;
        /**
         *Number of cells collected by this cluster.
         */
        protected int numCells = 0;
        /**
         *The "mass" of the cluster is the weighted number of events it has collected.
         */
        protected float mass;
        /**
         * timestamp of the first and the last events ever collected by the cluster
         */
        protected int lastEventTimestamp, firstEventTimestamp;
        /**
         * assigned to be the absolute number of the cluster that has been created.
         */
        private int clusterNumber;
        private float[] rgb = new float[ 4 ];
        /**
         * trajectory of the cluster
         */
        protected ArrayList<ClusterPathPoint> path = new ArrayList<ClusterPathPoint>(getPathLength());
        private RollingVelocityFitter velocityFitter = new RollingVelocityFitter(path,numVelocityPoints);

        /** Overrides default hashCode to return {@link #clusterNumber}. This overriding
         * allows for storing clusters in lists and checking for them by their clusterNumber.
         *
         * @return clusterNumber
         */
        @Override
        public int hashCode (){
            return clusterNumber;
        }

        /** Two clusters are equal if their {@link #clusterNumber}'s are equal.
         *
         * @param obj another Cluster.
         * @return true if equal.
         */
        @Override
        public boolean equals (Object obj){ // derived from http://www.geocities.com/technofundo/tech/java/equalhash.html
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

        /** Constructs a default cluster.
         *
         */
        public Cluster (){
            float hue = random.nextFloat();
            Color c = Color.getHSBColor(hue,1f,1f);
            setColor(c);
            setClusterNumber(clusterCounter++);
            maxRadius = 0;
        }

        /** Constructs a cluster with the first cell group
         * The numEvents, location, birthLocation, first and last timestamps are set.
         * @param cg the cell group.
         */
        public Cluster (CellGroup cg,int initialAge){
            this();
            location = cg.getLocation();
            birthLocation = cg.getLocation();
            lastEventTimestamp = cg.getLastEventTimestamp();
            firstEventTimestamp = cg.getFirstEventTimestamp();
            numEvents = cg.getNumEvents();
            numCells = cg.getNumMemberCells();
            mass = cg.getMass();
            increaseAgeUs(initialAge);
            setRadius(cg, 0f, mass);
            hitEdge = cg.isHitEdge();
            if ( hitEdge ){
                ageUs = (int)( 1000 * maximumClusterLifetimeMs );
            }

//            System.out.println("Cluster_"+clusterNumber+" is created @"+firstEventTimestamp);
        }

        /** Constructs a cluster by merging two clusters.
         * All parameters of the resulting cluster should be reasonable combinations of the
         * source cluster parameters.
         * For example, the merged location values are weighted
         * by the mass of events that have supported each
         * source cluster, so that older clusters weigh more heavily
         * in the resulting cluster location. Subtle bugs or poor performance can result
         * from not properly handling the merging of parameters.
         *
         * @param one the first cluster
         * @param two the second cluster
         */
        public Cluster (Cluster one,Cluster two){
            this();

            Cluster older = one.clusterNumber < two.clusterNumber ? one : two;
            float leakyfactor = one.calMassLeakyfactor(two.lastEventTimestamp);
            float one_mass = one.mass;
            float two_mass = two.mass;

            clusterNumber = older.clusterNumber;

            if(leakyfactor > 1)
                two_mass /= leakyfactor;
            else
                one_mass *= leakyfactor;

            mass = one_mass + two_mass;
            numEvents = one.numEvents + two.numEvents;
            numCells = one.numCells + two.numCells;

            // merge locations by average weighted by mass of events supporting each cluster
            location.x = ( one.location.x * one_mass + two.location.x * two_mass ) / ( mass );
            location.y = ( one.location.y * one_mass + two.location.y * two_mass ) / ( mass );

            lastEventTimestamp = one.lastEventTimestamp > two.lastEventTimestamp ? one.lastEventTimestamp : two.lastEventTimestamp;
            firstEventTimestamp = one.firstEventTimestamp < two.firstEventTimestamp ? one.firstEventTimestamp : two.firstEventTimestamp;
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
         * calculates mass leaky factor
         * @param timeStamp
         * @return
         */
        private float calMassLeakyfactor(int timestamp){
            return (float) Math.exp(((float) lastEventTimestamp - timestamp) / bfilter.getCellMassTimeConstantUs());
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

        /** returns the number of events collected by the cluster at each update
         *
         * @return numEvents
         */
        public int getNumEvents (){
            return numEvents;
        }

        /**
         * The "mass" of the cluster is the mass of the CellGroup of the BlurringFilter2D.
         * @return the mass
         */
        public float getMass (){
            return mass;
        }

        /**
         *
         * @return lastEventTimestamp
         */
        public int getLastEventTimestamp (){
            return lastEventTimestamp;
        }

        /** updates cluster by one CellGroup
         *
         * @param inGroup
         */
        public void addGroup (CellGroup cg){
            float leakyfactor = calMassLeakyfactor(cg.getLastEventTimestamp());
            float curMass = mass;
            float cgMass = cg.getMass();

            if(leakyfactor > 1)
                cgMass /= leakyfactor;
            else
                curMass *= leakyfactor;

            numEvents = cg.getNumEvents();
            numCells = cg.getNumMemberCells();
            mass = curMass + cgMass;

            int timeshift = cg.getLastEventTimestamp() - lastEventTimestamp;
            if(timeshift < 0) timeshift = 0;
            location.x = ( location.x * curMass + (cg.getLocation().x + velocityPPT.x * timeshift) * cgMass ) / ( mass );
            location.y = ( location.y * curMass + (cg.getLocation().y  + velocityPPT.y * timeshift)* cgMass ) / ( mass );

            if(cg.getLastEventTimestamp() > lastEventTimestamp){
                increaseAgeUs(cg.getLastEventTimestamp() - lastEventTimestamp);
                lastEventTimestamp = cg.getLastEventTimestamp();
            }
                
            if ( maxRadius == 0 ){
                birthLocation = cg.getLocation();
                firstEventTimestamp = cg.getFirstEventTimestamp();
            }

            hitEdge = cg.isHitEdge();
            if ( hitEdge ){
                ageUs = (int)( 1000 * maximumClusterLifetimeMs );
            }

            setRadius(cg, curMass, cgMass);
        }

        /** Measures distance from cluster center to a cell group.
         * @return distance
         */
        private float distanceTo (CellGroup cg){
            final float dx = cg.getLocation().x - location.x;
            final float dy = cg.getLocation().y - location.y;
            return distanceMetric(dx,dy);
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
        private float distanceToX (CellGroup cg){
            int dt = cg.getLastEventTimestamp() - lastEventTimestamp;
            float currentLocationX = location.x;
            if ( useVelocity ){
                currentLocationX += velocityPPT.x * dt;
            }

            if ( currentLocationX < 0 ){
                currentLocationX = 0;
            } else if ( currentLocationX > chip.getSizeX() - 1 ){
                currentLocationX = chip.getSizeX() - 1;
            }

            return Math.abs(cg.getLocation().x - currentLocationX);
        }

        /** Measures distance in y direction, accounting for predicted movement of cluster
         *
         * @return distance in y direction of this cluster to the event
         */
        private float distanceToY (CellGroup cg){
            int dt = cg.getLastEventTimestamp() - lastEventTimestamp;
            float currentLocationY = location.y;
            if ( useVelocity ){
                currentLocationY += velocityPPT.y * dt;
            }

            if ( currentLocationY < 0 ){
                currentLocationY = 0;
            } else if ( currentLocationY > chip.getSizeY() - 1 ){
                currentLocationY = chip.getSizeY() - 1;
            }

            return Math.abs(cg.getLocation().y - currentLocationY);
        }

        /** Computes and returns distance to another cluster.
         * @param c
         * @return distance of this cluster to the other cluster in pixels.
         */
        protected final float distanceTo (Cluster c){
            // TODO doesn't use predicted location of clusters, only present locations
            float dx = c.location.x - location.x;
            float dy = c.location.y - location.y;
            return distanceMetric(dx,dy);
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

        /** returns true if the given cell group is inside the cluster
         *
         * @param cg
         * @return
         */
        private boolean doesCover (CellGroup cg){
            float radius = maxRadius;
            float dx, dy;

            dx = distanceToX(cg);
            dy = distanceToY(cg);
            if ( dx < radius && dy < radius ){
                return true;
            }

            return false;
        }

        /** Returns measure of cluster radius, here the maxRadius.
         *
         * @return the maxRadius radius.
         */
        public float getRadius (){
            return maxRadius;
        }

        /** returns the inner radius of the cluster
         *
         * @return innerRadius
         */
        public final float getInnerRadius (){
            return innerRadius;
        }

        /** returns the outter radius of the cluster
         * 
         * @return outterRadius
         */
        public final float getOutterRadius (){
            return outterRadius;
        }

        /** returns the max radius of the cluster
         *
         * @return maxRadius
         */
        public final float getMaxRadius (){
            return maxRadius;
        }

        /** the radius of a cluster is the distance in pixels from the cluster center
         * that is the putative model size.
         * If highwayPerspectiveEnabled is true, then the radius is set to a fixed size
         * depending on the defaultClusterRadius and the perspective
         * location of the cluster and r is ignored. The aspect ratio parameters
         * radiusX and radiusY of the cluster are also set.
         * @param r the radius in pixels
         */
        private void setRadius (CellGroup cg, float curMass, float cgMass){
            float totalMass = curMass + cgMass;
            innerRadius = (innerRadius*curMass + cg.getInnerRadiusPixels()*cgMass)/totalMass;
            outterRadius = (outterRadius*curMass + cg.getOutterRadiusPixels()*cgMass)/totalMass;
            float maxRadiusCandidate;

            if ( cg.isHitEdge() ){
                maxRadiusCandidate = outterRadius;
            } else{
                maxRadiusCandidate = ( outterRadius + cg.getAreaRadiusPixels() ) / 2.0f;
            }

            if ( maxRadius < maxRadiusCandidate ){
                maxRadius = maxRadiusCandidate;

                int chipSize = chip.getSizeX() < chip.getSizeY() ? chip.getSizeX() : chip.getSizeY();
                if ( maxRadius > chipSize * 0.3f ){
                    maxRadius = chipSize * 0.3f;
                }
            } else{
                maxRadius = maxRadius * 0.95f + outterRadius * 0.05f;
            }
        }

        /** get the cluster location
         *
         * @return location
         */
        final public Point2D.Float getLocation (){
            return location;
        }

        /** set the cluster location
         * 
         * @param loc
         */
        public void setLocation (Point2D.Float loc){
            this.location = loc;
        }

        /** @return lifetime of cluster in timestamp ticks, measured as lastEventTimestamp-firstEventTimestamp. */
        final public int getLifetime (){
            return lastEventTimestamp - firstEventTimestamp;
        }

        /** Updates path (historical) information for this cluster,
         * including cluster velocityPPT.
         * @param t current timestamp.
         */
        final public void updatePath (int t){
            if ( !pathsEnabled ){
                return;
            }
            path.add(new ClusterPathPoint(location.x,location.y,t,numEvents));
//            System.out.println("Added Path ("+location.x + ", "+location.y+") @"+t);
            if ( path.size() > getPathLength() ){
                path.remove(path.get(0));
            }
            updateVelocity();
        }

        /** Updates velocities of cluster.
         *
         * @param t current timestamp.
         */
        private void updateVelocity (){
            velocityFitter.update();
            if ( velocityFitter.valid ){
                velocityPPT.x = (float)velocityFitter.getXVelocity();
                velocityPPT.y = (float)velocityFitter.getYVelocity();
                velocityPPS.x = (float)( velocityFitter.getXVelocity() * VELPPS_SCALING );
                velocityPPS.y = (float)( velocityFitter.getYVelocity() * VELPPS_SCALING );
                velocityValid = true;
            } else{
                velocityValid = false;
            }
        }

        @Override
        public String toString (){
            return String.format("Cluster number=#%d numEvents=%d locationX=%d locationY=%d lifetime=%d speedPPS=%.2f",
                    getClusterNumber(),numEvents,
                    (int)location.x,
                    (int)location.y,
                    getLifetime(),
                    getSpeedPPS());
        }

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

        /** get the age of cluster.
         *
         * @return age of cluster in us
         */
        public int getAgeUs (){
            return ageUs;
        }

        /** increases the age of cluster.
         * Age increases twice faster than it decreases.
         * @param deltaAge
         * @return
         */
        public int increaseAgeUs (int deltaAge){
            if ( deltaAge > 0 ){
                ageUs += 2 * deltaAge;
            } else{
                ageUs += deltaAge;
            }

            if ( ageUs > (int)( 1000 * maximumClusterLifetimeMs ) ){
                ageUs = (int)( 1000 * maximumClusterLifetimeMs );
            }

            return ageUs;
        }

        /** returns true if the cluster age is greater than 1.
         * So, the cluster is visible after it has been updated at least once after created.
         * @return true if the cluster age is greater than 1
         */
        public boolean isVisible (){
            if ( getAgeUs() > 0 ){
                return true;
            } else{
                return false;
            }
        }
        /**
         * Does a moving or rolling linear regression (a linear fit) on updated ClusterPathPoint data.
         * The new data point replaces the oldest data point. Summary statistics holds the rollling values
         * and are updated by subtracting the oldest point and adding the newest one.
         * From <a href="http://en.wikipedia.org/wiki/Ordinary_least_squares#Summarizing_the_data">Wikipedia article on Ordinary least squares</a>.
         *<p>
        If velocityPPT cannot be estimated (e.g. due to only 2 identical points) it is not updated.
         * @author tobi
         */
        private class RollingVelocityFitter{
            private static final int LENGTH_DEFAULT = 5;
            private int length = LENGTH_DEFAULT;
            private double st = 0, sx = 0, sy = 0, stt = 0, sxt = 0, syt = 0, den = 1; // summary stats
            private ArrayList<ClusterPathPoint> points;
            private double xVelocityPPT = 0, yVelocityPPT = 0;
            private boolean valid = false;
            private int nPoints = 0;

            /** Creates a new instance of RollingLinearRegression */
            public RollingVelocityFitter (ArrayList<ClusterPathPoint> points,int length){
                this.points = points;
                this.length = length;
            }

            @Override
            public String toString (){
                return String.format("RollingVelocityFitter: \n" + "valid=%s nPoints=%d\n"
                        + "xVel=%e, yVel=%e\n"
                        + "st=%f sx=%f sy=%f, sxt=%f syt=%f den=%f",
                        valid,nPoints,
                        xVelocityPPT,yVelocityPPT,
                        st,sx,sy,sxt,syt,den);

            }

            /**
             * Updates estimated velocityPPT based on last point in path. If velocityPPT cannot be estimated
            it is not updated.
             * @param t current timestamp.
             */
            private synchronized void update (){
                int n = points.size();
                if ( n < 1 ){
                    return;
                }
                ClusterPathPoint p = points.get(n - 1); // take last point
                if ( p.getNEvents() == 0 ){
                    return;
                }
                nPoints++;
                if ( n > length ){
                    removeOldestPoint(); // discard data beyond range length
                }
                n = n > length ? length : n;  // n grows to max length
                float dt = p.t - firstEventTimestamp; // t is time since cluster formed, limits absolute t for numerics
                st += dt;
                sx += p.x;
                sy += p.y;
                stt += dt * dt;
                sxt += p.x * dt;
                syt += p.y * dt;
//                if(n<length) return; // don't estimate velocityPPT until we have all necessary points, results very noisy and send cluster off to infinity very often, would give NaN
                den = ( n * stt - st * st );
                if ( n >= length && den != 0 ){
                    valid = true;
                    xVelocityPPT = ( n * sxt - st * sx ) / den;
                    if ( Math.abs(xVelocityPPT) < 1e-7 ){
                        xVelocityPPT = 0;  // set velocities zero if it's under the precision of float type
                    }
                    yVelocityPPT = ( n * syt - st * sy ) / den;
                    if ( Math.abs(yVelocityPPT) < 1e-7 ){
                        yVelocityPPT = 0;  // set velocities zero if it's under the precision of float type
                    }
                    p.velocityPPT = new Point2D.Float((float)xVelocityPPT,(float)yVelocityPPT);
                } else{
                    valid = false;
                }
//                System.out.println(this.toString());
            }

            private void removeOldestPoint (){
                // takes away from summary states the oldest point
                ClusterPathPoint p = points.get(points.size() - length - 1);
                // if points has 5 points (0-4), length=3, then remove points(5-3-1)=points(1) leaving 2-4 which is correct
                float dt = p.t - firstEventTimestamp;
                st -= dt;
                sx -= p.x;
                sy -= p.y;
                stt -= dt * dt;
                sxt -= p.x * dt;
                syt -= p.y * dt;
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
                return xVelocityPPT;
            }

            public double getYVelocity (){
                return yVelocityPPT;
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
            return firstEventTimestamp;
        }

        /** set birth location of the cluster
         *
         * @param birthLocation
         */
        public void setBirthLocation (Point2D.Float birthLocation){
            this.birthLocation = birthLocation;
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
    public java.util.List<Cluster> getClusters (){
        return clusters;
    }

    /** @param x x location of pixel
     *@param y y location
     *@param fr the frame data
     *@param channel the RGB channel number 0-2
     *@param brightness the brightness 0-1
     */
    private final void colorPixel (final int x,final int y,final float[][][] fr,int channel,Color color){
        if ( y < 0 || y > fr.length - 1 || x < 0 || x > fr[0].length - 1 ){
            return;
        }
        float[] rgb = color.getRGBColorComponents(null);
        float[] f = fr[y][x];
        for ( int i = 0 ; i < 3 ; i++ ){
            f[i] = rgb[i];
        }
    }

    public Object getFilterState (){
        return null;
    }

    synchronized public void resetFilter (){
        clusters.clear();
        clusterCounter = 0;
        if(chip!=null){
            csx = chip.getSizeX();
            csy = chip.getSizeY();
            xHistogram = new short[dsx];
        }
        maxHistogramX = 1; // not 0 to avoid division by 0
        short val = 0;
        Arrays.fill(xHistogram,val);
        getEnclosedFilter().resetFilter();
        packetCounter = 0;
        try {
            socket = new DatagramSocket();
        } catch (SocketException ex) {
            Logger.getLogger(SensorsToUDP.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            address = InetAddress.getByName("localhost");
        } catch (UnknownHostException ex) {
            Logger.getLogger(SensorsToUDP.class.getName()).log(Level.SEVERE, null, ex);
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
        support.firePropertyChange("pathsEnabled",this.pathsEnabled,pathsEnabled);
        this.pathsEnabled = pathsEnabled;
        getPrefs().putBoolean("BlurringTunnelTracker.pathsEnabled",pathsEnabled);
    }

    /** Processes the events if the Observable is an EventFilter2D.
     *
     * @param o
     * @param arg an UpdateMessage if caller is notify from EventFilter2D.
     */
    public void update (Observable o,Object arg){
        if ( o instanceof EventFilter2D ){
            CellGroup tmpcg = null;
            Collection<CellGroup> cgCollection = bfilter.getCellGroup();
            UpdateMessage msg = (UpdateMessage)arg;

            for ( Cluster c:clusters ){
                tmpcg = null;
                Iterator itr = cgCollection.iterator();

                while ( itr.hasNext() ){
                    CellGroup cg = (CellGroup)itr.next();

                    if ( c.doesCover(cg) && !cg.isMatched() ){ // If there are multiple cell groups under coverage of this cluster, merge all cell groups into one
                        if ( tmpcg == null ){
                            tmpcg = cg;
                            cg.setMatched(true);
                        } else{
                            tmpcg.merge(cg);
                            cgCollection.remove(cg);
                            itr = cgCollection.iterator();
                        }
                    }
                }
                if ( tmpcg != null ){
                    c.addGroup(tmpcg);
                    c.setUpdated(true);
                    cgCollection.remove(tmpcg);
                } else{
                    c.increaseAgeUs(-msg.packet.getDurationUs());
                }

                if ( c.getAgeUs() <= 0 ){
                    pruneList.add(c);
                }
                if (c.numCells < minCellsPerCluster){
                    pruneList.add(c);
                }
            }

            // Create cluster for the rest cell groups
            if ( cgCollection.size() != 0 &&  clusters.size() == 0 ){
                for ( CellGroup cg:cgCollection ){
                    track(cg,msg.packet.getDurationUs());
                }
            }

            updateClusterList(bfilter.getLastTime());
            maybeCallUpdateObservers(msg.packet,msg.timestamp); // callback to update() of any listeners on us, e.g. VirtualDrummer

        } else if ( o instanceof AEChip ){
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

    synchronized public void annotate (GLAutoDrawable drawable){
        if ( !isFilterEnabled() ){
            return;

        }
        GL gl = drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if ( gl == null ){
            log.warning("null GL in BlurringTunnelTracker.annotate");
            return;
        }
        gl.glPushMatrix();
        try{
            {
                for ( Cluster c:clusters ){
                    if ( showClusters && c.isVisible() ){
                        c.draw(drawable);
                    }
                }
            }
        } catch ( java.util.ConcurrentModificationException e ){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }

    /** Use cluster velocityPPT to estimate the location of cluster.
     * This is useful to select cell groups to take into this cluster.
     * @param useVelocity
     * @see #setPathsEnabled(boolean)
     */
    public void setUseVelocity (boolean useVelocity){
        if ( useVelocity ){
            setPathsEnabled(true);
        }
        support.firePropertyChange("useVelocity",this.useVelocity,useVelocity);
        this.useVelocity = useVelocity;
        getPrefs().putBoolean("BlurringTunnelTracker.useVelocity",useVelocity);
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
        getPrefs().putBoolean("BlurringTunnelTracker.showClusters",showClusters);
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
        getPrefs().putInt("BlurringTunnelTracker.pathLength",pathLength);
        support.firePropertyChange("pathLength",old,pathLength);
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
        getPrefs().putFloat("BlurringTunnelTracker.velAngDiffDegToNotMerge",velAngDiffDegToNotMerge);
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
        getPrefs().putBoolean("BlurringTunnelTracker.showClusterNumber",showClusterNumber);
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
        getPrefs().putBoolean("BlurringTunnelTracker.showClusterVelocity",showClusterVelocity);
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
        getPrefs().putFloat("BlurringTunnelTracker.velocityVectorScaling",velocityVectorScaling);
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
        getPrefs().putFloat("BlurringTunnelTracker.maximumClusterLifetimeMs",maximumClusterLifetimeMs);
        support.firePropertyChange("maximumClusterLifetimeMs",old,this.maximumClusterLifetimeMs);
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
        getPrefs().putBoolean("BlurringTunnelTracker.showClusterMass",showClusterMass);
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
        getPrefs().putInt("BlurringTunnelTracker.numVelocityPoints",velocityPoints);
        support.firePropertyChange("velocityPoints",old,this.numVelocityPoints);
    }

    /** @see #setMinCellsPerCluster(int)
     *
     * @return Minimum Cells per Cluster.
     */
    public int getMinCellsPerCluster (){
        return minCellsPerCluster;
    }

    /** Sets the number minimal cells per cluster
     *
     * @param minCellsPerCluster the amount of min. cells per cluster
     */
    public void setMinCellsPerCluster (int minCellsPerCluster){
        int old = this.minCellsPerCluster;
        this.minCellsPerCluster = minCellsPerCluster;
        getPrefs().putInt("BlurringTunnelTracker.minCellsPerCluster",minCellsPerCluster);
        support.firePropertyChange("minCellsPerCluster",old,this.minCellsPerCluster);

    }

    public boolean getUdpEnabled (){
        return udpEnabled;
    }

    public void setUdpEnabled (boolean udpEnabled){
        this.udpEnabled = udpEnabled;
        getPrefs().putBoolean("BlurringTunnelTracker.udpEnabled",udpEnabled);
    }

    public float getActivityDecayFactor (){
        return activityDecayFactor;
    }

    public void setActivityDecayFactor (float activityDecayFactor){
        float old = this.activityDecayFactor;
        this.activityDecayFactor = activityDecayFactor;
        getPrefs().putFloat("BlurringTunnelTracker.activityDecayFactor",activityDecayFactor);
        support.firePropertyChange("activityDecayFactor",old,this.activityDecayFactor);
    }

    public boolean getSendPosition (){
        return sendPosition;
    }

    public void setSendPosition (boolean sendPosition){
        this.sendPosition = sendPosition;
        getPrefs().putBoolean("BlurringTunnelTracker.sendPosition",sendPosition);
    }

    public boolean getSendVelocity (){
        return sendVelocity;
    }

    public void setSendVelocity (boolean sendVelocity){
        this.sendVelocity = sendVelocity;
        getPrefs().putBoolean("BlurringTunnelTracker.sendVelocity",sendVelocity);
    }

    public boolean getSendSize (){
        return sendSize;
    }

    public void setSendSize (boolean sendSize){
        this.sendSize = sendSize;
        getPrefs().putBoolean("BlurringTunnelTracker.sendSize",sendSize);
    }

    public boolean getSendActivity (){
        return sendActivity;
    }

    public void setSendActivity (boolean sendActivity){
        this.sendActivity = sendActivity;
        getPrefs().putBoolean("BlurringTunnelTracker.sendActivity",sendActivity);
    }
}
