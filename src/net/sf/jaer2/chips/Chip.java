package net.sf.jaer2.chips;

import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.eventpackets.raw.RawEventPacket;
import net.sf.jaer2.eventio.events.Event;

import com.google.common.collect.ImmutableList;

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

	public boolean compatibleWith(final Chip chip);

	public ImmutableList<Class<? extends Event>> getEventTypes();

	public void extractEventPacketContainer(final RawEventPacket rawEventPacket,
		final EventPacketContainer eventPacketContainer);

	public void reconstructRawEventPacket(final EventPacketContainer eventPacketContainer,
		final RawEventPacket rawEventPacket);
}
