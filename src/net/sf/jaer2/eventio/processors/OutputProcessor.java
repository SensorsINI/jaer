package net.sf.jaer2.eventio.processors;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;
import net.sf.jaer2.eventio.sinks.Sink;
import net.sf.jaer2.util.Reflections;

public final class OutputProcessor extends Processor {
	private static final long serialVersionUID = -1037683376951856401L;

	transient private final BlockingQueue<EventPacketContainer> outputQueue = new ArrayBlockingQueue<>(32);

	private Sink connectedSink;

	public OutputProcessor(final ProcessorChain chain) {
		super(chain);

		CommonConstructor();
	}

	private void CommonConstructor() {
		// Build GUIs for this processor, always in this order!
		buildConfigGUI();
		buildGUI();
	}

	private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		// Restore transient fields.
		Reflections.setFinalField(this, "outputQueue", new ArrayBlockingQueue<EventPacketContainer>(32));

		// Do construction.
		CommonConstructor();
	}

	public Sink getConnectedSink() {
		return connectedSink;
	}

	public void setConnectedSink(final Sink sink) {
		connectedSink = sink;

		Processor.logger.debug("ConnectedSink set to: {}.", sink);
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

	public boolean readyToRun() {
		return (connectedSink != null);
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			if (workQueue.drainTo(workToProcess) == 0) {
				// No elements, retry.
				continue;
			}

			for (final EventPacketContainer container : workToProcess) {
				// Check that this container is interesting for this processor.
				if (processContainer(container)) {
					outputQueue.add(container);
				}

				if (getNextProcessor() != null) {
					getNextProcessor().add(container);
				}
			}

			workToProcess.clear();
		}
	}

	public EventPacketContainer getFromOutput() {
		return outputQueue.poll();
	}

	public void getAllFromOutput(final Collection<EventPacketContainer> eventPacketContainers) {
		outputQueue.drainTo(eventPacketContainers);
	}

	private void buildGUI() {

	}

	private void buildConfigGUI() {

	}
}
