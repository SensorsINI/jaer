/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.event;

import java.util.Iterator;

import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;

/**
 * A "cooked" event packet containing ApsDvsEvent.
 *
 * <p>
 * This subclass of EventPacket allows iteration of just the events disregarding the
 * image sensor samples. To use this type of packet for processing DVS events,
 * you can just use the default iterator (i.e., for(BasicEvent e:inputPacket)
 * and you will get just the DVS events. However, if you iterate over <code>ApsDvsEventPacket</code>,
 * then the APS events will be bypassed to an internal output packet of <code>ApsDvsEventPacket</code>
 * called {@link #outputPacket}. This packet is automatically initialized when you call checkOutputPacketEventType(in).
 * <p>
 * Note that events that are "filteredOut" by a preceding filter (by it calling the setFilteredOut method on the event)
 * may not be filtered out
 * by the InputIterator of this packet if they are the last event in a packet.
 * </p>
 * Call
 *
 * <pre>
 *
 * checkOutputPacketEventType(in);
 * </pre>
 *
 * before your iterator
 * <p>
 * Internally this class overrides the default iterator of EventPacket.
 *
 * @author Christian Brandli, Tobi Delbruck
 * @see BackgroundActivityFilter
 */
public class ApsDvsEventPacket<E extends ApsDvsEvent> extends EventPacket<E> {

	/**
	 * Constructs a new EventPacket filled with the given event class.
	 *
	 * @see net.sf.jaer.event.BasicEvent
	 */
	public ApsDvsEventPacket(final Class<? extends ApsDvsEvent> eventClass) {
		if (!BasicEvent.class.isAssignableFrom(eventClass)) { // Check if evenClass is a subclass of BasicEvent
			throw new Error("making EventPacket that holds " + eventClass + " but these are not assignable from BasicEvent");
		}
		setEventClass(eventClass);
	}

	/**
	 * Constructs a new ApsDvsEventPacket from this one containing {@link #getEventClass()}.
	 * This method overrides {@link EventPacket#constructNewPacket() } to construct <code>ApsDvsEventPacket</code> so
	 * it is a kind of copy constructor.
	 *
	 * @see #setEventClass(java.lang.Class)
	 * @see #setEventClass(java.lang.reflect.Constructor)
	 */
	@Override
	public EventPacket<E> constructNewPacket() {
		final ApsDvsEventPacket<E> packet = new ApsDvsEventPacket<>(getEventClass());
		return packet;
	}

	/**
	 * This iterator (the default) just iterates over DVS events in the packet.
	 * Returns after initializing the iterator over input events.
	 *
	 * @return an iterator that can iterate over the DVS events.
	 */
	@Override
	public Iterator<E> inputIterator() {
		if (inputIterator == null || !(inputIterator instanceof ApsDvsEventPacket.InDvsItr)) {
			inputIterator = new InDvsItr();
		}
		else {
			inputIterator.reset();
		}

		return inputIterator;
	}

	/**
	 * This iterator iterates over all events, DVS, APS, and IMU. Use this one if you want all data.
	 * Returns after initializing the iterator over input events.
	 *
	 * @return an iterator that can iterate over all the events, DVS, APS, and IMU.
	 */
	public Iterator<E> fullIterator() {
		return super.inputIterator();
	}

	/**
	 * Initializes and returns the default iterator over events of type <E> consisting of the DVS events.
	 * This is the default iterator obtained by <code>for(BasicEvent e:in)</code>.
	 *
	 * @return an Iterator only yields DVS events.
	 */
	@Override
	public Iterator<E> iterator() {
		return inputIterator();
	}

	/**
	 * This iterator iterates over DVS events by copying
	 * only them to a reused temporary buffer "output"
	 * packet that is returned and is used for iteration.
	 * Remember to call checkOutputPacket or memory will be quickly consumed. See BackgroundActivityFilter for example.
	 *
	 */
	public class InDvsItr extends InItr {
		protected InDvsItr() {
			super();
		}

		/**
		 * Overrides the default hasNext() method to jump over non-DVS events.
		 *
		 * @return true if there is a (not filteredOut) event left in the packet
		 */
		@Override
		public boolean hasNext() {
//			if (usingTimeout && timeLimitTimer.isTimedOut()) {
//				return false;
//			}
//              tobi removed, too many problems with it

			while ((cursor < size) && (elementData[cursor] != null)
				&& (elementData[cursor].isFilteredOut() || !elementData[cursor].isDVSEvent())) {
				filteredOutCount++;
				cursor++;
			} // TODO can get null events here which causes null pointer exception; not clear how this is possible

			return cursor < size;
		}

		@Override
		public String toString() {
			return "InputEventIterator (DVS ONLY) cursor=" + cursor + " for packet with size=" + size;
		}
	}
}
