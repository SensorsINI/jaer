/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.merge;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;

/**
 *
 * @author matthias
 * 
 * Provides some basic methods for implementations of the interface ClusterMerge.
 */
public abstract class AbstractClusterMerge implements ClusterMerge {
    
    /** The storage of the FeatureClusters. */
    protected FeatureClusterStorage storage;
    
    /** The function used to evaluate the FeatureExtractables. */
    protected MergeCostFunction function;
    
    /**
     * Creates a new AbstractClusterMerge.
     */
    public AbstractClusterMerge(ParameterManager manager, 
                                FeatureClusterStorage storage, 
                                MergeCostFunction function) {
        manager.add(this);
        
        this.storage = storage;
        this.function = function;
    }
}
