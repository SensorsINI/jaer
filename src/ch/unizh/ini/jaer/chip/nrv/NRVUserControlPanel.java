package ch.unizh.ini.jaer.chip.nrv;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.PotTweaker;

/**
 * Simplified NRV controls: brightness change threshold and ON/OFF balance.
 */
public class NRVUserControlPanel extends JPanel implements PropertyChangeListener {

    private final NRVConfig config;
    private final PotTweaker thresholdTweaker = new PotTweaker();
    private final PotTweaker onOffBalanceTweaker = new PotTweaker();
    private final JLabel thresholdValueLabel = new JLabel();
    private final JLabel onOffValueLabel = new JLabel();
    private boolean updatingFromConfig;

    public NRVUserControlPanel(NRVConfig config) {
        super(new BorderLayout(4, 4));
        this.config = config;
        setBorder(new EmptyBorder(6, 6, 6, 6));

        thresholdTweaker.setName("Brightness threshold");
        thresholdTweaker.setTweakDescription("Adjusts brightness change (temporal contrast) threshold");
        thresholdTweaker.setLessDescription("Lower / more events");
        thresholdTweaker.setMoreDescription("Higher / fewer events");
        thresholdTweaker.setToolTipText("<html>Maps to register 0x0166 (REG_DIV_BCM_BOT_UNIT_AMP).<br>"
                + "Changes are sent live; the loaded .txt file is not modified.");

        onOffBalanceTweaker.setName("ON / OFF balance");
        onOffBalanceTweaker.setTweakDescription("Adjusts balance between ON and OFF events");
        onOffBalanceTweaker.setLessDescription("More OFF events");
        onOffBalanceTweaker.setMoreDescription("More ON events");
        onOffBalanceTweaker.setToolTipText("<html>Maps to registers 0x0167 (ON) and 0x0168 (OFF).<br>"
                + "Changes are sent live; the loaded .txt file is not modified.");

        thresholdTweaker.addChangeListener(e -> onThresholdChanged());
        onOffBalanceTweaker.addChangeListener(e -> onOnOffBalanceChanged());

        config.getSupport().addPropertyChangeListener(this);
        config.getSupport().addPropertyChangeListener(thresholdTweaker);
        config.getSupport().addPropertyChangeListener(onOffBalanceTweaker);

        final JPanel sliders = new JPanel();
        sliders.setLayout(new javax.swing.BoxLayout(sliders, javax.swing.BoxLayout.Y_AXIS));

        sliders.add(wrapSlider(thresholdTweaker, thresholdValueLabel));
        sliders.add(javax.swing.Box.createVerticalStrut(8));
        sliders.add(wrapSlider(onOffBalanceTweaker, onOffValueLabel));

        final JLabel help = new JLabel("<html>Sliders tweak values around those loaded from the settings file.<br>"
                + "Use <b>Undo</b> / <b>Redo</b> in the Biases toolbar to revert slider moves.<br>"
                + "Use <b>File → Revert</b> or reload the .txt to restore all register values from file.");

        add(help, BorderLayout.NORTH);
        add(sliders, BorderLayout.CENTER);

        syncFromConfig();
    }

    private static JPanel wrapSlider(PotTweaker tweaker, JLabel valueLabel) {
        final JPanel row = new JPanel(new BorderLayout(4, 0));
        final JPanel valueRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        valueRow.add(valueLabel);
        row.add(tweaker, BorderLayout.CENTER);
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

    void syncFromConfig() {
        updatingFromConfig = true;
        thresholdTweaker.setValue(config.getThresholdTweak());
        onOffBalanceTweaker.setValue(config.getOnOffBalanceTweak());
        updatingFromConfig = false;
        updateValueLabels();
    }

    void updateValueLabels() {
        thresholdValueLabel.setText(String.format("0x0166 = 0x%02X  (file: 0x%02X)",
                config.getRegisterValue(NRVConfig.REG_BRIGHTNESS_THRESHOLD),
                config.getBaselineThreshold()));
        onOffValueLabel.setText(String.format("0x0167 = 0x%02X, 0x0168 = 0x%02X  (file: 0x%02X, 0x%02X)",
                config.getRegisterValue(NRVConfig.REG_ON_UNIT),
                config.getRegisterValue(NRVConfig.REG_OFF_UNIT),
                config.getBaselineOnUnit(),
                config.getBaselineOffUnit()));
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
        } else if (NRVConfig.PROPERTY_REGISTER_UPDATED.equals(name)) {
            updateValueLabels();
        }
    }
}
