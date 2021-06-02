/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.tracking;

import java.awt.geom.Point2D;

/**
 *  A mimimal interface to cluster tracker clusters, exposing commonly used features.
 *
 * @author tobi
 */
public interface ClusterInterface {

    /** Returns the cluster center location in pixels.
     *
     * @return the location.
     */
    Point2D.Float getLocation();

    /**
     * Total number of events collected by this cluster.
     * @return the numEvents
     */
    public int getNumEvents();

    /** Returns path of cluster.
     *
     * @return path as list of points
     */
    public java.util.List<ClusterPathPoint> getPath();

    /**
     * Returns velocity of cluster in pixels per second.
     *
     * @return averaged velocity of cluster in pixels per second.
     * <p>
     * The method of measuring velocity is based on a linear regression of a number of previous cluter locations.
     * @see #getVelocityPPT()
     *
     */
    public Point2D.Float getVelocityPPS();

    /**
     * Cluster velocity in pixels/timestamp tick as a vector. Velocity values are set during cluster upate.
     *
     * @return the velocityPPT in pixels per timestamp tick.
     * @see #getVelocityPPS()
     */
    public Point2D.Float getVelocityPPT();

    /** Computes and returns speed of cluster in pixels per second.
     *
     * @return speed in pixels per second.
     */
    public float getSpeedPPS();
        
        /** Returns measure of cluster average radius in pixels.
     *
     * @return some measure of average cluster radius in pixels.
     */
    public float getRadius();

    /** Returns measure of cluster 'mass' or importance.  This mass is increased by support (e.g. events) and decays away with lack of support.
     *
     * @return the mass measure.
     */
    public float getMass();

    /** Returns true if the cluster has sufficient support to be reported as 'visible'.
     *
     * @return true if the  cluster has sufficient evidence to be marked as 'real' or visible.
     */
    public boolean isVisible();

    /** Returns the last timestamp of this Cluster.
     *
     * @return the timestamp in us.
     */
    public int getLastEventTimestamp();

}
