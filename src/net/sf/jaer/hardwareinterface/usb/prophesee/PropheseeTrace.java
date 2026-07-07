package net.sf.jaer.hardwareinterface.usb.prophesee;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Temporary EVK4 diagnostics. Enable with {@code -Djaer.prophesee.trace=true} or
 * {@code logging.properties}: {@code net.sf.jaer.level=FINER}.
 * TODO(remove): delete this class once EVK4 live capture is verified stable.
 */
final class PropheseeTrace {

    static final boolean ENABLED = Boolean.getBoolean("jaer.prophesee.trace");

    private PropheseeTrace() {
    }

    static void fine(Logger log, String msg, Object... params) {
        if (ENABLED) {
            log.log(Level.FINE, msg, params);
        }
    }

    static void finer(Logger log, String msg, Object... params) {
        if (ENABLED) {
            log.log(Level.FINER, msg, params);
        }
    }
}
