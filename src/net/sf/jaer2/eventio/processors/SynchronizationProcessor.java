package net.sf.jaer2.eventio.processors;

import java.util.Set;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;

public class SynchronizationProcessor extends EventProcessor {
	public SynchronizationProcessor(final ProcessorChain chain) {
		super(chain);
	}

	@Override
	protected void processEvents(@SuppressWarnings("unused") final EventPacketContainer container) {
		// TODO: write synchronization processor.
	}

	@Override
	protected void setCompatibleInputTypes(final Set<Class<? extends Event>> inputs) {
		// Accepts all inputs.
		inputs.add(Event.class);
	}

	@SuppressWarnings("unused")
	@Override
	protected void setAdditionalOutputTypes(final Set<Class<? extends Event>> outputs) {
		// Empty, no new output types are ever produced here.
	}
}
