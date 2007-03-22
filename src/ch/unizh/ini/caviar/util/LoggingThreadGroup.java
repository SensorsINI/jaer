package ch.unizh.ini.caviar.util;
import java.util.logging.*;

/** A thread group that implements uncaughtException to log uncaught exceptions */
public class LoggingThreadGroup extends ThreadGroup {
    private static Logger logger;
    public LoggingThreadGroup(String name) {
        super(name);
    }
    public void uncaughtException(Thread t, Throwable e) {
        // Initialize logger once
        if (logger == null) {
            logger = Logger.getLogger("example");
            Handler handler = LoggingWindowHandler.getInstance();
            logger.addHandler(handler);
        }
        logger.log(Level.WARNING, t.getName(), e);
    }
//    public static void main(String args[]) throws Exception {
//        Thread.UncaughtExceptionHandler handler = new LoggingThreadGroup("Logger");
//        Thread.currentThread().setUncaughtExceptionHandler(handler);
//        System.out.println(1 / 0);
//    }
}

