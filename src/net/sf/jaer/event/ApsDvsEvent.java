package net.sf.jaer.event;

import eu.seebetter.ini.chips.davis.imu.IMUSample;

/**
 * This event class is used in the extractor to hold IMU data from DAVIS cameras so
 * that it can be logged to files and played back here. It adds the ADC sample
 * value and IMU samples. This event has the usual timestamp in us but adds fields for the IMU sample value and IMU
 * sample timestamp
 * (if the event is an IMU sample) and the necessary
 * methods to extract IMU data.
 */
public class ApsDvsEvent extends PolarityEvent {
	   /**
     * The readout type of the multiple readouts: ResetRead is the readout of
     * reset level, SignalRead is the readout of first sample, C, is the readout
     * of 2nd sample, etc. Normally only ResetRead and SignalRead are used and
     * the CDS is done in digital domain by subtracting ResetRead-SignalRead
     * readings.
     */
    public enum ReadoutType {

        Null(3),
        DVS(3),
        ResetRead(0),
        SignalRead(1),
        SOF(3),
        EOF(3),
        SOE(3),
        EOE(3),
        IMU(3); // only 0, 1 are used in raw event stream from Davis cameras

        /**
         * code for this sample type used in raw address encoding in AEPacketRaw
         * events and index into data array containing raw samples
         */
        public final int code;

        private ReadoutType(int code) {
            this.code = code;
        }
        

    }

	/**
	 * This bit determines whether it is the first read (ResetRead) or the
	 * second read (SignalRead) of a pixel. Start/end of frame (SOF/EOF) and
	 * start/end of exposure (SOE/EOE)
	 */
	private ReadoutType readoutType = ReadoutType.Null;

	/**
	 * The ADC sample value. Has value -1 by convention for non-sample events.
	 */
	private int adcSample = 0;

	public ApsDvsEvent() {
	}

	@Override
	public String toString() {
		return super.toString() + ", adcSample=" + adcSample + ", readoutType=" + readoutType.toString();
	}

	@Override
	public void reset() {
		super.reset();

		readoutType = ReadoutType.Null;
		adcSample = 0;
		imuSample = null;
		colorFilter = ColorFilter.W;
	}

	/**
	 * The ADC sample value.
	 *
	 * @return the adcSample
	 */
	public int getAdcSample() {
		return adcSample;
	}

	/**
	 * Sets the ADC sample value.
	 *
	 * @param adcSample
	 *            the adcSample to set
	 */
	public void setAdcSample(final int adcSample) {
		this.adcSample = adcSample;
	}

	/**
	 * The readout type (ResetRead,SignalRead,C)
	 *
	 * @return the readoutType
	 */
	public ReadoutType getReadoutType() {
		return readoutType;
	}

	/**
	 * Sets the readout type (ResetRead,SignalRead,C)
	 *
	 * @param readoutType
	 *            the readoutType to set
	 */
	public void setReadoutType(final ReadoutType readoutType) {
		this.readoutType = readoutType;
	}

	@Override
	public void copyFrom(final BasicEvent src) {
		if (!(src instanceof ApsDvsEvent) && (src instanceof PolarityEvent)) {
			super.copyFrom(src);

			adcSample = 0;
			readoutType = ReadoutType.DVS;
			imuSample = null;
			colorFilter = ColorFilter.W;
		}
		else {
			final ApsDvsEvent e = (ApsDvsEvent) src;
			super.copyFrom(src);

			adcSample = e.getAdcSample();
			readoutType = e.getReadoutType();
			imuSample = e.getImuSample();
			colorFilter = e.getColorFilter();
		}
	}

	public boolean isValidData() {
		return readoutType != ReadoutType.Null;
	}

	/**
	 * Returns true if this is an ADC sample from the APS stream
	 *
	 * @return true if this is an ADC sample from the active pixel sensor imager output
	 */
	public boolean isApsData() {
		switch (readoutType) {
			case ResetRead:
			case SignalRead:
			case SOF:
			case EOF:
			case SOE:
			case EOE:
				return true;

			default:
				return false;
		}
	}

	public boolean isDVSEvent() {
		return readoutType == ReadoutType.DVS;
	}

	public boolean isResetRead() {
		return readoutType == ReadoutType.ResetRead;
	}

	public boolean isSignalRead() {
		return readoutType == ReadoutType.SignalRead;
	}

	public boolean isStartOfFrame() {
		return readoutType == ReadoutType.SOF;
	}

	public boolean isEndOfFrame() {
		return readoutType == ReadoutType.EOF;
	}

	public boolean isStartOfExposure() {
		return readoutType == ReadoutType.SOE;
	}

	public boolean isEndOfExposure() {
		return readoutType == ReadoutType.EOE;
	}

	public boolean isImuSample() {
		return readoutType == ReadoutType.IMU;
	}

	/** If this DAVIS camera event is an IMUSample then this object will be non null */
	private IMUSample imuSample = null;

	/**
	 * @return the imuSample
	 */
	public IMUSample getImuSample() {
		return imuSample;
	}

	/**
	 * Sets the associated IMUSample and ReadoutType.IMU. If imuSample==null, then ReadoutType is set to
	 * ReadoutType.Null
	 *
	 * @param imuSample
	 *            the imuSample to set
	 */
	public void setImuSample(final IMUSample imuSample) {
		this.imuSample = imuSample;

		if (imuSample != null) {
			setReadoutType(ReadoutType.IMU);
		}
		else {
			setReadoutType(ReadoutType.Null);
		}
	}

	/**
	 * Tells for APS events whether they are under a red (R), green (G), blue (B) or white (W) color filter.
	 * By default no color filter (Mono/W) is assumed
	 */
	public enum ColorFilter {
		W,
		R,
		G,
		B
	}

	private ColorFilter colorFilter = ColorFilter.W;

	/**
	 * @return the colorFilter
	 */
	public ColorFilter getColorFilter() {
		return colorFilter;
	}

	/**
	 * @param colorFilter
	 *            the colorFilter to set
	 */
	public void setColorFilter(final ColorFilter colorFilter) {
		this.colorFilter = colorFilter;
	}
}
