/*
 * StereoBiassgenHardwareInterface.java
 *
 * Created on March 19, 2006, 9:00 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 * CopyaemonRight March 28, 2011 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.stereopsis;

import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.event.MultiCameraEvent;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;

/**
 * Duplicates the hardware interface to a single bias generator to control multiple chips each with it's own hardware
 * interface.
 *
 * @author tobi
 */
public class MultiCameraBiasgenHardwareInterface extends MultiCameraHardwareInterface implements BiasgenHardwareInterface {
        
        final public int NUM_CAMERAS=MultiCameraHardwareInterface.NUM_CAMERAS; 
	protected BiasgenHardwareInterface[] biasgens = new BiasgenHardwareInterface[NUM_CAMERAS]; // TODO

	/**
	 * Creates a new instance of MultiCameraBiasgenHardwareInterface.
	 */
	public MultiCameraBiasgenHardwareInterface(AEMonitorInterface[] aemons) {
		super(aemons);
		int ind = 0;
		for (AEMonitorInterface aemon : aemons) {
			if (aemon instanceof BiasgenHardwareInterface) {
				biasgens[ind++] = (BiasgenHardwareInterface) aemon;
			}
		}
	}

	public BiasgenHardwareInterface[] getBiasgens() {
		return (biasgens);
	}

	/**
	 * Overrides the super method to set powerdown for all chips.
	 *
	 * @param powerDown
	 *            true to power OFF the biasgen, false to power on
	 */
	@Override
	public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
		for (BiasgenHardwareInterface b : biasgens) {
			if (b == null) {
				continue; // continue silently with null interfaces
			}
			b.setPowerDown(powerDown);
		}
	}

	/** sends the ipot values. */
	@Override
	public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
		for (BiasgenHardwareInterface b : biasgens) {
			if (b == null) {
				continue; // continue silently with null interfaces
			}
			b.sendConfiguration(biasgen);
		}
	}

	/** flashes the biases in non-volatile storage so they will be reloaded on reset or powerup */
	@Override
	public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
		for (BiasgenHardwareInterface b : biasgens) {
			if (b == null) {
				continue; // continue silently with null interfaces
			}
			b.flashConfiguration(biasgen);
		}
	}

	/**
	 * returns the bytes from the first elements of multiple interfaces
	 *
	 * @param biasgen
	 * @return byte array of configuration
	 */
	@Override
	public byte[] formatConfigurationBytes(Biasgen biasgen) {
		return biasgens[0].formatConfigurationBytes(biasgen);
	}
}
