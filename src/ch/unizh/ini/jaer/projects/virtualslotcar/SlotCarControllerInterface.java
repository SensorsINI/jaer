/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

/**
 * The interface for a slot car controller.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public interface SlotCarControllerInterface {

    /** Computes the control signal given the car tracker and the track model.
     *
     * @param tracker
     * @param track
     * @return the throttle setting ranging from 0 to 1.
     */
    public ThrottleBrake computeControl(CarTrackerInterface tracker, SlotcarTrack track);

    /** Returns the last computed throttle setting.
     *
     * @return the throttle setting.
     */
    public ThrottleBrake getThrottle();

    /** Implement this method to return a string logging the state of the controller, e.g. throttle, measured speed, and curvature.
     *
     * @return string to log
     */
    public String logControllerState();

    /** Returns a string that says what are the contents of the log, e.g. throttle, desired speed, measured speed.
     *
     * @return the string description of the log contents.
     */
    public String logContents();

}
