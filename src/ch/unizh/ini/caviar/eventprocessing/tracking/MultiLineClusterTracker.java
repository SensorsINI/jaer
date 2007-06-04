/*
 * MultiLineClusterTracker.java
 *
 * Created on December 5, 2005, 3:49 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.eventprocessing.tracking;
import ch.unizh.ini.caviar.aemonitor.AEConstants;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.graphics.*;
import com.sun.opengl.util.*;
import java.awt.*;
//import ch.unizh.ini.caviar.util.PreferencesEditor;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

/**
 * Tracks multiple lines in the scene using a cluster based method based on pairs of recent events.
 * The event pairs come from a buffer formed from recent events. Each pair defines a line with polar and angle parameters.
 * Lines are tracked using polar (rho) and angle (theta) parameters in a space of rho/theta, in analogy with the MultiLineClusterTracker tracking
 * of rectangular objects in retinal coordinate space.
 *
 * @author tobi
 */
public class MultiLineClusterTracker extends EventFilter2D implements FrameAnnotater, Observer {
    private static Preferences prefs=Preferences.userNodeForPackage(MultiLineClusterTracker.class);
    
    private java.util.List<LineCluster> clusters=new LinkedList<LineCluster>();
    
    private int eventBufferLength=prefs.getInt("MultiLineClusterTracker.eventBufferLength",30);
    {setPropertyTooltip("eventBufferLength","Number of past events to form line segments from for clustering");}
    
    private LIFOEventBuffer eventBuffer=new LIFOEventBuffer(eventBufferLength);
    
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
    private int minSegmentLength=prefs.getInt("MultiLineClusterTracker.minSegmentLength",3);
    {setPropertyTooltip("minSegmentLength","minium manhatten length of line segment in pixels");}
    private int maxSegmentLength=prefs.getInt("MultiLineClusterTracker.maxSegmentLength",20);
    {setPropertyTooltip("maxSegmentLength","max manhatten line segment length in pixels");}
    private int maxSegmentDt=prefs.getInt("MultiLineClusterTracker.maxSegmentDt",20000);
    {setPropertyTooltip("maxSegmentDt","maximum dt in ticks for line segment event pair");}
    
    /** scaling can't make cluster bigger or smaller than this ratio to default cluster size */
    public static final float MAX_SCALE_RATIO=2;
    
    
    private float rhoRadius=prefs.getFloat("MultiLineClusterTracker.rhoRadius",.05f);
    {setPropertyTooltip("rhoRadius","radius of line cluster around line in fraction of chip.getMaxSize()");}
    private float thetaRadius=prefs.getFloat("MultiLineClusterTracker.thetaRadius",10);
    {setPropertyTooltip("thetaRadius","radius of line cluster in degrees around center angle");}
    
    private float mixingFactorRho=prefs.getFloat("MultiLineClusterTracker.mixingFactorRho",0.01f); // amount each event moves COM of cluster towards itself
    {setPropertyTooltip("mixingFactor","how much cluster is moved by an event and its distance from the present locatoins");}
    private float mixingFactorTheta=prefs.getFloat("MultiLineClusterTracker.mixingFactorTheta",0.01f); // amount each event moves COM of cluster towards itself
    {setPropertyTooltip("mixingFactorTheta","how much cluster is moved by an event and its distance from the present locatoins");}
    
//    private float velocityMixingFactor=prefs.getFloat("MultiLineClusterTracker.velocityMixingFactor",0.01f); // mixing factor for velocity computation
//    {setPropertyTooltip("velocityMixingFactor","how much cluster velocity estimate is updated by each event");}
    
//    private float surround=prefs.getFloat("MultiLineClusterTracker.surround",2f);
//    {setPropertyTooltip("surround","the radius is expanded by this ratio to define events that pull radius of cluster");}
//    private boolean dynamicSizeEnabled=prefs.getBoolean("MultiLineClusterTracker.dynamicSizeEnabled", false);
//    {setPropertyTooltip("dynamicSizeEnabled","size varies dynamically depending on cluster events");}
//    private boolean dynamicAspectRatioEnabled=prefs.getBoolean("MultiLineClusterTracker.dynamicAspectRatioEnabled",false);
//    {setPropertyTooltip("dynamicAspectRatioEnabled","aspect ratio depends on events as well");}
//    private boolean pathsEnabled=prefs.getBoolean("MultiLineClusterTracker.pathsEnabled", true);
//    {setPropertyTooltip("pathsEnabled","draw paths of clusters over some window");}
    private boolean colorClustersDifferentlyEnabled=prefs.getBoolean("MultiLineClusterTracker.colorClustersDifferentlyEnabled",false);
    {setPropertyTooltip("colorClustersDifferentlyEnabled","each cluster gets assigned a random color, otherwise color indicates ages");}
    private boolean useOnePolarityOnlyEnabled=prefs.getBoolean("MultiLineClusterTracker.useOnePolarityOnlyEnabled",false);
    {setPropertyTooltip("useOnePolarityOnlyEnabled","use only one event polarity");}
    private boolean useOffPolarityOnlyEnabled=prefs.getBoolean("MultiLineClusterTracker.useOffPolarityOnlyEnabled",false);
    {setPropertyTooltip("useOffPolarityOnlyEnabled","use only OFF events, not ON - if useOnePolarityOnlyEnabled");}
    private float aspectRatio=prefs.getFloat("MultiLineClusterTracker.aspectRatio",1f);
    {setPropertyTooltip("aspectRatio","default (or starting) aspect ratio, taller is larger");}
    protected boolean growMergedSizeEnabled=prefs.getBoolean("MultiLineClusterTracker.growMergedSizeEnabled",false);
    {setPropertyTooltip("growMergedSizeEnabled","enabling makes merged clusters take on sum of sizes, otherwise they take on size of older cluster");}
    private boolean showVelocity=prefs.getBoolean("MultiLineClusterTracker.showVelocity",true); // enabling this enables both computation and rendering of cluster velocities
    {setPropertyTooltip("showVelocity","computes and shows cluster velocity");}
    private boolean logDataEnabled=false;
    {setPropertyTooltip("logDataEnabled","writes a cluster log file");}
    private PrintStream logStream=null;
    private boolean showAllClusters=prefs.getBoolean("MultiLineClusterTracker.showAllClusters",false);
    {setPropertyTooltip("showAllClusters","shows all clusters, not just those with sufficient support");}
    private boolean clusterLifetimeIncreasesWithAge=prefs.getBoolean("MultiLineClusterTracker.clusterLifetimeIncreasesWithAge",false);
    {setPropertyTooltip("clusterLifetimeIncreasesWithAge","older clusters can live longer without support, good for jumpy objects");}
    
