/*
 * AEViewer.java
 *
 * This is the "main" jAER interface to the user. The main event loop "ViewLoop" is here; see ViewLoop.run()
 *
 * Created on December 24, 2005, 1:58 PM
 */
package net.sf.jaer.graphics;
import java.net.SocketException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2EEPROM;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2MonitorSequencer;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2FirmwareFilennameChooserOkCancelDialog;
import net.sf.jaer.*;
import net.sf.jaer.aemonitor.*;
import net.sf.jaer.aesequencer.*;
import net.sf.jaer.biasgen.*;
import net.sf.jaer.chip.*;
import ch.unizh.ini.jaer.chip.retina.*;
import net.sf.jaer.event.*;
import net.sf.jaer.eventio.*;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.FilterFrame;
import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.hardwareinterface.usb.*;
import net.sf.jaer.util.*;
import net.sf.jaer.util.ExceptionListener;
import net.sf.jaer.util.browser.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.*;
import java.beans.*;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;
import java.util.prefs.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import net.sf.jaer.stereopsis.StereoHardwareInterface;
import spread.*;
/**
 * This is the main jAER interface to the user. The main event loop "ViewLoop" is here; see ViewLoop.run(). AEViewer shows AE chip live view and allows for controlling view and recording and playing back events from files and network connections.
<p>
AEViewer supports PropertyChangeListener's and fires PropertyChangeEvents on the following events:
<ul>
<li> "playmode" - when the player mode changes, e.g. from PlayMode.LIVE to PlayMode.PLAYBACK. The old and new values are the old and new PlayMode values
<li> "fileopen" - when a new file is opened; old=null, new=file.
<li> "stopme" - when stopme is called; old=new=null.
 * <li> "chip" - when a new AEChip is built for the viewer.
</ul>
In addition, when A5EViewer is in PLAYBACK PlayMode, users can register as PropertyChangeListeners on the AEFileInputStream for rewind events, etc.
 *
 * @author  tobi
 */
public class AEViewer extends javax.swing.JFrame implements PropertyChangeListener,DropTargetListener,ExceptionListener,RemoteControlled{
    public static String HELP_URL_USER_GUIDE = "http://jaer.wiki.sourceforge.net";
    public static String HELP_URL_RETINA = "http://siliconretina.ini.uzh.ch";
    public static String HELP_URL_JAVADOC_WEB = "http://jaer.sourceforge.net/javadoc";
    public static String HELP_URL_JAVADOC;

    static{
        String curDir = System.getProperty("user.dir");
        HELP_URL_JAVADOC = "file://" + curDir + "/dist/javadoc/index.html";
//        System.out.println("HELP_URL_JAVADOC="+HELP_URL_JAVADOC);
    }
    public static String HELP_URL_USER_GUIDE_USB2_MINI;
    public static String HELP_URL_USER_GUIDE_AER_CABLING;

    static{
        try{
            String curDir = System.getProperty("user.dir");
            File f = new File(curDir);
            File pf = f.getParentFile().getParentFile();
            HELP_URL_USER_GUIDE_USB2_MINI = "file://" + pf.getPath() + "/doc/USBAERmini2userguide.pdf";
            HELP_URL_USER_GUIDE_AER_CABLING = "file://" + pf.getPath() + "/doc/AER Hardware and cabling.pdf";
        } catch ( Exception e ){
            Logger.getAnonymousLogger().log(Level.WARNING,"While making help file URLs caught exception " + e + ", happens when starting on different filesystem than jAER installation");
        }
    }
    /** Default port number for remote control of this AEViewer.
     *
     */
    public final int REMOTE_CONTROL_PORT = 8997; // TODO make this the starting port number but find a free one if not available.

    /** Returns the frame for configurating chip. Could be null until user chooses to build it.
     *
     * @return the frame.
     */
    public BiasgenFrame getBiasgenFrame (){
        return biasgenFrame;
    }

    /** Returns the frame holding the event filters. Could be null until user builds it.
     *
     * @return the frame.
     */
    public FilterFrame getFilterFrame (){
        return filterFrame;
    }

