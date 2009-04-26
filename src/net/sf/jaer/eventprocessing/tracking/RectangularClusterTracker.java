/*
 * RectangularClusterTracker.java
 *
 * Created on December 5, 2005, 3:49 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.eventprocessing.tracking;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.*;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.*;
import com.sun.opengl.util.*;
import java.awt.*;
//import ch.unizh.ini.caviar.util.PreferencesEditor;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.util.filter.BandpassFilter;
import net.sf.jaer.util.filter.LowpassFilter;
/**
 * Tracks blobs of events using a rectangular hypothesis about the object shape.
 * Many parameters constrain the hypothesese in various ways, including perspective projection, fixed aspect ratio,
 * variable size and aspect ratio, "mixing factor" that determines how much each event moves a cluster, etc.
 *
 * @author tobi
 */
public class RectangularClusterTracker extends EventFilter2D implements FrameAnnotater,Observer /*, PreferenceChangeListener*/{
    public static String getDescription(){
        return "Tracks multiple moving rectangular objects";
    }
//    private static Preferences prefs=Preferences.userNodeForPackage(RectangularClusterTracker.class);
//    PreferencesEditor editor;
//    JFrame preferencesFrame;
    private java.util.List<Cluster> clusters=new LinkedList<Cluster>();
    protected AEChip chip;
    private AEChipRenderer renderer;
    /** the number of classes of objects */
    private final int NUM_CLASSES=2;
    /** scaling can't make cluster bigger or smaller than this ratio to default cluster size */
    public static final float MAX_SCALE_RATIO=2;
    private float classSizeRatio=getPrefs().getFloat("RectangularClusterTracker.classSizeRatio",2);
    private boolean sizeClassificationEnabled=getPrefs().getBoolean("RectangularClusterTracker.sizeClassificationEnabled",true);


    {
        setPropertyTooltip("sizeClassificationEnabled","Enables coloring cluster by size threshold");
    }
    /** maximum and minimum allowed dynamic aspect ratio */
    public static final float ASPECT_RATIO_MAX=2.5f,  ASPECT_RATIO_MIN=0.5f;
    private boolean opticalGyroEnabled=getPrefs().getBoolean("RectangularClusterTracker.opticalGyroEnabled",false);
    {
        setPropertyTooltip("opticalGyroEnabled","enables global cluster movement reporting");
    }
    private float opticalGyroTauLowpassMs=getPrefs().getFloat("RectangularClusterTracker.opticalGyroTauLowpassMs",100);


    {
        setPropertyTooltip("opticalGyroTauLowpassMs","lowpass filter time constant in ms for optical gyro position, increase to smooth values");
    }

    /**
     * @return the enableClusterExitPurging
     */
    public boolean isEnableClusterExitPurging(){
        return enableClusterExitPurging;
    }

    /**
    Enables rapid purging of clusters that hit the edge of the scene.

     * @param enableClusterExitPurging the enableClusterExitPurging to set
     */
    public void setEnableClusterExitPurging(boolean enableClusterExitPurging){
        this.enableClusterExitPurging=enableClusterExitPurging;
        getPrefs().putBoolean("RectangularClusterTracker.enableClusterExitPurging",enableClusterExitPurging);
    }
//    private float opticalGyroTauHighpassMs=getPrefs().getInt("RectangularClusterTracker.opticalGyroTauHighpassMs", 10000);
//   {
//        setPropertyTooltip("opticalGyroTauHighpassMs", "highpass filter time constant in ms for optical gyro position, increase to forget DC value more slowly");
//    }
    private class OpticalGyroFilters{
        LowpassFilter x=new LowpassFilter();
        LowpassFilter y=new LowpassFilter();

        private OpticalGyroFilters(){
            x.setTauMs(opticalGyroTauLowpassMs);
            y.setTauMs(opticalGyroTauLowpassMs);
        }
//        private void setTauMsHigh(float opticalGyroTauHighpassMs) {
////            x.setTauMsHigh(opticalGyroTauHighpassMs);
////            y.setTauMsHigh(opticalGyroTauHighpassMs);
//        }

        private void setTauMsLow(float opticalGyroTauLowpassMs){
            x.setTauMs(opticalGyroTauLowpassMs);
            y.setTauMs(opticalGyroTauLowpassMs);
        }
    }
    OpticalGyroFilters opticalGyroFilters=new OpticalGyroFilters();
    protected float defaultClusterRadius;


    {
        setPropertyTooltip("defaultClusterRadius","default starting size of cluster as fraction of chip size");
    }
    protected float mixingFactor=getPrefs().getFloat("RectangularClusterTracker.mixingFactor",0.05f); // amount each event moves COM of cluster towards itself


    {
        setPropertyTooltip("mixingFactor","how much cluster is moved towards an event, as a fraction of the distance from the cluster to the event");
    }
//    protected float velocityMixingFactor=getPrefs().getFloat("RectangularClusterTracker.velocityMixingFactor",0.0005f); // mixing factor for velocity computation
//    {setPropertyTooltip("velocityMixingFactor","how much cluster velocity estimate is updated by each packet (IIR filter constant)");}
//    private float velocityTauMs=getPrefs().getFloat("RectangularClusterTracker.velocityTauMs",10);
//    {setPropertyTooltip("velocityTauMs","time constant in ms for cluster velocity lowpass filter");}
    private int velocityPoints=getPrefs().getInt("RectangularClusterTracker.velocityPoints",10);


    {
        setPropertyTooltip("velocityPoints","the number of recent path points (one per packet of events) to use for velocity vector regression");
    }
    private float surround=getPrefs().getFloat("RectangularClusterTracker.surround",2f);


    {
        setPropertyTooltip("surround","the radius is expanded by this ratio to define events that pull radius of cluster");
    }
    private boolean dynamicSizeEnabled=getPrefs().getBoolean("RectangularClusterTracker.dynamicSizeEnabled",false);


    {
        setPropertyTooltip("dynamicSizeEnabled","size varies dynamically depending on cluster events");
    }
    private boolean dynamicAspectRatioEnabled=getPrefs().getBoolean("RectangularClusterTracker.dynamicAspectRatioEnabled",false);


    {
        setPropertyTooltip("dynamicAspectRatioEnabled","aspect ratio of cluster depends on events");
    }
    private boolean dynamicAngleEnabled=getPrefs().getBoolean("RectangularClusterTracker.dynamicAngleEnabled",false);


    {
        setPropertyTooltip("dynamicAngleEnabled","angle of cluster depends on events, otherwise angle is zero");
    }
    private boolean pathsEnabled=getPrefs().getBoolean("RectangularClusterTracker.pathsEnabled",true);


    {
        setPropertyTooltip("pathsEnabled","draw paths of clusters over some window");
    }
    private int pathLength=getPrefs().getInt("RectangularClusterTracker.pathLength",100);


    {
        setPropertyTooltip("pathLength","paths are at most this many packets long");
    }
    private boolean colorClustersDifferentlyEnabled=getPrefs().getBoolean("RectangularClusterTracker.colorClustersDifferentlyEnabled",false);


    {
        setPropertyTooltip("colorClustersDifferentlyEnabled","each cluster gets assigned a random color, otherwise color indicates ages");
    }
    private boolean useOnePolarityOnlyEnabled=getPrefs().getBoolean("RectangularClusterTracker.useOnePolarityOnlyEnabled",false);


    {
        setPropertyTooltip("useOnePolarityOnlyEnabled","use only one event polarity");
    }
    private boolean useOffPolarityOnlyEnabled=getPrefs().getBoolean("RectangularClusterTracker.useOffPolarityOnlyEnabled",false);


    {
        setPropertyTooltip("useOffPolarityOnlyEnabled","use only OFF events, not ON - if useOnePolarityOnlyEnabled");
    }
    private float aspectRatio=getPrefs().getFloat("RectangularClusterTracker.aspectRatio",1f);


    {
        setPropertyTooltip("aspectRatio","default (or initial) aspect ratio, <1 is wide");
    }
    private float clusterSize=getPrefs().getFloat("RectangularClusterTracker.clusterSize",0.1f);


    {
        setPropertyTooltip("clusterSize","size (starting) in fraction of chip max size");
    }
    protected boolean growMergedSizeEnabled=getPrefs().getBoolean("RectangularClusterTracker.growMergedSizeEnabled",false);


    {
        setPropertyTooltip("growMergedSizeEnabled","enabling makes merged clusters take on sum of sizes, otherwise they take on size of older cluster");
    }
    private final float VELOCITY_VECTOR_SCALING=1e5f; // to scale rendering of cluster velocity vector, velocity is in pixels/tick=pixels/us so this gives 1 screen pixel per 10 pix/s actual vel
    private boolean useVelocity=getPrefs().getBoolean("RectangularClusterTracker.useVelocity",true); // enabling this enables both computation and rendering of cluster velocities


    {
        setPropertyTooltip("useVelocity","uses measured cluster velocity to predict future position; vectors are scaled "+String.format("%.1f pix/pix/s",VELOCITY_VECTOR_SCALING/AEConstants.TICK_DEFAULT_US*1e-6));
    }
    private boolean logDataEnabled=false;


    {
        setPropertyTooltip("logDataEnabled","writes a cluster log file called RectangularClusterTrackerLog.txt in the startup folder host/java");
    }
    private PrintStream logStream=null;
    private boolean classifierEnabled=getPrefs().getBoolean("RectangularClusterTracker.classifierEnabled",false);


