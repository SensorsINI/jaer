package ch.unizh.ini.jaer.config.spi;

import ch.unizh.ini.jaer.config.AbstractConfigValue;
import net.sf.jaer.chip.AEChip;

public abstract class SPIConfigValue extends AbstractConfigValue {

	private final short moduleAddr, paramAddr;
	private final int numBits;

	public SPIConfigValue(final String configName, final String toolTip, final AEChip chip, final short moduleAddr, final short paramAddr,
		final int numBits) {
		super(configName, toolTip, chip);

		this.moduleAddr = moduleAddr;
		this.paramAddr = paramAddr;
		this.numBits = numBits;
	}

	public short getModuleAddr() {
		return moduleAddr;
	}

	public short getParamAddr() {
		return paramAddr;
	}

	public int getNumBits() {
		return numBits;
	}

	@Override
	public String toString() {
		return String.format("SPIConfigValue {configName=%s, prefKey=%s, moduleAddr=%d, paramAddr=%d, numBits=%d}", getName(),
			getPreferencesKey(), getModuleAddr(), getParamAddr(), getNumBits());
	}
}
