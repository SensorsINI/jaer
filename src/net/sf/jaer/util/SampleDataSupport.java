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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import net.sf.jaer.JaerConstants;

/**
 * Optional download of curated recordings into {@code sampleData/}.
 * The zip is a GitHub Release asset; sizes come from shipped {@code SIZE.txt}.
 */
public final class SampleDataSupport {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    public static final String DOWNLOAD_URL = JaerConstants.SAMPLE_DATA_DOWNLOAD_URL;

    public static final String PREF_DECLINED = "AEViewer.sampleDataDownloadDeclined";

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
            return true;
        }
        if (!force && JaerConstants.PREFS_ROOT.getBoolean(PREF_DECLINED, false)) {
            return false;
        }
        Sizes sizes = readSizes();
        String sizeLine = sizeOfferText(sizes);
        int choice = JOptionPane.showConfirmDialog(parent,
                "<html>jAER sample recordings are not in this <code>sampleData</code> folder.<br><br>"
                        + sizeLine + ".<br><br>"
                        + "Download from GitHub Latest and unpack here?<br>"
                        + "<code>" + DOWNLOAD_URL + "</code>",
                "Download sample recordings?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            if (!force) {
                JaerConstants.PREFS_ROOT.putBoolean(PREF_DECLINED, true);
            }
            return false;
        }
        try {
            downloadAndUnpack(parent);
            JaerConstants.PREFS_ROOT.putBoolean(PREF_DECLINED, false);
            return hasRecordings();
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Sample data download failed: " + ex, ex);
            JOptionPane.showMessageDialog(parent,
                    "<html>Download failed:<br>" + ex.getMessage()
                            + "<br><br>Manual: <code>" + DOWNLOAD_URL + "</code>"
                            + "<br>Unpack into <code>" + folder().getAbsolutePath() + "</code>",
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

    static void downloadAndUnpack(Component parent) throws Exception {
        File dir = folder();
        Files.createDirectories(dir.toPath());
        File zip = new File(dir, "jaer-sample-data.zip.partial");
        try {
            downloadTo(parent, DOWNLOAD_URL, zip);
            unzipTo(zip, dir);
        } finally {
            Files.deleteIfExists(zip.toPath());
        }
        log.info("Unpacked sample recordings into " + dir.getAbsolutePath());
    }

    private static void downloadTo(Component parent, String urlString, File dest) throws Exception {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        JLabel label = new JLabel("Downloading jaer-sample-data.zip …");
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(label, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(420, 70));
        JOptionPane pane = new JOptionPane(panel, JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
        final javax.swing.JDialog dialog = pane.createDialog(parent, "Downloading sample recordings");
        dialog.setModal(false);
        dialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);

        Exception[] error = new Exception[1];
        Thread worker = new Thread(() -> {
            try {
                HttpURLConnection conn = openFollowingRedirects(urlString);
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP " + code + " for " + urlString);
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
        dialog.setVisible(true);
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
            throw new Exception("HTTP " + code + " for " + url);
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