    {
        setPropertyTooltip("classifierEnabled","colors clusters based on single size metric");
    }
    private float classifierThreshold=getPrefs().getFloat("RectangularClusterTracker.classifierThreshold",0.2f);


    {
        setPropertyTooltip("classifierThreshold","the boundary for cluster size classification in fractions of chip max dimension");
    }
    private boolean showAllClusters=getPrefs().getBoolean("RectangularClusterTracker.showAllClusters",false);


    {
        setPropertyTooltip("showAllClusters","shows all clusters, not just those with sufficient support");
    }
    private boolean useNearestCluster=getPrefs().getBoolean("RectangularClusterTracker.useNearestCluster",false); // use the nearest cluster to an event, not the first containing it


    {
        setPropertyTooltip("useNearestCluster","event goes to nearest cluster, not to first (usually oldest) cluster containing it");
    }
    private boolean clusterLifetimeIncreasesWithAge=getPrefs().getBoolean("RectangularClusterTracker.clusterLifetimeIncreasesWithAge",true);


    {
        setPropertyTooltip("clusterLifetimeIncreasesWithAge","older clusters can live longer (up to clusterLifetimeWithoutSupportUs) without support, good for objects that stop (like walking flies)");
    }
    private int predictiveVelocityFactor=1;// making this M=10, for example, will cause cluster to substantially lead the events, then slow down, speed up, etc.


    {
        setPropertyTooltip("predictiveVelocityFactor","how much cluster position leads position based on estimated velocity");
    }
    private boolean highwayPerspectiveEnabled=getPrefs().getBoolean("RectangularClusterTracker.highwayPerspectiveEnabled",false);


    {
        setPropertyTooltip("highwayPerspectiveEnabled","Cluster size depends on perspective location; mouse click defines horizon");
    }
    private int thresholdEventsForVisibleCluster=getPrefs().getInt("RectangularClusterTracker.thresholdEventsForVisibleCluster",10);


    {
        setPropertyTooltip("thresholdEventsForVisibleCluster","Cluster needs this many events to be visible");
    }
    private float thresholdVelocityForVisibleCluster=getPrefs().getFloat("RectangularClusterTracker.thresholdVelocityForVisibleCluster",0);


    {
        setPropertyTooltip("thresholdVelocityForVisibleCluster","cluster must have at least this velocity in pixels/sec to become visible");
    }
    private int clusterLifetimeWithoutSupportUs=getPrefs().getInt("RectangularClusterTracker.clusterLifetimeWithoutSupport",10000);


    {
        setPropertyTooltip("clusterLifetimeWithoutSupportUs","Cluster lives this long in ticks (e.g. us) without events before pruning");
    }
    private boolean enableClusterExitPurging=getPrefs().getBoolean("RectangularClusterTracker.enableClusterExitPurging",true);


    {
        setPropertyTooltip("enableClusterExitPurging","enables rapid purging of clusters that hit edge of scene");
    }

    /**
     * Creates a new instance of RectangularClusterTracker
     */
    public RectangularClusterTracker(AEChip chip){
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        initFilter();
        chip.addObserver(this);
//        prefs.addPreferenceChangeListener(this);
    }

    public void initFilter(){
        initDefaults();
        defaultClusterRadius=(int)Math.max(chip.getSizeX(),chip.getSizeY())*getClusterSize();
    }

    private void initDefaults(){
        initDefault("RectangularClusterTracker.clusterLifetimeWithoutSupport","10000");
        initDefault("RectangularClusterTracker.maxNumClusters","10");
        initDefault("RectangularClusterTracker.clusterSize","0.15f");
        initDefault("RectangularClusterTracker.numEventsStoredInCluster","100");
        initDefault("RectangularClusterTracker.thresholdEventsForVisibleCluster","30");

//        initDefault("RectangularClusterTracker.","");
    }

    private void initDefault(String key,String value){
        if(getPrefs().get(key,null)==null){
            getPrefs().put(key,value);
        }
    }
//    ArrayList<Cluster> pruneList=new ArrayList<Cluster>(1);
    protected LinkedList<Cluster> pruneList=new LinkedList<Cluster>();

    // the method that actually does the tracking
    synchronized private void track(EventPacket<BasicEvent> ae){
        int n=ae.getSize();
        if(n==0){
            return;
        }
//        int maxNumClusters=getMaxNumClusters();

        // for each event, see which cluster it is closest to and add it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
//        for(int i=0;i<n;i++){
        for(BasicEvent ev:ae){
//            EventXYType ev=ae.getEvent2D(i);
            Cluster closest=null;
            if(useNearestCluster){
                closest=getNearestCluster(ev);
            }else{
                closest=getFirstContainingCluster(ev); // find cluster that event falls within (or also within surround if scaling enabled)
            }
            if(closest!=null){
                closest.addEvent(ev);
            }else if(clusters.size()<maxNumClusters){ // start a new cluster
                Cluster newCluster=new Cluster(ev);
                clusters.add(newCluster);
            }
        }
        // prune out old clusters that don't have support or that should be purged for some other reason
        pruneList.clear();
        for(Cluster c:clusters){
            int t0=c.getLastEventTimestamp();
            int t1=ae.getLastTimestamp();
            int timeSinceSupport=t1-t0;
            if(timeSinceSupport==0){
                continue; // don't kill off cluster spawned from first event
            }
            boolean killOff=false;
            if(clusterLifetimeIncreasesWithAge){
                int age=c.getLifetime();
                int supportTime=clusterLifetimeWithoutSupportUs;
                if(age<clusterLifetimeWithoutSupportUs){
                    supportTime=age;
                }
                if(timeSinceSupport>supportTime){
                    killOff=true;
//                    System.out.println("pruning unsupported "+c);
                }
            }else{
                if(timeSinceSupport>clusterLifetimeWithoutSupportUs){
                    killOff=true;
//                    System.out.println("pruning unzupported "+c);
                }
            }
            boolean hitEdge=c.hasHitEdge();

            if(t0>t1||killOff||timeSinceSupport<0||hitEdge){
                // ordinarily, we discard the cluster if it hasn't gotten any support for a while, but we also discard it if there
                // is something funny about the timestamps
                pruneList.add(c);

            }
//            if(t0>t1){
//                log.warning("last cluster timestamp is later than last packet timestamp");
//            }
        }
        clusters.removeAll(pruneList);

        // merge clusters that are too close to each other.
        // this must be done interatively, because merging 4 or more clusters feedforward can result in more clusters than
        // you start with. each time we merge two clusters, we start over, until there are no more merges on iteration.

        // for each cluster, if it is close to another cluster then merge them and start over.

//        int beforeMergeCount=clusters.size();
        boolean mergePending;
        Cluster c1=null, c2=null;
        do{
            mergePending=false;
            int nc=clusters.size();
            outer:
            for(int i=0;i<nc;i++){
                c1=clusters.get(i);
                for(int j=i+1;j<nc;j++){
                    c2=clusters.get(j); // get the other cluster
                    if(c1.distanceTo(c2)<(c1.getRadius()+c2.getRadius())){ // if distance is less than sum of radii merge them
                        // if cluster is close to another cluster, merge them
                        mergePending=true;
                        break outer; // break out of the outer loop
                    }
                }
            }
            if(mergePending&&c1!=null&&c2!=null){
                pruneList.add(c1);
                pruneList.add(c2);
                clusters.remove(c1);
                clusters.remove(c2);
                clusters.add(new Cluster(c1,c2));
//                    System.out.println("merged "+c1+" and "+c2);
            }
        }while(mergePending);

        // update all cluster sizes
        // note that without this following call, clusters maintain their starting size until they are merged with another cluster.
        if(isHighwayPerspectiveEnabled()){
            for(Cluster c:clusters){
                c.setRadius(defaultClusterRadius);
            }
        }

        // update paths of clusters
        for(Cluster c:clusters){
            c.updatePath(ae);
        }

        // update optical gyro value
        if(isOpticalGyroEnabled()){
            int t=ae.getLastTimestamp();
            int nn=0;
            float[] xs=new float[clusters.size()], ys=new float[clusters.size()]; // will hold samples for median filtering
            for(Cluster c:clusters){
                if(c.isVisible()){
                    xs[nn]=c.getLocation().x;
                    ys[nn]=c.getLocation().y;
                    nn++;
                }
            }
            if(nn>0){
                Arrays.sort(xs,0,nn-1);
                Arrays.sort(ys,0,nn-1);
                float xmed, ymed;
                if(nn%2!=0){
                    int nnn=nn/2;
                    xmed=xs[nnn];
                    ymed=ys[nnn];
                }else{
                    int nnn=nn/2;
                    xmed=(xs[nnn-1]+xs[nnn])/2;
                    ymed=(ys[nnn-1]+ys[nnn])/2;
                }

                opticalGyroFilters.x.filter(xmed,t);
                opticalGyroFilters.y.filter(ymed,t);
            }
        }

//        if(clusters.size()>beforeMergeCount) throw new RuntimeException("more clusters after merge than before");
        if(isLogDataEnabled()&&getNumClusters()>0){
            if(logStream!=null){
                for(Cluster c:clusters){
                    if(!c.isVisible()){
                        continue;
                    }
                    logStream.println(String.format("%d %d %f %f %f",c.getClusterNumber(),c.lastTimestamp,c.location.x,c.location.y,c.averageEventDistance));
                    if(logStream.checkError()){
                        log.warning("eroror logging data");
                    }
                }
            }
        }
    }

    public int getNumClusters(){
        return clusters.size();
    }

    @Override
    public String toString(){
        String s=clusters!=null?Integer.toString(clusters.size()):null;
        String s2="RectangularClusterTracker with "+s+" clusters ";
        return s2;
    }