    public void reopenSocketInputStream () throws HeadlessException{
        log.info("closing and reopening socket " + aeSocket);
        if ( aeSocket != null ){
            try{
                aeSocket.close();
            } catch ( Exception e ){
                log.warning("closing existing socket: caught " + e);
            }
        }
        try{
            aeSocket = new AESocket(); // uses preferred settings for port/buffer size, etc.
            aeSocket.connect();
            setPlayMode(PlayMode.REMOTE);
            openSocketInputStreamMenuItem.setText("Close socket input stream from " + aeSocket.getHost() + ":" + aeSocket.getPort());
            log.info("opened socket input stream " + aeSocket);
            socketInputEnabled = true;
        } catch ( Exception e ){
            JOptionPane.showMessageDialog(this,"Exception reopening socket: " + e,"AESocket Exception",JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Stores the preferred (startup) AEChip class for the viewer.
     * @param clazz the class.
     */
    public void setPreferredAEChipClass (Class clazz){
        prefs.put("AEViewer.aeChipClassName",clazz.getName());
    }
    public final String REMOTE_START_LOGGING = "startlogging";
    public final String REMOTE_STOP_LOGGING = "stoplogging";

    /** Processes remote control ocmmands for this AEViewer. A list of commands can be obtained
     * from a remote host by sending ? or help. The port number is logged to the console on startup.
     * @param command the parsed command (first token)
     * @param line the line sent from the remote host.
     * @return confirmation of command.
     */
    public String processRemoteControlCommand (RemoteControlCommand command,String line){
        String[] tokens = line.split("\\s");
        try{
            if ( command.getCmdName().equals(REMOTE_START_LOGGING) ){
                if ( tokens.length < 2 ){
                    return "not enough arguments\n";
                }
                String filename = line.substring(REMOTE_START_LOGGING.length() + 1);
                File f = startLogging(filename);
                if ( f == null ){
                    return "Couldn't start logging to filename=" + filename + ", startlogging returned " + f + "\n";
                } else{
                    return "starting logging to " + f + "\n";
                }
            } else if ( command.getCmdName().equals(REMOTE_STOP_LOGGING) ){
                stopLogging(false); // don't confirm filename
                return "stopped logging\n";
            }
        } catch ( Exception e ){
            return e.toString() + "\n";
        }
        return null;
    }

    /**
     * @return the playerControls
     */
    public AePlayerAdvancedControlsPanel getPlayerControls (){
        return playerControls;
    }

    /**
     * @param playerControls the playerControls to set
     */
    public void setPlayerControls (AePlayerAdvancedControlsPanel playerControls){
        this.playerControls = playerControls;
    }

    /**
     * @return the frameRater
     */
    public FrameRater getFrameRater (){
        return frameRater;
    }

    /**
     * @return the aeFileInputStreamTimestampResetBitmask
     */
    public int getAeFileInputStreamTimestampResetBitmask (){
        return aeFileInputStreamTimestampResetBitmask;
    }

    /**
     * @return the checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem
     */
    public javax.swing.JCheckBoxMenuItem getCheckNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem (){
        return checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem;
    }
    /** Modes of viewing: WAITING means waiting for device or for playback or remote, LIVE means showing a hardware interface, PLAYBACK means playing
     * back a recorded file, SEQUENCING means sequencing a file out on a sequencer device, REMOTE means playing a remote stream of AEs
     */
    public enum PlayMode{
        WAITING, LIVE, PLAYBACK, SEQUENCING, REMOTE
    }
    volatile private PlayMode playMode = PlayMode.WAITING;
    static Preferences prefs = Preferences.userNodeForPackage(AEViewer.class);
    Logger log = Logger.getLogger("AEViewer");
    private PropertyChangeSupport support = new PropertyChangeSupport(this);
    EventExtractor2D extractor = null;
    private BiasgenFrame biasgenFrame = null;
    Biasgen biasgen = null;
    EventFilter2D filter1 = null, filter2 = null;
    AEChipRenderer renderer = null;
    AEMonitorInterface aemon = null;
    private final ViewLoop viewLoop = new ViewLoop();
    FilterChain filterChain = null;
    private FilterFrame filterFrame = null;
    RecentFiles recentFiles = null;
    File lastFile = null;
    public File lastLoggingFolder = null;//changed pol
    File lastImageFile = null;
    File currentFile = null;
    private FrameRater frameRater = new FrameRater();
    ChipCanvas chipCanvas;
    volatile boolean loggingEnabled = false;
    /** The date formatter used by AEViewer for logged data files */
    private File loggingFile;
    AEFileOutputStream loggingOutputStream;
    private boolean activeRenderingEnabled = prefs.getBoolean("AEViewer.activeRenderingEnabled",false);
    private boolean openGLRenderingEnabled = prefs.getBoolean("AEViewer.openGLRenderingEnabled",true);
    private boolean renderBlankFramesEnabled = prefs.getBoolean("AEViewer.renderBlankFramesEnabled",false);
    // number of packets to skip over rendering, used to speed up real time processing
    private int skipPacketsRenderingNumber = prefs.getInt("AEViewer.skipPacketsRenderingNumber",0);
    int skipPacketsRenderingCount = 0; // render first packet always
    DropTarget dropTarget;
    File draggedFile;
    private boolean loggingPlaybackImmediatelyEnabled = prefs.getBoolean("AEViewer.loggingPlaybackImmediatelyEnabled",false);
    private boolean enableFiltersOnStartup = prefs.getBoolean("AEViewer.enableFiltersOnStartup",false);
    private long loggingTimeLimit = 0, loggingStartTime = System.currentTimeMillis();
    private boolean stereoModeEnabled = false;
    private boolean logFilteredEventsEnabled = prefs.getBoolean("AEViewer.logFilteredEventsEnabled",false);
    private DynamicFontSizeJLabel statisticsLabel;
    private boolean filterFrameBuilt = false; // flag to signal that the frame should be rebuilt when initially shown or when chip is changed
    private AEChip chip;
    /** The default AEChip class. */
    public static String DEFAULT_CHIP_CLASS = DVS128.class.getName();
    private String aeChipClassName = prefs.get("AEViewer.aeChipClassName",DEFAULT_CHIP_CLASS);
    Class aeChipClass;
//    WindowSaver windowSaver;
    private JAERViewer jaerViewer;
    // multicast connections
    private AEMulticastInput aeMulticastInput = null;
    private AEMulticastOutput aeMulticastOutput = null;
    private boolean multicastInputEnabled = false, multicastOutputEnabled = false;
    // unicast dataqgram data xfer
    private volatile AEUnicastOutput unicastOutput = null;
    private volatile AEUnicastInput unicastInput = null;
    private boolean unicastInputEnabled = false, unicastOutputEnabled = false;
    // socket connections
    private volatile AEServerSocket aeServerSocket = null; // this server socket accepts connections from clients who want events from us
    private volatile AESocket aeSocket = null; // this socket is used to get events from a server to us
    private boolean socketInputEnabled = false; // flags that we are using socket input stream
    // Spread connections
    private volatile AESpreadInterface spreadInterface = null;
    private boolean spreadOutputEnabled = false, spreadInputEnabled = false;
    private boolean blankDeviceMessageShown = false; // flags that we have warned about blank device, don't show message again
    AEViewerLoggingHandler loggingHandler;
    private RemoteControl remoteControl = null; // TODO move to JAERViewer
    private int aeFileInputStreamTimestampResetBitmask = prefs.getInt("AEViewer.aeFileInputStreamTimestampResetBitmask",0);
    private AePlayerAdvancedControlsPanel playerControls;

    /**
     * construct new instance and then set classname of device to show in it
     *
     * @param jaerViewer the manager of all viewers
     */
    public AEViewer (JAERViewer jaerViewer){
        setLocale(Locale.US); // to avoid problems with other language support in JOGL
//        try {
//            UIManager.setLookAndFeel(
//                    UIManager.getCrossPlatformLookAndFeelClassName());
////            UIManager.setLookAndFeel(new WindowsLookAndFeel());
//        } catch (Exception e) {
//            log.warning(e.getMessage());
//        }
         setName("AEViewer");

        initComponents();
        playerControls = new AePlayerAdvancedControlsPanel(this);
        playerControlPanel.add(playerControls,BorderLayout.NORTH);
        this.jaerViewer = jaerViewer;
        if ( jaerViewer != null ){
            // all stuff having to do with synchronizing player buttons here, binding components
            // TODO rework binding of jAERViewer player, AEViewer player, and player GUI. The whole MVC idea is too convoluted now.
            jaerViewer.addViewer(this); // register outselves, build menus here that sync views, e.g. synchronized playback // TODO, dependency, depends on existing player control panel
            if ( jaerViewer.getSyncPlayer() != null ){
                // now bind player control panel to SyncPlayer and bind jaer sync player to player control panel.
                playerControls.addPropertyChangeListener(jaerViewer.getSyncPlayer());
                jaerViewer.getSyncPlayer().getSupport().addPropertyChangeListener(playerControls);
                playerControls.setAePlayer(jaerViewer.getSyncPlayer());
            }
        }
        validate();
 
 
      loggingHandler = new AEViewerLoggingHandler(this); // handles log messages globally
      loggingHandler.getSupport().addPropertyChangeListener(this);
        Logger.getLogger("").addHandler(loggingHandler);

        log.info("AEViewer starting up...");

        statisticsLabel = new DynamicFontSizeJLabel();
        statisticsLabel.setToolTipText("Time slice/Absolute time, NumEvents/NumFiltered, events/sec, Frame rate acheived/desired, Time expansion X contraction /, delay after frame, color scale");
        statisticsPanel.add(statisticsLabel);

        HardwareInterfaceException.addExceptionListener(this);
        int n = menuBar.getMenuCount();
        for ( int i = 0 ; i < n ; i++ ){
            JMenu m = menuBar.getMenu(i);
            m.getPopupMenu().setLightWeightPopupEnabled(false);
        }
        filtersSubMenu.getPopupMenu().setLightWeightPopupEnabled(false); // otherwise can't see on canvas
        graphicsSubMenu.getPopupMenu().setLightWeightPopupEnabled(false);
        remoteMenu.getPopupMenu().setLightWeightPopupEnabled(false); // make remote submenu heavy to show over glcanvas

        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false); // to show menu tips over GLCanvas

        String lastFilePath = prefs.get("AEViewer.lastFile","");
        lastFile = new File(lastFilePath);

        String defaultLoggingFolderName = System.getProperty("user.dir");
        // lastLoggingFolder starts off at user.dir which is startup folder "host/java" where .exe launcher lives
        lastLoggingFolder = new File(prefs.get("AEViewer.lastLoggingFolder",defaultLoggingFolderName));

        // check lastLoggingFolder to see if it really exists, if not, default to user.dir
        if ( !lastLoggingFolder.exists() || !lastLoggingFolder.isDirectory() ){
            log.warning("lastLoggingFolder " + lastLoggingFolder + " no good, defaulting to " + defaultLoggingFolderName);
            lastLoggingFolder = new File(defaultLoggingFolderName);
        }

        // recent files tracks recently used files *and* folders. recentFiles adds the anonymous listener
        // built here to open the selected file
        recentFiles = new RecentFiles(prefs,fileMenu,new ActionListener(){
            public void actionPerformed (ActionEvent evt){
                File f = new File(evt.getActionCommand());
//                log.info("opening "+evt.getActionCommand());
                try{
                    if ( f != null && f.isFile() ){
                        getAePlayer().startPlayback(f);
                        recentFiles.addFile(f);
                    } else if ( f != null && f.isDirectory() ){
                        prefs.put("AEViewer.lastFile",f.getCanonicalPath());
                        aePlayer.openAEInputFileDialog();
                        recentFiles.addFile(f);
                    }
                } catch ( Exception fnf ){
                    fnf.printStackTrace();
//                    exceptionOccurred(fnf,this);
                    recentFiles.removeFile(f);
                }
            }
        });


        buildDeviceMenu();
        // we need to do this after building device menu so that proper menu item radio button can be selected
        cleanup(); // close sockets if they are open
        setAeChipClass(getAeChipClass()); // set default device - last chosen


        playerControlPanel.setVisible(false);
        timestampResetBitmaskMenuItem.setText("Set timestamp reset bitmask... (currently 0x" + Integer.toHexString(aeFileInputStreamTimestampResetBitmask) + ")");

//        pack(); // seems to make no difference

// tobi removed following oct 2008 because it was somehow apparently causing deadlock on exit, don't know why
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//
//            public void run() {
//                if (aemon != null && aemon.isOpen()) {
//                    aemon.close();
//                }
//            }
//        });

        setFocusable(true);
        requestFocus();
//        viewLoop = new ViewLoop(); // declared final for synchronization
        dropTarget = new DropTarget(imagePanel,this);

        fixLoggingControls();

        // init menu items that are checkboxes to correct initial state
        viewActiveRenderingEnabledMenuItem.setSelected(isActiveRenderingEnabled());
        viewOpenGLEnabledMenuItem.setSelected(isOpenGLRenderingEnabled());
        loggingPlaybackImmediatelyCheckBoxMenuItem.setSelected(isLoggingPlaybackImmediatelyEnabled());
        subsampleEnabledCheckBoxMenuItem.setSelected(chip.isSubSamplingEnabled());
        acccumulateImageEnabledCheckBoxMenuItem.setSelected(renderer.isAccumulateEnabled());
        autoscaleContrastEnabledCheckBoxMenuItem.setSelected(renderer.isAutoscaleEnabled());
        pauseRenderingCheckBoxMenuItem.setSelected(false);// not isPaused because aePlayer doesn't exist yet
        viewRenderBlankFramesCheckBoxMenuItem.setSelected(isRenderBlankFramesEnabled());
        logFilteredEventsCheckBoxMenuItem.setSelected(logFilteredEventsEnabled);
        enableFiltersOnStartupCheckBoxMenuItem.setSelected(enableFiltersOnStartup);

        fixSkipPacketsRenderingMenuItems();

        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.setSelected(prefs.getBoolean("AEViewer.checkNonMonotonicTimeExceptionsEnabled",true));

        // start the server thread for incoming socket connections for remote consumers of events
        if ( aeServerSocket == null ){
            try{
                aeServerSocket = new AEServerSocket();
                aeServerSocket.start();
            } catch ( IOException ex ){
                log.warning(ex.toString() + ": While constructing AEServerSocket. Another viewer or process has already bound this port or some other error. This viewer will not have a server socket for AE data.");
                aeServerSocket = null;
            }
        }
        viewLoop.start();

        // add remote control commands
        // TODO encapsulate all this and command processor
        try{
            remoteControl = new RemoteControl(REMOTE_CONTROL_PORT);
            remoteControl.addCommandListener(this,REMOTE_START_LOGGING + " <filename>","starts logging ae data to a file");
            remoteControl.addCommandListener(this,REMOTE_STOP_LOGGING,"stops logging ae data to a file");
            log.info("created " + remoteControl + " for remote control of some AEViewer functions");
        } catch ( SocketException ex ){
            log.warning(ex.toString());
        }
 
    }

    /** Closes hardware interface and network sockets.
     * Register all cleanup here for other classes, e.g. Chip classes that open
    sockets.
     */
    private void cleanup (){
        if ( aemon != null && aemon.isOpen() ){
            log.info("closing " + aemon);
            aemon.close();
        }

        if ( aeServerSocket != null ){
            log.info("closing " + aeServerSocket);
            try{
                aeServerSocket.close();
            } catch ( IOException e ){
                log.warning(e.toString());
            }
        }
        if ( unicastInput != null ){
            log.info("closing unicast input" + unicastInput);
            unicastInput.close();
        }
        if ( unicastOutput != null ){
            log.info("closing unicastOutput " + unicastOutput);
            unicastOutput.close();
        }
        if ( aeMulticastInput != null ){
            log.info("closing aeMulticastInput " + aeMulticastInput);
            aeMulticastInput.close();
        }
        if ( aeMulticastOutput != null ){
            log.info("closing multicastOutput " + aeMulticastOutput);
            aeMulticastOutput.close();
        }
    }

    private boolean isWindows (){
        String osName = System.getProperty("os.name");
        if ( osName.startsWith("Windows") ){
            return true;
        } else{
            return false;
        }
    }

    /** If we are are the only viewer, automatically set
    interface to the hardware interface if there is only 1 of them and there is not already
    a hardware inteface (e.g. StereoHardwareInterface which consists of
    two interfaces). otherwise force user choice.
     */
    private void openHardwareIfNonambiguous (){

        if ( jaerViewer != null && jaerViewer.getViewers().size() == 1 && chip.getHardwareInterface() == null && HardwareInterfaceFactory.instance().getNumInterfacesAvailable() == 1 ){
//            log.info("opening unambiguous device");
            chip.setHardwareInterface(HardwareInterfaceFactory.instance().getFirstAvailableInterface()); // if blank cypress, returns bare CypressFX2
        }
    }
    private ArrayList<String> chipClassNames;
    private ArrayList<Class> chipClasses;

    @SuppressWarnings ("unchecked")
    void getChipClassPrefs (){
        // Deserialize from a byte array
        try{
            byte[] bytes = prefs.getByteArray("chipClassNames",null);
            if ( bytes != null ){
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                chipClassNames = (ArrayList<String>)in.readObject();
                in.close();
            } else{
                makeDefaultChipClassNames();
            }
        } catch ( Exception e ){
            makeDefaultChipClassNames();
        }
    }

    private void makeDefaultChipClassNames (){
        chipClassNames = SubclassFinder.findSubclassesOf(AEChip.class.getName());
//        chipClassNames=new ArrayList<String>(AEChip.CHIP_CLASSSES.length);
//        for(int i=0;i<AEChip.CHIP_CLASSSES.length;i++){
//            chipClassNames.add(AEChip.CHIP_CLASSSES[i].getCanonicalName());
//        }
    }

    private void putChipClassPrefs (){
        try{
            // Serialize to a byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(chipClassNames);
            out.close();

            // Get the bytes of the serialized object
            byte[] buf = bos.toByteArray();
            prefs.putByteArray("chipClassNames",buf);
        } catch ( IOException e ){
            e.printStackTrace();
        } catch ( IllegalArgumentException e2 ){
            log.warning("too many classes in Preferences, " + chipClassNames.size() + " class names");
        }
    }
    private static class FastClassFinder{
        static HashMap<String,Class> map = new HashMap<String,Class>();

        private static Class forName (String name) throws ClassNotFoundException{
            Class c = null;
            if ( ( c = map.get(name) ) == null ){
                c = Class.forName(name);
                map.put(name,c);
                return c;
            } else{
                return c;
            }
        }
    }

    private void buildDeviceMenu (){
        ButtonGroup deviceGroup = new ButtonGroup();
        deviceMenu.removeAll();
        chipClasses = new ArrayList<Class>();
        deviceMenu.addSeparator();
        deviceMenu.add(customizeDevicesMenuItem);
        getChipClassPrefs();
        ArrayList<String> notFoundClasses = new ArrayList<String>();
        for ( String deviceClassName:chipClassNames ){
            try{
                Class c = FastClassFinder.forName(deviceClassName);
                chipClasses.add(c);
                JRadioButtonMenuItem b = new JRadioButtonMenuItem(deviceClassName);
                deviceMenu.insert(b,deviceMenu.getItemCount() - 2);
                b.addActionListener(new ActionListener(){
                    public void actionPerformed (ActionEvent evt){
                        try{
                            String name = evt.getActionCommand();
                            Class cl = FastClassFinder.forName(name);
                            setAeChipClass(cl);
                        } catch ( Exception e ){
                            e.printStackTrace();
                        }
                    }
                });
                deviceGroup.add(b);
            } catch ( ClassNotFoundException e ){
                log.warning("couldn't find device class " + e.getMessage() + ", removing from preferred classes");
                if ( deviceClassName != null ){
                    notFoundClasses.add(deviceClassName);
                }
            }
        }
        if ( notFoundClasses.size() > 0 ){
            chipClassNames.removeAll(notFoundClasses);
            putChipClassPrefs();
        }
    }

    public void zeroTimestamps (){
        if ( aemon != null && aemon.isOpen() ){
            aemon.resetTimestamps();
        }
    }

    /** Gets the AEchip class from the internal aeChipClassName
     *
     * @return the AEChip subclass. DEFAULT_CHIP_CLASS is returned if there is no stored preference.
     */
    public Class getAeChipClass (){
        if ( aeChipClass == null ){
//            log.warning("AEViewer.getAeChipClass(): null aeChipClass, initializing to default "+aeChipClassName);
            try{
//                log.info("getting class for "+aeChipClassName);
                aeChipClass = FastClassFinder.forName(aeChipClassName); // throws exception if class not found
                if ( java.lang.reflect.Modifier.isAbstract(aeChipClass.getModifiers()) ){
                    log.warning(aeChipClass + " is abstract, setting chip class to default " + DEFAULT_CHIP_CLASS);
                    aeChipClassName = DEFAULT_CHIP_CLASS;
                    aeChipClass = aeChipClass = FastClassFinder.forName(aeChipClassName);
                }
            } catch ( Exception e ){
                log.warning(aeChipClassName + " class not found, setting preferred chip class to default " + DEFAULT_CHIP_CLASS);
                prefs.put("AEViewer.aeChipClassName",DEFAULT_CHIP_CLASS);
                System.exit(1);
            }
        }
        return aeChipClass;
    }
    private long lastTimeTitleSet = 0;
    PlayMode lastTitlePlayMode = null;

    /** this sets window title according to actual state */
    public void setTitleAccordingToState (){
        if ( lastTitlePlayMode == getPlayMode() && System.currentTimeMillis() - lastTimeTitleSet < 1000 ){
            return; // don't bother with this expenive window operation more than 1/second
        }
        lastTimeTitleSet = System.currentTimeMillis();
        lastTitlePlayMode = getPlayMode();
        String ts = null;
        switch ( getPlayMode() ){
            case LIVE:
                ts = "LIVE - " + getAeChipClass().getSimpleName() + " - " + aemon + " - AEViewer";
                break;
            case PLAYBACK:
                ts = "PLAYING - " + currentFile.getName() + " - " + getAeChipClass().getSimpleName() + " - AEViewer";
                break;
            case WAITING:
                ts = "WAITING - " + getAeChipClass().getSimpleName() + " - AEViewer";
                break;
            case SEQUENCING:
                ts = " LIVE SEQUENCE-MONITOR - " + getAeChipClass().getSimpleName() + " - " + aemon + " - AEViewer";
                break;
            case REMOTE:
                ts = "REMOTE - " + getAeChipClass().getSimpleName() + " - AEViewer";
                break;
            default:
                ts = "Unknown state";
        }
        final String fts = ts;
        SwingUtilities.invokeLater(new Runnable(){
            public void run (){
                setTitle(fts);
            }
        });
    }

    /** sets the device class, e.g. Tmpdiff128, from the
     * fully qual classname provided by the menu item itself.
     * @param deviceClass the Class of the AEChip to add to the AEChip menu
     */
    public void setAeChipClass (Class deviceClass){
//        log.infox("AEViewer.setAeChipClass("+deviceClass+")");
        try{
            if ( filterFrame != null ){
                filterFrame.dispose();
                filterFrame = null;
            }
            filterFrameBuilt = false;
            filtersToggleButton.setVisible(false);
            viewFiltersMenuItem.setEnabled(false);
            showBiasgen(false);
            cleanup(); // close sockets so they can be reused
            Constructor<AEChip> constructor = deviceClass.getConstructor();
            if ( constructor == null ){
                log.warning("null chip constructer, need to select valid chip class");
                return;
            }
            AEChip oldChip = getChip();
            if ( getChip() == null ){ // handle initial case
                constructChip(constructor);
            } else{
                synchronized ( chip ){ // TODO handle live case -- this is not ideal thread programming - better to sync on a lock object in the run loop
                    synchronized ( extractor ){
                        synchronized ( renderer ){
                            if ( getChip().getRemoteControl() != null ){
                                getChip().getRemoteControl().close();
                            } // TODO this cleanup should happen automatically via a mechanism to close the chip somehow
                            constructChip(constructor);
                        }
                    }
                }
            }
            if ( chip == null ){
                log.warning("null chip, not continuing");
                return;
            }
            aeChipClass = deviceClass;
            setPreferredAEChipClass(aeChipClass);
            // chip constructed above, should have renderer already constructed as well
            if ( chip.getRenderer() != null && chip.getRenderer() instanceof Calibratible ){
                // begin added by Philipp
//            if (aeChipClass.renderer instanceof AdaptiveIntensityRenderer){ // that does not work since the renderer is obviously not defined before a chip gets instanciated
//            if (aeChipClass.getName().equals("no.uio.ifi.jaer.chip.foveated.UioFoveatedImager") ||
//                    aeChipClass.getName().equals("no.uio.ifi.jaer.chip.staticbiovis.UioStaticBioVis")) {
                calibrationStartStop.setVisible(true);
                calibrationStartStop.setEnabled(true);
            } else{
                calibrationStartStop.setVisible(false);
                calibrationStartStop.setEnabled(false);
            }
            // end added by Philipp
            if ( aemon != null ){ // force reopen
                aemon.close();
            }
            makeCanvas();
//            setTitleAccordingToState(); // called anyhow in ViewLoop.run
            Component[] devMenuComps = deviceMenu.getMenuComponents();
            for ( int i = 0 ; i < devMenuComps.length ; i++ ){
                if ( devMenuComps[i] instanceof JRadioButtonMenuItem ){
                    JMenuItem item = (JRadioButtonMenuItem)devMenuComps[i];
                    if ( item.getActionCommand().equals(aeChipClass.getName()) ){
                        item.setSelected(true);
                        break;
                    }
                }
            }
            fixLoggingControls();
            filterChain = chip.getFilterChain();
            if ( filterChain == null ){
                filtersToggleButton.setVisible(false);
                viewFiltersMenuItem.setEnabled(false);
            } else{
                filterChain.reset();
                filtersToggleButton.setVisible(true);
                viewFiltersMenuItem.setEnabled(true);
            }
            HardwareInterface hw = chip.getHardwareInterface();
            if ( hw != null ){
                log.warning("setting hardware interface of " + chip + " to " + hw);
                aemon = (AEMonitorInterface)hw;
            }

            showFilters(enableFiltersOnStartup);
            // fix selected radio button for chip class
            if ( deviceMenu.getItemCount() == 0 ){
                log.warning("tried to select device in menu but no device menu has been built yet");
            }
            for ( int i = 0 ; i < deviceMenu.getItemCount() ; i++ ){
                JMenuItem m = deviceMenu.getItem(i);
                if ( m != null && m instanceof JRadioButtonMenuItem && m.getText() == aeChipClass.getName() ){
                    m.setSelected(true);
                    break;
                }
            }
            getSupport().firePropertyChange("chip",oldChip,getChip());
        } catch ( Exception e ){
            e.printStackTrace();
        }
    }

    private void constructChip (Constructor<AEChip> constructor){
        try{
//            System.out.println("AEViewer.constructChip(): constructing chip with constructor "+constructor);
            setChip(constructor.newInstance((java.lang.Object[])null));
            getChip().setAeViewer(this);  // set this now so that chip has AEViewer for building BiasgenFrame etc properly
            extractor = chip.getEventExtractor();
            renderer = chip.getRenderer();

            extractor.setSubsamplingEnabled(subsampleEnabledCheckBoxMenuItem.isSelected());
            extractor.setSubsampleThresholdEventCount(renderer.getSubsampleThresholdEventCount()); // awkward connection between components here - ideally chip should contrain info about subsample limit
            if ( chip.getBiasgen() != null && !chip.getBiasgen().isInitialized() ){
                chip.getBiasgen().showUnitializedBiasesWarningDialog(this);
            }
        } catch ( Exception e ){
            log.warning("AEViewer.constructChip exception " + e.getMessage());
            e.printStackTrace();
        }
    }

    synchronized void makeCanvas (){
        if ( chipCanvas != null ){
            imagePanel.remove(chipCanvas.getCanvas());
        }
        if ( chip == null ){
            log.warning("null chip, not making canvas");
            return;
        }
        chipCanvas = chip.getCanvas();
        chipCanvas.setOpenGLEnabled(isOpenGLRenderingEnabled());
        imagePanel.add(chipCanvas.getCanvas());

//        chipCanvas.getCanvas().invalidate();
        // find display menu reference and fill it with display menu for this canvas
        viewMenu.remove(displayMethodMenu);
        viewMenu.add(chipCanvas.getDisplayMethodMenu());
        displayMethodMenu = chipCanvas.getDisplayMethodMenu();
        viewMenu.invalidate();

//        validate();
//        pack();
        // causes a lot of flashing ... Toolkit.getDefaultToolkit().setDynamicLayout(true); // dynamic resizing  -- see if this bombs!
    }

    /** This method sets the "current file" which sets the field, the preferences of the last file, and the window title. It does not
    actually start playing the file.
    @see AEViewer.AEPlayer
     */
    protected void setCurrentFile (File f){
        currentFile = new File(f.getPath());
        lastFile = currentFile;
        prefs.put("AEViewer.lastFile",lastFile.toString());
//        System.out.println("put AEViewer.lastFile="+lastFile);
        setTitleAccordingToState();
    }

    /** If the AEViewer is playing (or has played) a file, then this method returns it.
    @return the File
    @see PlayMode
     */
    public File getCurrentFile (){
        return currentFile;
    }
    /** writes frames and frame sequences for video making using, e.g. adobe premiere */
    class CanvasFileWriter{
        /*
        Part for OpenGL capture from http://www.cs.plu.edu/~dwolff/talks/jogl-ccsc/src/j_ScreenCapture/ScreenCaptureExample.java
         * Example 10: Screen capture
         *
         * This example demonstrates how to capture the OpenGL buffer into
         * a BufferedImage, and then optionally write it to a PNG file.
         *
         * Author: David Wolff
         *
         * Licensed under the Creative Commons Attribution License 2.5:
         * http://creativecommons.org/licenses/by/2.5/
         */
        boolean writingMovieEnabled = false;
        Canvas canvas;
        int frameNumber = 0;
        java.io.File sequenceDir;
        String sequenceName = "sequence";
        int snapshotNumber = 0; // this is appended automatically to single snapshot filenames
        String snapshotName = "snapshot";

        String getFilename (){
            return sequenceName + String.format("%04d.png",frameNumber);
        }

        synchronized void startWritingMovie (){
//            if(isOpenGLRenderingEnabled()){
//                JOptionPane.showMessageDialog(AEViewer.this,"Disable OpenGL graphics from the View menu first");
//                return;
//            }
            if ( !isActiveRenderingEnabled() ){
                JOptionPane.showMessageDialog(AEViewer.this,"Active rendering will be enabled for movie writing");
                setActiveRenderingEnabled(true);
                viewActiveRenderingEnabledMenuItem.setSelected(true);
            }
            String homeDir = System.getProperty("user.dir"); // the program startup folder
//            System.getenv("USERPROFILE"); // returns documents and setttings\tobi, not my documents
            sequenceName = JOptionPane.showInputDialog("<html>Sequence name?<br>This folder will be created in the directory<br> " + homeDir + "</html>");
            if ( sequenceName == null || sequenceName.equals("") ){
                log.info("canceled image sequence");
                return;
            }
            log.info("creating directory " + homeDir + File.separator + sequenceName);
            sequenceDir = new File(homeDir + File.separator + sequenceName);
            if ( sequenceDir.exists() ){
                JOptionPane.showMessageDialog(AEViewer.this,sequenceName + " already exists");
                return;
            }
            boolean madeit = sequenceDir.mkdir();
            if ( !madeit ){
                JOptionPane.showMessageDialog(AEViewer.this,"couldn't create directory " + sequenceName);
                return;
            }
            frameNumber = 0;
            writingMovieEnabled = true;
        }

        synchronized void stopWritingMovie (){
            writingMovieEnabled = false;
            openLoggingFolderWindow();
        }

        synchronized void writeMovieFrame (){
            try{
                Container container = getContentPane();
                canvas = chip.getCanvas().getCanvas();
                Rectangle r = canvas.getBounds();
                Image image = canvas.createImage(r.width,r.height);
                Graphics g = image.getGraphics();
                synchronized ( container ){
                    container.paintComponents(g);
                    if ( !isOpenGLRenderingEnabled() ){
                        chip.getCanvas().paint(g);
                        ImageIO.write((RenderedImage)image,"png",new File(sequenceDir,getFilename()));
                    } else if ( chip.getCanvas().getImageOpenGL() != null ){
                        ImageIO.write(chip.getCanvas().getImageOpenGL(),"png",new File(sequenceDir,getFilename()));
                    }
                }
                frameNumber++;
            } catch ( IOException ioe ){
                ioe.printStackTrace();
            }
        }

//        /** Take an Image associated with a file, and wait until it is
//         *  done loading. Just a simple application of MediaTracker.
//         *  If you are loading multiple images, don't use this
//         *  consecutive times; instead use the version that takes
//         *  an array of images.
//         */
//
//        boolean waitForImage(Image image, Component c) {
//            MediaTracker tracker = new MediaTracker(c);
//            tracker.addImage(image, 0);
//            try {
//                tracker.waitForAll();
//            } catch(InterruptedException ie) {}
//            return(!tracker.isErrorAny());
//        }
        synchronized void writeSnapshotImage (){
            boolean wasPaused = isPaused();
            setPaused(true);
            JFileChooser fileChooser = new JFileChooser();
            String lastFilePath = prefs.get("AEViewer.lastFile",""); // get the last folder
            lastFile = new File(lastFilePath);
//            fileChooser.setFileFilter(datFileFilter);
            PNGFileFilter indexFileFilter = new PNGFileFilter();
            fileChooser.addChoosableFileFilter(indexFileFilter);
            fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
            if ( lastImageFile == null ){
                lastImageFile = new File("snapshot.png");
            }
            fileChooser.setSelectedFile(lastImageFile);
            int retValue = fileChooser.showOpenDialog(AEViewer.this);
            if ( retValue == JFileChooser.APPROVE_OPTION ){
                lastImageFile = fileChooser.getSelectedFile();
                String suffix = "";
                if ( !lastImageFile.getName().endsWith(".png") ){
                    suffix = ".png";
                }
                try{
//                    if(!isOpenGLRenderingEnabled()){
                    Container container = getContentPane();
                    Rectangle r = container.getBounds();
                    Image image = container.createImage(r.width,r.height);
                    Graphics g = image.getGraphics();
                    synchronized ( container ){
                        container.paintComponents(g);
                        g.translate(0,statisticsPanel.getHeight());
                        chip.getCanvas().paint(g);
//                    ImageIO.write((RenderedImage)imageOpenGL, "png", new File(snapshotName+snapshotNumber+".png"));
//                        log.info("writing image to file");
                        ImageIO.write((RenderedImage)image,"png",new File(lastImageFile.getPath() + suffix));
                    }
//                    }else{ // open gl canvas
//                    }
                    snapshotNumber++;
                } catch ( Exception e ){
                    e.printStackTrace();
                }
            }
            setPaused(wasPaused);
        }
    }

    /** builds list of attached hardware interfaces by asking the
     * hardware interface factory for the list. */
    private synchronized void buildInterfaceMenu (){
        if ( !isWindows() ){ // TODO not really anymore with linux interface to retinas
            return;
        }
//        System.out.println("AEViewer.buildInterfaceMenu");
        ButtonGroup bg = new ButtonGroup();
        interfaceMenu.removeAll();

        // make a 'none' item
        JRadioButtonMenuItem noneInterfaceButton = new JRadioButtonMenuItem("None");
        noneInterfaceButton.putClientProperty("HardwareInterface",null);
        interfaceMenu.add(noneInterfaceButton);
        bg.add(noneInterfaceButton);
        noneInterfaceButton.addActionListener(new ActionListener(){
            public void actionPerformed (ActionEvent evt){
//                log.info("selected null interface");
                if ( chip.getHardwareInterface() != null ){
                    chip.getHardwareInterface().close();
                }
                chip.setHardwareInterface(null);
            }
        });
        interfaceMenu.add(new JSeparator());

        int n = HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
        boolean choseOneButton = false;
        JRadioButtonMenuItem interfaceButton = null;
        for ( int i = 0 ; i < n ; i++ ){
            HardwareInterface hw = HardwareInterfaceFactory.instance().getInterface(i);
            if ( hw == null ){
                continue;
            } // in case it disappeared
            interfaceButton = new JRadioButtonMenuItem(hw.toString());
            interfaceButton.putClientProperty("HardwareInterfaceNumber",new Integer(i));
            interfaceMenu.add(interfaceButton);
            bg.add(interfaceButton);
            interfaceButton.addActionListener(new ActionListener(){
                public void actionPerformed (ActionEvent evt){
                    JComponent comp = (JComponent)evt.getSource();
                    int interfaceNumber = (Integer)comp.getClientProperty("HardwareInterfaceNumber");
                    HardwareInterface hw = HardwareInterfaceFactory.instance().getInterface(interfaceNumber);
//                    HardwareInterface hw=(HardwareInterface)comp.getClientProperty("HardwareInterface");
                    log.info("selected interface " + evt.getActionCommand() + " with HardwareInterface number" + interfaceNumber + " which is " + hw);
                    chip.setHardwareInterface(hw);
                }
            });
            HardwareInterface chipInterface = chip.getHardwareInterface();
//            if (chipInterface != null) {
////                log.info("chip.getHardwareInterface="+chip.getHardwareInterface());
//            }
//            if (hw != null) {
////                log.info("hw="+hw);
//            }
            //check if device in menu is already one assigned to this chip, by String comparison. Checking by object doesn't work because
            // new device objects are created by HardwareInterfaceFactory's'
            if ( chipInterface != null && hw != null && chipInterface.toString().equals(hw.toString()) ){
                interfaceButton.setSelected(true);
                choseOneButton = true;
            }
//            if(chip!=null && chip.getHardwareInterface()==hw) b.setSelected(true);
        }
        if ( !choseOneButton ){
            noneInterfaceButton.setSelected(true);
        }
    }

    void fixBiasgenControls (){
//        // debug
//        biasesToggleButton.setEnabled(true);
//        biasesToggleButton.setVisible(true);
//        viewBiasesMenuItem.setEnabled(true);
        if ( chip == null ){
            return;
        }
        if ( chip.getBiasgen() == null ){
            biasesToggleButton.setEnabled(false);
            biasesToggleButton.setVisible(false);
            viewBiasesMenuItem.setEnabled(false);
            return;
        } else{
            biasesToggleButton.setEnabled(true);
            biasesToggleButton.setVisible(true);
            viewBiasesMenuItem.setEnabled(true);
        }
        if ( biasgenFrame != null ){
            boolean vis = biasgenFrame.isVisible();
            biasesToggleButton.setSelected(vis);
        }
    }

    // nulls out all hardware interfaces to start fresh
    private void nullifyHardware (){
        aemon = null; // if device is blank a bare interface may have been constructed and we must ensure the deivce is reinstantiated after programming
        if ( chip != null ){
            chip.setHardwareInterface(null); // should set chip's biasgen to null also
//            if(chip.getBiasgen()!=null) chip.getBiasgen().setHardwareInterface(null);
        }
    }

    /**
     * Tries to open the AE interface.
     *
     */
    private void openAEMonitor (){
        synchronized ( viewLoop ){ // TODO grabs lock on viewLoop so that other methods, e.g. startPlayback, which also grab this lock, will not race to set playMode. touchy design.
            if ( getPlayMode() == PlayMode.PLAYBACK ){ // don't open hardware if playing a file
                return;
            }
            if ( aemon != null && aemon.isOpen() ){
                if ( getPlayMode() != PlayMode.SEQUENCING ){
                    setPlayMode(PlayMode.LIVE);
                }
                // playMode=PlayMode.LIVE; // in case (like StereoHardwareInterface) where device can be open but not by AEViewer
                return;
            }
            try{
                openHardwareIfNonambiguous();
                // openHardwareIfNonambiguous will set chip's hardware interface, here we store local reference
                // if it's an aemon, then its an event monitor
                if ( chip.getHardwareInterface() != null && chip.getHardwareInterface() instanceof AEMonitorInterface ){
                    aemon = (AEMonitorInterface)chip.getHardwareInterface();
                    if ( aemon == null || !( aemon instanceof AEMonitorInterface ) ){
                        fixDeviceControlMenuItems();
                        fixLoggingControls();
                        fixBiasgenControls();
                        return;
                    }

                    aemon.setChip(chip);
                    aemon.open(); // will throw BlankDeviceException if device is blank.
                    fixLoggingControls();
                    fixBiasgenControls();
                    fixDeviceControlMenuItems();
                    tickUs = aemon.getTimestampTickUs();
                    // note it is important that this openAEMonitor succeeed BEFORE aemon is assigned to biasgen,
                    // which immeiately tries to openAEMonitor and download biases, creating a storm of complaints if not sucessful!

                    if ( aemon instanceof BiasgenHardwareInterface ){
                        Biasgen bg = chip.getBiasgen();
                        if ( bg == null ){
                            log.warning(chip + " is BiasgenHardwareInterface but has null biasgen object, not setting biases");
                        } else{
                            chip.getBiasgen().sendConfiguration(chip.getBiasgen());
//                chip.setHardwareInterface(aemon); // if we do this, events do not start coming again after reconnect of device
//                biasgen=chip.getBiasgen();
//                if(biasgenFrame==null) {
//                    biasgenFrame=new BiasgenFrame(biasgen);  // should check if exists...
//                }
                        }
                    }

                    if ( chip.getHardwareInterface() != null && chip.getHardwareInterface() instanceof AESequencerInterface ){
                        // the 'chip's' hardware interface is a pure sequencer
                        enableMonSeqMenu(true);
                    }
                    if ( this.getPlayMode() != PlayMode.SEQUENCING ){
                        setPlayMode(PlayMode.LIVE);
                    }
                    // TODO interface should do this check nonmonotonic timestamps automatically
                    if ( aemon != null && aemon instanceof StereoHardwareInterface ){
                        ( (StereoHardwareInterface)aemon ).setIgnoreTimestampNonmonotonicity(!checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.isSelected());
                    }
                } else if ( chip.getHardwareInterface() != null && chip.getHardwareInterface() instanceof AESequencerInterface ){
                    // the 'chip's' hardware interface is a pure sequencer
                    enableMonSeqMenu(true);
                }
                setPlaybackControlsEnabledState(true);

            } catch ( BlankDeviceException bd ){
                if ( !blankDeviceMessageShown ){
                    log.info(bd.getMessage() + " supressing further blank device messages");
                    blankDeviceMessageShown = true;
                    int v = JOptionPane.showConfirmDialog(this,"Blank Cypress FX2 found (" + aemon + "). Do you want to open the Cypress FX2 Programming utility?");

                    if ( v == JOptionPane.YES_OPTION ){
                        CypressFX2EEPROM instance = new CypressFX2EEPROM();
                        instance.setExitOnCloseEnabled(false);
                        instance.setVisible(true);
                    }
                }
                aemon.close();
                nullifyHardware();

            } catch ( Exception e ){
                log.warning(e.getMessage());
                if ( aemon != null ){
                    aemon.close();
                }
                nullifyHardware();
                setPlaybackControlsEnabledState(false);
                fixDeviceControlMenuItems();
                fixLoggingControls();
                fixBiasgenControls();
                setPlayMode(PlayMode.WAITING);
            }
            fixDeviceControlMenuItems();
        }
    }

    void setPlaybackControlsEnabledState (boolean yes){
        loggingButton.setEnabled(yes);
        biasesToggleButton.setEnabled(yes);
        closeMenuItem.setEnabled(yes);
        increasePlaybackSpeedMenuItem.setEnabled(yes);
        decreasePlaybackSpeedMenuItem.setEnabled(yes);
        rewindPlaybackMenuItem.setEnabled(yes);
        flextimePlaybackEnabledCheckBoxMenuItem.setEnabled(yes);
        togglePlaybackDirectionMenuItem.setEnabled(yes);
        toggleMarkCheckBoxMenuItem.setEnabled(yes);
        if ( !playerControlPanel.isVisible() ){
            playerControlPanel.setVisible(yes);
        }
    }

//    volatile boolean stop=false; // volatile because multiple threads will access
    int renderCount = 0;
    int numEvents;
    AEPacketRaw aeRaw;
//    AEPacket2D ae;
    private EventPacket packet;
//    EventPacket packetFiltered;
    boolean skipRender = false;
//    volatile private boolean paused=false; // multiple threads will access
    boolean overrunOccurred = false;
    int tickUs = 1;
    public AEPlayer aePlayer = new AEPlayer(this,this);
    int noEventCounter = 0;
    /** This thread acquires events and renders them to the RetinaCanvas for active rendering. The other components render themselves
     * on the usual Swing rendering thread.
     */
    class ViewLoop extends Thread{
        Graphics2D g = null;
//        volatile boolean rerenderOtherComponents=false;
//        volatile boolean renderImageEnabled=true;
        volatile boolean singleStepEnabled = false, doSingleStep = false;
        int numRawEvents, numFilteredEvents;

        public ViewLoop (){
            super();
            setName("AEViewer.ViewLoop");
        }

        void renderPacket (EventPacket ae){
            if ( aePlayer.isChoosingFile() ){
                return;
            } // don't render while filechooser is active
            boolean subsamplingEnabled = renderer.isSubsamplingEnabled();
            if ( isPaused() ){
                renderer.setSubsamplingEnabled(false);
            }
            renderer.render(packet);
            if ( isPaused() ){
                renderer.setSubsamplingEnabled(subsamplingEnabled);
            }
//            if(renderImageEnabled) {
            if ( isActiveRenderingEnabled() ){
                if ( canvasFileWriter.writingMovieEnabled ){
                    chipCanvas.grabNextImage();
                }
                chipCanvas.paintFrame(); // actively paint frame now, either with OpenGL or Java2D, depending on switch
            } else{
//                log.info("repaint by "+1000/frameRater.getDesiredFPS()+" ms");
                chipCanvas.repaint();
//                chipCanvas.repaint(1000 / frameRater.getDesiredFPS()); // ask for repaint within frame time
            }

            if ( canvasFileWriter.writingMovieEnabled ){
                canvasFileWriter.writeMovieFrame();
            }
        } // renderEvents

        private EventPacket extractPacket (AEPacketRaw aeRaw){
            boolean subsamplingEnabled = renderer.isSubsamplingEnabled();
            if ( isPaused() ){
                extractor.setSubsamplingEnabled(false);
            }
            EventPacket packet = extractor.extractPacket(aeRaw);
            if ( isPaused() ){
                extractor.setSubsamplingEnabled(subsamplingEnabled);
            }
            return packet;
        }
        private EngineeringFormat engFmt = new EngineeringFormat();
        private long beforeTime = 0, afterTime;
        private int lastts = 0;
        volatile boolean stop = false;

        /** Sets the stop flag so that the ViewLoop exits the run method on the next iteration.
         * 
         */
        public void stopThread (){
            stop = true;
        }

        /** the main loop - this is the 'game loop' of the program */
        public void run (){ // don't know why this needs to be thread-safe
        /* TODO synchronized tobi removed sync because it was causing deadlocks on exit. */
            while ( !isVisible() ){
                try{
                    log.info("sleeping until isVisible()==true");
                    Thread.sleep(1000); // sleep to let components realize on screen - may be crashing opengl on nvidia drivers if we draw to unrealized components
                } catch ( InterruptedException e ){
                }
            }
            while ( stop == false && !isInterrupted() ){

                // now get the data to be displayed
                if ( !isPaused() || isSingleStep() ){
//                    if(isSingleStep()){
//                        log.info("getting data for single step");
//                    }
                    // if !paused we always get data. below, if singleStepEnabled, we set paused after getting data.
                    // when the user unpauses via menu, we disable singleStepEnabled
                    // another flag, doSingleStep, tells loop to do a single data acquisition and then pause again
                    // in this branch, get new data to show
                    getFrameRater().takeBefore();
                    switch ( getPlayMode() ){
                        case SEQUENCING:
                            HardwareInterface chipHardwareInterface = chip.getHardwareInterface();

                            if ( chipHardwareInterface == null ){
                                log.warning("AE monitor/sequencer became null while sequencing");
                                setPlayMode(PlayMode.WAITING);
                                break;
                            }
                            AESequencerInterface aemonseq = (AESequencerInterface)chip.getHardwareInterface();
                            int nToSend = aemonseq.getNumEventsToSend();
                            int position = 0;
                            if ( nToSend != 0 ){
                                position = (int)( playerControls.getPlayerSlider().getMaximum() * (float)aemonseq.getNumEventsSent() / nToSend );
                            }

                            sliderDontProcess = true;
                            playerControls.getPlayerSlider().setValue(position);
                            if ( !( chip.getHardwareInterface() instanceof AEMonitorInterface ) ){
                                continue;                            // if we're a monitor plus sequencer than go on to monitor events, otherwise break out since there are no events to monitor
                            }
                        case LIVE:
                            openAEMonitor();
                            if ( aemon == null || !aemon.isOpen() ){
                                setPlayMode(PlayMode.WAITING);
                                try{
                                    Thread.sleep(300);
                                } catch ( InterruptedException e ){
                                    log.warning("LIVE openAEMonitor sleep interrupted");
                                }
                                continue;
                            }
                            overrunOccurred = aemon.overrunOccurred();
                            try{
                                // try to get an event to avoid rendering empty (black) frames
//                                int triesLeft = 15;
//                                do {
//                                    if (!isInterrupted()) {
                                aemon = (AEMonitorInterface)chip.getHardwareInterface(); // TODOkeep setting aemon to be chip's interface, this is kludge
                                if ( aemon == null ){
                                    log.warning("AEViewer.ViewLoop.run(): AEMonitorInterface became null during acquisition");
                                    throw new HardwareInterfaceException("hardware interface became null");
                                }
                                aeRaw = aemon.acquireAvailableEventsFromDriver();
//                                        System.out.println("got "+aeRaw);
//                                    }

//                                    if (aeRaw.getNumEvents() > 0) {
//                                        break;
//                                    }
//                                    System.out.print("."); System.out.flush();
//                                    try {
//                                        Thread.currentThread().sleep(3);
//                                    } catch (InterruptedException e) {
//                                        log.warning("LIVE attempt to get data loop interrupted");
//                                    }
//                                } while (triesLeft-- > 0);
////                                if(aeRaw.getNumEvents()==0) {System.out.print("0 events ..."); System.out.flush();}

                            } catch ( HardwareInterfaceException e ){
                                if ( stop ){
                                    break; // break out of loop if this aquisition thread got HardwareInterfaceException because we are exiting
                                }
                                setPlayMode(PlayMode.WAITING);
                                log.warning("while acquiring data caught " + e.toString());
//                                e.printStackTrace();
                                nullifyHardware();

                                continue;
                            } catch ( ClassCastException cce ){
                                setPlayMode(PlayMode.WAITING);
                                log.warning("Interface changed out from under us: " + cce.toString());
                                cce.printStackTrace();
                                nullifyHardware();
                                continue;
                            }
                            break;
                        case PLAYBACK:
//                            Thread thisThread=Thread.currentThread();
//                            System.out.println("thread "+thisThread+" getting events for renderCount="+renderCount);
                            aeRaw = getAePlayer().getNextPacket(aePlayer);
                            getAePlayer().adjustTimesliceForRealtimePlayback();
//                            System.out.println("."); System.out.flush();
                            break;
                        case REMOTE:
                            if ( unicastInputEnabled ){
                                if ( unicastInput == null ){
                                    log.warning("null unicastInput, going to WAITING state");
                                    setPlayMode(PlayMode.WAITING);
                                } else{
                                    aeRaw = unicastInput.readPacket();
                                }

                            }
                            if ( socketInputEnabled ){
                                if ( getAeSocket() == null ){
                                    log.warning("null socketInputStream, going to WAITING state");
                                    setPlayMode(PlayMode.WAITING);
                                    socketInputEnabled = false;
                                } else{
                                    try{
                                        aeRaw = getAeSocket().readPacket(); // reads a packet if there is data available
                                    } catch ( IOException e ){
                                        if ( stop ){
                                            break;
                                        }
                                        log.warning(e.toString() + ": closing and reconnecting...");
                                        try{
                                            getAeSocket().close();
                                            aeSocket = new AESocket(); // uses last values stored in preferences
                                            aeSocket.connect();
                                            log.info("connected " + aeSocket);
                                        } catch ( IOException ex3 ){
                                            log.warning(ex3 + ": failed reconnection, sleeping 1 s before trying again");
                                            try{
                                                Thread.sleep(1000);
                                            } catch ( InterruptedException ex2 ){
                                            }
                                        }
                                    }
                                }
                            }
                            if ( spreadInputEnabled ){
                                try{
                                    aeRaw = spreadInterface.readPacket();
                                } catch ( SpreadException e ){
                                    log.warning(e.toString());
                                }
                            }
                            if ( multicastInputEnabled ){
                                if ( aeMulticastInput == null ){
                                    log.warning("null aeMulticastInput, going to WAITING state");
                                    setPlayMode(PlayMode.WAITING);
                                } else{
                                    aeRaw = aeMulticastInput.readPacket();
                                }
                            }
                            break;
                        case WAITING:
//                          notify(); // notify waiter on this thread that we have gone to WAITING state
                            openAEMonitor();
                            if ( aemon == null || !aemon.isOpen() ){
                                statisticsLabel.setText("Choose interface from Interface menu");
//                                setPlayMode(PlayMode.WAITING); // we don't need to set it again
                                try{
                                    Thread.sleep(300);
                                } catch ( InterruptedException e ){
                                    log.info("WAITING interrupted");
                                }
                                continue;
                            }
                    } // playMode switch to get data

                    if ( aeRaw == null ){
                        fpsDelay();
                        continue;
                    }


                    numRawEvents = aeRaw.getNumEvents();


                    // new style packet with reused event objects
                    packet = extractPacket(aeRaw);

                    // filter events, do processing on them in rendering loop here
                    if ( filterChain.getProcessingMode() == FilterChain.ProcessingMode.RENDERING || playMode != playMode.LIVE ){
                        try{
                            packet = filterChain.filterPacket(packet);
                        } catch ( Exception e ){
                            log.warning("Caught " + e + ", disabling all filters");
                            e.printStackTrace();
                            for ( EventFilter f:filterChain ){
                                f.setFilterEnabled(false);
                            }
                        }
                        if ( packet == null ){
                            log.warning("null packet after filtering");
                            continue;
                        }
                    }

                    // write to network socket if a client has opened a socket to us
                    // we serve up events on this socket
                    if ( getAeServerSocket() != null && getAeServerSocket().getAESocket() != null ){
                        AESocket s = getAeServerSocket().getAESocket();
                        try{
                            if ( !isLogFilteredEventsEnabled() ){
                                s.writePacket(aeRaw);
                            } else{
                                // send the reconstructed packet after filtering
                                AEPacketRaw aeRawRecon = extractor.reconstructRawPacket(packet);
                                s.writePacket(aeRawRecon);
                            }
                        } catch ( IOException e ){
//                            e.printStackTrace();
                            log.warning("sending packet " + aeRaw + " from " + this + " to " + s + " failed, closing socket");
                            try{
                                s.close();
                            } catch ( IOException e2 ){
                                e2.printStackTrace();
                            } finally{
                                getAeServerSocket().setSocket(null);
                            }
                        }
                    }

                    // spread is a network system used by projects like the caltech darpa urban challange alice vehicle
                    if ( spreadOutputEnabled ){
                        try{
                            if ( !isLogFilteredEventsEnabled() ){
                                spreadInterface.writePacket(aeRaw);
                            } else{
                                // log the reconstructed packet after filtering
                                AEPacketRaw aeRawRecon = extractor.reconstructRawPacket(packet);
                                spreadInterface.writePacket(aeRawRecon);
                            }
                        } catch ( SpreadException e ){
                            e.printStackTrace();
                        }
                    }

                    // if we are multicasting output send it out here
                    if ( multicastOutputEnabled && aeMulticastOutput != null ){
                        try{
                            if ( !isLogFilteredEventsEnabled() ){
                                aeMulticastOutput.writePacket(aeRaw);
                            } else{
                                // log the reconstructed packet after filtering
                                AEPacketRaw aeRawRecon = extractor.reconstructRawPacket(packet);
                                aeMulticastOutput.writePacket(aeRawRecon);
                            }
                        } catch ( IOException e ){
                            e.printStackTrace();
                        }
                    }

                    if ( unicastOutputEnabled && unicastOutput != null ){
                        try{
                            if ( !isLogFilteredEventsEnabled() ){
                                unicastOutput.writePacket(aeRaw);
                            } else{
                                // log the reconstructed packet after filtering
                                AEPacketRaw aeRawRecon = extractor.reconstructRawPacket(packet);
                                unicastOutput.writePacket(aeRawRecon);
                            }
                        } catch ( IOException e ){
                            e.printStackTrace();
                        }
                    }

                    chip.setLastData(packet);// set the rendered data for use by various methods

                    // if we are logging data to disk do it here
                    if ( loggingEnabled ){
                        synchronized ( loggingOutputStream ){
                            try{
                                if ( !isLogFilteredEventsEnabled() ){
                                    loggingOutputStream.writePacket(aeRaw); // log all events
                                } else{
                                    // log the reconstructed packet after filtering
                                    AEPacketRaw aeRawRecon = extractor.reconstructRawPacket(packet);
                                    loggingOutputStream.writePacket(aeRawRecon);
                                }
                            } catch ( IOException e ){
                                e.printStackTrace();
                                loggingEnabled = false;
                                try{
                                    loggingOutputStream.close();
                                } catch ( IOException e2 ){
                                    e2.printStackTrace();
                                }
                            }
                        }
                        if ( loggingTimeLimit > 0 ){ // we may have a defined time for logging, if so, check here and abort logging
                            if ( System.currentTimeMillis() - loggingStartTime > loggingTimeLimit ){
                                log.info("logging time limit reached, stopping logging");
                                try{
                                    SwingUtilities.invokeAndWait(new Runnable(){
                                        public void run (){
                                            stopLogging(true); // must run this in AWT thread because it messes with file menu
                                        }
                                    });
                                } catch ( Exception e ){
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    singleStepDone();
                    numEvents = packet.getSize();
                    numFilteredEvents = packet.getSize();

                    if ( numFilteredEvents == 0 && !isRenderBlankFramesEnabled() ){
//                        log.info("blank frame, not rendering it");
                        fpsDelay();
                        continue;
                    }

                    if ( numEvents == 0 ){
                        noEventCounter++;
                    } else{
                        noEventCounter = 0;
                    }


                } // getting data

                if ( skipPacketsRenderingCount-- == 0 ){
                    // we only got new events if we were NOT paused. but now we can apply filters, different rendering methods, etc in 'paused' condition
                    makeStatisticsLabel(packet);
                    renderPacket(packet);
                    skipPacketsRenderingCount = skipPacketsRenderingNumber;

                }

                getFrameRater().takeAfter();
                renderCount++;

                fpsDelay();
            }
            log.info("AEViewer.run(): stop=" + stop + " isInterrupted=" + isInterrupted());
            if ( aemon != null ){
                aemon.close();
            }
            if ( unicastOutput != null ){
                unicastOutput.close();
            }
            if ( unicastInput != null ){
                unicastInput.close();
            }

//            if(windowSaver!=null)
//                try {
//                    windowSaver.saveSettings();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }

//            chipCanvas.getCanvas().setVisible(false);
//            remove(chipCanvas.getCanvas());
//            if (getJaerViewer() != null) {
//                log.info("removing " + AEViewer.this + " viewer from caviar viewer list");
//                getJaerViewer().removeViewer(AEViewer.this); // we want to remove the viewer, not this inner class
//            }
////            dispose();
//
//            if (getJaerViewer() == null || getJaerViewer().getViewers().isEmpty()) {
//                if (biasgenFrame != null && biasgenFrame.isModificationsSaved()) {
//                    System.exit(0);
//                }
//            }
        } // viewLoop.run()

        void fpsDelay (){
            if ( !isPaused() ){
                getFrameRater().delayForDesiredFPS();
            } else{
                synchronized ( this ){ // reason for grabbing monitor is because if we are sliding the slider, we need to make sure we have control of the view loop
                    try{
                        wait(100);
                    } catch ( java.lang.InterruptedException e ){
                        log.info("viewLoop wait() interrupted: " + e.getMessage() + " cause is " + e.getCause());
                    }
                }
            }
        }

        private float getDtMs (EventPacket packet){
            float dtMs = 0;
            int numEvents = packet.getSize();
            if ( numEvents > 0 ){
//                lastts=ae.getLastTimestamp();
                lastts = packet.getLastTimestamp();
            }
            if ( numEvents > 1 ){
                dtMs = (float)( ( lastts - packet.getFirstTimestamp() ) / ( tickUs * 1e3 ) );
            }
            return dtMs;
        }

        private float getTimeExpansion (float dtMs){
            float expansion = getFrameRater().getAverageFPS() * dtMs / 1000f;
            return expansion;
        }

        private void makeStatisticsLabel (EventPacket packet){
            if ( renderCount % 10 == 0 || isPaused() || isSingleStep() || getFrameRater().getDesiredFPS() < 20 ){  // don't draw stats too fast
                if ( getAePlayer().isChoosingFile() ){
                    return;
                } // don't render stats while user is choosing file
//            if(ae==null) return;
                if ( packet == null ){
                    return;
                }
                String thisTimeString = null;

                float ratekeps = packet.getEventRateHz() / 1e3f;
                switch ( getPlayMode() ){
                    case SEQUENCING:
                    case LIVE:
                        if ( aemon == null ){
                            return;
                        }
//                    ratekeps=aemon.getEstimatedEventRate()/1000f;
                        thisTimeString = String.format("%5.2f",lastts * aemon.getTimestampTickUs() * 1e-6f);
                        break;
                    case PLAYBACK:
//                    if(ae.getNumEvents()>2) ratekeps=(float)ae.getNumEvents()/(float)dtMs;
//                    if(packet.getSize()>2) ratekeps=(float)packet.getSize()/(float)dtMs;
                        thisTimeString = String.format("%5.2f",getAePlayer().getTime() * 1e-6f); // hack here, we don't know timestamp from data file, we assume 1us
                        break;
                    case REMOTE:
                        thisTimeString = String.format("%5.2f",packet.getLastTimestamp() * 1e-6f);
                        break;
                }
                String rateString = null;
                if ( ratekeps >= 10e3f ){
                    rateString = "   >10 Meps";
                } else{
                    rateString = String.format("%5.2f keps",ratekeps);
                }
                int cs = renderer.getColorScale();

                String ovstring;
                if ( overrunOccurred ){
                    ovstring = "(overrun)";
                } else{
                    ovstring = "";
                }
                String s = null;

//                if(numEvents==0) s=thisTimeString+ "s: No events";
//                else {
                String timeExpansionString;
                float dtMs = getDtMs(packet);
                if ( isPaused() ){
                    timeExpansionString = "Paused";
                } else if ( getPlayMode() == PlayMode.LIVE || getPlayMode() == PlayMode.SEQUENCING ){
                    timeExpansionString = "Live/Seq";
                } else{
                    float expansion = getTimeExpansion(dtMs);
                    if ( expansion == 0 ){
                        timeExpansionString = "???";
                    } else if ( expansion > 1 ){
                        timeExpansionString = String.format("%5.1fX",expansion);
                    } else{
                        timeExpansionString = String.format("%5.1f/",1 / expansion);
                    }
                }

                String numEventsString;
                if ( chip.getFilterChain().isAnyFilterEnabled() ){
                    if ( filterChain.isTimedOut() ){
                        numEventsString = String.format("%5d/%-5d TO  ",numRawEvents,numFilteredEvents);
                    } else{
                        numEventsString = String.format("%5d/%-5d evts",numRawEvents,numFilteredEvents);
                    }
                } else{
                    numEventsString = String.format("%5d evts",numRawEvents);
                }

                s = String.format("%8ss@%-8ss,%s %s,%s,%2.0f/%dfps,%4s,%2dms,%s=%2d",
                        engFmt.format((float)dtMs / 1000),
                        thisTimeString,
                        numEventsString.toString(),
                        ovstring,
                        rateString,
                        getFrameRater().getAverageFPS(),
                        getFrameRater().getDesiredFPS(),
                        timeExpansionString,
                        getFrameRater().getLastDelayMs(),
                        renderer.isAutoscaleEnabled() ? "AS" : "FS", // auto or fullscale rendering color
                        cs);
//                }
                setStatisticsLabel(s);
                if ( overrunOccurred ){
                    statisticsLabel.setForeground(Color.RED);
                } else{
                    statisticsLabel.setForeground(Color.BLACK);
                }
            }
        }
    }

    /** Sets the viewer's status message at the bottom of the window.
     *
     * @param s the string
     * @see #setStatusMessage(String)
     */
    public void setStatusMessage (String s){
        statusTextField.setText(s);
        statusTextField.setToolTipText(s);
    }

    /** Sets the color of the status field text - e.g. to highlight it.
     *
     * @param c
     */
    public void setStatusColor (Color c){
        statusTextField.setForeground(c);
    }

    public void exceptionOccurred (Exception x,Object source){
        if ( x.getMessage() != null ){
            setStatusMessage(x.getMessage());
            startStatusClearer(Color.RED);
        } else{
            if ( statusClearerThread != null && statusClearerThread.isAlive() ){
                return;
            }
            setStatusMessage(null);
            Color c = Color.GREEN;
            Color c2 = c.darker();
            startStatusClearer(c2);
        }
    }

    private void startStatusClearer (Color color){
        setStatusColor(color);
        if ( statusClearerThread != null && statusClearerThread.isAlive() ){
            statusClearerThread.renew();
        } else{
            statusClearerThread = new StatusClearerThread();
            statusClearerThread.start();
        }

    }
    StatusClearerThread statusClearerThread = null;
    /** length of exception highlighting in status bar in ms */
    public final long STATUS_DURATION = 1000;
    class StatusClearerThread extends Thread{
        long endTime;

        public void renew (){
//            System.out.println("renewing status change");
            endTime = System.currentTimeMillis() + STATUS_DURATION;
        }

        @Override
        public void run (){
//            System.out.println("start status clearer thread");
            endTime = System.currentTimeMillis() + STATUS_DURATION;
            try{
                while ( System.currentTimeMillis() < endTime ){
                    Thread.sleep(STATUS_DURATION);
                }
                setStatusColor(Color.DARK_GRAY);
            } catch ( InterruptedException e ){
            }

        }
    }

    void setStatisticsLabel (final String s){
//        statisticsLabel.setText(s);
        SwingUtilities.invokeLater(new Runnable(){
            public void run (){
                statisticsLabel.setText(s);
            }
        });
// for some reason invoking in swing thread (as it seems you should) doesn't always update the label... mystery
//        try {
//            SwingUtilities.invokeAndWait(new Runnable(){
//                public void run(){
//                    statisticsLabel.setText(s);
////                    if(statisticsLabel.getWidth()>statisticsPanel.getWidth()) {
//////                        System.out.println("statisticsLabel width="+statisticsLabel.getWidth()+" > statisticsPanel width="+statisticsPanel.getWidth());
////                        // possibly resize statistics font size
////                        formComponentResized(null);
////                    }
//                }
//            });
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    int getScreenRefreshRate (){
        int rate = 60;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        for ( int i = 0 ; i < gs.length ; i++ ){
            DisplayMode dm = gs[i].getDisplayMode();
            // Get refresh rate in Hz
            int refreshRate = dm.getRefreshRate();
            if ( refreshRate == DisplayMode.REFRESH_RATE_UNKNOWN ){
                log.warning("AEViewer.getScreenRefreshRate: got unknown refresh rate for screen " + i + ", assuming 60");
                refreshRate = 60;
            } else{
//                log.info("AEViewer.getScreenRefreshRate: screen "+i+" has refresh rate "+refreshRate);
            }
            if ( i == 0 ){
                rate = refreshRate;
            }
        }
        return rate;
    }// computes and executes appropriate delayForDesiredFPS to try to maintain constant rendering rate

    /** Measure actual rendering frame rate and creates appropriate frame delay.
     * 
     */
    public class FrameRater{
        final int MAX_FPS = 120;
        int desiredFPS = prefs.getInt("AEViewer.FrameRater.desiredFPS",getScreenRefreshRate());
        final int nSamples = 10;
        long[] samplesNs = new long[ nSamples ];
        int index = 0;
        int delayMs = 1;
        int desiredPeriodMs = (int)( 1000f / desiredFPS );

        final void setDesiredFPS (int fps){
            if ( fps < 1 ){
                fps = 1;
            } else if ( fps > MAX_FPS ){
                fps = MAX_FPS;
            }
            desiredFPS = fps;
            prefs.putInt("AEViewer.FrameRater.desiredFPS",fps);
            desiredPeriodMs = 1000 / fps;
        }

        final int getDesiredFPS (){
            return desiredFPS;
        }

        final float getAveragePeriodNs (){
            long sum = 0;
            for ( int i = 0 ; i < nSamples ; i++ ){
                sum += samplesNs[i];
            }
            return (float)sum / nSamples;
        }

        public  final float getAverageFPS (){
            return 1f / ( getAveragePeriodNs() / 1e9f );
        }

        final float getLastFPS (){
            return 1f / ( lastdt / 1e9f );
        }

        final int getLastDelayMs (){
            return delayMs;
        }

        final long getLastDtNs (){
            return lastdt;
        }
        private long beforeTimeNs = System.nanoTime(), lastdt, afterTimeNs;

        //  call this ONCE after capture/render. it will store the time since the last call
        void takeBefore (){
            beforeTimeNs = System.nanoTime();
        }
        private long lastAfterTime = System.nanoTime();

        //  call this ONCE after capture/render. it will store the time since the last call
        final void takeAfter (){
            afterTimeNs = System.nanoTime();
            lastdt = afterTimeNs - beforeTimeNs;
            samplesNs[index++] = afterTimeNs - lastAfterTime;
            lastAfterTime = afterTimeNs;
            if ( index >= nSamples ){
                index = 0;
            }
        }

        // call this to delayForDesiredFPS enough to make the total time including last sample period equal to desiredPeriodMs
        final void delayForDesiredFPS (){
            delayMs = (int)Math.round(desiredPeriodMs - (float)lastdt / 1000000);
            if ( delayMs < 0 ){
                delayMs = 1;
            }
            try{
                Thread.sleep(delayMs);
            } catch ( java.lang.InterruptedException e ){
            }
        }
    }

    /** Fires a property change "stopme", and then stops playback or closes device */
    public void stopMe (){
        getSupport().firePropertyChange("stopme",null,null);
//        log.info(Thread.currentThread()+ "AEViewer.stopMe() called");
        switch ( getPlayMode() ){
            case PLAYBACK:
                getAePlayer().stopPlayback(); // TODO can lead to deadlock if stopMe is called from a thread that
                break;
            case LIVE:
            case WAITING:
                viewLoop.stopThread();
                showBiasgen(false);
                break;
            case REMOTE:
                if ( unicastInputEnabled ){
                    unicastInput.close();
                    unicastInputEnabled = false;
                }
                if ( multicastInputEnabled ){
                    aeMulticastInput.close();
                    multicastInputEnabled = false;
                }
                if ( spreadInputEnabled ){
                    spreadInterface.disconnect();
                    spreadInputEnabled = false;
                }
                if ( socketInputEnabled ){
                    try{
                        aeSocket.close();
                    } catch ( IOException e ){
                        log.warning(e.toString());
                    }
                    socketInputEnabled = false;
                }
        }
// viewer is removed by WindowClosing event
//        if(caviarViewer!=null ){
//            log.info(this+" being removed from caviarViewer viewers list");
//            caviarViewer.getViewers().remove(this);
//        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jProgressBar1 = new javax.swing.JProgressBar();
        renderModeButtonGroup = new javax.swing.ButtonGroup();
        monSeqOpModeButtonGroup = new javax.swing.ButtonGroup();
        statisticsPanel = new javax.swing.JPanel();
        imagePanel = new javax.swing.JPanel();
        bottomPanel = new javax.swing.JPanel();
        buttonsPanel = new javax.swing.JPanel();
        biasesToggleButton = new javax.swing.JToggleButton();
        filtersToggleButton = new javax.swing.JToggleButton();
        dontRenderToggleButton = new javax.swing.JToggleButton();
        loggingButton = new javax.swing.JToggleButton();
        playerControlPanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        statusTextField = new javax.swing.JTextField();
        showConsoleOutputButton = new javax.swing.JButton();
        resizePanel = new javax.swing.JPanel();
        resizeLabel = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        newViewerMenuItem = new javax.swing.JMenuItem();
        openMenuItem = new javax.swing.JMenuItem();
        closeMenuItem = new javax.swing.JMenuItem();
        timestampResetBitmaskMenuItem = new javax.swing.JMenuItem();
        jSeparator8 = new javax.swing.JSeparator();
        saveImageMenuItem = new javax.swing.JMenuItem();
        saveImageSequenceMenuItem = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JSeparator();
        loggingMenuItem = new javax.swing.JMenuItem();
        loggingPlaybackImmediatelyCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        loggingSetTimelimitMenuItem = new javax.swing.JMenuItem();
        logFilteredEventsCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        networkSeparator = new javax.swing.JSeparator();
        remoteMenu = new javax.swing.JMenu();
        openSocketInputStreamMenuItem = new javax.swing.JMenuItem();
        reopenSocketInputStreamMenuItem = new javax.swing.JMenuItem();
        serverSocketOptionsMenuItem = new javax.swing.JMenuItem();
        jSeparator15 = new javax.swing.JSeparator();
        multicastOutputEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        openMulticastInputMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator14 = new javax.swing.JSeparator();
        unicastOutputEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        openUnicastInputMenuItem = new javax.swing.JMenuItem();
        syncSeperator = new javax.swing.JSeparator();
        syncEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator16 = new javax.swing.JSeparator();
        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        exitSeperator = new javax.swing.JSeparator();
        exitMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        markInPointMenuItem = new javax.swing.JMenuItem();
        markOutPointMenuItem = new javax.swing.JMenuItem();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        viewBiasesMenuItem = new javax.swing.JMenuItem();
        dataWindowMenu = new javax.swing.JMenuItem();
        filtersSubMenu = new javax.swing.JMenu();
        viewFiltersMenuItem = new javax.swing.JMenuItem();
        enableFiltersOnStartupCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        graphicsSubMenu = new javax.swing.JMenu();
        viewOpenGLEnabledMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewActiveRenderingEnabledMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewRenderBlankFramesCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        skipPacketsRenderingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        subsampleEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        subSampleSizeMenuItem = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JSeparator();
        cycleColorRenderingMethodMenuItem = new javax.swing.JMenuItem();
        increaseContrastMenuItem = new javax.swing.JMenuItem();
        decreaseContrastMenuItem = new javax.swing.JMenuItem();
        autoscaleContrastEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        calibrationStartStop = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JSeparator();
        cycleDisplayMethodButton = new javax.swing.JMenuItem();
        displayMethodMenu = new javax.swing.JMenu();
        jSeparator12 = new javax.swing.JSeparator();
        acccumulateImageEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewIgnorePolarityCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        increaseFrameRateMenuItem = new javax.swing.JMenuItem();
        decreaseFrameRateMenuItem = new javax.swing.JMenuItem();
        pauseRenderingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewSingleStepMenuItem = new javax.swing.JMenuItem();
        zeroTimestampsMenuItem = new javax.swing.JMenuItem();
        jSeparator11 = new javax.swing.JSeparator();
        increasePlaybackSpeedMenuItem = new javax.swing.JMenuItem();
        decreasePlaybackSpeedMenuItem = new javax.swing.JMenuItem();
        rewindPlaybackMenuItem = new javax.swing.JMenuItem();
        flextimePlaybackEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        togglePlaybackDirectionMenuItem = new javax.swing.JMenuItem();
        toggleMarkCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        measureTimeMenuItem = new javax.swing.JMenuItem();
        jSeparator10 = new javax.swing.JSeparator();
        zoomInMenuItem = new javax.swing.JMenuItem();
        zoomOutMenuItem = new javax.swing.JMenuItem();
        zoomCenterMenuItem = new javax.swing.JMenuItem();
        unzoomMenuItem = new javax.swing.JMenuItem();
        deviceMenu = new javax.swing.JMenu();
        deviceMenuSpparator = new javax.swing.JSeparator();
        customizeDevicesMenuItem = new javax.swing.JMenuItem();
        interfaceMenu = new javax.swing.JMenu();
        refreshInterfaceMenuItem = new javax.swing.JMenuItem();
        controlMenu = new javax.swing.JMenu();
        increaseBufferSizeMenuItem = new javax.swing.JMenuItem();
        decreaseBufferSizeMenuItem = new javax.swing.JMenuItem();
        increaseNumBuffersMenuItem = new javax.swing.JMenuItem();
        decreaseNumBuffersMenuItem = new javax.swing.JMenuItem();
        jSeparator9 = new javax.swing.JSeparator();
        changeAEBufferSizeMenuItem = new javax.swing.JMenuItem();
        jSeparator5 = new javax.swing.JSeparator();
        updateFirmwareMenuItem = new javax.swing.JMenuItem();
        cypressFX2EEPROMMenuItem = new javax.swing.JMenuItem();
        setDefaultFirmwareMenuItem = new javax.swing.JMenuItem();
        monSeqMenu = new javax.swing.JMenu();
        sequenceMenuItem = new javax.swing.JMenuItem();
        enableMissedEventsCheckBox = new javax.swing.JCheckBoxMenuItem();
        monSeqMissedEventsMenuItem = new javax.swing.JMenuItem();
        jSeparator13 = new javax.swing.JSeparator();
        monSeqOperationModeMenu = new javax.swing.JMenu();
        monSeqOpMode0 = new javax.swing.JRadioButtonMenuItem();
        monSeqOpMode1 = new javax.swing.JRadioButtonMenuItem();
        helpMenu = new javax.swing.JMenu();
        contentMenuItem = new javax.swing.JMenuItem();
        helpRetinaMenuItem = new javax.swing.JMenuItem();
        helpUserGuideMenuItem = new javax.swing.JMenuItem();
        helpAERCablingUserGuideMenuItem = new javax.swing.JMenuItem();
        javadocMenuItem = new javax.swing.JMenuItem();
        javadocWebMenuItem = new javax.swing.JMenuItem();
        jSeparator7 = new javax.swing.JSeparator();
        aboutMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("AEViewer");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });

        statisticsPanel.setFocusable(false);
        statisticsPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                statisticsPanelComponentResized(evt);
            }
        });
        getContentPane().add(statisticsPanel, java.awt.BorderLayout.NORTH);

        imagePanel.setEnabled(false);
        imagePanel.setFocusable(false);
        imagePanel.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                imagePanelMouseWheelMoved(evt);
            }
        });
        imagePanel.setLayout(new java.awt.BorderLayout());
        getContentPane().add(imagePanel, java.awt.BorderLayout.CENTER);

