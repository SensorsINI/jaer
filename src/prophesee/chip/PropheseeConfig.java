package prophesee.chip;

import java.awt.BorderLayout;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.VendorPrefsMigration;
import prophesee.usb.PropheseeBiases;
import prophesee.usb.PropheseeHardwareInterface;
import ch.unizh.ini.jaer.chip.retina.DVSAutoControllerPanel;
import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import ch.unizh.ini.jaer.chip.retina.DVSUserControlPanel;
import ch.unizh.ini.jaer.chip.retina.DvsDisplayConfigInterface;

/**
 * IMX636 bias control for Prophesee EVK4 HD.
 * Bias values live in the chip Preferences node and can be exported/imported as XML
 * via the Biases frame File menu (same mechanism as DVS128).
 * User-friendly tweaks are additive offsets around the last loaded/saved snapshot.
 *
 * @see https://www.prophesee.ai/
 * @see <a href="https://docs.prophesee.ai/stable/hw/manuals/biases.html">Metavision bias manual</a>
 */
public class PropheseeConfig extends Biasgen implements ChipControlPanel, DvsDisplayConfigInterface, DVSTweaks {

    private static final Logger log = Logger.getLogger(PropheseeConfig.class.getName());

    public static final String PROPERTY_DIFF = "propheseeDiff";
    public static final String PROPERTY_DIFF_ON = "propheseeDiffOn";
    public static final String PROPERTY_DIFF_OFF = "propheseeDiffOff";
    public static final String PROPERTY_PR = "propheseePr";
    public static final String PROPERTY_FO = "propheseeFo";
    public static final String PROPERTY_REFR = "propheseeRefr";
    public static final String PROPERTY_HPF = "propheseeHpf";

    /** Fired with new tweak value in −1…1. Names match {@link DVSTweaks}. */
    public static final String PROPERTY_THRESHOLD_TWEAK = DVSTweaks.THRESHOLD;
    public static final String PROPERTY_ON_OFF_BALANCE_TWEAK = DVSTweaks.ON_OFF_BALANCE;
    public static final String PROPERTY_BANDWIDTH_TWEAK = DVSTweaks.BANDWIDTH;
    public static final String PROPERTY_MAX_FIRING_RATE_TWEAK = DVSTweaks.MAX_FIRING_RATE;
    public static final String PROPERTY_HIGHPASS_TWEAK = "highpass";

    /**
     * IMX636 Metavision offset ranges (positive / negative magnitudes from factory).
     * @see <a href="https://docs.prophesee.ai/stable/hw/manuals/biases.html">Bias ranges</a>
     */
    public static final int DIFF_ON_POS = 140;
    public static final int DIFF_ON_NEG = 85;
    public static final int DIFF_OFF_POS = 190;
    public static final int DIFF_OFF_NEG = 35;
    public static final int FO_POS = 55;
    public static final int FO_NEG = 35;
    public static final int HPF_POS = 120;
    public static final int HPF_NEG = 0;
    public static final int REFR_POS = 80;
    public static final int REFR_NEG = 20;

    private static final String PREFS_BIAS = "PropheseeConfig.bias.";

    private JPanel controlPanel;
    private PropheseeControlPanel rawControlPanel;
    private PropheseeUserControlPanel userControlPanel;
    private PropheseeBiases biases = new PropheseeBiases();
    private PropheseeBiases chipBiases = new PropheseeBiases();
    /** Last loaded or saved bias snapshot; Revert restores this without re-reading prefs. Also the tweak baseline. */
    private PropheseeBiases savedBiases;

    private float thresholdTweak;
    private float onOffBalanceTweak;
    private float bandwidthTweak;
    private float maxFiringRateTweak;
    private float highpassTweak;

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

    /** Last loaded/saved snapshot (tweak center). Never null after first use. */
    public PropheseeBiases getSavedBiases() {
        return tweakBaseline().copy();
    }

    public float getThresholdTweak() {
        return thresholdTweak;
    }

    public float getOnOffBalanceTweak() {
        return onOffBalanceTweak;
    }

    public float getBandwidthTweak() {
        return bandwidthTweak;
    }

    public float getMaxFiringRateTweak() {
        return maxFiringRateTweak;
    }

    public float getHighpassTweak() {
        return highpassTweak;
    }

    @Override
    public float getPhotoreceptorSourceFollowerBandwidthHz() {
        return Float.NaN;
    }

    @Override
    public float getOnThresholdLogE() {
        return Float.NaN;
    }

    @Override
    public float getOffThresholdLogE() {
        return Float.NaN;
    }

    @Override
    public float getRefractoryPeriodS() {
        return Float.NaN;
    }

    /**
     * Tweaks ON and OFF contrast thresholds together. Larger is higher threshold (fewer events).
     * On IMX636 both {@code diffOn} and {@code diffOff} increase to raise threshold.
     *
     * @param val −1…1, 0 = last saved/loaded values
     */
    public void setThresholdTweak(float val) {
        val = clampTweak(val);
        if (thresholdTweak == val) {
            return;
        }
        final float old = thresholdTweak;
        thresholdTweak = val;
        if (!applyThresholdBalanceFromTweaks()) {
            thresholdTweak = old;
            return;
        }
        support.firePropertyChange(PROPERTY_THRESHOLD_TWEAK, old, val);
    }

