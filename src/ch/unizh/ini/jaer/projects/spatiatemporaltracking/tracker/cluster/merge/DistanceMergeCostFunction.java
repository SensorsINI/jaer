/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.merge;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;

/**
 *
 * @author matthias
 * 
 * The cost function evaluates the given FeatureExtractables according to their 
 * distance to each other.
 */
public class DistanceMergeCostFunction extends AbstractMergeCostFunction {

    @Override
    public double cost(FeatureCluster c1, FeatureCluster c2) {
        Vector p1 = ((PositionExtractor)c1.getFeatures().get(Features.Position)).getPosition();
        Vector p2 = ((PositionExtractor)c2.getFeatures().get(Features.Position)).getPosition();
        
        return p1.copy().substract(p2).squaredNorm();
    }
}
