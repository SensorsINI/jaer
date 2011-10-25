/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.config;

import eu.seebetter.ini.chips.config.ConfigInt;
import java.util.prefs.PreferenceChangeEvent;
import net.sf.jaer.chip.Chip;

/** A integer configuration value on CPLD shift register.
 * @author tobi
 */
public class CPLDInt extends CPLDConfigValue implements ConfigInt {

    volatile int value;
    int def;

    public CPLDInt(Chip chip, int startBit, int endBit, String name, String tip, int def) {
        super(chip, startBit, endBit, name, tip);
        this.startBit = startBit;
        this.endBit = endBit;
        this.def = def;
        key = "CPLDInt." + name;
        if (endBit - startBit != 15) {
            throw new Error("wrong number of bits (only counted " + (endBit - startBit + 1) + ") but there should be 16 in " + this);
        }
        loadPreference();
    }

    @Override
    public void set(int value) throws IllegalArgumentException {
        if (value < 0 || value >= 1 << nBits) {
            throw new IllegalArgumentException("tried to store value=" + value + " which larger than permitted value of " + (1 << nBits) + " or is negative in " + this);
        }
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
        return String.format("CPLDInt name=%s value=%d", name, value);
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
