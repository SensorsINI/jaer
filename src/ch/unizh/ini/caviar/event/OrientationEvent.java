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
 <p>
 Orientation type output takes values 0-3; 0 is a horizontal edge (0 deg),  1 is an edge tilted up and to right (rotated CCW 45 deg),
 2 is a vertical edge (rotated 90 deg), 3 is tilted up and to left (rotated 135 deg from horizontal edge).
 
 * @author tobi
 */
public class OrientationEvent extends PolarityEvent {
    
    /** The orientation value. */
    public byte orientation;
    
    /** Defaults to true; set to false to indicate unknown orientation. */
    public boolean hasOrientation=true;
    
    /** Creates a new instance of OrientationEvent */
    public OrientationEvent() {
    }
    
    /**
     Orientation type output takes values 0-3; 0 is a horizontal edge (0 deg),  1 is an edge tilted up and to right (rotated CCW 45 deg),
     * 2 is a vertical edge (rotated 90 deg), 3 is tilted up and to left (rotated 135 deg from horizontal edge).
     @see #hasOrientation
     */
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
