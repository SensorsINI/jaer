/*
 * FlyEyeOverlapDialog.java
 *
 * Modeless overlap tuner: mouse wheel, spinner, and arrow keys. Canvas width
 * is 2×128 − overlap.
 */
package ch.unizh.ini.jaer.chip.flyeye;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;

/**
 * Live / playback stitch width. Native DVS128 streams are unchanged; only the
 * virtual FlyEye remap uses this value.
 */
public class FlyEyeOverlapDialog extends JDialog {

    private final FlyEye flyEye;
    private final JSpinner spinner;
    private final JLabel sizeLabel;
    private boolean updating;

    public FlyEyeOverlapDialog(Frame owner, FlyEye flyEye) {
        super(owner, "FlyEye overlap", false);
        this.flyEye = flyEye;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        SpinnerNumberModel model = new SpinnerNumberModel(
                flyEye.getOverlapPixels(), 0, FlyEyeGeometry.NATIVE_W, 1);
        spinner = new JSpinner(model);
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().setColumns(4);
        editor.getTextField().setHorizontalAlignment(SwingConstants.RIGHT);
        installMouseWheel(spinner);
        installKeys(spinner);

        sizeLabel = new JLabel();
        refreshSizeLabel();

        ChangeListener cl = e -> {
            if (updating) {
                return;
            }
            int ov = ((Number) spinner.getValue()).intValue();
            flyEye.setOverlapPixels(ov);
            refreshSizeLabel();
        };
        spinner.addChangeListener(cl);

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        north.add(new JLabel("Overlap columns"));
        north.add(spinner);
        north.add(sizeLabel);

        JLabel hint = new JLabel("<html>Wheel or ↑↓ / ←→ / +− : ±1 column.<br>"
                + "Width = 256 − overlap. LeftRight color mode shows alignment.</html>");

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.add(north, BorderLayout.NORTH);
        root.add(hint, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    private void refreshSizeLabel() {
        int ov = flyEye.getOverlapPixels();
        sizeLabel.setText(String.format("canvas %d × %d",
                FlyEyeGeometry.panoramicWidth(ov), FlyEyeGeometry.NATIVE_H));
    }

    private void step(int delta) {
        int ov = FlyEyeGeometry.clampOverlap(flyEye.getOverlapPixels() + delta);
        updating = true;
        spinner.setValue(ov);
        updating = false;
        flyEye.setOverlapPixels(ov);
        refreshSizeLabel();
    }

    private void installKeys(JSpinner sp) {
        InputMap im = sp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = sp.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "overlap-dec");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "overlap-inc");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "overlap-dec");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "overlap-dec");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.SHIFT_DOWN_MASK), "overlap-inc");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "overlap-inc");
        am.put("overlap-dec", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                step(-1);
            }
        });
        am.put("overlap-inc", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                step(1);
            }
        });
    }

    private static void installMouseWheel(JSpinner spinner) {
        MouseWheelListener wheel = (MouseWheelEvent e) -> {
            if (!spinner.isEnabled()) {
                return;
            }
            e.consume();
            int rotation = e.getWheelRotation();
            if (rotation == 0) {
                return;
            }
            Object next = rotation < 0 ? spinner.getNextValue() : spinner.getPreviousValue();
            if (next != null) {
                spinner.setValue(next);
            }
        };
        spinner.addMouseWheelListener(wheel);
        for (Component child : spinner.getComponents()) {
            child.addMouseWheelListener(wheel);
            if (child instanceof JSpinner.DefaultEditor ed) {
                ed.getTextField().addMouseWheelListener(wheel);
            }
        }
    }
}