        bottomPanel.setLayout(new java.awt.BorderLayout());

        buttonsPanel.setMaximumSize(new java.awt.Dimension(1002, 200));
        buttonsPanel.setLayout(new javax.swing.BoxLayout(buttonsPanel, javax.swing.BoxLayout.X_AXIS));

        biasesToggleButton.setFont(new java.awt.Font("Tahoma", 0, 10));
        biasesToggleButton.setText("Biases");
        biasesToggleButton.setToolTipText("Shows or hides the bias generator control panel");
        biasesToggleButton.setAlignmentY(0.0F);
        biasesToggleButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        biasesToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                biasesToggleButtonActionPerformed(evt);
            }
        });
        buttonsPanel.add(biasesToggleButton);

        filtersToggleButton.setFont(new java.awt.Font("Tahoma", 0, 10));
        filtersToggleButton.setText("Filters");
        filtersToggleButton.setToolTipText("Shows or hides the filter window");
        filtersToggleButton.setAlignmentY(0.0F);
        filtersToggleButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        filtersToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filtersToggleButtonActionPerformed(evt);
            }
        });
        buttonsPanel.add(filtersToggleButton);

        dontRenderToggleButton.setFont(new java.awt.Font("Tahoma", 0, 10));
        dontRenderToggleButton.setText("Don't render");
        dontRenderToggleButton.setToolTipText("Disables rendering to spped up processing");
        dontRenderToggleButton.setAlignmentY(0.0F);
        dontRenderToggleButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        dontRenderToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dontRenderToggleButtonActionPerformed(evt);
            }
        });
        buttonsPanel.add(dontRenderToggleButton);

        loggingButton.setFont(new java.awt.Font("Tahoma", 0, 10));
        loggingButton.setMnemonic('l');
        loggingButton.setText("Start logging");
        loggingButton.setToolTipText("Starts or stops logging or relogging");
        loggingButton.setAlignmentY(0.0F);
        loggingButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonsPanel.add(loggingButton);

        playerControlPanel.setToolTipText("");
        playerControlPanel.setAlignmentY(0.0F);
        playerControlPanel.setMaximumSize(new java.awt.Dimension(32000, 32000));
        playerControlPanel.setLayout(new java.awt.BorderLayout());
        buttonsPanel.add(playerControlPanel);

        bottomPanel.add(buttonsPanel, java.awt.BorderLayout.CENTER);

        jPanel1.setLayout(new java.awt.BorderLayout());

        statusTextField.setEditable(false);
        statusTextField.setFont(new java.awt.Font("Tahoma", 0, 10));
        statusTextField.setToolTipText("Status messages show here");
        statusTextField.setFocusable(false);
        jPanel1.add(statusTextField, java.awt.BorderLayout.CENTER);

        showConsoleOutputButton.setFont(new java.awt.Font("Tahoma", 0, 10));
        showConsoleOutputButton.setText("Console");
        showConsoleOutputButton.setToolTipText("Shows console output window");
        showConsoleOutputButton.setFocusable(false);
        showConsoleOutputButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        showConsoleOutputButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        showConsoleOutputButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        showConsoleOutputButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showConsoleOutputButtonActionPerformed(evt);
            }
        });
        jPanel1.add(showConsoleOutputButton, java.awt.BorderLayout.EAST);

        bottomPanel.add(jPanel1, java.awt.BorderLayout.NORTH);

        resizePanel.setMinimumSize(new java.awt.Dimension(0, 0));
        resizePanel.setPreferredSize(new java.awt.Dimension(24, 24));
        resizePanel.setLayout(new java.awt.BorderLayout());

        resizeLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        resizeLabel.setIcon(new TriangleSquareWindowsCornerIcon());
        new TriangleSquareWindowsCornerIcon();
        resizeLabel.setToolTipText("Resizes window");
        resizeLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                resizeLabelMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                resizeLabelMouseExited(evt);
            }
            public void mousePressed(java.awt.event.MouseEvent evt) {
                resizeLabelMousePressed(evt);
            }
        });
        resizeLabel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                resizeLabelMouseDragged(evt);
            }
        });
        resizePanel.add(resizeLabel, java.awt.BorderLayout.SOUTH);

        bottomPanel.add(resizePanel, java.awt.BorderLayout.EAST);

        getContentPane().add(bottomPanel, java.awt.BorderLayout.SOUTH);

        fileMenu.setMnemonic('f');
        fileMenu.setText("File");

        newViewerMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_MASK));
        newViewerMenuItem.setMnemonic('N');
        newViewerMenuItem.setText("New viewer");
        newViewerMenuItem.setToolTipText("Opens a new viewer");
        newViewerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newViewerMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(newViewerMenuItem);

        openMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, 0));
        openMenuItem.setMnemonic('o');
        openMenuItem.setText("Open logged data file...");
        openMenuItem.setToolTipText("Opens a logged data file for playback");
        openMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openMenuItem);

        closeMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_MASK));
        closeMenuItem.setMnemonic('C');
        closeMenuItem.setText("Close");
        closeMenuItem.setToolTipText("Closes this viewer or the playing data file");
        closeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(closeMenuItem);

        timestampResetBitmaskMenuItem.setMnemonic('t');
        timestampResetBitmaskMenuItem.setText("dummy, set in constructor");
        timestampResetBitmaskMenuItem.setToolTipText("Setting a bitmask here will memorize and subtract timestamps when address  & bitmask != 0");
        timestampResetBitmaskMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timestampResetBitmaskMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(timestampResetBitmaskMenuItem);
        fileMenu.add(jSeparator8);

        saveImageMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        saveImageMenuItem.setText("Save image as PNG");
        saveImageMenuItem.setToolTipText("Saves a single PNG of the canvas");
        saveImageMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveImageMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveImageMenuItem);

        saveImageSequenceMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        saveImageSequenceMenuItem.setText("Save image sequence");
        saveImageSequenceMenuItem.setToolTipText("Saves sequence to a set of files in folder");
        saveImageSequenceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveImageSequenceMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveImageSequenceMenuItem);
        fileMenu.add(jSeparator6);

        loggingMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, 0));
        loggingMenuItem.setText("Start logging data");
        loggingMenuItem.setToolTipText("Starts or stops logging to disk");
        fileMenu.add(loggingMenuItem);

        loggingPlaybackImmediatelyCheckBoxMenuItem.setText("Playback logged data immediately after logging enabled");
        loggingPlaybackImmediatelyCheckBoxMenuItem.setToolTipText("If enabled, logged data plays back immediately");
        loggingPlaybackImmediatelyCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loggingPlaybackImmediatelyCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(loggingPlaybackImmediatelyCheckBoxMenuItem);

        loggingSetTimelimitMenuItem.setText("Set logging time limit...");
        loggingSetTimelimitMenuItem.setToolTipText("Sets a time limit for logging");
        loggingSetTimelimitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loggingSetTimelimitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(loggingSetTimelimitMenuItem);

        logFilteredEventsCheckBoxMenuItem.setText("Enable filtering of logged or network output events");
        logFilteredEventsCheckBoxMenuItem.setToolTipText("Logging or network writes apply active filters first (reduces file size or network traffi)");
        logFilteredEventsCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logFilteredEventsCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(logFilteredEventsCheckBoxMenuItem);
        fileMenu.add(networkSeparator);

        remoteMenu.setMnemonic('r');
        remoteMenu.setText("Remote");

        openSocketInputStreamMenuItem.setMnemonic('r');
        openSocketInputStreamMenuItem.setText("Open remote server input stream socket...");
        openSocketInputStreamMenuItem.setToolTipText("Opens a remote connection for stream (TCP) packets of  events ");
        openSocketInputStreamMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openSocketInputStreamMenuItemActionPerformed(evt);
            }
        });
        remoteMenu.add(openSocketInputStreamMenuItem);

        reopenSocketInputStreamMenuItem.setMnemonic('l');
        reopenSocketInputStreamMenuItem.setText("Reopen last or preferred stream socket input stream");
        reopenSocketInputStreamMenuItem.setToolTipText("After an input socket has been opened (and preferences set), this quickly closes and reopens it");
        reopenSocketInputStreamMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reopenSocketInputStreamMenuItemActionPerformed(evt);
            }
        });
        remoteMenu.add(reopenSocketInputStreamMenuItem);

        serverSocketOptionsMenuItem.setText("Stream socket server options...");
        serverSocketOptionsMenuItem.setToolTipText("Sets options for server sockets");
        serverSocketOptionsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                serverSocketOptionsMenuItemActionPerformed(evt);
            }
        });
        remoteMenu.add(serverSocketOptionsMenuItem);
        remoteMenu.add(jSeparator15);

        multicastOutputEnabledCheckBoxMenuItem.setMnemonic('s');
        multicastOutputEnabledCheckBoxMenuItem.setText("Enable Multicast (UDP) AE Output");
        multicastOutputEnabledCheckBoxMenuItem.setToolTipText("<html>Enable multicast AE output (datagrams)<br><strong>Warning! Will flood network if there are no listeners.</strong></html>");
        multicastOutputEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                multicastOutputEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        remoteMenu.add(multicastOutputEnabledCheckBoxMenuItem);

        openMulticastInputMenuItem.setMnemonic('s');
        openMulticastInputMenuItem.setText("Enable Multicast (UDP) AE input");
        openMulticastInputMenuItem.setToolTipText("Enable multicast AE input (datagrams)");
        openMulticastInputMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMulticastInputMenuItemActionPerformed(evt);
            }
        });
        remoteMenu.add(openMulticastInputMenuItem);
        remoteMenu.add(jSeparator14);

        unicastOutputEnabledCheckBoxMenuItem.setMnemonic('o');
        unicastOutputEnabledCheckBoxMenuItem.setText("Enable unicast datagram (UDP) output...");
        unicastOutputEnabledCheckBoxMenuItem.setToolTipText("Enables unicast datagram (UDP) outputs to a single receiver");
        unicastOutputEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unicastOutputEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        remoteMenu.add(unicastOutputEnabledCheckBoxMenuItem);

        openUnicastInputMenuItem.setMnemonic('i');
        openUnicastInputMenuItem.setText("Open Unicast (UDP) remote AE input...");
        openUnicastInputMenuItem.setToolTipText("Opens a remote UDP unicast AE input");
        openUnicastInputMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openUnicastInputMenuItemActionPerformed(evt);
            }
        });
        remoteMenu.add(openUnicastInputMenuItem);

        fileMenu.add(remoteMenu);
        fileMenu.add(syncSeperator);

        syncEnabledCheckBoxMenuItem.setText("Synchronized logging/playback enabled");
        syncEnabledCheckBoxMenuItem.setToolTipText("All viwers start/stop logging in synchrony and playback times are synchronized");
        syncEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                syncEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(syncEnabledCheckBoxMenuItem);
        fileMenu.add(jSeparator16);

        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.setSelected(true);
        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.setText("Check for non-monotonic time in input streams");
        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.setToolTipText("If enabled, nonmonotonic time stamps are checked for in input streams from file or network");
        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem);
        fileMenu.add(exitSeperator);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, 0));
        exitMenuItem.setMnemonic('x');
        exitMenuItem.setText("Exit");
        exitMenuItem.setToolTipText("Exits all viewers");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        editMenu.setMnemonic('e');
        editMenu.setText("Edit");
        editMenu.setEnabled(false);

        markInPointMenuItem.setMnemonic('i');
        markInPointMenuItem.setText("Mark IN point");
        editMenu.add(markInPointMenuItem);

        markOutPointMenuItem.setMnemonic('o');
        markOutPointMenuItem.setText("Mark OUT point");
        editMenu.add(markOutPointMenuItem);

        cutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_MASK));
        cutMenuItem.setText("Cut");
        editMenu.add(cutMenuItem);

        copyMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_MASK));
        copyMenuItem.setText("Copy");
        editMenu.add(copyMenuItem);

        pasteMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_MASK));
        pasteMenuItem.setText("Paste");
        editMenu.add(pasteMenuItem);

        menuBar.add(editMenu);

        viewMenu.setMnemonic('v');
        viewMenu.setText("View");

        viewBiasesMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, java.awt.event.InputEvent.CTRL_MASK));
        viewBiasesMenuItem.setMnemonic('b');
        viewBiasesMenuItem.setText("Biases");
        viewBiasesMenuItem.setToolTipText("Shows chip or board biasing controls");
        viewBiasesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewBiasesMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(viewBiasesMenuItem);

        dataWindowMenu.setText("Data Window");
        dataWindowMenu.setToolTipText("Shows a general purpose data window (including log output)");
        dataWindowMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataWindowMenuActionPerformed(evt);
            }
        });
        viewMenu.add(dataWindowMenu);

        filtersSubMenu.setMnemonic('f');
        filtersSubMenu.setText("Event Filtering");

        viewFiltersMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_MASK));
        viewFiltersMenuItem.setMnemonic('f');
        viewFiltersMenuItem.setText("Filters");
        viewFiltersMenuItem.setToolTipText("Shows filter controls");
        viewFiltersMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewFiltersMenuItemActionPerformed(evt);
            }
        });
        filtersSubMenu.add(viewFiltersMenuItem);

        enableFiltersOnStartupCheckBoxMenuItem.setText("Enable filters on startup");
        enableFiltersOnStartupCheckBoxMenuItem.setToolTipText("Enables creation of event processing filters on startup");
        enableFiltersOnStartupCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableFiltersOnStartupCheckBoxMenuItemActionPerformed(evt);
            }
        });
        filtersSubMenu.add(enableFiltersOnStartupCheckBoxMenuItem);

        viewMenu.add(filtersSubMenu);
        viewMenu.add(jSeparator1);

        graphicsSubMenu.setMnemonic('g');
        graphicsSubMenu.setText("Graphics options");

        viewOpenGLEnabledMenuItem.setText("Enable OpenGL rendering");
        viewOpenGLEnabledMenuItem.setToolTipText("Enables use of JOGL OpenGL library for rendering");
        viewOpenGLEnabledMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewOpenGLEnabledMenuItemActionPerformed(evt);
            }
        });
        graphicsSubMenu.add(viewOpenGLEnabledMenuItem);

        viewActiveRenderingEnabledMenuItem.setText("Active rendering enabled");
        viewActiveRenderingEnabledMenuItem.setToolTipText("Enables active display of each rendered frame if enabled.\nIf disabled, then  chipCanvas.repaint(1000 / frameRater.getDesiredFPS()) is called for repaint.");
        viewActiveRenderingEnabledMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewActiveRenderingEnabledMenuItemActionPerformed(evt);
            }
        });
        graphicsSubMenu.add(viewActiveRenderingEnabledMenuItem);

        viewRenderBlankFramesCheckBoxMenuItem.setText("Render blank frames");
        viewRenderBlankFramesCheckBoxMenuItem.setToolTipText("If enabled, frames without events are rendered");
        viewRenderBlankFramesCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewRenderBlankFramesCheckBoxMenuItemActionPerformed(evt);
            }
        });
        graphicsSubMenu.add(viewRenderBlankFramesCheckBoxMenuItem);
        graphicsSubMenu.add(jSeparator2);

        skipPacketsRenderingCheckBoxMenuItem.setText("Skip packets rendering enabled...");
        skipPacketsRenderingCheckBoxMenuItem.setToolTipText("Enables skipping rendering of packets");
        skipPacketsRenderingCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                skipPacketsRenderingCheckBoxMenuItemActionPerformed(evt);
            }
        });
        graphicsSubMenu.add(skipPacketsRenderingCheckBoxMenuItem);

        subsampleEnabledCheckBoxMenuItem.setText("Enable subsample rendering");
        subsampleEnabledCheckBoxMenuItem.setToolTipText("use to speed up rendering");
        subsampleEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subsampleEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        graphicsSubMenu.add(subsampleEnabledCheckBoxMenuItem);

        subSampleSizeMenuItem.setText("Choose rendering subsample limit...");
        subSampleSizeMenuItem.setToolTipText("Sets the number of events rendered in subsampling mode");
        subSampleSizeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                subSampleSizeMenuItemActionPerformed(evt);
            }
        });
        graphicsSubMenu.add(subSampleSizeMenuItem);

        viewMenu.add(graphicsSubMenu);
        viewMenu.add(jSeparator3);

        cycleColorRenderingMethodMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, 0));
        cycleColorRenderingMethodMenuItem.setText("Cycle color rendering mode");
        cycleColorRenderingMethodMenuItem.setToolTipText("Changes rendering mode (gray, contrast, RG, color-time)");
        cycleColorRenderingMethodMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cycleColorRenderingMethodMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(cycleColorRenderingMethodMenuItem);

        increaseContrastMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0));
        increaseContrastMenuItem.setText("Increase contrast");
        increaseContrastMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                increaseContrastMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(increaseContrastMenuItem);

        decreaseContrastMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0));
        decreaseContrastMenuItem.setText("Decrease contrast");
        decreaseContrastMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decreaseContrastMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(decreaseContrastMenuItem);

        autoscaleContrastEnabledCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, 0));
        autoscaleContrastEnabledCheckBoxMenuItem.setText("Autoscale contrast enabled");
        autoscaleContrastEnabledCheckBoxMenuItem.setToolTipText("Tries to autoscale histogram values");
        autoscaleContrastEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoscaleContrastEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(autoscaleContrastEnabledCheckBoxMenuItem);

        calibrationStartStop.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K, 0));
        calibrationStartStop.setText("Start Calibration");
        calibrationStartStop.setToolTipText("Hold uniform surface in front of lens and start calibration. Wait a few seconds and stop calibration.");
        calibrationStartStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                calibrationStartStopActionPerformed(evt);
            }
        });
        viewMenu.add(calibrationStartStop);
        viewMenu.add(jSeparator4);

        cycleDisplayMethodButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, 0));
        cycleDisplayMethodButton.setText("Cycle display method");
        cycleDisplayMethodButton.setToolTipText("Cycles the display method");
        cycleDisplayMethodButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cycleDisplayMethodButtonActionPerformed(evt);
            }
        });
        viewMenu.add(cycleDisplayMethodButton);

        displayMethodMenu.setText("display methods (placeholder)");
        viewMenu.add(displayMethodMenu);
        viewMenu.add(jSeparator12);

        acccumulateImageEnabledCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, 0));
        acccumulateImageEnabledCheckBoxMenuItem.setText("Accumulate image");
        acccumulateImageEnabledCheckBoxMenuItem.setToolTipText("Rendered data accumulates over 2d hisograms");
        acccumulateImageEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                acccumulateImageEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(acccumulateImageEnabledCheckBoxMenuItem);

        viewIgnorePolarityCheckBoxMenuItem.setText("Ignore cell type");
        viewIgnorePolarityCheckBoxMenuItem.setToolTipText("Throws away cells type for rendering");
        viewIgnorePolarityCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewIgnorePolarityCheckBoxMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(viewIgnorePolarityCheckBoxMenuItem);

        increaseFrameRateMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0));
        increaseFrameRateMenuItem.setText("Increase rendering frame rate");
        increaseFrameRateMenuItem.setToolTipText("Increases frames/second target for rendering");
        increaseFrameRateMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                increaseFrameRateMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(increaseFrameRateMenuItem);

        decreaseFrameRateMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0));
        decreaseFrameRateMenuItem.setText("Decrease rendering frame rate");
        decreaseFrameRateMenuItem.setToolTipText("Decreases frames/second target for rendering");
        decreaseFrameRateMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decreaseFrameRateMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(decreaseFrameRateMenuItem);

        pauseRenderingCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0));
        pauseRenderingCheckBoxMenuItem.setText("Paused");
        pauseRenderingCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseRenderingCheckBoxMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(pauseRenderingCheckBoxMenuItem);

        viewSingleStepMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PERIOD, 0));
        viewSingleStepMenuItem.setText("Single step");
        viewSingleStepMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewSingleStepMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(viewSingleStepMenuItem);

        zeroTimestampsMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, 0));
        zeroTimestampsMenuItem.setText("Zero timestamps");
        zeroTimestampsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zeroTimestampsMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(zeroTimestampsMenuItem);
        viewMenu.add(jSeparator11);

        increasePlaybackSpeedMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, 0));
        increasePlaybackSpeedMenuItem.setText("Increase playback speed");
        increasePlaybackSpeedMenuItem.setToolTipText("Makes the time slice longer");
        increasePlaybackSpeedMenuItem.setEnabled(false);
        increasePlaybackSpeedMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                increasePlaybackSpeedMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(increasePlaybackSpeedMenuItem);

        decreasePlaybackSpeedMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, 0));
        decreasePlaybackSpeedMenuItem.setText("Decrease playback speed");
        decreasePlaybackSpeedMenuItem.setToolTipText("Makes the time slice shorter");
        decreasePlaybackSpeedMenuItem.setEnabled(false);
        decreasePlaybackSpeedMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decreasePlaybackSpeedMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(decreasePlaybackSpeedMenuItem);

        rewindPlaybackMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, 0));
        rewindPlaybackMenuItem.setText("Rewind");
        rewindPlaybackMenuItem.setEnabled(false);
        rewindPlaybackMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rewindPlaybackMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(rewindPlaybackMenuItem);

        flextimePlaybackEnabledCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, 0));
        flextimePlaybackEnabledCheckBoxMenuItem.setText("Flextime playback enabled");
        flextimePlaybackEnabledCheckBoxMenuItem.setToolTipText("Enables playback with constant number of events");
        flextimePlaybackEnabledCheckBoxMenuItem.setEnabled(false);
        flextimePlaybackEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flextimePlaybackEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(flextimePlaybackEnabledCheckBoxMenuItem);

        togglePlaybackDirectionMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, 0));
        togglePlaybackDirectionMenuItem.setText("Toggle playback direction");
        togglePlaybackDirectionMenuItem.setEnabled(false);
        togglePlaybackDirectionMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                togglePlaybackDirectionMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(togglePlaybackDirectionMenuItem);

        toggleMarkCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, 0));
        toggleMarkCheckBoxMenuItem.setText("Toggle mark present location for rewind");
        toggleMarkCheckBoxMenuItem.setEnabled(false);
        toggleMarkCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleMarkCheckBoxMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(toggleMarkCheckBoxMenuItem);

        measureTimeMenuItem.setMnemonic('m');
        measureTimeMenuItem.setText("Measure time");
        measureTimeMenuItem.setToolTipText("Each click reports statistics about timing since last click");
        measureTimeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                measureTimeMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(measureTimeMenuItem);
        viewMenu.add(jSeparator10);

        zoomInMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PAGE_UP, 0));
        zoomInMenuItem.setText("Zoom in");
        zoomInMenuItem.setToolTipText("Zooms in around mouse point");
        zoomInMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(zoomInMenuItem);

        zoomOutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PAGE_DOWN, 0));
        zoomOutMenuItem.setText("Zoom out");
        zoomOutMenuItem.setToolTipText("Zooms out around mouse point");
        zoomOutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(zoomOutMenuItem);

        zoomCenterMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_END, 0));
        zoomCenterMenuItem.setText("Center display here");
        zoomCenterMenuItem.setToolTipText("Centers display on mouse point");
        zoomCenterMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomCenterMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(zoomCenterMenuItem);

        unzoomMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_HOME, 0));
        unzoomMenuItem.setText("Unzoom");
        unzoomMenuItem.setToolTipText("Goes to default display zooming");
        unzoomMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unzoomMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(unzoomMenuItem);

        menuBar.add(viewMenu);

        deviceMenu.setMnemonic('a');
        deviceMenu.setText("AEChip");
        deviceMenu.setToolTipText("Specifies which chip is connected");
        deviceMenu.add(deviceMenuSpparator);

        customizeDevicesMenuItem.setMnemonic('C');
        customizeDevicesMenuItem.setText("Customize...");
        customizeDevicesMenuItem.setToolTipText("Let's you customize which AEChip's are available");
        customizeDevicesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                customizeDevicesMenuItemActionPerformed(evt);
            }
        });
        deviceMenu.add(customizeDevicesMenuItem);

        menuBar.add(deviceMenu);

        interfaceMenu.setMnemonic('i');
        interfaceMenu.setText("Interface");
        interfaceMenu.setToolTipText("Select the HW interface to use");
        interfaceMenu.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                interfaceMenuMenuSelected(evt);
            }
        });
        interfaceMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interfaceMenuActionPerformed(evt);
            }
        });

        refreshInterfaceMenuItem.setText("Refresh");
        refreshInterfaceMenuItem.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                refreshInterfaceMenuItemComponentShown(evt);
            }
        });
        refreshInterfaceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshInterfaceMenuItemActionPerformed(evt);
            }
        });
        interfaceMenu.add(refreshInterfaceMenuItem);

        menuBar.add(interfaceMenu);

        controlMenu.setMnemonic('c');
        controlMenu.setText("CypressFX2");
        controlMenu.setToolTipText("control CypresFX2 driver parameters");

        increaseBufferSizeMenuItem.setText("Increase hardware buffer size");
        increaseBufferSizeMenuItem.setToolTipText("Increases the host USB fifo size");
        increaseBufferSizeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                increaseBufferSizeMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(increaseBufferSizeMenuItem);

        decreaseBufferSizeMenuItem.setText("Decrease hardware buffer size");
        decreaseBufferSizeMenuItem.setToolTipText("Decreases the host USB fifo size");
        decreaseBufferSizeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decreaseBufferSizeMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(decreaseBufferSizeMenuItem);

        increaseNumBuffersMenuItem.setText("Increase number of hardware buffers");
        increaseNumBuffersMenuItem.setToolTipText("Increases the host number of USB read buffers");
        increaseNumBuffersMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                increaseNumBuffersMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(increaseNumBuffersMenuItem);

        decreaseNumBuffersMenuItem.setText("Decrease num hardware buffers");
        decreaseNumBuffersMenuItem.setToolTipText("Decreases the host number of USB read buffers");
        decreaseNumBuffersMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decreaseNumBuffersMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(decreaseNumBuffersMenuItem);
        controlMenu.add(jSeparator9);

        changeAEBufferSizeMenuItem.setMnemonic('b');
        changeAEBufferSizeMenuItem.setText("Set rendering AE buffer size");
        changeAEBufferSizeMenuItem.setToolTipText("sets size of host raw event buffers used for render/capture data exchnage");
        changeAEBufferSizeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                changeAEBufferSizeMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(changeAEBufferSizeMenuItem);
        controlMenu.add(jSeparator5);

        updateFirmwareMenuItem.setMnemonic('u');
        updateFirmwareMenuItem.setText("Update firmware...");
        updateFirmwareMenuItem.setToolTipText("Updates device firmware with confirm dialog");
        updateFirmwareMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateFirmwareMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(updateFirmwareMenuItem);

        cypressFX2EEPROMMenuItem.setMnemonic('e');
        cypressFX2EEPROMMenuItem.setText("(Advanced users only) CypressFX2 EEPPROM Utility");
        cypressFX2EEPROMMenuItem.setToolTipText("(advanced users) Opens dialog to download device firmware ");
        cypressFX2EEPROMMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cypressFX2EEPROMMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(cypressFX2EEPROMMenuItem);

        setDefaultFirmwareMenuItem.setMnemonic('f');
        setDefaultFirmwareMenuItem.setText("Set default firmware for blank device...");
        setDefaultFirmwareMenuItem.setToolTipText("Sets the firmware that is downloaded to a blank CypressFX2");
        setDefaultFirmwareMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setDefaultFirmwareMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(setDefaultFirmwareMenuItem);

        menuBar.add(controlMenu);

        monSeqMenu.setText("MonSeq");
        monSeqMenu.setToolTipText("For sequencer or monitor+sequencer devices");
        monSeqMenu.setEnabled(false);

        sequenceMenuItem.setMnemonic('f');
        sequenceMenuItem.setText("Sequence data file...");
        sequenceMenuItem.setToolTipText("You can select a recorded data file to sequence");
        sequenceMenuItem.setActionCommand("start");
        sequenceMenuItem.setEnabled(false);
        sequenceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sequenceMenuItemActionPerformed(evt);
            }
        });
        monSeqMenu.add(sequenceMenuItem);

        enableMissedEventsCheckBox.setText("Enable Missed Events");
        enableMissedEventsCheckBox.setEnabled(false);
        enableMissedEventsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableMissedEventsCheckBoxActionPerformed(evt);
            }
        });
        monSeqMenu.add(enableMissedEventsCheckBox);

        monSeqMissedEventsMenuItem.setText("Get number of missed events");
        monSeqMissedEventsMenuItem.setToolTipText("If the device is a monitor, this will show how many events were missed");
        monSeqMissedEventsMenuItem.setEnabled(false);
        monSeqMissedEventsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                monSeqMissedEventsMenuItemActionPerformed(evt);
            }
        });
        monSeqMenu.add(monSeqMissedEventsMenuItem);
        monSeqMenu.add(jSeparator13);

        monSeqOperationModeMenu.setText("Timestamp tick");

        monSeqOpModeButtonGroup.add(monSeqOpMode0);
        monSeqOpMode0.setSelected(true);
        monSeqOpMode0.setText("1 microsecond ");
        monSeqOpMode0.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                monSeqOpMode0ActionPerformed(evt);
            }
        });
        monSeqOperationModeMenu.add(monSeqOpMode0);

        monSeqOpModeButtonGroup.add(monSeqOpMode1);
        monSeqOpMode1.setText("0.2 microsecond");
        monSeqOpMode1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                monSeqOpMode1ActionPerformed(evt);
            }
        });
        monSeqOperationModeMenu.add(monSeqOpMode1);

        monSeqMenu.add(monSeqOperationModeMenu);

        menuBar.add(monSeqMenu);

        helpMenu.setMnemonic('h');
        helpMenu.setText("Help");

        contentMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        contentMenuItem.setMnemonic('c');
        contentMenuItem.setText("jAER project web (jaer.wiki.sourceforge.net)");
        contentMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                contentMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(contentMenuItem);

        helpRetinaMenuItem.setText("Silicon retina web");
        helpRetinaMenuItem.setToolTipText("Goes to web site for silicon retina");
        helpRetinaMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpRetinaMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(helpRetinaMenuItem);

        helpUserGuideMenuItem.setText("USB2 Mini user guide (PDF)");
        helpUserGuideMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpUserGuideMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(helpUserGuideMenuItem);

        helpAERCablingUserGuideMenuItem.setText("AER cabling pin description (PDF)");
        helpAERCablingUserGuideMenuItem.setToolTipText("Cabling for AER headers");
        helpAERCablingUserGuideMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpAERCablingUserGuideMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(helpAERCablingUserGuideMenuItem);

        javadocMenuItem.setText("Javadoc (local)");
        javadocMenuItem.setToolTipText("Shows Javadoc for classes if it has been built");
        javadocMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                javadocMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(javadocMenuItem);

        javadocWebMenuItem.setText("Javadoc (SourceForge snapshot)");
        javadocWebMenuItem.setToolTipText("Goes to online snapshot of javadoc");
        javadocWebMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                javadocWebMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(javadocWebMenuItem);
        helpMenu.add(jSeparator7);

        aboutMenuItem.setText("About");
        aboutMenuItem.setToolTipText("Version information");
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private void javadocWebMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_javadocWebMenuItemActionPerformed
        try{
            BrowserLauncher.openURL(HELP_URL_JAVADOC_WEB);
        } catch ( IOException e ){
            JOptionPane.showMessageDialog(this,"<html>" + e.getMessage() + "<br>" + HELP_URL_JAVADOC_WEB + " is not available.","Javadoc not available",JOptionPane.INFORMATION_MESSAGE);
        }
    }//GEN-LAST:event_javadocWebMenuItemActionPerformed
