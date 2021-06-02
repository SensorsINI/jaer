package net.sf.jaer.hardwareinterface.usb.cypressfx2libusb;

import javax.swing.JOptionPane;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

import org.usb4java.Device;

/**
 * Adds biasgen functionality to base interface via Cypress FX2.
 *
 * @author tobi
 */
public class CypressFX2Biasgen extends CypressFX2 implements BiasgenHardwareInterface {

	/**
	 * max number of bytes used for each bias. For 24-bit biasgen, only 3 bytes are used, but we oversize considerably
	 * for the future.
	 */
	public static final int MAX_BYTES_PER_BIAS = 8;

	/**
	 * Creates a new instance of CypressFX2Biasgen. Note that it is possible to construct several instances
	 * and use each of them to open and read from the same device.
	 *
	 * @param devNumber
	 *            the desired device number, in range returned by CypressFX2Factory.getNumInterfacesAvailable
	 * @see CypressFX2TmpdiffRetinaFactory
	 */
	protected CypressFX2Biasgen(final Device device) {
		super(device);
	}

	/*
	 * sets the powerdown input pin to the biasgenerator.
	 * Chip may have been plugged in without being
	 * powered up. To ensure the biasgen is powered up, a negative transition is necessary. This transistion is
	 * necessary to ensure the startup circuit starts up the masterbias again.
	 *
	 * if this method is called from a GUI is may be desireable to actually toggle the powerdown pin high and then low
	 * to ensure the chip is powered up.
	 * otherwise it doesn't make sense to always toggle this pin because it will perturb the chip operation
	 * significantly.
	 * For instance, it should not be called very time new bias values are sent.
	 *
	 * @param powerDown true to power OFF the biasgen, false to power on
	 */
	@Override
	synchronized public void setPowerDown(final boolean powerDown) throws HardwareInterfaceException {
		setPowerDownSingle(powerDown);
	}

	synchronized private void setPowerDownSingle(final boolean powerDown) throws HardwareInterfaceException {
		if (deviceHandle == null) {
			throw new RuntimeException("device must be opened before sending this vendor request");
		}

		sendVendorRequest(VENDOR_REQUEST_POWERDOWN, (short) ((powerDown) ? (1) : (0)), (short) 0);
	}

	/**
	 * sends the ipot values.
	 *
	 * @param biasgen
	 *            the biasgen which has the values to send
	 */
	@Override
	synchronized public void sendConfiguration(final net.sf.jaer.biasgen.Biasgen biasgen)
		throws HardwareInterfaceException {
		if (deviceHandle == null) {
			try {
				open();
			}
			catch (final HardwareInterfaceException e) {
				CypressFX2.log.warning(e.getMessage());
				return; // may not have been constructed yet.
			}
		}

		if (biasgen.getPotArray() == null) {
			CypressFX2.log.warning("BiasgenUSBInterface.send(): potArray=null");
			return; // may not have been constructed yet.
		}

		final byte[] toSend = formatConfigurationBytes(biasgen);
		sendBiasBytes(toSend);
		HardwareInterfaceException.clearException();

	}

	/**
	 * Sends bytes with vendor request that signals these are bias (or other configuration) values.
	 * These are sent as control transfers which have a maximum data packet size of 64 bytes.
	 * If there are more than 64 bytes worth of bias data,
	 * then the transfer must be (and is automatically)
	 * split up into several control transfers and the
	 * bias values can only be latched on-chip when all of the bytes have been sent.
	 *
	 * @param b
	 *            bias bytes to clock out SPI interface
	 * @see CypressFX2#VENDOR_REQUEST_SEND_BIAS_BYTES
	 */
	synchronized public void sendBiasBytes(final byte[] b) throws HardwareInterfaceException {
		if (deviceHandle == null) {
			CypressFX2.log.warning("null gUsbIo, device must be opened before sending this vendor request");
			return;
		}

		if ((b == null) || (b.length == 0)) {
			CypressFX2.log.warning("null or empty bias byte array supplied");
			return;
		}

		sendVendorRequest(CypressFX2.VENDOR_REQUEST_SEND_BIAS_BYTES, (short) 0, (short) 0, b, 0, b.length);
	}

	@Override
	synchronized public void flashConfiguration(final Biasgen biasgen) throws HardwareInterfaceException {
		JOptionPane.showMessageDialog(null, "Flashing biases not yet supported on CypressFX2");
	}

	/**
	 * This implementation delegates the job of getting the bytes to send to the Biasgen object.
	 * Depending on the hardware interface, however, it may be that a particular subclass of this
	 * should override formatConfigurationBytes to return a different set of data.
	 *
	 * @param biasgen
	 *            the source of configuration information.
	 * @return the bytes to send
	 */
	@Override
	public byte[] formatConfigurationBytes(final Biasgen biasgen) {
		final byte[] b = biasgen.formatConfigurationBytes(biasgen);
		return b;
	}

}
