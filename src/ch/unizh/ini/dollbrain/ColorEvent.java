package ch.unizh.ini.dollbrain;

import ch.unizh.ini.caviar.event.*;
/*
 * TypedEvent.java
 *
 * Created on May 28, 2006, 9:20 AM
 *
 *
 *
 *Copyright May 28, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

/**
 * Represents an event with a byte type. This is a legacy class to support previous implementations.
 * @author tobi
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
