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

    /** Resets throttle to zero and brake off. */
    public void reset(){
        throttle=0;
        brake=false;
    }

    /** Sets the throttle and brake */
    public void set(float throttle, boolean brake){
        this.throttle=throttle;
        this.brake=brake;
    }

}
