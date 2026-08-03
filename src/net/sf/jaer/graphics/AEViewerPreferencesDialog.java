/*
 * AEViewerPreferencesDialog.java
 *
 * Tabbed preferences editor for AEViewer menu-backed settings.
 */
package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.FilterFrame;

/**
 * Preferences dialog for AEViewer. First tab groups preference-backed AEViewer
 * menu settings by menu section. Filters tab covers global FilterFrame /
 * FilterChain preferences (individual AEFilter property sheets come later).
 */
public class AEViewerPreferencesDialog extends JDialog {

    private final AEViewer viewer;
    private boolean updatingUi;

    private JCheckBox loggingPlaybackImmediatelyCB;
    private JCheckBox logFilteredEventsCB;
    private JCheckBox checkNonMonotonicCB;
    private JCheckBox syncEnabledCB;
    private JTextField timestampResetBitmaskTF;
    private JComboBox<String> loggingFormatCB;
    private JComboBox<String> aedat4CompressionCB;

    private JCheckBox activeRenderingCB;
    private JCheckBox renderBlankFramesCB;
    private JSpinner desiredFpsSpinner;
    private JCheckBox adaptiveRenderSkippingCB;
    private JSpinner adaptiveRenderSkipMaxSpinner;
    private JSpinner borderSpaceSpinner;
    private JCheckBox enableFiltersOnStartupCB;

    private JCheckBox repeatPlaybackCB;
    private JSpinner jogPacketCountSpinner;

    private JCheckBox rememberLastInterfaceCB;

    private JCheckBox restoreFilterEnabledStateCB;
    private JCheckBox simpleModeCB;
    private JCheckBox hideDisabledFiltersCB;
    private JRadioButton renderingModeRB;
    private JRadioButton acquisitionModeRB;
    private JSpinner updateIntervalSpinner;
    private JLabel filtersNoteLabel;