    /**
     * Method that given event, returns closest cluster and distance to it. The actual computation returns the first cluster that is within the
     * minDistance of the event, which reduces the computation at the cost of reduced precision.
     * @param event the event
     * @return closest cluster object (a cluster with a distance - that distance is the distance between the given event and the returned cluster).
     */
    private Cluster getNearestCluster(BasicEvent event){
        float minDistance=Float.MAX_VALUE;
        Cluster closest=null;
        float currentDistance=0;
        for(Cluster c:clusters){
            float rX=c.radiusX;
            float rY=c.radiusY; // this is surround region for purposes of dynamicSize scaling of cluster size or aspect ratio
            if(dynamicSizeEnabled){
                rX*=surround;
                rY*=surround; // the event is captured even when it is in "invisible surround"
            }
            float dx, dy;
            if((dx=c.distanceToX(event))<rX&&(dy=c.distanceToY(event))<rY){
                currentDistance=dx+dy;
                if(currentDistance<minDistance){
                    closest=c;
                    minDistance=currentDistance;
                    c.distanceToLastEvent=minDistance;
                }
            }
        }
        return closest;
    }

    /** Given AE, returns first (thus oldest) cluster that event is within.
     * The radius of the cluster here depends on whether {@link #setdynamicSizeEnabled scaling} is enabled.
     * @param event the event
     * @return cluster that contains event within the cluster's radius, modfied by aspect ratio. null is returned if no cluster is close enough.
     */
    private Cluster getFirstContainingCluster(BasicEvent event){
        float minDistance=Float.MAX_VALUE;
        Cluster closest=null;
        float currentDistance=0;
        for(Cluster c:clusters){
            float rX=c.radiusX;
            float rY=c.radiusY; // this is surround region for purposes of dynamicSize scaling of cluster size or aspect ratio
            if(dynamicSizeEnabled){
                rX*=surround;
                rY*=surround; // the event is captured even when it is in "invisible surround"
            }
            float dx, dy;
            if((dx=c.distanceToX(event))<rX&&(dy=c.distanceToY(event))<rY){
                currentDistance=dx+dy;
                closest=c;
                minDistance=currentDistance;
                c.distanceToLastEvent=minDistance;
                break;
            }
        }
        return closest;
    }
    protected int clusterCounter=0; // keeps track of absolute cluster number

    /** Represents a single tracked object */
    /**
     * @return the opticalGyroTauLowpassMs
     */
    public float getOpticalGyroTauLowpassMs(){
        return opticalGyroTauLowpassMs;
    }

    /**
     * @param opticalGyroTauLowpassMs the opticalGyroTauLowpassMs to set
     */
    public void setOpticalGyroTauLowpassMs(float opticalGyroTauLowpassMs){
        this.opticalGyroTauLowpassMs=opticalGyroTauLowpassMs;
        getPrefs().putFloat("RectangularClusterTracker.opticalGyroTauLowpassMs",opticalGyroTauLowpassMs);
        opticalGyroFilters.setTauMsLow(opticalGyroTauLowpassMs);
    }
//    /**
//     * @return the opticalGyroTauHighpassMs
//     */
//    public float getOpticalGyroTauHighpassMs() {
//        return opticalGyroTauHighpassMs;
//    }
//
//    /**
//     * @param opticalGyroTauHighpassMs the opticalGyroTauHighpassMs to set
//     */
//    public void setOpticalGyroTauHighpassMs(float opticalGyroTauHighpassMs) {
//        this.opticalGyroTauHighpassMs=opticalGyroTauHighpassMs;
//        getPrefs().putFloat("RectangularClusterTracker.opticalGyroTauHighpassMs",opticalGyroTauHighpassMs);
//        opticalGyroFilters.setTauMsHigh(opticalGyroTauHighpassMs);
//    }
    public class Cluster{
        private final int MIN_DT_FOR_VELOCITY_UPDATE=10;
        /** location of cluster in pixels */
        public Point2D.Float location=new Point2D.Float(); // location in chip pixels
        private Point2D.Float birthLocation=new Point2D.Float(); // birth location of cluster
        /** velocity of cluster in pixels/tick, where tick is timestamp tick (usually microseconds) */
        protected Point2D.Float velocity=new Point2D.Float(); // velocity in chip pixels/tick
        private Point2D.Float velocityPPS=new Point2D.Float(); // cluster velocity in pixels/second
        private boolean velocityValid=false; // used to flag invalid or uncomputable velocity
//        private LowpassFilter vxFilter=new LowpassFilter(), vyFilter=new LowpassFilter();
        final float VELPPS_SCALING=1e6f/AEConstants.TICK_DEFAULT_US;
//        public float tauMsVelocity=50; // LP filter time constant for velocity change
//        private LowpassFilter velocityFilter=new LowpassFilter();
        private float radius; // in chip chip pixels
//        private float mass; // a cluster has a mass correspoding to its support - the higher the mass, the harder it is to change its velocity
        private float aspectRatio,  radiusX,  radiusY;
        /** Angle of cluster in radians with zero being horizontal and CCW > 0. */
        private float angle;
        protected ArrayList<PathPoint> path=new ArrayList<PathPoint>(getPathLength());

        /** Returns true if this test is enabled and if the cluster has hit the edge of the array with a velocity vector
        or position in the direction of the nearest edge.
        @return true if cluster has hit edge and test enableClusterExitPurging
         */
        private boolean hasHitEdge(){
            if(!enableClusterExitPurging){
                return false;
            }
            int lx=(int)location.x, ly=(int)location.y;
            int sx=chip.getSizeX(), sy=chip.getSizeY();
            int rad=(int)getRadiusCorrectedForPerspective();
            if(lx<radius||lx>sx-rad){
                return true; // TODO check radii in x and y directions, not just one of them
            }
            if(ly<radius||ly>sy-rad){
                return true;
            }
            return false;
        }
        public class PathPoint extends Point2D.Float{
            private int t; // timestamp of this point
            private int nEvents; // num events contributed to this point

            public PathPoint(float x,float y,int t,int numEvents){
                this.x=x;
                this.y=y;
                this.t=t;
                this.nEvents=numEvents;
            }

            public int getT(){
                return t;
            }

            public void setT(int t){
                this.t=t;
            }

            public int getNEvents(){
                return nEvents;
            }

            public void setNEvents(int nEvents){
                this.nEvents=nEvents;
            }
        }
        private RollingVelocityFitter velocityFitter=new RollingVelocityFitter(path,velocityPoints);
        protected Color color=null;
        protected int numEvents=0,  previousNumEvents=0; // total number of events and number at previous packet
//        ArrayList<EventXYType> events=new ArrayList<EventXYType>();
        protected int lastTimestamp,  firstTimestamp;  // first (birth) and last (most recent event) timestamp for this cluster
        protected float instantaneousEventRate; // in events/tick
        private float avgEventRate=0;
        protected float instantaneousISI; // ticks/event
        private float avgISI;
        private int clusterNumber; // assigned to be the absolute number of the cluster that has been created
        private float averageEventDistance; // average (mixed) distance of events from cluster center, a measure of actual cluster size
        protected float distanceToLastEvent=Float.POSITIVE_INFINITY;
        boolean hasObtainedSupport=false;

        public Cluster(){
            setRadius(defaultClusterRadius);
            float hue=random.nextFloat();
            Color c=Color.getHSBColor(hue,1f,1f);
            setColor(c);
            setClusterNumber(clusterCounter++);
            setAspectRatio(RectangularClusterTracker.this.getAspectRatio());
//            vxFilter.setTauMs(velocityTauMs);
//            vyFilter.setTauMs(velocityTauMs);
        }

        public Cluster(BasicEvent ev){
            this();
            location.x=ev.x;
            location.y=ev.y;
            birthLocation.x=ev.x;
            birthLocation.y=ev.y;
            lastTimestamp=ev.timestamp;
            firstTimestamp=lastTimestamp;
            numEvents=1;
            setRadius(defaultClusterRadius);
//            System.out.println("constructed "+this);
        }

        /**
         * Computes a geometrical scale factor based on location of a point relative to the vanishing point.
         * If a pixel has been selected (we ask the renderer) then we compute the perspective from this vanishing point, otherwise
         * it is the top middle pixel.
         * @param p a point with 0,0 at lower left corner
         * @return scale factor, which grows linearly to 1 at botton of scene
         */
        final float getPerspectiveScaleFactor(Point2D.Float p){
            if(!highwayPerspectiveEnabled){
                return 1;
            }
            final float MIN_SCALE=0.1f; // to prevent microclusters that hold only a single pixel
            if(!renderer.isPixelSelected()){
                float scale=1f-(p.y/chip.getSizeY()); // yfrac grows to 1 at bottom of image
                if(scale<MIN_SCALE){
                    scale=MIN_SCALE;
                }
                return scale;
            }else{
                // scale is MIN_SCALE at vanishing point or above and grows linearly to 1 at max size of chip
                int size=chip.getMaxSize();
                float d=(float)p.distance(renderer.getXsel(),renderer.getYsel());
                float scale=d/size;
                if(scale<MIN_SCALE){
                    scale=MIN_SCALE;
                }
                return scale;
            }
        }

