package net.sf.jaer;

import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4PlaybackAssignment;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.util.IndexFileFilter;
import net.sf.jaer.util.JaerAllowedSubclasses;
import net.sf.jaer.util.SubclassFinder;

/**
 * Synchronized playback and control of such playback is not totally
 * straightforward because of the bursty nature of AER - there are no frames to
 * synchronize on and you obviously cannot sync on event number.
 * <p>
 * This class synchronizes multiple viewer players. It assumes one is the master
 * (whichever the user controls) and coordinates viewers synchronously so that
 * all viewers can present a consistent view.
 * <p>
 * To achieve this, each viewer encapsulates its playback functionality on an
 * AEPlayer class instance that is controlled either by the Viewer GUI (the
 * user) or by JAERViewer through its own SyncPlayer.
 * <p>
 * There is a single SyncPlayer and multiple AEViwer.AEPlayers, one for each
 * viewer. The user opens an index file to play back multiple files. The files
 * each play in their own AEViewer.
 * <p>
 *
 * The Players share a common interface so this is achieved by returning the
 * correct object within AEViewer depending on whether the views are
 * synchronized.
 *
 * <p>
 * The individual threads doing the rendering in each AEViewer are barricaded by
 * the CyclicBarrier here. Each time an AEViewer asks for synchronized events
 * using getNextPacket, the call here to SyncPlayer blocks until all threads
 * asking for events have gotten them. Then rendering in each thread happens
 * normally.
 */
public class SyncPlayer extends AbstractAEPlayer implements PropertyChangeListener {

    JAERViewer outer;
    JFileChooser fileChooser;
    /**
     * Shared playhead in unwrapped microseconds. A signed int wraps at ~35.8 min
     * and the low 32 bits wrap again at ~71.6 min, which made a slider seek in a
     * long merged file look like EOF.
     */
    long playheadUs = 0;
//        boolean paused=false;
    File lastFile;
    volatile CyclicBarrier barrier;
    // used to sync up viewers for playback
    int numPlayers = 0;
    private ArrayList<AEViewer> playingViewers = new ArrayList<AEViewer>();
    private boolean enforcingCountDuration;
    /** R or EOF: rewind every playback viewer on the next barrier, not one stream alone. */
    private final AtomicBoolean rewindRequested = new AtomicBoolean();
    /**
     * Jog or step already placed {@link #playheadUs}. The in-flight barrier must
     * not add another slice on top of that jump.
     */
    private final AtomicBoolean playheadPlaced = new AtomicBoolean();
    /**
     * False after a seek past OUT, so playback can continue to the file end.
     * Stepping back to or before OUT arms it again.
     */
    private volatile boolean outLoopArmed = true;
    /** One viewer hit EOF this slice; group-rewind after the barrier. */
    private final AtomicBoolean sliceEndedPrematurely = new AtomicBoolean();
    private boolean applyingGroupRewind;
    static Preferences prefs;

    /**
     * Create new SyncPlayer
     *
     * @param viewer the viewer we actually play in
     * @param outer the JAERViewer that maintains all the AEViewers
     */
    public SyncPlayer(AEViewer viewer, JAERViewer outer) {
        super(viewer);
        prefs = viewer.prefs;
        this.outer = outer;
    }

    public boolean isChoosingFile() {
        return fileChooser != null && fileChooser.isVisible();
    }

    /**
     * this call shows a file chooser for index files: files containing
     * information on which AE data files go together. This method is only
     * called when an index file is opened.
     */
    public void openAEInputFileDialog() {
        fileChooser = new JFileChooser();
        IndexFileFilter filter = new IndexFileFilter();
        String lastFilePath = prefs.get("JAERViewer.lastFile", "");
        // get the last folder
        lastFile = new File(lastFilePath);
        fileChooser.setFileFilter(filter);
        fileChooser.setCurrentDirectory(lastFile);
        // sets the working directory of the chooser
//            boolean wasPaused=isPaused();
        setPaused(true);
        int retValue = fileChooser.showOpenDialog(null);
        if (retValue == JFileChooser.APPROVE_OPTION) {
            try {
                lastFile = fileChooser.getSelectedFile();
//                    if(lastFile!=null) recentFiles.addFile(lastFile);
                startPlayback(lastFile);
                lastFilePath = lastFile.getPath();
                prefs.put("JAERViewer.lastFile", lastFilePath);
            } catch (IOException fnf) {
                fnf.printStackTrace();
            } catch (InterruptedException ex) {
                log.warning(ex.toString());
            }
        }
        fileChooser = null;
        setPaused(false);
    }

    /**
     * @return a simple class name (no package header) parsed from a .dat
     * filename as the part before the "-"
     */
    String parseClassnameFromFilename(String filename) {
        StringBuilder className = new StringBuilder();
        int i = 0;
        while (i < filename.length() && filename.charAt(i) != '-') {
            className.append(filename.charAt(i));
            i++;
        }
        log.info("filename " + filename + " parses to chip class name " + className.toString());
        return className.toString();
    }

    synchronized void makeBarrier() {
        if (numPlayers < 2) {
            log.fine("skip CyclicBarrier; need 2+ players, have " + numPlayers);
            barrier = null;
            return;
        }
        CyclicBarrier existing = barrier;
        if (existing != null && !existing.isBroken() && existing.getParties() == numPlayers) {
            return;
        }
        log.info("making barrier for " + this);
        barrier = new CyclicBarrier(numPlayers, new Runnable() {

            public void run() {
                // Do not CyclicBarrier.reset() here: the barrier already starts the next
                // generation after this action. reset() races a fast viewer into
                // BrokenBarrierException and the views drift (seen mid-file).
                if (rewindRequested.get() || sliceEndedPrematurely.get()) {
                    // A slider interrupt can break the barrier after EOF was
                    // latched and before this action runs. If the playhead was
                    // moved back inside the file, do not rewind to the start.
                    boolean requested = rewindRequested.get();
                    boolean eof = sliceEndedPrematurely.get();
                    if (eof && !requested && !playheadPastGroupEnd()) {
                        log.info("ignoring stale sync EOF; playhead " + playheadUs
                                + " us is still inside the recording");
                        sliceEndedPrematurely.set(false);
                    } else {
                        log.info("synchronized rewind (R or EOF) at t=" + playheadUs
                                + " us requested=" + requested
                                + " eof=" + eof);
                        applyGroupRewind();
                        rewindRequested.set(false);
                        sliceEndedPrematurely.set(false);
                    }
                } else if (playheadPlaced.compareAndSet(true, false)) {
                    // Jog/step already stored the new time.
                } else if (!isPaused()) {
                    playheadUs = playheadUs + getTimesliceUs();
                }
            }
        });
    }

