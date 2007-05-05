/*
 * AEDataFile.java
 *
 * Created on March 13, 2006, 12:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 13, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.eventio;

/**
 * Defines file extensions for data and index files.
 * @author tobi
 */
public interface AEDataFile {
    
    /** file extension for data files, including ".", e.g. ".dat" */
    public static final String DATA_FILE_EXTENSION=".dat";
    
    /** file extension for index files that contain information about a set of related data files, ".index" */
    public static final String INDEX_FILE_EXTENSION=".index";
    
    /** The leading comment character for data files, "#" */
    public static final char COMMENT_CHAR='#';
    
}
