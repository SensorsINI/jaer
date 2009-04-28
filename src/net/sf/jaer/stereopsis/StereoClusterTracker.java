/*
 * StereoClusterTracker.java
 *
 * Created on July 24, 2006, 7:58 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 24, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.stereopsis;

import net.sf.jaer.chip.*;
import net.sf.jaer.eventprocessing.tracking.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.BinocularEvent;
import com.sun.opengl.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import javax.media.opengl.*;

/**
 * Extends ClusterTracker to track objects in 3-d space. The StereoCluster is extended to include disparity information and the events
 * used to track are BinocularEvents.
 *
 * @author tobi
 */
public class StereoClusterTracker extends RectangularClusterTracker {
    
    private Logger log=Logger.getLogger("StereoClassTracker");
    private float velocityMixingFactor=getPrefs().getFloat("StereoClusterTracker.velocityMixingFactor",.001f);
    {setPropertyTooltip("velocityMixingFactor","fraction by which velocity of cluster is updated by each event");}
    
    StereoChipInterface stereoChip=null;
    /** list of StereoCluster, this field hides the super's 2d image plane Cluster's */
    protected ArrayList<StereoCluster> clusters=new ArrayList<StereoCluster>();
    StereoGeometry geom;
    private boolean logDataEnabled=false;
    PrintStream logStream=null;
    private float[] p=new float[3]; // used for 3d location and velocity computation
    private float[] v=new float[3]; // used for 3d velocity computation
    static final float TICK_SECONDS=1e-6f;
    
