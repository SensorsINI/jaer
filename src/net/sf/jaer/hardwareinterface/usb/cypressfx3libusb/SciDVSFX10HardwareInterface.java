/*
 * SciDVSFX10HardwareInterface.java
 *
 * Hardware interface for the SciDVS camera on the Infineon EZ-USB FX10
 * (CYUSB4014) USB controller, VID 0x152A PID 0x8420.
 *
 * The FX10 firmware (see scidvs_fx10 ModusToolbox project) implements an
 * FX3-compatible vendor-request set on EP0:
 *
 *   0xB2 VR_DEVICE_RESET          reset device
 *   0xBF VR_CHIP_CONFIG           wValue=moduleAddr, wIndex=paramAddr,
 *                                 4-byte big-endian-on-the-wire value
 *                                 (same framing as CypressFX3.VR_FPGA_CONFIG,
 *                                 so spiConfigSend()/spiConfigReceive() work
 *                                 unchanged; writes land in a shadow table
 *                                 until the bias shift-register bit-bang is
 *                                 wired to the sensor)
 *   0xC0 VR_RUN_STOP              wValue!=0 starts event streaming on EP1-IN
 *   0xC1 VR_MODE_SELECT           0=DVS (synthetic), 1=APS (synthetic),
 *                                 2=G-AER capture (chip/emulator)
 *   0xC2 VR_CHIP_CONFIG_MULTIPLE  burst config writes (CypressFX3
 *                                 SPIConfigSequence-compatible)
 *   0xC4 VR_STATISTICS            IN: 32-byte statistics block
 *   0xC5 VR_FLUSH_TIMEOUT         wValue = early flush timeout in ms
 *
 * Event stream: bulk EP1-IN (0x81), 8-byte little-endian events:
 *   word0 = 32-bit microsecond timestamp
 *   word1 = [31:28 type][27 pol][26:20 addr112][19:13 addr126][12:0 seq]
 * type 0x0 = DVS, type 0xF = buffer padding (skipped). The 7-bit field at
 * bit 13 carries the 126-valued address and the 7-bit field at bit 20 the
 * 112-valued one (firmware scidvs_vendor.c is the authoritative map).
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.swing.SwingWorker;

import org.usb4java.Device;
import org.usb4java.LibUsb;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisConfig;
import li.longi.USBTransferThread.USBTransferThread;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * CypressFX3-style hardware interface for the SciDVS-FX10 camera. Reuses the
 * whole jAER FX3 configuration path (spiConfigSend with moduleAddr/paramAddr,
 * exactly the SystemLogic2 numbering the SciDVSConfig bias class uses; the
 * FX10 firmware accepts any module/param into its shadow table), but replaces
 * the event reader with one that understands the FX10 8-byte event format on
 * bulk endpoint 0x81.
 *
 * @author rpgraca + Claude
 */
public class SciDVSFX10HardwareInterface extends CypressFX3Biasgen {

	/** USB product ID of the SciDVS-FX10 device (VID is the Thesycon 0x152A). */
	static public final short PID_FX10 = (short) 0x8420;

	/** FX10 vendor requests beyond the VR_FPGA_CONFIG(_MULTIPLE) inherited from CypressFX3. */
	public static final byte VR_DEVICE_RESET = (byte) 0xB2;
	public static final byte VR_RUN_STOP = (byte) 0xC0;
	public static final byte VR_MODE_SELECT = (byte) 0xC1;
	public static final byte VR_STATISTICS = (byte) 0xC4;
	public static final byte VR_FLUSH_TIMEOUT = (byte) 0xC5;

	/** VR_MODE_SELECT values. */
	public static final int MODE_DVS_SYNTHETIC = 0;
	public static final int MODE_APS_SYNTHETIC = 1;
	public static final int MODE_GAER_CAPTURE = 2;

	/** The FX10 streams events on bulk EP1-IN, not the FX3's EP2-IN (0x82). */
	private final static byte FX10_DATA_ENDPOINT_ADDRESS = (byte) 0x81;

	/** Sensor geometry. The chip has 126 addresses on one axis and 112 on the other. */
	public static final int SIZE_112 = 112;
	public static final int SIZE_126 = 126;

	/** Event type codes (top nibble of word1). */
	private static final int EVT_DVS = 0x0;
	private static final int EVT_PAD = 0xF;

	/**
	 * Capture mode sent to the device on open. Persisted preference;
	 * default is G-AER capture (the real sensor/emulator path). Set to
	 * {@link #MODE_DVS_SYNTHETIC} to view the firmware's synthetic
	 * raster-scan test pattern.
	 */
	private int captureMode = CypressFX3.prefs.getInt("SciDVSFX10.captureMode", SciDVSFX10HardwareInterface.MODE_GAER_CAPTURE);

