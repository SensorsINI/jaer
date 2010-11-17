/*
 * SubclassFinder.java
 *
 * Created on May 13, 2007, 8:13 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 13, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.util;

import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import java.util.*;
import java.lang.reflect.Modifier;
import java.util.logging.Logger;

/**
 * Finds subclasses of a given class name in classes on the loaded classpath.
 * <p>
 * See http://www.javaworld.com/javaworld/javatips/jw-javatip113.html?page=2
 * @author tobi
 */
public class SubclassFinder {
    
    static Logger log=Logger.getLogger("SubclassFinder");
    
    /** Creates a new instance of SubclassFinder */
    private SubclassFinder() {
    }
    
    private static class FastClassFinder {
        static HashMap<String, Class> map = new HashMap<String, Class>();

        synchronized private static Class forName(String name) throws ClassNotFoundException, ExceptionInInitializerError {
            Class c = null;
            if ((c = map.get(name)) == null) {
                try {
                    c = Class.forName(name);
                    map.put(name, c);
                } catch (Exception e) {
                    log.warning("caught " + e + " when trying to get class named " + name);
                }
                return c;
            } else {
                return c;
            }
        }

        static private synchronized void clear(){
            map.clear();
        }
    }
    
    /** Finds and returns list of fully-qualified name Strings of all subclases of a class.
     * @param superClassName the fully qualified name, e.g. net.sf.jaer.chip.AEChip
     * @return list of fully qualified class names that are subclasses (and not the same as) the argument
     */
    public static ArrayList<String> findSubclassesOf(String superClassName) {
         ArrayList<String> classes=new ArrayList<String>(100);
       if(superClassName==null){
            log.warning("tried to find subclasses of null class name, returning empty list");
            return classes;
        }
        try{
            Class superClass = FastClassFinder.forName(superClassName);
            List<String> allClasses=ListClasses.listClasses();  // expensive, must search all classpath and make big string array list
            int n=".class".length();
            Class c=null;
            if(allClasses.size()==0){
                log.warning("List of subclasses of "+superClassName+" is empty, is there something wrong with your classpath. Do you have \"compile on save\" turned on? (This option can break the SubclassFinder).");
            }
            for (String s:allClasses) {
                try {
                    s=s.substring(0,s.length()-n);
                    s=s.replace('/','.').replace('\\','.'); // TODO check this replacement of file separators on win/unix
                    if(s.indexOf("$")!=-1) continue; // inner class
                    c=FastClassFinder.forName(s);
                    if (c == superClass || c == null) {
                        continue; // don't add the superclass
                    }
                    if (Modifier.isAbstract(c.getModifiers())) {
                        continue;//if class is abstract, dont add to list.
                    }
                    if (superClass.isAssignableFrom(c)) { //sees if e.g. superclass AEChip can be cast from e.g. c=DVS128, i.e. can we do (AEChip)DVS128?
                           classes.add(s);
                    }
                } catch (Throwable t) { // must catch Error, not just Exception here, because UnsatisfiedLinkError is Error
                    log.warning("ERROR: " + t+" while seeing if "+superClass+" isAssignableFrom "+c);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            return classes;
        }
    }
    
    public static void main(String[] args){
        final String superclass="net.sf.jaer.eventprocessing.EventFilter2D";
        System.out.println("Subclasses of "+superclass+" are:");
        ArrayList<String> classNames=   findSubclassesOf(superclass);
        for(String s:classNames){
            System.out.println(s);
        }
        System.exit(0);
    }
    
    
}