    /**
     * this call starts playback on the supplied index file, starting playback
     * in each viewer appropriately. If the file is not an index file, then the
     * first available viewer is called to start playback of the data file.
     *
     * @param indexFile the .index file containing the filenames to play
     */
    @Override
    public void startPlayback(File indexFile) throws IOException, InterruptedException {
        inputFile = indexFile;
        log.info("Starting synchronized playback of files in indexFile=" + indexFile);
        stopPlayback();
        // first check to make sure that index file is really an index file, in case a viewer called it
        if (!indexFile.getName().endsWith(AEDataFile.INDEX_FILE_EXTENSION) && !indexFile.getName().endsWith(AEDataFile.OLD_INDEX_FILE_EXTENSION)) {
            AEViewer v = firstViewerForFileOpen();
            if (isAedat4File(indexFile)) {
                // Muxed AEDAT-4 is one file with several EVTS streams. Drop a
                // stale barrier so the first viewer's ViewLoop does not await
                // leftover parties or toggle sync off. Chooser + spawn enable
                // sync when 2+ cameras are selected.
                resetSyncGroup();
                log.info(indexFile + " is AEDAT-4 (not an .aeidx playlist); opening with EVTS stream chooser");
                v.aePlayer.startPlayback(indexFile);
                return;
            }
            log.info(indexFile + " doesn\'t appear to be an index file pointing to a set of data files because it does't end with the correct extension (.aeidx or .index), opening it in the first viewer and setting sync enabled false");
            if (outer.isSyncEnabled()) {
                JOptionPane.showMessageDialog(v, "<html>You are opening a single data file so synchronization has been disabled<br>To reenable, use File/Synchronization enabled</html>");
//                    setSyncEnabled(false);
                outer.getToggleSyncEnabledAction().actionPerformed(null);
            }
            v.aePlayer.startPlayback(indexFile);
            return;
        }
        getPlayingViewers().clear();
        // this map will map from the data files to the viewer windows
        HashMap<File, AEViewer> map = new HashMap<File, AEViewer>();
//        setTime(0);
        BufferedReader reader;
        // files are in same folder as index file
        try {
            reader = new BufferedReader(new FileReader(indexFile));
            String filename;
            ArrayList<AEViewer> dontUseAgain = new ArrayList<AEViewer>();
            // for each line in index file, get the data file, class of chip (from filename) and find or make a viewer window for it
            while ((filename = reader.readLine()) != null) {
//                    log.info("JAERViewer.startPlayback(): trying to open AE file "+filename);
                // find chip classname from leading part of e.g. Tmpdiff128-2006-02-16T11-51-13+0100-0.dat up to '-'
//                    log.info(" filename "+filename+" indicates chip class is "+className.toString());
                // now get the data file
                File file = new File(indexFile.getParentFile(), filename);
                // this is File object for the data file
                if (!file.isFile()) {
                    JOptionPane.showMessageDialog(null, file + " from index file doesn\'t exist", "Missing data file", JOptionPane.WARNING_MESSAGE);
                    reader.close();
                    return;
                }
                // for each filename in the index file, find the right viewer window.
                String className = parseClassnameFromFilename(filename);
                AEViewer vToUse = null;
                for (AEViewer v : outer.getViewers()) {
                    // a viewer is acceptable if it hasn't been mapped yet and its chip class is the same as the parsed filename chip class or if it is a virgin AEViewer window
                    if (!dontUseAgain.contains(v) && v.getAeChipClass().getSimpleName().equals(className)) {
                        vToUse = v;
                        dontUseAgain.add(v);
                        break;
                    }

//                    //  a viewer is acceptable if its window title name starts with the same classname as the filename
//                    // or if it is a virgin window named "AEViewer"
//                    // AND if it hasn't already been assigned to some file
//                    String windowTitle = v.getTitle();
                
                ////                        log.info("...AEViewer has window title "+windowTitle);
//                    if ( ( windowTitle.startsWith(className) || windowTitle.startsWith("AEViewer") ) && !dontUseAgain.contains(v) ){
//                        vToUse = v;
//                        // always gets first one...
//                        dontUseAgain.add(v);
//                        // don't use this one again
////                            log.info("... viewer "+v.getTitle()+" can be used for "+file);
//                        break;
//                    }
                }
                // if there is no acceptable window, create a new AEViewer for this file
                if (vToUse == null) {
                    log.info("no AEViewer found for " + filename + ", making new one");
                    vToUse = outer.createViewerForPlayback(dontUseAgain.size() + 1);
                    if (vToUse == null) {
                        log.warning("viewer cap reached; skipping " + filename);
                        continue;
                    }
                    dontUseAgain.add(vToUse);
                    vToUse.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                }
                map.put(file, vToUse);
                log.info("mapped " + file + " to viewer " + vToUse);
            }
            // foreach data file
            if (reader != null) {
                reader.close();
            }
            // now make a cyclic barrier to synchronize the players
            numPlayers = map.size();
            log.info(Thread.currentThread() + " constructing barrier for " + numPlayers + " synchronized players");
            makeBarrier();
            // now for each file, start playback in the correct window
            // also set the chip class for the viewer as parsed from the filename
            for (Entry<File, AEViewer> e : map.entrySet()) {
                AEViewer v = e.getValue();
                File f = e.getKey();
                log.info("Starting playback of File " + f + " in viewer " + v.getTitle());
                String className = parseClassnameFromFilename(f.getName());
                Class chipClass = getChipClassFromSimpleName(className);
//                    AEPlayerInterface p=v.getAePlayer(); // this resolves to this play (SyncPlayer), but we want the viewer local player
                v.setAeChipClass(chipClass);
                v.aePlayer.stopPlayback();
                v.aePlayer.startPlayback(e.getKey());
                v.aePlayer.getAEInputStream().getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
                getPlayingViewers().add(v);
            }
            initTime();
            enforceSynchronizedPlaybackContract();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (AEViewer v : getPlayingViewers()) {
                v.setCursor(Cursor.getDefaultCursor());
            }
        }
        outer.setPlayBack(true);
    }

