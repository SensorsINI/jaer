/*
 * DATFileFilter.java
 *
 * Created on September 26, 2005, 3:37 PM
 */

package net.sf.jaer.util;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.TextFileInputStream;
import net.sf.jaer.eventio.dsec.DsecHdf5AEInputStream;
import net.sf.jaer.eventio.ros.RosbagFileInputStream;
import prophesee.eventio.MetavisionRawFileInputStream;

/**
 * Filter for AE event data files in {@link JFileChooser} open dialogs.
 * <p>
 * As of April 2010 the default extension was changed to .aedat for data files and .adidx for index files, and  DATFileFilter was modifed to allow for old-style data files.
 * Directories are accepted in every {@link Category} so subfolders stay visible for navigation.
 *
 * @author tobi
 */
public class DATFileFilter extends javax.swing.filechooser.FileFilter {

    /** Which set of extensions this filter instance shows. */
    public enum Category {
        /** Every extension jAER can play (default open-dialog choice). */
        ALL_RECOGNIZED,
        /** {@code .aedat} only. */
        AEDAT,
        /** {@code .aedat2} only. */
        AEDAT2,
        /** {@code .aedat4} only. */
        AEDAT4,
        /** Recognized playback types other than {@code .aedat}/{@code .aedat2}/{@code .aedat4}. */
        OTHER,
        /** No extension restriction (files and folders). */
        ALL_FILES
    }

    private final Category category;

    /** Creates a filter for {@link Category#ALL_RECOGNIZED}. */
    public DATFileFilter() {
        this(Category.ALL_RECOGNIZED);
    }

    /** Creates a filter for the given {@code category}. */
    public DATFileFilter(Category category) {
        this.category = category == null ? Category.ALL_RECOGNIZED : category;
    }

    public Category getCategory() {
        return category;
    }

    /**
     * Installs the File → Open recorded data file filter list: all recognized
     * (selected by default), {@code .aedat}, {@code .aedat2}, {@code .aedat4},
     * other recognized types, then all files and folders. Replaces the built-in
     * "All Files" entry so that last choice is our all-files-and-folders filter.
     * Every filter shows directories.
     *
     * @param chooser the chooser to configure
     * @param previousFilter last selected filter from a prior dialog, or {@code null}
     */
    public static void installOpenDialogFilters(JFileChooser chooser, FileFilter previousFilter) {
        chooser.resetChoosableFileFilters();
        chooser.setAcceptAllFileFilterUsed(false);
        DATFileFilter allRecognized = new DATFileFilter(Category.ALL_RECOGNIZED);
        DATFileFilter[] filters = {
            allRecognized,
            new DATFileFilter(Category.AEDAT),
            new DATFileFilter(Category.AEDAT2),
            new DATFileFilter(Category.AEDAT4),
            new DATFileFilter(Category.OTHER),
            new DATFileFilter(Category.ALL_FILES)
        };
        for (DATFileFilter filter : filters) {
            chooser.addChoosableFileFilter(filter);
        }
        FileFilter selected = allRecognized;
        if (previousFilter != null) {
            for (DATFileFilter filter : filters) {
                if (sameFilter(filter, previousFilter)) {
                    selected = filter;
                    break;
                }
            }
        }
        chooser.setFileFilter(selected);
    }

    private static boolean sameFilter(DATFileFilter candidate, FileFilter previous) {
        if (previous instanceof DATFileFilter) {
            return candidate.category == ((DATFileFilter) previous).category;
        }
        return candidate.getDescription().equals(previous.getDescription());
    }

    @Override
    public boolean accept(File f) {
        if (f == null) {
            return false;
        }
        if (f.isDirectory()) {
            return true;
        }
        if (category == Category.ALL_FILES) {
            return true;
        }
        String extension = getExtension(f);
        if (extension == null) {
            return false;
        }
        switch (category) {
            case AEDAT:
                return isAedatExtension(extension);
            case AEDAT2:
                return isAedat2Extension(extension);
            case AEDAT4:
                return isAedat4Extension(extension);
            case OTHER:
                return isOtherRecognizedExtension(extension);
            case ALL_RECOGNIZED:
            default:
                return isRecognizedExtension(extension);
        }
    }

    /** True if {@code extension} (no leading dot, lower case) is any type jAER can play. */
    public static boolean isRecognizedExtension(String extension) {
        return isAedatExtension(extension)
                || isAedat2Extension(extension)
                || isAedat4Extension(extension)
                || isOtherRecognizedExtension(extension);
    }

    private static boolean isAedatExtension(String extension) {
        return EXTENSION.equals(extension);
    }

    private static boolean isAedat2Extension(String extension) {
        return EXTENSION2.equals(extension);
    }

    private static boolean isAedat4Extension(String extension) {
        return EXTENSION4.equals(extension);
    }

    /** Recognized playback extensions excluding {@code aedat}/{@code aedat2}/{@code aedat4}. */
    public static boolean isOtherRecognizedExtension(String extension) {
        if (extension == null) {
            return false;
        }
        return extension.equals(EXTENSIONZ)
                || extension.equals(OLDEXTENSION)
                || extension.equals(RosbagFileInputStream.DATA_FILE_EXTENSION)
                || extension.equals(TextFileInputStream.FILE_EXTENSION_CSV)
                || extension.equals(TextFileInputStream.FILE_EXTENSION_TXT)
                || extension.equals(MetavisionRawFileInputStream.DATA_FILE_EXTENSION)
                || extension.equals(DsecHdf5AEInputStream.DATA_FILE_EXTENSION_H5)
                || extension.equals(DsecHdf5AEInputStream.DATA_FILE_EXTENSION_HDF5)
                || extension.equals(INDEX_EXTENSION)
                || extension.equals(OLD_INDEX_EXTENSION);
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

    @Override
    public String getDescription() {
        switch (category) {
            case AEDAT:
                return "AEDAT (*.aedat)";
            case AEDAT2:
                return "AEDAT-2 (*.aedat2)";
            case AEDAT4:
                return "AEDAT-4 (*.aedat4)";
            case OTHER:
                return "Other event data (*.aedz, *.dat, *.raw, *.h5, *.hdf5, *.bag, *.csv, *.txt, *.aeidx)";
            case ALL_FILES:
                return "All files and folders";
            case ALL_RECOGNIZED:
            default:
                return "All recognized event data (*.aedat4, *.aedat2, *.aedat, *.aedz, *.dat, *.raw, *.h5, *.bag, *.csv, *.txt, *.aeidx)";
        }
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
    /** AEDZ extension without dot: "aedz" */
    public static final String EXTENSIONZ;
    static{
        EXTENSIONZ=AEDataFile.DATA_FILE_EXTENSION_AEDZ.substring(AEDataFile.DATA_FILE_EXTENSION_AEDZ.lastIndexOf(".")+1);
    }

    /** The original extension for AE data files */
    public static final String OLDEXTENSION="dat";

    /** Index playlist extension without dot: "aeidx" */
    public static final String INDEX_EXTENSION;
    static {
        INDEX_EXTENSION = AEDataFile.INDEX_FILE_EXTENSION.substring(AEDataFile.INDEX_FILE_EXTENSION.lastIndexOf(".") + 1);
    }

    /** Legacy index playlist extension without dot: "index" */
    public static final String OLD_INDEX_EXTENSION;
    static {
        OLD_INDEX_EXTENSION = AEDataFile.OLD_INDEX_FILE_EXTENSION.substring(AEDataFile.OLD_INDEX_FILE_EXTENSION.lastIndexOf(".") + 1);
    }

    
}
