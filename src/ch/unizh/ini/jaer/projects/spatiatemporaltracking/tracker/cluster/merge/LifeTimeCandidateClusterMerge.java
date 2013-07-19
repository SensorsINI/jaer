/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.merge;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.lifetime.LifetimeExtractor;

/**
 *
 * @author matthias
 * 
 * Merges CandidateClusters that are spatial too close together.
 */
public class LifeTimeCandidateClusterMerge extends AbstractClusterMerge {
    
    /** Defines the threshold to merge two CandidateClusters. */
    private float threshold;
    
    /**
     * Creates a new LifeTimeCandidateClusterMerge.
     */
    public LifeTimeCandidateClusterMerge(ParameterManager manager, 
                                         FeatureClusterStorage storage, 
                                         MergeCostFunction function) {
        super(manager, storage, function);
        
        this.parameterUpdate();
    }
    
    @Override
    public boolean merge(FeatureCluster c1, FeatureCluster c2) {
        if (!c1.isCandidate() && !c2.isCandidate()) return false;
        
        if (this.function.cost(c1, c2) < this.threshold) {
            if (c1.isCandidate() && c2.isCandidate()) {
                int t1 = ((LifetimeExtractor)c1.getFeatures().get(Features.Lifetime)).getLifetime();
                int t2 = ((LifetimeExtractor)c2.getFeatures().get(Features.Lifetime)).getLifetime();
                
                if (t1 < t2) {
                    this.storage.delete(c1);
                }
                else {
                    this.storage.delete(c2);
                }
            }
            else {
                if (c1.isCandidate()) {
                    this.storage.delete(c1);
                }
                else {
                    this.storage.delete(c2);
                }
            }
            return true;
        }
        return false;
    }
    
    @Override
    public void parameterUpdate() {
        if (Parameters.getInstance().hasKey(Parameters.CLUSTER_MERGE_THRESHOLD)) this.threshold = Parameters.getInstance().getAsFloat(Parameters.CLUSTER_MERGE_THRESHOLD);
    }
}
