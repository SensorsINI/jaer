package net.sf.jaer2.eventio.processors;

import java.util.Set;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;

public final class SynchronizationProcessor extends EventProcessor {
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

	@Override
	protected void setAdditionalOutputTypes(@SuppressWarnings("unused") final Set<Class<? extends Event>> outputs) {
		// Empty, no new output types are ever produced here by itself.
		// The fully depend on the selected Input types.
	}
}
