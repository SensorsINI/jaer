/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.conversion.cost;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.lifetime.LifetimeExtractor;

/**
 *
 * @author matthias
 */
public class LifetimeConversionCostFunction extends AbstractConversionCostFunction {

    @Override
    public double cost(FeatureCluster cluster) {
        return ((LifetimeExtractor)cluster.getFeatures().get(Features.Lifetime)).getLifetime();
    }
}
