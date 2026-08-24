package ch.unizh.ini.jaer.chip.retina;

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
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.PotTweaker;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Shared user-friendly DVS tweak sliders plus quantitative estimate labels
 * when {@link DVSTweaks} returns finite values.
 */
public class DVSUserControlPanel extends JPanel implements PropertyChangeListener {

    /** Default Biasgen-tab width; sliders track the viewport if the frame is resized. */
    static final int PREFERRED_PANEL_WIDTH = 520;

    private static final String HELP_HTML = "<html>This panel tweaks bias values around the nominal ones loaded from "
            + "the XML/preferences file. <b>Changes are not permanent</b> until settings are saved. "
            + "On restart after save, these become the new nominal (slider center).";

    protected final AEChip chip;
    protected final DVSTweaks tweaks;
    protected final PotTweaker thresholdTweaker = new PotTweaker();
    protected final PotTweaker onOffBalanceTweaker = new PotTweaker();
    protected final PotTweaker bandwidthTweaker = new PotTweaker();
    protected final PotTweaker maxFiringRateTweaker = new PotTweaker();
    protected final JLabel thresholdEstimateLabel = new JLabel();
    protected final JLabel bandwidthEstimateLabel = new JLabel();
    protected final JLabel refractoryEstimateLabel = new JLabel();
    protected final JPanel extraControls = new JPanel();
    protected boolean updatingFromConfig;
    private final EngineeringFormat eng = new EngineeringFormat();
    private final JPanel thresholdRow = new JPanel();
    private final JPanel onOffRow = new JPanel();
    private final JPanel bandwidthRow = new JPanel();
    private final JPanel refractoryRow = new JPanel();
    private final JPanel thresholdEstimateRow = new JPanel();
    private final JPanel bandwidthEstimateRow = new JPanel();
    private final JPanel refractoryEstimateRow = new JPanel();

    public DVSUserControlPanel(AEChip chip) {
        this(chip, chip != null && chip.getBiasgen() instanceof DVSTweaks t ? t : null, true);
    }

    public DVSUserControlPanel(AEChip chip, DVSTweaks tweaks) {
        this(chip, tweaks, true);
    }

    /**
     * @param finishInit false when a subclass still has fields to assign; call
     * {@link #finishInit()} at the end of the subclass constructor.
     */
    protected DVSUserControlPanel(AEChip chip, DVSTweaks tweaks, boolean finishInit) {
        super(new BorderLayout());
        this.chip = chip;
        this.tweaks = tweaks;
        extraControls.setLayout(new BoxLayout(extraControls, BoxLayout.Y_AXIS));
        extraControls.setAlignmentX(Component.LEFT_ALIGNMENT);
        extraControls.setOpaque(false);
        if (finishInit) {
            finishInit();
        }
    }

    protected final void finishInit() {
        configureTweakers();
        thresholdTweaker.addChangeListener(e -> onThresholdChanged());
        onOffBalanceTweaker.addChangeListener(e -> onOnOffBalanceChanged());
        bandwidthTweaker.addChangeListener(e -> onBandwidthChanged());
        maxFiringRateTweaker.addChangeListener(e -> onMaxFiringRateChanged());

        if (chip != null && chip.getBiasgen() != null) {
            chip.getBiasgen().getSupport().addPropertyChangeListener(this);
            chip.getBiasgen().getSupport().addPropertyChangeListener(thresholdTweaker);
            chip.getBiasgen().getSupport().addPropertyChangeListener(onOffBalanceTweaker);
            chip.getBiasgen().getSupport().addPropertyChangeListener(bandwidthTweaker);
            chip.getBiasgen().getSupport().addPropertyChangeListener(maxFiringRateTweaker);
        }
        if (chip != null) {
            chip.getSupport().addPropertyChangeListener(this);
        }

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(4, 8, 8, 8));
        content.add(helpLabel());
        content.add(Box.createVerticalStrut(8));

