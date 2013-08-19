package net.sf.jaer2.eventio;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import net.sf.jaer2.chips.Chip;
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
		eventProcessorTypes = Reflections.getSubClasses(EventProcessor.class);
	}

	/** Chain identification ID. */
	private final int chainId;
	/** Chain identification Name. */
	private final String chainName;

	/** Network this chain belongs to. */
	private final ProcessorNetwork parentNetwork;

	/** List of all processors in this chain. */
	private final ObservableList<Processor> processors = FXCollections.observableArrayList();

	/** Unique ID counter for processor identification. */
	private int processorIdCounter = 1;

	/** Map Processor IDs to the corresponding Processor. */
	private final ConcurrentMap<Integer, Processor> idToProcessorMap = new ConcurrentHashMap<>();

	/** Commit Change Signal. */
	private final BooleanProperty changesToCommit = new SimpleBooleanProperty(false);

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
		buildConfigGUI();
		buildGUI();

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
		rootLayout.getStyleClass().add("border-box");

		final VBox controlBox = new VBox(5);
		rootLayout.getChildren().add(controlBox);

		// First, add the name of the chain itself.
		GUISupport.addLabel(controlBox, toString(), null, null, null);

		// Then, add the buttons to delete ProcessorChains and add new
		// Processors.
		GUISupport.addButtonWithMouseClickedHandler(controlBox, "Delete Chain", true, "/images/icons/Remove.png",
			new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					parentNetwork.removeChain(ProcessorChain.this);
				}
			});

		GUISupport.addButtonWithMouseClickedHandler(controlBox, "New Processor", true, "/images/icons/Add.png",
			new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					GUISupport.showDialog("New Processor Configuration", rootConfigLayout, rootConfigTasks);
				}
			});

		final Button commitButton = GUISupport.addButtonWithMouseClickedHandler(controlBox, "Commit Changes", true,
			"/images/icons/Clear Green Button.png", new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					commitAndRunChain();
				}
			});

		// Disable the Commit Changes button when there aren't any.
		commitButton.disableProperty().bind(changesToCommit.not());

		// Add listener to enable the button when there are structural changes.
		processors.addListener(new InvalidationListener() {
			@Override
			public void invalidated(@SuppressWarnings("unused") final Observable observable) {
				newStructuralChangesToCommit();
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
		// Create Processor type chooser box, based on the ProcessorTypes enum.
		final ComboBox<ProcessorTypes> processorTypeChooser = GUISupport.addComboBox(null,
			EnumSet.allOf(ProcessorTypes.class), 0);
		GUISupport.addLabelWithControlsHorizontal(rootConfigLayout, "Processor Type:",
			"Select the processor type you want to create.", processorTypeChooser);

		// Give the opportunity to add a Processor before all others (start).
		final CheckBox processorPositionAtBeginning = GUISupport.addCheckBox(rootConfigLayout, "At Beginning", true);

		// Create Processor position chooser box, based on the currently
		// existing processors.
		final ComboBox<Processor> processorPositionChooser = GUISupport.addComboBox(null, processors, -1);
		GUISupport.addLabelWithControlsHorizontal(rootConfigLayout, "OR After Processor:",
			"Place this new Processor right after the selected one.", processorPositionChooser);

		// Bind the shown items to the main processors list, for auto-updating.
		processorPositionChooser.setItems(processors);

		processorPositionChooser.getItems().addListener(new ListChangeListener<Processor>() {
			@Override
			public void onChanged(final Change<? extends Processor> change) {
				// Reset default selection on each change to the backing list.
				processorPositionChooser.getSelectionModel().select(0);

				// If the list is empty, ensure the CheckBox is ticked.
				if (change.getList().isEmpty()) {
					processorPositionAtBeginning.setSelected(true);
				}
			}
		});

		// Bind the CheckBox and ComboBox enabled status to each-other.
		processorPositionChooser.disableProperty().bind(processorPositionAtBeginning.selectedProperty());

		processorPositionAtBeginning.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@SuppressWarnings("unused")
			@Override
			public void changed(final ObservableValue<? extends Boolean> observable, final Boolean oldValue,
				final Boolean newValue) {
				// If the processor list is empty, it should not be possible to
				// deselect the CheckBox tick.
				if ((!newValue) && (processors.isEmpty())) {
					processorPositionAtBeginning.setSelected(true);
				}
			}
		});

		// Create EventProcessor type chooser box.
		final ComboBox<Class<? extends EventProcessor>> eventProcessorTypeChooser = GUISupport.addComboBox(null,
			ProcessorChain.eventProcessorTypes, 0);
		final HBox eventProcessorTypeChooserBox = GUISupport.addLabelWithControlsHorizontal(rootConfigLayout,
			"Event Processor:", "Select the Event Processor you want to use.", eventProcessorTypeChooser);

		// Toggle the EventProcessor type chooser box depending on what type of
		// Processor the user has selected at the moment.
		eventProcessorTypeChooserBox.visibleProperty().bind(
			processorTypeChooser.valueProperty().isEqualTo(ProcessorTypes.EVENT_PROCESSOR));
		eventProcessorTypeChooserBox.managedProperty().bind(
			processorTypeChooser.valueProperty().isEqualTo(ProcessorTypes.EVENT_PROCESSOR));

		// Add task to be enacted, based on above GUI configuration settings.
		rootConfigTasks.add(new Runnable() {
			@Override
			public void run() {
				// Add a new Processor of the wanted type at the right position.
				int position = 0;

				if (!processorPositionAtBeginning.isSelected()) {
					position = processors.indexOf(processorPositionChooser.getValue()) + 1;
				}

				switch (processorTypeChooser.getValue()) {
					case INPUT_PROCESSOR:
						final Processor inputProcessor = createProcessor(ProcessorTypes.INPUT_PROCESSOR, null);
						addProcessor(inputProcessor, position);
						break;

					case OUTPUT_PROCESSOR:
						final Processor outputProcessor = createProcessor(ProcessorTypes.OUTPUT_PROCESSOR, null);
						addProcessor(outputProcessor, position);
						break;

					case EVENT_PROCESSOR:
						final Processor eventProcessor = createProcessor(ProcessorTypes.EVENT_PROCESSOR,
							eventProcessorTypeChooser.getValue());
						addProcessor(eventProcessor, position);
						break;

					default:
						GUISupport.showDialogError("Unknown Processor type.");
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

		// Set links in such an order that the effects of the calls to
		// rebuildStreamSets(), inside setPrevProcessor(), are minimized.
		if (prevProcessor != null) {
			prevProcessor.setNextProcessor(processor);
		}

		processor.setPrevProcessor(prevProcessor);
		processor.setNextProcessor(nextProcessor);

		if (nextProcessor != null) {
			nextProcessor.setPrevProcessor(processor);
		}
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

		// Set links in such an order that the effects of the calls to
		// rebuildStreamSets(), inside setPrevProcessor(), are minimized.
		// This is achieved by first re-linking the processors that remain in
		// the chain and only afterwards disconnecting the one to be removed.
		if (prevProcessor != null) {
			prevProcessor.setNextProcessor(nextProcessor);
		}

		if (nextProcessor != null) {
			nextProcessor.setPrevProcessor(prevProcessor);
		}

		processor.setNextProcessor(null);
		processor.setPrevProcessor(null);
	}

	/**
	 * Create a new processor of the given type.
	 *
	 * @param type
	 *            the type of processor to create (Input, Output, Event).
	 * @param clazz
	 *            concrete type of EventProcessor to instantiate. Only use this
	 *            when creating EventProcessors! For Input or Output processors
	 *            just pass null.
	 *
	 * @return the new processor.
	 */
	private Processor createProcessor(final ProcessorTypes type, final Class<? extends EventProcessor> clazz) {
		// Create the new, specified Processor.
		Processor processor;

		switch (type) {
			case INPUT_PROCESSOR:
				processor = new InputProcessor(this);
				break;

			case OUTPUT_PROCESSOR:
				processor = new OutputProcessor(this);
				break;

			case EVENT_PROCESSOR:
				Constructor<? extends EventProcessor> constr = null;

				try {
					// Try to find a compatible constructor for the given
					// concrete type.
					constr = clazz.getConstructor(ProcessorChain.class);

					if (constr == null) {
						throw new NullPointerException("constr is null in addEventProcessor()");
					}
				}
				catch (NoSuchMethodException | SecurityException | NullPointerException e) {
					GUISupport.showDialogException(e);
					return null;
				}

				processor = null;

				try {
					// Try to create a new instance of the given concrete type,
					// using
					// the constructor found above.
					processor = constr.newInstance(this);

					if (processor == null) {
						throw new NullPointerException("processor is null in addEventProcessor()");
					}
				}
				catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NullPointerException e) {
					GUISupport.showDialogException(e);
					return null;
				}
				break;

			default:
				GUISupport.showDialogError("Unknown Processor type.");
				return null;
		}

		return processor;
	}

	/**
	 * Add the specified processor to the chain and GUI at the specified
	 * position inside the chain.
	 *
	 * @param processor
	 *            processor to add.
	 * @param position
	 *            index at which to add the new processor.
	 */
	public void addProcessor(final Processor processor, final int position) {
		GUISupport.runOnJavaFXThread(new Runnable() {
			@Override
			public void run() {
				processors.add(position, processor);
				// Add +1 to compensate for ControlBox element at start.
				rootLayout.getChildren().add(position + 1, processor.getGUI());

				linkProcessor(processor);

				ProcessorChain.logger.debug("Added Processor {}.", processor);
			}
		});
	}

	/**
	 * Removes the specified processor from the chain and GUI.
	 *
	 * @param processor
	 *            processor to remove.
	 */
	public void removeProcessor(final Processor processor) {
		GUISupport.runOnJavaFXThread(new Runnable() {
			@Override
			public void run() {
				unlinkProcessor(processor);

				rootLayout.getChildren().remove(processor.getGUI());
				processors.remove(processor);

				ProcessorChain.logger.debug("Removed Processor {}.", processor);
			}
		});
	}

	/**
	 * Call this if you or your Processor just caused structural changes in the
	 * chain. This means you changed the presence or position of Processors
	 * (such as Drag&Drop) or you changed the Input/Output types configuration
	 * in any way (such as in the Synchronizer when enabling new outputs).
	 */
	public void newStructuralChangesToCommit() {
		GUISupport.runOnJavaFXThread(new Runnable() {
			@Override
			public void run() {
				changesToCommit.set(true);
			}
		});
	}

	/**
	 * Verify the changes done to chain configuration and either display error
	 * messages or commit them to the running configuration. If none exists, or
	 * if new processors are to be added, start them as required.
	 */
	private void commitAndRunChain() {
		// Check if there are any changes signalled.
		if (!changesToCommit.get()) {
			GUISupport.showDialogError("No structural changes to commit!");
			return;
		}

		// Empty chain? Invalid!
		if (processors.isEmpty() || (processors.size() < 2)) {
			GUISupport.showDialogError("Empty chain, add at least one Input and one Output Processor!");
			return;
		}

		// Check for valid positions (the first element in the chain has to
		// always be an input, the last an output).
		if (!(processors.get(0) instanceof InputProcessor)) {
			GUISupport.showDialogError("First Processor must always be an Input!");
			return;
		}

		if (!(processors.get(processors.size() - 1) instanceof OutputProcessor)) {
			GUISupport.showDialogError("Last Processor must always be an Output!");
			return;
		}

		// TODO: add other checks, then run the chain.

		// TODO: Update ID -> Processor mapping.
		// idToProcessorMap.put(processorId, processor);

		// No more changes to commit after successful commit operation.
		changesToCommit.set(false);
	}

	public Processor getProcessorForSourceId(final int sourceId) {
		// Any valid source ID will always be present inside the Map.
		return idToProcessorMap.get(sourceId);
	}

	public Chip getChipForSourceId(final int sourceId) {
		final Processor procSource = getProcessorForSourceId(sourceId);

		if ((procSource != null) && (procSource instanceof InputProcessor)) {
			return ((InputProcessor) procSource).getInterpreterChip();
		}

		return null;
	}

	@Override
	public String toString() {
		return String.format("%s - ID %d", chainName, chainId);
	}
}
