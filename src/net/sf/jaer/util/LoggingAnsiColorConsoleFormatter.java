package net.sf.jaer.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LoggingAnsiColorConsoleFormatter extends Formatter {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";
    public static final String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    public static final String ANSI_RED_BACKGROUND = "\u001B[41m";
    public static final String ANSI_GREEN_BACKGROUND = "\u001B[42m";
    public static final String ANSI_YELLOW_BACKGROUND = "\u001B[43m";
    public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    public static final String ANSI_PURPLE_BACKGROUND = "\u001B[45m";
    public static final String ANSI_CYAN_BACKGROUND = "\u001B[46m";
    public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";
    
    private final Date dat = new Date();
    private static final String format = "%1$s %2$tb %2$td, %2$tY %2$tl:%2$tM:%2$tS %2$Tp %3$s%n%5$s: %6$s%7$s%n";

    @Override
    public String format(LogRecord record) {
        dat.setTime(record.getMillis());
        String source;
        if (record.getSourceClassName() != null) {
            source = record.getSourceClassName();
            if (record.getSourceMethodName() != null) {
                source += " " + record.getSourceMethodName();
            }
        } else {
            source = record.getLoggerName();
        }
        String message = formatMessage(record);
        String throwable = "";
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            record.getThrown().printStackTrace(pw);
            pw.close();
            throwable = sw.toString();
        }

        switch (record.getLevel().toString()) {
            case "INFO":
                return String.format(format, ANSI_GREEN, dat, source, record.getLoggerName(),
                        record.getLevel().getLocalizedName(), message + ANSI_RESET, throwable);
            case "WARNING":
                return String.format(format, ANSI_RED, dat, source, record.getLoggerName(),
                        record.getLevel().getLocalizedName(), message + ANSI_RESET, throwable);
            case "SEVERE":
                return String.format(format, ANSI_RED_BACKGROUND+ANSI_BLACK, dat, source, record.getLoggerName(),
                        record.getLevel().getLocalizedName(), message + ANSI_RESET, throwable);
            default:
                return String.format(format, dat, source, record.getLoggerName(),
                        record.getLevel().getLocalizedName(), message, throwable);
        }
    }
}
