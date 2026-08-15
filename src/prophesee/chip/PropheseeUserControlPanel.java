package prophesee.chip;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.PotTweaker;
import prophesee.usb.PropheseeBiases;

/**
 * User-friendly IMX636 bias tweaks around last loaded/saved preference values.
 * Digital→physical mapping is not claimed; sliders are abstract −1…1 offsets.
 *
 * @see https://docs.prophesee.ai/stable/hw/manuals/biases.html
 */
public class PropheseeUserControlPanel extends JPanel implements PropertyChangeListener {

    private static final String HELP_HTML = "<html>This panel tweaks bias values around the nominal ones loaded from "
            + "preferences/XML. <b>Changes are not permanent</b> until settings are saved. "
            + "On save (or restart after save), these become the new nominal (slider center).<br>"
            + "Values are abstract digital offsets — not calibrated threshold % or filter Hz. "
            + "Use <b>Undo/Redo</b> in the Biases toolbar; <b>File→Revert</b> restores the last save.";

    private final PropheseeConfig config;
    private final PotTweaker thresholdTweaker = new PotTweaker();
    private final PotTweaker onOffBalanceTweaker = new PotTweaker();
    private final PotTweaker bandwidthTweaker = new PotTweaker();
    private final PotTweaker highpassTweaker = new PotTweaker();
    private final JLabel thresholdValueLabel = new JLabel();
    private final JLabel onOffValueLabel = new JLabel();
    private final JLabel bandwidthValueLabel = new JLabel();
    private final JLabel highpassValueLabel = new JLabel();
    private boolean updatingFromConfig;

    public PropheseeUserControlPanel(PropheseeConfig config) {
        super(new BorderLayout());
        this.config = config;

        thresholdTweaker.setName("Brightness change threshold");
        thresholdTweaker.setLessDescription("Lower / more events");
        thresholdTweaker.setMoreDescription("Higher / less events");
        thresholdTweaker.setTweakDescription(
                "Raises both ON and OFF contrast thresholds (IMX636: increase diff_on and diff_off).");
        thresholdTweaker.setTooltip("<html>Right: higher |Θ|, fewer events.<br>"
                + "Unlike DAVIS analog pots, both <i>diff_on</i> and <i>diff_off</i> increase together.<br>"
                + "Center = last saved/loaded values. Range uses Metavision IMX636 offsets.");
        configurePotTweaker(thresholdTweaker);

        onOffBalanceTweaker.setName("ON/OFF balance");
        onOffBalanceTweaker.setLessDescription("More OFF");
        onOffBalanceTweaker.setMoreDescription("More ON");
        onOffBalanceTweaker.setTweakDescription(
                "Shifts ON vs OFF threshold: right lowers ON threshold and raises OFF threshold.");
        onOffBalanceTweaker.setTooltip("<html>Right: more ON / fewer OFF events "
                + "(decrease <i>diff_on</i>, increase <i>diff_off</i>).<br>"
                + "Independent of the threshold slider; both apply from the saved baseline.");
        configurePotTweaker(onOffBalanceTweaker);

        bandwidthTweaker.setName("Pixel low-pass");
        bandwidthTweaker.setLessDescription("Slower");
        bandwidthTweaker.setMoreDescription("Faster");
        bandwidthTweaker.setTweakDescription(
                "Pixel low-pass (bias_fo): faster = wider bandwidth, shorter τ_LP, more flicker/noise.");
        bandwidthTweaker.setTooltip("<html>Maps to <i>bias_fo</i>. Increase fo to widen bandwidth "
                + "(shorter low-pass time constant).<br>"
                + "Used to cut flicker; also raises noise and lowers latency. "
                + "Extent ± from saved: −35…+55 (Metavision IMX636).");
        configurePotTweaker(bandwidthTweaker);

        highpassTweaker.setName("Pixel high-pass");
        highpassTweaker.setLessDescription("Pass slow changes");
        highpassTweaker.setMoreDescription("Reject slow / background");
        highpassTweaker.setTweakDescription(
                "Pixel high-pass (bias_hpf): right rejects more slow/DC change (shorter τ_HP).");
        highpassTweaker.setTooltip("<html>Maps to <i>bias_hpf</i>. Increase hpf to filter slow illumination "
                + "and background noise.<br>"
                + "Factory default is typically 0; the left side is a no-op until a positive hpf is saved. "
                + "Extent from saved: 0…+120.");
        configurePotTweaker(highpassTweaker);

        thresholdTweaker.addChangeListener(e -> onThresholdChanged());
        onOffBalanceTweaker.addChangeListener(e -> onOnOffBalanceChanged());
        bandwidthTweaker.addChangeListener(e -> onBandwidthChanged());
        highpassTweaker.addChangeListener(e -> onHighpassChanged());

        config.getSupport().addPropertyChangeListener(this);
        config.getSupport().addPropertyChangeListener(thresholdTweaker);
        config.getSupport().addPropertyChangeListener(onOffBalanceTweaker);
        config.getSupport().addPropertyChangeListener(bandwidthTweaker);
        config.getSupport().addPropertyChangeListener(highpassTweaker);

        final JPanel content = new ScrollablePanel();
        content.setBorder(new EmptyBorder(4, 8, 8, 8));
        content.add(helpLabel());
        content.add(Box.createVerticalStrut(8));
        content.add(wrapTweaker(thresholdTweaker, thresholdValueLabel));
        content.add(Box.createVerticalStrut(6));
        content.add(wrapTweaker(onOffBalanceTweaker, onOffValueLabel));
        content.add(Box.createVerticalStrut(6));
        content.add(wrapTweaker(bandwidthTweaker, bandwidthValueLabel));
        content.add(Box.createVerticalStrut(6));
        content.add(wrapTweaker(highpassTweaker, highpassValueLabel));
        stretchChildren(content);

        final JScrollPane scroll = new JScrollPane(new TopAlignedScrollView(content));
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        syncFromConfig();
    }

