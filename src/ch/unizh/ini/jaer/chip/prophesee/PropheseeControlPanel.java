package ch.unizh.ini.jaer.chip.prophesee;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEditSupport;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;

/**
 * IMX636 bias sliders for Prophesee EVK4 HD.
 * Slider moves post undoable edits to the Biases frame toolbar; Revert reloads saved prefs.
 *
 * @see https://www.prophesee.ai/
 */
public class PropheseeControlPanel extends JPanel implements PropertyChangeListener {

    private static final Logger log = Logger.getLogger(PropheseeControlPanel.class.getName());

    private final PropheseeConfig config;
    private final UndoableEditSupport editSupport = new UndoableEditSupport();
    private boolean addedUndoListener;
    private boolean updating;

    private final BiasRow prRow;
    private final BiasRow foRow;
    private final BiasRow diffRow;
    private final BiasRow diffOnRow;
    private final BiasRow diffOffRow;
    private final BiasRow refrRow;
    private final BiasRow hpfRow;

    public PropheseeControlPanel(PropheseeConfig config) {
        super(new GridBagLayout());
        this.config = config;

        int row = 0;
        row = addHelpRow(row);
        prRow = addBiasRow(row++, "pr (photoreceptor)", v -> config.setPr(v));
        foRow = addBiasRow(row++, "fo (source follower)", v -> config.setFo(v));
        diffRow = addBiasRow(row++, "diff (threshold)", v -> config.setDiff(v));
        diffOnRow = addBiasRow(row++, "diff_on", v -> config.setDiffOn(v));
        diffOffRow = addBiasRow(row++, "diff_off", v -> config.setDiffOff(v));
        refrRow = addBiasRow(row++, "refr (refractory)", v -> config.setRefr(v));
        hpfRow = addBiasRow(row, "hpf (high-pass)", v -> config.setHpf(v));

        config.getSupport().addPropertyChangeListener(this);
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

        refreshFromBiases();
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private static final class BiasRow {
        final String name;
        final JSlider slider;
        final JLabel valueLabel;
        final IntConsumer setter;
        int lastStableValue;
        int dragStartValue = -1;

        BiasRow(String name, JSlider slider, JLabel valueLabel, IntConsumer setter) {
            this.name = name;
            this.slider = slider;
            this.valueLabel = valueLabel;
            this.setter = setter;
        }
    }

    private int addHelpRow(int row) {
        final GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 4, 8, 4);
        add(new JLabel("<html>Use <b>Undo</b> / <b>Redo</b> in the Biases toolbar for slider moves.<br>"
                + "Use <b>Revert</b> or <b>File → Load settings</b> to restore values from saved preferences/XML."), c);
        return row + 1;
    }

    private BiasRow addBiasRow(int row, String name, IntConsumer setter) {
        final JLabel valueLabel = new JLabel();
        final JSlider slider = newSlider();
        final BiasRow biasRow = new BiasRow(name, slider, valueLabel, setter);

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

        slider.addChangeListener(e -> {
            if (updating) {
                return;
            }
            final int v = slider.getValue();
            valueLabel.setText(String.format("0x%02X", v));
            setter.accept(v);
            if (!slider.getValueIsAdjusting() && biasRow.dragStartValue < 0 && biasRow.lastStableValue != v) {
                postBiasEdit(biasRow, biasRow.lastStableValue, v);
                biasRow.lastStableValue = v;
            }
        });

        slider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                biasRow.dragStartValue = slider.getValue();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (updating) {
                    return;
                }
                final int v = slider.getValue();
                if (biasRow.dragStartValue >= 0 && biasRow.dragStartValue != v) {
                    postBiasEdit(biasRow, biasRow.dragStartValue, v);
                    biasRow.lastStableValue = v;
                }
                biasRow.dragStartValue = -1;
            }
        });
        return biasRow;
    }

    private void postBiasEdit(BiasRow biasRow, int oldValue, int newValue) {
        ensureUndoListenerAttached();
        editSupport.postEdit(new BiasValueEdit(config, biasRow.name, biasRow.setter, oldValue, newValue));
    }

    private static JSlider newSlider() {
        final JSlider slider = new JSlider(0, 0xFF, 0);
        slider.setMajorTickSpacing(0x40);
        slider.setPaintTicks(true);
        return slider;
    }

    private void attachUndoListener(java.awt.Component component) {
        if (addedUndoListener) {
            return;
        }
        Container anc = component instanceof Container ? (Container) component : null;
        while (anc != null) {
            if (anc instanceof UndoableEditListener) {
                editSupport.addUndoableEditListener((UndoableEditListener) anc);
                addedUndoListener = true;
                return;
            }
            anc = anc.getParent();
        }
        attachUndoListenerFromChip();
    }

    private void attachUndoListenerFromChip() {
        if (addedUndoListener || !(config.getChip() instanceof AEChip aeChip)) {
            return;
        }
        if (aeChip.getAeViewer() != null && aeChip.getAeViewer().getBiasgenFrame() != null) {
            editSupport.addUndoableEditListener(aeChip.getAeViewer().getBiasgenFrame());
            addedUndoListener = true;
        }
    }

    private void ensureUndoListenerAttached() {
        if (!addedUndoListener) {
            attachUndoListener(this);
            attachUndoListenerFromChip();
        }
    }

    void refreshFromBiases() {
        updating = true;
        try {
            syncRow(prRow, config.getPr());
            syncRow(foRow, config.getFo());
            syncRow(diffRow, config.getDiff());
            syncRow(diffOnRow, config.getDiffOn());
            syncRow(diffOffRow, config.getDiffOff());
            syncRow(refrRow, config.getRefr());
            syncRow(hpfRow, config.getHpf());
        } finally {
            updating = false;
        }
    }

    private static void syncRow(BiasRow row, int value) {
        row.slider.setValue(value);
        row.valueLabel.setText(String.format("0x%02X", value));
        row.lastStableValue = value;
        row.dragStartValue = -1;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (Biasgen.PROPERTY_CHANGE_PREFERENCES_LOADED.equals(evt.getPropertyName())) {
            refreshFromBiases();
        }
    }

    private static final class BiasValueEdit extends AbstractUndoableEdit {

        private final PropheseeConfig config;
        private final String biasName;
        private final IntConsumer setter;
        private final int oldValue;
        private final int newValue;

        BiasValueEdit(PropheseeConfig config, String biasName, IntConsumer setter, int oldValue, int newValue) {
            this.config = config;
            this.biasName = biasName;
            this.setter = setter;
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
            return "Prophesee " + biasName;
        }

        private void applyValue(int value) {
            try {
                setter.accept(value);
                if (config.getPropheseeControlPanel() != null) {
                    config.getPropheseeControlPanel().refreshFromBiases();
                }
            } catch (Exception e) {
                log.warning("Prophesee bias undo/redo failed for " + biasName + ": " + e.getMessage());
            }
        }
    }
}
