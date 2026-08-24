/* SubclassFinder.java
 * Created on May 13, 2007, 8:13 PM
 * Copyright May 13, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich */
package net.sf.jaer.util;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.DefaultListModel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

/**
 * Finds concrete subclasses of a given class. Packaged jAER uses the compiled
 * allowlist in {@code jAER.jar} ({@link JaerAllowedSubclasses}); git/dev falls
 * back to a classpath scan when the resource is missing or
 * {@code -Djaer.scanClasspath=true}.
 *
 * @author tobi
 */
public class SubclassFinder {

    private final static Logger log = Logger.getLogger("net.sf.jaer");

    /**
     * List of regexp package names to exclude from classpath search (dev scan
     * only). Ignored packages are also listed in {@link ListJaerClasses}.
     */
    public static final ArrayList<String> exclusionList = new ArrayList<>();

    private SubclassFinder() {
    }

    static final class FastClassFinder {

        static HashMap<String, Class<?>> map = new HashMap<>();

        static synchronized Class<?> forName(String name) {
            Class<?> c = map.get(name);
            if (c == null) {
                try {
                    c = Class.forName(name, false, SubclassFinder.class.getClassLoader());
                    map.put(name, c);
                } catch (ClassNotFoundException | LinkageError e) {
                    log.warning("caught " + e + " when trying to get class named " + name);
                }
            }
            return c;
        }
    }

    /**
     * Finds subclasses in SwingWorker
     */
    public static class SubclassFinderWorker extends SwingWorker<ArrayList<ClassNameWithDescriptionAndDevelopmentStatus>, ClassNameWithDescriptionAndDevelopmentStatus> {

        Class<?> clazz;
        private DefaultListModel<ClassNameWithDescriptionAndDevelopmentStatus> tableModel = null;
        private boolean useCacheIfAvailable = true;

        public SubclassFinderWorker(Class<?> clazz) {
            this.clazz = clazz;
        }

        /**
         * @param useCacheIfAvailable false to rescan the classpath (Refresh).
         * Packaged installs still use the allowlist.
         */
        public SubclassFinderWorker(Class clazz, DefaultListModel<ClassNameWithDescriptionAndDevelopmentStatus> model, boolean useCacheIfAvailable) {
            this(clazz);
            this.tableModel = model;
            this.useCacheIfAvailable = useCacheIfAvailable;
        }

        @Override
        protected ArrayList<ClassNameWithDescriptionAndDevelopmentStatus> doInBackground() throws Exception {
            long startTime = System.currentTimeMillis();
            setProgress(0);
            ArrayList<ClassNameWithDescriptionAndDevelopmentStatus> classes = new ArrayList<>(300);
            if (clazz == null) {
                log.warning("tried to find subclasses of null class, returning empty list");
                return classes;
            }
            boolean forceScan = !useCacheIfAvailable || JaerAllowedSubclasses.forceClasspathScan();
            if (forceScan && JaerAllowedSubclasses.isPackaged()) {
                forceScan = false;
            }
            List<String> names = null;
            if (!forceScan) {
                names = JaerAllowedSubclasses.listedNames(clazz);
            }
            if (names != null) {
                log.info("Using compiled allowlist of " + names.size() + " subclasses of "
                        + clazz.getName() + " (skipped classpath scan)");
                int n = Math.max(1, names.size());
                int i = 0;
                for (String fqcn : names) {
                    i++;
                    setProgress((int) (100f * i / n));
                    try {
                        Class<?> c = JaerAllowedSubclasses.load(fqcn, clazz);
                        ClassNameWithDescriptionAndDevelopmentStatus found
                                = new ClassNameWithDescriptionAndDevelopmentStatus(c);
                        classes.add(found);
                        publish(found);
                    } catch (ClassNotFoundException | LinkageError | RuntimeException e) {
                        // One broken optional dep (e.g. TensorFlow) must not empty the chooser.
                        log.warning("Could not load " + fqcn + " for description (still listing): " + e);
                        ClassNameWithDescriptionAndDevelopmentStatus found
                                = new ClassNameWithDescriptionAndDevelopmentStatus(fqcn, null, null);
                        classes.add(found);
                        publish(found);
                    }
                }
                log.info("Read " + classes.size() + " subclasses of " + clazz.getName()
                        + " from allowlist in " + (System.currentTimeMillis() - startTime) + " ms");
                return classes;
            }
            log.info("No allowlist for " + clazz.getName() + "; scanning classpath");
            classes.addAll(scanClasspath(clazz, (p) -> setProgress(p), (c) -> publish(c)));
            log.info("Scanned " + classes.size() + " subclasses of " + clazz.getName()
                    + " in " + (System.currentTimeMillis() - startTime) + " ms");
            return classes;
        }

        @Override
        protected void done() {
            setProgress(100);
        }

