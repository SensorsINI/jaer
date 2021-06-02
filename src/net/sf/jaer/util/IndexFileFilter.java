/*
 * XMLFileFilter.java
 *
 * Created on September 26, 2005, 3:37 PM
 */

package net.sf.jaer.util;

import java.io.File;

import net.sf.jaer.eventio.AEDataFile;

/**
 * filter for INDEX ae event data file set - this file just contains full paths to a set of related AEInputStream data files.
 * @author tobi
 */
public class IndexFileFilter extends javax.swing.filechooser.FileFilter {

    /** Creates a new instance of IndexFileFilter */
    public IndexFileFilter() {
    }

    @Override
	public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }

        String extension = "."+getExtension(f);
        if (extension.equals(EXTENSION) || extension.equals(OLDEXTENSION)){
            return true;
        }

        return false;
    }

    /** Returns extension, without leading ".".
     *
     * @param f some File
     * @return the extension, in lower case.
     */
    public static String getExtension(File f) {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        if ((i > 0) &&  (i < (s.length() - 1))) {
            ext = s.substring(i+1).toLowerCase();
        }
        return ext;
    }

    @Override
	public String getDescription() {
        return "aeidx (or index) file (set of AE data files)";
    }

       /** The extension, including the dot, ".aeidx"
     **/
    public static final String EXTENSION=AEDataFile.INDEX_FILE_EXTENSION;
//    static{
//        EXTENSION=AEDataFile.INDEX_FILE_EXTENSION.substring(AEDataFile.INDEX_FILE_EXTENSION.lastIndexOf(".")+1,AEDataFile.INDEX_FILE_EXTENSION.length());
//    }

    /** The orignal extension for AE index files */
    public static final String OLDEXTENSION=".index";

}
