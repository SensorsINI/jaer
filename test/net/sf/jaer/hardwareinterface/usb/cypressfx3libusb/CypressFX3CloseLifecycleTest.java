package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.usb4java.Device;
import org.usb4java.DeviceHandle;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Acceptance tests for closing an FX3 connection without overlapping reader
 * generations or leaking the native handle. usb4java is replaced before its
 * classes load, so these tests neither load its native library nor touch USB.
 */
public class CypressFX3CloseLifecycleTest {

    private static final int LIBUSB_SUCCESS = 0;
    private static final int LIBUSB_ERROR_IO = -1;
    private static final int LIBUSB_ERROR_BUSY = -6;

    @BeforeClass
    public static void installNoNativeLibUsbFake() throws Exception {
        final Instrumentation instrumentation = ByteBuddyAgent.install();
        assertFalse("LibUsb loaded before its no-native test fake was installed",
                isLoaded(instrumentation, "org.usb4java.LibUsb"));
        assertFalse("usb4java Loader loaded before its no-native test fake was installed",
                isLoaded(instrumentation, "org.usb4java.Loader"));

        new AgentBuilder.Default()
                .type(named("org.usb4java.Loader"))
                .transform((builder, type, loader, module, domain) -> builder
                        .method(named("load").and(isStatic()).and(takesArguments(0)))
                        .intercept(MethodDelegation.to(LoaderFake.class)))
                .type(named("org.usb4java.LibUsb"))
                .transform((builder, type, loader, module, domain) -> builder
                        .method(named("resetDevice").and(isStatic()).and(takesArguments(1)))
                        .intercept(MethodDelegation.to(ResetDeviceFake.class))
                        .method(named("releaseInterface").and(isStatic()).and(takesArguments(2)))
                        .intercept(MethodDelegation.to(ReleaseInterfaceFake.class))
                        .method(named("close").and(isStatic()).and(takesArguments(1)))
                        .intercept(MethodDelegation.to(CloseHandleFake.class))
                        .method(named("open").and(isStatic()).and(takesArguments(2)))
                        .intercept(MethodDelegation.to(OpenHandleFake.class))
                        .method(named("errorName").and(isStatic()).and(takesArguments(1)))
                        .intercept(MethodDelegation.to(ErrorNameFake.class)))
                .installOn(instrumentation);

        final Class<?> libUsb = Class.forName("org.usb4java.LibUsb", true,
                CypressFX3CloseLifecycleTest.class.getClassLoader());
        assertEquals("usb4java native loader must be replaced with the no-op fake", 1,
                LoaderFake.invocations.get());
        assertReplacedNative(libUsb, "resetDevice", DeviceHandle.class);
        assertReplacedNative(libUsb, "releaseInterface", DeviceHandle.class, int.class);
        assertReplacedNative(libUsb, "close", DeviceHandle.class);
        assertReplacedNative(libUsb, "open", Device.class, DeviceHandle.class);
        assertReplacedNative(libUsb, "errorName", int.class);
    }

    @Before
    public void resetFakeLibUsb() {
        NativeCalls.reset();
    }

    @Test
    public void readerStopAndJoinPrecedeNativeHandleClose() throws Exception {
        final TestMonitor monitor = new TestMonitor(ReaderStop.TERMINATES);
        try {
            monitor.markOpenWithFakeHandle();

            monitor.close();

            final List<String> events = NativeCalls.snapshot();
            final int readerTerminal = events.indexOf("reader-terminal");
            final int nativeClose = events.indexOf("native-close");
            assertTrue("reader stop/join must complete before native handle close; events=" + events,
                    readerTerminal >= 0 && nativeClose >= 0 && readerTerminal < nativeClose
                            && !monitor.readerGeneration.isAlive());
        } finally {
            monitor.forceReaderTermination();
        }
    }

    @Test
    public void nativeHandleClosesExactlyOnceWhenResetFails() throws Exception {
        final TestMonitor monitor = new TestMonitor(ReaderStop.TERMINATES);
        try {
            monitor.markOpenWithFakeHandle();
            NativeCalls.resetResult = LIBUSB_ERROR_IO;

            monitor.close();

            assertEquals("native handle close must run exactly once after resetDevice failure; events="
                    + NativeCalls.snapshot(), 1, NativeCalls.count("native-close"));
        } finally {
            monitor.forceReaderTermination();
        }
    }

