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
package net.sf.jaer.eventio;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Defines file extensions for AERDAT data and index files.
 * @author tobi
 */
public interface AEDataFile {

    /** Types of jAER files. */
    public enum Type {DataFile, IndexFile};  // TODO not used yet, should include permissible extensions, data file headers, FileFilters, etc


    /** file extension for data files, including ".", e.g. ".aedat" */
    public static final String DATA_FILE_EXTENSION = ".aedat";  // changed from .dat Apr 2010
    public static final String OLD_DATA_FILE_EXTENSION=".dat";
    /** file extension for index files that contain information about a set of related data files, ".adidx", including '.'. */
    public static final String INDEX_FILE_EXTENSION = ".aeidx"; // changed from .dat Apr 2010
    public static final String OLD_INDEX_FILE_EXTENSION = ".index"; // changed from .dat Apr 2010
    /** Used to mark end of header block after 15.11.2016. Next line starts binary data. */
    public static final String END_OF_HEADER_STRING="End Of ASCII Header";

    /** The leading comment character for data files, "#" */
    public static final char COMMENT_CHAR = '#';
    /** The format header, in unix/shell style the first line of the data file reads, e.g. "#!AER-DAT2.0" where
    the "!AER-DAT" is defined here 
     */
    public static final String DATA_FILE_FORMAT_HEADER = "!AER-DAT";
    /** The most recent format version number string */
    public static final String DATA_FILE_VERSION_NUMBER = "2.0";
    /** Format used for log file names */
    public static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ"); //e.g. Tmpdiff128-   2007-04-04T11-32-21-0700    -0 ants molting swarming.dat
    /** end of line (EOL) ending (the "windows type") used in data files */
    public static final byte[] EOL = new byte[]{'\r','\n'};
}
