/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import net.sf.jaer.chip.Chip;
import ch.unizh.ini.jaer.config.onchip.OnchipConfigBit;

/**
 * Bias generator, On-chip diagnostic readout, video acquisition and rendering
 * controls for the Davis240 vision sensors.
 *
 * @author Christian/Tobi
 */
public class Davis240Config extends DavisConfig {

	/**
	 * Creates a new instance of chip configuration
	 *
	 * @param chip
	 *            the chip this configuration belongs to
	 */
	public Davis240Config(Chip chip) {
		super(chip);
		setName("Davis240Config");

		// on-chip configuration chain, use new one.
		chipConfigChain.deleteObservers();
		chipConfigChain = new Davis240ChipConfigChain(chip);
		chipConfigChain.addObserver(this);
	}

	public class Davis240ChipConfigChain extends DavisChipConfigChain {
		OnchipConfigBit specialPixelControl = new OnchipConfigBit(
			chip,
			"SpecialPixelControl",
			3,
			"<html>DAVIS240a: enable hot pixel suppression circuit. <p>DAViS240b: enable experimental pixel stripes on right side of array. <p>DAViS240c: no effect.",
			false);

		public Davis240ChipConfigChain(Chip chip) {
			super(chip);

			configBits[3] = specialPixelControl;
			configBits[3].addObserver(this);
		}
	}
}
