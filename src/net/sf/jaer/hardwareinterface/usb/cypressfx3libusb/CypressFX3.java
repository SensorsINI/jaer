/*
 * USBAEMon.java
 *
 * Created on February 17, 2005, 7:54 AM
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.awt.Desktop;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.usb4java.BufferUtils;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import li.longi.USBTransferThread.RestrictedTransfer;
import li.longi.USBTransferThread.RestrictedTransferCallback;
import li.longi.USBTransferThread.USBTransferThread;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.hardwareinterface.BlankDeviceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.HasUsbStatistics;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.USBPacketStatistics;
import net.sf.jaer.stereopsis.StereoPairHardwareInterface;

/**
 * Devices that use the CypressFX3 and the USBIO driver, e.g. the DVS retinas,
 * the USBAERmini2. This class should not
 * normally be constructed but rather a subclass that overrides
 * the AEReader should be used.
 * <p>
 * In this class, you can also set the size of the host buffer with {@link #setAEBufferSize}, giving you more time
 * between calls to process the events.
 * <p>
 * On the device, a timer sends all available events approximately every 10ms -- you don't need to wait for a fixed size
 * buffer to be captured to be available to the host. But if events come quickly enough, new events can be available
 * much faster than this.
 * <p>
 * You can also request at any time an early transfer of events with {@link #requestEarlyTransfer}. This will send a
 * vendor request to the device to immediately transfer available events, but they won't be available to the host for a
 * little while, depending on USBIOInterface and driver latency.
 * <p>
 * See the main() method for an example of use.
 * <p>
 * Fires PropertyChangeEvent on the following
 * <ul>
 * <li>NEW_EVENTS_PROPERTY_CHANGE - on new events from driver
 * <li>"readerStarted" - when the reader thread is started
 * </ul>
 *
 *
 * @author tobi delbruck/raphael berner
 */
public class CypressFX3 implements AEMonitorInterface, ReaderBufferControl, USBInterface, HasUsbStatistics {

	/** Used to store preferences, e.g. buffer sizes and number of buffers. */
	protected static Preferences prefs = Preferences.userNodeForPackage(CypressFX3.class);

	protected static final Logger log = Logger.getLogger("net.sf.jaer");
	protected AEChip chip;

	/**
	 * A blank Cypress FX2 has VID/PID of 0x04b4/0x8613. This VID/PID pair is
	 * used to indicate a blank device that needs
	 * programming.
	 */
	static final public short VID_BLANK = (short) 0x04b4, PID_BLANK = (short) 0x00f3;
	/**
	 * All the devices here have vendor ID VID which has been allocated to jAER
	 * by Thesycon
	 */
	static public final short VID = USBInterface.VID_THESYCON;

	/**
	 * event supplied to listeners when new events are collected. this is final
	 * because it is just a marker for the
	 * listeners that new events are available
	 */
	public final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE = new PropertyChangeEvent(this, "NewEvents", null, null);

	/**
	 * Property change fired when new events are received. The new object in the
	 * event is AEPacketRaw just received.
	 */
	public static final String PROPERTY_CHANGE_NEW_EVENTS = "NewEvents";

	/**
	 * Property change fired when a new message is received on the asynchronous
	 * status endpoint.
	 *
	 * @see AsyncStatusThread
	 */
	public static final String PROPERTY_CHANGE_ASYNC_STATUS_MSG = "AsyncStatusMessage";

	/**
	 * This support can be used to register this interface for property change
	 * events
	 */
	public PropertyChangeSupport support = new PropertyChangeSupport(this);

	final static byte AE_MONITOR_ENDPOINT_ADDRESS = (byte) 0x82; // this is
	// endpoint
	// of AE
	// fifo on
	// Cypress
	// FX2, 0x86
	// means IN endpoint EP6.
	final static byte STATUS_ENDPOINT_ADDRESS = (byte) 0x81; // this is endpoint
	// 1 IN for
	// device to
	// report status

	public static final byte VR_FPGA_CONFIG = (byte) 0xBF;
	public static final byte VR_FPGA_CONFIG_MULTIPLE = (byte) 0xC2;

	protected final static short CONFIG_INDEX = 0;
	protected final static short CONFIG_NB_OF_INTERFACES = 1;
	protected final static short CONFIG_INTERFACE = 0;
	protected final static short CONFIG_ALT_SETTING = 0;
	protected final static int CONFIG_TRAN_SIZE = 512;
	// following are to support realtime filtering
	// the AEPacketRaw is used only within this class. Each packet is extracted
	// using the chip extractor object from the
	// first filter in the
	// realTimeFilterChain to a reused EventPacket.
	AEPacketRaw realTimeRawPacket = null; // used to hold raw events that are
	// extracted for real time procesing
	EventPacket realTimePacket = null; // used to hold extracted real time
	// events for processing
	/**
	 * start of events that have been captured but not yet processed by the
	 * realTimeFilters
	 */
	private int realTimeEventCounterStart = 0;
	/**
	 * timeout in ms to reopen driver (reloading firmware) if no events are
	 * received for this time. This timeout will
	 * restart AE transmission if
	 * another process (e.g. Biasgen) reloads the firmware. This timer is
	 * checked on every attempt to acquire events.
	 */
	public static long NO_AE_REOPEN_TIMEOUT = 3000;
	/**
	 * Time in us of each timestamp count here on host, could be different on
	 * board.
	 */
	public final short TICK_US = 1;

	/**
	 * default size of AE buffer for user processes. This is the buffer that is
	 * written by the hardware capture thread
	 * that holds events
	 * that have not yet been transferred via {@link #acquireAvailableEventsFromDriver} to another thread
	 *
	 * @see #acquireAvailableEventsFromDriver
	 * @see AEReader
	 * @see #setAEBufferSize
	 */
	public static final int AE_BUFFER_SIZE = 600000; // 100k should handle 5Meps at
	// 30FPS, but tobi increased to 600k to handle APS frames from Davis346B at 40FPS
	/**
	 * this is the size of the AEPacketRaw that are part of AEPacketRawPool that
	 * double buffer the translated events
	 * between rendering and capture threads
	 */
	protected int aeBufferSize = CypressFX3.prefs.getInt("CypressFX3.aeBufferSize", CypressFX3.AE_BUFFER_SIZE);
	/** the event reader - a buffer pool thread from USBIO subclassing */
	protected AEReader aeReader = null;
	/** the thread that reads device status messages on EP1 */
	protected AsyncStatusThread asyncStatusThread = null;
	/** The pool of raw AE packets, used for data transfer */
	protected AEPacketRawPool aePacketRawPool = new AEPacketRawPool(this);
	private String stringDescription = "CypressFX3"; // default which is
	private USBPacketStatistics usbPacketStatistics = new USBPacketStatistics();

	// modified by opening

	/**
	 * Populates the device descriptor and the string descriptors and builds the
	 * String for toString().
	 *
	 * @param gUsbIo
	 *            the handle to the UsbIo object.
	 */
	private void populateDescriptors() {
		try {
			int status;

			// getString device descriptor
			if (deviceDescriptor == null) {
				deviceDescriptor = new DeviceDescriptor();
				status = LibUsb.getDeviceDescriptor(device, deviceDescriptor);
				if (status != LibUsb.SUCCESS) {
					throw new HardwareInterfaceException("populateDescriptors(): getDeviceDescriptor: " + LibUsb.errorName(status));
				}
			}

			if (deviceDescriptor.iSerialNumber() != 0) {
				numberOfStringDescriptors = 3; // SN also defined.
			}
			else {
				numberOfStringDescriptors = 2;
			}

			stringDescriptor1 = LibUsb.getStringDescriptor(deviceHandle, (byte) 1);
			if (stringDescriptor1 == null) {
				throw new HardwareInterfaceException("populateDescriptors(): getStringDescriptor1");
			}

			stringDescriptor2 = LibUsb.getStringDescriptor(deviceHandle, (byte) 2);
			if (stringDescriptor2 == null) {
				throw new HardwareInterfaceException("populateDescriptors(): getStringDescriptor2");
			}

			if (numberOfStringDescriptors == 3) {
				stringDescriptor3 = LibUsb.getStringDescriptor(deviceHandle, (byte) 3);
				if (stringDescriptor3 == null) {
					throw new HardwareInterfaceException("populateDescriptors(): getStringDescriptor3");
				}
			}

			// build toString string
			if (numberOfStringDescriptors == 3) {
				stringDescription = (getStringDescriptors()[1] + " " + getStringDescriptors()[2]);
			}
			else if (numberOfStringDescriptors == 2) {
				stringDescription = (getStringDescriptors()[1] + ": Interface " + device);
			}
		}
		catch (final BlankDeviceException bd) {
			stringDescription = "Blank Cypress FX2 : Interface " + device;
		}
		catch (final Exception e) {
			stringDescription = (getClass().getSimpleName() + ": Interface " + device);
		}
	}

