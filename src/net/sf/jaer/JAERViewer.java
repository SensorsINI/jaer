/*
 * JAERViewer.java
 *
 * Created on January 30, 2006, 10:41 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.SplashScreen;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.util.LoggingThreadGroup;
import net.sf.jaer.util.WindowSaver;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.JoglVersion;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;

/**
 * Used to show multiple chips simultaneously in separate instances of
 * {@link net.sf.jaer.graphics.AEViewer}, each running in its own thread, and
 * each with its own hardware interface thread or
 * {@link net.sf.jaer.eventio.AEInputStream}. A single viewer is launched with a
 * default {@link net.sf.jaer.chip.AEChip}. New viewers can be constructed from
 * the File menu.
 *
 * @author tobi
 */
public class JAERViewer {

    public JAERViewer() {
        this(false); // set arg to true to enable experiment global view mode of JAERViewer (Peter O'Connor mode)
    }

    /**
     * Root preferences object for jAER
     *
     */
    protected static Preferences prefs = JaerConstants.PREFS_ROOT;
    /**
     * Root Logger
     *
     */
    protected static Logger log;
    private ArrayList<AEViewer> viewers = new ArrayList<AEViewer>();
    private boolean syncEnabled = prefs.getBoolean("JAERViewer.syncEnabled", false); // default false so that all viewers are independent
    ArrayList<AbstractButton> syncEnableButtons = new ArrayList<AbstractButton>(); // list of all viewer sync enable buttons, used here to change boolean state because this is not property of Action that buttons understand
    private ToggleSyncEnabledAction toggleSyncEnabledAction = new ToggleSyncEnabledAction();
    
    public ToggleSyncEnabledAction getToggleSyncEnabledAction() {
        return toggleSyncEnabledAction;
    }
    /**
     * This public flag marks that data logging (recording) is enabled. It is
     * normally set by startLogging/stopLogging, but special applications can
     * set it
     */
    public volatile boolean loggingEnabled = false;
    //private boolean electricalTimestampResetEnabled=prefs.getBoolean("JAERViewer.electricalTimestampResetEnabled",false);
//    private String aeChipClassName=prefs.get("JAERViewer.aeChipClassName",Tmpdiff128.class.getName());
    private WindowSaver windowSaver; // TODO: encapsulate
    private boolean playBack = false;
    //some time variables for timing across threads
    static public long globalTime1, globalTime2, globalTime3;
    private SyncPlayer syncPlayer = null; // add a sync player once we have a viewer to assign it to
    protected static final String JAERVIEWER_VIEWER_CHIP_CLASS_NAMES_KEY = "JAERViewer.viewerChipClassNames";

    // Internal switch: go into multiple-display mode right away?
    boolean multistartmode = false;

    /**
     * This shared GLAutoDrawable is constructed here and is used by all
     * ChipCanvas to set the shared JOGL context.
     */
    public static GLAutoDrawable sharedDrawable; // TODO tobi experimental to deal with graphics creation woes.
    // see also http://forum.jogamp.org/Multiple-GLCanvas-FPSAnimator-Hang-td4030581.html

