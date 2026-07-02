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
import ch.unizh.ini.jaer.chip.retina.DvsDisplayConfigInterface;

/**
 * Bias/configuration control for NRV cameras using SDK text settings files.
 */
public class NRVConfig extends Biasgen implements ChipControlPanel, DvsDisplayConfigInterface {

    private static final Logger log = Logger.getLogger(NRVConfig.class.getName());
    public static final String PREFS_LAST_SETTINGS_FILE = "NRVConfig.lastSettingsFile";
    public static final String DEFAULT_SETTINGS_FILENAME = "S5KRC1S_300_CX3.txt";

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

    public NRVConfig(Chip chip) {
        super(chip);
        setName("NRVConfig");
        setPotArray(new PotArray(this));
    }

    @Override
    public boolean isInitialized() {
        if (getHardwareInterface() instanceof NRVHardwareInterface) {
            return ((NRVHardwareInterface) getHardwareInterface()).isSettingsApplied();
        }
        return false;
    }

    public void loadSettingsFile(File file) throws IOException, HardwareInterfaceException {
        final NRVSettingsParser.ParseResult result = NRVSettingsParser.parseFile(file);
        loadedSettings = result.getSettings();
        settingsDescription = result.getDescription();
        loadedFile = file;
        getChip().getPrefs().put(PREFS_LAST_SETTINGS_FILE, file.getAbsolutePath());

        if (getHardwareInterface() instanceof NRVHardwareInterface) {
            final NRVHardwareInterface hw = (NRVHardwareInterface) getHardwareInterface();
            if (!hw.isOpen()) {
                hw.open();
            }
            hw.applySettings(loadedSettings);
        }

        if (controlPanel != null) {
            controlPanel.updateSettings(loadedSettings, settingsDescription, loadedFile);
        }
        support.firePropertyChange(PROPERTY_CHANGE_PREFERENCES_LOADED, null, file);
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
        if (loadedSettings != null && hardwareInterface instanceof NRVHardwareInterface) {
            try {
                if (!hardwareInterface.isOpen()) {
                    hardwareInterface.open();
                }
                ((NRVHardwareInterface) hardwareInterface).applySettings(loadedSettings);
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
