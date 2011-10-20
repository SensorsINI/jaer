/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.config;

import java.util.prefs.PreferenceChangeEvent;
import net.sf.jaer.chip.Chip;

/**
 * Base class for configuration value on the CPLD.
 * @author tobi
 */
public class CPLDConfigValue extends AbstractConfigValue {
    protected int startBit;
    protected int endBit;
    protected int nBits = 8;

    /**
     * Makes an abstract value.
     * 
     * @param chip Preferences source
     * @param startBit first bit in shift register
     * @param endBit last bit
     * @param name 
     * @param tip 
     */
    public CPLDConfigValue(Chip chip, int startBit, int endBit, String name, String tip) {
        super(chip, name, tip);
        this.startBit = startBit;
        this.endBit = endBit;
        nBits = endBit - startBit + 1;
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
        return "CPLDConfigValue{" + "name=" + name + " startBit=" + startBit + "endBit=" + endBit + "nBits=" + nBits + '}';
    }
    
}
