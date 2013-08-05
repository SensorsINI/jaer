package net.sf.jaer2.eventio.eventpackets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.jaer2.eventio.events.Event;

public final class EventPacketContainer implements Iterable<Event> {
	private final Map<Class<? extends Event>, EventPacket<? extends Event>> eventPackets = new HashMap<>();

	private final List<Event> eventsTimeOrdered = new ArrayList<>();
	private boolean timeOrderingEnforced;

	public EventPacketContainer() {
		this(false);
	}

	public EventPacketContainer(final boolean timeOrder) {
		timeOrderingEnforced = timeOrder;
	}

	public void clear() {
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			evtPkt.clear();
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
		boolean isEmptyFull = true;

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			if (!evtPkt.isEmptyFull()) {
				isEmptyFull = false;
				break;
			}
		}

		return isEmptyFull;
	}

	public boolean isEmpty() {
		boolean isEmpty = true;

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			if (!evtPkt.isEmpty()) {
				isEmpty = false;
				break;
			}
		}

		return isEmpty;
	}

	public int compactGain() {
		int compactGain = 0;

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			compactGain += evtPkt.compactGain();
		}

		return compactGain;
	}

	public void compact() {
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			evtPkt.compact();
		}
	}

	public <E extends Event> void appendEventPacket(final EventPacket<E> evtPacket) {
		@SuppressWarnings("unchecked")
		final EventPacket<E> eventPacket = (EventPacket<E>) eventPackets.get(evtPacket.getEventType());

		if (eventPacket == null) {
			// Packet of this type not found, add it to map.
			evtPacket.setParentContainer(this);
			eventPackets.put(evtPacket.getEventType(), evtPacket);
		}
		else {
			// Found packet of this type already, adding packet to it.
			eventPacket.appendEventPacket(evtPacket);
		}
	}

	public <E extends Event> void addEventPacket(final EventPacket<E> evtPacket) {
		appendEventPacket(evtPacket);

		// Make sure time ordering is still present, if requested.
		if (eventPackets.get(evtPacket.getEventType()).isTimeOrderingEnforced()) {
			eventPackets.get(evtPacket.getEventType()).timeOrder();
		}
	}

	public <E extends Event> void addEventPackets(final EventPacket<E>[] evtPackets) {
		addEventPackets(Arrays.asList(evtPackets));
	}

	public <E extends Event> void addEventPackets(final Iterable<EventPacket<E>> evtPackets) {
		Class<E> type = null;

		// Add all EventPackets.
		for (final EventPacket<E> evtPacket : evtPackets) {
			appendEventPacket(evtPacket);
			type = evtPacket.getEventType();
		}

		// Make sure time ordering is still present, if requested.
		if (eventPackets.get(type).isTimeOrderingEnforced()) {
			eventPackets.get(type).timeOrder();
		}
	}

	@SuppressWarnings("unchecked")
	public <E extends Event> EventPacket<E> getEventPacket(final Class<E> type) {
		return (EventPacket<E>) eventPackets.get(type);
	}

	public boolean isTimeOrdered() {
		boolean isTimeOrdered = true;

		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			if (!evtPkt.isTimeOrdered()) {
				isTimeOrdered = false;
				break;
			}
		}

		return isTimeOrdered;
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
		timeOrderingEnforced = timeOrderEnforced;

		// If enabled, make sure all contained packets are enforcing too, which
		// will automatically order them if they aren't already so.
		if (timeOrderingEnforced) {
			for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
				evtPkt.setTimeOrderingEnforced(timeOrderingEnforced);
			}
		}
	}

	public void timeOrder() {
		for (final EventPacket<? extends Event> evtPkt : eventPackets.values()) {
			evtPkt.timeOrder();
		}
	}

	@Override
	public Iterator<Event> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	public Iterator<Event> iteratorFull() {
		// TODO Auto-generated method stub
		return null;
	}

	public Iterator<Event> iteratorTimeOrder() {
		// TODO Auto-generated method stub
		return null;
	}

	public Iterator<Event> iteratorTimeOrderFull() {
		// TODO Auto-generated method stub
		return null;
	}
}
