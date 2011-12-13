/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.assignable;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.FeatureExtractable;
import java.util.Collection;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * The interface is used to assign events to an object.
 */
public interface EventAssignable extends FeatureExtractable {
    
    /**
     * Assigns an event to the object.
     * 
     * @param event The event to assign.
     */
    public void assign(TypedEvent event);
    
    /**
     * Assigns multiple events to the object.
     * 
     * @param events The events to assign.
     */
    public void assign(Collection<TypedEvent> events);
    
    /**
     * Indicates that a packet was successfully processed.
     * 
     * @param timestamp The timestamp of the algorithm.
     */
    public void packet(int timestamp);
}
