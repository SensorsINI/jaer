/*
 * AESequencerInterface.java
 *
 * Created on 23 de noviembre de 2005, 13:18
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.aesequencer;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Interface for AE sequencer devices.
 * @author raphael
 */
public interface AESequencerInterface extends HardwareInterface {
    
//    public void sendEventsToDevice(AEPacketRaw events) throws HardwareInterfaceException;
    
    public int getNumEventsSent();
    
    public int getNumEventsToSend();
    
    /** resets the timestamps to start at zero */
    public void resetTimestamps();
    
    /** @return true if event sequencing is enabled
     */
    public boolean isEventSequencingEnabled();
    
    /** 
     * Returns max capacity of this interface in events/sec
     *@return max capacity in events/sec
     */
    public int getMaxCapacity();
    
    /**
     * Returns estimate of present out event rate on this interface
     *@return estimated event rate in events/sec
     */
    public int getEstimatedOutEventRate();
    
    /** @return timestamp tick in us for this interface */
    public int getTimestampTickUs();
     
    /**
     Offers a packet to be sequenced to the sequencer. Calling this automatically should disable looping the sequenced data.
     @param packet the packet to add to the tail of the queue.
     */
    public void offerPacketToSequencer(AEPacketRaw packet);

        /** enables continuous sequencing, if enabled the AEWriter rewinds if it reaches the
     * end of the packet and restarts sending from the beginning. otherwise it just stops sequencing.
     @param set true to loop packet, false to sequence a single packet
     **/
    public void setLoopedSequencingEnabled(boolean set);
    
    /**
     @return true if sequencing will loop back to start at end of data
     */
    public boolean isLoopedSequencingEnabled();

    /** starts sequencing of events, starts AEWriter.
     @param eventsToSend the events that should be sequenced, timestamps are realtive to last event,
       inter spike interval must not be bigger than 2^16-1
     */
    public void startSequencing(AEPacketRaw eventsToSend) throws HardwareInterfaceException;
    
    
    /** stops sequencing of events
     * from the driver
     */
    public void stopSequencing() throws HardwareInterfaceException;
    

}
