package net.sf.jaer2.eventio.events;

public class PolarityEvent extends BaseEvent {
	public enum Polarity {
		ON,
		OFF,
	}

	public Polarity polarity;
}
