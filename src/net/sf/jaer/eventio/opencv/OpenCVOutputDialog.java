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

        final String pre = "font-family:Monospaced,monospace;font-size:11pt;margin:4px 0;padding:6px;"
                + "background-color:#f0f0f0;white-space:pre";
        MessageWithLink intro = new MessageWithLink(
                "Publishes DVS or Davis frames as HTTP Motion JPEG so stock OpenCV "
                + "<code>VideoCapture</code> can open the sensor like a camera.");
        MessageWithLink howTo = new MessageWithLink(
                "<p style=\"margin:4px 0 0 0;\"><b>Python / OpenCV</b> (any OS) after Start above:</p>"
                + "<pre style=\"" + pre + "\">cap = cv2.VideoCapture(\""
                + filter.getOpenCvClientUrl() + "\", cv2.CAP_FFMPEG)</pre>"
                + "<p style=\"margin:6px 0 0 0;\">Or <a href=\"" + filter.getOpenCvPageUrl()
                + "\">open the HTML preview</a> at <code>" + filter.getOpenCvPageUrl()
                + "</code> (not the raw <code>/video.mjpg</code> URL).</p>"
                + "<p style=\"margin:8px 0 0 0;\"><b>Linux webcam</b> (Cheese, Zoom, Google Meet). "
                + "These apps need a kernel loopback device. Commands require <code>sudo</code>. "
                + "No output from <code>modprobe</code> means success.</p>"
                + "<p style=\"margin:4px 0 0 0;\">Install once:</p>"
                + "<pre style=\"" + pre + "\">sudo apt install v4l2loopback-dkms v4l-utils</pre>"
                + "<p style=\"margin:4px 0 0 0;\">Load the module (unload first if it is already loaded):</p>"
                + "<pre style=\"" + pre + "\">sudo modprobe -r v4l2loopback\n"
                + "sudo modprobe v4l2loopback devices=1 video_nr=10 card_label=jAER exclusive_caps=1\n"
                + "v4l2-ctl --list-devices</pre>"
                + "<p style=\"margin:4px 0 0 0;\">That list should include <b>jAER</b> on "
                + "<code>/dev/video10</code>. Cheese and Zoom still hide it until jAER writes frames: "
                + "in the <b>V4L2</b> controls below, enable the <b>publishV4l2</b> checkbox, then Start above. "
                + "On Ubuntu, rescan cameras:</p>"
                + "<pre style=\"" + pre + "\">systemctl --user restart pipewire-media-session</pre>"
                + "<p style=\"margin:4px 0 0 0;\">Quit and reopen Zoom (it caches the camera list). "
                + "Pick camera <b>jAER</b> in Cheese, Zoom Settings → Video, or Google Meet.</p>"
                + "<p style=\"margin:6px 0 0 0;\">Use the Start/Stop button above to start or stop. "
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

        filterPanel = new FilterPanel(filter);
        filterPanel.setEnabledCheckBoxVisible(false);
        expandFilterControls();
        JScrollPane scroll = new JScrollPane(filterPanel);
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

    public OpenCVOutput getFilter() {
        return filter;
    }

    /** Expand OpenCVOutput properties (including publishV4l2) when this window is shown. */
    public void expandFilterControls() {
        filter.setControlsVisible(true);
        filterPanel.setControlsVisible(true);
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
