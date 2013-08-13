package net.sf.jaer2.eventio.processors;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;
import net.sf.jaer2.eventio.sinks.Sink;

public final class OutputProcessor extends Processor {
	private final BlockingQueue<EventPacketContainer> outputQueue = new ArrayBlockingQueue<>(16);

	private Sink connectedSink;

	public OutputProcessor(final ProcessorChain chain) {
		super(chain);
	}

	public Sink getConnectedSink() {
		return connectedSink;
	}

	public void setConnectedSink(final Sink sink) {
		connectedSink = sink;
	}

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
					outputQueue.add(container);
				}

				if (nextProcessor != null) {
					nextProcessor.add(container);
				}
			}

			toProcess.clear();
		}
	}

	@Override
	protected void setCompatibleInputTypes(final Set<Class<? extends Event>> inputs) {
		// Accepts all inputs.
		inputs.add(Event.class);
	}

	@SuppressWarnings("unused")
	@Override
	protected void setAdditionalOutputTypes(final Set<Class<? extends Event>> outputs) {
		// Empty, doesn't add any new output types to the system.
	}
}
