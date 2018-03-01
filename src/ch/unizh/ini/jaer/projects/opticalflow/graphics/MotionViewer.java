/*
 * FViewer.java
 *
 * Created on December 24, 2005, 1:58 PM
 */
package ch.unizh.ini.jaer.projects.opticalflow.graphics;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.RenderedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenFrame;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEMulticastInput;
import net.sf.jaer.eventio.AEMulticastOutput;
import net.sf.jaer.eventio.InputDataFileInterface;
import net.sf.jaer.graphics.AEViewerAboutDialog;
import net.sf.jaer.graphics.Chip2DRenderer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipDataFilePreview;
import net.sf.jaer.graphics.DynamicFontSizeJLabel;
import net.sf.jaer.util.DATFileFilter;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.PNGFileFilter;
import net.sf.jaer.util.RecentFiles;
import net.sf.jaer.util.RemoteControl;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.TobiLogger;
import net.sf.jaer.util.TriangleSquareWindowsCornerIcon;
import net.sf.jaer.util.WindowSaver;
//import ch.unizh.ini.caviar.chip.convolution.Conv64NoNegativeEvents;
import ch.unizh.ini.jaer.chip.retina.Tmpdiff128;
import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.MotionData;
import ch.unizh.ini.jaer.projects.opticalflow.io.MotionInputStream;
import ch.unizh.ini.jaer.projects.opticalflow.io.MotionOutputStream;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.MotionChipInterface;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.OpticalFlowHardwareInterfaceFactory;
import net.sf.jaer.eventio.AEInputStream;

/**
 * Shows retina live and allows for controlling view and recording and playing back events.
 * @author  tobi
 */
public class MotionViewer extends javax.swing.JFrame implements PropertyChangeListener, DropTargetListener, RemoteControlled {

    public static String HELP_URL_USER_GUIDE = "http://www.ini.unizh.ch/~tobi/caviar/INI-AE-Biasgen/";
    public static String HELP_URL_JAVADOC;
    public static DynamicFontSizeJLabel numericPanel;
    private MotionOutputStream loggingOutputStream;
    public static final String REMOTE_START_LOGGING = "startlogging";
    public static final String REMOTE_STOP_LOGGING = "stoplogging";
   private RemoteControl remoteControl = null;
   /** Default port number for remote control of this MotionViewer (same as AEViewer).
     *
     */
    public static final int REMOTE_CONTROL_PORT = 8997; // TODO make this the starting port number but find a free one if not available.


    static {
        String curDir = System.getProperty("user.dir");
        HELP_URL_JAVADOC = "file://" + curDir + "/dist/javadoc/index.html";
    }

    private void showInBrowser(String url) {
        if(!Desktop.isDesktopSupported()){
            log.warning("No Desktop support, can't show help from "+url);
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            log.warning("Couldn't show "+url+"; caught "+ex);
        }
    }

    enum PlayMode {

        WAITING, LIVE, PLAYBACK
    }
    volatile private PlayMode playMode = PlayMode.WAITING;
    static Preferences prefs = Preferences.userNodeForPackage(MotionViewer.class);
    static Logger log = Logger.getLogger("MotionViewer");
    FileInputStream fileInputStream;
    BiasgenFrame biasgenFrame = null;
    Biasgen biasgen = null;
    Chip2DRenderer renderer = null;
    public static MotionChipInterface hardware = null;
    public int hardwareInterfaceNum = -1; // references to OpticalFlowHardwareInterfaceFactory.getInterface(n)
    public ViewLoop viewLoop = null;
    RecentFiles recentFiles = null;
    File lastFile = null;
    File lastImageFile = null;
    File currentFile = null;
    FrameRater frameRater = new FrameRater();
    ChipCanvas chipCanvas;
    volatile boolean loggingEnabled = false;
    DateFormat loggingFilenameDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
    File loggingFile=null;
    DropTarget dropTarget;
    File draggedFile;
    private boolean loggingPlaybackImmediatelyEnabled = prefs.getBoolean("MotionViewer.loggingPlaybackImmediatelyEnabled", false);
    private long loggingTimeLimit = 0, loggingStartTime = System.currentTimeMillis();
    private boolean logFilteredEventsEnabled = prefs.getBoolean("MotionViewer.logFilteredEventsEnabled", false);
    DynamicFontSizeJLabel statisticsLabel = new DynamicFontSizeJLabel();
    public Chip2DMotion chip;
    private String aeChipClassName = prefs.get("MotionViewer.aeChipClassName", Tmpdiff128.class.getName());
    Class aeChipClass;
    WindowSaver windowSaver;
    MotionData motionData;
    ToggleLoggingAction toggleLoggingAction = new ToggleLoggingAction();
    private TobiLogger tobiLogger = new TobiLogger("MotionViewer", "Motion data from MDC2d");

