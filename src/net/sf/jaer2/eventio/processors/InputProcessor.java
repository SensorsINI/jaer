package net.sf.jaer2.eventio.processors;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import net.sf.jaer2.chips.Chip;
import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;
import net.sf.jaer2.eventio.sources.Source;

public final class InputProcessor extends Processor {
	private final BlockingQueue<EventPacketContainer> inputQueue = new ArrayBlockingQueue<>(16);

	private Source connectedSource;
	private Chip interpreterChip;

	public InputProcessor(final ProcessorChain chain) {
		super(chain);
	}

	public Source getConnectedSource() {
		return connectedSource;
	}

	public void setConnectedSource(final Source source) {
		connectedSource = source;
	}

	public Chip getInterpreterChip() {
		return interpreterChip;
	}

	public void setInterpreterChip(final Chip chip) {
		interpreterChip = chip;
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			if ((workQueue.drainTo(toProcess) == 0) && (inputQueue.drainTo(toProcess) == 0)) {
				// No elements, retry.
				continue;
			}

			if (nextProcessor != null) {
				nextProcessor.addAll(toProcess);
			}

			toProcess.clear();
		}
	}

	@SuppressWarnings("unused")
	@Override
	protected void setCompatibleInputTypes(final Set<Class<? extends Event>> inputs) {
		// Empty, doesn't process any inputs at all.
	}

	@Override
	protected void setAdditionalOutputTypes(final Set<Class<? extends Event>> outputs) {
		if (interpreterChip != null) {
			outputs.addAll(interpreterChip.getEventTypes());
		}
	}
}
