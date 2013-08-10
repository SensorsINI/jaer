package net.sf.jaer2.eventio.processors;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;

public final class OutputProcessor extends Processor {
	protected final BlockingQueue<EventPacketContainer> outputQueue = new ArrayBlockingQueue<>(16);

	private boolean lastOutput;

	public OutputProcessor(final ProcessorChain chain, final boolean isLastOutput) {
		super(chain);

		lastOutput = isLastOutput;
	}

	public boolean isLastOutput() {
		return lastOutput;
	}

	public void setLastOutput(final boolean isLastOutput) {
		lastOutput = isLastOutput;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
	}
}
