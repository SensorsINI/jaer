/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.merge;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;

/**
 *
 * @author matthias
 * 
 * Implementations of this cost function have to evaluate the given 
 * FeatureClusters to make a decision whether the FeatureClusters have to be
 * merged or not.
 */
public interface MergeCostFunction {
    
    /**
     * The method has to evaluate the given FeatureCluster and the given
     * TypedEvent to make a decision whether it is a good idea to create a
     * new cluster at the events location.
     * 
     * @param c1 The first cluster for the merge.
     * @param c2 The second cluster for the merge.
     * @return The cost to merge the two clusters.
     */
    public double cost(FeatureCluster c1, FeatureCluster e2);
}
