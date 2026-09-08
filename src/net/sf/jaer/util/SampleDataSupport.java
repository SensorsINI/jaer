/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import net.sf.jaer.JaerConstants;

/**
 * Optional download of curated recordings into {@code sampleData/}.
 * The zip is a GitHub Release asset; sizes come from shipped {@code SIZE.txt}.
 */
public final class SampleDataSupport {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    public static final String DOWNLOAD_URL = JaerConstants.SAMPLE_DATA_DOWNLOAD_URL;

    public static final String README_URL = JaerConstants.SAMPLE_DATA_README_URL;

    public static final String PREF_DECLINED = "AEViewer.sampleDataDownloadDeclined";

    /** Last folder the user chose for sample recordings (absolute path). */
    public static final String PREF_FOLDER = "AEViewer.sampleDataFolder";

    /** Suggested folder name under the user home when the install tree is not writable. */
    public static final String HOME_FOLDER_NAME = "jaerSampleData";

    public static final String HELP_MENU_DOWNLOAD = "Download jAER sample data";

    public static final String HELP_MENU_SHOW = "Show jAER sample data folder and README";

    private static final String[] META_NAMES = { "README.md", "SIZE.txt", ".gitignore", ".gitattributes" };

    private static final String[] RECORDING_SUFFIXES = { ".aedat4", ".aedat", ".dat", ".raw" };

    public static final class Sizes {
        public final long zipBytes;
        public final int zipMiB;
        public final long unpackedBytes;
        public final int unpackedMiB;
        public final boolean known;

        Sizes(long zipBytes, int zipMiB, long unpackedBytes, int unpackedMiB, boolean known) {
            this.zipBytes = zipBytes;
            this.zipMiB = zipMiB;
            this.unpackedBytes = unpackedBytes;
            this.unpackedMiB = unpackedMiB;
            this.known = known;
        }
    }

    private SampleDataSupport() {
    }

    /**
     * Folder used for Help → Show, File → Open fallback, and download unpack.
     * Prefers the last chosen download folder, otherwise the install {@code sampleData/}.
     */
    public static File folder() {
        String pref = JaerConstants.PREFS_ROOT.get(PREF_FOLDER, "");
        if (pref != null && !pref.isBlank()) {
            return new File(pref);
        }
        return installDefaultFolder();
    }

    /**
     * {@code sampleData} next to the running jAER (git checkout or installer
     * destination). Does not consult {@link #PREF_FOLDER}.
     */
    public static File installDefaultFolder() {
        File cwd = new File(System.getProperty("user.dir", "."), "sampleData");
        if (cwd.isDirectory() || new File(cwd, "README.md").isFile()) {
            return cwd;
        }
        File nested = new File(System.getProperty("user.dir", "."), "jaer" + File.separator + "sampleData");
        if (nested.isDirectory()) {
            return nested;
        }
        return cwd;
    }

    /**
     * Chooser default: install {@code sampleData} when that location is writable,
     * otherwise {@code jaerSampleData} in the user home directory.
     */
    public static File suggestedDownloadFolder() {
        String pref = JaerConstants.PREFS_ROOT.get(PREF_FOLDER, "");
        if (pref != null && !pref.isBlank()) {
            File remembered = new File(pref);
            if (isWritableLocation(remembered)) {
                return remembered;
            }
        }
        File install = installDefaultFolder();
        if (isWritableLocation(install)) {
            return install;
        }
        return new File(System.getProperty("user.home", "."), HOME_FOLDER_NAME);
    }

    /**
     * True if a file can be created in {@code dir} (or in an existing ancestor if
     * {@code dir} does not exist yet). {@link File#canWrite()} is not reliable on
     * Windows Program Files.
     */
    public static boolean isWritableLocation(File dir) {
        if (dir == null) {
            return false;
        }
        try {
            if (dir.isDirectory()) {
                return probeWrite(dir.toPath());
            }
            File walk = dir.exists() ? dir : dir.getParentFile();
            while (walk != null && !walk.exists()) {
                walk = walk.getParentFile();
            }
            if (walk != null && walk.isDirectory()) {
                return probeWrite(walk.toPath());
            }
        } catch (Exception ex) {
            log.log(Level.FINE, "sampleData write probe failed for " + dir + ": " + ex);
            return false;
        }
        return false;
    }

