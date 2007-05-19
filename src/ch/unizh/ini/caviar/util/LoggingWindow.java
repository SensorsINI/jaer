package ch.unizh.ini.caviar.util;
import java.awt.*;
import javax.swing.*;

/** A frame with text area to show logging results in. */
public class LoggingWindow extends JFrame {
    private JTextArea textArea;
    
    public LoggingWindow(String title, final int width,
            final int height) {
        super(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // exit if the user clicks the close button on uncaught exception
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                setSize(width, height);
                textArea = new JTextArea();
                JScrollPane pane = new JScrollPane(textArea);
                textArea.setEditable(false);
                getContentPane().add(pane);
                setVisible(true);
            }
        });
    }
    
    public void addLogInfo(final String data) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                textArea.append(data);
            }
        });
    }
    
}

