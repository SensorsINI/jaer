package net.sf.jaer2.eventio.events;

public class IMUEvent extends Event {
	public double accelX;
	public double accelY;
	public double accelZ;

	public double gyroX;
	public double gyroY;
	public double gyroZ;

	public IMUEvent(int ts) {
		super(ts);
	}

	protected final void deepCopyInternal(final IMUEvent evt) {
		super.deepCopyInternal(evt);

		evt.accelX = accelX;
		evt.accelY = accelY;
		evt.accelZ = accelZ;

		evt.gyroX = gyroX;
		evt.gyroY = gyroY;
		evt.gyroZ = gyroZ;
	}

	@Override
	public IMUEvent deepCopy() {
		final IMUEvent evt = new IMUEvent(timestamp);
		deepCopyInternal(evt);
		return evt;
	}
}
