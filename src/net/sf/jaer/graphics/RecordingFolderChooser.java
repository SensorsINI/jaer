package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.sf.jaer.util.FileAccessTimeout;
import net.sf.jaer.util.RecentFiles;
import net.sf.jaer.util.RecentFoldersComboAccessory;
import net.sf.jaer.util.RecordingDiskSpace;

/**
 * Directory chooser for an alternate recording location. The accessory shows
 * usable space of the selected folder in human units.
 */
public final class RecordingFolderChooser {

    private RecordingFolderChooser() {
    }

    /**
     * Modal folder chooser. Returns a directory with at least
     * {@link RecordingDiskSpace#MIN_FREE_BYTES} free, or {@code null} if the
     * user cancels. Approve button is {@code Record here}.
     */
    public static File chooseFolder(Component parent, File startDir, RecentFiles recentFiles) {
        return chooseFolder(parent, startDir, recentFiles, "Record here",
                "Choose recording folder (need at least "
                + RecordingDiskSpace.minFreeSpaceLabel() + " free)");
    }

    /**
     * Same as {@link #chooseFolder(Component, File, RecentFiles)} with a custom
     * approve button and dialog title (preferences vs start-recording prompt).
     */
    public static File chooseFolder(Component parent, File startDir, RecentFiles recentFiles,
            String approveText, String dialogTitle) {
        File initial = startDir != null ? startDir : new File(".");
        String approve = (approveText == null || approveText.isEmpty()) ? "OK" : approveText;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setMultiSelectionEnabled(false);
        chooser.setDialogTitle(dialogTitle != null ? dialogTitle
                : "Choose recording folder (need at least "
                + RecordingDiskSpace.minFreeSpaceLabel() + " free)");
        chooser.setCurrentDirectory(initial);
        chooser.setSelectedFile(initial);
        chooser.setApproveButtonText(approve);
        FreeSpaceAccessory space = new FreeSpaceAccessory(chooser);
        JPanel accessory = new JPanel();
        accessory.setLayout(new BoxLayout(accessory, BoxLayout.Y_AXIS));
        space.setAlignmentX(Component.LEFT_ALIGNMENT);
        accessory.add(space);
        if (recentFiles != null) {
            accessory.add(Box.createVerticalStrut(8));
            RecentFoldersComboAccessory recent = new RecentFoldersComboAccessory(
                    recentFiles, chooser, space::refresh);
            recent.setAlignmentX(Component.LEFT_ALIGNMENT);
            accessory.add(recent);
        }
        accessory.add(Box.createVerticalGlue());
        chooser.setAccessory(accessory);

        while (true) {
            int ret = chooser.showDialog(parent, approve);
            if (ret != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            File dir = chooser.getSelectedFile();
            if (dir == null) {
                dir = chooser.getCurrentDirectory();
            }
            if (dir != null && FileAccessTimeout.isFile(dir)) {
                dir = dir.getParentFile();
            }
            if (dir == null || FileAccessTimeout.directoryOrNull(dir) == null) {
                JOptionPane.showMessageDialog(parent,
                        "That is not a usable folder. Choose another location.",
                        "Recording folder", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            long free = RecordingDiskSpace.usableBytes(dir);
            if (free >= RecordingDiskSpace.MIN_FREE_BYTES) {
                return dir;
            }
            JOptionPane.showMessageDialog(parent,
                    "<html>Folder <code>" + dir.getAbsolutePath() + "</code><br>has only "
                    + RecordingDiskSpace.formatBytes(free) + " free.<br>Need at least "
                    + RecordingDiskSpace.minFreeSpaceLabel() + " to record.</html>",
                    "Not enough free space", JOptionPane.WARNING_MESSAGE);
        }
    }

    static final class FreeSpaceAccessory extends JPanel implements PropertyChangeListener {

        private final JFileChooser chooser;
        private final JLabel label;

        FreeSpaceAccessory(JFileChooser chooser) {
            this.chooser = chooser;
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("Free space"),
                    BorderFactory.createEmptyBorder(4, 8, 8, 8)));
            label = new JLabel();
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
            add(label, BorderLayout.NORTH);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            chooser.addPropertyChangeListener(this);
            refresh();
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension p = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, p.height);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension p = super.getPreferredSize();
            return new Dimension(Math.max(280, p.width), p.height);
        }

        void refresh() {
            File dir = chooser.getSelectedFile();
            if (dir == null) {
                dir = chooser.getCurrentDirectory();
            }
            if (dir != null && !dir.isDirectory()) {
                File parent = dir.getParentFile();
                if (parent != null) {
                    dir = parent;
                }
            }
            long free = RecordingDiskSpace.usableBytes(dir);
            boolean enough = free >= RecordingDiskSpace.MIN_FREE_BYTES;
            String path = dir != null ? dir.getAbsolutePath() : "(none)";
            label.setText("<html>Selected folder:<br>" + escape(path)
                    + "<br><b>" + RecordingDiskSpace.formatBytes(free) + " free</b>"
                    + " (need " + RecordingDiskSpace.minFreeSpaceLabel() + ")</html>");
            label.setForeground(enough ? new Color(0x1B5E20) : new Color(0xB71C1C));
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String p = evt.getPropertyName();
            if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(p)
                    || JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(p)) {
                refresh();
            }
        }

        private static String escape(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
