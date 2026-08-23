package ch.unizh.ini.jaer.chip.retina;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Biasgen tab for {@link DVSBiasController}: enable, goal, bounds, and live rates.
 */
public class DVSAutoControllerPanel extends JPanel implements PropertyChangeListener {

    private static final String HELP_HTML = "<html>Closed-loop DVS bias control. "
            + "<b>BoundEventRate</b> moves threshold. <b>LimitEventRate</b> moves refractory. "
            + "<b>TargetSNR</b> / <b>LimitNoise</b> move bandwidth. "
            + "The filter is auto-installed disabled and passes events through "
            + "(it does not denoise the live stream). Same instance as Filter → DVSBiasController.";

    private final DVSBiasController controller;
    private final EngineeringFormat eng = new EngineeringFormat();
    private final JCheckBox enableBox = new JCheckBox("Enable DVS Auto Controller");
    private final JComboBox<DVSBiasController.Goal> goalCombo = new JComboBox<>();
    private final JTextField rateLowField = new JTextField(10);
    private final JTextField rateHighField = new JTextField(10);
    private final JTextField hysteresisField = new JTextField(8);
    private final JTextField noiseLimitField = new JTextField(8);
    private final JTextField targetSnrField = new JTextField(8);
    private final JTextField stepField = new JTextField(8);
    private final JTextField minIntervalField = new JTextField(8);
    private final JTextField ignoreAfterField = new JTextField(8);
    private final JLabel ratesLabel = new JLabel();
    private final JLabel hzPixLabel = new JLabel();
    private final JLabel snrLabel = new JLabel();
    private final JLabel tweaksLabel = new JLabel();
    private final JLabel stateLabel = new JLabel();
    private boolean updatingFromController;

    public DVSAutoControllerPanel(DVSBiasController controller) {
        super(new BorderLayout());
        this.controller = controller;
        controller.getSupport().addPropertyChangeListener(this);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(8, 10, 10, 10));

        JLabel help = new JLabel(DVSUserControlPanel.htmlWrapped(HELP_HTML,
                DVSUserControlPanel.PREFERRED_PANEL_WIDTH - 48));
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(help);
        content.add(Box.createVerticalStrut(8));

        enableBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        enableBox.addActionListener(e -> {
            if (!updatingFromController) {
                controller.setFilterEnabled(enableBox.isSelected());
            }
        });
        content.add(enableBox);
        content.add(Box.createVerticalStrut(6));

        DVSTweaks tweaks = controller.getDvsTweaks();
        if (tweaks == null && controller.getChip() != null
                && controller.getChip().getBiasgen() instanceof DVSTweaks t) {
            tweaks = t;
        }
        for (DVSBiasController.Goal g : DVSBiasController.Goal.values()) {
            if (goalSupported(g, tweaks)) {
                goalCombo.addItem(g);
            }
        }
        goalCombo.setMaximumSize(new Dimension(280, 28));
        goalCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        goalCombo.addActionListener(e -> {
            if (!updatingFromController && goalCombo.getSelectedItem() instanceof DVSBiasController.Goal g) {
                controller.setGoal(g);
            }
        });
        content.add(labeled("Goal", goalCombo));
        content.add(Box.createVerticalStrut(8));

        JPanel bounds = new JPanel(new GridBagLayout());
        bounds.setBorder(BorderFactory.createTitledBorder("Bounds / policy"));
        bounds.setAlignmentX(Component.LEFT_ALIGNMENT);
        int r = 0;
        r = addField(bounds, r, "Rate low (Hz)", rateLowField, v -> controller.setEventRateLowHz(v));
        r = addField(bounds, r, "Rate high (Hz)", rateHighField, v -> controller.setEventRateHighHz(v));
        r = addField(bounds, r, "Rate hysteresis factor", hysteresisField, v -> controller.setEventRateBoundsHysteresisFactor(v));
        r = addField(bounds, r, "Noise limit (Hz/pix)", noiseLimitField, v -> controller.setNoiseLimitHzPerPixel(v));
        r = addField(bounds, r, "Target SNR", targetSnrField, v -> controller.setTargetSNR(v));
        r = addField(bounds, r, "Tweak step", stepField, v -> controller.setTweakStepAmount(v));
        r = addField(bounds, r, "Min command interval (ms)", minIntervalField, v -> controller.setMinCommandIntervalMs(Math.round(v)));
        addField(bounds, r, "Ignore after bias change (ms)", ignoreAfterField,
                v -> controller.setIgnoreEventsAfterBiasChangeMs(Math.round(v)));
        content.add(bounds);
        content.add(Box.createVerticalStrut(8));

        JButton revert = new JButton("Revert tweaks");
        revert.setAlignmentX(Component.LEFT_ALIGNMENT);
        revert.addActionListener(e -> controller.doRevertAllTweaks());
        content.add(revert);
        content.add(Box.createVerticalStrut(8));

        JPanel live = new JPanel();
        live.setLayout(new BoxLayout(live, BoxLayout.Y_AXIS));
        live.setBorder(BorderFactory.createTitledBorder("Live"));
        live.setAlignmentX(Component.LEFT_ALIGNMENT);
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        for (JLabel lab : new JLabel[] {ratesLabel, hzPixLabel, snrLabel, tweaksLabel, stateLabel}) {
            lab.setFont(mono);
            lab.setAlignmentX(Component.LEFT_ALIGNMENT);
            live.add(lab);
        }
        content.add(live);

