/* BinocularOrientationEvent.java
 *
 * Created on 9. Juni 2006, 13:55 */

package net.sf.jaer.event.orientation;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.BinocularEvent;

/** Extends BinocularEvent to add byte orientation.
 * 
 * @author Wyvern */
public class BinocularOrientationEvent extends BinocularEvent implements OrientationEventInterface {
    /** The orientation value. */
     public byte orientation;

    /** Defaults to true; set to false to indicate unknown orientation. */
    public boolean hasOrientation=true;
   
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
     * @param src the event to copy from */
    @Override public void copyFrom(BasicEvent src){
        BinocularEvent e = (BinocularEvent)src;
        super.copyFrom(e);
        if(e instanceof BinocularOrientationEvent) {
            this.orientation=((BinocularOrientationEvent)e).orientation;
            this.hasOrientation=((BinocularOrientationEvent)e).hasOrientation;
        }
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
