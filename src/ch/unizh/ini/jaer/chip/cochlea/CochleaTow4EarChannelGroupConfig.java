/**
 * CochleaChannelGroupConfig.java
 *
 * @author Ilya Kiselev, ilya.fpga@gmail.com
 * 
 * Created on August 7, 2016, 23:38
 */
package ch.unizh.ini.jaer.chip.cochlea;

import java.util.List;
import net.sf.jaer.chip.AEChip;

/** 
 * Represents a configuration register for a cochlea channel. Actual bit fields are defined in MultiBitValue[] parameters
 */
public class CochleaTow4EarChannelGroupConfig extends CochleaTow4EarChannelConfig {

	private final List<CochleaChannelConfig> cochleaChannels;

	public CochleaTow4EarChannelGroupConfig(final String configName, final String toolTip, final List<CochleaChannelConfig> cochleaChannels, AEChip chip) {
		super(configName, toolTip, -1, chip);
		if ((cochleaChannels == null) || cochleaChannels.isEmpty()) {
			throw new IllegalArgumentException("Attempted to create a global control for the empty channel set. " + this);
		}
		this.cochleaChannels = cochleaChannels;
		loadPreference();
	}

	@Override
	public String toString() {
		return String.format("CochleaChannelGroup {configName=%s, prefKey=%s}", getName(), getPreferencesKey());
	}

	@Override
	public synchronized void setPartialValue(final int parameterIdx, final int newValue) {
		super.setPartialValue(parameterIdx, newValue);
		for (final CochleaChannelConfig c : cochleaChannels) {
			c.setPartialValue(parameterIdx, newValue);
		}
		setFileModified();
	}

	@Override
	public void loadPreference() {
		int nFields = CochleaTow4EarChannelConfig.getFieldsNumber();
		int[] mean = new int[nFields];

		for (int i = 0; i < nFields; ++i) {
			mean[i] = 0;
		}
		for (final CochleaChannelConfig c : cochleaChannels) {
			for (int i = 0; i < nFields; ++i) {
				mean[i] += c.getPartialValue(i);
			}
		}
		for (int i = 0; i < nFields; ++i) {
			super.setPartialValue(i, Math.round(mean[i] / cochleaChannels.size()));
		}
	}

	@Override
	public void storePreference() {
	}
}