    private final float VELOCITY_VECTOR_SCALING=1e5f; // to scale rendering of cluster velocity vector
    private int predictiveVelocityFactor=1;// making this M=10, for example, will cause cluster to substantially lead the events, then slow down, speed up, etc.
    {setPropertyTooltip("predictiveVelocityFactor","how much cluster position leads position based on estimated velocity");}
    
    private int thresholdEventsForVisibleCluster=prefs.getInt("MultiLineClusterTracker.thresholdEventsForVisibleCluster",10);
    {setPropertyTooltip("thresholdEventsForVisibleCluster","Cluster needs this many events to be visible");}
    
    private int clusterLifetimeWithoutSupportUs=prefs.getInt("MultiLineClusterTracker.clusterLifetimeWithoutSupport",10000);
    {setPropertyTooltip("clusterLifetimeWithoutSupportUs","Cluster lives this long in ticks (e.g. us) without events before pruning");}
    
    private int maxNumClusters=prefs.getInt("MultiLineClusterTracker.maxNumClusters",10);
    {setPropertyTooltip("maxNumClusters","Sets the maximum potential number of clusters");}
    
    /**
     * Creates a new instance of MultiLineClusterTracker
     * @param chip the chip we are tracking for
     */
    public MultiLineClusterTracker(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        chip.getRenderer().addAnnotator(this); // to draw the clusters
        chip.getCanvas().addAnnotator(this);
        initFilter();
        chip.addObserver(this);
//        prefs.addPreferenceChangeListener(this);
    }
    
    public void initFilter() {
        initDefaults();
    }
    
    private void initDefaults(){
        initDefault("MultiLineClusterTracker.clusterLifetimeWithoutSupport","10000");
        initDefault("MultiLineClusterTracker.maxNumClusters","10");
        initDefault("MultiLineClusterTracker.clusterSize","0.15f");
        initDefault("MultiLineClusterTracker.numEventsStoredInCluster","100");
        initDefault("MultiLineClusterTracker.thresholdEventsForVisibleCluster","30");
        
//        initDefault("MultiLineClusterTracker.","");
    }
    
    private void initDefault(String key, String value){
        if(prefs.get(key,null)==null) prefs.put(key,value);
    }
    
//    ArrayList<LineCluster> pruneList=new ArrayList<LineCluster>(1);
    protected LinkedList<LineCluster> pruneList=new LinkedList<LineCluster>();
    
