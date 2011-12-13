/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.deletion.cost;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;

/**
 *
 * @author matthias
 * 
 * Implementations of this cost function have to evaluate the given 
 * FeatureCluster to make a decision whether the FeatureCluster has to be
 * removed or not.
 */
public interface DeletionCostFunction {
    
    /**
     * The method has to evaluate the given FeatureCluster to make a decision 
     * whether the FeatureCluster has to be removed or not.
     * 
     * @param cluster The FeatureCluster to evaluate.
     * @return The cost to delete the FeatureCluster.
     */
    public double cost(FeatureCluster cluster);
}
