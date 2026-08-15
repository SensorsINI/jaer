package ch.unizh.ini.jaer.chip.retina;

import java.awt.BorderLayout;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JPanel;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DVXplorerFX3HardwareInterface;

/**
 * DV-style DVXplorer (VGA Samsung DVS) bias and readout control.
 * Maps {@code contrastThresholdOn/Off} (0–17, default 9) and {@code ReadoutFPS} the same way as
 * iniVation dv-processing {@code dv::io::camera::DVXplorer}.
 *
 * @see <a href="https://docs.inivation.com/hardware/hardware-advanced-usage/biasing.html">iniVation biasing</a>
 * @see <a href="https://gitlab.com/inivation/dv/dv-processing/-/blob/master/include/dv-processing/io/camera/dvxplorer.hpp">dvxplorer.hpp</a>
 */
public class DVXplorerConfig extends Biasgen implements ChipControlPanel {

    private static final Logger log = Logger.getLogger(DVXplorerConfig.class.getName());

    public static final String PROPERTY_CONTRAST_ON = "contrastThresholdOn";
    public static final String PROPERTY_CONTRAST_OFF = "contrastThresholdOff";
    public static final String PROPERTY_READOUT_FPS = "readoutFps";
    public static final String PROPERTY_GLOBAL_HOLD = "globalHold";
    public static final String PROPERTY_GLOBAL_RESET = "globalReset";

    public static final int CONTRAST_MIN = 0;
    public static final int CONTRAST_MAX = 17;
    public static final int CONTRAST_DEFAULT = 9;

    /** Fine clock count used by dv-processing for T_ED / readout interval scaling. */
    public static final int SYSTEM_CLOCK_FREQUENCY = 50;

    private static final String PREF_ON = "DVXplorerConfig.contrastThresholdOn";
    private static final String PREF_OFF = "DVXplorerConfig.contrastThresholdOff";
    private static final String PREF_FPS = "DVXplorerConfig.readoutFps";
    private static final String PREF_HOLD = "DVXplorerConfig.globalHold";
    private static final String PREF_RESET = "DVXplorerConfig.globalReset";

    private int contrastThresholdOn = CONTRAST_DEFAULT;
    private int contrastThresholdOff = CONTRAST_DEFAULT;
    private ReadoutFPS readoutFps = ReadoutFPS.VARIABLE_5000;
    private boolean globalHold = true;
    private boolean globalReset = false;

    private DVXplorerControlPanel controlUi;

    /**
     * Event-frame readout modes from dv-processing / DV GUI.
     * CONSTANT: fixed period, no loss. CONSTANT_LOSSY: fixed period, may truncate.
     * VARIABLE: best-effort period, no loss (default VARIABLE_5000).
     */
    public enum ReadoutFPS {
        CONSTANT_100("CONSTANT_100 (10 ms, no loss)"),
        CONSTANT_200("CONSTANT_200 (5 ms, no loss)"),
        CONSTANT_500("CONSTANT_500 (2 ms, no loss)"),
        CONSTANT_1000("CONSTANT_1000 (1 ms, no loss)"),
        CONSTANT_LOSSY_2000("CONSTANT_LOSSY_2000 (500 µs, may drop)"),
        CONSTANT_LOSSY_5000("CONSTANT_LOSSY_5000 (200 µs, may drop)"),
        CONSTANT_LOSSY_10000("CONSTANT_LOSSY_10000 (100 µs, may drop)"),
        VARIABLE_2000("VARIABLE_2000 (500 µs min)"),
        VARIABLE_5000("VARIABLE_5000 (200 µs min, DV default)"),
        VARIABLE_10000("VARIABLE_10000 (100 µs min)"),
        VARIABLE_15000("VARIABLE_15000 (66 µs min)");

        private final String label;

