/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.tracking;

import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.event.ApsDvsEvent;

import net.sf.jaer.event.TypedEvent;

/**
 * Extends an event of a named type to include a reference to a RectangularClusterTracker.Cluster.
 * @author tobi
 */
public class RectangularClusterTrackerEvent extends ApsDvsEvent {

    protected RectangularClusterTracker.Cluster cluster;

    public RectangularClusterTrackerEvent() {
    }

    @Override
    public void draw(GLAutoDrawable drawable) {
        super.draw(drawable);
        getCluster().draw(drawable);
    }

    /**
     * Returns the associated Cluster.
     * @return the cluster
     */
    public RectangularClusterTracker.Cluster getCluster() {
        return cluster;
    }

    /**
     * Sets the associated cluster.
     * @param cluster the cluster to set
     */
    public void setCluster(RectangularClusterTracker.Cluster cluster) {
        this.cluster = cluster;
    }
}