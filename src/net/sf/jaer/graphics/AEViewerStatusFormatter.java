/*
 * JAERConsoleLoggingFormatter.java
 *
 * Created on June 3, 2007, 9:21 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright June 3, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.graphics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A super short logging formatter intended for status fields.
 * @author tobi
 */
public class AEViewerStatusFormatter extends Formatter{
    
    private DateFormat dateFormat=new SimpleDateFormat("kk:mm:ss ");
    
    /**
     * Format the given LogRecord.
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    public synchronized String format(LogRecord record) {
	StringBuffer sb = new StringBuffer(dateFormat.format(new Date()));
	String message = formatMessage(record);
	sb.append(message);
	if (record.getThrown() != null) {
	    try {
	        StringWriter sw = new StringWriter();
	        PrintWriter pw = new PrintWriter(sw);
	        record.getThrown().printStackTrace(pw);
	        pw.close();
		sb.append(sw.toString());
	    } catch (Exception ex) {
	    }
	}
	return sb.toString();
    }

    
}
