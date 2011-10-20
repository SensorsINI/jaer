/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.config;

import eu.seebetter.ini.chips.config.ConfigBit;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;
import net.sf.jaer.chip.Chip;

/**
 * Base class for configuration bits - both originating from USB chip and CPLD logic chip.
 * @author tobi
 */
public class AbstractConfigBit extends AbstractConfigValue implements ConfigBit {
    protected volatile boolean value;
    protected boolean def; // default preference value

    // default preference value
    public AbstractConfigBit(Chip chip, String name, String tip, boolean def) {
        super(chip, name, tip);
        this.def = def;
        loadPreference();
        prefs.addPreferenceChangeListener(this);
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
        set(prefs.getBoolean(key, def));
    }

    @Override
    public void storePreference() {
        prefs.putBoolean(key, value); // will eventually call pref change listener which will call set again
    }

    @Override
    public String toString() {
        return String.format("AbstractConfigBit name=%s key=%s value=%s", name, key, value);
    }
    
}