    /**
     * construct new instance and then set classname of device to show in it
     *
     */
    public MotionViewer(Chip2DMotion chip) {
        motionData = chip.getEmptyMotionData();

        setName("MotionViewer");
        log.setLevel(Level.INFO);
        initComponents();

        setChip(chip);
        makeCanvas();
        statisticsLabel = new DynamicFontSizeJLabel();
        statisticsLabel.setToolTipText("Time slice/Absolute time, NumEvents/NumFiltered, events/sec, Frame rate acheived/desired, Time expansion X contraction /, delay after frame, color scale");
        statisticsPanel.add(statisticsLabel);

        loggingButton.setAction(toggleLoggingAction);
        loggingMenuItem.setAction(toggleLoggingAction);

        int n = menuBar.getMenuCount();
        for (int i = 0; i < n; i++) {
            JMenu m = menuBar.getMenu(i);
            m.getPopupMenu().setLightWeightPopupEnabled(false);
        }

        String lastFilePath = prefs.get("MotionViewer.lastFile", "");
        lastFile = new File(lastFilePath);

        // recent files tracks recently used files *and* folders. recentFiles adds the anonymous listener
        // built here to open the selected file
        recentFiles = new RecentFiles(prefs, fileMenu, new ActionListener() {

            @Override
			public void actionPerformed(ActionEvent evt) {
                File f = new File(evt.getActionCommand());
                log.info("opening " + evt.getActionCommand());
                try {
                    if ((f != null) && f.isFile()) {
                        getPlayer().startPlayback(f);
                        recentFiles.addFile(f);
                    } else if ((f != null) && f.isDirectory()) {
                        prefs.put("MotionViewer.lastFile", f.getCanonicalPath());
                        getPlayer().openInputFileDialog();
                        recentFiles.addFile(f);
                    }
                } catch (Exception fnf) {
                    fnf.printStackTrace();
//                    exceptionOccurred(fnf,this);
                    recentFiles.removeFile(f);
                }
            }
        });

        // restore window position
        int x= prefs.getInt("Window.left", 0);
        int y= prefs.getInt("Window.top", 0);
        int w= prefs.getInt("Window.width", 1000);
        int h= prefs.getInt("Window.height", 800);
        setLocation(x,y);
        setSize(new Dimension(w,h));

        playerControlPanel.setVisible(false);

        pack();

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
			public void run() {
                if ((hardware != null) && hardware.isOpen()) {
                    hardware.close();
                }
            }
        });

        setFocusable(true);
        requestFocus();
        viewLoop = new ViewLoop();
        viewLoop.start();
        dropTarget = new DropTarget(imagePanel, this);

        fixLoggingControls();

        // init menu items that are checkboxes to correct initial state
        loggingPlaybackImmediatelyCheckBoxMenuItem.setSelected(isLoggingPlaybackImmediatelyEnabled());
        pauseRenderingCheckBoxMenuItem.setSelected(isPaused());
       // add remote control commands
        try {
            remoteControl = new RemoteControl(REMOTE_CONTROL_PORT);
            remoteControl.addCommandListener(this, REMOTE_START_LOGGING + " <filename>", "starts logging raw motion sensor data to a file");
            remoteControl.addCommandListener(this, REMOTE_STOP_LOGGING, "stops logging raw data to a file");
            log.info("created " + remoteControl + " for remote control of some MotionViewer functions");
        } catch (SocketException ex) {
            log.warning(ex.toString());
        }

    }

    protected boolean isWindows() {
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Windows")) {
            return true;
        } else {
            return false;
        }
    }

    /** this sets window title according to actual state */
    public void setTitleAccordingToState() {
        String ts = null;
        switch (getPlayMode()) {
            case LIVE:
                ts = " LIVE";
                break;
            case PLAYBACK:
                ts = currentFile.getName() + " PLAYING";
                break;
            case WAITING:
                ts = "MotionViewer - WAITING";
                break;
        }
        setTitle(ts);
    }

    synchronized void makeCanvas() {
        if (chipCanvas != null) {
            imagePanel.remove(chipCanvas.getCanvas());
        }
        imagePanel.setLayout(new BorderLayout());
        chipCanvas = chip.getCanvas();
        imagePanel.add(chipCanvas.getCanvas(), BorderLayout.CENTER);
        chipCanvas.getCanvas().invalidate();
        // find display menu reference and fill it with display menu for this canvas
        viewMenu.remove(displayMethodMenu);
        viewMenu.add(chipCanvas.getDisplayMethodMenu());
        displayMethodMenu = chipCanvas.getDisplayMethodMenu();

        // add the panel below the chip for controlling display of the chip (gain and offset values for rendered photoreceptor and motion vectors)
        JPanel cp = new OpticalFlowDisplayControlPanel((OpticalFlowDisplayMethod) chip.getCanvas().getDisplayMethod(), chip, this);
        imagePanel.add(cp, BorderLayout.SOUTH);

        viewMenu.invalidate();

        validate();
        pack();
        // causes a lot of flashing ... Toolkit.getDefaultToolkit().setDynamicLayout(true); // dynamic resizing  -- see if this bombs!
    }

    void setCurrentFile(File f) {
        currentFile = new File(f.getPath());
        lastFile = currentFile;
        prefs.put("MotionViewer.lastFile", lastFile.toString());
//        System.out.println("put MotionViewer.lastFile="+lastFile);
        setTitleAccordingToState();
    }

    File getCurrentFile() {
        return currentFile;
    }

    /** writes frames and frame sequences for video making using, e.g. adobe premiere */
    protected class CanvasFileWriter {
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

        String getFilename() {
            return sequenceName + String.format("%04d.png", frameNumber);
        }

        synchronized void startWritingMovie() {
//            if(isOpenGLRenderingEnabled()){
//                JOptionPane.showMessageDialog(MotionViewer.this,"Disable OpenGL graphics from the View menu first");
//                return;
//            }
            sequenceName = JOptionPane.showInputDialog("Sequence name (this folder will be created)?");
            if ((sequenceName == null) || sequenceName.equals("")) {
                log.info("canceled image sequence");
                return;
            }
            log.info("creating directory " + sequenceName);
            sequenceDir = new File(sequenceName);
            if (sequenceDir.exists()) {
                JOptionPane.showMessageDialog(MotionViewer.this, sequenceName + " already exists");
                return;
            }
            boolean madeit = sequenceDir.mkdir();
            if (!madeit) {
                JOptionPane.showMessageDialog(MotionViewer.this, "couldn't create directory " + sequenceName);
                return;
            }
            frameNumber = 0;
            writingMovieEnabled = true;
        }

        synchronized void stopWritingMovie() {
            writingMovieEnabled = false;
        }

        synchronized void writeMovieFrame() {
            try {
                Container container = getContentPane();
                canvas = chip.getCanvas().getCanvas();
                Rectangle r = canvas.getBounds();
                Image image = canvas.createImage(r.width, r.height);
                Graphics g = image.getGraphics();
                synchronized (container) {
                    container.paintComponents(g);
                    if (chip.getCanvas().getImageOpenGL() != null) {
                        ImageIO.write(chip.getCanvas().getImageOpenGL(), "png", new File(sequenceDir, getFilename()));
                    }
                }
                frameNumber++;
            } catch (IOException ioe) {
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
        synchronized void writeSnapshotImage() {
            boolean wasPaused = isPaused();
            setPaused(true);
            JFileChooser fileChooser = new JFileChooser();
            String lastFilePath = prefs.get("MotionViewer.lastFile", ""); // get the last folder
            lastFile = new File(lastFilePath);
//            fileChooser.setFileFilter(datFileFilter);
            PNGFileFilter indexFileFilter = new PNGFileFilter();
            fileChooser.addChoosableFileFilter(indexFileFilter);
            fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
            if (lastImageFile == null) {
                lastImageFile = new File("snapshot.png");
            }
            fileChooser.setSelectedFile(lastImageFile);
            int retValue = fileChooser.showOpenDialog(MotionViewer.this);
            if (retValue == JFileChooser.APPROVE_OPTION) {
                lastImageFile = fileChooser.getSelectedFile();
                String suffix = "";
                if (!lastImageFile.getName().endsWith(".png")) {
                    suffix = ".png";
                }
                try {
//                    if(!isOpenGLRenderingEnabled()){
                    Container container = getContentPane();
                    Rectangle r = container.getBounds();
                    Image image = container.createImage(r.width, r.height);
                    Graphics g = image.getGraphics();
                    synchronized (container) {
                        container.paintComponents(g);
                        g.translate(0, statisticsPanel.getHeight());
                        // TODO: convert this to use OpenGL properly.
           //             chip.getCanvas().paint(g);
//                    ImageIO.write((RenderedImage)imageOpenGL, "png", new File(snapshotName+snapshotNumber+".png"));
                        log.info("writing image to file");
                        ImageIO.write((RenderedImage) image, "png", new File(lastImageFile.getPath() + suffix));
                    }
//                    }else{ // open gl canvas
//                    }
                    snapshotNumber++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            setPaused(wasPaused);
        }
    }

    void fixBiasgenControls() {
        biasesToggleButton.setEnabled(true);
        biasesToggleButton.setVisible(true);
        viewBiasesMenuItem.setEnabled(true);

//        if(hardware==null || (hardware!=null && !hardware.isOpen())){
//            biasesToggleButton.setEnabled(false);
//            biasesToggleButton.setVisible(false);
//            viewBiasesMenuItem.setEnabled(false);
//            return;
//        }else if(hardware instanceof BiasgenHardwareInterface){
//            log.info("enabling biasgen menu items");
//            biasesToggleButton.setEnabled(true);
//            biasesToggleButton.setVisible(true);
//            viewBiasesMenuItem.setEnabled(true);
//        }
//        if(biasgenFrame!=null) {
//            boolean vis=biasgenFrame.isVisible();
//            biasesToggleButton.setSelected(vis);
//        }
    }

    public boolean hardwareIsOpen() {
        return (hardware != null) && hardware.isOpen();
    }

    void closeHardware() {
        if (!hardwareIsOpen()) {
            log.info("hardware already closed.");
            return;
        }
        hardware.close();
        hardware = null;
    }

    // opens the AE interface and handles stereo mode if two identical AERetina interfaces
    void openHardware() {
        if ((hardware != null) && hardware.isOpen()) {
            playMode = PlayMode.LIVE; // in case (like StereoHardwareInterface) where device can be open but not by MotionViewer
            return;
        }
        if (hardwareInterfaceNum < 0) {
            log.warning("cannot open hardware because hardwareInterfaceNum not set (via OpticalFlowDisplayControlPanel");
            return;
        }
        try {
            hardware = (MotionChipInterface) OpticalFlowHardwareInterfaceFactory.instance().getInterface(hardwareInterfaceNum);
            hardware.setChip(chip);
            chip.setHardwareInterface(hardware);
            hardware.open();



            if (hardware == null) {
                fixLoggingControls();
                fixBiasgenControls();
                return;
            }

            fixLoggingControls();
            fixBiasgenControls();
            // note it is important that this openHardware succeeed BEFORE hardware is assigned to biasgen, which immeiately tries to openHardware and download biases, creating a storm of complaints if not sucessful!

//            if(hardware instanceof BiasgenHardwareInterface){
//                chip.getBiasgen().sendConfiguration(chip.getBiasgen());
//                chip.setHardwareInterface(hardware); // if we do this, events do not start coming again after reconnect of device
//                biasgen=chip.getBiasgen();
//                if(biasgenFrame==null) {
//                    biasgenFrame=new BiasgenFrame(biasgen);  // should check if exists...
//                }
//            }

              setPlayMode(PlayMode.LIVE);
          SwingUtilities.invokeLater(new Runnable(){

                @Override
                public void run() {
                     setPlaybackControlsEnabledState(true);
                    setTitleAccordingToState();
                 }

            });

        } catch (Exception e) {
            log.warning(e.getMessage());
            if (hardware != null) {
                hardware.close();
            }
            setPlaybackControlsEnabledState(false);
            fixLoggingControls();
            fixBiasgenControls();
            setPlayMode(PlayMode.WAITING);
        }
    }

    void setPlaybackControlsEnabledState(boolean yes) {
        loggingButton.setEnabled(yes);
        biasesToggleButton.setEnabled(yes);
    }
    int renderCount = 0;
    int numEvents;
    AEPacketRaw aeRaw;
    boolean skipRender = false;
    boolean overrunOccurred = false;
    public Player player = new Player();
    int noEventCounter = 0;

    /** this class handles file input of AEs to control the number of events/sample or period of time in the sample, etc.
     *It handles the file input stream, opening a dialog box, etc.
     *It also handles synchronization of different viewers as follows:
     *<p>
     * If the viwer is not synchronized, then all calls from the GUI are passed directly to this instance of AEPlayer. Thus local control always happens.
     *<p>
     * If the viewer is sychronized, then all GUI calls pass instead to the CaviarViewer instance that contains (or started) this viewer. Then the CaviarViewer AEPlayer
     *calls all the viewers to take the player action (e.g. rewind, go to next slice, change direction).
     *<p>
     *Thus whichever controls the user uses to control playback, the viewers are all sychronized properly without recursively. The "master" is indicated by the GUI action,
     *which routes the request either to this instance's AEPlayer or to the CaviarViewer AEPlayer.
     */
    public class Player implements InputDataFileInterface {

        boolean fileInputEnabled = false;
        FileInputStream fileInputStream = null;
        MotionInputStream motionInputStream = null;
        JFileChooser fileChooser;
        long firstTimeMs = 0;

        public boolean isChoosingFile() {
            return ((fileChooser != null) && fileChooser.isVisible());
        }

        /** called when user asks to open data file file dialog */
        public void openInputFileDialog() {
//        try{Thread.currentThread().sleep(200);}catch(InterruptedException e){}
            float oldScale = chipCanvas.getScale();
            fileChooser = new JFileChooser();
//            new TypeAheadSelector(fileChooser);
            //com.sun.java.plaf.windows.WindowsFileChooserUI;
//            fileChooser.addKeyListener(new KeyAdapter() {
//                public void keyTyped(KeyEvent e){
//                    System.out.println("keycode="+e.getKeyCode());
//                }
//            });
//            System.out.println("fileChooser.getUIClassID()="+fileChooser.getUIClassID());
//            KeyListener[] keyListeners=fileChooser.getKeyListeners();
//            ChipDataFilePreview preview=new ChipDataFilePreview(fileChooser,chip); // from book swing hacks
//            new FileDeleter(fileChooser,preview);
//            fileChooser.addPropertyChangeListener(preview);
//            fileChooser.setAccessory(preview);
            String lastFilePath = prefs.get("MotionViewer.lastFile", ""); // get the last folder
            lastFile = new File(lastFilePath);
//            fileChooser.setFileFilter(datFileFilter);
            DATFileFilter datFileFilter = new DATFileFilter();
            fileChooser.addChoosableFileFilter(datFileFilter);
            fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
//            boolean wasPaused=isPaused();
            setPaused(true);
            int retValue = fileChooser.showOpenDialog(MotionViewer.this);
            if (retValue == JFileChooser.APPROVE_OPTION) {
                try {
                    lastFile = fileChooser.getSelectedFile();
                    if (lastFile != null) {
                        recentFiles.addFile(lastFile);
                    }
                    startPlayback(lastFile);
                } catch (FileNotFoundException fnf) {
                    fnf.printStackTrace();
//                exceptionOccurred(fnf,this);
                }
            }
            fileChooser = null;
//            chipCanvas.setScale(oldScale); // restore persistent scale so that we don't get tiny size on next startup
            setPaused(false);
        }

        public class FileDeleter extends KeyAdapter implements PropertyChangeListener {

            private JFileChooser chooser;
            private ChipDataFilePreview preview;
            File file = null;

            /** adds a keyreleased listener on the JFileChooser FilePane inner classes so that user can use Delete key to delete the file
             * that is presently being shown in the preview window
             * @param chooser the chooser
             * @param preview the data file preview
             */
            public FileDeleter(JFileChooser chooser, ChipDataFilePreview preview) {
                this.chooser = chooser;
                this.preview = preview;
                chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY, this);
                Component comp = addDeleteListener(chooser);
            }

            /** is called when the file selection is changed. Bound to the SELECTED_FILE_CHANGED_PROPERTY. */
            @Override
			public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getNewValue() instanceof File) {
                    file = (File) evt.getNewValue();
                } else {
                    file = null;
                }
                log.info("**** new file=" + file);
            }

            private Component addDeleteListener(Component comp) {
//            System.out.println("");
//            System.out.println("comp="+comp);
//            if (comp.getClass() == sun.swing.FilePane.class) return comp;

                if (comp instanceof Container) {
//                System.out.println(comp+"\n");
//                comp.addMouseListener(new MouseAdapter(){
//                    public void mouseEntered(MouseEvent e){
//                        System.out.println("mouse entered: "+e);
//                    }
//                });
//                    if(comp.getClass().getEnclosingClass()==sun.swing.FilePane.class){
//                        System.out.println("******adding keyListener to "+comp);
                    comp.addKeyListener(new KeyAdapter() {

                        @Override
						public void keyReleased(KeyEvent e) {
                            if (e.getKeyCode() == KeyEvent.VK_DELETE) {
//                                    System.out.println("delete key typed from "+e.getSource());
                                deleteFile();
                            }
                        }
                    });
//                    }
                    Component[] components = ((Container) comp).getComponents();
                    for (Component component2 : components) {
                        Component child = addDeleteListener(component2);
                        if (child != null) {
                            return child;
                        }
                    }
                }
                return null;
            }

            void deleteFile() {
                if (file == null) {
                    return;
                }
                log.info("trying to delete file " + file);
                preview.deleteCurrentFile();
            }
        }

        synchronized public void startPlayback(File file) throws FileNotFoundException {
            fileInputStream = new FileInputStream(file);
            setCurrentFile(file);
            try {
                motionInputStream = new MotionInputStream(fileInputStream, chip);
                MotionData d = motionInputStream.readData(motionData);
                firstTimeMs = d.getTimeCapturedMs();
                motionInputStream.rewind();
                motionInputStream.getSupport().addPropertyChangeListener(MotionViewer.this);
                closeMenuItem.setEnabled(true);
                increasePlaybackSpeedMenuItem.setEnabled(true);
                decreasePlaybackSpeedMenuItem.setEnabled(true);
                rewindPlaybackMenuItem.setEnabled(true);
                togglePlaybackDirectionMenuItem.setEnabled(true);
                if (!playerControlPanel.isVisible()) {
                    playerControlPanel.setVisible(true);
                }
                setPlayMode(PlayMode.PLAYBACK);
                setTitleAccordingToState();
                fixLoggingControls();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /** stops playback.
         *If not in PLAYBACK mode, then just returns.
         *If playing  back, could be waiting during sleep or during CyclicBarrier.await call in CaviarViewer. In case this is the case, we send
         *an interrupt to the the ViewLoop thread to stop this waiting.
         */
        public void stopPlayback() {


            if (getPlayMode() != PlayMode.PLAYBACK) {
                return;
            }
            setPlayMode(PlayMode.WAITING);
            playerControlPanel.setVisible(false);
            increasePlaybackSpeedMenuItem.setEnabled(false);
            decreasePlaybackSpeedMenuItem.setEnabled(false);
            rewindPlaybackMenuItem.setEnabled(false);
            togglePlaybackDirectionMenuItem.setEnabled(false);

            try {
                if (motionInputStream != null) {
                    motionInputStream.close();
                    motionInputStream = null;
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                    fileInputStream = null;
                }
            } catch (IOException ignore) {
                ignore.printStackTrace();
            }
            setTitleAccordingToState();
        }

        @Override
		public void rewind() {
            if (fileInputStream == null) {
                return;
            }
//            System.out.println(Thread.currentThread()+" MotionViewer.AEPlayer.rewind() called, rewinding "+motionInputStream);
            try {
                motionInputStream.rewind();
            } catch (Exception e) {
                System.err.println("rewind exception: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public void pause() {
            MotionViewer.this.setPaused(true);
        }

        public void resume() {
            MotionViewer.this.setPaused(false);
        }

        /**
         * sets the MotionViewer paused flag
         */
        public void setPaused(boolean yes) {
            MotionViewer.this.setPaused(yes);
        }

        /**
         * gets the MotionViewer paused flag
         */
        public boolean isPaused() {
            return MotionViewer.this.isPaused();
        }

        public MotionData getNextData() {
            MotionData data = null;
            try {
                data = motionInputStream.readData(motionData);
                return data;
            } catch (EOFException e) {
                try {
                    Thread.currentThread();
					Thread.sleep(200);
                } catch (InterruptedException ignore) {
                }
                rewind();
            } catch (IOException io) {
//                io.printStackTrace();
                rewind();
                return chip.getEmptyMotionData();
            } catch (NullPointerException np) {
                np.printStackTrace();
                rewind();
            }
            return data;
        }

        public void toggleDirection() {
        }

        public void speedUp() {
        }

        public void slowDown() {
        }

        void toggleFlexTime() {
        }

        public boolean isPlayingForwards() {
            return true;
        }

        @Override
		public float getFractionalPosition() {
            if (motionInputStream == null) {
                log.warning("MotionViewer.AEPlayer.getFractionalPosition: null motionInputStream, returning 0");
                return 0;
            }
            return motionInputStream.getFractionalPosition();
        }

        public void mark() throws IOException {
            motionInputStream.setMarkIn();
        }

        @Override
		public long setMarkIn() {
            return motionInputStream.setMarkIn();
        }

        @Override
		public long setMarkOut() {
            return motionInputStream.setMarkOut();
        }

        @Override
		public long getMarkInPosition() {
            return motionInputStream.getMarkInPosition();
        }

        @Override
		public long getMarkOutPosition() {
            return motionInputStream.getMarkOutPosition();
        }

        @Override
		public boolean isMarkInSet() {
            return motionInputStream.isMarkInSet();
        }

        @Override
		public boolean isMarkOutSet() {
            return motionInputStream.isMarkOutSet();
        }
                
        @Override
        public void setRepeat(boolean repeat) {
        }

        @Override
        public boolean isRepeat() {
            return true;
        }

        @Override
		public long position() {
            return motionInputStream.position();
        }

        @Override
		public void position(long event) {
            motionInputStream.position(event);
        }

        @Override
		public long size() {
            return motionInputStream.size();
        }

        @Override
		public void clearMarks() {
            motionInputStream.clearMarks();
        }

        @Override
		public void setFractionalPosition(float frac) {
            motionInputStream.setFractionalPosition(frac);
        }

        public void setTime(int time) {
//            System.out.println(this+".setTime("+time+")");
            if (motionInputStream != null) {
//                motionInputStream.setCurrentStartTimestamp(time); // TODO
            } else {
                log.warning("null AEInputStream");
            }
        }

        public int getTime() {
            if (motionInputStream == null) {
                return 0;
            }
            return 0; // TODO
//            return motionInputStream.getMostRecentTimestamp();
        }

        public MotionInputStream getMotionInputStream() {
            return motionInputStream;
        }
    }

    /** This thread acquires events and renders them to the RetinaCanvas for active rendering. The other components render themselves
     * on the usual Swing rendering thread.
     */
    class ViewLoop extends Thread {

        Graphics2D g = null;
//        volatile boolean rerenderOtherComponents=false;
//        volatile boolean renderImageEnabled=true;
        volatile boolean singleStepEnabled = false, doSingleStep = false;
        int numRawEvents, numFilteredEvents;
        long timeStarted = System.currentTimeMillis();

        public ViewLoop() {
            super();
            setName("MotionViewer.ViewLoop");
        }

        //asks the canvas to paint itself
        // this in turn calls the display(GLAutoDrawable) method of the canvas, which calls the
        // diplayMethod of the chip, which knows now to draw the data
        void renderFrame() {
            if (getPlayer().isChoosingFile()) {
                return; // don't render while filechooser is active
            }
            if (canvasFileWriter.writingMovieEnabled) {
                chipCanvas.grabNextImage();
            }
            chipCanvas.paintFrame(); // actively paint frame now, either with OpenGL or Java2D, depending on switch

            if (canvasFileWriter.writingMovieEnabled) {
                canvasFileWriter.writeMovieFrame();
            }
        } // renderFrame
        EngineeringFormat engFmt = new EngineeringFormat();
        long beforeTime = 0, afterTime;
        int lastts = 0;
        volatile boolean stop = false;
//        EventProcessingPerformanceMeter perf=new EventProcessingPerformanceMeter(new BackgroundActivityFilter(getChip()));
        final String waitString = "Waiting for device";
        int waitCounter = 0;

        /** the main loop - this is the 'game loop' of the program */
        @Override
		synchronized public void run() {
            while ((stop == false) && !isInterrupted()) {

                // now get the data to be displayed
                if (!isPaused() || isSingleStep()) {
//                    if(isSingleStep()){
//                        log.info("getting data for single step");
//                    }
                    // if !paused we always get data. below, if singleStepEnabled, we set paused after getting data.
                    // when the user unpauses via menu, we disable singleStepEnabled
                    // another flag, doSingleStep, tells loop to do a single data acquisition and then pause again
                    // in this branch, get new data to show
                    frameRater.takeBefore();
                    switch (getPlayMode()) {

                        case LIVE:
                            if ((hardware == null) || !hardware.isOpen()) {
                                setPlayMode(PlayMode.WAITING);
                                try {
                                    Thread.currentThread();
									Thread.sleep(300);
                                } catch (InterruptedException e) {
                                    log.warning("LIVE openAEMonitor sleep interrupted");
                                }
                                continue;
                            }
                            try {
                                motionData = hardware.getData(); // exchanges data with hardware interface, returns the new data buffer
                                /*
                                 * this should be done in the hardware interface class -- andstein
                                try {
                                    motionData.collectMotionInfo();
                                } catch (Exception e) {
                                    ;
                                }
                                */
                            } catch (java.util.concurrent.TimeoutException to) {
                                log.warning(to.getMessage());
                                hardware.close();
                                setPlayMode(PlayMode.WAITING);
                                continue;
                            }
                            chip.setLastData(motionData); // for use by rendering methods
                            if (loggingEnabled) {
                                synchronized (loggingOutputStream) {
                                    try {
                                        loggingOutputStream.writeData(motionData);
                                        tobiLogger.log(String.format("%f %s %f", motionData.getGlobalX(), "\t", motionData.getGlobalY()));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        loggingEnabled = false;
                                        try {
                                            loggingOutputStream.close();
                                        } catch (IOException e2) {
                                            e2.printStackTrace();
                                        }
                                    }
                                }
                                if (loggingTimeLimit > 0) {
                                    if ((System.currentTimeMillis() - loggingStartTime) > loggingTimeLimit) {
                                        log.info("logging time limit reached, stopping logging");
                                        try {
                                            SwingUtilities.invokeAndWait(new Runnable() {

                                                @Override
												public void run() {
                                                    stopLogging(); // must run this in AWT thread because it messes with file menu
                                                }
                                            });
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                            break;
                        case PLAYBACK:
                            motionData = getPlayer().getNextData();
                            chip.setLastData(motionData);
                            break;
                        case WAITING:
//                            chip.setLastData(new MotionData());
//                            renderFrame(); // debug
                            openHardware();
                            if ((hardware == null) || !hardware.isOpen()) {
                                StringBuffer s = new StringBuffer(waitString);
                                for (int i = 0; i < waitCounter; i++) {
                                    s.append('.');
                                }
                                if (waitCounter++ == 3) {
                                    waitCounter = 0;
                                }
                                statisticsLabel.setText(s.toString());
                                try {
                                    Thread.currentThread();
									Thread.sleep(300);
                                } catch (InterruptedException e) {
                                    log.warning("WAITING interrupted");
                                }
                                continue;
                            }
                    } // playMode switch to get data

                    singleStepDone();
                } // getting data


                if (!isInterrupted()) {
                    // get data from device
                    renderFrame();
//                                motionData=hardware.getData();
                }
                makeStatisticsLabel();

                frameRater.takeAfter();
                renderCount++;

                fpsDelay();
            }
            log.warning("MotionViewer.run(): stop=" + stop + " isInterrupted=" + isInterrupted());
            if (hardware != null) {
                hardware.close();
            }

            if (windowSaver != null) {
                try {
                    windowSaver.saveSettings();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            chipCanvas.getCanvas().setVisible(false);
            remove(chipCanvas.getCanvas());
            dispose();
            System.exit(0);
        } // viewLoop.run()

        void fpsDelay() {
            if (!isPaused()) {
                frameRater.delayForDesiredFPS();
            } else {
                synchronized (this) {
                    try {
                        wait(100);
                    } catch (java.lang.InterruptedException e) {
                        log.warning("viewLoop wait() interrupted: " + e.getMessage() + " cause is " + e.getCause());
                    }
                }
            }
        }

        private void makeStatisticsLabel() {
            StringBuilder sb = new StringBuilder();
            if (motionData != null) {
                sb.append(String.format("Seq# %4d, ", motionData.getSequenceNumber()));
            } else {
                sb.append(String.format("Frame %4d, ", renderCount));
            }
            sb.append(String.format("%5.0f/%-5d", frameRater.getAverageFPS(), frameRater.getDesiredFPS()));
            sb.append(String.format(", FS=%d", chip.getRenderer().getColorScale()));
            setStatisticsLabel(sb.toString());
        }
    }

    void setStatisticsLabel(final String s) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
				public void run() {
                    statisticsLabel.setText(s);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    int getScreenRefreshRate() {
        int rate = 60;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        for (int i = 0; i < gs.length; i++) {
            DisplayMode dm = gs[i].getDisplayMode();
            // Get refresh rate in Hz
            int refreshRate = dm.getRefreshRate();
            if (refreshRate == DisplayMode.REFRESH_RATE_UNKNOWN) {
//                log.warning("MotionViewer.getScreenRefreshRate: got unknown refresh rate for screen "+i+", assuming 60");
                refreshRate = 60;
            } else {
//                log.info("MotionViewer.getScreenRefreshRate: screen "+i+" has refresh rate "+refreshRate);
            }
            if (i == 0) {
                rate = refreshRate;
            }
        }
        return rate;
    }

// computes and executes appropriate delayForDesiredFPS to try to maintain constant rendering rate
    class FrameRater {

        final int MAX_FPS = 120;
        int desiredFPS = prefs.getInt("MotionViewer.FrameRater.desiredFPS", getScreenRefreshRate());
        final int nSamples = 10;
        long[] samplesNs = new long[nSamples];
        int index = 0;
        int delayMs = 1;
        int desiredPeriodMs = (int) (1000f / desiredFPS);

        void setDesiredFPS(int fps) {
            if (fps < 1) {
                fps = 1;
            } else if (fps > MAX_FPS) {
                fps = MAX_FPS;
            }
            desiredFPS = fps;
            prefs.putInt("MotionViewer.FrameRater.desiredFPS", fps);
            desiredPeriodMs = 1000 / fps;
        }

        int getDesiredFPS() {
            return desiredFPS;
        }

        float getAveragePeriodNs() {
            int sum = 0;
            for (int i = 0; i < nSamples; i++) {
                sum += samplesNs[i];
            }
            return (float) sum / nSamples;
        }

        float getAverageFPS() {
            return 1f / (getAveragePeriodNs() / 1e9f);
        }

        float getLastFPS() {
            return 1f / (lastdt / 1e9f);
        }

        int getLastDelayMs() {
            return delayMs;
        }

        long getLastDtNs() {
            return lastdt;
        }
        long beforeTimeNs = System.nanoTime(), lastdt, afterTimeNs;

        //  call this ONCE after capture/render. it will store the time since the last call
        void takeBefore() {
            beforeTimeNs = System.nanoTime();
        }
        long lastAfterTime = System.nanoTime();

        //  call this ONCE after capture/render. it will store the time since the last call
        void takeAfter() {
            afterTimeNs = System.nanoTime();
            lastdt = afterTimeNs - beforeTimeNs;
            samplesNs[index++] = afterTimeNs - lastAfterTime;
            lastAfterTime = afterTimeNs;
            if (index >= nSamples) {
                index = 0;
            }
        }

        // call this to delayForDesiredFPS enough to make the total time including last sample period equal to desiredPeriodMs
        void delayForDesiredFPS() {
            delayMs = Math.round(desiredPeriodMs - ((float) getLastDtNs() / 1000000));
            if (delayMs < 0) {
                delayMs = 1;
            }
            try {
                Thread.currentThread();
				Thread.sleep(delayMs);
            } catch (java.lang.InterruptedException e) {
            }
        }
    }

    public void stopMe() {
//        log.info(Thread.currentThread()+ "MotionViewer.stopMe() called");
        switch (getPlayMode()) {
            case PLAYBACK:
                getPlayer().stopPlayback();
                break;
            case LIVE:
            case WAITING:
                viewLoop.stop = true;
                showBiasgen(false);
                break;
        }
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
        statisticsPanel = new javax.swing.JPanel();
        imagePanel = new javax.swing.JPanel();
        mainToolBar = new javax.swing.JToolBar();
        jPanel2 = new javax.swing.JPanel();
        toolbarPanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        biasesToggleButton = new javax.swing.JToggleButton();
        loggingButton = new javax.swing.JToggleButton();
        playerControlPanel = new javax.swing.JPanel();
        playerSlider = new javax.swing.JSlider();
        jPanel3 = new javax.swing.JPanel();
        resizeIconLabel = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        openMenuItem = new javax.swing.JMenuItem();
        closeMenuItem = new javax.swing.JMenuItem();
        jSeparator7 = new javax.swing.JSeparator();
        saveImageMenuItem = new javax.swing.JMenuItem();
        saveImageSequenceMenuItem = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JSeparator();
        loggingMenuItem = new javax.swing.JMenuItem();
        loggingPlaybackImmediatelyCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        openLoggingFolderMenuItem = new javax.swing.JMenuItem();
        jSeparator5 = new javax.swing.JSeparator();
        exitMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        viewBiasesMenuItem = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JSeparator();
        increaseContrastMenuItem = new javax.swing.JMenuItem();
        decreaseContrastMenuItem = new javax.swing.JMenuItem();
        autoscaleContrastEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator4 = new javax.swing.JSeparator();
        cycleDisplayMethodButton = new javax.swing.JMenuItem();
        displayMethodMenu = new javax.swing.JMenu();
        jSeparator12 = new javax.swing.JSeparator();
        increaseFrameRateMenuItem = new javax.swing.JMenuItem();
        decreaseFrameRateMenuItem = new javax.swing.JMenuItem();
        pauseRenderingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewSingleStepMenuItem = new javax.swing.JMenuItem();
        zeroTimestampsMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        increasePlaybackSpeedMenuItem = new javax.swing.JMenuItem();
        decreasePlaybackSpeedMenuItem = new javax.swing.JMenuItem();
        rewindPlaybackMenuItem = new javax.swing.JMenuItem();
        togglePlaybackDirectionMenuItem = new javax.swing.JMenuItem();
        measureTimeMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        contentMenuItem = new javax.swing.JMenuItem();
        helpUserGuideMenuItem = new javax.swing.JMenuItem();
        javadocMenuItem = new javax.swing.JMenuItem();
        aboutMenuItem = new javax.swing.JMenuItem();
        chipMenu = new javax.swing.JMenu();

        setTitle("Retina");
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
			public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
			public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });

        statisticsPanel.setFocusable(false);
        statisticsPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
			public void componentResized(java.awt.event.ComponentEvent evt) {
                statisticsPanelComponentResized(evt);
            }
        });
        getContentPane().add(statisticsPanel, java.awt.BorderLayout.NORTH);

        imagePanel.setEnabled(false);
        imagePanel.setFocusable(false);
        imagePanel.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            @Override
			public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                imagePanelMouseWheelMoved(evt);
            }
        });
        imagePanel.setLayout(new java.awt.BorderLayout());
        getContentPane().add(imagePanel, java.awt.BorderLayout.CENTER);
        getContentPane().add(mainToolBar, java.awt.BorderLayout.SOUTH);
        getContentPane().add(jPanel2, java.awt.BorderLayout.EAST);

        toolbarPanel.setLayout(new java.awt.BorderLayout());

        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.LINE_AXIS));

        biasesToggleButton.setText("Biasgen");
        biasesToggleButton.setToolTipText("Shows or hides the bias generator control panel");
        biasesToggleButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                biasesToggleButtonActionPerformed(evt);
            }
        });
        jPanel1.add(biasesToggleButton);

        loggingButton.setText("Start logging");
        loggingButton.setToolTipText("Starts or stops logging or relogging");
        loggingButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                loggingButtonActionPerformed(evt);
            }
        });
        jPanel1.add(loggingButton);

        playerControlPanel.setToolTipText("playback controls");
        playerControlPanel.setPreferredSize(new java.awt.Dimension(310, 40));

        playerSlider.setSnapToTicks(true);
        playerSlider.setToolTipText("Shows or controls playback position (in events)");
        playerSlider.setValue(0);
        playerSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
			public void stateChanged(javax.swing.event.ChangeEvent evt) {
                playerSliderStateChanged(evt);
            }
        });
        playerControlPanel.add(playerSlider);

        jPanel1.add(playerControlPanel);

        toolbarPanel.add(jPanel1, java.awt.BorderLayout.WEST);

        jPanel3.setLayout(new java.awt.BorderLayout());

        resizeIconLabel.setIcon(new TriangleSquareWindowsCornerIcon());
        resizeIconLabel.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);
        resizeIconLabel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
			public void mouseDragged(java.awt.event.MouseEvent evt) {
                resizeIconLabelMouseDragged(evt);
            }
        });
        resizeIconLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
			public void mouseEntered(java.awt.event.MouseEvent evt) {
                resizeIconLabelMouseEntered(evt);
            }
            @Override
			public void mouseExited(java.awt.event.MouseEvent evt) {
                resizeIconLabelMouseExited(evt);
            }
            @Override
			public void mousePressed(java.awt.event.MouseEvent evt) {
                resizeIconLabelMousePressed(evt);
            }
        });
        jPanel3.add(resizeIconLabel, java.awt.BorderLayout.SOUTH);

        toolbarPanel.add(jPanel3, java.awt.BorderLayout.EAST);

        getContentPane().add(toolbarPanel, java.awt.BorderLayout.SOUTH);

        fileMenu.setMnemonic('f');
        fileMenu.setText("File");

        openMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, 0));
        openMenuItem.setMnemonic('o');
        openMenuItem.setText("Open logged data file...");
        openMenuItem.setToolTipText("Opens a logged data file for playback");
        openMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
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
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(closeMenuItem);
        fileMenu.add(jSeparator7);

        saveImageMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        saveImageMenuItem.setText("Save image as PNG");
        saveImageMenuItem.setToolTipText("Saves a single PNG of the canvas");
        saveImageMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveImageMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveImageMenuItem);

        saveImageSequenceMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        saveImageSequenceMenuItem.setText("Save image sequence");
        saveImageSequenceMenuItem.setToolTipText("Saves sequence to a set of files in folder");
        saveImageSequenceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveImageSequenceMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveImageSequenceMenuItem);
        fileMenu.add(jSeparator6);

        loggingMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, 0));
        loggingMenuItem.setText("Start logging data");
        fileMenu.add(loggingMenuItem);

        loggingPlaybackImmediatelyCheckBoxMenuItem.setText("Playback logged data immediately after logging enabled");
        loggingPlaybackImmediatelyCheckBoxMenuItem.setToolTipText("If enabled, logged data plays back immediately");
        loggingPlaybackImmediatelyCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                loggingPlaybackImmediatelyCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(loggingPlaybackImmediatelyCheckBoxMenuItem);

        openLoggingFolderMenuItem.setMnemonic('W');
        openLoggingFolderMenuItem.setText("Open logging folder in Windows");
        openLoggingFolderMenuItem.setToolTipText("Opens the folder where files are logged");
        openLoggingFolderMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                openLoggingFolderMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openLoggingFolderMenuItem);
        fileMenu.add(jSeparator5);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, 0));
        exitMenuItem.setMnemonic('x');
        exitMenuItem.setText("Exit");
        exitMenuItem.setToolTipText("Exits all viewers");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        viewMenu.setMnemonic('v');
        viewMenu.setText("View");

        viewBiasesMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, java.awt.event.InputEvent.CTRL_MASK));
        viewBiasesMenuItem.setMnemonic('b');
        viewBiasesMenuItem.setText("Biases");
        viewBiasesMenuItem.setToolTipText("Shows bias generator controls");
        viewBiasesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewBiasesMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(viewBiasesMenuItem);
        viewMenu.add(jSeparator3);

        increaseContrastMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0));
        increaseContrastMenuItem.setText("Increase contrast");
        increaseContrastMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                increaseContrastMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(increaseContrastMenuItem);

        decreaseContrastMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0));
        decreaseContrastMenuItem.setText("Decrease contrast");
        decreaseContrastMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                decreaseContrastMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(decreaseContrastMenuItem);

        autoscaleContrastEnabledCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, 0));
        autoscaleContrastEnabledCheckBoxMenuItem.setText("Autoscale contrast enabled");
        autoscaleContrastEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoscaleContrastEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(autoscaleContrastEnabledCheckBoxMenuItem);
        viewMenu.add(jSeparator4);

        cycleDisplayMethodButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, 0));
        cycleDisplayMethodButton.setText("Cycle display method");
        cycleDisplayMethodButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                cycleDisplayMethodButtonActionPerformed(evt);
            }
        });
        viewMenu.add(cycleDisplayMethodButton);

        displayMethodMenu.setText("display methods (placeholder)");
        viewMenu.add(displayMethodMenu);
        viewMenu.add(jSeparator12);

        increaseFrameRateMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0));
        increaseFrameRateMenuItem.setText("Increase rendering frame rate");
        increaseFrameRateMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                increaseFrameRateMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(increaseFrameRateMenuItem);

        decreaseFrameRateMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0));
        decreaseFrameRateMenuItem.setText("Decrease rendering frame rate");
        decreaseFrameRateMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                decreaseFrameRateMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(decreaseFrameRateMenuItem);

        pauseRenderingCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0));
        pauseRenderingCheckBoxMenuItem.setText("Paused");
        pauseRenderingCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseRenderingCheckBoxMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(pauseRenderingCheckBoxMenuItem);

        viewSingleStepMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PERIOD, 0));
        viewSingleStepMenuItem.setText("Single step");
        viewSingleStepMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewSingleStepMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(viewSingleStepMenuItem);

        zeroTimestampsMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, 0));
        zeroTimestampsMenuItem.setText("Zero timestamps");
        zeroTimestampsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                zeroTimestampsMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(zeroTimestampsMenuItem);
        viewMenu.add(jSeparator2);

        increasePlaybackSpeedMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, 0));
        increasePlaybackSpeedMenuItem.setText("Increase playback speed");
        increasePlaybackSpeedMenuItem.setEnabled(false);
        increasePlaybackSpeedMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                increasePlaybackSpeedMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(increasePlaybackSpeedMenuItem);

        decreasePlaybackSpeedMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, 0));
        decreasePlaybackSpeedMenuItem.setText("Decrease playback speed");
        decreasePlaybackSpeedMenuItem.setEnabled(false);
        decreasePlaybackSpeedMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                decreasePlaybackSpeedMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(decreasePlaybackSpeedMenuItem);

        rewindPlaybackMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, 0));
        rewindPlaybackMenuItem.setText("Rewind");
        rewindPlaybackMenuItem.setEnabled(false);
        rewindPlaybackMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                rewindPlaybackMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(rewindPlaybackMenuItem);

        togglePlaybackDirectionMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, 0));
        togglePlaybackDirectionMenuItem.setText("Toggle playback direction");
        togglePlaybackDirectionMenuItem.setEnabled(false);
        togglePlaybackDirectionMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                togglePlaybackDirectionMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(togglePlaybackDirectionMenuItem);

        measureTimeMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, 0));
        measureTimeMenuItem.setText("Measure time");
        measureTimeMenuItem.setToolTipText("Each click reports statistics about timing since last click");
        measureTimeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                measureTimeMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(measureTimeMenuItem);

        menuBar.add(viewMenu);

        helpMenu.setMnemonic('h');
        helpMenu.setText("Help");

        contentMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        contentMenuItem.setMnemonic('c');
        contentMenuItem.setText("Quick start & installation guide (online)");
        contentMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                contentMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(contentMenuItem);

        helpUserGuideMenuItem.setText("USB2 Mini user guide (PDF)");
        helpUserGuideMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpUserGuideMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(helpUserGuideMenuItem);

        javadocMenuItem.setText("Javadoc");
        javadocMenuItem.setToolTipText("Javadoc for classes");
        javadocMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                javadocMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(javadocMenuItem);

        aboutMenuItem.setText("About");
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        chipMenu.setText("Chip");
        chipMenu.setToolTipText("Things to do with chip");
        menuBar.add(chipMenu);

        setJMenuBar(menuBar);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void loggingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loggingButtonActionPerformed