        /** Constructs a cluster by merging two clusters. All parameters of the resulting cluster should be reasonable combinations of the
         * source cluster parameters. For example, the merged location values are weighted by the number of events that have supported each
         * source cluster, so that older clusters weigh more heavily in the resulting cluster location. Subtle bugs or poor performance can result
         * from not properly handling the merging of parameters.
         *
         * @param one the first cluster
         * @param two the second cluster
         */
        public Cluster(Cluster one,Cluster two){
            this();
            // merge locations by just averaging
//            location.x=(one.location.x+two.location.x)/2;
//            location.y=(one.location.y+two.location.y)/2;

            Cluster older=one.firstTimestamp<two.firstTimestamp?one:two;
//            Cluster older=one.numEvents>two.numEvents? one:two;

            // merge locations by average weighted by number of events supporting cluster
            int sumEvents=one.numEvents+two.numEvents;
            location.x=(one.location.x*one.numEvents+two.location.x*two.numEvents)/(sumEvents);
            location.y=(one.location.y*one.numEvents+two.location.y*two.numEvents)/(sumEvents);
            angle=older.angle;
            averageEventDistance=(one.averageEventDistance*one.numEvents+two.averageEventDistance*two.numEvents)/sumEvents;
            lastTimestamp=one.lastTimestamp>two.lastTimestamp?one.lastTimestamp:two.lastTimestamp;
            numEvents=sumEvents;
            firstTimestamp=older.firstTimestamp; // make lifetime the oldest src cluster
            path=older.path;
            birthLocation=older.birthLocation;
            velocityFitter=older.velocityFitter;
            velocity.x=older.velocity.x;
            velocity.y=older.velocity.y;
            velocityPPS.x=older.velocityPPS.x;
            velocityPPS.y=older.velocityPPS.y;
            velocityValid=older.velocityValid;
//            vxFilter=older.vxFilter;
//            vyFilter=older.vyFilter;
            avgEventRate=older.avgEventRate;
            avgISI=older.avgISI;
            hasObtainedSupport=older.hasObtainedSupport;
            setAspectRatio(older.getAspectRatio());

//            Color c1=one.getColor(), c2=two.getColor();
            setColor(older.getColor());
//            System.out.println("merged "+one+" with "+two);
            //the radius should increase
//            setRadius((one.getRadius()+two.getRadius())/2);
            if(growMergedSizeEnabled){
                float R=(one.getRadius()+two.getRadius())/2;
                setRadius(R+getMixingFactor()*R);
            }else{
                setRadius(older.getRadius());
            }

        }

        public int getLastEventTimestamp(){
//            EventXYType ev=events.get(events.size()-1);
//            return ev.timestamp;
            return lastTimestamp;
        }

        /** updates cluster by one event. The cluster velocity is updated at the filterPacket level after all events 
        in a packet are added.
        @param event the event
         */
        public void addEvent(BasicEvent event){
            if((event instanceof TypedEvent)){
                TypedEvent e=(TypedEvent)event;
                if(useOnePolarityOnlyEnabled){
                    if(useOffPolarityOnlyEnabled){
                        if(e.type==1){
                            return;
                        }
                    }else{
                        if(e.type==0){
                            return;
                        }
                    }
                }
            }

            // save location for computing velocity
            float oldx=location.x, oldy=location.y;

            float m=mixingFactor, m1=1-m;

            float dt=event.timestamp-lastTimestamp; // this timestamp may be bogus if it goes backwards in time, we need to check it later

            // if useVelocity is enabled, first update the location using the measured estimate of velocity.
            // this will give predictor characteristic to cluster because cluster will move ahead to the predicted location of
            // the present event
            if(useVelocity&&dt>0&&velocityFitter.valid){
                location.x=location.x+predictiveVelocityFactor*dt*velocity.x;
                location.y=location.y+predictiveVelocityFactor*dt*velocity.y;
            }

            // compute new cluster location by mixing old location with event location by using
            // mixing factor

            location.x=(m1*location.x+m*event.x);
            location.y=(m1*location.y+m*event.y);

            // velocity of cluster is updated here as follows
            // 1. instantaneous velocity is computed from old and new cluster locations and dt
            // 2. new velocity is computed by mixing old velocity with instaneous new velocity using velocityMixingFactor
            // Since an event may pull the cluster back in the opposite direction it is moving, this measure is likely to be quite noisy.
            // It would be better to use the saved cluster locations after each packet is processed to perform an online regression
            // over the history of the cluster locations. Therefore we do not use the following anymore.
//            if(useVelocity && dt>0){
//                // update velocity vector using old and new position only if valid dt
//                // and update it by the mixing factors
//                float oldvelx=velocity.x;
//                float oldvely=velocity.y;
//
//                float velx=(location.x-oldx)/dt; // instantaneous velocity for this event in pixels/tick (pixels/us)
//                float vely=(location.y-oldy)/dt;
//
//                float vm1=1-velocityMixingFactor;
//                velocity.x=vm1*oldvelx+velocityMixingFactor*velx;
//                velocity.y=vm1*oldvely+velocityMixingFactor*vely;
//                velocityPPS.x=velocity.x*VELPPS_SCALING;
//                velocityPPS.y=velocity.y*VELPPS_SCALING;
//            }

            int prevLastTimestamp=lastTimestamp;
            lastTimestamp=event.timestamp;
            numEvents++;
            instantaneousISI=lastTimestamp-prevLastTimestamp;
            if(instantaneousISI<=0){
                instantaneousISI=1;
            }
            avgISI=m1*avgISI+m*instantaneousISI;
            instantaneousEventRate=1f/instantaneousISI;
            avgEventRate=m1*avgEventRate+m*instantaneousEventRate;

            averageEventDistance=m1*averageEventDistance+m*distanceToLastEvent;

            // if scaling is enabled, now scale the cluster size
            scale(event);

        }

        /** sets the cluster radius according to distance of event from cluster center, but only if dynamicSizeEnabled or dynamicAspectRatioEnabled.
         * @param event the event to scale with
         */
        private final void scale(BasicEvent event){
            if(dynamicSizeEnabled){
                float dist=distanceTo(event);
                float oldr=radius;
                float newr=(1-mixingFactor)*oldr+dist*mixingFactor;
                float f;
                if(newr>(f=defaultClusterRadius*MAX_SCALE_RATIO)){
                    newr=f;
                }else if(newr<(f=defaultClusterRadius/MAX_SCALE_RATIO)){
                    newr=f;
                }
                setRadius(newr);
            }
            if(dynamicAspectRatioEnabled){
                float dx=(location.x-event.x);
                float dy=(location.y-event.y);
                float oldAspectRatio=getAspectRatio();
                float newAspectRatio=Math.abs(dy/dx/2);
                if(newAspectRatio>ASPECT_RATIO_MAX){
                    newAspectRatio=ASPECT_RATIO_MAX;
                }else if(newAspectRatio<ASPECT_RATIO_MIN){
                    newAspectRatio=ASPECT_RATIO_MIN;
                }
                setAspectRatio((1-mixingFactor)*oldAspectRatio+mixingFactor*newAspectRatio);
            }
            if(dynamicAngleEnabled){
                // awkwardness here is that events will fall on either side around center of cluster.
                // angle of event is 0 or +/-PI when events are mostly horizontal (there is a cut at +/-PI from atan2).
                // similarly, if events are mostly vertical, then angle is either PI/2 or -PI/2.
                // if we just average instantaneous angle we get something in between which is at 90 deg
                // to actual angle of cluster.
                // if the event angle<0, we use PI-angle; this transformation makes all event angles fall from 0 to PI.
                // now the problem is that horizontal events still average to PI/2 (vertical cluster).

                float dx=(location.x-event.x);
                float dy=(location.y-event.y);
                float newAngle=(float)(Math.atan2(dy,dx));
                if(newAngle<0){
                    newAngle+=(float)Math.PI; // puts newAngle in 0,PI, e.g -30deg becomes 150deg
                }
                // if newAngle is very different than established angle, assume it is 
                // just the other end of the object and flip the newAngle.
//                boolean flippedPos=false, flippedNeg=false;
                float diff=newAngle-angle;
                if((diff)>Math.PI/2){
                    // newAngle is clockwise a lot, flip it back across to
                    // negative value that can be averaged; e.g. angle=10, newAngle=179, newAngle->-1.
                    newAngle=newAngle-(float)Math.PI;
//                    flippedPos=true;
                }else if(diff<-Math.PI/2){
                    // newAngle is CCW
                    newAngle=-(float)Math.PI+newAngle; // angle=10, newAngle=179, newAngle->1
//                    flippedNeg=true;
                }
//                if(newAngle>3*Math.PI/4)
//                    newAngle=(float)Math.PI-newAngle;
                float angleDistance=(newAngle-angle); //angleDistance(angle, newAngle);
                // makes angle=0 for horizontal positive event, PI for horizontal negative event y=0+eps,x=-1, -PI for y=0-eps, x=-1, //
                // PI/2 for vertical positive, -Pi/2 for vertical negative event
                setAngle(angle+mixingFactor*angleDistance);
//                System.out.println(String.format("dx=%8.1f\tdy=%8.1f\tnewAngle=%8.1f\tangleDistance=%8.1f\tangle=%8.1f\tflippedPos=%s\tflippedNeg=%s",dx,dy,newAngle*180/Math.PI,angleDistance*180/Math.PI,angle*180/Math.PI,flippedPos,flippedNeg));
//                System.out.println(String.format("dx=%8.1f\tdy=%8.1f\tnewAngle=%8.1f\tangleDistance=%8.1f\tangle=%8.1f",dx,dy,newAngle*180/Math.PI,angleDistance*180/Math.PI,angle*180/Math.PI));
//                setAngle(-.1f);
            }
        }

