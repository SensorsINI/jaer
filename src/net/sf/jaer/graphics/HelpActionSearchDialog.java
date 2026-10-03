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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.sf.jaer.assistant.MenuActionCatalog;
import net.sf.jaer.assistant.MenuActionCatalog.Entry;

/**
 * Help → Search actions. Filters the viewer menu bar, and the Filters and
 * Hardware menus when those windows already exist. Call only on the EDT.
 */
public final class HelpActionSearchDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final int MAX_SHOWN = 200;

    private final MenuActionCatalog catalog;
    private final PromptField queryField;
    private final JList<Entry> list;
    private final DefaultListModel<Entry> model;
    private final JLabel status;

    private HelpActionSearchDialog(AEViewer viewer) {
        super(viewer, "Search actions", true);
        this.catalog = MenuActionCatalog.forViewer(viewer);
        if (viewer.getIconImage() != null) {
            setIconImage(viewer.getIconImage());
        }
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        queryField = new PromptField("Type a menu name, or part of a path…");
        queryField.setFont(queryField.getFont().deriveFont(Font.PLAIN, queryField.getFont().getSize2D() + 2f));
        queryField.setMargin(new Insets(8, 8, 8, 8));
        queryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        queryField.addActionListener(e -> activate());
        installListKeys();

        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new EntryRenderer());
        list.setVisibleRowCount(12);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    activate();
                }
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateStatus();
            }
        });

        status = new JLabel(" ");
        status.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        Color hint = UIManager.getColor("Label.disabledForeground");
        if (hint != null) {
            status.setForeground(hint);
        }

        JPanel fieldWrap = new JPanel(new BorderLayout());
        fieldWrap.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        fieldWrap.add(queryField, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        getContentPane().add(fieldWrap, BorderLayout.NORTH);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(status, BorderLayout.SOUTH);

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-search");
        getRootPane().getActionMap().put("close-search", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                queryField.requestFocusInWindow();
            }
        });

        setPreferredSize(new Dimension(640, 440));
        pack();
        setLocationRelativeTo(viewer);
        refresh();
    }

    /** Opens a search dialog for {@code viewer}. Call on the EDT. */
    public static void show(AEViewer viewer) {
        if (viewer == null) {
            return;
        }
        new HelpActionSearchDialog(viewer).setVisible(true);
    }

    private void installListKeys() {
        queryField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "search-down");
        queryField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "search-up");
        queryField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "search-page-down");
        queryField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "search-page-up");
        queryField.getActionMap().put("search-down", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                move(1);
            }
        });
        queryField.getActionMap().put("search-up", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                move(-1);
            }
        });
        queryField.getActionMap().put("search-page-down", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                move(8);
            }
        });
        queryField.getActionMap().put("search-page-up", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                move(-8);
            }
        });
    }

    private void move(int delta) {
        int n = model.getSize();
        if (n == 0) {
            return;
        }
        int i = list.getSelectedIndex();
        if (i < 0) {
            i = 0;
        } else {
            i = Math.max(0, Math.min(n - 1, i + delta));
        }
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
    }

    private void refresh() {
        java.util.List<Entry> hits = catalog.search(queryField.getText());
        int total = hits.size();
        model.clear();
        int shown = Math.min(total, MAX_SHOWN);
        for (int i = 0; i < shown; i++) {
            model.addElement(hits.get(i));
        }
        if (shown > 0) {
            list.setSelectedIndex(0);
            list.ensureIndexIsVisible(0);
        }
        updateStatus();
    }

    private void updateStatus() {
        int shown = model.getSize();
        int total = catalog.search(queryField.getText()).size();
        if (shown == 0) {
            status.setText(queryField.getText().isBlank() ? "No menu items." : "No matching menu items.");
            return;
        }
        Entry selected = list.getSelectedValue();
        StringBuilder sb = new StringBuilder();
        if (total > shown) {
            sb.append(shown).append(" of ").append(total).append(" matches");
        } else if (queryField.getText().isBlank()) {
            sb.append(shown).append(shown == 1 ? " menu item" : " menu items");
        } else {
            sb.append(shown).append(shown == 1 ? " match" : " matches");
        }
        if (selected != null && !selected.item.isEnabled()) {
            sb.append("  ·  selected item is disabled");
        } else {
            sb.append("  ·  Enter runs it, Esc closes");
        }
        status.setText(sb.toString());
    }

    private void activate() {
        Entry entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        JMenuItem item = entry.item;
        if (!item.isEnabled()) {
            status.setText("Disabled: " + entry.path);
            return;
        }
        Runnable beforeClick = entry.beforeClick;
        dispose();
        SwingUtilities.invokeLater(() -> {
            if (beforeClick != null) {
                beforeClick.run();
            }
            item.doClick();
        });
    }

    private static final class PromptField extends JTextField {
        private static final long serialVersionUID = 1L;
        private final String prompt;

        PromptField(String prompt) {
            this.prompt = prompt;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!getText().isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            Color c = UIManager.getColor("TextField.inactiveForeground");
            if (c == null) {
                c = Color.GRAY;
            }
            g2.setColor(c);
            g2.setFont(getFont());
            Insets in = getInsets();
            int y = in.top + g2.getFontMetrics().getAscent();
            g2.drawString(prompt, in.left, y);
            g2.dispose();
        }
    }

    private static final class EntryRenderer implements ListCellRenderer<Entry> {
        private final JPanel panel = new JPanel(new BorderLayout(12, 0));
        private final JPanel text = new JPanel(new BorderLayout(0, 1));
        private final JLabel title = new JLabel();
        private final JLabel subtitle = new JLabel();
        private final JLabel accelerator = new JLabel();

        EntryRenderer() {
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            Font small = subtitle.getFont().deriveFont(subtitle.getFont().getSize2D() - 1f);
            subtitle.setFont(small);
            accelerator.setFont(small);
            text.setOpaque(false);
            text.add(title, BorderLayout.NORTH);
            text.add(subtitle, BorderLayout.SOUTH);
            panel.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            panel.add(text, BorderLayout.CENTER);
            panel.add(accelerator, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Entry> list, Entry value, int index,
                boolean isSelected, boolean cellHasFocus) {
            String leaf = MenuActionCatalog.leaf(value.path);
            title.setText(leaf);
            String tip = MenuActionCatalog.plain(value.item.getToolTipText());
            String sub = value.path;
            if (!tip.isEmpty() && !tip.equalsIgnoreCase(leaf)) {
                if (tip.length() > 90) {
                    tip = tip.substring(0, 90) + "…";
                }
                sub = value.path + "  —  " + tip;
            }
            subtitle.setText(sub);
            accelerator.setText(MenuActionCatalog.acceleratorText(value.item));

            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            panel.setBackground(bg);
            panel.setOpaque(true);
            title.setForeground(value.item.isEnabled() || isSelected ? fg : disabled(fg));
            Color subFg = isSelected ? fg : disabled(fg);
            subtitle.setForeground(subFg);
            accelerator.setForeground(subFg);
            return panel;
        }

        private static Color disabled(Color fg) {
            Color c = UIManager.getColor("Label.disabledForeground");
            return c != null ? c : fg;
        }
    }
}
