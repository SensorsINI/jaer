package net.sf.jaer2.eventio.events.raw;

import java.io.Serializable;

/**
 * A read-only raw AER event, having an int (32 bit) time-stamp and int (32 bit)
 * raw address.
 *
 * @author llongi
 */
public final class RawEvent implements Serializable {
	private static final long serialVersionUID = 808179108331580491L;

	// 32 bit address and time-stamp
	public final int address;
	public final int timestamp;

	/**
	 * Creates a new instance of RawEvent, initialized with the given values.
	 *
	 * @param addr
	 *            the address.
	 * @param ts
	 *            the time-stamp.
	 */
	public RawEvent(final int addr, final int ts) {
		address = addr;
		timestamp = ts;
	}

	@Override
	public String toString() {
		return String.format("RawEvent with address %d and timestamp %d", address, timestamp);
	}
}
