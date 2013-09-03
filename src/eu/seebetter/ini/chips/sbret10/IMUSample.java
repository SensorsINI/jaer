package eu.seebetter.ini.chips.sbret10;

/*
 * This class created in Telluride 2013 to encapsulate Invensense IMU MPU-6150
 * used on SeeBetter cammeras for gyro/accelometer
 */

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.util.filter.LowpassFilter;
import de.thesycon.usbio.UsbIoBuf;
import eu.seebetter.ini.chips.ApsDvsChip;

/**
 * Data sent from device IMU: // accel x/y/z, temp, gyro x/y/z => 7 x 2 bytes =
 * 14 bytes
 */
public class IMUSample {
	private static Logger log = Logger.getLogger("IMUSample");
	/**
	 * This byte value as first byte signals an IMU (gyro/accelerometer/compass)
	 * sample in the USB byte array sent from device
	 */
	private static final byte IMU_SAMPLE_CODE = (byte) 0xff;

	/** The data bits of the IMU data are these bits */
	private static final int DATABITMASK = 0x0FFFF000;

	/**
	 * code for this sample is left shifted this many bits in the raw 32 bit AE
	 * address. The lsbs are the data, and the code for the data type is at this
	 * BITSHIFT
	 */
	private static final int CODEBITSHIFT = 16;

	private final short[] data = new short[IMUSample.SIZE_EVENTS];

	private int timestamp, deltaTimeUs;

	/** Size of IMUSample in events written or read from AEPacketRaw */
	public static final int SIZE_EVENTS = 7;

	private static long lastSampleTimeSystemNs = System.nanoTime();
	/**
	 * values are from datasheet for reset settings
	 */
	// final float accelScale = 2f / ((1 << 16)-1), gyroScale = 250f / ((1 <<
	// 16)-1), temperatureScale = 1f/340;
	private static final float accelScale = 1f / 16384, gyroScale = 1f / 131, temperatureScale = 1f / 340,
		temperatureOffset = 35;

	/** Full scale values */
	public static final float FULL_SCALE_ACCEL_G = 2f, FULL_SCALE_GYRO_DEG_PER_SEC = 250f;

	private static LowpassFilter sampleIntervalFilter = new LowpassFilter(1000);

	public class IncompleteIMUSampleException extends Exception {
		/**
		 *
		 */
		private static final long serialVersionUID = 4907792823114142362L;

		public IncompleteIMUSampleException(final String message) {
			super(message);
		}
	}

	/**
	 * Creates an new IMUSample from the AEPacketRaw
	 *
	 * @param packet
	 *            the packet
	 * @param start
	 *            the starting index where the sample is
	 */
	public IMUSample(final AEPacketRaw packet, final int start) throws IncompleteIMUSampleException {
		if ((start + IMUSample.SIZE_EVENTS) >= packet.getCapacity()) {
			throw new IncompleteIMUSampleException("IMUSample cannot be constructed from packet " + packet
				+ " starting from event " + start + ", not enough events for a complete sample");
		}

		for (final IMUSampleType sampleType : IMUSampleType.values()) {
			final int v = (IMUSample.DATABITMASK & packet.addresses[start + sampleType.code]) >>> 12;

			data[sampleType.code] = (short) v;
		}

		timestamp = packet.timestamps[start]; // assume all have same timestamp
	}

	/**
	 * Creates a new IMUSample collection from the byte buffer sent from device,
	 * assigning the given timestamp to the samples
	 *
	 * @param buf
	 *            the buffer sent on the endpoint from the device
	 * @param timestamp
	 *            the timestamp in us which comes from the AE event stream
	 *            thread
	 */
	public IMUSample(final UsbIoBuf buf, final int timestamp) {
		setFromUsbIoBuf(buf, timestamp);
	}

	private void setFromUsbIoBuf(final UsbIoBuf buf, final int ts) {
		if (buf.BytesTransferred != 15) {
			IMUSample.log.warning("wrong number of bytes transferred, got " + buf.BytesTransferred);
			return;
		}
		final byte[] b = buf.BufferMem;
		if (b[0] != IMUSample.IMU_SAMPLE_CODE) {
			IMUSample.log.warning("got IMU_Sample message with wrong first byte code. Should be "
				+ IMUSample.IMU_SAMPLE_CODE + " but got " + b[0]);
			return;
		}
		ByteBuffer.wrap(b, 1, 14).asShortBuffer().get(data, 0, 7); // from
																	// http://stackoverflow.com/questions/5625573/byte-array-to-short-array-and-back-again-in-java
		// see page 7 of RM-MPU-6100A.pdf (register map for MPU6150 IMU)
		// data is sent big-endian (MSB first for each sample).
		// data is scaled according to product specification datasheet
		// PS-MPU-6100A.pdf
		// data[IMUSampleType.ax.code] = extractS16(b, 1);
		// data[IMUSampleType.ay.code] = extractS16(b, 3);
		// data[IMUSampleType.az.code] = extractS16(b, 5);
		// data[IMUSampleType.temp.code] = extractS16(b, 7);
		// data[IMUSampleType.gx.code] = extractS16(b, 9);
		// data[IMUSampleType.gy.code] = extractS16(b, 11);
		// data[IMUSampleType.gz.code] = extractS16(b, 13); // TODO remove
		// temperature
		timestamp = ts;
		final long nowNs = System.nanoTime();
		deltaTimeUs = (int) ((nowNs - IMUSample.lastSampleTimeSystemNs) >> 10);
		IMUSample.sampleIntervalFilter.filter(deltaTimeUs, ts);
		IMUSample.lastSampleTimeSystemNs = nowNs;
		// System.out.println("on reception: "+this.toString()); // debug
	}

