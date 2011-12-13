/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.event.single;

import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * This interface is used to notifiy all extractor about a new available event.
 */
public interface SingleEventExtractor {
    
    /**
     * Gets the event stored on this extractor.
     * 
     * @return The event stored on this extractor.
     */
    public TypedEvent getEvent();
}
