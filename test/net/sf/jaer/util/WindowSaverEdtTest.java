package net.sf.jaer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Frozen acceptance tests for WindowSaver's EDT and persistence lifecycle.
 * The frame is allocated without running JFrame's constructor, so no physical
 * desktop or native peer is used. Screen-bound queries still require a virtual
 * X display. Preferences are an in-memory tree private to each test.
 */
public class WindowSaverEdtTest {

    private static final long WORKER_TIMEOUT_SECONDS = 3;
    private static final String WINDOW_NAME = "WindowSaverEdtFrame";

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(WindowSaverEdtTest.class);
    }

    private MemoryPreferences preferenceRoot;
    private Preferences windowPreferences;
    private WindowSaver saver;
    private TrackingFrame frame;

    @Before
    public void setUp() throws Exception {
        assertFalse("WindowSaverEdtTest requires an Xvfb display, not headless mode",
                java.awt.GraphicsEnvironment.isHeadless());
        preferenceRoot = new MemoryPreferences();
        windowPreferences = preferenceRoot.node("WindowSaver");
        saver = new WindowSaver(this, preferenceRoot);
        frame = allocateWithoutConstructor(TrackingFrame.class);
        frame.initialize(WINDOW_NAME, "Window Saver EDT Frame", 5, 6, 120, 90, Frame.NORMAL);
    }

    @After
    public void tearDown() throws Exception {
        if (frame != null) {
            frame.setMonitoring(false);
        }
        stopTestVisibleTimers();
        flushEdt();
    }

    @Test(timeout = 10000)
    public void workerThreadWindowOpenRestoresGeometryWithOnlyEdtFrameAccess() throws Exception {
        putStoredSnapshot(91, 82, 430, 315, Frame.NORMAL);
        WindowEvent opened = new WindowEvent(frame, WindowEvent.WINDOW_OPENED);

        frame.setMonitoring(true);
        runOffEdt(() -> saver.eventDispatched(opened));
        flushEdt();
        frame.setMonitoring(false);

        assertEquals("restored geometry", new Rectangle(91, 82, 430, 315), frame.rawBounds());
        assertTrue("install/restore touched the JFrame off the EDT: " + frame.violations(),
                frame.violations().isEmpty());
    }

    @Test(timeout = 10000)
    public void workerThreadMoveResizeAndStateCallbacksPersistTheFinalSnapshotOnEdt() throws Exception {
        installOnEdt();

        List<ComponentListener> componentListeners = frame.componentListenersSnapshot();
        List<WindowStateListener> stateListeners = frame.windowStateListenersSnapshot();
        assertFalse("opening a frame must install a component listener for move/resize persistence",
                componentListeners.isEmpty());
        assertFalse("opening a frame must install a window-state listener",
                stateListeners.isEmpty());

        frame.clearViolations();
        frame.setMonitoring(true);
        runOffEdt(() -> {
            for (int i = 0; i < 12; i++) {
                int state = i == 11 ? Frame.MAXIMIZED_HORIZ : Frame.NORMAL;
                frame.setRawSnapshot(40 + i, 50 + i, 300 + i, 200 + i, state);
                ComponentEvent moved = new ComponentEvent(frame, ComponentEvent.COMPONENT_MOVED);
                ComponentEvent resized = new ComponentEvent(frame, ComponentEvent.COMPONENT_RESIZED);
                WindowEvent stateChanged = new WindowEvent(
                        frame, WindowEvent.WINDOW_STATE_CHANGED, Frame.NORMAL, state);
                for (ComponentListener listener : componentListeners) {
                    listener.componentMoved(moved);
                    listener.componentResized(resized);
                }
                for (WindowStateListener listener : stateListeners) {
                    listener.windowStateChanged(stateChanged);
                }
            }
        });
        runOffEdt(() -> invokeRequiredLifecycleMethod("flush"));
        flushEdt();
        frame.setMonitoring(false);

        assertStoredSnapshot(51, 61, 311, 211, Frame.MAXIMIZED_HORIZ);
        assertTrue("move/resize/state persistence touched the JFrame off the EDT: "
                + frame.violations(), frame.violations().isEmpty());
    }

    @Test(timeout = 10000)
    public void flushAndCloseAreIdempotentAndLeaveNoPendingTimerOrListenerWork() throws Exception {
        installOnEdt();
        runOffEdt(() -> {
            invokeRequiredLifecycleMethod("flush");
            invokeRequiredLifecycleMethod("flush");
        });

        List<ComponentListener> componentListeners = frame.componentListenersSnapshot();
        List<WindowStateListener> stateListeners = frame.windowStateListenersSnapshot();
        assertFalse("fixture requires the installed move/resize listener", componentListeners.isEmpty());
        assertFalse("fixture requires the installed state listener", stateListeners.isEmpty());

        frame.clearViolations();
        frame.setMonitoring(true);
        frame.setRawSnapshot(170, 180, 520, 410, Frame.MAXIMIZED_VERT);
        runOffEdt(() -> fireCallbacks(componentListeners, stateListeners,
                Frame.NORMAL, Frame.MAXIMIZED_VERT));
        runOffEdt(() -> {
            invokeRequiredLifecycleMethod("close");
            invokeRequiredLifecycleMethod("close");
            invokeRequiredLifecycleMethod("flush");
        });
        flushEdt();

        assertStoredSnapshot(170, 180, 520, 410, Frame.MAXIMIZED_VERT);
        Map<String, String> snapshotAtClose = ((MemoryPreferences) windowPreferences).snapshot();

        assertTrue("close must remove component listeners: " + frame.componentListenersSnapshot(),
                frame.componentListenersSnapshot().isEmpty());
        assertTrue("close must remove window-state listeners: " + frame.windowStateListenersSnapshot(),
                frame.windowStateListenersSnapshot().isEmpty());
        assertTrue("close must remove window listeners: " + frame.windowListenersSnapshot(),
                frame.windowListenersSnapshot().isEmpty());
        assertNoRunningTimerOrFuture();
        assertSaverNotRegisteredWithToolkit();

        // A callback already copied by the event queue must become inert after close.
        frame.setRawSnapshot(1, 2, 3, 4, Frame.ICONIFIED);
        runOffEdt(() -> fireCallbacks(componentListeners, stateListeners,
                Frame.MAXIMIZED_VERT, Frame.ICONIFIED));
        flushEdt();
        flushEdt();
        frame.setMonitoring(false);

        assertEquals("closed WindowSaver accepted stale callback work",
                snapshotAtClose, ((MemoryPreferences) windowPreferences).snapshot());
        assertTrue("flush/close or a stale callback touched the JFrame off the EDT: "
                + frame.violations(), frame.violations().isEmpty());
    }

    private void installOnEdt() throws Exception {
        WindowEvent opened = new WindowEvent(frame, WindowEvent.WINDOW_OPENED);
        SwingUtilities.invokeAndWait(() -> saver.eventDispatched(opened));
        flushEdt();
    }

    private void putStoredSnapshot(int x, int y, int width, int height, int state) {
        windowPreferences.putInt(WINDOW_NAME + ".x", x);
        windowPreferences.putInt(WINDOW_NAME + ".y", y);
        windowPreferences.putInt(WINDOW_NAME + ".w", width);
        windowPreferences.putInt(WINDOW_NAME + ".h", height);
        windowPreferences.putInt(WINDOW_NAME + ".state", state);
    }

    private void assertStoredSnapshot(int x, int y, int width, int height, int state) {
        assertEquals("persisted x", x, windowPreferences.getInt(WINDOW_NAME + ".x", Integer.MIN_VALUE));
        assertEquals("persisted y", y, windowPreferences.getInt(WINDOW_NAME + ".y", Integer.MIN_VALUE));
        assertEquals("persisted width", width,
                windowPreferences.getInt(WINDOW_NAME + ".w", Integer.MIN_VALUE));
        assertEquals("persisted height", height,
                windowPreferences.getInt(WINDOW_NAME + ".h", Integer.MIN_VALUE));
        assertEquals("persisted extended state", state,
                windowPreferences.getInt(WINDOW_NAME + ".state", Integer.MIN_VALUE));
    }

    private void fireCallbacks(List<ComponentListener> componentListeners,
            List<WindowStateListener> stateListeners, int oldState, int newState) {
        ComponentEvent moved = new ComponentEvent(frame, ComponentEvent.COMPONENT_MOVED);
        ComponentEvent resized = new ComponentEvent(frame, ComponentEvent.COMPONENT_RESIZED);
        WindowEvent stateChanged = new WindowEvent(
                frame, WindowEvent.WINDOW_STATE_CHANGED, oldState, newState);
        for (ComponentListener listener : componentListeners) {
            listener.componentMoved(moved);
            listener.componentResized(resized);
        }
        for (WindowStateListener listener : stateListeners) {
            listener.windowStateChanged(stateChanged);
        }
    }

    private void invokeRequiredLifecycleMethod(String name) throws Exception {
        Method method;
        try {
            method = WindowSaver.class.getMethod(name);
        } catch (NoSuchMethodException e) {
            fail("WindowSaver must expose public " + name + "() for the frozen lifecycle contract");
            return;
        }
        try {
            method.invoke(saver);
        } catch (InvocationTargetException e) {
            rethrow(e.getCause());
        }
    }

    private void assertNoRunningTimerOrFuture() throws Exception {
        for (Class<?> type = saver.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Timer.class.isAssignableFrom(field.getType())
                        && !ScheduledFuture.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(saver);
                if (value instanceof Timer timer) {
                    assertFalse("close left Swing Timer running in field " + field.getName(),
                            timer.isRunning());
                } else if (value instanceof Future<?> future) {
                    assertTrue("close left scheduled work pending in field " + field.getName(),
                            future.isDone() || future.isCancelled());
                }
            }
        }
    }

    private void assertSaverNotRegisteredWithToolkit() {
        for (AWTEventListener listener : Toolkit.getDefaultToolkit().getAWTEventListeners()) {
            assertTrue("close left WindowSaver registered as a Toolkit AWTEventListener",
                    listener != saver);
        }
    }

    private void stopTestVisibleTimers() throws Exception {
        if (saver == null) {
            return;
        }
        List<Timer> timers = new ArrayList<>();
        for (Class<?> type = saver.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Timer.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Timer timer = (Timer) field.get(saver);
                    if (timer != null) {
                        timers.add(timer);
                    }
                }
            }
        }
        if (!timers.isEmpty()) {
            SwingUtilities.invokeAndWait(() -> timers.forEach(Timer::stop));
        }
    }

    private static void flushEdt() throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            // Barrier for all previously queued EDT work.
        });
    }

    private static void runOffEdt(ThrowingRunnable action) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WindowSaverEdtTest-worker");
            thread.setDaemon(true);
            return thread;
        });
        Future<Void> future = executor.submit(() -> {
            assertFalse("worker helper unexpectedly ran on the EDT",
                    SwingUtilities.isEventDispatchThread());
            action.run();
            return null;
        });
        try {
            future.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            rethrow(e.getCause());
        } catch (TimeoutException e) {
            future.cancel(true);
            fail("worker action did not finish within " + WORKER_TIMEOUT_SECONDS + " seconds");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void rethrow(Throwable failure) throws Exception {
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

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    private static final class TrackingFrame extends JFrame {

        private static final long serialVersionUID = 1L;

        private String trackedName;
        private String trackedTitle;
        private volatile int trackedX;
        private volatile int trackedY;
        private volatile int trackedWidth;
        private volatile int trackedHeight;
        private volatile int trackedState;
        private AtomicBoolean monitoring;
        private CopyOnWriteArrayList<String> offEdtAccesses;
        private CopyOnWriteArrayList<ComponentListener> componentListeners;
        private CopyOnWriteArrayList<WindowStateListener> stateListeners;
        private CopyOnWriteArrayList<WindowListener> windowListeners;

        void initialize(String name, String title, int x, int y, int width, int height, int state) {
            trackedName = name;
            trackedTitle = title;
            trackedX = x;
            trackedY = y;
            trackedWidth = width;
            trackedHeight = height;
            trackedState = state;
            monitoring = new AtomicBoolean();
            offEdtAccesses = new CopyOnWriteArrayList<>();
            componentListeners = new CopyOnWriteArrayList<>();
            stateListeners = new CopyOnWriteArrayList<>();
            windowListeners = new CopyOnWriteArrayList<>();
        }

        void setMonitoring(boolean enabled) {
            monitoring.set(enabled);
        }

        void clearViolations() {
            offEdtAccesses.clear();
        }

        List<String> violations() {
            return new ArrayList<>(offEdtAccesses);
        }

        Rectangle rawBounds() {
            return new Rectangle(trackedX, trackedY, trackedWidth, trackedHeight);
        }

        void setRawSnapshot(int x, int y, int width, int height, int state) {
            trackedX = x;
            trackedY = y;
            trackedWidth = width;
            trackedHeight = height;
            trackedState = state;
        }

        List<ComponentListener> componentListenersSnapshot() {
            return new ArrayList<>(componentListeners);
        }

        List<WindowStateListener> windowStateListenersSnapshot() {
            return new ArrayList<>(stateListeners);
        }

        List<WindowListener> windowListenersSnapshot() {
            return new ArrayList<>(windowListeners);
        }

        private void record(String operation) {
            if (monitoring.get() && !SwingUtilities.isEventDispatchThread()) {
                offEdtAccesses.add(operation + " on " + Thread.currentThread().getName());
            }
        }

        @Override
        public String getName() {
            record("getName");
            return trackedName;
        }

        @Override
        public String getTitle() {
            record("getTitle");
            return trackedTitle;
        }

        @Override
        public int getX() {
            record("getX");
            return trackedX;
        }

        @Override
        public int getY() {
            record("getY");
            return trackedY;
        }

        @Override
        public int getWidth() {
            record("getWidth");
            return trackedWidth;
        }

        @Override
        public int getHeight() {
            record("getHeight");
            return trackedHeight;
        }

        @Override
        public Rectangle getBounds() {
            record("getBounds");
            return rawBounds();
        }

        @Override
        public Point getLocation() {
            record("getLocation");
            return new Point(trackedX, trackedY);
        }

        @Override
        public Point getLocationOnScreen() {
            record("getLocationOnScreen");
            return new Point(trackedX, trackedY);
        }

        @Override
        public Dimension getSize() {
            record("getSize");
            return new Dimension(trackedWidth, trackedHeight);
        }

        @Override
        public int getExtendedState() {
            record("getExtendedState");
            return trackedState;
        }

        @Override
        public void setLocation(int x, int y) {
            record("setLocation(int,int)");
            trackedX = x;
            trackedY = y;
        }

        @Override
        public void setLocation(Point point) {
            record("setLocation(Point)");
            trackedX = point.x;
            trackedY = point.y;
        }

        @Override
        public void setSize(int width, int height) {
            record("setSize(int,int)");
            trackedWidth = width;
            trackedHeight = height;
        }

        @Override
        public void setSize(Dimension size) {
            record("setSize(Dimension)");
            trackedWidth = size.width;
            trackedHeight = size.height;
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            record("setBounds(int,int,int,int)");
            trackedX = x;
            trackedY = y;
            trackedWidth = width;
            trackedHeight = height;
        }

        @Override
        public void setBounds(Rectangle bounds) {
            record("setBounds(Rectangle)");
            setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        public void setExtendedState(int state) {
            record("setExtendedState");
            trackedState = state;
        }

        @Override
        public void validate() {
            record("validate");
        }

        @Override
        public void addComponentListener(ComponentListener listener) {
            record("addComponentListener");
            componentListeners.addIfAbsent(listener);
        }

        @Override
        public void removeComponentListener(ComponentListener listener) {
            record("removeComponentListener");
            componentListeners.remove(listener);
        }

        @Override
        public ComponentListener[] getComponentListeners() {
            record("getComponentListeners");
            return componentListeners.toArray(new ComponentListener[0]);
        }

        @Override
        public void addWindowStateListener(WindowStateListener listener) {
            record("addWindowStateListener");
            stateListeners.addIfAbsent(listener);
        }

        @Override
        public void removeWindowStateListener(WindowStateListener listener) {
            record("removeWindowStateListener");
            stateListeners.remove(listener);
        }

        @Override
        public WindowStateListener[] getWindowStateListeners() {
            record("getWindowStateListeners");
            return stateListeners.toArray(new WindowStateListener[0]);
        }

        @Override
        public void addWindowListener(WindowListener listener) {
            record("addWindowListener");
            windowListeners.addIfAbsent(listener);
        }

        @Override
        public void removeWindowListener(WindowListener listener) {
            record("removeWindowListener");
            windowListeners.remove(listener);
        }

        @Override
        public WindowListener[] getWindowListeners() {
            record("getWindowListeners");
            return windowListeners.toArray(new WindowListener[0]);
        }
    }

    private static final class MemoryPreferences extends AbstractPreferences {

        private final Map<String, String> values = Collections.synchronizedMap(new HashMap<>());
        private final Map<String, MemoryPreferences> children
                = Collections.synchronizedMap(new HashMap<>());

        MemoryPreferences() {
            super(null, "");
        }

        private MemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        Map<String, String> snapshot() {
            synchronized (values) {
                return new HashMap<>(values);
            }
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
            values.clear();
            children.clear();
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            synchronized (values) {
                String[] keys = values.keySet().toArray(new String[0]);
                Arrays.sort(keys);
                return keys;
            }
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            synchronized (children) {
                String[] names = children.keySet().toArray(new String[0]);
                Arrays.sort(names);
                return names;
            }
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            synchronized (children) {
                return children.computeIfAbsent(name, key -> new MemoryPreferences(this, key));
            }
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
            // In-memory and immediately consistent.
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
            // In-memory and immediately durable for the lifetime of this test.
        }
    }
}
