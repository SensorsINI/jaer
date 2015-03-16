/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.cpld;

import java.util.prefs.PreferenceChangeEvent;

import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.config.ConfigInt;

/** A integer (32-bit) configuration value on CPLD shift register.
 * @author tobi
 */
public class CPLDInt extends CPLDConfigValue implements ConfigInt, HasPreference {

    volatile int value;
    int def;

    /** Makes a new int value on the CPLD shift register.  The int has up to 32 bits. It occupies some bit positions.
     * 
     * @param chip enclosing chip, where preferences come from
     * @param msb the most significant bit position
     * @param lsb least significant
     * @param name name
     * @param tip tool-tip
     * @param def default value
    * @param maxVal maximum allowed value
      */
    public CPLDInt(Chip chip, int msb, int lsb, int maxVal, String name, String tip, int def) {
        super(chip, lsb, msb, maxVal, name, tip);
        this.lsb = lsb;
        this.msb = msb;
        this.def = def;
        key = "CPLDInt." + name;
        if (msb - lsb != 31) {
            log.warning("only counted " + (msb - lsb + 1) + " bits, but there should usually be 32 in a CPLDInt like we are (" + this+")");
        }
        loadPreference();
    }

    @Override
    public void set(int value) throws IllegalArgumentException {
        if (value < getMin() || value > getMax()) {
            log.warning("tried to store value=" + value + " which larger than permitted value of " + ((1 << nBits)-1) + " or is negative in " + this+"; clipped to valid value");
        }
        if(value<getMin()) value=(int)getMin(); else if(value>getMax())value=(int)getMax();
        if (this.value != value) {
            setChanged();
        }
        this.value = value;
        //                log.info("set " + this + " to value=" + value+" notifying "+countObservers()+" observers");
        notifyObservers();
    }

    @Override
    public int get() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("CPLDInt (%d bits %d to %d) name=%s value=%d", msb-lsb+1, lsb, msb, name, value);
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent e) {
        if (e.getKey().equals(key)) {
            //                    log.info(this+" preferenceChange(): event="+e+" key="+e.getKey()+" newValue="+e.getNewValue());
            int newv = Integer.parseInt(e.getNewValue());
            set(newv);
        }
    }

    @Override
    public void loadPreference() {
        set(prefs.getInt(key, def));
    }

    @Override
    public void storePreference() {
        prefs.putInt(key, value); // will eventually call pref change listener which will call set again
    }
    
 }
