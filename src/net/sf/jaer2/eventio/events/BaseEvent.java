package net.sf.jaer2.eventio.events;

public class BaseEvent implements Event {
	private int sourceID = 0;
	private boolean valid = true;

	public int timestamp;

	public int type;

	public int x;
	public int y;

	@Override
	public final Class<? extends Event> getEventType() {
		return getClass();
	}

	@Override
	public final int getEventSource() {
		return sourceID;
	}

	@Override
	public final void setEventSource(final int source) {
		sourceID = source;
	}

	@Override
	public final boolean isValid() {
		return valid;
	}

	@Override
	public final void setValid(final boolean validEvent) {
		valid = validEvent;
	}

	@Override
	public final void invalidate() {
		valid = false;
	}

	@Override
	public final int getTimestamp() {
		return timestamp;
	}

	protected final void deepCopyInternal(final BaseEvent evt) {
		evt.sourceID = sourceID;
		evt.valid = valid;

		evt.timestamp = timestamp;

		evt.type = type;

		evt.x = x;
		evt.y = y;
	}

	@Override
	public BaseEvent deepCopy() {
		final BaseEvent evt = new BaseEvent();
		deepCopyInternal(evt);
		return evt;
	}
}
