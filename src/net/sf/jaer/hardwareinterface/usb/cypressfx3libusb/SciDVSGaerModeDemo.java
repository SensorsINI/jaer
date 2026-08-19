package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.sf.jaer.chip.AEChip;

/**
 * Frozen headless acceptance vectors for the tri-state SciDVS GAER mode
 * resolver {@code SciDVSGaerMode}. The resolver is package-local, input is a
 * raw JVM property string plus a boolean describing whether the connected chip
 * is a SciDVS, and it returns the boolean that selects GAER. It must never take
 * an {@code AEChip}/{@code SciDVS} parameter, never construct a {@code SciDVS},
 * and fail closed to the chip boolean with a single WARNING on invalid input.
 *
 * <p>This test compiles today only because {@code SciDVSGaerMode} is absent;
 * after the resolver lands it must pass deterministically with no hardware and
 * no device-family-specific involvement.
 */
public final class SciDVSGaerModeDemo {

    private static int assertions;

    private SciDVSGaerModeDemo() {
    }

    public static void main(final String[] args) throws Exception {
        testResolveMatrix();
        testInvalidWarnsExactlyOnceAcrossTwoValues();
        testPropertyConstant();
        testResolveFromSystemProperty();
        testNoAEChipInteraction();
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
        // null true/false
        checkResolve(null, false, false, "null auto");
        checkResolve(null, true, true, "null auto");
        // empty
        checkResolve("", false, false, "empty auto");
        checkResolve("", true, true, "empty auto");
        // blank
        checkResolve("   ", false, false, "blank auto");
        checkResolve("   ", true, true, "blank auto");
        // auto
        checkResolve("auto", false, false, "auto lower");
        checkResolve("auto", true, true, "auto lower");
        checkResolve("AUTO", false, false, "auto upper");
        checkResolve(" aUtO ", true, true, "auto mixed trimmed");
        // true forced against chip bool
        checkResolve("TRUE", false, true, "true upper forced");
        checkResolve(" true ", true, true, "true lower forced");
        // false forced against chip bool
        checkResolve("FALSE", true, false, "false upper forced");
        checkResolve(" false ", false, false, "false lower forced");
        // invalid fails closed to auto (chip bool)
        checkResolve("banana", false, false, "invalid auto");
        checkResolve(" 87 ", true, true, "invalid auto");
    }

    /**
     * One injected {@code AtomicBoolean} gate shared across two invalid values,
     * with an anonymous {@code Logger} plus a counting {@code Handler}. The
     * resolver must log exactly one WARNING across the two calls (the shared
     * gate latches) and return auto (chip boolean) both times.
     */
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

    /**
     * {@code resolveFromSystemProperty} reads the raw JVM property and applies
     * the same semantics as {@code resolve}. When launched with
     * {@code -Daer.scidvs.gaer=false} it must read false.
     */
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

    /**
     * The resolver interface must stay boolean/String-shaped: no declared
     * method (or constructor or field) parameter/type may be assignable to
     * {@code AEChip}, and the resolver never constructs a {@code SciDVS}.
     */
    private static void testNoAEChipInteraction() throws Exception {
        final Class<?> mode = SciDVSGaerMode.class;

        final Method[] methods = mode.getDeclaredMethods();
        require(methods.length > 0, "SciDVSGaerMode declares at least one method");
        for (final Method method : methods) {
            for (final Class<?> parameter : method.getParameterTypes()) {
                require(!AEChip.class.isAssignableFrom(parameter),
                        "no declared method parameter is assignable to AEChip: "
                                + method.getName());
            }
        }

        final Constructor<?>[] constructors = mode.getDeclaredConstructors();
        for (final Constructor<?> constructor : constructors) {
            for (final Class<?> parameter : constructor.getParameterTypes()) {
                require(!AEChip.class.isAssignableFrom(parameter),
                        "no declared constructor parameter is assignable to AEChip");
            }
        }

        final Field[] fields = mode.getDeclaredFields();
        for (final Field field : fields) {
            require(!AEChip.class.isAssignableFrom(field.getType()),
                    "no declared field is assignable to AEChip");
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

    /** A silent logger dedicated to the supplied handler (no parent propagation). */
    private static Logger silent(final Handler handler) {
        final Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        return logger;
    }

    /** An anonymous counting handler that tallies WARNING records. */
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
