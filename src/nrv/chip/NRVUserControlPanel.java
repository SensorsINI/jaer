package nrv.chip;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.PotTweaker;
import nrv.usb.NRVRegisterSetting;

/**
 * Simplified NRV controls: brightness, ON/OFF balance, and timestamp timing.
 *
 * @see https://nrv.kr/
 */
public class NRVUserControlPanel extends JPanel implements PropertyChangeListener {

    private static final int SUB_UNIT_MIN = 1;
    private static final int SUB_UNIT_MAX = 0x7F;
    private static final int FRAME_MARGIN_MIN = 1;
    private static final int FRAME_MARGIN_MAX = 0x0F;

    private final NRVConfig config;
    private final PotTweaker thresholdTweaker = new PotTweaker();
    private final PotTweaker onOffBalanceTweaker = new PotTweaker();
    private final JSlider timestampSubSlider = new JSlider(SUB_UNIT_MIN, SUB_UNIT_MAX, 0x21);
    private final JSlider frameMarginSlider = new JSlider(FRAME_MARGIN_MIN, FRAME_MARGIN_MAX, 0x02);
    private final JLabel thresholdValueLabel = new JLabel();
    private final JLabel onOffValueLabel = new JLabel();
    private final JLabel onThresholdLabel = new JLabel();
    private final JLabel offThresholdLabel = new JLabel();
    private final JLabel timestampSubValueLabel = new JLabel();
    private final JLabel frameMarginValueLabel = new JLabel();
    private final JLabel frameTimingLabel = new JLabel();
    private final JLabel subTimestampTimingLabel = new JLabel();
    private boolean updatingFromConfig;

