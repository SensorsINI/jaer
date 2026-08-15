package ch.unizh.ini.jaer.chip.retina;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;

/**
 * User-facing DVXplorer controls matching the DV / dv-processing API:
 * contrast thresholds 0–17 and ReadoutFPS.
 */
public class DVXplorerControlPanel extends JPanel implements PropertyChangeListener {

    private static final String HELP = "<html>Samsung DVS biases in DV are two <b>contrast thresholds</b> "
            + "(0–17, default 9). Smaller values = more sensitive / more noise. "
            + "<b>ReadoutFPS</b> sets event-frame period "
            + "(<a href=\"https://docs.inivation.com/hardware/hardware-advanced-usage/biasing.html\">iniVation biasing docs</a>). "
            + "File → Save in this window stores values in Preferences.";

    private final DVXplorerConfig config;
    private boolean updating;

    private final JSlider onSlider = new JSlider(DVXplorerConfig.CONTRAST_MIN, DVXplorerConfig.CONTRAST_MAX,
            DVXplorerConfig.CONTRAST_DEFAULT);
    private final JSlider offSlider = new JSlider(DVXplorerConfig.CONTRAST_MIN, DVXplorerConfig.CONTRAST_MAX,
            DVXplorerConfig.CONTRAST_DEFAULT);
    private final JLabel onValue = new JLabel("9");
    private final JLabel offValue = new JLabel("9");
    private final JComboBox<DVXplorerConfig.ReadoutFPS> fpsCombo = new JComboBox<>(DVXplorerConfig.ReadoutFPS.values());
    private final JCheckBox holdBox = new JCheckBox("Global hold (default on; off can help LED tracking)");
    private final JCheckBox resetBox = new JCheckBox("Global reset");

    public DVXplorerControlPanel(DVXplorerConfig config) {
        super(new GridBagLayout());
        this.config = config;
        setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10));

        final GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        int row = 0;
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        add(new JLabel(HELP), c);

        c.gridwidth = 1;
        row++;
        addLabeledSlider(c, row++, "Contrast threshold ON", onSlider, onValue,
                "ON events: 0 = most sensitive, 17 = least. DV default 9.");
        addLabeledSlider(c, row++, "Contrast threshold OFF", offSlider, offValue,
                "OFF events: 0 = most sensitive, 17 = least. DV default 9.");

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        add(new JLabel("ReadoutFPS"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        fpsCombo.setToolTipText("<html>CONSTANT: fixed period, no loss.<br>"
                + "CONSTANT_LOSSY: fixed period, may truncate a busy frame.<br>"
                + "VARIABLE: best-effort period, no loss (DV default VARIABLE_5000).");
        add(fpsCombo, c);
        row++;

        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 3;
        holdBox.setToolTipText("REGISTER_DIGITAL_MODE_CONTROL bit 0. Default enabled in DV.");
        add(holdBox, c);
        c.gridy = row;
        resetBox.setToolTipText("REGISTER_DIGITAL_MODE_CONTROL bit 1. Default disabled in DV.");
        add(resetBox, c);

        onSlider.addChangeListener(e -> {
            if (!updating && !onSlider.getValueIsAdjusting()) {
                config.setContrastThresholdOn(onSlider.getValue());
            }
            onValue.setText(Integer.toString(onSlider.getValue()));
        });
        offSlider.addChangeListener(e -> {
            if (!updating && !offSlider.getValueIsAdjusting()) {
                config.setContrastThresholdOff(offSlider.getValue());
            }
            offValue.setText(Integer.toString(offSlider.getValue()));
        });
        fpsCombo.addActionListener(e -> {
            if (!updating) {
                config.setReadoutFps((DVXplorerConfig.ReadoutFPS) fpsCombo.getSelectedItem());
            }
        });
        holdBox.addActionListener(e -> {
            if (!updating) {
                config.setGlobalHold(holdBox.isSelected());
            }
        });
        resetBox.addActionListener(e -> {
            if (!updating) {
                config.setGlobalReset(resetBox.isSelected());
            }
        });

        config.getSupport().addPropertyChangeListener(this);
        syncFromConfig();
    }

    private void addLabeledSlider(GridBagConstraints c, int row, String name, JSlider slider, JLabel value,
            String tip) {
        slider.setMajorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setSnapToTicks(true);
        slider.setToolTipText(tip);
        value.setHorizontalAlignment(SwingConstants.RIGHT);

        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        add(new JLabel(name), c);
        c.gridx = 1;
        c.weightx = 1;
        add(slider, c);
        c.gridx = 2;
        c.weightx = 0;
        add(value, c);
    }

    void syncFromConfig() {
        updating = true;
        try {
            onSlider.setValue(config.getContrastThresholdOn());
            offSlider.setValue(config.getContrastThresholdOff());
            onValue.setText(Integer.toString(config.getContrastThresholdOn()));
            offValue.setText(Integer.toString(config.getContrastThresholdOff()));
            fpsCombo.setSelectedItem(config.getReadoutFps());
            holdBox.setSelected(config.isGlobalHold());
            resetBox.setSelected(config.isGlobalReset());
        } finally {
            updating = false;
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        syncFromConfig();
    }
}
