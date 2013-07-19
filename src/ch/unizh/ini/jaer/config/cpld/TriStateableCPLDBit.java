/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.cpld;

import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.config.ConfigTristate;
import ch.unizh.ini.jaer.config.Tristate;

/**
 *
 * @author tobi
 */
public class TriStateableCPLDBit extends CPLDBit implements ConfigTristate, HasPreference {
    int hiZBit;
    volatile boolean hiZEnabled = false;
    String hiZKey;
    Tristate def;

    /** Make a new tristatable output from the CPLD.
     *
     * @param valBit the location of the boolean value
     * @param hiZBit location of tristate bit
     * @param name symbolic name
     * @param tip tooltip
     * @param def default Tristate value
     * @param chip gets preferences from the Chip
     */
    public TriStateableCPLDBit(Chip chip, int valBit, int hiZBit, String name, String tip, Tristate def) {
        super(chip, valBit,name,tip,def.isHigh());
        this.def = def;
        this.hiZBit = hiZBit;
        hiZKey = super.key.concat("TriStateableCPLDBit." + name + ".hiZEnabled");
        loadPreference();
    }

    /**
     * @return the hiZEnabled
     */
    public boolean isHiZ() {
        return hiZEnabled;
    }

    /**
     * @param hiZEnabled the hiZEnabled to set
     */
    public void setHiZ(boolean hiZEnabled) {
        if (this.hiZEnabled != hiZEnabled) {
            setChanged();
        }
        this.hiZEnabled = hiZEnabled;
        notifyObservers();
    }

    @Override
    public String toString() {
        return String.format("TriStateableCPLDBit name=%s shiftregpos=%d value=%s hiZ=%s", name, pos, Boolean.toString(isSet()), hiZEnabled);
    }

    @Override
    public void loadPreference() {
        super.loadPreference();
        setHiZ(prefs.getBoolean(key, false));
    }

    @Override
    public void storePreference() {
        super.storePreference();
        prefs.putBoolean(key, hiZEnabled); // will eventually call pref change listener which will call set again
    }
    
}
