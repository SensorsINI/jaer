/*
 * PacketSuffix.java
 *
 * jAER 3: view of only the events appended since a watermark.
 */
package net.sf.jaer.event;

import java.util.IdentityHashMap;

/**
 * Slices a {@link PacketBundle} to the events appended after a {@link Mark}.
 * Suffix views share the pooled {@link BasicEvent} objects, so
 * {@code filteredOut} set while filtering the suffix stays on the events the
 * display later renders.
 */
public final class PacketSuffix {

    private PacketSuffix() {
    }

    /**
     * Sizes of packets already processed. Identity of the packet object is the
     * key, because USB demux grows the same packet across URBs.
     */
    public static final class Mark {

        private final IdentityHashMap<TypedDataPacket, Integer> sizes = new IdentityHashMap<>();

        /** Replaces the watermark with the current size of each packet. */
        public void capture(PacketBundle bundle) {
            sizes.clear();
            if (bundle == null) {
                return;
            }
            for (TypedDataPacket packet : bundle.snapshot()) {
                sizes.put(packet, packet.getSize());
            }
        }

        public void clear() {
            sizes.clear();
        }

        /**
         * @return marked size, or {@code -1} when {@code packet} was not in the mark
         */
        public int sizeOf(TypedDataPacket packet) {
            Integer n = sizes.get(packet);
            return n == null ? -1 : n;
        }
    }

    /**
     * Packets appended since {@code mark}, plus a suffix view of packets that
     * grew. Packets already fully processed are omitted. A mark size past the
     * current size (buffer reset) includes the whole packet.
     */
    public static PacketBundle slice(PacketBundle in, Mark mark) {
        PacketBundle out = new PacketBundle();
        if (in == null) {
            return out;
        }
        for (TypedDataPacket packet : in.snapshot()) {
            int size = packet.getSize();
            if (size <= 0) {
                continue;
            }
            int at = mark == null ? -1 : mark.sizeOf(packet);
            if (at < 0 || at > size) {
                out.add(packet);
            } else if (at < size) {
                out.add(suffixOf(packet, at));
            }
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TypedDataPacket suffixOf(TypedDataPacket packet, int from) {
        if (packet instanceof EventPacket events) {
            return events.viewFrom(from);
        }
        if (packet instanceof ImuPacket imu) {
            return imu.viewFrom(from);
        }
        // FramePacket size is 0 or 1. A new frame was not in the mark.
        // A frame that grew from empty is the whole frame.
        return packet;
    }
}
