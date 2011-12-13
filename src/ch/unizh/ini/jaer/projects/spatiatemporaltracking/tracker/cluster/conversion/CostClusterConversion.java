/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.conversion;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.CandidateCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.conversion.cost.ConversionCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.Parameters;

/**
 *
 * @author matthias
 */
public class CostClusterConversion extends AbstractClusterConversion {
    private int threshold = 1000000;
    
    /** Defines the maximal number of clusters. */
    private int nClusters;
    
    public CostClusterConversion(ParameterManager manager, FeatureClusterStorage storage, ConversionCostFunction function) {
        super(manager, storage, function);
    }

    @Override
    public boolean convert(CandidateCluster cluster) {
        double cost = this.function.cost(cluster);
        
        if (this.storage.getClusterNumber() < this.nClusters){
            if (cost > this.threshold) {
                cluster.convert(this.storage.addCluster());
                this.storage.delete(cluster);
                
                return true;
            }
        }
        else {
            this.storage.delete(cluster);
            
            return true;
        }
        return false;
    }
    
    @Override
    public void parameterUpdate() {
        if (Parameters.getInstance().hasKey(Parameters.GENERAL_CLUSTER_N)) this.nClusters = Parameters.getInstance().getAsInteger(Parameters.GENERAL_CLUSTER_N);
    }
}
