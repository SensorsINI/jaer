/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment.cost;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignable.AssignableCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.FeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal.SignalExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.SignalBasedTemporalPattern;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPattern;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPatternStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.correlation.Correlation;

/**
 *
 * @author matthias
 * 
 * Computes the cross-correlation between the temporal patterns given by the 
 * signal stored in the FeatureExtractable and the one provided by a instance
 * of the class TemporalPattern.
 * 
 */
public class ClusterCorrelationCostFunction extends AbstractClusterCostFunction {

    /*
     * Computes the cross-correlation between the temporal patterns given by 
     * the signal stored in the FeatureExtractable and the one provided by a 
     * instance of the class TemporalPattern.
     * 
     * @param cluster The FeatureExtractable to assign to a TemporalPattern.
     * @param pattern The TemporalPattern the FeatureExtractable has to be assigned.
     * @return The cost to assign the FeatureExtractable to the TemporalPattern.
     */
    @Override
    public double cost(AssignableCluster assignable, TemporalPattern pattern) {
        Signal signal = ((SignalExtractor)assignable.getFeatures().get(Features.Signal)).getSignal();
        
        return 1 - Correlation.getInstance().crossCorrelation(signal, pattern.getSignal());
    }

    @Override
    public TemporalPattern add(AssignableCluster assignable) {
        FeatureExtractor extractor = assignable.getFeatures().get(Features.Signal);
        if (extractor.isStatic()) {
            TemporalPattern pattern = new SignalBasedTemporalPattern(((SignalExtractor)extractor).getSignal());
            TemporalPatternStorage.getInstance().getPatterns().add(pattern);
            
            return pattern;
        }
        return null;
    }
}
