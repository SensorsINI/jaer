package net.sf.jaer.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Acceptance tests for integrity-checking optional TensorFlow native artifacts.
 * Fixtures are local inert JARs; these tests never load a native library.
 */
public class TensorFlowNativeSupportTest {

    private static final Set<String> SUPPORTED_CLASSIFIERS = Set.of(
            "windows-x86_64",
            "macosx-arm64",
            "macosx-x86_64",
            "linux-arm64",
            "linux-x86_64");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String originalUserDir;
    private String originalUserHome;
    private String originalOsName;
    private String originalOsArch;
    private ClassLoader originalContextClassLoader;
    private AtomicBoolean classpathAugmented;
    private boolean originalClasspathAugmented;

    @Before
    public void isolateMutableJvmState() throws Exception {
        originalUserDir = System.getProperty("user.dir");
        originalUserHome = System.getProperty("user.home");
        originalOsName = System.getProperty("os.name");
        originalOsArch = System.getProperty("os.arch");
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        classpathAugmented = classpathAugmentedGuard();
        if (classpathAugmented != null) {
            originalClasspathAugmented = classpathAugmented.get();
            classpathAugmented.set(false);
        }
    }

    @After
    public void restoreMutableJvmState() {
        restoreProperty("user.dir", originalUserDir);
        restoreProperty("user.home", originalUserHome);
        restoreProperty("os.name", originalOsName);
        restoreProperty("os.arch", originalOsArch);
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        if (classpathAugmented != null) {
            classpathAugmented.set(originalClasspathAugmented);
        }
    }

    @Test
    public void everySupportedClassifierHasPinnedSha256() throws Exception {
        String[][] osArchCases = {
            {"Windows 11", "amd64", "windows-x86_64"},
            {"Mac OS X", "amd64", "macosx-x86_64"},
            {"Mac OS X", "aarch64", "macosx-arm64"},
            {"Linux", "amd64", "linux-x86_64"},
            {"Linux", "aarch64", "linux-arm64"}
        };

        Set<String> classifiersProducedByPlatformSelection = new HashSet<>();
        for (String[] testCase : osArchCases) {
            System.setProperty("os.name", testCase[0]);
            System.setProperty("os.arch", testCase[1]);
            String classifier = TensorFlowNativeSupport.platformClassifier();
            assertEquals(testCase[2], classifier);
            classifiersProducedByPlatformSelection.add(classifier);
        }
        assertEquals("the digest pins must cover exactly the classifiers selected at runtime",
                SUPPORTED_CLASSIFIERS, classifiersProducedByPlatformSelection);

        Map<String, String> pins = pinnedSha256ByClassifier();
        assertEquals("every supported classifier, and no unrelated artifact, must be pinned",
                SUPPORTED_CLASSIFIERS, pins.keySet());

        Set<String> distinctPins = new HashSet<>();
        for (String classifier : SUPPORTED_CLASSIFIERS) {
            String digest = pins.get(classifier);
            assertNotNull("missing SHA-256 pin for " + classifier, digest);
            assertTrue("SHA-256 pin for " + classifier + " must be 64 lowercase hexadecimal characters",
                    digest.matches("[0-9a-f]{64}"));
            assertFalse("SHA-256 pin for " + classifier + " must not be a placeholder",
                    digest.matches("([0-9a-f])\\1{63}"));
            distinctPins.add(digest);
        }
        assertEquals("different platform artifacts must not share a placeholder digest",
                SUPPORTED_CLASSIFIERS.size(), distinctPins.size());
    }

    @Test
    public void wrongDigestPartialIsRejectedAndDeletedBeforePromotion() throws Exception {
        File installLib = configureInstallLib("wrong-partial");
        File destination = new File(installLib, TensorFlowNativeSupport.nativeJarFileName());
        File partial = new File(installLib, destination.getName() + ".partial");
        createFixtureJar(partial, false, "bytes supplied by an untrusted download");

        String expectedSha256 = sha256("different trusted bytes".getBytes(StandardCharsets.UTF_8));
        assertFalse("fixture setup must exercise a digest mismatch",
                expectedSha256.equals(sha256(Files.readAllBytes(partial.toPath()))));

        TrackingClassLoader trackingLoader = installTrackingContextClassLoader();
        Method promotion = verifiedPromotionMethod();
        Throwable rejection = null;
        try {
            invokeStatic(promotion, partial, destination, expectedSha256);
        } catch (Throwable t) {
            rejection = t;
        }

        assertNotNull("a wrong-digest partial file must be rejected", rejection);
        assertTrue("digest rejection must identify the integrity failure: " + messageChain(rejection),
                messageChain(rejection).matches("(?is).*(sha-?256|digest|checksum|integrity).*"));
        assertFalse("a rejected partial file must be deleted", partial.exists());
        assertFalse("a rejected partial file must never create the destination", destination.exists());
        assertEquals("digest rejection must occur before any class-loader mutation",
                0, trackingLoader.mutationAttempts());
    }

