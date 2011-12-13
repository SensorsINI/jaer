/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.activity;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this interface have to extract the activity
 * out of the given events. The activity measures the number of events over
 * time.
 */
public interface ActivityExtractor {
    
    /**
     * Gets the activity of the object which measures the number of events
     * over time.
     * 
     * @return The activity of the object.
     */
    public double getActivity();
}
