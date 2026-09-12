package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Confirmation after saving a file: message plus optional Show folder / play /
 * OK buttons. Packed to content and centered on the owner; {@link WindowSaver.DontRestore}
 * so a saved bounds restore cannot override that.
 */
public class ShowFolderSaveConfirmation extends JDialog implements WindowSaver.DontRestore {

    static final private Logger log = Logger.getLogger("net.sf.jaer");

    final File file;
    final String msg;

    /**
     * Constructs a new dialog that shows a message and if desktop is supported,
     * a button that shows the folder that the file is in
     *
     * @param owner the owner of the dialog, or null
     * @param file the File
     * @param msg the message
     */
    public ShowFolderSaveConfirmation(Window owner, File file, String msg) {
        this(owner, file, msg, null, null, "File saved");
    }

    /**
     * Constructs a new dialog that shows a message, optional folder button, and
     * optional Playback button. {@code playAction} should open the file in
     * AEViewer (e.g. {@code AEPlayer.startPlayback}); it is not the OS default app.
     */
    public ShowFolderSaveConfirmation(Window owner, File file, String msg, Runnable playAction) {
        this(owner, file, msg, playAction, "Playback", "File saved",
                "Open this file in AEViewer for playback");
    }

    /**
     * Constructs a new dialog with custom play-button label and title.
     *
     * @param owner the owner of the dialog, or null
     * @param file the File (used for Show folder parent path)
     * @param msg the message (HTML allowed)
     * @param playAction if non-null, adds a play button that runs this action
     *        (typically {@code AEViewer.openAedatInputFile} / {@code AEPlayer.startPlayback})
     * @param playButtonLabel label for the play button (e.g. "Play exported file"); ignored if playAction is null
     * @param title dialog title
     */
    public ShowFolderSaveConfirmation(Window owner, File file, String msg, Runnable playAction,
            String playButtonLabel, String title) {
        this(owner, file, msg, playAction, playButtonLabel, title, "Open this file in AEViewer for playback");
    }

    /**
     * Full constructor.
     *
     * @param playTooltip tooltip for the play button; ignored if playAction is null
     */
    public ShowFolderSaveConfirmation(Window owner, File file, String msg, Runnable playAction,
            String playButtonLabel, String title, String playTooltip) {
        super(owner);
        this.file = file;
        this.msg = msg;
        setResizable(true);
        if (getContentPane() instanceof JPanel panel) {
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // top, left, bottom, right
        }
        setTitle(title != null ? title : "File saved");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.DOCUMENT_MODAL);
        getContentPane().setLayout(new BorderLayout());
        JLabel msgLabel = new JLabel(msg);
        add(msgLabel, BorderLayout.CENTER);
        JPanel buts = new JPanel();
        buts.setLayout(new FlowLayout());

        if (Desktop.isDesktopSupported() && file != null) {
            final JButton showFileLocationButton = new JButton("Show folder");
            showFileLocationButton.setToolTipText("Open the folder containing the saved file");
            final File toReveal = file;
            showFileLocationButton.addActionListener((ActionEvent e) -> {
                try {
                    showInFileManager(toReveal);
                } finally {
                    dispose();
                }
            });
            buts.add(showFileLocationButton);

        }
        if (playAction != null) {
            JButton playB = new JButton(playButtonLabel != null ? playButtonLabel : "Playback");
            playB.setToolTipText(playTooltip != null ? playTooltip : "Open this file in AEViewer for playback");
            playB.addActionListener((ActionEvent e) -> {
                dispose();
                playAction.run();
            });
            buts.add(playB);
        }
        JButton okB = new JButton("OK");
        okB.addActionListener((ActionEvent e) -> {
            dispose();
        });