    @Test
    public void wrongDigestDestinationIsRejectedAndDeletedByLocalDiscovery() throws Exception {
        File installLib = configureInstallLib("wrong-destination");
        File destination = new File(installLib, TensorFlowNativeSupport.nativeJarFileName());
        createFixtureJar(destination, false, "tampered destination");

        TrackingClassLoader trackingLoader = installTrackingContextClassLoader();
        Method findLocalNativeJar = TensorFlowNativeSupport.class.getDeclaredMethod("findLocalNativeJar");
        findLocalNativeJar.setAccessible(true);
        File discovered = (File) invokeStatic(findLocalNativeJar);

        assertNull("a destination whose bytes do not match its classifier pin must not be accepted",
                discovered);
        assertFalse("a wrong-digest destination must be deleted", destination.exists());
        assertEquals("destination verification must not touch class loaders",
                0, trackingLoader.mutationAttempts());
    }

    @Test
    public void tamperedExistingJarIsRejectedAndDeletedByStartupScan() throws Exception {
        File userLib = configureUserLib("tampered-existing");
        File existing = new File(userLib, TensorFlowNativeSupport.nativeJarFileName());
        createFixtureJar(existing, false, "valid JAR structure, tampered content");

        installTrackingContextClassLoader();
        TensorFlowNativeSupport.installDownloadedJarsOnClasspath();

        assertFalse("a tampered existing TensorFlow native JAR must be deleted", existing.exists());
    }

    @Test
    public void wrongDigestStartupScanDoesNotMutateClassLoader() throws Exception {
        File userLib = configureUserLib("startup-no-hot-load");
        File existing = new File(userLib, TensorFlowNativeSupport.nativeJarFileName());
        createFixtureJar(existing, false, "wrong digest must be rejected before loading");

        TrackingClassLoader trackingLoader = installTrackingContextClassLoader();
        TensorFlowNativeSupport.installDownloadedJarsOnClasspath();

        assertEquals("a wrong-digest startup artifact must not attempt class-loader mutation",
                0, trackingLoader.mutationAttempts());
        assertFalse("the rejected startup artifact must be deleted", existing.exists());
    }

    @Test
    public void verifiedDownloadIsAtomicallyPromotedForRestartWithoutHotLoad() throws Exception {
        File installLib = configureInstallLib("verified-promotion");
        File destination = new File(installLib, TensorFlowNativeSupport.nativeJarFileName());
        File partial = new File(installLib, destination.getName() + ".partial");
        createFixtureJar(partial, true, "locally verified fixture");
        byte[] verifiedBytes = Files.readAllBytes(partial.toPath());
        String expectedSha256 = sha256(verifiedBytes);
        Object partialFileKey = Files.readAttributes(
                partial.toPath(), BasicFileAttributes.class).fileKey();

        Files.write(destination.toPath(), "stale destination".getBytes(StandardCharsets.UTF_8));
        TrackingClassLoader trackingLoader = installTrackingContextClassLoader();
        assertFalse("fixture precondition: TensorFlow native probe is not already on the test classpath",
                TensorFlowNativeSupport.isNativePresent());

        Method promotion = verifiedPromotionMethod();
        invokeStatic(promotion, partial, destination, expectedSha256);

        assertFalse("atomic promotion must consume the verified partial file", partial.exists());
        assertTrue("atomic promotion must create the final destination", destination.isFile());
        assertArrayEquals("the promoted destination must contain exactly the verified bytes",
                verifiedBytes, Files.readAllBytes(destination.toPath()));
        assertEquals("the promoted destination must retain the verified SHA-256",
                expectedSha256, sha256(Files.readAllBytes(destination.toPath())));

        Object destinationFileKey = Files.readAttributes(
                destination.toPath(), BasicFileAttributes.class).fileKey();
        if (partialFileKey != null && destinationFileKey != null) {
            assertEquals("promotion must move the verified file rather than copy bytes into place",
                    partialFileKey, destinationFileKey);
        }

        assertEquals("verified promotion must not mutate a live class loader",
                0, trackingLoader.mutationAttempts());
        assertFalse("the promoted native resource must remain unavailable until jAER restarts",
                TensorFlowNativeSupport.isNativePresent());
    }

