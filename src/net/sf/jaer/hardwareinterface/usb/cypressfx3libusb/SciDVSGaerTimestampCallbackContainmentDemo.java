package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Hardware-free regression for containing a SciDVS timestamp-order failure at
 * the USB callback boundary. The production hooks are loaded by reflection so
 * the regression compiles before containment exists and fails at runtime for
 * the missing contract. No USB device is constructed or enumerated.
 */
public final class SciDVSGaerTimestampCallbackContainmentDemo {

    private static final Path DAVIS_SOURCE = Paths.get("src", "net", "sf",
            "jaer", "hardwareinterface", "usb", "cypressfx3libusb",
            "DAViSFX3HardwareInterface.java");
    private static final String LOG_MARKER
            = "SCIDVS_GAER_TIMESTAMP_CALLBACK_FAILURE";
    private static final int EARLIER_TIMESTAMP_WORD_INDEX = 2043;
    private static final int DECREASE_WORD_INDEX = 2048;
    private static int assertions;

    private SciDVSGaerTimestampCallbackContainmentDemo() {
    }

    public static void main(final String[] args) throws Exception {
        boolean javaAssertionsEnabled = false;
        assert javaAssertionsEnabled = true;
        require(javaAssertionsEnabled,
                "run this regression with Java assertions enabled (-ea)");

        final Contract contract = Contract.load();
        final Harness monitor = new Harness();
        final ByteBuffer rejectedTransfer = exactInversionFixture();
        final BufferSnapshot inputSnapshot = BufferSnapshot.capture(rejectedTransfer);
        final SciDVSGaerTimestampOrderGuard.ValidationException firstFailure
                = reject(new SciDVSGaerTimestampOrderGuard(), rejectedTransfer);

        require(firstFailure.getByteOffset() == 4096,
                "first callback failure retains byte offset 4096");
        require(Arrays.equals(firstFailure.getTransferSnapshot(), inputSnapshot.bytes),
                "first callback failure retains the complete transfer snapshot");
        final byte[] mutableSnapshot = firstFailure.getTransferSnapshot();
        mutableSnapshot[0] ^= 0x7f;
        require(Arrays.equals(firstFailure.getTransferSnapshot(), inputSnapshot.bytes),
                "retained transfer snapshot is defensive");
        inputSnapshot.requireUnchanged(rejectedTransfer,
                "guard-rejected callback transfer");

        final CountingHandler handler = new CountingHandler();
        CypressFX3.log.addHandler(handler);
        try {
            contract.retain(monitor, firstFailure);
            require(contract.current(monitor) == firstFailure,
                    "production retains the original first exception object");

            final ByteBuffer laterMalformed = ByteBuffer.allocate(1);
            laterMalformed.put((byte) 0x55).flip();
            final SciDVSGaerTimestampOrderGuard.ValidationException laterFailure
                    = reject(new SciDVSGaerTimestampOrderGuard(), laterMalformed);
            contract.retain(monitor, laterFailure);
            require(contract.current(monitor) == firstFailure,
                    "a later callback cannot replace the first failure");
            require(handler.markerRecords == 1,
                    "only the first callback failure is logged");
        } finally {
            CypressFX3.log.removeHandler(handler);
        }

        expectPollingFailure(monitor::acquireAvailableEventsFromDriver,
                firstFailure, "raw polling");
        expectPollingFailure(monitor::acquireAvailablePacketBundle,
                firstFailure, "typed polling");
        require(monitor.openCalls == 0,
                "latched polling failure is surfaced before any hardware open");
        require(monitor.closeCalls == 0,
                "callback containment never invokes close");

        final Thread cleanup = new Thread(monitor::close,
                "timestamp-callback-cleanup-regression");
        cleanup.start();
        cleanup.join(1_000L);
        require(!cleanup.isAlive(),
                "cleanup from a non-callback thread completes promptly");
        require(monitor.closeCalls == 1,
                "only the explicit cleanup thread invokes close");

        contract.clearAfterOwnedRestartAndReset(monitor);
        require(contract.current(monitor) == null,
                "owned restart/reset clearing releases the retained callback fault");

        testProductionWiring();
        System.out.println("SCIDVS_TIMESTAMP_CALLBACK_CONTAINMENT_ASSERTIONS="
                + assertions);
        System.out.println("SCIDVS_TIMESTAMP_CALLBACK_CONTAINMENT_PASS");
    }

