/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.opencv;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
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
import java.net.URI;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.plaf.basic.BasicToggleButtonUI;

import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.FilterPanel;
import net.sf.jaer.util.MessageWithLink;
import net.sf.jaer.util.WindowSaver;

/**
 * Modeless control window wrapping a {@link FilterPanel} for {@link OpenCVOutput}.
 * Closing hides the window and leaves publishing running.
 * Implemented as a {@link JFrame} (not an owned {@code JDialog}) so it can go behind AEViewer.
 * {@link WindowSaver.DontResize} keeps {@link #pack()} size; last position may still restore.
 */
public class OpenCVOutputDialog extends JFrame implements WindowSaver.DontResize {

    private final OpenCVOutput filter;
    private final FilterPanel filterPanel;
    private final JToggleButton enableButton;
    private final PropertyChangeListener enabledSync;
    private final PropertyChangeListener urlSync;

    public OpenCVOutputDialog(Frame parent, OpenCVOutput filter) {
        super("OpenCV camera output");
        this.filter = filter;
        setName("OpenCVOutput");
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        if (parent != null) {
            setIconImage(parent.getIconImage());
        }

        enableButton = new JToggleButton();
        enableButton.setFont(enableButton.getFont().deriveFont(Font.BOLD));
        enableButton.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        enableButton.setMnemonic(KeyEvent.VK_E);
        enableButton.setToolTipText(
                "Start or stop the MJPEG server. Enabled status is shown as an overlay on the chip view.");
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

        JTextField urlField = new JTextField(filter.getOpenCvClientUrl(), 28);
        urlField.setEditable(false);
        urlField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        urlField.setToolTipText("Paste into cv2.VideoCapture(..., cv2.CAP_FFMPEG)");
        urlField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                urlField.selectAll();
            }
        });
        JButton copy = new JButton("Copy");
        copy.setToolTipText("Copy MJPEG URL to the clipboard");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(urlField.getText()), null);
            copy.setText("Copied");
        });
        JButton open = new JButton("Open");
        open.setMnemonic(KeyEvent.VK_O);
        open.setToolTipText("Open HTML preview " + filter.getOpenCvPageUrl() + " in the browser");
        open.addActionListener(e -> openPreviewPage());
        urlSync = evt -> {
            String n = evt.getPropertyName();
            if ("httpPort".equals(n) || "bindAddress".equals(n)) {
                urlField.setText(filter.getOpenCvClientUrl());
                copy.setText("Copy");
                open.setToolTipText("Open HTML preview " + filter.getOpenCvPageUrl() + " in the browser");
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
        urlRow.add(new JLabel("OpenCV MJPEG URL:"));
        urlRow.add(urlField);
        urlRow.add(copy);
        urlRow.add(open);

        MessageWithLink intro = new MessageWithLink(
                "Publishes DVS or Davis frames as HTTP Motion JPEG<br>"
                + "so stock OpenCV <code>VideoCapture</code> can open the sensor like a camera.");
        JPanel helpRow = showHelpRow(filter,
                "Python/C++ VideoCapture, HTML preview, Linux v4l2loopback (publishV4l2; Cheese: scripts/cheese-jaer.sh)");

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        intro.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        urlRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        north.add(enableButton);
        north.add(Box.createVerticalStrut(8));
        north.add(intro);
        north.add(Box.createVerticalStrut(4));
        north.add(helpRow);
        north.add(Box.createVerticalStrut(6));
        north.add(urlRow);

        filterPanel = new FilterPanel(filter);
        filterPanel.setEnabledCheckBoxVisible(false);
        expandFilterControls();
        JScrollPane scroll = new JScrollPane(filterPanel);
        scroll.setPreferredSize(filterPanelScrollSize(filterPanel, 480));
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

    public OpenCVOutput getFilter() {
        return filter;
    }

    /** Expand OpenCVOutput properties (including publishV4l2) when this window is shown. */
    public void expandFilterControls() {
        filter.setControlsVisible(true);
        filterPanel.setControlsVisible(true);
    }

    private static JPanel showHelpRow(EventFilter filter, String tip) {
        JButton showHelp = new JButton("Show Help");
        showHelp.setMnemonic(KeyEvent.VK_H);
        showHelp.setToolTipText(tip);
        showHelp.setEnabled(filter.hasHelp());
        showHelp.addActionListener(e -> filter.showHelpDialog());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        row.add(showHelp);
        return row;
    }

    private static Dimension filterPanelScrollSize(FilterPanel panel, int height) {
        int w = Math.max(panel.getPreferredSize().width + 24, 560);
        return new Dimension(w, height);
    }

    private void openPreviewPage() {
        String url = filter.getOpenCvPageUrl();
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(this, "No Desktop support, can't open " + url,
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void syncEnableButton() {
        boolean on = filter.isFilterEnabled();
        if (enableButton.isSelected() != on) {
            enableButton.setSelected(on);
        }
        if (on) {
            enableButton.setText("Streaming OpenCV camera output. Click to stop");
            enableButton.setBackground(Color.GREEN);
        } else {
            enableButton.setText("Stopped. Click to start OpenCV camera output");
            enableButton.setBackground(Color.RED);
        }
        enableButton.setForeground(Color.BLACK);
        enableButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, enableButton.getPreferredSize().height + 8));
    }
}
