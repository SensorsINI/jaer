/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventprocessing;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;

/**
 * Nonmodal HTML help dialog for an {@link EventFilter}. URLs in {@code <a href>}
 * are opened with the system browser. Relative {@code <img src>} / {@code href}
 * (e.g. {@code pig.png} next to the filter class) resolve against the class
 * package on the classpath.
 *
 * @author tobi
 * @see EventFilter#showHelpDialog()
 * @see net.sf.jaer.Help
 */
public class EventFilterHelpDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final int PREFERRED_WIDTH = 640;
    private static final int PREFERRED_HEIGHT = 520;

    public EventFilterHelpDialog(Window parent, EventFilter filter, String html) {
        super(parent, "Help — " + filter.getClass().getSimpleName(), ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        URL base = packageBaseUrl(filter.getClass());
        if (base != null && pane.getDocument() instanceof HTMLDocument) {
            ((HTMLDocument) pane.getDocument()).setBase(base);
        }
        pane.setText(wrapHtml(html));
        pane.setCaretPosition(0);
        pane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
                    return;
                }
                if (!Desktop.isDesktopSupported() || e.getURL() == null) {
                    return;
                }
                try {
                    URL url = e.getURL();
                    if ("file".equalsIgnoreCase(url.getProtocol())) {
                        Desktop.getDesktop().open(new File(url.toURI()));
                    } else {
                        Desktop.getDesktop().browse(url.toURI());
                    }
                } catch (IOException | URISyntaxException ex) {
                    JOptionPane.showMessageDialog(EventFilterHelpDialog.this, ex.toString(), "Could not open link",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);

        getContentPane().setLayout(new BorderLayout(0, 0));
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(close);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-help");
        getRootPane().getActionMap().put("close-help", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                pane.setCaretPosition(0);
            }
        });

        pack();
        setLocationRelativeTo(parent);
        setMinimumSize(new Dimension(360, 240));
    }

    /**
     * Directory URL of {@code clazz} on the classpath (folder or jar entry),
     * so relative Help resources like {@code pig.png} resolve.
     */
    static URL packageBaseUrl(Class<?> clazz) {
        URL classUrl = clazz.getResource(clazz.getSimpleName() + ".class");
        if (classUrl == null) {
            return null;
        }
        try {
            String s = classUrl.toExternalForm();
            int cut = s.lastIndexOf('/');
            if (cut < 0) {
                return null;
            }
            return new URL(s.substring(0, cut + 1));
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private static String wrapHtml(String html) {
        if (html == null) {
            html = "";
        }
        String trimmed = html.trim();
        if (trimmed.regionMatches(true, 0, "<html", 0, 5)) {
            return trimmed;
        }
        JLabel label = new JLabel();
        Font font = label.getFont();
        String style = "font-family:" + font.getFamily() + ";font-size:" + font.getSize() + "pt;";
        return "<html><body style=\"" + style + "\">" + trimmed + "</body></html>";
    }
}
