/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.assignment;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.creation.ClusterCreation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.assignment.cost.EventCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.probability.Distributions;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * To assign a TypedEvent to a FeatureExtractable this approach searches for 
 * the best possible match and assigns the TypedEvent to it. To find the best
 * match this classes uses different cost function to evaluate the temporal and
 * the spatial matching of the event.
 * To accelerate the process this implementation uses a fast cost function 
 * to determine whether the TypedEvent has a chance to be assigned to the 
 * cluster and uses a detailed cost function to make the final decision.
 */
public class BoltzmanEventAssignment extends AbstractEventAssignment {
    
    /** 
     * Defines the chance to further analzye the TypedEvent.
     */
    private double chance;
    
    /**
     * Defines the sharpness of the probability computed out of the spatial
     * cost function.
     */
    private float spatialSharpness;
    
    /**
     * Defines the sharpness of the probability computed out of the spatial
     * cost function.
     */
    private float temporalSharpness;
    
    /** 
     * Defines the threshold for the assignment. The minimal cost has to be
     * below this value.
     */
    private double threshold;
    
    /**
     * Defines the reliability needed to assign an event to a cluster.
     */
    private float reliability;
    
    /** The cost function used to for the fast decision. */
    protected EventCostFunction fast;
    
    /** 
     * The cost function used to evaluate the spatial matching of the TypedEvent
     * and the cluster.
     */
    protected EventCostFunction spatial;
    
    /** 
     * The cost function used to evaluate the temporal matching of the TypedEvent
     * and the cluster.
     */
    protected EventCostFunction temporal;
    
    /**
     * Creates a new FastProbabilityEventAssignment.
     * 
     * @param manager The parameter manager maintaining the parameters.
     * @param storage The storage maintaining the clusters.
     * @param fast fast The cost function to compute in a very efficent way whether
     * the cluster has a chance to belong to a certain cluster.
     * @param spatial The function used to evaluate the spatial matching of the
     * TypedEvent and the cluster.
     * @param temporal  The function used to evaluate the temporal matching of the
     * TypedEvent and the cluster.
     * @param creation The instance used to create new CandidateClusters.
     */
    public BoltzmanEventAssignment(ParameterManager manager, 
                                   FeatureClusterStorage storage, 
                                   EventCostFunction fast, 
                                   EventCostFunction spatial, 
                                   EventCostFunction temporal,
                                   ClusterCreation creation) {
        super(manager, storage, creation);
        
        this.fast = fast;
        this.spatial = spatial;
        this.temporal = temporal;
    }
    
    @Override
    public void assign(TypedEvent e) {
        float max = 0;
        FeatureCluster best = null;
        
        float sum = 0;
        for (int i = 0; i < this.storage.getClusters().size(); i++) {
            if (this.fast.cost(this.storage.getClusters().get(i), e) < this.chance) {
                float s = (float)this.spatial.cost(this.storage.getClusters().get(i), e);
                float ps = 2 * (1 - Distributions.NormalDistribution.getCumulativeDistributionFunction(0, this.spatialSharpness, s));
                
                float pt = 0.5f;
                if (this.temporal.isComputable(this.storage.getClusters().get(i))) {
                    float t = (float)this.temporal.cost(this.storage.getClusters().get(i), e);
                    pt = 2 * (1 - Distributions.NormalDistribution.getCumulativeDistributionFunction(0, this.temporalSharpness, t));
                }
                
                float p = ps * pt;
                
                if (p > this.threshold) {
                    if (max < p) {
                        max = p;
                        best = this.storage.getClusters().get(i);
                        
                        sum += Math.exp(-1 * (1 - p));
                    }
                }
            }
        }
        
        if (max > 0) {
            double p = Math.exp(-1 * (1 - max)) / sum;
            if (p >= this.reliability) {
                best.assign(e);
            }
            return;
        }
        this.creation.create(e);
    }
    
    @Override
    public void parameterUpdate() {
        if (Parameters.getInstance().hasKey(Parameters.EVENT_ASSINGMENT_CHANCE)) this.chance = Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_CHANCE);
        if (Parameters.getInstance().hasKey(Parameters.EVENT_ASSINGMENT_SPATIAL_SHARPNESS)) this.spatialSharpness = Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_SPATIAL_SHARPNESS);
        if (Parameters.getInstance().hasKey(Parameters.EVENT_ASSINGMENT_TEMPORAL_SHARPNESS)) this.temporalSharpness = Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_TEMPORAL_SHARPNESS);
        if (Parameters.getInstance().hasKey(Parameters.EVENT_ASSINGMENT_THRESHOLD)) this.threshold = Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_THRESHOLD);
        if (Parameters.getInstance().hasKey(Parameters.EVENT_ASSINGMENT_DIFFERENCE)) this.reliability = Parameters.getInstance().getAsFloat(Parameters.EVENT_ASSINGMENT_DIFFERENCE);
    }
}
