/*
 * BasicEvent.java
 *
 * Created on November 6, 2005, 10:31 AM
 */
package net.sf.jaer.event;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.util.Comparator;

/**
 * Base class for events. This class is extended by producers offering extended
 * event type information. Instances are used in EventPacket. This class is the
 * new-style event that replaces the original Event2D.
 *
 * @author tobi
 */
public class BasicEvent implements EventInterface<BasicEvent>, BasicEventInterface, Comparable {

    /**
     * When this bit is set in raw address it indicates some kind of special
     * event, e.g. sync, special data, etc.
     */
    public final static int SPECIAL_EVENT_BIT_MASK = 0x80000000;

    /**
     * timestamp of event, by convention in us
     */
    public int timestamp;

    /**
     * The raw address corresponding to the event which has originated in
     * hardware and is associated here for purposes of low level IO to streams.
     * <p>
     * This address is generally not transformed by event filtering, so filters
     * which transform events, e.g. by shifting them or rotating them, must
     * handle the transformation of the raw addresses. Event filters which
     * simply remove events need not worry about this effect.
     */
    public int address;

    /**
     * x address of event (horizontal coordinate, by convention starts at left
     * of image)
     */
    public short x;

    /**
     * y address of event (vertical coordinate, by convention starts at bottom
     * of image)
     */
    public short y;

    /**
     * Flags this event to be ignored in iteration (skipped over). Used to
     * filter out events in-place, without incurring the overhead of copying
     * other events to a mostly-duplicated output packet.
     */
    private boolean filteredOut = false;

    /**
     * Indicates that this event is a special (e.g. synchronizing) event, e.g.
     * originating from a separate hardware input pin or from a special source.
     */
    private boolean special = false;

    /**
     * Source byte. This is used to identify the source of the event when using
     * filters that integrate multiple event sources (eg, Retina, Cochlea ; Left
     * Retina, Right Retina, etc).
     */
    public byte source;

    /** Utility Comparator that compares BasicEvent by timestamp */
    final public class TimeStampComparator<E extends BasicEvent> implements Comparator<E> {

        @Override
        public int compare(final E e1, final E e2) {
            int diff = e1.timestamp - e2.timestamp;

            return diff;
        }
    }

    /**
     * Creates a new instance of BasicEvent
     */
    public BasicEvent() {
    }

    /**
     * Creates a new instance of BasicEvent.
     *
     * @param t the timestamp, by convention in us.
     */
    public BasicEvent(final int t) {
        timestamp = t;
    }

    /**
     * Creates a new instance of BasicEvent.
     *
     * @param timestamp the timestamp, by convention in us.
     * @param address the raw address
     */
    public BasicEvent(final int timestamp, final int address) {
        this.address = address;
        this.timestamp = timestamp;
    }

