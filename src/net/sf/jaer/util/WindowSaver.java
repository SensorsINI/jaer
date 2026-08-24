package net.sf.jaer.util;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import net.sf.jaer.JaerConstants;

/**
 * adapted from O'Reilly book Swing Hacks by Marinacci and Adamson ISBN
 * 0-596-00907-0. Used to save and restore window positions. Static methods
 * allow explicit saving and restoring, or the user can do the following in
 * their main class:<br> <code>
 * Toolkit.getDefaultToolkit().addAWTEventListener(windowSaver,AWTEvent.WINDOW_EVENT_MASK);
 * </code>. <br>
 * Then (magically) global window opening events will result in callbacks to
 * eventDispatched which loads saved settings, keyed on {@link JFrame#getName()}
 * when that is set (AEViewer uses {@code AEViewer-0}, {@code AEViewer-1}, …),
 * otherwise on the frame title. A class could
 * add a ShutdownHook to save the last window settings:
 * <pre>
 * Runtime.getRuntime().addShutdownHook(new Thread(){
 * public void run(){
 * if(windowSaver!=null){
 * try{
 * windowSaver.saveSettings();
 * }catch(IOException e){
 * e.printStackTrace();
 * }
 * }
 * }
 * });
 * </pre>
 * <p>
 * Unexpected behavior can result if the user application resizes its own
 * windows after the window settings are loaded.
 *
 *
 */
public class WindowSaver implements AWTEventListener {

    private final Preferences preferences;
    static final Logger log = Logger.getLogger("net.sf.jaer");
    /* Accounts for task bar at bottom; don't want window to underlap it. */
    public final int WINDOWS_TASK_BAR_HEIGHT = 100;
    /**
     * Offset from last window with same name.
     */
    public final int OFFSET_FROM_SAME = 20;
    private final Object stateLock = new Object();
    private final Map<String, Integer> lastframemap = new HashMap<>();
    /**
     * Default width and height values. Width and height are not set for a
     * window unless preferences are saved
     */
    public final int DEFAULT_WIDTH = 500, DEFAULT_HEIGHT = 500;
    private final Map<String, JFrame> framemap = new HashMap<>(); // this hashmap maps from windows to settings
    private final Map<JFrame, FrameRegistration> registrations = new IdentityHashMap<>();
    private final Map<String, FrameSnapshot> snapshots = new HashMap<>();
    private int lowerInset = WINDOWS_TASK_BAR_HEIGHT; // filled in from windows screen inset
    private long captureGeneration;
    private long snapshotGeneration;
    private long persistedSnapshotGeneration = -1;
    private boolean captureQueued;
    private boolean captureDirty;
    private boolean closing;
    private boolean closed;
    private Thread closeSaveThread;

    private static final class FrameRegistration {

        final String name;
        final JFrame frame;
        final ComponentListener componentListener;
        final WindowStateListener stateListener;
        final WindowListener lifecycleListener;

        FrameRegistration(String name, JFrame frame, ComponentListener componentListener,
                WindowStateListener stateListener, WindowListener lifecycleListener) {
            this.name = name;
            this.frame = frame;
            this.componentListener = componentListener;
            this.stateListener = stateListener;
            this.lifecycleListener = lifecycleListener;
        }
    }

    private static final class FrameTarget {

        final String name;
        final JFrame frame;

        FrameTarget(String name, JFrame frame) {
            this.name = name;
            this.frame = frame;
        }
    }

    private static final class FrameSnapshot {

        final int x;
        final int y;
        final int width;
        final int height;
        final int extendedState;

