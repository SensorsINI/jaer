package net.sf.jaer.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Build-time allowlist of concrete AEChip / EventFilter / DisplayMethod
 * subclasses packed in {@code jAER.jar}. Packaged jAER refuses
 * {@link Class#forName} of any other name (prefs leftovers, tmpdir cache,
 * dropped {@code dist/*.class} files).
 * <p>
 * {@code -Djaer.scanClasspath=true} forces a classpath rescan (git/dev only;
 * ignored when {@link #isPackaged()}).
 */
public final class JaerAllowedSubclasses {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** Classpath directory of generated UTF-8 FQCN lists. */
    public static final String RESOURCE_DIR = "net/sf/jaer/util/allowed-subclasses/";

    public static final String PROP_SCAN_CLASSPATH = "jaer.scanClasspath";

    private static final Map<String, Set<String>> CACHE = new ConcurrentHashMap<>();
    private static final Object PACKAGED_LOCK = new Object();
    private static Boolean packaged;

    private JaerAllowedSubclasses() {
    }

    public static String resourcePath(Class<?> superType) {
        return RESOURCE_DIR + superType.getName() + ".txt";
    }

    /**
     * True when running from an install4j tree or {@code jAER.jar} (not
     * {@code build/classes}). Packaged mode never falls back to a classpath
     * directory walk.
     */
    public static boolean isPackaged() {
        synchronized (PACKAGED_LOCK) {
            if (packaged != null) {
                return packaged;
            }
            packaged = detectPackaged();
            return packaged;
        }
    }

    private static boolean detectPackaged() {
        String appDir = System.getProperty("install4j.appDir");
        if (appDir != null && !appDir.isBlank()) {
            return true;
        }
        String userDir = System.getProperty("user.dir", ".");
        if (new File(userDir, ".install4j").isDirectory()) {
            return true;
        }
        try {
            CodeSource cs = net.sf.jaer.JAERViewer.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                String path = cs.getLocation().getPath();
                if (path != null) {
                    String norm = path.replace('\\', '/');
                    if (norm.endsWith("/jAER.jar") || norm.endsWith("jAER.jar")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.log(Level.FINE, "Could not inspect JAERViewer CodeSource", e);
        }
        return false;
    }

    /** Force a classpath scan even if a resource exists (git/dev Refresh). */
    public static boolean forceClasspathScan() {
        return Boolean.parseBoolean(System.getProperty(PROP_SCAN_CLASSPATH, "false"));
    }

    /**
     * Allowlist names for {@code superType}, or {@code null} if the resource is
     * missing (caller may scan in unpackaged mode). Empty set means the
     * resource existed but listed nothing. Packaged + missing → empty (fail
     * closed), never {@code null}.
     */
    public static Set<String> namesOrNullIfMissing(Class<?> superType) {
        if (superType == null) {
            return Collections.emptySet();
        }
        String key = superType.getName();
        Set<String> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Set<String> loaded = readResource(superType);
        if (loaded == null) {
            if (isPackaged()) {
                log.warning("Packaged jAER is missing allowlist resource " + resourcePath(superType)
                        + "; refusing all " + key + " loads (fail closed)");
                CACHE.put(key, Collections.emptySet());
                return Collections.emptySet();
            }
            return null;
        }
        CACHE.put(key, loaded);
        return loaded;
    }

    /**
     * True if {@code fqcn} may be loaded as {@code superType}. When no resource
     * exists in unpackaged mode, returns true so {@link #load} can still
     * {@code isAssignableFrom}-check (NetBeans compile-on-save).
     */
    public static boolean isAllowed(Class<?> superType, String fqcn) {
        if (fqcn == null || fqcn.isBlank() || superType == null) {
            return false;
        }
        Set<String> names = namesOrNullIfMissing(superType);
        if (names == null) {
            return !isPackaged();
        }
        return names.contains(fqcn.trim());
    }

    /**
     * {@link Class#forName} only after the allowlist and
     * {@code superType.isAssignableFrom} (and not abstract).
     */
    public static Class<?> load(String fqcn, Class<?> superType) throws ClassNotFoundException {
        if (fqcn == null || fqcn.isBlank()) {
            throw new ClassNotFoundException("null class name");
        }
        String name = fqcn.trim();
        if (!isAllowed(superType, name)) {
            throw new ClassNotFoundException(name + " is not an allowed " + superType.getName());
        }
        Class<?> c;
        try {
            c = Class.forName(name);
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            throw new ClassNotFoundException("Could not initialize " + name + ": " + e, e);
        }
        if (!superType.isAssignableFrom(c)) {
            throw new ClassNotFoundException(name + " is not assignable to " + superType.getName());
        }
        if (Modifier.isAbstract(c.getModifiers())) {
            throw new ClassNotFoundException(name + " is abstract");
        }
        return c;
    }

    public static Class<?> loadOrNull(String fqcn, Class<?> superType) {
        try {
            return load(fqcn, superType);
        } catch (ClassNotFoundException e) {
            log.fine(e.getMessage());
            return null;
        }
    }

    /**
     * Concrete subclass FQCNs from the resource, or empty if missing in
     * packaged mode. Does not scan the classpath.
     */
    public static List<String> listedNames(Class<?> superType) {
        Set<String> names = namesOrNullIfMissing(superType);
        if (names == null) {
            return null;
        }
        return new ArrayList<>(names);
    }

    private static Set<String> readResource(Class<?> superType) {
        String path = resourcePath(superType);
        URL url = JaerAllowedSubclasses.class.getClassLoader().getResource(path);
        if (url == null) {
            return null;
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        try (InputStream in = url.openStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                set.add(line);
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not read allowlist " + path, e);
            return null;
        }
        log.info("Loaded " + set.size() + " allowed subclasses of " + superType.getName()
                + " from " + path);
        return Collections.unmodifiableSet(set);
    }
}
