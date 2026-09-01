/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileOutputStream;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;
import net.sf.jaer.graphics.RecordingSaveDialogGuard;
import net.sf.jaer.util.DATFileFilter;
import net.sf.jaer.util.FileAccessTimeout;

/**
 * Records event data to disk according to various criteria.
 * @author tobi
 */
@Description("Records event data to disk according to various criteria.")
public class DataLogger extends EventFilter2D {

    private boolean recordingEnabled = false; // controlled by filterEnabled
    private AEFileOutputStream recordingOutputStream;
    private String defaultRecordingFolderName = System.getProperty("user.dir");
    // lastRecordingFolder starts off at user.dir which is startup folder "host/java" where .exe launcher lives
    private String recordingFolder = getPrefs().get("DataLogger.loggingFolder", defaultRecordingFolderName);
    private File recordingFile;
    private int maxLogFileSizeMB = prefs().getInt("DataLogger.maxLogFileSizeMB", 100);
    private boolean rotateFilesEnabled = prefs().getBoolean("DataLogger.rotateFilesEnabled", false);
    private int rotatePeriod = prefs().getInt("DataLogger.rotatePeriod", 7);
    private long bytesWritten = 0;
    private String logFileBaseName = prefs().get("DataLogger.logFileBaseName", "");
    private int rotationNumber = 0;
    private boolean filenameTimestampEnabled = prefs().getBoolean("DataLogger.filenameTimestampEnabled", true);
    /** Snapshot used by the active recording, and whether this logger created it. */
    private RecordingConfigurationSnapshot activeRecordingSnapshot;
    private boolean recordingSnapshotCapturedHere;

    public DataLogger(AEChip chip) {
        super(chip);
        final String cont = "Control", params = "Parameters";
        setPropertyTooltip(cont, "recordingEnabled", "Enable to start recording data");
        setPropertyTooltip(params, "filenameTimestampEnabled", "adds a timestamp to the filename, but means rotation will not overwrite old data files and will eventually fill disk");
        setPropertyTooltip(params, "logFileBaseName", "the base name of the recording file - if empty the AEChip class name is used");
        setPropertyTooltip(params, "rotatePeriod", "recording file rotation period");
        setPropertyTooltip(params, "rotateFilesEnabled", "enabling rotates recording files over rotatePeriod");
        setPropertyTooltip(params, "maxLogFileSizeMB", "recording is stopped when files get larger than this in MB");
        setPropertyTooltip(params, "recordingFolder", "directory to store recorded data files");
        // check lastRecordingFolder to see if it really exists, if not, default to user.dir
        File lf = new File(recordingFolder);
        if (FileAccessTimeout.directoryOrNull(lf) == null) {
            log.warning("recordingFolder " + lf + " doesn't exist, isn't a directory, or was not reachable within "
                    + FileAccessTimeout.timeoutMs() + " ms, defaulting to " + defaultRecordingFolderName);
            setRecordingFolder(defaultRecordingFolderName);
        }
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        recordData(in);
        return in;
    }

    synchronized private void recordData(EventPacket eventPacket) {
        if (eventPacket == null) {
            return;
        }
        // if we are recording data to disk do it here
        if (recordingEnabled) {
            try {
                recordingOutputStream.writePacket(eventPacket); // record all events
                bytesWritten += eventPacket.getSize();
                if (bytesWritten >>> 20 > maxLogFileSizeMB) {
                    setRecordingEnabled(false);
                    if (rotateFilesEnabled) {
                        startRecording();
                    }
                }
            } catch (IOException e) {
                log.warning("while recording data to " + recordingFile + " caught " + e + ", will try to close file");
                recordingEnabled = false;
                try {
                    recordingOutputStream.close();
                    log.info("closed recording file " + recordingFile);
                } catch (IOException e2) {
                    log.warning("while closing recording file " + recordingFile + " caught " + e2);
                } finally {
                    recordingOutputStream = null;
                    releaseActiveRecordingSnapshot();
                }
                getSupport().firePropertyChange("recordingEnabled", null, false);
            }
        }
    }

