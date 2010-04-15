/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.tracking;

/**
 * A RectangularClusterTrackerUpdateListener handles updates to the list of tracked clusters in RectangularClusterTracker.
 *
 * @author tobi
 */
public interface RectangularClusterTrackerUpdateListener {

    /** Called when the list of clusters is updated. This update can happen several times per event packet
     * (depending on the maxUpdateIntervalUs)
     * but definitely happens at least once per packet.
     * @param tracker the tracker which did the update.
     */
    public void update(RectangularClusterTracker tracker);

}
