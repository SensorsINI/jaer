package net.sf.jaer.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.SwingUtilities;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Runtime evidence for the ordering of AEViewer's internal window-closing
 * callback and JAERViewer's subsequently registered removal callback.
 *
 * <p>The resource-owning objects are allocated without invoking constructors.
 * A small AWT {@link Frame}, used only under virtual X, supplies the real
 * ordered {@link WindowListener} dispatch. Cleanup blocks in an inert player so
 * the test can observe whether AEViewer selected a daemon secondary-viewer
 * worker or a non-daemon last-viewer worker before the manager callback removes
 * the closing viewer. Every path that can call {@link System#exit(int)} runs in
 * a forked child JVM.</p>
 */
public class AEViewerWindowCloseOrderingTest {

    private static final String CHILD_ARGUMENT = "--window-close-ordering-child";
    private static final String LAST_MODE = "last-viewer";
    private static final String SECONDARY_MODE = "secondary-viewer";
    private static final long CHILD_TIMEOUT_SECONDS = 10;
    private static final long CHILD_DESTROY_TIMEOUT_SECONDS = 3;
    private static final long LATCH_TIMEOUT_SECONDS = 5;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(
                AEViewerWindowCloseOrderingTest.class);
    }

    @Test(timeout = 20_000L)
    public void oneRegisteredViewerChoosesNonDaemonExitPath() throws Exception {
        requireVirtualDisplay();
        ChildResult result = runChild(LAST_MODE);

        assertFalse("last-viewer child timed out; output:\n" + result.output,
                result.timedOut);
        assertEquals("last-viewer child exit status; output:\n" + result.output,
                0, result.exitCode);
        assertContainsAll(result.output,
                "ORDERED_LISTENER_DISPATCH=internal-before-manager",
                "CLASSIFICATION_OBSERVED_VIEWERS=1",
                "FINAL_CLEANUP_THREAD=AEViewer-FinalDisposal",
                "FINAL_CLEANUP_DAEMON=false",
                "MANAGER_REMAINING_AFTER_DISPATCH=0",
                "LAST_VIEWER_EXIT_PATH_RELEASED");
    }

    @Test(timeout = 20_000L)
    public void closingOneOfTwoChoosesDaemonNonExitPathAndRetainsOther()
            throws Exception {
        requireVirtualDisplay();
        ChildResult result = runChild(SECONDARY_MODE);

        assertFalse("secondary-viewer child timed out; output:\n" + result.output,
                result.timedOut);
        assertEquals("secondary-viewer child exit status; output:\n" + result.output,
                0, result.exitCode);
        assertContainsAll(result.output,
                "ORDERED_LISTENER_DISPATCH=internal-before-manager",
                "CLASSIFICATION_OBSERVED_VIEWERS=2",
                "FINAL_CLEANUP_THREAD=AEViewer-FinalDisposal",
                "FINAL_CLEANUP_DAEMON=false",
                "MANAGER_REMAINING_AFTER_DISPATCH=1",
                "RETAINED_OTHER_VIEWER=true",
                "SECONDARY_NON_EXIT_OBSERVED=true");
    }

    private static void requireVirtualDisplay() {
        Assume.assumeFalse("window-close ordering evidence requires virtual X",
                GraphicsEnvironment.isHeadless());
    }

    private ChildResult runChild(String mode) throws Exception {
        File root = temporaryFolder.newFolder(mode);
        Path home = Files.createDirectory(root.toPath().resolve("home"));
        Path temporary = Files.createDirectory(root.toPath().resolve("tmp"));
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");

        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.add("-Djava.awt.headless=false");
        command.add("-Duser.home=" + home);
        command.add("-Djava.io.tmpdir=" + temporary);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(AEViewerWindowCloseOrderingTest.class.getName());
        command.add(CHILD_ARGUMENT);
        command.add(mode);

        Process child = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        OutputCollector output = new OutputCollector(child.getInputStream());
        output.start();

        boolean exited = child.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!exited) {
            child.destroyForcibly();
            child.waitFor(CHILD_DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        output.awaitEnd(CHILD_DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return new ChildResult(!exited,
                exited ? child.exitValue() : Integer.MIN_VALUE, output.text());
    }

    private static void assertContainsAll(String output, String... markers) {
        for (String marker : markers) {
            assertTrue("missing runtime marker " + marker + "; output:\n" + output,
                    output.contains(marker));
        }
    }

    /** Child entry point. The JUnit parent never exits. */
    public static void main(String[] args) {
        if (args.length != 2 || !CHILD_ARGUMENT.equals(args[0])) {
            System.err.println("This entry point is only for the forked window-close test child");
            return;
        }

        try {
            childRequire(!GraphicsEnvironment.isHeadless(),
                    "child requires the inherited virtual X display");
            if (LAST_MODE.equals(args[1])) {
                runLastViewerChild();
                throw new AssertionError("last-viewer production path returned without System.exit");
            }
            if (SECONDARY_MODE.equals(args[1])) {
                runSecondaryViewerChild();
                System.exit(0);
            }
            throw new IllegalArgumentException("unknown child mode " + args[1]);
        } catch (Throwable failure) {
            System.err.println("CHILD_HARNESS_FAILURE: " + failure);
            failure.printStackTrace(System.err);
            System.err.flush();
            System.exit(70);
        }
    }

    private static void runLastViewerChild() throws Exception {
        ChildState state = createState(1);
        dispatchOrderedClose(state);
        awaitCleanupEntry(state);

        try {
            assertCommonClassification(state, 1, false);
            childRequire(state.manager.getViewers().isEmpty(),
                    "manager retained the only viewer after removal callback");
            printCommonEvidence(state);
            System.out.println("LAST_VIEWER_EXIT_PATH_RELEASED");
            System.out.flush();
        } finally {
            state.cleanupRelease.countDown();
        }

        // finishFinalDisposalOffEdt must terminate this child. The parent owns
        // the deadline if that production exit does not happen.
        while (true) {
            Thread.sleep(1_000L);
        }
    }

    private static void runSecondaryViewerChild() throws Exception {
        ChildState state = createState(2);
        dispatchOrderedClose(state);
        awaitCleanupEntry(state);

        try {
            assertCommonClassification(state, 2, false);
            childRequire(state.manager.getViewers().size() == 1,
                    "secondary close left " + state.manager.getViewers().size()
                    + " registered viewers instead of one");
            childRequire(state.manager.getViewers().get(0) == state.retained,
                    "secondary close did not retain the other viewer by identity");
        } finally {
            state.cleanupRelease.countDown();
        }

        childRequire(state.chipCleanup.await(
                LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "secondary cleanup did not reach its terminal inert resource");
        Thread.sleep(250L);

        printCommonEvidence(state);
        System.out.println("RETAINED_OTHER_VIEWER=true");
        System.out.println("SECONDARY_NON_EXIT_OBSERVED=true");
        System.out.flush();
    }

    private static ChildState createState(int registeredViewerCount) throws Exception {
        JAERViewer manager = allocateWithoutConstructor(JAERViewer.class);
        TrackingViewer closing = allocateWithoutConstructor(TrackingViewer.class);
        TrackingViewer retained = allocateWithoutConstructor(TrackingViewer.class);
        BlockingPlayer player = allocateWithoutConstructor(BlockingPlayer.class);
        TrackingChip chip = allocateWithoutConstructor(TrackingChip.class);
        ChildState state = new ChildState(
                manager, closing, retained, player, chip, registeredViewerCount);

        closing.state = state;
        retained.state = state;
        player.state = state;
        chip.state = state;
        closing.aePlayer = player;

        ArrayList<AEViewer> viewers = new ArrayList<>();
        viewers.add(closing);
        if (registeredViewerCount == 2) {
            viewers.add(retained);
        }
        setField(manager, "viewers", viewers);
        setField(manager, "syncEnableButtons", new ArrayList<>());
        setField(closing, "jaerViewer", manager);
        setField(closing, "chip", chip);
        setField(closing, "exitWatchdogArmed", new AtomicBoolean());
        return state;
    }

    private static void dispatchOrderedClose(ChildState state) throws Exception {
        AtomicReference<Throwable> dispatchFailure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Frame dispatchFrame = new Frame("AEViewer window-close ordering evidence");
            WindowListener internalCallback = new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    state.events.add("internal-enter");
                    try {
                        invokeFormWindowClosing(state.closing, event);
                    } catch (Throwable failure) {
                        dispatchFailure.compareAndSet(null, failure);
                    }
                    state.events.add("internal-return");
                }
            };
            WindowListener managerRemovalCallback = new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    state.events.add("manager-enter");
                    try {
                        state.manager.removeViewer(state.closing);
                    } catch (Throwable failure) {
                        dispatchFailure.compareAndSet(null, failure);
                    }
                    state.events.add("manager-return");
                }
            };

            try {
                // This is the production registration order: initComponents()
                // installs the internal callback before JAERViewer.addViewer()
                // installs its removal callback.
                dispatchFrame.addWindowListener(internalCallback);
                dispatchFrame.addWindowListener(managerRemovalCallback);
                WindowListener[] registered = dispatchFrame.getWindowListeners();
                childRequire(registered.length == 2,
                        "dispatch frame had " + registered.length + " window listeners");
                childRequire(registered[0] == internalCallback,
                        "internal callback was not the first registered listener");
                childRequire(registered[1] == managerRemovalCallback,
                        "manager callback was not the second registered listener");

                state.events.add("dispatch-enter");
                dispatchFrame.dispatchEvent(new WindowEvent(
                        dispatchFrame, WindowEvent.WINDOW_CLOSING));
                state.events.add("dispatch-return");
            } catch (Throwable failure) {
                dispatchFailure.compareAndSet(null, failure);
            } finally {
                dispatchFrame.dispose();
            }
        });
        rethrow(dispatchFailure.get());
    }

    private static void awaitCleanupEntry(ChildState state) throws Exception {
        childRequire(state.cleanupEntered.await(
                LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "AEViewer final-cleanup worker did not enter the inert player");
    }

    private static void assertCommonClassification(
            ChildState state, int expectedViewers, boolean expectedDaemon) {
        childRequire(state.registeredAtStop.get() == expectedViewers,
                "internal EDT callback observed " + state.registeredAtStop.get()
                + " viewers instead of " + expectedViewers);
        childRequire(Boolean.valueOf(expectedDaemon).equals(state.cleanupDaemon.get()),
                "final-cleanup daemon flag was " + state.cleanupDaemon.get()
                + " instead of " + expectedDaemon);
        childRequire("AEViewer-FinalDisposal".equals(state.cleanupThreadName.get()),
                "unexpected final-cleanup thread " + state.cleanupThreadName.get());
        childRequire(state.stopOffEdt.get() == 0,
                "internal stop callback ran off the EDT");
        childRequire(state.disposeOffEdt.get() == 0,
                "internal dispose callback ran off the EDT");
        childRequire(state.stopCalls.get() == 1,
                "internal stop callback count was " + state.stopCalls.get());
        childRequire(state.disposeCalls.get() == 1,
                "internal dispose callback count was " + state.disposeCalls.get());
        assertEventOrder(state.events,
                "dispatch-enter",
                "internal-enter",
                "viewer-stop",
                "viewer-dispose",
                "internal-return",
                "manager-enter",
                "manager-return",
                "dispatch-return");
    }

    private static void printCommonEvidence(ChildState state) {
        System.out.println("ORDERED_LISTENER_DISPATCH=internal-before-manager");
        System.out.println("CLASSIFICATION_OBSERVED_VIEWERS="
                + state.registeredAtStop.get());
        System.out.println("FINAL_CLEANUP_THREAD=" + state.cleanupThreadName.get());
        System.out.println("FINAL_CLEANUP_DAEMON=" + state.cleanupDaemon.get());
        System.out.println("MANAGER_REMAINING_AFTER_DISPATCH="
                + state.manager.getViewers().size());
    }

    private static void assertEventOrder(List<String> events, String... expected) {
        int previous = -1;
        for (String event : expected) {
            int index = events.indexOf(event);
            childRequire(index >= 0, "missing event " + event + "; events=" + events);
            childRequire(index > previous,
                    "event " + event + " was out of order; events=" + events);
            previous = index;
        }
    }

    private static void invokeFormWindowClosing(AEViewer viewer, WindowEvent event)
            throws Exception {
        Method method = AEViewer.class.getDeclaredMethod(
                "formWindowClosing", WindowEvent.class);
        method.setAccessible(true);
        try {
            method.invoke(viewer, event);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new AssertionError(cause);
        }
    }

    private static void childRequire(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new AssertionError(failure);
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

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
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
        final TrackingViewer closing;
        final TrackingViewer retained;
        final BlockingPlayer player;
        final TrackingChip chip;
        final int initialViewerCount;
        final List<String> events = new CopyOnWriteArrayList<>();
        final CountDownLatch cleanupEntered = new CountDownLatch(1);
        final CountDownLatch cleanupRelease = new CountDownLatch(1);
        final CountDownLatch chipCleanup = new CountDownLatch(1);
        final AtomicReference<Boolean> cleanupDaemon = new AtomicReference<>();
        final AtomicReference<String> cleanupThreadName = new AtomicReference<>();
        final AtomicInteger registeredAtStop = new AtomicInteger(-1);
        final AtomicInteger stopCalls = new AtomicInteger();
        final AtomicInteger stopOffEdt = new AtomicInteger();
        final AtomicInteger disposeCalls = new AtomicInteger();
        final AtomicInteger disposeOffEdt = new AtomicInteger();

        ChildState(JAERViewer manager, TrackingViewer closing,
                TrackingViewer retained, BlockingPlayer player,
                TrackingChip chip, int initialViewerCount) {
            this.manager = manager;
            this.closing = closing;
            this.retained = retained;
            this.player = player;
            this.chip = chip;
            this.initialViewerCount = initialViewerCount;
        }
    }

    private static final class TrackingViewer extends AEViewer {

        ChildState state;

        private TrackingViewer() {
            super((JAERViewer) null);
            // Never invoked: JFrame and AEViewer construction is bypassed.
        }

        @Override
        public void propertyChange(java.beans.PropertyChangeEvent event) {
        }

        @Override
        public void stopMe() {
            state.stopCalls.incrementAndGet();
            state.registeredAtStop.set(state.manager.getViewers().size());
            if (!SwingUtilities.isEventDispatchThread()) {
                state.stopOffEdt.incrementAndGet();
            }
            state.events.add("viewer-stop");
        }

        @Override
        public void dispose() {
            state.disposeCalls.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                state.disposeOffEdt.incrementAndGet();
            }
            state.events.add("viewer-dispose");
        }

        @Override
        public JCheckBoxMenuItem getSyncEnabledCheckBoxMenuItem() {
            return null;
        }

        @Override
        public void setTitleAccordingToState(boolean force) {
            // JAERViewer.removeViewer refreshes the retained viewer's title.
        }

        @Override
        public String toString() {
            return this == state.closing ? "ClosingTrackingViewer" : "RetainedTrackingViewer";
        }
    }

    private static final class BlockingPlayer extends AEPlayer {

        ChildState state;

        private BlockingPlayer() {
            super(null);
            // Never invoked: player construction is bypassed.
        }

        @Override
        public void close() throws IOException {
            Thread current = Thread.currentThread();
            state.cleanupThreadName.set(current.getName());
            state.cleanupDaemon.set(current.isDaemon());
            state.events.add("cleanup-player-enter");
            state.cleanupEntered.countDown();
            awaitUninterruptibly(state.cleanupRelease);
            state.events.add("cleanup-player-return");
        }
    }

    private static final class TrackingChip extends AEChip {

        ChildState state;

        private TrackingChip() {
            // Never invoked: chip construction is bypassed.
        }

        @Override
        public void cleanup() {
            state.events.add("chip-cleanup");
            state.chipCleanup.countDown();
        }
    }

    private static final class ChildResult {

        final boolean timedOut;
        final int exitCode;
        final String output;

        ChildResult(boolean timedOut, int exitCode, String output) {
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static final class OutputCollector {

        private final InputStream input;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CountDownLatch ended = new CountDownLatch(1);
        private Thread reader;

        OutputCollector(InputStream input) {
            this.input = input;
        }

        void start() {
            reader = new Thread(() -> {
                try (InputStream stream = input) {
                    byte[] buffer = new byte[4_096];
                    int count;
                    while ((count = stream.read(buffer)) >= 0) {
                        synchronized (bytes) {
                            bytes.write(buffer, 0, count);
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
            }, "window-close-child-output");
            reader.setDaemon(true);
            reader.start();
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
