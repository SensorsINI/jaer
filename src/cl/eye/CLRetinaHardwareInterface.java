/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import java.beans.PropertyChangeSupport;
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

    private int frameCounter=0;
   protected AEChip chip=null;
   protected PropertyChangeSupport support=new PropertyChangeSupport(this);
    AEPacketRaw packet=new AEPacketRaw(320*240);
    int[] frameBuffer=packet.getAddresses();
    int[] timestamps=packet.getTimestamps();
     
    public CLRetinaHardwareInterface(int cameraIndex) {
        super(cameraIndex);
    }

    /////////////////////////////////////////////////
    // this camera returns frames. 
    // We pack these frames into the AEPacketRaw and the consumer (the event extractor) interprets these to make events.
    
    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        getCameraFrame(frameBuffer, 100);
        packet.setNumEvents(320*240);
        timestamps[0]=frameCounter;
        return packet;
    }

    @Override
    public int getNumEventsAcquired() {
        if(packet==null) return 0; else return packet.getNumEvents();
    }

    @Override
    public AEPacketRaw getEvents() {
        return packet;
    }

    @Override
    public void resetTimestamps() {
        frameCounter=0;
    }

    @Override
    public boolean overrunOccurred() {
        return false;
    }

    @Override
    public int getAEBufferSize() {
        return 0;
    }

    @Override
    public void setAEBufferSize(int AEBufferSize) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        if(enable) startCamera(); else stopCamera();
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        return cameraStarted;
    }

     @Override
    public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

    @Override
    public int getMaxCapacity() {
        return 0;
    }

    @Override
    public int getEstimatedEventRate() {
        return 0;
    }

    @Override
    public int getTimestampTickUs() {
        return 1;
    }

    
    @Override
    public void setChip(AEChip chip) {
        this.chip=chip;
    }

    @Override
    public AEChip getChip() {
        return chip;
    }
}
