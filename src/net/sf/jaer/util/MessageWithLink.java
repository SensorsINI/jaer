package net.sf.jaer.util;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * From
 * https://stackoverflow.com/questions/8348063/clickable-links-in-joptionpane
 *
 * Usage: JOptionPane.showMessageDialog(null, new MessageWithLink("Here is a
 * link on <a href=\"http://www.google.com\">http://www.google.com</a>"));
 *
 * @author tobid
 */
public class MessageWithLink extends JEditorPane {

    private static final long serialVersionUID = 1L;

    public MessageWithLink(String htmlBody) {
        super("text/html", "<html><body style=\"" + getStyle() + "\">" + htmlBody + "</body></html>");
        addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                    // Process the click event on the link (for example with java.awt.Desktop.getDesktop().browse())
//                    System.out.println(e.getURL() + " was clicked");
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().browse(e.getURL().toURI());
                        } catch (IOException|URISyntaxException ex) {
                            JOptionPane.showMessageDialog(null, ex.toString(),"Error",JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });
        setEditable(false);
        setBorder(null);
    }

    static StringBuffer getStyle() {
        // for copying style
        JLabel label = new JLabel();
        Font font = label.getFont();
        Color color = label.getBackground();

        // create some css from the label's font
        StringBuffer style = new StringBuffer("font-family:" + font.getFamily() + ";");
        style.append("font-weight:" + (font.isBold() ? "bold" : "normal") + ";");
        style.append("font-size:" + font.getSize() + "pt;");
        style.append("background-color: rgb(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ");");
        return style;
    }

    public static void main(String[] args) {
        JOptionPane.showMessageDialog(null, new MessageWithLink("Here is a link on <a href=\"http://www.google.com\">http://www.google.com</a>"));

    }
}
