package net.sf.jaer.eventio.export;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.aedat4.Aedat4Compression;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AEViewer.PlayMode;
import net.sf.jaer.util.ShowFolderSaveConfirmation;
import net.sf.jaer.util.textio.DavisTextEventFormatter;

/**
 * File → Save As window: AEDAT-4, CSV/text, or DSEC HDF5 offline export of the
 * open recording (IN/OUT clip; preferred alternative to re-recording).
 * Implemented as a {@link JFrame} (not an owned {@code JDialog}) so it can go behind AEViewer.
 */
public final class SaveAsExportDialog extends JFrame implements PropertyChangeListener {

    private static final Preferences prefs = Preferences.userNodeForPackage(SaveAsExportDialog.class);

    private final AEViewer viewer;
    private SaveAsExporter exporter;

    private final JTextField pathField = new JTextField(36);
    private final JComboBox<SaveAsOptions.Format> formatCombo = new JComboBox<>(SaveAsOptions.Format.values());
    private final JCheckBox useMarkersCb = new JCheckBox("Use IN and OUT markers", true);
    private final JCheckBox applyFiltersCb = new JCheckBox("Apply EventFilters", true);
    private final JPanel filterSummaryPanel = new JPanel(new BorderLayout(6, 0));
    private final JLabel filterSummaryLabel = new JLabel();
    private final JButton openFiltersButton = new JButton("Open Filters…");
    private final JCheckBox writeFramesCb = new JCheckBox("Write XXX-frames/ PNGs", true);
    private final JCheckBox writeImuCb = new JCheckBox("Write XXX-imu.csv", true);
    private final JComboBox<String> aedat4CompressionCombo = new JComboBox<>(new String[]{
        "None",
        "LZ4 (recommended)",
        "LZ4 high",
        "ZSTD",
        "ZSTD high"
    });

    private final JCheckBox csvCommaCb = new JCheckBox("Comma separated (CSV)", true);
    private final JCheckBox csvUsCb = new JCheckBox("Timestamps in µs (else float seconds)", false);
    private final JCheckBox csvSignedCb = new JCheckBox("Signed polarity (−1/+1)", false);
    private final JCheckBox csvTsLastCb = new JCheckBox("Timestamp last (x,y,p,t)", false);
    private final JCheckBox csvSpecialCb = new JCheckBox("Special-event column", false);
    private final JCheckBox csvFlipCb = new JCheckBox("Flip polarity", false);
    private final JLabel csvFormatHint = new JLabel(" ");

    private final JPanel optionCards = new JPanel(new CardLayout());
    private final JPanel hvsPanel = new JPanel(new GridBagLayout());
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel(" ");

    private final JButton startButton = new JButton("Save");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton closeButton = new JButton("Close");
    /** Top-level chain filters we listen to for {@code filterEnabled}. */
    private final List<EventFilter2D> filterEnabledListenTargets = new ArrayList<>();

    public SaveAsExportDialog(AEViewer viewer) {
        super("Save As");
        this.viewer = viewer;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if (viewer != null) {
            setIconImage(viewer.getIconImage());
        }
        buildUi();
        loadPrefs();
        updateFormatUi();
        updateHvsUi();
        updateRecordingUi(false);
        bindFilterEnabledListeners();
        pack();
        setLocationRelativeTo(viewer);
    }

