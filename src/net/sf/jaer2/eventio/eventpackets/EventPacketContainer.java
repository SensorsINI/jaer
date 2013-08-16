package net.sf.jaer2.eventio.eventpackets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.sf.jaer2.eventio.events.Event;
import net.sf.jaer2.eventio.processors.Processor;
import net.sf.jaer2.util.PredicateIterator;

import org.apache.commons.lang3.tuple.ImmutablePair;

import com.google.common.collect.Iterators;

public final class EventPacketContainer implements Iterable<Event> {
	// Lookup map: a Pair consisting of the Event type and the Event source
	// determine the returned EventPacket<Type>.
	private final Map<ImmutablePair<Class<? extends Event>, Integer>, EventPacket<? extends Event>> eventPackets = new HashMap<>();
	private final ArrayList<Object> annotateDataSets = new ArrayList<>(8);
	private final int sourceId;

	private ArrayList<Event> eventsTimeOrdered;
	private boolean timeOrderingEnforced;

	public EventPacketContainer(final Processor parentProcessor) {
		this(parentProcessor, false);
	}

	public EventPacketContainer(final Processor parentProcessor, final boolean timeOrder) {
		sourceId = parentProcessor.getProcessorId();

		timeOrderingEnforced = timeOrder;

		if (timeOrderingEnforced) {
			eventsTimeOrdered = new ArrayList<>();
		}
	}

	public void addToAnnotateDataSets(final Object annotateData) {
		annotateDataSets.add(annotateData);
	}

	public Object removeFromAnnotateDataSets() {
		return annotateDataSets.remove(0);
	}

	public int getSourceId() {
		return sourceId;
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

	public <E extends Event> EventPacket<E> createPacket(final Class<E> type, final int source) {
		@SuppressWarnings("unchecked")
		EventPacket<E> internalEventPacket = (EventPacket<E>) eventPackets.get(new ImmutablePair<>(type, source));

		if (internalEventPacket == null) {
			// Packet of this type not found, create it and add it to map.
			internalEventPacket = new EventPacket<>(type, source);
			internalEventPacket.setParentContainer(this);

			eventPackets.put(new ImmutablePair<Class<? extends Event>, Integer>(type, source), internalEventPacket);
		}

		return internalEventPacket;
	}

	public <E extends Event> void appendPacket(final EventPacket<E> evtPacket) {
		createPacket(evtPacket.getEventType(), evtPacket.getEventSource()).appendPacket(evtPacket);
	}

	public <E extends Event> void addPacket(final EventPacket<E> evtPacket) {
		// Use EventPacket.addPacket() directly here, which takes care of both
		// local and global time-ordering itself.
		createPacket(evtPacket.getEventType(), evtPacket.getEventSource()).addPacket(evtPacket);
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
	public <E extends Event> EventPacket<E> getPacket(final Class<E> type, final int source) {
		return (EventPacket<E>) eventPackets.get(new ImmutablePair<>(type, source));
	}

	public <E extends Event> EventPacket<E> removePacket(final Class<E> type, final int source) {
		@SuppressWarnings("unchecked")
		final EventPacket<E> internalEventPacket = (EventPacket<E>) eventPackets.remove(new ImmutablePair<>(type,
			source));

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
		for (final Iterator<Event> iter = iteratorFull(); iter.hasNext();) {
			eventsTimeOrdered.add(iter.next());
		}

		// Sort global time-order list by timestamp.
		Collections.sort(eventsTimeOrdered, new EventTimestampComparator());
	}

	private final class EventTimestampComparator implements Comparator<Event> {
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
		return new PredicateIterator<Event>(iteratorFull()) {
			@Override
			public boolean verifyPredicate(final Event element) {
				return element.isValid();
			}
		};
	}

	public Iterator<Event> iteratorType(final Class<? extends Event> type) {
		return new PredicateIterator<Event>(iteratorFullType(type)) {
			@Override
			public boolean verifyPredicate(final Event element) {
				return element.isValid();
			}
		};
	}

	public Iterator<Event> iteratorSource(final int source) {
		return new PredicateIterator<Event>(iteratorFullSource(source)) {
			@Override
			public boolean verifyPredicate(final Event element) {
				return element.isValid();
			}
		};
	}

	public Iterator<Event> iteratorTypeSource(final Class<? extends Event> type, final int source) {
		return new PredicateIterator<Event>(iteratorFullTypeSource(type, source)) {
			@Override
			public boolean verifyPredicate(final Event element) {
				return element.isValid();
			}
		};
	}

	public Iterator<Event> iteratorFull() {
		final ArrayList<Iterator<? extends Event>> iters = new ArrayList<>(eventPackets.size());

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			iters.add(evtPkt.iteratorFull());
		}

		return Iterators.concat(iters.iterator());
	}

	public Iterator<Event> iteratorFullType(final Class<? extends Event> type) {
		final ArrayList<Iterator<? extends Event>> iters = new ArrayList<>(eventPackets.size());

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			if (evtPkt.getEventType().equals(type)) {
				iters.add(evtPkt.iteratorFull());
			}
		}

		return Iterators.concat(iters.iterator());
	}

