package net.sf.jaer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListenerProxy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Process-level regression tests for the real {@link JAERViewer} shutdown hook.
 *
 * <p>Only child JVMs initiate VM shutdown. They use constructor bypass for the
 * manager, viewers, and chip, then instantiate the anonymous {@link Thread}
 * implementation compiled from {@code JAERViewer}'s constructor. This executes
 * production hook bytecode without constructing an {@code AEViewer}, starting a
 * hardware factory, or touching USB, serial, or network devices.</p>
 */
public class JAERViewerShutdownHookProcessTest {

    private static final int REQUIRED_JAVA_FEATURE = 26;
    private static final long CHILD_STARTUP_TIMEOUT_SECONDS = 12;
    private static final long SHUTDOWN_DEADLINE_MILLIS = 3_000L;
    private static final long CHILD_DESTROY_TIMEOUT_SECONDS = 3;

    private static final String CHILD_ARGUMENT = "--shutdown-hook-child";
    private static final String EXIT_ON_EDT_MODE = "exit-on-edt";
    private static final String NON_EDT_HOOK_MODE = "non-edt-hook";
    private static final String EDT_EXIT_MARKER = "EDT_EXIT_BEGIN";
    private static final String NON_EDT_HOOK_MARKER = "NON_EDT_HOOK_BEGIN";

