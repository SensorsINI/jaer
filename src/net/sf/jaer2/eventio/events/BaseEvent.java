package net.sf.jaer2.eventio.events;

public class BaseEvent implements Event {
	private boolean validEvent = true;

	@Override
	public Class<? extends Event> getEventType() {
		return this.getClass();
	}

	@Override
	public void invalidate() {
		validEvent = false;
	}

	@Override
	public void setValid(final boolean valid) {
		validEvent = valid;
	}

	@Override
	public boolean isValid() {
		return validEvent;
	}
}
