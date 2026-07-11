package net.sf.jaer.hardwareinterface.usb.prophesee;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Temporary EVK4 diagnostics.
 * <ul>
 * <li>{@code -Djaer.prophesee.trace=true} — USB transfer FINER logs</li>
 * <li>{@code -Djaer.prophesee.trace.timestamps=true} — EVT3 timestamp FINE logs (2s throttle)</li>
 * </ul>
 * TODO(remove): delete this class once EVK4 live capture is verified stable.
 *
 * @see https://www.prophesee.ai/
 */
final class PropheseeTrace {

    static final boolean ENABLED = Boolean.getBoolean("jaer.prophesee.trace");
    static final boolean TIMESTAMP_ENABLED = Boolean.getBoolean("jaer.prophesee.trace.timestamps");

    private PropheseeTrace() {
    }

    static void fine(Logger log, String msg, Object... params) {
        if (ENABLED || TIMESTAMP_ENABLED) {
            log.log(Level.FINE, msg, params);
        }
    }

    static void finer(Logger log, String msg, Object... params) {
        if (ENABLED) {
            log.log(Level.FINER, msg, params);
        }
    }
}