        ReadoutFPS(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public DVXplorerConfig(Chip chip) {
        super(chip);
        setName("DVXplorerConfig");
        setPotArray(new PotArray(this));
        final Preferences p = getChip().getPrefs();
        if (p.get("BiasgenFrame.viewFunctionalBiasesEnabled", null) == null) {
            p.putBoolean("BiasgenFrame.viewFunctionalBiasesEnabled", true);
        }
    }

    public int getContrastThresholdOn() {
        return contrastThresholdOn;
    }

    public int getContrastThresholdOff() {
        return contrastThresholdOff;
    }

    public ReadoutFPS getReadoutFps() {
        return readoutFps;
    }

    public boolean isGlobalHold() {
        return globalHold;
    }

    public boolean isGlobalReset() {
        return globalReset;
    }

    public void setContrastThresholdOn(int value) {
        value = clampContrast(value);
        if (value == contrastThresholdOn) {
            return;
        }
        final int old = contrastThresholdOn;
        contrastThresholdOn = value;
        try {
            applyContrastOn();
            markFileModified();
            support.firePropertyChange(PROPERTY_CONTRAST_ON, old, value);
        } catch (HardwareInterfaceException e) {
            contrastThresholdOn = old;
            log.warning(e.toString());
        }
    }

    public void setContrastThresholdOff(int value) {
        value = clampContrast(value);
        if (value == contrastThresholdOff) {
            return;
        }
        final int old = contrastThresholdOff;
        contrastThresholdOff = value;
        try {
            applyContrastOff();
            markFileModified();
            support.firePropertyChange(PROPERTY_CONTRAST_OFF, old, value);
        } catch (HardwareInterfaceException e) {
            contrastThresholdOff = old;
            log.warning(e.toString());
        }
    }

    public void setReadoutFps(ReadoutFPS fps) {
        if (fps == null) {
            fps = ReadoutFPS.VARIABLE_5000;
        }
        if (fps == readoutFps) {
            return;
        }
        final ReadoutFPS old = readoutFps;
        readoutFps = fps;
        try {
            applyReadoutFps();
            markFileModified();
            support.firePropertyChange(PROPERTY_READOUT_FPS, old, fps);
        } catch (HardwareInterfaceException e) {
            readoutFps = old;
            log.warning(e.toString());
        }
    }

    public void setGlobalHold(boolean enable) {
        if (enable == globalHold) {
            return;
        }
        final boolean old = globalHold;
        globalHold = enable;
        try {
            applyGlobalHoldReset();
            markFileModified();
            support.firePropertyChange(PROPERTY_GLOBAL_HOLD, old, enable);
        } catch (HardwareInterfaceException e) {
            globalHold = old;
            log.warning(e.toString());
        }
    }

    public void setGlobalReset(boolean enable) {
        if (enable == globalReset) {
            return;
        }
        final boolean old = globalReset;
        globalReset = enable;
        try {
            applyGlobalHoldReset();
            markFileModified();
            support.firePropertyChange(PROPERTY_GLOBAL_RESET, old, enable);
        } catch (HardwareInterfaceException e) {
            globalReset = old;
            log.warning(e.toString());
        }
    }

    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        applyToHardware();
    }

    /** Write all live settings to the open DVXplorer. No-op if the camera is closed. */
    public void applyToHardware() throws HardwareInterfaceException {
        final DVXplorerFX3HardwareInterface fx3 = fx3();
        if (fx3 == null || !fx3.isOpen()) {
            return;
        }
        applyContrastOn();
        applyContrastOff();
        applyGlobalHoldReset();
        applyReadoutFps();
    }

    @Override
    public void loadPreferences() {
        final Preferences p = getChip().getPrefs();
        contrastThresholdOn = clampContrast(p.getInt(PREF_ON, CONTRAST_DEFAULT));
        contrastThresholdOff = clampContrast(p.getInt(PREF_OFF, CONTRAST_DEFAULT));
        readoutFps = parseFps(p.get(PREF_FPS, ReadoutFPS.VARIABLE_5000.name()));
        globalHold = p.getBoolean(PREF_HOLD, true);
        globalReset = p.getBoolean(PREF_RESET, false);
        super.loadPreferences();
        if (controlUi != null) {
            controlUi.syncFromConfig();
        }
    }

