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

import org.usb4java.Device;

import ch.unizh.ini.jaer.chip.cochlea.CochleaLP;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Adds functionality of apsDVS sensors to based CypressFX3Biasgen class. The
 * key method is translateEvents that parses the data from the sensor to
 * construct jAER raw events.
 *
 * @author Christian/Tobi
 */
public class CochleaFX3HardwareInterface extends CypressFX3Biasgen {

	protected CochleaFX3HardwareInterface(final Device device) {
		super(device);
	}

	@Override
	synchronized public void sendConfiguration(final net.sf.jaer.biasgen.Biasgen biasgen) throws HardwareInterfaceException {
		if ((biasgen != null) && (biasgen instanceof CochleaLP.Biasgen)) {
			((CochleaLP.Biasgen) biasgen).sendConfiguration();
		}
	}

	/**
	 * The USB product ID of this device
	 */
	static public final short PID_FX3 = (short) 0x841C;
	static public final int REQUIRED_FIRMWARE_VERSION_FX3 = 3;
	static public final int REQUIRED_LOGIC_REVISION_FX3 = 0;

	/**
	 * Starts reader buffer pool thread and enables in endpoints for AEs. This
	 * method is overridden to construct our own reader with its translateEvents
	 * method
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
	 * Data word is tagged as AER address or ADC sample by bit 0x2000 (DATA_TYPE_MASK). Set (ADDRESS_TYPE_ADC)=ADC
	 * sample; unset (DATA_TYPE_AER_ADDRESS)=AER address.
	 */
	public static final int DATA_TYPE_AER_ADDRESS = 0x0000; // aer addresses don't have the bit set
	public static final int DATA_TYPE_ADC = 0x2000; // adc samples have this bit set
	public static final int DATA_TYPE_ADC_CNV_START = 0x3000; // adc conversion start events also have bit 12 set
	public static final int DATA_TYPE_RANDOMDAC = 0x40000; // SampleProb only, 18 bits of space needed

	/**
	 * This reader understands the format of raw USB data and translates to the
	 * AEPacketRaw
	 */
	public class RetinaAEReader extends CypressFX3.AEReader implements PropertyChangeListener {
		private final int chipID;

		private final int aerMaxAddress;

		// Event Codes
		private static final int EC_SPECIAL = 0;
		private static final int EC_SPECIAL_RESERVED = 0;
		private static final int EC_SPECIAL_TIMESTAMP_RESET = 1;
		private static final int EC_SPECIAL_ADC_START_CNV = 44;
		private static final int EC_SPECIAL_ADC_START_CNV_1US = 45;

		private static final int EC_MISC10_RANDOM_PART1 = 0;
		private static final int EC_MISC10_RANDOM_PART2 = 1;

		private int wrapAdd;
		private int lastTimestamp;
		private int currentTimestamp;

		private int randomChannel;
		private int randomNumber;

		public RetinaAEReader(final CypressFX3 cypress) throws HardwareInterfaceException {
			super(cypress);

			checkFirmwareLogic(CochleaFX3HardwareInterface.REQUIRED_FIRMWARE_VERSION_FX3,
				CochleaFX3HardwareInterface.REQUIRED_LOGIC_REVISION_FX3);

			chipID = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 1);

			if (chipID == CochleaFX3HardwareInterface.CHIP_COCHLEALP) {
				aerMaxAddress = 256;
			}
			else if (chipID == CochleaFX3HardwareInterface.CHIP_COCHLEA4EAR) {
				aerMaxAddress = 1024;
			}
			else {
				// CHIP_SAMPLEPROB -- 12 bits
				aerMaxAddress = 4096;
			}

			updateTimestampMasterStatus();
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

					// General Event structure:
					// [ t | ccc | 12 bit subcode+data ]
					// t - type Timestamp (1) of Event (0), ccc - code of event
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
							// [ 0 | 000 | data ]
							case EC_SPECIAL: // Special event
								switch (data) {
									case EC_SPECIAL_RESERVED: // Ignore this, but log it.
										CypressFX3.log.severe("Caught special reserved event!");
										break;

									case EC_SPECIAL_TIMESTAMP_RESET: // Timetamp reset
										wrapAdd = 0;
										lastTimestamp = 0;
										currentTimestamp = 0;

										updateTimestampMasterStatus();

										CypressFX3.log.info("Timestamp reset event received on " + super.toString());
										break;

									case EC_SPECIAL_ADC_START_CNV: // ADC conversion start
									case EC_SPECIAL_ADC_START_CNV_1US: // ADC conversion start was timestamped with the
																		// delay 1us
																		// Check that the buffer has space for this
																		// event. Enlarge if needed.
										if (ensureCapacity(buffer, eventCounter + 1)) {
											buffer.getAddresses()[eventCounter] = data | DATA_TYPE_ADC_CNV_START;
											buffer.getTimestamps()[eventCounter++] = currentTimestamp;
										}
										break;

									default:
										CypressFX3.log.severe("Caught special event that can't be handled.");
										break;
								}
								break;

							// [ 0 | 001 | data ]
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

							// [ 0 | 100 | 1 | 11 zero bits ] - start of conversion token, comes first
							// [ 0 | 100 | 1 | 2 bit ADC-channel | 9 MSB data bits ]
							// [ 0 | 100 | 0 | 2 bit ADC-channel | 9 LSB data bits ]
							case 4: // ADC sample
								// Check that the buffer has space for this event. Enlarge if needed.
								if (ensureCapacity(buffer, eventCounter + 1)) {
									buffer.getAddresses()[eventCounter] = data | DATA_TYPE_ADC;
									buffer.getTimestamps()[eventCounter++] = currentTimestamp;
								}
								break;

							// MISC10 events, carry 2 bits type and 10 bits information.
							// Used in SampleProb chip to send info about random DAC values.
							case 6:
								// Get Misc10 identifier from upper 2 bits of the 12 bits data.
								byte misc10Code = (byte) ((data >>> 10) & 0x03);

								if (misc10Code == EC_MISC10_RANDOM_PART1) {
									// Part1: contains 8 bits of data, 4 for channel address, 4 for the upper bits of
									// the 14 bit random number.
									randomChannel = ((data >>> 4) & 0x0F);
									randomNumber = ((data & 0x0F) << 10);
								}
								else if (misc10Code == EC_MISC10_RANDOM_PART2) {
									// Part2: contains 10 bits of data, the lower bits of the 14 bit random number.
									randomNumber |= (data & 0x03FF);

									// Now we have all the parts and can commit the RandomDAC event.
									if (ensureCapacity(buffer, eventCounter + 1)) {
										buffer.getAddresses()[eventCounter] = DATA_TYPE_RANDOMDAC | (randomChannel << 14) | randomNumber;
										buffer.getTimestamps()[eventCounter++] = currentTimestamp;
									}
								}
								else {
									CypressFX3.log.severe("Caught Misc10 event that can't be handled.");
								}
								break;

							// [ 0 | 111 | 12 dummy bits ]
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

								// Generate event to advance clock on host side even with low event rate of Cochlea.
								if (ensureCapacity(buffer, eventCounter + 1)) {
									buffer.getAddresses()[eventCounter] = (data & 0xFFFF) | BasicEvent.SPECIAL_EVENT_BIT_MASK;
									buffer.getTimestamps()[eventCounter++] = currentTimestamp;
								}

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
