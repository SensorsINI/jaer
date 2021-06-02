
package net.sf.jaer.event.orientation;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEventInterface;

/** Common interface for all events signaling an orientation
 * @author tobi */
public interface OrientationEventInterface extends PolarityEventInterface{
    
    public byte getOrientation();
    public void setOrientation(byte orientation);
    public boolean isHasOrientation();
    public void setHasOrientation(boolean yes);
    public void copyFrom(BasicEvent src);
    
    /** represents a unit orientation.
     * The x and y fields represent relative offsets in the x and y 
     * directions by these amounts. The inputs are automatically normalized
     * to represent a valid UnitVector */
    public static final class UnitVector{
        public float x, y;
        
        UnitVector(float x, float y){
            float l = (float)Math.sqrt(x*x+y*y);
            x = x/l;
            y = y/l;
            this.x = x;
            this.y = y;
        }
        
        @Override public String toString (){
            return String.format("(%f,%f)",x,y);
        }
    }
    
    /** An array of 4 nearest-neighbor unit vectors going CCW from horizontal.
     * <p>
     * These unitDirs are indexed by inputType, then by (inputType+4)%4 (opposite direction)
     * When input type is orientation, then input type of 0 corresponds to
     * 0 degree horizontal edge, so first index could be to down, second to up
     * so list should start with down */
    public static final UnitVector[] unitVectors={
        new UnitVector( 1,0), 
        new UnitVector( 1,1), 
        new UnitVector( 0,1), 
        new UnitVector(-1,1), 
    };
}
