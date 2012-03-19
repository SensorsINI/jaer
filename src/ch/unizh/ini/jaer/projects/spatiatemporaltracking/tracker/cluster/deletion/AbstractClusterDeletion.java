/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.deletion;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;

/**
 *
 * @author matthias
 * 
 * Implementations of this abstract class have to evaluate FeatureClusters and
 * to delete them if they are no longer used.
 */
public abstract class AbstractClusterDeletion implements ClusterDeletion {
    
    /** The storage of the FeatureClusters. */
    protected FeatureClusterStorage storage;
    
    /** The function used to evaluate the FeatureCluster. */
    protected DeletionCostFunction function;
    
    /**
     * Creates a new AbstractConsistencyDeletion.
     * 
     * @param manager The manager storing the parameters.
     * @param storage The storage of the FeatureClusters.
     * @param function The function used to evaluate the FeatureCluster.
     */
    public AbstractClusterDeletion(ParameterManager manager, 
                                   FeatureClusterStorage storage, 
                                   DeletionCostFunction function) {
        manager.add(this);
        
        this.storage = storage;
        this.function = function;
        
        this.parameterUpdate();
    }
}
