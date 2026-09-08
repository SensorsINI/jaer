/*
 * PacketBundle.java
 *
 * jAER 3.0: time-ordered list of homogeneous typed packets from one slice.
 */
package net.sf.jaer.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Ordered collection of {@link TypedDataPacket}s produced from one acquisition
 * or playback timeslice. Each contained packet is homogeneous (one
 * {@link PacketType}); the bundle may interleave polarity, frame, and IMU
 * packets in timestamp order.
 * <p>
 * Replaces the former single mixed {@code ApsDvsEventPacket} as the unit passed
 * through extract → filter → render → log.
 * <p>
 * Structural mutation ({@link #add}, {@link #clear}) is synchronized. Iterators
 * snapshot the packet list so a concurrent {@code extractBundle} (file preview
 * on the live extractor, or the next ViewLoop extract of a reused buffer)
 * cannot throw {@link java.util.ConcurrentModificationException} in
 * {@code FilterChain.filterBundle}.
 *
 * @author tobi
 */
public class PacketBundle implements Iterable<TypedDataPacket> {

    private static final TypedDataPacket[] EMPTY = new TypedDataPacket[0];

    private final ArrayList<TypedDataPacket> packets = new ArrayList<>(8);

    /** Optional raw AE slice that produced this bundle (legacy / debug). */
    private net.sf.jaer.aemonitor.AEPacketRaw rawPacket;

    public PacketBundle() {
    }

    public synchronized void clear() {
        packets.clear();
        rawPacket = null;
    }

    public synchronized void add(TypedDataPacket packet) {
        if (packet != null && !packet.isEmpty()) {
            packets.add(packet);
        }
    }

    /**
     * Adds a packet even if empty (rarely needed for placeholders).
     */
    public synchronized void addAllowEmpty(TypedDataPacket packet) {
        if (packet != null) {
            packets.add(packet);
        }
    }

    public synchronized int getNumPackets() {
        return packets.size();
    }

    public synchronized boolean isEmpty() {
        return packets.isEmpty();
    }

    public synchronized TypedDataPacket get(int i) {
        return packets.get(i);
    }

    /**
     * Snapshot of packet refs. Safe to iterate after the bundle is mutated.
     */
    public synchronized TypedDataPacket[] snapshot() {
        return packets.toArray(EMPTY);
    }

    /**
     * Shallow copy of the packet list (same {@link TypedDataPacket} objects).
     * Extractors that reuse one scratch bundle should return this so ViewLoop
     * can filter while the next extract clears the scratch.
     */
    public PacketBundle copyPacketList() {
        PacketBundle c = new PacketBundle();
        synchronized (this) {
            c.packets.addAll(this.packets);
            c.rawPacket = this.rawPacket;
        }
        return c;
    }

    public synchronized List<TypedDataPacket> getPackets() {
        return Collections.unmodifiableList(Arrays.asList(snapshot()));
    }

    /**
     * First polarity (DVS) packet in the bundle, or null.
     */
    public synchronized EventPacket<?> getFirstPolarityPacket() {
        for (TypedDataPacket p : packets) {
            if (p.getPacketType() == PacketType.POLARITY && p instanceof EventPacket) {
                return (EventPacket<?>) p;
            }
        }
        return null;
    }

    /**
     * First frame packet, or null.
     */
    public synchronized FramePacket getFirstFramePacket() {
        for (TypedDataPacket p : packets) {
            if (p instanceof FramePacket) {
                return (FramePacket) p;
            }
        }
        return null;
    }

    /**
     * First IMU packet, or null.
     */
    public synchronized ImuPacket getFirstImuPacket() {
        for (TypedDataPacket p : packets) {
            if (p instanceof ImuPacket) {
                return (ImuPacket) p;
            }
        }
        return null;
    }

    /**
     * Total polarity events across all POLARITY packets.
     */
    public synchronized int getNumPolarityEvents() {
        int n = 0;
        for (TypedDataPacket p : packets) {
            if (p.getPacketType() == PacketType.POLARITY) {
                n += p.getSize();
            }
        }
        return n;
    }

    public synchronized long getFirstTimestampUs() {
        long t = Long.MAX_VALUE;
        boolean any = false;
        for (TypedDataPacket p : packets) {
            if (!p.isEmpty()) {
                long ft = p.getFirstTimestampUs();
                if (ft < t) {
                    t = ft;
                }
                any = true;
            }
        }
        return any ? t : 0;
    }

    public synchronized long getLastTimestampUs() {
        long t = Long.MIN_VALUE;
        boolean any = false;
        for (TypedDataPacket p : packets) {
            if (!p.isEmpty()) {
                long lt = p.getLastTimestampUs();
                if (lt > t) {
                    t = lt;
                }
                any = true;
            }
        }
        return any ? t : 0;
    }

    public synchronized net.sf.jaer.aemonitor.AEPacketRaw getRawPacket() {
        return rawPacket;
    }

    public synchronized void setRawPacket(net.sf.jaer.aemonitor.AEPacketRaw rawPacket) {
        this.rawPacket = rawPacket;
    }

    /**
     * Snapshot iterator — does not fail if the bundle is cleared or appended
     * during the loop (file-preview {@code extractBundle} vs ViewLoop filter).
     */
    @Override
    public Iterator<TypedDataPacket> iterator() {
        return Arrays.asList(snapshot()).iterator();
    }

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder("PacketBundle[");
        sb.append(packets.size()).append(" packets:");
        for (TypedDataPacket p : packets) {
            sb.append(' ').append(p.getPacketType()).append('(').append(p.getSize()).append(')');
        }
        sb.append(']');
        return sb.toString();
    }
}
