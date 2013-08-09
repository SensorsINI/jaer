package net.sf.jaer2.eventio.processors;

import net.sf.jaer2.eventio.ProcessorChain;

public final class InputProcessor extends Processor {
	private boolean firstInput;

	public InputProcessor(final ProcessorChain chain, final boolean firstInput) {
		super(chain);

		this.firstInput = firstInput;
	}

	public boolean isFirstInput() {
		return firstInput;
	}

	public void setFirstInput(final boolean firstInput) {
		this.firstInput = firstInput;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub

	}
}
