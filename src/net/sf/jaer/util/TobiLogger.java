/*
 * TobiLogger.java
 *
 * Created on February 12, 2008, 3:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.util;

import net.sf.jaer.eventio.AEDataFile;
import java.io.*;
import java.util.Date;
import java.util.logging.*;

/**
 * Eases writing log files for any purpose. 
 To use it, construct a new instance, then enable it to open the file and enable the subsequent logging calls to write.
 Enabling logging automatically opens the file.
 The logging files are created in the startup directory, e.g. in jAER, in the folder host/java.
 * Each log call prepends the system time automatically as the first field.
 * @author tobi
 */
public class TobiLogger {
    
    
    static Logger log=Logger.getLogger("TobiLogger");
    protected PrintStream logStream;
    boolean logDataEnabled=false;
    private boolean absoluteTimeEnabled=false;
    private boolean nanotimeEnabled=false;
    private long startingTime=0;
    private String headerLine;
    private String filename;
    
    /**
     * Creates a new instance of TobiLogger.
     *@param filename the filename. Date/Timestamp string us appended to the filename 
     * and ".txt" is appended if it is not already the suffix, e.g. "PencilBalancer-2008-10-12T10-23-58+0200.txt". The file is created in the program startup folder.
     *@param headerLineComment a comment usuually specifying the contents and data fields, a # is prepended automatically. 
     A second header line is also written automatically with the file creation date, e.g. "# created Sat Oct 11 13:04:34 CEST 2008"
     */
    public TobiLogger(String filename, String headerLineComment) {
        if(!filename.endsWith(".txt")) filename=filename+".txt";
        this.filename=filename;
        this.headerLine=headerLineComment;
    }
    
    private String getTimestampedFilename(){
        String base=filename;
        if(filename.endsWith(".txt")){
            base=filename.substring(0, filename.lastIndexOf('.'));
        }
        Date d=new Date();
        String fn=base+'-'+AEDataFile.DATE_FORMAT.format(d)+".txt";
        return fn;
    }
    
    /** Logs a string to the file (\n is appended), if logging is enabled.
     * Prepends the relative or absolute time in ms or nanoseconds, depending on nanotimeEnabled, comma separated from the rest of the string.
     * 
     * @param s the string
     * @see #setEnabled
     */
    synchronized public void log(String s) {
        if(!logDataEnabled) return;
        if(logStream!=null) {
            if(absoluteTimeEnabled){
                logStream.print((nanotimeEnabled?System.nanoTime():System.currentTimeMillis())+",");
            }else{
                logStream.print(((nanotimeEnabled?System.nanoTime():System.currentTimeMillis())-startingTime)+",");
            }
            logStream.println(s);
            if(logStream.checkError()) log.warning("eroror logging data");
        }
    }
    
    public boolean isEnabled() {
        return logDataEnabled;
    }
    
    /** Enables or disables logging; default is disabled. 
     * Each time logging is enabled a new log file is created with its own timetamped filename.
     * Each time logging is disabled the existing log file is closed. Reenabling logging opens a new logging file.
     * 
     * @param logDataEnabled true to enable logging 
     */
    synchronized public void setEnabled(boolean logDataEnabled){
        this.logDataEnabled = logDataEnabled;
        if(!logDataEnabled) {
            if(logStream==null){
                log.warning("disabling logging but stream was never created");
                return;
            }
            log.info("closing log file "+filename+" in folder "+System.getProperties().getProperty("user.dir"));
            logStream.flush();
            logStream.close();
            logStream=null;
        }else{
            try{
                String fn=getTimestampedFilename();
                logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File(fn))));
                logStream.println("# "+headerLine);
                logStream.println("# created "+new Date());
                log.info("created log file name "+fn+" in folder "+System.getProperties().getProperty("user.dir"));
                startingTime=nanotimeEnabled? System.nanoTime():System.currentTimeMillis();
                Runtime.getRuntime().addShutdownHook(new Thread(){
                        public void run(){
                            setEnabled(false);
                        }
                });
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        
    }

    public boolean isAbsoluteTimeEnabled() {
        return absoluteTimeEnabled;
    }

    /** If true, then absolute time (since 1970) in ms is first item in line, otherwise, time since file creation is logged.
     * This facility eases use in matlab which doesn't like to deal with these large integers, preferring to round them off to doubles that resolve
     * the actual time differences of interest to 0.
     * @param absoluteTimeEnabled default false
     */
    public void setAbsoluteTimeEnabled(boolean absoluteTimeEnabled) {
        this.absoluteTimeEnabled = absoluteTimeEnabled;
    }

    public boolean isNanotimeEnabled() {
        return nanotimeEnabled;
    }

    /** Sets whether to use System.nanotime() or the (default) System.currentTimeMillis()
     
     @param nanotimeEnabled true to use nanotime
     */
    public void setNanotimeEnabled(boolean nanotimeEnabled) {
        this.nanotimeEnabled = nanotimeEnabled;
    }
    
}
