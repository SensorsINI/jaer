package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

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
        super(owner);
        this.file = file;
        this.msg = msg;
        setResizable(true);
        setLocationRelativeTo(owner);
        setTitle("File saved");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);
        getContentPane().setLayout(new BorderLayout());
        JLabel msgLabel = new JLabel(msg);
        add(msgLabel, BorderLayout.CENTER);
        JPanel buts = new JPanel();
        buts.setLayout(new FlowLayout());

        if (Desktop.isDesktopSupported()) {
            final JButton showFileLocationButton = new JButton("Show folder");
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
        JButton okB = new JButton("OK");
        okB.addActionListener((ActionEvent e) -> {
            dispose();
        });
        buts.add(okB);
        add(buts, BorderLayout.SOUTH);
        pack();
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
