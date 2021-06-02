/*
 * Mapper.java
 *
 * Created on September 10, 2006, 10:42 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.aemapper;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Interface for an event mapper - a device that maps packets of events from input to output.
 * @author tobi
 */
public interface AEMapper {
    
    /** maps from input to output packet using the mapping and depending on the flags
     @param input the input packet
     @return output the output packet. This is likely to be a re-used object
     */
    public AEPacketRaw mapPacket(AEPacketRaw input);
        
    /** Sets identity mapping - all events just pass straight through.
     @param yes true to set identity mapping, false to activate normal mapper function.
     */
    public void setMappingPassThrough(boolean yes);
    
    /** Enables mapping.
     @param yes true to enable mapping. False means that events will not be mapped from input to output.
     */
    public void setMappingEnabled(boolean yes);
    
    public boolean isMappingPassThrough();
    
    public boolean isMappingEnabled();
}
