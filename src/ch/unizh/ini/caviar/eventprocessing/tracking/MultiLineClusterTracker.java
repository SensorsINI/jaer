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
import ch.unizh.ini.caviar.eventprocessing.TimeLimiter;
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
import javax.media.opengl.GLCanvas;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.GLU;
import javax.swing.*;

/**
 * Tracks multiple lines in the scene using a cluster based method based on pairs of recent events.
 * The event pairs come from a buffer formed from recent events. Each pair defines a line with polar and angle parameters.
 * Lines are tracked using polar (rho) and angle (theta) parameters in a space of rho/theta, in analogy with the MultiLineClusterTracker tracking
 * of rectangular objects in retinal coordinate space.
 *
 * @author tobi
 */
public class MultiLineClusterTracker extends EventFilter2D implements FrameAnnotater, Observer {
    static final double PI2=Math.PI*2;
    private static Preferences prefs=Preferences.userNodeForPackage(MultiLineClusterTracker.class);
    private java.util.List<LineCluster> clusters=new ArrayList<LineCluster>();
    
    private int eventBufferLength=prefs.getInt("MultiLineClusterTracker.eventBufferLength",30);
    {setPropertyTooltip("eventBufferLength","Number of past events to form line segments from for clustering");}
    
    private LIFOEventBuffer eventBuffer=new LIFOEventBuffer(eventBufferLength);
    LineClusterCanvas lineClusterCanvas=null;
    
    protected AEChip chip;
    private AEChipRenderer renderer;
    
//    private boolean showLineClusterCanvas=false;
//    {setPropertyTooltip("showLineClusterCanvas","shows a canvas with cluster information");}
    
    private boolean showLineSegments=false;
    {setPropertyTooltip("showLineSegments","show valid LineSegments contributing to LineClusters");}
    
    private int minSegmentLength=prefs.getInt("MultiLineClusterTracker.minSegmentLength",3);
    {setPropertyTooltip("minSegmentLength","minium manhatten length of line segment in pixels");}
    private int maxSegmentLength=prefs.getInt("MultiLineClusterTracker.maxSegmentLength",20);
    {setPropertyTooltip("maxSegmentLength","max manhatten line segment length in pixels");}
    private int maxSegmentDt=prefs.getInt("MultiLineClusterTracker.maxSegmentDt",20000);
    {setPropertyTooltip("maxSegmentDt","maximum dt in ticks for line segment event pair");}
    
    /** scaling can't make cluster bigger or smaller than this ratio to default cluster size */
    public static final float MAX_SCALE_RATIO=2;
    
    
    private float rhoRadius=prefs.getFloat("MultiLineClusterTracker.rhoRadius",6);
    {setPropertyTooltip("rhoRadius","\"radius\" of line cluster around line in pixels");}
    private float thetaRadiusRad=prefs.getFloat("MultiLineClusterTracker.thetaRadiusRad",.01f);
    {setPropertyTooltip("thetaRadiusDeg","\"radius\" of line cluster in degrees around line cluster angle");}
    
    private float mixingFactorRho=prefs.getFloat("MultiLineClusterTracker.mixingFactorRho",0.01f); // amount each event moves COM of cluster towards itself
    {setPropertyTooltip("mixingFactorRho","how much line cluster rho (distance from origin) is moved by a segment event");}
    private float mixingFactorTheta=prefs.getFloat("MultiLineClusterTracker.mixingFactorTheta",0.01f); // amount each event moves COM of cluster towards itself
    {setPropertyTooltip("mixingFactorTheta","how much line cluster angle is turned by a line segment event");}
    private float mixingFactorPosition=prefs.getFloat("MultiLineClusterTracker.mixingFactorPosition",0.01f);
    {setPropertyTooltip("mixingFactorPosition","how much line cluster position is moved by segment event");}
    
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
    }
    
    ArrayList<LineSegment> segList=new ArrayList<LineSegment>(); // used for rendering segments used for a packet
    
