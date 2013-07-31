package net.sf.jaer2.eventio.eventpackets;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.sf.jaer2.eventio.events.Event;

public final class EventPacketContainer implements Iterable<Event> {
	private final Map<Class<? extends Event>, EventPacket<? extends Event>> packetMap = new HashMap<>();

	public <E extends Event> boolean addEventPacket(final EventPacket<E> eventPacket) {
		if (packetMap.containsKey(eventPacket.getEventType())) {
			return false;
		}

		packetMap.put(eventPacket.getEventType(), eventPacket);

		return true;
	}

	@SuppressWarnings("unchecked")
	public <E extends Event> EventPacket<E> getEventPacket(final Class<E> type) {
		return (EventPacket<E>) packetMap.get(type);
	}

	@Override
	public Iterator<Event> iterator() {
		// TODO Auto-generated method stub
		return null;
	}
}
