/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.tracking;

import java.util.List;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;

/**
 * A delegate for scanning the list of clusters from RectangularClusterTracker.
 * @author tobi
 */
public interface RectangularClusterTrackerDelegate {

    public EventPacket filterPacket(EventPacket packet);

    public java.util.List<Cluster> updateClusterList(List<Cluster> clusters);


}
