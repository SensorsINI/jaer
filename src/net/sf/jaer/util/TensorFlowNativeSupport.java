package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * Central into {@code lib/} (if writable) or {@code ~/.jaer/lib/}, adds it to the
 * application classpath when possible, and otherwise asks the user to restart jAER.
 */
public final class TensorFlowNativeSupport {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** Must match {@code ivy.xml} tensorflow-core-platform revision. */
    public static final String TF_VERSION = "1.0.0-rc.2";

    private static final String MAVEN_DIR
            = "https://repo1.maven.org/maven2/org/tensorflow/tensorflow-core-native/" + TF_VERSION + "/";

    private static final AtomicBoolean classpathAugmented = new AtomicBoolean(false);

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
     * Adds previously downloaded TensorFlow native jars under {@code ~/.jaer/lib}.
     * Only expected {@code tensorflow-core-native-&lt;version&gt;-&lt;os&gt;.jar}
     * names are added. Call early from {@code JAERViewer.main}.
     */
    public static void installDownloadedJarsOnClasspath() {
        if (!classpathAugmented.compareAndSet(false, true)) {
            return;
        }
        File userLib = userLibDir();
        if (!userLib.isDirectory()) {
            return;
        }
        File[] jars = userLib.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            if (!isAllowedNativeJarName(jar.getName())) {
                log.warning("Skipping unexpected jar (not a known TensorFlow native artifact): "
                        + jar.getAbsolutePath());
                continue;
            }
            try {
                addJarToClasspath(jar);
                log.info("Added optional jar to classpath: " + jar.getAbsolutePath());
            } catch (Exception ex) {
                log.log(Level.WARNING, "Could not add optional jar " + jar + ": " + ex, ex);
            }
        }
    }

    /** Only TensorFlow platform native jars downloaded by this helper. */
    static boolean isAllowedNativeJarName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String n = fileName.toLowerCase(Locale.ROOT);
        String expected = nativeJarFileName().toLowerCase(Locale.ROOT);
        if (n.equals(expected)) {
            return true;
        }
        String prefix = ("tensorflow-core-native-" + TF_VERSION + "-").toLowerCase(Locale.ROOT);
        return n.startsWith(prefix) && n.endsWith(".jar") && !n.contains("..");
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
            try {
                addJarToClasspath(existing);
                if (isNativePresent()) {
                    log.info("Loaded TensorFlow natives from " + existing.getAbsolutePath());
                    return true;
                }
            } catch (Exception ex) {
                log.log(Level.WARNING, "Found " + existing + " but could not load it: " + ex, ex);
            }
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

        try {
            addJarToClasspath(dest);
            if (isNativePresent()) {
                JOptionPane.showMessageDialog(parent,
                        "<html>Downloaded and loaded:<br><code>" + dest.getAbsolutePath() + "</code>",
                        "TensorFlow natives ready",
                        JOptionPane.INFORMATION_MESSAGE);
                return true;
            }
        } catch (Exception ex) {
            log.log(Level.WARNING, "Download OK but classpath add failed: " + ex, ex);
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
        for (File dir : candidateLibDirs()) {
            File f = new File(dir, name);
            if (f.isFile()) {
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
                if (dest.exists() && !dest.delete()) {
                    throw new Exception("Could not replace existing " + dest);
                }
                if (!partial.renameTo(dest)) {
                    Files.move(partial.toPath(), dest.toPath());
                }
                log.info("Downloaded TensorFlow natives to " + dest.getAbsolutePath());
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
     * Best-effort add of a jar to application classloaders (install4j custom loader,
     * context loader, and system loader). Java 8 URLClassLoader or Java 9+ UCP / instrumentation.
     */
    public static void addJarToClasspath(File jar) throws Exception {
        URL url = jar.toURI().toURL();
        Exception last = null;
        boolean any = false;
        for (ClassLoader cl : candidateClassLoaders()) {
            if (cl == null) {
                continue;
            }
            try {
                if (addUrlToLoader(cl, url)) {
                    any = true;
                }
            } catch (Exception ex) {
                last = ex;
            }
        }
        if (any) {
            return;
        }
        if (last != null) {
            throw new Exception(
                    "Cannot add jar to classpath on this JVM without restart. Saved at " + jar.getAbsolutePath(),
                    last);
        }
        throw new Exception(
                "Cannot add jar to classpath on this JVM without restart. Saved at " + jar.getAbsolutePath());
    }

    private static boolean addUrlToLoader(ClassLoader cl, URL url) throws Exception {
        if (cl instanceof URLClassLoader) {
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(cl, url);
            return true;
        }
        try {
            Method append = cl.getClass().getDeclaredMethod("appendToClassPathForInstrumentation", String.class);
            append.setAccessible(true);
            append.invoke(cl, new File(url.toURI()).getAbsolutePath());
            return true;
        } catch (NoSuchMethodException ignore) {
            // fall through
        }
        // AppClassLoader / install4j: URLClassPath via getURLClassPath or ucp field
        ReflectiveOperationException last = null;
        for (String methodName : new String[]{"getURLClassPath", "getUcp"}) {
            try {
                Method getUcp = cl.getClass().getDeclaredMethod(methodName);
                getUcp.setAccessible(true);
                Object ucp = getUcp.invoke(cl);
                Method addURL = ucp.getClass().getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                addURL.invoke(ucp, url);
                return true;
            } catch (ReflectiveOperationException ex) {
                last = ex;
            }
        }
        try {
            java.lang.reflect.Field ucpField = findField(cl.getClass(), "ucp");
            if (ucpField != null) {
                ucpField.setAccessible(true);
                Object ucp = ucpField.get(cl);
                Method addURL = ucp.getClass().getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                addURL.invoke(ucp, url);
                return true;
            }
        } catch (ReflectiveOperationException ex) {
            last = ex;
        }
        if (last != null) {
            throw last;
        }
        return false;
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
