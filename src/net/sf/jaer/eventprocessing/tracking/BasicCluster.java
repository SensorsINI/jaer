/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.tracking;

import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.LinkedList;
import java.util.Random;
import net.sf.jaer.event.BasicEvent;
import java.util.logging.Level;
import static net.sf.jaer.eventprocessing.EventFilter.log;

/**
 *
 * @author Bjoern
 */
public class BasicCluster {

    protected static final float FULL_BRIGHTNESS_LIFETIME = 100000;
    
    private static int clusterCounter = 0;
    
    private static int MassDecayTauUs = 10000;
    private static float mixingFactor = 0.05f;
    private static float defaultClusterRadius = 10;
    
    public static int getMassDecayTauUs() {
        return MassDecayTauUs;
    }
    
    public static void setMassDecayTauUs(int massDecayTauUs) {
        BasicCluster.MassDecayTauUs = massDecayTauUs;
    }

    public static float getMixingFactor() {
        return mixingFactor;
    }

    public static void setMixingFactor(float mixingFactor) {
        BasicCluster.mixingFactor = mixingFactor;
    }

    public static float getDefaultClusterRadius() {
        return defaultClusterRadius;
    }

    public static void setDefaultClusterRadius(float defaultClusterRadius) {
        BasicCluster.defaultClusterRadius = defaultClusterRadius;
    }
    
    private final Random random = new Random();
    
    /** location of cluster in pixels */
    private Point2D.Float location = new Point2D.Float(); // location in chip pixels
    /** birth location of cluster */
    private Point2D.Float birthLocation = new Point2D.Float(); 
    /** Rendered color of cluster. */
    private Color color = null;
    /** Number of events collected by this cluster.*/
    private int numEvents = 0;
    /** First and last timestamp of cluster. 
     * <code>firstEventTimestamp</code> is updated when cluster becomes visible.
     * <code>lastEventTimestamp</code> is the last time the cluster was touched either by an event or by
     * some other timestamped update, e.g. {@link #updateClusterList(net.sf.jaer.event.EventPacket, int) }.
     * @see #isVisible() */
    private int lastEventTimestamp, firstEventTimestamp;
    /** The "mass" of the cluster is the weighted number of events it has collected.
     * The mass decays over time and is incremented by one by each collected event.
     * The mass decays with a first order time constant of clusterMassDecayTauUs in us.
     * If surroundInhibitionEnabled=true, then the mass is decremented by events captured in the surround. */
    private float mass = 1;
    /** This is the last time in timestamp ticks that the cluster was updated, either by an event
     * or by a regular update such as {@link #updateClusterLocations(int)}. This time can be used to
     * compute position updates given a cluster velocityPPT and time now. */
    private int lastUpdateTime;
//    /** average (mixed using mixingFactor) distance of events from cluster center, a measure of actual cluster size. */
//    private float averageEventDistance, averageEventXDistance, averageEventYDistance;
    private float averageEventDistance, averageEventXDistance, averageEventYDistance;
    private float distanceToLastEvent = Float.POSITIVE_INFINITY;
    private float xDistanceToLastEvent = Float.POSITIVE_INFINITY, yDistanceToLastEvent = Float.POSITIVE_INFINITY;
    /** assigned to be the absolute number of the cluster that has been created. */
    private int ClusterID;
    private float radius;
    private LinkedList<ClusterPathPoint> path = new LinkedList<>();
    /** Flag which is set true (forever) once a cluster has first obtained sufficient support. */
    private boolean hasObtainedSupport = false;
    private boolean visibilityFlag = false; // this flag updated in updateClusterList
    
    
    
    public BasicCluster() {
        ClusterID = ++BasicCluster.clusterCounter;
        color     = Color.getHSBColor(random.nextFloat(), 1f, 1f);
    }
    
