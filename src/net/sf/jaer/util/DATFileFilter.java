/*
 * DATFileFilter.java
 *
 * Created on September 26, 2005, 3:37 PM
 */

package net.sf.jaer.util;

import java.io.File;

import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.TextFileInputStream;
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
            if (extension.equals(EXTENSION)  || extension.equals(EXTENSION2) || extension.equals(OLDEXTENSION) 
                    || extension.endsWith(RosbagFileInputStream.DATA_FILE_EXTENSION)
                    || extension.equals(TextFileInputStream.EXTENTION1) || extension.equals(TextFileInputStream.EXTENTION2)){
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
        return "AEDAT-2.0 raw binary data file (.aedat), ROS bag (.bag) file, or text file (.csv or .txt)";
    }
    
    /** The extension, including the dot, ".aedat"
     **/
    public static final String EXTENSION;
    static{
        EXTENSION=AEDataFile.DATA_FILE_EXTENSION.substring(AEDataFile.DATA_FILE_EXTENSION.lastIndexOf(".")+1,AEDataFile.DATA_FILE_EXTENSION.length());
    }
    /** AEDAT-2.0 extension ".aedat2" */
    public static final String EXTENSION2;
    static{
        EXTENSION2=AEDataFile.DATA_FILE_EXTENSION_AEDAT2.substring(AEDataFile.DATA_FILE_EXTENSION_AEDAT2.lastIndexOf(".")+1,AEDataFile.DATA_FILE_EXTENSION_AEDAT2.length());
    }

    /** The original extension for AE data files */
    public static final String OLDEXTENSION="dat";

    
}