    /**
     * Open additional EVTS streams of the same AEDAT-4 in extra viewers.
     * {@code origin} is already opening {@code file} as its assigned stream.
     */
    public void startAdditionalAedat4Streams(File file, List<RecordingChipDetector.StreamHint> streams,
            AEViewer origin) {
        if (file == null || streams == null || streams.isEmpty() || origin == null) {
            return;
        }
        List<AEViewer> viewers = outer.getViewers();
        boolean[] used = new boolean[viewers.size()];
        int originIndex = viewers.indexOf(origin);
        if (originIndex >= 0) {
            used[originIndex] = true;
        }
        List<Aedat4PlaybackAssignment.ViewerSlot> slots = playbackSlots(viewers);
        List<Aedat4PlaybackAssignment.Binding> plan = Aedat4PlaybackAssignment.assign(
                streams, slots, origin.playbackChipCatalog(), used);
        startAssignedAedat4Streams(file, plan, origin);
    }

    /**
     * Open leftover bindings from {@link Aedat4PlaybackAssignment} (origin already playing).
     */
    public void startAssignedAedat4Streams(File file, List<Aedat4PlaybackAssignment.Binding> plan,
            AEViewer origin) {
        if (file == null || plan == null || plan.isEmpty() || origin == null) {
            return;
        }
        if (!outer.isSyncEnabled()) {
            log.info("enabling synchronized recording/playback for multi-stream AEDAT-4");
            outer.setSyncEnabled(true);
        }
        getPlayingViewers().clear();
        List<AEViewer> viewers = outer.getViewers();
        int originIndex = viewers.indexOf(origin);
        boolean originPlaying = origin.aePlayer != null && origin.aePlayer.getAEInputStream() != null;
        if (originPlaying) {
            getPlayingViewers().add(origin);
        }
        int deviceCount = plan.size();
        if (originPlaying && Aedat4PlaybackAssignment.bindingForViewer(plan, originIndex) == null) {
            deviceCount++;
        }
        for (Aedat4PlaybackAssignment.Binding b : plan) {
            if (!b.createNew && b.viewerIndex == originIndex) {
                continue;
            }
            AEViewer vToUse = null;
            if (!b.createNew && b.viewerIndex >= 0 && b.viewerIndex < viewers.size()) {
                vToUse = viewers.get(b.viewerIndex);
            }
            if (vToUse == null || vToUse == origin) {
                log.info("no AEViewer for AEDAT-4 stream " + b.stream.displayLabel() + ", making new one");
                vToUse = outer.createViewerForPlayback(deviceCount);
                if (vToUse == null) {
                    log.warning("viewer cap reached; skipping " + b.stream.displayLabel());
                    continue;
                }
                vToUse.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                viewers = outer.getViewers();
            }
            vToUse.ensureLivePathFinished();
            if (b.chip != null) {
                vToUse.setAeChipClass(b.chip);
            }
            vToUse.setPendingAedat4EventStreamId(b.stream.streamId);
            vToUse.setSkipAedat4Lz4Offer(true);
            try {
                vToUse.aePlayer.stopPlayback();
                vToUse.aePlayer.startPlayback(file);
                if (vToUse.aePlayer.getAEInputStream() != null) {
                    vToUse.aePlayer.getAEInputStream().getSupport()
                            .addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
                }
            } catch (IOException | InterruptedException e) {
                log.warning("opening AEDAT-4 stream " + b.stream.displayLabel() + ": " + e);
            }
            getPlayingViewers().add(vToUse);
        }
        numPlayers = getPlayingViewers().size();
        log.info("AEDAT-4 multi-stream playback: " + numPlayers + " viewers for " + file.getName());
        makeBarrier();
        initTime();
        enforceSynchronizedPlaybackContract();
        outer.setPlayBack(true);
        for (AEViewer v : getPlayingViewers()) {
            v.setCursor(Cursor.getDefaultCursor());
        }
    }

    private static List<Aedat4PlaybackAssignment.ViewerSlot> playbackSlots(List<AEViewer> viewers) {
        List<Aedat4PlaybackAssignment.ViewerSlot> slots = new ArrayList<>();
        for (int i = 0; i < viewers.size(); i++) {
            AEViewer v = viewers.get(i);
            Class<?> c = v.getAeChipClass();
            slots.add(new Aedat4PlaybackAssignment.ViewerSlot(
                    i, c == null ? null : c.getSimpleName(), v.playbackDeviceIdentity()));
        }
        return slots;
    }

    /**
     * Same-file multi-stream playback (all selected EVTS streams).
     */
    public void startPlayback(File aedat4, List<RecordingChipDetector.StreamHint> streams)
            throws IOException, InterruptedException {
        if (aedat4 == null || streams == null || streams.isEmpty()) {
            return;
        }
        stopPlayback();
        resetSyncGroup();
        if (streams.size() > 1 && !outer.isSyncEnabled()) {
            log.info("enabling synchronized recording/playback for multi-stream AEDAT-4");
            outer.setSyncEnabled(true);
        }
        List<AEViewer> viewers = outer.getViewers();
        if (viewers.isEmpty()) {
            AEViewer created = outer.createViewerForPlayback(streams.size());
            if (created == null) {
                created = new AEViewer(outer);
                created.setVisible(true);
            }
            viewers = outer.getViewers();
        }
        List<Aedat4PlaybackAssignment.Binding> plan = Aedat4PlaybackAssignment.assign(
                streams, playbackSlots(viewers), viewers.get(0).playbackChipCatalog());
        Aedat4PlaybackAssignment.Binding firstBind = plan.isEmpty() ? null : plan.get(0);
        AEViewer first = firstBind != null && !firstBind.createNew && firstBind.viewerIndex >= 0
                ? viewers.get(firstBind.viewerIndex) : viewers.get(0);
        if (firstBind != null) {
            first.setPendingAedat4EventStreamId(firstBind.stream.streamId);
            if (firstBind.chip != null) {
                first.setAeChipClass(firstBind.chip);
            }
        } else {
            first.setPendingAedat4EventStreamId(streams.get(0).streamId);
        }
        first.aePlayer.startPlayback(aedat4);
        if (streams.size() > 1) {
            startAssignedAedat4Streams(aedat4, plan, first);
        } else {
            getPlayingViewers().clear();
            getPlayingViewers().add(first);
            numPlayers = 1;
            makeBarrier();
            initTime();
            outer.setPlayBack(true);
        }
    }

