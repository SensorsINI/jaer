package net.sf.jaer2.eventio.processors;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;

public final class InputProcessor extends Processor {
	protected final BlockingQueue<EventPacketContainer> inputQueue = new ArrayBlockingQueue<>(16);

	private boolean firstInput;

	public InputProcessor(final ProcessorChain chain, final boolean isFirstInput) {
		super(chain);

		firstInput = isFirstInput;
	}

	public boolean isFirstInput() {
		return firstInput;
	}

	public void setFirstInput(final boolean isFirstInput) {
		firstInput = isFirstInput;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
	}
}
