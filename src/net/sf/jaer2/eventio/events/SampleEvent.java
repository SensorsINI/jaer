package net.sf.jaer2.eventio.events;

public class SampleEvent extends BaseEvent {
	public int sample;

	protected void deepCopyInternal(SampleEvent evt) {
		super.deepCopyInternal(evt);

		evt.sample = sample;
	}

	@Override
	public SampleEvent deepCopy() {
		SampleEvent evt = new SampleEvent();
		deepCopyInternal(evt);
		return evt;
	}
}
