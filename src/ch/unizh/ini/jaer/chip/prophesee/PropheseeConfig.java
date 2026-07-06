package ch.unizh.ini.jaer.chip.prophesee;

import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JPanel;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.prophesee.PropheseeBiases;
import net.sf.jaer.hardwareinterface.usb.prophesee.PropheseeHardwareInterface;
import ch.unizh.ini.jaer.chip.retina.DvsDisplayConfigInterface;

/**
 * IMX636 bias control for Prophesee EVK4 HD.
 * Bias values live in the chip Preferences node and can be exported/imported as XML
 * via the Biases frame File menu (same mechanism as DVS128).
 */
public class PropheseeConfig extends Biasgen implements ChipControlPanel, DvsDisplayConfigInterface {

    private static final Logger log = Logger.getLogger(PropheseeConfig.class.getName());

    public static final String PROPERTY_DIFF = "propheseeDiff";
    public static final String PROPERTY_DIFF_ON = "propheseeDiffOn";
    public static final String PROPERTY_DIFF_OFF = "propheseeDiffOff";
    public static final String PROPERTY_PR = "propheseePr";
    public static final String PROPERTY_FO = "propheseeFo";
    public static final String PROPERTY_REFR = "propheseeRefr";
    public static final String PROPERTY_HPF = "propheseeHpf";

    private static final String PREFS_BIAS = "PropheseeConfig.bias.";

    private PropheseeControlPanel controlPanel;
    private PropheseeBiases biases = new PropheseeBiases();
    private PropheseeBiases chipBiases = new PropheseeBiases();
    /** Last loaded or saved bias snapshot; Revert restores this without re-reading prefs. */
    private PropheseeBiases savedBiases;

    private boolean displayEvents = true;
    private boolean displayFrames = false;
    private boolean useAutoContrast = false;
    private float contrast = 1.0f;
    private float brightness = 0.0f;
    private float gamma = 1.0f;

    public PropheseeConfig(Chip chip) {
        super(chip);
        setName("PropheseeConfig");
        setPotArray(new PotArray(this));
    }

    public PropheseeBiases getBiases() {
        return biases.copy();
    }

    public PropheseeBiases getChipBiases() {
        return chipBiases.copy();
    }

    private Preferences chipPrefs() {
        return getChip().getPrefs();
    }

    private void applyToHardware() throws HardwareInterfaceException {
        if (!(getHardwareInterface() instanceof PropheseeHardwareInterface hw)) {
            return;
        }
        if (!hw.isOpen()) {
            hw.open();
        }
        hw.setBiases(biases);
    }

    private void commitBiasChange(Runnable revert, Runnable afterSuccess) {
        try {
            applyToHardware();
            markFileModified();
            if (afterSuccess != null) {
                afterSuccess.run();
            }
        } catch (HardwareInterfaceException e) {
            revert.run();
            log.warning(e.getMessage());
        }
    }

    private void updateSavedBiases() {
        savedBiases = biases.copy();
    }

    /**
     * Revert live bias values to the last loaded or saved snapshot (not current slider prefs).
     */
    public void revertToSavedBiases() {
        if (savedBiases == null) {
            loadPreferences();
            return;
        }
        biases = savedBiases.copy();
        if (controlPanel != null) {
            controlPanel.refreshFromBiases();
        }
        try {
            applyToHardware();
        } catch (HardwareInterfaceException e) {
            log.warning("Could not send reverted Prophesee biases to hardware: " + e.getMessage());
        }
        support.firePropertyChange(PROPERTY_CHANGE_PREFERENCES_LOADED, null, null);
    }

    private void markFileModified() {
        if (getChip() instanceof AEChip aeChip && aeChip.getAeViewer() != null
                && aeChip.getAeViewer().getBiasgenFrame() != null) {
            aeChip.getAeViewer().getBiasgenFrame().setFileModified(true);
        }
    }

    public void setDiff(int value) {
        value = clampBias(value);
        if (biases.diff == value) {
            return;
        }
        final int old = biases.diff;
        biases.diff = value;
        final int saved = old;
        final int newValue = value;
        commitBiasChange(
                () -> biases.diff = saved,
                () -> support.firePropertyChange(PROPERTY_DIFF, old, newValue));
    }

