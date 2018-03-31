/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileOutputStream;
import net.sf.jaer.util.DATFileFilter;

/**
 * Logs data to disk according to various criteria.
 * @author tobi
 */
@Description("Logs data to disk according to various criteria.")
public class DataLogger extends EventFilter2D {

    private boolean loggingEnabled = false; // controlled by filterEnabled
    private AEFileOutputStream loggingOutputStream;
    private String defaultLoggingFolderName = System.getProperty("user.dir");
    // lastLoggingFolder starts off at user.dir which is startup folder "host/java" where .exe launcher lives
    private String loggingFolder = getPrefs().get("DataLogger.loggingFolder", defaultLoggingFolderName);
    private File loggingFile;
    private int maxLogFileSizeMB = prefs().getInt("DataLogger.maxLogFileSizeMB", 100);
    private boolean rotateFilesEnabled = prefs().getBoolean("DataLogger.rotateFilesEnabled", false);
    private int rotatePeriod = prefs().getInt("DataLogger.rotatePeriod", 7);
    private long bytesWritten = 0;
    private String logFileBaseName = prefs().get("DataLogger.logFileBaseName", "");
    private int rotationNumber = 0;
    private boolean filenameTimestampEnabled = prefs().getBoolean("DataLogger.filenameTimestampEnabled", true);

    public DataLogger(AEChip chip) {
        super(chip);
        final String cont = "Control", params = "Parameters";
        setPropertyTooltip(cont, "loggingEnabled", "Enable to start logging data");
        setPropertyTooltip(params, "filenameTimestampEnabled", "adds a timestamp to the filename, but means rotation will not overwrite old data files and will eventually fill disk");
        setPropertyTooltip(params, "logFileBaseName", "the base name of the log file - if empty the AEChip class name is used");
        setPropertyTooltip(params, "rotatePeriod", "log file rotation period");
        setPropertyTooltip(params, "rotateFilesEnabled", "enabling rotates log files over rotatePeriod");
        setPropertyTooltip(params, "maxLogFileSizeMB", "logging is stopped when files get larger than this in MB");
        setPropertyTooltip(params, "loggingFolder", "directory to store logged data files");
        // check lastLoggingFolder to see if it really exists, if not, default to user.dir
        File lf = new File(loggingFolder);
        if (!lf.exists() || !lf.isDirectory()) {
            log.warning("loggingFolder " + lf + " doesn't exist or isn't a directory, defaulting to " + lf);
            setLoggingFolder(defaultLoggingFolderName);
        }
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        logData(in);
        return in;
    }

    synchronized private void logData(EventPacket eventPacket) {
        if (eventPacket == null) {
            return;
        }
        // if we are logging data to disk do it here
        if (loggingEnabled) {
            try {
                loggingOutputStream.writePacket(eventPacket); // log all events
                bytesWritten += eventPacket.getSize();
                if (bytesWritten >>> 20 > maxLogFileSizeMB) {
                    setLoggingEnabled(false);
                    if (rotateFilesEnabled) {
                        startLogging();
                    }
                }
            } catch (IOException e) {
                log.warning("while logging data to " + loggingFile + " caught " + e + ", will try to close file");
                loggingEnabled = false;
                getSupport().firePropertyChange("loggingEnabled", null, false);
                try {
                    loggingOutputStream.close();
                    log.info("closed logging file " + loggingFile);
                } catch (IOException e2) {
                    log.warning("while closing logging file " + loggingFile + " caught " + e2);
                }
            }
        }
    }

