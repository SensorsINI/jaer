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
import java.util.logging.*;

/**
 * Eases writing log files for any purpose
 * @author tobi
 */
public class TobiLogger {
    
    
    static Logger log=Logger.getLogger("TobiLogger");
    protected PrintStream logStream;
    boolean logDataEnabled=false;
    String headerLine;
    String filename;
    
    /**
     * Creates a new instance of EventFilterDataLogger for a filter.
     *@param filter the filter (used to make filename)
     *@param headerLineComment a comment usuually specifying the contents and data fields
     */
    public TobiLogger(String filename, String headerLineComment) {
        if(!filename.endsWith(".txt")) filename=filename+".txt";
        this.filename=filename;
        this.headerLine=headerLineComment;
    }
    
    synchronized public void log(String s) {
        if(logStream!=null) {
            logStream.println(s);
            if(logStream.checkError()) log.warning("eroror logging data");
        }
    }
    
    public boolean isEnabled() {
        return logDataEnabled;
    }
    
    synchronized public void setEnabled(boolean logDataEnabled){
        this.logDataEnabled = logDataEnabled;
        if(!logDataEnabled) {
            logStream.flush();
            logStream.close();
            logStream=null;
        }else{
            try{
                logStream=new PrintStream(new BufferedOutputStream(new FileOutputStream(new File(filename))));
                logStream.println(headerLine);
                log.info("opened log file name "+filename);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        
    }
    
}