	/**
	 * Creates a new IMUSample collection from the byte buffer (libusb) sent
	 * from device, assigning the given timestamp to the samples
	 *
	 * @param buf
	 *            the buffer sent on the endpoint from the device via libusb
	 * @param timestamp
	 *            the timestamp in us which comes from the AE event stream
	 *            thread
	 */
	public IMUSample(final ByteBuffer buf, final int timestamp) {
		setFromLibUsbBuf(buf, timestamp);
	}

	private void setFromLibUsbBuf(final ByteBuffer buf, final int ts) {
		if (buf.limit() != 15) {
			IMUSample.log.warning("wrong number of bytes transferred, got " + buf.limit());
			return;
		}

		if (buf.get(0) != IMUSample.IMU_SAMPLE_CODE) {
			IMUSample.log.warning("got IMU_Sample message with wrong first byte code. Should be "
				+ IMUSample.IMU_SAMPLE_CODE + " but got " + buf.get(0));
			return;
		}

		buf.position(1);
		buf.asShortBuffer().get(data, 0, 7);

		// see page 7 of RM-MPU-6100A.pdf (register map for MPU6150 IMU)
		// data is sent big-endian (MSB first for each sample).
		// data is scaled according to product specification datasheet
		// PS-MPU-6100A.pdf
		// data[IMUSampleType.ax.code] = extractS16(b, 1);
		// data[IMUSampleType.ay.code] = extractS16(b, 3);
		// data[IMUSampleType.az.code] = extractS16(b, 5);
		// data[IMUSampleType.temp.code] = extractS16(b, 7);
		// data[IMUSampleType.gx.code] = extractS16(b, 9);
		// data[IMUSampleType.gy.code] = extractS16(b, 11);
		// data[IMUSampleType.gz.code] = extractS16(b, 13); // TODO remove
		// temperature
		timestamp = ts;
		final long nowNs = System.nanoTime();
		deltaTimeUs = (int) ((nowNs - IMUSample.lastSampleTimeSystemNs) >> 10);
		IMUSample.sampleIntervalFilter.filter(deltaTimeUs, ts);
		IMUSample.lastSampleTimeSystemNs = nowNs;
		// System.out.println("on reception: "+this.toString()); // debug
	}

	@Override
	public String toString() {
		return String
			.format(
				"timestamp=%-14d deltaTime=%-8d ax=%-8.3f ay=%-8.3f az=%-8.3f gx=%-8.3f gy=%-8.3f gz=%-8.3f temp=%-8.1f ax= %-8d ay= %-8d az= %-8d gx= %-8d gy= %-8d gz= %-8d temp= %-8d",
				getTimestamp(), deltaTimeUs, getAccelX(), getAccelY(), getAccelZ(), getGyroTiltX(), getGyroYawY(),
				getGyroRollZ(), getTemperature(), getSensorRaw(IMUSampleType.ax), getSensorRaw(IMUSampleType.ay),
				getSensorRaw(IMUSampleType.az), getSensorRaw(IMUSampleType.gx), getSensorRaw(IMUSampleType.gy),
				getSensorRaw(IMUSampleType.gz), getSensorRaw(IMUSampleType.temp));
	}

	public short getSensorRaw(final IMUSampleType type) {
		return data[type.ordinal()];
	}

	/**
	 * Returns acceleration in g's.
	 * Positive is camera right.
	 *
	 * @return the acceleration along x axis
	 */
	final public float getAccelX() {
		return -data[IMUSampleType.ax.code] * IMUSample.accelScale;
	}

	/**
	 * Returns acceleration in g's in vertical direction.
	 * Positive is camera up. with camera in normal orientation will provide
	 * approx -1g gravitational acceleration.
	 *
	 * @return the accelY
	 */
	final public float getAccelY() {
		return (data[IMUSampleType.ay.code] * IMUSample.accelScale);
	}

	/**
	 * Returns acceleration in g's towards scene.
	 * Positive is towards scene.
	 *
	 * @return the accelZ
	 */
	final public float getAccelZ() {
		return data[IMUSampleType.az.code] * IMUSample.accelScale; // TODO sign
																	// not
		// checked
	}

