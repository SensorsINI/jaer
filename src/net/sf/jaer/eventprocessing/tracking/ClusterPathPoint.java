package net.sf.jaer.eventprocessing.tracking;

import java.awt.geom.Point2D;

/**
 * One point on a Cluster's path, including other statistics of the cluster history.
 *
 */
public class ClusterPathPoint extends Point2D.Float {

    /** timestamp of this point. */
    protected int t;
    /** Number of events that contributed to this point. */
    protected int nEvents;
    /** Velocity of cluster (filtered) at this point in pixels per timestamp tick (e.g. us).
     * This field is initially null and is initialized by the velocityPPT estimation, if used. */
    public Point2D.Float velocityPPT=null;
    /** disparity of stereo vision. Valid for stereo vision only */
    public float stereoDisparity;
    /** Measured size (average radius) of cluster */
    protected float radiusPixels=-1;
    
    /** Protected constructor to force use of factory method that can be overridden 
     * 
     * @param x
     * @param y
     * @param t 
     */
    protected ClusterPathPoint(float x, float y, int t) {
        super();
        this.x = x;
        this.y = y;
        this.t = t;
    }

    /** Factory method that subclasses can override to create custom path points, e.g. for storing different statistics
     * 
     * @return a new ClusterPathPoint with x,y,t set. Other fields must be set using methods.
     */
    public static ClusterPathPoint createPoint(float x, float y, int t){
        return new ClusterPathPoint(x, y, t);
    }
    
    /** Get time of point 
     * 
     * @return timestamp (typically us)
     */
    public int getT() {
        return t;
    }
   
    public int getNEvents() {
        return nEvents;
    }

    public void setStereoDisparity(float stereoDisparity){
        this.stereoDisparity = stereoDisparity;
    }

    public float getStereoDisparity(){
        return stereoDisparity;
    }

    @Override
    public String toString() {
        return String.format("%d, %f, %f, %d, %f", t, x, y, nEvents,radiusPixels);
    }

    /**
     * @param t the t to set
     */
    public void setT(int t) {
        this.t = t;
    }

  /** Returns number of events that contributed to this point. 
    * 
    * @return number of events
    */
    public int getnEvents() {
        return nEvents;
    }

    /**
     * @param nEvents the nEvents to set
     */
    public void setnEvents(int nEvents) {
        this.nEvents = nEvents;
    }

    /**
     * @return the radiusPixels
     */
    public float getRadiusPixels() {
        return radiusPixels;
    }

    /**
     * @param radiusPixels the radiusPixels to set
     */
    public void setRadiusPixels(float radiusPixels) {
        this.radiusPixels = radiusPixels;
    }
}