//    volatile boolean playerSliderMousePressed=false;
    volatile boolean playerSliderWasPaused = false;

    private void resizeLabelMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeLabelMouseExited
        setCursor(preResizeCursor);
    }//GEN-LAST:event_resizeLabelMouseExited
    Cursor preResizeCursor = Cursor.getDefaultCursor();

    private void resizeLabelMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeLabelMouseEntered
        preResizeCursor = getCursor();
        setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
    }//GEN-LAST:event_resizeLabelMouseEntered

    private void resizeLabelMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeLabelMousePressed
        oldSize = getSize();
        startResizePoint = evt.getPoint();
    }//GEN-LAST:event_resizeLabelMousePressed

    private void resizeLabelMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeLabelMouseDragged
        Point resizePoint = evt.getPoint();
        int widthInc = resizePoint.x - startResizePoint.x;
        int heightInc = resizePoint.y - startResizePoint.y;
        setSize(getWidth() + widthInc,getHeight() + heightInc);
    }//GEN-LAST:event_resizeLabelMouseDragged

    private void helpRetinaMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpRetinaMenuItemActionPerformed
        try{
            BrowserLauncher.openURL(HELP_URL_RETINA);
        } catch ( IOException e ){
            contentMenuItem.setText(e.getMessage());
        }
    }//GEN-LAST:event_helpRetinaMenuItemActionPerformed

    private void dataWindowMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataWindowMenuActionPerformed
        JAERViewer.globalDataViewer.setVisible(!jaerViewer.globalDataViewer.isVisible());
    }//GEN-LAST:event_dataWindowMenuActionPerformed

    private void serverSocketOptionsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serverSocketOptionsMenuItemActionPerformed
        if ( aeServerSocket == null ){
            log.warning("null aeServerSocket");
            JOptionPane.showMessageDialog(this,"No server socket to configure - maybe port is already bound? (Check the output logging)","No server socket",JOptionPane.WARNING_MESSAGE);
            return;
        }

        AEServerSocketOptionsDialog dlg = new AEServerSocketOptionsDialog(this,true,aeServerSocket);
        dlg.setVisible(true);
        int ret = dlg.getReturnStatus();
        if ( ret != AEServerSocketOptionsDialog.RET_OK ){
            return;
        }

        // TODO change options on server socket and reopen it - presently need to restart Viewer
    }//GEN-LAST:event_serverSocketOptionsMenuItemActionPerformed

    private void enableFiltersOnStartupCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableFiltersOnStartupCheckBoxMenuItemActionPerformed
        enableFiltersOnStartup = enableFiltersOnStartupCheckBoxMenuItem.isSelected();
        prefs.putBoolean("AEViewer.enableFiltersOnStartup",enableFiltersOnStartup);
    }//GEN-LAST:event_enableFiltersOnStartupCheckBoxMenuItemActionPerformed

    private void dontRenderToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dontRenderToggleButtonActionPerformed
        if ( dontRenderToggleButton.isSelected() ){
            skipPacketsRenderingNumber = 100;
        } else{
            skipPacketsRenderingNumber = 0;
        }
    }//GEN-LAST:event_dontRenderToggleButtonActionPerformed

    void fixSkipPacketsRenderingMenuItems (){
        skipPacketsRenderingCheckBoxMenuItem.setSelected(skipPacketsRenderingNumber > 0);
        skipPacketsRenderingCheckBoxMenuItem.setText("Skip rendering packets (skipping " + skipPacketsRenderingNumber + " packets)");
    }

    private void skipPacketsRenderingCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_skipPacketsRenderingCheckBoxMenuItemActionPerformed
        // come here when user wants to skip rendering except every n packets
        if ( !skipPacketsRenderingCheckBoxMenuItem.isSelected() ){
            skipPacketsRenderingNumber = 0;
            prefs.putInt("AEViewer.skipPacketsRenderingNumber",skipPacketsRenderingNumber);
            fixSkipPacketsRenderingMenuItems();
            return;
        }
        String s = "Number of packets to skip over between rendering (currently " + skipPacketsRenderingNumber + ")";
        boolean gotIt = false;
        while ( !gotIt ){
            String retString = JOptionPane.showInputDialog(this,s,Integer.toString(skipPacketsRenderingNumber));
            if ( retString == null ){
                return;
            } // cancelled
            try{
                skipPacketsRenderingNumber = Integer.parseInt(retString);
                gotIt = true;
            } catch ( NumberFormatException e ){
                log.warning(e.toString());
            }
        }
        prefs.putInt("AEViewer.skipPacketsRenderingNumber",skipPacketsRenderingNumber);
        fixSkipPacketsRenderingMenuItems();
    }//GEN-LAST:event_skipPacketsRenderingCheckBoxMenuItemActionPerformed

    private void customizeDevicesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_customizeDevicesMenuItemActionPerformed
