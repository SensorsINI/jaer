/*
 * AEInputStreamInterface.java
 *
 * Created on February 3, 2006, 4:39 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.eventio;

import java.io.IOException;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * The capabilities of the AEFileInputStream. Used in players that use these input streams.
 * This interface can rewind, go forwards as well as backwards, etc, as when reading from a file.
 * 
 * @author tobi
 */
public interface AEFileInputStreamInterface extends InputDataFileInterface {
    
    /** Reads a raw event packet of n events
     @param n the number of events to read
     @throws IOException if there is a problem, e.g. end of file
     */
    AEPacketRaw readPacketByNumber(int n) throws IOException;
    
    /**
     * returns an AEPacketRaw at least dt long up to the max size of the buffer or until end-of-file.
     * Events are read as long as the timestamp until (and including) the event whose timestamp is greater (for dt>0) than
     * startTimestamp+dt, where startTimestamp is the last timestamp from the previous call.
     *
     * @param dt the timestamp different in units of the timestamp (usually us)
     @return the packet, always at least one event even if there is no event in the interval dt.
     @throws IOException if there is any problem
     */
    AEPacketRaw readPacketByTime(int dt) throws IOException;
    
//    /** Reads a packet up to and including {@code time} as its timestamp
//     *@param time in timestamp units
//     *@param forwards true to read forwards, false to read backwards
//     */
//    public AEPacketRaw readPacketToTime(int time, boolean forwards) throws IOException;

    /** Checking for wrapped time exceptions can be disabled for reasons of speed or corrupted data files.
     * @return true if exceptions are checked (default)
     */
    public boolean isNonMonotonicTimeExceptionsChecked();
    
    /** Sets whether the input stream is checked for timestamp nonmonotonicity.
     * 
     * @param yes true to check (default).
     */
    public void setNonMonotonicTimeExceptionsChecked(boolean yes); // TODO should be a general property of any AE input stream, not just files. e.g. network too.
    
    
}
