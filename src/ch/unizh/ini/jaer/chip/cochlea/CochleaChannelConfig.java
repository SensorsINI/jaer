/**
 * CochleaChannelConfig.java
 *
 * @author Ilya Kiselev, ilya.fpga@gmail.com
 * 
 * Created on June 29, 2016, 3:38
 */
package ch.unizh.ini.jaer.chip.cochlea;

import ch.unizh.ini.jaer.config.MultiBitConfigRegister;
import ch.unizh.ini.jaer.config.MultiBitValue;
import net.sf.jaer.chip.AEChip;

/** 
 * Represents a configuration register for a cochlea channel. Actual bit fields are defined in MultiBitValue[] parameters
 */
public abstract class CochleaChannelConfig extends MultiBitConfigRegister {

	protected final int channelAddress;

	public CochleaChannelConfig(final MultiBitValue[] parameters, final String configName, final String toolTip, final int channelAddress, final AEChip chip) {
		super(parameters, configName, toolTip, chip);
		this.channelAddress = channelAddress;
	}

	public int getChannelAddress() {
		return channelAddress;
	}
}
