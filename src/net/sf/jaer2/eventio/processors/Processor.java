package net.sf.jaer2.eventio.processors;

import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;

public abstract class Processor implements Runnable {
	protected final ProcessorChain parentChain;
	protected final int processorId;
	protected final String processorName;

	protected final BlockingQueue<EventPacketContainer> inputQueue = new ArrayBlockingQueue<>(16);

	public Processor(final ProcessorChain chain) {
		parentChain = chain;
		processorId = parentChain.getNextAvailableSourceID();
		processorName = getClass().getSimpleName();
	}

	public int getProcessorId() {
		return processorId;
	}

	public String getProcessorName() {
		return processorName;
	}

	public final void add(final EventPacketContainer container) {
		inputQueue.add(container);
	}

	public final void addAll(final Collection<EventPacketContainer> containers) {
		inputQueue.addAll(containers);
	}

	@Override
	public String toString() {
		return String.format("%s - ID %d", processorName, processorId);
	}
}
