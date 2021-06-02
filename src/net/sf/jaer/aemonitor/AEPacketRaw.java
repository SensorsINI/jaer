/*
 * AEPacket.java
 *
 * Created on October 28, 2005, 5:57 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.aemonitor;

import java.util.Collection;
import net.sf.jaer.aemonitor.EventRaw.EventType;

/**
 * A structure containing a packer of AEs: addresses, timestamps. The AE packet
 * efficiently packages a set of events: rather than using an object per event,
 * it packs a lot of events into an object that references arrays of primitives.
 * These arrays can be newly allocated or statically allocated to the capacity
 * of the maximum buffer that is transferred from a device. Callers must use
 * {@link #getNumEvents} to find out the capacity of the packet in the case that
 * the arrays contain less events than their capacity, which is usually the case
 * when the packet is reused in a device acquisition.
 * <p>
 * These AEPacketRaw are used only for device events (raw events). For processed
 * events, see the net.sf.jaer.event package.
 *
 * @author tobi
 */
public class AEPacketRaw extends AEPacket {

    /**
     * The index of the start of the last packet captured from a device, used
     * for processing data on acquisition. The hardware interface class is
     * responsible for setting this value. After a capture of data,
     * lastCaptureLength points to the start of this capture. A real time
     * processor need not process the entire buffer but only starting from this
     * lastCaptureIndex.
     */
    public int lastCaptureIndex = 0;
    /**
     * The number of events last captured. The hardware interface class is
     * responsible for setting this value.
     */
    public int lastCaptureLength = 0;
    /**
     * The raw AER addresses
     */
    public int[] addresses;
    /**
     * Signals that an overrun occurred on this packet
     */
    public boolean overrunOccuredFlag = false;
    /**
     * An event, for internal use.
     */
    private EventRaw event = new EventRaw();
    /**
     * The last modification time from System.nanoTime(). Not all hardware
     * interfaces set this value.
     */
    public long systemModificationTimeNs;

    /**
     * Creates a new instance of AEPacketRaw with 0 capacity.
     */
    public AEPacketRaw() {
    }

    /**
     * Creates a new instance of AEPacketRaw from addresses and timestamps
     *
     * @param addresses
     * @param timestamps
     */
    public AEPacketRaw(int[] addresses, int[] timestamps) {
        if ((addresses == null) || (timestamps == null)) {
            return;
        }
        setAddresses(addresses);
        setTimestamps(timestamps);
        if (addresses.length != timestamps.length) {
            throw new RuntimeException("addresses.length=" + addresses.length + "!=timestamps.length=" + timestamps.length);
        }
        capacity = addresses.length;
        numEvents = addresses.length;
    }

    /**
     * Creates a new instance of AEPacketRaw from addresses and timestamps and
     * EventTypes
     *
     * @param addresses
     * @param timestamps
     * @param etypes
     */
    public AEPacketRaw(int[] addresses, int[] timestamps, EventType[] etypes) {
        if ((addresses == null) || (timestamps == null)) {
            return;
        }
        setAddresses(addresses);
        setTimestamps(timestamps);
        setEventtypes(etypes);
        if (addresses.length != timestamps.length) {
            throw new RuntimeException("addresses.length=" + addresses.length + "!=timestamps.length=" + timestamps.length);
        }
        capacity = addresses.length;
        numEvents = addresses.length;
    }

    /**
     * Creates a new instance of AEPacketRaw with an initial capacity and empty
     * event arrays.
     *
     * @param size capacity in events
     */
    public AEPacketRaw(int size) {
        if (size > MAX_PACKET_SIZE_EVENTS) {
            log.warning("allocating arrays of size " + size + " which is larger than MAX_PACKET_SIZE_EVENTS=" + MAX_PACKET_SIZE_EVENTS + " in size");
        } else {
            log.info("allocating size=" + size + " arrays of events");
        }
        allocateArrays(size);
    }

    /**
     * Constructs a new AEPacketRaw by concatenating two packets. The contents
     * of the source packets are copied to this packet's memory arrays.
     *
     * The timestamps will be probably not be ordered monotonically after this
     * concatenation! And unless the sources are identified by unique addresses,
     * the sources of the events will be lost.
     *
     * @param one
     * @param two
     */
    public AEPacketRaw(AEPacketRaw one, AEPacketRaw two) {
        this(one.getNumEvents() + two.getNumEvents());
        System.arraycopy(one.getAddresses(), 0, getAddresses(), 0, one.getNumEvents());
        System.arraycopy(two.getAddresses(), 0, getAddresses(), one.getNumEvents(), two.getNumEvents());
        System.arraycopy(one.getTimestamps(), 0, getTimestamps(), 0, one.getNumEvents());
        System.arraycopy(two.getTimestamps(), 0, getTimestamps(), one.getNumEvents(), two.getNumEvents());
        numEvents = addresses.length;
        capacity = numEvents;
    }

