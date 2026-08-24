package net.sf.jaer;

import static org.junit.Assert.assertTrue;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListenerProxy;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.util.WindowSaver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Frozen acceptance tests for process-level viewer shutdown. Constructors are
 * bypassed so the tests exercise shutdown ownership without opening a desktop,
 * loading JOGL, enumerating hardware, or installing a real application hook.
 *
 * <p>The required noninteractive entry point is a no-argument
 * {@code requestShutdown()} method returning a {@link CompletionStage}. The
 * returned stage is the test-observable terminal state; requesting teardown
 * must not terminate the JVM.</p>
 */
public class JAERViewerShutdownTest {

    private static final long TIMEOUT_SECONDS = 5;
    private static final Map<TrackingViewer, ViewerState> VIEWER_STATES
            = Collections.synchronizedMap(new IdentityHashMap<>());

    private JAERViewer manager;
    private Preferences testPreferences;
    private Preferences originalPreferences;
    private Logger testLogger;
    private Logger originalLogger;
    private boolean originalSkipPreferenceWrite;
    private TrackingWindowSaver registeredWindowSaver;
    private TrackingHandler registeredHandler;
    private Thread registeredExitHook;
    private boolean exitHookRegistered;
    private CountDownLatch releaseEdt;

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(JAERViewerShutdownTest.class);
    }

    @Before
    public void setUp() throws Exception {
        originalPreferences = JAERViewer.prefs;
        originalLogger = JAERViewer.log;
        originalSkipPreferenceWrite = JaerConstants.skipPreferenceWriteOnExit;

        testPreferences = Preferences.userRoot().node(
                "/net/sf/jaer/test/JAERViewerShutdownTest/" + UUID.randomUUID());
        testLogger = Logger.getLogger(
                "net.sf.jaer.test.JAERViewerShutdownTest." + UUID.randomUUID());
        testLogger.setUseParentHandlers(false);
        JAERViewer.prefs = testPreferences;
        JAERViewer.log = testLogger;
        JaerConstants.skipPreferenceWriteOnExit = false;

        manager = allocateWithoutConstructor(JAERViewer.class);
        setField(manager, "viewers", new ArrayList<AEViewer>());
        setField(manager, "syncEnableButtons", new ArrayList<>());
        initializeNullShutdownPrimitives(manager);
    }

    @After
    public void tearDown() throws Exception {
        if (releaseEdt != null) {
            releaseEdt.countDown();
        }
        if (registeredWindowSaver != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(registeredWindowSaver);
        }
        if (exitHookRegistered && registeredExitHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(registeredExitHook);
            } catch (IllegalStateException ignored) {
                // The JVM is not expected to be shutting down in this test.
            }
        }
        if (registeredHandler != null) {
            testLogger.removeHandler(registeredHandler);
            if (registeredHandler.closeCalls.get() == 0) {
                registeredHandler.close();
            }
        }
        VIEWER_STATES.clear();
        if (testPreferences != null) {
            testPreferences.removeNode();
        }
        JAERViewer.prefs = originalPreferences;
        JAERViewer.log = originalLogger;
        JaerConstants.skipPreferenceWriteOnExit = originalSkipPreferenceWrite;
        flushEdt();
    }

    @Test(timeout = 10000)
    public void offEdtRequestIsNonblockingNoninteractiveAndCompletesAfterEdtTeardown() throws Exception {
        TrackingViewer viewer = addTrackingViewer();
        ViewerState state = viewerState(viewer);
        List<String> violations = new ArrayList<>();

        CountDownLatch edtBlocked = new CountDownLatch(1);
        releaseEdt = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            edtBlocked.countDown();
            awaitUninterruptibly(releaseEdt);
        });
        require(edtBlocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "EDT blocker did not start", violations);

        ExecutorService caller = Executors.newSingleThreadExecutor();
        ShutdownCall call = null;
        try {
            Future<ShutdownCall> returned = caller.submit(() -> requestShutdown(manager));
            try {
                call = returned.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException blockedCaller) {
                violations.add("off-EDT requestShutdown blocked waiting for the EDT");
                releaseEdt.countDown();
                call = returned.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

            violations.addAll(call.violations);
            require(!call.completion.isDone(),
                    "shutdown reported terminal while Swing teardown was still blocked", violations);
            releaseEdt.countDown();
            awaitCompletion(call, violations);
        } finally {
            releaseEdt.countDown();
            caller.shutdownNow();
            caller.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        require(state.confirmingStopCalls.get() == 0,
                "shutdown requested a recording confirmation dialog "
                        + state.confirmingStopCalls.get() + " time(s)", violations);
        require(state.noninteractiveStopCalls.get() == 1,
                "noninteractive recording stop count was "
                        + state.noninteractiveStopCalls.get(), violations);
        require(state.stopCalls.get() == 1,
                "viewer stop count was " + state.stopCalls.get(), violations);
        require(state.disposeCalls.get() == 1,
                "viewer dispose count was " + state.disposeCalls.get(), violations);
        require(state.offEdtSwingCalls.get() == 0,
                "Swing-owned viewer teardown ran off the EDT "
                        + state.offEdtSwingCalls.get() + " time(s)", violations);
        require(manager.getViewers().isEmpty(),
                "terminal shutdown retained registered viewers", violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 10000)
    public void repeatedConcurrentRequestsCloseEveryRegisteredViewerExactlyOnce() throws Exception {
        TrackingViewer first = addTrackingViewer();
        TrackingViewer second = addTrackingViewer();
        List<String> violations = new ArrayList<>();
        int requestCount = 12;
        CyclicBarrier start = new CyclicBarrier(requestCount);
        ExecutorService callers = Executors.newFixedThreadPool(requestCount);
        List<Future<ShutdownCall>> returned = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                returned.add(callers.submit(() -> {
                    start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return requestShutdown(manager);
                }));
            }
            for (Future<ShutdownCall> future : returned) {
                ShutdownCall call = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                violations.addAll(call.violations);
                awaitCompletion(call, violations);
            }
        } finally {
            callers.shutdownNow();
            callers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        assertViewerClosedExactlyOnce(first, "first", violations);
        assertViewerClosedExactlyOnce(second, "second", violations);
        require(manager.getViewers().isEmpty(),
                "concurrent terminal shutdown retained registered viewers", violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 10000)
    public void repeatedShutdownStopsAndUnregistersLoggingAndExitResourcesExactlyOnce() throws Exception {
        List<String> violations = new ArrayList<>();

        registeredWindowSaver = new TrackingWindowSaver(testPreferences);
        setField(manager, "windowSaver", registeredWindowSaver);
        Toolkit.getDefaultToolkit().addAWTEventListener(
                registeredWindowSaver, AWTEvent.WINDOW_EVENT_MASK);

        registeredHandler = new TrackingHandler();
        testLogger.addHandler(registeredHandler);

        registeredExitHook = new Thread(() -> {
            throw new AssertionError("test exit hook must be removed, never run");
        }, "JAERViewerShutdownTest-exit-hook");
        Runtime.getRuntime().addShutdownHook(registeredExitHook);
        exitHookRegistered = true;
        retainShutdownHookForTest(manager, registeredExitHook, violations);

        ShutdownCall first = requestShutdown(manager);
        violations.addAll(first.violations);
        awaitCompletion(first, violations);
        ShutdownCall second = requestShutdown(manager);
        violations.addAll(second.violations);
        awaitCompletion(second, violations);

        require(registeredWindowSaver.saveCalls.get() == 1,
                "WindowSaver save count was " + registeredWindowSaver.saveCalls.get(), violations);
        require(registeredWindowSaver.offEdtCalls.get() == 0,
                "WindowSaver was read off the EDT "
                        + registeredWindowSaver.offEdtCalls.get() + " time(s)", violations);
        require(!containsIdentity(Toolkit.getDefaultToolkit().getAWTEventListeners(),
                registeredWindowSaver), "WindowSaver remained registered globally", violations);
        require(registeredHandler.closeCalls.get() == 1,
                "logging handler close count was " + registeredHandler.closeCalls.get(), violations);
        require(!containsIdentity(testLogger.getHandlers(), registeredHandler),
                "closed logging handler remained registered", violations);

        boolean hookWasStillRegistered = Runtime.getRuntime().removeShutdownHook(registeredExitHook);
        if (hookWasStillRegistered) {
            exitHookRegistered = false;
        }
        require(!hookWasStillRegistered,
                "JAERViewer shutdown hook remained registered after terminal shutdown", violations);
        assertNoViolations(violations);
    }

    private TrackingViewer addTrackingViewer() throws Exception {
        TrackingViewer viewer = allocateWithoutConstructor(TrackingViewer.class);
        AEChip chip = allocateWithoutConstructor(TrackingChip.class);
        VIEWER_STATES.put(viewer, new ViewerState(chip));
        manager.getViewers().add(viewer);
        return viewer;
    }

    private static void assertViewerClosedExactlyOnce(
            TrackingViewer viewer, String label, List<String> violations) {
        ViewerState state = viewerState(viewer);
        require(state.confirmingStopCalls.get() == 0,
                label + " viewer requested confirmation "
                        + state.confirmingStopCalls.get() + " time(s)", violations);
        require(state.noninteractiveStopCalls.get() == 1,
                label + " viewer noninteractive stop count was "
                        + state.noninteractiveStopCalls.get(), violations);
        require(state.stopCalls.get() == 1,
                label + " viewer stop count was " + state.stopCalls.get(), violations);
        require(state.disposeCalls.get() == 1,
                label + " viewer dispose count was " + state.disposeCalls.get(), violations);
        require(state.offEdtSwingCalls.get() == 0,
                label + " viewer teardown ran off EDT "
                        + state.offEdtSwingCalls.get() + " time(s)", violations);
    }

    private static ShutdownCall requestShutdown(JAERViewer target) {
        List<String> violations = new ArrayList<>();
        Method method = findNoArgMethod(target.getClass(), "requestShutdown");
        if (method == null) {
            violations.add("JAERViewer has no noninteractive requestShutdown() CompletionStage seam");
            runCurrentShutdownHook(target, violations);
            return new ShutdownCall(CompletableFuture.completedFuture(null), violations);
        }

        try {
            method.setAccessible(true);
            Object returned = method.invoke(target);
            if (!(returned instanceof CompletionStage<?> stage)) {
                violations.add("requestShutdown() did not return a CompletionStage terminal state");
                return new ShutdownCall(CompletableFuture.completedFuture(null), violations);
            }
            CompletableFuture<?> completion;
            try {
                completion = stage.toCompletableFuture();
            } catch (RuntimeException unsupportedObservation) {
                violations.add("requestShutdown() returned an unobservable CompletionStage: "
                        + unsupportedObservation);
                completion = CompletableFuture.completedFuture(null);
            }
            return new ShutdownCall(completion, violations);
        } catch (InvocationTargetException invocationFailure) {
            violations.add("requestShutdown() threw " + invocationFailure.getCause());
        } catch (ReflectiveOperationException | RuntimeException invocationFailure) {
            violations.add("could not invoke requestShutdown(): " + invocationFailure);
        }
        return new ShutdownCall(CompletableFuture.completedFuture(null), violations);
    }

    /**
     * Exercise today's real hook when the required seam is absent. This keeps
     * RED output behavioral (confirmation, thread ownership, duplicate close,
     * retained resources) without starting JVM shutdown.
     */
    private static void runCurrentShutdownHook(JAERViewer target, List<String> violations) {
        try {
            for (int suffix = 1; suffix <= 16; suffix++) {
                Class<?> candidate;
                try {
                    candidate = Class.forName(JAERViewer.class.getName() + "$" + suffix);
                } catch (ClassNotFoundException missingSuffix) {
                    continue;
                }
                if (!Thread.class.isAssignableFrom(candidate)) {
                    continue;
                }
                for (Constructor<?> constructor : candidate.getDeclaredConstructors()) {
                    Class<?>[] parameters = constructor.getParameterTypes();
                    if (parameters.length == 1
                            && parameters[0].isAssignableFrom(target.getClass())) {
                        constructor.setAccessible(true);
                        ((Thread) constructor.newInstance(target)).run();
                        return;
                    }
                }
            }
            violations.add("could not locate the current JAERViewer shutdown hook runtime path");
        } catch (ReflectiveOperationException | RuntimeException hookFailure) {
            violations.add("current JAERViewer shutdown hook failed: " + hookFailure);
        }
    }

    private static void awaitCompletion(ShutdownCall call, List<String> violations) {
        try {
            call.completion.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            violations.add("shutdown terminal state did not complete normally: " + failure);
        }
    }

    private static void retainShutdownHookForTest(
            JAERViewer target, Thread hook, List<String> violations) {
        Field field = findField(target.getClass(), "shutdownHook");
        if (field == null || !Thread.class.isAssignableFrom(field.getType())) {
            violations.add("JAERViewer does not retain its shutdownHook for removal");
            return;
        }
        try {
            field.setAccessible(true);
            field.set(target, hook);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            violations.add("could not install the retained test shutdownHook: " + failure);
        }
    }

    private static void initializeNullShutdownPrimitives(Object target) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getName().toLowerCase().contains("shutdown")) {
                    continue;
                }
                field.setAccessible(true);
                if (field.get(target) != null) {
                    continue;
                }
                if (field.getType() == AtomicBoolean.class) {
                    field.set(target, new AtomicBoolean());
                } else if (field.getType() == AtomicInteger.class) {
                    field.set(target, new AtomicInteger());
                } else if (field.getType() == AtomicReference.class) {
                    field.set(target, new AtomicReference<>());
                } else if (field.getType() == CompletableFuture.class) {
                    field.set(target, new CompletableFuture<>());
                }
            }
        }
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean containsIdentity(Object[] values, Object expected) {
        for (Object value : values) {
            if (value == expected) {
                return true;
            }
            if (value instanceof AWTEventListenerProxy proxy
                    && proxy.getListener() == expected) {
                return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message, List<String> violations) {
        if (!condition) {
            violations.add(message);
        }
    }

    private static void assertNoViolations(List<String> violations) {
        assertTrue(String.join("; ", violations), violations.isEmpty());
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void flushEdt() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeAndWait(() -> {
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return (T) allocateInstance.invoke(unsafe, type);
    }

    private static ViewerState viewerState(TrackingViewer viewer) {
        ViewerState state = VIEWER_STATES.get(viewer);
        if (state == null) {
            throw new AssertionError("unregistered tracking viewer");
        }
        return state;
    }

    private static final class ShutdownCall {

        final CompletableFuture<?> completion;
        final List<String> violations;

        ShutdownCall(CompletableFuture<?> completion, List<String> violations) {
            this.completion = completion;
            this.violations = violations;
        }
    }

    private static final class ViewerState {

        final AEChip chip;
        final AtomicInteger confirmingStopCalls = new AtomicInteger();
        final AtomicInteger noninteractiveStopCalls = new AtomicInteger();
        final AtomicInteger stopCalls = new AtomicInteger();
        final AtomicInteger disposeCalls = new AtomicInteger();
        final AtomicInteger offEdtSwingCalls = new AtomicInteger();
        final File recordingFile = new File("JAERViewerShutdownTest-recording.aedat4");

        ViewerState(AEChip chip) {
            this.chip = chip;
        }

        void recordSwingCall() {
            if (!SwingUtilities.isEventDispatchThread()) {
                offEdtSwingCalls.incrementAndGet();
            }
        }
    }

    private static final class TrackingViewer extends AEViewer {

        private TrackingViewer() {
            super((JAERViewer) null);
        }

        @Override
        public AEChip getChip() {
            return viewerState(this).chip;
        }

        @Override
        public File getRecordingFile() {
            return viewerState(this).recordingFile;
        }

        @Override
        public synchronized File stopRecording(boolean confirmFilename) {
            ViewerState state = viewerState(this);
            state.recordSwingCall();
            if (confirmFilename) {
                state.confirmingStopCalls.incrementAndGet();
            } else {
                state.noninteractiveStopCalls.incrementAndGet();
            }
            return state.recordingFile;
        }

        @Override
        public void stopMe() {
            ViewerState state = viewerState(this);
            state.recordSwingCall();
            state.stopCalls.incrementAndGet();
            stopRecording(false);
        }

        @Override
        public void dispose() {
            ViewerState state = viewerState(this);
            state.recordSwingCall();
            state.disposeCalls.incrementAndGet();
        }
    }

    private static final class TrackingChip extends AEChip {

        private TrackingChip() {
        }
    }

    private static final class TrackingWindowSaver extends WindowSaver {

        final AtomicInteger saveCalls = new AtomicInteger();
        final AtomicInteger offEdtCalls = new AtomicInteger();

        TrackingWindowSaver(Preferences preferences) {
            super(new Object(), preferences);
        }

        @Override
        public void saveSettings() {
            saveCalls.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                offEdtCalls.incrementAndGet();
            }
        }
    }

    private static final class TrackingHandler extends Handler {

        final AtomicInteger closeCalls = new AtomicInteger();

        @Override
        public void publish(LogRecord record) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
        }
    }
}
