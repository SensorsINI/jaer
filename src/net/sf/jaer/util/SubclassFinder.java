/* SubclassFinder.java
 * Created on May 13, 2007, 8:13 PM
 * Copyright May 13, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich */
package net.sf.jaer.util;

import java.util.*;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

/** Finds subclasses of a given class name in classes on the loaded classpath.
 * Classes are cached in a HashMap to reduce cost of subsequent lookups.
 * <p>
 * See http://www.javaworld.com/javaworld/javatips/jw-javatip113.html?page=2
 * @author tobi */
public class SubclassFinder {
    // TODO needs a way of caching in preferences the list of classes and the number or checksum of classes,
    // to reduce startup time, since this lookup of subclasses takes 10s of seconds on some machines

    private final static Logger log = Logger.getLogger("SubclassFinder");

    /** Creates a new instance of SubclassFinder */
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
                    log.warning("caught "+e+" when trying to get class named "+name);
                }
            }
            return c;
        }

        private static synchronized void clear() {
            map.clear();
        }
    }
    
    /** Finds subclasses in SwingWorker */
    public static class SubclassFinderWorker extends SwingWorker<ArrayList<String>, Object> {

        Class clazz;

        public SubclassFinderWorker(Class clazz) {
            this.clazz = clazz;
        }

        /** Called by SwingWorker on execute()
         * @return the list of classes that are subclasses.
         * @throws Exception on any error */
        @Override
        protected ArrayList<String> doInBackground() throws Exception {
            setProgress(0);
            String superClassName = clazz.getName();
            ArrayList<String> classes = new ArrayList<String>(100);
            if (superClassName == null) {
                log.warning("tried to find subclasses of null class name, returning empty list");
                return classes;
            }
            publish("Building class list");
            Class superClass = FastClassFinder.forName(superClassName);
            List<String> allClasses = ListClasses.listClasses();  // expensive, must search all classpath and make big string array list
            int n = ".class".length();
            Class c = null;
            if (allClasses.isEmpty()) {
                log.warning("List of subclasses of "+superClassName+" is empty, is there something wrong with your classpath. Do you have \"compile on save\" turned on? (This option can break the SubclassFinder).");
            }
            int i = 0;
            int nclasses = allClasses.size();
            publish("Scanning class list to find subclasses");
            int lastProgress = 0;
            for (String s : allClasses) {
                i++;
                try {
                    int p = (int) ((float) i / nclasses * 100);
                    if (p > lastProgress+5) {
                        setProgress(p);
                        lastProgress = p;
                    }

                    s = s.substring(0, s.length() - n);
                    s = s.replace('/', '.').replace('\\', '.'); // TODO check this replacement of file separators on win/unix
                    if (s.indexOf("$") != -1) {
                        continue; // inner class
                    }
                    c = FastClassFinder.forName(s);
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
                    log.warning(t+" while seeing if "+superClass+" isAssignableFrom "+c);
                } catch (NoClassDefFoundError t) {
                    log.warning(t+" while seeing if "+superClass+" isAssignableFrom "+c);
                } catch (UnsatisfiedLinkError t) {
                    log.warning(t+" while seeing if "+superClass+" isAssignableFrom "+c);
                }
            }
            return classes;
        }

        @Override
        protected void done() {
            setProgress(100);
        }

        @Override
        protected void process(List<Object> chunks) {
//            System.out.println("chunks="+chunks);
        }
    }
    
    /** Updates a ProgressMonitor while finding subclasses
     * @param name class to find subclasses of
     * @return list of subclasses that are not abstract */
    public static ArrayList<String> findSubclassesOf(String name) {
        return findSubclassesOf(name, null);
    }

    /** Finds and returns list of fully-qualified name Strings of all subclases of a class.
     * @param superClassName the fully qualified name, e.g. net.sf.jaer.chip.AEChip
     * @param progressMonitor updated during search
     * @return list of fully qualified class names that are subclasses (and not the same as) the argument
     * @see #findSubclassesOf(java.lang.String) */
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
            log.warning("List of subclasses of "+superClassName+" is empty, is there something wrong with your classpath. Do you have \"compile on save\" turned on? (This option can break the SubclassFinder).");
        }
        int i = 0;
        if (progressMonitor != null) {
            progressMonitor.setNote("Scanning class list to find subclasses");
        }
        if (progressMonitor != null) {
            progressMonitor.setMaximum(allClasses.size());
        }
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
                log.warning(t+" while seeing if "+superClass+" isAssignableFrom "+c);
            } catch (NoClassDefFoundError t) {
                log.warning(t+" while seeing if "+superClass+" isAssignableFrom "+c);
            } catch (UnsatisfiedLinkError t) {
                log.warning(t+" while seeing if "+superClass+" isAssignableFrom "+c);
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
