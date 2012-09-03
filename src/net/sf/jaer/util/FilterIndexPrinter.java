/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import net.sf.jaer.Description;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * This class will scan for filters and print out the wiki page that indexes them.
 * 
 * When the file-selection box pops up, make a text-file to write to.  You can 
 * then copy this file into the wiki page at:
 * https://sourceforge.net/apps/trac/jaer/wiki/FilterIndex
 * to update the list of filters.
 * 
 * @author Peter
 */
public class FilterIndexPrinter {
    
    public static void main(String[] args) throws IOException
    {
        
//        List<String> classList=ListClasses.listClasses();
        List<String> classList=SubclassFinder.findSubclassesOf("net.sf.jaer.eventprocessing.EventFilter2D");
        
        class Filter implements Comparable<Filter>
        {   final String fullName;
            final String shortName;
            Filter(String longName)
            {
                fullName=longName;
                int point=longName.lastIndexOf(".");            
                shortName=longName.substring(point+1,longName.length());
                                
            }
            
            @Override
            public int compareTo(Filter o) {
                return shortName.compareTo(o.shortName);
            }
        }
        
        List<Filter> filterList=new ArrayList();
        for(String c:classList)
            filterList.add(new Filter(c));
        
        Collections.sort(filterList);
        
        
        
        /** Get file to save into */
        JFileChooser fileChooser = new JFileChooser(".");
//    FileFilter filter1 = new FileExtensionFilter("JPG and JPEG", new String[] { "JPG", "JPEG" });
//    fileChooser.setFileFilter(filter1);
        int status = fileChooser.showOpenDialog(null);
        
        File selectedFile=null;
        if (status == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
//            System.out.println(selectedFile.getParent());
//            System.out.println(selectedFile.getName());
        } else if (status == JFileChooser.CANCEL_OPTION) {
//        System.out.println(JFileChooser.CANCEL_OPTION);
        }
        
//        File file=fileDialog.
        
//        String file=fileDialog.getFile();
        
        if (selectedFile==null)
            return;
        
        FileOutputStream fout=null;
        try {
            fout=new FileOutputStream(selectedFile);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(FilterIndexPrinter.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        
        
        // Print the file.
        System.out.print("Printing file....");
        PrintStream ps = new PrintStream(fout);
        ps.println("= List of Event-Processing Filters =");
        ps.println();
        ps.println("Click on a filter for more info.");
        ps.println();
        
//        ps.println("||'''Filter Name'''||'''Description'''||'''Package'''||'''doc/src'''||"); // Some weird problem happened with the page getting cut off when we added this
        ps.println("||'''Filter Name'''||'''Description'''||'''Package'''||");
        
        for (Filter f:filterList)
        {   
            Description des=null;
            
            Method method = null;
            try {
                Class c = Class.forName(f.fullName);
                
//                method = c.getDeclaredMethod ("getDescription");
                
                if (c.isAnnotationPresent(Description.class)) 
                    des = (Description) c.getAnnotation(Description.class);
                
//            } catch (NoSuchMethodException ex) {
                //Logger.getLogger(FilterIndexPrinter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SecurityException ex) {
                Logger.getLogger(FilterIndexPrinter.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(FilterIndexPrinter.class.getName()).log(Level.SEVERE, null, ex);
            } 
            
            String description;
            if (des==null)
                description=" ";
            else
                description=des.value();    
            
//            ps.println("||[wiki:\"filt/"+f.fullName+"\" "+f.shortName+"]||"+description+"||"+f.fullName.substring(0,f.fullName.length()-f.shortName.length()-1)+"||[http://jaer.svn.sourceforge.net/viewvc/jaer/trunk/host/java/src/"+f.fullName.replaceAll("\\.", "/")+".java?view=markup" +" .]||"); 
            
//            ps.println("||[wiki:\"filt/"+f.fullName+"\" "+f.shortName+"]||"+description+"||"+f.fullName.substring(0,f.fullName.length()-f.shortName.length()-1)+"||[http://jaer.sourceforge.net/javadoc/"+f.fullName.replaceAll("\\.", "/")+".html" +" D] [http://jaer.svn.sourceforge.net/viewvc/jaer/trunk/host/java/src/"+f.fullName.replaceAll("\\.", "/")+".java?view=markup" +" S] ||"); 
            ps.println("||[wiki:\"filt/"+f.fullName+"\" "+f.shortName+"]||"+description+"||"+f.fullName.substring(0,f.fullName.length()-f.shortName.length()-1)+"||"); 
            
            ps.println();
        }
        
        ps.println();
        String footer="Run the class net.sf.jaer.util.!FilterIndexPrinter to regenerate this list.";
        fout.write(footer.getBytes());
        ps.close();
        fout.close();
        System.out.print("Done");
    }
    
    
}