//    ArrayList<LineCluster> pruneList=new ArrayList<LineCluster>(1);
    protected LinkedList<LineCluster> pruneList=new LinkedList<LineCluster>();
    
    private LineSegment segment=new LineSegment();
    
    // the method that actually does the tracking
    synchronized private void track(EventPacket<BasicEvent> ae){
        int n=ae.getSize();
        if(n==0) return;
        int maxNumClusters=getMaxNumClusters();
        // for each event, see which cluster it is closest to and add it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
//        for(int i=0;i<n;i++){
        if(showLineSegments){
            synchronized(segList) {
                segList.clear();
            }
        }
        for(BasicEvent event:ae){
//            System.out.println("event="+event);
            // for each past event, possibly form a line segment
            // and use it to move clusters if the segment is valid for clustering
            for(BasicEvent oldEvent:eventBuffer){
//                System.out.println("    oldEvent="+oldEvent);
                if(isValidSegment(oldEvent,event)){ // args are older,newer
                    if(showLineSegments){
                        // expensive but good for debugging
                        segment=new LineSegment(oldEvent,event);
                        segList.add(segment);
                    }else{
                        segment.set(oldEvent,event); // this reuses object, saves cost of newing segments
                    }
                    LineCluster closest=null;
                    closest=getNearestCluster(segment);
                    if( closest!=null ){
                        if(showLineSegments){
                            segment.lineCluster=closest; // we'll color this segment the color of the cluster
                        }
                        closest.addSegment(segment);
                    }else if(clusters.size()<maxNumClusters){ // start a new cluster
                        LineCluster newCluster=new LineCluster(segment);
                        clusters.add(newCluster);
                    }
                }
            }
            eventBuffer.add(event); // add the most recent event
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
//        pruneList.clear();
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
                        if(c1.isOverlapping(c2)) { // if distance is less than sum of radii merge them
                            // if cluster is close to another cluster, merge them
                            mergePending=true;
                            break outer; // break out of the outer loop
                        }
                    }
                }
                if(mergePending && c1!=null && c2!=null){
//                    pruneList.add(c1);
//                    pruneList.add(c2); // we just remove them, no need to prune them
                    clusters.remove(c1); // ok to remove because we are not iterating here
                    clusters.remove(c2);
                    clusters.add(new LineCluster(c1,c2));
                }
        }while(mergePending);
//        clusters.removeAll(pruneList);
        
// update all cluster sizes
//        // note that without this following call, clusters maintain their starting size until they are merged with another cluster.
//        if(isHighwayPerspectiveEnabled()){
//            for(LineCluster c:clusters){
//                c.setRadius(defaultClusterRadius);
//            }
//        }
        
