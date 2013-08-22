package net.sf.jaer2.eventio.eventpackets.raw;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import net.sf.jaer2.eventio.events.raw.RawEvent;

public final class RawEventPacket implements Iterable<RawEvent> {
	private static final int DEFAULT_EVENT_CAPACITY = 2048;

	// RawEvents array and index into it for adding new elements.
	private final RawEvent[] rawEvents;
	private int lastRawEvent;

	public RawEventPacket() {
		// Use default capacity.
		rawEvents = new RawEvent[RawEventPacket.DEFAULT_EVENT_CAPACITY];
	}

	public RawEventPacket(final int capacity) {
		// Use user-supplied capacity.
		rawEvents = new RawEvent[capacity];
	}

	public void clear() {
		// Ensure freeing by GC.
		for (int i = 0; i < lastRawEvent; i++) {
			rawEvents[i] = null;
		}

		lastRawEvent = 0;
	}

	public int capacity() {
		return rawEvents.length;
	}

	public int size() {
		return lastRawEvent;
	}

	public boolean isEmpty() {
		return (lastRawEvent == 0);
	}

	public int addRawEvent(final RawEvent event) {
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

	public int addRawEvents(final RawEvent[] events) {
		// Efficiently convert array to array-list and use the collections
		// method.
		return addRawEvents(Arrays.asList(events));
	}

	public int addRawEvents(final Iterable<RawEvent> events) {
		final int origLastRawEvent = lastRawEvent;

		// Copy all events over.
		for (final RawEvent event : events) {
			// Exit loop if no more space in current array.
			if (lastRawEvent >= rawEvents.length) {
				break;
			}

			// Add event.
			rawEvents[lastRawEvent++] = event;
		}

		// Return number of added events.
		return lastRawEvent - origLastRawEvent;
	}

	@Override
	public Iterator<RawEvent> iterator() {
		return new RawEventPacketIterator();
	}

	private final class RawEventPacketIterator implements Iterator<RawEvent> {
		private int position = 0;

		@Override
		public boolean hasNext() {
			if (position < lastRawEvent) {
				return true;
			}

			return false;
		}

		@Override
		public RawEvent next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			return rawEvents[position++];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
