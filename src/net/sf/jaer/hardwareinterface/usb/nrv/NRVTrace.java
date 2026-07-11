package net.sf.jaer.hardwareinterface.usb.nrv;

/**
 * Optional NRV diagnostics (off by default for acquisition performance).
 * <ul>
 * <li>{@code -Djaer.nrv.trace.timestampOrder=true} — log first non-monotonic timestamp per USB chunk</li>
 * </ul>
 *
 * @see https://nrv.kr/
 */
final class NRVTrace {

    static final boolean TIMESTAMP_ORDER_ENABLED = Boolean.getBoolean("jaer.nrv.trace.timestampOrder");

    private NRVTrace() {
    }
}
