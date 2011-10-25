/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface;

import de.thesycon.usbio.UsbIoBuf;
import net.sf.jaer.aemonitor.AEPacketRawPool;

/**
 * This class can translate events from UsbIo data buffers to AEPacketRaw data.
 * AEChips that have special requirements of event translation can implement methods here
 * to be called by the AEReader of particular USBIO USB interfaces.
 * 
 * @author Tobi
 */
public interface HandlesUSBIOEventTranslation {

    /** Takes data supplied by USBIO in buf and writes raw addresses and timestamp data to the write buffer from the pool of user buffers.
     * 
     * @param buf
     * @param pool 
     */
    public void translateEvents(UsbIoBuf buf, AEPacketRawPool pool);
}
