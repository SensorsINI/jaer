package net.sf.jaer.graphics;

import java.awt.Window;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.aedat4.Aedat4Concat;
import net.sf.jaer.util.RecordingDiskSpace;

/**
 * File → Merge VCR deck… and Open-preview accessory: save-as, space check,
 * background {@link Aedat4Concat}. Never deletes source cassettes.
 */
public final class RecordingVcrMerge {

    private RecordingVcrMerge() {
    }

    /**
     * Folder chooser then {@link #mergeInteractive(Window, File)}.
     */
    public static void chooseDeckAndMerge(Window parent, File startDir) {
        File initial = startDir != null && startDir.isDirectory() ? startDir : new File(".");
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose VCR deck folder");
        chooser.setApproveButtonText("Merge");
        chooser.setCurrentDirectory(initial);
        chooser.setSelectedFile(initial);
        int ret = chooser.showDialog(parent, "Merge");
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dir = chooser.getSelectedFile();
        if (dir != null && dir.isFile()) {
            dir = dir.getParentFile();
        }
        mergeInteractive(parent, dir);
    }

    /**
     * Confirm output path and run concat. No-op if {@code deck} is not a deck.
     */
    public static void mergeInteractive(Window parent, File deckOrFile) {
        File deck = RecordingVcrSession.deckFolder(deckOrFile);
        if (deck == null) {
            JOptionPane.showMessageDialog(parent,
                    "That folder is not a VCR deck (need vcr-session.txt or two *_cNNNN.aedat4 files).",
                    "Merge VCR deck", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<File> sources;
        try {
            sources = Aedat4Concat.sourcesFromDeck(deck);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Merge VCR deck",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        File suggested = Aedat4Concat.defaultOutput(deck);
        JFileChooser save = new JFileChooser();
        save.setDialogTitle("Save merged AEDAT-4");
        save.setSelectedFile(suggested);
        save.setCurrentDirectory(suggested.getParentFile());
        save.setFileFilter(new FileNameExtensionFilter("AEDAT-4 (*.aedat4)", "aedat4"));
        int ret = save.showSaveDialog(parent);
        if (ret != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dest = save.getSelectedFile();
        if (dest == null) {
            return;
        }
        if (!dest.getName().toLowerCase().endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4)) {
            dest = new File(dest.getParentFile(), dest.getName() + AEDataFile.DATA_FILE_EXTENSION_AEDAT4);
        }
        Aedat4Concat.SpaceCheck space = Aedat4Concat.checkSpace(sources, dest);
        if (!space.enough) {
            JOptionPane.showMessageDialog(parent,
                    "<html>Not enough free space to merge.<br>Need "
                    + RecordingDiskSpace.formatBytes(space.requiredBytes)
                    + " (sources "
                    + RecordingDiskSpace.formatBytes(space.sourceBytes)
                    + " + overhead + "
                    + RecordingDiskSpace.minFreeSpaceLabel() + " headroom).<br>This volume has "
                    + RecordingDiskSpace.formatBytes(space.usableBytes) + " free.</html>",
                    "Merge VCR deck", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (dest.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(parent,
                    dest.getName() + " exists. Replace it?",
                    "Merge VCR deck", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.OK_OPTION) {
                return;
            }
        }
        final File out = dest;
        final List<File> src = sources;
        ProgressMonitor monitor = new ProgressMonitor(parent,
                "Merging VCR deck", "Starting…", 0, 100);
        monitor.setMillisToPopup(200);
        SwingWorker<Aedat4Concat.Result, Void> worker = new SwingWorker<>() {
            @Override
            protected Aedat4Concat.Result doInBackground() throws Exception {
                return Aedat4Concat.mergeFiles(src, out, monitor);
            }

            @Override
            protected void done() {
                monitor.close();
                try {
                    Aedat4Concat.Result r = get();
                    JOptionPane.showMessageDialog(parent,
                            "<html>Wrote " + r.output.getAbsolutePath()
                            + "<br>" + r.packets + " packets from " + r.sources.size()
                            + " cassettes.<br>Source files were not deleted.</html>",
                            "Merge VCR deck", JOptionPane.INFORMATION_MESSAGE);
                } catch (ExecutionException e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    if (c instanceof InterruptedException) {
                        JOptionPane.showMessageDialog(parent, "Merge canceled.",
                                "Merge VCR deck", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    JOptionPane.showMessageDialog(parent,
                            c.getMessage() != null ? c.getMessage() : c.toString(),
                            "Merge VCR deck", JOptionPane.WARNING_MESSAGE);
                } catch (InterruptedException e) {
                    JOptionPane.showMessageDialog(parent, "Merge canceled.",
                            "Merge VCR deck", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
