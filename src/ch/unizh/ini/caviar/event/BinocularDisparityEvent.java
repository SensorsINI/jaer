/*
 * BinocularDisparityEvent.java
 *
 * Created on 27. Juni 2006, 13:45
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.event;

/**
 *
 * @author Wyvern
 */
public class BinocularDisparityEvent extends BinocularOrientationEvent {
    public byte disparity;
    
    /** Creates a new instance of BinocularDisparityEvent */
    public BinocularDisparityEvent() {
    }
    
    @Override public int getType(){
        return disparity;
    }
    
    @Override public String toString(){
        return super.toString() + " disparity=" + disparity;
    }
    
    @Override public int getNumCellTypes() {
        return 1;
    }
    
       /** copies fields from source event src to this event 
     @param src the event to copy from 
     */
    @Override public void copyFrom(BasicEvent src){
        BinocularOrientationEvent e = (BinocularOrientationEvent)src;
        super.copyFrom(e);
        if(e instanceof BinocularDisparityEvent) this.disparity = ((BinocularDisparityEvent)e).disparity;
    }    
}