// update paths of clusters
//        for(LineCluster c:clusters) c.updatePath();
        
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
    
    /**
     * Computes signed distance to-from between two angles with cut at -PI,PI. E.g.
     *     if e is from small angle and from=PI-e, to=-PI+e, then angular distance to-from is
     *     -2e rather than (PI-e)-(-PI+e)=2PI-2e.
     *     This minimum angle difference is useful to push an angle in the correct direction
     *     by the correct amount. For this example, we want to push an angle hovering around PI-e.
     *     We don't want angles of -PI+e to push the angle from lot, just from bit towards PI. If we have from
     *     mixing factor m<<1, then new angle c=from+m*angleDistance(from,to)
     *
     *
     * @param from the first angle
     * @param to the second angle
     * @return the smallest difference to-from, ordinarily positive if to>from
     */
    private double angleDistance(double from, double to){
        double d=to-from;
        if(d>Math.PI)
            return d-Math.PI;
        if(d<-Math.PI)
            return d+Math.PI;
        return d;
    }
    
    private class LineSegment{
//        BasicEvent a=null, b=null; // we don't use these because they can get referenced out from under us
        double thetaRad=0, rhoPixels=0; // theta is angle in radians from 0 to PI with 0 and PI horizontal lines
        // rhoPixels is distance of line segment closest passage to origin of chip image LL corner
        int dx=0,dy=0, dt=0;
        int ax=0,bx=0,ay=0,by=0;
        float x=0,y=0; //TODO need float?
        int timestamp=0;
        LineCluster lineCluster=null;
        
        /** makes an empty segment */
        LineSegment(){
        }
        
        /** Make a new LineSegment with first event a and second (later) event b */
        LineSegment(BasicEvent a, BasicEvent b){
            set(a,b);
        }
        
        /**
         computes the rho (pixels distance from origin at LL corner)
         * and theta (radian angle from 0 horizontal) for the segment given the two events.
         *This is confusing because a raw calculation can give negative rho and angle that spans -PI to PI.
         *This values are unambiguous about specifying the line segment but similar segments can have very different rho/theta values
         *depending on the quadrant that dx,dy are in. Also, the rho values -PI and PI are identical although they differ by 2*PI. This makes
         *combining values confusing.
         */
        void set(BasicEvent a, BasicEvent b){
//            this.a=a;
//            this.b=b;
            this.timestamp=b.timestamp;
            ax=a.x;
            bx=b.x;
            ay=a.y;
            by=b.y;
            x=(ax+bx)/2;
            y=(ay+by)/2;
            dt=b.timestamp-a.timestamp;
            dx=ax-bx;
            dy=ay-by; // vector (dx,dy) points from b to a (adding dx,dy to b gives a)
            // now compute angle of dual of vector
            // dual of vector has components exchanged and one sign flipped
            // this is a rotation by 90 deg
            // then compute this angle
            // this angle is angle relative to x axis CCW of normal to dx,dy
            thetaRad=Math.atan2(dx,-dy); // atan2(y,x) goes from -PI to PI, theta goes from 0 to Pi, 0 and Pi being horizontal lines
            // now compute rho of this segment. This is closest passage to origin.
            rhoPixels=x*Math.cos(thetaRad)+y*Math.sin(thetaRad);
            // rho may come out negative, in this case we make it positive and rotate theta by PI
            if(rhoPixels<0){
                // flip rho, rotate by PI 
                rhoPixels=-rhoPixels;
                if(thetaRad>0)
                    thetaRad-=Math.PI;
                else
                    thetaRad+=Math.PI;
//                thetaRad=Math.IEEEremainder(thetaRad,PI2);
            }
            //           System.out.println(String.format("x,y= %.0f,%.0f dx,dy= %d,%d, rhoPixels=%.0f thetaDeg=%.0f",x,y,dx,dy,rhoPixels, Math.toDegrees(thetaRad)));
        }
        
//        /** @return normalized "distance" between two segments based on polar representation. Are segments co-linear? If so measure should
//         be small.
//         */
//        double distance2To(LineSegment b){
//            double dtheta=(thetaRad-b.thetaRad)/Math.PI/2; // problem is that 0 and PI are close
//            dtheta*=dtheta;
//            double drho=(rhoPixels-b.rhoPixels)/chip.getMaxSize();
//            drho*=drho;
//            return (dtheta+drho);
//        }
        
        public String toString(){
            return String.format("LineSegment a x,y=%d,%d, b x,y=%d,%d, rho=%.1f pix theta=%.0f deg x,y=%.1f,%.1f", ax,ay,bx,by,rhoPixels, Math.toDegrees(thetaRad),x,y);
        }
        
        private void draw(GL gl) {
            if(lineCluster!=null){
                gl.glColor3fv(lineCluster.rgb,0);
//                System.out.println(this);
            }else{
                gl.glColor3f(.2f,.2f,.2f);
            }
            gl.glPushMatrix();
            gl.glLineWidth(1);
            
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2i(ax,ay);
            gl.glVertex2i(bx,by);
            gl.glEnd();
            gl.glPopMatrix();
        }
        
        
    }
    /** Checks two events to see if they could form a valid line segment.
     @return segment if line segment is long enough but not too long and is not based on events too far apart in time. Returns null if not
     a goog segment*/
    private boolean isValidSegment(BasicEvent older, BasicEvent newer){
        int dt=newer.timestamp-older.timestamp;
        if(dt<0){
            log.warning("older event is later than newer event; negative dt="+dt+" older="+older+" newer="+newer);
            return false;
        }
        if(dt>maxSegmentDt) return false;
        int dx=Math.abs(newer.x-older.x), dy=Math.abs(older.y-newer.y);
        if(dx<minSegmentLength && dy<minSegmentLength) return false;
        if(dx>maxSegmentLength || dy>maxSegmentLength) return false;
        return true;
    }
    
    /**
     * Method that given segment event, returns closest cluster and distance to it. The actual computation returns the first cluster that is within the
     * minDistance of the event, which reduces the computation at the cost of reduced precision.
     * @param segment the segment event
     * @return closest cluster object (a cluster with a distance - that distance is the distance between the given event and the returned cluster).
     */
    private LineCluster getNearestCluster(LineSegment segment){
//        double minDistance=Double.MAX_VALUE;
        LineCluster closest=null;
        double currentDistance;
        for(LineCluster c:clusters){
            if(c.contains(segment)){
                closest=c;
//                System.out.println("contains: dRho="+(c.rhoPixels-segment.rhoPixels)+" dTheta="+(c.thetaRad-segment.thetaRad));
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
        
        protected final int MAX_PATH_LENGTH=100;
//        protected ArrayList<Point2D.Float> path=new ArrayList<Point2D.Float>(MAX_PATH_LENGTH);
        protected Color color=null;
        float[] rgb=new float[4];
        
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
        
        public LineCluster(LineSegment s){
            this();
            rhoPixels=s.rhoPixels;
            thetaRad=s.thetaRad;
            location.x=s.x;
            location.y=s.y;
            lastTimestamp=s.timestamp;
            firstTimestamp=lastTimestamp;
            numEvents=1;
            s.lineCluster=this;
        }
        
//        /**
//         * Computes a geometrical scale factor based on location of a point relative to the vanishing point.
//         * If a pixel has been selected (we ask the renderer) then we compute the perspective from this vanishing point, otherwise
//         * it is the top middle pixel.
//         * @param p a point with 0,0 at lower left corner
//         * @return scale factor, which grows linearly to 1 at botton of scene
//         */
//        final float getPerspectiveScaleFactor(Point2D.Float p){
//            if(!renderer.isPixelSelected()){
//                float yfrac=1f-(p.y/chip.getSizeY()); // yfrac grows to 1 at bottom of image
//                return yfrac;
//            }else{
//                // scale is 0 at vanishing point and grows linearly to 1 at max size of chip
//                int size=chip.getMaxSize();
//                float d=(float)p.distance(renderer.getXsel(),renderer.getYsel());
//                float scale=d/size;
//                return scale;
//            }
//        }
//
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
//            path=older.path;
            velocity.x=older.velocity.x;
            velocity.y=older.velocity.y;
            avgEventRate=older.avgEventRate;
            avgISI=older.avgISI;
            
            rhoPixels=older.rhoPixels;
            thetaRad=older.thetaRad;
            
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
            
            float mRho=mixingFactorRho,  m1Rho=1-mRho;;
//            float mTheta=mixingFactorTheta,   m1Theta=1-mTheta;;
            
            float dt=seg.timestamp-lastTimestamp; // this timestamp may be bogus if it goes backwards in time, we need to check it later
            
            // compute new cluster location by mixing old location with event location by using
            // mixing factors
            rhoPixels=mRho*seg.rhoPixels+m1Rho*rhoPixels;
            double dTheta=angleDistance(thetaRad,seg.thetaRad);
            thetaRad=thetaRad+mixingFactorTheta*dTheta;
            
            location.x=(1-mixingFactorPosition)*location.x+mixingFactorPosition*seg.x;
            location.y=(1-mixingFactorPosition)*location.y+mixingFactorPosition*seg.y;
            
            
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
        private double distanceAbsToRho(LineSegment seg){
            return Math.abs(rhoPixels-seg.rhoPixels);
        }
        
        private double distanceAbsToTheta(LineSegment seg){
            double d=angleDistance(thetaRad,seg.thetaRad);
            return Math.abs(d);
        }
        
        /** @return distance in pixels between line segment and line cluster rho metric */
        private double distanceAbsToRho(LineCluster c){
            return Math.abs(rhoPixels-c.rhoPixels);
        }
        
        private double distanceAbsToTheta(LineCluster c){
            double d=angleDistance(thetaRad,c.thetaRad);
            return Math.abs(d);
        }
        
        /** @return distance of this cluster to the other cluster, abs value in normalized units combining rho and theta */
        public double distanceAbsTo(LineCluster c){
            double dRho=distanceAbsToRho(c);
            double dTheta=distanceAbsToTheta(c);
            return distanceMetric(dRho,dTheta);
        }
        
        /** @return distance of this cluster to the other cluster, abs value in normalized units combining rho and theta  */
        public double distanceAbsTo(LineSegment c){
            double dRho=distanceAbsToRho(c);
            double dTheta=distanceAbsToTheta(c);
            double d=distanceMetric(dRho,dTheta);
            return d;
        }
        
        /** Returns true if cluster overlapped another cluster
         @param the other cluster
         */
        public boolean isOverlapping(LineCluster c){
            double d1=distanceAbsToRho(c);
            double d2=distanceAbsToTheta(c);
            if(d1<rhoRadius*2 && d2<thetaRadiusRad*2) return true;
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
            return String.format("Cluster #%d with %d events near x,y=%d,%d rho=%.0f, theta=%.0f deg, visible=%s",
                    getClusterNumber(),                     numEvents,
                    (int)location.x,
                    (int)location.y,
                    rhoPixels,
                    thetaRad*180/Math.PI,
                    isVisible()
                    );
        }
        
//        public ArrayList<Point2D.Float> getPath() {
//            return path;
//        }
        
        public Color getColor() {
            return color;
        }
        
        public void setColor(Color color) {
            this.color = color;
            color.getRGBComponents(rgb); // set the rgb components to use for rendering
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
        
        /** draws LineCluster in current GL context assuming 1 pixel to 1 graphics unit with chip LL corner at LL canvas corner */
        private void draw(GL gl) {
            int x=(int)location.x;
            int y=(int)location.y;
            final int length=chip.getMaxSize()/3;
//            System.out.println("drawing "+this);
            
            int sx=(int)(Math.cos(thetaRad+Math.PI/2)*length);
            int sy=(int)(Math.sin(thetaRad+Math.PI/2)*length);
//           int sx1=(int)(Math.cos(thetaRad+thetaRadiusRad+Math.PI/2)*length);
//            int sy1=(int)(Math.sin(thetaRad+thetaRadiusRad+Math.PI/2)*length);
//          int sx2=(int)(Math.cos(thetaRad-thetaRadiusRad+Math.PI/2)*length);
//            int sy2=(int)(Math.sin(thetaRad-thetaRadiusRad+Math.PI/2)*length);
            
            // set color and line width of cluster annotation
            getColor().getRGBComponents(rgb);
            float f=getLifetime()/clusterLifetimeWithoutSupportUs;
            if(f>1) f=1;
            for(int i=0;i<3;i++) rgb[i]*=f;
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
//               gl.glVertex2i(x-sx1,y-sy1);
//                gl.glVertex2i(x+sx2,y+sy2);
//               gl.glVertex2i(x-sx2,y-sy2);
//                gl.glVertex2i(x+sx2,y+sy2);
            }
            gl.glEnd();
        }
        
        /**
         *
         *
         * @return true if LineCluster contains segment, i.e., rho is within rhoRadius and thetaRad is within thetaRadiusRad
         */
        private boolean contains(MultiLineClusterTracker.LineSegment segment) {
            double dRho=distanceAbsToRho(segment);
            if(dRho>getRhoRadius())
                return false;
            double dTheta=distanceAbsToTheta(segment);
            if(dTheta>thetaRadiusRad)
                return false;
            return true;
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
                if(showLineSegments){
                    for(LineSegment s:segList){
                        s.draw(gl);
                    }
                }
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
        
//        if(showLineClusterCanvas && lineClusterCanvas!=null){
//            lineClusterCanvas.display();
//        }
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
    
    public float getRhoRadius() {
        return rhoRadius;
    }
    
    public void setRhoRadius(float rhoRadius) {
        this.rhoRadius = rhoRadius;
        prefs.putFloat("MultiLineClusterTracker.rhoRadius",rhoRadius);
        
    }
    
    public float getThetaRadiusDeg() {
        return (float)Math.toDegrees(thetaRadiusRad);
    }
    
    /** Sets radius of LineClusters; argument is in degrees for user interface */
    public void setThetaRadiusDeg(float thetaRadius) {
        if(thetaRadius<0) thetaRadius=0; else if(thetaRadius>180) thetaRadius=180;
        this.thetaRadiusRad = (float)Math.toRadians(thetaRadius);
        prefs.putFloat("MultiLineClusterTracker.thetaRadiusRad",this.thetaRadiusRad);
        
    }
    
    public int getMinSegmentLength() {
        return minSegmentLength;
    }
    
    public void setMinSegmentLength(int minSegmentLength) {
        this.minSegmentLength = minSegmentLength;
        prefs.putInt("MultiLineClusterTracker.minSegmentLength",minSegmentLength);
    }
    
    public int getMaxSegmentLength() {
        return maxSegmentLength;
    }
    
    public void setMaxSegmentLength(int maxSegmentLength) {
        this.maxSegmentLength = maxSegmentLength;
        prefs.putInt("MultiLineClusterTracker.maxSegmentLength",maxSegmentLength);
    }
    
    public int getMaxSegmentDt() {
        return maxSegmentDt;
    }
    
    public void setMaxSegmentDt(int maxSegmentDt) {
        this.maxSegmentDt = maxSegmentDt;
        prefs.putInt("MultiLineClusterTracker.maxSegmentDt",maxSegmentDt);
    }
    
    public float getMixingFactorPosition() {
        return mixingFactorPosition;
    }
    
    public void setMixingFactorPosition(float mixingFactorPosition) {
        if(mixingFactorPosition>1) mixingFactorPosition=1; else if(mixingFactorPosition<0) mixingFactorPosition=0;
        this.mixingFactorPosition = mixingFactorPosition;
        prefs.putFloat("MultiLineClusterTracker.mixingFactorPosition",mixingFactorPosition);
    }
    
    class LineClusterCanvas extends GLCanvas implements GLEventListener {
        GLU glu=new GLU();
        GLUT glut = new GLUT();
        LineClusterCanvas(GLCapabilities caps){
            super(caps);
            setName("LineClusterCanvas");
            addGLEventListener(this);
            setPreferredSize(new Dimension(400,200));
        }
        public void init(GLAutoDrawable gLAutoDrawable) {
            GL gl = gLAutoDrawable.getGL();
            
            log.info(
                    "INIT GL IS: " + gl.getClass().getName()+"\nGL_VENDOR: " + gl.glGetString(GL.GL_VENDOR)
                    + "\nGL_RENDERER: " + gl.glGetString(GL.GL_RENDERER)
                    + "\nGL_VERSION: " + gl.glGetString(GL.GL_VERSION)
//                + "\nGL_EXTENSIONS: " + gl.glGetString(GL.GL_EXTENSIONS)
                    
                    );
            
            gl.setSwapInterval(1);
            gl.glShadeModel(GL.GL_FLAT);
            
            gl.glClearColor(0,0,0,0f);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            gl.glLoadIdentity();
            
            gl.glRasterPos3f(0,0,0);
            gl.glColor3f(1,1,1);
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,"Initialized display");
        }
        
        public void display(GLAutoDrawable gLAutoDrawable) {
            float[] rgb=new float[4];
            float w=gLAutoDrawable.getWidth();
            float h=gLAutoDrawable.getHeight();
            GL gl=gLAutoDrawable.getGL();
            gl.glClearColor(0,0,0,0f);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            gl.glPushMatrix();
            gl.glPointSize(10);
            gl.glColor3f(1,1,1);
            gl.glBegin(GL.GL_POINTS);
            for(LineCluster c:clusters){
                float rho=(int)((float)c.rhoPixels/chip.getMaxSize()*h);
                float theta=(float)((Math.PI+c.thetaRad)/Math.PI/2*w);
                gl.glColor3fv(c.rgb,0);
                gl.glVertex2f(rho,theta);
            }
            gl.glPopMatrix();
            gl.glEnd();
        }
        
        public void reshape(GLAutoDrawable gLAutoDrawable, int x, int y, int w, int h) {
            GL gl = getGL();
            gl.glMatrixMode(GL.GL_PROJECTION);
            gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
            gl.glMatrixMode(GL.GL_MODELVIEW);
            gl.glLoadIdentity();
            gl.glViewport(0,0,w,h);
        }
        
        public void displayChanged(GLAutoDrawable gLAutoDrawable, boolean b, boolean b0) {
        }
        
        /** Utility method to check for GL errors
         @param g the GL context
         @param glu the GLU used to obtain the error strings
         @param msg an error message to log to e.g., show the context
         */
        public void checkGLError(GL g, GLU glu,String msg){
            int error=g.glGetError();
            int nerrors=10;
            while(error!=GL.GL_NO_ERROR && nerrors--!=0){
                log.warning("GL error number "+error+" "+glu.gluErrorString(error)+" : "+msg);
//             Thread.dumpStack();
                error=g.glGetError();
            }
        }
        
    }
    
//    public boolean isShowLineClusterCanvas() {
//        return showLineClusterCanvas;
//    }
//
//    JFrame lineClusterFrame=null;
//
//    public void setShowLineClusterCanvas(boolean showLineClusterCanvas) {
//        this.showLineClusterCanvas = showLineClusterCanvas;
//        if(showLineClusterCanvas){
//            if(lineClusterCanvas==null){
//                GLCapabilities caps=new GLCapabilities();
//                caps.setDoubleBuffered(true);
//                caps.setHardwareAccelerated(true);
//                caps.setAlphaBits(8);
//                caps.setRedBits(8);
//                caps.setGreenBits(8);
//                caps.setBlueBits(8);
//                lineClusterCanvas=new LineClusterCanvas(caps);
//                lineClusterFrame=new JFrame("LineClusters");
//                lineClusterFrame.setPreferredSize(new Dimension(400,200));
//                lineClusterFrame.getContentPane().add(lineClusterCanvas);
//                lineClusterFrame.pack();
//            }
//            lineClusterFrame.setVisible(true);
//        }else{
//            if(lineClusterCanvas!=null){
//                lineClusterFrame.setVisible(false);
//            }
//        }
//    }
    
    public boolean isShowLineSegments() {
        return showLineSegments;
    }
    
    public void setShowLineSegments(boolean showLineSegments) {
        this.showLineSegments = showLineSegments;
    }
    
    
}
