package nrv.chip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.InvalidPreferencesFormatException;

import javax.swing.JPanel;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.VendorPrefsMigration;
import nrv.usb.NRVHardwareInterface;
import nrv.usb.NRVRegisterSetting;
import nrv.usb.NRVSettingsParser;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.biasgen.PotTweakerUtilities;
import ch.unizh.ini.jaer.chip.retina.DvsDisplayConfigInterface;

/**
 * Bias/configuration control for NRV cameras using SDK text settings files.
 *
 * @see https://nrv.kr/
 */
public class NRVConfig extends Biasgen implements ChipControlPanel, DvsDisplayConfigInterface {

    private static final Logger log = Logger.getLogger(NRVConfig.class.getName());
    public static final String PREFS_LAST_SETTINGS_FILE = "NRVConfig.lastSettingsFile";
    public static final String DEFAULT_SETTINGS_FILENAME = "S5KRC1S_300_CX3.txt";

    public static final int I2C_SLAVE = 0x20;
    /** EVTH MSB range bits: REF[4], ON[3], OFF[2] (SDK: CRGS Setting). */
    public static final int REG_EVTH_MSB = 0x0157;
    /** EVTH_REF_LSB_r [5:0] — reference / brightness threshold (SDK: REG_DIV_BCM_BOT_UNIT_AMP). */
    public static final int REG_BRIGHTNESS_THRESHOLD = 0x0166;
    /** EVTH_ON_LSB_r [5:0] (SDK: REG_DIV_BCM_BOT_UNIT_ON). */
    public static final int REG_ON_UNIT = 0x0167;
    /** EVTH_OFF_LSB_r [5:0] (SDK: REG_DIV_BCM_BOT_UNIT_nOFF). */
    public static final int REG_OFF_UNIT = 0x0168;
    /** TSTAMP_SUB_UNIT_VAL_r MSB/LSB — interval between sub-timestamp USB packets. */
    public static final int REG_TSTAMP_SUB_UNIT_MSB = 0x32B1;
    public static final int REG_TSTAMP_SUB_UNIT_LSB = 0x32B2;
    /**
     * DTAG_FRM_MARGIN_r MSB/LSB ({@code 0x321D:321E}) — primary scan-rate / readout frame period.
     * SDK: {@code 1 LSB × 2^12 × event_clock_period}.
     */
    public static final int REG_DTAG_FRM_MARGIN_MSB = 0x321D;
    public static final int REG_DTAG_FRM_MARGIN_LSB = 0x321E;
    /** Other Scan Rate Setting registers (from settings .txt; editable in full table). */
    public static final int REG_DTAG_MODE = 0x320C;
    public static final int REG_DTAG_SELX = 0x3216;
    public static final int REG_DTAG_SENSE = 0x3217;
    public static final int REG_DTAG_AY = 0x3218;
    public static final int REG_DTAG_AY_RST_GAP = 0x3219;
    public static final int REG_DTAG_APS_RST = 0x321A;
    public static final int REG_DTAG_COL_MARGIN = 0x321C;
    /** OUTIF to_scnt0 — event-clock tick (factory presets: 0x7C → 1 µs). */
    public static final int REG_TO_SCNT0 = 0x3911;
    /** TSTAMP_REF_UNIT_VAL_r MSB/LSB — sub-µs field spans 0..ref within each ref ms. */
    public static final int REG_TSTAMP_REF_MSB = 0x32B3;
    public static final int REG_TSTAMP_REF_LSB = 0x32B4;

    /**
     * Gain on ln(K ratio) terms — analogous to DVS {@code κ_n C_2 / (κ_p² C_1)} (≈ 0.05–0.1),
     * not the literal “10” sometimes seen in NRV register numerators.
     */
    public static final float EVTH_THRESHOLD_GAIN = 0.07f;
    /** Initial offset for EVTH ln-threshold display. */
    public static final float EVTH_THRESHOLD_OFFSET = 0f;
    /** Factory presets use {@code 3911=0x7C} for a 1 µs event-clock period. */
    private static final float EVENT_CLOCK_PERIOD_US_AT_7C = 1f;
    private static final int TO_SCNT0_NOMINAL = 0x7C;

    public static final String PROPERTY_THRESHOLD = "nrvThreshold";
    public static final String PROPERTY_ON_OFF_BALANCE = "nrvOnOffBalance";
    public static final String PROPERTY_TIMESTAMP_SUB = "nrvTimestampSub";
    public static final String PROPERTY_FRAME_MARGIN = "nrvFrameMargin";
    public static final String PROPERTY_SCAN_RATE_HZ = "nrvScanRateHz";
    public static final String PROPERTY_REGISTER_UPDATED = "nrvRegisterUpdated";

    private static final float TWEAK_MAX_RATIO = 8f;
    private static final int REG_VALUE_MIN = 1;
    private static final int REG_VALUE_MAX = 0x3F;

    /** Scan-rate slider range (vendor presets claim ~100–2000 fps; NRV marketing cites up to 2 kHz). */
    public static final int SCAN_RATE_HZ_MIN = 100;
    public static final int SCAN_RATE_HZ_MAX = 2000;

    /**
     * Registers in the settings-file “Scan Rate Setting” block that dominate column/frame timing.
     * {@code DTAG_FRM_MARGIN} alone cannot reach 1–2 kHz under the ×2^12 padding formula.
     */
    private static final int[] SCAN_RATE_REGS = {
            0x320C, 0x3210, 0x3211, 0x3212, 0x3213, 0x3214, 0x3215,
            REG_DTAG_SELX, REG_DTAG_SENSE, REG_DTAG_AY, REG_DTAG_AY_RST_GAP, REG_DTAG_APS_RST,
            REG_DTAG_COL_MARGIN, REG_DTAG_FRM_MARGIN_MSB, REG_DTAG_FRM_MARGIN_LSB
    };

