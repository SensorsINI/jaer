package net.sf.jaer2.eventio.events;

public abstract class Event {
	private int sourceID = 0;
	private boolean valid = true;

	public int timestamp;

	public int type;

	public final Class<? extends Event> getEventType() {
		return getClass();
	}

	public final int getEventSource() {
		return sourceID;
	}

	public final void setEventSource(final int source) {
		sourceID = source;
	}

	public final boolean isValid() {
		return valid;
	}

	public final void setValid(final boolean validEvent) {
		valid = validEvent;
	}

	public final void invalidate() {
		valid = false;
	}

	public final int getTimestamp() {
		return timestamp;
	}

	protected final void deepCopyInternal(final Event evt) {
		evt.sourceID = sourceID;
		evt.valid = valid;

		evt.timestamp = timestamp;

		evt.type = type;
	}

	public abstract Event deepCopy();
}
