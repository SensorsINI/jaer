package es.us.atc.jaer.chips.FpgaConfig;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;

public class ATCFpgaConfig extends EventFilter2D {

        private final int Ntrackers = 4;
        private int trackerId = getInt("trackerId", 1);
	private int[] AcmCellInitX = new int[Ntrackers]; // = getInt("cmCellInitX", 64);
	private int[] AcmCellInitY = new int[Ntrackers]; // = getInt("cmCellInitY", 64);
	private int[] AcmCellRadixTh = new int[Ntrackers]; // = getInt("cmCellRadixStep", 1);
	private int[] AcmCellInitRadix = new int[Ntrackers]; // = getInt("cmCellInitRadix", 1);
	private int[] AcmCellRadixStep = new int[Ntrackers]; // = getInt("cmCellRadixStep", 1);
	private int[] AcmCellRadixMax = new int[Ntrackers]; // = getInt("cmCellRadixStep", 1);
	private int[] AcmCellRadixMin = new int[Ntrackers]; // = getInt("cmCellRadixStep", 1);
	private int[] AcmCellMaxTime = new int[Ntrackers]; // = getInt("cmCellMaxTime", 200000);
	private int[] AcmCellNevTh = new int[Ntrackers]; // = getInt("cmCellNevTh", 1);
	private int[] AcmCellAVG = new int[Ntrackers]; // = getInt("cmCellAVG", 1);
	private boolean[] AtrackerEnable = new boolean[Ntrackers]; //getBoolean("trackerEnable", true);
	private boolean BGAF_OTs_Enable = getBoolean("BGAF_OTs_Enable", true);
	private boolean OTsEnable = getBoolean("OTsEnable", true);
        private boolean DAVIS_Enable = getBoolean("DAVIS_Enable", true);

	private int bgaFilterDeltaT = getInt("bgaFilterDeltaT", 100);

	// FPGA clock speed in MegaHertz (MHz) for time conversion.
	private final int CLOCK_SPEED = 60;
        
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
		setPropertyTooltip("BGAF_OTs_Enable", "If unchecked, both bacground filter and trackers are bypassed.");
		setPropertyTooltip("OTsEnable", "Enable all the trackers.");