        FrameSnapshot(int x, int y, int width, int height, int extendedState) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.extendedState = extendedState;
        }
    }

    /**
     * Creates a new instance of WindowSaver.
     *
     * @param o the object for which to save
     * @param preferences the user preferences to save to, in node "WindowSaver"
     */
    public WindowSaver(Object o, Preferences preferences) {
        this.preferences = preferences.node("WindowSaver");
    }

    /**
     * Called when event is dispatched. WindowEvent.WINDOW_OPENED events for
     * JFrames are processed here to loadSettings.
     *
     * @param evt the AWTEvent. Only WINDOW_OPENED events are processed to
     * loadSettings
     * @see #loadSettings
     */
    @Override
    public void eventDispatched(AWTEvent evt) {
        try {
            if (evt.getID() == WindowEvent.WINDOW_OPENED) {
                ComponentEvent cev = (ComponentEvent) evt;
                if (cev.getComponent() instanceof JFrame) {
//                    log.info("event: " + evt);
                    JFrame frame = (JFrame) cev.getComponent();
                    loadSettings(frame);
                }
            }
        } catch (Exception ex) {
            log.warning(ex.toString());
        }
    }

    /**
     * Preference key for a frame: {@link JFrame#getName()} when set (used by
     * AEViewer instances as {@code AEViewer-0}, {@code AEViewer-1}, … so two
     * viewers with the same title restore independently), otherwise the title
     * with spaces removed.
     */
    private String windowKey(JFrame frame) {
        String n = frame.getName();
        if (n != null && !n.isEmpty()) {
            return n.replaceAll(" ", "");
        }
        String t = frame.getTitle();
        return t == null ? "JFrame" : t.replaceAll(" ", "");
    }

    /**
     * The preferred settings are loaded based on {@link #windowKey(JFrame)}. A
     * window which would be displayed partly off-screen is moved to originate
     * at 0,0. A window which would be too tall or wide is resized to screen
     * size.
     *
     * @param frame JFrame to load settings for
     */
    public void loadSettings(final JFrame frame) throws IOException {
        if (frame == null) {
            return;
        }
        runOnEdtAndWait(() -> loadSettingsOnEdt(frame));
    }

    private void loadSettingsOnEdt(final JFrame frame) {
        synchronized (stateLock) {
            if (closing || closed || registrations.containsKey(frame)) {
                return;
            }
        }
        boolean resize = false; // set true if window is too big for screen
        if(frame instanceof DontRestore){
            log.info("Frame implements DontRestore, not loading settings for it");
            return;
        }
        final String name = windowKey(frame);
        String loadKey = name;
        if (!isPreference(name + ".x")) {
            String titleKey = frame.getTitle() == null ? null : frame.getTitle().replaceAll(" ", "");
            if (titleKey != null && !titleKey.equals(name) && isPreference(titleKey + ".x")) {
                log.info("no prefs for " + name + ", using legacy title key " + titleKey);
                loadKey = titleKey;
            }
        }

        // screen UL corner is 0,0
        int x = preferences.getInt(loadKey + ".x", 10);
        int y = preferences.getInt(loadKey + ".y", 10); // UL corner
        int w = preferences.getInt(loadKey + ".w", DEFAULT_WIDTH);
        int h = preferences.getInt(loadKey + ".h", DEFAULT_HEIGHT);
        int extendedState = preferences.getInt(loadKey + ".state", frame.getExtendedState());
        if (w != DEFAULT_WIDTH | h != DEFAULT_HEIGHT) {
            resize = true;
        }
        if(x!=0 || y!=0 || w!=DEFAULT_WIDTH || h!=DEFAULT_HEIGHT){
            log.fine(String.format("found non default window %s preferences x=%d y=%d w=%d h=%d",
                    name,x,y,w,h));
        }
        Dimension sd = Toolkit.getDefaultToolkit().getScreenSize();

        // determine the height of the windows taskbar by this roundabout proceedure
        // TODO tobi removed this because it was causing a runtime native code exception using NVIDIA 181.22 driver with win xp
        // replaced by hardcoded lowerInset
        lowerInset = 64;

        Rectangle windowBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (windowBounds != null) {
            lowerInset = sd.height - windowBounds.height;
        }

        // any call to getConfigurations or getConfiguration for GraphicsDevice causes JOGL to drop back to GDI rendering, reason unknown
//        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
////        GraphicsDevice[] gs=ge.getScreenDevices(); // TODO it could be that remote session doesn't show screen that used to be used. Should check that we are not offscreen. Otherwise registy edit is required to show window!
//        if (ge != null) {
//            GraphicsDevice gd = ge.getDefaultScreenDevice();
////        } // TODO it could be that remote session doesn't show screen that used to be used. Should check that we are not offscreen. Otherwise registy edit is required to show window!
//
////        if(gs!=null&&gs.length>0) {
////            if(gs.length>1){
//////                log.info("There are "+gs.length+" GraphicsDevice's found; using first one which is "+gs[0].getIDstring());
////            }
//            // TODO tobi commented out the calls below because they seems to trigger the OpenGL exceptions in JOGL on context creation; see
//            // http://forum.jogamp.org/Jogl-Using-Wrong-Generic-Graphics-Adapter-td4033216i20.html#a4033747
//            // and https://jogamp.org/bugzilla/show_bug.cgi?id=1105
////            GraphicsDevice gd=gs[0];
//            if (gd != null) {
//                GraphicsConfiguration[] gc = gd.getConfigurations();
//                if (gc != null && gc.length > 0) {
//                    if (gc.length > 1) {
////                    log.info("There are "+gc.length+" GraphicsConfiguration's found; using first one which is "+gc[0].toString());
//                    }
//                    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc[0]);
//                    lowerInset = insets.bottom;
//                }
//            }
//        }
        if (x < 0) {
            log.info("window x origin is <0, moving back to zero");
            x = 0;
        }
        if (y < 0) {
            log.info(String.format("window y origin=%d is < 0, moving back to 0",y));
            y = 0;
        }
        if (x + w > sd.width || y + h > sd.height) {
            log.info("window extends over edge of screen, moving back to UL origin");
            x = 0;
            y=0;
        }
        if (h > sd.height - lowerInset) {
            log.log(Level.INFO, "window height ({0}) is bigger than screen height minus WINDOWS_TASK_BAR_HEIGHT ({1}), resizing height", new Object[]{h, sd.height - WINDOWS_TASK_BAR_HEIGHT});
            h = sd.height - lowerInset;
            resize = true;
        }
        if (w > sd.width) {
            log.log(Level.INFO, "window width ({0}) is bigger than screen width ({1}), resizing height", new Object[]{w, sd.width});
            w = sd.width;
            resize = true;
        }
        // check for last window with same name, if there is one, offset this one by OFFSET_FROM_SAME
        synchronized (stateLock) {
            if (framemap.containsKey(name)) { // we had a frame already with this name
                int offset = lastframemap.containsKey(name) ? lastframemap.get(name) : 0;
                offset += OFFSET_FROM_SAME;
//            Insets insets=frame.getInsets();
                x += offset;//+insets.left;
                y += offset;//+insets.top;
                lastframemap.put(name, offset);
            }
        }

        if (resize && !(frame instanceof DontResize)) {
            frame.setSize(new Dimension(w, h));
        }
        frame.setLocation(x, y);  // sets UL corner position to these values
        frame.setExtendedState(extendedState);
//        log.info("loaded settings location for "+frame.getName());
        installListenersOnEdt(name, frame);
        frame.validate();
        captureFrameOnEdt(name, frame);

    }

    private void installListenersOnEdt(final String name, final JFrame frame) {
        ComponentListener componentListener = new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent event) {
                requestCapture();
            }

            @Override
            public void componentResized(ComponentEvent event) {
                requestCapture();
            }
        };
        WindowStateListener stateListener = event -> requestCapture();
        WindowListener lifecycleListener = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                requestCapture();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                requestCapture();
            }
        };

        frame.addComponentListener(componentListener);
        frame.addWindowStateListener(stateListener);
        frame.addWindowListener(lifecycleListener);
        synchronized (stateLock) {
            framemap.put(name, frame);
            registrations.put(frame, new FrameRegistration(name, frame, componentListener,
                    stateListener, lifecycleListener));
        }
    }

    private void requestCapture() {
        final long generation;
        synchronized (stateLock) {
            if (closing || closed) {
                return;
            }
            captureDirty = true;
            if (captureQueued) {
                return;
            }
            captureQueued = true;
            generation = captureGeneration;
        }
        SwingUtilities.invokeLater(() -> runQueuedCaptureOnEdt(generation));
    }

    private void runQueuedCaptureOnEdt(long generation) {
        synchronized (stateLock) {
            if (closing || closed || generation != captureGeneration) {
                if (generation == captureGeneration) {
                    captureQueued = false;
                    captureDirty = false;
                }
                return;
            }
            captureDirty = false;
        }

        captureAllFramesOnEdt();
        try {
            persistSnapshots();
        } catch (BackingStoreException ex) {
            log.log(Level.WARNING, "Could not persist window settings", ex);
        }

        boolean repeat;
        synchronized (stateLock) {
            if (closing || closed || generation != captureGeneration) {
                captureQueued = false;
                captureDirty = false;
                return;
            }
            repeat = captureDirty;
            if (!repeat) {
                captureQueued = false;
            }
        }
        if (repeat) {
            SwingUtilities.invokeLater(() -> runQueuedCaptureOnEdt(generation));
        }
    }

    private void captureAllFramesOnEdt() {
        List<FrameTarget> targets = new ArrayList<>();
        synchronized (stateLock) {
            for (Map.Entry<String, JFrame> entry : framemap.entrySet()) {
                targets.add(new FrameTarget(entry.getKey(), entry.getValue()));
            }
        }
        for (FrameTarget target : targets) {
            captureFrameOnEdt(target.name, target.frame);
        }
    }

    private void captureFrameOnEdt(String name, JFrame frame) {
        FrameSnapshot snapshot = new FrameSnapshot(frame.getX(), frame.getY(),
                frame.getWidth(), frame.getHeight(), frame.getExtendedState());
        synchronized (stateLock) {
            snapshots.put(name, snapshot);
            snapshotGeneration++;
        }
    }

    // returns true if there is a stored preference
    private boolean isPreference(String name) {
        return !(preferences.get(name, null) == null);
    }

