/* BasicEvent.java
 *
 * Created on November 6, 2005, 10:31 AM */
package net.sf.jaer.event;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

/** Base class for events. 
 * This class is extended by producers offering extended event type information. 
 * Instances are used in EventPacket. This class is the new-style event 
 * that replaces the original Event2D.
 *
 * @author tobi */
public class BasicEvent implements EventInterface<BasicEvent>, BasicEventInterface {

    /** When this bit is set in raw address it indicates some kind of special
     * event, e.g. sync, special data, etc. */
    public final static int SPECIAL_EVENT_BIT_MASK = 0x80000000;
    
    /** timestamp of event, by convention in us */
    public int timestamp;
    
    /** The raw address corresponding to the event which has originated in
     * hardware and is associated here for purposes of low level IO to streams.
     * <p> 
     * This address is generally not transformed by event filtering, so
     * filters which transform events, e.g. by shifting them or rotating them,
     * must handle the transformation of the raw addresses. Event filters which
     * simply remove events need not worry about this effect. */
    public int address;
    
    /** x address of event (horizontal coordinate, by convention starts at left
     * of image) */
    public short x;
    
    /** y address of event (vertical coordinate, by convention starts at bottom
     * of image) */
    public short y;
 
    /** Flags this event to be ignored in iteration (skipped over). 
     * Used to filter out events in-place, without incurring the overhead of 
     * copying other events to a mostly-duplicated output packet. */
    private boolean filteredOut = false;
    
    /** Indicates that this event is a special (e.g. synchronizing) event, e.g.
     * originating from a separate hardware input pin or from a special source. */
    public boolean special = false;

    /** Source byte.  This is used to identify the source of the event when using
     * filters that integrate multiple event sources (eg, Retina, Cochlea ; 
     * Left Retina, Right Retina, etc). */
    public byte source;

    /** Creates a new instance of BasicEvent */
    public BasicEvent() {}

    /** Creates a new instance of BasicEvent.
     * @param t the timestamp, by convention in us. */
    public BasicEvent(int t) {
        timestamp = t;
    }

    /** Creates a new instance of BasicEvent.
     * @param timestamp the timestamp, by convention in us.
     * @param address the raw address */
    public BasicEvent(int timestamp, int address) {
        this.address   = address;
        this.timestamp = timestamp;
    }
    
    /** create an BasicEvent with a timestamp, x, y, and a variable 
     * length number of bytes types. 
     * TODO: currently the type and types fields are ignored.
     * @param timestamp the timestamp, by convention in us.
     * @param x x address of event
     * @param y y address of event
     * @param type CURRENTLY IRGNORED
     * @param types CURRENTLY IRGNORED */
    public BasicEvent(int timestamp, short x, short y, byte type, byte... types) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
    }

    // TODO implement filteredAway in a consistent way across jAER so that the numbers of events and iteration are properly handled (big job)
    //    /** Marks whether event is filtered away; false is default value and filters can set true to mark
    //     * the event as unused for further processing. */
    //    public boolean filteredAway=false;
    
    // TODO implement this filteredAway in such a way that the count of events is properly maintained in a packet.
    //    /** True if an EventFilter has marked this event to be ignored */
    //    public boolean isFilteredAway() {
    //        return filteredAway;
    //    }
    //
    //    public void setFilteredAway(boolean filteredAway) {
    //        this.filteredAway=filteredAway;
    //    }
    
    
    /** Indicates that this event is a special event, e.g.
     * originating from a separate hardware input pin or from a special, 
     * i.e., exceptional, source.
     * @return the special */
    @Override public boolean isSpecial() {
        return special;
    }

    /** Indicates that this event is a special (e.g. synchronizing) event, e.g.
     * originating from a separate hardware input pin or from a special source.
     * This method also sets or clears the SPECIAL_EVENT_BIT_MASK in the address.
     * @param yes the special to set */
    @Override public void setSpecial(boolean yes) {
        this.special = yes;
        if (yes) {
            this.address |= SPECIAL_EVENT_BIT_MASK;
        } else {
            this.address &= (~SPECIAL_EVENT_BIT_MASK);
        }
    }
    
    /** copies fields from source event e to this event
     * @param e the event to copy from */
    @Override public void copyFrom(BasicEvent e) {
        this.timestamp = e.timestamp;
        this.x = e.x;
        this.y = e.y;
        this.address = e.address;
        this.special = e.special;
        this.source = e.source;
        this.setFilteredOut(e.isFilteredOut());
        //        this.filteredAway=e.filteredAway;
    }
    
    @Override public String toString() {
        return getClass().getSimpleName() 
                + " timestamp=" + timestamp 
                + " address=" + address 
                + " x=" + x 
                + " y=" + y 
                + " special=" + special 
                + " filteredOut=" + filteredOut;
    }

    @Override public int getNumCellTypes() {
        return 1;
    }

    @Override public int getType() {
        return 1;
    }

    @Override final public int getTimestamp() {
        return timestamp;
    }
    
    @Override final public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * @return the address */
    @Override public int getAddress() {
        return address;
    }

    /**
     * @param address the address to set  */
    @Override public void setAddress(int address) {
        this.address = address;
    }

    @Override final public short getX() {
        return x;
    }

    @Override final public void setX(short x) {
        this.x = x;
    }

    @Override final public short getY() {
        return y;
    }

    @Override final public void setY(short y) {
        this.y = y;
    }

    /** Draws this event by some standardized method that will depend on
     * rendering method.
     *
     * @param drawable the OpenGL drawable. */
    public void draw(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2(); // when we get this we are already set up with scale 1=1 pixel, at LL corner

        gl.glColor3f(1, 1, 1);
        gl.glRectf(x, y, x + 1, y + 1);
    }


    /** Is this event to be ignored in iteration (skipped over)? 
     * Used to filter out events in-place, without incurring the 
     * overhead of copying other events to a mostly-duplicated output packet.
     * @return the filteredOut */
    @Override public boolean isFilteredOut() {
        return filteredOut;
    }

    /** Flags this event to be ignored in iteration (skipped over). 
     * Used to filter out events in-place, without incurring the overhead 
     * of copying other events to a mostly-duplicated output packet.
     * @param filteredOut the filteredOut to set */
    @Override  public void setFilteredOut(boolean filteredOut) {
        this.filteredOut = filteredOut;
    }
}
