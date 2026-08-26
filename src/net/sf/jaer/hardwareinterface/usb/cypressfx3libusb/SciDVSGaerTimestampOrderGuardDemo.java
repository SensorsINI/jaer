package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.lang.IllegalAccessException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Frozen, hardware-free RED contract for the SciDVS GAER timestamp-order
 * guard. The production guard is loaded only by reflection so this demo
 * compiles before the guard exists. It reads source files only, constructs
 * no USB device, and performs no hardware enumeration.
 */
public final class SciDVSGaerTimestampOrderGuardDemo {

    private static final String GUARD_CLASS_NAME =
            "net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.SciDVSGaerTimestampOrderGuard";
    private static final Path DECODER_DEMO_SOURCE = Paths.get("src", "net",
            "sf", "jaer", "hardwareinterface", "usb", "cypressfx3libusb",
            "SciDVSGaerDecoderDemo.java");
    private static final Path DAVIS_SOURCE = Paths.get("src", "net", "sf",
            "jaer", "hardwareinterface", "usb", "cypressfx3libusb",
            "DAViSFX3HardwareInterface.java");
    private static final int EARLIER_TIMESTAMP_WORD_INDEX = 2043;
    private static final int DECREASE_WORD_INDEX = 2048;
    private static final int DECREASE_BYTE_OFFSET
            = DECREASE_WORD_INDEX * Short.BYTES;
    private static int assertions;

    private SciDVSGaerTimestampOrderGuardDemo() {
    }

    public static void main(final String[] args) throws Exception {
        boolean javaAssertionsEnabled = false;
        assert javaAssertionsEnabled = true;
        require(javaAssertionsEnabled,
                "run this frozen RED demo with Java assertions enabled (-ea)");

        testDecoderDemoFixtureContract();
        System.out.println("SCIDVS_TIMESTAMP_ORDER_GUARD_FIXTURE_SELF_CHECK_PASS assertions="
                + assertions);

        final GuardContract contract = GuardContract.load();
        testIncreasingTimestamps(contract);
        testEqualTimestamps(contract);
        testValidWrap(contract);
        testPositiveResetLowerTimestamp(contract);
        testExactIntraTransferDecrease(contract);
        testCrossTransferDecrease(contract);
        testOddAndMalformedFailClosed(contract);
        testInputIsUnchanged(contract);
        testTransactionalState(contract);
        testLatchedFaultAndOwnedRestart(contract);
        testProductionWiring(contract);

        System.out.println("SCIDVS_TIMESTAMP_ORDER_GUARD_ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_TIMESTAMP_ORDER_GUARD_PASS");
    }

    private static void testDecoderDemoFixtureContract() throws Exception {
        final String source = Files.readString(DECODER_DEMO_SOURCE,
                StandardCharsets.UTF_8);
        require(source.contains(
                "ByteBuffer.allocate(words.length * Short.BYTES)"),
                "fixtures reuse the decoder demo's exact two-byte word width");
        require(source.contains(".order(ByteOrder.LITTLE_ENDIAN)"),
                "fixtures reuse the decoder demo's exact little-endian order");
        require(source.contains(
                "words(0x8064, 0x100A, 0x2001, 0x7003, 0x2010)"),
                "decoder demo source provides timestamp, wrap, and payload words");
        require(source.contains("0x0001, 0x87D0")
                && source.contains("0x876C, 0x2002"),
                "decoder demo source defines positive reset and strict decrease semantics");

        final Fixture fixture = exactInversionFixture();
        require(fixture.buffer.order() == ByteOrder.LITTLE_ENDIAN,
                "exact fixture is little-endian");
        require(fixture.buffer.position() == 0
                && fixture.buffer.limit() == (DECREASE_WORD_INDEX + 2) * Short.BYTES,
                "exact fixture has expected complete-word bounds");
        require(wordAt(fixture.buffer, EARLIER_TIMESTAMP_WORD_INDEX) == 0x95AE,
                "exact fixture has 0x95AE at word 2043");
        require(wordAt(fixture.buffer, DECREASE_WORD_INDEX) == 0x944D,
                "exact fixture has 0x944D at word 2048");
        require(DECREASE_BYTE_OFFSET == 4096,
                "word 2048 starts at byte offset 4096");

        final BufferSnapshot snapshot = BufferSnapshot.capture(fixture.buffer);
        final SciDVSGaerDecoder decoder = new SciDVSGaerDecoder(defaultConfig());
        final CountingSink sink = new CountingSink();
        decoder.decode(fixture.buffer.duplicate()
                .order(ByteOrder.LITTLE_ENDIAN), sink);
        require(decoder.getNonMonotonicTimestampCount() == 1L,
                "existing decoder confirms exactly one strict fixture decrease");
        require(decoder.getMaxBackwardTimestampUs() == 353,
                "existing decoder confirms the 0x95AE to 0x944D decrease is 353");
        require(sink.events == 1,
                "the decoder would emit the trailing fixture payload if unguarded");
        snapshot.requireUnchanged(fixture.buffer, "fixture self-check");
    }

