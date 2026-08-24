/*
 * CypressFX3Biasgen.java
 *
 * Created on 23 Jan 2008
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.ByteBuffer;

import org.usb4java.Device;

import eu.seebetter.ini.chips.davis.DavisConfig;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * GAER-format SciDVS USB decode (orphaned).
 * <p>
 * <b>Not registered</b> in {@link LibUsb3HardwareInterfaceFactory}: PID
 * {@code 0x841A}/{@code 0x841B} open as {@link DAViSFX3HardwareInterface}. Live
 * SciDVS therefore shares the Davis FX3 typed {@code PacketBundle} demux path
 * (prefs {@code hardware/DAViSFX3/usbTypedDemux}). This class remains for
 * reference / experimental GAER boards only — do not assume it is the live path.
 *
 * @author Christian/Tobi
 * @deprecated Use {@link DAViSFX3HardwareInterface} for factory-opened SciDVS devices.
 */
@Deprecated
public class SciDVSHardwareInterface extends CypressFX3Biasgen {

	protected SciDVSHardwareInterface(final Device device) {
		super(device);
	}

	@Override
	synchronized public void sendConfiguration(final net.sf.jaer.biasgen.Biasgen biasgen) throws HardwareInterfaceException {
		if ((biasgen != null) && (biasgen instanceof DavisConfig)) {
			((DavisConfig) biasgen).sendConfiguration();
		}
	}

	/** The USB product ID of this device */
	static public final short PID_FX3 = (short) 0x841A;
	static public final short PID_FX2 = (short) 0x841B;
	static public final int REQUIRED_FIRMWARE_VERSION_FX3 = 6;
	static public final int REQUIRED_FIRMWARE_VERSION_FX2 = 4;
	static public final int REQUIRED_LOGIC_REVISION_FX3 = 18;
	//static public final int REQUIRED_LOGIC_REVISION_FX3 = 9912;
	static public final int REQUIRED_LOGIC_REVISION_FX2 = 18;

	static public final int GAER_GROUPADDR_WIDTH = 5;
	static public final int GAER_ADDRY_WIDTH = 7;
	static public final int GAER_EVENT_WIDTH = 4;

	static int decodeGaerTransfer(final SciDVSGaerDecoder decoder,
		final SciDVSGaerRawSink rawSink, final AEPacketRaw buffer,
		final int startingEventCounter, final ByteBuffer input) {
		rawSink.begin(buffer, startingEventCounter);
		decoder.decode(input, rawSink);
		return rawSink.end();
	}

	private boolean updatedRealClockValues = false;
	public float logicClockFreq = 90.0f;
	public float adcClockFreq = 30.0f;
	public float usbClockFreq = 30.0f;

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

