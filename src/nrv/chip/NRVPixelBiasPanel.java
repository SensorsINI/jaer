package nrv.chip;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEditSupport;

import net.sf.jaer.biasgen.Biasgen;
import nrv.usb.NRVRegisterSetting;

/**
 * Experimental sliders for unlabeled pixel-bias registers {@code 0x0160}–{@code 0x016B}
 * (excluding known EVTH LSBs {@code 0x0166}–{@code 0x0168}).
 * <p>
 * Slider drags post undoable edits to the Biases frame (same stack as the register table /
 * {@link net.sf.jaer.biasgen.PotTweaker}). <b>File→Revert</b> reloads the settings {@code .txt}
 * and restores these registers to sheet defaults.
 *
 * @see NRVConfig#PIXEL_BIAS_EXPERIMENTAL
 */
public class NRVPixelBiasPanel extends JPanel implements PropertyChangeListener {

    private static final Logger log = Logger.getLogger(NRVPixelBiasPanel.class.getName());

    private static final String SECTION_TOOLTIP = "<html>Unlabeled registers from NRV’s pixel-bias sheet "
            + "(<code>0x0160</code>–<code>0x016B</code>).<br>"
            + "Known EVTH LSBs <code>0x0166</code>–<code>0x0168</code> stay on the User-Friendly tab.<br>"
            + "<b>Undo/Redo</b> in the Biases toolbar; <b>File→Revert</b> reloads the .txt "
            + "and restores sheet defaults.<br>"
            + "Notes: <code>0x0161</code> → more OFF at low; <code>0x016A</code> → more ON at low.";

    private final NRVConfig config;
    private final Map<Integer, JSlider> sliders = new LinkedHashMap<>();
    private final Map<Integer, JLabel> valueLabels = new LinkedHashMap<>();
    private final Map<Integer, Integer> dragStartValues = new HashMap<>();
    private final UndoableEditSupport editSupport = new UndoableEditSupport();
    private boolean addedUndoListener;
    private boolean updatingFromConfig;