        buts.add(okB);
        add(buts, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okB);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Center on the owner after {@link #pack()}. Not always-on-top: that covers
     * a still-open File Open chooser and the chooser's modal pump then ignores
     * clicks on this dialog.
     */
    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            pack();
            setLocationRelativeTo(getOwner());
            super.setVisible(true);
            return;
        }
        super.setVisible(false);
    }

    /**
     * True when a {@link JFileChooser} dialog (File → Open / Save) is showing.
     * Save As completion can fire on the EDT nested in that modal pump.
     */
    public static Window findShowingFileChooserDialog() {
        for (Window w : Window.getWindows()) {
            if (w != null && w.isShowing() && containsFileChooser(w)) {
                return w;
            }
        }
        return null;
    }

    private static boolean containsFileChooser(Container c) {
        if (c instanceof JFileChooser) {
            return true;
        }
        for (Component ch : c.getComponents()) {
            if (ch instanceof Container && containsFileChooser((Container) ch)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Run {@code action} now, or after every showing file-chooser dialog closes
     * so a Save As confirmation is not stacked on File → Open.
     */
    public static void runAfterFileChoosersClose(Runnable action) {
        if (action == null) {
            return;
        }
        Window chooser = findShowingFileChooserDialog();
        if (chooser == null) {
            action.run();
            return;
        }
        log.info("Deferring dialog until file chooser closes: " + chooser.getName());
        WindowAdapter once = new WindowAdapter() {
            private boolean fired;

            private void fire() {
                if (fired) {
                    return;
                }
                fired = true;
                chooser.removeWindowListener(this);
                SwingUtilities.invokeLater(() -> runAfterFileChoosersClose(action));
            }

            @Override
            public void windowClosed(WindowEvent e) {
                fire();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                fire();
            }
        };
        chooser.addWindowListener(once);
    }

    /**
     * Convert plain text (newlines OK) to HTML body fragments for {@link JLabel}.
     */
    public static String plainToHtml(String text) {
        return escapeHtml(text).replace("\n", "<br>");
    }

    /**
     * Message for the recording-finished confirmation (path + stream summary).
     */
    public static String htmlRecordingSavedMessage(File savedFile, String fileInfo) {
        StringBuilder sb = new StringBuilder("<html>Done saving recording as<br>");
        if (savedFile != null) {
            sb.append(escapeHtml(savedFile.getAbsolutePath()));
        }
        if (fileInfo != null && !fileInfo.isEmpty()) {
            sb.append("<br>").append(plainToHtml(fileInfo));
        }
        return sb.toString();
    }

    /**
     * Message for File → Save As: original recording stats (when available) and
     * the saved file summary.
     */
    public static String htmlSaveAsMessage(File savedFile, String afterInfo, String sourceInfo) {
        StringBuilder sb = new StringBuilder("<html>Done Save As");
        if (sourceInfo != null && !sourceInfo.isEmpty()) {
            sb.append("<br><br><b>Original</b><br>").append(plainToHtml(sourceInfo));
        }
        sb.append("<br><br><b>Saved as</b>");
        if (savedFile != null) {
            sb.append("<br>").append(escapeHtml(savedFile.getAbsolutePath()));
        }
        if (afterInfo != null && !afterInfo.isEmpty()) {
            sb.append("<br>").append(plainToHtml(afterInfo));
        }
        return sb.toString();
    }

    public static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Opens the file manager at {@code file}'s folder (and selects the file
     * when {@link Desktop.Action#BROWSE_FILE_DIR} is supported).
     */
    public static void showInFileManager(File file) {
        if (file == null) {
            log.warning("Cannot show folder: file is null");
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            log.warning("Desktop operations not supported, cannot show folder for " + file);
            return;
        }
        File abs = file.getAbsoluteFile();
        File folder = abs.isDirectory() ? abs : abs.getParentFile();
        if (folder == null) {
            log.warning("Cannot show folder for " + abs + ": no parent");
            return;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (abs.isFile() && abs.exists() && desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                log.info("Showing file in folder: " + abs);
                desktop.browseFileDirectory(abs);
                return;
            }
            if (!folder.exists()) {
                log.warning("Cannot show folder for " + abs + ": " + folder + " does not exist");
                return;
            }
            log.info("Opening folder: " + folder.getAbsolutePath());
            desktop.open(folder);
        } catch (Exception ex) {
            log.warning("Could not show file location: " + ex);
        }
    }

    /**
     * Opens {@code file} with the OS default application (e.g. video player).
     * Uses {@link Desktop#open} when available, then {@code xdg-open} /
     * {@code open} / {@code cmd /c start} as a fallback.
     *
     * @return true if a launch was started
     */
    public static boolean openWithDesktop(File file) {
        if (file == null || !file.isFile()) {
            log.warning("Cannot open file: " + file);
            return false;
        }
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file);
                return true;
            } catch (Exception ex) {
                log.fine("Desktop.open failed for " + file + ": " + ex);
            }
        }
        if (openWithOsCommand(file)) {
            return true;
        }
        log.warning("Could not open " + file + " with the system application");
        return false;
    }

    private static boolean openWithOsCommand(File file) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> cmd = new ArrayList<>();
        if (os.contains("win")) {
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add("start");
            cmd.add("");
            cmd.add(file.getAbsolutePath());
        } else if (os.contains("mac")) {
            cmd.add("open");
            cmd.add(file.getAbsolutePath());
        } else {
            cmd.add("xdg-open");
            cmd.add(file.getAbsolutePath());
        }
        try {
            new ProcessBuilder(cmd).start();
            log.info("Opened with " + cmd.get(0) + ": " + file);
            return true;
        } catch (Exception e) {
            log.warning("OS open command failed (" + cmd.get(0) + "): " + e);
            return false;
        }
    }

    public static final void main(String[] args) {
        log.info("making dialog");
        final ShowFolderSaveConfirmation d = new ShowFolderSaveConfirmation(null, new File("/tmp/testfile"), "<html>Saved jkjdjk fjdk fjkd jfkdsjfkdsjkf dsjkfjdsklfj dskf dsjkfdsjkfj <br>dkfdsjkf dsjklf dsjkfdsjfkds jfkdsl <br>jfkdsjfkds djflksd jkfds");
        log.info("showing in swing thread");
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                log.info("setting visible");
                d.setVisible(true);
            }
        });
    }

}
