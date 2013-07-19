/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event;


/**
 *
 * @author matthias
 * 
 * This abstract class provides some basic methods used by implementations of
 * the interface EventCostFunction.
 */
public abstract class AbstractEventCostFunction implements EventCostFunction {
    
    @Override
    public boolean isComputable(EventAssignable assignable) {
        return true;
    }
}
