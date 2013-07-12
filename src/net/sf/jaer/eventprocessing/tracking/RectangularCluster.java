///*
// * To change this template, choose Tools | Templates
// * and open the template in the editor.
// */
//package net.sf.jaer.eventprocessing.tracking;
//
//import java.util.List;
//import java.util.Observable;
//import net.sf.jaer.aemonitor.AEConstants;
//import net.sf.jaer.chip.*;
//import net.sf.jaer.eventio.AEDataFile;
//import net.sf.jaer.eventprocessing.EventFilter2D;
//import net.sf.jaer.event.*;
//import net.sf.jaer.event.EventPacket;
//import net.sf.jaer.eventprocessing.tracking.ClusterPathPoint;
//import net.sf.jaer.graphics.*;
//import java.awt.*;
////import ch.unizh.ini.caviar.util.PreferencesEditor;
//import java.awt.geom.*;
//import java.io.*;
//import java.text.DateFormat;
//import java.text.SimpleDateFormat;
//import java.util.*;
//import javax.media.opengl.GL;
//import javax.media.opengl.GLAutoDrawable;
//import net.sf.jaer.util.filter.LowpassFilter;
//import java.awt.geom.Point2D;
//import net.sf.jaer.util.filter.LowpassFilter;
///**
// *
// * @author tobi
// *
// * This is part of jAER
//<a href="http://jaerproject.net/">jaerproject.net</a>,
//licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
// */
///** The basic object that is tracked, which is a rectangular cluster with (optionally) variable size, aspect ratio, and angle.
// *
// */
//public class RectangularCluster implements ClusterInterface{
//    private final int MIN_DT_FOR_VELOCITY_UPDATE = 10;
//    /** location of cluster in pixels */
//    public Point2D.Float location = new Point2D.Float(); // location in chip pixels
//    private Point2D.Float birthLocation = new Point2D.Float(); // birth location of cluster
//    private Point2D.Float lastPacketLocation = new Point2D.Float(); // location at end of last packet, used for movement sample
//    /** velocityPPT of cluster in pixels/tick, where tick is timestamp tick (usually microseconds) */
//    protected Point2D.Float velocityPPT = new Point2D.Float(); // velocityPPT in chip pixels/tick
//    private Point2D.Float velocityPPS = new Point2D.Float(); // cluster velocityPPT in pixels/second
//    private boolean velocityValid = false; // used to flag invalid or uncomputable velocityPPT
//    private LowpassFilter vxFilter = new LowpassFilter(), vyFilter = new LowpassFilter();
//    final float VELPPS_SCALING = 1e6f / AEConstants.TICK_DEFAULT_US;
////        public float tauMsVelocity=50; // LP filter time constant for velocityPPT change
////        private LowpassFilter velocityFilter=new LowpassFilter();
//    private float radius; // in chip chip pixels
////        private float mass; // a cluster has a mass correspoding to its support - the higher the mass, the harder it is to change its velocityPPT
//    private float aspectRatio, radiusX, radiusY;
//    /** Angle of cluster in radians with zero being horizontal and CCW > 0. sinAngle and cosAngle are updated when instantaneousAngle is updated. */
//    private float angle = 0, cosAngle = 1, sinAngle = 0;
//    int hitEdgeTime = 0;
//    private int pathLength;
//    protected ArrayList<ClusterPathPoint> path = new ArrayList<ClusterPathPoint>(pathLength);
//   private int numVelocityPoints;
//     private RollingVelocityFitter velocityFitter = new RollingVelocityFitter(path,numVelocityPoints);
//    /** Rendered color of cluster. */
//    protected Color color = null;
////        ArrayList<EventXYType> events=new ArrayList<EventXYType>();
//    /** Number of events collected by this cluster.*/
//    protected int numEvents = 0;
//    /** Num events from previous update of cluster list. */
//    protected int previousNumEvents = 0; // total number of events and number at previous packet
//    /** First and last timestamp of cluster. <code>firstEventTimestamp</code> is updated when cluster becomes visible.
//     * <code>lastEventTimestamp</code> is the last time the cluster was touched either by an event or by
//     * some other timestamped update, e.g. {@link #updateClusterList(net.sf.jaer.event.EventPacket, int) }.
//     * @see #isVisible()
//     */
//    protected int lastEventTimestamp, firstEventTimestamp;
//    /** The "mass" of the cluster is the weighted number of events it has collected.
//     * The mass decays over time and is incremented by one by each collected event.
//     * The mass decays with a first order time constant of clusterLifetimeWithoutSupportUs in us.
//     */
//    private float mass = 1;
//    /** This is the last time in timestamp ticks that the cluster was updated, either by an event
//     * or by a regular update such as {@link #updateClusterLocations(int)}. This time can be used to
//     * compute postion updates given a cluster velocityPPT and time now.
//     */
//    protected int lastUpdateTime;
//    /** events/tick event rate for last two events. */
//    protected float instantaneousEventRate; // in events/tick
//    /** Average event rate as computed using mixingFactor.
//     * @see #mixingFactor
//     */
//    private float avgEventRate = 0;
//    protected float instantaneousISI; // ticks/event
//    private float avgISI;
//    /** assigned to be the absolute number of the cluster that has been created. */
//    private int clusterNumber;
//    /** average (mixed using mixingFactor) distance of events from cluster center, a measure of actual cluster size. */
//    private float averageEventDistance, averageEventXDistance, averageEventYDistance;
//    protected float distanceToLastEvent = Float.POSITIVE_INFINITY;
//    protected float xDistanceToLastEvent = Float.POSITIVE_INFINITY, yDistanceToLastEvent = Float.POSITIVE_INFINITY;
//    /** Flag which is set true (forever) once a cluster has first obtained sufficient support. */
//    protected boolean hasObtainedSupport = false;
//    private float ASPECT_RATIO_MAX_DYNAMIC_ANGLE_ENABLED=RectangularClusterTracker.ASPECT_RATIO_MAX_DYNAMIC_ANGLE_ENABLED;
//    private float ASPECT_RATIO_MIN_DYNAMIC_ANGLE_ENABLED=RectangularClusterTracker.ASPECT_RATIO_MIN_DYNAMIC_ANGLE_ENABLED;
//    private float ASPECT_RATIO_MAX_DYNAMIC_ANGLE_DISABLED=RectangularClusterTracker.ASPECT_RATIO_MAX_DYNAMIC_ANGLE_DISABLED;
//    private float ASPECT_RATIO_MIN_DYNAMIC_ANGLE_DISABLED=RectangularClusterTracker.ASPECT_RATIO_MIN_DYNAMIC_ANGLE_DISABLED;
//    private float MAX_SCALE_RATIO=RectangularClusterTracker.MAX_SCALE_RATIO;
//
//    private float[] rgb = new float[ 4 ];
//    private boolean enableClusterExitPurging;
//    private float clusterLifetimeWithoutSupportUs;
//    private int sizey;
//    private int sizex;
//    private float defaultClusterRadius;
//    private static final Random random=new Random();
//    private static  int clusterCounter=0; // global cluster counter
//    private final float velocityTauMs;
//    private boolean showClusterVelocity;
//    private float velocityVectorScaling;
//    private boolean highwayPerspectiveEnabled;
//    private final float VELOCITY_VECTOR_SCALING;
//    private boolean showClusterEps;
//    private boolean showClusterNumber;
//    static GLUT glut=new GLUT();
//    private boolean showClusterMass;
//    private boolean dynamicAngleEnabled;
//    private final boolean growMergedSizeEnabled;
//    private final float mixingFactor;
//    private boolean useVelocity;
//    private float predictiveVelocityFactor;
//    private boolean dynamicSizeEnabled;
//    private boolean dynamicAspectRatioEnabled;
//     private int thresholdEventsForVisibleCluster;
//    private boolean pathsEnabled;
//    private double thresholdVelocityForVisibleCluster;
//    private boolean logDataEnabled;
//    private float maxSize;
//    private boolean colorClustersDifferentlyEnabled;
//    private boolean useOnePolarityOnlyEnabled;
//    private boolean useOffPolarityOnlyEnabled;
//    private final boolean initializeVelocityToAverage;
//    private Point2D.Float averageVelocityPPT = new Point2D.Float();
//
//    public RectangularCluster(RectangularClusterTracker tracker){
//        this();
//      rgb = new float[ 4 ];
//   enableClusterExitPurging=tracker.isEnableClusterExitPurging();
//    clusterLifetimeWithoutSupportUs=tracker.getClusterLifetimeWithoutSupportUs();
//    sizey=tracker.getChip().getSizeY();
//    sizex=tracker.getChip().getSizeX();
//    defaultClusterRadius=tracker.getClusterSize();
//    clusterCounter=0; // global cluster counter
//    velocityTauMs=tracker.getVelocityTauMs();
//    showClusterVelocity=tracker.isShowClusterVelocity();
//    velocityVectorScaling=tracker.getVelocityVectorScaling();
//      highwayPerspectiveEnabled=tracker.isHighwayPerspectiveEnabled();
//      showClusterEps=tracker.isShowClusterEps();
//    showClusterNumber=tracker.isShowClusterNumber();
//   showClusterMass=tracker.isShowClusterMass();
//      dynamicAngleEnabled=tracker.isDynamicAngleEnabled();
//       growMergedSizeEnabled=tracker.growMergedSizeEnabled;
//       mixingFactor=tracker.mixingFactor;
//      useVelocity=tracker.isUseVelocity();
//    predictiveVelocityFactor=tracker.getPredictiveVelocityFactor();
//    dynamicSizeEnabled=tracker.isDynamicSizeEnabled();
//    dynamicAspectRatioEnabled;
//       thresholdEventsForVisibleCluster;
//    pathsEnabled;
//      thresholdVelocityForVisibleCluster;
//    logDataEnabled;
//      maxSize;
//    colorClustersDifferentlyEnabled;
//    useOnePolarityOnlyEnabled;
//    useOffPolarityOnlyEnabled;
//       initializeVelocityToAverage;
//      averageVelocityPPT;
//
//    }
//
//      /** Constructs a default cluster. */
//    public RectangularCluster (){
//        setRadius(defaultClusterRadius);
//        float hue = random.nextFloat();
//        Color c = Color.getHSBColor(hue,1f,1f);
//        setColor(c);
//        setClusterNumber(clusterCounter++);
//        aspectRatio=// TODO
//        vxFilter.setTauMs(velocityTauMs);
//        vyFilter.setTauMs(velocityTauMs);
//        if ( initializeVelocityToAverage ){
//            velocityPPT.x = averageVelocityPPT.x;
//            velocityPPT.y = averageVelocityPPT.y;
//            velocityValid = true;
//        }
//    }
//
//
//       /** Constructs a cluster at the location of an event.
//     * The numEvents, location, birthLocation, first and last timestamps are set.
//     * The radius is set to defaultClusterRadius.
//     *
//     * @param ev the event.
//     */
//    public RectangularCluster (BasicEvent ev){
//        this();
//        location.x = ev.x;
//        location.y = ev.y;
//        birthLocation.x = ev.x;
//        birthLocation.y = ev.y;
//        lastPacketLocation.x = ev.x;
//        lastPacketLocation.y = ev.y;
//        lastEventTimestamp = ev.timestamp;
//        firstEventTimestamp = lastEventTimestamp;
//        lastUpdateTime = ev.timestamp;
//        numEvents = 1;
//        setRadius(defaultClusterRadius);
//    }
//
//    /** Constructs a cluster by merging two clusters.
//     * All parameters of the resulting cluster should be reasonable combinations of the
//     * source cluster parameters.
//     * For example, the merged location values are weighted
//     * by the mass of events that have supported each
//     * source cluster, so that older clusters weigh more heavily
//     * in the resulting cluster location. Subtle bugs or poor performance can result
//     * from not properly handling the merging of parameters.
//     *
//     * @param one the first cluster
//     * @param two the second cluster
//     */
//    public RectangularCluster (RectangularCluster one,RectangularCluster two){
//        this();
//        // merge locations by just averaging
////            location.x=(one.location.x+two.location.x)/2;
////            location.y=(one.location.y+two.location.y)/2;
//
//        RectangularCluster stronger = one.mass > two.mass ? one : two; //one.firstEventTimestamp < two.firstEventTimestamp ? one : two;
////            RectangularCluster older=one.numEvents>two.numEvents? one:two;
//        clusterNumber = stronger.clusterNumber;
//        // merge locations by average weighted by mass of events supporting each cluster
//        mass = one.mass + two.mass;
//        numEvents = one.numEvents + two.numEvents;
//        // change to older for location to avoid discontinuities in postion
//        location.x = stronger.location.x; //(one.location.x * one.mass + two.location.x * two.mass) / (mass);
//        location.y = stronger.location.y; // (one.location.y * one.mass + two.location.y * two.mass) / (mass);
//        angle = stronger.angle;
//        cosAngle = stronger.cosAngle;
//        sinAngle = stronger.sinAngle;
//        averageEventDistance = ( one.averageEventDistance * one.mass + two.averageEventDistance * two.mass ) / mass;
//        averageEventXDistance = ( one.averageEventXDistance * one.mass + two.averageEventXDistance * two.mass ) / mass;
//        averageEventYDistance = ( one.averageEventYDistance * one.mass + two.averageEventYDistance * two.mass ) / mass;
//
//        lastEventTimestamp = one.lastEventTimestamp > two.lastEventTimestamp ? one.lastEventTimestamp : two.lastEventTimestamp;
//        lastUpdateTime = lastEventTimestamp;
//        lastPacketLocation.x = stronger.location.x;
//        lastPacketLocation.y = stronger.location.y;
//        firstEventTimestamp = stronger.firstEventTimestamp; // make lifetime the oldest src cluster
//        path = stronger.path;
//        birthLocation = stronger.birthLocation;
//        velocityFitter = stronger.velocityFitter;
//        velocityPPT.x = stronger.velocityPPT.x;
//        velocityPPT.y = stronger.velocityPPT.y;
//        velocityPPS.x = stronger.velocityPPS.x;
//        velocityPPS.y = stronger.velocityPPS.y;
//        velocityValid = stronger.velocityValid;
//        vxFilter = stronger.vxFilter;
//        vyFilter = stronger.vyFilter;
//        avgEventRate = stronger.avgEventRate;
//        avgISI = stronger.avgISI;
//        hasObtainedSupport = stronger.hasObtainedSupport;
//        setAspectRatio(stronger.getAspectRatio());
//
////            Color c1=one.getColor(), c2=two.getColor();
//        setColor(stronger.getColor());
////            System.out.println("merged "+one+" with "+two);
//        //the radius should increase
////            setRadius((one.getRadius()+two.getRadius())/2);
//        if ( growMergedSizeEnabled ){
//            float R = ( one.getRadius() + two.getRadius() ) / 2;
//            setRadius(R + mixingFactor* R);
//        } else{
//            setRadius(stronger.getRadius());
//        }
//    }
//
//    /** Creates a new RectangularCluster using the event and generates a new output event which points back to the RectangularCluster.
//     *
//     * @param ev the event to center the cluster on.
//     * @param outItr used to generate the new event pointing back to the cluster.
//     */
//    private RectangularCluster (BasicEvent ev,OutputEventIterator outItr){
//        this(ev);
//        RectangularClusterTrackerEvent oe = (RectangularClusterTrackerEvent)outItr.nextOutput();
//        oe.copyFrom(ev);
////            oe.setX((short) getLocation().x);
////            oe.setY((short) getLocation().y);
//        oe.setCluster(this);
//    }
//
//    /** Computes and returns {@link #mass} at time t, using the last time an event hit this cluster
//     * and
//     * the {@link #clusterLifetimeWithoutSupportUs}. Does not change the mass.
//     *
//     * @param t timestamp now.
//     * @return the mass.
//     */
//    protected float getMassNow (int t){
//        float m = mass * (float)Math.exp(( (float)( lastEventTimestamp - t ) ) / clusterLifetimeWithoutSupportUs);
//        return m;
//    }
//
//    /**
//     * The "mass" of the cluster is the weighted number of events it has collected.
//     * The mass decays over time and is incremented by one by each collected event.
//     * The mass decays with a first order time constant of clusterLifetimeWithoutSupportUs in us.
//     * @return the mass
//     */
//    public float getMass (){
//        return mass;
//    }
//
//    /**Increments mass of cluster by one after decaying it away since the {@link #lastEventTimestamp} according
//    to exponential decay with time constant {@link #clusterLifetimeWithoutSupportUs}.
//    @param event used for event timestamp.
//     */
//    protected void incrementMass (BasicEvent event){
//        mass = 1 + mass * (float)Math.exp(( (float)lastEventTimestamp - event.timestamp ) / clusterLifetimeWithoutSupportUs);
//    }
//
//    /** Returns true if the cluster center is outside the array or if this test is enabled and if the
//    cluster has hit the edge of the array and has been there at least the
//    minimum time for support.
//    @return true if cluster has hit edge for long
//    enough (getClusterLifetimeWithoutSupportUs) and test enableClusterExitPurging
//     */
//    private boolean hasHitEdge (){
//        int lx = (int)location.x, ly = (int)location.y;
//
//        if ( lx < 0 || lx > sizex || ly < 0 || ly > sizey ){
//            return true;  // always prune if cluster is outside array, e.g. from velocityPPT prediction
//        }
//        if ( !enableClusterExitPurging ){
//            return false;
//        }
//        if ( lx < radiusX || lx > sizex - radiusX || ly < radiusY || ly > sizey - radiusY ){
//            if ( hitEdgeTime == 0 ){
//                hitEdgeTime = lastEventTimestamp;
//                return false;
//            } else{
//                if ( lastEventTimestamp - hitEdgeTime > 0/* getClusterLifetimeWithoutSupportUs()*/ ){
//                    return true;
//                } else{
//                    return false;
//                }
//            }
//        }
//        return false;
//    }
//
//    /** Total number of events collected by this cluster.
//     * @return the numEvents
//     */
//    public int getNumEvents (){
//        return numEvents;
//    }
//
//    /** Sets count of events.
//     *
//     * @param numEvents the numEvents to set
//     */
//    public void setNumEvents (int numEvents){
//        this.numEvents = numEvents;
//    }
//
//    /**
//     * RectangularCluster velocityPPT in pixels/timestamp tick as a vector. Velocity values are set during cluster upate.
//     *
//     * @return the velocityPPT in pixels per timestamp tick.
//     * @see #getVelocityPPS()
//     */
//    public Point2D.Float getVelocityPPT (){
//        return velocityPPT;
//    }
//
//    /**
//     * The location of the cluster at the end of the previous packet.
//     * Can be used to measure movement of cluster during this
//     * packet.
//     * @return the lastPacketLocation.
//     */
//    public Point2D.Float getLastPacketLocation (){
//        return lastPacketLocation;
//    }
//
//    /** Updates cluster and generates new output event pointing to the cluster.
//     *
//     * @param ev the event.
//     * @param outItr the output iterator; used to generate new output event pointing to cluster.
//     */
//    private void addEvent (BasicEvent ev,OutputEventIterator outItr){
//        addEvent(ev);
//        if ( !isVisible() ){
//            return;
//        }
//        RectangularClusterTrackerEvent oe = (RectangularClusterTrackerEvent)outItr.nextOutput();
//        oe.copyFrom(ev);
////            oe.setX((short) getLocation().x);
////            oe.setY((short) getLocation().y);
//        oe.setCluster(this);
//    }
//
//
//    /** Overrides default hashCode to return {@link #clusterNumber}. This overriding
//     * allows for storing clusters in lists and checking for them by their clusterNumber.
//     *
//     * @return clusterNumber
//     */
//    @Override
//    public int hashCode (){
//        return clusterNumber;
//    }
//
//    /** Two clusters are equal if their {@link #clusterNumber}'s are equal.
//     *
//     * @param obj another RectangularCluster.
//     * @return true if equal.
//     */
//    @Override
//    public boolean equals (Object obj){ // derived from http://www.geocities.com/technofundo/tech/java/equalhash.html
//        if ( this == obj ){
//            return true;
//        }
//        if ( ( obj == null ) || ( obj.getClass() != this.getClass() ) ){
//            return false;
//        }
//        // object must be Test at this point
//        RectangularCluster test = (RectangularCluster)obj;
//        return clusterNumber == test.clusterNumber;
//    }
//
//     /** Draws this cluster using OpenGL.
//     *
//     * @param drawable area to draw this.
//     */
//    public void draw (GLAutoDrawable drawable){
//        final float BOX_LINE_WIDTH = 2f; // in chip
//        final float PATH_POINT_SIZE = 4f;
//        final float VEL_LINE_WIDTH = 4f;
//        GL2 gl = drawable.getGL().getGL2();
//        int x = (int)getLocation().x;
//        int y = (int)getLocation().y;
//
//
//        int sy = (int)radiusY; // sx sy are (half) size of rectangle
//        int sx = (int)radiusX;
//
//        // set color and line width of cluster annotation
//        setColorAutomatically();
//        getColor().getRGBComponents(rgb);
//        if ( isVisible() ){
//            gl.glColor3fv(rgb,0);
//            gl.glLineWidth(BOX_LINE_WIDTH);
//        } else{
//            gl.glColor3f(.3f,.3f,.3f);
//            gl.glLineWidth(.5f);
//        }
//
//        // draw cluster rectangle
//        drawBox(gl,x,y,sx,sy,getAngle());
//
//        gl.glPointSize(PATH_POINT_SIZE);
//        gl.glBegin(GL.GL_POINTS);
//        {
//            ArrayList<ClusterPathPoint> points = getPath();
//            for ( Point2D.Float p:points ){
//                gl.glVertex2f(p.x,p.y);
//            }
//        }
//        gl.glEnd();
//
//        // now draw velocityPPT vector
//        if ( showClusterVelocity ){
//            gl.glLineWidth(VEL_LINE_WIDTH);
//            gl.glBegin(GL2.GL_LINES);
//            {
//                gl.glVertex2i(x,y);
//                gl.glVertex2f(x + getVelocityPPT().x * VELOCITY_VECTOR_SCALING * velocityVectorScaling,y + getVelocityPPT().y * VELOCITY_VECTOR_SCALING * velocityVectorScaling);
//            }
//            gl.glEnd();
//        }
//        // text annoations on clusters, setup
//        final int font = GLUT.BITMAP_HELVETICA_18;
//        gl.glColor3f(1,1,1);
//        gl.glRasterPos3f(location.x,location.y,0);
//
//        // draw radius text
////                            glut.glutBitmapString(font, String.format("%.1f", getRadiusCorrectedForPerspective()));
//
//        // annotate with instantaneousAngle (debug)
////                        glut.glutBitmapString(font, String.format("%.0fdeg", instantaneousAngle*180/Math.PI));
//
//        //annotate the cluster with the event rate computed as 1/(avg ISI) in keps
//        if ( showClusterEps ){
//            float keps = getAvgEventRate() / ( AEConstants.TICK_DEFAULT_US ) * 1e3f;
//            glut.glutBitmapString(font,String.format("eps=%.0fk ",keps));
//        }
//
//        // annotate the cluster with hash ID
//        if ( showClusterNumber ){
//            glut.glutBitmapString(font,String.format("#=%d ",hashCode()));
//        }
//
//        //annotate the cluster with the velocityPPT in pps
//        if ( showClusterVelocity ){
//            Point2D.Float velpps = getVelocityPPS();
//            glut.glutBitmapString(font,String.format("v=%.0f,%.0fpps ",velpps.x,velpps.y));
//        }
//
//        if ( showClusterMass ){
//            glut.glutBitmapString(font,String.format("m=%.1f ",getMassNow(lastUpdateTime)));
//        }
//    }
//    protected void drawBox(GL2 gl, int x, int y, int sx, int sy, float angle) {
//        final float r2d = (float) (180 / Math.PI);
//        gl.glPushMatrix();
//        gl.glTranslatef(x, y, 0);
//        gl.glRotatef(angle * r2d, 0, 0, 1);
//        gl.glBegin(GL2.GL_LINE_LOOP);
//        {
//            gl.glVertex2i(-sx, -sy);
//            gl.glVertex2i(+sx, -sy);
//            gl.glVertex2i(+sx, +sy);
//            gl.glVertex2i(-sx, +sy);
//        }
//        gl.glEnd();
//        if (dynamicAngleEnabled) {
//            gl.glBegin(GL2.GL_LINES);
//            {
//                gl.glVertex2i(0, 0);
//                gl.glVertex2i(sx, 0);
//            }
//            gl.glEnd();
//        }
//        gl.glPopMatrix();
//    }
//
//    /**
//     * Computes a geometrical scale factor based on location of a point relative to the vanishing point.
//     * If a pixel has been selected (we ask the renderer) then we compute the perspective from this vanishing point, otherwise
//     * it is the top middle pixel.
//     * @param p a point with 0,0 at lower left corner
//     * @return scale factor, which grows linearly to 1 at botton of scene
//     */
//    final float getPerspectiveScaleFactor (Point2D.Float p){
//        if ( !highwayPerspectiveEnabled ){
//            return 1;
//        }
//        final float MIN_SCALE = 0.1f; // to prevent microclusters that hold only a single pixel
//        if ( !renderer.isPixelSelected() ){
//            float scale = 1f - ( p.y / chip.getSizeY() ); // yfrac grows to 1 at bottom of image
//            if ( scale < MIN_SCALE ){
//                scale = MIN_SCALE;
//            }
//            return scale;
//        } else{
//            // scale is MIN_SCALE at vanishing point or above and grows linearly to 1 at max size of chip
//            int size = chip.getMaxSize();
//            float d = (float)p.distance(renderer.getXsel(),renderer.getYsel());
//            float scale = d / size;
//            if ( scale < MIN_SCALE ){
//                scale = MIN_SCALE;
//            }
//            return scale;
//        }
//    }
//
//
//    public int getLastEventTimestamp (){
////            EventXYType ev=events.get(events.size()-1);
////            return ev.timestamp;
//        return lastEventTimestamp;
//    }
//
//    /** updates cluster by one event. The cluster velocityPPT is updated at the filterPacket level after all events
//    in a packet are added.
//    @param event the event
//     */
//    public void addEvent (BasicEvent event){
//        if ( ( event instanceof TypedEvent ) ){
//            TypedEvent e = (TypedEvent)event;
//            if ( useOnePolarityOnlyEnabled ){
//                if ( useOffPolarityOnlyEnabled ){
//                    if ( e.type == 1 ){
//                        return;
//                    }
//                } else{
//                    if ( e.type == 0 ){
//                        return;
//                    }
//                }
//            }
//        }
//
//        //Increments mass of cluster by one after decaying it away since the lastEventTimestamp according
//        // to exponential decay with time constant clusterLifetimeWithoutSupportUs.
//        incrementMass(event);
//
//        float m = mixingFactor, m1 = 1 - m;
//
//        float dt = event.timestamp - lastUpdateTime; // this timestamp may be bogus if it goes backwards in time, we need to check it later
//
//        // if useVelocity is enabled, first update the location using the measured estimate of velocityPPT.
//        // this will give predictor characteristic to cluster because cluster will move ahead to the predicted location of
//        // the present event
//        // don't do this now because the location is already updated by updateClusterLocations() TODO
//        if ( useVelocity && dt > 0 && velocityFitter.valid ){
//            location.x = location.x + predictiveVelocityFactor * dt * velocityPPT.x;
//            location.y = location.y + predictiveVelocityFactor * dt * velocityPPT.y;
//        }
//
//        // compute new cluster location by mixing old location with event location by using
//        // mixing factor.
//
//        if ( ( event instanceof OrientationEvent ) ){
//            // if event is an orientation event, use the orientation to only move the cluster in a direction perpindicular to
//            // the estimated orientation
//            OrientationEvent eout = (OrientationEvent)event;
//            OrientationEvent.UnitVector d = OrientationEvent.unitVectors[( eout.orientation + 2 ) % 4];
//            //calculate projection
//            float eventXCentered = event.x - location.x;
//            float eventYCentered = event.y - location.y;
//            float aDotB = ( d.x * eventXCentered ) + ( d.y * eventYCentered );
//            //float aDotA = (d.x * d.x) + (d.y *d.y);
//            float division = aDotB; /// aDotA;
//            float newX = ( division * d.x ) + location.x;
//            float newY = ( division * d.y ) + location.y;
//
//            location.x = ( m1 * location.x + m * newX );
//            location.y = ( m1 * location.y + m * newY );
//        } else{
//            // otherwise, move the cluster in the direction of the event.
//            location.x = ( m1 * location.x + m * event.x );
//            location.y = ( m1 * location.y + m * event.y );
//        }
//
//
//        lastUpdateTime = event.timestamp;
//
//        // velocityPPT of cluster is updated here as follows
//        // 1. instantaneous velocityPPT is computed from old and new cluster locations and dt
//        // 2. new velocityPPT is computed by mixing old velocityPPT with instaneous new velocityPPT using velocityMixingFactor
//        // Since an event may pull the cluster back in the opposite direction it is moving, this measure is likely to be quite noisy.
//        // It would be better to use the saved cluster locations after each packet is processed to perform an online regression
//        // over the history of the cluster locations. Therefore we do not use the following anymore.
////            if(useVelocity && dt>0){
////                // update velocityPPT vector using old and new position only if valid dt
////                // and update it by the mixing factors
////                float oldvelx=velocityPPT.x;
////                float oldvely=velocityPPT.y;
////
////                float velx=(location.x-oldx)/dt; // instantaneous velocityPPT for this event in pixels/tick (pixels/us)
////                float vely=(location.y-oldy)/dt;
////
////                float vm1=1-velocityMixingFactor;
////                velocityPPT.x=vm1*oldvelx+velocityMixingFactor*velx;
////                velocityPPT.y=vm1*oldvely+velocityMixingFactor*vely;
////                velocityPPS.x=velocityPPT.x*VELPPS_SCALING;
////                velocityPPS.y=velocityPPT.y*VELPPS_SCALING;
////            }
//
//        int prevLastTimestamp = lastEventTimestamp;
//        lastEventTimestamp = event.timestamp;
//        numEvents++;
//        instantaneousISI = lastEventTimestamp - prevLastTimestamp;
//        if ( instantaneousISI <= 0 ){
//            instantaneousISI = 1;
//        }
//        avgISI = m1 * avgISI + m * instantaneousISI;
//        instantaneousEventRate = 1f / instantaneousISI;
//        avgEventRate = m1 * avgEventRate + m * instantaneousEventRate;
//
//        averageEventDistance = m1 * averageEventDistance + m * distanceToLastEvent;
//        averageEventXDistance = m1 * averageEventXDistance + m * xDistanceToLastEvent;
//        averageEventYDistance = m1 * averageEventYDistance + m * yDistanceToLastEvent;
//
//        // if scaling is enabled, now scale the cluster size
//        scale(event);
//
//    }
//
//    /** sets the cluster radius according to distance of event from cluster center, but only if dynamicSizeEnabled or dynamicAspectRatioEnabled.
//     * @param event the event to scale with
//     */
//    private final void scale (BasicEvent event){
//        if ( dynamicSizeEnabled ){
//            float dist = distanceTo(event);
//            float oldr = radius;
//            float newr = ( 1 - mixingFactor ) * oldr + dist * mixingFactor;
//            float f;
//            if ( newr > ( f = defaultClusterRadius * MAX_SCALE_RATIO ) ){
//                newr = f;
//            } else if ( newr < ( f = defaultClusterRadius / MAX_SCALE_RATIO ) ){
//                newr = f;
//            }
//            setRadius(newr);
//        }
//        if ( dynamicAspectRatioEnabled ){
//            // TODO aspect ratio must also account for dynamicAngleEnabled.
//            float dx = ( event.x - location.x );
//            float dy = ( event.y - location.y );
//            float dw = dx * cosAngle + dy * sinAngle; // dot dx,dy with unit vector of instantaneousAngle of cluster
//            float dh = -dx * sinAngle + dy * cosAngle; // and with normal to unit vector
//            float oldAspectRatio = getAspectRatio();
//            float newAspectRatio = Math.abs(dh / dw);
//            if ( dynamicAngleEnabled ){
//                if ( newAspectRatio > ASPECT_RATIO_MAX_DYNAMIC_ANGLE_ENABLED ){
//                    newAspectRatio = ASPECT_RATIO_MAX_DYNAMIC_ANGLE_ENABLED;
//                } else if ( newAspectRatio < ASPECT_RATIO_MIN_DYNAMIC_ANGLE_ENABLED ){
//                    newAspectRatio = ASPECT_RATIO_MIN_DYNAMIC_ANGLE_ENABLED;
//                }
//            } else{
//                if ( newAspectRatio > ASPECT_RATIO_MAX_DYNAMIC_ANGLE_DISABLED ){
//                    newAspectRatio = ASPECT_RATIO_MAX_DYNAMIC_ANGLE_DISABLED;
//                } else if ( newAspectRatio < ASPECT_RATIO_MIN_DYNAMIC_ANGLE_DISABLED ){
//                    newAspectRatio = ASPECT_RATIO_MIN_DYNAMIC_ANGLE_DISABLED;
//                }
//            }
//            setAspectRatio(( 1 - mixingFactor ) * oldAspectRatio + mixingFactor * newAspectRatio);
//        }
//        if ( dynamicAngleEnabled ){
//            // dynamicall rotates cluster to line it up with edge.
//            // the cluster instantaneousAngle is defined so horizontal edges have instantaneousAngle 0 or +/-PI, vertical have +/- PI/2.
//            // instantaneousAngle increases CCW from 0 for rightward from center of cluster events.
//            //
//            // awkwardness here is that events will fall on either side around center of cluster.
//            // instantaneousAngle of event is 0 or +/-PI when events are mostly horizontal (there is a cut at +/-PI from atan2).
//            // similarly, if events are mostly vertical, then instantaneousAngle is either PI/2 or -PI/2.
//            // if we just average instantaneous instantaneousAngle we get something in between which is at 90 deg
//            // to actual instantaneousAngle of cluster.
//            // if the event instantaneousAngle<0, we use PI-instantaneousAngle; this transformation makes all event angles fall from 0 to PI.
//            // now the problem is that horizontal events still average to PI/2 (vertical cluster).
//
//            float dx = ( location.x - event.x );
//            float dy = ( location.y - event.y );
//            float newAngle = (float)( Math.atan2(dy,dx) );
//            if ( newAngle < 0 ){
//                newAngle += (float)Math.PI; // puts newAngle in 0,PI, e.g -30deg becomes 150deg
//            }
//            // if newAngle is very different than established instantaneousAngle, assume it is
//            // just the other end of the object and flip the newAngle.
////                boolean flippedPos=false, flippedNeg=false;
//            float diff = newAngle - angle;
//            if ( ( diff ) > Math.PI / 2 ){
//                // newAngle is clockwise a lot, flip it back across to
//                // negative value that can be averaged; e.g. instantaneousAngle=10, newAngle=179, newAngle->-1.
//                newAngle = newAngle - (float)Math.PI;
////                    flippedPos=true;
//            } else if ( diff < -Math.PI / 2 ){
//                // newAngle is CCW
//                newAngle = -(float)Math.PI + newAngle; // instantaneousAngle=10, newAngle=179, newAngle->1
////                    flippedNeg=true;
//            }
////                if(newAngle>3*Math.PI/4)
////                    newAngle=(float)Math.PI-newAngle;
//            float angleDistance = ( newAngle - angle ); //angleDistance(instantaneousAngle, newAngle);
//            // makes instantaneousAngle=0 for horizontal positive event, PI for horizontal negative event y=0+eps,x=-1, -PI for y=0-eps, x=-1, //
//            // PI/2 for vertical positive, -Pi/2 for vertical negative event
//            setAngle(angle + mixingFactor * angleDistance);
////                System.out.println(String.format("dx=%8.1f\tdy=%8.1f\tnewAngle=%8.1f\tangleDistance=%8.1f\tangle=%8.1f\tflippedPos=%s\tflippedNeg=%s",dx,dy,newAngle*180/Math.PI,angleDistance*180/Math.PI,instantaneousAngle*180/Math.PI,flippedPos,flippedNeg));
////                System.out.println(String.format("dx=%8.1f\tdy=%8.1f\tnewAngle=%8.1f\tangleDistance=%8.1f\tangle=%8.1f",dx,dy,newAngle*180/Math.PI,angleDistance*180/Math.PI,instantaneousAngle*180/Math.PI));
////                setAngle(-.1f);
//        }
//    }
//
//    /**
//     * Computes signed distance to-from between two angles with cut at -PI,PI. E.g.
//     *     if e is from small instantaneousAngle and from=PI-e, to=-PI+e, then angular distance to-from is
//     *     -2e rather than (PI-e)-(-PI+e)=2PI-2e.
//     *     This minimum instantaneousAngle difference is useful to push an instantaneousAngle in the correct direction
//     *     by the correct amount. For this example, we want to push an instantaneousAngle hovering around PI-e.
//     *     We don't want angles of -PI+e to push the instantaneousAngle from lot, just from bit towards PI.
//     *     If we have instantaneousAngle <code>from</code> and new instantaneousAngle <code>to</code> and
//     *     mixing factor m<<1, then new instantaneousAngle <code>c=from+m*angleDistance(from,to)</code>.
//     *
//     *
//     * @param from the first instantaneousAngle
//     * @param to the second instantaneousAngle
//     * @return the smallest difference to-from, ordinarily positive if to>from
//     */
//    private float angleDistance (float from,float to){
//        float d = to - from;
//        if ( d > Math.PI ){
//            return d - (float)Math.PI;
//        }
//        if ( d < -Math.PI ){
//            return d + (float)Math.PI;
//        }
//        return d;
//    }
//
//    /** Measures distance from cluster center to event.
//     * @return distance of this cluster to the event in manhatten (cheap) metric (sum of abs values of x and y distance).
//     */
//    private float distanceTo (BasicEvent event){
//        final float dx = event.x - location.x;
//        final float dy = event.y - location.y;
////            return Math.abs(dx)+Math.abs(dy);
//        return distanceMetric(dx,dy);
////            dx*=dx;
////            dy*=dy;
////            float distance=(float)Math.sqrt(dx+dy);
////            return distance;
//    }
//
//    public float distanceMetric (float dx,float dy){
//        return ( ( dx > 0 ) ? dx : -dx ) + ( ( dy > 0 ) ? dy : -dy );
//    }
//
//    /** Measures distance in x direction, accounting for
//     * instantaneousAngle of cluster and predicted movement of cluster.
//     *
//     * @return distance in x direction of this cluster to the event,
//     * where x is measured along instantaneousAngle=0.
//     */
//    private float distanceToX (BasicEvent event){
//        int dt = event.timestamp - lastUpdateTime;
//        float distance = Math.abs(( event.x - location.x + velocityPPT.x * ( dt ) ) * cosAngle + ( event.y - location.y + velocityPPT.y * ( dt ) ) * sinAngle);
////            float distance = Math.abs (event.x - location.x);
//        return distance;
//    }
//
//    /** Measures distance in y direction, accounting for instantaneousAngle of cluster,
//     * where y is measured along instantaneousAngle=Pi/2  and predicted movement of cluster
//     *
//     * @return distance in y direction of this cluster to the event
//     */
//    private float distanceToY (BasicEvent event){
//        int dt = event.timestamp - lastUpdateTime;
//        float distance = Math.abs(( event.y - location.y + velocityPPT.y * ( dt ) ) * cosAngle - ( event.x - location.x + velocityPPT.x * ( dt ) ) * sinAngle);
/////           float distance = Math.abs (event.y - location.y);
//        return distance;
//    }
//
//    /** Computes and returns distance to another cluster.
//     * @return distance of this cluster to the other cluster in pixels.
//     */
//    protected final float distanceTo (RectangularCluster c){
//        // TODO doesn't use predicted location of clusters, only present locations
//        float dx = c.location.x - location.x;
//        float dy = c.location.y - location.y;
//        return distanceMetric(dx,dy);
////            if(dx<0)dx=-dx;
////            if(dy<0)dy=-dy;
////            dx*=dx;
////            dy*=dy;
////            float distance=(float)Math.sqrt(dx+dy);
////            distance=dx+dy;
////            return distance;
//    }
//
//    /** Computes and returns the angle of this cluster's velocityPPT vector to another cluster's velocityPPT vector.
//     *
//     * @param c the other cluster.
//     * @return the angle in radians, from 0 to PI in radians. If either cluster has zero velocityPPT, returns 0.
//     */
//    protected final float velocityAngleToRad (RectangularCluster c){
//        float s1 = getSpeedPPS(), s2 = c.getSpeedPPS();
//        if ( s1 == 0 || s2 == 0 ){
//            return 0;
//        }
//        float dot = velocityPPS.x * c.velocityPPS.x + velocityPPS.y * c.velocityPPS.y;
//        float angleRad = (float)Math.acos(dot / s1 / s2);
//        return angleRad;
//    }
//
//    /**
//     * Computes and returns the total absolute distance
//     * (shortest path) traveled in pixels since the birth of this cluster
//     * @return distance in pixels since birth of cluster
//     */
//    public float getDistanceFromBirth (){
//        double dx = location.x - birthLocation.x;
//        double dy = location.y - birthLocation.y;
//        return (float)Math.sqrt(dx * dx + dy * dy);
//    }
//
//    /** @return signed distance in Y from birth. */
//    public float getDistanceYFromBirth (){
//        return location.y - birthLocation.y;
//    }
//
//    /** @return signed distance in X from birth. */
//    public float getDistanceXFromBirth (){
//        return location.x - birthLocation.x;
//    }
//
//    /** Corrects for perspective looking down on a flat surface towards a horizon.
//    @return the absolute size of the cluster after perspective correction, i.e., a large cluster at the bottom
//     * of the scene is the same absolute size as a smaller cluster higher up in the scene.
//     */
//    public float getRadiusCorrectedForPerspective (){
//        float scale = 1 / getPerspectiveScaleFactor(location);
//        return radius * scale;
//    }
//
//    /** The effective radius of the cluster depends on whether highwayPerspectiveEnabled is true or not and also
//    on the surround of the cluster. The getRadius value is not used directly since it is a parameter that is combined
//    with perspective location and aspect ratio.
//
//    @return the cluster radius.
//     */
//    public final float getRadius (){
//        return radius;
//    }
//
//    /** the radius of a cluster is the distance in pixels from the cluster center
//     * that is the putative model size.
//     * If highwayPerspectiveEnabled is true, then the radius is set to a fixed size
//     * depending on the defaultClusterRadius and the perspective
//     * location of the cluster and r is ignored. The aspect ratio parameters
//     * radiusX and radiusY of the cluster are also set.
//     * @param r the radius in pixels
//     */
//    public void setRadius (float r){
//        if ( !highwayPerspectiveEnabled ){
//            radius = r;
//        } else{
//            radius = defaultClusterRadius * getPerspectiveScaleFactor(location);
//        }
//        radiusX = radius / aspectRatio;
//        radiusY = radius * aspectRatio;
//    }
//
//    final public Point2D.Float getLocation (){
//        return location;
//    }
//
//    public void setLocation (Point2D.Float l){
//        this.location = l;
//    }
//
//    /** Flags whether cluster has gotten enough support. This flag is sticky and will be true from when the cluster
//    has gotten sufficient support and has enough velocityPPT (when using velocityPPT).
//    When the cluster is first marked visible, it's birthLocation is set to the current location.
//    @return true if cluster has enough support.
//     */
//    final public boolean isVisible (){
////            if (hasObtainedSupport) {
////                return true;
////            }
//        boolean ret = true;
//        if ( numEvents < thresholdEventsForVisibleCluster ){
//            ret = false;
//        }
//        if ( pathsEnabled ){
//            double speed = Math.sqrt(velocityPPT.x * velocityPPT.x + velocityPPT.y * velocityPPT.y) * 1e6 / AEConstants.TICK_DEFAULT_US; // speed is in pixels/sec
//            if ( speed < thresholdVelocityForVisibleCluster ){
//                ret = false;
//            }
//        }
//        hasObtainedSupport = ret;
//        if ( ret ){
//            birthLocation.x = location.x;
//            birthLocation.y = location.y;  // reset location of birth to presumably less noisy current location.
//        }
//        return ret;
//    }
//
//    /** Flags whether this cluster was ever 'visible', i.e., had ever obtained sufficient support to be marked visible.
//     *
//     * @return true if it was ever visible.
//     */
//    final public boolean isWasEverVisible (){
//        return hasObtainedSupport;
//    }
//
//    /** @return lifetime of cluster in timestamp ticks, measured as lastUpdateTime-firstEventTimestamp. */
//    final public int getLifetime (){
//        return lastUpdateTime - firstEventTimestamp;
//    }
//
//    /** Updates path (historical) information for this cluster,
//     * including cluster velocity (by calling updateVelocity()).
//     * The path is trimmed to maximum length if logging is not enabled.
//     * @param t current timestamp.
//     */
//    final public void updatePath (int t){
//        if ( !pathsEnabled ){
//            return;
//        }
//        if ( numEvents == previousNumEvents ){
//            return; // don't add point unless we had events that caused change in path (aside from prediction from velocityPPT)
//        }
//        path.add(new ClusterPathPoint(location.x,location.y,t,numEvents - previousNumEvents));
//        previousNumEvents = numEvents;
//        updateVelocity();
//
//        if ( path.size() > pathLength ){
//            if ( !logDataEnabled || clusterLoggingMethod != clusterLoggingMethod.LogClusters ){
////                    path.remove(path.get(0)); // if we're logging cluster paths, then save all cluster history regardless of pathLength
//                path.remove(path.get(0)); // if we're logging cluster paths, then save all cluster history regardless of pathLength
//            }
//        }
//    }
//
//    /** Updates velocityPPT, velocityPPS of cluster and last path point lowpass filtered velocity.
//     *
//     */
//    private void updateVelocity (){
////            velocityFitter.update();
////            if (velocityFitter.valid) {
////                velocityPPT.x = velocityFitter.getXVelocity();
////                velocityPPT.y = velocityFitter.getYVelocity();
////                velocityPPS.x = velocityPPT.x * VELPPS_SCALING;
////                velocityPPS.y = velocityPPT.y * VELPPS_SCALING;
////                velocityValid = true;
////            } else {
////                velocityValid = false;
////            }
//
//        // update velocityPPT of cluster using last two path points
//        if ( path.size() > 1 ){
//            ClusterPathPoint c1 = path.get(path.size() - 2);
//            ClusterPathPoint c2 = path.get(path.size() - 1);
//            int dt = c2.t - c1.t;
//            if ( dt > MIN_DT_FOR_VELOCITY_UPDATE ){
//                float vx = ( c2.x - c1.x ) / dt;
//                float vy = ( c2.y - c1.y ) / dt;
//                velocityPPT.x = vxFilter.filter(vx,lastEventTimestamp);
//                velocityPPT.y = vyFilter.filter(vy,lastEventTimestamp);
//                c2.velocityPPT = new Point2D.Float(velocityPPT.x,velocityPPT.y);
////                    float m1=1-velocityMixingFactor;
////                    velocityPPT.x=m1*velocityPPT.x+velocityMixingFactor*vx;
////                    velocityPPT.y=m1*velocityPPT.y+velocityMixingFactor*vy;
//                velocityPPS.x = velocityPPT.x * VELPPS_SCALING;
//                velocityPPS.y = velocityPPT.y * VELPPS_SCALING;
//                setVelocityValid(true);
//            }
//        }
//    }
//
//    @Override
//    public String toString (){
//        return String.format("Cluster number=#%d numEvents=%d locationX=%d locationY=%d radiusX=%.1f radiusY=%.1f lifetime=%d visible=%s speedPPS=%.2f",
//                getClusterNumber(),numEvents,
//                (int)location.x,
//                (int)location.y,
//                radiusX,
//                radiusY,
//                getLifetime(),
//                isVisible(),
//                getSpeedPPS());
//    }
//
//    public ArrayList<ClusterPathPoint> getPath (){
//        return path;
//    }
//
//    public Color getColor (){
//        return color;
//    }
//
//    public void setColor (Color color){
//        this.color = color;
//    }
//
//    /** Returns velocityPPT of cluster in pixels per second.
//     *
//     * @return averaged velocityPPT of cluster in pixels per second.
//     * <p>
//     * The method of measuring velocityPPT is based on a linear regression of a number of previous cluter locations.
//     * @see #getVelocityPPT()
//     *
//     */
//    public Point2D.Float getVelocityPPS (){
//        return velocityPPS;
//        /* old method for velocityPPT estimation is as follows
//         * The velocityPPT is instantaneously
//         * computed from the movement of the cluster caused by the last event, then this velocityPPT is mixed
//         * with the the old velocityPPT by the mixing factor. Thus the mixing factor is appplied twice: once for moving
//         * the cluster and again for changing the velocityPPT.
//         * */
//    }
//
//    /** Computes and returns speed of cluster in pixels per second.
//     *
//     * @return speed in pixels per second.
//     */
//    public float getSpeedPPS (){
//        return (float)Math.sqrt(velocityPPS.x * velocityPPS.x + velocityPPS.y * velocityPPS.y);
//    }
//
//    /** Computes and returns speed of cluster in pixels per timestamp tick.
//     *
//     * @return speed in pixels per timestamp tick.
//     */
//    public float getSpeedPPT (){
//        return (float)Math.sqrt(velocityPPT.x * velocityPPT.x + velocityPPT.y * velocityPPT.y);
//    }
//
//    /** @return average (mixed by {@link #mixingFactor}) distance from events to cluster center
//     */
//    public float getAverageEventDistance (){
//        return averageEventDistance;
//    }
//
//    /** @see #getAverageEventDistance */
//    public void setAverageEventDistance (float averageEventDistance){
//        this.averageEventDistance = averageEventDistance;
//    }
//
//    public float getAverageEventXDistance (){
//        return averageEventXDistance;
//    }
//
//    public void setAverageEventXDistance (float averageEventXDistance){
//        this.averageEventXDistance = averageEventXDistance;
//    }
//
//    public float getAverageEventYDistance (){
//        return averageEventYDistance;
//    }
//
//    public void setAverageEventYDistance (float averageEventYDistance){
//        this.averageEventYDistance = averageEventYDistance;
//    }
//
//    public float getMeasuredAspectRatio (){
//        return averageEventYDistance / averageEventXDistance;
//    }
//
//    public float getMeasuredArea (){
//        return averageEventYDistance * averageEventXDistance;
//    }
//
//    public float getMeasuredRadius (){
//        return (float)Math.sqrt(averageEventYDistance * averageEventYDistance + averageEventXDistance * averageEventXDistance);
//    }
//
//    public float getMeasuredAverageEventRate (){
//        return avgEventRate / radius;
//    }
//
//    /** Computes the size of the cluster based on average event distance and adjusted for perpective scaling.
//     * A large cluster at botton of screen is the same size as a smaller cluster closer to horizon
//     * @return size of cluster in pizels
//     */
//    public float getMeasuredSizeCorrectedByPerspective (){
//        float scale = getPerspectiveScaleFactor(location);
//        if ( scale <= 0 ){
//            return averageEventDistance;
//        }
//        return averageEventDistance / scale;
//    }
//
//    /** Sets color according to measured cluster size */
//    public void setColorAccordingToSize (){
//        float s = getMeasuredSizeCorrectedByPerspective();
//        float hue = 2 * s / maxSize;
//        if ( hue > 1 ){
//            hue = 1;
//        }
//        setColor(Color.getHSBColor(hue,1f,1f));
//    }
//
//    /** Sets color according to age of cluster */
//    public void setColorAccordingToAge (){
//        float brightness = (float)Math.max(0f,Math.min(1f,getLifetime() / clusterLifetimeWithoutSupportUs));
//        setColor(Color.getHSBColor(.5f,1f,brightness));
//    }
//
////        public void setColorAccordingToClass(){
////            float s=getMeasuredSizeCorrectedByPerspective();
////            float hue=0.5f;
////            if(s>getClassifierThreshold()){
////                hue=.3f;
////            }else{
////                hue=.8f;
////            }
////            Color c=Color.getHSBColor(hue,1f,1f);
////            setColor(c);
////        }
//    public void setColorAutomatically (){
//        if ( colorClustersDifferentlyEnabled ){
//            // color is set on object creation, don't change it
//        } else{
//            setColorAccordingToSize();
//        }
////            else if(!isClassifierEnabled()){
////                setColorAccordingToSize(); // sets color according to measured cluster size, corrected by perspective, if this is enabled
////            // setColorAccordingToAge(); // sets color according to how long the cluster has existed
////            }else{ // classifier enabled
////                setColorAccordingToClass();
////            }
//    }
//
//    public int getClusterNumber (){
//        return clusterNumber;
//    }
//
//    public void setClusterNumber (int clusterNumber){
//        this.clusterNumber = clusterNumber;
//    }
//
//    /** @return average ISI for this cluster in timestamp ticks. Average is computed using cluster location mising factor.
//     */
//    public float getAvgISI (){
//        return avgISI;
//    }
//
//    public void setAvgISI (float avgISI){
//        this.avgISI = avgISI;
//    }
//
//    /** @return average event rate in spikes per timestamp tick. Average is computed using location mixing factor. Note that this measure
//     * emphasizes the high spike rates because a few events in rapid succession can rapidly push up the average rate.
//     */
//    public float getAvgEventRate (){
//        return avgEventRate;
//    }
//
//    public void setAvgEventRate (float avgEventRate){
//        this.avgEventRate = avgEventRate;
//    }
//
//    public float getAspectRatio (){
//        return aspectRatio;
//    }
//
//    /** Aspect ratio is 1 for square cluster and in general is height/width.
//     *
//     * @param aspectRatio
//     */
//    public void setAspectRatio (float aspectRatio){
//        this.aspectRatio = aspectRatio;
////            float radiusX=radius/aspectRatio, radiusY=radius*aspectRatio;
//    }
//
//    /** Angle of cluster, in radians.
//     *
//     * @return in radians.
//     */
//    public float getAngle (){
//        return angle;
//    }
//
//    /** Angle of cluster is zero by default and increases CCW from 0 lying along the x axis.
//     * Also sets internal cosAngle and sinAngle.
//     * @param angle in radians.
//     */
//    public void setAngle (float angle){
//        this.angle = angle;
//        cosAngle = (float)Math.cos(angle);
//        sinAngle = 1 - cosAngle * cosAngle;
//    }
//    /**
//     * Does a moving or rolling linear regression (a linear fit) on updated PathPoint data.
//     * The new data point replaces the oldest data point. Summary statistics holds the rollling values
//     * and are updated by subtracting the oldest point and adding the newest one.
//     * From <a href="http://en.wikipedia.org/wiki/Ordinary_least_squares#Summarizing_the_data">Wikipedia article on Ordinary least squares</a>.
//     *<p>
//    If velocityPPT cannot be estimated (e.g. due to only 2 identical points) it is not updated.
//     * @author tobi
//     */
//    private class RollingVelocityFitter{
//        private static final int LENGTH_DEFAULT = 5;
//        private int length = LENGTH_DEFAULT;
//        private float st = 0, sx = 0, sy = 0, stt = 0, sxt = 0, syt = 0, den = 1; // summary stats
//        private ArrayList<ClusterPathPoint> points;
//        private float xVelocity = 0, yVelocity = 0;
//        private boolean valid = false;
//        private int nPoints = 0;
//
//        /** Creates a new instance of RollingLinearRegression */
//        public RollingVelocityFitter (ArrayList<ClusterPathPoint> points,int length){
//            this.points = points;
//            this.length = length;
//        }
//
//        public String toString (){
//            return String.format("RollingVelocityFitter: \n" + "valid=%s nPoints=%d\n"
//                    + "xVel=%f, yVel=%f\n"
//                    + "st=%f sx=%f sy=%f, sxt=%f syt=%f den=%f",
//                    valid,nPoints,
//                    xVelocity,yVelocity,
//                    st,sx,sy,sxt,syt,den);
//
//        }
//
//        /**
//         * Updates estimated velocityPPT based on last point in path. If velocityPPT cannot be estimated
//        it is not updated.
//         * @param t current timestamp.
//         */
//        private synchronized void update (){
//            int n = points.size();
//            if ( n < 1 ){
//                return;
//            }
//            ClusterPathPoint p = points.get(n - 1); // take last point
//            if ( p.getNEvents() == 0 ){
//                return;
//            }
//            nPoints++;
//            if ( n > length ){
//                removeOldestPoint(); // discard data beyond range length
//            }
//            n = n > length ? length : n;  // n grows to max length
//            float dt = p.t - firstEventTimestamp; // t is time since cluster formed, limits absolute t for numerics
//            st += dt;
//            sx += p.x;
//            sy += p.y;
//            stt += dt * dt;
//            sxt += p.x * dt;
//            syt += p.y * dt;
////                if(n<length) return; // don't estimate velocityPPT until we have all necessary points, results very noisy and send cluster off to infinity very often, would give NaN
//            den = ( n * stt - st * st );
//            if ( n >= length && den != 0 ){
//                valid = true;
//                xVelocity = ( n * sxt - st * sx ) / den;
//                yVelocity = ( n * syt - st * sy ) / den;
//            } else{
//                valid = false;
//            }
////                System.out.println(this.toString());
//        }
//
//        private void removeOldestPoint (){
//            // takes away from summary states the oldest point
//            ClusterPathPoint p = points.get(points.size() - length - 1);
//            // if points has 5 points (0-4), length=3, then remove points(5-3-1)=points(1) leaving 2-4 which is correct
//            float dt = p.t - firstEventTimestamp;
//            st -= dt;
//            sx -= p.x;
//            sy -= p.y;
//            stt -= dt * dt;
//            sxt -= p.x * dt;
//            syt -= p.y * dt;
//        }
//
//        int getLength (){
//            return length;
//        }
//
//        /** Sets the window length.  Clears the accumulated data.
//         * @param length the number of points to fit
//         * @see #LENGTH_DEFAULT
//         */
//        synchronized void setLength (int length){
//            this.length = length;
//        }
//
//        public float getXVelocity (){
//            return xVelocity;
//        }
//
//        public float getYVelocity (){
//            return yVelocity;
//        }
//
//        /** Returns true if the last estimate resulted in a valid measurement
//         * (false when e.g. there are only two identical measurements)
//         */
//        public boolean isValid (){
//            return valid;
//        }
//
//        public void setValid (boolean valid){
//            this.valid = valid;
//        }
//    } // rolling velocityPPT fitter
//
//    /** Returns birth location of cluster: initially the first event and later, after cluster
//     * becomes visible, it is the location when it becomes visible, which is presumably less noisy.
//     *
//     * @return x,y location.
//     */
//    public Point2D.Float getBirthLocation (){
//        return birthLocation;
//    }
//
//    /** Returns first timestamp of cluster; this time is updated when cluster becomes visible.
//     *
//     * @return timestamp of birth location.
//     */
//    public int getBirthTime (){
//        return firstEventTimestamp;
//    }
//
//    public void setBirthLocation (Point2D.Float birthLocation){
//        this.birthLocation = birthLocation;
//    }
//
//    /** This flog is set true after a velocityPPT has been computed for the cluster.
//     * This may take several packets.
//
//    @return true if valid.
//     */
//    public boolean isVelocityValid (){
//        return velocityValid;
//    }
//
//    public void setVelocityValid (boolean velocityValid){
//        this.velocityValid = velocityValid;
//    }
//
//} // RectangularCluster