    private static void testIncreasingTimestamps(final GuardContract contract)
            throws Exception {
        final Object guard = contract.newGuard();
        contract.validate(guard, words(0x0001, 0x8001, 0x8002, 0x8003));
        require(!contract.isFaultLatched(guard),
                "increasing timestamps pass without latching a fault");
    }

    private static void testEqualTimestamps(final GuardContract contract)
            throws Exception {
        final Object guard = contract.newGuard();
        contract.validate(guard, words(0x0001, 0x8064, 0x8064));
        require(!contract.isFaultLatched(guard),
                "equal timestamps are valid");
    }

    private static void testValidWrap(final GuardContract contract)
            throws Exception {
        final Object guard = contract.newGuard();
        contract.validate(guard, words(0x0001, 0xFFFF, 0x7001, 0x8000));
        require(contract.getWrapAdd(guard) == 0x8000L,
                "valid wrap commits exactly 2e*15 to the wrap addend");
    }

    private static void testPositiveResetLowerTimestamp(
            final GuardContract contract) throws Exception {
        final Object guard = contract.newGuard();
        contract.validate(guard, words(0x95AE, 0x0001, 0x8001));
        require(contract.getEpoch(guard) > 0,
                "a positive reset starts a new epoch and permits a lower timestamp");
    }

    private static void testExactIntraTransferDecrease(
            final GuardContract contract) throws Exception {
        final Object guard = contract.newGuard();
        final Fixture fixture = exactInversionFixture();
        final BufferSnapshot snapshot = BufferSnapshot.capture(fixture.buffer);
        final CountingSink sink = new CountingSink();

        final GuardFailure failure;
        try {
            contract.invokeValidate(guard, fixture.buffer);
            new SciDVSGaerDecoder(defaultConfig())
                    .decode(fixture.buffer, sink);
            throw new AssertionError("exact intra-transfer decrease was accepted");
        } catch (final InvocationTargetException rejected) {
            failure = contract.asGuardFailure(rejected.getCause(),
                    "exact intra-transfer decrease");
        }

        require(failure.byteOffset == DECREASE_BYTE_OFFSET,
                "exact decrease is rejected at byte offset 4096");
        require(sink.events == 0,
                "exact rejected transfer emits and publishes zero events");
        require(contract.isFaultLatched(guard),
                "exact decrease latches the guard fault");
        snapshot.requireUnchanged(fixture.buffer,
                "exact rejected transfer");
    }

    private static void testCrossTransferDecrease(
            final GuardContract contract) throws Exception {
        final Object guard = contract.newGuard();
        contract.validate(guard, words(0x0001, 0x95AE));
        final GuardFailure failure = contract.expectRejected(
                guard, words(0x944D), "cross-transfer decrease");
        require(failure.byteOffset == 0,
                "cross-transfer decrease is reported at the second transfer's first byte");
    }

    private static void testOddAndMalformedFailClosed(
            final GuardContract contract) throws Exception {
        final Object oddGuard = contract.newGuard();
        final ByteBuffer odd = ByteBuffer.allocate(3)
                .order(ByteOrder.LITTLE_ENDIAN);
        odd.putShort((short) 0x8001).put((byte) 0x55).flip();
        final BufferSnapshot oddSnapshot = BufferSnapshot.capture(odd);
        final GuardFailure oddFailure = contract.expectRejected(
                oddGuard, odd, "odd-length input");
        require(oddFailure.byteOffset >= 0,
                "odd-length input reports a diagnostic byte offset");
        require(contract.isFaultLatched(oddGuard),
                "odd-length input latches the fault");
        oddSnapshot.requireUnchanged(odd, "odd-length rejection");

        final Object malformedGuard = contract.newGuard();
        final GuardFailure malformedFailure = contract.expectRejected(
                malformedGuard, null, "null malformed input");
        require(malformedFailure.byteOffset == -1,
                "malformed null input has no fictitious byte offset");
        require(contract.isFaultLatched(malformedGuard),
                "malformed null input fails closed and latches the fault");
    }

