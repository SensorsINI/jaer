package ch.unizh.ini.jaer2.chip.dvs;

import java.util.List;

import net.sf.jaer2.chips.Chip;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.eventpackets.raw.RawEventPacket;
import net.sf.jaer2.eventio.events.Event;

public class DVS128 implements Chip {

	@Override
	public int getSizeX() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getSizeY() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMinSize() {
		// TODO Auto-generated method stub
		return 0;
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
	public boolean compatibleWith(Chip chip) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<Class<? extends Event>> getEventTypes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EventPacketContainer extractEventPacketContainer(RawEventPacket rawEventPacket) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RawEventPacket reconstructRawEventPacket(EventPacketContainer eventPacketContainer) {
		// TODO Auto-generated method stub
		return null;
	}

}
