/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.creator;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;

/**
 *
 * This class is used to create a Signal.
 * 
 * @author matthias
 */
public interface SignalCreator {
    
    /**
     * Initializes the SignalCreator.
     */
    public void init();
    
    /**
     * Resets the SignalCreator.
     */
    public void reset();
    
    /**
     * Adds the given Transition to the SignalCreator.
     * 
     * @param t The Transition to add.
     */
    public void add(Transition t);
    
    /**
     * Adds a Transition to the SignalCreator based on the given time and
     * state.
     * 
     * @param time The time of the Transition.
     * @param state The state of the signal after this Transition.
     */
    public void add(int time, int state);
    
    /**
     * Creates the Signal based on the information of the SignalCreator.
     * 
     * @return The Signal based on the information of the SignalCreator.
     */
    public Signal getSignal();
}
