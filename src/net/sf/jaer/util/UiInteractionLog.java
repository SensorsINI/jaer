/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.util;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionListener;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.RootPaneContainer;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import net.sf.jaer.JaerConstants;

/**
 * Optional JSONL log of menu items and keyboard shortcuts under
 * {@code ${java.io.tmpdir}/jaer/interactions/}. Off by default; enable with
 * File → Preferences → Collect usage data. Does not log mouse motion or wheel.
 */
public final class UiInteractionLog {

    public static final String PREF_KEY = "collectUsageData";
    private static final Logger log = Logger.getLogger(UiInteractionLog.class.getName());
    private static final Object LOCK = new Object();
    private static final String INSTALLED = "jaer.uiLog.installed";

    private static volatile boolean enabled;
    private static AWTEventListener windowListener;
    private static ChangeListener menuPathListener;
    private static java.awt.KeyEventPostProcessor keyPost;
    private static BufferedWriter writer;
    private static File currentFile;

    private static final ActionListener ITEM_LOGGER = e -> {
        if (!enabled) {
            return;
        }
        Object src = e.getSource();
        if (src instanceof JMenuItem && !(src instanceof JMenu)) {
            logMenuItem((JMenuItem) src, viaFromQueue());
        }
    };

    private static final MenuListener MENU_WALKER = new MenuListener() {
        @Override
        public void menuSelected(MenuEvent e) {
            if (e.getSource() instanceof JMenu) {
                walkMenu((JMenu) e.getSource());
            }
        }

        @Override
        public void menuDeselected(MenuEvent e) {
        }

        @Override
        public void menuCanceled(MenuEvent e) {
        }
    };

    private UiInteractionLog() {
    }

    public static boolean isEnabled() {
        return JaerConstants.PREFS_ROOT.getBoolean(PREF_KEY, false);
    }

    public static void setEnabled(boolean on) {
        JaerConstants.PREFS_ROOT.putBoolean(PREF_KEY, on);
        syncFromPrefs();
    }

    /** Directory {@code …/jaer/interactions} (created when logging starts). */
    public static File directory() {
        return new File(JaerTmpdir.get(), "interactions");
    }

    public static void syncFromPrefs() {
        boolean on = isEnabled();
        synchronized (LOCK) {
            if (on == enabled) {
                return;
            }
            if (on) {
                startLocked();
            } else {
                stopLocked();
            }
        }
    }

