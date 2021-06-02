/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import net.sf.jaer.eventprocessing.tracking.ClusterInterface;

/**
 * Interface for a tracked car.
 * @author tobi
 */
public interface CarClusterInterface extends ClusterInterface{

    /**
     * @return the segmentIdx
     */
    int getSegmentIdx();

    /**
     * @return the crashed
     */
    boolean isCrashed();

    /**
     * @param crashed the crashed to set
     */
    void setCrashed(boolean crashed);

    /**
     * @param segmentIdx the segmentIdx to set
     */
    void setSegmentIdx(int segmentIdx);

}
