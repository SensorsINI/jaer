/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips;

import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;
import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.davis.frames.DavisFrameAviWriter;
import eu.seebetter.ini.chips.davis.AutoExposureController;
import eu.seebetter.ini.chips.davis.DavisAutoShooter;
import net.sf.jaer.eventio.FlexTimePlayer;
import net.sf.jaer.eventprocessing.filter.ApsDvsEventFilter;
import net.sf.jaer.eventprocessing.label.SimpleOrientationFilter;
import net.sf.jaer.util.avioutput.JaerAviWriter;

/**
 * Constants for DAVIS AE data format such as raw address encodings. 
 *
 * @author Christian/Tobi (added IMU)
 * @see eu.seebetter.ini.chips.davis.DavisBaseCamera
 */
abstract public class DavisChip extends AETemporalConstastRetina {

	/**
	 * Field for decoding pixel address and data type
	 */
	public static final int YSHIFT = 22, YMASK = 511 << YSHIFT, // 9 bits from bits 22 to 30
		XSHIFT = 12, XMASK = 1023 << XSHIFT, // 10 bits from bits 12 to 21
		POLSHIFT = 11, POLMASK = 1 << POLSHIFT, // , // 1 bit at bit 11
		EVENT_TYPE_SHIFT = 10, EVENT_TYPE_MASK = 3 << EVENT_TYPE_SHIFT, // these 2 bits encode readout type for APS and
																		// other event type (IMU/DVS) for
		EXTERNAL_INPUT_EVENT_ADDR = 1 << EVENT_TYPE_SHIFT, // This special address is is for external pin input events
		IMU_SAMPLE_VALUE = 3 << EVENT_TYPE_SHIFT, // this special code is for IMU sample events
		HW_BGAF = 5, HW_TRACKER_CM = 6, HW_TRACKER_CLUSTER = 7, HW_OMC_EVENT = 4; // event code cannot be higher than 7
																					// in 3 bits
        /** Special event addresses for external input pin events */
        public static final int  // See DavisFX3HardwareInterface line 334 (tobi)
                EXTERNAL_INPUT_EVENT_ADDR_FALLING=2+EXTERNAL_INPUT_EVENT_ADDR,
                EXTERNAL_INPUT_ADDR_RISING=3+EXTERNAL_INPUT_EVENT_ADDR,
                EXTERNAL_INPUT_EVENT_ADDR_PULSE=4+EXTERNAL_INPUT_EVENT_ADDR;
        
	/* Detects bit indicating a DVS external event of type IMU */
	public static final int IMUSHIFT = 0, IMUMASK = 1 << IMUSHIFT;

	/*
	 * Address-type refers to data if is it an "address". This data is either an AE address or ADC reading or an IMU
	 * sample.
	 */
	public static final int ADDRESS_TYPE_MASK = 0x80000000, ADDRESS_TYPE_DVS = 0x00000000, ADDRESS_TYPE_APS = 0x80000000,
		ADDRESS_TYPE_IMU = 0x80000C00;
	/**
	 * Maximal ADC value
	 */
	public static final int ADC_BITS = 10, MAX_ADC = (1 << ADC_BITS) - 1;

	public static final int ADC_NUMBER_OF_TRAILING_ZEROS = Integer.numberOfTrailingZeros(DavisChip.ADC_READCYCLE_MASK);

	/**
	 * For ADC data, the data is defined by the reading cycle (0:reset read, 1
	 * first read, 2 second read, which is deprecated and not used).
	 */
	public static final int ADC_DATA_MASK = MAX_ADC, ADC_READCYCLE_SHIFT = 10, ADC_READCYCLE_MASK = 0xC00;

	/**
	 * Property change events fired when these properties change
	 */
	public static final String PROPERTY_FRAME_RATE_HZ = "DavisChip.FRAME_RATE_HZ", PROPERTY_MEASURED_EXPOSURE_MS = "ApsDvsChip.EXPOSURE_MS";

	public static final String PROPERTY_AUTO_EXPOSURE_ENABLED = "autoExposureEnabled";

	public DavisChip() {
		addDefaultEventFilter(ApsDvsEventFilter.class);
		addDefaultEventFilter(ApsFrameExtractor.class);
		addDefaultEventFilter(FlexTimePlayer.class);
		addDefaultEventFilter(DavisAutoShooter.class);
		addDefaultEventFilter(DavisFrameAviWriter.class);
                removeDefaultEventFilter(JaerAviWriter.class);
		addDefaultEventFilter(JaerAviWriter.class);
	}

	/**
	 * Returns maximum ADC count value
	 *
	 * @return max value, e.g. 1023
	 */
	abstract public int getMaxADC();

	/**
	 * Turns off bias generator
	 *
	 * @param powerDown
	 *            true to turn off
	 */
	abstract public void setPowerDown(boolean powerDown);

	/**
	 * Returns measured frame rate
	 *
	 * @return frame rate in Hz
	 */
	abstract public float getFrameRateHz();

	/**
	 * Returns start of exposure time in timestamp tick units (us). Note this is
	 * particularly relevant for global shutter mode. For rolling shutter
	 * readout mode, this time is the start of exposure time of the first
	 * column.
	 *
	 * @return start of exposure time in timestamp units (us).
	 */
	abstract public int getFrameExposureStartTimestampUs();

	/**
	 * Returns end of exposure time in timestamp tick units (us). Note this is
	 * particularly relevant for global shutter mode. For rolling shutter
	 * readout mode, this time is the last value read from last column.
	 *
	 * @return start of exposure time in timestamp units (us).
	 */
	abstract public int getFrameExposureEndTimestampUs();

	/**
	 * Returns measured exposure time
	 *
	 * @return exposure time in ms
	 */
	abstract public float getMeasuredExposureMs();

	/**
	 * Triggers the taking of one snapshot, i.e, triggers a frame capture.
	 */
	abstract public void takeSnapshot();

	/**
	 * Turns on/off ADC
	 *
	 * @param adcEnabled
	 *            true to turn on
	 */
	abstract public void setADCEnabled(boolean adcEnabled);

	/**
	 * Controls exposure value automatically if auto exposure is enabled.
	 *
	 */
	abstract public void controlExposure();

	/**
	 * Returns the automatic APS exposure controller
	 *
	 * @return the controller
	 */
	abstract public AutoExposureController getAutoExposureController();

	/**
	 * Sets threshold for automatically triggers snapshot images
	 *
	 * @param thresholdEvents
	 *            set to zero to disable automatic snapshots
	 */
	abstract public void setAutoshotThresholdEvents(int thresholdEvents);

	/**
	 * Returns threshold
	 *
	 * @return threshold. Zero means auto-shot is disabled.
	 */
	abstract public int getAutoshotThresholdEvents();

	/**
	 * Sets whether automatic exposure control is enabled
	 *
	 * @param yes
	 */
	abstract public void setAutoExposureEnabled(boolean yes);

	/**
	 * Returns if automatic exposure control is enabled.
	 *
	 * @return
	 */
	abstract public boolean isAutoExposureEnabled();

	/**
	 * Returns if the image histogram should be displayed.
	 *
	 * @return true if it should be displayed.
	 */
	abstract public boolean isShowImageHistogram();

	/**
	 * Sets if the image histogram should be displayed.
	 *
	 * @param yes
	 *            true to show histogram
	 */
	abstract public void setShowImageHistogram(boolean yes);

	/**
	 * Returns the frame counter value. This value is set on each end-of-frame
	 * sample.
	 *
	 * @return the frameCount
	 */
	public abstract int getFrameCount();
}