    private File configureInstallLib(String name) throws IOException {
        File workDir = temporaryFolder.newFolder(name + "-work");
        File homeDir = temporaryFolder.newFolder(name + "-home");
        File installLib = new File(workDir, "lib");
        Files.createDirectories(installLib.toPath());
        System.setProperty("user.dir", workDir.getAbsolutePath());
        System.setProperty("user.home", homeDir.getAbsolutePath());
        return installLib;
    }

    private File configureUserLib(String name) throws IOException {
        File workDir = temporaryFolder.newFolder(name + "-work");
        File homeDir = temporaryFolder.newFolder(name + "-home");
        File userLib = new File(homeDir, ".jaer" + File.separator + "lib");
        Files.createDirectories(userLib.toPath());
        System.setProperty("user.dir", workDir.getAbsolutePath());
        System.setProperty("user.home", homeDir.getAbsolutePath());
        return userLib;
    }

    private TrackingClassLoader installTrackingContextClassLoader() {
        TrackingClassLoader loader = new TrackingClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        return loader;
    }

    private static Method verifiedPromotionMethod() {
        Class<?>[] expectedParameters = {File.class, File.class, String.class};
        for (Method method : TensorFlowNativeSupport.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            if (Modifier.isPrivate(method.getModifiers())
                    && Modifier.isStatic(method.getModifiers())
                    && Arrays.equals(expectedParameters, method.getParameterTypes())
                    && name.contains("promot")
                    && (name.contains("verif") || name.contains("digest") || name.contains("sha"))) {
                method.setAccessible(true);
                return method;
            }
        }
        fail("TensorFlowNativeSupport needs a private static verified-promotion seam "
                + "with parameters (File partial, File destination, String expectedSha256)");
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pinnedSha256ByClassifier() throws Exception {
        for (Field field : TensorFlowNativeSupport.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof Map)) {
                continue;
            }
            Map<?, ?> candidate = (Map<?, ?>) value;
            if (!candidate.keySet().containsAll(SUPPORTED_CLASSIFIERS)) {
                continue;
            }
            assertTrue("classifier SHA-256 pins must be private",
                    Modifier.isPrivate(field.getModifiers()));
            assertTrue("classifier SHA-256 pins must be final",
                    Modifier.isFinal(field.getModifiers()));
            return (Map<String, String>) candidate;
        }
        fail("TensorFlowNativeSupport needs a private static final SHA-256 map keyed by every "
                + "supported classifier");
        return Map.of();
    }

    private static AtomicBoolean classpathAugmentedGuard() throws Exception {
        try {
            Field field = TensorFlowNativeSupport.class.getDeclaredField("classpathAugmented");
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof AtomicBoolean ? (AtomicBoolean) value : null;
        } catch (NoSuchFieldException e) {
            // A restart-only implementation may remove the old hot-load guard entirely.
            return null;
        }
    }

    private static Object invokeStatic(Method method, Object... arguments) throws Exception {
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new AssertionError(cause);
        }
    }

    private static void createFixtureJar(File file, boolean includeNativeProbe, String payload)
            throws IOException {
        Files.createDirectories(file.toPath().getParent());
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(file))) {
            putJarEntry(jar, "fixture/payload.txt", payload.getBytes(StandardCharsets.UTF_8));
            if (includeNativeProbe) {
                putJarEntry(jar, TensorFlowNativeSupport.probeResourcePath(),
                        "inert test bytes; not a native library".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void putJarEntry(JarOutputStream jar, String name, byte[] contents)
            throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        jar.putNextEntry(entry);
        jar.write(contents);
        jar.closeEntry();
    }

    private static String sha256(byte[] contents) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(contents);
        StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hexadecimal.append(String.format("%02x", value & 0xff));
        }
        return hexadecimal.toString();
    }

    private static String messageChain(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                if (messages.length() > 0) {
                    messages.append(": ");
                }
                messages.append(current.getMessage());
            }
        }
        return messages.toString();
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    /** Records a hot-add request but deliberately does not mutate any class path. */
    private static final class TrackingClassLoader extends ClassLoader {

        private int mutationAttempts;

        TrackingClassLoader() {
            super(null);
        }

        @SuppressWarnings("unused")
        private void appendToClassPathForInstrumentation(String path) {
            mutationAttempts++;
        }

        int mutationAttempts() {
            return mutationAttempts;
        }
    }
}
