
package uk.ac.imperial.pseye;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Extends the frame based PSEyeCamera to add packing and return of AEPacketRaw object holding frame data.
 * 
 */
public class PSEyeHardwareInterface extends PSEyeCamera implements AEMonitorInterface {

    /**
     * event supplied to listeners when new events are collected. this is final because it is just a marker for the listeners that new events are available
     */
    public final PropertyChangeEvent newEventPropertyChange = new PropertyChangeEvent(this, "NewEvents", null, null);
    private int frameCounter = 0;
    private long startTimeUs = System.currentTimeMillis() * 1000;
    protected AEChip chip = null;
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);
    private PSEyeFramePacketRaw packet = new PSEyeFramePacketRaw(320 * 240);

    public PSEyeHardwareInterface(int cameraIndex) {
        super(cameraIndex);
    }
        
    /**
     * This camera returns frames. 
     * This method returns all frames of image data produced by the data capture thread. 
     * We pack this data into the AEPacketRaw and the 
     * consumer (the event extractor) interprets these to make events.
     * 
     * @return the raw RGBA pixel data from frames of the camera. The pixel data is packed in the AEPacketRaw addresses array. The pixel color data depends on the CameraMode of the CLCamera.
     * The timestamp of the frame is in the first timestamp of the packets timestamp array - the rest of the elements are untouched. The timestamps are untouched except for the first one, which is set to the System.currentTimeMillis*1000-startTimeUs.
     *
     */
    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        // check manager running
        if (!frameManager.running) return null;
        
        // keep consuming until queue empty
        int nframes = frameManager.getFrameCount();
        if (nframes == 0) return null;
        
        PSEyeFrame frame = null;
        int count = 0;
        packet.nFrames = 0;
        int frameSize = frameManager.frameSize;
        // loop across available frames
        for (int i = 0; i < nframes; i++) {
            // load frame
            frame = frameManager.popFrame();
            if (frame != null) {
                // check to see if resolution change (unlikely but possible here)
                if (frameSize != frame.getSize()) {
                    // if already read frames of diffrent size, break
                    // otherwise rest frame size and continue
                    if (count > 0) 
                        break;
                    else
                        frameSize = frame.getSize();
                }
                packet.ensureCapacity(count + frameSize);
                
                // copy frame data to passed array
                frame.copyData(packet.getAddresses(), i * frameSize);
                packet.getTimestamps()[i * frameSize] = (int) (frame.getTimeStamp() - startTimeUs);
                packet.nFrames++;
                
                // put frame back into producer queue
                frameManager.pushFrame(frame);
                frameCounter++; // TODO notify AE listeners here or in thread acquiring frames
                count += frameSize;
            }
            // check to see if frame manager has been stopped
            if (!frameManager.running) {
                if (count > 0) break;
                else return null;
            }
        }
        packet.frameSize = frameSize;
        packet.setNumEvents(count);
        support.firePropertyChange(newEventPropertyChange);
        return (AEPacketRaw) packet;
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
        return (AEPacketRaw) packet;
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
