/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment.cost;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignable.AssignableCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPattern;

/**
 *
 * @author matthias
 * 
 * The interface provides methods to compute the cost to assign a FeatureExtractable
 * to a TemporalPattern.
 */
public interface ClusterCostFunction {
    
    /*
     * Adds the FeatureExtractable to the list of TemporalPatterns. Used if a the 
     * FeatureExtractable contains an unkown temporal pattern.
     * 
     * @param cluster The FeatureExtractable containing the unkown temporal pattern.
     * @return The new TemporalPattern.
     */
    public TemporalPattern add(AssignableCluster assignable);
    
    /*
     * Computes the cost to assign the given FeatureExtractable to the given
     * TemporalPattern.
     * 
     * @param cluster The FeatureExtractable to assign to a TemporalPattern.
     * @param pattern The TemporalPattern the FeatureExtractable has to be assigned.
     * @return The cost to assign the FeatureExtractable to the TemporalPattern.
     */
    public double cost(AssignableCluster assignable, TemporalPattern pattern);
}
