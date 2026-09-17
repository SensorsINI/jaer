package net.sf.jaer.util;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import net.sf.jaer.JaerConstants;

/**
 * Save and restore {@link JFrame} bounds from the {@code WindowSaver} Preferences
 * node (under {@link net.sf.jaer.JaerConstants#PREFS_ROOT}).
 * <p>
 * JAERViewer registers one instance as a JVM-wide AWT listener. On
 * {@code WINDOW_OPENED} it {@link #loadSettings}; on {@code WINDOW_CLOSING} it
 * {@link #saveOneFrame} so close/reopen does not wait for process exit.
 * Shutdown still calls {@link #saveSettings} for anything still in
 * {@link #framemap}.
 *
 * <h2>Preference keys</h2>
 * Key is {@link JFrame#getName()} with spaces stripped ({@code AEViewer-0},
 * {@code UsbTuning}, {@code Biasgen}), else the title. <b>Every HW configuration
 * frame is named {@code Biasgen}</b>, so NRV and Davis share one saved size —
 * a stale 915 px height from one chip used to reopen on another.
 *
 * <h2>Restore contract ({@link #loadSettings})</h2>
 * <ol>
 * <li>{@link DontRestore}: do nothing.</li>
 * <li>No saved {@code .x}: leave the caller's pack/location, except a first-run
 * {@code AEViewer} which gets {@link #firstRunAeViewerBounds()}.</li>
 * <li>{@link DontResize} (USB tuning, prefs dialog, …): restore origin only.
 * Never apply saved w/h. After {@code setVisible}, pack again with
 * {@link #runAfterQueuedRestores} so this listener cannot race {@code pack()}.</li>
 * <li>Otherwise restore x,y,w,h, then <b>cap</b> to {@link #usableBounds()}
 * (task-bar work area) and <b>clamp</b> origin so the box stays on screen.
 * Do not snap to 0,0. Always {@code setSize} to the (capped) saved size.</li>
 * </ol>
 *
 * <h2>Bugs this is meant to prevent</h2>
 * Missing prefs used to default to 10,10 / 500×500 and always {@code setLocation},
 * covering AEViewer. A saved height larger than the work area (915 vs ~800)
 * used to log “extends over edge, moving back to UL origin” and slam x,y to 0.
 * {@code OFFSET_FROM_SAME} used to shift a <em>disposed</em> previous frame with
 * the same name (Biasgen close/reopen). A wrapping {@code JTextArea} could make
 * {@code pack()} taller than the screen; {@link #clampToScreen} caps size first.
 *
 * <h2>Logging</h2>
 * INFO (console): name+title when bounds are capped or moved, first-run AEViewer,
 * DontRestore. FINE (rotating {@code %t/jaer/jAER-%g.log}): WINDOW_OPENED, skip
 * when no prefs, apply, save. Regression: {@code UsbTuningFrameDemo}.
 *
 * @see DontResize
 * @see DontRestore
 * @see #placeAdjacent
 */
public class WindowSaver implements AWTEventListener {

