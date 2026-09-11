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
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.RecordingFilename;
import net.sf.jaer.eventio.aedat4.Aedat4Compression;
import net.sf.jaer.util.FileAccessTimeout;
import net.sf.jaer.util.OutputFilename;
import net.sf.jaer.util.RecentFoldersJumpCombo;
import net.sf.jaer.util.ShowFolderSaveConfirmation;

/**
 * Start-recording prompt for folder, filename, format, AEDAT-4 compression, and
 * a session time limit (default none). Shown for the first
 * {@link #MAX_AUTO_SHOWS} recordings this JVM, whenever a non-zero time limit
 * is set, and whenever the user chooses File → Start recording (not the
 * toolbar button or {@code L}). Enter activates {@code Start Recording}.
 * Confirmed format, compression, and folder are written back to AEViewer prefs.
 * The time limit is sticky for this JVM only.
 */
public final class RecordingSetupDialog extends JDialog {

    /** Unlimited toolbar/{@code L} starts after this many accepted setup dialogs. */
    public static final int MAX_AUTO_SHOWS = 3;
    private static final AtomicInteger acceptedShowCount = new AtomicInteger(0);
    private static final AtomicLong sessionTimeLimitMs = new AtomicLong(0L);
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

    private final JTextField nameField = new JTextField(36);
    private final JComboBox<String> formatCombo = new JComboBox<>(FORMAT_LABELS);
    private final JComboBox<String> compressionCombo = new JComboBox<>(COMPRESSION_LABELS);
    private final JComboBox<String> timeLimitPreset = new JComboBox<>(RecordingTimeLimit.PRESETS);
    private final JTextField timeLimitField = new JTextField(16);
    private RecentFoldersJumpCombo folderCombo;
    private File folder;
    private boolean accepted;
    private boolean updatingUi;

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
        if (!shouldShow(acceptedShowCount.get(), sessionTimeLimitMs.get(), forceFromFileMenu)) {
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
     * and whenever a time limit is set. File menu always shows.
     */
    public static boolean shouldShow(int acceptedShows, long sessionLimitMs, boolean forceFromFileMenu) {
        if (forceFromFileMenu) {
            return true;
        }
        if (sessionLimitMs > 0L) {
            return true;
        }
        return acceptedShows < MAX_AUTO_SHOWS;
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
        form.add(new JLabel("File name:"), c);
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
            updateNameExtension();
            updateMuxHint();
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
        c.weightx = 0;
        form.add(new JLabel("Time limit:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        timeLimitPreset.setMaximumRowCount(RecordingTimeLimit.PRESETS.length);
        timeLimitPreset.setToolTipText("<html>Optional duration for this JVM session (not saved in prefs).<br>"
                + "0 or No limit: unlimited. A non-zero limit shows this dialog on every start "
                + "so you can name the long recording.</html>");
        RecordingTimeLimit.bindPresetChooser(timeLimitPreset, timeLimitField);
        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        timeRow.add(timeLimitPreset);
        timeRow.add(new JLabel("or type:"));
        timeLimitField.setToolTipText("Examples: 0, 10m, 2h, 1h 15m. Bare numbers are milliseconds.");
        timeRow.add(timeLimitField);
        form.add(timeRow, c);
        c.gridwidth = 1;

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        JLabel hint = new JLabel("<html><i>First " + MAX_AUTO_SHOWS
                + " recordings this session, File → Start recording, or any timed recording. "
                + "Enter starts. Format, compression, and folder become AEViewer prefs. "
                + "Time limit stays for this jAER run only.</i></html>");
        form.add(hint, c);

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
        updateMuxHint();
        updateNameTooltip();
        String initial = RecordingTimeLimit.initialValue(sessionTimeLimitMs.get());
        timeLimitField.setText(RecordingTimeLimit.NO_LIMIT.equals(initial) ? "0" : initial);
        timeLimitPreset.setSelectedIndex(RecordingTimeLimit.presetIndex(initial));
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

    private void updateNameExtension() {
        String cur = nameField.getText().trim();
        if (cur.isEmpty()) {
            File proposed = proposedFile(host, applyTargets, selectedVersion(), stamp, folder);
            nameField.setText(proposed.getName());
        } else {
            nameField.setText(withFormatExtension(cur, selectedVersion()));
        }
        updateNameTooltip();
    }

    private void updateMuxHint() {
        boolean perFile = muxMany && !AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(selectedVersion());
        nameField.setEnabled(!perFile);
        if (perFile) {
            nameField.setToolTipText("Each camera gets its own chip-datestamp file in this folder");
        } else {
            updateNameTooltip();
        }
    }

    private void updateNameTooltip() {
        File f = chosenFile();
        nameField.setToolTipText("<html>" + ShowFolderSaveConfirmation.escapeHtml(f.getAbsolutePath())
                + "<br>Folder is the row below.</html>");
    }

    private File chosenFile() {
        applyPathPaste();
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = proposedFile(host, applyTargets, selectedVersion(), stamp, folder).getName();
        }
        name = withFormatExtension(name, selectedVersion());
        File dir = folder != null ? folder : new File(".");
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
        File dir = out.getParentFile() != null ? out.getParentFile() : folder;
        sessionTimeLimitMs.set(Math.max(0L, limitMs));
        for (AEViewer v : applyTargets) {
            if (v == null) {
                continue;
            }
            v.setRecordingDataFileVersion(version);
            v.setAedat4Compression(compression);
            if (dir != null) {
                v.setLastRecordingFolder(dir);
            }
            v.applyRecordingTimeLimit(sessionTimeLimitMs.get());
        }
        boolean muxAedat4 = muxMany && AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(version);
        if (!muxMany || muxAedat4) {
            host.setPendingRecordingStartFile(out);
        }
        acceptedShowCount.incrementAndGet();
        accepted = true;
        dispose();
    }
}