	protected SciDVSFX10HardwareInterface(final Device device) {
		super(device);
	}

	@Override
	public String getTypeName() {
		return "SciDVSFX10";
	}

	public int getCaptureMode() {
		return captureMode;
	}

	/**
	 * Select the device capture mode (VR_MODE_SELECT). Takes effect
	 * immediately if the device is open, and is persisted for future opens.
	 *
	 * @param mode one of MODE_DVS_SYNTHETIC, MODE_APS_SYNTHETIC, MODE_GAER_CAPTURE
	 */
	public synchronized void setCaptureMode(final int mode) throws HardwareInterfaceException {
		captureMode = mode;
		CypressFX3.prefs.putInt("SciDVSFX10.captureMode", mode);
		if (isOpen()) {
			sendVendorRequest(SciDVSFX10HardwareInterface.VR_MODE_SELECT, (short) mode, (short) 0);
		}
	}

	/** Reads the 32-byte statistics block (VR_STATISTICS) as 8 little-endian uint32 words. */
	public synchronized int[] getStatistics() throws HardwareInterfaceException {
		final ByteBuffer buf = sendVendorRequestIN(SciDVSFX10HardwareInterface.VR_STATISTICS, (short) 0, (short) 0, 32);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		final int[] stats = new int[8];
		for (int i = 0; i < 8; i++) {
			stats[i] = buf.getInt(i * 4);
		}
		return stats;
	}

	@Override
	synchronized public void open() throws HardwareInterfaceException {
		super.open(); // CypressFX3Biasgen.open(): USB open + sends current biasgen configuration.

		// Select the capture mode. Mode 2 also powers the G-AER pads and
		// arms the M0+ capture loop on the device.
		sendVendorRequest(SciDVSFX10HardwareInterface.VR_MODE_SELECT, (short) captureMode, (short) 0);
		CypressFX3.log.info("SciDVS-FX10 opened, capture mode=" + captureMode);
	}

	@Override
	synchronized public void sendConfiguration(final net.sf.jaer.biasgen.Biasgen biasgen) throws HardwareInterfaceException {
		if ((biasgen != null) && (biasgen instanceof DavisConfig)) {
			((DavisConfig) biasgen).sendConfiguration();
		}
	}

	/**
	 * The FX10 firmware starts/stops streaming with VR_RUN_STOP instead of
	 * the FX3's SPI mux/USB module writes (which on FX10 would only land in
	 * the config shadow table).
	 */
	@Override
	protected synchronized void enableINEndpoint() throws HardwareInterfaceException {
		if (deviceHandle == null) {
			CypressFX3.log.warning("SciDVSFX10.enableINEndpoint(): null device handle");
			return;
		}
		sendVendorRequest(SciDVSFX10HardwareInterface.VR_RUN_STOP, (short) 1, (short) 0);
		inEndpointEnabled = true;
	}

	@Override
	protected synchronized void disableINEndpoint() {
		try {
			sendVendorRequest(SciDVSFX10HardwareInterface.VR_RUN_STOP, (short) 0, (short) 0);
		}
		catch (final HardwareInterfaceException e) {
			CypressFX3.log.info("SciDVSFX10.disableINEndpoint(): couldn't send VR_RUN_STOP=0 (device gone?)");
		}
		inEndpointEnabled = false;
	}

	/**
	 * The firmware has no timestamp-reset request yet; the device timestamp
	 * is a free-running microsecond counter. Suppress the FX3 SPI sequence,
	 * which would only dirty the shadow config table.
	 */
	@Override
	synchronized public void resetTimestamps() {
		CypressFX3.log.info("SciDVSFX10.resetTimestamps(): not supported by FX10 firmware v1, ignoring");
	}

	@Override
	public void startAEReader() throws HardwareInterfaceException {
		setAeReader(new Fx10AEReader(this));
		allocateAEBuffers();
		getAeReader().startThread();
		HardwareInterfaceException.clearException();
	}

	/**
	 * Reads 8-byte FX10 events from bulk EP1-IN and translates them to jAER
	 * raw addresses in the DavisChip format expected by the SciDVS chip
	 * class extractor.
	 */
	public class Fx10AEReader extends CypressFX3.AEReader {

		/** Carry-over storage for an event split across two USB transfers. */
		private final byte[] pendingEvent = new byte[8];
		private int pendingCount = 0;

		private int badAddressCount = 0;

		public Fx10AEReader(final CypressFX3 cypress) throws HardwareInterfaceException {
			super(cypress);
			// No firmware/logic revision check and no SPI SYSINFO reads here:
			// the FX10 firmware has no FPGA behind it and geometry is fixed.
		}

