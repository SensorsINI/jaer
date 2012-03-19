/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.creation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.EventAssignable;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.Parameters;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * Uses a cost function to decide whether it is a good idea to create a new
 * CandidateCluster at a particular location.
 */
public class CostClusterCreation extends AbstractClusterCreation {

    /** Defines the maximal number of clusters. */
    private int nClusters;
    
    /** Defines the maximal number of candidate clusters. */
    private int nCandidates;
    
    /** 
     * Determines the minimal distance between a FeatureCluster and the new
     * CandidateCluster.
     */
    private float threshold;
    
    /**
     * Creates a new CostClusterCreation.
     */
    public CostClusterCreation(ParameterManager manager, 
                               FeatureClusterStorage storage, 
                               CreationCostFunction function) {
        super(manager, storage, function);
    }
    
    @Override
    public void create(TypedEvent e) {
        if (this.isValid(e)) {
            EventAssignable assignable = this.storage.addCandidateCluster();
            assignable.assign(e);
        }
    }
    
    /**
     * Checks whether a creation of a cluster is valid based on the given
     * TypedEvent.
     * 
     * @param e The TypedEvent for which a new cluster has to be created.
     * 
     * @return True, if the creation of a new cluster is valid, false otherwise.
     */
    private boolean isValid(TypedEvent e) {
        if (this.storage.getClusterNumber() < this.nClusters && 
            this.storage.getCandidateClusterNumber() < this.nCandidates) {
            
            double min = Double.MAX_VALUE;
            for (FeatureCluster c : this.storage.getClusters()) {
                double cost = this.function.cost(c, e);

                if (min > cost) {
                    min = cost;
                }
            }

            if (min > this.threshold) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public void parameterUpdate() {
        if (Parameters.getInstance().hasKey(Parameters.GENERAL_CLUSTER_N)) this.nClusters = Parameters.getInstance().getAsInteger(Parameters.GENERAL_CLUSTER_N);
        if (Parameters.getInstance().hasKey(Parameters.GENERAL_CANDIDATE_N)) this.nCandidates = Parameters.getInstance().getAsInteger(Parameters.GENERAL_CANDIDATE_N);
        if (Parameters.getInstance().hasKey(Parameters.CLUSTER_MERGE_THRESHOLD)) this.threshold = Parameters.getInstance().getAsFloat(Parameters.CLUSTER_MERGE_THRESHOLD);
    }
}
