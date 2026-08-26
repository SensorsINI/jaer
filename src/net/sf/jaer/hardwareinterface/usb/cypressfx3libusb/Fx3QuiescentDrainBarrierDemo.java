package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.TimeUnit;

/** Hardware-free deterministic acceptance checks for the FX3 payload drain barrier. */
public final class Fx3QuiescentDrainBarrierDemo {

    private static final long QUIET_MILLIS = 5L;
    private static final long TIMEOUT_MILLIS = 20L;
    private static int assertions;

    private Fx3QuiescentDrainBarrierDemo() {
    }

    public static void main(final String[] args) throws Exception {
        testContractShape();
        testNoActivityReachesQuietSuccess();
        testPayloadExtendsQuietDeadline();
        testMetadataDoesNotExtendQuietDeadline();
        testContinuingPayloadTimesOut();
        testZeroLengthDoesNotExtendPayloadQuiet();
        testNegativeLengthRejected();
        testCompletedTransferTimeline();
        testEndDrainIsolatesFreshDrain();
        System.out.println("FX3_QUIESCENT_DRAIN_BARRIER ASSERTIONS=" + assertions);
        System.out.println("FX3_QUIESCENT_DRAIN_BARRIER PASS");
    }

    private static void testContractShape() throws Exception {
        final Class<?> type = Fx3QuiescentDrainBarrier.class;
        final int typeModifiers = type.getModifiers();
        require(!Modifier.isPublic(typeModifiers)
                && !Modifier.isProtected(typeModifiers)
                && !Modifier.isPrivate(typeModifiers),
                "drain barrier class is package-local");

        Constructor<?> deterministic = null;
        for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 2) {
                deterministic = constructor;
            }
        }
        require(deterministic != null,
                "drain barrier exposes a two-source deterministic constructor");
        final int constructorModifiers = deterministic.getModifiers();
        require(!Modifier.isPublic(constructorModifiers)
                && !Modifier.isProtected(constructorModifiers)
                && !Modifier.isPrivate(constructorModifiers),
                "deterministic constructor is package-private");

        final Method begin = type.getDeclaredMethod("beginDrain");
        final Method note = type.getDeclaredMethod("noteCompletedTransfer", int.class, boolean.class);
        final Method await = type.getDeclaredMethod("awaitQuiescence",
                long.class, long.class);
        final Method end = type.getDeclaredMethod("endDrain");
        require(begin.getReturnType() == void.class
                && note.getReturnType() == void.class
                && await.getReturnType() == boolean.class
                && end.getReturnType() == void.class,
                "drain barrier methods expose the frozen return types");
        boolean interruptible = false;
        for (final Class<?> exceptionType : await.getExceptionTypes()) {
            interruptible |= exceptionType == InterruptedException.class;
        }
        require(interruptible,
                "awaitQuiescence declares InterruptedException");
    }

    private static void testNoActivityReachesQuietSuccess() throws Exception {
        final FakeTime time = new FakeTime();
        final Fx3QuiescentDrainBarrier barrier = barrier(time);
        barrier.beginDrain();
        require(barrier.awaitQuiescence(QUIET_MILLIS, TIMEOUT_MILLIS),
                "no payload activity reaches quiet success");
        require(time.elapsedMillis() == QUIET_MILLIS,
                "quiet success waits for the complete quiet interval");
        barrier.endDrain();
    }

    private static void testPayloadExtendsQuietDeadline() throws Exception {
        final FakeTime time = new FakeTime();
        final Fx3QuiescentDrainBarrier barrier = barrier(time);
        barrier.beginDrain();
        time.advanceMillis(4L);
        barrier.noteCompletedTransfer(8, true);
        require(barrier.awaitQuiescence(QUIET_MILLIS, TIMEOUT_MILLIS),
                "one nonempty transfer still reaches quiescence");
        require(time.elapsedMillis() == 9L,
                "nonempty transfer extends the quiet deadline from its completion");
        barrier.endDrain();
    }

    private static void testMetadataDoesNotExtendQuietDeadline() throws Exception {
        final FakeTime time = new FakeTime();
        final Fx3QuiescentDrainBarrier barrier = barrier(time);
        barrier.beginDrain();
        time.advanceMillis(4L);
        barrier.noteCompletedTransfer(2, false);
        require(barrier.awaitQuiescence(QUIET_MILLIS, TIMEOUT_MILLIS),
                "nonempty metadata-only transfer still reaches quiescence");
        require(time.elapsedMillis() == QUIET_MILLIS,
                "metadata-only transfer does not extend source-payload quiet");
        barrier.endDrain();
    }

    private static void testContinuingPayloadTimesOut() throws Exception {
        final FakeTime time = new FakeTime();
        final Fx3QuiescentDrainBarrier barrier = barrier(time);
        time.afterSleep = () -> barrier.noteCompletedTransfer(1, true);
        barrier.beginDrain();
        require(!barrier.awaitQuiescence(QUIET_MILLIS, 12L),
                "continuing nonempty transfers force the bounded wait to time out");
        require(time.elapsedMillis() == 12L,
                "continuing payload stops exactly at the timeout bound");
        barrier.endDrain();
    }

    private static void testZeroLengthDoesNotExtendPayloadQuiet() throws Exception {
        final FakeTime time = new FakeTime();
        final Fx3QuiescentDrainBarrier barrier = barrier(time);
        barrier.beginDrain();
        time.advanceMillis(4L);
        barrier.noteCompletedTransfer(0, false);
        require(barrier.awaitQuiescence(QUIET_MILLIS, TIMEOUT_MILLIS),
                "zero-length completion does not prevent quiet success");
        require(time.elapsedMillis() == QUIET_MILLIS,
                "zero-length completion does not extend payload quiet");
        barrier.endDrain();
    }

    private static void testNegativeLengthRejected() {
        final FakeTime time = new FakeTime();
        final Fx3QuiescentDrainBarrier barrier = barrier(time);
        barrier.beginDrain();
        boolean rejected = false;
        try {
            barrier.noteCompletedTransfer(-1, false);
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        } finally {
            barrier.endDrain();
        }
        require(rejected, "negative completed-transfer lengths are rejected");
    }

    private static void testCompletedTransferTimeline() throws Exception {
        final FakeTime time = new FakeTime();
        final Fx3QuiescentDrainBarrier barrier = barrier(time);
        barrier.beginDrain();
        time.advanceMillis(2L);
        barrier.noteCompletedTransfer(8, false);
        time.advanceMillis(3L);
        barrier.noteCompletedTransfer(0, false);
        barrier.endDrain();

        final Class<?> type = Fx3QuiescentDrainBarrier.class;
        final Method count = type.getDeclaredMethod("getCompletedTransferCount");
        final Method length = type.getDeclaredMethod(
                "getCompletedTransferLength", int.class);
        final Method elapsed = type.getDeclaredMethod(
                "getCompletedTransferElapsedNanos", int.class);
        final Method payload = type.getDeclaredMethod(
                "getCompletedTransferSourcePayload", int.class);
        final Method truncated = type.getDeclaredMethod(
                "isTransferTimelineTruncated");
        require(((Number) count.invoke(barrier)).intValue() == 2,
                "timeline records every completed transfer including zero length");
        require(((Number) length.invoke(barrier, 0)).intValue() == 8
                && ((Number) length.invoke(barrier, 1)).intValue() == 0,
                "timeline retains exact transfer lengths in callback order");
        require(!((Boolean) payload.invoke(barrier, 0))
                && !((Boolean) payload.invoke(barrier, 1)),
                "timeline retains metadata-only classification including zero length");
        require(TimeUnit.NANOSECONDS.toMillis(
                ((Number) elapsed.invoke(barrier, 0)).longValue()) == 2L
                && TimeUnit.NANOSECONDS.toMillis(
                        ((Number) elapsed.invoke(barrier, 1)).longValue()) == 5L,
                "timeline retains completion times relative to drain start");
        require(!((Boolean) truncated.invoke(barrier)),
                "bounded two-transfer timeline is complete");
    }

    private static void testEndDrainIsolatesFreshDrain() throws Exception {
        final FakeTime time = new FakeTime();
        final Fx3QuiescentDrainBarrier barrier = barrier(time);
        barrier.beginDrain();
        time.advanceMillis(2L);
        barrier.noteCompletedTransfer(1, true);
        barrier.endDrain();

        time.advanceMillis(3L);
        barrier.noteCompletedTransfer(1, true);
        time.advanceMillis(5L);

        barrier.beginDrain();
        require(barrier.awaitQuiescence(QUIET_MILLIS, TIMEOUT_MILLIS),
                "fresh drain reaches quiescence after an ended drain");
        require(time.elapsedMillis() == 15L,
                "ended-drain notes cannot alter the fresh drain quiet deadline");
        barrier.endDrain();
    }

    private static Fx3QuiescentDrainBarrier barrier(final FakeTime time) {
        return new Fx3QuiescentDrainBarrier(time::nanoTime, time::sleepMillis);
    }

    private static final class FakeTime {
        private long nowNanos;
        private Runnable afterSleep;

        private long nanoTime() {
            return nowNanos;
        }

        private void sleepMillis(final long millis) throws InterruptedException {
            if (millis < 0L) {
                throw new IllegalArgumentException("sleep duration must be nonnegative");
            }
            nowNanos += TimeUnit.MILLISECONDS.toNanos(Math.max(1L, millis));
            if (afterSleep != null) {
                afterSleep.run();
            }
        }

        private void advanceMillis(final long millis) {
            if (millis < 0L) {
                throw new IllegalArgumentException("advance must be nonnegative");
            }
            nowNanos += TimeUnit.MILLISECONDS.toNanos(millis);
        }

        private long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(nowNanos);
        }
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
