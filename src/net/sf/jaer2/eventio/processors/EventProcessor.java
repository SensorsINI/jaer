package net.sf.jaer2.eventio.processors;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;

public abstract class EventProcessor extends Processor {
	public EventProcessor(final ProcessorChain chain, final Processor prev, final Processor next) {
		super(chain, next, prev);
	}

	public abstract void processEvents(EventPacketContainer container);

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			if (workQueue.drainTo(toProcess) == 0) {
				// No elements, retry.
				continue;
			}

			for (final EventPacketContainer container : toProcess) {
				// Check that this container is interesting for this processor.
				if (processContainer(container)) {
					processEvents(container);
				}

				nextProcessor.add(container);
			}

			toProcess.clear();
		}
	}
}