    /** True for scan-rate block and TSTAMP ref/sub registers that affect USB timestamp cadence. */
    public static boolean isTimingRegister(int regAddr) {
        if (regAddr == REG_TSTAMP_SUB_UNIT_MSB || regAddr == REG_TSTAMP_SUB_UNIT_LSB
                || regAddr == REG_TSTAMP_REF_MSB || regAddr == REG_TSTAMP_REF_LSB) {
            return true;
        }
        for (int scanReg : SCAN_RATE_REGS) {
            if (scanReg == regAddr) {
                return true;
            }
        }
        return false;
    }

    /**
     * Known Scan Rate Setting anchors from factory {@code S5KRC1S_*} files (nominal Hz → register bytes).
     * {@code -1} = not present in that preset (keep / take other endpoint when interpolating).
     * <p>
     * Note: CX3 100/300/600 share the same scan-rate registers (only {@code TSTAMP_SUB} differs);
     * 1000 and 2000 change the full DTAG timing block. The ×2^12 FRM_MARGIN note is padding only.
     */
    private static final int[] SCAN_ANCHOR_HZ = {100, 1000, 2000};
    private static final int[][] SCAN_ANCHOR_REGS = {
            // 100 (CX3 slow block; also used by 300/600 scan section)
            {0x1D, 0x1E, 0x00, 0x07, 0x1D, 0x00, 0x00, 0x04, 0x1C, 0x0C, 0x05, 0x07, 0x02, 0x00, 0x0F},
            // 1000 CX3
            {0x1D, 0x19, 0x00, 0x00, 0x04, 0x00, 0x00, 0x1A, 0x1B, 0x0C, 0x14, 0x1C, 0x02, 0x00, 0x02},
            // 2000 FX10
            {0x7D, -1, -1, -1, -1, -1, -1, 0x04, 0x02, 0x0C, 0x05, 0x07, 0x04, 0x00, 0x01}
    };

    private NRVControlPanel controlPanel;
    private String settingsDescription = "";
    private List<NRVRegisterSetting> loadedSettings;
    private File loadedFile;
    private boolean displayEvents = true;
    private boolean displayFrames = false;
    private boolean useAutoContrast = false;
    private float contrast = 1.0f;
    private float brightness = 0.0f;
    private float gamma = 1.0f;
    private float thresholdTweak = 0f;
    private float onOffBalanceTweak = 0f;
    /** True while slider-driven ON/OFF writes are in flight (skip inverse slider sync). */
    private boolean applyingOnOffTweaks = false;
    private boolean applyingScanRateBatch = false;
    private int baselineThreshold = 0x0F;
    private int baselineOnUnit = 0x07;
    private int baselineOffUnit = 0x1F;
    private int baselineTimestampSub = 0x21;
    private int baselineFrameMargin = 0x02;
    /** Target sensor scan / frame-end rate from the user slider (nominal Hz). */
    private int scanRateHz = 300;

    public NRVConfig(Chip chip) {
        super(chip);
        setName("NRVConfig");
        setPotArray(new PotArray(this));
        tryEnsureSettingsParsedFromPreferences();
    }

    @Override
    public boolean isInitialized() {
        if (getHardwareInterface() instanceof NRVHardwareInterface) {
            return ((NRVHardwareInterface) getHardwareInterface()).isSettingsApplied();
        }
        return false;
    }

    public void loadSettingsFile(File file) throws IOException, HardwareInterfaceException {
        parseSettingsFile(file);
        getChip().getPrefs().put(PREFS_LAST_SETTINGS_FILE, file.getAbsolutePath());
        applyLoadedSettingsToHardware();
        support.firePropertyChange(PROPERTY_CHANGE_PREFERENCES_LOADED, null, file);
        support.firePropertyChange(PROPERTY_THRESHOLD, null, thresholdTweak);
        support.firePropertyChange(PROPERTY_ON_OFF_BALANCE, null, onOffBalanceTweak);
        support.firePropertyChange(PROPERTY_SCAN_RATE_HZ, null, scanRateHz);
    }

    private void parseSettingsFile(File file) throws IOException {
        final NRVSettingsParser.ParseResult result = NRVSettingsParser.parseFile(file);
        loadedSettings = result.getSettings();
        settingsDescription = result.getDescription();
        loadedFile = file;
        captureBaselinesFromSettings();
        thresholdTweak = 0f;
        onOffBalanceTweak = 0f;
        scanRateHz = estimateNominalScanRateHzFromFile();
        if (controlPanel != null) {
            controlPanel.updateSettings(loadedSettings, settingsDescription, loadedFile);
        }
    }

    private void applyLoadedSettingsToHardware() throws HardwareInterfaceException {
        if (loadedSettings == null) {
            return;
        }
        if (getHardwareInterface() instanceof NRVHardwareInterface hw) {
            if (!hw.isOpen()) {
                hw.open();
            }
            hw.applySettings(loadedSettings);
        }
    }

    /**
     * Ensures register settings are loaded and pushed over I2C before USB event capture.
     *
     * @return true when settings are applied to the connected device
     */
    public boolean ensureAppliedToHardware() throws HardwareInterfaceException {
        if (loadedSettings == null && !autoLoadSettingsIfNeeded()) {
            return false;
        }
        if (getHardwareInterface() instanceof NRVHardwareInterface hw) {
            if (!hw.isSettingsApplied()) {
                applyLoadedSettingsToHardware();
            }
            return hw.isSettingsApplied();
        }
        return loadedSettings != null;
    }

