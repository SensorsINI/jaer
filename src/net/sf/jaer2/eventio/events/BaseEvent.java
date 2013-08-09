package net.sf.jaer2.eventio.events;

public class BaseEvent implements Event {
	private int sourceID = 0;
	private boolean valid = true;

	public int timestamp;

	public int type;

	public int x;
	public int y;

	@Override
	public Class<? extends Event> getEventType() {
		return this.getClass();
	}

	@Override
	public int getEventSource() {
		return sourceID;
	}

	@Override
	public void setEventSource(final int source) {
		sourceID = source;
	}

	@Override
	public void invalidate() {
		setValid(false);
	}

	@Override
	public void setValid(final boolean validEvent) {
		valid = validEvent;
	}

	@Override
	public boolean isValid() {
		return valid;
	}

	@Override
	public int getTimestamp() {
		return timestamp;
	}
}