    @Override
    synchronized public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    public void doSelectRecordingFolder() {
        if (recordingFolder == null || recordingFolder.isEmpty()) {
            recordingFolder = System.getProperty("user.dir");
        }
        JFileChooser chooser = new JFileChooser(recordingFolder);
        chooser.setDialogTitle("Choose data recording folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        int retval = chooser.showOpenDialog(getChip().getAeViewer().getFilterFrame());
        if (retval == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (f != null && f.isDirectory()) {
                setRecordingFolder(f.toString());
                log.info("selected data recording folder " + recordingFolder);
            } else {
                log.warning("tried to select invalid recording folder named " + f);
            }
        }
    }

    /** Starts recording AE data to a file.
     *
     * @param filename the filename to record to, including all path information. Filenames without path
     * are recorded to the startup folder. Appends {@link AEDataFile#DATA_FILE_EXTENSION_AEDAT2}
     * if there is no known data-file extension.
     *
     * @return the file that is recorded to.
     */
    synchronized public File startRecording(String filename) {
        if (filename == null) {
            log.warning("tried to log to null filename, aborting");
            return null;
        }
        if (recordingEnabled) {
            log.warning("already recording to " + recordingFile);
            return recordingFile;
        }
        if (!AEDataFile.hasDataFileExtension(filename)) {
            filename = filename + AEDataFile.DATA_FILE_EXTENSION_AEDAT2;
            log.info("Appended extension to make filename=" + filename);
        }
        RecordingConfigurationSnapshot snapshot = null;
        boolean capturedHere = false;
        FileOutputStream fileOutputStream = null;
        AEFileOutputStream newRecordingOutputStream = null;
        try {
            recordingFile = new File(filename);

            // Reuse an owner-supplied snapshot by identity. Only a direct call with
            // no owner captures and temporarily installs its own snapshot.
            snapshot = chip.getRecordingConfigurationSnapshot();
            capturedHere = snapshot == null;
            if (capturedHere) {
                snapshot = RecordingConfigurationSnapshot.captureFromChip(chip);
                chip.setRecordingConfigurationSnapshot(snapshot);
            }
            // The one-arg AEFileOutputStream used to be constructed with a null chip and
            // null version, which NPE'd on chip.getHardwareInterface(); and a
            // BufferedOutputStream wrapper has no FileChannel of its own, so the 2.0
            // writer (which uses its own ByteBuffer/channel) cannot buffer through it.
            // Use the active chip and AEDAT-2 version with a FileOutputStream, the same
            // pattern as the AEViewer legacy path.
            fileOutputStream = new FileOutputStream(recordingFile);
            newRecordingOutputStream = new AEFileOutputStream(fileOutputStream, chip, AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2);
            fileOutputStream = null; // ownership transferred to the AEFileOutputStream
            recordingOutputStream = newRecordingOutputStream;
            activeRecordingSnapshot = snapshot;
            recordingSnapshotCapturedHere = capturedHere;
            recordingEnabled = true;
            getSupport().firePropertyChange("recordingEnabled", null, true);
            log.info("starting recording to " + recordingFile);

        } catch (IOException | RuntimeException e) {
            // A constructor or listener can fail after the raw stream is open. Close
            // whichever layer owns it, then release only a snapshot captured here.
            try {
                if (newRecordingOutputStream != null) {
                    newRecordingOutputStream.close();
                } else if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException | RuntimeException closeException) {
                log.warning("while closing failed recording start for " + filename + " caught " + closeException);
            }
            recordingOutputStream = null;
            activeRecordingSnapshot = null;
            recordingSnapshotCapturedHere = false;
            releaseSnapshotIfCapturedHere(snapshot, capturedHere);
            recordingFile = null;
            log.warning("exception on starting to record data to file "+filename+": "+e);
            recordingEnabled=false;
            getSupport().firePropertyChange("recordingEnabled", null, false);
        }

        return recordingFile;
    }

    /** Starts recording data to a default data recording file.
     * @return the file that is recorded to.
     */
    synchronized public File startRecording() {

        if (recordingEnabled) {
            return recordingFile;
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
            filename = recordingFolder + File.separator + base + "-" + dateString + "-" + suffix + AEDataFile.DATA_FILE_EXTENSION_AEDAT2;
            File lf = new File(filename);
            if (rotateFilesEnabled) {
                succeeded = true; // if rotation, always use next file
            } else if (!lf.isFile()) {
                succeeded = true;
            }

        } while (succeeded == false && suffixNumber++ <= 99);
        if (succeeded == false) {
            log.warning("could not open a unique new file for recording after trying up to " + filename + " aborting startRecording");
            return null;
        }

        File lf = startRecording(filename);
        bytesWritten = 0;
        return lf;

    }

    /** Stops recording and optionally opens file dialog for where to save file.
     * If number of AEViewers is more than one, dialog is also skipped since we may be recording from multiple viewers.
     * @param confirmFilename true to show file dialog to confirm filename, false to skip dialog.
     * @return chosen File
     */
    synchronized public File stopRecording(boolean confirmFilename) {
        if (!recordingEnabled) {
            return null;
        }
        // the file has already been recorded somewhere with a timestamped name, what this method does is
        // to move the already recorded file to a possibly different location with a new name, or if cancel is hit,
        // to delete it.
        int retValue = JFileChooser.CANCEL_OPTION;

        try {
            log.info("stopped recording at " + AEDataFile.DATE_FORMAT.format(new Date()));
            recordingEnabled = false;
            recordingOutputStream.close();
            recordingOutputStream = null;
            // Release logger-owned state before save/rename UI or a rotation starts.
            releaseActiveRecordingSnapshot();
// if jaer viewer is recording synchronized data files, then just save the file where it was recorded originally

            if (confirmFilename) {
                JFileChooser chooser = new JFileChooser();
                chooser.setCurrentDirectory(new File(recordingFolder));
                chooser.setFileFilter(new DATFileFilter());
                chooser.setDialogTitle(AEDataFile.saveRecordedDataTitle(
                        AEDataFile.dataFileExtensionOf(recordingFile != null ? recordingFile.getName() : null)));

                String fn =
                        recordingFile.getName();
                String base = fn;
                String fnLower = fn.toLowerCase();
                for (String ext : new String[]{
                    AEDataFile.DATA_FILE_EXTENSION_AEDAT4,
                    AEDataFile.DATA_FILE_EXTENSION_AEDAT2,
                    AEDataFile.DATA_FILE_EXTENSION,
                    AEDataFile.OLD_DATA_FILE_EXTENSION}) {
                    if (fnLower.endsWith(ext)) {
                        base = fn.substring(0, fn.length() - ext.length());
                        break;
                    }
                }
                chooser.setSelectedFile(new File(base));
                chooser.setDialogType(JFileChooser.SAVE_DIALOG);
                chooser.setMultiSelectionEnabled(false);
                boolean savedIt = false;
                do {
                    retValue = RecordingSaveDialogGuard.showSaveDialog(chooser, chip.getAeViewer(), base);
                    if (retValue == JFileChooser.APPROVE_OPTION) {
                        File newFile = chooser.getSelectedFile();
                        if (RecordingSaveDialogGuard.isStrayRecordingShortcutFilename(newFile.getName())) {
                            RecordingSaveDialogGuard.restoreSelectedFilename(chooser, base);
                            chooser.setDialogTitle(AEDataFile.saveRecordedDataTitle(
                                    AEDataFile.dataFileExtensionOf(recordingFile != null ? recordingFile.getName() : null),
                                    "restored default filename"));
                            continue;
                        }
                        if (!AEDataFile.hasDataFileExtension(newFile.getName())) {
                            newFile = new File(newFile.getCanonicalPath() + AEDataFile.DATA_FILE_EXTENSION_AEDAT2);
                        }
// we'll rename the recorded data file to the selection

                        boolean renamed = recordingFile.renameTo(newFile);
                        if (renamed) {
                            // if successful, cool, save persistence
                            savedIt = true;
                            setRecordingFolder(chooser.getCurrentDirectory().getPath());
                            recordingFile = newFile; // so that we play it back if it was saved and playback immediately is selected
                            log.info("renamed recording file to " + newFile);
                        } else {
                            // confirm overwrite
                            int overwrite = JOptionPane.showConfirmDialog(chooser, "Overwrite file \"" + newFile + "\"?", "Overwrite file?", JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
                            if (overwrite == JOptionPane.OK_OPTION) {
                                // we need to delete the file
                                boolean deletedOld = newFile.delete();
                                if (deletedOld) {
                                    savedIt = recordingFile.renameTo(newFile);
                                    savedIt = true;
                                    log.info("renamed recording file to " + newFile); // TODO something messed up here with confirmed overwrite of recording file
                                    recordingFile = newFile;
                                } else {
                                    log.warning("couldn't delete recording file " + newFile);
                                }

                            } else {
                                chooser.setDialogTitle("Couldn't save file there, try again");
                            }

                        }
                    } else {
                        // user hit cancel, delete recorded data
                        boolean deleted = recordingFile.delete();
                        if (deleted) {
                            log.info("Deleted temporary recording file " + recordingFile);
                        } else {
                            log.warning("Couldn't delete temporary recording file " + recordingFile);
                        }

                        savedIt = true;
                    }

                } while (savedIt == false); // keep trying until user is happy (unless they deleted some crucial data!)
                }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            recordingOutputStream = null;
            releaseActiveRecordingSnapshot();
        }

        recordingEnabled = false;
        getSupport().firePropertyChange("recordingEnabled", null, false);
        return recordingFile;
    }

    /** Release the active snapshot only when this DataLogger captured and installed it. */
    private void releaseActiveRecordingSnapshot() {
        RecordingConfigurationSnapshot snapshot = activeRecordingSnapshot;
        boolean capturedHere = recordingSnapshotCapturedHere;
        activeRecordingSnapshot = null;
        recordingSnapshotCapturedHere = false;
        releaseSnapshotIfCapturedHere(snapshot, capturedHere);
    }

    /** Never stomp an owner-supplied or subsequently replaced snapshot. */
    private void releaseSnapshotIfCapturedHere(RecordingConfigurationSnapshot snapshot, boolean capturedHere) {
        if (capturedHere && snapshot != null && chip.getRecordingConfigurationSnapshot() == snapshot) {
            chip.setRecordingConfigurationSnapshot(null);
        }
    }

    /**
     * @return the lastFolderName
     */
    public String getRecordingFolder() {
        return recordingFolder;
    }

    /**
     * @param recordingFolder the lastFolderName to set
     */
    public void setRecordingFolder(String recordingFolder) {
        String old = recordingFolder;
        this.recordingFolder = recordingFolder;
        getPrefs().put("DataLogger.loggingFolder", recordingFolder);
        getSupport().firePropertyChange("recordingFolder", old, recordingFolder);
    }

    /**
     * @return the recordingEnabled
     */
    private boolean isRecordingEnabled() {
        return recordingEnabled;
    }

    /**
     * @param recordingEnabled the recordingEnabled to set
     */
    synchronized private void setRecordingEnabled(boolean recordingEnabled) {
        boolean old = this.recordingEnabled;
        boolean success = false;
        if (recordingEnabled) {
            File f = startRecording();
            if (f == null) {
                log.warning("startRecording returned null");
                recordingEnabled=false;
            } else {
                success = true;
            }
        } else {
            File f = stopRecording(false);
            if (f == null) {
                log.warning("stopRecording returned null");
            } else {
                success = true;
            }
        }
            this.recordingEnabled = recordingEnabled;
            getSupport().firePropertyChange("recordingEnabled", old, recordingEnabled);
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
        setRecordingEnabled(yes);
    }


}    