    /** Constructs a cluster at the location of an event.
     * The numEvents, location, birthLocation, first and last timestamps are set.
     * The radius is set to defaultClusterRadius.
     * @param ev the event. */
    public BasicCluster(BasicEvent ev) {
        this();
        location.x           = ev.x;
        location.y           = ev.y;
        birthLocation.x      = ev.x;
        birthLocation.y      = ev.y;
        lastEventTimestamp   = ev.timestamp;
        lastUpdateTime       = ev.timestamp;
        firstEventTimestamp  = lastEventTimestamp;
        numEvents            = 1;
        mass                 = 1;
        radius               = BasicCluster.getDefaultClusterRadius();
    }
    
    /** Constructs a cluster by merging two clusters.
     * All parameters of the resulting cluster should be reasonable
     * combinations of the source cluster parameters.
     * For example, the merged location values are weighted by the mass of
     * events that have supported each source cluster, so that older
     * clusters weigh more heavily in the resulting cluster location.
     * Subtle bugs or poor performance can result from not properly handling
     * the merging of parameters.
     *
     * @param one the first cluster
     * @param two the second cluster */
    public BasicCluster(BasicCluster one, BasicCluster two) {
        this();
        mergeTwoClustersToThis(one, two);
    }
    
    /** prune is called when the cluster is about to be pruned from the list of clusters.  
     * By default no special action is taken. Subclasses can override 
     * this method to take a special action on pruning. */
    protected void prune() { }
        
    /** Merges information from two source clusters into this cluster to preserve the combined history that is most reliable.
     * @param one
     * @param two */
    protected final void mergeTwoClustersToThis(BasicCluster one, BasicCluster two) {
        BasicCluster stronger = one.getMass() > two.getMass() ? one : two; 
        ClusterID = stronger.getClusterNumber();
        color     = stronger.getColor();

        mass      = one.getMass()      + two.getMass(); // merge locations by average weighted by mass of events supporting each cluster
        numEvents = one.getNumEvents() + two.getNumEvents();
        location  = stronger.getLocation(); // change to older for location to avoid discontinuities in postion
        
        averageEventDistance = ((one.averageEventDistance * one.mass) + (two.averageEventDistance * two.mass)) / mass;
        averageEventXDistance = ((one.averageEventXDistance * one.mass) + (two.averageEventXDistance * two.mass)) / mass;
        averageEventYDistance = ((one.averageEventYDistance * one.mass) + (two.averageEventYDistance * two.mass)) / mass;

        lastEventTimestamp  = one.getLastEventTimestamp() > two.getLastEventTimestamp() ? one.getLastEventTimestamp() : two.getLastEventTimestamp();
        lastUpdateTime      = lastEventTimestamp;
        firstEventTimestamp = stronger.getFirstEventTimestamp(); // make lifetime the oldest src cluster
        path                = stronger.getPath();
        birthLocation       = stronger.getBirthLocation();
        hasObtainedSupport  = stronger.isHasObtainedSupport();
        visibilityFlag      = stronger.isVisible();
        radius              = stronger.getRadius();
    }
    
    /**Increments mass of cluster by one after decaying it away since the {@link #lastEventTimestamp} according
     * to exponential decay with time constant {@link #clusterMassDecayTauUs}.
     * @param event used for event timestamp. */
    protected void updateMass(BasicEvent event) {
        boolean wasInfinite=Float.isInfinite(mass);
        // don't worry about distance, just increment
        int dt=lastEventTimestamp - event.timestamp;
        if(dt<0){
            mass = 1 + (mass * (float) Math.exp((float) dt / getMassDecayTauUs()));
            if (!wasInfinite && Float.isInfinite(mass)) {
                log.log(Level.WARNING, "mass became infinite for {0}", this);
            }
        }
    }
    
    protected void updatePosition(BasicEvent event, float mixingFactor) {
        float m1 = 1 - mixingFactor;

        location.x = ((m1 * location.x) + (mixingFactor * event.x));
        location.y = ((m1 * location.y) + (mixingFactor * event.y));
    }
    
    protected void updateAverageEventDistance(float mixingFactor) {
        float m1 = 1 - mixingFactor;
        averageEventDistance = (m1 * averageEventDistance) + (mixingFactor * distanceToLastEvent);
        averageEventXDistance = (m1 * averageEventXDistance) + (mixingFactor * xDistanceToLastEvent);
        averageEventYDistance = (m1 * averageEventYDistance) + (mixingFactor * yDistanceToLastEvent);
    }
    
