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

import net.sf.jaer.util.*;
import java.util.logging.*;

/**
 * Defines logging format for status messages in jAER.
 * @author tobi
 */
public class AEViewerStatusFormatter extends SimpleFormatter{
    
    /** Creates a new instance of JAERConsoleLoggingFormatter */
    public AEViewerStatusFormatter() {
    }

    /** Replaces \n with space to single/line format the message */
    @Override public String format(LogRecord record){
        StringBuilder sb=new StringBuilder();

        sb.append(super.format(record));
        int nl=sb.indexOf("\n");
        while(nl>=1){
            sb.replace(nl, nl+1, ": ");
             nl=sb.indexOf("\n");
        }
        String s=sb.toString();
        return s;
    }
}
