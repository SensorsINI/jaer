package net.sf.jaer2.eventio.events;

public class XYPositionEvent extends Event {
	public int x;
	public int y;

	public XYPositionEvent(int ts) {
		super(ts);
	}

	protected final void deepCopyInternal(final XYPositionEvent evt) {
		super.deepCopyInternal(evt);

		evt.x = x;
		evt.y = y;
	}

	@Override
	public XYPositionEvent deepCopy() {
		final XYPositionEvent evt = new XYPositionEvent(timestamp);
		deepCopyInternal(evt);
		return evt;
	}
}
