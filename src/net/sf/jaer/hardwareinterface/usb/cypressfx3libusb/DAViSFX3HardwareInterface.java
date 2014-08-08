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

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

import org.usb4java.Device;

import eu.seebetter.ini.chips.ApsDvsChip;
import eu.seebetter.ini.chips.sbret10.IMUSample;

/**
 * Adds functionality of apsDVS sensors to based CypressFX3Biasgen class. The
 * key method is translateEvents that parses
 * the data from the sensor to construct jAER raw events.
 *
 * @author Christian/Tobi
 */
public class DAViSFX3HardwareInterface extends CypressFX3Biasgen {

	protected DAViSFX3HardwareInterface(final Device device) {
		super(device);
	}

	/** The USB product ID of this device */
	static public final short PID = (short) 0x841A;
	static public final short DID = (short) 0x0000;

	private boolean translateRowOnlyEvents = CypressFX3.prefs.getBoolean(
		"ApsDvsHardwareInterface.translateRowOnlyEvents", false);

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

	/**
	 * If set, then row-only events are transmitted to raw packets from USB
	 * interface
	 *
	 * @param translateRowOnlyEvents
	 *            true to translate these parasitic events.
	 */
	public void setTranslateRowOnlyEvents(final boolean translateRowOnlyEvents) {
		this.translateRowOnlyEvents = translateRowOnlyEvents;
		CypressFX3.prefs.putBoolean("ApsDvsHardwareInterface.translateRowOnlyEvents", translateRowOnlyEvents);
	}

	public boolean isTranslateRowOnlyEvents() {
		return translateRowOnlyEvents;
	}

	/**
	 * This reader understands the format of raw USB data and translates to the
	 * AEPacketRaw
	 */
	public class RetinaAEReader extends CypressFX3.AEReader implements PropertyChangeListener {
		private int currentTimestamp, lastTimestamp;
		private short lastY;
		private boolean gotY;

		private static final int IMU_DATA_LENGTH = 7;
		private final short[] currImuSample;
		private int currImuSamplePosition = 0;

		public RetinaAEReader(final CypressFX3 cypress) throws HardwareInterfaceException {
			super(cypress);

			currImuSample = new short[RetinaAEReader.IMU_DATA_LENGTH];
		}