    public NRVPixelBiasPanel(NRVConfig config) {
        super(new BorderLayout());
        this.config = config;

        final ScrollablePanel content = new ScrollablePanel();
        content.setBorder(new EmptyBorder(8, 10, 8, 10));

        final JLabel intro = new JLabel("<html><b>Pixel biases (experimental)</b> — unlabeled "
                + "<code>0x0160</code>–<code>0x0165</code>, <code>0x0169</code>–<code>0x016B</code>");
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        intro.setToolTipText(SECTION_TOOLTIP);
        content.add(intro);
        content.add(Box.createVerticalStrut(8));

        final JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("0x0160–0x016B (unknown only)"),
                new EmptyBorder(4, 4, 4, 4)));
        section.setToolTipText(SECTION_TOOLTIP);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (NRVConfig.PixelBiasSpec spec : NRVConfig.PIXEL_BIAS_EXPERIMENTAL) {
            section.add(buildSliderRow(spec));
            section.add(Box.createVerticalStrut(6));
        }
        stretchHorizontal(section);
        content.add(section);

        final JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override
            public void ancestorAdded(javax.swing.event.AncestorEvent evt) {
                attachUndoListener(evt.getComponent());
            }

            @Override
            public void ancestorRemoved(javax.swing.event.AncestorEvent evt) {
            }

            @Override
            public void ancestorMoved(javax.swing.event.AncestorEvent evt) {
            }
        });

        config.getSupport().addPropertyChangeListener(this);
        syncFromConfig();
    }

    private void attachUndoListener(Component component) {
        if (addedUndoListener) {
            return;
        }
        Container anc = component instanceof Container ? (Container) component : null;
        while (anc != null) {
            if (anc instanceof UndoableEditListener) {
                editSupport.addUndoableEditListener((UndoableEditListener) anc);
                addedUndoListener = true;
                break;
            }
            anc = anc.getParent();
        }
    }

    private JPanel buildSliderRow(NRVConfig.PixelBiasSpec spec) {
        final JSlider slider = new JSlider(spec.min, spec.max, spec.defaultValue);
        slider.setMajorTickSpacing(Math.max(1, (spec.max - spec.min) / 4));
        slider.setPaintTicks(true);
        // Allow narrow windows: default JSlider min/preferred width is large with ticks.
        final int sliderH = Math.max(slider.getPreferredSize().height, 36);
        slider.setPreferredSize(new Dimension(120, sliderH));
        slider.setMinimumSize(new Dimension(0, sliderH));
        slider.setToolTipText(buildTooltip(spec));
        sliders.put(spec.address, slider);

        final JLabel valueLabel = new JLabel(" ", SwingConstants.LEFT);
        valueLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, valueLabel.getFont().getSize()));
        valueLabels.put(spec.address, valueLabel);

        final ChangeListener listener = e -> {
            if (updatingFromConfig) {
                return;
            }
            final int oldValue = config.getPixelBiasValue(spec.address);
            final int newValue = slider.getValue();
            if (oldValue == newValue) {
                return;
            }
            config.setPixelBiasValue(spec.address, newValue);
            updateValueLabel(spec.address);
            // Mouse drag: one undo unit on release. Keyboard / click: post immediately.
            if (!dragStartValues.containsKey(spec.address)) {
                postEdit(spec.address, oldValue, newValue);
            }
        };
        slider.addChangeListener(listener);
        slider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStartValues.put(spec.address, config.getPixelBiasValue(spec.address));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                final Integer start = dragStartValues.remove(spec.address);
                if (start == null) {
                    return;
                }
                final int end = config.getPixelBiasValue(spec.address);
                if (start != end) {
                    postEdit(spec.address, start, end);
                }
            }
        });

        final JLabel name = new JLabel(spec.uiLabel());
        name.setToolTipText(slider.getToolTipText());

        final JPanel row = new JPanel(new BorderLayout(4, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(name, BorderLayout.NORTH);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.SOUTH);
        stretchHorizontal(row);
        return row;
    }

    private void postEdit(int regAddr, int oldValue, int newValue) {
        if (oldValue == newValue) {
            return;
        }
        editSupport.postEdit(new PixelBiasEdit(config, regAddr, oldValue, newValue));
    }

    private static String buildTooltip(NRVConfig.PixelBiasSpec spec) {
        final StringBuilder sb = new StringBuilder("<html>");
        sb.append(String.format("Register 0x%04X — sheet default 0x%02X, range 0x%02X–0x%02X",
                spec.address, spec.defaultValue, spec.min, spec.max));
        if (spec.differenceNote != null) {
            sb.append("<br>").append(spec.differenceNote);
        }
        sb.append("<br>Undo/Redo via Biases toolbar; Revert restores sheet default.");
        return sb.toString();
    }

    private static void stretchHorizontal(javax.swing.JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        c.setMinimumSize(new Dimension(0, c.getPreferredSize().height));
    }

    /** Vertical scroll when short; width tracks the viewport (no horizontal clip). */
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

    void syncFromConfig() {
        updatingFromConfig = true;
        try {
            for (NRVConfig.PixelBiasSpec spec : NRVConfig.PIXEL_BIAS_EXPERIMENTAL) {
                final JSlider slider = sliders.get(spec.address);
                if (slider == null) {
                    continue;
                }
                final int value = spec.clamp(config.getPixelBiasValue(spec.address));
                slider.setValue(value);
                updateValueLabel(spec.address);
            }
        } finally {
            updatingFromConfig = false;
        }
    }

    private void updateValueLabel(int regAddr) {
        final JLabel label = valueLabels.get(regAddr);
        final NRVConfig.PixelBiasSpec spec = NRVConfig.findPixelBiasSpec(regAddr);
        if (label == null || spec == null) {
            return;
        }
        final int value = config.getPixelBiasValue(regAddr);
        label.setText(String.format("0x%04X = 0x%02X  (default 0x%02X, max 0x%02X)",
                regAddr, value, spec.defaultValue, spec.max));
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        final String name = evt.getPropertyName();
        if (Biasgen.PROPERTY_CHANGE_PREFERENCES_LOADED.equals(name)
                || NRVConfig.PROPERTY_PIXEL_BIAS.equals(name)) {
            syncFromConfig();
            return;
        }
        if (NRVConfig.PROPERTY_REGISTER_UPDATED.equals(name) && evt.getNewValue() instanceof NRVRegisterSetting) {
            final int addr = ((NRVRegisterSetting) evt.getNewValue()).getRegAddr();
            if (sliders.containsKey(addr)) {
                syncFromConfig();
            }
        }
    }

    private static final class PixelBiasEdit extends AbstractUndoableEdit {

        private final NRVConfig config;
        private final int regAddr;
        private final int oldValue;
        private final int newValue;

        PixelBiasEdit(NRVConfig config, int regAddr, int oldValue, int newValue) {
            this.config = config;
            this.regAddr = regAddr;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            applyValue(oldValue);
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            applyValue(newValue);
        }

        @Override
        public String getPresentationName() {
            return String.format("NRV pixel bias 0x%04X", regAddr);
        }

        private void applyValue(int value) {
            try {
                config.setPixelBiasValue(regAddr, value);
            } catch (RuntimeException e) {
                log.warning("NRV pixel bias undo/redo failed for 0x"
                        + Integer.toHexString(regAddr) + ": " + e.getMessage());
            }
        }
    }
}
