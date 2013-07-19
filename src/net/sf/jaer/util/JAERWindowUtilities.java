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
import java.awt.Toolkit;
import java.util.logging.Logger;

import javax.swing.JFrame;

/**
 * Use static methods for window handling.
 *
 * @author tobi
 */
public class JAERWindowUtilities {
    
    final static int WINDOWS_TASK_BAR_HEIGHT=100; // accounts for task bar at bottom, don't want window to underlap it
    private static int lowerInset=WINDOWS_TASK_BAR_HEIGHT; // filled in from windows screen inset
    static Logger log=Logger.getLogger("JAERWindowUtilities");
    
    /** Creates a new instance of JAERWindowUtilities */
    public JAERWindowUtilities() {
    }
    
    /** The preferred settings are loaded based on window name. A windows which would be displayed partly off-screen is moved to originate at 0,0.
     * A window which would be too tall or wide is resized to screen size.
     * @param frame JFrame
     */
    public static void constrainFrameSizeToScreenSize(JFrame frame) {
//        Properties settings = new Properties();
        // if this file does not already exist, create an empty one
//        try {
//            settings.load(new FileInputStream("configuration.props"));
//        } catch (FileNotFoundException fnfe) {
//            settings.store (new FileOutputStream ("configuration.props"),
//                            "Window settings");
//        }
        boolean resize=false; // set true if window is too big for screen
        String name = frame.getName();
        
        try{
            Point loc=frame.getLocationOnScreen();
            Dimension dim=frame.getSize();
            
            int x = loc.x;
            int y = loc.y;
            int w = dim.width;
            int h = dim.height;
            Dimension sd=Toolkit.getDefaultToolkit().getScreenSize();
            // determine the height of the windows taskbar by this roundabout proceedure
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] gs = ge.getScreenDevices();
            if(gs!=null && gs.length>0){
                GraphicsDevice gd = gs[0];
                GraphicsConfiguration[] gc = gd.getConfigurations();
                if(gc!=null && gc.length>0){
                    Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(gc[0]);
                    lowerInset=insets.bottom*2; // TODO tobi had to make bigger to handle FilterPanel resize
                }
            }
//        if(x+w>sd.width || y+h>sd.height) {
//            log.info("window extends over edge of screen, moving back to origin");
//            x=y=0;
//        }
            if(h>sd.height-lowerInset) {
                log.info("window height ("+h+") is bigger than screen height minus WINDOWS_TASK_BAR_HEIGHT ("+(sd.height-WINDOWS_TASK_BAR_HEIGHT)+"), resizing height");
                h=sd.height- lowerInset;
                resize=true;
            }
            if(w>sd.width) {
                log.info("window width ("+w+") is bigger than screen width ("+(sd.width)+"), resizing height");
                w=sd.width;
                resize=true;
            }
            
//        frame.setLocation(x,y); // don't move it, just contrain size
            if (resize) {
                frame.setSize(new Dimension(w, h));
            }
            frame.validate();
        } catch (IllegalComponentStateException e) {
            log.warning(e.toString() + ": not constraining window size to screen");
            return;
        }
        
    }
    
}
