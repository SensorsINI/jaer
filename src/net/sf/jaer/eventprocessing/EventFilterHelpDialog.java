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
import java.io.IOException;
import java.net.URISyntaxException;

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

/**
 * Nonmodal HTML help dialog for an {@link EventFilter}. URLs in {@code <a href>}
 * are opened with the system browser.
 *
 * @author tobi
 * @see EventFilter#showHelpDialog()
 * @see net.sf.jaer.Help
 */
public class EventFilterHelpDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final int PREFERRED_WIDTH = 560;
    private static final int PREFERRED_HEIGHT = 480;

    public EventFilterHelpDialog(Window parent, EventFilter filter, String html) {
        super(parent, "Help — " + filter.getClass().getSimpleName(), ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JEditorPane pane = new JEditorPane("text/html", wrapHtml(html));
        pane.setEditable(false);
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
                    Desktop.getDesktop().browse(e.getURL().toURI());
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
