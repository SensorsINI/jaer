/*
 * AEViewerPreferencesDialog.java
 *
 * Tabbed preferences editor for AEViewer menu-backed settings.
 */
package net.sf.jaer.graphics;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.text.WordUtils;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.FilterFrame;
import net.sf.jaer.eventprocessing.filter.AreaEventCountExposer;
import net.sf.jaer.util.HtmlHelpStyle;
import net.sf.jaer.util.JaerPreferencesStore;
import net.sf.jaer.util.RecentFiles;
import net.sf.jaer.util.RecordingDiskSpace;
import net.sf.jaer.util.RemoteControl;
import net.sf.jaer.util.UiInteractionLog;
import net.sf.jaer.util.ViewerInterfaceBindingMap;
import net.sf.jaer.util.WindowSaver;

/**
 * Nonmodal preferences window for AEViewer. First tab groups preference-backed
 * AEViewer menu settings by menu section. Filters tab covers global FilterFrame /
 * FilterChain preferences (individual AEFilter property sheets come later).
 * Export/Reset tab exports, imports, or deletes the {@code /jaer} Preferences tree.
 * Implemented as a {@link JFrame} (not an owned {@code JDialog}) so it can go behind AEViewer.
 * {@link WindowSaver.DontResize} keeps {@link #pack()} size; last position may still restore.
 */
public class AEViewerPreferencesDialog extends JFrame implements WindowSaver.DontResize {

    /**
     * Map the recording-format combo index to the data-file version sentinel:
     * index 0 = AEDAT-4, index 2 = AEDZ, anything else = AEDAT-2. Package-private
     * static so the headless probe can verify the preference round-trip without
     * constructing a JDialog on a machine with no display.
     *
     * @param index the combo index
     * @return the {@link AEDataFile} data-file version
     */
    static String recordingFormatVersionForIndex(int index) {
        if (index == 0) {
            return AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4;
        }
        if (index == 2) {
            return AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ;
        }
        return AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2;
    }

    /**
     * Map a data-file version sentinel back to the recording-format combo index:
     * AEDAT-4 = 0, AEDZ = 2, anything else = 1 (AEDAT-2). Inverse of
     * {@link #recordingFormatVersionForIndex(int)}.
     *
     * @param version the {@link AEDataFile} data-file version
     * @return the combo index
     */
    static int recordingFormatIndexForVersion(String version) {
        if (AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(version)) {
            return 0;
        }
        if (AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals(version)) {
            return 2;
        }
        return 1;
    }

    private final AEViewer viewer;
    private boolean updatingUi;

    private JCheckBox recordingPlaybackImmediatelyCB;
    private JCheckBox recordFilteredEventsCB;
    private JCheckBox showRecordingOverlayCB;
    private JCheckBox showRosOutputOverlayCB;
    private JCheckBox showDnnSharedMemoryOverlayCB;
    private JCheckBox showOpenCvOutputOverlayCB;
    private JCheckBox checkNonMonotonicCB;
    private JCheckBox syncEnabledCB;
    private JTextField timestampResetBitmaskTF;
    private JComboBox<String> recordingFormatCB;
    private JComboBox<String> aedat4CompressionCB;
    private JLabel recordingFolderStatusLabel;

    private JCheckBox activeRenderingCB;
    private JCheckBox renderBlankFramesCB;
    private JSpinner desiredFpsSpinner;
    private JCheckBox adaptiveRenderSkippingCB;
    private JSpinner adaptiveRenderSkipMaxSpinner;
    private JSpinner borderSpaceSpinner;
    private JCheckBox enableFiltersOnStartupCB;
    private JCheckBox raiseAllWindowsOnFocusCB;

    private JCheckBox repeatPlaybackCB;
    private JSpinner jogPacketCountSpinner;
    private JSpinner numAreasSpinner;
    private JRadioButton sliderTimeRelativeRB;
    private JRadioButton sliderTimeAbsoluteRB;

    private JCheckBox exitCompletelyWithXCB;
    private JCheckBox rememberLastInterfaceCB;
    private JCheckBox collectUsageDataCB;
    private JCheckBox remoteControlEnabledCB;
    private JLabel remoteControlRestartHint;
    private JSpinner remoteControlViewerPortSpinner;
    private JSpinner remoteControlChipPortSpinner;
    private JLabel remoteControlSessionLabel;
    private JButton remoteControlHelpButton;
    private AEViewerQuickHelpFrame remoteControlHelpDialog;
    private boolean remoteControlSnapEnabled;
    private int remoteControlSnapViewerPort;
    private int remoteControlSnapChipPort;
    private JComboBox<HtmlHelpStyle.HelpFontFamily> helpFontFamilyCB;
    private JSpinner helpFontSizeSpinner;

    private JCheckBox restoreFilterEnabledStateCB;
    private JCheckBox simpleModeCB;
    private JCheckBox hideDisabledFiltersCB;
    private JRadioButton renderingModeRB;
    private JRadioButton acquisitionModeRB;
    private JSpinner updateIntervalSpinner;
    private JLabel filtersNoteLabel;
    private JSpinner maxRecentFilesSpinner;
    private JSpinner maxRecentFoldersSpinner;
    private JTabbedPane tabs;
    private JTextField searchField;
    private JLabel searchMatchLabel;

