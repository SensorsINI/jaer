/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;

import net.sf.jaer.JaerConstants;
import net.sf.jaer.util.WindowSaver;

/**
 * Nonmodal quick-help window. Implemented as a {@link JFrame} (not an owned
 * {@code JDialog}) so it can go behind AEViewer. Contents come from
 * {@code /net/sf/jaer/quickhelp.html} on the classpath.
 * <p>
 * Type to incremental-search; Esc cancels the query (or hides if none);
 * F3 / Shift+F3 cycle matches.
 */
public class AEViewerQuickHelpFrame extends JFrame implements WindowSaver.DontResize {

    private static final long serialVersionUID = 1L;
    private static final int PREFERRED_WIDTH = 720;
    private static final int PREFERRED_HEIGHT = 640;
    static final String RESOURCE = "/net/sf/jaer/quickhelp.html";

    private static final Color MATCH_COLOR = new Color(255, 255, 120);
    private static final Color CURRENT_MATCH_COLOR = new Color(255, 165, 0);

    private final AEViewer viewer;
    private final JEditorPane pane;
    private final JLabel searchStatus;
    private final StringBuilder searchQuery = new StringBuilder();
    private final List<int[]> matches = new ArrayList<int[]>();
    private int currentMatch = -1;
    private final Highlighter.HighlightPainter matchPainter
            = new DefaultHighlighter.DefaultHighlightPainter(MATCH_COLOR);
    private final Highlighter.HighlightPainter currentPainter
            = new DefaultHighlighter.DefaultHighlightPainter(CURRENT_MATCH_COLOR);
    private final java.awt.KeyEventDispatcher searchDispatcher = e -> {
        if (!isActive()) {
            return false;
        }
        if (e.getID() == KeyEvent.KEY_TYPED) {
            handleSearchKeyTyped(e);
            return e.isConsumed();
        }
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            handleSearchKeyPressed(e);
            return e.isConsumed();
        }
        return false;
    };

    public AEViewerQuickHelpFrame(AEViewer viewer) {
        super("Quick help / Shortcuts");
        this.viewer = viewer;
        setName("AEViewerQuickHelp");
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        if (viewer != null) {
            setIconImage(viewer.getIconImage());
        }

        pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        URL url = AEViewerQuickHelpFrame.class.getResource(RESOURCE);
        try {
            if (url != null) {
                pane.setPage(url);
            } else {
                pane.setText("<html><body><p>Missing classpath resource " + RESOURCE
                        + ".</p><p><a href=\"" + JaerConstants.HELP_URL_USER_GUIDE
                        + "\">See user guide</a></p></body></html>");
            }
        } catch (IOException e) {
            pane.setText("<html><body><p>Could not load quick help: " + e
                    + "</p><p><a href=\"" + JaerConstants.HELP_URL_USER_GUIDE
                    + "\">See user guide</a></p></body></html>");
        }
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
                    URL target = e.getURL();
                    if ("file".equalsIgnoreCase(target.getProtocol())) {
                        Desktop.getDesktop().open(new File(target.toURI()));
                    } else {
                        Desktop.getDesktop().browse(target.toURI());
                    }
                } catch (IOException | URISyntaxException ex) {
                    JOptionPane.showMessageDialog(AEViewerQuickHelpFrame.this, ex.toString(),
                            "Could not open link", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(searchDispatcher);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .removeKeyEventDispatcher(searchDispatcher);
            }
        });

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton close = new JButton("Close");
        close.addActionListener(e -> setVisible(false));
        searchStatus = new JLabel("Type to search");
        searchStatus.setForeground(Color.DARK_GRAY);
        JPanel buttons = new JPanel(new BorderLayout());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(close);
        buttons.add(searchStatus, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout(0, 0));
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(null);

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc-quick-help");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "toggle-quick-help");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0), "find-next");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, KeyEvent.SHIFT_DOWN_MASK), "find-prev");
        getRootPane().getActionMap().put("esc-quick-help", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (searchQuery.length() > 0) {
                    clearSearch();
                } else {
                    setVisible(false);
                }
            }
        });
        getRootPane().getActionMap().put("toggle-quick-help", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (viewer != null) {
                    viewer.toggleQuickHelp();
                } else {
                    setVisible(false);
                }
            }
        });
        getRootPane().getActionMap().put("find-next", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cycleMatch(1);
            }
        });
        getRootPane().getActionMap().put("find-prev", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cycleMatch(-1);
            }
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowActivated(java.awt.event.WindowEvent e) {
                pane.requestFocusInWindow();
            }
        });

        pack();
        setLocationRelativeTo(viewer);
        setMinimumSize(new Dimension(400, 280));
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        if (b) {
            pane.requestFocusInWindow();
        }
    }

    private void handleSearchKeyPressed(KeyEvent e) {
        if (e.isConsumed()) {
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            if (searchQuery.length() > 0) {
                searchQuery.setLength(searchQuery.length() - 1);
                runSearch(true);
            }
            e.consume();
        }
    }

    private void handleSearchKeyTyped(KeyEvent e) {
        if (e.isConsumed()) {
            return;
        }
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown()) {
            return;
        }
        char c = e.getKeyChar();
        if (c == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(c)) {
            return;
        }
        searchQuery.append(c);
        runSearch(true);
        e.consume();
    }

    private void clearSearch() {
        searchQuery.setLength(0);
        matches.clear();
        currentMatch = -1;
        pane.getHighlighter().removeAllHighlights();
        searchStatus.setText("Type to search");
        searchStatus.setForeground(Color.DARK_GRAY);
    }

    private void runSearch(boolean keepFirstMatch) {
        pane.getHighlighter().removeAllHighlights();
        matches.clear();
        String q = searchQuery.toString();
        if (q.isEmpty()) {
            currentMatch = -1;
            searchStatus.setText("Type to search");
            searchStatus.setForeground(Color.DARK_GRAY);
            return;
        }
        Document doc = pane.getDocument();
        String haystack;
        try {
            haystack = doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            searchStatus.setText("Search failed");
            return;
        }
        String needle = q.toLowerCase();
        String lower = haystack.toLowerCase();
        int from = 0;
        while (from < lower.length()) {
            int at = lower.indexOf(needle, from);
            if (at < 0) {
                break;
            }
            matches.add(new int[] { at, at + q.length() });
            from = at + Math.max(1, q.length());
        }
        if (matches.isEmpty()) {
            currentMatch = -1;
            searchStatus.setText("Find: \"" + q + "\"  (no match)");
            searchStatus.setForeground(Color.RED.darker());
            return;
        }
        if (keepFirstMatch || currentMatch < 0 || currentMatch >= matches.size()) {
            currentMatch = 0;
        }
        applyHighlights();
        updateSearchStatus();
        scrollToCurrent();
    }

    private void cycleMatch(int delta) {
        if (searchQuery.length() == 0) {
            return;
        }
        if (matches.isEmpty()) {
            runSearch(true);
            return;
        }
        currentMatch = Math.floorMod(currentMatch + delta, matches.size());
        applyHighlights();
        updateSearchStatus();
        scrollToCurrent();
    }

    private void applyHighlights() {
        Highlighter hl = pane.getHighlighter();
        hl.removeAllHighlights();
        for (int i = 0; i < matches.size(); i++) {
            int[] span = matches.get(i);
            try {
                hl.addHighlight(span[0], span[1], i == currentMatch ? currentPainter : matchPainter);
            } catch (BadLocationException ignored) {
            }
        }
    }

    private void updateSearchStatus() {
        searchStatus.setForeground(Color.DARK_GRAY);
        searchStatus.setText("Find: \"" + searchQuery + "\"  " + (currentMatch + 1) + "/" + matches.size()
                + "   (F3 / Shift+F3, Esc)");
    }

    private void scrollToCurrent() {
        if (currentMatch < 0 || currentMatch >= matches.size()) {
            return;
        }
        int pos = matches.get(currentMatch)[0];
        try {
            Rectangle r = pane.modelToView(pos);
            if (r != null) {
                r.grow(0, 40);
                pane.scrollRectToVisible(r);
            }
        } catch (BadLocationException ignored) {
        }
    }
}
