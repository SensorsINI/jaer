package net.sf.jaer.util;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A thread group that implements uncaughtException to log uncaught exceptions */
public class LoggingThreadGroup extends ThreadGroup {
    private static Logger logger;
    public LoggingThreadGroup(String name) {
        super(name);
    }

    /** Thread and Throwable passed in here are passed to a
     * Logger named "UncaughtExceptionLogger"
     * which has the handler LoggingWindowHandler.
     */
    public void uncaughtException(Thread t, Throwable e) {
        // Initialize logger once
        if (logger == null) {
            logger = Logger.getLogger("UncaughtExceptionLogger");
            Handler handler = LoggingWindowHandler.getInstance();
            logger.addHandler(handler);
        }
        logger.log(Level.WARNING, t.getName(), e);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        String st=sw.toString();
        logger.log(Level.WARNING,st);
    }
//    public static void main(String args[]) throws Exception {
//        Thread.UncaughtExceptionHandler handler = new LoggingThreadGroup("Logger");
//        Thread.currentThread().setUncaughtExceptionHandler(handler);
//        System.out.println(1 / 0);
//    }
}