    private static void testInputIsUnchanged(final GuardContract contract)
            throws Exception {
        final Object guard = contract.newGuard();
        final ByteBuffer input = words(0x0001, 0x8064, 0x8064, 0x8065);
        input.order(ByteOrder.BIG_ENDIAN);
        final BufferSnapshot snapshot = BufferSnapshot.capture(input);
        contract.validate(guard, input);
        snapshot.requireUnchanged(input, "valid input");
    }

    private static void testTransactionalState(final GuardContract contract)
            throws Exception {
        final Object guard = contract.newGuard();
        contract.validate(guard, words(0x0001, 0x8064));
        final long lastTimestamp = contract.getLastTimestamp(guard);
        final long wrapAdd = contract.getWrapAdd(guard);
        final long epoch = contract.getEpoch(guard);

        final ByteBuffer rejected = words(
                0x7001, 0xFFFF, 0x0001, 0x8064, 0x8032);
        final BufferSnapshot snapshot = BufferSnapshot.capture(rejected);
        final GuardFailure failure = contract.expectRejected(
                guard, rejected, "transactional rejection");
        require(failure.byteOffset == 8,
                "transactional fixture decrease is at byte offset 8");
        require(contract.getLastTimestamp(guard) == lastTimestamp,
                "rejection commits no timestamp state");
        require(contract.getWrapAdd(guard) == wrapAdd,
                "rejection commits no wrap state");
        require(contract.getEpoch(guard) == epoch,
                "rejection commits no epoch state");
        snapshot.requireUnchanged(rejected, "transactional rejection");
    }

    private static void testLatchedFaultAndOwnedRestart(
            final GuardContract contract) throws Exception {
        final Object guard = contract.newGuard();
        contract.validate(guard, words(0x0001, 0x95AE));
        contract.expectRejected(guard, words(0x944D),
                "latched-fault precondition");
        require(contract.isFaultLatched(guard),
                "rejection latches the fault");
        final long lastTimestamp = contract.getLastTimestamp(guard);
        contract.expectRejected(guard, words(0x0001, 0x8001),
                "latched guard refuses a reset-like later transfer");
        require(contract.getLastTimestamp(guard) == lastTimestamp,
                "latched refusal commits no state");

        contract.clearAfterOwnedRestartAndReset(guard);
        require(!contract.isFaultLatched(guard),
                "explicit owned restart/reset clears the latched fault");
        contract.validate(guard, words(0x8001));
    }

    private static void testProductionWiring(final GuardContract contract)
            throws Exception {
        final Field guardField = DAViSFX3HardwareInterface.RetinaAEReader.class
                .getDeclaredField("gaerTimestampOrderGuard");
        require(guardField.getType() == contract.type,
                "reader owns the exact timestamp-order guard type");
        require(Modifier.isFinal(guardField.getModifiers()),
                "reader timestamp-order guard is a final state owner");

        final String source = Files.readString(DAVIS_SOURCE,
                StandardCharsets.UTF_8);
        final String translate = method(source,
                "protected void translateEvents(final ByteBuffer b)");
        final int validate = translate.indexOf(
                "gaerTimestampOrderGuard.validate(b)");
        require(validate >= 0,
                "GAER production path invokes timestamp-order validation");

        final String[] mutations = {
            "prepareAuthoritativeTypedBundle(typedOut)",
            "typedBuilder.attach(",
            "gaerRawSink.begin(",
            "gaerDecoder.decode(",
            "eventCounter = gaerRawSink.end()",
            "typedBuilder.flushAll()"
        };
        for (final String mutation : mutations) {
            final int at = translate.indexOf(mutation);
          require(at > validate,
                    "timestamp-order validation precedes production mutation: "
                    + mutation);
        }

        final String start = method(source, "public void startAEReader()");
        final int await = start.indexOf("reader.awaitStartupTimestampReset(");
        final int clear = start.indexOf(
                "reader.gaerTimestampOrderGuard.clearAfterOwnedRestartAndReset()");
        require(await >= 0 && clear > await,
                "only the positively owned startup reset barrier clears the guard");

        final String handler = method(source,
                "private void handleGaerTimestampReset()");
        require(!handler.contains("clearAfterOwnedRestartAndReset"),
                "a reset-like word alone cannot clear the latched guard");
    }

