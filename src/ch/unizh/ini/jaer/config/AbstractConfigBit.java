/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config;

import java.awt.event.ActionEvent;
import java.util.prefs.PreferenceChangeEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.Chip;

/**
 * Base class for configuration bits - both originating from USB chip and CPLD logic chip.
 * @author tobi
 */
public class AbstractConfigBit extends AbstractConfigValue implements ConfigBit, HasPreference {

    protected volatile boolean value;
    protected boolean def; // default preference value
    /** This Action can be used in GUIs */
    private SelectAction action = new SelectAction();

    // default preference value
    public AbstractConfigBit(Chip chip, String name, String tip, boolean def) {
        super(chip, name, tip);
        this.def = def;
        loadPreference();
        prefs.addPreferenceChangeListener(this);
        action.putValue(Action.SHORT_DESCRIPTION, tip);
        action.putValue(Action.NAME, name);
        action.putValue(Action.SELECTED_KEY,value);
    }

    /** Sets the value and notifies observers if it changes.
     * 
     * @param value the new value
     */
    @Override
    public void set(boolean value) {
        if (this.value != value) {
            setChanged();
        }
        this.value = value;
        action.putValue(Action.SELECTED_KEY, value);
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

    /**
     * @return the action
     */
    public SelectAction getAction() {
        return action;
    }

    /**
     * @param action the action to set
     */
    public void setAction(SelectAction action) {
        this.action = action;
    }

    /** This action toggles the bit */
    public class SelectAction extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent e) {
            set(!value);
        }
    }
}
