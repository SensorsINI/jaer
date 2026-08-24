package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * JFileChooser accessory: pulldown of {@link RecentFiles} folders. Choosing an
 * item sets the chooser directory so the user does not have to walk the tree.
 */
public class RecentFoldersComboAccessory extends JPanel {

    private final JFileChooser chooser;
    private final RecentFiles recentFiles;
    private final Runnable afterDirectoryChange;
    private final JComboBox<File> combo;
    private boolean syncing;

    /**
     * @param recentFiles source of recent folders
     * @param chooser chooser to navigate
     * @param afterDirectoryChange optional; run after a jump (e.g. restore the
     *        proposed filename)
     */
    public RecentFoldersComboAccessory(RecentFiles recentFiles, JFileChooser chooser,
            Runnable afterDirectoryChange) {
        this.recentFiles = recentFiles;
        this.chooser = chooser;
        this.afterDirectoryChange = afterDirectoryChange;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Recent folders"),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        JLabel hint = new JLabel("<html>Jump to a folder<br>from the File menu list");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        add(hint, BorderLayout.NORTH);

        combo = new JComboBox<>();
        combo.setRenderer(new FolderRenderer());
        combo.setMaximumRowCount(recentFiles != null ? recentFiles.getMaxFolders() : RecentFiles.DEFAULT_MAX_FOLDERS);
        combo.setToolTipText("Open this folder in the save dialog");
        combo.setPreferredSize(new Dimension(280, combo.getPreferredSize().height));
        add(combo, BorderLayout.CENTER);

        combo.addActionListener(e -> {
            if (syncing) {
                return;
            }
            Object item = combo.getSelectedItem();
            if (!(item instanceof File folder)) {
                return;
            }
            if (!FileAccessTimeout.isDirectory(folder)) {
                if (recentFiles != null) {
                    recentFiles.removeFile(folder);
                }
                rebuildModel();
                return;
            }
            File current = chooser.getCurrentDirectory();
            if (current != null && sameFolder(current, folder)) {
                return;
            }
            chooser.setCurrentDirectory(folder);
            if (afterDirectoryChange != null) {
                afterDirectoryChange.run();
            }
        });

        chooser.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, evt -> {
            if (syncing) {
                return;
            }
            SwingUtilities.invokeLater(this::syncSelectionToChooser);
        });

        rebuildModel();
    }

    private void rebuildModel() {
        LinkedHashSet<File> folders = new LinkedHashSet<>();
        File current = chooser.getCurrentDirectory();
        if (current != null && FileAccessTimeout.isDirectory(current)) {
            folders.add(current.getAbsoluteFile());
        }
        if (recentFiles != null) {
            List<File> recent = recentFiles.getRecentFolders();
            for (File f : recent) {
                if (f != null) {
                    folders.add(f.getAbsoluteFile());
                }
            }
        }
        syncing = true;
        try {
            combo.setModel(new DefaultComboBoxModel<>(folders.toArray(File[]::new)));
            combo.setEnabled(!folders.isEmpty());
            syncSelectionToChooser();
        } finally {
            syncing = false;
        }
    }

    private void syncSelectionToChooser() {
        File current = chooser.getCurrentDirectory();
        if (current == null) {
            return;
        }
        File abs = current.getAbsoluteFile();
        syncing = true;
        try {
            DefaultComboBoxModel<File> model = (DefaultComboBoxModel<File>) combo.getModel();
            for (int i = 0; i < model.getSize(); i++) {
                File item = model.getElementAt(i);
                if (item != null && sameFolder(item, abs)) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
            combo.setSelectedIndex(-1);
        } finally {
            syncing = false;
        }
    }

    private static boolean sameFolder(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getAbsoluteFile().equals(b.getAbsoluteFile());
    }

    private static final class FolderRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof File f) {
                String path = f.getAbsolutePath();
                setText(path);
                setToolTipText(path);
            }
            return this;
        }
    }
}