    Preferences preferences = null;
    static final Logger log = Logger.getLogger("net.sf.jaer");
    /**
     * Historical task-bar guess. {@link #loadSettings} uses {@link #usableBounds()}
     * instead; keep the field so old call sites still compile.
     */
    public final int WINDOWS_TASK_BAR_HEIGHT = 100;
    /**
     * Offset a <em>still-showing</em> second frame with the same
     * {@link #windowKey}. Disposed/hidden previous frames are replaced in
     * {@link #framemap} with no offset (Biasgen close/reopen).
     */
    public final int OFFSET_FROM_SAME = 20;
    private HashMap<String, Integer> lastframemap = new HashMap();
    /**
     * Fallback when a saved width/height is missing or invalid. Not applied on
     * first open of a non-AEViewer frame (caller pack stands). First-open
     * {@code AEViewer} uses {@link #firstRunAeViewerBounds()}.
     */
    public final int DEFAULT_WIDTH = 500, DEFAULT_HEIGHT = 500;
    /** Inset from the usable-screen origin for a first-run AEViewer. */
    public static final int FIRST_RUN_MARGIN = 10;
    private HashMap<String, JFrame> framemap = new HashMap(); // last frame per windowKey

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
     * Called when event is dispatched. {@code WINDOW_OPENED} restores bounds;
     * {@code WINDOW_CLOSING} writes this frame so close/reopen does not wait
     * for JVM shutdown.
     */
    @Override
    public void eventDispatched(AWTEvent evt) {
        try {
            if (!(evt instanceof WindowEvent)) {
                return;
            }
            ComponentEvent cev = (ComponentEvent) evt;
            if (!(cev.getComponent() instanceof JFrame)) {
                return;
            }
            JFrame frame = (JFrame) cev.getComponent();
            if (evt.getID() == WindowEvent.WINDOW_OPENED) {
                log.fine(String.format("WINDOW_OPENED %s DontResize=%s bounds=%d,%d %dx%d",
                        describe(frame),
                        frame instanceof DontResize,
                        frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight()));
                loadSettings(frame);
            } else if (evt.getID() == WindowEvent.WINDOW_CLOSING) {
                saveOneFrame(frame);
            }
        } catch (Exception ex) {
            log.warning(ex.toString());
        }
    }

    static String describe(JFrame frame) {
        if (frame == null) {
            return "null";
        }
        String n = frame.getName();
        String t = frame.getTitle();
        return String.format("name=%s title=\"%s\"", n == null ? "" : n, t == null ? "" : t);
    }

    /**
     * Single-display work area ({@link GraphicsEnvironment#getMaximumWindowBounds()}),
     * not the virtual desktop and not {@code screenSize} minus a guessed task bar.
     */
    public static Rectangle usableBounds() {
        Dimension sd = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (usable != null && usable.width > 0 && usable.height > 0) {
            return usable;
        }
        return new Rectangle(0, 0, sd.width, sd.height);
    }