    /** Creates a new instance of StereoClusterTracker */
    public StereoClusterTracker(AEChip chip) {
        super(chip);
        if(chip!=null && chip instanceof StereoChipInterface ){
            this.stereoChip=(StereoChipInterface)chip;
        }else{
            log.warning("AEChip "+chip+" is not StereoChipInterface");
        }
        setEnclosedFilter(new StereoTranslateRotate(chip));
        geom=new StereoGeometry(chip);
    }
    
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        track(in);
        return in;
    }
    
    /** returns stereo clusters
     @return list of StereoClusterTracker clusters
     */
    public ArrayList<StereoCluster> getStereoClusters() {
        return this.clusters;
    }
    
    /** @return number of StereoCluster's
     */
    @Override public int getNumClusters(){
        return clusters.size();
    }
    
    /**
     * returns the physically nearest (to observer) visible cluster based on maximum disparity. Not to be confused with method
     *     getNearestCluster(BasicEvent ev) that returns the closest cluster to an event in the image plane.
     *     A cluster is only returned if has received enough support to become visible.
     * 
     * 
     * @return closest cluster, or null if there are no clusters
     */
    public StereoCluster getNearestCluster(){
        if(clusters==null || clusters.size()==0) return null;
        float maxDisparity=Float.NEGATIVE_INFINITY;
        StereoCluster cluster=null;
        for(StereoCluster c:clusters){
            if(c.getDisparity()>maxDisparity){
                cluster=c;
                maxDisparity=c.getDisparity();
            }
        }
        if(cluster!=null && cluster.isVisible()) return cluster; else return null;
    }
        

    /** Given AE, returns first (thus oldest) cluster that event is within.
     The radius of the cluster here depends on whether size scaling is enabled.
     * @param event the event
     * @return cluster that contains event within the cluster's radius, modfied by aspect ratio. null is returned if no cluster is close enough.
     */
    public StereoCluster getFirstContainingCluster(BasicEvent event){
       if(clusters.isEmpty()) return null;
       float minDistance=Float.MAX_VALUE;
        StereoCluster closest=null;
        float currentDistance=0;
        for(StereoCluster c:clusters){
            if((currentDistance=c.distanceTo(event))<minDistance){
                closest=c;
                minDistance=currentDistance;
            }
        }
        
        if(closest!=null && minDistance<=closest.getRadius()) 
            return closest;
        else
            return null;
    }
    
    synchronized private void track(EventPacket<BasicEvent> ae){
        int n=ae.getSize();
        if(n==0) return;
        int maxNumClusters=getMaxNumClusters();
        
        // for each event, see which cluster it is closest to and add it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
//        for(int i=0;i<n;i++){
        for(BasicEvent ev:ae){
//            EventXYType ev=ae.getEvent2D(i);
            StereoCluster closest=getFirstContainingCluster(ev);
            if(closest!=null){
                closest.addEvent(ev);
            }else if(clusters.size()<maxNumClusters){ // start a new cluster
                StereoCluster newCluster=new StereoCluster(ev);
                clusters.add(newCluster);
//                log.info("added "+newCluster);
                
            }
        }
        // prune out old clusters that don't have support
        int clusterLifetimeWithoutSupport=getClusterLifetimeWithoutSupportUs();
        pruneList.clear();
        for(StereoCluster c:clusters){
            if(ae.getLastTimestamp()-c.getLastEventTimestamp()>clusterLifetimeWithoutSupport)
                pruneList.add(c);
        }
        clusters.removeAll(pruneList);
        
        // merge clusters that are too close to each other.
        // this must be done interatively, because merging 4 or more clusters feedforward can result in more clusters than
        // you start with. each time we merge two clusters, we start over, until there are no more merges on iteration.
        
        // for each cluster, if it is close to another cluster then merge them and start over.
        
//        int beforeMergeCount=clusters.size();
        boolean mergePending;
        StereoCluster c1=null,c2=null;
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
                    clusters.add(new StereoCluster(c1,c2));
                }
        }while(mergePending);
        
        // iterate over all clusters, updating them for outside values that user may want
        // update position and velocities in 3d space
        for(StereoCluster c:clusters){
            // update all cluster sizes
            // note that without this following call, clusters maintain their starting size until they are merged with another cluster.
            if(isHighwayPerspectiveEnabled()) c.setRadius(defaultClusterRadius);
            
            // update location in 3d physical space
            geom.compute3dlocationM(c.location.x,c.location.y,c.getDisparity(), p);
            c.location3dm.x=p[0];
            c.location3dm.y=p[1];
            c.location3dm.z=p[2];
            
            // update velocity in 3d physical space
            geom.compute3dVelocityMps(p, c.getDisparity(), c.getVelocityPPS().x,c.getVelocityPPS().y,c.disparityVelocity,v);
            c.velocity3dmps.x=v[0];
            c.velocity3dmps.y=v[1];
            c.velocity3dmps.z=v[2];
            
            // update paths of clusters
            c.updatePath(ae);
        }
        
        if(isLogDataEnabled() && getNumClusters()==1 && clusters.get(0).isVisible() && clusters.get(0).getDisparity()>4){
//            System.out.println(ae.getLastTimestamp()/1e6f+" "+clusters.get(0));
            if(logStream!=null) {
                StereoCluster c=clusters.get(0);
                logStream.println(String.format("%d %f %f %f %f", clusterCounter,ae.getLastTimestamp()/1e6f,c.location3dm.x,c.location3dm.y,c.location3dm.z));
                if(logStream.checkError()) log.warning("eroror logging data");
            }
        }
        
