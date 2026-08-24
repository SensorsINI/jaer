package prophesee.chip;

import java.awt.Component;
import java.beans.PropertyChangeEvent;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.PotTweaker;
import net.sf.jaer.chip.AEChip;
import prophesee.usb.PropheseeBiases;
import ch.unizh.ini.jaer.chip.retina.DVSUserControlPanel;

/**
 * User-friendly IMX636 bias tweaks around last loaded/saved preference values.
 *
 * @see https://docs.prophesee.ai/stable/hw/manuals/biases.html
 */
public class PropheseeUserControlPanel extends DVSUserControlPanel {

    private static final String HELP_HTML = "<html>This panel tweaks bias values around the nominal ones loaded from "
            + "preferences/XML. <b>Changes are not permanent</b> until settings are saved. "
            + "On save (or restart after save), these become the new nominal (slider center).<br>"
            + "Values are abstract digital offsets — not calibrated threshold % or filter Hz. "
            + "Use <b>Undo/Redo</b> in the Biases toolbar; <b>File→Revert</b> restores the last save.";

    private final PropheseeConfig config;
    private final PotTweaker highpassTweaker = new PotTweaker();
    private final JLabel thresholdValueLabel = new JLabel();
    private final JLabel onOffValueLabel = new JLabel();
    private final JLabel bandwidthValueLabel = new JLabel();
    private final JLabel highpassValueLabel = new JLabel();
    private final JLabel refractoryValueLabel = new JLabel();

    public PropheseeUserControlPanel(PropheseeConfig config) {
        super(config.getChip() instanceof AEChip ae ? ae : null, config, false);
        this.config = config;
        finishInit();
        config.getSupport().addPropertyChangeListener(highpassTweaker);
        syncFromConfig();
    }

    @Override
    protected String helpHtml() {
        return HELP_HTML;
    }

    @Override
    protected void configureTweakers() {
        configurePotTweaker(thresholdTweaker, "Brightness change threshold", "Lower / more events",
                "Higher / less events",
                "Raises both ON and OFF contrast thresholds (IMX636: increase diff_on and diff_off).");
        thresholdTweaker.setTooltip("<html>Right: higher |Θ|, fewer events.<br>"
                + "Unlike DAVIS analog pots, both <i>diff_on</i> and <i>diff_off</i> increase together.");
        configurePotTweaker(onOffBalanceTweaker, "ON/OFF balance", "More OFF", "More ON",
                "Shifts ON vs OFF threshold: right lowers ON threshold and raises OFF threshold.");
        configurePotTweaker(bandwidthTweaker, "Pixel low-pass", "Slower", "Faster",
                "Pixel low-pass (bias_fo): faster = wider bandwidth, shorter τ_LP, more flicker/noise.");
        configurePotTweaker(maxFiringRateTweaker, "Maximum firing rate", "Slower", "Faster",
                "Pixel refractory (bias_refr): right shortens refractory / raises max rate.");
        configurePotTweaker(highpassTweaker, "Pixel high-pass", "Pass slow changes", "Reject slow / background",
                "Pixel high-pass (bias_hpf): right rejects more slow/DC change (shorter τ_HP).");
        highpassTweaker.addChangeListener(e -> {
            if (!updatingFromConfig) {
                config.setHighpassTweak(highpassTweaker.getValue());
                updateChipSpecificLabels();
            }
        });
    }

    @Override
    protected void addExtraControls(JPanel extra) {
        extra.add(Box.createVerticalStrut(6));
        extra.add(highpassTweaker);
        extra.add(highpassValueLabel);
        extra.add(thresholdValueLabel);
        extra.add(onOffValueLabel);
        extra.add(bandwidthValueLabel);
        extra.add(refractoryValueLabel);
        for (JLabel lab : new JLabel[] {thresholdValueLabel, onOffValueLabel, bandwidthValueLabel,
                highpassValueLabel, refractoryValueLabel}) {
            lab.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        stretchHorizontal(highpassTweaker);
    }

    void syncFromConfig() {
        syncFromTweaks();
        updatingFromConfig = true;
        try {
            highpassTweaker.setValue(config.getHighpassTweak());
        } finally {
            updatingFromConfig = false;
        }
        updateChipSpecificLabels();
    }

    @Override
    protected void updateChipSpecificLabels() {
        if (config == null) {
            return;
        }
        final PropheseeBiases saved = config.getSavedBiases();
        thresholdValueLabel.setText(String.format("diff_on %s    diff_off %s",
                hexOffset(config.getDiffOn(), saved.diffOn),
                hexOffset(config.getDiffOff(), saved.diffOff)));
        onOffValueLabel.setText(String.format("balance  diff_on %s    diff_off %s",
                hexOffset(config.getDiffOn(), saved.diffOn),
                hexOffset(config.getDiffOff(), saved.diffOff)));
        bandwidthValueLabel.setText("fo " + hexOffset(config.getFo(), saved.fo));
        refractoryValueLabel.setText("refr " + hexOffset(config.getRefr(), saved.refr));
        highpassValueLabel.setText("hpf " + hexOffset(config.getHpf(), saved.hpf));
    }

    private static String hexOffset(int current, int saved) {
        return String.format("0x%02X (%+d from saved)", current, current - saved);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        String name = evt.getPropertyName();
        if (Biasgen.PROPERTY_CHANGE_PREFERENCES_LOADED.equals(name)
                || Biasgen.PROPERTY_CHANGE_PREFERENCES_STORED.equals(name)) {
            syncFromConfig();
            return;
        }
        if (PropheseeConfig.PROPERTY_HIGHPASS_TWEAK.equals(name) && evt.getNewValue() instanceof Float v) {
            updatingFromConfig = true;
            highpassTweaker.setValue(v);
            updatingFromConfig = false;
            updateChipSpecificLabels();
        } else if (PropheseeConfig.PROPERTY_DIFF_ON.equals(name)
                || PropheseeConfig.PROPERTY_DIFF_OFF.equals(name)
                || PropheseeConfig.PROPERTY_FO.equals(name)
                || PropheseeConfig.PROPERTY_HPF.equals(name)
                || PropheseeConfig.PROPERTY_REFR.equals(name)) {
            updateChipSpecificLabels();
        }
    }
}
