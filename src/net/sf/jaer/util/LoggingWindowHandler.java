package net.sf.jaer.util;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/** A singleton handler for uncaught exceptions.
 */
public class LoggingWindowHandler extends Handler {
    private static LoggingWindow window;
    private static LoggingWindowHandler handler;
    
    private LoggingWindowHandler() {
        configure();
        window = new LoggingWindow("Uncaught Exception in jAER...", 500, 400);
    }

    /** Returns the instance */
    public static synchronized LoggingWindowHandler getInstance() {
        if (handler == null) {
            handler = new LoggingWindowHandler();
        }
        return handler;
    }
    
    /**
     * Get any configuration properties set
     */
    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String className = getClass().getName();
        String level = manager.getProperty(className + ".level");
        setLevel((level == null) ? Level.INFO : Level.parse(level));
        String filter = manager.getProperty(className + ".filter");
        setFilter(makeFilter(filter));
        String formatter =
                manager.getProperty(className + ".formatter");
        setFormatter(makeFormatter(formatter));
    }
    
    private Filter makeFilter(String name) {
        Filter f = null;
        try {
            Class c = Class.forName(name);
            f = (Filter)c.newInstance();
        } catch (Exception e) {
            if (name != null) {
                System.err.println("Unable to load filter: " + name);
            }
        }
        return f;
    }
    
    private Formatter makeFormatter(String name) {
        Formatter f = null;
        try {
            Class c = Class.forName(name);
            f = (Formatter)c.newInstance();
        } catch (Exception e) {
            f = new SimpleFormatter();
        }
        return f;
    }
    
    // Overridden abstract Handler methods
    
    public void close() {
    }
    
    public void flush() {
    }
    
    /**
     * If record is loggable, format it and add it to window
     */
    public void publish(LogRecord record) {
        String message = null;
        if (isLoggable(record)) {
            try {
                message = getFormatter().format(record);
            } catch (Exception e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
                return;
            }
            try {
                window.addLogInfo(message);
            } catch (Exception e) {
                reportError(null, e, ErrorManager.WRITE_FAILURE);
            }
        }
    }
}