    private static boolean probeWrite(Path dir) {
        try {
            Path probe = Files.createTempFile(dir, ".jaer-w", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** True if {@link #folder()} exists as a directory (installer README tree or a download). */
    public static boolean folderExists() {
        return folder().isDirectory();
    }

    /**
     * Help menu uses Show after recordings are present (README-only install tree
     * still offers Download, including when Program Files is not writable).
     */
    public static boolean useShowHelpItem() {
        return hasRecordings();
    }

    public static String helpMenuLabel() {
        return useShowHelpItem() ? HELP_MENU_SHOW : HELP_MENU_DOWNLOAD;
    }

    public static String helpMenuToolTip() {
        if (useShowHelpItem()) {
            return "Opens the sample recordings folder and the GitHub README (in-app README if offline)";
        }
        return "Choose a folder, download curated recordings (Cancel stops the download only), then open the folder and README";
    }

    public static File sizeFile() {
        File inFolder = new File(folder(), "SIZE.txt");
        if (inFolder.isFile()) {
            return inFolder;
        }
        return new File(installDefaultFolder(), "SIZE.txt");
    }

    public static boolean isMetaName(String name) {
        if (name == null) {
            return true;
        }
        for (String m : META_NAMES) {
            if (m.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** True for sample recordings (AEDAT-4/2, legacy {@code .dat}, Prophesee {@code .raw}). */
    public static boolean isRecordingName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String s : RECORDING_SUFFIXES) {
            if (lower.endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    /** True when the sample folder has at least one recording (not README / WebP previews). */
    public static boolean hasRecordings() {
        File dir = folder();
        if (!dir.isDirectory()) {
            return false;
        }
        try {
            return Files.walk(dir.toPath())
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .anyMatch(SampleDataSupport::isRecordingName);
        } catch (Exception ex) {
            log.log(Level.FINE, "sampleData walk failed: " + ex, ex);
            return false;
        }
    }

    public static Sizes readSizes() {
        File f = sizeFile();
        if (!f.isFile()) {
            return new Sizes(0, 0, 0, 0, false);
        }
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
            long zipB = parseLong(p.getProperty("zipBytes"));
            long unB = parseLong(p.getProperty("unpackedBytes"));
            int zipM = parseInt(p.getProperty("zipMiB"), mib(zipB));
            int unM = parseInt(p.getProperty("unpackedMiB"), mib(unB));
            boolean known = zipB > 0 || zipM > 0 || unB > 0 || unM > 0;
            return new Sizes(zipB, zipM, unB, unM, known);
        } catch (Exception ex) {
            log.log(Level.WARNING, "Could not read " + f + ": " + ex, ex);
            return new Sizes(0, 0, 0, 0, false);
        }
    }

    /** Typical home Wi-Fi used for installer and in-app time estimates. */
    public static final double WIFI_MB_PER_SEC = 10.0;

    public static String etaAtWifi10MBps(int zipMiB) {
        if (zipMiB <= 0) {
            return "time depends on your connection (10 MB/s Wi-Fi assumed)";
        }
        int sec = Math.max(1, (int) Math.round(zipMiB / WIFI_MB_PER_SEC));
        if (sec < 90) {
            return "about " + sec + " s at 10 MB/s Wi-Fi";
        }
        return String.format(Locale.ROOT, "about %.1f min at 10 MB/s Wi-Fi", sec / 60.0);
    }

    public static String sizeOfferText(Sizes s) {
        if (s == null || !s.known) {
            return "Download and unpacked sizes are unknown (missing sampleData/SIZE.txt).";
        }
        return s.zipMiB + " MB download (" + etaAtWifi10MBps(s.zipMiB) + "), " + s.unpackedMiB + " MB on disk";
    }

    /** User stopped an in-app sample-data download. */
    public static final class DownloadCancelledException extends Exception {
        public DownloadCancelledException() {
            super("Sample data download cancelled");
        }
    }

    /**
     * File → Open: if sampleData has no recordings and the user has not declined,
     * offer to download. Help menu uses {@code force=true}.
     *
     * @return true if recordings are present after this call
     */
    public static boolean maybeDownload(Component parent, boolean force) {
        if (hasRecordings()) {
            log.info("File > Open: sampleData already has recordings at " + folder().getAbsolutePath());
            return true;
        }
        if (!force && JaerConstants.PREFS_ROOT.getBoolean(PREF_DECLINED, false)) {
            log.info("File > Open: sample-data download previously declined");
            return false;
        }
        Sizes sizes = readSizes();
        String sizeLine = sizeOfferText(sizes);
        log.info("File > Open: offering sample-data download (" + sizeLine + ")");
        int choice = JOptionPane.showConfirmDialog(parent,
                "<html>jAER sample recordings are not in this <code>sampleData</code> folder.<br><br>"
                        + sizeLine + ".<br><br>"
                        + "Download from GitHub Latest? You will choose the unpack folder next.<br>"
                        + "You can cancel the download after it starts.<br>"
                        + "<code>" + DOWNLOAD_URL + "</code>",
                "Download sample recordings?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            log.info("User declined sample-data download");
            if (!force) {
                JaerConstants.PREFS_ROOT.putBoolean(PREF_DECLINED, true);
            }
            return false;
        }
        log.info("User accepted sample-data download");
        File dest = chooseDownloadFolder(parent, recentFilesFrom(parent));
        if (dest == null) {
            log.info("User cancelled sample-data folder chooser");
            return false;
        }
        try {
            downloadAndUnpack(parent, dest);
            JaerConstants.PREFS_ROOT.putBoolean(PREF_DECLINED, false);
            if (parent instanceof net.sf.jaer.graphics.AEViewer v) {
                SwingUtilities.invokeLater(v::refreshSampleDataHelpMenu);
                rememberFolder(v.getRecentFiles());
            }
            openFolderAndReadme();
            return hasRecordings();
        } catch (DownloadCancelledException ex) {
            log.info("Sample-data download cancelled");
            return false;
        } catch (Exception ex) {
            logDownloadFailure(ex);
            JOptionPane.showMessageDialog(parent, formatDownloadFailureHtml(ex),
                    "Sample data download failed",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public static File defaultOpenFolder() {
        File dir = folder();
        if (dir.isDirectory()) {
            return dir;
        }
        File suggested = suggestedDownloadFolder();
        if (suggested.isDirectory()) {
            return suggested;
        }
        File parent = suggested.getParentFile();
        if (parent != null && parent.isDirectory()) {
            return parent;
        }
        return new File(System.getProperty("user.dir", "."));
    }

    /** Open {@link #README_URL} in the default browser (safe off the EDT). */
    public static void browseReadmeUrl() {
        Runnable r = () -> openInBrowser(README_URL);
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /**
     * GitHub README in the browser on Windows, macOS, and Linux. If GitHub is
     * unreachable, show the local {@code sampleData/README.md} inside jAER (do
     * not hand a {@code .md} file to the OS). Does not open Explorer/Finder.
     */
    public static void openFolderAndReadme() {
        File dir = folder();
        log.info("Sample data: opening folder " + dir.getAbsolutePath());
        try {
            if (!dir.isDirectory()) {
                if (isWritableLocation(dir)) {
                    Files.createDirectories(dir.toPath());
                } else {
                    log.warning("Sample data folder missing and not writable: " + dir.getAbsolutePath());
                    return;
                }
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
                log.info("Sample data: Desktop.open(folder) returned");
            } else {
                log.warning("Sample data: Desktop not supported, cannot open folder");
            }
        } catch (Exception ex) {
            log.log(Level.WARNING, "Could not open sampleData folder: " + ex, ex);
        }
        log.info("Sample data README: probing GitHub, then browser or in-app README");
        Thread probe = new Thread(() -> {
            boolean online = githubReadmeReachable();
            log.info("Sample data README: GitHub reachable=" + online + " url=" + README_URL);
            SwingUtilities.invokeLater(() -> {
                if (online) {
                    browseReadmeUrl();
                } else {
                    showLocalReadmeDialog(null);
                }
            });
        }, "jaer-sample-data-readme");
        probe.setDaemon(true);
        probe.start();
    }

    private static boolean githubReadmeReachable() {
        HttpURLConnection conn = null;
        String probe = README_URL;
        int hash = probe.indexOf('#');
        if (hash >= 0) {
            probe = probe.substring(0, hash);
        }
        long t0 = System.currentTimeMillis();
        try {
            conn = (HttpURLConnection) URI.create(probe).toURL().openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.connect();
            int code = conn.getResponseCode();
            long ms = System.currentTimeMillis() - t0;
            boolean ok = code >= 200 && code < 400;
            log.info("Sample data README: GET " + probe + " -> HTTP " + code + " in " + ms + " ms (ok=" + ok + ")");
            return ok;
        } catch (Exception ex) {
            long ms = System.currentTimeMillis() - t0;
            log.log(Level.INFO, "Sample data README: GitHub probe failed after " + ms + " ms: " + ex, ex);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void openInBrowser(String url) {
        String os = System.getProperty("os.name", "");
        boolean desktop = Desktop.isDesktopSupported();
        boolean browse = desktop && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
        log.info("Sample data README: opening browser url=" + url + " os=" + os
                + " Desktop.supported=" + desktop + " BROWSE.supported=" + browse);
        try {
            String osL = os.toLowerCase(Locale.ROOT);
            if (osL.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
                log.info("Sample data README: started Windows cmd start for browser");
                return;
            }
            if (osL.contains("mac")) {
                new ProcessBuilder("open", url).start();
                log.info("Sample data README: started macOS open for browser");
                return;
            }
            new ProcessBuilder("xdg-open", url).start();
            log.info("Sample data README: started xdg-open for browser");
        } catch (Exception ex) {
            log.log(Level.WARNING, "OS browser launch failed for " + url + ", trying Desktop.browse: " + ex, ex);
            try {
                if (browse) {
                    Desktop.getDesktop().browse(URI.create(url));
                    log.info("Sample data README: Desktop.browse returned for " + url);
                } else {
                    log.warning("Sample data README: Desktop.BROWSE not supported, cannot open " + url);
                }
            } catch (Exception ex2) {
                log.log(Level.WARNING, "Could not open sample-data README URL: " + ex2, ex2);
            }
        }
    }

    private static void showLocalReadmeDialog(Component parent) {
        File readme = new File(folder(), "README.md");
        log.info("Sample data README: GitHub unreachable, showing in-app README from "
                + (readme.isFile() ? readme.getAbsolutePath() : "(missing) " + readme.getAbsolutePath()));
        String body;
        if (readme.isFile()) {
            try {
                body = Files.readString(readme.toPath(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                body = "Could not read " + readme.getAbsolutePath() + "\n" + ex.getMessage();
            }
        } else {
            body = "GitHub is not reachable and there is no local README.md.\n\n"
                    + README_URL + "\n\nFolder: " + folder().getAbsolutePath();
        }
        JTextArea area = new JTextArea(body);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, 480));
        JOptionPane.showMessageDialog(parent, scroll, "jAER sample recordings (offline)",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Put {@code sampleData/} on the File menu recent-folders list. */
    public static void rememberFolder(RecentFiles recentFiles) {
        if (recentFiles == null) {
            return;
        }
        File dir = folder();
        if (dir.isDirectory()) {
            recentFiles.addFolder(dir);
            log.fine("Sample data: remembered folder " + dir.getAbsolutePath());
        }
    }

    /** HTML body for a failed zip download (Help menu and File → Open). */
    public static String formatDownloadFailureHtml(Throwable ex) {
        String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex);
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(escapeHtml(msg).replace("\n", "<br>"));
        sb.append("<br><br>Manual: <code>").append(escapeHtml(DOWNLOAD_URL)).append("</code>");
        sb.append("<br>README: <code>").append(escapeHtml(README_URL)).append("</code>");
        sb.append("<br>Unpack into <code>").append(escapeHtml(folder().getAbsolutePath())).append("</code>");
        return sb.toString();
    }

    public static void logDownloadFailure(Throwable ex) {
        if (isMissingOnRelease(ex)) {
            log.warning(ex.getMessage());
            return;
        }
        log.log(Level.SEVERE, "Sample data download failed: " + ex, ex);
    }

    /**
     * Modal folder chooser for the zip unpack location. Default is the install
     * {@code sampleData} folder when writable, otherwise
     * {@code user.home/jaerSampleData}. Returns {@code null} if the user cancels.
     */
    public static File chooseDownloadFolder(Component parent, RecentFiles recentFiles) {
        if (!SwingUtilities.isEventDispatchThread()) {
            final File[] holder = new File[1];
            try {
                SwingUtilities.invokeAndWait(() -> holder[0] = chooseDownloadFolder(parent, recentFiles));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception ex) {
                log.log(Level.WARNING, "Sample data folder chooser failed: " + ex, ex);
                return null;
            }
            return holder[0];
        }
        File suggested = suggestedDownloadFolder();
        File install = installDefaultFolder();
        boolean installWritable = isWritableLocation(install);
        log.info("Sample data folder chooser: suggested=" + suggested.getAbsolutePath()
                + " install=" + install.getAbsolutePath() + " installWritable=" + installWritable);
        if (!suggested.exists() && isWritableLocation(suggested)) {
            try {
                Files.createDirectories(suggested.toPath());
            } catch (Exception ex) {
                log.log(Level.FINE, "Could not pre-create suggested sample folder " + suggested + ": " + ex);
            }
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setMultiSelectionEnabled(false);
        chooser.setDialogTitle("Choose folder for jAER sample recordings");
        chooser.setApproveButtonText("Use this folder");
        File current = suggested.isDirectory() ? suggested
                : (suggested.getParentFile() != null && suggested.getParentFile().isDirectory()
                        ? suggested.getParentFile()
                        : new File(System.getProperty("user.home", ".")));
        chooser.setCurrentDirectory(current);
        chooser.setSelectedFile(suggested);

        JPanel accessory = new JPanel();
        accessory.setLayout(new BoxLayout(accessory, BoxLayout.Y_AXIS));
        String hintHtml;
        if (installWritable) {
            hintHtml = "<html>Unpack into this folder.<br>Default is the jAER <code>sampleData</code> folder.</html>";
        } else {
            hintHtml = "<html>The install folder is not writable<br>(for example under Program Files).<br>"
                    + "Default is <code>" + HOME_FOLDER_NAME + "</code> in your home folder.</html>";
        }
        JLabel hint = new JLabel(hintHtml);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        accessory.add(hint);
        if (recentFiles != null) {
            accessory.add(Box.createVerticalStrut(8));
            RecentFoldersComboAccessory recent = new RecentFoldersComboAccessory(recentFiles, chooser, null);
            recent.setAlignmentX(Component.LEFT_ALIGNMENT);
            accessory.add(recent);
        }
        accessory.add(Box.createVerticalGlue());
        chooser.setAccessory(accessory);

        while (true) {
            int ret = chooser.showDialog(parent, "Use this folder");
            if (ret != JFileChooser.APPROVE_OPTION) {
                return null;
            }
            File dir = chooser.getSelectedFile();
            if (dir == null) {
                dir = chooser.getCurrentDirectory();
            }
            if (dir != null && dir.isFile()) {
                dir = dir.getParentFile();
            }
            if (dir == null) {
                JOptionPane.showMessageDialog(parent,
                        "That is not a usable folder. Choose another location.",
                        "Sample data folder", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            try {
                Files.createDirectories(dir.toPath());
            } catch (Exception ex) {
                log.log(Level.INFO, "Could not create sample data folder " + dir + ": " + ex);
                JOptionPane.showMessageDialog(parent,
                        "<html>Cannot create or write to<br><code>" + escapeHtml(dir.getAbsolutePath())
                                + "</code><br><br>" + escapeHtml(String.valueOf(ex.getMessage()))
                                + "<br><br>Choose a writable folder (for example "
                                + HOME_FOLDER_NAME + " in your home directory).</html>",
                        "Folder not writable", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (!dir.isDirectory() || !isWritableLocation(dir)) {
                JOptionPane.showMessageDialog(parent,
                        "<html>Folder <code>" + escapeHtml(dir.getAbsolutePath())
                                + "</code> is not writable.<br>Choose another location.</html>",
                        "Folder not writable", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            persistFolder(dir);
            rememberFolder(recentFiles);
            log.info("Sample data download folder: " + dir.getAbsolutePath());
            return dir;
        }
    }

    private static void persistFolder(File dir) {
        if (dir == null) {
            return;
        }
        JaerConstants.PREFS_ROOT.put(PREF_FOLDER, dir.getAbsolutePath());
    }

    private static RecentFiles recentFilesFrom(Component parent) {
        if (parent instanceof net.sf.jaer.graphics.AEViewer v) {
            return v.getRecentFiles();
        }
        return null;
    }

    /**
     * Download and unpack the curated sample recordings into {@code dest}.
     * Progress UI is created on the Swing EDT.
     */
    public static void downloadAndUnpack(Component parent, File dest) throws Exception {
        if (dest == null) {
            throw new Exception("No sample data folder chosen");
        }
        Files.createDirectories(dest.toPath());
        persistFolder(dest);
        File zip = new File(dest, "jaer-sample-data.zip.partial");
        log.info("Downloading sample-data zip from " + DOWNLOAD_URL + " -> " + zip.getAbsolutePath());
        try {
            downloadTo(parent, DOWNLOAD_URL, zip);
            log.info("Unpacking sample-data zip into " + dest.getAbsolutePath());
            unzipTo(zip, dest);
        } finally {
            Files.deleteIfExists(zip.toPath());
        }
        log.info("Unpacked sample recordings into " + dest.getAbsolutePath());
    }

    private static void downloadTo(Component parent, String urlString, File dest) throws Exception {
        final JProgressBar[] barHolder = new JProgressBar[1];
        final javax.swing.JDialog[] dialogHolder = new javax.swing.JDialog[1];
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        Runnable initUi = () -> {
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setStringPainted(true);
            barHolder[0] = bar;

            JLabel label = new JLabel("Downloading jaer-sample-data.zip …");
            JButton cancel = new JButton("Cancel");
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.add(label, BorderLayout.NORTH);
            panel.add(bar, BorderLayout.CENTER);
            panel.add(cancel, BorderLayout.SOUTH);
            panel.setPreferredSize(new Dimension(420, 100));

            JOptionPane pane = new JOptionPane(panel, JOptionPane.INFORMATION_MESSAGE,
                    JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
            javax.swing.JDialog dialog = pane.createDialog(parent, "Downloading sample recordings");
            dialog.setModal(true);
            dialog.setDefaultCloseOperation(javax.swing.JDialog.DISPOSE_ON_CLOSE);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    cancelled.set(true);
                }
            });
            cancel.addActionListener(e -> {
                cancelled.set(true);
                dialog.dispose();
            });
            dialogHolder[0] = dialog;
        };
        if (SwingUtilities.isEventDispatchThread()) {
            initUi.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(initUi);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new Exception("Interrupted while opening download dialog", ie);
            }
        }

        final JProgressBar bar = barHolder[0];
        final javax.swing.JDialog dialog = dialogHolder[0];

        Exception[] error = new Exception[1];
        Thread worker = new Thread(() -> {
            try {
                HttpURLConnection conn = openFollowingRedirects(urlString);
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw httpFailure(urlString, conn.getURL() != null ? conn.getURL().toString() : urlString, code);
                }
                long total = conn.getContentLengthLong();
                long startNs = System.nanoTime();
                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                    byte[] buf = new byte[64 * 1024];
                    long read = 0;
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                            throw new DownloadCancelledException();
                        }
                        out.write(buf, 0, n);
                        read += n;
                        final int pct = total > 0 ? (int) Math.min(100, (read * 100) / total) : 0;
                        final long readMb = read / (1024 * 1024);
                        long remain = total > 0 ? total - read : 0;
                        double elapsed = (System.nanoTime() - startNs) / 1e9;
                        double bps = elapsed > 0.4 ? read / elapsed : WIFI_MB_PER_SEC * 1e6;
                        final int etaSec = remain > 0
                                ? (int) Math.max(1, Math.round(remain / Math.max(bps, 5e5))) : 0;
                        SwingUtilities.invokeLater(() -> {
                            bar.setValue(pct);
                            String eta = etaSec >= 90
                                    ? String.format(Locale.ROOT, "%.1f min left", etaSec / 60.0)
                                    : (etaSec > 0 ? etaSec + " s left" : "");
                            bar.setString(total > 0
                                    ? String.format(Locale.ROOT, "%d%% (%d MB) %s", pct, readMb, eta)
                                    : String.format(Locale.ROOT, "%d MB", readMb));
                        });
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ex) {
                error[0] = ex;
                try {
                    Files.deleteIfExists(dest.toPath());
                } catch (Exception ignore) {
                }
            } finally {
                SwingUtilities.invokeLater(dialog::dispose);
            }
        }, "jaer-sample-data-download");
        worker.setDaemon(true);
        worker.start();
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                dialog.setVisible(true);
            } else {
                SwingUtilities.invokeAndWait(() -> dialog.setVisible(true));
            }
            worker.join(2000);
        } catch (InterruptedException ie) {
            cancelled.set(true);
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new DownloadCancelledException();
        }
        if (cancelled.get() && (error[0] == null || error[0] instanceof DownloadCancelledException)) {
            throw new DownloadCancelledException();
        }
        if (error[0] != null) {
            throw error[0];
        }
        if (!dest.isFile() || dest.length() == 0) {
            throw new Exception("Download finished but file missing: " + dest);
        }
    }

    private static HttpURLConnection openFollowingRedirects(String urlString) throws Exception {
        URL url = URI.create(urlString).toURL();
        for (int hop = 0; hop < 8; hop++) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(300000);
            conn.setInstanceFollowRedirects(false);
            conn.connect();
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                return conn;
            }
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null || loc.isEmpty()) {
                    throw new Exception("Redirect without Location from " + url);
                }
                url = URI.create(url.toString()).resolve(loc).toURL();
                continue;
            }
            conn.disconnect();
            throw httpFailure(urlString, url.toString(), code);
        }
        throw new Exception("Too many redirects for " + urlString);
    }

    private static void unzipTo(File zip, File destDir) throws Exception {
        Path dest = destDir.toPath().toAbsolutePath().normalize();
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buf = new byte[64 * 1024];
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.isEmpty()) {
                    continue;
                }
                Path out = dest.resolve(name).normalize();
                if (!out.startsWith(dest)) {
                    throw new Exception("Refusing zip path " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out.toFile()))) {
                    int n;
                    while ((n = zin.read(buf)) >= 0) {
                        os.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private static final Pattern RELEASE_DOWNLOAD = Pattern.compile("/releases/download/([^/]+)/");

    private static Exception httpFailure(String requested, String finalUrl, int code) {
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            String tag = releaseTagFromUrl(finalUrl);
            if (tag == null) {
                tag = releaseTagFromUrl(requested);
            }
            StringBuilder sb = new StringBuilder();
            if (tag != null) {
                sb.append("Sample data is not available for GitHub release ").append(tag).append('.');
            } else {
                sb.append("Sample data is not available for this GitHub release.");
            }
            sb.append("\nLatest has no jaer-sample-data.zip asset yet (HTTP 404).");
            sb.append("\nTried ").append(requested);
            if (finalUrl != null && !finalUrl.equals(requested)) {
                sb.append(" → ").append(finalUrl);
            }
            return new Exception(sb.toString());
        }
        return new Exception("HTTP " + code + " for " + finalUrl);
    }

    private static boolean isMissingOnRelease(Throwable ex) {
        while (ex != null) {
            String m = ex.getMessage();
            if (m != null && (m.contains("Sample data is not available for") || m.contains("HTTP 404"))) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }

    private static String releaseTagFromUrl(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = RELEASE_DOWNLOAD.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int mib(long bytes) {
        if (bytes <= 0) {
            return 0;
        }
        return (int) Math.round(bytes / (1024.0 * 1024.0));
    }
}