        /**
         * Computes signed distance to-from between two angles with cut at -PI,PI. E.g.
         *     if e is from small angle and from=PI-e, to=-PI+e, then angular distance to-from is
         *     -2e rather than (PI-e)-(-PI+e)=2PI-2e.
         *     This minimum angle difference is useful to push an angle in the correct direction
         *     by the correct amount. For this example, we want to push an angle hovering around PI-e.
         *     We don't want angles of -PI+e to push the angle from lot, just from bit towards PI.
         *     If we have angle <code>from</code> and new angle <code>to</code> and
         *     mixing factor m<<1, then new angle <code>c=from+m*angleDistance(from,to)</code>.
         *
         *
         * @param from the first angle
         * @param to the second angle
         * @return the smallest difference to-from, ordinarily positive if to>from
         */
        private float angleDistance(float from,float to){
            float d=to-from;
            if(d>Math.PI){
                return d-(float)Math.PI;
            }
            if(d<-Math.PI){
                return d+(float)Math.PI;
            }
            return d;
        }

        /** @return distance of this cluster to the event in manhatten (cheap) metric (sum of abs values of x and y distance */
        private float distanceTo(BasicEvent event){
            final float dx=event.x-location.x;
            final float dy=event.y-location.y;
//            return Math.abs(dx)+Math.abs(dy);
            return distanceMetric(dx,dy);
//            dx*=dx;
//            dy*=dy;
//            float distance=(float)Math.sqrt(dx+dy);
//            return distance;
        }

        public float distanceMetric(float dx,float dy){
            return ((dx>0)?dx:-dx)+((dy>0)?dy:-dy);
        }

        /** @return distance in x direction of this cluster to the event */
        private float distanceToX(BasicEvent event){
            float distance=Math.abs(event.x-location.x);
            return distance;
        }

        /** @return distance in y direction of this cluster to the event */
        private float distanceToY(BasicEvent event){
            float distance=Math.abs(event.y-location.y);
            return distance;
        }

        /** @return distance of this cluster to the other cluster */
        protected final float distanceTo(Cluster c){
            float dx=c.location.x-location.x;
            float dy=c.location.y-location.y;
            return distanceMetric(dx,dy);
//            if(dx<0)dx=-dx;
//            if(dy<0)dy=-dy;
//            dx*=dx;
//            dy*=dy;
//            float distance=(float)Math.sqrt(dx+dy);
//            distance=dx+dy;
//            return distance;
        }

        /**
         * Computes and returns the total absolute distance (shortest path) traveled in pixels since the birth of this cluster
         * @return distance in pixels since birth of cluster
         */
        public float getDistanceFromBirth(){
            double dx=location.x-birthLocation.x;
            double dy=location.y-birthLocation.y;
            return (float)Math.sqrt(dx*dx+dy*dy);
        }

        /** @return signed distance in Y from birth */
        public float getDistanceYFromBirth(){
            return location.y-birthLocation.y;
        }

        /** @return signed distance in X from birth */
        public float getDistanceXFromBirth(){
            return location.x-birthLocation.x;
        }

        /** @return the absolute size of the cluster after perspective correction, i.e., a large cluster at the bottom
         * of the scene is the same absolute size as a smaller cluster higher up in the scene.
         */
        public float getRadiusCorrectedForPerspective(){
            float scale=1/getPerspectiveScaleFactor(location);
            return radius*scale;
        }

        public final float getRadius(){
            return radius;
        }

        /** the radius of a cluster is the distance in pixels from the cluster center that is the putative model size.
         * If highwayPerspectiveEnabled is true, then the radius is set to a fixed size depending on the defaultClusterRadius and the perspective
         * location of the cluster and r is ignored. The aspect ratio parameters of the cluster are also set.
         * @param r the radius in pixels
         */
        public void setRadius(float r){
            if(!highwayPerspectiveEnabled){
                radius=r;
            }else{
                radius=defaultClusterRadius*getPerspectiveScaleFactor(location);
            }
            radiusX=radius/aspectRatio;
            radiusY=radius*aspectRatio;
        }

        final public Point2D.Float getLocation(){
            return location;
        }

        public void setLocation(Point2D.Float l){
            this.location=l;
        }

        /** @return true if cluster has enough support */
        final public boolean isVisible(){
            if(hasObtainedSupport){
                return true;
            }
            boolean ret=true;
            if(numEvents<getThresholdEventsForVisibleCluster()){
                ret=false;
            }
            if(pathsEnabled){
                double speed=Math.sqrt(velocity.x*velocity.x+velocity.y*velocity.y)*1e6/AEConstants.TICK_DEFAULT_US; // speed is in pixels/sec
                if(speed<thresholdVelocityForVisibleCluster){
                    ret=false;
                }
            }
            hasObtainedSupport=ret;
            return ret;
        }

        /** @return lifetime of cluster in timestamp ticks */
        final public int getLifetime(){
            return lastTimestamp-firstTimestamp;
        }

        /** Updates path (historical) information for this cluster, including cluster velocity. */
        final public void updatePath(EventPacket<?> in){
            if(!pathsEnabled){
                return;
            }
            path.add(new PathPoint(location.x,location.y,in.getLastTimestamp(),numEvents-previousNumEvents));
            previousNumEvents=numEvents;
            if(path.size()>getPathLength()){
                path.remove(path.get(0));
            }
            updateVelocity();
        }

        private void updateVelocity(){
            velocityFitter.update();
            if(velocityFitter.valid){
                velocity.x=velocityFitter.getXVelocity();
                velocity.y=velocityFitter.getYVelocity();
                velocityPPS.x=velocity.x*VELPPS_SCALING;
                velocityPPS.y=velocity.y*VELPPS_SCALING;
                velocityValid=true;
            }else{
                velocityValid=false;
            }
//            // update velocity of cluster using last two path points
//            if(path.size()>1){
//                PathPoint c1=path.get(path.size()-2);
//                PathPoint c2=path.get(path.size()-1);
//             int dt=c2.t-c1.t;
//                if(dt>MIN_DT_FOR_VELOCITY_UPDATE){
//                    float vx=(c2.x-c1.x)/dt;
//                    float vy=(c2.y-c1.y)/dt;
//                    velocity.x=vxFilter.filter(vx,lastTimestamp);
//                    velocity.y=vyFilter.filter(vy,lastTimestamp);
////                    float m1=1-velocityMixingFactor;
////                    velocity.x=m1*velocity.x+velocityMixingFactor*vx;
////                    velocity.y=m1*velocity.y+velocityMixingFactor*vy;
//                    velocityPPS.x=velocity.x*VELPPS_SCALING;
//                    velocityPPS.y=velocity.y*VELPPS_SCALING;
//                }
//            }
        }

        public String toString(){
            return String.format("Cluster #%d with %d events near x,y=%d,%d of absRadius=%.1f, visible=%s",
                    getClusterNumber(),numEvents,
                    (int)location.x,
                    (int)location.y,
                    getRadiusCorrectedForPerspective(),
                    isVisible());
        }

        public ArrayList<PathPoint> getPath(){
            return path;
        }

        public Color getColor(){
            return color;
        }

        public void setColor(Color color){
            this.color=color;
        }

        /** @return averaged velocity of cluster in pixels per second. The velocity is instantaneously
         * computed from the movement of the cluster caused by the last event, then this velocity is mixed
         * with the the old velocity by the mixing factor. Thus the mixing factor is appplied twice: once for moving
         * the cluster and again for changing the velocity.
         */
        public Point2D.Float getVelocityPPS(){
            return velocityPPS;
        }

        /** @return average (mixed by {@link #mixingFactor}) distance from events to cluster center
         */
        public float getAverageEventDistance(){
            return averageEventDistance;
        }

        /** @see #getAverageEventDistance */
        public void setAverageEventDistance(float averageEventDistance){
            this.averageEventDistance=averageEventDistance;
        }

        /** Computes the size of the cluster based on average event distance and adjusted for perpective scaling.
         * A large cluster at botton of screen is the same size as a smaller cluster closer to horizon
         * @return size of cluster in pizels
         */
        public float getMeasuredSizeCorrectedByPerspective(){
            float scale=getPerspectiveScaleFactor(location);
            if(scale<=0){
                return averageEventDistance;
            }
            return averageEventDistance/scale;
        }

        /** Sets color according to measured cluster size */
        public void setColorAccordingToSize(){
            float s=getMeasuredSizeCorrectedByPerspective();
            float hue=2*s/chip.getMaxSize();
            if(hue>1){
                hue=1;
            }
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
        }

        /** Sets color according to age of cluster */
        public void setColorAccordingToAge(){
            float brightness=(float)Math.max(0f,Math.min(1f,getLifetime()/fullbrightnessLifetime));
            Color color=Color.getHSBColor(.5f,1f,brightness);
            setColor(color);
        }

        public void setColorAccordingToClass(){
            float s=getMeasuredSizeCorrectedByPerspective();
            float hue=0.5f;
            if(s>getClassifierThreshold()){
                hue=.3f;
            }else{
                hue=.8f;
            }
            Color c=Color.getHSBColor(hue,1f,1f);
            setColor(c);
        }

        public void setColorAutomatically(){
            if(isColorClustersDifferentlyEnabled()){
                // color is set on object creation, don't change it
            }else if(!isClassifierEnabled()){
                setColorAccordingToSize(); // sets color according to measured cluster size, corrected by perspective, if this is enabled
            // setColorAccordingToAge(); // sets color according to how long the cluster has existed
            }else{ // classifier enabled
                setColorAccordingToClass();
            }
        }