//        log.info("customizing chip classes");
        ClassChooserDialog dlg = new ClassChooserDialog(this,AEChip.class,chipClassNames,null);
        dlg.setVisible(true);
        int ret = dlg.getReturnStatus();
        if ( ret == ClassChooserDialog.RET_OK ){
            chipClassNames = dlg.getList();
            putChipClassPrefs();
            buildDeviceMenu();
        }
    }//GEN-LAST:event_customizeDevicesMenuItemActionPerformed

    private void openMulticastInputMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openMulticastInputMenuItemActionPerformed
        multicastInputEnabled = openMulticastInputMenuItem.isSelected();
        if ( multicastInputEnabled ){
            try{
                aeMulticastInput = new AEMulticastInput();
                aeMulticastInput.start();
                setPlayMode(PlayMode.REMOTE);
            } catch ( IOException e ){
                log.warning(e.getMessage());
                openMulticastInputMenuItem.setSelected(false);
            }
        } else{
            if ( aeMulticastInput != null ){
                aeMulticastInput.close();
            }
            setPlayMode(PlayMode.WAITING);
        }
    }//GEN-LAST:event_openMulticastInputMenuItemActionPerformed

    private void multicastOutputEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_multicastOutputEnabledCheckBoxMenuItemActionPerformed
        multicastOutputEnabled = multicastOutputEnabledCheckBoxMenuItem.isSelected();
        if ( multicastOutputEnabled ){
            aeMulticastOutput = new AEMulticastOutput();
        } else{
            if ( aeMulticastOutput != null ){
                aeMulticastOutput.close();
            }
        }
    }//GEN-LAST:event_multicastOutputEnabledCheckBoxMenuItemActionPerformed