    /**
     * Parse settings from preferences / default folder if not already in memory.
     * Does not require hardware to be connected.
     */
    private void tryEnsureSettingsParsedFromPreferences() {
        if (loadedSettings != null) {
            return;
        }
        final File settingsFile = resolveLastSettingsFile(null);
        if (settingsFile == null) {
            return;
        }
        try {
            parseSettingsFile(settingsFile);
            log.info("Pre-loaded NRV settings from " + settingsFile);
        } catch (IOException e) {
            log.warning("Could not pre-load NRV settings from " + settingsFile + ": " + e.getMessage());
        }
    }

    /**
     * Load settings when hardware attaches and Biases frame was never opened.
     * Applies parsed settings to hardware when a device is already connected.
     */
    public boolean autoLoadSettingsIfNeeded() {
        if (loadedSettings != null) {
            try {
                if (getHardwareInterface() instanceof NRVHardwareInterface hw && !hw.isSettingsApplied()) {
                    applyLoadedSettingsToHardware();
                }
            } catch (HardwareInterfaceException e) {
                log.warning("Could not apply pre-loaded NRV settings to hardware: " + e.getMessage());
                return false;
            }
            return true;
        }
        final File settingsFile = resolveLastSettingsFile(getBiasgenFrameLastFile());
        if (settingsFile == null) {
            log.warning("NRV camera attached but no settings .txt found (check biasgenSettings/NRV)");
            return false;
        }
        try {
            loadSettingsFile(settingsFile);
            log.info("Auto-loaded NRV settings from " + settingsFile);
            return true;
        } catch (IOException | HardwareInterfaceException e) {
            log.warning("Could not auto-load NRV settings from " + settingsFile + ": " + e.getMessage());
            return false;
        }
    }

    private File getBiasgenFrameLastFile() {
        if (getChip() instanceof AEChip aeChip && aeChip.getAeViewer() != null
                && aeChip.getAeViewer().getBiasgenFrame() != null) {
            return aeChip.getAeViewer().getBiasgenFrame().getLastFile();
        }
        return null;
    }

    private void captureBaselinesFromSettings() {
        baselineThreshold = registerValueOrDefault(REG_BRIGHTNESS_THRESHOLD, 0x0F);
        baselineOnUnit = registerValueOrDefault(REG_ON_UNIT, 0x07);
        baselineOffUnit = registerValueOrDefault(REG_OFF_UNIT, 0x1F);
        baselineTimestampSub = registerValueOrDefault(REG_TSTAMP_SUB_UNIT_LSB, 0x21);
        baselineFrameMargin = ((registerValueOrDefault(REG_DTAG_FRM_MARGIN_MSB, 0) & 0xFF) << 8)
                | (registerValueOrDefault(REG_DTAG_FRM_MARGIN_LSB, 0x02) & 0xFF);
        if (baselineFrameMargin <= 0) {
            baselineFrameMargin = 0x02;
        }
    }

    private int registerValueOrDefault(int regAddr, int defaultValue) {
        final NRVRegisterSetting setting = findRegisterSetting(regAddr);
        return setting == null ? defaultValue : (setting.getValue() & 0xff);
    }

    public NRVRegisterSetting findRegisterSetting(int regAddr) {
        if (loadedSettings == null) {
            return null;
        }
        for (NRVRegisterSetting setting : loadedSettings) {
            if (!setting.isWait() && setting.getRegAddr() == regAddr) {
                return setting;
            }
        }
        return null;
    }

    public int getRegisterValue(int regAddr) {
        final NRVRegisterSetting setting = findRegisterSetting(regAddr);
        return setting == null ? 0 : (setting.getValue() & 0xff);
    }

    public int getBaselineThreshold() {
        return baselineThreshold;
    }

    public int getBaselineOnUnit() {
        return baselineOnUnit;
    }

    public int getBaselineOffUnit() {
        return baselineOffUnit;
    }

    public int getBaselineTimestampSub() {
        return baselineTimestampSub;
    }

    public int getBaselineFrameMargin() {
        return baselineFrameMargin;
    }

    public int getTimestampSubUnit() {
        return getRegisterValue(REG_TSTAMP_SUB_UNIT_LSB);
    }

    /** Combined TSTAMP_SUB_UNIT_VAL ({@code 0x32B1:32B2}). */
    public int getTimestampSubUnitCombined() {
        return (getRegisterValue(REG_TSTAMP_SUB_UNIT_MSB) << 8)
                | (getRegisterValue(REG_TSTAMP_SUB_UNIT_LSB) & 0xFF);
    }

    /**
     * Combined DTAG_FRM_MARGIN ({@code 0x321D:321E}). Prefer {@link #getFrameMarginCombined()}.
     */
    public int getFrameMargin() {
        return getFrameMarginCombined();
    }

    /** EVTH MSB bit from {@link #REG_EVTH_MSB}: 4=REF, 3=ON, 2=OFF. */
    private int evthMsb(int bitIndex) {
        return (getRegisterValue(REG_EVTH_MSB) >> bitIndex) & 1;
    }

    private static int evthLsb(int regValue) {
        return regValue & 0x3F;
    }

    /** Relative reference bias current K_REF (Id) from EVTH REF MSB/LSB. */
    private static double kRef(int refMsb, int refLsb) {
        final double numerator = refMsb * 10.0 + (1 - refMsb) * 2.5;
        return numerator / ((1 + refLsb) * 176.0);
    }

    /** Relative ON bias current K_ON (Ion) from EVTH ON MSB/LSB. */
    private static double kOn(int onMsb, int onLsb) {
        final double numerator = onMsb * 50.0 + (1 - onMsb) * 12.5;
        return numerator / ((1 + onLsb) * 88.0);
    }

    /** Relative OFF bias current K_OFF (Ioff) from EVTH OFF MSB/LSB. */
    private static double kOff(int offMsb, int offLsb) {
        final double numerator = offMsb * 5.0 + (1 - offMsb) * 1.25;
        return numerator / ((1 + offLsb) * 880.0);
    }

