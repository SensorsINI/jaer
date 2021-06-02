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
     * 
     * @param thread the thread that threw the exception
     * @param throwable the Throwable that caused the exception
     */
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Initialize logger once
        if (logger == null) {
            logger = Logger.getLogger("UncaughtExceptionLogger");
            Handler handler = LoggingWindowHandler.getInstance();
            logger.addHandler(handler);
        }
        try {
            logger.log(Level.WARNING, thread == null ? "(null thread supplied)" : thread.toString(), throwable == null ? "(null exception)" : throwable);
        } catch (RuntimeException rte) {
            System.out.println((thread == null ? "(null thread supplied)" : thread.toString()) + ": " + (throwable == null ? "(null exception)" : throwable.toString()));
            if (throwable != null) {
                throwable.printStackTrace();
                if (throwable.getCause() != null) {
                    throwable.getCause().printStackTrace();
                }
            }
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        if (thread != null) { // throwable might have a null stack trace because of some optimization of JVM (tobi)
            if (throwable == null) {
                new Throwable().printStackTrace(pw); // get stack trace if throwable is somehow null

            } else { // even if throwable is not null, if it doesn't have stack trace, get one from current thread
                StackTraceElement[] st = throwable.getStackTrace();
                if (st == null || st.length == 0) {
                    new Throwable().printStackTrace(pw); // get stack trace if throwable is somehow null
                }
            }
        }
        if (throwable != null) {
            throwable.printStackTrace(pw);
            if (throwable.getCause() != null) {
                throwable.getCause().printStackTrace(pw);
            }
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