    /**
     * Creates a new instance of JAERViewer
     *
     * @param multimode set to true to enable global viewer so that all sources
     * are aggregated to one window.
     * @see net.sf.jaer.graphics.GlobalViewer
     */
    public JAERViewer(boolean multimode) {

        multistartmode = multimode;

        // GLProfile and GLCapabilities should be equal across all shared GL drawable/context.
        // tobi implemented this from user guide for JOGL that suggests a shared drawable context for all uses of JOGL
//        GLProfile.initSingleton(); // recommneded by https://sites.google.com/site/justinscsstuff/jogl-tutorial-2 especially for linux systems
//        final GLCapabilities caps = new GLCapabilities(GLProfile.getDefault());
//        final GLProfile glp = GLProfile.getMaximum(true);// FixedFunc(true);
        ////        final GLProfile glp = caps.getGLProfile();
//        final boolean createNewDevice = true; // use 'own' display device!
//        sharedDrawable = GLDrawableFactory.getFactory(glp).createDummyAutoDrawable(null, createNewDevice, caps, null);
//        sharedDrawable.display(); // triggers GLContext object creation and native realization. sharedDrawable is a static variable that can be used by all AEViewers and file preview dialogs
        log.info("JOGL version information: " + JoglVersion.getInstance().toString());

        windowSaver = new WindowSaver(this, prefs);
        // WindowSaver calls for determining screen insets (e.g. Windows Taskbar) could cause problems on different OS's
        Toolkit.getDefaultToolkit().addAWTEventListener(windowSaver, AWTEvent.WINDOW_EVENT_MASK); // adds windowSaver as JVM-wide event handler for window events

        SwingUtilities.invokeLater(new RunningThread());

        try {
            // Create temp file.
            File temp = new File("JAERViewerRunning.txt");

            // Delete temp file when program exits.
            temp.deleteOnExit();

            // Write to temp file
            BufferedWriter out = new BufferedWriter(new FileWriter(temp));
            out.write("JAERViewer started " + new Date());
            out.close();
        } catch (IOException e) {
            log.warning(e.getMessage());
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("JAERViewer shutdown hook - start of shutdown");
                System.out.flush();
                if ((viewers != null) && !viewers.isEmpty()) {
                    System.out.println("JAERViewer shutdown hook - shutting down AEViewers");
                    System.out.flush();
                    try {

                        ArrayList<String> viewerChipClassNames = new ArrayList<String>();
                        for (AEViewer v : viewers) {
                            viewerChipClassNames.add(v.getChip().getClass().getName());
                        }
                        // Serialize to a byte array
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutput out = new ObjectOutputStream(bos);
                        out.writeObject(viewerChipClassNames);
                        out.close();

                        // Get the bytes of the serialized object
                        byte[] buf = bos.toByteArray();
                        prefs.putByteArray(JAERVIEWER_VIEWER_CHIP_CLASS_NAMES_KEY, buf);
                        prefs.flush();
                    } catch (IOException e) {
                        System.err.println(String.format("could not store class names: %s", e.toString()));
                    } catch (IllegalArgumentException e2) {
                        System.err.println("tried to store too many classes in last chip classes? " + e2.toString());
                    } catch (BackingStoreException ex) {
                        System.err.println("could not flush the preferences holding AEChip class names: " + ex.toString());
                    }
                    System.out.println("JAERViewer shutdown hook - saving possible open data logging");
                    try {
                        for (AEViewer v : viewers) {
                            if (v.getLoggingFile() != null) {
                                v.stopLogging(true);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println(String.format("stopping logging, caught Exception %s", e.toString()));
                    }
                }

                System.out.println("JAERViewer shutdown hook - saving window settings");
                if (windowSaver != null) {
                    try {
                        windowSaver.saveSettings();
                    } catch (Exception e) {
                        System.err.println(String.format("could not save window settings: %s", e.toString()));
                    }
                }
                System.out.println("JAERViewer shutdown hook - end of shutdown");
                System.out.flush();

            }
        });
    }

    class RunningThread implements Runnable {

        @Override
        public void run() {

            // try to load a list of previous chip classes that running in viewers and then reOGloopen them
            ArrayList<String> classNames = null;
            try {
                byte[] bytes = prefs.getByteArray(JAERVIEWER_VIEWER_CHIP_CLASS_NAMES_KEY, null);
                if (bytes != null) {
                    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                    classNames = (ArrayList<String>) in.readObject();
                    in.close();
                }
            } catch (Exception e) {
                log.info("couldn't load previous viewer AEChip classes, starting with last class");
            }

            try {
                if (classNames == null) {
                    AEViewer v = new AEViewer(JAERViewer.this); // this call already adds the viwer to our list of viewers
//                player=new SyncPlayer(v); // associate with the initial viewer
//                v.pack();
                    v.setVisible(true);
                    //                splashThread.interrupt();
                } else {
                    for (String s : classNames) {
                        // check to make sure cla
                        AEViewer v;
                        v = new AEViewer(JAERViewer.this, s);
                        v.setVisible(true);
                    }
                }
            } catch (java.lang.UnsatisfiedLinkError err) {

                log.info("Unsatisfied link error.  Chances are that you are not running the right project configuration.  Set the project configuration to the appropiate platform (win,win64,linux32,linux64,etc...). The jAER project must be set to use a JVM that matches the project runtime configuration, e.g., if you are using a 32 bit JVM to run jAER (as selected in the project properties/Libraries/Java Platform), then you must choose the \"win\" configuration so that java.libray.path is set so that your DLLs come from host/java/jars/win32.");

                err.printStackTrace();
            }

        }

    }

