/*
 * Event.java
 *
 * Created on November 6, 2005, 10:31 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.aemonitor;

/**
 * Has a timestamp and that's all. Not used for packets because too inefficient. Used for some methods.
 *
 * @author tobi
 */
public class Event {
    public int timestamp;
    short x,y;
    byte[] types;
    
    /** Creates a new instance of Event */
    public Event() {
    }
    
    /** create an Event with a timestamp, x, y, and a variable length number of bytes types */
    public Event(int timestamp, short x, short y, byte... types){
        this.timestamp=timestamp;
        this.x=x;
        this.y=y;
        this.types=types;
    }
    
    /** Creates a new instance of Event */
    public Event(int t) {
        timestamp=t;
    }
    
}
