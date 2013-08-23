package net.sf.jaer2.eventio.processors;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;

import org.apache.commons.lang3.tuple.ImmutablePair;

public final class SynchronizationProcessor extends EventProcessor {
	private static final long serialVersionUID = -5426769954051929383L;

	public SynchronizationProcessor() {
		super();

		CommonConstructor();
	}

	private void CommonConstructor() {
		// Setup a change listener on the selected input streams for this
		// Processor. If they change, the change shall be reflected in the types
		// this Processor can output, since synchronization is solved for the
		// general case (depends only on time-stamp/number) and thus throws back
		// out synchronized containers with the same types it gets as an input.
		rootConfigTasksDialogOK.add(new Runnable() {
			@Override
			public void run() {
				final List<Class<? extends Event>> newOutputs = new ArrayList<>();

				for (final ImmutablePair<Class<? extends Event>, Integer> selInStream : selectedInputStreamsReadOnly) {
					newOutputs.add(selInStream.left);
				}

				regenerateAdditionalOutputTypes(newOutputs, true);
			}
		});
	}

	private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();

		// When loading from serialized settings, the additional output types
		// need to be regenerated for the SynchronizerProcessor, since it
		// doesn't declare any himself in setAdditionalOutputTypes().
		final List<Class<? extends Event>> newOutputs = new ArrayList<>();

		for (final ImmutablePair<Class<? extends Event>, Integer> selInStream : selectedInputStreamsReadOnly) {
			newOutputs.add(selInStream.left);
		}

		regenerateAdditionalOutputTypes(newOutputs, false);

		// Do construction.
		CommonConstructor();
	}

	@Override
	protected void processEvents(@SuppressWarnings("unused") final EventPacketContainer container) {
		// TODO: write synchronization processor.
	}

	@Override
	protected void setCompatibleInputTypes(final Set<Class<? extends Event>> inputs) {
		// Accepts all inputs.
		inputs.add(Event.class);
	}

	@Override
	protected void setAdditionalOutputTypes(@SuppressWarnings("unused") final Set<Class<? extends Event>> outputs) {
		// Empty, no new output types are ever produced here by itself.
		// They fully depend on the selected input types (see constructor).
	}

	@Override
	protected boolean readyToRun() {
		return true;
	}
}
