/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface;

import java.util.prefs.Preferences;
import net.sf.jaer.chip.Chip;

/**
 * Base class for hardware proxies, which are host-side software objects representing hardware state and maintaining preference information about them.
 * Examples are scanners, ADCs. Legacy classes like bias generators are handled separately.
 * @author tobi
 */
public class HardwareInterfaceProxy {

    protected Chip chip;

    /** Preferences should be stored in prefs. */
    private Preferences prefs = null;

    public HardwareInterfaceProxy(Chip chip) {
        this.chip = chip;
        prefs=chip.getPrefs();
    }

    /**
     * @return the prefs
     */
    public Preferences getPrefs() {
        return prefs;
    }

}
