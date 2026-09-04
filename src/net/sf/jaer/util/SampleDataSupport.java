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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    public static final String HELP_MENU_DOWNLOAD = "Download jAER sample data";

    public static final String HELP_MENU_SHOW = "Show jAER sample data folder and README";

    private static final String[] META_NAMES = { "README.md", "SIZE.txt", ".gitignore", ".gitattributes" };

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

    public static File folder() {
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

    /** True if {@link #folder()} exists as a directory (installer README tree or a download). */
    public static boolean folderExists() {
        return folder().isDirectory();
    }

    /** Help menu uses Show (folder+README) when the folder is already there. */
    public static boolean useShowHelpItem() {
        return folderExists() || hasRecordings();
    }

    public static String helpMenuLabel() {
        return useShowHelpItem() ? HELP_MENU_SHOW : HELP_MENU_DOWNLOAD;
    }

    public static String helpMenuToolTip() {
        if (useShowHelpItem()) {
            return "Opens the sampleData folder and the GitHub README (in-app README if offline)";
        }
        return "Downloads curated recordings into sampleData, then opens the folder and README";
    }

    public static File sizeFile() {
        return new File(folder(), "SIZE.txt");
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

    /** Any regular file under sampleData other than README / SIZE / gitignore. */
    public static boolean hasRecordings() {
        File dir = folder();
        if (!dir.isDirectory()) {
            return false;
        }
        try {
            return Files.walk(dir.toPath())
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .anyMatch(n -> !isMetaName(n));
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

    public static String sizeOfferText(Sizes s) {
        if (s == null || !s.known) {
            return "Download and unpacked sizes are unknown (missing sampleData/SIZE.txt).";
        }
        return s.zipMiB + " MB download, " + s.unpackedMiB + " MB on disk";
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
                        + "Download from GitHub Latest and unpack here?<br>"
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
        try {
            downloadAndUnpack(parent);
            JaerConstants.PREFS_ROOT.putBoolean(PREF_DECLINED, false);
            if (parent instanceof net.sf.jaer.graphics.AEViewer v) {
                SwingUtilities.invokeLater(v::refreshSampleDataHelpMenu);
            }
            openFolderAndReadme();
            return hasRecordings();
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
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
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
                dir.mkdirs();
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
     * Download and unpack the curated sample recordings into {@code sampleData/}.
     * <p>
     * Used by UI actions; progress UI is created on the Swing EDT.
     */
    public static void downloadAndUnpack(Component parent) throws Exception {
        File dir = folder();
        Files.createDirectories(dir.toPath());
        File zip = new File(dir, "jaer-sample-data.zip.partial");
        log.info("Downloading sample-data zip from " + DOWNLOAD_URL + " -> " + zip.getAbsolutePath());
        try {
            downloadTo(parent, DOWNLOAD_URL, zip);
            log.info("Unpacking sample-data zip into " + dir.getAbsolutePath());
            unzipTo(zip, dir);
        } finally {
            Files.deleteIfExists(zip.toPath());
        }
        log.info("Unpacked sample recordings into " + dir.getAbsolutePath());
    }

    private static void downloadTo(Component parent, String urlString, File dest) throws Exception {
        final JProgressBar[] barHolder = new JProgressBar[1];
        final javax.swing.JDialog[] dialogHolder = new javax.swing.JDialog[1];
        Runnable initUi = () -> {
            JProgressBar bar = new JProgressBar(0, 100);
            bar.setStringPainted(true);
            barHolder[0] = bar;

            JLabel label = new JLabel("Downloading jaer-sample-data.zip …");
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.add(label, BorderLayout.NORTH);
            panel.add(bar, BorderLayout.CENTER);
            panel.setPreferredSize(new Dimension(420, 70));

            JOptionPane pane = new JOptionPane(panel, JOptionPane.INFORMATION_MESSAGE,
                    JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
            javax.swing.JDialog dialog = pane.createDialog(parent, "Downloading sample recordings");
            dialog.setModal(false);
            dialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);
            dialogHolder[0] = dialog;
            dialog.setVisible(true);
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
                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                    byte[] buf = new byte[64 * 1024];
                    long read = 0;
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        out.write(buf, 0, n);
                        read += n;
                        final int pct = total > 0 ? (int) Math.min(100, (read * 100) / total) : 0;
                        final long readMb = read / (1024 * 1024);
                        SwingUtilities.invokeLater(() -> {
                            bar.setValue(pct);
                            bar.setString(total > 0
                                    ? String.format(Locale.ROOT, "%d%% (%d MB)", pct, readMb)
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
            worker.join();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new Exception("Download interrupted", ie);
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