    public AEViewerPreferencesDialog(AEViewer viewer) {
        super("Preferences");
        this.viewer = viewer;
        setName("AEViewerPreferences");
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        if (viewer != null) {
            setIconImage(viewer.getIconImage());
        }
        buildUi();
        if (viewer != null) {
            viewer.getSupport().addPropertyChangeListener(AEViewer.EVENT_REMEMBER_LAST_INTERFACE,
                    new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (rememberLastInterfaceCB == null || updatingUi) {
                                return;
                            }
                            Object nv = evt.getNewValue();
                            if (nv instanceof Boolean) {
                                updatingUi = true;
                                try {
                                    rememberLastInterfaceCB.setSelected((Boolean) nv);
                                } finally {
                                    updatingUi = false;
                                }
                            }
                        }
                    });
            viewer.getSupport().addPropertyChangeListener(AEViewer.EVENT_SYNC_ENABLED,
                    new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (syncEnabledCB == null || updatingUi) {
                                return;
                            }
                            Object nv = evt.getNewValue();
                            if (nv instanceof Boolean) {
                                updatingUi = true;
                                try {
                                    syncEnabledCB.setSelected((Boolean) nv);
                                } finally {
                                    updatingUi = false;
                                }
                            }
                        }
                    });
            viewer.getSupport().addPropertyChangeListener(AEViewer.EVENT_RAISE_ALL_WINDOWS_ON_FOCUS,
                    new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (raiseAllWindowsOnFocusCB == null || updatingUi) {
                                return;
                            }
                            Object nv = evt.getNewValue();
                            if (nv instanceof Boolean) {
                                updatingUi = true;
                                try {
                                    raiseAllWindowsOnFocusCB.setSelected((Boolean) nv);
                                } finally {
                                    updatingUi = false;
                                }
                            }
                        }
                    });
        }
        pack();
        setLocationRelativeTo(viewer);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                refreshFromViewer();
                applyPreferenceSearch();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                maybeWarnRemoteControlRestart();
            }
        });
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            refreshFromViewer();
            applyPreferenceSearch();
        }
        super.setVisible(visible);
    }

    private void buildUi() {
        tabs = new JTabbedPane();
        tabs.addTab("AEViewer", buildAeViewerTab());
        tabs.addTab("Filters", buildFiltersTab());
        tabs.addTab("Export/Reset", buildStoreTab());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                maybeWarnRemoteControlRestart();
                setVisible(false);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(closeButton);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(buildSearchBar(), BorderLayout.NORTH);
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(closeButton);
    }

    private JPanel buildSearchBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.LINE_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        JButton clearButton = new JButton("x");
        clearButton.setToolTipText("Clear the search");
        clearButton.setMargin(new Insets(1, 4, 1, 4));
        clearButton.addActionListener(e -> {
            searchField.setText("");
            searchField.requestFocusInWindow();
        });

        searchField = new JTextField();
        searchField.setToolTipText("Show preference items whose labels contain this text");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyPreferenceSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyPreferenceSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyPreferenceSearch();
            }
        });

        searchMatchLabel = new JLabel(" ");
        searchMatchLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        bar.add(clearButton);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(new JLabel("Filter"));
        bar.add(Box.createHorizontalStrut(4));
        bar.add(searchField);
        bar.add(searchMatchLabel);
        return bar;
    }

    private JPanel buildAeViewerTab() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        content.add(buildFileSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildViewSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildHelpSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildPlaybackSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildInterfaceSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildRemoteControlSection());
        content.add(Box.createVerticalStrut(8));
        content.add(buildDiagnosticsSection());
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

    private JPanel buildStoreTab() {
        JPanel p = titledSection("jAER preference tree (" + JaerPreferencesStore.jaerTreePath() + ")");
        int y = 0;

        String noteText = "Java Preferences under " + JaerPreferencesStore.jaerTreePath()
                + " hold AEViewer layout, last files, USB tuning, chip/filter settings, and Hardware Configuration values"
                + " that were saved with File → Save. Export the tree to move it to another computer or keep a backup."
                + " Revert deletes stored values so code defaults apply after restart.";
        JLabel note = new JLabel("<html>" + WordUtils.wrap(noteText, 60).replace("\n", "<br>") + "</html>");
        p.add(note, gbc(y++));

        JButton exportBtn = new JButton("Export all jAER preferences...");
        exportBtn.setToolTipText("Writes " + JaerPreferencesStore.jaerTreePath()
                + " to a Java Preferences XML file for import here or on another computer");
        exportBtn.addActionListener(e -> JaerPreferencesStore.exportDialog(this));
        p.add(exportBtn, gbc(y++));

        JButton importBtn = new JButton("Import jAER preferences...");
        importBtn.setToolTipText("Loads a previously exported Java Preferences XML file (absolute node paths)");
        importBtn.addActionListener(e -> {
            if (JaerPreferencesStore.importDialog(this) != null) {
                offerQuitAndRestart("Imported preferences.");
            }
        });
        p.add(importBtn, gbc(y++));

        JButton revertBtn = new JButton("Revert all preferences to defaults...");
        revertBtn.setToolTipText("Deletes stored jAER Preferences (offers export first). Restart afterwards.");
        revertBtn.addActionListener(e -> revertAllPreferences());
        p.add(revertBtn, gbc(y++));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(p, BorderLayout.NORTH);
        JPanel outer = new JPanel(new BorderLayout());
        outer.add(wrap, BorderLayout.CENTER);
        return outer;
    }

    private void revertAllPreferences() {
        int keyCount = JaerPreferencesStore.countJaerPreferenceKeys();
        String countText = keyCount >= 0 ? (keyCount + " stored keys") : "stored keys";
        String msg = "<html>Delete all jAER Preference values (" + countText + " under "
                + JaerPreferencesStore.jaerTreePath() + ", plus leftover package nodes)?"
                + "<p>Window layout, last files, USB tuning, filter settings, and saved Hardware Configuration"
                + " values in Preferences will be gone. This cannot be undone unless you export first."
                + "<p>Quit and restart jAER afterwards so in-memory settings reload from code defaults.";
        String[] options = {"Export and revert...", "Revert without export", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, msg, "Revert all preferences to defaults?",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[2]);
        if (choice == 0) {
            if (JaerPreferencesStore.exportDialog(this) == null) {
                return;
            }
            int go = JOptionPane.showConfirmDialog(this,
                    "Export finished. Delete stored jAER Preferences now?",
                    "Revert after export?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (go != JOptionPane.OK_OPTION) {
                return;
            }
        } else if (choice == 1) {
            int go = JOptionPane.showConfirmDialog(this,
                    "<html>Really delete all stored jAER Preferences <b>without</b> a backup?",
                    "Revert without export?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (go != JOptionPane.OK_OPTION) {
                return;
            }
        } else {
            return;
        }
        try {
            int deleted = JaerPreferencesStore.deleteAllJaerPreferences();
            offerQuitAndRestart("Deleted " + deleted + " keys under " + JaerPreferencesStore.jaerTreePath() + ".");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.toString(), "Revert failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void offerQuitAndRestart(String whatHappened) {
        String msg = "<html>" + whatHappened
                + "<p>Quit and restart jAER so this session reloads from the Preferences store."
                + "<p>In-memory settings are not updated live.";
        int quit = JOptionPane.showConfirmDialog(this, msg, "Quit and restart jAER?",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (quit == JOptionPane.YES_OPTION) {
            quitJaer();
        }
    }

    private void quitJaer() {
        dispose();
        viewer.requestExit();
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
        recordingFormatCB = new JComboBox<>(new String[]{"AEDAT-4 (.aedat4)", "AEDAT-2 (.aedat2)", "AEDZ compressed AEDAT-2 (.aedz)"});
        recordingFormatCB.setToolTipText("<html>File format used when starting recording with the button or 'l' key.<br>"
                + "AEDZ stores polarity events only. Starting AEDZ on a camera with IMU or APS frames<br>"
                + "offers to switch to AEDAT-4 at the compression selected below.");
        recordingFormatCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                int index = recordingFormatCB.getSelectedIndex();
                String version = recordingFormatVersionForIndex(index);
                boolean aedat4 = AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(version);
                viewer.setRecordingDataFileVersion(version);
                aedat4CompressionCB.setEnabled(aedat4);
            }
        });
        p.add(recordingFormatCB, gbcField(y++));

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
                + "at high event rates. Takes effect on the next Start recording.");
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

        p.add(new JLabel("Recording folder:"), gbcLabel(y));
        recordingFolderStatusLabel = new JLabel();
        recordingFolderStatusLabel.setToolTipText("Current next-recording folder and free space on that volume");
        p.add(recordingFolderStatusLabel, gbcField(y++));

        JButton chooseRecordingFolderBtn = new JButton("Choose...");
        chooseRecordingFolderBtn.setToolTipText("Folder for the next Start recording (L). Temporary files are written here until you save.");
        chooseRecordingFolderBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                File folder = RecordingFolderChooser.chooseFolder(AEViewerPreferencesDialog.this,
                        viewer.getLastRecordingFolder(), viewer.getRecentFiles(),
                        "Use this folder",
                        "Choose next recording folder (need at least "
                        + RecordingDiskSpace.minFreeSpaceLabel() + " free)");
                if (folder != null) {
                    viewer.setLastRecordingFolder(folder);
                    refreshRecordingFolderStatus();
                }
            }
        });
        GridBagConstraints chooseGbc = gbcField(y++);
        chooseGbc.fill = GridBagConstraints.NONE;
        chooseGbc.weightx = 0;
        chooseGbc.anchor = GridBagConstraints.WEST;
        p.add(chooseRecordingFolderBtn, chooseGbc);

        recordingPlaybackImmediatelyCB = new JCheckBox("Playback recorded data immediately after recording");
        recordingPlaybackImmediatelyCB.setToolTipText("If enabled, recorded data plays back immediately");
        recordingPlaybackImmediatelyCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setRecordingPlaybackImmediatelyEnabled(recordingPlaybackImmediatelyCB.isSelected());
            }
        });
        p.add(recordingPlaybackImmediatelyCB, gbc(y++));

        recordFilteredEventsCB = new JCheckBox("Enable filtering of recorded or network output events");
        recordFilteredEventsCB.setToolTipText("Recording or network writes apply active filters first");
        recordFilteredEventsCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setRecordFilteredEventsEnabled(recordFilteredEventsCB.isSelected());
            }
        });
        p.add(recordFilteredEventsCB, gbc(y++));

        showRecordingOverlayCB = new JCheckBox("Show recording overlay");
        showRecordingOverlayCB.setToolTipText("Show a transparent red Recording overlay on the chip view while recording");
        showRecordingOverlayCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setShowRecordingOverlay(showRecordingOverlayCB.isSelected());
            }
        });
        p.add(showRecordingOverlayCB, gbc(y++));

        showRosOutputOverlayCB = new JCheckBox("Show ROS2 / Foxglove overlay");
        showRosOutputOverlayCB.setToolTipText("Show publishing status on the chip view while ROSOutput is enabled");
        showRosOutputOverlayCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setShowRosOutputOverlay(showRosOutputOverlayCB.isSelected());
            }
        });
        p.add(showRosOutputOverlayCB, gbc(y++));

        showDnnSharedMemoryOverlayCB = new JCheckBox("Show DNN shared memory overlay");
        showDnnSharedMemoryOverlayCB.setToolTipText("Show mmap publishing status on the chip view while DNNOutputViaSharedMemory is enabled");
        showDnnSharedMemoryOverlayCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setShowDnnSharedMemoryOverlay(showDnnSharedMemoryOverlayCB.isSelected());
            }
        });
        p.add(showDnnSharedMemoryOverlayCB, gbc(y++));

        showOpenCvOutputOverlayCB = new JCheckBox("Show OpenCV MJPEG overlay");
        showOpenCvOutputOverlayCB.setToolTipText("Show publishing status on the chip view while OpenCVOutput is enabled");
        showOpenCvOutputOverlayCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setShowOpenCvOutputOverlay(showOpenCvOutputOverlayCB.isSelected());
            }
        });
        p.add(showOpenCvOutputOverlayCB, gbc(y++));

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

        syncEnabledCB = new JCheckBox("Synchronized recording/playback enabled");
        syncEnabledCB.setToolTipText("All viewers start/stop recording in synchrony and playback times are synchronized");
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
        timestampResetBitmaskTF.setToolTipText("<html>Hex bitmask for zeroing timestamps in playback files (e.g. 8000).<br>"
                + "Whenever any of these bits are set in an address, time is zeroed at that point and later timestamps have that time subtracted.<br>"
                + "Re-open the file after changing.");
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

        p.add(new JLabel("Max recent files:"), gbcLabel(y));
        maxRecentFilesSpinner = new JSpinner(new SpinnerNumberModel(
                RecentFiles.DEFAULT_MAX_FILES, RecentFiles.MIN_LIMIT, RecentFiles.MAX_LIMIT, 1));
        maxRecentFilesSpinner.setToolTipText("How many recent files to keep in the File menu");
        maxRecentFilesSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                RecentFiles recent = viewer.getRecentFiles();
                if (recent != null) {
                    recent.setMaxFiles(((Number) maxRecentFilesSpinner.getValue()).intValue());
                }
            }
        });
        p.add(maxRecentFilesSpinner, gbcField(y++));

        p.add(new JLabel("Max recent folders:"), gbcLabel(y));
        maxRecentFoldersSpinner = new JSpinner(new SpinnerNumberModel(
                RecentFiles.DEFAULT_MAX_FOLDERS, RecentFiles.MIN_LIMIT, RecentFiles.MAX_LIMIT, 1));
        maxRecentFoldersSpinner.setToolTipText("How many recent folders to keep in the File menu");
        maxRecentFoldersSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                RecentFiles recent = viewer.getRecentFiles();
                if (recent != null) {
                    recent.setMaxFolders(((Number) maxRecentFoldersSpinner.getValue()).intValue());
                }
            }
        });
        p.add(maxRecentFoldersSpinner, gbcField(y++));

        exitCompletelyWithXCB = new JCheckBox("Exit completely with 'x'");
        exitCompletelyWithXCB.setToolTipText("<html>Applies only to the <b>x</b> key, not File → Exit.<br>"
                + "When several AEViewer windows are open, <b>x</b> exits jAER instead of closing only this window.<br>"
                + "Offered the first time you press <b>x</b> with multiple windows.<br>"
                + "Later <b>x</b> exits with several windows still ask you to confirm that all AEViewers will close.<br>"
                + "File → Exit always quits jAER immediately, with no confirmation.");
        exitCompletelyWithXCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setExitCompletelyWithX(exitCompletelyWithXCB.isSelected());
            }
        });
        p.add(exitCompletelyWithXCB, gbc(y++));

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
        activeRenderingCB.setToolTipText("<html>On: ViewLoop waits for each OpenGL present (display()). Off: async repaint(); the loop continues without waiting.<br>Recommend <b>on</b> for daily use. Use <b>off</b> with a high target FPS for latency-sensitive applications, e.g. robots.");
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
        adaptiveRenderSkippingCB.setToolTipText("Skip packets when rendering cannot keep up; raw recording is unaffected");
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

        raiseAllWindowsOnFocusCB = new JCheckBox("Raise all jAER windows when any window is focused");
        raiseAllWindowsOnFocusCB.setToolTipText("<html>When any jAER window is activated, bring the other visible windows of this process to the front<br>"
                + "(other AEViewers, Hardware Configuration / Biasgen, Filters, Preferences).<br>"
                + "Minimized windows stay minimized. Menus and tooltips are not raised.<br>"
                + "<b>Windows:</b> works for windows of this process once jAER is in the foreground.<br>"
                + "<b>macOS:</b> native apps already group windows; Swing often does not — this uses toFront().<br>"
                + "<b>Linux:</b> some window managers ignore toFront() (focus-stealing prevention); uncheck if it flickers or does nothing.");
        raiseAllWindowsOnFocusCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setRaiseAllWindowsOnFocus(raiseAllWindowsOnFocusCB.isSelected());
            }
        });
        p.add(raiseAllWindowsOnFocusCB, gbc(y++));

        return p;
    }

    private JPanel buildHelpSection() {
        JPanel p = titledSection("Help");
        int y = 0;

        p.add(new JLabel("Help font family"), gbcLabel(y));
        helpFontFamilyCB = new JComboBox<>(HtmlHelpStyle.HelpFontFamily.values());
        helpFontFamilyCB.setToolTipText("Serif or Sans Serif for HTML help (quick help and filter help)");
        helpFontFamilyCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                HtmlHelpStyle.HelpFontFamily family
                        = (HtmlHelpStyle.HelpFontFamily) helpFontFamilyCB.getSelectedItem();
                viewer.setHelpFontFamily(family);
            }
        });
        p.add(helpFontFamilyCB, gbcField(y++));

        p.add(new JLabel("Help font size"), gbcLabel(y));
        helpFontSizeSpinner = new JSpinner(new SpinnerNumberModel(
                HtmlHelpStyle.DEFAULT_SIZE_PT, HtmlHelpStyle.MIN_SIZE_PT, HtmlHelpStyle.MAX_SIZE_PT, 1));
        helpFontSizeSpinner.setToolTipText("Body text size in points for HTML help (8–20). Ctrl+wheel or Ctrl+= / Ctrl+- in a help window also changes this.");
        helpFontSizeSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setHelpFontSize(((Number) helpFontSizeSpinner.getValue()).intValue());
            }
        });
        p.add(helpFontSizeSpinner, gbcField(y++));

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

        p.add(new JLabel("# areas (AreaEventCount):"), gbcLabel(y));
        numAreasSpinner = new JSpinner(new SpinnerNumberModel(AreaEventCountExposer.NUM_AREAS_DEFAULT, 1, 1024, 1));
        numAreasSpinner.setToolTipText("Target number of spatial cells for AreaEventCount playback (T method). Changing this briefly shows the grid.");
        numAreasSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                AbstractAEPlayer player = viewer.getAePlayer();
                if (player != null) {
                    player.setNumAreas(((Number) numAreasSpinner.getValue()).intValue());
                }
            }
        });
        p.add(numAreasSpinner, gbcField(y++));

        p.add(new JLabel("Playback slider time overlay:"), gbc(y++));
        sliderTimeRelativeRB = new JRadioButton("Relative to start of recording");
        sliderTimeRelativeRB.setToolTipText(
                "While press-sliding the playback slider, show elapsed time from the start of the recording on the chip view");
        sliderTimeAbsoluteRB = new JRadioButton("Absolute date/time");
        sliderTimeAbsoluteRB.setToolTipText(
                "While press-sliding the playback slider, show wall-clock date/time from the recording start on the chip view");
        ButtonGroup sliderTimeGroup = new ButtonGroup();
        sliderTimeGroup.add(sliderTimeRelativeRB);
        sliderTimeGroup.add(sliderTimeAbsoluteRB);
        ActionListener sliderTimeListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                viewer.setSliderTimeOverlayAbsolute(sliderTimeAbsoluteRB.isSelected());
            }
        };
        sliderTimeRelativeRB.addActionListener(sliderTimeListener);
        sliderTimeAbsoluteRB.addActionListener(sliderTimeListener);
        p.add(sliderTimeRelativeRB, gbc(y++));
        p.add(sliderTimeAbsoluteRB, gbc(y++));

        return p;
    }

    private JPanel buildInterfaceSection() {
        JPanel p = titledSection("Interface");
        rememberLastInterfaceCB = new JCheckBox("Remember last interface selected");
        rememberLastInterfaceCB.setToolTipText("Reopen each window's last USB camera on restart (global; all AEViewers share this). Mapping is "
                + ViewerInterfaceBindingMap.file().getAbsolutePath());
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

    private JPanel buildRemoteControlSection() {
        JPanel p = titledSection("Remote control");
        int y = 0;

        JPanel enableRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        enableRow.setOpaque(false);
        remoteControlEnabledCB = new JCheckBox("Enable UDP remote control");
        remoteControlEnabledCB.setToolTipText("Opens UDP sockets for AEViewer and the current AEChip. Off by default.");
        remoteControlRestartHint = new JLabel("(restart required)");
        remoteControlEnabledCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                RemoteControl.setEnabledPref(remoteControlEnabledCB.isSelected());
                updateRemoteControlRestartHint();
                if (remoteControlHelpDialog != null && remoteControlHelpDialog.isVisible()) {
                    remoteControlHelpDialog.setHtml(viewer.getRemoteControlHelpHtml());
                }
            }
        });
        enableRow.add(remoteControlEnabledCB);
        enableRow.add(remoteControlRestartHint);
        p.add(enableRow, gbc(y++));

        p.add(new JLabel("AEViewer UDP port:"), gbcLabel(y));
        remoteControlViewerPortSpinner = new JSpinner(new SpinnerNumberModel(RemoteControl.PORT_DEFAULT_VIEWER, 1, 65535, 1));
        remoteControlViewerPortSpinner.setToolTipText("UDP port for this AEViewer. Change takes effect after restart.");
        remoteControlViewerPortSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                RemoteControl.setViewerPortPref(((Number) remoteControlViewerPortSpinner.getValue()).intValue());
            }
        });
        p.add(remoteControlViewerPortSpinner, gbcField(y++));

        p.add(new JLabel("AEChip UDP port:"), gbcLabel(y));
        remoteControlChipPortSpinner = new JSpinner(new SpinnerNumberModel(RemoteControl.PORT_DEFAULT, 1, 65535, 1));
        remoteControlChipPortSpinner.setToolTipText("UDP port for the current AEChip (biases and chip commands). Change takes effect after restart.");
        remoteControlChipPortSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (updatingUi) {
                    return;
                }
                RemoteControl.setChipPortPref(((Number) remoteControlChipPortSpinner.getValue()).intValue());
            }
        });
        p.add(remoteControlChipPortSpinner, gbcField(y++));

        remoteControlSessionLabel = new JLabel(" ");
        p.add(remoteControlSessionLabel, gbc(y++));

        remoteControlHelpButton = new JButton("Show remote control help");
        remoteControlHelpButton.addActionListener(e -> toggleRemoteControlHelp());
        p.add(remoteControlHelpButton, gbc(y++));

        return p;
    }

    private void updateRemoteControlRestartHint() {
        boolean sessionOn = viewer.getRemoteControl() != null;
        boolean prefOn = remoteControlEnabledCB.isSelected();
        remoteControlRestartHint.setVisible(!prefOn);
        String session;
        if (sessionOn) {
            RemoteControl viewerRc = viewer.getRemoteControl();
            RemoteControl chipRc = viewer.getChip() != null ? viewer.getChip().getRemoteControl() : null;
            session = "<html>This session:<br>"
                    + "AEViewer " + viewerRc + "<br>"
                    + (chipRc != null ? "chip " + chipRc : "chip not listening") + "</html>";
        } else {
            session = "<html>This session:<br>remote control is not running.</html>";
        }
        remoteControlSessionLabel.setText(session);
    }

    private void toggleRemoteControlHelp() {
        if (remoteControlHelpDialog != null && remoteControlHelpDialog.isDisplayable()
                && remoteControlHelpDialog.isVisible()) {
            remoteControlHelpDialog.setVisible(false);
            return;
        }
        if (remoteControlHelpDialog == null || !remoteControlHelpDialog.isDisplayable()) {
            remoteControlHelpDialog = new AEViewerQuickHelpFrame(viewer, "Help — Remote control");
            remoteControlHelpDialog.setToggleHandler(this::toggleRemoteControlHelp);
            remoteControlHelpDialog.setHiddenHandler(this::syncRemoteControlHelpButton);
        }
        remoteControlHelpDialog.setHtml(viewer.getRemoteControlHelpHtml());
        remoteControlHelpDialog.setVisible(true);
        syncRemoteControlHelpButton();
    }

    private void syncRemoteControlHelpButton() {
        boolean shown = remoteControlHelpDialog != null && remoteControlHelpDialog.isDisplayable()
                && remoteControlHelpDialog.isVisible();
        if (remoteControlHelpButton != null) {
            remoteControlHelpButton.setText(shown ? "Hide remote control help" : "Show remote control help");
        }
    }

    private void snapshotRemoteControlPrefs() {
        remoteControlSnapEnabled = RemoteControl.isEnabledPref();
        remoteControlSnapViewerPort = RemoteControl.getViewerPortPref();
        remoteControlSnapChipPort = RemoteControl.getChipPortPref();
    }

    private void maybeWarnRemoteControlRestart() {
        boolean enabled = RemoteControl.isEnabledPref();
        int viewerPort = RemoteControl.getViewerPortPref();
        int chipPort = RemoteControl.getChipPortPref();
        if (enabled == remoteControlSnapEnabled
                && viewerPort == remoteControlSnapViewerPort
                && chipPort == remoteControlSnapChipPort) {
            return;
        }
        remoteControlSnapEnabled = enabled;
        remoteControlSnapViewerPort = viewerPort;
        remoteControlSnapChipPort = chipPort;
        JOptionPane.showMessageDialog(this,
                "Remote control enable or port changes take effect after you restart jAER.",
                "Restart required",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel buildDiagnosticsSection() {
        JPanel p = titledSection("Diagnostics");
        File dir = UiInteractionLog.directory();
        collectUsageDataCB = new JCheckBox("Collect usage data");
        collectUsageDataCB.setToolTipText("<html>When enabled, menu items and keyboard shortcuts (name, purpose, accelerator) are appended as JSON lines under<br>"
                + dir.getAbsolutePath()
                + "<br>Mouse motion and wheel are not logged. Off by default.");
        collectUsageDataCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updatingUi) {
                    return;
                }
                UiInteractionLog.setEnabled(collectUsageDataCB.isSelected());
            }
        });
        p.add(collectUsageDataCB, gbc(0));
        JLabel path = new JLabel("<html>Log folder: " + dir.getAbsolutePath() + "</html>");
        p.add(path, gbc(1));
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

    private void refreshRecordingFolderStatus() {
        if (recordingFolderStatusLabel == null) {
            return;
        }
        File folder = viewer.getLastRecordingFolder();
        if (folder == null) {
            recordingFolderStatusLabel.setText("<html>No recording folder set</html>");
            recordingFolderStatusLabel.setForeground(Color.DARK_GRAY);
            return;
        }
        long free = RecordingDiskSpace.usableBytes(folder);
        boolean enough = free >= RecordingDiskSpace.MIN_FREE_BYTES;
        String path = folder.getAbsolutePath();
        recordingFolderStatusLabel.setText("<html>" + escapeHtml(path)
                + "<br><b>" + RecordingDiskSpace.formatBytes(free) + " free</b>"
                + " (need " + RecordingDiskSpace.minFreeSpaceLabel() + " to start recording)</html>");
        recordingFolderStatusLabel.setForeground(enough ? new Color(0x1B5E20) : new Color(0xB71C1C));
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void refreshFromViewer() {
        updatingUi = true;
        try {
            recordingPlaybackImmediatelyCB.setSelected(viewer.isRecordingPlaybackImmediatelyEnabled());
            recordFilteredEventsCB.setSelected(viewer.isRecordFilteredEventsEnabled());
            showRecordingOverlayCB.setSelected(viewer.isShowRecordingOverlay());
            showRosOutputOverlayCB.setSelected(viewer.isShowRosOutputOverlay());
            showDnnSharedMemoryOverlayCB.setSelected(viewer.isShowDnnSharedMemoryOverlay());
            showOpenCvOutputOverlayCB.setSelected(viewer.isShowOpenCvOutputOverlay());
            checkNonMonotonicCB.setSelected(viewer.isCheckNonMonotonicTimeExceptionsEnabled());
            JAERViewer jaerViewer = viewer.getJaerViewer();
            syncEnabledCB.setSelected(jaerViewer != null && jaerViewer.isSyncEnabled());
            timestampResetBitmaskTF.setText(Integer.toHexString(viewer.getAeFileInputStreamTimestampResetBitmask()));
            String version = viewer.getRecordingDataFileVersion();
            int versionIndex = recordingFormatIndexForVersion(version);
            boolean aedat4 = versionIndex == 0;
            recordingFormatCB.setSelectedIndex(versionIndex);
            aedat4CompressionCB.setSelectedIndex(viewer.getAedat4Compression());
            aedat4CompressionCB.setEnabled(aedat4);
            refreshRecordingFolderStatus();

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
            if (raiseAllWindowsOnFocusCB != null) {
                raiseAllWindowsOnFocusCB.setSelected(viewer.isRaiseAllWindowsOnFocus());
            }

            helpFontFamilyCB.setSelectedItem(viewer.getHelpFontFamily());
            helpFontSizeSpinner.setValue(viewer.getHelpFontSize());

            AbstractAEPlayer player = viewer.getAePlayer();
            if (player != null) {
                repeatPlaybackCB.setSelected(player.isRepeat());
                jogPacketCountSpinner.setValue(Math.max(1, player.getJogPacketCount()));
                numAreasSpinner.setValue(Math.max(1, player.getNumAreas()));
            }
            boolean absTime = viewer.isSliderTimeOverlayAbsolute();
            sliderTimeRelativeRB.setSelected(!absTime);
            sliderTimeAbsoluteRB.setSelected(absTime);

            rememberLastInterfaceCB.setSelected(viewer.isRememberLastInterface());
            if (exitCompletelyWithXCB != null) {
                exitCompletelyWithXCB.setSelected(viewer.isExitCompletelyWithX());
            }

            snapshotRemoteControlPrefs();
            remoteControlEnabledCB.setSelected(RemoteControl.isEnabledPref());
            remoteControlViewerPortSpinner.setValue(RemoteControl.getViewerPortPref());
            remoteControlChipPortSpinner.setValue(RemoteControl.getChipPortPref());
            updateRemoteControlRestartHint();
            if (remoteControlHelpDialog != null && remoteControlHelpDialog.isVisible()) {
                remoteControlHelpDialog.setHtml(viewer.getRemoteControlHelpHtml());
            }
            syncRemoteControlHelpButton();

            collectUsageDataCB.setSelected(UiInteractionLog.isEnabled());

            RecentFiles recent = viewer.getRecentFiles();
            if (recent != null) {
                maxRecentFilesSpinner.setValue(recent.getMaxFiles());
                maxRecentFoldersSpinner.setValue(recent.getMaxFolders());
                maxRecentFilesSpinner.setEnabled(true);
                maxRecentFoldersSpinner.setEnabled(true);
            } else {
                maxRecentFilesSpinner.setEnabled(false);
                maxRecentFoldersSpinner.setEnabled(false);
            }

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
            hideDisabled = chip.getPrefs().getBoolean("hideDisabled", true);
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

    private void applyPreferenceSearch() {
        String q = searchField == null ? "" : searchField.getText();
        if (q == null) {
            q = "";
        }
        q = q.trim().toLowerCase();
        boolean showAll = q.isEmpty();
        int matches = 0;
        int firstMatchTab = -1;
        int selectedTabMatches = 0;
        if (tabs != null) {
            int selected = tabs.getSelectedIndex();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                int tabMatches = applySearchToComponent(tabs.getComponentAt(i), q, showAll);
                matches += tabMatches;
                if (tabMatches > 0 && firstMatchTab < 0) {
                    firstMatchTab = i;
                }
                if (i == selected) {
                    selectedTabMatches = tabMatches;
                }
            }
            if (!showAll && selectedTabMatches == 0 && firstMatchTab >= 0) {
                tabs.setSelectedIndex(firstMatchTab);
            }
        }
        if (searchMatchLabel != null) {
            searchMatchLabel.setText(showAll ? " " : (matches + (matches == 1 ? " match" : " matches")));
        }
        if (tabs != null) {
            tabs.revalidate();
            tabs.repaint();
        }
    }

    /**
     * @return number of matching preference rows (or leaf items)
     */
    private int applySearchToComponent(Component c, String q, boolean showAll) {
        if (c == null) {
            return 0;
        }
        if (c instanceof JScrollPane) {
            return applySearchToComponent(((JScrollPane) c).getViewport().getView(), q, showAll);
        }
        if (c instanceof Box.Filler) {
            c.setVisible(showAll);
            return 0;
        }
        if (c instanceof JPanel) {
            JPanel p = (JPanel) c;
            if (p.getLayout() instanceof GridBagLayout && p.getBorder() instanceof TitledBorder) {
                return applySearchToSection(p, q, showAll);
            }
            int matches = 0;
            for (Component child : p.getComponents()) {
                matches += applySearchToComponent(child, q, showAll);
            }
            return matches;
        }
        if (c instanceof JLabel || c instanceof AbstractButton) {
            boolean match = showAll || searchable(componentSearchText(c)).contains(q);
            c.setVisible(showAll || match);
            return match && !showAll ? 1 : 0;
        }
        return 0;
    }

    private int applySearchToSection(JPanel p, String q, boolean showAll) {
        String title = "";
        if (p.getBorder() instanceof TitledBorder) {
            String t = ((TitledBorder) p.getBorder()).getTitle();
            if (t != null) {
                title = t;
            }
        }
        boolean titleMatch = showAll || searchable(title).contains(q);
        GridBagLayout layout = (GridBagLayout) p.getLayout();
        Map<Integer, List<Component>> rows = new TreeMap<>();
        for (Component child : p.getComponents()) {
            GridBagConstraints gbc = layout.getConstraints(child);
            rows.computeIfAbsent(gbc.gridy, k -> new ArrayList<>()).add(child);
        }
        int matches = 0;
        boolean anyRow = titleMatch;
        for (List<Component> row : rows.values()) {
            boolean match = titleMatch;
            if (!match) {
                StringBuilder sb = new StringBuilder();
                for (Component child : row) {
                    sb.append(componentSearchText(child)).append(' ');
                }
                match = searchable(sb.toString()).contains(q);
            }
            for (Component child : row) {
                child.setVisible(match);
            }
            if (match) {
                anyRow = true;
                if (!showAll) {
                    matches++;
                }
            }
        }
        p.setVisible(showAll || anyRow);
        return matches;
    }

    private static String componentSearchText(Component c) {
        if (c instanceof AbstractButton) {
            return nvl(((AbstractButton) c).getText()) + " " + nvl(((AbstractButton) c).getToolTipText());
        }
        if (c instanceof JLabel) {
            return nvl(((JLabel) c).getText()) + " " + nvl(((JLabel) c).getToolTipText());
        }
        if (c instanceof JComboBox) {
            JComboBox<?> cb = (JComboBox<?>) c;
            StringBuilder sb = new StringBuilder(nvl(cb.getToolTipText()));
            for (int i = 0; i < cb.getItemCount(); i++) {
                Object item = cb.getItemAt(i);
                if (item != null) {
                    sb.append(' ').append(item);
                }
            }
            return sb.toString();
        }
        if (c instanceof JSpinner) {
            return nvl(((JSpinner) c).getToolTipText());
        }
        if (c instanceof JTextField) {
            return nvl(((JTextField) c).getToolTipText()) + " " + nvl(((JTextField) c).getText());
        }
        return nvl(c instanceof javax.swing.JComponent ? ((javax.swing.JComponent) c).getToolTipText() : null);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String searchable(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").toLowerCase();
    }
}
