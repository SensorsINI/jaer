/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this interface have to extract the signal
 * out of the observed object based on its assigned events.
 */
public interface SignalExtractor {
    
    /*
     * Gets the signal of the observed object based on its assigned events.
     * 
     * @return The signal of the observed object.
     */
    public Signal getSignal();
}