    public AEViewerPreferencesDialog(AEViewer viewer) {
        super(viewer, "Preferences", true);
        this.viewer = viewer;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        pack();
        setLocationRelativeTo(viewer);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                refreshFromViewer();
            }
        });
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            refreshFromViewer();
        }
        super.setVisible(visible);
    }

    private void buildUi() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("AEViewer", buildAeViewerTab());
        tabs.addTab("Filters", buildFiltersTab());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(closeButton);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(closeButton);
    }

    private JPanel buildAeViewerTab() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        content.add(buildFileSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildViewSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildPlaybackSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildInterfaceSection());
        content.add(Box.createVerticalGlue());

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrap);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel outer = new JPanel(new BorderLayout());
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildFiltersTab() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        content.add(buildFiltersGlobalSection());
        content.add(Box.createVerticalStrut(8));
        filtersNoteLabel = new JLabel("<html>Per-filter property preferences remain in the Filters window for now.</html>");
        filtersNoteLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(filtersNoteLabel);
        content.add(Box.createVerticalGlue());

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrap);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel outer = new JPanel(new BorderLayout());
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildFiltersGlobalSection() {
        JPanel p = titledSection("FilterFrame / FilterChain");
        int y = 0;

        restoreFilterEnabledStateCB = new JCheckBox("Restore filter enabled state");
        restoreFilterEnabledStateCB.setToolTipText("If enabled, filter enabled state is restored on startup");
        restoreFilterEnabledStateCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                applyRestoreFilterEnabledState(restoreFilterEnabledStateCB.isSelected());
            }
        });
        p.add(restoreFilterEnabledStateCB, gbc(y++));

        simpleModeCB = new JCheckBox("Simple mode (Preferred properties only)");
        simpleModeCB.setToolTipText("Only show Preferred properties (commonly used) in filter panels");
        simpleModeCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                applySimpleMode(simpleModeCB.isSelected());
            }
        });
        p.add(simpleModeCB, gbc(y++));

        hideDisabledFiltersCB = new JCheckBox("Hide disabled filters");
        hideDisabledFiltersCB.setToolTipText("Hides filters that are not enabled (by checkbox)");
        hideDisabledFiltersCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                applyHideDisabled(hideDisabledFiltersCB.isSelected());
            }
        });
        p.add(hideDisabledFiltersCB, gbc(y++));

        renderingModeRB = new JRadioButton("Process on rendering cycle");
        renderingModeRB.setToolTipText("Process events on rendering cycle");
        acquisitionModeRB = new JRadioButton("Process on acquisition cycle");
        acquisitionModeRB.setToolTipText("Process events on hardware data acquisition cycle");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(renderingModeRB);
        modeGroup.add(acquisitionModeRB);
        ActionListener modeListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                applyProcessingMode(renderingModeRB.isSelected()
                        ? FilterChain.ProcessingMode.RENDERING
                        : FilterChain.ProcessingMode.ACQUISITION);
            }
        };
        renderingModeRB.addActionListener(modeListener);
        acquisitionModeRB.addActionListener(modeListener);
        p.add(renderingModeRB, gbc(y++));
        p.add(acquisitionModeRB, gbc(y++));

        p.add(new JLabel("Global update interval (ms):"), gbcLabel(y));
        updateIntervalSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.001, 10000.0, 1.0));
        updateIntervalSpinner.setToolTipText("Maximum update interval for filters that notify observers");
        updateIntervalSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                applyUpdateIntervalMs(((Number) updateIntervalSpinner.getValue()).floatValue());
            }
        });
        p.add(updateIntervalSpinner, gbcField(y++));

        return p;
    }

    private FilterFrame getFilterFrame() {
        return viewer.getFilterFrame();
    }

    private FilterChain getFilterChain() {
        AEChip chip = viewer.getChip();
        return chip == null ? null : chip.getFilterChain();
    }

    private void applyRestoreFilterEnabledState(boolean enabled) {
        FilterFrame frame = getFilterFrame();
        if (frame != null) {
            frame.setRestoreFilterEnabledStateEnabled(enabled);
            return;
        }
        AEChip chip = viewer.getChip();
        if (chip != null && chip.getPrefs() != null) {
            chip.getPrefs().putBoolean("FilterFrame.restoreFilterEnabledStateEnabled", enabled);
        }
    }

    private void applySimpleMode(boolean simple) {
        FilterFrame frame = getFilterFrame();
        if (frame != null) {
            frame.setSimpleMode(simple);
            return;
        }
        AEChip chip = viewer.getChip();
        if (chip != null && chip.getPrefs() != null) {
            chip.getPrefs().putBoolean("simpleMode", simple);
        }
    }

    private void applyHideDisabled(boolean hide) {
        FilterFrame frame = getFilterFrame();
        if (frame != null) {
            frame.setHideDisabled(hide);
            return;
        }
        AEChip chip = viewer.getChip();
        if (chip != null && chip.getPrefs() != null) {
            chip.getPrefs().putBoolean("hideDisabled", hide);
        }
    }

    private void applyProcessingMode(FilterChain.ProcessingMode mode) {
        FilterChain chain = getFilterChain();
        if (chain != null) {
            chain.setProcessingMode(mode);
        }
    }

    private void applyUpdateIntervalMs(float ms) {
        FilterFrame frame = getFilterFrame();
        if (frame != null) {
            frame.setUpdateIntervalMs(ms);
            return;
        }
        FilterChain chain = getFilterChain();
        if (chain != null) {
            chain.setUpdateIntervalMs(ms);
        }
    }

    private JPanel titledSection(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder(title));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private GridBagConstraints gbc(int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 4, 2, 4);
        c.weightx = 1;
        return c;
    }

    private GridBagConstraints gbcLabel(int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 4, 2, 4);
        return c;
    }

    private GridBagConstraints gbcField(int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(2, 4, 2, 4);
        return c;
    }

    private JPanel buildFileSection() {
        JPanel p = titledSection("File");
        int y = 0;

        p.add(new JLabel("Recording format:"), gbcLabel(y));
        loggingFormatCB = new JComboBox<>(new String[]{"AEDAT-4 (.aedat4)", "AEDAT-2 (.aedat2)"});
        loggingFormatCB.setToolTipText("File format used when starting logging with the button or 'l' key");
        loggingFormatCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                boolean aedat4 = loggingFormatCB.getSelectedIndex() == 0;
                viewer.setLoggingDataFileVersion(aedat4
                        ? AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4
                        : AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2);
                aedat4CompressionCB.setEnabled(aedat4);
            }
        });
        p.add(loggingFormatCB, gbcField(y++));

        p.add(new JLabel("AEDAT-4 compression:"), gbcLabel(y));
        aedat4CompressionCB = new JComboBox<>(new String[]{
            "None",
            "LZ4 (recommended)",
            "LZ4 high",
            "ZSTD",
            "ZSTD high"
        });
        aedat4CompressionCB.setToolTipText("<html>DV-compatible per-packet compression for AEDAT-4.<br>"
                + "LZ4 is best for real-time recording. HIGH modes shrink files more but may slow live display "
                + "at high event rates. Takes effect on the next Start logging.");
        aedat4CompressionCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setAedat4Compression(aedat4CompressionCB.getSelectedIndex());
            }
        });
        p.add(aedat4CompressionCB, gbcField(y++));

        loggingPlaybackImmediatelyCB = new JCheckBox("Playback logged data immediately after logging enabled");
        loggingPlaybackImmediatelyCB.setToolTipText("If enabled, logged data plays back immediately");
        loggingPlaybackImmediatelyCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setLoggingPlaybackImmediatelyEnabled(loggingPlaybackImmediatelyCB.isSelected());
            }
        });
        p.add(loggingPlaybackImmediatelyCB, gbc(y++));

        logFilteredEventsCB = new JCheckBox("Enable filtering of logged or network output events");
        logFilteredEventsCB.setToolTipText("Logging or network writes apply active filters first");
        logFilteredEventsCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setLogFilteredEventsEnabled(logFilteredEventsCB.isSelected());
            }
        });
        p.add(logFilteredEventsCB, gbc(y++));

        checkNonMonotonicCB = new JCheckBox("Check for non-monotonic time in input streams");
        checkNonMonotonicCB.setToolTipText("If enabled, nonmonotonic timestamps are checked for in input streams from file or network");
        checkNonMonotonicCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setCheckNonMonotonicTimeExceptionsEnabled(checkNonMonotonicCB.isSelected());
            }
        });
        p.add(checkNonMonotonicCB, gbc(y++));

        syncEnabledCB = new JCheckBox("Synchronized logging/playback enabled");
        syncEnabledCB.setToolTipText("All viewers start/stop logging in synchrony and playback times are synchronized");
        syncEnabledCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                JAERViewer jaerViewer = viewer.getJaerViewer();
                if (jaerViewer != null) {
                    jaerViewer.setSyncEnabled(syncEnabledCB.isSelected());
                }
            }
        });
        p.add(syncEnabledCB, gbc(y++));

        p.add(new JLabel("Timestamp reset bitmask (hex):"), gbcLabel(y));
        timestampResetBitmaskTF = new JTextField(8);
        timestampResetBitmaskTF.setToolTipText("Whenever any of these bits are set in an address, time is zeroed at that point. Re-open the file after changing.");
        timestampResetBitmaskTF.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyTimestampResetBitmaskFromField();
            }
        });
        timestampResetBitmaskTF.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyTimestampResetBitmaskFromField();
            }
        });
        p.add(timestampResetBitmaskTF, gbcField(y++));

        return p;
    }

    private void applyTimestampResetBitmaskFromField() {
        if (updatingUi) {
            return;
        }
        String text = timestampResetBitmaskTF.getText().trim();
        if (text.isEmpty()) {
            text = "0";
        }
        if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        try {
            int mask = Integer.parseInt(text, 16);
            viewer.setAeFileInputStreamTimestampResetBitmask(mask);
        } catch (NumberFormatException ex) {
            updatingUi = true;
            try {
                timestampResetBitmaskTF.setText(Integer.toHexString(viewer.getAeFileInputStreamTimestampResetBitmask()));
            } finally {
                updatingUi = false;
            }
        }
    }

    private JPanel buildViewSection() {
        JPanel p = titledSection("View");
        int y = 0;

        activeRenderingCB = new JCheckBox("Active rendering enabled");
        activeRenderingCB.setToolTipText("Enables active display of each rendered frame");
        activeRenderingCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setActiveRenderingEnabled(activeRenderingCB.isSelected());
            }
        });
        p.add(activeRenderingCB, gbc(y++));

        renderBlankFramesCB = new JCheckBox("Render blank frames");
        renderBlankFramesCB.setToolTipText("If enabled, frames without events are rendered");
        renderBlankFramesCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setRenderBlankFramesEnabled(renderBlankFramesCB.isSelected());
            }
        });
        p.add(renderBlankFramesCB, gbc(y++));

        p.add(new JLabel("Rendering rate (FPS):"), gbcLabel(y));
        desiredFpsSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 1000, 1));
        desiredFpsSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.getFrameRater().setDesiredFPS(((Number) desiredFpsSpinner.getValue()).intValue());
            }
        });
        p.add(desiredFpsSpinner, gbcField(y++));

        adaptiveRenderSkippingCB = new JCheckBox("Adaptive render skipping");
        adaptiveRenderSkippingCB.setToolTipText("Skip packets when rendering cannot keep up; raw logging is unaffected");
        adaptiveRenderSkippingCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                AEChipRenderer renderer = getRenderer();
                if (renderer != null) {
                    renderer.setAdaptiveRenderSkippingEnabled(adaptiveRenderSkippingCB.isSelected());
                    viewer.syncAdaptiveRenderSkipMenuFromRenderer();
                }
                adaptiveRenderSkipMaxSpinner.setEnabled(adaptiveRenderSkippingCB.isSelected());
            }
        });
        p.add(adaptiveRenderSkippingCB, gbc(y++));

        p.add(new JLabel("Adaptive skip max packets:"), gbcLabel(y));
        adaptiveRenderSkipMaxSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
        adaptiveRenderSkipMaxSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                AEChipRenderer renderer = getRenderer();
                if (renderer != null) {
                    renderer.setConfiguredSkipFrameRenderingNumberMax(((Number) adaptiveRenderSkipMaxSpinner.getValue()).intValue());
                    viewer.syncAdaptiveRenderSkipMenuFromRenderer();
                }
            }
        });
        p.add(adaptiveRenderSkipMaxSpinner, gbcField(y++));

        p.add(new JLabel("Border space (pixels):"), gbcLabel(y));
        borderSpaceSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 500, 1));
        borderSpaceSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                ChipCanvas canvas = getCanvas();
                if (canvas != null) {
                    int space = ((Number) borderSpaceSpinner.getValue()).intValue();
                    canvas.setBorderSpacePixels(space);
                    viewer.updateBorderSpaceMenuItemText(space);
                    viewer.repaint();
                }
            }
        });
        p.add(borderSpaceSpinner, gbcField(y++));

        enableFiltersOnStartupCB = new JCheckBox("Enable filters on startup");
        enableFiltersOnStartupCB.setToolTipText("Enables creation of event processing filters on startup");
        enableFiltersOnStartupCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setEnableFiltersOnStartup(enableFiltersOnStartupCB.isSelected());
            }
        });
        p.add(enableFiltersOnStartupCB, gbc(y++));

        return p;
    }

    private JPanel buildPlaybackSection() {
        JPanel p = titledSection("Playback");
        int y = 0;

        repeatPlaybackCB = new JCheckBox("Repeat playback");
        repeatPlaybackCB.setToolTipText("When enabled, playback restarts at the end of the file");
        repeatPlaybackCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                AbstractAEPlayer player = viewer.getAePlayer();
                if (player != null) {
                    player.setRepeat(repeatPlaybackCB.isSelected());
                }
            }
        });
        p.add(repeatPlaybackCB, gbc(y++));

        p.add(new JLabel("Jog packet count:"), gbcLabel(y));
        jogPacketCountSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000000, 1));
        jogPacketCountSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                AbstractAEPlayer player = viewer.getAePlayer();
                if (player != null) {
                    player.setJogPacketCount(((Number) jogPacketCountSpinner.getValue()).intValue());
                    viewer.updateJogPacketCountMenuItemText();
                }
            }
        });
        p.add(jogPacketCountSpinner, gbcField(y++));

        return p;
    }

    private JPanel buildInterfaceSection() {
        JPanel p = titledSection("Interface");
        rememberLastInterfaceCB = new JCheckBox("Remember last interface selected");
        rememberLastInterfaceCB.setToolTipText("Remember the last selected hardware interface and reopen it automatically if found");
        rememberLastInterfaceCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setRememberLastInterface(rememberLastInterfaceCB.isSelected());
            }
        });
        p.add(rememberLastInterfaceCB, gbc(0));
        return p;
    }

    private AEChipRenderer getRenderer() {
        AEChip chip = viewer.getChip();
        return chip == null ? null : chip.getRenderer();
    }

    private ChipCanvas getCanvas() {
        AEChip chip = viewer.getChip();
        return chip == null ? null : chip.getCanvas();
    }

    private void refreshFromViewer() {
        updatingUi = true;
        try {
            loggingPlaybackImmediatelyCB.setSelected(viewer.isLoggingPlaybackImmediatelyEnabled());
            logFilteredEventsCB.setSelected(viewer.isLogFilteredEventsEnabled());
            checkNonMonotonicCB.setSelected(viewer.isCheckNonMonotonicTimeExceptionsEnabled());
            JAERViewer jaerViewer = viewer.getJaerViewer();
            syncEnabledCB.setSelected(jaerViewer != null && jaerViewer.isSyncEnabled());
            timestampResetBitmaskTF.setText(Integer.toHexString(viewer.getAeFileInputStreamTimestampResetBitmask()));
            boolean aedat4 = AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(viewer.getLoggingDataFileVersion());
            loggingFormatCB.setSelectedIndex(aedat4 ? 0 : 1);
            aedat4CompressionCB.setSelectedIndex(viewer.getAedat4Compression());
            aedat4CompressionCB.setEnabled(aedat4);

            activeRenderingCB.setSelected(viewer.isActiveRenderingEnabled());
            renderBlankFramesCB.setSelected(viewer.isRenderBlankFramesEnabled());
            desiredFpsSpinner.setValue(Math.max(1, viewer.getFrameRater().getDesiredFPS()));

            AEChipRenderer renderer = getRenderer();
            if (renderer != null) {
                adaptiveRenderSkippingCB.setSelected(renderer.isAdaptiveRenderSkippingEnabled());
                adaptiveRenderSkipMaxSpinner.setValue(Math.max(1, renderer.getConfiguredSkipFrameRenderingNumberMax()));
                adaptiveRenderSkipMaxSpinner.setEnabled(renderer.isAdaptiveRenderSkippingEnabled());
            } else {
                adaptiveRenderSkippingCB.setSelected(false);
                adaptiveRenderSkippingCB.setEnabled(false);
                adaptiveRenderSkipMaxSpinner.setEnabled(false);
            }

            ChipCanvas canvas = getCanvas();
            if (canvas != null) {
                borderSpaceSpinner.setValue(Math.max(0, canvas.getBorderSpacePixels()));
                borderSpaceSpinner.setEnabled(true);
            } else {
                borderSpaceSpinner.setEnabled(false);
            }

            enableFiltersOnStartupCB.setSelected(viewer.isEnableFiltersOnStartup());

            AbstractAEPlayer player = viewer.getAePlayer();
            if (player != null) {
                repeatPlaybackCB.setSelected(player.isRepeat());
                jogPacketCountSpinner.setValue(Math.max(1, player.getJogPacketCount()));
            }

            rememberLastInterfaceCB.setSelected(viewer.isRememberLastInterface());

            refreshFiltersFromViewer();
        } finally {
            updatingUi = false;
        }
    }

    private void refreshFiltersFromViewer() {
        AEChip chip = viewer.getChip();
        FilterFrame frame = getFilterFrame();
        FilterChain chain = getFilterChain();

        boolean restore = true;
        boolean simple = false;
        boolean hideDisabled = false;
        if (frame != null) {
            restore = frame.isRestoreFilterEnabledStateEnabled();
            simple = frame.isSimpleMode();
            hideDisabled = frame.isHideDisabled();
        } else if (chip != null && chip.getPrefs() != null) {
            restore = chip.getPrefs().getBoolean("FilterFrame.restoreFilterEnabledStateEnabled", true);
            simple = chip.getPrefs().getBoolean("simpleMode", false);
            hideDisabled = chip.getPrefs().getBoolean("hideDisabled", false);
        }
        restoreFilterEnabledStateCB.setSelected(restore);
        simpleModeCB.setSelected(simple);
        hideDisabledFiltersCB.setSelected(hideDisabled);

        FilterChain.ProcessingMode mode = FilterChain.ProcessingMode.RENDERING;
        float updateInterval = 10f;
        if (chain != null) {
            mode = chain.getProcessingMode();
            updateInterval = chain.getUpdateIntervalMs();
        }
        renderingModeRB.setSelected(mode == FilterChain.ProcessingMode.RENDERING);
        acquisitionModeRB.setSelected(mode == FilterChain.ProcessingMode.ACQUISITION);
        updateIntervalSpinner.setValue((double) Math.max(0.001f, updateInterval));

        boolean hasChipPrefs = chip != null && chip.getPrefs() != null;
        boolean hasChain = chain != null;
        restoreFilterEnabledStateCB.setEnabled(hasChipPrefs || frame != null);
        simpleModeCB.setEnabled(hasChipPrefs || frame != null);
        hideDisabledFiltersCB.setEnabled(hasChipPrefs || frame != null);
        renderingModeRB.setEnabled(hasChain);
        acquisitionModeRB.setEnabled(hasChain);
        updateIntervalSpinner.setEnabled(hasChain || frame != null);
    }
}