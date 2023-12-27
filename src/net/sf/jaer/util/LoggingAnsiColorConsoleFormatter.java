package net.sf.jaer.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Based on
 * https://stackoverflow.com/questions/54909752/how-to-change-the-util-logging-logger-printing-colour-in-logging-properties
 * with ANSI codes from
 * https://stackoverflow.com/questions/5762491/how-to-print-color-in-console-using-system-out-println
 *
 * @author tobid Jan 2021
 */
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

    private final Date date = new Date();
    // FORMAT uses e.g. 2$ which refers to 2nd argument of String.format
    // It has two lines: Line 1 is the date and class/method. Line 2 is the LEVEL and message
    // Lines are separated by the format spec %n which makes newline
    // This format puts date and class/method in CYAN, followed by newline with level colored, followed by default message color
    private static final String FORMAT = ANSI_CYAN+"%2$tb %2$td, %2$tY %2$tl:%2$tM:%2$tS %2$Tp %3$s%n%1$s%5$s:" + ANSI_RESET + " %6$s%7$s%n";
    // args to String.format
    // 1 ansi code
    // 2 date 
    // 3 source (class/method) 
    // 4 logger name 
    // 5 level 
    // 6 message, 
    // 7 throwable
    // output example
    // Jan 05, 2021 7:09:55 AM net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter resetFilter
    //INFO: resetting BackgroundActivityFilter

    @Override
    public String format(LogRecord record) {
        date.setTime(record.getMillis());
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
                return String.format(FORMAT, ANSI_GREEN_BACKGROUND+ANSI_BLACK, date, source, record.getLoggerName(),
                        record.getLevel().getLocalizedName(), message + ANSI_RESET, throwable);
            case "WARNING":
                return String.format(FORMAT, ANSI_YELLOW_BACKGROUND+ANSI_BLACK, date, source, record.getLoggerName(),
                        record.getLevel().getLocalizedName(), message + ANSI_RESET, throwable);
            case "SEVERE":
                return String.format(FORMAT, ANSI_RED_BACKGROUND + ANSI_WHITE, date, source, record.getLoggerName(),
                        record.getLevel().getLocalizedName(), message + ANSI_RESET, throwable);
            default:
                return String.format(FORMAT, ANSI_WHITE_BACKGROUND + ANSI_BLUE, date, source, record.getLoggerName(),
                        record.getLevel().getLocalizedName(), message + ANSI_RESET, throwable);
        }
    }
}
