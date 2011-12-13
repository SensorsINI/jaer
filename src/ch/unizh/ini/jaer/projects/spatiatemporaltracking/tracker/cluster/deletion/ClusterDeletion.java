/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.deletion;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterListener;

/**
 *
 * @author matthias
 * 
 * The interface provides methods to delete FeatureClusters that are no longer
 * used.
 */
public interface ClusterDeletion extends ParameterListener {
    
    /**
     * Evalutes the given FeatureCluster and deletes it if it is no longer
     * used.
     * 
     * @param cluster The FeatureCluster to evaluate.
     * 
     * @return True, if the given FeatureCluster is deleted, false otherwise.
     */
    public boolean delete(FeatureCluster cluster);
}