    public void setDiffOn(int value) {
        value = clampBias(value);
        if (biases.diffOn == value) {
            return;
        }
        final int old = biases.diffOn;
        biases.diffOn = value;
        final int saved = old;
        final int newValue = value;
        commitBiasChange(
                () -> biases.diffOn = saved,
                () -> support.firePropertyChange(PROPERTY_DIFF_ON, old, newValue));
    }

    public void setDiffOff(int value) {
        value = clampBias(value);
        if (biases.diffOff == value) {
            return;
        }
        final int old = biases.diffOff;
        biases.diffOff = value;
        final int saved = old;
        final int newValue = value;
        commitBiasChange(
                () -> biases.diffOff = saved,
                () -> support.firePropertyChange(PROPERTY_DIFF_OFF, old, newValue));
    }

    public void setPr(int value) {
        value = clampBias(value);
        if (biases.pr == value) {
            return;
        }
        final int old = biases.pr;
        biases.pr = value;
        final int saved = old;
        final int newValue = value;
        commitBiasChange(
                () -> biases.pr = saved,
                () -> support.firePropertyChange(PROPERTY_PR, old, newValue));
    }

    public void setFo(int value) {
        value = clampBias(value);
        if (biases.fo == value) {
            return;
        }
        final int old = biases.fo;
        biases.fo = value;
        final int saved = old;
        final int newValue = value;
        commitBiasChange(
                () -> biases.fo = saved,
                () -> support.firePropertyChange(PROPERTY_FO, old, newValue));
    }

    public void setHpf(int value) {
        value = clampBias(value);
        if (biases.hpf == value) {
            return;
        }
        final int old = biases.hpf;
        biases.hpf = value;
        final int saved = old;
        final int newValue = value;
        commitBiasChange(
                () -> biases.hpf = saved,
                () -> support.firePropertyChange(PROPERTY_HPF, old, newValue));
    }

    public void setRefr(int value) {
        value = clampBias(value);
        if (biases.refr == value) {
            return;
        }
        final int old = biases.refr;
        biases.refr = value;
        final int saved = old;
        final int newValue = value;
        commitBiasChange(
                () -> biases.refr = saved,
                () -> support.firePropertyChange(PROPERTY_REFR, old, newValue));
    }

    public int getDiff() {
        return biases.diff;
    }

    public int getDiffOn() {
        return biases.diffOn;
    }

    public int getDiffOff() {
        return biases.diffOff;
    }

    public int getPr() {
        return biases.pr;
    }

    public int getFo() {
        return biases.fo;
    }

    public int getRefr() {
        return biases.refr;
    }

    public int getHpf() {
        return biases.hpf;
    }

