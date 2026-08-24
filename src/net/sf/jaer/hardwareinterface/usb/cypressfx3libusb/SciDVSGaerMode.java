package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Package-local tri-state resolver for the SciDVS GAER operating mode.
 *
 * <p>The operating mode is selected by a JVM system property
 * {@code -Daer.scidvs.gaer=&lt;value&gt;}. The raw property string is resolved
 * against a boolean describing whether the connected chip is a SciDVS. The
 * resolver never takes an {@code AEChip}/{@code SciDVS} parameter, never
 * constructs a {@code SciDVS}, and fails closed to the chip boolean with a
 * single WARNING on invalid input.
 *
 * <p>Resolution semantics:
 * <ul>
 * <li>{@code null}, blank, or trimmed case-insensitive {@code auto} &mdash;
 * return the chip boolean as-is.</li>
 * <li>trimmed case-insensitive {@code true}/{@code false} &mdash; force the
 * return value regardless of the chip boolean.</li>
 * <li>any other value &mdash; log exactly one WARNING (latched per
 * {@link AtomicBoolean} gate) and return the chip boolean.</li>
 * </ul>
 */
final class SciDVSGaerMode {

    /** JVM system property that selects the SciDVS GAER operating mode. */
    static final String PROPERTY = "aer.scidvs.gaer";

    /** Private static latch behind {@link #resolveFromSystemProperty}. */
    private static final AtomicBoolean WARNING_LATCH = new AtomicBoolean();

    private SciDVSGaerMode() {
    }

    /**
     * Resolve a raw property value to the boolean that selects GAER, using the
     * caller-supplied {@code warnLatch} to gate the single invalid-input
     * WARNING.
     *
     * @param rawValue      the raw JVM property string, possibly {@code null}.
     * @param chipIsSciDVS  whether the connected chip is a SciDVS.
     * @param log           the logger to warn on invalid input.
     * @param warnLatch     the gate shared across calls for invalid-input
     *                      warnings.
     * @return the boolean that selects GAER, per resolution semantics.
     */
    static boolean resolve(final String rawValue, final boolean chipIsSciDVS,
            final Logger log, final AtomicBoolean warnLatch) {
        if (rawValue == null) {
            return chipIsSciDVS;
        }
        final String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return chipIsSciDVS;
        }
        final String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("auto")) {
            return chipIsSciDVS;
        }
        if (lower.equals("true")) {
            return true;
        }
        if (lower.equals("false")) {
            return false;
        }
        if (warnLatch.compareAndSet(false, true)) {
            log.warning("Unrecognized value for " + PROPERTY + "=\"" + rawValue
                    + "\"; falling back to auto (chip is SciDVS=" + chipIsSciDVS + ")");
        }
        return chipIsSciDVS;
    }

    /**
     * Resolve the running JVM's {@value #PROPERTY} property using the same
     * semantics as {@link #resolve}, gated by a single private static latch.
     *
     * @param chipIsSciDVS whether the connected chip is a SciDVS.
     * @param log          the logger to warn on invalid input.
     * @return the boolean that selects GAER.
     */
    static boolean resolveFromSystemProperty(final boolean chipIsSciDVS,
            final Logger log) {
        return resolve(System.getProperty(PROPERTY), chipIsSciDVS, log, WARNING_LATCH);
    }
}
