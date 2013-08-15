package net.sf.jaer2.eventio.processors;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;

public abstract class EventProcessor extends Processor {
	public EventProcessor(final ProcessorChain chain) {
		super(chain);
	}

	protected abstract void processEvents(EventPacketContainer container);

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

					// Annotation support.
					if (this instanceof AnnotatedProcessor) {
						container.addToAnnotateDataSets(((AnnotatedProcessor)this).prepareAnnotateEvents(container));
					}
				}

				if (nextProcessor != null) {
					nextProcessor.add(container);
				}
			}

			toProcess.clear();
		}
	}
}