    public boolean checkAndSetClusterVisibilityFlag(int t){
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    /** Sets color according to age of cluster */
    public void setColorAccordingToAge() {
        float brightness = Math.max(0f, Math.min(1f, getLifetime() / FULL_BRIGHTNESS_LIFETIME));
        Color c = Color.getHSBColor(.5f, 1f, brightness);
        setColor(c);
    }
    
    public void updatePath(int t) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override public String toString() {
        return String.format("Cluster number=#%d numEvents=%d location(x,y)=(%d,%d) radius=%.1f mass=%.1f lifetime=%d visible=%s",
                getClusterNumber(), 
                numEvents,
                (int) location.x,
                (int) location.y,
                radius,
                getMass(),
                getLifetime(),
                isVisible());
    }
    
    /** Overrides default hashCode to return {@link #clusterNumber}. This overriding
     * allows for storing clusters in lists and checking for them by their clusterNumber.
     * @return clusterNumber */
    @Override public int hashCode() {
        return ClusterID;
    }
    
    /** Two clusters are equal if their {@link #clusterNumber}'s are equal.
     * @param obj another Cluster.
     * @return true if equal. */
    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if ((obj == null) || (obj.getClass() != this.getClass())) return false;
        
        // object must be Test at this point
        BasicCluster test = (BasicCluster) obj;
        return ClusterID == test.getClusterNumber();
    }
    
    public void draw(GLAutoDrawable drawable) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    /** updates cluster by one event.
     * @param event the event */
    public void addEvent(BasicEvent event) {
        numEvents++;
        lastEventTimestamp   = event.timestamp;
        lastUpdateTime       = event.timestamp;
        distanceToLastEvent  = distanceTo(event);
        xDistanceToLastEvent = distanceToX(event);
        yDistanceToLastEvent = distanceToY(event);
        
        updateMass(event);
        updatePosition(event,BasicCluster.getMixingFactor());
        updateAverageEventDistance(BasicCluster.getMixingFactor());
    }
    
    /** Measures distance from cluster center to event.
     * @param event
     * @return distance of this cluster to the event in Manhattan (cheap)
     * metric (sum of absolute values of x and y distance). */
    public float distanceTo(BasicEvent event) {
        final float dx = event.x - location.x;
        final float dy = event.y - location.y;

        return distanceMetric(dx, dy);
    }

    /** Returns the implemented distance metric which is the Manhattan distance for speed.
     * This is the sum of abs(dx)+abs(dy).
     * @param dx the x distance
     * @param dy the y distance
     * @return abs(dx)+abs(dy) */
    public float distanceMetric(float dx, float dy) {
        return ((dx > 0) ? dx : -dx) + ((dy > 0) ? dy : -dy);
    }

    /** Measures distance in x direction
     * @param event
     * @return distance in x direction of this cluster to the event,
     * where x is measured along instantaneousAngle=0. */
    protected float distanceToX(BasicEvent event) {
        return distanceMetric(event.x - location.x,0);
    }

    /** Measures distance in y direction
     * @param event
     * @return distance in y direction of this cluster to the event */
    protected float distanceToY(BasicEvent event) {
        return distanceMetric(0,event.y - location.y);
    }

    /** Computes and returns distance to another cluster.
     * @param c
     * @return distance of this cluster to the other cluster in pixels. */
    public float distanceTo(BasicCluster c) {
        float dx = c.getLocation().x - location.x;
        float dy = c.getLocation().y - location.y;
        return distanceMetric(dx, dy);
    }
    
    /** Determines if this cluster overlaps the center of another cluster.
     * @param c2 the other cluster
     * @return true if overlapping. */
    protected boolean isOverlappingCenterOf(BasicCluster c2) {
        final boolean overlapping = distanceTo(c2) < (getRadius() + c2.getRadius());
        return overlapping;
    }
    