    private double currentKRef() {
        return kRef(evthMsb(4), evthLsb(getRegisterValue(REG_BRIGHTNESS_THRESHOLD)));
    }

    private double currentKOn() {
        return kOn(evthMsb(3), evthLsb(getRegisterValue(REG_ON_UNIT)));
    }

    private double currentKOff() {
        return kOff(evthMsb(2), evthLsb(getRegisterValue(REG_OFF_UNIT)));
    }

    /** Relative bias current K_REF (Id) from EVTH REF registers. */
    public double getKRef() {
        return currentKRef();
    }

    /** Relative bias current K_ON (Ion) from EVTH ON registers. */
    public double getKOn() {
        return currentKOn();
    }

    /** Relative bias current K_OFF (Ioff) from EVTH OFF registers. */
    public double getKOff() {
        return currentKOff();
    }

    /**
     * ON temporal-contrast threshold Θ_ON (Nozaki &amp; Delbruck, IEEE TED 2018):
     * {@code gain × ln(K_ON/K_REF)} with K ∝ bias current.
     *
     * @see <a href="https://ieeexplore.ieee.org/document/7962235">7962235</a>
     */
    public float getOnThresholdLogE() {
        final double kRef = currentKRef();
        final double kOn = currentKOn();
        if (kRef <= 0 || kOn <= 0) {
            return Float.NaN;
        }
        return (float) (EVTH_THRESHOLD_GAIN * Math.log(kOn / kRef) + EVTH_THRESHOLD_OFFSET);
    }

    /**
     * OFF temporal-contrast threshold Θ_OFF (signed, negative when K_OFF &lt; K_REF):
     * {@code gain × ln(K_OFF/K_REF)} — same form as DAVIS {@code ln(I_OFF/I_d)}.
     * <p>
     * NRV vendor docs often write {@code gain × ln(K_REF/K_OFF)} (= −Θ_OFF), i.e. magnitude only.
     */
    public float getOffThresholdLogE() {
        final double kRef = currentKRef();
        final double kOff = currentKOff();
        if (kRef <= 0 || kOff <= 0) {
            return Float.NaN;
        }
        return (float) (EVTH_THRESHOLD_GAIN * Math.log(kOff / kRef) + EVTH_THRESHOLD_OFFSET);
    }

    /** NRV vendor-documented OFF magnitude: {@code gain × ln(K_REF/K_OFF) = −Θ_OFF}. */
    public float getOffThresholdLogEVendorMagnitude() {
        final float off = getOffThresholdLogE();
        return Float.isNaN(off) ? Float.NaN : -off;
    }

    /** Percent intensity change from memorized log value: {@code 100 × (e^lnThr − 1)} (DAVIS convention). */
    public static float logThresholdToPercentChange(float logE) {
        if (Float.isNaN(logE)) {
            return Float.NaN;
        }
        return (float) (100.0 * (Math.exp(logE) - 1.0));
    }

    /** Event-clock period in µs from register 0x3911 (1 µs at factory 0x7C). */
    public float getEventClockPeriodUs() {
        final int toScnt0 = getRegisterValue(REG_TO_SCNT0);
        if (toScnt0 <= 0) {
            return EVENT_CLOCK_PERIOD_US_AT_7C;
        }
        return EVENT_CLOCK_PERIOD_US_AT_7C * toScnt0 / TO_SCNT0_NOMINAL;
    }

    /** Combined DTAG_FRM_MARGIN register (MSB:LSB). */
    public int getFrameMarginCombined() {
        return (getRegisterValue(REG_DTAG_FRM_MARGIN_MSB) << 8)
                | (getRegisterValue(REG_DTAG_FRM_MARGIN_LSB) & 0xFF);
    }

    /**
     * FRM_MARGIN padding duration in µs from the SDK note
     * {@code 1 LSB × 2^12 × event_clock}. This is <b>not</b> the full sensor frame period —
     * column scan timing ({@code 0x3216}–{@code 0x321C}, etc.) dominates at high rates.
     */
    public float getFrmMarginPaddingUs() {
        return getFrmMarginPaddingUsForMargin(getFrameMarginCombined());
    }

    public float getFrmMarginPaddingUsForMargin(int marginCombined) {
        if (marginCombined <= 0) {
            return Float.NaN;
        }
        return marginCombined * 4096f * getEventClockPeriodUs();
    }

    /**
     * @deprecated Misleading: only FRM_MARGIN padding under ×2^12, not true scan rate.
     * Use {@link #getScanRateHz()} / {@link #setScanRateHz(int)}.
     */
    @Deprecated
    public float getFramePeriodUs() {
        return getFrmMarginPaddingUs();
    }

    /** @deprecated Use {@link #getScanRateHz()}. */
    @Deprecated
    public float getReadoutFrameRateHz() {
        return scanRateHz;
    }

    /** @deprecated Use {@link #getScanRateHz()}. */
    @Deprecated
    public float getReadoutFrameRateHzForMargin(int marginCombined) {
        final float pad = getFrmMarginPaddingUsForMargin(marginCombined);
        if (Float.isNaN(pad) || pad <= 0) {
            return Float.NaN;
        }
        return 1_000_000f / pad;
    }

    /** @deprecated Use {@link #getFrmMarginPaddingUsForMargin(int)}. */
    @Deprecated
    public float getFramePeriodUsForMargin(int marginCombined) {
        return getFrmMarginPaddingUsForMargin(marginCombined);
    }

    /** Combined TSTAMP_REF_UNIT_VAL (0x32B3:32B4). */
    public int getTstampRefUnitVal() {
        return (getRegisterValue(REG_TSTAMP_REF_MSB) << 8)
                | (getRegisterValue(REG_TSTAMP_REF_LSB) & 0xFF);
    }

