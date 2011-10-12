/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;

/**
 * Reads raw data files written from the CLCamera, so they can be played back.
 * @author Tobi
 */
public class CLCameraFileInputStream extends AEFileInputStream {

    /** Packets can hold this many frames at most. */
    public static final int MAX_FRAMES=20;
    
    /** Assumed number of pixels in each frame, each event holding the RGB data in address field of raw event. */
    public static final int NPIXELS = 320 * 240; // TODO assumes QVGA, can be obtained from CameraModel in principle, but only QVGA models for now
    /** Assumed frame interval in ms */
    public static final int FRAME_INTERVAL_MS = 15; 
    

    public CLCameraFileInputStream(File f) throws IOException {
        super(f);
        packet.ensureCapacity(NPIXELS*MAX_FRAMES);
    }

    /** Overrides to read image frames rather than events. 
     * The events are extracted by the PSEyeCLModelRetina event extractor.
     * 
     * @param nframes the number of frames to read
     * @return
     * @throws IOException 
     */
    @Override
    public synchronized AEPacketRaw readPacketByNumber(int nframes) throws IOException {
        if (!firstReadCompleted) {
            fireInitPropertyChange();
        }
        int[] addr = packet.getAddresses();
        int[] ts = packet.getTimestamps();
        int oldPosition = position();
        EventRaw ev;
        int count = 0;
        for (int frame = 0; frame < nframes; frame++) {
            for (int i = 0; i < NPIXELS; i++) {
                ev = readEventForwards();
                addr[count] = ev.address;
                ts[count] = ev.timestamp;
//                if(ev.timestamp!=0)System.out.println("count="+count+" ev.timetamp="+ev.timestamp);
                count++;
            }
        }
        packet.setNumEvents(count);
        getSupport().firePropertyChange(AEInputStream.EVENT_POSITION, oldPosition, position());
        return packet;
//        return new AEPacketRaw(addr,ts);
    }

    /** Reads the next n frames, computed from dt/1000/FRAME_INTERVAL_MS
     * 
     * @param dt the time in us.
     * @return some number of frames of image data, computed from dt, but at least 1.
     * @throws IOException on IO exception
     */
    @Override
    public synchronized AEPacketRaw readPacketByTime(int dt) throws IOException {
        int nframes = dt / 1000 / FRAME_INTERVAL_MS;
        if (nframes < 1) {
            nframes = 1;
        }

        return readPacketByNumber(nframes);
    }
    private EventRaw tmpEvent = new EventRaw();

    /** Reads the next frame forwards.
     * @throws EOFException at end of file
     * @throws NonMonotonicTimeException
     * @throws WrappedTimeException
     */
    private EventRaw readEventForwards() throws IOException {
        try {
            tmpEvent.address = byteBuffer.getInt();;
            tmpEvent.timestamp = byteBuffer.getInt();;
            position++;

            return tmpEvent;
        } catch (BufferUnderflowException e) {
            try {
                mapNextChunk();
                return readEventForwards();
            } catch (IOException eof) {
                byteBuffer = null;
                System.gc(); // all the byteBuffers have referred to mapped files and use up all memory, now free them since we're at end of file anyhow
                getSupport().firePropertyChange(AEInputStream.EVENT_EOF, position(), position());
                throw new EOFException("reached end of file");
            }
        } catch (NullPointerException npe) {
            rewind();
            return readEventForwards();
        } finally {
        }
    }
}
