/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.creation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.FeatureExtractable;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * This type of a cost function has to compute the cost to create a new
 * cluster for a given event considering a particular cluster. The cost
 * has to be high if it is likely that the new cluster will influence the
 * existing cluster.
 */
public interface CreationCostFunction {
    
    /**
     * The method has to evaluate the given FeatureCluster and the given
     * TypedEvent to make a decision whether it is a good idea to create a
     * new cluster at the events location.
     * 
     * @param cluster The given FeatureCluster.
     * @param e The event for which a new cluster has to be created.
     * 
     * @return The cost to create a new cluster at the events position.
     */
    public double cost(FeatureCluster cluster, TypedEvent e);
}
