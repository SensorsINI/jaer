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
    
    /** The built-in reference to the output packet */
    protected EventPacket out=null;
    
    /** Resets the output packet to be a new packet if none has been instanced or clears the packet
     if it exists
     */
    protected void resetOut(){
        if(out==null){
            out=new EventPacket();
        }else{
            out.clear();
        }
    }
    
    /** checks out packet to make sure it is the same type as the 
     input packet. This method is used for filters that must pass output
     that has same event type as input.
     @param in the input packet
     @see #out
     */
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
            Class oldClass=out.getEventClass();
            out=new EventPacket(outClass);
           log.info("oldClass="+oldClass+" outClass="+outClass+"; allocated new "+out);
         }
    }
    
    /** Subclasses implement this method to define custom processing
     @param in the input packet
     @return the output packet
     */
    public abstract EventPacket<?> filterPacket(EventPacket<?> in);
    
    /** Subclasses should call this super initializer */
    public EventFilter2D(AEChip chip){
        super(chip);
        this.chip=chip;
    }
    
    /** overrides EventFilter type in EventFilter */
    protected EventFilter2D enclosedFilter;
    
    /** A filter can enclose another filter and can access and process this filter. Note that this
     processing is not automatic. Enclosing a filter inside another filter means that it will
     be built into the GUI as such
     @return the enclosed filter
     */
    public EventFilter2D getEnclosedFilter() {
        return this.enclosedFilter;
    }
    
    /** A filter can enclose another filter and can access and process this filter. Note that this
     processing is not automatic. Enclosing a filter inside another filter means that it will
     be built into the GUI as such.
     @param enclosedFilter the enclosed filter
     */
    public void setEnclosedFilter(final EventFilter2D enclosedFilter) {
        super.setEnclosedFilter(enclosedFilter,this);
        this.enclosedFilter = enclosedFilter;
    }
    
    /** Resets the filter
     @param yes true to reset
     */
    synchronized public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        if(yes){
            resetOut();
        }else{
            out=null; // garbage collect
        }
    }
    
}
