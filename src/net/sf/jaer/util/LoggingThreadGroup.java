package net.sf.jaer.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A thread group that implements uncaughtException to log uncaught exceptions
 */
public class LoggingThreadGroup extends ThreadGroup {

    private static Logger logger;

    public LoggingThreadGroup(String name) {
        super(name);
    }

    /**
     * Thread and Throwable passed in here are passed to a Logger named
     * "UncaughtExceptionLogger" which has the handler LoggingWindowHandler.
     */
    public void uncaughtException(Thread t, Throwable e) {
        // Initialize logger once
        if (logger == null) {
            logger = Logger.getLogger("UncaughtExceptionLogger");
            Handler handler = LoggingWindowHandler.getInstance();
            logger.addHandler(handler);
        }
        try {
            logger.log(Level.WARNING, t == null ? "(null thread supplied)" : t.toString(), e == null ? "(null exception)" : e.toString());
        } catch (RuntimeException rte) {
            System.out.println((t == null ? "(null thread supplied)" : t.toString()) + ": " + (e == null ? "(null exception)" : e.toString()));
            if(e!=null){
                e.printStackTrace();
            }
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        if (e != null) {
            e.printStackTrace(pw);
        } else {
            sw.write("(null exception, cannot provide stack trace)");
        }
        pw.flush();
        String st = sw.toString();
        logger.log(Level.WARNING, st);
    }
//    public static void main(String args[]) throws Exception {
//        Thread.UncaughtExceptionHandler handler = new LoggingThreadGroup("Logger");
//        Thread.currentThread().setUncaughtExceptionHandler(handler);
////        System.out.println(1 / 0);
//        throw new RuntimeException("test RuntimeException");
//    }
}
