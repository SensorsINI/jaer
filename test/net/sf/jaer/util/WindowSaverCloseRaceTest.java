package net.sf.jaer.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.AWTEvent;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Regression coverage for an older {@link WindowSaver#flush()} overtaking the
 * final snapshot written by {@link WindowSaver#close()}.
 */
public class WindowSaverCloseRaceTest {

    private static final long DEADLINE_SECONDS = 3;
    private static final String WINDOW_NAME = "WindowSaverCloseRaceFrame";
    private static final Geometry OLD_GEOMETRY
            = new Geometry(40, 50, 320, 240, Frame.NORMAL);
    private static final Geometry FINAL_GEOMETRY
            = new Geometry(140, 150, 520, 410, Frame.MAXIMIZED_VERT);

    /** Keep the NetBeans Ant runner on its JUnit-4 adapter path. */
    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(WindowSaverCloseRaceTest.class);
    }

    private BlockingMemoryPreferences preferenceRoot;
    private BlockingMemoryPreferences windowPreferences;
    private WindowSaver saver;
    private InstrumentedFrame frame;
    private ExecutorService callers;
    private List<ComponentListener> saverComponentListeners;
    private List<WindowStateListener> saverStateListeners;
    private List<WindowListener> saverWindowListeners;

    @Before
    public void setUp() throws Exception {
        assertFalse("WindowSaverCloseRaceTest requires virtual X, not headless mode",
                GraphicsEnvironment.isHeadless());

        preferenceRoot = new BlockingMemoryPreferences();
        windowPreferences = (BlockingMemoryPreferences) preferenceRoot.node("WindowSaver");
        saver = new WindowSaver(this, preferenceRoot);
        callers = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "WindowSaverCloseRaceTest-caller");
            thread.setDaemon(true);
            return thread;
        });

        SwingUtilities.invokeAndWait(() -> {
            frame = new InstrumentedFrame();
            frame.setName(WINDOW_NAME);
            frame.setTitle("WindowSaver close race");
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setBounds(10, 10, 200, 150);
            frame.setVisible(true);
        });
        drainEdt();

        Toolkit.getDefaultToolkit().addAWTEventListener(
                saver, AWTEvent.WINDOW_EVENT_MASK);
        saver.loadSettings(frame);
        drainEdt();

        saverComponentListeners = windowSaverListeners(frame.getComponentListeners());
        saverStateListeners = windowSaverListeners(frame.getWindowStateListeners());
        saverWindowListeners = windowSaverListeners(frame.getWindowListeners());
        assertFalse("fixture did not locate WindowSaver's component listener",
                saverComponentListeners.isEmpty());
        assertFalse("fixture did not locate WindowSaver's state listener",
                saverStateListeners.isEmpty());
        assertFalse("fixture did not locate WindowSaver's lifecycle listener",
                saverWindowListeners.isEmpty());

        frame.reportGeometry(OLD_GEOMETRY);
        saver.flush();
        drainEdt();
    }

    @After
    public void tearDown() throws Exception {
        if (windowPreferences != null) {
            windowPreferences.releaseDelayedFlush();
        }
        if (saver != null) {
            try {
                saver.close();
            } catch (Exception ignored) {
                // The test body reports lifecycle failures; cleanup remains best-effort.
            }
            Toolkit.getDefaultToolkit().removeAWTEventListener(saver);
        }
        if (callers != null) {
            callers.shutdownNow();
            callers.awaitTermination(DEADLINE_SECONDS, TimeUnit.SECONDS);
        }
        if (frame != null) {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
        drainEdt();
    }

    @Test(timeout = 15000)
    public void delayedFlushCannotOverwriteFinalCloseSnapshot() throws Exception {
        Map<String, String> oldSnapshot = geometryValues(OLD_GEOMETRY);
        Map<String, String> finalSnapshot = geometryValues(FINAL_GEOMETRY);
        String delayedCallerName = "WindowSaverCloseRaceTest-old-flush";
        windowPreferences.delayNextFlushOf(oldSnapshot, delayedCallerName);

        Future<Void> delayedFlush = submit(delayedCallerName, () -> saver.flush());
        assertTrue("old flush did not copy its geometry snapshot within the deadline",
                windowPreferences.awaitDelayedFlushCopy(DEADLINE_SECONDS, TimeUnit.SECONDS));

        frame.reportGeometry(FINAL_GEOMETRY);
        Future<Void> close = submit("WindowSaverCloseRaceTest-close", () -> saver.close());
        assertTrue("close did not persist its final geometry before the old flush was released",
                windowPreferences.awaitCommitted(
                        finalSnapshot, DEADLINE_SECONDS, TimeUnit.SECONDS));
        awaitCaller("close", close);

        windowPreferences.releaseDelayedFlush();
        awaitCaller("flush", delayedFlush);

        List<String> violations = new ArrayList<>();
        Map<String, String> afterRace = windowPreferences.snapshot();
        if (!containsSnapshot(afterRace, finalSnapshot)) {
            violations.add("delayed flush overwrote the newer close snapshot: expected "
                    + finalSnapshot + " but was " + afterRace);
        }

        addListenerRemovalViolations(violations);
        if (isToolkitListenerRegistered(saver)) {
            violations.add("close left WindowSaver registered with the Toolkit");
        }

        Map<String, String> beforeStaleCallbacks = windowPreferences.snapshot();
        frame.reportGeometry(new Geometry(1, 2, 3, 4, Frame.ICONIFIED));
        fireCopiedCallbacks();
        drainEdt();
        drainEdt();
        Map<String, String> afterStaleCallbacks = windowPreferences.snapshot();
        if (!beforeStaleCallbacks.equals(afterStaleCallbacks)) {
            violations.add("a callback copied before close remained active: before "
                    + beforeStaleCallbacks + " but after " + afterStaleCallbacks);
        }

        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    private Future<Void> submit(String threadName, ThrowingRunnable operation) {
        return callers.submit(() -> {
            Thread.currentThread().setName(threadName);
            assertFalse("caller unexpectedly ran on the EDT",
                    SwingUtilities.isEventDispatchThread());
            operation.run();
            return null;
        });
    }

    private static void awaitCaller(String name, Future<Void> caller) throws Exception {
        try {
            caller.get(DEADLINE_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            rethrow(e.getCause());
        } catch (TimeoutException e) {
            caller.cancel(true);
            fail(name + " caller did not terminate within " + DEADLINE_SECONDS + " seconds");
        }
    }

    private void addListenerRemovalViolations(List<String> violations) {
        for (ComponentListener listener : saverComponentListeners) {
            if (containsIdentity(frame.getComponentListeners(), listener)) {
                violations.add("close left its component listener installed");
            }
        }
        for (WindowStateListener listener : saverStateListeners) {
            if (containsIdentity(frame.getWindowStateListeners(), listener)) {
                violations.add("close left its state listener installed");
            }
        }
        for (WindowListener listener : saverWindowListeners) {
            if (containsIdentity(frame.getWindowListeners(), listener)) {
                violations.add("close left its lifecycle listener installed");
            }
        }
    }

    private void fireCopiedCallbacks() {
        ComponentEvent moved = new ComponentEvent(frame, ComponentEvent.COMPONENT_MOVED);
        ComponentEvent resized = new ComponentEvent(frame, ComponentEvent.COMPONENT_RESIZED);
        WindowEvent stateChanged = new WindowEvent(
                frame, WindowEvent.WINDOW_STATE_CHANGED, Frame.NORMAL, Frame.ICONIFIED);
        WindowEvent closing = new WindowEvent(frame, WindowEvent.WINDOW_CLOSING);
        WindowEvent closed = new WindowEvent(frame, WindowEvent.WINDOW_CLOSED);
        for (ComponentListener listener : saverComponentListeners) {
            listener.componentMoved(moved);
            listener.componentResized(resized);
        }
        for (WindowStateListener listener : saverStateListeners) {
            listener.windowStateChanged(stateChanged);
        }
        for (WindowListener listener : saverWindowListeners) {
            listener.windowClosing(closing);
            listener.windowClosed(closed);
        }
    }

    private static boolean isToolkitListenerRegistered(AWTEventListener expected) {
        for (AWTEventListener listener : Toolkit.getDefaultToolkit().getAWTEventListeners()) {
            if (listener == expected) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> windowSaverListeners(T[] listeners) {
        List<T> owned = new ArrayList<>();
        String ownedClassPrefix = WindowSaver.class.getName() + "$";
        for (T listener : listeners) {
            if (listener.getClass().getName().startsWith(ownedClassPrefix)) {
                owned.add(listener);
            }
        }
        return owned;
    }

    private static boolean containsIdentity(Object[] values, Object expected) {
        for (Object value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> geometryValues(Geometry geometry) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(WINDOW_NAME + ".x", Integer.toString(geometry.x));
        values.put(WINDOW_NAME + ".y", Integer.toString(geometry.y));
        values.put(WINDOW_NAME + ".w", Integer.toString(geometry.width));
        values.put(WINDOW_NAME + ".h", Integer.toString(geometry.height));
        values.put(WINDOW_NAME + ".state", Integer.toString(geometry.extendedState));
        return values;
    }

    private static boolean containsSnapshot(
            Map<String, String> actual, Map<String, String> expected) {
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!Objects.equals(actual.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static void drainEdt() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeAndWait(() -> {
                // Barrier for all previously queued Swing work.
            });
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

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    private static final class Geometry {

        final int x;
        final int y;
        final int width;
        final int height;
        final int extendedState;

        Geometry(int x, int y, int width, int height, int extendedState) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.extendedState = extendedState;
        }
    }

    /** A real JFrame peer whose reported geometry can be changed without events. */
    private static final class InstrumentedFrame extends JFrame {

        private static final long serialVersionUID = 1L;
        private volatile Geometry reportedGeometry;

        void reportGeometry(Geometry geometry) {
            reportedGeometry = geometry;
        }

        @Override
        public int getX() {
            Geometry geometry = reportedGeometry;
            return geometry == null ? super.getX() : geometry.x;
        }

        @Override
        public int getY() {
            Geometry geometry = reportedGeometry;
            return geometry == null ? super.getY() : geometry.y;
        }

        @Override
        public int getWidth() {
            Geometry geometry = reportedGeometry;
            return geometry == null ? super.getWidth() : geometry.width;
        }

        @Override
        public int getHeight() {
            Geometry geometry = reportedGeometry;
            return geometry == null ? super.getHeight() : geometry.height;
        }

        @Override
        public Rectangle getBounds() {
            Geometry geometry = reportedGeometry;
            return geometry == null
                    ? super.getBounds()
                    : new Rectangle(geometry.x, geometry.y, geometry.width, geometry.height);
        }

        @Override
        public int getExtendedState() {
            Geometry geometry = reportedGeometry;
            return geometry == null ? super.getExtendedState() : geometry.extendedState;
        }
    }

    /**
     * Per-thread transactional in-memory preferences. One selected flush copies
     * its complete old batch and then blocks before committing it, while close
     * can commit its newer batch on another thread.
     */
    private static final class BlockingMemoryPreferences extends Preferences {

        private final BlockingMemoryPreferences parent;
        private final String name;
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, BlockingMemoryPreferences> children = new LinkedHashMap<>();
        private final ThreadLocal<Map<String, String>> pending
                = ThreadLocal.withInitial(LinkedHashMap::new);
        private final Object commitMonitor = new Object();
        private final List<Map<String, String>> committedBatches = new ArrayList<>();
        private volatile boolean removed;
        private Map<String, String> delayedSnapshot;
        private String delayedThreadName;
        private boolean delayedFlushClaimed;
        private CountDownLatch delayedFlushCopied = new CountDownLatch(0);
        private CountDownLatch releaseDelayedFlush = new CountDownLatch(0);

        BlockingMemoryPreferences() {
            this(null, "");
        }

        private BlockingMemoryPreferences(BlockingMemoryPreferences parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        void delayNextFlushOf(Map<String, String> snapshot, String threadName) {
            synchronized (commitMonitor) {
                delayedSnapshot = new LinkedHashMap<>(snapshot);
                delayedThreadName = threadName;
                delayedFlushClaimed = false;
                delayedFlushCopied = new CountDownLatch(1);
                releaseDelayedFlush = new CountDownLatch(1);
            }
        }

        boolean awaitDelayedFlushCopy(long timeout, TimeUnit unit) throws InterruptedException {
            return delayedFlushCopied.await(timeout, unit);
        }

        void releaseDelayedFlush() {
            releaseDelayedFlush.countDown();
        }

        boolean awaitCommitted(Map<String, String> expected, long timeout, TimeUnit unit)
                throws InterruptedException {
            long remainingNanos = unit.toNanos(timeout);
            long deadline = System.nanoTime() + remainingNanos;
            synchronized (commitMonitor) {
                while (!hasCommitted(expected)) {
                    if (remainingNanos <= 0) {
                        return false;
                    }
                    TimeUnit.NANOSECONDS.timedWait(commitMonitor, remainingNanos);
                    remainingNanos = deadline - System.nanoTime();
                }
                return true;
            }
        }

        Map<String, String> snapshot() {
            synchronized (commitMonitor) {
                return new LinkedHashMap<>(values);
            }
        }

        private boolean hasCommitted(Map<String, String> expected) {
            for (Map<String, String> batch : committedBatches) {
                if (containsSnapshot(batch, expected)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void put(String key, String value) {
            pending.get().put(Objects.requireNonNull(key), Objects.requireNonNull(value));
        }

        @Override
        public String get(String key, String defaultValue) {
            Map<String, String> current = pending.get();
            if (current.containsKey(key)) {
                return current.get(key);
            }
            synchronized (commitMonitor) {
                return values.getOrDefault(key, defaultValue);
            }
        }

        @Override
        public void remove(String key) {
            pending.get().remove(key);
            synchronized (commitMonitor) {
                values.remove(key);
            }
        }

        @Override
        public void clear() {
            pending.get().clear();
            synchronized (commitMonitor) {
                values.clear();
            }
        }

        @Override
        public void putInt(String key, int value) {
            put(key, Integer.toString(value));
        }

        @Override
        public int getInt(String key, int defaultValue) {
            try {
                return Integer.parseInt(get(key, null));
            } catch (NumberFormatException | NullPointerException ignored) {
                return defaultValue;
            }
        }

        @Override
        public void putLong(String key, long value) {
            put(key, Long.toString(value));
        }

        @Override
        public long getLong(String key, long defaultValue) {
            try {
                return Long.parseLong(get(key, null));
            } catch (NumberFormatException | NullPointerException ignored) {
                return defaultValue;
            }
        }

        @Override
        public void putBoolean(String key, boolean value) {
            put(key, Boolean.toString(value));
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            String value = get(key, null);
            return value == null ? defaultValue : Boolean.parseBoolean(value);
        }

        @Override
        public void putFloat(String key, float value) {
            put(key, Float.toString(value));
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            try {
                return Float.parseFloat(get(key, null));
            } catch (NumberFormatException | NullPointerException ignored) {
                return defaultValue;
            }
        }

        @Override
        public void putDouble(String key, double value) {
            put(key, Double.toString(value));
        }

        @Override
        public double getDouble(String key, double defaultValue) {
            try {
                return Double.parseDouble(get(key, null));
            } catch (NumberFormatException | NullPointerException ignored) {
                return defaultValue;
            }
        }

        @Override
        public void putByteArray(String key, byte[] value) {
            put(key, Base64.getEncoder().encodeToString(value));
        }

        @Override
        public byte[] getByteArray(String key, byte[] defaultValue) {
            try {
                String value = get(key, null);
                return value == null ? defaultValue : Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException ignored) {
                return defaultValue;
            }
        }

        @Override
        public String[] keys() {
            synchronized (commitMonitor) {
                return values.keySet().toArray(new String[0]);
            }
        }

        @Override
        public String[] childrenNames() {
            synchronized (children) {
                return children.keySet().toArray(new String[0]);
            }
        }

        @Override
        public Preferences parent() {
            return parent;
        }

        @Override
        public Preferences node(String path) {
            Objects.requireNonNull(path);
            if (path.isEmpty()) {
                return this;
            }
            if (path.equals("/")) {
                return root();
            }
            if (path.startsWith("/")) {
                return root().node(path.substring(1));
            }
            int separator = path.indexOf('/');
            String childName = separator < 0 ? path : path.substring(0, separator);
            String remainder = separator < 0 ? "" : path.substring(separator + 1);
            BlockingMemoryPreferences child;
            synchronized (children) {
                child = children.computeIfAbsent(
                        childName, key -> new BlockingMemoryPreferences(this, key));
            }
            return remainder.isEmpty() ? child : child.node(remainder);
        }

        @Override
        public boolean nodeExists(String path) {
            if (path.isEmpty()) {
                return !removed;
            }
            if (path.equals("/")) {
                return true;
            }
            if (path.startsWith("/")) {
                return root().nodeExists(path.substring(1));
            }
            int separator = path.indexOf('/');
            String childName = separator < 0 ? path : path.substring(0, separator);
            String remainder = separator < 0 ? "" : path.substring(separator + 1);
            BlockingMemoryPreferences child;
            synchronized (children) {
                child = children.get(childName);
            }
            return child != null && (remainder.isEmpty() || child.nodeExists(remainder));
        }

        @Override
        public void removeNode() {
            removed = true;
            if (parent != null) {
                synchronized (parent.children) {
                    parent.children.remove(name);
                }
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String absolutePath() {
            if (parent == null) {
                return "/";
            }
            String parentPath = parent.absolutePath();
            return (parentPath.equals("/") ? parentPath : parentPath + "/") + name;
        }

        @Override
        public boolean isUserNode() {
            return true;
        }

        @Override
        public String toString() {
            return "BlockingMemoryPreferences[" + absolutePath() + "]";
        }

        @Override
        public void flush() throws BackingStoreException {
            Map<String, String> batch = new LinkedHashMap<>(pending.get());
            pending.remove();

            boolean delay = false;
            CountDownLatch copiedLatch;
            CountDownLatch releaseLatch;
            synchronized (commitMonitor) {
                if (!delayedFlushClaimed && delayedSnapshot != null
                        && Objects.equals(Thread.currentThread().getName(), delayedThreadName)
                        && containsSnapshot(batch, delayedSnapshot)) {
                    delayedFlushClaimed = true;
                    delay = true;
                }
                copiedLatch = delayedFlushCopied;
                releaseLatch = releaseDelayedFlush;
            }
            if (delay) {
                copiedLatch.countDown();
                try {
                    if (!releaseLatch.await(10, TimeUnit.SECONDS)) {
                        throw new BackingStoreException(
                                "test did not release delayed preferences flush");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BackingStoreException("delayed preferences flush interrupted");
                }
            }

            synchronized (commitMonitor) {
                values.putAll(batch);
                committedBatches.add(new LinkedHashMap<>(batch));
                commitMonitor.notifyAll();
            }
        }

        @Override
        public void sync() throws BackingStoreException {
            flush();
        }

        @Override
        public void addPreferenceChangeListener(PreferenceChangeListener listener) {
            Objects.requireNonNull(listener);
        }

        @Override
        public void removePreferenceChangeListener(PreferenceChangeListener listener) {
            Objects.requireNonNull(listener);
        }

        @Override
        public void addNodeChangeListener(NodeChangeListener listener) {
            Objects.requireNonNull(listener);
        }

        @Override
        public void removeNodeChangeListener(NodeChangeListener listener) {
            Objects.requireNonNull(listener);
        }

        @Override
        public void exportNode(OutputStream output) throws IOException {
            output.write(snapshot().toString().getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void exportSubtree(OutputStream output) throws IOException {
            exportNode(output);
        }

        private BlockingMemoryPreferences root() {
            BlockingMemoryPreferences current = this;
            while (current.parent != null) {
                current = current.parent;
            }
            return current;
        }
    }
}
