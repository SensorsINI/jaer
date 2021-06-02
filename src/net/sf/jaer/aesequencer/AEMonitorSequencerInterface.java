/*
 * AEMonitorSequencerInterface.java
 *
 * Created on 24 de noviembre de 2005, 13:01
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.aesequencer;

import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 *  Inteface for monitoring and sequencing for devices that support both.
 * @author raphael
 */
public interface AEMonitorSequencerInterface extends AEMonitorInterface, AESequencerInterface {
    
    /** starts sequencing and monitoring of events, starts AEReader and AEWriter and sends vendor request to device
     @param eventsToSend the events that should be sequenced, timestamps are realtive to last event,
       inter spike interval must not be bigger than 2^16-1
     */
    public void startMonitoringSequencing(AEPacketRaw eventsToSend) throws HardwareInterfaceException;
    
    
    /** stops monitoring and sequencing of events, gets and returns the last events
     * from the driver
     * @return AEPacketRaw: the last events
     */
    public AEPacketRaw stopMonitoringSequencing() throws HardwareInterfaceException;
    
    
}