//        private void spreadInputCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
//        if(spreadInputCheckBoxMenuItem.isSelected()){
//            if(spreadInterface==null){
//                spreadInterface=new AESpreadInterface();
//            }
//            try{
//                spreadInterface.connect();
//                spreadInputEnabled=true;
//                setPlayMode(PlayMode.REMOTE);
//            }catch(Exception e){
//                log.warning(e.getMessage());
//                spreadInputCheckBoxMenuItem.setSelected(false);
//                return;
//            }
//        }else{
//            setPlayMode(PlayMode.WAITING);
//            spreadInputEnabled=false;
//        }
//    }
//        private void spreadServerCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
//        if(spreadServerCheckBoxMenuItem.isSelected()){
//            try{
//                spreadInterface=new AESpreadInterface();
//                spreadInterface.connect();
//                spreadOutputEnabled=true;
//            }catch(Exception e){
//                log.warning(e.getMessage());
//                spreadServerCheckBoxMenuItem.setSelected(false);
//                return;
//            }
//        }else{
//            spreadInterface.disconnect();
//            spreadOutputEnabled=false;
//        }
//    }
    private void helpAERCablingUserGuideMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpAERCablingUserGuideMenuItemActionPerformed
        try{
            BrowserLauncher.openURL(HELP_URL_USER_GUIDE_AER_CABLING);
        } catch ( IOException e ){
            contentMenuItem.setText(e.getMessage());
        }
    }//GEN-LAST:event_helpAERCablingUserGuideMenuItemActionPerformed

    private void openSocketInputStreamMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openSocketInputStreamMenuItemActionPerformed
        if ( socketInputEnabled ){
            if ( aeSocket != null ){
                try{
                    aeSocket.close();
                    log.info("closed " + aeSocket);
                } catch ( IOException e ){
                    e.printStackTrace();
                } finally{
                    openSocketInputStreamMenuItem.setText("Open socket input stream");
                    aeSocket = null;
                }
            }
            socketInputEnabled = false;
            setPlayMode(PlayMode.WAITING);
        } else{
            try{
                aeSocket = new AESocket();
                AESocketOkCancelDialog dlg = new AESocketOkCancelDialog(this,true,aeSocket);
                dlg.setVisible(true);
                int ret = dlg.getReturnStatus();
                if ( ret != AESocketOkCancelDialog.RET_OK ){
                    return;
                }
                aeSocket.connect();
                setPlayMode(PlayMode.REMOTE);
                openSocketInputStreamMenuItem.setText("Close socket input stream from " + aeSocket.getHost() + ":" + aeSocket.getPort());
//                reopenSocketInputStreamMenuItem.setEnabled(true);
                log.info("opened socket input stream " + aeSocket);
                socketInputEnabled = true;
            } catch ( Exception e ){
                log.warning(e.toString());
                JOptionPane.showMessageDialog(this,"<html>Couldn't open AESocket input stream: <br>" + e.toString() + "</html>");
            }
        }
//        if(socketInputStream==null){
//            try{
//
////                socketInputStream=new AEUnicastInput();
//                String host=JOptionPane.showInputDialog(this,"Hostname to receive from",socketInputStream.getHost());
//                if(host==null) return;
//                aeSocket=new AESocket(host);
////                socketInputStream.setHost(host);
////                socketInputStream.start();
//                setPlayMode(PlayMode.REMOTE);
//                openSocketInputStreamMenuItem.setText("Close socket input stream");
//            }catch(Exception e){
//                e.printStackTrace();
//            }
//        }else{
//            if(aeSocket!=null){
//                aeSocket
//            }
//            if(socketInputStream!=null){
//                socketInputStream.close();
//                socketInputStream=null;
//            }
//            log.info("set socketInputStream to null");
//            openSocketInputStreamMenuItem.setText("Open socket input stream");
//        }
    }//GEN-LAST:event_openSocketInputStreamMenuItemActionPerformed

    private void logFilteredEventsCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logFilteredEventsCheckBoxMenuItemActionPerformed
        setLogFilteredEventsEnabled(logFilteredEventsCheckBoxMenuItem.isSelected());
    }//GEN-LAST:event_logFilteredEventsCheckBoxMenuItemActionPerformed

    private void sequenceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sequenceMenuItemActionPerformed

        if ( evt.getActionCommand().equals("start") ){
            float oldScale = chipCanvas.getScale();
            AESequencerInterface aemonseq = (AESequencerInterface)chip.getHardwareInterface();
            try{
                if ( aemonseq != null && aemonseq instanceof AEMonitorSequencerInterface ){
                    ( (AEMonitorSequencerInterface)aemonseq ).stopMonitoringSequencing();
                }
            } catch ( HardwareInterfaceException e ){
                e.printStackTrace();
            }

            JFileChooser fileChooser = new JFileChooser();
            ChipDataFilePreview preview = new ChipDataFilePreview(fileChooser,chip); // from book swing hacks
            fileChooser.addPropertyChangeListener(preview);
            fileChooser.setAccessory(preview);

            String lastFilePath = prefs.get("AEViewer.lastFile",""); // get the last folder

            lastFile = new File(lastFilePath);

            DATFileFilter datFileFilter = new DATFileFilter();
            fileChooser.addChoosableFileFilter(datFileFilter);
            fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
//            boolean wasPaused=isPaused();
//        setPaused(true);
            int retValue = fileChooser.showOpenDialog(this);
            if ( retValue == JFileChooser.APPROVE_OPTION ){
                lastFile = fileChooser.getSelectedFile();
                if ( lastFile != null ){
                    recentFiles.addFile(lastFile);
                }
                SwingUtilities.invokeLater(new Runnable(){
                    public void run (){
                        sequenceFile(lastFile);
                    }
                });
            }
            fileChooser = null;
            //     setPaused(false);
            chipCanvas.setScale(oldScale);
        } else if ( evt.getActionCommand().equals("stop") ){
            setPlayMode(PlayMode.LIVE);
            stopSequencing();
        }
    }//GEN-LAST:event_sequenceMenuItemActionPerformed

    private void sequenceFile (File file){
        try{
            setCurrentFile(file);
            AEFileInputStream fileAEInputStream = new AEFileInputStream(file);
            fileAEInputStream.setFile(file);
            fileAEInputStream.setNonMonotonicTimeExceptionsChecked(false); // the code below has to take care about non-monotonic time anyway

            int numberOfEvents = (int)fileAEInputStream.size();

            AEPacketRaw seqPkt = fileAEInputStream.readPacketByNumber(numberOfEvents);

            if ( seqPkt.getNumEvents() < numberOfEvents ){
                int[] ad = new int[ numberOfEvents ];
                int[] ts = new int[ numberOfEvents ];
                int remainingevents = numberOfEvents;
                int ind = 0;
                do{
                    remainingevents = remainingevents - AEFileInputStream.MAX_BUFFER_SIZE_EVENTS;
                    System.arraycopy(seqPkt.getTimestamps(),0,ts,ind * AEFileInputStream.MAX_BUFFER_SIZE_EVENTS,seqPkt.getNumEvents());
                    System.arraycopy(seqPkt.getAddresses(),0,ad,ind * AEFileInputStream.MAX_BUFFER_SIZE_EVENTS,seqPkt.getNumEvents());
                    seqPkt = fileAEInputStream.readPacketByNumber(remainingevents);
                    ind++;

                } while ( remainingevents > AEFileInputStream.MAX_BUFFER_SIZE_EVENTS );

                seqPkt = new AEPacketRaw(ad,ts);
            }
            // calculate interspike intervals
            int[] ts = seqPkt.getTimestamps();
            int[] isi = new int[ seqPkt.getNumEvents() ];

            isi[0] = ts[0];

            for ( int i = 1 ; i < seqPkt.getNumEvents() ; i++ ){
                isi[i] = ts[i] - ts[i - 1];
                if ( isi[i] < 0 ){
                    //  if (!(ts[i-1]>0 && ts[i]<0)) //if it is not an overflow, it is non-monotonic time, so set isi to zero
                    //{
                    log.info("non-monotonic time at event " + i + ", set interspike interval to zero");
                    isi[i] = 0;
                    //}
                }
            }
            seqPkt.setTimestamps(isi);

            AESequencerInterface aemonseq = (AESequencerInterface)chip.getHardwareInterface();

            setPaused(false);

            if ( aemonseq instanceof AEMonitorSequencerInterface ){
                ( (AEMonitorSequencerInterface)aemonseq ).startMonitoringSequencing(seqPkt);
            } else{
                ( (AESequencerInterface)aemonseq ).startSequencing(seqPkt);
            }
            aemonseq.setLoopedSequencingEnabled(true);
            setPlayMode(PlayMode.SEQUENCING);
            sequenceMenuItem.setActionCommand("stop");
            sequenceMenuItem.setText("Stop sequencing data file");

            if ( !playerControlPanel.isVisible() ){
                playerControlPanel.setVisible(true);
            }
            //   playerSlider.setVisible(true);
            playerControls.getPlayerSlider().setEnabled(false);
//            System.gc(); // garbage collect...
        } catch ( Exception e ){
            e.printStackTrace();
        }
    }

    /** Stops sequencing. */
    public  void stopSequencing (){
        try{
            if ( chip != null && chip.getHardwareInterface() != null ){
                ( (AESequencerInterface)chip.getHardwareInterface() ).stopSequencing();
            }

        } catch ( HardwareInterfaceException e ){
            e.printStackTrace();
        }
        sequenceMenuItem.setActionCommand("start");
        sequenceMenuItem.setText("Sequence data file...");
        playerControlPanel.setVisible(false);
        //   playerSlider.setVisible(true);
        playerControls.getPlayerSlider().setEnabled(true);
    }
    Dimension oldSize;
    Point startResizePoint;

    private void cycleDisplayMethodButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cycleDisplayMethodButtonActionPerformed
        chipCanvas.cycleDisplayMethod();
        chip.setPreferredDisplayMethod(chipCanvas.getDisplayMethod().getClass());
    }//GEN-LAST:event_cycleDisplayMethodButtonActionPerformed

    private void unzoomMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unzoomMenuItemActionPerformed
        chipCanvas.unzoom();
    }//GEN-LAST:event_unzoomMenuItemActionPerformed

    private void viewIgnorePolarityCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewIgnorePolarityCheckBoxMenuItemActionPerformed
        chip.getRenderer().setIgnorePolarityEnabled(viewIgnorePolarityCheckBoxMenuItem.isSelected());
    }//GEN-LAST:event_viewIgnorePolarityCheckBoxMenuItemActionPerformed

    private void cypressFX2EEPROMMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cypressFX2EEPROMMenuItemActionPerformed
        new CypressFX2EEPROM().setVisible(true);
    }//GEN-LAST:event_cypressFX2EEPROMMenuItemActionPerformed

    private void loggingSetTimelimitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loggingSetTimelimitMenuItemActionPerformed
        String ans = JOptionPane.showInputDialog(this,"Enter logging time limit in ms (0 for no limit)",loggingTimeLimit);
        try{
            int n = Integer.parseInt(ans);
            loggingTimeLimit = n;
        } catch ( NumberFormatException e ){
            Toolkit.getDefaultToolkit().beep();
        }
    }//GEN-LAST:event_loggingSetTimelimitMenuItemActionPerformed

    private void helpUserGuideMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpUserGuideMenuItemActionPerformed
        try{
            BrowserLauncher.openURL(HELP_URL_USER_GUIDE_USB2_MINI);
        } catch ( IOException e ){
            contentMenuItem.setText(e.getMessage());
        }
    }//GEN-LAST:event_helpUserGuideMenuItemActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        log.info("window closed event, calling stopMe");
        stopMe();
    }//GEN-LAST:event_formWindowClosed

    private void javadocMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_javadocMenuItemActionPerformed
        try{
            BrowserLauncher.openURL(HELP_URL_JAVADOC);
        } catch ( IOException e ){
            JOptionPane.showMessageDialog(this,"<html>" + e.getMessage() + "<br>" + HELP_URL_JAVADOC + " is not available.<br>You may need to build the javadoc </html>","Javadoc not available",JOptionPane.INFORMATION_MESSAGE);
        }
    }//GEN-LAST:event_javadocMenuItemActionPerformed

    private void viewRenderBlankFramesCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewRenderBlankFramesCheckBoxMenuItemActionPerformed
        setRenderBlankFramesEnabled(viewRenderBlankFramesCheckBoxMenuItem.isSelected());
    }//GEN-LAST:event_viewRenderBlankFramesCheckBoxMenuItemActionPerformed

    private void monSeqMissedEventsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_monSeqMissedEventsMenuItemActionPerformed
        if ( aemon instanceof CypressFX2MonitorSequencer ){
            CypressFX2MonitorSequencer fx = (CypressFX2MonitorSequencer)aemon;
            try{
                JOptionPane.showMessageDialog(this,fx + " missed approximately " + fx.getNumMissedEvents() + " events");
            } catch ( Exception e ){
                e.printStackTrace();
                aemon.close();
            }
        }
    }//GEN-LAST:event_monSeqMissedEventsMenuItemActionPerformed
    volatile boolean doSingleStepEnabled = false;

    synchronized public void doSingleStep (){
//        log.info("doSingleStep");
        setDoSingleStepEnabled(true);
    }

    public void setDoSingleStepEnabled (boolean yes){
        doSingleStepEnabled = yes;
    }

    synchronized public boolean isSingleStep (){
//        boolean isSingle=caviarViewer.getSyncPlayer().isSingleStep();
//        return isSingle;
        return doSingleStepEnabled;
    }

    synchronized public void singleStepDone (){
        if ( isSingleStep() ){
            setDoSingleStepEnabled(false);
            setPaused(true);
        }
//        caviarViewer.getSyncPlayer().singleStepDone();
    }

    private void viewSingleStepMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewSingleStepMenuItemActionPerformed
        jaerViewer.getSyncPlayer().doSingleStep();
    }//GEN-LAST:event_viewSingleStepMenuItemActionPerformed

    private void buildMonSeqMenu (){
        monSeqMenu.getPopupMenu().setLightWeightPopupEnabled(false); // canvas is heavyweight so we need this to make menu popup show
        monSeqOperationModeMenu.getPopupMenu().setLightWeightPopupEnabled(false); // canvas is heavyweight so we need this to make menu popup show
        monSeqOperationModeMenu.setText("MonitorSequencer Operation Mode");
        this.monSeqOpMode0.setText("Tick: 1us");
        this.monSeqOpMode1.setText("Tick: 0.2us");
        this.monSeqMissedEventsMenuItem.setText("Get number of missed events");
    }

    private void enableMonSeqMenu (boolean state){
        this.monSeqMenu.setEnabled(state);
        if ( chip.getHardwareInterface() instanceof AEMonitorInterface ){
            this.monSeqOperationModeMenu.setEnabled(state);
            this.monSeqOpMode0.setEnabled(state);
            this.monSeqOpMode1.setEnabled(state);
            this.monSeqMissedEventsMenuItem.setEnabled(state);
            this.enableMissedEventsCheckBox.setEnabled(state);
        }
        this.sequenceMenuItem.setEnabled(state);
    }
    // used to print dt for measuring frequency from playback by using '1' keystrokes
    class Statistics{
        JFrame statFrame;
        JLabel statLabel;
        int lastTime = 0, thisTime;
        EngineeringFormat fmt = new EngineeringFormat();

        {
            fmt.precision = 2;
        }

        void printStats (){
            synchronized ( aePlayer ){
                thisTime = aePlayer.getTime();
                int dt = lastTime - thisTime;
                float dtSec = (float)( (float)dt / 1e6f + Float.MIN_VALUE );
                float freqHz = 1 / dtSec;
//                System.out.println(String.format("dt=%.2g s, freq=%.2g Hz",dtSec,freqHz));
                if ( statFrame == null ){
                    statFrame = new JFrame("Statistics");
                    statLabel = new JLabel();
                    statLabel.setFont(statLabel.getFont().deriveFont(16f));
                    statLabel.setToolTipText("Type \"1\" to update interval statistics");
                    statFrame.getContentPane().setLayout(new BorderLayout());
                    statFrame.getContentPane().add(statLabel,BorderLayout.CENTER);
                    statFrame.pack();
                }
                String s = " dt=" + fmt.format(dtSec) + "s, freq=" + fmt.format(freqHz) + " Hz ";
                log.info(s);
                statLabel.setText(s);
                statLabel.revalidate();
                statFrame.pack();
                if ( !statFrame.isVisible() ){
                    statFrame.setVisible(true);
                }
                requestFocus(); // leave the focus here
                lastTime = thisTime;
            }
        }
    }
    Statistics statistics;

    private void measureTimeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_measureTimeMenuItemActionPerformed
        if ( statistics == null ){
            statistics = new Statistics();
        }
        statistics.printStats();
    }//GEN-LAST:event_measureTimeMenuItemActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        log.info("window closing");
        if ( biasgenFrame != null && !biasgenFrame.isModificationsSaved() ){
            return;
        }
        viewLoop.stopThread();
        cleanup();

        if ( jaerViewer.getViewers().size() == 1 ){
            log.info("window closing event, only 1 viewer, calling System.exit");
//            stopMe(); // TODO seems to deadlock
            System.exit(0);
        } else{
            log.info("window closing event with more than one AEViewer window, calling stopMe");
            if ( filterFrame != null && filterFrame.isVisible() ){
                filterFrame.dispose();  // close this frame if the window is closed
            }

            // TODO should close biasgen window also
            stopMe();
            dispose();
        }
    }//GEN-LAST:event_formWindowClosing

    private void newViewerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newViewerMenuItemActionPerformed
        new AEViewer(jaerViewer).setVisible(true);
    }//GEN-LAST:event_newViewerMenuItemActionPerformed

    private void interfaceMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_interfaceMenuMenuSelected
        buildInterfaceMenu();
    }//GEN-LAST:event_interfaceMenuMenuSelected

    private void interfaceMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interfaceMenuActionPerformed
        buildInterfaceMenu();
    }//GEN-LAST:event_interfaceMenuActionPerformed

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        // handle statistics label font sizing here
//        System.out.println("*****************frame resize");
        double fw = getWidth();
        if ( statisticsLabel == null ){
            return;
        } // not realized yet
        double lw = statisticsLabel.getWidth();

        if ( fw < 200 ){
            fw = 200;
        }
        double r = fw / lw;
        final double mn = .3, mx = 2.3;
        if ( r < mn ){
            r = mn;
        }
        if ( r > mx ){
            r = mx;
        }

        final int minFont = 10, maxFont = 36;
//        System.out.println("frame/label width="+r);
        Font f = statisticsLabel.getFont();
        int size = f.getSize();
        int newsize = (int)Math.floor(size * r);
        if ( newsize < minFont ){
            newsize = minFont;
        }
        if ( newsize > maxFont ){
            newsize = maxFont;
        }
        if ( size == newsize ){
            return;
        }
        Font nf = f.deriveFont((float)newsize);
//        System.out.println("old font="+f);
//        System.out.println("new font="+nf);
        statisticsLabel.setFont(nf);

    }//GEN-LAST:event_formComponentResized

    private void statisticsPanelComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_statisticsPanelComponentResized
//        statisticsPanel.revalidate();
    }//GEN-LAST:event_statisticsPanelComponentResized

    private void saveImageSequenceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveImageSequenceMenuItemActionPerformed
        if ( canvasFileWriter.writingMovieEnabled ){
            canvasFileWriter.stopWritingMovie();
            saveImageSequenceMenuItem.setText("Start writing image sequence");
        } else{
            canvasFileWriter.startWritingMovie();
            saveImageSequenceMenuItem.setText("Stop writing sequence");
        }
    }//GEN-LAST:event_saveImageSequenceMenuItemActionPerformed
    CanvasFileWriter canvasFileWriter = new CanvasFileWriter();

    private void saveImageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveImageMenuItemActionPerformed
        synchronized ( chipCanvas ){
            canvasFileWriter.writeSnapshotImage(); // chipCanvas must be drawn with java (not openGL) for this to work
        }
    }//GEN-LAST:event_saveImageMenuItemActionPerformed

    private void refreshInterfaceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshInterfaceMenuItemActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_refreshInterfaceMenuItemActionPerformed

    private void toggleMarkCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleMarkCheckBoxMenuItemActionPerformed
        try{
            synchronized ( getAePlayer() ){
                if ( toggleMarkCheckBoxMenuItem.isSelected() ){
                    getAePlayer().mark();
////                    Dictionary<Integer,JLabel> dict=new Dictionary<Integer,JLabel>();
//                    Hashtable<Integer,JLabel> markTable = new Hashtable<Integer,JLabel>();
//                    markTable.put(playerSlider.getValue(),new JLabel("^"));
//                    playerSlider.setLabelTable(markTable);
//                    playerSlider.setPaintLabels(true); // TODO move all this to AePlayerAdvancedControlsPanel
                } else{
                    getAePlayer().unmark();
//                    playerSlider.setPaintLabels(false);
                }
            }
        } catch ( IOException e ){
            e.printStackTrace();
        }
    }//GEN-LAST:event_toggleMarkCheckBoxMenuItemActionPerformed

    private void subSampleSizeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subSampleSizeMenuItemActionPerformed
        String ans = JOptionPane.showInputDialog(this,"Enter limit to number of rendered events",renderer.getSubsampleThresholdEventCount());
        try{
            int n = Integer.parseInt(ans);
            renderer.setSubsampleThresholdEventCount(n);
            extractor.setSubsampleThresholdEventCount(n);
        } catch ( NumberFormatException e ){
            Toolkit.getDefaultToolkit().beep();
        }
    }//GEN-LAST:event_subSampleSizeMenuItemActionPerformed

    private void filtersToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filtersToggleButtonActionPerformed
        showFilters(filtersToggleButton.isSelected());
    }//GEN-LAST:event_filtersToggleButtonActionPerformed

    private void biasesToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_biasesToggleButtonActionPerformed
        showBiasgen(biasesToggleButton.isSelected());
    }//GEN-LAST:event_biasesToggleButtonActionPerformed

    private void imagePanelMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_imagePanelMouseWheelMoved
        int rotation = evt.getWheelRotation();
        renderer.setColorScale(renderer.getColorScale() + rotation);
    }//GEN-LAST:event_imagePanelMouseWheelMoved

    private void loggingPlaybackImmediatelyCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loggingPlaybackImmediatelyCheckBoxMenuItemActionPerformed
        setLoggingPlaybackImmediatelyEnabled(!isLoggingPlaybackImmediatelyEnabled());
    }//GEN-LAST:event_loggingPlaybackImmediatelyCheckBoxMenuItemActionPerformed

    private void togglePlaybackDirectionMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_togglePlaybackDirectionMenuItemActionPerformed
        getAePlayer().toggleDirection();
    }//GEN-LAST:event_togglePlaybackDirectionMenuItemActionPerformed

    private void flextimePlaybackEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flextimePlaybackEnabledCheckBoxMenuItemActionPerformed
        if ( jaerViewer == null ){
            return;
        }
        if ( !jaerViewer.isSyncEnabled() || jaerViewer.getViewers().size() == 1 ){
            aePlayer.toggleFlexTime();
        } else{
            JOptionPane.showMessageDialog(this,"Flextime playback doesn't make sense for sychronized viewing");
            flextimePlaybackEnabledCheckBoxMenuItem.setSelected(false);
        }
    }//GEN-LAST:event_flextimePlaybackEnabledCheckBoxMenuItemActionPerformed

    private void rewindPlaybackMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rewindPlaybackMenuItemActionPerformed
        getAePlayer().rewind();
    }//GEN-LAST:event_rewindPlaybackMenuItemActionPerformed

    private void decreasePlaybackSpeedMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decreasePlaybackSpeedMenuItemActionPerformed
        getAePlayer().slowDown();
    }//GEN-LAST:event_decreasePlaybackSpeedMenuItemActionPerformed

    private void increasePlaybackSpeedMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_increasePlaybackSpeedMenuItemActionPerformed
        getAePlayer().speedUp();
    }//GEN-LAST:event_increasePlaybackSpeedMenuItemActionPerformed

    private void autoscaleContrastEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoscaleContrastEnabledCheckBoxMenuItemActionPerformed
        renderer.setAutoscaleEnabled(!renderer.isAutoscaleEnabled());
        ;
    }//GEN-LAST:event_autoscaleContrastEnabledCheckBoxMenuItemActionPerformed

    private void acccumulateImageEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acccumulateImageEnabledCheckBoxMenuItemActionPerformed
        renderer.setAccumulateEnabled(!renderer.isAccumulateEnabled());
    }//GEN-LAST:event_acccumulateImageEnabledCheckBoxMenuItemActionPerformed

    private void zeroTimestampsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zeroTimestampsMenuItemActionPerformed
        if ( jaerViewer != null && jaerViewer.isSyncEnabled() ){
            jaerViewer.zeroTimestamps();
        } else{
            zeroTimestamps();
            log.info("zeroing timestamps only on current AEViewer");
        }
    }//GEN-LAST:event_zeroTimestampsMenuItemActionPerformed

    private void pauseRenderingCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseRenderingCheckBoxMenuItemActionPerformed
        setPaused(!isPaused());
        if ( !isPaused() ){
//            viewLoop.singleStepEnabled=false;
//            System.out.println("pauseRenderingCheckBoxMenuItemActionPerformed: set singleStepEnabled=false");
        }
    }//GEN-LAST:event_pauseRenderingCheckBoxMenuItemActionPerformed

    private void decreaseFrameRateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decreaseFrameRateMenuItemActionPerformed
