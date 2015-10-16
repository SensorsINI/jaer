/*
 * CypressFX3Biasgen.java
 *
 * Created on 23 Jan 2008
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.usb4java.Device;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Adds functionality of apsDVS sensors to based CypressFX3Biasgen class. The
 * key method is translateEvents that parses
 * the data from the sensor to construct jAER raw events.
 *
 * @author Christian/Tobi
 */
public class CochleaFX3HardwareInterface extends CypressFX3Biasgen {

	protected CochleaFX3HardwareInterface(final Device device) {
		super(device);
	}

	/** The USB product ID of this device */
	static public final short PID = (short) 0x841C;
	static public final int REQUIRED_FIRMWARE_VERSION = 2;
	static public final int REQUIRED_LOGIC_REVISION = 0;

	/**
	 * Starts reader buffer pool thread and enables in endpoints for AEs. This
	 * method is overridden to construct
	 * our own reader with its translateEvents method
	 */
	@Override
	public void startAEReader() throws HardwareInterfaceException {
		setAeReader(new RetinaAEReader(this));
		allocateAEBuffers();

		getAeReader().startThread(); // arg is number of errors before giving up
		HardwareInterfaceException.clearException();
	}

	public static final int CHIP_COCHLEALP = 11;
	public static final int CHIP_COCHLEA4EAR = 12;
	public static final int CHIP_SAMPLEPROB = 13; // Reuse Cochlea code for SampleProb chip.

	/**
	 * This reader understands the format of raw USB data and translates to the
	 * AEPacketRaw
	 */
	public class RetinaAEReader extends CypressFX3.AEReader implements PropertyChangeListener {
		private final int chipID;

		private final int aerMaxAddress;

		private int wrapAdd;
		private int lastTimestamp;
		private int currentTimestamp;

		public RetinaAEReader(final CypressFX3 cypress) throws HardwareInterfaceException {
			super(cypress);

			// Verify device firmware version and logic revision.
			final int usbFWVersion = getDID() & 0x00FF;
			if (usbFWVersion < CochleaFX3HardwareInterface.REQUIRED_FIRMWARE_VERSION) {
				final String updateString = String.format(
					"Device firmware version too old. You have version %d; but at least version %d is required. Please updated by following the Flashy upgrade documentation at 'https://goo.gl/TGM0w1'.",
					usbFWVersion, CochleaFX3HardwareInterface.REQUIRED_FIRMWARE_VERSION);

				final SwingWorker<Void, Void> strWorker = new SwingWorker<Void, Void>() {
					@Override
					public Void doInBackground() {
						JOptionPane.showMessageDialog(null, updateString);

						return (null);
					}
				};
				strWorker.execute();

				throw new HardwareInterfaceException(updateString);
			}

			final int logicRevision = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 0);
			if (logicRevision < CochleaFX3HardwareInterface.REQUIRED_LOGIC_REVISION) {
				final String updateString = String.format(
					"Device logic revision too old. You have revision %d; but at least revision %d is required. Please updated by following the Flashy upgrade documentation at 'https://goo.gl/TGM0w1'.",
					logicRevision, CochleaFX3HardwareInterface.REQUIRED_LOGIC_REVISION);

				final SwingWorker<Void, Void> strWorker = new SwingWorker<Void, Void>() {
					@Override
					public Void doInBackground() {
						JOptionPane.showMessageDialog(null, updateString);

						return (null);
					}
				};
				strWorker.execute();

				throw new HardwareInterfaceException(updateString);
			}

			chipID = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 1);