    /**
     * Sub-timestamp interval in µs within each reference millisecond:
     * {@code (TSTAMP_REF_UNIT + 1) / TSTAMP_SUB_UNIT}.
     */
    public float getSubTimestampIntervalUs() {
        return getSubTimestampIntervalUsForSubUnit(getTimestampSubUnit());
    }

    /** Sub-timestamp interval for a SUB register value (for slider preview). */
    public float getSubTimestampIntervalUsForSubUnit(int subUnit) {
        if (subUnit <= 0) {
            return Float.NaN;
        }
        return (getTstampRefUnitVal() + 1f) / subUnit;
    }

    public int getScanRateHz() {
        return scanRateHz;
    }

    /**
     * Sets the nominal sensor scan / frame-end rate by interpolating the full
     * Scan Rate Setting register block between factory anchors (100 / 1000 / 2000 Hz).
     * Also updates {@code TSTAMP_SUB} to the vendor pairing for that rate.
     * <p>
     * True frame-end rate should be verified from USB {@code 0x0C} frame-end packets;
     * {@code DTAG_FRM_MARGIN × 2^12 × clk} alone cannot explain 1–2 kHz.
     */
    public void setScanRateHz(int hz) {
        hz = clampScanRateHz(hz);
        final int old = scanRateHz;
        if (old == hz) {
            return;
        }
        scanRateHz = hz;
        try {
            applyingScanRateBatch = true;
            applyScanRateRegisters(hz);
            final int sub = timestampSubForScanRateHz(hz);
            if (getTimestampSubUnit() != sub) {
                applyTweakRegister(REG_TSTAMP_SUB_UNIT_LSB, sub);
            }
        } catch (HardwareInterfaceException e) {
            scanRateHz = old;
            log.warning("NRV scan-rate apply failed: " + e.getMessage());
            return;
        } finally {
            applyingScanRateBatch = false;
        }
        notifyTimingRegisterChange(-1, "scanRateHz=" + hz);
        support.firePropertyChange(PROPERTY_SCAN_RATE_HZ, old, hz);
        support.firePropertyChange(PROPERTY_FRAME_MARGIN, null, getFrameMarginCombined());
        support.firePropertyChange(PROPERTY_TIMESTAMP_SUB, null, getTimestampSubUnit());
    }

    private static int clampScanRateHz(int hz) {
        if (hz < SCAN_RATE_HZ_MIN) {
            return SCAN_RATE_HZ_MIN;
        }
        if (hz > SCAN_RATE_HZ_MAX) {
            return SCAN_RATE_HZ_MAX;
        }
        return hz;
    }

    private void applyScanRateRegisters(int hz) throws HardwareInterfaceException {
        final int[] values = interpolateScanRateRegs(hz);
        for (int i = 0; i < SCAN_RATE_REGS.length; i++) {
            if (values[i] < 0) {
                continue;
            }
            writeOrCreateRegister(SCAN_RATE_REGS[i], values[i] & 0xFF);
        }
    }

    private static int[] interpolateScanRateRegs(int hz) {
        hz = clampScanRateHz(hz);
        int hi = SCAN_ANCHOR_HZ.length - 1;
        int lo = 0;
        for (int i = 0; i < SCAN_ANCHOR_HZ.length; i++) {
            if (hz <= SCAN_ANCHOR_HZ[i]) {
                hi = i;
                lo = Math.max(0, i - 1);
                break;
            }
        }
        if (hz <= SCAN_ANCHOR_HZ[0]) {
            return SCAN_ANCHOR_REGS[0].clone();
        }
        if (hz >= SCAN_ANCHOR_HZ[SCAN_ANCHOR_HZ.length - 1]) {
            return fillMissingFromLower(SCAN_ANCHOR_REGS[SCAN_ANCHOR_HZ.length - 1],
                    SCAN_ANCHOR_REGS[SCAN_ANCHOR_HZ.length - 2]);
        }
        final int hzLo = SCAN_ANCHOR_HZ[lo];
        final int hzHi = SCAN_ANCHOR_HZ[hi];
        final float t = hzHi == hzLo ? 0f : (hz - hzLo) / (float) (hzHi - hzLo);
        final int[] a = SCAN_ANCHOR_REGS[lo];
        final int[] b = fillMissingFromLower(SCAN_ANCHOR_REGS[hi], a);
        final int[] out = new int[SCAN_RATE_REGS.length];
        for (int i = 0; i < out.length; i++) {
            final int va = a[i];
            final int vb = b[i];
            if (va < 0 && vb < 0) {
                out[i] = -1;
            } else if (va < 0) {
                out[i] = vb;
            } else if (vb < 0) {
                out[i] = va;
            } else {
                out[i] = Math.round(va + t * (vb - va));
            }
        }
        return out;
    }

    private static int[] fillMissingFromLower(int[] upper, int[] lower) {
        final int[] out = upper.clone();
        for (int i = 0; i < out.length; i++) {
            if (out[i] < 0 && lower[i] >= 0) {
                out[i] = lower[i];
            }
        }
        return out;
    }

    /** Vendor pairing of TSTAMP_SUB LSB with nominal scan-rate presets. */
    public static int timestampSubForScanRateHz(int hz) {
        hz = clampScanRateHz(hz);
        // piecewise linear through 100→0x0B, 300→0x21, 600→0x42, 1000→0x7D
        final int[] hzPts = {100, 300, 600, 1000, 2000};
        final int[] subPts = {0x0B, 0x21, 0x42, 0x7D, 0x7D};
        for (int i = 0; i < hzPts.length; i++) {
            if (hz <= hzPts[i]) {
                if (i == 0) {
                    return subPts[0];
                }
                final float t = (hz - hzPts[i - 1]) / (float) (hzPts[i] - hzPts[i - 1]);
                return Math.round(subPts[i - 1] + t * (subPts[i] - subPts[i - 1]));
            }
        }
        return subPts[subPts.length - 1];
    }

