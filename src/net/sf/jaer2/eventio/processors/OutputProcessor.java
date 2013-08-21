package net.sf.jaer2.eventio.processors;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;
import net.sf.jaer2.eventio.sinks.Sink;
import net.sf.jaer2.util.GUISupport;

public final class OutputProcessor extends Processor {
	private final BlockingQueue<EventPacketContainer> outputQueue = new ArrayBlockingQueue<>(32);

	private final ObjectProperty<Sink> connectedSink = new SimpleObjectProperty<>();

	public OutputProcessor(final ProcessorChain chain) {
		super(chain);

		buildConfigGUI();
		buildGUI();
	}

	public Sink getConnectedSink() {
		return connectedSink.get();
	}

	public void setConnectedSink(final Sink sink) {
		GUISupport.runOnJavaFXThread(new Runnable() {
			@Override
			public void run() {
				connectedSink.set(sink);

				Processor.logger.debug("ConnectedSink set to: {}.", sink);
			}
		});
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
		return (getConnectedSink() != null);
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
