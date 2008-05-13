/** From http://forums.java.net/jive/thread.jspa?messageID=212405&tstart=0 */
package ch.unizh.ini.caviar.util;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.*;

/**
 Provides a static method that returns a List<String> of all classes on java.class.path starting with root of package name, e.g. "org/netbeans/.." and ending with
 ".class"
 <p>
 From http://forums.java.net/jive/thread.jspa?messageID=212405&tstart=0
 */
public class ListClasses {
    
    /** ListClasses doesn't iterate over classpath classes starting with these strings; this is hack to avoid
     iterating over standard java classes to avoid running into missing DLLs with JOGL.
     */
    public static final String[] IGNORED_CLASSPATH={
        "java",
        "com/sun"
    };
    
    static Logger log=Logger.getLogger("ch.unizh.ini.caviar.util");
    private static boolean debug = false;
    
    private static final int INIT_SIZE=500, SIZE_INC=500;
    
    /**
     * Main method used in command-line mode for searching the system
     * classpath for all the .class file available in the classpath
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length ==1) {
            if (args[0].equals("-debug")) {
                debug = true;
                System.out.println(listClasses().size());
            } else {
                System.err.println(
                        "error: unrecognized \"" + args[0] + "\" option ");
                System.exit(-1);
            }
        } else if (args.length == 1 && args[0].equals("-help")) {
            usage();
        } else if (args.length == 0) {
            List<String> classNames=listClasses();
            for(String s:classNames){
                System.out.println(s);
            }
        } else {
            usage();
        }
        System.exit(0);
    }
    private static void usage() {
        System.err.println(
                "usage: java ListClasses [-debug]"
                + "\n\tThe commandline version of ListClasses will search the system"
                + "\n\tclasspath defined in your environment for all .class files available" );
        System.exit(-1);
    }
    
    /**
     * Iterate over the system classpath defined by "java.class.path" searching
     * for all .class files available
     @return list of all fully qualified class names
     *
     */
    public static List<String> listClasses() {
        List<String> classes = new ArrayList<String>(INIT_SIZE);
        try {
            // get the system classpath
            String classpath = System.getProperty("java.class.path", "");
            if(debug) log.info("java.class.path="+classpath);
            
            if (classpath.equals("")) {
                log.severe("error: classpath is not set");
            }
            
            if (debug) {
                log.info("system classpath = " + classpath);
            }
            
            StringTokenizer st =
                    new StringTokenizer(classpath, File.pathSeparator);
            
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                if(debug) log.info("classpath token="+token);
                File classpathElement = new File(token);
                classes .addAll(classpathElement.isDirectory()
                ? loadClassesFromDir(classpathElement.list(new CLASSFilter()))
                : loadClassesFromJar(classpathElement));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classes;
    }
    
    private static List<String> loadClassesFromJar(File jarFile) {
        List<String> files = new ArrayList<String>(INIT_SIZE);
        try {
            if(jarFile.getName().endsWith(".jar")){
                if(debug) log.info(jarFile+" is being scanned");
                Enumeration<JarEntry> fileNames;
                fileNames = new JarFile(jarFile).entries();
                JarEntry entry = null;
                while(fileNames.hasMoreElements()){
                    entry = fileNames.nextElement();
                    boolean skipThis=false;
                    for(String s:IGNORED_CLASSPATH){
                        if(entry.getName().startsWith(s)) {
                            if(debug) log.info("skipping "+entry.getName()+" because it starts with "+s);
                            skipThis=true;
                            break;
                        }
                    }
                    if(!skipThis && entry.getName().endsWith(".class")){
                        files.add(entry.getName());
                    }
                    if(skipThis){
                        if(debug) log.info("ignoring rest of classes in jar file "+jarFile.getName());
                        break;
                    }
                }
            }
        } catch (IOException e) {
            
            e.printStackTrace();
        }
        
        return files;
    }
    
    private static List<String> loadClassesFromDir(String fileNames[]) {
        return Arrays.asList(fileNames);
    }
    
    static class CLASSFilter implements FilenameFilter {

        public boolean accept(File dir, String name) {
            return (name.endsWith(".class"));
        }
    }


}
