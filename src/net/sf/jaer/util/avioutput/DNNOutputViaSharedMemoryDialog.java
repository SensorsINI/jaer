/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.util.avioutput;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.plaf.basic.BasicToggleButtonUI;

import net.sf.jaer.eventprocessing.FilterPanel;
import net.sf.jaer.util.MessageWithLink;
import net.sf.jaer.util.WindowSaver;

/**
 * Modeless control window wrapping a {@link FilterPanel} for
 * {@link DNNOutputViaSharedMemory}. Parameter changes apply immediately;
 * closing hides the window and leaves publishing running.
 * Enable/disable is the bold toggle at the top; File → Remote only opens this window.
 * Implemented as a {@link JFrame} (not an owned {@code JDialog}) so it can go behind AEViewer.
 * {@link WindowSaver.DontResize} keeps {@link #pack()} size; last position may still restore.
 */
public class DNNOutputViaSharedMemoryDialog extends JFrame implements WindowSaver.DontResize {

    private final DNNOutputViaSharedMemory filter;
    private final JToggleButton enableButton;
    private final PropertyChangeListener enabledSync;

    public DNNOutputViaSharedMemoryDialog(Frame parent, DNNOutputViaSharedMemory filter) {
        super("DNN shared memory output");
        this.filter = filter;
        setName("DNNSharedMemoryOutput");
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        if (parent != null) {
            setIconImage(parent.getIconImage());
        }

        enableButton = new JToggleButton();
        enableButton.setFont(enableButton.getFont().deriveFont(Font.BOLD));
        enableButton.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        enableButton.setMnemonic(KeyEvent.VK_E);
        enableButton.setToolTipText(
                "Start or stop publishing. Enabled status is shown as an overlay on the chip view.");
        enableButton.setUI(new BasicToggleButtonUI());
        enableButton.setOpaque(true);
        enableButton.setContentAreaFilled(true);
        enableButton.addActionListener(e -> {
            filter.setFilterEnabled(enableButton.isSelected());
            syncEnableButton();
        });
        enabledSync = evt -> SwingUtilities.invokeLater(this::syncEnableButton);
        syncEnableButton();
        filter.getSupport().addPropertyChangeListener("filterEnabled", enabledSync);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                filter.getSupport().removePropertyChangeListener("filterEnabled", enabledSync);
            }
        });

        MessageWithLink intro = new MessageWithLink(
                "Publishes DVS data over a memory-mapped file plus localhost TCP "
                + "for a Python DNN. Set <b>outputMode</b> to match the consumer.");
        MessageWithLink howTo = new MessageWithLink(
                "<ul style=\"margin:4px 0 0 16px;padding:0;\">"
                + "<li><b>EventCountFrames</b> — 64&times;64 uint8 histograms for "
                + "<a href=\"https://github.com/SensorsINI/dextra-roshambo-python\">dextra-roshambo-python</a> "
                + "<code>consumer.py --jaer-mmap</code> (TCP 14100).</li>"
                + "<li><b>EventWindows</b> — packed <code>(t,x,y,p)</code> windows for "
                + "<a href=\"https://github.com/SensorsINI/rpg_e2vid\">rpg_e2vid / FireNet</a> "
                + "<code>live_reconstruction.py</code> (TCP 14101).</li>"
                + "</ul>"
                + "<p style=\"margin:6px 0 0 0;\">Use the filter <b>?</b> Help for full setup. "
                + "Closing this window does not stop publishing; use the Start/Stop button above.</p>");

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        intro.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        howTo.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        north.add(enableButton);
        north.add(Box.createVerticalStrut(8));
        north.add(intro);
        north.add(Box.createVerticalStrut(6));
        north.add(howTo);

        FilterPanel panel = new FilterPanel(filter);
        panel.setEnabledCheckBoxVisible(false);
        panel.setControlsVisible(true);
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setPreferredSize(new Dimension(640, 520));
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

    public DNNOutputViaSharedMemory getFilter() {
        return filter;
    }

    private void syncEnableButton() {
        boolean on = filter.isFilterEnabled();
        if (enableButton.isSelected() != on) {
            enableButton.setSelected(on);
        }
        if (on) {
            enableButton.setText("Streaming DNN shared memory output. Click to stop");
            enableButton.setBackground(Color.GREEN);
        } else {
            enableButton.setText("Stopped. Click to start DNN shared memory output");
            enableButton.setBackground(Color.RED);
        }
        enableButton.setForeground(Color.BLACK);
        enableButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, enableButton.getPreferredSize().height + 8));
    }
}
