package net.sf.jaer2.eventio.events;

public class PolarityEvent extends XYPositionEvent {
	private static final long serialVersionUID = -7577307756995975930L;

	public static enum Polarity {
		ON,
		OFF,
	}

	public Polarity polarity;

	public PolarityEvent(final int ts) {
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