    private static class SplashHandler extends java.util.logging.Handler {

        SplashScreen splashScreen;
        Graphics2D g;
        Logger logger = null;
        int cursor = 0;

        public SplashHandler(java.awt.SplashScreen splashScreen) {
            if (splashScreen == null) {
                log.warning("null splash screen passed in");
                return;
            }
            this.splashScreen = splashScreen;
            this.g = splashScreen.createGraphics();
            logger = Logger.getLogger("net.sf.jaer");
            logger.addHandler(this);
        }

        @Override
        public synchronized void publish(LogRecord record) {
            if ((splashScreen == null) || !splashScreen.isVisible()) {
                // DO NOT call log.something here, leads to stack overflow
                System.out.println("JAERViewer.SplashHandler.publish(): splash screen is null or no longer visible, closing logging to it");
                close();
                return;
            }
            String s = record.getMessage();
            if (s == null) {
                return;
            }
            Dimension d = splashScreen.getSize();
            int x = 45, starty = 30, textheight = 20, ystep = 15;
            g.setComposite(AlphaComposite.Clear);
            g.setColor(Color.white);
            g.fillRect(x - (textheight / 2), (starty - 10) + cursor, (int) d.getWidth(), textheight);
            g.setPaintMode();
            g.setColor(Color.blue);
            g.drawString(s, x, starty + cursor);
            cursor += ystep;
            if ((starty + cursor) > (d.height - textheight)) {
                cursor = 0;
            }
            try {
                splashScreen.update();
            } catch (IllegalStateException e) {
                System.err.println(e.toString());
            }

        }

        @Override
        public void close() throws SecurityException {
            if (logger == null) {
                return;
            }
            try {
                logger.removeHandler(this);
                splashScreen = null;
                g = null;
                logger = null;
            } catch (Exception e) {
                log.warning(e.toString());
            }
        }

        @Override
        public void flush() {
        }
    }

    public void addViewer(AEViewer viewer) {
        if (syncPlayer == null) {
            syncPlayer = new SyncPlayer(viewer, this);
            log.info("added " + syncPlayer + " to first viewer " + this);
        }
        viewer.setViewerInstanceIndex(allocateViewerIndex());
        getViewers().add(viewer);
        viewer.addWindowListener(new java.awt.event.WindowAdapter() {

            @Override
            public void windowClosing(java.awt.event.WindowEvent evt) {
                if (evt.getSource() instanceof AEViewer) {
                    log.info("removing " + evt.getSource() + " from list of AEViewers");
                    removeViewer((AEViewer) evt.getSource());
                }
            }
        });
        buildMenus(viewer);
        refreshViewerTitles();
    }

    /** Lowest unused {@code AEViewer-N} index so WindowSaver can restore each viewer separately. */
    private int allocateViewerIndex() {
        int i = 0;
        while (true) {
            boolean used = false;
            for (AEViewer v : viewers) {
                if (v.getViewerInstanceIndex() == i) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return i;
            }
            i++;
        }
    }

    private void refreshViewerTitles() {
        for (AEViewer v : viewers) {
            v.setTitleAccordingToState(true);
        }
    }

