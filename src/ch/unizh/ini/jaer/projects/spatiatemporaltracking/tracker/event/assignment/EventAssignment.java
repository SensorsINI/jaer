/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.assignment;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterListener;

import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * The interface provides methods to assign a TypedEvent to a FeatureExtractable.
 */
public interface EventAssignment extends ParameterListener {
    
    /*
     * Assigns a TypedEvent to a FeatureExtractable.
     * 
     * @param e The TypedEvent to assign to a FeatureExtractable.
     */
    public void assign(TypedEvent e);
}
