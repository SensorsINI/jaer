package ch.unizh.ini.caviar.util;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;

/** adapted from O'Reilly book Swing Hacks by Marinacci and Adamson ISBN 0-596-00907-0. Used to save and restore window positions.
 Static methods allow explicit saving and restoring, or the user can do the following in their main class:<br> <code>
 Toolkit.getDefaultToolkit().addAWTEventListener(windowSaver,AWTEvent.WINDOW_EVENT_MASK);
 </code>. <br>
 Then (magically) global window opening events will result in callbacks to eventDispatched which loads saved settings, keyed on the frame.
 A class could add a ShutdownHook to save the last window settings:
 <pre>
 Runtime.getRuntime().addShutdownHook(new Thread(){
 public void run(){
 if(windowSaver!=null){
 try{
 windowSaver.saveSettings();
 }catch(IOException e){
 e.printStackTrace();
 }
 }
 }
 });
 </pre>
 <p>
 Unexpected behavior can result if the user application resizes its own windows after the window settings are loaded.
 
 
 */
public class WindowSaver implements AWTEventListener {
    
    Preferences preferences=null;
    static Logger log=Logger.getLogger("WindowSaver");
    final int WINDOWS_TASK_BAR_HEIGHT=100; // accounts for task bar at bottom, don't want window to underlap it
    
    private HashMap framemap; // this hashmap maps from windows to settings

    private int lowerInset=WINDOWS_TASK_BAR_HEIGHT; // filled in from windows screen inset
    
    /** Creates a new instance of WindowSaver.
     @param o the object for which to save
     @param preferences the user preferences to save to
     */
    public WindowSaver(Object o, Preferences preferences) {
        this.preferences=preferences;
        framemap = new HashMap();
    }
    
    /** called when event is dispatched. WindowEvent.WINDOW_OPENED events for JFrames are processed here to loadSettings.
     @param evt the AWTEvent. Only WINDOW_OPENED events are processed to loadSettings
     @see #loadSettings
     */
    public void eventDispatched(AWTEvent evt) {
        try {
            if(evt.getID() == WindowEvent.WINDOW_OPENED) {
                ComponentEvent cev = (ComponentEvent)evt;
                if(cev.getComponent() instanceof JFrame) {
//                    log.info("event: " + evt);
                    JFrame frame = (JFrame)cev.getComponent();
                    loadSettings(frame);
                }
            }
        }catch(Exception ex) {
            log.warning(ex.toString());
        }
    }
    
    /** The preferred settings are loaded based on window name. A windows which would be displayed partly off-screen is moved to originate at 0,0.
     A window which would be too tall or wide is resized to screen size.
     @param frame JFrame
     */
    public void loadSettings(JFrame frame) throws IOException {
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
        if(!isPreference(name+".x")){
            // if the window has not been sized, then don't set its size
//            log.info("no preference saved for "+name+".x, not restoring position or size");
            return;
        }
        
        int x = preferences.getInt(name+".x",100);
        int y = preferences.getInt(name+".y",100);
        int w = preferences.getInt(name+".w",500);
        int h = preferences.getInt(name+".h",500);
        Dimension sd=Toolkit.getDefaultToolkit().getScreenSize();
        // determine the height of the windows taskbar by this roundabout proceedure
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        if(gs!=null && gs.length>0){
            GraphicsDevice gd = gs[0];
            GraphicsConfiguration[] gc = gd.getConfigurations();
            if(gc!=null && gc.length>0){
                Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(gc[0]);
                lowerInset=insets.bottom;
            }
        }
        if(x+w>sd.width || y+h>sd.height) {
            log.info("window extends over edge of screen, moving back to origin");
            x=y=0;
        }
        if(h>sd.height-lowerInset) {
            log.info("window height ("+h+") is bigger than screen height minus WINDOWS_TASK_BAR_HEIGHT ("+(sd.height-WINDOWS_TASK_BAR_HEIGHT)+"), resizing height");
            h=sd.height-lowerInset;
            resize=true;
        }
        if(w>sd.width) {
            log.info("window width ("+w+") is bigger than screen width ("+(sd.width)+"), resizing height");
            w=sd.width;
            resize=true;
        }
        
        frame.setLocation(x,y);
        if(resize){
            frame.setSize(new Dimension(w,h));
        }
//        log.info("restored window location");
        framemap.put(name,frame);
        frame.validate();
    }
    
    // returns true if there is a stored preference
    private boolean isPreference(String name){
        return !(preferences.get(name,null)==null);
    }
    
//    public int getInt(Properties props, String name, int value) {
//        String v = props.getProperty(name);
//        if(v == null) {
//            return value;
//        }
//        return Integer.parseInt(v);
//    }
    
    /** Used to explicity save settings.  Saves the x,y and width, height settings of window in preferences.
     */
    public void saveSettings() throws IOException {
        StringBuilder sb=new StringBuilder();
        sb.append("saved "+preferences+" for \n");
        Iterator it = framemap.keySet().iterator();
        while(it.hasNext()) {
            String name = (String)it.next();
            JFrame frame = (JFrame)framemap.get(name);
            preferences.putInt(name+".x",frame.getX()); sb.append(name+".x="+frame.getX()+" ");
            preferences.putInt(name+".y",frame.getY());sb.append(name+".y="+frame.getY()+" ");
            preferences.putInt(name+".w",frame.getWidth());sb.append(name+".w="+frame.getWidth()+" ");
            preferences.putInt(name+".h",frame.getHeight());sb.append(name+".h="+frame.getHeight()+" ");
            sb.append(" window "+name+"\n");
            
        }
        log.info(sb.toString());
    }
    
    /** This static method can be used to restore the window x,y, position (but not size) of a window based on the Window class
     name. This is a separate mechanism than the instance methods saveSettings and loadSettings.
     @param window the window to restore
     @param prefs user preferences node
     @see #saveWindowLocation
     */
    public static void restoreWindowLocation(Window window, Preferences prefs){
        Dimension scr=Toolkit.getDefaultToolkit().getScreenSize();
        String name=window.getClass().getName();
        int x=prefs.getInt(name+".XPosition",0);
        x=(int)Math.min(scr.getWidth()-window.getWidth()-20,x);
        int y=prefs.getInt(name+".YPosition",0);
        y=(int)Math.min(scr.getHeight()-window.getHeight()-20,y);
        window.setLocation(x,y);
    }
    
    /** This static method can be used to save the window x,y, position (but not size).
     This statis method saves the window
     origin but not the size, based on a classname-based key in the supplied preferences node.
     @param window the window to save for
     @param prefs user preferences node
     @see #restoreWindowLocation
     */
    public static void saveWindowLocation(Window window, Preferences prefs){
        String name=window.getClass().getName();
        Point p=new Point(0,0);
        try{ p=window.getLocationOnScreen(); }catch(IllegalComponentStateException e){ p=window.getLocation();};
        prefs.putInt(name+".XPosition",(int)p.getX());
        prefs.putInt(name+".YPosition",(int)p.getY());
//        log.info("WindowSaver.saveWindowLocation saved location for window "+name);
    }
    
}
