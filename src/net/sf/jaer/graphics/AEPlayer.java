package net.sf.jaer.graphics;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import com.jogamp.opengl.GLException;
import java.time.ZoneId;
import java.util.logging.Level;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEDZInputStream;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStream.Marks;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4Lz4Rerecorder;
import net.sf.jaer.graphics.AEViewer.PlayMode;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.DATFileFilter;
import net.sf.jaer.util.IndexFileFilter;

/**
 * Handles file input of AEs to control the number of events/sample or period of
 * time in the sample, etc. It handles the file input stream, opening a dialog
 * box, etc. It also handles synchronization of different AEViewers as follows
 * (this refers to multiple-AEViewer time-locked playback synchronization, not
 * java object locking):
 * <p>
 * If the viewer is not synchronized, then all calls from the GUI are passed
 * directly to this instance of AEPlayer. Thus local control always happens.
 * <p>
 * If the viewer is synchronized, then all GUI calls pass instead to the
 * JAERViewer instance that contains (or started) this viewer. Then the
 * JAERViewer AEPlayer calls all the viewers to take the player action (e.g.
 * rewind, go to next slice, change direction).
 * <p>
 * Thus whichever controls the user uses to control playback, the viewers are
 * all synchronized properly without recursively. The "master" is indicated by
 * the GUI action, which routes the request either to this instance's AEPlayer
 * or to the JAERViewer AEPlayer.
 */
public class AEPlayer extends AbstractAEPlayer implements AEFileInputStreamInterface {

    JFileChooser fileChooser;

    /**
     * Make a new AEPlayer
     *
     * @param viewer the viewer who is using us.
     * @param viewer from refactoring, refers to the same viewer
     */
    public AEPlayer(AEViewer viewer) {
        super(viewer);
    }

    private boolean isSyncEnabled() {
        return viewer.getJaerViewer().isSyncEnabled();
    }

    @Override
    public boolean isChoosingFile() {
        return (fileChooser != null) && fileChooser.isVisible();
    }

    /**
     * Called when user asks to open data file file dialog.
     */
    @Override
    public void openAEInputFileDialog() {
//        try{Thread.currentThread().sleep(200);}catch(InterruptedException e){}
        fileChooser = new JFileChooser();
        ChipDataFilePreview preview = new ChipDataFilePreview(fileChooser, viewer.getChip());
        // from book swing hacks
        new FileDeleter(fileChooser, preview);
        fileChooser.addPropertyChangeListener(preview);
        fileChooser.setAccessory(preview);
        String lastFilePath = this.viewer.prefs.get("AEViewer.lastFile", "");
        // get the last folder
        viewer.lastFile = new File(lastFilePath);
        DATFileFilter.installOpenDialogFilters(fileChooser, null);
        fileChooser.setCurrentDirectory(viewer.lastFile);
        // sets the working directory of the chooser
//            boolean wasPaused=isPaused();
        setPaused(true);
        try {
            int retValue = fileChooser.showOpenDialog(viewer);
            if (retValue == JFileChooser.APPROVE_OPTION) {
                viewer.lastFile = fileChooser.getSelectedFile();
                if (viewer.lastFile != null) {
                    viewer.recentFiles.addFile(viewer.lastFile);
                }
                final File file = viewer.lastFile;
                startPlayback(file);

            } else {
                preview.showFile(null);
            }
        } catch (GLException e) {
            log.warning(e.toString());
            preview.showFile(null);
        } catch (IOException e) {
            log.warning(e.toString());
        } catch (InterruptedException e) {
            log.warning(e.toString());
        } finally {
            fileChooser = null;
//        viewer.chipCanvas.setScale(oldScale);
            // restore persistent scale so that we don't get tiny size on next startup
            setPaused(false);
        }
    }

    @Override
    public void setDoSingleStepEnabled(boolean b) {
        viewer.doSingleStepEnabled = b;
    }

    @Override
    public void doSingleStep() {
        viewer.doSingleStep();
    }

    @Override
    public long getAbsoluteStartingTimeMs() {
        return aeInputStream.getAbsoluteStartingTimeMs();
    }

    @Override
    public int getDurationUs() {
        return aeInputStream.getDurationUs();
    }

    @Override
    public int getFirstTimestamp() {
        return aeInputStream.getFirstTimestamp();
    }

