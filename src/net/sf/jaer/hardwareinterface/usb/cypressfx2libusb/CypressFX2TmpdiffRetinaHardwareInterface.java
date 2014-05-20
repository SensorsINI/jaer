/*
 * CypressFX2Biasgen.java
 *
 * Created on December 1, 2005, 2:00 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx2libusb;

import java.nio.ByteBuffer;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;

import org.usb4java.Device;

/**
 * The hardware interface for the Tmpdiff128 (original) retina boards.
 *
 * @author tobi/rapha
 */
public class CypressFX2TmpdiffRetinaHardwareInterface extends CypressFX2Biasgen implements HasResettablePixelArray {

	/** Creates a new instance of CypressFX2Biasgen */
	protected CypressFX2TmpdiffRetinaHardwareInterface(final Device device) {
		super(device);
	}

	/**
	 * Starts reader buffer pool thread and enables in endpoints for AEs. This method is overridden to construct
	 * our own reader with its translateEvents method
	 */
	@Override
	public void startAEReader() throws HardwareInterfaceException { // raphael: changed from private to protected,
		// because i need to access this method
		setAeReader(new RetinaAEReader(this));
		allocateAEBuffers();
		getAeReader().startThread();
		HardwareInterfaceException.clearException();
	}

	/** This reader understands the format of raw USB data and translates to the AEPacketRaw */
	public class RetinaAEReader extends CypressFX2.AEReader {

		private int numNonMonotonicTimeExceptionsPrinted = 0;
		// note timestamps are multiplied by 10 so that they are in us, because the cypress fx2 retina board uses the
		// timer1 interrupt with period 10us to clock the timestamps counters
		final int REAL_WRAP_TIME_MS = (TICK_US_BOARD * ((1 << 16) - 1)) / 1000; // this is wrap time in ms on device
		// timestamp counter, e.g. 650ms
		// it is used below to check for bogus timetamp wraps due to glitches in sampling timestamp counter output
		long lastWrapTimeMs = System.currentTimeMillis();
		volatile int lastshortts = 0, tsinccounter = 0;
		volatile int lasttimestamp = 0;

		public RetinaAEReader(final CypressFX2 cypress) throws HardwareInterfaceException {
			super(cypress);
		}

		@Override
		public synchronized void resetTimestamps() {
			super.resetTimestamps();
			lasttimestamp = 0;
			lastshortts = 0;
		}

