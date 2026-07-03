package ch.unizh.ini.jaer.chip.nrv;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JPanel;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.nrv.NRVHardwareInterface;
import net.sf.jaer.hardwareinterface.usb.nrv.NRVRegisterSetting;
import net.sf.jaer.hardwareinterface.usb.nrv.NRVSettingsParser;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.biasgen.PotTweakerUtilities;
import ch.unizh.ini.jaer.chip.retina.DvsDisplayConfigInterface;

/**
 * Bias/configuration control for NRV cameras using SDK text settings files.
 */
public class NRVConfig extends Biasgen implements ChipControlPanel, DvsDisplayConfigInterface {

    private static final Logger log = Logger.getLogger(NRVConfig.class.getName());
    public static final String PREFS_LAST_SETTINGS_FILE = "NRVConfig.lastSettingsFile";
    public static final String DEFAULT_SETTINGS_FILENAME = "S5KRC1S_300_CX3.txt";

    public static final int I2C_SLAVE = 0x20;
    /** REG_DIV_BCM_BOT_UNIT_AMP — brightness change threshold. */
    public static final int REG_BRIGHTNESS_THRESHOLD = 0x0166;
    /** REG_DIV_BCM_BOT_UNIT_ON */
    public static final int REG_ON_UNIT = 0x0167;
    /** REG_DIV_BCM_BOT_UNIT_nOFF / OFF */
    public static final int REG_OFF_UNIT = 0x0168;

    public static final String PROPERTY_THRESHOLD = "nrvThreshold";
    public static final String PROPERTY_ON_OFF_BALANCE = "nrvOnOffBalance";
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
    private int baselineThreshold = 0x0F;
    private int baselineOnUnit = 0x07;
    private int baselineOffUnit = 0x1F;

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

    public float getThresholdTweak() {
        return thresholdTweak;
    }

    public float getOnOffBalanceTweak() {
        return onOffBalanceTweak;
    }

    /**
     * Tweaks brightness change threshold around the value loaded from file.
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
        final int newValue = tweakRegisterValue(baselineThreshold, val, TWEAK_MAX_RATIO);
        try {
            applyTweakRegister(REG_BRIGHTNESS_THRESHOLD, newValue);
        } catch (HardwareInterfaceException e) {
            thresholdTweak = old;
            log.warning("NRV threshold tweak failed: " + e.getMessage());
            return;
        }
        support.firePropertyChange(PROPERTY_THRESHOLD, old, val);
    }

    /**
     * Tweaks ON/OFF event balance around values loaded from file.
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
        final float ratio = PotTweakerUtilities.getRatioTweak(val, TWEAK_MAX_RATIO);
        final int onValue = clampRegister((int) Math.round(baselineOnUnit * ratio));
        final int offValue = clampRegister((int) Math.round(baselineOffUnit / ratio));
        try {
            applyTweakRegister(REG_ON_UNIT, onValue);
            applyTweakRegister(REG_OFF_UNIT, offValue);
        } catch (HardwareInterfaceException e) {
            onOffBalanceTweak = old;
            log.warning("NRV ON/OFF balance tweak failed: " + e.getMessage());
            return;
        }
        support.firePropertyChange(PROPERTY_ON_OFF_BALANCE, old, val);
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

    private static int tweakRegisterValue(int baseline, float tweak, float maxRatio) {
        final float ratio = PotTweakerUtilities.getRatioTweak(tweak, maxRatio);
        return clampRegister((int) Math.round(baseline * ratio));
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
        File folder = new File(System.getProperty("user.dir") + File.separator + "biasgenSettings" + File.separator + "NRV");
        if (!folder.isDirectory()) {
            folder = new File("biasgenSettings" + File.separator + "NRV");
        }
        return folder;
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
        } else if (!nrvHw.isSettingsApplied()) {
            try {
                applyLoadedSettingsToHardware();
            } catch (HardwareInterfaceException e) {
                log.warning("Could not apply NRV settings after hardware attach: " + e.getMessage());
            }
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
     * Sends a single register value to the camera. Updates in-memory setting only.
     */
    public void writeRegisterValue(NRVRegisterSetting setting, int newValue) throws HardwareInterfaceException {
        if (setting == null || setting.isWait()) {
            throw new HardwareInterfaceException("Cannot write wait entry");
        }
        if (newValue < 0 || newValue > 0xFF) {
            throw new HardwareInterfaceException("Register value out of range: " + newValue);
        }
        if (!(getHardwareInterface() instanceof NRVHardwareInterface)) {
            throw new HardwareInterfaceException("NRV hardware not connected");
        }
        final NRVHardwareInterface hw = (NRVHardwareInterface) getHardwareInterface();
        hw.writeRegister(setting.getSlaveAddr(), setting.getRegAddr(), newValue);
        setting.setValue(newValue);
        setting.setApplied(true);
        log.info(String.format("Wrote NRV register %02x:%04x=%02x", setting.getSlaveAddr(),
                setting.getRegAddr(), newValue & 0xff));
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
        if (regAddr == REG_BRIGHTNESS_THRESHOLD) {
            final float old = thresholdTweak;
            thresholdTweak = tweakFromRegisterValue(newValue, baselineThreshold);
            if (old != thresholdTweak) {
                support.firePropertyChange(PROPERTY_THRESHOLD, old, thresholdTweak);
            }
        } else if (regAddr == REG_ON_UNIT || regAddr == REG_OFF_UNIT) {
            final float onRatio = (float) getRegisterValue(REG_ON_UNIT) / baselineOnUnit;
            final float offRatio = (float) baselineOffUnit / Math.max(1, getRegisterValue(REG_OFF_UNIT));
            final float ratio = (float) Math.sqrt(onRatio * offRatio);
            final float old = onOffBalanceTweak;
            onOffBalanceTweak = tweakFromRatio(ratio);
            if (old != onOffBalanceTweak) {
                support.firePropertyChange(PROPERTY_ON_OFF_BALANCE, old, onOffBalanceTweak);
            }
        }
    }

    private static float tweakFromRegisterValue(int current, int baseline) {
        if (baseline <= 0) {
            return 0f;
        }
        return tweakFromRatio((float) current / baseline);
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
}
