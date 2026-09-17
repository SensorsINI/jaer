package nrv.chip;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;
import nrv.usb.NRVRegisterSetting;
import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import ch.unizh.ini.jaer.chip.retina.DVSUserControlPanel;

/**
 * Simplified NRV controls: shared DVS tweaks plus scan-rate / USB timing.
 *
 * @see https://nrv.kr/
 */
public class NRVUserControlPanel extends DVSUserControlPanel {

    private static final String BIAS_SECTION_TOOLTIP = "<html>Sliders tweak loaded settings around file values.<br>"
            + "<b>Undo/Redo</b> in the Biases toolbar; <b>File→Revert</b> restores the .txt.";
    private static final String TIMING_SECTION_TOOLTIP = "<html>Scan rate morphs DTAG registers (0x321D:321E and block).<br>"
            + "Sub-timestamp (0x32B2) is USB packet cadence within each ms.<br>"
            + "Host-time stretch maps decoded event µs onto System.nanoTime() (CX3 prototype clock).";
    private static final String GLOBAL_SETTING_TOOLTIP = "<html>Global settings for the NRV sensor (0x320C register).";
    private static final int SUB_UNIT_MIN = 1;
    private static final int SUB_UNIT_MAX = 0x7F;

    private final NRVConfig config;
    private final JSlider scanRateSlider = new JSlider(NRVConfig.SCAN_RATE_HZ_MIN, NRVConfig.SCAN_RATE_HZ_MAX, 300);
    private final JSlider timestampSubSlider = new JSlider(SUB_UNIT_MIN, SUB_UNIT_MAX, 0x21);
    private final JCheckBox globalResetCheckBox = new JCheckBox("Enable global reset mode (0x320C[1])");
    private final JCheckBox globalHoldCheckBox = new JCheckBox("Enable global hold mode (0x320C[0])");
    private final JCheckBox hostTimeStretchCheckBox = new JCheckBox(
            "Stretch timestamps to host clock (CX3 prototype)");
    private final JLabel thresholdValueLabel = new JLabel();
    private final JLabel onOffValueLabel = new JLabel();
    private final JLabel kRatioLabel = new JLabel();
    private final JLabel scanRateValueLabel = new JLabel();
    private final JLabel scanRateDetailLabel = new JLabel();
    private final JLabel timestampSubValueLabel = new JLabel();
    private final JLabel subTimestampTimingLabel = new JLabel();

    public NRVUserControlPanel(NRVConfig config) {
        super(config.getChip() instanceof AEChip ae ? ae : null, config, false);
        this.config = config;
        finishInit();
        syncFromConfig();
        applyPreferredSizeFromContent();
    }

    @Override
    protected String helpHtml() {
        return BIAS_SECTION_TOOLTIP;
    }

    @Override
    protected void configureTweakers() {
        configurePotTweaker(thresholdTweaker, "Event threshold", "Lower", "Higher",
                "Right: lower 0x0167 / higher 0x0168 → raise both |Θ|.");
        configurePotTweaker(onOffBalanceTweaker, "ON / OFF balance", "More OFF", "More ON",
                "Right: more ON events (independent of threshold).");
    }

    @Override
    protected void addExtraControls(JPanel extra) {
        for (JLabel lab : new JLabel[] {thresholdValueLabel, onOffValueLabel, kRatioLabel,
                scanRateValueLabel, scanRateDetailLabel, timestampSubValueLabel, subTimestampTimingLabel}) {
            lab.setAlignmentX(Component.LEFT_ALIGNMENT);
            lab.setText(" ");
        }
        extra.add(thresholdValueLabel);
        extra.add(onOffValueLabel);
        extra.add(kRatioLabel);
        extra.add(Box.createVerticalStrut(8));
        extra.add(buildTimingSection());
        extra.add(Box.createVerticalStrut(8));
        extra.add(buildGlobalSettingSection());

        scanRateSlider.addChangeListener(e -> {
            if (!updatingFromConfig) {
                config.setScanRateHz(scanRateSlider.getValue());
                updateChipSpecificLabels();
            }
        });
        timestampSubSlider.addChangeListener(e -> {
            if (!updatingFromConfig) {
                config.setTimestampSubUnit(timestampSubSlider.getValue());
                updateChipSpecificLabels();
            }
        });
        globalResetCheckBox.addActionListener(e -> {
            if (!updatingFromConfig) {
                config.setGlobalResetModeEnabled(globalResetCheckBox.isSelected());
            }
        });
        globalHoldCheckBox.addActionListener(e -> {
            if (!updatingFromConfig) {
                config.setGlobalHoldModeEnabled(globalHoldCheckBox.isSelected());
            }
        });
        hostTimeStretchCheckBox.addActionListener(e -> {
            if (!updatingFromConfig) {
                config.setHostTimeStretchEnabled(hostTimeStretchCheckBox.isSelected());
            }
        });
    }