        @Override
        protected void process(List<ClassNameWithDescriptionAndDevelopmentStatus> list) {
            if (list == null || tableModel == null) {
                return;
            }
            for (ClassNameWithDescriptionAndDevelopmentStatus c : list) {
                tableModel.addElement(c);
            }
        }
    }

    @FunctionalInterface
    private interface ProgressSink {
        void accept(int percent);
    }

    @FunctionalInterface
    private interface FoundSink {
        void accept(ClassNameWithDescriptionAndDevelopmentStatus c);
    }

    static ArrayList<ClassNameWithDescriptionAndDevelopmentStatus> scanClasspath(Class<?> superClass,
            ProgressSink progress, FoundSink found) {
        ArrayList<ClassNameWithDescriptionAndDevelopmentStatus> classes = new ArrayList<>(300);
        List<String> allClasses = ListJaerClasses.listClasses();
        int n = ".class".length();
        if (allClasses.isEmpty()) {
            log.warning("List of subclasses of " + superClass.getName()
                    + " is empty. Run ant compile (not compile-on-save alone).");
        }
        int i = 0;
        int nclasses = Math.max(1, allClasses.size());
        int lastProgress = 0;
        allclassloop:
        for (String s : allClasses) {
            i++;
            try {
                int p = (int) ((float) i / nclasses * 100);
                if (progress != null && p > lastProgress + 5) {
                    progress.accept(p);
                    lastProgress = p;
                }
                s = s.substring(0, s.length() - n);
                s = s.replace('/', '.').replace('\\', '.');
                if (s.indexOf('$') != -1) {
                    continue allclassloop;
                }
                for (String excl : exclusionList) {
                    if (s.matches(excl)) {
                        continue allclassloop;
                    }
                }
                Class<?> c = FastClassFinder.forName(s);
                if (c == superClass || c == null) {
                    continue;
                }
                if (Modifier.isAbstract(c.getModifiers())) {
                    continue;
                }
                if (superClass.isAssignableFrom(c)) {
                    ClassNameWithDescriptionAndDevelopmentStatus myFoundClass
                            = new ClassNameWithDescriptionAndDevelopmentStatus(c);
                    classes.add(myFoundClass);
                    if (found != null) {
                        found.accept(myFoundClass);
                    }
                }
            } catch (LinkageError t) {
                log.fine(t + " while seeing if " + superClass + " isAssignableFrom " + s);
            } catch (Exception t) {
                log.fine(t + " while scanning " + s);
            }
        }
        return classes;
    }

    public static ArrayList<String> findSubclassesOf(String name) {
        return findSubclassesOf(name, null);
    }

    /**
     * Fully-qualified names of concrete subclasses of {@code superClassName}.
     */
    public static ArrayList<String> findSubclassesOf(String superClassName, final ProgressMonitor progressMonitor) {
        ArrayList<String> classes = new ArrayList<>(300);
        if (superClassName == null) {
            log.warning("tried to find subclasses of null class name, returning empty list");
            return classes;
        }
        Class<?> superClass = FastClassFinder.forName(superClassName);
        if (superClass == null) {
            log.warning("could not load superclass " + superClassName);
            return classes;
        }
        boolean forceScan = JaerAllowedSubclasses.forceClasspathScan() && !JaerAllowedSubclasses.isPackaged();
        List<String> names = forceScan ? null : JaerAllowedSubclasses.listedNames(superClass);
        if (names != null) {
            log.info("Using compiled allowlist of " + names.size() + " subclasses of "
                    + superClassName + " (skipped classpath scan)");
            if (progressMonitor != null) {
                progressMonitor.setNote("Loading allowlist");
                progressMonitor.setMaximum(Math.max(1, names.size()));
            }
            int i = 0;
            for (String fqcn : names) {
                if (progressMonitor != null) {
                    if (progressMonitor.isCanceled()) {
                        break;
                    }
                    progressMonitor.setProgress(i++);
                }
                classes.add(fqcn);
            }
            return classes;
        }
        if (JaerAllowedSubclasses.isPackaged()) {
            log.warning("Packaged jAER has no allowlist for " + superClassName + "; returning empty list");
            return classes;
        }
        log.info("No allowlist for " + superClassName + "; scanning classpath");
        if (progressMonitor != null) {
            progressMonitor.setNote("Scanning class list to find subclasses");
        }
        ArrayList<ClassNameWithDescriptionAndDevelopmentStatus> found = scanClasspath(superClass, (p) -> {
            if (progressMonitor != null) {
                progressMonitor.setProgress(p);
            }
        }, null);
        for (ClassNameWithDescriptionAndDevelopmentStatus c : found) {
            classes.add(c.getClassName());
        }
        return classes;
    }

    public static void main(String[] args) {
        final String superclass = "net.sf.jaer.eventprocessing.EventFilter2D";
        System.out.println("Subclasses of " + superclass + " are:");
        ArrayList<String> classNames = findSubclassesOf(superclass);
        for (String s : classNames) {
            System.out.println(s);
        }
        System.exit(0);
    }
}