//            case KeyEvent.VK_LEFT: // slower
        setFrameRate(getFrameRate() / 2);
//                break;
//            case KeyEvent.VK_RIGHT: //faster
//                setFrameRate(getFrameRate()*2);
//                break;

    }//GEN-LAST:event_decreaseFrameRateMenuItemActionPerformed

    private void increaseFrameRateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_increaseFrameRateMenuItemActionPerformed
//            case KeyEvent.VK_LEFT: // slower
//                setFrameRate(getFrameRate()/2);
//                break;
//            case KeyEvent.VK_RIGHT: //faster
        setFrameRate(getFrameRate() * 2);
//                break;

    }//GEN-LAST:event_increaseFrameRateMenuItemActionPerformed

    private void decreaseContrastMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decreaseContrastMenuItemActionPerformed
        renderer.setColorScale(renderer.getColorScale() + 1);
    }//GEN-LAST:event_decreaseContrastMenuItemActionPerformed

    private void increaseContrastMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_increaseContrastMenuItemActionPerformed
        renderer.setColorScale(renderer.getColorScale() - 1);
    }//GEN-LAST:event_increaseContrastMenuItemActionPerformed

    private void cycleColorRenderingMethodMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cycleColorRenderingMethodMenuItemActionPerformed
        renderer.cycleColorMode();
    }//GEN-LAST:event_cycleColorRenderingMethodMenuItemActionPerformed

    private void subsampleEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subsampleEnabledCheckBoxMenuItemActionPerformed
        chip.setSubSamplingEnabled(subsampleEnabledCheckBoxMenuItem.isSelected());
    }//GEN-LAST:event_subsampleEnabledCheckBoxMenuItemActionPerformed

    private void closeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeMenuItemActionPerformed
        stopMe();
    }//GEN-LAST:event_closeMenuItemActionPerformed

    private void viewOpenGLEnabledMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewOpenGLEnabledMenuItemActionPerformed
        synchronized ( chip.getCanvas() ){
            setOpenGLRenderingEnabled(viewOpenGLEnabledMenuItem.isSelected());
        }
    }//GEN-LAST:event_viewOpenGLEnabledMenuItemActionPerformed

    private void viewActiveRenderingEnabledMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewActiveRenderingEnabledMenuItemActionPerformed
        setActiveRenderingEnabled(viewActiveRenderingEnabledMenuItem.isSelected());
    }//GEN-LAST:event_viewActiveRenderingEnabledMenuItemActionPerformed

    void fixDeviceControlMenuItems (){
//        log.info("fixing device control menu");
        int k = controlMenu.getMenuComponentCount();
        if ( aemon == null || ( !( aemon instanceof ReaderBufferControl ) && !aemon.isOpen() ) ){
            for ( int i = 0 ; i < k ; i++ ){
                if ( controlMenu.getMenuComponent(i) instanceof JMenuItem ){
                    ( (JMenuItem)controlMenu.getMenuComponent(i) ).setEnabled(false);
                }
            }
        } else if ( aemon != null && ( aemon instanceof ReaderBufferControl ) && aemon.isOpen() ){
            ReaderBufferControl readerControl = (ReaderBufferControl)aemon;
            try{
                CypressFX2 fx2 = (CypressFX2)aemon;
                PropertyChangeSupport support = fx2.getSupport();
                // propertyChange method in this file deals with these events
                if ( !support.hasListeners("readerStarted") ){
                    support.addPropertyChangeListener("readerStarted",this); // when the reader starts running, we get called back to fix device control menu
                }
            } catch ( ClassCastException e ){
                try{
                    StereoHardwareInterface stereo = (StereoHardwareInterface)aemon;
                    PropertyChangeSupport support = stereo.getSupport();
                    // propertyChange method in this file deals with these events
                    if ( !support.hasListeners("readerStarted") ){
                        support.addPropertyChangeListener("readerStarted",this); // when the reader starts running, we get called back to fix device control menu
                    }
                } catch ( ClassCastException e2 ){
                    log.warning(e2 + ": tried to add " + aemon + " as listener for reader start/stop in device control menu but this hardware interface doesn't support stop/start control");
                }
            }


            if ( readerControl == null ){
                return;
            }
            int n = readerControl.getNumBuffers();
            int f = readerControl.getFifoSize();
            decreaseNumBuffersMenuItem.setText("Decrease num buffers to " + ( n - 1 ));
            increaseNumBuffersMenuItem.setText("Increase num buffers to " + ( n + 1 ));
            decreaseBufferSizeMenuItem.setText("Decrease FIFO size to " + ( f / 2 ));
            increaseBufferSizeMenuItem.setText("Increase FIFO size to " + ( f * 2 ));

            for ( int i = 0 ; i < k ; i++ ){
                if ( controlMenu.getMenuComponent(i) instanceof JMenuItem ){
                    ( (JMenuItem)controlMenu.getMenuComponent(i) ).setEnabled(true);
                }
            }
        }

        cypressFX2EEPROMMenuItem.setEnabled(true); // always set the true to be able to launch utility even if the device is not a retina

        setDefaultFirmwareMenuItem.setEnabled(true);
        if ( aemon != null && ( aemon instanceof HasUpdatableFirmware ) ){
            updateFirmwareMenuItem.setEnabled(true);
        } else{
            updateFirmwareMenuItem.setEnabled(false);
        }
    }

    private void decreaseNumBuffersMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decreaseNumBuffersMenuItemActionPerformed
        if ( aemon != null && aemon instanceof ReaderBufferControl && aemon.isOpen() ){
            ReaderBufferControl reader = (ReaderBufferControl)aemon;
            int n = reader.getNumBuffers() - 1;
            if ( n < 1 ){
                n = 1;
            }

            reader.setNumBuffers(n);
            fixDeviceControlMenuItems();

        }

    }//GEN-LAST:event_decreaseNumBuffersMenuItemActionPerformed

    private void increaseNumBuffersMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_increaseNumBuffersMenuItemActionPerformed
        if ( aemon != null && aemon instanceof ReaderBufferControl && aemon.isOpen() ){
            ReaderBufferControl reader = (ReaderBufferControl)aemon;
            int n = reader.getNumBuffers() + 1;
            reader.setNumBuffers(n);
            fixDeviceControlMenuItems();

        }
    }//GEN-LAST:event_increaseNumBuffersMenuItemActionPerformed

    private void decreaseBufferSizeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decreaseBufferSizeMenuItemActionPerformed
        if ( aemon != null && aemon instanceof ReaderBufferControl && aemon.isOpen() ){
            ReaderBufferControl reader = (ReaderBufferControl)aemon;
            int n = reader.getFifoSize() / 2;
            reader.setFifoSize(n);
            fixDeviceControlMenuItems();

        }
    }//GEN-LAST:event_decreaseBufferSizeMenuItemActionPerformed

    private void increaseBufferSizeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_increaseBufferSizeMenuItemActionPerformed
        if ( aemon != null && aemon instanceof ReaderBufferControl && aemon.isOpen() ){
            ReaderBufferControl reader = (ReaderBufferControl)aemon;
            int n = reader.getFifoSize() * 2;
            reader.setFifoSize(n);
            fixDeviceControlMenuItems();

        }
    }//GEN-LAST:event_increaseBufferSizeMenuItemActionPerformed

    private void viewFiltersMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewFiltersMenuItemActionPerformed
        showFilters(true);
    }//GEN-LAST:event_viewFiltersMenuItemActionPerformed

    private void viewBiasesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewBiasesMenuItemActionPerformed
        showBiasgen(true);
    }//GEN-LAST:event_viewBiasesMenuItemActionPerformed

    //avoid stateChanged events from slider that is set by player
    volatile boolean sliderDontProcess = false;

    /** messages come back here from e.g. programmatic state changes, like a new aePlayer file posiiton.
     * This methods sets the GUI components to a consistent state, using a flag to tell the slider that it has not been set by
     * a user mouse action
     */
    public void propertyChange (PropertyChangeEvent evt){
        if ( evt.getPropertyName().equals("readerStarted") ){ // comes from hardware interface AEReader thread
//            log.info("AEViewer.propertyChange: AEReader started, fixing device control menu");
            // cypress reader started, can set device control for cypress usbio reader thread
            fixDeviceControlMenuItems();
        }else if(evt.getPropertyName().equals("cleared")){
            setStatusMessage(null);
        }
    }

    private void contentMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_contentMenuItemActionPerformed
        try{
            BrowserLauncher.openURL(HELP_URL_USER_GUIDE);
        } catch ( IOException e ){
            contentMenuItem.setText(e.getMessage());
        }
    }//GEN-LAST:event_contentMenuItemActionPerformed

    private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
        new AEViewerAboutDialog(new javax.swing.JFrame(),true).setVisible(true);
    }//GEN-LAST:event_aboutMenuItemActionPerformed

    private void openMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openMenuItemActionPerformed
        /*getAePlayer().*/this.aePlayer.openAEInputFileDialog();
    }//GEN-LAST:event_openMenuItemActionPerformed

    void showFilters (boolean yes){
        if ( yes && !filterFrameBuilt ){
            filterFrameBuilt = true;
            filterFrame = new FilterFrame(chip);
            filterFrame.addWindowListener(new WindowAdapter(){
                @Override
                public void windowClosed (WindowEvent e){
//                    log.info(e.toString());
                    filtersToggleButton.setSelected(false);
                }
            });
        }

        if ( filterFrame != null ){
            filterFrame.setVisible(yes);
        }

        filtersToggleButton.setSelected(yes);
    }

    void showBiasgen (final boolean yes){
        if ( chip == null ){
            if ( yes ){
                log.warning("null chip, can't try to show biasgen");
            } // only show warning if trying to show biasgen for null chip

            return;
        }

        SwingUtilities.invokeLater(new Runnable(){
            public void run (){
                if ( chip.getBiasgen() == null ){ // this chip has no biasgen - but it won't have one until HW interface is opened for it successfully
                    if ( getBiasgenFrame() != null ){
                        getBiasgenFrame().dispose();
                    }
//            biasesToggleButton.setEnabled(false);  // chip don't have biasgen until it has HW interface, which it doesn't at first....

                    return;
                } else{
                    biasesToggleButton.setEnabled(true);
                    viewBiasesMenuItem.setEnabled(true);
                }

                if ( biasgen != chip.getBiasgen() ){ // biasgen changed
                    if ( getBiasgenFrame() != null ){
                        getBiasgenFrame().dispose();
                    }

                    biasgenFrame = new BiasgenFrame(chip);
                    getBiasgenFrame().addWindowListener(new WindowAdapter(){
                        @Override
                        public void windowClosed (WindowEvent e){
//                            log.info(e.toString());
                            biasesToggleButton.setSelected(false);
                        }
                    });
                }

                if ( getBiasgenFrame() != null ){
                    getBiasgenFrame().setVisible(yes);
                }

                biasesToggleButton.setSelected(yes);
                biasgen = chip.getBiasgen();

            }
        });
    }

    synchronized public void toggleLogging (){
        if ( jaerViewer != null && jaerViewer.isSyncEnabled() && jaerViewer.getViewers().size() > 1 ){
            jaerViewer.toggleSynchronizedLogging();
        } else{
            if ( loggingEnabled ){
                stopLogging(true); // confirms filename dialog when flag true
            } else{
                startLogging();
            }

        }
//        if(loggingButton.isSelected()){
//            if(caviarViewer!=null && caviarViewer.isSyncEnabled() ) caviarViewer.startSynchronizedLogging(); else startLogging();
//        }else{
//            if(caviarViewer!=null && caviarViewer.isSyncEnabled()) caviarViewer.stopSynchronizedLogging(); else stopLogging();
//        }
    }

    void fixLoggingControls (){
//        System.out.println("fixing logging controls, loggingEnabled="+loggingEnabled);
        if ( ( playMode != PlayMode.REMOTE ) && ( aemon == null || ( aemon != null && !aemon.isOpen() ) ) && playMode != playMode.PLAYBACK ){
            // we can log from live input or from playing file (e.g. after refiltering it) or we can log network data
            // TODO: not ideal logic here, too confusing
            loggingButton.setEnabled(false);
            loggingMenuItem.setEnabled(false);
            return;

        } else{
            loggingButton.setEnabled(true);
            loggingMenuItem.setEnabled(true);
        }

        if ( !loggingEnabled && playMode == PlayMode.PLAYBACK ){
            loggingButton.setText("Start Re-logging");
            loggingMenuItem.setText("Start re-logging data");
        } else if ( loggingEnabled ){
            loggingButton.setText("Stop logging");
            loggingButton.setSelected(true);
            loggingMenuItem.setText("Stop logging data");
        } else{
            loggingButton.setText("Start logging");
            loggingButton.setSelected(false);
            loggingMenuItem.setText("Start logging data");
        }

    }

    public void openLoggingFolderWindow (){
        String osName = System.getProperty("os.name");
        if ( osName == null ){
            log.warning("no OS name property, cannot open browser");
            return;

        }


        String curDir = System.getProperty("user.dir");
//        log.info("opening folder window for folder "+curDir);
        if ( osName.startsWith("Windows") ){
            try{
                Runtime.getRuntime().exec("explorer.exe " + curDir);
            } catch ( IOException e ){
                log.warning(e.getMessage());
            }

        } else if ( System.getProperty("os.name").indexOf("Linux") != -1 ){
            log.warning("cannot open linux folder browsing window");
        }

    }

    /** Starts logging AE data to a file.
     *
     * @param filename the filename to log to, including all path information. Filenames without path
     * are logged to the startup folder. The default extension of AEDataFile.DATA_FILE_EXTENSION is appended if there is no extension.
     *
     * @return the file that is logged to.
     */
    synchronized public File startLogging (String filename){
        if ( filename == null ){
            log.warning("tried to log to null filename, aborting");
            return null;
        }
        if ( !filename.toLowerCase().endsWith(AEDataFile.DATA_FILE_EXTENSION) ){
            filename = filename + AEDataFile.DATA_FILE_EXTENSION;
            log.info("Appended extension to make filename=" + filename);
        }
        try{
            loggingFile = new File(filename);

            loggingOutputStream = new AEFileOutputStream(new BufferedOutputStream(new FileOutputStream(loggingFile),100000));
            loggingEnabled = true;

            log.info("starting logging to " + loggingFile);
            setCurrentFile(loggingFile);
            loggingEnabled = true;

            fixLoggingControls();

            if ( loggingTimeLimit > 0 ){
                loggingStartTime = System.currentTimeMillis();
            }
//            aemon.resetTimestamps();

        } catch ( FileNotFoundException e ){
            loggingFile = null;
            e.printStackTrace();
        }

        return loggingFile;
    }

    /** Starts logging data to a default data logging file.
     * @return the file that is logged to.
     */
    synchronized public File startLogging (){
//        if(playMode!=PlayMode.LIVE) return null;
        // first reset timestamps to zero time, and for stereo interfaces, to sychronize them
        /* TODO : fix so that timestamps are zeroed before recording really starts */
        //zeroTimestamps();

        String dateString =
                AEDataFile.DATE_FORMAT.format(new Date());
        String className =
                chip.getClass().getSimpleName();
        int suffixNumber = 0;
        boolean succeeded = false;
        String filename;

        do{
            // log files to tmp folder initially, later user will move or delete file on end of logging
            filename = lastLoggingFolder + File.separator + className + "-" + dateString + "-" + suffixNumber + AEDataFile.DATA_FILE_EXTENSION;
            File lf = new File(filename);
            if ( !lf.isFile() ){
                succeeded = true;
            }

        } while ( succeeded == false && suffixNumber++ <= 5 );
        if ( succeeded == false ){
            log.warning("AEViewer.startLogging(): could not open a unigue new file for logging after trying up to " + filename);
            return null;
        }

        File lf = startLogging(filename);
        return lf;

    }

    /** Stops logging and optionally opens file dialog for where to save file.
     * If number of AEViewers is more than one, dialog is also skipped since we may be logging from multiple viewers.
     * @param confirmFilename true to show file dialog to confirm filename, false to skip dialog.
     */
    synchronized public File stopLogging (boolean confirmFilename){
        // the file has already been logged somewhere with a timestamped name, what this method does is
        // to move the already logged file to a possibly different location with a new name, or if cancel is hit,
        // to delete it.
        int retValue = JFileChooser.CANCEL_OPTION;
        if ( loggingEnabled ){
            if ( loggingButton.isSelected() ){
                loggingButton.setSelected(false);
            }

            loggingButton.setText("Start logging");
            loggingMenuItem.setText("Start logging data");
            try{
                log.info("stopped logging at " + AEDataFile.DATE_FORMAT.format(new Date()));
                synchronized ( loggingOutputStream ){
                    loggingEnabled = false;
                    loggingOutputStream.close();
                }
// if jaer viewer is logging synchronized data files, then just save the file where it was logged originally

                if ( confirmFilename && jaerViewer.getNumViewers() == 1 ){
                    JFileChooser chooser = new JFileChooser();
                    chooser.setCurrentDirectory(lastLoggingFolder);
                    chooser.setFileFilter(new DATFileFilter());
                    chooser.setDialogTitle("Save logged data");

                    String fn =
                            loggingFile.getName();
//                System.out.println("fn="+fn);
                    // strip off .dat to make it easier to add comment to filename
                    String base =
                            fn.substring(0,fn.lastIndexOf(AEDataFile.DATA_FILE_EXTENSION));
//                System.out.println("base="+base);
                    // we'll add the extension back later
                    chooser.setSelectedFile(new File(base));
//                chooser.setAccessory(new ResetFileButton(base,chooser));
                    chooser.setDialogType(JFileChooser.SAVE_DIALOG);
                    chooser.setMultiSelectionEnabled(false);
//                Component[] comps=chooser.getComponents();
//                for(Component c:comps){
//                    if(c.getName().equals("buttonPanel")){
//                        ((JPanel)c).add(new ResetFileButton(base,chooser));
//                    }
//                }
                    boolean savedIt = false;
                    do{
                        // clear the text input buffer to prevent multiply typed characters from destroying proposed datetimestamped filename
                        retValue = chooser.showSaveDialog(AEViewer.this);
                        if ( retValue == JFileChooser.APPROVE_OPTION ){
                            File newFile = chooser.getSelectedFile();
                            // make sure filename ends with .dat
                            if ( !newFile.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION) ){
                                newFile = new File(newFile.getCanonicalPath() + AEDataFile.DATA_FILE_EXTENSION);
                            }
// we'll rename the logged data file to the selection

                            boolean renamed = loggingFile.renameTo(newFile);
                            if ( renamed ){
                                // if successful, cool, save persistence
                                savedIt = true;
                                lastLoggingFolder = chooser.getCurrentDirectory();
                                prefs.put("AEViewer.lastLoggingFolder",lastLoggingFolder.getCanonicalPath());
                                recentFiles.addFile(newFile);
                                loggingFile = newFile; // so that we play it back if it was saved and playback immediately is selected
                                log.info("renamed logging file to " + newFile);
                            } else{
                                // confirm overwrite
                                int overwrite = JOptionPane.showConfirmDialog(chooser,"Overwrite file \"" + newFile + "\"?","Overwrite file?",JOptionPane.WARNING_MESSAGE,JOptionPane.OK_CANCEL_OPTION);
                                if ( overwrite == JOptionPane.OK_OPTION ){
                                    // we need to delete the file
                                    boolean deletedOld = newFile.delete();
                                    if ( deletedOld ){
                                        savedIt = loggingFile.renameTo(newFile);
                                        savedIt = true;
                                        log.info("renamed logging file to " + newFile); // TODO something messed up here with confirmed overwrite of logging file
                                        loggingFile = newFile;
                                    } else{
                                        log.warning("couldn't delete logging file " + newFile);
                                    }

                                } else{
                                    chooser.setDialogTitle("Couldn't save file there, try again");
                                }

                            }
                        } else{
                            // user hit cancel, delete logged data
                            boolean deleted = loggingFile.delete();
                            if ( deleted ){
                                log.info("Deleted temporary logging file " + loggingFile);
                            } else{
                                log.warning("Couldn't delete temporary logging file " + loggingFile);
                            }

                            savedIt = true;
                        }

                    } while ( savedIt == false ); // keep trying until user is happy (unless they deleted some crucial data!)
                }

            } catch ( IOException e ){
                e.printStackTrace();
            }

            if ( retValue == JFileChooser.APPROVE_OPTION && isLoggingPlaybackImmediatelyEnabled() ){
                try{
                    getAePlayer().startPlayback(loggingFile);
                } catch ( IOException e ){
                    log.warning(e.toString());
                    e.printStackTrace();
                }

            }
            loggingEnabled = false;
        }

        fixLoggingControls();
        return loggingFile;
    }    // doesn't actually reset the test in the dialog'
    class ResetFileButton extends JButton{
        String fn;

        ResetFileButton (final String fn,final JFileChooser chooser){
            this.fn = fn;
            setText("Reset filename");
            addActionListener(new ActionListener(){
                public void actionPerformed (ActionEvent e){
                    System.out.println("reset file");
                    chooser.setSelectedFile(new File(fn));
                }
            });
        }
    }

    @Override
    public String toString (){
        return getTitle();
    }

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        if ( biasgenFrame != null && !biasgenFrame.isModificationsSaved() ){
            return;
        }

        viewLoop.stopThread();
        cleanup();

        System.exit(0);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void changeAEBufferSizeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changeAEBufferSizeMenuItemActionPerformed
        if ( aemon == null ){
            JOptionPane.showMessageDialog(this,"No hardware interface open, can't set size","Can't set buffer size",JOptionPane.WARNING_MESSAGE);
            return;

        }


        String ans = JOptionPane.showInputDialog(this,"Enter size of render/capture exchange buffer in events",aemon.getAEBufferSize());
        try{
            int n = Integer.parseInt(ans);
            aemon.setAEBufferSize(n);
        } catch ( NumberFormatException e ){
            Toolkit.getDefaultToolkit().beep();
        }
    }//GEN-LAST:event_changeAEBufferSizeMenuItemActionPerformed

    private void openUnicastInputMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openUnicastInputMenuItemActionPerformed
        if ( unicastInputEnabled ){
            if ( unicastInput != null ){
                unicastInput.close();
                log.info("closed " + unicastInput);
                openUnicastInputMenuItem.setText("Open unicast UDP input...");
                unicastInput = null;

            }


            unicastInputEnabled = false;
            setPlayMode(PlayMode.WAITING);
        } else{
            try{
                unicastInput = new AEUnicastInput();
                AEUnicastDialog dlg =
                        new AEUnicastDialog(this,true,unicastInput);
                dlg.setVisible(true);
                int ret = dlg.getReturnStatus();
                if ( ret != AEUnicastDialog.RET_OK ){
                    return;
                }

                setPlayMode(PlayMode.REMOTE);
                openUnicastInputMenuItem.setText("Close unicast input from " + unicastInput.getHost() + ":" + unicastInput.getPort());
                log.info("opened unicast input " + unicastInput);
                unicastInputEnabled = true;

                unicastInput.start();
            } catch ( Exception e ){
                log.warning(e.toString());
                JOptionPane.showMessageDialog(this,"<html>Couldn't open AEUnicastInput input: <br>" + e.toString() + "</html>");
            }

        }
    }//GEN-LAST:event_openUnicastInputMenuItemActionPerformed

    private void unicastOutputEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unicastOutputEnabledCheckBoxMenuItemActionPerformed
        if ( unicastOutputEnabled ){
            if ( unicastOutput != null ){
                unicastOutput.close();
                log.info("closed " + unicastOutput);
                unicastOutput = null;

            }


            unicastOutputEnabled = false;
//            setPlayMode(PlayMode.WAITING); // don't stop live input or file just because we stop output datagrams
        } else{
            try{
                unicastOutput = new AEUnicastOutput();
                AEUnicastDialog dlg =
                        new AEUnicastDialog(this,true,unicastOutput);
                dlg.setVisible(true);
                int ret = dlg.getReturnStatus();
                if ( ret != AEUnicastDialog.RET_OK ){
                    return;
                }

                log.info("opened unicast output " + unicastOutput);
                unicastOutputEnabled = true;

            } catch ( Exception e ){
                log.warning(e.toString());
                JOptionPane.showMessageDialog(this,"<html>Couldn't open AEUnicastOutput: <br>" + e.toString() + "</html>");
            }

        }
    }//GEN-LAST:event_unicastOutputEnabledCheckBoxMenuItemActionPerformed

