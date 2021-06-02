package eu.seebetter.ini.chips.davis.imu;

/*
 * This class created in Telluride 2013 to encapsulate Invensense IMU MPU-6150
 * used on SeeBetter cammeras for gyro/accelometer
 */
import java.util.logging.Logger;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.util.filter.LowpassFilter;
import eu.seebetter.ini.chips.DavisChip;

/**
 * Encapsulates data sent from device Invensense Inertial Measurement Unit (IMU)
 * MPU-6150. (acceleration x/y/z, temperature, gyro x/y/z => 7 x 2 bytes = 14
 * bytes) plus the sample timestamp.
 *
 */
public class IMUSample {

    private final static Logger log = Logger.getLogger("IMUSample");
    /**
     * This byte value as first byte signals an IMU (gyro/accelerometer/compass)
     * sample in the USB byte array sent from device
     */
    static final byte IMU_SAMPLE_CODE = (byte) 0xff;

    /**
     * The 16-bit ADC sample data bits of the IMU data are these bits
     */
    static final int DATABITMASK = 0x0FFFF000;

    /**
     * Data is shifted left this many bits in final 32-bit address
     */
    static final int DATABITSHIFT = 12;

    /**
     * code for this sample is left shifted this many bits plus the DATABITSHIFT
     * shift in the raw 32 bit AE address. The 4 LS bytes are the data, and the
     * code for the data type is the next 3 bits at this shift
     */
    static final int CODEBITSHIFT = 16 + IMUSample.DATABITSHIFT;

    /**
     * These bits contain the type of the sample, as coded in
     * IMUSampleType.code. If any of these bits are set then also this AE
     * address is an IMU sample of some type.
     */
    static final int CODEBITMASK = 0x07 << IMUSample.CODEBITSHIFT;

    /**
     * Size of IMUSample in events written or read from AEPacketRaw
     */
    public static final int SIZE_EVENTS = 7;

    /**
     * IMU sensitivity scaling: The IMU 16-bit values are scaled by this amount
     * to result in either deg/s, g, or deg C
     */
    private static float accelSensitivityScaleFactorGPerLsb = 1f / 8192,
            gyroSensitivityScaleFactorDegPerSecPerLsb = 1f / 65.5f, temperatureScaleFactorDegCPerLsb = 1f / 340,
            temperatureOffsetDegC = 35;

    /**
     * Full scale values
     */
    private static float fullScaleAccelG = 4f;
    /**
     * Full scale values
     */
    private static float fullScaleGyroDegPerSec = 1000f;

    /**
     * @return the fullScaleAccelG
     */
    public static float getFullScaleAccelG() {
        return fullScaleAccelG;
    }

    /**
     * Changes full scale value constant. Note that this only changes scaling,
     * not the IMU itself.
     *
     * @param aFullScaleAccelG the fullScaleAccelG to set
     */
    public static void setFullScaleAccelG(float aFullScaleAccelG) {
        fullScaleAccelG = aFullScaleAccelG;
    }

    /**
     * @return the fullScaleGyroDegPerSec
     */
    public static float getFullScaleGyroDegPerSec() {
        return fullScaleGyroDegPerSec;
    }

    /**
     * Changes full scale value constant. Note that this only changes scaling,
     * not the IMU itself.
     *
     * @param aFullScaleGyroDegPerSec the fullScaleGyroDegPerSec to set
     */
    public static void setFullScaleGyroDegPerSec(float aFullScaleGyroDegPerSec) {
        fullScaleGyroDegPerSec = aFullScaleGyroDegPerSec;
    }

    /**
     * @return the accelSensitivityScaleFactorGPerLsb
     */
    public static float getAccelSensitivityScaleFactorGPerLsb() {
        return accelSensitivityScaleFactorGPerLsb;
    }

    /**
     * @param aAccelSensitivityScaleFactorGPerLsb the
     * accelSensitivityScaleFactorGPerLsb to set
     */
    public static void setAccelSensitivityScaleFactorGPerLsb(float aAccelSensitivityScaleFactorGPerLsb) {
        accelSensitivityScaleFactorGPerLsb = aAccelSensitivityScaleFactorGPerLsb;
    }

