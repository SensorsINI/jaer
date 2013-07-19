/*
 * XMLFileFilter.java
 *
 * Created on September 26, 2005, 3:37 PM
 */

package net.sf.jaer.util;

import java.io.File;

/**
 * filter for PNF image files.
 * @author tobi
 */
public class PNGFileFilter extends javax.swing.filechooser.FileFilter {
    
    /** Creates a new instance of PNGFileFilter */
    public PNGFileFilter() {
    }
    
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
        
        String extension = getExtension(f);
        if (extension != null) {
            if (extension.equals("png")){
                return true;
            } else {
                return false;
            }
        }
        return true;
    }
    
    public static String getExtension(File f) {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');
        
        if (i > 0 &&  i < s.length() - 1) {
            ext = s.substring(i+1).toLowerCase();
        }
        return ext;
    }

    public String getDescription() {
        return "PNG image file";
    }
    
}
