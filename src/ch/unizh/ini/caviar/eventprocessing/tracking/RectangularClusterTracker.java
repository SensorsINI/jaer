/*
 * RectangularClusterTracker.java
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
 * Tracks blobs of events for classes of objects of different sizes.
 *<p>
 A single cluster tracks each object but the cluster simultaneously maintains several hypotheses about the size of the object.
 A cluster is moved by the presently-dominant hypothesis. The present hypothesis is highlighted in annotation.
 The dominant hypothesis for object size is the one that has most support per pixel. 
 A small object with large hypothesis has low average support
 but a large object with small hypotesis will have more support from the large hypothesis (usually).
 *
 * @author tobi
 */
public class RectangularClusterTracker extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
    
    
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
    {setPropertyTooltip("sizeClassificationEnabled","Enables coloring cluster by size threshold");}
    
    protected float defaultClusterRadius;
    {setPropertyTooltip("defaultClusterRadius","default starting size of cluster as fraction of chip size");}
    protected float mixingFactor=getPrefs().getFloat("RectangularClusterTracker.mixingFactor",0.01f); // amount each event moves COM of cluster towards itself
    {setPropertyTooltip("mixingFactor","how much cluster is moved by an event and its distance from the present locatoins");}
    protected float velocityMixingFactor=getPrefs().getFloat("RectangularClusterTracker.velocityMixingFactor",0.01f); // mixing factor for velocity computation
    {setPropertyTooltip("velocityMixingFactor","how much cluster velocity estimate is updated by each event");}
    
    private float surround=getPrefs().getFloat("RectangularClusterTracker.surround",2f);
    {setPropertyTooltip("surround","the radius is expanded by this ratio to define events that pull radius of cluster");}
    private boolean dynamicSizeEnabled=getPrefs().getBoolean("RectangularClusterTracker.dynamicSizeEnabled", false);
    {setPropertyTooltip("dynamicSizeEnabled","size varies dynamically depending on cluster events");}
    private boolean dynamicAspectRatioEnabled=getPrefs().getBoolean("RectangularClusterTracker.dynamicAspectRatioEnabled",false);
    {setPropertyTooltip("dynamicAspectRatioEnabled","aspect ratio depends on events as well");}
    private boolean pathsEnabled=getPrefs().getBoolean("RectangularClusterTracker.pathsEnabled", true);
    {setPropertyTooltip("pathsEnabled","draw paths of clusters over some window");}
    private boolean colorClustersDifferentlyEnabled=getPrefs().getBoolean("RectangularClusterTracker.colorClustersDifferentlyEnabled",false);
    {setPropertyTooltip("colorClustersDifferentlyEnabled","each cluster gets assigned a random color, otherwise color indicates ages");}
    private boolean useOnePolarityOnlyEnabled=getPrefs().getBoolean("RectangularClusterTracker.useOnePolarityOnlyEnabled",false);
    {setPropertyTooltip("useOnePolarityOnlyEnabled","use only one event polarity");}
    private boolean useOffPolarityOnlyEnabled=getPrefs().getBoolean("RectangularClusterTracker.useOffPolarityOnlyEnabled",false);
    {setPropertyTooltip("useOffPolarityOnlyEnabled","use only OFF events, not ON - if useOnePolarityOnlyEnabled");}
    private float aspectRatio=getPrefs().getFloat("RectangularClusterTracker.aspectRatio",1f);
    {setPropertyTooltip("aspectRatio","default (or starting) aspect ratio, taller is larger");}
    private float clusterSize=getPrefs().getFloat("RectangularClusterTracker.clusterSize",.2f);
    {setPropertyTooltip("clusterSize","size (starting) in chip pixels");}
    protected boolean growMergedSizeEnabled=getPrefs().getBoolean("RectangularClusterTracker.growMergedSizeEnabled",false);
    {setPropertyTooltip("growMergedSizeEnabled","enabling makes merged clusters take on sum of sizes, otherwise they take on size of older cluster");}
    private boolean showVelocity=getPrefs().getBoolean("RectangularClusterTracker.showVelocity",true); // enabling this enables both computation and rendering of cluster velocities
    {setPropertyTooltip("showVelocity","computes and shows cluster velocity");}
    private boolean logDataEnabled=false;
    {setPropertyTooltip("logDataEnabled","writes a cluster log file");}
    private PrintStream logStream=null;
    private boolean classifierEnabled=getPrefs().getBoolean("RectangularClusterTracker.classifierEnabled",false);
    {setPropertyTooltip("classifierEnabled","colors clusters based on single size metric");}
    private float classifierThreshold=getPrefs().getFloat("RectangularClusterTracker.classifierThreshold",0.2f);
    {setPropertyTooltip("classifierThreshold","the threshold for classification");}
    private boolean showAllClusters=getPrefs().getBoolean("RectangularClusterTracker.showAllClusters",false);
    {setPropertyTooltip("showAllClusters","shows all clusters, not just those with sufficient support");}
    private boolean useNearestCluster=getPrefs().getBoolean("RectangularClusterTracker.useNearestCluster",false); // use the nearest cluster to an event, not the first containing it
    {setPropertyTooltip("useNearestCluster","event goes to nearest cluster, not to first (usually oldest) cluster containing it");}
    private boolean clusterLifetimeIncreasesWithAge=getPrefs().getBoolean("RectangularClusterTracker.clusterLifetimeIncreasesWithAge",false);
    {setPropertyTooltip("clusterLifetimeIncreasesWithAge","older clusters can live longer without support, good for objects that stop (like walking flies)");}
    
    private final float VELOCITY_VECTOR_SCALING=1e5f; // to scale rendering of cluster velocity vector
    private int predictiveVelocityFactor=1;// making this M=10, for example, will cause cluster to substantially lead the events, then slow down, speed up, etc.
    {setPropertyTooltip("predictiveVelocityFactor","how much cluster position leads position based on estimated velocity");}
    
    private boolean highwayPerspectiveEnabled=getPrefs().getBoolean("RectangularClusterTracker.highwayPerspectiveEnabled",false);
    {setPropertyTooltip("highwayPerspectiveEnabled","Cluster size depends on perspective location; mouse click defines horizon");}
    
    private int thresholdEventsForVisibleCluster=getPrefs().getInt("RectangularClusterTracker.thresholdEventsForVisibleCluster",10);
    {setPropertyTooltip("thresholdEventsForVisibleCluster","Cluster needs this many events to be visible");}
    
    private float thresholdVelocityForVisibleCluster=getPrefs().getFloat("RectangularClusterTracker.thresholdVelocityForVisibleCluster",0);
    {setPropertyTooltip("thresholdVelocityForVisibleCluster","cluster must have at least this velocity in pixels/sec to become visible");}
    
    private int clusterLifetimeWithoutSupportUs=getPrefs().getInt("RectangularClusterTracker.clusterLifetimeWithoutSupport",10000);
    {setPropertyTooltip("clusterLifetimeWithoutSupportUs","Cluster lives this long in ticks (e.g. us) without events before pruning");}
    
    /** maximum and minimum allowed dynamic aspect ratio */
    public static final float ASPECT_RATIO_MAX=5, ASPECT_RATIO_MIN=0.2f;
    
    /**
     * Creates a new instance of RectangularClusterTracker
     */
    public RectangularClusterTracker(AEChip chip) {
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
    
    private void initDefault(String key, String value){
        if(getPrefs().get(key,null)==null) getPrefs().put(key,value);
    }
    
//    ArrayList<Cluster> pruneList=new ArrayList<Cluster>(1);
    protected LinkedList<Cluster> pruneList=new LinkedList<Cluster>();
    
    // the method that actually does the tracking
    synchronized private void track(EventPacket<BasicEvent> ae){
        int n=ae.getSize();
        if(n==0) return;
        int maxNumClusters=getMaxNumClusters();
        
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
            if( closest!=null ){
                closest.addEvent(ev);
            }else if(clusters.size()<maxNumClusters){ // start a new cluster
                Cluster newCluster=new Cluster(ev);
                clusters.add(newCluster);
            }
        }
        // prune out old clusters that don't have support
        pruneList.clear();
        for(Cluster c:clusters){
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
        Cluster c1=null,c2=null;
        do{
            mergePending=false;
            int nc=clusters.size();
            outer:
                for(int i=0;i<nc;i++){
                    c1=clusters.get(i);
                    for(int j=i+1;j<nc;j++){
                        c2=clusters.get(j); // get the other cluster
                        if(c1.distanceTo(c2)<(c1.getRadius()+c2.getRadius())) { // if distance is less than sum of radii merge them
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
                    clusters.add(new Cluster(c1,c2));
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
        for(Cluster c:clusters) c.updatePath();
        
//        if(clusters.size()>beforeMergeCount) throw new RuntimeException("more clusters after merge than before");
        if(isLogDataEnabled() && getNumClusters()>0){
            if(logStream!=null) {
                for(Cluster c:clusters){
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
        String s=clusters!=null? Integer.toString(clusters.size()): null;
        String s2="RectangularClusterTracker with "+s+" clusters ";
        return s2;
    }
    
    /**
     * Method that given event, returns closest cluster and distance to it. The actual computation returns the first cluster that is within the
     minDistance of the event, which reduces the computation at the cost of reduced precision.
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
            if(dynamicSizeEnabled) {
                rX*=surround;
                rY*=surround; // the event is captured even when it is in "invisible surround"
            }
            float dx,dy;
            if((dx=c.distanceToX(event))<rX && (dy=c.distanceToY(event))<rY){
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
     The radius of the cluster here depends on whether {@link #setdynamicSizeEnabled scaling} is enabled.
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
            if(dynamicSizeEnabled) {
                rX*=surround;
                rY*=surround; // the event is captured even when it is in "invisible surround"
            }
            float dx,dy;
            if((dx=c.distanceToX(event))<rX && (dy=c.distanceToY(event))<rY){
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
    public class Cluster{
        /** location of cluster in pixels */
        public Point2D.Float location=new Point2D.Float(); // location in chip pixels
        
        /** velocity of cluster in pixels/tick, where tick is timestamp tick (usually microseconds) */
        public Point2D.Float velocity=new Point2D.Float(); // velocity in chip pixels/sec
        
//        public float tauMsVelocity=50; // LP filter time constant for velocity change
        
//        private LowpassFilter velocityFilter=new LowpassFilter();
        
        private float radius; // in chip chip pixels
//        private float mass; // a cluster has a mass correspoding to its support - the higher the mass, the harder it is to change its velocity
        private float aspectRatio, radiusX, radiusY;
        
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
        private float averageEventDistance; // average (mixed) distance of events from cluster center, a measure of actual cluster size
        protected float distanceToLastEvent=Float.POSITIVE_INFINITY;
        boolean hasObtainedSupport=false;
        
        public Cluster(){
            setRadius(defaultClusterRadius);
            float hue=random.nextFloat();
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
            setClusterNumber(clusterCounter++);
            setAspectRatio(RectangularClusterTracker.this.getAspectRatio());
        }
        
        public Cluster(BasicEvent ev){
            this();
            location.x=ev.x;
            location.y=ev.y;
            lastTimestamp=ev.timestamp;
            firstTimestamp=lastTimestamp;
            numEvents=1;
            setRadius(defaultClusterRadius);
//            System.out.println("constructed "+this);
        }
        
        /**
         Computes a geometrical scale factor based on location of a point relative to the vanishing point.
         If a pixel has been selected (we ask the renderer) then we compute the perspective from this vanishing point, otherwise
         it is the top middle pixel.
         @param p a point with 0,0 at lower left corner
         @return scale factor, which grows linearly to 1 at botton of scene
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
         source cluster parameters. For example, the merged location values are weighted by the number of events that have supported each
         source cluster, so that older clusters weigh more heavily in the resulting cluster location. Subtle bugs or poor performance can result
         from not properly handling the merging of parameters.
         
         @param one the first cluster
         @param two the second cluster
         */
        public Cluster(Cluster one, Cluster two){
            this();
            // merge locations by just averaging
//            location.x=(one.location.x+two.location.x)/2;
//            location.y=(one.location.y+two.location.y)/2;
            
            Cluster older=one.firstTimestamp<two.firstTimestamp? one:two;
//            Cluster older=one.numEvents>two.numEvents? one:two;
            
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
            hasObtainedSupport=older.hasObtainedSupport;
            setAspectRatio(older.getAspectRatio());
            
//            Color c1=one.getColor(), c2=two.getColor();
            setColor(older.getColor());
//            System.out.println("merged "+one+" with "+two);
            //the radius should increase
//            setRadius((one.getRadius()+two.getRadius())/2);
            if(growMergedSizeEnabled){
                float R = (one.getRadius()+two.getRadius())/2;
                setRadius(R + getMixingFactor()*R);
            }else{
                setRadius(older.getRadius());
            }
            
        }
        
        public int getLastEventTimestamp(){
//            EventXYType ev=events.get(events.size()-1);
//            return ev.timestamp;
            return lastTimestamp;
        }
        
        public void addEvent(BasicEvent event){
            if((event instanceof TypedEvent)){
                TypedEvent e=(TypedEvent)event;
                if(useOnePolarityOnlyEnabled){
                    if(useOffPolarityOnlyEnabled){
                        if(e.type==1) return;
                    }else{
                        if(e.type==0) return;
                    }
                }
            }
            
            // save location for computing velocity
            float oldx=location.x, oldy=location.y;
            
            float m=mixingFactor,m1=1-m;;
            
            float dt=event.timestamp-lastTimestamp; // this timestamp may be bogus if it goes backwards in time, we need to check it later
            
            // if showVelocity is enabled, first update the location using the measured estimate of velocity.
            // this will give predictor characteristic to cluster because cluster will move ahead to the predicted location of
            // the present event
            if(showVelocity && dt>0){
                location.x=location.x+getPredictiveVelocityFactor()*dt*velocity.x;
                location.y=location.y+getPredictiveVelocityFactor()*dt*velocity.y;
            }
            
            // compute new cluster location by mixing old location with event location by using
            // mixing factor
            
            location.x=(m1*location.x+m*event.x);
            location.y=(m1*location.y+m*event.y);
            
            if(showVelocity && dt>0){
                // update velocity vector using old and new position only if valid dt
                // and update it by the mixing factors
                float oldvelx=velocity.x;
                float oldvely=velocity.y;
                
                float velx=(location.x-oldx)/dt; // instantaneous velocity for this event in pixels/tick (pixels/us)
                float vely=(location.y-oldy)/dt;
                
                float vm1=1-velocityMixingFactor;
                velocity.x=vm1*oldvelx+velocityMixingFactor*velx;
                velocity.y=vm1*oldvely+velocityMixingFactor*vely;
            }
            
            int prevLastTimestamp=lastTimestamp;
            lastTimestamp=event.timestamp;
            numEvents++;
            instantaneousISI=lastTimestamp-prevLastTimestamp;
            if(instantaneousISI<=0) instantaneousISI=1;
            avgISI=m1*avgISI+m*instantaneousISI;
            instantaneousEventRate=1f/instantaneousISI;
            avgEventRate=m1*avgEventRate+m*instantaneousEventRate;
            
            averageEventDistance=m1*averageEventDistance+m*distanceToLastEvent;
            
            // if scaling is enabled, now scale the cluster size
            scale(event);
            
        }
        
        /** sets the cluster radius according to distance of event from cluster center, but only if dynamicSizeEnabled or dynamicAspectRatioEnabled.
         @param event the event to scale with
         */
        private final void scale(BasicEvent event){
            if(!dynamicSizeEnabled && !dynamicAspectRatioEnabled) return;
            if(dynamicSizeEnabled){
                float dist=distanceTo(event);
                float oldr = radius;
                float newr=(1-mixingFactor)*oldr+dist*mixingFactor;
                float f;
                if(newr>(f=defaultClusterRadius*MAX_SCALE_RATIO))
                    newr=f;
                else if(newr<(f=defaultClusterRadius/MAX_SCALE_RATIO))
                    newr=f;
                setRadius(newr);
            }
            if(dynamicAspectRatioEnabled){
                float dx=(location.x-event.x);
                float dy=(location.y-event.y);
                float oldAspectRatio=getAspectRatio();
                float newAspectRatio=Math.abs(dy/dx/2);
                if(newAspectRatio>ASPECT_RATIO_MAX) newAspectRatio=ASPECT_RATIO_MAX; else if(newAspectRatio<ASPECT_RATIO_MIN) newAspectRatio=ASPECT_RATIO_MIN;
                setAspectRatio((1-mixingFactor)*oldAspectRatio+mixingFactor*newAspectRatio);
            }
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
        
        /** @return the absolute size of the cluster after perspective correction, i.e., a large cluster at the bottom
         of the scene is the same absolute size as a smaller cluster higher up in the scene.
         */
        public float getRadiusCorrectedForPerspective(){
            float scale=1/getPerspectiveScaleFactor(location);
            return radius*scale;
        }
        
        public final float getRadius(){
            return radius;
        }
        
        /** the radius of a cluster is the distance in pixels from the cluster center that is the putative model size.
         If highwayPerspectiveEnabled is true, then the radius is set to a fixed size depending on the defaultClusterRadius and the perspective
         location of the cluster and r is ignored. The aspect ratio parameters of the cluster are also set.
         @param r the radius in pixels
         */
        public void setRadius(float r){
            if(!isHighwayPerspectiveEnabled())
                radius=r;
            else{
                radius=defaultClusterRadius*getPerspectiveScaleFactor(location);
            }
            radiusX=radius/aspectRatio;
            radiusY=radius*aspectRatio;
        }
        
        final public Point2D.Float getLocation() {
            return location;
        }
        public void setLocation(Point2D.Float l){
            this.location = l;
        }
        
        /** @return true if cluster has enough support */
        final public boolean isVisible(){
            if(hasObtainedSupport) return true;
            boolean ret=true;
            if(numEvents<getThresholdEventsForVisibleCluster()) ret=false;
            double speed=Math.sqrt(velocity.x*velocity.x+velocity.y*velocity.y)*1e6/AEConstants.TICK_DEFAULT_US; // speed is in pixels/sec
            if(speed<getThresholdVelocityForVisibleCluster()) ret=false;
            hasObtainedSupport=ret;
            return ret;
        }
        
        /** @return lifetime of cluster in timestamp ticks */
        final public int getLifetime(){
            return lastTimestamp-firstTimestamp;
        }
        
        final public void updatePath(){
            if(!pathsEnabled) return;
            path.add(new Point2D.Float(location.x,location.y));
            if(path.size()>MAX_PATH_LENGTH) path.remove(path.get(0));
        }
        
        public String toString(){
            return String.format("Cluster #%d with %d events near x,y=%d,%d of absRadius=%.1f, visible=%s",
                    getClusterNumber(),                     numEvents,
                    (int)location.x,
                    (int)location.y,
                    getRadiusCorrectedForPerspective(),
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
         computed from the movement of the cluster caused by the last event, then this velocity is mixed
         with the the old velocity by the mixing factor. Thus the mixing factor is appplied twice: once for moving
         the cluster and again for changing the velocity.
         */
        public Point2D.Float getVelocity() {
            return velocity;
        }
        
        /** @return average (mixed by {@link #mixingFactor}) distance from events to cluster center
         */
        public float getAverageEventDistance() {
            return averageEventDistance;
        }
        
        /** @see #getAverageEventDistance */
        public void setAverageEventDistance(float averageEventDistance) {
            this.averageEventDistance = averageEventDistance;
        }
        
        /** Computes the size of the cluster based on average event distance and adjusted for perpective scaling.
         A large cluster at botton of screen is the same size as a smaller cluster closer to horizon
         @return size of cluster in pizels
         */
        public float getMeasuredSizeCorrectedByPerspective(){
            float scale=getPerspectiveScaleFactor(location);
            if(scale<=0) return averageEventDistance;
            return averageEventDistance/scale;
        }
        
        /** Sets color according to measured cluster size */
        public void setColorAccordingToSize(){
            float s=getMeasuredSizeCorrectedByPerspective();
            float hue=2*s/chip.getMaxSize();
            if(hue>1) hue=1;
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
        }
        
        /** Sets color according to age of cluster */
        public void setColorAccordingToAge(){
            float brightness=(float)Math.max(0f,Math.min(1f,getLifetime()/fullbrightnessLifetime));
            Color color=Color.getHSBColor(.5f,1f,brightness);
            setColor(color);
        }
        
        public void setColorAccordingToClass() {
            float s=getMeasuredSizeCorrectedByPerspective();
            float hue=0.5f;
            if(s>getClassifierThreshold()) hue=.3f; else hue=.8f;
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
        }
        
        public void setColorAutomatically() {
            if(isColorClustersDifferentlyEnabled()){
                // color is set on object creation, don't change it
            }else if(!isClassifierEnabled()){
                setColorAccordingToSize(); // sets color according to measured cluster size, corrected by perspective, if this is enabled
                // setColorAccordingToAge(); // sets color according to how long the cluster has existed
            }else{ // classifier enabled
                setColorAccordingToClass();
            }
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
         emphasizes the high spike rates because a few events in rapid succession can rapidly push up the average rate.
         */
        public float getAvgEventRate() {
            return avgEventRate;
        }
        
        public void setAvgEventRate(float avgEventRate) {
            this.avgEventRate = avgEventRate;
        }
        
        public float getAspectRatio() {
            return aspectRatio;
        }
        
        public void setAspectRatio(float aspectRatio) {
            this.aspectRatio = aspectRatio;
            float radiusX=radius/aspectRatio, radiusY=radius*aspectRatio;
        }
        
    }
    
    public java.util.List<RectangularClusterTracker.Cluster> getClusters() {
        return this.clusters;
    }
    
    private LinkedList<RectangularClusterTracker.Cluster> getPruneList(){
        return this.pruneList;
    }
    
    
    protected static final float fullbrightnessLifetime=1000000;
    
    
    protected Random random=new Random();
    
    private final void drawCluster(final Cluster c, float[][][] fr){
        int x=(int)c.getLocation().x;
        int y=(int)c.getLocation().y;
        
        
        int sy=(int)c.getRadius(); // sx sy are (half) size of rectangle
        int sx=sy;
        int ix, iy;
        int mn,mx;
        
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
        
        ArrayList<Point2D.Float> points=c.getPath();
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
    private final void colorPixel(final int x, final int y, final float[][][] fr, int channel, Color color){
        if(y<0 || y>fr.length-1 || x<0 || x>fr[0].length-1) return;
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
    public final int getClusterLifetimeWithoutSupportUs() {
        return clusterLifetimeWithoutSupportUs;
    }
    
    /** lifetime of cluster in ms without support */
    public void setClusterLifetimeWithoutSupportUs(final int clusterLifetimeWithoutSupport) {
        this.clusterLifetimeWithoutSupportUs=clusterLifetimeWithoutSupport;
        getPrefs().putInt("RectangularClusterTracker.clusterLifetimeWithoutSupport", clusterLifetimeWithoutSupport);
    }
    
    /** max distance from cluster to event as fraction of size of array */
    public final float getClusterSize() {
        return clusterSize;
    }
    
    /** sets max distance from cluster center to event as fraction of maximum size of chip pixel array.
     e.g. clusterSize=0.5 and 128x64 array means cluster has radius of 0.5*128=64 pixels.
     
     @param clusterSize
     */
    public void setClusterSize(float clusterSize) {
        if(clusterSize>1f) clusterSize=1f;
        if(clusterSize<0) clusterSize=0;
        defaultClusterRadius=(int)Math.max(chip.getSizeX(),chip.getSizeY())*clusterSize;
        this.clusterSize=clusterSize;
        for(Cluster c:clusters){
            c.setRadius(defaultClusterRadius);
        }
        getPrefs().putFloat("RectangularClusterTracker.clusterSize", clusterSize);
    }
    
    private int maxNumClusters=getPrefs().getInt("RectangularClusterTracker.maxNumClusters",10);
    {setPropertyTooltip("maxNumClusters","Sets the maximum potential number of clusters");}
    
    /** max number of clusters */
    public final int getMaxNumClusters() {
        return maxNumClusters;
    }
    
    /** max number of clusters */
    public void setMaxNumClusters(final int maxNumClusters) {
        this.maxNumClusters=maxNumClusters;
        getPrefs().putInt("RectangularClusterTracker.maxNumClusters", maxNumClusters);
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
    public final int getThresholdEventsForVisibleCluster() {
        return thresholdEventsForVisibleCluster;
    }
    
    /** number of events to make a potential cluster visible */
    public void setThresholdEventsForVisibleCluster(final int thresholdEventsForVisibleCluster) {
        this.thresholdEventsForVisibleCluster=thresholdEventsForVisibleCluster;
        getPrefs().putInt("RectangularClusterTracker.thresholdEventsForVisibleCluster", thresholdEventsForVisibleCluster);
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
        EventPacket out;
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) {
            out=enclosedFilter.filterPacket(in);
            track(out);
            return out;
        } else {
            track(in);
            return in;
        }
    }
    
    
    
    
    public boolean isHighwayPerspectiveEnabled() {
        return highwayPerspectiveEnabled;
    }
    
    public void setHighwayPerspectiveEnabled(boolean highwayPerspectiveEnabled) {
        this.highwayPerspectiveEnabled = highwayPerspectiveEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.highwayPerspectiveEnabled",highwayPerspectiveEnabled);
    }
    
    public float getMixingFactor() {
        return mixingFactor;
    }
    
    public void setMixingFactor(float mixingFactor) {
        if(mixingFactor<0) mixingFactor=0; if(mixingFactor>1) mixingFactor=1f;
        this.mixingFactor = mixingFactor;
        getPrefs().putFloat("RectangularClusterTracker.mixingFactor",mixingFactor);
    }
    
    /** @see #setSurround */
    public float getSurround() {
        return surround;
    }
    
    /** sets scale factor of radius that events outside the cluster size can affect the size of the cluster if
     {@link #setDynamicSizeEnabled scaling} is enabled.
     @param surround the scale factor, constrained >1 by setter. radius is multiplied by this to determine if event is within surround.
     */
    public void setSurround(float surround){
        if(surround < 1) surround = 1;
        this.surround = surround;
        getPrefs().putFloat("RectangularClusterTracker.surround",surround);
    }
    
    /** @see #setPathsEnabled
     */
    public boolean isPathsEnabled() {
        return pathsEnabled;
    }
    
    /** @param pathsEnabled true to show the history of the cluster locations on each packet */
    public void setPathsEnabled(boolean pathsEnabled) {
        this.pathsEnabled = pathsEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.pathsEnabled",pathsEnabled);
    }
    
    /** @see #setDynamicSizeEnabled
     */
    public boolean getDynamicSizeEnabled(){
        return dynamicSizeEnabled;
    }
    
    /**
     Enables cluster size scaling. The clusters are dynamically resized by the distances of the events from the cluster center. If most events
     are far from the cluster then the cluster size is increased, but if most events are close to the cluster center than the cluster size is
     decreased. The size change for each event comes from mixing the old size with a the event distance from the center using the mixing factor.
     @param dynamicSizeEnabled true to enable scaling of cluster size
     */
    public void setDynamicSizeEnabled(boolean dynamicSizeEnabled){
        this.dynamicSizeEnabled = dynamicSizeEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.dynamicSizeEnabled",dynamicSizeEnabled);
    }
    
    /**@see #setColorClustersDifferentlyEnabled */
    public boolean isColorClustersDifferentlyEnabled() {
        return colorClustersDifferentlyEnabled;
    }
    
    /** @param colorClustersDifferentlyEnabled true to color each cluster a different color. false to color each cluster
     by its age
     */
    public void setColorClustersDifferentlyEnabled(boolean colorClustersDifferentlyEnabled) {
        this.colorClustersDifferentlyEnabled = colorClustersDifferentlyEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.colorClustersDifferentlyEnabled",colorClustersDifferentlyEnabled);
    }
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    public boolean isUseOnePolarityOnlyEnabled() {
        return useOnePolarityOnlyEnabled;
    }
    
    public void setUseOnePolarityOnlyEnabled(boolean useOnePolarityOnlyEnabled) {
        this.useOnePolarityOnlyEnabled = useOnePolarityOnlyEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.useOnePolarityOnlyEnabled",useOnePolarityOnlyEnabled);
    }
    
    public boolean isUseOffPolarityOnlyEnabled() {
        return useOffPolarityOnlyEnabled;
    }
    
    public void setUseOffPolarityOnlyEnabled(boolean useOffPolarityOnlyEnabled) {
        this.useOffPolarityOnlyEnabled = useOffPolarityOnlyEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.useOffPolarityOnlyEnabled",useOffPolarityOnlyEnabled);
    }
    
    public void annotate(Graphics2D g) {
    }
    
    protected void drawBox(GL gl, int x, int y, int sx, int sy){
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(x-sx,y-sy);
            gl.glVertex2i(x+sx,y-sy);
            gl.glVertex2i(x+sx,y+sy);
            gl.glVertex2i(x-sx,y+sy);
        }
        gl.glEnd();
    }
    
    synchronized public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        final float BOX_LINE_WIDTH=5f; // in pixels
        final float PATH_LINE_WIDTH=3f;
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
                    if(showAllClusters || c.isVisible()){
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
                        drawBox(gl,x,y,sx,sy);
                        
                        // draw path points
                        gl.glLineWidth(PATH_LINE_WIDTH);
                        gl.glBegin(GL.GL_LINE_STRIP);
                        {
                            ArrayList<Point2D.Float> points=c.getPath();
                            for(Point2D.Float p:points){
                                gl.glVertex2f(p.x,p.y);
                            }
                        }
                        gl.glEnd();
                        
                        // now draw velocity vector
                        if(showVelocity){
                            gl.glBegin(GL.GL_LINES);
                            {
                                gl.glVertex2i(x,y);
                                gl.glVertex2f(x+c.velocity.x*VELOCITY_VECTOR_SCALING,y+c.velocity.y*VELOCITY_VECTOR_SCALING);
                            }
                            gl.glEnd();
                        }
                        
//                        // draw text size of cluster corrected for perspective
//                            // text for cluster
//                            int font = GLUT.BITMAP_HELVETICA_12;
//                            gl.glColor3f(1,1,1);
//                            gl.glRasterPos3f(c.location.x,c.location.y,0);
//                            chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.1f", c.getRadiusCorrectedForPerspective()));
                        
                        // draw text avgEventRate
                        int font = GLUT.BITMAP_HELVETICA_12;
                        gl.glColor3f(1,1,1);
                        gl.glRasterPos3f(c.location.x,c.location.y,0);
                        // annotate the cluster with the event rate computed as 1/(avg ISI) in keps
                        float keps=c.getAvgEventRate()/(AEConstants.TICK_DEFAULT_US)*1e3f;
                        chip.getCanvas().getGlut().glutBitmapString(font, String.format("%.0fkeps", keps ));
                    }
                }
            }
        }catch(java.util.ConcurrentModificationException e){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }
    
//    void drawGLCluster(int x1, int y1, int x2, int y2)
    
    /** annotate the rendered retina frame to show locations of clusters */
    synchronized public void annotate(float[][][] frame) {
        if(!isFilterEnabled()) return;
        // disable for now TODO
        if(chip.getCanvas().isOpenGLEnabled()) return; // done by open gl annotator
        for(Cluster c:clusters){
            if(c.isVisible()){
                drawCluster(c, frame);
            }
        }
    }
    
//    public void preferenceChange(PreferenceChangeEvent evt) {
//        mixingFactor=prefs.getFloat("RectangularClusterTracker.mixingFactor",0.1f); // amount each event moves COM of cluster towards itself
//        pathsEnabled=prefs.getBoolean("RectangularClusterTracker.pathsEnabled", true);
//        colorClustersDifferentlyEnabled=prefs.getBoolean("RectangularClusterTracker.colorClustersDifferentlyEnabled",false);
//        useOnePolarityOnlyEnabled=prefs.getBoolean("RectangularClusterTracker.useOnePolarityOnlyEnabled",false);
//        useOffPolarityOnlyEnabled=prefs.getBoolean("RectangularClusterTracker.useOffPolarityOnlyEnabled",false);
//        aspectRatio=prefs.getFloat("RectangularClusterTracker.aspectRatio",1f);
//    }
    
    public boolean isGrowMergedSizeEnabled() {
        return growMergedSizeEnabled;
    }
    
    public void setGrowMergedSizeEnabled(boolean growMergedSizeEnabled) {
        this.growMergedSizeEnabled = growMergedSizeEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.growMergedSizeEnabled",growMergedSizeEnabled);
    }
    
    public float getVelocityMixingFactor() {
        return velocityMixingFactor;
    }
    
    public void setVelocityMixingFactor(float velocityMixingFactor) {
        if(velocityMixingFactor<0) velocityMixingFactor=0; if(velocityMixingFactor>1) velocityMixingFactor=1f;
        this.velocityMixingFactor = velocityMixingFactor;
        getPrefs().putFloat("RectangularClusterTracker.velocityMixingFactor",velocityMixingFactor);
    }
    
    public void setShowVelocity(boolean showVelocity){
        this.showVelocity = showVelocity;
        getPrefs().putBoolean("RectangularClusterTracker.showVelocity",showVelocity);
    }
    public boolean isShowVelocity(){
        return showVelocity;
    }
    
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
    
    public float getAspectRatio() {
        return aspectRatio;
    }
    
    public void setAspectRatio(float aspectRatio) {
        if(aspectRatio<0) aspectRatio=0; else if(aspectRatio>4) aspectRatio=4;
        this.aspectRatio = aspectRatio;
        getPrefs().putFloat("RectangularClusterTracker.aspectRatio",aspectRatio);
        
    }
    
    public boolean isClassifierEnabled() {
        return classifierEnabled;
    }
    
    /** Sets whether classifier is enabled.
     @param classifierEnabled true to enable classifier
     */
    public void setClassifierEnabled(boolean classifierEnabled) {
        this.classifierEnabled = classifierEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.classifierEnabled",classifierEnabled);
    }
    
    public float getClassifierThreshold() {
        return classifierThreshold;
    }
    
    public void setClassifierThreshold(float classifierThreshold) {
        this.classifierThreshold = classifierThreshold;
        getPrefs().putFloat("RectangularClusterTracker.classifierThreshold",classifierThreshold);
    }
    
    public boolean isShowAllClusters() {
        return showAllClusters;
    }
    
    /**Sets annotation visibility of clusters that are not "visible"
     @param showAllClusters true to show all clusters even if there are not "visible"
     */
    public void setShowAllClusters(boolean showAllClusters) {
        this.showAllClusters = showAllClusters;
        getPrefs().putBoolean("RectangularClusterTracker.showAllClusters",showAllClusters);
    }
    
    public boolean isDynamicAspectRatioEnabled() {
        return dynamicAspectRatioEnabled;
    }
    
    public void setDynamicAspectRatioEnabled(boolean dynamicAspectRatioEnabled) {
        this.dynamicAspectRatioEnabled = dynamicAspectRatioEnabled;
        getPrefs().putBoolean("RectangularClusterTracker.dynamicAspectRatioEnabled",dynamicAspectRatioEnabled);
    }
    
    public boolean isUseNearestCluster() {
        return useNearestCluster;
    }
    
    public void setUseNearestCluster(boolean useNearestCluster) {
        this.useNearestCluster = useNearestCluster;
        getPrefs().putBoolean("RectangularClusterTracker.useNearestCluster",useNearestCluster);
    }
    
    public int getPredictiveVelocityFactor() {
        return predictiveVelocityFactor;
    }
    
    public void setPredictiveVelocityFactor(int predictiveVelocityFactor) {
        this.predictiveVelocityFactor = predictiveVelocityFactor;
    }
    
    public boolean isClusterLifetimeIncreasesWithAge() {
        return clusterLifetimeIncreasesWithAge;
    }
    
    /**
     * If true, cluster lifetime withtout support increases proportional to the age of the cluster relative to the clusterLifetimeWithoutSupportUs time
     */
    public void setClusterLifetimeIncreasesWithAge(boolean clusterLifetimeIncreasesWithAge) {
        this.clusterLifetimeIncreasesWithAge = clusterLifetimeIncreasesWithAge;
        getPrefs().putBoolean("RectangularClusterTracker.clusterLifetimeIncreasesWithAge",clusterLifetimeIncreasesWithAge);
        
    }

    public float getThresholdVelocityForVisibleCluster() {
        return thresholdVelocityForVisibleCluster;
    }

    /** A cluster must have at least this velocity magnitude to become visible
     @param thresholdVelocityForVisibleCluster speed in pixels/second
     */
    public void setThresholdVelocityForVisibleCluster(float thresholdVelocityForVisibleCluster) {
        if(thresholdVelocityForVisibleCluster<0) thresholdVelocityForVisibleCluster=0;
        this.thresholdVelocityForVisibleCluster = thresholdVelocityForVisibleCluster;
        getPrefs().putFloat("RectangularClusterTracker.thresholdVelocityForVisibleCluster",thresholdVelocityForVisibleCluster);
    }
    
    
}
