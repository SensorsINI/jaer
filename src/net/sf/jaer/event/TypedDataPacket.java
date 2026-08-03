/*
 * TypedDataPacket.java
 *
 * jAER 3.0: common contract for homogeneous packets in a PacketBundle.
 */
package net.sf.jaer.event;

/**
 * A packet that carries a single uniform {@link PacketType} (AEDAT-4 style).
 * Implemented by {@link EventPacket} (polarity / special / …),
 * {@link FramePacket}, and {@link ImuPacket}.
 *
 * @author tobi
 */
public interface TypedDataPacket {

    /**
     * @return the uniform data kind of every element in this packet
     */
    PacketType getPacketType();

    /**
     * @return number of elements (events, IMU samples, or 1 for a frame)
     */
    int getSize();

    /**
     * @return true if this packet has no elements
     */
    default boolean isEmpty() {
        return getSize() == 0;
    }

    /**
     * Clears contents for reuse. Capacity may be retained.
     */
    void clear();

    /**
     * First timestamp in this packet, in microseconds (AER tick or Unix µs
     * depending on source). Named {@code *Us} to avoid clashing with legacy
     * {@link EventPacket#getFirstTimestamp()} ({@code int}). Returns 0 if empty.
     */
    long getFirstTimestampUs();

    /**
     * Last timestamp in this packet, in microseconds. Returns 0 if empty.
     */
    long getLastTimestampUs();
}