    void buildMenus(AEViewer v) {
        JMenu m = v.getFileMenu();

        ToggleLoggingAction action = new ToggleLoggingAction(v);
        v.getLoggingButton().setAction(action);
        v.getLoggingMenuItem().setAction(action);

        // adds to each AEViewers syncenabled check box menu item the toggleSyncEnabledAction
        AbstractButton b = v.getSyncEnabledCheckBoxMenuItem();
        b.setAction(getToggleSyncEnabledAction());
        syncEnableButtons.add(b);   // we need this stupid list because java 1.5 doesn't have Action property to support togglebuttons selected state (1.6 adds it)
        b.setSelected(isSyncEnabled());

        AbstractButton bbb = v.getPlayerControls().getSyncPlaybackCheckBox(); // TODO dependency, depends on existing player control panel
        syncEnableButtons.add(bbb);
        bbb.setSelected(isSyncEnabled());
        bbb.setAction(getToggleSyncEnabledAction());

        boolean en = true; //viewers.size()>1? true:false;
        for (AbstractButton bb : syncEnableButtons) {
            bb.setEnabled(en);
        }

        syncPlayer.getSupport().addPropertyChangeListener(v.getPlayerControls()); // TODO not very clean way of adding property change support....

//        if(en==false) syncEnableButtons.get(0).setSelected(false); // disable sync if there is only one viewer
    }

    public void removeViewer(AEViewer v) {
        if (getViewers().remove(v) == false) {
            log.warning("JAERViewer.removeViewer(): " + v + " is not in viewers list");
        } else {
            syncEnableButtons.remove(v.getSyncEnabledCheckBoxMenuItem());
        }
        boolean en = true; //viewers.size()>1? true:false;
        for (AbstractButton bb : syncEnableButtons) {
            bb.setEnabled(en);
        }
        refreshViewerTitles();
    }

    /**
     * @return collection of viewers we manage
     */
    public ArrayList<AEViewer> getViewers() {
        return viewers;
    }

    public int getNumViewers() {
        return viewers.size();
    }
    File indexFile = null;
    final String indexFileNameHeader = "JAERViewer-";
    final String indexFileSuffix = AEDataFile.INDEX_FILE_EXTENSION;
    DateFormat loggingFilenameDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");

    private String getDateString() {
        String dateString = loggingFilenameDateFormat.format(new Date());
        return dateString;
    }

    /**
     * Creates the index file at location path with timestamped name
     */
    private File createIndexFile(String path) {
        String indexFileName = indexFileNameHeader + getDateString() + indexFileSuffix;
        log.info("createIndexFile " + path + File.separator + indexFileName);
        indexFile = new File(path + File.separator + indexFileName);
        if (indexFile.isFile()) {
            throw new RuntimeException("index file " + indexFile + " already exists");
        }
        return indexFile;
    }

    public void startSynchronizedLogging() {
        log.info("starting synchronized logging");

        if (viewers.size() > 1) {// && !isElectricalSyncEnabled()){
//            zeroTimestamps();  // TODO this is commented out because there is still a bug of getting old timestamps at start of recording, causing problems when synchronized playback is enabled.
        } else {
            // log.info("not zeroing all board timestamps because they are specified electrically synchronized");
        }
        for (AEViewer v : viewers) {
            v.setPaused(true);

        }

        for (AEViewer v : viewers) {
            File f = v.startLogging();

        }
        for (AEViewer v : viewers) {
            v.setPaused(false);

        }

        loggingEnabled = true;
    }

    public void stopSynchronizedLogging() {
        log.info("stopping synchronized logging");
        FileWriter writer = null;
        boolean writingIndex = false;
        // pause all viewers
        viewers.get(0).aePlayer.pause();

        try {
            for (AEViewer v : viewers) {
                File f = v.stopLogging(getNumViewers() == 1); // only confirm filename if there is only a single viewer
                if (f == null) {
                    log.warning("something is wrong; the logging file is null when you tried to stop logging data. Ignoring this AEViewer instance. \nYou may be trying to do synchronized logging when using only a single AEViewer. \n Disable this functionality from the menu File/Synchronize AEViewer logging/playback");
                    continue;
                }
                log.info("Stopped logging to file " + f);
                if (f.exists()) { // if not cancelled
                    if (getNumViewers() > 1) {

                        if (writer == null) {
                            writingIndex = true;
                            createIndexFile(f.getParent());
                            writer = new FileWriter(indexFile);
                        }
                        writer.write(f.getName() + "\n");//  .getPath()+"\n");
                    }
                }
            }
            if ((viewers.size() > 1) && writingIndex) {
                writer.close();
            }
            if (indexFile != null) {
                for (AEViewer v : viewers) {
                    v.getRecentFiles().addFile(indexFile);
                }
                log.info("Saved index file " + indexFile.getCanonicalPath());
//                JOptionPane.showMessageDialog(null,"Saved index file " + indexFile.getCanonicalPath());
            }
        } catch (IOException e) {
            log.warning("creating index file " + indexFile);
            e.printStackTrace();
        }
        // resume all viewers
        viewers.get(0).aePlayer.resume();

        loggingEnabled = false;
    }