	/**
	 * Returns rotation in deg/sec. Positive is rotating clockwise.
	 *
	 * @return the rotational velocity in deg/s
	 */
	final public float getGyroRollZ() {
		return -data[IMUSampleType.gz.code] * IMUSample.gyroScale;
	}

	/**
	 * Returns rotation in deg/sec. Positive is tilt up.
	 *
	 * @return the rotational velocity in deg/s
	 */
	final public float getGyroTiltX() {
		return -data[IMUSampleType.gx.code] * IMUSample.gyroScale;
	}

	/**
	 * Returns rotation in deg/sec. Positive is yaw right.
	 *
	 * @return the rotational velocity in deg/s
	 */
	final public float getGyroYawY() {
		return data[IMUSampleType.gy.code] * IMUSample.gyroScale;
	}

	/**
	 * Returns temperature in degrees Celsius
	 *
	 * @return the temperature
	 */
	final public float getTemperature() {
		return (data[IMUSampleType.temp.code] * IMUSample.temperatureScale) + IMUSample.temperatureOffset;
	}

	/**
	 * Returns timestamp of sample based in most recent apsDVS timestamp
	 *
	 * @return the timestamp
	 */
	final public int getTimestamp() {
		return timestamp;
	}

	/**
	 * @param timestamp
	 *            the timestamp to set
	 */
	public void setTimestamp(final int timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Returns raw AE address corresponding to a particular IMU sample type from
	 * this IMUSample object that has all sensor values. This method is used to
	 * encode the sensor values as raw addresses.
	 *
	 * @param imuSampletype
	 *            the type of sensor value event address we want
	 */
	final public int getAddress(final IMUSampleType imuSampleType) {
		switch (imuSampleType) {
			case ax:
				return ApsDvsChip.ADDRESS_TYPE_IMU
					| (((IMUSampleType.ax.code << IMUSample.CODEBITSHIFT) | data[IMUSampleType.ax.code]) << 12);
			case ay:
				return ApsDvsChip.ADDRESS_TYPE_IMU
					| (((IMUSampleType.ay.code << IMUSample.CODEBITSHIFT) | data[IMUSampleType.ay.code]) << 12);
			case az:
				return ApsDvsChip.ADDRESS_TYPE_IMU
					| (((IMUSampleType.az.code << IMUSample.CODEBITSHIFT) | data[IMUSampleType.az.code]) << 12);
			case gx:
				return ApsDvsChip.ADDRESS_TYPE_IMU
					| (((IMUSampleType.gx.code << IMUSample.CODEBITSHIFT) | data[IMUSampleType.gx.code]) << 12);
			case gy:
				return ApsDvsChip.ADDRESS_TYPE_IMU
					| (((IMUSampleType.gy.code << IMUSample.CODEBITSHIFT) | data[IMUSampleType.gy.code]) << 12);
			case gz:
				return ApsDvsChip.ADDRESS_TYPE_IMU
					| (((IMUSampleType.gz.code << IMUSample.CODEBITSHIFT) | data[IMUSampleType.gz.code]) << 12);
			case temp:
				return ApsDvsChip.ADDRESS_TYPE_IMU
					| (((IMUSampleType.temp.code << IMUSample.CODEBITSHIFT) | data[IMUSampleType.temp.code]) << 12);
			default:
				throw new RuntimeException("no such sample type " + imuSampleType);
		}
	}

	/**
	 * Writes the IMUSample to the packet starting at start location. The number
	 * of events written to packet is returned and
	 * can be used to update the eventCouner in the translateEvents method.
	 *
	 * @param packet
	 *            to write to
	 * @param start
	 *            starting event location to write to in the packet
	 * @return the number of events written
	 */
	public int writeToPacket(final AEPacketRaw packet, final int start) {
		if ((start + IMUSample.SIZE_EVENTS) >= packet.getCapacity()) {
			packet.ensureCapacity(packet.getCapacity() + IMUSample.SIZE_EVENTS);
		}
		for (final IMUSampleType sampleType : IMUSampleType.values()) {
			packet.addresses[start + sampleType.code] = getAddress(sampleType);
			packet.timestamps[start + sampleType.code] = timestamp;
		}
		return IMUSample.SIZE_EVENTS;
	}

	// /** Fills in this IMUSample from the event packet, assuming that all
	// samples are in order.
	// * If the packet ends before this can be filled then the method returns
	// false, but an internal counter is set
	// * that allow completing the fill from the next call.
	// * @param packet
	// * @param start
	// * @return true if sample is filled in, false if read is not completed
	// before packet ends
	// */
	// public boolean readFromPacket(AEPacketRaw packet, int start){
	// for (IMUSampleType sampleType : IMUSampleType.values()) {
	// data[sampleType.code]=DATABITMASK&packet.addresses[start +
	// sampleType.code];
	// packet.timestamps[start + sampleType.code] = timestamp;
	// }
	// return true;
	// }

	static public float getAverageSampleIntervalUs() {
		return IMUSample.sampleIntervalFilter.getValue();
	}
}
