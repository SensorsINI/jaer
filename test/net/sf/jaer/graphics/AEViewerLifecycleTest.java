package net.sf.jaer.graphics;

import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventprocessing.FilterFrame;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Frozen acceptance tests for AEViewer's final-disposal and dynamic-menu
 * lifecycle. Constructors that create native windows, USB factories, global
 * registrations, or sockets are deliberately bypassed. The callbacks and menu
 * builders under test are the real AEViewer methods.
 */
public class AEViewerLifecycleTest {

    private static final long SHORT_TIMEOUT_MS = 1_000;
    private static final long LONG_TIMEOUT_MS = 5_000;

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(AEViewerLifecycleTest.class);
    }

    private static final Map<FakeViewer, ViewerState> VIEWERS
            = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<TrackingFilterFrame, FilterFrameState> FILTER_FRAMES
            = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ControlledChip, HardwareInterface> CHIPS
            = Collections.synchronizedMap(new IdentityHashMap<>());

    private final List<Thread> workers = new ArrayList<>();

    private FakeViewer viewer;
    private ViewerState viewerState;
    private TrackingStream activeStream;
    private TrackingPlayer player;
    private TrackingMonitor monitor;
    private TrackingFilterFrame filterFrame;
    private FilterFrameState filterFrameState;
    private Preferences preferenceNode;

    @Before
    public void setUp() throws Exception {
        org.junit.Assume.assumeFalse(
                "AEViewer lifecycle acceptance requires a virtual display",
                GraphicsEnvironment.isHeadless());

        viewer = allocateWithoutConstructor(FakeViewer.class);
        viewerState = new ViewerState();
        VIEWERS.put(viewer, viewerState);

        preferenceNode = Preferences.userRoot().node(
                "/net/sf/jaer/test/AEViewerLifecycleTest/" + UUID.randomUUID());
        viewer.prefs = preferenceNode;

        activeStream = new TrackingStream();
        player = new TrackingPlayer(viewer, activeStream.proxy);
        viewer.aePlayer = player;
        viewerState.player = player;

        monitor = new TrackingMonitor();
        viewer.aemon = monitor.proxy;

        filterFrame = allocateWithoutConstructor(TrackingFilterFrame.class);
        filterFrameState = new FilterFrameState();
        FILTER_FRAMES.put(filterFrame, filterFrameState);
        setField(viewer, "filterFrame", filterFrame);
        setField(viewer, "viewLoop", null);

        JAERViewer manager = allocateWithoutConstructor(JAERViewer.class);
        ArrayList<AEViewer> managedViewers = new ArrayList<>();
        managedViewers.add(viewer);
        managedViewers.add(null); // Keep this viewer on the secondary-window path.
        setField(manager, "viewers", managedViewers);
        setField(viewer, "jaerViewer", manager);
    }

    @After
    public void tearDown() throws Exception {
        if (monitor != null) {
            monitor.releaseClose();
        }
        for (Thread worker : workers) {
            worker.interrupt();
            worker.join(LONG_TIMEOUT_MS);
        }
        flushEdt();

        if (activeStream != null && player != null && activeStream.closeCalls.get() == 0) {
            player.close();
            flushEdt();
        }

        FILTER_FRAMES.remove(filterFrame);
        VIEWERS.remove(viewer);
        if (viewerState != null && viewerState.chip != null) {
            CHIPS.remove(viewerState.chip);
        }
        if (preferenceNode != null) {
            preferenceNode.removeNode();
        }
    }

    @Test(timeout = 15_000)
    public void secondaryWindowCloseFromWorkerReturnsWhileUiTeardownRunsOnEdt() throws Exception {
        monitor.blockClose();
        WorkerRun close = startWorker(
                () -> invokePrivate(viewer, "formWindowClosing", WindowEvent.class, null),
                "secondary-window-close-callback");

        boolean monitorCloseStarted = monitor.closeEntered.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        CountDownLatch edtPulse = new CountDownLatch(1);
        SwingUtilities.invokeLater(edtPulse::countDown);

        boolean callbackReturnedPromptly = close.done.await(SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        boolean edtStayedResponsive = edtPulse.await(SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        boolean filterDisposedWhileMonitorBlocked
                = filterFrameState.disposed.await(SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        boolean viewerDisposedWhileMonitorBlocked
                = viewerState.disposed.await(SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        monitor.releaseClose();
        close.thread.join(LONG_TIMEOUT_MS);
        flushEdt();

        List<String> violations = new ArrayList<>();
        require(monitorCloseStarted,
                "secondary close never reached the fake monitor", violations);
        require(callbackReturnedPromptly,
                "off-EDT windowClosing callback blocked on teardown", violations);
        require(edtStayedResponsive,
                "secondary teardown blocked the EDT while monitor close was pending", violations);
        require(filterDisposedWhileMonitorBlocked,
                "filter frame was not detached/disposed before blocking monitor close completed", violations);
        require(viewerDisposedWhileMonitorBlocked,
                "viewer window was not detached/disposed before blocking monitor close completed", violations);
        require(filterFrameState.offEdtDisposeCalls.get() == 0,
                "filter frame was disposed off the EDT", violations);
        require(viewerState.offEdtDisposeCalls.get() == 0,
                "viewer window was disposed off the EDT", violations);
        require(viewerState.offEdtStopCalls.get() == 0,
                "viewer stop/detach ran off the EDT", violations);
        require(monitor.closeOnEdtCalls.get() == 0,
                "potentially blocking monitor close ran on the EDT", violations);
        require(close.failure.get() == null,
                "windowClosing callback threw " + close.failure.get(), violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 15_000)
    public void repeatedWindowCloseReleasesEveryOwnedResourceExactlyOnce() throws Exception {
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> invokePrivateCapturing(
                viewer, "formWindowClosing", WindowEvent.class, null, firstFailure));
        SwingUtilities.invokeAndWait(() -> invokePrivateCapturing(
                viewer, "formWindowClosing", WindowEvent.class, null, secondFailure));

        awaitCondition(() -> monitor.closeCalls.get() > 0
                && filterFrameState.disposeCalls.get() > 0
                && viewerState.disposeCalls.get() > 0, LONG_TIMEOUT_MS);
        flushEdt();

        List<String> violations = new ArrayList<>();
        require(firstFailure.get() == null,
                "first windowClosing callback threw " + firstFailure.get(), violations);
        require(secondFailure.get() == null,
                "repeated windowClosing callback threw " + secondFailure.get(), violations);
        require(player.closeCalls.get() == 1,
                "viewer-owned AEPlayer close count was " + player.closeCalls.get(), violations);
        require(activeStream.closeCalls.get() == 1,
                "active playback stream close count was " + activeStream.closeCalls.get(), violations);
        require(monitor.closeCalls.get() == 1,
                "monitor close count was " + monitor.closeCalls.get(), violations);
        require(filterFrameState.disposeCalls.get() == 1,
                "filter frame dispose count was " + filterFrameState.disposeCalls.get(), violations);
        require(viewerState.disposeCalls.get() == 1,
                "viewer window dispose count was " + viewerState.disposeCalls.get(), violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 15_000)
    public void interfaceMenuPopulationRunsOnEdtAndPreservesHeavyweightPopup() throws Exception {
        TrackingMonitor menuHardware = new TrackingMonitor();
        ControlledChip chip = allocateWithoutConstructor(ControlledChip.class);
        CHIPS.put(chip, menuHardware.proxy);
        viewerState.chip = chip;
        setField(viewer, "chip", chip);

        TrackingMenu interfaceMenu = onEdt(() -> new TrackingMenu("Interface"));
        interfaceMenu.startTracking();

        Class[] savedFactories = HardwareInterfaceFactory.factories.clone();
        WorkerRun build;
        boolean returned;
        try {
            // The menu contract does not require probing real serial/USB factories.
            // Keep their static registry inert so this acceptance test never touches hardware.
            Arrays.fill(HardwareInterfaceFactory.factories, Object.class);
            build = startWorker(
                    () -> viewer.buildInterfaceMenu(interfaceMenu),
                    "interface-menu-population");
            returned = build.done.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            flushEdt();
        } finally {
            System.arraycopy(savedFactories, 0, HardwareInterfaceFactory.factories, 0,
                    savedFactories.length);
        }
        boolean lightweight = onEdt(
                () -> interfaceMenu.getPopupMenu().isLightWeightPopupEnabled());

        List<String> violations = new ArrayList<>();
        require(returned, "interface-menu population did not return", violations);
        require(build.failure.get() == null,
                "interface-menu population threw " + build.failure.get(), violations);
        require(interfaceMenu.mutationCalls.get() > 0,
                "interface-menu builder made no observable menu mutation", violations);
        require(interfaceMenu.offEdtMutationCalls.get() == 0,
                "interface menu was mutated off the EDT "
                + interfaceMenu.offEdtMutationCalls.get() + " times", violations);
        require(!lightweight,
                "interface menu popup lost setLightWeightPopupEnabled(false)", violations);
        assertNoViolations(violations);
    }

    @Test(timeout = 15_000)
    public void hardwareDeviceMenuPopulationRunsOnEdtAndPreservesHeavyweightPopup() throws Exception {
        preferenceNode.putByteArray("chipClassNames", serializedEmptyStringList());
        preferenceNode.put("AEViewer.chipClassNamesDefaultsMerged",
                String.join("\n", AEViewer.DEFAULT_CHIP_CLASS_NAMES));

        TrackingMenu deviceMenu = onEdt(() -> new TrackingMenu("AEChip"));
        setField(viewer, "deviceMenu", deviceMenu);
        setField(viewer, "renewChipMI", new JMenuItem("Renew"));
        setField(viewer, "customizeDevicesMenuItem", new JMenuItem("Customize"));
        deviceMenu.startTracking();

        WorkerRun build = startWorker(
                () -> invokePrivate(viewer, "buildDeviceMenu"),
                "hardware-device-menu-population");
        boolean returned = build.done.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        flushEdt();
        boolean lightweight = onEdt(
                () -> deviceMenu.getPopupMenu().isLightWeightPopupEnabled());

        List<String> violations = new ArrayList<>();
        require(returned, "hardware-device-menu population did not return", violations);
        require(build.failure.get() == null,
                "hardware-device-menu population threw " + build.failure.get(), violations);
        require(deviceMenu.mutationCalls.get() > 0,
                "hardware-device-menu builder made no observable menu mutation", violations);
        require(deviceMenu.offEdtMutationCalls.get() == 0,
                "hardware device menu was mutated off the EDT "
                + deviceMenu.offEdtMutationCalls.get() + " times", violations);
        require(!lightweight,
                "hardware device menu popup lost setLightWeightPopupEnabled(false)", violations);
        assertNoViolations(violations);
    }

    private WorkerRun startWorker(ThrowingRunnable action, String name) {
        WorkerRun run = new WorkerRun();
        run.thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                run.failure.set(t);
            } finally {
                run.done.countDown();
            }
        }, name);
        run.thread.setDaemon(true);
        workers.add(run.thread);
        run.thread.start();
        return run;
    }

    private static void invokePrivateCapturing(Object target, String methodName,
            Class<?> argumentType, Object argument, AtomicReference<Throwable> failure) {
        try {
            invokePrivate(target, methodName, argumentType, argument);
        } catch (Throwable t) {
            failure.set(t);
        }
    }

    private static void invokePrivate(Object target, String methodName) throws Exception {
        Method method = target.getClass().getSuperclass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void invokePrivate(Object target, String methodName,
            Class<?> argumentType, Object argument) throws Exception {
        Method method = target.getClass().getSuperclass().getDeclaredMethod(methodName, argumentType);
        method.setAccessible(true);
        method.invoke(target, new Object[]{argument});
    }

    private static byte[] serializedEmptyStringList() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(new ArrayList<String>());
        }
        return bytes.toByteArray();
    }

    private static void awaitCondition(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            flushEdt();
            Thread.sleep(5);
        }
    }

    private static void flushEdt() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeAndWait(() -> {
                // Drain all EDT work queued before this marker.
            });
        }
    }

    private static <T> T onEdt(ThrowingSupplier<T> supplier) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return supplier.get();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(supplier.get());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        rethrow(failure.get());
        return result.get();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class WorkerRun {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread;
    }

    private static final class ViewerState {
        final AtomicInteger disposeCalls = new AtomicInteger();
        final AtomicInteger offEdtDisposeCalls = new AtomicInteger();
        final AtomicInteger stopCalls = new AtomicInteger();
        final AtomicInteger offEdtStopCalls = new AtomicInteger();
        final CountDownLatch disposed = new CountDownLatch(1);
        volatile TrackingPlayer player;
        volatile ControlledChip chip;
    }

    private static final class FilterFrameState {
        final AtomicInteger disposeCalls = new AtomicInteger();
        final AtomicInteger offEdtDisposeCalls = new AtomicInteger();
        final CountDownLatch disposed = new CountDownLatch(1);
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
        public PlayMode getPlayMode() {
            return PlayMode.WAITING;
        }

        @Override
        public AEChip getChip() {
            return state(this).chip;
        }

        @Override
        public AbstractAEPlayer getAePlayer() {
            return state(this).player;
        }

        @Override
        public File stopRecording(boolean confirmFilename) {
            return null;
        }

        @Override
        public void stopMe() {
            ViewerState state = state(this);
            state.stopCalls.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                state.offEdtStopCalls.incrementAndGet();
            }
        }

        @Override
        public void dispose() {
            ViewerState state = state(this);
            state.disposeCalls.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                state.offEdtDisposeCalls.incrementAndGet();
            }
            state.disposed.countDown();
        }

        @Override
        public void endFilePlaybackOpen() {
            // AEPlayer.close() projects this state change to the viewer.
        }

        @Override
        public String toString() {
            return "FakeViewer";
        }
    }

    private static ViewerState state(FakeViewer viewer) {
        ViewerState state = VIEWERS.get(viewer);
        if (state == null) {
            throw new AssertionError("unregistered fake viewer");
        }
        return state;
    }

    private static final class TrackingPlayer extends AEPlayer {
        final AtomicInteger closeCalls = new AtomicInteger();
        final AtomicInteger stopPlaybackCalls = new AtomicInteger();

        TrackingPlayer(AEViewer viewer, AEFileInputStreamInterface activeStream) {
            super(viewer);
            aeInputStream = activeStream;
        }

        @Override
        public void stopPlayback(boolean resumeLive) {
            stopPlaybackCalls.incrementAndGet();
            super.stopPlayback(resumeLive);
        }

        @Override
        public void close() throws java.io.IOException {
            closeCalls.incrementAndGet();
            super.close();
        }
    }

    private static final class TrackingStream {
        final AtomicInteger closeCalls = new AtomicInteger();
        final AEFileInputStreamInterface proxy;

        TrackingStream() {
            proxy = (AEFileInputStreamInterface) Proxy.newProxyInstance(
                    AEFileInputStreamInterface.class.getClassLoader(),
                    new Class<?>[]{AEFileInputStreamInterface.class},
                    (ignoredProxy, method, args) -> switch (method.getName()) {
                        case "close" -> {
                            closeCalls.incrementAndGet();
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
        final AtomicInteger closeCalls = new AtomicInteger();
        final AtomicInteger closeOnEdtCalls = new AtomicInteger();
        final CountDownLatch closeEntered = new CountDownLatch(1);
        final CountDownLatch closeReleased = new CountDownLatch(1);
        final AEMonitorInterface proxy;
        volatile boolean blocking;

        TrackingMonitor() {
            proxy = (AEMonitorInterface) Proxy.newProxyInstance(
                    AEMonitorInterface.class.getClassLoader(),
                    new Class<?>[]{AEMonitorInterface.class},
                    (ignoredProxy, method, args) -> switch (method.getName()) {
                        case "isOpen" -> true;
                        case "close" -> {
                            closeCalls.incrementAndGet();
                            if (SwingUtilities.isEventDispatchThread()) {
                                closeOnEdtCalls.incrementAndGet();
                            }
                            closeEntered.countDown();
                            if (blocking) {
                                boolean waiting = true;
                                while (waiting) {
                                    try {
                                        closeReleased.await();
                                        waiting = false;
                                    } catch (InterruptedException e) {
                                        // A driver may ignore interruption; final disposal must stay bounded.
                                    }
                                }
                            }
                            yield null;
                        }
                        case "toString" -> "TrackingMonitor";
                        case "hashCode" -> System.identityHashCode(ignoredProxy);
                        case "equals" -> ignoredProxy == args[0];
                        default -> primitiveDefault(method.getReturnType());
                    });
        }

        void blockClose() {
            blocking = true;
        }

        void releaseClose() {
            closeReleased.countDown();
        }
    }

    private static final class TrackingFilterFrame extends FilterFrame {
        private TrackingFilterFrame() {
            super(null);
            // Never invoked; JFrame construction is intentionally bypassed.
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public void dispose() {
            FilterFrameState state = FILTER_FRAMES.get(this);
            if (state == null) {
                throw new AssertionError("unregistered filter frame");
            }
            state.disposeCalls.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                state.offEdtDisposeCalls.incrementAndGet();
            }
            state.disposed.countDown();
        }
    }

    private static final class ControlledChip extends AEChip {
        private ControlledChip() {
            // Never invoked; AEChip construction is intentionally bypassed.
        }

        @Override
        public HardwareInterface getHardwareInterface() {
            HardwareInterface hardware = CHIPS.get(this);
            if (hardware == null) {
                throw new AssertionError("unregistered controlled chip");
            }
            return hardware;
        }
    }

    private static final class TrackingMenu extends JMenu {
        final AtomicInteger mutationCalls = new AtomicInteger();
        final AtomicInteger offEdtMutationCalls = new AtomicInteger();
        volatile boolean tracking;

        TrackingMenu(String text) {
            super(text);
        }

        void startTracking() {
            tracking = true;
        }

        private void mutation() {
            if (!tracking) {
                return;
            }
            mutationCalls.incrementAndGet();
            if (!SwingUtilities.isEventDispatchThread()) {
                offEdtMutationCalls.incrementAndGet();
            }
        }

        @Override
        public void removeAll() {
            mutation();
            super.removeAll();
        }

        @Override
        public JMenuItem add(JMenuItem item) {
            mutation();
            return super.add(item);
        }

        @Override
        public Component add(Component component) {
            mutation();
            return super.add(component);
        }

        @Override
        public JMenuItem insert(JMenuItem item, int position) {
            mutation();
            return super.insert(item, position);
        }

        @Override
        public void addSeparator() {
            mutation();
            super.addSeparator();
        }

        @Override
        public void insertSeparator(int index) {
            mutation();
            super.insertSeparator(index);
        }

        @Override
        public void remove(int position) {
            mutation();
            super.remove(position);
        }

        @Override
        public void remove(Component component) {
            mutation();
            super.remove(component);
        }

        @Override
        protected void addImpl(Component component, Object constraints, int index) {
            mutation();
            super.addImpl(component, constraints, index);
        }
    }
}
