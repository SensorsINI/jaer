/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.tracking;

/**
 * Interface for stereo clusters with disparity.
 *
 * @author jun haeng lee, tobi delbruck
 */
public interface StereoClusterInterface extends ClusterInterface{

    /** Returns disparity in pixels.
     *
     * @return disparity in pixels
     */
    public float getDisparity();

}
