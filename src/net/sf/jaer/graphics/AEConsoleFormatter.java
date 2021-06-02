/*
 * @(#)SimpleFormatter.java	1.16 10/03/23
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
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
 * Special formatter for built-in AEViewer Console, to make logging one line with no timestamp.
 * Print a brief summary of the LogRecord in a human readable
 * format.  The summary will typically be 1 line.
 *
 */

public class AEConsoleFormatter extends Formatter {


    private DateFormat dateFormat=new SimpleDateFormat("kk:mm:ss ");
    // Line separator string.  This is the value of the line.separator
    // property at the moment that the SimpleFormatter was created.
    private String lineSeparator = System.getProperty("line.separator");

    /**
     * Format the given LogRecord.
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    public synchronized String format(LogRecord record) {
        Date date=new Date();
        String time=dateFormat.format(date);
	StringBuffer sb = new StringBuffer(time);
	sb.append(record.getLevel().getLocalizedName());
	sb.append(": ");
	if (record.getSourceClassName() != null) {	
	    sb.append(record.getSourceClassName());
	} else {
	    sb.append(record.getLoggerName());
	}
	if (record.getSourceMethodName() != null) {	
	    sb.append(".");
	    sb.append(record.getSourceMethodName());
	}
//	sb.append(lineSeparator);
	String message = formatMessage(record);
	sb.append(": ");
	sb.append(message);
	sb.append(lineSeparator);
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