    /**
     * Constructs a new AEPacketRaw by concatenating a list of event packets.
     * The contents of the source packets are copied to this packet's memory
     * arrays according to the iterator of the Collection.
     *
     * The timestamps will be probably not be ordered monotonically after this
     * concatenation! And unless the sources are identified by unique addresses,
     * the sources of the events will be lost.
     *
     * @param collection to copy from.
     */
    public AEPacketRaw(Collection<AEPacketRaw> collection) {
        int n = 0;
        for (AEPacketRaw packet : collection) {
            n += packet.getNumEvents();
        }
        allocateArrays(n);
        //setNumEvents(n);
        int counter = 0;
        for (AEPacketRaw packet : collection) {
            try {
                int ne = packet.getNumEvents();
                System.arraycopy(packet.getAddresses(), 0, getAddresses(), counter, ne);
                System.arraycopy(packet.getTimestamps(), 0, getTimestamps(), counter, ne);
                counter += ne;
            } catch (ArrayIndexOutOfBoundsException e) {
                log.warning("caught " + e.toString() + "when constructing new RawPacket from Collection.");
                continue;
            }
        }
        setNumEvents(counter);
    }

    private void allocateArrays(int size) {
        addresses = new int[size]; //new E[size];
        timestamps = new int[size];
        eventtypes = new EventType[size];
        pixelDataArray = new int[size];
        this.capacity = size;
        numEvents = 0;
    }

    public int[] getAddresses() {
        return this.addresses;
    }

    public void setAddresses(final int[] addresses) {
        this.addresses = addresses;
        if (addresses == null) {
            numEvents = 0;
        } else {
            numEvents = addresses.length;
        }
    }

    /**
     * Uses local EventRaw to return packaged event. (Does not create a new
     * object instance.)
     */
    final public EventRaw getEvent(int k) {
        event.timestamp = timestamps[k];
        event.address = addresses[k];
        event.eventtype = eventtypes[k];
        event.pixelData = pixelDataArray[k];
        return event;
    }

    /**
     * Ensure the capacity given. Overrides AEPacket's ensureCapacity to
     * increase the size of the addresses array. If present capacity is less
     * than capacity, then arrays are newly allocated and old contents are
     * copied.
     *
     * @param c the desired capacity
     */
    @Override
    final public void ensureCapacity(final int c) {
        super.ensureCapacity(c);
        if (addresses == null) {
            addresses = new int[c];
            this.capacity = c;
        } else if (addresses.length < c) {
            int newcap = (int) ENLARGE_CAPACITY_FACTOR * c;
            int[] newaddresses = new int[newcap]; // TODO can use all of heap and OutOfMemoryError here if we keep adding events
            System.arraycopy(addresses, 0, newaddresses, 0, addresses.length);
            addresses = newaddresses;
            this.capacity = newcap;
        }
    }

    /**
     * Appends event, enlarging packet if necessary. Not thread safe.
     *
     * @param e an Event to add to the ones already present. Capacity is
     * enlarged if necessary.
     */
    @Override
    final public void addEvent(EventRaw e) {
        if (e == null) {
            log.warning("tried to add null event, not adding it");
        }
        super.addEvent(e); // will increment numEvents
//        int n=getCapacity();    // make sure our address array is big enough
        this.ensureCapacity(capacity); // enlarge the address array if necessary
        addresses[numEvents - 1] = e.address; // store the address at the end of the array
        // numEvents++; // we already incremented the number of events in the super call
    }

    /**
     * Allocates a new AEPacketRaw and copies the events from this packet into
     * the new one, returning it. The size of the new packet that is returned is
     * exactly the number of events stored in the this packet. This method can
     * be used to more efficiently use matlab memory, which handles java garbage
     * collection poorly.
     *
     * @return a new packet sized to the src packet number of events
     */
    public AEPacketRaw getPrunedCopy() {
        int n = getNumEvents();
        AEPacketRaw dest = new AEPacketRaw(n);
        int[] srcTs = getTimestamps();
        int[] srcAddr = getAddresses();
        int[] destTs = dest.getTimestamps();
        int[] destAddr = dest.getAddresses();
        System.arraycopy(srcTs, 0, destTs, 0, n);
        System.arraycopy(srcAddr, 0, destAddr, 0, n);
        dest.setNumEvents(n);
        return dest;
    }

    @Override
    public String toString() {
        if (getNumEvents() == 0) {
            return super.toString();
        } else {
            return super.toString() + (numEvents > 0 ? 
                    String.format(" tstart=%d tend=%d dt=%d", timestamps[0], timestamps[numEvents - 1], (timestamps[numEvents - 1]- timestamps[0])) 
                    :
                    " empty");
        }
    }

    /**
     * Appends another AEPacketRaw to this one
     *
     * @param source
     * @return the appended packet
     */
    public AEPacketRaw append(AEPacketRaw source) {
        if (source == null || source.getNumEvents() == 0) {
            return this;
        }
        ensureCapacity(getNumEvents() + source.getNumEvents());
        System.arraycopy(source.getAddresses(), 0, addresses, numEvents, source.getNumEvents());
        System.arraycopy(source.getTimestamps(), 0, timestamps, numEvents, source.getNumEvents());
        setNumEvents(getNumEvents() + source.getNumEvents());
        return this;
    }

    /**
     * Static method to copy from one AEPacketRaw to another
     *
     * @param src source packet
     * @param srcPos the starting index in src
     * @param dest destination packet
     * @param destPos the starting index in destination
     * @param length the number of events to copy
     */
    public static void copy(AEPacketRaw src, int srcPos, AEPacketRaw dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("null src or dest");
        }
        dest.ensureCapacity(dest.getNumEvents() + length);
        System.arraycopy(src.getAddresses(), srcPos, dest.getAddresses(), destPos, length);
        System.arraycopy(src.getTimestamps(), srcPos, dest.getTimestamps(), destPos, length);
        dest.setNumEvents(dest.getNumEvents() + length);
    }
}