    private JPanel buildTimingSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Timing / readout"));
        section.setToolTipText(TIMING_SECTION_TOOLTIP);
        configureReadoutSlider(scanRateSlider, 500, 100);
        scanRateSlider.setToolTipText("<html>Nominal sensor scan / frame-end rate (100–2000 Hz).<br>"
                + "Interpolates DTAG Scan Rate registers between factory anchors.");
        configureReadoutSlider(timestampSubSlider, 0x20, 0);
        timestampSubSlider.setToolTipText("<html>Register 0x32B2 — USB packet cadence within each ref ms.<br>"
                + "Auto-updated with scan rate; fine-tune here.");
        section.add(wrapSlider("Scan rate (100–2000 Hz)", scanRateSlider, scanRateValueLabel));
        section.add(scanRateDetailLabel);
        section.add(Box.createVerticalStrut(6));
        section.add(wrapSlider("Sub-timestamp (0x32B2)", timestampSubSlider, timestampSubValueLabel));
        section.add(subTimestampTimingLabel);
        section.add(Box.createVerticalStrut(6));
        hostTimeStretchCheckBox.setToolTipText("<html>USB decode still uses SDK t_us = ref_ms*1000+sub.<br>"
                + "Then each event is rounded onto System.nanoTime() so muxed OpenCV stays in sync.<br>"
                + "Turn off when CX3 firmware matches host time.");
        hostTimeStretchCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(hostTimeStretchCheckBox);
        stretchHorizontal(section);
        return section;
    }

    private JPanel buildGlobalSettingSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Global setting"));
        section.setToolTipText(GLOBAL_SETTING_TOOLTIP);
        globalResetCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        globalHoldCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(globalResetCheckBox);
        section.add(globalHoldCheckBox);
        stretchHorizontal(section);
        return section;
    }

    private static void configureReadoutSlider(JSlider slider, int majorTick, int minorTick) {
        slider.setMajorTickSpacing(majorTick);
        if (minorTick > 0) {
            slider.setMinorTickSpacing(minorTick);
        }
        slider.setPaintTicks(true);
        slider.setPaintLabels(false);
        int sliderH = Math.max(slider.getPreferredSize().height, 36);
        slider.setPreferredSize(new Dimension(120, sliderH));
        slider.setMinimumSize(new Dimension(0, sliderH));
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, sliderH));
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static JPanel wrapSlider(String title, JSlider slider, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(4, 2));
        row.add(new JLabel(title), BorderLayout.NORTH);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.SOUTH);
        stretchHorizontal(row);
        return row;
    }

    void syncFromConfig() {
        syncFromTweaks();
        updatingFromConfig = true;
        try {
            scanRateSlider.setValue(clamp(config.getScanRateHz(),
                    NRVConfig.SCAN_RATE_HZ_MIN, NRVConfig.SCAN_RATE_HZ_MAX, 300));
            timestampSubSlider.setValue(clamp(config.getTimestampSubUnit(), SUB_UNIT_MIN, SUB_UNIT_MAX,
                    config.getBaselineTimestampSub()));
            globalResetCheckBox.setSelected(config.isGlobalResetModeEnabled());
            globalHoldCheckBox.setSelected(config.isGlobalHoldModeEnabled());
            hostTimeStretchCheckBox.setSelected(config.isHostTimeStretchEnabled());
        } finally {
            updatingFromConfig = false;
        }
        updateChipSpecificLabels();
    }

    void updateValueLabels() {
        updateChipSpecificLabels();
    }

    @Override
    protected void updateChipSpecificLabels() {
        if (config == null) {
            return;
        }
        thresholdValueLabel.setText(String.format("0x0167=0x%02X, 0x0168=0x%02X (file: 0x%02X, 0x%02X)",
                config.getRegisterValue(NRVConfig.REG_ON_UNIT),
                config.getRegisterValue(NRVConfig.REG_OFF_UNIT),
                config.getBaselineOnUnit(),
                config.getBaselineOffUnit()));
        onOffValueLabel.setText(String.format("balance → 0x0167=0x%02X, 0x0168=0x%02X",
                config.getRegisterValue(NRVConfig.REG_ON_UNIT),
                config.getRegisterValue(NRVConfig.REG_OFF_UNIT)));
        float kOnRatio = (float) (config.getKOn() / config.getKRef());
        float kOffRatio = (float) (config.getKOff() / config.getKRef());
        kRatioLabel.setText(String.format("K_ON/K_REF = %.4g    K_OFF/K_REF = %.4g", kOnRatio, kOffRatio));

        scanRateValueLabel.setText(config.getScanRateHz() + " Hz");
        int margin = config.getFrameMarginCombined();
        float padUs = config.getFrmMarginPaddingUsForMargin(margin);
        String pad = Float.isNaN(padUs) ? "—" : String.format("%.2f ms", padUs / 1000f);
        scanRateDetailLabel.setText(String.format("FRM_MARGIN 0x%04X (pad %s)  SELX 0x%02X SENSE 0x%02X COL 0x%02X",
                margin, pad,
                config.getRegisterValue(NRVConfig.REG_DTAG_SELX),
                config.getRegisterValue(NRVConfig.REG_DTAG_SENSE),
                config.getRegisterValue(NRVConfig.REG_DTAG_COL_MARGIN)));
        timestampSubValueLabel.setText(String.format("0x32B1:32B2 = 0x%04X (LSB file: 0x%02X)",
                config.getTimestampSubUnitCombined(),
                config.getBaselineTimestampSub()));
        float subUs = config.getSubTimestampIntervalUsForSubUnit(config.getTimestampSubUnit());
        if (Float.isNaN(subUs)) {
            subTimestampTimingLabel.setText("Sub-timestamp interval: —");
        } else {
            subTimestampTimingLabel.setText(String.format("Sub-timestamp interval: %.2f µs", subUs));
        }
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        String name = evt.getPropertyName();
        if (Biasgen.PROPERTY_CHANGE_PREFERENCES_LOADED.equals(name)) {
            syncFromConfig();
            return;
        }
        if (NRVConfig.PROPERTY_SCAN_RATE_HZ.equals(name) && evt.getNewValue() instanceof Integer v) {
            updatingFromConfig = true;
            scanRateSlider.setValue(v);
            updatingFromConfig = false;
            updateChipSpecificLabels();
        } else if (NRVConfig.PROPERTY_TIMESTAMP_SUB.equals(name) && evt.getNewValue() instanceof Integer v) {
            updatingFromConfig = true;
            timestampSubSlider.setValue(v);
            updatingFromConfig = false;
            updateChipSpecificLabels();
        } else if (NRVConfig.PROPERTY_HOST_TIME_STRETCH.equals(name) && evt.getNewValue() instanceof Boolean v) {
            updatingFromConfig = true;
            hostTimeStretchCheckBox.setSelected(v);
            updatingFromConfig = false;
        } else if (NRVConfig.PROPERTY_FRAME_MARGIN.equals(name)
                || NRVConfig.PROPERTY_THRESHOLD.equals(name)
                || NRVConfig.PROPERTY_ON_OFF_BALANCE.equals(name)
                || DVSTweaks.THRESHOLD.equals(name)
                || DVSTweaks.ON_OFF_BALANCE.equals(name)) {
            updateChipSpecificLabels();
        } else if (NRVConfig.PROPERTY_REGISTER_UPDATED.equals(name)
                && evt.getNewValue() instanceof NRVRegisterSetting src) {
            int addr = src.getRegAddr();
            if (addr == NRVConfig.REG_TSTAMP_SUB_UNIT_LSB || addr == NRVConfig.REG_TSTAMP_SUB_UNIT_MSB
                    || addr == NRVConfig.REG_DTAG_FRM_MARGIN_LSB || addr == NRVConfig.REG_DTAG_FRM_MARGIN_MSB
                    || addr == NRVConfig.REG_DTAG_SELX || addr == NRVConfig.REG_DTAG_SENSE
                    || addr == NRVConfig.REG_DTAG_MODE) {
                syncFromConfig();
            } else {
                updateChipSpecificLabels();
            }
        }
    }
}
