/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.util.prefs.PreferenceChangeEvent;

import javax.swing.JComponent;

import net.sf.jaer.biasgen.AddressedIPot;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;

/**
 * A 6-bit R2R VDAC with 3-bit buffer current control.
 *
 * @author tobi
 */
public class TowerOnChip6BitVDAC extends AddressedIPot {

	public static final float VDD_VOLTAGE = 3.3f;

	protected int vdacBitMask = 0x003F; // 6 bits for level of shifted source
	/**
	 * Bit mask for buffer bias bits
	 */
	protected int bufferBitMask = 0x01C0; // 3 bits for bias current for shifted source buffer amplifier
	/**
	 * Number of bits used for bias value
	 */
	protected int numVdacBits = Integer.bitCount(vdacBitMask);
	/**
	 * The number of bits specifying buffer bias current as fraction of master
	 * bias current
	 */
	protected int numBufferBits = Integer.bitCount(bufferBitMask);
	/**
	 * Maximum buffer bias value (all bits on)
	 */
	public int maxBufBitValue = (1 << numBufferBits) - 1;
	/**
	 * Max bias bit value
	 */
	public int maxVdacBitValue = (1 << numVdacBits) - 1;

	protected int vdacBitValue = 0, bufferBitValue = 0;

	public TowerOnChip6BitVDAC(final Biasgen biasgen) {
		super(biasgen);

	}

	/**
	 * Creates a new instance of IPot
	 *
	 * @param biasgen
	 * @param name
	 * @param address
	 *            the position in the shift register, 0 based, starting on
	 *            end from which bits are loaded
	 * @param displayPosition
	 *            position in GUI from top (logical order)
	 * @param tooltipString
	 *            a String to display to user of GUI telling them what
	 *            the pots does
	 */
	public TowerOnChip6BitVDAC(final Biasgen biasgen, final String name, final int address, final int displayPosition,
		final String tooltipString) {
		this(biasgen);
		numBits = numVdacBits; // should come out 16 bits=2 bytes overrides IPot value of 24
		setName(name);
		this.displayPosition = displayPosition;
		this.tooltipString = tooltipString;
		this.address = address;
		loadPreferences(); // do this after name is set

		// System.out.println(this);
	}

	/**
	 * Builds the component used to control the IPot. This component is the user
	 * interface.
	 *
	 * @return a JComponent that can be added to a GUI
	 */
	@Override
	public JComponent makeGUIPotControl() {
		return new TowerOnChip6BitVDACControl(this);
	}

	public int getVdacBitValue() {
		return vdacBitValue;
	}

	/**
	 * Set the buffer bias bit value
	 *
	 * @param vdacBitValue
	 *            the value of vdac bits
	 */
	public void setVdacBitValue(final int vdacBitValue) {
		final int oldBitValue = this.vdacBitValue;
		this.vdacBitValue = clippedVdacBitValue(vdacBitValue);
		updateBitValue();
		if (vdacBitValue != oldBitValue) {
			setChanged();
			notifyObservers(this);
		}
	}

	public int getBufferBitValue() {
		return bufferBitValue;
	}

	/**
	 * Set the buffer bias bit value
	 *
	 * @param regBitValue
	 *            the value which has maxBufBitValue as maximum and
	 *            specifies fraction of master bias
	 */
	public void setBufferBitValue(final int bufferBitValue) {
		final int oldBitValue = this.bufferBitValue;
		this.bufferBitValue = clippedBufferBitValue(bufferBitValue);
		updateBitValue();
		if (bufferBitValue != oldBitValue) {
			setChanged();
			notifyObservers(this);
		}
	}

	/**
	 * returns clipped value of potential new value for buffer bit value,
	 * constrained by limits of hardware.
	 *
	 * @param o
	 *            candidate new value.
	 * @return allowed value.
	 */
	protected int clippedBufferBitValue(final int o) {
		int n = o; // new value
		if (o < 0) {
			n = 0;
		}
		if (o > maxBufBitValue) {
			n = maxBufBitValue;
		}
		return n;
	}

