/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.assistant;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/**
 * Viewer menu bar as a path catalog. Not a global Swing ActionMap: filter
 * panels and the hardware configuration window are not included.
 * Call only on the EDT.
 */
public final class MenuActionCatalog {

    public final List<Entry> entries;

    private MenuActionCatalog(List<Entry> entries) {
        this.entries = entries;
    }

    public static MenuActionCatalog fromMenuBar(JMenuBar bar) {
        List<Entry> entries = new ArrayList<>();
        if (bar != null) {
            for (int i = 0; i < bar.getMenuCount(); i++) {
                JMenu menu = bar.getMenu(i);
                if (menu != null && menu.getText() != null) {
                    walk(menu, itemText(menu), entries);
                }
            }
        }
        return new MenuActionCatalog(entries);
    }

    private static void walk(JMenu menu, String path, List<Entry> entries) {
        for (Component c : menu.getMenuComponents()) {
            if (c instanceof JMenu sub) {
                String text = itemText(sub);
                if (!text.isEmpty()) {
                    walk(sub, path + " > " + text, entries);
                }
            } else if (c instanceof JMenuItem item) {
                String text = itemText(item);
                if (!text.isEmpty()) {
                    entries.add(new Entry(path + " > " + text, item));
                }
            }
        }
    }

    public String format(boolean enabledOnly, int maxLines) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        int shown = 0;
        for (Entry e : entries) {
            n++;
            if (enabledOnly && !e.item.isEnabled()) {
                continue;
            }
            if (shown >= maxLines) {
                continue;
            }
            sb.append(e.item.isEnabled() ? "" : "[off] ");
            sb.append(e.path);
            String tip = plain(e.item.getToolTipText());
            if (!tip.isEmpty()) {
                sb.append(" — ").append(tip);
            }
            sb.append('\n');
            shown++;
        }
        if (shown < n && !(enabledOnly && shown == 0)) {
            int hidden = 0;
            for (Entry e : entries) {
                if (!enabledOnly || e.item.isEnabled()) {
                    hidden++;
                }
            }
            if (hidden > shown) {
                sb.append("… ").append(hidden - shown).append(" more. Call list_menu_items.\n");
            }
        }
        return sb.toString();
    }

    /**
     * Menu items matching {@code query}, best first. An empty query returns
     * every item in menu order. Every word must appear in the item name, path,
     * tooltip, or accelerator.
     */
    public List<Entry> search(String query) {
        String q = norm(query);
        if (q.isEmpty()) {
            return new ArrayList<>(entries);
        }
        String[] tokens = q.split(" ");
        List<Scored> hits = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int score = score(e, tokens);
            if (score > 0) {
                hits.add(new Scored(e, score, i));
            }
        }
        hits.sort(Comparator.comparingInt((Scored s) -> -s.score).thenComparingInt(s -> s.index));
        List<Entry> out = new ArrayList<>(hits.size());
        for (Scored s : hits) {
            out.add(s.entry);
        }
        return out;
    }

    private static int score(Entry e, String[] tokens) {
        String leaf = norm(leaf(e.path));
        String path = norm(e.path);
        String tip = norm(plain(e.item.getToolTipText()));
        String accel = norm(acceleratorText(e.item)).replace("+", "");
        int score = 0;
        for (String raw : tokens) {
            if (raw.isEmpty()) {
                continue;
            }
            String token = raw.replace("+", "");
            int part = 0;
            if (leaf.equals(token)) {
                part = 100;
            } else if (leaf.startsWith(token)) {
                part = 80;
            } else if (leaf.contains(token)) {
                part = 50;
            } else if (path.contains(token)) {
                part = 30;
            } else if (!tip.isEmpty() && tip.contains(token)) {
                part = 15;
            } else if (!accel.isEmpty() && accel.contains(token)) {
                part = 15;
            }
            if (part == 0) {
                return 0;
            }
            score += part;
        }
        return score;
    }

    public Match find(String path) {
        if (path == null || path.isBlank()) {
            return Match.none("Menu path is empty.");
        }
        String want = norm(path);
        List<Entry> hits = new ArrayList<>();
        for (Entry e : entries) {
            if (norm(e.path).equals(want)) {
                hits.add(e);
            }
        }
        if (hits.size() == 1) {
            return Match.one(hits.get(0));
        }
        if (hits.size() > 1) {
            return Match.none("Several menu items match \"" + path + "\": " + names(hits));
        }
        List<Entry> fuzzy = new ArrayList<>();
        for (Entry e : entries) {
            String got = norm(e.path);
            if (got.endsWith(want) || got.contains(want)) {
                fuzzy.add(e);
            }
        }
        if (fuzzy.size() == 1) {
            return Match.one(fuzzy.get(0));
        }
        if (fuzzy.isEmpty()) {
            return Match.none("No menu item matches \"" + path + "\". Call list_menu_items.");
        }
        return Match.none("Several menu items match \"" + path + "\": " + names(fuzzy));
    }

    private static String names(List<Entry> hits) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(8, hits.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(hits.get(i).path);
        }
        if (hits.size() > n) {
            sb.append(" …");
        }
        return sb.toString();
    }

    public static String leaf(String path) {
        if (path == null) {
            return "";
        }
        int sep = path.lastIndexOf('>');
        if (sep < 0) {
            return path.trim();
        }
        return path.substring(sep + 1).trim();
    }

    /** Accelerator label such as {@code Ctrl+K}, or empty when the item has none. */
    public static String acceleratorText(JMenuItem item) {
        if (item == null) {
            return "";
        }
        KeyStroke ks = item.getAccelerator();
        if (ks == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int mods = ks.getModifiers();
        if (mods != 0) {
            sb.append(KeyEvent.getModifiersExText(mods));
            sb.append('+');
        }
        int code = ks.getKeyCode();
        if (code != KeyEvent.VK_UNDEFINED && code != 0) {
            sb.append(KeyEvent.getKeyText(code));
        } else if (ks.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            sb.append(ks.getKeyChar());
        }
        return sb.toString();
    }

    static String norm(String path) {
        String s = plain(path).toLowerCase(Locale.ROOT);
        s = s.replace(" > ", ">");
        s = s.replace(">", " > ");
        return s.replaceAll("\\s+", " ").trim();
    }

    static boolean destructive(String path) {
        String leaf = path;
        int sep = path.lastIndexOf('>');
        if (sep >= 0) {
            leaf = path.substring(sep + 1);
        }
        leaf = leaf.toLowerCase(Locale.ROOT);
        String[] keys = {"exit", "quit", "close", "reset", "record", "logging", "firmware", "flash", "unbind", "delete", "erase"};
        for (String k : keys) {
            if (leaf.contains(k)) {
                return true;
            }
        }
        return false;
    }

    static String itemText(JMenuItem item) {
        return plain(item.getText());
    }

    public static String plain(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static final class Entry {
        public final String path;
        public final JMenuItem item;

        Entry(String path, JMenuItem item) {
            this.path = path;
            this.item = item;
        }
    }

    public static final class Match {
        public final Entry entry;
        public final String error;

        private Match(Entry entry, String error) {
            this.entry = entry;
            this.error = error;
        }

        static Match one(Entry entry) {
            return new Match(entry, null);
        }

        static Match none(String error) {
            return new Match(null, error);
        }
    }

    private static final class Scored {
        final Entry entry;
        final int score;
        final int index;

        Scored(Entry entry, int score, int index) {
            this.entry = entry;
            this.score = score;
            this.index = index;
        }
    }
}
