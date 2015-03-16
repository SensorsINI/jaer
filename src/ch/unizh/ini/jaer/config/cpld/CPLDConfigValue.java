/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.cpld;

import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;

import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.config.AbstractConfigValue;

/**
 * Base class for configuration value on the CPLD. Values are stored on a shift register on the CPLD and are
 * loaded big-endian so that the first bit loaded is the msb and the last the lsb; i.e., the bits are loaded
 * from the lsb end (the right end if the bits are thought to be strung out like a binary number from left to right).
 * 
 * @author tobi
 */
public class CPLDConfigValue extends AbstractConfigValue {
    /** Least significant bit position */
    protected int lsb;
    /** Most significant bit position */
    protected int msb;
    protected int nBits = 8;
    protected static final Logger log=Logger.getLogger("CPLDConfigValue");
    protected long maxVal=-1; // if not -1, then determines actual max value

    /**
     * Makes an abstract value.
     * 
     * @param chip Preferences source
     * @param lsb first bit in shift register
     * @param msb last bit
     * @param name 
     * @param tip 
     * @param maxVal maximum allowed value
     */
    public CPLDConfigValue(Chip chip, int startBit, int endBit, long maxVal, String name, String tip) {
        super(chip, name, tip);
        this.lsb = startBit;
        this.msb = endBit;
        this.maxVal=maxVal;
        nBits = endBit - startBit + 1;
    }
    
    public int getNumBits(){
        return nBits;
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent evt) {
    }

    @Override
    public void loadPreference() {
    }

    @Override
    public void storePreference() {
    }

    @Override
    public String toString() {
        return "CPLDConfigValue{" + "name=" + name + " startBit=" + lsb + "endBit=" + msb + "nBits=" + nBits + "maxVal=" + maxVal + '}';
    }

    public long getMax() {
        return maxVal; // tobi changed to support values that are not full resolution of bits that are sent, e.g. to support different endpoints for data on device // (1 << nBits) - 1;
    }

    public long getMin() {
        return 0;
    }
    
}
