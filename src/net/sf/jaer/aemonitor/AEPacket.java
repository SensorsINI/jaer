/*
 * AEPacket.java
 *
 * Created on October 29, 2005, 10:18 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.aemonitor;

import java.util.logging.Logger;
import net.sf.jaer.aemonitor.EventRaw.EventType;

/**
 * The superclass for raw event packets, as captured from an AEMonitorInterface.
 * These packets are not used for processed events. An AEPacket has only a
 * timestamp array.
 *
 * @author tobi
 * @see net.sf.jaer.aemonitor.AEPacketRaw
 */
public abstract class AEPacket {

    /**
     * Can optionally be used to limit maximum packet size. This limit must be
     * enforced at the user level.
     */
    public static final int MAX_PACKET_SIZE_EVENTS = 100000;

    static Logger log = Logger.getLogger("AEPacket");

    /**
     * when we try to store an event and the packet is not big enough, we
     * enlarge by this factor (truncated) to minimize memory allocation
     */
    static final float ENLARGE_CAPACITY_FACTOR = 2;

    protected int numEvents = 0;
    protected int capacity = 0;
    public int[] timestamps;

    public EventType[] eventtypes;    //Just for jAER 3.0
    public int[] pixelDataArray;               //Just for jAER 3.0 Frame Event

    protected EventRaw[] events;

    /**
     * Returns the number of events in the packet.
     *
     * @return number of events.
     */
    public int getNumEvents() {
        return this.numEvents;
    }

    /**
     * Sets the number of events in the packet. Just sets the field holding this
     * value.
     *
     * @param numEvents
     */
    public void setNumEvents(final int numEvents) {
        this.numEvents = numEvents;
    }

    /**
     * Returns the timestamp array.
     *
     * @return the array of timestamps. Only elements up to numEvents-1 are
     * valid.
     */
    public int[] getTimestamps() {
        return this.timestamps;
    }

    /**
     * Returns the event types array. Just for jAER 3.0 data
     *
     * @return the array of event types. Only elements up to numEvents-1 are
     * valid.
     */
    public EventType[] getEventtypes() {
        return this.eventtypes;
    }

    /**
     * Returns the data array. Just for jAER 3.0 Frame Event
     *
     * @return the array of event types. Only elements up to numEvents-1 are
     * valid.
     */
    public int[] getPixelDataArray() {
        return this.pixelDataArray;
    }

    /**
     * @param n the index (0 based) of the timestamp. Only values up to
     * numEvents-1 are valid.
     * @return the timestamp.
     */
    public int getTimestamp(int n) {
        return timestamps[n];
    }

    public int getFirstTimestamp() {
        return timestamps[0];
    }

    public int getLastTimestamp() {
        if ((timestamps == null) || (numEvents == 0)) {
            log.warning("tried to get last timestamp of null or empty packet");
            return 0;
        }

        return timestamps[numEvents - 1];
//        int n=(int)Math.min(numEvents,timestamps.length); // prevent exceptions
//        return timestamps[n-1];
    }

    /**
     * @return time interval for packet - from first to last event, in timestamp
     * ticks. Returns 0 if there are fewer than two events.
     */
    public int getDt() {
        if (numEvents < 2) {
            return 0;
        }
        return timestamps[numEvents - 1] - timestamps[0];
    }

    public void setTimestamps(final int[] timestamps) {
        this.timestamps = timestamps;
        if (timestamps == null) {
            numEvents = 0;
        } else {
            numEvents = timestamps.length;
        }
    }

    public void setEventtypes(final EventType[] etypes) {
        this.eventtypes = etypes;
        if (etypes == null) {
            numEvents = 0;
        } else {
            numEvents = etypes.length;
        }
    }

    public void setData(final int[] dataArray) {
        this.pixelDataArray = dataArray;
        if (dataArray == null) {
            numEvents = 0;
        } else {
            numEvents = dataArray.length;
        }
    }

    /**
     * Returns the maximum capacity for events.
     *
     * @return the maximum capacity for holding events in the packet. Not the
     * number of events present now.
     * @see #ensureCapacity(int)
     */
    public int getCapacity() {
        return this.capacity;
    }

    /**
     * Returns true if packet is empty
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return getNumEvents() == 0;
    }

    /**
     * Ensure the capacity given. If present capacity is less than capacity,
     * then arrays are newly allocated and old contents are copied to the new
     * arrays.
     *
     * @param c the desired capacity
     * @see #getCapacity()
     */
    public void ensureCapacity(final int c) {
//        System.out.println("ensure capacity "+c);
        if (timestamps == null) {
            timestamps = new int[c]; // if we have no timestamps, just allocate c
            eventtypes = new EventType[c]; // EventTypes and pixelDataArray just for AER3.0 Data file, no influence on 2.0
            pixelDataArray = new int[c];
            this.capacity = c;
        } else if (this.capacity < c) {
            int newcap = (int) ENLARGE_CAPACITY_FACTOR * c;
            int[] newtimestamps = new int[newcap];
            EventType[] newEventTypes = new EventType[newcap];
            int[] newPixelDataArray = new int[newcap];
            System.arraycopy(timestamps, 0, newtimestamps, 0, this.capacity);
            System.arraycopy(eventtypes, 0, newEventTypes, 0, this.capacity);
            System.arraycopy(pixelDataArray, 0, newPixelDataArray, 0, this.capacity);
            timestamps = newtimestamps;
            eventtypes = newEventTypes; // EventTypes and pixelDataArray just for AER3.0 Data file, no influence on 2.0
            pixelDataArray = newPixelDataArray;
            this.capacity = newcap; // only if we enlarge capacity to desired set the new capacity, otherwise leave it untouched!
        }

    }

    @Override
    public String toString() {
        return "AEPacket " + super.toString() + " of capacity " + capacity + " with " + numEvents + " events";
    }

    /**
     * Appends event to the packet, enlarging if necessary. Not thread safe.
     *
     * @param e an Event to add to the ones already present. Capacity is
     * enlarged if necessary.
     */
    public void addEvent(EventRaw e) {
//        System.out.println("add event "+e);
        int n = numEvents + 1;
        ensureCapacity(n);
        if (e == null) {
            throw new RuntimeException("null event " + e);
        }
//        if(timestamps==null) throw new RuntimeException("null timestamps");
//        try{
        timestamps[n - 1] = e.timestamp;
//        }catch(NullPointerException ex){
//            ex.printStackTrace();
//        }
        numEvents++; // we added one event
    }

    /**
     * Sets number of events to zero.
     */
    public void clear() {
        setNumEvents(0);

    }

}
