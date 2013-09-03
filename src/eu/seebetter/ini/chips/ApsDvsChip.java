/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips;

import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;

/**
 * Constants for ApsDvsChip AE data format such as raw address encodings.
 *
 * @author Christian/Tobi (added IMU)
 */
abstract public class ApsDvsChip extends AETemporalConstastRetina {

    /**
     * Field for decoding pixel address and data type
     */
    public static final int YSHIFT = 22,
            YMASK = 511 << YSHIFT, // 9 bits from bits 22 to 30
            XSHIFT = 12,
            XMASK = 1023 << XSHIFT, // 10 bits from bits 12 to 21
            POLSHIFT = 11,
            POLMASK = 1 << POLSHIFT, //,    // 1 bit at bit 11
            TRIGGERSHIFT = 10,
            TRIGGERMASK = 1 << TRIGGERSHIFT;
//            IMUSHIFT =31, // 1 bit at bit 31 encodes IMU data
//            IMUMASK=1<<IMUSHIFT; // adc samples are bits 0-9, with ADC data type at bits 10 to 11 and x and y addresses at DVS x,y bit locations

    /* Address-type refers to data if is it an "address". This data is either an AE address or ADC reading or an IMU sample.*/
    public static final int ADDRESS_TYPE_MASK = 0x80000000, ADDRESS_TYPE_DVS = 0x00000000, ADDRESS_TYPE_APS = 0x80000000, ADDRESS_TYPE_IMU=0x80000C00;
    /**
     * Maximal ADC value
     */
    public static final int ADC_BITS = 10, MAX_ADC = (1 << ADC_BITS) - 1;
    /**
     * For ADC data, the data is defined by the reading cycle (0:reset read, 1
     * first read, 2 second read, which is deprecated and not used).
     */
    public static final int ADC_DATA_MASK = MAX_ADC, ADC_READCYCLE_SHIFT = 10, ADC_READCYCLE_MASK = 0xC00;
    /**
     * Property change events fired when these properties change
     */
    public static final String PROPERTY_FRAME_RATE_HZ = "ApsDvsChip.FRAME_RATE_HZ", PROPERTY_EXPOSURE_MS = "ApsDvsChip.EXPOSURE_MS";

    /**
     * Returns maximum ADC count value
     *
     * @return max value, e.g. 1023
     */
    abstract public int getMaxADC();

    /**
     * Turns off bias generator
     *
     * @param powerDown true to turn off
     */
    abstract public void setPowerDown(boolean powerDown);

    /**
     * Returns measured frame rate
     *
     * @return frame rate in Hz
     */
    abstract public float getFrameRateHz();

    /**
     * Returns measured exposure time
     *
     * @return exposure time in ms
     */
    abstract public float getExposureMs();

      /**
     * Triggers the taking of one snapshot
     */
    abstract public void takeSnapshot();

    /** Sets threshold for automatically triggers snapshot images
     *
     * @param thresholdEvents set to zero to disable automatic snapshots
     */
    abstract public void setAutoshotThresholdEvents(int thresholdEvents);
    /** Returns threshold
     *
     * @return threshold. Zero means auto-shot is disabled.
     */
    abstract public int getAutoshotThresholdEvents();

    /**
     * Sets whether automatic exposure control is enabled
     * @param yes
     */
    abstract public void setAutoExposureEnabled(boolean yes);

    /**
     * Returns if automatic exposure control is enabled.
     * @return
     */
    abstract public boolean isAutoExposureEnabled();

    /**
     * Returns if the image histogram should be displayed.
     * @return true if it should be displayed.
     */
    abstract public boolean isShowImageHistogram();

    /**
     * Sets if the image histogram should be displayed.
     * @param yes true to show histogram
     */
    abstract public void setShowImageHistogram(boolean yes);

    /**
     * Sets whether to display IMU output
     * @param yes true to show IMU output (if available)
     */
    abstract public void setShowIMU(boolean yes);
    /**
     * Returns whether IMU output should be shown.
     * @return true if it should be shown
     */
    abstract public boolean isShowIMU();

}
