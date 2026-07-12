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
    /** TSTAMP_SUB_UNIT_VAL_r LSB — interval between sub-timestamp USB packets. */
    public static final int REG_TSTAMP_SUB_UNIT_LSB = 0x32B2;
    /** DTAG_FRM_MARGIN_r LSB — frame period (lower → faster frames / finer event timestamps). */
    public static final int REG_DTAG_FRM_MARGIN_MSB = 0x321D;
    public static final int REG_DTAG_FRM_MARGIN_LSB = 0x321E;
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
    public static final String PROPERTY_REGISTER_UPDATED = "nrvRegisterUpdated";

    private static final float TWEAK_MAX_RATIO = 8f;
    private static final int REG_VALUE_MIN = 1;
    private static final int REG_VALUE_MAX = 0x3F;

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
    private int baselineThreshold = 0x0F;
    private int baselineOnUnit = 0x07;
    private int baselineOffUnit = 0x1F;
    private int baselineTimestampSub = 0x21;
    private int baselineFrameMargin = 0x02;

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
    }

    private void parseSettingsFile(File file) throws IOException {
        final NRVSettingsParser.ParseResult result = NRVSettingsParser.parseFile(file);
        loadedSettings = result.getSettings();
        settingsDescription = result.getDescription();
        loadedFile = file;
        captureBaselinesFromSettings();
        thresholdTweak = 0f;
        onOffBalanceTweak = 0f;
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
     */
    public boolean autoLoadSettingsIfNeeded() {
        if (loadedSettings != null) {
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
        baselineFrameMargin = registerValueOrDefault(REG_DTAG_FRM_MARGIN_LSB, 0x02);
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

    public int getFrameMargin() {
        return getRegisterValue(REG_DTAG_FRM_MARGIN_LSB);
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
     * Sensor frame period in µs: {@code DTAG_FRM_MARGIN × 2^12 × event_clock_period}
     * (SDK note on DTAG_FRM_MARGIN_r_LSB).
     */
    public float getFramePeriodUs() {
        final int margin = getFrameMarginCombined();
        if (margin <= 0) {
            return Float.NaN;
        }
        return margin * 4096f * getEventClockPeriodUs();
    }

    /** Readout frame rate in Hz from frame-margin and event-clock registers. */
    public float getReadoutFrameRateHz() {
        return getReadoutFrameRateHzForMargin(getFrameMarginCombined());
    }

    /** Frame rate for a combined DTAG_FRM_MARGIN value (for slider preview). */
    public float getReadoutFrameRateHzForMargin(int marginCombined) {
        if (marginCombined <= 0) {
            return Float.NaN;
        }
        final float periodUs = marginCombined * 4096f * getEventClockPeriodUs();
        return 1_000_000f / periodUs;
    }

    /** Frame period in µs for a combined DTAG_FRM_MARGIN value. */
    public float getFramePeriodUsForMargin(int marginCombined) {
        if (marginCombined <= 0) {
            return Float.NaN;
        }
        return marginCombined * 4096f * getEventClockPeriodUs();
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

    /**
     * Sets TSTAMP sub-unit (0x32B2). Lower values can increase sub-timestamp rate within a frame;
     * factory presets use 0x0B..0x7D. Values below ~0x0B may behave poorly.
     */
    public void setTimestampSubUnit(int value) {
        applyDirectRegisterValue(REG_TSTAMP_SUB_UNIT_LSB, clampTimestampSub(value), PROPERTY_TIMESTAMP_SUB);
    }

    /**
     * Sets frame margin (0x321E). Lower values shorten the sensor frame period (~400 Hz at 0x02).
     */
    public void setFrameMargin(int value) {
        applyDirectRegisterValue(REG_DTAG_FRM_MARGIN_LSB, clampFrameMargin(value), PROPERTY_FRAME_MARGIN);
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

    private static int clampFrameMargin(int value) {
        if (value < 1) {
            return 1;
        }
        if (value > 0xFF) {
            return 0xFF;
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
        if (loadedSettings == null) {
            autoLoadSettingsIfNeeded();
        } else if (needsApplyToHardware(nrvHw)) {
            try {
                applyLoadedSettingsToHardware();
            } catch (HardwareInterfaceException e) {
                log.warning("Could not apply NRV settings after hardware attach: " + e.getMessage());
            }
        }
    }

    private boolean needsApplyToHardware(NRVHardwareInterface hw) {
        if (!hw.isSettingsApplied()) {
            return true;
        }
        if (loadedSettings == null) {
            return false;
        }
        for (NRVRegisterSetting setting : loadedSettings) {
            if (!setting.isWait() && !setting.isApplied()) {
                return true;
            }
        }
        return false;
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
