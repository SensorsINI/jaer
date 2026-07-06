package ch.unizh.ini.jaer.chip.prophesee;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;

/**
 * Simple IMX636 bias sliders for Prophesee EVK4 HD.
 */
public class PropheseeControlPanel extends JPanel {

    private final PropheseeConfig config;
    private final JLabel prLabel = new JLabel();
    private final JLabel foLabel = new JLabel();
    private final JLabel diffLabel = new JLabel();
    private final JLabel diffOnLabel = new JLabel();
    private final JLabel diffOffLabel = new JLabel();
    private final JLabel refrLabel = new JLabel();
    private final JLabel hpfLabel = new JLabel();

    private final JSlider prSlider = newSlider();
    private final JSlider foSlider = newSlider();
    private final JSlider diffSlider = newSlider();
    private final JSlider diffOnSlider = newSlider();
    private final JSlider diffOffSlider = newSlider();
    private final JSlider refrSlider = newSlider();
    private final JSlider hpfSlider = newSlider();

    private boolean updating;

    public PropheseeControlPanel(PropheseeConfig config) {
        super(new GridBagLayout());
        this.config = config;

        int row = 0;
        row = addBiasRow(row, "pr (photoreceptor)", prSlider, prLabel, v -> config.setPr(v));
        row = addBiasRow(row, "fo (source follower)", foSlider, foLabel, v -> config.setFo(v));
        row = addBiasRow(row, "diff (threshold)", diffSlider, diffLabel, v -> config.setDiff(v));
        row = addBiasRow(row, "diff_on", diffOnSlider, diffOnLabel, v -> config.setDiffOn(v));
        row = addBiasRow(row, "diff_off", diffOffSlider, diffOffLabel, v -> config.setDiffOff(v));
        row = addBiasRow(row, "refr (refractory)", refrSlider, refrLabel, v -> config.setRefr(v));
        addBiasRow(row, "hpf (high-pass)", hpfSlider, hpfLabel, v -> config.setHpf(v));

        refreshFromBiases();
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private static JSlider newSlider() {
        final JSlider slider = new JSlider(0, 0xFF, 0);
        slider.setMajorTickSpacing(0x40);
        slider.setPaintTicks(true);
        return slider;
    }

    private int addBiasRow(int row, String name, JSlider slider, JLabel valueLabel, IntConsumer onChange) {
        final GridBagConstraints labelC = new GridBagConstraints();
        labelC.gridx = 0;
        labelC.gridy = row;
        labelC.anchor = GridBagConstraints.WEST;
        labelC.insets = new Insets(4, 4, 4, 8);
        add(new JLabel(name), labelC);

        final GridBagConstraints sliderC = new GridBagConstraints();
        sliderC.gridx = 1;
        sliderC.gridy = row;
        sliderC.weightx = 1.0;
        sliderC.fill = GridBagConstraints.HORIZONTAL;
        sliderC.insets = new Insets(4, 4, 4, 8);
        add(slider, sliderC);

        final GridBagConstraints valueC = new GridBagConstraints();
        valueC.gridx = 2;
        valueC.gridy = row;
        valueC.anchor = GridBagConstraints.EAST;
        valueC.insets = new Insets(4, 4, 4, 4);
        add(valueLabel, valueC);

        final ChangeListener listener = e -> {
            if (updating) {
                return;
            }
            onChange.accept(slider.getValue());
            valueLabel.setText(String.format("0x%02X", slider.getValue()));
        };
        slider.addChangeListener(listener);
        return row + 1;
    }

    void refreshFromBiases() {
        updating = true;
        try {
            setSlider(prSlider, prLabel, config.getPr());
            setSlider(foSlider, foLabel, config.getFo());
            setSlider(diffSlider, diffLabel, config.getDiff());
            setSlider(diffOnSlider, diffOnLabel, config.getDiffOn());
            setSlider(diffOffSlider, diffOffLabel, config.getDiffOff());
            setSlider(refrSlider, refrLabel, config.getRefr());
            setSlider(hpfSlider, hpfLabel, config.getHpf());
        } finally {
            updating = false;
        }
    }

    private static void setSlider(JSlider slider, JLabel label, int value) {
        slider.setValue(value);
        label.setText(String.format("0x%02X", value));
    }
}
