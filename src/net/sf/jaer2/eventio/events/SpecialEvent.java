package net.sf.jaer2.eventio.events;

public class SpecialEvent extends XYPositionEvent {
	public enum Type {
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