	/**
	 * returns clipped value of potential new value for buffer bit value,
	 * constrained by limits of hardware.
	 *
	 * @param o
	 *            candidate new value.
	 * @return allowed value.
	 */
	protected int clippedVdacBitValue(final int o) {
		int n = o; // new value
		if (o < 0) {
			n = 0;
		}
		if (o > maxVdacBitValue) {
			n = maxVdacBitValue;
		}
		return n;
	}

	/**
	 * Computes the actual bit pattern to be sent to chip based on configuration
	 * values. The order of the bits from the input end of the shift register is
	 * operating mode config bits, buffer bias current code bits, voltage level
	 * config bits, voltage level code bits.
	 */
	public int computeBinaryRepresentation() {
		final int ret = ((bufferBitMask & (bufferBitValue << Integer.numberOfTrailingZeros(bufferBitMask)))
			| (vdacBitMask & (vdacBitValue << Integer.numberOfTrailingZeros(vdacBitMask)))) & 0xFFFF;

		return ret;
	}

	private byte[] bytes = null;

	/**
	 * returns a byte[] with the short binary representation in big endian order
	 * (MSB to LSB) of the binary representation of the shifted source to be
	 * written to the SPI port. The SPI routine writes bytes in the order passed
	 * from here. The bits in each byte are written in big endian order, msb to
	 * lsb.
	 *
	 * @return byte[] of length 2.
	 */
	@Override
	public byte[] getBinaryRepresentation() {
		final int n = 3; // two plus address
		if (bytes == null) {
			bytes = new byte[n];
		}
		final int val = computeBinaryRepresentation();
		int k = 1;
		for (int i = bytes.length - 2; i >= 0; i--) {
			bytes[k++] = (byte) (0xff & (val >>> (i * 8)));
		}
		bytes[0] = (byte) (0xFF & address);
		return bytes;
	}

	/**
	 * Returns the String key by which this pot is known in the Preferences. For
	 * IPot's, this name is the Chip simple class name followed by
	 * IPot.<potName>, e.g. "Tmpdiff128.IPot.Pr".
	 *
	 * @return preferences key
	 */
	@Override
	protected String prefsKey() {
		return biasgen.getChip().getClass().getSimpleName() + ".VDACBias." + name;
	}

	static String KEY_VDAC_VALUE = "VdacBitValue", KEY_BUFFER_VALUE = "BufBitValue";
	static String SEP = ".";

	@Override
	public void preferenceChange(final PreferenceChangeEvent e) {
		// TODO we get pref change events here but by this time the new values have already been set and there is no
		// change in value so the GUI elements are not updated
		try {
			final String base = prefsKey() + TowerOnChip6BitVDAC.SEP;
			final String key = e.getKey();
			if (!key.startsWith(base)) {
				return;
			}
			final String val = e.getNewValue();
			// log.info("key="+key+" value="+val);
			if (key.equals(base + TowerOnChip6BitVDAC.KEY_VDAC_VALUE)) {
				if (getVdacBitValue() != Integer.parseInt(val)) {
					Pot.log.info("reference voltage bit value change from preferences");
				}
				setVdacBitValue(Integer.parseInt(val));
			}
			else if (key.equals(base + TowerOnChip6BitVDAC.KEY_BUFFER_VALUE)) {
				if (getBufferBitValue() != Integer.parseInt(val)) {
					Pot.log.info("regulator bit value changed from preferences");
				}
				setBufferBitValue(Integer.parseInt(val));
			}
		}
		catch (final Exception ex) {
			Pot.log.warning("while responding to preference change event " + e + ", caught " + ex.toString());
		}
	}

	/**
	 * stores as a preference the bit value
	 */
	@Override
	public void storePreferences() {
		final String s = prefsKey() + TowerOnChip6BitVDAC.SEP;
		prefs.putInt(s + TowerOnChip6BitVDAC.KEY_VDAC_VALUE, getVdacBitValue());
		prefs.putInt(s + TowerOnChip6BitVDAC.KEY_BUFFER_VALUE, getBufferBitValue());
		setModified(false);
	}

