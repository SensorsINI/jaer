/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.Noticeable;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.FeatureExtractable;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPattern;

/**
 *
 * @author matthias
 * 
 * The interface is used to assign concrete implementations of this interface
 * to a TemporalPattern.
 */
public interface AssignableCluster extends FeatureExtractable, Noticeable {
    /**
     * Assigns an object to the given TemporalPattern.
     * 
     * @param pattern The TemporalPattern the object is assigned to.
     */
    public void assign(TemporalPattern pattern);
    
    /**
     * Returns true, if the object is allready assigned, false otherwise.
     * 
     * @return True, if the object is allready assigned, false otherwise.
     */
    public boolean isAssigned();
    
    /**
     * Gets the TemporalPattern the object is assigned to.
     * 
     * @return The TemporalPattern the object is assigned to.
     */
    public TemporalPattern getPattern();
}
