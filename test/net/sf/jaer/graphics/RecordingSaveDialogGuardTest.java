package net.sf.jaer.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.junit.Test;

/**
 * macOS Aqua save-dialog filename row: left-align and stretch to pane width.
 */
public class RecordingSaveDialogGuardTest {

    private static final String LONG_NAME = "50491_2026-09-12T15-50-35-0400";

    @Test
    public void saveAsLabelIsRecognized() {
        assertTrue(RecordingSaveDialogGuard.isFilenameChooserLabel("Save As:"));
        assertTrue(RecordingSaveDialogGuard.isFilenameChooserLabel("File Name:"));
        assertTrue(!RecordingSaveDialogGuard.isFilenameChooserLabel("File Format:"));
    }

    @Test
    public void aquaRowIsDetected() {
        JPanel row = aquaSaveAsRow();
        JTextField field = (JTextField) row.getComponent(1);
        assertTrue(RecordingSaveDialogGuard.isAquaSaveFilenameRow(row, field));
    }

    @Test
    public void stretchLeftAlignsAndFillsPane() {
        JPanel row = aquaSaveAsRow();
        JLabel label = (JLabel) row.getComponent(0);
        JTextField field = (JTextField) row.getComponent(1);

        assertTrue(RecordingSaveDialogGuard.stretchFilenameRowToPaneWidth(field));
        assertTrue(row.getLayout() instanceof BorderLayout);
        assertEquals(Component.LEFT_ALIGNMENT, row.getAlignmentX(), 0.01f);
        assertEquals(Integer.MAX_VALUE, field.getMaximumSize().width);
        assertEquals(label, ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.LINE_START));
        assertEquals(field, ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER));

        row.setSize(720, 32);
        row.doLayout();
        assertTrue("filename field should fill remaining pane width", field.getWidth() > 500);
        assertTrue("label should sit on the left inset", label.getX() <= RecordingSaveDialogGuard.AQUA_SAVE_FILENAME_INSET);
        assertTrue(field.getX() > label.getX());
        assertTrue(field.getX() + field.getWidth() >= 720 - RecordingSaveDialogGuard.AQUA_SAVE_FILENAME_INSET - 1);

        assertTrue("idempotent", RecordingSaveDialogGuard.stretchFilenameRowToPaneWidth(field));
    }

    @Test
    public void nonAquaRowIsLeftAlone() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField field = new JTextField(LONG_NAME);
        field.setPreferredSize(new Dimension(400, 22));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.add(new JLabel("File Name:"));
        row.add(field);
        assertTrue(!RecordingSaveDialogGuard.stretchFilenameRowToPaneWidth(field));
        assertTrue(row.getLayout() instanceof FlowLayout);
    }

    @Test
    public void findFilenameFieldAfterSaveAsLabel() {
        JPanel chooser = new JPanel();
        JPanel row = aquaSaveAsRow();
        chooser.add(row);
        JTextField found = RecordingSaveDialogGuard.findFilenameTextField(chooser);
        assertEquals(LONG_NAME, found.getText());
    }

    private static JPanel aquaSaveAsRow() {
        JPanel labelArea = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel label = new JLabel("Save As:");
        JTextField field = new JTextField(LONG_NAME);
        Dimension aqua = new Dimension(RecordingSaveDialogGuard.AQUA_SAVE_FILENAME_FIELD_WIDTH, 22);
        field.setPreferredSize(aqua);
        field.setMaximumSize(aqua);
        labelArea.add(label);
        labelArea.add(field);
        return labelArea;
    }
}
