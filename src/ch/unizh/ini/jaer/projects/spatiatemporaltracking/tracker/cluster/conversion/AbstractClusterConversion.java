/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.conversion;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;

/**
 *
 * @author matthias
 */
public abstract class AbstractClusterConversion implements ClusterConversion {
    
    /** The storage of the FeatureClusters. */
    protected FeatureClusterStorage storage;
    
    /** The function used to evaluate the FeatureCluster. */
    protected ConversionCostFunction function;
    
    /**
     * Creates a new AbstractConsistencyDeletion.
     * 
     * @param manager The manager storing the parameters.
     * @param storage The storage of the FeatureClusters.
     * @param function The function used to evaluate the FeatureCluster.
     */
    public AbstractClusterConversion(ParameterManager manager, FeatureClusterStorage storage, ConversionCostFunction function) {
        manager.add(this);
        
        this.storage = storage;
        this.function = function;
        
        this.parameterUpdate();
    }
}