private void updateFirmwareMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateFirmwareMenuItemActionPerformed
    if ( aemon == null ){
        return;
    }

    if ( !( aemon instanceof HasUpdatableFirmware ) ){
        JOptionPane.showMessageDialog(this,"Device does not have updatable firmware","Firmware update failed",JOptionPane.WARNING_MESSAGE);
        return;

    }



    int DID = aemon.getDID();
    int ret = JOptionPane.showConfirmDialog(this,"Current FX2 firmware device ID (firmware version number)=" + DID + ": Are you sure you want to update the firmware?","Really update?",JOptionPane.YES_NO_OPTION);
    if ( !( ret == JOptionPane.YES_OPTION ) ){
        return;
    }

    try{
        HasUpdatableFirmware d = (HasUpdatableFirmware)aemon;
        d.updateFirmware(); // starts a thread in cypressfx2dvs128hardwareinterface, shows progress
    } catch ( Exception e ){
        log.warning(e.toString());
    } finally{
    }
}//GEN-LAST:event_updateFirmwareMenuItemActionPerformed

private void monSeqOpMode0ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_monSeqOpMode0ActionPerformed
    if ( aemon instanceof CypressFX2MonitorSequencer ){
        CypressFX2MonitorSequencer fx = (CypressFX2MonitorSequencer)aemon;
        try{
            fx.setOperationMode(0);
            JOptionPane.showMessageDialog(this,"Timestamp tick set to " + fx.getOperationMode() + " us.");
        } catch ( Exception e ){
            e.printStackTrace();
            aemon.close();
        }

    }
}//GEN-LAST:event_monSeqOpMode0ActionPerformed

private void monSeqOpMode1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_monSeqOpMode1ActionPerformed
    if ( aemon instanceof CypressFX2MonitorSequencer ){
        CypressFX2MonitorSequencer fx = (CypressFX2MonitorSequencer)aemon;
        try{
            fx.setOperationMode(1);
            JOptionPane.showMessageDialog(this,"Timestamp tick set to " + fx.getOperationMode() + " us. Note that jAER will treat the ticks as 1us anyway.");
        } catch ( Exception e ){
            e.printStackTrace();
            aemon.close();
        }

    }

}//GEN-LAST:event_monSeqOpMode1ActionPerformed

private void enableMissedEventsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableMissedEventsCheckBoxActionPerformed
    if ( aemon instanceof CypressFX2MonitorSequencer ){
        CypressFX2MonitorSequencer fx = (CypressFX2MonitorSequencer)aemon;
        try{
            fx.enableMissedEvents(enableMissedEventsCheckBox.getState());
            // JOptionPane.showMessageDialog(this, "Timestamp tick set to " + fx.getOperationMode() + " us. Note that jAER will treat the ticks as 1us anyway.");
        } catch ( Exception e ){
            e.printStackTrace();
            aemon.close();
        }

    }
}//GEN-LAST:event_enableMissedEventsCheckBoxActionPerformed

private void calibrationStartStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calibrationStartStopActionPerformed
    if ( renderer instanceof Calibratible ){
        ( (Calibratible)renderer ).setCalibrationInProgress(!( (Calibratible)renderer ).isCalibrationInProgress());
        if ( ( (Calibratible)renderer ).isCalibrationInProgress() ){
            calibrationStartStop.setText("Stop Calibration");
        } else{
            calibrationStartStop.setText("Start Calibration");
        }

    }
}//GEN-LAST:event_calibrationStartStopActionPerformed

private void reopenSocketInputStreamMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reopenSocketInputStreamMenuItemActionPerformed
    reopenSocketInputStream();
}//GEN-LAST:event_reopenSocketInputStreamMenuItemActionPerformed

private void setDefaultFirmwareMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setDefaultFirmwareMenuItemActionPerformed
    CypressFX2FirmwareFilennameChooserOkCancelDialog dialog = new CypressFX2FirmwareFilennameChooserOkCancelDialog(this,true);
    dialog.setVisible(true);
    int v = dialog.getReturnStatus();
    if ( v == CypressFX2FirmwareFilennameChooserOkCancelDialog.RET_OK ){
        CypressFX2.setDefaultFirmwareBixFileForBlankDevice(dialog.getLastFile());
        log.info("set default firmware file to " + CypressFX2.getDefaultFirmwareBixFileForBlankDevice());
    }
}//GEN-LAST:event_setDefaultFirmwareMenuItemActionPerformed

private void refreshInterfaceMenuItemComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_refreshInterfaceMenuItemComponentShown
// TODO not used apparently
}//GEN-LAST:event_refreshInterfaceMenuItemComponentShown

private void zoomInMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInMenuItemActionPerformed
    chip.getCanvas().zoomIn();
}//GEN-LAST:event_zoomInMenuItemActionPerformed

private void zoomOutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutMenuItemActionPerformed
    chip.getCanvas().zoomOut();
}//GEN-LAST:event_zoomOutMenuItemActionPerformed

private void zoomCenterMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomCenterMenuItemActionPerformed
    chip.getCanvas().zoomCenter();
}//GEN-LAST:event_zoomCenterMenuItemActionPerformed

private void checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItemActionPerformed
    if ( aePlayer != null ){
        aePlayer.setNonMonotonicTimeExceptionsChecked(checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.isSelected());
        prefs.putBoolean("AEViewer.checkNonMonotonicTimeExceptionsEnabled",checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.isSelected());
    }

    if ( aemon != null && aemon instanceof StereoHardwareInterface ){
        ( (StereoHardwareInterface)aemon ).setIgnoreTimestampNonmonotonicity(checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.isSelected());
    }
}//GEN-LAST:event_checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItemActionPerformed

private void syncEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syncEnabledCheckBoxMenuItemActionPerformed
    log.warning("no effect");
}//GEN-LAST:event_syncEnabledCheckBoxMenuItemActionPerformed

private void showConsoleOutputButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showConsoleOutputButtonActionPerformed
//    log.info("opening logging output window");
//    jaerViewer.globalDataViewer.setVisible(!jaerViewer.globalDataViewer.isVisible());
    loggingHandler.getConsoleWindow().setVisible(!loggingHandler.getConsoleWindow().isVisible());
}//GEN-LAST:event_showConsoleOutputButtonActionPerformed

private void timestampResetBitmaskMenuItemActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timestampResetBitmaskMenuItemActionPerformed
    String ret = (String)JOptionPane.showInputDialog(this,"Enter hex value bitmask for zeroing timestamps","Timestamp reset bitmask value",JOptionPane.QUESTION_MESSAGE,null, null, Integer.toHexString(aeFileInputStreamTimestampResetBitmask) );
    if(ret==null) return;
    try{
        aeFileInputStreamTimestampResetBitmask = Integer.parseInt(ret,16);
        prefs.putInt("AEViewer.aeFileInputStreamTimestampResetBitmask",aeFileInputStreamTimestampResetBitmask);
        log.info("set aeFileInputStreamTimestampResetBitmask=" + HexString.toString(aeFileInputStreamTimestampResetBitmask));
        timestampResetBitmaskMenuItem.setText("Set timestamp reset bitmask... (currently 0x" + Integer.toHexString(aeFileInputStreamTimestampResetBitmask) + ")");
    } catch ( Exception e ){
        log.warning(e.toString());
    }
}//GEN-LAST:event_timestampResetBitmaskMenuItemActionPerformed

    public int getFrameRate (){
        return frameRater.getDesiredFPS();
    }

    public void setFrameRate (int renderDesiredFrameRateHz){
        frameRater.setDesiredFPS(renderDesiredFrameRateHz);
    }

    public boolean isPaused (){
        return jaerViewer.getSyncPlayer().isPaused();
    }

    /** sets paused. If viewing is synchronized, then all viwewers will be paused.
     *@param paused true to pause
     */
    public void setPaused (boolean paused){
        jaerViewer.getSyncPlayer().setPaused(paused);
//        log.info("paused="+paused);
    }

    public boolean isActiveRenderingEnabled (){
        return activeRenderingEnabled;
    }

    public void setActiveRenderingEnabled (boolean activeRenderingEnabled){
        this.activeRenderingEnabled = activeRenderingEnabled;
        prefs.putBoolean("AEViewer.activeRenderingEnabled",activeRenderingEnabled);
    }

    public boolean isOpenGLRenderingEnabled (){
        return openGLRenderingEnabled;
    }

    public void setOpenGLRenderingEnabled (boolean openGLRenderingEnabled){
        this.openGLRenderingEnabled = openGLRenderingEnabled;
        getChip().getCanvas().setOpenGLEnabled(openGLRenderingEnabled);
        prefs.putBoolean("AEViewer.openGLRenderingEnabled",openGLRenderingEnabled);
//        makeCanvas();
    }

// drag and drop data file onto frame to play it
//          Called while a drag operation is ongoing, when the mouse pointer enters the operable part of the drop site for the DropTarget registered with this listener.
    public void dragEnter (DropTargetDragEvent dtde){
        Transferable transferable = dtde.getTransferable();
        try{
            if ( transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ){
                java.util.List<File> files = (java.util.List<File>)transferable.getTransferData(DataFlavor.javaFileListFlavor);
                for ( File f:files ){
                    if ( f.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION) || f.getName().endsWith(AEDataFile.INDEX_FILE_EXTENSION) ){
                        draggedFile = f;
                    } else{
                        draggedFile = null;
                    }

                }
//                System.out.println("AEViewer.dragEnter(): draqged file="+draggedFile);
            }
        } catch ( UnsupportedFlavorException e ){
            e.printStackTrace();
        } catch ( IOException e ){
            e.printStackTrace();
        }

    }

//          Called while a drag operation is ongoing, when the mouse pointer has exited the operable part of the drop site for the DropTarget registered with this listener.
    public void dragExit (DropTargetEvent dte){
        draggedFile = null;
    }
//          Called when a drag operation is ongoing, while the mouse pointer is still over the operable part of the drop site for the DropTarget registered with this listener.

    public void dragOver (DropTargetDragEvent dtde){
    }

    //  Called when the drag operation has terminated with a drop on the operable part of the drop site for the DropTarget registered with this listener.
    public void drop (DropTargetDropEvent dtde){
        if ( draggedFile != null ){
//            log.info("AEViewer.drop(): opening file "+draggedFile);
            try{
                recentFiles.addFile(draggedFile);
                getAePlayer().startPlayback(draggedFile);
            } catch ( IOException e ){
                log.warning(e.toString());
                e.printStackTrace();
            }

        }
    }

//          Called if the user has modified the current drop gesture.
    public void dropActionChanged (DropTargetDragEvent dtde){
    }

    public boolean isLoggingPlaybackImmediatelyEnabled (){
        return loggingPlaybackImmediatelyEnabled;
    }

    public void setLoggingPlaybackImmediatelyEnabled (boolean loggingPlaybackImmediatelyEnabled){
        this.loggingPlaybackImmediatelyEnabled = loggingPlaybackImmediatelyEnabled;
        prefs.putBoolean("AEViewer.loggingPlaybackImmediatelyEnabled",loggingPlaybackImmediatelyEnabled);
    }

    /** @return the chip we are displaying */
    public AEChip getChip (){
        return chip;
    }

    public void setChip (AEChip chip){
        this.chip = chip;
    }

    public boolean isRenderBlankFramesEnabled (){
        return renderBlankFramesEnabled;
    }

    public void setRenderBlankFramesEnabled (boolean renderBlankFramesEnabled){
        this.renderBlankFramesEnabled = renderBlankFramesEnabled;
        prefs.putBoolean("AEViewer.renderBlankFramesEnabled",renderBlankFramesEnabled);
//        log.info("renderBlankFramesEnabled="+renderBlankFramesEnabled);
    }

    public javax.swing.JMenu getFileMenu (){
        return fileMenu;
    }

    /** used in CaviarViewer to control sync'ed logging */
    public javax.swing.JMenuItem getLoggingMenuItem (){
        return loggingMenuItem;
    }

    public void setLoggingMenuItem (javax.swing.JMenuItem loggingMenuItem){
        this.loggingMenuItem = loggingMenuItem;
    }

    /** this toggle button is used in CaviarViewer to assign an action to start and stop logging for (possibly) all viewers */
    public javax.swing.JToggleButton getLoggingButton (){
        return loggingButton;
    }

    public void setLoggingButton (javax.swing.JToggleButton b){
        this.loggingButton = b;
    }

    public JCheckBoxMenuItem getSyncEnabledCheckBoxMenuItem (){
        return syncEnabledCheckBoxMenuItem;
    }

    public void setSyncEnabledCheckBoxMenuItem (javax.swing.JCheckBoxMenuItem syncEnabledCheckBoxMenuItem){
        this.syncEnabledCheckBoxMenuItem = syncEnabledCheckBoxMenuItem;
    }

    /**Returns the proper AbstractAEPlayer: either the local AEPlayer or the delegated-to JAERViewer.SyncPlayer.
     *
     * @return the local player, unless we are part of a synchronized playback gruop.
     */
    public AbstractAEPlayer getAePlayer (){
        if ( jaerViewer == null || !jaerViewer.isSyncEnabled() || jaerViewer.getViewers().size() == 1 ){
            return aePlayer;
        }

        return jaerViewer.getSyncPlayer();
    }

    /** returns the playing mode
     * @return the mode
     */
    public PlayMode getPlayMode (){
        return playMode;
    }

    /** Sets mode, LIVE, PLAYBACK, WAITING, etc,
     * sets window title, and fires property change event
    @param playMode the new play mode
     */
    public void setPlayMode (PlayMode playMode){
        // TODO there can be a race condition where user tries to open file, this sets
        // playMode to PLAYBACK but run() method in ViewLoop sets it back to WAITING or LIVE
        String oldmode = playMode.toString();
        this.playMode = playMode;
//        log.info("set playMode=" + playMode);
        setTitleAccordingToState();

        fixLoggingControls();

        getSupport().firePropertyChange("playmode",oldmode,playMode.toString());
        // won't fire if old and new are the same,
        // e.g. playing a file and then start playing a new one
    }

    public boolean isLogFilteredEventsEnabled (){
        return logFilteredEventsEnabled;
    }

    public void setLogFilteredEventsEnabled (boolean logFilteredEventsEnabled){
//        log.info("logFilteredEventsEnabled="+logFilteredEventsEnabled);
        this.logFilteredEventsEnabled = logFilteredEventsEnabled;
        prefs.putBoolean("AEViewer.logFilteredEventsEnabled",logFilteredEventsEnabled);
        logFilteredEventsCheckBoxMenuItem.setSelected(logFilteredEventsEnabled);
    }

    public JAERViewer getJaerViewer (){
        return jaerViewer;
    }

    public void setJaerViewer (JAERViewer jaerViewer){
        this.jaerViewer = jaerViewer;
    }

    /** AEViewer makes a ServerSocket that accepts incoming connections. A connecting client
    gets served the events being rendered.
    @return the server socket. This holds the client socket.
     */
    public AEServerSocket getAeServerSocket (){
        return aeServerSocket;
    }

    /** If we have opened a socket to a server of events, then this is it
    @return the input socket
     */
    public AESocket getAeSocket (){
        return aeSocket;
    }

    /** gets the RecentFiles handler for use, e.g. in storing sychronized logging index files
    @return refernce to RecentFiles object
     */
    public RecentFiles getRecentFiles (){
        return recentFiles;
    }

    /** AEViewer supports property change events. See the class description for supported events
    @return the support
     */
    public PropertyChangeSupport getSupport (){
        return support;
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutMenuItem;
    private javax.swing.JCheckBoxMenuItem acccumulateImageEnabledCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem autoscaleContrastEnabledCheckBoxMenuItem;
    private javax.swing.JToggleButton biasesToggleButton;
    private javax.swing.JPanel bottomPanel;
    private javax.swing.JPanel buttonsPanel;
    private javax.swing.JMenuItem calibrationStartStop;
    private javax.swing.JMenuItem changeAEBufferSizeMenuItem;
    private javax.swing.JCheckBoxMenuItem checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem;
    private javax.swing.JMenuItem closeMenuItem;
    private javax.swing.JMenuItem contentMenuItem;
    private javax.swing.JMenu controlMenu;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JMenuItem customizeDevicesMenuItem;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JMenuItem cycleColorRenderingMethodMenuItem;
    private javax.swing.JMenuItem cycleDisplayMethodButton;
    private javax.swing.JMenuItem cypressFX2EEPROMMenuItem;
    private javax.swing.JMenuItem dataWindowMenu;
    private javax.swing.JMenuItem decreaseBufferSizeMenuItem;
    private javax.swing.JMenuItem decreaseContrastMenuItem;
    private javax.swing.JMenuItem decreaseFrameRateMenuItem;
    private javax.swing.JMenuItem decreaseNumBuffersMenuItem;
    private javax.swing.JMenuItem decreasePlaybackSpeedMenuItem;
    private javax.swing.JMenu deviceMenu;
    private javax.swing.JSeparator deviceMenuSpparator;
    private javax.swing.JMenu displayMethodMenu;
    private javax.swing.JToggleButton dontRenderToggleButton;
    private javax.swing.JMenu editMenu;
    private javax.swing.JCheckBoxMenuItem enableFiltersOnStartupCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem enableMissedEventsCheckBox;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JSeparator exitSeperator;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu filtersSubMenu;
    private javax.swing.JToggleButton filtersToggleButton;
    private javax.swing.JCheckBoxMenuItem flextimePlaybackEnabledCheckBoxMenuItem;
    private javax.swing.JMenu graphicsSubMenu;
    private javax.swing.JMenuItem helpAERCablingUserGuideMenuItem;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenuItem helpRetinaMenuItem;
    private javax.swing.JMenuItem helpUserGuideMenuItem;
    private javax.swing.JPanel imagePanel;
    private javax.swing.JMenuItem increaseBufferSizeMenuItem;
    private javax.swing.JMenuItem increaseContrastMenuItem;
    private javax.swing.JMenuItem increaseFrameRateMenuItem;
    private javax.swing.JMenuItem increaseNumBuffersMenuItem;
    private javax.swing.JMenuItem increasePlaybackSpeedMenuItem;
    private javax.swing.JMenu interfaceMenu;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator10;
    private javax.swing.JSeparator jSeparator11;
    private javax.swing.JSeparator jSeparator12;
    private javax.swing.JSeparator jSeparator13;
    private javax.swing.JSeparator jSeparator14;
    private javax.swing.JSeparator jSeparator15;
    private javax.swing.JSeparator jSeparator16;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JSeparator jSeparator7;
    private javax.swing.JSeparator jSeparator8;
    private javax.swing.JSeparator jSeparator9;
    private javax.swing.JMenuItem javadocMenuItem;
    private javax.swing.JMenuItem javadocWebMenuItem;
    private javax.swing.JCheckBoxMenuItem logFilteredEventsCheckBoxMenuItem;
    private javax.swing.JToggleButton loggingButton;
    private javax.swing.JMenuItem loggingMenuItem;
    private javax.swing.JCheckBoxMenuItem loggingPlaybackImmediatelyCheckBoxMenuItem;
    private javax.swing.JMenuItem loggingSetTimelimitMenuItem;
    private javax.swing.JMenuItem markInPointMenuItem;
    private javax.swing.JMenuItem markOutPointMenuItem;
    private javax.swing.JMenuItem measureTimeMenuItem;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenu monSeqMenu;
    private javax.swing.JMenuItem monSeqMissedEventsMenuItem;
    private javax.swing.JRadioButtonMenuItem monSeqOpMode0;
    private javax.swing.JRadioButtonMenuItem monSeqOpMode1;
    private javax.swing.ButtonGroup monSeqOpModeButtonGroup;
    private javax.swing.JMenu monSeqOperationModeMenu;
    private javax.swing.JCheckBoxMenuItem multicastOutputEnabledCheckBoxMenuItem;
    private javax.swing.JSeparator networkSeparator;
    private javax.swing.JMenuItem newViewerMenuItem;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JCheckBoxMenuItem openMulticastInputMenuItem;
    private javax.swing.JMenuItem openSocketInputStreamMenuItem;
    private javax.swing.JMenuItem openUnicastInputMenuItem;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JCheckBoxMenuItem pauseRenderingCheckBoxMenuItem;
    private javax.swing.JPanel playerControlPanel;
    private javax.swing.JMenuItem refreshInterfaceMenuItem;
    private javax.swing.JMenu remoteMenu;
    private javax.swing.ButtonGroup renderModeButtonGroup;
    private javax.swing.JMenuItem reopenSocketInputStreamMenuItem;
    private javax.swing.JLabel resizeLabel;
    private javax.swing.JPanel resizePanel;
    private javax.swing.JMenuItem rewindPlaybackMenuItem;
    private javax.swing.JMenuItem saveImageMenuItem;
    private javax.swing.JMenuItem saveImageSequenceMenuItem;
    private javax.swing.JMenuItem sequenceMenuItem;
    private javax.swing.JMenuItem serverSocketOptionsMenuItem;
    private javax.swing.JMenuItem setDefaultFirmwareMenuItem;
    private javax.swing.JButton showConsoleOutputButton;
    private javax.swing.JCheckBoxMenuItem skipPacketsRenderingCheckBoxMenuItem;
    private javax.swing.JPanel statisticsPanel;
    private javax.swing.JTextField statusTextField;
    private javax.swing.JMenuItem subSampleSizeMenuItem;
    private javax.swing.JCheckBoxMenuItem subsampleEnabledCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem syncEnabledCheckBoxMenuItem;
    private javax.swing.JSeparator syncSeperator;
    private javax.swing.JMenuItem timestampResetBitmaskMenuItem;
    private javax.swing.JCheckBoxMenuItem toggleMarkCheckBoxMenuItem;
    private javax.swing.JMenuItem togglePlaybackDirectionMenuItem;
    private javax.swing.JCheckBoxMenuItem unicastOutputEnabledCheckBoxMenuItem;
    private javax.swing.JMenuItem unzoomMenuItem;
    private javax.swing.JMenuItem updateFirmwareMenuItem;
    private javax.swing.JCheckBoxMenuItem viewActiveRenderingEnabledMenuItem;
    private javax.swing.JMenuItem viewBiasesMenuItem;
    private javax.swing.JMenuItem viewFiltersMenuItem;
    private javax.swing.JCheckBoxMenuItem viewIgnorePolarityCheckBoxMenuItem;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JCheckBoxMenuItem viewOpenGLEnabledMenuItem;
    private javax.swing.JCheckBoxMenuItem viewRenderBlankFramesCheckBoxMenuItem;
    private javax.swing.JMenuItem viewSingleStepMenuItem;
    private javax.swing.JMenuItem zeroTimestampsMenuItem;
    private javax.swing.JMenuItem zoomCenterMenuItem;
    private javax.swing.JMenuItem zoomInMenuItem;
    private javax.swing.JMenuItem zoomOutMenuItem;
    // End of variables declaration//GEN-END:variables
}
