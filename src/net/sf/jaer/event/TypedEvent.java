package net.sf.jaer.event;
/*
 * TypedEvent.java
 *
 * Created on May 28, 2006, 9:20 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 * Copyright May 28, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

/**
 * Represents an event with a byte type. This is a legacy class to support previous implementations.
 *
 * @author tobi
 */
public class TypedEvent extends BasicEvent implements TypedEventInterface {

	/** The type field of the event. Generally a small number representing the cell type. */
	public byte type = 0;

	/** Creates a new instance of TypedEvent */
	public TypedEvent() {
	}

	/**
	 * The type field of the event. Generally a small number representing the cell type.
	 *
	 * @return the type
	 */
	@Override
	public int getType() {
		return type;
	}

	public void setType(final byte t) {
		type = t;
	}

	@Override
	public String toString() {
		return super.toString() + " type=" + type;
	}

	@Override
	public void reset() {
		super.reset();

		type = 0;
	}

	/**
	 * copies fields from source event src to this event
	 *
	 * @param src
	 *            the event to copy from
	 */
	@Override
	public void copyFrom(final BasicEvent src) {
		final TypedEvent e = (TypedEvent) src;
		super.copyFrom(e);

		type = e.type;
	}
}