		/**
		 * Same as the base AEReader.startThread(), but reading from the FX10
		 * data endpoint 0x81 instead of the hardcoded FX3 0x82.
		 */
		@Override
		public void startThread() {
			if (!isOpen()) {
				try {
					open();
				}
				catch (final HardwareInterfaceException e) {
					CypressFX3.log.warning(e.toString());
				}
			}

			CypressFX3.log.info("Starting Fx10AEReader on EP 0x81");
			usbTransfer = new USBTransferThread(deviceHandle, SciDVSFX10HardwareInterface.FX10_DATA_ENDPOINT_ADDRESS,
				LibUsb.TRANSFER_TYPE_BULK, new ProcessAEData(), getNumBuffers(), getFifoSize(), null, null, new Runnable() {
					@Override
					public void run() {
						final SwingWorker<Void, Void> shutdownWorker = new SwingWorker<Void, Void>() {
							@Override
							public Void doInBackground() {
								close();

								return (null);
							}
						};
						shutdownWorker.execute();
					}
				});
			usbTransfer.setName("SciDVSFX10AEReaderThread");
			usbTransfer.start();

			getSupport().firePropertyChange("readerStarted", false, true);
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

				buffer.lastCaptureIndex = eventCounter;

				b.order(ByteOrder.LITTLE_ENDIAN);

				while (b.remaining() > 0) {
					if ((pendingCount != 0) || (b.remaining() < 8)) {
						// Slow path: complete a split event byte by byte.
						while ((pendingCount < 8) && (b.remaining() > 0)) {
							pendingEvent[pendingCount++] = b.get();
						}
						if (pendingCount < 8) {
							break; // Still incomplete, wait for next transfer.
						}
						pendingCount = 0;
						final int tsUs = ((pendingEvent[0] & 0xFF)) | ((pendingEvent[1] & 0xFF) << 8) | ((pendingEvent[2] & 0xFF) << 16)
							| ((pendingEvent[3] & 0xFF) << 24);
						final int word1 = ((pendingEvent[4] & 0xFF)) | ((pendingEvent[5] & 0xFF) << 8) | ((pendingEvent[6] & 0xFF) << 16)
							| ((pendingEvent[7] & 0xFF) << 24);
						processEvent(buffer, tsUs, word1);
					}
					else {
						processEvent(buffer, b.getInt(), b.getInt());
					}
				}

				buffer.setNumEvents(eventCounter);
				buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
			} // sync on aePacketRawPool
		}

		/**
		 * Translate one 8-byte event. word1 layout (firmware scidvs_vendor.c):
		 * [31:28]=type, [27]=polarity, [26:20]=112-valued address,
		 * [19:13]=126-valued address, [12:0]=sequence/sub-us. The SciDVS chip
		 * class is 112 wide (sizeX) by 126 tall (sizeY), so the 112-valued
		 * field maps to jAER x and the 126-valued field to jAER y. The X
		 * address is stored flipped because the DavisBaseCamera extractor
		 * computes x = sizeX-1-rawX.
		 */
		private void processEvent(final AEPacketRaw buffer, final int tsUs, final int word1) {
			final int type = (word1 >>> 28) & 0x0F;

			if (type != SciDVSFX10HardwareInterface.EVT_DVS) {
				// 0xF = DMA buffer padding (expected, silently skipped).
				// APS/IMU/timestamp event types are not handled in v1.
				if (type != SciDVSFX10HardwareInterface.EVT_PAD) {
					CypressFX3.log.fine("SciDVSFX10: skipping unhandled event type 0x" + Integer.toHexString(type));
				}
				return;
			}

			final int pol = (word1 >>> 27) & 0x01;
			final int addr112 = (word1 >>> 20) & 0x7F; // jAER x, 0..111
			final int addr126 = (word1 >>> 13) & 0x7F; // jAER y, 0..125

			if ((addr112 >= SciDVSFX10HardwareInterface.SIZE_112) || (addr126 >= SciDVSFX10HardwareInterface.SIZE_126)) {
				if ((badAddressCount++ % 1000) == 0) {
					CypressFX3.log.severe("SciDVSFX10: event address out of range: x=" + addr112 + " y=" + addr126 + " (count="
						+ badAddressCount + ")");
				}
				return;
			}

			if (!ensureCapacity(buffer, eventCounter + 1)) {
				return; // Buffer overrun.
			}

			buffer.getAddresses()[eventCounter] = ((((SciDVSFX10HardwareInterface.SIZE_112 - 1) - addr112) << DavisChip.XSHIFT)
				& DavisChip.XMASK) | ((addr126 << DavisChip.YSHIFT) & DavisChip.YMASK)
				| ((pol << DavisChip.POLSHIFT) & DavisChip.POLMASK);
			buffer.getTimestamps()[eventCounter++] = tsUs; // Device timestamps are already in microseconds.
		}
	}
}
