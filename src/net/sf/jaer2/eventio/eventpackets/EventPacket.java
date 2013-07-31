package net.sf.jaer2.eventio.eventpackets;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import net.sf.jaer2.eventio.events.BaseEvent;
import net.sf.jaer2.eventio.events.Event;

public final class EventPacket<E extends Event> implements Iterable<E> {
	private static final int DEFAULT_EVENT_CAPACITY = 4096;

	// RawEvents array and index into it for adding new elements.
	protected final E[] events;
	protected int lastEvent;
	protected int validEvents;

	private Class<? extends Event> eventType;

	public Class<? extends Event> getEventType() {
		return eventType;
	}

	public EventPacket() {
		this(BaseEvent.class);
	}

	public EventPacket(final Class<? extends Event> type) {
		this(type, EventPacket.DEFAULT_EVENT_CAPACITY);
	}

	@SuppressWarnings("unchecked")
	public EventPacket(final Class<? extends Event> type, final int capacity) {
		eventType = type;

		// Use user-supplied capacity.
		events = (E[]) Array.newInstance(eventType, capacity);
	}

	public void clear() {
		lastEvent = 0;
		validEvents = 0;
	}

	public int capacity() {
		return events.length;
	}

	public int sizeFull() {
		return lastEvent;
	}

	public int size() {
		return validEvents;
	}

	public boolean isEmptyFull() {
		return (lastEvent == 0);
	}

	public boolean isEmpty() {
		return (validEvents == 0);
	}

	public boolean addEvent(final E evt) {
		// If no more space to add event, exit and report.
		if (lastEvent >= events.length) {
			// Return number of added events: zero on failure.
			return false;
		}

		// Add event.
		events[lastEvent++] = evt;

		if (evt.isValid()) {
			validEvents++;
		}

		// Return number of added events: always one here.
		return true;
	}

	public int addEvents(final E[] evts) {
		// Efficiently convert array to array-list and use the collections
		// method.
		return addEvents(Arrays.asList(evts));
	}

	public int addEvents(final Iterable<E> evts) {
		final int origLastEvent = lastEvent;

		// Copy all events over.
		for (final E evt : evts) {
			// Exit loop if no more space in current array.
			if (lastEvent >= events.length) {
				break;
			}

			// Add event.
			events[lastEvent++] = evt;

			if (evt.isValid()) {
				validEvents++;
			}
		}

		// Return number of added events.
		return lastEvent - origLastEvent;
	}

	public void addEventPacket(final EventPacket<E> evtPacket) {
		addEvents(evtPacket);
	}

	/**
	 * Calculate how much events can be freed by compacting the EventPacket.
	 *
	 * @return number of freeable events
	 */
	public int compactGain() {
		return (lastEvent - validEvents);
	}

	/**
	 * Compact EventPacket by removing all invalid events.
	 */
	public void compact() {
		// Compact only if there actually is anything to compact.
		if (compactGain() == 0) {
			return;
		}

		int copyOffset = 0;

		for (int position = 0; position < lastEvent; position++) {
			if (events[position].isValid()) {
				if (copyOffset == 0) {
					continue;
				}

				events[(position - copyOffset)] = events[position];
			}
			else {
				copyOffset++;
			}
		}

		// Reset array length.
		lastEvent -= copyOffset;
	}

	@Override
	public Iterator<E> iterator() {
		return new EventPacketIterator();
	}

	private final class EventPacketIterator implements Iterator<E> {
		private int position = 0;
		private boolean nextCalled = false;

		public EventPacketIterator() {
		}

		/**
		 * Check if a valid event is available on next iteration, but do so
		 * without any side effects or global state changes (pure function).
		 *
		 * @return offset from current position of next valid element, or -1
		 *         if none exists
		 */
		private int hasNextPure() {
			// Fail fast if there are no valid events at all.
			if (validEvents <= 0) {
				return -1;
			}

			int offset = 0;

			while ((position + offset) < lastEvent) {
				// Check for event validity.
				if (events[(position + offset)].isValid()) {
					return offset;
				}

				// Advance offset to next event.
				offset++;
			}

			// Nothing found.
			return -1;
		}

		@Override
		public boolean hasNext() {
			return (hasNextPure() >= 0);
		}

		@Override
		public E next() {
			final int offset = hasNextPure();

			if (offset < 0) {
				throw new NoSuchElementException();
			}

			position += offset;
			nextCalled = true;

			return events[position++];
		}

		@Override
		public void remove() {
			if (!nextCalled) {
				throw new IllegalStateException();
			}

			// Support remove() correctly.
			nextCalled = false;

			// Remove by invalidating the current element (-1 because next()
			// always does +1 internally).
			events[(position - 1)].invalidate();
			validEvents--;
		}
	}

	public Iterator<E> iteratorFull() {
		return new EventPacketIteratorFull();
	}

	private final class EventPacketIteratorFull implements Iterator<E> {
		private int position = 0;
		private boolean nextCalled = false;

		public EventPacketIteratorFull() {
		}

		@Override
		public boolean hasNext() {
			if (position < lastEvent) {
				return true;
			}

			return false;
		}

		@Override
		public E next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			// Support remove() correctly.
			nextCalled = true;

			return events[position++];
		}

		@Override
		public void remove() {
			if (!nextCalled) {
				throw new IllegalStateException();
			}

			nextCalled = false;

			// Remove by invalidating the current element (-1 because next()
			// always does +1 internally), but only if it was valid originally.
			if (events[(position - 1)].isValid()) {
				events[(position - 1)].invalidate();
				validEvents--;
			}
		}
	}
}
