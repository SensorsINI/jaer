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

package ch.unizh.ini.caviar.util;

import java.util.logging.*;

/**
 * Defines logging format for console messages in jAER
 * @author tobi
 */
public class JAERConsoleLoggingFormatter extends SimpleFormatter{
    
    /** Creates a new instance of JAERConsoleLoggingFormatter */
    public JAERConsoleLoggingFormatter() {
    }
    
    @Override public String format(LogRecord record){
        StringBuilder sb=new StringBuilder();
       Level l=record.getLevel();
       if(l.intValue()>=Level.WARNING.intValue()) {
           sb.append("******");
       }
        sb.append(super.format(record));
        String s=sb.toString();
//        s=s.replace("\n",": ");
        return s;
    }
    
}