		@Override
		protected void translateEvents(final ByteBuffer b) {
			try {
				synchronized (aePacketRawPool) {
					final AEPacketRaw buffer = aePacketRawPool.writeBuffer();

					// Truncate off any extra partial event.
					if ((b.limit() & 0x01) != 0) {
						CypressFX3.log.severe(b.limit() + " bytes sent via USB, which is not a multiple of two.");
						b.limit(b.limit() & ~0x01);
					}

					final int[] addresses = buffer.getAddresses();
					final int[] timestamps = buffer.getTimestamps();

					buffer.lastCaptureIndex = eventCounter;

					final ShortBuffer sBuf = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

					for (int i = 0; i < sBuf.limit(); i++) {
						if ((eventCounter >= aeBufferSize) || (buffer.overrunOccuredFlag)) {
							buffer.overrunOccuredFlag = true;

							// Throw away the rest on buffer overrun.
							continue;
						}

						final short event = sBuf.get(i);

						// Check if timestamp
						if ((event & 0x8000) != 0) {
							// Is a timestamp!
							lastTimestamp = currentTimestamp;
							currentTimestamp = wrapAdd + (event & 0x7FFF);

							if (currentTimestamp < lastTimestamp) {
								CypressFX3.log.severe(toString() + ": non-monotonic timestamp: currentTimestamp="
									+ currentTimestamp + " lastTimestamp=" + lastTimestamp + " difference="
									+ (currentTimestamp - lastTimestamp));
							}
						}
						else {
							// Look at the code, to determine event and data
							// type
							final byte code = (byte) ((event & 0x7000) >> 12);
							final short data = (short) (event & 0x0FFF);

							switch (code) {
								case 0: // Special event
									switch (data) {
										case 0: // Ignore this, but log it.
											CypressFX3.log.severe("Caught special reserved event!");
											break;

										case 1: // Timetamp reset
											resetTimestamps();
											currentTimestamp = 0;
											lastTimestamp = 0;
											CypressFX3.log
												.info("Timestamp reset event received on " + super.toString());
											break;

										case 2: // External trigger (falling
												// edge)
										case 3: // External trigger (rising
												// edge)
										case 4: // External trigger (pulse)
											addresses[eventCounter] = ApsDvsChip.EXTERNAL_INPUT_EVENT_ADDR;
											timestamps[eventCounter++] = currentTimestamp;
											break;

										case 5: // IMU Start (6 axes), reset IMU
												// sample position for writing
											currImuSamplePosition = 0;
											break;

										case 7: // IMU End, write out IMU sample
												// to raw packet
											if (currImuSamplePosition != 14) {
												// Lost some IMU events in
												// transit, don't use them.
												currImuSamplePosition = 0;
												break;
											}

											final IMUSample imuSample = new IMUSample(currentTimestamp, currImuSample);
											eventCounter += imuSample.writeToPacket(buffer, eventCounter);
											break;

										default:
											CypressFX3.log.severe("Caught special event that can't be handled.");
											break;
									}
									break;

								case 1: // Y address
									if (gotY) {
										if (translateRowOnlyEvents) {
											addresses[eventCounter] = ((lastY << ApsDvsChip.YSHIFT) & ApsDvsChip.YMASK);
											timestamps[eventCounter++] = currentTimestamp;
											CypressFX3.log.info("Row only event on " + super.toString());
										}
									}

									lastY = data;
									gotY = true;

									break;

								case 2: // X address, Polarity OFF
								case 3: // X address, Polarity ON
									addresses[eventCounter] = ((lastY << ApsDvsChip.YSHIFT) & ApsDvsChip.YMASK)
										| ((data << ApsDvsChip.XSHIFT) & ApsDvsChip.XMASK)
										| (((code & 0x01) << ApsDvsChip.POLSHIFT) & ApsDvsChip.POLMASK);
									timestamps[eventCounter++] = currentTimestamp;

									gotY = false;

									break;

								case 5: // Misc 8bit data, used currently only
										// for IMU events in DAViS FX3 boards
									final byte misc8Code = (byte) ((data & 0x0F00) >> 8);
									final short misc8Data = (short) (data & 0x00FF);

									switch (misc8Code) {
										case 0:
											// Detect missing IMU end events.
											if (currImuSamplePosition >= 14) {
												break;
											}

											// IMU data event.
											if ((currImuSamplePosition & 0x01) == 0) {
												// Current position is even, so
												// we are getting the upper 8
												// bits of data.
												currImuSample[currImuSamplePosition >>> 1] = (short) (misc8Data << 8);
											}
											else {
												// Current position is uneven,
												// so we are getting the lower 8
												// bits of data.
												currImuSample[currImuSamplePosition >>> 1] = (short) (currImuSample[currImuSamplePosition >>> 1] | misc8Data);
											}

											currImuSamplePosition++;

											break;

										default:
											CypressFX3.log.severe("Caught Misc8 event that can't be handled.");
											break;
									}

									break;

								case 7: // Timestamp wrap
									// Each wrap is 2^15 µs (~32ms), and we have
									// to multiply it with the wrap counter,
									// which is located in the data part of this
									// event.
									wrapAdd += (0x8000L * data);

									CypressFX3.log.info(String.format(
										"Timestamp wrap event received on %s with multiplier of %d.", super.toString(),
										data));
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
			catch (final java.lang.IndexOutOfBoundsException e) {
				CypressFX3.log.warning(e.toString());
			}
		}

		@Override
		public void propertyChange(final PropertyChangeEvent arg0) {
			// Do nothing here, IMU comes directly via event-stream.
		}
	}
}
