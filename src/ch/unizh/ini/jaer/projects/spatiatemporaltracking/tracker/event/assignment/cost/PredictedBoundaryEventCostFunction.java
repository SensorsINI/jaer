/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.assignment.cost;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.assignable.EventAssignable;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.boundary.BoundaryExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.position.PositionPredictor;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * Uses the boundary of the observed object to compute a cost to assign an 
 * event to the object. The boundary of an object determines the area in which
 * most of the events belong to the object.
 * Furthermore it uses several predictors to compute the objects position in
 * the future to improve the quality of the assignment.
 */
public class PredictedBoundaryEventCostFunction extends AbstractEventCostFunction {

    /*
     * Uses the boundary of the observed object to compute a cost to assign an 
     * event to the object.
     * Furthermore it uses several predictors to compute the objects position 
     * in the future to improve the quality of the assignment.
     * 
     * @param cluster The FeatureExtractable the TypedEvent has to be assigned.
     * @param e The TypedEvent to assign to a FeatureExtractable.
     * @return The cost to assign the TypedEvent to the FeatureExtractable.
     */
    @Override
    public double cost(EventAssignable assignable, TypedEvent e) {
        Vector p = ((PositionPredictor)assignable.getFeatures().get(Features.PositionPredictor)).getPosition(e.timestamp);
        
        BoundaryExtractor boundary = ((BoundaryExtractor)assignable.getFeatures().get(Features.Boundary));
        float major = boundary.getMajorLength();
        float minor = boundary.getMinorLength();
        
        /**
         * distance in x-direction.
         */
        double dx = 0;
        if (!(p.get(0) + major >= e.x &&
                p.get(0) - major <= e.x)) {
            dx = Math.min(Math.pow(p.get(0) + major - e.x, 2.0), 
                          Math.pow(p.get(0) - major - e.x, 2.0));
        }
        
        /**
         * distance in y-direction.
         */
        double dy = 0;
        if (!(p.get(1) + minor >= e.y &&
                p.get(1) - minor <= e.y)) {
            dy = Math.min(Math.pow(p.get(1) + minor - e.y, 2.0), 
                          Math.pow(p.get(1) - minor - e.y, 2.0));
        }
        return Math.sqrt(dx + dy);
    }   
}
