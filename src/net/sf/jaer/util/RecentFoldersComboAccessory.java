package net.sf.jaer.util;

import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * JFileChooser accessory: pulldown of {@link RecentFiles} folders. Choosing an
 * item sets the chooser directory so the user does not have to walk the tree.
 */
public class RecentFoldersComboAccessory extends JPanel {

    private final JFileChooser chooser;
    private final RecentFoldersJumpCombo combo;

    /**
     * @param recentFiles source of recent folders
     * @param chooser chooser to navigate
     * @param afterDirectoryChange optional; run after a jump (e.g. restore the
     *        proposed filename)
     */
    public RecentFoldersComboAccessory(RecentFiles recentFiles, JFileChooser chooser,
            Runnable afterDirectoryChange) {
        this.chooser = chooser;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Recent folders"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        JLabel hint = new JLabel("<html>Jump to a folder<br>from the File menu list");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        hint.setAlignmentX(LEFT_ALIGNMENT);
        add(hint);

        combo = new RecentFoldersJumpCombo(recentFiles, chooser::getCurrentDirectory, folder -> {
            chooser.setCurrentDirectory(folder);
            if (afterDirectoryChange != null) {
                afterDirectoryChange.run();
            }
        });
        combo.setToolTipText("Open this folder in the save dialog");
        combo.setAlignmentX(LEFT_ALIGNMENT);
        combo.setPreferredSize(new Dimension(280, combo.getPreferredSize().height));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, combo.getPreferredSize().height));
        add(Box.createVerticalStrut(4));
        add(combo);

        chooser.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, evt -> {
            SwingUtilities.invokeLater(() -> combo.syncSelection(chooser.getCurrentDirectory()));
        });
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension p = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, p.height);
    }
}
