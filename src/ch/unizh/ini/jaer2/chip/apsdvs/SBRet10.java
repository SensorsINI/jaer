package ch.unizh.ini.jaer2.chip.apsdvs;

import net.sf.jaer2.chips.Chip;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.eventpackets.raw.RawEventPacket;
import net.sf.jaer2.eventio.events.Event;
import net.sf.jaer2.eventio.events.PolarityEvent;
import net.sf.jaer2.eventio.events.SampleEvent;
import net.sf.jaer2.eventio.events.SpecialEvent;

import com.google.common.collect.ImmutableList;

public class SBRet10 implements Chip {
	@Override
	public int getSizeX() {
		return 240;
	}

	@Override
	public int getSizeY() {
		return 180;
	}

	@Override
	public int getMaxSize() {
		return 240;
	}

	@Override
	public int getMinSize() {
		return 180;
	}

	@Override
	public int getNumCells() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getNumPixels() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean compatibleWith(final Chip chip) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ImmutableList<Class<? extends Event>> getEventTypes() {
		return ImmutableList.<Class<? extends Event>> of(PolarityEvent.class, SampleEvent.class, SpecialEvent.class);
	}

	@Override
	public void extractEventPacketContainer(final RawEventPacket rawEventPacket,
		final EventPacketContainer eventPacketContainer) {
		// TODO Auto-generated method stub

	}

	@Override
	public void reconstructRawEventPacket(final EventPacketContainer eventPacketContainer,
		final RawEventPacket rawEventPacket) {
		// TODO Auto-generated method stub

	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