        public int getClusterNumber(){
            return clusterNumber;
        }

        public void setClusterNumber(int clusterNumber){
            this.clusterNumber=clusterNumber;
        }

        /** @return average ISI for this cluster in timestamp ticks. Average is computed using cluster location mising factor.
         */
        public float getAvgISI(){
            return avgISI;
        }

        public void setAvgISI(float avgISI){
            this.avgISI=avgISI;
        }

        /** @return average event rate in spikes per timestamp tick. Average is computed using location mixing factor. Note that this measure
         * emphasizes the high spike rates because a few events in rapid succession can rapidly push up the average rate.
         */
        public float getAvgEventRate(){
            return avgEventRate;
        }

        public void setAvgEventRate(float avgEventRate){
            this.avgEventRate=avgEventRate;
        }

        public float getAspectRatio(){
            return aspectRatio;
        }

        public void setAspectRatio(float aspectRatio){
            this.aspectRatio=aspectRatio;
//            float radiusX=radius/aspectRatio, radiusY=radius*aspectRatio;
        }

        public float getAngle(){
            return angle;
        }

        public void setAngle(float angle){
            this.angle=angle;
        }
        /**
         * Does a moving or rolling linear regression (a linear fit) on updated PathPoint data.
         * The new data point replaces the oldest data point. Summary statistics holds the rollling values
         * and are updated by subtracting the oldest point and adding the newest one.
         * From <a href="http://en.wikipedia.org/wiki/Ordinary_least_squares#Summarizing_the_data">Wikipedia article on Ordinary least squares</a>.
         *<p>
        If velocity cannot be estimated (e.g. due to only 2 identical points) it is not updated.
         * @author tobi
         */
        private class RollingVelocityFitter{
            private static final int LENGTH_DEFAULT=5;
            private int length=LENGTH_DEFAULT;
            private float st=0,  sx=0,  sy=0,  stt=0,  sxt=0,  syt=0; // summary stats
            private ArrayList<PathPoint> points;
            private float xVelocity=0,  yVelocity=0;
            private boolean valid=false;
            private int nPoints=0;

            /** Creates a new instance of RollingLinearRegression */
            public RollingVelocityFitter(ArrayList<PathPoint> points,int length){
                this.points=points;
                this.length=length;
            }

            /**
             * Updates estimated velocity based on last point in path. If velocity cannot be estimated
            it is not updated.
             */
            private synchronized void update(){
                nPoints++;
                int n=points.size();
                if(n<1){
                    return;
                }
                PathPoint p=points.get(points.size()-1); // take last point
                if(p.getNEvents()==0){
                    return;
                }
                if(n>length){
                    removeOldestPoint(); // discard data beyond range length
                }
                n=n>length?length:n;  // n grows to max length
                float t=p.t-firstTimestamp; // t is time since cluster formed, limits absolute t for numerics
                st+=t;
                sx+=p.x;
                sy+=p.y;
                stt+=t*t;
                sxt+=p.x*t;
                syt+=p.y*t;
//                if(n<length) return; // don't estimate velocity until we have all necessary points, results very noisy and send cluster off to infinity very often, would give NaN
                float den=(n*stt-st*st);
                if(den!=0){
                    valid=true;
                    xVelocity=(n*sxt-st*sx)/den;
                    yVelocity=(n*syt-st*sy)/den;
                }else{
                    valid=false;
                }
            }

            private void removeOldestPoint(){
                // takes away from summary states the oldest point
                PathPoint p=points.get(points.size()-length-1);
                // if points has 5 points (0-4), length=3, then remove points(5-3-1)=points(1) leaving 2-4 which is correct
                float t=p.t-firstTimestamp;
                st-=t;
                sx-=p.x;
                sy-=p.y;
                stt-=t*t;
                sxt-=p.x*t;
                syt-=p.y*t;
            }

            int getLength(){
                return length;
            }

            /** Sets the window length.  Clears the accumulated data.
             * @param length the number of points to fit
             * @see #LENGTH_DEFAULT
             */
            synchronized void setLength(int length){
                this.length=length;
            }

            public float getXVelocity(){
                return xVelocity;
            }

            public float getYVelocity(){
                return yVelocity;
            }

            /** Returns true if the last estimate resulted in a valid measurement (false when e.g. there are only two identical measurements)
             */
            public boolean isValid(){
                return valid;
            }

            public void setValid(boolean valid){
                this.valid=valid;
            }
        } // rolling velocity fitter

        public Point2D.Float getBirthLocation(){
            return birthLocation;
        }

        public void setBirthLocation(Point2D.Float birthLocation){
            this.birthLocation=birthLocation;
        }

        /** This flog is set true after a velocity has been computed for the cluster. This may take several packets.

        @return true if valid.
         */
        public boolean isVelocityValid(){
            return velocityValid;
        }

        public void setVelocityValid(boolean velocityValid){
            this.velocityValid=velocityValid;
        }
    } // Cluster

    public java.util.List<RectangularClusterTracker.Cluster> getClusters(){
        return this.clusters;
    }

    private LinkedList<RectangularClusterTracker.Cluster> getPruneList(){
        return this.pruneList;
    }
    protected static final float fullbrightnessLifetime=1000000;
    protected Random random=new Random();

    private final void drawCluster(final Cluster c,float[][][] fr){
        int x=(int)c.getLocation().x;
        int y=(int)c.getLocation().y;


        int sy=(int)c.getRadius(); // sx sy are (half) size of rectangle
        int sx=sy;
        int ix, iy;
        int mn, mx;

        if(isColorClustersDifferentlyEnabled()){
        }else{
            c.setColorAccordingToSize();
        }

        Color color=c.getColor();
        if(true){ // draw boxes
            iy=y-sy;    // line under center
            mn=x-sx;
            mx=x+sx;
            for(ix=mn;ix<=mx;ix++){
                colorPixel(ix,iy,fr,clusterColorChannel,color);
            }
            iy=y+sy;    // line over center
            for(ix=mn;ix<=mx;ix++){
                colorPixel(ix,iy,fr,clusterColorChannel,color);
            }
            ix=x-sx;        // line to left
            mn=y-sy;
            mx=y+sy;
            for(iy=mn;iy<=mx;iy++){
                colorPixel(ix,iy,fr,clusterColorChannel,color);
            }
            ix=x+sx;    // to right
            for(iy=mn;iy<=mx;iy++){
                colorPixel(ix,iy,fr,clusterColorChannel,color);
            }
        }else{ // draw diamond reflecting manhatten distance measure doesn't look very nice because not antialiased at all
            iy=y-sy;    // line up right from bot
            ix=x;
            mx=x+sx;
            while(ix<mx){
                colorPixel(ix++,iy++,fr,clusterColorChannel,color);
            }
            mx=x+sx;
            ix=x;
            iy=y+sy;    // line down right from top
            while(ix<mx){
                colorPixel(ix++,iy--,fr,clusterColorChannel,color);
            }
            ix=x;        // line from top down left
            iy=y+sy;
            while(iy>=y){
                colorPixel(ix--,iy--,fr,clusterColorChannel,color);
            }
            ix=x;
            iy=y-sy;
            while(iy<y){
                colorPixel(ix--,iy++,fr,clusterColorChannel,color);
            }
        }

        ArrayList<Cluster.PathPoint> points=c.getPath();
        for(Point2D.Float p:points){
            colorPixel(Math.round(p.x),Math.round(p.y),fr,clusterColorChannel,color);
        }

    }
    private static final int clusterColorChannel=2;

    /** @param x x location of pixel
     *@param y y location
     *@param fr the frame data
     *@param channel the RGB channel number 0-2
     *@param brightness the brightness 0-1
     */
    private final void colorPixel(final int x,final int y,final float[][][] fr,int channel,Color color){
        if(y<0||y>fr.length-1||x<0||x>fr[0].length-1){
            return;
        }
        float[] rgb=color.getRGBColorComponents(null);
        float[] f=fr[y][x];
        for(int i=0;i<3;i++){
            f[i]=rgb[i];
        }
//        fr[y][x][channel]=brightness;
////        if(brightness<1){
//        for(int i=0;i<3;i++){
//            if(i!=channel) fr[y][x][i]=0;
//        }
////        }
    }

    /** lifetime of cluster in ms without support */
    public final int getClusterLifetimeWithoutSupportUs(){
        return clusterLifetimeWithoutSupportUs;
    }

    /** lifetime of cluster in ms without support */
    public void setClusterLifetimeWithoutSupportUs(final int clusterLifetimeWithoutSupport){
        this.clusterLifetimeWithoutSupportUs=clusterLifetimeWithoutSupport;
        getPrefs().putInt("RectangularClusterTracker.clusterLifetimeWithoutSupport",clusterLifetimeWithoutSupport);
    }

    /** max distance from cluster to event as fraction of size of array */
    public final float getClusterSize(){
        return clusterSize;
    }

    /** sets max distance from cluster center to event as fraction of maximum size of chip pixel array.
     * e.g. clusterSize=0.5 and 128x64 array means cluster has radius of 0.5*128=64 pixels.
     *
     * @param clusterSize
     */
    public void setClusterSize(float clusterSize){
        if(clusterSize>1f){
            clusterSize=1f;
        }
        if(clusterSize<0){
            clusterSize=0;
        }
        defaultClusterRadius=(int)Math.max(chip.getSizeX(),chip.getSizeY())*clusterSize;
        this.clusterSize=clusterSize;
        for(Cluster c:clusters){
            c.setRadius(defaultClusterRadius);
        }
        getPrefs().putFloat("RectangularClusterTracker.clusterSize",clusterSize);
    }
    private int maxNumClusters=getPrefs().getInt("RectangularClusterTracker.maxNumClusters",10);


