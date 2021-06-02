/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.tracking;

import java.util.List;

/**
 * Superinterface for cluster trackers, so that users of trackers can swap in different trackers more easily.
 *
 * @author tobi
 */
public interface ClusterTrackerInterface {

    /** Returns the list of clusters.
     *
     * @return cluster list.
     */
    List<? extends ClusterInterface> getClusters();

}
