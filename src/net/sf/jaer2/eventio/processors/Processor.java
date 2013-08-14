package net.sf.jaer2.eventio.processors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Insets;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;
import net.sf.jaer2.util.GUISupport;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Processor implements Runnable {
	/**
	 * Enumeration containing the available processor types and their string
	 * representations for printing.
	 */
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

	/** Local logger for log messages. */
	protected final static Logger logger = LoggerFactory.getLogger(Processor.class);

	/** Processor identification ID. */
	protected final int processorId;
	/** Processor identification Name. */
	protected final String processorName;

	/** Chain this processor belongs to. */
	protected final ProcessorChain parentChain;
	/** Previous processor in the ordered chain. */
	protected Processor prevProcessor;
	/** Next processor in the ordered chain. */
	protected Processor nextProcessor;

	/**
	 * Processor type management.
	 *
	 * inputStreams defines which types of events this Processor can
	 * work on.
	 * selectedInputStreams defines which types of events this Processor will
	 * work on,
	 * based on user configuration.
	 * outputStreams defines which types of events this Processor can output,
	 * based upon the Processor itself and all previous inputs before it.
	 */
	private final ObservableSet<Class<? extends Event>> compatibleInputTypes = FXCollections
		.observableSet(new HashSet<Class<? extends Event>>(4));
	private final ObservableSet<Class<? extends Event>> additionalOutputTypes = FXCollections
		.observableSet(new HashSet<Class<? extends Event>>(4));

	private final ObservableSet<ImmutablePair<Class<? extends Event>, Integer>> inputStreams = FXCollections
		.observableSet(new HashSet<ImmutablePair<Class<? extends Event>, Integer>>(4));
	private final ObservableSet<ImmutablePair<Class<? extends Event>, Integer>> selectedInputStreams = FXCollections
		.observableSet(new HashSet<ImmutablePair<Class<? extends Event>, Integer>>(4));
	private final ObservableSet<ImmutablePair<Class<? extends Event>, Integer>> outputStreams = FXCollections
		.observableSet(new HashSet<ImmutablePair<Class<? extends Event>, Integer>>(4));

	protected final BlockingQueue<EventPacketContainer> workQueue = new ArrayBlockingQueue<>(16);
	protected final ArrayList<EventPacketContainer> toProcess = new ArrayList<>(32);

	/** Main GUI layout. */
	protected final VBox rootLayout = new VBox(10);

	/** Configuration GUI layout. */
	protected final VBox rootConfigLayout = new VBox(10);
	/** Configuration GUI: tasks to execute on success. */
	protected final List<Runnable> rootConfigTasks = new ArrayList<>(2);

	public Processor(final ProcessorChain chain) {
		parentChain = chain;

		processorId = parentChain.getNextAvailableProcessorID();
		processorName = getClass().getSimpleName();

		// Fill in the type information from the extending sub-classes.
		setCompatibleInputTypes(compatibleInputTypes);
		setAdditionalOutputTypes(additionalOutputTypes);

		// Build GUIs for this processor.
		buildGUI();
		buildConfigGUI();

		Processor.logger.debug("Created Processor {}.", this);
	}

	/**
	 * Return the ID number of this processor.
	 *
	 * @return processor ID number.
	 */
	public int getProcessorId() {
		return processorId;
	}

	/**
	 * Return the name of this processor.
	 *
	 * @return processor name.
	 */
	public String getProcessorName() {
		return processorName;
	}

	/**
	 * Return the chain this processor belongs to.
	 *
	 * @return parent chain.
	 */
	public ProcessorChain getParentChain() {
		return parentChain;
	}

	public Processor getPrevProcessor() {
		return prevProcessor;
	}

	public void setPrevProcessor(final Processor prev) {
		prevProcessor = prev;

		// These depends on the previous processor!
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
		return Collections.unmodifiableSet(inputStreams);
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
		return Collections.unmodifiableSet(outputStreams);
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

	/**
	 * Check if a container is to be processed by this processor.
	 * This is the case if it contains <Type, Source> combinations that are
	 * relevant, based upon the configuration done by the user.
	 *
	 * @param container
	 *            the EventPacket container to check.
	 * @return whether relevant EventPackets are present or not.
	 */
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

	/**
	 * Get the graphical layout corresponding to this class, so that it can be
	 * displayed somewhere by adding it to a Scene.
	 *
	 * @return GUI reference to display.
	 */
	public Pane getGUI() {
		return rootLayout;
	}

	/**
	 * Create the base GUI elements and add them to the rootLayout.
	 */
	protected void buildGUI() {
		rootLayout.setPadding(new Insets(5));
		rootLayout.setStyle("-fx-border-style: solid; -fx-border-width: 1; -fx-border-color: black");

		GUISupport.addLabel(rootLayout, toString(), null, null, null);
	}

	/**
	 * Get the graphical layout for the configuration screen corresponding to
	 * this class, so that it can be
	 * displayed somewhere by adding it to a Scene.
	 *
	 * @return GUI reference to display.
	 */
	public Pane getConfigGUI() {
		return rootConfigLayout;
	}

	/**
	 * Create the base GUI elements for the configuration screen and add them to
	 * the rootConfigLayout.
	 */
	protected void buildConfigGUI() {
		// TODO: follows.
	}

	@Override
	public String toString() {
		return String.format("%s - ID %d", processorName, processorId);
	}
}