		/**
		 * Does the translation, timestamp unwrapping and reset
		 *
		 * @param b
		 *            the raw buffer
		 */
		@Override
		/** Populates the AE array, translating from raw bufffer data to raw addresses and unwrapped timestamps.
		 *<p>
		 * Event addresses and timestamps are sent from USB device in BIG-ENDIAN format. MSB comes first,
		 * The addresses are simply copied over, and the timestamps are unwrapped to make 32 bit int timestamps.
		 *<p>
		 * Wrapping is detected differently depending on whether the hardware sends empty wrap packets or simply wraps the timestamps.
		 * When the timestamps simply wrap, then a wrap is detected when the present timestamp is less than the previous one.
		 * Then we assume the counter has wrapped--but only once--and add into this and subsequent
		 * timestamps the wrap value of 2^16. this offset is maintained and increases every time there
		 * is a wrap. Hence it integrates the wrap offset.
		 *
		 *<p>
		 *Newer USB monitor interfaces based on using a CPLD pumping data into the CypressFX2 in slave FIFO mode signal a timestamp
		 *wrap by sending an empty wrap event;see {@link #translateEvents_EmptyWrapEvent}.
		 *
		 * <p>
		 * The timestamp is returned in 1 us ticks.
		 * This conversion is to make values more compatible with other CAVIAR software components.
		 *<p>
		 *If an overrun has occurred, then the data is still translated up to the overrun.
		 *@see #translateEvents_EmptyWrapEvent
		 *@see #translateEvents_TCVS320
		 *@param b the data buffer
		 */
		synchronized protected void translateEvents(final ByteBuffer b) {
			// System.out.println("buf has "+b.BytesTransferred+" bytes");
			// synchronized(aePacketRawPool){
			if (aePacketRawPool.writeBuffer().overrunOccuredFlag) {
				return; // don't bother if there's already an overrun, consumer must get the events to clear this flag
				// before there is more room for new events
			}
			int shortts;
			final byte[] aeBuffer = b.array();
			// byte lsb,msb;
			int bytesSent = b.limit();
			if ((bytesSent % 4) != 0) {
				// System.err.println("CypressFX2.AEReader.translateEvents(): warning: "+bytesSent+" bytes sent, which is not multiple of 4");
				bytesSent = (bytesSent / 4) * 4; // truncate off any extra part-event
			}

			final AEPacketRaw activeBuffer = aePacketRawPool.writeBuffer();

			final int[] addresses = activeBuffer.getAddresses();
			final int[] timestamps = activeBuffer.getTimestamps();

			final long timeNowMs = System.currentTimeMillis();

			// write the start of the packet
			activeBuffer.lastCaptureIndex = eventCounter;

			for (int i = 0; i < bytesSent; i += 4) {
				if (eventCounter > (aeBufferSize - 1)) {
					activeBuffer.overrunOccuredFlag = true;
					// log.warning("overrun");
					return; // return, output event buffer is full and we cannot add any more events to it.
					// no more events will be translated until the existing events have been consumed by
					// acquireAvailableEventsFromDriver
				}
				// according to FX2 tech ref manual 10.2.8, words from GPIF databus are sent over usb as LSB then MSB.
				// therefore AE07 come first, then AE8-15, then TS0-7, then TS8-15
				// see this useful URL: http://www.rgagnon.com/javadetails/java-0026.html about converting singed bytes
				// to int as unsigned
				// address is LSB MSB
				addresses[eventCounter] = (short) (0xffff & ((aeBuffer[i] & 0xff) | ((aeBuffer[i + 1] & 0xff) << 8)));

				// same for timestamp, LSB MSB
				shortts = ((aeBuffer[i + 2] & 0xff) | ((aeBuffer[i + 3] & 0xff) << 8)); // this is 16 bit value of
				// timestamp in TICK_US tick

				// shortts could be a negative short value, but each ts should be greater than the last one until 16 bit
				// rollover
				// tobi added following heuristic 12/05 to help deal with problem bit in timestamp counter that
				// apparently gets read incorrectly
				// occasionally, leading to excessive numbers of timestamp wraps

				// the preceeding special condition still occurs on tmpdiff128 usb2 cypress retina boards depending on
				// something probably
				// in cypress firmware and reset state, relative to cypress GPIF interface. not understood as of
				// 10/2006. see below

				if (shortts < lastshortts) {
					// if new counter value is less than previous one, assume counter has wrapped around.
					if (dontwrap) {
						dontwrap = false; // this flag is set in outer method resetTimestamps, it should prevent badwrap
						// messages here
						// even though resetting the timestamps has caused device timestamps to reset
					}
					else {
						// we count how many bits have changed since last timestamp. if timestamp has gone backwards
						// because of a real wrap, then
						// lots of bits should have changed, e.g from 0xfe to 0x03. but if the timestamp has gone
						// backwards because a single
						// or two bits have been latched incorrectly, then we count this as bad wrap event.
						final int or = shortts ^ lastshortts;
						final int bc = Integer.bitCount(or);
						// System.err.println("wrap, "+bc+" bits changed"); // usually 15/16 bits change or at least 8
						// when activity is very low
						if (bc < 7) {
						}
						else {
							// this IS a real counter wrap
							// now we need to increment the wrapAdd
							lastWrapAdd = wrapAdd;
							wrapAdd += 0x10000 * TICK_US_BOARD; // we put the tick here to correctly detect big wraps //
							// This is 0xFFFF +1; if we wrapped then increment wrap
							// value by 2^16
							// if(wrapAdd<lastWrapAdd) {
							// wrappedBig=true;
							// }else {
							// wrappedBig=false;
							// }
							lastWrapTimeMs = timeNowMs;
							// System.out.println(this+" incremented wrapAdd to "+wrapAdd/(0x10000L)/TICK_US_BOARD+" wraps");
						}
					}
				}

				// compute tentative value of new timestamp
				int thistimestamp = (TICK_US_BOARD * shortts) + wrapAdd; // *TICK_US; //add in the wrap offset and
				// convert to 1us tick

				// // if shortts is the same as last value, inc the timestamp by 1us to retain some order, at least for
				// first 10 events
				// if(shortts==lastshortts){
				// if(tsinccounter++<10) thistimestamp++;
				// }else {
				// tsinccounter=0;
				// }

				// don't let timestamps go backwards in time, UNLESS the wrapAdd has wrapped (this happens every 20
				// minutes)
				if ((thistimestamp < lasttimestamp) && !((wrapAdd & 0x80000000) != 0)) {
					if (numNonMonotonicTimeExceptionsPrinted++ < MAX_NONMONOTONIC_TIME_EXCEPTIONS_TO_PRINT) {
						CypressFX2.log.warning("NonMonotonicTime event: dt=" + (thistimestamp - lasttimestamp));
						if (numNonMonotonicTimeExceptionsPrinted == MAX_NONMONOTONIC_TIME_EXCEPTIONS_TO_PRINT) {
							CypressFX2.log.warning("suppressing further warnings about NonMonotonicTimeException");
						}
					}
					thistimestamp = lasttimestamp; // somehow this time is earlier than last time, we force zero dt here
					// *and* don't reset lastshortts
				}
				else {
					lastshortts = shortts; // save last timestamp to check for rollover; this is usual branch
				}

				// save the timestamp
				timestamps[eventCounter] = thistimestamp;
				lasttimestamp = thistimestamp;
				eventCounter++;
				activeBuffer.setNumEvents(eventCounter);
			}
			// write capture size
			activeBuffer.lastCaptureLength = eventCounter - activeBuffer.lastCaptureIndex;
			activeBuffer.systemModificationTimeNs = System.nanoTime();
			// System.out.println("index="+activeBuffer.lastCaptureIndex+", length="+activeBuffer.lastCaptureLength);
			// if(eventCounter<2){
			// int j=i+1;
			// System.out.println("aeBuffer["+i+"]="+HexString.byteToHexString(aeBuffer[i])+" aeBuffer["+j+"]="+HexString.byteToHexString(aeBuffer[i+1])
			// +" addr="+HexString.shortToHexString(addresses[eventCounter-1]));
			// }

			// } // sync on aePacketRawPool
		}
	}

	/**
	 * set the pixel array reset.
	 *
	 * @param value
	 *            true to reset the pixels (hold them from spiking), false to let them run normally.
	 */
	@Override
	synchronized public void setArrayReset(final boolean value) {
		arrayResetEnabled = value;
		// send vendor request for device to reset array
		if (deviceHandle == null) {
			throw new RuntimeException("device must be opened before sending this vendor request");
		}

		try {
			sendVendorRequest(VENDOR_REQUEST_SET_ARRAY_RESET, (short) ((value) ? (1) : (0)), (short) 0);
		}
		catch (final HardwareInterfaceException e) {
			System.err.println("CypressFX2.resetPixelArray: couldn't send vendor request to reset array");
		}
	}

	public boolean isArrayReset() {
		return arrayResetEnabled;
	}
}
