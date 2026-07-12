package nrv.chip;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.PotTweaker;
import nrv.usb.NRVRegisterSetting;

/**
 * Simplified NRV controls: DVS-style event thresholds first, then scan-rate
 * and USB sub-timestamp timing at the bottom.
 *
 * @see https://nrv.kr/
 */
public class NRVUserControlPanel extends JPanel implements PropertyChangeListener {

    private static final Logger log = Logger.getLogger(NRVUserControlPanel.class.getName());

    private static final String BIAS_SECTION_TOOLTIP = "<html>Sliders tweak loaded settings around file values.<br>"
            + "<b>Undo/Redo</b> in the Biases toolbar; <b>File→Revert</b> restores the .txt.<br>"
            + "Event threshold: right → lower 0x0167 / higher 0x0168, raise both |Θ|.<br>"
            + "ON/OFF balance: right → more ON events (independent of threshold).";

    private static final String TIMING_SECTION_TOOLTIP = "<html>Scan rate morphs DTAG registers (0x321D:321E and block).<br>"
            + "Sub-timestamp (0x32B2) is USB packet cadence within each ms.";

    private static final int SUB_UNIT_MIN = 1;
    private static final int SUB_UNIT_MAX = 0x7F;

    private final NRVConfig config;
    private final PotTweaker thresholdTweaker = new PotTweaker();
    private final PotTweaker onOffBalanceTweaker = new PotTweaker();
    private final JSlider scanRateSlider = new JSlider(NRVConfig.SCAN_RATE_HZ_MIN, NRVConfig.SCAN_RATE_HZ_MAX, 300);
    private final JSlider timestampSubSlider = new JSlider(SUB_UNIT_MIN, SUB_UNIT_MAX, 0x21);
    private final JLabel thresholdValueLabel = new JLabel();
    private final JLabel onOffValueLabel = new JLabel();
    private final JLabel onThresholdLabel = new JLabel();
    private final JLabel offThresholdLabel = new JLabel();
    private final JLabel scanRateValueLabel = new JLabel();
    private final JLabel scanRateDetailLabel = new JLabel();
    private final JLabel timestampSubValueLabel = new JLabel();
    private final JLabel subTimestampTimingLabel = new JLabel();
    private final JScrollPane scrollPane;
    private final ScrollablePanel contentPanel;
    private boolean updatingFromConfig;