    private static int clampBias(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 0xFF) {
            return 0xFF;
        }
        return value;
    }

    private static String prefsKey(String name) {
        return PREFS_BIAS + name;
    }

    @Override
    public void loadPreferences() {
        if (biases == null) {
            biases = new PropheseeBiases();
        }
        final Preferences p = chipPrefs();
        final PropheseeBiases defaults = new PropheseeBiases();
        biases.pr = p.getInt(prefsKey("pr"), defaults.pr);
        biases.fo = p.getInt(prefsKey("fo"), defaults.fo);
        biases.hpf = p.getInt(prefsKey("hpf"), defaults.hpf);
        biases.diffOn = p.getInt(prefsKey("diffOn"), defaults.diffOn);
        biases.diff = p.getInt(prefsKey("diff"), defaults.diff);
        biases.diffOff = p.getInt(prefsKey("diffOff"), defaults.diffOff);
        biases.inv = p.getInt(prefsKey("inv"), defaults.inv);
        biases.refr = p.getInt(prefsKey("refr"), defaults.refr);
        biases.reqpuy = p.getInt(prefsKey("reqpuy"), defaults.reqpuy);
        biases.reqpux = p.getInt(prefsKey("reqpux"), defaults.reqpux);
        biases.sendreqpdy = p.getInt(prefsKey("sendreqpdy"), defaults.sendreqpdy);
        biases.unknown1 = p.getInt(prefsKey("unknown1"), defaults.unknown1);
        biases.unknown2 = p.getInt(prefsKey("unknown2"), defaults.unknown2);

        displayEvents = p.getBoolean("PropheseeConfig.displayEvents", true);
        displayFrames = p.getBoolean("PropheseeConfig.displayFrames", false);
        useAutoContrast = p.getBoolean("PropheseeConfig.useAutoContrast", false);
        contrast = p.getFloat("PropheseeConfig.contrast", 1.0f);
        brightness = p.getFloat("PropheseeConfig.brightness", 0.0f);
        gamma = p.getFloat("PropheseeConfig.gamma", 1.0f);

        if (controlPanel != null) {
            controlPanel.refreshFromBiases();
        }
        try {
            applyToHardware();
        } catch (HardwareInterfaceException e) {
            log.warning("Could not send reverted Prophesee biases to hardware: " + e.getMessage());
        }
        updateSavedBiases();
        support.firePropertyChange(PROPERTY_CHANGE_PREFERENCES_LOADED, null, null);
    }

    @Override
    public void storePreferences() {
        if (biases == null) {
            return;
        }
        putPref(prefsKey("pr"), biases.pr);
        putPref(prefsKey("fo"), biases.fo);
        putPref(prefsKey("hpf"), biases.hpf);
        putPref(prefsKey("diffOn"), biases.diffOn);
        putPref(prefsKey("diff"), biases.diff);
        putPref(prefsKey("diffOff"), biases.diffOff);
        putPref(prefsKey("inv"), biases.inv);
        putPref(prefsKey("refr"), biases.refr);
        putPref(prefsKey("reqpuy"), biases.reqpuy);
        putPref(prefsKey("reqpux"), biases.reqpux);
        putPref(prefsKey("sendreqpdy"), biases.sendreqpdy);
        putPref(prefsKey("unknown1"), biases.unknown1);
        putPref(prefsKey("unknown2"), biases.unknown2);

        putPref("PropheseeConfig.displayEvents", displayEvents);
        putPref("PropheseeConfig.displayFrames", displayFrames);
        putPref("PropheseeConfig.useAutoContrast", useAutoContrast);
        putPref("PropheseeConfig.contrast", contrast);
        putPref("PropheseeConfig.brightness", brightness);
        putPref("PropheseeConfig.gamma", gamma);

        updateSavedBiases();
        support.firePropertyChange(PROPERTY_CHANGE_PREFERENCES_STORED, null, null);
    }

    @Override
    public boolean isInitialized() {
        if (getChip() != null && getChip().isDefaultPreferencesLoadedOnce()) {
            return true;
        }
        return chipPrefs().get(prefsKey("diff"), null) != null;
    }

    @Override
    public void setHardwareInterface(final BiasgenHardwareInterface hardwareInterface) {
        super.setHardwareInterface(hardwareInterface);
        if (hardwareInterface instanceof PropheseeHardwareInterface hw && hw.isOpen()) {
            chipBiases = hw.getChipFirmwareBiases().copy();
        }
        if (controlPanel != null) {
            controlPanel.refreshFromBiases();
        }
    }

    @Override
    public JPanel buildControlPanel() {
        return getControlPanel();
    }

    PropheseeControlPanel getPropheseeControlPanel() {
        if (controlPanel == null) {
            getControlPanel();
        }
        return controlPanel;
    }

    @Override
    public JPanel getControlPanel() {
        if (controlPanel == null) {
            controlPanel = new PropheseeControlPanel(this);
            controlPanel.refreshFromBiases();
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
        putPref("PropheseeConfig.displayFrames", displayFrames);
    }

    @Override
    public boolean isDisplayEvents() {
        return displayEvents;
    }

    @Override
    public void setDisplayEvents(boolean displayEvents) {
        this.displayEvents = displayEvents;
        putPref("PropheseeConfig.displayEvents", displayEvents);
    }

    @Override
    public boolean isUseAutoContrast() {
        return useAutoContrast;
    }

    @Override
    public void setUseAutoContrast(boolean useAutoContrast) {
        this.useAutoContrast = useAutoContrast;
        putPref("PropheseeConfig.useAutoContrast", useAutoContrast);
    }

    @Override
    public float getContrast() {
        return contrast;
    }

    @Override
    public void setContrast(float contrast) {
        this.contrast = contrast;
        putPref("PropheseeConfig.contrast", contrast);
    }

    @Override
    public float getBrightness() {
        return brightness;
    }

    @Override
    public void setBrightness(float brightness) {
        this.brightness = brightness;
        putPref("PropheseeConfig.brightness", brightness);
    }

    @Override
    public float getGamma() {
        return gamma;
    }

    @Override
    public void setGamma(float gamma) {
        this.gamma = gamma;
        putPref("PropheseeConfig.gamma", gamma);
    }
}
