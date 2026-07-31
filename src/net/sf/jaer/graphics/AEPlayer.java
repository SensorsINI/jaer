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
import javax.swing.filechooser.FileFilter;

import com.jogamp.opengl.GLException;
import java.time.ZoneId;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
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
    FileFilter lastFilter = null;

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
//            fileChooser.setFileFilter(datFileFilter);
        IndexFileFilter indexFileFilter = new IndexFileFilter();
        fileChooser.addChoosableFileFilter(indexFileFilter);
        DATFileFilter datFileFilter = new DATFileFilter();
        fileChooser.addChoosableFileFilter(datFileFilter);
        if (lastFilter == null) {
            fileChooser.setFileFilter(datFileFilter);
        } else {
            fileChooser.setFileFilter(lastFilter);
        }
        fileChooser.setCurrentDirectory(viewer.lastFile);
        // sets the working directory of the chooser
//            boolean wasPaused=isPaused();
        setPaused(true);
        try {
            int retValue = fileChooser.showOpenDialog(viewer);
            if (retValue == JFileChooser.APPROVE_OPTION) {
                lastFilter = fileChooser.getFileFilter();
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
        inputFile = file;
        if ((file == null) || !file.isFile()) {
            throw new FileNotFoundException("file not found: " + file);
        }
        // Filename / header chip check before open (may switch AEChip or cancel).
        if (!viewer.ensureChipCompatibleWithRecording(file)) {
            return;
        }
        // Stop ViewLoop from opening USB / flipping to LIVE while we index the file.
        // Known race: ViewLoop openAEMonitor() can set LIVE and ignore playback.
        viewer.beginFilePlaybackOpen();
        setPaused(true);
        // idea is that we set open the file and set playback mode and the ViewLoop.run
        // loop will then render from the file.
        String ext = "." + IndexFileFilter.getExtension(file); // TODO change to use of a new static method in AEDataFile for determining file type
        if (ext.equals(AEDataFile.INDEX_FILE_EXTENSION) || ext.equals(AEDataFile.OLD_INDEX_FILE_EXTENSION)) {
            try {
                if (viewer.getJaerViewer() != null) {
                    viewer.getJaerViewer().getSyncPlayer().startPlayback(file);
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
        final ProgressMonitor progressMonitor = new ProgressMonitor(viewer, "Opening " + file, "Generating or loading cache of events", 0, 100);
        progressMonitor.setMillisToPopup(300);
        progressMonitor.setMillisToDecideToPopup(300);
        final Timer[] cancelPollRef = new Timer[1];
        final SwingWorker<AEFileInputStreamInterface, Void> worker = new SwingWorker<AEFileInputStreamInterface, Void>() {
            volatile Exception exception = null;

            @Override
            protected AEFileInputStreamInterface doInBackground() throws Exception {
                AEFileInputStreamInterface stream = null;
                try {
                    log.fine("startPlayback.doInBackground begin file=" + file.getName()
                            + " chip=" + viewer.getChip().getClass().getSimpleName()
                            + " thread=" + Thread.currentThread().getName());
                    setPaused(true);
                    log.fine("paused=true");
                    // Do not set WAIT_CURSOR for the whole open — ProgressMonitor is enough.
                    // A stuck wait cursor was left behind when open hung or cancel raced.
                    progressMonitor.setProgress(0);
                    progressMonitor.setNote("Opening " + file.getName());
                    try {
                        log.fine("constuctFileInputStream calling");
                        stream = viewer.getChip().constuctFileInputStream(file, progressMonitor);
                        log.fine("constuctFileInputStream returned " + (stream == null ? "null" : stream.getClass().getSimpleName()));
                    } catch (Exception e) {
                        if (progressMonitor.isCanceled() || Thread.currentThread().isInterrupted()
                                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("cancel"))) {
                            log.info("File open canceled: " + file.getName());
                            return null;
                        }
                        exception = e;
                        log.warning("Could not construct input stream: " + e);
                        e.printStackTrace();
                        return null;
                    }
                    if (isCancelled() || progressMonitor.isCanceled()) {
                        log.info("File open canceled after construct: " + file.getName());
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
                    stream.setFile(file);
                    if (stream instanceof AEFileInputStream s) {
                        s.marksInitialize();
                        log.fine("marksInitialize done");
                    }
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
                        viewer.endFilePlaybackOpen();
                        viewer.setPaused(false);
                        if (isCancelled() || progressMonitor.isCanceled()) {
                            log.info("Playback open canceled for " + file.getName());
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
                    log.fine("done(): fixLoggingControls");
                    viewer.fixLoggingControls();
                    try {
                        log.fine("done(): renderer.resetFrame");
                        viewer.getChip().getRenderer().resetFrame(0);
                    } catch (Exception e) {
                        log.warning("tried to reset renderer but caught " + e);
                    }
                    log.fine("done(): firing mark property changes");
                    if (!aeInputStream.isMarkInSet() && !aeInputStream.isMarkOutSet()) {
                        getSupport().firePropertyChange(AEInputStream.EVENT_MARKS_CLEARED, false, true);
                    } else {
                        if (aeInputStream.isMarkInSet()) {
                            getSupport().firePropertyChange(AEInputStream.EVENT_MARK_IN_SET, null, aeInputStream.getMarkInPosition());
                        }
                        if (aeInputStream.isMarkOutSet()) {
                            getSupport().firePropertyChange(AEInputStream.EVENT_MARK_OUT_SET, null, aeInputStream.getMarkOutPosition());
                        }
                    }
                    if (viewer.getChip().getRenderer() != null && (viewer.getChip().getRenderer() instanceof AEChipRenderer)) {
                        log.fine("done(): showRenderingModeTextOnAeViewer");
                        AEChipRenderer renderer = (AEChipRenderer) viewer.getChip().getRenderer();
                        renderer.showRenderingModeTextOnAeViewer();
                    }
                    log.fine("done(): fire EVENT_FILEOPEN");
                    getSupport().firePropertyChange(EVENT_FILEOPEN, null, file);
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
     * stops playback. If not in PLAYBACK mode, then just returns. If playing
     * back, could be waiting during sleep or during CyclicBarrier.await call in
     * CaviarViewer. In case this is the case, we send an interrupt to the the
     * ViewLoop thread to stop this waiting.
     */
    @Override
    public void stopPlayback() {
        if (viewer.getPlayMode() != AEViewer.PlayMode.PLAYBACK) {
            return;
        }

        // Resume live only if hardware is already open. Never call aemon.open() here:
        // cleanup() used to close the device then call stopPlayback(), and open() can hang
        // the EDT in native USB (NRV LibUsb.getStringDescriptorAscii while the reader thread
        // is stuck in deallocateTransfers/handleEventsTimeout).
        if (viewer.aemon != null && viewer.aemon.isOpen()) {
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

//        log.info(this+" viewer.getAePlayer().getTimesliceUs()="+viewer.getAePlayer().getTimesliceUs());
        try {
            if (!jogOccuring || (jogOccuring && jogPacketsLeft == 0)) {
                if (!viewer.aePlayer.isFlexTimeEnabled()) {
                    aeRaw = aeInputStream.readPacketByTime(viewer.getAePlayer().getTimesliceUs());
                } else {
                    aeRaw = aeInputStream.readPacketByNumber(viewer.getAePlayer().getPacketSizeEvents());
                }
            } else {
                while (jogPacketsLeft != 0) {
                    setDirectionForwards(jogPacketsLeft >= 0);
                    if (!viewer.aePlayer.isFlexTimeEnabled()) {
                        aeRaw = aeInputStream.readPacketByTime(viewer.getAePlayer().getTimesliceUs());
                    } else {
                        aeRaw = aeInputStream.readPacketByNumber(viewer.getAePlayer().getPacketSizeEvents());
                    }
                    if (jogPacketsLeft < 0) {
                        jogPacketsLeft++;
                    } else if (jogPacketsLeft > 0) {
                        jogPacketsLeft--;
                    }
                }
            }
            if (jogOccuring && jogPacketsLeft == 0) {
                jogOccuring = false;
                setDirectionForwards(true);
            }
            return aeRaw;
        } catch (EOFException e) {
//            e.printStackTrace();
            log.fine(String.format("%s: %s", player.getAEInputStream().getFile(), e.toString()));
            cancelJog();
            setDirectionForwards(true);
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignore) {
            }
            // when we get to end, we now just wraps in either direction, to make it easier to explore the ends
//                System.out.println("***********"+this+" reached EOF, calling rewind");
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
            log.warning(anyOtherException.toString() + ", returning empty AEPacketRaw");
            anyOtherException.printStackTrace();
            return new AEPacketRaw(0);
        }
    }

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
        float samplePeriodS = getTimesliceUs() * 1.0E-6F;
        float factor = fps * samplePeriodS;
//            System.out.println("fps=" + fps + " samplePeriodS=" + samplePeriodS + " factor=" + factor);
//            if ( factor < 1.2 || factor > 0.8f ){
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
