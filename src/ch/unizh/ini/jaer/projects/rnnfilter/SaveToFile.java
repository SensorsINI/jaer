/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.rnnfilter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileOutputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.util.DATFileFilter;

/**
 * Was unable to get the data logging done through DataLogger class, so wrote one very similar to DataLogger.java, mostly copied from there
 * 
 * @author jithendar
 */
public class SaveToFile {
    
    private boolean loggingEnabled = false; // controlled by filterEnabled
    private AEFileOutputStream loggingOutputStream;

    private File loggingFile;
    private int maxLogFileSizeMB = 100;

    private long bytesWritten = 0;
    private final AEChip chip;
    
    public SaveToFile(AEChip chip) {
        this.chip = chip;
    }
    
    synchronized public void logData(EventPacket eventPacket) {
        if (eventPacket == null) {
            return;
        }
        // if we are logging data to disk do it here
        if (isLoggingEnabled()) {
            try {
                loggingOutputStream.writePacket(eventPacket); // log all events
                bytesWritten += eventPacket.getSize();
                if (bytesWritten >>> 20 > maxLogFileSizeMB) {
                    setLoggingEnabled(false);
                }
            } catch (IOException e) {
                log.log(Level.WARNING, "while logging data to {0} caught {1}, will try to close file", new Object[]{loggingFile, e});
                setLoggingEnabled(false);
                try {
                    loggingOutputStream.close();
                    log.log(Level.INFO, "closed logging file {0}", loggingFile);
                } catch (IOException e2) {
                    log.log(Level.WARNING, "while closing logging file {0} caught {1}", new Object[]{loggingFile, e2});
                }
            }
        }
    }

    synchronized public void startLogging(String filename) {
        if (filename == null) {
            log.warning("tried to log to null filename, aborting");
            return;
        }
        if (!filename.toLowerCase().endsWith(AEDataFile.DATA_FILE_EXTENSION)) {
            filename = filename + AEDataFile.DATA_FILE_EXTENSION;
            log.info("Appended extension to make filename=" + filename);
        }
        try {
            loggingFile = new File(filename);

            loggingOutputStream = new AEFileOutputStream(new BufferedOutputStream(new FileOutputStream(loggingFile), 100000), this.chip, AEDataFile.DATA_FILE_VERSION_NUMBER);
            loggingEnabled = true;
            log.info("starting logging to " + loggingFile);

        } catch (IOException e) {
            loggingFile = null;
            log.warning("exception on starting to log data to file "+filename+": "+e);
            loggingEnabled=false;
        }
    }
        
    synchronized public void stopLogging() {
        if (!loggingEnabled) {
            return;
        }
        
        try {
            log.info("stopped logging at " + AEDataFile.DATE_FORMAT.format(new Date()));
            loggingEnabled = false;
            loggingOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        loggingEnabled = false;
     
    }


    
    /**
     * @return the loggingEnabled
     */
    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    /**
     * @param loggingEnabled the loggingEnabled to set
     */
    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }



    
}
