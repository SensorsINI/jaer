/*
 * EventInterface.java
 *
 * Created on May 28, 2006, 7:04 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.event;

/**
 * The basic interface for an event
 * @author tobi
 */
public interface EventInterface<T extends BasicEvent> {
    /** Returns the number of types this type of event has, e.g. 2 for a PolarityEvent or 4 for an OrientationEvent
     @return number of types
     */
    public int getNumCellTypes();
    
    /** Returns the int type of the event, by convention starting with 0
     @return the type
     */
    public int getType();
    
    /** Copies fields from event to this event. 
     If event is a supertype of this event, added fields
     take default values.
     @param event the event to copy from
     */
    public void copyFrom(T event);
}
