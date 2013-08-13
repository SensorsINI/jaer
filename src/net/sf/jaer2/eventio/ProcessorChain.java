package net.sf.jaer2.eventio;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessorChain {
	private final static Logger logger = LoggerFactory.getLogger(ProcessorChain.class);

	private final int chainId;
	private final String chainName;

	protected final ProcessorNetwork parentNetwork;

	private final List<Processor> processors = new LinkedList<>();

	private int processorIdCounter = 1;

	private final HBox rootLayout = new HBox(20);

	public ProcessorChain(final ProcessorNetwork network) {
		parentNetwork = network;

		chainId = parentNetwork.getNextAvailableChainID();
		chainName = getClass().getSimpleName();

		buildGUI();

		ProcessorChain.logger.debug("Created ProcessorChain {}.", this);
	}

	/**
	 * Get unique ID for Processors in this chain.
	 * Always increases by one, no duplicates.
	 * Starts at 1.
	 *
	 * @return Next unique ID for Processor identification.
	 */
	public int getNextAvailableProcessorID() {
		return processorIdCounter++;
	}

	public Pane getGUI() {
		return rootLayout;
	}

	private void buildGUI() {
		final VBox controlBox = new VBox(10);
		rootLayout.getChildren().add(controlBox);

		// First, add the buttons to manage ProcessorChains and new Processors.
		final Label chainDescription = new Label(toString());
		controlBox.getChildren().add(chainDescription);

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
					processorDialog();
				}
			});
	}

	public int getChainId() {
		return chainId;
	}

	public String getChainName() {
		return chainName;
	}

	public ProcessorNetwork getParentNetwork() {
		return parentNetwork;
	}

	protected void processorDialog() {
		final VBox box = new VBox(20);

		// Generate a list of classes extending EventProcessor.
		final Set<Class<? extends EventProcessor>> eventProcessorTypes = Reflections.getSubTypes(EventProcessor.class);

		// Create EventProcessor type chooser box. It will be added later on.
		final ImmutablePair<HBox, ComboBox<Class<? extends EventProcessor>>> eventProcessorTypeChooser = GUISupport
			.addLabelWithComboBoxHorizontal(null, "Event Processor: ", "Select the Event Processor you want to use.",
				eventProcessorTypes, 0);

		final ImmutablePair<HBox, ComboBox<ProcessorTypes>> processorTypeChooser = GUISupport
			.addLabelWithComboBoxHorizontal(box, "Processor Type: ", "Select the processor type you want to create.",
				EnumSet.allOf(ProcessorTypes.class), 0);

		processorTypeChooser.right.valueProperty().addListener(new ChangeListener<ProcessorTypes>() {
			private boolean eventProcessorTypeChooserVisible = false;

			@SuppressWarnings("unused")
			@Override
			public void changed(final ObservableValue<? extends ProcessorTypes> observable,
				final ProcessorTypes oldValue, final ProcessorTypes newValue) {
				// Add or remove EventProcessor type chooser box, based on what
				// the user selected as a Processor type.
				if ((newValue == ProcessorTypes.EVENT_PROCESSOR) && (!eventProcessorTypeChooserVisible)) {
					box.getChildren().add(eventProcessorTypeChooser.left);
					eventProcessorTypeChooserVisible = true;
				}
				else if ((newValue != ProcessorTypes.EVENT_PROCESSOR) && (eventProcessorTypeChooserVisible)) {
					box.getChildren().remove(eventProcessorTypeChooser.left);
					eventProcessorTypeChooserVisible = false;
				}
			}
		});

		final ImmutablePair<HBox, ComboBox<Processor>> processorPositionChooser = GUISupport
			.addLabelWithComboBoxHorizontal(box, "After Processor: ",
				"Place this new Processor right after the selected one.", processors, -1);

		final Dialog dialog = new Dialog(null, "New Processor Configuration", true, false);
		dialog.setContent(box);
		dialog.getActions().addAll(Dialog.Actions.OK, Dialog.Actions.CLOSE);
		final Action response = dialog.show();

		if (response == Dialog.Actions.OK) {
			// Add 1 to shift indexOf return value (-1 to size()-1) into valid
			// range (0 to size()).
			final int position = processors.indexOf(processorPositionChooser.right.getValue()) + 1;

			switch (processorTypeChooser.right.getValue()) {
				case INPUT_PROCESSOR:
					addInputProcessor(position);
					break;

				case OUTPUT_PROCESSOR:
					addOutputProcessor(position);
					break;

				case EVENT_PROCESSOR:
					addEventProcessor(position, eventProcessorTypeChooser.right.getValue());
					break;

				default:
					break;
			}
		}
	}

	private void linkProcessor(final Processor processor, final int position) {
		// Set all internal processor links.
		Processor prevProcessor, nextProcessor;

		try {
			prevProcessor = processors.get(position - 1);

			prevProcessor.setNextProcessor(processor);
		}
		catch (final IndexOutOfBoundsException e) {
			prevProcessor = null;
		}

		try {
			nextProcessor = processors.get(position + 1);

			nextProcessor.setPrevProcessor(processor);
		}
		catch (final IndexOutOfBoundsException e) {
			nextProcessor = null;
		}

		processor.setPrevProcessor(prevProcessor);
		processor.setNextProcessor(nextProcessor);
	}

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

	public InputProcessor addInputProcessor(final int position) {
		final InputProcessor processor = new InputProcessor(this);

		processors.add(position, processor);
		// Add 1 to position to compensate for Control Box at index 0.
		rootLayout.getChildren().add(position + 1, processor.getGUI());

		linkProcessor(processor, position);

		ProcessorChain.logger.debug("Added InputProcessor {}.", processor);

		return processor;
	}

	public void removeInputProcessor(final InputProcessor processor) {
		unlinkProcessor(processor);

		rootLayout.getChildren().remove(processor.getGUI());
		processors.remove(processor);

		ProcessorChain.logger.debug("Removed InputProcessor {}.", processor);
	}

	public OutputProcessor addOutputProcessor(final int position) {
		final OutputProcessor processor = new OutputProcessor(this);

		processors.add(position, processor);
		// Add 1 to position to compensate for Control Box at index 0.
		rootLayout.getChildren().add(position + 1, processor.getGUI());

		linkProcessor(processor, position);

		ProcessorChain.logger.debug("Added OutputProcessor {}.", processor);

		return processor;
	}

	public void removeOutputProcessor(final OutputProcessor processor) {
		unlinkProcessor(processor);

		rootLayout.getChildren().remove(processor.getGUI());
		processors.remove(processor);

		ProcessorChain.logger.debug("Removed OutputProcessor {}.", processor);
	}

	public EventProcessor addEventProcessor(final int position, final Class<? extends EventProcessor> clazz) {
		Constructor<? extends EventProcessor> constr = null;

		try {
			constr = clazz.getConstructor(ProcessorChain.class);

			if (constr == null) {
				throw new NullPointerException("constr is null in addEventProcessor()");
			}
		}
		catch (NoSuchMethodException | SecurityException | NullPointerException e) {
			Dialogs.create().lightweight().title("Exception detected").showException(e);
			return null;
		}

		EventProcessor processor = null;

		try {
			processor = constr.newInstance(this);

			if (processor == null) {
				throw new NullPointerException("processor is null in addEventProcessor()");
			}
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
			| NullPointerException e) {
			Dialogs.create().lightweight().title("Exception detected").showException(e);
			return null;
		}

		processors.add(position, processor);
		// Add 1 to position to compensate for Control Box at index 0.
		rootLayout.getChildren().add(position + 1, processor.getGUI());

		linkProcessor(processor, position);

		ProcessorChain.logger.debug("Added EventProcessor {}.", processor);

		return processor;
	}

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
