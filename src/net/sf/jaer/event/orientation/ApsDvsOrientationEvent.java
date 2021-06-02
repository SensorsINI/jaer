/* ApsDvsOrientationEvent.java
 *
 * Created on May 27, 2006, 11:49 PM
 *
 *Copyright May 27, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich */

package net.sf.jaer.event.orientation;

import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/** Represents an event with an orientation that can take 4 values; subclass of ApsDvsEvent.
 * <p>
 * Orientation type output takes values 0-3;                <br>
 * 0 is horizontal edge              (0 deg),               <br>
 * 1 is tilted up and to right edge  (rotated CCW 45 deg),  <br>
 * 2 is vertical edge                (rotated CCW 90 deg),  <br>
 * 3 is tilted up and to left edge   (rotated CCW 135 deg).
 *
 * @author tobi */
public class ApsDvsOrientationEvent extends ApsDvsEvent implements OrientationEventInterface {
    
    /** The orientation value. */
    public byte orientation;
    
    /** Defaults to true; set to false to indicate unknown orientation. */
    public boolean hasOrientation=true;
    
    /** Creates a new instance of OrientationEvent */
    public ApsDvsOrientationEvent() {
    }
    
    /** Orientation type output takes values 0-3; 0 is a horizontal edge (0 deg),  1 is an edge tilted up and to right (rotated CCW 45 deg),
     * 2 is a vertical edge (rotated 90 deg), 3 is tilted up and to left (rotated 135 deg from horizontal edge).
     * @return the orientation type
     * @see #hasOrientation */
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
        if(e instanceof ApsDvsOrientationEvent) this.orientation=((ApsDvsOrientationEvent)e).orientation;
    }
    
    /** gets the Orientation of the event
     * @return the orientation */
    @Override public byte getOrientation() {
        if(!isHasOrientation()) throw new IllegalStateException("the event is flaged as not having an orientation!");
        return orientation;
    }

    /** sets the orientation of the event
     * @param orientation the orientation to set  */
    @Override public void setOrientation(final byte orientation) {
        this.orientation = orientation;
        setHasOrientation(true);
    }

    /** checks if the event has an orientation
     * @return the hasOrientation */
    @Override public boolean isHasOrientation() {
        return hasOrientation;
    }

    /** set the hasOrientation flag.
     * IMPORTANT: When this is set to true without the orientation being set
     * to a meaningful value there might be unexpected behavior.
     * @param hasOrientation the hasOrientation to set */
    @Override public void setHasOrientation(final boolean hasOrientation) {
        this.hasOrientation = hasOrientation;
    }
}
