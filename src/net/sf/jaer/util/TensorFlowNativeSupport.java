package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/**
 * Ensures TensorFlow platform JNI natives are available for {@code MLPNoiseFilter}.
 * <p>
 * Large {@code tensorflow-core-native-*-&lt;os&gt;.jar} files are omitted from installers
 * to shrink media size. On first use this helper downloads the current-OS jar from Maven
 * Central into {@code lib/} (if writable) or {@code ~/.jaer/lib/}, verifies its pinned
 * SHA-256 digest, and asks the user to restart jAER.
 */
public final class TensorFlowNativeSupport {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** Must match {@code ivy.xml} tensorflow-core-platform revision. */
    public static final String TF_VERSION = "1.0.0-rc.2";

    private static final String MAVEN_DIR
            = "https://repo.maven.apache.org/maven2/org/tensorflow/tensorflow-core-native/"
                    + TF_VERSION + "/";

    /** SHA-256 of the exact TensorFlow Java native artifacts published by Maven Central. */
    private static final Map<String, String> PINNED_SHA256_BY_CLASSIFIER = Map.of(
            "windows-x86_64", "8367da64f3f29c107d6001eafbbdc499927c94c7e4f459685c84f2906e197438",
            "macosx-arm64", "629bafa67bc30ca47681d69c85f84e7b617210b49cf65b1bc10634d573d75cd0",
            "macosx-x86_64", "cd269c61e0cabc80cbc36c0f0c75091118b5495e55ef03c42461ad617f8ec207",
            "linux-arm64", "200e3224de93ada2734c772cb5a7d6b7a215b6e81b6784793a1de15e1950c466",
            "linux-x86_64", "33a2f923c6f1c342758e713b9badf889d6bd0be26df4799719e6b748ff524b16");

    private TensorFlowNativeSupport() {
    }

