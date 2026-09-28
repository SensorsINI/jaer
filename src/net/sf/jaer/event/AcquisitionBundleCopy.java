/*
 * AcquisitionBundleCopy.java
 *
 * Deep copy of a filter-chain PacketBundle for the acquisition record queue.
 */
package net.sf.jaer.event;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Copies a {@link PacketBundle} so a later URB can reuse the pooled events
 * without changing a slice already queued for disk.
 * <p>
 * Does not use {@link EventPacket#appendCopyOfEvent}: that clears
 * {@code filteredOut} on the source event.
 */
public final class AcquisitionBundleCopy {

    private AcquisitionBundleCopy() {
    }

    public static void copyInto(PacketBundle dst, PacketBundle src) {
        dst.clear();
        if (src == null) {
            return;
        }
        dst.setRawPacket(copyRaw(src.getRawPacket()));
        for (TypedDataPacket packet : src.snapshot()) {
            TypedDataPacket copied = copyPacket(packet);
            if (copied != null) {
                dst.add(copied);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TypedDataPacket copyPacket(TypedDataPacket packet) {
        if (packet instanceof EventPacket events) {
            return events.deepCopy();
        }
        if (packet instanceof ImuPacket imu) {
            return imu.deepCopy();
        }
        if (packet instanceof FramePacket frame) {
            return frame.copy();
        }
        return null;
    }

    private static AEPacketRaw copyRaw(AEPacketRaw src) {
        if (src == null) {
            return null;
        }
        int n = src.getNumEvents();
        if (n <= 0) {
            return null;
        }
        AEPacketRaw dst = new AEPacketRaw(n);
        System.arraycopy(src.getAddresses(), 0, dst.getAddresses(), 0, n);
        System.arraycopy(src.getTimestamps(), 0, dst.getTimestamps(), 0, n);
        dst.setNumEvents(n);
        return dst;
    }
}
