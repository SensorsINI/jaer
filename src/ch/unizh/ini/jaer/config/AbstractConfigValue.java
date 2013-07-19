/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config;

import java.util.ArrayList;
import java.util.Observable;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.Chip;

/**
 * Top level configuration value. This is an Observable so changes to the value will inform Observers listening to this.
 * 
 * @author tobi
 */
public abstract class AbstractConfigValue extends Observable implements PreferenceChangeListener, HasPreference {
    protected String name;
    protected String tip;
    protected String key = "AbstractConfigValue";
    final protected Preferences prefs;
    protected Chip chip;

    /**
     * Creates new abstract value.
     * 
     * @param chip from where we get Preferences
     * @param name name of value
     * @param tip tooltip
     */
    public AbstractConfigValue(Chip chip, String name, String tip) {
        this.name = name;
        this.tip = tip;
        this.chip=chip;
        this.prefs=chip.getPrefs();
        this.key = chip.getClass().getSimpleName() + "." + name;
    }

    @Override
    public String toString() {
        return String.format("AbstractConfigValue name=%s key=%s", name, key);
    }

    public String getName() {
        return name;
    }

    @Override
    public synchronized void setChanged() {
        super.setChanged();
    }

    public String getDescription() {
        return tip;
    }
    
    public void addToPreferenceList(ArrayList<HasPreference> hasPreferencesList){
        hasPreferencesList.add(this);
    }
    
}