    private static boolean isAedat4File(File f) {
        if (f == null || f.getName() == null) {
            return false;
        }
        return f.getName().toLowerCase().endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4);
    }

    /** Prefer a non-WAITING window so the EVTS chooser is not shown on an idle viewer. */
    private AEViewer firstViewerForFileOpen() {
        List<AEViewer> list = outer.getViewers();
        if (list == null || list.isEmpty()) {
            return new AEViewer(outer);
        }
        for (AEViewer v : list) {
            if (v.getPlayMode() != AEViewer.PlayMode.WAITING) {
                return v;
            }
        }
        return list.get(0);
    }

    /**
     * stops playback on all players
     */
    public void stopPlayback() {
        log.info(Thread.currentThread() + " stopping playback");
        for (AEViewer v : outer.getViewers()) {
            v.aePlayer.stopPlayback();
        }
        resetSyncGroup();
        outer.setPlayBack(false);
    }

    /** Drop CyclicBarrier and playing-viewer list so the next open cannot await stale parties. */
    private void resetSyncGroup() {
        getPlayingViewers().clear();
        numPlayers = 0;
        barrier = null;
    }

    // iniitalizes time pointer for all viewers by getting first timestep for each viewer's ae input stream
    // and setting global time to minimum value
    void initTime() {
        long minTime = Long.MAX_VALUE;
        for (AEViewer v : outer.getViewers()) {
            try {
                AEFileInputStreamInterface s = v.aePlayer.getAEInputStream();
                if (s == null) {
                    continue;
                }
                long t = s.getFirstTimestampUs();
                if (t < minTime) {
                    minTime = t;
                }
            } catch (NullPointerException e) {
                log.fine("skip viewer without stream while initializing sync time: " + v);
            }
        }
        log.info("JAERViewer.SyncPlayer.initialized time min value found: " + minTime);
        if (minTime == Long.MAX_VALUE) {
            log.fine("initTime: no streams ready yet");
            return;
        }
        setTimeUs(minTime);
    }

    /**
     * rewinds all players. While ViewLoops are running, only set a flag so the
     * CyclicBarrier action rewinds every stream together. Immediate rewind
     * while paused (no grab in flight).
     */
    public void rewind() {
        rewindRequested.set(true);
        if (isPaused()) {
            applyGroupRewind();
            rewindRequested.set(false);
        }
    }

    /**
     * Local rewind of every playback stream and shared playhead. Called from
     * the barrier action or when paused.
     */
    void applyGroupRewind() {
        applyingGroupRewind = true;
        try {
            List<AEViewer> group = syncGroup();
            long[] inUs = new long[group.size()];
            boolean[] inSet = new boolean[group.size()];
            long fallback = Long.MAX_VALUE;
            for (int i = 0; i < group.size(); i++) {
                AEViewer v = group.get(i);
                if (v.aePlayer != null) {
                    v.aePlayer.rewindStreamOnly();
                }
                AEFileInputStreamInterface s = fileStream(v);
                if (s == null) {
                    inUs[i] = Long.MAX_VALUE;
                    continue;
                }
                long start = s.getFirstTimestampUs();
                if (start < fallback) {
                    fallback = start;
                }
                if (s.isMarkInSet()) {
                    long t = s.timestampUsAt(s.getMarkInPosition());
                    if (t < 0) {
                        t = s.getPositionTimestampUs();
                    }
                    inUs[i] = t;
                    inSet[i] = true;
                }
            }
            if (fallback == Long.MAX_VALUE) {
                fallback = 0;
            }
            // Shared playhead must follow IN. initTime() is the file start, and the
            // next slice would seek every viewer back there.
            playheadUs = syncedRewindUs(inUs, inSet, fallback);
            outLoopArmed = true;
        } finally {
            applyingGroupRewind = false;
        }
    }

    /** One synced viewer reached EOF; rewind the group after this barrier. */
    public void noteSliceEndedPrematurely() {
        sliceEndedPrematurely.set(true);
    }

    /**
     * pauses all players
     */
    @Override
    public void pause() {
        setPaused(true);
    }

    /**
     * resumes all players
     */
    @Override
    public void resume() {
        setPaused(false);
    }
    static final int SYNC_PLAYER_TIMEOUT_SEC = 3;

    /**
     * Returns next packet of AE data to the caller, which is a particular
     * AEPlayer owned by an AEViewer. getNextPacket is called via the ViewLoop
     * run() loop thread of that AEViewer. The packet is synchronized in event
     * time if synchronized playback is enabled.
     *
     * @return a raw packet of events
     */
    public AEPacketRaw getNextPacket(AbstractAEPlayer player) {
        // each player will call in their own thread the getNextPacket and
        // then return the ae to be rendered here,
        // AFTER the blocking await call that synchronizes them.
        // if the viewer is paused during the await call, then we may get a timeout here.
        // therefore we do not stop playback if the viewers are paused, only very slowly step along

        // We first set all the player's currentStartTimestamp to the same values, based on all the player's values.
        // Since currentStartTimestamp is set by each player to whatever time it happens to end at
        // (which is not nessarily the last timestamp plust the delta time), we have to keep synchrnozing the players
        if (numPlayers < 2 || getPlayingViewers().size() < 2) {
            if (outer.isSyncEnabled()) {
                rebuildPlayingViewersFromOpenPlayback();
            }
            if (numPlayers < 2 || getPlayingViewers().size() < 2) {
                return player.getNextPacket(player);
            }
        }
        // Shared CountDuration playhead. Do not take max(getMostRecentTimestamp()):
        // AEDAT-4 readPacketByTime walks file position, and a capped high-rate
        // slice on one camera then pulled the other to a different time.
        if (rewindRequested.get() || playheadPastGroupEnd()) {
            if (!rewindRequested.get() && playheadPastGroupEnd()) {
                if (groupRepeatEnabled()) {
                    sliceEndedPrematurely.set(true);
                } else {
                    setPaused(true);
                }
            }
            AEPacketRaw empty = new AEPacketRaw(0);
            awaitSyncBarrier();
            return empty;
        }
        player.seekPlayheadUs(playheadUs);
        AEPacketRaw ae = player.getNextPacket(player);
        awaitSyncBarrier();
        return ae;
    }

    /**
     * AEDAT-4 {@code setPositionFromTimestamp} clamps to the last event, so
     * {@code readPacketByTime} never throws EOF and the playhead sits on the
     * last packet. Repeat must be decided here from the shared time, not from
     * a per-stream auto-rewind (that seeks back to the end on the next slice).
     * <p>
     * The end is the unwrapped stream time, or the latest OUT when that loop is
     * armed. Equality still shows the last slice; the next timeslice steps past
     * it. Comparing low 32 bits made a seek past ~71.6 min look like EOF
     * whenever those bits were numerically smaller than the scrub time.
     */
    private boolean playheadPastGroupEnd() {
        long t = playheadUs;
        // Repeat when the shared playhead passes the longest stream. A shorter
        // camera stays clamped on its last event so the longer file can be scrubbed.
        Long longest = null;
        List<AEViewer> group = getPlayingViewers();
        long[] outUs = new long[group.size()];
        boolean[] outSet = new boolean[group.size()];
        int i = 0;
        for (AEViewer v : group) {
            AEFileInputStreamInterface s = v.getAeFileInputStream();
            if (s == null) {
                i++;
                continue;
            }
            if (s instanceof Aedat4FileInputStream a4 && !a4.hasEventPackets()) {
                i++;
                continue;
            }
            long last = s.getLastTimestampUs();
            if (longest == null || last > longest) {
                longest = last;
            }
            if (s.isMarkOutSet()) {
                long ot = s.timestampUsAt(s.getMarkOutPosition());
                if (ot >= 0) {
                    outUs[i] = ot;
                    outSet[i] = true;
                }
            }
            i++;
        }
        if (longest == null) {
            return false;
        }
        long end = longest;
        Long out = syncedOutLimitUs(outUs, outSet);
        if (out != null) {
            if (t <= out) {
                outLoopArmed = true;
            }
            if (outLoopArmed && out < end) {
                end = out;
            }
        }
        return playheadPastEnd(t, end);
    }

    /**
     * Earliest set IN, so a camera whose file starts later does not skip the
     * section. {@code fallbackUs} is the file start when no IN is set.
     */
    static long syncedRewindUs(long[] inUs, boolean[] set, long fallbackUs) {
        long min = Long.MAX_VALUE;
        boolean any = false;
        int n = inUs == null ? 0 : inUs.length;
        for (int i = 0; i < n; i++) {
            if (set != null && (i >= set.length || !set[i])) {
                continue;
            }
            if (inUs[i] < min) {
                min = inUs[i];
                any = true;
            }
        }
        return any ? min : fallbackUs;
    }

    /**
     * Latest set OUT. A camera that ended before the mark clamps its own OUT
     * earlier; that must not cut the longer file.
     * Null when no OUT timestamp is known.
     */
    static Long syncedOutLimitUs(long[] outUs, boolean[] set) {
        long max = Long.MIN_VALUE;
        boolean any = false;
        int n = outUs == null ? 0 : outUs.length;
        for (int i = 0; i < n; i++) {
            if (set != null && (i >= set.length || !set[i])) {
                continue;
            }
            if (outUs[i] > max) {
                max = outUs[i];
                any = true;
            }
        }
        return any ? Long.valueOf(max) : null;
    }

    /** Earliest unwrapped timestamp among playing streams, or the current playhead. */
    public long groupStartUs() {
        long min = Long.MAX_VALUE;
        for (AEViewer v : syncGroup()) {
            AEFileInputStreamInterface s = fileStream(v);
            if (s == null) {
                continue;
            }
            long t = s.getFirstTimestampUs();
            if (t < min) {
                min = t;
            }
        }
        return min == Long.MAX_VALUE ? playheadUs : min;
    }

    /** Latest unwrapped timestamp among playing streams, or the current playhead. */
    public long groupEndUs() {
        long max = Long.MIN_VALUE;
        for (AEViewer v : syncGroup()) {
            AEFileInputStreamInterface s = fileStream(v);
            if (s == null) {
                continue;
            }
            long t = s.getLastTimestampUs();
            if (t > max) {
                max = t;
            }
        }
        return max == Long.MIN_VALUE ? playheadUs : max;
    }

    /**
     * Slider fraction 0–1 on the shared axis (earliest start through latest end).
     * Each viewer's own duration is not the axis: a 2 h stream beside a 4 h
     * stream used to put the short handle at the end when the long one was halfway.
     */
    public float sliderFractionForPlayhead() {
        return fractionOnSpan(playheadUs, groupStartUs(), groupEndUs());
    }

    /** Shared-axis timestamp for a slider fraction in 0–1. */
    public long timestampForSliderFraction(float frac) {
        if (frac < 0f) {
            frac = 0f;
        } else if (frac > 1f) {
            frac = 1f;
        }
        long start = groupStartUs();
        long span = groupEndUs() - start;
        if (span <= 0L) {
            return start;
        }
        return start + (long) ((double) frac * span);
    }

    /**
     * Where {@code stream}'s own timeline sits on the shared slider, as fractions
     * of the track. A camera that ends at half the group duration returns {@code 0.5}.
     */
    public float streamEndFraction(AEFileInputStreamInterface stream) {
        return fractionOnGroupAxis(stream == null ? playheadUs : stream.getLastTimestampUs());
    }

    public float streamStartFraction(AEFileInputStreamInterface stream) {
        return fractionOnGroupAxis(stream == null ? playheadUs : stream.getFirstTimestampUs());
    }

    private float fractionOnGroupAxis(long timeUs) {
        return fractionOnSpan(timeUs, groupStartUs(), groupEndUs());
    }

    /** Fraction of {@code [startUs, endUs]} at {@code timeUs}, clamped to 0–1. */
    static float fractionOnSpan(long timeUs, long startUs, long endUs) {
        long span = endUs - startUs;
        if (span <= 0L) {
            return 0f;
        }
        double f = (timeUs - startUs) / (double) span;
        if (f < 0d) {
            return 0f;
        }
        if (f > 1d) {
            return 1f;
        }
        return (float) f;
    }

    private static AEFileInputStreamInterface fileStream(AEViewer v) {
        if (v == null || v.aePlayer == null) {
            return null;
        }
        return v.aePlayer.getAEInputStream();
    }

    /** Re-place every synced sparkline on the shared time axis. */
    public void refreshSyncedSliderAxes() {
        if (getPlayingViewers().size() < 2 && syncGroup().size() < 2) {
            return;
        }
        for (AEViewer v : syncGroup()) {
            if (v.getPlayerControls() == null) {
                continue;
            }
            SwingUtilities.invokeLater(v.getPlayerControls()::applySyncedSparklineSpan);
        }
    }

    /** Regression: a mid-file scrub must not count as past an unwrapped end. */
    static boolean playheadPastEnd(long playheadUs, long endUs) {
        return playheadUs > endUs;
    }

    private boolean groupRepeatEnabled() {
        for (AEViewer v : getPlayingViewers()) {
            if (v.aePlayer != null && v.aePlayer.isRepeat()) {
                return true;
            }
        }
        return isRepeat();
    }

    private void awaitSyncBarrier() {
        try {
            if (barrier == null) {
                makeBarrier();
            }
            if (barrier == null) {
                return;
            }
            barrier.await();
        } catch (InterruptedException e) {
            // Slider single-step interrupts a view thread blocked in await().
            // Restoring the flag breaks the next barrier immediately.
            log.fine("sync barrier wait interrupted; remaking so the next slice can join");
            makeBarrier();
        } catch (BrokenBarrierException e) {
            log.warning("sync barrier broken; remaking so the next slice can join");
            makeBarrier();
        }
    }

    public float getFractionalPosition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearMarks() {
        for (AEViewer v : markGroup()) {
            if (v.aePlayer != null) {
                v.aePlayer.clearMarksHere();
            }
        }
        outLoopArmed = true;
    }

    /** Playback viewers that should share IN, OUT, and markers. */
    private List<AEViewer> markGroup() {
        if (getPlayingViewers().size() < 2 && outer.isSyncEnabled()) {
            rebuildPlayingViewersFromOpenPlayback();
        }
        List<AEViewer> group = syncGroup();
        ArrayList<AEViewer> playing = new ArrayList<>();
        for (AEViewer v : group) {
            if (v.getPlayMode() == AEViewer.PlayMode.PLAYBACK && fileStream(v) != null) {
                playing.add(v);
            }
        }
        return playing.isEmpty() ? group : playing;
    }

    /** A seek past OUT plays through to the file end. A seek back to OUT re-arms it. */
    private void noteOutLoopForSeek(long timeUs) {
        Long out = groupOutLimitUs();
        outLoopArmed = out == null || timeUs <= out;
    }

    private Long groupOutLimitUs() {
        List<AEViewer> group = markGroup();
        long[] outUs = new long[group.size()];
        boolean[] outSet = new boolean[group.size()];
        for (int i = 0; i < group.size(); i++) {
            AEFileInputStreamInterface s = fileStream(group.get(i));
            if (s == null || !s.isMarkOutSet()) {
                continue;
            }
            long t = s.timestampUsAt(s.getMarkOutPosition());
            if (t < 0) {
                continue;
            }
            outUs[i] = t;
            outSet[i] = true;
        }
        return syncedOutLimitUs(outUs, outSet);
    }

    private static long markerTimeUs(AEFileInputStreamInterface s) {
        long t = s.timestampUsAt(s.position());
        return t < 0 ? s.getPositionTimestampUs() : t;
    }

    /** j on one viewer: every stream seeks to that marker's time. */
    public boolean jumpGroupToPreviousMarker(AEViewer origin) {
        AEFileInputStreamInterface s = fileStream(origin);
        if (s == null || !s.jumpToPrevMarker()) {
            return false;
        }
        seekAllToTimestamp(markerTimeUs(s), origin);
        return true;
    }

    /** k on one viewer: every stream seeks to that marker's time. */
    public boolean jumpGroupToNextMarker(AEViewer origin) {
        AEFileInputStreamInterface s = fileStream(origin);
        if (s == null || !s.jumpToNextMarker()) {
            return false;
        }
        seekAllToTimestamp(markerTimeUs(s), origin);
        return true;
    }

    public long position(AEFileInputStreamInterface stream) {
        return stream.position();
    }

    public int position() {
        throw new UnsupportedOperationException();
    }

    public void position(int event, AEFileInputStreamInterface stream) {
        stream.position(event);
    }

    public AEPacketRaw readPacketByNumber(int n) throws IOException {
        throw new UnsupportedOperationException();
    }

    public AEPacketRaw readPacketByNumber(int n, AEFileInputStreamInterface stream) throws IOException {
        return stream.readPacketByNumber(n);
    }

    public AEPacketRaw readPacketByTime(int dt) throws IOException {
        throw new UnsupportedOperationException();
    }

    public AEPacketRaw readPacketByTime(int dt, AEFileInputStreamInterface stream) throws IOException {
        return stream.readPacketByTime(dt);
    }

    public long size(AEFileInputStream stream) {
        return stream.size();
    }

    public long size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFractionalPosition(float frac) {
        for (AEViewer v : outer.getViewers()) {
            v.aePlayer.setFractionalPosition(frac);
        }
    }

    @Override
    public void setTimesliceUs(int samplePeriodUs) {
        super.setTimesliceUs(samplePeriodUs);
        for (AEViewer v : getPlayingViewers()) {
            v.aePlayer.applySharedTimesliceUs(samplePeriodUs);
        }
    }

    @Override
    public void setPlaybackMode(PlaybackMode playbackMode) {
        if (playbackMode == PlaybackMode.FixedPacketSize || playbackMode == PlaybackMode.AreaEventCount) {
            log.info("synchronized playback requires CountDuration; ignoring " + playbackMode);
            playbackMode = PlaybackMode.FixedTimeSlice;
            if (viewer != null) {
                viewer.showActionText("CountDuration required for synchronized playback");
            }
        }
        super.setPlaybackMode(playbackMode);
        for (AEViewer v : syncGroup()) {
            if (v.aePlayer != null && v.aePlayer.getPlaybackMode() != playbackMode) {
                v.aePlayer.setPlaybackMode(playbackMode);
            }
        }
    }

    @Override
    public void setPacketSizeEvents(int packetSizeEvents) {
        super.setPacketSizeEvents(packetSizeEvents);
        for (AEViewer v : getPlayingViewers()) {
            if (v.aePlayer != null) {
                v.aePlayer.setPacketSizeEvents(packetSizeEvents);
            }
        }
    }

    /**
     * Sets all viewers to the same time.
     *
     * @param time current playback time relative to start in us
     */
    @Override
    public void setTime(int time) {
        setTimeUs(time & 0xffffffffL);
    }

    /**
     * Sets the shared playhead in unwrapped µs and each viewer's start timestamp.
     */
    public void setTimeUs(long timeUs) {
        playheadUs = timeUs;
        try {
            for (AEViewer v : getPlayingViewers()) {
                if (v.aePlayer.getAEInputStream() != null) {
                    v.aePlayer.setTime((int) timeUs);
                }
            }
        } catch (ConcurrentModificationException e) {
            log.warning("couldn\'t set time on a viewer because of exception " + e.getMessage());
        }
    }

    /**
     * Slider / jog: move every playing viewer to the packet nearest
     * {@code timeUs}. {@code origin} is already at that time.
     */
    public void seekAllToTimestamp(int timeUs, AEViewer origin) {
        seekAllToTimestamp(timeUs & 0xffffffffL, origin);
    }

    /**
     * Slider / jog: move every playing viewer to the packet nearest
     * {@code timeUs}. {@code origin} is already at that time.
     * {@code timeUs} is unwrapped microseconds, not a 32-bit timestamp.
     */
    public void seekAllToTimestamp(long timeUs, AEViewer origin) {
        playheadUs = timeUs;
        noteOutLoopForSeek(timeUs);
        if (!playheadPastGroupEnd()) {
            sliceEndedPrematurely.set(false);
        }
        List<AEViewer> group = getPlayingViewers().isEmpty() ? outer.getViewers() : getPlayingViewers();
        boolean resetFilters = origin == null || origin.getPlayerControls() == null
                || !origin.getPlayerControls().isSliderBeingAdjusted();
        for (AEViewer v : group) {
            if (v == origin || v.getPlayMode() != AEViewer.PlayMode.PLAYBACK) {
                continue;
            }
            v.aePlayer.seekToTimestampUs(timeUs, resetFilters);
        }
    }

    /**
     * @return current playback time relative to start in us
     */
    @Override
    public int getTime() {
        return (int) playheadUs;
    }

    /**
     * will throw UnsupportedOperationException since the correct call is to
     * getNextPacket(player).
     */
    @Override
    public AEPacketRaw getNextPacket() {
        throw new UnsupportedOperationException();
    }

    /**
     * always returns null, because this is a sync player for multiple viewers
     */
    public AEFileInputStream getAEInputStream() {
        return null;
    }

    /**
     * JAERViewer gets PropertyChangeEvent from the AEPlayer in the AEViewers.
     * This method presently only logs this event.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(AEInputStream.EVENT_REWOUND)) {
            for (AEViewer v : outer.getViewers()) {
                if (v.getChip() != null && v.getChip().getRenderer() != null) {
                    v.getChip().getRenderer().resetFrame(v.getChip().getRenderer().getGrayValue());
                }
            }
            if (!applyingGroupRewind) {
                rewindRequested.set(true);
            }
            log.info("rewind PropertyChangeEvent received by " + this + " from " + evt.getSource()
                    + " groupRewind=" + applyingGroupRewind);
        }
    }

    /**
     * One slice in the current direction, then one rendered frame on every viewer.
     * {@code .} and {@code ,} come here. The shared playhead must move; a local
     * packet read is seeked back to this time on the next slice.
     */
    @Override
    public void doSingleStep() {
        long slice = getTimesliceUs();
        if (slice == 0) {
            slice = 1;
        }
        placePlayhead(clampPlayhead(playheadUs + slice));
        refreshPausedFrame();
    }

    /** Render the current playhead without moving it. Slider preview uses this. */
    public void refreshPausedFrame() {
        setPaused(true);
        for (AEViewer v : outer.getViewers()) {
            v.doSingleStep();
        }
    }

    @Override
    public void jogForwards(int packets) {
        movePlayheadSlices(packets);
    }

    @Override
    public void jogBackwards(int packets) {
        movePlayheadSlices(-packets);
    }

    /** Positive moves later in the file. CountDuration slices, which sync playback uses. */
    private void movePlayheadSlices(int signedSlices) {
        if (signedSlices == 0) {
            return;
        }
        long slice = Math.abs((long) getTimesliceUs());
        if (slice < 1L) {
            slice = 1L;
        }
        placePlayhead(clampPlayhead(playheadUs + signedSlices * slice));
        if (isPaused()) {
            refreshPausedFrame();
        }
    }

    private void placePlayhead(long timeUs) {
        seekAllToTimestamp(timeUs, null);
        CyclicBarrier waiting = barrier;
        if (!isPaused() && waiting != null && waiting.getNumberWaiting() > 0) {
            playheadPlaced.set(true);
        }
    }

    /** Keep a jog or step inside the recording, and inside OUT while that loop is armed. */
    private long clampPlayhead(long target) {
        long start = groupStartUs();
        if (target < start) {
            target = start;
        }
        long end = groupEndUs();
        Long out = groupOutLimitUs();
        if (outLoopArmed && out != null && out < end) {
            end = out;
        }
        if (target > end) {
            target = end;
        }
        return target;
    }

    public void adjustTimesliceForRealtimePlayback() {
    }

    @Override
    public void setDoSingleStepEnabled(boolean b) {
    }

    /**
     * Returns the list of viewers involved in this playback.
     *
     * @return the playingViewers
     */
    public ArrayList<AEViewer> getPlayingViewers() {
        return playingViewers;
    }

    private List<AEViewer> syncGroup() {
        return getPlayingViewers().isEmpty() ? outer.getViewers() : getPlayingViewers();
    }

    /**
     * CountDuration on every playback viewer, shared timeslice, shared target
     * FPS. ConstantCount / AreaEventCount would advance each camera by a
     * different event-time window per barrier cycle.
     */
    public void enforceSynchronizedPlaybackContract() {
        List<AEViewer> group = new ArrayList<>();
        for (AEViewer v : syncGroup()) {
            if (v.getPlayMode() == AEViewer.PlayMode.PLAYBACK
                    || getPlayingViewers().contains(v)) {
                group.add(v);
            }
        }
        if (group.size() < 2 && getPlayingViewers().size() < 2) {
            return;
        }
        if (enforcingCountDuration) {
            return;
        }
        enforcingCountDuration = true;
        try {
            int sliceUs = getTimesliceUs();
            if (sliceUs == 0) {
                sliceUs = 20000;
            }
            for (AEViewer v : group) {
                if (v.aePlayer == null) {
                    continue;
                }
                if (v.aePlayer.getAEInputStream() != null) {
                    // Per-stream repeat rewinds one camera, then the next seekPlayhead
                    // pulls it back to the old shared time. SyncPlayer loops the group.
                    v.aePlayer.getAEInputStream().setRepeat(false);
                }
                AbstractAEPlayer.PlaybackMode mode = v.aePlayer.getPlaybackMode();
                if (mode == AbstractAEPlayer.PlaybackMode.FixedPacketSize
                        || mode == AbstractAEPlayer.PlaybackMode.AreaEventCount) {
                    v.aePlayer.setFixedTimesliceEnabled();
                }
                if (sliceUs == 20000 && v.aePlayer.getTimesliceUs() != 0) {
                    sliceUs = v.aePlayer.getTimesliceUs();
                }
            }
            setTimesliceUs(sliceUs);
            super.setPlaybackMode(AbstractAEPlayer.PlaybackMode.FixedTimeSlice);
            equalizeDesiredFrameRate(group);
            for (AEViewer v : group) {
                SwingUtilities.invokeLater(v::refreshPlaybackAccumulationControls);
            }
            log.info("synchronized playback: CountDuration timesliceUs=" + sliceUs
                    + " viewers=" + group.size());
            refreshSyncedSliderAxes();
        } finally {
            enforcingCountDuration = false;
        }
    }

    /**
     * LEFT/RIGHT on one window used to change only that chip's FrameRater, so
     * slices painted at different wall-clock rates. Share the slowest target.
     */
    void equalizeDesiredFrameRate(List<AEViewer> group) {
        int minFps = Integer.MAX_VALUE;
        for (AEViewer v : group) {
            int fps = v.getDesiredFrameRate();
            if (fps > 0 && fps < minFps) {
                minFps = fps;
            }
        }
        if (minFps == Integer.MAX_VALUE || minFps < 1) {
            return;
        }
        outer.setDesiredFrameRateForSyncedPlayback(minFps);
    }

    /**
     * File → Synchronize with already-open files never filled
     * {@code playingViewers}; muxed AEDAT-4 {@link #resetSyncGroup()} can also
     * leave a stale empty list. Rebuild from PLAYBACK streams so the barrier
     * actually joins.
     */
    synchronized void rebuildPlayingViewersFromOpenPlayback() {
        ArrayList<AEViewer> found = new ArrayList<>();
        for (AEViewer v : outer.getViewers()) {
            if (v.getPlayMode() == AEViewer.PlayMode.PLAYBACK
                    && v.aePlayer != null && v.aePlayer.getAEInputStream() != null) {
                found.add(v);
            }
        }
        if (found.size() < 2) {
            return;
        }
        if (found.size() == playingViewers.size() && playingViewers.containsAll(found)
                && numPlayers == found.size() && barrier != null) {
            return;
        }
        playingViewers.clear();
        playingViewers.addAll(found);
        numPlayers = playingViewers.size();
        log.info("rebuilt sync group: " + numPlayers + " playback viewers");
        makeBarrier();
        enforceSynchronizedPlaybackContract();
    }
    private static List<String> chipClassNames; // cache expensive search for all AEChip classes

    /**
     * Returns AEChip class from simple name. If chip classes have not yet been
     * cached, waits until they exist.
     *
     * @param className, e.g. DVS128.
     * @return class for AEChip.
     */
    private Class getChipClassFromSimpleName(String className) {
        Class deviceClass = null;
        if (getChipClassNames() == null) {
            cacheChipClassNames();
        }
        for (String s : chipClassNames) {
            int ind = s.lastIndexOf('.');
            String s2 = s.substring(ind + 1);
            if (s2.equals(className)) {
                try {
                    deviceClass = JaerAllowedSubclasses.load(s, net.sf.jaer.chip.AEChip.class);
                    log.info("found class " + deviceClass + " for className " + className);
                    break;
                } catch (ClassNotFoundException e) {
                    log.warning(e.getMessage());
                }
            }
        }
        if (deviceClass == null) {
            log.warning("no chip class for chip className=" + className);
        }
        return deviceClass;
    }

    private void cacheChipClassNames() {
        chipClassNames = SubclassFinder.findSubclassesOf(net.sf.jaer.chip.AEChip.class.getName());
    }

    public static List<String> getChipClassNames() {
        return chipClassNames;
    }

    /**
     * Sets IN on every playback viewer at the shared playhead.
     *
     * @return always 0; each file stores its own event index for that time.
     */
    @Override
    public long setMarkIn() {
        long t = playheadUs;
        for (AEViewer v : markGroup()) {
            if (v.aePlayer != null) {
                v.aePlayer.setMarkInAtTimestamp(t);
            }
        }
        return 0;
    }

    /**
     * Sets OUT on every playback viewer at the shared playhead.
     * The next slice past that time rewinds the group to IN.
     *
     * @return always 0; each file stores its own event index for that time.
     */
    @Override
    public long setMarkOut() {
        long t = playheadUs;
        for (AEViewer v : markGroup()) {
            if (v.aePlayer != null) {
                v.aePlayer.setMarkOutAtTimestamp(t);
            }
        }
        outLoopArmed = true;
        return 0;
    }

    @Override
    public boolean toggleMarker() {
        long t = playheadUs;
        int reactionMs = 0;
        for (AEViewer v : markGroup()) {
            if (v.aePlayer != null) {
                reactionMs = v.aePlayer.getMarkerReactionTimeMs();
                break;
            }
        }
        if (!isPaused() && getTimesliceUs() > 0 && reactionMs > 0) {
            t -= reactionMs * 1000L;
            long start = groupStartUs();
            if (t < start) {
                t = start;
            }
        }
        boolean added = false;
        for (AEViewer v : markGroup()) {
            if (v.aePlayer != null) {
                added = v.aePlayer.toggleMarkerAtTimestamp(t);
            }
        }
        return added;
    }
}
