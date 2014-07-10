package es.us.atc.jaer.chips.FpgaConfig;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;

public class ATCFpgaConfig extends EventFilter2D {
	private int trackerId = getInt("trackerId", 1);
	private int cmCellInitX = getInt("cmCellInitX", 64);
	private int cmCellInitY = getInt("cmCellInitY", 64);
	private int cmCellRadixStep = getInt("cmCellRadixStep", 1);
	private int cmCellInitRadix = getInt("cmCellInitRadix", 1);
	private int cmCellMaxTime = getInt("cmCellMaxTime", 200000);
	private int cmCellNevTh = getInt("cmCellNevTh", 1);
	private int cmCellAVG = getInt("cmCellAVG", 1);
	private boolean trackerEnable = getBoolean("trackerEnable", true);

	private int bgaFilterDeltaT = getInt("bgaFilterDeltaT", 100);

	// FPGA clock speed in MegaHertz (MHz) for time conversion.
	private final int CLOCK_SPEED = 50;

	public ATCFpgaConfig(final AEChip chip) {
		super(chip);

		initFilter();

		setPropertyTooltip("trackerId", "ID of the tracker to configure.");
		setPropertyTooltip("cmCellInitX", "Initial focus point (X axis).");
		setPropertyTooltip("cmCellInitY", "Initial focus point (Y axis).");
		setPropertyTooltip("cmCellRadixStep", "Threshold for increasing cluster area dynamically.");
		setPropertyTooltip("cmCellInitRadix", "Initial cluster radix.");
		setPropertyTooltip("cmCellMaxTime",
			"Maximum allowed delay without detecting events for current tracking (in µs). Once elapsed cell will reset itself.");
		setPropertyTooltip("cmCellNevTh",
			"Number of events to receive within a cluster before calculating center of mass.");
		setPropertyTooltip("cmCellAVG",
			"Amount of CM history involved in calculating the average for the new CM point (2^cmCellAVG).");
		setPropertyTooltip("trackerEnable", "Enable this tracker.");

		setPropertyTooltip("bgaFilterDeltaT", "Delta time for BackgroundActivity filter (in µs).");
	}

	public int getTrackerId() {
		return trackerId;
	}

	public static int getMinTrackerId() {
		return 1;
	}

	public static int getMaxTrackerId() {
		return 4;
	}

	public void setTrackerId(final int trackerId) {
		this.trackerId = trackerId;
		putInt("trackerId", trackerId);
	}

	public int getCmCellInitX() {
		return cmCellInitX;
	}

	public static int getMinCmCellInitX() {
		return 0;
	}

	public static int getMaxCmCellInitX() {
		return 127;
	}

	public void setCmCellInitX(final int cmCellInitX) {
		this.cmCellInitX = cmCellInitX;
		putInt("cmCellInitX", cmCellInitX);
	}

	public int getCmCellInitY() {
		return cmCellInitY;
	}

	public static int getMinCmCellInitY() {
		return 0;
	}

	public static int getMaxCmCellInitY() {
		return 127;
	}

	public void setCmCellInitY(final int cmCellInitY) {
		this.cmCellInitY = cmCellInitY;
		putInt("cmCellInitY", cmCellInitY);
	}

	public int getCmCellRadixStep() {
		return cmCellRadixStep;
	}

	public static int getMinCmCellRadixStep() {
		return 0;
	}

	public static int getMaxCmCellRadixStep() {
		return 7;
	}

	public void setCmCellRadixStep(final int cmCellRadixStep) {
		this.cmCellRadixStep = cmCellRadixStep;
		putInt("cmCellRadixStep", cmCellRadixStep);
	}

	public int getCmCellInitRadix() {
		return cmCellInitRadix;
	}

	public static int getMinCmCellInitRadix() {
		return 0;
	}

	public static int getMaxCmCellInitRadix() {
		return 63;
	}

	public void setCmCellInitRadix(final int cmCellInitRadix) {
		this.cmCellInitRadix = cmCellInitRadix;
		putInt("cmCellInitRadix", cmCellInitRadix);
	}

	public int getCmCellMaxTime() {
		return cmCellMaxTime;
	}

	public static int getMinCmCellMaxTime() {
		return 1; // 1 micro-second (in µs).
	}

	public static int getMaxCmCellMaxTime() {
		return 1000000; // 1 second (in µs).
	}

	public void setCmCellMaxTime(final int cmCellMaxTime) {
		this.cmCellMaxTime = cmCellMaxTime;
		putInt("cmCellMaxTime", cmCellMaxTime);
	}

	public int getCmCellNevTh() {
		return cmCellNevTh;
	}

	public static int getMinCmCellNevTh() {
		return 1;
	}

	public static int getMaxCmCellNevTh() {
		return 1000;
	}

