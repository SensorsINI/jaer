/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;

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
	public Davis240Config(final Chip chip) {
		super(chip);
		setName("Davis240Config");

		final SPIConfigBit specialPixelControl = new SPIConfigBit("Chip.SpecialPixelControl",
			"<html>DAVIS240A: enable experimental hot pixel suppression circuit. <p>DAVIS240B: enable experimental pixel stripes on right side of array. <p>DAVIS240C: no effect.",
			CypressFX3.FPGA_CHIPBIAS, (short) 139, false, this);
		chipControl.add(specialPixelControl);
		specialPixelControl.addObserver(this);
		allPreferencesList.add(specialPixelControl);
	}
}
