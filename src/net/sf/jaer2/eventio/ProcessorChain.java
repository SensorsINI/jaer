package net.sf.jaer2.eventio;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.eventio.processors.EventProcessor;
import net.sf.jaer2.eventio.processors.InputProcessor;
import net.sf.jaer2.eventio.processors.OutputProcessor;
import net.sf.jaer2.eventio.processors.Processor;
import net.sf.jaer2.eventio.processors.Processor.ProcessorTypes;
import net.sf.jaer2.util.GUISupport;
import net.sf.jaer2.util.Reflections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessorChain {
	/** Local logger for log messages. */
	private final static Logger logger = LoggerFactory.getLogger(ProcessorChain.class);

	/** List of classes extending EventProcessor. */
	private final static Set<Class<? extends EventProcessor>> eventProcessorTypes;
	static {
		// Generate the list of classes only once. */
		eventProcessorTypes = Reflections.getSubTypes(EventProcessor.class);
	}

	/** Chain identification ID. */
	private final int chainId;
	/** Chain identification Name. */
	private final String chainName;

	/** Network this chain belongs to. */
	private final ProcessorNetwork parentNetwork;

	/**
	 * List of all processors in this chain. Index 0 contains a null element as
	 * place-holder to enable the insertion of new Processors at the head of the
	 * list (selection in ComboBox) and link it correctly.
	 */
	private final ObservableList<Processor> processors = FXCollections.observableArrayList((Processor) null);

	/** Unique ID counter for processor identification. */
	private int processorIdCounter = 1;

	/** Main GUI layout - Horizontal Box. */
	private final HBox rootLayout = new HBox(10);

	/** Configuration GUI layout - Vertical Box. */
	private final VBox rootConfigLayout = new VBox(10);
	/** Configuration GUI: tasks to execute on success. */
	private final List<Runnable> rootConfigTasks = new ArrayList<>(2);

	public ProcessorChain(final ProcessorNetwork network) {
		parentNetwork = network;

		chainId = parentNetwork.getNextAvailableChainID();
		chainName = getClass().getSimpleName();

		// Build GUIs for this processor.
		buildGUI();
		buildConfigGUI();

		ProcessorChain.logger.debug("Created ProcessorChain {}.", this);
	}

	/**
	 * Get unique ID for processors in this chain.
	 * Always increases by one, no duplicates.
	 * Starts at 1.
	 *
	 * @return Next unique ID for processor identification.
	 */
	public int getNextAvailableProcessorID() {
		return processorIdCounter++;
	}

	/**
	 * Return the ID number of this chain.
	 *
	 * @return chain ID number.
	 */
	public int getChainId() {
		return chainId;
	}

	/**
	 * Return the name of this chain.
	 *
	 * @return chain name.
	 */
	public String getChainName() {
		return chainName;
	}

	/**
	 * Return the network this chain belongs to.
	 *
	 * @return parent network.
	 */
	public ProcessorNetwork getParentNetwork() {
		return parentNetwork;
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
	private void buildGUI() {
		rootLayout.setPadding(new Insets(5));
		rootLayout.setStyle("-fx-border-style: solid; -fx-border-width: 2; -fx-border-color: black");

		final VBox controlBox = new VBox(5);
		rootLayout.getChildren().add(controlBox);

		// First, add the name of the chain itself.
		GUISupport.addLabel(controlBox, toString(), null, null, null);

		// Then, add the buttons to delete ProcessorChains and add new
		// Processors.
		GUISupport.addButtonWithMouseClickedHandler(controlBox, "Delete Chain", "/icons/Remove.png",
			new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					parentNetwork.removeChain(ProcessorChain.this);
				}
			});

		GUISupport.addButtonWithMouseClickedHandler(controlBox, "New Processor", "/icons/Add.png",
			new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					GUISupport.showDialog("New Processor Configuration", rootConfigLayout, rootConfigTasks);
				}
			});
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
	private void buildConfigGUI() {
		// Create EventProcessor type chooser box. It will be added later on.
		final ComboBox<Class<? extends EventProcessor>> eventProcessorTypeChooser = GUISupport.addComboBox(null,
			ProcessorChain.eventProcessorTypes, 0);
		final HBox eventProcessorTypeChooserBox = GUISupport.addLabelWithControlHorizontal(null, "Event Processor: ",
			"Select the Event Processor you want to use.", eventProcessorTypeChooser);

		// Create Processor type chooser box, based on the ProcessorTypes enum.
		final ComboBox<ProcessorTypes> processorTypeChooser = GUISupport.addComboBox(null,
			EnumSet.allOf(ProcessorTypes.class), 0);
		GUISupport.addLabelWithControlHorizontal(rootConfigLayout, "Processor Type: ",
			"Select the processor type you want to create.", processorTypeChooser);

		// Toggle the EventProcessor type chooser box depending on what type of
		// Processor the user has selected at the moment.
		processorTypeChooser.valueProperty().addListener(new ChangeListener<ProcessorTypes>() {
			private boolean eventProcessorTypeChooserVisible = false;

			@SuppressWarnings("unused")
			@Override
			public void changed(final ObservableValue<? extends ProcessorTypes> observable,
				final ProcessorTypes oldValue, final ProcessorTypes newValue) {
				// Add or remove EventProcessor type chooser box, based on what
				// the user selected as a Processor type.
				if ((newValue == ProcessorTypes.EVENT_PROCESSOR) && (!eventProcessorTypeChooserVisible)) {
					rootConfigLayout.getChildren().add(eventProcessorTypeChooserBox);
					eventProcessorTypeChooserVisible = true;
				}
				else if ((newValue != ProcessorTypes.EVENT_PROCESSOR) && (eventProcessorTypeChooserVisible)) {
					rootConfigLayout.getChildren().remove(eventProcessorTypeChooserBox);
					eventProcessorTypeChooserVisible = false;
				}
			}
		});

		// Create Processor position chooser box, based on the currently
		// existing processors.
		final ComboBox<Processor> processorPositionChooser = GUISupport.addComboBox(null, processors, 0);
		GUISupport.addLabelWithControlHorizontal(rootConfigLayout, "After Processor: ",
			"Place this new Processor right after the selected one.", processorPositionChooser);

		// Bind the shown items to the main processors list, for auto-updating.
		processorPositionChooser.itemsProperty().set(processors);

		// Add task to be enacted, based on above GUI configuration settings.
		rootConfigTasks.add(new Runnable() {
			@Override
			public void run() {
				// Add a new Processor of the wanted type at the right position.
				final int position = processors.indexOf(processorPositionChooser.getValue()) + 1;

				switch (processorTypeChooser.getValue()) {
					case INPUT_PROCESSOR:
						addInputProcessor(position);
						break;

					case OUTPUT_PROCESSOR:
						addOutputProcessor(position);
						break;

					case EVENT_PROCESSOR:
						addEventProcessor(position, eventProcessorTypeChooser.getValue());
						break;

					default:
						break;
				}
			}
		});
	}

	/**
	 * Links a processor correctly to its neighbors on creation.
	 *
	 * @param processor
	 *            processor to link.
	 */
	private void linkProcessor(final Processor processor) {
		final int position = processors.indexOf(processor);

		// Set all internal processor links.
		Processor prevProcessor, nextProcessor;

		try {
			prevProcessor = processors.get(position - 1);
		}
		catch (final IndexOutOfBoundsException e) {
			prevProcessor = null;
		}

		try {
			nextProcessor = processors.get(position + 1);
		}
		catch (final IndexOutOfBoundsException e) {
			nextProcessor = null;
		}

		if (prevProcessor != null) {
			prevProcessor.setNextProcessor(processor);
		}

		if (nextProcessor != null) {
			nextProcessor.setPrevProcessor(processor);
		}

		processor.setPrevProcessor(prevProcessor);
		processor.setNextProcessor(nextProcessor);
	}

	/**
	 * Unlinks a processor correctly from its neighbors on deletion.
	 *
	 * @param processor
	 *            processor to unlink.
	 */
	private void unlinkProcessor(final Processor processor) {
		final int position = processors.indexOf(processor);

		// Unset all internal processor links.
		Processor prevProcessor, nextProcessor;

		try {
			prevProcessor = processors.get(position - 1);
		}
		catch (final IndexOutOfBoundsException e) {
			prevProcessor = null;
		}

		try {
			nextProcessor = processors.get(position + 1);
		}
		catch (final IndexOutOfBoundsException e) {
			nextProcessor = null;
		}

		if (prevProcessor != null) {
			prevProcessor.setNextProcessor(nextProcessor);
		}

		if (nextProcessor != null) {
			nextProcessor.setPrevProcessor(prevProcessor);
		}

		processor.setPrevProcessor(null);
		processor.setNextProcessor(null);
	}

	private void updateAllStreams() {
		for (final Processor proc : processors) {
			if (proc != null) {
				proc.rebuildStreamSets();
			}
		}
	}

	/**
	 * Create a new input processor and add it to the GUI at the specified
	 * position in the chain.
	 *
	 * @param position
	 *            index at which to add the new processor. Already includes +1
	 *            to compensate for the place-holder elements (null in
	 *            processors, controlBox in rootLayout).
	 *
	 * @return the new processor.
	 */
	public InputProcessor addInputProcessor(final int position) {
		final InputProcessor processor = new InputProcessor(this);

		// Position already compensates for place-holder elements.
		processors.add(position, processor);
		rootLayout.getChildren().add(position, processor.getGUI());

		linkProcessor(processor);
		updateAllStreams();

		ProcessorChain.logger.debug("Added InputProcessor {}.", processor);

		return processor;
	}

	/**
	 * Deletes the specified input processor and removes it from the GUI.
	 *
	 * @param processor
	 *            processor to remove.
	 */
	public void removeInputProcessor(final InputProcessor processor) {
		unlinkProcessor(processor);

		rootLayout.getChildren().remove(processor.getGUI());
		processors.remove(processor);

		ProcessorChain.logger.debug("Removed InputProcessor {}.", processor);
	}

	/**
	 * Create a new output processor and add it to the GUI at the specified
	 * position in the chain.
	 *
	 * @param position
	 *            index at which to add the new processor. Already includes +1
	 *            to compensate for the place-holder elements (null in
	 *            processors, controlBox in rootLayout).
	 *
	 * @return the new processor.
	 */
	public OutputProcessor addOutputProcessor(final int position) {
		final OutputProcessor processor = new OutputProcessor(this);

		// Position already compensates for place-holder elements.
		processors.add(position, processor);
		rootLayout.getChildren().add(position, processor.getGUI());

		linkProcessor(processor);
		updateAllStreams();

		ProcessorChain.logger.debug("Added OutputProcessor {}.", processor);

		return processor;
	}

	/**
	 * Deletes the specified output processor and removes it from the GUI.
	 *
	 * @param processor
	 *            processor to remove.
	 */
	public void removeOutputProcessor(final OutputProcessor processor) {
		unlinkProcessor(processor);

		rootLayout.getChildren().remove(processor.getGUI());
		processors.remove(processor);

		ProcessorChain.logger.debug("Removed OutputProcessor {}.", processor);
	}

	/**
	 * Create a new event processor and add it to the GUI at the specified
	 * position in the chain.
	 *
	 * @param position
	 *            index at which to add the new processor. Already includes +1
	 *            to compensate for the place-holder elements (null in
	 *            processors, controlBox in rootLayout).
	 * @param clazz
	 *            concrete type of EventProcessor to instantiate.
	 *
	 * @return the new processor.
	 */
	public EventProcessor addEventProcessor(final int position, final Class<? extends EventProcessor> clazz) {
		Constructor<? extends EventProcessor> constr = null;

		try {
			// Try to find a compatible constructor for the given concrete type.
			constr = clazz.getConstructor(ProcessorChain.class);

			if (constr == null) {
				throw new NullPointerException("constr is null in addEventProcessor()");
			}
		}
		catch (NoSuchMethodException | SecurityException | NullPointerException e) {
			GUISupport.showDialogException(e);
			return null;
		}

		EventProcessor processor = null;

		try {
			// Try to create a new instance of the given concrete type, using
			// the constructor found above.
			processor = constr.newInstance(this);

			if (processor == null) {
				throw new NullPointerException("processor is null in addEventProcessor()");
			}
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
			| NullPointerException e) {
			GUISupport.showDialogException(e);
			return null;
		}

		// Position already compensates for place-holder elements.
		processors.add(position, processor);
		rootLayout.getChildren().add(position, processor.getGUI());

		linkProcessor(processor);
		updateAllStreams();

		ProcessorChain.logger.debug("Added EventProcessor {}.", processor);

		return processor;
	}

	/**
	 * Deletes the specified event processor and removes it from the GUI.
	 *
	 * @param processor
	 *            processor to remove.
	 */
	public void removeEventProcessor(final EventProcessor processor) {
		unlinkProcessor(processor);

		rootLayout.getChildren().remove(processor.getGUI());
		processors.remove(processor);

		ProcessorChain.logger.debug("Removed EventProcessor {}.", processor);
	}

	@Override
	public String toString() {
		return String.format("%s - ID %d", chainName, chainId);
	}
}
