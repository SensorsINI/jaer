/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips;

import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;

/**
 * Constants for APSDVSChip AE data format such as raw address encodings.
 *
 * @author Christian
 */
abstract public class APSDVSchip extends AETemporalConstastRetina {

    /**
     * Field for decoding pixel address and data type
     */
    public static final int YSHIFT = 22,
            YMASK = 511 << YSHIFT, // 9 bits
            XSHIFT = 12,
            XMASK = 1023 << XSHIFT, // 10 bits
            POLSHIFT = 11,
            POLMASK = 1 << POLSHIFT;

    /* Address-type refers to data if is it an "address". This data is either an AE address or ADC reading.*/
    public static final int ADDRESS_TYPE_MASK = 0x80000000, ADDRESS_TYPE_DVS = 0x00000000, ADDRESS_TYPE_APS = 0x80000000;
    /**
     * Maximal ADC value
     */
    public static final int ADC_BITS = 10, MAX_ADC = (int) ((1 << ADC_BITS) - 1);
    /**
     * For ADC data, the data is defined by the reading cycle (0:reset read, 1
     * first read, 2 second read).
     */
    public static final int ADC_DATA_MASK = MAX_ADC, ADC_READCYCLE_SHIFT = 10, ADC_READCYCLE_MASK = 0xC00;
    /**
     * Property change events fired when these properties change
     */
    public static final String PROPERTY_FRAME_RATE_HZ = "SBRet10.FRAME_RATE_HZ", PROPERTY_EXPOSURE_MS = "SBRet10.EXPOSURE_MS";

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
    
    
    
}
