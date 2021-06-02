/* OrientationEvent.java
 *
 * Created on May 27, 2006, 11:49 PM
 *
 * Copyright May 27, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich */

package net.sf.jaer.event.orientation;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/** Represents an event with an orientation that can take 4 values.
 * <p>
 * Orientation type output takes values 0-3; 0 is a horizontal edge (0 deg),  1 is an edge tilted up and to right (rotated CCW 45 deg),
 * 2 is a vertical edge (rotated 90 deg), 3 is tilted up and to left (rotated 135 deg from horizontal edge).
 * 
 * @author tobi
 * @deprecated Due to the ApsDvs / Dvs problems there is now the 
 *             {@see DvsOrientationEvent} and the {@see ApsDvsOrientationEvent} 
 *             that both implement the {@see OrientationEventInterface} */
@Deprecated
public class OrientationEvent extends PolarityEvent {
    // <editor-fold defaultstate="collapsed" desc="-- DEPRECATED --">
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
     * @return 
     @see #hasOrientation
     */
    @Override 
    public int getType(){
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
    
    /** represents a unit orientation.
     The x and y fields represent relative offsets in the x and y directions by these amounts. */
    public static final class UnitVector{
        public float x, y;
        UnitVector(float x, float y){
            float l=(float)Math.sqrt(x*x+y*y);
            x=x/l;
            y=y/l;
            this.x=x;
            this.y=y;
        }
    }
    
    /**
     *     An array of 4 nearest-neighbor unit vectors going CCW from horizontal.
     *     <p>
     *    these unitDirs are indexed by inputType, then by (inputType+4)%4 (opposite direction)
     *    when input type is orientation, then input type 0 is 0 deg horiz edge, so first index could be to down, second to up
     *    so list should start with down
     */
    public static final UnitVector[] unitVectors={
        new UnitVector(1,0), 
        new UnitVector(1,1), 
        new UnitVector(0,1), 
        new UnitVector(-1,1), 
    };
    // </editor-fold>
}
