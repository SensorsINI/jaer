/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment;

/**
 *
 * @author matthias
 * 
 * Concrete implementations of this abstract class have to provide methods to 
 * compute the cost to assign a FeatureExtractable to a TemporalPattern.
 */
public abstract class AbstractClusterCostFunction implements ClusterCostFunction {
    
    /** A constant to defining a horrible solution.  */
    public static final int COST_EXCEEDED = 100000;
}
