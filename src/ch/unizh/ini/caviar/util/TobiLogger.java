/*
 * TobiLogger.java
 *
 * Created on February 12, 2008, 3:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.util;

import java.io.*;
import java.util.Date;
import java.util.logging.*;

/**
 * Eases writing log files for any purpose.
 * @author tobi
 */
public class TobiLogger {
    
    
    static Logger log=Logger.getLogger("TobiLogger");
    protected PrintStream logStream;
    boolean logDataEnabled=false;
    private boolean absoluteTimeEnabled=false;
    private long startingTime=0;
    private String headerLine;
    private String filename;
    
    /**
     * Creates a new instance of TobiLogger.
     *@param filename the filename. ".txt" is appended if it is not already the suffix. The file is created in the program startup folder.
     *@param headerLineComment a comment usuually specifying the contents and data fields
     */
    public TobiLogger(String filename, String headerLineComment) {
        if(!filename.endsWith(".txt")) filename=filename+".txt";
        this.filename=filename;
        this.headerLine=headerLineComment;
    }
    
    /** Logs a string to the file (\n is appended), if logging is enabled.
     * Prepends the 
     * 
     * @param s the string
     * @see #setEnabled
     */
    synchronized public void log(String s) {
        if(!logDataEnabled) return;
        if(logStream!=null) {
            if(absoluteTimeEnabled){
                logStream.print(System.currentTimeMillis()+" ");
            }else{
                logStream.print((System.currentTimeMillis()-startingTime)+" ");
            }
            logStream.println(s);
            if(logStream.checkError()) log.warning("eroror logging data");
        }
    }
    
    public boolean isEnabled() {
        return logDataEnabled;
    }
    
    /** Enables or disables logging; default is disabled. 
     * 
     * @param logDataEnabled true to enable logging 
     */
    synchronized public void setEnabled(boolean logDataEnabled){
        this.logDataEnabled = logDataEnabled;
        if(!logDataEnabled) {
            log.info("closing log file "+filename+" in folder "+System.getProperties().getProperty("user.dir"));
            logStream.flush();
            logStream.close();
            logStream=null;
        }else{
            try{
                logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File(filename))));
                logStream.println(headerLine);
                logStream.println("# created "+new Date());
                log.info("opened log file name "+filename+" in folder "+System.getProperties().getProperty("user.dir"));
                startingTime=System.currentTimeMillis();
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
    
}
