/*
 * OrientationEvent.java
 *
 * Created on May 27, 2006, 11:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 27, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.event;

/**
 * Represents an event with an orientation that can take 4 values.
 * @author tobi
 */
public class OrientationEvent extends PolarityEvent{
    
    /** The orientation value. */
    public byte orientation;
    
    /** Defaults to true; set to false to indicate unknown orientation. */
    public boolean hasOrientation=true;
    
    /** Creates a new instance of OrientationEvent */
    public OrientationEvent() {
    }
    
    @Override public int getType(){
        return orientation;
    }
    
    @Override public String toString(){
        return super.toString()+" orientation="+orientation;
    }
    
    @Override public int getNumCellTypes() {
        return 4;
    }
    
       /** copies fields from source event src to this event 
     @param src the event to copy from 
     */
    @Override public void copyFrom(BasicEvent src){
        PolarityEvent e=(PolarityEvent)src;
        super.copyFrom(e);
        if(e instanceof OrientationEvent) this.orientation=((OrientationEvent)e).orientation;
    }


}
