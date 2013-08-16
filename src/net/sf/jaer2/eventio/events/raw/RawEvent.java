package net.sf.jaer2.eventio.events.raw;

/**
 * A raw AER event, having an int (32 bit) time-stamp and int (32 bit) raw
 * address.
 *
 * @author llongi
 */
public final class RawEvent {
	// 32 bit address and time-stamp
	public int address;
	public int timestamp;

	/**
	 * Creates a new instance of RawEvent, with address and time-stamp
	 * initialized to zero.
	 */
	public RawEvent() {
	}

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

	/**
	 * Return a new instance of RawEvent, with all the data copied over.
	 *
	 * @return deep copy of this RawEvent.
	 */
	public RawEvent deepCopy() {
		return new RawEvent(address, timestamp);
	}

	@Override
	public String toString() {
		return String.format("RawEvent with address %d and timestamp %d", address, timestamp);
	}
}
