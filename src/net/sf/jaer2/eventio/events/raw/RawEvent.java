package net.sf.jaer2.eventio.events.raw;

/**
 * A raw AER event, having an int (32 bit) timestamp and int (32 bit) raw
 * address.
 *
 * @author llongi
 */
public final class RawEvent {
	// 32 bit address and timestamp
	public int address;
	public int timestamp;

	/**
	 * Creates a new instance of EventRaw, with address and timestamp
	 * initialized to zero.
	 */
	public RawEvent() {
	}

	/**
	 * Creates a new instance of EventRaw, initialized with the given values.
	 *
	 * @param addr
	 *            the address
	 * @param ts
	 *            the timestamp
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
