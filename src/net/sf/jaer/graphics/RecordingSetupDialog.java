package net.sf.jaer.graphics;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.RecordingFilename;
import net.sf.jaer.eventio.aedat4.Aedat4Compression;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.util.FileAccessTimeout;
import net.sf.jaer.util.OutputFilename;
import net.sf.jaer.util.RecentFoldersJumpCombo;
import net.sf.jaer.util.ShowFolderSaveConfirmation;

/**
 * Start-recording prompt for folder, filename, format, AEDAT-4 compression,
 * File → Enable filtering of recorded events (with the same active-filter
 * confirmation as Save As), and a session time limit (default none). Shown for
 * the first {@link #MAX_AUTO_SHOWS} recordings this JVM, whenever a non-zero
 * time limit is set, whenever VCR (multiple cassettes) is on, and whenever the
 * user chooses File → Start recording (not the toolbar button or {@code L}).
 * Enter activates {@code Start Recording}. Confirmed format, compression,
 * filtering, and folder are written back to AEViewer prefs. The time limit and
 * VCR mode are sticky for this JVM; last-used values also persist in this
 * class's hidden Preferences and refill the timed section when it is expanded
 * (not applied on jAER launch, so toolbar/{@code L} stay unlimited).
 */
public final class RecordingSetupDialog extends JDialog implements PropertyChangeListener {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger("net.sf.jaer");

    /** Unlimited toolbar/{@code L} starts after this many accepted setup dialogs. */
    public static final int MAX_AUTO_SHOWS = 3;
    private static final AtomicInteger acceptedShowCount = new AtomicInteger(0);
    private static final AtomicLong sessionTimeLimitMs = new AtomicLong(0L);
    private static final AtomicReference<RecordingVcrSession.Mode> stickyVcrMode
            = new AtomicReference<>(RecordingVcrSession.Mode.OFF);
    private static final AtomicInteger stickyRotateKeep
            = new AtomicInteger(RecordingVcrSession.ROTATE_DEFAULT);
    private static final Preferences LAST_TIMED_PREFS
            = Preferences.userNodeForPackage(RecordingSetupDialog.class);
    private static final String PREF_TIME_LIMIT_MS = "timeLimitMs";
    private static final String PREF_VCR_MODE = "vcrMode";
    private static final String PREF_ROTATE_KEEP = "rotateKeep";
    private static final String PREF_WRITTEN = "written";
    private static final String[] FORMAT_LABELS = {
        "AEDAT-4 (.aedat4)",
        "AEDAT-2 (.aedat2)",
        "AEDZ compressed AEDAT-2 (.aedz)"
    };
    private static final String[] COMPRESSION_LABELS = {
        "None",
        "LZ4 (recommended)",
        "LZ4 high",
        "ZSTD",
        "ZSTD high"
    };

    private final AEViewer host;
    private final List<AEViewer> applyTargets;
    private final boolean muxMany;
    private final Date stamp = new Date();

    private static final String VCR_INFINITE = "Infinite";
    private static final String VCR_FINITE = "Finite";
    private static final String VCR_ROTATE = "Rotate";

    private final JLabel nameLabel = new JLabel("File name:");
    private final JTextField nameField = new JTextField(36);
    private final JComboBox<String> formatCombo = new JComboBox<>(FORMAT_LABELS);
    private final JComboBox<String> compressionCombo = new JComboBox<>(COMPRESSION_LABELS);
    private final JComboBox<String> timeLimitPreset = new JComboBox<>(RecordingTimeLimit.PRESETS);
    private final JTextField timeLimitField = new JTextField(16);
    private final JCheckBox vcrCb = new JCheckBox("VCR (multiple files)");
    private final JComboBox<String> vcrKindCombo = new JComboBox<>(new String[] {
        VCR_INFINITE, VCR_FINITE, VCR_ROTATE});
    private final JLabel rotateKeepLabel = new JLabel("Keep last");
    private final JSpinner rotateKeepSpinner = new JSpinner(new SpinnerNumberModel(
            RecordingVcrSession.ROTATE_DEFAULT,
            RecordingVcrSession.ROTATE_MIN,
            RecordingVcrSession.ROTATE_MAX,
            1));
    private final JCheckBox recordFilteredCb = new JCheckBox(
            "Enable filtering of recorded or network output events");
    private final JPanel filterSummaryPanel = new JPanel(new BorderLayout(6, 0));
    private final JLabel filterSummaryLabel = new JLabel();
    private final JButton timedSectionToggle = new JButton();
    private final JLabel timedCollapsedSummary = new JLabel();
    private JPanel timedBody;
    private boolean timedSectionExpanded;
    private boolean timedPrefsAppliedThisDialog;
    private RecentFoldersJumpCombo folderCombo;
    private File folder;
    private boolean accepted;
    private boolean updatingUi;
    private boolean nameAsSessionFolder;
    private final Map<AEViewer, Boolean> recordFilteredAtOpen = new HashMap<>();
    private final List<EventFilter2D> filterEnabledListenTargets = new ArrayList<>();
    private FilterChain filterEnabledListenChain;