	public Iterator<Event> iteratorFullSource(final int source) {
		final ArrayList<Iterator<? extends Event>> iters = new ArrayList<>(eventPackets.size());

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			if (evtPkt.getEventSource() == source) {
				iters.add(evtPkt.iteratorFull());
			}
		}

		return Iterators.concat(iters.iterator());
	}

	public Iterator<Event> iteratorFullTypeSource(final Class<? extends Event> type, final int source) {
		@SuppressWarnings("unchecked")
		final EventPacket<Event> evtPkt = (EventPacket<Event>) getPacket(type, source);

		if (evtPkt == null) {
			return Collections.emptyIterator();

		}

		return evtPkt.iteratorFull();
	}

	public Iterator<Event> iteratorTimeOrder() throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		return new PredicateIterator<Event>(iteratorTimeOrderFull()) {
			@Override
			public boolean verifyPredicate(final Event element) {
				return element.isValid();
			}
		};
	}

	public Iterator<Event> iteratorTimeOrderType(final Class<? extends Event> type)
		throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		return new PredicateIterator<Event>(iteratorTimeOrderFull()) {
			@Override
			public boolean verifyPredicate(final Event element) {
				return (element.isValid() && element.getEventType().equals(type));
			}
		};
	}

	public Iterator<Event> iteratorTimeOrderSource(final int source) throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		return new PredicateIterator<Event>(iteratorTimeOrderFull()) {
			@Override
			public boolean verifyPredicate(final Event element) {
				return (element.isValid() && (element.getEventSource() == source));
			}
		};
	}

	public Iterator<Event> iteratorTimeOrderTypeSource(final Class<? extends Event> type, final int source)
		throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		@SuppressWarnings("unchecked")
		final EventPacket<Event> evtPkt = (EventPacket<Event>) getPacket(type, source);

		if (evtPkt == null) {
			return Collections.emptyIterator();

		}

		return evtPkt.iteratorTimeOrder();
	}

	public Iterator<Event> iteratorTimeOrderFull() throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		return eventsTimeOrdered.iterator();
	}

	public Iterator<Event> iteratorTimeOrderFullType(final Class<? extends Event> type)
		throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		return new PredicateIterator<Event>(iteratorTimeOrderFull()) {
			@Override
			public boolean verifyPredicate(final Event element) {
				return (element.getEventType().equals(type));
			}
		};
	}

	public Iterator<Event> iteratorTimeOrderFullSource(final int source) throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		return new PredicateIterator<Event>(iteratorTimeOrderFull()) {
			@Override
			public boolean verifyPredicate(final Event element) {
				return (element.getEventSource() == source);
			}
		};
	}

	public Iterator<Event> iteratorTimeOrderFullTypeSource(final Class<? extends Event> type, final int source)
		throws UnsupportedOperationException {
		if (!timeOrderingEnforced) {
			throw new UnsupportedOperationException("EventPacketContainer doesn't support global time-ordering.");
		}

		@SuppressWarnings("unchecked")
		final EventPacket<Event> evtPkt = (EventPacket<Event>) getPacket(type, source);

		if (evtPkt == null) {
			return Collections.emptyIterator();

		}

		return evtPkt.iteratorTimeOrderFull();
	}
}
