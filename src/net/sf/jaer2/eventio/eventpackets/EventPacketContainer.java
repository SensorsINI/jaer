package net.sf.jaer2.eventio.eventpackets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import net.sf.jaer2.eventio.events.Event;

import com.google.common.collect.Iterators;

public final class EventPacketContainer implements Iterable<Event> {
	private final Map<Class<? extends Event>, EventPacket<? extends Event>> eventPackets = new HashMap<>();

	private ArrayList<Event> eventsTimeOrdered;
	private boolean timeOrderingEnforced;

	public EventPacketContainer() {
		this(false);
	}

	public EventPacketContainer(final boolean timeOrder) {
		timeOrderingEnforced = timeOrder;

		if (timeOrderingEnforced) {
			eventsTimeOrdered = new ArrayList<>();
		}
	}

	public void clear() {
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			evtPkt.clearInternal();
		}

		if (timeOrderingEnforced) {
			// Rebuild global time-ordering list only once.
			rebuildGlobalTimeOrder();
		}
	}

	public int capacity() {
		int totalCapacity = 0;

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			totalCapacity += evtPkt.capacity();
		}

		return totalCapacity;
	}

	public int sizeFull() {
		int totalSizeFull = 0;

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			totalSizeFull += evtPkt.sizeFull();
		}

		return totalSizeFull;
	}

	public int size() {
		int totalSize = 0;

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			totalSize += evtPkt.size();
		}

		return totalSize;
	}

	public boolean isEmptyFull() {
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			if (!evtPkt.isEmptyFull()) {
				return false;
			}
		}

		return true;
	}

	public boolean isEmpty() {
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			if (!evtPkt.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	public int compactGain() {
		int compactGain = 0;

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			compactGain += evtPkt.compactGain();
		}

		return compactGain;
	}

	public void compact() {
		if (compactGain() == 0) {
			// Nothing to gain across all packets.
			return;
		}

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			evtPkt.compactInternal();
		}

		if (timeOrderingEnforced) {
			// Rebuild global time-ordering list only once.
			rebuildGlobalTimeOrder();
		}
	}

	public <E extends Event> EventPacket<E> createPacket(final Class<E> type) {
		@SuppressWarnings("unchecked")
		EventPacket<E> internalEventPacket = (EventPacket<E>) eventPackets.get(type);

		if (internalEventPacket == null) {
			// Packet of this type not found, create it and add it to map.
			internalEventPacket = new EventPacket<>(type);
			internalEventPacket.setParentContainer(this);

			eventPackets.put(type, internalEventPacket);
		}

		return internalEventPacket;
	}

	public <E extends Event> void appendPacket(final EventPacket<E> evtPacket) {
		createPacket(evtPacket.getEventType()).appendPacket(evtPacket);
	}

	public <E extends Event> void addPacket(final EventPacket<E> evtPacket) {
		// Use EventPacket.addPacket() directly here, which takes care of both
		// local and global time-ordering itself.
		createPacket(evtPacket.getEventType()).addPacket(evtPacket);
	}

	public <E extends Event> void addAllPackets(final EventPacket<E>[] evtPackets) {
		addAllPackets(Arrays.asList(evtPackets));
	}

	public <E extends Event> void addAllPackets(final Iterable<EventPacket<E>> evtPackets) {
		// Add all EventPackets. Use EventPacketContainer.appendPacket() because
		// of the possibility of different types due to inheritance effects.
		for (final EventPacket<E> evtPacket : evtPackets) {
			appendPacket(evtPacket);
		}

		// Make sure time ordering is still present, if requested.
		// Check all packets, since we can't know exactly which types were
		// manipulated above due to possible inheritance effects.
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			if (evtPkt.isTimeOrderingEnforced()) {
				evtPkt.timeOrderInternal();
			}
		}

		// Rebuild global time-ordering list only once.
		if (timeOrderingEnforced) {
			rebuildGlobalTimeOrder();
		}
	}

	@SuppressWarnings("unchecked")
	public <E extends Event> EventPacket<E> getPacket(final Class<E> type) {
		return (EventPacket<E>) eventPackets.get(type);
	}

	public <E extends Event> EventPacket<E> removePacket(final Class<E> type) {
		@SuppressWarnings("unchecked")
		final EventPacket<E> internalEventPacket = (EventPacket<E>) eventPackets.remove(type);

		if (internalEventPacket != null) {
			internalEventPacket.setParentContainer(null);
		}

		return internalEventPacket;
	}

	public boolean isTimeOrdered() {
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			if (!evtPkt.isTimeOrdered()) {
				return false;
			}
		}

		return true;
	}

	public void setTimeOrdered(final boolean timeOrder) {
		if (!timeOrder && !timeOrderingEnforced) {
			// In the case explicit time ordering is not wanted, nor required,
			// simply return without doing anything.
			return;
		}

		// In all other cases, enforce either the requirement or the will of the
		// user.
		timeOrder();
	}

	public boolean isTimeOrderingEnforced() {
		return timeOrderingEnforced;
	}

	public void setTimeOrderingEnforced(final boolean timeOrderEnforced) {
		if (timeOrderingEnforced == timeOrderEnforced) {
			// Return right away if setting to previous value (no change).
			return;
		}

		timeOrderingEnforced = timeOrderEnforced;

		// Ensure main time ordering list is available or cleared.
		if (timeOrderingEnforced && (eventsTimeOrdered == null)) {
			eventsTimeOrdered = new ArrayList<>();
		}
		else if (!timeOrderingEnforced && (eventsTimeOrdered != null)) {
			eventsTimeOrdered.clear();
			eventsTimeOrdered = null;
		}

		// If enabled, make sure all contained packets are enforcing too, which
		// will automatically order them if they aren't already so.
		if (timeOrderingEnforced) {
			for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
				evtPkt.setTimeOrderingEnforced(true);
			}

			// Rebuild global time-ordering list only once.
			rebuildGlobalTimeOrder();
		}
	}

	public void timeOrder() {
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			evtPkt.timeOrderInternal();
		}

		if (timeOrderingEnforced) {
			// Rebuild global time-ordering list only once.
			rebuildGlobalTimeOrder();
		}
	}

	void rebuildGlobalTimeOrder() {
		// Don't do anything if global time-ordering is disabled.
		if (!timeOrderingEnforced) {
			return;
		}

		// Clear current global time-order list.
		eventsTimeOrdered.clear();
		eventsTimeOrdered.ensureCapacity(sizeFull());

		// Regenerate it by adding all events from all packets.
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			for (final Iterator<? extends Event> iter = evtPkt.iteratorFull(); iter.hasNext();) {
				eventsTimeOrdered.add(iter.next());
			}
		}

		// Sort global time-order list by timestamp.
		Collections.sort(eventsTimeOrdered, new EventTimestampComparator());
	}

	private final class EventTimestampComparator implements Comparator<Event> {
		public EventTimestampComparator() {
		}

		@Override
		public int compare(final Event evt1, final Event evt2) {
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
	public Iterator<Event> iterator() {
		final ArrayList<Iterator<? extends Event>> iters = new ArrayList<>(eventPackets.size());

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			iters.add(evtPkt.iterator());
		}

		return Iterators.concat(iters.iterator());
	}

	public Iterator<Event> iteratorFull() {
		final ArrayList<Iterator<? extends Event>> iters = new ArrayList<>(eventPackets.size());

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			iters.add(evtPkt.iteratorFull());
		}

		return Iterators.concat(iters.iterator());
	}

	public Iterator<Event> iteratorTimeOrder() throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		return new TimeOrderIterator();
	}

	private final class TimeOrderIterator implements Iterator<Event> {
		private Iterator<Event> iterator = null;
		private Event currentEvent = null;

		public TimeOrderIterator() {
			iterator = iteratorTimeOrderFull();
		}

		@Override
		public boolean hasNext() {
			if (currentEvent != null) {
				return true;
			}

			while (iterator.hasNext()) {
				currentEvent = iterator.next();

				if (currentEvent.isValid()) {
					return true;
				}
			}

			return false;
		}

		@Override
		public Event next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			final Event evt = currentEvent;
			currentEvent = null;
			return evt;
		}

		@Override
		public void remove() {
			iterator.remove();
		}
	}

	public Iterator<Event> iteratorTimeOrderFull() throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		return eventsTimeOrdered.iterator();
	}
}
