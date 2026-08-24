package ch.unizh.ini.jaer.chip.retina;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
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

    private static final String HELP_HTML = "<html><font color=\"red\"><b>(Experimental)</b></font><br>"
            + "Closed-loop DVS bias control. "
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
    private final JLabel stateLabel = new JLabel();
    private final JLabel measureName = new JLabel();
    private final JLabel measureValue = new JLabel();
    private final MeterBar measureBar = new MeterBar();
    private final JLabel thrName = new JLabel("Thr");
    private final JLabel thrValue = new JLabel();
    private final MeterBar thrBar = new MeterBar();
    private final JLabel bwName = new JLabel("BW");
    private final JLabel bwValue = new JLabel();
    private final MeterBar bwBar = new MeterBar();
    private final JLabel refrName = new JLabel("Refr");
    private final JLabel refrValue = new JLabel();
    private final MeterBar refrBar = new MeterBar();
    private final JPanel measureRow = new JPanel(new GridBagLayout());
    private static final Font LIVE_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font LIVE_FONT_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private static final Color TWEAK_COLOR = new Color(40, 80, 200);
    private static final Color ACTIVE_TWEAK_COLOR = new Color(20, 50, 180);
    private boolean updatingFromController;

    public DVSAutoControllerPanel(DVSBiasController controller) {
        super(new BorderLayout());
        this.controller = controller;
        controller.getSupport().addPropertyChangeListener(this);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                updateLive();
            }
        });

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(8, 10, 10, 10));

        JLabel help = new JLabel(DVSUserControlPanel.htmlWrapped(HELP_HTML,
                DVSUserControlPanel.PREFERRED_PANEL_WIDTH - 48));
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (controller.getDescription() != null) {
            help.setToolTipText(controller.getDescription());
        }
        content.add(help);
        content.add(Box.createVerticalStrut(8));

        enableBox.addActionListener(e -> {
            if (!updatingFromController) {
                controller.setFilterEnabled(enableBox.isSelected());
            }
        });
        inheritTooltip(enableBox, "filterEnabled");
        JButton showHelp = new JButton("Show help");
        showHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
        showHelp.setMaximumSize(showHelp.getPreferredSize());
        showHelp.setToolTipText("Show DVSBiasController help");
        showHelp.setEnabled(controller.hasHelp());
        showHelp.addActionListener(e -> controller.showHelpDialog());
        JPanel enableRow = new JPanel();
        enableRow.setLayout(new BoxLayout(enableRow, BoxLayout.X_AXIS));
        enableRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        enableRow.add(enableBox);
        enableRow.add(Box.createHorizontalStrut(8));
        enableRow.add(showHelp);
        enableRow.add(Box.createHorizontalGlue());
        content.add(enableRow);
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
        content.add(labeled("Goal", goalCombo, "goal"));
        content.add(Box.createVerticalStrut(8));

        JPanel bounds = new JPanel(new GridBagLayout());
        bounds.setBorder(BorderFactory.createTitledBorder("Bounds / policy"));
        bounds.setAlignmentX(Component.LEFT_ALIGNMENT);
        int r = 0;
        r = addField(bounds, r, "Rate low (Hz)", rateLowField, "eventRateLowHz",
                v -> controller.setEventRateLowHz(v));
        r = addField(bounds, r, "Rate high (Hz)", rateHighField, "eventRateHighHz",
                v -> controller.setEventRateHighHz(v));
        r = addField(bounds, r, "Rate hysteresis factor", hysteresisField, "eventRateBoundsHysteresisFactor",
                v -> controller.setEventRateBoundsHysteresisFactor(v));
        r = addField(bounds, r, "Noise limit (Hz/pix)", noiseLimitField, "noiseLimitHzPerPixel",
                v -> controller.setNoiseLimitHzPerPixel(v));
        r = addField(bounds, r, "Target SNR", targetSnrField, "targetSNR",
                v -> controller.setTargetSNR(v));
        r = addField(bounds, r, "Tweak step", stepField, "tweakStepAmount",
                v -> controller.setTweakStepAmount(v));
        r = addField(bounds, r, "Min command interval (ms)", minIntervalField, "minCommandIntervalMs",
                v -> controller.setMinCommandIntervalMs(Math.round(v)));
        addField(bounds, r, "Ignore after bias change (ms)", ignoreAfterField, "ignoreEventsAfterBiasChangeMs",
                v -> controller.setIgnoreEventsAfterBiasChangeMs(Math.round(v)));
        content.add(bounds);
        content.add(Box.createVerticalStrut(8));

        JButton revert = new JButton("Revert tweaks");
        revert.setAlignmentX(Component.LEFT_ALIGNMENT);
        revert.setMaximumSize(revert.getPreferredSize());
        inheritTooltip(revert, "revertAllTweaks");
        revert.addActionListener(e -> controller.doRevertAllTweaks());
        content.add(revert);
        content.add(Box.createVerticalStrut(8));

        JPanel live = new JPanel(new GridBagLayout());
        live.setBorder(BorderFactory.createTitledBorder("Live (3/s)"));
        live.setAlignmentX(Component.LEFT_ALIGNMENT);
        stateLabel.setFont(LIVE_FONT);
        inheritTooltip(stateLabel, "goal");
        inheritTooltip(measureRow, "eventRateTauMs");
        inheritTooltip(thrName, "thresholdTweak");
        inheritTooltip(bwName, "bandwidthTweak");
        inheritTooltip(refrName, "maxFiringRateTweak");
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.gridy = 0;
        lc.gridwidth = 3;
        lc.weightx = 1;
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(1, 2, 4, 2);
        live.add(stateLabel, lc);
        buildLiveRow(measureRow, measureName, measureValue, measureBar);
        lc.gridy = 1;
        lc.insets = new Insets(1, 2, 2, 2);
        live.add(measureRow, lc);
        lc.gridy = 2;
        live.add(liveTweakRow(thrName, thrValue, thrBar), lc);
        lc.gridy = 3;
        live.add(liveTweakRow(bwName, bwValue, bwBar), lc);
        lc.gridy = 4;
        live.add(liveTweakRow(refrName, refrValue, refrBar), lc);
        content.add(live);

        syncFromController();
        keepCompact(help, enableRow, bounds, live);
        Dimension comboMax = goalCombo.getMaximumSize();
        goalCombo.setMaximumSize(new Dimension(Math.min(comboMax.width, 220), comboMax.height));

        add(DVSUserControlPanel.compactScrollPane(content), BorderLayout.CENTER);
        int contentH = content.getPreferredSize().height + 32;
        setPreferredSize(new Dimension(DVSUserControlPanel.PREFERRED_PANEL_WIDTH,
                Math.min(Math.max(contentH, 400), 720)));
        setMinimumSize(new Dimension(320, 280));
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

    private JPanel labeled(String title, JComponent field, String property) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        JLabel lab = new JLabel(title + "  ");
        inheritTooltip(lab, property);
        inheritTooltip(field, property);
        inheritTooltip(row, property);
        row.add(lab);
        row.add(field);
        row.add(Box.createHorizontalGlue());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        keepCompact(row);
        return row;
    }

    private int addField(JPanel grid, int row, String title, JTextField field, String property, FloatConsumer setter) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = row;
        JLabel lab = new JLabel(title);
        inheritTooltip(lab, property);
        inheritTooltip(field, property);
        grid.add(lab, c);
        c.gridx = 1;
        field.setColumns(10);
        Dimension fieldSize = field.getPreferredSize();
        field.setMinimumSize(fieldSize);
        field.setMaximumSize(fieldSize);
        field.setPreferredSize(fieldSize);
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

    private void inheritTooltip(JComponent c, String property) {
        String tip = controller.getPropertyTooltip(property);
        if (tip != null && !tip.isBlank()) {
            c.setToolTipText(tip);
        }
    }

    private static void keepCompact(JComponent... components) {
        for (JComponent c : components) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension p = c.getPreferredSize();
            int w = Math.min(Math.max(p.width, 1), DVSUserControlPanel.PREFERRED_PANEL_WIDTH);
            int h = Math.max(p.height, 1);
            c.setMaximumSize(new Dimension(w, h));
        }
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

    /**
     * Live meters only when this tab is selected and the hardware window is
     * showing ({@link #isShowing()}).
     */
    private void updateLive() {
        if (!isShowing()) {
            return;
        }
        boolean on = controller.isFilterEnabled();
        DVSBiasController.Goal goal = controller.getGoal();
        if (!on) {
            stateLabel.setText("DVSBiasController off");
            measureRow.setVisible(false);
        } else if (goal == DVSBiasController.Goal.None) {
            stateLabel.setText("Goal None — no closed-loop control");
            measureRow.setVisible(false);
        } else {
            stateLabel.setText(goal + " — " + stateTextForGoal(goal));
            measureRow.setVisible(true);
            updateMeasure(goal);
        }
        setTweak(thrName, thrValue, thrBar, controller.getThresholdTweak(),
                on && goal == DVSBiasController.Goal.BoundEventRate);
        setTweak(bwName, bwValue, bwBar, controller.getBandwidthTweak(),
                on && (goal == DVSBiasController.Goal.TargetSNR || goal == DVSBiasController.Goal.LimitNoise));
        setTweak(refrName, refrValue, refrBar, controller.getMaxFiringRateTweak(),
                on && goal == DVSBiasController.Goal.LimitEventRate);
    }

    private String stateTextForGoal(DVSBiasController.Goal goal) {
        return switch (goal) {
            case BoundEventRate, LimitEventRate -> controller.getEventRateStateText();
            case LimitNoise -> controller.getNoiseEventRateStateText();
            case TargetSNR -> controller.getSnrStateText();
            default -> "";
        };
    }

    private void updateMeasure(DVSBiasController.Goal goal) {
        switch (goal) {
            case BoundEventRate, LimitEventRate -> {
                measureName.setText("Input");
                float rate = controller.getInputEventRate();
                measureValue.setText(engOrDash(rate) + "Hz");
                float logLow = (float) Math.log10(Math.max(controller.getEventRateLowHz(), 1e-3f));
                float logHigh = (float) Math.log10(Math.max(controller.getEventRateHighHz(), 1e-3f));
                if (logHigh <= logLow) {
                    logHigh = logLow + 1;
                }
                float logMin = logLow - 1;
                float logMax = logHigh + 1;
                float logR = (Float.isFinite(rate) && rate > 0) ? (float) Math.log10(rate) : logMin;
                float span = logMax - logMin;
                inheritTooltip(measureRow, "eventRateTauMs");
                measureBar.setFromLeft((logR - logMin) / span, colorForRateText(controller.getEventRateStateText()),
                        (logLow - logMin) / span, (logHigh - logMin) / span);
            }
            case LimitNoise -> {
                measureName.setText("Noise");
                int nPix = Math.max(1, controller.getChip().getNumPixels());
                float hzPix = controller.getNoiseEventRate() / nPix;
                float limit = controller.getNoiseLimitHzPerPixel();
                measureValue.setText(engOrDash(hzPix) + "Hz/pix");
                float max = Math.max(limit * 5, 1e-6f);
                inheritTooltip(measureRow, "noiseLimitHzPerPixel");
                measureBar.setFromLeft(hzPix / max, colorForRateText(controller.getNoiseEventRateStateText()),
                        limit / max);
            }
            case TargetSNR -> {
                measureName.setText("SNR");
                float snr = controller.getSnr();
                float db = 20 * (float) Math.log10(snr);
                measureValue.setText(engOrDash(snr) + " (" + engOrDash(db) + "dB)");
                float target = controller.getTargetSNR();
                Color c = (!Float.isFinite(snr) || snr < target) ? Color.RED : new Color(0, 160, 0);
                inheritTooltip(measureRow, "targetSNR");
                measureBar.setBipolar(snr - target, c);
            }
            default -> measureRow.setVisible(false);
        }
    }

    private void setTweak(JLabel name, JLabel value, MeterBar bar, float tweak, boolean active) {
        Font font = active ? LIVE_FONT_BOLD : LIVE_FONT;
        name.setFont(font);
        value.setFont(font);
        value.setText(engOrDash(tweak));
        bar.setBipolar(tweak, active ? ACTIVE_TWEAK_COLOR : TWEAK_COLOR);
    }

    private static Color colorForRateText(String text) {
        if (text.contains("below")) {
            return Color.BLUE;
        }
        if (text.contains("above")) {
            return Color.RED;
        }
        if (text.contains("within")) {
            return new Color(0, 160, 0);
        }
        return new Color(140, 140, 0);
    }

    private static void buildLiveRow(JPanel row, JLabel name, JLabel value, MeterBar bar) {
        name.setFont(LIVE_FONT);
        value.setFont(LIVE_FONT);
        name.setPreferredSize(new Dimension(48, 16));
        value.setPreferredSize(new Dimension(120, 16));
        row.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 2, 0, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        row.add(name, c);
        c.gridx = 1;
        row.add(value, c);
        c.gridx = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        row.add(bar, c);
    }

    private static JPanel liveTweakRow(JLabel name, JLabel value, MeterBar bar) {
        JPanel row = new JPanel(new GridBagLayout());
        buildLiveRow(row, name, value, bar);
        return row;
    }

    private String fmt(float v) {
        if (Math.abs(v) >= 1000) {
            return String.format("%.0f", v);
        }
        return String.format("%.3g", v);
    }

    private String engOrDash(float v) {
        if (!Float.isFinite(v)) {
            return "—";
        }
        return eng.format(v);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String n = evt.getPropertyName();
        boolean config = "filterEnabled".equals(n)
                || DVSBiasController.EVENT_GOAL.equals(n)
                || "eventRateLowHz".equals(n)
                || "eventRateHighHz".equals(n)
                || "noiseLimitHzPerPixel".equals(n)
                || "targetSNR".equals(n)
                || "eventRateBoundsHysteresisFactor".equals(n)
                || "tweakStepAmount".equals(n)
                || "minCommandIntervalMs".equals(n)
                || "ignoreEventsAfterBiasChangeMs".equals(n);
        if (!config && !DVSBiasController.EVENT_CONTROL_STATE.equals(n)) {
            return;
        }
        if (!config && !isShowing()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (config) {
                syncFromController();
            } else {
                updateLive();
            }
        });
    }

    /**
     * Horizontal meter matching the OpenGL overlay bars: left-fill with ticks,
     * or bipolar around center for tweaks / SNR error.
     */
    private static final class MeterBar extends JComponent {

        private float value;
        private boolean bipolar;
        private Color fillColor = Color.GRAY;
        private float[] ticks = new float[0];

        MeterBar() {
            setPreferredSize(new Dimension(180, 16));
            setMinimumSize(new Dimension(64, 14));
            setOpaque(false);
        }

        void setFromLeft(float value01, Color color, float... tick01) {
            bipolar = false;
            value = value01;
            fillColor = color != null ? color : Color.GRAY;
            ticks = tick01 != null ? tick01 : new float[0];
            repaint();
        }

        void setBipolar(float valueM11, Color color) {
            bipolar = true;
            value = valueM11;
            fillColor = color != null ? color : Color.GRAY;
            ticks = new float[]{0.5f};
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = Math.max(1, getWidth());
                int h = Math.max(1, getHeight());
                int midY = h / 2;
                g2.setColor(new Color(225, 225, 225));
                g2.fillRoundRect(0, midY - 3, w - 1, 7, 4, 4);
                g2.setColor(new Color(180, 180, 180));
                g2.drawRoundRect(0, midY - 3, w - 1, 7, 4, 4);
                g2.setColor(fillColor);
                if (bipolar) {
                    float v = Float.isFinite(value) ? Math.max(-1f, Math.min(1f, value)) : 0f;
                    int mid = (w - 1) / 2;
                    int x = Math.round((v + 1f) / 2f * (w - 1));
                    g2.fillRect(Math.min(mid, x), midY - 2, Math.max(2, Math.abs(x - mid)), 5);
                } else {
                    float v = Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
                    g2.fillRect(1, midY - 2, Math.max(2, Math.round(v * (w - 1))), 5);
                }
                g2.setColor(Color.DARK_GRAY);
                for (float t : ticks) {
                    if (!Float.isFinite(t)) {
                        continue;
                    }
                    int x = Math.round(Math.max(0f, Math.min(1f, t)) * (w - 1));
                    g2.drawLine(x, 1, x, h - 2);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    @FunctionalInterface
    private interface FloatConsumer {
        void accept(float v);
    }
}
