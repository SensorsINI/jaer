/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.EventAssignable;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.occurance.OccurancePredictor;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * Uses a prediction of the occurance of events belonging to the object to
 * compute a cost. As more the occurance of the event deviates from the
 * predicted temporal position, as higher has the cost to be.
 */
public class OccuranceEventCostFunction extends AbstractEventCostFunction {

    /** 
     * Defines the measured distribution of the events belonging to the same 
     * transition.
     */
    private int distribution = 100;
    
    /*
     * Uses a prediction of the occurance of events belonging to the object to
     * compute a cost. As more the occurance of the event deviates from the
     * predicted temporal position, as higher has the cost to be.
     * 
     * @param cluster The FeatureExtractable the TypedEvent has to be assigned.
     * @param e The TypedEvent to assign to a FeatureExtractable.
     * @return The cost to assign the TypedEvent to the FeatureExtractable.
     */
    @Override
    public double cost(EventAssignable assignable, TypedEvent e) {
        OccurancePredictor op = ((OccurancePredictor)assignable.getFeatures().get(Features.Occurance));
        
        return op.getDistance(e.type, e.timestamp);
    }
    
    @Override
    public boolean isComputable(EventAssignable assignable) {
        OccurancePredictor op = ((OccurancePredictor)assignable.getFeatures().get(Features.Occurance));
        return op.isPredictable();
    }
}