		setPropertyTooltip("bgaFilterDeltaT", "Delta time for BackgroundActivity filter (in µs).");
	}

	public boolean isBGAF_OTs_Enable() {
		return BGAF_OTs_Enable;
	}

	public void setBGAF_OTs_Enable(final boolean Enable) {
		this.BGAF_OTs_Enable = Enable;
		putBoolean("BGAF_OTs_Enable", Enable);
	}

       	public boolean isOTsEnable() {
		return OTsEnable;
	}

	public void setOTsEnable(final boolean Enable) {
		this.OTsEnable = Enable;
		putBoolean("OTsEnable", Enable);
	}
        
       	public boolean isDAVIS_Enable() {
		return DAVIS_Enable;
	}

	public void setDAVIS_Enable(final boolean Enable) {
		this.DAVIS_Enable = Enable;
		putBoolean("DAVIS_Enable", Enable);
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
            support.firePropertyChange("trackerId", 0, trackerId);
		this.trackerId = trackerId;
                putInt("trackerId", trackerId);
                setCmCellInitX(getCmCellInitX());
                setCmCellInitY(getCmCellInitY());
                setCmCellRadixStep(getCmCellRadixStep());
                setCmCellRadixTh(getCmCellRadixTh());
                setCmCellRadixMax(getCmCellRadixMax());
                setCmCellRadixMin(getCmCellRadixMin());
                setCmCellInitRadix(getCmCellInitRadix());
                setCmCellMaxTime(getCmCellMaxTime());
                setCmCellNevTh(getCmCellNevTh());
                setCmCellAVG(getCmCellAVG());
                setTrackerEnable(isTrackerEnable());
	}

	public int getCmCellInitX() {
		return AcmCellInitX[trackerId - 1];
	}

	public static int getMinCmCellInitX() {
		return 0;
	}

	public static int getMaxCmCellInitX() {
		return 127;
	}

	public void setCmCellInitX(final int cmCellInitX) {
            support.firePropertyChange("cmCellInitX", 0, cmCellInitX);
		this.AcmCellInitX[trackerId - 1] = cmCellInitX;
		putInt("cmCellInitX_" + (trackerId - 1), cmCellInitX);
	}

	public int getCmCellInitY() {
		return AcmCellInitY[trackerId - 1];
	}

	public static int getMinCmCellInitY() {
		return 0;
	}

	public static int getMaxCmCellInitY() {
		return 127;
	}

	public void setCmCellInitY(final int cmCellInitY) {
            support.firePropertyChange("cmCellInitY", 0, cmCellInitY);
            this.AcmCellInitY[trackerId - 1] = cmCellInitY;
            putInt("cmCellInitY_" + (trackerId - 1), cmCellInitY);
	}

	public int getCmCellRadixStep() {
		return AcmCellRadixStep[trackerId - 1];
	}

	public static int getMinCmCellRadixStep() {
		return 0;
	}

	public static int getMaxCmCellRadixStep() {
		return 7;
	}

	public void setCmCellRadixStep(final int cmCellRadixStep) {
            support.firePropertyChange("cmCellRadixStep", 0, cmCellRadixStep);
		this.AcmCellRadixStep[trackerId - 1] = cmCellRadixStep;
		putInt("cmCellRadixStep_" + (trackerId - 1), cmCellRadixStep);
	}

	public int getCmCellRadixTh() {
		return AcmCellRadixTh[trackerId - 1];
	}

	public static int getMinCmCellRadixTh() {
		return 0;
	}

	public static int getMaxCmCellRadixTh() {
		return 7;
	}

	public void setCmCellRadixTh(final int cmCellRadixTh) {
            support.firePropertyChange("cmCellRadixTh", 0, cmCellRadixTh);
		this.AcmCellRadixTh[trackerId - 1] = cmCellRadixTh;
		putInt("cmCellRadixTh_" + (trackerId - 1), cmCellRadixTh);
	}

	public int getCmCellRadixMax() {
		return AcmCellRadixMax[trackerId - 1];
	}

	public static int getMinCmCellRadixMax() {
		return 0;
	}

	public static int getMaxCmCellRadixMax() {
		return 63;
	}

	public void setCmCellRadixMax(final int cmCellRadixMax) {
            support.firePropertyChange("cmCellRadixMax", 0, cmCellRadixMax);
		this.AcmCellRadixMax[trackerId - 1] = cmCellRadixMax;
		putInt("cmCellRadixMax_" + (trackerId - 1), cmCellRadixMax);
	}

        public int getCmCellRadixMin() {
		return AcmCellRadixMin[trackerId - 1];
	}

	public static int getMinCmCellRadixMin() {
		return 0;
	}

	public static int getMaxCmCellRadixMin() {
		return 63;
	}

	public void setCmCellRadixMin(final int cmCellRadixMin) {
            support.firePropertyChange("cmCellRadixMin", 0, cmCellRadixMin);
		this.AcmCellRadixMin[trackerId - 1] = cmCellRadixMin;
		putInt("cmCellRadixMin_" + (trackerId - 1), cmCellRadixMin);
	}

        public int getCmCellInitRadix() {
		return AcmCellInitRadix[trackerId - 1];
	}

	public static int getMinCmCellInitRadix() {
		return 0;
	}

	public static int getMaxCmCellInitRadix() {
		return 63;
	}

	public void setCmCellInitRadix(final int cmCellInitRadix) {
            support.firePropertyChange("cmCellInitRadix", 0, cmCellInitRadix);
		this.AcmCellInitRadix[trackerId - 1] = cmCellInitRadix;
		putInt("cmCellInitRadix_" + (trackerId - 1), cmCellInitRadix);
	}

	public int getCmCellMaxTime() {
		return AcmCellMaxTime[trackerId - 1];
	}

	public static int getMinCmCellMaxTime() {
		return 1; // 1 micro-second (in µs).
	}

	public static int getMaxCmCellMaxTime() {
		return 1000000; // 1 second (in µs).
	}

	public void setCmCellMaxTime(final int cmCellMaxTime) {
            support.firePropertyChange("cmCellMaxTime", 0, cmCellMaxTime);
		this.AcmCellMaxTime[trackerId - 1] = cmCellMaxTime;
		putInt("cmCellMaxTime_" + (trackerId - 1), cmCellMaxTime);
	}

	public int getCmCellNevTh() {
		return AcmCellNevTh[trackerId - 1];
	}

	public static int getMinCmCellNevTh() {
		return 1;
	}

	public static int getMaxCmCellNevTh() {
		return 1000;
	}

	public void setCmCellNevTh(final int cmCellNevTh) {
            support.firePropertyChange("cmCellNevTh", 0, cmCellNevTh);
		this.AcmCellNevTh[trackerId - 1] = cmCellNevTh;
		putInt("cmCellNevTh_" + (trackerId - 1), cmCellNevTh);
	}

	public int getCmCellAVG() {
		return AcmCellAVG[trackerId - 1];
	}

	public static int getMinCmCellAVG() {
		return 1;
	}

	public static int getMaxCmCellAVG() {
		return 8;
	}

	public void setCmCellAVG(final int cmCellAVG) {
            support.firePropertyChange("cmCellAVG", 0, cmCellAVG);
		this.AcmCellAVG[trackerId - 1] = cmCellAVG;
		putInt("cmCellAVG_" + (trackerId - 1), cmCellAVG);
	}

	public boolean isTrackerEnable() {
		return AtrackerEnable[trackerId];
	}

	public void setTrackerEnable(final boolean trackerEnable) {
            support.firePropertyChange("trackerEnable",0,trackerEnable);
		this.AtrackerEnable[trackerId-1] = trackerEnable;
		putBoolean("trackerEnable_" + (trackerId-1), trackerEnable);
	}

	synchronized public void doConfigureCMCell() {
		// Convert time into cycles.
		final int cmCellMaxTimeCycles = getInt("cmCellMaxTime_" + (trackerId - 1), 0) * CLOCK_SPEED;

		// Select the tracker.
		sendCommand((byte) 127, (byte) (trackerId & 0xFF));

		// Send all the tracker configuration.
		sendCommand((byte) 78, (byte) (getInt("cmCellInitY_" + (trackerId - 1), 0) & 0xFF));
		sendCommand((byte) 79, (byte) (getInt("cmCellInitX_" + (trackerId - 1), 0) & 0xFF));
		sendCommand((byte) 80, (byte) (getInt("cmCellRadixTh_" + (trackerId - 1), 0) & 0xFF));
		sendCommand((byte) 81, (byte) (getInt("cmCellInitRadix_" + (trackerId - 1), 0) & 0xFF));
		sendCommand((byte) 82, (byte) (cmCellMaxTimeCycles & 0xFF));
		sendCommand((byte) 83, (byte) ((cmCellMaxTimeCycles >>> 8) & 0xFF));
		sendCommand((byte) 84, (byte) ((cmCellMaxTimeCycles >>> 16) & 0xFF));
		sendCommand((byte) 85, (byte) ((cmCellMaxTimeCycles >>> 24) & 0xFF));
		sendCommand((byte) 86, (byte) (getInt("cmCellNevTh_" + (trackerId - 1), 0) & 0xFF));
		sendCommand((byte) 87, (byte) (getInt("cmCellAVG_" + (trackerId - 1), 0) & 0xFF));
		sendCommand((byte) 88, (byte) ((getBoolean("trackerEnable_" + (trackerId-1), true)) ? (0xFF) : (0x00)));
		sendCommand((byte) 89, (byte) (getInt("cmCellRadixStep_" + (trackerId - 1), 0) & 0xFF));
		sendCommand((byte) 90, (byte) (getInt("cmCellRadixMax_" + (trackerId - 1), 0) & 0xFF));
		sendCommand((byte) 91, (byte) (getInt("cmCellRadixMin_" + (trackerId - 1), 0) & 0xFF));

		// Disable tracker configuration, so CMCell is not under reset
		sendCommand((byte) 127, (byte) 0);
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
		sendCommand((byte) 128, (byte) (bgaFilterDeltaTCycles & 0xFF));
		sendCommand((byte) 129, (byte) ((bgaFilterDeltaTCycles >>> 8) & 0xFF));
		sendCommand((byte) 130, (byte) ((bgaFilterDeltaTCycles >>> 16) & 0xFF));
		sendCommand((byte) 131, (byte) ((bgaFilterDeltaTCycles >>> 24) & 0xFF));
		sendCommand((byte) 133, (byte) ((getBoolean("BGAF_OTs_Enable", true)) ? (0xFF) : (0x00)));
		sendCommand((byte) 132, (byte) ((getBoolean("OTsEnable", true)) ? (0xFF) : (0x00)));
		sendCommand((byte) 134, (byte) ((getBoolean("DAVIS_Enable", true)) ? (0xFF) : (0x00)));
	}

	@Override
	public EventPacket<?> filterPacket(final EventPacket<?> in) {
		// Don't modify events and packets going through.
		return (in);
	}

	private void sendCommand(final byte cmd, final byte data) {
		System.out.println(String.format("Sending command - cmd: %X, data: %X", cmd, data));

		if ((chip.getHardwareInterface() != null) && (chip.getHardwareInterface() instanceof CypressFX3)) {
			try {
				((CypressFX3) chip.getHardwareInterface()).sendVendorRequest((byte) 0xBF,
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
            for (int i=0;i<Ntrackers;i++) {
                AcmCellInitX[i] = getInt("cmCellInitX", 64 + (i%2)*64);
                AcmCellInitY[i] = getInt("cmCellInitY", 64 + (i%2)*64);
                AcmCellRadixTh[i] = getInt("cmCellRadixTh", 7);
                AcmCellInitRadix[i] = getInt("cmCellInitRadix", 63);
                AcmCellRadixStep[i] = getInt("cmCellRadixStep", 7);
                AcmCellRadixMax[i] = getInt("AcmCellRadixMax", 7);
                AcmCellRadixMin[i] = getInt("AcmCellRadixMin", 1);
                AcmCellMaxTime[i] = getInt("cmCellMaxTime", 200000);
                AcmCellNevTh[i] = getInt("cmCellNevTh", 5);
                AcmCellAVG[i] = getInt("cmCellAVG", 1);
                AtrackerEnable[i] = getBoolean("trackerEnable", (i%2)==0);
            }
            trackerId = 1;
                setCmCellInitX(AcmCellInitX[trackerId-1]);
                setCmCellInitY(AcmCellInitY[trackerId-1]);
                setCmCellRadixStep(AcmCellRadixStep[trackerId-1]);
                setCmCellRadixTh(AcmCellRadixTh[trackerId-1]);
                setCmCellRadixMax(AcmCellRadixMax[trackerId-1]);
                setCmCellRadixMin(AcmCellRadixMin[trackerId-1]);
                setCmCellInitRadix(AcmCellInitRadix[trackerId-1]);
                setCmCellMaxTime(AcmCellMaxTime[trackerId-1]);
                setCmCellNevTh(AcmCellNevTh[trackerId-1]);
                setCmCellAVG(AcmCellAVG[trackerId-1]);
                setTrackerEnable(AtrackerEnable[trackerId-1]);
	}

	@Override
	public void initFilter() {
		// Empty.
            for (int i=0;i<Ntrackers;i++) {
                AcmCellInitX[i] = getInt("cmCellInitX", 64 + (i%2)*64);
                AcmCellInitY[i] = getInt("cmCellInitY", 64 + (i%2)*64);
                AcmCellRadixTh[i] = getInt("cmCellRadixTh", 7);
                AcmCellInitRadix[i] = getInt("cmCellInitRadix", 63);
                AcmCellRadixStep[i] = getInt("cmCellRadixStep", 7);
                AcmCellRadixMax[i] = getInt("AcmCellRadixMax", 7);
                AcmCellRadixMin[i] = getInt("AcmCellRadixMin", 1);
                AcmCellMaxTime[i] = getInt("cmCellMaxTime", 200000);
                AcmCellNevTh[i] = getInt("cmCellNevTh", 5);
                AcmCellAVG[i] = getInt("cmCellAVG", 1);
                AtrackerEnable[i] = getBoolean("trackerEnable", (i%2)==0);
            }
            trackerId = 1;
                setCmCellInitX(AcmCellInitX[trackerId-1]);
                setCmCellInitY(AcmCellInitY[trackerId-1]);
                setCmCellRadixStep(AcmCellRadixStep[trackerId-1]);
                setCmCellRadixTh(AcmCellRadixTh[trackerId-1]);
                setCmCellRadixMax(AcmCellRadixMax[trackerId-1]);
                setCmCellRadixMin(AcmCellRadixMin[trackerId-1]);
                setCmCellInitRadix(AcmCellInitRadix[trackerId-1]);
                setCmCellMaxTime(AcmCellMaxTime[trackerId-1]);
                setCmCellNevTh(AcmCellNevTh[trackerId-1]);
                setCmCellAVG(AcmCellAVG[trackerId-1]);
                setTrackerEnable(AtrackerEnable[trackerId-1]);
	}
}
