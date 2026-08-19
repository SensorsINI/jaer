package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Frozen headless acceptance vectors for the tri-state SciDVS GAER mode
 * resolver {@code SciDVSGaerMode}. The resolver is package-local, input is a
 * raw JVM property string plus a boolean describing whether the connected chip
 * is a SciDVS, and it returns the boolean that selects GAER. It must never take
 * an {@code AEChip}/{@code SciDVS} parameter, never construct a {@code SciDVS},
 * and fail closed to the chip boolean with a single WARNING on invalid input.
 *
 * <p>The terminal regression commit adds reflection coverage for the pure
 * interface. This prefix is paired with a verbose class-load probe proving that
 * the resolver does not load or construct SciDVS.
 */
public final class SciDVSGaerModeDemo {

    private static int assertions;

    private SciDVSGaerModeDemo() {
    }

    public static void main(final String[] args) {
        testResolveMatrix();
        testInvalidWarnsExactlyOnceAcrossTwoValues();
        testPropertyConstant();
        testResolveFromSystemProperty();
        System.out.println("SCIDVS_GAER_MODE ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_GAER_MODE PASS");
    }

    /**
     * Sixteen-case cross product of input-value category and the boolean chip
     * flag. Null/empty/blank/auto return the chip boolean; trimmed
     * case-insensitive true/false force; invalid fails closed to the chip
     * boolean (auto).
     */
    private static void testResolveMatrix() {
        checkResolve(null, false, false, "null auto");
        checkResolve(null, true, true, "null auto");
        checkResolve("", false, false, "empty auto");
        checkResolve("", true, true, "empty auto");
        checkResolve("   ", false, false, "blank auto");
        checkResolve("   ", true, true, "blank auto");
        checkResolve("auto", false, false, "auto lower");
        checkResolve("auto", true, true, "auto lower");
        checkResolve("AUTO", false, false, "auto upper");
        checkResolve(" aUtO ", true, true, "auto mixed trimmed");
        checkResolve("TRUE", false, true, "true upper forced");
        checkResolve(" true ", true, true, "true lower forced");
        checkResolve("FALSE", true, false, "false upper forced");
        checkResolve(" false ", false, false, "false lower forced");
        checkResolve("banana", false, false, "invalid auto");
        checkResolve(" 87 ", true, true, "invalid auto");
    }

    /** One shared latch logs exactly one WARNING across two invalid values. */
    private static void testInvalidWarnsExactlyOnceAcrossTwoValues() {
        final int[] warnings = {0};
        final Handler counting = countingHandler(warnings);
        final AtomicBoolean gate = new AtomicBoolean();

        final boolean first = SciDVSGaerMode.resolve("nonsense-one", true, silent(counting), gate);
        require(first, "invalid first value returns auto chip boolean");
        require(gate.get(), "invalid first value latches the shared warning gate");

        final boolean second = SciDVSGaerMode.resolve("nonsense-two", true, silent(counting), gate);
        require(second, "invalid second value returns auto chip boolean even when gate latched");
        require(warnings[0] == 1,
                "exactly one WARNING across two invalid values sharing one AtomicBoolean gate, saw "
                        + warnings[0]);
        require(gate.get(), "shared warning gate stays latched after second invalid value");
    }

    private static void testPropertyConstant() {
        require("aer.scidvs.gaer".equals(SciDVSGaerMode.PROPERTY),
                "PROPERTY equals aer.scidvs.gaer");
    }

    /** Resolve the running JVM property with the same semantics as direct input. */
    private static void testResolveFromSystemProperty() {
        final String property = System.getProperty(SciDVSGaerMode.PROPERTY);
        final boolean chipIsSciDVS = true;

        final boolean viaProperty = SciDVSGaerMode.resolveFromSystemProperty(
                chipIsSciDVS, silent(countingHandler(new int[1])));
        final boolean viaResolve = SciDVSGaerMode.resolve(
                property, chipIsSciDVS, silent(countingHandler(new int[1])), new AtomicBoolean());
        require(viaProperty == viaResolve,
                "resolveFromSystemProperty applies resolve semantics to the raw property value");

        if (property != null && "false".equalsIgnoreCase(property.trim())) {
            require(!viaProperty,
                    "with -Daer.scidvs.gaer=false, resolveFromSystemProperty reads false");
        }
    }

    private static void checkResolve(final String value, final boolean chipIsSciDVS,
            final boolean expected, final String description) {
        final int[] warnings = {0};
        final boolean actual = SciDVSGaerMode.resolve(
                value, chipIsSciDVS, silent(countingHandler(warnings)), new AtomicBoolean());
        require(actual == expected, description + " resolves to " + expected);
        require(warnings[0] == (isRecognized(value) ? 0 : 1),
                description + " warning count " + warnings[0]);
    }

    private static boolean isRecognized(final String value) {
        if (value == null) {
            return true;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty()
                || trimmed.equalsIgnoreCase("auto")
                || trimmed.equalsIgnoreCase("true")
                || trimmed.equalsIgnoreCase("false");
    }

    private static Logger silent(final Handler handler) {
        final Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        return logger;
    }

    private static Handler countingHandler(final int[] warnings) {
        return new Handler() {
            @Override
            public void publish(final LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    warnings[0]++;
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
