/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.hardwareinterface.usb.linux;

import ch.unizh.ini.caviar.aemonitor.AEListener;
import ch.unizh.ini.caviar.aemonitor.AEMonitorInterface;
import ch.unizh.ini.caviar.aemonitor.AEPacketRaw;
import ch.unizh.ini.caviar.biasgen.Biasgen;
import ch.unizh.ini.caviar.biasgen.BiasgenHardwareInterface;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;

/**
 * The Tmpdiff128 retina under linux using the JSR-80 
 * linux java USB library 
 * (<a href="http://sourceforge.net/projects/javax-usb">JSR-80 project</a>).
 * 
 * @author Martin Ebner (martin_ebner)
 */
public class CypressFX2RetinaLinux implements AEMonitorInterface, BiasgenHardwareInterface {

    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getNumEventsAcquired() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public AEPacketRaw getEvents() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void resetTimestamps() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean overrunOccurred() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getAEBufferSize() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setAEBufferSize(int AEBufferSize) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean isEventAcquisitionEnabled() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void addAEListener(AEListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void removeAEListener(AEListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getMaxCapacity() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getEstimatedEventRate() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getTimestampTickUs() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setChip(AEChip chip) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public AEChip getChip() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String[] getStringDescriptors() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int[] getVIDPID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public short getVID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public short getPID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public short getDID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getTypeName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void close() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void open() throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean isOpen() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void sendPotValues(Biasgen biasgen) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void flashPotValues(Biasgen biasgen) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