        buildTweakerRow(thresholdRow, thresholdTweaker);
        buildTweakerRow(onOffRow, onOffBalanceTweaker);
        buildTweakerRow(bandwidthRow, bandwidthTweaker);
        buildTweakerRow(refractoryRow, maxFiringRateTweaker);
        buildEstimateRow(thresholdEstimateRow, "Est. DVS thresholds", thresholdEstimateLabel,
                "<html>Computed ON/OFF temporal-contrast thresholds (log-e and %).");
        buildEstimateRow(bandwidthEstimateRow, "Est. photoreceptor bandwidth", bandwidthEstimateLabel,
                "<html>Theoretical source-follower bandwidth when that stage dominates.");
        buildEstimateRow(refractoryEstimateRow, "Est. refractory period", refractoryEstimateLabel,
                "<html>Estimated refractory period from capacitance and refractory current.");

        content.add(bandwidthRow);
        content.add(bandwidthEstimateRow);
        content.add(Box.createVerticalStrut(4));
        content.add(thresholdRow);
        content.add(thresholdEstimateRow);
        content.add(Box.createVerticalStrut(4));
        content.add(onOffRow);
        content.add(Box.createVerticalStrut(4));
        content.add(refractoryRow);
        content.add(refractoryEstimateRow);
        content.add(Box.createVerticalStrut(6));
        addExtraControls(extraControls);
        content.add(extraControls);

        applyCapabilityVisibility();
        stretchChildren(content);

