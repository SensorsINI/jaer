/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.conversion.cost;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;

/**
 *
 * @author matthias
 */
public interface ConversionCostFunction {
    
    public double cost(FeatureCluster cluster);
}
