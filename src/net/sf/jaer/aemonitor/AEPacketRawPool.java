package net.sf.jaer.aemonitor;

/**
 * Object that holds pool of AEPacketRaw that handles data interchange between capture and other (rendering) threads.
 * While the capture thread (AEReader.processData) captures events into one buffer (an AEPacketRaw) the other thread (AEViewer.run()) can
 * render the events. The only time the monitor on the pool needs to be acquired is when swapping or initializing the buffers, to prevent
 * either referencing unrelated data or having memory change out from under you.
 */
public class AEPacketRawPool {

    private AEPacketRaw[] buffers;
//    private AEPacketRaw lastBufferReference;
    volatile int readBuffer = 0;
    volatile int writeBuffer = 1;
    private AEMonitorInterface outer;

    public AEPacketRawPool(AEMonitorInterface outer) {
        super();
        this.outer = outer;
        allocateMemory();
        reset();
    }

    /** Swaps the read and write buffers so that the buffer that was getting written is now the one that is read from, and the one that was read from is
     * now the one written to. Thread safe. This method is called by the consumer.
     */
    public final synchronized void swap() {
//        lastBufferReference = buffers[readBuffer];
        if (readBuffer == 0) {
            readBuffer = 1;
            writeBuffer = 0;
        } else {
            readBuffer = 0;
            writeBuffer = 1;
        }
        writeBuffer().clear();
        writeBuffer().overrunOccuredFlag = false;
    }

    /** @return buffer that consumer reads from. */
    public final synchronized AEPacketRaw readBuffer() {
        return buffers[readBuffer];
    }

    /** @return buffer that acquisition thread writes to. */
    public final synchronized AEPacketRaw writeBuffer() {
        return buffers[writeBuffer];
    }

    /** Set the current buffer to be the first one and clear the write buffer */
    public final synchronized void reset() {
        readBuffer = 0;
        writeBuffer = 1;
        buffers[writeBuffer].clear();
        // new events go into this buffer which should be empty
        buffers[readBuffer].clear();
    }

    /** allocates AEPacketRaw buffers each with capacity of  */
    public final void allocateMemory() {
        buffers = new AEPacketRaw[2];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new AEPacketRaw();
            buffers[i].ensureCapacity(outer.getAEBufferSize());
        }
    }
}
