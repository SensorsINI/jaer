package net.sf.jaer2.eventio.processors;

import net.sf.jaer2.eventio.ProcessorChain;

public final class OutputProcessor extends Processor {
	private boolean lastOutput;

	public OutputProcessor(final ProcessorChain chain, final boolean lastOutput) {
		super(chain);

		this.lastOutput = lastOutput;
	}

	public boolean isLastOutput() {
		return lastOutput;
	}

	public void setLastOutput(final boolean lastOutput) {
		this.lastOutput = lastOutput;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub

	}
}