    /**
     * Tweaks ON vs OFF balance. Larger is more ON events (lower ON threshold, higher OFF threshold).
     *
     * @param val −1…1, 0 = last saved/loaded values
     */
    public void setOnOffBalanceTweak(float val) {
        val = clampTweak(val);
        if (onOffBalanceTweak == val) {
            return;
        }
        final float old = onOffBalanceTweak;
        onOffBalanceTweak = val;
        if (!applyThresholdBalanceFromTweaks()) {
            onOffBalanceTweak = old;
            return;
        }
        support.firePropertyChange(PROPERTY_ON_OFF_BALANCE_TWEAK, old, val);
    }

    /**
     * Tweaks pixel low-pass ({@code bias_fo}). Larger is higher bandwidth / shorter τ_LP.
     *
     * @param val −1…1, 0 = last saved/loaded values
     */
    public void setBandwidthTweak(float val) {
        val = clampTweak(val);
        if (bandwidthTweak == val) {
            return;
        }
        final float old = bandwidthTweak;
        bandwidthTweak = val;
        if (!applyFoFromTweak()) {
            bandwidthTweak = old;
            return;
        }
        support.firePropertyChange(PROPERTY_BANDWIDTH_TWEAK, old, val);
    }

    /**
     * Tweaks refractory ({@code bias_refr}). Larger is higher max firing rate (shorter refractory).
     *
     * @param val −1…1, 0 = last saved/loaded values
     */
    @Override
    public void setMaxFiringRateTweak(float val) {
        val = clampTweak(val);
        if (maxFiringRateTweak == val) {
            return;
        }
        final float old = maxFiringRateTweak;
        maxFiringRateTweak = val;
        if (!applyRefrFromTweak()) {
            maxFiringRateTweak = old;
            return;
        }
        support.firePropertyChange(PROPERTY_MAX_FIRING_RATE_TWEAK, old, val);
    }

