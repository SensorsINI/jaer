/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.merge;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterListener;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;

/**
 *
 * @author matthias
 * 
 * The interface provides methods to merge two FeatureClusters that are to
 * similar.
 */
public interface ClusterMerge extends ParameterListener {
    
    /**
     * Evaluates the two given FeatureCluster and merges it if necessary.
     * 
     * @param c1 The first FeatureCluster.
     * @param c2 The second FeatureCluster.
     * 
     * @return True, if the two FeatureExtractable are merged, false otherwise.
     */
    public boolean merge(FeatureCluster c1, FeatureCluster c2);
}