// TODO add your handling code here:
    }//GEN-LAST:event_loggingButtonActionPerformed

    private void openLoggingFolderMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openLoggingFolderMenuItemActionPerformed
        openLoggingFolderWindow();
    }//GEN-LAST:event_openLoggingFolderMenuItemActionPerformed
    volatile AEMulticastInput socketInputStream = null;
    volatile AEMulticastOutput socketOutputStream = null;

    private void resizeIconLabelMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeIconLabelMouseExited
        setCursor(preResizeCursor);
    }//GEN-LAST:event_resizeIconLabelMouseExited
    Cursor preResizeCursor = Cursor.getDefaultCursor();

    private void resizeIconLabelMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeIconLabelMouseEntered
        preResizeCursor = getCursor();
        setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
    }//GEN-LAST:event_resizeIconLabelMouseEntered
    Dimension oldSize;
    Point startResizePoint;

    private void resizeIconLabelMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeIconLabelMouseDragged
        Point resizePoint = evt.getPoint();
        int widthInc = resizePoint.x - startResizePoint.x;
        int heightInc = resizePoint.y - startResizePoint.y;
        setSize(getWidth() + widthInc, getHeight() + heightInc);
    }//GEN-LAST:event_resizeIconLabelMouseDragged

    private void resizeIconLabelMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_resizeIconLabelMousePressed
        oldSize = getSize();
        startResizePoint = evt.getPoint();
    }//GEN-LAST:event_resizeIconLabelMousePressed

    private void cycleDisplayMethodButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cycleDisplayMethodButtonActionPerformed
        chipCanvas.cycleDisplayMethod();
    }//GEN-LAST:event_cycleDisplayMethodButtonActionPerformed

    private void helpUserGuideMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpUserGuideMenuItemActionPerformed
        showInBrowser("http://www.google.com");
