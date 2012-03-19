/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.creation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;

/**
 *
 * @author matthias
 * 
 * Provides some basic methods and attributes used by implementations of the
 * interface ClusterCreation.
 */
public abstract class AbstractClusterCreation implements ClusterCreation {

    /** The storage of the FeatureClusters. */
    protected FeatureClusterStorage storage;
    
    /** 
     * The function used to evaluate whether a new CandidateCluster has to be
     * created or not. 
     */
    protected CreationCostFunction function;
    
    /**
     * Creates a new AbstractClusterCreation.
     * 
     * @param manager The manager storing the parameters.
     * @param storage The storage of the FeatureClusters.
     * @param function The function used to evaluate whether a new 
     * CandidateCluster has to be created or not.
     */
    public AbstractClusterCreation(ParameterManager manager, 
                                   FeatureClusterStorage storage, 
                                   CreationCostFunction function) {
        manager.add(this);
        
        this.storage = storage;
        this.function = function;
        
        this.parameterUpdate();
    }
}
