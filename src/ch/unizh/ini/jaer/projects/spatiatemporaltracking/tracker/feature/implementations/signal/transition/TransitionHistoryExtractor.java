/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.transition;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this interface have to extract the 
 * transitions of the state of the observed object. Whenever the object
 * changes the state a transition has to describe this change with an instance
 * of the class <i>Transition</i>.
 */
public interface TransitionHistoryExtractor {
    
    /**
     * Gets the next transition of the observed object.
     * 
     * @return The next transition of the observed object.
     */
    public Transition getTransition();
}
