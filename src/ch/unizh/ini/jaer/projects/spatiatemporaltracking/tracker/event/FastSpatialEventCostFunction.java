/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.EventAssignable;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * This cost function has to decide on a very simple criterion whether the
 * TypedEvent has a chance to be assigned to a particular EventAssignable.
 */
public class FastSpatialEventCostFunction extends AbstractEventCostFunction {

    /*
     * Uses the current position of the observed object represented by the
     * EventAssignable to decide whether the event has a chance to be assigned
     * to it.
     * 
     * @param cluster The FeatureExtractable the TypedEvent has to be assigned.
     * @param e The TypedEvent to assign to a FeatureExtractable.
     * @return The cost to assign the TypedEvent to the FeatureExtractable.
     */
    @Override
    public double cost(EventAssignable assignable, TypedEvent e) {
        PositionExtractor pe = (PositionExtractor)assignable.getFeatures().get(Features.Position);
        
        return Math.max(Math.pow(pe.getPosition().get(0) - e.x, 2.0), Math.pow(pe.getPosition().get(1) - e.y, 2.0));
    }
}
