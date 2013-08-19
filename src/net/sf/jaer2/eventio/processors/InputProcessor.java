package net.sf.jaer2.eventio.processors;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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

	public InputProcessor(final ProcessorChain chain) {
		super(chain);

		buildConfigGUI();
	}

	public Source getConnectedSource() {
		return connectedSource;
	}

	public void setConnectedSource(final Source source) {
		connectedSource = source;
	}

	public Chip getInterpreterChip() {
		return interpreterChip;
	}

	public void setInterpreterChip(final Chip chip) {
		interpreterChip = chip;

		// Regenerate output types based on what the Chip can produce.
		if (interpreterChip != null) {
			final List<Class<? extends Event>> chipOutputTypes = new ArrayList<>();
			chipOutputTypes.addAll(interpreterChip.getEventTypes());

			regenerateAdditionalOutputTypes(chipOutputTypes);
			rebuildStreamSets();
		}
	}

	private void buildConfigGUI() {
		// Clear input stream selection box from parent, not needed.
		rootConfigLayout.getChildren().clear();

		// Create Source type chooser box.
		final ComboBox<Class<? extends Source>> sourceTypeChooser = GUISupport.addComboBox(null,
			Reflections.sourceTypes, 0);
		GUISupport.addLabelWithControlsHorizontal(rootConfigLayout, "Source:",
			"Select the input Source you want to use.", sourceTypeChooser);

		// Create Chip type chooser box.
		final ComboBox<Class<? extends Chip>> chipTypeChooser = GUISupport.addComboBox(null, Reflections.chipTypes, 0);
		GUISupport.addLabelWithControlsHorizontal(rootConfigLayout, "Chip:",
			"Select the Chip you want to use to translate the raw events coming from the source into meaningful ones.",
			chipTypeChooser);

		rootConfigTasks.add(new Runnable() {
			@Override
			public void run() {
				Constructor<? extends Chip> constr = null;

				try {
					constr = chipTypeChooser.getValue().getConstructor();

					if (constr == null) {
						throw new NullPointerException("constructor is null");
					}
				}
				catch (NoSuchMethodException | SecurityException | NullPointerException e) {
					GUISupport.showDialogException(e);
					return;
				}

				Chip chip = null;

				try {
					chip = constr.newInstance();

					if (chip == null) {
						throw new NullPointerException("newly created class is null");
					}
				}
				catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NullPointerException e) {
					GUISupport.showDialogException(e);
					return;
				}

				setInterpreterChip(chip);
			}
		});
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
				final EventPacketContainer pktContainer = interpreterChip.extractEventPacketContainer(inRawEventPacket);

				if (pktContainer != null) {
					getNextProcessor().add(pktContainer);
				}
			}

			inputToProcess.clear();
		}
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

	public void addToInput(final RawEventPacket rawEventPacket) {
		inputQueue.offer(rawEventPacket);
	}

	public void addAllToInput(final Collection<RawEventPacket> rawEventPackets) {
		for (final RawEventPacket rawEventPacket : rawEventPackets) {
			inputQueue.offer(rawEventPacket);
		}
	}
}
