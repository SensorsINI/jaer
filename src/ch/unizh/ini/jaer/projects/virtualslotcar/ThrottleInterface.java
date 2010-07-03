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
     * @return the last throttle level that was set.
     */
    float getThrottle ();

    /**
     * Set the throttle level and returns success.
     * @param throttle The throttle level to set.
     * @return true if interface was open.
     */
    boolean setThrottle (float throttle);

//    /** These methods just for ease of delegating building the GUI. If we delegate these methods then the GUI can build a slider. */
//    public float getMaxThrottle();
//    public float getMinThrottle();

}