    /**
     * Tweaks pixel high-pass ({@code bias_hpf}). Larger rejects more slow/DC change.
     * At factory {@code hpf=0} the negative side is a no-op (Metavision range is 0…+120).
     *
     * @param val −1…1, 0 = last saved/loaded values
     */
    public void setHighpassTweak(float val) {
        val = clampTweak(val);
        if (highpassTweak == val) {
            return;
        }
        final float old = highpassTweak;
        highpassTweak = val;
        if (!applyHpfFromTweak()) {
            highpassTweak = old;
            return;
        }
        support.firePropertyChange(PROPERTY_HIGHPASS_TWEAK, old, val);
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

    private PropheseeBiases tweakBaseline() {
        if (savedBiases == null) {
            savedBiases = biases.copy();
        }
        return savedBiases;
    }

    private void resetTweaks() {
        thresholdTweak = 0f;
        onOffBalanceTweak = 0f;
        bandwidthTweak = 0f;
        maxFiringRateTweak = 0f;
        highpassTweak = 0f;
    }

    private void updateSavedBiases() {
        savedBiases = biases.copy();
        resetTweaks();
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
        resetTweaks();
        refreshControlPanels();
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

    private boolean applyThresholdBalanceFromTweaks() {
        final PropheseeBiases base = tweakBaseline();
        final int thrOn = tweakOffset(thresholdTweak, DIFF_ON_POS, DIFF_ON_NEG);
        final int balOn = tweakOffset(onOffBalanceTweak, DIFF_ON_POS, DIFF_ON_NEG);
        final int thrOff = tweakOffset(thresholdTweak, DIFF_OFF_POS, DIFF_OFF_NEG);
        final int balOff = tweakOffset(onOffBalanceTweak, DIFF_OFF_POS, DIFF_OFF_NEG);
        final int newOn = clampToFactoryRange(
                base.diffOn + thrOn - balOn, chipBiases.diffOn, DIFF_ON_POS, DIFF_ON_NEG, base.diffOn);
        final int newOff = clampToFactoryRange(
                base.diffOff + thrOff + balOff, chipBiases.diffOff, DIFF_OFF_POS, DIFF_OFF_NEG, base.diffOff);
        if (biases.diffOn == newOn && biases.diffOff == newOff) {
            return true;
        }
        final int oldOn = biases.diffOn;
        final int oldOff = biases.diffOff;
        biases.diffOn = newOn;
        biases.diffOff = newOff;
        try {
            applyToHardware();
            markFileModified();
            support.firePropertyChange(PROPERTY_DIFF_ON, oldOn, newOn);
            support.firePropertyChange(PROPERTY_DIFF_OFF, oldOff, newOff);
            return true;
        } catch (HardwareInterfaceException e) {
            biases.diffOn = oldOn;
            biases.diffOff = oldOff;
            log.warning(e.getMessage());
            return false;
        }
    }

    private boolean applyFoFromTweak() {
        final PropheseeBiases base = tweakBaseline();
        final int newFo = clampToFactoryRange(
                base.fo + tweakOffset(bandwidthTweak, FO_POS, FO_NEG),
                chipBiases.fo, FO_POS, FO_NEG, base.fo);
        if (biases.fo == newFo) {
            return true;
        }
        final int old = biases.fo;
        biases.fo = newFo;
        try {
            applyToHardware();
            markFileModified();
            support.firePropertyChange(PROPERTY_FO, old, newFo);
            return true;
        } catch (HardwareInterfaceException e) {
            biases.fo = old;
            log.warning(e.getMessage());
            return false;
        }
    }

    private boolean applyRefrFromTweak() {
        final PropheseeBiases base = tweakBaseline();
        final int newRefr = clampToFactoryRange(
                base.refr + tweakOffset(maxFiringRateTweak, REFR_POS, REFR_NEG),
                chipBiases.refr, REFR_POS, REFR_NEG, base.refr);
        if (biases.refr == newRefr) {
            return true;
        }
        final int old = biases.refr;
        biases.refr = newRefr;
        try {
            applyToHardware();
            markFileModified();
            support.firePropertyChange(PROPERTY_REFR, old, newRefr);
            return true;
        } catch (HardwareInterfaceException e) {
            biases.refr = old;
            log.warning(e.getMessage());
            return false;
        }
    }

    private boolean applyHpfFromTweak() {
        final PropheseeBiases base = tweakBaseline();
        final int newHpf = clampToFactoryRange(
                base.hpf + tweakOffset(highpassTweak, HPF_POS, HPF_NEG),
                chipBiases.hpf, HPF_POS, HPF_NEG, base.hpf);
        if (biases.hpf == newHpf) {
            return true;
        }
        final int old = biases.hpf;
        biases.hpf = newHpf;
        try {
            applyToHardware();
            markFileModified();
            support.firePropertyChange(PROPERTY_HPF, old, newHpf);
            return true;
        } catch (HardwareInterfaceException e) {
            biases.hpf = old;
            log.warning(e.getMessage());
            return false;
        }
    }

    /**
     * Additive offset for a −1…1 tweaker. {@code negMax} is the magnitude of the negative range.
     */
    static int tweakOffset(float tweak, int posMax, int negMax) {
        if (tweak >= 0f) {
            return Math.round(tweak * posMax);
        }
        return Math.round(tweak * negMax);
    }

    /**
     * Clamp to factory±Metavision range, expanded to include {@code baseline} so tweak 0 is identity.
     */
    static int clampToFactoryRange(int value, int factory, int posMax, int negMax, int baseline) {
        int lo = factory - negMax;
        int hi = factory + posMax;
        if (baseline < lo) {
            lo = baseline;
        }
        if (baseline > hi) {
            hi = baseline;
        }
        lo = Math.max(0, lo);
        hi = Math.min(0xFF, hi);
        if (value < lo) {
            return lo;
        }
        if (value > hi) {
            return hi;
        }
        return value;
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

        try {
            applyToHardware();
        } catch (HardwareInterfaceException e) {
            log.warning("Could not send reverted Prophesee biases to hardware: " + e.getMessage());
        }
        updateSavedBiases();
        refreshControlPanels();
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
        refreshControlPanels();
    }

    @Override
    public JPanel buildControlPanel() {
        return getControlPanel();
    }

    PropheseeControlPanel getPropheseeControlPanel() {
        if (rawControlPanel == null) {
            getControlPanel();
        }
        return rawControlPanel;
    }

    PropheseeUserControlPanel getPropheseeUserControlPanel() {
        if (userControlPanel == null) {
            getControlPanel();
        }
        return userControlPanel;
    }

    private void refreshControlPanels() {
        if (rawControlPanel != null) {
            rawControlPanel.refreshFromBiases();
        }
        if (userControlPanel != null) {
            userControlPanel.syncFromConfig();
        }
    }

    @Override
    public JPanel getControlPanel() {
        if (controlPanel == null) {
            userControlPanel = new PropheseeUserControlPanel(this);
            rawControlPanel = new PropheseeControlPanel(this);
            final JTabbedPane tabs = new JTabbedPane();
            tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            tabs.addTab("<html><strong><font color=\"red\">User-Friendly Controls", userControlPanel);
            DVSAutoControllerPanel.addTab(tabs, getChip() instanceof AEChip ae ? ae : null);
            tabs.addTab("Raw biases", rawControlPanel);
            DVSUserControlPanel.capTabbedPanePreferredWidth(tabs);
            controlPanel = new JPanel(new BorderLayout());
            controlPanel.add(tabs, BorderLayout.CENTER);
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

    @Override
    public void importPreferences(InputStream is) throws IOException, InvalidPreferencesFormatException,
            HardwareInterfaceException {
        super.importPreferences(VendorPrefsMigration.rewriteLegacyPreferencesXml(is));
    }
}
