package net.sf.jaer2.eventio.events;

public class SpecialEvent extends XYPositionEvent {
	private static final long serialVersionUID = -7699877440015843698L;

	public static enum Type {
		SYNC,
		TRIGGER,
		ROW_ONLY,
	}

	public Type type;

	public SpecialEvent(final int ts) {
		super(ts);
	}

	protected final void deepCopyInternal(final SpecialEvent evt) {
		super.deepCopyInternal(evt);

		evt.type = type;
	}

	@Override
	public SpecialEvent deepCopy() {
		final SpecialEvent evt = new SpecialEvent(timestamp);
		deepCopyInternal(evt);
		return evt;
	}
}
