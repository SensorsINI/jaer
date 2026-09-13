package net.sf.jaer.graphics;

import java.awt.BorderLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.aedat4.Aedat4Concat;
import net.sf.jaer.util.RecentFiles;
import net.sf.jaer.util.RecordingDiskSpace;
import net.sf.jaer.util.ShowFolderSaveConfirmation;

/**
 * File → Merge VCR deck… and Open-preview accessory: save-as, space check,
 * background {@link Aedat4Concat}. Source cassettes stay unless the user
 * chooses to delete the deck folder after a successful merge.
 */
public final class RecordingVcrMerge {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

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
        mergeInteractive(parent, deckOrFile, viewerFrom(parent, null));
    }

    /**
     * Same as {@link #mergeInteractive(Window, File)} with an explicit viewer
     * for Play merged / Recent Files (Open-preview accessory).
     */
    public static void mergeInteractive(Window parent, File deckOrFile, AEViewer viewer) {
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
        JCheckBox deleteSourcesCb = new JCheckBox("<html>Delete source folder<br>after successful merge</html>");
        deleteSourcesCb.setToolTipText("Remove the VCR deck folder (unmerged cassettes) after the concat file is written");
        JPanel accessory = new JPanel(new BorderLayout());
        accessory.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        accessory.add(deleteSourcesCb, BorderLayout.NORTH);
        save.setAccessory(accessory);
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
        boolean deleteSources = deleteSourcesCb.isSelected();
        if (deleteSources && isInsideOrSame(dest, deck)) {
            JOptionPane.showMessageDialog(parent,
                    "<html>The merged file is inside the source folder, so the folder will not be deleted.</html>",
                    "Merge VCR deck", JOptionPane.WARNING_MESSAGE);
            deleteSources = false;
        }
        if (deleteSources) {
            int confirm = JOptionPane.showConfirmDialog(parent,
                    "<html>After a successful merge, delete the source folder<br><code>"
                    + ShowFolderSaveConfirmation.escapeHtml(deck.getAbsolutePath())
                    + "</code><br>(" + sources.size()
                    + (sources.size() == 1 ? " cassette" : " cassettes")
                    + ")? This cannot be undone.</html>",
                    "Delete source folder", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                deleteSources = false;
            }
        }
        final File out = dest;
        final List<File> src = sources;
        final File deckDir = deck;
        final boolean deleteDeck = deleteSources;
        final AEViewer playViewer = viewerFrom(parent, viewer);
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
                    rememberMergedRecording(playViewer, r);
                    String sourceFate = "Source files were not deleted.";
                    if (deleteDeck) {
                        String err = deleteSourceDeck(deckDir, out);
                        if (err == null) {
                            forgetDeckFolder(playViewer, deckDir);
                            sourceFate = "Source folder was deleted.";
                        } else {
                            sourceFate = err;
                        }
                    }
                    showMergeDone(parent, playViewer, r, sourceFate);
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

    private static void showMergeDone(Window parent, AEViewer viewer, Aedat4Concat.Result r,
            String sourceFate) {
        File out = r.output;
        String msg = ShowFolderSaveConfirmation.htmlVcrMergeMessage(out, r.packets, r.sources.size(),
                sourceFate);
        Runnable play = null;
        if (viewer != null && out != null) {
            play = () -> {
                try {
                    if (viewer.getAePlayer() != null) {
                        viewer.getAePlayer().startPlayback(out);
                    }
                } catch (IOException e) {
                    log.log(Level.WARNING, "Could not play merged file: " + e, e);
                    JOptionPane.showMessageDialog(viewer,
                            e.getMessage() != null ? e.getMessage() : e.toString(),
                            "Could not play file", JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException ex) {
                    log.info("merged playback interrupted");
                }
            };
        }
        Window owner = viewer != null ? viewer : parent;
        ShowFolderSaveConfirmation dialog = new ShowFolderSaveConfirmation(
                owner, out, msg, play, "Play merged", "Merge VCR deck",
                "Open the merged file in AEViewer for playback");
        dialog.setVisible(true);
    }

    /**
     * Drop cassette paths from every viewer's Recent Files and put the merged
     * recording at the head.
     */
    static void rememberMergedRecording(AEViewer viewer, Aedat4Concat.Result r) {
        if (r == null || r.output == null) {
            return;
        }
        for (AEViewer v : viewersForRecentFiles(viewer)) {
            RecentFiles recent = v.getRecentFiles();
            if (recent == null) {
                continue;
            }
            recent.replaceFilesWithMerged(r.output, cassettesToDrop(recent, r.output, r.sources));
        }
    }

    static List<File> cassettesToDrop(RecentFiles recent, File merged, List<File> sources) {
        List<File> drop = new ArrayList<>();
        if (sources != null) {
            drop.addAll(sources);
        }
        File deck = null;
        if (sources != null) {
            for (File s : sources) {
                if (s != null && s.getParentFile() != null) {
                    deck = s.getParentFile().getAbsoluteFile();
                    break;
                }
            }
        }
        if (recent == null || deck == null) {
            return drop;
        }
        File mergedAbs = merged != null ? merged.getAbsoluteFile() : null;
        for (File listed : recent.snapshot()) {
            if (listed == null) {
                continue;
            }
            File listedAbs = listed.getAbsoluteFile();
            if (mergedAbs != null && listedAbs.equals(mergedAbs)) {
                continue;
            }
            File parent = listedAbs.getParentFile();
            if (parent != null && parent.equals(deck)
                    && RecordingVcrSession.cassetteIndexFromFile(listedAbs) > 0) {
                drop.add(listedAbs);
            }
        }
        return drop;
    }

    /**
     * Delete the VCR deck folder after a successful merge. No-op (with a reason)
     * if {@code keep} is inside the deck.
     *
     * @return {@code null} if the folder was removed, otherwise why it remains
     */
    static String deleteSourceDeck(File deck, File keep) {
        if (deck == null || !deck.isDirectory()) {
            return "No source folder to delete.";
        }
        File deckAbs = deck.getAbsoluteFile();
        if (keep != null && isInsideOrSame(keep, deckAbs)) {
            return "Merged file is inside the source folder; left cassettes on disk.";
        }
        deleteTree(deckAbs);
        if (deckAbs.exists()) {
            return "Could not delete " + deckAbs.getName() + " (file in use?).";
        }
        log.info("Deleted VCR source folder " + deckAbs);
        return null;
    }

    static boolean isInsideOrSame(File file, File dir) {
        if (file == null || dir == null) {
            return false;
        }
        Path f = file.getAbsoluteFile().toPath().normalize();
        Path d = dir.getAbsoluteFile().toPath().normalize();
        return f.startsWith(d);
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        if (!f.delete()) {
            log.warning("could not delete " + f);
        }
    }

    private static void forgetDeckFolder(AEViewer viewer, File deck) {
        if (deck == null) {
            return;
        }
        for (AEViewer v : viewersForRecentFiles(viewer)) {
            RecentFiles recent = v.getRecentFiles();
            if (recent != null) {
                recent.removeFile(deck);
            }
        }
    }

    private static List<AEViewer> viewersForRecentFiles(AEViewer viewer) {
        List<AEViewer> out = new ArrayList<>();
        if (viewer == null) {
            return out;
        }
        if (viewer.getJaerViewer() != null) {
            out.addAll(viewer.getJaerViewer().getViewers());
        }
        if (out.isEmpty()) {
            out.add(viewer);
        }
        return out;
    }

    private static AEViewer viewerFrom(Window parent, AEViewer hinted) {
        if (hinted != null) {
            return hinted;
        }
        Window w = parent;
        while (w != null) {
            if (w instanceof AEViewer v) {
                return v;
            }
            w = w.getOwner();
        }
        for (Window win : Window.getWindows()) {
            if (win instanceof AEViewer v && v.isDisplayable()) {
                return v;
            }
        }
        return null;
    }
}
