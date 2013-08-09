package net.sf.jaer2.chips;

import java.util.List;

import net.sf.jaer2.eventio.eventpackets.EventPacket;
import net.sf.jaer2.eventio.eventpackets.raw.RawEventPacket;
import net.sf.jaer2.eventio.events.Event;

public interface Chip {
	public int getSizeX();

	public int getSizeY();

	public int getMaxSize();

	public int getMinSize();

	/**
	 * Total number of cells on the chip.
	 *
	 * @return number of cells.
	 */
	public int getNumCells();

	/**
	 * Total number of pixels on the chip.
	 *
	 * @return number of pixels.
	 */
	public int getNumPixels();

	public boolean compatibleWith(Chip chip);

	public List<Class<? extends Event>> getEventTypes();

	public <E extends Event> EventPacket<E> extractEventPacket(RawEventPacket rawEventPacket);

	public <E extends Event> RawEventPacket reconstructRawEventPacket(EventPacket<E> eventPacket);
}