    private int estimateNominalScanRateHzFromFile() {
        final String name = loadedFile != null ? loadedFile.getName().toLowerCase() : "";
        final String desc = settingsDescription != null ? settingsDescription.toLowerCase() : "";
        if (name.contains("2000") || desc.contains("2000")) {
            return 2000;
        }
        if (name.contains("1000") || desc.contains("1000")) {
            return 1000;
        }
        if (name.contains("600") || desc.contains("600")) {
            return 600;
        }
        if (name.contains("300") || desc.contains("300")) {
            return 300;
        }
        if (name.contains("100") || desc.contains("100")) {
            return 100;
        }
        return nearestAnchorHzFromCurrentRegs();
    }

    private int nearestAnchorHzFromCurrentRegs() {
        int bestHz = 300;
        long bestDist = Long.MAX_VALUE;
        for (int a = 0; a < SCAN_ANCHOR_HZ.length; a++) {
            long dist = 0;
            int compared = 0;
            for (int i = 0; i < SCAN_RATE_REGS.length; i++) {
                final int anchorVal = SCAN_ANCHOR_REGS[a][i];
                if (anchorVal < 0) {
                    continue;
                }
                dist += Math.abs(getRegisterValue(SCAN_RATE_REGS[i]) - anchorVal);
                compared++;
            }
            if (compared > 0 && dist < bestDist) {
                bestDist = dist;
                bestHz = SCAN_ANCHOR_HZ[a];
            }
        }
        return bestHz;
    }

    private void writeOrCreateRegister(int regAddr, int value) throws HardwareInterfaceException {
        NRVRegisterSetting setting = findRegisterSetting(regAddr);
        if (setting == null) {
            if (loadedSettings == null) {
                throw new HardwareInterfaceException("No settings loaded; cannot write 0x"
                        + Integer.toHexString(regAddr));
            }
            setting = new NRVRegisterSetting(I2C_SLAVE, regAddr, value, "scan-rate");
            loadedSettings.add(setting);
        }
        writeRegisterValue(setting, value);
    }

    /**
     * Sets TSTAMP_SUB_UNIT LSB ({@code 0x32B2}); MSB {@code 0x32B1} is left as loaded (usually 0).
     * Lower values increase sub-timestamp USB packet rate within each ref ms.
     * Factory presets: 100→0x0B, 300→0x21, 600→0x42, 1000→0x7D.
     */
    public void setTimestampSubUnit(int value) {
        applyDirectRegisterValue(REG_TSTAMP_SUB_UNIT_LSB, clampTimestampSub(value), PROPERTY_TIMESTAMP_SUB);
    }

    /**
     * Sets combined DTAG_FRM_MARGIN ({@code 0x321D:321E}), writing both MSB and LSB.
     * Prefer {@link #setScanRateHz(int)} to change the full scan-rate block.
     */
    public void setFrameMargin(int combined) {
        combined = clampFrameMarginCombined(combined);
        final int old = getFrameMarginCombined();
        if (old == combined) {
            return;
        }
        final int msb = (combined >> 8) & 0xFF;
        final int lsb = combined & 0xFF;
        try {
            writeOrCreateRegister(REG_DTAG_FRM_MARGIN_MSB, msb);
            writeOrCreateRegister(REG_DTAG_FRM_MARGIN_LSB, lsb);
        } catch (HardwareInterfaceException e) {
            log.warning("NRV DTAG_FRM_MARGIN write failed: " + e.getMessage());
            return;
        }
        support.firePropertyChange(PROPERTY_FRAME_MARGIN, old, combined);
    }

    private static int clampTimestampSub(int value) {
        if (value < 1) {
            return 1;
        }
        if (value > 0xFF) {
            return 0xFF;
        }
        return value;
    }

    private static int clampFrameMarginCombined(int value) {
        if (value < 1) {
            return 1;
        }
        if (value > 0xFFFF) {
            return 0xFFFF;
        }
        return value;
    }

    private void applyDirectRegisterValue(int regAddr, int newValue, String propertyName) {
        final int current = getRegisterValue(regAddr);
        if (current == newValue) {
            return;
        }
        try {
            applyTweakRegister(regAddr, newValue);
        } catch (HardwareInterfaceException e) {
            log.warning("NRV register 0x" + Integer.toHexString(regAddr) + " write failed: " + e.getMessage());
            return;
        }
        support.firePropertyChange(propertyName, current, newValue);
    }

    public float getThresholdTweak() {
        return thresholdTweak;
    }

    public float getOnOffBalanceTweak() {
        return onOffBalanceTweak;
    }

    /**
     * Tweaks ON and OFF thresholds together from file baselines (like DVS {@code diffOn}/{@code diffOff}).
     * Does not modify {@code K_REF} ({@code 0x0166}) or the ON/OFF balance slider value.
     *
     * @param val slider value in -1..1 (higher → fewer events)
     */
    public void setThresholdTweak(float val) {
        val = clampTweak(val);
        if (thresholdTweak == val) {
            return;
        }
        final float old = thresholdTweak;
        thresholdTweak = val;
        try {
            applyOnOffFromTweaks();
        } catch (HardwareInterfaceException e) {
            thresholdTweak = old;
            log.warning("NRV threshold tweak failed: " + e.getMessage());
            return;
        }
        support.firePropertyChange(PROPERTY_THRESHOLD, old, val);
    }

