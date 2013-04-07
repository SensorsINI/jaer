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
abstract public class APSDVSchip extends AETemporalConstastRetina{
    
    public static final int 
            YSHIFT = 22,
            YMASK = 511 << YSHIFT, // 9 bits
            XSHIFT = 12, 
            XMASK = 1023 << XSHIFT, // 10 bits
            POLSHIFT = 11, 
            POLMASK = 1 << POLSHIFT; 

    /*
     * data type fields
     */
    
    /* Address-type refers to data if is it an "address". This data is either an AE address or ADC reading.*/
    public static final int ADDRESS_TYPE_MASK = 0x80000000, ADDRESS_TYPE_DVS = 0x00000000, ADDRESS_TYPE_APS = 0x80000000;
    /** Maximal ADC value */
    public static final int ADC_BITS = 10, MAX_ADC = (int) ((1 << ADC_BITS) - 1);
    /** For ADC data, the data is defined by the reading cycle (0:reset read, 1 first read, 2 second read). */
    public static final int ADC_DATA_MASK = MAX_ADC, ADC_READCYCLE_SHIFT = 10, ADC_READCYCLE_MASK = 0xC00; 
    
    
    abstract public void takeSnapshot() ;
    
    abstract public int getMaxADC();
    
    abstract public void setPowerDown(boolean powerDown);
    
}
