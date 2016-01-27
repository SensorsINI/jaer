package ch.unizh.ini.jaer.config.spi;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import ch.unizh.ini.jaer.config.ConfigBit;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;

public class SPIConfigBit extends SPIConfigValue implements ConfigBit {

	private final boolean defaultValue;
	private boolean value;

	private final Biasgen biasgen;
	private final Preferences sprefs;

	public SPIConfigBit(final String configName, final String toolTip, final short moduleAddr, final short paramAddr,
		final boolean defaultValue, final Biasgen biasgen) {
		super(configName, toolTip, (AEChip) biasgen.getChip(), moduleAddr, paramAddr, 1);

		this.defaultValue = defaultValue;

		this.biasgen = biasgen;
		sprefs = biasgen.getChip().getPrefs();

		loadPreference();
		sprefs.addPreferenceChangeListener(this);
	}

	@Override
	public boolean isSet() {
		return value;
	}

	@Override
	public void set(final boolean value) {
		if (this.value != value) {
			setChanged();
		}

		this.value = value;

		notifyObservers();
	}

	@Override
	public String toString() {
		return String.format("SPIConfigBit {configName=%s, prefKey=%s, moduleAddr=%d, paramAddr=%d, numBits=%d, default=%b}", getName(),
			getPreferencesKey(), getModuleAddr(), getParamAddr(), getNumBits(), defaultValue);
	}

	@Override
	public void preferenceChange(final PreferenceChangeEvent e) {
		if (e.getKey().equals(getPreferencesKey())) {
			final boolean newVal = Boolean.parseBoolean(e.getNewValue());
			set(newVal);
		}
	}

	@Override
	public String getPreferencesKey() {
		return biasgen.getChip().getClass().getSimpleName() + "." + getName();
	}

	@Override
	public void loadPreference() {
		set(sprefs.getBoolean(getPreferencesKey(), defaultValue));
	}

	@Override
	public void storePreference() {
		sprefs.putBoolean(getPreferencesKey(), isSet());
	}

	public static void makeSPIBitConfig(final SPIConfigBit bitVal, final JPanel panel, final Map<SPIConfigValue, JComponent> configValueMap,
		final Biasgen biasgen) {
		final JRadioButton but = new JRadioButton("<html>" + bitVal.getName() + ": " + bitVal.getDescription());
		but.setToolTipText("<html>" + bitVal.toString() + "<br>Select to set bit, clear to clear bit.");
		but.setSelected(bitVal.isSet());
		but.setAlignmentX(Component.LEFT_ALIGNMENT);
		but.addActionListener(new SPIConfigBitAction(bitVal));

		panel.add(but);
		configValueMap.put(bitVal, but);
		bitVal.addObserver(biasgen);
	}

	private static class SPIConfigBitAction implements ActionListener {

		private final SPIConfigBit bitConfig;

		SPIConfigBitAction(final SPIConfigBit bitCfg) {
			bitConfig = bitCfg;
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			final JRadioButton button = (JRadioButton) e.getSource();
			bitConfig.set(button.isSelected()); // TODO add undo
			bitConfig.setFileModified();
		}
	}
}