    /**
     * @return the gyroSensitivityScaleFactorDegPerSecPerLsb
     */
    public static float getGyroSensitivityScaleFactorDegPerSecPerLsb() {
        return gyroSensitivityScaleFactorDegPerSecPerLsb;
    }

    /**
     * @param aGyroSensitivityScaleFactorDegPerSecPerLsb the
     * gyroSensitivityScaleFactorDegPerSecPerLsb to set
     */
    public static void setGyroSensitivityScaleFactorDegPerSecPerLsb(float aGyroSensitivityScaleFactorDegPerSecPerLsb) {
        gyroSensitivityScaleFactorDegPerSecPerLsb = aGyroSensitivityScaleFactorDegPerSecPerLsb;
    }

    /**
     * The IMU data
     */
    private final short[] data = new short[IMUSample.SIZE_EVENTS];

    /**
     * Timestamp of IMUSample in us units using AER time basis
     */
    private int timestampUs;
    /*
	 * the time in us from since last sample. However note that if multiple
	 * threads or objects create IMUSamples, than this deltaTimeUs is
	 * meaningless.
     */
    private int deltaTimeUs;

    /**
     * Used to track when last sample came in via EventPacket in us timestamp
     * units
     */
    private static int lastTimestampUs = 0;
    private static boolean firstSampleDone = false;

    /**
     * Used to track sample rate
     */
    private static LowpassFilter sampleIntervalFilter = new LowpassFilter(100); // time
    // constant

    /**
     * Holds incomplete IMUSample and completion status
     *
     */
    public static class IncompleteIMUSampleException extends Exception {

        /**
         *
         */
        private static final long serialVersionUID = -4325337520299924553L;
        IMUSample partialSample;
        int nextCode = 0;

        /**
         * Constructs new IncompleteIMUSampleException
         *
         * @param partialSample the partially completed sample.
         * @param nextCode the next sample type to be filled in.
         */
        public IncompleteIMUSampleException(final IMUSample partialSample, final int nextCode) {
            this.partialSample = partialSample;
            this.nextCode = nextCode;
        }

        @Override
        public String toString() {
            return String.format("IncompleteIMUSampleException holding %s completed up to sampleType.code=%d",
                    partialSample, nextCode);
        }
    }

    public static class BadIMUDataException extends Exception {

        /**
         *
         */
        private static final long serialVersionUID = -4336829576047317261L;

        public BadIMUDataException(final String message) {
            super(message);
        }

    }

    /**
     * The protected constructor for an empty IMUSample
     *
     */
    protected IMUSample() {
        super();
    }

    public void copyFrom(final IMUSample src) {
        final IMUSample s = (IMUSample) src;
        System.arraycopy(s.data, 0, data, 0, data.length);
        deltaTimeUs = s.deltaTimeUs;
        timestampUs = s.timestampUs;
    }

    /**
     * Constructs a new IMUSample from the AEPacketRaw. This factory method
     * deals with situation that packet does not contain an entire IMUSample.
     *
     * @param packet the packet.
     * @param start the starting index where the sample starts.
     * @param previousException null ordinarily, or a previous exception if the
     * sample was not completed.
     * @return the sample, or null if the sample is bad because bogus data was
     * detected in it.
     * @throws IncompleteIMUSampleException if the packet is too short to
     * contain the entire sample. The returned exception contains the partially
     * completed sample and the completion status and can be passed into a new
     * call to constructFromAEPacketRaw to complete the sample.
     */
    public static IMUSample constructFromAEPacketRaw(final AEPacketRaw packet, final int start,
            final IncompleteIMUSampleException previousException) throws IncompleteIMUSampleException, BadIMUDataException {
        IMUSample sample;
        int startingCode = 0;
        if (previousException != null) {
            sample = previousException.partialSample;
            startingCode = previousException.nextCode;
        } else {
            sample = new IMUSample();
        }
        sample.timestampUs = packet.timestamps[start]; // assume all have same
        // timestamp
        int offset = 0;
        for (int code = startingCode; code < IMUSampleType.values().length; code++) {
            if ((start + offset) >= packet.getNumEvents()) {
                throw new IncompleteIMUSampleException(sample, code);
            }
            final int data = packet.addresses[start + offset];
            if ((DavisChip.ADDRESS_TYPE_IMU & data) != DavisChip.ADDRESS_TYPE_IMU) {
                throw new BadIMUDataException("bad data, not an IMU data type, wrong bits are set: " + data);
            }
            int actualCode;
            if ((actualCode = IMUSample.extractSampleTypeCode(data)) != code) {
                throw new BadIMUDataException("bad data, data=" + data + " should contain code=" + code
                        + " but actual code=" + actualCode);
            }
            final int v = (IMUSample.DATABITMASK & data) >>> IMUSample.DATABITSHIFT;
            sample.data[code] = (short) v;
            offset++;
        }
        sample.updateStatistics(sample.timestampUs);
        return sample;
    }

