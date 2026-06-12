/*
 * SciDVSFX10DecoderSelfTest.java
 *
 * Offline, hardware-free harness for the SciDVS-FX10 event decoder. Builds a
 * synthetic EP1 byte stream (padding words + DVS events + one complete APS
 * frame with interleaved DVS events), feeds it through the same static
 * translation core used by the live AE reader
 * (SciDVSFX10HardwareInterface.translateWordToRawEvents), then re-applies the
 * DavisBaseCamera extractor conventions to verify the decode end to end:
 * event counts per type, DVS coordinates/polarity, APS frame SOF/EOF markers
 * and the CDS-reconstructed pixel values.
 *
 * Run with:
 *   java -cp jars/jAER.jar net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.SciDVSFX10DecoderSelfTest
 * (only this class and DavisChip/ApsDvsEvent constants are touched; no USB,
 * no GUI, no OpenGL)
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.event.ApsDvsEvent;

/**
 * Sanity-check harness for the FX10 wire-format decoder. Exits with status 0
 * and prints PASS if all checks succeed, prints the failures and exits with
 * status 1 otherwise.
 */
public final class SciDVSFX10DecoderSelfTest {

	private static final int SIZE_X = SciDVSFX10HardwareInterface.SIZE_112; // jAER sizeX = 112
	private static final int SIZE_Y = SciDVSFX10HardwareInterface.SIZE_126; // jAER sizeY = 126

	private static int failures = 0;

	private SciDVSFX10DecoderSelfTest() {
	}

	private static void check(final boolean ok, final String what) {
		if (!ok) {
			failures++;
			System.out.println("FAIL: " + what);
		}
	}

	/** Encodes a DVS wire word: [31:28]=0x0, [27]=pol, [26:20]=addr112, [19:13]=addr126, [12:0]=seq. */
	private static int dvsWord(final int addr112, final int addr126, final int pol, final int seq) {
		return (SciDVSFX10HardwareInterface.EVT_DVS << 28) | ((pol & 0x01) << 27) | ((addr112 & 0x7F) << 20)
			| ((addr126 & 0x7F) << 13) | (seq & 0x1FFF);
	}

	/** Encodes an APS wire word: [31:28]=0x1, [27]=SOF, [26:20]=addr112, [19:13]=addr126, [9:0]=sample. */
	private static int apsWord(final int addr112, final int addr126, final boolean sof, final int sample) {
		return (SciDVSFX10HardwareInterface.EVT_APS << 28) | (sof ? (1 << 27) : 0) | ((addr112 & 0x7F) << 20)
			| ((addr126 & 0x7F) << 13) | (sample & SciDVSFX10HardwareInterface.APS_SAMPLE_MASK);
	}

	/** Encodes an external-input edge word: [31:28]=0x2, [27]=polarity, [12:0]=seq snapshot. */
	private static int extWord(final int rising, final int seq) {
		return (SciDVSFX10HardwareInterface.EVT_EXT << 28) | ((rising & 0x01) << 27) | (seq & 0x1FFF);
	}

	/** The synthetic test pattern: sample value at jAER pixel (x, y). */
	private static int expectedSample(final int x, final int y) {
		return ((x * SIZE_Y) + y) % (DavisChip.MAX_ADC + 1);
	}

