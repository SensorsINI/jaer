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
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * adapted from O'Reilly book Swing Hacks by Marinacci and Adamson ISBN
 * 0-596-00907-0. Used to save and restore window positions. Static methods
 * allow explicit saving and restoring, or the user can do the following in
 * their main class:<br> <code>
 * Toolkit.getDefaultToolkit().addAWTEventListener(windowSaver,AWTEvent.WINDOW_EVENT_MASK);
 * </code>. <br>
 * Then (magically) global window opening events will result in callbacks to
 * eventDispatched which loads saved settings, keyed on the frame. A class could
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

    Preferences preferences = null;
    static final Logger log = Logger.getLogger("WindowSaver");
    /* Accounts for task bar at bottom; don't want window to underlap it. */
    public final int WINDOWS_TASK_BAR_HEIGHT = 100;
    /**
     * Offset from last window with same name.
     */
    public final int OFFSET_FROM_SAME = 20;
    private HashMap<String, Integer> lastframemap = new HashMap();
    /**
     * Default width and height values. Width and height are not set for a
     * window unless preferences are saved
     */
    public final int DEFAULT_WIDTH = 500, DEFAULT_HEIGHT = 500;
    private HashMap<String, JFrame> framemap = new HashMap(); // this hashmap maps from windows to settings
    private int lowerInset = WINDOWS_TASK_BAR_HEIGHT; // filled in from windows screen inset

    /**
     * Creates a new instance of WindowSaver.
     *
     * @param o the object for which to save
     * @param preferences the user preferences to save to
     */
    public WindowSaver(Object o, Preferences preferences) {
        this.preferences = preferences;
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
     * The preferred settings are loaded based on window name. A windows which
     * would be displayed partly off-screen is moved to originate at 0,0. A
     * window which would be too tall or wide is resized to screen size.
     *
     * @param frame JFrame to load settings for
     */
    public void loadSettings(final JFrame frame) throws IOException {
        boolean resize = false; // set true if window is too big for screen
        final String name = frame.getTitle().replaceAll(" ", "");

        // screen UL corner is 0,0
        int x = preferences.getInt(name + ".x", 0);
        int y = preferences.getInt(name + ".y", 0); // UL corner
        int w = preferences.getInt(name + ".w", DEFAULT_WIDTH);
        int h = preferences.getInt(name + ".h", DEFAULT_HEIGHT);
        if (w != DEFAULT_WIDTH | h != DEFAULT_HEIGHT) {
            resize = true;
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
        if (y < lowerInset) {
            log.info("window y origin is < lowerInset, moving back to " + 0);
            y = 0;
        }
        if (x + w > sd.width || y + h > sd.height) {
            log.info("window extends over edge of screen, moving back to UL origin");
            x = 0;
            y=0;
        }
        if (h > sd.height - lowerInset) {
            log.info("window height (" + h + ") is bigger than screen height minus WINDOWS_TASK_BAR_HEIGHT (" + (sd.height - WINDOWS_TASK_BAR_HEIGHT) + "), resizing height");
            h = sd.height - lowerInset;
            resize = true;
        }
        if (w > sd.width) {
            log.info("window width (" + w + ") is bigger than screen width (" + (sd.width) + "), resizing height");
            w = sd.width;
            resize = true;
        }
        // check for last window with same name, if there is one, offset this one by OFFSET_FROM_SAME
        if (framemap.containsKey(name)) { // we had a frame already with this name
            int offset = lastframemap.containsKey(name) ? lastframemap.get(name) : 0;
            offset += OFFSET_FROM_SAME;
//            Insets insets=frame.getInsets();
            x += offset;//+insets.left;
            y += offset;//+insets.top;
            lastframemap.put(name, offset);
        }

        final boolean resize2 = resize;
        final int w2=w, h2=h, x2=x, y2=y;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (resize2 && !(frame instanceof DontResize)) {
                    frame.setSize(new Dimension(w2, h2));
                }
                frame.setLocation(x2, y2);  // sets UL corner position to these values
//        log.info("loaded settings location for "+frame.getName());
                framemap.put(name, frame);
                frame.validate();
            }
        });

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
     * Used to explicity save settings. Saves the x,y and width, height settings
     * of window in preferences.
     */
    public void saveSettings() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("saved " + preferences + " for \n");
        Iterator it = framemap.keySet().iterator();
        while (it.hasNext()) {
            String name = (String) it.next();
            JFrame frame = (JFrame) framemap.get(name);
            preferences.putInt(name + ".x", frame.getX());
            sb.append(name + ".x=" + frame.getX() + " ");
            preferences.putInt(name + ".y", frame.getY());
            sb.append(name + ".y=" + frame.getY() + " ");
            preferences.putInt(name + ".w", frame.getWidth());
            sb.append(name + ".w=" + frame.getWidth() + " ");
            preferences.putInt(name + ".h", frame.getHeight());
            sb.append(name + ".h=" + frame.getHeight() + " ");
            sb.append(" window " + name + "\n");

        }
//        log.info(sb.toString());
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
     * size). This statis method saves the window origin but not the size, based
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
     * This marker interface can be implemented to avoid resizing the window
     */
    public interface DontResize {
    }
}
