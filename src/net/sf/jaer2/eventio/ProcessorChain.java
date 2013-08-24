package net.sf.jaer2.eventio;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import net.sf.jaer2.util.XMLconf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessorChain implements Serializable {
	private static final long serialVersionUID = -3333908358242929812L;

	/** Local logger for log messages. */
	private static final Logger logger = LoggerFactory.getLogger(ProcessorChain.class);

	/** Chain identification ID. */
	transient private final int chainId;
	/** Chain identification Name. */
	private final String chainName;

	/** Network this chain belongs to. */
	transient private ProcessorNetwork parentNetwork;

	/** List of all processors in this chain. */
	private final List<Processor> processors = new ArrayList<>();

	/** Unique ID counter for processor identification. */
	private int processorIdCounter = 1;

	/** Map Processor IDs to the corresponding Processor. */
	private final ConcurrentMap<Integer, Processor> idToProcessorMap = new ConcurrentHashMap<>();

	/** Commit Change Signal. */
	transient private final BooleanProperty changesToCommit = new SimpleBooleanProperty(false);

	/** Main GUI layout - Horizontal Box. */
	transient private final HBox rootLayout = new HBox(10);

	/** Configuration GUI layout - Vertical Box. */
	transient private final VBox rootConfigLayout = new VBox(10);

	/** Configuration GUI: tasks to execute before showing the dialog. */
	transient private final List<Runnable> rootConfigTasksDialogRefresh = new ArrayList<>();
	/** Configuration GUI: tasks to execute on clicking OK. */
	transient private final List<Runnable> rootConfigTasksDialogOK = new ArrayList<>();

	public ProcessorChain() {
		chainId = ProcessorNetwork.getNextAvailableChainID();
		chainName = getClass().getSimpleName();

		CommonConstructor();
	}

	private void CommonConstructor() {
		// Build GUIs for this processor, always in this order!
		buildConfigGUI();
		buildGUI();

		ProcessorChain.logger.debug("Created ProcessorChain {}.", this);
	}

	private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		// Restore transient fields.
		Reflections.setFinalField(this, "chainId", ProcessorNetwork.getNextAvailableChainID());
		Reflections.setFinalField(this, "changesToCommit", new SimpleBooleanProperty(false));
		Reflections.setFinalField(this, "rootLayout", new HBox(10));
		Reflections.setFinalField(this, "rootConfigLayout", new VBox(10));
		Reflections.setFinalField(this, "rootConfigTasksDialogRefresh", new ArrayList<Runnable>());
		Reflections.setFinalField(this, "rootConfigTasksDialogOK", new ArrayList<Runnable>());

		// Update processors to set parent chain to this current instance.
		for (final Processor proc : processors) {
			proc.setParentChain(this);
		}

		// Ensure the full stream sets are rebuilt and displayed.
		if (!processors.isEmpty()) {
			processors.get(0).rebuildStreamSets();
		}

		// Do construction.
		CommonConstructor();
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
	 * Set the network this chain belongs to.
	 *
	 * @param network
	 *            parent network.
	 */
	public void setParentNetwork(final ProcessorNetwork network) {
		parentNetwork = network;
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
		GUISupport.addButtonWithMouseClickedHandler(controlBox, "Remove Chain", true, "/images/icons/Remove.png",
			new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					parentNetwork.removeChain(ProcessorChain.this);
				}
			});

		GUISupport.addButtonWithMouseClickedHandler(controlBox, "Save Chain", true,
			"/images/icons/Export To Document.png", new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					XMLconf.toXML(ProcessorChain.this, null);
				}
			});

		GUISupport.addButtonWithMouseClickedHandler(controlBox, "New Processor", true, "/images/icons/Add.png",
			new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					GUISupport.showDialog("New Processor Configuration", rootConfigLayout,
						rootConfigTasksDialogRefresh, rootConfigTasksDialogOK, null);
				}
			});

		GUISupport.addButtonWithMouseClickedHandler(controlBox, "Load Processor", true,
			"/images/icons/Import Document.png", new EventHandler<MouseEvent>() {
				@Override
				public void handle(@SuppressWarnings("unused") final MouseEvent event) {
					addProcessor(XMLconf.fromXML(Processor.class), 0);
					// TODO: position support for processor load.
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

		// Add content already present at build-time.
		for (final Processor proc : processors) {
			rootLayout.getChildren().add(proc.getGUI());
		}
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

		// Update processors to choose from with the latest content.
		rootConfigTasksDialogRefresh.add(new Runnable() {
			@Override
			public void run() {
				processorPositionChooser.getItems().clear();
				processorPositionChooser.getItems().addAll(processors);

				// Reset default selection on each change to the backing list.
				processorPositionChooser.getSelectionModel().select(0);

				// If the list is empty, ensure the CheckBox is ticked.
				if (processors.isEmpty()) {
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
				if ((!newValue) && (processorPositionChooser.getItems().isEmpty())) {
					processorPositionAtBeginning.setSelected(true);
				}
			}
		});

		// Create EventProcessor type chooser box.
		final ComboBox<Class<? extends EventProcessor>> eventProcessorTypeChooser = GUISupport.addComboBox(null,
			Reflections.eventProcessorTypes, 0);
		final HBox eventProcessorTypeChooserBox = GUISupport.addLabelWithControlsHorizontal(rootConfigLayout,
			"Event Processor:", "Select the Event Processor you want to use.", eventProcessorTypeChooser);

		// Toggle the EventProcessor type chooser box depending on what type of
		// Processor the user has selected at the moment.
		eventProcessorTypeChooserBox.managedProperty().bind(
			processorTypeChooser.valueProperty().isEqualTo(ProcessorTypes.EVENT_PROCESSOR));
		eventProcessorTypeChooserBox.visibleProperty().bind(
			processorTypeChooser.valueProperty().isEqualTo(ProcessorTypes.EVENT_PROCESSOR));

		// Add task to be enacted, based on above GUI configuration settings.
		rootConfigTasksDialogOK.add(new Runnable() {
			@Override
			public void run() {
				// Add a new Processor of the wanted type at the right position.
				int position = 0;

				if (!processorPositionAtBeginning.isSelected()) {
					position = processors.indexOf(processorPositionChooser.getValue()) + 1;
				}

				switch (processorTypeChooser.getValue()) {
					case INPUT_PROCESSOR:
						final Processor inputProcessor = ProcessorChain.createProcessor(ProcessorTypes.INPUT_PROCESSOR,
							null);
						addProcessor(inputProcessor, position);
						break;

					case OUTPUT_PROCESSOR:
						final Processor outputProcessor = ProcessorChain.createProcessor(
							ProcessorTypes.OUTPUT_PROCESSOR, null);
						addProcessor(outputProcessor, position);
						break;

					case EVENT_PROCESSOR:
						final Processor eventProcessor = ProcessorChain.createProcessor(ProcessorTypes.EVENT_PROCESSOR,
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
	private static Processor createProcessor(final ProcessorTypes type, final Class<? extends EventProcessor> clazz) {
		// Create the new, specified Processor.
		Processor processor;

		switch (type) {
			case INPUT_PROCESSOR:
				processor = new InputProcessor();
				break;

			case OUTPUT_PROCESSOR:
				processor = new OutputProcessor();
				break;

			case EVENT_PROCESSOR:
				try {
					processor = Reflections.newInstanceForClass(clazz);
				}
				catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException | NullPointerException e) {
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
		if (processor == null) {
			// Ignore null.
			return;
		}

		GUISupport.runOnJavaFXThread(new Runnable() {
			@Override
			public void run() {
				processor.setParentChain(ProcessorChain.this);

				processors.add(position, processor);
				// Add +1 to compensate for ControlBox element at start.
				rootLayout.getChildren().add(position + 1, processor.getGUI());

				linkProcessor(processor);

				ProcessorChain.logger.debug("Added Processor {}.", processor);

				newStructuralChangesToCommit();
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

				newStructuralChangesToCommit();
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