    public NRVUserControlPanel(NRVConfig config) {
        super(new BorderLayout());
        this.config = config;

        thresholdTweaker.setTweakDescription("");
        thresholdTweaker.setLessDescription("Lower");
        thresholdTweaker.setMoreDescription("Higher");
        thresholdTweaker.setToolTipText("<html>Right: lower 0x0167 / higher 0x0168 → raise both |Θ| (like DVS diffOn/diffOff).<br>"
                + "Does not change 0x0166 (K_REF). Changes are live; .txt file unchanged.");
        configurePotTweaker(thresholdTweaker);

        onOffBalanceTweaker.setTweakDescription("");
        onOffBalanceTweaker.setLessDescription("More OFF");
        onOffBalanceTweaker.setMoreDescription("More ON");
        onOffBalanceTweaker.setToolTipText("<html>Right: raise both ON/OFF LSBs → lower Θ_ON, higher |Θ_OFF|.<br>"
                + "Independent of event threshold. K_REF (0x0166) unchanged.");
        configurePotTweaker(onOffBalanceTweaker);

        scanRateSlider.setMajorTickSpacing(500);
        scanRateSlider.setMinorTickSpacing(100);
        scanRateSlider.setPaintTicks(true);
        scanRateSlider.setPaintLabels(false);
        scanRateSlider.setToolTipText("<html>Nominal sensor scan / frame-end rate (100–2000 Hz).<br>"
                + "Interpolates Scan Rate Setting block between factory 100 / 1000 / 2000 anchors.<br>"
                + "Also sets TSTAMP_SUB. Verify from USB frame-end (0x0C) packets.");

        timestampSubSlider.setMajorTickSpacing(0x20);
        timestampSubSlider.setPaintTicks(true);
        timestampSubSlider.setToolTipText("<html>Register 0x32B2 — sub-timestamp USB packet rate within each ref ms.<br>"
                + "Auto-updated with scan rate; fine-tune here. Factory: 100→0x0B … 1000→0x7D.");

        scanRateSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                onScanRateChanged();
            }
        });
        timestampSubSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                onTimestampSubChanged();
            }
        });
        thresholdTweaker.addChangeListener(e -> onThresholdChanged());
        onOffBalanceTweaker.addChangeListener(e -> onOnOffBalanceChanged());

        config.getSupport().addPropertyChangeListener(this);
        config.getSupport().addPropertyChangeListener(thresholdTweaker);
        config.getSupport().addPropertyChangeListener(onOffBalanceTweaker);

        contentPanel = new ScrollablePanel();
        contentPanel.setBorder(new EmptyBorder(4, 8, 2, 8));
        contentPanel.add(buildBiasSection());
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(buildTimingSection());

        scrollPane = new JScrollPane(new TopAlignedScrollView(contentPanel));
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override
            public void ancestorAdded(javax.swing.event.AncestorEvent evt) {
                SwingUtilities.invokeLater(() -> {
                    relayoutContent();
                    logViewportSize("shown");
                });
            }

            @Override
            public void ancestorRemoved(javax.swing.event.AncestorEvent evt) {
            }

            @Override
            public void ancestorMoved(javax.swing.event.AncestorEvent evt) {
            }
        });
        scrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                logViewportSize("resized");
            }
        });

        syncFromConfig();
        relayoutContent();
    }

    /** Recompute sizes after labels are populated; safe to call on show/resize. */
    private void relayoutContent() {
        stretchChildren(contentPanel);
        stretchTree(contentPanel);
        contentPanel.revalidate();
        contentPanel.repaint();
        scrollPane.getViewport().getView().revalidate();
        scrollPane.revalidate();
    }

    private void logViewportSize(String reason) {
        final Dimension vp = scrollPane.getViewport().getSize();
        final Dimension pref = scrollPane.getViewport().getView().getPreferredSize();
        log.info(String.format("NRVUserControlPanel %s: viewport=%dx%d contentPref=%dx%d panel=%dx%d",
                reason, vp.width, vp.height, pref.width, pref.height, getWidth(), getHeight()));
    }

    private static void configurePotTweaker(PotTweaker tweaker) {
        tweaker.setBorder(null);
        tweaker.getSlider().setPaintLabels(false);
        tweaker.setPreferredSize(new Dimension(200, 40));
        tweaker.setMinimumSize(new Dimension(0, 40));
    }

    private JPanel buildBiasSection() {
        final JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Event thresholds (EVTH)"),
                new EmptyBorder(4, 4, 4, 4)));
        section.setToolTipText(BIAS_SECTION_TOOLTIP);

        section.add(wrapPotTweaker("Event threshold", thresholdTweaker, thresholdValueLabel));
        section.add(Box.createVerticalStrut(6));
        section.add(wrapPotTweaker("ON / OFF balance", onOffBalanceTweaker, onOffValueLabel));
        section.add(Box.createVerticalStrut(8));
        section.add(centerComponent(buildThresholdReadoutRow()));
        stretchChildren(section);
        return section;
    }

    private static JPanel centerComponent(JComponent component) {
        final JPanel wrap = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
        wrap.add(component);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        return wrap;
    }

    private JPanel buildTimingSection() {
        final JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createTitledBorder("Timing / readout"));
        section.setToolTipText(TIMING_SECTION_TOOLTIP);

        section.add(wrapRegisterSlider("Scan rate (100–2000 Hz)", scanRateSlider, scanRateValueLabel));
        section.add(Box.createVerticalStrut(2));
        section.add(wrapDetailLabel(scanRateDetailLabel));
        section.add(Box.createVerticalStrut(8));
        section.add(wrapRegisterSlider("Sub-timestamp (0x32B2)", timestampSubSlider, timestampSubValueLabel));
        section.add(Box.createVerticalStrut(2));
        section.add(wrapDetailLabel(subTimestampTimingLabel));
        stretchChildren(section);
        return section;
    }

    private JPanel buildThresholdReadoutRow() {
        final JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        onThresholdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        offThresholdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(onThresholdLabel);
        inner.add(Box.createVerticalStrut(2));
        inner.add(offThresholdLabel);

        final JPanel row = new JPanel(new BorderLayout());
        row.setBorder(BorderFactory.createTitledBorder("Estimated thresholds"));
        row.add(inner, BorderLayout.CENTER);
        stretchChildren(row);
        return row;
    }

    private static JPanel wrapPotTweaker(String title, PotTweaker tweaker, JLabel valueLabel) {
        final JPanel row = new JPanel(new BorderLayout(4, 2));
        final JLabel name = new JLabel(title);
        name.setToolTipText(tweaker.getToolTipText());
        row.add(name, BorderLayout.NORTH);
        row.add(tweaker, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.SOUTH);
        return row;
    }

    private static JPanel wrapDetailLabel(JLabel label) {
        final JPanel row = new JPanel(new BorderLayout());
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private static JPanel wrapRegisterSlider(String title, JSlider slider, JLabel valueLabel) {
        final JPanel row = new JPanel(new BorderLayout(4, 2));
        final JLabel name = new JLabel(title);
        name.setToolTipText(slider.getToolTipText());
        row.add(name, BorderLayout.NORTH);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.SOUTH);
        return row;
    }

    /** BoxLayout children expand to viewport width; height must stay unconstrained. */
    private static void stretchChildren(JPanel panel) {
        for (Component child : panel.getComponents()) {
            if (child instanceof javax.swing.JComponent) {
                stretchHorizontal((javax.swing.JComponent) child);
            }
        }
    }

    private static void stretchHorizontal(javax.swing.JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
    }

    private static void stretchTree(JPanel panel) {
        for (Component child : panel.getComponents()) {
            if (child instanceof JPanel) {
                stretchChildren((JPanel) child);
                stretchTree((JPanel) child);
            }
        }
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

    private void onScanRateChanged() {
        if (updatingFromConfig) {
            return;
        }
        final int hz = scanRateSlider.getValue();
        if (scanRateSlider.getValueIsAdjusting()) {
            scanRateValueLabel.setText(hz + " Hz (preview)");
            updateScanRateDetailPreview(hz);
            return;
        }
        config.setScanRateHz(hz);
        updateValueLabels();
    }

    private void onTimestampSubChanged() {
        if (updatingFromConfig) {
            return;
        }
        if (timestampSubSlider.getValueIsAdjusting()) {
            timestampSubValueLabel.setText(String.format("<html>0x32B2 = 0x%02X<br>(file: 0x%02X)",
                    timestampSubSlider.getValue(), config.getBaselineTimestampSub()));
            updateSubTimestampTimingLabel(timestampSubSlider.getValue());
            return;
        }
        config.setTimestampSubUnit(timestampSubSlider.getValue());
        updateValueLabels();
    }

    void syncFromConfig() {
        updatingFromConfig = true;
        thresholdTweaker.setValue(config.getThresholdTweak());
        onOffBalanceTweaker.setValue(config.getOnOffBalanceTweak());
        syncTimingSliders();
        updatingFromConfig = false;
        updateValueLabels();
    }

    private void syncTimingSliders() {
        scanRateSlider.setValue(clamp(config.getScanRateHz(),
                NRVConfig.SCAN_RATE_HZ_MIN, NRVConfig.SCAN_RATE_HZ_MAX, 300));
        final int sub = clamp(config.getTimestampSubUnit(), SUB_UNIT_MIN, SUB_UNIT_MAX, config.getBaselineTimestampSub());
        timestampSubSlider.setValue(sub);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    void updateValueLabels() {
        thresholdValueLabel.setText(String.format(
                "<html>0x0167=0x%02X, 0x0168=0x%02X<br>(file: 0x%02X, 0x%02X)",
                config.getRegisterValue(NRVConfig.REG_ON_UNIT),
                config.getRegisterValue(NRVConfig.REG_OFF_UNIT),
                config.getBaselineOnUnit(),
                config.getBaselineOffUnit()));
        onOffValueLabel.setText(String.format("<html><center>balance → 0x0167=0x%02X, 0x0168=0x%02X</center>",
                config.getRegisterValue(NRVConfig.REG_ON_UNIT),
                config.getRegisterValue(NRVConfig.REG_OFF_UNIT)));
        updateThresholdReadoutLabels();

        scanRateValueLabel.setText(config.getScanRateHz() + " Hz");
        updateScanRateDetailLabel();

        timestampSubValueLabel.setText(String.format("<html>0x32B1:32B2 = 0x%04X<br>(LSB file: 0x%02X)",
                config.getTimestampSubUnitCombined(),
                config.getBaselineTimestampSub()));
        updateSubTimestampTimingLabel(config.getTimestampSubUnit());
    }

    private void updateScanRateDetailPreview(int hz) {
        scanRateDetailLabel.setText(String.format(
                "<html>Preview ~%d Hz; TSTAMP_SUB→0x%02X.",
                hz, NRVConfig.timestampSubForScanRateHz(hz)));
    }

    private void updateScanRateDetailLabel() {
        final int margin = config.getFrameMarginCombined();
        final float padUs = config.getFrmMarginPaddingUsForMargin(margin);
        final String pad = Float.isNaN(padUs) ? "—" : String.format("%.2f ms", padUs / 1000f);
        scanRateDetailLabel.setText(String.format(
                "<html>FRM_MARGIN 0x%04X (pad %s)<br>SELX 0x%02X, SENSE 0x%02X, COL 0x%02X, MODE 0x%02X",
                margin, pad,
                config.getRegisterValue(NRVConfig.REG_DTAG_SELX),
                config.getRegisterValue(NRVConfig.REG_DTAG_SENSE),
                config.getRegisterValue(NRVConfig.REG_DTAG_COL_MARGIN),
                config.getRegisterValue(NRVConfig.REG_DTAG_MODE)));
    }

    private void updateThresholdReadoutLabels() {
        final float onLogE = config.getOnThresholdLogE();
        final float offLogE = config.getOffThresholdLogE();
        if (Float.isNaN(onLogE)) {
            onThresholdLabel.setText("ON: —");
        } else {
            final float onPct = NRVConfig.logThresholdToPercentChange(onLogE);
            onThresholdLabel.setText(String.format(
                    "<html><center>ON: %.2f e-folds (%.2f%%)<br>K_ON/K_REF = %.2g</center>",
                    onLogE, onPct, config.getKOn() / config.getKRef()));
        }
        if (Float.isNaN(offLogE)) {
            offThresholdLabel.setText("OFF: —");
        } else {
            final float offPct = NRVConfig.logThresholdToPercentChange(offLogE);
            offThresholdLabel.setText(String.format(
                    "<html><center>OFF: %.2f e-folds (%.2f%%)<br>K_OFF/K_REF = %.2g</center>",
                    offLogE, offPct, config.getKOff() / config.getKRef()));
        }
    }

    private void updateSubTimestampTimingLabel(int subUnit) {
        final float subUs = config.getSubTimestampIntervalUsForSubUnit(subUnit);
        if (Float.isNaN(subUs)) {
            subTimestampTimingLabel.setText("Sub-timestamp interval: —");
        } else {
            subTimestampTimingLabel.setText(String.format(
                    "<html>Sub-timestamp interval: %.2f µs<br>(REF 0x%04X / SUB 0x%02X)",
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
        } else if (NRVConfig.PROPERTY_SCAN_RATE_HZ.equals(name)) {
            updatingFromConfig = true;
            scanRateSlider.setValue((Integer) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (NRVConfig.PROPERTY_TIMESTAMP_SUB.equals(name)) {
            updatingFromConfig = true;
            timestampSubSlider.setValue((Integer) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (NRVConfig.PROPERTY_FRAME_MARGIN.equals(name)) {
            updateValueLabels();
        } else if (NRVConfig.PROPERTY_REGISTER_UPDATED.equals(name)) {
            final Object src = evt.getNewValue();
            if (src instanceof NRVRegisterSetting) {
                final int addr = ((NRVRegisterSetting) src).getRegAddr();
                if (addr == NRVConfig.REG_TSTAMP_SUB_UNIT_LSB || addr == NRVConfig.REG_TSTAMP_SUB_UNIT_MSB
                        || addr == NRVConfig.REG_DTAG_FRM_MARGIN_LSB || addr == NRVConfig.REG_DTAG_FRM_MARGIN_MSB
                        || addr == NRVConfig.REG_DTAG_SELX || addr == NRVConfig.REG_DTAG_SENSE
                        || addr == NRVConfig.REG_DTAG_MODE) {
                    updatingFromConfig = true;
                    syncTimingSliders();
                    updatingFromConfig = false;
                }
            }
            updateValueLabels();
        }
    }

    /** Content height is natural; pins to top so empty viewport area stays minimal. */
    private static final class TopAlignedScrollView extends JPanel implements Scrollable {

        private final JComponent content;

        TopAlignedScrollView(JComponent content) {
            super(new BorderLayout());
            this.content = content;
            add(content, BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredSize() {
            final Insets ins = getInsets();
            final Dimension cd = content.getPreferredSize();
            return new Dimension(cd.width + ins.left + ins.right, cd.height + ins.top + ins.bottom);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 16, 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Vertical scroll when short; width tracks the viewport (no horizontal scroll). */
    private static final class ScrollablePanel extends JPanel implements Scrollable {

        ScrollablePanel() {
            super();
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 16, 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