    public static void showDialog(AEViewer viewer) {
        if (viewer == null) {
            return;
        }
        if (viewer.getPlayMode() != PlayMode.PLAYBACK) {
            JOptionPane.showMessageDialog(viewer,
                    "Save As is only available while playing back a recording.",
                    "Save As", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SaveAsExportDialog existing = findForViewer(viewer);
        if (existing != null) {
            if (existing.exporter == null || existing.exporter.isDone()) {
                existing.syncToOpenRecording();
            }
            existing.updateFilterSummary();
            existing.toFront();
            existing.setVisible(true);
            return;
        }
        SaveAsExportDialog d = new SaveAsExportDialog(viewer);
        d.setVisible(true);
    }

    public static boolean isExportActive(AEViewer viewer) {
        if (viewer == null) {
            return false;
        }
        SaveAsExportDialog d = findForViewer(viewer);
        return d != null && d.exporter != null && !d.exporter.isDone();
    }

    /** Disposes the Save As window for this viewer, if any. */
    public static void disposeForViewer(AEViewer viewer) {
        SaveAsExportDialog d = findForViewer(viewer);
        if (d != null) {
            d.dispose();
        }
    }

    private static SaveAsExportDialog findForViewer(AEViewer viewer) {
        if (viewer == null) {
            return null;
        }
        for (Window w : Window.getWindows()) {
            if (w instanceof SaveAsExportDialog && w.isDisplayable()) {
                SaveAsExportDialog d = (SaveAsExportDialog) w;
                if (d.viewer == viewer) {
                    return d;
                }
            }
        }
        return null;
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel("Output file:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(pathField, c);
        c.gridx = 2;
        c.weightx = 0;
        JButton browse = new JButton("Browse…");
        browse.addActionListener(this::browse);
        form.add(browse, c);

        row++;
        c.gridx = 0;
        c.gridy = row;
        form.add(new JLabel("Format:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        formatCombo.addActionListener(e -> {
            updatePathExtension();
            updateFormatUi();
        });
        form.add(formatCombo, c);
        c.gridwidth = 1;

        row++;
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        useMarkersCb.setToolTipText("<html>Checked: export only the IN–OUT interval "
                + "(unset IN = file start, unset OUT = EOF).<br>"
                + "Unchecked: export the entire recording, ignoring markers.</html>");
        form.add(useMarkersCb, c);

        row++;
        c.gridy = row;
        applyFiltersCb.setToolTipText("<html>Checked: run the current EventFilter chain before writing "
                + "(same as filtered re-recording).<br>"
                + "Unchecked: write extracted events with no filtering.<br>"
                + "AEDAT-4 Save As is the preferred way to clip or filter a recording; "
                + "the recording button still re-records at playback pace.</html>");
        applyFiltersCb.addItemListener(e -> updateFilterSummary());
        form.add(applyFiltersCb, c);

        row++;
        c.gridy = row;
        filterSummaryLabel.setVerticalAlignment(JLabel.TOP);
        openFiltersButton.setToolTipText("Open the Filters window to enable, disable, or reorder EventFilters before saving");
        openFiltersButton.addActionListener(e -> {
            viewer.showFilters(true);
            updateFilterSummary();
        });
        filterSummaryPanel.add(filterSummaryLabel, BorderLayout.CENTER);
        filterSummaryPanel.add(openFiltersButton, BorderLayout.EAST);
        form.add(filterSummaryPanel, c);

        JPanel csvPanel = new JPanel(new GridBagLayout());
        csvPanel.setBorder(BorderFactory.createTitledBorder("CSV / text options"));
        GridBagConstraints cc = new GridBagConstraints();
        cc.anchor = GridBagConstraints.WEST;
        cc.insets = new Insets(2, 2, 2, 2);
        cc.gridx = 0;
        cc.gridy = 0;
        cc.gridwidth = 2;
        csvCommaCb.setToolTipText("<html>Checked: comma-separated CSV.<br>Unchecked: space-separated text.</html>");
        csvUsCb.setToolTipText("<html>Checked: integer timestamps in microseconds.<br>"
                + "Unchecked: float timestamps in seconds (t_us × 1e-6).</html>");
        csvSignedCb.setToolTipText("<html>Checked: polarity −1 (off) / +1 (on).<br>"
                + "Unchecked: polarity 0 (off) / 1 (on).</html>");
        csvTsLastCb.setToolTipText("<html>Checked: columns x, y, p, t.<br>Unchecked: columns t, x, y, p.</html>");
        csvSpecialCb.setToolTipText("<html>Checked: extra 0/1 column for special/external events.<br>"
                + "Unchecked: no special-event column.</html>");
        csvFlipCb.setToolTipText("<html>Checked: invert on/off polarity.<br>Unchecked: use recorded polarity.</html>");
        csvPanel.add(csvCommaCb, cc);
        cc.gridy++;
        csvPanel.add(csvUsCb, cc);
        cc.gridy++;
        csvPanel.add(csvSignedCb, cc);
        cc.gridy++;
        csvPanel.add(csvTsLastCb, cc);
        cc.gridy++;
        csvPanel.add(csvSpecialCb, cc);
        cc.gridy++;
        csvPanel.add(csvFlipCb, cc);
        cc.gridy++;
        JButton rpg = new JButton("Set to RPG format");
        rpg.setToolTipText("Space-separated t x y p with float seconds and 0/1 polarity");
        rpg.addActionListener(e -> applyRpg());
        csvPanel.add(rpg, cc);
        cc.gridy++;
        csvFormatHint.setToolTipText("Line format with current options");
        csvPanel.add(csvFormatHint, cc);
        java.awt.event.ItemListener hint = e -> updateCsvHint();
        csvCommaCb.addItemListener(hint);
        csvUsCb.addItemListener(hint);
        csvSignedCb.addItemListener(hint);
        csvTsLastCb.addItemListener(hint);
        csvSpecialCb.addItemListener(hint);
        csvFlipCb.addItemListener(hint);

        JPanel dsecPanel = new JPanel(new GridBagLayout());
        dsecPanel.setBorder(BorderFactory.createTitledBorder("DSEC HDF5"));
        GridBagConstraints dc = new GridBagConstraints();
        dc.anchor = GridBagConstraints.WEST;
        dc.insets = new Insets(2, 2, 2, 2);
        dc.gridx = 0;
        dc.gridy = 0;
        dsecPanel.add(new JLabel("<html>Cooked <code>/events/{p,t,x,y}</code>, <code>/ms_to_idx</code>, <code>/t_offset</code>.<br>"
                + "Uncompressed (jHDF 0.12 has no gzip write). Width/height attributes are stored."), dc);

        JPanel aedat4Panel = new JPanel(new GridBagLayout());
        aedat4Panel.setBorder(BorderFactory.createTitledBorder("AEDAT-4"));
        GridBagConstraints ac = new GridBagConstraints();
        ac.anchor = GridBagConstraints.WEST;
        ac.insets = new Insets(2, 2, 2, 2);
        ac.gridx = 0;
        ac.gridy = 0;
        ac.gridwidth = 2;
        aedat4Panel.add(new JLabel("<html>Native DV-compatible AEDAT-4 (events, frames, IMU in one file).<br>"
                + "Pauses playback and scans as fast as possible — preferred over re-recording "
                + "to clip with IN/OUT or apply EventFilters.</html>"), ac);
        ac.gridy++;
        ac.gridwidth = 1;
        aedat4Panel.add(new JLabel("Compression:"), ac);
        ac.gridx = 1;
        aedat4CompressionCombo.setToolTipText("<html>DV-compatible per-packet compression.<br>"
                + "LZ4 is best for large files. HIGH modes shrink more but take longer.</html>");
        aedat4Panel.add(aedat4CompressionCombo, ac);

        optionCards.add(aedat4Panel, SaveAsOptions.Format.AEDAT4.name());
        optionCards.add(csvPanel, SaveAsOptions.Format.CSV.name());
        optionCards.add(dsecPanel, SaveAsOptions.Format.DSEC_H5.name());

        row++;
        c.gridy = row;
        c.gridwidth = 3;
        form.add(optionCards, c);

        hvsPanel.setBorder(BorderFactory.createTitledBorder("HVS sidecars (DAVIS / CDAVIS)"));
        GridBagConstraints hc = new GridBagConstraints();
        hc.anchor = GridBagConstraints.WEST;
        hc.insets = new Insets(2, 2, 2, 2);
        hc.gridx = 0;
        hc.gridy = 0;
        writeFramesCb.setToolTipText("<html>Checked: write compressed PNG frames to &lt;basename&gt;-frames/ "
                + "plus timestamps.txt.<br>Unchecked: skip APS frame export.</html>");
        hvsPanel.add(writeFramesCb, hc);
        hc.gridy++;
        writeImuCb.setToolTipText("<html>Checked: write IMU samples to &lt;basename&gt;-imu.csv.<br>"
                + "Unchecked: skip IMU export.</html>");
        hvsPanel.add(writeImuCb, hc);

        row++;
        c.gridy = row;
        form.add(hvsPanel, c);

        row++;
        c.gridy = row;
        progressBar.setStringPainted(true);
        form.add(progressBar, c);

        row++;
        c.gridy = row;
        form.add(statusLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        startButton.addActionListener(this::startExport);
        cancelButton.addActionListener(this::cancelExport);
        closeButton.addActionListener(e -> {
            if (exporter != null && !exporter.isDone()) {
                cancelExport(null);
            }
            dispose();
        });
        buttons.add(startButton);
        buttons.add(cancelButton);
        buttons.add(closeButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        updateCsvHint();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                bindFilterEnabledListeners();
                updateFilterSummary();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                unbindFilterEnabledListeners();
            }
        });
    }

    private void loadPrefs() {
        try {
            formatCombo.setSelectedItem(SaveAsOptions.Format.valueOf(prefs.get("format", "AEDAT4")));
        } catch (Exception e) {
            formatCombo.setSelectedItem(SaveAsOptions.Format.AEDAT4);
        }
        useMarkersCb.setSelected(prefs.getBoolean("useMarkers", true));
        applyFiltersCb.setSelected(prefs.getBoolean("applyFilters", true));
        int compression = Aedat4Compression.clamp(prefs.getInt("aedat4Compression", viewer.getAedat4Compression()));
        aedat4CompressionCombo.setSelectedIndex(compression);
        csvCommaCb.setSelected(prefs.getBoolean("csvComma", true));
        csvUsCb.setSelected(prefs.getBoolean("csvUs", false));
        csvSignedCb.setSelected(prefs.getBoolean("csvSigned", false));
        csvTsLastCb.setSelected(prefs.getBoolean("csvTsLast", false));
        csvSpecialCb.setSelected(prefs.getBoolean("csvSpecial", false));
        csvFlipCb.setSelected(prefs.getBoolean("csvFlip", false));
        writeFramesCb.setSelected(prefs.getBoolean("writeFrames", true));
        writeImuCb.setSelected(prefs.getBoolean("writeImu", true));
        syncToOpenRecording();
    }

    /**
     * Default output path from the currently open recording (not the last export).
     */
    private void syncToOpenRecording() {
        pathField.setText(defaultOutputPath());
        updateHvsUi();
        bindFilterEnabledListeners();
        updateFilterSummary();
    }

    private String defaultOutputPath() {
        SaveAsOptions.Format f = (SaveAsOptions.Format) formatCombo.getSelectedItem();
        String ext = f != null ? f.extension : "aedat4";
        File src = viewer.getInputFile();
        if (src == null) {
            return "jAER-export." + ext;
        }
        String base = stripExt(src.getName());
        File parent = src.getParentFile();
        File out = parent != null ? new File(parent, base + "-export." + ext) : new File(base + "-export." + ext);
        return out.getAbsolutePath();
    }

    private void savePrefs() {
        SaveAsOptions.Format f = (SaveAsOptions.Format) formatCombo.getSelectedItem();
        prefs.put("format", f != null ? f.name() : "AEDAT4");
        prefs.putBoolean("useMarkers", useMarkersCb.isSelected());
        prefs.putBoolean("applyFilters", applyFiltersCb.isSelected());
        prefs.putInt("aedat4Compression", aedat4CompressionCombo.getSelectedIndex());
        prefs.putBoolean("csvComma", csvCommaCb.isSelected());
        prefs.putBoolean("csvUs", csvUsCb.isSelected());
        prefs.putBoolean("csvSigned", csvSignedCb.isSelected());
        prefs.putBoolean("csvTsLast", csvTsLastCb.isSelected());
        prefs.putBoolean("csvSpecial", csvSpecialCb.isSelected());
        prefs.putBoolean("csvFlip", csvFlipCb.isSelected());
        prefs.putBoolean("writeFrames", writeFramesCb.isSelected());
        prefs.putBoolean("writeImu", writeImuCb.isSelected());
    }

    private void applyRpg() {
        csvCommaCb.setSelected(false);
        csvUsCb.setSelected(false);
        csvSignedCb.setSelected(false);
        csvTsLastCb.setSelected(false);
        csvSpecialCb.setSelected(false);
        csvFlipCb.setSelected(false);
        updateCsvHint();
    }

    private void updateCsvHint() {
        csvFormatHint.setText("Line format: " + currentFormatter().shortFormatHint());
    }

    private DavisTextEventFormatter currentFormatter() {
        return new DavisTextEventFormatter(csvCommaCb.isSelected(), csvUsCb.isSelected(),
                csvSignedCb.isSelected(), csvTsLastCb.isSelected(),
                csvSpecialCb.isSelected(), csvFlipCb.isSelected());
    }

    private void updateFormatUi() {
        SaveAsOptions.Format f = (SaveAsOptions.Format) formatCombo.getSelectedItem();
        CardLayout cl = (CardLayout) optionCards.getLayout();
        cl.show(optionCards, f != null ? f.name() : SaveAsOptions.Format.AEDAT4.name());
        updateHvsUi();
        pack();
    }

    private void updateHvsUi() {
        boolean hvs = viewer.getChip() instanceof DavisChip;
        SaveAsOptions.Format f = (SaveAsOptions.Format) formatCombo.getSelectedItem();
        boolean sidecars = hvs && f != SaveAsOptions.Format.AEDAT4;
        hvsPanel.setVisible(sidecars);
        writeFramesCb.setEnabled(sidecars);
        writeImuCb.setEnabled(sidecars);
        if (!hvs) {
            writeFramesCb.setSelected(false);
            writeImuCb.setSelected(false);
        }
    }

    /**
     * Show which EventFilters would run if Apply EventFilters is checked.
     * Updates immediately when {@link EventFilter#setFilterEnabled} fires
     * {@code filterEnabled}, and when this dialog is activated.
     */
    private void updateFilterSummary() {
        boolean apply = applyFiltersCb.isSelected();
        boolean wasVisible = filterSummaryPanel.isVisible();
        filterSummaryPanel.setVisible(apply);
        openFiltersButton.setEnabled(apply && (exporter == null || exporter.isDone()));
        boolean textChanged = false;
        if (apply) {
            String html = buildEnabledFiltersHtml();
            textChanged = !html.equals(filterSummaryLabel.getText());
            if (textChanged) {
                filterSummaryLabel.setText(html);
            }
        }
        if ((wasVisible != apply || textChanged) && isDisplayable()) {
            pack();
        }
    }

    /**
     * Listen to each top-level filter's {@code filterEnabled} so the summary
     * updates while FilterFrame and this dialog are both open.
     */
    private void bindFilterEnabledListeners() {
        unbindFilterEnabledListeners();
        FilterChain chain = viewer.getChip() != null ? viewer.getChip().getFilterChain() : null;
        if (chain == null) {
            return;
        }
        for (EventFilter2D f : chain) {
            if (f == null) {
                continue;
            }
            f.getSupport().addPropertyChangeListener("filterEnabled", this);
            filterEnabledListenTargets.add(f);
        }
    }

    private void unbindFilterEnabledListeners() {
        for (EventFilter2D f : filterEnabledListenTargets) {
            if (f != null) {
                f.getSupport().removePropertyChangeListener("filterEnabled", this);
            }
        }
        filterEnabledListenTargets.clear();
    }

    private String buildEnabledFiltersHtml() {
        FilterChain chain = viewer.getChip() != null ? viewer.getChip().getFilterChain() : null;
        if (chain == null) {
            return "<html>No filter chain on this chip.";
        }
        if (!chain.isFilteringEnabled()) {
            return "<html><b>Filter processing is globally off.</b> Export will be unfiltered.<br>"
                    + "Turn it on in the Filters window, or uncheck Apply EventFilters.";
        }
        List<String> names = new ArrayList<>();
        for (EventFilter2D f : chain) {
            if (f != null && f.isFilterEnabled()) {
                names.add(f.getShortName());
            }
        }
        if (names.isEmpty()) {
            return "<html>No EventFilters are enabled — export will be <b>unfiltered</b>.<br>"
                    + "Enable a denoiser in Filters if you meant to clean the file.";
        }
        StringBuilder sb = new StringBuilder("<html>Will apply in chain order:");
        for (String name : names) {
            sb.append("<br>&nbsp;&nbsp;").append(ShowFolderSaveConfirmation.escapeHtml(name));
        }
        return sb.toString();
    }

    private void updatePathExtension() {
        SaveAsOptions.Format f = (SaveAsOptions.Format) formatCombo.getSelectedItem();
        if (f == null) {
            return;
        }
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            return;
        }
        File file = new File(path);
        String name = stripExt(file.getName());
        File parent = file.getParentFile();
        File next = parent != null ? new File(parent, name + "." + f.extension) : new File(name + "." + f.extension);
        pathField.setText(next.getPath());
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void browse(ActionEvent e) {
        SaveAsOptions.Format f = (SaveAsOptions.Format) formatCombo.getSelectedItem();
        JFileChooser chooser = new JFileChooser(pathField.getText());
        if (f == SaveAsOptions.Format.AEDAT4) {
            chooser.setFileFilter(new FileNameExtensionFilter("AEDAT-4 (*.aedat4)", "aedat4"));
        } else if (f == SaveAsOptions.Format.DSEC_H5) {
            chooser.setFileFilter(new FileNameExtensionFilter("DSEC HDF5 (*.h5, *.hdf5)", "h5", "hdf5"));
        } else {
            chooser.setFileFilter(new FileNameExtensionFilter("CSV / text (*.csv, *.txt)", "csv", "txt"));
        }
        chooser.setSelectedFile(new File(pathField.getText()));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            updatePathExtension();
        }
    }

    private void startExport(ActionEvent e) {
        if (viewer.getPlayMode() != PlayMode.PLAYBACK) {
            JOptionPane.showMessageDialog(this, "Save As is only available during playback.",
                    "Save As", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Choose an output file path.", "Save As", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File out = new File(path);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            JOptionPane.showMessageDialog(this, "Could not create folder " + parent, "Save As", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (out.exists()) {
            int r = JOptionPane.showConfirmDialog(this, "Overwrite " + out + "?", "Save As", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) {
                return;
            }
        }
        savePrefs();
        SaveAsOptions opt = new SaveAsOptions();
        opt.outputFile = out;
        opt.format = (SaveAsOptions.Format) formatCombo.getSelectedItem();
        opt.useInOutMarkers = useMarkersCb.isSelected();
        opt.applyEventFilters = applyFiltersCb.isSelected();
        opt.aedat4Compression = Aedat4Compression.clamp(aedat4CompressionCombo.getSelectedIndex());
        opt.csvFormatter = currentFormatter();
        boolean hvs = viewer.getChip() instanceof DavisChip;
        boolean sidecars = hvs && opt.format != SaveAsOptions.Format.AEDAT4;
        opt.writeFrames = sidecars && writeFramesCb.isSelected();
        opt.writeImu = sidecars && writeImuCb.isSelected();
        AEChip chip = viewer.getChip();
        opt.sensorWidth = chip != null ? chip.getSizeX() : 0;
        opt.sensorHeight = chip != null ? chip.getSizeY() : 0;
        exporter = new SaveAsExporter(viewer, opt);
        exporter.addPropertyChangeListener(this);
        updateRecordingUi(true);
        statusLabel.setText("Starting…");
        exporter.execute();
    }

    private void cancelExport(ActionEvent e) {
        if (exporter != null && !exporter.isDone()) {
            exporter.cancel(false); // do not interrupt: FileChannel would close
            statusLabel.setText("Cancelling…");
        }
    }

    private void updateRecordingUi(boolean running) {
        startButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        formatCombo.setEnabled(!running);
        pathField.setEnabled(!running);
        aedat4CompressionCombo.setEnabled(!running);
        applyFiltersCb.setEnabled(!running);
        useMarkersCb.setEnabled(!running);
        updateFilterSummary();
    }

    @Override
    public void dispose() {
        unbindFilterEnabledListeners();
        super.dispose();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("filterEnabled".equals(evt.getPropertyName())) {
            if (SwingUtilities.isEventDispatchThread()) {
                updateFilterSummary();
            } else {
                SwingUtilities.invokeLater(this::updateFilterSummary);
            }
            return;
        }
        if ("progress".equals(evt.getPropertyName())) {
            progressBar.setValue((Integer) evt.getNewValue());
        } else if (SaveAsExporter.PROP_STATUS.equals(evt.getPropertyName())) {
            statusLabel.setText(String.valueOf(evt.getNewValue()));
        } else if ("state".equals(evt.getPropertyName()) && exporter != null && exporter.isDone()) {
            updateRecordingUi(false);
            try {
                SaveAsExporter.Result r = exporter.get();
                progressBar.setValue(100);
                statusLabel.setText(r.badEvents > 0
                        ? String.format("Done (skipped %,d bad events).", r.badEvents)
                        : "Done.");
                final File exported = r.outputFile;
                String after = r.outputFileInfo;
                if (r.badEvents > 0) {
                    after = (after == null ? "" : after + "\n")
                            + String.format("Skipped %,d bad events", r.badEvents);
                }
                String msg = ShowFolderSaveConfirmation.htmlSaveAsMessage(exported, after, r.sourceFileInfo);
                dispose();
                viewer.showSavedFileConfirmation(exported, msg);
            } catch (CancellationException | InterruptedException cancel) {
                statusLabel.setText("Cancelled.");
                progressBar.setValue(0);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof CancellationException) {
                    statusLabel.setText("Cancelled.");
                    progressBar.setValue(0);
                    return;
                }
                statusLabel.setText("Failed: " + cause.getMessage());
                JOptionPane.showMessageDialog(this, cause.getMessage(), "Save As failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
