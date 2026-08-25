package net.sf.jaer.util;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * No-op JUL handler listed first in {@code conf/Logging.properties} so
 * {@link JaerTmpdir#get()} runs before {@code FileHandler} opens
 * {@code %t/jaer/jAER-%g.log} (FileHandler does not create parent dirs).
 */
public final class JaerTmpdirLogBootstrap extends Handler {

    public JaerTmpdirLogBootstrap() {
        JaerTmpdir.get();
    }

    @Override
    public void publish(LogRecord record) {
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
