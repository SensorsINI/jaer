/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

/**
 * The interface to the throttle of the slot car; either of physical slot car controller or virtual slot car.
 * @author tobi
 */
public interface ThrottleInterface {

    /**
     * @return the last speed that was set.
     */
    float getThrottle ();

    /**
     * Set the speed and returns success.
     * @param speed the speed to set.
     * @return true if interface was open.
     */
    boolean setThrottle (float speed);

}