//        try {
//            BrowserLauncher launcher = new BrowserLauncher();
//            launcher.openURLinBrowser("http://www.google.com");
//        } catch (Exception e) {
//            contentMenuItem.setText(e.getMessage());
//        }
    }//GEN-LAST:event_helpUserGuideMenuItemActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        log.info("window closed event, calling stopMe");
        stopMe();
    }//GEN-LAST:event_formWindowClosed

    private void javadocMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_javadocMenuItemActionPerformed
        showInBrowser(HELP_URL_JAVADOC);
//        try {
//            BrowserLauncher launcher = new BrowserLauncher();
//            launcher.openURLinBrowser(HELP_URL_JAVADOC);
////            BrowserLauncher.openURL(HELP_URL_JAVADOC);
//        } catch (Exception e) {
//            contentMenuItem.setText(e.getMessage());
//        }
    }//GEN-LAST:event_javadocMenuItemActionPerformed
    volatile boolean doSingleStepEnabled = false;

    synchronized public void doSingleStep() {
        setDoSingleStepEnabled(true);
        setPaused(true);
    }

    public void setDoSingleStepEnabled(boolean yes) {
        doSingleStepEnabled = yes;
    }

    synchronized public boolean isSingleStep() {
        return doSingleStepEnabled;
    }

    synchronized public void singleStepDone() {
        if (isSingleStep()) {
            setDoSingleStepEnabled(false);
            setPaused(true);
        }
    }

    private void viewSingleStepMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewSingleStepMenuItemActionPerformed
        doSingleStep();
    }//GEN-LAST:event_viewSingleStepMenuItemActionPerformed

    // used to print dt for measuring frequency from playback by using '1' keystrokes
    class Statistics {

        JFrame statFrame;
        JLabel statLabel;
        int lastTime = 0, thisTime;
        EngineeringFormat fmt = new EngineeringFormat();

        {
            fmt.precision = 2;
        }

        void printStats() {
            synchronized (player) {
                thisTime = player.getTime();
                int dt = lastTime - thisTime;
                float dtSec = (dt / 1e6f) + Float.MIN_VALUE;
                float freqHz = 1 / dtSec;
//                System.out.println(String.format("dt=%.2g s, freq=%.2g Hz",dtSec,freqHz));
                if (statFrame == null) {
                    statFrame = new JFrame("Statistics");
                    statLabel = new JLabel();
                    statLabel.setFont(statLabel.getFont().deriveFont(16f));
                    statLabel.setToolTipText("Type \"1\" to update interval statistics");
                    statFrame.getContentPane().setLayout(new BorderLayout());
                    statFrame.getContentPane().add(statLabel, BorderLayout.CENTER);
                    statFrame.pack();
                }
                String s = " dt=" + fmt.format(dtSec) + "s, freq=" + fmt.format(freqHz) + " Hz ";
//                System.out.println(s);
                statLabel.setText(s);
                statLabel.revalidate();
                statFrame.pack();
                if (!statFrame.isVisible()) {
                    statFrame.setVisible(true);
                }
                requestFocus(); // leave the focus here
                lastTime = thisTime;
            }
        }
    }
    Statistics statistics;

    private void measureTimeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_measureTimeMenuItemActionPerformed
        if (statistics == null) {
            statistics = new Statistics();
        }
        statistics.printStats();
    }//GEN-LAST:event_measureTimeMenuItemActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        Dimension d= getSize();
        Point p= getLocation();
        prefs.putInt("Window.left", (int) p.getX());
        prefs.putInt("Window.top", (int) p.getY());
        prefs.putInt("Window.width", (int) d.getWidth());
        prefs.putInt("Window.height", (int) d.getHeight());

        log.info("window closing event, only 1 viewer so calling System.exit");
        stopMe();
        System.exit(0);
    }//GEN-LAST:event_formWindowClosing

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        // handle statistics label font sizing here
//        System.out.println("*****************frame resize");
        double fw = getWidth();
        double lw = statisticsLabel.getWidth();

        if (fw < 200) {
            fw = 200;
        }
        double r = fw / lw;
        final double mn = .3, mx = 2.3;
        if (r < mn) {
            r = mn;
        }
        if (r > mx) {
            r = mx;
        }

        final int minFont = 10, maxFont = 36;
