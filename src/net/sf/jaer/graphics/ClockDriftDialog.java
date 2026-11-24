package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.sf.jaer.eventio.aedat4.Aedat4ClockCorrection;
import net.sf.jaer.eventio.aedat4.MulticameraClockDrift;
import net.sf.jaer.util.ShowFolderSaveConfirmation;

/**
 * File → Measure clock drift. Runs {@link MulticameraClockDrift} off the EDT.
 */
public final class ClockDriftDialog extends JDialog {

    private final JLabel fileLabel = new JLabel(" ");
    private final JSpinner binMs = spinner(250, 10, 10_000, 10);
    private final JSpinner windowMin = spinner(10, 1, 180, 1);
    private final JSpinner stepMin = spinner(5, 1, 60, 1);
    private final JSpinner maxLagS = spinner(120, 1, 3600, 1);
    private final JSpinner minCorr = new JSpinner(new SpinnerNumberModel(0.20, 0.0, 0.99, 0.05));
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JTextArea report = new JTextArea(18, 72);
    private final JButton measureButton = new JButton("Measure");
    private final JButton saveButton = new JButton("Save corrected copy...");
    private final JButton showFolderButton = new JButton("Show folder");
    private final JButton copyButton = new JButton("Copy");
    private final AtomicBoolean cancel = new AtomicBoolean();
    private volatile Thread worker;
    private volatile MulticameraClockDrift.Result lastResult;
    private volatile File correctedFile;
    private File file;

    private static final String INTRO = """
            Packet activity from each camera is cross-correlated.
            The original file is not modified.
            After a fit, Save corrected copy writes a new file.
            """;

    public ClockDriftDialog(Frame owner) {
        super(owner, "Measure clock drift");
        setName("ClockDrift");
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        report.setEditable(false);
        report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        report.setText(INTRO);
        progress.setStringPainted(true);
        progress.setString(" ");

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.add(labeled("Bin (ms)", binMs));
        options.add(labeled("Window (min)", windowMin));
        options.add(labeled("Step (min)", stepMin));
        options.add(labeled("Max lag (s)", maxLagS));
        options.add(labeled("Min correlation", minCorr));

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        north.add(fileLabel, BorderLayout.NORTH);
        north.add(options, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.addActionListener(e -> setVisible(false));
        copyButton.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(report.getText()), null));
        measureButton.addActionListener(e -> start());
        saveButton.setEnabled(false);
        saveButton.setToolTipText("Write a new AEDAT-4 file. Fitted cameras are mapped onto the reference clock.");
        saveButton.addActionListener(e -> {
            if (correctedFile != null) {
                playCorrected();
            } else {
                saveCorrected();
            }
        });
        showFolderButton.setEnabled(false);
        showFolderButton.setToolTipText("Show the corrected file in the system file browser.");
        showFolderButton.addActionListener(e -> ShowFolderSaveConfirmation.showInFileManager(correctedFile));
        buttons.add(measureButton);
        buttons.add(saveButton);
        buttons.add(showFolderButton);
        buttons.add(copyButton);
        buttons.add(close);

        JPanel south = new JPanel(new BorderLayout());
        south.add(progress, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(report);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(measureButton);
        pack();
        setMinimumSize(getSize());
    }

    @Override
    public void dispose() {
        cancel.set(true);
        super.dispose();
    }

    /** Show this dialog for {@code recording}. A measurement already running is left alone. */
    public void showFor(File recording) {
        boolean same = recording != null && recording.equals(file);
        this.file = recording;
        fileLabel.setText(recording == null ? " " : recording.getName());
        if (!same && worker == null) {
            lastResult = null;
            correctedFile = null;
            report.setText(INTRO);
            progress.setValue(0);
            progress.setString(" ");
            setRunning(false);
        }
        if (!isVisible()) {
            setLocationRelativeTo(getOwner());
            setVisible(true);
        } else {
            toFront();
        }
    }