    private static final AtomicInteger VIEWER_STOP_CALLS = new AtomicInteger();
    private static final AtomicInteger VIEWER_DISPOSE_CALLS = new AtomicInteger();
    private static volatile AEChip trackingChip;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(
                JAERViewerShutdownHookProcessTest.class);
    }

    @Test(timeout = 25_000L)
    public void exitInitiatedOnEdtTerminatesWithinDeadline() throws Exception {
        requireJdk26();

        ChildResult result = runChild(EXIT_ON_EDT_MODE, EDT_EXIT_MARKER);

        assertTrue("child did not reach EDT-initiated exit; output:\n" + result.output,
                result.startMarkerSeen);
        assertFalse("production shutdown hook exceeded the "
                + SHUTDOWN_DEADLINE_MILLIS + " ms deadline after System.exit was "
                + "initiated on the EDT; the child was forcibly destroyed. Runtime "
                + "evidence should show JAERViewer-shutdown joining requestShutdown "
                + "while AWT-EventQueue is blocked in System.exit:\n" + result.output,
                result.timedOut);
        assertEquals("child exit status; output:\n" + result.output, 0, result.exitCode);
        assertContainsAll(result.output,
                "PRODUCTION_HOOK_INSTALLED",
                "VIEWER_STOP_ATTEMPTED",
                "VIEWER_DISPOSE_ATTEMPTED",
                "WINDOW_SAVER_SAVE_ATTEMPTED",
                "LOG_HANDLER_CLOSE_ATTEMPTED",
                "JAERViewer shutdown hook - end of shutdown");
    }

    @Test(timeout = 25_000L)
    public void ordinaryNonEdtHookCleanupIsBoundedAndAttemptsRegisteredResources()
            throws Exception {
        requireJdk26();

        ChildResult result = runChild(NON_EDT_HOOK_MODE, NON_EDT_HOOK_MARKER);

        assertTrue("child did not start the ordinary non-EDT hook path; output:\n"
                + result.output, result.startMarkerSeen);
        assertFalse("ordinary non-EDT production hook exceeded the "
                + SHUTDOWN_DEADLINE_MILLIS + " ms cleanup deadline; the child was "
                + "forcibly destroyed:\n" + result.output, result.timedOut);
        assertEquals("child exit status; output:\n" + result.output, 0, result.exitCode);
        assertContainsAll(result.output,
                "PRODUCTION_HOOK_INSTALLED",
                "VIEWER_STOP_ATTEMPTED",
                "VIEWER_DISPOSE_ATTEMPTED",
                "WINDOW_SAVER_SAVE_ATTEMPTED",
                "LOG_HANDLER_CLOSE_ATTEMPTED",
                "NON_EDT_REGISTERED_RESOURCE_CLEANUP_COMPLETE");
    }

    private static void requireJdk26() {
        assertEquals("this regression must run on JDK 26",
                REQUIRED_JAVA_FEATURE, Runtime.version().feature());
    }

    private ChildResult runChild(String mode, String startMarker) throws Exception {
        File childRoot = temporaryFolder.newFolder(mode);
        Path home = Files.createDirectory(childRoot.toPath().resolve("home"));
        Path preferences = Files.createDirectory(childRoot.toPath().resolve("preferences"));
        Path temporary = Files.createDirectory(childRoot.toPath().resolve("tmp"));
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");

        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.add("-Djava.awt.headless=true");
        command.add("-Duser.home=" + home);
        command.add("-Djava.util.prefs.userRoot=" + preferences);
        command.add("-Djava.io.tmpdir=" + temporary);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(JAERViewerShutdownHookProcessTest.class.getName());
        command.add(CHILD_ARGUMENT);
        command.add(mode);

        Process child = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        ChildOutput output = new ChildOutput(child.getInputStream(), startMarker);
        output.start();

        boolean markerSeen = output.awaitMarker(
                CHILD_STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!markerSeen) {
            boolean alreadyExited = child.waitFor(100L, TimeUnit.MILLISECONDS);
            if (!alreadyExited) {
                child.destroyForcibly();
                child.waitFor(CHILD_DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            output.awaitEnd(CHILD_DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new ChildResult(false, !alreadyExited,
                    alreadyExited ? child.exitValue() : Integer.MIN_VALUE,
                    output.text());
        }

        boolean exited = child.waitFor(SHUTDOWN_DEADLINE_MILLIS, TimeUnit.MILLISECONDS);
        if (!exited) {
            child.destroyForcibly();
            child.waitFor(CHILD_DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        output.awaitEnd(CHILD_DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return new ChildResult(true, !exited,
                exited ? child.exitValue() : Integer.MIN_VALUE, output.text());
    }

    private static void assertContainsAll(String output, String... markers) {
        for (String marker : markers) {
            assertTrue("missing runtime marker " + marker + "; output:\n" + output,
                    output.contains(marker));
        }
    }

    /** Child entry point; the JUnit parent itself never exits. */
    public static void main(String[] args) {
        if (args.length != 2 || !CHILD_ARGUMENT.equals(args[0])) {
            System.err.println("This entry point is only for the forked shutdown-hook test child");
            return;
        }

        try {
            if (Runtime.version().feature() != REQUIRED_JAVA_FEATURE) {
                throw new AssertionError("child requires JDK 26 but was "
                        + Runtime.version());
            }
            ChildState state = createChildState();
            if (EXIT_ON_EDT_MODE.equals(args[1])) {
                runExitOnEdtChild(state);
            } else if (NON_EDT_HOOK_MODE.equals(args[1])) {
                runNonEdtHookChild(state);
            } else {
                throw new IllegalArgumentException("unknown child mode " + args[1]);
            }
        } catch (Throwable failure) {
            System.err.println("CHILD_HARNESS_FAILURE: " + failure);
            failure.printStackTrace(System.err);
            System.err.flush();
            System.exit(70);
        }
    }

    private static ChildState createChildState() throws Exception {
        VIEWER_STOP_CALLS.set(0);
        VIEWER_DISPOSE_CALLS.set(0);

        Preferences childPreferences = Preferences.userRoot().node(
                "/net/sf/jaer/test/JAERViewerShutdownHookProcessTest/"
                + UUID.randomUUID());
        Logger childLogger = Logger.getLogger(
                "net.sf.jaer.test.JAERViewerShutdownHookProcessTest."
                + UUID.randomUUID());
        childLogger.setUseParentHandlers(false);
        TrackingHandler handler = new TrackingHandler();
        childLogger.addHandler(handler);

        JAERViewer.prefs = childPreferences;
        JAERViewer.log = childLogger;
        JaerConstants.skipPreferenceWriteOnExit = false;

        JAERViewer manager = allocateWithoutConstructor(JAERViewer.class);
        TrackingViewer viewer = allocateWithoutConstructor(TrackingViewer.class);
        trackingChip = allocateWithoutConstructor(TrackingChip.class);
        ArrayList<AEViewer> viewers = new ArrayList<>();
        viewers.add(viewer);
        setField(manager, "viewers", viewers);
        setField(manager, "syncEnableButtons", new ArrayList<>());
        setField(manager, "shutdownCompletion", new AtomicReference<CompletableFuture<Void>>());

        TrackingWindowSaver saver = new TrackingWindowSaver(childPreferences);
        setField(manager, "windowSaver", saver);
        Toolkit.getDefaultToolkit().addAWTEventListener(
                saver, AWTEvent.WINDOW_EVENT_MASK);

        Thread hook = instantiateProductionShutdownHook(manager);
        hook.setName("JAERViewer-shutdown");
        setField(manager, "shutdownHook", hook);
        Runtime.getRuntime().addShutdownHook(hook);
        System.out.println("PRODUCTION_HOOK_INSTALLED=" + hook.getClass().getName());
        System.out.flush();
        return new ChildState(manager, hook, saver, handler, childLogger);
    }

    private static void runExitOnEdtChild(ChildState state) throws Exception {
        startDeadlockEvidencePrinter();
        CountDownLatch exitTaskStarted = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            System.out.println(EDT_EXIT_MARKER);
            System.out.flush();
            exitTaskStarted.countDown();
            System.exit(0);
        });
        if (!exitTaskStarted.await(CHILD_STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("EDT exit task did not start");
        }

        // A correct implementation terminates the child. The parent owns the
        // deadline and forcibly destroys only this child if shutdown deadlocks.
        while (true) {
            Thread.sleep(1_000L);
        }
    }

    private static void runNonEdtHookChild(ChildState state) throws Exception {
        System.out.println(NON_EDT_HOOK_MARKER);
        System.out.flush();
        state.hook.start();
        state.hook.join(SHUTDOWN_DEADLINE_MILLIS);
        if (state.hook.isAlive()) {
            throw new AssertionError("production hook remained alive past cleanup deadline");
        }

        assertChildCondition(VIEWER_STOP_CALLS.get() == 1,
                "viewer stop attempts=" + VIEWER_STOP_CALLS.get());
        assertChildCondition(VIEWER_DISPOSE_CALLS.get() == 1,
                "viewer dispose attempts=" + VIEWER_DISPOSE_CALLS.get());
        assertChildCondition(state.saver.saveCalls.get() == 1,
                "WindowSaver save attempts=" + state.saver.saveCalls.get());
        assertChildCondition(state.handler.closeCalls.get() == 1,
                "log handler close attempts=" + state.handler.closeCalls.get());
        assertChildCondition(state.manager.getViewers().isEmpty(),
                "manager retained registered viewers");
        assertChildCondition(!containsIdentity(
                Toolkit.getDefaultToolkit().getAWTEventListeners(), state.saver),
                "WindowSaver remained globally registered");
        assertChildCondition(!containsIdentity(state.logger.getHandlers(), state.handler),
                "closed log handler remained registered");
        assertChildCondition(!Runtime.getRuntime().removeShutdownHook(state.hook),
                "production hook remained registered after ordinary cleanup");

        System.out.println("NON_EDT_REGISTERED_RESOURCE_CLEANUP_COMPLETE");
        System.out.flush();
        System.exit(0);
    }

    private static Thread instantiateProductionShutdownHook(JAERViewer manager)
            throws Exception {
        for (int suffix = 1; suffix <= 64; suffix++) {
            Class<?> candidate;
            try {
                candidate = Class.forName(JAERViewer.class.getName() + "$" + suffix);
            } catch (ClassNotFoundException absent) {
                continue;
            }
            if (!Thread.class.isAssignableFrom(candidate)) {
                continue;
            }
            for (Constructor<?> constructor : candidate.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 1 && parameters[0] == JAERViewer.class) {
                    constructor.setAccessible(true);
                    return (Thread) constructor.newInstance(manager);
                }
            }
        }
        throw new AssertionError("NO_SAFE_PRODUCTION_HOOK_RUNTIME_SEAM: no compiled "
                + "JAERViewer constructor-owned Thread could be instantiated without "
                + "running hardware-capable constructors");
    }

    private static void startDeadlockEvidencePrinter() {
        Thread evidence = new Thread(() -> {
            try {
                Thread.sleep(750L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            System.out.println("DEADLOCK_THREAD_DUMP_BEGIN");
            Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
            traces.entrySet().stream()
                    .filter(entry -> entry.getKey().getName().startsWith("AWT-EventQueue")
                    || entry.getKey().getName().equals("JAERViewer-shutdown"))
                    .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                    .forEach(entry -> {
                        Thread thread = entry.getKey();
                        System.out.println("THREAD " + thread.getName()
                                + " state=" + thread.getState());
                        for (StackTraceElement frame : entry.getValue()) {
                            System.out.println("  at " + frame);
                        }
                    });
            System.out.println("DEADLOCK_THREAD_DUMP_END");
            System.out.flush();
        }, "shutdown-hook-deadlock-evidence");
        evidence.setDaemon(true);
        evidence.start();
    }

    private static void assertChildCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
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

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException absent) {
                // Try the superclass.
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

    private static final class ChildState {

        final JAERViewer manager;
        final Thread hook;
        final TrackingWindowSaver saver;
        final TrackingHandler handler;
        final Logger logger;

        ChildState(JAERViewer manager, Thread hook, TrackingWindowSaver saver,
                TrackingHandler handler, Logger logger) {
            this.manager = manager;
            this.hook = hook;
            this.saver = saver;
            this.handler = handler;
            this.logger = logger;
        }
    }

    private static final class TrackingViewer extends AEViewer {

        private TrackingViewer() {
            super((JAERViewer) null);
        }

        @Override
        public AEChip getChip() {
            return trackingChip;
        }

        @Override
        public void stopMe() {
            VIEWER_STOP_CALLS.incrementAndGet();
            System.out.println("VIEWER_STOP_ATTEMPTED");
            System.out.flush();
        }

        @Override
        public void dispose() {
            VIEWER_DISPOSE_CALLS.incrementAndGet();
            System.out.println("VIEWER_DISPOSE_ATTEMPTED");
            System.out.flush();
        }
    }

    private static final class TrackingChip extends AEChip {

        private TrackingChip() {
        }
    }

    private static final class TrackingWindowSaver extends WindowSaver {

        final AtomicInteger saveCalls = new AtomicInteger();

        TrackingWindowSaver(Preferences preferences) {
            super(new Object(), preferences);
        }

        @Override
        public void saveSettings() {
            saveCalls.incrementAndGet();
            System.out.println("WINDOW_SAVER_SAVE_ATTEMPTED");
            System.out.flush();
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
            System.out.println("LOG_HANDLER_CLOSE_ATTEMPTED");
            System.out.flush();
        }
    }

    private static final class ChildResult {

        final boolean startMarkerSeen;
        final boolean timedOut;
        final int exitCode;
        final String output;

        ChildResult(boolean startMarkerSeen, boolean timedOut,
                int exitCode, String output) {
            this.startMarkerSeen = startMarkerSeen;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static final class ChildOutput {

        private final InputStream input;
        private final String marker;
        private final CountDownLatch markerSeen = new CountDownLatch(1);
        private final CountDownLatch ended = new CountDownLatch(1);
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Thread reader;

        ChildOutput(InputStream input, String marker) {
            this.input = input;
            this.marker = marker;
        }

        void start() {
            reader = new Thread(() -> {
                try (InputStream stream = input) {
                    byte[] buffer = new byte[4_096];
                    int count;
                    while ((count = stream.read(buffer)) >= 0) {
                        synchronized (bytes) {
                            bytes.write(buffer, 0, count);
                            if (bytes.toString(StandardCharsets.UTF_8).contains(marker)) {
                                markerSeen.countDown();
                            }
                        }
                    }
                } catch (IOException failure) {
                    synchronized (bytes) {
                        try {
                            bytes.write(("\nOUTPUT_READER_FAILURE: " + failure + "\n")
                                    .getBytes(StandardCharsets.UTF_8));
                        } catch (IOException impossible) {
                            throw new AssertionError(impossible);
                        }
                    }
                } finally {
                    ended.countDown();
                }
            }, "shutdown-hook-child-output");
            reader.setDaemon(true);
            reader.start();
        }

        boolean awaitMarker(long timeout, TimeUnit unit) throws InterruptedException {
            return markerSeen.await(timeout, unit);
        }

        void awaitEnd(long timeout, TimeUnit unit) throws InterruptedException {
            ended.await(timeout, unit);
        }

        String text() {
            synchronized (bytes) {
                return bytes.toString(StandardCharsets.UTF_8);
            }
        }
    }
}