    @Override
    synchronized public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public void doSelectLoggingFolder() {
        if (loggingFolder == null || loggingFolder.isEmpty()) {
            loggingFolder = System.getProperty("user.dir");
        }
        JFileChooser chooser = new JFileChooser(loggingFolder);
        chooser.setDialogTitle("Choose data logging folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        int retval = chooser.showOpenDialog(getChip().getAeViewer().getFilterFrame());
        if (retval == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (f != null && f.isDirectory()) {
                setLoggingFolder(f.toString());
                log.info("selected data logging folder " + loggingFolder);
            } else {
                log.warning("tried to select invalid logging folder named " + f);
            }
        }
    }

    /** Starts logging AE data to a file.
     *
     * @param filename the filename to log to, including all path information. Filenames without path
     * are logged to the startup folder. The default extension of AEDataFile.DATA_FILE_EXTENSION is appended if there is no extension.
     *
     * @return the file that is logged to.
     */
    synchronized public File startLogging(String filename) {
        if (filename == null) {
            log.warning("tried to log to null filename, aborting");
            return null;
        }
        if (!filename.toLowerCase().endsWith(AEDataFile.DATA_FILE_EXTENSION)) {
            filename = filename + AEDataFile.DATA_FILE_EXTENSION;
            log.info("Appended extension to make filename=" + filename);
        }
        try {
            loggingFile = new File(filename);

            loggingOutputStream = new AEFileOutputStream(new BufferedOutputStream(new FileOutputStream(loggingFile), 100000));
            loggingEnabled = true;
            getSupport().firePropertyChange("loggingEnabled", null, true);
            log.info("starting logging to " + loggingFile);

        } catch (IOException e) {
            loggingFile = null;
            log.warning("exception on starting to log data to file "+filename+": "+e);
            loggingEnabled=false;
            getSupport().firePropertyChange("loggingEnabled", null, false);
        }

        return loggingFile;
    }

    /** Starts logging data to a default data logging file.
     * @return the file that is logged to.
     */
    synchronized public File startLogging() {

        if (loggingEnabled) {
            return loggingFile;
        }

        String dateString = filenameTimestampEnabled ? AEDataFile.DATE_FORMAT.format(new Date()) : "";
        String base =
                chip.getClass().getSimpleName();
        int suffixNumber = rotateFilesEnabled ? rotationNumber++ : 0;
        if (rotationNumber >= rotatePeriod) {
            rotationNumber = 0;
        }
        boolean succeeded = false;
        String filename;

        if (logFileBaseName != null && !logFileBaseName.isEmpty()) {
            base = logFileBaseName;
        }
        String suffix;
        if (rotateFilesEnabled) {
            suffix = String.format("%02d", suffixNumber);
        } else {
            suffix = "";
        }
        do {
            filename = loggingFolder + File.separator + base + "-" + dateString + "-" + suffix + AEDataFile.DATA_FILE_EXTENSION;
            File lf = new File(filename);
            if (rotateFilesEnabled) {
                succeeded = true; // if rotation, always use next file
            } else if (!lf.isFile()) {
                succeeded = true;
            }

        } while (succeeded == false && suffixNumber++ <= 99);
        if (succeeded == false) {
            log.warning("could not open a unigue new file for logging after trying up to " + filename + " aborting startLogging");
            return null;
        }

        File lf = startLogging(filename);
        bytesWritten = 0;
        return lf;

    }

    /** Stops logging and optionally opens file dialog for where to save file.
     * If number of AEViewers is more than one, dialog is also skipped since we may be logging from multiple viewers.
     * @param confirmFilename true to show file dialog to confirm filename, false to skip dialog.
     * @return chosen File
     */
    synchronized public File stopLogging(boolean confirmFilename) {
        if (!loggingEnabled) {
            return null;
        }
        // the file has already been logged somewhere with a timestamped name, what this method does is
        // to move the already logged file to a possibly different location with a new name, or if cancel is hit,
        // to delete it.
        int retValue = JFileChooser.CANCEL_OPTION;

        try {
            log.info("stopped logging at " + AEDataFile.DATE_FORMAT.format(new Date()));
            loggingEnabled = false;
            loggingOutputStream.close();
// if jaer viewer is logging synchronized data files, then just save the file where it was logged originally

            if (confirmFilename) {
                JFileChooser chooser = new JFileChooser();
                chooser.setCurrentDirectory(new File(loggingFolder));
                chooser.setFileFilter(new DATFileFilter());
                chooser.setDialogTitle("Save logged data");

                String fn =
                        loggingFile.getName();
//                System.out.println("fn="+fn);
                // strip off .aedat to make it easier to appendCopy comment to filename
                String base =
                        fn.substring(0, fn.lastIndexOf(AEDataFile.DATA_FILE_EXTENSION));
                chooser.setSelectedFile(new File(base));
                chooser.setDialogType(JFileChooser.SAVE_DIALOG);
                chooser.setMultiSelectionEnabled(false);
                boolean savedIt = false;
                do {
                    // clear the text input buffer to prevent multiply typed characters from destroying proposed datetimestamped filename
                    retValue = chooser.showSaveDialog(chip.getAeViewer());
                    if (retValue == JFileChooser.APPROVE_OPTION) {
                        File newFile = chooser.getSelectedFile();
                        // make sure filename ends with .aedat
                        if (!newFile.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION)) {
                            newFile = new File(newFile.getCanonicalPath() + AEDataFile.DATA_FILE_EXTENSION);
                        }
// we'll rename the logged data file to the selection

                        boolean renamed = loggingFile.renameTo(newFile);
                        if (renamed) {
                            // if successful, cool, save persistence
                            savedIt = true;
                            setLoggingFolder(chooser.getCurrentDirectory().getPath());
                            loggingFile = newFile; // so that we play it back if it was saved and playback immediately is selected
                            log.info("renamed logging file to " + newFile);
                        } else {
                            // confirm overwrite
                            int overwrite = JOptionPane.showConfirmDialog(chooser, "Overwrite file \"" + newFile + "\"?", "Overwrite file?", JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
                            if (overwrite == JOptionPane.OK_OPTION) {
                                // we need to delete the file
                                boolean deletedOld = newFile.delete();
                                if (deletedOld) {
                                    savedIt = loggingFile.renameTo(newFile);
                                    savedIt = true;
                                    log.info("renamed logging file to " + newFile); // TODO something messed up here with confirmed overwrite of logging file
                                    loggingFile = newFile;
                                } else {
                                    log.warning("couldn't delete logging file " + newFile);
                                }

                            } else {
                                chooser.setDialogTitle("Couldn't save file there, try again");
                            }

                        }
                    } else {
                        // user hit cancel, delete logged data
                        boolean deleted = loggingFile.delete();
                        if (deleted) {
                            log.info("Deleted temporary logging file " + loggingFile);
                        } else {
                            log.warning("Couldn't delete temporary logging file " + loggingFile);
                        }

                        savedIt = true;
                    }

                } while (savedIt == false); // keep trying until user is happy (unless they deleted some crucial data!)
                }

        } catch (IOException e) {
            e.printStackTrace();
        }

        loggingEnabled = false;
        getSupport().firePropertyChange("loggingEnabled", null, false);
        return loggingFile;
    }

    /**
     * @return the lastFolderName
     */
    public String getLoggingFolder() {
        return loggingFolder;
    }

    /**
     * @param loggingFolder the lastFolderName to set
     */
    public void setLoggingFolder(String loggingFolder) {
        String old = loggingFolder;
        this.loggingFolder = loggingFolder;
        getPrefs().put("DataLogger.loggingFolder", loggingFolder);
        getSupport().firePropertyChange("loggingFolder", old, loggingFolder);
    }

    /**
     * @return the loggingEnabled
     */
    private boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    /**
     * @param loggingEnabled the loggingEnabled to set
     */
    synchronized private void setLoggingEnabled(boolean loggingEnabled) {
        boolean old = this.loggingEnabled;
        boolean success = false;
        if (loggingEnabled) {
            File f = startLogging();
            if (f == null) {
                log.warning("startLogging returned null");
                loggingEnabled=false;
            } else {
                success = true;
            }
        } else {
            File f = stopLogging(false);
            if (f == null) {
                log.warning("stopLogging returned null");
            } else {
                success = true;
            }
        }
            this.loggingEnabled = loggingEnabled;
            getSupport().firePropertyChange("loggingEnabled", old, loggingEnabled);
    }

    /**
     * @return the maxLogFileSizeMB
     */
    public int getMaxLogFileSizeMB() {
        return maxLogFileSizeMB;
    }

    /**
     * @param maxLogFileSizeMB the maxLogFileSizeMB to set
     */
    public void setMaxLogFileSizeMB(int maxLogFileSizeMB) {
        this.maxLogFileSizeMB = maxLogFileSizeMB;
        prefs().putInt("DataLogger.maxLogFileSizeMB", maxLogFileSizeMB);
    }

    /**
     * @return the rotateFilesEnabled
     */
    public boolean isRotateFilesEnabled() {
        return rotateFilesEnabled;
    }

    /**
     * @param rotateFilesEnabled the rotateFilesEnabled to set
     */
    public void setRotateFilesEnabled(boolean rotateFilesEnabled) {
        this.rotateFilesEnabled = rotateFilesEnabled;
        prefs().putBoolean("DataLogger.rotateFilesEnabled", rotateFilesEnabled);
    }

    /**
     * @return the rotatePeriod
     */
    public int getRotatePeriod() {
        return rotatePeriod;
    }

    /**
     * @param rotatePeriod the rotatePeriod to set
     */
    public void setRotatePeriod(int rotatePeriod) {
        this.rotatePeriod = rotatePeriod;
        prefs().putInt("DataLogger.rotatePeriod", rotatePeriod);
    }

    /**
     * @return the logFileBaseName
     */
    public String getLogFileBaseName() {
        return logFileBaseName;
    }

    /**
     * @param logFileBaseName the logFileBaseName to set
     */
    public void setLogFileBaseName(String logFileBaseName) {
        this.logFileBaseName = logFileBaseName;
        prefs().put("DataLogger.logFileBaseName", logFileBaseName);
    }

    /**
     * @return the filenameTimestampEnabled
     */
    public boolean isFilenameTimestampEnabled() {
        return filenameTimestampEnabled;
    }

    /**
     * @param filenameTimestampEnabled the filenameTimestampEnabled to set
     */
    public void setFilenameTimestampEnabled(boolean filenameTimestampEnabled) {
        this.filenameTimestampEnabled = filenameTimestampEnabled;
        prefs().putBoolean("DataLogger.filenameTimestampEnabled", filenameTimestampEnabled);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        setLoggingEnabled(yes);
    }


}    
