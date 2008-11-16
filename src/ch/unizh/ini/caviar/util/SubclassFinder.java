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

package ch.unizh.ini.caviar.util;

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
        static HashMap<String,Class> map=new HashMap<String,Class>();
        synchronized private static Class forName(String name)throws ClassNotFoundException{
            Class c=null;
            if((c=map.get(name))==null){
                c=Class.forName(name);
                map.put(name,c);
                return c;
            }else{
                return c;
            }
        }
        static private synchronized void clear(){ 
            map.clear();
        }
    }
    
    /** Finds and returns list of all subclases of a class
     * @param superClassName the fully qualified name, e.g. ch.unizh.ini.caviar.chip.AEChip
     * @return list of fully qualified class names that are subclasses (and not the same as) the argument
     */
    public static ArrayList<String> findSubclassesOf(String superClassName) {
        ArrayList<String> classes=new ArrayList<String>(100);
        try{
            Class superClass = FastClassFinder.forName(superClassName);
            List<String> allClasses=ListClasses.listClasses();  // expensive, must search all classpath and make big string array list
            int n=".class".length();
            Class c=null;
            for (String s:allClasses) {
                try {
                    s=s.substring(0,s.length()-n);
                    s=s.replace('/','.');
                    if(s.indexOf("$")!=-1) continue; // inner class
                    c=FastClassFinder.forName(s);
                    if(c==superClass) continue; // don't add the superclass
                    if(superClass.isAssignableFrom(c)){
                        if(!Modifier.isAbstract(c.getModifiers()))//if class is abstract, dont add to list.
                            classes.add(s);
                    }
                } catch (Throwable t) { // must catch Error, not just Exception here, because UnsatisfiedLinkError is Error
                    log.warning("ERROR: " + t+" while scanning class="+c);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            return classes;
        }
    }
    
    public static void main(String[] args){
        ArrayList<String> classNames=   findSubclassesOf("ch.unizh.ini.caviar.eventprocessing.EventFilter2D");
        for(String s:classNames){
            System.out.println(s);
        }
        System.exit(0);
    }
    
    
}
