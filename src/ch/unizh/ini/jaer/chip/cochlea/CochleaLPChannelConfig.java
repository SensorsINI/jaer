/**
 * CochleaLPChannelConfig.java
 *
 * @author Ilya Kiselev, ilya.fpga@gmail.com
 * 
 * Created on August 7, 2016, 22:23
 */
package ch.unizh.ini.jaer.chip.cochlea;

import ch.unizh.ini.jaer.config.MultiBitValue;
import net.sf.jaer.chip.AEChip;

/** 
 * Represents a configuration register for the CochleaLP channel. Actual bit fields are defined in MultiBitValue[] parameters
 */
public class CochleaLPChannelConfig extends CochleaChannelConfig {
	private static final MultiBitValue[] CHANNEL_FIELDS;

	// Create the structure of the cochlea channel
	static {
		CHANNEL_FIELDS = new MultiBitValue[6];
		// comparatorSelfOscillationEnable
		CHANNEL_FIELDS[0] = new MultiBitValue(1, 19, 
			"Comparator self-oscillation enable. i.e. spike generation enable 1 bit, select to turn on");
		// delayCapConfigADM
		CHANNEL_FIELDS[1] = new MultiBitValue(3, 16, 
			"Delay cap configuration in ADM. Reset signal φrst pulse width control 3bits, 0 is shortest delay, if pulse is too short then ADM does not function correctly");
		// resetCapConfigADM
		CHANNEL_FIELDS[2] = new MultiBitValue(2, 14, 
			"Reset cap configuration in ADM. ADM reset level control, 2bits, 0 is lowest subtraction 1delta, 1 is 1.5delta, 2 is 2delta, 3 is max subtraction 2.5delta, used to compensate slope overload and delay");
		// lnaGainConfig
		CHANNEL_FIELDS[3] = new MultiBitValue(3, 11, 
			"PGA gain configuration. PGA gain control 3 bits, effectively 4 levels, uses only values 0,1,3,7. 0 is highest gain (40dB designed), 7 is lowest (18dB designed??)");
		// attenuatorConfig
		CHANNEL_FIELDS[4] = new MultiBitValue(3, 8, 
			"Attenuator configuration. Attenuation level control 3 bits, 8 levels, 7 is no attenuation, 0 is -18dB");
		// qTuning
		CHANNEL_FIELDS[5] = new MultiBitValue(8, 0, 
			"QTuning configuration. Q control, 8 bits. 255 is lowest Q, 0 is highest (unstable). Reduce to increase effective Q");
	}

	public CochleaLPChannelConfig(final String configName, final String toolTip, final int channelAddress, final AEChip chip) {
		super(CHANNEL_FIELDS, configName, toolTip, channelAddress, chip);
	}

	@Override
	public String toString() {
		return String.format("CochleaLPChannel {configName=%s, prefKey=%s, channelAddress=%d}", getName(), getPreferencesKey(), channelAddress);
	}

	// CochleaChannelConfig.GetFields interface implementation
	public static int getFieldsNumber() {
		return CHANNEL_FIELDS.length;
	}

	public static int[] getFieldsLengths() {
		final int[] fieldsLengths = new int[CHANNEL_FIELDS.length];
		for (int i = 0; i < CHANNEL_FIELDS.length; ++i) {
			fieldsLengths[i] = CHANNEL_FIELDS[i].length;
		}
		return fieldsLengths;
	}

	public static MultiBitValue getFieldConfig(int i) {
		return CHANNEL_FIELDS[i];
	}
}