    @Test
    public void nativeHandleClosesExactlyOnceWhenReleaseFails() throws Exception {
        final TestMonitor monitor = new TestMonitor(ReaderStop.TERMINATES);
        try {
            monitor.markOpenWithFakeHandle();
            NativeCalls.releaseResult = LIBUSB_ERROR_IO;

            monitor.close();

            assertEquals("native handle close must run exactly once after releaseInterface failure; events="
                    + NativeCalls.snapshot(), 1, NativeCalls.count("native-close"));
        } finally {
            monitor.forceReaderTermination();
        }
    }

    @Test
    public void reopenIsBlockedWhilePriorReaderGenerationIsNonterminal() throws Exception {
        final TestMonitor monitor = new TestMonitor(ReaderStop.REMAINS_NONTERMINAL);
        try {
            monitor.markOpenWithFakeHandle();

            monitor.close();
            assertTrue("fixture must retain the simulated nonterminal reader generation",
                    monitor.readerGeneration.isAlive());
            assertEquals("native reset must be skipped while the reader generation is nonterminal; events="
                    + NativeCalls.snapshot(), 0, NativeCalls.count("native-reset"));
            assertEquals("native interface release must be skipped while the reader generation is nonterminal; events="
                    + NativeCalls.snapshot(), 0, NativeCalls.count("native-release"));
            assertEquals("native handle close must be skipped while the reader generation is nonterminal; events="
                    + NativeCalls.snapshot(), 0, NativeCalls.count("native-close"));
            assertEquals("endpoint disable must be skipped while the reader generation is nonterminal; events="
                    + NativeCalls.snapshot(), 0, NativeCalls.count("endpoint-disabled"));

            boolean clearRejected = false;
            try {
                monitor.setAeReader(null);
            } catch (final IllegalStateException expected) {
                clearRejected = true;
            }
            assertTrue("a retained nonterminal reader generation must not be cleared", clearRejected);

            try {
                monitor.open();
            } catch (final HardwareInterfaceException | IllegalStateException expected) {
                // A lifecycle guard may reject reopen with either existing API exception.
            }

            assertEquals("LibUsb.open must not run while the prior reader generation is nonterminal; events="
                    + NativeCalls.snapshot(), 0, NativeCalls.count("native-open"));
        } finally {
            monitor.forceReaderTermination();
        }
    }

    @Test
    public void nativeHandleIsAbandonedWhenReaderStopThrows() throws Exception {
        final TestMonitor monitor = new TestMonitor(ReaderStop.THROWS_NONTERMINAL);
        try {
            monitor.markOpenWithFakeHandle();

            monitor.close();

            assertTrue("fixture must retain the reader after a stop failure",
                    monitor.readerGeneration.isAlive());
            assertEquals("native reset must be skipped after reader stop failure; events="
                    + NativeCalls.snapshot(), 0, NativeCalls.count("native-reset"));
            assertEquals("native interface release must be skipped after reader stop failure; events="
                    + NativeCalls.snapshot(), 0, NativeCalls.count("native-release"));
            assertEquals("native handle close must be skipped after reader stop failure; events="
                    + NativeCalls.snapshot(), 0, NativeCalls.count("native-close"));
        } finally {
            monitor.forceReaderTermination();
        }
    }

    private static boolean isLoaded(final Instrumentation instrumentation, final String className) {
        for (final Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (className.equals(loaded.getName())) {
                return true;
            }
        }
        return false;
    }

    private static void assertReplacedNative(final Class<?> type, final String name,
            final Class<?>... parameterTypes) throws Exception {
        final Method method = type.getDeclaredMethod(name, parameterTypes);
        assertFalse(name + " must be replaced before invocation so no native libusb code can run",
                Modifier.isNative(method.getModifiers()));
    }

    private enum ReaderStop {
        TERMINATES,
        REMAINS_NONTERMINAL,
        THROWS_NONTERMINAL
    }

