/*
 * BasicEvent.java
 *
 * Created on November 6, 2005, 10:31 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.event;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

/**
 * Base class for events. This class is extended by producers offering extended
 * event type information. Instances are used in EventPacket. This class is the
 * new-style event that replaces the original Event2D.
 *
 * @author tobi
 */
public class BasicEvent implements EventInterface<BasicEvent> {

	//    public int serial=-1;
	/**
	 * timestamp of event, by convention in us
	 */
	public int timestamp;
	/**
	 * The raw address corresponding to the event which has originated in
	 * hardware and is associated here for purposes of low level IO to streams.
	 * <p> This address is generally not transformed by event filtering, so
	 * filters which transform events, e.g. by shifting them or rotating them,
	 * must handle the transformation of the raw addresses. Event filters which
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
	 * Indicates that this event is a special (e.g. synchronizing) event, e.g.
	 * originating from a separate hardware input pin or from a special source.
	 */
	public boolean special = false;
	/**
	 * When this bit is set in raw address it indicates some kind of special
	 * event, e.g. sync, special data, etc.
	 */
	public final static int SPECIAL_EVENT_BIT_MASK = 0x80000000;

	/** Source byte.  This is used to identify the source of the event when using
	 * filters that integrate multiple event sources (eg, Retina, Cochlea ;
	 * Left Retina, Right Retina, etc).
	 */
	public byte source;

	/**
	 * Indicates that this event is a special synchronizing event, e.g.
	 * originating from a separate hardware input pin or from the a special
	 * source.
	 *
	 * @return the special
	 */
	public boolean isSpecial() {
		return special;
	}

	/**
	 * Indicates that this event is a special (e.g. synchronizing) event, e.g.
	 * originating from a separate hardware input pin or from a special source.
	 * This method also sets or clears the SPECIAL_EVENT_BIT_MASK in the address.
	 *
	 * @param special the special to set
	 */
	public void setSpecial(boolean yes) {
		special = yes;
		if (yes) {
			address |= SPECIAL_EVENT_BIT_MASK;
		} else {
			address &= (~SPECIAL_EVENT_BIT_MASK);
		}
	}

	// TODO implement filteredAway in a consistent way across jAER so that the numbers of events and iteration are properly handled (big job)
	//    /** Marks whether event is filtered away; false is default value and filters can set true to mark
	//     the event as unused for further processing.
	//     */
	//    public boolean filteredAway=false;
	/**
	 * Creates a new instance of BasicEvent
	 */
	public BasicEvent() {
	}

	/**
	 * create an BasicEvent with a timestamp, x, y, and a variable length number
	 * of bytes types. TODO, currently the type and types fields are ignored.
	 */
	public BasicEvent(int timestamp, short x, short y, byte type, byte... types) {
		this.timestamp = timestamp;
		this.x = x;
		this.y = y;
	}

	/**
	 * copies fields from source event src to this event
	 *
	 * @param e the event to copy from
	 */
	@Override
	public void copyFrom(BasicEvent e) {
		timestamp = e.timestamp;
		x = e.x;
		y = e.y;
		address = e.address;
		special = e.special;
		source = e.source;
		//        this.filteredAway=e.filteredAway;
	}

	/**
	 * Creates a new instance of BasicEvent.
	 *
	 * @param t the timestamp, by convention in us.
	 */
	public BasicEvent(int t) {
		timestamp = t;
	}

	/**
	 * Creates a new instance of BasicEvent.
	 *
	 * @param timestamp the timestamp, by convention in us.
	 * @param address the raw address
	 */
	public BasicEvent(int timestamp, int address) {
		this.address = address;
		this.timestamp = timestamp;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timestamp=" + timestamp + " address=" + address + " x=" + x + " y=" + y + " special=" + special;
	}

	@Override
	public int getNumCellTypes() {
		return 1;
	}

	@Override
	public int getType() {
		return 1;
	}

	final public int getTimestamp() {
		return timestamp;
	}

	/**
	 * @return the address
	 */
	public int getAddress() {
		return address;
	}

	/**
	 * @param address the address to set
	 */
	public void setAddress(int address) {
		this.address = address;
	}

	final public void setTimestamp(int timestamp) {
		this.timestamp = timestamp;
	}

	final public short getX() {
		return x;
	}

	final public void setX(short x) {
		this.x = x;
	}

	final public short getY() {
		return y;
	}

	final public void setY(short y) {
		this.y = y;
	}

	/**
	 * Draws this event by some standardized method that will depend on
	 * rendering method.
	 *
	 * @param drawable the OpenGL drawable.
	 */
	public void draw(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2(); // when we get this we are already set up with scale 1=1 pixel, at LL corner

		gl.glColor3f(1, 1, 1);
		gl.glRectf(x, y, x + 1, y + 1);
	}
	// TODO implement this filteredAway in such a way that the count of events is properly maintained in a packet.
	//    /** True if an EventFilter has marked this event to be ignored */
	//    public boolean isFilteredAway() {
	//        return filteredAway;
	//    }
	//
	//    public void setFilteredAway(boolean filteredAway) {
	//        this.filteredAway=filteredAway;
	//    }
}
