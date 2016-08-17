/**
 * MultiBitValue.java
 *
 * @author Ilya Kiselev, ilya.fpga@gmail.com
 * 
 * Created on June 27, 2016, 17:24
 */

package ch.unizh.ini.jaer.config;

/**
 * Class represents partial multi-bit parameter in a 32-bit configuration word.
 * 
 * The method int setValue(int configWord, int newValue) is used to update
 * the whole configuration word with the new value of the parameter
 */
public class MultiBitValue {

	public final byte length;
	private final byte position;
	private final String toolTip;

	/**
	 * Create a partial multi-bit parameter in the channel config word.
	 *
	 * @param length
	 *     Bit-length of the parameter.
	 * @param position
	 *     0-based bit-index of the parameter.
	 * @param toolTip
	 *     toolTip.
	 */
	public MultiBitValue(final int length, final int position, final String toolTip) {
		if ((length <= 0) || (length > 32)) {
			throw new IllegalArgumentException("Attempted to set length=" + length + ", which is larger than the maximum permitted value of 32 or is less than 1, in " + this);
		}
		if ((position < 0) || (position > 31)) {
			throw new IllegalArgumentException("Attempted to set position=" + position + ", which is larger than the maximum permitted value of 31 or is negative, in " + this);
		}
		this.length = (byte) length;
		this.position = (byte) position;
		this.toolTip = toolTip;
	}

	public int getValue(int configWord) {
		return (configWord >>> position) & ((1 << length) - 1);
	}

	public int setValue(int configWord, int newValue) {
		if ((newValue < 0) || (newValue >= (1 << length))) {
			throw new IllegalArgumentException("Attempted to store value=" + newValue + ", which is larger than the maximum permitted value of " + ((1 << length) - 1) + " or is negative, in " + this);
		}
		int configWordClean = configWord & ~(((1 << length) - 1) << position);	// Clear corresponding bits
		return configWordClean | (newValue << position);
	}

	public String getToolTip() {
		return toolTip;
	}
}