    private static void testProductionWiring() throws Exception {
        final String source = Files.readString(DAVIS_SOURCE,
                StandardCharsets.UTF_8);
        final String translate = method(source,
                "protected void translateEvents(final ByteBuffer b)");
        final int recoveryPending = translate.indexOf(
                "if (gaerTimestampCallbackRecoveryPending");
        require(recoveryPending >= 0,
                "callback translation has an explicit recovery-pending gate");
        final String recovery = block(translate, recoveryPending);
        require(compact(recovery).equals(
                "if(gaerTimestampCallbackRecoveryPending"
                + "&&gaerTimestampCallbackFailure.get()!=null){"
                + "decodeGaerTimestampResetOnly(b);return;}"),
                "recovery plus retained failure invokes the reset-only helper and returns");
        require(!recovery.contains("gaerTimestampOrderGuard.validate("),
                "recovery callbacks bypass timestamp-order validation");
        require(!recovery.contains("aePacketRawPool.writeBuffer()")
                && !recovery.contains("packetBundlePool.writeBuffer()")
                && !recovery.contains("typedBuilder.attach(")
                && !recovery.contains("gaerRawSink.begin(")
                && !recovery.contains("gaerTypedSink")
                && !recovery.contains("typedBuilder.flushAll()")
                && !recovery.contains("typedOut.setRawPacket("),
                "reset-only recovery performs no raw or typed publication calls");
        require(!recovery.contains("stopAEReader(")
                && !recovery.contains("stopThread(")
                && !recovery.contains(".join(")
                && !recovery.contains("close("),
                "reset-only recovery performs no callback-thread cleanup");

        final int retainedFault = translate.indexOf(
                "if (gaerTimestampCallbackFailure.get() != null)",
                recoveryPending);
        require(retainedFault > recoveryPending,
                "callback translation has a separate retained-failure gate");
        final String retainedDiscard = block(translate, retainedFault);
        require(compact(retainedDiscard).equals(
                "if(gaerTimestampCallbackFailure.get()!=null){return;}"),
                "retained failure without recovery returns immediately");

        final String resetOnlyDecoder = method(source,
                "private void decodeGaerTimestampResetOnly(final ByteBuffer b)");
        require(compact(resetOnlyDecoder).equals(
                "privatevoiddecodeGaerTimestampResetOnly(finalByteBufferb){"
                + "if(quiescentDrain.isDraining()){"
                + "quiescentDrain.noteCompletedTransfer("
                + "completedTransferActualLength,"
                + "SciDVSGaerDecoder.containsSourcePayload(b));}"
                + "gaerDecoder.decode(b,gaerTimestampResetOnlySink);}"),
                "shared reset-only helper performs only conditional drain accounting and reset decoding");

        final int validate = translate.indexOf(
                "gaerTimestampOrderGuard.validate(b)");
        final int exactCatch = translate.indexOf(
                "catch (final SciDVSGaerTimestampOrderGuard.ValidationException failure)");
        final int retain = translate.indexOf(
                "retainGaerTimestampCallbackFailure(failure)", exactCatch);
        final int enableRecovery = translate.indexOf(
                "gaerTimestampCallbackRecoveryPending = true;", exactCatch);
        final int rejectedResetOnly = translate.indexOf(
                "decodeGaerTimestampResetOnly(b)", exactCatch);
        final int callbackReturn = translate.indexOf("return;", rejectedResetOnly);
        require(recoveryPending < retainedFault && retainedFault < validate
                && exactCatch > validate,
                "callback catches the exact timestamp validation exception");
        require(retain > exactCatch && enableRecovery > retain
                && rejectedResetOnly > enableRecovery
                && callbackReturn > rejectedResetOnly,
                "guard failure retains the fault, enables recovery, reset-decodes the rejected transfer, and returns");
        require(count(translate, "decodeGaerTimestampResetOnly(b)") == 2,
                "translation invokes one shared reset-only helper from recovery and rejection paths");

        final String[] mutations = {
            "aePacketRawPool.writeBuffer()",
            "packetBundlePool.writeBuffer()",
            "prepareAuthoritativeTypedBundle(typedOut)",
            "typedBuilder.attach(",
            "gaerRawSink.begin(",
            "gaerDecoder.decode(b, gaerTypedSink)",
            "gaerDecoder.decode(b, gaerRawSink)",
            "eventCounter = gaerRawSink.end()",
            "typedBuilder.flushAll()"
        };
        for (final String mutation : mutations) {
            final int at = translate.indexOf(mutation);
            require(at > callbackReturn,
                    "failing and later callbacks return before mutation: "
                    + mutation);
        }
        require(!translate.contains("stopAEReader(")
                && !translate.contains("stopThread(")
                && !translate.contains(".join(")
                && !translate.contains("close("),
                "callback translation performs no stop, join, or close");

        final String retainMethod = method(source,
                "private void retainGaerTimestampCallbackFailure(");
        require(retainMethod.contains("compareAndSet(null, failure)"),
                "first callback failure is retained atomically");
        require(retainMethod.contains(LOG_MARKER),
                "first callback failure uses the stable one-shot log marker");
        require(!retainMethod.contains("stopAEReader(")
                && !retainMethod.contains("stopThread(")
                && !retainMethod.contains(".join(")
                && !retainMethod.contains("close("),
                "fault retention performs no callback-thread cleanup");

        final String rawPoll = method(source,
                "public AEPacketRaw acquireAvailableEventsFromDriver()");
        final int rawPrecheck = rawPoll.indexOf(
                "throwIfGaerTimestampCallbackFailure()");
        final int rawSuper = rawPoll.indexOf(
                "super.acquireAvailableEventsFromDriver()");
        final int rawPostcheck = rawPoll.indexOf(
                "throwIfGaerTimestampCallbackFailure()", rawPrecheck + 1);
        require(rawPrecheck >= 0 && rawSuper > rawPrecheck
                && rawPostcheck > rawSuper,
                "raw polling checks the callback fault before and after acquisition");

        final String typedPoll = method(source,
                "public PacketBundle acquireAvailablePacketBundle()");
        final int typedPrecheck = typedPoll.indexOf(
                "throwIfGaerTimestampCallbackFailure()");
        final int typedSuper = typedPoll.indexOf(
                "super.acquireAvailablePacketBundle()");
        final int typedPostcheck = typedPoll.indexOf(
                "throwIfGaerTimestampCallbackFailure()", typedPrecheck + 1);
        require(typedPrecheck >= 0 && typedSuper > typedPrecheck
                && typedPostcheck > typedSuper,
                "typed polling checks the callback fault before and after acquisition");

        final String start = method(source, "public void startAEReader()");
        final int resetSequence = start.indexOf("final boolean resetObserved;");
        final int resetTry = start.indexOf("try {", resetSequence);
        final int arm = start.indexOf("reader.armStartupTimestampReset()");
        final int reset = start.indexOf("resetTimestamps()", arm);
        final int await = start.indexOf("reader.awaitStartupTimestampReset(");
        final int resetFinally = start.indexOf("finally", await);
        require(resetSequence >= 0 && resetTry > resetSequence
                && arm > resetTry && reset > arm && await > reset
                && resetFinally > await,
                "startup arm, reset, and await are enclosed by one try/finally");
        final String resetCleanup = block(start, resetFinally);
        require(compact(resetCleanup).equals(
                "finally{reader.disarmStartupTimestampReset();}"),
                "startup reset arming is always disarmed in finally");
        final int safeLock = start.indexOf("synchronized (aePacketRawPool)", await);
        require(safeLock > await,
                "post-barrier state transition uses the callback's packet-pool lock");
        final String barrierSuccess = block(start, safeLock);
        final int postBarrierAllocation = barrierSuccess.indexOf("allocateAEBuffers()");
        final int guardClear = barrierSuccess.indexOf(
                "reader.gaerTimestampOrderGuard.clearAfterOwnedRestartAndReset()");
        final int recoveryClear = barrierSuccess.indexOf(
                "reader.gaerTimestampCallbackRecoveryPending = false;");
        final int callbackClear = barrierSuccess.indexOf(
                "clearGaerTimestampCallbackFailureAfterOwnedRestartAndReset()");
        require(postBarrierAllocation >= 0 && guardClear > postBarrierAllocation
                && recoveryClear > guardClear && callbackClear > recoveryClear,
                "barrier success clears buffers, guard, recovery, then retained fault under one lock");
        require(count(source,
                "clearGaerTimestampCallbackFailureAfterOwnedRestartAndReset()") == 2,
                "callback fault clear has exactly one production call site");
        require(source.contains(
                "private volatile boolean gaerStartupTimestampResetArmed;"),
                "startup reset arming is reader-local and callback-visible");
        final String armMethod = method(source,
                "private void armStartupTimestampReset()");
        require(compact(armMethod).equals(
                "privatevoidarmStartupTimestampReset(){"
                + "gaerStartupTimestampResetArmed=true;}"),
                "startup reset arming is explicit");
        final String disarmMethod = method(source,
                "private void disarmStartupTimestampReset()");
        require(compact(disarmMethod).equals(
                "privatevoiddisarmStartupTimestampReset(){"
                + "gaerStartupTimestampResetArmed=false;}"),
                "startup reset disarming only clears the reader-local arm");
        final String resetHandler = method(source,
                "private void handleGaerTimestampReset()");
        final int armedCheck = resetHandler.indexOf(
                "if (gaerStartupTimestampResetArmed)");
        final int consumeArm = resetHandler.indexOf(
                "gaerStartupTimestampResetArmed = false;", armedCheck);
        final int satisfyBarrier = resetHandler.indexOf(
                "startupTimestampReset.markResetObserved()", armedCheck);
        require(armedCheck >= 0 && consumeArm > armedCheck
                && satisfyBarrier > consumeArm,
                "only an armed reset word consumes the arm and satisfies the barrier");
        require(!resetHandler.contains(
                "clearGaerTimestampCallbackFailureAfterOwnedRestartAndReset"),
                "a reset-like callback word cannot clear the retained failure");
    }