    // the method that actually does the tracking
    synchronized private void track(EventPacket<BasicEvent> ae){
        int n=ae.getSize();
        if(n==0) return;
        int maxNumClusters=getMaxNumClusters();
        LineSegment seg;
        // for each event, see which cluster it is closest to and add it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
//        for(int i=0;i<n;i++){
        for(BasicEvent ev:ae){
            
            // for each past event, form a line segment and use it to move clusters if the segment is valid for clustering
            for(BasicEvent e:eventBuffer){
                seg=new LineSegment(ev,e);
                if(seg.isValid()){
                    LineCluster closest=null;
                    closest=getNearestCluster(seg);
                    if( closest!=null ){
                        closest.addSegment(seg);
                    }else if(clusters.size()<maxNumClusters){ // start a new cluster
                        LineCluster newCluster=new LineCluster(seg);
                        clusters.add(newCluster);
                    }
                }
            }
            eventBuffer.add(ev); // add the most recent event
            
        }
        // prune out old clusters that don't have support
        pruneList.clear();
        for(LineCluster c:clusters){
            int t0=c.getLastEventTimestamp();
            int t1=ae.getLastTimestamp();
            int timeSinceSupport=t1-t0;
            boolean killOff=false;
            if(clusterLifetimeIncreasesWithAge){
                int age=c.getLifetime();
                int supportTime=clusterLifetimeWithoutSupportUs;
                if(age<clusterLifetimeWithoutSupportUs) supportTime=age;
                if(timeSinceSupport>supportTime) killOff=true;
            }else{
                if(timeSinceSupport>clusterLifetimeWithoutSupportUs) killOff=true;
            }
            if(t0>t1 || killOff || timeSinceSupport<0){
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
        LineCluster c1=null,c2=null;
        do{
            mergePending=false;
            int nc=clusters.size();
            outer:
                for(int i=0;i<nc;i++){
                    c1=clusters.get(i);
                    for(int j=i+1;j<nc;j++){
                        c2=clusters.get(j); // get the other cluster
                        if(c1.distanceToRho(c2)<(c1.radiusRhoPixels+c2.radiusRhoPixels)) { // if distance is less than sum of radii merge them
                            // if cluster is close to another cluster, merge them
                            mergePending=true;
                            break outer; // break out of the outer loop
                        }
                    }
                }
                if(mergePending && c1!=null && c2!=null){
                    pruneList.add(c1);
                    pruneList.add(c2);
                    clusters.remove(c1);
                    clusters.remove(c2);
                    clusters.add(new LineCluster(c1,c2));
                }
        }while(mergePending);
        
        // update all cluster sizes
//        // note that without this following call, clusters maintain their starting size until they are merged with another cluster.
//        if(isHighwayPerspectiveEnabled()){
//            for(LineCluster c:clusters){
//                c.setRadius(defaultClusterRadius);
//            }
//        }
        
        // update paths of clusters
        for(LineCluster c:clusters) c.updatePath();
        
//        if(clusters.size()>beforeMergeCount) throw new RuntimeException("more clusters after merge than before");
        if(isLogDataEnabled() && getNumClusters()>0){
            if(logStream!=null) {
                for(LineCluster c:clusters){
                    if(!c.isVisible()) continue;
                    logStream.println(String.format("%d %d %f %f %f", c.getClusterNumber(), c.lastTimestamp,c.location.x,c.location.y, c.averageEventDistance));
                    if(logStream.checkError()) log.warning("eroror logging data");
                }
            }
        }
    }
    
    public int getNumClusters(){
        return clusters.size();
    }
    
    public String toString(){
        String s="MultiLineClusterTracker with "+clusters.size()+" clusters ";
        return s;
    }
    
    
    class LineSegment{
        BasicEvent a, b;
        double thetaRad, rhoPixels;
        int dx,dy, dt;
        float x,y;
        int timestamp;
        /** Make a new LineSegment with first event a and second (later) event b */
        LineSegment(BasicEvent a, BasicEvent b){
            this.a=a;
            this.b=b;
            this.timestamp=b.timestamp;
            computeRhoTheta();
            computeLocation();
        }
        
        void computeRhoTheta(){
            dt=b.timestamp-a.timestamp;
            dx=b.x-a.x;
            dy=b.y-a.y;
            thetaRad=Math.abs(Math.atan2(dy,dx)); // theta goes from 0 to Pi, 0 and Pi being horizontal lines
            rhoPixels=a.x*Math.cos(thetaRad)+a.y*Math.sin(thetaRad);
        }
        
        double distance2To(LineSegment b){
            double dtheta=(thetaRad-b.thetaRad)/Math.PI/2;
            dtheta*=dtheta;
            double drho=(rhoPixels-b.rhoPixels)/chip.getMaxSize();
            drho*=drho;
            return (dtheta+drho);
        }
        
        public String toString(){
            return String.format("LineSegment rho=%.1f pixels theta=%.0f deg", rhoPixels, thetaRad*180/Math.PI/2);
        }
        
        private boolean isValid() {
            if(dx<minSegmentLength && dy<minSegmentLength) return false;
            if(dx>maxSegmentLength || dy>maxSegmentLength) return false;
            if(dt>maxSegmentDt) return false;
            return true;
        }
        
        private void computeLocation() {
            x=(a.x+b.x)/2;
            y=(a.y+b.y)/2;
        }
    }
    
    /**
     * Method that given segment event, returns closest cluster and distance to it. The actual computation returns the first cluster that is within the
     * minDistance of the event, which reduces the computation at the cost of reduced precision.
     * @param segment the segment event
     * @return closest cluster object (a cluster with a distance - that distance is the distance between the given event and the returned cluster).
     */
    private LineCluster getNearestCluster(LineSegment segment){
        double minDistance=Double.MAX_VALUE;
        LineCluster closest=null;
        double currentDistance;
        for(LineCluster c:clusters){
//            float rRho=c.radiusRhoPixels;
//            float rTheta=c.radiusThetaRad; // this is surround region for purposes of dynamicSize scaling of cluster size or aspect ratio
//            if(dynamicSizeEnabled) {
//                rRho*=surround;
//                rTheta*=surround; // the event is captured even when it is in "invisible surround"
//            }
            if((currentDistance=c.distanceTo(segment))<minDistance){
                minDistance=currentDistance;
                closest=c;
                c.distanceToLastEvent=minDistance;
            }
        }
        return closest;
    }
    
    protected int clusterCounter=0; // keeps track of absolute cluster number
    
    /** Represents a single tracked object */
    public class LineCluster{
        
        /** center or average line location of cluster in pixels */
        public Point2D.Float location=new Point2D.Float(); // location in chip pixels
        
        /** Distance of line from passing origin in pixels */
        public double rhoPixels=0;
        
        /** Angle of line CCW from x axis with 0 being horizontal and Pi/2 being vertical */
        public double thetaRad=0;
        
        /** velocity of cluster in pixels/tick, where tick is timestamp tick (usually microseconds) */
        public Point2D.Float velocity=new Point2D.Float(); // velocity in chip pixels/sec
        
        public float radiusRhoPixels=rhoRadius, radiusThetaRad=thetaRadius;
        
        protected final int MAX_PATH_LENGTH=100;
        protected ArrayList<Point2D.Float> path=new ArrayList<Point2D.Float>(MAX_PATH_LENGTH);
        protected Color color=null;
        
        protected int numEvents;
//        ArrayList<EventXYType> events=new ArrayList<EventXYType>();
        protected int lastTimestamp, firstTimestamp;
        protected float instantaneousEventRate; // in events/tick
        private float avgEventRate = 0;
        protected float instantaneousISI; // ticks/event
        private float avgISI;
        private int clusterNumber; // assigned to be the absolute number of the cluster that has been created
        private double averageEventDistance; // average (mixed) distance of events from cluster center, a measure of actual cluster size
        public double distanceToLastEvent=Float.POSITIVE_INFINITY;
        
        
        public LineCluster(){
            float hue=random.nextFloat();
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
            setClusterNumber(clusterCounter++);
        }
        
        public LineCluster(LineSegment ev){
            this();
            location.x=ev.x;
            location.y=ev.y;
            lastTimestamp=ev.timestamp;
            firstTimestamp=lastTimestamp;
            numEvents=1;
        }
        
        /**
         * Computes a geometrical scale factor based on location of a point relative to the vanishing point.
         * If a pixel has been selected (we ask the renderer) then we compute the perspective from this vanishing point, otherwise
         * it is the top middle pixel.
         * @param p a point with 0,0 at lower left corner
         * @return scale factor, which grows linearly to 1 at botton of scene
         */
        final float getPerspectiveScaleFactor(Point2D.Float p){
            if(!renderer.isPixelSelected()){
                float yfrac=1f-(p.y/chip.getSizeY()); // yfrac grows to 1 at bottom of image
                return yfrac;
            }else{
                // scale is 0 at vanishing point and grows linearly to 1 at max size of chip
                int size=chip.getMaxSize();
                float d=(float)p.distance(renderer.getXsel(),renderer.getYsel());
                float scale=d/size;
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
        public LineCluster(LineCluster one, LineCluster two){
            this();
            // merge locations by just averaging
//            location.x=(one.location.x+two.location.x)/2;
//            location.y=(one.location.y+two.location.y)/2;
            
            LineCluster older=one.firstTimestamp<two.firstTimestamp? one:two;
//            LineCluster older=one.numEvents>two.numEvents? one:two;
            
            // merge locations by average weighted by number of events supporting cluster
            int sumEvents=one.numEvents+two.numEvents;
            location.x=(one.location.x*one.numEvents+two.location.x*two.numEvents)/(sumEvents);
            location.y=(one.location.y*one.numEvents+two.location.y*two.numEvents)/(sumEvents);
            averageEventDistance=( one.averageEventDistance*one.numEvents + two.averageEventDistance*two.numEvents )/sumEvents;
            lastTimestamp=(one.lastTimestamp+two.lastTimestamp)/2;
            numEvents=sumEvents;
            firstTimestamp=older.firstTimestamp; // make lifetime the oldest src cluster
            lastTimestamp=older.lastTimestamp;
            path=older.path;
            velocity.x=older.velocity.x;
            velocity.y=older.velocity.y;
            avgEventRate=older.avgEventRate;
            avgISI=older.avgISI;
            
            setColor(older.getColor());
            
        }
        
        public int getLastEventTimestamp(){
//            EventXYType ev=events.get(events.size()-1);
//            return ev.timestamp;
            return lastTimestamp;
        }
        
        public void addSegment(LineSegment seg){
            
            // save location for computing velocity
            float oldx=location.x, oldy=location.y;
            
            float mRho=mixingFactorRho,m1Rho=1-mRho;;
            float mTheta=mixingFactorTheta,m1Theta=1-mTheta;;
            
            float dt=seg.timestamp-lastTimestamp; // this timestamp may be bogus if it goes backwards in time, we need to check it later
            
            // compute new cluster location by mixing old location with event location by using
            // mixing factors
            
            rhoPixels=mRho*(seg.rhoPixels-rhoPixels)+m1Rho*rhoPixels;
            thetaRad=mTheta*(seg.thetaRad-thetaRad)+m1Theta*thetaRad;
            
//            location.x=(m1Rho*location.x+mRho*seg.x);
//            location.y=(m1Rho*location.y+mRho*seg.y);
            
            
//            if(showVelocity && dt>0){
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
//            }
            
            int prevLastTimestamp=lastTimestamp;
            lastTimestamp=seg.timestamp;
            numEvents++;
            instantaneousISI=lastTimestamp-prevLastTimestamp;
            if(instantaneousISI<=0) instantaneousISI=1;
            avgISI=m1Rho*avgISI+mRho*instantaneousISI;
            instantaneousEventRate=1f/instantaneousISI;
            avgEventRate=m1Rho*avgEventRate+mRho*instantaneousEventRate;
            
            averageEventDistance=m1Rho*averageEventDistance+mRho*distanceToLastEvent;
            
            // if scaling is enabled, now scale the cluster size
//            scale(event);
            
        }
        
        /** computes metric of distance in rho/theta space, normalizing each by appropriate size: rho by chip.getMaxSize() and theta
         *by 2*Math.PI. Returns normalized distance metric which can be used for searching for closest LineCluster to a LineSegment
         **/
        public double distanceMetric(double dRhoPixels, double dThetaRad){
            dRhoPixels=Math.abs(dRhoPixels/chip.getMaxSize());
            dThetaRad=Math.abs(dThetaRad/Math.PI/2);
            return dThetaRad+dRhoPixels;
        }
        
        /** @return distance in x direction of this cluster to the event */
        private double distanceToX(BasicEvent event){
            float distance=Math.abs(event.x-location.x);
            return distance;
        }
        
        /** @return distance in y direction of this cluster to the event */
        private double distanceToY(BasicEvent event){
            float distance=Math.abs(event.y-location.y);
            return distance;
        }
        
        /** @return distance in pixels between line segment and line cluster rho metric */
        private double distanceToRho(LineSegment seg){
            return rhoPixels-seg.rhoPixels;
        }
        
        private double distanceToTheta(LineSegment seg){
            double d=Math.abs(thetaRad-seg.thetaRad);
            if(d>Math.PI/2) d-=Math.PI/2;
            return d;
        }
        
        /** @return distance in pixels between line segment and line cluster rho metric */
        private double distanceToRho(LineCluster c){
            return rhoPixels-c.rhoPixels;
        }
        
        private double distanceToTheta(LineCluster c){
            double d=Math.abs(thetaRad-c.thetaRad);
            if(d>Math.PI/2) d-=Math.PI/2;
            return d;
        }
        
        /** @return distance of this cluster to the other cluster */
        public double distanceTo(LineCluster c){
            double dRho=distanceToRho(c);
            double dTheta=distanceToTheta(c);
            return distanceMetric(dRho,dTheta);
        }
        
        /** @return distance of this cluster to the other cluster */
        public double distanceTo(LineSegment c){
            double dRho=distanceToRho(c);
            double dTheta=distanceToTheta(c);
            return distanceMetric(dRho,dTheta);
        }
        
        public boolean isOverlapping(LineCluster c){
            if(distanceToRho(c)<rhoRadius*2 && distanceToTheta(c)<thetaRadius*2) return true;
            return false;
        }
        
        final public Point2D.Float getLocation() {
            return location;
        }
        public void setLocation(Point2D.Float l){
            this.location = l;
        }
        
        /** @return true if cluster has enough support */
        final public boolean isVisible(){
            boolean ret=true;
            if(numEvents<getThresholdEventsForVisibleCluster()) ret=false;
            return ret;
        }
        
        /** @return lifetime of cluster in timestamp ticks */
        final public int getLifetime(){
            return lastTimestamp-firstTimestamp;
        }
        
        final public void updatePath(){
//            if(!pathsEnabled) return;
//            path.add(new Point2D.Float(location.x,location.y));
//            if(path.size()>MAX_PATH_LENGTH) path.remove(path.get(0));
        }
        
        public String toString(){
            return String.format("Cluster #%d with %d events near x,y=%d,%d visible=%s",
                    getClusterNumber(),                     numEvents,
                    (int)location.x,
                    (int)location.y,
                    isVisible()
                    );
        }
        
        public ArrayList<Point2D.Float> getPath() {
            return path;
        }
        
        public Color getColor() {
            return color;
        }
        
        public void setColor(Color color) {
            this.color = color;
        }
        
        /** @return averaged velocity of cluster in pixels per second. The velocity is instantaneously
         * computed from the movement of the cluster caused by the last event, then this velocity is mixed
         * with the the old velocity by the mixing factor. Thus the mixing factor is appplied twice: once for moving
         * the cluster and again for changing the velocity.
         */
        public Point2D.Float getVelocity() {
            return velocity;
        }
        
        /** @return average (mixed by {@link #mixingFactor}) distance from events to cluster center
         */
        public double getAverageEventDistance() {
            return averageEventDistance;
        }
        
        /** @see #getAverageEventDistance */
        public void setAverageEventDistance(float averageEventDistance) {
            this.averageEventDistance = averageEventDistance;
        }
        
        /** Sets color according to age of cluster */
        public void setColorAccordingToAge(){
            float brightness=(float)Math.max(0f,Math.min(1f,getLifetime()/fullbrightnessLifetime));
            Color color=Color.getHSBColor(.5f,1f,brightness);
            setColor(color);
        }
        
        public void setColorAutomatically() {
//            if(isColorClustersDifferentlyEnabled()){
//                // color is set on object creation, don't change it
//            }else if(!isClassifierEnabled()){
//                setColorAccordingToSize(); // sets color according to measured cluster size, corrected by perspective, if this is enabled
//                // setColorAccordingToAge(); // sets color according to how long the cluster has existed
//            }else{ // classifier enabled
//                setColorAccordingToClass();
//            }
        }
        
        public int getClusterNumber() {
            return clusterNumber;
        }
        
        public void setClusterNumber(int clusterNumber) {
            this.clusterNumber = clusterNumber;
        }
        
        /** @return average ISI for this cluster in timestamp ticks. Average is computed using cluster location mising factor.
         */
        public float getAvgISI() {
            return avgISI;
        }
        
        public void setAvgISI(float avgISI) {
            this.avgISI = avgISI;
        }
        
        /** @return average event rate in spikes per timestamp tick. Average is computed using location mixing factor. Note that this measure
         * emphasizes the high spike rates because a few events in rapid succession can rapidly push up the average rate.
         */
        public float getAvgEventRate() {
            return avgEventRate;
        }
        
        public void setAvgEventRate(float avgEventRate) {
            this.avgEventRate = avgEventRate;
        }
        
        private void draw(GL gl) {
            int x=(int)location.x;
            int y=(int)location.y;
            final int length=chip.getMaxSize()/4;
            
            int sx=(int)(Math.cos(thetaRad)*length);
            int sy=(int)(Math.sin(thetaRad)*length);
            
            // set color and line width of cluster annotation
            setColorAutomatically();
            getColor().getRGBComponents(rgb);
            if(isVisible()){
                gl.glColor3fv(rgb,0);
                gl.glLineWidth(6);
            }else{
                gl.glColor3f(.3f,.3f,.3f);
                gl.glLineWidth(.5f);
            }
            gl.glBegin(GL.GL_LINE_LOOP);
            {
                gl.glVertex2i(x-sx,y-sy);
                gl.glVertex2i(x+sx,y+sy);
            }
            gl.glEnd();
        }
        
    }
    
    public java.util.List<MultiLineClusterTracker.LineCluster> getClusters() {
        return this.clusters;
    }
    
    private LinkedList<MultiLineClusterTracker.LineCluster> getPruneList(){
        return this.pruneList;
    }
    
    
    protected static final float fullbrightnessLifetime=1000000;
    
    
    protected Random random=new Random();
    
    private static final int clusterColorChannel=2;
    
    
    /** lifetime of cluster in ms without support */
    public final int getClusterLifetimeWithoutSupportUs() {
        return clusterLifetimeWithoutSupportUs;
    }
    
    /** lifetime of cluster in ms without support */
    public void setClusterLifetimeWithoutSupportUs(final int clusterLifetimeWithoutSupport) {
        this.clusterLifetimeWithoutSupportUs=clusterLifetimeWithoutSupport;
        prefs.putInt("MultiLineClusterTracker.clusterLifetimeWithoutSupport", clusterLifetimeWithoutSupport);
    }
    
    /** max number of clusters */
    public final int getMaxNumClusters() {
        return maxNumClusters;
    }
    
    /** max number of clusters */
    public void setMaxNumClusters(final int maxNumClusters) {
        this.maxNumClusters=maxNumClusters;
        prefs.putInt("MultiLineClusterTracker.maxNumClusters", maxNumClusters);
    }
    
//    /** number of events to store for a cluster */
//    public int getNumEventsStoredInCluster() {
//        return prefs.getInt("MultiLineClusterTracker.numEventsStoredInCluster",10);
//    }
//
//    /** number of events to store for a cluster */
//    public void setNumEventsStoredInCluster(final int numEventsStoredInCluster) {
//        prefs.putInt("MultiLineClusterTracker.numEventsStoredInCluster", numEventsStoredInCluster);
//    }
    
    
    /** number of events to make a potential cluster visible */
    public final int getThresholdEventsForVisibleCluster() {
        return thresholdEventsForVisibleCluster;
    }
    
    /** number of events to make a potential cluster visible */
    public void setThresholdEventsForVisibleCluster(final int thresholdEventsForVisibleCluster) {
        this.thresholdEventsForVisibleCluster=thresholdEventsForVisibleCluster;
        prefs.putInt("MultiLineClusterTracker.thresholdEventsForVisibleCluster", thresholdEventsForVisibleCluster);
    }
    
    
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }
    
    synchronized public void resetFilter() {
        clusters.clear();
    }
    
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        track(in);
        return in;
    }
    
    public float getMixingFactorRho() {
        return mixingFactorRho;
    }
    
    public void setMixingFactorRho(float mixingFactor) {
        if(mixingFactor<0) mixingFactor=0; if(mixingFactor>1) mixingFactor=1f;
        this.mixingFactorRho = mixingFactor;
        prefs.putFloat("MultiLineClusterTracker.mixingFactorRho",mixingFactor);
    }
    
    public float getMixingFactorTheta() {
        return mixingFactorRho;
    }
    
    public void setMixingFactorTheta(float mixingFactor) {
        if(mixingFactor<0) mixingFactor=0; if(mixingFactor>1) mixingFactor=1f;
        this.mixingFactorRho = mixingFactor;
        prefs.putFloat("MultiLineClusterTracker.mixingFactorTheta",mixingFactor);
    }
    
//    /** @see #setSurround */
//    public float getSurround() {
//        return surround;
//    }
//
//    /** sets scale factor of radius that events outside the cluster size can affect the size of the cluster if
//     * {@link #setDynamicSizeEnabled scaling} is enabled.
//     * @param surround the scale factor, constrained >1 by setter. radius is multiplied by this to determine if event is within surround.
//     */
//    public void setSurround(float surround){
//        if(surround < 1) surround = 1;
//        this.surround = surround;
//        prefs.putFloat("MultiLineClusterTracker.surround",surround);
//    }
    
//    /** @see #setPathsEnabled
//     */
//    public boolean isPathsEnabled() {
//        return pathsEnabled;
//    }
//
//    /** @param pathsEnabled true to show the history of the cluster locations on each packet */
//    public void setPathsEnabled(boolean pathsEnabled) {
//        this.pathsEnabled = pathsEnabled;
//        prefs.putBoolean("MultiLineClusterTracker.pathsEnabled",pathsEnabled);
//    }
    
//    /** @see #setDynamicSizeEnabled
//     */
//    public boolean getDynamicSizeEnabled(){
//        return dynamicSizeEnabled;
//    }
//
//    /**
//     * Enables cluster size scaling. The clusters are dynamically resized by the distances of the events from the cluster center. If most events
//     * are far from the cluster then the cluster size is increased, but if most events are close to the cluster center than the cluster size is
//     * decreased. The size change for each event comes from mixing the old size with a the event distance from the center using the mixing factor.
//     * @param dynamicSizeEnabled true to enable scaling of cluster size
//     */
//    public void setDynamicSizeEnabled(boolean dynamicSizeEnabled){
//        this.dynamicSizeEnabled = dynamicSizeEnabled;
//        prefs.putBoolean("MultiLineClusterTracker.dynamicSizeEnabled",dynamicSizeEnabled);
//    }
    
    /**@see #setColorClustersDifferentlyEnabled */
    public boolean isColorClustersDifferentlyEnabled() {
        return colorClustersDifferentlyEnabled;
    }
    
    /** @param colorClustersDifferentlyEnabled true to color each cluster a different color. false to color each cluster
     * by its age
     */
    public void setColorClustersDifferentlyEnabled(boolean colorClustersDifferentlyEnabled) {
        this.colorClustersDifferentlyEnabled = colorClustersDifferentlyEnabled;
        prefs.putBoolean("MultiLineClusterTracker.colorClustersDifferentlyEnabled",colorClustersDifferentlyEnabled);
    }
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
//    public boolean isUseOnePolarityOnlyEnabled() {
//        return useOnePolarityOnlyEnabled;
//    }
//
//    public void setUseOnePolarityOnlyEnabled(boolean useOnePolarityOnlyEnabled) {
//        this.useOnePolarityOnlyEnabled = useOnePolarityOnlyEnabled;
//        prefs.putBoolean("MultiLineClusterTracker.useOnePolarityOnlyEnabled",useOnePolarityOnlyEnabled);
//    }
//
//    public boolean isUseOffPolarityOnlyEnabled() {
//        return useOffPolarityOnlyEnabled;
//    }
//
//    public void setUseOffPolarityOnlyEnabled(boolean useOffPolarityOnlyEnabled) {
//        this.useOffPolarityOnlyEnabled = useOffPolarityOnlyEnabled;
//        prefs.putBoolean("MultiLineClusterTracker.useOffPolarityOnlyEnabled",useOffPolarityOnlyEnabled);
//    }
    
    public void annotate(Graphics2D g) {
    }
    
    public void annotate(float[][][] frame) {
    }
    
    
    float[] rgb=new float[4]; // used for rendering
    
    synchronized public void annotate(GLAutoDrawable drawable) {
        final float BOX_LINE_WIDTH=5f; // in pixels
        final float PATH_LINE_WIDTH=3f;
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in MultiLineClusterTracker.annotate");
            return;
        }
        gl.glPushMatrix();
        try{
            {
                for(LineCluster c:clusters){
                    if(showAllClusters || c.isVisible()){
                        c.draw(gl);
                    }
                }
            }
        }catch(java.util.ConcurrentModificationException e){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }
    
//    public boolean isGrowMergedSizeEnabled() {
//        return growMergedSizeEnabled;
//    }
//
//    public void setGrowMergedSizeEnabled(boolean growMergedSizeEnabled) {
//        this.growMergedSizeEnabled = growMergedSizeEnabled;
//        prefs.putBoolean("MultiLineClusterTracker.growMergedSizeEnabled",growMergedSizeEnabled);
//    }
    
//    public float getVelocityMixingFactor() {
//        return velocityMixingFactor;
//    }
//
//    public void setVelocityMixingFactor(float velocityMixingFactor) {
//        if(velocityMixingFactor<0) velocityMixingFactor=0; if(velocityMixingFactor>1) velocityMixingFactor=1f;
//        this.velocityMixingFactor = velocityMixingFactor;
//        prefs.putFloat("MultiLineClusterTracker.velocityMixingFactor",velocityMixingFactor);
//    }
    
//    public void setShowVelocity(boolean showVelocity){
//        this.showVelocity = showVelocity;
//        prefs.putBoolean("MultiLineClusterTracker.showVelocity",showVelocity);
//    }
//    public boolean isShowVelocity(){
//        return showVelocity;
//    }
    
    public synchronized boolean isLogDataEnabled() {
        return logDataEnabled;
    }
    
    public synchronized void setLogDataEnabled(boolean logDataEnabled) {
        this.logDataEnabled = logDataEnabled;
        if(!logDataEnabled) {
            logStream.flush();
            logStream.close();
            logStream=null;
        }else{
            try{
                logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File("classTrackerData.txt"))));
                logStream.println("# clusterNumber lasttimestamp x y avergeEventDistance");
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
//    public float getAspectRatio() {
//        return aspectRatio;
//    }
//
//    public void setAspectRatio(float aspectRatio) {
//        if(aspectRatio<0) aspectRatio=0; else if(aspectRatio>4) aspectRatio=4;
//        this.aspectRatio = aspectRatio;
//        prefs.putFloat("MultiLineClusterTracker.aspectRatio",aspectRatio);
//
//    }
    
    
    public boolean isShowAllClusters() {
        return showAllClusters;
    }
    
    /**Sets annotation visibility of clusters that are not "visible"
     * @param showAllClusters true to show all clusters even if there are not "visible"
     */
    public void setShowAllClusters(boolean showAllClusters) {
        this.showAllClusters = showAllClusters;
        prefs.putBoolean("MultiLineClusterTracker.showAllClusters",showAllClusters);
    }
    
//    public boolean isDynamicAspectRatioEnabled() {
//        return dynamicAspectRatioEnabled;
//    }
//
//    public void setDynamicAspectRatioEnabled(boolean dynamicAspectRatioEnabled) {
//        this.dynamicAspectRatioEnabled = dynamicAspectRatioEnabled;
//        prefs.putBoolean("MultiLineClusterTracker.dynamicAspectRatioEnabled",dynamicAspectRatioEnabled);
//    }
    
//    public int getPredictiveVelocityFactor() {
//        return predictiveVelocityFactor;
//    }
//
//    public void setPredictiveVelocityFactor(int predictiveVelocityFactor) {
//        this.predictiveVelocityFactor = predictiveVelocityFactor;
//    }
    
    public boolean isClusterLifetimeIncreasesWithAge() {
        return clusterLifetimeIncreasesWithAge;
    }
    
    /**
     * If true, cluster lifetime withtout support increases proportional to the age of the cluster relative to the clusterLifetimeWithoutSupportUs time
     */
    public void setClusterLifetimeIncreasesWithAge(boolean clusterLifetimeIncreasesWithAge) {
        this.clusterLifetimeIncreasesWithAge = clusterLifetimeIncreasesWithAge;
        prefs.putBoolean("MultiLineClusterTracker.clusterLifetimeIncreasesWithAge",clusterLifetimeIncreasesWithAge);
        
    }
    
    public int getEventBufferLength() {
        return eventBufferLength;
    }
    
    /** Sets tne number of events to consider for forming line segments for clustering
     *@param eventBufferLength the length of the buffer in events
     **/
    synchronized public void setEventBufferLength(int eventBufferLength) {
        if(eventBufferLength<2) eventBufferLength=2; else if(eventBufferLength>1000) eventBufferLength=1000;
        this.eventBufferLength = eventBufferLength;
        prefs.putInt("MultiLineClusterTracker.eventBufferLength",eventBufferLength);
        eventBuffer=new LIFOEventBuffer(eventBufferLength);
    }
    
    
}
