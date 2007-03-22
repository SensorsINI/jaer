/*
 * EventRaw.java
 *
 * Created on November 6, 2005, 10:32 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.aemonitor;

/**
 * A raw address-event, having a timestamp and raw address
 * @author tobi
 */
public class EventRaw extends Event{
    public short address;

    /** Creates a new instance of EventRaw */
    public EventRaw() {
        super();
    }
     
    /** Creates a new instance of EventRaw 
     @param a the address
     @param t the timestamp
     */
    public EventRaw(short a, int t) {
        super(t);
        address=a;
    }
    
    public String toString(){
        return "EventRaw with address "+address+" and timestamp "+timestamp;
    }
    
}
