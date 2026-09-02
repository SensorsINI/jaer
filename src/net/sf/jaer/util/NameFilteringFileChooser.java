/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * {@link JFileChooser} with a ClassChooser-style name filter: lowercase query
 * is case-insensitive substring; any uppercase letter switches to
 * {@link CamelCaseSearch}. Directories stay visible so the user can still
 * navigate. The choosable {@link javax.swing.filechooser.FileFilter} (Files of
 * Type) is unchanged; the name query is ANDed in {@link #accept(File)}.
 */
public class NameFilteringFileChooser extends JFileChooser {

    private static final int DEBOUNCE_MS = 250;

    private final JTextField filterField = new JTextField();
    private final Timer debounce;
    private String nameQuery = "";
    private boolean filterBarInstalled;

    public NameFilteringFileChooser() {
        super();
        debounce = createDebounceTimer();
        installFilterBar();
    }

    public NameFilteringFileChooser(File currentDirectory) {
        super(currentDirectory);
        debounce = createDebounceTimer();
        installFilterBar();
    }

    public NameFilteringFileChooser(String currentDirectoryPath) {
        super(currentDirectoryPath);
        debounce = createDebounceTimer();
        installFilterBar();
    }

    @Override
    public boolean accept(File f) {
        if (!super.accept(f)) {
            return false;
        }
        if (f == null || f.isDirectory() || nameQuery.isEmpty()) {
            return true;
        }
        return CamelCaseSearch.matches(f.getName(), nameQuery);
    }

    private Timer createDebounceTimer() {
        Timer t = new Timer(DEBOUNCE_MS, e -> applyNameQuery(filterField.getText()));
        t.setRepeats(false);
        return t;
    }

    private void installFilterBar() {
        if (filterBarInstalled) {
            return;
        }
        JLabel label = new JLabel("Filter");
        filterField.setToolTipText("<html>Type any part of the file name to show only matching files."
                + "<br>If you type any uppercase character, search uses CamelCase,"
                + "<br>e.g. <i>D240</i> for <i>DAVIS240C-2016-...</i>."
                + "<br>Esc clears the filter; Esc again cancels.");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        filterField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear-name-filter");
        filterField.getActionMap().put("clear-name-filter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (filterField.getText().length() > 0) {
                    debounce.stop();
                    filterField.setText("");
                    applyNameQuery("");
                } else {
                    cancelSelection();
                }
            }
        });

        JButton clear = new JButton("Clear");
        clear.setToolTipText("Clear the file-name filter");
        clear.addActionListener(e -> {
            debounce.stop();
            filterField.setText("");
            applyNameQuery("");
            filterField.requestFocusInWindow();
        });

        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bar.add(label, BorderLayout.WEST);
        bar.add(filterField, BorderLayout.CENTER);
        bar.add(clear, BorderLayout.EAST);

        LayoutManager lm = getLayout();
        if (lm instanceof BorderLayout bl) {
            Component north = bl.getLayoutComponent(BorderLayout.NORTH);
            if (north != null) {
                remove(north);
                JPanel wrapped = new JPanel(new BorderLayout());
                wrapped.add(north, BorderLayout.NORTH);
                wrapped.add(bar, BorderLayout.SOUTH);
                add(wrapped, BorderLayout.NORTH);
            } else {
                add(bar, BorderLayout.NORTH);
            }
        } else {
            add(bar, BorderLayout.NORTH);
        }
        filterBarInstalled = true;

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(() -> filterField.requestFocusInWindow());
            }
        });
    }

    private void applyNameQuery(String q) {
        String next = q == null ? "" : q;
        if (next.equals(nameQuery)) {
            return;
        }
        nameQuery = next;
        File selected = getSelectedFile();
        rescanCurrentDirectory();
        if (selected != null && selected.isFile() && !accept(selected)) {
            setSelectedFile(null);
        }
    }
}