    private static JLabel helpLabel() {
        final JLabel help = new JLabel(HELP_HTML);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        return help;
    }

    private static void configurePotTweaker(PotTweaker tweaker) {
        tweaker.getSlider().setPaintLabels(true);
        tweaker.setPreferredSize(new Dimension(200, 88));
        tweaker.setMinimumSize(new Dimension(0, 72));
        tweaker.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static JPanel wrapTweaker(PotTweaker tweaker, JLabel valueLabel) {
        valueLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, valueLabel.getFont().getSize()));
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        final JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        row.add(tweaker);
        row.add(Box.createVerticalStrut(2));
        row.add(valueLabel);
        stretchHorizontal(tweaker);
        stretchHorizontal(valueLabel);
        stretchHorizontal(row);
        return row;
    }

    private static void stretchChildren(JPanel panel) {
        for (Component child : panel.getComponents()) {
            if (child instanceof JComponent jc) {
                stretchHorizontal(jc);
            }
        }
    }

    private static void stretchHorizontal(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
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

    private void onBandwidthChanged() {
        if (updatingFromConfig) {
            return;
        }
        config.setBandwidthTweak(bandwidthTweaker.getValue());
        updateValueLabels();
    }

    private void onHighpassChanged() {
        if (updatingFromConfig) {
            return;
        }
        config.setHighpassTweak(highpassTweaker.getValue());
        updateValueLabels();
    }

    void syncFromConfig() {
        updatingFromConfig = true;
        try {
            thresholdTweaker.setValue(config.getThresholdTweak());
            onOffBalanceTweaker.setValue(config.getOnOffBalanceTweak());
            bandwidthTweaker.setValue(config.getBandwidthTweak());
            highpassTweaker.setValue(config.getHighpassTweak());
        } finally {
            updatingFromConfig = false;
        }
        updateValueLabels();
    }

    void updateValueLabels() {
        final PropheseeBiases saved = config.getSavedBiases();
        thresholdValueLabel.setText(String.format(
                "diff_on %s    diff_off %s",
                hexOffset(config.getDiffOn(), saved.diffOn),
                hexOffset(config.getDiffOff(), saved.diffOff)));
        onOffValueLabel.setText(String.format(
                "diff_on %s    diff_off %s",
                hexOffset(config.getDiffOn(), saved.diffOn),
                hexOffset(config.getDiffOff(), saved.diffOff)));
        bandwidthValueLabel.setText("fo " + hexOffset(config.getFo(), saved.fo));
        highpassValueLabel.setText("hpf " + hexOffset(config.getHpf(), saved.hpf));
    }

    private static String hexOffset(int current, int saved) {
        return String.format("0x%02X (%+d from saved)", current, current - saved);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        final String name = evt.getPropertyName();
        if (Biasgen.PROPERTY_CHANGE_PREFERENCES_LOADED.equals(name)
                || Biasgen.PROPERTY_CHANGE_PREFERENCES_STORED.equals(name)) {
            syncFromConfig();
            return;
        }
        if (PropheseeConfig.PROPERTY_THRESHOLD_TWEAK.equals(name)) {
            updatingFromConfig = true;
            thresholdTweaker.setValue((Float) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (PropheseeConfig.PROPERTY_ON_OFF_BALANCE_TWEAK.equals(name)) {
            updatingFromConfig = true;
            onOffBalanceTweaker.setValue((Float) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (PropheseeConfig.PROPERTY_BANDWIDTH_TWEAK.equals(name)) {
            updatingFromConfig = true;
            bandwidthTweaker.setValue((Float) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (PropheseeConfig.PROPERTY_HIGHPASS_TWEAK.equals(name)) {
            updatingFromConfig = true;
            highpassTweaker.setValue((Float) evt.getNewValue());
            updatingFromConfig = false;
            updateValueLabels();
        } else if (PropheseeConfig.PROPERTY_DIFF_ON.equals(name)
                || PropheseeConfig.PROPERTY_DIFF_OFF.equals(name)
                || PropheseeConfig.PROPERTY_FO.equals(name)
                || PropheseeConfig.PROPERTY_HPF.equals(name)) {
            updateValueLabels();
        }
    }

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
