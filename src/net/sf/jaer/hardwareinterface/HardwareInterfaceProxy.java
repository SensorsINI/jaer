/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface;

import java.util.Observable;
import java.util.prefs.Preferences;

import net.sf.jaer.chip.Chip;

/**
 * Base class for hardware proxies, which are host-side software objects representing hardware state and maintaining preference information about them.
 * Examples are scanners, ADCs. Chip/board level configuration of a device can use this proxy by encapsulating some state in the proxy, and registering 
 * an update listener on it in the Chip's bias generator object. 
 * These updates can then cause communication with the hardware through the Chip's bias generator object.
 * Preferences are stored in the Chip's preferences node, which is a field of this.
 * @author tobi
 */
public class HardwareInterfaceProxy extends Observable{

    /** The Chip object is the central object in jAER. */
    protected Chip chip;

    /** Preferences should be stored in prefs. */
    protected Preferences prefs = null;

    /** Constructs new proxy, using chip to obtain Preferences node
     * 
     * @param chip 
     */
    public HardwareInterfaceProxy(Chip chip) {
        this.chip = chip;
        prefs=chip.getPrefs();
    }

    /** Returns the Preferences object in which state is stored.
     * @return the preferences node.
     */
    public Preferences getPrefs() {
        return prefs;
    }

    /** Notifies observers there is a change after calling setChanged()
     * 
     * @param arg - some object, typically a static final String event.
     */
    protected void notifyChange(Object arg) {
        setChanged();
        notifyObservers(arg);
    }

}
