/*
 * USBAEMon.java
 *
 * Created on August 28, 2005, 11:14 AM
 */

package net.sf.jaer.aemonitor;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Defines interface for an AE monitor.
 *
 * @author tobi
 */
public interface AEMonitorInterface extends HardwareInterface{
    
    /** Gets available events from driver. This call returns a reference to an AEPacket that holds the events.
     * {@link HardwareInterfaceException} is thrown if there is an error.
     *<p>
     *@return packet. A empty packet is returned if there are no events.
     *@throws HardwareInterfaceException
     * .
     */
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException ;
    
//    /** Gets available events from driver. {@link HardwareInterfaceException} is thrown if there is an error.
//     *{@link #overrunOccurred} will be true if these was an overrun of the host driver buffers.
//     *<p>
//     *@return number of events acquired. If this is zero there is no point in getting the events, because there are none.
//     *@throws HardwareInterfaceException
//     *@param aePacket a reference to an AEPacket to be filled with the events. If it is too small it will be enlarged to hold the events.
//     */
//    public int acquireAvailableEventsFromDriver(AEPacketRaw aePacket) throws HardwareInterfaceException ;
    
    /** Returns the number of events acquired by the last call to {@link
     * #acquireAvailableEventsFromDriver }
     * @return number of events acquired
     */
    public int getNumEventsAcquired();
    
    /** returns the last events acquired by {@link #acquireAvailableEventsFromDriver}
     * @return the packet of raw events
     */
    public AEPacketRaw getEvents();
    
    /** resets the timestamps to start at zero */
    public void resetTimestamps();
    
    /** Is true if an overrun occured in the driver the last time {@link
     * #acquireAvailableEventsFromDriver } was called. This flag is cleared by the next {@link #acquireAvailableEventsFromDriver}.
     *If there is an overrun, the events grabbed are the most ancient; events after the overrun are discarded. The timestamps continue on but will
     *probably be lagged behind what they should be.
     * @return true if there was an overrun.
     */
    public boolean overrunOccurred();
    
    /** Returns the size of the host buffer.
     * @return the size of the buffer in events */
    public int getAEBufferSize() ;
    
    /** Sets the size in events of the host buffer. Default is AE_BUFFER_SIZE. You can set this larger if you
     *have overruns because your host processing (e.g. rendering) is taking too long.
     *<p>
     *This call discards collected events.
     * @param AEBufferSize size of buffer in events
     */
    public void setAEBufferSize(int AEBufferSize) ;
    
    /** Enables event acquisition, e.g. sends vendor commands to enable transfers, starts buffer pool threads.
     *This method can be called, e.g. from acquireAvailableEventsFromDriver method to ensure driver is acquiring events.
     *@param enable true to start, false to stop
     */
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException ;
    
    /** @return true if event acquisition is enabled
     */
    public boolean isEventAcquisitionEnabled();
    
    /** add a PropertyChangeListener for new events
     * @param listener will be called after each {@link #acquireAvailableEventsFromDriver} call
     */
    public void addAEListener(AEListener listener);
    
    /** remove a PropertyChangeListener for new events
     @param listener to remove
     */
    public void removeAEListener(AEListener listener);
    
    /**
     * Returns max capacity of this interface in events/sec
     *@return max capacity in events/sec
     */
    public int getMaxCapacity();
    
    /**
     * Returns estimate of present event rate on this interface
     *@return estimated event rate in events/sec
     */
    public int getEstimatedEventRate();
    
    /** @return timestamp tick in us for this interface */
    public int getTimestampTickUs();
    
    /**
     * Sets the AEChip that this interface is acquiring events for
     * @param chip the chip
     */
    public void setChip(AEChip chip);
    
    /**
     * Sets the AEChip that this interface is acquiring events for
     * @return the chip
     */
    public AEChip getChip();
    
    
}


