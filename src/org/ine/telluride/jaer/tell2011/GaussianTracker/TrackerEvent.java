/*
 * TrackerEvent.java
 *
 * Created on July 7, 2011
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 */

package org.ine.telluride.jaer.tell2011.GaussianTracker;

import net.sf.jaer.event.BasicEvent;

/**
 * Represents an event with an integeter flag for the tracker that caused the event.
 
 * @author Michael Pfeiffer
 */
public class TrackerEvent extends BasicEvent {
    
    /** The tracker ID value. */
    public int trackerID;
    
    
    /** Creates a new instance of TrackerEvent */
    public TrackerEvent() {
    }
    
    /**
     Returns the ID of the tracker that produced the event.
     */
    public int getTrackerID(){
        return trackerID;
    }
    
    @Override public String toString(){
        return super.toString()+" trackerID="+trackerID;
    }
    
   
    /** copies fields from source event src to this event
     @param src the event to copy from
     */
    @Override public void copyFrom(BasicEvent src){
        BasicEvent e=(BasicEvent)src;
        super.copyFrom(e);
        if(e instanceof TrackerEvent) this.trackerID=((TrackerEvent)e).trackerID;
    }
    
}