    /**
     * Extracts the sample type code from the 32-bit address data
     *
     * @param addr the 32-bit raw address in AEPacketRaw
     * @return the code for the sample type
     * @see IMUSampleType
     */
    public static int extractSampleTypeCode(final int addr) {
        final int code = ((addr & IMUSample.CODEBITMASK) >>> IMUSample.CODEBITSHIFT);
        return code;
    }

    /**
     * Creates a new IMUSample collection from the short buffer of 7
     * measurements
     *
     * @param ts timestamp
     * @param buf short array with 7 measurements from IMU Sensor
     *
     */
    public IMUSample(final int ts, final short[] buf) {
        this();
        setFromShortArrayBuf(ts, buf);
    }

    private void setFromShortArrayBuf(final int ts, final short[] buf) {
        timestampUs = ts;
        System.arraycopy(buf, 0, data, 0, 7);
    }

    /**
     * Computes deltaTimeUs and average sample rate
     *
     * @param timestampUs
     */
    private void updateStatistics(final int timestampUsNew) {
        deltaTimeUs = timestampUsNew - IMUSample.lastTimestampUs;
        IMUSample.sampleIntervalFilter.filter(IMUSample.firstSampleDone ? deltaTimeUs : 0, timestampUsNew);
        IMUSample.firstSampleDone = true;
        IMUSample.lastTimestampUs = timestampUsNew;
    }

    @Override
    public String toString() {
        return String.format(
            "timestampUs=%d deltaTimeUs=%d (ax,ay,az)=(%.2f,%.2f,%.2f) g (gx,gy,gz)=(%.2f,%.2f,%.2f) deg/sec temp=%.2fC Samples: (ax,ay,az)=(%d,%d,%d) (gx,gy,gz)=(%d,%d,%d) temp=%d",
            getTimestampUs(), deltaTimeUs, getAccelX(), getAccelY(), getAccelZ(),
            getGyroTiltX(), getGyroYawY(), getGyroRollZ(), getTemperature(), getSensorRaw(IMUSampleType.ax),
            getSensorRaw(IMUSampleType.ay), getSensorRaw(IMUSampleType.az), getSensorRaw(IMUSampleType.gx),
            getSensorRaw(IMUSampleType.gy), getSensorRaw(IMUSampleType.gz), getSensorRaw(IMUSampleType.temp));
    }

    public short getSensorRaw(final IMUSampleType typeIn) {
        return data[typeIn.ordinal()];
    }

    /**
     * Returns acceleration in g's. Positive is camera right.
     *
     * @return the acceleration along x axis
     */
    final public float getAccelX() {
        return -data[IMUSampleType.ax.code] * IMUSample.accelSensitivityScaleFactorGPerLsb;
    }

    /**
     * Returns acceleration in g's in vertical direction. Positive is camera up.
     * with camera in normal orientation will provide approx -1g gravitational
     * acceleration.
     *
     * @return the accelY
     */
    final public float getAccelY() {
        return (data[IMUSampleType.ay.code] * IMUSample.accelSensitivityScaleFactorGPerLsb);
    }

