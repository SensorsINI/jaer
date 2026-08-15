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
import net.sf.jaer.eventio.dsec.DsecHdf5AEInputStream;
import prophesee.eventio.MetavisionRawFileInputStream;

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
            if (extension.equals(EXTENSION)  || extension.equals(EXTENSION2) || extension.equals(EXTENSION4) || extension.equals(OLDEXTENSION) 
                    || extension.endsWith(RosbagFileInputStream.DATA_FILE_EXTENSION)
                    || extension.equals(TextFileInputStream.FILE_EXTENSION_CSV) || extension.equals(TextFileInputStream.FILE_EXTENSION_TXT)
                    || extension.equals(MetavisionRawFileInputStream.DATA_FILE_EXTENSION)
                    || extension.equals(DsecHdf5AEInputStream.DATA_FILE_EXTENSION_H5)
                    || extension.equals(DsecHdf5AEInputStream.DATA_FILE_EXTENSION_HDF5)){
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
        return "AEDAT (.aedat4, .aedat2, .aedat), .dat (legacy jAER or Metavision DAT), Metavision RAW (.raw), DSEC HDF5 (.h5), ROS bag (.bag), or text (.csv / .txt)";
    }
    
    /** Legacy extension without dot: "aedat" */
    public static final String EXTENSION;
    static{
        EXTENSION=AEDataFile.DATA_FILE_EXTENSION.substring(AEDataFile.DATA_FILE_EXTENSION.lastIndexOf(".")+1,AEDataFile.DATA_FILE_EXTENSION.length());
    }
    /** AEDAT-2 preferred extension without dot: "aedat2" */
    public static final String EXTENSION2;
    static{
        EXTENSION2=AEDataFile.DATA_FILE_EXTENSION_AEDAT2.substring(AEDataFile.DATA_FILE_EXTENSION_AEDAT2.lastIndexOf(".")+1,AEDataFile.DATA_FILE_EXTENSION_AEDAT2.length());
    }
    /** AEDAT-4 extension without dot: "aedat4" */
    public static final String EXTENSION4;
    static{
        EXTENSION4=AEDataFile.DATA_FILE_EXTENSION_AEDAT4.substring(AEDataFile.DATA_FILE_EXTENSION_AEDAT4.lastIndexOf(".")+1);
    }

    /** The original extension for AE data files */
    public static final String OLDEXTENSION="dat";

    
}