        add(widthTrackingScrollPane(content), BorderLayout.CENTER);
        int contentH = content.getPreferredSize().height + 32;
        setPreferredSize(new Dimension(PREFERRED_PANEL_WIDTH, Math.min(Math.max(contentH, 400), 720)));
        setMinimumSize(new Dimension(320, 280));
        syncFromTweaks();
    }

    protected String helpHtml() {
        return HELP_HTML;
    }

    private JLabel helpLabel() {
        JLabel help = new JLabel(htmlWrapped(helpHtml(), PREFERRED_PANEL_WIDTH - 48));
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        return help;
    }

    protected void configureTweakers() {
        configurePotTweaker(thresholdTweaker, "Threshold", "Lower/more events", "Higher/less events",
                "Adjusts event threshold");
        configurePotTweaker(onOffBalanceTweaker, "On/Off balance", "More Off events", "More On events",
                "Adjusts balance between On and Off events");
        configurePotTweaker(bandwidthTweaker, "Bandwidth", "Slower", "Faster",
                "Tweaks bandwidth of pixel front end.");
        configurePotTweaker(maxFiringRateTweaker, "Maximum firing rate", "Slower", "Faster",
                "Adjusts maximum pixel firing rate (1/refractory period)");
    }

    protected void configurePotTweaker(PotTweaker tweaker, String name, String less, String more, String desc) {
        tweaker.setName(name);
        tweaker.setLessDescription(less);
        tweaker.setMoreDescription(more);
        tweaker.setTweakDescription(desc);
        tweaker.getSlider().setPaintLabels(true);
        tweaker.setPreferredSize(new Dimension(200, 88));
        tweaker.setMinimumSize(new Dimension(0, 80));
        tweaker.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /** Chip-specific extra controls (high-pass, scan rate, …). */
    protected void addExtraControls(JPanel extra) {
    }

    /** Extra estimate lines after the common ON/OFF / BW / refractory readout. */
    protected void updateChipSpecificLabels() {
    }

    private void buildTweakerRow(JPanel row, PotTweaker tweaker) {
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(tweaker);
        stretchHorizontal(tweaker);
        stretchHorizontal(row);
    }

    private void buildEstimateRow(JPanel row, String title, JLabel value, String tip) {
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 4));
        JLabel name = new JLabel(title + "  ");
        name.setToolTipText(tip);
        value.setFont(new Font(Font.MONOSPACED, Font.PLAIN, value.getFont().getSize()));
        value.setToolTipText(tip);
        row.add(name);
        row.add(value);
        row.add(Box.createHorizontalGlue());
        stretchHorizontal(row);
    }

    private void applyCapabilityVisibility() {
        boolean thr = tweaks == null || tweaks.supportsThresholdTweak();
        boolean bal = tweaks == null || tweaks.supportsOnOffBalanceTweak();
        boolean bw = tweaks == null || tweaks.supportsBandwidthTweak();
        boolean refr = tweaks == null || tweaks.supportsMaxFiringRateTweak();
        thresholdRow.setVisible(thr);
        onOffRow.setVisible(bal);
        bandwidthRow.setVisible(bw);
        refractoryRow.setVisible(refr);
    }

    protected void onThresholdChanged() {
        if (updatingFromConfig || tweaks == null) {
            return;
        }
        tweaks.setThresholdTweak(thresholdTweaker.getValue());
        setFileModified();
        updateEstimates();
    }

    protected void onOnOffBalanceChanged() {
        if (updatingFromConfig || tweaks == null) {
            return;
        }
        tweaks.setOnOffBalanceTweak(onOffBalanceTweaker.getValue());
        setFileModified();
        updateEstimates();
    }

    protected void onBandwidthChanged() {
        if (updatingFromConfig || tweaks == null) {
            return;
        }
        tweaks.setBandwidthTweak(bandwidthTweaker.getValue());
        setFileModified();
        updateEstimates();
    }

    protected void onMaxFiringRateChanged() {
        if (updatingFromConfig || tweaks == null) {
            return;
        }
        tweaks.setMaxFiringRateTweak(maxFiringRateTweaker.getValue());
        setFileModified();
        updateEstimates();
    }

    private static float tweakOrZero(FloatSupplier getter) {
        try {
            return getter.getAsFloat();
        } catch (UnsupportedOperationException e) {
            return 0f;
        }
    }

    @FunctionalInterface
    private interface FloatSupplier {
        float getAsFloat();
    }

    protected void setFileModified() {
        if (chip != null && chip.getAeViewer() != null && chip.getAeViewer().getBiasgenFrame() != null) {
            chip.getAeViewer().getBiasgenFrame().setFileModified(true);
        }
    }

    protected void syncFromTweaks() {
        if (tweaks == null) {
            return;
        }
        updatingFromConfig = true;
        try {
            thresholdTweaker.setValue(tweakOrZero(() -> tweaks.getThresholdTweak()));
            onOffBalanceTweaker.setValue(tweakOrZero(() -> tweaks.getOnOffBalanceTweak()));
            bandwidthTweaker.setValue(tweakOrZero(() -> tweaks.getBandwidthTweak()));
            maxFiringRateTweaker.setValue(tweakOrZero(() -> tweaks.getMaxFiringRateTweak()));
        } finally {
            updatingFromConfig = false;
        }
        updateEstimates();
    }

    protected void updateEstimates() {
        if (tweaks == null) {
            return;
        }
        float on = tweaks.getOnThresholdLogE();
        float off = tweaks.getOffThresholdLogE();
        if (Float.isFinite(on) && Float.isFinite(off)) {
            float onPct = (float) (100 * (Math.exp(on) - 1));
            float offPct = (float) (100 * (Math.exp(off) - 1));
            thresholdEstimateLabel.setText(String.format("ON %.3f e-folds (%.1f%%)   OFF %.3f e-folds (%.1f%%)   ON+OFF %.3f",
                    on, onPct, off, offPct, on + off));
            thresholdEstimateRow.setVisible(true);
        } else {
            thresholdEstimateLabel.setText("—");
            thresholdEstimateRow.setVisible(false);
        }
        float bw = tweaks.getPhotoreceptorSourceFollowerBandwidthHz();
        if (Float.isFinite(bw) && tweaks.supportsBandwidthTweak()) {
            bandwidthEstimateLabel.setText(eng.format(bw) + "Hz");
            bandwidthEstimateRow.setVisible(true);
        } else {
            bandwidthEstimateLabel.setText("—");
            bandwidthEstimateRow.setVisible(false);
        }
        float refr = tweaks.getRefractoryPeriodS();
        if (Float.isFinite(refr) && tweaks.supportsMaxFiringRateTweak()) {
            refractoryEstimateLabel.setText(eng.format(refr) + "s");
            refractoryEstimateRow.setVisible(true);
        } else {
            refractoryEstimateLabel.setText("—");
            refractoryEstimateRow.setVisible(false);
        }
        updateChipSpecificLabels();
    }

    private static void stretchChildren(JPanel panel) {
        for (Component child : panel.getComponents()) {
            if (child instanceof JComponent jc) {
                stretchHorizontal(jc);
            }
        }
    }

    protected static void stretchHorizontal(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        int h = Math.max(c.getPreferredSize().height, 1);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    /** Wrap HTML so a JLabel does not force {@code pack()} to a single long line. */
    static String htmlWrapped(String html, int widthPx) {
        if (html == null) {
            return "";
        }
        String body = html;
        if (body.regionMatches(true, 0, "<html>", 0, 6)) {
            body = body.substring(6);
        }
        return "<html><body style='width:" + widthPx + "px'>" + body;
    }

    /**
     * Vertical scroll; width tracks the viewport so {@link PotTweaker} sliders
     * follow the Biasgen frame instead of staying at their preferred width.
     */
    /** Keep {@code pack()} from using a wide expert/raw tab as the default frame size. */
    public static void capTabbedPanePreferredWidth(JTabbedPane tabs) {
        if (tabs == null) {
            return;
        }
        Dimension pref = tabs.getPreferredSize();
        tabs.setPreferredSize(new Dimension(PREFERRED_PANEL_WIDTH, pref.height));
    }

    static JScrollPane widthTrackingScrollPane(JComponent content) {
        return scrollPane(content, true);
    }

    /**
     * Vertical scroll that shrinks with a narrow viewport but does not stretch
     * form controls across a wide Biasgen frame.
     */
    static JScrollPane compactScrollPane(JComponent content) {
        return scrollPane(content, false);
    }

    private static JScrollPane scrollPane(JComponent content, boolean expandToViewport) {
        JScrollPane scroll = new JScrollPane(new WidthTrackingTopView(content, expandToViewport));
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Content stays top-aligned; width follows the scroll viewport. */
    static final class WidthTrackingTopView extends JPanel implements Scrollable {

        private final JComponent content;
        private final boolean expandToViewport;

        WidthTrackingTopView(JComponent content, boolean expandToViewport) {
            super(new BorderLayout());
            this.content = content;
            this.expandToViewport = expandToViewport;
            add(content, BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredSize() {
            Insets ins = getInsets();
            Dimension cd = content.getPreferredSize();
            return new Dimension(Math.min(cd.width, PREFERRED_PANEL_WIDTH) + ins.left + ins.right,
                    cd.height + ins.top + ins.bottom);
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
            if (expandToViewport) {
                return true;
            }
            if (getParent() instanceof javax.swing.JViewport vp) {
                return vp.getWidth() < getPreferredSize().width;
            }
            return false;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String name = evt.getPropertyName();
        if (Biasgen.PROPERTY_CHANGE_PREFERENCES_LOADED.equals(name)
                || Biasgen.PROPERTY_CHANGE_PREFERENCES_STORED.equals(name)) {
            syncFromTweaks();
            return;
        }
        if (DVSTweaks.THRESHOLD.equals(name) && evt.getNewValue() instanceof Float v) {
            updatingFromConfig = true;
            thresholdTweaker.setValue(v);
            updatingFromConfig = false;
            updateEstimates();
        } else if (DVSTweaks.ON_OFF_BALANCE.equals(name) && evt.getNewValue() instanceof Float v) {
            updatingFromConfig = true;
            onOffBalanceTweaker.setValue(v);
            updatingFromConfig = false;
            updateEstimates();
        } else if (DVSTweaks.BANDWIDTH.equals(name) && evt.getNewValue() instanceof Float v) {
            updatingFromConfig = true;
            bandwidthTweaker.setValue(v);
            updatingFromConfig = false;
            updateEstimates();
        } else if (DVSTweaks.MAX_FIRING_RATE.equals(name) && evt.getNewValue() instanceof Float v) {
            updatingFromConfig = true;
            maxFiringRateTweaker.setValue(v);
            updatingFromConfig = false;
            updateEstimates();
        }
    }
}
