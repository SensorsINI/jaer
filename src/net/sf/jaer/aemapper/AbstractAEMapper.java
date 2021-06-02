/*
 * AbstractAEMapper.java
 *
 * Created on September 29, 2006, 10:06 PM
 *
 *  Copyright T. Delbruck, Inst. of Neuroinformatics, 2006
 */

package net.sf.jaer.aemapper;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;

/**
 * An abstract AEMapper that only needs implementation of getMapping method.
 
 * @author tobi
 */
abstract public class AbstractAEMapper implements AEMapper {
    
    AEPacketRaw outputPacket;
    AEMap map;
    
    boolean mappingEnabled=false;
    boolean passThroughEnabled=false;
    
    /** get the array of destination addresses for a source address
     @return array of destination addresses
     @param src the source address
     */
    abstract public int[] getMapping(int src);
    
    /** Sets identity mapping - all events just pass straight through.
     @param yes true to set identity mapping, false to activate normal mapper function.
     */
    public void setMappingPassThrough(boolean yes){
        passThroughEnabled=yes;
    }
    
    public boolean isMappingPassThrough(){
        return passThroughEnabled;
    }
    
    /** Enables mapping.
     @param yes true to enable mapping. False means that events will not be mapped from input to output.
     */
    public void setMappingEnabled(boolean yes){
        this.mappingEnabled=yes;
    }
    
    public boolean isMappingEnabled(){
        return mappingEnabled;
    }
    
    EventRaw tmpEvent=new EventRaw();
    
    /** Iterates over events in input packet to supply output packet
     @param input the packet of raw input events
     @return a packet of mapped output events
     */
    synchronized public AEPacketRaw mapPacket(AEPacketRaw input){
        if(!isMappingEnabled() || isMappingPassThrough()) return input;
        checkOutputPacket();
        int n=input.getNumEvents();
        int[] ain=input.getAddresses();
        int[] tin=input.getTimestamps();
        outputPacket.setNumEvents(0);
        for(int i=0;i<n;i++){
            int[] map=getMapping(ain[i]);
            if(map==null || map.length==0) continue;
            outputPacket.ensureCapacity(outputPacket.getCapacity()+map.length);
            tmpEvent.timestamp=tin[i];
            for(int j=0;j<map.length;j++){
                tmpEvent.address=(short)map[i];
                outputPacket.addEvent(tmpEvent);
            }
        }
        return outputPacket;
    }
    
    private void checkOutputPacket(){
        if(outputPacket==null) outputPacket=new AEPacketRaw();
    }
    
}
