package net.sf.jaer2.eventio.processors;

import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;

public abstract class EventProcessor extends Processor {
	private static final long serialVersionUID = 3796706196373847072L;

	public EventProcessor() {
		super();
	}

	protected abstract void processEvents(EventPacketContainer container);

	@Override
	public final void run() {
		while (!Thread.currentThread().isInterrupted()) {
			if (workQueue.drainTo(workToProcess) == 0) {
				// No elements, retry.
				continue;
			}

			for (final EventPacketContainer container : workToProcess) {
				// Check that this container is interesting for this processor.
				if (processContainer(container)) {
					processEvents(container);

					// Annotation support.
					if (this instanceof AnnotatedProcessor) {
						container.addToAnnotateDataSets(((AnnotatedProcessor) this).prepareAnnotateEvents(container));
					}
				}

				getNextProcessor().add(container);
			}

			workToProcess.clear();
		}
	}
}
