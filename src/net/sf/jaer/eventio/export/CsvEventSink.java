package net.sf.jaer.eventio.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.util.textio.DavisTextEventFormatter;

/**
 * Writes DVS polarity events as one text/CSV line each.
 */
public final class CsvEventSink implements AutoCloseable {

    private final PrintWriter out;
    private final DavisTextEventFormatter formatter;
    private long eventsWritten;

    public CsvEventSink(File file, DavisTextEventFormatter formatter, File sourceFile) throws IOException {
        this.formatter = formatter != null ? formatter : DavisTextEventFormatter.rpg();
        this.out = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), 1 << 20));
        out.println("# jAER DAVIS/DVS camera text file output");
        out.println("# created " + new Date());
        out.println("# source-file: " + (sourceFile != null ? sourceFile : "(unknown)"));
        out.println("# dvs-events: One event per line: " + this.formatter.columnLegend());
        out.println("# format: " + this.formatter.shortFormatHint());
    }

    public void write(PolarityEvent ae) {
        if (ae == null) {
            return;
        }
        out.println(formatter.format(ae));
        eventsWritten++;
        if (out.checkError()) {
            throw new IllegalStateException("Error writing CSV/text events");
        }
    }

    public long getEventsWritten() {
        return eventsWritten;
    }

    @Override
    public void close() {
        out.close();
    }
}
