/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;

/**
 *
 * @author matthias
 * 
 * Concrete implementations of the abstract class have to assign a FeatureCluster
 * to a TemporalPattern according to the given cost function.
 */
public abstract class AbstractClusterAssignment implements ClusterAssignment {
    
    /** The cost function used to assign a FeatureExtractable to a TemporalPattern */
    protected ClusterCostFunction function;
    
    /*
     * Creates a new AbstractClusterAssignment.
     * 
     * @param manager The instance maintaining the parameters used by this instance.
     * @param function The cost function serving as the criterion to assign a 
     * FeatureCluster to a TemporalPattern.
     */
    public AbstractClusterAssignment(ParameterManager manager, ClusterCostFunction function) {
        manager.add(this);
        
        this.function = function;
        
        this.parameterUpdate();
    }
}
