/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.assignment.cost;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.probability.Distributions;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.assignable.EventAssignable;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * Uses a prediction of the occurance of events belonging to the object to
 * compute a cost and combines this cost with a spatial cost to penalize the
 * an increasing spatial distance.
 */
public class SpatialTemporalEventCostFunction extends AbstractEventCostFunction {

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
     * The cost function to evaluate the spatial closness for an event and a
     * cluster.
     */
    private EventCostFunction spatial;
    
    /**
     * The cost function to evalute the temporal closness for an event and a
     * cluster.
     */
    private EventCostFunction temporal;
    
    /**
     * Creates a new instance of the class.
     * 
     * @param spatial The cost function to evaluate the spatial closness for an 
     * event and a cluster.
     * @param temporal The cost function to evaluate the temporal closness for 
     * an event and a cluster.
     * 
     * @param spatialSharpness Defines the sharpness of the probability computed 
     * out of the spatial cost function.
     * @param temporalSharpness Defines the sharpness of the probability 
     * computed out of the temporal cost function.
     */
    public SpatialTemporalEventCostFunction(EventCostFunction spatial, 
                                            EventCostFunction temporal,
                                            float spatialSharpness,
                                            float temporalSharpness) {
        this.spatial = spatial;
        this.temporal = temporal;
        
        this.spatialSharpness = spatialSharpness;
        this.temporalSharpness = temporalSharpness;
        
    }
    
    /*
     * Uses a prediction of the occurance of events belonging to the object to
     * compute a cost and combines this cost with a spatial cost to penalize the
     * an increasing spatial distance.
     * 
     * @param cluster The FeatureExtractable the TypedEvent has to be assigned.
     * @param e The TypedEvent to assign to a FeatureExtractable.
     * @return The cost to assign the TypedEvent to the FeatureExtractable.
     */
    @Override
    public double cost(EventAssignable assignable, TypedEvent e) {
        float s = (float)this.spatial.cost(assignable, e);
        float ps = (1 - Distributions.ExponentialDistribution.getCumulativeDistributionFunction(1 / this.spatialSharpness, s));

        float pt = 0.5f;
        if (this.temporal.isComputable(assignable)) {
            float t = (float)this.temporal.cost(assignable, e);
            pt = (1 - Distributions.ExponentialDistribution.getCumulativeDistributionFunction(1 / this.temporalSharpness, t));
        }

        return -Math.log(ps * pt);
    }
}