	/**
	 * The count of events acquired but not yet passed to user via
	 * acquireAvailableEventsFromDriver
	 */
	protected int eventCounter = 0; // counts events acquired but not yet passed
	// to user
	/**
	 * the last events from {@link #acquireAvailableEventsFromDriver}, This
	 * packet is reused.
	 */
	protected AEPacketRaw lastEventsAcquired = new AEPacketRaw();
	protected boolean inEndpointEnabled = false; // raphael: changed from
	// private to protected,
	// because i need to access
	// this member
	/** device open status */
	private boolean isOpened = false;
	/**
	 * the device number, out of all potential compatible devices that could be
	 * opened
	 */
	protected Device device = null;
	protected DeviceHandle deviceHandle = null;

	/**
	 * This constructor is protected because these instances should be
	 * constructed by the CypressFX3Factory.
	 * Creates a new instance of USBAEMonitor. Note that it is possible to
	 * construct several instances
	 * and use each of them to open and read from the same device.
	 *
	 * @param devNumber
	 *            the desired device number, in range returned by
	 *            CypressFX3Factory.getNumInterfacesAvailable
	 */
	protected CypressFX3(final Device device) {
		this.device = device;
	}

	/**
	 * acquire a device for exclusive use, other processes can't open the device
	 * anymore
	 * used for example for continuous sequencing in matlab
	 */
	public void acquireDevice() throws HardwareInterfaceException {
		CypressFX3.log.log(Level.INFO, "{0} acquiring device for exclusive access", this);

		final int status = LibUsb.claimInterface(deviceHandle, 0);
		if (status != LibUsb.SUCCESS) {
			throw new HardwareInterfaceException("Unable to acquire device for exclusive use: " + LibUsb.errorName(status));
		}
	}

	/** release the device from exclusive use */
	public void releaseDevice() throws HardwareInterfaceException {
		CypressFX3.log.log(Level.INFO, "{0} releasing device", this);

		final int status = LibUsb.releaseInterface(deviceHandle, 0);
		if (status != LibUsb.SUCCESS) {
			throw new HardwareInterfaceException("Unable to release device from exclusive use: " + LibUsb.errorName(status));
		}
	}

	/**
	 * Returns the PropertyChangeSupport.
	 *
	 * @return the support.
	 * @see #PROPERTY_CHANGE_ASYNC_STATUS_MSG
	 * @see #PROPERTY_CHANGE_NEW_EVENTS
	 */
	public PropertyChangeSupport getSupport() {
		return support;
	}

	/**
	 * Returns string description of device including the
	 * USB vendor/project IDs. If the device has not been
	 * opened then it is minimally opened to populate the deviceDescriptor and
	 * then closed.
	 *
	 * @return the string description of the device.
	 */
	@Override
	public String toString() {
		if (numberOfStringDescriptors == 0) {
			try {
				open_minimal_close(); // populates stringDescription and sets
				// numberOfStringDescriptors!=0
			}
			catch (final Exception e) {
                            log.warning("caught exception when trying to populate stringDescription: "+e.toString());
			}
		}
		return stringDescription;
	}

	/**
	 * Writes the serial number string to the device EEPROM
	 *
	 * @param name
	 *            the string. This string has very limited length, e.g. 4 bytes.
	 * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
	 */
	public void setSerialNumber(final String name) throws HardwareInterfaceException {
		if (!isOpen()) {
			open();
		}

		final CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
		final ByteBuffer buffer = BufferUtils.allocateByteBuffer(name.length());

		encoder.encode(CharBuffer.wrap(name), buffer, true);
		encoder.flush(buffer);

		// sendVendorRequest(CypressFX3.VR_SET_DEVICE_NAME, (short) 0, (short)
		// 0, buffer);

		stringDescriptor3 = LibUsb.getStringDescriptor(deviceHandle, (byte) 3);
		if (stringDescriptor3 == null) {
			CypressFX3.log.warning("Could not get new device name!");
		}
		else {
			CypressFX3.log.info("New Devicename set, close and reopen the device to see the change");
		}
	}

	/**
	 * adds a listener for new events captured from the device.
	 * Actually gets called whenever someone looks for new events and there are
	 * some using
	 * acquireAvailableEventsFromDriver, not when data is actually captured by
	 * AEReader.
	 * Thus it will be limited to the users sampling rate, e.g. the game loop
	 * rendering rate.
	 *
	 * @param listener
	 *            the listener. It is called with a PropertyChangeEvent when new
	 *            events
	 *            are received by a call to {@link #acquireAvailableEventsFromDriver}.
	 *            These events may be accessed by calling {@link #getEvents}.
	 */
	@Override
	public void addAEListener(final AEListener listener) {
		support.addPropertyChangeListener(listener);
	}

	@Override
	public void removeAEListener(final AEListener listener) {
		support.removePropertyChangeListener(listener);
	}

	/**
	 * starts reader buffer pool thread and enables in endpoints for AEs.
	 * Subclasses *MUST* override this method to
	 * start their own customized reader
	 * with their own translateEvents method.
	 */
	public void startAEReader() throws HardwareInterfaceException {
		throw new HardwareInterfaceException(
			"This method should not be called - the CypressFX3 subclass should override startAEReader. Probably this is a blank device that requires programming.");
	}

	long lastTimeEventCaptured = System.currentTimeMillis();

	/**
	 * Gets available events from driver. {@link HardwareInterfaceException} is
	 * thrown if there is an error. {@link #overrunOccurred} will be reset after
	 * this call.
	 * <p>
	 * This method also starts event acquisition if it is not running already.
	 *
	 * Not thread safe but does use the thread-safe swap() method of AEPacketRawPool to swap data with the acquisition
	 * thread.
	 *
	 * @return packet of events acquired.
	 * @throws HardwareInterfaceException
	 * @see #setEventAcquisitionEnabled
	 *
	 *      .
	 */
	@Override
	public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
		if (!isOpen()) {
			open();
		}

		// make sure event acquisition is running
		if (!inEndpointEnabled) {
			setEventAcquisitionEnabled(true);
		}

		// HardwareInterfaceException.clearException();

		// make sure that event translation from driver is allowed to run if
		// need be, to avoid holding up event sender
		// Thread.currentThread().yield();

		// short[] addresses;
		// int[] timestamps;
		int nEvents;

		// getString the 'active' buffer for events (the one that has just been
		// written by the hardware thread)
		// synchronized(aePacketRawPool){ // synchronize on aeReader so that we
		// don't try to access the events at the
		// same time
		synchronized (aePacketRawPool) {
			aePacketRawPool.swap();
			lastEventsAcquired = aePacketRawPool.readBuffer();
			eventCounter = 0;
			realTimeEventCounterStart = 0;
		}