    public NRVUserControlPanel(NRVConfig config) {
        super(new BorderLayout());
        this.config = config;

        thresholdTweaker.setName("Event threshold");
        thresholdTweaker.setTweakDescription("Adjusts ON and OFF contrast thresholds together");
        thresholdTweaker.setLessDescription("Lower / more events");
        thresholdTweaker.setMoreDescription("Higher / fewer events");
        thresholdTweaker.setToolTipText("<html>Right: lower 0x0167 / higher 0x0168 → raise both |Θ| (like DVS diffOn/diffOff).<br>"
                + "Does not change 0x0166 (K_REF) — edit that in the register table or settings file.<br>"
                + "Changes are sent live; the loaded .txt file is not modified.");

        onOffBalanceTweaker.setName("ON / OFF balance");
        onOffBalanceTweaker.setTweakDescription("Adjusts balance between ON and OFF events");
        onOffBalanceTweaker.setLessDescription("More OFF events");
        onOffBalanceTweaker.setMoreDescription("More ON events");
        onOffBalanceTweaker.setToolTipText("<html>Right: raise both ON/OFF LSBs → lower Θ_ON, higher |Θ_OFF| (more ON / fewer OFF).<br>"
                + "Like raising DVS diff/Id. Independent of the event-threshold slider. K_REF (0x0166) unchanged.<br>"
                + "Changes are sent live; the loaded .txt file is not modified.");

        timestampSubSlider.setMajorTickSpacing(0x20);
        timestampSubSlider.setPaintTicks(true);
        timestampSubSlider.setToolTipText("<html>Register 0x32B2 (TSTAMP_SUB_UNIT).<br>"
                + "Factory presets: 100→0x0B, 300→0x21, 600→0x42, 1000→0x7D.<br>"
                + "Use ~0x21; values below 0x0B often degrade timing.");

        frameMarginSlider.setMajorTickSpacing(4);
        frameMarginSlider.setPaintTicks(true);
        frameMarginSlider.setToolTipText("<html>Register 0x321E (DTAG_FRM_MARGIN).<br>"
                + "Lower → faster sensor frames (~2.5 ms steps at 0x02 vs ~4 ms at 0x0F).<br>"
                + "Increases USB bandwidth.");

        thresholdTweaker.addChangeListener(e -> onThresholdChanged());
        onOffBalanceTweaker.addChangeListener(e -> onOnOffBalanceChanged());
        timestampSubSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                onTimestampSubChanged();
            }
        });
        frameMarginSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                onFrameMarginChanged();
            }
        });

        config.getSupport().addPropertyChangeListener(this);
        config.getSupport().addPropertyChangeListener(thresholdTweaker);
        config.getSupport().addPropertyChangeListener(onOffBalanceTweaker);

        final JLabel help = new JLabel("<html>Sliders tweak values around those loaded from the settings file.<br>"
                + "Use <b>Undo</b> / <b>Redo</b> in the Biases toolbar to revert slider moves (analog section).<br>"
                + "Use <b>File → Revert</b> or reload the .txt to restore all register values from file.");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(6, 6, 6, 6));
        content.add(help);
        content.add(Box.createVerticalStrut(10));
        content.add(wrapSlider(thresholdTweaker, thresholdValueLabel));
        content.add(Box.createVerticalStrut(4));
        content.add(buildThresholdReadoutRow());
        content.add(Box.createVerticalStrut(10));
        content.add(wrapSlider(onOffBalanceTweaker, onOffValueLabel));
        content.add(Box.createVerticalStrut(12));
        content.add(buildTimestampSection());

        final JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        syncFromConfig();
    }

    private JPanel buildTimestampSection() {
        final JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Timestamp timing"));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JLabel hint = new JLabel("<html>Event timestamps update once per sensor frame. "
                + "Tune frame rate (321E) first; keep 32B2 near 0x21.");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(hint);
        section.add(Box.createVerticalStrut(6));
        section.add(wrapRegisterSlider("Sub-timestamp unit (0x32B2)", timestampSubSlider, timestampSubValueLabel));
        section.add(Box.createVerticalStrut(4));
        section.add(wrapTimingLabel(subTimestampTimingLabel));
        section.add(Box.createVerticalStrut(6));
        section.add(wrapRegisterSlider("Frame margin (0x321E)", frameMarginSlider, frameMarginValueLabel));
        section.add(Box.createVerticalStrut(4));
        section.add(wrapTimingLabel(frameTimingLabel));
        return section;
    }

    private JPanel buildThresholdReadoutRow() {
        final JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createTitledBorder("Estimated event thresholds"));
        onThresholdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        offThresholdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(onThresholdLabel);
        row.add(Box.createVerticalStrut(2));
        row.add(offThresholdLabel);
        return row;
    }

    private static JPanel wrapTimingLabel(JLabel label) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label);
        return row;
    }

    private static JPanel wrapSlider(PotTweaker tweaker, JLabel valueLabel) {
        final JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        final JPanel valueRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        valueRow.add(valueLabel);
        row.add(tweaker, BorderLayout.CENTER);
        row.add(valueRow, BorderLayout.SOUTH);
        return row;
    }

    private static JPanel wrapRegisterSlider(String title, JSlider slider, JLabel valueLabel) {
        final JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        final JLabel name = new JLabel(title);
        name.setToolTipText(slider.getToolTipText());
        final JPanel valueRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        valueRow.add(valueLabel);
        row.add(name, BorderLayout.NORTH);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueRow, BorderLayout.SOUTH);
        return row;
    }

    private void onThresholdChanged() {
        if (updatingFromConfig) {
            return;
        }
        config.setThresholdTweak(thresholdTweaker.getValue());
        updateValueLabels();
    }

    private void onOnOffBalanceChanged() {
        if (updatingFromConfig) {
            return;
        }
        config.setOnOffBalanceTweak(onOffBalanceTweaker.getValue());
        updateValueLabels();
    }

    private void onTimestampSubChanged() {
        if (updatingFromConfig) {
            return;
        }
        if (timestampSubSlider.getValueIsAdjusting()) {
            timestampSubValueLabel.setText(String.format("0x32B2 = 0x%02X  (file: 0x%02X)",
                    timestampSubSlider.getValue(), config.getBaselineTimestampSub()));
            updateSubTimestampTimingLabel(timestampSubSlider.getValue());
            return;
        }
        config.setTimestampSubUnit(timestampSubSlider.getValue());
        updateValueLabels();
    }

    private void onFrameMarginChanged() {
        if (updatingFromConfig) {
            return;
        }
        if (frameMarginSlider.getValueIsAdjusting()) {
            frameMarginValueLabel.setText(String.format("0x321E = 0x%02X  (file: 0x%02X)",
                    frameMarginSlider.getValue(), config.getBaselineFrameMargin()));
            final int margin = (config.getRegisterValue(NRVConfig.REG_DTAG_FRM_MARGIN_MSB) << 8)
                    | (frameMarginSlider.getValue() & 0xFF);
            updateFrameTimingLabel(margin);
            return;
        }
        config.setFrameMargin(frameMarginSlider.getValue());
        updateValueLabels();
    }

    void syncFromConfig() {
        updatingFromConfig = true;
        thresholdTweaker.setValue(config.getThresholdTweak());
        onOffBalanceTweaker.setValue(config.getOnOffBalanceTweak());
        syncTimestampSliders();
        updatingFromConfig = false;
        updateValueLabels();
    }

    private void syncTimestampSliders() {
        final int sub = clamp(config.getTimestampSubUnit(), SUB_UNIT_MIN, SUB_UNIT_MAX, config.getBaselineTimestampSub());
        final int margin = clamp(config.getFrameMargin(), FRAME_MARGIN_MIN, FRAME_MARGIN_MAX, config.getBaselineFrameMargin());
        timestampSubSlider.setValue(sub);
        frameMarginSlider.setValue(margin);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    void updateValueLabels() {
        thresholdValueLabel.setText(String.format("0x0167 = 0x%02X, 0x0168 = 0x%02X  (file: 0x%02X, 0x%02X)",
                config.getRegisterValue(NRVConfig.REG_ON_UNIT),
                config.getRegisterValue(NRVConfig.REG_OFF_UNIT),
                config.getBaselineOnUnit(),
                config.getBaselineOffUnit()));
        onOffValueLabel.setText(String.format("balance → 0x0167 = 0x%02X, 0x0168 = 0x%02X",
                config.getRegisterValue(NRVConfig.REG_ON_UNIT),
                config.getRegisterValue(NRVConfig.REG_OFF_UNIT)));
        updateThresholdReadoutLabels();
        timestampSubValueLabel.setText(String.format("0x32B2 = 0x%02X  (file: 0x%02X)",
                config.getRegisterValue(NRVConfig.REG_TSTAMP_SUB_UNIT_LSB),
                config.getBaselineTimestampSub()));
        frameMarginValueLabel.setText(String.format("0x321E = 0x%02X  (file: 0x%02X)",
                config.getRegisterValue(NRVConfig.REG_DTAG_FRM_MARGIN_LSB),
                config.getBaselineFrameMargin()));
        updateTimingReadoutLabels();
    }

    private void updateThresholdReadoutLabels() {
        final float onLogE = config.getOnThresholdLogE();
        final float offLogE = config.getOffThresholdLogE();
        if (Float.isNaN(onLogE)) {
            onThresholdLabel.setText("ON: —");
        } else {
            final float onPct = NRVConfig.logThresholdToPercentChange(onLogE);
            onThresholdLabel.setText(String.format(
                    "ON:  %.2f e-folds (%.2f%%)   K_ON/K_REF = %.2g",
                    onLogE, onPct, config.getKOn() / config.getKRef()));
        }
        if (Float.isNaN(offLogE)) {
            offThresholdLabel.setText("OFF: —");
        } else {
            final float offPct = NRVConfig.logThresholdToPercentChange(offLogE);
            offThresholdLabel.setText(String.format(
                    "OFF: %.2f e-folds (%.2f%%)   K_OFF/K_REF = %.2g",
                    offLogE, offPct, config.getKOff() / config.getKRef()));
        }
    }

    private void updateTimingReadoutLabels() {
        final int margin = config.getFrameMarginCombined();
        updateFrameTimingLabel(margin);
        updateSubTimestampTimingLabel(config.getTimestampSubUnit());
    }

    private void updateFrameTimingLabel(int marginCombined) {
        final float frameHz = config.getReadoutFrameRateHzForMargin(marginCombined);
        final float framePeriodUs = config.getFramePeriodUsForMargin(marginCombined);
        if (Float.isNaN(frameHz) || Float.isNaN(framePeriodUs)) {
            frameTimingLabel.setText("Readout frame rate: —");
        } else {
            frameTimingLabel.setText(String.format(
                    "Readout frame rate: %.1f Hz  (period %.2f ms, margin 0x%04X)",
                    frameHz, framePeriodUs / 1000f, marginCombined));
        }
    }

    private void updateSubTimestampTimingLabel(int subUnit) {
        final float subUs = config.getSubTimestampIntervalUsForSubUnit(subUnit);
        if (Float.isNaN(subUs)) {
            subTimestampTimingLabel.setText("Sub-timestamp interval: —");
        } else {
            subTimestampTimingLabel.setText(String.format(
                    "Sub-timestamp interval: %.2f µs  (REF 0x%04X / SUB 0x%02X)",
                    subUs, config.getTstampRefUnitVal(), subUnit));
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        final String name = evt.getPropertyName();
        if (Biasgen.PROPERTY_CHANGE_PREFERENCES_LOADED.equals(name)) {
            syncFromConfig();
            return;
        }
        if (NRVConfig.PROPERTY_THRESHOLD.equals(name)) {
            updatingFromConfig = true;
            thresholdTweaker.setValue((Float) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (NRVConfig.PROPERTY_ON_OFF_BALANCE.equals(name)) {
            updatingFromConfig = true;
            onOffBalanceTweaker.setValue((Float) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (NRVConfig.PROPERTY_TIMESTAMP_SUB.equals(name)) {
            updatingFromConfig = true;
            timestampSubSlider.setValue((Integer) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (NRVConfig.PROPERTY_FRAME_MARGIN.equals(name)) {
            updatingFromConfig = true;
            frameMarginSlider.setValue((Integer) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (NRVConfig.PROPERTY_REGISTER_UPDATED.equals(name)) {
            final Object src = evt.getNewValue();
            if (src instanceof NRVRegisterSetting) {
                final int addr = ((NRVRegisterSetting) src).getRegAddr();
                if (addr == NRVConfig.REG_TSTAMP_SUB_UNIT_LSB || addr == NRVConfig.REG_DTAG_FRM_MARGIN_LSB
                        || addr == NRVConfig.REG_DTAG_FRM_MARGIN_MSB || addr == NRVConfig.REG_TO_SCNT0
                        || addr == NRVConfig.REG_TSTAMP_REF_MSB || addr == NRVConfig.REG_TSTAMP_REF_LSB) {
                    updatingFromConfig = true;
                    syncTimestampSliders();
                    updatingFromConfig = false;
                }
            }
            updateValueLabels();
        }
    }
}
