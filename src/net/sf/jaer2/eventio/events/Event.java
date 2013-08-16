package net.sf.jaer2.eventio.events;

public interface Event {
	public Class<? extends Event> getEventType();

	public int getEventSource();

	public void setEventSource(int source);

	public void invalidate();

	public void setValid(boolean valid);

	public boolean isValid();

	public int getTimestamp();

	public Event deepCopy();
}
