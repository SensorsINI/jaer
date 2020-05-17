/**
 * From http://forums.java.net/jive/thread.jspa?messageID=212405&tstart=0
 */
package net.sf.jaer.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;

/**
 * Provides a static method that returns a List<String> of all classes on
 * java.class.path starting with root of package name, e.g. "org/netbeans/.."
 * and ending with ".class". Classes that are on IGNORED_CLASSPATH are not
 * scanned.
 * <p>
 * From http://forums.java.net/jive/thread.jspa?messageID=212405&tstart=0
 */
public class ListClasses {

    /**
     * ListClasses doesn't iterate over classpath classes starting with these
     * strings; this is hack to avoid iterating over standard java classes to
     * avoid running into missing DLLs with JOGL.
     */
    public static final String[] IGNORED_CLASSPATH = {
        "java", "javax", "META-INF",
        "com/sun", "com/oracle", "org/w3c", "org.omg", "org/xml", "sun", "oracle",
        "gnu/io", // added because we don't bother with the native code for I2C, parallel ports, etc
        "build/classes", // added to ignore classes not in jaer.jar and only temporarily built
        //Below list is to speed up the search as well as the subclassFinder
        // by excluding items from the list that will never contain jAER code.
        // This also helps eliminating 'UnsatisfiedLinkError' on some systems.
        "com/googlecode", "org/jfree", "org/jblas", "flanagan", "org/apache",
        "org/jdesktop", "de/thesycon", "com/kitfox", "org/uncommons",
        "de/ailis", "org/netbeans",
        "com/kitfox", "org/bytedeco", "org/usb4java", "org/openni",
        "jogamp", "org/jogamp", "com/jogamp", "gluegen", "newt","ncsa.hdf.hdf5lib", "ncsa.hdf",
        "lib", "org/tensorflow", "org/ros", "com/fasterxml", "ch/qos", "groovy/lang", "com/google", "groovy", "ch/systemsx"
    };

    static final Logger log = Logger.getLogger("net.sf.jaer.util");
    private static boolean debug = false;
    private static final int INIT_SIZE = 4000;

    private static void usage() {
        System.err.println(
                "usage: java ListClasses [-debug]"
                + "\n\tThe commandline version of ListClasses will search the system"
                + "\n\tclasspath defined in your environment for all .class files available");
        System.exit(-1);
    }

    /**
     * Iterate over the system classpath defined by "java.class.path" searching
     * for all .class files available
     *
     * @return list of all fully qualified class names
     */
    public static List<String> listClasses() {
        List<String> classNames = new ArrayList<String>(INIT_SIZE);
        try {
            // get the system classpath
            String classpath = System.getProperty("java.class.path", "");
            if (debug) {
                log.log(Level.INFO, "java.class.path={0}", classpath);
            }

            if (classpath.equals("")) {
                log.log(Level.SEVERE, "classpath is not set!");
            }

            StringTokenizer st = new StringTokenizer(classpath, File.pathSeparator);

            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                if (debug) {
                    log.log(Level.INFO, "classpath token = {0}", token);
                }
                File classpathElement = new File(token);
                classNames.addAll(classpathElement.isDirectory()
                        //?loadClassesFromDir(classpathElement.list(new CLASSFilter()))
                        ? loadClassesFromDir(null, classpathElement, classpathElement)
                        : loadClassesFromJar(classpathElement));
            }
            HashSet<String> hash = new HashSet(classNames); // store only unique
            classNames = new ArrayList(hash);
            log.info("found " + classNames.size() + " classes in " + classpath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classNames;
    }

    private static List<String> loadClassesFromJar(File jarFile) {
        List<String> files = new ArrayList<String>(INIT_SIZE);
        try {
            if (jarFile.getName().endsWith(".jar")) {
                if (debug) {
                    log.log(Level.INFO, "{0} is being scanned", jarFile);
                }
                Enumeration<JarEntry> fileNames=null;
                try {
                    fileNames = new JarFile(jarFile).entries();
                } catch (ZipException e) {
                    log.warning("jar file "+jarFile+" is corrupt: "+e);
                    return files;
                }
                JarEntry entry = null;
                while (fileNames.hasMoreElements()) {
                    entry = fileNames.nextElement();
                    boolean skipThis = false;
                    skipThis = isIgnored(entry.getName(), skipThis);
                    if (!skipThis && entry.getName().endsWith(".class")) {
                        files.add(entry.getName());
                    }
                    if (skipThis) {
                        if (debug) {
                            log.log(Level.INFO, "ignoring rest of classes in jar file {0}", jarFile.getName());
                        }
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (files.size() > 0) {
            log.info("found " + files.size() + " class files in " + jarFile.getName());
        }
        return files;
    }

    private static boolean isIgnored(String name, boolean skipThis) {
        for (String s : IGNORED_CLASSPATH) {
            if (name.startsWith(s)) {
                if (debug) {
                    log.log(Level.INFO, "skipping {0} because it starts with {1}", new Object[]{name, s});
                }
                skipThis = true;
                break;
            }
        }
        return skipThis;
    }

    private static List<String> loadClassesFromDir(String fileNames[]) {
        return Arrays.asList(fileNames);
    }

    private static List<String> loadClassesFromDir(List<String> classes, File rootDir, File baseDir) {
        if (classes == null) {
            classes = new LinkedList<String>();
        }

        File[] classFiles = baseDir.listFiles(new CLASSFilter());
        for (File classFile : classFiles) {
            if (classFile != null) {
                classes.add(classFile.getAbsolutePath().replace(rootDir.getAbsolutePath() + File.separator, ""));
                //  classes.add(clazz.getAbsolutePath());
            }
        }

        File[] directories = baseDir.listFiles(new DirFilter());

        for (File dir : directories) {
            classes = loadClassesFromDir(classes, rootDir, dir);
        }

        return classes;
    }

    static class CLASSFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            return (name.endsWith(".class"));
        }
    }

    static class DirFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory();
        }
    }

    /**
     * Main method used in command-line mode for searching the system classpath
     * for all the .class file available in the classpath
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length == 1) {
            if (args[0].equals("-debug")) {
                debug = true;
                System.out.println(listClasses().size());
            } else {
                System.err.println(
                        "error: unrecognized \"" + args[0] + "\" option ");
                System.exit(-1);
            }
        } else if ((args.length == 1) && args[0].equals("-help")) {
            usage();
        } else if (args.length == 0) {
            List<String> classNames = listClasses();
            for (String s : classNames) {
                System.out.println(s);
            }
        } else {
            usage();
        }
        System.exit(0);
    }
}
