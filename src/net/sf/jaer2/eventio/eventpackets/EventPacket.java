package net.sf.jaer2.eventio.eventpackets;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

import net.sf.jaer2.eventio.events.Event;

public final class EventPacket<E extends Event> implements Iterable<E> {
	private static final int DEFAULT_EVENT_CAPACITY = 4096;

	// RawEvents array and index into it for adding new elements.
	protected E[] events;
	protected int lastEvent;
	protected int validEvents;

	private final Class<E> eventType;

	private boolean timeOrdered;
	private boolean timeOrderingEnforced;

	public Class<? extends Event> getEventType() {
		return eventType;
	}

	public EventPacket(final Class<E> type) {
		this(type, false);
	}

	public EventPacket(final Class<E> type, final boolean timeOrder) {
		this(type, EventPacket.DEFAULT_EVENT_CAPACITY, timeOrder);
	}

	public EventPacket(final Class<E> type, final int capacity) {
		this(type, capacity, false);
	}

	public EventPacket(final Class<E> type, final int capacity, final boolean timeOrder) {
		eventType = type;
		timeOrderingEnforced = timeOrder;

		// Use user-supplied capacity.
		events = net.sf.jaer2.util.Arrays.newArrayFromType(type, capacity);
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

	/**
	 * Double array capacity until there is enough place for the supplied amount
	 * of elements to be added and then copy over old elements to new array.
	 *
	 * @param number
	 *            number of additional elements to ensure available space for
	 */
	private void increaseCapacity(final int number) {
		int arrayLength = events.length;
		int currentCapacity = arrayLength - lastEvent;

		// Keep doubling array capacity until it's enough.
		while (currentCapacity < number) {
			arrayLength *= 2;
			currentCapacity = arrayLength - lastEvent;
		}

		final E[] eventsNew = net.sf.jaer2.util.Arrays.newArrayFromType(eventType, arrayLength);

		System.arraycopy(events, 0, eventsNew, 0, events.length);

		events = eventsNew;
	}

	public void ensureCapacity(final int number) {
		// First check if there's enough capacity in the still empty part of the
		// events array.
		int currentCapacity = events.length - lastEvent;

		if (currentCapacity >= number) {
			return;
		}

		// Then check if by compacting we can gain enough capacity on the
		// current events array.
		currentCapacity += compactGain();
		compact();

		if (currentCapacity >= number) {
			return;
		}

		// As a last resort, increase the capacity, so that there is enough
		// place for sure available.
		increaseCapacity(number);
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

	/**
	 * Add one event to the EventPacket, after all other events.
	 * If needed, take care to manually ensure time ordering, either by adding
	 * the packets in such an order that you know follows time ordering and then
	 * calling setTimeOrdered(true) yourself, or by calling
	 * setTimeOrdered(false), which will take the correct actions to ensure
	 * compliance with time ordering policy as needed.
	 *
	 * @param evt
	 *            event to add to the end of the EventPacket
	 */
	public void addEventAfterAll(final E evt) {
		ensureCapacity(1);

		// Add event.
		events[lastEvent++] = evt;

		validEvents++;
	}

	/**
	 * Add one event to the EventPacket, ensuring time ordering as needed by the
	 * time ordering policy.
	 *
	 * @param evt
	 *            event to add to the EventPacket
	 */
	public void addEvent(final E evt) {
		addEventAfterAll(evt);

		// Make sure time ordering is still present, if requested.
		if (timeOrderingEnforced) {
			timeOrder();
		}
	}

	/**
	 * Add multiple events to the EventPacket, ensuring time ordering as needed
	 * by the time ordering policy.
	 *
	 * @param evts
	 *            events to add to the EventPacket
	 */
	public void addEvents(final E[] evts) {
		// Efficiently convert array to array-list, so that the Iterable-based
		// method can be used.
		addEvents(Arrays.asList(evts));
	}

	/**
	 * Add multiple events to the EventPacket, ensuring time ordering as needed
	 * by the time ordering policy.
	 *
	 * @param evts
	 *            events to add to the EventPacket
	 */
	public void addEvents(final Iterable<E> evts) {
		// Copy all events over.
		for (final E evt : evts) {
			addEventAfterAll(evt);
		}

		// Make sure time ordering is still present, if requested.
		if (timeOrderingEnforced) {
			timeOrder();
		}
	}

	public void addEventPacket(final EventPacket<E> evtPacket) {
		// TODO: implement
	}

	public void addEventPackets(final EventPacket<E>[] evtPackets) {
		addEventPackets(Arrays.asList(evtPackets));
	}

	public void addEventPackets(final Iterable<EventPacket<E>> evtPackets) {
		// TODO: implement
	}

	public boolean isTimeOrdered() {
		return timeOrdered;
	}

	public void setTimeOrdered(final boolean timeOrder) {
		timeOrdered = timeOrder;

		// If time ordering enforced, setting time order manually to false leads
		// to explicit call to reorder and resets this to true.
		if (timeOrderingEnforced && !timeOrdered) {
			timeOrder();
		}
	}

	public boolean isTimeOrderingEnforced() {
		return timeOrderingEnforced;
	}

	public void setTimeOrderingEnforced(final boolean timeOrderEnforced) {
		timeOrderingEnforced = timeOrderEnforced;

		// If time ordering enforced, but not yet time ordered, do so.
		if (timeOrderingEnforced && !timeOrdered) {
			timeOrder();
		}
	}

	public void timeOrder() {
		// Nothing to do if already timeOrdered.
		if (timeOrdered) {
			return;
		}

		timeOrdered = true;

		// Sort by timestamp.
		Arrays.sort(events, 0, lastEvent, new EventTimestampComparator());
	}

	private final class EventTimestampComparator implements Comparator<E> {
		public EventTimestampComparator() {
		}

		@Override
		public int compare(final E evt1, final E evt2) {
			if (evt1.getTimestamp() > evt2.getTimestamp()) {
				return 1;
			}

			if (evt1.getTimestamp() < evt2.getTimestamp()) {
				return -1;
			}

			return 0;
		}
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
