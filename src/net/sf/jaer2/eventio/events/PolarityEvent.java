package net.sf.jaer2.eventio.events;

public class PolarityEvent extends XYPositionEvent {
	public enum Polarity {
		ON,
		OFF,
	}

	public Polarity polarity;

	public PolarityEvent(int ts) {
		super(ts);
	}

	protected final void deepCopyInternal(final PolarityEvent evt) {
		super.deepCopyInternal(evt);

		evt.polarity = polarity;
	}

	@Override
	public PolarityEvent deepCopy() {
		final PolarityEvent evt = new PolarityEvent(timestamp);
		deepCopyInternal(evt);
		return evt;
	}
}
