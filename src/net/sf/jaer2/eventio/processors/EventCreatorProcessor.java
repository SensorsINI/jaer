package net.sf.jaer2.eventio.processors;

import net.sf.jaer2.eventio.ProcessorChain;

public abstract class EventCreatorProcessor extends EventProcessor {
	public EventCreatorProcessor(final ProcessorChain chain) {
		super(chain);
	}
}
