package net.sf.jaer.graphics;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.eventio.AEUnicastOutput;
import net.sf.jaer.util.RemoteControl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance tests for completion-bearing AEViewer final cleanup. GUI, chip,
 * network, and remote-control constructors are bypassed so no hardware or
 * socket is opened. The required public {@code requestFinalDisposal()} seam
 * performs Swing disposal on the EDT, performs every blocking cleanup attempt
 * off the EDT, never terminates the JVM, and returns one shared terminal stage.
 */
public class AEViewerFinalCleanupTest {

    private static final long SHORT_TIMEOUT_MS = 500;
    private static final long LONG_TIMEOUT_MS = 5_000;
    private static final Logger ROOT_LOGGER = Logger.getLogger("");

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(AEViewerFinalCleanupTest.class);
    }

    private static final Map<FakeViewer, ViewerState> VIEWER_STATES
            = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<TrackingChip, Attempt> CHIP_ATTEMPTS
            = Collections.synchronizedMap(new IdentityHashMap<>());

    private final List<ObservedCompletion> observedCompletions = new ArrayList<>();

    private Preferences originalManagerPreferences;
    private Logger originalManagerLogger;
    private boolean originalSkipPreferenceWrite;
    private Preferences testPreferences;
    private Logger testManagerLogger;
    private JAERViewer manager;
    private FakeViewer viewer;
    private FakeViewer passiveViewer;
    private ViewerState viewerState;
    private ViewerState passiveViewerState;
    private Resources resources;

    @Before
    public void setUp() throws Exception {
        originalManagerPreferences = (Preferences) getStaticField(JAERViewer.class, "prefs");
        originalManagerLogger = (Logger) getStaticField(JAERViewer.class, "log");
        originalSkipPreferenceWrite = JaerConstants.skipPreferenceWriteOnExit;

        testPreferences = Preferences.userRoot().node(
                "/net/sf/jaer/test/AEViewerFinalCleanupTest/" + UUID.randomUUID());
        testManagerLogger = Logger.getLogger(
                "net.sf.jaer.test.AEViewerFinalCleanupTest." + UUID.randomUUID());
        testManagerLogger.setUseParentHandlers(false);
        setStaticField(JAERViewer.class, "prefs", testPreferences);
        setStaticField(JAERViewer.class, "log", testManagerLogger);
        JaerConstants.skipPreferenceWriteOnExit = false;

        manager = allocateWithoutConstructor(JAERViewer.class);
        setField(manager, "viewers", new ArrayList<AEViewer>());
        setField(manager, "syncEnableButtons", new ArrayList<>());
        initializeNullLifecyclePrimitives(manager, "shutdown");

        resources = new Resources();
        viewer = newViewer(resources, "active");
        viewerState = state(viewer);
        passiveViewer = newViewer(new Resources(), "passive");
        passiveViewerState = state(passiveViewer);
        manager.getViewers().add(viewer);
        manager.getViewers().add(passiveViewer);

        setField(viewer, "jaerViewer", manager);
        setField(passiveViewer, "jaerViewer", manager);
        ROOT_LOGGER.addHandler(resources.loggingHandler);
    }

    @After
    public void tearDown() throws Exception {
        if (resources != null) {
            resources.monitor.releaseClose();
        }
        for (ObservedCompletion observed : observedCompletions) {
            observed.done.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        flushEdt();

        if (resources != null && resources.loggingHandler != null) {
            ROOT_LOGGER.removeHandler(resources.loggingHandler);
            if (resources.handlerClose.calls.get() == 0) {
                resources.loggingHandler.close();
            }
        }
        VIEWER_STATES.clear();
        CHIP_ATTEMPTS.clear();
        if (testPreferences != null) {
            testPreferences.removeNode();
        }
        setStaticField(JAERViewer.class, "prefs", originalManagerPreferences);
        setStaticField(JAERViewer.class, "log", originalManagerLogger);
        JaerConstants.skipPreferenceWriteOnExit = originalSkipPreferenceWrite;
        flushEdt();
    }

    @Test(timeout = 15_000)
    public void monitorFailureCannotSkipAnyFinalCleanupAttempt() throws Exception {
        resources.monitor.throwAfterCloseEntered = true;

        CompletionStage<Void> first = requestFinalDisposal(viewer);
        CompletionStage<Void> repeated = requestFinalDisposal(viewer);
        ObservedCompletion completion = observe(first, resources.events, "viewer-completion");

        List<String> violations = new ArrayList<>();
        require(completion.done.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "viewer final-disposal stage did not complete", violations);
        require(first == repeated,
                "repeated final-disposal requests did not return the same terminal stage", violations);
        require(completion.failure.get() == null,
                "best-effort final disposal completed exceptionally after monitor failure: "
                        + completion.failure.get(), violations);

        assertEveryResourceAttemptedOnceOffEdt(resources, violations);
        assertSwingDisposedOnceOnEdt(viewerState, "active", violations);
        require(!containsIdentity(ROOT_LOGGER.getHandlers(), resources.loggingHandler),
                "viewer logging handler remained registered on the root logger", violations);
        require(!resources.loggingHandler.attachedWhenClosed.get(),
                "viewer logging handler was closed before root-logger removal", violations);
        assertCompletionAfterAttempts(resources.events, "viewer-completion", violations);

        // Reaching this assertion after the terminal stage is also the in-process
        // proof that requestFinalDisposal itself did not call System.exit.
        require(completion.done.getCount() == 0,
                "the JVM did not continue after viewer final disposal", violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 15_000)
    public void processShutdownWaitsForBlockedSecondaryViewerCleanup() throws Exception {
        resources.monitor.blockClose = true;
        resources.monitor.throwAfterCloseEntered = false;

        CompletionStage<Void> viewerCompletionStage = requestFinalDisposal(viewer);
        CompletionStage<Void> repeated = requestFinalDisposal(viewer);
        ObservedCompletion viewerCompletion = observe(
                viewerCompletionStage, resources.events, "viewer-completion");

        List<String> violations = new ArrayList<>();
        boolean blockedCleanupStarted = resources.monitor.attempt.entered.await(
                LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        require(blockedCleanupStarted,
                "secondary viewer cleanup never reached the blocking monitor close", violations);
        require(viewerCompletionStage == repeated,
                "blocked secondary cleanup did not retain one shared terminal stage", violations);
        require(viewerCompletion.done.getCount() != 0,
                "viewer reported terminal while monitor close was still blocked", violations);

        CompletionStage<Void> processCompletionStage = manager.requestShutdown();
        ObservedCompletion processCompletion = observe(
                processCompletionStage, resources.events, "process-completion");
        boolean processFinishedWhileBlocked = processCompletion.done.await(
                SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        CountDownLatch edtPulse = new CountDownLatch(1);
        SwingUtilities.invokeLater(edtPulse::countDown);
        boolean edtResponsive = edtPulse.await(SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        require(!processFinishedWhileBlocked,
                "process-level completion truncated a blocked secondary viewer cleanup", violations);
        require(edtResponsive,
                "process-level coordination blocked the EDT while secondary cleanup was pending", violations);

        resources.monitor.releaseClose();
        require(viewerCompletion.done.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "secondary viewer cleanup did not complete after monitor release", violations);
        require(processCompletion.done.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "process-level completion did not follow secondary cleanup", violations);
        require(viewerCompletion.failure.get() == null,
                "secondary viewer cleanup completed exceptionally: "
                        + viewerCompletion.failure.get(), violations);
        require(processCompletion.failure.get() == null,
                "process-level shutdown completed exceptionally: "
                        + processCompletion.failure.get(), violations);

        assertEveryResourceAttemptedOnceOffEdt(resources, violations);
        assertSwingDisposedOnceOnEdt(viewerState, "active", violations);
        assertSwingDisposedOnceOnEdt(passiveViewerState, "passive", violations);
        require(manager.getViewers().isEmpty(),
                "process-level completion retained registered viewers", violations);
        assertEventAfterAllAttempts(resources.events, "process-completion", violations);
        assertNoViolations(violations);
    }

    private FakeViewer newViewer(Resources owned, String label) throws Exception {
        FakeViewer created = allocateWithoutConstructor(FakeViewer.class);
        TrackingChip chip = allocateWithoutConstructor(TrackingChip.class);
        Attempt chipAttempt = owned == resources
                ? owned.chip
                : owned.attempt(label + "-chip");
        CHIP_ATTEMPTS.put(chip, chipAttempt);

        ViewerState state = new ViewerState(owned, chip, label);
        VIEWER_STATES.put(created, state);
        created.prefs = testPreferences;
        created.aePlayer = owned == resources
                ? new TrackingPlayer(created, owned.player, owned.stream)
                : null;

        setField(created, "chip", chip);
        setField(created, "viewLoop", null);
        initializeNullLifecyclePrimitives(created, "finalDisposal");

        if (owned == resources) {
            setField(created, "aemon", owned.monitor.proxy);
            setField(created, "unicastInput", owned.unicastInput);
            setField(created, "unicastOutput", owned.unicastOutput);
            setField(created, "remoteControl", owned.remoteControl);
            setField(created, "loggingHandler", owned.loggingHandler);
        }
        return created;
    }

    private static CompletionStage<Void> requestFinalDisposal(AEViewer target) throws Exception {
        final Method method;
        try {
            method = AEViewer.class.getMethod("requestFinalDisposal");
        } catch (NoSuchMethodException missing) {
            throw new AssertionError(
                    "AEViewer must expose public requestFinalDisposal() returning CompletionStage<Void>",
                    missing);
        }
        if (!CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            throw new AssertionError(
                    "AEViewer.requestFinalDisposal() must return CompletionStage<Void>, not "
                            + method.getReturnType().getName());
        }
        try {
            Object returned = method.invoke(target);
            if (!(returned instanceof CompletionStage<?> stage)) {
                throw new AssertionError("AEViewer.requestFinalDisposal() returned " + returned);
            }
            @SuppressWarnings("unchecked")
            CompletionStage<Void> typed = (CompletionStage<Void>) stage;
            return typed;
        } catch (InvocationTargetException invocationFailure) {
            Throwable cause = invocationFailure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        }
    }

    private ObservedCompletion observe(
            CompletionStage<Void> stage, List<String> events, String completionEvent) {
        ObservedCompletion observed = new ObservedCompletion();
        observedCompletions.add(observed);
        stage.whenComplete((ignored, failure) -> {
            events.add(completionEvent);
            observed.failure.set(failure);
            observed.done.countDown();
        });
        return observed;
    }

    private static void assertEveryResourceAttemptedOnceOffEdt(
            Resources owned, List<String> violations) {
        for (Attempt attempt : List.of(
                owned.player,
                owned.stream,
                owned.monitor.attempt,
                owned.unicastInputAttempt,
                owned.unicastOutputAttempt,
                owned.chip,
                owned.remoteControlAttempt,
                owned.handlerClose)) {
            require(attempt.calls.get() == 1,
                    attempt.name + " attempt count was " + attempt.calls.get(), violations);
            require(attempt.edtCalls.get() == 0,
                    attempt.name + " ran on the EDT " + attempt.edtCalls.get() + " time(s)",
                    violations);
        }
    }

    private static void assertSwingDisposedOnceOnEdt(
            ViewerState state, String label, List<String> violations) {
        require(state.stopCalls.get() == 1,
                label + " viewer stop count was " + state.stopCalls.get(), violations);
        require(state.disposeCalls.get() == 1,
                label + " viewer dispose count was " + state.disposeCalls.get(), violations);
        require(state.offEdtSwingCalls.get() == 0,
                label + " viewer Swing disposal ran off EDT "
                        + state.offEdtSwingCalls.get() + " time(s)", violations);
    }

    private static void assertCompletionAfterAttempts(
            List<String> events, String completionEvent, List<String> violations) {
        assertEventAfterAllAttempts(events, completionEvent, violations);
        require(Collections.frequency(events, completionEvent) == 1,
                completionEvent + " was observed "
                        + Collections.frequency(events, completionEvent) + " time(s)", violations);
    }

    private static void assertEventAfterAllAttempts(
            List<String> events, String completionEvent, List<String> violations) {
        int completionIndex = events.indexOf(completionEvent);
        require(completionIndex >= 0,
                completionEvent + " was never observed; events=" + events, violations);
        for (String resource : List.of(
                "player", "stream", "monitor", "unicast-input", "unicast-output",
                "active-chip", "remote-control", "logging-handler-close")) {
            int resourceIndex = events.indexOf(resource);
            require(resourceIndex >= 0,
                    resource + " attempt was absent; events=" + events, violations);
            require(completionIndex > resourceIndex,
                    completionEvent + " preceded " + resource + "; events=" + events,
                    violations);
        }
    }

    private static void require(boolean condition, String message, List<String> violations) {
        if (!condition) {
            violations.add(message);
        }
    }

    private static void assertNoViolations(List<String> violations) {
        assertTrue(String.join("; ", violations), violations.isEmpty());
    }

    private static boolean containsIdentity(Object[] values, Object expected) {
        for (Object value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private static void flushEdt() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeAndWait(() -> {
                // Drain all EDT work queued before this marker.
            });
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static ViewerState state(FakeViewer target) {
        ViewerState state = VIEWER_STATES.get(target);
        if (state == null) {
            throw new AssertionError("unregistered fake viewer");
        }
        return state;
    }

    private static Attempt chipAttempt(TrackingChip chip) {
        Attempt attempt = CHIP_ATTEMPTS.get(chip);
        if (attempt == null) {
            throw new AssertionError("unregistered tracking chip");
        }
        return attempt;
    }

    private static void initializeNullLifecyclePrimitives(Object target, String nameFragment)
            throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getName().toLowerCase().contains(nameFragment.toLowerCase())) {
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
                }
            }
        }
    }

    private static Object getStaticField(Class<?> type, String name) throws Exception {
        Field field = findField(type, name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStaticField(Class<?> type, String name, Object value) throws Exception {
        Field field = findField(type, name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
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

    private static final class ObservedCompletion {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
    }

    private static final class Attempt {
        final String name;
        final List<String> events;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger edtCalls = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);

        Attempt(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        void record() {
            calls.incrementAndGet();
            if (SwingUtilities.isEventDispatchThread()) {
                edtCalls.incrementAndGet();
            }
            events.add(name);
            entered.countDown();
        }
    }

    private static final class Resources {
        final List<String> events = new CopyOnWriteArrayList<>();
        final Attempt player = attempt("player");
        final Attempt stream = attempt("stream");
        final Attempt unicastInputAttempt = attempt("unicast-input");
        final Attempt unicastOutputAttempt = attempt("unicast-output");
        final Attempt chip = attempt("active-chip");
        final Attempt remoteControlAttempt = attempt("remote-control");
        final Attempt handlerClose = attempt("logging-handler-close");
        final TrackingMonitor monitor = new TrackingMonitor(attempt("monitor"));
        final TrackingUnicastInput unicastInput;
        final TrackingUnicastOutput unicastOutput;
        final TrackingRemoteControl remoteControl;
        final TrackingLoggingHandler loggingHandler;

        Resources() throws Exception {
            unicastInput = allocateWithoutConstructor(TrackingUnicastInput.class);
            unicastInput.attempt = unicastInputAttempt;
            unicastOutput = allocateWithoutConstructor(TrackingUnicastOutput.class);
            unicastOutput.attempt = unicastOutputAttempt;
            remoteControl = allocateWithoutConstructor(TrackingRemoteControl.class);
            remoteControl.attempt = remoteControlAttempt;
            loggingHandler = allocateWithoutConstructor(TrackingLoggingHandler.class);
            loggingHandler.attempt = handlerClose;
            loggingHandler.attachedWhenClosed = new AtomicBoolean();
        }

        Attempt attempt(String name) {
            return new Attempt(name, events);
        }
    }

    private static final class ViewerState {
        final Resources resources;
        final TrackingChip chip;
        final String label;
        final AtomicInteger stopCalls = new AtomicInteger();
        final AtomicInteger disposeCalls = new AtomicInteger();
        final AtomicInteger offEdtSwingCalls = new AtomicInteger();

        ViewerState(Resources resources, TrackingChip chip, String label) {
            this.resources = resources;
            this.chip = chip;
            this.label = label;
        }

        void swingCall(String operation) {
            if (!SwingUtilities.isEventDispatchThread()) {
                offEdtSwingCalls.incrementAndGet();
            }
            resources.events.add(label + "-viewer-" + operation);
        }
    }

    private static final class FakeViewer extends AEViewer {
        private FakeViewer() {
            super((JAERViewer) null);
            // Never invoked; JFrame construction is intentionally bypassed.
        }

        @Override
        public void propertyChange(java.beans.PropertyChangeEvent event) {
        }

        @Override
        public AEChip getChip() {
            return state(this).chip;
        }

        @Override
        public AbstractAEPlayer getAePlayer() {
            return aePlayer;
        }

        @Override
        public PlayMode getPlayMode() {
            return PlayMode.WAITING;
        }

        @Override
        public File stopRecording(boolean confirmFilename) {
            if (confirmFilename) {
                throw new AssertionError("final disposal must not request a recording dialog");
            }
            return null;
        }

        @Override
        public void stopMe() {
            ViewerState state = state(this);
            state.stopCalls.incrementAndGet();
            state.swingCall("stop");
        }

        @Override
        public void dispose() {
            ViewerState state = state(this);
            state.disposeCalls.incrementAndGet();
            state.swingCall("dispose");
        }

        @Override
        public void endFilePlaybackOpen() {
            // TrackingPlayer does not project playback state during final close.
        }
    }

    private static final class TrackingPlayer extends AEPlayer {
        final Attempt attempt;
        final TrackingStream stream;
        final AtomicBoolean streamClosed = new AtomicBoolean();

        TrackingPlayer(AEViewer viewer, Attempt attempt, Attempt streamAttempt) {
            super(viewer);
            this.attempt = attempt;
            this.stream = new TrackingStream(streamAttempt);
        }

        @Override
        public void close() throws IOException {
            attempt.record();
            if (streamClosed.compareAndSet(false, true)) {
                stream.proxy.close();
            }
        }
    }

    private static final class TrackingStream {
        final Attempt attempt;
        final AEFileInputStreamInterface proxy;

        TrackingStream(Attempt attempt) {
            this.attempt = attempt;
            proxy = (AEFileInputStreamInterface) java.lang.reflect.Proxy.newProxyInstance(
                    AEFileInputStreamInterface.class.getClassLoader(),
                    new Class<?>[]{AEFileInputStreamInterface.class},
                    (ignoredProxy, method, args) -> switch (method.getName()) {
                        case "close" -> {
                            attempt.record();
                            yield null;
                        }
                        case "toString" -> "TrackingStream";
                        case "hashCode" -> System.identityHashCode(ignoredProxy);
                        case "equals" -> ignoredProxy == args[0];
                        default -> primitiveDefault(method.getReturnType());
                    });
        }
    }

    private static final class TrackingMonitor {
        final Attempt attempt;
        final CountDownLatch release = new CountDownLatch(1);
        final AEMonitorInterface proxy;
        volatile boolean blockClose;
        volatile boolean throwAfterCloseEntered;

        TrackingMonitor(Attempt attempt) {
            this.attempt = attempt;
            proxy = (AEMonitorInterface) java.lang.reflect.Proxy.newProxyInstance(
                    AEMonitorInterface.class.getClassLoader(),
                    new Class<?>[]{AEMonitorInterface.class},
                    (ignoredProxy, method, args) -> switch (method.getName()) {
                        case "isOpen" -> true;
                        case "close" -> {
                            attempt.record();
                            if (blockClose) {
                                awaitUninterruptibly(release);
                            }
                            if (throwAfterCloseEntered) {
                                throw new IllegalStateException("expected monitor-close failure");
                            }
                            yield null;
                        }
                        case "toString" -> "TrackingMonitor";
                        case "hashCode" -> System.identityHashCode(ignoredProxy);
                        case "equals" -> ignoredProxy == args[0];
                        default -> primitiveDefault(method.getReturnType());
                    });
        }

        void releaseClose() {
            release.countDown();
        }
    }

    private static final class TrackingUnicastInput extends AEUnicastInput {
        Attempt attempt;

        private TrackingUnicastInput() {
            super((AEChip) null);
            // Never invoked; construction is bypassed so no socket can be opened.
        }

        @Override
        public void close() {
            attempt.record();
        }
    }

    private static final class TrackingUnicastOutput extends AEUnicastOutput {
        Attempt attempt;

        private TrackingUnicastOutput() {
            // Never invoked; construction is bypassed so no buffers/socket are created.
        }

        @Override
        public void close() {
            attempt.record();
        }
    }

    private static final class TrackingRemoteControl extends RemoteControl {
        Attempt attempt;

        private TrackingRemoteControl() throws SocketException {
            super(0);
            // Never invoked; construction is bypassed so no datagram socket is opened.
        }

        @Override
        public void close() {
            attempt.record();
        }
    }

    private static final class TrackingLoggingHandler extends AEViewerLoggingHandler {
        Attempt attempt;
        AtomicBoolean attachedWhenClosed;

        private TrackingLoggingHandler() {
            super(null);
            // Never invoked; construction is bypassed so no console window is created.
        }

        @Override
        public void publish(LogRecord record) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            attempt.record();
            attachedWhenClosed.set(containsIdentity(ROOT_LOGGER.getHandlers(), this));
        }
    }

    private static final class TrackingChip extends AEChip {
        private TrackingChip() {
            // Never invoked; AEChip construction is intentionally bypassed.
        }

        @Override
        public void cleanup() {
            chipAttempt(this).record();
        }
    }
}
