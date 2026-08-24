package net.sf.jaer;

import static org.junit.Assert.assertTrue;

import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;

import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.eventio.AEUnicastOutput;
import net.sf.jaer.graphics.AEPlayer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.util.WindowSaver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Exercises process shutdown through the real {@link AEViewer#stopMe()} and
 * AEViewer final-disposal implementation. Constructors that create windows,
 * sockets, native resources, hardware factories, or shutdown hooks are
 * deliberately bypassed.
 */
public class JAERViewerRealShutdownTest {

    private static final long RETURN_TIMEOUT_SECONDS = 2;
    private static final long RESOURCE_TIMEOUT_SECONDS = 2;
    private static final long COMPLETION_TIMEOUT_SECONDS = 5;

    private static final Map<TrackingViewer, ViewerState> VIEWERS
            = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<TrackingChip, GateResource> CHIPS
            = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<TrackingUnicastInput, GateResource> INPUTS
            = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<TrackingUnicastOutput, GateResource> OUTPUTS
            = Collections.synchronizedMap(new IdentityHashMap<>());

    private JAERViewer manager;
    private TrackingViewer viewer;
    private ViewerState state;
    private TrackingWindowSaver windowSaver;
    private Thread viewLoop;
    private Preferences testPreferences;
    private Preferences originalPreferences;
    private Logger originalLogger;
    private boolean originalSkipPreferenceWrite;

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(JAERViewerRealShutdownTest.class);
    }

    @Before
    public void setUp() throws Exception {
        originalPreferences = JAERViewer.prefs;
        originalLogger = JAERViewer.log;
        originalSkipPreferenceWrite = JaerConstants.skipPreferenceWriteOnExit;

        testPreferences = Preferences.userRoot().node(
                "/net/sf/jaer/test/JAERViewerRealShutdownTest/" + UUID.randomUUID());
        JAERViewer.prefs = testPreferences;
        JAERViewer.log = Logger.getLogger(
                "net.sf.jaer.test.JAERViewerRealShutdownTest." + UUID.randomUUID());
        JAERViewer.log.setUseParentHandlers(false);
        JaerConstants.skipPreferenceWriteOnExit = false;

        manager = allocateWithoutConstructor(JAERViewer.class);
        setField(manager, "syncEnableButtons", new ArrayList<>());

        viewer = allocateWithoutConstructor(TrackingViewer.class);
        state = new ViewerState();
        VIEWERS.put(viewer, state);

        PropertyChangeSupport support = new PropertyChangeSupport(viewer);
        support.addPropertyChangeListener(AEViewer.EVENT_STOPME, event -> {
            state.stopEvents.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                state.stopEventsOffEdt.incrementAndGet();
            }
        });
        setField(viewer, "support", support);
        setField(viewer, "viewLoopPauseLock", new Object());
        setField(viewer, "jaerViewer", manager);
        viewer.prefs = testPreferences.node("Viewer");

        TrackingChip chip = allocateWithoutConstructor(TrackingChip.class);
        CHIPS.put(chip, state.chipCleanup);
        state.chip = chip;
        setField(viewer, "chip", chip);

        TrackingStream stream = new TrackingStream(state.streamClose);
        state.player = new TrackingPlayer(viewer, state.playerClose, stream.proxy);
        viewer.aePlayer = state.player;

        TrackingMonitor monitor = new TrackingMonitor(state.monitorClose);
        setField(viewer, "aemon", monitor.proxy);

        TrackingUnicastInput input = allocateWithoutConstructor(TrackingUnicastInput.class);
        TrackingUnicastOutput output = allocateWithoutConstructor(TrackingUnicastOutput.class);
        INPUTS.put(input, state.networkInputClose);
        OUTPUTS.put(output, state.networkOutputClose);
        setField(viewer, "unicastInput", input);
        setField(viewer, "unicastOutput", output);

        viewLoop = constructViewLoop(viewer);
        state.viewLoop = viewLoop;
        setField(viewer, "viewLoop", viewLoop);

        // A duplicate registration verifies identity-based exactly-once teardown
        // while ensuring this fixture can never enter AEViewer's last-window exit path.
        ArrayList<AEViewer> managedViewers = new ArrayList<>();
        managedViewers.add(viewer);
        managedViewers.add(viewer);
        setField(manager, "viewers", managedViewers);

        windowSaver = new TrackingWindowSaver(testPreferences);
        setField(manager, "windowSaver", windowSaver);

        viewLoop.setName("JAERViewerRealShutdownTest-ViewLoop");
        viewLoop.setDaemon(true);
        viewLoop.start();
        assertTrue("constructor-bypassed ViewLoop did not reach its inert visibility wait",
                state.viewLoopStarted.await(RESOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @After
    public void tearDown() throws Exception {
        if (state != null) {
            state.releaseAll();
        }
        stopViewLoopForTestCleanup(viewLoop);
        flushEdt();

        VIEWERS.clear();
        CHIPS.clear();
        INPUTS.clear();
        OUTPUTS.clear();
        if (testPreferences != null) {
            testPreferences.removeNode();
        }
        JAERViewer.prefs = originalPreferences;
        JAERViewer.log = originalLogger;
        JaerConstants.skipPreferenceWriteOnExit = originalSkipPreferenceWrite;
    }

    @Test(timeout = 20000)
    public void concurrentShutdownAwaitsRealViewerCleanupAndClosesWindowSaverOnce() throws Exception {
        List<String> violations = new ArrayList<>();
        List<CompletionStage<Void>> stages = requestShutdownConcurrently(12, violations);

        CompletableFuture<Void> completion = null;
        if (stages.isEmpty()) {
            violations.add("no concurrent requestShutdown call returned a completion stage");
            state.releaseAll();
        } else {
            IdentityHashMap<CompletionStage<Void>, Boolean> identities = new IdentityHashMap<>();
            for (CompletionStage<Void> stage : stages) {
                identities.put(stage, Boolean.TRUE);
            }
            require(identities.size() == 1,
                    "concurrent requestShutdown calls returned " + identities.size()
                            + " distinct terminal stages", violations);
            completion = stages.get(0).toCompletableFuture();
        }

        if (completion != null) {
            List<GateResource> remaining = new ArrayList<>(state.finalCleanupResources());
            while (!remaining.isEmpty()) {
                GateResource entered = awaitAnyEntered(remaining);
                if (entered == null) {
                    violations.add("shutdown reached no pending final-cleanup resource; missing "
                            + resourceNames(remaining));
                    state.releaseAll();
                    break;
                }
                require(!completion.isDone(),
                        "shutdown reported terminal before " + entered.name + " completed",
                        violations);
                entered.release();
                remaining.remove(entered);
            }
            awaitCompletion(completion, violations);
        }

        if (viewLoop != null) {
            viewLoop.join(RESOURCE_TIMEOUT_SECONDS * 1000);
        }

        require(state.asyncFailure.get() == null,
                "instrumented ViewLoop failed: " + state.asyncFailure.get(), violations);
        require(state.stopEvents.get() == 1,
                "real AEViewer.stopMe event count was " + state.stopEvents.get(), violations);
        require(state.stopEventsOffEdt.get() == 0,
                "real AEViewer.stopMe event ran off the EDT "
                        + state.stopEventsOffEdt.get() + " time(s)", violations);
        require(state.confirmingStopCalls.get() == 0,
                "requestShutdown requested recording confirmation "
                        + state.confirmingStopCalls.get() + " time(s)", violations);
        require(state.noninteractiveStopCalls.get() == 1,
                "noninteractive recording stop count was "
                        + state.noninteractiveStopCalls.get(), violations);
        require(state.stopRecordingOffEdt.get() == 0,
                "recording stop ran off the EDT "
                        + state.stopRecordingOffEdt.get() + " time(s)", violations);
        require(state.disposeCalls.get() == 1,
                "Swing viewer dispose count was " + state.disposeCalls.get(), violations);
        require(state.disposeOffEdt.get() == 0,
                "Swing viewer disposal ran off the EDT "
                        + state.disposeOffEdt.get() + " time(s)", violations);

        require(state.viewLoopStop.calls.get() == 1,
                "ViewLoop stop/exit count was " + state.viewLoopStop.calls.get(), violations);
        require(isViewLoopStopRequested(viewLoop, state.asyncFailure),
                "terminal shutdown did not request ViewLoop stop", violations);
        require(viewLoop != null && !viewLoop.isAlive(),
                "terminal shutdown left ViewLoop alive", violations);
        require(state.player.stopPlaybackCalls.get() == 0,
                "process shutdown used playback-mode stop instead of final player close "
                        + state.player.stopPlaybackCalls.get() + " time(s)", violations);

        assertExactlyOnceOffEdt(state.playerClose, violations);
        assertExactlyOnceOffEdt(state.streamClose, violations);
        assertExactlyOnceOffEdt(state.monitorClose, violations);
        assertExactlyOnceOffEdt(state.networkInputClose, violations);
        assertExactlyOnceOffEdt(state.networkOutputClose, violations);
        assertExactlyOnceOffEdt(state.chipCleanup, violations);

        require(windowSaver.closeCalls.get() == 1,
                "TrackingWindowSaver.close count was " + windowSaver.closeCalls.get(), violations);
        require(windowSaver.saveCalls.get() == 0,
                "shutdown used saveSettings instead of close "
                        + windowSaver.saveCalls.get() + " time(s)", violations);
        require(windowSaver.closeOffEdt.get() == 0,
                "TrackingWindowSaver.close ran off the EDT "
                        + windowSaver.closeOffEdt.get() + " time(s)", violations);
        require(manager.getViewers().isEmpty(),
                "terminal shutdown retained registered viewers", violations);

        assertTrue(String.join("; ", violations), violations.isEmpty());
    }

    private List<CompletionStage<Void>> requestShutdownConcurrently(
            int requestCount, List<String> violations) throws Exception {
        CyclicBarrier start = new CyclicBarrier(requestCount);
        ExecutorService callers = Executors.newFixedThreadPool(requestCount, runnable -> {
            Thread thread = new Thread(runnable, "JAERViewerRealShutdownTest-caller");
            thread.setDaemon(true);
            return thread;
        });
        List<Future<CompletionStage<Void>>> submitted = new ArrayList<>();
        List<CompletionStage<Void>> returned = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                submitted.add(callers.submit(() -> {
                    start.await(RETURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return manager.requestShutdown();
                }));
            }
            for (Future<CompletionStage<Void>> future : submitted) {
                try {
                    returned.add(future.get(RETURN_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                } catch (TimeoutException blockedRequest) {
                    violations.add("requestShutdown blocked instead of returning its terminal stage");
                    state.releaseAll();
                    try {
                        returned.add(future.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    } catch (ExecutionException failure) {
                        violations.add("blocked requestShutdown failed: " + failure.getCause());
                    }
                } catch (ExecutionException failure) {
                    violations.add("requestShutdown threw " + failure.getCause());
                }
            }
        } finally {
            callers.shutdownNow();
            callers.awaitTermination(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        return returned;
    }

    private static GateResource awaitAnyEntered(List<GateResource> resources)
            throws InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(RESOURCE_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            for (GateResource resource : resources) {
                if (resource.hasEntered()) {
                    return resource;
                }
            }
            Thread.sleep(5);
        }
        for (GateResource resource : resources) {
            if (resource.hasEntered()) {
                return resource;
            }
        }
        return null;
    }

    private static void awaitCompletion(
            CompletableFuture<Void> completion, List<String> violations) {
        try {
            completion.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException failure) {
            violations.add("requestShutdown terminal stage did not complete after all gates released");
        } catch (ExecutionException failure) {
            violations.add("requestShutdown terminal stage failed: " + failure.getCause());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            violations.add("interrupted while awaiting requestShutdown terminal stage");
        }
    }

    private static void assertExactlyOnceOffEdt(
            GateResource resource, List<String> violations) {
        require(resource.calls.get() == 1,
                resource.name + " count was " + resource.calls.get(), violations);
        require(resource.onEdtCalls.get() == 0,
                resource.name + " ran on the EDT "
                        + resource.onEdtCalls.get() + " time(s)", violations);
    }

    private static String resourceNames(List<GateResource> resources) {
        List<String> names = new ArrayList<>();
        for (GateResource resource : resources) {
            names.add(resource.name);
        }
        return String.join(", ", names);
    }

    private static void require(
            boolean condition, String message, List<String> violations) {
        if (!condition) {
            violations.add(message);
        }
    }

    private static Thread constructViewLoop(AEViewer outer) throws Exception {
        Class<?> viewLoopClass = Class.forName(AEViewer.class.getName() + "$ViewLoop");
        Constructor<?> constructor = viewLoopClass.getDeclaredConstructor(AEViewer.class);
        constructor.setAccessible(true);
        return (Thread) constructor.newInstance(outer);
    }

    private static boolean isViewLoopStopRequested(
            Thread thread, AtomicReference<Throwable> failure) {
        if (thread == null) {
            return false;
        }
        try {
            Field stop = findField(thread.getClass(), "stop");
            if (stop == null) {
                throw new NoSuchFieldException("ViewLoop.stop");
            }
            stop.setAccessible(true);
            return stop.getBoolean(thread);
        } catch (Throwable reflectionFailure) {
            failure.compareAndSet(null, reflectionFailure);
            return true;
        }
    }

    private static void stopViewLoopForTestCleanup(Thread thread) throws Exception {
        if (thread == null || !thread.isAlive()) {
            return;
        }
        Field stop = findField(thread.getClass(), "stop");
        if (stop != null) {
            stop.setAccessible(true);
            stop.setBoolean(thread, true);
        }
        thread.interrupt();
        thread.join(COMPLETION_TIMEOUT_SECONDS * 1000);
        assertTrue("test ViewLoop did not terminate during fixture cleanup", !thread.isAlive());
    }

    private static void flushEdt() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeAndWait(() -> {
                // Drain all EDT work queued before this marker.
            });
        }
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

    @SuppressWarnings("unchecked")
    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return (T) allocateInstance.invoke(unsafe, type);
    }

    private static Object primitiveDefault(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        throw new AssertionError("unhandled primitive " + returnType);
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

    private static final class ViewerState {

        final AtomicInteger confirmingStopCalls = new AtomicInteger();
        final AtomicInteger noninteractiveStopCalls = new AtomicInteger();
        final AtomicInteger stopRecordingOffEdt = new AtomicInteger();
        final AtomicInteger stopEvents = new AtomicInteger();
        final AtomicInteger stopEventsOffEdt = new AtomicInteger();
        final AtomicInteger disposeCalls = new AtomicInteger();
        final AtomicInteger disposeOffEdt = new AtomicInteger();
        final AtomicReference<Throwable> asyncFailure = new AtomicReference<>();
        final CountDownLatch viewLoopStarted = new CountDownLatch(1);

        final GateResource viewLoopStop = new GateResource("ViewLoop final exit");
        final GateResource playerClose = new GateResource("AEPlayer.close");
        final GateResource streamClose = new GateResource("playback stream close");
        final GateResource monitorClose = new GateResource("AEMonitor close");
        final GateResource networkInputClose = new GateResource("network input close");
        final GateResource networkOutputClose = new GateResource("network output close");
        final GateResource chipCleanup = new GateResource("AEChip.cleanup");

        volatile Thread viewLoop;
        volatile TrackingPlayer player;
        volatile TrackingChip chip;

        List<GateResource> finalCleanupResources() {
            return List.of(viewLoopStop, playerClose, streamClose, monitorClose,
                    networkInputClose, networkOutputClose, chipCleanup);
        }

        void releaseAll() {
            for (GateResource resource : finalCleanupResources()) {
                resource.release();
            }
        }
    }

    private static final class GateResource {

        final String name;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger onEdtCalls = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);

        GateResource(String name) {
            this.name = name;
        }

        void enter() {
            calls.incrementAndGet();
            if (SwingUtilities.isEventDispatchThread()) {
                onEdtCalls.incrementAndGet();
            }
            entered.countDown();
            awaitUninterruptibly(released);
        }

        boolean hasEntered() {
            return entered.getCount() == 0;
        }

        void release() {
            released.countDown();
        }
    }

    /** Does not override stopMe or any final-disposal method. */
    private static final class TrackingViewer extends AEViewer {

        private TrackingViewer() {
            super((JAERViewer) null);
            // Never invoked: JFrame/AEViewer construction is intentionally bypassed.
        }

        @Override
        public PlayMode getPlayMode() {
            return PlayMode.PLAYBACK;
        }

        @Override
        public AEChip getChip() {
            return viewerState(this).chip;
        }

        @Override
        public TrackingPlayer getAePlayer() {
            return viewerState(this).player;
        }

        @Override
        public synchronized File stopRecording(boolean confirmFilename) {
            ViewerState state = viewerState(this);
            if (confirmFilename) {
                state.confirmingStopCalls.incrementAndGet();
            } else {
                state.noninteractiveStopCalls.incrementAndGet();
            }
            if (!SwingUtilities.isEventDispatchThread()) {
                state.stopRecordingOffEdt.incrementAndGet();
            }
            return null;
        }

        @Override
        public void dispose() {
            ViewerState state = viewerState(this);
            state.disposeCalls.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                state.disposeOffEdt.incrementAndGet();
            }
        }

        @Override
        public boolean isVisible() {
            ViewerState state = viewerState(this);
            state.viewLoopStarted.countDown();
            if (!isViewLoopStopRequested(state.viewLoop, state.asyncFailure)) {
                return false;
            }
            state.viewLoopStop.enter();
            return true;
        }

        @Override
        public void endFilePlaybackOpen() {
            // AEPlayer.close projects this Swing state change back to its owner.
        }

        @Override
        public String toString() {
            return "TrackingViewer";
        }
    }

    private static ViewerState viewerState(TrackingViewer viewer) {
        ViewerState state = VIEWERS.get(viewer);
        if (state == null) {
            throw new AssertionError("unregistered TrackingViewer");
        }
        return state;
    }

    private static final class TrackingPlayer extends AEPlayer {

        final GateResource closeGate;
        final AtomicInteger stopPlaybackCalls = new AtomicInteger();

        TrackingPlayer(AEViewer viewer, GateResource closeGate,
                AEFileInputStreamInterface stream) {
            super(viewer);
            this.closeGate = closeGate;
            this.aeInputStream = stream;
        }

        @Override
        public void stopPlayback() {
            stopPlaybackCalls.incrementAndGet();
        }

        @Override
        public void stopPlayback(boolean resumeLive) {
            stopPlaybackCalls.incrementAndGet();
        }

        @Override
        public void close() throws IOException {
            closeGate.enter();
            super.close();
        }
    }

    private static final class TrackingStream {

        final AEFileInputStreamInterface proxy;

        TrackingStream(GateResource closeGate) {
            proxy = (AEFileInputStreamInterface) Proxy.newProxyInstance(
                    AEFileInputStreamInterface.class.getClassLoader(),
                    new Class<?>[]{AEFileInputStreamInterface.class},
                    (ignoredProxy, method, args) -> {
                        switch (method.getName()) {
                            case "close":
                                closeGate.enter();
                                return null;
                            case "toString":
                                return "TrackingStream";
                            case "hashCode":
                                return System.identityHashCode(ignoredProxy);
                            case "equals":
                                return ignoredProxy == args[0];
                            default:
                                return primitiveDefault(method.getReturnType());
                        }
                    });
        }
    }

    private static final class TrackingMonitor {

        final AEMonitorInterface proxy;

        TrackingMonitor(GateResource closeGate) {
            proxy = (AEMonitorInterface) Proxy.newProxyInstance(
                    AEMonitorInterface.class.getClassLoader(),
                    new Class<?>[]{AEMonitorInterface.class},
                    (ignoredProxy, method, args) -> {
                        switch (method.getName()) {
                            case "isOpen":
                                return true;
                            case "isEventAcquisitionEnabled":
                                return false;
                            case "close":
                                closeGate.enter();
                                return null;
                            case "toString":
                                return "TrackingMonitor";
                            case "hashCode":
                                return System.identityHashCode(ignoredProxy);
                            case "equals":
                                return ignoredProxy == args[0];
                            default:
                                return primitiveDefault(method.getReturnType());
                        }
                    });
        }
    }

    private static final class TrackingChip extends AEChip {

        private TrackingChip() {
            // Never invoked: AEChip construction is intentionally bypassed.
        }

        @Override
        public Class<? extends BasicEvent> getEventClass() {
            return BasicEvent.class;
        }

        @Override
        public void cleanup() {
            GateResource resource = CHIPS.get(this);
            if (resource == null) {
                throw new AssertionError("unregistered TrackingChip");
            }
            resource.enter();
        }
    }

    private static final class TrackingUnicastInput extends AEUnicastInput {

        private TrackingUnicastInput() {
            super((AEChip) null);
            // Never invoked: network construction is intentionally bypassed.
        }

        @Override
        public void close() {
            GateResource resource = INPUTS.get(this);
            if (resource == null) {
                throw new AssertionError("unregistered TrackingUnicastInput");
            }
            resource.enter();
        }

        @Override
        public String toString() {
            return "TrackingUnicastInput";
        }
    }

    private static final class TrackingUnicastOutput extends AEUnicastOutput {

        private TrackingUnicastOutput() {
            // Never invoked: network construction is intentionally bypassed.
        }

        @Override
        public void close() {
            GateResource resource = OUTPUTS.get(this);
            if (resource == null) {
                throw new AssertionError("unregistered TrackingUnicastOutput");
            }
            resource.enter();
        }

        @Override
        public String toString() {
            return "TrackingUnicastOutput";
        }
    }

    private static final class TrackingWindowSaver extends WindowSaver {

        final AtomicInteger saveCalls = new AtomicInteger();
        final AtomicInteger closeCalls = new AtomicInteger();
        final AtomicInteger closeOffEdt = new AtomicInteger();

        TrackingWindowSaver(Preferences preferences) {
            super(new Object(), preferences);
        }

        @Override
        public void saveSettings() {
            saveCalls.incrementAndGet();
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                closeOffEdt.incrementAndGet();
            }
        }
    }
}