	public void setCmCellNevTh(final int cmCellNevTh) {
		this.cmCellNevTh = cmCellNevTh;
		putInt("cmCellNevTh", cmCellNevTh);
	}

	public int getCmCellAVG() {
		return cmCellAVG;
	}

	public static int getMinCmCellAVG() {
		return 1;
	}

	public static int getMaxCmCellAVG() {
		return 8;
	}

	public void setCmCellAVG(final int cmCellAVG) {
		this.cmCellAVG = cmCellAVG;
		putInt("cmCellAVG", cmCellAVG);
	}

	public boolean isTrackerEnable() {
		return trackerEnable;
	}

	public void setTrackerEnable(final boolean trackerEnable) {
		this.trackerEnable = trackerEnable;
		putBoolean("trackerEnable", trackerEnable);
	}

	synchronized public void doConfigureCMCell() {
		// Convert time into cycles.
		final int cmCellMaxTimeCycles = getInt("cmCellMaxTime", 0) * CLOCK_SPEED;

		// Select the tracker.
		sendCommand((byte) 127, (byte) (getInt("trackerId", 0) & 0xFF), true);

		// Send all the tracker configuration.
		sendCommand((byte) 78, (byte) (getInt("cmCellInitY", 0) & 0xFF), true);
		sendCommand((byte) 79, (byte) (getInt("cmCellInitX", 0) & 0xFF), true);
		sendCommand((byte) 80, (byte) (getInt("cmCellRadixStep", 0) & 0xFF), true);
		sendCommand((byte) 81, (byte) (getInt("cmCellInitRadix", 0) & 0xFF), true);
		sendCommand((byte) 82, (byte) (cmCellMaxTimeCycles & 0xFF), true);
		sendCommand((byte) 83, (byte) ((cmCellMaxTimeCycles >>> 8) & 0xFF), true);
		sendCommand((byte) 84, (byte) ((cmCellMaxTimeCycles >>> 16) & 0xFF), true);
		sendCommand((byte) 85, (byte) ((cmCellMaxTimeCycles >>> 24) & 0xFF), true);
		sendCommand((byte) 86, (byte) (getInt("cmCellNevTh", 0) & 0xFF), true);
		sendCommand((byte) 87, (byte) (getInt("cmCellAVG", 0) & 0xFF), true);
		sendCommand((byte) 88, (byte) ((getBoolean("trackerEnable", true)) ? (0xFF) : (0x00)), true);

		// Disable tracker configuration.
		sendCommand((byte) 127, (byte) 0xFF, true);

		sendCommand((byte) 0, (byte) 0, false);

	}

	public int getBgaFilterDeltaT() {
		return bgaFilterDeltaT;
	}

	public static int getMinBgaFilterDeltaT() {
		return 1; // 1 micro-second (in µs).
	}

	public static int getMaxBgaFilterDeltaT() {
		return 1000000; // 1 second (in µs).
	}

	public void setBgaFilterDeltaT(final int bgaFilterDeltaT) {
		this.bgaFilterDeltaT = bgaFilterDeltaT;
		putInt("bgaFilterDeltaT", bgaFilterDeltaT);
	}

	synchronized public void doConfigureBGAFilter() {
		// Convert time into cycles.
		final int bgaFilterDeltaTCycles = getInt("bgaFilterDeltaT", 0) * CLOCK_SPEED;

		// Send the four bytes that make up the integer to their respective
		// addresses.
		sendCommand((byte) 128, (byte) (bgaFilterDeltaTCycles & 0xFF), true);
		sendCommand((byte) 129, (byte) ((bgaFilterDeltaTCycles >>> 8) & 0xFF), true);
		sendCommand((byte) 130, (byte) ((bgaFilterDeltaTCycles >>> 16) & 0xFF), true);
		sendCommand((byte) 131, (byte) ((bgaFilterDeltaTCycles >>> 24) & 0xFF), true);

		sendCommand((byte) 0, (byte) 0, false);
	}

	@Override
	public EventPacket<?> filterPacket(final EventPacket<?> in) {
		// Don't modify events and packets going through.
		return (in);
	}

	private void sendCommand(final byte cmd, final byte data, final boolean spiEnable) {
		System.out.println(String.format("Sending command - cmd: %X, data: %X", cmd, data));

		if ((chip.getHardwareInterface() != null) && (chip.getHardwareInterface() instanceof CypressFX3)) {
			try {
				((CypressFX3) chip.getHardwareInterface()).sendVendorRequest((byte) 0xC2,
					(short) (0x0100 | (cmd & 0xFF)), (short) (data & 0xFF));
			}
			catch (HardwareInterfaceException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void resetFilter() {
		// Empty.
	}

	@Override
	public void initFilter() {
		// Empty.
	}
}
