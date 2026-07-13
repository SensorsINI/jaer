/*
 * ImuPacket.java
 *
 * jAER 3.0: homogeneous packet of IMU samples (not 7 AE words per sample).
 */
package net.sf.jaer.event;

import java.util.Arrays;

import eu.seebetter.ini.chips.davis.imu.IMUSample;

/**
 * A typed packet of inertial samples. Replaces embedding
 * {@link IMUSample} inside mixed {@code ApsDvsEvent} shells.
 * <p>
 * Samples are stored as reused {@link IMUSample} objects (object pool),
 * similar to {@link EventPacket}.
 *
 * @author tobi
 */
public class ImuPacket implements TypedDataPacket {

    public static final int DEFAULT_CAPACITY = 64;

    private IMUSample[] elementData;
    private int size;
    private int capacity;
    private PacketType packetType = PacketType.IMU6;
    private int streamId;
    private byte source;

    public ImuPacket() {
        this(DEFAULT_CAPACITY);
    }

    public ImuPacket(int initialCapacity) {
        capacity = Math.max(1, initialCapacity);
        elementData = new IMUSample[capacity];
        for (int i = 0; i < capacity; i++) {
            elementData[i] = newIMUSample();
        }
        size = 0;
    }

    /**
     * IMUSample's protected ctor — use reflection-free factory via subclass
     * accessor. IMUSample has protected no-arg ctor in same module usage;
     * we allocate via a small holder that subclasses it.
     */
    private static IMUSample newIMUSample() {
        return new AllocatableIMUSample();
    }

    /** Public no-arg construction for pool slots. */
    private static final class AllocatableIMUSample extends IMUSample {
        AllocatableIMUSample() {
            super();
        }
    }

    @Override
    public PacketType getPacketType() {
        return packetType;
    }

    public void setPacketType(PacketType packetType) {
        if (packetType != null && !packetType.isImu()) {
            throw new IllegalArgumentException("ImuPacket type must be IMU6 or IMU9, got " + packetType);
        }
        this.packetType = packetType == null ? PacketType.IMU6 : packetType;
    }

    @Override
    public int getSize() {
        return size;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public void clear() {
        size = 0;
    }

    @Override
    public long getFirstTimestampUs() {
        return size == 0 ? 0 : elementData[0].getTimestampUs();
    }

    @Override
    public long getLastTimestampUs() {
        return size == 0 ? 0 : elementData[size - 1].getTimestampUs();
    }

    public IMUSample get(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("index " + i + " size " + size);
        }
        return elementData[i];
    }

    /**
     * Obtains the next empty sample slot, growing capacity if needed, and
     * increments size. Caller fills fields via {@link IMUSample#copyFrom}.
     */
    public IMUSample nextOutput() {
        if (size >= capacity) {
            grow();
        }
        return elementData[size++];
    }

    /**
     * Appends a copy of {@code sample}.
     */
    public void appendCopy(IMUSample sample) {
        IMUSample dst = nextOutput();
        dst.copyFrom(sample);
    }

    private void grow() {
        int newCap = capacity * 2;
        IMUSample[] neu = Arrays.copyOf(elementData, newCap);
        for (int i = capacity; i < newCap; i++) {
            neu[i] = newIMUSample();
        }
        elementData = neu;
        capacity = newCap;
    }

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    public byte getSource() {
        return source;
    }

    public void setSource(byte source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return String.format("ImuPacket type=%s size=%d ts=[%d,%d]",
                packetType, size, getFirstTimestampUs(), getLastTimestampUs());
    }
}
