/*
 * BinocularXYDisparityEvent.java
 *
 * Created on 6. October 2010, 13:45
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.event.orientation;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.orientation.BinocularOrientationEvent;

/**
 *
 * @author rogister based on BinocularDisparityEvent
 */
public class BinocularXYDisparityEvent extends BinocularDisparityEvent {
    public byte xdisparity;
    public byte ydisparity;
    public int matchtime;
    
    /** Creates a new instance of BinocularDisparityEvent */
    public BinocularXYDisparityEvent() {
    }
    
    @Override public int getType(){
        return (int)Math.round(Math.sqrt(xdisparity*xdisparity+ydisparity*ydisparity));
    }
    
    @Override public String toString(){
        return super.toString() + " xdisparity=" + xdisparity + " ydisparity=" + ydisparity;
    }
    
    @Override public int getNumCellTypes() {
        return 1;
    }
    
       /** copies fields from source event src to this event 
     @param src the event to copy from 
     */
    @Override public void copyFrom(BasicEvent src){
        BinocularEvent e = (BinocularEvent)src;
        super.copyFrom(e);
        if(e instanceof BinocularOrientationEvent) this.orientation = ((BinocularOrientationEvent)e).orientation;
        if(e instanceof BinocularDisparityEvent) this.disparity = ((BinocularDisparityEvent)e).disparity;
         if(e instanceof BinocularXYDisparityEvent) {
             this.xdisparity = ((BinocularXYDisparityEvent)e).xdisparity;
             this.ydisparity = ((BinocularXYDisparityEvent)e).ydisparity;
             this.matchtime = ((BinocularXYDisparityEvent)e).matchtime;


         }

    }    
}
