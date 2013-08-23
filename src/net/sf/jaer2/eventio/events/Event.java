package net.sf.jaer2.eventio.events;

import java.io.Serializable;

public abstract class Event implements Serializable {
	private static final long serialVersionUID = 6776816266258337111L;

	transient private int sourceID = 0;
	private boolean valid = true;

	public final int timestamp;

	public Event(final int ts) {
		timestamp = ts;
	}

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
	}

	public abstract Event deepCopy();
}
