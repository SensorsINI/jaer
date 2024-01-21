/*
 * JAERWindowUtilities.java
 *
 * Created on June 29, 2007, 9:20 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright June 29, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.util;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Use static methods for window handling.
 *
 * @author tobi
 */
public class JAERWindowUtilities {

    final static int WINDOWS_TASK_BAR_HEIGHT = 100; // accounts for task bar at bottom, don't want window to underlap it
    private static int lowerInset = WINDOWS_TASK_BAR_HEIGHT; // filled in from windows screen inset
    private static Logger log = Logger.getLogger("net.sf.jaer");
//    private static int resizeCounter=0;
    final static int STOP_TRYING_THRESHOLD = 5;  // after this many calls on same window, give up, some feedback loop with underlying window manager such as Windows tablet mode
    private final static HashMap<JFrame, Integer> resizingCountMap = new HashMap<JFrame, Integer>();

    /**
     * Creates a new instance of JAERWindowUtilities
     */
    public JAERWindowUtilities() {
    }

    /**
     * The preferred settings are loaded based on window name. A windows which
     * would be displayed partly off-screen is moved to originate at 0,0. A
     * window which would be too tall or wide is resized to screen size.
     *
     * @param frame JFrame
     */
    public static void constrainFrameSizeToScreenSize(final JFrame frame) {
        boolean resize = false; // set true if window is too big for screen

        if (resizingCountMap.get(frame) == null) {
            resizingCountMap.put(frame, new Integer(0));
        }
        int count = resizingCountMap.get(frame);
        if (++count >= STOP_TRYING_THRESHOLD) {
            log.info("won't try to constrain window size anymore; could be some loop with underlying window manager. Try disabling tablet mode.");
            return;
        }
        resizingCountMap.put(frame, count);

        try {
            Point loc = frame.getLocationOnScreen();
            Dimension dim = frame.getSize();

            int x = loc.x;
            int y = loc.y;
            int w = dim.width;
            int h = dim.height;
            Dimension sd = Toolkit.getDefaultToolkit().getScreenSize();
            // determine the height of the windows taskbar by this roundabout proceedure
            // tobi commented code below because of JOGL or driver or java bug that causes JOGL to drop to GDI rendering.
            // see WindowSaver for more comments. Insets are now determined by calls below
            // determine the height of the windows taskbar by this roundabout proceedure
            // TODO tobi removed this because it was causing a runtime native code exception using NVIDIA 181.22 driver with win xp
            // replaced by hardcoded lowerInset
            lowerInset = 100;

            Rectangle windowBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            if (windowBounds != null) {
                lowerInset = sd.height - windowBounds.height;
            }
//            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
//            GraphicsDevice[] gs = ge.getScreenDevices();
//            if(gs!=null && gs.length>0){
//                GraphicsDevice gd = gs[0];
//                GraphicsConfiguration[] gc = gd.getConfigurations();
//                if(gc!=null && gc.length>0){
//                    Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(gc[0]);
//                    lowerInset=insets.bottom*2; // TODO tobi had to make bigger to handle FilterPanel resize
//                }
//            }
//        if(x+w>sd.width || y+h>sd.height) {
//            log.info("window extends over edge of screen, moving back to origin");
//            x=y=0;
//        }
            if (h + y > sd.height - lowerInset) {
                log.info("window height (" + h + ") is bigger than screen height minus WINDOWS_TASK_BAR_HEIGHT (" + (sd.height - WINDOWS_TASK_BAR_HEIGHT) + "), resizing height");
                h = sd.height - lowerInset - y;
                resize = true;
            }
            if (w > sd.width) {
                log.info("window width (" + w + ") is bigger than screen width (" + (sd.width) + "), resizing width");
                w = sd.width;
                resize = true;
            }

//        frame.setLocation(x,y); // don't move it, just contrain size
            final boolean resize2 = resize;
            final int w2 = w, h2 = h, x2 = x, y2 = y;
            if (resize) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        frame.setLocation(x2, y2);
                        if (resize2 && !(frame instanceof WindowSaver.DontResize)) {
                            frame.setSize(new Dimension(w2, h2));
                        }
//                    frame.validate();
                    }
                });
            }
        } catch (IllegalComponentStateException e) {
            log.warning(e.toString() + ": not constraining window size to screen");
            return;
        }

    }

}
