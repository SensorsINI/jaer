/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.deletion;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.deletion.cost.DeletionCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.Parameters;

/**
 *
 * @author matthias
 * 
 * This class evaluates the FeatureCluster according to the given cost function
 * and deletes the cluster if the the cost is below a certain threshold.
 */
public class CostClusterDeletion extends AbstractClusterDeletion {
    
    /** Defines the threshold to delete a cluster. */
    private double threshold;
    
    /**
     * Creates a new ConsistencyCostDeletion.
     */
    public CostClusterDeletion(ParameterManager manager, 
                               FeatureClusterStorage storage, 
                               DeletionCostFunction function) {
        super(manager, storage, function);
    }

    @Override
    public boolean delete(FeatureCluster cluster) {
        double cost = this.function.cost(cluster);
        
        if (cost < this.threshold) {
            this.storage.delete(cluster);
            return true;
        }
        return false;
    }
    
    @Override
    public void parameterUpdate() {
        if (Parameters.getInstance().hasKey(Parameters.CLUSTER_DELETION_THRESHOLD)) this.threshold = Parameters.getInstance().getAsFloat(Parameters.CLUSTER_DELETION_THRESHOLD);
    }
}