    private void start() {
        File target = file;
        if (target == null || worker != null) {
            return;
        }
        MulticameraClockDrift.Options options = new MulticameraClockDrift.Options();
        options.binUs = ((Number) binMs.getValue()).longValue() * 1_000L;
        options.windowUs = ((Number) windowMin.getValue()).longValue() * 60L * 1_000_000L;
        options.stepUs = ((Number) stepMin.getValue()).longValue() * 60L * 1_000_000L;
        options.maxLagUs = ((Number) maxLagS.getValue()).longValue() * 1_000_000L;
        options.minCorrelation = ((Number) minCorr.getValue()).doubleValue();
        cancel.set(false);
        lastResult = null;
        correctedFile = null;
        setRunning(true);
        progress.setValue(0);
        progress.setString("Starting");
        report.setText("");
        Thread thread = new Thread(() -> runMeasure(target, options), "clock-drift");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    private void runMeasure(File target, MulticameraClockDrift.Options options) {
        try {
            MulticameraClockDrift.Result result = MulticameraClockDrift.measure(target, options, (note, percent) -> {
                if (cancel.get()) {
                    return false;
                }
                SwingUtilities.invokeLater(() -> {
                    if (percent >= 0) {
                        progress.setValue(Math.max(0, Math.min(100, percent)));
                    }
                    progress.setString(note);
                });
                return !cancel.get();
            });
            SwingUtilities.invokeLater(() -> {
                lastResult = result;
                showText(result.report, "Done");
            });
        } catch (RuntimeException ex) {
            if (MulticameraClockDrift.isCancelled(ex) || cancel.get()) {
                SwingUtilities.invokeLater(() -> showText("Cancelled.\n", "Cancelled"));
            } else {
                SwingUtilities.invokeLater(() -> showText(ex.getMessage() + "\n", "Failed"));
            }
        } catch (IOException ex) {
            if (cancel.get()) {
                SwingUtilities.invokeLater(() -> showText("Cancelled.\n", "Cancelled"));
            } else {
                SwingUtilities.invokeLater(() -> showText(ex.getMessage() + "\n", "Failed"));
            }
        } finally {
            worker = null;
            SwingUtilities.invokeLater(() -> setRunning(false));
        }
    }

    private void showText(String text, String status) {
        if (!isDisplayable()) {
            return;
        }
        report.setText(text);
        report.setCaretPosition(0);
        progress.setString(status);
        if ("Done".equals(status) || "Wrote copy".equals(status)) {
            progress.setValue(100);
        }
    }

    private void setRunning(boolean running) {
        measureButton.setEnabled(!running);
        binMs.setEnabled(!running);
        windowMin.setEnabled(!running);
        stepMin.setEnabled(!running);
        maxLagS.setEnabled(!running);
        minCorr.setEnabled(!running);
        saveButton.setEnabled(false);
        showFolderButton.setEnabled(false);
        if (running) {
            measureButton.setText("Cancel");
            for (var listener : measureButton.getActionListeners()) {
                measureButton.removeActionListener(listener);
            }
            measureButton.addActionListener(e -> cancel.set(true));
            measureButton.setEnabled(true);
        } else {
            measureButton.setText("Measure");
            for (var listener : measureButton.getActionListeners()) {
                measureButton.removeActionListener(listener);
            }
            measureButton.addActionListener(e -> start());
            if (correctedFile != null) {
                saveButton.setText("Play corrected recording");
                saveButton.setToolTipText("Open the corrected AEDAT-4 file.");
                saveButton.setEnabled(true);
                showFolderButton.setEnabled(true);
            } else {
                saveButton.setText("Save corrected copy...");
                saveButton.setToolTipText("Write a new AEDAT-4 file. Fitted cameras are mapped onto the reference clock.");
                saveButton.setEnabled(lastResult != null && lastResult.crossCorrelationOk());
            }
        }
    }

    private void saveCorrected() {
        File source = file;
        MulticameraClockDrift.Result result = lastResult;
        if (source == null || result == null || !result.crossCorrelationOk() || worker != null) {
            return;
        }
        JFileChooser chooser = new JFileChooser(source.getParentFile());
        chooser.setDialogTitle("Save clock-corrected copy");
        chooser.setSelectedFile(Aedat4ClockCorrection.sibling(source));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dest = chooser.getSelectedFile();
        if (dest == null) {
            return;
        }
        if (!dest.getName().toLowerCase(Locale.ROOT).endsWith(".aedat4")) {
            dest = new File(dest.getParentFile(), dest.getName() + ".aedat4");
        }
        File target = dest;
        cancel.set(false);
        setRunning(true);
        progress.setValue(0);
        progress.setString("Starting");
        Thread thread = new Thread(() -> runSave(source, target, result), "clock-drift-copy");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    private void runSave(File source, File dest, MulticameraClockDrift.Result result) {
        try {
            File out = Aedat4ClockCorrection.writeCorrectedCopy(source, dest, result, (note, percent) -> {
                if (cancel.get()) {
                    return false;
                }
                SwingUtilities.invokeLater(() -> {
                    if (percent >= 0) {
                        progress.setValue(Math.max(0, Math.min(100, percent)));
                    }
                    progress.setString(note);
                });
                return !cancel.get();
            });
            correctedFile = out;
            SwingUtilities.invokeLater(() -> {
                rememberCorrected(out);
                showText(result.report + "\n" + MulticameraClockDrift.wrapReport(
                        "Wrote " + out.getAbsolutePath() + "\n"
                                + "The original recording was not changed.\n"),
                        "Wrote copy");
            });
        } catch (RuntimeException ex) {
            if (MulticameraClockDrift.isCancelled(ex) || cancel.get()) {
                SwingUtilities.invokeLater(() -> showText(result.report + "\nCancelled.\n", "Cancelled"));
            } else {
                SwingUtilities.invokeLater(() -> showText(result.report + "\n" + ex.getMessage() + "\n", "Failed"));
            }
        } catch (IOException ex) {
            if (cancel.get()) {
                SwingUtilities.invokeLater(() -> showText(result.report + "\nCancelled.\n", "Cancelled"));
            } else {
                SwingUtilities.invokeLater(() -> showText(result.report + "\n" + ex.getMessage() + "\n", "Failed"));
            }
        } finally {
            worker = null;
            SwingUtilities.invokeLater(() -> setRunning(false));
        }
    }

    private void rememberCorrected(File out) {
        if (getOwner() instanceof AEViewer viewer && viewer.recentFiles != null) {
            viewer.recentFiles.addFile(out);
        }
    }

    private void playCorrected() {
        File out = correctedFile;
        if (!(getOwner() instanceof AEViewer viewer) || out == null) {
            return;
        }
        try {
            viewer.getAePlayer().startPlayback(out);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage() != null ? ex.getMessage() : ex.toString(),
                    "Could not play file", JOptionPane.ERROR_MESSAGE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static JPanel labeled(String name, JSpinner spinner) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.add(new JLabel(name), BorderLayout.NORTH);
        panel.add(spinner, BorderLayout.CENTER);
        return panel;
    }
}
