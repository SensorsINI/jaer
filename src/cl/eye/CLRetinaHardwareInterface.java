/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import java.beans.PropertyChangeEvent;
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
public class CLRetinaHardwareInterface extends CLCamera implements AEMonitorInterface {

    /**
     * event supplied to listeners when new events are collected. this is final because it is just a marker for the listeners that new events are available
     */
    public final PropertyChangeEvent newEventPropertyChange = new PropertyChangeEvent(this, "NewEvents", null, null);
    private int frameCounter = 0;
    private long startTimeUs = System.currentTimeMillis() * 1000;
    protected AEChip chip = null;
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);
    private AEPacketRaw packet = new AEPacketRaw(320 * 240);
    private int[] frameBuffer = packet.getAddresses();
    private int[] timestamps = packet.getTimestamps();

    public CLRetinaHardwareInterface(int cameraIndex) {
        super(cameraIndex);
    }
        
    public CLRetinaHardwareInterface(int cameraIndex, CLCamera.CameraMode cameraMode) {
        super(cameraIndex, cameraMode);
    }

    /**
     * This camera returns frames. 
     *  This method return a single frame of image data. We pack this single frame into the AEPacketRaw and the 
     * consumer (the event extractor) interprets these to make events.
     * <p>
     * The AEFileInputStream subclass for the PSEyeCLModelRetina actually can read multiple input frames. It assigns the event timestamps using the
     * frame timestamps we write here.
     * 
     * @return the raw RGBA pixel data from one QVGA frame from the camera. The pixel data is packed in the AEPacketRaw addresses array. The pixel color data depends on the CameraMode of the CLCamera.
     * The timestamp of the frame is in the first timestamp of the packets timestamp array - the rest of the elements are untouched. The timestamps are untouched except for the first one, which is set to the System.currentTimeMillis*1000-startTimeUs.
     *
     */
        @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        getCameraFrame(frameBuffer, 300); // TODO acquire multiple frames between calls, pack into single packet and return that packet with multiple frame timestamps
        packet.setNumEvents(320 * 240);
        timestamps[0] = (int) (System.currentTimeMillis() * 1000 - startTimeUs);
        frameCounter++; // TODO notify AE listeners here or in thread acquiring frames
        support.firePropertyChange(newEventPropertyChange);
        return packet;
    }

    @Override
    public int getNumEventsAcquired() {
        if (packet == null) {
            return 0;
        } else {
            return packet.getNumEvents();
        }
    }

    /** Returns the collected event packet.
     * 
     * @return the packet of raw events, consisting of the pixels values.
     */
    @Override
    public AEPacketRaw getEvents() {
        return packet;
    }

    /** Resets the timestamps to the current system time in ms.
     * 
     */
    @Override
    public void resetTimestamps() {
        frameCounter = 0;
        startTimeUs = System.currentTimeMillis() * 1000;
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

    /** Starts and stops the camera.
     * 
     * @param enable true to run the camera
     * @throws HardwareInterfaceException 
     */
    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        if (enable) {
            startCamera();
        } else {
            stopCamera();
        }
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        return cameraStarted;
    }

    /** Listeners are called on every new frame of data.
     * 
     * @param listener 
     */
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
        this.chip = chip;
    }

    @Override
    public AEChip getChip() {
        return chip;
    }
}
