package net.sf.jaer2.eventio.processors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;
import net.sf.jaer2.eventio.events.Event;

import org.apache.commons.lang3.tuple.ImmutablePair;

public final class SynchronizationProcessor extends EventProcessor {
	public SynchronizationProcessor(final ProcessorChain chain) {
		super(chain);

		// Setup a change listener on the selected input streams for this
		// Processor. If they change, the change shall be reflected in the types
		// this Processor can output, since synchronization is solved for the
		// general case (depends only on time-stamp/number) and thus throws back
		// out synchronized containers with the same types it gets as an input.
		rootConfigTasksDialogOK.add(new Runnable() {
			@Override
			public void run() {
				final List<Class<? extends Event>> newOutputs = new ArrayList<>();

				if (!selectedInputStreamsReadOnly.isEmpty()) {
					for (final ImmutablePair<Class<? extends Event>, Integer> selInStream : selectedInputStreamsReadOnly) {
						newOutputs.add(selInStream.left);
					}
				}

				regenerateAdditionalOutputTypes(newOutputs);
			}
		});
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
}