	/**
	 * loads and makes active the preference value. The name should be set
	 * before this is called.
	 */
	@Override
	public final void loadPreferences() {
		final String s = prefsKey() + TowerOnChip6BitVDAC.SEP;

		final int bv = prefs.getInt(s + TowerOnChip6BitVDAC.KEY_VDAC_VALUE, 0);
		setVdacBitValue(bv);
		final int bbv = prefs.getInt(s + TowerOnChip6BitVDAC.KEY_BUFFER_VALUE, maxBufBitValue);
		setBufferBitValue(bbv);
		setModified(false);
		updateBitValue();
	}

	/**
	 * returns the preference value
	 */
	@Override
	public int getPreferedBitValue() {
		final String key = prefsKey();
		final int v = prefs.getInt(key, 0);
		return v;
	}

	/**
	 * sets the bit value based on desired current and {@link #masterbias} current. Observers are notified if value
	 * changes.
	 *
	 * @param current
	 *            in amps
	 * @return actual float value of current after resolution clipping.
	 */
	public float setBufferCurrent(final float current) {

		final double im = AddressedIPotCF.ACTUAL_MASTER_BIAS_CURRENT; // TODO real MasterBias
		setBufferBitValue((int) Math.round((Math.log(current / im) / Math.log(8)) + 5));
		return getBufferCurrent();
	}

	/**
	 * Computes the estimated current based on the bit value for the current
	 * splitter and the {@link #masterbias}
	 *
	 * @return current in amps
	 */
	public float getBufferCurrent() {
		final float im = AddressedIPotCF.ACTUAL_MASTER_BIAS_CURRENT; // largest coarse current is about 8^2 times master
																		// current
		final float i = (float) (im * Math.pow(AddressedIPotCF.RATIO_COARSE_CURRENT_STEP, getBufferBitValue() - 5));
		return i;
	}

	/**
	 * sets the bit value based on desired voltage. Observers are notified if
	 * value changes.
	 *
	 * @param voltage
	 *            in volts
	 * @return actual float value of voltage after resolution clipping.
	 */
	public float setVdacVoltage(final float voltage) {
		setVdacBitValue((int) Math.floor((voltage / TowerOnChip6BitVDAC.VDD_VOLTAGE) * maxVdacBitValue));
		return getVdacVoltage();
	}

	/**
	 * Computes the estimated current based on the bit value for the current
	 * splitter and the {@link #masterbias}
	 *
	 * @return current in amps
	 */
	public float getVdacVoltage() {
		final float v = (vdacBitValue * TowerOnChip6BitVDAC.VDD_VOLTAGE) / maxVdacBitValue;
		return v;
	}

	public void updateBitValue() {
		bitValue = (bufferBitValue << (numBufferBits)) + (vdacBitValue << (numVdacBits));
	}

	@Override
	public String toString() {
		return "TowerOnChip6BitVDACControl name=" + name + " vdacBitValue=" + vdacBitValue + " bufferBitValue=" + bufferBitValue;
	}

	/**
	 * return the max value representing all stages of current splitter enabled
	 */
	@Override
	public int getMaxBitValue() {
		return maxVdacBitValue;
	}

	/**
	 * no current: zero
	 */
	@Override
	public int getMinBitValue() {
		return 0;
	}

	/**
	 * Returns true if all parameters are identical, otherwise false.
	 *
	 * @param obj
	 *            another ConfigurableIPotRev0
	 * @return true if all parameters are identical, otherwise false.
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!(obj instanceof TowerOnChip6BitVDAC)) {
			return false;
		}
		final TowerOnChip6BitVDAC other = (TowerOnChip6BitVDAC) obj;
		if (!getName().equals(other.getName())) {
			return false;
		}
		if (getVdacBitValue() != other.getVdacBitValue()) {
			return false;
		}
		if (getBufferBitValue() != other.getBufferBitValue()) {
			return false;
		}
		return true;
	}

}
