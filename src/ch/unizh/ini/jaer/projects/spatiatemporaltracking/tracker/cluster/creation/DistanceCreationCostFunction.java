/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.creation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * Uses the distance between the FeatureCluster and the given TypedEvent to
 * to compute a cost to create a new CandidateCluster.
 */
public class DistanceCreationCostFunction extends AbstractCreationCostFunction {

    @Override
    public double cost(FeatureCluster cluster, TypedEvent e) {
        Vector p = ((PositionExtractor)cluster.getFeatures().get(Features.Position)).getPosition();
        return Math.pow(p.get(0) - e.x, 2.0) + Math.pow(p.get(1) - e.y, 2.0);
    }
}
