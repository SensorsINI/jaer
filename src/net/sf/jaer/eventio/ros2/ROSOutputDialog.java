/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.ros2;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.sf.jaer.eventprocessing.FilterPanel;
import net.sf.jaer.util.MessageWithLink;

/**
 * Modeless control window wrapping a {@link FilterPanel} for {@link ROSOutput}.
 * Parameter changes apply immediately; closing hides the window and leaves publishing running.
 * Enable/disable is the bold toggle at the top; File → Remote only opens this dialog.
 */
public class ROSOutputDialog extends JDialog {

    private final ROSOutput filter;
    private final JToggleButton enableButton;
    private final PropertyChangeListener enabledSync;
    private final PropertyChangeListener urlSync;

    public ROSOutputDialog(Frame parent, ROSOutput filter) {
        super(parent, "ROS2 / Foxglove frame output", false);
        this.filter = filter;
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        enableButton = new JToggleButton("Enable ROS2 / Foxglove output");
        enableButton.setFont(enableButton.getFont().deriveFont(Font.BOLD));
        enableButton.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        enableButton.setMnemonic(KeyEvent.VK_E);
        enableButton.setToolTipText(
                "Start or stop publishing. Enabled status is shown as an overlay on the chip view.");
        enableButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, enableButton.getPreferredSize().height + 8));
        enableButton.setSelected(filter.isFilterEnabled());
        enableButton.addActionListener(e -> filter.setFilterEnabled(enableButton.isSelected()));
        enabledSync = evt -> SwingUtilities.invokeLater(() -> {
            boolean on = filter.isFilterEnabled();
            if (enableButton.isSelected() != on) {
                enableButton.setSelected(on);
            }
        });
        filter.getSupport().addPropertyChangeListener("filterEnabled", enabledSync);

        JTextField urlField = new JTextField(filter.getFoxgloveClientUrl(), 22);
        urlField.setEditable(false);
        urlField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        urlField.setToolTipText("Paste this URL in Foxglove: Open connection → Foxglove WebSocket");
        urlField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                urlField.selectAll();
            }
        });
        JButton copy = new JButton("Copy");
        copy.setToolTipText("Copy WebSocket URL to the clipboard");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(urlField.getText()), null);
            copy.setText("Copied");
        });
        urlSync = evt -> {
            String n = evt.getPropertyName();
            if ("foxglovePort".equals(n) || "foxgloveBindAddress".equals(n)) {
                urlField.setText(filter.getFoxgloveClientUrl());
                copy.setText("Copy");
            }
        };
        filter.getSupport().addPropertyChangeListener(urlSync);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                filter.getSupport().removePropertyChangeListener(urlSync);
                filter.getSupport().removePropertyChangeListener("filterEnabled", enabledSync);
            }
        });

        JPanel urlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        urlRow.setOpaque(false);
        urlRow.add(new JLabel("Foxglove WebSocket URL:"));
        urlRow.add(urlField);
        urlRow.add(copy);

        MessageWithLink intro = new MessageWithLink(
                "Publishes assembled DVS frames (not the OpenGL pixmap) to ROS2 DDS "
                + "and/or <a href=\"https://foxglove.dev/download\">Foxglove</a> Studio.");
        MessageWithLink howTo = new MessageWithLink(
                "<ol style=\"margin:4px 0 0 16px;padding:0;\">"
                + "<li>Download <a href=\"https://foxglove.dev/download\">Foxglove</a>, then "
                + "<b>Open connection</b> → <b>Foxglove WebSocket</b> and paste the URL (Copy or Ctrl+C).</li>"
                + "<li><b>Layouts</b> → <b>Create new layout</b> → choose the <b>Image</b> template.</li>"
                + "<li>Pick topic <code>/jaer/event_count</code> (or time-surface / voxel).</li>"
                + "</ol>"
                + "<p style=\"margin:6px 0 0 0;\">Use <b>Enable ROS2 / Foxglove output</b> above to start or stop. "
                + "Closing this window does not stop publishing.</p>");

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        intro.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        urlRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        howTo.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        north.add(enableButton);
        north.add(Box.createVerticalStrut(8));
        north.add(intro);
        north.add(Box.createVerticalStrut(6));
        north.add(urlRow);
        north.add(howTo);

        FilterPanel panel = new FilterPanel(filter);
        panel.setEnabledCheckBoxVisible(false);
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(640, 480));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.setToolTipText("Hide this window; publishing stays in its current state");
        close.addActionListener(e -> setVisible(false));
        buttons.add(close);
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(close);
        Action escape = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        };
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
        getRootPane().getActionMap().put("escape", escape);
        pack();
        setLocationRelativeTo(parent);
    }

    public ROSOutput getFilter() {
        return filter;
    }
}