    /** Computes and returns the total absolute distance
     * (shortest path) traveled in pixels since the birth of this cluster
     * @return distance in pixels since birth of cluster */
    public float getDistanceFromBirth() {
        double dx = location.x - birthLocation.x;
        double dy = location.y - birthLocation.y;
        return (float) Math.sqrt((dx * dx) + (dy * dy));
    }

    /** @return signed distance in Y from birth. */
    public float getDistanceYFromBirth() {
        return location.y - birthLocation.y;
    }

    /** @return signed distance in X from birth. */
    public float getDistanceXFromBirth() {
        return location.x - birthLocation.x;
    }

    public final float getRadius() {
        return radius;
    }    
    
    public void setRadius(float radius) {
        this.radius = radius;
    }
    
    public int getClusterNumber() {
        return ClusterID;
    }

    public LinkedList<ClusterPathPoint> getPath() {
        return path;
    }

    /** Returns the flag that marks cluster visibility.
     * This flag is set by <code>checkAndSetClusterVisibilityFlag</code>.
     * This flag flags whether cluster has gotten enough support.
     * @return true if cluster has obtained enough support.
     * @see #checkAndSetClusterVisibilityFlag */
    public final boolean isVisible() {
        return visibilityFlag;
    }
    /** Flags whether this cluster was ever 'visible', i.e. had ever
     * obtained sufficient support to be marked visible.
     * @return true if it was ever visible. */
     public final boolean isWasEverVisible() {
        return hasObtainedSupport;
    }
    
    /** @return lifetime of cluster in timestamp ticks, measured as lastUpdateTime-firstEventTimestamp. */
    public final int getLifetime() {
        return lastUpdateTime - firstEventTimestamp;
    }
    
    /** Computes and returns {@link #mass} at time t, using the last time
     * an event hit this cluster and the {@link #clusterMassDecayTauUs}.
     * Does not change the mass.
     * @param t timestamp now.
     * @return the mass. */
    protected float getMassNow(int t) {
        return (mass * (float) Math.exp(((float) (lastEventTimestamp - t)) / getMassDecayTauUs()));
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --mass--">
    /** The "mass" of the cluster is the weighted number of events it has collected.
     * The mass decays over time and is incremented by one by each collected event.
     * The mass decays with a first order time constant of clusterMassDecayTauUs in us.
     * @return the mass */
    public float getMass() {
        return mass;
    }

    /** Sets the internal "mass" of the cluster.
     * @see #getMass()
     * @param mass */
    public void setMass(float mass){
        this.mass=mass;
    }
    // </editor-fold>

    public int getNumEvents() {
        return numEvents;
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --Color--">
    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --Location--">
    final public Point2D.Float getLocation() {
        return location;
    }

    public void setLocation(Point2D.Float NewLocation) {
        this.location = NewLocation;
    }
    // </editor-fold>
    
    public float getAverageEventDistance() {
        return averageEventDistance;
    }
    
    public float getAverageEventXDistance() {
        return averageEventXDistance;
    }
    
    public float getAverageEventYDistance() {
        return averageEventYDistance;
    }
    
    public float getMeasuredAspectRatio() {
        return averageEventYDistance / averageEventXDistance;
    }

    public float getMeasuredArea() {
        return averageEventYDistance * averageEventXDistance;
    }

    public float getMeasuredRadius() {
        return (float) Math.sqrt((averageEventYDistance * averageEventYDistance) + (averageEventXDistance * averageEventXDistance));
    }
    
    public int getLastEventTimestamp() {
        return lastEventTimestamp;
    }
    
    public int getFirstEventTimestamp() {
        return firstEventTimestamp;
    }
    
    /** Returns birth location of cluster: initially the first event and later, after cluster
     * becomes visible, it is the location when it becomes visible, which is presumably less noisy.
     * @return x,y location. */
    public Point2D.Float getBirthLocation() {
        return birthLocation;
    }
    
    /** Returns first timestamp of cluster; this time is updated when cluster becomes visible.
     * @return timestamp of birth location. */
    public int getBirthTime() {
        return firstEventTimestamp;
    }
    
    public boolean isHasObtainedSupport() {
        return hasObtainedSupport;
    }
    
    
}
