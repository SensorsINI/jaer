package net.sf.jaer2.eventio.events;

public interface Event {
	public Class<? extends Event> getEventType();

	public void invalidate();

	public void setValid(boolean valid);

	public boolean isValid();

	public int getTimestamp();
}
