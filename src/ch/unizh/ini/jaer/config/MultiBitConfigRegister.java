/**
 * MultiBitConfigRegister.java
 *
 * @author Ilya Kiselev, ilya.fpga@gmail.com
 * 
 * Created on June 27, 2016, 16:05
 */

package ch.unizh.ini.jaer.config;

import java.util.prefs.PreferenceChangeEvent;
import net.sf.jaer.chip.AEChip;
//import java.util.logging.Logger;

public class MultiBitConfigRegister extends AbstractConfigValue implements ConfigBase {

	private final int configWordLength;
	private int configWord;
	private final MultiBitValue[] parameters;
	// TODO: we can update only those controls which were really changed
	// Set corestonding bits in this word in setPartialValue() and clear all in updateControl()
	// private int fieldsNeedUpdate;

	public MultiBitConfigRegister(final MultiBitValue[] parameters, final String configName, final String toolTip, final AEChip chip) {
		super(configName, toolTip, chip);
		if ((parameters == null) || (parameters.length == 0)) {
			throw new IllegalArgumentException("Attempted to create a register with undefined field structure: " + this);
		}
		this.parameters = parameters;
		int regLength = 0;
		for (MultiBitValue v : parameters) {
			regLength += v.length;
		}
		configWordLength = regLength;
		//loadPreference();
		chip.getPrefs().addPreferenceChangeListener(this);
		addObserver(this);		// Self observer for the GUI update
	}

	/**
	 * Set value of a partial parameter in the channel config word.
	 *
	 * @param parameterIdx
	 *            0-based index of the parameter.
	 * @param newValue
	 *            new value for the parameter.
	 */
	public synchronized void setPartialValue(final int parameterIdx, final int newValue) {
		if (parameterIdx + 1 > parameters.length) {
			throw new IllegalArgumentException("Attempted to set unknown partial parameter #" + parameterIdx + " in " + this);
		}
		setFullValue(parameters[parameterIdx].setValue(configWord, newValue));
	}

	/**
	 * Returns binary encoded full state value.
	 *
	 * @return
	 */
	public synchronized int getFullValue() {
		return configWord;
	}

	/**
	 * Returns integer value of the specified parameter.
	 *
	 * @param parameterIdx
	 *     0-based index of the parameter.
	 *
	 * @return
	 */
	public synchronized int getPartialValue(final int parameterIdx) {
		return parameters[parameterIdx].getValue(configWord);
	}

	/*
	public synchronized boolean fieldNeedsUpdate(final int parameterIdx) {
		return (parameters[parameterIdx].getValue(fieldsNeedUpdate) == 1);
	}
	*/

	/**
	 * Sets binary encoded full state value, and calls Observers if value is
	 * changed.
	 *
	 * @param fullValue
	 */
	public synchronized void setFullValue(final int fullValue) {
		if (configWord != fullValue) {
			checkValueLimits(fullValue, configWordLength);
			//log.fine("binary full value of " + this.toString() + " changed from " + configWord + " to " + fullValue + ", notifying observers");
			setChanged();
			configWord = fullValue;
			notifyObservers();
		}
	}

	private void checkValueLimits(final int value, final int maxLength) {
		if ((value < 0) || (value >= (1 << maxLength))) {
			throw new IllegalArgumentException("Attempted to store value=" + value + ", which is larger than the maximum permitted value of " + ((1 << maxLength) - 1) + " or is negative, in " + this);
		}
	}

	public int computeBinaryRepresentation() {
		return getFullValue();
	}

	public int fieldsNumber() {
		return parameters.length;
	}

	public MultiBitValue fieldConfig(int i) {
		return parameters[i];
	}

	@Override
	public void preferenceChange(final PreferenceChangeEvent e) {
		if (e.getKey().equals(getPreferencesKey())) {
			final int newVal = Integer.parseInt(e.getNewValue());
			setFullValue(newVal);
		}
	}

	@Override
	public void loadPreference() {
		setFullValue(chip.getPrefs().getInt(getPreferencesKey(), 0));
	}

	@Override
	public void storePreference() {
		chip.getPrefs().putInt(getPreferencesKey(), getFullValue());
	}

	@Override
	public void updateControl() {
		if (control != null) {
			((AbstractMultiBitRegisterCP) control).updateGUI();
		}
	}

	/**
	 * @return the control panel
	 */
	public AbstractMultiBitRegisterCP getControlPanel() {
		return (AbstractMultiBitRegisterCP) control;
	}

	/**
	 * @param controlPanel
	 *     the control panel to set
	 */
	public void setControlPanel(final AbstractMultiBitRegisterCP controlPanel) {
		setControl(controlPanel);
	}
}