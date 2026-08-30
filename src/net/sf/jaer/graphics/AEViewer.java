/*
 * AEViewer.java
 *
 * This is the "main" jAER interface to the user. The main event loop "ViewLoop" is here; see ViewLoop.run()
 *
 * Created on December 24, 2005, 1:58 PM
 */
package net.sf.jaer.graphics;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.ToolTipManager;
import java.util.logging.Handler;
import java.util.logging.ConsoleHandler;

import org.apache.commons.io.FileUtils;

import ch.unizh.ini.jaer.chip.retina.*;
import nrv.chip.NRVS5KRC1S;
import net.sf.jaer.eventio.dsec.DsecHdf5AEInputStream;
import prophesee.chip.PropheseeIMX636HD;
import prophesee.usb.PropheseeHardwareInterface;
import com.google.common.collect.EvictingQueue;
import eu.seebetter.ini.chips.davis.*;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.KeyStroke;
import net.sf.jaer.Description;
import net.sf.jaer.JAERViewer;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.Welcome;
import net.sf.jaer.JaerUpdaterFrame;
import net.sf.jaer.JaerUpdaterInstall4j;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.DroppedDataInfo;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aesequencer.AEMonitorSequencerInterface;
import net.sf.jaer.aesequencer.AESequencerInterface;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenFrame;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.TypedDataPacket;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEDZOutputStream;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEFileOutputStream;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.AENetworkInterfaceConstants;
import net.sf.jaer.eventio.AESocket;
import net.sf.jaer.eventio.AEUnicastDialog;
import net.sf.jaer.eventio.AEUnicastInput;
import net.sf.jaer.eventio.AEUnicastOutput;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;
import net.sf.jaer.eventio.TextFileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4Compression;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.eventio.aedat4.Aedat4Lz4Rerecorder;
import net.sf.jaer.eventio.ros.RosbagFileInputStream;
import net.sf.jaer.eventio.ros2.ROSOutput;
import net.sf.jaer.eventio.ros2.ROSOutputDialog;
import net.sf.jaer.eventio.opencv.OpenCVOutput;
import net.sf.jaer.eventio.opencv.OpenCVOutputDialog;
import net.sf.jaer.util.avioutput.DNNOutputViaSharedMemory;
import net.sf.jaer.util.avioutput.DNNOutputViaSharedMemoryDialog;
import prophesee.eventio.MetavisionRawFileInputStream;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.FilterFrame;
import net.sf.jaer.hardwareinterface.BlankDeviceException;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryChooserDialog;
import net.sf.jaer.hardwareinterface.udp.NetworkChip;
import net.sf.jaer.hardwareinterface.udp.UDPInterface;
import net.sf.jaer.hardwareinterface.usb.HasUsbStatistics;
import net.sf.jaer.hardwareinterface.usb.LibUsbHotplug;
import net.sf.jaer.hardwareinterface.usb.LibUsbLinkInfo;
import net.sf.jaer.hardwareinterface.usb.LiveAcquisitionBench;
import net.sf.jaer.hardwareinterface.usb.LiveDeviceChipDetector;
import net.sf.jaer.hardwareinterface.usb.MacosLibusbHelp;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.UsbIds;
import net.sf.jaer.hardwareinterface.usb.UsbLog;
import net.sf.jaer.hardwareinterface.usb.SessionCameraOpenCoordinator;
import net.sf.jaer.hardwareinterface.usb.UsbOpenTrace;
import net.sf.jaer.hardwareinterface.usb.UsbTransferSubmit;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.WindowsUsbPollSchedule;
import net.sf.jaer.hardwareinterface.usb.WinUsbDriverHelp;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2EEPROM;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2MonitorSequencer;
import net.sf.jaer.stereopsis.StereoPairHardwareInterface;
import net.sf.jaer.util.ClassChooserDialog;
import net.sf.jaer.util.DATFileFilter;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.ExceptionListener;
import net.sf.jaer.util.FileAccessTimeout;
import net.sf.jaer.util.JaerIssueReporter;
import net.sf.jaer.util.HexString;
import net.sf.jaer.util.HtmlHelpStyle;
import net.sf.jaer.util.JaerAllowedSubclasses;
import net.sf.jaer.util.MenuScroller;
import net.sf.jaer.util.RecentFiles;
import net.sf.jaer.util.RecentFoldersComboAccessory;
import net.sf.jaer.util.RemoteControl;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.ShowFolderSaveConfirmation;
import net.sf.jaer.util.TriangleSquareWindowsCornerIcon;
import net.sf.jaer.util.VendorPrefsMigration;
import net.sf.jaer.util.ViewerInterfaceBindingMap;
import net.sf.jaer.util.WarningDialogWithDontShowPreference;
import net.sf.jaer.util.WindowSaver;
import net.sf.jaer.util.avioutput.ExportVideoDialog;
import net.sf.jaer.util.avioutput.JaerAviWriter;
import net.sf.jaer.eventio.export.SaveAsExportDialog;
import net.sf.jaer.util.filter.LowpassFilter;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.opencv.core.Core;

/**
 * This is the main jAER interface to the user. The main event loop "ViewLoop"
 * is here; see ViewLoop.run(). AEViewer shows AE chip live view and allows for
 * controlling view and recording and playing back events from files and network
 * connections.
 * <p>
 * AEViewer supports PropertyChangeListener's and fires PropertyChangeEvents on
 * the following events:
 * <ul>
 * <li> "playmode" - when the player mode changes, e.g. from PlayMode.LIVE to
 * PlayMode.PLAYBACK. The old and new values are the old and new PlayMode values
 * <li> "fileopen" - when a new file is opened; old=null, new=file.
 * <li> "stopme" - when stopme is called; old=new=null.
 * <li> "chip" - when a new AEChip is built for the viewer.
 * <li> "paused" - when paused or resumed - old and new booleans are passed to
 * firePropertyChange.
 * <li> "rememberLastInterface" - global Interface-menu checkbox; all AEViewers
 * stay in sync. Setter no-ops when the value is unchanged.
 * </ul>
 * In addition, when A5EViewer is in PLAYBACK PlayMode, users can register as
 * PropertyChangeListeners on the AEFileInputStream for rewind events, etc.
 *
 *  * <p>
 * In order to use this event, an EventFilter must register itself either with
 * the AEViewer. But this registration is only possible after AEViewer is
 * constructed, which is after the EventFilter is constructed. The registration
 * can occur in the EventFilter filterPacket() method as in the code snippet
 * below:
 * <pre><code>
 *    private boolean addedViewerPropertyChangeListener = false;
 *
 * synchronized public EventPacket filterPacket(EventPacket in) { // TODO completely rework this code because IMUSamples are part of the packet now!
 *  if (!addedViewerPropertyChangeListener) {
 *      if (chip.getAeViewer() != null) {
 * chip.getAeViewer().addPropertyChangeListener(this); // AEViewer refires these events for convenience
 * addedViewerPropertyChangeListener = true;
 * }
 * }
 * }
 * </code>
 * @author tobi
 */
public class AEViewer extends javax.swing.JFrame implements PropertyChangeListener, DropTargetListener, ExceptionListener, RemoteControlled {

    /**
     * PropertyChangeEvent fired from this AEViewer to the PropertyChangeSupport
     * that is part of AEViewer. <b>This support is different than the Java AWT
     * property change support</b>.
     *
     * <p>
     * <b>IMPORTANT:</b> it is a bug to getAeViewer().addPropertyChangeListener
     * - this will add the listener to the AWT component!! Instead use
     * getAeViewer()<b>.getSupport()</b>.addPropertyChangeListener
     *
     * @see #getSupport()
     */
    public static final String EVENT_PLAYMODE = "playmode",
            EVENT_FILEOPEN = "fileopen",
            EVENT_STOPME = "stopme",
            EVENT_CHIP = "chip",
            EVENT_PAUSED = "paused",
            EVENT_TIMESTAMPS_RESET = "timestampsReset",
            EVENT_CHECK_NONMONOTONIC_TIMESTAMPS = "checkNonMonotonicTimestamps",
            EVENT_ACCUMULATE_ENABLED = "accumulateEnabled",
            EVENT_RECORDING_STARTED = "recordingStarted",
            EVENT_RECORDING_STOPPED = "recordingStopped",
            EVENT_REMEMBER_LAST_INTERFACE = "rememberLastInterface";
    private PropertyChangeSupport support = new PropertyChangeSupport(this);

    // note filenames cannot have spaces in them for browser to work easily, some problem with space encoding; %20 doesn't work as advertized.
//    public static String HELP_USER_GUIDE_USB2_MINI = "/docs/USBAERmini2userguide.pdf";
//    public static String HELP_USER_GUIDE_AER_CABLING = "/docs/AERHardwareAndCabling.pdf";
    public static final String HARDWARE_INTERFACE_NUMBER_PROPERTY = "HardwareInterfaceNumber";
    public static final String HARDWARE_INTERFACE_OBJECT_PROPERTY = "hardwareInterfaceObject";
    private static final String SET_DEFAULT_FIRMWARE_FOR_BLANK_DEVICE = "Set default firmware for blank device...";
    // set true to force null hardware (None in interface menu) even if only single interface
    private boolean nullInterface = false;

    //    volatile boolean stop=false; // volatile because multiple threads will access
    int renderCount = 0;
    int numEvents;
//    private AEPacketRaw rawPacket; // the raw packet (just timestamps and addresses) recieved from hardware, network, or file input
//    private EventPacket packet; // the cooked packet (with BasicEvent or subclass objects) of data
    boolean skipRender = false;
    /**
     * Non-null overlay text: skip chip pixmap {@code render()} and
     * {@code paintFrame()} a blank canvas with this message (no APS, DVS, IMU,
     * or filter annotations).
     */
    private volatile String skipChipRenderingOverlay = null;
    /**
     * While non-null, {@link #showWelcomeOverlay()} keeps the
     * {@link Welcome#opening} lines instead of restoring the idle welcome
     * (canvas rebuild during AEChip switch).
     */
    private volatile String pendingOpeningCameraLabel = null;
    DroppedDataInfo droppedDataInfo = DroppedDataInfo.none();
    int tickUs = 1;
    public AEPlayer aePlayer;
    int noEventCounter = 0;

    public final String REMOTE_START_RECORDING = "startrecording";
    public final String REMOTE_STOP_RECORDING = "stoprecording";
    public final String REMOTE_TOGGLE_SYNCHRONIZED_RECORDING = "togglesyncrecording";
    /** Alias of {@link #REMOTE_START_RECORDING} for existing remote scripts. */
    public final String REMOTE_START_LOGGING = "startlogging";
    /** Alias of {@link #REMOTE_STOP_RECORDING} for existing remote scripts. */
    public final String REMOTE_STOP_LOGGING = "stoplogging";
    /** Alias of {@link #REMOTE_TOGGLE_SYNCHRONIZED_RECORDING} for existing remote scripts. */
    public final String REMOTE_TOGGLE_SYNCHRONIZED_LOGGING = "togglesynclogging";
    public final String REMOTE_ZERO_TIMESTAMPS = "zerotimestamps";
    public final String REMOTE_OPEN_FILE = "openfile";
    public final String REMOTE_PAUSE = "pause";
    public final String REMOTE_PLAY = "play";
    public final String REMOTE_REWIND = "rewind";
    public final String REMOTE_SET_MARK_IN = "setmarkin";
    public final String REMOTE_SET_MARK_OUT = "setmarkout";

    /**
     * Modes of viewing: WAITING means waiting for device or for playback or
     * remote, LIVE means showing a hardware interface, PLAYBACK means playing
     * back a recorded file, SEQUENCING means sequencing a file out on a
     * sequencer device, REMOTE means playing a remote stream of AEs,
     * FILTER_INPUT means input comes from an EventFilter.
     */
    public enum PlayMode {

        WAITING, LIVE, PLAYBACK, SEQUENCING, REMOTE, FILTER_INPUT
    }
    volatile private PlayMode playMode = PlayMode.WAITING;
    /**
     * The Preferences node for the AEViewer, which has it's own node in the
     * preferences tree, below the package
     */
    public Preferences prefs = JaerConstants.PREFS_ROOT.node("AEViewer");
    static Logger log = Logger.getLogger("net.sf.jaer");
    //    private PropertyChangeSupport support = new PropertyChangeSupport(this); // already has support as Componenent!!!
    EventExtractor2D extractor = null;
    private BiasgenFrame biasgenFrame = null;
    Biasgen biasgen = null;
    /**
     * First-hardware-use dialog / Hardware Configuration open, run on EDT after
     * {@link PlayMode#LIVE} so preference import and camera start are not blocked.
     */
    private volatile Runnable pendingFirstHardwareUseUi = null;
    /** True if shipped deviceSettings XML should be imported after LIVE. */
    private volatile boolean pendingFirstHardwareUseImport = false;
    /** Heartbeat for EDT liveness during first-use open/import (0 = not armed). */
    private final AtomicLong edtHeartbeatMs = new AtomicLong(0);
    private static final long EDT_FREEZE_HALT_MS = 20000;
    EventFilter2D filter1 = null, filter2 = null;
    private AEChipRenderer renderer = null;
    AEMonitorInterface aemon = null;
    private ViewLoop viewLoop = new ViewLoop();
    /**
     * Dedicated lock for ViewLoop pause wait/notify. Do not use {@link #viewLoop}
     * itself as a mutex for playMode — ViewLoop holds that monitor in
     * {@link #openAEMonitor()} during USB open, which deadlocks EDT file open.
     */
    private final Object viewLoopPauseLock = new Object();
    /**
     * File → Save As owns the playback stream. ViewLoop must not grabInput,
     * extract, filter, or paint until this is cleared — {@link #setPaused}
     * alone is not enough ({@code wait(1000)} still re-renders, and a 150 ms
     * sleep can overlap an in-flight AEDAT-4 {@code grabInput}).
     */
    private volatile boolean viewLoopSuspendedForOfflineExport = false;
    /** ViewLoop has left grabInput and is waiting on {@link #viewLoopPauseLock}. */
    private volatile boolean viewLoopParkedForOfflineExport = false;
    /** WIP experimental: max wait for ViewLoop exit before {@link System#exit(int)}. */
    private static final long VIEWLOOP_EXIT_JOIN_TIMEOUT_MS = 3000;
    /**
     * If orderly close (ViewLoop join + {@link #cleanup()}) has not called
     * {@link System#exit} within this time, a non-EDT watchdog forces exit.
     * Must exceed {@link #VIEWLOOP_EXIT_JOIN_TIMEOUT_MS}.
     */
    private static final long EXIT_WATCHDOG_MS = 8000;
    /** If {@link System#exit} itself hangs in shutdown hooks, {@link Runtime#halt} after this. */
    private static final long EXIT_HALT_AFTER_EXIT_MS = 3000;
    private final AtomicBoolean exitWatchdogArmed = new AtomicBoolean(false);
    FilterChain filterChain = null;
    private FilterFrame filterFrame = null;
    RecentFiles recentFiles = null;
    File lastFile = null;
    public File lastRecordingFolder = null;//changed pol
    File lastImageFile = null;
    File currentFile = null;
    private FrameRater frameRater = null; // constructed in constructor since it needs prefs
    ChipCanvas chipCanvas;
    private volatile boolean recordingEnabled = false, recordingPaused = false;
    /**
     * The file that AE data is currently being recorded to. Note it can change
     * when the user finally selects the file to save the data to.
     *
     * @see #startRecording(String,String)
     */
    private File recordingFile = null;
    AEFileOutputStream recordingOutputStream;
    Aedat4FileOutputStream aedat4RecordingOutputStream;
    AEDZOutputStream aedzRecordingOutputStream;
    private RecordingConfigurationSnapshot activeRecordingSnapshot;
    private boolean activeRenderingEnabled = prefs.getBoolean("AEViewer.activeRenderingEnabled", true);
    private boolean renderBlankFramesEnabled = prefs.getBoolean("AEViewer.renderBlankFramesEnabled", false);

    private DropTarget myDraggedFileDropTarget = null; // added back after losing somehow
    private File draggedFile;
    private boolean recordingPlaybackImmediatelyEnabled = prefs.getBoolean("AEViewer.loggingPlaybackImmediatelyEnabled", false);
    private boolean showRecordingOverlay = prefs.getBoolean("AEViewer.showRecordingOverlay", true);
    private boolean showRosOutputOverlay = prefs.getBoolean("AEViewer.showRosOutputOverlay", true);
    private boolean showDnnSharedMemoryOverlay = prefs.getBoolean("AEViewer.showDnnSharedMemoryOverlay", true);
    private boolean showOpenCvOutputOverlay = prefs.getBoolean("AEViewer.showOpenCvOutputOverlay", true);
    private javax.swing.JMenuItem rosOutputMenuItem;
    private ROSOutputDialog rosOutputDialog;
    private PropertyChangeListener rosOutputEnabledSync;
    private ROSOutput rosOutputMenuBoundFilter;
    private javax.swing.JMenuItem dnnSharedMemoryMenuItem;
    private DNNOutputViaSharedMemoryDialog dnnSharedMemoryDialog;
    private PropertyChangeListener dnnSharedMemoryEnabledSync;
    private DNNOutputViaSharedMemory dnnSharedMemoryMenuBoundFilter;
    private javax.swing.JMenuItem openCvOutputMenuItem;
    private OpenCVOutputDialog openCvOutputDialog;
    private PropertyChangeListener openCvOutputEnabledSync;
    private OpenCVOutput openCvOutputMenuBoundFilter;
    private boolean enableFiltersOnStartup = prefs.getBoolean("AEViewer.enableFiltersOnStartup", false);
    private volatile long recordingTimeLimit = 0, recordingStartTime = System.currentTimeMillis();
    private static final String RECORDING_TIME_LIMIT_NO_LIMIT = "No limit";
    private static final String[] RECORDING_TIME_LIMIT_PRESETS = {
        RECORDING_TIME_LIMIT_NO_LIMIT,
        "1m", "10m", "30m", "1h", "3h", "12h", "24h", "1d", "7d", "14d", "30d"
    };
    private static final PeriodFormatter RECORDING_TIME_LIMIT_FORMATTER = new PeriodFormatterBuilder()
            .appendDays().appendSuffix("d")
            .appendSeparator(" ")
            .appendHours().appendSuffix("h")
            .appendSeparator(" ")
            .appendMinutes().appendSuffix("m")
            .appendSeparator(" ")
            .appendSeconds().appendSuffix("s")
            .appendSeparator(" ")
            .appendMillis()
            .toFormatter();
    /** Cached overlay for recording time limit; refreshed at most once per second. */
    private volatile String recordingTimeLimitOverlayText = null;
    private volatile long recordingTimeLimitOverlayLastMs = 0;
    private boolean recordFilteredEventsEnabled = prefs.getBoolean("AEViewer.logFilteredEventsEnabled", false);
    /** Recording format version string, e.g. {@code "4.0"} or {@code "2.0"}. */
    private String recordingDataFileVersion = prefs.get("AEViewer.loggingDataFileVersion",
            AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4);
    /** AEDAT-4 {@link net.sf.jaer.eventio.aedat4.dv.CompressionType} (default LZ4). */
    private int aedat4Compression = prefs.getInt("AEViewer.aedat4Compression",
            net.sf.jaer.eventio.aedat4.dv.CompressionType.LZ4);
    private DynamicFontSizeJLabel statisticsLabel;
    private boolean filterFrameBuilt = false; // flag to signal that the frame should be rebuilt when initially shown or when chip is changed
    private JaerUpdaterFrame jaerUpdaterFrame = null;
    /** Nonmodal File/Show file info window; reused while this viewer is open. */
    private JFrame fileInfoDialog;
    private JTextArea fileInfoTextArea;
    /** Last File → Save As output, for File info input/output compression summary. */
    private File lastSaveAsOutputFile;
    private String lastSaveAsSourceFileInfo;
    /** Nonmodal File/Preferences dialog; reused while this viewer is open. */
    private AEViewerPreferencesDialog preferencesDialog;
    /** Nonmodal Help → Quick help / Shortcuts window (F1). */
    private AEViewerQuickHelpFrame quickHelpFrame;

    private boolean rememberLastInterface = prefs.getBoolean("rememberLastInterface", true);
    /** Prefs key: {@code x} / File → Exit quits jAER instead of closing only this window. */
    public static final String PREF_EXIT_COMPLETELY_WITH_X = "AEViewer.exitCompletelyWithX";
    /** Prefs key: user has already chosen {@link #PREF_EXIT_COMPLETELY_WITH_X} (dialog or Preferences). */
    public static final String PREF_EXIT_COMPLETELY_WITH_X_CHOSEN = "AEViewer.exitCompletelyWithXChosen";
    /** False for File→New and other extra windows: WAITING must not grab leftover cameras. */
    private volatile boolean autobindOnWaiting = true;
    private String rememberLastInterfaceDeviceID = null;
    /** USB serial from a successful open, if the device exposes one. */ 
    private String rememberLastInterfaceSerial = null;
    /** Session keys for live USB chip-offer dialogs already shown this run. */
    private final java.util.HashSet<String> liveChipOfferPromptedKeys = new java.util.HashSet<>();

    private AEChip chip;
    /**
     * The default AEChip class.
     */
    public static String DEFAULT_CHIP_CLASS = Davis346red.class.getName();
    /**
     * The array list of default available AEChip classes pre-loaded into AEChip
     * menu
     */
    public static String[] DEFAULT_CHIP_CLASS_NAMES = {
        DEFAULT_CHIP_CLASS,
        DVS128.class.getName(),
        DAVIS240C.class.getName(),
        Davis346red.class.getName(),
        Davis346redColor.class.getName(),
        Davis346blue.class.getName(),
        SciDVS.class.getName(),
        DVXplorer.class.getName(),
        DVXplorerMicro.class.getName(),
        //        CochleaAMS1c.class.getName(),
        DVS640.class.getName(),
        NRVS5KRC1S.class.getName(),
        PropheseeIMX636HD.class.getName(),
        DVS1280x720SD.class.getName()
    };
    /**
     * The class name of the aeChipClass
     */
    private String aeChipClassName = null;
    /**
     * The class we are displaying - this is the root object for practically
     * everything display in an AEViewer.
     */
    protected Class aeChipClass = null;
    //    WindowSaver windowSaver;
    private JAERViewer jaerViewer;
    // blockingQueue input
    private ArrayBlockingQueue blockingQueueInput = null;
    private boolean blockingQueueInputEnabled = false;
    private ArrayBlockingQueue blockingQueueOutput = null;
    private boolean blockingQueueOutputEnabled = false;
    // unicast dataqgram data xfer
    private volatile AEUnicastOutput unicastOutput = null;
    private volatile AEUnicastInput unicastInput = null;
    private boolean unicastInputEnabled = false, unicastOutputEnabled = false;
    private boolean blankDeviceMessageShown = false; // flags that we have warned about blank device, don't show message again
    AEViewerLoggingHandler loggingHandler;
    private RemoteControl remoteControl = null; // TODO move to JAERViewer
    private int aeFileInputStreamTimestampResetBitmask = prefs.getInt("AEViewer.aeFileInputStreamTimestampResetBitmask", 0);
    private AePlayerAdvancedControlsPanel playerControls;
    private static boolean showedSkippedPacketsRenderingWarning = false;
    /** Live ARS max saved when entering file playback; restored when leaving PLAYBACK. */
    private int adaptiveRenderSkipMaxBeforePlayback = -1;
    /** True when live USB acquisition was paused for file playback (resume on stopPlayback). */
    private boolean eventAcquisitionPausedForPlayback;
    /**
     * Set while a data file is being opened so ViewLoop must not call
     * {@link #openAEMonitor()} (USB open can block for a long time and miss PLAYBACK).
     */
    private volatile boolean suppressHardwareOpen;
    /** Max wait for {@link HardwareInterface#close()} from UI actions (NRV LibUsb can hang). */
    private static final long HARDWARE_CLOSE_TIMEOUT_MS = 3000L;
    /**
     * ViewLoop join of {@code jaer-hw-close} before the next {@code aemon.open()}.
     * Prophesee ISSD stop/destroy is many Treuzell writes and often exceeds
     * {@link #HARDWARE_CLOSE_TIMEOUT_MS} (EVK4 → Davis 8:12:14).
     */
    private static final long HARDWARE_CLOSE_JOIN_MS = 20_000L;
    /** In-flight {@code jaer-hw-close}; next {@link #openAEMonitor()} joins this first. */
    private volatile Thread hardwareCloseThread;
    /** Device the current {@link #hardwareCloseThread} is closing. */
    private volatile HardwareInterface hardwareCloseTarget;
    /**
     * Closer that stops every open USB interface before a bus reset. Shared
     * libusb context: a sibling AEReader still in native USB makes the next
     * IN queue fail. Every viewer joins this before {@code open()}.
     */
    private static volatile Thread usbBusResetCloser;
    /** {@code jaer-aemon-open} still in native USB after timeout; do not {@code close()}. */
    private volatile HardwareInterface abandonedHungHardware;
    private volatile Thread hardwareOpenThread;
    private volatile HardwareInterface hardwareOpenTarget;
    /**
     * Interface menu is detaching/binding a camera. ViewLoop must not call
     * {@link #openAEMonitor()} until bind finishes; {@link #interruptViewloop()}
     * after that must not unbind the camera that is still opening.
     */
    private volatile boolean hardwareSwitchInProgress;
    /**
     * Max wait for worker-thread {@code aemon.open()} on FX3/Davis (claim is fast;
     * biases are sent off-thread).
     */
    /** Wait for this camera's {@code open()} plus config after the USB open
     * serializer is held. Sibling cameras queue; they do not overlap libusb. */
    private static final long HARDWARE_OPEN_WAIT_MS = 25_000L;
    /** Prophesee ISSD includes ~2.5 s sleeps plus many USB register writes. */
    private static final long HARDWARE_OPEN_WAIT_PROPHESEE_MS = 45_000L;
    /**
     * AEDAT-4 EVTS stream chosen in {@link #ensureChipCompatibleWithRecording(File)}
     * for multi-camera files; consumed by {@link AEChip#constuctFileInputStream}.
     */
    private Integer pendingAedat4EventStreamId;
    private boolean suppressAdaptiveRenderSkipMenuSync;
    public static final float FPS_LOWPASS_FILTER_TIMECONSTANT_MS = 300;
    private final int defaultDismissTimeout = ToolTipManager.sharedInstance().getDismissDelay();
    /** How long File → Remote HTML tooltips stay visible while the pointer is over the item. */
    private static final int NETWORK_MENU_TOOLTIP_DISMISS_MS = 60_000;
    
    // Actions
    FrameRateDecreaseAction frameRateDecreaseAction=new FrameRateDecreaseAction();
    FrameRateIncreaseAction frameRateIncreaseAction=new FrameRateIncreaseAction();

    /**
     * Constructs a new AEViewer using a default AEChip.
     *
     * @param jAERViewer the containing top level JAERViewer
     */
    public AEViewer(JAERViewer jAERViewer) {
        this(jAERViewer, null);
    }

    /**
     * Constructs a new instance and then sets class name of device to show in
     * it
     *
     * @param jaerViewer the manager of all viewers
     * @param chipClassName the AEChip to use
     */
    public AEViewer(JAERViewer jaerViewer, String chipClassName) {
        loggingHandler = new AEViewerLoggingHandler(this); // handles log messages globally
        loggingHandler.getSupport().addPropertyChangeListener(this); // logs to Console handler in AEViewer
        Logger.getLogger("").addHandler(loggingHandler);

        log.info("AEViewer starting up...");

        frameRater = new FrameRater(); // needs preferences, so make it here
        // log prefs info to debug location of prefs
        // unfortunately this returns null always, seems no way to find out
//        log.info(String.format("Preferences storage is located at %s",System.getProperty("java.util.prefs.userRoot")));
        if (chipClassName == null) {
            aeChipClassName = VendorPrefsMigration.migrateChipClassName(
                    prefs.get("AEViewer.aeChipClassName", DEFAULT_CHIP_CLASS));
        } else {
            aeChipClassName = VendorPrefsMigration.migrateChipClassName(chipClassName);
        }
        if (!aeChipClassName.equals(prefs.get("AEViewer.aeChipClassName", DEFAULT_CHIP_CLASS))) {
            prefs.put("AEViewer.aeChipClassName", aeChipClassName);
        }
        log.info("AEChip class name is " + aeChipClassName);
        try {
            //                log.info("getting class for "+aeChipClassName);
            aeChipClass = FastClassFinder.forName(getAeChipClassName()); // throws exception if class not found
            if (java.lang.reflect.Modifier.isAbstract(aeChipClass.getModifiers())) {
                log.warning(aeChipClass + " is abstract, setting chip class to default " + DEFAULT_CHIP_CLASS);
                setAeChipClassName(DEFAULT_CHIP_CLASS);
                aeChipClass = FastClassFinder.forName(getAeChipClassName());
            }
        } catch (ClassNotFoundException e) {
            handleAEChipClassNotAvailable();
        } catch (NoClassDefFoundError err) {
            handleAEChipClassNotAvailable();
        }
        setLocale(Locale.US); // to avoid problems with other language support in JOGL
        //        try {
        //            UIManager.setLookAndFeel(
        //                    UIManager.getCrossPlatformLookAndFeelClassName());
        ////            UIManager.setLookAndFeel(new WindowsLookAndFeel());
        //        } catch (Exception e) {
        //            log.warning(e.getMessage());
        //        }
        setName("AEViewer");

        aePlayer = new AEPlayer(this);
        playerControls = new AePlayerAdvancedControlsPanel(this);

        initComponents();
        updateExitMenuTooltip();
        initRosOutputRemoteMenu();
        initDnnSharedMemoryRemoteMenu();
        initOpenCvOutputRemoteMenu();
        remoteMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                bindRemoteOutputMenuItems();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        applyNetworkMenuDescriptionTooltips();
        // Esc cancels queued jog even when focus is on the heavyweight GL canvas
        // (menu accelerators alone are not always delivered in that case).
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_ESCAPE) {
                    return false;
                }
                Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
                if (active != AEViewer.this) {
                    return false;
                }
                if ((getPlayMode() != PlayMode.PLAYBACK) || (getAePlayer() == null) || !getAePlayer().isJogOccurring()) {
                    return false;
                }
                getAePlayer().cancelJog();
                e.consume();
                return true;
            }
        });
        // Temporary: Ctrl+Shift+F12 tests uncaught-exception / native-crash issue reporting.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_F12
                        || !e.isControlDown() || !e.isShiftDown()) {
                    return false;
                }
                Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
                if (active != AEViewer.this) {
                    return false;
                }
                JaerIssueReporter.offerCrashTest(AEViewer.this);
                e.consume();
                return true;
            }
        });
        // Ctrl+Shift+U: Interface → Refresh. Menu accelerators are not always delivered
        // when the heavyweight GL canvas has focus; Windows has no libusb hotplug.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_U
                        || !e.isControlDown() || !e.isShiftDown() || e.isAltDown()) {
                    return false;
                }
                Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
                if (active != AEViewer.this) {
                    return false;
                }
                refreshInterfaceMenuItem.doClick();
                e.consume();
                return true;
            }
        });
        // Windows has no libusb hotplug: clicking the viewer restarts 1 s WAITING
        // USB scans so a camera plugged in while jAER was in the background is found.
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                onViewerWindowGainedFocus();
            }
        });
        setupAdaptiveRenderSkippingMenu();
        setFocusTraversalKeysEnabled(false); // enable TAB key for menus - doesn't work

        setIconImage(new javax.swing.ImageIcon(getClass().getResource(JaerConstants.ICON_IMAGE_MAIN)).getImage());

        playerControlPanel.add(playerControls, BorderLayout.NORTH);
        this.jaerViewer = jaerViewer;
        if (jaerViewer != null) {
            // File → New (and other extra windows) wait for Interface; only
            // session restore / first window autobind leftover cameras.
            autobindOnWaiting = jaerViewer.isRestoringSessionViewers()
                    || jaerViewer.getViewers().isEmpty();
            // all stuff having to do with synchronizing player buttons here, binding components
            // TODO rework binding of jAERViewer player, AEViewer player, and player GUI. The whole MVC idea is too convoluted now.
            jaerViewer.addViewer(this); // register ourselves; assigns AEViewer-N name for WindowSaver
            if (!autobindOnWaiting) {
                nullInterface = true;
                log.info(getViewerWindowLabel()
                        + " waiting for Interface menu (not autobinding leftover cameras)");
            }
            loadRememberedInterfaceBinding();
            if (jaerViewer.getSyncPlayer() != null) {
                // now bind player control panel to SyncPlayer and bind jaer sync player to player control panel.
                playerControls.addPropertyChangeListener(jaerViewer.getSyncPlayer());
                jaerViewer.getSyncPlayer().getSupport().addPropertyChangeListener(playerControls);
                if (jaerViewer.isSyncEnabled()) {
                    playerControls.setAePlayer(jaerViewer.getSyncPlayer());
                }
            }
        } else {
            autobindOnWaiting = true;
            setViewerInstanceIndex(0);
            loadRememberedInterfaceBinding();
        }
        validate();

        statisticsLabel = new DynamicFontSizeJLabel();
        //        statisticsLabel.setFont(new java.awt.Font("Bitstream Vera Sans Mono 11 Bold", 0, 8));
        statisticsLabel.setToolTipText("Time slice/Absolute time, NumEvents/NumFiltered, events/sec, Graphics rendering frame rate desired/achieved, Time speedup X, delay after frame, color scale");
        statisticsPanel.add(statisticsLabel);
        PropertyChangeListener[] list = statisticsLabel.getPropertyChangeListeners();
        for (PropertyChangeListener p : list) {
            statisticsLabel.removePropertyChangeListener(p);
        }

        //        HardwareInterfaceException.addExceptionListener(this);
        int n = menuBar.getMenuCount();
        for (int i = 0; i < n; i++) {
            JMenu m = menuBar.getMenu(i);
            m.getPopupMenu().setLightWeightPopupEnabled(false);
        }
        graphicsSubMenu.getPopupMenu().setLightWeightPopupEnabled(false);
        remoteMenu.getPopupMenu().setLightWeightPopupEnabled(false); // make remote submenu heavy to show over glcanvas

        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false); // to show menu tips over GLCanvas
        ToolTipManager.sharedInstance().setInitialDelay(1500); // 1.5s so tooltips do not obscure menus while navigating

        statusTextField.addMouseListener(new MouseAdapter() {

            public void mouseEntered(MouseEvent me) {
                StringBuilder sb = new StringBuilder("<html>");
                for (String m : statusTextFieldMessages) {
                    if (m != null) {
                        sb.append("<br>").append(m.substring(0, Math.min(m.length(), 80)));
                    }
                }
                statusTextField.setToolTipText(sb.toString());

                ToolTipManager.sharedInstance().setDismissDelay(5000);
            }

            public void mouseExited(MouseEvent me) {
                ToolTipManager.sharedInstance().setDismissDelay(defaultDismissTimeout);
            }
        });

        String lastFilePath = prefs.get("AEViewer.lastFile", "");
        lastFile = new File(lastFilePath);

        // Prefer ${java.io.tmpdir}/jaer over the install/repo folder (often read-only / NFS).
        String defaultRecordingFolderName = net.sf.jaer.util.JaerTmpdir.get().getAbsolutePath();
        log.info("using " + defaultRecordingFolderName + " as the defaultRecordingFolderName");
        // lastRecordingFolder starts off at user.dir which is startup folder "host/java" where .exe launcher lives
        lastRecordingFolder = new File(prefs.get("AEViewer.lastLoggingFolder", defaultRecordingFolderName));
        log.info("AEViewer.lastRecordingFolder=" + lastRecordingFolder);

        // exists()/isDirectory() can stall for minutes on a wedged Dropbox/NFS path.
        FileAccessTimeout.Kind recordingKind = FileAccessTimeout.kind(lastRecordingFolder);
        if (recordingKind != FileAccessTimeout.Kind.DIRECTORY) {
            log.warning("lastRecordingFolder " + lastRecordingFolder + " no good (" + recordingKind
                    + " within " + FileAccessTimeout.timeoutMs() + " ms), defaulting to " + defaultRecordingFolderName);
            lastRecordingFolder = new File(defaultRecordingFolderName);
            if (recordingKind == FileAccessTimeout.Kind.TIMEOUT) {
                try {
                    prefs.put("AEViewer.lastLoggingFolder", lastRecordingFolder.getCanonicalPath());
                } catch (IOException e) {
                    prefs.put("AEViewer.lastLoggingFolder", lastRecordingFolder.getAbsolutePath());
                }
            }
        }

        // recent files tracks recently used files *and* folders. recentFiles adds the anonymous listener
        // built here to open the selected file
        recentFiles = new RecentFiles(prefs, fileMenu, new ActionListener() {

            @Override
            @SuppressWarnings("LoggerStringConcat")
            public void actionPerformed(ActionEvent evt) {
                if ((evt.getModifiers() & ActionEvent.SHIFT_MASK) != 0) {
                    if (Desktop.isDesktopSupported()) {
                        log.info("opening folder for " + evt.getActionCommand());
                        try {
                            File f = new File(evt.getActionCommand());
                            if (FileAccessTimeout.isFile(f)) {
                                f = f.getParentFile();
                            }
                            Desktop.getDesktop().open(f);
                        } catch (IOException e) {
                            log.warning("Cannot show folder: " + e.toString());
                        }
                    }
                } else {
                    File f = new File(evt.getActionCommand());
                    //                log.info("opening "+evt.getActionCommand());
                    try {
                        openAedatInputFile(f);
                    } catch (Exception fnf) {
                        log.log(Level.WARNING, fnf.toString(), fnf);
                        recentFiles.removeFile(f);
                    }
                }
            }
        });

        // additional help
        try {
            addHelpURLItem(JaerConstants.HELP_URL_USER_GUIDE, "jAER user guide", "Opens the jAER user guide");
            JMenuItem quickHelpMenuItem = new JMenuItem(new AbstractAction("Quick help/Shortcuts") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggleQuickHelp();
                }
            });
            quickHelpMenuItem.setToolTipText("Mouse gestures and common keyboard shortcuts (F1 toggles)");
            quickHelpMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
            int userGuideIndex = -1;
            for (int i = 0; i < helpMenu.getMenuComponentCount(); i++) {
                Component c = helpMenu.getMenuComponent(i);
                if (c instanceof JMenuItem && "jAER user guide".equals(((JMenuItem) c).getText())) {
                    userGuideIndex = i;
                    break;
                }
            }
            helpMenu.insert(quickHelpMenuItem, userGuideIndex < 0 ? 1 : userGuideIndex + 1);
            addHelpURLItem(JaerConstants.HELP_URL_HELP_FORUM, "jAER help forum", "Opens the help forum.  Post your questions and look for answers there.");
            addHelpURLItem(JaerConstants.JAER_ISSUES, "Give feedback/File issue...",
                    "Opens the jAER GitHub Issues page to give feedback or file a bug");
            addHelpURLItem(JaerConstants.HELP_URL_JAER_HOME, "jAER project home", "jAER project home on Github");
//            addHelpURLItem(HELP_URL_JAVADOC_WEB, "jAER javadoc", "jAER online javadoc (probably out of date)");

//            addHelpItem(new JSeparator());
//            addHelpURLItem(pathToURL(HELP_USER_GUIDE_USB2_MINI), "USBAERmini2 board", "User guide for USB2AERmini2 AER monitor/sequencer interface board");
//            addHelpURLItem(pathToURL(HELP_USER_GUIDE_AER_CABLING), "AER protocol and cabling guide", "Guide to AER pin assignment and cabling for the Rome and CAVIAR standards");
//            addHelpURLItem(pathToURL("/devices/pcbs/ServoUSBPCB/ServoUSB.pdf"), "USB Servo board", "Layout and schematics for the USB servo controller board");
            addHelpItem(new JSeparator());
            addHelpURLItem(JaerConstants.HELP_URL_INIVATION_CAMERAS, "Inivation Cameras", "iniVation hardware product guides (DVXplorer, DAVIS, sync, connectors)");
            addHelpURLItem(JaerConstants.HELP_URL_PROPHESEE_CAMERAS, "Prophesee cameras", "Prophesee / Sony event sensor technical docs (IMX636, GenX320)");
            addHelpURLItem(JaerConstants.HELP_URL_NRV_CAMERAS, "NRV cameras", "NRV DELTA / RC1S technical documentation (SDK, event format, products)");
            addHelpURLItem(JaerConstants.HELP_USER_GUIDE_URL_FLASHY, "Flashy reflashing utility help", "Guide for reflashing firmware");
            addHelpItem(new JSeparator());
            addHelpURLItem(JaerConstants.HELP_URL_EVENT_BASED_VISION_RESOURCES, "Event-Based Vision Resources",
                    "Community list of papers, workshops, datasets, code, and videos for event-based vision");
            JMenu sampleDataMenu = new JMenu("Sample data");
            sampleDataMenu.setToolTipText("Links to publicly available event camera sample recordings");
            sampleDataMenu.add(makeHelpURLMenuItem(JaerConstants.HELP_URL_DVS128_SAMPLE_DATA,
                    "DVS09 / DVS128 sample data",
                    "DVS09 DVS128 sample data files (Google Doc with download links)"));
            sampleDataMenu.add(makeHelpURLMenuItem(JaerConstants.HELP_URL_DAVIS346_SAMPLE_DATA,
                    "DAVIS346 sample data (AEDAT-2)",
                    "DAVIS24 sample DAVIS346 recordings (mostly AEDAT-2.0) for exploring data and algorithm development"));
            sampleDataMenu.add(makeHelpURLMenuItem(JaerConstants.HELP_URL_AEDAT4_SAMPLE_DATA,
                    "AEDAT-4 / DV sample data",
                    "Open MIT-licensed DAVIS346 AEDAT-4 recordings with direct per-file downloads (soccer ball scenes)"));
            sampleDataMenu.add(makeHelpURLMenuItem(JaerConstants.HELP_URL_INIVATION_AEDAT4_DATA,
                    "Inivation AEDAT-4 data",
                    "iniVation AEDAT-4 samples; use dv-filestat -v to check XML 'source' for camera (e.g. mean_shift is DVXplorer)"));
            sampleDataMenu.add(makeHelpURLMenuItem(JaerConstants.HELP_URL_PROPHESEE_SAMPLE_DATA,
                    "Prophesee / Metavision sample data",
                    "Prophesee sample recordings and datasets (RAW EVT2/EVT3, HDF5, DAT) including EVK4 / IMX636"));
            addHelpItem(sampleDataMenu);
            addHelpItem(new JSeparator());
        } catch (Exception e) {
            log.warning("could register help item: " + e.toString());
        }

        // Do not rescan USB when other viewers are already LIVE (WinUSB getDeviceList
        // can stall streaming). First window of a session fills the cache.
        if (HardwareInterfaceFactory.instance().getCachedNumInterfacesAvailable() == 0) {
            HardwareInterfaceFactory.instance().buildInterfaceList();
        }
        buildInterfaceMenu();
        buildDeviceMenu();
        // Prefer remembered live AEChip before first GLCanvas so ViewLoop does not
        // immediately recreate/reparent OpenGL (crashes some Intel Arc drivers).
        maybeUseRememberedLiveChipAtStartup();
        // we need to do this after building device menu so that proper menu item radio button can be selected
//        cleanup(); // close sockets if they are open
        setAeChipClass(aeChipClass);

        playerControlPanel.setVisible(false);
        timestampResetBitmaskMenuItem.setText("Set timestamp reset bitmask... (currently 0x" + Integer.toHexString(aeFileInputStreamTimestampResetBitmask) + ")");
        setFocusable(true);
        requestFocus();

        fixRecordingControls();

        myDraggedFileDropTarget = new DropTarget(getImagePanel(), this); // add support for dragged file onto display, lost somehow. AEViewer is the listener via drag events

        // init menu items that are checkboxes to correct initial state
        viewActiveRenderingEnabledMenuItem.setSelected(isActiveRenderingEnabled());
        recordingPlaybackImmediatelyCheckBoxMenuItem.setSelected(isRecordingPlaybackImmediatelyEnabled());
        if (getRenderer() == null) {
            throw new NullPointerException("getRenderer() returns null for this AEChip " + chip);
        }
        acccumulateImageEnabledCheckBoxMenuItem.setSelected(getRenderer().isAccumulateEnabled());
//        autoscaleContrastEnabledCheckBoxMenuItem.setSelected(getRenderer().isAutoscaleEnabled());
        pauseRenderingCheckBoxMenuItem.setSelected(false);// not isPaused because aePlayer doesn't exist yet
        viewRenderBlankFramesCheckBoxMenuItem.setSelected(isRenderBlankFramesEnabled());
        recordFilteredEventsCheckBoxMenuItem.setSelected(recordFilteredEventsEnabled);
        enableFiltersOnStartupCheckBoxMenuItem.setSelected(enableFiltersOnStartup);
        setJogNCount.setText("Set forward/reverse jog packet count N... (currently " + getAePlayer().getJogPacketCount() + ")");

        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.setSelected(prefs.getBoolean("AEViewer.checkNonMonotonicTimeExceptionsEnabled", true));
        syncAdaptiveRenderSkipMenuFromRenderer();

        viewLoop = new ViewLoop();
        LibUsbHotplug.addListener(usbHotplugListener);
        viewLoop.start();

        if (RemoteControl.isEnabledPref()) {
            try {
                int remoteControlPort = RemoteControl.getViewerPortPref();
                int lastPort = remoteControlPort + 10;
                while (remoteControlPort <= lastPort) {
                    try {
                        remoteControl = new RemoteControl(remoteControlPort);
                    } catch (SocketException e) {
                        remoteControlPort++;
                        continue;
                    }
                    break;
                }
                if (remoteControl != null) {
                    remoteControl.addCommandListener(this, REMOTE_START_RECORDING + " <filename>", "starts recording ae data to a file");
                    remoteControl.addCommandListener(this, REMOTE_STOP_RECORDING, "stops recording ae data to a file");
                    remoteControl.addCommandListener(this, REMOTE_TOGGLE_SYNCHRONIZED_RECORDING, "starts synchronized recording ae data to a set of files with aeidx filename automatically timestamped");
                    remoteControl.addCommandListener(this, REMOTE_START_LOGGING + " <filename>", "alias of startrecording");
                    remoteControl.addCommandListener(this, REMOTE_STOP_LOGGING, "alias of stoprecording");
                    remoteControl.addCommandListener(this, REMOTE_TOGGLE_SYNCHRONIZED_LOGGING, "alias of togglesyncrecording");
                    remoteControl.addCommandListener(this, REMOTE_ZERO_TIMESTAMPS, "zeros timestamps on all AEViewers");
                    remoteControl.addCommandListener(this, REMOTE_OPEN_FILE + " <filename>", "<filename> open file for playback");
                    remoteControl.addCommandListener(this, REMOTE_PAUSE, "pause player");
                    remoteControl.addCommandListener(this, REMOTE_PLAY, "resume player");
                    remoteControl.addCommandListener(this, REMOTE_REWIND, "rewind player");
                    remoteControl.addCommandListener(this, REMOTE_SET_MARK_IN + " <timestamp_us>", "set mark IN timestamp");
                    remoteControl.addCommandListener(this, REMOTE_SET_MARK_OUT + " <timestamp_us>", "set mark OUT timestamp");
                    log.info("created " + remoteControl + " for remote control of some AEViewer functions");
                }
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        } else {
            log.fine("AEViewer RemoteControl disabled (Preferences " + RemoteControl.PREF_ENABLED + "=false)");
        }
        setTitleAccordingToState();

    }

    private void handleAEChipClassNotAvailable() {
        log.warning(getAeChipClassName() + " class not found or not a valid AEChip, setting preferred chip class to default " + DEFAULT_CHIP_CLASS + " and using that class");
        prefs.put("AEViewer.aeChipClassName", DEFAULT_CHIP_CLASS);
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            log.warning("couldnt' flush the preferences to save preferred chip class: " + ex.toString());
        }
        try {
            aeChipClass = FastClassFinder.forName(DEFAULT_CHIP_CLASS);
        } catch (ClassNotFoundException ex) {
            log.warning("could not even find the default chip class " + DEFAULT_CHIP_CLASS + ", exiting");
            System.exit(1);
        }
    }

    /**
     * Closes hardware interface and network sockets. Register all cleanup here
     * for other classes, e.g. Chip classes that open sockets.
     */
    private void cleanup() {
        cleanup(false);
    }

    /**
     * @param joinUsbClose true when this AEViewer is exiting (window close /
     * File → Exit) so leftover windows do not get ACCESS. Chip switch must
     * pass false — that path already closes USB off the EDT.
     */
    private void cleanup(boolean joinUsbClose) {
        log.fine("cleanup()");
        // Do not remove the libusb hotplug listener here. setAeChipClass() calls
        // cleanup() on every camera/chip switch; dropping the listener left
        // WAITING deaf to ARRIVED/LEFT (jAER-0.log 4:35:05 "no AEViewer listener").
        stopRecording(true); // in case recording, make sure we give chance to save file
        // Close the playback file without starting USB; aemon.close() follows.
        if (aePlayer != null && !suppressHardwareOpen) {
            aePlayer.stopPlayback(false);
        }
        if (aemon != null) {
            log.fine("closing device " + aemon + (joinUsbClose
                    ? " (join so leftover viewers do not get ACCESS)"
                    : " async (USB close can hang native)"));
            final AEMonitorInterface mon = aemon;
            aemon = null;
            long joinMs = (mon instanceof PropheseeHardwareInterface)
                    ? HARDWARE_CLOSE_JOIN_MS : HARDWARE_CLOSE_TIMEOUT_MS;
            Thread t = new Thread(() -> {
                try {
                    mon.close();
                } catch (Exception e) {
                    log.warning("aemon.close in cleanup: " + e);
                }
            }, "jaer-async-aemon-close");
            t.setDaemon(true);
            hardwareCloseTarget = (HardwareInterface) mon;
            hardwareCloseThread = t;
            t.start();
            if (joinUsbClose) {
                try {
                    t.join(joinMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (t.isAlive()) {
                    log.warning("cleanup: USB close still running after " + joinMs
                            + " ms; remaining viewers may see ACCESS until native close finishes");
                } else {
                    log.info("cleanup: closed " + mon);
                }
            }
        }

        if (unicastInput != null) {
            log.fine("closing unicast input" + unicastInput);
            unicastInput.close();
        }
        if (unicastOutput != null) {
            log.fine("closing unicastOutput " + unicastOutput);
            unicastOutput.close();
        }
        if (chip != null) {
            log.fine("Running cleanup() for " + chip);
            chip.cleanup();
        }
        log.info("end of AEViewer.cleanup(). Please wait for shutdown hook to finish running...");

    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Windows")) {
            return true;
        } else {
            return false;
        }
    }
    private int showMultipleInterfacesMessageCount = 0;
    private int lastWelcomeInterfaceCount = -1;

    private long lastInterfaceCheckTime = 0;
    /** Last libusb ARRIVED/LEFT; empty scans retry until this window elapses. */
    private volatile long lastUsbHotplugTimeMs = 0;
    /** Fallback full scan when libusb hotplug is active (events handle the fast path). */
    private static final long HOTPLUG_FALLBACK_CHECK_INTERVAL_MS = 15_000;
    /** After ARRIVED, {@code getDeviceList} can still be empty; retry WAITING scans this long. */
    private static final long HOTPLUG_EMPTY_SCAN_RETRY_MS = 2_000;
    /** Windows has no libusb hotplug; WAITING discovery decays 1 s → 3 s → 15 s. */
    private final WindowsUsbPollSchedule windowsUsbPoll = new WindowsUsbPollSchedule();
    private final LibUsbHotplug.Listener usbHotplugListener = this::onLibUsbHotplug;

    /**
     * Bind a remembered camera (tmpdir map) or the sole interface when this is
     * the only viewer. Multiple AEViewers reopen the cameras they last used.
     */
    private void openHardwareIfNonambiguous() {
        // TODO doesn't open an AEMonitor if there is a ServoInterface plugged in.
        // Should check to see if there is only 1 AEMonitorInterface, but this check is not possible currently without opening the interface.
        //        HardwareInterfaceFactory.instance().buildInterfaceList(); // TODO this burns up a lot of heap memory because the PnpListeners
        // check to see if null interface required instead
        if (!SessionCameraOpenCoordinator.mayOpenUsb(this)) {
            return;
        }
        if (nullInterface) {
            log.fine("openHardwareIfNonambiguous skipped (nullInterface; Interface → None or failed ACCESS open)");
            return;
        }
        final boolean dirty = HardwareInterfaceFactory.instance().isUsbEnumerationDirty();
        final long now = System.currentTimeMillis();
        final long dtCheck = now - lastInterfaceCheckTime;
        final boolean windowsPoll = !LibUsbHotplug.isSupported();
        final long intervalMs;
        if (windowsPoll) {
            intervalMs = windowsUsbPoll.intervalMs(now);
            windowsUsbPoll.logPhaseIfChanged(now, log);
        } else {
            intervalMs = HOTPLUG_FALLBACK_CHECK_INTERVAL_MS;
        }

        if (!dirty && dtCheck < intervalMs) {
            log.finer(String.format("Not checking for new devices because only %,d<%,d ms have elapsed since last check", dtCheck, intervalMs));
            return;
        }
        lastInterfaceCheckTime = now;
        if (LibUsbHotplug.isSupported()) {
            HardwareInterfaceFactory.instance().markUsbEnumerationDirty();
        }

        int ninterfaces = HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
        log.fine("openHardwareIfNonambiguous ninterfaces=" + ninterfaces + " dirtyWas=" + dirty);
        if (windowsPoll) {
            windowsUsbPoll.noteScanResult(usbDeviceFingerprint(ninterfaces), now, log);
        }
        if (ninterfaces == 0 && LibUsbHotplug.isSupported()
                && (System.currentTimeMillis() - lastUsbHotplugTimeMs) < HOTPLUG_EMPTY_SCAN_RETRY_MS) {
            lastInterfaceCheckTime = 0;
            HardwareInterfaceFactory.instance().markUsbEnumerationDirty();
            log.fine("hotplug scan found 0 devices; will retry on next WAITING tick");
        }
        boolean bound = bindRememberedInterfaceIfPossible(ninterfaces);
        if (!bound) {
            bound = bindUnambiguousInterfaceIfPossible(ninterfaces);
        }
        if (!bound && ninterfaces > 1 && (showMultipleInterfacesMessageCount++ % 100) == 0) {
            log.info("found " + ninterfaces + " hardware interfaces, choose one from Interface menu to connect");
        }
        if (getPlayMode() == PlayMode.WAITING && ninterfaces != lastWelcomeInterfaceCount) {
            lastWelcomeInterfaceCount = ninterfaces;
            SwingUtilities.invokeLater(this::showWelcomeOverlay);
        }
    }

    /** Sorted unopened labels for Windows poll-schedule reset; does not {@code LibUsb.open}. */
    private static String usbDeviceFingerprint(int ninterfaces) {
        if (ninterfaces <= 0) {
            return "none";
        }
        HardwareInterfaceFactory factory = HardwareInterfaceFactory.instance();
        String[] labels = new String[ninterfaces];
        for (int i = 0; i < ninterfaces; i++) {
            HardwareInterface hw = factory.getInterface(i);
            labels[i] = UsbIds.enumerationKey(hw);
        }
        Arrays.sort(labels);
        return String.join(", ", labels);
    }

    /** Windows-only: restart 1 s USB scans for 1 min (unplug / device-gone / focus). */
    private void resetWindowsUsbPoll(String reason) {
        if (LibUsbHotplug.isSupported()) {
            return;
        }
        lastInterfaceCheckTime = 0;
        windowsUsbPoll.reset(reason, System.currentTimeMillis(), log);
    }

    /**
     * Clicking back to the viewer (no Windows libusb hotplug) restarts 1 s
     * WAITING scans so a newly plugged camera is found without waiting 15 s.
     * USB enumeration stays off the EDT.
     */
    private void onViewerWindowGainedFocus() {
        if (LibUsbHotplug.isSupported()) {
            return;
        }
        if (!SessionCameraOpenCoordinator.mayOpenUsb(this)) {
            return;
        }
        resetWindowsUsbPoll("window focus gained");
        if (viewLoop != null && getPlayMode() == PlayMode.WAITING) {
            interruptViewloop();
        }
    }

    /**
     * Rebind this window's last USB camera from
     * {@link ViewerInterfaceBindingMap} (tmpdir file). Works with multiple
     * AEViewers and multiple devices. Does not {@code LibUsb.open}.
     */
    /** Serializes bind across ViewLoops so four restored windows do not grab the same camera. */
    private static final Object HARDWARE_CLAIM_LOCK = new Object();
    /**
     * Serializes native {@code open()}+config across AEViewers. The coordinator
     * only blocks USB during UI restore; this lock is the open serializer.
     */
    private static final ReentrantLock USB_OPEN_SERIAL_LOCK = new ReentrantLock(true);
    /** LIVE ticks where aemon was not open; drop to WAITING only after several. */
    private int liveOpenMisses;
    private static volatile String usbOpenSerialHolder;
    private boolean loggedStartupBindMiss;

    private boolean bindRememberedInterfaceIfPossible(int ninterfaces) {
        if (!autobindOnWaiting && !SessionCameraOpenCoordinator.hasOpenGrant(this)) {
            return false;
        }
        if (!isRememberLastInterface() || nullInterface || chip == null || ninterfaces < 1) {
            return false;
        }
        if (chip.getHardwareInterface() != null) {
            return false;
        }
        if (NetworkChip.class.isInstance(chip)) {
            return false;
        }
        HardwareInterface match;
        String reason;
        synchronized (HARDWARE_CLAIM_LOCK) {
            if (chip.getHardwareInterface() != null) {
                return false;
            }
            match = findAutobindMatch(ninterfaces);
            reason = lastAutobindReason;
            if (match == null) {
                logBindMiss(reason);
                return false;
            }
        }
        // Chip chooser must not hold the claim lock (EDT dialog).
        ensureChipCompatibleWithLiveDevice(match);
        synchronized (HARDWARE_CLAIM_LOCK) {
            if (chip.getHardwareInterface() != null) {
                return true;
            }
            if (hardwareTakenByOtherViewer(match)) {
                match = firstUnusedMatchingThisChip(ninterfaces);
                if (match == null) {
                    logBindMiss("camera taken before bind; no unused match for "
                            + chip.getClass().getSimpleName());
                    return false;
                }
                reason = "first unused after race " + chip.getClass().getSimpleName();
            }
            bindLiveHardwareIfCompatible(match, getViewerWindowLabel() + " autobind (" + reason + ") ");
            return chip.getHardwareInterface() != null;
        }
    }

    private volatile String lastAutobindReason;

    private HardwareInterface findAutobindMatch(int ninterfaces) {
        ViewerInterfaceBindingMap.Binding remembered = ViewerInterfaceBindingMap.get(viewerInstanceIndex);
        HardwareInterfaceFactory factory = HardwareInterfaceFactory.instance();
        HardwareInterface match = null;
        HardwareInterface uniqueVidPid = null;
        int vidPidHits = 0;
        String wantVidPid = remembered == null ? null : remembered.vidPid();
        lastAutobindReason = remembered == null
                ? "no map entry in " + ViewerInterfaceBindingMap.file().getName()
                : null;
        for (int i = 0; i < ninterfaces; i++) {
            HardwareInterface hw = factory.getInterface(i);
            if (hw == null || hardwareTakenByOtherViewer(hw) || UDPInterface.class.isInstance(hw)) {
                continue;
            }
            if (skipClassicDvxAutobind(hw)) {
                continue;
            }
            if (remembered != null && remembered.matches(hw)) {
                match = hw;
                break;
            }
            if (wantVidPid != null && wantVidPid.equals(ViewerInterfaceBindingMap.vidPid(UsbIds.enumerationKey(hw)))) {
                vidPidHits++;
                uniqueVidPid = hw;
            }
        }
        if (match != null) {
            lastAutobindReason = "map " + remembered.label;
            return match;
        }
        if (wantVidPid != null && vidPidHits == 1) {
            lastAutobindReason = "sole unused " + wantVidPid;
            return uniqueVidPid;
        }
        match = firstUnusedMatchingThisChip(ninterfaces);
        if (match != null) {
            lastAutobindReason = remembered == null
                    ? "first unused camera matching " + chip.getClass().getSimpleName()
                    : "remembered taken; unused " + chip.getClass().getSimpleName();
            return match;
        }
        // Same-family leftover only (never EVK4 onto a DVS128 window).
        match = firstUnusedNotReservedForOtherViewer(ninterfaces);
        if (match != null) {
            lastAutobindReason = "leftover same-family " + UsbIds.enumerationKey(match);
            return match;
        }
        lastAutobindReason = remembered == null
                ? "no unused camera matching " + chip.getClass().getSimpleName()
                : "remembered " + remembered.label + " not free among "
                        + ninterfaces + " devices";
        return null;
    }

    /**
     * Classic FX3 DVX SPI hangs on WinUSB. Autobind skips it; Interface may
     * still select it. A 1-viewer map pointing at classic DVX must not block
     * DVS128 / Mini / Davis.
     */
    private boolean skipClassicDvxAutobind(HardwareInterface hw) {
        if (SessionCameraOpenCoordinator.hasOpenGrant(this)) {
            return false;
        }
        return SessionCameraOpenCoordinator.isClassicDvxHardware(hw);
    }

    private void logBindMiss(String reason) {
        log.fine(getViewerWindowLabel() + " autobind miss: " + reason);
        if (!loggedStartupBindMiss) {
            loggedStartupBindMiss = true;
            log.info(getViewerWindowLabel() + " did not autobind: " + reason);
        }
    }

    /**
     * First enumerated camera this restored AEChip declares and no other
     * viewer has claimed. Two DVX windows each take the next unused DVX.
     */
    private HardwareInterface firstUnusedMatchingThisChip(int ninterfaces) {
        if (chip == null || !LiveDeviceChipDetector.declaresAnyUsbDevices(chip.getClass())) {
            return null;
        }
        HardwareInterfaceFactory factory = HardwareInterfaceFactory.instance();
        for (int i = 0; i < ninterfaces; i++) {
            HardwareInterface hw = factory.getInterface(i);
            if (hw == null || hardwareTakenByOtherViewer(hw) || UDPInterface.class.isInstance(hw)) {
                continue;
            }
            if (skipClassicDvxAutobind(hw)) {
                continue;
            }
            if (hardwareReservedForOtherViewer(hw)) {
                continue;
            }
            if (LiveDeviceChipDetector.currentChipMatches(chip.getClass(), hw)) {
                return hw;
            }
        }
        return null;
    }

    /**
     * Same-family leftover: unused camera matching this AEChip that is not
     * another window's remembered camera. Never binds a different family.
     */
    private HardwareInterface firstUnusedNotReservedForOtherViewer(int ninterfaces) {
        if (chip == null || !LiveDeviceChipDetector.declaresAnyUsbDevices(chip.getClass())) {
            return null;
        }
        HardwareInterfaceFactory factory = HardwareInterfaceFactory.instance();
        for (int i = 0; i < ninterfaces; i++) {
            HardwareInterface hw = factory.getInterface(i);
            if (hw == null || hardwareTakenByOtherViewer(hw) || UDPInterface.class.isInstance(hw)) {
                continue;
            }
            if (skipClassicDvxAutobind(hw)) {
                continue;
            }
            if (hardwareReservedForOtherViewer(hw)) {
                continue;
            }
            if (!LiveDeviceChipDetector.currentChipMatches(chip.getClass(), hw)) {
                continue;
            }
            return hw;
        }
        return null;
    }

    private boolean hardwareReservedForOtherViewer(HardwareInterface hw) {
        if (jaerViewer == null || hw == null) {
            return false;
        }
        for (AEViewer other : jaerViewer.getViewers()) {
            if (other == this) {
                continue;
            }
            ViewerInterfaceBindingMap.Binding b = ViewerInterfaceBindingMap.get(other.getViewerInstanceIndex());
            if (b != null && b.matches(hw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Viewer that already has this USB camera on its chip (this window or
     * another). Match is bus/addr, then VID:PID+topology from enumeration
     * keys (does not open the USB device).
     */
    private AEViewer viewerClaimingHardware(HardwareInterface hw) {
        if (jaerViewer == null || hw == null) {
            return null;
        }
        for (AEViewer other : jaerViewer.getViewers()) {
            if (other.getChip() == null) {
                continue;
            }
            HardwareInterface taken = other.getChip().getHardwareInterface();
            if (taken == null) {
                continue;
            }
            if (UsbIds.samePhysicalDevice(hw, taken)) {
                return other;
            }
            String keyHw = UsbIds.enumerationKey(hw);
            String keyTaken = UsbIds.enumerationKey(taken);
            String vp1 = ViewerInterfaceBindingMap.vidPid(keyHw);
            String vp2 = ViewerInterfaceBindingMap.vidPid(keyTaken);
            String ba1 = ViewerInterfaceBindingMap.busAddr(keyHw);
            String ba2 = ViewerInterfaceBindingMap.busAddr(keyTaken);
            if (vp1 != null && vp1.equals(vp2) && ba1 != null && ba1.equals(ba2)) {
                return other;
            }
        }
        return null;
    }

    private boolean hardwareTakenByOtherViewer(HardwareInterface hw) {
        AEViewer owner = viewerClaimingHardware(hw);
        return owner != null && owner != this;
    }

    /** Title suffix, e.g. {@code AEViewer #1} ({@code viewerInstanceIndex + 1}). */
    public String getViewerWindowLabel() {
        return "AEViewer #" + (viewerInstanceIndex + 1);
    }

    /**
     * Interface / Welcome label: family + (#n), plus which window already
     * opened this camera when that is not this viewer.
     */
    private String interfaceChoiceLabel(HardwareInterface hw, int index) {
        String base = String.format("%s (#%d)", interfaceMenuLabel(hw), index);
        AEViewer owner = viewerClaimingHardware(hw);
        if (owner != null && owner != this) {
            return base + " — " + owner.getViewerWindowLabel();
        }
        if (hw.isOpen() && (chip == null || !UsbIds.samePhysicalDevice(hw, chip.getHardwareInterface()))) {
            return base + " — already open";
        }
        return base;
    }

    /**
     * Refresh Welcome overlays on other WAITING viewers so Interface claims
     * stay aligned across windows (not a thread lock).
     */
    private void notifyOtherViewersOfHardwareClaimChange() {
        if (jaerViewer == null) {
            return;
        }
        Runnable refresh = () -> {
            for (AEViewer other : jaerViewer.getViewers()) {
                if (other == this || other.getPlayMode() != PlayMode.WAITING) {
                    continue;
                }
                HardwareInterface otherHw = other.getChip() == null
                        ? null : other.getChip().getHardwareInterface();
                if (otherHw != null && otherHw.isOpen()) {
                    continue;
                }
                other.showWelcomeOverlay();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            refresh.run();
        } else {
            SwingUtilities.invokeLater(refresh);
        }
    }

    private void loadRememberedInterfaceBinding() {
        ViewerInterfaceBindingMap.Binding b = ViewerInterfaceBindingMap.get(viewerInstanceIndex);
        if (b == null) {
            return;
        }
        rememberLastInterfaceSerial = b.serial.isBlank() ? null : b.serial;
        rememberLastInterfaceDeviceID = rememberLastInterfaceSerial != null ? rememberLastInterfaceSerial : b.label;
    }

    /**
     * Write this window's current USB identity to the tmpdir map.
     */
    public void persistRememberedInterfaceBinding() {
        if (!isRememberLastInterface() || chip == null) {
            return;
        }
        HardwareInterface hw = chip.getHardwareInterface();
        if (hw instanceof USBInterface) {
            rememberUsbIdentity(hw, rememberLastInterfaceSerial);
        }
    }

    private void rememberUsbIdentity(HardwareInterface hw, String serial) {
        if (!isRememberLastInterface() || !(hw instanceof USBInterface)) {
            return;
        }
        String label = UsbIds.enumerationKey(hw);
        String ser = (serial == null || serial.isBlank()) ? null : serial;
        rememberLastInterfaceSerial = ser;
        rememberLastInterfaceDeviceID = ser != null ? ser : label;
        String chipName = chip != null ? chip.getClass().getName() : "";
        ViewerInterfaceBindingMap.put(viewerInstanceIndex, label, ser, chipName);
    }

    /**
     * Bind the sole enumerated interface (startup, WAITING poll, or Interface →
     * Refresh) so ViewLoop {@link #openAEMonitor()} can open it. Uses the cached
     * factory list; does not scan USB.
     *
     * @return true if the chip hardware interface was set in this call
     */
    private boolean bindUnambiguousInterfaceIfPossible(int ninterfaces) {
        if (nullInterface || chip == null || jaerViewer == null || jaerViewer.getViewers().size() != 1) {
            return false;
        }
        if (chip.getHardwareInterface() != null) {
            return false;
        }
        if (ninterfaces != 1) {
            return false;
        }
        synchronized (HARDWARE_CLAIM_LOCK) {
            HardwareInterface hw = HardwareInterfaceFactory.instance().getFirstAvailableInterface();
            if (hw == null) {
                return false;
            }
            // UDP interfaces should only be opened if the chip is a NetworkChip
            if (UDPInterface.class.isInstance(hw)) {
                if (NetworkChip.class.isInstance(chip)) {
                    log.info("opening unambiguous network device");
                    chip.setHardwareInterface(hw);
                    return true;
                }
                return false;
            }
            if (NetworkChip.class.isInstance(chip)) {
                return false;
            }
            ensureChipCompatibleWithLiveDevice(hw);
            // Chip switch closes HW; re-fetch sole interface if needed.
            if (chip.getHardwareInterface() == null && hw != null) {
                hw = HardwareInterfaceFactory.instance().getFirstAvailableInterface();
                bindLiveHardwareIfCompatible(hw, "setting hardware interface for unambiguous device to ");
            }
            return chip.getHardwareInterface() != null;
        }
    }

    private ArrayList<String> chipClassNames;
    private ArrayList<Class> chipClasses;

    void getChipClassPrefs() {
        // Deserialize from a byte array
        try {
            byte[] bytes = prefs.getByteArray("chipClassNames", null);
            if (bytes != null) {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                chipClassNames = (ArrayList<String>) in.readObject();
                in.close();
                if (mergeNewDefaultChipClassNames()) {
                    putChipClassPrefs();
                }
            } else {
                log.warning("Building list of default AEChip devices - this can takes some time. To reduce startup time, use AEChip/Customize to specify desired devices");
                makeDefaultChipClassNames();
                storeMergedDefaultChipClassNames();
            }
        } catch (Exception e) {
            makeDefaultChipClassNames();
            storeMergedDefaultChipClassNames();
            putChipClassPrefs(); // added this to cache chip classes to speed startup for subsequent launches
        }
    }

    /**
     * Prefs key: newline-joined {@link #DEFAULT_CHIP_CLASS_NAMES} already merged
     * into leftover {@code chipClassNames}. Chips the user later removes via
     * Customize stay removed.
     */
    private static final String CHIP_CLASS_NAMES_DEFAULTS_MERGED_PREF = "AEViewer.chipClassNamesDefaultsMerged";

    /**
     * Leftover {@code chipClassNames} from older jAER omit cameras added later
     * to {@link #DEFAULT_CHIP_CLASS_NAMES} (e.g. NRV). Add those once so an
     * upgrade does not require clearing Preferences.
     *
     * @return true if {@link #chipClassNames} changed
     */
    private boolean mergeNewDefaultChipClassNames() {
        if (chipClassNames == null) {
            chipClassNames = new ArrayList<>();
        }
        String mergedRaw = prefs.get(CHIP_CLASS_NAMES_DEFAULTS_MERGED_PREF, "");
        HashSet<String> previouslyMerged = new HashSet<>();
        if (!mergedRaw.isEmpty()) {
            for (String s : mergedRaw.split("\n")) {
                if (!s.isEmpty()) {
                    previouslyMerged.add(s);
                }
            }
        }
        boolean added = false;
        for (String s : DEFAULT_CHIP_CLASS_NAMES) {
            if (!chipClassNames.contains(s) && !previouslyMerged.contains(s)) {
                chipClassNames.add(s);
                added = true;
                log.info("Upgrade: adding default AEChip " + s + " to AEChip menu");
            }
        }
        storeMergedDefaultChipClassNames();
        return added;
    }

    private void storeMergedDefaultChipClassNames() {
        prefs.put(CHIP_CLASS_NAMES_DEFAULTS_MERGED_PREF, String.join("\n", DEFAULT_CHIP_CLASS_NAMES));
    }

    private void makeDefaultChipClassNames() {
//      chipClassNames = SubclassFinder.findSubclassesOf(AEChip.class.getName());
        chipClassNames = new ArrayList<String>();
        for (String s : DEFAULT_CHIP_CLASS_NAMES) {
            chipClassNames.add(s);
        }
    }

    private void putChipClassPrefs() {
        try {
            // Serialize to a byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(chipClassNames);
            out.close();

            // Get the bytes of the serialized object
            byte[] buf = bos.toByteArray();
            prefs.putByteArray("chipClassNames", buf);
        } catch (IOException e) {
            log.log(Level.SEVERE, e.toString(), e);
        } catch (IllegalArgumentException e2) {
            log.warning("too many classes in Preferences, " + chipClassNames.size() + " class names");
        }
    }

    private static class FastClassFinder {

        static HashMap<String, Class> map = new HashMap<String, Class>();

        private static Class forName(String name) throws ClassNotFoundException {
            Class c = null;
            if ((c = map.get(name)) == null) {
                c = JaerAllowedSubclasses.load(name, AEChip.class);
                map.put(name, c);
                return c;
            } else {
                return c;
            }
        }
    }

    private void buildDeviceMenu() {
        ButtonGroup deviceGroup = new ButtonGroup();
        deviceMenu.removeAll();
        chipClasses = new ArrayList<Class>();
        deviceMenu.addSeparator();
        deviceMenu.add(renewChipMI);
        deviceMenu.addSeparator();
        deviceMenu.add(customizeDevicesMenuItem);
        getChipClassPrefs();
        ArrayList<String> notFoundClasses = new ArrayList<String>();
        for (String deviceClassName : chipClassNames) {
            try {
                Class c = FastClassFinder.forName(deviceClassName);
                chipClasses.add(c);
                JRadioButtonMenuItem b = new JRadioButtonMenuItem(deviceClassName);
                b.setToolTipText(descriptionTooltipForClass(c));
                deviceMenu.insert(b, deviceMenu.getItemCount() - 4); // change if more items added at end of AEChip menu, e.g. renewChipMI
                b.addActionListener((ActionEvent evt) -> {
                    try {
                        String name1 = evt.getActionCommand();
                        Class cl = FastClassFinder.forName(name1);
                        try {
                            setCursor(new Cursor(Cursor.WAIT_CURSOR));
                            // Manual AEChip menu choice overrides silent auto-open Remember.
                            clearRememberedLiveChipSelections();
                            setAeChipClass(cl);
                        } finally {
                            setCursor(Cursor.getDefaultCursor());
                        }
                    } catch (Exception e) {
                        log.log(Level.SEVERE, e.toString(), e);
                    }
                });
                deviceGroup.add(b);
            } catch (ClassNotFoundException e) {
                log.warning("couldn't find device class " + e.getMessage() + ", removing from preferred classes");
                if (deviceClassName != null) {
                    notFoundClasses.add(deviceClassName);
                }
            } catch (NoClassDefFoundError err) {
                log.warning("couldn't find device class " + err.getMessage() + ", removing from preferred classes");
                if (deviceClassName != null) {
                    notFoundClasses.add(deviceClassName);
                }
            }
        }
        if (notFoundClasses.size() > 0) {
            chipClassNames.removeAll(notFoundClasses);
            putChipClassPrefs();
        }
        /*
             * appendCopyOfEventReferences scroll arrows to menu
             * arguments are: items to show, scrolling interval,
             * froozen items top, frozen items bottom
         */
        MenuScroller.setScrollerFor(deviceMenu, 15, 100, 4, 2);
    }

    /** Tooltip from {@link Description} on a class, or a short placeholder. */
    private static String descriptionTooltipForClass(Class<?> c) {
        return descriptionTooltip(c, null);
    }

    /**
     * Tooltip from {@link Description} on a class or declared method. HTML is
     * used so multi-line descriptions wrap in Swing tooltips.
     */
    private static String descriptionTooltip(Class<?> type, String methodName, Class<?>... parameterTypes) {
        Description d = null;
        String where = type != null ? type.getSimpleName() : "?";
        if (type != null) {
            try {
                if (methodName != null) {
                    where = where + "." + methodName;
                    d = type.getDeclaredMethod(methodName, parameterTypes).getAnnotation(Description.class);
                } else {
                    d = type.getAnnotation(Description.class);
                }
            } catch (NoSuchMethodException e) {
                log.warning("no method " + where + " for @Description tooltip");
            }
        }
        if (d != null && d.value() != null && !d.value().isBlank()) {
            return htmlTooltip(d.value());
        }
        return htmlTooltip("No @Description on " + where + " — add @Description(\"...\") on the class or method");
    }

    private static String htmlTooltip(String text) {
        String t = text.trim();
        if (t.regionMatches(true, 0, "<html>", 0, 6)) {
            return t;
        }
        return "<html>" + t.replace("\n", "<br>") + "</html>";
    }

    /**
     * File → Remote (network) menu tooltips come from {@link Description} on the
     * I/O classes and the AEViewer methods those items invoke.
     */
    private void applyNetworkMenuDescriptionTooltips() {
        remoteMenu.setToolTipText(descriptionTooltip(AENetworkInterfaceConstants.class, null));
        unicastOutputEnabledCheckBoxMenuItem.setToolTipText(descriptionTooltip(AEUnicastOutput.class, null));
        openUnicastInputMenuItem.setToolTipText(descriptionTooltip(AEUnicastInput.class, null));
        openBlockingQueueInputMenuItem.setToolTipText(descriptionTooltip(AEViewer.class, "openBlockingQueueInputMenuItemActionPerformed", ActionEvent.class));
        blockingQueueOutputEnabledCheckBoxMenuItem.setToolTipText(descriptionTooltip(AEViewer.class, "blockingQueueOutputEnabledCheckBoxMenuItemActionPerformed", ActionEvent.class));
        if (rosOutputMenuItem != null) {
            rosOutputMenuItem.setToolTipText(descriptionTooltip(ROSOutput.class, null));
        }
        if (dnnSharedMemoryMenuItem != null) {
            dnnSharedMemoryMenuItem.setToolTipText(descriptionTooltip(DNNOutputViaSharedMemory.class, null));
        }
        if (openCvOutputMenuItem != null) {
            openCvOutputMenuItem.setToolTipText(descriptionTooltip(OpenCVOutput.class, null));
        }
        enableLongLivedNetworkMenuTooltips(
                remoteMenu,
                unicastOutputEnabledCheckBoxMenuItem,
                openUnicastInputMenuItem,
                openBlockingQueueInputMenuItem,
                blockingQueueOutputEnabledCheckBoxMenuItem,
                rosOutputMenuItem,
                dnnSharedMemoryMenuItem,
                openCvOutputMenuItem);
    }

    /**
     * Keep the long HTML network-menu tooltips on screen for a minute while the
     * pointer is over the item; restore the default dismiss delay when the menu
     * closes or the pointer leaves.
     */
    private void enableLongLivedNetworkMenuTooltips(JComponent... items) {
        MouseAdapter linger = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                ToolTipManager.sharedInstance().setDismissDelay(NETWORK_MENU_TOOLTIP_DISMISS_MS);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ToolTipManager.sharedInstance().setDismissDelay(defaultDismissTimeout);
            }
        };
        for (JComponent c : items) {
            if (c != null) {
                c.addMouseListener(linger);
            }
        }
        remoteMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
            }

            @Override
            public void menuDeselected(MenuEvent e) {
                ToolTipManager.sharedInstance().setDismissDelay(defaultDismissTimeout);
            }

            @Override
            public void menuCanceled(MenuEvent e) {
                ToolTipManager.sharedInstance().setDismissDelay(defaultDismissTimeout);
            }
        });
    }

    /**
     * If the AEMonitor is open, tells it to resetTimestamps, and fires
     * PropertyChange EVENT_TIMESTAMPS_RESET.
     *
     * @see AEMonitorInterface#resetTimestamps()
     */
    public void zeroTimestamps() {
        if ((aemon != null) && aemon.isOpen()) {
            aemon.resetTimestamps();
        }
        getSupport().firePropertyChange(EVENT_TIMESTAMPS_RESET, null, EVENT_TIMESTAMPS_RESET);
    }

    /**
     * Gets the AEchip class from the internal aeChipClassName
     *
     * @return the AEChip subclass. DEFAULT_CHIP_CLASS is returned if there is
     * no stored preference.
     */
    public Class getAeChipClass() {

        return aeChipClass;
    }

    /**
     * Loaded AEChip FQCNs shown in the AEChip / device menu (user-selected subset).
     */
    public java.util.List<String> getChipClassNames() {
        return chipClassNames;
    }

    /** Prefs key prefix: remembered AEChip FQCN for a live USB device key (auto-apply). */
    private static final String LIVE_CHIP_REMEMBERED_PREF_PREFIX = "AEViewer.liveChipOffer.chip.";
    /** Prefs key prefix: last OK choice for dialog preselect only (does not auto-apply). */
    private static final String LIVE_CHIP_DEFAULT_PREF_PREFIX = "AEViewer.liveChipOffer.default.";

    /**
     * When a live USB device is about to be bound (startup or hot-plug), align
     * the viewer AEChip with {@link net.sf.jaer.UsbDevices} matches:
     * <ul>
     * <li>Search the AEChip menu, then {@link #DEFAULT_CHIP_CLASS_NAMES} if the
     * leftover Customize list has no VID/PID match (upgrade without clearing
     * Preferences).</li>
     * <li>If a remembered AEChip exists for this device key and is still a
     * match, apply it silently.</li>
     * <li>If exactly one AEChip matches and it is already selected,
     * continue.</li>
     * <li>If exactly one matches but the current AEChip does not declare this
     * VID/PID, switch to it (and add it to the menu if needed).</li>
     * <li>If several match (e.g. Davis346 red/blue and experimental SciDVS,
     * same VID/PID) and the current AEChip already declares this VID/PID, keep
     * it. Do not open USB to distinguish SciDVS from Davis346.</li>
     * <li>If several match and the current AEChip cannot drive this device,
     * offer a chooser that lists every match (including SciDVS). OK stores a
     * dialog default; Remember auto-applies next time. Choosing from the AEChip
     * menu clears Remember mappings.</li>
     * </ul>
     * Safe to call from ViewLoop (uses EDT).
     */
    public void ensureChipCompatibleWithLiveDevice(HardwareInterface hw) {
        if (hw == null || chipClassNames == null || chipClassNames.isEmpty()) {
            return;
        }
        if (UDPInterface.class.isInstance(hw) || NetworkChip.class.isInstance(chip)) {
            return;
        }
        UsbIds.Pair ids = UsbIds.peek(hw);
        if (!ids.isKnown()) {
            return;
        }
        java.util.List<Class<? extends AEChip>> found
                = LiveDeviceChipDetector.findMatches(hw, chipClassNames);
        if (found.isEmpty()) {
            found = LiveDeviceChipDetector.findMatches(hw, Arrays.asList(DEFAULT_CHIP_CLASS_NAMES));
            if (!found.isEmpty()) {
                log.info("USB device " + ids.key()
                        + " matched default AEChip(s) not in leftover AEChip menu; adding to menu");
                addChipClassesToMenu(found);
            }
        }
        if (found.isEmpty()) {
            log.info("No loaded or default AEChip declares USB " + ids.key()
                    + ". Use AEChip/Customize to add the device class.");
            return;
        }

        // Prefer remembered AEChip. Same VID/PID for Davis346 and experimental
        // SciDVS is resolved by AEChip menu / chooser, not by opening USB.
        String deviceKey = liveDevicePromptKey(hw, ids);
        Class<? extends AEChip> remembered = loadRememberedLiveChip(deviceKey, found);
        if (remembered != null) {
            liveChipOfferPromptedKeys.add(deviceKey);
            if (!remembered.equals(getAeChipClass())) {
                log.info("Using remembered AEChip " + remembered.getSimpleName()
                        + " for USB device " + deviceKey);
                setAeChipClass(remembered);
            }
            return;
        }

        Class current = getAeChipClass();
        boolean currentIsMatch = false;
        for (Class<? extends AEChip> m : found) {
            if (m.equals(current)) {
                currentIsMatch = true;
                break;
            }
        }

        final java.util.List<Class<? extends AEChip>> matches = found;

        // Unique match and already selected — continue.
        if (matches.size() == 1 && currentIsMatch) {
            liveChipOfferPromptedKeys.add(deviceKey);
            return;
        }

        // Unique VID/PID match and current AEChip cannot drive this device
        // (typical upgrade: leftover preferred chip is CDAVIS, camera is NRV).
        if (matches.size() == 1 && !currentIsMatch) {
            Class<? extends AEChip> suggested = matches.get(0);
            liveChipOfferPromptedKeys.add(deviceKey);
            prefs.put(LIVE_CHIP_REMEMBERED_PREF_PREFIX + deviceKey, suggested.getName());
            log.info("Switching AEChip from "
                    + (current == null ? "(none)" : current.getSimpleName())
                    + " to " + suggested.getSimpleName()
                    + " for USB device " + deviceKey
                    + " (unique @UsbDevices match)");
            setAeChipClass(suggested);
            return;
        }

        // Several chips share this VID/PID. If this window already has a matching
        // AEChip (Davis346blue from last session), keep it. A modal chooser here
        // stays up after autobind has already gone LIVE.
        if (currentIsMatch) {
            liveChipOfferPromptedKeys.add(deviceKey);
            return;
        }
        liveChipOfferPromptedKeys.add(deviceKey);

        if (autobindOnWaiting && !SessionCameraOpenCoordinator.hasOpenGrant(this)) {
            log.info("session restore: skip AEChip chooser for " + ids.key()
                    + " (keep current AEChip; Interface can change it)");
            return;
        }

        final Class<? extends AEChip>[] chosenHolder = new Class[1];
        final boolean[] rememberHolder = new boolean[1];
        final String idLabel = ids.key();
        final String promptDeviceKey = deviceKey;
        Runnable dialog = () -> {
            String currentName = current == null ? "(none)" : current.getSimpleName();
            String[] names = new String[matches.size()];
            int preselect = 0;
            Class<? extends AEChip> defaultChoice = loadDefaultLiveChip(promptDeviceKey, matches);
            for (int i = 0; i < matches.size(); i++) {
                names[i] = matches.get(i).getSimpleName();
                if (defaultChoice != null && matches.get(i).equals(defaultChoice)) {
                    preselect = i;
                } else if (defaultChoice == null && matches.get(i).equals(current)) {
                    preselect = i;
                }
            }
            javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 10));
            panel.add(new javax.swing.JLabel(String.format(
                    "<html>USB device <b>%s</b> matches several AEChips (same VID/PID).<br>"
                    + "jAER cannot tell which physical camera this is (e.g. Davis346 red vs blue vs SciDVS).<br>"
                    + "Current AEChip is <b>%s</b>. Choose the AEChip for this camera:<br>"
                    + "<b>OK</b> uses it now and as the dialog default next time;<br>"
                    + "<b>Remember this selection</b> also auto-opens it when this device is found.</html>",
                    idLabel, currentName)), java.awt.BorderLayout.NORTH);
            javax.swing.JComboBox<String> combo = new javax.swing.JComboBox<>(names);
            combo.setSelectedIndex(preselect);
            panel.add(combo, java.awt.BorderLayout.CENTER);
            Object[] options = {"OK", "Remember this selection", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    this, panel, "AEChip for USB device",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]);
            if (choice == 0 || choice == 1) {
                Object sel = combo.getSelectedItem();
                if (sel != null) {
                    for (Class<? extends AEChip> c : matches) {
                        if (c.getSimpleName().equals(sel)) {
                            chosenHolder[0] = c;
                            break;
                        }
                    }
                }
                if (choice == 1) {
                    rememberHolder[0] = true;
                }
            }
        };
        try {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                dialog.run();
            } else {
                javax.swing.SwingUtilities.invokeAndWait(dialog);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warning("Interrupted during live AEChip offer dialog");
            return;
        } catch (java.lang.reflect.InvocationTargetException e) {
            log.warning("Live AEChip offer dialog failed: " + e.getCause());
            return;
        }
        if (chosenHolder[0] != null) {
            // OK and Remember both store the dialog default for next prompt.
            prefs.put(LIVE_CHIP_DEFAULT_PREF_PREFIX + deviceKey, chosenHolder[0].getName());
            if (rememberHolder[0]) {
                prefs.put(LIVE_CHIP_REMEMBERED_PREF_PREFIX + deviceKey, chosenHolder[0].getName());
                log.info("Remembered AEChip " + chosenHolder[0].getSimpleName()
                        + " for USB device " + deviceKey + " (auto-open)");
            } else {
                log.info("Default AEChip for USB device " + deviceKey
                        + " dialog set to " + chosenHolder[0].getSimpleName());
            }
            if (!chosenHolder[0].equals(getAeChipClass())) {
                log.info("Switching AEChip to " + chosenHolder[0].getSimpleName()
                        + " for USB device " + deviceKey);
                setAeChipClass(chosenHolder[0]);
            }
        }
    }

    /**
     * Loads a previously remembered AEChip for {@code deviceKey} if it is still
     * among the VID/PID matches (silent auto-apply).
     */
    private Class<? extends AEChip> loadRememberedLiveChip(String deviceKey,
            java.util.List<Class<? extends AEChip>> matches) {
        return loadLiveChipPref(LIVE_CHIP_REMEMBERED_PREF_PREFIX, deviceKey, matches, true);
    }

    /**
     * Loads the last OK/Remember dialog default for {@code deviceKey} if still
     * among the VID/PID matches (preselect only).
     */
    private Class<? extends AEChip> loadDefaultLiveChip(String deviceKey,
            java.util.List<Class<? extends AEChip>> matches) {
        return loadLiveChipPref(LIVE_CHIP_DEFAULT_PREF_PREFIX, deviceKey, matches, false);
    }

    private Class<? extends AEChip> loadLiveChipPref(String prefix, String deviceKey,
            java.util.List<Class<? extends AEChip>> matches, boolean forgetStale) {
        String fqcn = prefs.get(prefix + deviceKey, null);
        if (fqcn == null || fqcn.isEmpty()) {
            return null;
        }
        for (Class<? extends AEChip> m : matches) {
            if (m.getName().equals(fqcn)) {
                return m;
            }
        }
        if (forgetStale) {
            log.info("Forgetting stale live AEChip pref " + fqcn + " for " + deviceKey
                    + " (no longer in menu matches)");
            prefs.remove(prefix + deviceKey);
        }
        return null;
    }

    /**
     * Clears all silent auto-open Remember mappings. Called when the user picks
     * an AEChip from the menu so a different camera is not forced to the old
     * remembered variant.
     */
    private void clearRememberedLiveChipSelections() {
        try {
            boolean cleared = false;
            for (String key : prefs.keys()) {
                if (key.startsWith(LIVE_CHIP_REMEMBERED_PREF_PREFIX)) {
                    prefs.remove(key);
                    cleared = true;
                }
            }
            if (cleared) {
                log.info("Cleared Remember-this-selection AEChip mappings (AEChip menu change)");
            }
        } catch (java.util.prefs.BackingStoreException e) {
            log.warning("Could not clear remembered live AEChip prefs: " + e);
        }
        liveChipOfferPromptedKeys.clear();
    }

    private String liveDevicePromptKey(HardwareInterface hw, UsbIds.Pair ids) {
        String serial = "";
        if (hw instanceof USBInterface && hw.isOpen()) {
            try {
                String[] desc = ((USBInterface) hw).getStringDescriptors();
                if (desc != null && desc.length >= 3 && desc[2] != null && !desc[2].isBlank()) {
                    serial = desc[2].trim();
                }
            } catch (Throwable t) {
                // serial optional
            }
        }
        if (serial.isEmpty()) {
            return ids.key();
        }
        return ids.key() + "#" + serial;
    }

    /**
     * Add AEChip classes to the Customize menu (and persist) so a USB match
     * found only in {@link #DEFAULT_CHIP_CLASS_NAMES} is available next time.
     */
    private void addChipClassesToMenu(java.util.List<Class<? extends AEChip>> classes) {
        if (classes == null || classes.isEmpty() || chipClassNames == null) {
            return;
        }
        boolean added = false;
        for (Class<? extends AEChip> c : classes) {
            if (c == null) {
                continue;
            }
            String name = c.getName();
            if (!chipClassNames.contains(name)) {
                chipClassNames.add(name);
                added = true;
                log.info("Added " + c.getSimpleName() + " to AEChip menu (USB device match)");
            }
        }
        if (!added) {
            return;
        }
        putChipClassPrefs();
        Runnable rebuild = this::buildDeviceMenu;
        if (SwingUtilities.isEventDispatchThread()) {
            rebuild.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(rebuild);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warning("Interrupted while rebuilding AEChip menu");
            } catch (InvocationTargetException e) {
                log.warning("Could not rebuild AEChip menu: " + e.getCause());
            }
        }
    }

    /**
     * Bind live USB hardware only when the current AEChip either has no
     * {@link net.sf.jaer.UsbDevices} annotation or declares this VID/PID.
     * Prevents leftover preferred chips (e.g. CDAVIS) from opening an NRV
     * interface.
     */
    private void bindLiveHardwareIfCompatible(HardwareInterface hw, String logPrefix) {
        if (hw == null || chip == null) {
            return;
        }
        Class current = getAeChipClass();
        if (LiveDeviceChipDetector.declaresAnyUsbDevices(current)
                && !LiveDeviceChipDetector.currentChipMatches(current, hw)) {
            UsbIds.Pair ids = UsbIds.peek(hw);
            log.warning("Not binding " + hw + " to "
                    + (current == null ? "?" : current.getSimpleName())
                    + " (USB " + ids.key()
                    + " is not declared in @UsbDevices). Choose a matching AEChip.");
            clearOpeningCameraOverlay();
            return;
        }
        log.info(logPrefix + hw);
        showOpeningCameraOverlay(hw);
        // Biasgen binding can issue immediate hardware reads that require the
        // monitor's reverse chip association. Install it before
        // Chip.setHardwareInterface delegates to Biasgen; the Chip setter sets
        // it again after binding as its normal final invariant.
        if (hw instanceof AEMonitorInterface) {
            ((AEMonitorInterface) hw).setChip(chip);
        }
        chip.setHardwareInterface(hw);
        if (hw instanceof USBInterface) {
            rememberUsbIdentity(hw, rememberLastInterfaceSerial);
        }
        notifyOtherViewersOfHardwareClaimChange();
    }

    /**
     * If the recording's chip (from filename, then header) differs from the
     * current {@link AEChip}, ask to switch before opening. For multi-camera
     * AEDAT-4 files, also let the user pick which EVTS stream to play.
     * Returns false if the user cancels open.
     */
    public boolean ensureChipCompatibleWithRecording(File file) {
        if (file == null || !file.isFile()) {
            return true;
        }
        pendingAedat4EventStreamId = null;
        Class<? extends AEChip> suggested = null;
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4) || name.endsWith(".aedat4")) {
            List<RecordingChipDetector.StreamHint> eventStreams
                    = RecordingChipDetector.listAedat4EventStreams(file);
            if (eventStreams.size() > 1) {
                RecordingChipDetector.StreamHint chosen = chooseAedat4EventStream(file, eventStreams);
                if (chosen == null) {
                    log.info("Playback open canceled (AEDAT-4 stream selection)");
                    return false;
                }
                pendingAedat4EventStreamId = chosen.streamId;
                suggested = RecordingChipDetector.resolve(chosen.toChipHint(),
                        loadChipClasses(chipClassNames));
                log.info("AEDAT-4 stream selected: " + chosen.displayLabel()
                        + (suggested == null ? "" : " -> " + suggested.getSimpleName()));
            } else if (eventStreams.size() == 1) {
                pendingAedat4EventStreamId = eventStreams.get(0).streamId;
            }
        }
        if (suggested == null) {
            suggested = RecordingChipDetector.detect(file, chipClassNames);
        }
        if (suggested == null) {
            return true;
        }
        Class current = getAeChipClass();
        if (current != null && (current.equals(suggested)
                || current.getSimpleName().equalsIgnoreCase(suggested.getSimpleName()))) {
            return true;
        }
        String currentName = current == null ? "(none)" : current.getSimpleName();
        String msg = String.format(
                "<html>This recording appears to use chip <b>%s</b>,<br>"
                + "but the viewer is set to <b>%s</b>.<br><br>"
                + "Switch to <b>%s</b> before opening?</html>",
                suggested.getSimpleName(), currentName, suggested.getSimpleName());
        int choice = JOptionPane.showConfirmDialog(
                this,
                msg,
                "AEChip mismatch",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
            log.info("Playback open canceled (AEChip mismatch dialog)");
            pendingAedat4EventStreamId = null;
            return false;
        }
        if (choice == JOptionPane.YES_OPTION) {
            log.info("Switching AEChip from " + currentName + " to " + suggested.getSimpleName()
                    + " for recording " + file.getName());
            setAeChipClass(suggested);
        } else {
            log.info("Keeping AEChip " + currentName + " despite recording hint "
                    + suggested.getSimpleName());
        }
        return true;
    }

    /**
     * Consumed once by file open: selected AEDAT-4 EVTS stream, or null.
     */
    public Integer consumePendingAedat4EventStreamId() {
        Integer id = pendingAedat4EventStreamId;
        pendingAedat4EventStreamId = null;
        return id;
    }

    /**
     * If {@code file} is a DV AEDAT-4 with dependent-block LZ4, offer to open or
     * create a sibling {@code *-rerecord.aedat4} with fast independent-block LZ4.
     *
     * @return open plan, or {@code null} if the user canceled playback
     */
    public Aedat4Lz4Rerecorder.OpenPlan offerAedat4Lz4Rerecord(File file) {
        if (file == null || !file.isFile() || Aedat4Lz4Rerecorder.isRerecordFile(file)) {
            return new Aedat4Lz4Rerecorder.OpenPlan(file, null);
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".aedat4") && !lower.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4)) {
            return new Aedat4Lz4Rerecorder.OpenPlan(file, null);
        }
        if (!Aedat4Compression.probeUsesDependentBlockLz4(file)) {
            return new Aedat4Lz4Rerecorder.OpenPlan(file, null);
        }
        File sibling = Aedat4Lz4Rerecorder.rerecordSibling(file);
        File parent = sibling.getParentFile();
        boolean canWrite = parent != null && parent.isDirectory() && parent.canWrite();
        EngineeringFormat eng = new EngineeringFormat();
        String sizeHint = eng.format(file.length()) + "B";
        if (sibling.isFile()) {
            Object[] options = {
                "Open optimized copy",
                "Play original (slow)",
                "Re-create optimized copy"
            };
            int choice = JOptionPane.showOptionDialog(
                    this,
                    String.format(
                            "<html>This AEDAT-4 file uses <b>dependent-block LZ4</b> (common in DV recordings),<br>"
                            + "which is much slower to play in jAER.<br><br>"
                            + "An optimized copy already exists:<br><code>%s</code><br><br>"
                            + "Open the optimized copy?</html>",
                            sibling.getName()),
                    "Slow LZ4 — " + file.getName(),
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);
            if (choice == 0) {
                log.info("Opening existing AEDAT-4 LZ4 re-record: " + sibling.getName());
                return new Aedat4Lz4Rerecorder.OpenPlan(sibling, null);
            }
            if (choice == 1) {
                log.info("Playing original dependent-block LZ4 file: " + file.getName());
                return new Aedat4Lz4Rerecorder.OpenPlan(file, null);
            }
            if (choice == 2) {
                if (!canWrite) {
                    JOptionPane.showMessageDialog(this,
                            "Cannot write optimized copy in:\n" + parent,
                            "Re-record failed",
                            JOptionPane.ERROR_MESSAGE);
                    return new Aedat4Lz4Rerecorder.OpenPlan(file, null);
                }
                log.info("Re-creating AEDAT-4 LZ4 re-record: " + sibling.getName());
                return new Aedat4Lz4Rerecorder.OpenPlan(sibling, file);
            }
            log.info("Playback open canceled (AEDAT-4 LZ4 re-record dialog)");
            return null;
        }
        if (!canWrite) {
            int keep = JOptionPane.showConfirmDialog(
                    this,
                    String.format(
                            "<html>This AEDAT-4 file uses <b>dependent-block LZ4</b>, which is slow in jAER.<br>"
                            + "Cannot write an optimized copy next to the file (folder not writable).<br><br>"
                            + "Play the original anyway?</html>"),
                    "Slow LZ4 — " + file.getName(),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (keep == JOptionPane.YES_OPTION) {
                return new Aedat4Lz4Rerecorder.OpenPlan(file, null);
            }
            return null;
        }
        int create = JOptionPane.showConfirmDialog(
                this,
                String.format(
                        "<html>This AEDAT-4 file uses <b>dependent-block LZ4</b> (common in DV recordings),<br>"
                        + "which is much slower to play in jAER (~30× vs native LZ4).<br><br>"
                        + "Create an optimized copy next to the original?<br>"
                        + "<code>%s</code><br>"
                        + "(about %s; same folder as the source)</html>",
                        sibling.getName(), sizeHint),
                "Slow LZ4 — create optimized copy?",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (create == JOptionPane.YES_OPTION) {
            log.info("Will create AEDAT-4 LZ4 re-record: " + sibling.getName());
            return new Aedat4Lz4Rerecorder.OpenPlan(sibling, file);
        }
        if (create == JOptionPane.NO_OPTION) {
            log.info("Playing original dependent-block LZ4 file: " + file.getName());
            return new Aedat4Lz4Rerecorder.OpenPlan(file, null);
        }
        log.info("Playback open canceled (AEDAT-4 LZ4 re-record dialog)");
        return null;
    }

    private RecordingChipDetector.StreamHint chooseAedat4EventStream(
            File file, List<RecordingChipDetector.StreamHint> eventStreams) {
        String[] labels = new String[eventStreams.size()];
        for (int i = 0; i < eventStreams.size(); i++) {
            labels[i] = eventStreams.get(i).displayLabel();
        }
        javax.swing.JList<String> list = new javax.swing.JList<>(labels);
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(8, labels.length));
        int choice = JOptionPane.showConfirmDialog(
                this,
                new Object[]{
                    "<html>This AEDAT-4 file contains <b>" + eventStreams.size()
                    + "</b> event camera streams.<br>"
                    + "jAER can play one stream at a time. Select which to open:<br><br></html>",
                    new javax.swing.JScrollPane(list)
                },
                "Select AEDAT-4 camera stream — " + file.getName(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        int idx = list.getSelectedIndex();
        if (idx < 0 || idx >= eventStreams.size()) {
            return null;
        }
        return eventStreams.get(idx);
    }

    @SuppressWarnings("unchecked")
    private static List<Class<? extends AEChip>> loadChipClasses(List<String> fqcn) {
        List<Class<? extends AEChip>> out = new ArrayList<>();
        if (fqcn == null) {
            return out;
        }
        for (String name : fqcn) {
            try {
                Class<?> c = JaerAllowedSubclasses.load(name, AEChip.class);
                if (AEChip.class.isAssignableFrom(c)) {
                    out.add((Class<? extends AEChip>) c);
                }
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        return out;
    }

    private int viewerInstanceIndex = 0;

    /**
     * Stable WindowSaver key {@code AEViewer-N} and title suffix {@code #N+1}.
     */
    public int getViewerInstanceIndex() {
        return viewerInstanceIndex;
    }

    /** True for session-restore / first window; File → New is false. */
    public boolean isAutobindOnWaiting() {
        return autobindOnWaiting;
    }

    /** Unused: session viewers keep autobind so restart rebinds without Interface. */
    public void endSessionAutobind() {
        // Intentionally empty. Permanent disable was the restart killer.
    }

    /**
     * Called from {@link JAERViewer#addViewer} before the frame is shown.
     */
    public void setViewerInstanceIndex(int viewerInstanceIndex) {
        this.viewerInstanceIndex = viewerInstanceIndex;
        setName("AEViewer-" + viewerInstanceIndex);
    }

    private long lastTimeTitleSet = 0;
    PlayMode lastTitlePlayMode = null;

    /**
     * this sets window title according to actual state
     */
    public void setTitleAccordingToState() {
        setTitleAccordingToState(false);
    }

    /**
     * this sets window title according to actual state
     *
     * @param force ignore the 1 Hz throttle (e.g. when a second viewer opens)
     */
    public void setTitleAccordingToState(boolean force) {
        if (!force && (lastTitlePlayMode == getPlayMode()) && ((System.currentTimeMillis() - lastTimeTitleSet) < 1000)) {
            return; // don't bother with this expenive window operation more than 1/second
        }
        lastTimeTitleSet = System.currentTimeMillis();
        lastTitlePlayMode = getPlayMode();
        String ts = null;
        switch (getPlayMode()) {
            case LIVE:
                ts = "LIVE - " + getAeChipClass().getSimpleName() + " - " + aemon + " - AEViewer";
                break;
            case PLAYBACK:
                ts = "PLAYING - " + (currentFile == null ? "Null" : currentFile.getName()) + " - " + getAeChipClass().getSimpleName() + " - AEViewer";
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
            case FILTER_INPUT:
                ts = "FILTER_INPUT - " + getAeChipClass().getSimpleName() + " - AEViewer";
                break;
            default:
                ts = "Unknown state";
        }
        if ((jaerViewer != null) && (jaerViewer.getNumViewers() > 1)) {
            ts = ts + " #" + (viewerInstanceIndex + 1);
        }
        final String fts = ts;
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                setTitle(fts);
            }
        });
    }

    /**
     * Sets the device class, e.g. DVS127, from the fully qualified class name
     * which is provided by the menu item itself.
     *
     * @param deviceClass the Class of the AEChip to appendCopyOfEventReferences
     * to the AEChip menu
     */
    public void setAeChipClass(Class deviceClass) {
        // ChipCanvas / GLCanvas create+realize must run on the EDT. ViewLoop calls
        // ensureChipCompatibleWithLiveDevice off-EDT; racing AWT reshape into
        // SetPixelFormat crashes some drivers (Intel Arc igxelpgicd64.dll).
        if (!SwingUtilities.isEventDispatchThread()) {
            log.fine("Marshaling setAeChipClass(" + deviceClass + ") to EDT");
            try {
                SwingUtilities.invokeAndWait(() -> setAeChipClass(deviceClass));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warning("Interrupted while switching AEChip on EDT");
            } catch (InvocationTargetException e) {
                log.log(Level.SEVERE, "AEChip switch on EDT failed", e.getCause() != null ? e.getCause() : e);
            }
            return;
        }
        log.fine("AEViewer.setAeChipClass(" + deviceClass + ")");
        try {
            if (filterFrame != null) {
                filterFrame.dispose();
                filterFrame = null;
            }
            filterFrameBuilt = false;
            disposeRosOutputDialog();
            disposeDnnSharedMemoryDialog();
            disposeOpenCvOutputDialog();
            filtersToggleButton.setVisible(false);
            viewFiltersMenuItem.setEnabled(false);
            showBiasgen(false);
            cleanup(); // close sockets so they can be reused
            // During file open, never block the EDT on USB close (NRV/libusb can hang).
            closeHardwareInterfaceForChipSwitch();
            AEFileInputStreamInterface oldAeInputStream = null;
            if (chip != null) {
                oldAeInputStream = chip.getAeInputStream(); // save it to assign to new chip in case we have a stream open already
            }
            // force null interface
//            nullInterface = true; // setting null true here prevents openHardwareIfNonambiguous to work correctly (tobi)
            Constructor<AEChip> constructor = deviceClass.getConstructor();
            if (constructor == null) {
                log.warning("null chip constructer, need to select valid chip class");
                return;
            }
            AEChip oldChip = getChip();
            if (oldChip != null) {
                oldChip.onDeregistration();
                if ((oldChip.getCanvas() != null) && (oldChip.getCanvas().getDisplayMethod() != null)) {
                    oldChip.getCanvas().getDisplayMethod().onDeregistration();
                }
            }
            // Keep the existing GLCanvas across AEChip switches. Creating a second
            // GLCanvas crashes Intel Arc (igxelpgicd64.dll SetPixelFormat) even if
            // the new canvas is never realized — so pass it into ChipCanvas ctor.
            final com.jogamp.opengl.awt.GLCanvas reusableGlCanvas = detachChipCanvasKeepGl();
            if (reusableGlCanvas != null) {
                ChipCanvas.setGlCanvasToAdopt(reusableGlCanvas);
            }
            try {
                if (getChip() == null) { // handle initial case
                    constructChip(constructor);
                } else {
                    synchronized (chip) { // TODO handle live case -- this is not ideal thread programming - better to sync on a lock object in the run loop
                        synchronized (extractor) {
                            synchronized (getRenderer()) {
                                getChip().cleanup();
                                constructChip(constructor);
                            }
                        }
                    }
                }
            } finally {
                ChipCanvas.setGlCanvasToAdopt(null); // clear if constructChip failed
            }
            if (chip == null) {
                log.warning("null chip, not continuing");
                return;
            }
            chip.setAeInputStream(oldAeInputStream);
            aeChipClass = deviceClass;
            setPreferredAEChipClass(aeChipClass);
            // chip constructed above, should have renderer already constructed as well
//            if ((chip.getRenderer() != null) && (chip.getRenderer() instanceof Calibratible)) {
//                // begin added by Philipp
//                //            if (aeChipClass.renderer instanceof AdaptiveIntensityRenderer){ // that does not work since the renderer is obviously not defined before a chip gets instanciated
//                //            if (aeChipClass.getName().equals("no.uio.ifi.jaer.chip.foveated.UioFoveatedImager") ||
//                //                    aeChipClass.getName().equals("no.uio.ifi.jaer.chip.staticbiovis.UioStaticBioVis")) {
//                calibrationStartStop.setVisible(true);
//                calibrationStartStop.setEnabled(true);
//            } else {
//                calibrationStartStop.setVisible(false);
//                calibrationStartStop.setEnabled(false);
//            }
            // end added by Philipp
            if (aemon != null) { // force reopen on next LIVE; avoid blocking file-open path
                if (suppressHardwareOpen) {
                    final AEMonitorInterface mon = aemon;
                    aemon = null;
                    Thread t = new Thread(() -> {
                        try {
                            mon.close();
                        } catch (Exception e) {
                            log.warning("async aemon.close after chip construct: " + e);
                        }
                    }, "jaer-async-aemon-close2");
                    t.setDaemon(true);
                    t.start();
                } else {
                    aemon.close();
                }
            }
            makeCanvas();
            Component[] devMenuComps = deviceMenu.getMenuComponents();
            for (Component devMenuComp : devMenuComps) {
                if (devMenuComp instanceof JRadioButtonMenuItem) {
                    JMenuItem item = (JRadioButtonMenuItem) devMenuComp;
                    if (item.getActionCommand().equals(aeChipClass.getName())) {
                        item.setSelected(true);
                        break;
                    }
                }
            }
            fixRecordingControls();
            filterChain = chip.getFilterChain();
            if (filterChain == null) {
                filtersToggleButton.setVisible(false);
                viewFiltersMenuItem.setEnabled(false);
            } else {
                filterChain.reset();
                filtersToggleButton.setVisible(true);
                viewFiltersMenuItem.setEnabled(true);
            }
            HardwareInterface hw = chip.getHardwareInterface();
            if (hw != null) {
                log.info("setting hardware interface of " + chip + " to " + hw);
                aemon = (AEMonitorInterface) hw;
            }

            showFilters(enableFiltersOnStartup);
            if (enableFiltersOnStartup) {
                getFilterFrame().setState(Frame.ICONIFIED); // set the filter frame iconified at first (but open) so that it doesn't obscure view
            }            // fix selected radio button for chip class
            if (deviceMenu.getItemCount() == 0) {
                log.warning("tried to select device in menu but no device menu has been built yet");
            }
            for (int i = 0; i < deviceMenu.getItemCount(); i++) {
                JMenuItem m = deviceMenu.getItem(i);
                if ((m != null) && (m instanceof JRadioButtonMenuItem) && (m.getText() == aeChipClass.getName())) {
                    m.setSelected(true);
                    break;
                }
            }

            // add renderer actions
            // TODO ugly, put the color mode menu at correct spot in View menu
            int i = 0, colorModeSelectionMenuLocation = 0;
            for (Component c : viewMenu.getMenuComponents()) {
                if (c == cyclePreviousColorRenderingMethodMenuItem) {
                    colorModeSelectionMenuLocation = i + 1;
                    break;
                }
                i++;
            }
            chip.getRenderer().getColorModeMenu().setMnemonic('m');
            viewMenu.add(chip.getRenderer().getColorModeMenu(), colorModeSelectionMenuLocation);

            fadingMI.setAction(chip.getRenderer().toggleFadingAction);
            slidingMI.setAction(chip.getRenderer().toggleSlidingWindowAction);
            acccumulateImageEnabledCheckBoxMenuItem.setAction(chip.getRenderer().toggleAccumulationAction);
            increaseContrastMenuItem.setAction(chip.getRenderer().increaseContrastAction);
            decreaseContrastMenuItem.setAction(chip.getRenderer().decreaseContrastAction);

//            // https://docs.oracle.com/javase/tutorial/uiswing/misc/keybinding.html
//            // https://docs.oracle.com/javase/tutorial/uiswing/misc/keybinding.html#howto
//            // https://stackoverflow.com/questions/1946232/can-multiple-accelerators-be-defined-for-a-jmenuitem 
            ////            InputMap im=getRootPane().getInputMap();
//            InputMap imAnces=getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
//            ActionMap am=getRootPane().getActionMap();
//            
//            KeyStroke existingKeyStroke=null, additionalKeyStroke=null;
//            InputMap im=null;
//            
//            existingKeyStroke=increaseContrastMenuItem.getAccelerator();
//            additionalKeyStroke=KeyStroke.getKeyStroke("alt UP");
//            im=increaseContrastMenuItem.getInputMap();
//            imAnces.put(additionalKeyStroke,im.get(existingKeyStroke));
////            im.put(altDown, im.get(KeyStroke.getKeyStroke("DOWN")));
////            imAnces.put(altDown, imAnces.get(KeyStroke.getKeyStroke("DOWN")));
////            am.put(altDown,chip.getRenderer().increaseContrastAction);
//            
////            String altUpStr="alt UP";
////            KeyStroke altUp=KeyStroke.getKeyStroke(altUpStr);
//////            getRootPane().getInputMap().put(altUp, altUpStr);
////            im.put(altUp, im.get(KeyStroke.getKeyStroke("UP")));
////            imAnces.put(altUp, imAnces.get(KeyStroke.getKeyStroke("UP")));
////            am.put(altUp,chip.getRenderer().decreaseContrastAction);
            getSupport().firePropertyChange(EVENT_CHIP, oldChip, getChip());

            chip.onRegistration();

        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }

    private void constructChip(Constructor<AEChip> constructor)
            throws InvocationTargetException,
            InstantiationException,
            IllegalAccessException,
            IllegalArgumentException,
            ExceptionInInitializerError {
        try {
            log.fine(String.format("Constructing instance AEChip using constructor %s", constructor));
            setChip(constructor.newInstance((java.lang.Object[]) null));
        } catch (Exception e) {
            log.log(Level.SEVERE, "AEViewer.constructChip exception " + e.getMessage(), e); // log stack trace
        }
    }

    void makeCanvas() {
        synchronized (getTreeLock()) {
            if (chip == null) {
                log.warning("null chip, not making canvas");
                return;
            }
            chipCanvas = chip.getCanvas();
            Component glComp = chipCanvas.getCanvas();
            // Only add if not already parented — never remove/re-add (new HWND → SetPixelFormat crash on Intel Arc).
            if (glComp != null && glComp.getParent() != getImagePanel()) {
                getImagePanel().add(glComp, BorderLayout.CENTER);
            }

            //        chipCanvas.getCanvas().invalidate();
            // find display menu reference and fill it with display menu for this canvas
            int dispMethodsMenuIdx = 0;
            for (Component c : viewMenu.getMenuComponents()) {
                dispMethodsMenuIdx++;
                if (c == displayMethodMenu) {
                    break;
                }
            }
            viewMenu.remove(displayMethodMenu);
            displayMethodMenu = chipCanvas.getDisplayMethodMenu();
            viewMenu.add(chipCanvas.getDisplayMethodMenu(), dispMethodsMenuIdx - 1);
            viewMenu.invalidate();
            chipCanvas.unzoom();
            if (chip.getRenderer() != null) {
                chip.getRenderer().ensurePixmapReadyForDisplay();
            }
            showWelcomeOverlay();
        }

    }

    /**
     * Detaches the current ChipCanvas from its {@link com.jogamp.opengl.awt.GLCanvas}
     * but leaves the GLCanvas in the image panel. Reparenting a GLCanvas creates a
     * new native peer and a second {@code SetPixelFormat}, which crashes some
     * Intel Arc drivers ({@code igxelpgicd64.dll}).
     *
     * @return reusable GLCanvas, or null
     */
    private com.jogamp.opengl.awt.GLCanvas detachChipCanvasKeepGl() {
        if (chipCanvas == null) {
            return null;
        }
        com.jogamp.opengl.awt.GLCanvas gl = null;
        try {
            gl = chipCanvas.detachGlCanvas();
        } catch (Exception e) {
            log.log(Level.WARNING, "Error detaching GLCanvas: " + e, e);
        }
        chipCanvas = null;
        return gl;
    }

    /**
     * If exactly one USB interface is present, use a remembered AEChip or a
     * unique {@link net.sf.jaer.UsbDevices} match for the first
     * {@link #setAeChipClass} so startup does not build the leftover preferred
     * chip then immediately rebuild for the live device.
     */
    private void maybeUseRememberedLiveChipAtStartup() {
        try {
            if (chipClassNames == null || chipClassNames.isEmpty()) {
                return;
            }
            if (HardwareInterfaceFactory.instance().getNumInterfacesAvailable() != 1) {
                return;
            }
            HardwareInterface hw = HardwareInterfaceFactory.instance().getFirstAvailableInterface();
            if (hw == null || UDPInterface.class.isInstance(hw)
                    || (aeChipClass != null && NetworkChip.class.isAssignableFrom(aeChipClass))) {
                return;
            }
            UsbIds.Pair ids = UsbIds.peek(hw);
            if (!ids.isKnown()) {
                return;
            }
            java.util.List<Class<? extends AEChip>> matches
                    = LiveDeviceChipDetector.findMatches(hw, chipClassNames);
            if (matches.isEmpty()) {
                matches = LiveDeviceChipDetector.findMatches(hw, Arrays.asList(DEFAULT_CHIP_CLASS_NAMES));
                if (!matches.isEmpty()) {
                    addChipClassesToMenu(matches);
                }
            }
            if (matches.isEmpty()) {
                return;
            }
            String deviceKey = liveDevicePromptKey(hw, ids);
            Class<? extends AEChip> chosen = loadRememberedLiveChip(deviceKey, matches);
            if (chosen == null && matches.size() == 1
                    && (aeChipClass == null || !matches.get(0).equals(aeChipClass))) {
                chosen = matches.get(0);
            }
            if (chosen != null && !chosen.equals(aeChipClass)) {
                log.info("Startup: using AEChip " + chosen.getSimpleName()
                        + " for USB device " + deviceKey
                        + " (avoids recreating OpenGL canvas)");
                aeChipClass = chosen;
                aeChipClassName = chosen.getName();
                liveChipOfferPromptedKeys.add(deviceKey);
            }
        } catch (Throwable t) {
            log.log(Level.WARNING, "Could not apply remembered live AEChip at startup", t);
        }
    }

    /**
     * This method sets the "current file" which sets the field, the preferences
     * of the last file, and the window title. It does not actually start
     * playing the file. That is done by the AEPlayer that calls startPlayback()
     * on the file.
     *
     * setInputFile() fires PropertyChange AEViewer.EVENT_FILEOPEN with the
     * oldFile and currentFile passed to listeners.
     *
     * @param f
     */
    protected void setInputFile(File f) {
        currentFile = new File(f.getPath());
        File oldFile = lastFile;
        lastFile = currentFile;
        prefs.put("AEViewer.lastFile", lastFile.toString());
        setTitleAccordingToState();
        getSupport().firePropertyChange(AEViewer.EVENT_FILEOPEN, oldFile, currentFile);
    }

    /**
     * If the AEViewer is playing (or has played) a file, then this method
     * returns it.
     *
     * @return the File
     * @see PlayMode
     */
    public File getInputFile() {
        return currentFile;
    }

    /**
     * Builds the interface menu. Synchronized to avoid clashing with
     * ViewLoop.run() method that is also trying to open devices.
     *
     */
    synchronized private void buildInterfaceMenu() {
        buildInterfaceMenu(interfaceMenu, false);
    }

    /**
     * Rebuilds the Interface menu, optionally forcing a USB bus scan.
     *
     * @param forceUsbRescan if true, re-enumerate even when a device is already
     * open so newly attached cameras appear (Interface → Refresh)
     */
    synchronized private void buildInterfaceMenu(boolean forceUsbRescan) {
        buildInterfaceMenu(interfaceMenu, forceUsbRescan);
    }

    /**
     * Builds list of attached hardware interfaces by asking the hardware
     * interface factories for the interfaces. Populates the Interface menu with
     * these items, and with a "None" item to close and set the chip's
     * HardwareInterface to null. Various specialized interfaces customize the
     * code below.
     */
    public void buildInterfaceMenu(JMenu interfaceMenu) {
        buildInterfaceMenu(interfaceMenu, false);
    }

    /**
     * Builds list of attached hardware interfaces by asking the hardware
     * interface factories for the interfaces. Populates the Interface menu with
     * these items, and with a "None" item to close and set the chip's
     * HardwareInterface to null. Various specialized interfaces customize the
     * code below.
     *
     * @param forceUsbRescan if true, run {@link HardwareInterfaceFactory}
     * enumeration even when a device is already open
     */
    public void buildInterfaceMenu(JMenu interfaceMenu, boolean forceUsbRescan) {
        interfaceMenu.removeAll();
        boolean interfaceAlreadyOpen = false;
        // make an item for the currently opened hardware interface, if there is one for this chip, and select it.
        if ((chip != null) && (chip.getHardwareInterface() != null) && chip.getHardwareInterface().isOpen()) {
            String menuText = String.format("%s", chip.getHardwareInterface().toString());
            JMenuItem item = new JMenuItem(menuText);
            item.setFont(item.getFont().deriveFont(Font.ITALIC));
//            interfaceButton.putClientProperty(HARDWARE_INTERFACE_NUMBER_PROPERTY, new Integer(i)); // has no number, already opened
            item.putClientProperty(HARDWARE_INTERFACE_OBJECT_PROPERTY, chip.getHardwareInterface());
            LibUsbLinkInfo.Snapshot usbLink = LibUsbLinkInfo.lastOpen();
            item.setToolTipText(usbLink != null ? usbLink.tooltipHtml()
                    : "Currently selected hardware interface");
            interfaceMenu.add(item);

            item.setSelected(true);
            interfaceMenu.add(new JSeparator());
            interfaceAlreadyOpen = true;
            log.fine(String.format("Added open device %s", chip.getHardwareInterface().toString()));
            // don't appendOfEventReferences action listener because we are already selected as interface
        }
        ButtonGroup bg = new ButtonGroup();

        //create a list of available hardware interfaces from enumerated devices.
        // Skip USB re-enumeration when a device is already open unless Refresh:
        // getNumInterfacesAvailable() blocks on the EDT and can hang the UI
        // during/after open or preference download. Use Interface → Refresh
        // to find cameras plugged in after the current one opened.
        boolean choseOneButton = false;
        JRadioButtonMenuItem interfaceButton = null;
        // Never scan USB on the EDT. After a hung open, getNumInterfacesAvailable()
        // blocks the Interface menu (jAER 11:52:08). Refresh enumerates off-EDT.
        final int n = HardwareInterfaceFactory.instance().getCachedNumInterfacesAvailable();
        if (interfaceAlreadyOpen) {
            log.info(String.format(
                    "Interface menu: device already open, listing %d cached interface(s) (Refresh to rescan USB)",
                    n));
        } else {
            log.info(String.format("Interface menu: listing %d cached interface(s) (Refresh to rescan USB)", n));
        }
        //        StringBuilder sb = new StringBuilder("adding menu items for ").append(Integer.toString(n)).append(" interfaces");
//                log.info("found "+n+" interfaces");
        for (int i = 0; i < n; i++) {
            HardwareInterface hw = HardwareInterfaceFactory.instance().getInterface(i);// should only return interfaces that are not opened and exclusively owned (modified contract as of Feb 2015, tobi and luca)
//                        log.info("found device "+hw);
            if (hw == null) {
                continue;
            } // in case it disappeared
            // Factory still lists the live device; skip by USB bus/addr, not toString
            // (unopened wrappers default to CypressFX3 and fail ACCESS).
            if (interfaceAlreadyOpen && UsbIds.samePhysicalDevice(hw, chip.getHardwareInterface())) {
                log.info("Skipping already-open USB device " + hw);
                continue;
            }

            // if found interface is NOT some network interface, then make a chooser button for it.
            if ((!UDPInterface.class.isInstance(hw) && !NetworkChip.class.isInstance(chip))
                    || (UDPInterface.class.isInstance(hw) && NetworkChip.class.isInstance(chip))) {
                // if the chip is a normal AEChip with regular (not network) hardware interface, and the interface is not a network interface,
                // then appendOfEventReferences a menu item to select this interface.
                String menuText = interfaceChoiceLabel(hw, i);
                log.info(String.format("Adding menu item for %s", menuText));
                interfaceButton = new JRadioButtonMenuItem(menuText);
                interfaceButton.putClientProperty(HARDWARE_INTERFACE_NUMBER_PROPERTY, i);
                interfaceButton.putClientProperty(HARDWARE_INTERFACE_OBJECT_PROPERTY, hw);
                AEViewer claimedBy = viewerClaimingHardware(hw);
                if (claimedBy == null && hw.isOpen()
                        && (chip == null || !UsbIds.samePhysicalDevice(hw, chip.getHardwareInterface()))) {
                    interfaceButton.setEnabled(false);
                    interfaceButton.setText(menuText + " — already open");
                    interfaceButton.setToolTipText("USB handle already open (another window)");
                    interfaceMenu.add(interfaceButton);
                    bg.add(interfaceButton);
                    continue;
                }
                if (claimedBy != null && claimedBy != this) {
                    interfaceButton.setEnabled(false);
                    interfaceButton.setToolTipText("Already open in " + claimedBy.getViewerWindowLabel());
                    interfaceMenu.add(interfaceButton);
                    bg.add(interfaceButton);
                    continue;
                }
                interfaceMenu.add(interfaceButton);
                bg.add(interfaceButton);
                interfaceButton.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent evt) {
                        JComponent comp = (JComponent) evt.getSource();
                        int interfaceNumber = (Integer) comp.getClientProperty("HardwareInterfaceNumber");
                        HardwareInterface hw = HardwareInterfaceFactory.instance().getInterface(interfaceNumber);
                        if (hw == null || chip == null) {
                            return;
                        }
                        HardwareInterface currentHw = chip.getHardwareInterface();
                        if (currentHw != null && currentHw.isOpen()
                                && UsbIds.samePhysicalDevice(hw, currentHw)) {
                            return;
                        }
                        // Block ViewLoop open until bind finishes. LIVE + nulled HI
                        // otherwise starts the next camera during chip construct and
                        // Prophesee ISSD shutdown (EVK4 → Davis 8:12:14).
                        SessionCameraOpenCoordinator.userRequestedOpen(AEViewer.this);
                        hardwareSwitchInProgress = true;
                        try {
                            showOpeningCameraOverlay(hw);
                            // Allow auto/manual reopen after Interface→None.
                            nullInterface = false;
                            final HardwareInterface previous = currentHw;
                            aemon = null;
                            // Detach first; close previous async so EDT does not block on USB.
                            chip.setHardwareInterface(null);
                            if (previous != null) {
                                // Mid-open Prophesee isOpen()==false but still holds LibUsb — always abort/close.
                                if (previous instanceof PropheseeHardwareInterface) {
                                    ((PropheseeHardwareInterface) previous).requestOpenAbort();
                                }
                                if (isHungNativeHardware(previous)) {
                                    log.info("not closing hung " + previous
                                            + "; unbind only before selecting " + hw);
                                    abandonedHungHardware = previous;
                                } else if (previous.isOpen()
                                        || (previous instanceof PropheseeHardwareInterface
                                        && ((PropheseeHardwareInterface) previous).isOpenInProgress())) {
                                    log.info("closing previous interface before selecting " + hw);
                                    long closeWait = (previous instanceof PropheseeHardwareInterface)
                                            ? HARDWARE_CLOSE_JOIN_MS : HARDWARE_CLOSE_TIMEOUT_MS;
                                    closeHardwareInterfaceWithTimeout(previous, closeWait, "Switch interface");
                                }
                            }
                            // Chip chooser / bind must stay on the EDT.
                            // Off-EDT open with a timeout abandoned mid-dialog and left the
                            // wrong AEChip bound (see jAER log: Not binding … to Prophesee…).
                            ensureChipCompatibleWithLiveDevice(hw);
                            if (chip.getHardwareInterface() == null) {
                                HardwareInterface bind = HardwareInterfaceFactory.instance().getInterface(interfaceNumber);
                                if (bind == null) {
                                    bind = HardwareInterfaceFactory.instance().getFirstAvailableInterface();
                                }
                                bindLiveHardwareIfCompatible(bind, "selected interface " + evt.getActionCommand()
                                        + " with HardwareInterface number" + interfaceNumber + " which is ");
                                rememberUsbIdentity(bind, null);
                            }
                            if (getPlayMode() != PlayMode.PLAYBACK && getPlayMode() != PlayMode.FILTER_INPUT) {
                                setPlayMode(PlayMode.WAITING);
                            }
                        } finally {
                            hardwareSwitchInProgress = false;
                        }
                        // Wake WAITING sleep only after bind. interruptViewloop during
                        // openAEMonitor must not unbind the camera that is still opening.
                        interruptViewloop();
                    }
                });
                //            if(chip!=null && chip.getHardwareInterface()==hw) b.setSelected(true);
                //                sb.append("\n").append(hw.toString());
            }
        }
        // now make items for HardwareInterfaceFactoryChooserDialog factories
        // these HardwareInterfaceFactories allow choice of multiple alternative interfaces, e.g. for a serial port or network interface
        interfaceMenu.add(new JSeparator());

        for (Class c : HardwareInterfaceFactory.factories) {
            log.fine(String.format("Checking HardwareInterfaceFactory %s", c.toString()));
            if (HardwareInterfaceFactoryChooserDialog.class.isAssignableFrom(c)) {
                //                log.log(Level.INFO, "found hardware chooser class {0}", c);
                try {
                    Method m = (c.getMethod("instance")); // get singleton instance of factory
                    final HardwareInterfaceFactoryChooserDialog inst = (HardwareInterfaceFactoryChooserDialog) m.invoke(c);
                    log.fine(String.format("Adding menu item for %s", inst.getName()));
                    JRadioButtonMenuItem mi = new JRadioButtonMenuItem(inst.getName());
                    mi.setToolTipText("Shows a chooser dialog for making this type of HardwareInterface");
                    interfaceMenu.add(mi);
                    bg.add(mi);
                    mi.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            JDialog fac = inst.getInterfaceChooser(chip);
                            fac.setVisible(true);
                            if (inst.getChosenHardwareInterface() != null) {
//                                synchronized (viewLoop) {
                                // close interface on chip if there is one and it's open
                                if (chip.getHardwareInterface() != null) {
                                    log.info("before opening new interface, closing " + chip.getHardwareInterface().toString());
                                    chip.getHardwareInterface().close();
                                    aemon = null; // TODO aemon is a bad hack
                                }
                                HardwareInterface hw = inst.getChosenHardwareInterface();
                                log.info("setting new interface " + hw);
                                chip.setHardwareInterface(hw);
//                                }
                                if (e.getSource() instanceof JMenuItem) {
                                    JMenuItem item = (JMenuItem) e.getSource();
                                    item.setSelected(true); // doesn't work because menu is contantly rebuilt TODO
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    log.warning(c + " threw Exception when trying to get HardwareInterfaceChooserFactory: " + e.toString());
                    log.log(Level.SEVERE, e.toString(), e);
                }

            }
        }

        // make a 'none' item (only there is no interface) // TOTO tobi changed to always make one
        JRadioButtonMenuItem noneInterfaceButton = new JRadioButtonMenuItem("None");
        noneInterfaceButton.setToolTipText("Close hardware interface if it is open");
        noneInterfaceButton.putClientProperty(HARDWARE_INTERFACE_OBJECT_PROPERTY, null);
        interfaceMenu.add(new JSeparator());
        interfaceMenu.add(noneInterfaceButton);
        bg.add(noneInterfaceButton);
        noneInterfaceButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent evt) {
                final HardwareInterface hw = (chip != null) ? chip.getHardwareInterface() : null;
                if (chip != null) {
                    chip.setHardwareInterface(null);
                }
                aemon = null;
                SessionCameraOpenCoordinator.userCancelledOpen(AEViewer.this);
                // force null interface (do not auto-reopen until user picks a device)
                nullInterface = true;
                rememberLastInterfaceDeviceID = null;
                rememberLastInterfaceSerial = null;
                ViewerInterfaceBindingMap.remove(viewerInstanceIndex);
                clearOpeningCameraOverlay();
                notifyOtherViewersOfHardwareClaimChange();
                if (getPlayMode() == PlayMode.LIVE || getPlayMode() == PlayMode.SEQUENCING) {
                    setPlayMode(PlayMode.WAITING);
                }
                interruptViewloop();
                if (hw != null) {
                    if (isHungNativeHardware(hw)) {
                        log.info("None: not closing hung " + hw);
                        abandonedHungHardware = hw;
                    } else {
                        log.info(String.format("selected None interface so closing %s (async, timeout %d ms)",
                                hw, HARDWARE_CLOSE_TIMEOUT_MS));
                        closeHardwareInterfaceWithTimeout(hw, HARDWARE_CLOSE_TIMEOUT_MS, "Close interface");
                    }
                }
            }
        });
//        interfaceMenu.add(new JSeparator());
        noneInterfaceButton.setSelected(!interfaceAlreadyOpen);  // if we already have an interface open, then set none button deselected
        // set current interface selected
        if ((chip != null) && (chip.getHardwareInterface() != null)) {
            choseOneButton = false;
//			String chipInterfaceClass = chip.getHardwareInterface().getClass().getSimpleName();
            //            System.out.println("chipInterface="+chipInterface);
            for (Component c : interfaceMenu.getMenuComponents()) {
                if (!(c instanceof JMenuItem)) {
                    continue;
                }
                JMenuItem item = (JMenuItem) c;
                // set the button on for the actual interface of the chip if there is one already
                Object bound = item.getClientProperty(HARDWARE_INTERFACE_OBJECT_PROPERTY);
                if (bound instanceof HardwareInterface
                        && UsbIds.samePhysicalDevice((HardwareInterface) bound, chip.getHardwareInterface())) {
                    item.setSelected(true);
                    //                    System.out.println("selected "+item.getText());
                    choseOneButton = true;
                    // normal interface selected
                    nullInterface = false;
                }
            }
        }
        if (choseOneButton == false) {

        }

        // Refresh is its own section so a user-initiated scan is not on every menu open.
        interfaceMenu.add(new JSeparator());
        refreshInterfaceMenuItem.setToolTipText("Rescan USB for newly attached cameras without closing the current interface. Opens the camera if exactly one is found (Ctrl+Shift+U; needed on Windows which lacks USB hotplug)");
        interfaceMenu.add(refreshInterfaceMenuItem);

        // make a 'reset device' item 
        interfaceMenu.add(new JSeparator());
        JMenuItem resetDeviceB = new JMenuItem(new ResetHardwareIntefaceAction());
        interfaceMenu.add(resetDeviceB);
        JCheckBoxMenuItem rememberSeletedInterfaceMI = new JCheckBoxMenuItem(getRememberLastInterfaceAction());
        interfaceMenu.add(rememberSeletedInterfaceMI);

        snapshotInterfaceMenuDevices();
        showWelcomeOverlay();
    }

    /**
     * @return the rememberLastInterface
     */
    public boolean isRememberLastInterface() {
        return rememberLastInterface;
    }

    /**
     * Global Interface-menu setting (shared {@code AEViewer} prefs node).
     * Unchanged values return without firing so sibling PropertyChange
     * listeners cannot loop.
     */
    public void setRememberLastInterface(boolean rememberLastInterface) {
        boolean old = this.rememberLastInterface;
        if (old == rememberLastInterface) {
            return;
        }
        this.rememberLastInterface = rememberLastInterface;
        prefs.putBoolean("rememberLastInterface", this.rememberLastInterface);
        if (rememberLastInterfaceAction != null) {
            rememberLastInterfaceAction.putValue(Action.SELECTED_KEY, this.rememberLastInterface);
        }
        getSupport().firePropertyChange(EVENT_REMEMBER_LAST_INTERFACE, old, this.rememberLastInterface);
    }

    private RememberLastInterfaceAction rememberLastInterfaceAction;

    RememberLastInterfaceAction getRememberLastInterfaceAction() {
        if (rememberLastInterfaceAction == null) {
            rememberLastInterfaceAction = new RememberLastInterfaceAction();
        }
        return rememberLastInterfaceAction;
    }

    final public class RememberLastInterfaceAction extends MyAction {

        public RememberLastInterfaceAction() {
            super("Remember last interface selected");
            putValue(Action.SHORT_DESCRIPTION,
                    "Reopen each window's last USB camera on restart (global; all AEViewers share this)");
            putValue(Action.SELECTED_KEY, isRememberLastInterface());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setRememberLastInterface(!rememberLastInterface);
            showAction(isRememberLastInterface()
                    ? "Will reopen last interface automatically"
                    : "Select desired interface from Interface menu");
        }

    }

    final public class ResetHardwareIntefaceAction extends MyAction {

        public ResetHardwareIntefaceAction() {
            super("Reset USB interface");
            putValue(Action.NAME, "Reset USB interface");
            putValue(Action.SHORT_DESCRIPTION,
                    "Close and reopen this USB interface. Does not reset Davis FPGA logic; unplug/replug if APS frames stay incomplete.");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_R, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            showAction("USB reset");
            // Detach every USB HI first. LibUsb.resetDevice / abandoned IN URBs
            // on the shared default context wreck sibling endpoints.
            final List<HardwareInterface> toClose = new ArrayList<>();
            if (jaerViewer != null) {
                for (AEViewer v : jaerViewer.getViewers()) {
                    if (v == AEViewer.this) {
                        continue;
                    }
                    HardwareInterface other = v.detachUsbHardwareForBusReset();
                    if (other != null) {
                        toClose.add(other);
                    }
                }
            }
            final HardwareInterface thisHw = detachHardwareInterfaceForReset();
            if (thisHw != null) {
                toClose.add(thisHw);
            }
            if (toClose.isEmpty()) {
                showAction("No USB interface to reset");
                return;
            }
            log.info(String.format("USB reset: closing %d interface(s) before reopen (timeout %d ms)",
                    toClose.size(), HARDWARE_CLOSE_TIMEOUT_MS));
            closeHardwareInterfacesWithTimeout(toClose, HARDWARE_CLOSE_TIMEOUT_MS, "USB reset", thisHw, true);
        }
    }

    /**
     * Davis FPGA APS sequencer stayed mid-frame after host USB reset/reopen.
     * Interface → Reset USB does not reset camera logic; unplug/replug does.
     */
    public void warnDavisApsStuckNeedReplug() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::warnDavisApsStuckNeedReplug);
            return;
        }
        WarningDialogWithDontShowPreference d = new WarningDialogWithDontShowPreference(this, false,
                "Davis APS readout stuck",
                "<html>The Davis FPGA APS sequencer is still mid-frame "
                        + "(incomplete frames, far fewer signal samples than W×H).<br>"
                        + "Interface → Reset USB only restarts the host USB reader; "
                        + "it does not reset camera logic.<br>"
                        + "<p><b>Unplug the Davis USB cable, wait a second, then plug it back in.</b></html>");
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    final public class FrameRateIncreaseAction extends MyAction {

        public FrameRateIncreaseAction() {
            super("Increase rendering rate", "Faster16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
            putValue(Action.SHORT_DESCRIPTION, "Increase the (target) rendering frame rate");
        }

        public void actionPerformed(ActionEvent e) {
            setDesiredFrameRate(getDesiredFrameRate() * 2);
            showAction(String.format("Increased target rendering frame rate to %d Hz", getDesiredFrameRate()));
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class FrameRateDecreaseAction extends MyAction {

        public FrameRateDecreaseAction() {
            super("Decrease rendering rate", "Slower16");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0));
            putValue(Action.SHORT_DESCRIPTION, "Decrease the (target) rendering frame rate");
        }

        public void actionPerformed(ActionEvent e) {
            setDesiredFrameRate(getDesiredFrameRate() / 2);
            showAction(String.format("Decreased rendering frame rate to %d Hz", getDesiredFrameRate()));
            putValue(Action.SELECTED_KEY, true);
        }
    }

    abstract public class MyAction extends AbstractAction {

        protected final String path = "/net/sf/jaer/graphics/icons/";

        public MyAction() {
            super();
        }

        public MyAction(String name) {
            super(name);
            putValue(Action.SHORT_DESCRIPTION, name);
        }

        public MyAction(String name, String icon) {
            putValue(Action.NAME, name);
            if (icon != null) {
                putValue(Action.SMALL_ICON, new javax.swing.ImageIcon(getClass().getResource(path + icon + ".gif")));
            }
            putValue("hideActionText", "true");
            putValue(Action.SHORT_DESCRIPTION, name);
        }

        protected void showAction() {
            showActionText((String) getValue(Action.SHORT_DESCRIPTION));
        }

        protected void showAction(String s) {
            if (s != null) {
                showActionText(s);
            }
        }
    }

    /**
     * Labels of choosable devices currently in the Interface menu (radio items
     * with a {@link HardwareInterface} client property). Excludes None, Refresh,
     * Reset, and chooser-dialog factories. Updated at the end of
     * {@link #buildInterfaceMenu(JMenu, boolean)}.
     */
    private volatile List<String> interfaceMenuDeviceLabels = List.of();

    /**
     * Interface menu this viewer builds (devices, None, Refresh).
     *
     * @return the menu, or null before init
     */
    public javax.swing.JMenu getInterfaceMenu() {
        return interfaceMenu;
    }

    /**
     * Device labels from the last USB enumeration cache (same text as Interface
     * menu radios). Not the last menu snapshot: two WAITING viewers can share
     * two cameras while each menu was last built with one item.
     *
     * @return never {@code null}
     */
    public List<String> getEnumeratedDeviceLabels() {
        HardwareInterfaceFactory factory = HardwareInterfaceFactory.instance();
        int n = factory.getCachedNumInterfacesAvailable();
        if (n <= 0) {
            return List.of();
        }
        List<String> labels = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            HardwareInterface hw = factory.getInterface(i);
            if (hw == null) {
                continue;
            }
            labels.add(interfaceChoiceLabel(hw, i));
        }
        return List.copyOf(labels);
    }

    /**
     * How many {@link AEViewer} windows this process has. Auto-open is only
     * for a single viewer with a single camera.
     */
    public int getOpenViewerCount() {
        return jaerViewer == null ? 1 : jaerViewer.getViewers().size();
    }

    /**
     * Device labels from the last Interface menu build (same text as the radio
     * items). Empty when no choosable interface is listed. Safe to call from
     * the render thread; the list is an immutable snapshot.
     *
     * @return never {@code null}
     * @see #buildInterfaceMenu(JMenu, boolean)
     * @see #getEnumeratedDeviceLabels()
     */
    public List<String> getInterfaceMenuDeviceLabels() {
        List<String> labels = interfaceMenuDeviceLabels;
        return labels != null ? labels : List.of();
    }

    /** Interface → None is in force (do not autobind until the user picks a device). */
    public boolean isNullInterface() {
        return nullInterface;
    }

    public boolean isHardwareSwitchInProgress() {
        return hardwareSwitchInProgress;
    }

    /**
     * Bound/available USB state for {@link net.sf.jaer.hardwareinterface.usb.USBRebindTester}.
     * Safe off the EDT (volatile flags + cached factory list).
     */
    public String dumpUsbRebindState() {
        StringBuilder sb = new StringBuilder();
        sb.append(getViewerWindowLabel());
        sb.append(" index=").append(viewerInstanceIndex);
        sb.append(" play=").append(getPlayMode());
        sb.append(" chip=");
        sb.append(chip == null ? "-" : chip.getClass().getSimpleName());
        sb.append('\n');
        HardwareInterface hw = chip == null ? null : chip.getHardwareInterface();
        sb.append("  hw=").append(hw == null ? "-" : UsbIds.enumerationKey(hw));
        try {
            sb.append(" open=").append(hw != null && hw.isOpen());
        } catch (Throwable t) {
            sb.append(" open=").append(t.getClass().getSimpleName());
        }
        sb.append(" aemon=");
        if (aemon == null) {
            sb.append("-");
        } else {
            try {
                sb.append(aemon.isOpen() ? "open" : "closed");
            } catch (Throwable t) {
                sb.append(t.getClass().getSimpleName());
            }
        }
        sb.append('\n');
        sb.append("  nullInterface=").append(nullInterface);
        sb.append(" hwSwitch=").append(hardwareSwitchInProgress);
        sb.append(" autobind=").append(autobindOnWaiting);
        sb.append(" suppressHW=").append(suppressHardwareOpen);
        sb.append('\n');
        String wait = SessionCameraOpenCoordinator.waitReason(this);
        sb.append("  grant=").append(SessionCameraOpenCoordinator.hasOpenGrant(this));
        sb.append(" mayOpen=").append(SessionCameraOpenCoordinator.mayOpenUsb(this));
        sb.append(" wait=").append(wait == null ? "-" : wait);
        sb.append('\n');
        ViewerInterfaceBindingMap.Binding b = ViewerInterfaceBindingMap.get(viewerInstanceIndex);
        sb.append("  map=").append(b == null ? "-" : b.label);
        if (b != null && !b.chipClass.isBlank()) {
            sb.append(" chip=").append(b.chipClass);
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Rebuild the Interface menu and click an item. Must run on the EDT.
     * {@code match} is {@code None}, {@code Refresh}, {@code unbound}, a factory
     * index, or a substring of the radio text (bus/addr, VID:PID, label).
     */
    public String injectInterfaceMenuClick(String match) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("injectInterfaceMenuClick must run on the EDT");
        }
        if (match == null || match.isBlank()) {
            return "ERR empty menu match\n";
        }
        String want = match.trim();
        if (want.equalsIgnoreCase("Refresh")) {
            refreshInterfaceMenuItem.doClick();
            return getViewerWindowLabel() + " clicked Refresh\n";
        }
        buildInterfaceMenu(interfaceMenu, false);
        JMenuItem found = findInterfaceMenuItem(want);
        if (found == null) {
            return getViewerWindowLabel() + " no Interface item matching '" + want + "'\n"
                    + listInterfaceMenuItems();
        }
        if (!found.isEnabled()) {
            return getViewerWindowLabel() + " Interface item disabled: " + found.getText() + "\n";
        }
        found.doClick();
        return getViewerWindowLabel() + " clicked " + found.getText() + "\n";
    }

    /** Rebuild Interface menu and list items. EDT only. */
    public String listInterfaceMenuItems() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("listInterfaceMenuItems must run on the EDT");
        }
        buildInterfaceMenu(interfaceMenu, false);
        StringBuilder sb = new StringBuilder();
        sb.append(getViewerWindowLabel()).append(" Interface menu\n");
        if (interfaceMenu == null) {
            return sb.append("  (menu not created)\n").toString();
        }
        for (Component c : interfaceMenu.getMenuComponents()) {
            if (!(c instanceof JMenuItem)) {
                continue;
            }
            JMenuItem item = (JMenuItem) c;
            String text = item.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            Object num = item.getClientProperty(HARDWARE_INTERFACE_NUMBER_PROPERTY);
            sb.append("  ");
            if (!item.isEnabled()) {
                sb.append("[disabled] ");
            }
            if (item instanceof javax.swing.JRadioButtonMenuItem radio && radio.isSelected()) {
                sb.append("[selected] ");
            }
            if (num instanceof Integer) {
                sb.append('[').append(num).append("] ");
            }
            sb.append(text).append('\n');
        }
        return sb.toString();
    }

    private JMenuItem findInterfaceMenuItem(String match) {
        if (interfaceMenu == null) {
            return null;
        }
        String want = match.trim();
        boolean unbound = want.equalsIgnoreCase("unbound");
        Integer wantIndex = null;
        try {
            wantIndex = Integer.valueOf(want);
        } catch (NumberFormatException ignored) {
        }
        JMenuItem firstUnbound = null;
        for (Component c : interfaceMenu.getMenuComponents()) {
            if (!(c instanceof JMenuItem)) {
                continue;
            }
            JMenuItem item = (JMenuItem) c;
            String text = item.getText();
            if (text == null) {
                continue;
            }
            if (want.equalsIgnoreCase("None") && text.equalsIgnoreCase("None")) {
                return item;
            }
            Object num = item.getClientProperty(HARDWARE_INTERFACE_NUMBER_PROPERTY);
            Object bound = item.getClientProperty(HARDWARE_INTERFACE_OBJECT_PROPERTY);
            if (wantIndex != null && wantIndex.equals(num)) {
                return item;
            }
            if (!unbound && text.toLowerCase().contains(want.toLowerCase())
                    && !text.equalsIgnoreCase("None") && !text.equalsIgnoreCase("Refresh")) {
                return item;
            }
            if (unbound && item.isEnabled() && bound instanceof HardwareInterface
                    && num instanceof Integer) {
                HardwareInterface hw = (HardwareInterface) bound;
                AEViewer owner = viewerClaimingHardware(hw);
                if (owner == null || owner == this) {
                    HardwareInterface current = chip == null ? null : chip.getHardwareInterface();
                    if (current == null || !UsbIds.samePhysicalDevice(hw, current)) {
                        if (firstUnbound == null) {
                            firstUnbound = item;
                        }
                    }
                }
            }
        }
        return firstUnbound;
    }

    /** File → Exit without confirmation. Safe to invoke from the tester on the EDT. */
    public void requestExit() {
        doExitAllViewers();
    }

    /**
     * If selected, the {@code x} accelerator (File → Exit) quits jAER. If not,
     * {@code x} closes only this AEViewer when more than one is open.
     */
    public boolean isExitCompletelyWithX() {
        return prefs.getBoolean(PREF_EXIT_COMPLETELY_WITH_X, true);
    }

    /** True after the user set {@link #isExitCompletelyWithX()} in the first multi-viewer dialog or Preferences. */
    public boolean isExitCompletelyWithXChosen() {
        return prefs.getBoolean(PREF_EXIT_COMPLETELY_WITH_X_CHOSEN, false);
    }

    /**
     * Sticky choice for the {@code x} accelerator. Also marks the choice as made
     * so the first-time multi-viewer dialog is not shown again.
     */
    public void setExitCompletelyWithX(boolean exitCompletely) {
        prefs.putBoolean(PREF_EXIT_COMPLETELY_WITH_X, exitCompletely);
        prefs.putBoolean(PREF_EXIT_COMPLETELY_WITH_X_CHOSEN, true);
        updateExitMenuTooltip();
        if (jaerViewer != null) {
            for (AEViewer v : jaerViewer.getViewers()) {
                if (v != this) {
                    v.updateExitMenuTooltip();
                }
            }
        }
    }

    private void updateExitMenuTooltip() {
        if (exitMenuItem == null) {
            return;
        }
        String xPart = isExitCompletelyWithX()
                ? "The x key also exits jAER (confirms when several windows are open)."
                : "The x key closes only this AEViewer.";
        exitMenuItem.setToolTipText("File → Exit quits jAER immediately (all windows, no confirmation). " + xPart);
    }

    /**
     * True when File → Exit was fired by the {@code x} KeyStroke, not a menu
     * click or mnemonic while the File menu is open.
     */
    private static boolean isXAcceleratorActivation(ActionEvent evt) {
        if (evt == null) {
            return false;
        }
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
            return false;
        }
        AWTEvent cur = EventQueue.getCurrentEvent();
        if (!(cur instanceof KeyEvent)) {
            return false;
        }
        KeyEvent ke = (KeyEvent) cur;
        if (ke.getKeyCode() != KeyEvent.VK_X) {
            return false;
        }
        int mods = ke.getModifiersEx();
        return (mods & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK)) == 0;
    }

    /**
     * Copies choosable Interface-menu item text for {@link Welcome}. Call on
     * the same thread that just rebuilt the menu.
     */
    private void snapshotInterfaceMenuDevices() {
        List<String> labels = new ArrayList<>();
        if (interfaceMenu != null) {
            for (Component c : interfaceMenu.getMenuComponents()) {
                if (!(c instanceof AbstractButton)) {
                    continue;
                }
                AbstractButton item = (AbstractButton) c;
                Object hw = item.getClientProperty(HARDWARE_INTERFACE_OBJECT_PROPERTY);
                if (hw instanceof HardwareInterface) {
                    String text = item.getText();
                    if (text != null && !text.isEmpty()) {
                        labels.add(text);
                    }
                }
            }
        }
        interfaceMenuDeviceLabels = List.copyOf(labels);
    }

    /**
     * Chip canvas for this viewer (may be null before {@link #makeCanvas()}).
     *
     * @return the live {@link ChipCanvas}, or null
     */
    public ChipCanvas getChipCanvas() {
        if (chipCanvas != null) {
            return chipCanvas;
        }
        if (chip != null) {
            return chip.getCanvas();
        }
        return null;
    }

    /**
     * Pushes {@link Welcome#linesFor(AEViewer)} onto the chip canvas. Called
     * from {@link #makeCanvas()} so the overlay is applied after each canvas
     * (re)build. The canvas still only paints while play mode is WAITING and
     * no hardware is open.
     *
     * @see ChipCanvas#setWelcomeOverlay(String[])
     * @see Welcome#linesFor(AEViewer)
     */
    public void showWelcomeOverlay() {
        ChipCanvas canvas = getChipCanvas();
        if (canvas == null) {
            return;
        }
        canvas.clearUsbLinkOverlay();
        if (pendingOpeningCameraLabel != null) {
            canvas.setWelcomeOverlay(Welcome.opening(this, pendingOpeningCameraLabel));
        } else {
            canvas.setWelcomeOverlay(Welcome.linesFor(this));
        }
        canvas.repaint();
    }

    /**
     * Replaces the idle {@link Welcome} overlay with {@link Welcome#opening}
     * as soon as the user picks a camera (Interface menu / auto-bind). Call on
     * the EDT when possible so the canvas updates before USB open blocks.
     *
     * @param hw the camera being opened, or {@code null} for a generic label
     */
    public void showOpeningCameraOverlay(HardwareInterface hw) {
        String label = hw == null ? "camera" : interfaceMenuLabel(hw);
        pendingOpeningCameraLabel = label;
        ChipCanvas canvas = getChipCanvas();
        if (canvas == null) {
            return;
        }
        canvas.setWelcomeOverlay(Welcome.opening(this, label));
        canvas.repaint();
    }

    /**
     * Drop the opening-camera overlay and restore idle {@link Welcome} copy
     * (None, failed open, abandoned USB).
     */
    public void clearOpeningCameraOverlay() {
        pendingOpeningCameraLabel = null;
        showWelcomeOverlay();
    }

    /**
     * Replaces the welcome overlay lines on the chip canvas. Pass {@code null}
     * to restore {@link Welcome} defaults; pass an empty array to hide the
     * overlay even while WAITING.
     *
     * @param lines overlay lines, or {@code null} for {@link Welcome} defaults
     * @see ChipCanvas#setWelcomeOverlay(String[])
     * @see #showWelcomeOverlay()
     * @see #clearWelcomeOverlay()
     */
    public void setWelcomeOverlay(String[] lines) {
        ChipCanvas canvas = getChipCanvas();
        if (canvas == null) {
            return;
        }
        canvas.setWelcomeOverlay(lines);
    }

    /**
     * Hides the welcome overlay even while WAITING (empty lines on the canvas).
     *
     * @see ChipCanvas#clearWelcomeOverlay()
     * @see #showWelcomeOverlay()
     */
    public void clearWelcomeOverlay() {
        ChipCanvas canvas = getChipCanvas();
        if (canvas == null) {
            return;
        }
        canvas.clearWelcomeOverlay();
    }

    /**
     * Lines currently set on the chip canvas (defaults from {@link Welcome} if
     * none were pushed yet).
     *
     * @return overlay lines, or an empty array if the canvas is missing or the
     * overlay was cleared
     * @see ChipCanvas#getWelcomeOverlay()
     */
    public String[] getWelcomeOverlay() {
        ChipCanvas canvas = getChipCanvas();
        if (canvas == null) {
            return new String[0];
        }
        return canvas.getWelcomeOverlay();
    }

    /**
     * Skip all AEViewer chip rendering (pixmap {@code render()}, APS/DVS
     * textures, IMU overlay, frame markers, filter annotations) and paint a
     * blank frame with {@code overlay} naming what is blocking the update.
     * Filters still run. Pass {@code null} or empty to resume normal rendering.
     *
     * @param overlay message to draw, or null/empty to stop skipping
     */
    public void setSkipChipRenderingOverlay(String overlay) {
        if (overlay == null || overlay.isEmpty()) {
            skipChipRenderingOverlay = null;
        } else {
            skipChipRenderingOverlay = overlay;
        }
    }

    /**
     * @return overlay set by {@link #setSkipChipRenderingOverlay(String)}, or null
     */
    public String getSkipChipRenderingOverlay() {
        return skipChipRenderingOverlay;
    }

    /**
     * @return true when a filter requested skip of chip pixmap rendering
     */
    public boolean isSkipChipRenderingRequested() {
        return skipChipRenderingOverlay != null;
    }

    /**
     * Sets a flag that rendering this current packet is skipped. Can be used by
     * event filters to skip rendering if results are boring.
     */
    public void fastForward() {
        viewLoop.fastForward = true;
    }

    void fixBiasgenControls() {

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {//        // debug
                //        biasesToggleButton.setEnabled(true);
                //        biasesToggleButton.setVisible(true);
                //        viewBiasesMenuItem.setEnabled(true);
                if (chip == null) {
                    return;
                }
                if (chip.getBiasgen() == null) {
                    log.info("setting hardware config / biasgen buttons false");
                    biasesToggleButton.setEnabled(false);
                    biasesToggleButton.setVisible(false);
                    viewBiasesMenuItem.setEnabled(false);
                    return;
                } else {
                    biasesToggleButton.setEnabled(true);
                    biasesToggleButton.setVisible(true);
                    viewBiasesMenuItem.setEnabled(true);
                }
                if (biasgenFrame != null) {
                    boolean vis = biasgenFrame.isVisible();
                    biasesToggleButton.setSelected(vis);
                }
            }
        });
    }
    // nulls out all hardware interfaces to start fresh

    private void nullifyHardware() {
        aemon = null; // if device is blank a bare interface may have been constructed and we must ensure the deivce is reinstantiated after programming
        if (chip != null) {
            chip.setHardwareInterface(null); // should set chip's biasgen to null also
            //            if(chip.getBiasgen()!=null) chip.getBiasgen().setHardwareInterface(null);
        }
        notifyOtherViewersOfHardwareClaimChange();
        pendingOpeningCameraLabel = null;
        ChipCanvas canvas = getChipCanvas();
        if (canvas != null) {
            canvas.clearUsbLinkOverlay();
        }
    }

    /** True when the last USB scan still lists this bus/addr (not an unplug). */
    private boolean factoryCacheHasPhysicalDevice(HardwareInterface hw) {
        if (hw == null) {
            return false;
        }
        HardwareInterfaceFactory factory = HardwareInterfaceFactory.instance();
        int n = factory.getCachedNumInterfacesAvailable();
        for (int i = 0; i < n; i++) {
            if (UsbIds.samePhysicalDevice(hw, factory.getInterface(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wait for {@link #USB_OPEN_SERIAL_LOCK} without starting this camera's
     * open timeout. Returns false if ViewLoop is stopping, playback won, or
     * the bind vanished.
     */
    private boolean acquireUsbOpenSerialLock(AEMonitorInterface opening) {
        long lastLogMs = 0;
        boolean showedWait = false;
        while (true) {
            if (viewLoop != null && viewLoop.stop) {
                return false;
            }
            if (hardwareSwitchInProgress || suppressHardwareOpen
                    || getPlayMode() == PlayMode.PLAYBACK || getPlayMode() == PlayMode.FILTER_INPUT) {
                return false;
            }
            if (chip == null || chip.getHardwareInterface() != opening) {
                return false;
            }
            try {
                if (USB_OPEN_SERIAL_LOCK.tryLock(100, TimeUnit.MILLISECONDS)) {
                    usbOpenSerialHolder = getViewerWindowLabel() + " " + UsbIds.enumerationKey(opening);
                    log.info("USB open serializer: " + usbOpenSerialHolder);
                    UsbOpenTrace.event("hold", "one open+config", usbOpenSerialHolder);
                    return true;
                }
            } catch (InterruptedException ie) {
                if (viewLoop != null && viewLoop.stop) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                log.info("USB open serializer: interrupt while waiting; continuing");
            }
            long now = System.currentTimeMillis();
            if (now - lastLogMs >= 5000) {
                lastLogMs = now;
                String holder = usbOpenSerialHolder;
                log.info("USB open serializer: waiting to open " + opening
                        + (holder == null ? "" : " (in progress: " + holder + ")"));
                UsbOpenTrace.event("wait", "queue behind in-progress open",
                        opening + " behind=" + holder);
                if (!showedWait) {
                    showedWait = true;
                    showActionText("Waiting for another camera USB open…");
                }
            }
        }
    }

    private void releaseUsbOpenSerialLock() {
        if (!USB_OPEN_SERIAL_LOCK.isHeldByCurrentThread()) {
            return;
        }
        log.fine("USB open serializer: released " + usbOpenSerialHolder);
        UsbOpenTrace.event("release", "next camera may open",
                usbOpenSerialHolder == null ? "none" : usbOpenSerialHolder);
        usbOpenSerialHolder = null;
        USB_OPEN_SERIAL_LOCK.unlock();
    }

    /**
     * Tries to open the AE interface.
     *
     */
    private void openAEMonitor() {
        // Intentionally not synchronized on viewLoop: holding that monitor during USB
        // open/aemon.open() blocked EDT setPlayMode(PLAYBACK) indefinitely.
        boolean wantLive = false;
        boolean wantWaiting = false;
        boolean sessionDeviceGone = false;
        boolean keepInterfaceGrant = false;
        if (hardwareSwitchInProgress || suppressHardwareOpen
                || getPlayMode() == PlayMode.PLAYBACK || getPlayMode() == PlayMode.FILTER_INPUT) {
            // don't open hardware if playing a file, a file open is in progress,
            // or Interface menu is still binding the next camera
            return;
        }
        awaitPendingHardwareClose();
        if ((aemon != null) && aemon.isOpen()) {
            if (getPlayMode() != PlayMode.SEQUENCING) {
                wantLive = true;
            }
        } else {
            if (!SessionCameraOpenCoordinator.mayOpenUsb(this)) {
                return;
            }
            try {
                HardwareInterface bound = (chip != null) ? chip.getHardwareInterface() : null;
                // AEReader shutdown closes the live wrapper on unplug but leaves it on the
                // chip. Reopening that instance throws devicePointer-not-initialized and
                // used to set nullInterface, so the next plug was ignored (jAER-0.log 17:44:59).
                if (bound != null && aemon == bound && !bound.isOpen()) {
                    boolean stillPlugged = factoryCacheHasPhysicalDevice(bound);
                    boolean retrySame = SessionCameraOpenCoordinator.hasOpenGrant(this) && stillPlugged;
                    if (retrySame) {
                        log.info("retrying Interface-selected closed wrapper: " + bound);
                    } else {
                        log.info("dropping closed hardware wrapper so unplug can re-enumerate: " + bound);
                        nullifyHardware();
                        if (stillPlugged) {
                            // Same-tick map-autobind was a close/open loop. Permanent
                            // nullInterface left cameras WAITING after a sibling DVX
                            // open timeout during Prophesee ISSD (jAER 3:19:38).
                            log.info("USB still enumerated after close of " + UsbIds.enumerationKey(bound)
                                    + "; retry bind on next WAITING poll");
                            wantWaiting = true;
                        } else {
                            resetWindowsUsbPoll("device removed");
                        }
                        if (stillPlugged) {
                            setPlayMode(PlayMode.WAITING);
                            showWelcomeOverlay();
                            return;
                        }
                    }
                }
                openHardwareIfNonambiguous();
                if (hardwareSwitchInProgress || suppressHardwareOpen
                        || getPlayMode() == PlayMode.PLAYBACK || getPlayMode() == PlayMode.FILTER_INPUT) {
                    return;
                }
                if (chip.getHardwareInterface() == null) {
                    SessionCameraOpenCoordinator.noteEmptyBind(this);
                    return;
                }
                if (SessionCameraOpenCoordinator.shouldDeferClassicDvxOpen(this)) {
                    log.fine(getViewerWindowLabel() + " defer classic DVX open until other cameras are LIVE");
                    return;
                }
                // openHardwareIfNonambiguous will set chip's hardware interface, here we store local reference
                // if it's an aemon, then its an event monitor
                if ((chip.getHardwareInterface() != null) && (chip.getHardwareInterface() instanceof AEMonitorInterface)) {
                    aemon = (AEMonitorInterface) chip.getHardwareInterface();
                    if ((aemon == null) || !(aemon instanceof AEMonitorInterface)) {
                        fixDeviceControlMenuItems();
                        fixRecordingControls();
                        fixBiasgenControls();
                        return;
                    }

                    aemon.setChip(chip);
                    log.info("openAEMonitor: opening " + aemon);
                    showActionText("Opening " + aemon + "…");
                    final AEMonitorInterface opening = aemon;
                    boolean serialHeld = false;
                    try {
                    serialHeld = acquireUsbOpenSerialLock(opening);
                    if (!serialHeld) {
                        if (viewLoop != null && viewLoop.stop) {
                            aemon = null;
                            return;
                        }
                        if (suppressHardwareOpen || getPlayMode() == PlayMode.PLAYBACK
                                || getPlayMode() == PlayMode.FILTER_INPUT) {
                            return;
                        }
                        aemon = null;
                        wantWaiting = true;
                        return;
                    }
                    // Open on a worker so Interface switch can abort a stuck Prophesee ISSD /
                    // native USB call without freezing ViewLoop forever (log 10:49:00–16).
                    final AtomicReference<Throwable> openError = new AtomicReference<>();
                    final CountDownLatch openDone = new CountDownLatch(1);
                    final AEChip chipForOpen = chip;
                    Thread opener = new Thread(() -> {
                        log.fine("openAEMonitor worker begin open() of " + opening + " " + UsbLog.t());
                        try {
                            opening.open();
                            log.fine("openAEMonitor worker open() returned " + opening + " " + UsbLog.t());
                            // Configure on this worker before ViewLoop goes LIVE.
                            // A parallel jaer-send-biases thread held CypressFX3 during
                            // native controlTransfer; LIVE acquire / EDT isOpen() then
                            // froze the UI (Davis None→Davis, jAER 12:57:03).
                            if (chipForOpen.getHardwareInterface() == opening
                                    && opening instanceof BiasgenHardwareInterface) {
                                Biasgen bg = chipForOpen.getBiasgen();
                                if (bg != null) {
                                    if (chipForOpen instanceof DVXplorer dvx) {
                                        log.fine("jaer-aemon-open dvxConfig begin " + UsbLog.t());
                                        dvx.dvxConfig();
                                        log.fine("jaer-aemon-open dvxConfig done " + UsbLog.t());
                                    }
                                    log.fine("jaer-aemon-open sendConfiguration begin " + UsbLog.t());
                                    bg.sendConfiguration(bg);
                                    log.fine("jaer-aemon-open sendConfiguration done " + UsbLog.t());
                                }
                            }
                            // AEReader starts on ViewLoop after LIVE. Queuing Mini IN on
                            // this opener starved EVK4 ISSD (LIBUSB_ERROR_TIMEOUT at
                            // readFirmwareInfo, jAER 4:20:44).
                        } catch (Throwable t) {
                            openError.set(t);
                            log.fine("openAEMonitor worker open() threw " + t + " " + UsbLog.t());
                        } finally {
                            openDone.countDown();
                        }
                    }, "jaer-aemon-open");
                    opener.setDaemon(true);
                    hardwareOpenThread = opener;
                    hardwareOpenTarget = opening;
                    opener.start();
                    boolean abandoned = false;
                    final long openWaitMs = (opening instanceof PropheseeHardwareInterface)
                            ? HARDWARE_OPEN_WAIT_PROPHESEE_MS : HARDWARE_OPEN_WAIT_MS;
                    final long openDeadline = System.currentTimeMillis() + openWaitMs;
                    long lastOpenWaitFineMs = 0;
                    while (true) {
                        try {
                            if (openDone.await(100, TimeUnit.MILLISECONDS)) {
                                break;
                            }
                        } catch (InterruptedException ie) {
                            if (viewLoop != null && viewLoop.stop) {
                                Thread.currentThread().interrupt();
                                log.info("openAEMonitor: ViewLoop stopping; aborting open of " + opening);
                                if (opening instanceof PropheseeHardwareInterface) {
                                    ((PropheseeHardwareInterface) opening).requestOpenAbort();
                                }
                                opener.interrupt();
                                unbindAbandonedHardware(opening);
                                aemon = null;
                                wantWaiting = true;
                                return;
                            }
                            // Interface switch interruptViewloop must not unbind a camera
                            // whose open() is about to return (Davis 1 ms after EVK4 close).
                            log.info("openAEMonitor: interrupt while waiting for open of " + opening
                                    + "; continuing");
                            continue;
                        }
                        long now = System.currentTimeMillis();
                        if (now - lastOpenWaitFineMs >= 5000) {
                            lastOpenWaitFineMs = now;
                            log.fine("openAEMonitor waiting " + (now - (openDeadline - openWaitMs))
                                    + " ms for " + opening + " waiter=" + UsbLog.t()
                                    + " worker=" + UsbLog.stack(opener, 8));
                        }
                        if (chip.getHardwareInterface() != opening) {
                            log.info("openAEMonitor: interface changed while opening " + opening
                                    + "; aborting (not closing hung USB handle)");
                            log.fine("openAEMonitor abort (HI changed) " + UsbLog.t()
                                    + " worker=" + UsbLog.stack(opener, 10));
                            if (opening instanceof PropheseeHardwareInterface) {
                                ((PropheseeHardwareInterface) opening).requestOpenAbort();
                            }
                            opener.interrupt();
                            abandoned = true;
                            break;
                        }
                        if (now >= openDeadline) {
                            log.warning("openAEMonitor: open of " + opening + " timed out after "
                                    + openWaitMs + " ms; aborting");
                            UsbOpenTrace.event("timeout", "unbind hung camera; release serializer",
                                    opening + " workerAlive=" + opener.isAlive());
                            log.fine("openAEMonitor timeout " + UsbLog.t()
                                    + " worker=" + UsbLog.stack(opener, 12));
                            showActionText("Open timed out — aborting");
                            if (opening instanceof PropheseeHardwareInterface) {
                                ((PropheseeHardwareInterface) opening).requestOpenAbort();
                            }
                            opener.interrupt();
                            // Let ISSD unwind and release USB so the next select is not ACCESS.
                            try {
                                if (!openDone.await(HARDWARE_CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                                    log.warning("openAEMonitor: open thread still alive after abort of " + opening);
                                    log.fine("openAEMonitor still alive after abort wait "
                                            + UsbLog.stack(opener, 12));
                                }
                            } catch (InterruptedException abortWaitIe) {
                                log.fine("openAEMonitor: interrupt during abort wait of " + opening);
                            }
                            abandoned = true;
                            break;
                        }
                    }
                    if (abandoned) {
                        // Do not close() the same instance: open() is synchronized and
                        // still inside native USB, so close() blocks forever (log 11:14:16).
                        // Unbind so ViewLoop does not retry this wrapper (became "CypressFX3").
                        abandonedHungHardware = opening;
                        unbindAbandonedHardware(opening);
                        if (aemon == opening) {
                            aemon = null;
                        }
                        if (opener.isAlive()) {
                            UsbOpenTrace.event("timeout-release",
                                    "release serializer so remaining cameras can open",
                                    UsbIds.enumerationKey(opening) + " " + UsbLog.stack(opener, 6));
                        }
                        HardwareInterface nextHi = (chip != null) ? chip.getHardwareInterface() : null;
                        if (nextHi != null && nextHi != opening) {
                            nullInterface = false;
                            SessionCameraOpenCoordinator.userRequestedOpen(this);
                            log.info("openAEMonitor: keeping newly bound " + nextHi
                                    + " after abort of hung " + opening);
                            return;
                        }
                        wantWaiting = true;
                        SessionCameraOpenCoordinator.viewerFinishedOpenAttempt(this, "timeout");
                        return;
                    }
                    Throwable openTh = openError.get();
                    if (openTh instanceof BlankDeviceException) {
                        throw (BlankDeviceException) openTh;
                    }
                    if (openTh instanceof HardwareInterfaceException) {
                        throw (HardwareInterfaceException) openTh;
                    }
                    if (openTh instanceof RuntimeException) {
                        throw (RuntimeException) openTh;
                    }
                    if (openTh != null) {
                        throw new HardwareInterfaceException("open failed: " + openTh, openTh);
                    }
                    // User may have selected another Interface while ISSD/USB open ran.
                    if (chip.getHardwareInterface() != aemon) {
                        log.info("openAEMonitor: interface changed during open of " + aemon
                                + "; closing abandoned open");
                        try {
                            aemon.close();
                        } catch (Exception closeEx) {
                            log.warning("closing abandoned open: " + closeEx);
                        }
                        aemon = null;
                        wantWaiting = true;
                        SessionCameraOpenCoordinator.viewerFinishedOpenAttempt(this, "interface-changed");
                        return;
                    }
                    if (suppressHardwareOpen || getPlayMode() == PlayMode.PLAYBACK || getPlayMode() == PlayMode.FILTER_INPUT) {
                        // File open won the race while USB open blocked; leave device but do not go LIVE.
                        log.info("openAEMonitor: playMode became PLAYBACK during aemon.open(); skipping LIVE");
                        SessionCameraOpenCoordinator.viewerFinishedOpenAttempt(this, "playback");
                        return;
                    }
                    hardwareOpenThread = null;
                    hardwareOpenTarget = null;
                    if (aemon instanceof USBInterface) {
                        USBInterface usb = (USBInterface) aemon;
                        String serial = null;
                        if ((usb.getStringDescriptors() != null) && (usb.getStringDescriptors().length == 3) && (usb.getStringDescriptors()[2] != null)) {
                            serial = usb.getStringDescriptors()[2];
                        }
                        rememberUsbIdentity(aemon, serial);
                    }
                    fixRecordingControls();
                    fixBiasgenControls();
                    fixDeviceControlMenuItems();
                    tickUs = aemon.getTimestampTickUs();
                    // note it is important that this openAEMonitor succeeed BEFORE aemon is assigned to biasgen,
                    // which immeiately tries to openAEMonitor and download biases, creating a storm of complaints if not sucessful!

                    if (aemon instanceof BiasgenHardwareInterface) {
                        Biasgen bg = chip.getBiasgen();
                        if (bg != null && !chip.isFirstHardwareUseHandled()) {
                            // Do not import here — that used to block before LIVE and flood SPI (DavisConfig).
                            // Mark handled, then import + UI after PlayMode.LIVE (see below).
                            final String defaultPath = chip.resolveDefaultPreferencesFile();
                            final boolean wantDefaults = !chip.isDefaultPreferencesLoadedOnce() && defaultPath != null;
                            chip.setFirstHardwareUseHandled(true);
                            final AEChip chipForUi = chip;
                            pendingFirstHardwareUseImport = wantDefaults;
                            pendingFirstHardwareUseUi = () -> {
                                try {
                                    log.info("running first-hardware-use UI for "
                                            + chipForUi.getClass().getSimpleName()
                                            + " (notifyDefaults=" + wantDefaults + ")");
                                    if (wantDefaults) {
                                        chipForUi.showDefaultPreferencesLoadedDialog(AEViewer.this, defaultPath);
                                    }
                                    showBiasgenOnEdt(true);
                                } catch (Throwable t) {
                                    log.log(java.util.logging.Level.WARNING, "First-hardware-use UI failed", t);
                                }
                            };
                        } else if ((bg != null) && !bg.isInitialized()) {
                            bg.showUnitializedBiasesWarningDialog(this);
                        }
                    }

                    if ((chip.getHardwareInterface() != null) && (chip.getHardwareInterface() instanceof AESequencerInterface)) {
                        // the 'chip's' hardware interface is a pure sequencer
                        enableMonSeqMenu(true);
                    }
                    if (getPlayMode() != PlayMode.SEQUENCING) {
                        wantLive = true;
                    }
                    // TODO interface should do this check nonmonotonic timestamps automatically
                    if ((aemon != null) && (aemon instanceof StereoPairHardwareInterface)) {
                        ((StereoPairHardwareInterface) aemon).setIgnoreTimestampNonmonotonicity(!checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.isSelected());
                    }

                    if (aemon instanceof HasUsbStatistics) {
                        printUSBStatisticsCBMI.setSelected(((HasUsbStatistics) aemon).isPrintUsbStatistics());
                    }
                    showUsbLinkOverlayAfterOpen();
                    } finally {
                        if (serialHeld) {
                            releaseUsbOpenSerialLock();
                        }
                    }
                } else if ((chip.getHardwareInterface() != null) && (chip.getHardwareInterface() instanceof AESequencerInterface)) {
                    // the 'chip's' hardware interface is a pure sequencer
                    enableMonSeqMenu(true);
                }
                //                setPlaybackControlsEnabledState(true); // TODO why set this true here? commented out

            } catch (BlankDeviceException bd) {
                if (!blankDeviceMessageShown) {
                    log.info(bd.getMessage() + " suppressing further blank device messages");
                    blankDeviceMessageShown = true;
                    int v = JOptionPane.showConfirmDialog(this, "<html>Blank Cypress FX2 found (" + aemon + "). <br>Caught exception " + bd.getMessage() + ".<br>Do you want to open the Cypress FX2 Programming utility?<p>Otherwise set the default firmware in the USB menu to download desired firmware to RAM for CypressFX2 devices");

                    if (v == JOptionPane.YES_OPTION) {
                        CypressFX2EEPROM instance = new CypressFX2EEPROM();
                        instance.setExitOnCloseEnabled(false);
                        instance.setVisible(true);
                    }
                }
                log.warning(bd.toString());
                aemon.close();
                nullifyHardware();

            } catch (Exception e) {
                log.warning(e.getMessage() + " (Could some other process have the device open, e.g. flashy or caer?)");
                UsbOpenTrace.event("open-failed", "open succeeds or ACCESS after hung sibling",
                        String.valueOf(e.getMessage()));
                log.log(Level.FINE, e.toString(), e);
                if (aemon instanceof PropheseeHardwareInterface) {
                    PropheseeHardwareInterface.maybeShowLinuxUdevAccessDialog(this, e);
                }
                WinUsbDriverHelp.maybeShowDialog(this, aemon, e);
                MacosLibusbHelp.maybeShowDialog(this, e);
                HardwareInterface failed = aemon;
                boolean stillPlugged = factoryCacheHasPhysicalDevice(failed);
                boolean keepForRetry = SessionCameraOpenCoordinator.hasOpenGrant(this)
                        && stillPlugged && !isUsbDeviceGone(e);
                if (keepForRetry) {
                    // Interface select + ACCESS: sibling closer may still hold WinUSB.
                    // Keep the bound wrapper so the next poll retries this camera,
                    // not a map-autobind of hung classic DVX.
                    log.warning("USB ACCESS after Interface select of still-enumerated "
                            + UsbIds.enumerationKey(failed) + "; will retry");
                    nullInterface = false;
                    keepInterfaceGrant = true;
                } else {
                    if (aemon != null) {
                        log.info("closing Monitor " + aemon.getClass().getSimpleName());
                        aemon.close();
                    }
                    nullifyHardware();
                    if (isUsbDeviceGone(e)) {
                        log.info("USB device gone; WAITING will scan again on plug (not blocking auto-open)");
                        nullInterface = false;
                        sessionDeviceGone = true;
                        HardwareInterfaceFactory.instance().markUsbEnumerationDirty();
                        resetWindowsUsbPoll("device removed");
                    } else {
                        // Stop WAITING from rebinding the same ghost device on the next poll
                        // (jAER 12:15: ACCESS loop after unplug; UI looked hung).
                        nullInterface = true;
                    }
                }
                setPlaybackControlsEnabledState(false);
                fixDeviceControlMenuItems();
                fixRecordingControls();
                fixBiasgenControls();
                wantWaiting = true;
            }
        }
        if (wantWaiting) {
            pendingFirstHardwareUseUi = null;
            pendingFirstHardwareUseImport = false;
            disarmEdtLivenessWatchdog();
            pendingOpeningCameraLabel = null;
            setPlayMode(PlayMode.WAITING);
            showWelcomeOverlay();
            if (!keepInterfaceGrant) {
                SessionCameraOpenCoordinator.viewerFinishedOpenAttempt(this,
                        sessionDeviceGone ? "device-gone" : "open-failed");
            }
            if (sessionDeviceGone) {
                SessionCameraOpenCoordinator.allowRememberedRebind(this);
            }
        } else if (wantLive && getPlayMode() == PlayMode.WAITING && !suppressHardwareOpen) {
            // Only WAITING→LIVE; never overwrite PLAYBACK/REMOTE/FILTER_INPUT (file-open race).
            pendingOpeningCameraLabel = null;
            ChipCanvas liveCanvas = getChipCanvas();
            if (liveCanvas != null) {
                liveCanvas.setWelcomeOverlay(null); // idle defaults if unplug returns to WAITING
            }
            setPlayMode(PlayMode.LIVE);
            liveOpenMisses = 0;
            SessionCameraOpenCoordinator.noteAcquiring(this);
            runPendingFirstHardwareUseAfterLive();
        }
    }

    /**
     * After AEChip matching and a successful USB open, show speed/topology on
     * the chip view for {@link ChipCanvas#USB_LINK_OVERLAY_MS}. Snapshot comes
     * from {@link LibUsbLinkInfo#logOnOpen}.
     */
    private void showUsbLinkOverlayAfterOpen() {
        LibUsbLinkInfo.Snapshot snap = LibUsbLinkInfo.lastOpen();
        if (snap == null || chip == null) {
            return;
        }
        ChipCanvas canvas = chip.getCanvas();
        if (canvas == null) {
            return;
        }
        canvas.showUsbLinkOverlay(snap.overlayText());
    }

    /**
     * After LIVE: import shipped defaults (batched SPI), then show dialog / Hardware Configuration.
     * Import runs on ViewLoop here so the title already shows LIVE and close/EDT stay responsive
     * once DavisConfig respects {@link Biasgen#isBatchEditOccurring()}.
     */
    private void runPendingFirstHardwareUseAfterLive() {
        final boolean doImport = pendingFirstHardwareUseImport;
        pendingFirstHardwareUseImport = false;
        final Runnable ui = pendingFirstHardwareUseUi;
        pendingFirstHardwareUseUi = null;
        if (!doImport && ui == null) {
            return;
        }
        armEdtLivenessWatchdog();
        try {
            if (doImport && chip != null) {
                log.info("importing first-hardware-use preferences after LIVE for "
                        + chip.getClass().getSimpleName());
                chip.maybeLoadDefaultPreferences();
            }
        } catch (Throwable t) {
            log.log(Level.WARNING, "First-hardware-use preference import failed", t);
        }
        if (ui == null) {
            disarmEdtLivenessWatchdog();
            return;
        }
        log.info("scheduling first-hardware-use UI after LIVE");
        // Delay so Biasgen.importPreferences' deferred sendConfiguration can finish before BiasgenFrame.
        javax.swing.Timer t = new javax.swing.Timer(1200, e -> {
            try {
                ui.run();
            } catch (Throwable ex) {
                log.log(Level.WARNING, "First-hardware-use UI failed", ex);
            } finally {
                disarmEdtLivenessWatchdog();
            }
        });
        t.setRepeats(false);
        t.start();
    }

    /**
     * If the EDT stops pumping for {@link #EDT_FREEZE_HALT_MS} during first-use open/import,
     * force-halt so the process is not left requiring Task Manager (window close never runs
     * when the EDT is deadlocked).
     */
    private void armEdtLivenessWatchdog() {
        edtHeartbeatMs.set(System.currentTimeMillis());
        javax.swing.Timer beat = new javax.swing.Timer(250, e -> {
            if (edtHeartbeatMs.get() != 0) {
                edtHeartbeatMs.set(System.currentTimeMillis());
            } else {
                ((javax.swing.Timer) e.getSource()).stop();
            }
        });
        beat.start();
        Thread watchdog = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(500);
                    long t0 = edtHeartbeatMs.get();
                    if (t0 == 0) {
                        return; // disarmed
                    }
                    long frozenMs = System.currentTimeMillis() - t0;
                    if (frozenMs > EDT_FREEZE_HALT_MS) {
                        System.err.println(String.format(
                                "AEViewer: EDT frozen for %d ms during first-hardware-use; Runtime.halt(1)",
                                frozenMs));
                        System.err.flush();
                        Runtime.getRuntime().halt(1);
                    }
                }
            } catch (InterruptedException e) {
                // exit
            }
        }, "AEViewer-EdtLiveness");
        watchdog.setDaemon(true);
        watchdog.start();
        log.info(String.format("Armed EDT liveness watchdog (%d ms -> Runtime.halt)", EDT_FREEZE_HALT_MS));
    }

    private void disarmEdtLivenessWatchdog() {
        edtHeartbeatMs.set(0);
    }

    /** True while a file open is in progress (ViewLoop should not open USB). */
    public boolean isSuppressHardwareOpen() {
        return suppressHardwareOpen;
    }

    /**
     * Call at the start of file open so ViewLoop stops touching USB and switches
     * to PLAYBACK (paused until the stream is ready).
     */
    public void beginFilePlaybackOpen() {
        suppressHardwareOpen = true;
        log.fine("beginFilePlaybackOpen: suppressHardwareOpen=true playMode=" + getPlayMode());
        if (aemon != null && aemon.isOpen() && aemon.isEventAcquisitionEnabled()) {
            try {
                aemon.setEventAcquisitionEnabled(false);
                eventAcquisitionPausedForPlayback = true;
                log.info("paused live event acquisition for file open");
            } catch (HardwareInterfaceException e) {
                log.warning("failed to pause live acquisition for file open: " + e.getMessage());
            }
        }
        // Force PLAYBACK even if already there so ViewLoop leaves LIVE/WAITING USB paths.
        if (getPlayMode() != PlayMode.PLAYBACK) {
            setPlayMode(PlayMode.PLAYBACK);
        } else {
            wakeViewLoopForPlayback();
        }
    }

    /**
     * Detach current chip HW under the viewLoop lock so LIVE acquisition stops
     * using it; does not call {@link HardwareInterface#close()}.
     */
    private HardwareInterface detachHardwareInterfaceForReset() {
        synchronized (viewLoop) {
            HardwareInterface hw = (chip != null) ? chip.getHardwareInterface() : null;
            if (!(hw instanceof USBInterface)) {
                return null;
            }
            if (chip != null) {
                chip.setHardwareInterface(null); // AEViewer will reopen when nullInterface is false
            }
            if (aemon == hw) {
                aemon = null;
            }
            nullInterface = false; // allow ViewLoop to reopen after reset
            return hw;
        }
    }

    /**
     * Sibling viewer: drop its USB HI so a bus reset cannot leave its AEReader
     * in native libusb. Blocks autobind ({@code nullInterface}) until the user
     * picks Interface again.
     */
    HardwareInterface detachUsbHardwareForBusReset() {
        final HardwareInterface hw;
        synchronized (viewLoop) {
            hw = (chip != null) ? chip.getHardwareInterface() : null;
            if (!(hw instanceof USBInterface)) {
                return null;
            }
            chip.setHardwareInterface(null);
            if (aemon == hw) {
                aemon = null;
            }
            nullInterface = true;
        }
        if (getPlayMode() == PlayMode.LIVE) {
            setPlayMode(PlayMode.WAITING);
        }
        return hw;
    }

    /**
     * After a hung {@code open()}, drop the wrapper so ViewLoop does not retry
     * the same object (its {@code synchronized open()/close()} is stuck in native USB).
     * Next Interface selection enumerates a fresh factory instance.
     */
    private void unbindAbandonedHardware(HardwareInterface hung) {
        if (hung != null) {
            abandonedHungHardware = hung;
        }
        HardwareInterface current = (chip != null) ? chip.getHardwareInterface() : null;
        if (current != null && hung != null && current != hung) {
            if (aemon == hung) {
                aemon = null;
            }
            log.info("Unbound hung hardware; keeping newly bound " + current);
            log.fine("unbindAbandonedHardware hung=" + hung + " kept=" + current + " " + UsbLog.t());
            clearOpeningCameraOverlay();
            return;
        }
        if (chip != null && (hung == null || current == hung || current == null)) {
            chip.setHardwareInterface(null);
        }
        aemon = null;
        // Stop auto-rebind; hung native USB still holds the device (ACCESS on retry).
        nullInterface = true;
        try {
            HardwareInterfaceFactory.instance().markUsbEnumerationDirty();
        } catch (Exception e) {
            log.fine("markUsbEnumerationDirty after abandoned open: " + e);
        }
        log.info("Unbound hung hardware; choose Interface again (do not retry the same USB wrapper)");
        log.fine("unbindAbandonedHardware hung=" + hung + " " + UsbLog.t());
        clearOpeningCameraOverlay();
    }

    /**
     * True when {@code open()} or {@code dvxConfig} is still in native USB on
     * this wrapper. {@code close()} on the same synchronized instance blocks.
     */
    private boolean isHungNativeHardware(HardwareInterface hw) {
        if (hw == null) {
            return false;
        }
        if (hw == abandonedHungHardware) {
            return true;
        }
        Thread opener = hardwareOpenThread;
        return hw == hardwareOpenTarget && opener != null && opener.isAlive();
    }

    private boolean shouldSkipCloseWait(HardwareInterface next, HardwareInterface closing) {
        if (closing == null) {
            return false;
        }
        if (next != null && !UsbIds.samePhysicalDevice(next, closing)) {
            return true;
        }
        if (next == null && (isHungNativeHardware(closing)
                || SessionCameraOpenCoordinator.isClassicDvxHardware(closing))) {
            return true;
        }
        return false;
    }

    /**
     * ViewLoop must not start the next {@code aemon.open()} while
     * {@code jaer-hw-close} is still in native USB. Wait up to
     * {@link #HARDWARE_CLOSE_JOIN_MS} when the closer is the same physical
     * device. A hung classic DVX closer must not delay DVS128 / Mini / Davis.
     * {@link #interruptViewloop()} from the same Interface click must not abort
     * this join.
     */
    private void awaitPendingHardwareClose() {
        final Thread busReset = usbBusResetCloser;
        if (busReset != null && busReset.isAlive()) {
            log.info("openAEMonitor: waiting for USB bus-reset close of all interfaces");
            final long busDeadline = System.currentTimeMillis() + HARDWARE_CLOSE_JOIN_MS;
            while (busReset.isAlive()) {
                long remaining = busDeadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                try {
                    busReset.join(Math.min(200L, remaining));
                } catch (InterruptedException e) {
                    if (viewLoop != null && viewLoop.stop) {
                        Thread.currentThread().interrupt();
                        log.info("openAEMonitor: ViewLoop stopping during USB bus-reset close wait");
                        return;
                    }
                    log.fine("openAEMonitor: interrupt during USB bus-reset close wait; still waiting");
                }
            }
            if (busReset.isAlive()) {
                log.warning("openAEMonitor: USB bus-reset close still alive after "
                        + HARDWARE_CLOSE_JOIN_MS
                        + " ms; opening next camera (native close skipped if AEReader hung)");
            }
        }
        final Thread closer = hardwareCloseThread;
        if (closer == null || !closer.isAlive()) {
            return;
        }
        HardwareInterface next = chip != null ? chip.getHardwareInterface() : null;
        HardwareInterface closing = hardwareCloseTarget;
        if (shouldSkipCloseWait(next, closing)) {
            log.info("openAEMonitor: not waiting for close of " + closing
                    + " before opening " + (next == null ? "unbound next camera" : next));
            return;
        }
        log.info("openAEMonitor: waiting for previous hardware close");
        final long deadline = System.currentTimeMillis() + HARDWARE_CLOSE_JOIN_MS;
        while (closer.isAlive()) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            try {
                closer.join(Math.min(200L, remaining));
            } catch (InterruptedException e) {
                if (viewLoop != null && viewLoop.stop) {
                    Thread.currentThread().interrupt();
                    log.info("openAEMonitor: ViewLoop stopping during previous close wait");
                    return;
                }
                log.fine("openAEMonitor: interrupt during previous close wait; still waiting");
            }
        }
        if (closer.isAlive()) {
            log.warning("openAEMonitor: previous close still alive after "
                    + HARDWARE_CLOSE_JOIN_MS
                    + " ms; opening next camera (native close skipped if AEReader hung)");
        }
    }

    /**
     * Close a detached hardware interface on a daemon thread. Waits up to
     * {@code timeoutMs} then abandons the close so the EDT cannot hang forever
     * (seen with NRV {@code LibUsb.close} / transfer teardown).
     *
     * @return the closer thread, or null if {@code hw} is null
     */
    private Thread closeHardwareInterfaceWithTimeout(HardwareInterface hw, long timeoutMs, String actionLabel) {
        if (hw == null) {
            return null;
        }
        return closeHardwareInterfacesWithTimeout(List.of(hw), timeoutMs, actionLabel, hw, false);
    }

    /**
     * Close detached hardware interfaces on one daemon thread. ViewLoop joins
     * that thread before the next {@code open()}. When {@code busReset} is
     * true, every viewer waits ({@link #usbBusResetCloser}).
     */
    private Thread closeHardwareInterfacesWithTimeout(List<HardwareInterface> hws, long timeoutMs,
            String actionLabel, HardwareInterface waitTarget, boolean busReset) {
        List<HardwareInterface> closable = new ArrayList<>();
        for (HardwareInterface hw : hws) {
            if (hw == null) {
                continue;
            }
            if (isHungNativeHardware(hw)) {
                log.info(actionLabel + ": not closing hung " + hw);
                abandonedHungHardware = hw;
                continue;
            }
            closable.add(hw);
        }
        if (closable.isEmpty()) {
            return null;
        }
        final HardwareInterface logged = waitTarget != null ? waitTarget : closable.get(closable.size() - 1);
        Thread closer = new Thread(() -> {
            for (HardwareInterface hw : closable) {
                log.fine(actionLabel + " close() begin " + hw + " " + UsbLog.t());
                try {
                    hw.close();
                    log.info(actionLabel + ": closed " + hw);
                } catch (Exception ex) {
                    log.warning(actionLabel + ": exception closing device: " + ex);
                }
                log.fine(actionLabel + " close() end " + UsbLog.t());
            }
        }, "jaer-hw-close");
        closer.setDaemon(true);
        hardwareCloseTarget = logged;
        hardwareCloseThread = closer;
        if (busReset) {
            usbBusResetCloser = closer;
        }
        closer.start();
        // Do not block the EDT: schedule a timeout watcher on a background thread.
        Thread watcher = new Thread(() -> {
            try {
                closer.join(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (closer.isAlive()) {
                log.warning(String.format(
                        "%s: hardware close timed out after %d ms; abandoning stuck close of %s (daemon thread). UI continues; unplug/replug if device stays busy.",
                        actionLabel, timeoutMs, logged));
                SwingUtilities.invokeLater(() -> showActionText(actionLabel + " timed out"));
            } else {
                SwingUtilities.invokeLater(() -> showActionText(actionLabel + " done"));
            }
        }, "jaer-hw-close-watch");
        watcher.setDaemon(true);
        watcher.start();
        return closer;
    }

    /**
     * Newbie-friendly Interface menu label: prefer AEChip family names from
     * {@link UsbDevices} (e.g. {@code Davis346 / SciDVS}) over raw HI class
     * names like {@code DAViSFX3HardwareInterface}. Opened devices keep their
     * USB product string from {@link HardwareInterface#toString()}.
     */
    private String interfaceMenuLabel(HardwareInterface hw) {
        if (hw == null) {
            return "USB";
        }
        if (hw.isOpen()) {
            return hw.toString();
        }
        java.util.List<Class<? extends AEChip>> matches
                = LiveDeviceChipDetector.findMatches(hw, chipClassNames);
        if (matches.isEmpty()) {
            matches = LiveDeviceChipDetector.findMatches(hw, Arrays.asList(DEFAULT_CHIP_CLASS_NAMES));
        }
        if (!matches.isEmpty()) {
            String family = chipFamilyMenuLabel(matches);
            UsbIds.Pair ids = UsbIds.peek(hw);
            String topo = ViewerInterfaceBindingMap.busAddr(UsbIds.enumerationKey(hw));
            String id = ids.isKnown() ? ids.key() : "";
            if (topo != null) {
                return id.isEmpty() ? family + " " + topo : family + " (" + id + " " + topo + ")";
            }
            return ids.isKnown() ? family + " (" + id + ")" : family;
        }
        return hw.toString();
    }

    /** Collapse Davis346blue/red/… to {@code Davis346}; keep SciDVS / DVXplorer / DVXplorerMicro distinct. */
    private static String chipFamilyMenuLabel(java.util.List<Class<? extends AEChip>> matches) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (Class<? extends AEChip> c : matches) {
            if (c == null) {
                continue;
            }
            String s = c.getSimpleName();
            if (s.startsWith("Davis346")) {
                names.add("Davis346");
            } else if (s.startsWith("Davis240")) {
                names.add("Davis240");
            } else if (s.startsWith("Davis128") || s.equals("DAVIS128")) {
                names.add("Davis128");
            } else {
                names.add(s);
            }
        }
        return names.isEmpty() ? "USB camera" : String.join(" / ", names);
    }

    /**
     * Close chip HW / {@link #aemon} for an AEChip class switch. When
     * {@link #suppressHardwareOpen} (file open), close asynchronously so a stuck
     * USB driver cannot hang the EDT.
     */
    private void closeHardwareInterfaceForChipSwitch() {
        final HardwareInterface hw = (chip != null) ? chip.getHardwareInterface() : null;
        final AEMonitorInterface mon = aemon;
        if (chip != null) {
            chip.setHardwareInterface(null);
        }
        aemon = null;
        if (hw == null && mon == null) {
            return;
        }
        Runnable close = () -> {
            try {
                if (mon != null && mon.isOpen()) {
                    mon.close();
                }
            } catch (Exception e) {
                log.warning("async aemon.close during chip switch: " + e);
            }
            try {
                if (hw != null && hw != mon && hw.isOpen()) {
                    hw.close();
                }
            } catch (Exception e) {
                log.warning("async hardwareInterface.close during chip switch: " + e);
            }
        };
        // Never block the EDT on USB close (NRV/libusb can hang). setAeChipClass
        // runs on the EDT, including live-device chip offers marshaled from ViewLoop.
        if (suppressHardwareOpen || SwingUtilities.isEventDispatchThread()) {
            Thread t = new Thread(close, "jaer-async-hw-close");
            t.setDaemon(true);
            t.start();
            log.info(suppressHardwareOpen
                    ? "Closing live hardware asynchronously for file playback chip switch"
                    : "Closing live hardware asynchronously for AEChip switch on EDT");
        } else {
            close.run();
        }
    }

    /**
     * Call when file open finishes (success or cancel) so LIVE can resume later.
     */
    public void endFilePlaybackOpen() {
        suppressHardwareOpen = false;
        log.fine("endFilePlaybackOpen: suppressHardwareOpen=false");
        wakeViewLoopForPlayback();
    }

    /** Wake ViewLoop from pause wait so PLAYBACK can proceed.
     * Do <b>not</b> {@link #interruptViewloop()}: {@code Thread.interrupt} closes
     * {@link java.nio.channels.FileChannel} ({@code ClosedByInterruptException}),
     * which permanently breaks AEDAT-4/AEDAT-2 playback. See AEPlayer comment. */
    private void wakeViewLoopForPlayback() {
        synchronized (viewLoopPauseLock) {
            viewLoopPauseLock.notifyAll();
        }
    }

    /**
     * Pause playback and keep ViewLoop out of grabInput/paint for File → Save As.
     * Does not {@link #interruptViewloop()}: interrupt closes the playback
     * {@code FileChannel}.
     */
    public void suspendViewLoopForOfflineExport() {
        viewLoopSuspendedForOfflineExport = true;
        setPaused(true);
    }

    /**
     * Block until ViewLoop has finished any in-flight grabInput and is parked,
     * or until {@code timeoutMs}. Call from the Save As worker, not the ViewLoop
     * thread.
     *
     * @return true if ViewLoop is parked and will not touch the stream
     */
    public boolean waitUntilViewLoopParkedForOfflineExport(long timeoutMs) {
        if (Thread.currentThread() == viewLoop) {
            throw new IllegalStateException("cannot wait for ViewLoop park from ViewLoop");
        }
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        synchronized (viewLoopPauseLock) {
            while (!viewLoopParkedForOfflineExport && viewLoopSuspendedForOfflineExport) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    log.warning("ViewLoop did not park for Save As within " + timeoutMs
                            + " ms; export may still contend with grabInput");
                    return false;
                }
                try {
                    viewLoopPauseLock.wait(Math.min(left, 200L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return viewLoopParkedForOfflineExport;
                }
            }
        }
        return viewLoopParkedForOfflineExport;
    }

    /**
     * End Save As exclusive use of the stream and restore the previous pause
     * state. Wakes ViewLoop with notify, not interrupt.
     */
    public void resumeViewLoopAfterOfflineExport(boolean restorePaused) {
        setPaused(restorePaused); // still suspended so setPaused will not interrupt FileChannel
        viewLoopSuspendedForOfflineExport = false;
        synchronized (viewLoopPauseLock) {
            viewLoopPauseLock.notifyAll();
        }
    }

    public boolean isViewLoopSuspendedForOfflineExport() {
        return viewLoopSuspendedForOfflineExport;
    }

    void setPlaybackControlsEnabledState(boolean yes) {
        //        log.info("*****************************************************       setting playback controls enabled = "+yes);
        recordingButton.setEnabled(!yes);

        if (DVS128.class.isInstance(chip)) {
            // We don't want the HW configuration button to be visible on DVS128 (ticket #13),
            // when in playback mode.
            biasesToggleButton.setEnabled(!yes);
        } else {
            // On others, it seems to be needed for some settings (ticket #75).
            biasesToggleButton.setEnabled(true);
        }
        closeMenuItem.setEnabled(yes);
        saveAsMenuItem.setEnabled(yes);
        showFileInfoMenuItem.setEnabled(yes && isShowFileInfoAvailable());
        increasePlaybackSpeedMenuItem.setEnabled(yes);
        decreasePlaybackSpeedMenuItem.setEnabled(yes);
        rewindPlaybackMenuItem.setEnabled(yes);
        flextimePlaybackEnabledCheckBoxMenuItem.setEnabled(yes);
        togglePlaybackDirectionMenuItem.setEnabled(yes);
        clearMarksMI.setEnabled(yes);
        setMarkInMI.setEnabled(yes);
        setMarkOutMI.setEnabled(yes);
        //        if ( !playerControlPanel.isVisible() ){ // TODO why only do this if not visible?
        playerControlPanel.setVisible(yes);
        //        }
    }

    /**
     * This thread is the main animation loop that acquires events and renders
     * them to the canvas for active rendering. The other components render
     * themselves on the usual Swing rendering thread.
     */
    class ViewLoop extends Thread {

        Graphics2D g = null;
        volatile boolean singleStepEnabled = false, doSingleStep = false;
        volatile boolean fastForward = false; // flag set by fastForward() by e.g. EventFilter to skip packet rendering
        int numRawEvents, numFilteredEvents;
        private EngineeringFormat engFmt = new EngineeringFormat();
        private long beforeTime = 0, afterTime;
        volatile boolean stop = false;
        private AEPacketRaw emptyRawPacket;
        private EventPacket emptyCookedPacket;
        private long lastViewLoopHeartbeatMs;
        /** True when this iteration used HW USB typed PacketBundle (no extractBundle). */
        private boolean viewLoopUsedHwTypedBundle;

        public ViewLoop() {
            super();
            setName("AEViewer.ViewLoop");
        }

        /**
         * The main loop of AEViewer - this is the 'game loop' of the program.
         */
        @Override
        public void run() { // don't know why this needs to be thread-safe
            emptyCookedPacket = new EventPacket(chip.getEventClass());
            emptyRawPacket = new AEPacketRaw(0);
            EventPacket cookedPacket = new EventPacket(chip.getEventClass());
            PacketBundle cookedBundle = new PacketBundle();
            AEPacketRaw rawPacket = new AEPacketRaw();
            while (!isVisible()) {
                try {
                    log.info("sleeping until isVisible()==true");
                    Thread.sleep(1000); // sleep to let components realize on screen - may be crashing opengl on nvidia drivers if we draw to unrealized components
                } catch (InterruptedException e) {
                }
            }
            while (stop == false/*&& !isInterrupslsted()*/) { // the only way to break out of the run loop is either setting stop true or by some uncaught exception.
                getRenderer().clearPacketRenderSkipDecision();
                boolean skipRendering = false;
                viewLoopUsedHwTypedBundle = false;
                setTitleAccordingToState();
                pauseIdleWaitIfNeeded();
                if (stop) {
                    log.info("breaking out of view loop after pauseIdleWaitIfNeeded() because stop=true");
                    break;
                }
                if (viewLoopSuspendedForOfflineExport) {
                    // Still owned by Save As (spurious wakeup). Do not grabInput/render.
                    continue;
                }
                stopRecordingIfTimeLimitReached();
                // Heartbeat when FINE: proves ViewLoop is alive vs stuck in USB/JOGL.
                if (log.isLoggable(Level.FINE)) {
                    long now = System.currentTimeMillis();
                    if (now - lastViewLoopHeartbeatMs > 2000) {
                        lastViewLoopHeartbeatMs = now;
                        log.fine("ViewLoop heartbeat playMode=" + getPlayMode()
                                + " paused=" + isPaused()
                                + " interrupted=" + isInterrupted()
                                + " suppressHW=" + suppressHardwareOpen
                                + " stream=" + (getAePlayer() != null && getAePlayer().getAEInputStream() != null));
                    }
                }
                // unless fastForward is set, in which case there is no delay
                if (!isPaused() || (isSingleStep() && !isInterrupted())) { // we check interrupted to make sure we are not getting data after being interrupted
                    // if !paused we always get data. below, if singleStepEnabled, we set paused after getting data.
                    // when the user unpauses via menu, we disable singleStepEnabled
                    // another flag, doSingleStep, tells loop to do a single data acquisition and then pause again
                    // in this branch, getString new data to show
                    getFrameRater().takeBefore();

                    // Grab input from one of various sources
                    if (getPlayMode() == PlayMode.FILTER_INPUT) {
                        try {
                            if (cookedPacket == null) {
                                cookedPacket = new EventPacket(chip.getEventClass());
                            }
                            cookedPacket = filterChain.filterPacket(cookedPacket);
                            cookedBundle.clear();
                            cookedBundle.addAllowEmpty(cookedPacket);
                            rawPacket = getChip().getEventExtractor().reconstructRawPacket(cookedPacket); // so that we can log or stream to network
                            numEvents = cookedPacket.getSize();
                        } catch (Exception e) {
                            log.warning("Caught " + e + ", disabling all filters. See following stack trace.");
                            log.log(Level.SEVERE, e.toString(), e);

                            log.log(Level.WARNING, "Filter exception", e);
                            for (EventFilter f : filterChain) {
                                f.setFilterEnabled(false);
                            }
                        }

                    } else {
                        // jAER 3.0: prefer USB-level typed PacketBundle when the HW interface supplies it
                        PacketBundle hwBundle = null;
                        viewLoopUsedHwTypedBundle = false;
                        if ((getPlayMode() == PlayMode.LIVE) || (getPlayMode() == PlayMode.SEQUENCING)) {
                            try {
                                openAEMonitor();
                                if (suppressHardwareOpen || getPlayMode() == PlayMode.PLAYBACK || getPlayMode() == PlayMode.FILTER_INPUT) {
                                    // File open flipped mode while we were opening USB — fall through to grabInput.
                                    hwBundle = null;
                                } else if ((aemon != null) && aemon.isOpen()) {
                                    hwBundle = aemon.acquireAvailablePacketBundle();
                                }
                            } catch (Exception ex) {
                                if (ex instanceof HardwareInterfaceException
                                        && ex.getMessage() != null
                                        && ex.getMessage().contains("failed to queue USB IN")) {
                                    log.warning("acquireAvailablePacketBundle: " + ex.getMessage()
                                            + " (will retry after USB settle)");
                                } else {
                                    log.log(Level.WARNING, "acquireAvailablePacketBundle failed, falling back to raw extract", ex);
                                }
                                hwBundle = null;
                                if (ex instanceof HardwareInterfaceException
                                        && !(SessionCameraOpenCoordinator.hasOpenGrant(AEViewer.this)
                                        && factoryCacheHasPhysicalDevice(aemon))) {
                                    // AEReader failed to queue USB IN then USBTransferThread
                                    // shutdown closed the wrapper. WAITING autobind every 1 s
                                    // was Davis346 LIVE/WAITING blink (jAER 3:27:31).
                                    // ACCESS after Interface on a still-enumerated device is retried.
                                    nullInterface = true;
                                }
                            }
                        }
                        if (hwBundle != null) {
                            viewLoopUsedHwTypedBundle = true;
                            SessionCameraOpenCoordinator.noteAcquiring(AEViewer.this);
                            rawPacket = hwBundle.getRawPacket();
                            cookedBundle = hwBundle;
                            if (cookedBundle.isEmpty()) {
                                // Still finish FrameRater sample so close/pacing stay responsive
                                getFrameRater().takeAfter();
                                paceViewLoopFrame();
                                continue;
                            }
                        } else {
                            rawPacket = grabInput();
                            if (rawPacket == null) {
                                log.fine("null rawPacket, probably at OUT marker or end of file");
                                paceViewLoopFrame();
                                continue;
                            }
                        }

                        numRawEvents = rawPacket != null ? rawPacket.getNumEvents() : cookedBundle.getNumPolarityEvents();
                        final boolean filtersNeeded = chip.getFilterChain().isAnyFilterEnabled() || isRecordFilteredEventsEnabled();
                        // Never skip rendering while writing synchronized AVI frames — every packet must paint.
                        if (!isPaused() && !isJaerAviRecordingActive() && getRenderer().isPacketLevelRenderSkipping()) {
                            skipRendering = getRenderer().advanceSkipRenderSlot();
                        }
                        if (skipRendering && !filtersNeeded) {
                            numEvents = cookedBundle != null && !cookedBundle.isEmpty()
                                    ? cookedBundle.getNumPolarityEvents()
                                    : numRawEvents;
                            numFilteredEvents = numEvents;
                            if (cookedBundle != null) {
                                cookedPacket = firstEventPacket(cookedBundle);
                                chip.setLastData(cookedPacket);
                                chip.setLastBundle(cookedBundle);
                            } else {
                                chip.setLastData(cookedPacket);
                            }
                            if (isRecordingEnabled() & !isRecordingPaused()) {
                                recordPacket(rawPacket, null, cookedBundle);
                            }
                            boolean breakout = writeOutputStreams(rawPacket, null);
                            if (breakout) {
                                break;
                            }
                            singleStepDone();
                            getFrameRater().takeAfter();
                            getRenderer().adaptRenderSkipping();
                            renderCount++;
                            paceViewLoopFrame();
                            continue;
                        }
                        if (hwBundle == null) {
                            cookedBundle = extractBundle(rawPacket);
                        }
                        // AEDAT-4: inject FRME/IMUS decoded for this time slice
                        if (getAePlayer() != null
                                && getAePlayer().getAEInputStream() instanceof Aedat4FileInputStream) {
                            if (cookedBundle == null) {
                                cookedBundle = new PacketBundle();
                            }
                            ((Aedat4FileInputStream) getAePlayer().getAEInputStream()).appendTypedPackets(cookedBundle);
                        }
                        if (cookedBundle == null || cookedBundle.isEmpty()) {
                            // Mid-USB APS-only slices can yield empty typed bundles; do not spam SEVERE.
                            log.fine("packet bundle empty after extract (raw may be mid-frame APS only)");
                            paceViewLoopFrame();
                            continue;
                        }
                        cookedBundle = filterBundle(cookedBundle);
                        cookedPacket = firstEventPacket(cookedBundle);
                        if (cookedPacket == null) {
                            // Frame/IMU-only slice: keep empty polarity packet for stats/compat
                            cookedPacket = emptyCookedPacket;
                            cookedPacket.clear();
                        }
                        numEvents = cookedBundle.getNumPolarityEvents();
                        if (fastForward) { // maybe a filter set this flag.
                            fastForward = false;
                            continue;
                        }

                    }
                    chip.setLastData(cookedPacket);// set the rendered data for use by various methods
                    chip.setLastBundle(cookedBundle);

                    // if we are recording data to disk do it here
                    if (isRecordingEnabled() & !isRecordingPaused()) {
                        // AEDAT-2 needs raw AE; when USB demux drops APS dual-write, reconstruct polarity
                        if (rawPacket == null && cookedPacket != null && aedat4RecordingOutputStream == null) {
                            rawPacket = extractor.reconstructRawPacket(cookedPacket);
                        }
                        recordPacket(rawPacket, cookedPacket, cookedBundle);
                    }

                    // Write the ouput to whatever streams need it
                    boolean breakout = writeOutputStreams(rawPacket, cookedPacket);
                    if (breakout) {
                        break;
                    }

                    singleStepDone(); // if doing single colorContrastAdditiveStep, mark it done

                } // if (!isPaused() || isSingleStep())

                if (stop) {
                    log.info("breaking out of view loop before rendering because stop=true");
                    break;
                }
                if ((cookedBundle != null && !cookedBundle.isEmpty()) || (cookedPacket != null)) {
                    // we only got new events if we were NOT paused. but now we can apply filters, different rendering methods, etc in 'paused' condition
                    try {
                        boolean skipRequested = isSkipChipRenderingRequested() && !isJaerAviRecordingActive();
                        boolean skipChipGfx = skipRendering || isRosOutputSkipChipRendering()
                                || isOpenCvOutputSkipChipRendering() || skipRequested;
                        if (!skipChipGfx) {
                            renderBundle(cookedBundle, cookedPacket);
                            OpenCVOutput opencvOut = findOpenCvOutput();
                            if (opencvOut != null) {
                                opencvOut.publishChipViewAfterRender();
                            }
                        } else if (isShowRosOutputOverlay() || isShowOpenCvOutputOverlay() || skipRequested) {
                            // Skip pixmap render(); paint a blank canvas + overlay (no APS/IMU/markers).
                            chipCanvas.paintFrame();
                        }
                    } catch (RuntimeException e) {
                        String cause = " unknown cause";
                        if (e.getCause() != null) {
                            cause = e.getCause().toString();
                        }
                        log.warning("caught " + e.toString() + " caused by " + cause);
                        log.log(Level.SEVERE, e.toString(), e);
                    }
                    if (cookedPacket == null) {
                        log.warning("packet became null after rendering");
                        paceViewLoopFrame();
                        continue;
                    }
                    numFilteredEvents = cookedPacket.getSizeNotFilteredOut();
                    makeStatisticsLabel(cookedPacket);
                }
                getFrameRater().takeAfter();
                if (LiveAcquisitionBench.isEnabled() && ((getPlayMode() == PlayMode.LIVE) || (getPlayMode() == PlayMode.SEQUENCING))) {
                    final int polCount = cookedBundle != null ? cookedBundle.getNumPolarityEvents()
                            : (cookedPacket != null ? cookedPacket.getSize() : 0);
                    final int rawCount = rawPacket != null ? rawPacket.getNumEvents() : 0;
                    final boolean overrun = rawPacket != null && rawPacket.overrunOccuredFlag;
                    final String chipName = chip != null ? chip.getClass().getSimpleName() : "";
                    String driverName = "";
                    if (aemon != null) {
                        try {
                            driverName = aemon.getTypeName();
                        } catch (Exception ignored) {
                            driverName = aemon.getClass().getSimpleName();
                        }
                    }
                    final long loopNs = Math.max(0L, getFrameRater().getLastDtNs());
                    LiveAcquisitionBench.record(chipName, driverName, viewLoopUsedHwTypedBundle,
                            polCount, rawCount, overrun, loopNs);
                }
                getRenderer().adaptRenderSkipping();
                renderCount++;
                paceViewLoopFrame();
            } // while (stop == false): end of run() loop - main loop of AEViewer.ViewLoop

            // Loop Cleanup
            log.info("AEViewer.run() ending: stop=" + stop + " isInterrupted=" + isInterrupted());
            // Hardware close is done on the EDT via cleanup(); closing here during stop=true
            // can deadlock with stopViewLoopForExit() (synchronized NRV close + USB join).
            if (aemon != null && !stop) {
                aemon.close();
            }
            if (unicastOutput != null) {
                unicastOutput.close();
            }
            if (unicastInput != null) {
                unicastInput.close();
            }

        } // viewLoop.run()

        private void renderPacket(EventPacket cookedPacket) {
            final JaerAviWriter aviWriter = getActiveJaerAviWriter();
            final boolean forceRenderForVideo = aviWriter != null;
            if (aePlayer.isChoosingFile() || (cookedPacket == null)
                    || (!forceRenderForVideo && !isRenderBlankFramesEnabled() && (cookedPacket.getSize() == 0))) {
                if (aviWriter != null) {
                    aviWriter.cancelPendingFrameCapture();
                }
                return;
            } // don't render while filechooser is active
            if (!(getRenderer().isAccumulateEnabled() && isPaused())) {
                getRenderer().render(cookedPacket);
            }
            paintFrameAndAwaitVideoCapture(aviWriter);
        } // renderEvents

        /**
         * jAER 3.0: render a typed {@link PacketBundle}. DavisRenderer applies
         * Frame/IMU packets; polarity still goes through EventPacket render.
         */
        private void renderBundle(PacketBundle bundle, EventPacket cookedPacket) {
            final JaerAviWriter aviWriter = getActiveJaerAviWriter();
            final boolean forceRenderForVideo = aviWriter != null;
            if (aePlayer.isChoosingFile()) {
                if (aviWriter != null) {
                    aviWriter.cancelPendingFrameCapture();
                }
                return;
            }
            if (bundle == null || bundle.isEmpty()) {
                renderPacket(cookedPacket);
                return;
            }
            if (!forceRenderForVideo && !isRenderBlankFramesEnabled() && bundle.getNumPolarityEvents() == 0
                    && bundle.getFirstFramePacket() == null && bundle.getFirstImuPacket() == null) {
                if (aviWriter != null) {
                    aviWriter.cancelPendingFrameCapture();
                }
                return;
            }
            if (!(getRenderer().isAccumulateEnabled() && isPaused())) {
                AEChipRenderer ren = getRenderer();
                if (ren instanceof DavisRenderer) {
                    ((DavisRenderer) ren).render(bundle);
                } else {
                    ren.render(bundle);
                }
            }
            paintFrameAndAwaitVideoCapture(aviWriter);
        }

        /**
         * Active-renders the canvas and, when AVI export is active, captures that
         * frame inside {@code paintFrame()} (via {@link JaerAviWriter#annotate})
         * before the view loop advances. Encoding therefore paces the loop
         * (export may run slower than real time) and yields one AVI frame per
         * rendered view at the AEViewer target rate.
         */
        private void paintFrameAndAwaitVideoCapture(JaerAviWriter aviWriter) {
            if (aviWriter != null && !isActiveRenderingEnabled()) {
                // Passive repaint is not synchronized with the view loop.
                setActiveRenderingEnabled(true);
            }
            if (!isActiveRenderingEnabled()) {
                chipCanvas.repaint();
                return;
            }
            if (aviWriter != null) {
                aviWriter.requestFrameCapture();
            }
            try {
                // JaerAviWriter.annotate reads the back buffer and writeFrame() runs
                // before display() returns, so the loop waits for encode to finish.
                chipCanvas.paintFrame();
            } catch (RuntimeException e) {
                if (aviWriter != null) {
                    aviWriter.cancelPendingFrameCapture();
                }
                throw e;
            }
            if (aviWriter != null && aviWriter.isFrameCapturePending()) {
                // annotate did not run (e.g. annotation disabled, reverse playback)
                aviWriter.cancelPendingFrameCapture();
                log.fine("AVI frame capture skipped this cycle (annotate did not capture)");
            }
        }

        private EventPacket extractPacket(AEPacketRaw aeRaw) {
            boolean subsamplingEnabled = getRenderer().isSubsamplingEnabled();
            if (isPaused()) {
                extractor.setSubsamplingEnabled(false);
            }
            AEViewer.this.extractor = AEViewer.this.chip.getEventExtractor();   // Jaer3BufferParser will update the extractor in the chip, so we should monitor this value all the time
            EventPacket packet = extractor.extractPacket(aeRaw);
            packet.setRawPacket(aeRaw);
            if (isPaused()) {
                extractor.setSubsamplingEnabled(subsamplingEnabled);
            }

            return packet;
        }

        private PacketBundle extractBundle(AEPacketRaw aeRaw) {
            boolean subsamplingEnabled = getRenderer().isSubsamplingEnabled();
            if (isPaused()) {
                extractor.setSubsamplingEnabled(false);
            }
            AEViewer.this.extractor = AEViewer.this.chip.getEventExtractor();
            PacketBundle bundle = extractor.extractBundle(aeRaw);
            if (bundle != null) {
                bundle.setRawPacket(aeRaw);
            }
            if (isPaused()) {
                extractor.setSubsamplingEnabled(subsamplingEnabled);
            }
            return bundle;
        }

        private static EventPacket firstEventPacket(PacketBundle bundle) {
            if (bundle == null) {
                return null;
            }
            EventPacket polarity = bundle.getFirstPolarityPacket();
            if (polarity != null) {
                return polarity;
            }
            for (TypedDataPacket p : bundle) {
                if (p instanceof EventPacket) {
                    return (EventPacket) p;
                }
            }
            return null;
        }

        private PacketBundle filterBundle(PacketBundle input) {
            if (playerControls.isSliderBeingAdjusted() || getAePlayer().getPlaybackDirection() == AbstractAEPlayer.PlaybackDirection.Backward) {
                return input;
            }
            if ((filterChain.getProcessingMode() == FilterChain.ProcessingMode.RENDERING) || (getPlayMode() != PlayMode.LIVE)) {
                try {
                    return filterChain.filterBundle(input);
                } catch (Exception e) {
                    log.warning("Caught " + e + ", disabling all filters. See following stack trace.");
                    log.log(Level.SEVERE, e.toString(), e);
                    for (EventFilter f : filterChain) {
                        f.setFilterEnabled(false);
                    }
                }
            }
            return input;
        }

        /**
         * Sets the stop flag so that the ViewLoop exits the run method on the
         * next iteration.
         *
         */
        public void stopThread() {
            stop = true;
            log.info("Set stop on main processing thread");
        }

        /**
         * Grabs the input data from whatever source is currently supplying it,
         * e.g. a file, live sensor input, etc.
         *
         * @return returns false if there are no error and data was acquired,
         * true if there is no data and the code should continue from here and
         * skip future processing
         */
        @SuppressWarnings("LoggerStringConcat")
        private AEPacketRaw grabInput() {

            switch (getPlayMode()) {
                case SEQUENCING:
                    HardwareInterface chipHardwareInterface = chip.getHardwareInterface();

                    if (chipHardwareInterface == null) {
                        log.warning("AE monitor/sequencer became null while sequencing");
                        setPlayMode(PlayMode.WAITING);
                        break;
                    }
                    AESequencerInterface aemonseq = (AESequencerInterface) chip.getHardwareInterface();
                    int nToSend = aemonseq.getNumEventsToSend();
                    int position = 0;
                    if (nToSend != 0) {
                        position = (int) ((playerControls.getPlayerSlider().getMaximum() * (float) aemonseq.getNumEventsSent()) / nToSend);
                    }

                    sliderDontProcess = true;
                    playerControls.getPlayerSlider().setValue(position);
                    if (!(chip.getHardwareInterface() instanceof AEMonitorInterface)) {
                        return emptyRawPacket; // if we're a monitor plus sequencer than go on to monitor events, otherwise break out since there are no events to monitor
                    }
                case LIVE:
                    if (aemon != null && aemon.isOpen()) {
                        liveOpenMisses = 0;
                    } else {
                        if (!nullInterface && !hardwareSwitchInProgress) {
                            openAEMonitor();
                        }
                        if ((aemon == null) || !aemon.isOpen()) {
                            liveOpenMisses++;
                            if (liveOpenMisses < 3 && !hardwareSwitchInProgress) {
                                try {
                                    Thread.sleep(150);
                                } catch (InterruptedException e) {
                                    log.fine("LIVE reopen miss sleep interrupted");
                                }
                                return emptyRawPacket;
                            }
                            liveOpenMisses = 0;
                            setPlayMode(PlayMode.WAITING);
                            if (!hardwareSwitchInProgress) {
                                showWelcomeOverlay();
                            }
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                log.warning("LIVE openAEMonitor sleep interrupted");
                            }
                            return emptyRawPacket;
                        }
                        liveOpenMisses = 0;
                    }
                    droppedDataInfo = aemon.getDroppedDataInfo();
                    try {
                        aemon = (AEMonitorInterface) chip.getHardwareInterface(); // TODOkeep setting aemon to be chip's interface, this is kludge
                        if (aemon == null) {
                            log.warning("AEViewer.ViewLoop.run(): AEMonitorInterface became null during acquisition");
                            throw new HardwareInterfaceException("hardware interface became null");
                        }
                        AEPacketRaw liveRaw = aemon.acquireAvailableEventsFromDriver();
                        SessionCameraOpenCoordinator.noteAcquiring(AEViewer.this);
                        return liveRaw;
                    } catch (HardwareInterfaceException | IllegalArgumentException e) {
                        if (stop) {
                            break; // break out of loop if this aquisition thread got HardwareInterfaceException because we are exiting
                        }
                        setPlayMode(PlayMode.WAITING);
                        log.warning("while acquiring data caught " + e.toString());
                        if (aemon != null) {
                            aemon.close(); // TODO check if this is OK -tobi
                        }
                        nullifyHardware();
                        showWelcomeOverlay();
                        SessionCameraOpenCoordinator.viewerFinishedOpenAttempt(AEViewer.this, "acquire-failed");
//                        stopMe();

                        return emptyRawPacket;
                    } catch (ClassCastException cce) {
                        setPlayMode(PlayMode.WAITING);
                        log.warning("Interface changed out from under us: " + cce.toString());
                        log.log(Level.SEVERE, cce.toString(), cce);
                        nullifyHardware();
                        showWelcomeOverlay();
                        SessionCameraOpenCoordinator.viewerFinishedOpenAttempt(AEViewer.this, "acquire-failed");
                        return emptyRawPacket;
                    }
                case PLAYBACK:
                    // Clear stale interrupt before NIO reads — interrupt closes FileChannel.
                    if (interrupted()) {
                        // log.fine("ViewLoop.grabInput PLAYBACK: cleared interrupt flag before FileChannel read");
                    }
                    // log.fine("ViewLoop.grabInput PLAYBACK paused=" + isPaused() + ...);
                    getAePlayer().adjustTimesliceForRealtimePlayback();
                    droppedDataInfo = DroppedDataInfo.none();
                    AEPacketRaw pb = getAePlayer().getNextPacket(aePlayer);
                    // log.fine("ViewLoop.grabInput PLAYBACK packet n=" + (pb == null ? -1 : pb.getNumEvents()));
                    return pb;
                case REMOTE:
                    if (unicastInputEnabled) {
                        if (unicastInput == null) {
                            log.warning("null unicastInput, going to WAITING state");
                            setPlayMode(PlayMode.WAITING);
                        } else {
                            return unicastInput.readPacket();  // TODO should throw interruptedexception
                        }
                    }
                    if (blockingQueueInputEnabled) {
                        if (getBlockingQueueInput() == null) {
                            log.warning("null blockingQueueInput, going to WAITING state");
                            setPlayMode(PlayMode.WAITING);
                        } else {
                            Collection<AEPacketRaw> tempPackets = new ArrayList<AEPacketRaw>();
                            getBlockingQueueInput().drainTo(tempPackets);
                            int numOfCochleaPackets = 0;  // TODO make more general mechanism of merging streams
                            int numOfRetinaPackets = 0;
                            for (AEPacketRaw packet : tempPackets) {
                                if (packet.getNumEvents() != 0) {
                                    if ((packet.addresses[0] & 0x8000) == 0) {
                                        numOfCochleaPackets++;
                                    } else {
                                        numOfRetinaPackets++;
                                    }
                                }
                            }
                            return new AEPacketRaw(tempPackets);
                        }
                    }
                    break;
                case WAITING:
                    if (unicastInputEnabled) {
                        // if were were playing back a recording and a remote interface is active, then we go back to it here.
                        setPlayMode(PlayMode.REMOTE);
                        return emptyRawPacket;
                    }
                    if (suppressHardwareOpen || hardwareSwitchInProgress) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            log.fine("WAITING suppressHardwareOpen sleep interrupted");
                        }
                        return emptyRawPacket;
                    }
                    String sessionWait = SessionCameraOpenCoordinator.waitReason(AEViewer.this);
                    if (sessionWait != null) {
                        statisticsLabel.setText(sessionWait);
                        SessionCameraOpenCoordinator.pollWatchdog();
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            log.fine("WAITING session USB gate interrupted");
                        }
                        return emptyRawPacket;
                    }
                    if (!nullInterface) {
                        openAEMonitor();
                    }

                    if ((aemon == null) || !aemon.isOpen()) {
                        final int nDev = HardwareInterfaceFactory.instance().getCachedNumInterfacesAvailable();
                        if (nDev <= 0) {
                            statisticsLabel.setText("WAITING; File/Open (^o) to play a recording");
                        } else if (nDev > 1 || getOpenViewerCount() > 1) {
                            statisticsLabel.setText("WAITING: " + nDev
                                    + " devices found; use Interface menu to choose one");
                        } else {
                            statisticsLabel.setText("WAITING: 1 device found");
                        }

                        try {
                            long sleepMs = 600;
                            if (!LibUsbHotplug.isSupported()) {
                                long now = System.currentTimeMillis();
                                windowsUsbPoll.logPhaseIfChanged(now, log);
                                sleepMs = windowsUsbPoll.waitingSleepMs(now);
                            }
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException e) {
                            log.info("WAITING interrupted");
                        }
                        return emptyRawPacket;
                    }
                case FILTER_INPUT:
                    // input is coming from some EventFilter
                    fpsDelay();
                    return emptyRawPacket; // no error, but return true so that we don't assume that raw packet was captured
            } // playMode switch

            return emptyRawPacket;
        }

        /**
         * Filters packet through processing chain if ProcessingMode is
         * RENDERING or LIVE. If any filter throws an exception, all filters are
         * disabled.
         *
         * @return true if packet is null, otherwise false.
         */
        private EventPacket filterPacket(EventPacket inputPacket) {

            if (playerControls.isSliderBeingAdjusted() || getAePlayer().getPlaybackDirection() == AbstractAEPlayer.PlaybackDirection.Backward) {
                return inputPacket; // don't run filters if user is manipulating position or playing backwards
            }
            FilterChain chain = filterChain;
            if (chain == null && chip != null) {
                chain = chip.getFilterChain();
                filterChain = chain;
            }
            if (chain == null) {
                return inputPacket;
            }
            // filter events, do processing on them in rendering loop here
            if ((chain.getProcessingMode() == FilterChain.ProcessingMode.RENDERING) || (getPlayMode() != PlayMode.LIVE)) {
                try {
                    EventPacket p = chain.filterPacket(inputPacket);
                    return p;
                } catch (Exception e) {
                    log.warning("Caught " + e + ", disabling all filters. See following stack trace.");
                    log.log(Level.SEVERE, e.toString(), e);

                    log.log(Level.WARNING, "Filter exception", e);
                    for (EventFilter f : chain) {
                        f.setFilterEnabled(false);
                    }
                }
            }
            return inputPacket; // if we don't filter, just return the input packet to render the raw data
        }

        void recordPacket(AEPacketRaw rawPacket, EventPacket cookedPacket) {
            recordPacket(rawPacket, cookedPacket, chip.getLastBundle());
        }

        void recordPacket(AEPacketRaw rawPacket, EventPacket cookedPacket, PacketBundle cookedBundle) {
            Object streamLock = aedat4RecordingOutputStream != null ? aedat4RecordingOutputStream
                    : (aedzRecordingOutputStream != null ? aedzRecordingOutputStream : recordingOutputStream);
            synchronized (streamLock) {
                try {
                    if (aedat4RecordingOutputStream != null) {
                        PacketBundle bundle = cookedBundle != null ? cookedBundle : chip.getLastBundle();
                        // AEDAT-4 logs the cooked PacketBundle. Omit events STCF etc. marked
                        // filteredOut so the file matches the display (iterator semantics).
                        // File → "Enable filtering of recorded events" still applies to AEDAT-2.
                        boolean skipFilteredOut = isRecordFilteredEventsEnabled()
                                || (chip.getFilterChain() != null && chip.getFilterChain().isAnyFilterEnabled());
                        aedat4RecordingOutputStream.writeBundle(bundle, skipFilteredOut);
                    } else if (aedzRecordingOutputStream != null) {
                        aedzRecordingOutputStream.writePacket(isRecordFilteredEventsEnabled()
                                ? extractor.reconstructRawPacket(cookedPacket)
                                : rawPacket);
                    } else if (!isRecordFilteredEventsEnabled()) {
                        recordingOutputStream.writePacket(rawPacket); // record all events
                    } else {
                        // log the reconstructed packet after filtering
                        AEPacketRaw aeRawRecon = extractor.reconstructRawPacket(cookedPacket);
                        recordingOutputStream.writePacket(aeRawRecon);
                    }
                } catch (IOException e) {
                    log.log(Level.SEVERE, e.toString(), e);

                    setRecordingEnabled(false);
                    try {
                        if (aedat4RecordingOutputStream != null) {
                            aedat4RecordingOutputStream.close();
                        } else if (aedzRecordingOutputStream != null) {
                            aedzRecordingOutputStream.close();
                        } else {
                            recordingOutputStream.close();
                        }
                    } catch (IOException e2) {
                        log.log(Level.SEVERE, "Exception closing file: " + e2.toString(), e2);

                    }
                }
            }
            if (recordingTimeLimit > 0) { // we may have a defined time for recording, if so, check here and abort recording
                stopRecordingIfTimeLimitReached();
            }
        }

        /**
         * Write data to output streams Returns true if the run loop should
         * break.
         *
         * @return true to break out of loop, e.g. there is error, false is OK
         */
        private boolean writeOutputStreams(AEPacketRaw rawPacket, EventPacket cookedPacket) {
            if (blockingQueueOutputEnabled && (blockingQueueOutput != null) && (rawPacket != null)) {
                AEPacketRaw toSend = rawPacket;
                if (isRecordFilteredEventsEnabled()) {
                    toSend = extractor.reconstructRawPacket(cookedPacket);
                }
                offerBlockingQueuePacket(toSend);
            }

            if (unicastOutputEnabled && (unicastOutput != null)) {
                try {
                    if (!isRecordFilteredEventsEnabled()) {
                        unicastOutput.writePacket(rawPacket);
                    } else {
                        // log the reconstructed packet after filtering.
                        // TODO handle reconstructed packet with filtering that transforms events. At present the original raw addresses are sent out, so e.g. rotation will not appear
                        // in the output.
                        AEPacketRaw aeRawRecon = extractor.reconstructRawPacket(cookedPacket);
                        unicastOutput.writePacket(aeRawRecon);
                    }
                } catch (IOException e) {
                    log.log(Level.SEVERE, e.toString(), e);

                }
            }

            return false;

        }

        /** Idle wait while paused (no acquisition). */
        void pauseIdleWaitIfNeeded() {
            if (viewLoopSuspendedForOfflineExport) {
                // Park until Save As finishes. Do not use interruptViewloop(): it
                // closes FileChannel. Do not honor interrupted() by skipping the
                // wait — that busy-spins paintFrame() and keeps sharing the stream.
                interrupted(); // clear stale interrupt from contrast/zoom/etc.
                synchronized (viewLoopPauseLock) {
                    if (!viewLoopParkedForOfflineExport) {
                        log.info("ViewLoop parked for File → Save As (no grabInput/paint until export finishes)");
                    }
                    viewLoopParkedForOfflineExport = true;
                    viewLoopPauseLock.notifyAll();
                    try {
                        while (viewLoopSuspendedForOfflineExport && !stop) {
                            try {
                                viewLoopPauseLock.wait();
                            } catch (java.lang.InterruptedException e) {
                                interrupted();
                            }
                        }
                    } finally {
                        viewLoopParkedForOfflineExport = false;
                    }
                }
                return;
            }
            if (isPaused() && !interrupted()) {
                synchronized (viewLoopPauseLock) {
                    try {
                        viewLoopPauseLock.wait(1000);
                    } catch (java.lang.InterruptedException e) {
//						log.log(Level.INFO, "viewLoop idle wait() was interrupted: {0}", e.toString());
                    }
                }
            }
        }

        /**
         * Pace the loop to the desired FPS after work time was measured by
         * {@link FrameRater#takeAfter()}. Called at the end of each iteration so
         * sleep uses the current frame's work time (not the previous frame's).
         */
        void paceViewLoopFrame() {
            if (!isPaused()) {
                getFrameRater().delayForDesiredFPS();
            }
        }

        /** Used by FILTER_INPUT grab path; combines idle wait and pacing. */
        void fpsDelay() {
            pauseIdleWaitIfNeeded();
            paceViewLoopFrame();
        }
        private int lastPacketLastTs = 0; // last timestamp of previous packet, used by getDtMs
        private float lastDtMs = 0;

        // returns delta time in ms of the current packet, or 0 if there is less than two events
        private float getDtMs(EventPacket packet) {
            if ((packet == null) || ((numEvents = packet.getSize()) < 2)) {
                return 0;
            }

            float dtMs = (float) ((packet.getLastTimestamp() - packet.getFirstTimestamp()) / (tickUs * 1e3));
            return dtMs;
        }

        //        private int lastPacketLastTs = 0; // last timestamp of previous packet, used by getDtMs
        //        private float lastDtMs=0;
        //
        //        // returns delta time in ms from last time this method was called, uses lastts
        //        private float getDtMs (EventPacket packet){
        //            int numEvents=0;
        //            if(packet==null || (numEvents=packet.getSize())==0 || packet.getLastTimestamp()==lastPacketLastTs) return lastDtMs;
        //
        //                int t=packet.getLastTimestamp();
        //                float dtMs = (float)( ( t-lastPacketLastTs ) / ( tickUs * 1e3 ) );
        //                lastPacketLastTs = t; // save last timestamp of this packet
        //                lastDtMs=dtMs;
        //                return dtMs;
        //        }
        private float getTimeExpansion(float dtMs) {
            lastTimeExpansionFactor = (getFrameRater().getAverageFPS() * dtMs) / 1000f;
            return lastTimeExpansionFactor;
        }
        //        private String statLabel = null;
        private StringBuilder sb = new StringBuilder(160);
        private float thisTime = Float.NaN;
        /** Do not rebuild the status line faster than this; String.format + setText every packet was the JFR allocation hotspot. */
        private static final long STATISTICS_LABEL_MIN_INTERVAL_MS = 200;
        private long lastStatisticsLabelMs = 0;
        /** Empty live polls before status shows "waiting for events" (not one empty swap). */
        private static final int LIVE_WAITING_LABEL_EMPTY_PACKET_THRESHOLD = 50;
        private int consecutiveEmptyLivePackets = 0;

        private void makeStatisticsLabel(EventPacket packet) {
            if (getAePlayer().isChoosingFile()) {
                return;
            }
            if (packet == null) {
                return;
            }
            if (packet.getSize() == 0) {
                if ((getPlayMode() == PlayMode.LIVE || getPlayMode() == PlayMode.SEQUENCING)
                        && aemon != null && aemon.isOpen()) {
                    consecutiveEmptyLivePackets++;
                    if (consecutiveEmptyLivePackets >= LIVE_WAITING_LABEL_EMPTY_PACKET_THRESHOLD) {
                        setStatisticsLabel("Live: " + aemon + " — waiting for events");
                    }
                }
                return;
            }
            consecutiveEmptyLivePackets = 0;
            appendStatisticsLabelForPacket(packet);
        }

        private static final char[] STATS_PAD = "                    ".toCharArray();

        private static void padLeft(StringBuilder buf, int fieldStart, int width) {
            int need = width - (buf.length() - fieldStart);
            if (need > 0) {
                buf.insert(fieldStart, STATS_PAD, 0, Math.min(need, STATS_PAD.length));
            }
        }

        private static void padRight(StringBuilder buf, int fieldStart, int width) {
            int need = width - (buf.length() - fieldStart);
            while (need-- > 0) {
                buf.append(' ');
            }
        }

        /** {@code %.3f} without {@link String#format}. */
        private static void appendFixed3(StringBuilder buf, float v) {
            if (Float.isNaN(v)) {
                buf.append("NaN");
                return;
            }
            if (v < 0) {
                buf.append('-');
                v = -v;
            }
            int ip = (int) v;
            int frac = Math.round((v - ip) * 1000f);
            if (frac >= 1000) {
                ip++;
                frac = 0;
            }
            buf.append(ip).append('.');
            if (frac < 100) {
                buf.append('0');
            }
            if (frac < 10) {
                buf.append('0');
            }
            buf.append(frac);
        }

        /** {@code %.1f} without {@link String#format}. */
        private static void appendFixed1(StringBuilder buf, float v) {
            if (v < 0) {
                buf.append('-');
                v = -v;
            }
            int ip = (int) v;
            int frac = Math.round((v - ip) * 10f);
            if (frac >= 10) {
                ip++;
                frac = 0;
            }
            buf.append(ip).append('.').append(frac);
        }

        private void appendStatisticsLabelForPacket(EventPacket packet) {
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastStatisticsLabelMs < STATISTICS_LABEL_MIN_INTERVAL_MS) {
                    return;
                }
                lastStatisticsLabelMs = nowMs;
                float dtMs = getDtMs(packet);

                switch (getPlayMode()) {
                    case SEQUENCING:
                    case LIVE:
                        if (aemon == null) {
                            return;
                        }
                        if (packet.getSize() > 0) { // only update time if there is an event in the packet
                            thisTime = packet.getLastTimestamp() * aemon.getTimestampTickUs() * 1e-6f;
                        }
                        break;
                    case PLAYBACK:
                    case FILTER_INPUT:
                    case REMOTE:
                        thisTime = packet.getLastTimestamp() * 1e-6f; // just use the raw timestamp from the data file, but this will not account for wrapping.
                        break;
                }

                sb.setLength(0);

                int field = sb.length();
                engFmt.append(sb, dtMs / 1000.0);
                padLeft(sb, field, 10);
                sb.append('s').append('@');

                field = sb.length();
                appendFixed3(sb, thisTime);
                padLeft(sb, field, 5);
                sb.append("s ");

                if (chip.getFilterChain().isAnyFilterEnabled()) {
                    field = sb.length();
                    sb.append(numEvents);
                    padLeft(sb, field, 5);
                    sb.append('/');
                    field = sb.length();
                    sb.append(numFilteredEvents);
                    padRight(sb, field, 5);
                    if (filterChain.isTimedOut()) {
                        sb.append(" TO  ");
                    } else {
                        sb.append("evts");
                    }
                } else {
                    field = sb.length();
                    sb.append(numEvents);
                    padLeft(sb, field, 5);
                    sb.append("evts ");
                }

                sb.append(droppedDataInfo.getStatsLineToken());

                field = sb.length();
                engFmt.append(sb, packet.getEventRateHz());
                padLeft(sb, field, 9);
                sb.append("eps ");

                if (isPaused()) {
                    sb.append("Paused ");
                } else if ((getPlayMode() == PlayMode.LIVE) || (getPlayMode() == PlayMode.SEQUENCING)) {
                    sb.append("Live/Seq ");
                } else {
                    float expansion = getTimeExpansion(dtMs);
                    if (expansion == 0) {
                        sb.append("??? ");
                    } else {
                        field = sb.length();
                        engFmt.append(sb, expansion);
                        padLeft(sb, field, 7);
                        sb.append('X');
                    }
                }

                FrameRater fr = getFrameRater();
                field = sb.length();
                sb.append(Math.round(fr.getAverageFPS()));
                padLeft(sb, field, 3);
                sb.append('/').append(fr.getDesiredFPS()).append("fps,");
                field = sb.length();
                sb.append(fr.getLastDelayMs());
                padLeft(sb, field, 2);
                sb.append("ms");

                AEChipRenderer renderer = getRenderer();
                if (renderer.isAdaptiveRenderSkippingEnabled()) {
                    sb.append(" ARS lvl=").append(renderer.getSkipFrameRenderingNumberCurrent())
                            .append('/').append(renderer.getSkipFrameRenderingNumberMax())
                            .append(" sk=").append(renderer.getSkipPacketsRenderingCount())
                            .append(" ld=");
                    appendFixed1(sb, fr.getLastLoopLoad());
                } else {
                    sb.append(" ARS off");
                }

                sb.append(renderer.isAutoscaleEnabled() ? " AS=" : " FS=").append(renderer.getColorScale());

                setStatisticsLabel(sb.toString());
                if (droppedDataInfo.any()) {
                    statisticsLabel.setForeground(Color.RED);
                    if (!droppedDataInfo.getDetail().isEmpty()) {
                        statisticsLabel.setToolTipText(droppedDataInfo.getDetail());
                    } else {
                        statisticsLabel.setToolTipText(null);
                    }
                } else {
                    statisticsLabel.setForeground(Color.BLACK);
                    statisticsLabel.setToolTipText(null);
                }
        }
    }

    private javax.swing.Timer statusTimer = null;

    /**
     * Shows the action text momentarily centered in middle of display, for
     * DisplayMethod that implement it.
     *
     * @param text
     */
    public void showActionText(String s) {
        if (chip.getCanvas().getDisplayMethod() != null) {
            chip.getCanvas().getDisplayMethod().showActionText(s);
        }
    }

    private final EvictingQueue<String> statusTextFieldMessages = EvictingQueue.create(4);

    /**
     * Sets the viewer's status message at the bottom of the window.
     *
     * @param s the string
     * @see #setStatusMessage(String)
     */
    public void setStatusMessage(final String s) {
        if (s == null) {
            return;
        }
        statusTextFieldMessages.add(s);
        SwingUtilities.invokeLater(new Runnable() { //invoke in Swing thread to avoid Errors thrown by getLock when the viewloop (which is calling setStatusMessage) is interrupted by playMode change

            @Override
            public void run() {
                statusTextField.setText(s);
                if (statusTimer != null) {
                    statusTimer.stop();
                }
                statusTimer = new javax.swing.Timer(2000, new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        setStatusColor(Color.gray);
                    }
                });
                statusTimer.setRepeats(false);
                statusTimer.setCoalesce(true);
                statusTimer.start();
            }
        });
    }
    private float lastTimeExpansionFactor = 1;

    /**
     * Returns the most recent time dilation/contraction factor for display.
     *
     * @return the time expansion factor. 1 means real time, >1 means faster
     * than real time.
     */
    public float getTimeExpansion() {
        return lastTimeExpansionFactor;
    }

    /**
     * Sets the color of the status field text - e.g. to highlight it.
     *
     * @param c
     */
    public void setStatusColor(final Color c) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                statusTextField.setForeground(c);
            }
        });
    }

    @Override
    public void exceptionOccurred(Exception x, Object source) {
        if (x.getMessage() != null) {
            setStatusMessage(x.getMessage());
            startStatusClearer(Color.RED);
        } else {
            if ((statusClearerThread != null) && statusClearerThread.isAlive()) {
                return;
            }
            setStatusMessage(null);
            Color c = Color.GREEN;
            Color c2 = c.darker();
            startStatusClearer(c2);
        }
    }

    private void startStatusClearer(Color color) {
        setStatusColor(color);
        if ((statusClearerThread != null) && statusClearerThread.isAlive()) {
            statusClearerThread.renew();
        } else {
            statusClearerThread = new StatusClearerThread();
            statusClearerThread.start();
        }

    }
    StatusClearerThread statusClearerThread = null;
    /**
     * length of exception highlighting in status bar in ms
     */
    public final long STATUS_DURATION = 1000;

    class StatusClearerThread extends Thread {

        long endTime;

        public StatusClearerThread() {
            super("AEViewerStatusClearerThread");
        }

        public void renew() {
            //            System.out.println("renewing status change");
            endTime = System.currentTimeMillis() + STATUS_DURATION;
        }

        @Override
        public void run() {
            //            System.out.println("start status clearer thread");
            endTime = System.currentTimeMillis() + STATUS_DURATION;
            try {
                while (System.currentTimeMillis() < endTime) {
                    Thread.sleep(STATUS_DURATION);
                }
                setStatusColor(Color.DARK_GRAY);
            } catch (InterruptedException e) {
            }

        }
    }

    /** Latest status-line text from ViewLoop; EDT reads this in a coalesced invokeLater. */
    private volatile String pendingStatisticsLabelText;
    private final AtomicBoolean statisticsLabelUpdateScheduled = new AtomicBoolean(false);

    void setStatisticsLabel(final String s) {
        pendingStatisticsLabelText = s;
        if (!statisticsLabelUpdateScheduled.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                statisticsLabelUpdateScheduled.set(false);
                String text = pendingStatisticsLabelText;
                if (text != null && !text.equals(statisticsLabel.getText())) {
                    statisticsLabel.setText(text);
                }
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
        //        }
    }

    int getScreenRefreshRate() {
        int rate = 60;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (ge == null) {
            return rate;
        }
        GraphicsDevice[] gs = ge.getScreenDevices();
        for (int i = 0; i < gs.length; i++) {
            DisplayMode dm = gs[i].getDisplayMode();
            // Get refresh rate in Hz
            if (dm == null) {
                return rate;
            }
            int refreshRate = dm.getRefreshRate();
            if (refreshRate == DisplayMode.REFRESH_RATE_UNKNOWN) {
                log.warning("AEViewer.getScreenRefreshRate: got unknown refresh rate for screen " + i + ", assuming 60");
                refreshRate = 60;
            } else {
                //                log.info("AEViewer.getScreenRefreshRate: screen "+i+" has refresh rate "+refreshRate);
            }
            if (i == 0) {
                rate = refreshRate;
            }
        }
        return rate;
    }// computes and executes appropriate delayForDesiredFPS to try to maintain constant rendering rate

    /**
     * Measure actual rendering frame rate and creates appropriate frame delay.
     *
     */
    public class FrameRater {

        final int MAX_FPS = 1000;
        int desiredFPS = prefs.getInt("AEViewer.FrameRater.desiredFPS", getScreenRefreshRate());
//        public final int N_SAMPLES = 10;
//        long[] samplesNs = new long[N_SAMPLES];
        int index = 0;
        int delayMs = 1;
        int desiredPeriodMs = (int) (1000f / desiredFPS);
        private long beforeTimeNs = System.nanoTime(), lastdt, afterTimeNs;
        private LowpassFilter periodFilter = new LowpassFilter(FPS_LOWPASS_FILTER_TIMECONSTANT_MS);

        /**
         * Sets the desired target frames rate in frames/sec
         *
         * @param fps frames/sec desired. Shows warning if rate is too high or
         * too low, so that users do not inadvertently set a rate that may be
         * unintended.
         *
         */
        public final void setDesiredFPS(int fps) {
            if ((fps < 30) || (fps > 120)) {
                WarningDialogWithDontShowPreference fpsWarning;

                fpsWarning = new WarningDialogWithDontShowPreference(AEViewer.this, false,
                        "jAER Rendering rate warning",
                        "<html>You are setting rendering (and processing rate) at " + fps + " Hz. <br>which is either less than 30Hz or greater than 120Hz. "
                        + "<p>To change the rendering rate, see menu item use the LEFT or RIGHT arrow keys. "
                        + "<p>The current actual/desired rendering rate is shown in the status bar as XX/YYfps"
                        + "<p>You can render at a higher rate to reduce latency, but computational cost will be higher. "
                        + "<p>For real-time applications, see the <i>Options/Process on acquistion cycle</i> menu item in the FilterFrame window."
                );
                fpsWarning.setLocationRelativeTo(AEViewer.this);
                fpsWarning.setVisible(true);
            }
            if (fps < 1) {
                fps = 1;
            } else if (fps > MAX_FPS) {
                fps = MAX_FPS;
            }
            desiredFPS = fps;
            prefs.putInt("AEViewer.FrameRater.desiredFPS", fps);
            desiredPeriodMs = 1000 / fps;
        }

        public final int getDesiredFPS() {
            return desiredFPS;
        }

        /**
         * Returns average over last N_SAMPLES frames of the frame period in ns
         *
         * @return ns average period
         */
        public final float getAveragePeriodNs() {
            return periodFilter.getValue();
//            long sum = 0;
//            for (int i = 0; i < N_SAMPLES; i++) {
//                sum += samplesNs[i];
//            }
//            return (float) sum / N_SAMPLES;
        }

        /**
         * Returns average actual frame rate over last N_SAMPLES frames
         *
         * @return box-averaged frame rate in frames/sec
         */
        public final float getAverageFPS() {
            return 1f / (getAveragePeriodNs() / 1e9f);
        }

        final float getLastFPS() {
            return 1f / (lastdt / 1e9f);
        }

        /**
         * Returns last loop delay in ms
         *
         * @return last frame delay in ms
         */
        public final int getLastDelayMs() {
            return delayMs;
        }

        /**
         * Returns time since last frame in ns
         *
         * @return time in ns
         */
        final long getLastDtNs() {
            return lastdt;
        }

        /**
         * Ratio of last loop time to desired frame period; values &gt; 1 mean
         * the pipeline is falling behind the target frame rate.
         */
        public final float getLastLoopLoad() {
            if (desiredPeriodMs <= 0) {
                return 0f;
            }
            return (lastdt / 1e6f) / desiredPeriodMs;
        }

        public final boolean isPeriodFilterInitialized() {
            return periodFilter.isInitialized();
        }

        //  call this ONCE after capture/render. it will store the time since the last call
        void takeBefore() {
            beforeTimeNs = System.nanoTime();
        }
        private long lastAfterTime = System.nanoTime();

        //  call this ONCE after capture/render. it will store the time since the last call
        final void takeAfter() {
            afterTimeNs = System.nanoTime();
            lastdt = afterTimeNs - beforeTimeNs;
            periodFilter.filter((int) (afterTimeNs - lastAfterTime), (int) (afterTimeNs / 1000));
//            samplesNs[index++] = afterTimeNs - lastAfterTime;
            lastAfterTime = afterTimeNs;
//            if (index >= N_SAMPLES) {
//                index = 0;
//            }
        }

        /**
         * call this to delayForDesiredFPS enough to make the total time
         * including last sample period equal to desiredPeriodMs
         *
         */
        final void delayForDesiredFPS() {
            if (Thread.interrupted()) {
                return; // clear the interrupt flag here to make sure we don't just pass through with no one clearing the flag
            }

            if (viewLoop.fastForward) {
                viewLoop.fastForward = false;
                takeAfter(); // count this packet for rendering speed measuurement
                if (playMode != PlayMode.LIVE) {
                    return;
                }
            }

            delayMs = Math.round(desiredPeriodMs - ((float) lastdt / 1000000));
            if (delayMs < 0) {
                delayMs = 0;
            }
            try {
                Thread.sleep(delayMs);
            } catch (java.lang.InterruptedException e) {
            }
        }
    }

    /**
     * Fires a property change {@link #EVENT_STOPME}, and then stops playback or
     * closes device
     */
    public void stopMe() {
        stopRecording(true); // in case recording, make sure we give chance to save file
        getSupport().firePropertyChange(EVENT_STOPME, null, null);
        //        log.info(Thread.currentThread()+ "AEViewer.stopMe() called");
        switch (getPlayMode()) {
            case PLAYBACK:
                getAePlayer().stopPlayback(); // TODO can lead to deadlock if stopMe is called from a thread that
                break;
            case LIVE:
            case WAITING:
                viewLoop.stopThread();
                showBiasgen(false);
                break;
            case REMOTE:
                if (unicastInputEnabled) {
                    closeUnicastInput();
                }
                if (blockingQueueInputEnabled) {
                    blockingQueueInput = null;
                    blockingQueueInputEnabled = false;
                }
        }
        // viewer is removed by WindowClosing event
        //        if(caviarViewer!=null ){
        //            log.info(this+" being removed from caviarViewer viewers list");
        //            caviarViewer.getViewers().remove(this);
        //        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jProgressBar1 = new javax.swing.JProgressBar();
        renderModeButtonGroup = new javax.swing.ButtonGroup();
        monSeqOpModeButtonGroup = new javax.swing.ButtonGroup();
        jMenuItem2 = new javax.swing.JMenuItem();
        jSeparator20 = new javax.swing.JSeparator();
        jSeparator21 = new javax.swing.JSeparator();
        jSeparator22 = new javax.swing.JSeparator();
        statisticsPanel = new javax.swing.JPanel();
        imagePanel = new javax.swing.JPanel();
        bottomPanel = new javax.swing.JPanel();
        buttonsPanel = new javax.swing.JPanel();
        biasesToggleButton = new javax.swing.JToggleButton();
        filtersToggleButton = new javax.swing.JToggleButton();
        recordingButton = new javax.swing.JToggleButton();
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
        showFileInfoMenuItem = new javax.swing.JMenuItem();
        closeMenuItem = new javax.swing.JMenuItem();
        saveAsMenuItem = new javax.swing.JMenuItem();
        exportVideoMenuItem = new javax.swing.JMenuItem();
        stopVideoExportMenuItem = new javax.swing.JMenuItem();
        jSeparator8 = new javax.swing.JSeparator();
        recordingMenuItem = new javax.swing.JMenuItem();
        recordingPlaybackImmediatelyCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        recordingSetTimelimitMenuItem = new javax.swing.JMenuItem();
        recordFilteredEventsCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        networkSeparator = new javax.swing.JSeparator();
        remoteMenu = new javax.swing.JMenu();
        unicastOutputEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        openUnicastInputMenuItem = new javax.swing.JMenuItem();
        jSeparator17 = new javax.swing.JPopupMenu.Separator();
        openBlockingQueueInputMenuItem = new javax.swing.JCheckBoxMenuItem();
        blockingQueueOutputEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        syncSeperator = new javax.swing.JSeparator();
        syncEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        timestampResetBitmaskMenuItem = new javax.swing.JMenuItem();
        jSeparator16 = new javax.swing.JSeparator();
        exitSeperator = new javax.swing.JSeparator();
        preferencesMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        viewFiltersMenuItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        increaseContrastMenuItem = new javax.swing.JMenuItem();
        decreaseContrastMenuItem = new javax.swing.JMenuItem();
        cycleNextColorRenderingMethodMenuItem = new javax.swing.JMenuItem();
        cyclePreviousColorRenderingMethodMenuItem = new javax.swing.JMenuItem();
        showRenderingModeMI = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JSeparator();
        cycleDisplayMethodButton = new javax.swing.JMenuItem();
        displayMethodMenu = new javax.swing.JMenu();
        jSeparator12 = new javax.swing.JSeparator();
        acccumulateImageEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        resetAccumulationMenuItem = new javax.swing.JMenuItem();
        fadingMI = new javax.swing.JCheckBoxMenuItem();
        slidingMI = new javax.swing.JCheckBoxMenuItem();
        viewIgnorePolarityCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator18 = new javax.swing.JPopupMenu.Separator();
        zoomInMenuItem = new javax.swing.JMenuItem();
        zoomOutMenuItem = new javax.swing.JMenuItem();
        unzoomMenuItem = new javax.swing.JMenuItem();
        graphicsSubMenu = new javax.swing.JMenu();
        viewActiveRenderingEnabledMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewRenderBlankFramesCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        setFrameRateMenuItem = new javax.swing.JMenuItem();
        skipPacketsRenderingCheckBoxMenuItem = new ScrollWheelTunableCheckBoxMenuItem();
        setBorderSpaceMenuItem = new javax.swing.JMenuItem();
        enableFiltersOnStartupCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        playbackMenu = new javax.swing.JMenu();
        pauseRenderingCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        viewStepForwardsMI = new javax.swing.JMenuItem();
        viewStepBackwardsMI = new javax.swing.JMenuItem();
        jSeparator28 = new javax.swing.JPopupMenu.Separator();
        increasePlaybackSpeedMenuItem = new javax.swing.JMenuItem();
        decreasePlaybackSpeedMenuItem = new javax.swing.JMenuItem();
        flextimePlaybackEnabledCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator11 = new javax.swing.JPopupMenu.Separator();
        increaseFrameRateMenuItem = new javax.swing.JMenuItem();
        decreaseFrameRateMenuItem = new javax.swing.JMenuItem();
        jSeparator23 = new javax.swing.JPopupMenu.Separator();
        rewindPlaybackMenuItem = new javax.swing.JMenuItem();
        togglePlaybackDirectionMenuItem = new javax.swing.JMenuItem();
        jSeparator10 = new javax.swing.JPopupMenu.Separator();
        jogForwardMI = new javax.swing.JMenuItem();
        jogBackwardsMI = new javax.swing.JMenuItem();
        cancelJogMI = new javax.swing.JMenuItem();
        setJogNCount = new javax.swing.JMenuItem();
        jSeparator19 = new javax.swing.JPopupMenu.Separator();
        setMarkInMI = new javax.swing.JMenuItem();
        setMarkOutMI = new javax.swing.JMenuItem();
        toggleMarkerMI = new javax.swing.JMenuItem();
        jumpPrevMarkerMI = new javax.swing.JMenuItem();
        jumpNextMarkerMI = new javax.swing.JMenuItem();
        clearMarksMI = new javax.swing.JMenuItem();
        exportMarksMI = new javax.swing.JMenuItem();
        importMarksMI = new javax.swing.JMenuItem();
        deviceMenu = new javax.swing.JMenu();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        renewChipMI = new javax.swing.JMenuItem();
        deviceMenuSpparator = new javax.swing.JSeparator();
        customizeDevicesMenuItem = new javax.swing.JMenuItem();
        interfaceMenu = new javax.swing.JMenu();
        refreshInterfaceMenuItem = new javax.swing.JMenuItem();
        controlMenu = new javax.swing.JMenu();
        viewBiasesMenuItem = new javax.swing.JMenuItem();
        jSeparator26 = new javax.swing.JPopupMenu.Separator();
        usbTuningMenuItem = new javax.swing.JMenuItem();
        jSeparator5 = new javax.swing.JSeparator();
        printUSBStatisticsCBMI = new javax.swing.JCheckBoxMenuItem();
        jSeparator24 = new javax.swing.JPopupMenu.Separator();
        zeroTimestampsMenuItem = new javax.swing.JMenuItem();
        resetUsbInterfaceMenuItem = new javax.swing.JMenuItem();
        monSeqMenu = new javax.swing.JMenu();
        sequenceMenuItem = new javax.swing.JMenuItem();
        enableMissedEventsCheckBox = new javax.swing.JCheckBoxMenuItem();
        monSeqMissedEventsMenuItem = new javax.swing.JMenuItem();
        jSeparator13 = new javax.swing.JSeparator();
        monSeqOperationModeMenu = new javax.swing.JMenu();
        monSeqOpMode0 = new javax.swing.JRadioButtonMenuItem();
        monSeqOpMode1 = new javax.swing.JRadioButtonMenuItem();
        helpMenu = new javax.swing.JMenu();
        jSeparator7 = new javax.swing.JSeparator();
        releaseNotesMenuItem = new javax.swing.JMenuItem();
        checkForUpdatesMenuItem = new javax.swing.JMenuItem();
        gitUpdateMenuItem = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JPopupMenu.Separator();
        loggingLevelMenu = new javax.swing.JMenu();
        jSeparator25 = new javax.swing.JPopupMenu.Separator();
        aboutMenuItem = new javax.swing.JMenuItem();

        jMenuItem2.setText("jMenuItem2");

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("AEViewer");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        statisticsPanel.setFocusable(false);
        statisticsPanel.setLayout(new javax.swing.BoxLayout(statisticsPanel, javax.swing.BoxLayout.LINE_AXIS));
        getContentPane().add(statisticsPanel, java.awt.BorderLayout.NORTH);

        imagePanel.setEnabled(false);
        imagePanel.setFocusable(false);
        imagePanel.setPreferredSize(new java.awt.Dimension(200, 200));
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

        biasesToggleButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        biasesToggleButton.setText("HW Configuration");
        biasesToggleButton.setToolTipText("Shows or hides the hardware configuration (e.g. bias generator, scanner, ADC, etc) control panel");
        biasesToggleButton.setAlignmentY(0.0F);
        biasesToggleButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        biasesToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                biasesToggleButtonActionPerformed(evt);
            }
        });
        buttonsPanel.add(biasesToggleButton);

        filtersToggleButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
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

        recordingButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        recordingButton.setMnemonic('l');
        recordingButton.setText("Start recording");
        recordingButton.setToolTipText("Starts or stops recording or re-recording");
        recordingButton.setAlignmentY(0.0F);
        recordingButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonsPanel.add(recordingButton);

        playerControlPanel.setToolTipText("");
        playerControlPanel.setAlignmentY(0.0F);
        playerControlPanel.setMaximumSize(new java.awt.Dimension(32000, 32000));
        playerControlPanel.setLayout(new java.awt.BorderLayout());
        buttonsPanel.add(playerControlPanel);

        bottomPanel.add(buttonsPanel, java.awt.BorderLayout.CENTER);

        jPanel1.setLayout(new java.awt.BorderLayout());

        statusTextField.setEditable(false);
        statusTextField.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        statusTextField.setToolTipText("Status messages show here");
        statusTextField.setFocusable(false);
        jPanel1.add(statusTextField, java.awt.BorderLayout.CENTER);

        showConsoleOutputButton.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
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

        newViewerMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        newViewerMenuItem.setMnemonic('N');
        newViewerMenuItem.setText("New viewer");
        newViewerMenuItem.setToolTipText("Opens a new viewer");
        newViewerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newViewerMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(newViewerMenuItem);

        openMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openMenuItem.setMnemonic('o');
        openMenuItem.setText("Open recorded data file...");
        openMenuItem.setToolTipText("Opens a recorded data file for playback");
        openMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openMenuItem);

        showFileInfoMenuItem.setMnemonic('I');
        showFileInfoMenuItem.setText("Show file info...");
        showFileInfoMenuItem.setToolTipText("Show summary information about the recording being played (AEDAT-4)");
        showFileInfoMenuItem.setEnabled(false);
        showFileInfoMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showFileInfoMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(showFileInfoMenuItem);

        closeMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        closeMenuItem.setMnemonic('C');
        closeMenuItem.setText("Close");
        closeMenuItem.setToolTipText("Closes this viewer or the playing data file");
        closeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(closeMenuItem);

        saveAsMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
                java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        saveAsMenuItem.setMnemonic('A');
        saveAsMenuItem.setText("Save As...");
        saveAsMenuItem.setToolTipText("Export the open recording to AEDAT-4, CSV/text, or DSEC HDF5 (playback only)");
        saveAsMenuItem.setEnabled(false);
        saveAsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveAsMenuItem);

        exportVideoMenuItem.setText("Export video...");
        exportVideoMenuItem.setToolTipText("Export rendered AEViewer frames to AVI (optional MP4 via ffmpeg)");
        exportVideoMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportVideoMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exportVideoMenuItem);

        stopVideoExportMenuItem.setText("Stop video export");
        stopVideoExportMenuItem.setToolTipText("Stops an active File/Export video recording");
        stopVideoExportMenuItem.setEnabled(false);
        stopVideoExportMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopVideoExportMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(stopVideoExportMenuItem);
        fileMenu.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                updateStopVideoExportMenuItemEnabled();
                updateSaveAsMenuItemEnabled();
                updateShowFileInfoMenuItemEnabled();
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
        });

        fileMenu.add(jSeparator8);

        recordingMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, 0));
        recordingMenuItem.setText("Start recording data");
        recordingMenuItem.setToolTipText("Starts or stops recording to disk");
        fileMenu.add(recordingMenuItem);

        recordingPlaybackImmediatelyCheckBoxMenuItem.setText("Playback recorded data immediately after recording");
        recordingPlaybackImmediatelyCheckBoxMenuItem.setToolTipText("If enabled, recorded data plays back immediately");
        recordingPlaybackImmediatelyCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recordingPlaybackImmediatelyCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(recordingPlaybackImmediatelyCheckBoxMenuItem);

        recordingSetTimelimitMenuItem.setText("Set recording time limit...");
        recordingSetTimelimitMenuItem.setToolTipText("Sets a time limit for recording from presets or a free-form duration (0 for no limit). Applies immediately to an in-progress recording.");
        recordingSetTimelimitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recordingSetTimelimitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(recordingSetTimelimitMenuItem);

        enableFiltersOnStartupCheckBoxMenuItem.setText("Enable filters on startup");
        enableFiltersOnStartupCheckBoxMenuItem.setToolTipText("Enables creation of event processing filters on startup");
        enableFiltersOnStartupCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableFiltersOnStartupCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(enableFiltersOnStartupCheckBoxMenuItem);

        recordFilteredEventsCheckBoxMenuItem.setText("Enable filtering of recorded or network output events");
        recordFilteredEventsCheckBoxMenuItem.setToolTipText("Recording or network writes apply active filters first (reduces file size or network traffic)");
        recordFilteredEventsCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recordFilteredEventsCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(recordFilteredEventsCheckBoxMenuItem);

        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.setSelected(true);
        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.setText("Check for non-monotonic time in input streams");
        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.setToolTipText("If enabled, nonmonotonic time stamps are checked for in input streams from file or network");
        checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem);
        fileMenu.add(networkSeparator);

        remoteMenu.setMnemonic('r');
        remoteMenu.setText("Remote");

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
        remoteMenu.add(jSeparator17);

        openBlockingQueueInputMenuItem.setMnemonic('b');
        openBlockingQueueInputMenuItem.setText("Enable BlockingQueue input from another viewer");
        openBlockingQueueInputMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openBlockingQueueInputMenuItemActionPerformed(evt);
            }
        });
        remoteMenu.add(openBlockingQueueInputMenuItem);

        blockingQueueOutputEnabledCheckBoxMenuItem.setMnemonic('q');
        blockingQueueOutputEnabledCheckBoxMenuItem.setText("Enable BlockingQueue output to another viewer");
        blockingQueueOutputEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                blockingQueueOutputEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        remoteMenu.add(blockingQueueOutputEnabledCheckBoxMenuItem);

        fileMenu.add(remoteMenu);
        fileMenu.add(syncSeperator);

        syncEnabledCheckBoxMenuItem.setSelected(false);
        syncEnabledCheckBoxMenuItem.setText("Synchronized recording/playback enabled");
        syncEnabledCheckBoxMenuItem.setToolTipText("All viewers start/stop recording in synchrony and playback times are synchronized");
        syncEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                syncEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(syncEnabledCheckBoxMenuItem);

        timestampResetBitmaskMenuItem.setMnemonic('t');
        timestampResetBitmaskMenuItem.setText("dummy, set in constructor");
        timestampResetBitmaskMenuItem.setToolTipText("Setting a bitmask here will memorize and subtract timestamps when address  & bitmask != 0");
        timestampResetBitmaskMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timestampResetBitmaskMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(timestampResetBitmaskMenuItem);
        // RecentFiles inserts: [sep] recent files [sep] folders [sep] before Preferences

        preferencesMenuItem.setMnemonic('p');
        preferencesMenuItem.setText("Preferences...");
        preferencesMenuItem.setToolTipText("Edit AEViewer preferences");
        preferencesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                preferencesMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(preferencesMenuItem);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, 0));
        exitMenuItem.setMnemonic('x');
        exitMenuItem.setText("Exit");
        exitMenuItem.setToolTipText("File → Exit quits jAER immediately (all windows, no confirmation). The x key follows Preferences → Exit completely with 'x'.");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        viewMenu.setMnemonic('v');
        viewMenu.setText("View");
        viewMenu.setToolTipText("Controls view of events, contrast, accumulation, etc.");

        viewFiltersMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        viewFiltersMenuItem.setMnemonic('f');
        viewFiltersMenuItem.setText("Show filters");
        viewFiltersMenuItem.setToolTipText("Shows filter controls");
        viewFiltersMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewFiltersMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(viewFiltersMenuItem);
        viewMenu.add(jSeparator1);

        increaseContrastMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0));
        increaseContrastMenuItem.setText("Increase contrast");
        viewMenu.add(increaseContrastMenuItem);

        decreaseContrastMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0));
        decreaseContrastMenuItem.setText("Decrease contrast");
        viewMenu.add(decreaseContrastMenuItem);

        cycleNextColorRenderingMethodMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, 0));
        cycleNextColorRenderingMethodMenuItem.setText("Next color rendering mode");
        cycleNextColorRenderingMethodMenuItem.setToolTipText("Changes rendering mode (gray, contrast, RG, color-time)");
        cycleNextColorRenderingMethodMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cycleNextColorRenderingMethodMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(cycleNextColorRenderingMethodMenuItem);

        cyclePreviousColorRenderingMethodMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        cyclePreviousColorRenderingMethodMenuItem.setText("Previous color rendering mode");
        cyclePreviousColorRenderingMethodMenuItem.setToolTipText("Changes rendering mode (gray, contrast, RG, color-time)");
        cyclePreviousColorRenderingMethodMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cyclePreviousColorRenderingMethodMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(cyclePreviousColorRenderingMethodMenuItem);

        showRenderingModeMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0));
        showRenderingModeMI.setText("Show rendering mode momentarily");
        showRenderingModeMI.setToolTipText("Momentarily display the current rendering mode details");
        showRenderingModeMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showRenderingModeMIActionPerformed(evt);
            }
        });
        viewMenu.add(showRenderingModeMI);
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
        acccumulateImageEnabledCheckBoxMenuItem.setText("Accumulate events without resetting");
        acccumulateImageEnabledCheckBoxMenuItem.setToolTipText("Rendered data accumulates over 2d hisograms");
        viewMenu.add(acccumulateImageEnabledCheckBoxMenuItem);

        resetAccumulationMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        resetAccumulationMenuItem.setText("Reset accumulation");
        resetAccumulationMenuItem.setToolTipText("Resets the accumulation (and enables accumulation if not enabled)");
        resetAccumulationMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetAccumulationMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(resetAccumulationMenuItem);

        fadingMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        fadingMI.setText("Fading");
        fadingMI.setToolTipText("Controls if previous DVS events fade away according to color scale or frames are just last slice");
        viewMenu.add(fadingMI);

        slidingMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        slidingMI.setText("Sliding window");
        slidingMI.setToolTipText("Controls if previous DVS events fade away according to color scale or frames are just last slice");
        viewMenu.add(slidingMI);

        viewIgnorePolarityCheckBoxMenuItem.setText("Ignore cell type");
        viewIgnorePolarityCheckBoxMenuItem.setToolTipText("Throws away cells type for rendering");
        viewIgnorePolarityCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewIgnorePolarityCheckBoxMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(viewIgnorePolarityCheckBoxMenuItem);
        viewMenu.add(jSeparator18);

        zoomInMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        zoomInMenuItem.setText("Zoom in");
        zoomInMenuItem.setToolTipText("Zooms in around mouse point. Also Ctl+mouse wheel. Right click and drag to pan.");
        zoomInMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(zoomInMenuItem);

        zoomOutMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        zoomOutMenuItem.setText("Zoom out");
        zoomOutMenuItem.setToolTipText("Zooms out around mouse point. Also Ctl+mouse wheel. Right click and drag to pan.");
        zoomOutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(zoomOutMenuItem);

        unzoomMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        unzoomMenuItem.setText("Unzoom");
        unzoomMenuItem.setToolTipText("<html>Default display zooming, with border (see View/Filtering options/Set border space...). <p> Right click and drag to pan. <p> Ctl+wheel to zoom.");
        unzoomMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unzoomMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(unzoomMenuItem);

        graphicsSubMenu.setMnemonic('g');
        graphicsSubMenu.setText("View options");

        viewActiveRenderingEnabledMenuItem.setText("Active rendering enabled");
        viewActiveRenderingEnabledMenuItem.setToolTipText("<html>On: ViewLoop waits for each OpenGL present (display()). Off: async repaint(); the loop continues without waiting.<br>Recommend <b>on</b> for daily use. Use <b>off</b> with a high target FPS for latency-sensitive applications, e.g. robots.");
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

        setFrameRateMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        setFrameRateMenuItem.setText("Set rendering rate...");
        setFrameRateMenuItem.setToolTipText("Opens dialog to set the rendering (animation) target rate in frames/sec (fps)");
        setFrameRateMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setFrameRateMenuItemActionPerformed(evt);
            }
        });
        graphicsSubMenu.add(setFrameRateMenuItem);

        skipPacketsRenderingCheckBoxMenuItem.setText("Adaptive render skipping");
        skipPacketsRenderingCheckBoxMenuItem.setToolTipText("<html>Click the checkbox to enable/disable.<br>Hover and use the mouse wheel or Up/Down keys to change the maximum skipped packets.<br>Raw .aedat recording is unaffected.<br>Status bar shows ARS current/max and loop load (ld).");
        skipPacketsRenderingCheckBoxMenuItem.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                skipPacketsRenderingCheckBoxMenuItemStateChanged(evt);
            }
        });
        skipPacketsRenderingCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                skipPacketsRenderingCheckBoxMenuItemActionPerformed(evt);
            }
        });
        graphicsSubMenu.add(skipPacketsRenderingCheckBoxMenuItem);

        setBorderSpaceMenuItem.setText("Set border space...");
        setBorderSpaceMenuItem.setToolTipText("Set the border space around the chip canvas in pixels");
        setBorderSpaceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setBorderSpaceMenuItemActionPerformed(evt);
            }
        });
        graphicsSubMenu.add(setBorderSpaceMenuItem);

        viewMenu.add(graphicsSubMenu);

        menuBar.add(viewMenu);

        playbackMenu.setText("Playback");
        playbackMenu.setToolTipText("Controls playback time slices, frame rate, direction, etc");

        pauseRenderingCheckBoxMenuItem.setAction(aePlayer.pausePlayAction);
        pauseRenderingCheckBoxMenuItem.setText("Pause");
        playbackMenu.add(pauseRenderingCheckBoxMenuItem);

        viewStepForwardsMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PERIOD, 0));
        viewStepForwardsMI.setText("Step forwards");
        viewStepForwardsMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewStepForwardsMIActionPerformed(evt);
            }
        });
        playbackMenu.add(viewStepForwardsMI);

        viewStepBackwardsMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_COMMA, 0));
        viewStepBackwardsMI.setText("Step backwards");
        viewStepBackwardsMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewStepBackwardsMIActionPerformed(evt);
            }
        });
        playbackMenu.add(viewStepBackwardsMI);
        playbackMenu.add(jSeparator28);

        increasePlaybackSpeedMenuItem.setAction(aePlayer.fasterAction);
        increasePlaybackSpeedMenuItem.setText("Increase accumulation");
        increasePlaybackSpeedMenuItem.setToolTipText("<html>Makes the time slice or event count longer (see FlextTime mode)<p>Or use SHIFT+ALT+mouse wheel up.<p>Only enabled for playing back recorded data.");
        increasePlaybackSpeedMenuItem.setEnabled(false);
        playbackMenu.add(increasePlaybackSpeedMenuItem);

        decreasePlaybackSpeedMenuItem.setAction(aePlayer.slowerAction);
        decreasePlaybackSpeedMenuItem.setText("Decrease accumulation");
        decreasePlaybackSpeedMenuItem.setToolTipText("<html>Makes the time slice or event count shorter (see FlextTime mode)<p>Or use SHIFT+ALT+mouse wheel down.<p>Only enabled for playing back recorded data.");
        decreasePlaybackSpeedMenuItem.setEnabled(false);
        playbackMenu.add(decreasePlaybackSpeedMenuItem);

        flextimePlaybackEnabledCheckBoxMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, 0));
        flextimePlaybackEnabledCheckBoxMenuItem.setText("Toggle Flextime playback mode");
        flextimePlaybackEnabledCheckBoxMenuItem.setToolTipText("Toggles playback betweeen constant-duration and constant count event frames ");
        flextimePlaybackEnabledCheckBoxMenuItem.setEnabled(false);
        flextimePlaybackEnabledCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flextimePlaybackEnabledCheckBoxMenuItemActionPerformed(evt);
            }
        });
        playbackMenu.add(flextimePlaybackEnabledCheckBoxMenuItem);
        playbackMenu.add(jSeparator11);

        increaseFrameRateMenuItem.setAction(frameRateIncreaseAction);
        increaseFrameRateMenuItem.setText("Increase rendering frame rate");
        increaseFrameRateMenuItem.setToolTipText("Increases frames/second target for rendering");
        playbackMenu.add(increaseFrameRateMenuItem);

        decreaseFrameRateMenuItem.setAction(frameRateDecreaseAction);
        decreaseFrameRateMenuItem.setText("Decrease rendering frame rate");
        decreaseFrameRateMenuItem.setToolTipText("Decreases frames/second target for rendering");
        decreaseFrameRateMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decreaseFrameRateMenuItemActionPerformed(evt);
            }
        });
        playbackMenu.add(decreaseFrameRateMenuItem);
        playbackMenu.add(jSeparator23);

        rewindPlaybackMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, 0));
        rewindPlaybackMenuItem.setText("Rewind");
        rewindPlaybackMenuItem.setEnabled(false);
        rewindPlaybackMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rewindPlaybackMenuItemActionPerformed(evt);
            }
        });
        playbackMenu.add(rewindPlaybackMenuItem);

        togglePlaybackDirectionMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, 0));
        togglePlaybackDirectionMenuItem.setText("Toggle playback direction");
        togglePlaybackDirectionMenuItem.setEnabled(false);
        togglePlaybackDirectionMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                togglePlaybackDirectionMenuItemActionPerformed(evt);
            }
        });
        playbackMenu.add(togglePlaybackDirectionMenuItem);
        playbackMenu.add(jSeparator10);

        jogForwardMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PERIOD, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jogForwardMI.setText("Jog Forward N packets");
        jogForwardMI.setToolTipText("Or use SHIFT+mouse wheel");
        jogForwardMI.setActionCommand("Jog forward N packets");
        jogForwardMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jogForwardMIActionPerformed(evt);
            }
        });
        playbackMenu.add(jogForwardMI);

        jogBackwardsMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_COMMA, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        jogBackwardsMI.setText("Jog back N packets");
        jogBackwardsMI.setToolTipText("Or use SHIFT+mouse wheel");
        jogBackwardsMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jogBackwardsMIActionPerformed(evt);
            }
        });
        playbackMenu.add(jogBackwardsMI);

        cancelJogMI.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0));
        cancelJogMI.setText("Cancel jog");
        cancelJogMI.setToolTipText("Cancel queued jog packets (Esc); useful for slow HDF5 seeks");
        cancelJogMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelJogMIActionPerformed(evt);
            }
        });
        playbackMenu.add(cancelJogMI);

        setJogNCount.setText("Set jog N...");
        setJogNCount.setToolTipText("Sets the size of jog for keyboard jog");
        setJogNCount.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setJogNCountActionPerformed(evt);
            }
        });
        playbackMenu.add(setJogNCount);
        playbackMenu.add(jSeparator19);

        setMarkInMI.setAction(aePlayer.markInAction);
        setMarkInMI.setText("Set IN marker");
        setMarkInMI.setToolTipText("If playing back file, it rewinds to this position");
        playbackMenu.add(setMarkInMI);

        setMarkOutMI.setAction(aePlayer.markOutAction);
        setMarkOutMI.setText("Set OUT marker");
        setMarkOutMI.setToolTipText("If playing back recording, it plays to this marker");
        playbackMenu.add(setMarkOutMI);

        toggleMarkerMI.setAction(aePlayer.toggleMarkerAction);
        toggleMarkerMI.setText("Toggle marker");
        playbackMenu.add(toggleMarkerMI);

        jumpPrevMarkerMI.setAction(aePlayer.jumpPrevMarkerAction);
        jumpPrevMarkerMI.setText("Jump to previous marker");
        playbackMenu.add(jumpPrevMarkerMI);

        jumpNextMarkerMI.setAction(aePlayer.jumpNextMarkerAction);
        jumpNextMarkerMI.setText("Jump to next marker");
        playbackMenu.add(jumpNextMarkerMI);

        clearMarksMI.setAction(aePlayer.clearMarksAction);
        clearMarksMI.setText("Clear IN and OUT markers");
        clearMarksMI.setToolTipText("Clears the IN and OUT markers for playing back a section of a recording");
        playbackMenu.add(clearMarksMI);

        exportMarksMI.setAction(aePlayer.exportMarksAction);
        exportMarksMI.setText("Export marks");
        playbackMenu.add(exportMarksMI);

        importMarksMI.setAction(aePlayer.importMarksAction);
        importMarksMI.setText("Import marks");
        playbackMenu.add(importMarksMI);

        menuBar.add(playbackMenu);

        deviceMenu.setMnemonic('a');
        deviceMenu.setText("AEChip");
        deviceMenu.setToolTipText("Specifies which AEChip class is used either for playback or live interfacnig to a device");
        deviceMenu.add(jSeparator3);

        renewChipMI.setText("Renew AEChip");
        renewChipMI.setToolTipText("Construct new instance of selected AEChip");
        renewChipMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                renewChipMIActionPerformed(evt);
            }
        });
        deviceMenu.add(renewChipMI);
        deviceMenu.add(deviceMenuSpparator);

        customizeDevicesMenuItem.setMnemonic('C');
        customizeDevicesMenuItem.setText("Customize AEChip Menu...");
        customizeDevicesMenuItem.setToolTipText("Let's you customize which AEChip's are available. If your device does not appear, then find it and add it using this option.");
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

        refreshInterfaceMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK));
        refreshInterfaceMenuItem.setMnemonic('R');
        refreshInterfaceMenuItem.setText("Refresh");
        refreshInterfaceMenuItem.setToolTipText("Rescan USB for newly attached cameras without closing the current interface. Opens the camera if exactly one is found (Ctrl+Shift+U; needed on Windows which lacks USB hotplug)");
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
        controlMenu.setText("USB");
        controlMenu.setToolTipText("USB reader and render buffer tuning");

        viewBiasesMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        viewBiasesMenuItem.setMnemonic('b');
        viewBiasesMenuItem.setText("Biases/HW Configuration");
        viewBiasesMenuItem.setToolTipText("Shows chip or board configuration controls");
        viewBiasesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewBiasesMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(viewBiasesMenuItem);
        controlMenu.add(jSeparator26);

        usbTuningMenuItem.setText("USB tuning...");
        usbTuningMenuItem.setToolTipText("<html>Open a separate window to adjust USB FIFO, buffer count, and AE render packet size<br>with spinner arrows, typed values, and keyboard while the camera is running.");
        usbTuningMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                usbTuningMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(usbTuningMenuItem);
        controlMenu.add(jSeparator5);

        printUSBStatisticsCBMI.setMnemonic('t');
        printUSBStatisticsCBMI.setSelected(true);
        printUSBStatisticsCBMI.setText("Show USB statistics");
        printUSBStatisticsCBMI.setToolTipText("Prints statistics about USB packet sizes and packet intervals to System.out (only visible in standard console, not built in logging console)");
        printUSBStatisticsCBMI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printUSBStatisticsCBMIActionPerformed(evt);
            }
        });
        controlMenu.add(printUSBStatisticsCBMI);
        controlMenu.add(jSeparator24);

        zeroTimestampsMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, 0));
        zeroTimestampsMenuItem.setText("Zero timestamps");
        zeroTimestampsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zeroTimestampsMenuItemActionPerformed(evt);
            }
        });
        controlMenu.add(zeroTimestampsMenuItem);

        resetUsbInterfaceMenuItem.setAction(new ResetHardwareIntefaceAction());
        controlMenu.add(resetUsbInterfaceMenuItem);

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
        helpMenu.add(jSeparator7);

        releaseNotesMenuItem.setText("Release notes");
        releaseNotesMenuItem.setToolTipText("Opens jAER GitHub Releases (release notes and installers)");
        releaseNotesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                releaseNotesMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(releaseNotesMenuItem);

        checkForUpdatesMenuItem.setText("Check for release updates...");
        checkForUpdatesMenuItem.setToolTipText("Checks if there is a newer release of jAER installer on github");
        checkForUpdatesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkForUpdatesMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(checkForUpdatesMenuItem);

        gitUpdateMenuItem.setText("Git update and build jAER....");
        gitUpdateMenuItem.setToolTipText("Shows dialog to check for git updates to jAER");
        gitUpdateMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                gitUpdateMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(gitUpdateMenuItem);
        helpMenu.add(jSeparator6);

        loggingLevelMenu.setText("Set logging level...");
        loggingLevelMenu.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                loggingLevelMenuMenuSelected(evt);
            }
        });
        helpMenu.add(loggingLevelMenu);
        helpMenu.add(jSeparator25);

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
            setSize(getWidth() + widthInc, getHeight() + heightInc);
	}//GEN-LAST:event_resizeLabelMouseDragged

	private void enableFiltersOnStartupCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableFiltersOnStartupCheckBoxMenuItemActionPerformed
            setEnableFiltersOnStartup(enableFiltersOnStartupCheckBoxMenuItem.isSelected());
	}//GEN-LAST:event_enableFiltersOnStartupCheckBoxMenuItemActionPerformed

    void fixSkipPacketsRenderingMenuItems() {
        if (chip == null || chip.getRenderer() == null) {
            return;
        }
        skipPacketsRenderingCheckBoxMenuItem.refreshLabel();
    }

    private void showAdaptiveRenderSkippingOverlay() {
        if (chip == null || chip.getRenderer() == null) {
            return;
        }
        final AEChipRenderer renderer = chip.getRenderer();
        showActionText(String.format("Adaptive render skipping %s; maximum %d packets",
                renderer.isAdaptiveRenderSkippingEnabled() ? "ON" : "OFF",
                renderer.getConfiguredSkipFrameRenderingNumberMax()));
    }

    /**
     * Keeps the ARS menu checkbox aligned with renderer state (live or playback).
     */
    void syncAdaptiveRenderSkipMenuFromRenderer() {
        if (chip == null || chip.getRenderer() == null) {
            return;
        }
        suppressAdaptiveRenderSkipMenuSync = true;
        try {
            skipPacketsRenderingCheckBoxMenuItem.setSelected(chip.getRenderer().isAdaptiveRenderSkippingEnabled());
            fixSkipPacketsRenderingMenuItems();
        } finally {
            suppressAdaptiveRenderSkipMenuSync = false;
        }
    }

    /**
     * ARS is for live overload; disable while playing back a file unless the user turns it on.
     */
    private void updateAdaptiveRenderSkippingForPlayMode(PlayMode oldMode, PlayMode newMode) {
        // Avoid getRenderer() here: it throws if chip is still null during startup.
        if (chip == null || chip.getRenderer() == null) {
            return;
        }
        if (newMode == PlayMode.PLAYBACK && oldMode != PlayMode.PLAYBACK) {
            adaptiveRenderSkipMaxBeforePlayback = getRenderer().getSkipFrameRenderingNumberMax();
            getRenderer().setSkipFrameRenderingNumberMax(0, false);
            getRenderer().clearPacketRenderSkipDecision();
        } else if (oldMode == PlayMode.PLAYBACK && newMode != PlayMode.PLAYBACK) {
            if (adaptiveRenderSkipMaxBeforePlayback >= 0) {
                getRenderer().setSkipFrameRenderingNumberMax(adaptiveRenderSkipMaxBeforePlayback, true);
                adaptiveRenderSkipMaxBeforePlayback = -1;
            }
        }
        syncAdaptiveRenderSkipMenuFromRenderer();
    }

    /**
     * Stops live USB reader threads while playing back a file so NRV/Prophesee/DAVIS
     * background transfers do not fill host buffers. Resume is handled by {@link AEPlayer#stopPlayback()}.
     */
    private void updateLiveAcquisitionForPlayMode(PlayMode oldMode, PlayMode newMode) {
        if (newMode == PlayMode.PLAYBACK && oldMode != PlayMode.PLAYBACK) {
            log.fine("updateLiveAcquisition: entering PLAYBACK from " + oldMode
                    + " aemon=" + aemon
                    + " open=" + (aemon != null && aemon.isOpen())
                    + " acqEnabled=" + (aemon != null && aemon.isOpen() && aemon.isEventAcquisitionEnabled()));
            if (oldMode == PlayMode.SEQUENCING) {
                stopSequencing();
            }
            if (aemon != null && aemon.isOpen() && aemon.isEventAcquisitionEnabled()) {
                try {
                    log.fine("updateLiveAcquisition: setEventAcquisitionEnabled(false) begin");
                    aemon.setEventAcquisitionEnabled(false);
                    eventAcquisitionPausedForPlayback = true;
                    log.info("paused live event acquisition for file playback");
                    log.fine("updateLiveAcquisition: setEventAcquisitionEnabled(false) done");
                } catch (HardwareInterfaceException e) {
                    log.warning("failed to pause live acquisition for playback: " + e.getMessage());
                }
            }
        } else if (oldMode == PlayMode.PLAYBACK && newMode != PlayMode.PLAYBACK) {
            eventAcquisitionPausedForPlayback = false;
        }
    }

	private void customizeDevicesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_customizeDevicesMenuItemActionPerformed
            //        log.info("customizing chip classes");
            ClassChooserDialog dlg;
            try {
                //            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                dlg = new ClassChooserDialog(this, AEChip.class, chipClassNames, null);
            } finally {
                //            setCursor(Cursor.getDefaultCursor());
            }
            dlg.setLocationRelativeTo(this);
            dlg.setVisible(true);
            int ret = dlg.getReturnStatus();
            if (ret == ClassChooserDialog.RET_OK) {
                chipClassNames = dlg.getList();
                putChipClassPrefs();
                buildDeviceMenu();
                if (dlg.getLastSelectedClass() != null) {
                    String cn = dlg.getLastSelectedClass();
                    try {
                        Class cl = FastClassFinder.forName(cn);
                        setCursor(new Cursor(Cursor.WAIT_CURSOR));
                        clearRememberedLiveChipSelections();
                        setAeChipClass(cl);
                        log.info(String.format("Set AEChip to last one added which is %s", cn));
                        JOptionPane.showMessageDialog(this,
                                String.format("Set AEChip to  %s", cn),
                                "AEChip error", JOptionPane.INFORMATION_MESSAGE);

                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(this,
                                String.format("Error setting AEChip to  %s: got exception %s", cn, e.toString()),
                                "AEChip error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
	}//GEN-LAST:event_customizeDevicesMenuItemActionPerformed

	private void sequenceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sequenceMenuItemActionPerformed

            if (evt.getActionCommand().equals("start")) {
                float oldScale = chipCanvas.getScale();
                AESequencerInterface aemonseq = (AESequencerInterface) chip.getHardwareInterface();
                try {
                    if ((aemonseq != null) && (aemonseq instanceof AEMonitorSequencerInterface)) {
                        ((AEMonitorSequencerInterface) aemonseq).stopMonitoringSequencing();
                    }
                } catch (HardwareInterfaceException e) {
                    log.log(Level.SEVERE, e.toString(), e);

                }

                JFileChooser fileChooser = new JFileChooser();
                ChipDataFilePreview preview = new ChipDataFilePreview(fileChooser, chip); // from book swing hacks
                fileChooser.addPropertyChangeListener(preview);
                fileChooser.setAccessory(preview);

                String lastFilePath = prefs.get("AEViewer.lastFile", ""); // getString the last folder

                lastFile = new File(lastFilePath);

                DATFileFilter.installOpenDialogFilters(fileChooser, null);
                fileChooser.setCurrentDirectory(lastFile); // sets the working directory of the chooser
                //            boolean wasPaused=isPaused();
                //        setPaused(true);
                int retValue = fileChooser.showOpenDialog(this);
                if (retValue == JFileChooser.APPROVE_OPTION) {
                    lastFile = fileChooser.getSelectedFile();
                    if (lastFile != null) {
                        recentFiles.addFile(lastFile);
                    }
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            sequenceFile(lastFile);
                        }
                    });
                }
                fileChooser = null;
                //     setPaused(false);
                //            chipCanvas.setScale(oldScale);
            } else if (evt.getActionCommand().equals("stop")) {
                setPlayMode(PlayMode.LIVE);
                stopSequencing();
            }
	}//GEN-LAST:event_sequenceMenuItemActionPerformed

    private void sequenceFile(File file) {
        try {
            AEFileInputStream fileAEInputStream = new AEFileInputStream(file, getChip());
            fileAEInputStream.setFile(file);
            fileAEInputStream.setRepeat(aePlayer.isRepeat());
            fileAEInputStream.setNonMonotonicTimeExceptionsChecked(false); // the code below has to take care about non-monotonic time anyway
            setInputFile(file);

            int numberOfEvents = (int) fileAEInputStream.size();

            AEPacketRaw seqPkt = fileAEInputStream.readPacketByNumber(numberOfEvents);

            if (seqPkt.getNumEvents() < numberOfEvents) {
                int[] ad = new int[numberOfEvents];
                int[] ts = new int[numberOfEvents];
                int remainingevents = numberOfEvents;
                int ind = 0;
                do {
                    remainingevents = remainingevents - AEFileInputStream.MAX_BUFFER_SIZE_EVENTS;
                    System.arraycopy(seqPkt.getTimestamps(), 0, ts, ind * AEFileInputStream.MAX_BUFFER_SIZE_EVENTS, seqPkt.getNumEvents());
                    System.arraycopy(seqPkt.getAddresses(), 0, ad, ind * AEFileInputStream.MAX_BUFFER_SIZE_EVENTS, seqPkt.getNumEvents());
                    seqPkt = fileAEInputStream.readPacketByNumber(remainingevents);
                    ind++;

                } while (remainingevents > AEFileInputStream.MAX_BUFFER_SIZE_EVENTS);

                seqPkt = new AEPacketRaw(ad, ts);
            }
            // calculate interspike intervals
            int[] ts = seqPkt.getTimestamps();
            int[] isi = new int[seqPkt.getNumEvents()];

            isi[0] = ts[0];

            for (int i = 1; i < seqPkt.getNumEvents(); i++) {
                isi[i] = ts[i] - ts[i - 1];
                if (isi[i] < 0) {
                    //  if (!(ts[i-1]>0 && ts[i]<0)) //if it is not an overflow, it is non-monotonic time, so set isi to zero
                    //{
                    log.info("non-monotonic time at event " + i + ", set interspike interval to zero");
                    isi[i] = 0;
                    //}
                }
            }
            seqPkt.setTimestamps(isi);

            AESequencerInterface aemonseq = (AESequencerInterface) chip.getHardwareInterface();

            setPaused(false);

            if (aemonseq instanceof AEMonitorSequencerInterface) {
                ((AEMonitorSequencerInterface) aemonseq).startMonitoringSequencing(seqPkt);
            } else {
                aemonseq.startSequencing(seqPkt);
            }
            aemonseq.setLoopedSequencingEnabled(true);
            setPlayMode(PlayMode.SEQUENCING);
            sequenceMenuItem.setActionCommand("stop");
            sequenceMenuItem.setText("Stop sequencing data file");

            if (!playerControlPanel.isVisible()) {
                playerControlPanel.setVisible(true);
            }
            //   playerSlider.setVisible(true);
            playerControls.getPlayerSlider().setEnabled(false);
            //            System.gc(); // garbage collect...
        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }

    /**
     * Stops sequencing.
     */
    public void stopSequencing() {
        try {
            if ((chip != null) && (chip.getHardwareInterface() != null)) {
                ((AESequencerInterface) chip.getHardwareInterface()).stopSequencing();
            }

        } catch (HardwareInterfaceException e) {
            log.log(Level.SEVERE, e.toString(), e);
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
            showActionText(chipCanvas.getDisplayMethod().getClass().getSimpleName());
	}//GEN-LAST:event_cycleDisplayMethodButtonActionPerformed

	private void unzoomMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unzoomMenuItemActionPerformed
            chipCanvas.unzoom();
	}//GEN-LAST:event_unzoomMenuItemActionPerformed

	private void viewIgnorePolarityCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewIgnorePolarityCheckBoxMenuItemActionPerformed
            chip.getRenderer().setIgnorePolarityEnabled(viewIgnorePolarityCheckBoxMenuItem.isSelected());
	}//GEN-LAST:event_viewIgnorePolarityCheckBoxMenuItemActionPerformed

	private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
            log.info("window closed event, calling stopMe");
            stopMe();
	}//GEN-LAST:event_formWindowClosed

	private void monSeqMissedEventsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_monSeqMissedEventsMenuItemActionPerformed
            if (aemon instanceof CypressFX2MonitorSequencer) {
                CypressFX2MonitorSequencer fx = (CypressFX2MonitorSequencer) aemon;
                try {
                    JOptionPane.showMessageDialog(this, fx + " missed approximately " + fx.getNumMissedEvents() + " events");
                } catch (Exception e) {
                    log.log(Level.SEVERE, e.toString(), e);
                    aemon.close();
                }
            }
	}//GEN-LAST:event_monSeqMissedEventsMenuItemActionPerformed
    volatile boolean doSingleStepEnabled = false;

    synchronized public void doSingleStep() {
        setPaused(true); // better to set paused before single colorContrastAdditiveStep starts
        setDoSingleStepEnabled(true);
        interruptViewloop();
    }

    public void setDoSingleStepEnabled(boolean yes) {
        doSingleStepEnabled = yes;
    }

    public boolean isSingleStep() {
        //        boolean isSingle=caviarViewer.getSyncPlayer().isSingleStep();
        //        return isSingle;
        return doSingleStepEnabled;
    }

    public void singleStepDone() {
        if (isSingleStep()) {
//            log.info("finished single colorContrastAdditiveStep");
            setDoSingleStepEnabled(false);
        }
    }

	private void viewStepForwardsMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewStepForwardsMIActionPerformed
            getAePlayer().stepForwardAction.actionPerformed(evt);
	}//GEN-LAST:event_viewStepForwardsMIActionPerformed

    private void buildMonSeqMenu() {
        monSeqMenu.getPopupMenu().setLightWeightPopupEnabled(false); // canvas is heavyweight so we need this to make menu popup show
        monSeqOperationModeMenu.getPopupMenu().setLightWeightPopupEnabled(false); // canvas is heavyweight so we need this to make menu popup show
        monSeqOperationModeMenu.setText("MonitorSequencer Operation Mode");
        monSeqOpMode0.setText("Tick: 1us");
        monSeqOpMode1.setText("Tick: 0.2us");
        monSeqMissedEventsMenuItem.setText("Get number of missed events");
    }

    private void enableMonSeqMenu(boolean state) {
        monSeqMenu.setEnabled(state);
        if (chip.getHardwareInterface() instanceof AEMonitorInterface) {
            monSeqOperationModeMenu.setEnabled(state);
            monSeqOpMode0.setEnabled(state);
            monSeqOpMode1.setEnabled(state);
            monSeqMissedEventsMenuItem.setEnabled(state);
            enableMissedEventsCheckBox.setEnabled(state);
        }
        sequenceMenuItem.setEnabled(state);
    }
    // used to print dt for measuring frequency from playback by using '1' keystrokes

    //    class Statistics {
    //
    //        JFrame statFrame;
    //        JLabel statLabel;
    //        int lastTime = 0, thisTime;
    //        EngineeringFormat fmt = new EngineeringFormat();
    //
    //        {
    //            fmt.precision = 2;
    //        }
    //
    //        void printStats() {
    //            synchronized (aePlayer) {
    //                thisTime = aePlayer.getTime();
    //                int dt = lastTime - thisTime;
    //                float dtSec = (float) ((float) dt / 1e6f + java.lang.Float.MIN_VALUE);
    //                float freqHz = 1 / dtSec;
    ////                System.out.println(String.format("dt=%.2g s, freq=%.2g Hz",dtSec,freqHz));
    //                if (statFrame == null) {
    //                    statFrame = new JFrame("Statistics");
    //                    statLabel = new JLabel();
    //                    statLabel.setFont(statLabel.getFont().deriveFont(16f));
    //                    statLabel.setToolTipText("Type \"1\" to update interval statistics");
    //                    statFrame.getContentPane().setLayout(new BorderLayout());
    //                    statFrame.getContentPane().appendOfEventReferences(statLabel, BorderLayout.CENTER);
    //                    statFrame.pack();
    //                }
    //                String s = " dt=" + fmt.format(dtSec) + "s, freq=" + fmt.format(freqHz) + " Hz ";
    //                log.info(s);
    //                statLabel.setText(s);
    //                statLabel.revalidate();
    //                statFrame.pack();
    //                if (!statFrame.isVisible()) {
    //                    statFrame.setVisible(true);
    //                }
    //                requestFocus(); // leave the focus here
    //                lastTime = thisTime;
    //            }
    //        }
    //    }
    //    Statistics statistics;
	private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
            log.info("window closing");
            if ((biasgenFrame != null) && !biasgenFrame.isModificationsSaved()) {
                return;
            }
            if (!confirmExitFromWindowClose()) {
                return;
            }
            if (getOpenViewerCount() <= 1) {
                doExitAllViewers();
            } else {
                closeThisViewerOnly(false);
            }
	}//GEN-LAST:event_formWindowClosing

        /**
         * One-time confirm for title-bar close and for the {@code x} accelerator
         * when this is the last AEViewer. File → Exit does not use this.
         * Don't show again defaults to checked so new users confirm once then skip later.
         */
        private boolean confirmExitFromWindowClose() {
            WarningDialogWithDontShowPreference d = new WarningDialogWithDontShowPreference(this, true,
                    "Confirm exit",
                    "<html>Do you want to exit jAER?<p>Shown once for the title-bar close button or the <b>x</b> accelerator"
                    + " when this is the last AEViewer.",
                    JOptionPane.QUESTION_MESSAGE, true, JOptionPane.OK_CANCEL_OPTION);
            d.setLocationRelativeTo(this);
            d.setVisible(true);
            return d.isConfirmed();
        }

        /**
         * First time {@code x} is used with several AEViewers: sticky choice.
         * @return 0 exit completely, 1 close this viewer, anything else cancel
         */
        private int offerFirstTimeXExitChoice(int viewerCount) {
            String msg = "<html>You have <b>" + viewerCount + " AEViewer</b> windows open.<p>"
                    + "What should the <b>x</b> key do from now on?<p>"
                    + "You can change this later in File → Preferences "
                    + "(<i>Exit completely with 'x'</i>).";
            String[] options = {"Exit completely", "Close this viewer", "Cancel"};
            return JOptionPane.showOptionDialog(this, msg, "x key with multiple AEViewers",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        }

        /** Subsequent {@code x} with several windows and {@link #isExitCompletelyWithX()}. */
        private boolean confirmExitClosesAllViewers(int viewerCount) {
            String msg = "<html>Exit will close all <b>" + viewerCount + " AEViewer</b> windows.<p>Continue?";
            int choice = JOptionPane.showConfirmDialog(this, msg, "Exit jAER?",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            return choice == JOptionPane.OK_OPTION;
        }

        /**
         * Close this window only; other AEViewers stay open.
         * @param removeFromViewerList false when already inside {@code windowClosing}
         * (JAERViewer removes this viewer); true for File → Exit / {@code x}
         */
        private void closeThisViewerOnly(boolean removeFromViewerList) {
            log.info("closing this AEViewer; other windows remain");
            try {
                stopViewLoopForExit();
                LibUsbHotplug.removeListener(usbHotplugListener);
                cleanup(true);
                if ((filterFrame != null) && filterFrame.isVisible()) {
                    filterFrame.dispose();
                }
                disposeRosOutputDialog();
                disposeDnnSharedMemoryDialog();
                disposeOpenCvOutputDialog();
                disposeFileMenuFrames();
                stopMe();
                if (removeFromViewerList && jaerViewer != null && jaerViewer.getViewers().contains(this)) {
                    jaerViewer.removeViewer(this);
                }
                dispose();
            } catch (Throwable t) {
                log.log(Level.SEVERE, "orderly viewer-close failed", t);
            }
        }

        /** Quit the JVM after stopping this viewer's loop and USB. */
        private void doExitAllViewers() {
            armExitWatchdog();
            try {
                stopViewLoopForExit();
                LibUsbHotplug.removeListener(usbHotplugListener);
                cleanup(true);
                dispose();
                System.exit(0);
            } catch (Throwable t) {
                log.log(Level.SEVERE, "orderly Exit-menu shutdown failed; forcing System.exit(1)", t);
                System.exit(1);
            }
        }

	private void refreshInterfaceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshInterfaceMenuItemActionPerformed
            showActionText("Scanning USB…");
            Thread t = new Thread(() -> {
                try {
                    HardwareInterfaceFactory.instance().markUsbEnumerationDirty();
                    final int n = HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
                    if (!LibUsbHotplug.isSupported()) {
                        windowsUsbPoll.noteScanResult(usbDeviceFingerprint(n),
                                System.currentTimeMillis(), log);
                    }
                    SwingUtilities.invokeLater(() -> {
                        // User asked to find cameras; allow auto-open even after Interface→None
                        // or a failed open (those set nullInterface and block WAITING).
                        SessionCameraOpenCoordinator.userRequestedOpen(AEViewer.this);
                        nullInterface = false;
                        lastInterfaceCheckTime = System.currentTimeMillis();
                        boolean playback = getPlayMode() == PlayMode.PLAYBACK
                                || getPlayMode() == PlayMode.FILTER_INPUT;
                        boolean bound = false;
                        if (!playback) {
                            bound = bindRememberedInterfaceIfPossible(n);
                            if (!bound) {
                                bound = bindUnambiguousInterfaceIfPossible(n);
                            }
                        }
                        buildInterfaceMenu();
                        if (bound) {
                            interruptViewloop();
                            showActionText("Found 1 hardware interface, opening…");
                        } else if (n > 1) {
                            showActionText(String.format("Found %d hardware interfaces; choose one from Interface menu", n));
                        } else {
                            showActionText(String.format("Found %d hardware interface(s)", n));
                        }
                    });
                } catch (Exception e) {
                    log.warning("USB refresh failed: " + e);
                    SwingUtilities.invokeLater(() -> showActionText("USB refresh failed"));
                }
            }, "jaer-usb-refresh");
            t.setDaemon(true);
            t.start();
	}//GEN-LAST:event_refreshInterfaceMenuItemActionPerformed

	private void filtersToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filtersToggleButtonActionPerformed
            showFilters(filtersToggleButton.isSelected());
	}//GEN-LAST:event_filtersToggleButtonActionPerformed

	private void biasesToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_biasesToggleButtonActionPerformed
            showBiasgen(biasesToggleButton.isSelected());
	}//GEN-LAST:event_biasesToggleButtonActionPerformed

	private void imagePanelMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_imagePanelMouseWheelMoved
            boolean control = ((evt.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK);
            boolean alt = ((evt.getModifiersEx() & InputEvent.ALT_DOWN_MASK) == InputEvent.ALT_DOWN_MASK);;
            boolean shift = ((evt.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == InputEvent.SHIFT_DOWN_MASK);;
            int rotation = evt.getWheelRotation();
            ActionEvent ae = new ActionEvent(evt.getSource(), evt.getID(), evt.paramString());

            if (!(control || alt || shift)) {
                if (rotation > 0) {
                    getRenderer().decreaseContrastAction.actionPerformed(ae);
                } else if (rotation < 0) {
                    getRenderer().increaseContrastAction.actionPerformed(ae);
                }
                if (isPaused()) {
                    interruptViewloop();
                }
            } else if (control && !(shift || alt)) {
                if (rotation > 0) {
                    chipCanvas.zoomOutAround(evt.getPoint()); // wheel down
                } else if (rotation < 0) {
                    chipCanvas.zoomInAround(evt.getPoint()); //wheel up
                }
                if (isPaused()) {
                    interruptViewloop();
                }
            } else if (shift && !(control || alt)) { // shift mouse scrolls through recording
                if (getAePlayer() != null) {
                    AbstractAEPlayer p = getAePlayer();
                    int rabs = (int) Math.abs(rotation);
                    if (p.isPaused()) {
                        for (int i = 0; i < rabs; i++) {
                            if (rotation < 0) {
                                p.stepForwardAction.actionPerformed(ae);
                            } else {
                                p.stepBackwardAction.actionPerformed(ae);
                            }
                            while (isSingleStep()) {
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    break;
                                }
                            }
                        }
                    } else {
                        try {
                            for (int i = 0; i < rabs; i++) {
                                if (rotation < 0) {
                                    jogForwardMIActionPerformed(ae);
                                } else {
                                    jogBackwardsMIActionPerformed(ae);
                                }
                            }
                        } finally {
                        }
                    }
                }
            } else if (shift && alt && !(control)) { // shift+alt mouse changes timeslice
                if (getAePlayer() != null) {
                    AbstractAEPlayer p = getAePlayer();
                    int n = (int) Math.abs(rotation);
                    if (rotation < 0) { // mouse wheel up
                        aePlayer.fasterAction.actionPerformed(null);
                    } else if (rotation > 0) {
                        aePlayer.slowerAction.actionPerformed(null);
                    }
                }
                if (isPaused()) {
                    interruptViewloop();
                }
            }
	}//GEN-LAST:event_imagePanelMouseWheelMoved

	private void togglePlaybackDirectionMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_togglePlaybackDirectionMenuItemActionPerformed
            getAePlayer().reverseAction.actionPerformed(evt);
	}//GEN-LAST:event_togglePlaybackDirectionMenuItemActionPerformed

	private void flextimePlaybackEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flextimePlaybackEnabledCheckBoxMenuItemActionPerformed
            if (jaerViewer == null) {
                return;
            }
            if (!jaerViewer.isSyncEnabled() || (jaerViewer.getViewers().size() == 1)) {
                getAePlayer().toggleFlextimeAction.actionPerformed(evt);
            } else {
                JOptionPane.showMessageDialog(this, "Flextime playback doesn't make sense for sychronized viewing");
                flextimePlaybackEnabledCheckBoxMenuItem.setSelected(false);
            }
	}//GEN-LAST:event_flextimePlaybackEnabledCheckBoxMenuItemActionPerformed

	private void rewindPlaybackMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rewindPlaybackMenuItemActionPerformed
            getAePlayer().rewindAction.actionPerformed(evt);
	}//GEN-LAST:event_rewindPlaybackMenuItemActionPerformed

	private void zeroTimestampsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zeroTimestampsMenuItemActionPerformed
            if ((jaerViewer != null) && jaerViewer.isSyncEnabled()) {
                log.info("zeroing timestamps on all viewers because isSyncEnabled=true");
                jaerViewer.zeroTimestamps();
                showActionText("Zeroed timestamps");
            } else {
                log.info("zeroing timestamps only on current AEViewer " + this);
                zeroTimestamps();
            }
	}//GEN-LAST:event_zeroTimestampsMenuItemActionPerformed

	private void decreaseFrameRateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decreaseFrameRateMenuItemActionPerformed
            setDesiredFrameRate(getDesiredFrameRate() / 2);
            showActionText(String.format("Decrease rendering frame rate to %d Hz", getDesiredFrameRate()));
	}//GEN-LAST:event_decreaseFrameRateMenuItemActionPerformed

	private void cycleNextColorRenderingMethodMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cycleNextColorRenderingMethodMenuItemActionPerformed
            if ((chipCanvas != null) && (chipCanvas.getDisplayMethod() != null) /*&& (chipCanvas.getDisplayMethod() instanceof DisplayMethod2D)*/) {
                getRenderer().cycleColorMode(true);
                showActionText(String.format("DVS color mode %s", getRenderer().getColorMode().toString()));
            } else {
                log.warning("It does not make sense to cycle color mode for this display method, ignoring");
            }
	}//GEN-LAST:event_cycleNextColorRenderingMethodMenuItemActionPerformed

    /**
     * Fills in the device control menu (the USB menu) so that menu items are
     * populated with correct values of USB buffer size and number of buffers,
     * etc. Runs in the Swing worker thread. Coalesced: ViewLoop used to call
     * this every packet via {@link #openAEMonitor()}, flooding the EDT.
     */
    private final AtomicBoolean deviceControlMenuFixScheduled = new AtomicBoolean(false);

    public void fixDeviceControlMenuItems() {
        if (!deviceControlMenuFixScheduled.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                deviceControlMenuFixScheduled.set(false);
                final boolean deviceOpen = aemon != null && aemon.isOpen();
                if (!deviceOpen) {
                    for (int i = 0; i < controlMenu.getMenuComponentCount(); i++) {
                        if (controlMenu.getMenuComponent(i) instanceof JMenuItem) {
                            final JMenuItem item = (JMenuItem) controlMenu.getMenuComponent(i);
                            // Keep reset available for recovery after a failed USB session.
                            item.setEnabled(item == resetUsbInterfaceMenuItem);
                        }
                    }
                    return;
                }

                final boolean readerControl = aemon instanceof ReaderBufferControl;

                if (readerControl) {
                    ReaderBufferControl reader = (ReaderBufferControl) aemon;
                    PropertyChangeSupport readerSupport = reader.getReaderSupport();
                    if (!readerSupport.hasListeners("readerStarted")) {
                        readerSupport.addPropertyChangeListener("readerStarted", AEViewer.this);
                    }
                }

                final int k = controlMenu.getMenuComponentCount();
                for (int i = 0; i < k; i++) {
                    final Component c = controlMenu.getMenuComponent(i);
                    if (c instanceof JMenuItem) {
                        ((JMenuItem) c).setEnabled(true);
                    }
                }
            }
        });
    }

    private void setupAdaptiveRenderSkippingMenu() {
        ScrollWheelTunableMenuItem.installPopupWheelHandler(graphicsSubMenu);
        skipPacketsRenderingCheckBoxMenuItem.bind(new ScrollWheelTunableMenuItem.IntParameter() {
            private static final int MIN_SKIP = 1;
            private static final int MAX_SKIP = 1000;

            @Override
            public int get() {
                // Chip/renderer are not ready yet during AEViewer construction when bind() refreshes the label.
                if (chip == null || chip.getRenderer() == null) {
                    return AEChipRenderer.DEFAULT_SKIP_PACKETS_RENDERING_MAX;
                }
                return chip.getRenderer().getConfiguredSkipFrameRenderingNumberMax();
            }

            @Override
            public void set(int value) {
                if (chip == null || chip.getRenderer() == null) {
                    return;
                }
                chip.getRenderer().setConfiguredSkipFrameRenderingNumberMax(value);
                if (getPlayMode() == PlayMode.PLAYBACK && adaptiveRenderSkipMaxBeforePlayback > 0) {
                    adaptiveRenderSkipMaxBeforePlayback = value;
                }
            }

            @Override
            public int stepUp(int current) {
                return Math.min(current + 1, MAX_SKIP);
            }

            @Override
            public int stepDown(int current) {
                return Math.max(current - 1, MIN_SKIP);
            }

            @Override
            public String formatLabel(int value) {
                return String.format("Adaptive render skipping: max %d packets", value);
            }
        }, () -> {
            syncAdaptiveRenderSkipMenuFromRenderer();
            showAdaptiveRenderSkippingOverlay();
        });

        graphicsSubMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                syncAdaptiveRenderSkipMenuFromRenderer();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
    }

    private UsbTuningFrame usbTuningFrame;

    private void showUsbTuningFrame() {
        if (usbTuningFrame == null || !usbTuningFrame.isDisplayable()) {
            usbTuningFrame = new UsbTuningFrame(this);
        }
        usbTuningFrame.showForCurrentDevice();
    }

	private void viewFiltersMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewFiltersMenuItemActionPerformed
            showFilters(true);
	}//GEN-LAST:event_viewFiltersMenuItemActionPerformed

	private void viewBiasesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewBiasesMenuItemActionPerformed
            showBiasgen(true);
	}//GEN-LAST:event_viewBiasesMenuItemActionPerformed
    //avoid stateChanged events from slider that is set by player
    volatile boolean sliderDontProcess = false;

    /**
     * messages come back here from e.g. programmatic state changes, like a new
     * aePlayer file position. This methods sets the GUI components to a
     * consistent state, using a flag to tell the slider that it has not been
     * set by a user mouse action
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (EVENT_REMEMBER_LAST_INTERFACE.equals(evt.getPropertyName())) {
            Object nv = evt.getNewValue();
            if (nv instanceof Boolean) {
                setRememberLastInterface((Boolean) nv);
            }
            return;
        }
        if (evt.getSource() instanceof HardwareInterface) {
            if (evt.getPropertyName().equals("readerStarted")) { // comes from hardware interface AEReader thread
                //            log.info("AEViewer.propertyChange: AEReader started, fixing device control menu");
                // cypress reader started, can set device control for cypress usbio reader thread
                fixDeviceControlMenuItems();
            }
        } else if (evt.getPropertyName().equals("cleared")) {
            setStatusMessage(null);
        } else if (evt.getSource() instanceof AEFileInputStreamInterface) { // Tobi 4.1.21 changed to AEFileInputStreamInterface from AEFileInputStream to handle RosbagFileInputStream too
            switch (evt.getPropertyName()) {
                case AEInputStream.EVENT_POSITION:
                    // don't pass on position on every packet since this consumes a lot of processing time in each filter
                    break;
                default:
                    getSupport().firePropertyChange(evt); // forward the event, e.g. for fileopen, etc
            }
        } else if (evt.getSource() instanceof AEPlayer) {
            getSupport().firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());  // forward/refire events from AEFileInputStream to listeners on AEViewer
        }
    }

	private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
            AEViewerAboutDialog d = new AEViewerAboutDialog(this, true);
            d.setLocationRelativeTo(this);
            d.setVisible(true);
	}//GEN-LAST:event_aboutMenuItemActionPerformed

    public void showFilters(boolean yes) {
        if (yes && !filterFrameBuilt) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                filterFrame = new FilterFrame(chip);
            } finally {
                setCursor(Cursor.getDefaultCursor());
            }
            filterFrame.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosed(WindowEvent e) {
                    //                    log.info(e.toString());
                    filtersToggleButton.setSelected(false);
                }

                @Override
                public void windowOpened(WindowEvent e) {
                    filtersToggleButton.setSelected(true);
                }
            });
            filterFrameBuilt = true;
        }

        if (filterFrame != null) {
            filterFrame.setVisible(yes);
            filterFrame.setState(Frame.NORMAL);
        }

        filtersToggleButton.setSelected(yes);
    }

    /**
     * Returns true if configuration frame for controlling biases and other
     * configuration exists and is visible
     *
     * @return true if really visible
     */
    public boolean isBiasgenVisible() {
        if (getBiasgenFrame() == null) {
            return false;
        }
        return getBiasgenFrame().isVisible();
    }

    /**
     * Shows the configuration frame. The process to show the frame occurs in
     * the background Swing thread, so the frame is not immediately visible. To
     * check for valid frame, use isBiasgenVisible().
     *
     * @param yes true to show.
     */
    public void showBiasgen(final boolean yes) {
        if (chip == null) {
            if (yes) {
                log.warning("null chip, can't try to show biasgen");
            }
            return;
        }
        Runnable r = () -> showBiasgenOnEdt(yes);
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /**
     * Shows or hides the Hardware Configuration frame; must run on the EDT.
     * When hiding, only disposes an existing frame (does not construct a new
     * one) so chip switches do not race first-use preference loading.
     */
    private void showBiasgenOnEdt(boolean yes) {
        if (chip == null) {
            return;
        }
        if (chip.getBiasgen() == null) {
            if (getBiasgenFrame() != null) {
                getBiasgenFrame().dispose();
                biasgenFrame = null;
            }
            biasgen = null;
            return;
        }
        biasesToggleButton.setEnabled(true);
        viewBiasesMenuItem.setEnabled(true);
        try {
            if (!yes) {
                if (getBiasgenFrame() != null) {
                    getBiasgenFrame().dispose();
                    biasgenFrame = null;
                }
                biasesToggleButton.setSelected(false);
                biasgen = null;
                return;
            }
            if (biasgen != chip.getBiasgen() || getBiasgenFrame() == null) {
                if (getBiasgenFrame() != null) {
                    getBiasgenFrame().dispose();
                }
                biasgenFrame = new BiasgenFrame(chip);
                biasgenFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        biasesToggleButton.setSelected(false);
                    }
                });
            }
            getBiasgenFrame().setVisible(true);
            biasesToggleButton.setSelected(true);
            biasgen = chip.getBiasgen();
        } catch (Exception e) {
            log.warning("Caught exception when trying to set up Biasgen: " + e);
        }
    }

    synchronized public void toggleRecording() {
        if ((jaerViewer != null) && jaerViewer.isSyncEnabled() && (jaerViewer.getViewers().size() > 1)) {
            jaerViewer.toggleSynchronizedRecording();
        } else if (isRecordingEnabled()) {
            stopRecording(true); // confirms filename dialog when flag true
        } else {
            startRecording();
        }
        //        if(recordingButton.isSelected()){
        //            if(caviarViewer!=null && caviarViewer.isSyncEnabled() ) caviarViewer.startSynchronizedRecording(); else startRecording();
        //        }else{
        //            if(caviarViewer!=null && caviarViewer.isSyncEnabled()) caviarViewer.stopSynchronizedRecording(); else stopRecording();
        //        }
    }

    void fixRecordingControls() {
        SwingUtilities.invokeLater(new Runnable() { // made this a runnable to run later to fix possible race problems - tobi
            @Override
            public void run() {//        System.out.println("fixing recording controls, recordingEnabled="+recordingEnabled);
                if ((getPlayMode() != PlayMode.REMOTE) && ((aemon == null) || ((aemon != null) && !aemon.isOpen())) && (getPlayMode() != PlayMode.PLAYBACK)) {
                    // we can record from live input or from playing file (e.g. after refiltering it) or we can record network data
                    // TODO: not ideal logic here, too confusing
                    recordingButton.setEnabled(false);
                    recordingMenuItem.setEnabled(false);
                    return;

                } else {
                    recordingButton.setEnabled(true);
                    recordingMenuItem.setEnabled(true);
                }

                if (!isRecordingEnabled() && (getPlayMode() == PlayMode.PLAYBACK)) {
                    recordingButton.setText("Start re-recording");
                    recordingMenuItem.setText("Start re-recording data");
                } else if (isRecordingEnabled()) {
                    recordingButton.setText("Stop recording");
                    recordingButton.setSelected(true);
                    recordingMenuItem.setText("Stop recording data");
                } else {
                    recordingButton.setText("Start recording");
                    recordingButton.setSelected(false);
                    recordingMenuItem.setText("Start recording data");
                }
            }
        });

    }

    public void openRecordingFolderWindow() {
        String osName = System.getProperty("os.name");
        if (osName == null) {
            log.warning("no OS name property, cannot open browser");
            return;

        }

        String curDir = System.getProperty("user.dir");
        //        log.info("opening folder window for folder "+curDir);
        if (osName.startsWith("Windows")) {
            try {
                Runtime.getRuntime().exec(new String[]{"explorer.exe", curDir});
            } catch (IOException e) {
                log.warning(e.getMessage());
            }

        } else if (System.getProperty("os.name").contains("Linux")) {
            log.warning("cannot open linux folder browsing window");
        }

    }

    /**
     * Decide the recording-stream format for a requested data-file version and
     * filename, and the effective filename to hand to the writer (appending the
     * format's extension when the filename has none). Package-private static so
     * the headless probe can verify the version/extension/writer selection
     * without constructing a full {@link AEViewer} (a JFrame, which needs a
     * display).
     *
     * @param filename the requested recording filename
     * @param dataFileVersionNum the requested data-file version ("2.0", "4.0" or the "aedz" sentinel)
     * @return the resolved format version and effective filename
     */
    static RecordingFormatChoice resolveRecordingFormat(String filename, String dataFileVersionNum) {
        boolean aedz = AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals(dataFileVersionNum)
                || filename.toLowerCase().endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDZ);
        boolean aedat4 = !aedz && (AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(dataFileVersionNum)
                || filename.toLowerCase().endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4));
        String version;
        if (aedz) {
            version = AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ;
        } else if (aedat4) {
            version = AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4;
        } else {
            version = AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2;
        }
        String effective = filename;
        if (!AEDataFile.hasDataFileExtension(filename)) {
            String extension = AEDataFile.extensionForVersion(version);
            effective = filename + extension;
        }
        return new RecordingFormatChoice(version, effective);
    }

    /**
     * Immutable resolution result from {@link #resolveRecordingFormat(String, String)}:
     * the format version to write and the effective filename (with any appended
     * extension).
     */
    static final class RecordingFormatChoice {

        final String version;
        final String filename;

        RecordingFormatChoice(String version, String filename) {
            this.version = version;
            this.filename = filename;
        }
    }

    /**
     * Starts recording AE data to a file.
     *
     * @param filename the filename to record to, including all path information.
     * Filenames without path are recorded to the startup folder. If there is no
     * extension, appends {@code .aedat4} for AEDAT-4 or {@code .aedat2} for
     * AEDAT-2 (legacy {@code .aedat}/{@code .dat} still accepted if supplied).
     *
     * @param dataFileVersionNum the version number string, e.g. "2.0", "3.0",
     * or "3.1". ("2.0" is standard AEDAT file format for pre-caer records and
     * is most stable))
     *
     * @return the file that is recorded to.
     */
    synchronized public File startRecording(String filename, String dataFileVersionNum) {
        if (filename == null) {
            log.warning("tried to record to null filename, aborting");
            return null;
        }

        if (recordingEnabled && recordingFile != null) {
            log.warning(String.format("Already recording to file %s", recordingFile.getAbsolutePath()));
            return recordingFile;
        }
        RecordingFormatChoice choice = resolveRecordingFormat(filename, dataFileVersionNum);
        boolean aedz = AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals(choice.version);
        boolean aedat4 = !aedz && AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(choice.version);
        if (!choice.filename.equals(filename)) {
            log.info("Appended extension " + AEDataFile.extensionForVersion(choice.version)
                    + " to make filename=" + choice.filename);
        }
        filename = choice.filename;
        OpenedRecordingStream opened = null;
        Closeable writer = null;
        try {
            recordingFile = new File(filename);
            // Freeze the configuration once at recording start; the same immutable
            // snapshot is handed to the AEDAT-4 writer and placed on the chip so the
            // legacy writer and readers use the identical recording-start values.
            // The snapshot is captured and the file opened atomically by the helper,
            // which clears the chip snapshot if the open fails so a later recording
            // can never reuse stale metadata.
            if (aedat4) {
                recordingOutputStream = null;
                aedzRecordingOutputStream = null;
                opened = openWithFrozenSnapshot(chip, recordingFile);
                constructRecordingWriter(chip, opened, (stream, snapshot) -> {
                    aedat4RecordingOutputStream = new Aedat4FileOutputStream(stream, chip, getAedat4Compression(), snapshot);
                });
                writer = aedat4RecordingOutputStream;
                log.info(String.format("AEDAT-4 recording compression=%s, omitFilteredOut=%s (any filter enabled or File→Enable filtering of recorded events)",
                        net.sf.jaer.eventio.aedat4.Aedat4Compression.nameOf(getAedat4Compression()),
                        isRecordFilteredEventsEnabled()
                        || (chip.getFilterChain() != null && chip.getFilterChain().isAnyFilterEnabled())));
            } else if (aedz) {
                aedat4RecordingOutputStream = null;
                recordingOutputStream = null;
                opened = openWithFrozenSnapshot(chip, recordingFile);
                // Hand the owner-captured object explicitly to AEDZ; the writer must not
                // rediscover it through mutable chip state or recapture live preferences.
                constructRecordingWriter(chip, opened, (stream, snapshot) -> {
                    aedzRecordingOutputStream = new AEDZOutputStream(stream, chip, snapshot);
                });
                writer = aedzRecordingOutputStream;
            } else {
                aedat4RecordingOutputStream = null;
                aedzRecordingOutputStream = null;
                opened = openWithFrozenSnapshot(chip, recordingFile);
                constructRecordingWriter(chip, opened, (stream, snapshot) -> {
                    recordingOutputStream = new AEFileOutputStream(stream, chip, dataFileVersionNum); // tobi changed to 8k buffer (from 400k) because this has measurablly better performance than super large buffer
                });
                writer = recordingOutputStream;
            }
            activeRecordingSnapshot = opened.snapshot;

            if (getPlayMode() == PlayMode.PLAYBACK) { // change listener for rewind to stop recording
                getAePlayer().getAEInputStream().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        if ((evt.getSource() == getAePlayer().getAEInputStream()) && evt.getPropertyName().equals(AEInputStream.EVENT_REWOUND)) {
                            log.info("recording reached end, stopping re-recording");
                            SwingUtilities.invokeLater(new Runnable() {

                                @Override
                                public void run() {
                                    stopRecording(true);
                                }
                            });
                        }
                    }
                });
            }
            setRecordingEnabled(true);

            fixRecordingControls();

            recordingStartTime = System.currentTimeMillis();
            recordingTimeLimitOverlayText = null;
            recordingTimeLimitOverlayLastMs = 0;
            log.info("starting recording to " + recordingFile.getAbsolutePath());
            getSupport().firePropertyChange(EVENT_RECORDING_STARTED, null, recordingFile);

            //            aemon.resetTimestamps();
        } catch (FileNotFoundException e) {
            cleanupFailedRecordingStart(opened, writer, e);
            log.log(Level.WARNING, "In trying to open a recording output file, caught: " + e.toString(), e);

        } catch (IOException ioe) {
            cleanupFailedRecordingStart(opened, writer, ioe);
            log.log(Level.WARNING, "In trying to open a recording output file, caught: " + ioe.toString(), ioe);
        } catch (RuntimeException runtime) {
            // Runtime failures can occur after a writer has taken ownership (for
            // example playback listener setup or EVENT_RECORDING_STARTED listeners).
            // Preserve that failure while deterministically releasing this start.
            cleanupFailedRecordingStart(opened, writer, runtime);
            throw runtime;
        }

        return recordingFile;
    }

    /**
     * The opened raw recording stream together with the immutable recording-start
     * snapshot that was frozen (and placed on the chip) just before the file was
     * opened.
     */
    static final class OpenedRecordingStream {

        final FileOutputStream stream;
        final RecordingConfigurationSnapshot snapshot;
        private boolean ownershipTransferred;
        private boolean rawStreamClosed;

        OpenedRecordingStream(FileOutputStream stream, RecordingConfigurationSnapshot snapshot) {
            this.stream = stream;
            this.snapshot = snapshot;
        }

        synchronized void transferOwnership() {
            ownershipTransferred = true;
        }

        synchronized void closeRawStreamOnFailure(Throwable primary) {
            if (ownershipTransferred || rawStreamClosed) {
                return;
            }
            rawStreamClosed = true;
            try {
                stream.close();
            } catch (IOException | RuntimeException cleanupFailure) {
                addCleanupFailure(primary, cleanupFailure,
                        "closing raw recording stream after failed writer construction");
            }
        }
    }

    /**
     * Release a failed recording start without firing secondary property events.
     * The selected writer owns the file only after construction returns; before
     * then {@link OpenedRecordingStream} still owns the raw stream. All fields are
     * cleared in {@code finally}, even if writer close itself fails.
     */
    private void cleanupFailedRecordingStart(OpenedRecordingStream opened, Closeable writer, Throwable primary) {
        try {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException | RuntimeException cleanupFailure) {
                    addCleanupFailure(primary, cleanupFailure,
                            "closing recording writer after failed recording start");
                }
            } else if (opened != null) {
                opened.closeRawStreamOnFailure(primary);
            }
        } finally {
            recordingOutputStream = null;
            aedat4RecordingOutputStream = null;
            aedzRecordingOutputStream = null;
            recordingEnabled = false;
            recordingPaused = false;
            recordingFile = null;
            if (opened != null) {
                clearCapturedSnapshotByIdentity(chip, opened.snapshot);
                if (activeRecordingSnapshot == opened.snapshot) {
                    activeRecordingSnapshot = null;
                }
            }
        }
    }

    private static void addCleanupFailure(Throwable primary, Throwable cleanupFailure, String message) {
        if (primary != null && cleanupFailure != primary) {
            primary.addSuppressed(cleanupFailure);
        }
        log.log(Level.FINE, message, cleanupFailure);
    }

    private static void clearCapturedSnapshotByIdentity(AEChip chip,
            RecordingConfigurationSnapshot capturedSnapshot) {
        if (chip != null && chip.getRecordingConfigurationSnapshot() == capturedSnapshot) {
            chip.setRecordingConfigurationSnapshot(null);
        }
    }

    /** Release only the snapshot owned by the recording being stopped. */
    void releaseActiveRecordingSnapshot(RecordingConfigurationSnapshot snapshot) {
        if (activeRecordingSnapshot == snapshot) {
            activeRecordingSnapshot = null;
        }
        clearCapturedSnapshotByIdentity(chip, snapshot);
    }

    /**
     * Freeze the recording-start configuration snapshot, place it on the chip,
     * and open the raw recording file. The snapshot is captured BEFORE the file open
     * so the recorded header reflects recording-start values; if the open fails,
     * the chip's cached snapshot is cleared so a later recording can never reuse
     * stale metadata (regression: a failed start used to leave the stale
     * snapshot set, which the next start could then hand to a writer).
     *
     * @param chip the chip whose live configuration is frozen
     * @param file the file to open for recording
     * @return the opened stream together with its frozen snapshot
     * @throws FileNotFoundException if the file cannot be opened
     */
    static OpenedRecordingStream openWithFrozenSnapshot(AEChip chip, File file) throws FileNotFoundException {
        RecordingConfigurationSnapshot snapshot = RecordingConfigurationSnapshot.captureFromChip(chip);
        chip.setRecordingConfigurationSnapshot(snapshot);
        FileOutputStream stream;
        try {
            stream = new FileOutputStream(file);
        } catch (FileNotFoundException | RuntimeException e) {
            clearCapturedSnapshotByIdentity(chip, snapshot);
            throw e;
        }
        return new OpenedRecordingStream(stream, snapshot);
    }

    /**
     * The step that takes ownership of an already-opened raw log stream and
     * snapshot to build the recording writer around them. Thrown exceptions
     * propagate as {@link IOException} (or {@link RuntimeException}); the caller
     * owns closing the stream exactly once on the failure path.
     */
    @FunctionalInterface
    public interface RecordingWriterFactory {

        /**
         * Construct the writer around the given opened stream and recording-start
         * snapshot.
         *
         * @param stream the already-opened raw log stream (stream ownership passes
         *               to the writer only if this returns normally)
         * @param snapshot the frozen recording-start snapshot
         * @throws IOException if the writer cannot be constructed
         */
        void construct(FileOutputStream stream, RecordingConfigurationSnapshot snapshot) throws IOException;
    }

    /**
     * Open the raw log stream (freezing the recording-start snapshot onto the
     * chip), then hand it to the given writer factory. If the writer constructor
     * throws after the stream was successfully opened, the stream is closed
     * exactly once and the chip snapshot released so neither the leaked file
     * handle nor stale recording metadata survives a failed start. On a normal
     * return the writer has taken ownership of the stream and it is never closed
     * here.
     *
     * @param chip the chip whose live configuration is frozen
     * @param opened the opened stream together with its frozen snapshot
     * @param factory the writer-construction step that takes over the stream
     * @throws IOException if opening or writer construction fails
     */
    static void constructRecordingWriter(AEChip chip, OpenedRecordingStream opened, RecordingWriterFactory factory) throws IOException {
        try {
            factory.construct(opened.stream, opened.snapshot);
        } catch (IOException | RuntimeException e) {
            // The writer constructor failed before taking ownership: close the raw
            // stream exactly once and clear the frozen chip snapshot so a later
            // recording can never reuse a leaked file handle or stale metadata.
            opened.closeRawStreamOnFailure(e);
            clearCapturedSnapshotByIdentity(chip, opened.snapshot);
            throw e;
        }
        opened.transferOwnership();
        // Normal return: ownership transferred to the writer; never close here.
    }

    /**
     * Close a raw log stream, suppressing any {@link IOException} from close.
     *
     * @param stream the stream to close
     */
    static void closeQuietly(FileOutputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException e) {
            log.log(Level.FINE, "closing raw recording stream after failed writer construction", e);
        }
    }

    /**
     * Starts recording data to a default data recording file.
     *
     * @return the file that is recorded to.
     * @see #getRecordingFile()
     */
    synchronized public File startRecording() {
        //        if(playMode!=PlayMode.LIVE) return null;
        // first reset timestamps to zero time, and for stereo interfaces, to sychronize them
        /* TODO : fix so that timestamps are zeroed before recording really starts */
        //zeroTimestamps();

        // The aedat file's format user want to use in the log file.
        String dataFileVersionNum;
//        dataFileVersionNum = (String)JOptionPane.showInputDialog(this,
//        "Choose the aedat file's format", "This is a format chooser dialog",
//        JOptionPane.QUESTION_MESSAGE,null,
//        new Object[]{"2.0","3.1"},"2.0");
//        // User cancel the aedat format choosing dialog.
//        if(dataFileVersionNum == null) {
//            return null;
//        }
        dataFileVersionNum = getRecordingDataFileVersion();

        String dateString
                = AEDataFile.DATE_FORMAT.format(new Date()); // uses local time zone on this computer (must be set correctly to be able to find true local time of recording later)
        String className
                = chip.getClass().getSimpleName();
        int suffixNumber = 0;
        // TODO replace with real serial number code in devices!
        String serialNumber = "";
        if ((chip.getHardwareInterface() != null) && (chip.getHardwareInterface() instanceof USBInterface)) {
            USBInterface usb = (USBInterface) chip.getHardwareInterface();
            if ((usb.getStringDescriptors() != null) && (usb.getStringDescriptors().length == 3) && (usb.getStringDescriptors()[2] != null)) {
                serialNumber = usb.getStringDescriptors()[2];
            }
            // replace non-printable characters with X to avoid errors on windows 10 with creating such filenames.
            // this sitation can occur with early prototypes that lack serial number (i.e. serial number is integer 0)
            StringBuilder sb = new StringBuilder("-");
            for (Character c : serialNumber.toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    sb.append(c);
                } else {
                    sb.append('X');
                }
            }
            serialNumber = sb.toString();

        }
        boolean succeeded = false;
        String filename;

        do {
            // Record files to the temporary folder initially; the user may move or delete the file when recording ends.
            // Use the extension for the preferred data-file version so the selected format (e.g. AEDAT-2
            // or AEDZ) is actually honored; previously this was hardcoded to .aedat4, which made startRecording
            // route to AEDAT-4 regardless of the preference.
            filename = lastRecordingFolder + File.separator + className + "-" + dateString + serialNumber + "-" + suffixNumber + AEDataFile.extensionForVersion(dataFileVersionNum);
            File lf = new File(filename);
            if (!lf.isFile()) {
                succeeded = true;
            }

        } while ((succeeded == false) && (suffixNumber++ <= 5));
        if (succeeded == false) {
            log.warning("AEViewer.startRecording(): could not open a unique new file for recording after trying up to " + filename);
            return null;
        }

        File lf = startRecording(filename, dataFileVersionNum);
        return lf;

    }

//    /** Currently not used, since it throws up dialogs within the doInBackground thread */
//    class RecordingSaverWorker extends SwingWorker<Boolean, String> {
//
//        Component comp;
//        File srcFile, newFile;
//
//        public RecordingSaverWorker(Component comp, File srcFile) {
//            this.comp = comp;
//            this.srcFile = srcFile;
//        }
//
//        protected Boolean doInBackground() throws Exception {
//            comp.setCursor(preResizeCursor);
//            JFileChooser chooser = new JFileChooser();
//            chooser.setCurrentDirectory(lastRecordingFolder);
//            chooser.setFileFilter(new DATFileFilter());
//            chooser.setDialogTitle("Save recorded data");
//
//            String fn
//                    = recordingFile.getName();
//            //                System.out.println("fn="+fn);
//            // strip off .aedat to make it easier to appendOfEventReferences comment to filename
//            int extInd = fn.lastIndexOf(AEDataFile.DATA_FILE_EXTENSION);
//            String base = fn;
//            if (extInd > 0) {
//                base = fn.substring(0, extInd); // maybe trying to save old .dat extension
//            }
//            chooser.setSelectedFile(new File(base));
//            chooser.setDialogType(JFileChooser.SAVE_DIALOG);
//            chooser.setMultiSelectionEnabled(false);
//
//            boolean savedIt = false;
//            do {
//                // clear the text input buffer to prevent multiply typed characters from destroying proposed datetimestamped filename
//                int retValue = chooser.showSaveDialog(AEViewer.this);
//                if (retValue == JFileChooser.APPROVE_OPTION) {
//                    File newFile = chooser.getSelectedFile();
//                    // make sure filename ends with .aedat
//                    if (!newFile.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION)) {
//                        newFile = new File(newFile.getCanonicalPath() + AEDataFile.DATA_FILE_EXTENSION);
//                    }
//                    // we'll rename the recorded data file to the selection
//                    lastRecordingFolder = chooser.getCurrentDirectory();
//                    prefs.put("AEViewer.lastLoggingFolder", lastRecordingFolder.getCanonicalPath());
//
//                    boolean renamed = recordingFile.renameTo(newFile);
//                    if (renamed) {
//                        // if successful, cool, save persistence
//                        savedIt = true;
//                        recentFiles.addFile(newFile);
//                        recordingFile = newFile; // so that we play it back if it was saved and playback immediately is selected
//                        log.info("renamed recording file to " + newFile.getAbsolutePath());
//                    } else {
//                        // if this fails, it does not only mean that a file already exists,
//                        // the failure reasons are platform dependent, for example on Linux
//                        // this might fail if its a move across different file-systems, such
//                        // as from /tmp to /home depending on configuration.
//                        // so we check if the new file really exists, if it doesn't, we don't
//                        // have to delete it or ask for overwrite confirmation, just use it.
//                        if (newFile.exists()) {
//                            int overwrite = JOptionPane.showConfirmDialog(chooser, "Overwrite file \"" + newFile + "\"?", "Overwrite file?", JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
//                            if (overwrite == JOptionPane.OK_OPTION) {
//                                // we need to delete the file
//                                boolean deletedOld = newFile.delete();
//                                if (deletedOld) {
//                                    recordingFile.renameTo(newFile);
//                                    savedIt = true;
//                                    log.info("renamed recording file to " + newFile); // TODO something messed up
//                                    // here with confirmed
//                                    // overwrite of recording file
//                                    recordingFile = newFile;
//                                } else {
//                                    log.warning("couldn't delete recording file " + newFile);
//                                }
//
//                            } else {
//                                chooser.setDialogTitle("Couldn't save file there, try again");
//                            }
//                        } else {
//                            log.info(String.format("(Please wait) moving temporary file %s to final location %s", recordingFile.getAbsolutePath(), newFile.getAbsolutePath()));
//                            class Result {
//
//                                Exception exception = null;
//                            }
//                            final Result result = new Result();
//                            final File newFinalFile = new File(newFile.getAbsolutePath());
//                            setCursor(new Cursor(Cursor.WAIT_CURSOR));
//                            Thread t = new Thread() {
//                                public void run() {
//                                    try {
//                                        FileUtils.moveFile(recordingFile, newFinalFile);
//                                    } catch (IOException e) {
//                                        log.warning(String.format("could not FileUtils.moveFile(%s,%s): %s", recordingFile, newFinalFile, e.toString()));
//                                        result.exception = e;
//                                    } finally {
//                                    }
//                                }
//                            };
//
    ////                            JOptionPane.showMessageDialog(getImagePanel(), "Moving recording to final location", "Moving recording", JOptionPane.INFORMATION_MESSAGE);
//                            t.start();
//                            StringBuilder sb = new StringBuilder("Saving..");
//                            while (t.isAlive()) {
//                                try {
//                                    Thread.sleep(500);
//                                } catch (InterruptedException e) {
//                                }
//                                sb.append(".");
//                                log.info(sb.toString());
//                                publish(sb.toString());
//                            }
//                            if (result.exception == null) {
//                                log.info("done saving " + newFinalFile.getAbsolutePath());
//                                savedIt = true;
//                                recordingFile = newFile;
//                            } else {
//                                log.severe(String.format("Could not save %s: %s", newFinalFile, result.exception));
//                            }
//                            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
//                        }
//                    }
//                } else {
//                    // user hit cancel, delete recorded data
//                    boolean deleted = recordingFile.delete();
//                    if (deleted) {
//                        log.info("Deleted temporary recording file " + recordingFile);
//                    } else {
//                        log.warning("Couldn't delete temporary recording file " + recordingFile);
//                    }
//
//                    savedIt = true;
//                }
//
//            } while (savedIt == false); // keep trying until user is happy (unless they deleted some crucial data!)
//            return true;
//
//        }
//
//        protected void done() {
//            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
//            JOptionPane.showMessageDialog(comp, "Saved " + newFile.getAbsolutePath());
//        }
//
//    }
    private JButton findOkButton(Container container) {
        for (Component c : container.getComponents()) {
            log.fine("Component " + c);
        }
        for (Component c : container.getComponents()) {

            if (c instanceof AbstractButton) {
                if (((AbstractButton) c).getText().equals("OK")) {
                    log.fine("found OK button");
                    return (JButton) c;
                }
            } else if (c instanceof Container) {
                log.fine("found container " + c);
                return findOkButton((Container) c);
            } else {
                log.fine("some other component " + c);
            }
        }
        return null;
    }

    /**
     * Stops recording and optionally opens file dialog for where to save file. If
     * number of AEViewers is more than one, dialog is also skipped since we may
     * be recording from multiple viewers.
     *
     * @param confirmFilename true to show file dialog to confirm filename,
     * false to skip dialog.
     * @return chosen File
     */
    synchronized public File stopRecording(boolean confirmFilename) {
        // the file has already been recorded somewhere with a timestamped name, what this method does is
        // to move the already recorded file to a possibly different location with a new name, or if cancel is hit,
        // to delete it.
        int retValue = JFileChooser.CANCEL_OPTION;
        String fileInfo = "";
        if (isRecordingEnabled()) {
            if (recordingButton.isSelected()) {
                recordingButton.setSelected(false);
            }

            recordingButton.setText("Start recording");
            recordingMenuItem.setText("Start recording data");
            try {
                log.info("stopped recording at " + AEDataFile.DATE_FORMAT.format(new Date()) + " to file " + recordingFile);
                final boolean wasAedat4 = aedat4RecordingOutputStream != null;
                final boolean wasAedz = aedzRecordingOutputStream != null;
                final String preferredSaveExt = wasAedat4
                        ? AEDataFile.DATA_FILE_EXTENSION_AEDAT4
                        : (wasAedz
                        ? AEDataFile.DATA_FILE_EXTENSION_AEDZ
                        : AEDataFile.DATA_FILE_EXTENSION_AEDAT2);
                Object streamLock = aedat4RecordingOutputStream != null ? aedat4RecordingOutputStream
                        : (aedzRecordingOutputStream != null ? aedzRecordingOutputStream : recordingOutputStream);
                final RecordingConfigurationSnapshot stoppingSnapshot = activeRecordingSnapshot;
                try {
                    synchronized (streamLock) {
                        setRecordingEnabled(false);
                        if (aedat4RecordingOutputStream != null) {
                            aedat4RecordingOutputStream.close();
                            fileInfo = aedat4RecordingOutputStream.toString();
                            aedat4RecordingOutputStream = null;
                        } else if (aedzRecordingOutputStream != null) {
                            aedzRecordingOutputStream.close();
                            fileInfo = aedzRecordingOutputStream.toString();
                            aedzRecordingOutputStream = null;
                        } else {
                            recordingOutputStream.close();
                            fileInfo = recordingOutputStream.toString();
                        }
                    }
                } finally {
                    // Close failures must not retain stale recording metadata, and a
                    // newer owner-installed snapshot must survive this stop.
                    releaseActiveRecordingSnapshot(stoppingSnapshot);
                }
                // if jaer viewer is recording synchronized data files, then just save the file where it was recorded originally

                if (confirmFilename && !jaerViewer.isSyncEnabled()) {
                    // Pause live acquisition/rendering while the modal save UI is up so
                    // USB packets are not cooked/rendered into unbounded memory.
                    final boolean wasPausedForSaveDialog = isPaused();
                    if (!wasPausedForSaveDialog) {
                        setPaused(true);
                    }
                    try {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setCurrentDirectory(lastRecordingFolder);
                    chooser.setFileFilter(new DATFileFilter());
                    chooser.setDialogTitle("Save recorded data");

                    String fn
                            = recordingFile.getName();
                    // strip known data extension so user can append a comment in the basename
                    String base = fn;
                    String fnLower = fn.toLowerCase();
                    for (String ext : new String[]{
                        AEDataFile.DATA_FILE_EXTENSION_AEDAT4,
                        AEDataFile.DATA_FILE_EXTENSION_AEDAT2,
                        AEDataFile.DATA_FILE_EXTENSION_AEDZ,
                        AEDataFile.DATA_FILE_EXTENSION,
                        AEDataFile.OLD_DATA_FILE_EXTENSION}) {
                        if (fnLower.endsWith(ext)) {
                            base = fn.substring(0, fn.length() - ext.length());
                            break;
                        }
                    }
                    // we'll append the preferred extension back later
                    final String filenameBase = base;
                    chooser.setSelectedFile(new File(filenameBase));
                    //                chooser.setAccessory(new ResetFileButton(base,chooser));
                    chooser.setDialogType(JFileChooser.SAVE_DIALOG);
                    chooser.setMultiSelectionEnabled(false);
                    chooser.setAccessory(new RecentFoldersComboAccessory(recentFiles, chooser,
                            () -> RecordingSaveDialogGuard.restoreSelectedFilename(chooser, filenameBase)));
                    //                Component[] comps=chooser.getComponents();
                    //                for(Component c:comps){
                    //                    if(c.getName().equals("buttonPanel")){
                    //                        ((JPanel)c).appendOfEventReferences(new ResetFileButton(base,chooser));
                    //                    }
                    //                }
//                                        JPanel commentsPanel=new JPanel();
//                                        commentsPanel.setLayout(new BoxLayout(commentsPanel,BoxLayout.Y_AXIS));
//                                        JTextField tf=new JTextField("");
//                                        JLabel tfLabel=new JLabel("Optional comment");
//                                        commentsPanel.appendOfEventReferences(tfLabel);
//                                        commentsPanel.appendOfEventReferences(tf);
//                                        chooser.setAccessory(commentsPanel);

                    boolean doneSavingOrCancelling = false;
                    do {
                        retValue = RecordingSaveDialogGuard.showSaveDialog(chooser, AEViewer.this, base);
                        if (retValue == JFileChooser.APPROVE_OPTION) {
                            File selected = chooser.getSelectedFile();
                            if (selected == null || RecordingSaveDialogGuard.isStrayRecordingShortcutFilename(selected.getName())) {
                                RecordingSaveDialogGuard.restoreSelectedFilename(chooser, base);
                                chooser.setDialogTitle("Save recorded data (restored default filename)");
                                continue;
                            }
                            File newFile = resolveLoggingSaveDestination(chooser, preferredSaveExt);
                            if (newFile == null) {
                                chooser.setDialogTitle("Couldn't save file there, try again");
                                continue;
                            }
                            // persist the folder the user actually saved into
                            lastRecordingFolder = newFile.getParentFile() != null
                                    ? newFile.getParentFile() : chooser.getCurrentDirectory();
                            prefs.put("AEViewer.lastLoggingFolder", lastRecordingFolder.getCanonicalPath());

                            File saved = relocateRecordingFile(newFile);
                            if (saved != null) {
                                doneSavingOrCancelling = true;
                                recentFiles.addFile(saved);
                                recordingFile = saved;
                                showLoggingSaveConfirmation(saved, fileInfo);
                            } else {
                                chooser.setDialogTitle("Couldn't save file there, try again");
                            }
                        } else {
                            // user hit cancel, delete recorded data
                            boolean deleted = recordingFile.delete();
                            if (deleted) {
                                log.info("Deleted temporary recording file " + recordingFile);
                            } else {
                                log.warning("Couldn't delete temporary recording file " + recordingFile);
                            }

                            doneSavingOrCancelling = true;
                        }

                    } while (doneSavingOrCancelling == false); // keep trying until user is happy (unless they deleted some crucial data!)
                    } finally {
                        if (!wasPausedForSaveDialog) {
                            setPaused(false);
                            synchronized (viewLoopPauseLock) {
                                viewLoopPauseLock.notifyAll();
                            }
                        }
                    }
                }

            } catch (IOException e) {
                String msg = "In trying to save a recording output file, got exception: " + e.toString();
                JOptionPane.showMessageDialog(this, msg, "Error saving file", JOptionPane.ERROR_MESSAGE);
                log.log(Level.WARNING, msg, e);
            }

            if ((retValue == JFileChooser.APPROVE_OPTION) && isRecordingPlaybackImmediatelyEnabled()) {
                try {
                    getAePlayer().startPlayback(recordingFile); // TODO fix it with progress monitor later
                } catch (IOException e) {
                    log.log(Level.WARNING, "In trying play a file, caught: " + e.toString(), e);
                } catch (InterruptedException ex) {
                    log.info("playback interrupted");
                }

            }
            setRecordingEnabled(false);
            getSupport().firePropertyChange(EVENT_RECORDING_STOPPED, null, recordingFile);
        }

        fixRecordingControls();
        return recordingFile;
    }    // doesn't actually reset the test in the dialog'

    /**
     * Save-dialog destination: folder the user is viewing plus the selected
     * filename. {@link JFileChooser#getSelectedFile()} can keep the original
     * parent after the user changes directory, and a relative selection
     * canonicalizes to {@code user.dir} rather than the chooser folder.
     */
    private static File resolveLoggingSaveDestination(JFileChooser chooser, String preferredExt) {
        File selected = chooser.getSelectedFile();
        File dir = chooser.getCurrentDirectory();
        if (selected == null) {
            return null;
        }
        File dest = (dir != null) ? new File(dir, selected.getName()) : selected;
        if (!AEDataFile.hasDataFileExtension(dest.getName())) {
            dest = new File(dest.getPath() + preferredExt);
        }
        File abs = dest.getAbsoluteFile();
        if (dir != null && selected.getParentFile() != null && !dir.equals(selected.getParentFile())) {
            log.info(String.format("Save chooser selected %s but current directory is %s; using %s",
                    selected.getAbsolutePath(), dir.getAbsolutePath(), abs.getAbsolutePath()));
        }
        return abs;
    }

    /**
     * Moves {@link #recordingFile} to {@code dest}. Uses rename when possible;
     * copies across filesystems with a short "Moving recording" dialog that is
     * disposed when the copy finishes.
     *
     * @return dest if successful, null if the user cancelled overwrite or the
     *         move failed
     */
    private File relocateRecordingFile(File dest) {
        if (dest == null || recordingFile == null) {
            return null;
        }
        dest = dest.getAbsoluteFile();
        File src = recordingFile.getAbsoluteFile();
        try {
            if (src.getCanonicalFile().equals(dest.getCanonicalFile())) {
                log.info("recording file already at " + dest.getAbsolutePath());
                return dest;
            }
        } catch (IOException e) {
            if (src.equals(dest)) {
                return dest;
            }
        }
        if (dest.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    "Overwrite file \"" + dest + "\"?", "Overwrite file?",
                    JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            if (overwrite != JOptionPane.OK_OPTION) {
                return null;
            }
            if (!dest.delete()) {
                log.warning("couldn't delete existing file " + dest);
                return null;
            }
        }
        if (src.renameTo(dest)) {
            log.info("renamed recording file to " + dest.getAbsolutePath());
            return dest;
        }
        // renameTo fails across filesystems (e.g. /tmp to /home)
        return moveRecordingFileWithProgress(src, dest);
    }

    /**
     * Copies {@code src} to {@code dest} on a worker thread. The "Moving
     * recording" dialog is shown only while that copy runs.
     */
    private File moveRecordingFileWithProgress(File src, File dest) {
        log.info(String.format(
                "Rename failed, trying FileUtils.moveFile. Please wait, moving temporary file %s to final location %s...",
                src.getAbsolutePath(), dest.getAbsolutePath()));
        class Result {
            Exception exception = null;
        }
        final Result result = new Result();
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        Thread t = new Thread(() -> {
            try {
                FileUtils.moveFile(src, dest);
            } catch (IOException e) {
                log.warning(String.format("could not FileUtils.moveFile(%s,%s): %s", src, dest, e));
                result.exception = e;
            }
        }, "move-recording-file");
        final StringBuilder sb = new StringBuilder("Moving recording..");
        final JOptionPane pane = new JOptionPane(sb.toString(), JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
        final JDialog dialog = pane.createDialog(this, "Moving recording");
        dialog.setModal(false);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(true);
        dialog.setVisible(true);
        t.start();
        Exception failure = null;
        try {
            while (t.isAlive()) {
                try {
                    t.join(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                sb.append(".");
                pane.setMessage(sb.toString());
            }
            if (result.exception == null && dest.exists()) {
                log.info("done saving " + dest.getAbsolutePath());
                return dest;
            }
            failure = result.exception != null
                    ? result.exception
                    : new IOException("destination missing after move");
        } finally {
            dialog.dispose();
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
        String s = String.format("Could not save %s: %s", dest, failure);
        log.severe(s);
        JOptionPane.showMessageDialog(this, s, "Error saving file", JOptionPane.ERROR_MESSAGE);
        return null;
    }

    /**
     * Shared confirmation after recording or File → Save As: add to Recent
     * Files, then Show folder / Playback / OK. {@code htmlMessage} should be
     * produced by {@link ShowFolderSaveConfirmation#htmlRecordingSavedMessage}
     * or {@link ShowFolderSaveConfirmation#htmlSaveAsMessage}.
     */
    public void showSavedFileConfirmation(File savedFile, String htmlMessage) {
        if (savedFile != null && recentFiles != null) {
            recentFiles.addFile(savedFile);
        }
        final File toPlay = savedFile;
        String msg = htmlMessage != null ? htmlMessage
                : ShowFolderSaveConfirmation.htmlRecordingSavedMessage(savedFile, null);
        ShowFolderSaveConfirmation dialog = new ShowFolderSaveConfirmation(this, savedFile, msg, () -> {
            try {
                if (toPlay != null) {
                    getAePlayer().startPlayback(toPlay);
                }
            } catch (IOException e) {
                log.log(Level.WARNING, "Could not play saved file: " + e.toString(), e);
                JOptionPane.showMessageDialog(this,
                        e.getMessage() != null ? e.getMessage() : e.toString(),
                        "Could not play file", JOptionPane.ERROR_MESSAGE);
            } catch (InterruptedException ex) {
                log.info("playback interrupted");
            }
        });
        dialog.setVisible(true);
    }

    /**
     * Remember File → Save As original vs output so File/Show file info can
     * show both compression summaries while this export is the playing file.
     */
    public void rememberLastSaveAs(File outputFile, String sourceFileInfo) {
        lastSaveAsOutputFile = outputFile != null ? outputFile.getAbsoluteFile() : null;
        lastSaveAsSourceFileInfo = sourceFileInfo != null ? sourceFileInfo : "";
        if (lastSaveAsOutputFile != null) {
            try {
                prefs.put("AEViewer.lastSaveAsOutput", lastSaveAsOutputFile.getAbsolutePath());
                String src = lastSaveAsSourceFileInfo;
                if (src.length() > Preferences.MAX_VALUE_LENGTH) {
                    src = src.substring(0, Preferences.MAX_VALUE_LENGTH);
                }
                prefs.put("AEViewer.lastSaveAsSourceInfo", src);
            } catch (IllegalArgumentException e) {
                log.fine("Could not persist last Save As file info: " + e);
            }
        }
    }

    private String lastSaveAsSourceInfoFor(File playing) {
        if (playing == null) {
            return "";
        }
        File out = lastSaveAsOutputFile;
        String src = lastSaveAsSourceFileInfo;
        if (out == null) {
            String path = prefs.get("AEViewer.lastSaveAsOutput", "");
            if (path.isEmpty()) {
                return "";
            }
            out = new File(path);
            src = prefs.get("AEViewer.lastSaveAsSourceInfo", "");
        }
        if (src == null || src.isEmpty() || out == null) {
            return "";
        }
        File a = playing.getAbsoluteFile();
        File b = out.getAbsoluteFile();
        if (!a.equals(b) && !a.getPath().equalsIgnoreCase(b.getPath())) {
            return "";
        }
        return src;
    }

    /**
     * Shows the post-save confirmation dialog with Show folder / Playback / OK.
     */
    private void showLoggingSaveConfirmation(File savedFile, String fileInfo) {
        showSavedFileConfirmation(savedFile, ShowFolderSaveConfirmation.htmlRecordingSavedMessage(savedFile, fileInfo));
    }

    /**
     * Overlay detail while recording: elapsed {@code Recorded XXhYYmZZs}, plus
     * total and remaining when a time limit is set. Refreshed at most once per
     * second.
     *
     * @return overlay lines, or {@code null} when not recording or overlay is off
     */
    public String getRecordingTimeLimitOverlayText() {
        if (!isShowRecordingOverlay() || !isRecordingEnabled()) {
            recordingTimeLimitOverlayText = null;
            return null;
        }
        long now = System.currentTimeMillis();
        if (recordingTimeLimitOverlayText != null && (now - recordingTimeLimitOverlayLastMs) < 1000) {
            return recordingTimeLimitOverlayText;
        }
        recordingTimeLimitOverlayLastMs = now;
        long elapsedMs = Math.max(0L, now - recordingStartTime);
        StringBuilder sb = new StringBuilder("Recorded ");
        sb.append(formatRecordingDurationHms(elapsedMs));
        if (recordingTimeLimit > 0) {
            long remainingMs = Math.max(0L, recordingTimeLimit - elapsedMs);
            sb.append('\n').append(formatRecordingDurationHms(recordingTimeLimit)).append(" total");
            sb.append('\n').append(formatRecordingDurationHms(remainingMs)).append(" left to record");
        }
        recordingTimeLimitOverlayText = sb.toString();
        return recordingTimeLimitOverlayText;
    }

    /**
     * Formats a duration as {@code XXhYYmZZs} (hours, minutes, seconds).
     *
     * @param durationMs duration in milliseconds
     * @return padded hours, minutes, and seconds
     */
    private static String formatRecordingDurationHms(long durationMs) {
        long totalSec = Math.max(0L, durationMs) / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        return String.format("%02dh%02dm%02ds", h, m, s);
    }

    /**
     * Returns true if currently recording data to file
     *
     * @return the recordingEnabled
     */
    public boolean isRecordingEnabled() {
        return recordingEnabled;
    }

    /**
     * Disables recording if it is enabled. Set true when recording is started.
     * Users can disable during recording. Has no effect if recording is not
     * started.
     *
     * @param recordingEnabled the recordingEnabled to set
     */
    private void setRecordingEnabled(boolean recordingEnabled) {
        this.recordingEnabled = recordingEnabled;
        if (!recordingEnabled) {
            recordingTimeLimitOverlayText = null;
        }
    }

    /**
     * Returns true if recording is paused.
     *
     * @return the recordingPaused
     */
    public boolean isRecordingPaused() {
        return recordingPaused;
    }

    /**
     * Pauses recording data if it is enabled. Users can disable before starting
     * or during recording. Has no effect if recording is not started.
     *
     * @param recordingPaused the recordingEnabled to set
     */
    private void setRecordingPaused(boolean recordingPaused) {
        this.recordingPaused = recordingPaused;
    }

    class ResetFileButton extends JButton {

        String fn;

        ResetFileButton(final String fn, final JFileChooser chooser) {
            this.fn = fn;
            setText("Reset filename");
            addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    System.out.println("reset file");
                    chooser.setSelectedFile(new File(fn));
                }
            });
        }
    }

    @Override
    public String toString() {
        return getTitle();
    }

    /**
     * Utility method to return a URL to a file in the installation.
     *
     * @param path relative to root of installation, e.g.
     * "/doc/USBAERmini2userguide.pdf"
     * @return the URL string pointing to the local file
     * @see #addHelpURLItem(java.lang.String, java.lang.String,
     * java.lang.String)
     * @throws MalformedURLException if there is something wrong with the URL
     */
//    public String pathToURL(String path) throws MalformedURLException, URISyntaxException {
//        String curDir = System.getProperty("user.dir");
//        File f = new File(curDir);
//        File pf = f.getParentFile().getParentFile();
//        String urlString = "file://" + pf.getPath() + path;
//        URL url = new URI(urlString).toURL();
//        return url.toString();
//    }
    /**
     * Adds item above separator/about
     *
     * @param menuItem item to appendCopyOfEventReferences
     * @see #removeHelpItem(javax.swing.JMenuItem)
     * @see #addHelpURLItem(java.lang.String, java.lang.String,
     * java.lang.String)
     * @return the component that you added, for later removal
     */
    public JComponent addHelpItem(JComponent menuItem) {
        int n = helpMenu.getItemCount();
        final int NUM_STATIC_HELP_ITEMS = 6;
        if (n <= NUM_STATIC_HELP_ITEMS) { // TODO NOTE adjust when adding new helpMenu items
            n = 0;
        } else {
            n = n - NUM_STATIC_HELP_ITEMS;
        }
        helpMenu.add(menuItem, n);
        return menuItem;
    }

    /**
     * Registers a new item in the Help menu.
     *
     * @param url for the item to be opened in the browser, e.g.
     * pathToURL("docs/board.pdf"), or "http://jaerproject.net/".
     * @param title the menu item title
     * @param tooltip useful tip about help
     * @return the menu item - useful for removing the help item.
     * @see #removeHelpItem(javax.swing.JMenuItem)
     */
    final public JComponent addHelpURLItem(final String url, String title, String tooltip) {
        JMenuItem menuItem = makeHelpURLMenuItem(url, title, tooltip);
        addHelpItem(menuItem);
        return menuItem;
    }

    /** Builds a Help URL menu item without inserting it into the Help menu. */
    private JMenuItem makeHelpURLMenuItem(final String url, String title, String tooltip) {
        JMenuItem menuItem = new JMenuItem(title);
        menuItem.setToolTipText(tooltip);
        menuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showInBrowser(url);
            }
        });
        return menuItem;
    }

    private void showInBrowser(String url) {
        if (!Desktop.isDesktopSupported()) {
            log.warning("No Desktop support, can't show help from " + url);
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            log.log(Level.WARNING, "Couldn't show " + url + "; caught " + ex, ex);
        }
    }

    /**
     * Unregisters an item from the Help menu.
     *
     * @param m the menu item originally returns from addHelpURLItem or
     * addHelpItem.
     * @see #addHelpURLItem(java.lang.String, java.lang.String,
     * java.lang.String)
     * @see #addHelpItem(javax.swing.JMenuItem)
     */
    final public void removeHelpItem(JComponent m) {
        if (m == null) {
            return;
        }
        helpMenu.remove(m);
    }

    /**
     * PropertyChangeSupport for events like file opening, file rewind, etc.
     *
     * @return the support
     * @see AEViewer#EVENT_FILEOPEN etc
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

    /**
     * Default port number for remote control of this AEViewer.
     * Overridden by {@link RemoteControl#getViewerPortPref()}.
     */
    public final int REMOTE_CONTROL_PORT = RemoteControl.PORT_DEFAULT_VIEWER;

    /**
     * UDP remote control for this viewer, or null if disabled or bind failed.
     */
    public RemoteControl getRemoteControl() {
        return remoteControl;
    }

    /**
     * HTML for the shared help frame: usage plus live command lists when this
     * session has listeners.
     */
    public String getRemoteControlHelpHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<p>Remote control accepts UDP text commands (for example ");
        sb.append("<code>nc -u localhost PORT</code> on Linux, or <code>nc.exe -u localhost PORT</code> on Windows). ");
        sb.append("Send <code>help</code> on that socket for the command list.</p>");
        sb.append("<p>There are two listeners when the feature is enabled:</p><ul>");
        sb.append("<li><b>AEViewer</b> — recording, playback, pause, open file, zero timestamps.</li>");
        sb.append("<li><b>AEChip</b> — chip-specific settings such as biases and Davis exposure, APS/DVS/IMU enables.</li>");
        sb.append("</ul>");
        RemoteControl viewerRc = remoteControl;
        AEChip chip = getChip();
        RemoteControl chipRc = chip != null ? chip.getRemoteControl() : null;
        if (viewerRc == null && chipRc == null) {
            sb.append("<p>This session has remote control off, so no commands are registered yet. ");
            sb.append("Enable the option, set the ports to match your controller, then restart jAER.</p>");
        } else {
            if (viewerRc != null) {
                sb.append("<h3>AEViewer (").append(escRemoteHelp(viewerRc.toString())).append(")</h3><pre>");
                sb.append(escRemoteHelp(viewerRc.getHelp()));
                sb.append("</pre>");
            }
            if (chipRc != null) {
                String chipName = chip.getClass().getSimpleName();
                sb.append("<h3>").append(escRemoteHelp(chipName)).append(" (").append(escRemoteHelp(chipRc.toString())).append(")</h3><pre>");
                sb.append(escRemoteHelp(chipRc.getHelp()));
                sb.append("</pre>");
            } else {
                sb.append("<p>The current AEChip is not listening on a UDP port.</p>");
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escRemoteHelp(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Returns the frame for configurating chip. Could be null until user
     * chooses to build it.
     *
     * @return the frame.
     */
    public BiasgenFrame getBiasgenFrame() {
        return biasgenFrame;
    }

    /**
     * Returns the frame holding the event filters. Could be null until user
     * builds it.
     *
     * @return the frame.
     */
    public FilterFrame getFilterFrame() {
        return filterFrame;
    }

    /**
     * Call this method to break the ViewLoop out of a sleep wait, e.g. to force
     * re-rendering of the data.
     */
    public void interruptViewloop() {
//        log.info("interrupting ViewLoop");
        viewLoop.interrupt(); // to break it out of blocking operation such as wait on cyclic barrier or socket
    }

    private void onLibUsbHotplug(boolean arrived, int vid, int pid) {
        log.info(String.format("USB hotplug %s %04x:%04x; scanning for cameras",
                arrived ? "add" : "remove", vid, pid));
        log.fine("onLibUsbHotplug playMode=" + getPlayMode() + " nullInterface=" + nullInterface
                + " " + UsbLog.t());
        lastUsbHotplugTimeMs = System.currentTimeMillis();
        lastInterfaceCheckTime = 0;
        if (SessionCameraOpenCoordinator.isUiRestore()) {
            return;
        }
        if (arrived) {
            // Do not clear nullInterface on every viewer (Interface-menu thrash).
            // Session serial / an explicit grant may open; otherwise overlay only.
            if (SessionCameraOpenCoordinator.mayOpenUsb(this) && nullInterface) {
                log.info("USB hotplug add: clearing nullInterface for " + getViewerWindowLabel());
                nullInterface = false;
            }
            if (getPlayMode() == PlayMode.WAITING) {
                SwingUtilities.invokeLater(this::showWelcomeOverlay);
            }
            if (viewLoop != null && SessionCameraOpenCoordinator.mayOpenUsb(this)
                    && getPlayMode() == PlayMode.WAITING) {
                interruptViewloop();
            }
            return;
        } else {
            boolean unbound = false;
            HardwareInterface hw = (chip != null) ? chip.getHardwareInterface() : null;
            if (hw != null) {
                UsbIds.Pair ids = UsbIds.peek(hw);
                boolean vidPidMatch = ids.isKnown()
                        && ((ids.vid & 0xffff) == vid) && ((ids.pid & 0xffff) == pid);
                if (vidPidMatch || (getPlayMode() == PlayMode.LIVE && !hw.isOpen())) {
                    log.info("USB hotplug remove: unbinding " + hw);
                    nullifyHardware();
                    unbound = true;
                }
            }
            if (unbound && viewLoop != null) {
                interruptViewloop();
            }
        }
    }

    /**
     * True while {@link #showOpeningCameraOverlay} is active (USB open /
     * {@code dvxConfig} still running). Welcome stays up even though
     * {@code isOpen()} is already true after the 1 ms libusb open.
     */
    public boolean isCameraOpenInProgress() {
        return pendingOpeningCameraLabel != null;
    }

    /** True when open/close failed because the USB device has already left the bus. */
    private static boolean isUsbDeviceGone(Throwable t) {
        if (UsbTransferSubmit.isUnrecoverableSubmitFailure(t)) {
            return true;
        }
        for (; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) {
                continue;
            }
            if (m.contains("devicePointer") || m.contains("LIBUSB_ERROR_NO_DEVICE")
                    || m.contains("NO_DEVICE") || m.contains("not initialized")
                    || m.contains("LIBUSB_ERROR_NOT_FOUND")
                    || m.contains("LIBUSB_ERROR_IO") || m.contains("LIBUSB_ERROR_PIPE")) {
                return true;
            }
        }
        return false;
    }

    /** Stop USB/live reader before joining ViewLoop so shutdown does not fill AE buffers. */
    private void stopLiveAcquisitionForExit() {
        if (aemon == null || !aemon.isOpen()) {
            return;
        }
        try {
            if (aemon.isEventAcquisitionEnabled()) {
                log.info("stopping live event acquisition before ViewLoop exit join");
                aemon.setEventAcquisitionEnabled(false);
            }
        } catch (HardwareInterfaceException e) {
            log.warning("error stopping live acquisition on exit: " + e.getMessage());
        }
    }

    /**
     * Starts a daemon watchdog <em>before</em> orderly shutdown work that may
     * block the EDT (USB close, ViewLoop join). A try/finally on the EDT cannot
     * recover from that hang; this thread can still call {@link System#exit}
     * and, if shutdown hooks also hang, {@link Runtime#halt}.
     * <p>
     * Does <b>not</b> help if the EDT is already deadlocked before the user
     * clicks close — {@code windowClosing} never runs, so the watchdog is never
     * armed.
     */
    private void armExitWatchdog() {
        if (!exitWatchdogArmed.compareAndSet(false, true)) {
            return;
        }
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(EXIT_WATCHDOG_MS);
            } catch (InterruptedException e) {
                return;
            }
            log.severe(String.format(
                    "Orderly AEViewer shutdown did not finish within %d ms (EDT blocked, USB hang, or deadlock); calling System.exit(1)",
                    EXIT_WATCHDOG_MS));
            Thread haltThread = new Thread(() -> {
                try {
                    Thread.sleep(EXIT_HALT_AFTER_EXIT_MS);
                } catch (InterruptedException e) {
                    return;
                }
                // Last resort: skip shutdown hooks if System.exit itself hung.
                System.err.println("AEViewer: System.exit hung in shutdown hooks; Runtime.halt(1)");
                System.err.flush();
                Runtime.getRuntime().halt(1);
            }, "AEViewer-ExitHalt");
            haltThread.setDaemon(true);
            haltThread.start();
            System.exit(1);
        }, "AEViewer-ExitWatchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        log.info(String.format(
                "Armed AEViewer exit watchdog (%d ms -> System.exit(1), then %d ms -> Runtime.halt(1))",
                EXIT_WATCHDOG_MS, EXIT_HALT_AFTER_EXIT_MS));
    }

    /**
     * WIP experimental: stop ViewLoop and wait briefly so JVM shutdown is not
     * blocked by this non-daemon thread stuck in wait/sleep/USB/JOGL.
     */
    private void stopViewLoopForExit() {
        if (viewLoop == null) {
            return;
        }
        viewLoop.stopThread();
        stopLiveAcquisitionForExit();
        interruptViewloop();
        synchronized (viewLoopPauseLock) {
            viewLoopPauseLock.notifyAll();
        }
        if (!viewLoop.isAlive()) {
            log.info("AEViewer.ViewLoop already exited before shutdown");
            return;
        }
        try {
            viewLoop.join(VIEWLOOP_EXIT_JOIN_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warning("interrupted while waiting for AEViewer.ViewLoop exit");
        }
        if (viewLoop.isAlive()) {
            log.warning(String.format(
                    "AEViewer.ViewLoop still alive after %d ms; JVM exit may hang",
                    VIEWLOOP_EXIT_JOIN_TIMEOUT_MS));
        } else {
            log.info("AEViewer.ViewLoop exited before shutdown");
        }
    }

    /**
     * Stores the preferred (startup) AEChip class for the viewer.
     *
     * @param clazz the class.
     */
    public void setPreferredAEChipClass(Class clazz) {
        prefs.put("AEViewer.aeChipClassName", clazz.getName());
    }

    /**
     * Processes remote control commands for this AEViewer. A list of commands
     * can be obtained from a remote host by sending ? or help. The port number
     * is logged to the console on startup.
     *
     * @param command the parsed command (first token)
     * @param line the line sent from the remote host.
     * @return confirmation of command.
     */
    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String line) {
        String[] tokens = line.split("\\s");
        log.finer("got command " + command + " with line=\"" + line + "\"");
        try {
            if (command.getCmdName().equals(REMOTE_START_RECORDING)
                    || command.getCmdName().equals(REMOTE_START_LOGGING)) {
                if (tokens.length < 2) {
                    return "not enough arguments\n";
                }
                String filename = line.substring(command.getCmdName().length() + 1);
                // TODO: ask user to choose the data format they want to use.
                File f = startRecording(filename, getRecordingDataFileVersion());
                if (f == null) {
                    return "Couldn't start recording to filename=" + filename + ", startrecording returned " + f + "\n";
                } else {
                    return "starting recording to " + f.getAbsoluteFile() + "\n";
                }
            } else if (command.getCmdName().equals(REMOTE_STOP_RECORDING)
                    || command.getCmdName().equals(REMOTE_STOP_LOGGING)) {
                File f = stopRecording(false); // don't confirm filename
                return "stopped recording to file " + f.getAbsolutePath() + "\n";
            } else if (command.getCmdName().equals(REMOTE_TOGGLE_SYNCHRONIZED_RECORDING)
                    || command.getCmdName().equals(REMOTE_TOGGLE_SYNCHRONIZED_LOGGING)) {
                if ((jaerViewer != null) && jaerViewer.isSyncEnabled() && (jaerViewer.getViewers().size() > 1)) {
                    jaerViewer.toggleSynchronizedRecording();
                    return "toggled synchronized recording\n";
                } else {
                    return "couldn't toggle synchronized recording because there is only 1 viewer or sync is disbled";
                }
            } else if (command.getCmdName().equals(REMOTE_ZERO_TIMESTAMPS)) {
                jaerViewer.zeroTimestamps();
            } else if (command.getCmdName().equals(REMOTE_OPEN_FILE)) {
                if (tokens.length < 2) {
                    return "not enough arguments, need file to open\n";
                }
                String filename = line.substring(REMOTE_OPEN_FILE.length() + 1);
                try {
                    openAedatInputFile(new File(filename));
                    return String.format("Opened file %s\n", filename);
                } catch (Exception e) {
                    return String.format("Could not open file %s, caught exception %s\n", filename, e.toString());
                }
            } else if (command.getCmdName().equals(REMOTE_PAUSE)) {
                setPaused(true);
                return String.format("Paused viewer\n");
            } else if (command.getCmdName().equals(REMOTE_PLAY)) {
                setPaused(false);
                return String.format("Started viewer\n");
            } else if (command.getCmdName().equals(REMOTE_REWIND)) {
                if (getAePlayer() != null) {
                    getAePlayer().rewind();
                    return String.format("Rewound playback\n");
                } else {
                    return String.format("No file is playing, cannot rewind\n");
                }
            } else if (command.getCmdName().equals(REMOTE_SET_MARK_IN)) {
                if (getAePlayer() != null) {
                    return String.format("Setting mark is not yet supported\n");
                } else {
                    return String.format("No file is playing, cannot set mark\n");
                }
            } else if (command.getCmdName().equals(REMOTE_SET_MARK_OUT)) {
                if (getAePlayer() != null) {
                    return String.format("Setting mark is not yet supported\n");
                } else {
                    return String.format("No file is playing, cannot set mark\n");
                }
            }
        } catch (Exception e) {
            return e.toString() + "\n";
        }
        return null;
    }

    /**
     * @return the playerControls
     */
    public AePlayerAdvancedControlsPanel getPlayerControls() {
        return playerControls;
    }

    /**
     * @param playerControls the playerControls to set
     */
    public void setPlayerControls(AePlayerAdvancedControlsPanel playerControls) {
        this.playerControls = playerControls;
    }

    /**
     * @return the frameRater
     */
    public FrameRater getFrameRater() {
        return frameRater;
    }

    /**
     * @return the aeFileInputStreamTimestampResetBitmask
     */
    public int getAeFileInputStreamTimestampResetBitmask() {
        return aeFileInputStreamTimestampResetBitmask;
    }

    /**
     * Sets the timestamp reset bitmask used when opening AE input streams and
     * updates the File menu item label.
     */
    public void setAeFileInputStreamTimestampResetBitmask(int aeFileInputStreamTimestampResetBitmask) {
        this.aeFileInputStreamTimestampResetBitmask = aeFileInputStreamTimestampResetBitmask;
        prefs.putInt("AEViewer.aeFileInputStreamTimestampResetBitmask", aeFileInputStreamTimestampResetBitmask);
        log.info("set aeFileInputStreamTimestampResetBitmask=" + HexString.toString(aeFileInputStreamTimestampResetBitmask));
        if (timestampResetBitmaskMenuItem != null) {
            timestampResetBitmaskMenuItem.setText("Set timestamp reset bitmask... (currently 0x" + Integer.toHexString(aeFileInputStreamTimestampResetBitmask) + ")");
        }
    }

    public boolean isCheckNonMonotonicTimeExceptionsEnabled() {
        return checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem != null
                && checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.isSelected();
    }

    /**
     * Enables/disables non-monotonic timestamp checks and syncs the File menu
     * checkbox and related input stream / hardware state.
     */
    public void setCheckNonMonotonicTimeExceptionsEnabled(boolean enabled) {
        if (checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem != null) {
            checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.setSelected(enabled);
        }
        if (aePlayer != null) {
            aePlayer.setNonMonotonicTimeExceptionsChecked(enabled);
        }
        prefs.putBoolean("AEViewer.checkNonMonotonicTimeExceptionsEnabled", enabled);
        if ((aemon != null) && (aemon instanceof StereoPairHardwareInterface)) {
            ((StereoPairHardwareInterface) aemon).setIgnoreTimestampNonmonotonicity(enabled);
        }
        getSupport().firePropertyChange(EVENT_CHECK_NONMONOTONIC_TIMESTAMPS, null, enabled);
    }

    public boolean isEnableFiltersOnStartup() {
        return enableFiltersOnStartup;
    }

    public void setEnableFiltersOnStartup(boolean enableFiltersOnStartup) {
        this.enableFiltersOnStartup = enableFiltersOnStartup;
        prefs.putBoolean("AEViewer.enableFiltersOnStartup", enableFiltersOnStartup);
        if (enableFiltersOnStartupCheckBoxMenuItem != null) {
            enableFiltersOnStartupCheckBoxMenuItem.setSelected(enableFiltersOnStartup);
        }
    }

    /** HTML help dialog font family (quick help and filter help). */
    public HtmlHelpStyle.HelpFontFamily getHelpFontFamily() {
        return HtmlHelpStyle.getFamily();
    }

    public void setHelpFontFamily(HtmlHelpStyle.HelpFontFamily helpFontFamily) {
        HtmlHelpStyle.setFamily(helpFontFamily);
    }

    /** HTML help dialog body size in points (8–14). */
    public int getHelpFontSize() {
        return HtmlHelpStyle.getSizePt();
    }

    public void setHelpFontSize(int helpFontSize) {
        HtmlHelpStyle.setSizePt(helpFontSize);
    }

    /**
     * Updates the View menu border-space item label after the value changes.
     */
    public void updateBorderSpaceMenuItemText(int borderSpacePixels) {
        if (setBorderSpaceMenuItem != null) {
            setBorderSpaceMenuItem.setText(String.format("Set border space (currently %d)", borderSpacePixels));
        }
    }

    /**
     * Updates the Playback menu jog-count item label after the value changes.
     */
    public void updateJogPacketCountMenuItemText() {
        if (setJogNCount != null && getAePlayer() != null) {
            setJogNCount.setText("Set forward/rewind N... (currently " + getAePlayer().getJogPacketCount() + ")");
        }
    }

    /**
     * @return the checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem
     */
    public javax.swing.JCheckBoxMenuItem getCheckNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem() {
        return checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem;
    }

    /**
     * Returns an ArrayBlockingQueue that may be associated with this viewer;
     * used for inter-viewer communication.
     *
     * @return the blockingQueueInput
     */
    public ArrayBlockingQueue getBlockingQueueInput() {
        return blockingQueueInput;
    }

    /**
     * Another viewer in this JVM that has BlockingQueue input enabled, or null.
     */
    private AEViewer findBlockingQueueInputViewer() {
        if (jaerViewer == null) {
            return null;
        }
        for (AEViewer v : jaerViewer.getViewers()) {
            if ((v != this) && (v.getBlockingQueueInput() != null)) {
                return v;
            }
        }
        return null;
    }

    private void offerBlockingQueuePacket(AEPacketRaw src) {
        if ((blockingQueueOutput == null) || (src == null) || (src.getNumEvents() == 0)) {
            return;
        }
        int n = src.getNumEvents();
        int[] a = new int[n];
        int[] t = new int[n];
        System.arraycopy(src.getAddresses(), 0, a, 0, n);
        System.arraycopy(src.getTimestamps(), 0, t, 0, n);
        AEPacketRaw copy = new AEPacketRaw(a, t);
        if (!blockingQueueOutput.offer(copy)) {
            blockingQueueOutput.poll();
            blockingQueueOutput.offer(copy);
        }
    }

    private void closeUnicastInput() {
        if (unicastInput != null) {
            unicastInput.close();
            removePropertyChangeListener(unicastInput);
            log.info("closed " + unicastInput);
            openUnicastInputMenuItem.setText("Open unicast UDP input...");
            unicastInput = null;
        }
        unicastInputEnabled = false;
    }

    /**
     * Returns the main viewer image display panel where the ChipCanvas is
     * shown. DisplayMethod's can use this getter to appendCopy their own
     * display controls.
     *
     * @return the imagePanel
     */
    public javax.swing.JPanel getImagePanel() {
        return imagePanel;
    }

    /**
     * @return the aeChipClassName
     */
    public String getAeChipClassName() {
        return aeChipClassName;
    }

    /**
     * @param aeChipClassName the aeChipClassName to set
     */
    public void setAeChipClassName(String aeChipClassName) {
        this.aeChipClassName = aeChipClassName;
    }


	private void monSeqOpMode0ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_monSeqOpMode0ActionPerformed
            if (aemon instanceof CypressFX2MonitorSequencer) {
                CypressFX2MonitorSequencer fx = (CypressFX2MonitorSequencer) aemon;
                try {
                    fx.setOperationMode(0);
                    JOptionPane.showMessageDialog(this, "Timestamp tick set to " + fx.getOperationMode() + " us.");
                } catch (Exception e) {
                    log.log(Level.WARNING, "In trying set sequncer operation mode: " + e.toString(), e);
                    aemon.close();
                }

            }
	}//GEN-LAST:event_monSeqOpMode0ActionPerformed

	private void monSeqOpMode1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_monSeqOpMode1ActionPerformed
            if (aemon instanceof CypressFX2MonitorSequencer) {
                CypressFX2MonitorSequencer fx = (CypressFX2MonitorSequencer) aemon;
                try {
                    fx.setOperationMode(1);
                    JOptionPane.showMessageDialog(this, "Timestamp tick set to " + fx.getOperationMode() + " us. Note that jAER will treat the ticks as 1us anyway.");
                } catch (Exception e) {
                    log.log(Level.WARNING, "In trying sequence, caught: " + e.toString(), e);
                    aemon.close();
                }
            }
	}//GEN-LAST:event_monSeqOpMode1ActionPerformed

	private void enableMissedEventsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableMissedEventsCheckBoxActionPerformed
            if (aemon instanceof CypressFX2MonitorSequencer) {
                CypressFX2MonitorSequencer fx = (CypressFX2MonitorSequencer) aemon;
                try {
                    fx.enableMissedEvents(enableMissedEventsCheckBox.getState());
                    // JOptionPane.showMessageDialog(this, "Timestamp tick set to " + fx.getOperationMode() + " us. Note that jAER will treat the ticks as 1us anyway.");
                } catch (Exception e) {
                    log.log(Level.WARNING, "In trying enable missed events count: " + e.toString(), e);
                    aemon.close();
                }

            }
	}//GEN-LAST:event_enableMissedEventsCheckBoxActionPerformed

	private void refreshInterfaceMenuItemComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_refreshInterfaceMenuItemComponentShown
            // TODO not used apparently
	}//GEN-LAST:event_refreshInterfaceMenuItemComponentShown

	private void zoomInMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInMenuItemActionPerformed
            chip.getCanvas().zoomInAround(null);
            if (isPaused())
                interruptViewloop();
	}//GEN-LAST:event_zoomInMenuItemActionPerformed

	private void zoomOutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutMenuItemActionPerformed
            chip.getCanvas().zoomOutAround(null);
            if (isPaused())
                interruptViewloop();
	}//GEN-LAST:event_zoomOutMenuItemActionPerformed

	private void showConsoleOutputButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showConsoleOutputButtonActionPerformed
            //    log.info("opening logging output window");
            //    jaerViewer.globalDataViewer.setVisible(!jaerViewer.globalDataViewer.isVisible());
            loggingHandler.getConsoleWindow().setVisible(!loggingHandler.getConsoleWindow().isVisible());
	}//GEN-LAST:event_showConsoleOutputButtonActionPerformed

	private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
            if ((biasgenFrame != null) && !biasgenFrame.isModificationsSaved()) {
                return;
            }
            if (!isXAcceleratorActivation(evt)) {
                doExitAllViewers();
                return;
            }

            final int viewerCount = getOpenViewerCount();
            final boolean multiple = viewerCount > 1;

            if (multiple && !isExitCompletelyWithXChosen()) {
                int choice = offerFirstTimeXExitChoice(viewerCount);
                if (choice == 0) {
                    setExitCompletelyWithX(true);
                    doExitAllViewers();
                } else if (choice == 1) {
                    setExitCompletelyWithX(false);
                    closeThisViewerOnly(true);
                }
                return;
            }

            if (multiple && !isExitCompletelyWithX()) {
                closeThisViewerOnly(true);
                return;
            }

            if (multiple) {
                if (!confirmExitClosesAllViewers(viewerCount)) {
                    return;
                }
                doExitAllViewers();
                return;
            }

            if (!confirmExitFromWindowClose()) {
                return;
            }
            doExitAllViewers();
	}//GEN-LAST:event_exitMenuItemActionPerformed

    /**
     * Shows or hides the nonmodal Quick help / Shortcuts window (Help menu / F1).
     */
    public void toggleQuickHelp() {
        if (quickHelpFrame != null && quickHelpFrame.isDisplayable() && quickHelpFrame.isVisible()) {
            quickHelpFrame.setVisible(false);
            return;
        }
        if (quickHelpFrame == null || !quickHelpFrame.isDisplayable()) {
            quickHelpFrame = new AEViewerQuickHelpFrame(this);
        }
        quickHelpFrame.setVisible(true);
        quickHelpFrame.toFront();
    }

	private void preferencesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_preferencesMenuItemActionPerformed
            if (preferencesDialog == null || !preferencesDialog.isDisplayable()) {
                preferencesDialog = new AEViewerPreferencesDialog(this);
            }
            if (!preferencesDialog.isVisible()) {
                preferencesDialog.setVisible(true);
            } else {
                preferencesDialog.toFront();
            }
	}//GEN-LAST:event_preferencesMenuItemActionPerformed

	private void checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItemActionPerformed
            setCheckNonMonotonicTimeExceptionsEnabled(checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem.isSelected());
	}//GEN-LAST:event_checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItemActionPerformed

	private void syncEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syncEnabledCheckBoxMenuItemActionPerformed
            log.warning("no effect here - this event is handled by jAERViewer, not AEViewer");
	}//GEN-LAST:event_syncEnabledCheckBoxMenuItemActionPerformed

	@Description("""
            <html>
            <b>BlockingQueue input from another viewer</b><br>
            Receive events from another AEViewer in <i>this</i> JVM through an in-memory queue (no sockets, no packet loss).<br>
            On the <b>sending</b> viewer use File → Remote → <b>Enable BlockingQueue output to another viewer</b>.<br>
            Start order: enable input here first, then enable output on the sender.<br>
            <p>Not a network protocol — both viewers must be started from the same jAER process (File → New viewer).
            </html>
            """)
	private void openBlockingQueueInputMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openBlockingQueueInputMenuItemActionPerformed
            blockingQueueInputEnabled = openBlockingQueueInputMenuItem.isSelected();
            if (blockingQueueInputEnabled) {
                try {
                    blockingQueueInput = new ArrayBlockingQueue(100);
                    setPlayMode(PlayMode.REMOTE);

                    if ((getAeChipClass() == ch.unizh.ini.jaer.chip.retina.DVS128andCochleaAMS1b.class) && (getJaerViewer().getNumViewers() < 2)) {
                        //Start the cochlea viewer:
                        AEViewer cochleaViewer = new AEViewer(jaerViewer);
                        cochleaViewer.setAeChipClass(ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1b.class);
                        AEChip cochleaChip = cochleaViewer.getChip();

                        //start the retina viewer:
                        AEViewer retinaViewer = new AEViewer(jaerViewer);
                        retinaViewer.setAeChipClass(ch.unizh.ini.jaer.chip.retina.DVS128.class);
                        AEChip retinaChip = retinaViewer.getChip();

                        int n = HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
                        for (int i = 0; i < n; i++) {
                            HardwareInterface hw = HardwareInterfaceFactory.instance().getInterface(i);
                            if (hw == null) {
                                continue;
                            } // in case it disappeared
                            if (hw.toString().startsWith("CypressFX2")) {
                                cochleaChip.setHardwareInterface(hw);
                            } else if (hw.toString().startsWith("DVS128")) {
                                retinaChip.setHardwareInterface(hw);
                                retinaChip.addDefaultEventFilter(ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer.MultichipRetinaEventProducer.class);
                                ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer.MultichipRetinaEventProducer retinaFilter = (ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer.MultichipRetinaEventProducer) retinaChip.getFilterChain().findFilter(ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer.MultichipRetinaEventProducer.class);
                                retinaFilter.initFilter();
                                retinaFilter.setFilterEnabled(true);
                                retinaFilter.doFindAEViewerConsumer();
                                retinaViewer.showFilters(true);
                            } else if (hw.toString().startsWith("CochleaAMS1b")) {
                                cochleaChip.setHardwareInterface(hw);
                                cochleaChip.addDefaultEventFilter(ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer.MultichipAMS1bEventProducer.class);
                                ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer.MultichipAMS1bEventProducer cochleaFilter = (ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer.MultichipAMS1bEventProducer) cochleaChip.getFilterChain().findFilter(ch.unizh.ini.jaer.projects.cochsoundloc.multichipviewer.MultichipAMS1bEventProducer.class);
                                cochleaFilter.initFilter();
                                cochleaFilter.setFilterEnabled(true);
                                cochleaFilter.doFindAEViewerConsumer();
                                cochleaViewer.showFilters(true);
                            }
                        }
                        retinaViewer.setState(Frame.ICONIFIED);
                        retinaViewer.setVisible(true);
                        cochleaViewer.setState(Frame.ICONIFIED);
                        cochleaViewer.setVisible(true);
                    }
                } catch (Exception e) {
                    log.warning(e.getMessage());
                    openBlockingQueueInputMenuItem.setSelected(false);
                }
            } else {
                if (getBlockingQueueInput() != null) {
                    blockingQueueInput = null;
                }
                setPlayMode(PlayMode.WAITING);
            }
	}//GEN-LAST:event_openBlockingQueueInputMenuItemActionPerformed

	@Description("""
            <html>
            <b>BlockingQueue output to another viewer</b><br>
            Send this viewer's events into another AEViewer's in-memory queue in <i>this</i> JVM (no sockets).<br>
            On the <b>receiving</b> viewer first enable File → Remote → <b>Enable BlockingQueue input from another viewer</b>,
            then enable this item.<br>
            <p>Both viewers must be started from the same jAER process (File → New viewer), not two separate <code>ant run</code>s.
            </html>
            """)
	private void blockingQueueOutputEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_blockingQueueOutputEnabledCheckBoxMenuItemActionPerformed
            if (blockingQueueOutputEnabledCheckBoxMenuItem.isSelected()) {
                AEViewer consumer = findBlockingQueueInputViewer();
                if (consumer == null) {
                    blockingQueueOutputEnabledCheckBoxMenuItem.setSelected(false);
                    JOptionPane.showMessageDialog(this,
                            "<html>No other AEViewer has BlockingQueue <b>input</b> enabled.<br><br>"
                            + "On the receiving viewer: File → Remote → <b>Enable BlockingQueue input from another viewer</b>.<br>"
                            + "Then come back here and enable output.<br><br>"
                            + "Both viewers must be in the same jAER process (File → New viewer).",
                            "No BlockingQueue receiver",
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                blockingQueueOutput = consumer.getBlockingQueueInput();
                blockingQueueOutputEnabled = true;
                log.info("BlockingQueue output to " + consumer.getTitle());
            } else {
                blockingQueueOutputEnabled = false;
                blockingQueueOutput = null;
            }
	}//GEN-LAST:event_blockingQueueOutputEnabledCheckBoxMenuItemActionPerformed

	private void openUnicastInputMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openUnicastInputMenuItemActionPerformed
            if (unicastInputEnabled) {

                closeUnicastInput();
                setPlayMode(PlayMode.WAITING);
            } else {
                try {
                    unicastInput = new AEUnicastInput(chip);
                    getSupport().addPropertyChangeListener(EVENT_PAUSED, unicastInput);
                    AEUnicastDialog dlg
                            = new AEUnicastDialog(this, true, unicastInput);
                    dlg.setVisible(true);
                    int ret = dlg.getReturnStatus();
                    if (ret != AEUnicastDialog.RET_OK) {
                        return;
                    }
                    unicastInput.open();
                    setPlayMode(PlayMode.REMOTE);
                    openUnicastInputMenuItem.setText("Close unicast input from " + unicastInput.getHost() + ":" + unicastInput.getPort());
                    log.info("opened unicast input " + unicastInput);
                    unicastInputEnabled = true;

                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(this, "<html>Couldn't open AEUnicastInput input: <br>" + e.toString() + "</html>");
                }

            }
	}//GEN-LAST:event_openUnicastInputMenuItemActionPerformed

	private void unicastOutputEnabledCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unicastOutputEnabledCheckBoxMenuItemActionPerformed
            if (unicastOutputEnabled) {
                if (unicastOutput != null) {
                    unicastOutput.close();
                    log.info("closed " + unicastOutput);
                    unicastOutput = null;

                }

                unicastOutputEnabled = false;
                //            setPlayMode(PlayMode.WAITING); // don't stop live input or file just because we stop output datagrams
            } else {
                try {
                    unicastOutput = new AEUnicastOutput();
                    AEUnicastDialog dlg
                            = new AEUnicastDialog(this, true, unicastOutput);
                    dlg.setVisible(true);
                    int ret = dlg.getReturnStatus();
                    if (ret != AEUnicastDialog.RET_OK) {
                        return;
                    }
                    unicastOutput.open();
                    log.info("opened unicast output " + unicastOutput);
                    unicastOutputEnabled = true;

                } catch (Exception e) {
                    log.warning(e.toString());
                    JOptionPane.showMessageDialog(this, "<html>Couldn't open AEUnicastOutput: <br>" + e.toString() + "</html>");
                }

            }
	}//GEN-LAST:event_unicastOutputEnabledCheckBoxMenuItemActionPerformed

	private void recordFilteredEventsCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recordFilteredEventsCheckBoxMenuItemActionPerformed
            setRecordFilteredEventsEnabled(recordFilteredEventsCheckBoxMenuItem.isSelected());
	}//GEN-LAST:event_recordFilteredEventsCheckBoxMenuItemActionPerformed

	private void recordingSetTimelimitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recordingSetTimelimitMenuItemActionPerformed
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.add(new JLabel("<html>Choose a preset or type a duration (0 or No limit for none).<br>"
                    + "Examples: 1000 (ms implied), 2m 30s, 1h 15m<br>"
                    + (isRecordingEnabled()
                    ? "Applies immediately to the <b>current recording</b> (total time from when it started; now "
                    + formatRecordingDurationHms(recordingElapsedMs()) + " recorded)."
                    : "Applies to the next recording.")
                    + "</html>"), BorderLayout.NORTH);

            JComboBox<String> chooser = new JComboBox<>(RECORDING_TIME_LIMIT_PRESETS);
            chooser.setMaximumRowCount(RECORDING_TIME_LIMIT_PRESETS.length);
            JTextField freeForm = new JTextField(16);
            String initial = recordingTimeLimitDialogInitialValue();
            freeForm.setText(RECORDING_TIME_LIMIT_NO_LIMIT.equals(initial) ? "0" : initial);
            int presetIndex = -1;
            for (int i = 0; i < RECORDING_TIME_LIMIT_PRESETS.length; i++) {
                if (RECORDING_TIME_LIMIT_PRESETS[i].equals(initial)) {
                    presetIndex = i;
                    break;
                }
            }
            chooser.setSelectedIndex(presetIndex);
            chooser.addActionListener(e -> {
                Object sel = chooser.getSelectedItem();
                if (sel == null) {
                    return;
                }
                String preset = sel.toString();
                freeForm.setText(RECORDING_TIME_LIMIT_NO_LIMIT.equals(preset) ? "0" : preset);
                freeForm.requestFocusInWindow();
                freeForm.selectAll();
            });

            JPanel chooserRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            chooserRow.add(new JLabel("Preset:"));
            chooserRow.add(chooser);
            chooserRow.add(new JLabel("or type:"));
            chooserRow.add(freeForm);
            panel.add(chooserRow, BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(this, panel, "Recording time limit",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
            String ans = freeForm.getText();
            if (ans == null) {
                return;
            }
            ans = ans.trim();
            if (ans.isEmpty()) {
                return;
            }

            try {
                boolean wasRecording = isRecordingEnabled();
                long elapsedMs = wasRecording ? recordingElapsedMs() : 0L;
                applyRecordingTimeLimit(parseRecordingTimeLimitMs(ans));
                String s = recordingTimeLimit <= 0 ? RECORDING_TIME_LIMIT_NO_LIMIT
                        : formatRecordingTimeLimitForDialog(recordingTimeLimit);
                log.info(String.format("recording time limit set to %s (%d ms)", s, recordingTimeLimit));
                if (wasRecording && recordingTimeLimit > 0 && elapsedMs > recordingTimeLimit) {
                    return; // already past the new limit; stopRecording shows the save dialog
                }
                String msg;
                if (wasRecording && isRecordingEnabled() && recordingTimeLimit > 0) {
                    long remainingMs = Math.max(0L, recordingTimeLimit - elapsedMs);
                    msg = String.format("Time limit set to %s (%d ms). Current recording has %s remaining.",
                            s, recordingTimeLimit, formatRecordingDurationHms(remainingMs));
                } else if (wasRecording && recordingTimeLimit <= 0) {
                    msg = "Recording time limit cleared; current recording continues with no limit.";
                } else {
                    msg = String.format("Time limit set to %s (%d ms)", s, recordingTimeLimit);
                }
                JOptionPane.showMessageDialog(this, msg);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, String.format("Bad format? Caught %s", e.toString()), "Error with duration", JOptionPane.ERROR_MESSAGE);
            }
	}//GEN-LAST:event_recordingSetTimelimitMenuItemActionPerformed

    private String recordingTimeLimitDialogInitialValue() {
        if (recordingTimeLimit <= 0) {
            return RECORDING_TIME_LIMIT_NO_LIMIT;
        }
        for (String preset : RECORDING_TIME_LIMIT_PRESETS) {
            if (RECORDING_TIME_LIMIT_NO_LIMIT.equals(preset)) {
                continue;
            }
            try {
                if (parseRecordingTimeLimitMs(preset) == recordingTimeLimit) {
                    return preset;
                }
            } catch (IllegalArgumentException e) {
                // skip unmatched preset
            }
        }
        return formatRecordingTimeLimitForDialog(recordingTimeLimit);
    }

    private static String formatRecordingTimeLimitForDialog(long ms) {
        if (ms <= 0) {
            return RECORDING_TIME_LIMIT_NO_LIMIT;
        }
        Period p = new Period(ms).normalizedStandard(PeriodType.dayTime());
        String printed = RECORDING_TIME_LIMIT_FORMATTER.print(p);
        return printed.isEmpty() ? Long.toString(ms) : printed;
    }

    private static long parseRecordingTimeLimitMs(String ans) {
        if (ans == null) {
            throw new IllegalArgumentException("null duration");
        }
        ans = ans.trim();
        if (ans.isEmpty()) {
            throw new IllegalArgumentException("empty duration");
        }
        if (ans.equalsIgnoreCase(RECORDING_TIME_LIMIT_NO_LIMIT)) {
            return 0L;
        }
        if (ans.matches("\\d+")) {
            return Long.parseLong(ans);
        }
        Period p = RECORDING_TIME_LIMIT_FORMATTER.parsePeriod(ans);
        return p.toStandardDuration().getMillis();
    }

    private long recordingElapsedMs() {
        return Math.max(0L, System.currentTimeMillis() - recordingStartTime);
    }

    private void invalidateRecordingTimeLimitOverlay() {
        recordingTimeLimitOverlayText = null;
        recordingTimeLimitOverlayLastMs = 0;
    }

    /**
     * Sets the recording time limit and applies it to an in-progress recording:
     * overlay updates immediately, and recording stops if elapsed time already
     * exceeds the new limit. {@code 0} means no limit.
     */
    private void applyRecordingTimeLimit(long limitMs) {
        recordingTimeLimit = Math.max(0L, limitMs);
        invalidateRecordingTimeLimitOverlay();
        if (isRecordingEnabled() && recordingTimeLimit > 0) {
            stopRecordingIfTimeLimitReached();
        }
    }

    /**
     * Stops recording when a time limit is set and wall time since
     * {@link #recordingStartTime} exceeds it. Safe from the view loop or the EDT.
     */
    private void stopRecordingIfTimeLimitReached() {
        if (!isRecordingEnabled() || recordingTimeLimit <= 0) {
            return;
        }
        if (recordingElapsedMs() <= recordingTimeLimit) {
            return;
        }
        log.info("recording time limit reached, stopping recording");
        Runnable stop = () -> {
            if (isRecordingEnabled()) {
                stopRecording(true); // AWT thread: file menu and save dialog
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            stop.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(stop);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Exception stopping recording: " + e.toString(), e);
            }
        }
    }

	private void recordingPlaybackImmediatelyCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recordingPlaybackImmediatelyCheckBoxMenuItemActionPerformed
            setRecordingPlaybackImmediatelyEnabled(!isRecordingPlaybackImmediatelyEnabled());
	}//GEN-LAST:event_recordingPlaybackImmediatelyCheckBoxMenuItemActionPerformed

	private void timestampResetBitmaskMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timestampResetBitmaskMenuItemActionPerformed
            String ret = (String) JOptionPane.showInputDialog(this, "<html>Enter hex value bitmask for zeroing timestamps, e.g. 8000<br>Whenever any of these bits are set, the time will be zeroed at this point,<br> and subsequent timestamps will have this one subtracted from it.<br>The file must be opened after the mask is set.", "Timestamp reset bitmask value", JOptionPane.QUESTION_MESSAGE, null, null, Integer.toHexString(aeFileInputStreamTimestampResetBitmask));
            if (ret == null) {
                return;
            }
            try {
                setAeFileInputStreamTimestampResetBitmask(Integer.parseInt(ret, 16));
            } catch (Exception e) {
                log.warning(e.toString());
            }
	}//GEN-LAST:event_timestampResetBitmaskMenuItemActionPerformed

	private void closeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeMenuItemActionPerformed
            stopMe();
	}//GEN-LAST:event_closeMenuItemActionPerformed

        private void exportVideoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
            ExportVideoDialog.showDialog(this);
        }

        private void saveAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
            SaveAsExportDialog.showDialog(this);
        }

        private void updateSaveAsMenuItemEnabled() {
            saveAsMenuItem.setEnabled(getPlayMode() == PlayMode.PLAYBACK
                    && getAePlayer() != null && getAePlayer().getAEInputStream() != null
                    && !SaveAsExportDialog.isExportActive(this));
        }

        private void stopVideoExportMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
            ExportVideoDialog.stopActiveExport(this);
            updateStopVideoExportMenuItemEnabled();
        }

        private void updateStopVideoExportMenuItemEnabled() {
            stopVideoExportMenuItem.setEnabled(ExportVideoDialog.isExportRecordingActive(this));
        }

    /**
     * Returns the enabled {@link JaerAviWriter} currently writing a video file,
     * or null if none. Used by the view loop for synchronized frame capture.
     */
    public JaerAviWriter getActiveJaerAviWriter() {
        if (chip == null || chip.getFilterChain() == null) {
            return null;
        }
        JaerAviWriter w = (JaerAviWriter) chip.getFilterChain().findFilter(JaerAviWriter.class);
        if (w != null && w.isFilterEnabled() && w.isRecordingActive()) {
            return w;
        }
        return null;
    }

    /** True when synchronized OpenGL→AVI recording is in progress. */
    public boolean isJaerAviRecordingActive() {
        return getActiveJaerAviWriter() != null;
    }

	private void openMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openMenuItemActionPerformed
            try {
                openAedatInputFile(null);
            } catch (IOException ex) {
                Logger.getLogger(AEViewer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(AEViewer.class.getName()).log(Level.SEVERE, null, ex);
            }
	}//GEN-LAST:event_openMenuItemActionPerformed

        private void showFileInfoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
            showFileInfoDialog();
        }

        private void updateShowFileInfoMenuItemEnabled() {
            showFileInfoMenuItem.setEnabled(isShowFileInfoAvailable());
        }

        /** AEDAT-4 playback only; other formats return an empty {@code getFileInfo()}. */
        private boolean isShowFileInfoAvailable() {
            if (getPlayMode() != PlayMode.PLAYBACK || getAePlayer() == null) {
                return false;
            }
            AEFileInputStreamInterface stream = getAePlayer().getAEInputStream();
            return stream instanceof Aedat4FileInputStream;
        }

        private void showFileInfoDialog() {
            AEFileInputStreamInterface stream = getAePlayer() == null ? null : getAePlayer().getAEInputStream();
            String info = composeFileInfoText(stream);
            File f = stream == null ? null : stream.getFile();
            boolean firstShow = fileInfoDialog == null;
            if (firstShow) {
                fileInfoTextArea = new JTextArea();
                fileInfoTextArea.setEditable(false);
                fileInfoTextArea.setLineWrap(false);
                fileInfoTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
                JScrollPane scroll = new JScrollPane(fileInfoTextArea);
                JButton close = new JButton("Close");
                fileInfoDialog = new FileInfoFrame();
                fileInfoDialog.setIconImage(getIconImage());
                fileInfoDialog.getContentPane().add(scroll, BorderLayout.CENTER);
                JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                buttons.add(close);
                fileInfoDialog.getContentPane().add(buttons, BorderLayout.SOUTH);
                close.addActionListener(e -> fileInfoDialog.setVisible(false));
                fileInfoDialog.getRootPane().setDefaultButton(close);
                fileInfoDialog.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            }
            fileInfoTextArea.setForeground(Color.BLACK);
            fileInfoTextArea.setDisabledTextColor(Color.BLACK);
            fileInfoTextArea.setBackground(Color.WHITE);
            fileInfoTextArea.setOpaque(true);
            fileInfoTextArea.setCaretColor(Color.BLACK);
            fileInfoDialog.setTitle(f != null ? "File info — " + f.getName() : "Recording file info");
            fileInfoTextArea.setText(info);
            fileInfoTextArea.setCaretPosition(0);
            String[] lines = info.split("\n", -1);
            int cols = 40;
            for (String line : lines) {
                cols = Math.max(cols, line.length());
            }
            fileInfoTextArea.setColumns(cols);
            fileInfoTextArea.setRows(Math.max(8, lines.length + 1));
            fileInfoDialog.pack();
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            Dimension size = fileInfoDialog.getSize();
            int maxW = (int) (screen.width * 0.9);
            int maxH = (int) (screen.height * 0.8);
            if (size.width > maxW || size.height > maxH) {
                fileInfoDialog.setSize(Math.min(size.width, maxW), Math.min(size.height, maxH));
            }
            if (firstShow || !fileInfoDialog.isVisible()) {
                fileInfoDialog.setLocationRelativeTo(this);
            }
            if (!fileInfoDialog.isVisible()) {
                fileInfoDialog.setVisible(true);
            } else {
                fileInfoDialog.toFront();
            }
        }

        /**
         * File info body: current recording, plus original vs exported compression
         * when this file is the last File → Save As output.
         */
        private String composeFileInfoText(AEFileInputStreamInterface stream) {
            String info = stream == null ? "" : stream.getFileInfo();
            if (info == null || info.isEmpty()) {
                info = "File info is not available for this recording format.";
            }
            File f = stream == null ? null : stream.getFile();
            String source = lastSaveAsSourceInfoFor(f);
            if (source == null || source.isEmpty()) {
                return info;
            }
            return "Original (input)\n" + source + "\n\nExported (output)\n" + info;
        }

    /**
     * File → Show file info. Packed to content; {@link WindowSaver.DontResize}
     * so a saved huge size cannot stretch it. Stable {@code FileInfo} name so
     * position restore is not keyed on the per-file title.
     */
    private static final class FileInfoFrame extends JFrame implements WindowSaver.DontResize {
        FileInfoFrame() {
            super("Recording file info");
            setName("FileInfo");
        }
    }

    /**
     * Centralized call to open an input file. The opened file is added the
     * recentFiles list.
     *
     * @param f the input file. Pass null to open the file dialog with preview,
     * etc. If f is a folder, then the file dialog opens.
     * @throws IOException
     * @throws InterruptedException if opening is interrupted somehow
     */
    public void openAedatInputFile(File f) throws IOException, InterruptedException {
        if ((f != null) && f.isFile()) {
            recentFiles.addFile(f);
            getAePlayer().startPlayback(f); // TODO fix with progress monitor
        } else if ((f != null) && f.isDirectory()) {
            prefs.put("AEViewer.lastFile", f.getCanonicalPath());
            recentFiles.addFile(f);
            aePlayer.openAEInputFileDialog();
        } else if (f == null) {
            aePlayer.openAEInputFileDialog();
        }
    }

	private void newViewerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newViewerMenuItemActionPerformed
            new AEViewer(jaerViewer).setVisible(true);
	}//GEN-LAST:event_newViewerMenuItemActionPerformed

	private void interfaceMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_interfaceMenuMenuSelected
            // Cache only — USB enumeration on the EDT hangs the whole UI.
            buildInterfaceMenu();
	}//GEN-LAST:event_interfaceMenuMenuSelected

    private void usbTuningMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_usbTuningMenuItemActionPerformed
        showUsbTuningFrame();
    }//GEN-LAST:event_usbTuningMenuItemActionPerformed

    private void printUSBStatisticsCBMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_printUSBStatisticsCBMIActionPerformed
        if ((chip.getHardwareInterface() != null) && (chip.getHardwareInterface() instanceof HasUsbStatistics)) {
            HasUsbStatistics usbStatistics = (HasUsbStatistics) chip.getHardwareInterface();
            usbStatistics.setPrintUsbStatistics(printUSBStatisticsCBMI.isSelected());
        }
    }//GEN-LAST:event_printUSBStatisticsCBMIActionPerformed

    private void setFrameRateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setFrameRateMenuItemActionPerformed
        int fpsNow = getFrameRater().getDesiredFPS();
        String fpsString = JOptionPane.showInputDialog(this, "Desired frame rate?", Integer.toString(fpsNow));
        try {
            int fps = Integer.parseInt(fpsString);
            if (fps != fpsNow) {
                getFrameRater().setDesiredFPS(fps);
            }
        } catch (NumberFormatException e) {
            log.warning(e.toString());
        }
    }//GEN-LAST:event_setFrameRateMenuItemActionPerformed

    private void setBorderSpaceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setBorderSpaceMenuItemActionPerformed
        int borderSpaceNow = chip.getCanvas().getBorderSpacePixels();
        String borderString = JOptionPane.showInputDialog(this, "Desired border space in chip pixels?", Integer.toString(borderSpaceNow));
        try {
            int newSpace = Integer.parseInt(borderString);
            if (newSpace != borderSpaceNow) {
                chip.getCanvas().setBorderSpacePixels(newSpace);
                setBorderSpaceMenuItem.setText(String.format("Set border space (currently %d)", newSpace));
                repaint();
            }
        } catch (NumberFormatException e) {
            log.warning(e.toString());
        }
    }//GEN-LAST:event_setBorderSpaceMenuItemActionPerformed

    private void skipPacketsRenderingCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_skipPacketsRenderingCheckBoxMenuItemActionPerformed
        if (chip == null || chip.getRenderer() == null) {
            return;
        }
        chip.getRenderer().setAdaptiveRenderSkippingEnabled(
                skipPacketsRenderingCheckBoxMenuItem.isSelected());
        if (getPlayMode() == PlayMode.PLAYBACK) {
            // A user choice during playback supersedes the temporary saved state.
            adaptiveRenderSkipMaxBeforePlayback = -1;
        }
        syncAdaptiveRenderSkipMenuFromRenderer();
        showAdaptiveRenderSkippingOverlay();
    }//GEN-LAST:event_skipPacketsRenderingCheckBoxMenuItemActionPerformed

    private void viewRenderBlankFramesCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewRenderBlankFramesCheckBoxMenuItemActionPerformed
        setRenderBlankFramesEnabled(viewRenderBlankFramesCheckBoxMenuItem.isSelected());
    }//GEN-LAST:event_viewRenderBlankFramesCheckBoxMenuItemActionPerformed

    private void viewActiveRenderingEnabledMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewActiveRenderingEnabledMenuItemActionPerformed
        setActiveRenderingEnabled(viewActiveRenderingEnabledMenuItem.isSelected());
    }//GEN-LAST:event_viewActiveRenderingEnabledMenuItemActionPerformed

    private void skipPacketsRenderingCheckBoxMenuItemStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_skipPacketsRenderingCheckBoxMenuItemStateChanged
        if (suppressAdaptiveRenderSkipMenuSync) {
            return;
        }
        fixSkipPacketsRenderingMenuItems();
    }//GEN-LAST:event_skipPacketsRenderingCheckBoxMenuItemStateChanged

    private void jogBackwardsMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jogBackwardsMIActionPerformed
        if ((getPlayMode() == PlayMode.PLAYBACK) && (getAePlayer() != null)) {
            getAePlayer().jogBackwardAction.actionPerformed(evt);
        }
    }//GEN-LAST:event_jogBackwardsMIActionPerformed

    private void cancelJogMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelJogMIActionPerformed
        if ((getPlayMode() == PlayMode.PLAYBACK) && (getAePlayer() != null)) {
            getAePlayer().cancelJogAction.actionPerformed(evt);
        }
    }//GEN-LAST:event_cancelJogMIActionPerformed

    private void setJogNCountActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setJogNCountActionPerformed
        String s = JOptionPane.showInputDialog("Number of packets to fast forward or rewind?", getAePlayer().getJogPacketCount());
        if ((s == null) || s.isEmpty()) {
            return;
        }
        try {
            int n = Integer.parseInt(s);
            getAePlayer().setJogPacketCount(n);
            setJogNCount.setText("Set forward/rewind N... (currently " + getAePlayer().getJogPacketCount() + ")");
        } catch (NumberFormatException e) {
            return;
        }
    }//GEN-LAST:event_setJogNCountActionPerformed

    private void jogForwardMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jogForwardMIActionPerformed
        if ((getPlayMode() == PlayMode.PLAYBACK) && (getAePlayer() != null)) {
            getAePlayer().jogForwardAction.actionPerformed(evt);
        }
    }//GEN-LAST:event_jogForwardMIActionPerformed

    private void gitUpdateMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gitUpdateMenuItemActionPerformed
        if (jaerUpdaterFrame == null) {
            jaerUpdaterFrame = new JaerUpdaterFrame();
        }
        jaerUpdaterFrame.setVisible(true);
        jaerUpdaterFrame.setLocationRelativeTo(this);
    }//GEN-LAST:event_gitUpdateMenuItemActionPerformed

    private void viewStepBackwardsMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewStepBackwardsMIActionPerformed
        getAePlayer().stepBackwardAction.actionPerformed(evt);
    }//GEN-LAST:event_viewStepBackwardsMIActionPerformed

    private void resetAccumulationMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetAccumulationMenuItemActionPerformed
        boolean old = getRenderer().isAccumulateEnabled();
        getRenderer().resetAccumulation();
        if (!old) {
            getRenderer().setAccumulateEnabled(true);
            getSupport().firePropertyChange(AEViewer.EVENT_ACCUMULATE_ENABLED, old, getRenderer().isAccumulateEnabled());
            showActionText("Accumulate events=" + getRenderer().isAccumulateEnabled());
        }
    }//GEN-LAST:event_resetAccumulationMenuItemActionPerformed

    private void releaseNotesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        showInBrowser(JaerConstants.JAER_RELEASES);
    }

    private void checkForUpdatesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkForUpdatesMenuItemActionPerformed
        new JaerUpdaterInstall4j().checkForInstall4jReleaseUpdate(this, true);
    }//GEN-LAST:event_checkForUpdatesMenuItemActionPerformed

    private void loggingLevelMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_loggingLevelMenuMenuSelected
        if (loggingLevelRadioButtons == null) {
            Level currentLevel = log.getLevel();
            loggingLevelMenu.getPopupMenu().setLightWeightPopupEnabled(false);
            loggingLevelRadioButtons = new ArrayList();
            for (Level l : loggingLevels) {
                LoggingLevelButton bmi = new LoggingLevelButton(l);
                loggingLevelButtonGroup.add(bmi);
                loggingLevelMenu.add(bmi);
                if (l.equals(currentLevel)) {
                    bmi.setSelected(true);
                }
            }
        }
    }//GEN-LAST:event_loggingLevelMenuMenuSelected

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        new JaerUpdaterInstall4j().maybeDoPeriodicUpdateCheck(AEViewer.this);
    }//GEN-LAST:event_formWindowOpened

    private void cyclePreviousColorRenderingMethodMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cyclePreviousColorRenderingMethodMenuItemActionPerformed
        if ((chipCanvas != null) && (chipCanvas.getDisplayMethod() != null) /*&& (chipCanvas.getDisplayMethod() instanceof DisplayMethod2D)*/) {
            getRenderer().cycleColorMode(false);
            showActionText(String.format("DVS color mode %s", getRenderer().getColorMode().toString()));
        } else {
            log.warning("It does not make sense to cycle color mode for this display method, ignoring");
        }
    }//GEN-LAST:event_cyclePreviousColorRenderingMethodMenuItemActionPerformed

    private void showRenderingModeMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showRenderingModeMIActionPerformed
        log.info(getRenderer().getDescription());
        showActionText(getRenderer().getDescription());
    }//GEN-LAST:event_showRenderingModeMIActionPerformed

    private void renewChipMIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_renewChipMIActionPerformed
        if ((biasgenFrame != null) && !biasgenFrame.isModificationsSaved()) {
            return;
        }
        try {
            Class cl = chip.getClass();
            try {
                setCursor(new Cursor(Cursor.WAIT_CURSOR));
                setAeChipClass(cl);
            } finally {
                setCursor(Cursor.getDefaultCursor());
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }

    }//GEN-LAST:event_renewChipMIActionPerformed

    private ArrayList<LoggingLevelButton> loggingLevelRadioButtons = null;
    private final Level[] loggingLevels = {Level.OFF, Level.SEVERE, Level.WARNING, Level.INFO, Level.FINE, Level.FINER, Level.FINEST, Level.ALL};
    private final ButtonGroup loggingLevelButtonGroup = new ButtonGroup();

    final class LoggingLevelButton extends JRadioButtonMenuItem {

        final Level level;

        public LoggingLevelButton(Level level) {
            this.level = level;
            setName(level.getName());
            setText(level.getName());
            setToolTipText(String.format("Sets logging level to %s", level.getName()));
            addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    log.info(String.format("Setting logging level of logger %s to %s", log.getName(), level.getName()));
                    log.setLevel(level);
                    //get the top Logger
                    Logger topLogger = java.util.logging.Logger.getLogger("");

                    // Handler for console (reuse it if it already exists)
                    // https://stackoverflow.com/questions/470430/java-util-logging-logger-doesnt-respect-java-util-logging-level
                    Handler consoleHandler = null;
                    //find the console handler
                    for (Handler handler : topLogger.getHandlers()) {
                        if (handler instanceof ConsoleHandler) {
                            //found the console handler
                            consoleHandler = handler;
                            break;
                        }
                    }

                    if (consoleHandler != null) {
                        consoleHandler.setLevel(level);
                    }
                    if (loggingHandler != null) {
                        loggingHandler.setLevel(level);
                    }
                }
            });
        }

    }

    private KeyEvent lastKeyEvent = null;

    /**
     * Returns desired frame rate of FrameRater
     *
     * @return desired frame rate in Hz.
     */
    public int getDesiredFrameRate() {
        return frameRater.getDesiredFPS();
    }

    /**
     * Sets desired frame rate of FrameRater
     *
     * @param renderDesiredFrameRateHz frame rate in Hz
     */
    public void setDesiredFrameRate(int renderDesiredFrameRateHz) {
        frameRater.setDesiredFPS(renderDesiredFrameRateHz);
    }

    /**
     * Returns true if viewer is paused
     *
     * @return true if paused
     */
    public boolean isPaused() {
        return getAePlayer().isPaused();
    }

    /**
     * Sets paused. If viewing is synchronized, then all viwewers will be
     * paused. Fires PropertyChangeEvent "paused". Interrupts the ViewLoop.
     *
     * @param paused true to pause
     */
    public void setPaused(boolean paused) {
        //        log.info("settings paused=" + paused);
        boolean old = isPaused();
        getAePlayer().setPaused(paused);
        pauseRenderingCheckBoxMenuItem.setSelected(paused);
        // Do not interrupt during Save As: Thread.interrupt closes FileChannel and
        // also makes pauseIdleWaitIfNeeded() skip its wait (interrupted()==true).
        if (!viewLoopSuspendedForOfflineExport && !isSingleStep()
                && (getJaerViewer().getNumViewers() > 1)) {
            interruptViewloop();  // to break out of exchangeers that might be waiting, problem is that it also interrupts a singleStep ....
        }
        getSupport().firePropertyChange(EVENT_PAUSED, old, isPaused());
    }

    /**
     * Returns true if AEViewer (or the BiasgenFrame or FilterSetting) windows
     * is active, i.e. has focus
     *
     * @return true if some jAER window has focus
     */
    public boolean isAnyWindowActive() {
        if (isActive()) {
            return true;
        }
        if (getFilterFrame() != null && getFilterFrame().isActive()) {
            return true;
        }
        if (getBiasgenFrame() != null && getBiasgenFrame().isActive()) {
            return true;
        }
        return false;
    }

    public boolean isActiveRenderingEnabled() {
        return activeRenderingEnabled;
    }

    public void setActiveRenderingEnabled(boolean activeRenderingEnabled) {
        this.activeRenderingEnabled = activeRenderingEnabled;
        prefs.putBoolean("AEViewer.activeRenderingEnabled", activeRenderingEnabled);
        if (viewActiveRenderingEnabledMenuItem != null) {
            viewActiveRenderingEnabledMenuItem.setSelected(activeRenderingEnabled);
        }
    }

    /**
     * Drag and drop data file onto frame to play it. Called while a drag
     * operation is ongoing, when the mouse pointer enters the operable part of
     * the drop site for the DropTarget registered with this listener.
     *
     * @param dtde the event.
     *
     */
    @Override
    synchronized public void dragEnter(DropTargetDragEvent dtde) {
        log.info(dtde.toString());
        Transferable transferable = dtde.getTransferable();
        try {
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                java.util.List<File> files = (java.util.List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                for (File f : files) {
                    if (f.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION)
                            || f.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT2)
                            || f.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4)
                            || f.getName().endsWith(AEDataFile.INDEX_FILE_EXTENSION)
                            || f.getName().endsWith(AEDataFile.OLD_DATA_FILE_EXTENSION)
                            || f.getName().endsWith(AEDataFile.OLD_INDEX_FILE_EXTENSION)
                            || f.getName().endsWith(RosbagFileInputStream.DATA_FILE_EXTENSION)
                            || f.getName().endsWith(TextFileInputStream.FILE_EXTENSION_CSV)
                            || f.getName().endsWith(TextFileInputStream.FILE_EXTENSION_TXT)
                            || f.getName().toLowerCase(Locale.ROOT).endsWith("." + MetavisionRawFileInputStream.DATA_FILE_EXTENSION)
                            || f.getName().toLowerCase(Locale.ROOT).endsWith("." + DsecHdf5AEInputStream.DATA_FILE_EXTENSION_H5)
                            || f.getName().toLowerCase(Locale.ROOT).endsWith("." + DsecHdf5AEInputStream.DATA_FILE_EXTENSION_HDF5)) {
                        draggedFile = f;
                        log.info("User dragged file " + draggedFile);
                    } else {
                        String s = String.format("Cannot play this file extension for file '%s'", f.getAbsoluteFile());
                        log.warning(s);
                        JOptionPane.showMessageDialog(this, s, "Cannot play", JOptionPane.WARNING_MESSAGE);
                        draggedFile = null;
                    }
                }
            }
        } catch (UnsupportedFlavorException e) {
            log.warning(String.format("Format not supported: %s", e.toString()));
        } catch (IOException e) {
            log.severe(String.format("IOException: %s", e.toString()));
        }

    }

    /**
     * Called while a drag operation is ongoing, when the mouse pointer has
     * exited the operable part of the drop site for the DropTarget registered
     * with this listener.
     *
     * @param dte the event.
     */
    @Override
    synchronized public void dragExit(DropTargetEvent dte) {
        log.info(dte.toString());
        draggedFile = null;
    }
    //          Called when a drag operation is ongoing, while the mouse pointer is still over the operable part of the drop site for the DropTarget registered with this listener.

    @Override
    synchronized public void dragOver(DropTargetDragEvent dtde) {
//        log.info(dtde.toString());
    }

    /**
     * Called when the drag operation has terminated with a drop on the operable
     * part of the drop site for the DropTarget registered with this listener.
     *
     * @param dtde the drop event.
     */
    @Override
    synchronized public void drop(DropTargetDropEvent dtde) {
        log.info(dtde.toString());
        if (draggedFile != null) {
            //            log.info("AEViewer.drop(): opening file "+draggedFile);
            try {
                recentFiles.addFile(draggedFile);
                synchronized (getAePlayer()) {
                    getAePlayer().startPlayback(draggedFile); // TODO fix with progress monitor
                }
            } catch (IOException e) {
                log.warning(e.toString());
            } catch (InterruptedException ex) {
                log.warning("opening dropped file " + draggedFile + " interrupted");
            }
        } else {
//            log.warning("null dragged file in DropTargetDropEvent="+dtde);
        }
    }

    //          Called if the user has modified the current drop gesture.
    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    public boolean isRecordingPlaybackImmediatelyEnabled() {
        return recordingPlaybackImmediatelyEnabled;
    }

    public void setRecordingPlaybackImmediatelyEnabled(boolean recordingPlaybackImmediatelyEnabled) {
        this.recordingPlaybackImmediatelyEnabled = recordingPlaybackImmediatelyEnabled;
        prefs.putBoolean("AEViewer.loggingPlaybackImmediatelyEnabled", recordingPlaybackImmediatelyEnabled);
        if (recordingPlaybackImmediatelyCheckBoxMenuItem != null) {
            recordingPlaybackImmediatelyCheckBoxMenuItem.setSelected(recordingPlaybackImmediatelyEnabled);
        }
    }

    /**
     * Whether to draw the on-canvas Recording overlay while recording.
     *
     * @return true if the overlay should be shown (default)
     */
    public boolean isShowRecordingOverlay() {
        return showRecordingOverlay;
    }

    /**
     * Enables or disables the on-canvas Recording overlay while recording.
     *
     * @param showRecordingOverlay true to show the overlay
     */
    public void setShowRecordingOverlay(boolean showRecordingOverlay) {
        this.showRecordingOverlay = showRecordingOverlay;
        prefs.putBoolean("AEViewer.showRecordingOverlay", showRecordingOverlay);
    }

    public boolean isShowRosOutputOverlay() {
        return showRosOutputOverlay;
    }

    public void setShowRosOutputOverlay(boolean showRosOutputOverlay) {
        this.showRosOutputOverlay = showRosOutputOverlay;
        prefs.putBoolean("AEViewer.showRosOutputOverlay", showRosOutputOverlay);
    }

    /**
     * Whether to draw the on-canvas DNN mmap overlay while
     * {@link DNNOutputViaSharedMemory} is enabled.
     */
    public boolean isShowDnnSharedMemoryOverlay() {
        return showDnnSharedMemoryOverlay;
    }

    /**
     * Enables or disables the on-canvas DNN mmap overlay.
     */
    public void setShowDnnSharedMemoryOverlay(boolean showDnnSharedMemoryOverlay) {
        this.showDnnSharedMemoryOverlay = showDnnSharedMemoryOverlay;
        prefs.putBoolean("AEViewer.showDnnSharedMemoryOverlay", showDnnSharedMemoryOverlay);
    }

    public boolean isShowOpenCvOutputOverlay() {
        return showOpenCvOutputOverlay;
    }

    public void setShowOpenCvOutputOverlay(boolean showOpenCvOutputOverlay) {
        this.showOpenCvOutputOverlay = showOpenCvOutputOverlay;
        prefs.putBoolean("AEViewer.showOpenCvOutputOverlay", showOpenCvOutputOverlay);
    }

    private void initRosOutputRemoteMenu() {
        rosOutputMenuItem = new JMenuItem();
        rosOutputMenuItem.setMnemonic('r');
        rosOutputMenuItem.addActionListener(e -> rosOutputMenuItemActionPerformed());
        remoteMenu.add(rosOutputMenuItem);
        updateRosOutputMenuItem();
    }

    private void updateRosOutputMenuItem() {
        if (rosOutputMenuItem == null) {
            return;
        }
        ROSOutput r = findRosOutput();
        boolean streaming = r != null && r.isFilterEnabled();
        rosOutputMenuItem.setText(streaming
                ? "ROS2 / Foxglove frame output (streaming)..."
                : "ROS2 / Foxglove frame output (stopped)...");
        applyRemoteMenuStreamingStyle(rosOutputMenuItem, streaming);
        updateRemoteMenuStreamingStyle();
    }

    private ROSOutput findRosOutput() {
        return ROSOutput.find(getChip());
    }

    private boolean isRosOutputSkipChipRendering() {
        ROSOutput r = findRosOutput();
        return r != null && r.isFilterEnabled() && r.isSkipChipRendering();
    }

    private void disposeFileMenuFrames() {
        if (fileInfoDialog != null) {
            fileInfoDialog.dispose();
            fileInfoDialog = null;
        }
        if (preferencesDialog != null) {
            preferencesDialog.dispose();
            preferencesDialog = null;
        }
        if (quickHelpFrame != null) {
            quickHelpFrame.dispose();
            quickHelpFrame = null;
        }
        ExportVideoDialog.disposeForViewer(this);
        SaveAsExportDialog.disposeForViewer(this);
    }

    private void disposeRosOutputDialog() {
        if (rosOutputDialog != null) {
            rosOutputDialog.dispose();
            rosOutputDialog = null;
        }
    }

    private void showRosOutputDialog(ROSOutput r) {
        if (rosOutputDialog != null && rosOutputDialog.getFilter() != r) {
            disposeRosOutputDialog();
        }
        if (rosOutputDialog == null) {
            rosOutputDialog = new ROSOutputDialog(this, r);
        }
        rosOutputDialog.setVisible(true);
        rosOutputDialog.toFront();
    }

    private void rosOutputMenuItemActionPerformed() {
        AEChip c = getChip();
        if (c == null) {
            return;
        }
        ROSOutput.ensurePresent(c);
        bindRemoteOutputMenuItems();
        ROSOutput r = ROSOutput.find(c);
        if (r == null) {
            JOptionPane.showMessageDialog(this, "Could not create ROSOutput filter", "ROS2 / Foxglove",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        showRosOutputDialog(r);
    }

    private void initDnnSharedMemoryRemoteMenu() {
        dnnSharedMemoryMenuItem = new JMenuItem();
        dnnSharedMemoryMenuItem.setMnemonic('d');
        dnnSharedMemoryMenuItem.addActionListener(e -> dnnSharedMemoryMenuItemActionPerformed());
        remoteMenu.add(dnnSharedMemoryMenuItem);
        updateDnnSharedMemoryMenuItem();
    }

    private void updateDnnSharedMemoryMenuItem() {
        if (dnnSharedMemoryMenuItem == null) {
            return;
        }
        DNNOutputViaSharedMemory f = DNNOutputViaSharedMemory.find(getChip());
        boolean streaming = f != null && f.isFilterEnabled();
        dnnSharedMemoryMenuItem.setText(streaming
                ? "DNN shared memory output (streaming)..."
                : "DNN shared memory output (stopped)...");
        applyRemoteMenuStreamingStyle(dnnSharedMemoryMenuItem, streaming);
        updateRemoteMenuStreamingStyle();
    }

    private void bindRemoteOutputMenuItems() {
        ROSOutput ros = findRosOutput();
        if (rosOutputMenuBoundFilter != ros) {
            if (rosOutputMenuBoundFilter != null && rosOutputEnabledSync != null) {
                rosOutputMenuBoundFilter.getSupport().removePropertyChangeListener("filterEnabled", rosOutputEnabledSync);
            }
            rosOutputMenuBoundFilter = ros;
            if (ros != null) {
                if (rosOutputEnabledSync == null) {
                    rosOutputEnabledSync = evt -> SwingUtilities.invokeLater(this::updateRosOutputMenuItem);
                }
                ros.getSupport().addPropertyChangeListener("filterEnabled", rosOutputEnabledSync);
            }
        }
        updateRosOutputMenuItem();

        DNNOutputViaSharedMemory dnn = DNNOutputViaSharedMemory.find(getChip());
        if (dnnSharedMemoryMenuBoundFilter != dnn) {
            if (dnnSharedMemoryMenuBoundFilter != null && dnnSharedMemoryEnabledSync != null) {
                dnnSharedMemoryMenuBoundFilter.getSupport().removePropertyChangeListener("filterEnabled", dnnSharedMemoryEnabledSync);
            }
            dnnSharedMemoryMenuBoundFilter = dnn;
            if (dnn != null) {
                if (dnnSharedMemoryEnabledSync == null) {
                    dnnSharedMemoryEnabledSync = evt -> SwingUtilities.invokeLater(this::updateDnnSharedMemoryMenuItem);
                }
                dnn.getSupport().addPropertyChangeListener("filterEnabled", dnnSharedMemoryEnabledSync);
            }
        }
        updateDnnSharedMemoryMenuItem();

        OpenCVOutput opencv = findOpenCvOutput();
        if (openCvOutputMenuBoundFilter != opencv) {
            if (openCvOutputMenuBoundFilter != null && openCvOutputEnabledSync != null) {
                openCvOutputMenuBoundFilter.getSupport().removePropertyChangeListener("filterEnabled", openCvOutputEnabledSync);
            }
            openCvOutputMenuBoundFilter = opencv;
            if (opencv != null) {
                if (openCvOutputEnabledSync == null) {
                    openCvOutputEnabledSync = evt -> SwingUtilities.invokeLater(this::updateOpenCvOutputMenuItem);
                }
                opencv.getSupport().addPropertyChangeListener("filterEnabled", openCvOutputEnabledSync);
            }
        }
        updateOpenCvOutputMenuItem();
    }

    private void disposeDnnSharedMemoryDialog() {
        if (dnnSharedMemoryDialog != null) {
            dnnSharedMemoryDialog.dispose();
            dnnSharedMemoryDialog = null;
        }
    }

    private void showDnnSharedMemoryDialog(DNNOutputViaSharedMemory f) {
        if (dnnSharedMemoryDialog != null && dnnSharedMemoryDialog.getFilter() != f) {
            disposeDnnSharedMemoryDialog();
        }
        if (dnnSharedMemoryDialog == null) {
            dnnSharedMemoryDialog = new DNNOutputViaSharedMemoryDialog(this, f);
        }
        dnnSharedMemoryDialog.setVisible(true);
        dnnSharedMemoryDialog.toFront();
    }

    private void dnnSharedMemoryMenuItemActionPerformed() {
        AEChip c = getChip();
        if (c == null) {
            return;
        }
        DNNOutputViaSharedMemory.ensurePresent(c);
        bindRemoteOutputMenuItems();
        DNNOutputViaSharedMemory f = DNNOutputViaSharedMemory.find(c);
        if (f == null) {
            JOptionPane.showMessageDialog(this, "Could not create DNNOutputViaSharedMemory filter",
                    "DNN shared memory output", JOptionPane.ERROR_MESSAGE);
            return;
        }
        showDnnSharedMemoryDialog(f);
    }

    private void initOpenCvOutputRemoteMenu() {
        openCvOutputMenuItem = new JMenuItem();
        openCvOutputMenuItem.setMnemonic('c');
        openCvOutputMenuItem.addActionListener(e -> openCvOutputMenuItemActionPerformed());
        remoteMenu.add(openCvOutputMenuItem);
        updateOpenCvOutputMenuItem();
    }

    private void updateOpenCvOutputMenuItem() {
        if (openCvOutputMenuItem == null) {
            return;
        }
        OpenCVOutput o = findOpenCvOutput();
        boolean streaming = o != null && o.isFilterEnabled();
        openCvOutputMenuItem.setText(streaming
                ? "OpenCV camera output (streaming)..."
                : "OpenCV camera output (stopped)...");
        applyRemoteMenuStreamingStyle(openCvOutputMenuItem, streaming);
        updateRemoteMenuStreamingStyle();
    }

    /** Dark green for File → Remote items that are currently publishing. */
    private static final Color REMOTE_STREAMING_MENU_FOREGROUND = new Color(0, 100, 0);

    private static void applyRemoteMenuStreamingStyle(JMenuItem item, boolean streaming) {
        if (item == null) {
            return;
        }
        item.setForeground(streaming ? REMOTE_STREAMING_MENU_FOREGROUND : null);
    }

    private void updateRemoteMenuStreamingStyle() {
        ROSOutput ros = findRosOutput();
        DNNOutputViaSharedMemory dnn = DNNOutputViaSharedMemory.find(getChip());
        OpenCVOutput opencv = findOpenCvOutput();
        boolean anyStreaming = (ros != null && ros.isFilterEnabled())
                || (dnn != null && dnn.isFilterEnabled())
                || (opencv != null && opencv.isFilterEnabled());
        applyRemoteMenuStreamingStyle(remoteMenu, anyStreaming);
    }

    private OpenCVOutput findOpenCvOutput() {
        return OpenCVOutput.find(getChip());
    }

    private boolean isOpenCvOutputSkipChipRendering() {
        OpenCVOutput o = findOpenCvOutput();
        return o != null && o.isFilterEnabled() && o.isSkipChipRendering();
    }

    private void disposeOpenCvOutputDialog() {
        if (openCvOutputDialog != null) {
            openCvOutputDialog.dispose();
            openCvOutputDialog = null;
        }
    }

    private void showOpenCvOutputDialog(OpenCVOutput f) {
        if (openCvOutputDialog != null && openCvOutputDialog.getFilter() != f) {
            disposeOpenCvOutputDialog();
        }
        if (openCvOutputDialog == null) {
            openCvOutputDialog = new OpenCVOutputDialog(this, f);
        }
        openCvOutputDialog.expandFilterControls();
        openCvOutputDialog.setVisible(true);
        openCvOutputDialog.toFront();
    }

    private void openCvOutputMenuItemActionPerformed() {
        AEChip c = getChip();
        if (c == null) {
            return;
        }
        OpenCVOutput.ensurePresent(c);
        bindRemoteOutputMenuItems();
        OpenCVOutput f = OpenCVOutput.find(c);
        if (f == null) {
            JOptionPane.showMessageDialog(this, "Could not create OpenCVOutput filter",
                    "OpenCV camera output", JOptionPane.ERROR_MESSAGE);
            return;
        }
        showOpenCvOutputDialog(f);
    }

    /**
     * Preferred recording file format version ({@code "4.0"} or {@code "2.0"}).
     * Used by Start recording / {@code l} key; change via File/Preferences.
     */
    public String getRecordingDataFileVersion() {
        if (recordingDataFileVersion == null || recordingDataFileVersion.isEmpty()) {
            return AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4;
        }
        return recordingDataFileVersion;
    }

    /**
     * Validate a requested recording data-file version and return the value to
     * use, defaulting any unrecognised (or null/empty) input to AEDAT-4. This is
     * the exact decision {@link #setRecordingDataFileVersion(String)} applies;
     * extracted as a package-private static so the headless probe can verify the
     * selection/default without constructing an {@link AEViewer} (a JFrame).
     *
     * @param version the requested data-file version string
     * @return the accepted version, or {@link AEDataFile#DATA_FILE_VERSION_NUMBER_AEDAT4}
     */
    static String normalizeRecordingDataFileVersion(String version) {
        if (AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2.equals(version)
                || AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(version)
                || AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals(version)) {
            return version;
        }
        return AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4;
    }

    public void setRecordingDataFileVersion(String recordingDataFileVersion) {
        this.recordingDataFileVersion = normalizeRecordingDataFileVersion(recordingDataFileVersion);
        prefs.put("AEViewer.loggingDataFileVersion", this.recordingDataFileVersion);
    }

    /**
     * AEDAT-4 packet compression ({@link net.sf.jaer.eventio.aedat4.dv.CompressionType}).
     * Takes effect on the next Start recording.
     */
    public int getAedat4Compression() {
        return net.sf.jaer.eventio.aedat4.Aedat4Compression.clamp(aedat4Compression);
    }

    public void setAedat4Compression(int aedat4Compression) {
        this.aedat4Compression = net.sf.jaer.eventio.aedat4.Aedat4Compression.clamp(aedat4Compression);
        prefs.putInt("AEViewer.aedat4Compression", this.aedat4Compression);
    }

    /**
     * @return the chip we are displaying
     */
    public AEChip getChip() {
        return chip;
    }

    public void setChip(AEChip chip) {
        if (chip != this.chip) {
            this.chip = chip;

            getChip().setAeViewer(this);  // set this now so that chip has AEViewer for building BiasgenFrame etc properly
            extractor = chip.getEventExtractor();
            renderer = chip.getRenderer();
            filterChain = chip.getFilterChain();

            extractor.setSubsampleThresholdEventCount(getRenderer().getSubsampleThresholdEventCount()); // awkward connection between components here - ideally chip should contrain info about subsample limit
            if (chip.getFilterChain() != null) {
                chip.getFilterChain().initFilters(); // at this point AEChip is fully initialized, so asking all filters to initialize themselves makes sense
            }
        }
        bindRemoteOutputMenuItems();
    }

    public boolean isRenderBlankFramesEnabled() {
        return renderBlankFramesEnabled;
    }

    public void setRenderBlankFramesEnabled(boolean renderBlankFramesEnabled) {
        this.renderBlankFramesEnabled = renderBlankFramesEnabled;
        prefs.putBoolean("AEViewer.renderBlankFramesEnabled", renderBlankFramesEnabled);
        if (viewRenderBlankFramesCheckBoxMenuItem != null) {
            viewRenderBlankFramesCheckBoxMenuItem.setSelected(renderBlankFramesEnabled);
        }
    }

    public javax.swing.JMenu getFileMenu() {
        return fileMenu;
    }

    /**
     * used in CaviarViewer to control sync'ed recording
     */
    public javax.swing.JMenuItem getRecordingMenuItem() {
        return recordingMenuItem;
    }

    public void setRecordingMenuItem(javax.swing.JMenuItem recordingMenuItem) {
        this.recordingMenuItem = recordingMenuItem;
    }

    /**
     * this toggle button is used in CaviarViewer to assign an action to start
     * and stop recording for (possibly) all viewers
     */
    public javax.swing.JToggleButton getRecordingButton() {
        return recordingButton;
    }

    public void setRecordingButton(javax.swing.JToggleButton b) {
        recordingButton = b;
    }

    /**
     * Returns the current AE data recording file. Note that this file can change
     * if the user selects a different final file destination or name than the
     * original default one.
     *
     * @return the recordingFile
     */
    public File getRecordingFile() {
        return recordingFile;
    }

    public JCheckBoxMenuItem getSyncEnabledCheckBoxMenuItem() {
        return syncEnabledCheckBoxMenuItem;
    }

    public void setSyncEnabledCheckBoxMenuItem(javax.swing.JCheckBoxMenuItem syncEnabledCheckBoxMenuItem) {
        this.syncEnabledCheckBoxMenuItem = syncEnabledCheckBoxMenuItem;
    }

    /**
     * Returns the proper AbstractAEPlayer: either the local AEPlayer or the
     * delegated-to JAERViewer.SyncPlayer.
     *
     * @return the local player, unless we are part of a synchronized playback
     * gruop.
     */
    public AbstractAEPlayer getAePlayer() {
        if ((jaerViewer == null) || !jaerViewer.isSyncEnabled() || (jaerViewer.getViewers().size() == 1)) {
            return aePlayer;
        }

        return jaerViewer.getSyncPlayer();
    }

    /**
     * returns the playing mode
     *
     * @return the mode
     */
    public PlayMode getPlayMode() {
        return playMode;
    }

    /**
     * Sets mode, LIVE, PLAYBACK, WAITING, etc, sets window title, and fires
     * property change event
     *
     * @param playMode the new play mode
     */
    public void setPlayMode(PlayMode playMode) {
        // TODO there can be a race condition where user tries to open file, this sets
        // playMode to PLAYBACK but run() method in ViewLoop sets it back to WAITING or LIVE
        if (getPlayMode().equals(playMode)) {
            log.fine("setPlayMode(" + playMode + ") no-op (already)");
            // Re-open while already PLAYBACK must still wake ViewLoop and stop USB.
            if (playMode == PlayMode.PLAYBACK) {
                wakeViewLoopForPlayback();
                updateLiveAcquisitionForPlayMode(PlayMode.LIVE, PlayMode.PLAYBACK);
            }
            return;
        }
        final PlayMode oldMode = this.playMode;
        log.info("Changing PlayMode from " + this.playMode + " to " + playMode);
        log.fine("setPlayMode " + oldMode + " -> " + playMode
                + " thread=" + Thread.currentThread().getName()
                + " EDT=" + javax.swing.SwingUtilities.isEventDispatchThread()
                + " paused=" + isPaused());

        if (playMode == PlayMode.FILTER_INPUT) {
            setPlaybackControlsEnabledState(true); // tobi added to enable faster/slower for DavisTextInputReader
        }
        // playMode is volatile — do not synchronize on viewLoop here. ViewLoop may hold
        // viewLoop for a long time in openAEMonitor (USB), which deadlocked EDT file open.
        log.fine("setPlayMode: assigning playMode (no viewLoop lock)");
        this.playMode = playMode;
        if (isPaused()) {
            // Prefer notify over interrupt for PLAYBACK — interrupt closes FileChannel.
            if (playMode == PlayMode.PLAYBACK) {
                log.fine("setPlayMode: notify pause wait (PLAYBACK, no interrupt)");
                synchronized (viewLoopPauseLock) {
                    viewLoopPauseLock.notifyAll();
                }
            } else {
                log.fine("setPlayMode: interruptViewloop (paused)");
                interruptViewloop();
                synchronized (viewLoopPauseLock) {
                    viewLoopPauseLock.notifyAll();
                }
            }
        }
        log.fine("setPlayMode: updateAdaptiveRenderSkipping");
        updateAdaptiveRenderSkippingForPlayMode(oldMode, playMode);
        log.fine("setPlayMode: updateLiveAcquisition");
        updateLiveAcquisitionForPlayMode(oldMode, playMode);
        log.fine("setPlayMode: setTitleAccordingToState");
        setTitleAccordingToState();
        log.fine("setPlayMode: fixRecordingControls");
        fixRecordingControls();
        log.fine("setPlayMode: fire EVENT_PLAYMODE");
        getSupport().firePropertyChange(EVENT_PLAYMODE, oldMode.toString(), playMode.toString());
        log.fine("setPlayMode complete");
    }

    public boolean isRecordFilteredEventsEnabled() {
        return recordFilteredEventsEnabled;
    }

    public void setRecordFilteredEventsEnabled(boolean recordFilteredEventsEnabled) {
        //        log.info("recordFilteredEventsEnabled="+recordFilteredEventsEnabled);
        this.recordFilteredEventsEnabled = recordFilteredEventsEnabled;
        prefs.putBoolean("AEViewer.logFilteredEventsEnabled", recordFilteredEventsEnabled);
        recordFilteredEventsCheckBoxMenuItem.setSelected(recordFilteredEventsEnabled);
    }

    /**
     * Returns the enclosing JAERViewer, which is the top level object in jAER.
     *
     * @return the top-level owner
     */
    public JAERViewer getJaerViewer() {
        return jaerViewer;
    }

    /** In-app console text for issue reports; empty if the console is unavailable. */
    public String getConsoleText() {
        if (loggingHandler == null || loggingHandler.getConsoleWindow() == null) {
            return "";
        }
        return loggingHandler.getConsoleWindow().getText();
    }

    public void setJaerViewer(JAERViewer jaerViewer) {
        this.jaerViewer = jaerViewer;
    }

    /**
     * Finds the top-level menu named s.
     *
     * @param s the text of the menu. If null, returns null.
     * @return the menu, if there is one, or null if not found.
     */
    public JMenu getMenu(String s) {
        if (s == null) {
            return null;
        }
        JMenuBar b = getJMenuBar();
        int n = b.getMenuCount();
        for (int i = 0; i < n; i++) {
            JMenu m = b.getMenu(i);
            if (m.getText().equals(s)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Adds (or replaces existing) JMenu to AEViewer, just before the Help menu.
     *
     * @param menu the menu
     * @see #removeMenu(javax.swing.JMenu)
     */
    public void addMenu(JMenu menu) {
        JMenuBar b = getJMenuBar();
        int n = b.getMenuCount();
        // check for existing and replace
        for (int i = 0; i < n; i++) {
            JMenu m = b.getMenu(i);
            if ((m != null) && m.getText().equals(menu.getText())) {
                b.remove(i);
                b.add(menu, i);
                return;
            }
        }
        // otherwise appendOfEventReferences just before Help menu
        boolean didit = false;
        for (int i = 0; i < n; i++) {
            JMenu m = b.getMenu(i);
            if ((m != null) && m.getText().equals("Help")) {
                b.add(menu, i);
                didit = true;
            }
        }
        if (!didit) { // if no help menu, appendOfEventReferences to end
            b.add(menu, -1);
        }
    }

    /**
     * Removes existing JMenu in AEViewer.
     *
     * @param menu the menu
     * @see #addMenu(javax.swing.JMenu)
     */
    public void removeMenu(JMenu menu) {
        JMenuBar b = getJMenuBar();
        b.remove(menu);
    }

    /**
     * TCP AE stream sockets are no longer started by AEViewer; kept so existing
     * callers still compile.
     *
     * @return always {@code null}
     */
    public AESocket getAeSocket() {
        return null;
    }

    /**
     * gets the RecentFiles handler for use, e.g. in storing synchronized recording
     * index files
     *
     * @return refernce to RecentFiles object
     */
    public RecentFiles getRecentFiles() {
        return recentFiles;
    }

    /**
     * @return the renderer
     */
    protected AEChipRenderer getRenderer() {
        if (chip == null) {
            throw new NullPointerException("chip instance is null; this should not happen. Something probably went wrong in the constructor. You can try to clear the preferences. To see earlier exceptions, run the launcher from a shell.");
        }
        return chip.getRenderer();
    }

    /**
     * This method takes in an hardware interface and tries to find the
     * appropriate chip class. You could use it before initializing an AEViewer.
     *
     * It will return null if it does not find the appropriate chipclassname.
     *
     * @return
     */
    public static Class hardwareInterface2chipClassName(HardwareInterface hw) {
        if (hw == null) {
            return null;
        }

        if (hw.toString().contains("DVS128")) {
            return ch.unizh.ini.jaer.chip.retina.DVS128.class;
        } else if (hw.toString().contains("Cochlea")) {
            return ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1b.class;
        } else if (hw.toString().contains("Retina")) {
            return ch.unizh.ini.jaer.chip.retina.DVS128.class;
        } else {
            JOptionPane.showConfirmDialog(null, "Unknown hardware, cannot find appropriate chip class.", "null hardware", JOptionPane.WARNING_MESSAGE);
            Logger.getAnonymousLogger().warning("Unknown hardware, can't find chip class.");
            return null;
        }
    }

    /**
     * @return the jaerUpdaterFrame
     */
    public JaerUpdaterFrame getJaerUpdaterFrame() {
        return jaerUpdaterFrame;
    }

    /**
     * @param jaerUpdaterFrame the jaerUpdaterFrame to set
     */
    public void setJaerUpdaterFrame(JaerUpdaterFrame jaerUpdaterFrame) {
        this.jaerUpdaterFrame = jaerUpdaterFrame;
    }

    /**
     * Zoom to center a particular point, with a zoom ratio. Can be used by
     * EventFilter that manipulates the view.
     *
     * @param pixel the pixel to center
     * @param zoomFactor the zoom ratio, 1 for unzoomed, >1 for zoomed in by
     * some factor
     */
    public void zoomToCenter(Point pixel, float zoomFactor) {
        getChip().getCanvas().zoomToCenter(pixel, zoomFactor);
    }

    /**
     * Remove any zoom
     */
    public void unzoom() {
        if (getChip().getCanvas() != null) {
            getChip().getCanvas().unzoom();
            repaint();
        }
    }

    public boolean isZoomed() {
        if (getChip().getCanvas() != null) {
            return getChip().getCanvas().isZoomed();
        } else {
            return false;
        }
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutMenuItem;
    private javax.swing.JCheckBoxMenuItem acccumulateImageEnabledCheckBoxMenuItem;
    private javax.swing.JToggleButton biasesToggleButton;
    private javax.swing.JPanel bottomPanel;
    private javax.swing.JPanel buttonsPanel;
    private javax.swing.JMenuItem releaseNotesMenuItem;
    private javax.swing.JMenuItem checkForUpdatesMenuItem;
    private javax.swing.JCheckBoxMenuItem checkNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem;
    private javax.swing.JMenuItem clearMarksMI;
    private javax.swing.JMenuItem closeMenuItem;
    private javax.swing.JMenuItem showFileInfoMenuItem;
    private javax.swing.JMenuItem saveAsMenuItem;
    private javax.swing.JMenuItem exportVideoMenuItem;
    private javax.swing.JMenuItem stopVideoExportMenuItem;
    private javax.swing.JMenu controlMenu;
    private javax.swing.JMenuItem customizeDevicesMenuItem;
    private javax.swing.JMenuItem cycleDisplayMethodButton;
    private javax.swing.JMenuItem cycleNextColorRenderingMethodMenuItem;
    private javax.swing.JMenuItem cyclePreviousColorRenderingMethodMenuItem;
    private javax.swing.JMenuItem usbTuningMenuItem;
    private javax.swing.JMenuItem decreaseContrastMenuItem;
    private javax.swing.JMenuItem decreaseFrameRateMenuItem;
    private javax.swing.JMenuItem decreasePlaybackSpeedMenuItem;
    private javax.swing.JMenu deviceMenu;
    private javax.swing.JSeparator deviceMenuSpparator;
    private javax.swing.JMenu displayMethodMenu;
    private javax.swing.JCheckBoxMenuItem enableFiltersOnStartupCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem enableMissedEventsCheckBox;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JSeparator exitSeperator;
    private javax.swing.JMenuItem preferencesMenuItem;
    private javax.swing.JMenuItem exportMarksMI;
    private javax.swing.JCheckBoxMenuItem fadingMI;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JToggleButton filtersToggleButton;
    private javax.swing.JCheckBoxMenuItem flextimePlaybackEnabledCheckBoxMenuItem;
    private javax.swing.JMenuItem gitUpdateMenuItem;
    private javax.swing.JMenu graphicsSubMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JPanel imagePanel;
    private javax.swing.JMenuItem importMarksMI;
    private javax.swing.JMenuItem increaseContrastMenuItem;
    private javax.swing.JMenuItem increaseFrameRateMenuItem;
    private javax.swing.JMenuItem increasePlaybackSpeedMenuItem;
    private javax.swing.JMenu interfaceMenu;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator10;
    private javax.swing.JPopupMenu.Separator jSeparator11;
    private javax.swing.JSeparator jSeparator12;
    private javax.swing.JSeparator jSeparator13;
    private javax.swing.JSeparator jSeparator16;
    private javax.swing.JPopupMenu.Separator jSeparator17;
    private javax.swing.JPopupMenu.Separator jSeparator18;
    private javax.swing.JPopupMenu.Separator jSeparator19;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator20;
    private javax.swing.JSeparator jSeparator21;
    private javax.swing.JSeparator jSeparator22;
    private javax.swing.JPopupMenu.Separator jSeparator23;
    private javax.swing.JPopupMenu.Separator jSeparator24;
    private javax.swing.JPopupMenu.Separator jSeparator25;
    private javax.swing.JPopupMenu.Separator jSeparator26;
    private javax.swing.JPopupMenu.Separator jSeparator28;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JPopupMenu.Separator jSeparator6;
    private javax.swing.JSeparator jSeparator7;
    private javax.swing.JSeparator jSeparator8;
    private javax.swing.JMenuItem cancelJogMI;
    private javax.swing.JMenuItem jogBackwardsMI;
    private javax.swing.JMenuItem jogForwardMI;
    private javax.swing.JMenuItem jumpNextMarkerMI;
    private javax.swing.JMenuItem jumpPrevMarkerMI;
    private javax.swing.JCheckBoxMenuItem recordFilteredEventsCheckBoxMenuItem;
    private javax.swing.JToggleButton recordingButton;
    private javax.swing.JMenu loggingLevelMenu;
    private javax.swing.JMenuItem recordingMenuItem;
    private javax.swing.JCheckBoxMenuItem recordingPlaybackImmediatelyCheckBoxMenuItem;
    private javax.swing.JMenuItem recordingSetTimelimitMenuItem;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenu monSeqMenu;
    private javax.swing.JMenuItem monSeqMissedEventsMenuItem;
    private javax.swing.JRadioButtonMenuItem monSeqOpMode0;
    private javax.swing.JRadioButtonMenuItem monSeqOpMode1;
    private javax.swing.ButtonGroup monSeqOpModeButtonGroup;
    private javax.swing.JMenu monSeqOperationModeMenu;
    private javax.swing.JSeparator networkSeparator;
    private javax.swing.JMenuItem newViewerMenuItem;
    private javax.swing.JCheckBoxMenuItem openBlockingQueueInputMenuItem;
    private javax.swing.JCheckBoxMenuItem blockingQueueOutputEnabledCheckBoxMenuItem;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JMenuItem openUnicastInputMenuItem;
    private javax.swing.JCheckBoxMenuItem pauseRenderingCheckBoxMenuItem;
    private javax.swing.JMenu playbackMenu;
    private javax.swing.JPanel playerControlPanel;
    private javax.swing.JCheckBoxMenuItem printUSBStatisticsCBMI;
    private javax.swing.JMenuItem refreshInterfaceMenuItem;
    private javax.swing.JMenu remoteMenu;
    private javax.swing.ButtonGroup renderModeButtonGroup;
    private javax.swing.JMenuItem renewChipMI;
    private javax.swing.JMenuItem resetAccumulationMenuItem;
    private javax.swing.JLabel resizeLabel;
    private javax.swing.JPanel resizePanel;
    private javax.swing.JMenuItem rewindPlaybackMenuItem;
    private javax.swing.JMenuItem sequenceMenuItem;
    private javax.swing.JMenuItem setBorderSpaceMenuItem;
    private javax.swing.JMenuItem setFrameRateMenuItem;
    private javax.swing.JMenuItem setJogNCount;
    private javax.swing.JMenuItem setMarkInMI;
    private javax.swing.JMenuItem setMarkOutMI;
    private javax.swing.JButton showConsoleOutputButton;
    private javax.swing.JMenuItem showRenderingModeMI;
    private ScrollWheelTunableCheckBoxMenuItem skipPacketsRenderingCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem slidingMI;
    private javax.swing.JPanel statisticsPanel;
    private javax.swing.JTextField statusTextField;
    private javax.swing.JCheckBoxMenuItem syncEnabledCheckBoxMenuItem;
    private javax.swing.JSeparator syncSeperator;
    private javax.swing.JMenuItem timestampResetBitmaskMenuItem;
    private javax.swing.JMenuItem toggleMarkerMI;
    private javax.swing.JMenuItem togglePlaybackDirectionMenuItem;
    private javax.swing.JCheckBoxMenuItem unicastOutputEnabledCheckBoxMenuItem;
    private javax.swing.JMenuItem unzoomMenuItem;
    private javax.swing.JCheckBoxMenuItem viewActiveRenderingEnabledMenuItem;
    private javax.swing.JMenuItem viewBiasesMenuItem;
    private javax.swing.JMenuItem viewFiltersMenuItem;
    private javax.swing.JCheckBoxMenuItem viewIgnorePolarityCheckBoxMenuItem;
    private javax.swing.JMenu viewMenu;
    private javax.swing.JCheckBoxMenuItem viewRenderBlankFramesCheckBoxMenuItem;
    private javax.swing.JMenuItem viewStepBackwardsMI;
    private javax.swing.JMenuItem viewStepForwardsMI;
    private javax.swing.JMenuItem zeroTimestampsMenuItem;
    private javax.swing.JMenuItem resetUsbInterfaceMenuItem;
    private javax.swing.JMenuItem zoomInMenuItem;
    private javax.swing.JMenuItem zoomOutMenuItem;
    // End of variables declaration//GEN-END:variables

}