    /**
     * Tweaks ON/OFF event balance from file baselines (like DVS {@code diff} pot).
     * Does not modify {@code K_REF} ({@code 0x0166}) or the event-threshold slider value.
     *
     * @param val slider value in -1..1 (higher → more ON events)
     */
    public void setOnOffBalanceTweak(float val) {
        val = clampTweak(val);
        if (onOffBalanceTweak == val) {
            return;
        }
        final float old = onOffBalanceTweak;
        onOffBalanceTweak = val;
        try {
            applyOnOffFromTweaks();
        } catch (HardwareInterfaceException e) {
            onOffBalanceTweak = old;
            log.warning("NRV ON/OFF balance tweak failed: " + e.getMessage());
            return;
        }
        support.firePropertyChange(PROPERTY_ON_OFF_BALANCE, old, val);
    }

    /**
     * Applies both sliders to ON/OFF LSB registers from file baselines (DVS-style preferred ratios).
     * Higher LSB → lower K → lower |threshold| for that polarity.
     * <ul>
     * <li>Threshold right: lower ON LSB, higher OFF LSB → both |Θ| up (like DVS {@code diffOn↑}/{@code diffOff↓})</li>
     * <li>Balance right: higher ON and OFF LSB → Θ_ON down, |Θ_OFF| up (more ON / fewer OFF; like raising DVS {@code diff}/Id)</li>
     * </ul>
     */
    private void applyOnOffFromTweaks() throws HardwareInterfaceException {
        applyingOnOffTweaks = true;
        try {
            applyTweakRegister(REG_ON_UNIT, onRegisterFromTweaks());
            applyTweakRegister(REG_OFF_UNIT, offRegisterFromTweaks());
        } finally {
            applyingOnOffTweaks = false;
        }
    }

    /** {@code 0x0167}: baseline × balance / threshold. */
    private int onRegisterFromTweaks() {
        final float thrRatio = PotTweakerUtilities.getRatioTweak(thresholdTweak, TWEAK_MAX_RATIO);
        final float balRatio = PotTweakerUtilities.getRatioTweak(onOffBalanceTweak, TWEAK_MAX_RATIO);
        return clampRegister(Math.round(baselineOnUnit * balRatio / thrRatio));
    }

    /** {@code 0x0168}: baseline × balance × threshold (OFF LSB polarity opposite ON for threshold). */
    private int offRegisterFromTweaks() {
        final float thrRatio = PotTweakerUtilities.getRatioTweak(thresholdTweak, TWEAK_MAX_RATIO);
        final float balRatio = PotTweakerUtilities.getRatioTweak(onOffBalanceTweak, TWEAK_MAX_RATIO);
        return clampRegister(Math.round(baselineOffUnit * balRatio * thrRatio));
    }

    private static float clampTweak(float val) {
        if (val > 1f) {
            return 1f;
        }
        if (val < -1f) {
            return -1f;
        }
        return val;
    }

    private static int clampRegister(int value) {
        if (value < REG_VALUE_MIN) {
            return REG_VALUE_MIN;
        }
        if (value > REG_VALUE_MAX) {
            return REG_VALUE_MAX;
        }
        return value;
    }

    private void applyTweakRegister(int regAddr, int newValue) throws HardwareInterfaceException {
        final NRVRegisterSetting setting = findRegisterSetting(regAddr);
        if (setting == null) {
            throw new HardwareInterfaceException("Register not in loaded settings: 0x" + Integer.toHexString(regAddr));
        }
        writeRegisterValue(setting, newValue);
    }

    /**
     * Resolves the settings file to auto-load: saved preference, then BiasgenFrame
     * last file if it is an NRV {@code .txt}, then default preset in biasgenSettings/NRV.
     */
    public File resolveLastSettingsFile(File biasgenFrameLastFile) {
        final String savedPath = getChip().getPrefs().get(PREFS_LAST_SETTINGS_FILE, "");
        if (!savedPath.isEmpty()) {
            final File saved = new File(savedPath);
            if (saved.isFile()) {
                return saved;
            }
            log.info("Saved NRV settings file no longer exists: " + savedPath);
        }
        if (biasgenFrameLastFile != null && biasgenFrameLastFile.isFile()
                && biasgenFrameLastFile.getName().toLowerCase().endsWith(".txt")) {
            return biasgenFrameLastFile;
        }
        final File defaultFile = new File(getDefaultSettingsFolder(), DEFAULT_SETTINGS_FILENAME);
        if (defaultFile.isFile()) {
            return defaultFile;
        }
        return null;
    }

    public static File getDefaultSettingsFolder() {
        final String rel = "biasgenSettings" + File.separator + "NRV";
        final File fromUserDir = new File(System.getProperty("user.dir"), rel);
        if (fromUserDir.isDirectory()) {
            return fromUserDir;
        }
        final File relative = new File(rel);
        if (relative.isDirectory()) {
            return relative;
        }
        try {
            final URL codeLocation = NRVConfig.class.getProtectionDomain().getCodeSource().getLocation();
            File base = new File(codeLocation.toURI());
            if (base.isFile()) {
                base = base.getParentFile();
            }
            for (int depth = 0; depth < 5 && base != null; depth++) {
                final File candidate = new File(base, rel);
                if (candidate.isDirectory()) {
                    return candidate;
                }
                base = base.getParentFile();
            }
        } catch (Exception e) {
            log.fine("Could not resolve NRV settings folder from code location: " + e.getMessage());
        }
        return fromUserDir;
    }

