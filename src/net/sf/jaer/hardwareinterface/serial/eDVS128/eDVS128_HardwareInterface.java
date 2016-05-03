/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.serial.eDVS128;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import gnu.io.SerialPort;
import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Interface to eDVS128 cameras via FTDI serial port or wifi TCP socket.
 * <p>
 * This camera uses 4Mbaud 8 bits 1 stop no parity with RTS/CTS.
 * <p>
 * The camera returns the following help
 *
 * <pre>
 * DVS128 - LPC2106/01 Interface Board, 1.1: July 27, 2011.
System Clock: 64MHz / 64 -> 1000ns event time resolution
Modules:

Supported Commands:

E+/-       - enable/disable event sending
!Ex        - specify event data format, ??E to list options

!BF          - send bias settings to DVS
!Bx=y      - set bias register x[0..11] to value y[0..0xFFFFFF]
?Bx        - get bias register x current value

!R+/-      - transmit event rate on/off
0,1,2      - LED off/on/blinking
!S=x       - set baudrate to x

R          - reset board
P          - enter reprogramming mode

??         - display help

??E
!E0   - 2 bytes per event binary 0yyyyyyy.pxxxxxxx (default)
!E1   - 4 bytes per event (as above followed by 16bit timestamp)

!E10  - 3 bytes per event, 6bit encoded
!E11  - 6 bytes per event+timestamp, 6bit encoded
!E12  - 4 bytes per event, 6bit encoded; new-line
!E13  - 7 bytes per event+timestamp, 6bit encoded; new-line

!E20  - 4 bytes per event, hex encoded
!E21  - 8 bytes per event+timestamp, hex encoded
!E22  - 5 bytes per event, hex encoded; new-line
!E23  - 8 bytes per event+timestamp, hex encoded; new-line

!E30  - 10 bytes per event, ASCII <1p> <3y> <3x>; new-line
!E31  - 10 bytes per event+timestamp, ASCII <1p> <3y> <3x> <5ts>; new-line
 * </pre>
 *
 * @author lou, tobi
 */
