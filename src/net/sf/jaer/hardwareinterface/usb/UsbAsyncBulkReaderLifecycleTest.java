package net.sf.jaer.hardwareinterface.usb;

import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle.Config;

/**
 * Self-checking tests for {@link UsbAsyncBulkReaderLifecycle} and
 * {@link UsbReaderBufferSettings}. Run with
 * {@code java net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycleTest}
 * after compiling (no JUnit dependency).
 */
public final class UsbAsyncBulkReaderLifecycleTest {

    private static final Logger LOG = Logger.getLogger("net.sf.jaer");

    private UsbAsyncBulkReaderLifecycleTest() {
    }

    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("UsbAsyncBulkReaderLifecycleTest: all checks passed");
    }

    public static void runAll() throws Exception {
        testClampAndTotalCap();
        testCoalesceAppliesFinalValueOnce();
        testStopPrecedesStart();
        testStaleCallbacksIgnored();
        testTimeoutRecoversWithoutConcurrentSessions();
        testIdleConfigDoesNotStart();
        testStoreForNextStartNeverRestarts();
        testStoreForNextStartRejectsActiveReader();
        testStatusRequestedVsActive();
        testStatusFailedPhase();
    }

    static void testClampAndTotalCap() {
        assertEquals(UsbReaderBufferSettings.MIN_FIFO_SIZE, UsbReaderBufferSettings.clampFifoSize(1));
        assertEquals(UsbReaderBufferSettings.MAX_FIFO_SIZE,
                UsbReaderBufferSettings.clampFifoSize(UsbReaderBufferSettings.MAX_FIFO_SIZE + 1));
        assertEquals(16, UsbReaderBufferSettings.clampNumBuffers(16));
        assertEquals(UsbReaderBufferSettings.MAX_NUM_BUFFERS, UsbReaderBufferSettings.clampNumBuffers(99));
        final int hugeFifo = UsbReaderBufferSettings.MAX_FIFO_SIZE;
        final int n = UsbReaderBufferSettings.clampNumBuffers(32);
        final long total = (long) hugeFifo * n;
        assertTrue("fifo x buffers should be computable", total > 0);
    }

    static void testCoalesceAppliesFinalValueOnce() throws Exception {
        final FakeHost host = new FakeHost();
        host.running.set(true);
        final UsbAsyncBulkReaderLifecycle life = new UsbAsyncBulkReaderLifecycle(host, 30L, 500L);
        try {
            life.adoptExternalStart(new Config(4096, 4));
            life.schedule(new Config(8192, 4));
            life.schedule(new Config(16384, 4));
            life.schedule(new Config(32768, 8));
            life.awaitIdle(2000L);
            assertEquals(1, host.stopCount.get());
            assertEquals(1, host.startCount.get());
            assertEquals(new Config(32768, 8), host.lastStarted.get(0));
            assertEquals(List.of("stop", "start"), host.order);
        } finally {
            life.shutdown();
        }
    }

    static void testStopPrecedesStart() throws Exception {
        final FakeHost host = new FakeHost();
        host.running.set(true);
        final UsbAsyncBulkReaderLifecycle life = new UsbAsyncBulkReaderLifecycle(host, 5L, 500L);
        try {
            life.adoptExternalStart(new Config(4096, 2));
            life.schedule(new Config(8192, 2));
            life.awaitIdle(2000L);
            assertEquals(List.of("stop", "start"), host.order);
            assertTrue("stop generation should be the previous session", host.lastStopGen.get() < host.lastStartGen.get());
        } finally {
            life.shutdown();
        }
    }

    static void testStaleCallbacksIgnored() throws Exception {
        final FakeHost host = new FakeHost();
        host.running.set(true);
        final UsbAsyncBulkReaderLifecycle life = new UsbAsyncBulkReaderLifecycle(host, 5L, 500L);
        try {
            final long gen = life.adoptExternalStart(new Config(4096, 2));
            assertTrue(life.isCurrent(gen));
            life.schedule(new Config(8192, 2));
            life.awaitIdle(2000L);
            assertFalse("old generation must not parse after replace", life.isCurrent(gen));
            assertTrue(life.isCurrent(life.currentGeneration()));
        } finally {
            life.shutdown();
        }
    }

    static void testTimeoutRecoversWithoutConcurrentSessions() throws Exception {
        final FakeHost host = new FakeHost();
        host.running.set(true);
        host.stopSucceeds.set(false);
        final UsbAsyncBulkReaderLifecycle life = new UsbAsyncBulkReaderLifecycle(host, 5L, 50L);
        try {
            life.adoptExternalStart(new Config(4096, 2));
            life.schedule(new Config(8192, 2));
            life.awaitIdle(2000L);
            assertEquals(1, host.stopCount.get());
            assertEquals(0, host.startCount.get());
            assertEquals(1, host.recoverCount.get());
            assertEquals(UsbAsyncBulkReaderLifecycle.State.FAILED, life.state());
            assertFalse("must not have two live sessions", host.running.get() && host.startCount.get() > 0);
        } finally {
            life.shutdown();
        }
    }

    static void testIdleConfigDoesNotStart() throws Exception {
        final FakeHost host = new FakeHost();
        host.running.set(false);
        final UsbAsyncBulkReaderLifecycle life = new UsbAsyncBulkReaderLifecycle(host, 5L, 500L);
        try {
            life.schedule(new Config(8192, 4));
            life.awaitIdle(2000L);
            assertEquals(0, host.stopCount.get());
            assertEquals(0, host.startCount.get());
            assertEquals(new Config(8192, 4), host.lastIdle);
            assertEquals(new Config(8192, 4), life.appliedConfig());
            assertEquals(UsbAsyncBulkReaderLifecycle.Phase.STORED_FOR_NEXT_START,
                    life.statusSnapshot().phase);
        } finally {
            life.shutdown();
        }
    }

    static void testStoreForNextStartNeverRestarts() throws Exception {
        final FakeHost host = new FakeHost();
        final UsbAsyncBulkReaderLifecycle life = new UsbAsyncBulkReaderLifecycle(
                host, 5L, 500L);
        try {
            final Config stored = new Config(16384, 8);
            life.storeForNextStart(stored);
            life.awaitIdle(2000L);
            assertEquals(0, host.stopCount.get());
            assertEquals(0, host.startCount.get());
            assertEquals(stored, host.lastIdle);
            assertEquals(stored, life.appliedConfig());
            assertEquals(UsbAsyncBulkReaderLifecycle.Phase.STORED_FOR_NEXT_START,
                    life.statusSnapshot().phase);
        } finally {
            life.shutdown();
        }
    }

    static void testStoreForNextStartRejectsActiveReader() {
        final FakeHost host = new FakeHost();
        host.running.set(true);
        final UsbAsyncBulkReaderLifecycle life = new UsbAsyncBulkReaderLifecycle(host);
        try {
            try {
                life.storeForNextStart(new Config(16384, 8));
                throw new AssertionError(
                        "active reader accepted next-start-only buffer settings");
            } catch (final IllegalStateException expected) {
                assertEquals(0, host.stopCount.get());
                assertEquals(0, host.startCount.get());
            }
        } finally {
            life.shutdown();
        }
    }

    static void testStatusRequestedVsActive() throws Exception {
        final FakeHost host = new FakeHost();
        host.running.set(true);
        final UsbAsyncBulkReaderLifecycle life = new UsbAsyncBulkReaderLifecycle(host, 200L, 500L);
        try {
            life.adoptExternalStart(new Config(4096, 2));
            assertEquals(UsbAsyncBulkReaderLifecycle.Phase.ACTIVE, life.statusSnapshot().phase);
            life.schedule(new Config(8192, 4));
            assertEquals(UsbAsyncBulkReaderLifecycle.Phase.QUEUED, life.statusSnapshot().phase);
            assertEquals(new Config(8192, 4), life.statusSnapshot().requested);
            assertEquals(new Config(4096, 2), life.statusSnapshot().active);
            life.awaitIdle(2000L);
            assertEquals(UsbAsyncBulkReaderLifecycle.Phase.ACTIVE, life.statusSnapshot().phase);
            assertEquals(new Config(8192, 4), life.statusSnapshot().active);
        } finally {
            life.shutdown();
        }
    }

    static void testStatusFailedPhase() throws Exception {
        final FakeHost host = new FakeHost();
        host.running.set(true);
        host.stopSucceeds.set(false);
        final UsbAsyncBulkReaderLifecycle life = new UsbAsyncBulkReaderLifecycle(host, 5L, 50L);
        try {
            life.adoptExternalStart(new Config(4096, 2));
            life.schedule(new Config(8192, 2));
            life.awaitIdle(2000L);
            assertEquals(UsbAsyncBulkReaderLifecycle.Phase.FAILED, life.statusSnapshot().phase);
            assertTrue("failure detail should be set",
                    life.statusSnapshot().detail != null && !life.statusSnapshot().detail.isEmpty());
        } finally {
            life.shutdown();
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(String msg, boolean cond) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void assertTrue(boolean cond) {
        assertTrue("expected true", cond);
    }

    private static void assertFalse(String msg, boolean cond) {
        if (cond) {
            throw new AssertionError(msg);
        }
    }

    private static final class FakeHost implements UsbAsyncBulkReaderLifecycle.Host {
        final AtomicBoolean running = new AtomicBoolean();
        final AtomicBoolean stopSucceeds = new AtomicBoolean(true);
        final AtomicInteger stopCount = new AtomicInteger();
        final AtomicInteger startCount = new AtomicInteger();
        final AtomicInteger recoverCount = new AtomicInteger();
        final AtomicInteger lastStopGen = new AtomicInteger();
        final AtomicInteger lastStartGen = new AtomicInteger();
        final List<Config> lastStarted = new CopyOnWriteArrayList<>();
        final List<String> order = new ArrayList<>();
        volatile Config lastIdle;
        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

        @Override
        public String deviceLabel() {
            return "FakeUSB";
        }

        @Override
        public Logger log() {
            return LOG;
        }

        @Override
        public PropertyChangeSupport readerSupport() {
            return pcs;
        }

        @Override
        public boolean hasActiveTransfer() {
            return running.get();
        }

        @Override
        public boolean stopSession(long generation, long joinTimeoutMs) {
            stopCount.incrementAndGet();
            lastStopGen.set((int) generation);
            synchronized (order) {
                order.add("stop");
            }
            if (!stopSucceeds.get()) {
                return false;
            }
            running.set(false);
            return true;
        }

        @Override
        public Config startSession(Config requested, long generation) {
            startCount.incrementAndGet();
            lastStartGen.set((int) generation);
            lastStarted.add(requested);
            synchronized (order) {
                order.add("start");
            }
            running.set(true);
            return requested;
        }

        @Override
        public void applyIdleConfig(Config config) {
            lastIdle = config;
        }

        @Override
        public void recoverFailedSession(Config pending, Exception cause) {
            recoverCount.incrementAndGet();
        }
    }
}