    public boolean loadLastSettingsFromPreferences(File biasgenFrameLastFile) {
        final File settingsFile = resolveLastSettingsFile(biasgenFrameLastFile);
        if (settingsFile == null) {
            return false;
        }
        try {
            loadSettingsFile(settingsFile);
            log.info("Auto-loaded NRV settings from " + settingsFile);
            return true;
        } catch (IOException | HardwareInterfaceException e) {
            log.warning("Could not auto-load NRV settings from " + settingsFile + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public JPanel buildControlPanel() {
        return getControlPanel();
    }

    @Override
    public void setHardwareInterface(final BiasgenHardwareInterface hardwareInterface) {
        super.setHardwareInterface(hardwareInterface);
        if (!(hardwareInterface instanceof NRVHardwareInterface nrvHw)) {
            return;
        }
        try {
            ensureAppliedToHardware();
        } catch (HardwareInterfaceException e) {
            log.warning("Could not apply NRV settings after hardware attach: " + e.getMessage());
        }
    }

    public List<NRVRegisterSetting> getLoadedSettings() {
        return loadedSettings;
    }

    public String getSettingsDescription() {
        return settingsDescription;
    }

    public File getLoadedFile() {
        return loadedFile;
    }

    /**
     * Updates a register in the loaded settings list and writes it to hardware when connected.
     * Offline edits are kept in memory ({@code applied=false}) and pushed on the next connect/apply.
     */
    public void writeRegisterValue(NRVRegisterSetting setting, int newValue) throws HardwareInterfaceException {
        if (setting == null || setting.isWait()) {
            throw new HardwareInterfaceException("Cannot write wait entry");
        }
        if (newValue < 0 || newValue > 0xFF) {
            throw new HardwareInterfaceException("Register value out of range: " + newValue);
        }
        final int oldValue = setting.getValue() & 0xff;
        if (oldValue == (newValue & 0xff)) {
            return;
        }

        setting.setValue(newValue);

        if (getHardwareInterface() instanceof NRVHardwareInterface hw) {
            hw.writeRegister(setting.getSlaveAddr(), setting.getRegAddr(), newValue);
            setting.setApplied(true);
            log.info(String.format("Wrote NRV register %02x:%04x=%02x", setting.getSlaveAddr(),
                    setting.getRegAddr(), newValue & 0xff));
        } else {
            setting.setApplied(false);
            log.fine(String.format("Stored NRV register %02x:%04x=%02x (hardware not connected)",
                    setting.getSlaveAddr(), setting.getRegAddr(), newValue & 0xff));
        }

        syncTweaksFromRegisterWrite(setting.getRegAddr(), newValue & 0xff);
        if (controlPanel != null) {
            controlPanel.updateRegisterRow(setting);
        }
        support.firePropertyChange(PROPERTY_REGISTER_UPDATED, null, setting);
        if (!applyingScanRateBatch && isTimingRegister(setting.getRegAddr())) {
            notifyTimingRegisterChange(setting.getRegAddr(), "registerWrite");
        }
    }

    private void notifyTimingRegisterChange(int regAddr, String reason) {
        if (getHardwareInterface() instanceof NRVHardwareInterface hw) {
            hw.notifyTimingRegisterChanged(regAddr, reason);
        }
    }

    NRVControlPanel getNrvControlPanel() {
        if (controlPanel == null) {
            getControlPanel();
        }
        return controlPanel;
    }

    private void syncTweaksFromRegisterWrite(int regAddr, int newValue) {
        if (applyingOnOffTweaks) {
            return;
        }
        if (regAddr != REG_ON_UNIT && regAddr != REG_OFF_UNIT) {
            return;
        }
        final int on = Math.max(1, getRegisterValue(REG_ON_UNIT));
        final int off = Math.max(1, getRegisterValue(REG_OFF_UNIT));
        if (baselineOnUnit <= 0 || baselineOffUnit <= 0) {
            return;
        }
        // on/baselineOn = bal/thr , off/baselineOff = bal*thr
        final float onRatio = (float) on / baselineOnUnit;
        final float offRatio = (float) off / baselineOffUnit;
        final float balRatio = (float) Math.sqrt(onRatio * offRatio);
        final float thrRatio = (float) Math.sqrt(offRatio / onRatio);

        final float oldBalance = onOffBalanceTweak;
        onOffBalanceTweak = tweakFromRatio(balRatio);
        if (oldBalance != onOffBalanceTweak) {
            support.firePropertyChange(PROPERTY_ON_OFF_BALANCE, oldBalance, onOffBalanceTweak);
        }

        final float oldThreshold = thresholdTweak;
        thresholdTweak = tweakFromRatio(thrRatio);
        if (oldThreshold != thresholdTweak) {
            support.firePropertyChange(PROPERTY_THRESHOLD, oldThreshold, thresholdTweak);
        }
    }

    private static float tweakFromRatio(float ratio) {
        if (ratio <= 0f) {
            return 0f;
        }
        final float logRatio = (float) (Math.log(ratio) / Math.log(TWEAK_MAX_RATIO));
        return clampTweak(logRatio);
    }

    @Override
    public JPanel getControlPanel() {
        if (controlPanel == null) {
            controlPanel = new NRVControlPanel(this);
        }
        return controlPanel;
    }

    @Override
    public boolean isDisplayFrames() {
        return displayFrames;
    }

    @Override
    public void setDisplayFrames(boolean displayFrames) {
        this.displayFrames = displayFrames;
    }

    @Override
    public boolean isDisplayEvents() {
        return displayEvents;
    }

    @Override
    public void setDisplayEvents(boolean displayEvents) {
        this.displayEvents = displayEvents;
    }

    @Override
    public boolean isUseAutoContrast() {
        return useAutoContrast;
    }

    @Override
    public void setUseAutoContrast(boolean useAutoContrast) {
        this.useAutoContrast = useAutoContrast;
    }

    @Override
    public float getContrast() {
        return contrast;
    }

    @Override
    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    @Override
    public float getBrightness() {
        return brightness;
    }

    @Override
    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    @Override
    public float getGamma() {
        return gamma;
    }

    @Override
    public void setGamma(float gamma) {
        this.gamma = gamma;
    }

    @Override
    public void importPreferences(InputStream is) throws IOException, InvalidPreferencesFormatException,
            HardwareInterfaceException {
        super.importPreferences(VendorPrefsMigration.rewriteLegacyPreferencesXml(is));
    }
}
