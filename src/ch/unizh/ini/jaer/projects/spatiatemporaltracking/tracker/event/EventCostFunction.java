/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.EventAssignable;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * The interface provides methods to compute the cost to assign a TypedEvent
 * to a EventAssignment.
 */
public interface EventCostFunction {
    
    /*
     * Computes the cost to assign the given TypedEvent to the given
     * EventAssignment.
     * 
     * @param assignment The EventAssignable the TypedEvent has to be assigned.
     * @param e The TypedEvent to assign to a EventAssignable.
     * @return The cost to assign the TypedEvent to the EventAssignable.
     */
    public double cost(EventAssignable assignable, TypedEvent e);
    
    /**
     * Gets true, if all required data to compute the cost function are
     * available, false otherwise.
     * 
     * @param assignment The EventAssignable the TypedEvent has to be assigned.
     * @return True, if all required data are available, false otherwise.
     */
    public boolean isComputable(EventAssignable assignable);
}
