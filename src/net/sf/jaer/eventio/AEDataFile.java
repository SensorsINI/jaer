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


    /**
     * Legacy AEDAT-2 extension ({@code .aedat}), still accepted on open.
     * Prefer {@link #DATA_FILE_EXTENSION_AEDAT2} when writing AEDAT-2 so files are
     * distinguishable from AEDAT-4 on disk.
     */
    public static final String DATA_FILE_EXTENSION = ".aedat";
    /** Preferred extension when writing AEDAT-2.0 files. */
    public static final String DATA_FILE_EXTENSION_AEDAT2 = ".aedat2";
    /**
     * Extension when writing AEDAT-4.0 files. AEDAT-4 also starts with the magic
     * header line {@code #!AER-DAT4.0\r\n} (iniVation AEDAT 4.0 spec).
     */
    public static final String DATA_FILE_EXTENSION_AEDAT4 = ".aedat4";
    /**
     * Pre-2010 jAER / DVS128 extension. Also used by Prophesee Metavision DAT
     * (disambiguated by a {@code % } ASCII header; see
     * {@code MetavisionDatFileInputStream}).
     */
    public static final String OLD_DATA_FILE_EXTENSION=".dat";
    /** file extension for index files that contain information about a set of related data files, ".adidx", including '.'. */
    public static final String INDEX_FILE_EXTENSION = ".aeidx"; // changed from .dat Apr 2010
    public static final String OLD_INDEX_FILE_EXTENSION = ".index"; // changed from .dat Apr 2010
    /** Used to mark end of header block after 15.11.2016. Next line starts binary data. */
    public static final String END_OF_HEADER_STRING="End Of ASCII Header";
    /** line starting with this string is written just before data block starts */
    public static final String DATA_START_TIME_SYSTEMCURRENT_TIME_MILLIS = " DataStartTime: System.currentTimeMillis() ";

    /** The leading comment character for data files, "#" */
    public static final char COMMENT_CHAR = '#';
    /** The format header, in unix/shell style the first line of the data file reads, e.g. "#!AER-DAT2.0" where
    the "!AER-DAT" is defined here 
     */
    public static final String DATA_FILE_FORMAT_HEADER = "!AER-DAT";
    /** Legacy AEDAT-2 file version number string */
    public static final String DATA_FILE_VERSION_NUMBER_AEDAT2 = "2.0";
    /** AEDAT-4 file version number string */
    public static final String DATA_FILE_VERSION_NUMBER_AEDAT4 = "4.0";
    /** AEDZ file extension including '.': ".aedz" */
    public static final String DATA_FILE_EXTENSION_AEDZ = ".aedz";
    /**
     * Format-selector sentinel for the AEDZ compressed recording format. Not an
     * AEDAT version number: it is only used to route
     * {@link AEViewer#startRecording(String,String)} and the preferences combo to
     * an {@code AEDZOutputStream}. Never parsed numerically.
     */
    public static final String DATA_FILE_VERSION_NUMBER_AEDZ = "aedz";
    /** The default format version number string */
    public static final String DATA_FILE_VERSION_NUMBER = DATA_FILE_VERSION_NUMBER_AEDAT4;

    /**
     * Preferred filename extension for a new recording of the given AEDAT version
     * string (e.g. {@code "2.0"} → {@code .aedat2}, {@code "4.0"} → {@code .aedat4},
     * {@code "aedz"} → {@code .aedz}).
     */
    public static String extensionForVersion(String dataFileVersionNum) {
        if (DATA_FILE_VERSION_NUMBER_AEDAT4.equals(dataFileVersionNum)) {
            return DATA_FILE_EXTENSION_AEDAT4;
        }
        if (DATA_FILE_VERSION_NUMBER_AEDZ.equals(dataFileVersionNum)) {
            return DATA_FILE_EXTENSION_AEDZ;
        }
        return DATA_FILE_EXTENSION_AEDAT2;
    }

    /** True if {@code name} ends with a known AEDAT data-file extension (case-insensitive). */
    public static boolean hasDataFileExtension(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(DATA_FILE_EXTENSION_AEDAT4)
                || lower.endsWith(DATA_FILE_EXTENSION_AEDAT2)
                || lower.endsWith(DATA_FILE_EXTENSION_AEDZ)
                || lower.endsWith(DATA_FILE_EXTENSION)
                || lower.endsWith(OLD_DATA_FILE_EXTENSION);
    }

    /**
     * Known data-file extension of {@code name} including the leading dot
     * ({@code .aedat4}), or empty if none.
     */
    public static String dataFileExtensionOf(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase();
        String[] known = {
            DATA_FILE_EXTENSION_AEDAT4,
            DATA_FILE_EXTENSION_AEDAT2,
            DATA_FILE_EXTENSION_AEDZ,
            DATA_FILE_EXTENSION,
            OLD_DATA_FILE_EXTENSION
        };
        for (String ext : known) {
            if (lower.endsWith(ext)) {
                return ext;
            }
        }
        return "";
    }

    /**
     * Save-dialog title that names the format, e.g. {@code Save .aedat4 recorded data}.
     *
     * @param extension with or without a leading dot; empty falls back to
     *                  {@code Save recorded data}
     */
    public static String saveRecordedDataTitle(String extension) {
        return saveRecordedDataTitle(extension, null);
    }

    /**
     * @param extra optional parenthetical, e.g. {@code restored default filename}
     */
    public static String saveRecordedDataTitle(String extension, String extra) {
        String ext = extension == null ? "" : extension.trim();
        if (!ext.isEmpty() && !ext.startsWith(".")) {
            ext = "." + ext;
        }
        String title = ext.isEmpty() ? "Save recorded data" : "Save " + ext + " recorded data";
        if (extra != null && !extra.isEmpty()) {
            return title + " (" + extra + ")";
        }
        return title;
    }

    /** The date/time/timezone format for filenames */
    static final String YYYY_M_MDD_TH_HMMSS_Z = "yyyy-MM-dd'T'HH-mm-ssZ";
    /** Format used for log file names */
    public static DateFormat DATE_FORMAT = new SimpleDateFormat(YYYY_M_MDD_TH_HMMSS_Z); //e.g. Tmpdiff128-   2007-04-04T11-32-21-0700    -0 ants molting swarming.dat
    /** end of line (EOL) ending (the "windows type") used in data files */
    public static final byte[] EOL = new byte[]{'\r','\n'};
}
