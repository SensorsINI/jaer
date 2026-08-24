package net.sf.jaer.graphics;

import static org.junit.Assert.assertTrue;

import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.aedat4.Aedat4Lz4Rerecorder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance tests for asynchronous playback-open ownership. The viewer and
 * chip constructors are deliberately bypassed: these tests exercise the real
 * {@link AEPlayer} worker/EDT path without creating a desktop or touching
 * hardware.
 */
public class AEPlayerOpenGenerationTest {

    private static final long TIMEOUT_SECONDS = 5;

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(AEPlayerOpenGenerationTest.class);
    }

    private static final Map<FakeViewer, ViewerState> VIEWERS
            = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ControlledChip, ChipState> CHIPS
            = Collections.synchronizedMap(new IdentityHashMap<>());

    private FakeViewer viewer;
    private ViewerState viewerState;
    private ControlledChip chip;
    private AEPlayer player;
    private Preferences preferenceNode;
    private final List<ControlledOpen> opens = new ArrayList<>();
    private final List<File> files = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        System.setProperty("java.awt.headless", "true");

        viewer = allocateWithoutConstructor(FakeViewer.class);
        chip = allocateWithoutConstructor(ControlledChip.class);
        FakePlayerControls controls = allocateWithoutConstructor(FakePlayerControls.class);
        FakeRenderer renderer = allocateWithoutConstructor(FakeRenderer.class);

        viewerState = new ViewerState(chip, controls);
        VIEWERS.put(viewer, viewerState);
        CHIPS.put(chip, new ChipState(renderer));

        preferenceNode = Preferences.userRoot().node(
                "/net/sf/jaer/test/AEPlayerOpenGenerationTest/" + UUID.randomUUID());
        viewer.prefs = preferenceNode;
        player = new AEPlayer(viewer);
        viewerState.player = player;
    }

    @After
    public void tearDown() throws Exception {
        for (ControlledOpen open : opens) {
            open.release();
        }
        for (ControlledOpen open : opens) {
            open.returned.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        flushEdt();

        // Baseline failures can strand a stream that is no longer active.
        // Close every remaining fake directly so one RED test cannot affect the next.
        for (ControlledOpen open : opens) {
            if (open.stream.closeCalls.get() == 0) {
                open.stream.proxy.close();
            }
        }
        player.aeInputStream = null;

        VIEWERS.remove(viewer);
        CHIPS.remove(chip);
        if (preferenceNode != null) {
            preferenceNode.removeNode();
        }
        for (File file : files) {
            file.delete();
        }
        flushEdt();
    }

    @Test(timeout = 10000)
    public void newerThenOlderCompletionKeepsTheNewerStream() throws Exception {
        ControlledOpen older = newOpen("older");
        ControlledOpen newer = newOpen("newer");
        startAndAwait(older);
        startAndAwait(newer);

        newer.release();
        awaitDisposition(newer);
        older.release();
        awaitDisposition(older);
        flushEdt();

        List<String> violations = new ArrayList<>();
        require(player.getAEInputStream() == newer.stream.proxy,
                "older completion replaced the newer stream", violations);
        require(older.stream.closeCalls.get() == 1,
                "stale older stream close count was " + older.stream.closeCalls.get(), violations);
        require(newer.stream.closeCalls.get() == 0,
                "active newer stream was closed " + newer.stream.closeCalls.get() + " times", violations);
        require(newer.file.equals(viewerState.inputFile),
                "viewer input file was not the newer request", violations);
        require(viewerState.offEdtUiCalls.get() == 0,
                "playback completion updated the viewer off the EDT", violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 10000)
    public void olderThenNewerCompletionRejectsTheOlderWithoutClearingNewerOpenState() throws Exception {
        ControlledOpen older = newOpen("older");
        ControlledOpen newer = newOpen("newer");
        startAndAwait(older);
        startAndAwait(newer);

        older.release();
        awaitDisposition(older);
        flushEdt();
        boolean newerStillOpening = viewerState.playbackOpen;
        boolean staleWasInstalled = player.getAEInputStream() == older.stream.proxy;

        newer.release();
        awaitDisposition(newer);
        flushEdt();

        List<String> violations = new ArrayList<>();
        require(newerStillOpening,
                "stale completion cleared the newer playback-open state", violations);
        require(!staleWasInstalled,
                "stale older stream was installed while the newer request was pending", violations);
        require(older.stream.closeCalls.get() == 1,
                "stale older stream close count was " + older.stream.closeCalls.get(), violations);
        require(player.getAEInputStream() == newer.stream.proxy,
                "newer completion did not install the newer stream", violations);
        require(newer.stream.closeCalls.get() == 0,
                "active newer stream was closed " + newer.stream.closeCalls.get() + " times", violations);
        require(viewerState.offEdtUiCalls.get() == 0,
                "playback completion updated the viewer off the EDT", violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 10000)
    public void replacingAnActiveStreamClosesItExactlyOnce() throws Exception {
        ControlledOpen first = newOpen("first-active");
        startAndAwait(first);
        first.release();
        awaitDisposition(first);

        ControlledOpen replacement = newOpen("replacement");
        startAndAwait(replacement);
        replacement.release();
        awaitDisposition(replacement);
        flushEdt();

        List<String> violations = new ArrayList<>();
        require(first.stream.closeCalls.get() == 1,
                "replaced stream close count was " + first.stream.closeCalls.get(), violations);
        require(player.getAEInputStream() == replacement.stream.proxy,
                "replacement stream was not active", violations);
        require(replacement.stream.closeCalls.get() == 0,
                "active replacement stream was closed " + replacement.stream.closeCalls.get() + " times", violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 10000)
    public void closeCancelsPendingOpenAndRejectsItsLateResult() throws Exception {
        ControlledOpen pending = newOpen("pending-at-close");
        startAndAwait(pending);

        Throwable firstCloseFailure = closeOnEdt();
        Throwable secondCloseFailure = closeOnEdt();
        boolean cancellationObserved = pending.interrupted.await(1, TimeUnit.SECONDS);

        // The fake deliberately ignores interruption and returns a stream. A
        // closed player must still reject and close this late result once.
        pending.release();
        assertTrue("pending fake open did not return",
                pending.returned.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        awaitDisposition(pending);
        flushEdt();

        List<String> violations = new ArrayList<>();
        require(firstCloseFailure == null,
                "first close threw " + firstCloseFailure, violations);
        require(secondCloseFailure == null,
                "idempotent second close threw " + secondCloseFailure, violations);
        require(cancellationObserved,
                "close did not interrupt/cancel the running open task", violations);
        require(pending.stream.closeCalls.get() == 1,
                "late stream close count was " + pending.stream.closeCalls.get(), violations);
        require(player.getAEInputStream() == null,
                "late completion installed a stream after close", violations);
        require(!viewerState.playbackOpen,
                "close left playback-open state set", violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 10000)
    public void repeatedCloseClosesTheActiveStreamExactlyOnce() throws Exception {
        ControlledOpen active = newOpen("active-at-close");
        startAndAwait(active);
        active.release();
        awaitDisposition(active);
        flushEdt();

        Throwable firstCloseFailure = closeOnEdt();
        Throwable secondCloseFailure = closeOnEdt();
        flushEdt();

        List<String> violations = new ArrayList<>();
        require(firstCloseFailure == null,
                "first close threw " + firstCloseFailure, violations);
        require(secondCloseFailure == null,
                "idempotent second close threw " + secondCloseFailure, violations);
        require(active.stream.closeCalls.get() == 1,
                "active stream close count was " + active.stream.closeCalls.get(), violations);
        require(player.getAEInputStream() == null,
                "close retained the active stream", violations);
        assertNoViolations(violations);
    }

    private ControlledOpen newOpen(String label) throws IOException {
        File file = File.createTempFile("aeplayer-" + label + "-", ".dat");
        files.add(file);
        ControlledOpen open = new ControlledOpen(file, new TrackingStream(label));
        opens.add(open);
        chipState(chip).opens.put(file, open);
        return open;
    }

    private void startAndAwait(ControlledOpen open) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                player.startPlayback(open.file);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        rethrow(failure.get());
        assertTrue("fake open did not start for " + open.file,
                open.entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private Throwable closeOnEdt() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                player.close();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        return failure.get();
    }

    private void awaitDisposition(ControlledOpen open) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            flushEdt();
            if (player.getAEInputStream() == open.stream.proxy
                    || open.stream.closeCalls.get() > 0) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("open result was neither installed nor closed: " + open.file);
    }

    private static void flushEdt() throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            // Drain all EDT work queued before this marker.
        });
    }

    private static void require(boolean condition, String message, List<String> violations) {
        if (!condition) {
            violations.add(message);
        }
    }

    private static void assertNoViolations(List<String> violations) {
        assertTrue(String.join("; ", violations), violations.isEmpty());
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError(failure);
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

    private static ViewerState viewerState(FakeViewer viewer) {
        ViewerState state = VIEWERS.get(viewer);
        if (state == null) {
            throw new AssertionError("unregistered fake viewer");
        }
        return state;
    }

    private static ChipState chipState(ControlledChip chip) {
        ChipState state = CHIPS.get(chip);
        if (state == null) {
            throw new AssertionError("unregistered controlled chip");
        }
        return state;
    }

    private static final class ControlledOpen {

        final File file;
        final TrackingStream stream;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final CountDownLatch returned = new CountDownLatch(1);

        ControlledOpen(File file, TrackingStream stream) {
            this.file = file;
            this.stream = stream;
        }

        AEFileInputStreamInterface awaitReleaseIgnoringCancellation() {
            entered.countDown();
            boolean waiting = true;
            while (waiting) {
                try {
                    released.await();
                    waiting = false;
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    // Deliberately continue: a late result must be rejected by ownership.
                }
            }
            return stream.proxy;
        }

        void release() {
            released.countDown();
        }
    }

    private static final class TrackingStream {

        final String label;
        final AtomicInteger closeCalls = new AtomicInteger();
        final PropertyChangeSupport support = new PropertyChangeSupport(this);
        final AEFileInputStreamInterface proxy;
        volatile File file;

        TrackingStream(String label) {
            this.label = label;
            proxy = (AEFileInputStreamInterface) Proxy.newProxyInstance(
                    AEFileInputStreamInterface.class.getClassLoader(),
                    new Class<?>[]{AEFileInputStreamInterface.class},
                    this::invoke);
        }

        private Object invoke(Object ignoredProxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "close" -> {
                    closeCalls.incrementAndGet();
                    yield null;
                }
                case "getSupport" -> support;
                case "getFile" -> file;
                case "setFile" -> {
                    file = (File) args[0];
                    yield null;
                }
                case "getZoneId" -> ZoneId.systemDefault();
                case "readPacketByNumber", "readPacketByTime" -> new AEPacketRaw(0);
                case "toString" -> "TrackingStream[" + label + "]";
                case "hashCode" -> System.identityHashCode(ignoredProxy);
                case "equals" -> ignoredProxy == args[0];
                default -> primitiveDefault(method.getReturnType());
            };
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
    }

    private static final class ChipState {

        final Map<File, ControlledOpen> opens = new ConcurrentHashMap<>();
        final AEChipRenderer renderer;

        ChipState(AEChipRenderer renderer) {
            this.renderer = renderer;
        }
    }

    private static final class ViewerState {

        final ControlledChip chip;
        final FakePlayerControls controls;
        final JCheckBoxMenuItem checkNonMonotonic = new JCheckBoxMenuItem();
        final AtomicInteger offEdtUiCalls = new AtomicInteger();
        volatile AEPlayer player;
        volatile boolean playbackOpen;
        volatile AEViewer.PlayMode playMode = AEViewer.PlayMode.WAITING;
        volatile File inputFile;

        ViewerState(ControlledChip chip, FakePlayerControls controls) {
            this.chip = chip;
            this.controls = controls;
        }

        void uiCall() {
            if (!SwingUtilities.isEventDispatchThread()) {
                offEdtUiCalls.incrementAndGet();
            }
        }
    }

    private static final class ControlledChip extends AEChip {

        private ControlledChip() {
            // Never invoked; instances are allocated without GUI-heavy AEChip construction.
        }

        @Override
        public AEFileInputStreamInterface constuctFileInputStream(
                File file, ProgressMonitor progressMonitor) throws IOException {
            ControlledOpen open = chipState(this).opens.get(file);
            if (open == null) {
                throw new IOException("no controlled open registered for " + file);
            }
            try {
                return open.awaitReleaseIgnoringCancellation();
            } finally {
                open.returned.countDown();
            }
        }

        @Override
        public AEChipRenderer getRenderer() {
            return chipState(this).renderer;
        }
    }

    private static final class FakeRenderer extends AEChipRenderer {

        private FakeRenderer() {
            super(null);
            // Never invoked; only the two completion callbacks below are used.
        }

        @Override
        public synchronized void resetFrame(float value) {
        }

        @Override
        public void showRenderingModeTextOnAeViewer() {
        }
    }

    private static final class FakePlayerControls extends AePlayerAdvancedControlsPanel {

        private FakePlayerControls() {
            super(null);
            // Never invoked; only the overridden listener seam is used.
        }

        @Override
        public void addMeToPropertyChangeListeners(AEFileInputStreamInterface stream) {
            // The stream itself tracks listener registration; no Swing widgets are needed.
            stream.getSupport().addPropertyChangeListener(event -> {
            });
        }
    }

    private static final class FakeViewer extends AEViewer {

        private FakeViewer() {
            super((JAERViewer) null);
            // Never invoked; JFrame construction is intentionally bypassed.
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            // AbstractAEPlayer fires state changes from both worker and EDT paths.
        }

        @Override
        public AEChip getChip() {
            return viewerState(this).chip;
        }

        @Override
        public AbstractAEPlayer getAePlayer() {
            return viewerState(this).player;
        }

        @Override
        public JAERViewer getJaerViewer() {
            return null;
        }

        @Override
        public boolean ensureChipCompatibleWithRecording(File file) {
            return true;
        }

        @Override
        public Aedat4Lz4Rerecorder.OpenPlan offerAedat4Lz4Rerecord(File file) {
            return new Aedat4Lz4Rerecorder.OpenPlan(file, null);
        }

        @Override
        public void beginFilePlaybackOpen() {
            ViewerState state = viewerState(this);
            state.uiCall();
            state.playbackOpen = true;
            state.playMode = PlayMode.PLAYBACK;
        }

        @Override
        public void endFilePlaybackOpen() {
            ViewerState state = viewerState(this);
            state.uiCall();
            state.playbackOpen = false;
        }

        @Override
        public PlayMode getPlayMode() {
            return viewerState(this).playMode;
        }

        @Override
        public void setPlayMode(PlayMode playMode) {
            ViewerState state = viewerState(this);
            state.uiCall();
            state.playMode = playMode;
        }

        @Override
        public void setPaused(boolean paused) {
            viewerState(this).uiCall();
        }

        @Override
        public JCheckBoxMenuItem getCheckNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem() {
            return viewerState(this).checkNonMonotonic;
        }

        @Override
        public int getAeFileInputStreamTimestampResetBitmask() {
            return 0;
        }

        @Override
        public AePlayerAdvancedControlsPanel getPlayerControls() {
            return viewerState(this).controls;
        }

        @Override
        void setPlaybackControlsEnabledState(boolean enabled) {
            viewerState(this).uiCall();
        }

        @Override
        void fixRecordingControls() {
            viewerState(this).uiCall();
        }

        @Override
        protected void setInputFile(File file) {
            ViewerState state = viewerState(this);
            state.uiCall();
            state.inputFile = file;
        }

        @Override
        public void setCursor(Cursor cursor) {
            viewerState(this).uiCall();
        }

        @Override
        public void setTitleAccordingToState() {
            viewerState(this).uiCall();
        }
    }
}