    /**
     * create an BasicEvent with a timestamp, x, y.
     *
     * @param timestamp the timestamp, by convention in us.
     * @param x x address of event
     * @param y y address of event
     */
    public BasicEvent(int timestamp, short x, short y) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
    }

    /**
     * Indicates that this event is a special event, e.g. originating from a
     * separate hardware input pin or from a special, i.e., exceptional, source.
     *
     * @return the special
     */
    @Override
    public boolean isSpecial() {
        return special;
    }

    /**
     * Indicates that this event is a special (e.g. synchronizing) event, e.g.
     * originating from a separate hardware input pin or from a special source.
     * This method also sets or clears the SPECIAL_EVENT_BIT_MASK in the
     * address.
     *
     * @param yes the special to set
     */
    @Override
    public void setSpecial(final boolean yes) {
        special = yes;

        if (yes) {
            address |= BasicEvent.SPECIAL_EVENT_BIT_MASK;
        } else {
            address &= (~BasicEvent.SPECIAL_EVENT_BIT_MASK);
        }
    }

    /**
     * copies fields from source event e to this event
     *
     * @param e the event to copy from
     */
    @Override
    public void copyFrom(final BasicEvent e) {
        timestamp = e.timestamp;
        x = e.x;
        y = e.y;
        address = e.address;
        special = e.special;
        source = e.source;
        filteredOut = e.filteredOut;
    }

    @Override
    public String toString() {
        return String.format("%s: timestamp=%,d address=0x%X x=%d y=%d special=%s filteredOut=%s",getClass().getSimpleName(),timestamp,address,x,y,special,filteredOut);
//        return getClass().getSimpleName() + " timestamp=" + timestamp + " address=" + address + " x=" + x + " y=" + y + " special="
//                + special + " filteredOut=" + filteredOut;
    }

    public void reset() {
        timestamp = 0;
        x = 0;
        y = 0;
        address = 0;
        special = false;
        source = 0;
        filteredOut = false;
    }

    @Override
    public int getNumCellTypes() {
        return 1;
    }

    @Override
    public int getType() {
        return 1;
    }

    @Override
    final public int getTimestamp() {
        return timestamp;
    }

    @Override
    final public void setTimestamp(final int timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * @return the address
     */
    @Override
    public int getAddress() {
        return address;
    }

    /**
     * @param address the address to set
     */
    @Override
    public void setAddress(final int address) {
        this.address = address;
    }

    @Override
    final public short getX() {
        return x;
    }

    @Override
    final public void setX(final short x) {
        this.x = x;
    }

    @Override
    final public short getY() {
        return y;
    }

    @Override
    final public void setY(final short y) {
        this.y = y;
    }

    /**
     * Draws this event by some standardized method that will depend on
     * rendering method.
     *
     * @param drawable the OpenGL drawable.
     */
    public void draw(final GLAutoDrawable drawable) {
        final GL2 gl = drawable.getGL().getGL2(); // when we get this we are already set up with scale 1=1 pixel, at LL
        // corner

        gl.glColor3f(1, 1, 1);
        gl.glRectf(x, y, x + 1, y + 1);
    }

    /**
     * Is this event to be ignored in iteration (skipped over)? Used to filter
     * out events in-place, without incurring the overhead of copying other
     * events to a mostly-duplicated output packet.
     *
     * @return the filteredOut
     */
    @Override
    public boolean isFilteredOut() {
        return filteredOut;
    }

    /**
     * Flags this event to be ignored in iteration (skipped over). Used to
     * filter out events in-place, without incurring the overhead of copying
     * other events to a mostly-duplicated output packet.
     *
     * @param filteredOut the filteredOut to set
     */
    @Override
    public void setFilteredOut(final boolean filteredOut) {
        this.filteredOut = filteredOut;
    }

    /**
     * Overrides default to use hashCode which considers timestamp, address,
     * special, but NOT filteredOut.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
//        if (obj.getClass() != this.getClass()) {
//            return false;
//        }
        if(!(obj instanceof BasicEvent)){
            return false;
        }
        BasicEvent tgt = (BasicEvent) obj;

        return (this.x == tgt.x) && (this.y == tgt.y) && (this.timestamp == tgt.timestamp) /*&& (this.filteredOut==tgt.filteredOut)*/ && (this.special == tgt.special); // DO NOT include filtered out or you cannot match events in different lists, e.g. in NoiseTesterFilter
//        return this.hashCode() == tgt.hashCode(); // do NOT use hashCode since it is idential for different events

    }

    /**
     * Overrides hashCode to result in identical hashCode (even if for different
     * objects) if the timestamp, x,y, and special fields are the same.
     * <p>
     * NOTE: filteredOut is NOT included in hashCode. Events can be equal even
     * if one is filtered out.
     *
     * @return the hashcode
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + this.timestamp;
        hash = 89 * hash + this.x;
        hash = 89 * hash + this.y;
//        hash = 89 * hash + (this.filteredOut ? 1 : 0); // DO NOT include filteredOut so that even if events has been marked filtered out it is still equal
        hash = 89 * hash + (this.special ? 1 : 0);
        return hash;
    }

    /**
     * Overrides built-in comparator to compare timestamps, unless the events
     * are equal() then return 0 always
     *
     * @param t another BasicEvent
     * @return timestamp difference, this event minus previous one. Result is
     * positive if this event is later than previous one.
     */
    @Override
    public int compareTo(Object t) {
        if (t == null) {
            return 1;
        }
        if (!(t instanceof BasicEvent)) {
            return 1;
        }
        if (this.hashCode() == t.hashCode()) {
            return 0;
        }
        return this.timestamp - ((BasicEvent) t).timestamp; // if not, compare timestamps
    }

}
