package net.sf.jaer2.eventio.processors;

import java.util.ArrayList;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;

public abstract class EventProcessor extends Processor {
	private final ArrayList<EventPacketContainer> toProcess = new ArrayList<>(32);

	public EventProcessor(final ProcessorChain chain) {
		super(chain);
	}

	public abstract void processEvents(EventPacketContainer container);

	public abstract void annotateEvents(EventPacketContainer container);

	@Override
	public final void run() {
		while (!Thread.currentThread().isInterrupted()) {
			if (workQueue.drainTo(toProcess) == 0) {
				// No elements, retry.
				continue;
			}

			for (final EventPacketContainer container : toProcess) {
				processEvents(container);
				annotateEvents(container);
			}
		}
	}
}
