package net.sf.jaer2.eventio.events;

public class PolarityEvent extends BaseEvent {
	public enum Polarity {
		ON,
		OFF,
	}

	public Polarity polarity;

	protected final void deepCopyInternal(final PolarityEvent evt) {
		super.deepCopyInternal(evt);

		evt.polarity = polarity;
	}

	@Override
	public PolarityEvent deepCopy() {
		final PolarityEvent evt = new PolarityEvent();
		deepCopyInternal(evt);
		return evt;
	}
}
