/*
 * PacketBundlePool.java
 *
 * jAER 3.0: double-buffer PacketBundle interchange between USB capture and ViewLoop.
 */
package net.sf.jaer.event;

/**
 * Twin of {@link net.sf.jaer.aemonitor.AEPacketRawPool} for typed
 * {@link PacketBundle}s. Capture thread writes; consumer swaps and reads.
 *
 * @author tobi
 */
public class PacketBundlePool {

    private PacketBundle[] buffers;
    volatile int readBuffer = 0;
    volatile int writeBuffer = 1;

    public PacketBundlePool() {
        allocate();
        reset();
    }

    public final synchronized void swap() {
        if (buffers == null) {
            allocate();
        }
        final PacketBundle completedWrite = writeBuffer();
        if (completedWrite.getAcquisitionMetadata() != null && !completedWrite.isSealed()) {
            completedWrite.seal();
        }
        if (readBuffer == 0) {
            readBuffer = 1;
            writeBuffer = 0;
        } else {
            readBuffer = 0;
            writeBuffer = 1;
        }
        writeBuffer().clear();
    }

    public final synchronized PacketBundle readBuffer() {
        if (buffers == null) {
            allocate();
        }
        return buffers[readBuffer];
    }

    public final synchronized PacketBundle writeBuffer() {
        if (buffers == null) {
            allocate();
        }
        return buffers[writeBuffer];
    }

    public final synchronized void reset() {
        if (buffers == null) {
            allocate();
        }
        readBuffer = 0;
        writeBuffer = 1;
        buffers[0].clear();
        buffers[1].clear();
    }

    private void allocate() {
        buffers = new PacketBundle[]{new PacketBundle(), new PacketBundle()};
    }
}