    {
        setPropertyTooltip("maxNumClusters","Sets the maximum potential number of clusters");
    }

    /** max number of clusters */
    public final int getMaxNumClusters(){
        return maxNumClusters;
    }

    /** max number of clusters */
    public void setMaxNumClusters(final int maxNumClusters){
        this.maxNumClusters=maxNumClusters;
        getPrefs().putInt("RectangularClusterTracker.maxNumClusters",maxNumClusters);
    }

//    /** number of events to store for a cluster */
//    public int getNumEventsStoredInCluster() {
//        return prefs.getInt("RectangularClusterTracker.numEventsStoredInCluster",10);
//    }
//
//    /** number of events to store for a cluster */
//    public void setNumEventsStoredInCluster(final int numEventsStoredInCluster) {
//        prefs.putInt("RectangularClusterTracker.numEventsStoredInCluster", numEventsStoredInCluster);
//    }
    /** number of events to make a potential cluster visible */
    public final int getThresholdEventsForVisibleCluster(){
        return thresholdEventsForVisibleCluster;
    }

    /** number of events to make a potential cluster visible */
    public void setThresholdEventsForVisibleCluster(final int thresholdEventsForVisibleCluster){
        this.thresholdEventsForVisibleCluster=thresholdEventsForVisibleCluster;
        getPrefs().putInt("RectangularClusterTracker.thresholdEventsForVisibleCluster",thresholdEventsForVisibleCluster);
    }

    public Object getFilterState(){
        return null;
    }

    private boolean isGeneratingFilter(){
        return false;
    }

    synchronized public void resetFilter(){
        clusters.clear();
        if(isOpticalGyroEnabled()){
            opticalGyroFilters.x.setInternalValue(chip.getSizeX()/2);
            opticalGyroFilters.y.setInternalValue(chip.getSizeY()/2);
        }
    }

    public EventPacket filterPacket(EventPacket in){
        EventPacket out;
        if(in==null){
            return null;
        }
        if(!filterEnabled){
            return in;
        }
        if(enclosedFilter!=null){
            out=enclosedFilter.filterPacket(in);
            track(out);
            return out;
        }else{
            track(in);
            return in;
        }
    }

    public boolean isHighwayPerspectiveEnabled(){
        return highwayPerspectiveEnabled;
    }

    public void setHighwayPerspectiveEnabled(boolean highwayPerspectiveEnabled){
        this.highwayPerspectiveEnabled=highwayPerspectiveEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.highwayPerspectiveEnabled",highwayPerspectiveEnabled);
    }

    public float getMixingFactor(){
        return mixingFactor;
    }

    public void setMixingFactor(float mixingFactor){
        if(mixingFactor<0){
            mixingFactor=0;
        }
        if(mixingFactor>1){
            mixingFactor=1f;
        }
        this.mixingFactor=mixingFactor;
        getPrefs().putFloat("RectangularClusterTracker.mixingFactor",mixingFactor);
    }

    /** Implemeting getMin and getMax methods constucts a slider control for the mixing factor in the FilterPanel.
     *
     * @return 0
     */
    public float getMinMixingFactor(){
        return 0;
    }

    /**
     *
     * @return 1
     */
    public float getMaxMixingFactor(){
        return 1;
    }

    /** @see #setSurround */
    public float getSurround(){
        return surround;
    }

    /** sets scale factor of radius that events outside the cluster size can affect the size of the cluster if
     * {@link #setDynamicSizeEnabled scaling} is enabled.
     * @param surround the scale factor, constrained >1 by setter. radius is multiplied by this to determine if event is within surround.
     */
    public void setSurround(float surround){
        if(surround<1){
            surround=1;
        }
        this.surround=surround;
        getPrefs().putFloat("RectangularClusterTracker.surround",surround);
    }

    /** @see #setPathsEnabled
     */
    public boolean isPathsEnabled(){
        return pathsEnabled;
    }

    /** @param pathsEnabled true to show the history of the cluster locations on each packet */
    public void setPathsEnabled(boolean pathsEnabled){
        this.pathsEnabled=pathsEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.pathsEnabled",pathsEnabled);
    }

    /** @see #setDynamicSizeEnabled
     */
    public boolean getDynamicSizeEnabled(){
        return dynamicSizeEnabled;
    }

    /**
     * Enables cluster size scaling. The clusters are dynamically resized by the distances of the events from the cluster center. If most events
     * are far from the cluster then the cluster size is increased, but if most events are close to the cluster center than the cluster size is
     * decreased. The size change for each event comes from mixing the old size with a the event distance from the center using the mixing factor.
     * @param dynamicSizeEnabled true to enable scaling of cluster size
     */
    public void setDynamicSizeEnabled(boolean dynamicSizeEnabled){
        this.dynamicSizeEnabled=dynamicSizeEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.dynamicSizeEnabled",dynamicSizeEnabled);
    }

    /**@see #setColorClustersDifferentlyEnabled */
    public boolean isColorClustersDifferentlyEnabled(){
        return colorClustersDifferentlyEnabled;
    }

    /** @param colorClustersDifferentlyEnabled true to color each cluster a different color. false to color each cluster
     * by its age
     */
    public void setColorClustersDifferentlyEnabled(boolean colorClustersDifferentlyEnabled){
        this.colorClustersDifferentlyEnabled=colorClustersDifferentlyEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.colorClustersDifferentlyEnabled",colorClustersDifferentlyEnabled);
    }

    public void update(Observable o,Object arg){
        initFilter();
    }

    public boolean isUseOnePolarityOnlyEnabled(){
        return useOnePolarityOnlyEnabled;
    }

    public void setUseOnePolarityOnlyEnabled(boolean useOnePolarityOnlyEnabled){
        this.useOnePolarityOnlyEnabled=useOnePolarityOnlyEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.useOnePolarityOnlyEnabled",useOnePolarityOnlyEnabled);
    }

    public boolean isUseOffPolarityOnlyEnabled(){
        return useOffPolarityOnlyEnabled;
    }

    public void setUseOffPolarityOnlyEnabled(boolean useOffPolarityOnlyEnabled){
        this.useOffPolarityOnlyEnabled=useOffPolarityOnlyEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.useOffPolarityOnlyEnabled",useOffPolarityOnlyEnabled);
    }

    public void annotate(Graphics2D g){
    }

    protected void drawBox(GL gl,int x,int y,int sx,int sy,float angle){
        final float r2d=(float)(180/Math.PI);
        gl.glPushMatrix();
        gl.glTranslatef(x,y,0);
        gl.glRotatef(angle*r2d,0,0,1);
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(-sx,-sy);
            gl.glVertex2i(+sx,-sy);
            gl.glVertex2i(+sx,+sy);
            gl.glVertex2i(-sx,+sy);
        }
        gl.glEnd();
        if(dynamicAngleEnabled){
            gl.glBegin(GL.GL_LINES);
            {
                gl.glVertex2i(0,0);
                gl.glVertex2i(sx,0);
            }
            gl.glEnd();
        }
        gl.glPopMatrix();
    }

    synchronized public void annotate(GLAutoDrawable drawable){
        if(!isFilterEnabled()){
            return;
        }
        final float BOX_LINE_WIDTH=2f; // in chip
        final float PATH_LINE_WIDTH=.5f;
        final float VEL_LINE_WIDTH=2f;
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in RectangularClusterTracker.annotate");
            return;
        }
        float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
            {
                for(Cluster c:clusters){
                    if(showAllClusters||c.isVisible()){
                        int x=(int)c.getLocation().x;
                        int y=(int)c.getLocation().y;


                        int sy=(int)c.radiusY; // sx sy are (half) size of rectangle
                        int sx=(int)c.radiusX;

                        // set color and line width of cluster annotation
                        c.setColorAutomatically();
                        c.getColor().getRGBComponents(rgb);
                        if(c.isVisible()){
                            gl.glColor3fv(rgb,0);
                            gl.glLineWidth(BOX_LINE_WIDTH);
                        }else{
                            gl.glColor3f(.3f,.3f,.3f);
                            gl.glLineWidth(.5f);
                        }

                        // draw cluster rectangle
                        drawBox(gl,x,y,sx,sy,c.getAngle());

                        // draw path points
                        gl.glLineWidth(PATH_LINE_WIDTH);
                        gl.glBegin(GL.GL_LINE_STRIP);
                        {
                            ArrayList<Cluster.PathPoint> points=c.getPath();
                            for(Point2D.Float p:points){
                                gl.glVertex2f(p.x,p.y);
                            }
                        }
                        gl.glEnd();

                        // now draw velocity vector
                        if(useVelocity){
                            gl.glLineWidth(VEL_LINE_WIDTH);
                            gl.glBegin(GL.GL_LINES);
                            {
                                gl.glVertex2i(x,y);
                                gl.glVertex2f(x+c.velocity.x*VELOCITY_VECTOR_SCALING,y+c.velocity.y*VELOCITY_VECTOR_SCALING);
                            }
                            gl.glEnd();
                        }
                    // text annoations on clusters, setup
//                        int font=GLUT.BITMAP_HELVETICA_12;
//                        gl.glColor3f(1, 1, 1);
//                        gl.glRasterPos3f(c.location.x, c.location.y, 0);

                    // draw radius text
//                            chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.1f", c.getRadiusCorrectedForPerspective()));

                    // annotate with angle (debug)
//                        chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0fdeg", c.angle*180/Math.PI));

                    // annotate the cluster with the event rate computed as 1/(avg ISI) in keps
//                        float keps=c.getAvgEventRate()/(AEConstants.TICK_DEFAULT_US)*1e3f;
//                        chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0fkeps", keps ));

                    // annotate the cluster with the velocity in pps
//                        Point2D.Float velpps=c.getVelocityPPS();
//                        chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0f,%.0f pps", velpps.x,velpps.y ));
                    }
                }
            }
        }catch(java.util.ConcurrentModificationException e){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
        if(isOpticalGyroEnabled()){
            gl.glLineWidth(6f);
            gl.glColor3f(1,0,0);
            gl.glBegin(GL.GL_LINES);
            float x=opticalGyroFilters.x.getValue(), y=opticalGyroFilters.y.getValue();
            gl.glVertex2f(x-3,y);
            gl.glVertex2f(x+3,y);
            gl.glVertex2f(x,y-3);
            gl.glVertex2f(x,y+3);
            gl.glEnd();
        }
    }

