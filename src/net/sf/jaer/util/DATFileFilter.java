/*
 * DATFileFilter.java
 *
 * Created on September 26, 2005, 3:37 PM
 */

package net.sf.jaer.util;

import java.io.File;

import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.ros.RosbagFileInputStream;

/**
 * filter for AE event data files.
 * <p>
 * As of April 2010 the default extension was changed to .aedat for data files and .adidx for index files, and  DATFileFilter was modifed to allow for old-style data files.
 *
 * @author tobi
 */
public class DATFileFilter extends javax.swing.filechooser.FileFilter {
    
    /** Creates a new instance of DATFileFilter */
    public DATFileFilter() {
    }
    
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
        
        String extension = getExtension(f);
        if (extension != null) {
            if (extension.equals(EXTENSION)  || extension.equals(OLDEXTENSION) || extension.endsWith(RosbagFileInputStream.DATA_FILE_EXTENSION)){
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
        return "AER raw binary data file or ROS bag file";
    }
    
    /** The extension, including the dot, ".aedat"
     **/
    public static final String EXTENSION;
    static{
        EXTENSION=AEDataFile.DATA_FILE_EXTENSION.substring(AEDataFile.DATA_FILE_EXTENSION.lastIndexOf(".")+1,AEDataFile.DATA_FILE_EXTENSION.length());
    }

    /** The orignal extension for AE data files */
    public static final String OLDEXTENSION="dat";

    
}
