package net.sf.jaer2.eventio.events;

public class XYPositionEvent extends Event {
	private static final long serialVersionUID = 5576838970200124104L;

	public int x;
	public int y;

	public XYPositionEvent(final int ts) {
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