		nEvents = lastEventsAcquired.getNumEvents();
		computeEstimatedEventRate(lastEventsAcquired);
		if (nEvents != 0) {
			support.firePropertyChange(CypressFX3.PROPERTY_CHANGE_NEW_EVENTS, null, lastEventsAcquired); // call
			// listeners
		}
		return lastEventsAcquired;
	}

	/**
	 * the max capacity of this USB2 bus interface is 24MB/sec/4 bytes/event
	 */
	@Override
	public int getMaxCapacity() {
		return 6000000;
	}

	private int estimatedEventRate = 0;

	/**
	 * @return event rate in events/sec as computed from last acquisition.
	 *
	 */
	@Override
	public int getEstimatedEventRate() {
		return estimatedEventRate;
	}

	/** computes the estimated event rate for a packet of events */
	void computeEstimatedEventRate(final AEPacketRaw events) {
		if ((events == null) || (events.getNumEvents() < 2)) {
			estimatedEventRate = 0;
		}
		else {
			final int[] ts = events.getTimestamps();
			final int n = events.getNumEvents();
			final int dt = ts[n - 1] - ts[0];
			estimatedEventRate = (int) ((1e6f * n) / dt);
		}
	}

	/**
	 * Returns the number of events acquired by the last call to {@link #acquireAvailableEventsFromDriver }
	 *
	 * @return number of events acquired
	 */
	@Override
	public int getNumEventsAcquired() {
		return lastEventsAcquired.getNumEvents();
	}

	/**
	 * Check the firmware and logic versions against exact required ones and display an error if not met.
	 */
	protected void checkFirmwareLogic(final int requiredFirmwareVersion, final int requiredLogicRevision)
		throws HardwareInterfaceException {
		final StringBuilder updateStringBuilder = new StringBuilder("<html>");
                boolean needsUpdate=false;

		// Verify device firmware version and logic revision.
		final int usbFWVersion = getDID() & 0x00FF;
		if (usbFWVersion != requiredFirmwareVersion) {
			updateStringBuilder
				.append(String.format("<p>Device firmware version incorrect. You have version %d; but version %d is required.</p>",
					usbFWVersion, requiredFirmwareVersion));
                        needsUpdate=true;
		}

		final int logicRevision = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 0);
		if (logicRevision != requiredLogicRevision) {
			updateStringBuilder
				.append(String.format("<p>Device logic version incorrect. You have version %d; but version %d is required.</p>",
					logicRevision, requiredLogicRevision));
                        needsUpdate=true;
		}
                
		if (needsUpdate) {
			updateStringBuilder
				.append("<p>Please update by following the Flashy documentation at <a href=\" " + JaerConstants.HELP_USER_GUIDE_URL_FLASHY+"\">"+JaerConstants.HELP_USER_GUIDE_URL_FLASHY+"</a></p>");
                        updateStringBuilder.append("<p>Clicking OK will open this URL in browser</p>");
                        updateStringBuilder.append("<p>After installing DV, you can launch flashy from command line (in linux) or from DV GUI</p>");
			final String updateString = updateStringBuilder.toString();

			final SwingWorker<Void, Void> strWorker = new SwingWorker<Void, Void>() {
				@Override
				public Void doInBackground() {
					JOptionPane.showMessageDialog(null, updateString);
                                        if(Desktop.isDesktopSupported()){
                                            try {
                                                Desktop.getDesktop().browse(new URI(JaerConstants.HELP_USER_GUIDE_URL_FLASHY));
                                            } catch (Exception ex) {
                                                Logger.getLogger(CypressFX3.class.getName()).log(Level.WARNING, null, ex);
                                            }
                                        }

					return (null);
				}
			};
			strWorker.execute();

//			throw new HardwareInterfaceException(updateString);
		}
	}

	/**
	 * Reset the timestamps to zero. This has two effects. First it sends a
	 * vendor request down the control endpoint
	 * to tell the device to reset its own internal timestamp counters. Second,
	 * it tells the AEReader object to reset
	 * its
	 * timestamps, meaning to reset its unwrap counter.
	 */
	@Override
	synchronized public void resetTimestamps() {
		CypressFX3.log.info(this + ".resetTimestamps(): zeroing timestamps");

		// send vendor request for device to reset timestamps
		if (deviceHandle == null) {
			throw new RuntimeException("device must be opened before sending this vendor request");
		}

		try {
			final SPIConfigSequence configSequence = new SPIConfigSequence();

			configSequence.addConfig(CypressFX3.FPGA_MUX, (short) 2, 1);
			configSequence.addConfig(CypressFX3.FPGA_MUX, (short) 2, 0);

			configSequence.sendConfigSequence();
		}
		catch (final HardwareInterfaceException e) {
			CypressFX3.log.warning("CypressFX3.resetTimestamps: couldn't send vendor request to reset timestamps");
		}
	}

	/**
	 * Is true if an overrun occured in the driver (> <code> AE_BUFFER_SIZE</code> events) during the period before the
	 * last time {@link #acquireAvailableEventsFromDriver } was called. This flag
	 * is cleared by {@link #acquireAvailableEventsFromDriver}, so you need to
	 * check it before you acquire the events.
	 * <p>
	 * If there is an overrun, the events grabbed are the most ancient; events after the overrun are discarded. The
	 * timestamps continue on but will probably be lagged behind what they should be.
	 *
	 * @return true if there was an overrun.
	 */
	@Override
	public boolean overrunOccurred() {
		return lastEventsAcquired.overrunOccuredFlag;
	}

	/**
	 * Closes the device. Never throws an exception.
	 */
	@Override
	synchronized public void close() {
		if (!isOpen()) {
			return;
		}

		try {
			setEventAcquisitionEnabled(false);

			if (asyncStatusThread != null) {
				asyncStatusThread.stopThread();
			}
		}
		catch (final HardwareInterfaceException e) {
			e.printStackTrace();
		}

		LibUsb.releaseInterface(deviceHandle, 0);
		LibUsb.close(deviceHandle);

		deviceHandle = null;
		deviceDescriptor = null;

		inEndpointEnabled = false;
		isOpened = false;
	}

	// not really necessary to stop this thread, i believe, because close will
	// unbind already according to usbio docs

	public void stopAEReader() {
		final AEReader reader = getAeReader();

		if (reader != null) {
                    log.info("Stopping thread "+reader);
			reader.stopThread();

			setAeReader(null);
		}
		else {
			CypressFX3.log.warning("null reader, nothing to stop");
		}
	}

	/**
	 * @return true if inEndpoint was enabled.
	 *         However, some other connection (e.g. biasgen) could have disabled
	 *         the in transfers.
	 */
	public boolean isInEndpointEnabled() {
		return inEndpointEnabled;
	}

	/**
	 * sends a vendor request to enable or disable in transfers of AEs
	 *
	 * @param inEndpointEnabled
	 *            true to send vendor request to enable, false to send request
	 *            to disable
	 */
	public void setInEndpointEnabled(final boolean inEndpointEnabled) throws HardwareInterfaceException {
		CypressFX3.log.info("Setting IN endpoint enabled=" + inEndpointEnabled);
		if (inEndpointEnabled) {
			enableINEndpoint();
		}
		else {
			disableINEndpoint();
		}
	}

	public final static short FPGA_MUX = 0;
	public final static short FPGA_DVS = 1; // GenericAERConfig, DVSAERConfig, AERKillConfig
	public final static short FPGA_APS = 2;
	public final static short FPGA_IMU = 3;
	public final static short FPGA_EXTINPUT = 4;
	public final static short FPGA_CHIPBIAS = 5; // Biases, Chip Config, Channel Config
	public final static short FPGA_SYSINFO = 6;
	public final static short FPGA_DAC = 7;
	public final static short FPGA_SCANNER = 8;
	public final static short FPGA_USB = 9;
	public final static short FPGA_ADC = 10;

	public class SPIConfigSequence {
		private class SPIConfigParameter {
			private final byte moduleAddr;
			private final byte paramAddr;
			private final byte[] param;

			public SPIConfigParameter(final short moduleAddr, final short paramAddr, final int param) {
				final byte[] configBytes = new byte[4];

				configBytes[0] = (byte) ((param >>> 24) & 0x00FF);
				configBytes[1] = (byte) ((param >>> 16) & 0x00FF);
				configBytes[2] = (byte) ((param >>> 8) & 0x00FF);
				configBytes[3] = (byte) ((param >>> 0) & 0x00FF);

				this.moduleAddr = (byte) moduleAddr;
				this.paramAddr = (byte) paramAddr;
				this.param = configBytes;
			}

			public byte getModuleAddr() {
				return moduleAddr;
			}

			public byte getParamAddr() {
				return paramAddr;
			}

			public byte[] getParam() {
				return param;
			}
		}

		private final List<SPIConfigParameter> configList;

		public SPIConfigSequence() {
			configList = new ArrayList<>();
		}

		private boolean canAddConfig() {
			// Max number of 6 bytes elements in 4096 bytes buffer is 682.
			return (configList.size() < 682);
		}

		public void addConfig(final short moduleAddr, final short paramAddr, final int param) throws HardwareInterfaceException {
			if (!canAddConfig()) {
				// Send current config, clean up for new one.
				sendConfigSequence();
			}

			configList.add(new SPIConfigParameter(moduleAddr, paramAddr, param));
		}

		public void sendConfigSequence() throws HardwareInterfaceException {
			// Build byte buffer and send it. 6 bytes per config parameter.
			final ByteBuffer buffer = BufferUtils.allocateByteBuffer(configList.size() * 6);

			for (final SPIConfigParameter cfg : configList) {
				buffer.put(cfg.getModuleAddr());
				buffer.put(cfg.getParamAddr());
				buffer.put(cfg.getParam());
			}

			sendVendorRequest(CypressFX3.VR_FPGA_CONFIG_MULTIPLE, (short) configList.size(), (short) 0, buffer);

			clearConfig();
		}

		private void clearConfig() {
			configList.clear();
		}
	}

	protected int adjustHWParam(final short moduleAddr, final short paramAddr, int param) {
		// No change by default.
		return (param);
	}

	public synchronized void spiConfigSend(final short moduleAddr, final short paramAddr, int param) throws HardwareInterfaceException {
		// Some values have to be adjusted based on hardware features, so we do that here.
		param = adjustHWParam(moduleAddr, paramAddr, param);

		final byte[] configBytes = new byte[4];

		configBytes[0] = (byte) ((param >>> 24) & 0x00FF);
		configBytes[1] = (byte) ((param >>> 16) & 0x00FF);
		configBytes[2] = (byte) ((param >>> 8) & 0x00FF);
		configBytes[3] = (byte) ((param >>> 0) & 0x00FF);

		// System.out.println(String.format("SPI Config sent with modAddr=%d, paramAddr=%d, value=%d.\n", moduleAddr,
		// paramAddr, param));

		sendVendorRequest(CypressFX3.VR_FPGA_CONFIG, moduleAddr, paramAddr, configBytes);
//                
//		int returnedParam = 0;
//
//		final ByteBuffer configBytesRecv = sendVendorRequestIN(CypressFX3.VR_FPGA_CONFIG, moduleAddr, paramAddr, 4);
//
//		returnedParam |= (configBytesRecv.get(0) & 0x00FF) << 24;
//		returnedParam |= (configBytesRecv.get(1) & 0x00FF) << 16;
//		returnedParam |= (configBytesRecv.get(2) & 0x00FF) << 8;
//		returnedParam |= (configBytesRecv.get(3) & 0x00FF) << 0;
//                
//                System.out.println(String.format("SPI Received back with modAddr=%d, paramAddr=%d, value=0x%x.\n", moduleAddr,
//		 paramAddr, returnedParam));
//                
//                if(returnedParam != param && (moduleAddr == 0 || moduleAddr == 1 || moduleAddr == 5 ))
//                {
//                    System.out.println("SPI write error!\n");
//                }
        }

	public synchronized int spiConfigReceive(final short moduleAddr, final short paramAddr) throws HardwareInterfaceException {
		int returnedParam = 0;

		final ByteBuffer configBytes = sendVendorRequestIN(CypressFX3.VR_FPGA_CONFIG, moduleAddr, paramAddr, 4);

		returnedParam |= (configBytes.get(0) & 0x00FF) << 24;
		returnedParam |= (configBytes.get(1) & 0x00FF) << 16;
		returnedParam |= (configBytes.get(2) & 0x00FF) << 8;
		returnedParam |= (configBytes.get(3) & 0x00FF) << 0;

		return (returnedParam);
	}

	protected synchronized void enableINEndpoint() throws HardwareInterfaceException {
		if (deviceHandle == null) {
			CypressFX3.log.warning("CypressFX3.enableINEndpoint(): null USBIO device");
			return;
		}

		final SPIConfigSequence configSequence = new SPIConfigSequence();
                
                spiConfigSend(CypressFX3.FPGA_MUX, (short) 3, 1);

                spiConfigSend(CypressFX3.FPGA_USB, (short) 0, 1);

                spiConfigSend(CypressFX3.FPGA_MUX, (short) 1, 1);

                spiConfigSend(CypressFX3.FPGA_MUX, (short) 0, 1);
//                
//		configSequence.addConfig(CypressFX3.FPGA_MUX, (short) 3, 1); // Enable biasgen.
//
//		configSequence.addConfig(CypressFX3.FPGA_USB, (short) 0, 1); // Enable USB.
//
//		configSequence.addConfig(CypressFX3.FPGA_MUX, (short) 1, 1); // Enable timestamps.
//		configSequence.addConfig(CypressFX3.FPGA_MUX, (short) 0, 1); // Enable mux.
//
//		configSequence.sendConfigSequence();
                
 		inEndpointEnabled = true;
	}

	/**
	 * // stop endpoint sending events by sending vendor request 0xb4 to control
	 * endpoint 0
	 * // these requests are documented in firmware file FX2_to_extFIFO.c
	 */
	protected synchronized void disableINEndpoint() {
		try {
			spiConfigSend(CypressFX3.FPGA_EXTINPUT, (short) 0, 0); // Disable ext detector.
			spiConfigSend(CypressFX3.FPGA_IMU, (short) 2, 0); // Disable IMU accel.
			spiConfigSend(CypressFX3.FPGA_IMU, (short) 3, 0); // Disable IMU gyro.
			spiConfigSend(CypressFX3.FPGA_IMU, (short) 4, 0); // Disable IMU temp.
			spiConfigSend(CypressFX3.FPGA_APS, (short) 4, 0); // Disable APS.
			spiConfigSend(CypressFX3.FPGA_DVS, (short) 3, 0); // Disable DVS.

			spiConfigSend(CypressFX3.FPGA_MUX, (short) 1, 0); // Disable timestamps.
			spiConfigSend(CypressFX3.FPGA_MUX, (short) 0, 0); // Disable mux.

			spiConfigSend(CypressFX3.FPGA_USB, (short) 0, 0); // Disable USB.

			spiConfigSend(CypressFX3.FPGA_MUX, (short) 3, 0); // Disable biasgen
                        
//			final SPIConfigSequence configSequence = new SPIConfigSequence();
                        
//			configSequence.addConfig(CypressFX3.FPGA_EXTINPUT, (short) 0, 0); // Disable ext detector.
//			configSequence.addConfig(CypressFX3.FPGA_IMU, (short) 2, 0); // Disable IMU accel.
//			configSequence.addConfig(CypressFX3.FPGA_IMU, (short) 3, 0); // Disable IMU gyro.
//			configSequence.addConfig(CypressFX3.FPGA_IMU, (short) 4, 0); // Disable IMU temp.
//			configSequence.addConfig(CypressFX3.FPGA_APS, (short) 4, 0); // Disable APS.
//			configSequence.addConfig(CypressFX3.FPGA_DVS, (short) 3, 0); // Disable DVS.
//
//			configSequence.addConfig(CypressFX3.FPGA_MUX, (short) 1, 0); // Disable timestamps.
//			configSequence.addConfig(CypressFX3.FPGA_MUX, (short) 0, 0); // Disable mux.
//
//			configSequence.addConfig(CypressFX3.FPGA_USB, (short) 0, 0); // Disable USB.
//
//			configSequence.addConfig(CypressFX3.FPGA_MUX, (short) 3, 0); // Disable biasgen.
//
//			configSequence.sendConfigSequence();
		}
		catch (final HardwareInterfaceException e) {
			CypressFX3.log.info(
				"disableINEndpoint: couldn't send vendor request to disable IN transfers--it could be that device is gone or sendor is OFF and and completing GPIF cycle");
		}

		inEndpointEnabled = false;
	}

	@Override
	public PropertyChangeSupport getReaderSupport() {
		return support;
	}

	/**
	 * This threads reads asynchronous status or other data from the device.
	 * It handles timestamp reset messages from the device and possibly other
	 * types of data.
	 * It fires PropertyChangeEvent {@link #PROPERTY_CHANGE_ASYNC_STATUS_MSG} on
	 * receiving a message
	 *
	 * @author tobi delbruck
	 * @see #getSupport()
	 */
	private class AsyncStatusThread {
		USBTransferThread usbTransfer;

		public void stopThread() {
			usbTransfer.interrupt();

			try {
				usbTransfer.join();
			}
			catch (final InterruptedException e) {
				CypressFX3.log.severe("Failed to join AsyncStatusThread");
			}
		}
	}

	private int aeReaderFifoSize = CypressFX3.prefs.getInt("CypressFX3.AEReader.fifoSize", 8192);

	/**
	 * sets the buffer size for the aereader thread. optimal size depends on
	 * event rate, for high event
	 * rates, at least 8k or 16k bytes should be chosen, and low event rates
	 * need smaller
	 * buffer size to produce suitable frame rates
	 */
	public void setAEReaderFifoSize(final int size) {
		aeReaderFifoSize = size;
		CypressFX3.prefs.putInt("CypressFX3.AEReader.fifoSize", size);
	}

	private int aeReaderNumBuffers = CypressFX3.prefs.getInt("CypressFX3.AEReader.numBuffers", 8);

	/** sets the number of buffers for the aereader thread. */
	public void setAEReaderNumBuffers(final int number) {
		aeReaderNumBuffers = number;
		CypressFX3.prefs.putInt("CypressFX3.AEReader.numBuffers", number);
	}
  
	/**
	 * AE reader class. the thread continually reads events into buffers. when a
	 * buffer is read, ProcessData transfers
	 * and transforms the buffer data to AE address
	 * and timestamps information and puts it in the addresses and timestamps
	 * arrays. a call to
	 * acquireAvailableEventsFromDriver copies the events to enw user
	 * arrays that can be accessed by getEvents() (this packet is also returned
	 * by {@link #acquireAvailableEventsFromDriver}). The relevant methods are
	 * synchronized so are thread safe.
	 */
	public class AEReader implements ReaderBufferControl {
		/**
		 * the number of capture buffers for the buffer pool for the translated
		 * address-events.
		 * These buffers allow for smoother access to buffer space by the event
		 * capture thread
		 */
		private int numBuffers;
		/**
		 * size of FIFOs in bytes used in AEReader for event capture from
		 * device.
		 * This does not have to be the same size as the FIFOs in the CypressFX3
		 * (512 bytes). If it is too small, then
		 * there
		 * are frequent thread context switches that can greatly slow down
		 * rendering loops.
		 */
		private int fifoSize;

		USBTransferThread usbTransfer;
		CypressFX3 monitor;

		public AEReader(final CypressFX3 m) throws HardwareInterfaceException {
			monitor = m;
			fifoSize = monitor.aeReaderFifoSize;
			numBuffers = monitor.aeReaderNumBuffers;
		}

		public void startThread() {
			if (!isOpen()) {
				try {
					open();
				}
				catch (final HardwareInterfaceException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
      
			CypressFX3.log.info("Starting AEReader");
			usbTransfer = new USBTransferThread(monitor.deviceHandle, CypressFX3.AE_MONITOR_ENDPOINT_ADDRESS, LibUsb.TRANSFER_TYPE_BULK,
				new ProcessAEData(), getNumBuffers(), getFifoSize(), null, null, new Runnable() {
					@Override
					public void run() {
						final SwingWorker<Void, Void> shutdownWorker = new SwingWorker<Void, Void>() {
							@Override
							public Void doInBackground() {
								monitor.close();

								return (null);
							}
						};
						shutdownWorker.execute();
					}
				});
			usbTransfer.setName("AEReaderThread");
			usbTransfer.start();

			getSupport().firePropertyChange("readerStarted", false, true);
		}

		public void stopThread() {
                    if(usbTransfer==null){
                        log.warning("USB transfer thread became null before stopThread was called; doing nothing");
                        return;
                    }
                    log.info("Interrupting USBTransferThread "+usbTransfer);
			usbTransfer.interrupt();

			try {
                            log.info("Waiting for USBTransferThread to join()");
				usbTransfer.join(Duration.ofSeconds(1));
                                log.info("USBTransferThread join()'ed");
			}
			catch (final InterruptedException e) {
				CypressFX3.log.severe("Failed to join AEReaderThread");
			}
		}

		@Override
		public String toString() {
			return "AEReader for " + CypressFX3.this;
		}

		/**
		 * Subclasses must override this method to process the raw data to write
		 * to the raw event packet buffers.
		 *
		 * @param buf
		 *            the raw byte buffers
		 */
		protected void translateEvents(final ByteBuffer buffer) {
			CypressFX3.log.severe("Error: This method should never be called, it must be overridden!");
		}

		class ProcessAEData implements RestrictedTransferCallback {
			@Override
			public void prepareTransfer(final RestrictedTransfer transfer) {
				// Nothing to do here.
			}

			/**
			 * Called on completion of read on a data buffer is received from
			 * USBIO driver.
			 *
			 * @param Buf
			 *            the data buffer with raw data
			 */
			@Override
			public void processTransfer(final RestrictedTransfer transfer) {
				synchronized (aePacketRawPool) {
					if (transfer.status() == LibUsb.TRANSFER_COMPLETED) {
						usbPacketStatistics.addSample(transfer);
						translateEvents(transfer.buffer());

						if ((chip != null) && (chip.getFilterChain() != null)
							&& (chip.getFilterChain().getProcessingMode() == FilterChain.ProcessingMode.ACQUISITION)) {
							// here we do the realTimeFiltering. We finished
							// capturing this buffer's worth of events,
							// now process them apply realtime filters and
							// realtime (packet level) mapping

							// synchronize here so that rendering thread doesn't
							// swap the buffer out from under us while
							// we process these events
							// aePacketRawPool.writeBuffer is also synchronized
							// so we the same lock twice which is ok
							final AEPacketRaw buffer = aePacketRawPool.writeBuffer();
							final int[] addresses = buffer.getAddresses();
							final int[] timestamps = buffer.getTimestamps();
							realTimeFilter(addresses, timestamps);
						}
					}
					else if(transfer.status() != LibUsb.TRANSFER_CANCELLED){
						CypressFX3.log.warning("ProcessAEData: Bytes transferred: " + transfer.actualLength() + "  Status: "
							+ LibUsb.errorName(transfer.status()));
					}
				}
			}
		}

		/** size of CypressFX3 USB fifo's in bytes. */
		public static final int CYPRESS_FIFO_SIZE = 512;

		@Override
		public int getFifoSize() {
			return fifoSize;
		}

		@Override
		public void setFifoSize(int fifoSize) {
			if (fifoSize < AEReader.CYPRESS_FIFO_SIZE) {
				CypressFX3.log.warning("CypressFX3 fifo size clipped to device FIFO size " + AEReader.CYPRESS_FIFO_SIZE);
				fifoSize = AEReader.CYPRESS_FIFO_SIZE;
			}

			this.fifoSize = fifoSize;

			usbTransfer.setBufferSize(fifoSize);

			CypressFX3.prefs.putInt("CypressFX3.AEReader.fifoSize", fifoSize);
		}

		@Override
		public int getNumBuffers() {
			return numBuffers;
		}

		@Override
		public void setNumBuffers(final int numBuffers) {
			this.numBuffers = numBuffers;

			usbTransfer.setBufferNumber(numBuffers);

			CypressFX3.prefs.putInt("CypressFX3.AEReader.numBuffers", numBuffers);
		}

		/**
		 * Applies the filterChain processing on the most recently captured
		 * data. The processing is done
		 * by extracting the events just captured and then applying the filter
		 * chain.
		 * <strong>The filter outputs are discarded and
		 * will not be visble in the rendering of the chip output, but may be
		 * used for motor control or other purposes.
		 * </strong>
		 * <p>
		 * TODO: at present this processing is redundant in that the most recently captured events are copied to a
		 * different AEPacketRaw, extracted to an EventPacket, and then processed. This effort is duplicated later in
		 * rendering. This should be fixed somehow.
		 *
		 * @param addresses
		 *            the raw input addresses; these are filtered in place
		 * @param timestamps
		 *            the input timestamps
		 */
		private void realTimeFilter(final int[] addresses, final int[] timestamps) {

			if (!chip.getFilterChain().isAnyFilterEnabled()) {
				return;
			}
			final int nevents = getNumRealTimeEvents();

			// initialize packets
			if (realTimeRawPacket == null) {
				realTimeRawPacket = new AEPacketRaw(nevents); // TODO: expensive
			}
			else {
				realTimeRawPacket.ensureCapacity(nevents);// // copy data to
				// real time raw
				// packet
				// if(addresses==null || timestamps==null){
				// log.warning("realTimeFilter: addresses or timestamp array became null");
				// }else{
			}
			try {
				System.arraycopy(addresses, realTimeEventCounterStart, realTimeRawPacket.getAddresses(), 0, nevents);
				System.arraycopy(timestamps, realTimeEventCounterStart, realTimeRawPacket.getTimestamps(), 0, nevents);
			}
			catch (final IndexOutOfBoundsException e) {
				e.printStackTrace();
			}
			realTimeEventCounterStart = eventCounter;
			// System.out.println("RealTimeEventCounterStart: " +
			// realTimeEventCounterStart + " nevents " + nevents +
			// " eventCounter " + eventCounter);
			realTimeRawPacket.setNumEvents(nevents);
			// init extracted packet
			// if(realTimePacket==null)
			// realTimePacket=new EventPacket(chip.getEventClass());
			// extract events for this filter. This duplicates later effort
			// during rendering and should be fixed for
			// later.
			// at present this may mess up everything else because the output
			// packet is reused.

			// hack for stereo hardware interfaces - for real time processing we
			// must label the eye bit here based on
			// which eye our hardware
			// interface is. note the events are labeled here and the real time
			// processing method is called for each low
			// level hardware interface.
			// But each call will only getString events from one eye. it is
			// important that the filterPacket method be
			// sychronized (thread safe) because the
			// filter object may getString called by both AEReader threads at
			// the "same time"
			if (chip.getHardwareInterface() instanceof StereoPairHardwareInterface) {
				final StereoPairHardwareInterface stereoInterface = (StereoPairHardwareInterface) chip.getHardwareInterface();
				if (stereoInterface.getAemonLeft() == CypressFX3.this) {
					stereoInterface.labelLeftEye(realTimeRawPacket);
				}
				else {
					stereoInterface.labelRightEye(realTimeRawPacket);
				}
			}
			// regardless, we now extract to typed events for example and
			// process
			realTimePacket = chip.getEventExtractor().extractPacket(realTimeRawPacket); // ,realTimePacket);
			realTimePacket.setRawPacket(realTimeRawPacket);

			try {
				getChip().getFilterChain().filterPacket(realTimePacket);
			}
			catch (final Exception e) {
				CypressFX3.log.warning(e.toString() + ": disabling all filters");
				e.printStackTrace();
				for (final EventFilter f : getChip().getFilterChain()) {
					f.setFilterEnabled(false);
				}
			}
			// we don't do following because the results are an AEPacketRaw that
			// still needs to be written to
			// addresses/timestamps
			// and this is not done yet. at present results of realtime
			// filtering are just not rendered at all.
			// that means that user will see raw events, e.g. if
			// BackgroundActivityFilter is used, then user will still
			// see all
			// events because the filters are not applied for normal rendering.
			// (If they were applied, then the filters
			// would
			// be in a funny state becaues they would process the same data more
			// than once and out of order, resulting
			// in all kinds
			// of problems.)
			// However, the graphical annotations (like the boxes drawn around
			// clusters in RectangularClusterTracker)
			// done by the real time processing are still shown when the
			// rendering thread calls the
			// annotate methods.

			// chip.getEventExtractor().reconstructRawPacket(realTimePacket);
		}

		@Override
		public PropertyChangeSupport getReaderSupport() {
			return support;
		}
	}

	private int getNumRealTimeEvents() {
		return eventCounter - realTimeEventCounterStart;
	}

	/**
	 * Allocates internal memory for transferring data from reader to consumer,
	 * e.g. rendering.
	 */
	protected void allocateAEBuffers() {
		synchronized (aePacketRawPool) {
			aePacketRawPool.allocateMemory();
		}
	}

	/**
	 * @return the size of the double buffer raw packet for AEs
	 */
	@Override
	public int getAEBufferSize() {
		return aeBufferSize; // aePacketRawPool.writeBuffer().getCapacity();
	}

	/**
	 * set the size of the raw event packet buffer. Default is AE_BUFFER_SIZE.
	 * You can set this larger if you
	 * have overruns because your host processing (e.g. rendering) is taking too
	 * long.
	 * <p>
	 * This call discards collected events.
	 *
	 * @param size
	 *            of buffer in events
	 */
	@Override
	public void setAEBufferSize(final int size) {
		if ((size < 1000) || (size > 2000000)) {
			CypressFX3.log
				.warning("ignoring unreasonable aeBufferSize of " + size + ", choose a more reasonable size between 1000 and 1000000");
			return;
		}
		aeBufferSize = size;
		CypressFX3.prefs.putInt("CypressFX3.aeBufferSize", aeBufferSize);
		allocateAEBuffers();
	}

	/**
	 * start or stops the event acquisition. sends appropriate vendor request to
	 * device and starts or stops the AEReader.
	 * Thread-safe on hardware interface.
	 *
	 * @param enable
	 *            boolean to enable or disable event acquisition
	 */
	@Override
	public synchronized void setEventAcquisitionEnabled(final boolean enable) throws HardwareInterfaceException {
		// Start reader before sending data enable commands.
		if (enable) {
			startAEReader();
		}
		else {
                    log.info("stopping AEReader");
			stopAEReader();
		}

		setInEndpointEnabled(enable);
	}

	@Override
	public boolean isEventAcquisitionEnabled() {
		return isInEndpointEnabled();
	}

	@Override
	public String getTypeName() {
		return "CypressFX3";
	}

	/** the first USB string descriptor (Vendor name) (if available) */
	protected String stringDescriptor1 = null;
	/** the second USB string descriptor (Product name) (if available) */
	protected String stringDescriptor2 = null;
	/** the third USB string descriptor (Serial number) (if available) */
	protected String stringDescriptor3 = null;
	/**
	 * The number of string desriptors - all devices have at least two (Vendor
	 * and Product strings) but some may have in
	 * addition
	 * a third serial number string. Default value is 2. Initialized to zero
	 * until device descriptors have been
	 * obtained.
	 */
	protected int numberOfStringDescriptors = 0;

	/**
	 * returns number of string descriptors
	 *
	 * @return number of string descriptors: 2 for TmpDiff128, 3 for
	 *         MonitorSequencer
	 */
	public int getNumberOfStringDescriptors() {
		return numberOfStringDescriptors;
	}

	/** the USBIO device descriptor */
	protected DeviceDescriptor deviceDescriptor = null;

	/**
	 * checks if device has a string identifier that is a non-empty string
	 *
	 * @return false if not, true if there is one
	 */
	private boolean hasStringIdentifier() {
		// getString string descriptor
		final String stringDescriptor1 = LibUsb.getStringDescriptor(deviceHandle, (byte) 1);

		if (stringDescriptor1 == null) {
			return false;
		}
		else if (stringDescriptor1.length() > 0) {
			return true;
		}

		return false;
	}

	/**
	 * Constructs a new USB connection and opens it. Does NOT start event
	 * acquisition.
	 *
	 * @see #setEventAcquisitionEnabled
	 * @throws HardwareInterfaceException
	 *             if there is an error opening device
	 * @see #open_minimal_close()
	 */
	@Override
	synchronized public void open() throws HardwareInterfaceException {
		// device has already been UsbIo Opened by now, in factory

		// opens the USBIOInterface device, configures it, binds a reader thread
		// with buffer pool to read from the
		// device and starts the thread reading events.
		// we got a UsbIo object when enumerating all devices and we also made a
		// device list. the device has already
		// been
		// opened from the UsbIo viewpoint, but it still needs firmware
		// download, setting up pipes, etc.

		if (isOpen()) {
			return;
		}

		int status;

		// Open device.
		if (deviceHandle == null) {
			deviceHandle = new DeviceHandle();
			status = LibUsb.open(device, deviceHandle);
			if (status != LibUsb.SUCCESS) {
				throw new HardwareInterfaceException("open(): failed to open device: " + LibUsb.errorName(status));
			}
		}

		// Check for blank devices (must first get device descriptor).
		if (deviceDescriptor == null) {
			deviceDescriptor = new DeviceDescriptor();
			LibUsb.getDeviceDescriptor(device, deviceDescriptor);
		}

		if (isBlankDevice()) { // TODO throws null pointer exception if deviceDescriptor is not obtained above, which
								// tobi has seen occur
			CypressFX3.log.warning("open(): blank device detected, downloading preferred firmware");

			isOpened = true;
			CypressFX3.log.severe("USE FLASHY FOR FIRMWARE UPLOAD!");
			isOpened = false;

			boolean success = false;
			int triesLeft = 10;
			status = 0;

			while (!success && (triesLeft > 0)) {
				try {
					Thread.sleep(1000);
				}
				catch (final InterruptedException e) {
				}

				try {
					LibUsb.close(deviceHandle);

					status = LibUsb.open(device, deviceHandle);
					if (status != LibUsb.SUCCESS) {
						triesLeft--;
					}
					else {
						success = true;
					}
				}
				catch (final IllegalStateException e) {
					// Ignore.
				}
			}

			if (!success) {
				throw new HardwareInterfaceException(
					"open(): couldn't reopen device after firmware download and re-enumeration: " + LibUsb.errorName(status));
			}
			else {
				throw new HardwareInterfaceException(
					"open(): device firmware downloaded successfully, a new instance must be constructed by the factory using the new VID/PID settings");
			}
		}

		// Initialize device.
		if (deviceDescriptor.bNumConfigurations() != 1) {
			throw new HardwareInterfaceException(
				"number of configurations=" + deviceDescriptor.bNumConfigurations() + " is not 1 like it should be");
		}

                final IntBuffer activeConfig = BufferUtils.allocateIntBuffer();
                try {
                    LibUsb.getConfiguration(deviceHandle, activeConfig);
                } catch (IllegalStateException e) {
                    log.log(Level.SEVERE, "Exception try to LibUsb.getConfiguration: "+ e.toString(), e);
                    return;
                }
		if (activeConfig.get() != 1) {
			LibUsb.setConfiguration(deviceHandle, 1);
		}

            try {
                LibUsb.claimInterface(deviceHandle, 0);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Exception try to LibUsb.claimInterface: " + e.toString(), e);
                return;
            }
		populateDescriptors();

		isOpened = true;

		CypressFX3.log.info("open(): device opened");

		if (LibUsb.getDeviceSpeed(device) == LibUsb.SPEED_FULL) {
			CypressFX3.log.warning("Device is not operating at USB 2.0 High Speed, performance will be limited to about 300 keps");
		}

		// start the thread that listens for device status information.
		// This is only preset on FX3 devices.
		// if (getPID() == DAViSFX3HardwareInterface.PID_FX3) {
		// asyncStatusThread = new AsyncStatusThread(this);
		// asyncStatusThread.startThread();
		// TODO: fix USBTransferThread to only use one thread to handle USB events.
		// }
	}

	/**
	 * Opens the device just enough to read the device descriptor but does not
	 * start the reader or writer
	 * thread. If the device does not have a string descriptor it is assumed
	 * that firmware must be downloaded to
	 * device RAM and this is done automatically. The device is <strong>not
	 * configured by this method. Vendor requests
	 * and
	 * probably other functionality will not be available.</strong>. The device
	 * is left open this method returns.
	 *
	 * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
	 * @see #open()
	 */
	synchronized protected void open_minimal_close() throws HardwareInterfaceException {
		// device has already been UsbIo Opened by now, in factory

		// opens the USBIOInterface device, configures it, binds a reader thread
		// with buffer pool to read from the
		// device and starts the thread reading events.
		// we got a UsbIo object when enumerating all devices and we also made a
		// device list. the device has already
		// been
		// opened from the UsbIo viewpoint, but it still needs firmware
		// download, setting up pipes, etc.

		if (isOpen()) {
			return;
		}

		int status;

		// Open device.
		if (deviceHandle == null) {
			deviceHandle = new DeviceHandle();
			status = LibUsb.open(device, deviceHandle);
			if (status != LibUsb.SUCCESS) {
				throw new HardwareInterfaceException("open_minimal_close(): failed to open device: " + LibUsb.errorName(status));
			}
		}

		// Check for blank devices (must first get device descriptor).
		if (deviceDescriptor == null) {
			deviceDescriptor = new DeviceDescriptor();
			LibUsb.getDeviceDescriptor(device, deviceDescriptor);
		}

		if (isBlankDevice()) {
			throw new BlankDeviceException("Blank Cypress FX2");
		}

		if (!hasStringIdentifier()) { // TODO: does this really ever happen, a
			// non-blank device with invalid fw?
			CypressFX3.log.warning("open_minimal_close(): blank device detected, downloading preferred firmware");

			CypressFX3.log.severe("USE FLASHY FOR FIRMWARE UPLOAD!");

			boolean success = false;
			int triesLeft = 10;
			status = 0;

			while (!success && (triesLeft > 0)) {
				try {
					Thread.sleep(1000);
				}
				catch (final InterruptedException e) {
				}

				LibUsb.close(deviceHandle);

				status = LibUsb.open(device, deviceHandle);
				if (status != LibUsb.SUCCESS) {
					triesLeft--;
				}
				else {
					success = true;
				}
			}

			if (!success) {
				throw new HardwareInterfaceException(
					"open_minimal_close(): couldn't reopen device after firmware download and re-enumeration: " + LibUsb.errorName(status));
			}
			else {
				throw new HardwareInterfaceException(
					"open_minimal_close(): device firmware downloaded successfully, a new instance must be constructed by the factory using the new VID/PID settings");
			}
		}

		populateDescriptors();

		// And close opened resources again.
		LibUsb.close(deviceHandle);

		deviceHandle = null;
		deviceDescriptor = null;
	}

	/**
	 * return the string USB descriptors for the device
	 *
	 * @return String[] of length 2 or 3 of USB descriptor strings.
	 */
	@Override
	public String[] getStringDescriptors() {
		if (stringDescriptor1 == null) {
			CypressFX3.log.warning("USBAEMonitor: getStringDescriptors called but device has not been opened");

			final String[] s = new String[numberOfStringDescriptors];

			for (int i = 0; i < numberOfStringDescriptors; i++) {
				s[i] = "";
			}

			return s;
		}

		final String[] s = new String[numberOfStringDescriptors];

		s[0] = (stringDescriptor1 == null) ? ("") : (stringDescriptor1);
		s[1] = (stringDescriptor2 == null) ? ("") : (stringDescriptor2);
		if (numberOfStringDescriptors == 3) {
			s[2] = (stringDescriptor3 == null) ? ("") : (stringDescriptor3);
		}

		return s;
	}

	@Override
	public short getVID_THESYCON_FX2_CPLD() {
		if (deviceDescriptor == null) {
			CypressFX3.log.warning("USBAEMonitor: getVID called but device has not been opened");
			return 0;
		}
		// int[] n=new int[2]; n is never used
		return deviceDescriptor.idVendor();
	}

	@Override
	public short getPID() {
		if (deviceDescriptor == null) {
			CypressFX3.log.warning("USBAEMonitor: getPID called but device has not been opened");
			return 0;
		}
		return deviceDescriptor.idProduct();
	}

	/**
	 * @return bcdDevice (the binary coded decimel device version
	 */
	@Override
	public short getDID() { // this is not part of USB spec in device
		// descriptor.
		if (deviceDescriptor == null) {
			CypressFX3.log.warning("USBAEMonitor: getDID called but device has not been opened");
			return 0;
		}
		return deviceDescriptor.bcdDevice();
	}

	/**
	 * reports if interface is {@link #open}.
	 *
	 * @return true if already open
	 */
	@Override
	synchronized public boolean isOpen() {
		return isOpened;
	}

	/**
	 * @return timestamp tick in us
	 *         NOTE: DOES NOT RETURN THE TICK OF THE USBAERmini2 board
	 */
	@Override
	final public int getTimestampTickUs() {
		return TICK_US;
	}

	/**
	 * returns last events from {@link #acquireAvailableEventsFromDriver}
	 *
	 * @return the event packet
	 */
	@Override
	public AEPacketRaw getEvents() {
		return lastEventsAcquired;
	}

	/**
	 * Sends a vendor request without any data packet, value and index are set
	 * to zero. This is a blocking method.
	 *
	 * @param request
	 *            the vendor request byte, identifies the request on the device
	 */
	synchronized public void sendVendorRequest(final byte request) throws HardwareInterfaceException {
		sendVendorRequest(request, (short) 0, (short) 0);
	}

	/**
	 * Sends a vendor request without any data packet but with request, value
	 * and index. This is a blocking method.
	 *
	 * @param request
	 *            the vendor request byte, identifies the request on the device
	 * @param value
	 *            the value of the request (bValue USB field)
	 * @param index
	 *            the "index" of the request (bIndex USB field)
	 */
	synchronized public void sendVendorRequest(final byte request, final short value, final short index) throws HardwareInterfaceException {
		sendVendorRequest(request, value, index, (ByteBuffer) null);
	}

	/**
	 * Sends a vendor request with a given byte[] as data. This is a blocking
	 * method.
	 *
	 * @param request
	 *            the vendor request byte, identifies the request on the device
	 * @param value
	 *            the value of the request (bValue USB field)
	 * @param index
	 *            the "index" of the request (bIndex USB field)
	 * @param bytes
	 *            the data which is to be transmitted to the device
	 */
	synchronized public void sendVendorRequest(final byte request, final short value, final short index, final byte[] bytes)
		throws HardwareInterfaceException {
		sendVendorRequest(request, value, index, bytes, 0, bytes.length);
	}

	/**
	 * Sends a vendor request with a given byte[] as data. This is a blocking
	 * method.
	 *
	 * @param request
	 *            the vendor request byte, identifies the request on the device
	 * @param value
	 *            the value of the request (bValue USB field)
	 * @param index
	 *            the "index" of the request (bIndex USB field)
	 * @param bytes
	 *            the data which is to be transmitted to the device
	 * @param pos
	 *            position at which to start consuming the bytes array
	 * @param length
	 *            number of bytes to copy, starting at position pos
	 */
	synchronized public void sendVendorRequest(final byte request, final short value, final short index, final byte[] bytes, final int pos,
		final int length) throws HardwareInterfaceException {
		final ByteBuffer dataBuffer = BufferUtils.allocateByteBuffer(length);

		dataBuffer.put(bytes, pos, length);

		sendVendorRequest(request, value, index, dataBuffer);
	}

	/**
	 * Sends a vendor request with data (including special bits). This is a
	 * blocking method.
	 *
	 * @param requestType
	 *            the vendor requestType byte (used for special cases, usually
	 *            0)
	 * @param request
	 *            the vendor request byte, identifies the request on the device
	 * @param value
	 *            the value of the request (bValue USB field)
	 * @param index
	 *            the "index" of the request (bIndex USB field)
	 * @param dataBuffer
	 *            the data which is to be transmitted to the device (null means
	 *            no data)
	 */
	synchronized public void sendVendorRequest(final byte request, final short value, final short index, ByteBuffer dataBuffer)
		throws HardwareInterfaceException {
		if (!isOpen()) {
			open();
		}

		if (dataBuffer == null) {
			dataBuffer = BufferUtils.allocateByteBuffer(0);
		}

		// System.out.println(String.format("Sent VR %X, wValue %X, wIndex %X, wLength %d.\n", request, value, index,
		// dataBuffer.limit()));
                
		final byte bmRequestType = (byte) (LibUsb.ENDPOINT_OUT | LibUsb.REQUEST_TYPE_VENDOR | LibUsb.RECIPIENT_DEVICE);
                
		final int status = LibUsb.controlTransfer(deviceHandle, bmRequestType, request, value, index, dataBuffer, 0);
                if (status < LibUsb.SUCCESS) {
			throw new HardwareInterfaceException(
				"Unable to send vendor OUT request " + String.format("0x%x", request) + ": " + LibUsb.errorName(status));
		}

		if (status != dataBuffer.capacity()) {
			throw new HardwareInterfaceException(
				"Wrong number of bytes transferred, wanted: " + dataBuffer.capacity() + ", got: " + status);
		}
	}

	/**
	 * Sends a vendor request to receive (IN direction) data. This is a blocking
	 * method.
	 *
	 * @param request
	 *            the vendor request byte, identifies the request on the device
	 * @param value
	 *            the value of the request (bValue USB field)
	 * @param index
	 *            the "index" of the request (bIndex USB field)
	 * @param dataLength
	 *            amount of data to receive, determines size of returned buffer
	 *            (must be greater than 0)
	 * @return a buffer containing the data requested from the device
	 */
	synchronized public ByteBuffer sendVendorRequestIN(final byte request, final short value, final short index, final int dataLength)
		throws HardwareInterfaceException {
		if (dataLength == 0) {
			throw new HardwareInterfaceException("Unable to send vendor IN request with dataLength of zero!");
		}

		if (!isOpen()) {
			open();
		}

		final ByteBuffer dataBuffer = BufferUtils.allocateByteBuffer(dataLength);

		final byte bmRequestType = (byte) (LibUsb.ENDPOINT_IN | LibUsb.REQUEST_TYPE_VENDOR | LibUsb.RECIPIENT_DEVICE);

		final int status = LibUsb.controlTransfer(deviceHandle, bmRequestType, request, value, index, dataBuffer, 0);
		if (status < LibUsb.SUCCESS) {
			throw new HardwareInterfaceException(
				"Unable to send vendor IN request " + String.format("0x%x", request) + ": " + LibUsb.errorName(status));
		}

		if (status != dataLength) {
			throw new HardwareInterfaceException("Wrong number of bytes transferred, wanted: " + dataLength + ", got: " + status);
		}

		// Update ByteBuffer internal limit to show how much was successfully
		// read.
		// usb4java never touches the ByteBuffer's internals by design, so we do
		// it here.
		dataBuffer.limit(dataLength);

		return (dataBuffer);
	}

	public AEReader getAeReader() {
		return aeReader;
	}

	public void setAeReader(final AEReader aeReader) {
		this.aeReader = aeReader;
	}

	@Override
	public int getFifoSize() {
		if (aeReader == null) {
			return -1;
		}
		else {
			return aeReader.getFifoSize();
		}
	}

	@Override
	public void setFifoSize(final int fifoSize) {
		if (aeReader == null) {
			return;
		}

		aeReader.setFifoSize(fifoSize);
	}

	@Override
	public int getNumBuffers() {
		if (aeReader == null) {
			return 0;
		}
		else {
			return aeReader.getNumBuffers();
		}
	}

	@Override
	public void setNumBuffers(final int numBuffers) {
		if (aeReader == null) {
			return;
		}

		aeReader.setNumBuffers(numBuffers);
	}

	@Override
	public void setChip(final AEChip chip) {
		this.chip = chip;
	}

	@Override
	public AEChip getChip() {
		return chip;
	}

	/** Resets the USB device using USBIO resetDevice */
	public void resetUSB() {
		if (deviceHandle == null) {
			return;
		}

		final int status = LibUsb.resetDevice(deviceHandle);
		if (status != LibUsb.SUCCESS) {
			CypressFX3.log.warning("Error resetting device: " + LibUsb.errorName(status));
		}
	}

	/**
	 * Checks for blank cypress VID/PID.
	 * Device deviceDescriptor must be populated before calling this method.
	 *
	 * @return true if blank
	 */
        protected boolean isBlankDevice() throws HardwareInterfaceException {
            try {
                if ((deviceDescriptor.idVendor() == CypressFX3.VID_BLANK) && (deviceDescriptor.idProduct() == CypressFX3.PID_BLANK)) {
                    // log.warning("blank CypressFX3 detected");
                    return true;
                }
                return false;
            } catch (java.lang.IllegalStateException ex) {
                throw new HardwareInterfaceException("exception when checking for blank device ", ex);
            }
        }

	@Override
	public void setShowUsbStatistics(boolean yes) {
		usbPacketStatistics.setShowUsbStatistics(yes);
	}

	@Override
	public void setPrintUsbStatistics(boolean yes) {
		usbPacketStatistics.setPrintUsbStatistics(yes);
	}

	@Override
	public boolean isShowUsbStatistics() {
		return usbPacketStatistics.isShowUsbStatistics();
	}

	@Override
	public boolean isPrintUsbStatistics() {
		return usbPacketStatistics.isPrintUsbStatistics();
	}

	private AtomicBoolean isMaster = new AtomicBoolean(true);

	public boolean isTimestampMaster() {
		return isMaster.get();
	}

	protected void updateTimestampMasterStatus() {
		final SwingWorker<Void, Void> masterUpdateWorker = new SwingWorker<Void, Void>() {
			@Override
			public Void doInBackground() {
				try {
					final int isMasterSPI = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 2);

					isMaster.set(isMasterSPI != 0);
				}
				catch (HardwareInterfaceException e) {
					// Ignore exceptions.
				}

				return (null);
			}
		};

		masterUpdateWorker.execute();
	}
}