    public void toggleSynchronizedLogging() {
        //TODO - unchecking synchronized logging in AEViewer still comes here and logs sychrnoized
        loggingEnabled = !loggingEnabled;
        if (loggingEnabled) {
            startSynchronizedLogging();
        } else {
            stopSynchronizedLogging();
        }
    }

    public void zeroTimestamps() {
//        if(!isElectricalSyncEnabled()){
        log.info("JAERViewer.zeroTimestamps(): zeroing timestamps on all AEViewers");
        for (AEViewer v : viewers) {
            v.zeroTimestamps();

        }
//        }else{
//            log.warning("JAERViewer.zeroTimestamps(): electricalSyncEnabled, not resetting all viewer device timestamps");
//        }
    }
//    public class ViewerAction extends AbstractAction{
//        AEViewer viewer;
//        public ViewerAction(AEViewer viewer){
//            this.viewer=viewer;
//        }
//        public void actionPerformed(ActionEvent e){
//            throw new UnsupportedOperationException("this Action doesn't do anything, use subclass");
//        }
//    }
    File logIndexFile;

    /**
     * this action toggles logging, possibily for all viewers depending on
     * switch
     */
    public class ToggleLoggingAction extends AbstractAction {

        AEViewer viewer; // to find source of logging action

        public ToggleLoggingAction(AEViewer viewer) {
            this.viewer = viewer;
            putValue(NAME, "Start logging");
            putValue(SHORT_DESCRIPTION, "Controls synchronized logging on all viewers");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_L, 0));
            putValue(MNEMONIC_KEY, KeyEvent.VK_L);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
//            log.info("JAERViewer.ToggleLoggingAction.actionPerformed");
            if (isSyncEnabled()) {
                toggleSynchronizedLogging();
                if (loggingEnabled) {
                    putValue(NAME, "Stop logging");
                } else if (viewers.get(0).getPlayMode() == AEViewer.PlayMode.PLAYBACK) {
                    putValue(NAME, "Start Re-logging");
                } else {
                    putValue(NAME, "Start logging");
                }
                log.info("loggingEnabled=" + loggingEnabled);
            } else {
                viewer.toggleLogging();
            }
        }
    }

    /**
     * Toggles player synchronization over all viewers.
     *
     */
    public class ToggleSyncEnabledAction extends AbstractAction {

        public ToggleSyncEnabledAction() {
            String name = "Synchronize AEViewer logging/playback";
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, "<html>When enabled, multiple viewer logging and playback are synchronized. <br>Does not affect timestamp synchronization except to send timestamp reset to all viewers."
                    + "<br>Device electrical synchronization is independent of this setting.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            log.info("JAERViewer.ToggleSyncEnabledAction.actionPerformed");
            setSyncEnabled(!isSyncEnabled());
        }
    }
    
    /** Returns true if any AEViewer (or its HardwareConfiguration or FilterSetting) windows are active, i.e. has focus
     * 
     * @return true if some jAER window has focus
     */
    public boolean isAnyWindowActive(){
        for(AEViewer v:viewers){
            if(v.isAnyWindowActive()){
                return true;
            }
        }
        return false;
    }

    /**
     * Controls whether multiple viewers are synchronized for logging and
     * playback.
     *
     * @return true if sychronized.
     */
    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    /**
     * Controls whether multiple viewers are synchronized for logging and
     * playback.
     *
     * @param syncEnabled true to be synchronized.
     */
    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
        prefs.putBoolean("JAERViewer.syncEnabled", syncEnabled);
        for (AbstractButton b : syncEnableButtons) {
            b.setSelected(syncEnabled);
        }
        for (AEViewer v : viewers) {
            AbstractAEPlayer p = syncEnabled ? syncPlayer : v.aePlayer;
            if (v.getPlayerControls() != null) {
                v.getPlayerControls().setAePlayer(p);
            }
        }
    }

    public void pause() {
        log.info("this pause shouldn't normally be called");
    }

    public SyncPlayer getSyncPlayer() {
        return syncPlayer;
    }

    /**
     * @return true if boards are electrically connected and this connection
     * synchronizes the local timestamp value
     */
    /*public boolean isElectricalSyncEnabled(){
    return electricalTimestampResetEnabled;
    }*/
 /* public void setElectricalSyncEnabled(boolean b) {
    electricalTimestampResetEnabled=b;
    prefs.putBoolean("JAERViewer.electricalTimestampResetEnabled",electricalTimestampResetEnabled);
    for(AEViewer v:viewers){
    v.getElectricalSyncEnabledCheckBoxMenuItem().setSelected(b);
    }
    }*/
    public boolean isPlayBack() {
        return playBack;
    }

    public void setPlayBack(boolean playBack) {
        this.playBack = playBack;
    }

    /**
     * The main launcher for AEViewer's.
     *
     * @param args optional recorded AE data filename, and/or {@code -Dname=value}
     * flags (applied via {@link System#setProperty} when a shell passed them as
     * app args instead of JVM args — common with unquoted {@code -D} in PowerShell)
     */
    public static void main(String[] args) {
        final String[] fileArgs = applyLauncherArgsAsSystemProperties(args);

        Thread.UncaughtExceptionHandler handler = new LoggingThreadGroup("jAER UncaughtExceptionHandler");
        Thread.setDefaultUncaughtExceptionHandler(handler);

        //init static fields
        log = Logger.getLogger("net.sf.jaer");

        final java.awt.SplashScreen splash = java.awt.SplashScreen.getSplashScreen();
        if (splash != null) {
            new SplashHandler(splash);
        } else {
            log.warning("no Java 6 splash screen to animate (don't worry; this happens if you run from development environment)");
        }

        log.info("jAERViewer starting up");
        net.sf.jaer.util.TensorFlowNativeSupport.installDownloadedJarsOnClasspath();
        log.info("java.version=" + System.getProperty("java.version") + "  java.vm.version=" + System.getProperty("java.vm.version") + " user.dir=" + System.getProperty("user.dir"));
        net.sf.jaer.util.MemoryDiagnostics.maybeStartPeriodicLogging(log);
        log.info("Java logging is configured by the command line option -Djava.util.logging.config.file=<filename>."
                + " \nThe current value of java.util.logging.config.file is " + System.getProperty("java.util.logging.config.file")
                + "\nEdit this file to configure logging." + "\nThe value of java.io.tmpdir is " + System.getProperty("java.io.tmpdir"));
        log.info("Preferences come from root located at " + prefs.absolutePath());
        Logger root = log;
        while (root.getParent() != null) {
            root = root.getParent(); // find root logger
        }
        log.info("logging configuration read from java.util.logging.config.file=" + System.getProperty("java.util.logging.config.file"));
        for (Handler h : root.getHandlers()) {
            log.info(String.format("Handler %s logging with Level=%s", h, h.getLevel()));
//            if (h instanceof ConsoleHandler) {
//                log.info("debug logging to console with Level=" + ((ConsoleHandler) h).getLevel());
//            } else if (h instanceof FileHandler) {
//                log.info("debug logging to file with Level=" + ((FileHandler) h).getLevel() + " to file (see config file for location)");
//            } else {
//                log.info("debug logging to handler that is not ConsoleHandler or FileHandler using " + h);
//            }
        }

        if (fileArgs.length > 0) {
            final File f = new File(fileArgs[0]);
            if (!f.isFile()) {
                log.warning("Ignoring non-file launch argument \"" + fileArgs[0]
                        + "\" (from PowerShell quote -D flags, use --%, or set JAER_JVM_ARGS)");
                SwingUtilities.invokeLater(() -> new JAERViewer());
                return;
            }
            log.info("starting with file=" + f.getAbsolutePath() + " in working directory=" + System.getProperty("user.dir"));
            try {
                // Windows file association / double-click passes the path as argv[0].
                // AEViewer registers itself in JAERViewer before setAeChipClass finishes,
                // so waiting only for getNumViewers()>0 races and startPlayback hits a null chip.
                final JAERViewer jv = new JAERViewer();
                final AEViewer ready = waitForReadyViewer(jv, 60_000L);
                if (ready == null || jv.getSyncPlayer() == null) {
                    throw new IllegalStateException(
                            "Timed out waiting for AEViewer chip to initialize before opening "
                            + f.getAbsolutePath());
                }
                // File open shows Swing dialogs and may switch AEChip; run on EDT.
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        jv.getSyncPlayer().startPlayback(f);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                Throwable shown = e;
                while ((shown instanceof java.lang.reflect.InvocationTargetException
                        || shown instanceof RuntimeException)
                        && shown.getCause() != null && shown.getCause() != shown) {
                    shown = shown.getCause();
                }
                log.log(Level.SEVERE, "Failed to open launch file " + f, shown);
                JOptionPane.showMessageDialog(null, "<html>Trying to start JAERViewer with <br>file=\"" + f + "\"<br>Caught " + shown);
            }
        } else {
            log.info("starting with no file arguments in working directory=" + System.getProperty("user.dir"));
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    new JAERViewer();
                }
            });
        }

    }

    /**
     * Wait until at least one {@link AEViewer} has finished constructing its
     * {@link net.sf.jaer.chip.AEChip}. Used when opening a file from argv
     * (Windows "Open with" / file association).
     *
     * @param jv top-level viewer manager
     * @param timeoutMs max wait; return null on timeout
     */
    static AEViewer waitForReadyViewer(JAERViewer jv, long timeoutMs) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() <= deadline) {
            if (jv.getNumViewers() > 0) {
                final AEViewer v = jv.getViewers().get(0);
                if (v != null && v.getChip() != null && jv.getSyncPlayer() != null) {
                    return v;
                }
            }
            Thread.sleep(50);
        }
        return null;
    }

    /**
     * Apply {@code -Dname=value} from the app argument list via
     * {@link System#setProperty}; return remaining file-path args. Also repairs
     * PowerShell mangling of {@code -Djaer.live.bench=true} into
     * {@code .live.bench} + {@code true}.
     */
    static String[] applyLauncherArgsAsSystemProperties(String[] args) {
        if (args == null || args.length == 0) {
            return new String[0];
        }
        final ArrayList<String> files = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; i++) {
            final String a = args[i];
            if (a == null || a.isEmpty()) {
                continue;
            }
            if (a.startsWith("-D") && a.length() > 2) {
                setPropertyFromDashD(a.substring(2));
                continue;
            }
            // PowerShell: -Djaer.live.bench=true → ".live.bench", "true"
            if (a.startsWith(".") && i + 1 < args.length && isLooseTrueFalse(args[i + 1])) {
                final String key = "jaer" + a;
                if (key.startsWith("jaer.")) {
                    System.setProperty(key, args[i + 1]);
                    i++;
                    continue;
                }
            }
            if (a.startsWith("jaer.") && a.indexOf('=') > 0) {
                setPropertyFromDashD(a);
                continue;
            }
            if (a.startsWith("-")) {
                continue; // unknown option, not a data file
            }
            files.add(a);
        }
        return files.toArray(new String[0]);
    }

    private static void setPropertyFromDashD(String nameEqualsValue) {
        final int eq = nameEqualsValue.indexOf('=');
        if (eq <= 0) {
            System.setProperty(nameEqualsValue, "");
            return;
        }
        System.setProperty(nameEqualsValue.substring(0, eq), nameEqualsValue.substring(eq + 1));
    }

    private static boolean isLooseTrueFalse(String s) {
        return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
    }

}
