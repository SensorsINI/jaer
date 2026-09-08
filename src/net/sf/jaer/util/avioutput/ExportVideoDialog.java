package net.sf.jaer.util.avioutput;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AEViewer.PlayMode;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.util.FileAccessTimeout;
import net.sf.jaer.util.RecentFiles;
import net.sf.jaer.util.RecentFoldersComboAccessory;
import net.sf.jaer.util.RecentFoldersJumpCombo;
import net.sf.jaer.util.ShowFolderSaveConfirmation;
import net.sf.jaer.util.WindowSaver;

/**
 * File/Export video window: drives {@link JaerAviWriter} to capture the rendered
 * AEViewer OpenGL view to AVI, optionally converting to MP4 with ffmpeg afterward.
 * Implemented as a {@link JFrame} (not an owned {@code JDialog}) so it can go behind AEViewer.
 * {@link WindowSaver.DontResize} keeps {@link #pack()} size; last position may still restore.
 */
public class ExportVideoDialog extends JFrame implements PropertyChangeListener, WindowSaver.DontResize {

    private static final Preferences prefs = Preferences.userNodeForPackage(ExportVideoDialog.class);

    private final AEViewer viewer;
    private AbstractAviWriter writer;

    private final JTextField pathField = new JTextField(36);
    private final JComboBox<AVIOutputStream.VideoFormat> formatCombo = new JComboBox<>(
            new AVIOutputStream.VideoFormat[]{
                AVIOutputStream.VideoFormat.JPG,
                AVIOutputStream.VideoFormat.PNG,
                AVIOutputStream.VideoFormat.RAW
            });
    private final JSpinner frameRateSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 240, 1));
    private final JCheckBox matchViewerRateCb = new JCheckBox("Match AEViewer target rendering rate", true);
    private RecentFoldersJumpCombo recentFolderCombo;
    private final JCheckBox writeTimecodeCb = new JCheckBox("Write timecode file", false);
    private final JCheckBox useMarkersCb = new JCheckBox("Use IN and OUT markers", true);
    private final JCheckBox convertMp4Cb = new JCheckBox("Convert to MP4 with ffmpeg after close", true);
    private final JCheckBox deleteAviCb = new JCheckBox("Delete intermediate AVI after successful MP4", false);
    private final JTextField ffmpegPathField = new JTextField(24);
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel ffmpegStatusLabel = new JLabel(" ");

    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");
    private final JButton convertNowButton = new JButton("Convert AVI→MP4");
    private final JButton closeButton = new JButton("Close");

    private File aviFile;
    private File mp4File;
    private boolean convertToMp4;
    private boolean deleteAviAfterMp4;
    private boolean listeningToWriter;
    /** True after AVI finished with MP4 requested but ffmpeg was missing. */
    private boolean pendingMp4Convert;
    /** Prior JaerAviWriter enabled/selected state restored when export ends. */
    private boolean writerStateSaved;
    private boolean writerWasFilterEnabled;
    private boolean writerWasSelected;
    private boolean writerWasAnnotationEnabled;
    private boolean playerRepeatSaved;
    private boolean savedPlayerRepeat;
    private boolean marksClearedForExport;
    private AEFileInputStreamInterface marksStream;
    private long savedMarkIn;
    private long savedMarkOut;
    private boolean savedMarkInSet;
    private boolean savedMarkOutSet;

    public ExportVideoDialog(AEViewer viewer) {
        super("Export video");
        this.viewer = viewer;
        setName("ExportVideo");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if (viewer != null) {
            setIconImage(viewer.getIconImage());
        }
        buildUi();
        loadPrefs();
        applyAedatDefaults();
        updateFfmpegStatus();
        updateRecordingUi(false);
        pack();
        setLocationRelativeTo(viewer);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                detachWriterListener();
            }
        });
    }

    /**
     * Finds or reports missing JaerAviWriter on the chip filter chain.
     */
    public static JaerAviWriter findJaerAviWriter(AEViewer viewer) {
        if (viewer == null || viewer.getChip() == null || viewer.getChip().getFilterChain() == null) {
            return null;
        }
        FilterChain chain = viewer.getChip().getFilterChain();
        return (JaerAviWriter) chain.findFilter(JaerAviWriter.class);
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
        form.add(new JLabel("Output file:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(pathField, c);
        c.gridx = 2;
        c.weightx = 0;
        JButton browse = new JButton("Browse…");
        browse.addActionListener(this::browse);
        form.add(browse, c);

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel("Recent folder:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        recentFolderCombo = new RecentFoldersJumpCombo(viewer != null ? viewer.getRecentFiles() : null,
                this::parentOfPathField, this::setOutputFolder);
        recentFolderCombo.setToolTipText("Jump the output file to a folder from File → recent folders");
        form.add(recentFolderCombo, c);
        c.gridwidth = 1;

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel("AVI frame format:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        form.add(formatCombo, c);
        c.gridwidth = 1;

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel("Playback frame rate:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        frameRateSpinner.setToolTipText("AVI/MP4 playback FPS; with Match AEViewer rate, uses View target FPS (arrow keys)");
        form.add(frameRateSpinner, c);
        c.gridwidth = 1;

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        matchViewerRateCb.setToolTipText("<html>Sets AVI playback FPS from AEViewer target rendering rate at Start.<br>"
                + "Recording waits for each rendered frame (may be slower than real time) so playback is smooth at that rate.");
        matchViewerRateCb.addActionListener(e -> syncFrameRateSpinnerEnabled());
        form.add(matchViewerRateCb, c);

        row++;
        c.gridy = row;
        form.add(writeTimecodeCb, c);

        row++;
        c.gridy = row;
        useMarkersCb.setToolTipText("<html>Checked: record the IN–OUT interval "
                + "(unset IN = file start, unset OUT = EOF; one play-through).<br>"
                + "Unchecked: record the entire file, ignoring markers.<br>"
                + "Live capture ignores this option; click Stop to finish.</html>");
        form.add(useMarkersCb, c);

        row++;
        c.gridy = row;
        form.add(convertMp4Cb, c);

        row++;
        c.gridy = row;
        form.add(deleteAviCb, c);

        row++;
        c.gridy = row;
        c.gridwidth = 1;
        c.gridx = 0;
        form.add(new JLabel("ffmpeg path (optional):"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(ffmpegPathField, c);
        c.gridx = 2;
        c.weightx = 0;
        JButton detect = new JButton("Detect");
        detect.setToolTipText("Search PATH, Windows registry PATH, and WinGet install folders for ffmpeg (no restart needed)");
        detect.addActionListener(e -> detectFfmpeg());
        form.add(detect, c);

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        form.add(ffmpegStatusLabel, c);

        row++;
        c.gridy = row;
        form.add(new JLabel("<html><i>Synchronized capture: one AVI frame per rendered view; loop waits for encode.<br>"
                + "Playback FPS matches AEViewer target rate (may export slower than real time).</i>"), c);

        row++;
        c.gridy = row;
        form.add(statusLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        startButton.addActionListener(this::startRecording);
        stopButton.addActionListener(this::stopRecording);
        convertNowButton.addActionListener(e -> convertPendingAvi());
        convertNowButton.setToolTipText("Convert the last exported AVI to MP4 now (after installing ffmpeg)");
        closeButton.addActionListener(e -> dispose());
        buttons.add(startButton);
        buttons.add(stopButton);
        buttons.add(convertNowButton);
        buttons.add(closeButton);

        convertMp4Cb.addActionListener(e -> updateFfmpegStatus());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
    }

    private void loadPrefs() {
        pathField.setText(prefs.get("lastExportPath", "jAER-export.mp4"));
        try {
            formatCombo.setSelectedItem(AVIOutputStream.VideoFormat.valueOf(prefs.get("format", "JPG")));
        } catch (Exception e) {
            formatCombo.setSelectedItem(AVIOutputStream.VideoFormat.JPG);
        }
        matchViewerRateCb.setSelected(prefs.getBoolean("matchViewerFrameRate", true));
        int defaultFps = viewer != null ? Math.max(1, viewer.getDesiredFrameRate()) : 30;
        frameRateSpinner.setValue(prefs.getInt("frameRate", defaultFps));
        if (matchViewerRateCb.isSelected() && viewer != null) {
            frameRateSpinner.setValue(Math.max(1, viewer.getDesiredFrameRate()));
        }
        writeTimecodeCb.setSelected(prefs.getBoolean("writeTimecode", false));
        useMarkersCb.setSelected(prefs.getBoolean("useMarkers", true));
        convertMp4Cb.setSelected(prefs.getBoolean("convertMp4", true));
        deleteAviCb.setSelected(prefs.getBoolean("deleteAvi", false));
        ffmpegPathField.setText(FfmpegMp4Converter.getConfiguredFfmpegPath());
        syncFrameRateSpinnerEnabled();
    }

    private void syncFrameRateSpinnerEnabled() {
        boolean match = matchViewerRateCb.isSelected();
        frameRateSpinner.setEnabled(!match && startButton.isEnabled());
        if (match && viewer != null) {
            frameRateSpinner.setValue(Math.max(1, viewer.getDesiredFrameRate()));
        }
    }

    private void savePrefs() {
        prefs.put("lastExportPath", pathField.getText().trim());
        prefs.put("format", formatCombo.getSelectedItem().toString());
        prefs.putInt("frameRate", (Integer) frameRateSpinner.getValue());
        prefs.putBoolean("matchViewerFrameRate", matchViewerRateCb.isSelected());
        prefs.putBoolean("writeTimecode", writeTimecodeCb.isSelected());
        prefs.putBoolean("useMarkers", useMarkersCb.isSelected());
        prefs.putBoolean("convertMp4", convertMp4Cb.isSelected());
        prefs.putBoolean("deleteAvi", deleteAviCb.isSelected());
        FfmpegMp4Converter.setConfiguredFfmpegPath(ffmpegPathField.getText().trim());
    }

    private void applyAedatDefaults() {
        boolean playback = viewer != null && viewer.getPlayMode() == PlayMode.PLAYBACK;
        useMarkersCb.setEnabled(playback);
        if (recentFolderCombo != null) {
            recentFolderCombo.refresh();
        }
    }

    private void detectFfmpeg() {
        // Prefer auto-discovery over a stale empty/wrong prefs path
        String typed = ffmpegPathField.getText().trim();
        if (!typed.isEmpty() && new File(typed).isFile()) {
            FfmpegMp4Converter.setConfiguredFfmpegPath(typed);
        } else {
            FfmpegMp4Converter.setConfiguredFfmpegPath("");
        }
        String found = FfmpegMp4Converter.findFfmpeg();
        if (found != null) {
            // Remember absolute path so next launch skips search
            if (new File(found).isFile()) {
                ffmpegPathField.setText(found);
                FfmpegMp4Converter.setConfiguredFfmpegPath(found);
            }
            updateFfmpegStatus();
            if (pendingMp4Convert && aviFile != null && aviFile.isFile()) {
                int r = JOptionPane.showConfirmDialog(this,
                        "ffmpeg found.\nConvert " + aviFile.getName() + " to MP4 now?",
                        "Export video", JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) {
                    convertPendingAvi();
                }
            }
        } else {
            updateFfmpegStatus();
            JOptionPane.showMessageDialog(this,
                    new net.sf.jaer.util.MessageWithLink(
                            "Still could not find ffmpeg.<p>"
                            + "Install via winget (<code>winget install Gyan.FFmpeg.Essentials</code>) "
                            + "or download from <a href=\"" + FfmpegMp4Converter.FFMPEG_DOWNLOAD_URL + "\">"
                            + FfmpegMp4Converter.FFMPEG_DOWNLOAD_URL + "</a>, then click Detect again "
                            + "or paste the full path to <code>ffmpeg.exe</code>."),
                    "ffmpeg not found", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void updateFfmpegStatus() {
        String typed = ffmpegPathField.getText().trim();
        if (!typed.isEmpty()) {
            FfmpegMp4Converter.setConfiguredFfmpegPath(typed);
        }
        String found = FfmpegMp4Converter.findFfmpeg();
        if (found != null) {
            ffmpegStatusLabel.setText("<html>ffmpeg: <b>found</b> (" + found + ")");
            if (ffmpegPathField.getText().trim().isEmpty() && new File(found).isFile()) {
                ffmpegPathField.setText(found);
            }
        } else if (convertMp4Cb.isSelected()) {
            ffmpegStatusLabel.setText("<html>ffmpeg: <b>not found</b> — click Detect after installing (no jAER restart needed)");
        } else {
            ffmpegStatusLabel.setText("ffmpeg: not needed (MP4 convert off)");
        }
        boolean canConvert = pendingMp4Convert && aviFile != null && aviFile.isFile() && found != null;
        convertNowButton.setEnabled(canConvert || (aviFile != null && aviFile.isFile() && found != null && convertMp4Cb.isSelected()));
    }

    private void convertPendingAvi() {
        final File avi = aviFile;
        if (avi == null || !avi.isFile()) {
            JOptionPane.showMessageDialog(this, "No AVI file to convert.", "Export video", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (mp4File == null) {
            String base = avi.getAbsolutePath();
            int dot = base.lastIndexOf('.');
            mp4File = new File((dot > 0 ? base.substring(0, dot) : base) + ".mp4");
        }
        deleteAviAfterMp4 = deleteAviCb.isSelected();
        if (!FfmpegMp4Converter.isFfmpegAvailable()) {
            FfmpegMp4Converter.showMissingFfmpegDialog(this, avi);
            return;
        }
        statusLabel.setText("Converting to MP4 with ffmpeg…");
        convertNowButton.setEnabled(false);
        FfmpegMp4Converter.convertAviToMp4Async(this, avi, mp4File, deleteAviAfterMp4, (success, out, message) -> {
            if (success) {
                pendingMp4Convert = false;
                statusLabel.setText("MP4 written: " + (out != null ? out.getName() : ""));
                showExportSavedDialog(out != null ? out : avi,
                        "<html>Saved MP4:<br>" + (out != null ? out.getAbsolutePath() : message));
            } else if (!"ffmpeg not found".equals(message)) {
                statusLabel.setText("MP4 convert failed; AVI kept");
                showExportSavedDialog(avi, ffmpegFailHtml(message, avi));
            } else {
                statusLabel.setText("ffmpeg missing; AVI kept");
            }
            updateFfmpegStatus();
        });
    }

    private File parentOfPathField() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            return null;
        }
        File parent = new File(path).getParentFile();
        return parent != null ? parent : null;
    }

    private void setOutputFolder(File folder) {
        if (folder == null || !FileAccessTimeout.isDirectory(folder)) {
            return;
        }
        File current = new File(pathField.getText().trim());
        String name = current.getName();
        if (name == null || name.isEmpty()) {
            name = "jAER-export.mp4";
        }
        pathField.setText(new File(folder, name).getAbsolutePath());
        if (recentFolderCombo != null) {
            recentFolderCombo.syncSelection(folder);
        }
    }

    private void browse(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        String path = pathField.getText().trim();
        File current = path.isEmpty() ? new File("jAER-export.mp4") : new File(path);
        chooser.setSelectedFile(current);
        File parent = current.getParentFile();
        if (parent != null && FileAccessTimeout.isDirectory(parent)) {
            chooser.setCurrentDirectory(parent);
        }
        chooser.setFileFilter(new FileNameExtensionFilter("Video (*.mp4, *.avi)", "mp4", "avi"));
        chooser.setDialogType(JFileChooser.SAVE_DIALOG);
        RecentFiles recent = viewer != null ? viewer.getRecentFiles() : null;
        if (recent != null) {
            final String proposedName = current.getName();
            chooser.setAccessory(new RecentFoldersComboAccessory(recent, chooser, () -> {
                File dir = chooser.getCurrentDirectory();
                if (dir != null && proposedName != null && !proposedName.isEmpty()) {
                    chooser.setSelectedFile(new File(dir, proposedName));
                }
            }));
        }
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            if (recentFolderCombo != null) {
                recentFolderCombo.refresh();
            }
        }
    }

    private void startRecording(ActionEvent e) {
        writer = findJaerAviWriter(viewer);
        if (writer == null) {
            JOptionPane.showMessageDialog(this,
                    "JaerAviWriter was not found on the filter chain.\nAdd it under Filters, then try again.",
                    "Export video", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Choose an output file path.", "Export video", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File chosen = new File(path);
        convertToMp4 = convertMp4Cb.isSelected() || path.toLowerCase().endsWith(".mp4");
        deleteAviAfterMp4 = deleteAviCb.isSelected();

        if (convertToMp4) {
            if (path.toLowerCase().endsWith(".mp4")) {
                mp4File = chosen;
                String aviPath = path.substring(0, path.length() - 4) + ".avi";
                aviFile = new File(aviPath);
            } else if (path.toLowerCase().endsWith(".avi")) {
                aviFile = chosen;
                mp4File = new File(path.substring(0, path.length() - 4) + ".mp4");
            } else {
                aviFile = new File(path + ".avi");
                mp4File = new File(path + ".mp4");
            }
        } else {
            if (!path.toLowerCase().endsWith(".avi")) {
                aviFile = new File(path + ".avi");
            } else {
                aviFile = chosen;
            }
            mp4File = null;
        }

        if (aviFile.exists()) {
            int r = JOptionPane.showConfirmDialog(this, "Overwrite " + aviFile + "?", "Export video", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) {
                return;
            }
        }

        savePrefs();
        FfmpegMp4Converter.setConfiguredFfmpegPath(ffmpegPathField.getText().trim());
        pendingMp4Convert = false;

        rememberWriterState(writer);
        writer.setFilterEnabled(true);
        writer.setAnnotationEnabled(true);
        writer.setOutputContainer(AbstractAviWriter.OutputContainer.AVI);
        writer.setFormat((AVIOutputStream.VideoFormat) formatCombo.getSelectedItem());
        writer.setMatchViewerFrameRate(matchViewerRateCb.isSelected());
        if (matchViewerRateCb.isSelected() && viewer != null) {
            int fps = Math.max(1, viewer.getDesiredFrameRate());
            frameRateSpinner.setValue(fps);
            writer.setFrameRate(fps);
        } else {
            writer.setFrameRate((Integer) frameRateSpinner.getValue());
        }
        writer.setWriteTimecodeFile(writeTimecodeCb.isSelected());
        boolean playback = viewer != null && viewer.getPlayMode() == PlayMode.PLAYBACK;
        writer.setRewindBeforeRecording(playback);
        writer.setCloseOnRewind(playback);
        writer.setShowCloseOnRewindDialog(false);
        if (playback) {
            preparePlaybackForExport(useMarkersCb.isSelected());
        }
        writer.setWriteEnabled(true);

        if (viewer != null && !viewer.isActiveRenderingEnabled()) {
            viewer.setActiveRenderingEnabled(true);
            statusLabel.setText("Enabled active rendering for reliable frame capture");
        }

        attachWriterListener();
        if (!writer.startRecording(aviFile)) {
            detachWriterListener();
            restorePlaybackAfterExport();
            restoreWriterState();
            JOptionPane.showMessageDialog(this, "Could not start recording to " + aviFile, "Export video", JOptionPane.WARNING_MESSAGE);
            return;
        }
        updateRecordingUi(true);
        statusLabel.setText(String.format("Recording %s at %d fps (synchronized)…",
                aviFile.getName(), writer.getFrameRate()));
    }

    private void rememberWriterState(AbstractAviWriter w) {
        if (w == null) {
            writerStateSaved = false;
            return;
        }
        writerWasFilterEnabled = w.isFilterEnabled();
        writerWasSelected = w.isSelected();
        writerWasAnnotationEnabled = w.isAnnotationEnabled();
        writerStateSaved = true;
    }

    private void preparePlaybackForExport(boolean useMarkers) {
        AbstractAEPlayer player = viewer != null ? viewer.getAePlayer() : null;
        if (player == null) {
            return;
        }
        savedPlayerRepeat = player.isRepeat();
        playerRepeatSaved = true;
        player.setRepeat(true);
        AEFileInputStreamInterface stream = player.getAEInputStream();
        if (stream != null) {
            stream.setRepeat(true);
            if (!useMarkers && (stream.isMarkInSet() || stream.isMarkOutSet())) {
                marksStream = stream;
                savedMarkIn = stream.getMarkInPosition();
                savedMarkOut = stream.getMarkOutPosition();
                savedMarkInSet = stream.isMarkInSet();
                savedMarkOutSet = stream.isMarkOutSet();
                marksClearedForExport = true;
                stream.clearMarks();
            }
        }
        if (player.isPaused()) {
            player.setPaused(false);
        }
    }

    private void restorePlaybackAfterExport() {
        AbstractAEPlayer player = viewer != null ? viewer.getAePlayer() : null;
        if (playerRepeatSaved && player != null) {
            player.setRepeat(savedPlayerRepeat);
            AEFileInputStreamInterface stream = player.getAEInputStream();
            if (stream != null) {
                stream.setRepeat(savedPlayerRepeat);
            }
            playerRepeatSaved = false;
        }
        if (marksClearedForExport && marksStream != null) {
            try {
                if (savedMarkInSet) {
                    marksStream.position(savedMarkIn);
                    marksStream.setMarkIn();
                }
                if (savedMarkOutSet) {
                    marksStream.position(savedMarkOut);
                    marksStream.setMarkOut();
                }
            } catch (Exception ignored) {
            }
            marksClearedForExport = false;
            marksStream = null;
        }
    }

    /**
     * Restores JaerAviWriter enabled/selected state from before export started.
     * Safe after finish: the AVI stream is already closed.
     */
    private void restoreWriterState() {
        if (!writerStateSaved || writer == null) {
            return;
        }
        writerStateSaved = false;
        writer.setWriteEnabled(false);
        writer.setFilterEnabled(writerWasFilterEnabled);
        writer.setAnnotationEnabled(writerWasAnnotationEnabled);
        writer.setSelected(writerWasSelected);
        if (!writerWasSelected && writer.getFilterPanel() != null) {
            // Collapse controls if export had expanded/selected the panel indirectly
            try {
                writer.setControlsVisible(false);
            } catch (Exception ignored) {
            }
        }
    }

    private void stopRecording(ActionEvent e) {
        if (writer != null && writer.isRecordingActive()) {
            writer.doFinishRecording();
            // propertyChange handles post-process
        } else {
            updateRecordingUi(false);
        }
    }

    private void attachWriterListener() {
        if (writer != null && !listeningToWriter) {
            writer.getSupport().addPropertyChangeListener(this);
            listeningToWriter = true;
        }
    }

    private void detachWriterListener() {
        if (writer != null && listeningToWriter) {
            writer.getSupport().removePropertyChangeListener(this);
            listeningToWriter = false;
        }
        if (writer != null) {
            writer.setShowCloseOnRewindDialog(true);
        }
    }

    private void updateRecordingUi(boolean recording) {
        startButton.setEnabled(!recording);
        stopButton.setEnabled(recording);
        pathField.setEnabled(!recording);
        formatCombo.setEnabled(!recording);
        matchViewerRateCb.setEnabled(!recording);
        writeTimecodeCb.setEnabled(!recording);
        convertMp4Cb.setEnabled(!recording);
        deleteAviCb.setEnabled(!recording);
        boolean playback = viewer != null && viewer.getPlayMode() == PlayMode.PLAYBACK;
        useMarkersCb.setEnabled(!recording && playback);
        if (recentFolderCombo != null) {
            recentFolderCombo.setEnabled(!recording && recentFolderCombo.getItemCount() > 0);
        }
        syncFrameRateSpinnerEnabled();
        if (recording) {
            frameRateSpinner.setEnabled(false);
        }
        convertNowButton.setEnabled(!recording && aviFile != null && aviFile.isFile()
                && FfmpegMp4Converter.isFfmpegAvailable() && (pendingMp4Convert || convertMp4Cb.isSelected()));
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (AbstractAviWriter.EVENT_RECORDING_ACTIVE.equals(evt.getPropertyName())) {
            boolean active = Boolean.TRUE.equals(evt.getNewValue());
            if (active) {
                javax.swing.SwingUtilities.invokeLater(() -> updateRecordingUi(true));
            } else {
                javax.swing.SwingUtilities.invokeLater(this::onRecordingFinished);
            }
        } else if ("framesWritten".equals(evt.getPropertyName()) && evt.getNewValue() instanceof Integer) {
            final Object v = evt.getNewValue();
            javax.swing.SwingUtilities.invokeLater(() -> statusLabel.setText("Frames written: " + v));
        }
    }

    private void onRecordingFinished() {
        updateRecordingUi(false);
        detachWriterListener();
        restorePlaybackAfterExport();
        restoreWriterState();
        final File avi = aviFile != null ? aviFile : (writer != null ? writer.getFile() : null);
        if (avi != null) {
            aviFile = avi;
        }
        int frames = writer != null ? writer.getFramesWritten() : 0;
        statusLabel.setText("Finished AVI (" + frames + " frames): " + (avi != null ? avi.getName() : ""));

        if (!convertToMp4) {
            pendingMp4Convert = false;
            showExportSavedDialog(avi,
                    "<html>Saved AVI with " + frames + " frames:<br>"
                    + (avi != null ? avi.getAbsolutePath() : ""));
            return;
        }

        // Re-check now (user may have installed ffmpeg during recording)
        if (!FfmpegMp4Converter.isFfmpegAvailable()) {
            pendingMp4Convert = true;
            updateFfmpegStatus();
            FfmpegMp4Converter.showMissingFfmpegDialog(this, avi);
            return;
        }

        pendingMp4Convert = false;
        statusLabel.setText("Converting to MP4 with ffmpeg…");
        FfmpegMp4Converter.convertAviToMp4Async(this, avi, mp4File, deleteAviAfterMp4, (success, out, message) -> {
            if (success) {
                statusLabel.setText("MP4 written: " + (out != null ? out.getName() : ""));
                showExportSavedDialog(out != null ? out : avi,
                        "<html>Saved MP4 (" + frames + " frames):<br>"
                        + (out != null ? out.getAbsolutePath() : message));
            } else if (!"ffmpeg not found".equals(message)) {
                pendingMp4Convert = true;
                statusLabel.setText("MP4 convert failed; AVI kept");
                showExportSavedDialog(avi, ffmpegFailHtml(message, avi));
            } else {
                pendingMp4Convert = true;
                statusLabel.setText("ffmpeg missing; AVI kept");
            }
            updateFfmpegStatus();
        });
    }

    /**
     * Confirmation after a successful (or AVI-kept) export: Show folder / Play video / OK.
     */
    private void showExportSavedDialog(File videoFile, String htmlMsg) {
        rememberRecentExport(videoFile);
        if (videoFile == null) {
            JOptionPane.showMessageDialog(this, htmlMsg.replaceAll("<[^>]+>", " "),
                    "Export video", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final File toOpen = videoFile;
        ShowFolderSaveConfirmation dialog = new ShowFolderSaveConfirmation(
                this,
                videoFile,
                htmlMsg,
                () -> ShowFolderSaveConfirmation.openWithDesktop(toOpen),
                "Play video",
                "Export video",
                "Open with the system default video player");
        dialog.setVisible(true);
    }

    private void rememberRecentExport(File videoFile) {
        if (videoFile == null || !videoFile.isFile() || videoFile.length() == 0
                || viewer == null || viewer.getRecentFiles() == null) {
            return;
        }
        viewer.getRecentFiles().addFile(videoFile);
        if (recentFolderCombo != null) {
            recentFolderCombo.refresh();
        }
    }

    private static String ffmpegFailHtml(String summary, File avi) {
        String escaped = ShowFolderSaveConfirmation.escapeHtml(summary != null ? summary : "ffmpeg failed");
        escaped = escaped.replace("\n", "<br>");
        String aviPath = avi != null ? ShowFolderSaveConfirmation.escapeHtml(avi.getAbsolutePath()) : "";
        return "<html>AVI was saved, but MP4 conversion failed.<br><br>"
                + escaped
                + (aviPath.isEmpty() ? "" : "<br><br>AVI kept:<br>" + aviPath);
    }

    /**
     * Opens the dialog for the given viewer (or brings an existing one forward).
     */
    public static void showDialog(AEViewer viewer) {
        if (viewer == null) {
            return;
        }
        ExportVideoDialog existing = findForViewer(viewer);
        if (existing != null) {
            existing.applyAedatDefaults();
            existing.toFront();
            return;
        }
        ExportVideoDialog d = new ExportVideoDialog(viewer);
        d.setVisible(true);
    }

    /** Stops an active export recording if any export window for this viewer is recording. */
    public static void stopActiveExport(AEViewer viewer) {
        if (viewer == null) {
            return;
        }
        ExportVideoDialog d = findForViewer(viewer);
        if (d != null && d.writer != null && d.writer.isRecordingActive()) {
            d.stopRecording(null);
        }
        // also stop writer directly if dialog closed but filter still recording from export
        JaerAviWriter w = findJaerAviWriter(viewer);
        if (w != null && w.isRecordingActive()) {
            w.doFinishRecording();
        }
    }

    /** True if JaerAviWriter (or export dialog) currently has an open video recording. */
    public static boolean isExportRecordingActive(AEViewer viewer) {
        if (viewer == null) {
            return false;
        }
        JaerAviWriter w = findJaerAviWriter(viewer);
        return w != null && w.isRecordingActive();
    }

    /** Stops recording if needed and disposes the export window for this viewer. */
    public static void disposeForViewer(AEViewer viewer) {
        stopActiveExport(viewer);
        ExportVideoDialog d = findForViewer(viewer);
        if (d != null) {
            d.dispose();
        }
    }

    private static ExportVideoDialog findForViewer(AEViewer viewer) {
        if (viewer == null) {
            return null;
        }
        for (Window w : Window.getWindows()) {
            if (w instanceof ExportVideoDialog && w.isDisplayable()) {
                ExportVideoDialog d = (ExportVideoDialog) w;
                if (d.viewer == viewer) {
                    return d;
                }
            }
        }
        return null;
    }
}
