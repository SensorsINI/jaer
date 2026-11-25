package net.sf.jaer.chip.multidevice;

import java.awt.Frame;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Azimuth, elevation, baseline, and timestamp offset for each vision slot.
 */
final class DeviceRelationDialog extends JDialog {

    private final MultiDeviceChip chip;
    private final List<Row> rows = new ArrayList<>();

    DeviceRelationDialog(Frame owner, MultiDeviceChip chip) {
        super(owner, "Devices", true);
        this.chip = chip;
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 4));
        form.add(new JLabel("Slot"));
        form.add(new JLabel("azimuth, elevation, baseline mm, offset µs"));
        for (DeviceSlot slot : chip.visionSlots()) {
            ViewingPose.SlotPose pose = chip.getPose().poseFor(slot.getId());
            JTextField field = new JTextField(text(pose, slot.getTimestampOffsetUs()), 28);
            rows.add(new Row(slot, field));
            form.add(new JLabel(slot.getId()));
            form.add(field);
        }
        JPanel buttons = new JPanel();
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> applyAndClose());
        cancel.addActionListener(e -> setVisible(false));
        buttons.add(ok);
        buttons.add(cancel);
        getContentPane().add(form, java.awt.BorderLayout.CENTER);
        getContentPane().add(buttons, java.awt.BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private void applyAndClose() {
        for (Row row : rows) {
            String[] parts = row.field.getText().trim().split("[,\\s]+");
            if (parts.length < 4) {
                JOptionPane.showMessageDialog(this,
                        row.slot.getId() + " needs azimuth, elevation, baseline, offset",
                        "Devices", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                ViewingPose.SlotPose pose = chip.getPose().poseFor(row.slot.getId());
                pose.azimuthDeg = Float.parseFloat(parts[0]);
                pose.elevationDeg = Float.parseFloat(parts[1]);
                pose.baselineMm = Float.parseFloat(parts[2]);
                row.slot.setTimestampOffsetUs(Integer.parseInt(parts[3]));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        row.slot.getId() + " has a value that is not a number",
                        "Devices", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        chip.getPose().store(chip.getPrefs(), chip.getSlots());
        chip.refreshDevicesMenu();
        setVisible(false);
    }

    private static String text(ViewingPose.SlotPose pose, int offsetUs) {
        return pose.azimuthDeg + ", " + pose.elevationDeg + ", " + pose.baselineMm + ", " + offsetUs;
    }

    private static final class Row {
        final DeviceSlot slot;
        final JTextField field;

        Row(DeviceSlot slot, JTextField field) {
            this.slot = slot;
            this.field = field;
        }
    }
}
