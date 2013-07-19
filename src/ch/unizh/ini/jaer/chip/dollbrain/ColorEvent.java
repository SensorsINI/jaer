package ch.unizh.ini.jaer.chip.dollbrain;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.TypedEvent;
/*
 * ColorEvent.java
 *
 * Created on Nov 28, 2007, 9:20 AM
 *
 *
 *
 *Copyright May 28, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

/**
 * Represents an event with a short color. 
 * @author raphael
 */
public class ColorEvent extends TypedEvent {
    
    public short color=0;
    
    /** Creates a new instance of TypedEvent */
    public ColorEvent() {
    }
    
    public short getColor(){
        return color;
    }
    
    @Override public String toString(){
        return super.toString()+" color="+color;
    }
    
    /** copies fields from source event src to this event
     @param src the event to copy from
     */
    @Override public void copyFrom(BasicEvent src){
        ColorEvent e=(ColorEvent)src;
        super.copyFrom(e);
        this.color=e.color;
    }
    
    
}
