/* SubclassFinder.java
 * Created on May 13, 2007, 8:13 PM
 * Copyright May 13, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich */
package net.sf.jaer.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.lang.reflect.Modifier;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.DefaultListModel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

/**
 * Finds subclasses of a given class name in classes on the loaded classpath.
 * Classes are cached in a HashMap to reduce cost of subsequent lookups.
 * <p>
 * See http://www.javaworld.com/javaworld/javatips/jw-javatip113.html?page=2
 *
 * @author tobi
 */
public class SubclassFinder {

    private static Preferences prefs = Preferences.userNodeForPackage(SubclassFinder.class); // used to store keys/values of cache filenames
    private final static Logger log = Logger.getLogger("SubclassFinder");
// TODO needs a way of caching in preferences the list of classes and the number or checksum of classes,
    // to reduce startup time, since this lookup of subclasses takes 10s of seconds on some machines

    private static HashMap<String, String> className2subclassListFileNameMap = null; // map from super class to filename of file that holds the subclasses
    private static final String SUBCLASS_FINDERFILENAME_HASH_MAP_PREFS_KEY = "SubclassFinder.filenameHashMap";

    static {
        try {
            byte[] bytes = prefs.getByteArray(SUBCLASS_FINDERFILENAME_HASH_MAP_PREFS_KEY, null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                className2subclassListFileNameMap = (HashMap<String, String>) in.readObject();
                in.close();
                log.info("loaded SubclassFinder cache existing filename hashmap from preferences using key " + SUBCLASS_FINDERFILENAME_HASH_MAP_PREFS_KEY);
                if (className2subclassListFileNameMap.isEmpty()) {
                    log.info("map is empty");
                } else {
                    StringBuilder sb = new StringBuilder("map constains following entries\n");
                    for (String s : className2subclassListFileNameMap.keySet()) {
                        sb.append(String.format("  key: %s -> entry: %s\n", s, className2subclassListFileNameMap.get(s)));
                    }
                    log.info(sb.toString());
                }
            } else {
                log.info("no existing SubclassFinder cache filename hashmap to load, must scan classpath to find subclasses");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * List of regexp package names to exclude from search
     */
    public static final ArrayList<String> exclusionList = new ArrayList();
//    static{
//            exclusionList.add("com.jogamp.*");
//            exclusionList.add("jogamp.*");
//            exclusionList.add("java.*");
//            exclusionList.add("javax.*");
//            exclusionList.add("org.openni.*");
//            exclusionList.add("com.sun.*");
//            exclusionList.add("lib.*");
//            exclusionList.add("org.jblas.*");
//            exclusionList.add("com.phidgets.*");
//            exclusionList.add("org.usb4java.*");
//            exclusionList.add("com.kitfox.*");
//            exclusionList.add("org.uncommons.*");
//            exclusionList.add("org.bytedeco.*");
//
//    } // already handled in ListClasses

    /**
     * Creates a new instance of SubclassFinder
     */
    private SubclassFinder() {
        super();
    }

    private static class FastClassFinder {

        static HashMap<String, Class> map = new HashMap<String, Class>();

        private static synchronized Class forName(String name) throws ExceptionInInitializerError, NoClassDefFoundError, UnsatisfiedLinkError {
            Class c;
            if ((c = map.get(name)) == null) {
                try {
                    c = Class.forName(name);
                    map.put(name, c);
                } catch (ClassNotFoundException e) {
                    log.warning("caught " + e + " when trying to get class named " + name);
                }
            }
            return c;
        }

        private static synchronized void clear() {
            map.clear();
        }
    }

    /**
     * Finds subclasses in SwingWorker
     */
    public static class SubclassFinderWorker extends SwingWorker<ArrayList<ClassNameWithDescriptionAndDevelopmentStatus>, ClassNameWithDescriptionAndDevelopmentStatus> {

        Class clazz;
        private DefaultListModel<ClassNameWithDescriptionAndDevelopmentStatus> tableModel = null;
        private boolean useCacheIfAvailable = true; // set this flag true to use cache (if it exists), set false to rescan classpath for subclasse (needed when new classes added) 

        /**
         * Constructs the worker thread for finding subclasses of clazz. This
         * constructor also populates the exclusion list of packages that are
         * excluded from consideration.
         *
         * @param clazz the class to find subclasses of
         */
        public SubclassFinderWorker(Class clazz) {
            this.clazz = clazz;
            // exclude known libraries
            StringBuilder sb = new StringBuilder("The following packages are excluded from searching for subclasses of " + clazz.getName() + ": \n");
            for (String s : exclusionList) {
                sb.append(s).append("\n");
            }
            sb.append("To modify this list, modify the class SubclassFinder.");
            log.info(sb.toString());
        }

        /**
         * Constructs the worker thread for finding subclasses of clazz.
         *
         * @param clazz the class to find subclasses of
         * @param model the list model to be populated with the subclasses
         * @param useCacheIfAvailable et this flag true to use cache (if it
         * exists), set false to rescan classpath for subclasses (needed when
         * new classes added)
         */
        public SubclassFinderWorker(Class clazz, DefaultListModel<ClassNameWithDescriptionAndDevelopmentStatus> model, boolean useCacheIfAvailable) {
            this(clazz);
            this.tableModel = model;
            this.useCacheIfAvailable = useCacheIfAvailable;
        }

        /**
         * Called by SwingWorker on execute()
         *
         * @return the list of classes that are subclasses.
         * @throws Exception on any error
         */
        @Override
        protected ArrayList<ClassNameWithDescriptionAndDevelopmentStatus> doInBackground() throws Exception {
            long startTime = System.currentTimeMillis();
            setProgress(0);
            String superClassName = clazz.getName();
            ArrayList<ClassNameWithDescriptionAndDevelopmentStatus> classes = new ArrayList<>(300);
            if (superClassName == null) {
                log.warning("tried to find subclasses of null class name, returning empty list");
                return classes;
            }
            // see if cache should be used
            if (useCacheIfAvailable && className2subclassListFileNameMap != null) {
                String cachefilename = className2subclassListFileNameMap.get(superClassName);
                if (cachefilename == null) {
                    log.info("no cache file found for " + superClassName);
                }
                File f = null;
                if (cachefilename != null) {
                    f = new File(cachefilename);
                }
                if (f != null && f.exists() && f.isFile()) {
                    log.info("For super class " + superClassName + ", found cache file " + f.getAbsolutePath() + "; reading subclasses from this file");
                    LineNumberReader is = new LineNumberReader(new FileReader(f));
                    // count lines for progress
                    while (is.skip(Long.MAX_VALUE) > 0) {
                    }; // skip to end of file
                    int nLines = is.getLineNumber();
                    is.close();
                    is = new LineNumberReader(new FileReader(f)); // reset to start

                    // read lines, each with class name, then for each determine description and development status
                    String line = is.readLine();
                    int linesReadCount = 0;
                    while (line != null) {
                        Class c = Class.forName(line);
                        final ClassNameWithDescriptionAndDevelopmentStatus myFoundClass = new ClassNameWithDescriptionAndDevelopmentStatus(c);
//sees if e.g. superclass AEChip can be cast from e.g. c=DVS128, i.e. can we do (AEChip)DVS128?
                        classes.add(myFoundClass);
                        int prog = (int) (100 * ((float) (linesReadCount++) / nLines));
                        if (prog > 100) {
                            prog = 100;
                        }
                        setProgress(prog);
                        publish(myFoundClass);
                        line = is.readLine();
                    }
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Read " + classes.size() + " subclasses from cache file " + cachefilename + " in " + duration / 1000 + "s");
                    return classes;
                } else {
                    log.info("Cache filename " + cachefilename + " does not lead to a readable file; will rescan entire classpath");
                }
            }
            log.info("No cache found for " + superClassName + "; now scanning entire classpath to build list of subclasses of " + superClassName);
            Class superClass = FastClassFinder.forName(superClassName);
            List<String> allClasses = ListClasses.listClasses();  // expensive, must search all classpath and make big string array list
            int n = ".class".length();
            Class c = null;
            if (allClasses.isEmpty()) {
                log.warning("List of subclasses of " + superClassName + " is empty, is there something wrong with your classpath. Do you have \"compile on save\" turned on? (This option can break the SubclassFinder).");
            }
            int i = 0;
            int nclasses = allClasses.size();
            int lastProgress = 0;
            allclassloop:
            for (String s : allClasses) {
                i++;
                try {
                    int p = (int) ((float) i / nclasses * 100);
                    if (p > lastProgress + 5) {
                        setProgress(p);
                        lastProgress = p;
                    }

                    s = s.substring(0, s.length() - n);
                    s = s.replace('/', '.').replace('\\', '.'); // TODO check this replacement of file separators on win/unix
                    if (s.indexOf("$") != -1) {
                        continue allclassloop; // inner class
                    }
                    for (String excl : exclusionList) {
                        if (s.matches(excl)) {
                            continue allclassloop;
                        }
                    }
                    c = FastClassFinder.forName(s);
                    if (c == superClass || c == null) {
                        continue; // don't add the superclass
                    }
                    if (Modifier.isAbstract(c.getModifiers())) {
                        continue;//if class is abstract, dont add to list.
                    }
                    if (superClass.isAssignableFrom(c)) {
                        final ClassNameWithDescriptionAndDevelopmentStatus myFoundClass = new ClassNameWithDescriptionAndDevelopmentStatus(c);
//sees if e.g. superclass AEChip can be cast from e.g. c=DVS128, i.e. can we do (AEChip)DVS128?
                        classes.add(myFoundClass);
                        publish(myFoundClass);
                    }
                    // TODO: Better way of handling Errors is needed. Most of them arent a problem, as they dont belong to jAER anyway. If that is the case we should ignore, not log...    
                } catch (ExceptionInInitializerError t) {
                    log.warning(t + " while seeing if " + superClass + " isAssignableFrom " + c);
                } catch (NoClassDefFoundError t) {
                    log.warning(t + " while seeing if " + superClass + " isAssignableFrom " + c);
                } catch (UnsatisfiedLinkError t) {
                    log.warning(t + " while seeing if " + superClass + " isAssignableFrom " + c);
                }
            }
            if (!allClasses.isEmpty()) {
                // store cache
                String tmpDir = System.getProperty("java.io.tmpdir");
                String cacheFileName = superClassName + "-subclass-cache.txt";
                File cacheFile = new File(tmpDir + File.separator + cacheFileName);
                if (cacheFile.isFile() && cacheFile.exists()) {
                    log.info("overwriting existing cache file " + cacheFile);
                }
                PrintStream ps = new PrintStream(cacheFile);
                for (ClassNameWithDescriptionAndDevelopmentStatus clz : classes) {
                    ps.println(clz.getClassName());
                }
                ps.close();
                log.info("wrote " + classes.size() + " classes to cache file " + cacheFile.getAbsolutePath());
                if (className2subclassListFileNameMap == null) {
                    className2subclassListFileNameMap = new HashMap<String, String>();
                }
                className2subclassListFileNameMap.put(superClassName, cacheFile.getAbsolutePath());
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput oos = new ObjectOutputStream(bos);
                oos.writeObject(className2subclassListFileNameMap);
                oos.close();
                // Get the bytes of the serialized object
                byte[] buf = bos.toByteArray();
                prefs.putByteArray(SUBCLASS_FINDERFILENAME_HASH_MAP_PREFS_KEY, buf);
                log.info("stored cache file name with preferences key " + SUBCLASS_FINDERFILENAME_HASH_MAP_PREFS_KEY);
            } else {
                log.warning("skipped storing empty cache file");
            }
            long duration = System.currentTimeMillis() - startTime;
            log.info("Scanned " + classes.size() + " subclasses in " + duration / 1000 + "s");

            return classes;
        }

        @Override
        protected void done() {
            setProgress(100);
        }

        @Override
        protected void process(List<ClassNameWithDescriptionAndDevelopmentStatus> list) { // publish comes here
            if (list == null || tableModel == null) {
                return;
            }
            for (ClassNameWithDescriptionAndDevelopmentStatus c : list) {
                tableModel.addElement(c);
            }
        }

    }

    /**
     * Updates a ProgressMonitor while finding subclasses
     *
     * @param name class to find subclasses of
     * @return list of subclasses that are not abstract
     */
    public static ArrayList<String> findSubclassesOf(String name) {
        return findSubclassesOf(name, null);
    }

    /**
     * Finds and returns list of fully-qualified name Strings of all subclases
     * of a class.
     *
     * @param superClassName the fully qualified name, e.g.
     * net.sf.jaer.chip.AEChip
     * @param progressMonitor updated during search
     * @return list of fully qualified class names that are subclasses (and not
     * the same as) the argument
     * @see #findSubclassesOf(java.lang.String)
     */
    public static ArrayList<String> findSubclassesOf(String superClassName, final ProgressMonitor progressMonitor) { // TODO this doesn't work, monitor bar does not update and just shows blank, even if this method is executed in a SwingWorker thread
        ArrayList<String> classes = new ArrayList<String>(1000);
        if (superClassName == null) {
            log.warning("tried to find subclasses of null class name, returning empty list");
            return classes;
        }
        if (progressMonitor != null) {
            progressMonitor.setNote("Building class list");
        }
        Class superClass = FastClassFinder.forName(superClassName);
        List<String> allClasses = ListClasses.listClasses();  // expensive, must search all classpath and make big string array list
        int n = ".class".length();
        Class c = null;
        if (allClasses.isEmpty()) {
            log.warning("List of subclasses of " + superClassName + " is empty, is there something wrong with your classpath. Do you have \"compile on save\" turned on? (This option can break the SubclassFinder).");
        }
        int i = 0;
        if (progressMonitor != null) {
            progressMonitor.setNote("Scanning class list to find subclasses");
        }
        if (progressMonitor != null) {
            progressMonitor.setMaximum(allClasses.size());
        }
        allclassloop:
        for (String s : allClasses) {
            try {
                if (progressMonitor != null) {
                    if (progressMonitor.isCanceled()) {
                        break;
                    }
                    progressMonitor.setProgress(i);
                    i++;
//                        Thread.sleep(1); // for debug of progressmonitor
                }
                s = s.substring(0, s.length() - n);
                s = s.replace('/', '.').replace('\\', '.'); // TODO check this replacement of file separators on win/unix
                if (s.indexOf("$") != -1) {
                    continue; // inner class
                }
                for (String excl : exclusionList) {
                    if (s.matches(excl)) {
                        continue allclassloop;
                    }
                }
                c = FastClassFinder.forName(s); //THIS LINE THROWS ALL THE EXCEPTIONS
                if (c == superClass || c == null) {
                    continue; // don't add the superclass
                }
                if (Modifier.isAbstract(c.getModifiers())) {
                    continue;//if class is abstract, dont add to list.
                }
                if (superClass.isAssignableFrom(c)) { //sees if e.g. superclass AEChip can be cast from e.g. c=DVS128, i.e. can we do (AEChip)DVS128?
                    classes.add(s);
                }
                // TODO: Better way of handling Errors is needed. Most of them arent a problem, as they dont belong to jAER anyway. If that is the case we should ignore, not log...    
            } catch (ExceptionInInitializerError t) {
                log.warning(t + " while seeing if " + superClass + " isAssignableFrom " + c);
            } catch (NoClassDefFoundError t) {
                log.warning(t + " while seeing if " + superClass + " isAssignableFrom " + c);
            } catch (UnsatisfiedLinkError t) {
                log.warning(t + " while seeing if " + superClass + " isAssignableFrom " + c);
            }
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
