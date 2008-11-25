/*
 * EventExtractorInterface.java
 *
 * Created on September 2, 2005, 10:07 AM
 */

package net.sf.jaer.chip;

/**
 * Interface for methods that extract event information (e.g. x,y,pol) from address-events.
 * @author tobi
 @deprecated should not be used, just left over from early development
 
 */
public interface EventExtractorInterface {
    
    /** extracts events to another representation
     *@param addresses the raw address-events
     */
       public void extract(int[] addresses, int[] timestamps);
}
