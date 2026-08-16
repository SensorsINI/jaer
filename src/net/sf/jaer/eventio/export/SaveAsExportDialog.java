package net.sf.jaer.eventio.export;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AEViewer.PlayMode;
import net.sf.jaer.util.ShowFolderSaveConfirmation;
import net.sf.jaer.util.textio.DavisTextEventFormatter;

/**
 * File → Save As dialog: CSV/text or DSEC HDF5 offline export of the open
 * recording.
 */
public final class SaveAsExportDialog extends JDialog implements PropertyChangeListener {

    private static final Preferences prefs = Preferences.userNodeForPackage(SaveAsExportDialog.class);

    private final AEViewer viewer;
    private SaveAsExporter exporter;

    private final JTextField pathField = new JTextField(36);
    private final JComboBox<SaveAsOptions.Format> formatCombo = new JComboBox<>(SaveAsOptions.Format.values());
    private final JCheckBox useMarkersCb = new JCheckBox("Use IN and OUT markers", true);
    private final JCheckBox applyFiltersCb = new JCheckBox("Apply EventFilters", true);
    private final JCheckBox writeFramesCb = new JCheckBox("Write XXX-frames/ PNGs", true);
    private final JCheckBox writeImuCb = new JCheckBox("Write XXX-imu.csv", true);

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

    public SaveAsExportDialog(AEViewer viewer) {
        super(viewer, "Save As", false);
        this.viewer = viewer;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        buildUi();
        loadPrefs();
        updateFormatUi();
        updateHvsUi();
        updateRecordingUi(false);
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
        for (Window w : viewer.getOwnedWindows()) {
            if (w instanceof SaveAsExportDialog) {
                SaveAsExportDialog d = (SaveAsExportDialog) w;
                if (d.exporter == null || d.exporter.isDone()) {
                    d.syncToOpenRecording();
                }
                d.toFront();
                d.setVisible(true);
                return;
            }
        }
        SaveAsExportDialog d = new SaveAsExportDialog(viewer);
        d.setVisible(true);
    }

    public static boolean isExportActive(AEViewer viewer) {
        if (viewer == null) {
            return false;
        }
        for (Window w : viewer.getOwnedWindows()) {
            if (w instanceof SaveAsExportDialog) {
                SaveAsExportDialog d = (SaveAsExportDialog) w;
                return d.exporter != null && !d.exporter.isDone();
            }
        }
        return false;
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
                + "(same as filtered relogging).<br>"
                + "Unchecked: write extracted events with no filtering.</html>");
        form.add(applyFiltersCb, c);

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
    }

    private void loadPrefs() {
        try {
            formatCombo.setSelectedItem(SaveAsOptions.Format.valueOf(prefs.get("format", "CSV")));
        } catch (Exception e) {
            formatCombo.setSelectedItem(SaveAsOptions.Format.CSV);
        }
        useMarkersCb.setSelected(prefs.getBoolean("useMarkers", true));
        applyFiltersCb.setSelected(prefs.getBoolean("applyFilters", true));
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
    }

    private String defaultOutputPath() {
        SaveAsOptions.Format f = (SaveAsOptions.Format) formatCombo.getSelectedItem();
        String ext = f != null ? f.extension : "csv";
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
        prefs.put("format", f != null ? f.name() : "CSV");
        prefs.putBoolean("useMarkers", useMarkersCb.isSelected());
        prefs.putBoolean("applyFilters", applyFiltersCb.isSelected());
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
        cl.show(optionCards, f != null ? f.name() : SaveAsOptions.Format.CSV.name());
        pack();
    }

    private void updateHvsUi() {
        boolean hvs = viewer.getChip() instanceof DavisChip;
        hvsPanel.setVisible(hvs);
        writeFramesCb.setEnabled(hvs);
        writeImuCb.setEnabled(hvs);
        if (!hvs) {
            writeFramesCb.setSelected(false);
            writeImuCb.setSelected(false);
        }
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
        if (f == SaveAsOptions.Format.DSEC_H5) {
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
        opt.csvFormatter = currentFormatter();
        boolean hvs = viewer.getChip() instanceof DavisChip;
        opt.writeFrames = hvs && writeFramesCb.isSelected();
        opt.writeImu = hvs && writeImuCb.isSelected();
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
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress".equals(evt.getPropertyName())) {
            progressBar.setValue((Integer) evt.getNewValue());
        } else if (SaveAsExporter.PROP_STATUS.equals(evt.getPropertyName())) {
            statusLabel.setText(String.valueOf(evt.getNewValue()));
        } else if ("state".equals(evt.getPropertyName()) && exporter != null && exporter.isDone()) {
            updateRecordingUi(false);
            try {
                SaveAsExporter.Result r = exporter.get();
                progressBar.setValue(100);
                String msg = String.format("<html>Wrote %,d events", r.events);
                if (r.frames > 0) {
                    msg += String.format(", %,d frames", r.frames);
                }
                if (r.imuSamples > 0) {
                    msg += String.format(", %,d IMU samples", r.imuSamples);
                }
                msg += " to " + r.outputFile.getName();
                statusLabel.setText("Done.");
                final File exported = r.outputFile;
                new ShowFolderSaveConfirmation(this, exported, msg, () -> {
                    try {
                        SaveAsExportDialog.this.dispose();
                        viewer.getAePlayer().startPlayback(exported);
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(viewer,
                                e.getMessage() != null ? e.getMessage() : e.toString(),
                                "Could not play exported file", JOptionPane.ERROR_MESSAGE);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }, "Play exported file", "Save As finished",
                        "Open this file in AEViewer (replaces the current recording)").setVisible(true);
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
