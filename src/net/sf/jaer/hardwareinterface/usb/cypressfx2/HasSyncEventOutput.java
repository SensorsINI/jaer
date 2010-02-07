/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.usb.cypressfx2;

/**
 * This hardware device can emit special synchronization events on some external sync input.
 * 
 * @author tobi
 */
public interface HasSyncEventOutput {

    public final int SYNC_ADDRESS=0xFFFE;

    public void setSyncEventEnabled(boolean yes);
    public boolean isSyncEventEnabled();

}