    /**
     * Returns acceleration in g's towards scene. Positive is towards scene.
     *
     * @return the accelZ
     */
    final public float getAccelZ() {
        return data[IMUSampleType.az.code] * IMUSample.accelSensitivityScaleFactorGPerLsb; // TODO sign
        // not
        // checked
    }

    /**
     * Returns rotation in deg/sec. Positive is rotating clockwise.
     *
     * @return the rotational velocity in deg/s
     */
    final public float getGyroRollZ() {
        return -data[IMUSampleType.gz.code] * IMUSample.gyroSensitivityScaleFactorDegPerSecPerLsb;
    }

    /**
     * Returns rotation in deg/sec. Positive is tilt up.
     *
     * @return the rotational velocity in deg/s
     */
    final public float getGyroTiltX() {
        return -data[IMUSampleType.gx.code] * IMUSample.gyroSensitivityScaleFactorDegPerSecPerLsb;
    }

    /**
     * Returns rotation in deg/sec. Positive is yaw right.
     *
     * @return the rotational velocity in deg/s
     */
    final public float getGyroYawY() {
        return data[IMUSampleType.gy.code] * IMUSample.gyroSensitivityScaleFactorDegPerSecPerLsb;
    }

    /**
     * Returns temperature in degrees Celsius
     *
     * @return the temperature
     */
    final public float getTemperature() {
        return (data[IMUSampleType.temp.code] * IMUSample.temperatureScaleFactorDegCPerLsb)
                + IMUSample.temperatureOffsetDegC;
    }

    /**
     * Returns timestamp of sample, which should be on same time base as AEs
     * from sensor. Units are microseconds.
     *
     * @return the timestamp in us.
     */
    final public int getTimestampUs() {
        return timestampUs;
    }

    /**
     * @param timestamp the timestamp to set, in microseconds
     */
    public void setTimestampUs(final int timestamp) {
        timestampUs = timestamp;
    }

    /**
     * Returns raw AE address corresponding to a particular IMU sample type from
     * this IMUSample object that has all sensor values. This method is used to
     * encode the sensor values as raw addresses.
     *
     * @param imuSampletype the type of sensor value event address we want
     */
    final static public int computeAddress(final IMUSample sample, final IMUSampleType imuSampleType) {
        final short data = sample.data[imuSampleType.code];
        final int addr = imuSampleType.codeBits | (IMUSample.DATABITMASK & (data << IMUSample.DATABITSHIFT));
        return addr;
    }

    /**
     * Writes the IMUSample to the AEPacketRaw raw event packet starting at start location. The number
     * of events written to packet is returned and can be used to update the
     * eventCouner in the translateEvents method.
     *
     * @param packet to write to
     * @param start starting event location to write to in the packet
     * @return the number of events written
     */
    public int writeToPacket(final AEPacketRaw packet, final int start) {
        final int cap = packet.getCapacity();
        if ((start + IMUSample.SIZE_EVENTS) >= cap) {
            packet.ensureCapacity(cap + IMUSample.SIZE_EVENTS);
        }
        for (final IMUSampleType sampleType : IMUSampleType.values()) {
            final int idx = start + sampleType.code;
            packet.addresses[idx] = IMUSample.computeAddress(this, sampleType);
            packet.timestamps[idx] = timestampUs;
        }
        packet.setNumEvents(packet.getNumEvents() + IMUSample.SIZE_EVENTS);
        return IMUSample.SIZE_EVENTS;
    }

    /**
     * Returns the global average sample interval in us for IMU samples
     *
     * @return time average sample interval in us
     */
    static public float getAverageSampleIntervalUs() {
        return IMUSample.sampleIntervalFilter.getValue();
    }

    /**
     * Returns the time in us since last sample. Note that only a single
     * thread/class can access IMUSample class for this number to have a
     * meaning, because it is a static class measure.
     *
     * @return the deltaTimeUs
     */
    public int getDeltaTimeUs() {
        return deltaTimeUs;
    }
}