//    public int getInt(Properties props, String name, int value) {
//        String v = props.getProperty(name);
//        if(v == null) {
//            return value;
//        }
//        return Integer.parseInt(v);
//    }
    /**
     * Used to explicitly save settings. Saves the x,y and width, height settings
     * of window in preferences.
     */
    public void saveSettings() throws IOException, BackingStoreException {
        if (JaerConstants.skipPreferenceWriteOnExit) {
            log.info("skipping WindowSaver.saveSettings (preferences were reverted)");
            return;
        }
        flush();
    }

    /**
     * Drains queued capture work, snapshots all tracked frames on the EDT, and
     * persists the latest geometry and extended state. Safe to call from any
     * thread.
     */
    public void flush() throws IOException, BackingStoreException {
        boolean closeSave;
        synchronized (stateLock) {
            closeSave = closing && Thread.currentThread() == closeSaveThread;
        }
        if (closeSave) {
            persistSnapshots();
            return;
        }
        runOnEdtAndWait(() -> {
            synchronized (stateLock) {
                if (closing || closed) {
                    return;
                }
                captureGeneration++;
                captureQueued = false;
                captureDirty = false;
            }
            captureAllFramesOnEdt();
        });
        synchronized (stateLock) {
            if (closing || closed) {
                return;
            }
        }
        persistSnapshots();
    }

    /**
     * Performs the final capture, removes all listeners installed by this
     * saver, and unregisters it from the Toolkit. Repeated calls are harmless.
     */
    public void close() throws IOException, BackingStoreException {
        synchronized (stateLock) {
            if (closing || closed) {
                return;
            }
            closing = true;
            captureGeneration++;
            captureQueued = false;
            captureDirty = false;
        }

        Throwable failure = null;
        try {
            runOnEdtAndWait(this::captureAndRemoveListenersOnEdt);
        } catch (Throwable ex) {
            failure = recordFailure(failure, ex);
        }
        try {
            synchronized (stateLock) {
                closeSaveThread = Thread.currentThread();
            }
            // Preserve the long-standing virtual contract: subclasses that
            // customize saveSettings still observe exactly one final save.
            saveSettings();
        } catch (Throwable ex) {
            failure = recordFailure(failure, ex);
        } finally {
            synchronized (stateLock) {
                closeSaveThread = null;
            }
        }
        try {
            ensureFinalSnapshotPersisted();
        } catch (Throwable ex) {
            failure = recordFailure(failure, ex);
        }
        try {
            Toolkit.getDefaultToolkit().removeAWTEventListener(this);
        } catch (Throwable ex) {
            failure = recordFailure(failure, ex);
        } finally {
            synchronized (stateLock) {
                captureGeneration++;
                captureQueued = false;
                captureDirty = false;
                closing = false;
                closed = true;
            }
        }

        rethrowCloseFailure(failure);
    }

    private void ensureFinalSnapshotPersisted() throws BackingStoreException {
        synchronized (stateLock) {
            if (persistedSnapshotGeneration == snapshotGeneration) {
                return;
            }
        }
        persistSnapshots();
    }

    private void captureAndRemoveListenersOnEdt() {
        Throwable failure = null;
        try {
            captureAllFramesOnEdt();
        } catch (Throwable ex) {
            failure = recordFailure(failure, ex);
        }

        List<FrameRegistration> installed;
        synchronized (stateLock) {
            installed = new ArrayList<>(registrations.values());
        }
        for (FrameRegistration registration : installed) {
            try {
                registration.frame.removeComponentListener(registration.componentListener);
            } catch (Throwable ex) {
                failure = recordFailure(failure, ex);
            }
            try {
                registration.frame.removeWindowStateListener(registration.stateListener);
            } catch (Throwable ex) {
                failure = recordFailure(failure, ex);
            }
            try {
                registration.frame.removeWindowListener(registration.lifecycleListener);
            } catch (Throwable ex) {
                failure = recordFailure(failure, ex);
            }
        }
        synchronized (stateLock) {
            registrations.clear();
            framemap.clear();
            lastframemap.clear();
        }
        rethrowUnchecked(failure);
    }

    private static Throwable recordFailure(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        if (primary != additional) {
            primary.addSuppressed(additional);
        }
        return primary;
    }

    private static void rethrowUnchecked(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Unexpected checked failure during EDT cleanup", failure);
    }

    private static void rethrowCloseFailure(Throwable failure)
            throws IOException, BackingStoreException {
        if (failure == null) {
            return;
        }
        if (failure instanceof IOException) {
            throw (IOException) failure;
        }
        if (failure instanceof BackingStoreException) {
            throw (BackingStoreException) failure;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IOException("Unexpected failure while closing WindowSaver", failure);
    }

    private void persistSnapshots() throws BackingStoreException {
        if (JaerConstants.skipPreferenceWriteOnExit) {
            return;
        }
        while (true) {
            Map<String, FrameSnapshot> current;
            long generation;
            synchronized (stateLock) {
                current = new HashMap<>(snapshots);
                generation = snapshotGeneration;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("saved window settings for \n");
            for (Map.Entry<String, FrameSnapshot> entry : current.entrySet()) {
                String name = entry.getKey();
                FrameSnapshot snapshot = entry.getValue();
                preferences.putInt(name + ".x", snapshot.x);
                sb.append(name).append(".x=").append(snapshot.x).append('\n');
                preferences.putInt(name + ".y", snapshot.y);
                sb.append(name).append(".y=").append(snapshot.y).append('\n');
                preferences.putInt(name + ".w", snapshot.width);
                sb.append(name).append(".w=").append(snapshot.width).append('\n');
                preferences.putInt(name + ".h", snapshot.height);
                sb.append(name).append(".h=").append(snapshot.height).append('\n');
                preferences.putInt(name + ".state", snapshot.extendedState);
                sb.append(name).append(".state=").append(snapshot.extendedState).append('\n');
                sb.append("for window ").append(name);
            }
            preferences.flush();
            synchronized (stateLock) {
                if (generation == snapshotGeneration) {
                    persistedSnapshotGeneration = generation;
                    log.fine(sb.toString());
                    return;
                }
            }
        }
    }

    private void runOnEdtAndWait(Runnable operation) throws IOException {
        if (SwingUtilities.isEventDispatchThread()) {
            operation.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(operation);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the EDT", ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IOException("EDT operation failed", cause);
        }
    }

    /**
     * This static method can be used to restore the window x,y, position (but
     * not size) of a window based on the Window class name. This is a separate
     * mechanism than the instance methods saveSettings and loadSettings.
     *
     * @param window the window to restore
     * @param prefs user preferences node
     * @see #saveWindowLocation
     */
    public static void restoreWindowLocation(Window window, Preferences prefs) {
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        String name = window.getClass().getName();
        int x = prefs.getInt(name + ".XPosition", 0);
        x = (int) Math.min(scr.getWidth() - window.getWidth() - 20, x);
        int y = prefs.getInt(name + ".YPosition", 0);
        y = (int) Math.min(scr.getHeight() - window.getHeight() - 20, y);
        window.setLocation(x, y);
//        log.info("restored window "+window.getName()+" to location x,y="+x+","+y);
    }

    /**
     * This static method can be used to save the window x,y, position (but not
     * size). This static method saves the window origin but not the size, based
     * on a classname-based key in the supplied preferences node.
     *
     * @param window the window to save for
     * @param prefs user preferences node
     * @see #restoreWindowLocation
     */
    public static void saveWindowLocation(Window window, Preferences prefs) {
        String name = window.getClass().getName();
        Point p = new Point(0, 0);
        try {
            p = window.getLocationOnScreen();
        } catch (IllegalComponentStateException e) {
            p = window.getLocation();
        }
        prefs.putInt(name + ".XPosition", (int) p.getX());
        prefs.putInt(name + ".YPosition", (int) p.getY());
//        log.info("saved location for window "+name);
    }

    /**
     * This marker interface can be implemented to avoid resizing the JFrame
     */
    public interface DontResize {
    }
    
    /**
     * This marker interface can be implemented to avoid loading the stored settings for the JFRame
     */
    public interface DontRestore {
    }
}