    @Override
    public File getFile() {
        return aeInputStream.getFile();
    }

    @Override
    public String getFileInfo() {
        return aeInputStream == null ? "" : aeInputStream.getFileInfo();
    }

    @Override
    public int getLastTimestamp() {
        return aeInputStream.getLastTimestamp();
    }

    @Override
    public int getMostRecentTimestamp() {
        return aeInputStream.getMostRecentTimestamp();
    }

    @Override
    public void setFile(File file) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getTimestampResetBitmask() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setTimestampResetBitmask(int timestampResetBitmask) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getCurrentStartTimestamp() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setCurrentStartTimestamp(int currentStartTimestamp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ZoneId getZoneId() {
        if (aeInputStream != null) {
            return aeInputStream.getZoneId();
        } else {
            return ZoneId.systemDefault();
        }
    }

    @Override
    public boolean jumpToNextMarker() {
        if (aeInputStream != null) {
            return aeInputStream.jumpToNextMarker();
        }
        return false;
    }

    @Override
    public boolean jumpToPrevMarker() {
        if (aeInputStream != null) {
            return aeInputStream.jumpToPrevMarker();
        }
        return false;
    }

    public class FileDeleter extends KeyAdapter implements PropertyChangeListener {

        private JFileChooser chooser;
        private ChipDataFilePreview preview;
        File file = null;

        /**
         * adds a key-released listener on the JFileChooser FilePane inner
         * classes so that user can use Delete key to delete the file that is
         * presently being shown in the preview window
         *
         * @param chooser the chooser
         * @param preview the data file preview
         */
        public FileDeleter(JFileChooser chooser, ChipDataFilePreview preview) {
            super();
            this.chooser = chooser;
            this.preview = preview;
            chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY, this);
            Component comp = addDeleteListener(chooser);
        }

        /**
         * is called when the file selection is changed. Bound to the
         * SELECTED_FILE_CHANGED_PROPERTY.
         */
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            // comes from chooser when new file is selected
            if (evt.getNewValue() instanceof File) {
                file = (File) evt.getNewValue();
            } else {
                file = null;
            }
        }

        private Component addDeleteListener(Component comp) {
//            System.out.println("");
//            System.out.println("comp="+comp);
//            if (comp.getClass() == sun.swing.FilePane.class) return comp;
            if (comp instanceof Container) {
//                System.out.println(comp+"\n");
//                comp.addMouseListener(new MouseAdapter(){
//                    public void mouseEntered(MouseEvent e){
//                        System.out.println("mouse entered: "+e);
//                    }
//                });
                // if this is a known filepane class, then add a key listener for deleting log files.
                // may need to remove this in future release of java and
                //find a portable way to detect we are in the FilePane
//                    if(comp.getClass().getEnclosingClass()==sun.swing.FilePane.class) {
//                        System.out.println("******adding keyListener to "+comp);
                comp.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
//                                    System.out.println("delete key typed from "+e.getSource());
                            deleteFile();
                        }
                    }
                });
