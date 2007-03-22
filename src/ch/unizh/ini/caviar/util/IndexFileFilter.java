/*
 * XMLFileFilter.java
 *
 * Created on September 26, 2005, 3:37 PM
 */

package ch.unizh.ini.caviar.util;

import java.io.*;

/**
 * filter for INDEX ae event data file set - this file just contains full paths to a set of related AEInputStream data files.
 * @author tobi
 */
public class IndexFileFilter extends javax.swing.filechooser.FileFilter {
    
    /** Creates a new instance of IndexFileFilter */
    public IndexFileFilter() {
    }
    
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
        
        String extension = getExtension(f);
        if (extension != null) {
            if (extension.equals("index")){
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
        return "INDEX file (set of AE data files)";
    }
    
}
