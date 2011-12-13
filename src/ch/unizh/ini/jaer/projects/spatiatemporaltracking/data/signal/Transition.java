/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal;

/**
 *
 * @author matthias
 * 
 * The class Transition represents a change in the state of the observing 
 * object.
 */
public class Transition {
    
    /** The time of the transition. */
    public int time;
    
    /** The state of the object after the transition. */
    public int state;
    
    /**
     * Creates a new Transition.
     * 
     * @param time The time of the transition.
     * @param state The state of the object after the transition.
     */
    public Transition(int time, int state) {
        this.time = time;
        this.state = state;
    }
    
    /**
     * Copies the current Transition.
     * 
     * @return A copy of the current Transition.
     */
    public Transition copy() {
        return new Transition(this.time, this.state);
    }
}
