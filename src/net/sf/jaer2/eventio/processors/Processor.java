package net.sf.jaer2.eventio.processors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javafx.scene.control.Label;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Processor implements Runnable {
	public enum ProcessorTypes {
		INPUT_PROCESSOR("Input"),
		OUTPUT_PROCESSOR("Output"),
		EVENT_PROCESSOR("Event");

		private final String str;

		private ProcessorTypes(final String s) {
			str = s;
		}

		@Override
		public String toString() {
			return str;
		}
	}

	protected final static Logger logger = LoggerFactory.getLogger(Processor.class);

	protected final int processorId;
	protected final String processorName;

	private final ProcessorChain parentChain;
	protected Processor prevProcessor;
	protected Processor nextProcessor;

	/**
	 * Processor type management.
	 *
	 * compatibleInputTypes defines which types of events this Processor can
	 * work on.
	 * inputTypes defines which types of events this Processor will work on,
	 * based on user configuration.
	 * outputTypes defines which types of events this Processor can output,
	 * based upon the Processor itself and all previous inputs before it.
	 */
	private final Set<Class<? extends Event>> compatibleInputTypes = new HashSet<>(4);
	private final Set<Class<? extends Event>> additionalOutputTypes = new HashSet<>(4);

	private final Set<ImmutablePair<Class<? extends Event>, Integer>> inputStreams = new HashSet<>(4);
	private final Set<ImmutablePair<Class<? extends Event>, Integer>> selectedInputStreams = new HashSet<>(4);
	private final Set<ImmutablePair<Class<? extends Event>, Integer>> outputStreams = new HashSet<>(4);

	protected final BlockingQueue<EventPacketContainer> workQueue = new ArrayBlockingQueue<>(16);
	protected final ArrayList<EventPacketContainer> toProcess = new ArrayList<>(32);

	protected final HBox rootLayout = new HBox(20);
	protected final HBox rootConfigLayout = new HBox(20);

	public Processor(final ProcessorChain chain) {
		parentChain = chain;

		processorId = parentChain.getNextAvailableProcessorID();
		processorName = getClass().getSimpleName();

		// Fill in the type information from the inheriting classes.
		setCompatibleInputTypes(compatibleInputTypes);
		setAdditionalOutputTypes(additionalOutputTypes);

		// Build GUIs for this processor.
		buildGUI();
		buildConfigGUI();
	}

	public int getProcessorId() {
		return processorId;
	}

	public String getProcessorName() {
		return processorName;
	}

	public ProcessorChain getParentChain() {
		return parentChain;
	}

	public Processor getPrevProcessor() {
		return prevProcessor;
	}

	public void setPrevProcessor(final Processor prev) {
		prevProcessor = prev;

		rebuildStreamSets();
	}

	public Processor getNextProcessor() {
		return nextProcessor;
	}

	public void setNextProcessor(final Processor next) {
		nextProcessor = next;
	}

	protected abstract void setCompatibleInputTypes(Set<Class<? extends Event>> inputs);

	protected abstract void setAdditionalOutputTypes(Set<Class<? extends Event>> outputs);

	private void rebuildInputStreams() {
		inputStreams.clear();

		// Add all outputs from previous Processor, filtering incompatible
		// types out.
		if (prevProcessor != null) {
			for (final ImmutablePair<Class<? extends Event>, Integer> stream : prevProcessor.getAllOutputStreams()) {
				if (compatibleInputTypes.contains(stream.left)) {
					inputStreams.add(stream);
				}
			}
		}

		// Clear out any elements that now can't be selected anymore.
		selectedInputStreams.retainAll(inputStreams);
	}

	public Set<ImmutablePair<Class<? extends Event>, Integer>> getAllInputStreams() {
		return inputStreams;
	}

	private void rebuildOutputStreams() {
		outputStreams.clear();

		// Add all outputs from previous Processor, as well as outputs produced
		// by the current Processor.
		if (prevProcessor != null) {
			outputStreams.addAll(prevProcessor.getAllOutputStreams());
		}

		for (final Class<? extends Event> outputType : additionalOutputTypes) {
			outputStreams.add(new ImmutablePair<Class<? extends Event>, Integer>(outputType, processorId));
		}
	}

	public Set<ImmutablePair<Class<? extends Event>, Integer>> getAllOutputStreams() {
		return outputStreams;
	}

	public void rebuildStreamSets() {
		rebuildInputStreams();
		rebuildOutputStreams();
	}

	public void addToSelectedInputStreams(final ImmutablePair<Class<? extends Event>, Integer> stream) {
		selectedInputStreams.add(stream);
	}

	public void removeFromSelectedInputStreams(final ImmutablePair<Class<? extends Event>, Integer> stream) {
		selectedInputStreams.remove(stream);
	}

	public Set<ImmutablePair<Class<? extends Event>, Integer>> getAllSelectedInputStreams() {
		// This is strictly a subset of inputStreams.
		return Collections.unmodifiableSet(selectedInputStreams);
	}

	public void clearSelectedInputStreams() {
		selectedInputStreams.clear();
	}

	public boolean processContainer(final EventPacketContainer container) {
		for (final ImmutablePair<Class<? extends Event>, Integer> relevant : selectedInputStreams) {
			if (container.getPacket(relevant.left, relevant.right) != null) {
				return true;
			}
		}

		return false;
	}

	public final void add(final EventPacketContainer container) {
		workQueue.add(container);
	}

	public final void addAll(final Collection<EventPacketContainer> containers) {
		workQueue.addAll(containers);
	}

	public Pane getGUI() {
		return rootLayout;
	}

	public Pane getConfigGUI() {
		return rootConfigLayout;
	}

	protected void buildGUI() {
		final VBox box = new VBox();
		box.setBorder(new Border(new BorderStroke(null, BorderStrokeStyle.SOLID, null, BorderWidths.FULL)));
		rootLayout.getChildren().add(box);

		final Label name = new Label(toString());
		box.getChildren().add(name);

	}

	protected void buildConfigGUI() {

	}

	@Override
	public String toString() {
		return String.format("%s - ID %d", processorName, processorId);
	}
}
