package net.sf.jaer2.eventio;

import java.util.LinkedList;
import java.util.List;

import net.sf.jaer2.eventio.processors.EventProcessor;
import net.sf.jaer2.eventio.processors.InputProcessor;
import net.sf.jaer2.eventio.processors.OutputProcessor;
import net.sf.jaer2.eventio.processors.Processor;

public class ProcessorChain {
	private List<InputProcessor> inputProcessors = new LinkedList<>();
	private List<OutputProcessor> outputProcessors = new LinkedList<>();
	private List<EventProcessor> eventProcessors = new LinkedList<>();
	private List<Processor> processors = new LinkedList<>();

}
