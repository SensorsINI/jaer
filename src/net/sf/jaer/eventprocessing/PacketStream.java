/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.util.concurrent.CyclicBarrier;

import net.sf.jaer.event.EventPacket;

/**
 * This interface pretty much just returns eventPackets.  Its function is to 
 * provide a common interface between AEviewers and Event Filters for grabbing packets.
 * 
 * @author Peter
 */
public interface PacketStream {
    
    EventPacket getPacket();
    
    /**
     * Sets the semaphore for a packet stream.  This only needs to be properly 
     * implemented for subclasses of Thread - otherwise, just leave this method 
     * empty and you'll be fine.
     */
    void setSemaphore(CyclicBarrier barr);
    
    String getName();
    
    /** Check if packet is ready */
    public boolean isReady();
    
    /** Do what it takes to get your packets to a ready state.  If for whatever
     * reason (stream disabled, etc) they don't process, return false.
     */
    public boolean process();
    
}
