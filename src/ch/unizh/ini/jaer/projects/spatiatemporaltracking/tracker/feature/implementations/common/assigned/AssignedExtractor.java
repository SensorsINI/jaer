/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.assigned;

import java.util.List;

/**
 *
 * @author matthias
 * 
 * This type of extractor has to give information about the assigned events of
 * the observed object.
 */
public interface AssignedExtractor {
    
    /**
     * Gets the events assigned to the observed object.
     * 
     * @return The events assigned to the observed object.
     */
    public List<List<EventStorage>> getAssignedEvents();
    
    /**
     * This class stores a timestamp and the number of events at this
     * particular timestamp
     */
    public class EventStorage {
        
        /** The timestamp of the observation. */
        public int timestamp;
        
        /** The number of events at the timestamp. */
        public int count;
        
        /**
         * Creates a new EventStorage.
         * 
         * @param timestamp The timestamp of the observation.
         * @param count The number of events at the timestamp.
         */
        public EventStorage(int timestamp, int count) {
            this.timestamp = timestamp;
            this.count = count;
        }
    }
}
