/*
 * TobiLogger.java
 *
 * Created on February 12, 2008, 3:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import java.awt.Desktop;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.logging.Logger;

import net.sf.jaer.eventio.AEDataFile;

/**
 * Eases writing log files for any purpose. To use it, construct a new instance,
 * then enable it to open the file and enable the subsequent logging calls to
 * write. Enabling logging automatically opens the file. The logging files are
 * created users home folder, i.e. System.properties.user.home. 
 * 
 * <p>By default, each
 * log call prepends the system time in ms since logging started automatically as the first field, followed
 * by a separator character, by default a comma to allow easier parsing as CSV
 * file. Comment lines have the comment character # prepended to them.
 *
 * @author tobi
 */
public class TobiLogger {

    static Logger log = Logger.getLogger("TobiLogger");
    protected PrintStream logStream;
    boolean logDataEnabled = false;
    private boolean absoluteTimeEnabled = false;
    private boolean nanotimeEnabled = false;
    private long startingTime = 0;
    private String columnHeaderLine;
    private String fileCommentString;
    private String fileNameBase;
    private String fileNameActual = null;
    /** Default field separator */
    public static final String DEFAULT_FIELD_SEP_STRING=",";
    private String separator = DEFAULT_FIELD_SEP_STRING;
    private File file = null;
    /** The default comment line header */
    public static final String DEFAULT_COMMENT="#";
    private String commentChar="#";
    
    private boolean suppressShowingFolder=false;
    private boolean suppressTimeField=false;
    
    /** Add a version string for this logger.
     When this file is changed, please increase this version number.
     **/
    private static final String TOBI_LOGGER_VERSION = "1.0";

    /**
     * Creates a new instance of TobiLogger.
     *
     * @param filename the filename. Date/Timestamp string us appended to the
     * filename and ".txt" is appended if it is not already the suffix, e.g.
     * "PencilBalancer-2008-10-12T10-23-58+0200.txt". The file is created in the
     * users home folder.
     * @param columnHeaderLine a comment usually specifying the contents and
     * data fields, a {@value #DEFAULT_COMMENT} is prepended automatically. A second header line is also
     * written automatically with the file creation date, e.g. "# created Sat
     * Oct 11 13:04:34 CEST 2008"
     * @see #setFileCommentString(java.lang.String) 
     * @see #setColumnHeaderLine(java.lang.String) 
     * @see #setCommentChar(java.lang.String) 
     */
    public TobiLogger(String filename, String columnHeaderLine) {
        if (!filename.endsWith(".txt")) {
            filename = filename + ".txt";
        }
        this.fileNameBase = filename;
        this.columnHeaderLine = columnHeaderLine;
    }

    private String getTimestampedFilename() {
        String base = getFileNameBase();
        if (getFileNameBase().endsWith(".txt")) {
            base = getFileNameBase().substring(0, getFileNameBase().lastIndexOf('.'));
        }
        Date d = new Date();
        String fn = base + '-' + AEDataFile.DATE_FORMAT.format(d) + ".txt";
        return fn;
    }

    /**
     * Logs a string to the file (\n is appended), if logging is enabled.
     * Prepends the relative or absolute time in ms or nanoseconds, depending on
     * nanotimeEnabled, tab separated from the rest of the string.
     *
     * @param s the string
     * @see #setEnabled
     */
    synchronized public void log(String s) {
        if (!logDataEnabled) {
            return;
        }
        if (logStream != null) {
            long time;
            time = nanotimeEnabled ? System.nanoTime() : System.currentTimeMillis();
            if (!absoluteTimeEnabled) {
                time -= startingTime;
            }
            String timeString = String.format("%d%s%s", time, separator, s);
            logStream.println(timeString);
            if (logStream.checkError()) {
                log.warning("error logging data");
            }
        }
    }

