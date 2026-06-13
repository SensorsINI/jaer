/*
 * SciDVSFX10HardwareInterface.java
 *
 * Hardware interface for the SciDVS camera on the Infineon EZ-USB FX10
 * (CYUSB4014) USB controller, VID 0x152A PID 0x841E.
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
 *   word1 (DVS, type 0x0) = [31:28 type][27 pol][26:20 addr112][19:13 addr126][12:0 seq]
 *   word1 (APS, type 0x1) = [31:28 type][27 SOF][26:20 addr112][19:13 addr126][9:0 ADC sample]
 * type 0x0 = DVS, type 0x1 = APS sample, type 0x2 = external-input edge
 * (word1 = [27]=polarity, [12:0]=shared seq snapshot), type 0xF = padding.
 * The 7-bit field at bit 13 carries the 126-valued address and the 7-bit
 * field at bit 20 the 112-valued one (firmware scidvs_vendor.c is the
 * authoritative map). APS frames are scanned row-major with the 112-valued
 * address in the outer loop and the 126-valued address in the inner loop,
 * so the first wire sample of each frame is (0,0) and the last is
 * (111,125) in jAER coordinates; bit 27 (SOF) is set on the first sample
 * of each frame.
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.swing.SwingWorker;

import org.usb4java.Device;
import org.usb4java.LibUsb;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisConfig;
import net.sf.jaer.event.ApsDvsEvent;
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
	static public final short PID_FX10 = (short) 0x841E;

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
	/**
	 * Mode 3 = SIP wide-link "fast" capture. EP1-IN carries RAW 32-bit
	 * little-endian words (one per interface clock, NO 8-byte framing); the
	 * clock divider (480 MHz / div) is passed in VR_MODE_SELECT wIndex. Decoded
	 * natively here (no Python daemon) by {@link #translateSipWordToRawEvents}
	 * and decimated to a renderable rate in the reader thread. SuperSpeed only.
	 */
	public static final int MODE_SIP_CAPTURE = 3;

	/** The FX10 streams events on bulk EP1-IN, not the FX3's EP2-IN (0x82). */
	private final static byte FX10_DATA_ENDPOINT_ADDRESS = (byte) 0x81;

	/** Sensor geometry. The chip has 126 addresses on one axis and 112 on the other. */
	public static final int SIZE_112 = 112;
	public static final int SIZE_126 = 126;

	/** Event type codes (top nibble of word1). */
	static final int EVT_DVS = 0x0;
	static final int EVT_APS = 0x1;
	/**
	 * External-input edge (firmware 2026-06-12): emitted by the FX10 when the
	 * ExtInput lane changes level, gated on the jAER ExtInput.RunDetector /
	 * DetectRisingEdges / DetectFallingEdges config (module 4 params 0..2).
	 * word1 = [31:28]=0x2 [27]=edge polarity (1 rising) [26:13]=0
	 * [12:0]=current shared DVS sequence counter value (not consumed).
	 */
	static final int EVT_EXT = 0x2;
	static final int EVT_PAD = 0xF;

	/** Mask for the 10-bit ADC sample in an APS word1. */
	static final int APS_SAMPLE_MASK = 0x3FF;

	// ------------------------------------------------------------------
	// Static, hardware-free decode core. Used both by the AE reader below
	// and by the offline harness SciDVSFX10DecoderSelfTest, so the decode
	// can be sanity-checked without USB hardware.
	// ------------------------------------------------------------------

	/** Returns the event type code (top nibble) of an FX10 word1. */
	static int eventType(final int word1) {
		return (word1 >>> 28) & 0x0F;
	}

	/** Returns the 112-valued address field word1[26:20], which maps to jAER x (chip sizeX=112). */
	static int addr112(final int word1) {
		return (word1 >>> 20) & 0x7F;
	}

	/** Returns the 126-valued address field word1[19:13], which maps to jAER y (chip sizeY=126). */
	static int addr126(final int word1) {
		return (word1 >>> 13) & 0x7F;
	}

	/** Returns true if the APS start-of-frame flag (word1 bit 27) is set. */
	static boolean isApsStartOfFrame(final int word1) {
		return ((word1 >>> 27) & 0x01) != 0;
	}

	/** Throttle counter for bad-address warnings (shared; there is only one device). */
	private static int badAddressCount = 0;

	private static boolean checkAddressInRange(final int word1) {
		final int a112 = SciDVSFX10HardwareInterface.addr112(word1);
		final int a126 = SciDVSFX10HardwareInterface.addr126(word1);
		if ((a112 >= SciDVSFX10HardwareInterface.SIZE_112) || (a126 >= SciDVSFX10HardwareInterface.SIZE_126)) {
			if ((SciDVSFX10HardwareInterface.badAddressCount++ % 1000) == 0) {
				CypressFX3.log.severe("SciDVSFX10: event address out of range: x=" + a112 + " y=" + a126 + " (count="
					+ SciDVSFX10HardwareInterface.badAddressCount + ")");
			}
			return false;
		}
		return true;
	}

	/** Throttle counter for SOF-flag/address mismatch warnings. */
	private static int sofMismatchCount = 0;

	/**
	 * Translates one FX10 wire event (tsUs, word1) into 0..2 jAER raw events
	 * in the DavisChip address format, appended to the given arrays starting
	 * at index count. The arrays must have room for at least count+2 entries.
	 *
	 * <ul>
	 * <li>DVS (type 0x0): one raw DVS address. The X field is stored flipped
	 * because the DavisBaseCamera extractor computes x = sizeX-1-rawX for DVS
	 * events (it does NOT flip APS events).</li>
	 * <li>APS (type 0x1): two raw ADC samples, a ResetRead carrying the
	 * 10-bit sample followed by a SignalRead carrying 0, so the DAVIS frame
	 * renderer's digital CDS (reset - signal) reproduces the sample and
	 * brighter pixels get larger values in the 0..1023 = 0..MAX_ADC range.
	 * The extractor synthesizes SOF from the (0,0) ResetRead and EOF from
	 * the (111,125) SignalRead (see SciDVSFX10's apsFirstPixelReadOut /
	 * apsLastPixelReadOut), so frames display exactly like on stock DAVIS
	 * cameras and coexist with DVS events.</li>
	 * <li>padding (type 0xF) and unknown types: nothing appended.</li>
	 * </ul>
	 *
	 * @return the new event count
	 */
	static int translateWordToRawEvents(final int tsUs, final int word1, final int[] addresses, final int[] timestamps,
		final int count) {
		final int type = SciDVSFX10HardwareInterface.eventType(word1);

		switch (type) {
			case EVT_DVS: {
				if (!SciDVSFX10HardwareInterface.checkAddressInRange(word1)) {
					return count;
				}
				final int pol = (word1 >>> 27) & 0x01;
				addresses[count] = ((((SciDVSFX10HardwareInterface.SIZE_112 - 1) - SciDVSFX10HardwareInterface.addr112(word1)) << DavisChip.XSHIFT)
					& DavisChip.XMASK) | ((SciDVSFX10HardwareInterface.addr126(word1) << DavisChip.YSHIFT) & DavisChip.YMASK)
					| ((pol << DavisChip.POLSHIFT) & DavisChip.POLMASK);
				timestamps[count] = tsUs;
				return count + 1;
			}

			case EVT_APS: {
				if (!SciDVSFX10HardwareInterface.checkAddressInRange(word1)) {
					return count;
				}
				final boolean firstPixel = (SciDVSFX10HardwareInterface.addr112(word1) == 0)
					&& (SciDVSFX10HardwareInterface.addr126(word1) == 0);
				if (SciDVSFX10HardwareInterface.isApsStartOfFrame(word1) != firstPixel) {
					// SOF flag should be set exactly on the (0,0) sample; flag/address mismatch
					// indicates dropped samples or a firmware/format inconsistency.
					if ((SciDVSFX10HardwareInterface.sofMismatchCount++ % 1000) == 0) {
						CypressFX3.log.warning("SciDVSFX10: APS SOF flag/address mismatch: SOF="
							+ SciDVSFX10HardwareInterface.isApsStartOfFrame(word1) + " at x="
							+ SciDVSFX10HardwareInterface.addr112(word1) + " y=" + SciDVSFX10HardwareInterface.addr126(word1)
							+ " (count=" + SciDVSFX10HardwareInterface.sofMismatchCount + ")");
					}
				}

				final int baseAddress = DavisChip.ADDRESS_TYPE_APS
					| ((SciDVSFX10HardwareInterface.addr112(word1) << DavisChip.XSHIFT) & DavisChip.XMASK)
					| ((SciDVSFX10HardwareInterface.addr126(word1) << DavisChip.YSHIFT) & DavisChip.YMASK);

				// ResetRead carries the sample, SignalRead carries 0: renderer shows (reset - signal) = sample.
				addresses[count] = baseAddress | (ApsDvsEvent.ReadoutType.ResetRead.code << DavisChip.ADC_READCYCLE_SHIFT)
					| (word1 & SciDVSFX10HardwareInterface.APS_SAMPLE_MASK);
				timestamps[count] = tsUs;
				addresses[count + 1] = baseAddress | (ApsDvsEvent.ReadoutType.SignalRead.code << DavisChip.ADC_READCYCLE_SHIFT);
				timestamps[count + 1] = tsUs;
				return count + 2;
			}

			case EVT_EXT: {
				// External-input edge -> the DAVIS special-event address the
				// DavisBaseCamera extractor turns into a special ApsDvsEvent
				// (same encoding as DAViSFX3HardwareInterface: base address
				// EXTERNAL_INPUT_EVENT_ADDR plus 3 = rising, 2 = falling).
				final int rising = (word1 >>> 27) & 0x01;
				addresses[count] = DavisChip.EXTERNAL_INPUT_EVENT_ADDR + ((rising != 0) ? 3 : 2);
				timestamps[count] = tsUs;
				return count + 1;
			}

			case EVT_PAD:
				// DMA buffer padding (expected, silently skipped).
				return count;

			default:
				CypressFX3.log.fine("SciDVSFX10: skipping unhandled event type 0x" + Integer.toHexString(type));
				return count;
		}
	}

	// ------------------------------------------------------------------
	// Mode-3 (SIP) raw-word decode core. Hardware-free + static so the
	// self-test can pin it against the Python reference (host/sip400_parse.py).
	//
	// Raw 32-bit little-endian word layout (rig reroute applied):
	//   [3:0]   eventOFF nibble (lane 0..3)
	//   [7:4]   eventON  nibble (lane 0..3)
	//   [8]     groupAddr[0]      [9] DEAD (ignored)   [12:10] groupAddr[4:2]
	//   [13]    Valid             [14] SYNC_INJECT      [15] ExtInputActive
	//   [22:16] addrY[6:0]        [30:23] ChipADCData (APS, ignored for now)
	//   [31]    groupAddr[1] (rerouted; the C18/P0DQ9 path is dead)
	// group = b8 | b31<<1 | b[12:10]<<2 (0..31); x126 = group*4 + lane; y112 = addrY.
	// ------------------------------------------------------------------

	/** True if the SIP word carries a valid event (bit 13). Most words are not. */
	static boolean sipValid(final int w) {
		return (w & 0x2000) != 0;
	}

	/** Reassembles the 5-bit group address from its scattered/rerouted bits. */
	static int sipGroup(final int w) {
		return ((w >>> 8) & 0x1) | (((w >>> 31) & 0x1) << 1) | (((w >>> 10) & 0x7) << 2);
	}

	/** The 7-bit addrY (jAER x source) from a SIP word. */
	static int sipAddrY(final int w) {
		return (w >>> 16) & 0x7F;
	}

	/**
	 * Decodes one raw mode-3 SIP word into 0..8 jAER raw DVS events (4 lanes x
	 * {ON,OFF}). Bit-exact with host/sip400_parse.py + dvs_addresses: ON nibble
	 * bit set => polarity 1, OFF => 0; x126 = group*4+lane; y112 = addrY. The
	 * actual address math is delegated to {@link #translateWordToRawEvents} by
	 * synthesizing a type-0 DVS word (addr112<-y112, addr126<-x126), so the SIP
	 * path and the 8-byte path share ONE encoder and ONE range check (the check
	 * drops group-31 lanes 2/3 whose x126 >= 126). Caller is responsible for the
	 * Valid check + decimation; this returns count unchanged for an invalid word.
	 *
	 * @return the new event count
	 */
	static int translateSipWordToRawEvents(final int tsUs, final int w, final int[] addresses, final int[] timestamps,
		final int count) {
		if (!SciDVSFX10HardwareInterface.sipValid(w)) {
			return count;
		}
		final int group = SciDVSFX10HardwareInterface.sipGroup(w);
		final int y112 = SciDVSFX10HardwareInterface.sipAddrY(w);
		final int on = (w >>> 4) & 0xF;
		final int off = w & 0xF;
		if (y112 >= SciDVSFX10HardwareInterface.SIZE_112) {
			return count; // off-array row (shouldn't happen for real data)
		}
		int c = count;
		for (int lane = 0; lane < 4; lane++) {
			final int x126 = (group << 2) | lane;
			// group 31 lanes 2/3 give x126 = 126/127, beyond the 126-wide array.
			// The integrity-counter test content sweeps all 32 groups so it hits
			// these every cycle; skip them silently here instead of flooding the
			// shared checkAddressInRange SEVERE log (real sensor data never does).
			if (x126 >= SciDVSFX10HardwareInterface.SIZE_126) {
				continue;
			}
			if (((on >>> lane) & 0x1) != 0) {
				c = SciDVSFX10HardwareInterface.emitSipDvs(tsUs, x126, y112, 1, addresses, timestamps, c);
			}
			if (((off >>> lane) & 0x1) != 0) {
				c = SciDVSFX10HardwareInterface.emitSipDvs(tsUs, x126, y112, 0, addresses, timestamps, c);
			}
		}
		return c;
	}

	/** Emits one DVS event by synthesizing a type-0 word and reusing the shared encoder. */
	private static int emitSipDvs(final int tsUs, final int x126, final int y112, final int pol, final int[] addresses,
		final int[] timestamps, final int count) {
		final int word1 = (EVT_DVS << 28) | ((pol & 0x1) << 27) | ((y112 & 0x7F) << 20) | ((x126 & 0x7F) << 13);
		return SciDVSFX10HardwareInterface.translateWordToRawEvents(tsUs, word1, addresses, timestamps, count);
	}

	/**
	 * Capture mode sent to the device on open. Persisted preference;
	 * default is G-AER capture (the real sensor/emulator path). Set to
	 * {@link #MODE_DVS_SYNTHETIC} to view the firmware's synthetic
	 * raster-scan test pattern.
	 */
	private int captureMode = CypressFX3.prefs.getInt("SciDVSFX10.captureMode", SciDVSFX10HardwareInterface.MODE_GAER_CAPTURE);

	/** Mode-3 clock divider: interface clock = 480 MHz / divider. 24 = 20 MHz (proven-clean), 8 = 60 MHz spec. */
	private int sipClockDivider = CypressFX3.prefs.getInt("SciDVSFX10.sipClockDivider", 24);

	/** Mode-3 jAER feed is decimated to roughly this many events/s (the raw stream is far too fast to render). */
	private float sipTargetMeps = CypressFX3.prefs.getFloat("SciDVSFX10.sipTargetMeps", 2.0f);

	/** Fast-source content: 0 = dense integrity counter (max rate, all groups), 1 = animated pattern (ball/bars/sweep). */
	private int sipContent = CypressFX3.prefs.getInt("SciDVSFX10.sipContent", 0);

	/** Pattern select when sipContent=1: 0 = raster, 1 = bouncing ball, 2 = moving bars, 3 = rotating sweep. */
	private int sipPattern = CypressFX3.prefs.getInt("SciDVSFX10.sipPattern", 1);

	/** Animation speed (FPGA bias addr 8 fine, 0..255): 128 ~ 24 steps/s, 255 fastest. */
	private int sipSpeed = CypressFX3.prefs.getInt("SciDVSFX10.sipSpeed", 128);

	/** Event density for animated patterns (FPGA bias addr 10 fine, 0..255): 255 = all, lower thins via LFSR. */
	private int sipDensity = CypressFX3.prefs.getInt("SciDVSFX10.sipDensity", 255);

	/** Reconstruct APS frames from the in-band ADC field (bits 30:23) in mode 3, emitted as DAVIS APS events. */
	private boolean sipApsEnable = CypressFX3.prefs.getBoolean("SciDVSFX10.sipApsEnable", true);

	public boolean isSipApsEnable() { return sipApsEnable; }

	/** Enable/disable in-band APS frame reconstruction for the mode-3 stream. */
	public synchronized void setSipApsEnable(final boolean en) {
		sipApsEnable = en;
		CypressFX3.prefs.putBoolean("SciDVSFX10.sipApsEnable", en);
	}

	protected SciDVSFX10HardwareInterface(final Device device) {
		super(device);
	}

	public int getSipContent() { return sipContent; }

	/** 0 = dense counter (max rate), 1 = animated pattern. Applied at next mode-3 open. */
	public synchronized void setSipContent(final int c) {
		sipContent = (c != 0) ? 1 : 0;
		CypressFX3.prefs.putInt("SciDVSFX10.sipContent", sipContent);
	}

	public int getSipPattern() { return sipPattern; }

	/** 0=raster,1=ball,2=bars,3=sweep (used when content=animated). Applied at next mode-3 open. */
	public synchronized void setSipPattern(final int p) {
		sipPattern = Math.max(0, Math.min(3, p));
		CypressFX3.prefs.putInt("SciDVSFX10.sipPattern", sipPattern);
	}

	public int getSipSpeed() { return sipSpeed; }

	public synchronized void setSipSpeed(final int s) {
		sipSpeed = Math.max(0, Math.min(255, s));
		CypressFX3.prefs.putInt("SciDVSFX10.sipSpeed", sipSpeed);
	}

	public int getSipDensity() { return sipDensity; }

	public synchronized void setSipDensity(final int d) {
		sipDensity = Math.max(0, Math.min(255, d));
		CypressFX3.prefs.putInt("SciDVSFX10.sipDensity", sipDensity);
	}

	public int getSipClockDivider() {
		return sipClockDivider;
	}

	/** Sets the mode-3 clock divider (480 MHz / div). Clamped to the firmware-usable 5..24 range. */
	public synchronized void setSipClockDivider(final int div) {
		sipClockDivider = Math.max(5, Math.min(24, div));
		CypressFX3.prefs.putInt("SciDVSFX10.sipClockDivider", sipClockDivider);
	}

	public float getSipTargetMeps() {
		return sipTargetMeps;
	}

	/** Sets the decimation target (events/s) for the mode-3 jAER feed. */
	public synchronized void setSipTargetMeps(final float meps) {
		sipTargetMeps = Math.max(0.01f, meps);
		CypressFX3.prefs.putFloat("SciDVSFX10.sipTargetMeps", sipTargetMeps);
	}

	/** Interface clock in Hz for the current divider (mode-3 timestamp base). */
	public double getSipLinkHz() {
		return 480.0e6 / sipClockDivider;
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
		final int prevMode = captureMode;
		captureMode = mode;
		CypressFX3.prefs.putInt("SciDVSFX10.captureMode", mode);
		if (isOpen()) {
			// Mode-3 hygiene: a mode-3 session must be torn down with a device
			// reset + settle before the next mode-3/mode-2 entry, or the link
			// wedges (confirmed on the rig). When leaving mode 3, reset and warn
			// that a reconnect is required; the live VR_MODE_SELECT below only
			// covers the clean 0/1/2 transitions.
			if (prevMode == SciDVSFX10HardwareInterface.MODE_SIP_CAPTURE) {
				sendVendorRequest(SciDVSFX10HardwareInterface.VR_RUN_STOP, (short) 0, (short) 0);
				sendVendorRequest(SciDVSFX10HardwareInterface.VR_DEVICE_RESET, (short) 0, (short) 0);
				CypressFX3.log.warning("SciDVSFX10: left mode 3 (SIP); device reset issued, reconnect to continue");
				return;
			}
			final short modeIndex = (mode == SciDVSFX10HardwareInterface.MODE_SIP_CAPTURE) ? (short) sipClockDivider
				: (short) 0;
			sendVendorRequest(SciDVSFX10HardwareInterface.VR_MODE_SELECT, (short) mode, modeIndex);
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

		// For mode 3, configure the FPGA fast source BEFORE entering mode 3:
		// bias/chip-config bit-bang only reaches the emulator while the SIP pads
		// are firmware-controlled (i.e. NOT in mode 3). DigitalMux1=0 selects the
		// dense integrity-counter content (overlay, not interleave) so the source
		// free-runs every interface clock (~80 Meps @ 20 MHz); without this the
		// source stays near-idle (~300 valid words/s) and jAER shows ~2 keps.
		// Valid-duty (addr 12) -> full. Values are big-endian on the wire
		// (spiConfigSend convention). The FPGA retains these into mode 3.
		if (captureMode == SciDVSFX10HardwareInterface.MODE_SIP_CAPTURE) {
			// The FPGA pattern engine reads its "fine" controls from wire[11:4],
			// so a fine value F is sent as host (F << 4).
			final int dmux1 = (sipContent != 0) ? 0x2 : 0x0; // bit1=chain45 content: 0=counter, 1=pattern (overlay)
			spiConfigSend((short) 5, (short) 129, dmux1);          // DigitalMux1: content + overlay
			spiConfigSend((short) 5, (short) 128, sipPattern);     // DigitalMux0: pattern select (raster/ball/bars/sweep)
			spiConfigSend((short) 5, (short) 8, (sipSpeed & 0xFF) << 4);   // animation speed (addr 8 fine)
			spiConfigSend((short) 5, (short) 10, (sipDensity & 0xFF) << 4); // event density (addr 10 fine)
			spiConfigSend((short) 5, (short) 12, 0x0FF0);          // Valid-duty full; rate to jAER set by sipTargetMeps decimation
			CypressFX3.log.info("SciDVSFX10: fast source content=" + (sipContent != 0 ? ("pattern#" + sipPattern) : "counter")
				+ " speed=" + sipSpeed + " density=" + sipDensity);
		}

		// Select the capture mode. Mode 2 also powers the G-AER pads and arms
		// the M0+ capture loop; mode 3 (SIP) carries the clock divider in wIndex.
		final short modeIndex = (captureMode == SciDVSFX10HardwareInterface.MODE_SIP_CAPTURE) ? (short) sipClockDivider
			: (short) 0;
		sendVendorRequest(SciDVSFX10HardwareInterface.VR_MODE_SELECT, (short) captureMode, modeIndex);
		CypressFX3.log.info("SciDVS-FX10 opened, capture mode=" + captureMode
			+ (captureMode == SciDVSFX10HardwareInterface.MODE_SIP_CAPTURE
				? (" (SIP, divider=" + sipClockDivider + " = " + (getSipLinkHz() / 1e6) + " MHz)") : ""));
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

		// --- mode-3 (SIP) reader state ---
		/** Carry-over for a 32-bit raw word split across two USB transfers (0..3 bytes). */
		private final byte[] sipCarry = new byte[4];
		private int sipCarryCount = 0;
		/** Free-running raw word index (every word, valid or not) for the timestamp base. */
		private long sipWordIndex = 0;
		/** Fractional-accumulator decimator state (uniform, non-aliasing — unlike a fixed stride). */
		private double sipDecAcc = 0.0;
		private double sipKeepRatio = 1.0;
		private long sipLastNanos = 0;
		private long sipKeptEventsThisPacket = 0;

		// --- in-band APS frame reconstruction (mirrors host ApsReconstructor) ---
		/** The ADC field (bits 30:23) is quasi-static, changing only at ScanClock
		 * edges (hundreds-thousands of identical words apart). A "scan window" is a
		 * constant-ADC run >= APS_MIN_RUN words; each window gray-decodes to one row
		 * of the column-0 scan. 112 windows fill a column = one frame. The rig's
		 * column-SR is dead, so the column is tiled across all X for display. */
		private static final int APS_MIN_RUN = 8;
		private final int[] apsCol = new int[SIZE_112];
		private int apsPtr = 0;
		private int apsLastAdc = -1;
		private int apsRun = 0;
		/**
		 * Adaptive per-row scan dwell, in link words. One scan row holds a
		 * constant ADC value for a fixed number of words (~61 at the 20 MHz
		 * bring-up clock; scales with the link clock). Measured from single-row
		 * runs so it survives clock-divider changes. Used to (a) split a single
		 * constant run that spans several equal-value rows (flat gradient
		 * regions) into the right row count, and (b) size the inter-frame idle
		 * threshold. Without this the reconstruction mis-segments and frames
		 * fragment/tear (see host/debug_full.py + analyze).
		 */
		private int apsDwell = 61;
		/** Cap APS frames pushed to jAER (the scan can complete ~700x/s; jAER renders ~30-60). */
		private long apsLastEmitWord = 0;
		/** Previous bit-15 (sync/ExtInput) level, to emit one special event per rising edge. */
		private int sipLastSync = 0;

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

			// Mode 3 is a raw firehose; keep MORE buffers queued (across GC
			// pauses) but keep the per-URB transfer size at the proven default --
			// a single bulk URB larger than the kernel's usbfs per-URB limit
			// (~256 KB on SuperSpeed) fails submit with LIBUSB_ERROR_IO.
			// Modes 0..2 keep the configured sizing.
			final boolean sip = (captureMode == SciDVSFX10HardwareInterface.MODE_SIP_CAPTURE);
			final int nbuf = sip ? Math.max(getNumBuffers(), 32) : getNumBuffers();
			final int fifo = sip ? Math.min(Math.max(getFifoSize(), 1 << 16), 1 << 18) : getFifoSize();

			CypressFX3.log.info("Starting Fx10AEReader on EP 0x81" + (sip ? " (SIP mode, " + nbuf + "x" + fifo + " buffers)" : ""));
			usbTransfer = new USBTransferThread(deviceHandle, SciDVSFX10HardwareInterface.FX10_DATA_ENDPOINT_ADDRESS,
				LibUsb.TRANSFER_TYPE_BULK, new ProcessAEData(), nbuf, fifo, null, null, new Runnable() {
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
			if (captureMode == SciDVSFX10HardwareInterface.MODE_SIP_CAPTURE) {
				translateSipWords(b);
				return;
			}
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
		 * Translate one 8-byte event into jAER raw events via the static,
		 * hardware-free {@link SciDVSFX10HardwareInterface#translateWordToRawEvents}
		 * core (DVS = 1 raw event, APS sample = ResetRead + SignalRead pair,
		 * padding = nothing). Device timestamps are already in microseconds.
		 */
		private void processEvent(final AEPacketRaw buffer, final int tsUs, final int word1) {
			if (!ensureCapacity(buffer, eventCounter + 2)) {
				return; // Buffer overrun.
			}

			eventCounter = SciDVSFX10HardwareInterface.translateWordToRawEvents(tsUs, word1, buffer.getAddresses(),
				buffer.getTimestamps(), eventCounter);
		}

		/**
		 * Mode-3 (SIP) hot path: decode raw 32-bit little-endian words into jAER
		 * DVS events, uniformly decimated to ~{@code sipTargetMeps} events/s.
		 *
		 * Decode is delegated to the static {@link SciDVSFX10HardwareInterface#translateSipWordToRawEvents}
		 * (shared encoder + range check). Timestamps are derived from the
		 * free-running raw word index / interface clock. The decimator is a
		 * fractional accumulator over VALID words (keep whole words, preserving
		 * per-word ON/OFF balance); its keep-ratio is a slow closed loop on the
		 * measured output rate, so it adapts to scene density and -- unlike the
		 * Python fixed-stride vidx[::dec] -- never phase-locks onto the period-32
		 * group counter (no group/column aliasing). APS (ADC field) is not
		 * reconstructed here yet; DVS-only first.
		 */
		private void translateSipWords(final ByteBuffer b) {
			synchronized (aePacketRawPool) {
				final AEPacketRaw buffer = aePacketRawPool.writeBuffer();
				buffer.lastCaptureIndex = eventCounter;

				b.order(ByteOrder.LITTLE_ENDIAN);
				final double usPerWord = 1.0e6 / SciDVSFX10HardwareInterface.this.getSipLinkHz();
				sipKeptEventsThisPacket = 0;

				// Complete a word split across the previous transfer boundary.
				while ((sipCarryCount != 0) && (sipCarryCount < 4) && (b.remaining() > 0)) {
					sipCarry[sipCarryCount++] = b.get();
				}
				if (sipCarryCount == 4) {
					final int w = (sipCarry[0] & 0xFF) | ((sipCarry[1] & 0xFF) << 8) | ((sipCarry[2] & 0xFF) << 16)
						| ((sipCarry[3] & 0xFF) << 24);
					sipCarryCount = 0;
					decodeSipWord(buffer, w, usPerWord);
				}

				while (b.remaining() >= 4) {
					decodeSipWord(buffer, b.getInt(), usPerWord);
				}

				// Stash a partial trailing word for the next transfer.
				while (b.remaining() > 0) {
					sipCarry[sipCarryCount++] = b.get();
				}

				buffer.setNumEvents(eventCounter);
				buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;

				// Slow closed-loop on the keep-ratio to hold ~sipTargetMeps out.
				final long now = System.nanoTime();
				if (sipLastNanos != 0) {
					final double dt = (now - sipLastNanos) / 1.0e9;
					if (dt > 1.0e-4) {
						final double eps = sipKeptEventsThisPacket / dt;
						final double target = SciDVSFX10HardwareInterface.this.getSipTargetMeps() * 1.0e6;
						if (eps > (target * 1.1)) {
							sipKeepRatio = Math.max(1.0e-4, sipKeepRatio * Math.max(0.5, target / eps));
						}
						else if ((eps < (target * 0.5)) && (sipKeepRatio < 1.0)) {
							sipKeepRatio = Math.min(1.0, sipKeepRatio * 1.5);
						}
					}
				}
				sipLastNanos = now;
			} // sync on aePacketRawPool
		}

		/** Advance the word index, apply the Valid filter + decimator, and decode one kept word. */
		private void decodeSipWord(final AEPacketRaw buffer, final int w, final double usPerWord) {
			final int tsUs = (int) (sipWordIndex * usPerWord);
			sipWordIndex++;
			// APS rides in-band on EVERY captured word (bits 30:23), independent of
			// the DVS Valid bit, so feed it before the DVS Valid/decimation gate.
			if (sipApsEnable) {
				feedAps(buffer, w, tsUs);
			}
			// bit-15 = the FPGA-injected sync marker (ball wall-bounce). Emit one
			// DAVIS external-input special event on each rising edge so it shows in
			// jAER and carries the capture timestamp (rides on every word).
			final int sync = (w >>> 15) & 0x1;
			if ((sync != 0) && (sipLastSync == 0) && ensureCapacity(buffer, eventCounter + 1)) {
				eventCounter = SciDVSFX10HardwareInterface.translateWordToRawEvents(tsUs,
					(EVT_EXT << 28) | (1 << 27), buffer.getAddresses(), buffer.getTimestamps(), eventCounter);
			}
			sipLastSync = sync;
			if (!SciDVSFX10HardwareInterface.sipValid(w)) {
				return; // Valid==0 idle word: the overwhelming majority. Cheapest skip.
			}
			sipDecAcc += sipKeepRatio;
			if (sipDecAcc < 1.0) {
				return; // decimated out
			}
			sipDecAcc -= 1.0;
			if (!ensureCapacity(buffer, eventCounter + 8)) {
				return; // buffer full
			}
			final int before = eventCounter;
			eventCounter = SciDVSFX10HardwareInterface.translateSipWordToRawEvents(tsUs, w, buffer.getAddresses(),
				buffer.getTimestamps(), eventCounter);
			sipKeptEventsThisPacket += (eventCounter - before);
		}

		/** 8-bit gray->binary, inverted (DAVIS/ECP3 convention; mirrors host _gray_decode). */
		private static int grayDecode8(final int adc) {
			int g = adc & 0xFF;
			g ^= g >>> 4;
			g ^= g >>> 2;
			g ^= g >>> 1;
			return (~g) & 0xFF;
		}

		/**
		 * Feed one word's in-band ADC field (bits 30:23) to the streaming APS
		 * reconstructor. Detects scan windows (constant-ADC runs >= APS_MIN_RUN),
		 * gray-decodes each into one row of the 112-entry column scan, and emits a
		 * full APS frame when a column completes (mirrors host ApsReconstructor).
		 */
		private void feedAps(final AEPacketRaw buffer, final int w, final int tsUs) {
			final int adc = (w >>> 23) & 0xFF;
			if (adc == apsLastAdc) {
				apsRun++;
				return;
			}
			// A constant run of apsRun words at value apsLastAdc just ended.
			if (apsLastAdc >= 0) {
				// Inter-frame idle is far longer than a whole column scan
				// (112 rows * dwell). A constant run beyond that is the gap
				// between frames, not scan data; anything shorter (down to a
				// single dwell) is scan rows. A run can legitimately span
				// several rows that share an ADC value (flat gradient regions),
				// so expand it by the measured dwell instead of counting it as
				// one row -- that is what kept frames from completing and tore
				// them (validated against host/debug_full.py captures).
				final int idleMin = Math.max(2000, (SciDVSFX10HardwareInterface.SIZE_112 * apsDwell * 5) / 4);
				if (apsRun >= idleMin) {
					// Column scan finished: emit it (paced ~30 fps) and realign
					// the next frame to the scan start so frames never drift.
					if (apsPtr >= (SciDVSFX10HardwareInterface.SIZE_112 / 2)) {
						final long minWords = (long) (getSipLinkHz() / 30.0);
						if ((sipWordIndex - apsLastEmitWord) >= minWords) {
							// Pad a short tail so a partial scan still renders cleanly.
							for (int r = apsPtr; r < SciDVSFX10HardwareInterface.SIZE_112; r++) {
								apsCol[r] = (apsPtr > 0) ? apsCol[apsPtr - 1] : 0;
							}
							emitApsFrame(buffer, tsUs);
							apsLastEmitWord = sipWordIndex;
						}
					}
					apsPtr = 0;
				}
				else if (apsRun >= APS_MIN_RUN) {
					final int rows = Math.max(1, Math.round(apsRun / (float) apsDwell));
					// Adapt the dwell from single-row runs (robust to clkdiv).
					if (rows == 1) {
						apsDwell = Math.max(8, ((apsDwell * 7) + apsRun) / 8);
					}
					final int gv = grayDecode8(apsLastAdc);
					for (int k = 0; (k < rows) && (apsPtr < SciDVSFX10HardwareInterface.SIZE_112); k++) {
						apsCol[apsPtr++] = gv;
					}
				}
				// Runs shorter than APS_MIN_RUN are DVS-active jitter in the
				// shared ADC field, not scan rows; ignore them.
			}
			apsLastAdc = adc;
			apsRun = 1;
		}

		/**
		 * Emit the reconstructed 112-row column as a full DAVIS APS frame, tiled
		 * across all 126 columns (the rig column-SR is dead, so every column is the
		 * column-0 scan). Scan order: 112-valued address outer, 126-valued inner,
		 * SOF on (0,0); each pixel is a ResetRead(sample)+SignalRead(0) pair so the
		 * DAVIS renderer's CDS reproduces the value. Reuses the shared EVT_APS
		 * encoder. The 8-bit ADC is scaled to the 10-bit sample field.
		 */
		private void emitApsFrame(final AEPacketRaw buffer, final int tsUs) {
			for (int r = 0; r < SIZE_112; r++) {
				final int sample10 = (apsCol[r] << 2) & APS_SAMPLE_MASK;
				for (int c = 0; c < SIZE_126; c++) {
					if (!ensureCapacity(buffer, eventCounter + 2)) {
						return;
					}
					final int sof = ((r == 0) && (c == 0)) ? (1 << 27) : 0;
					final int word1 = (EVT_APS << 28) | sof | ((r & 0x7F) << 20) | ((c & 0x7F) << 13) | sample10;
					eventCounter = SciDVSFX10HardwareInterface.translateWordToRawEvents(tsUs, word1,
						buffer.getAddresses(), buffer.getTimestamps(), eventCounter);
				}
			}
		}
	}
}
