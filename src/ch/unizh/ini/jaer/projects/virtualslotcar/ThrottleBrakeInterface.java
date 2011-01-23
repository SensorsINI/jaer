/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

/**
 * The interface to the throttle of the slot car; either of physical slot car controller or virtual slot car.
 * @author tobi
 */
public interface ThrottleBrakeInterface {

    /**
     * @return the current throttle.
     */
    public ThrottleBrake getThrottle ();

    /**
     * Sets the throttle.
     */
    public void setThrottle (ThrottleBrake throttle);


}