    /** OS/arch classifier used by TensorFlow Java native artifacts. */
    public static String platformClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.equals("arm64");
        if (os.contains("win")) {
            return "windows-x86_64";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return arm ? "macosx-arm64" : "macosx-x86_64";
        }
        // linux and other unix
        return arm ? "linux-arm64" : "linux-x86_64";
    }

    public static String nativeJarFileName() {
        return "tensorflow-core-native-" + TF_VERSION + "-" + platformClassifier() + ".jar";
    }

    public static String mavenDownloadUrl() {
        return MAVEN_DIR + nativeJarFileName();
    }

    /** Resource path inside the platform jar used to probe whether natives are on the classpath. */
    public static String probeResourcePath() {
        String c = platformClassifier();
        if (c.startsWith("windows")) {
            return "org/tensorflow/internal/c_api/" + c + "/tensorflow.dll";
        }
        if (c.startsWith("macosx")) {
            return "org/tensorflow/internal/c_api/" + c + "/libtensorflow_cc.2.dylib";
        }
        return "org/tensorflow/internal/c_api/" + c + "/libtensorflow_cc.so.2";
    }

    /** ClassLoaders that may see install4j / IDE / dynamically added jars. */
    private static ClassLoader[] candidateClassLoaders() {
        ClassLoader app = TensorFlowNativeSupport.class.getClassLoader();
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        ClassLoader sys = ClassLoader.getSystemClassLoader();
        if (ctx != null && ctx != app && ctx != sys) {
            return new ClassLoader[]{app, ctx, sys};
        }
        if (sys != null && sys != app) {
            return new ClassLoader[]{app, sys};
        }
        return new ClassLoader[]{app};
    }

    public static boolean isNativePresent() {
        String probe = probeResourcePath();
        for (ClassLoader cl : candidateClassLoaders()) {
            if (cl != null && cl.getResource(probe) != null) {
                return true;
            }
        }
        return false;
    }

    /** Location of {@code org.bytedeco.javacpp.Loader} (detect leftover javacpp-1.4 on classpath). */
    public static String javacppLoaderJar() {
        try {
            java.security.CodeSource cs = org.bytedeco.javacpp.Loader.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                return cs.getLocation().toString();
            }
        } catch (Throwable ignore) {
        }
        return "(unknown)";
    }

    public static boolean isCompatibleJavacpp() {
        String loc = javacppLoaderJar().toLowerCase(Locale.ROOT);
        if (loc.contains("javacpp-1.4")) {
            return false;
        }
        // Prefer 1.5.10+; allow unknown (IDE) if not clearly 1.4
        return true;
    }

    /**
     * Verifies previously downloaded TensorFlow native jars under {@code ~/.jaer/lib}.
     * The historical method name is retained as public API, but startup never mutates a
     * live class loader. Invalid artifacts are deleted and valid artifacts require restart.
     */
    public static void installDownloadedJarsOnClasspath() {
        File userLib = userLibDir();
        if (!userLib.isDirectory()) {
            return;
        }
        File[] jars = userLib.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            String expectedSha256 = expectedSha256ForFileName(jar.getName());
            if (expectedSha256 == null) {
                log.warning("Skipping unexpected jar (not a known TensorFlow native artifact): "
                        + jar.getAbsolutePath());
                continue;
            }
            if (verifyInstalledJar(jar, expectedSha256, "startup scan")) {
                log.info("Verified optional TensorFlow native jar for restart: "
                        + jar.getAbsolutePath());
            }
        }
    }

    /** Only TensorFlow platform native jars downloaded by this helper. */
    static boolean isAllowedNativeJarName(String fileName) {
        return expectedSha256ForFileName(fileName) != null;
    }

    /**
     * If natives are missing, prompts to download them. Returns true if TF natives
     * are available afterwards (or already were).
     *
     * @param parent dialog parent, may be null
     */
    public static boolean ensureAvailable(Component parent) {
        installDownloadedJarsOnClasspath();
        if (!isCompatibleJavacpp()) {
            String loc = javacppLoaderJar();
            log.severe("TensorFlow requires javacpp 1.5.10+, but Loader is from: " + loc
                    + " — delete lib/javacpp-1.4.jar (upgrade leftover) and restart jAER.");
            JOptionPane.showMessageDialog(parent,
                    "<html>MLPNoiseFilter needs <b>javacpp 1.5.10</b>, but an old<br>"
                            + "<code>javacpp-1.4.jar</code> is still on the classpath<br>"
                            + "(common after upgrading over an older install).<br><br>"
                            + "Delete <code>lib/javacpp-1.4.jar</code> from the jAER install folder<br>"
                            + "and restart. New installers remove this leftover automatically.<br><br>"
                            + "<small>" + loc + "</small></html>",
                    "Incompatible javacpp",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (isNativePresent()) {
            return true;
        }

        // Jar may already sit in lib/ but not yet on this JVM's classpath (e.g. just downloaded).
        File existing = findLocalNativeJar();
        if (existing != null && existing.isFile()) {
            promptRestart(parent, existing);
            return false;
        }

        String jarName = nativeJarFileName();
        String url = mavenDownloadUrl();
        long approxMb = estimateSizeMb();
        int choice = JOptionPane.showConfirmDialog(
                parent,
                "<html>MLPNoiseFilter needs the TensorFlow native library for <b>"
                        + platformClassifier() + "</b> (~" + approxMb + " MB).<br><br>"
                        + "Download from Maven Central?<br>"
                        + "<code>" + url + "</code><br><br>"
                        + "Saved under <code>lib/</code> or <code>~/.jaer/lib/</code>. "
                        + "A restart of jAER may be required.",
                "Download TensorFlow natives?",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }

        File dest;
        try {
            dest = downloadNativeJar(parent, url, jarName);
        } catch (Exception ex) {
            log.log(Level.SEVERE, "TensorFlow native download failed: " + ex, ex);
            JOptionPane.showMessageDialog(parent,
                    "<html>Download failed:<br>" + ex.getMessage()
                            + "<br><br>For air-gapped machines, place <code>" + jarName
                            + "</code> into the jAER <code>lib</code> folder and restart.",
                    "TensorFlow download failed",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        promptRestart(parent, dest);
        return false;
    }

    private static void promptRestart(Component parent, File jar) {
        JOptionPane.showMessageDialog(parent,
                "<html>TensorFlow natives are at:<br><code>" + jar.getAbsolutePath()
                        + "</code><br><br>Please <b>restart jAER</b> so the library is on the classpath, "
                        + "then enable MLPNoiseFilter again.",
                "Restart required",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static long estimateSizeMb() {
        String c = platformClassifier();
        if (c.startsWith("windows")) {
            return 74;
        }
        if (c.contains("macosx-arm64")) {
            return 122;
        }
        if (c.contains("macosx")) {
            return 146;
        }
        if (c.contains("arm64")) {
            return 142;
        }
        return 142;
    }

    private static File findLocalNativeJar() {
        String name = nativeJarFileName();
        String expectedSha256 = expectedSha256ForClassifier(platformClassifier());
        for (File dir : candidateLibDirs()) {
            File f = new File(dir, name);
            if (f.isFile() && verifyInstalledJar(f, expectedSha256, "local discovery")) {
                return f;
            }
        }
        return null;
    }

    private static File[] candidateLibDirs() {
        return new File[]{
            new File(System.getProperty("user.dir", "."), "lib"),
            userLibDir()
        };
    }

    public static File userLibDir() {
        return new File(System.getProperty("user.home"), ".jaer" + File.separator + "lib");
    }

    private static File chooseWritableLibDir() throws Exception {
        File installLib = new File(System.getProperty("user.dir", "."), "lib");
        if (installLib.isDirectory() && installLib.canWrite()) {
            File probe = new File(installLib, ".jaer-write-test");
            try {
                if (probe.createNewFile() || probe.exists()) {
                    probe.delete();
                    return installLib;
                }
            } catch (Exception ignore) {
                // fall through to user dir
            }
        }
        File userLib = userLibDir();
        Files.createDirectories(userLib.toPath());
        return userLib;
    }

    private static File downloadNativeJar(Component parent, String urlString, String jarName) throws Exception {
        File dir = chooseWritableLibDir();
        File dest = new File(dir, jarName);
        File partial = new File(dir, jarName + ".partial");
        String expectedSha256 = expectedSha256ForClassifier(platformClassifier());

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        JLabel label = new JLabel("Downloading " + jarName + " …");
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(label, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(420, 70));

        JOptionPane pane = new JOptionPane(panel, JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
        final javax.swing.JDialog dialog = pane.createDialog(parent, "Downloading TensorFlow natives");
        dialog.setModal(false);
        dialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);

        Exception[] error = new Exception[1];
        Thread worker = new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                Files.deleteIfExists(partial.toPath());
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(120000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP " + code + " for " + urlString);
                }
                long total = conn.getContentLengthLong();
                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(partial))) {
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
                                    ? String.format("%d%% (%d MB)", pct, readMb)
                                    : String.format("%d MB", readMb));
                        });
                    }
                }
                verifyAndPromote(partial, dest, expectedSha256);
                log.info("Downloaded and SHA-256 verified TensorFlow natives at "
                        + dest.getAbsolutePath());
            } catch (Exception ex) {
                error[0] = ex;
                try {
                    Files.deleteIfExists(partial.toPath());
                } catch (Exception ignore) {
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                SwingUtilities.invokeLater(dialog::dispose);
            }
        }, "tf-native-download");
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
        if (!dest.isFile()) {
            throw new Exception("Download finished but file missing: " + dest);
        }
        return dest;
    }

    /**
     * Retained public API for callers compiled against older jAER releases. Runtime
     * class-loader mutation is intentionally disabled; verified installs are restart-only.
     */
    public static void addJarToClasspath(File jar) throws Exception {
        throw new Exception(
                "Runtime class-loader mutation is disabled. Restart jAER to use "
                        + jar.getAbsolutePath());
    }

    private static String expectedSha256ForClassifier(String classifier) {
        String digest = PINNED_SHA256_BY_CLASSIFIER.get(classifier);
        if (digest == null) {
            throw new IllegalStateException("No SHA-256 pin for TensorFlow classifier " + classifier);
        }
        return digest;
    }

    private static String expectedSha256ForFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        for (Map.Entry<String, String> pin : PINNED_SHA256_BY_CLASSIFIER.entrySet()) {
            String expectedName = "tensorflow-core-native-" + TF_VERSION + "-"
                    + pin.getKey() + ".jar";
            if (expectedName.equalsIgnoreCase(fileName)) {
                return pin.getValue();
            }
        }
        return null;
    }

    private static boolean verifyInstalledJar(File jar, String expectedSha256, String context) {
        try {
            String actualSha256 = sha256(jar);
            if (expectedSha256.equals(actualSha256)) {
                return true;
            }
            IOException mismatch = new IOException("SHA-256 integrity check failed during "
                    + context + " for " + jar + ": expected " + expectedSha256
                    + " but found " + actualSha256);
            deleteRejectedArtifacts(jar);
            log.log(Level.WARNING, mismatch.getMessage(), mismatch);
        } catch (Exception ex) {
            deleteRejectedArtifacts(jar);
            log.log(Level.WARNING, "Could not verify TensorFlow native jar during "
                    + context + ": " + jar, ex);
        }
        return false;
    }

    private static void verifyAndPromote(File partial, File destination, String expectedSha256)
            throws Exception {
        String actualSha256 = sha256(partial);
        if (!expectedSha256.equals(actualSha256)) {
            deleteRejectedArtifacts(partial, destination);
            throw new IOException("SHA-256 integrity check failed for " + partial
                    + ": expected " + expectedSha256 + " but found " + actualSha256);
        }

        try {
            Files.move(partial.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            // Same-directory move: no byte copy, and REPLACE_EXISTING avoids partial output.
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        String promotedSha256 = sha256(destination);
        if (!expectedSha256.equals(promotedSha256)) {
            deleteRejectedArtifacts(partial, destination);
            throw new IOException("SHA-256 integrity check failed after promotion for "
                    + destination + ": expected " + expectedSha256 + " but found "
                    + promotedSha256);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder hexadecimal = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hexadecimal.append(String.format("%02x", value & 0xff));
        }
        return hexadecimal.toString();
    }

    private static void deleteRejectedArtifacts(File... files) {
        for (File file : files) {
            if (file == null) {
                continue;
            }
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException ex) {
                log.log(Level.WARNING, "Could not delete rejected TensorFlow artifact " + file, ex);
            }
        }
    }
}