    private static SciDVSGaerTimestampOrderGuard.ValidationException reject(
            final SciDVSGaerTimestampOrderGuard guard,
            final ByteBuffer transfer) {
        try {
            guard.validate(transfer);
        } catch (final SciDVSGaerTimestampOrderGuard.ValidationException failure) {
            return failure;
        }
        throw new AssertionError("timestamp-order guard accepted rejection fixture");
    }

    private static ByteBuffer exactInversionFixture() {
        final int[] values = new int[DECREASE_WORD_INDEX + 2];
        Arrays.fill(values, 0x100A);
        values[0] = 0x0001;
        values[EARLIER_TIMESTAMP_WORD_INDEX] = 0x95AE;
        values[DECREASE_WORD_INDEX] = 0x944D;
        values[DECREASE_WORD_INDEX + 1] = 0x2001;
        final ByteBuffer buffer = ByteBuffer.allocate(
                values.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (final int value : values) {
            buffer.putShort((short) value);
        }
        return buffer.flip();
    }

    private static void expectPollingFailure(final Poll poll,
            final SciDVSGaerTimestampOrderGuard.ValidationException expected,
            final String description) throws Exception {
        try {
            poll.acquire();
        } catch (final HardwareInterfaceException failure) {
            require(failure.getCause() == expected,
                    description + " preserves the original callback failure as cause");
            require(failure.getMessage().contains("byte offset 4096"),
                    description + " reports the original byte offset");
            return;
        }
        throw new AssertionError(description
                + " did not surface the retained callback failure");
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

    private static String block(final String source, final int statementStart) {
        final int openingBrace = source.indexOf("{", statementStart);
        if (statementStart < 0 || openingBrace < statementStart) {
            throw new AssertionError("missing source block at " + statementStart);
        }
        int depth = 0;
        for (int i = openingBrace; i < source.length(); i++) {
            final char item = source.charAt(i);
            if (item == '{') {
                depth++;
            } else if (item == '}' && --depth == 0) {
                return source.substring(statementStart, i + 1);
            }
        }
        throw new AssertionError("unterminated source block at " + statementStart);
    }

    private static String compact(final String value) {
        return value.replaceAll("\\s+", "");
    }

    private static int count(final String value, final String needle) {
        int result = 0;
        int at = 0;
        while ((at = value.indexOf(needle, at)) >= 0) {
            result++;
            at += needle.length();
        }
        return result;
    }

    private static void require(final boolean condition,
            final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    @FunctionalInterface
    private interface Poll {
        Object acquire() throws HardwareInterfaceException;
    }

    private static final class CountingHandler extends Handler {
        int markerRecords;

        CountingHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(final LogRecord record) {
            if (record != null && record.getMessage() != null
                    && record.getMessage().contains(LOG_MARKER)) {
                markerRecords++;
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
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
            final ByteBuffer copy = buffer.duplicate();
            copy.clear();
            final byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            return new BufferSnapshot(bytes, buffer.position(), buffer.limit(),
                    buffer.order());
        }

        void requireUnchanged(final ByteBuffer buffer,
                final String description) {
            require(buffer.position() == position,
                    description + " preserves position");
            require(buffer.limit() == limit,
                    description + " preserves limit");
            require(buffer.order() == order,
                    description + " preserves byte order");
            final ByteBuffer copy = buffer.duplicate();
            copy.clear();
            final byte[] current = new byte[copy.remaining()];
            copy.get(current);
            require(Arrays.equals(bytes, current),
                    description + " preserves bytes");
        }
    }

    private static final class Contract {
        final Field retainedFailure;
        final Method retain;
        final Method clear;

        private Contract(final Field retainedFailure, final Method retain,
                final Method clear) {
            this.retainedFailure = retainedFailure;
            this.retain = retain;
            this.clear = clear;
        }

        static Contract load() throws Exception {
            try {
                final Field field = DAViSFX3HardwareInterface.class
                        .getDeclaredField("gaerTimestampCallbackFailure");
                final Method retain = DAViSFX3HardwareInterface.class
                        .getDeclaredMethod("retainGaerTimestampCallbackFailure",
                                SciDVSGaerTimestampOrderGuard.ValidationException.class);
                final Method clear = DAViSFX3HardwareInterface.class
                        .getDeclaredMethod(
                                "clearGaerTimestampCallbackFailureAfterOwnedRestartAndReset");
                field.setAccessible(true);
                retain.setAccessible(true);
                clear.setAccessible(true);
                require(field.getType() == AtomicReference.class,
                        "retained callback failure uses AtomicReference");
                require(Modifier.isFinal(field.getModifiers()),
                        "retained callback failure has one final state owner");
                require(retain.getReturnType() == void.class,
                        "first-failure retention returns void");
                require(clear.getReturnType() == void.class,
                        "owned-reset callback clear returns void");
                return new Contract(field, retain, clear);
            } catch (final NoSuchFieldException | NoSuchMethodException missing) {
                throw new AssertionError(
                        "INTENDED RED: missing SciDVS callback-containment contract",
                        missing);
            }
        }

        void retain(final DAViSFX3HardwareInterface monitor,
                final SciDVSGaerTimestampOrderGuard.ValidationException failure)
                throws Exception {
            invoke(retain, monitor, failure);
        }

        SciDVSGaerTimestampOrderGuard.ValidationException current(
                final DAViSFX3HardwareInterface monitor) throws Exception {
            @SuppressWarnings("unchecked")
            final AtomicReference<SciDVSGaerTimestampOrderGuard.ValidationException>
                    reference = (AtomicReference<SciDVSGaerTimestampOrderGuard.ValidationException>)
                            retainedFailure.get(monitor);
            return reference.get();
        }

        void clearAfterOwnedRestartAndReset(
                final DAViSFX3HardwareInterface monitor) throws Exception {
            invoke(clear, monitor);
        }

        private static Object invoke(final Method method, final Object target,
                final Object... arguments) throws Exception {
            try {
                return method.invoke(target, arguments);
            } catch (final InvocationTargetException failure) {
                final Throwable cause = failure.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw failure;
            }
        }
    }

    private static final class Harness extends DAViSFX3HardwareInterface {
        int openCalls;
        int closeCalls;

        Harness() {
            super(null);
        }

        @Override
        synchronized public void open() {
            openCalls++;
            throw new AssertionError("polling attempted a hardware open");
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }
}