//        System.out.println("frame/label width="+r);
        Font f = statisticsLabel.getFont();
        int size = f.getSize();
        int newsize = (int) Math.floor(size * r);
        if (newsize < minFont) {
            newsize = minFont;
        }
        if (newsize > maxFont) {
            newsize = maxFont;
        }
        if (size == newsize) {
            return;
        }
        Font nf = f.deriveFont((float) newsize);
//        System.out.println("old font="+f);
//        System.out.println("new font="+nf);
        statisticsLabel.setFont(nf);

    }//GEN-LAST:event_formComponentResized

    private void statisticsPanelComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_statisticsPanelComponentResized
//        statisticsPanel.revalidate();
    }//GEN-LAST:event_statisticsPanelComponentResized

    private void saveImageSequenceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveImageSequenceMenuItemActionPerformed
        if (canvasFileWriter.writingMovieEnabled) {
            canvasFileWriter.stopWritingMovie();
            saveImageSequenceMenuItem.setText("Start writing image sequence");
        } else {
            canvasFileWriter.startWritingMovie();
            saveImageSequenceMenuItem.setText("Stop writing sequence");
        }
    }//GEN-LAST:event_saveImageSequenceMenuItemActionPerformed
    CanvasFileWriter canvasFileWriter = new CanvasFileWriter();

    private void saveImageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveImageMenuItemActionPerformed
        synchronized (chipCanvas) {
            canvasFileWriter.writeSnapshotImage(); // chipCanvas must be drawn with java (not openGL) for this to work
        }
    }//GEN-LAST:event_saveImageMenuItemActionPerformed

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
        getPlayer().toggleDirection();
    }//GEN-LAST:event_togglePlaybackDirectionMenuItemActionPerformed

    private void rewindPlaybackMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rewindPlaybackMenuItemActionPerformed
        getPlayer().rewind();
    }//GEN-LAST:event_rewindPlaybackMenuItemActionPerformed

    private void decreasePlaybackSpeedMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decreasePlaybackSpeedMenuItemActionPerformed
        getPlayer().slowDown();
    }//GEN-LAST:event_decreasePlaybackSpeedMenuItemActionPerformed

    private void increasePlaybackSpeedMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_increasePlaybackSpeedMenuItemActionPerformed
        getPlayer().speedUp();
    }//GEN-LAST:event_increasePlaybackSpeedMenuItemActionPerformed

    private void autoscaleContrastEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoscaleContrastEnabledCheckBoxMenuItemActionPerformed
        renderer.setAutoscaleEnabled(!renderer.isAutoscaleEnabled());;
    }//GEN-LAST:event_autoscaleContrastEnabledCheckBoxMenuItemActionPerformed

    private void zeroTimestampsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zeroTimestampsMenuItemActionPerformed
                                                                            }//GEN-LAST:event_zeroTimestampsMenuItemActionPerformed

    private void pauseRenderingCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseRenderingCheckBoxMenuItemActionPerformed
        setPaused(!isPaused());
        if (!isPaused()) {
//            viewLoop.singleStepEnabled=false;
//            System.out.println("pauseRenderingCheckBoxMenuItemActionPerformed: set singleStepEnabled=false");
        }
    }//GEN-LAST:event_pauseRenderingCheckBoxMenuItemActionPerformed

    private void decreaseFrameRateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decreaseFrameRateMenuItemActionPerformed
        setFrameRate(getFrameRate() / 2);
    }//GEN-LAST:event_decreaseFrameRateMenuItemActionPerformed

    private void increaseFrameRateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_increaseFrameRateMenuItemActionPerformed
        setFrameRate(getFrameRate() * 2);
    }//GEN-LAST:event_increaseFrameRateMenuItemActionPerformed

    private void decreaseContrastMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decreaseContrastMenuItemActionPerformed
        renderer.setColorScale(renderer.getColorScale() + 1);
    }//GEN-LAST:event_decreaseContrastMenuItemActionPerformed

    private void increaseContrastMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_increaseContrastMenuItemActionPerformed
        renderer.setColorScale(renderer.getColorScale() - 1);
    }//GEN-LAST:event_increaseContrastMenuItemActionPerformed

    private void closeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeMenuItemActionPerformed
        stopMe();
    }//GEN-LAST:event_closeMenuItemActionPerformed

    private void viewBiasesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewBiasesMenuItemActionPerformed
        showBiasgen(true);
    }//GEN-LAST:event_viewBiasesMenuItemActionPerformed
    //avoid stateChanged events from slider that is set by player
    volatile boolean sliderDontProcess = false;

    /** messages come back here from e.g. programmatic state changes, like a new aePlayer file position.
     * This methods sets the GUI components to a consistent state, using a flag to tell the slider that it has not been set by
     * a user mouse action
     */
    @Override
	public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(AEInputStream.EVENT_POSITION)) {
//            System.out.println("slider property change new val="+evt.getNewValue());
            sliderDontProcess = true;
            // note this cool semaphore/flag trick to avoid processing the
            // event generated when we programmatically set the slider position here
            playerSlider.setValue(Math.round(player.getFractionalPosition() * 100));
        } else if (evt.getPropertyName().equals("readerStarted")) {
            log.info("MotionViewer.propertyChange: AEReader started, fixing device control menu");
            // cypress reader started, can set device control for cypress usbio reader thread
//            fixDeviceControlMenuItems();
        }
    }

    private void playerSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_playerSliderStateChanged
        if (sliderDontProcess) {
            sliderDontProcess = false; // to avoid player callbacks generating more AWT events
            return;
        }
        float fracPos = (float) playerSlider.getValue() / (playerSlider.getMaximum());
        player.setFractionalPosition(fracPos);
    }//GEN-LAST:event_playerSliderStateChanged

    private void contentMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_contentMenuItemActionPerformed
            showInBrowser(HELP_URL_USER_GUIDE);
    }//GEN-LAST:event_contentMenuItemActionPerformed

    private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
        new AEViewerAboutDialog(new javax.swing.JFrame(), true).setVisible(true);
    }//GEN-LAST:event_aboutMenuItemActionPerformed

    private void openMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openMenuItemActionPerformed
        getPlayer().openInputFileDialog();
    }//GEN-LAST:event_openMenuItemActionPerformed

    void showBiasgen(final boolean yes) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
			public void run() {
                if (chip.getBiasgen() == null) { // this chip has no biasgen - but it won't have one until HW interface is opened for it successfully
                    if (biasgenFrame != null) {
                        biasgenFrame.dispose();
                    }
//            biasesToggleButton.setEnabled(false);  // chip don't have biasgen until it has HW interface, which it doesn't at first....
                    return;
                } else {
                    biasesToggleButton.setEnabled(true);
                    viewBiasesMenuItem.setEnabled(true);
                }
                if (biasgen != chip.getBiasgen()) { // biasgen changed
                    if (biasgenFrame != null) {
                        biasgenFrame.dispose();
                    }
                    biasgenFrame = new BiasgenFrame(chip);
                    biasgenFrame.addWindowListener(new WindowAdapter() {

                        @Override
						public void windowClosed(WindowEvent e) {
                            log.info(e.toString());
                            biasesToggleButton.setSelected(false);
                        }
                    });
                }
                if (biasgenFrame != null) {
                    biasgenFrame.setVisible(yes);
                }
                biasesToggleButton.setSelected(yes);
                biasgen = chip.getBiasgen();

            }
        });

    }

    synchronized public void toggleLogging() {
        if (loggingEnabled) {
            stopLogging();
        } else {
            startLogging();
        }
    }

    /** this action toggles logging, possibily for all viewers depending on switch */
    public class ToggleLoggingAction extends AbstractAction {

        public ToggleLoggingAction() {
            putValue(NAME, "Start logging");
            putValue(SHORT_DESCRIPTION, "Starts and stops logging");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_L, 0));
            putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_L));
        }

        @Override
		public void actionPerformed(ActionEvent e) {
            log.info("CaviarViewer.ToggleLoggingAction.actionPerformed");
            toggleLogging();
            if (loggingEnabled) {
                putValue(NAME, "Stop logging");
            } else {
                putValue(NAME, "Start logging");
            }
            log.info(e + " loggingEnabled=" + loggingEnabled);
        }
    }

    void fixLoggingControls() {
//        System.out.println("fixing logging controls, loggingEnabled="+loggingEnabled);
        if (((hardware == null) || ((hardware != null) && !hardware.isOpen())) && (playMode != PlayMode.PLAYBACK)) { // we can log from live input or from playing file (e.g. after refiltering it)
            loggingButton.setEnabled(false);
            loggingMenuItem.setEnabled(false);
            return;
        } else {
            loggingButton.setEnabled(true);
            loggingMenuItem.setEnabled(true);
        }
        if (!loggingEnabled && (playMode == PlayMode.PLAYBACK)) {
            loggingButton.setText("Start Re-logging");
            loggingMenuItem.setText("Start Re-logging");
        } else if (loggingEnabled) {
            loggingButton.setText("Stop logging");
            loggingButton.setSelected(true);
            loggingMenuItem.setText("Stop logging data");
        } else {
            loggingButton.setText("Start logging");
            loggingButton.setSelected(false);
            loggingMenuItem.setText("Start logging data");
        }
    }

    public void openLoggingFolderWindow() {
        String osName = System.getProperty("os.name");
        if (osName == null) {
            log.warning("no OS name property, cannot open browser");
            return;
        }
        String curDir = System.getProperty("user.dir");
        log.info("opening folder window for folder " + curDir);
        if (osName.startsWith("Windows")) {
            try {
                Runtime.getRuntime().exec("explorer.exe " + curDir);
            } catch (IOException e) {
                log.warning(e.getMessage());
            }
        } else if (System.getProperty("os.name").indexOf("Linux") != -1) {
            log.warning("cannot open linux folder browsing window");
        }
    }

    synchronized public File startLogging() {
        if (playMode != PlayMode.LIVE) {
            return null;
        }
        String dateString = loggingFilenameDateFormat.format(new Date());
        String className = chip.getClass().getSimpleName();
        int suffixNumber = 0;
        boolean suceeded = false;
        String filename;
        do {
            filename = className + "-" + dateString + "-" + suffixNumber + AEDataFile.DATA_FILE_EXTENSION;
            loggingFile = new File(filename);
            if (!loggingFile.isFile()) {
                suceeded = true;
            }
        } while ((suceeded == false) && (suffixNumber++ <= 5));
        if (suceeded == false) {
            log.warning("MotionViewer.startLogging(): could not open a unigue new file for logging after trying up to " + filename);
            return null;
        }
        try {
            loggingOutputStream = new MotionOutputStream(new BufferedOutputStream(new FileOutputStream(loggingFile)));
            loggingEnabled = true;
            log.info("starting logging at " + dateString);
            setCurrentFile(loggingFile);
            loggingEnabled = true;
            fixLoggingControls();
            if (loggingTimeLimit > 0) {
                loggingStartTime = System.currentTimeMillis();
            }
            tobiLogger.setEnabled(true);
//            hardware.resetTimestamps();
        } catch (Exception e) {
            loggingFile = null;
            e.printStackTrace();
        }
        return loggingFile;
    }

    synchronized public void stopLogging() {
        if (loggingEnabled) {
            if (loggingButton.isSelected()) {
                loggingButton.setSelected(false);
            }
            loggingButton.setText("Start logging");
            loggingMenuItem.setText("Start logging data");
            try {
                synchronized (loggingOutputStream) {
                    loggingEnabled = false;
                    loggingOutputStream.close();
                }
                String dateString = loggingFilenameDateFormat.format(new Date());
                log.info("stopping logging at " + dateString);
                recentFiles.addFile(loggingFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (isLoggingPlaybackImmediatelyEnabled()) {
                try {
                    getPlayer().startPlayback(loggingFile);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            tobiLogger.setEnabled(false);
            loggingEnabled = false;
        }
        fixLoggingControls();
    }

    @Override
	public String toString() {
        return getTitle();
    }

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
//        System.exit(0);
        stopMe();
        if ((hardware != null) && hardware.isOpen()) {
            hardware.close();
        }
        System.exit(0);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    public int getFrameRate() {
        return frameRater.getDesiredFPS();
    }

    public void setFrameRate(int renderDesiredFrameRateHz) {
        frameRater.setDesiredFPS(renderDesiredFrameRateHz);
    }
    boolean paused = false;

    public boolean isPaused() {
        return paused;
    }

    /** sets paused. If viewing is synchronized, then all viwewers will be paused.
     *@param paused true to pause
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    // drag and drop data file onto frame to play it
//          Called while a drag operation is ongoing, when the mouse pointer enters the operable part of the drop site for the DropTarget registered with this listener.
    @Override
	public void dragEnter(DropTargetDragEvent dtde) {
        Transferable transferable = dtde.getTransferable();
        try {
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                java.util.List<File> files = (java.util.List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                for (File f : files) {
                    if (f.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION) || f.getName().endsWith(AEDataFile.INDEX_FILE_EXTENSION)
                            || f.getName().endsWith(AEDataFile.OLD_DATA_FILE_EXTENSION) || f.getName().endsWith(AEDataFile.OLD_INDEX_FILE_EXTENSION)) {
                        draggedFile = f;
                    } else {
                        draggedFile = null;
                    }
                }
//                System.out.println("MotionViewer.dragEnter(): draqged file="+draggedFile);
            }
        } catch (UnsupportedFlavorException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

//          Called while a drag operation is ongoing, when the mouse pointer has exited the operable part of the drop site for the DropTarget registered with this listener.
    @Override
	public void dragExit(DropTargetEvent dte) {
        draggedFile = null;
    }
//          Called when a drag operation is ongoing, while the mouse pointer is still over the operable part of the drop site for the DropTarget registered with this listener.

    @Override
	public void dragOver(DropTargetDragEvent dtde) {
    }

    //  Called when the drag operation has terminated with a drop on the operable part of the drop site for the DropTarget registered with this listener.
    @Override
	public void drop(DropTargetDropEvent dtde) {
        if (draggedFile != null) {
            log.info("MotionViewer.drop(): opening file " + draggedFile);
            try {
                recentFiles.addFile(draggedFile);
                getPlayer().startPlayback(draggedFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

//          Called if the user has modified the current drop gesture.
    @Override
	public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    public boolean isLoggingPlaybackImmediatelyEnabled() {
        return loggingPlaybackImmediatelyEnabled;
    }

    public void setLoggingPlaybackImmediatelyEnabled(boolean loggingPlaybackImmediatelyEnabled) {
        this.loggingPlaybackImmediatelyEnabled = loggingPlaybackImmediatelyEnabled;
        prefs.putBoolean("MotionViewer.loggingPlaybackImmediatelyEnabled", loggingPlaybackImmediatelyEnabled);
    }

    /** @return the chip we are displaying */
    public Chip2D getChip() {
        return chip;
    }

    public void setChip(Chip2DMotion chip) {
        this.chip = chip;
        if (chip != null) {
            renderer = chip.getRenderer();
        }
    }

    public javax.swing.JMenu getFileMenu() {
        return fileMenu;
    }

    /** used in CaviarViewer to control sync'ed logging */
    public javax.swing.JMenuItem getLoggingMenuItem() {
        return loggingMenuItem;
    }

    public void setLoggingMenuItem(javax.swing.JMenuItem loggingMenuItem) {
        this.loggingMenuItem = loggingMenuItem;
    }

    /** this toggle button is used in CaviarViewer to assign an action to start and stop logging for (possibly) all viewers */
    public javax.swing.JToggleButton getLoggingButton() {
        return loggingButton;
    }

    public void setLoggingButton(javax.swing.JToggleButton b) {
        this.loggingButton = b;
    }

    /** @return the local player, unless we are part of a synchronized playback gruop */
    public Player getPlayer() {
        return player;
    }

    public PlayMode getPlayMode() {
        return playMode;
    }

    /** sets mode, LIVE, PLAYBACK, WAITING, etc */
    public void setPlayMode(PlayMode playMode) {
        // there can be a race condition where user tries to open file, this sets
        // playMode to PLAYBACK but run() method in ViewLoop sets it back to WAITING
        this.playMode = playMode;
//        log.info("set playMode="+playMode);
    }

    public javax.swing.JMenu getChipMenu() {
        return chipMenu;
    }

    public MotionChipInterface getHardware() {
        return MotionViewer.hardware;
    }


      /** Processes remote control commands for this AEViewer. A list of commands can be obtained
     * from a remote host by sending ? or help. The port number is logged to the console on startup.
     * @param command the parsed command (first token)
     * @param line the line sent from the remote host.
     * @return confirmation of command.
     */
    @Override
	public String processRemoteControlCommand(RemoteControlCommand command, String line) {
        String[] tokens = line.split("\\s");
        log.finer("got command " + command + " with line=\"" + line + "\"");
        try {
            if (command.getCmdName().equals(REMOTE_START_LOGGING)) {
                if (tokens.length < 1) {
                    return "not enough arguments\n";
                }
                File f = startLogging();
                    return "starting logging to " + f + "\n";
            } else if (command.getCmdName().equals(REMOTE_STOP_LOGGING)) {
                stopLogging();
                return "stopped logging to "+loggingFile+"\n";
            }
        } catch (Exception e) {
            return e.toString() + "\n";
        }
        return null;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutMenuItem;
    private javax.swing.JCheckBoxMenuItem autoscaleContrastEnabledCheckBoxMenuItem;
    private javax.swing.JToggleButton biasesToggleButton;
    private javax.swing.JMenu chipMenu;
    private javax.swing.JMenuItem closeMenuItem;
    private javax.swing.JMenuItem contentMenuItem;
    private javax.swing.JMenuItem cycleDisplayMethodButton;
    private javax.swing.JMenuItem decreaseContrastMenuItem;
    private javax.swing.JMenuItem decreaseFrameRateMenuItem;
    private javax.swing.JMenuItem decreasePlaybackSpeedMenuItem;
    private javax.swing.JMenu displayMethodMenu;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenuItem helpUserGuideMenuItem;
    private javax.swing.JPanel imagePanel;
    private javax.swing.JMenuItem increaseContrastMenuItem;
    private javax.swing.JMenuItem increaseFrameRateMenuItem;
    private javax.swing.JMenuItem increasePlaybackSpeedMenuItem;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JSeparator jSeparator12;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JSeparator jSeparator7;
    private javax.swing.JMenuItem javadocMenuItem;
    private javax.swing.JToggleButton loggingButton;
    private javax.swing.JMenuItem loggingMenuItem;
    private javax.swing.JCheckBoxMenuItem loggingPlaybackImmediatelyCheckBoxMenuItem;
    private javax.swing.JToolBar mainToolBar;
    private javax.swing.JMenuItem measureTimeMenuItem;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem openLoggingFolderMenuItem;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JCheckBoxMenuItem pauseRenderingCheckBoxMenuItem;
    private javax.swing.JPanel playerControlPanel;
    private javax.swing.JSlider playerSlider;
    private javax.swing.ButtonGroup renderModeButtonGroup;
    private javax.swing.JLabel resizeIconLabel;
    private javax.swing.JMenuItem rewindPlaybackMenuItem;
    private javax.swing.JMenuItem saveImageMenuItem;
    private javax.swing.JMenuItem saveImageSequenceMenuItem;
    private javax.swing.JPanel statisticsPanel;
    private javax.swing.JMenuItem togglePlaybackDirectionMenuItem;
    private javax.swing.JPanel toolbarPanel;
    private javax.swing.JMenuItem viewBiasesMenuItem;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JMenuItem viewSingleStepMenuItem;
    private javax.swing.JMenuItem zeroTimestampsMenuItem;
    // End of variables declaration//GEN-END:variables
}
