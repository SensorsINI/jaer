package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Confirmation after saving a file: message plus optional Show folder / play /
 * OK buttons.
 */
public class ShowFolderSaveConfirmation extends JDialog {

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
        setLocationRelativeTo(owner);
        if (getContentPane() instanceof JPanel panel) {
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // top, left, bottom, right
        }
        setTitle(title != null ? title : "File saved");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);
        getContentPane().setLayout(new BorderLayout());
        JLabel msgLabel = new JLabel(msg);
        add(msgLabel, BorderLayout.CENTER);
        JPanel buts = new JPanel();
        buts.setLayout(new FlowLayout());

        if (Desktop.isDesktopSupported() && file != null && file.getParentFile() != null) {
            final JButton showFileLocationButton = new JButton("Show folder");
            showFileLocationButton.setToolTipText("Open the folder containing the saved file");
            final File f2 = new File(file.getAbsolutePath());
            showFileLocationButton.addActionListener((ActionEvent e) -> {
                try {
                    Desktop.getDesktop().open(new File(f2.getParent()));
                } catch (Exception ex) {
                    log.warning("Could not show file location: " + ex.toString());
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
    }

    /**
     * Opens {@code file} with the OS default application (e.g. video player).
     */
    public static void openWithDesktop(File file) {
        if (file == null || !file.isFile()) {
            log.warning("Cannot open file: " + file);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            log.warning("Desktop operations not supported");
            return;
        }
        try {
            Desktop.getDesktop().open(file);
        } catch (Exception ex) {
            log.warning("Could not open " + file + ": " + ex);
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
