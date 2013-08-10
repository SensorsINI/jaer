package net.sf.jaer2.eventio;

import java.util.LinkedList;
import java.util.List;

import net.sf.jaer2.eventio.processors.EventProcessor;
import net.sf.jaer2.eventio.processors.InputProcessor;
import net.sf.jaer2.eventio.processors.OutputProcessor;
import net.sf.jaer2.eventio.processors.Processor;

public final class ProcessorChain {
	private final List<InputProcessor> inputProcessors = new LinkedList<>();
	private final List<OutputProcessor> outputProcessors = new LinkedList<>();
	private final List<EventProcessor> eventProcessors = new LinkedList<>();
	private final List<Processor> processors = new LinkedList<>();

	private int sourceIdCounter = 1;

	/**
	 * Get unique source ID for Processors in this chain.
	 * Always increases by one, no duplicates.
	 * Starts at 1.
	 *
	 * @return Next unique source ID for Processor identification.
	 */
	public int getNextAvailableSourceID() {
		return sourceIdCounter++;
	}
}
