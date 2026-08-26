/*
 * PacketBundle.java
 *
 * jAER 3.0: time-ordered list of homogeneous typed packets from one slice.
 */
package net.sf.jaer.event;

import java.util.ArrayList;
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
 *
 * @author tobi
 */
public class PacketBundle implements Iterable<TypedDataPacket> {

    private final ArrayList<TypedDataPacket> packets = new ArrayList<>(8);

    /** Optional raw AE slice that produced this bundle (legacy / debug). */
    private net.sf.jaer.aemonitor.AEPacketRaw rawPacket;

    /** Source accounting for an authoritative typed acquisition bundle. */
    private AcquisitionMetadata acquisitionMetadata;

    private boolean sealed;

    public PacketBundle() {
    }

    public void clear() {
        packets.clear();
        rawPacket = null;
        acquisitionMetadata = null;
        sealed = false;
    }

    public void add(TypedDataPacket packet) {
        ensureMutable();
        if (packet != null && !packet.isEmpty()) {
            packets.add(packet);
        }
    }

    /**
     * Adds a packet even if empty (rarely needed for placeholders).
     */
    public void addAllowEmpty(TypedDataPacket packet) {
        ensureMutable();
        if (packet != null) {
            packets.add(packet);
        }
    }

    public int getNumPackets() {
        return packets.size();
    }

    public boolean isEmpty() {
        return packets.isEmpty();
    }

    public TypedDataPacket get(int i) {
        return packets.get(i);
    }

    public List<TypedDataPacket> getPackets() {
        return Collections.unmodifiableList(packets);
    }

    /**
     * First polarity (DVS) packet in the bundle, or null.
     */
    public EventPacket<?> getFirstPolarityPacket() {
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
    public FramePacket getFirstFramePacket() {
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
    public ImuPacket getFirstImuPacket() {
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
    public int getNumPolarityEvents() {
        int n = 0;
        for (TypedDataPacket p : packets) {
            if (p.getPacketType() == PacketType.POLARITY) {
                n += p.getSize();
            }
        }
        return n;
    }

    public long getFirstTimestampUs() {
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

    public long getLastTimestampUs() {
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

    public net.sf.jaer.aemonitor.AEPacketRaw getRawPacket() {
        return rawPacket;
    }

    public void setRawPacket(net.sf.jaer.aemonitor.AEPacketRaw rawPacket) {
        ensureMutable();
        if (rawPacket != null && acquisitionMetadata != null) {
            throw new IllegalStateException(
                    "authoritative acquisition metadata and a raw sidecar are mutually exclusive");
        }
        this.rawPacket = rawPacket;
    }

    /**
     * Begins source accounting for an authoritative typed acquisition bundle.
     *
     * @return the new mutable metadata builder
     */
    public AcquisitionMetadata beginAcquisition(final long acquisitionSessionId,
            final long sequenceId) {
        ensureMutable();
        if (rawPacket != null) {
            throw new IllegalStateException(
                    "a legacy raw bridge cannot begin authoritative acquisition metadata");
        }
        if (acquisitionMetadata != null) {
            throw new IllegalStateException("authoritative acquisition metadata already begun");
        }
        acquisitionMetadata = new AcquisitionMetadata(acquisitionSessionId, sequenceId);
        return acquisitionMetadata;
    }

    public AcquisitionMetadata getAcquisitionMetadata() {
        return acquisitionMetadata;
    }

    public boolean isLegacyRawBridge() {
        return rawPacket != null && acquisitionMetadata == null;
    }

    public boolean isSealed() {
        return sealed;
    }

    /**
     * Validates and freezes the current bundle for publication. Initial
     * authoritative sealing derives source accepted counts from the payload.
     */
    public void seal() {
        if (sealed) {
            return;
        }
        if (acquisitionMetadata != null) {
            if (rawPacket != null) {
                throw new IllegalStateException(
                        "authoritative acquisition metadata and a raw sidecar are mutually exclusive");
            }
            acquisitionMetadata.sealFromPackets(packets);
        }
        sealed = true;
    }

    /**
     * Copies source acquisition context for filtering. Authoritative metadata
     * is deep-copied with its original counts, epochs, and losses; a raw
     * sidecar is copied only for a legacy bridge.
     */
    public void copyAcquisitionContextFrom(final PacketBundle source) {
        ensureMutable();
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (source == this) {
            return;
        }
        if (source.acquisitionMetadata != null) {
            if (rawPacket != null) {
                throw new IllegalStateException(
                        "a legacy raw bridge cannot copy authoritative acquisition metadata");
            }
            acquisitionMetadata = AcquisitionMetadata.copySourceContext(source.acquisitionMetadata);
            rawPacket = null;
        } else if (source.isLegacyRawBridge()) {
            if (acquisitionMetadata != null) {
                throw new IllegalStateException(
                        "authoritative acquisition metadata cannot copy a raw sidecar");
            }
            rawPacket = source.rawPacket;
            acquisitionMetadata = null;
        } else {
            rawPacket = null;
            acquisitionMetadata = null;
        }
    }

    private void ensureMutable() {
        if (sealed) {
            throw new IllegalStateException("packet bundle is sealed");
        }
    }

    @Override
    public Iterator<TypedDataPacket> iterator() {
        return sealed ? Collections.unmodifiableList(packets).iterator() : packets.iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PacketBundle[");
        sb.append(packets.size()).append(" packets:");
        for (TypedDataPacket p : packets) {
            sb.append(' ').append(p.getPacketType()).append('(').append(p.getSize()).append(')');
        }
        sb.append(']');
        return sb.toString();
    }
}