public class eDVS128_HardwareInterface implements HardwareInterface, AEMonitorInterface, BiasgenHardwareInterface,
	Observer/* , CommPortOwnershipListener */ {

	private static Preferences prefs = Preferences.userNodeForPackage(eDVS128_HardwareInterface.class);
	public PropertyChangeSupport support = new PropertyChangeSupport(this);
	static Logger log = Logger.getLogger("eDVS128");
	private AEChip chip;
	/** Amount by which we need to divide the received timestamp values to get us timestamps. */
	public final int TICK_DIVIDER = 1;
	private AEPacketRaw lastEventsAcquired = new AEPacketRaw();
	public static final int AE_BUFFER_SIZE = 100000; // should handle 5Meps at 30FPS
	private int aeBufferSize = prefs.getInt("eDVS128.aeBufferSize", AE_BUFFER_SIZE);
	private static boolean isOpen = false; // confuses things
	private static boolean eventAcquisitionEnabled = false;
	private static boolean overrunOccuredFlag = false;
	private byte cHighBitMask = (byte) 0x80;
	private byte cLowerBitsMask = (byte) 0x7F;
	private int eventCounter = 0;
	private int bCalibrated = 0;
	protected String devicName;
	private InputStream inputStream;
	private OutputStream outputStream;
	public final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE = new PropertyChangeEvent(this, "NewEvents", null, null);
	private AEPacketRawPool aePacketRawPool = new AEPacketRawPool();
	private int lastshortts = 0;
	private final int NUM_BIASES = 12; // number of biases, to renumber biases for bias command
	private boolean DEBUG = false;
	SerialPort serialPort;
	Socket socket;
	ArrayList<Pot> pots;

	/**
	 * Constructs a new eDVS128_HardwareInterface using the input and output stream supplied. The other arguments should
	 * only have one non-null entry and
	 * are used to properly close the interface.
	 *
	 * @param inputStream
	 * @param outputStream
	 * @param serialPort
	 *            - either supply this
	 * @param socket
	 *            - or supply this (not both)
	 */
	public eDVS128_HardwareInterface(InputStream inputStream, OutputStream outputStream, SerialPort serialPort, Socket socket) {
		this.inputStream = inputStream;
		this.outputStream = outputStream;
		this.serialPort = serialPort;
		this.socket = socket;
		if ((socket != null) && (serialPort != null)) {
			throw new Error(serialPort + " and " + socket + " are both supplied which is an error");
		}
	}

	public void writeOut(String s) {
		try {
			write(s);
		}
		catch (IOException e) {
			// Ignore this.
		}
	}

	/**
	 * Writes a string (adding \n if needed) and flushes the socket's output stream
	 *
	 * @param s
	 *            the string to write
	 * @throws IOException
	 */
	synchronized private void write(String s) throws IOException {
		if (outputStream == null) {
			throw new IOException("null output stream");
		}
		if (s == null) {
			return;
		}
		if (!s.endsWith("\n")) {
			s = s + "\n";
		}
		byte[] b = s.getBytes();
		outputStream.write(b, 0, b.length);
		// outputStream.flush();
		// log.info("sent "+s);
	}

	@Override
	public void open() throws HardwareInterfaceException {

		if (outputStream == null) {
			throw new HardwareInterfaceException("no interface to open; outputStream=null");
		}

		if (!isOpen) {
			isOpen = true;
			log.info("opening (resetting, turning LED steady ON and starting event transfer)");
			if ((chip == null) || !(chip instanceof DVS128)) {
				log.warning(
					"null chip or AEChip is not instance of DVS128, cannot add this interface as listener for bias generator ipot changes");
			}
			else {
				addedBiasListeners = true;
				DVS128 dvs128 = (DVS128) chip;
				ArrayList<Pot> pots = dvs128.getBiasgen().getPotArray().getPots();
				for (Pot p : pots) {
					p.addObserver(this); // TODO first send won't work
				}
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			CharBuffer buf = CharBuffer.allocate(1000);
			try {
				int avail = inputStream.available();
				if (reader.ready()) {
					reader.read(buf); // clear buffer
					log.info(buf.toString());
				}
				write("R");
				int wait = 10;
				Thread.sleep(300);

				while (!reader.ready() && (wait-- > 0)) {
					Thread.sleep(100);
				}
				if (!reader.ready() || (wait == 0)) {
					log.warning("eDVS did not return startup message after reset");
				}
				avail = inputStream.available();
				if (reader.ready()) {
					buf.clear();
					int nread = reader.read(buf);
					String s = buf.flip().toString();
					log.info("Device sent after reset: " + s);
					if ((s == null) || !s.contains("EDVS")) {
						log.warning("Did not see \"EDVS\" in the post-reset response. Maybe the wrong serial port or IP address?");
					}
				}
				write("!E2"); // data format, as in serial port interrface
				sendAllBiases();
				wait = 10;
				while (!reader.ready() && (wait-- > 0)) {
					Thread.sleep(100);
				}
				if (!reader.ready() || (wait == 0)) {
					log.warning("eDVS did not return any messages on sending biases");
				}
				avail = inputStream.available();
				if (reader.ready()) {
					buf.clear();
					int nread = reader.read(buf);
					log.info("Device sent after sending biases: " + buf.flip().toString());
				}
				setEventAcquisitionEnabled(true);
			}
			catch (Exception e) {
				throw new HardwareInterfaceException("could not open", e);
			}
		}
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	/**
	 * Closes the interface. The interface cannot be reopened without constructing a new instance.
	 *
	 */
	@Override
	public void close() {
		if (!isOpen) {
			log.info("already closed");
			return;
		}
		try {
			if ((chip != null) && (chip instanceof DVS128)) {
				DVS128 dvs128 = (DVS128) chip;
				ArrayList<Pot> pots = dvs128.getBiasgen().getPotArray().getPots();
				for (Pot p : pots) {
					p.deleteObserver(this); // TODO first send won't work
				}
			}
			write("0"); // turn off LED on eDVS board (not wifi board)
			setEventAcquisitionEnabled(false);
			// following commented because it deadlocks on RXTX read, which never seems to complete
			// if (getAeReader() != null) {
			// getAeReader().join();
			// }
			if (inputStream != null) {
				inputStream.close();
			}
			if (outputStream != null) {
				outputStream.close();
			}

			if (serialPort != null) {
				serialPort.removeEventListener();
				serialPort.close();
				serialPort = null;
				// serialPort=null;
			}
			else if (socket != null) {
				socket.close();
				socket = null;
			}
			isOpen = false;
		}
		catch (Exception ex) {
			log.warning(ex.toString());
		}
	}

	@Override
	public String getTypeName() {
		return "eDVS128";
	}

	@Override
	public void setChip(AEChip chip) {
		this.chip = chip;
	}

	@Override
	public AEChip getChip() {
		return chip;
	}

	@Override
	final public int getTimestampTickUs() {
		return 1;
	}

	private int estimatedEventRate = 0;

	@Override
	public int getEstimatedEventRate() {
		return estimatedEventRate;
	}

	@Override
	public int getMaxCapacity() {
		return 100000;
	}

	@Override
	public void addAEListener(AEListener listener) {
		support.addPropertyChangeListener(listener);
	}

	@Override
	public void removeAEListener(AEListener listener) {
		support.removePropertyChangeListener(listener);
	}

	@Override
	public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
		if (enable) {
			startAEReader();
		}
		else {
			stopAEReader();
		}
		try {
			if (enable) {
				write("E+");
			}
			else {
				write("E-");
			}
		}
		catch (IOException ex) {
			log.warning(ex.toString());
			throw new HardwareInterfaceException("couldn't write command to start or stop event sending", ex);
		}
	}

	@Override
	public boolean isEventAcquisitionEnabled() {
		return eventAcquisitionEnabled;
	}

	@Override
	public int getAEBufferSize() {
		return aeBufferSize; // aePacketRawPool.writeBuffer().getCapacity();
	}

	@Override
	public void setAEBufferSize(int size) {
		if ((size < 1000) || (size > 1000000)) {
			log.warning("ignoring unreasonable aeBufferSize of " + size + ", choose a more reasonable size between 1000 and 1000000");
			return;
		}
		this.aeBufferSize = size;
		prefs.putInt("eDVS128.aeBufferSize", aeBufferSize);
	}

	@Override
	public boolean overrunOccurred() {
		return overrunOccuredFlag;
	}

	/**
	 * Resets the timestamp unwrap value, and resets the AEPacketRawPool.
	 */
	@Override
	synchronized public void resetTimestamps() {
		wrapAdd = 0; // TODO call TDS to reset timestamps
		aePacketRawPool.reset();
	}

	/**
	 * returns last events from {@link #acquireAvailableEventsFromDriver}
	 *
	 * @return the event packet
	 */
	@Override
	public AEPacketRaw getEvents() {
		return this.lastEventsAcquired;
	}

	/**
	 * Returns the number of events acquired by the last call to {@link
	 * #acquireAvailableEventsFromDriver }
	 *
	 * @return number of events acquired
	 */
	@Override
	public int getNumEventsAcquired() {
		return lastEventsAcquired.getNumEvents();
	}

	@Override
	public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
		if (!eventAcquisitionEnabled) {
			setEventAcquisitionEnabled(true);
		}
		int nEvents;
		aePacketRawPool.swap();
		lastEventsAcquired = aePacketRawPool.readBuffer();
		nEvents = lastEventsAcquired.getNumEvents();
		eventCounter = 0;
		computeEstimatedEventRate(lastEventsAcquired);

		if (nEvents != 0) {
			support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE); // call listeners
		}

		return lastEventsAcquired;
	}

	synchronized public void vendorRequest(int cmd) {
		try {
			switch (cmd) {
				case 1:
					byte[] command = new byte[] { 'E', '+', '\r', '\n' };
					outputStream.write(command, 0, 4);
					break;

				case 2:
					// byte[] command = new byte[]{'E','-','\n'};
					// retinaVendor.write(command,0,3);
					break;

			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** computes the estimated event rate for a packet of events */
	void computeEstimatedEventRate(AEPacketRaw events) {
		if ((events == null) || (events.getNumEvents() < 2)) {
			estimatedEventRate = 0;
		}
		else {
			int[] ts = events.getTimestamps();
			int n = events.getNumEvents();
			int dt = ts[n - 1] - ts[0];
			estimatedEventRate = (int) ((1e6f * n) / dt);
		}
	}

	@Override
	public void setPowerDown(boolean powerDown) {
		log.warning("Power down not supported by eDVS128 devices.");
	}

	private boolean addedBiasListeners = false;

	@Override
	public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
		// comes here when bias values should be sent, but we don't know which one should be sent from this call.
		// generates a storm of biases on each bias change, so commented out for now. But this means the resend biases
		// and revert biases buttons in BiasgenFrame don't do what we want.
		// try {
		// if (biasgen instanceof DVS128.Biasgen) {
		// DVS128.Biasgen b2 = (DVS128.Biasgen) biasgen;
		// for (Pot p : b2.getPotArray().getPots()) {
		// sendPot((IPot) p);
		// }
		// }
		// } catch (IOException ex) {
		// log.warning(ex.toString());
		// }
	}

	private void sendAllBiases() {
		if (pots == null) {
			return;
		}
		for (Pot p : pots) {
			try {
				sendPot((IPot) p);
			}
			catch (IOException ex) {
				log.warning(ex.toString());
				break;
			}
		}
	}

	@Override
	public void flashConfiguration(Biasgen biasgen) {
		log.warning("Flash configuration not supported by eDVS128 devices.");
	}

	@Override
	public byte[] formatConfigurationBytes(Biasgen biasgen) {
		throw new UnsupportedOperationException("Not supported  or used for this device.");// TODO use this to send all
																							// biases at once? yes, this
																							// is how the on-chip bias
																							// generator works
	}

	@Override
	public String toString() {
		return "eDVS128_HardwareInterface with inputStream=" + inputStream + " outputStream=" + outputStream;
	}

	protected boolean running = true;
	final int WRAP = 0x10000; // amount of timestamp wrap to add for each overflow of 1 bit timestamps
	final int WRAP_START = 0; // (int)(0xFFFFFFFFL&(2147483648L-0x400000L)); // set high to test big wrap 1<<30;
	volatile int wrapAdd = WRAP_START; // 0;
	protected AEReader aeReader = null;

	public AEReader getAeReader() {
		return aeReader;
	}

	public void setAeReader(AEReader aeReader) {
		this.aeReader = aeReader;
	}

	public void startAEReader() {
		setAeReader(new AEReader(this));
		log.info("Start AE reader...");
		getAeReader().start();
		eventAcquisitionEnabled = true;
	}

	public void stopAEReader() {
		if (getAeReader() != null) {
			// close device
			getAeReader().finish();
		}
	}

	/** Called when notifyObservers is called in Observable we are watching, e.g. biasgen */
	@Override
	synchronized public void update(Observable o, Object arg) {
		// log.info("update from observable=" + o + " with arg=" + arg);
		if (o instanceof IPot) {
			try {
				if (outputStream == null) {
					log.warning("no connection; null output stream");
					return;
				}
				IPot p = (IPot) o;

				sendPot(p);
			}
			catch (IOException ex) {
				log.warning(ex.toString());
			}
		}
	}

	private void sendPot(IPot p) throws IOException {
		if (!isOpen) {
			return;
		}
		int v = p.getBitValue();
		int n = NUM_BIASES - 1 - p.getShiftRegisterNumber(); // eDVS firmware numbers in reverse order from DVS
																// firmware, we want shift register 0 to become 11 on
																// the eDVS
		String s = String.format("!B%d=%d", n, v); // LPC210 has 16-byte serial buffer, hopefully fits
		if (DEBUG) {
			log.info("sending command " + s + " for pot " + p + " at bias position " + n);
		}
		// note that eDVS must have rev1 firmware for BF command to work.
		if (s.length() > 16) {
			log.warning("sending " + s.length() + " bytes, might not fit in eDVS LPC2016 16-byte buffer");
		}
		write(s);
		// try {
		// Thread.sleep(100);
		// } catch (InterruptedException ex) {
		//
		// }
		write("!BF"); // these commands are NOT echoed if the sensor is sending events "e+" except that a special
						// confirmation event is sent.
	}

	// doesn't work because native ownership change notification is not implemented in 2011 in our RXTX
	// @Override
	// public void ownershipChange(int type) {
	// switch (type) {
	// case CommPortOwnershipListener.PORT_OWNED:
	// log.info("We got the port");
	// break;
	// case CommPortOwnershipListener.PORT_UNOWNED:
	// log.info("We've just lost our port ownership");
	// break;
	// case CommPortOwnershipListener.PORT_OWNERSHIP_REQUESTED:
	// log.info("Someone is asking our port's ownership; we will close() ourselves");
	// close();
	// break;
	// }
	// }
	public class AEReader extends Thread implements Runnable {

		private byte[] buffer = null;
		eDVS128_HardwareInterface monitor;

		public AEReader(eDVS128_HardwareInterface monitor) {
			this.monitor = monitor;
			setName("eDVS_AEReader");
			/* This is a list of all this interface's endpoints. */
			allocateAEBuffers();

			buffer = new byte[8192 * 4];// UsbUtil.unsignedInt(usbPipe.getUsbEndpoint().getUsbEndpointDescriptor().wMaxPacketSize())];
		}

		@Override
		@SuppressWarnings("SleepWhileInLoop")
		public void run() {

			int offset = 0;
			int length = 0;
			int len = 0;

			// int count=0;
			while (running) {
				try {
					len = monitor.inputStream.available();
					if (len == 0) {
						try {
							Thread.sleep(10);
							// System.out.print(".");
							// if(count++%80==0) System.out.print("\n"+count+"\t");
						}
						catch (InterruptedException ex) {
							log.warning("sleep interrupted while waiting events");
						}
						continue;
					}
					length = monitor.inputStream.read(buffer, 0, len - (len % 4));
					// System.out.println(length);
				}
				catch (IOException e) {
					log.warning("Aborting AEReader because caught exception " + e);
					e.printStackTrace();
					return;
				}

				int nDump = 0;

				if (len > 3) { // TODO what if len<=3? e.g. for part of an event that was sent
					// try {
					// length = inputStream.read(buffer, 0, len - (len % 4));
					// } catch (IOException e) {
					// e.printStackTrace();
					// }

					translateEvents_code(buffer, length);

					// what is this 'calibration'? TODO
					if (bCalibrated == 0) {
						int diff = 0;
						if (length > 100) {
							for (int i = 0; i <= 3; i++) {
								// offset=i;
								diff = 0;
								for (int m = 0; m < 10; m++) {
									diff += (buffer[(4 * (m + 1)) + i] - buffer[(4 * m) + i])
										* (buffer[(4 * (m + 1)) + i] - buffer[(4 * m) + i]);
								}
								// System.out.println(diff);
								if (diff < 20) { // 10
									offset = i;
									// break;
								}
							}
						}

						// System.out.println("length: " + length + " tail: " + nTail + " offset: " + offset);

						switch (offset) {
							case 0:
								nDump = 2;
								break;
							case 1:
								nDump = 3;
								break;
							case 2:
								nDump = 0;
								break;
							case 3:
								nDump = 1;
								break;
							default:
								log.warning("offset value should not be " + offset);
						}

						if (nDump != 0) {
							long length1 = 0;
							long len1 = 0;
							try {
								while (length1 != nDump) {
									// len = inputStream.read(buffer, length1, nDump - length1);
									len1 = monitor.inputStream.skip(nDump - length1);
									length1 = length1 + len1;
								}
							}
							catch (IOException e) {
								log.warning("Caught while skpping bytes: " + e.toString());
							}
							log.info("Dumped: " + length1 + " bytes / " + nDump);
						}
						else {
							bCalibrated = 1;
							log.info("Calibrated");
						}
					}

				}

				if (timestampsReset) {
					log.info("timestampsReset: flushing aePacketRawPool buffers");
					aePacketRawPool.reset();
					timestampsReset = false;
				}
			}
			log.info("reader thread ending");
		}

		/**
		 * Stop/abort listening for data events.
		 */
		synchronized public void finish() {
			running = false;
			interrupt();
		}

		synchronized public void resetTimestamps() {
			log.info(eDVS128_HardwareInterface.this + ": wrapAdd=" + wrapAdd + ", zeroing it");
			wrapAdd = WRAP_START;
			timestampsReset = true; // will inform reader thread that timestamps are reset

		}

		protected boolean running = true;
		volatile boolean timestampsReset = false; // used to tell processData that another thread has reset timestamps
	}

	private long lastWrapTime = 0;

	/**
	 * Writes events from buffer to AEPacketRaw in buffer pool.
	 *
	 * @param b
	 *            data sent from camera
	 * @param bytesSent
	 *            number of bytes in buffer that are valid
	 */
	protected void translateEvents_code(byte[] b, int bytesSent) {
		synchronized (aePacketRawPool) {

			AEPacketRaw buffer = aePacketRawPool.writeBuffer();
			int shortts;

			int[] addresses = buffer.getAddresses();
			int[] timestamps = buffer.getTimestamps();

			// write the start of the packet
			buffer.lastCaptureIndex = eventCounter;
			// log.info("entering translateEvents_code with "+eventCounter+" events");

			StringBuilder sb = null;
			if (DEBUG) {
				sb = new StringBuilder(String.format("%d events: Timestamp deltas are ", bytesSent / 4));
			}
			int i = 0;
			while (i < bytesSent) {
				/*
				 * event polarity is encoded in the msb of the second byte. i.e.
				 * Byte0, bit 7: always zero
				 * Byte0, bits 6-0: event address y
				 * Byte1, bit 7: event polarity
				 * Byte1, bits 6-0: event address x
				 * Bytes2+3: 16 bit timestamp, MSB first
				 */
				int y_ = (0xff & b[i]); // & with 0xff to prevent sign bit from extending to int (treat byte as
										// unsigned)
				int x_ = (0xff & b[i + 1]);
				int c_ = (0xff & b[i + 2]);
				int d_ = (0xff & b[i + 3]);

				if ((y_ & 0x80) != 0x80) {
					log.warning("Data not aligned - flushing rest of buffer");
					i++;
				}
				else {

					if (eventCounter >= buffer.getCapacity()) {
						buffer.overrunOccuredFlag = true;
						continue;
					}
					addresses[eventCounter] = (((x_ & cHighBitMask) >> 7) | ((y_ & cLowerBitsMask) << 8) | ((x_ & cLowerBitsMask) << 1))
						& 0x7FFF;

					shortts = ((c_ << 8) + d_); // should be unsigned since c_ and d_ are unsigned, timestamp is sent
												// big endian, MSB first at index 3
					if (lastshortts > shortts) { // timetamp wrapped
						wrapAdd += WRAP;
						long thisWrapTime = System.nanoTime();

						if (DEBUG) {
							log.info("This timestamp was less than last one by " + (shortts - lastshortts) + " and system deltaT="
								+ ((thisWrapTime - lastWrapTime) / 1000) + "us, wrapAdd=" + (wrapAdd / WRAP) + " wraps");
						}
						lastWrapTime = thisWrapTime;
					}
					timestamps[eventCounter] = (wrapAdd + shortts) / TICK_DIVIDER;
					if (DEBUG) {
						sb.append(String.format("%d ", shortts - lastshortts));
					}
					lastshortts = shortts;

					eventCounter++;
					i += 4; // event size in bytes
				}
			}
			if (DEBUG) {
				log.info(sb.toString());
			}
			buffer.setNumEvents(eventCounter);

			// write capture size
			buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
		} // sync on aePacketRawPool

	}

	void allocateAEBuffers() {
		synchronized (aePacketRawPool) {
			aePacketRawPool.allocateMemory();
		}
	}

	/** Double buffer for writing and reading events. */
	private class AEPacketRawPool {

		int capacity;
		AEPacketRaw[] buffers;
		AEPacketRaw lastBufferReference;
		volatile int readBuffer = 0, writeBuffer = 1; // this buffer is the one currently being read from

		AEPacketRawPool() {
			allocateMemory();
			reset();
		}

		/**
		 * Swaps read and write buffers in preparation for reading last captured buffer of events.
		 *
		 */
		synchronized final void swap() {
			lastBufferReference = buffers[readBuffer];
			if (readBuffer == 0) {
				readBuffer = 1;
				writeBuffer = 0;
			}
			else {
				readBuffer = 0;
				writeBuffer = 1;
			}
			writeBuffer().clear();
			writeBuffer().overrunOccuredFlag = false; // mark new write buffer clean, no overrun happened yet. writer
														// sets this if it happens
			// log.info("swapped buffers - new read buffer has "+readBuffer().getNumEvents()+" events");
		}

		/**
		 * Returns the current read buffer.
		 *
		 * @return buffer to read from
		 */
		synchronized final AEPacketRaw readBuffer() {
			return buffers[readBuffer];
		}

		/**
		 * Returns the current writing buffer. Does not swap buffers.
		 *
		 * @return buffer to write to
		 * @see #swap
		 */
		synchronized final AEPacketRaw writeBuffer() {
			return buffers[writeBuffer];
		}

		/** Set the current buffer to be the first one and clear the write buffer */
		synchronized final void reset() {
			readBuffer = 0;
			writeBuffer = 1;
			buffers[writeBuffer].clear(); // new events go into this buffer which should be empty

			buffers[readBuffer].clear(); // clear read buffer in case this buffer was reset by resetTimestamps
			// log.info("buffers reset");

		}

		// allocates AEPacketRaw each with capacity AE_BUFFER_SIZE
		private void allocateMemory() {
			buffers = new AEPacketRaw[2];
			for (int i = 0; i < buffers.length; i++) {
				buffers[i] = new AEPacketRaw();
				buffers[i].ensureCapacity(getAEBufferSize()); // preallocate this memory for capture thread and to try
																// to make it contiguous

			}
		}
	}
}