	private void getRealClockValues() {
		try {
			final int logicFreq = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 3);
			final int adcFreq = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 4);
			final int usbFreq = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 5);
			final int clockDeviation = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 6);

			logicClockFreq = (float) (logicFreq * (clockDeviation / 1000.0));
			adcClockFreq = (float) (adcFreq * (clockDeviation / 1000.0));
			usbClockFreq = (float) (usbFreq * (clockDeviation / 1000.0));
		}
		catch (final HardwareInterfaceException e) {
			// No clock update on failure.
		}

		CypressFX3.log
			.info(String.format("Device clock frequencies - Logic: %f, ADC: %f, USB: %f.", logicClockFreq, adcClockFreq, usbClockFreq));
	}

	@Override
	protected int adjustHWParam(final short moduleAddr, final short paramAddr, final int param) {
		if (!updatedRealClockValues) {
			getRealClockValues();
			updatedRealClockValues = true;
		}
                
		if ((moduleAddr == CypressFX3.FPGA_APS) && (paramAddr == 12)) {
			// Exposure multiplied by clock.
			return (int) (param * adcClockFreq);
		}

		if ((moduleAddr == CypressFX3.FPGA_APS) && (paramAddr == 13)) {
			// FrameInterval multiplied by clock.
			return (int) (param * adcClockFreq);
		}

		if ((moduleAddr == CypressFX3.FPGA_USB) && (paramAddr == 1)) {
			// Early packet delay is 125µs slices on host, but in cycles
			// @ USB_CLOCK_FREQ on FPGA, so we must multiply here.
			return (int) (param * (125.0f * usbClockFreq));
		}

		// No change by default.
		return (param);
	}

	public static final int CHIP_DAVIS240A = 0;
	public static final int CHIP_DAVIS240B = 1;
	public static final int CHIP_DAVIS240C = 2;
	public static final int CHIP_DAVIS128 = 3;
	public static final int CHIP_DAVIS346A = 4;
	public static final int CHIP_DAVIS346B = 5;
	public static final int CHIP_DAVIS640 = 6;
	public static final int CHIP_DAVISRGB = 7;
	public static final int CHIP_DAVIS208 = 8;
	public static final int CHIP_DAVIS346C = 9;

	/**
	 * This reader understands the format of raw USB data and translates to the
	 * AEPacketRaw
	 */
	public class RetinaAEReader extends CypressFX3.AEReader implements PropertyChangeListener {
		private final SciDVSGaerDecoder gaerDecoder;
		private final SciDVSGaerRawSink gaerRawSink;

		public RetinaAEReader(final CypressFX3 cypress) throws HardwareInterfaceException {
			super(cypress);

			if (getPID() == SciDVSHardwareInterface.PID_FX2) {
				// FX2 firmware now emulates the same interface as FX3 firmware, so we support it here too.
				checkFirmwareLogic(SciDVSHardwareInterface.REQUIRED_FIRMWARE_VERSION_FX2,
					SciDVSHardwareInterface.REQUIRED_LOGIC_REVISION_FX2);
			}
			else {
				checkFirmwareLogic(SciDVSHardwareInterface.REQUIRED_FIRMWARE_VERSION_FX3,
					SciDVSHardwareInterface.REQUIRED_LOGIC_REVISION_FX3);
			}

			final int chipID = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 1);

			final int apsSizeX = spiConfigReceive(CypressFX3.FPGA_APS, (short) 0);
			final int apsSizeY = spiConfigReceive(CypressFX3.FPGA_APS, (short) 1);

			final int chipAPSStreamStart = spiConfigReceive(CypressFX3.FPGA_APS, (short) 2);
			final boolean apsInvertXY = (chipAPSStreamStart & 0x04) != 0;
			final boolean apsFlipX = (chipAPSStreamStart & 0x02) != 0;
			final boolean apsFlipY = (chipAPSStreamStart & 0x01) != 0;

			final int dvsSizeX = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 0);
			final int dvsSizeY = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 1);
			//dvsSizeY = 128;
			//dvsSizeX = 126;

			final boolean dvsInvertXY = (spiConfigReceive(CypressFX3.FPGA_DVS, (short) 2) & 0x04) != 0;

			final int imuOrientation = spiConfigReceive(CypressFX3.FPGA_IMU, (short) 1);
			final boolean imuFlipX = (imuOrientation & 0x04) != 0;
			final boolean imuFlipY = (imuOrientation & 0x02) != 0;
			final boolean imuFlipZ = (imuOrientation & 0x01) != 0;

			updateTimestampMasterStatus();

			gaerDecoder = new SciDVSGaerDecoder(new SciDVSGaerDecoder.Config(
				chipID, dvsSizeX, dvsSizeY, dvsInvertXY,
				apsSizeX, apsSizeY, apsInvertXY, apsFlipX, apsFlipY,
				imuFlipX, imuFlipY, imuFlipZ), super.toString());
			gaerRawSink = new SciDVSGaerRawSink(
				SciDVSHardwareInterface.this::getAEBufferSize, this::handleTimestampReset);
		}

		@Override
		protected void translateEvents(final ByteBuffer b) {
			synchronized (aePacketRawPool) {
				final AEPacketRaw buffer = aePacketRawPool.writeBuffer();
				eventCounter = SciDVSHardwareInterface.decodeGaerTransfer(
					gaerDecoder, gaerRawSink, buffer, eventCounter, b);
			}
		}

		private void handleTimestampReset() {
			updateTimestampMasterStatus();
			CypressFX3.log.info("Timestamp reset event received on " + super.toString()
				+ " at System.currentTimeMillis()=" + System.currentTimeMillis());
		}

		@Override
		public void propertyChange(final PropertyChangeEvent arg0) {
			// Do nothing here, IMU comes directly via event-stream.
		}
	}
}
