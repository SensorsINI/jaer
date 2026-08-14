package net.sf.jaer.util;

import java.awt.Component;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import net.sf.jaer.JaerConstants;

/**
 * Export, import, and wipe the jAER Java Preferences tree
 * ({@link JaerConstants#PREFS_ROOT}, path {@code /jaer}).
 */
public final class JaerPreferencesStore {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final String LAST_FILE_KEY = "JaerPreferencesStore.lastFile";

    /**
     * Leftover package-based nodes from before {@code /jaer}. Cleared on revert so
     * Chip constructors do not fall back to old values after {@code /jaer} is emptied.
     */
    private static final String[] LEFTOVER_NODE_PATHS = {
        "net/sf/jaer",
        "eu/seebetter",
        "ch/unizh/ini/jaer",
        "nrv/chip",
        "prophesee/chip",
        VendorPrefsMigration.LEGACY_NRV_CHIP_PACKAGE,
        VendorPrefsMigration.LEGACY_PROPHESEE_CHIP_PACKAGE,
        VendorPrefsMigration.LEGACY_NRV_HW_PACKAGE,
        VendorPrefsMigration.LEGACY_PROPHESEE_HW_PACKAGE
    };

    private JaerPreferencesStore() {
    }

    /** Absolute path of the exported / imported tree. */
    public static String jaerTreePath() {
        return JaerConstants.PREFS_ROOT.absolutePath();
    }

    /**
     * Shows a save dialog and writes {@code /jaer} with {@link Preferences#exportSubtree}.
     *
     * @return the file written, or {@code null} if the user cancelled or export failed
     */
    public static File exportDialog(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export all jAER preferences");
        chooser.setFileFilter(new XMLFileFilter());
        File suggested = suggestedExportFile();
        File startDir = suggested.getParentFile();
        if (startDir != null && startDir.isDirectory()) {
            chooser.setCurrentDirectory(startDir);
        }
        chooser.setSelectedFile(suggested);
        int ret = chooser.showSaveDialog(parent);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        if (file == null) {
            return null;
        }
        if (!file.getName().toLowerCase().endsWith(XMLFileFilter.EXTENSION)) {
            file = new File(file.getPath() + XMLFileFilter.EXTENSION);
        }
        if (file.exists()) {
            int over = JOptionPane.showConfirmDialog(parent, file + " already exists, overwrite it?",
                    "Overwrite file?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (over != JOptionPane.OK_OPTION) {
                return null;
            }
        }
        try {
            exportToFile(file);
            rememberLastFile(file);
            log.info("exported jAER preferences subtree " + jaerTreePath() + " to " + file);
            JOptionPane.showMessageDialog(parent,
                    "<html>Exported " + jaerTreePath() + " to<br>" + file.getAbsolutePath()
                            + "<p>Import this file on another computer or after revert via File → Preferences.",
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);
            return file;
        } catch (Exception e) {
            log.warning("export failed: " + e);
            JOptionPane.showMessageDialog(parent, e.toString(), "Export failed", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /**
     * Shows an open dialog and imports a Java Preferences XML file (absolute node paths).
     *
     * @return the file imported, or {@code null} if cancelled or failed
     */
    public static File importDialog(Component parent) {
        int warn = JOptionPane.showConfirmDialog(parent,
                "<html>Import replaces matching keys in the Java Preferences store"
                        + " (tree " + jaerTreePath() + " and any other nodes in the file)."
                        + "<p>Quit and restart jAER afterwards; in-memory settings are not updated live.",
                "Import jAER preferences?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (warn != JOptionPane.OK_OPTION) {
            return null;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import jAER preferences");
        chooser.setFileFilter(new XMLFileFilter());
        File last = lastFile();
        if (last != null) {
            chooser.setCurrentDirectory(last.isDirectory() ? last : last.getParentFile());
            if (last.isFile()) {
                chooser.setSelectedFile(last);
            }
        }
        int ret = chooser.showOpenDialog(parent);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        if (file == null) {
            return null;
        }
        try {
            importFromFile(file);
            rememberLastFile(file);
            log.info("imported jAER preferences from " + file);
            return file;
        } catch (Exception e) {
            log.warning("import failed: " + e);
            JOptionPane.showMessageDialog(parent, e.toString(), "Import failed", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    public static void exportToFile(File file) throws IOException, BackingStoreException {
        try (BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {
            JaerConstants.PREFS_ROOT.sync();
            JaerConstants.PREFS_ROOT.exportSubtree(os);
        }
    }

    public static void importFromFile(File file)
            throws IOException, InvalidPreferencesFormatException, BackingStoreException {
        try (InputStream raw = new BufferedInputStream(new FileInputStream(file));
                InputStream is = VendorPrefsMigration.rewriteLegacyPreferencesXml(raw)) {
            Preferences.importPreferences(is);
        }
        JaerConstants.PREFS_ROOT.sync();
    }

    /**
     * Removes stored values under {@code /jaer} and leftover package nodes.
     * Empty {@code /jaer} child nodes are left so static {@link Preferences} handles stay valid.
     */
    public static int deleteAllJaerPreferences() throws BackingStoreException {
        int keys = clearSubtreeKeys(JaerConstants.PREFS_ROOT);
        int leftoverNodes = 0;
        Preferences root = Preferences.userRoot();
        for (String path : LEFTOVER_NODE_PATHS) {
            if (root.nodeExists(path)) {
                root.node(path).removeNode();
                leftoverNodes++;
            }
        }
        root.flush();
        JaerConstants.skipPreferenceWriteOnExit = true;
        log.info(String.format("deleted %d keys under %s and removed %d leftover package nodes",
                keys, jaerTreePath(), leftoverNodes));
        return keys;
    }

    public static int countJaerPreferenceKeys() {
        try {
            return countSubtreeKeys(JaerConstants.PREFS_ROOT);
        } catch (BackingStoreException e) {
            log.warning(e.toString());
            return -1;
        }
    }

    private static int clearSubtreeKeys(Preferences node) throws BackingStoreException {
        int n = 0;
        for (String key : node.keys()) {
            node.remove(key);
            n++;
        }
        for (String child : node.childrenNames()) {
            n += clearSubtreeKeys(node.node(child));
        }
        return n;
    }

    private static int countSubtreeKeys(Preferences node) throws BackingStoreException {
        int n = node.keys().length;
        for (String child : node.childrenNames()) {
            n += countSubtreeKeys(node.node(child));
        }
        return n;
    }

    private static File suggestedExportFile() {
        File last = lastFile();
        File dir = last != null ? (last.isDirectory() ? last : last.getParentFile()) : null;
        if (dir == null || !dir.isDirectory()) {
            dir = new File(System.getProperty("user.home"));
        }
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        return new File(dir, "jaer-preferences-" + date + ".xml");
    }

    private static File lastFile() {
        String path = JaerConstants.PREFS_ROOT.get(LAST_FILE_KEY, null);
        if (path == null || path.isEmpty()) {
            return null;
        }
        return new File(path);
    }

    private static void rememberLastFile(File file) {
        try {
            JaerConstants.PREFS_ROOT.put(LAST_FILE_KEY, file.getCanonicalPath());
        } catch (IOException e) {
            JaerConstants.PREFS_ROOT.put(LAST_FILE_KEY, file.getAbsolutePath());
        }
    }
}
