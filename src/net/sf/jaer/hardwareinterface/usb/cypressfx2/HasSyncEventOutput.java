/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.usb.cypressfx2;

/**
 * This hardware device can emit or detect special synchronization events on some external sync IO pin.
 * The events thus detected on the external input are sent along with the normal events.
 * This interface is also used by devices that can synchronize their timestamps. The master timestamp device enables sync events, and the slaves disable them.
 * The the master device clocks the slaves which detect the sync events emitted by the master.
 * 
 * @author tobi
 */
public interface HasSyncEventOutput {

//    public final int SYNC_ADDRESS=0xFFFE;

    
    /** Sets whether sync events are enabled.
     * 
     * @param yes true to enable sync events.
     */
    public void setSyncEventEnabled(boolean yes);
    
    /** Returns whether sync events are enabled.
     * 
     * @return true if enabled.
     */
    public boolean isSyncEventEnabled();

}