        for (Component c : content.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
                jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height + 8));
            }
        }

        add(DVSUserControlPanel.widthTrackingScrollPane(content), BorderLayout.CENTER);
        int contentH = content.getPreferredSize().height + 32;
        setPreferredSize(new Dimension(DVSUserControlPanel.PREFERRED_PANEL_WIDTH,
                Math.min(Math.max(contentH, 400), 720)));
        setMinimumSize(new Dimension(320, 280));
        syncFromController();
    }

    /**
     * Installs this tab immediately after the last existing tab if {@code chip}'s
     * biasgen is {@link DVSTweaks}. Call right after adding the user-friendly tab.
     */
    public static void addTab(JTabbedPane tabs, AEChip chip) {
        if (tabs == null || chip == null || !(chip.getBiasgen() instanceof DVSTweaks)) {
            return;
        }
        DVSBiasController.ensurePresent(chip);
        DVSBiasController ctrl = DVSBiasController.find(chip);
        if (ctrl == null) {
            return;
        }
        tabs.addTab("DVS Auto Controller", new DVSAutoControllerPanel(ctrl));
    }

    private static boolean goalSupported(DVSBiasController.Goal g, DVSTweaks tweaks) {
        if (tweaks == null || g == DVSBiasController.Goal.None) {
            return true;
        }
        return switch (g) {
            case BoundEventRate -> tweaks.supportsThresholdTweak();
            case LimitEventRate -> tweaks.supportsMaxFiringRateTweak();
            case TargetSNR, LimitNoise -> tweaks.supportsBandwidthTweak();
            default -> true;
        };
    }

    private JPanel labeled(String title, JComponent field) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        JLabel lab = new JLabel(title + "  ");
        row.add(lab);
        row.add(field);
        row.add(Box.createHorizontalGlue());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private int addField(JPanel grid, int row, String title, JTextField field, FloatConsumer setter) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = row;
        grid.add(new JLabel(title), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        field.addActionListener(e -> applyField(field, setter));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyField(field, setter);
            }
        });
        grid.add(field, c);
        return row + 1;
    }

    private void applyField(JTextField field, FloatConsumer setter) {
        if (updatingFromController) {
            return;
        }
        try {
            setter.accept(Float.parseFloat(field.getText().trim()));
        } catch (NumberFormatException ignore) {
            syncFromController();
        }
    }

    private void syncFromController() {
        updatingFromController = true;
        try {
            enableBox.setSelected(controller.isFilterEnabled());
            goalCombo.setSelectedItem(controller.getGoal());
            rateLowField.setText(fmt(controller.getEventRateLowHz()));
            rateHighField.setText(fmt(controller.getEventRateHighHz()));
            hysteresisField.setText(fmt(controller.getEventRateBoundsHysteresisFactor()));
            noiseLimitField.setText(fmt(controller.getNoiseLimitHzPerPixel()));
            targetSnrField.setText(fmt(controller.getTargetSNR()));
            stepField.setText(fmt(controller.getTweakStepAmount()));
            minIntervalField.setText(Integer.toString(controller.getMinCommandIntervalMs()));
            ignoreAfterField.setText(Integer.toString(controller.getIgnoreEventsAfterBiasChangeMs()));
            updateLive();
        } finally {
            updatingFromController = false;
        }
    }

    private void updateLive() {
        int nPix = Math.max(1, controller.getChip().getNumPixels());
        ratesLabel.setText(String.format("Input / signal / noise: %s / %s / %s Hz",
                eng.format(controller.getInputEventRate()),
                eng.format(controller.getSignalEventRate()),
                eng.format(controller.getNoiseEventRate())));
        hzPixLabel.setText(String.format("Hz/pix  input %s   noise %s",
                eng.format(controller.getInputEventRate() / nPix),
                eng.format(controller.getNoiseEventRate() / nPix)));
        snrLabel.setText("SNR  " + eng.format(controller.getSnr()));
        tweaksLabel.setText(String.format("Tweaks  Thr %s   BW %s   Refr %s",
                eng.format(controller.getThresholdTweak()),
                eng.format(controller.getBandwidthTweak()),
                eng.format(controller.getMaxFiringRateTweak())));
        stateLabel.setText(String.format("State  %s | noise %s | SNR %s",
                controller.getEventRateStateText(),
                controller.getNoiseEventRateStateText(),
                controller.getSnrStateText()));
    }

    private String fmt(float v) {
        if (Math.abs(v) >= 1000) {
            return String.format("%.0f", v);
        }
        return String.format("%.3g", v);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        SwingUtilities.invokeLater(() -> {
            if ("filterEnabled".equals(evt.getPropertyName())
                    || DVSBiasController.EVENT_GOAL.equals(evt.getPropertyName())
                    || "eventRateLowHz".equals(evt.getPropertyName())
                    || "eventRateHighHz".equals(evt.getPropertyName())) {
                syncFromController();
            } else {
                updateLive();
            }
        });
    }

    @FunctionalInterface
    private interface FloatConsumer {
        void accept(float v);
    }
}
