/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Extends the frame based CLCamera to add packing and return of AEPacketRaw object holding frame data.
 * 
 * @author tobi
 */
public class CLRetinaHardwareInterface extends CLCamera implements AEMonitorInterface{

    public CLRetinaHardwareInterface(int cameraIndex) {
        super(cameraIndex);
    }

    /////////////////////////////////////////////////
    // this camera returns frames. 
    // We pack these frames into the AEPacketRaw and the consumer (the event extractor) interprets these to make events.
    
    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getNumEventsAcquired() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AEPacketRaw getEvents() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void resetTimestamps() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean overrunOccurred() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getAEBufferSize() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setAEBufferSize(int AEBufferSize) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void addAEListener(AEListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void removeAEListener(AEListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getMaxCapacity() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getEstimatedEventRate() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getTimestampTickUs() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setChip(AEChip chip) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public AEChip getChip() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