    private static void startLocked() {
        try {
            File dir = directory();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Could not create " + dir.getAbsolutePath());
            }
            currentFile = new File(dir, "ui-" + Instant.now().toString().replace(':', '-') + ".jsonl");
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(currentFile, true),
                    StandardCharsets.UTF_8));
            enabled = true;
            windowListener = UiInteractionLog::windowEvent;
            Toolkit.getDefaultToolkit().addAWTEventListener(windowListener, AWTEvent.WINDOW_EVENT_MASK);
            menuPathListener = e -> {
                MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
                for (MenuElement el : path) {
                    if (el instanceof JMenu) {
                        walkMenu((JMenu) el);
                    } else if (el instanceof JMenuItem) {
                        installOnItem((JMenuItem) el);
                    } else if (el instanceof JPopupMenu) {
                        walkPopup((JPopupMenu) el);
                    }
                }
            };
            MenuSelectionManager.defaultManager().addChangeListener(menuPathListener);
            keyPost = UiInteractionLog::postProcessKey;
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventPostProcessor(keyPost);
            for (Window w : Window.getWindows()) {
                installOnWindow(w);
            }
            writeLineLocked("{\"ts\":\"" + Instant.now() + "\",\"kind\":\"session\",\"file\":\""
                    + jsonEscape(currentFile.getAbsolutePath()) + "\"}");
            log.info("UI interaction log (menus/shortcuts): " + currentFile.getAbsolutePath());
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not start UI interaction log", e);
            stopLocked();
        }
    }

    private static void stopLocked() {
        enabled = false;
        if (windowListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(windowListener);
            windowListener = null;
        }
        if (menuPathListener != null) {
            MenuSelectionManager.defaultManager().removeChangeListener(menuPathListener);
            menuPathListener = null;
        }
        if (keyPost != null) {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventPostProcessor(keyPost);
            keyPost = null;
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.log(Level.FINE, "close interaction log", e);
            }
            writer = null;
        }
        currentFile = null;
    }

    private static void windowEvent(AWTEvent event) {
        if (!enabled || !(event instanceof WindowEvent)) {
            return;
        }
        if (event.getID() == WindowEvent.WINDOW_OPENED || event.getID() == WindowEvent.WINDOW_ACTIVATED) {
            Object src = event.getSource();
            if (src instanceof Window) {
                installOnWindow((Window) src);
            }
        }
    }

    private static void installOnWindow(Window w) {
        if (w instanceof JFrame) {
            installMenuBar(((JFrame) w).getJMenuBar());
        } else if (w instanceof JDialog) {
            installMenuBar(((JDialog) w).getJMenuBar());
        }
    }

    private static void installMenuBar(JMenuBar bar) {
        if (bar == null) {
            return;
        }
        if (bar.getClientProperty(INSTALLED) == null) {
            bar.putClientProperty(INSTALLED, Boolean.TRUE);
            bar.addContainerListener(new ContainerAdapter() {
                @Override
                public void componentAdded(ContainerEvent e) {
                    Component c = e.getChild();
                    if (c instanceof JMenu) {
                        walkMenu((JMenu) c);
                    }
                }
            });
        }
        for (int i = 0; i < bar.getMenuCount(); i++) {
            walkMenu(bar.getMenu(i));
        }
    }

    private static void walkMenu(JMenu menu) {
        if (menu == null) {
            return;
        }
        if (menu.getClientProperty(INSTALLED + ".walk") == null) {
            menu.putClientProperty(INSTALLED + ".walk", Boolean.TRUE);
            menu.addMenuListener(MENU_WALKER);
        }
        for (int i = 0; i < menu.getMenuComponentCount(); i++) {
            Component c = menu.getMenuComponent(i);
            if (c instanceof JMenu) {
                walkMenu((JMenu) c);
            } else if (c instanceof JMenuItem) {
                installOnItem((JMenuItem) c);
            }
        }
    }

    private static void walkPopup(JPopupMenu popup) {
        if (popup == null) {
            return;
        }
        for (Component c : popup.getComponents()) {
            if (c instanceof JMenu) {
                walkMenu((JMenu) c);
            } else if (c instanceof JMenuItem) {
                installOnItem((JMenuItem) c);
            }
        }
    }

    private static void installOnItem(JMenuItem item) {
        if (item == null || item instanceof JMenu) {
            return;
        }
        if (item.getClientProperty(INSTALLED) != null) {
            return;
        }
        item.putClientProperty(INSTALLED, Boolean.TRUE);
        item.addActionListener(ITEM_LOGGER);
    }

    /**
     * Root-pane shortcuts that are not menu accelerators (for example help zoom).
     * Menu accelerators are logged from {@link #ITEM_LOGGER} instead.
     */
    private static boolean postProcessKey(KeyEvent e) {
        if (!enabled || e.getID() != KeyEvent.KEY_PRESSED) {
            return false;
        }
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
                || code == KeyEvent.VK_META || code == KeyEvent.VK_UNDEFINED) {
            return false;
        }
        KeyStroke ks = KeyStroke.getKeyStrokeForEvent(e);
        Window w = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        if (w instanceof RootPaneContainer) {
            JMenuBar bar = menuBarOf(w);
            if (bar != null && findMenuItem(bar, ks) != null) {
                return false;
            }
            JRootPane root = ((RootPaneContainer) w).getRootPane();
            if (root != null) {
                InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
                Object binding = im == null ? null : im.get(ks);
                if (binding != null) {
                    ActionMap am = root.getActionMap();
                    Action action = am == null ? null : am.get(binding);
                    String name = binding.toString();
                    String purpose = "";
                    if (action != null) {
                        Object n = action.getValue(Action.NAME);
                        if (n != null && !n.toString().isEmpty()) {
                            name = n.toString();
                        }
                        Object d = action.getValue(Action.SHORT_DESCRIPTION);
                        if (d != null) {
                            purpose = d.toString();
                        }
                    }
                    appendCommand(name, purpose, formatAccel(ks), "", "shortcut");
                }
            }
        }
        return false;
    }

    private static JMenuBar menuBarOf(Window w) {
        if (w instanceof JFrame) {
            return ((JFrame) w).getJMenuBar();
        }
        if (w instanceof JDialog) {
            return ((JDialog) w).getJMenuBar();
        }
        return null;
    }

    private static JMenuItem findMenuItem(JMenuBar bar, KeyStroke ks) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenuItem found = findMenuItem(bar.getMenu(i), ks);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static JMenuItem findMenuItem(JMenu menu, KeyStroke ks) {
        if (menu == null) {
            return null;
        }
        for (int i = 0; i < menu.getMenuComponentCount(); i++) {
            Component c = menu.getMenuComponent(i);
            if (c instanceof JMenu) {
                JMenuItem found = findMenuItem((JMenu) c, ks);
                if (found != null) {
                    return found;
                }
            } else if (c instanceof JMenuItem) {
                if (acceleratorMatches((JMenuItem) c, ks)) {
                    return (JMenuItem) c;
                }
            }
        }
        return null;
    }

    private static boolean acceleratorMatches(JMenuItem item, KeyStroke ks) {
        KeyStroke a = item.getAccelerator();
        if (a != null && a.equals(ks)) {
            return true;
        }
        Action action = item.getAction();
        if (action != null) {
            Object v = action.getValue(Action.ACCELERATOR_KEY);
            return v instanceof KeyStroke && v.equals(ks);
        }
        return false;
    }

    private static void logMenuItem(JMenuItem item, String via) {
        String name = labelOf(item);
        String purpose = plain(item.getToolTipText());
        Action action = item.getAction();
        if (action != null) {
            if (name.isEmpty()) {
                Object n = action.getValue(Action.NAME);
                name = n == null ? action.getClass().getSimpleName() : n.toString();
            }
            if (purpose.isEmpty()) {
                Object d = action.getValue(Action.SHORT_DESCRIPTION);
                if (d != null) {
                    purpose = plain(d.toString());
                }
            }
        }
        if (name.isEmpty()) {
            name = item.getClass().getSimpleName();
        }
        appendCommand(name, purpose, formatAccel(item.getAccelerator()), menuPath(item), via);
    }

    private static String viaFromQueue() {
        AWTEvent cur = EventQueue.getCurrentEvent();
        if (cur instanceof KeyEvent) {
            return "accelerator";
        }
        if (cur instanceof InputEvent) {
            return "menu";
        }
        return "action";
    }

    private static String labelOf(JMenuItem item) {
        String t = item.getText();
        return t == null ? "" : t.trim();
    }

    private static String menuPath(JMenuItem item) {
        List<String> parts = new ArrayList<>();
        String leaf = labelOf(item);
        if (leaf.isEmpty() && item.getAction() != null) {
            Object n = item.getAction().getValue(Action.NAME);
            leaf = n == null ? "" : n.toString();
        }
        parts.add(leaf);
        Container p = item.getParent();
        while (p != null) {
            if (p instanceof JPopupMenu) {
                Component inv = ((JPopupMenu) p).getInvoker();
                if (inv instanceof JMenu) {
                    parts.add(((JMenu) inv).getText());
                    p = inv.getParent();
                    continue;
                }
                break;
            }
            if (p instanceof JMenuBar) {
                break;
            }
            p = p.getParent();
        }
        Collections.reverse(parts);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                b.append(" > ");
            }
            b.append(parts.get(i));
        }
        return b.toString();
    }

    private static String formatAccel(KeyStroke ks) {
        if (ks == null) {
            return "";
        }
        String mods = InputEvent.getModifiersExText(ks.getModifiers());
        String key = KeyEvent.getKeyText(ks.getKeyCode());
        if (ks.getKeyCode() == KeyEvent.VK_UNDEFINED && ks.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            key = String.valueOf(ks.getKeyChar());
        }
        if (mods == null || mods.isEmpty()) {
            return key;
        }
        return mods + "+" + key;
    }

    private static String plain(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String t = s.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (t.length() > 160) {
            return t.substring(0, 157) + "...";
        }
        return t;
    }

    private static void appendCommand(String name, String purpose, String accel, String path, String via) {
        append("command", "\"name\":\"" + jsonEscape(name)
                + "\",\"purpose\":\"" + jsonEscape(purpose)
                + "\",\"accel\":\"" + jsonEscape(accel)
                + "\",\"path\":\"" + jsonEscape(path)
                + "\",\"via\":\"" + jsonEscape(via) + "\"");
    }

    private static void append(String kind, String fields) {
        writeLine("{\"ts\":\"" + Instant.now() + "\",\"kind\":\"" + kind + "\"," + fields + "}");
    }

    private static void writeLine(String line) {
        synchronized (LOCK) {
            writeLineLocked(line);
        }
    }

    private static void writeLineLocked(String line) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not write UI interaction log", e);
            stopLocked();
        }
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    b.append("\\\\");
                    break;
                case '"':
                    b.append("\\\"");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }
}
