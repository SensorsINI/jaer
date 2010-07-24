/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.util.List;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.eventprocessing.tracking.*;

/**
 * Tracks multiple slot cars using a SlotcarTrack model and determines which car is which.
 * 
 *
 * @author tobi
 */
public class MultiCarTracker extends RectangularClusterTracker {

    private SlotcarTrack track;
    private SlotcarState carState;

    public MultiCarTracker(AEChip chip) {
        super(chip);
        if (!isPreferenceStored("maxNumClusters")) {
            setMaxNumClusters(2);
        }
        if (!isPreferenceStored("highwayPerspectiveEnabled")) {
            setHighwayPerspectiveEnabled(false);
        }
        // etc to set reasonable defaults
    }

    /**
     * @param track the track to set
     */
    public void setTrack(SlotcarTrack track) {
        this.track = track;
    }

    /**
     * @param carState the carState to set
     */
    public void setCarState(SlotcarState carState) {
        this.carState = carState;
    }

       /** Returns the putative car cluster.
     *
     * @return the car cluster, or null if there is no good cluster
     */
    public ClusterInterface getCarCluster(){
        // defines the criteria for a visible car cluster
        // very simple now
        ClusterInterface carCluster=null;
        for(ClusterInterface c:clusters){
            if(c!=null && c.isVisible()) return c;
        }
        return null;
    }

    public class CarCluster extends RectangularClusterTracker.Cluster {

        /** Overrides updatePosition method to only allow movement along the track model, between spline points of the track.
         * Events that
         * @param m the mixing factor, 0 to not move, 1 to move cluster to location of event.
         * @param event
         * @return 1-m.
         */
        @Override
        protected float updatePosition(float m, BasicEvent event) {
            if (track == null) {
                return super.updatePosition(m, event);
            } else {
                return super.updatePosition(m, event);

            }
        }
    }

}