	public static void main(final String[] args) {
		// ------------------------------------------------------------------
		// 1. Build the synthetic wire stream (8-byte little-endian entries).
		// Frame scan order per the firmware contract: the 112-valued address
		// (jAER x) in the outer loop, the 126-valued address (jAER y) in the
		// inner loop, SOF flag on the first sample only.
		// ------------------------------------------------------------------
		final int numPixels = SIZE_X * SIZE_Y;
		final int dvsBefore = 5, dvsInterleaved = SIZE_X, dvsAfter = 3;
		final int numDvs = dvsBefore + dvsInterleaved + dvsAfter;
		final int numPad = 4;
		final int numBogus = 2; // unknown type + out-of-range address, both must be dropped
		final int numExt = 2; // one rising + one falling external-input edge

		final ByteBuffer stream = ByteBuffer.allocate((numPixels + numDvs + numPad + numBogus + numExt) * 8)
			.order(ByteOrder.LITTLE_ENDIAN);

		int ts = 1000;
		int seq = 0;

		stream.putInt(ts++).putInt(0xF0000000); // padding
		for (int i = 0; i < dvsBefore; i++) {
			stream.putInt(ts++).putInt(dvsWord(i, 2 * i, i & 1, seq++));
		}
		stream.putInt(ts++).putInt(extWord(1, seq)); // external-input rising edge
		stream.putInt(ts++).putInt(extWord(0, seq)); // external-input falling edge
		stream.putInt(ts++).putInt(0x70000000); // unknown type 0x7, must be skipped
		stream.putInt(ts++).putInt(apsWord(0x7F, 0, false, 7)); // addr112=127 out of range, must be dropped

		for (int x = 0; x < SIZE_X; x++) { // outer loop: 112-valued address = jAER x
			for (int y = 0; y < SIZE_Y; y++) { // inner loop: 126-valued address = jAER y
				stream.putInt(ts++).putInt(apsWord(x, y, (x == 0) && (y == 0), expectedSample(x, y)));
			}
			// interleave one DVS event after each row, as the firmware interleaves the streams
			stream.putInt(ts++).putInt(dvsWord(x, x % SIZE_Y, x & 1, seq++));
		}

		for (int i = 0; i < numPad - 1; i++) {
			stream.putInt(ts++).putInt(0xF0000000 | i);
		}
		for (int i = 0; i < dvsAfter; i++) {
			stream.putInt(ts++).putInt(dvsWord(SIZE_X - 1 - i, SIZE_Y - 1 - i, i & 1, seq++));
		}
		stream.flip();

		// ------------------------------------------------------------------
		// 2. Feed every word pair through the shared translation core.
		// ------------------------------------------------------------------
		final int capacity = ((numPixels * 2) + numDvs + 16);
		final int[] addresses = new int[capacity];
		final int[] timestamps = new int[capacity];
		int count = 0;

		while (stream.remaining() >= 8) {
			final int tsUs = stream.getInt();
			final int word1 = stream.getInt();
			count = SciDVSFX10HardwareInterface.translateWordToRawEvents(tsUs, word1, addresses, timestamps, count);
		}

		// ------------------------------------------------------------------
		// 3. Re-apply the DavisBaseCamera extractor conventions and verify.
		// ------------------------------------------------------------------
		int dvsCount = 0, dvsOn = 0, dvsOff = 0, resetReads = 0, signalReads = 0, sofCount = 0, eofCount = 0;
		int dvsCoordErrors = 0, frameValueErrors = 0, scanOrderErrors = 0;
		final int[][] resetBuffer = new int[SIZE_X][SIZE_Y];
		final int[][] frame = new int[SIZE_X][SIZE_Y];
		for (final int[] col : frame) {
			java.util.Arrays.fill(col, -1);
		}
		int expectedDvsIdx = 0;
		int lastApsLinear = -1;
		int extCount = 0;

		for (int i = 0; i < count; i++) {
			final int data = addresses[i];

			if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_DVS) {
				if ((data & DavisChip.EXTERNAL_INPUT_EVENT_ADDR) != 0) {
					// External-input special event (extractor marks it special via bit 10)
					if (extCount == 0) {
						check(data == (DavisChip.EXTERNAL_INPUT_EVENT_ADDR + 3), "first ext event is rising (addr=" + data + ")");
					}
					else {
						check(data == (DavisChip.EXTERNAL_INPUT_EVENT_ADDR + 2), "second ext event is falling (addr=" + data + ")");
					}
					extCount++;
					continue;
				}
				// Extractor: x = sizeX-1-rawX (DVS only), y = rawY.
				final int x = (SIZE_X - 1) - ((data & DavisChip.XMASK) >>> DavisChip.XSHIFT);
				final int y = (data & DavisChip.YMASK) >>> DavisChip.YSHIFT;
				final boolean on = (data & DavisChip.POLMASK) == DavisChip.POLMASK;
				dvsCount++;
				if (on) {
					dvsOn++;
				}
				else {
					dvsOff++;
				}

				// Recompute what the synthetic generator emitted for this DVS event.
				final int k = expectedDvsIdx++;
				int expX, expY, expPol;
				if (k < dvsBefore) {
					expX = k;
					expY = 2 * k;
					expPol = k & 1;
				}
				else if (k < (dvsBefore + dvsInterleaved)) {
					final int r = k - dvsBefore;
					expX = r;
					expY = r % SIZE_Y;
					expPol = r & 1;
				}
				else {
					final int r = k - dvsBefore - dvsInterleaved;
					expX = SIZE_X - 1 - r;
					expY = SIZE_Y - 1 - r;
					expPol = r & 1;
				}
				if ((x != expX) || (y != expY) || ((on ? 1 : 0) != expPol)) {
					dvsCoordErrors++;
				}
			}
			else if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_APS) {
				// Extractor: APS x/y are NOT flipped.
				final int x = (data & DavisChip.XMASK) >>> DavisChip.XSHIFT;
				final int y = (data & DavisChip.YMASK) >>> DavisChip.YSHIFT;
				final int readCycle = (data & DavisChip.ADC_READCYCLE_MASK) >> DavisChip.ADC_NUMBER_OF_TRAILING_ZEROS;
				final int sample = data & DavisChip.ADC_DATA_MASK;

				if (readCycle == ApsDvsEvent.ReadoutType.ResetRead.code) {
					resetReads++;
					resetBuffer[x][y] = sample;
					if ((x == 0) && (y == 0)) {
						sofCount++; // extractor: firstFrameAddress && ResetRead -> SOF flag event
					}
					// scan order: linear index must be strictly increasing within the frame
					final int linear = (x * SIZE_Y) + y;
					if (linear <= lastApsLinear) {
						scanOrderErrors++;
					}
					lastApsLinear = linear;
				}
				else if (readCycle == ApsDvsEvent.ReadoutType.SignalRead.code) {
					signalReads++;
					// Renderer CDS: displayed value = reset - signal.
					final int val = resetBuffer[x][y] - sample;
					frame[x][y] = val;
					if (val != expectedSample(x, y)) {
						frameValueErrors++;
					}
					if ((x == (SIZE_X - 1)) && (y == (SIZE_Y - 1))) {
						eofCount++; // extractor: lastFrameAddress && SignalRead -> EOF flag event
					}
				}
				else {
					failures++;
					System.out.println("FAIL: APS raw event with unexpected read cycle " + readCycle);
				}
			}
			else {
				failures++;
				System.out.println("FAIL: raw event with unknown address type: 0x" + Integer.toHexString(data));
			}
		}

		int missingPixels = 0;
		for (int x = 0; x < SIZE_X; x++) {
			for (int y = 0; y < SIZE_Y; y++) {
				if (frame[x][y] < 0) {
					missingPixels++;
				}
			}
		}

		System.out.println("SciDVS-FX10 decoder self test");
		System.out.println("  raw events out:      " + count);
		System.out.println("  DVS events:          " + dvsCount + " (ON=" + dvsOn + ", OFF=" + dvsOff + ")");
		System.out.println("  APS ResetRead:       " + resetReads);
		System.out.println("  APS SignalRead:      " + signalReads);
		System.out.println("  SOF markers (0,0):   " + sofCount);
		System.out.println("  EOF markers (" + (SIZE_X - 1) + "," + (SIZE_Y - 1) + "): " + eofCount);
		System.out.println("  missing frame pixels:" + missingPixels);

		check(count == ((numPixels * 2) + numDvs + numExt), "total raw event count: got " + count + ", expected " + ((numPixels * 2) + numDvs + numExt)
			+ " (pad/bogus words must produce nothing)");
		check(dvsCount == numDvs, "DVS count: got " + dvsCount + ", expected " + numDvs);
		check(extCount == numExt, "external-input count: got " + extCount + ", expected " + numExt);
		check(resetReads == numPixels, "ResetRead count: got " + resetReads + ", expected " + numPixels);
		check(signalReads == numPixels, "SignalRead count: got " + signalReads + ", expected " + numPixels);
		check(sofCount == 1, "exactly one SOF-generating sample, got " + sofCount);
		check(eofCount == 1, "exactly one EOF-generating sample, got " + eofCount);
		check(dvsCoordErrors == 0, dvsCoordErrors + " DVS events decoded to wrong x/y/polarity");
		check(scanOrderErrors == 0, scanOrderErrors + " APS samples out of row-major scan order");
		check(frameValueErrors == 0, frameValueErrors + " frame pixels with wrong CDS value");
		check(missingPixels == 0, missingPixels + " frame pixels never written");

		if (failures == 0) {
			System.out.println("PASS: all checks OK");
		}
		else {
			System.out.println(failures + " check(s) FAILED");
			System.exit(1);
		}
	}
}
