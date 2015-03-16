/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.cpld;

import java.util.prefs.PreferenceChangeEvent;

import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.config.ConfigBit;

/** A bit output from CPLD port. */
/**
 *
 * @author tobi
 */
public class CPLDBit extends CPLDConfigValue implements ConfigBit, HasPreference {
    int pos; // bit position from lsb position in CPLD config
    boolean value;
    boolean def;

    /** Constructs new CPLDBit.
     *
     * @param chip from where we get preferences
     * @param pos position in shift register
     * @param name name label
     * @param tip tool-tip
     * @param def default preferred value
     */
    public CPLDBit(Chip chip, int pos, String name, String tip, boolean def) {
        super(chip, pos, pos, 1, name, tip);
        this.pos = pos;
        this.def = def;
        loadPreference();
        //                hasPreferencesList.add(this);
    }

    @Override
    public void set(boolean value) {
        if (this.value != value) {
            setChanged();
        }
        this.value = value;
        //                log.info("set " + this + " to value=" + value+" notifying "+countObservers()+" observers");
        notifyObservers();
    }

    @Override
    public boolean isSet() {
        return value;
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent e) {
        if (e.getKey().equals(key)) {
            //                    log.info(this+" preferenceChange(): event="+e+" key="+e.getKey()+" newValue="+e.getNewValue());
            boolean newv = Boolean.parseBoolean(e.getNewValue());
            set(newv);
        }
    }

    @Override
    public void loadPreference() {
        set(chip.getPrefs().getBoolean(key, def));
    }

    @Override
    public void storePreference() {
        prefs.putBoolean(key, value); // will eventually call pref change listener which will call set again
    }

    @Override
    public String toString() {
        return "CPLDBit{" + " name=" + name + " pos=" + pos + " value=" + value + '}';
    }
    
}
