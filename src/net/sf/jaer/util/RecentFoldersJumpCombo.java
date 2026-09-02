package net.sf.jaer.util;

import java.awt.Component;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

/**
 * Pulldown of {@link RecentFiles} folders. Choosing an item jumps to that
 * directory (JFileChooser accessory or a save-dialog path field).
 */
public class RecentFoldersJumpCombo extends JComboBox<File> {

    private final RecentFiles recentFiles;
    private final Supplier<File> currentFolder;
    private final Consumer<File> onFolder;
    private boolean syncing;

    /**
     * @param recentFiles source of recent folders (may be {@code null})
     * @param currentFolder current directory to keep at the top of the list
     * @param onFolder invoked when the user picks a different existing folder
     */
    public RecentFoldersJumpCombo(RecentFiles recentFiles, Supplier<File> currentFolder,
            Consumer<File> onFolder) {
        this.recentFiles = recentFiles;
        this.currentFolder = currentFolder;
        this.onFolder = onFolder;
        setRenderer(new FolderRenderer());
        setMaximumRowCount(recentFiles != null ? recentFiles.getMaxFolders() : RecentFiles.DEFAULT_MAX_FOLDERS);
        setToolTipText("Jump to a folder from the File menu recent list");
        addActionListener(e -> {
            if (syncing) {
                return;
            }
            Object item = getSelectedItem();
            if (!(item instanceof File folder)) {
                return;
            }
            if (!FileAccessTimeout.isDirectory(folder)) {
                if (recentFiles != null) {
                    recentFiles.removeFile(folder);
                }
                refresh();
                return;
            }
            File current = currentOrNull();
            if (current != null && sameFolder(current, folder)) {
                return;
            }
            if (onFolder != null) {
                onFolder.accept(folder);
            }
        });
        refresh();
    }

    /** Rebuild the list from {@link RecentFiles} and the current folder. */
    public void refresh() {
        LinkedHashSet<File> folders = new LinkedHashSet<>();
        File current = currentOrNull();
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
            setModel(new DefaultComboBoxModel<>(folders.toArray(File[]::new)));
            setEnabled(!folders.isEmpty());
            syncSelection(current);
        } finally {
            syncing = false;
        }
    }

    /** Select the list item that matches {@code folder}, or clear if none. */
    public void syncSelection(File folder) {
        if (folder == null) {
            syncing = true;
            try {
                setSelectedIndex(-1);
            } finally {
                syncing = false;
            }
            return;
        }
        File abs = folder.getAbsoluteFile();
        syncing = true;
        try {
            DefaultComboBoxModel<File> model = (DefaultComboBoxModel<File>) getModel();
            for (int i = 0; i < model.getSize(); i++) {
                File item = model.getElementAt(i);
                if (item != null && sameFolder(item, abs)) {
                    setSelectedIndex(i);
                    return;
                }
            }
            setSelectedIndex(-1);
        } finally {
            syncing = false;
        }
    }

    private File currentOrNull() {
        if (currentFolder == null) {
            return null;
        }
        File f = currentFolder.get();
        return f != null ? f : null;
    }

    static boolean sameFolder(File a, File b) {
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
