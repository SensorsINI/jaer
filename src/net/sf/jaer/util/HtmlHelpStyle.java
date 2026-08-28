/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.util;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import net.sf.jaer.JaerConstants;

/**
 * CSS 1 rules for jAER HTML help ({@code JEditorPane} / {@code HTMLEditorKit}).
 * Swing has no global stylesheet; call {@link #apply(JEditorPane)} on each pane
 * after content is loaded. Preferences live on the AEViewer node.
 */
public final class HtmlHelpStyle {

    public static final String PREF_FAMILY = "AEViewer.helpFontFamily";
    public static final String PREF_SIZE = "AEViewer.helpFontSize";
    public static final int MIN_SIZE_PT = 8;
    public static final int MAX_SIZE_PT = 20;
    public static final int DEFAULT_SIZE_PT = 12;

    public enum HelpFontFamily {
        Serif("Serif", "Serif"),
        SansSerif("Sans Serif", "SansSerif");

        private final String displayName;
        private final String cssName;

        HelpFontFamily(String displayName, String cssName) {
            this.displayName = displayName;
            this.cssName = cssName;
        }

        /** Java logical font name for {@code font-family} in Swing CSS. */
        public String cssName() {
            return cssName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        public static HelpFontFamily fromPref(String stored) {
            if (stored == null || stored.isEmpty()) {
                return SansSerif;
            }
            for (HelpFontFamily v : values()) {
                if (v.name().equalsIgnoreCase(stored) || v.cssName.equalsIgnoreCase(stored)
                        || v.displayName.equalsIgnoreCase(stored)) {
                    return v;
                }
            }
            return SansSerif;
        }
    }

    private static final WeakHashMap<JEditorPane, Boolean> PANES = new WeakHashMap<>();

    private HtmlHelpStyle() {
    }

    private static Preferences prefs() {
        return JaerConstants.PREFS_ROOT.node("AEViewer");
    }

    public static HelpFontFamily getFamily() {
        return HelpFontFamily.fromPref(prefs().get(PREF_FAMILY, HelpFontFamily.SansSerif.name()));
    }

    public static void setFamily(HelpFontFamily family) {
        if (family == null) {
            family = HelpFontFamily.SansSerif;
        }
        prefs().put(PREF_FAMILY, family.name());
        refreshOpen();
    }

    public static int getSizePt() {
        return clampSize(prefs().getInt(PREF_SIZE, DEFAULT_SIZE_PT));
    }

    public static void setSizePt(int sizePt) {
        int clamped = clampSize(sizePt);
        if (clamped == getSizePt()) {
            prefs().putInt(PREF_SIZE, clamped);
            return;
        }
        prefs().putInt(PREF_SIZE, clamped);
        refreshOpen();
    }

    /** Change help body size by {@code delta} points and persist. */
    public static void adjustSizeBy(int delta) {
        setSizePt(getSizePt() + delta);
    }

    /**
     * Scroll pane that zooms on Ctrl+wheel and otherwise scrolls the document.
     * Do not put a {@code MouseWheelListener} on the editor: that steals wheel
     * events from {@link JScrollPane}.
     */
    public static JScrollPane createZoomingScrollPane(JEditorPane pane) {
        JScrollPane scroll = new JScrollPane(pane) {
            @Override
            protected void processMouseWheelEvent(MouseWheelEvent e) {
                if (handleZoomWheel(e)) {
                    e.consume();
                    return;
                }
                super.processMouseWheelEvent(e);
            }
        };
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /**
     * Ctrl+wheel (via {@link #createZoomingScrollPane}) and Ctrl+= / Ctrl+-
     * (and numpad +/−) change {@link #getSizePt()}.
     */
    public static void installZoom(JRootPane root, JEditorPane pane) {
        if (root == null || pane == null) {
            return;
        }
        pane.setToolTipText("Ctrl+mouse wheel or Ctrl+= / Ctrl+- to zoom this help text");
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrl), "help-zoom-in");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrl | InputEvent.SHIFT_DOWN_MASK), "help-zoom-in");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ctrl), "help-zoom-in");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ctrl), "help-zoom-out");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ctrl), "help-zoom-out");
        am.put("help-zoom-in", new AbstractAction("Zoom help text in") {
            @Override
            public void actionPerformed(ActionEvent e) {
                adjustSizeBy(1);
            }
        });
        am.put("help-zoom-out", new AbstractAction("Zoom help text out") {
            @Override
            public void actionPerformed(ActionEvent e) {
                adjustSizeBy(-1);
            }
        });
    }

    private static boolean handleZoomWheel(MouseWheelEvent e) {
        if (!e.isControlDown()) {
            return false;
        }
        int notches = e.getWheelRotation();
        if (notches == 0) {
            double precise = e.getPreciseWheelRotation();
            if (Math.abs(precise) < 0.5) {
                return true;
            }
            notches = precise < 0 ? -1 : 1;
        }
        adjustSizeBy(-Integer.signum(notches));
        return true;
    }

    public static int clampSize(int sizePt) {
        if (sizePt < MIN_SIZE_PT) {
            return MIN_SIZE_PT;
        }
        if (sizePt > MAX_SIZE_PT) {
            return MAX_SIZE_PT;
        }
        return sizePt;
    }

    /**
     * Apply current family/size via {@link StyleSheet#addRule(String)} and
     * remember the pane so preference changes update open help windows.
     */
    public static void apply(JEditorPane pane) {
        if (pane == null) {
            return;
        }
        synchronized (PANES) {
            PANES.put(pane, Boolean.TRUE);
        }
        applyRules(pane);
    }

    public static void refreshOpen() {
        Runnable r = () -> {
            List<JEditorPane> copy;
            synchronized (PANES) {
                copy = new ArrayList<>(PANES.keySet());
            }
            for (JEditorPane pane : copy) {
                applyRules(pane);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private static void applyRules(JEditorPane pane) {
        if (pane.getDocument() == null) {
            return;
        }
        StyleSheet ss = null;
        if (pane.getDocument() instanceof HTMLDocument) {
            ss = ((HTMLDocument) pane.getDocument()).getStyleSheet();
        } else if (pane.getEditorKit() instanceof HTMLEditorKit) {
            ss = ((HTMLEditorKit) pane.getEditorKit()).getStyleSheet();
        }
        if (ss == null) {
            return;
        }
        String family = getFamily().cssName();
        int size = getSizePt();
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(new Font(family, Font.PLAIN, size));
        ss.addRule("body { font-family: " + family + "; font-size: " + size + "pt; }");
        ss.addRule("p { font-family: " + family + "; font-size: " + size + "pt; }");
        ss.addRule("li { font-family: " + family + "; font-size: " + size + "pt; }");
        ss.addRule("td { font-family: " + family + "; font-size: " + size + "pt; }");
        ss.addRule("th { font-family: " + family + "; font-size: " + size + "pt; }");
        ss.addRule("div { font-family: " + family + "; font-size: " + size + "pt; }");
        ss.addRule("h1 { font-family: " + family + "; }");
        ss.addRule("h2 { font-family: " + family + "; }");
        ss.addRule("h3 { font-family: " + family + "; }");
        ss.addRule("h4 { font-family: " + family + "; }");
        ss.addRule("code { font-family: Monospaced; font-size: " + size + "pt; }");
        ss.addRule("pre { font-family: Monospaced; font-size: " + size + "pt; }");
        pane.revalidate();
        pane.repaint();
    }
}