			if (chipID == CochleaFX3HardwareInterface.CHIP_COCHLEALP) {
				aerMaxAddress = 256;
			}
			else if (chipID == CochleaFX3HardwareInterface.CHIP_COCHLEA4EAR) {
				aerMaxAddress = 1024;
			}
			else {
				// CHIP_SAMPLEPROB
				aerMaxAddress = 16;
			}
		}

		private void checkMonotonicTimestamp() {
			if (currentTimestamp <= lastTimestamp) {
				CypressFX3.log.severe(toString() + ": non strictly-monotonic timestamp detected: lastTimestamp=" + lastTimestamp
					+ ", currentTimestamp=" + currentTimestamp + ", difference=" + (lastTimestamp - currentTimestamp) + ".");
			}
		}

		private boolean ensureCapacity(final AEPacketRaw buffer, final int capacity) {
			if (buffer.getCapacity() > getAEBufferSize()) {
				if (buffer.overrunOccuredFlag || (capacity > buffer.getCapacity())) {
					buffer.overrunOccuredFlag = true;
					return (false);
				}

				return (true);
			}

			buffer.ensureCapacity(capacity);
			return (true);
		}

		@Override
		protected void translateEvents(final ByteBuffer b) {
			synchronized (aePacketRawPool) {
				final AEPacketRaw buffer = aePacketRawPool.writeBuffer();

				// Truncate off any extra partial event.
				if ((b.limit() & 0x01) != 0) {
					CypressFX3.log.severe(b.limit() + " bytes received via USB, which is not a multiple of two.");
					b.limit(b.limit() & ~0x01);
				}

				buffer.lastCaptureIndex = eventCounter;

				final ShortBuffer sBuf = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

				for (int i = 0; i < sBuf.limit(); i++) {
					final short event = sBuf.get(i);

					// Check if timestamp
					if ((event & 0x8000) != 0) {
						// Is a timestamp! Expand to 32 bits. (Tick is 1us already.)
						lastTimestamp = currentTimestamp;
						currentTimestamp = wrapAdd + (event & 0x7FFF);

						// Check monotonicity of timestamps.
						checkMonotonicTimestamp();
					}
					else {
						// Look at the code, to determine event and data
						// type
						final byte code = (byte) ((event & 0x7000) >>> 12);
						final short data = (short) (event & 0x0FFF);

						switch (code) {
							case 0: // Special event
								switch (data) {
									case 0: // Ignore this, but log it.
										CypressFX3.log.severe("Caught special reserved event!");
										break;

									case 1: // Timetamp reset
										wrapAdd = 0;
										lastTimestamp = 0;
										currentTimestamp = 0;

										CypressFX3.log.info("Timestamp reset event received on " + super.toString());
										break;

									default:
										CypressFX3.log.severe("Caught special event that can't be handled.");
										break;
								}
								break;

							case 1: // AER address
								// Check range conformity.
								if (data >= aerMaxAddress) {
									CypressFX3.log.severe("AER: address out of range (0-" + (aerMaxAddress - 1) + "): " + data + ".");
									break; // Skip invalid AER address.
								}

								// Check that the buffer has space for this event. Enlarge if needed.
								if (ensureCapacity(buffer, eventCounter + 1)) {
									buffer.getAddresses()[eventCounter] = data;

									buffer.getTimestamps()[eventCounter++] = currentTimestamp;
								}

								break;

							case 7: // Timestamp wrap
								// Each wrap is 2^15 us (~32ms), and we have
								// to multiply it with the wrap counter,
								// which is located in the data part of this
								// event.
								wrapAdd += (0x8000L * data);

								lastTimestamp = currentTimestamp;
								currentTimestamp = wrapAdd;

								// Check monotonicity of timestamps.
								checkMonotonicTimestamp();

								CypressFX3.log.fine(
									String.format("Timestamp wrap event received on %s with multiplier of %d.", super.toString(), data));
								break;

							default:
								CypressFX3.log.severe("Caught event that can't be handled.");
								break;
						}
					}
				} // end loop over usb data buffer

				buffer.setNumEvents(eventCounter);
				// write capture size
				buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
			} // sync on aePacketRawPool
		}

		@Override
		public void propertyChange(final PropertyChangeEvent arg0) {
			// Do nothing here, IMU comes directly via event-stream.
		}
	}
}
