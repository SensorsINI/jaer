/*
 * AEConstants.java
 *
 * Created on October 12, 2006, 12:06 PM
 *
 *  Copyright T. Delbruck, Inst. of Neuroinformatics, 2006
 */

package net.sf.jaer.aemonitor;

import net.sf.jaer.eventio.AEFileInputStream;

/**
 * Holds constants related to AE interface.
 * @author tobi
 */
public class AEConstants {
    
    /** The default timestamp tick value in microseconds.
     * The timestamp is a signed 32-bit int value. If the tick is 1us, the timestamp wraps every int32 us, which is about 4295 seconds which is 71 minutes. Time is negative, then positive, then negative again.
     @see AEFileInputStream
     */
    public static final int TICK_DEFAULT_US=1;
    
//    /** The most positive possible timestamp time in seconds */
    
    /** Creates a new instance of AEConstants */
    private AEConstants() {
    }
    
}
