package net.sf.jaer2.eventio.eventpackets.raw;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import net.sf.jaer2.eventio.events.raw.RawEvent;

public final class RawEventPacket implements Iterable<RawEvent> {
	private static final int DEFAULT_EVENT_CAPACITY = 4096;

	// RawEvents array and index into it for adding new elements.
	private int lastRawEvent;
	private RawEvent[] rawEvents;

	public RawEventPacket() {
		// Use default capacity.
		rawEvents = new RawEvent[DEFAULT_EVENT_CAPACITY];
	}

	public RawEventPacket(int capacity) {
		// Use user-supplied capacity.
		rawEvents = new RawEvent[capacity];
	}

	public void clear() {
		lastRawEvent = 0;
	}

	public int getCapacity() {
		return rawEvents.length;
	}

	public int getSize() {
		return lastRawEvent;
	}

	public int addRawEvent(RawEvent event) {
		// If no more space to add event, exit and report.
		if (lastRawEvent >= rawEvents.length) {
			// Return number of added events: zero on failure.
			return 0;
		}

		// Add event.
		rawEvents[lastRawEvent++] = event;

		// Return number of added events: always one here.
		return 1;
	}

	public int addRawEvents(RawEvent[] events) {
		// Efficiently convert array to array-list and use the collections method.
		return addRawEvents(Arrays.asList(events));
	}

	public int addRawEvents(Collection<RawEvent> events) {
		int tmpLastRawEvent = lastRawEvent;

		// Copy all events over.
		for (RawEvent event : events) {
			// Exit loop if no more space in current array.
			if (lastRawEvent >= rawEvents.length) {
				break;
			}

			// Add event.
			rawEvents[lastRawEvent++] = event;
		}

		// Return number of added events.
		return lastRawEvent - tmpLastRawEvent;
	}

	@Override
	public Iterator<RawEvent> iterator() {
		// TODO Auto-generated method stub
		return null;
	}
}
