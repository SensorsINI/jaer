/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.config;

import java.util.Observable;
import java.util.prefs.PreferenceChangeListener;

import net.sf.jaer.biasgen.Biasgen.HasPreference;
import net.sf.jaer.chip.AEChip;

public abstract class AbstractConfigValue extends Observable implements PreferenceChangeListener, HasPreference {

	private final String configName, toolTip, prefKey;
	protected final AEChip chip;

	public AbstractConfigValue(final String configName, final String toolTip, final AEChip chip) {
		this.configName = configName;
		this.toolTip = toolTip;
		prefKey = getClass().getSimpleName() + "." + configName;
		this.chip = chip;
	}

	public String getName() {
		return configName;
	}

	public String getDescription() {
		return toolTip;
	}

	public String getPreferencesKey() {
		return prefKey;
	}

	@Override
	public String toString() {
		return String.format("AbstractConfigValue {configName=%s, prefKey=%s}", getName(), getPreferencesKey());
	}

	// Modify accessibility level from private to public 
	@Override
	public synchronized void setChanged() {
		super.setChanged();
	}

	public void setFileModified() {
		if ((chip != null) && (chip.getAeViewer() != null) && (chip.getAeViewer().getBiasgenFrame() != null)) {
			chip.getAeViewer().getBiasgenFrame().setFileModified(true);
		}
	}
}
