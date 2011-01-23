/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

/**
 * Encapsulates throttle float and brake boolean for a track.
 * @author tobi
 */
public class ThrottleBrake {
    float throttle=0;
    boolean brake=false;

    public ThrottleBrake() {
    }

    ThrottleBrake(float throttle, boolean b) {
        this.throttle=throttle;
        this.brake=b;
    }

}
