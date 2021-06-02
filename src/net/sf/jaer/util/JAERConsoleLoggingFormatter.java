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

package net.sf.jaer.util;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Defines logging format for console messages in jAER. Use of this logger is defined in the logging
 configuration file host/java/conf/Logging.properties. There this class is defined as in the following
 example:
 <pre>
 # this logging configuration is used by jAER when running the application by specifying
# -Djava.util.logging.config.file=conf/Logging.properties in the java machine invocation

# Specify the handlers to create in the root logger

# Set the default logging level for the root logger
.level = INFO

# Set the default logging level for new ConsoleHandler and FileHandler instances
java.util.logging.ConsoleHandler.level = INFO
java.util.logging.FileHandler.level = ALL

# (all loggers are children of the root logger)
# The following creates two handlers
handlers = java.util.logging.ConsoleHandler, java.util.logging.FileHandler
#handlers = java.util.logging.FileHandler


# The following special tokens can be used in the pattern property
# which specifies the location and name of the log file.
#   / - standard path separator
#   %t - system temporary directory
#   %h - value of the user.home system property
#   %g - generation number for rotating logs
#   %u - unique number to avoid conflicts
java.util.logging.FileHandler.pattern=%t/jAER.log

# Set the default formatter for new ConsoleHandler instances
java.util.logging.ConsoleHandler.formatter = net.sf.jaer.util.JAERConsoleLoggingFormatter
java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter
 </pre>
 * @author tobi
 */
public class JAERConsoleLoggingFormatter extends SimpleFormatter{

    /** Creates a new instance of JAERConsoleLoggingFormatter */
    public JAERConsoleLoggingFormatter() {
    }

    @Override public synchronized String format(LogRecord record){
        StringBuilder sb=new StringBuilder();
       Level l=record.getLevel();
       boolean warning=false;  // we indent warning to make them stand out
       if(l.intValue()>=Level.WARNING.intValue()) {
           sb.append("       ******");
           warning=true;
       }
        sb.append(super.format(record)); // prepend marker "     ******", then append 1st line warning
        int nl=sb.indexOf("\n");
        while(nl>=1){
            sb.replace(nl, nl+1, ": ");
             nl=sb.indexOf("\n");
        }
        sb.append("\n");
        String s=sb.toString();
//        if(warning) {
//            s=s.replaceFirst("\n","       \n"); // replace newlines with "      \n"
//        }
//        s=s.replace("\n",": ");
        return s;
    }

//    public static void main(String[] args){
//        Logger log=Logger.getLogger("test");
//        log.info("info log jjjjjjjjjjjjjjjjjjjjj jjjaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbcccccccccccccccccccccccccccccccccccc");
//        log.warning("warning log jjjjjjjjjjjjjjjjjjjjj jjjaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbcccccccccccccccccccccccccccccccccccc");
//    }

}