    private static final class TestMonitor extends CypressFX3 {

        private final ReaderStop readerStop;
        private final Thread readerGeneration;

        TestMonitor(final ReaderStop readerStop) throws Exception {
            super(null);
            this.readerStop = readerStop;
            numberOfStringDescriptors = 2;

            final CountDownLatch started = new CountDownLatch(1);
            readerGeneration = new Thread(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (final InterruptedException expected) {
                    Thread.currentThread().interrupt();
                }
            }, "CypressFX3CloseLifecycleTest-reader");
            readerGeneration.setDaemon(true);
            readerGeneration.start();
            assertTrue("fake reader generation did not start",
                    started.await(1, TimeUnit.SECONDS));

            setAeReader(new AEReader(this) {
                @Override
                public boolean stopThread() {
                    return stopReaderGeneration();
                }
            });
        }

        void markOpenWithFakeHandle() throws Exception {
            final Field opened = CypressFX3.class.getDeclaredField("isOpened");
            opened.setAccessible(true);
            opened.setBoolean(this, true);
            deviceHandle = new DeviceHandle();
            inEndpointEnabled = true;
        }

        @Override
        protected synchronized void disableINEndpoint() {
            NativeCalls.record("endpoint-disabled");
            inEndpointEnabled = false;
        }

        private boolean stopReaderGeneration() {
            NativeCalls.record("reader-stop-requested");
            if (readerStop == ReaderStop.REMAINS_NONTERMINAL) {
                NativeCalls.record("reader-nonterminal");
                return false;
            }
            if (readerStop == ReaderStop.THROWS_NONTERMINAL) {
                NativeCalls.record("reader-stop-failed");
                throw new IllegalStateException("simulated reader stop failure");
            }

            readerGeneration.interrupt();
            try {
                readerGeneration.join(1_000L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while joining fake reader", e);
            }
            if (readerGeneration.isAlive()) {
                throw new IllegalStateException("fake reader did not become terminal");
            }
            NativeCalls.record("reader-terminal");
            return true;
        }

        void forceReaderTermination() throws InterruptedException {
            readerGeneration.interrupt();
            readerGeneration.join(1_000L);
            assertFalse("fake reader leaked past test cleanup", readerGeneration.isAlive());
        }
    }

    private static final class NativeCalls {

        private static final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        private static volatile int resetResult = LIBUSB_SUCCESS;
        private static volatile int releaseResult = LIBUSB_SUCCESS;
        private static volatile int openResult = LIBUSB_ERROR_BUSY;

        private static void reset() {
            events.clear();
            resetResult = LIBUSB_SUCCESS;
            releaseResult = LIBUSB_SUCCESS;
            openResult = LIBUSB_ERROR_BUSY;
        }

        private static void record(final String event) {
            events.add(event);
        }

        private static List<String> snapshot() {
            return new ArrayList<>(events);
        }

        private static int count(final String wanted) {
            int count = 0;
            for (final String event : events) {
                if (wanted.equals(event)) {
                    count++;
                }
            }
            return count;
        }
    }

    public static final class LoaderFake {
        private static final AtomicInteger invocations = new AtomicInteger();

        public static void intercept() {
            invocations.incrementAndGet();
        }
    }

    public static final class ResetDeviceFake {
        public static int intercept(final DeviceHandle ignored) {
            NativeCalls.record("native-reset");
            return NativeCalls.resetResult;
        }
    }

    public static final class ReleaseInterfaceFake {
        public static int intercept(final DeviceHandle ignored, final int interfaceNumber) {
            NativeCalls.record("native-release");
            return NativeCalls.releaseResult;
        }
    }

    public static final class CloseHandleFake {
        public static void intercept(final DeviceHandle ignored) {
            NativeCalls.record("native-close");
        }
    }

    public static final class OpenHandleFake {
        public static int intercept(final Device ignored, final DeviceHandle handle) {
            NativeCalls.record("native-open");
            return NativeCalls.openResult;
        }
    }

    public static final class ErrorNameFake {
        public static String intercept(final int status) {
            return "fake-libusb-status-" + status;
        }
    }
}