//                    }
                Component[] components = ((Container) comp).getComponents();
                for (Component component : components) {
                    Component child = addDeleteListener(component);
                    if (child != null) {
                        return child;
                    }
                }
            }
            return null;
        }

        void deleteFile() {
            if (file == null) {
                return;
            }
            log.fine("trying to delete file " + file);
            preview.deleteCurrentFile();
        }
    }

    /**
     * Starts playback on the data file. If the file is an index file, the
     * JAERViewer is called to start playback of the set of data files. Fires a
     * property change event "fileopen", after playMode is changed to PLAYBACK.
     *
     * @param file the File to play.
     */
    @Override
    public synchronized void startPlayback(final File file) throws IOException, InterruptedException {
        if (aeInputStream != null) {
            try {
                aeInputStream.close();
            } catch (IOException e) {
                log.warning(String.format("Could not close existing file: %s", e.toString()));
            }
        }
        log.info("starting playback with file=" + file);
        clearIgnoreRecordingToggleKey();
        inputFile = file;
        if ((file == null) || !file.isFile()) {
            throw new FileNotFoundException("file not found: " + file);
        }
        // Stop ViewLoop from opening USB / flipping to LIVE before any AEChip switch.
        // Chip switch may close a live interface; doing that while ViewLoop is in LIVE hangs
        // (seen with NRV plugged in). Known race: ViewLoop openAEMonitor() can set LIVE.
        viewer.beginFilePlaybackOpen();
        setPaused(true);
        // Filename / header / multi-stream check (may switch AEChip or cancel).
        if (!viewer.ensureChipCompatibleWithRecording(file)) {
            viewer.endFilePlaybackOpen();
            setPaused(false);
            return;
        }
        // Dependent-block LZ4 DV files: offer sibling *-rerecord.aedat4 for fast playback.
        final Aedat4Lz4Rerecorder.OpenPlan lz4Plan = viewer.offerAedat4Lz4Rerecord(file);
        if (lz4Plan == null || lz4Plan.fileToOpen == null) {
            viewer.endFilePlaybackOpen();
            setPaused(false);
            return;
        }
        final File playFile = lz4Plan.fileToOpen;
        final File rerecordFrom = lz4Plan.rerecordFrom;
        inputFile = playFile;
        // idea is that we set open the file and set playback mode and the ViewLoop.run
        // loop will then render from the file.
        String ext = "." + IndexFileFilter.getExtension(playFile); // TODO change to use of a new static method in AEDataFile for determining file type
        if (ext.equals(AEDataFile.INDEX_FILE_EXTENSION) || ext.equals(AEDataFile.OLD_INDEX_FILE_EXTENSION)) {
            try {
                if (viewer.getJaerViewer() != null) {
                    viewer.getJaerViewer().getSyncPlayer().startPlayback(playFile);
                }
            } finally {
                viewer.endFilePlaybackOpen();
                setPaused(false);
            }
            return;
        }

        int tries = 20;
        while ((viewer.getChip() == null) && (tries-- > 0)) {
            log.info("null AEChip in AEViewer, waiting... " + tries);
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                break;
            }
        }
        if (viewer.getChip() == null) {
            viewer.endFilePlaybackOpen();
            setPaused(false);
            throw new IOException("chip is not set in AEViewer so we cannot contruct the file input stream for it");
        }
        final ProgressMonitor progressMonitor = new ProgressMonitor(viewer, "Opening " + playFile,
                rerecordFrom != null ? "Re-recording LZ4 for faster playback" : "Generating or loading cache of events",
                0, 100);
        progressMonitor.setMillisToPopup(300);
        progressMonitor.setMillisToDecideToPopup(300);
        final Timer[] cancelPollRef = new Timer[1];
        final SwingWorker<AEFileInputStreamInterface, Void> worker = new SwingWorker<AEFileInputStreamInterface, Void>() {
            volatile Exception exception = null;

            @Override
            protected AEFileInputStreamInterface doInBackground() throws Exception {
                AEFileInputStreamInterface stream = null;
                try {
                    log.fine("startPlayback.doInBackground begin file=" + playFile.getName()
                            + " chip=" + viewer.getChip().getClass().getSimpleName()
                            + " thread=" + Thread.currentThread().getName());
                    setPaused(true);
                    log.fine("paused=true");
                    // Do not set WAIT_CURSOR for the whole open — ProgressMonitor is enough.
                    // A stuck wait cursor was left behind when open hung or cancel raced.
                    progressMonitor.setProgress(0);
                    if (rerecordFrom != null) {
                        progressMonitor.setNote("Re-recording LZ4 (independent blocks)…");
                        try {
                            Aedat4Lz4Rerecorder.rerecord(rerecordFrom, playFile, progressMonitor);
                        } catch (InterruptedException ie) {
                            log.info("AEDAT-4 LZ4 re-record canceled: " + rerecordFrom.getName());
                            return null;
                        } catch (Exception e) {
                            exception = e;
                            log.warning("AEDAT-4 LZ4 re-record failed: " + e);
                            e.printStackTrace();
                            return null;
                        }
                        if (isCancelled() || progressMonitor.isCanceled()) {
                            log.info("File open canceled after re-record: " + playFile.getName());
                            return null;
                        }
                    }
                    progressMonitor.setNote("Opening " + playFile.getName());
                    try {
                        log.fine("constuctFileInputStream calling");
                        stream = viewer.getChip().constuctFileInputStream(playFile, progressMonitor);
                        log.fine("constuctFileInputStream returned " + (stream == null ? "null" : stream.getClass().getSimpleName()));
                    } catch (Exception e) {
                        if (progressMonitor.isCanceled() || Thread.currentThread().isInterrupted()
                                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("cancel"))) {
                            log.info("File open canceled: " + playFile.getName());
                            return null;
                        }
                        exception = e;
                        log.warning("Could not construct input stream: " + e);
                        e.printStackTrace();
                        return null;
                    }
                    if (isCancelled() || progressMonitor.isCanceled()) {
                        log.info("File open canceled after construct: " + playFile.getName());
                        if (stream != null) {
                            try {
                                stream.close();
                            } catch (IOException ignore) {
                            }
                        }
                        return null;
                    }
                    // Configure stream only on this worker thread; UI updates run in done() on EDT.
                    log.fine("configuring stream on worker thread");
                    stream.setFile(playFile);
                    stream.marksInitialize();
                    log.fine("marksInitialize done (" + stream.getClass().getSimpleName() + ")");
                    stream.setRepeat(isRepeat());
                    stream.setNonMonotonicTimeExceptionsChecked(viewer.getCheckNonMonotonicTimeExceptionsEnabledCheckBoxMenuItem().isSelected());
                    stream.setTimestampResetBitmask(viewer.getAeFileInputStreamTimestampResetBitmask());
                    stream.getSupport().addPropertyChangeListener(viewer);
                    log.fine("stream listeners attached");
                    if ((viewer.getJaerViewer() != null) && (viewer.getJaerViewer().getViewers().size() > 1)) {
                        try {
                            log.fine("multi-viewer rewind");
                            stream.rewind();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    log.fine("doInBackground returning stream ok");
                    return stream;
                } catch (Exception e) {
                    exception = e;
                    log.warning("AEPlayer.startPlayback background failed: " + e);
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (IOException ignore) {
                        }
                    }
                    return null;
                }
            }

            @Override
            protected void done() {
                log.fine("startPlayback.done() begin EDT=" + SwingUtilities.isEventDispatchThread()
                        + " cancelled=" + isCancelled()
                        + " monitorCancelled=" + progressMonitor.isCanceled());
                try {
                    AEFileInputStreamInterface stream = null;
                    try {
                        log.fine("done(): worker.get()");
                        stream = isCancelled() ? null : get();
                        log.fine("done(): get() -> " + (stream == null ? "null" : stream.getClass().getSimpleName()));
                    } catch (Exception e) {
                        log.fine("done(): get() threw " + e);
                        if (exception == null) {
                            exception = e;
                        }
                    }
                    if (exception != null) {
                        JOptionPane.showMessageDialog(
                                viewer != null ? viewer : null,
                                "in AEPlayer.startPlayback(), caught " + exception,
                                "AEPlayer Exception",
                                JOptionPane.ERROR_MESSAGE);
                    }
                    if (stream == null || isCancelled() || progressMonitor.isCanceled()) {
                        log.fine("done(): aborting open (null stream or cancel)");
                        if (stream != null) {
                            try {
                                stream.close();
                            } catch (IOException ignore) {
                            }
                        }
                        aeInputStream = null;
                        // beginFilePlaybackOpen() forced PLAYBACK before the stream existed;
                        // leave WAITING so ViewLoop does not call getNextPacket() on a null stream.
                        if (viewer.getPlayMode() == AEViewer.PlayMode.PLAYBACK) {
                            viewer.setPlayMode(AEViewer.PlayMode.WAITING);
                        }
                        viewer.endFilePlaybackOpen();
                        viewer.setPaused(false);
                        if (isCancelled() || progressMonitor.isCanceled()) {
                            log.info("Playback open canceled for " + playFile.getName());
                        } else if (rerecordFrom != null) {
                            log.info("Playback open aborted (re-record/open failed) for "
                                    + rerecordFrom.getName());
                        } else {
                            log.info("Playback open aborted for " + playFile.getName());
                        }
                        return;
                    }
                    aeInputStream = stream;
                    // Close progress before setPlayMode — dialog/EDT must not contend with ViewLoop locks.
                    try {
                        progressMonitor.setProgress(100);
                    } catch (Exception ignore) {
                    }
                    progressMonitor.close();
                    log.info("AEDAT-4 open: EDT setup begin (setPlayMode PLAYBACK)");
                    log.fine("done(): setPlayMode(PLAYBACK)");
                    viewer.setPlayMode(AEViewer.PlayMode.PLAYBACK);
                    log.info("AEDAT-4 open: setPlayMode(PLAYBACK) returned");
                    log.fine("done(): addMeToPropertyChangeListeners");
                    viewer.getPlayerControls().addMeToPropertyChangeListeners(aeInputStream);
                    log.fine("done(): setPlaybackControlsEnabledState");
                    viewer.setPlaybackControlsEnabledState(true);
                    log.fine("done(): fixRecordingControls");
                    viewer.fixRecordingControls();
                    try {
                        log.fine("done(): renderer.resetFrame");
                        viewer.getChip().getRenderer().resetFrame(0);
                    } catch (Exception e) {
                        log.warning("tried to reset renderer but caught " + e);
                    }
                    log.fine("done(): applying restored marks to player controls");
                    applyRestoredMarksToPlayerControls(aeInputStream);
                    if (viewer.getChip().getRenderer() != null && (viewer.getChip().getRenderer() instanceof AEChipRenderer)) {
                        log.fine("done(): showRenderingModeTextOnAeViewer");
                        AEChipRenderer renderer = (AEChipRenderer) viewer.getChip().getRenderer();
                        renderer.showRenderingModeTextOnAeViewer();
                    }
                    log.fine("done(): fire EVENT_FILEOPEN");
                    getSupport().firePropertyChange(EVENT_FILEOPEN, null, playFile);
                    log.fine("done(): setInputFile");
                    viewer.setInputFile(file);
                    log.fine("done(): endFilePlaybackOpen + setPaused(false)");
                    viewer.endFilePlaybackOpen();
                    viewer.setPaused(false);
                    log.info("AEDAT-4 open: EDT setup complete, playback should run");
                    log.fine("done(): playback UI setup complete");
                } finally {
                    log.fine("done(): finally close progress + cursor");
                    try {
                        progressMonitor.setProgress(100);
                    } catch (Exception ignore) {
                    }
                    try {
                        progressMonitor.close();
                    } catch (Exception ignore) {
                    }
                    if (viewer != null) {
                        viewer.endFilePlaybackOpen(); // idempotent if already cleared after successful setup
                        viewer.setCursor(Cursor.getDefaultCursor());
                    }
                    if (cancelPollRef[0] != null) {
                        cancelPollRef[0].stop();
                    }
                    log.fine("done(): finally complete");
                }
            }
        };
        cancelPollRef[0] = new Timer(200, e -> {
            if (progressMonitor.isCanceled() && !worker.isDone()) {
                log.info("Cancel requested while opening " + file.getName());
                worker.cancel(true);
            }
        });
        cancelPollRef[0].setRepeats(true);
        cancelPollRef[0].start();
        worker.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getSource() == worker && progressMonitor.isCanceled() && !worker.isDone()) {
                    worker.cancel(true);
                }
            }
        });

        worker.execute();
    }

    /**
     * After open, push restored IN/OUT/other marks onto the slider on the EDT.
     * {@link AEFileInputStreamInterface#marksInitialize()} may have loaded them
     * on a worker thread before property listeners were attached.
     */
    private void applyRestoredMarksToPlayerControls(AEFileInputStreamInterface stream) {
        if (stream == null || viewer.getPlayerControls() == null) {
            return;
        }
        Marks restored = playbackMarksFor(stream);
        boolean hasOther = restored != null && restored.otherMarks != null && !restored.otherMarks.isEmpty();
        if (stream.isMarkInSet() || stream.isMarkOutSet() || hasOther) {
            if (restored == null) {
                restored = new Marks();
                restored.markIn = stream.getMarkInPosition();
                restored.markOut = stream.getMarkOutPosition();
            }
            viewer.getPlayerControls().setMarks(restored);
            if (stream.isMarkInSet()) {
                getSupport().firePropertyChange(AEInputStream.EVENT_MARK_IN_SET, null, stream.getMarkInPosition());
            }
            if (stream.isMarkOutSet()) {
                getSupport().firePropertyChange(AEInputStream.EVENT_MARK_OUT_SET, null, stream.getMarkOutPosition());
            }
        } else {
            getSupport().firePropertyChange(AEInputStream.EVENT_MARKS_CLEARED, false, true);
        }
    }

    /** Shared slider-mark extraction for all preference-backed input streams. */
    static Marks playbackMarksFor(AEFileInputStreamInterface stream) {
        if (stream instanceof AEFileInputStream a2) {
            return a2.getMarks();
        }
        if (stream instanceof Aedat4FileInputStream a4) {
            return a4.getPlaybackMarks();
        }
        if (stream instanceof AEDZInputStream aedz) {
            return aedz.getPlaybackMarks();
        }
        return null;
    }

    /**
     * stops playback. If not in PLAYBACK mode, then just returns. If playing
     * back, could be waiting during sleep or during CyclicBarrier.await call in
     * CaviarViewer. In case this is the case, we send an interrupt to the the
     * ViewLoop thread to stop this waiting.
     */
    @Override
    public void stopPlayback() {
        stopPlayback(true);
    }

    /**
     * Stops file playback.
     *
     * @param resumeLive if true and the camera is still open, re-enable USB
     *        acquisition and go {@code LIVE}. Pass false from {@code cleanup()}
     *        so exit/chip-switch does not start ISSD streaming just to close the device.
     */
    public void stopPlayback(boolean resumeLive) {
        if (viewer.getPlayMode() != AEViewer.PlayMode.PLAYBACK) {
            return;
        }

        // Resume live only if hardware is already open. Never call aemon.open() here:
        // cleanup() used to close the device then call stopPlayback(), and open() can hang
        // the EDT in native USB (NRV LibUsb.getStringDescriptorAscii while the reader thread
        // is stuck in deallocateTransfers/handleEventsTimeout).
        if (resumeLive && viewer.aemon != null && viewer.aemon.isOpen()) {
            try {
                viewer.aemon.setEventAcquisitionEnabled(true);
                if (viewer.aemon.getChip().getBiasgen() != null) {
                    viewer.aemon.getChip().getBiasgen().sendConfiguration(viewer.aemon.getChip().getBiasgen());
                }
                viewer.setPlayMode(AEViewer.PlayMode.LIVE);
            } catch (HardwareInterfaceException e) {
                viewer.setPlayMode(AEViewer.PlayMode.WAITING);
                log.warning(e.toString());
                e.printStackTrace();
            } catch (IllegalStateException ise) {
                viewer.setPlayMode(AEViewer.PlayMode.WAITING);
                log.warning(ise.toString());
            }
        } else {
            viewer.setPlayMode(AEViewer.PlayMode.WAITING);
        }
        viewer.setPlaybackControlsEnabledState(false);
        try {
            if (aeInputStream != null) {
                aeInputStream.close();
                aeInputStream = null;
            }
        } catch (IOException ignore) {
            ignore.printStackTrace();
        }
        clearIgnoreRecordingToggleKey();
        viewer.setTitleAccordingToState();
    }

    @Override
    public void rewind() {
        cancelJog();
        if (aeInputStream == null) {
            return;
        }
        try {
            aeInputStream.rewind();
            if (viewer != null) {
                viewer.filterChain.reset(); // already done in aePlayer
                viewer.getRenderer().resetAccumulation();
            } else {
                log.warning("null AEViewer, cannot reset filter change or accumulation mode");
            }
        } catch (Exception e) {
            log.warning("rewind exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public AEPacketRaw getNextPacket() {
        return getNextPacket(null);
    }

    @Override
    public AEPacketRaw getNextPacket(AbstractAEPlayer player) {
        if (player != this) {
            throw new UnsupportedOperationException("tried to get data from some other player");
        }
        AEPacketRaw aeRaw = null;
        if (aeInputStream == null) {
            // Common after canceling file open / LZ4 re-record before the stream is ready.
            return new AEPacketRaw(0);
        }

        try {
            boolean flex = viewer.aePlayer.isFlexTimeEnabled();
            if (!jogOccuring || jogPacketsLeft == 0) {
                int slice = flex ? viewer.aePlayer.getPacketSizeEvents() : viewer.aePlayer.getTimesliceUs();
                if (!flex) {
                    aeRaw = aeInputStream.readPacketByTime(slice);
                } else {
                    aeRaw = aeInputStream.readPacketByNumber(slice);
                }
            } else {
                // Must re-read slice AFTER setDirection* — previously slice was captured once
                // while still positive, so jog-backwards kept calling readPacket*(+dt).
                if (log.isLoggable(Level.FINE)) {
                    log.fine(String.format(
                            "jog begin left=%d flex=%s pos=%d stream=%s",
                            jogPacketsLeft, flex,
                            aeInputStream != null ? aeInputStream.position() : -1,
                            aeInputStream != null ? aeInputStream.getClass().getSimpleName() : "null"));
                }
                // Check jogOccuring each step so Esc (cancelJog on EDT) can abort remaining
                // queued steps between slow reads (e.g. DSEC HDF5 window loads).
                while (jogOccuring && jogPacketsLeft != 0) {
                    boolean forwards = jogPacketsLeft >= 0;
                    setDirectionForwards(forwards);
                    int slice = flex ? viewer.aePlayer.getPacketSizeEvents() : viewer.aePlayer.getTimesliceUs();
                    long posBefore = aeInputStream.position();
                    if (!flex) {
                        aeRaw = aeInputStream.readPacketByTime(slice);
                    } else {
                        aeRaw = aeInputStream.readPacketByNumber(slice);
                    }
                    if (log.isLoggable(Level.FINE)) {
                        log.fine(String.format(
                                "jog step forwards=%s slice=%d pos %d->%d events=%d left=%d",
                                forwards, slice, posBefore, aeInputStream.position(),
                                aeRaw != null ? aeRaw.getNumEvents() : -1, jogPacketsLeft));
                    }
                    if (!jogOccuring) {
                        break; // cancelled during read
                    }
                    if (jogPacketsLeft < 0) {
                        jogPacketsLeft++;
                    } else if (jogPacketsLeft > 0) {
                        jogPacketsLeft--;
                    }
                }
                if (jogOccuring && jogPacketsLeft == 0) {
                    jogOccuring = false;
                    setDirectionForwards(true);
                    setJogWaitCursor(false);
                    if (log.isLoggable(Level.FINE)) {
                        log.fine(String.format("jog done pos=%d",
                                aeInputStream != null ? aeInputStream.position() : -1));
                    }
                } else if (!jogOccuring) {
                    // cancelled mid-drain; cancelJog already cleared cursor
                    setJogWaitCursor(false);
                }
            }
            return aeRaw;
        } catch (EOFException e) {
            log.fine(String.format("%s: %s", player.getAEInputStream().getFile(), e.toString()));
            cancelJog();
            setDirectionForwards(true);
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignore) {
            }
            if (repeat) {
                viewer.getAePlayer().rewind();
            }
            return aeRaw;
        } catch (java.nio.channels.ClosedChannelException e) {
            // Thread.interrupt() closes FileChannel (incl. ClosedByInterruptException); do not printStackTrace every frame.
            long now = System.currentTimeMillis();
            if (now - lastClosedChannelWarnMs > 2000) {
                lastClosedChannelWarnMs = now;
                log.warning("FileChannel closed (often by ViewLoop interrupt); returning empty packet: " + e);
            }
            setDirectionForwards(true);
            cancelJog();
            return new AEPacketRaw(0);
        } catch (Exception anyOtherException) {
            setDirectionForwards(true);
            cancelJog();
            // Rate-limit: cancel races used to spam NPE stack traces every ViewLoop frame.
            long now = System.currentTimeMillis();
            if (now - lastGetNextPacketWarnMs > 2000) {
                lastGetNextPacketWarnMs = now;
                log.warning(anyOtherException.toString() + ", returning empty AEPacketRaw");
                anyOtherException.printStackTrace();
            }
            return new AEPacketRaw(0);
        }
    }

    private long lastGetNextPacketWarnMs;

    private long lastClosedChannelWarnMs;

    /**
     * Tries to adjust timeslice to approach realtime playback.
     *
     */
    @Override
    public void adjustTimesliceForRealtimePlayback() {
        if (!isRealtimeEnabled() || isPaused()) {
            return;
        }
        float fps = viewer.getFrameRater().getAverageFPS();
        if (fps < 1f) {
            return; // avoid timeslice → ∞ when first frames are slow (sparse AEDAT-4 decode)
        }
        float samplePeriodS = getTimesliceUs() * 1.0E-6F;
        float factor = fps * samplePeriodS;
        if (factor < 1e-3f || !Float.isFinite(factor)) {
            return;
        }
        setTimesliceUs((int) (getTimesliceUs() / factor));
    }

    @Override
    public float getFractionalPosition() {
        if (aeInputStream == null) {
            log.warning("AEViewer.AEPlayer.getFractionalPosition: null fileAEInputStream, returning 0");
            return 0;
        }
        float fracPos = aeInputStream.getFractionalPosition();
        return fracPos;
    }

    @Override
    public long position() {
        return aeInputStream.position();
    }

    @Override
    public void position(long event) {
        aeInputStream.position(event);
    }

    @Override
    public AEPacketRaw readPacketByNumber(int n) throws IOException {
        return aeInputStream.readPacketByNumber(n);
    }

    @Override
    public AEPacketRaw readPacketByTime(int dt) throws IOException {
        return aeInputStream.readPacketByTime(dt);
    }

    @Override
    public long size() {
        return aeInputStream.size();
    }

    @Override
    public void clearMarks() {
        if (aeInputStream != null) {
            aeInputStream.clearMarks();
        }
    }

    @Override
    public long getMarkInPosition() {
        if (aeInputStream == null) {
            return -1;
        }
        return aeInputStream.getMarkInPosition();
    }

    @Override
    public long getMarkOutPosition() {
        if (aeInputStream == null) {
            return -1;
        }
        return aeInputStream.getMarkOutPosition();
    }

    @Override
    public boolean isMarkInSet() {
        if (aeInputStream == null) {
            return false;
        }
        return aeInputStream.isMarkInSet();
    }

    @Override
    public boolean isMarkOutSet() {
        if (aeInputStream == null) {
            return false;
        }
        return aeInputStream.isMarkOutSet();
    }

    @Override
    public long setMarkIn() {
        if (aeInputStream == null) {
            return -1;
        }
        return aeInputStream.setMarkIn();
    }

    @Override
    public long setMarkOut() {
        if (aeInputStream == null) {
            return -1;
        }
        return aeInputStream.setMarkOut();
    }

    @Override
    public boolean toggleMarker() {
        if (aeInputStream != null) {
            return aeInputStream.toggleMarker();
        }
        return false;
    }

    @Override
    public void setFractionalPosition(float frac) {
        if (aeInputStream == null) {
            return;
        }
        aeInputStream.setFractionalPosition(frac);
        if (viewer != null) {
            viewer.filterChain.reset(); // already done in aePlayer
            viewer.getRenderer().resetAccumulation();
//            viewer.interruptViewloop(); // causes havoc in AEFileInputStream with the mapped FileChannel being ClosedByInterrupt exceptions
        }
    }

    @Override
    public void setTime(int time) {
//            System.out.println(this+".setTime("+time+")");
        if (aeInputStream != null) {
            aeInputStream.setCurrentStartTimestamp(time);
        } else {
            log.warning("null AEInputStream");
            Thread.dumpStack();
        }
    }

    @Override
    public int getTime() {
        if (aeInputStream == null) {
            return 0;
        }
        return aeInputStream.getMostRecentTimestamp();
    }

    @Override
    public AEFileInputStreamInterface getAEInputStream() {
        return aeInputStream;
    }

    /**
     * Returns state of repeat.
     *
     * @return true if the playback is repeated.
     */
    @Override
    public boolean isRepeat() {
        return repeat;
    }

    /**
     * Repeats playback and sets this property on the existing AEFileInputStream
     * if it is not null. Fires property change EVENT_REPEAT.
     *
     * @param yes true to pause, false to resume.
     * @see #EVENT_REPEAT
     */
    @Override
    public void setRepeat(boolean yes) {
        if (aeInputStream != null) {
            aeInputStream.setRepeat(yes);
        }
        super.setRepeat(yes);
    }

    /**
     * Says if checking for non-monotonic timestamps in input file is enabled.
     *
     * @return true if enabled.
     */
    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() {
        if (aeInputStream == null) {
            return false;
        }
        return aeInputStream.isNonMonotonicTimeExceptionsChecked();
    }

    /**
     * Enables or disables checking for non-monotonic timestamps in input file.
     *
     * @param yes true to check and log exceptions (up to some limit)
     */
    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) {
        if (aeInputStream == null) {
            log.warning("null fileAEInputStream");
            return;
        }
        aeInputStream.setNonMonotonicTimeExceptionsChecked(yes);
    }
}