//        if(clusters.size()>beforeMergeCount) throw new RuntimeException("more clusters after merge than before");
        
        
    }
    
    synchronized public void resetFilter() {
        clusters.clear();
    }
    
    /**
     Extends the 2-d cluster to include 2.5-d disparity information. Adding an event
     updates the cluster's disparity value. The events added from left and right eye
     can have different x,y locations depending on misalignment and disparity. The cluster, however, has
     a single location in x,y,disparity space. When an event is added, the cluster disparity value is taken
     account of for determining the distance of the event to the cluster.
     
     */
    public class StereoCluster extends RectangularClusterTracker.Cluster {
        
        /** x,y,z triplet for representing 3d spatial location of cluster. Units are meters. */
        public class Point3d {
            public float x,y,z;
        }
        
        /**
         the disparity of the cluster in pixels,
         i.e. the shift needed to bring one eye's view in registration with the other's
         */
        private float disparity=0;
        
        private float disparityVelocity = 0; // rate of change of disparity in pixels/second
        
        /** location of cluster in 3d space as computed from pixel location and disparity, given chip's StereoGeometry.
         Coordinate frame is centered on bridge of viewers nose and z increases with distance. Units are meters.
         x increses from 0 rightwards from midline in image coordinates (larger to right in image) and y increases upwards in the same way
         */
        public Point3d location3dm=new Point3d();
        
        /** velocity of cluster in 3d space as computed from pixel location and disparity, given chip's StereoGeometry.
         Coordinate frame is centered on bridge of viewers nose and z increases with distance. Units are meters per second.
         x increses from 0 rightwards from midline in image coordinates (larger to right in image) and y increases upwards in the same way
         */
        public Point3d velocity3dmps=new Point3d();
        
        public StereoCluster(){
            setRadius(defaultClusterRadius);
            float hue=random.nextFloat();
            Color color=Color.getHSBColor(hue,1f,1f);
            setColor(color);
            setClusterNumber(clusterCounter++);
        }
        
        /** Constructs a new cluster centered on an event
         @param ev the event
         */
        public StereoCluster(BasicEvent ev){
            this();
            location.x=ev.x;
            location.y=ev.y;
            lastTimestamp=ev.timestamp;
            firstTimestamp=lastTimestamp;
            setRadius(defaultClusterRadius);
//            System.out.println("constructed "+this);
        }
        
        /** Constructs a cluster by merging two clusters */
        public StereoCluster(StereoCluster one, StereoCluster two){
            this();
            // merge locations by just averaging
//            location.x=(one.location.x+two.location.x)/2;
//            location.y=(one.location.y+two.location.y)/2;
            
            // merge locations by average weighted by number of events supporting cluster
            location.x=(one.location.x*one.numEvents+two.location.x*two.numEvents)/(one.numEvents+two.numEvents);
            location.y=(one.location.y*one.numEvents+two.location.y*two.numEvents)/(one.numEvents+two.numEvents);
            
            lastTimestamp=(one.lastTimestamp+two.lastTimestamp)/2;
            numEvents=one.numEvents+two.numEvents;
            firstTimestamp=Math.min(one.firstTimestamp, two.firstTimestamp); // make lifetime the oldest src cluster
            StereoCluster older=one.firstTimestamp<two.firstTimestamp? one:two;
            path=older.path;
            disparity=older.disparity;
            disparityVelocity=older.disparityVelocity;  // don't forget the other fields!!!
            
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
        
        /** adds one BasicEvent event to this cluster, updating its parameters in the process
         @param event the event to add
         */
        @Override public void addEvent(BasicEvent event){
            addEvent((BinocularEvent)event);
        }
        
        /** adds one BinocularEvent to this cluster, updating its parameters in the process
         @param event the event to add
         */
        public void addEvent(BinocularEvent event){
            
            // save location for computing velocity
            float oldx=location.x, oldy=location.y;
            
            float m=mixingFactor,m1=1-m;;
            
            // compute new cluster location by mixing old location with event location by using
            // mixing factor
            
            location.x=(m1*location.x+m*event.x);
            location.y=(m1*location.y+m*event.y);
            float thisEventDisparity=0;
            
            // if we add events from each eye, moviing disparity and location according to each event, then a mismatch
            // in the number of events from each eye will put cluster location closer to this eye; eventually the cluster center
            // will move so far away from one eye that that eye's inputs will be outside the cluster and disparity tracking will be lost.
            
            switch(event.eye){
                case LEFT:
                    thisEventDisparity= 2*(location.x-event.x); // event left of cluster location makes disparity more negative
                    break;
                case RIGHT:
                    thisEventDisparity= -2*(location.x-event.x); // right eye, if event to right of location it makes disparity more positive
                    break;
                default:
                    log.warning("BinocularEvent doesn't have Eye type");
            }
            float oldDisparity=disparity;
            disparity=m1*disparity+m*thisEventDisparity;
            
            // update velocity vector using old and new position only if valid dt
            // and update it by the mixing factors
            
            float dt=TICK_SECONDS*(event.timestamp-lastTimestamp);
            if(dt>0){
                float oldvelx=velocity.x;
                float oldvely=velocity.y;
                
                float velx=(location.x-oldx)/dt;
                float vely=(location.y-oldy)/dt;
                
                float vm1=1-getVelocityMixingFactor();
                velocity.x=vm1*oldvelx+getVelocityMixingFactor()*velx;
                velocity.y=vm1*oldvely+getVelocityMixingFactor()*vely;
                
                float dv=(disparity-oldDisparity)/dt;
                disparityVelocity=vm1*disparityVelocity+getVelocityMixingFactor()*dv;
//                disparityVelocity=vm1*disparityVelocity+velocityMixingFactor*velDisp;
            }
            int prevLastTimestamp=lastTimestamp;
            lastTimestamp=event.timestamp;
            setNumEvents(getNumEvents() + 1);
            instantaneousEventRate=1f/(lastTimestamp-prevLastTimestamp+Float.MIN_VALUE);
        } // addEvent
        
        /** Computes ditance of cluster to event
         @param event the event
         @return distance of this cluster to the event in manhatten (cheap) metric (sum of abs values of x and y distance
         */
        public float distanceTo(BasicEvent event){
            BinocularEvent e=(BinocularEvent)event;
            float dx=0;
            switch(e.eye){
                case LEFT:
                    dx=event.x-(location.x-disparity/2);
                    break;
                case RIGHT:
                    dx=event.x-(location.x+disparity/2);
                    break;
                default:
                    log.warning("BinocularEvent doesn't have Eye type");
            }
            final float dy=event.y-location.y;
//            return Math.abs(dx)+Math.abs(dy);
            return distanceMetric(dx,dy);
//            dx*=dx;
//            dy*=dy;
//            float distance=(float)Math.sqrt(dx+dy);
//            return distance;
        }
        
        /**
         * Computes distance to another cluster
         *
         * @param c the other StereoCluster
         * @return distance of this cluster to the other cluster in pixels
         */
        protected final float distanceTo(StereoCluster c){
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
        
        @Override public String toString(){
            String s=String.format("%s disp=%.1f, dispVel=%.1f, x,y,z=%.1f, %.1f, %.1f, vx,vy,vz=%.1f, %.1f, %.1f",
                    super.toString(),
                    disparity,disparityVelocity,
                    location3dm.x,
                    location3dm.y,
                    location3dm.z,
                    velocity3dmps.x,
                    velocity3dmps.y,
                    velocity3dmps.z
                    );
            return s;
        }
        
        public float getDisparityVelocity() {
            return disparityVelocity;
        }
        
        public void setDisparityVelocity(float disparityVelocity) {
            this.disparityVelocity = disparityVelocity;
        }
        
        /** @see #location3dm
         */
        public Point3d getLocation3dm() {
            return location3dm;
        }
        
        /** @see #location3dm
         */
        public void setLocation3dm(Point3d location3dm) {
            this.location3dm = location3dm;
        }
        
        /** @see #velocity3dmps
         */
        public Point3d getVelocity3dmps() {
            return velocity3dmps;
        }
        
        /** @see #velocity3dmps
         */
        public void setVelocity3dmps(Point3d velocity3dmps) {
            this.velocity3dmps = velocity3dmps;
        }
        
        public float getDisparity() {
            return disparity;
        }
        
        public void setDisparity(float disparity) {
            this.disparity = disparity;
        }
        
    }
    
    synchronized public void annotate(GLAutoDrawable drawable) {
        final float LINE_WIDTH=6f; // in pixels
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in StereoClusterTracker.annotate");
            return;
        }
        float[] rgb=new float[4];
        gl.glPushMatrix();
        try{
            for(StereoCluster c:clusters){
                if(c.isVisible()){
                    int x=(int)c.getLocation().x;
                    int y=(int)c.getLocation().y;
                    
                    
                    int sy=(int)c.getRadius(); // sx sy are (half) size of rectangle
                    int sx=sy;
                    
                    if(isColorClustersDifferentlyEnabled()){
                    }else{
                        float brightness=(float)Math.max(0f,Math.min(1f,c.getLifetime()/fullbrightnessLifetime));
                        Color color=Color.getHSBColor(.5f,1f,brightness);
                        c.setColor(color);
                    }
                    c.getColor().getRGBComponents(rgb);
                    gl.glColor3fv(rgb,0);
                    gl.glLineWidth(LINE_WIDTH);

                    drawBox(gl,x,y,sx,sy,0);
                    
                    // draw left and right disparity clusters
                    
                    
                    // left
                    gl.glColor3f(0,1,0); // green
                    gl.glLineWidth(LINE_WIDTH/2);
                    
                    int x2=(int)(x-c.getDisparity()/2);
                    drawBox(gl,x2,y,sx,sy,0);
                   
                    
                    // red right
                    gl.glColor3f(1,0,0); // green
                    gl.glLineWidth(LINE_WIDTH/2);
                    
                    x2=(int)(x+c.getDisparity()/2);
                    drawBox(gl,x2,y,sx,sy,0);
                    
                    
                    
                    gl.glPointSize(LINE_WIDTH);
                    gl.glBegin(GL.GL_POINTS);
                    {
                        ArrayList<StereoCluster.PathPoint> points=c.getPath();
                        for(Point2D.Float p:points){
                            gl.glVertex2f(p.x,p.y);
                        }
                    }
                    gl.glEnd();
                    
                    // now draw velocity vector
                    if(isUseVelocity()){
                        gl.glBegin(GL.GL_LINES);
                        {
                            gl.glVertex2i(x,y);
                            gl.glVertex2f(x+c.getVelocityPPS().x*1e6f,y+c.getVelocityPPS().y*1e6f);
                        }
                        gl.glEnd();
                    }
                    
                    int font = GLUT.BITMAP_TIMES_ROMAN_24;
                    GLUT glut=chip.getCanvas().getGlut();
                    gl.glColor3f(1,1,1);
                    
                    gl.glRasterPos3f(c.location.x,c.location.y,0);
                    glut.glutBitmapString(font, Integer.toString(c.getClusterNumber()));
                    
                } // visible cluster
            } // clusters
            
            if(getNearestCluster()!=null){
                StereoCluster c=getNearestCluster();
                int font = GLUT.BITMAP_TIMES_ROMAN_24;
                GLUT glut=chip.getCanvas().getGlut();
                gl.glColor3f(1,1,1);
                
                gl.glRasterPos3f(1,13,0);
                glut.glutBitmapString(font, String.format("x,y,z=%5.2f, %5.2f, %5.2f", c.location3dm.x, c.location3dm.y, c.location3dm.z));
                
                gl.glRasterPos3f(1,8,0);
                glut.glutBitmapString(font, String.format("vx,vy,vz=%5.2f, %5.2f, %5.2f", c.velocity3dmps.x, c.velocity3dmps.y, c.velocity3dmps.z));
                
                gl.glRasterPos3f(1,3,0);
                glut.glutBitmapString(font, String.format("disp=%6.1f dispVel=%6.1f", c.getDisparity(), c.disparityVelocity));
            } // text for closest cluster only
        }catch(java.util.ConcurrentModificationException e){
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning(e.getMessage());
        }
        gl.glPopMatrix();
    }
    
    
    synchronized public boolean isLogDataEnabled() {
        return logDataEnabled;
    }
    
    synchronized public void setLogDataEnabled(boolean logDataEnabled) {
        this.logDataEnabled = logDataEnabled;
        if(!logDataEnabled) {
            logStream.flush();
            logStream.close();
            logStream=null;
        }else{
            try{
                logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File("stereoClusterTrackerData.txt"))));
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public float getVelocityMixingFactor() {
        return velocityMixingFactor;
    }

    public void setVelocityMixingFactor(float velocityMixingFactor) {
        if(velocityMixingFactor>1) velocityMixingFactor=1;
        this.velocityMixingFactor = velocityMixingFactor;
        getPrefs().putFloat("StereoClusterTracker.velocityMixingFactor",velocityMixingFactor);
    }
    
}
