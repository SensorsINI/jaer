/*
 * BinocularOrientationEvent.java
 *
 * Created on 9. Juni 2006, 13:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.event;

/**
 *
 * @author Wyvern
 */
public class BinocularOrientationEvent extends BinocularEvent {
    public byte orientation;
    
    /** Creates a new instance of BinocularOrientationEvent */
    public BinocularOrientationEvent() {
    }
   
    @Override public int getType(){
        return orientation;
    }
    
    @Override public String toString(){
        return super.toString()+" orientation="+orientation;
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
        if(e instanceof BinocularOrientationEvent) this.orientation=((BinocularOrientationEvent)e).orientation;
    }    
}
