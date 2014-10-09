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
import eu.seebetter.ini.chips.DAViS.IMUSample;

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
		private int currentTimestamp, lastTimestamp, wrapAdd;
		private int dvsTimestamp, imuTimestamp, extTriggerTimestamp;
		private short lastY;
		private short misc8Data;
		private boolean gotY;
		private boolean gotCMevent;
		private boolean gotClusterEvent;
		private boolean gotBGAFevent;
                private boolean gotOMCevent;
		
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
							// Is a timestamp! Expand to 32 bits. (Tick is 1µs
							// already.)
							lastTimestamp = currentTimestamp;
							currentTimestamp = wrapAdd + (event & 0x7FFF);

							// Check monotonicity of timestamps.
							if (currentTimestamp <= lastTimestamp) {
								CypressFX3.log.severe(toString() + ": non-monotonic timestamp: currentTimestamp="
									+ currentTimestamp + " lastTimestamp=" + lastTimestamp + " difference="
									+ (lastTimestamp - currentTimestamp));
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
											wrapAdd = 0;
											currentTimestamp = 0;
											lastTimestamp = 0;
											dvsTimestamp = 0;
											imuTimestamp = 0;
											extTriggerTimestamp = 0;

											CypressFX3.log
												.info("Timestamp reset event received on " + super.toString());
											break;

										case 2: // External trigger (falling
												// edge)
										case 3: // External trigger (rising
												// edge)
										case 4: // External trigger (pulse)
											extTriggerTimestamp = currentTimestamp;

											addresses[eventCounter] = ApsDvsChip.EXTERNAL_INPUT_EVENT_ADDR;
											timestamps[eventCounter++] = extTriggerTimestamp;
											break;

										case 5: // IMU Start (6 axes), reset IMU
												// sample position for writing
											currImuSamplePosition = 0;
											imuTimestamp = currentTimestamp;
											break;

										case 7: // IMU End, write out IMU sample
												// to raw packet
											if (currImuSamplePosition != 14) {
												// Lost some IMU events in
												// transit, don't use them.
												currImuSamplePosition = 0;
												break;
											}

											final IMUSample imuSample = new IMUSample(imuTimestamp, currImuSample);
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
											timestamps[eventCounter++] = dvsTimestamp;
											CypressFX3.log.info("Row only event on " + super.toString());
										}
									}

									lastY = data;
									gotY = true;
									dvsTimestamp = currentTimestamp;

									break;

								case 2: // X address, Polarity OFF
								case 3: // X address, Polarity ON
									addresses[eventCounter] = ((lastY << ApsDvsChip.YSHIFT) & ApsDvsChip.YMASK)
										| ((data << ApsDvsChip.XSHIFT) & ApsDvsChip.XMASK)
										| (((code & 0x01) << ApsDvsChip.POLSHIFT) & ApsDvsChip.POLMASK)
										| ((((gotBGAFevent ? ApsDvsChip.HW_BGAF : 0) & 0x7) << 8) | misc8Data)
										| ((((gotCMevent ? ApsDvsChip.HW_TRACKER_CM : 0) & 0x07) << 8) | misc8Data)
										| ((((gotClusterEvent ? ApsDvsChip.HW_TRACKER_CLUSTER : 0) & 0x07) << 8) | misc8Data)
                                                                                | ((((gotOMCevent ? ApsDvsChip.HW_OMC_EVENT : 0) & 0x07) << 8) | misc8Data); //OMC event
									timestamps[eventCounter++] = dvsTimestamp;

									gotY = false;
									gotBGAFevent = false;
									gotCMevent = false;
									gotClusterEvent = false;
                                                                        gotOMCevent = false;
									misc8Data = 0;

									break;

								case 5: // Misc 8bit data, used currently only
										// for IMU events in DAViS FX3 boards
									final byte misc8Code = (byte) ((data & 0x0F00) >> 8);
									misc8Data = (short) (data & 0x00FF);

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

										case 5:
											gotBGAFevent = true;
											break;

										case 6:
											gotCMevent = true;
											break; // misc8data is from 0 to 3.
													// But if misc8data is 128,
													// then this is an ON BGAF
													// output event.

										case 7:
											gotClusterEvent = true;
											break; // misc8data is from 16 to 19
													// to identify the tracker.
													// But if misc8data is 128,
													// then this is an OFF BGAF
													// output event.

                                                                                case 8:
                                                                                        gotOMCevent = true;
                                                                                        break; // OMC cell's output
                                                                                    
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

									lastTimestamp = currentTimestamp;
									currentTimestamp = wrapAdd;

									// Check monotonicity of timestamps.
									if (currentTimestamp <= lastTimestamp) {
										CypressFX3.log.severe(toString()
											+ ": non-monotonic timestamp: currentTimestamp=" + currentTimestamp
											+ " lastTimestamp=" + lastTimestamp + " difference="
											+ (lastTimestamp - currentTimestamp));
									}

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