    private RecordingSetupDialog(AEViewer host, List<AEViewer> muxViewers) {
        super(host, "Start recording", ModalityType.APPLICATION_MODAL);
        this.host = host;
        this.applyTargets = muxViewers != null && !muxViewers.isEmpty()
                ? new ArrayList<>(muxViewers)
                : List.of(host);
        this.muxMany = applyTargets.size() > 1;
        this.folder = host.getLastRecordingFolder() != null
                ? host.getLastRecordingFolder()
                : new File(".");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if (host.getIconImage() != null) {
            setIconImage(host.getIconImage());
        }
        buildUi();
        loadFromViewer();
        for (AEViewer v : applyTargets) {
            if (v != null) {
                recordFilteredAtOpen.put(v, v.isRecordFilteredEventsEnabled());
            }
        }
        host.getSupport().addPropertyChangeListener(AEViewer.EVENT_RECORD_FILTERED_EVENTS, this);
        bindFilterEnabledListeners();
        pack();
        setLocationRelativeTo(host);
    }

    /**
     * Show the setup dialog when policy requires it. Cancel returns
     * {@code false} and does not start recording. Headless: skip and proceed.
     *
     * @param host viewer that owns the dialog (and pending filename)
     * @param muxViewers synchronized cameras, or {@code null} for one viewer
     * @param forceFromFileMenu true when File → Start recording was chosen with
     * the mouse (not {@code L} and not the toolbar button)
     */
    public static boolean confirmIfNeeded(AEViewer host, List<AEViewer> muxViewers,
            boolean forceFromFileMenu) {
        if (!shouldShow(acceptedShowCount.get(), sessionTimeLimitMs.get(), forceFromFileMenu,
                isSessionVcrEnabled())) {
            applySessionLimit(host, muxViewers);
            return true;
        }
        if (host == null || java.awt.GraphicsEnvironment.isHeadless()) {
            acceptedShowCount.set(MAX_AUTO_SHOWS);
            applySessionLimit(host, muxViewers);
            return true;
        }
        boolean[] ok = {false};
        Runnable show = () -> ok[0] = showModal(host, muxViewers);
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(show);
            } catch (Exception e) {
                return false;
            }
        }
        return ok[0];
    }

    public static boolean confirmIfNeeded(AEViewer host, boolean forceFromFileMenu) {
        return confirmIfNeeded(host, null, forceFromFileMenu);
    }

    /**
     * Toolbar/{@code L} path: show for the first {@link #MAX_AUTO_SHOWS} starts
     * and whenever a time limit is set. File menu always shows. VCR is treated
     * as off.
     */
    public static boolean shouldShow(int acceptedShows, long sessionLimitMs, boolean forceFromFileMenu) {
        return shouldShow(acceptedShows, sessionLimitMs, forceFromFileMenu, false);
    }

    /**
     * Same as {@link #shouldShow(int, long, boolean)} but also show whenever
     * VCR (multiple cassettes) is enabled.
     */
    public static boolean shouldShow(int acceptedShows, long sessionLimitMs, boolean forceFromFileMenu,
            boolean vcrEnabled) {
        if (forceFromFileMenu) {
            return true;
        }
        if (sessionLimitMs > 0L) {
            return true;
        }
        if (vcrEnabled) {
            return true;
        }
        return acceptedShows < MAX_AUTO_SHOWS;
    }

    /** Sticky VCR mode for this JVM ({@link RecordingVcrSession.Mode#OFF} by default). */
    public static RecordingVcrSession.Mode sessionVcrMode() {
        return stickyVcrMode.get();
    }

    /** Sticky rotate keep-count for this JVM. */
    public static int sessionRotateKeep() {
        return stickyRotateKeep.get();
    }

    public static boolean isSessionVcrEnabled() {
        return stickyVcrMode.get() != RecordingVcrSession.Mode.OFF;
    }

    /**
     * Sticky VCR choice for this JVM. {@link RecordingVcrSession.Mode#OFF} is
     * the default (single-file timed recording).
     */
    public static void setSessionVcr(RecordingVcrSession.Mode mode, int rotateKeep) {
        stickyVcrMode.set(mode == null ? RecordingVcrSession.Mode.OFF : mode);
        stickyRotateKeep.set(RecordingVcrSession.clampRotateKeep(rotateKeep));
    }

    /**
     * Last Start values. Not loaded into JVM-sticky on launch; used when the
     * user expands Timed recording / VCR.
     */
    static final class LastTimed {
        final long timeLimitMs;
        final RecordingVcrSession.Mode vcrMode;
        final int rotateKeep;

        LastTimed(long timeLimitMs, RecordingVcrSession.Mode vcrMode, int rotateKeep) {
            this.timeLimitMs = Math.max(0L, timeLimitMs);
            this.vcrMode = vcrMode == null ? RecordingVcrSession.Mode.OFF : vcrMode;
            this.rotateKeep = RecordingVcrSession.clampRotateKeep(rotateKeep);
        }
    }

    /** Write on accepted Start only (including No limit / VCR off). Cancel does not write. */
    static void persistLastTimedPrefs(long timeLimitMs, RecordingVcrSession.Mode mode, int rotateKeep) {
        LastTimed last = new LastTimed(timeLimitMs, mode, rotateKeep);
        LAST_TIMED_PREFS.putLong(PREF_TIME_LIMIT_MS, last.timeLimitMs);
        LAST_TIMED_PREFS.put(PREF_VCR_MODE, last.vcrMode.name());
        LAST_TIMED_PREFS.putInt(PREF_ROTATE_KEEP, last.rotateKeep);
        LAST_TIMED_PREFS.putBoolean(PREF_WRITTEN, true);
        try {
            LAST_TIMED_PREFS.flush();
        } catch (BackingStoreException e) {
            log.warning("could not save last timed recording prefs: " + e);
        }
    }

    static boolean lastTimedPrefsWritten() {
        return LAST_TIMED_PREFS.getBoolean(PREF_WRITTEN, false);
    }

    static LastTimed lastTimedPrefs() {
        long ms = LAST_TIMED_PREFS.getLong(PREF_TIME_LIMIT_MS, 0L);
        RecordingVcrSession.Mode mode;
        try {
            mode = RecordingVcrSession.parseMode(LAST_TIMED_PREFS.get(PREF_VCR_MODE, "OFF"));
        } catch (IllegalArgumentException e) {
            mode = RecordingVcrSession.Mode.OFF;
        }
        int keep = LAST_TIMED_PREFS.getInt(PREF_ROTATE_KEEP, RecordingVcrSession.ROTATE_DEFAULT);
        return new LastTimed(ms, mode, keep);
    }

    /**
     * Prefs if this JVM has written them; otherwise the newest {@code vcr-session.txt}
     * under {@code searchFolder} (last recording parent).
     */
    static LastTimed lastTimedForRestore(File searchFolder) {
        if (lastTimedPrefsWritten()) {
            return lastTimedPrefs();
        }
        LastTimed deck = lastTimedFromNewestDeck(searchFolder);
        return deck != null ? deck : lastTimedPrefs();
    }

    static LastTimed lastTimedFromNewestDeck(File parent) {
        File man = newestVcrManifest(parent);
        if (man == null) {
            return null;
        }
        try {
            RecordingVcrSession s = RecordingVcrSession.readManifest(man.getParentFile());
            return new LastTimed(s.getCassetteDurationMs(), s.getMode(), s.getRotateKeep());
        } catch (Exception e) {
            log.fine("could not read last VCR deck prefs from " + man + ": " + e);
            return null;
        }
    }

    static File newestVcrManifest(File parent) {
        if (parent == null || !parent.isDirectory()) {
            return null;
        }
        File best = null;
        long bestM = Long.MIN_VALUE;
        File here = new File(parent, RecordingVcrSession.MANIFEST_NAME);
        if (here.isFile()) {
            best = here;
            bestM = here.lastModified();
        }
        File[] kids = parent.listFiles();
        if (kids == null) {
            return best;
        }
        for (File k : kids) {
            if (k == null || !k.isDirectory()) {
                continue;
            }
            File m = new File(k, RecordingVcrSession.MANIFEST_NAME);
            if (m.isFile() && m.lastModified() >= bestM) {
                best = m;
                bestM = m.lastModified();
            }
        }
        return best;
    }

    /**
     * Prefs refill the expand-open controls when this JVM has not yet accepted
     * a timed or VCR Start.
     */
    static boolean shouldRestoreLastTimedFromPrefs() {
        return sessionTimeLimitMs.get() <= 0L && !isSessionVcrEnabled();
    }

    /** VCR is AEDAT-4 only and needs a positive cassette length (the time limit). */
    public static boolean vcrAvailable(String version, long cassetteDurationMs) {
        return AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(version) && cassetteDurationMs > 0L;
    }

    /**
     * True when Start recording came from a File menu click, not {@code L} and
     * not the toolbar button. Accelerator on the same {@link JMenuItem} still
     * delivers that item as {@link ActionEvent#getSource()}.
     */
    public static boolean isExplicitFileMenuStart(ActionEvent e) {
        if (e == null) {
            return false;
        }
        AWTEvent ev = EventQueue.getCurrentEvent();
        if (ev instanceof KeyEvent) {
            return false;
        }
        return e.getSource() instanceof JMenuItem;
    }

    /** Test hook: next starts can show the dialog again. */
    static void resetShownThisJvmForTests() {
        acceptedShowCount.set(0);
        sessionTimeLimitMs.set(0L);
        stickyVcrMode.set(RecordingVcrSession.Mode.OFF);
        stickyRotateKeep.set(RecordingVcrSession.ROTATE_DEFAULT);
    }

    static boolean wasShownThisJvm() {
        return acceptedShowCount.get() > 0;
    }

    static long sessionTimeLimitMsForTests() {
        return sessionTimeLimitMs.get();
    }

    private static void applySessionLimit(AEViewer host, List<AEViewer> muxViewers) {
        long ms = sessionTimeLimitMs.get();
        List<AEViewer> targets = muxViewers != null && !muxViewers.isEmpty()
                ? muxViewers
                : (host != null ? List.of(host) : List.of());
        for (AEViewer v : targets) {
            if (v != null) {
                v.applyRecordingTimeLimit(ms);
            }
        }
    }

    /**
     * Force {@code name} to the extension for {@code version} (replace any other
     * data-file suffix).
     */
    static String withFormatExtension(String name, String version) {
        String ext = AEDataFile.extensionForVersion(version);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        return OutputFilename.ensureExtensionName(name, ext, ext);
    }

    static File proposedFile(AEViewer host, List<AEViewer> muxViewers, String version, Date when, File folder) {
        File dir = folder != null ? folder : (host != null ? host.getLastRecordingFolder() : new File("."));
        String ext = AEDataFile.extensionForVersion(version);
        Date stamp = when != null ? when : new Date();
        if (muxViewers != null && muxViewers.size() > 1
                && AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(version)) {
            List<RecordingFilename.DeviceToken> tokens = new ArrayList<>();
            for (AEViewer v : muxViewers) {
                tokens.add(RecordingFilename.tokenFromChip(v != null ? v.getChip() : null));
            }
            return RecordingFilename.uniqueFile(dir, RecordingFilename.muxedAedat4Base(tokens, stamp), ext);
        }
        return RecordingFilename.uniqueFile(dir,
                RecordingFilename.singleCameraBase(host != null ? host.getChip() : null, stamp), ext);
    }

    private static boolean showModal(AEViewer host, List<AEViewer> muxViewers) {
        RecordingSetupDialog d = new RecordingSetupDialog(host, muxViewers);
        KeyEventDispatcher strayL = installStrayLGuard(d);
        try {
            d.setVisible(true);
        } finally {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(strayL);
        }
        log.info("recording setup dialog closed accepted=" + d.accepted
                + " alreadyRecording=" + (host != null && host.isRecordingEnabled()));
        return d.accepted;
    }

    private static KeyEventDispatcher installStrayLGuard(Window dialog) {
        final long openMs = System.currentTimeMillis();
        KeyEventDispatcher dispatcher = event -> {
            if (System.currentTimeMillis() - openMs > RecordingSaveDialogGuard.STRAY_KEY_GUARD_MS) {
                return false;
            }
            int id = event.getID();
            if (id != KeyEvent.KEY_TYPED && id != KeyEvent.KEY_PRESSED && id != KeyEvent.KEY_RELEASED) {
                return false;
            }
            boolean isL = (id == KeyEvent.KEY_TYPED)
                    ? (event.getKeyChar() == 'l' || event.getKeyChar() == 'L')
                    : (event.getKeyCode() == KeyEvent.VK_L);
            return isL && dialog.isVisible();
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        return dispatcher;
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(nameLabel, c);
        c.gridx = 1;
        c.weightx = 1;
        nameField.setToolTipText("<html>Same chip + date name as Start recording / Save recorded data.<br>"
                + "Folder is the row below. Paste a full path to split folder and name.</html>");
        form.add(nameField, c);
        c.gridx = 2;
        c.weightx = 0;
        JButton browse = new JButton("Browse…");
        browse.addActionListener(this::browseFolder);
        form.add(browse, c);

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel("Folder:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        folderCombo = new RecentFoldersJumpCombo(host.getRecentFiles(), () -> folder, this::setFolder);
        folderCombo.setToolTipText("Next recording folder (AEViewer prefs / last Save recorded data)");
        form.add(folderCombo, c);
        c.gridwidth = 1;

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel("Format:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        formatCombo.setToolTipText("<html>File format used by Start recording (same as File → Preferences).<br>"
                + "AEDZ stores polarity events only.</html>");
        formatCombo.addActionListener(e -> {
            if (updatingUi) {
                return;
            }
            updateCompressionEnabled();
            updateVcrControls();
            if (!vcrUiActive()) {
                updateNameExtension();
            }
        });
        form.add(formatCombo, c);
        c.gridwidth = 1;

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel("AEDAT-4 compression:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        compressionCombo.setToolTipText("<html>DV-compatible per-packet compression (same as File → Preferences).<br>"
                + "LZ4 is best for live recording.</html>");
        form.add(compressionCombo, c);
        c.gridwidth = 1;

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        c.weightx = 1;
        JPanel filterSection = new JPanel(new BorderLayout(6, 4));
        filterSection.setBorder(BorderFactory.createTitledBorder("Filtering"));
        recordFilteredCb.setToolTipText("<html>Same as File → Enable filtering of recorded or network output events.<br>"
                + "Checked: recording writes the filter-chain output (events marked filteredOut are omitted).<br>"
                + "Unchecked: record the raw stream as received.</html>");
        recordFilteredCb.addActionListener(e -> {
            if (updatingUi) {
                return;
            }
            applyRecordFilteredToTargets(recordFilteredCb.isSelected());
            updateFilterSummary();
        });
        JButton showFiltersBtn = new JButton("Show filters");
        showFiltersBtn.setToolTipText("Open the Filters window (same as View → Show filters) to enable and configure EventFilters.");
        showFiltersBtn.addActionListener(e -> openFiltersWindow());
        JPanel filterNorth = new JPanel(new BorderLayout(8, 0));
        filterNorth.add(recordFilteredCb, BorderLayout.CENTER);
        filterNorth.add(showFiltersBtn, BorderLayout.EAST);
        filterSection.add(filterNorth, BorderLayout.NORTH);
        filterSummaryLabel.setVerticalAlignment(JLabel.TOP);
        filterSummaryPanel.add(filterSummaryLabel, BorderLayout.CENTER);
        filterSection.add(filterSummaryPanel, BorderLayout.CENTER);
        form.add(filterSection, c);
        c.gridwidth = 1;

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        JLabel hint = new JLabel("<html><i>First " + MAX_AUTO_SHOWS
                + " recordings this session, File → Start recording, any timed recording, or VCR. "
                + "Enter starts. Format, compression, filtering, and folder become AEViewer prefs.</i></html>");
        form.add(hint, c);

        row++;
        c.gridy = row;
        form.add(new JSeparator(), c);

        row++;
        c.gridy = row;
        timedSectionToggle.setBorderPainted(false);
        timedSectionToggle.setContentAreaFilled(false);
        timedSectionToggle.setFocusPainted(false);
        timedSectionToggle.setHorizontalAlignment(JButton.LEFT);
        timedSectionToggle.setMargin(new Insets(0, 0, 0, 0));
        timedSectionToggle.setToolTipText("Time limit and optional VCR (multiple cassette files). Last Start values refill when you expand this section.");
        timedSectionToggle.addActionListener(e -> setTimedSectionExpanded(!timedSectionExpanded));
        timedCollapsedSummary.setFont(timedCollapsedSummary.getFont().deriveFont(java.awt.Font.ITALIC));
        JPanel timedHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        timedHeader.add(timedSectionToggle);
        timedHeader.add(timedCollapsedSummary);
        form.add(timedHeader, c);

        row++;
        c.gridy = row;
        timedBody = buildTimedBody();
        timedBody.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                restoreLastTimedSettingsIfNeeded();
            }
        });
        timedBody.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                    && timedBody.isShowing()) {
                restoreLastTimedSettingsIfNeeded();
            }
        });
        form.add(timedBody, c);
        c.gridwidth = 1;

        JButton start = new JButton("Start Recording");
        start.addActionListener(e -> acceptAndClose());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(start);
        buttons.add(cancel);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(start);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelRecordingSetup");
        getRootPane().getActionMap().put("cancelRecordingSetup", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                start.requestFocusInWindow();
            }
        });
    }

    private JPanel buildTimedBody() {
        JPanel body = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        body.add(new JLabel("Time limit:"), c);
        c.gridx = 1;
        c.weightx = 1;
        timeLimitPreset.setMaximumRowCount(RecordingTimeLimit.PRESETS.length);
        timeLimitPreset.setToolTipText("<html>Optional duration for this JVM session (not saved in prefs).<br>"
                + "0 or No limit: unlimited. A non-zero limit shows this dialog on every start "
                + "so you can name the long recording. With VCR this is the length of each cassette.</html>");
        RecordingTimeLimit.bindPresetChooser(timeLimitPreset, timeLimitField);
        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        timeRow.add(timeLimitPreset);
        timeRow.add(new JLabel("or type:"));
        timeLimitField.setToolTipText("Examples: 0, 10m, 2h, 1h 15m. Bare numbers are milliseconds.");
        timeRow.add(timeLimitField);
        timeLimitField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onTimeLimitEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onTimeLimitEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onTimeLimitEdited();
            }
        });
        body.add(timeRow, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        body.add(new JLabel("VCR:"), c);
        c.gridx = 1;
        c.weightx = 1;
        vcrCb.setToolTipText("<html>Optional: many AEDAT-4 files of the time-limit length in one folder.<br>"
                + "Infinite keeps adding. Finite records N files then stops. Rotate keeps the last N.<br>"
                + "Each cassette is closed fully so a synced folder can be inspected. No Save As per cassette.<br>"
                + "AEDAT-4 and a non-zero time limit required. Sticky for this jAER run.</html>");
        vcrKindCombo.setToolTipText("Infinite: keep adding. Finite: stop after N cassettes. Rotate: keep only the last N.");
        rotateKeepSpinner.setToolTipText("Finite: stop after this many files. Rotate: how many to keep (including the one being written).");
        JSpinner.DefaultEditor keepEditor = (JSpinner.DefaultEditor) rotateKeepSpinner.getEditor();
        keepEditor.getTextField().setColumns(4);
        vcrCb.addActionListener(e -> {
            if (!updatingUi) {
                updateVcrControls();
            }
        });
        vcrKindCombo.addActionListener(e -> {
            if (!updatingUi) {
                updateVcrControls();
            }
        });
        JPanel vcrRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        vcrRow.add(vcrCb);
        vcrRow.add(vcrKindCombo);
        vcrRow.add(rotateKeepLabel);
        vcrRow.add(rotateKeepSpinner);
        body.add(vcrRow, c);
        return body;
    }

    private void setTimedSectionExpanded(boolean expanded) {
        timedSectionExpanded = expanded;
        timedSectionToggle.setText(expanded ? "\u25BE Timed recording / VCR" : "\u25B8 Timed recording / VCR");
        if (timedBody != null) {
            timedBody.setVisible(expanded);
        }
        timedCollapsedSummary.setVisible(!expanded);
        updateTimedCollapsedSummary();
        if (isDisplayable()) {
            pack();
        }
    }

    /**
     * First time this dialog's timed section is showing, fill last Start prefs
     * (or the newest VCR deck in the recording folder if prefs were never written).
     * JVM-sticky values from an earlier Start this run win.
     */
    private void restoreLastTimedSettingsIfNeeded() {
        if (timedPrefsAppliedThisDialog) {
            return;
        }
        if (!shouldRestoreLastTimedFromPrefs()) {
            timedPrefsAppliedThisDialog = true;
            return;
        }
        timedPrefsAppliedThisDialog = true;
        File search = host != null ? host.getLastRecordingFolder() : folder;
        LastTimed last = lastTimedForRestore(search);
        applyTimedChoice(last.timeLimitMs, last.vcrMode, last.rotateKeep);
        log.info("restored last timed recording limitMs=" + last.timeLimitMs
                + " vcr=" + last.vcrMode + " keep=" + last.rotateKeep
                + (lastTimedPrefsWritten() ? " (prefs)" : " (last VCR deck)"));
    }

    private void updateTimedCollapsedSummary() {
        long ms = parsedTimeLimitMsOrZero();
        String limit = ms <= 0L ? "no time limit" : RecordingTimeLimit.formatForDialog(ms);
        if (vcrCb.isSelected() && vcrAvailable(selectedVersion(), ms)) {
            RecordingVcrSession.Mode kind = vcrModeFromKindCombo();
            if (kind == RecordingVcrSession.Mode.ROTATE) {
                timedCollapsedSummary.setText(limit + ", VCR rotate " + rotateKeepFromSpinner());
            } else if (kind == RecordingVcrSession.Mode.FINITE) {
                timedCollapsedSummary.setText(limit + ", VCR finite " + rotateKeepFromSpinner());
            } else {
                timedCollapsedSummary.setText(limit + ", VCR infinite");
            }
        } else {
            timedCollapsedSummary.setText(limit);
        }
    }

    private void loadFromViewer() {
        updatingUi = true;
        try {
            formatCombo.setSelectedIndex(
                    AEViewerPreferencesDialog.recordingFormatIndexForVersion(host.getRecordingDataFileVersion()));
            compressionCombo.setSelectedIndex(Aedat4Compression.clamp(host.getAedat4Compression()));
        } finally {
            updatingUi = false;
        }
        updateCompressionEnabled();
        File proposed = proposedFile(host, applyTargets, selectedVersion(), stamp, folder);
        nameField.setText(proposed.getName());
        if (proposed.getParentFile() != null) {
            folder = proposed.getParentFile();
        }
        folderCombo.refresh();
        folderCombo.syncSelection(folder);
        recordFilteredCb.setSelected(host.isRecordFilteredEventsEnabled());
        applyTimedChoice(sessionTimeLimitMs.get(), stickyVcrMode.get(), stickyRotateKeep.get());
        updateFilterSummary();
        boolean expandTimed = sessionTimeLimitMs.get() > 0L || isSessionVcrEnabled();
        setTimedSectionExpanded(expandTimed);
    }

    private void applyTimedChoice(long limitMs, RecordingVcrSession.Mode vcr, int keep) {
        updatingUi = true;
        try {
            String initial = RecordingTimeLimit.initialValue(limitMs);
            timeLimitField.setText(RecordingTimeLimit.NO_LIMIT.equals(initial) ? "0" : initial);
            timeLimitPreset.setSelectedIndex(RecordingTimeLimit.presetIndex(initial));
            RecordingVcrSession.Mode mode = vcr == null ? RecordingVcrSession.Mode.OFF : vcr;
            vcrCb.setSelected(mode != RecordingVcrSession.Mode.OFF);
            vcrKindCombo.setSelectedItem(vcrKindLabel(mode));
            rotateKeepSpinner.setValue(RecordingVcrSession.clampRotateKeep(keep));
        } finally {
            updatingUi = false;
        }
        updateVcrControls();
    }

    private void setFolder(File dir) {
        if (dir == null || !FileAccessTimeout.isDirectory(dir)) {
            return;
        }
        folder = dir;
        folderCombo.refresh();
        folderCombo.syncSelection(folder);
        updateNameTooltip();
    }

    private void browseFolder(ActionEvent e) {
        File chosen = RecordingFolderChooser.chooseFolder(this, folder, host.getRecentFiles(),
                "Use this folder",
                "Choose recording folder");
        if (chosen != null) {
            setFolder(chosen);
        }
    }

    private String selectedVersion() {
        return AEViewerPreferencesDialog.recordingFormatVersionForIndex(formatCombo.getSelectedIndex());
    }

    private void updateCompressionEnabled() {
        compressionCombo.setEnabled(
                AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(selectedVersion()));
    }

    private void onTimeLimitEdited() {
        if (updatingUi) {
            return;
        }
        updateVcrControls();
    }

    /**
     * Filters window is a separate JFrame; this setup dialog is application-modal,
     * so drop modality once so the user can enable filters without closing Start recording.
     */
    private void openFiltersWindow() {
        if (isVisible() && getModalityType() != ModalityType.MODELESS) {
            setVisible(false);
            setModalityType(ModalityType.MODELESS);
            setVisible(true);
        }
        host.showFilters(true);
        if (host.getFilterFrame() != null) {
            host.getFilterFrame().toFront();
        }
    }

    private long parsedTimeLimitMsOrZero() {
        try {
            return Math.max(0L, RecordingTimeLimit.parseMs(timeLimitField.getText()));
        } catch (IllegalArgumentException e) {
            return 0L;
        }
    }

    private boolean vcrUiActive() {
        return vcrCb.isSelected() && vcrAvailable(selectedVersion(), parsedTimeLimitMsOrZero());
    }

    private int rotateKeepFromSpinner() {
        Object v = rotateKeepSpinner.getValue();
        if (v instanceof Number) {
            return RecordingVcrSession.clampRotateKeep(((Number) v).intValue());
        }
        return RecordingVcrSession.ROTATE_DEFAULT;
    }

    private RecordingVcrSession.Mode vcrModeFromKindCombo() {
        Object sel = vcrKindCombo.getSelectedItem();
        if (VCR_ROTATE.equals(sel)) {
            return RecordingVcrSession.Mode.ROTATE;
        }
        if (VCR_FINITE.equals(sel)) {
            return RecordingVcrSession.Mode.FINITE;
        }
        return RecordingVcrSession.Mode.INFINITE;
    }

    private static String vcrKindLabel(RecordingVcrSession.Mode mode) {
        if (mode == RecordingVcrSession.Mode.ROTATE) {
            return VCR_ROTATE;
        }
        if (mode == RecordingVcrSession.Mode.FINITE) {
            return VCR_FINITE;
        }
        return VCR_INFINITE;
    }

    private void updateVcrControls() {
        boolean avail = vcrAvailable(selectedVersion(), parsedTimeLimitMsOrZero());
        vcrCb.setEnabled(avail);
        boolean on = avail && vcrCb.isSelected();
        vcrKindCombo.setEnabled(on);
        RecordingVcrSession.Mode kind = vcrModeFromKindCombo();
        boolean needsN = on && (kind == RecordingVcrSession.Mode.ROTATE || kind == RecordingVcrSession.Mode.FINITE);
        rotateKeepLabel.setText(kind == RecordingVcrSession.Mode.FINITE ? "Stop after" : "Keep last");
        rotateKeepLabel.setEnabled(needsN);
        rotateKeepSpinner.setEnabled(needsN);
        if (on != nameAsSessionFolder) {
            nameAsSessionFolder = on;
            applyNameStyle(on);
        } else {
            updateNameTooltip();
        }
        updateMuxHint();
        updateTimedCollapsedSummary();
    }

    private String proposedSessionFolderName() {
        return RecordingVcrSession.vcrSessionFolderName(
                proposedFile(host, applyTargets, selectedVersion(), stamp, folder).getName());
    }

    private void applyNameStyle(boolean sessionFolder) {
        if (sessionFolder) {
            nameLabel.setText("Session folder:");
            String cur = nameField.getText().trim();
            if (cur.isEmpty()) {
                nameField.setText(proposedSessionFolderName());
            } else {
                nameField.setText(RecordingVcrSession.ensureVcrFolderMark(cur));
            }
        } else {
            nameLabel.setText("File name:");
            String cur = nameField.getText().trim();
            if (cur.isEmpty()) {
                nameField.setText(proposedFile(host, applyTargets, selectedVersion(), stamp, folder).getName());
            } else {
                nameField.setText(withFormatExtension(
                        RecordingVcrSession.stripVcrFolderMark(cur), selectedVersion()));
            }
        }
        updateNameTooltip();
    }

    private void updateNameExtension() {
        nameAsSessionFolder = vcrUiActive();
        applyNameStyle(nameAsSessionFolder);
    }

    private void updateMuxHint() {
        boolean perFile = muxMany && !AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(selectedVersion());
        boolean session = vcrUiActive();
        nameField.setEnabled(!perFile || session);
        if (perFile && !session) {
            nameField.setToolTipText("Each camera gets its own chip-datestamp file in this folder");
        } else {
            updateNameTooltip();
        }
    }

    private void updateNameTooltip() {
        if (vcrUiActive()) {
            File cassette = chosenFile();
            File sessionDir = cassette.getParentFile();
            nameField.setToolTipText("<html>Session folder "
                    + ShowFolderSaveConfirmation.escapeHtml(sessionDir.getAbsolutePath())
                    + "<br>Cassettes: "
                    + ShowFolderSaveConfirmation.escapeHtml(cassette.getName())
                    + ", _c0002, … (closed so a synced folder can be inspected)</html>");
            return;
        }
        File f = chosenFile();
        nameField.setToolTipText("<html>" + ShowFolderSaveConfirmation.escapeHtml(f.getAbsolutePath())
                + "<br>Folder is the row below.</html>");
    }

    private File chosenFile() {
        applyPathPaste();
        File dir = folder != null ? folder : new File(".");
        if (vcrUiActive()) {
            String session = nameField.getText().trim();
            if (session.isEmpty()) {
                session = proposedSessionFolderName();
            }
            return RecordingVcrSession.firstCassetteFile(dir, session);
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = proposedFile(host, applyTargets, selectedVersion(), stamp, folder).getName();
        }
        name = withFormatExtension(name, selectedVersion());
        return new File(dir, name);
    }

    private void applyPathPaste() {
        String text = nameField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        File asFile = new File(text);
        boolean looksAbsolute = asFile.isAbsolute()
                || text.indexOf('/') >= 0
                || text.indexOf('\\') >= 0;
        if (looksAbsolute && asFile.getParentFile() != null) {
            nameField.setText(asFile.getName());
            File parent = asFile.getParentFile();
            if (FileAccessTimeout.isDirectory(parent)) {
                setFolder(parent);
            }
        }
    }

    private void acceptAndClose() {
        hideComboPopups();
        long limitMs;
        try {
            limitMs = RecordingTimeLimit.parseMs(timeLimitField.getText());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    "Bad time limit? Caught " + ex.toString(),
                    "Error with duration", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String version = selectedVersion();
        int compression = Aedat4Compression.clamp(compressionCombo.getSelectedIndex());
        File out = chosenFile();
        File parentFolder = folder != null ? folder : (out.getParentFile() != null ? out.getParentFile() : new File("."));
        if (vcrCb.isSelected() && !vcrAvailable(version, limitMs)) {
            JOptionPane.showMessageDialog(this,
                    "VCR (multiple files) needs AEDAT-4 and a non-zero time limit (cassette length).",
                    "VCR not available", JOptionPane.ERROR_MESSAGE);
            return;
        }
        RecordingVcrSession.Mode vcrMode = RecordingVcrSession.Mode.OFF;
        int keep = rotateKeepFromSpinner();
        if (vcrCb.isSelected() && vcrAvailable(version, limitMs)) {
            vcrMode = vcrModeFromKindCombo();
            parentFolder = folder != null ? folder : new File(".");
            File sessionDir = out.getParentFile();
            if (sessionDir != null && !sessionDir.isDirectory() && !sessionDir.mkdirs()) {
                JOptionPane.showMessageDialog(this,
                        "Could not create VCR session folder:\n" + sessionDir.getAbsolutePath(),
                        "VCR folder", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        sessionTimeLimitMs.set(Math.max(0L, limitMs));
        setSessionVcr(vcrMode, keep);
        persistLastTimedPrefs(sessionTimeLimitMs.get(), vcrMode, keep);
        for (AEViewer v : applyTargets) {
            if (v == null) {
                continue;
            }
            v.setRecordingDataFileVersion(version);
            v.setAedat4Compression(compression);
            v.setLastRecordingFolder(parentFolder);
            v.applyRecordingTimeLimit(sessionTimeLimitMs.get());
        }
        applyRecordFilteredToTargets(recordFilteredCb.isSelected());
        boolean muxAedat4 = muxMany && AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(version);
        if (!muxMany || muxAedat4) {
            host.setPendingRecordingStartFile(out);
        }
        acceptedShowCount.incrementAndGet();
        accepted = true;
        log.info("recording setup accepted vcr=" + vcrMode + " pending="
                + (out != null ? out.getAbsolutePath() : "null"));
        // Start the file *before* closing the modal. On Windows, dispose() with a
        // JComboBox popup open can leave setVisible(true) blocked (jAER-0.log
        // 2026-09-13 9:14:48: accepted, no startRecording, ViewLoop kept running).
        startRecordingNow();
        closeToStart();
    }

    private void startRecordingNow() {
        if (host != null && host.isRecordingEnabled()) {
            return;
        }
        if (muxMany && host != null && host.getJaerViewer() != null) {
            host.getJaerViewer().startSynchronizedRecording();
            return;
        }
        if (host != null) {
            host.startRecording();
        }
    }

    /**
     * Unblock {@code setVisible(true)} without hanging on an open JComboBox
     * popup (Windows). Hide popups, then defer hide/dispose so popup teardown
     * can finish before the modal secondary loop is torn down.
     */
    private void closeToStart() {
        hideComboPopups();
        SwingUtilities.invokeLater(() -> {
            hideComboPopups();
            setVisible(false);
            dispose();
        });
    }

    private void hideComboPopups() {
        formatCombo.hidePopup();
        compressionCombo.hidePopup();
        timeLimitPreset.hidePopup();
        vcrKindCombo.hidePopup();
        if (folderCombo != null) {
            folderCombo.hidePopup();
        }
    }

    private void applyRecordFilteredToTargets(boolean enabled) {
        for (AEViewer v : applyTargets) {
            if (v != null) {
                v.setRecordFilteredEventsEnabled(enabled);
            }
        }
    }

    private void restoreRecordFiltered() {
        for (Map.Entry<AEViewer, Boolean> e : recordFilteredAtOpen.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                e.getKey().setRecordFilteredEventsEnabled(e.getValue());
            }
        }
    }

    private void updateFilterSummary() {
        boolean apply = recordFilteredCb.isSelected();
        boolean wasVisible = filterSummaryPanel.isVisible();
        filterSummaryPanel.setVisible(apply);
        boolean textChanged = false;
        if (apply) {
            String html = buildEnabledFiltersHtml();
            textChanged = !html.equals(filterSummaryLabel.getText());
            if (textChanged) {
                filterSummaryLabel.setText(html);
            }
        }
        if ((wasVisible != apply || textChanged) && isDisplayable()) {
            pack();
        }
    }

    private String buildEnabledFiltersHtml() {
        FilterChain chain = host.getChip() != null ? host.getChip().getFilterChain() : null;
        if (chain == null) {
            return "<html>No filter chain on this chip.";
        }
        return chain.enabledFiltersConfirmationHtml("Recording");
    }

    private void bindFilterEnabledListeners() {
        unbindFilterEnabledListeners();
        FilterChain chain = host.getChip() != null ? host.getChip().getFilterChain() : null;
        if (chain == null) {
            return;
        }
        filterEnabledListenChain = chain;
        chain.getSupport().addPropertyChangeListener("filteringEnabled", this);
        for (EventFilter2D f : chain) {
            if (f == null) {
                continue;
            }
            f.getSupport().addPropertyChangeListener("filterEnabled", this);
            filterEnabledListenTargets.add(f);
        }
    }

    private void unbindFilterEnabledListeners() {
        if (filterEnabledListenChain != null) {
            filterEnabledListenChain.getSupport().removePropertyChangeListener("filteringEnabled", this);
            filterEnabledListenChain = null;
        }
        for (EventFilter2D f : filterEnabledListenTargets) {
            if (f != null) {
                f.getSupport().removePropertyChangeListener("filterEnabled", this);
            }
        }
        filterEnabledListenTargets.clear();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String n = evt.getPropertyName();
        if (AEViewer.EVENT_RECORD_FILTERED_EVENTS.equals(n)) {
            Object nv = evt.getNewValue();
            if (nv instanceof Boolean) {
                Runnable ui = () -> {
                    if (updatingUi) {
                        return;
                    }
                    updatingUi = true;
                    try {
                        recordFilteredCb.setSelected((Boolean) nv);
                    } finally {
                        updatingUi = false;
                    }
                    updateFilterSummary();
                };
                if (SwingUtilities.isEventDispatchThread()) {
                    ui.run();
                } else {
                    SwingUtilities.invokeLater(ui);
                }
            }
            return;
        }
        if ("filterEnabled".equals(n) || "filteringEnabled".equals(n)) {
            if (SwingUtilities.isEventDispatchThread()) {
                updateFilterSummary();
            } else {
                SwingUtilities.invokeLater(this::updateFilterSummary);
            }
        }
    }

    @Override
    public void dispose() {
        unbindFilterEnabledListeners();
        host.getSupport().removePropertyChangeListener(AEViewer.EVENT_RECORD_FILTERED_EVENTS, this);
        if (!accepted) {
            restoreRecordFiltered();
        }
        super.dispose();
    }
}