    /**
     * Put {@code child} to the right of {@code owner}, or to the left if it
     * would go off-screen. Used for first-open HW config / similar tools.
     */
    public static void placeAdjacent(Window owner, Window child) {
        if (owner == null || child == null) {
            return;
        }
        Rectangle usable = usableBounds();
        int gap = 8;
        int x;
        int y;
        try {
            Point p = owner.getLocationOnScreen();
            x = p.x + owner.getWidth() + gap;
            y = p.y;
            if (x + child.getWidth() > usable.x + usable.width) {
                x = p.x - child.getWidth() - gap;
            }
        } catch (IllegalComponentStateException e) {
            child.setLocationRelativeTo(owner);
            return;
        }
        if (x < usable.x) {
            x = usable.x;
        }
        if (y + child.getHeight() > usable.y + usable.height) {
            y = usable.y + usable.height - child.getHeight();
        }
        if (y < usable.y) {
            y = usable.y;
        }
        child.setLocation(x, y);
        log.fine(String.format("placeAdjacent child=%s at %d,%d next to owner=%s",
                child.getName(), x, y, owner.getName()));
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
     * Restore last bounds for {@link #windowKey(JFrame)}. Missing prefs leave
     * the caller's location and size (do not default to 10,10 / 500x500),
     * except an {@code AEViewer} which gets {@link #firstRunAeViewerBounds()}.
     * A saved size that does not fit the usable screen is capped and the origin
     * is clamped; the window is not slammed to 0,0.
     */
    public void loadSettings(final JFrame frame) throws IOException {
        if (frame instanceof DontRestore) {
            log.info("DontRestore, not loading settings for " + describe(frame));
            return;
        }
        final String name = windowKey(frame);
        String loadKey = name;
        if (!isPreference(name + ".x")) {
            String titleKey = frame.getTitle() == null ? null : frame.getTitle().replaceAll(" ", "");
            if (titleKey != null && !titleKey.equals(name) && isPreference(titleKey + ".x")) {
                log.info("no prefs for " + name + ", using legacy title key " + titleKey
                        + " (" + describe(frame) + ")");
                loadKey = titleKey;
            }
        }

        if (!isPreference(loadKey + ".x")) {
            if (isAeViewerFrame(frame)) {
                applyFirstRunAeViewerBounds(frame, name);
                return;
            }
            log.fine(String.format("no saved origin for %s key=%s; leaving %d,%d %dx%d",
                    describe(frame), loadKey, frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight()));
            rememberFrame(name, frame);
            return;
        }

        int x = preferences.getInt(loadKey + ".x", 10);
        int y = preferences.getInt(loadKey + ".y", 10);
        int w = preferences.getInt(loadKey + ".w", DEFAULT_WIDTH);
        int h = preferences.getInt(loadKey + ".h", DEFAULT_HEIGHT);
        log.fine(String.format("loadSettings %s key=%s prefs %d,%d %dx%d DontResize=%s",
                describe(frame), loadKey, x, y, w, h, frame instanceof DontResize));
        if (frame instanceof DontResize) {
            restoreOriginOnly(frame, name, x, y);
            return;
        }

        Rectangle usable = usableBounds();
        int origX = x;
        int origY = y;
        int origW = w;
        int origH = h;
        boolean resize = false;
        if (w > usable.width) {
            w = usable.width;
            resize = true;
        }
        if (h > usable.height) {
            h = usable.height;
            resize = true;
        }
        if (w < 1) {
            w = Math.min(DEFAULT_WIDTH, usable.width);
            resize = true;
        }
        if (h < 1) {
            h = Math.min(DEFAULT_HEIGHT, usable.height);
            resize = true;
        }
        if (x + w > usable.x + usable.width) {
            x = usable.x + usable.width - w;
        }
        if (y + h > usable.y + usable.height) {
            y = usable.y + usable.height - h;
        }
        if (x < usable.x) {
            x = usable.x;
        }
        if (y < usable.y) {
            y = usable.y;
        }

        JFrame previous = framemap.get(name);
        if (previous != null && previous != frame && previous.isShowing()) {
            int offset = lastframemap.containsKey(name) ? lastframemap.get(name) : 0;
            offset += OFFSET_FROM_SAME;
            x += offset;
            y += offset;
            lastframemap.put(name, offset);
        }

        if (resize || x != origX || y != origY) {
            log.info(String.format("%s saved %d,%d %dx%d -> %d,%d %dx%d (usable %d,%d %dx%d)",
                    describe(frame), origX, origY, origW, origH, x, y, w, h,
                    usable.x, usable.y, usable.width, usable.height));
        }

        final boolean resize2 = resize;
        final int w2 = w, h2 = h, x2 = x, y2 = y;
        SwingUtilities.invokeLater(() -> {
            log.fine(String.format("apply %s location %d,%d size %dx%d (capped=%s)",
                    describe(frame), x2, y2, w2, h2, resize2));
            frame.setSize(new Dimension(w2, h2));
            frame.setLocation(x2, y2);
            framemap.put(name, frame);
            frame.validate();
        });
    }

    private void rememberFrame(String name, JFrame frame) {
        SwingUtilities.invokeLater(() -> framemap.put(name, frame));
    }

    /**
     * {@link net.sf.jaer.graphics.AEViewer} uses {@code AEViewer} then
     * {@code AEViewer-N}. Do not match {@code AEViewerPreferences} and similar.
     */
    static boolean isAeViewerFrame(JFrame frame) {
        if (frame == null || frame instanceof DontResize || frame instanceof DontRestore) {
            return false;
        }
        String n = frame.getName();
        return n != null && (n.equals("AEViewer") || n.startsWith("AEViewer-"));
    }

    /**
     * First-open AEViewer: half the single-monitor work-area width, near the
     * top-left, filling the remaining usable height. Uses
     * {@link Toolkit#getScreenSize()} so a virtual desktop spanning several
     * monitors does not make the window one full wall wide.
     */
    public static Rectangle firstRunAeViewerBounds() {
        Rectangle usable = usableBounds();
        Dimension sd = Toolkit.getDefaultToolkit().getScreenSize();
        int monitorW = sd != null && sd.width > 0 ? sd.width : usable.width;
        int monitorH = sd != null && sd.height > 0 ? sd.height : usable.height;
        int workW = Math.min(usable.width, monitorW);
        int workH = Math.min(usable.height, monitorH);
        int x = usable.x + FIRST_RUN_MARGIN;
        int y = usable.y + FIRST_RUN_MARGIN;
        int w = Math.max(workW / 2, 1);
        int h = Math.max(workH - 2 * FIRST_RUN_MARGIN, 1);
        if (x + w > usable.x + usable.width) {
            w = Math.max(usable.x + usable.width - x, 1);
        }
        if (y + h > usable.y + usable.height) {
            h = Math.max(usable.y + usable.height - y, 1);
        }
        return new Rectangle(x, y, w, h);
    }

    private void applyFirstRunAeViewerBounds(JFrame frame, String name) {
        Rectangle b = firstRunAeViewerBounds();
        int x = b.x;
        int y = b.y;
        JFrame previous = framemap.get(name);
        if (previous != null && previous != frame && previous.isShowing()) {
            int offset = lastframemap.containsKey(name) ? lastframemap.get(name) : 0;
            offset += OFFSET_FROM_SAME;
            x += offset;
            y += offset;
            lastframemap.put(name, offset);
        }
        log.info(String.format("no saved size for %s; first-run %d,%d %dx%d",
                describe(frame), x, y, b.width, b.height));
        final int x2 = x, y2 = y, w2 = b.width, h2 = b.height;
        SwingUtilities.invokeLater(() -> {
            frame.setSize(new Dimension(w2, h2));
            frame.setLocation(x2, y2);
            framemap.put(name, frame);
            frame.validate();
        });
    }

    /**
     * Packed {@link DontResize} frames keep {@code pack()} size. Restore origin
     * only: a stale saved height (from a previous stretch) must not trigger
     * off-screen snapping to 0,0 or a resize log.
     */
    private void restoreOriginOnly(JFrame frame, String name, int x, int y) {
        Dimension sd = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int maxX = usable != null ? usable.x + usable.width : sd.width;
        int maxY = usable != null ? usable.y + usable.height : sd.height;
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x >= maxX) {
            x = 0;
        }
        if (y >= maxY) {
            y = 0;
        }
        JFrame previous = framemap.get(name);
        if (previous != null && previous != frame && previous.isShowing()) {
            int offset = lastframemap.containsKey(name) ? lastframemap.get(name) : 0;
            offset += OFFSET_FROM_SAME;
            x += offset;
            y += offset;
            lastframemap.put(name, offset);
        }
        log.fine(String.format("DontResize %s restore origin only to %d,%d (ignoring saved size)", name, x, y));
        final int x2 = x;
        final int y2 = y;
        SwingUtilities.invokeLater(() -> {
            log.fine(String.format("DontResize %s setLocation %d,%d (size stays %dx%d)",
                    name, x2, y2, frame.getWidth(), frame.getHeight()));
            frame.setLocation(x2, y2);
            framemap.put(name, frame);
            frame.validate();
        });
    }

    /**
     * Keep the frame on the usable screen. Caps width/height first so a
     * too-tall pack cannot be shoved to y=0 and still overfill the display.
     */
    public static void clampToScreen(Window window) {
        if (window == null) {
            return;
        }
        Dimension sd = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int minX = usable != null ? usable.x : 0;
        int minY = usable != null ? usable.y : 0;
        int maxW = usable != null ? usable.width : sd.width;
        int maxH = usable != null ? usable.height : sd.height;
        int x = window.getX();
        int y = window.getY();
        int w = window.getWidth();
        int h = window.getHeight();
        int capW = Math.min(w, maxW);
        int capH = Math.min(h, maxH);
        if (x + capW > minX + maxW) {
            x = minX + maxW - capW;
        }
        if (y + capH > minY + maxH) {
            y = minY + maxH - capH;
        }
        if (x < minX) {
            x = minX;
        }
        if (y < minY) {
            y = minY;
        }
        if (x != window.getX() || y != window.getY() || capW != w || capH != h) {
            log.fine(String.format("clampToScreen %s %d,%d %dx%d -> %d,%d %dx%d (usable %d,%d %dx%d)",
                    window.getName(), window.getX(), window.getY(), w, h, x, y, capW, capH,
                    minX, minY, maxW, maxH));
            window.setBounds(x, y, capW, capH);
        }
    }

    /**
     * True when {@link #loadSettings} has a stored origin for this frame name
     * (or a legacy title key). Packed frames with no origin should keep the
     * caller's {@code setLocationRelativeTo}.
     */
    public boolean hasSavedOrigin(JFrame frame) {
        if (frame == null || frame instanceof DontRestore) {
            return false;
        }
        String name = windowKey(frame);
        if (isPreference(name + ".x")) {
            return true;
        }
        String titleKey = frame.getTitle() == null ? null : frame.getTitle().replaceAll(" ", "");
        return titleKey != null && !titleKey.equals(name) && isPreference(titleKey + ".x");
    }

    /**
     * {@link #loadSettings} applies bounds on a later EDT turn. After every
     * session frame has been {@code setVisible} (so {@code WINDOW_OPENED} has
     * already queued those restores), run {@code r} on the following EDT turn.
     */
    public static void runAfterQueuedRestores(Runnable r) {
        SwingUtilities.invokeLater(r);
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
        StringBuilder sb = new StringBuilder();
        sb.append("saved window settings for \n");
        Iterator it = framemap.keySet().iterator();
        while (it.hasNext()) {
            String name = (String) it.next();
            JFrame frame = (JFrame) framemap.get(name);
            if (frame == null) {
                continue;
            }
            saveOneFrame(frame);
            sb.append(name).append(" ").append(frame.getX()).append(",").append(frame.getY());
            if (!(frame instanceof DontResize)) {
                sb.append(" ").append(frame.getWidth()).append("x").append(frame.getHeight());
            }
            sb.append("\n");
        }
        preferences.flush();
        log.fine(sb.toString());
    }

    /** Persist one frame immediately so close/reopen does not wait for shutdown. */
    public void saveOneFrame(JFrame frame) {
        if (frame == null || frame instanceof DontRestore) {
            return;
        }
        if (JaerConstants.skipPreferenceWriteOnExit) {
            return;
        }
        String name = windowKey(frame);
        framemap.put(name, frame);
        preferences.putInt(name + ".x", frame.getX());
        preferences.putInt(name + ".y", frame.getY());
        if (frame instanceof DontResize) {
            preferences.remove(name + ".w");
            preferences.remove(name + ".h");
        } else {
            preferences.putInt(name + ".w", frame.getWidth());
            preferences.putInt(name + ".h", frame.getHeight());
        }
        log.fine(String.format("saved %s %d,%d %dx%d", describe(frame),
                frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight()));
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
     * Packed dialogs (USB tuning, prefs, file info, …). {@link #loadSettings}
     * restores x,y only and never writes w/h. Call {@code pack()} after
     * {@link #runAfterQueuedRestores} because restore runs on a later EDT turn
     * than {@code setVisible}.
     */
    public interface DontResize {
    }

    /**
     * Skip restore and skip {@link #saveOneFrame} (transient dialogs).
     */
    public interface DontRestore {
    }
}
