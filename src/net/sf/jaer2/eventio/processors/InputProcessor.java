package net.sf.jaer2.eventio.processors;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ComboBox;
import net.sf.jaer2.chips.Chip;
import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.eventpackets.raw.RawEventPacket;
import net.sf.jaer2.eventio.events.Event;
import net.sf.jaer2.eventio.sources.Source;
import net.sf.jaer2.util.GUISupport;
import net.sf.jaer2.util.Reflections;

public final class InputProcessor extends Processor {
	private final BlockingQueue<RawEventPacket> inputQueue = new ArrayBlockingQueue<>(32);
	private final List<RawEventPacket> inputToProcess = new ArrayList<>(32);

	private Source connectedSource;
	private Chip interpreterChip;

	/** For displaying and maintaining a link to the current config GUI. */
	private Source currentSourceConfig;

	public InputProcessor(final ProcessorChain chain) {
		super(chain);

		// Build GUIs for this processor, always in this order!
		buildConfigGUI();
		buildGUI();
	}

	public Source getConnectedSource() {
		return connectedSource;
	}

	public void setConnectedSource(final Source source) {
		connectedSource = source;

		Processor.logger.debug("ConnectedSource set to: {}.", source);
	}

	public Chip getInterpreterChip() {
		return interpreterChip;
	}

	public void setInterpreterChip(final Chip chip) {
		interpreterChip = chip;

		// Regenerate output types based on what the Chip can produce.
		if (interpreterChip != null) {
			regenerateAdditionalOutputTypes(getInterpreterChip().getEventTypes());
		}

		Processor.logger.debug("InterpreterChip set to: {}.", chip);
	}

	@Override
	protected void setCompatibleInputTypes(@SuppressWarnings("unused") final Set<Class<? extends Event>> inputs) {
		// Empty, doesn't process any inputs at all.
	}

	@Override
	protected void setAdditionalOutputTypes(@SuppressWarnings("unused") final Set<Class<? extends Event>> outputs) {
		// No events exist here by default, they depend on what the selected
		// Chip can output.
	}

	public boolean readyToRun() {
		return ((getInterpreterChip() != null) && (getConnectedSource() != null));
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			// First forward currently waiting packets from previous processors.
			if (workQueue.drainTo(workToProcess) != 0) {
				getNextProcessor().addAll(workToProcess);

				workToProcess.clear();
			}

			// Then see if there is any conversion to be done.
			if (inputQueue.drainTo(inputToProcess) == 0) {
				// Nothing to be done, next cycle!
				continue;
			}

			// Convert raw events into real ones.
			for (final RawEventPacket inRawEventPacket : inputToProcess) {
				final EventPacketContainer outPacketContainer = new EventPacketContainer(this);

				getInterpreterChip().extractEventPacketContainer(inRawEventPacket, outPacketContainer);

				// Send only packets with some (in)valid events on their way.
				if (outPacketContainer.sizeFull() != 0) {
					getNextProcessor().add(outPacketContainer);
				}
			}

			inputToProcess.clear();
		}
	}

	public void addToInput(final RawEventPacket rawEventPacket) {
		inputQueue.offer(rawEventPacket);
	}

	public void addAllToInput(final Collection<RawEventPacket> rawEventPackets) {
		for (final RawEventPacket rawEventPacket : rawEventPackets) {
			inputQueue.offer(rawEventPacket);
		}
	}

	private void buildGUI() {
		rootTasksUIRefresh.add(new Runnable() {
			@Override
			public void run() {
				rootLayoutChildren.getChildren().clear();

				if (interpreterChip != null) {
					GUISupport.addLabel(rootLayoutChildren, interpreterChip.toString(), null, null, null);
				}

				if (connectedSource != null) {
					rootLayoutChildren.getChildren().add(connectedSource.getGUI());
				}
			}
		});
	}

	private void buildConfigGUI() {
		// Create Chip type chooser box.
		final ComboBox<Class<? extends Chip>> chipTypeChooser = GUISupport.addComboBox(null, Reflections.chipTypes, 0);
		GUISupport.addLabelWithControlsHorizontal(rootConfigLayoutChildren, "Chip:",
			"Select the Chip you want to use to translate the raw events coming from the source into meaningful ones.",
			chipTypeChooser);

		rootConfigTasksDialogRefresh.add(new Runnable() {
			@Override
			public void run() {
				if (interpreterChip != null) {
					// Set default value.
					chipTypeChooser.setValue(interpreterChip.getClass());
				}
			}
		});

		rootConfigTasksDialogOK.add(new Runnable() {
			@Override
			public void run() {
				Chip chip;

				try {
					chip = Reflections.newInstanceForClass(chipTypeChooser.getValue());
				}
				catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException | NullPointerException e) {
					GUISupport.showDialogException(e);
					return;
				}

				setInterpreterChip(chip);
			}
		});

		// Create Source type chooser box.
		final ComboBox<Class<? extends Source>> sourceTypeChooser = GUISupport.addComboBox(null,
			Reflections.sourceTypes, -1);
		GUISupport.addLabelWithControlsHorizontal(rootConfigLayoutChildren, "Source:",
			"Select the input Source you want to use.", sourceTypeChooser);

		sourceTypeChooser.valueProperty().addListener(new ChangeListener<Class<? extends Source>>() {
			@SuppressWarnings("unused")
			@Override
			public void changed(final ObservableValue<? extends Class<? extends Source>> observable,
				final Class<? extends Source> oldValue, final Class<? extends Source> newValue) {
				// Don't display old value anymore (if any).
				if (currentSourceConfig != null) {
					rootConfigLayoutChildren.getChildren().remove(currentSourceConfig.getConfigGUI());
				}

				// When the chosen source type changes, create an instance of
				// the new one and save it for future reference, so that when
				// the user clicks OK, it gets saved.
				try {
					currentSourceConfig = Reflections.newInstanceForClass(newValue);
				}
				catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException | NullPointerException e) {
					GUISupport.showDialogException(e);
					return;
				}

				// Add config GUI for new source instance.
				rootConfigLayoutChildren.getChildren().add(currentSourceConfig.getConfigGUI());
			}
		});

		rootConfigTasksDialogRefresh.add(new Runnable() {
			@Override
			public void run() {
				if (connectedSource != null) {
					// Set default value.
					sourceTypeChooser.setValue(connectedSource.getClass());
				}
			}
		});

		rootConfigTasksDialogOK.add(new Runnable() {
			@Override
			public void run() {
				if (currentSourceConfig == null) {
					// Enforce setting a source type.
					GUISupport.showDialogError("No Source selected, please do so!");
					return;
				}

				setConnectedSource(currentSourceConfig);
			}
		});
	}
}