    private static Fixture exactInversionFixture() throws Exception {
        final int[] values = new int[DECREASE_WORD_INDEX + 2];
        Arrays.fill(values, 0x100A);
        values[0] = 0x0001;
        values[EARLIER_TIMESTAMP_WORD_INDEX] = 0x95AE;
        values[DECREASE_WORD_INDEX] = 0x944D;
        values[DECREASE_WORD_INDEX + 1] = 0x2001;
        return new Fixture(words(values));
    }

    private static ByteBuffer words(final int... values) throws Exception {
        final Method words = SciDVSGaerDecoderDemo.class
                .getDeclaredMethod("words", int[].class);
        words.setAccessible(true);
        return (ByteBuffer) words.invoke(null, (Object) values);
    }

    private static SciDVSGaerDecoder.Config defaultConfig() throws Exception {
        final Method defaultConfig = SciDVSGaerDecoderDemo.class
                .getDeclaredMethod("defaultConfig");
        defaultConfig.setAccessible(true);
        return (SciDVSGaerDecoder.Config) defaultConfig.invoke(null);
    }

    private static int wordAt(final ByteBuffer buffer, final int index) {
        return buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                .getShort(index * Short.BYTES) & 0xFFFF;
    }

    private static String method(final String source, final String signature) {
        final int start = source.indexOf(signature);
        require(start >= 0, "source method exists: " + signature);
        final int openingBrace = source.indexOf("{", start);
        require(openingBrace > start, "source method opens: " + signature);
        int depth = 0;
        for (int i = openingBrace; i < source.length(); i++) {
            final char item = source.charAt(i);
            if (item == '{') {
                depth++;
            } else if (item == '}' && --depth == 0) {
                return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("unterminated source method: " + signature);
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static final class Fixture {
        final ByteBuffer buffer;

        Fixture(final ByteBuffer buffer) {
            this.buffer = buffer;
        }
    }

    private static final class CountingSink implements SciDVSGaerSink {
        int events;

        @Override
        public void onPolarity(final int packedAddress, final int x, final int y,
                final boolean on, final int timestamp) {
            events++;
        }
    }

    private static final class BufferSnapshot {
        final byte[] bytes;
        final int position;
        final int limit;
        final ByteOrder order;

        private BufferSnapshot(final byte[] bytes, final int position,
                final int limit, final ByteOrder order) {
            this.bytes = bytes;
            this.position = position;
            this.limit = limit;
            this.order = order;
        }

        static BufferSnapshot capture(final ByteBuffer buffer) {
            final byte[] bytes = new byte[buffer.capacity()];
          final ByteBuffer all = buffer.duplicate();
            all.clear();
            all.get(bytes);
            return new BufferSnapshot(bytes, buffer.position(),
                    buffer.limit(), buffer.order());
        }

        void requireUnchanged(final ByteBuffer buffer, final String description) {
            require(buffer.position() == position,
                    description + " preserves position");
            require(buffer.limit() == limit,
                    description + " preserves limit");
            require(buffer.order() == order,
                    description + " preserves byte order");
            final byte[] current = new byte[buffer.capacity()];
            final ByteBuffer all = buffer.duplicate();
            all.clear();
            all.get(current);
            require(Arrays.equals(bytes, current),
                    description + " preserves all bytes");
        }
    }

    private static final class GuardFailure {
        final int byteOffset;

        GuardFailure(final int byteOffset) {
            this.byteOffset = byteOffset;
        }
    }

    private static final class GuardContract {
        final Class<?> type;
        final Constructor<?> constructor;
        final Method validate;
        final Method isFaultLatched;
        final Method clearAfterOwnedRestartAndReset;
        final Method getLastTimestamp;
        final Method getWrapAdd;
        final Method getEpoch;
        final Class<?> validationExceptionType;
        final Method getByteOffset;

        private GuardContract(final Class<?> type, final Constructor<?> constructor,
                final Method validate, final Method isFaultLatched,
                final Method clearAfterOwnedRestartAndReset,
                final Method getLastTimestamp, final Method getWrapAdd,
                final Method getEpoch, final Class<?> validationExceptionType,
                final Method getByteOffset) {
            this.type = type;
            this.constructor = constructor;
            this.validate = validate;
            this.isFaultLatched = isFaultLatched;
            this.clearAfterOwnedRestartAndReset = clearAfterOwnedRestartAndReset;
            this.getLastTimestamp = getLastTimestamp;
            this.getWrapAdd = getWrapAdd;
            this.getEpoch = getEpoch;
            this.validationExceptionType = validationExceptionType;
            this.getByteOffset = getByteOffset;
        }

        static GuardContract load() throws Exception {
          final Class<?> type;
            try {
                type = Class.forName(GUARD_CLASS_NAME);
            } catch (final ClassNotFoundException missing) {
                throw new AssertionError(
                        "INTENDED RED: missing production guard contract "
                        + GUARD_CLASS_NAME, missing);
            }
          require(Modifier.isFinal(type.getModifiers()),
                    "timestamp-order guard class is final");

          final Constructor<?> constructor = type.getDeclaredConstructor();
          final Method validate = type.getDeclaredMethod("validate", ByteBuffer.class);
          final Method isFaultLatched = type.getDeclaredMethod("isFaultLatched");
          final Method clear = type.getDeclaredMethod(
                  "clearAfterOwnedRestartAndReset");
          final Method getLast = type.getDeclaredMethod("getLastTimestamp");
          final Method getWrap = type.getDeclaredMethod("getWrapAdd");
          final Method getEpoch = type.getDeclaredMethod("getEpoch");
          final Class<?> validationException = Class.forName(
                GUARD_CLASS_NAME + "$ValidationException");
          final Method getOffset = validationException.getDeclaredMethod(
                "getByteOffset");

          constructor.setAccessible(true);
          validate.setAccessible(true);
          isFaultLatched.setAccessible(true);
          clear.setAccessible(true);
          getLast.setAccessible(true);
          getWrap.setAccessible(true);
          getEpoch.setAccessible(true);
          getOffset.setAccessible(true);

          require(validate.getReturnType() == void.class,
                    "validate(ByteBuffer) returns void");
          require(isFaultLatched.getReturnType() == boolean.class,
                    "isFaultLatched() returns boolean");
          require(clear.getReturnType() == void.class,
                    "clearAfterOwnedRestartAndReset() returns void");
          require(getLast.getReturnType() == long.class
                    && getWrap.getReturnType() == long.class
                    && getEpoch.getReturnType() == long.class,
                    "state diagnostic getters return long");
          require(RuntimeException.class.isAssignableFrom(validationException),
                    "ValidationException is unchecked");
          require(getOffset.getReturnType() == int.class,
                    "ValidationException.getByteOffset() returns int");

          return new GuardContract(type, constructor, validate,
                isFaultLatched, clear, getLast, getWrap, getEpoch,
                validationException, getOffset);
        }

        Object newGuard() throws Exception {
            return constructor.newInstance();
        }

        void validate(final Object guard, final ByteBuffer input) throws Exception {
          try {
                invokeValidate(guard, input);
          } catch (final InvocationTargetException rejected) {
                final Throwable cause = rejected.getCause();
                throw new AssertionError("expected guard acceptance but got "
                        + cause.getClass().getName() + ": " + cause.getMessage(), cause);
          }
        }

        void invokeValidate(final Object guard, final ByteBuffer input)
                throws IllegalAccessException, InvocationTargetException {
            validate.invoke(guard, new Object[]{input});
        }

        GuardFailure expectRejected(final Object guard,
                final ByteBuffer input, final String description) throws Exception {
            try {
                invokeValidate(guard, input);
            } catch (final InvocationTargetException rejected) {
                return asGuardFailure(rejected.getCause(), description);
            }
            throw new AssertionError(description + " was accepted");
        }

        GuardFailure asGuardFailure(final Throwable cause,
                final String description) throws Exception {
            require(validationExceptionType.isInstance(cause),
                    description + " throws the exact ValidationException type");
            return new GuardFailure(((Number) getByteOffset.invoke(cause)).intValue());
        }

        boolean isFaultLatched(final Object guard) throws Exception {
            return (Boolean) isFaultLatched.invoke(guard);
        }

        void clearAfterOwnedRestartAndReset(final Object guard)
                throws Exception {
            clearAfterOwnedRestartAndReset.invoke(guard);
        }

        long getLastTimestamp(final Object guard) throws Exception {
            return ((Number) getLastTimestamp.invoke(guard)).longValue();
        }

        long getWrapAdd(final Object guard) throws Exception {
            return ((Number) getWrapAdd.invoke(guard)).longValue();
        }

        long getEpoch(final Object guard) throws Exception {
            return ((Number) getEpoch.invoke(guard)).longValue();
        }
    }
}
