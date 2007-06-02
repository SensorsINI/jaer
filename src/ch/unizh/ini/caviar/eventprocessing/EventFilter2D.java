/*
 * EventFilter2D.java
 *
 * Created on November 9, 2005, 8:49 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.eventprocessing;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import java.beans.*;

/**
 * A filter that filters a packet of 2D events.
 *
 * @author tobi
 */
abstract public class EventFilter2D extends EventFilter {
    protected EventPacket out=null;
    
//    /** Subclasses can use this instance to limit their own processing time in conjunction with other methods.
//     Typically they call timeLimiter.init(getTimeLimitMs) before iterating over events, and then check timeLimit with timeLimiter.isTimedOut().
//     
//     @see #setTimeLimitMs
//     @see #getTimeLimitMs
//     @see #setTimeLimitEnabled
//     @see #isTimeLimitEnabled
//     @see TimeLimitingFilter
//     */
//    protected TimeLimiter timeLimiter=new TimeLimiter();
//    private int timeLimitMs=10;
//    {setPropertyTooltip("timeLimitMs","if timeLimitEnabled, this is limit for packet processing time per packet (rest of packet discarded)");}
//    protected boolean timeLimitEnabled=false;
//    {setPropertyTooltip("timeLimitEnabled","if implemented by filter, limits time spent processing each event packet");}
    
    protected void resetOut(){
        if(out==null){
            out=new EventPacket();
        }else{
            out.clear();
        }
    }
    
    /** checks out packet to make sure it is the same type as the input packet. This method is used for filters that must pass output
     that has same event type as input.
     @param in the input packet*/
    protected void checkOutputPacketEventType(EventPacket in){
        if( out!=null && out.getEventClass()==in.getEventClass() ) return;
        out=new EventPacket(in.getEventClass());
    }
    
    /** checks out packet to make sure it is the same type as the given class. This method is used for filters that must pass output
     that has a particular output type.
     @param outClass the output packet.
     */
    protected void checkOutputPacketEventType(Class<? extends BasicEvent> outClass){
        if( out.getEventClass()!=outClass){
            out=new EventPacket(outClass);
        }
    }
    
    public abstract EventPacket<?> filterPacket(EventPacket<?> in);
    
    public EventFilter2D(AEChip chip){
        this.chip=chip;
    }
    
    /** overrides EventFilter type in EventFilter */
    protected EventFilter2D enclosedFilter;
    
    public EventFilter2D getEnclosedFilter() {
        return this.enclosedFilter;
    }
    
    public void setEnclosedFilter(final EventFilter2D enclosedFilter) {
        super.setEnclosedFilter(enclosedFilter);
        this.enclosedFilter = enclosedFilter;
    }
    
    synchronized public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        if(yes){
            resetOut();
        }else{
            out=null; // garbage collect
        }
    }
    
//    public int getTimeLimitMs() {
//        return timeLimitMs;
//    }
//
//    public void setTimeLimitMs(int timeLimitMs) {
//        this.timeLimitMs = timeLimitMs;
//        if(getEnclosedFilter()!=null) enclosedFilter.setTimeLimitMs(timeLimitMs);
//    }
//
//    final public boolean isTimeLimitEnabled() {
//        return timeLimitEnabled;
//    }
//
//    public void setTimeLimitEnabled(boolean limitTimeEnabled) {
//        this.timeLimitEnabled = limitTimeEnabled;
//        if(getEnclosedFilter()!=null) enclosedFilter.setTimeLimitEnabled(limitTimeEnabled);
//    }
    
    /** Returns true if this PropertyDescriptor is a time limiting property. Used to control GUI construction.
     @return true if property relates to limiting filter processing time
     */
    static boolean isTimeLimitProperty(PropertyDescriptor p){
        if(p.getName().equals("timeLimitEnabled")) return true;
        if(p.getName().equals("timeLimitMs")) return true;
        return false;
    }
 
}
