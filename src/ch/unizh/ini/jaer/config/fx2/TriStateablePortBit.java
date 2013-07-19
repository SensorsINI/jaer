/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config.fx2;

import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.config.ConfigTristate;
import ch.unizh.ini.jaer.config.Tristate;

/** 
 * Adds a hiZ state to the PortBit. A PortBit is a direct port output from the controller, as opposed to a CPLD bit.
 *
 * @author tobi
 */
public class TriStateablePortBit extends PortBit implements ConfigTristate, HasPreference {
    volatile boolean hiZEnabled = false;
    String hiZKey;
    Tristate def;

    public TriStateablePortBit(Chip chip, String portBit, String name, String tip, Tristate def) {
        super(chip, portBit, name, tip, def.isHigh());
        this.def = def;
        hiZKey = "CochleaAMS1c.Biasgen.BitConfig." + name + ".hiZEnabled";
        loadPreference();
    }

    /**
     * @return the hiZEnabled
     */
    @Override
    public boolean isHiZ() {
        return hiZEnabled;
    }

    /**
     * @param hiZEnabled the hiZEnabled to set
     */
    @Override
    public void setHiZ(boolean hiZEnabled) {
        if (this.hiZEnabled != hiZEnabled) {
            setChanged();
        }
        this.hiZEnabled = hiZEnabled;
        notifyObservers();
    }

    @Override
    public String toString() {
        return String.format("TriStateablePortBit name=%s portbit=%s value=%s hiZEnabled=%s", name, portBitString, Boolean.toString(isSet()), hiZEnabled);
    }

    @Override
    public void loadPreference() {
        super.loadPreference();
        setHiZ(prefs.getBoolean(key, def.isHiZ()));
    }

    @Override
    public void storePreference() {
        super.storePreference();
        prefs.putBoolean(key, hiZEnabled); // will eventually call pref change listener which will call set again
    }
    
}