//    void drawGLCluster(int x1, int y1, int x2, int y2)
    /** annotate the rendered retina frame to show locations of clusters */
    synchronized public void annotate(float[][][] frame){
        if(!isFilterEnabled()){
            return;
        }
        // disable for now TODO
        if(chip.getCanvas().isOpenGLEnabled()){
            return; // done by open gl annotator
        }
        for(Cluster c:clusters){
            if(c.isVisible()){
                drawCluster(c,frame);
            }
        }
    }

    /** Returns the current location of the optical gyro filters

     @return a Point2D.Flaot with x,y, values that show the present position of the gyro output. Returns null if the optical gyro is disabled.
     @see #isOpticalGyroEnabled
     */
    public Point2D.Float getOpticalGyroValue(){
     if (!isOpticalGyroEnabled()) return null;
        Point2D.Float p=new Point2D.Float(opticalGyroFilters.x.getValue(), opticalGyroFilters.y.getValue());
        return p;
    }

    public boolean isGrowMergedSizeEnabled(){
        return growMergedSizeEnabled;
    }

    /** Flags whether to grow the clusters when two clusters are merged, or to take the new size as the size of the older cluster.

     @param growMergedSizeEnabled true to grow the cluster size, false to use the older cluster's size.
     */
    public void setGrowMergedSizeEnabled(boolean growMergedSizeEnabled){
        this.growMergedSizeEnabled=growMergedSizeEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.growMergedSizeEnabled",growMergedSizeEnabled);
    }

//    public float getVelocityMixingFactor() {
//        return velocityMixingFactor;
//    }
//
//    public void setVelocityMixingFactor(float velocityMixingFactor) {
//        if(velocityMixingFactor<0) velocityMixingFactor=0; if(velocityMixingFactor>1) velocityMixingFactor=1f;
//        this.velocityMixingFactor = velocityMixingFactor;
//        getPrefs().putFloat("RectangularClusterTracker.velocityMixingFactor",velocityMixingFactor);
//    }
    public void setUseVelocity(boolean useVelocity){
        this.useVelocity=useVelocity;
        getPrefs().putBoolean("RectangularClusterTracker.useVelocity",useVelocity);
    }

    public boolean isUseVelocity(){
        return useVelocity;
    }

    public synchronized boolean isLogDataEnabled(){
        return logDataEnabled;
    }

    public synchronized void setLogDataEnabled(boolean logDataEnabled){
        this.logDataEnabled=logDataEnabled;
        if(!logDataEnabled){
            logStream.flush();
            logStream.close();
            logStream=null;
        }else{
            try{
                logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File("RectangularClusterTrackerLog.txt"))));
                logStream.println("# clusterNumber lasttimestamp x y avergeEventDistance");
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public float getAspectRatio(){
        return aspectRatio;
    }

    public void setAspectRatio(float aspectRatio){
        if(aspectRatio<0){
            aspectRatio=0;
        }else if(aspectRatio>4){
            aspectRatio=4;
        }
        this.aspectRatio=aspectRatio;
        getPrefs().putFloat("RectangularClusterTracker.aspectRatio",aspectRatio);

    }

    public boolean isClassifierEnabled(){
        return classifierEnabled;
    }

    /** Sets whether classifier is enabled.
     * @param classifierEnabled true to enable classifier
     */
    public void setClassifierEnabled(boolean classifierEnabled){
        this.classifierEnabled=classifierEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.classifierEnabled",classifierEnabled);
    }

    public float getClassifierThreshold(){
        return classifierThreshold;
    }

    public void setClassifierThreshold(float classifierThreshold){
        this.classifierThreshold=classifierThreshold;
        getPrefs().putFloat("RectangularClusterTracker.classifierThreshold",classifierThreshold);
    }

    public boolean isShowAllClusters(){
        return showAllClusters;
    }

    /**Sets annotation visibility of clusters that are not "visible"
     * @param showAllClusters true to show all clusters even if there are not "visible"
     */
    public void setShowAllClusters(boolean showAllClusters){
        this.showAllClusters=showAllClusters;
        getPrefs().putBoolean("RectangularClusterTracker.showAllClusters",showAllClusters);
    }

    public boolean isDynamicAspectRatioEnabled(){
        return dynamicAspectRatioEnabled;
    }

    public void setDynamicAspectRatioEnabled(boolean dynamicAspectRatioEnabled){
        this.dynamicAspectRatioEnabled=dynamicAspectRatioEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.dynamicAspectRatioEnabled",dynamicAspectRatioEnabled);
    }

    public boolean isUseNearestCluster(){
        return useNearestCluster;
    }

    public void setUseNearestCluster(boolean useNearestCluster){
        this.useNearestCluster=useNearestCluster;
        getPrefs().putBoolean("RectangularClusterTracker.useNearestCluster",useNearestCluster);
    }

    public int getPredictiveVelocityFactor(){
        return predictiveVelocityFactor;
    }

    public void setPredictiveVelocityFactor(int predictiveVelocityFactor){
        this.predictiveVelocityFactor=predictiveVelocityFactor;
    }

    public boolean isClusterLifetimeIncreasesWithAge(){
        return clusterLifetimeIncreasesWithAge;
    }

    /**
     * If true, cluster lifetime withtout support increases proportional to the age of the cluster relative to the clusterLifetimeWithoutSupportUs time
     */
    synchronized public void setClusterLifetimeIncreasesWithAge(boolean clusterLifetimeIncreasesWithAge){
        this.clusterLifetimeIncreasesWithAge=clusterLifetimeIncreasesWithAge;
        getPrefs().putBoolean("RectangularClusterTracker.clusterLifetimeIncreasesWithAge",clusterLifetimeIncreasesWithAge);

    }

    public float getThresholdVelocityForVisibleCluster(){
        return thresholdVelocityForVisibleCluster;
    }

    /** A cluster must have at least this velocity magnitude to become visible
     * @param thresholdVelocityForVisibleCluster speed in pixels/second
     */
    synchronized public void setThresholdVelocityForVisibleCluster(float thresholdVelocityForVisibleCluster){
        if(thresholdVelocityForVisibleCluster<0){
            thresholdVelocityForVisibleCluster=0;
        }
        this.thresholdVelocityForVisibleCluster=thresholdVelocityForVisibleCluster;
        getPrefs().putFloat("RectangularClusterTracker.thresholdVelocityForVisibleCluster",thresholdVelocityForVisibleCluster);
    }

    /**
     * Get the value of opticalGyroEnabled
     *
     * @return the value of opticalGyroEnabled
     */
    public boolean isOpticalGyroEnabled(){
        return opticalGyroEnabled;
    }

    /**
     * Set the value of opticalGyroEnabled
     *
     * @param opticalGyroEnabled new value of opticalGyroEnabled
     */
    public void setOpticalGyroEnabled(boolean opticalGyroEnabled){
        this.opticalGyroEnabled=opticalGyroEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.opticalGyroEnabled",opticalGyroEnabled);
    }

    public int getPathLength(){
        return pathLength;
    }

    synchronized public void setPathLength(int pathLength){
        if(pathLength<2){
            pathLength=2;
        }
        this.pathLength=pathLength;
        getPrefs().putInt("RectangularClusterTracker.pathLength",pathLength);

    }

    public boolean isDynamicAngleEnabled(){
        return dynamicAngleEnabled;
    }

    /** Setting dynamicAngleEnabled true enables variable-angle clusters. */
    synchronized public void setDynamicAngleEnabled(boolean dynamicAngleEnabled){
        this.dynamicAngleEnabled=dynamicAngleEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.dynamicAngleEnabled",dynamicAngleEnabled);
    }
//
//    public float getVelocityTauMs() {
//        return velocityTauMs;
//    }
//
//    synchronized public void setVelocityTauMs(float velocityTauMs) {
//        this.velocityTauMs = velocityTauMs;
//        getPrefs().putFloat("RectangularClusterTracker.velocityTauMs",velocityTauMs);
////        for(Cluster c:clusters){
////            c.vxFilter.setTauMs(velocityTauMs);
////            c.vyFilter.setTauMs(velocityTauMs);
////        }
//
//    }

    public int getVelocityPoints(){
        return velocityPoints;
    }

    public void setVelocityPoints(int velocityPoints){
        if(velocityPoints>=pathLength){
            velocityPoints=pathLength;
        }
        this.velocityPoints=velocityPoints;
        getPrefs().putInt("RectangularClusterTracker.velocityPoints",velocityPoints);

    }
}
