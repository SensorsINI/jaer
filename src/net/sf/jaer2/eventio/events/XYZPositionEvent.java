package net.sf.jaer2.eventio.events;

public class XYZPositionEvent extends XYPositionEvent {
	private static final long serialVersionUID = -7413859531742503973L;

	public int z;

	public XYZPositionEvent(final int ts) {
		super(ts);
	}

	protected final void deepCopyInternal(final XYZPositionEvent evt) {
		super.deepCopyInternal(evt);

		evt.z = z;
	}

	@Override
	public XYZPositionEvent deepCopy() {
		final XYZPositionEvent evt = new XYZPositionEvent(timestamp);
		deepCopyInternal(evt);
		return evt;
	}
}
