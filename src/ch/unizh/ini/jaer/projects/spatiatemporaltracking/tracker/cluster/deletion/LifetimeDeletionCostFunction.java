/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.deletion;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.activity.ActivityExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.lifetime.LifetimeExtractor;

/**
 *
 * @author matthias
 * 
 * The cost function evaluates the given FeatureCluster according to the 
 * activity of the FeatureCluster. If the activity is high, it is very likely
 * that the cluster observs a blinking LED and therefore the cost to delete the
 * FeatureCluster has to be high.
 * An addtional condition ensures that the FeatureCluster has a chance to 
 * survive. This is ensured by providing a minimal lifetime of the 
 * FeatureCluster.
 */
public class LifetimeDeletionCostFunction extends AbstractDeletionCostFunction {

    /** The minimal lifetime of the FeatureCluster. */
    private int lifetime = 100000;
    
    @Override
    public double cost(FeatureCluster cluster) {
        if (((LifetimeExtractor)cluster.getFeatures().get(Features.Lifetime)).getLifetime() < this.lifetime) {
            cluster.getFeatures().get(Features.Activity);
            
            return Double.MAX_VALUE;
        }
        
        return ((ActivityExtractor)cluster.getFeatures().get(Features.Activity)).getActivity();
    }
}