    @Override
    public void storePreferences() {
        final Preferences p = getChip().getPrefs();
        p.putInt(PREF_ON, contrastThresholdOn);
        p.putInt(PREF_OFF, contrastThresholdOff);
        p.put(PREF_FPS, readoutFps.name());
        p.putBoolean(PREF_HOLD, globalHold);
        p.putBoolean(PREF_RESET, globalReset);
        super.storePreferences();
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public void setHardwareInterface(final BiasgenHardwareInterface hardwareInterface) {
        super.setHardwareInterface(hardwareInterface);
        if (controlUi != null) {
            controlUi.syncFromConfig();
        }
    }

    @Override
    public JPanel buildControlPanel() {
        return getControlPanel();
    }

    @Override
    public JPanel getControlPanel() {
        if (controlPanel == null) {
            controlUi = new DVXplorerControlPanel(this);
            controlPanel = new JPanel(new BorderLayout());
            controlPanel.add(controlUi, BorderLayout.CENTER);
        }
        return controlPanel;
    }

    private void applyContrastOn() throws HardwareInterfaceException {
        final DVXplorerFX3HardwareInterface fx3 = fx3();
        if (fx3 == null || !fx3.isOpen()) {
            return;
        }
        final short dvs = DVXplorer.DEVICE_DVS;
        if (contrastThresholdOn < 9) {
            fx3.spiConfigSend(dvs, DVXplorer.REGISTER_BIAS_CURRENT_ON, contrastThresholdOn);
            fx3.spiConfigSend(dvs, DVXplorer.REGISTER_BIAS_CURRENT_RANGE_SELECT_LOGSFONREST, 0x04);
        } else {
            fx3.spiConfigSend(dvs, DVXplorer.REGISTER_BIAS_CURRENT_ON, contrastThresholdOn - 9);
            fx3.spiConfigSend(dvs, DVXplorer.REGISTER_BIAS_CURRENT_RANGE_SELECT_LOGSFONREST, 0x06);
        }
    }

    private void applyContrastOff() throws HardwareInterfaceException {
        final DVXplorerFX3HardwareInterface fx3 = fx3();
        if (fx3 == null || !fx3.isOpen()) {
            return;
        }
        final short dvs = DVXplorer.DEVICE_DVS;
        if (contrastThresholdOff < 9) {
            fx3.spiConfigSend(dvs, DVXplorer.REGISTER_BIAS_CURRENT_OFF, 8 - contrastThresholdOff);
            fx3.spiConfigSend(dvs, DVXplorer.REGISTER_BIAS_CURRENT_LEVEL_SFOFF, 0x7F);
        } else {
            fx3.spiConfigSend(dvs, DVXplorer.REGISTER_BIAS_CURRENT_OFF, 8 - (contrastThresholdOff - 9));
            fx3.spiConfigSend(dvs, DVXplorer.REGISTER_BIAS_CURRENT_LEVEL_SFOFF, 0x7D);
        }
    }

    private void applyGlobalHoldReset() throws HardwareInterfaceException {
        final DVXplorerFX3HardwareInterface fx3 = fx3();
        if (fx3 == null || !fx3.isOpen()) {
            return;
        }
        int reg = fx3.spiConfigReceive(DVXplorer.DEVICE_DVS, DVXplorer.REGISTER_DIGITAL_MODE_CONTROL);
        if (globalHold) {
            reg |= 0x01;
        } else {
            reg &= ~0x01;
        }
        if (globalReset) {
            reg |= 0x02;
        } else {
            reg &= ~0x02;
        }
        fx3.spiConfigSend(DVXplorer.DEVICE_DVS, DVXplorer.REGISTER_DIGITAL_MODE_CONTROL, reg & 0xFF);
    }

    private void applyReadoutFps() throws HardwareInterfaceException {
        final DVXplorerFX3HardwareInterface fx3 = fx3();
        if (fx3 == null || !fx3.isOpen()) {
            return;
        }
        final int clk = SYSTEM_CLOCK_FREQUENCY;
        final short dvs = DVXplorer.DEVICE_DVS;
        fx3.spiConfigSend(dvs, DVXplorer.REGISTER_DIGITAL_RESTART, 0);

        switch (readoutFps) {
            case CONSTANT_100:
            case CONSTANT_200:
            case CONSTANT_500:
            case CONSTANT_1000:
                writeU16(fx3, DVXplorer.REGISTER_TIMING_READ_TIME_INTERVAL, 900 * clk);
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_DIGITAL_FIXED_READ_TIME, 1);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_NEXT_SELX_START, 15);
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_TIMING_MAX_EVENT_NUM, 10);
                if (readoutFps == ReadoutFPS.CONSTANT_100) {
                    writeGhCount(fx3, 9, 100);
                } else if (readoutFps == ReadoutFPS.CONSTANT_200) {
                    writeGhCount(fx3, 4, 100);
                } else if (readoutFps == ReadoutFPS.CONSTANT_500) {
                    writeGhCount(fx3, 1, 100);
                } else {
                    writeGhCount(fx3, 0, 100);
                }
                break;
            case CONSTANT_LOSSY_2000:
            case CONSTANT_LOSSY_5000:
            case CONSTANT_LOSSY_10000:
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_DIGITAL_FIXED_READ_TIME, 1);
                writeGhCount(fx3, 0, 1);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_NEXT_SELX_START, 5);
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_TIMING_MAX_EVENT_NUM, 0);
                if (readoutFps == ReadoutFPS.CONSTANT_LOSSY_2000) {
                    writeU16(fx3, DVXplorer.REGISTER_TIMING_READ_TIME_INTERVAL, 499 * clk);
                } else if (readoutFps == ReadoutFPS.CONSTANT_LOSSY_5000) {
                    writeU16(fx3, DVXplorer.REGISTER_TIMING_READ_TIME_INTERVAL, 199 * clk);
                } else {
                    writeU16(fx3, DVXplorer.REGISTER_TIMING_READ_TIME_INTERVAL, 99 * clk);
                }
                break;
            case VARIABLE_2000:
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_DIGITAL_FIXED_READ_TIME, 0);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_READ_TIME_INTERVAL, 900 * clk);
                writeGhCount(fx3, 0, 307);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_NEXT_SELX_START, 15);
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_TIMING_MAX_EVENT_NUM, 10);
                break;
            case VARIABLE_5000:
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_DIGITAL_FIXED_READ_TIME, 0);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_READ_TIME_INTERVAL, 900 * clk);
                writeGhCount(fx3, 0, 7);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_NEXT_SELX_START, 15);
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_TIMING_MAX_EVENT_NUM, 10);
                break;
            case VARIABLE_10000:
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_DIGITAL_FIXED_READ_TIME, 0);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_READ_TIME_INTERVAL, 900 * clk);
                writeGhCount(fx3, 0, 10);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_NEXT_SELX_START, 7);
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_TIMING_MAX_EVENT_NUM, 2);
                break;
            case VARIABLE_15000:
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_DIGITAL_FIXED_READ_TIME, 0);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_READ_TIME_INTERVAL, 900 * clk);
                writeGhCount(fx3, 0, 1);
                writeU16(fx3, DVXplorer.REGISTER_TIMING_NEXT_SELX_START, 5);
                fx3.spiConfigSend(dvs, DVXplorer.REGISTER_TIMING_MAX_EVENT_NUM, 0);
                break;
            default:
                break;
        }

        fx3.spiConfigSend(dvs, DVXplorer.REGISTER_DIGITAL_RESTART, 1);
    }

    private void writeU16(DVXplorerFX3HardwareInterface fx3, short base, int value)
            throws HardwareInterfaceException {
        fx3.spiConfigSend(DVXplorer.DEVICE_DVS, base, (value >> 8) & 0xFF);
        fx3.spiConfigSend(DVXplorer.DEVICE_DVS, (short) (base + 1), value & 0xFF);
    }

    private void writeGhCount(DVXplorerFX3HardwareInterface fx3, int msec, int usec)
            throws HardwareInterfaceException {
        fx3.spiConfigSend(DVXplorer.DEVICE_DVS, DVXplorer.REGISTER_TIMING_GH_COUNT, msec);
        fx3.spiConfigSend(DVXplorer.DEVICE_DVS, (short) (DVXplorer.REGISTER_TIMING_GH_COUNT + 1), (usec >> 8) & 0xFF);
        fx3.spiConfigSend(DVXplorer.DEVICE_DVS, (short) (DVXplorer.REGISTER_TIMING_GH_COUNT + 2), usec & 0xFF);
    }

    private static int clampContrast(int value) {
        if (value < CONTRAST_MIN) {
            return CONTRAST_MIN;
        }
        if (value > CONTRAST_MAX) {
            return CONTRAST_MAX;
        }
        return value;
    }

    private static ReadoutFPS parseFps(String name) {
        try {
            return ReadoutFPS.valueOf(name);
        } catch (RuntimeException e) {
            return ReadoutFPS.VARIABLE_5000;
        }
    }

    private DVXplorerFX3HardwareInterface fx3() {
        if (getHardwareInterface() instanceof DVXplorerFX3HardwareInterface d) {
            return d;
        }
        if (getChip() != null && getChip().getHardwareInterface() instanceof DVXplorerFX3HardwareInterface d) {
            return d;
        }
        return null;
    }

    private void markFileModified() {
        if (getChip() instanceof net.sf.jaer.chip.AEChip aeChip
                && aeChip.getAeViewer() != null
                && aeChip.getAeViewer().getBiasgenFrame() != null) {
            aeChip.getAeViewer().getBiasgenFrame().setFileModified(true);
        }
    }
}