    /**
     * Adds a comment to the log preceeded by # . Note this method should be
     * called just after setEnabled(true).
     *
     * @param s the string
     * @see #setEnabled
     */
    synchronized public void addComment(String s) { // TODO needs to handle multiline comments by prepending comment char to each line
        if (!logDataEnabled) {
            log.warning("comment will not be logged because logging is not enabled yet. call setEnable to open logging file before adding comment");
            return;
        }
        if (logStream != null) {
            logStream.print(getCommentChar());
            logStream.println(s);
            if (logStream.checkError()) {
                log.warning("error adding log comment");
            }
        }
    }

    public boolean isEnabled() {
        return logDataEnabled;
    }

    /**
     * Enables or disables logging; default is disabled. Each time logging is
     * enabled a new log file is created with its own timetamped filename. Each
     * time logging is disabled the existing log file is closed. Reenabling
     * logging opens a new logging file.
     * 
     * The folder is opened unless {@link #setSuppressShowingFolder(boolean)} supresses it.
     *
     * @param logDataEnabled true to enable logging
     */
    synchronized public void setEnabled(boolean logDataEnabled) {
        this.logDataEnabled = logDataEnabled;
        if (!logDataEnabled) {
            if (logStream == null) {
                log.warning("disabling logging but stream was never created");
                return;
            }
            log.info("closing logging file " + getFileNameActual());
            logStream.flush();
            logStream.close();
            logStream = null;
            if(!isSuppressShowingFolder()){
                showFolderInDesktop();
            }
        } else {
            try {
                if(checkFilename(getFileNameBase()))
                {
                    fileNameActual = getTimestampedFilename();
                }
                else       // if no path is specified, home folder is used by default.
                {
                    fileNameActual = System.getProperty("user.home")+File.separator+getTimestampedFilename();                    
                }
                this.file = new File(fileNameActual);
                logStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(getFile())));
                // start with generic header of toString()
                logStream.println(getCommentChar()+toString());
                // add creation date
                logStream.println(getCommentChar()+"created " + new Date());
                // add user's comment header if user set it
                if (getFileCommentString() != null)
                {
                    String s=getCommentChar()
                            +getFileCommentString().replaceAll("\\R", System.lineSeparator()+getCommentChar());
                    logStream.println(s);                    
                }
                // finally the first record, column names usually
                logStream.println(getFirstRecordLine());
                
                log.info("created log file name " + getFileNameActual());
                startingTime = nanotimeEnabled ? System.nanoTime() : System.currentTimeMillis();
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {
                        setEnabled(false);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * @see #setAbsoluteTimeEnabled(boolean)
     */
    public boolean isAbsoluteTimeEnabled() {
        return absoluteTimeEnabled;
    }

    /**
     * If true, then absolute time (since 1970) in ms is first item in line,
     * otherwise, time since file creation is logged. This facility eases use in
     * matlab which doesn't like to deal with these large integers, preferring
     * to round them off to doubles that resolve the actual time differences of
     * interest to 0.
     *
     * @param absoluteTimeEnabled default false
     */
    public void setAbsoluteTimeEnabled(boolean absoluteTimeEnabled) {
        this.absoluteTimeEnabled = absoluteTimeEnabled;
    }

    /**
     * @see #setNanotimeEnabled(boolean)
     */
    public boolean isNanotimeEnabled() {
        return nanotimeEnabled;
    }

    /**
     * Sets whether to use and log System.nanotime() or the (default)
     * System.currentTimeMillis()
     *
     * @param nanotimeEnabled true to use nanotime
     */
    public void setNanotimeEnabled(boolean nanotimeEnabled) {
        this.nanotimeEnabled = nanotimeEnabled;
    }

    /**
     * Use this method to set the header line that is written before any logging line. 
     * It is NOT prepended by comment character so it can be used for CSV column header line.
     * It does take into account {@link #setSuppressTimeField(boolean) }.
     * 
     * @return the columnHeaderLine
     */
    public String getFirstRecordLine() {
        return (isSuppressTimeField()?"":"systemTimeMs"+getSeparator())+getColumnHeaderLine();
    }


    @Override
    public String toString() {
        return "TobiLogger{V" + TOBI_LOGGER_VERSION + ", absoluteTimeEnabled=" + absoluteTimeEnabled + ", nanotimeEnabled=" + nanotimeEnabled + ", startingTime=" + startingTime + ", headerLine=" + getColumnHeaderLine() + ", fileNameActual=" + getFileNameActual() + '}';
    }

    /**
     * Returns String that separates logging time from rest of line. Default is {@value #DEFAULT_FIELD_SEP_STRING}
     *
     * @return the separator
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * Sets character that separates logging time from rest of line, by default {@value #DEFAULT_FIELD_SEP_STRING}.
     *
     * @param separator the separator to set
     */
    public void setSeparator(String separator) {
        this.separator = separator;
    }

    /**
     * Returns the full path including filename with timestamp of the last log file.
     * @return the fileNameActual
     */
    public String getFileNameActual() {
        return fileNameActual;
    }

    /**
     * @return the fileNameBase
     */
    public String getFileNameBase() {
        return fileNameBase;
    }

    /** check the filename provided by user is path or filename*/
    public boolean checkFilename(String filename) {
        File tmpFile = new File(filename);
        return tmpFile.isAbsolute();
    }
    
    /** Opens the last folder where logging was sent to in desktop file explorer */
    public void showFolderInDesktop() {
        if (!Desktop.isDesktopSupported()) {
            log.warning("Sorry, desktop operations are not supported, cannot show the folder with "+fileNameActual);
            return;
        }
        try {
            if (file.exists()) {
//                Path folder=Paths.get(fileNameActual).getParent().getFileName();
                File folder=file.getAbsoluteFile().getParentFile();
                Desktop.getDesktop().open(folder);
            }else{
                log.warning(file+" does not exist to open folder to");
            }
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }

    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * @return the commentChar
     */
    public String getCommentChar() {
        return commentChar;
    }

    /**
     * Sets the comment line character that starts each line of file header, by default {@value #DEFAULT_COMMENT}.
     * @param commentChar the commentChar to set
     */
    public void setCommentChar(String commentChar) {
        this.commentChar = commentChar;
    }

    /**
     * @return the suppressShowingFolder
     */
    public boolean isSuppressShowingFolder() {
        return suppressShowingFolder;
    }

    /**
     * Set true to prevent opening the logging folder when logging is ended. By default folder is 
     * shown with Desktop.showFolderInDesktop().
     * 
     * @param suppressShowingFolder the suppressShowingFolder to set
     */
    public void setSuppressShowingFolder(boolean suppressShowingFolder) {
        this.suppressShowingFolder = suppressShowingFolder;
    }

    /**
     * @return the suppressTimeField
     */
    public boolean isSuppressTimeField() {
        return suppressTimeField;
    }

    /**By default the first field of the log file is the time in ms (either absolute since 1970 or since start of recording, see {@link #setAbsoluteTimeEnabled(boolean)}. This method allows suppressing the field.
     * @param suppressTimeField the suppressTimeField to set
     */
    public void setSuppressTimeField(boolean suppressTimeField) {
        this.suppressTimeField = suppressTimeField;
    }

    /**
     * @return the columnHeaderLine
     */
    public String getColumnHeaderLine() {
        return columnHeaderLine;
    }

    /**
     * Sets the contents of the first header line.
     * The first line written after the file header, which usually in CSV files has the column names, separated by commas
     *
     * @param headerLineComment a line usually specifying the field names of the columns that follow
     */
    public void setColumnHeaderLine(String columnHeaderLine) {
        this.columnHeaderLine = columnHeaderLine;
    }

    /**
     * @return the fileCommentString
     */
    public String getFileCommentString() {
        return fileCommentString;
    }

    /**
     * A comment section written at the start of the file. Each line is automatically prepended by the comment char.
     * It includes other information automatically written with the file creation date, e.g. "# created Sat
     * Oct 11 13:04:34 CEST 2008"
     * @param fileCommentString the fileCommentString to set
     */
    public void setFileCommentString(String fileCommentString) {
        this.fileCommentString = fileCommentString;
    }

}
