package net.sf.jaer.graphics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import net.sf.jaer.JAERViewer;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.TypedDataPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStream.Marks;
import net.sf.jaer.eventio.aedat4.Aedat4CameraTrack;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.eventio.aedat4.Aedat4LiveActivityHistogram;
import net.sf.jaer.eventprocessing.AcquisitionCycle;
import net.sf.jaer.eventprocessing.FilterChain;

/**
 * Cable-box timeshift for one viewer. Recording stays in {@link AEViewer.PlayMode#LIVE}.
 * The scrubber is the activity histogram; dragging behind the live edge plays the
 * file written so far. Back to live returns the view to the camera without closing
 * the writer.
 */
final class LiveTimeshift {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final float LIVE_EDGE_FRACTION = 0.995f;

    private final AEViewer viewer;
    private final Aedat4LiveActivityHistogram histogram = new Aedat4LiveActivityHistogram();
    private final Object pumpLock = new Object();

    private volatile boolean scrubberShowing;
    private volatile boolean viewingFile;
    private volatile boolean yieldsUsb;
    private volatile boolean viewLoopInUsbGrab;
    private volatile boolean pumpStartedHere;
    private volatile boolean stopPump;
    private Thread pumpThread;
    private ScheduledExecutorService histogramTimer;
    private Aedat4FileInputStream reader;
    private long lastPumpWarnMs;
    private final Object markLock = new Object();
    private final TreeSet<Long> markers = new TreeSet<>();
    private long markIn;
    private long markOut = Long.MAX_VALUE;
    private boolean outFollowsEnd = true;
    private int lastMarkerOffsetMs;
    private File markedFile;

    LiveTimeshift(AEViewer viewer) {
        this.viewer = viewer;
    }

    boolean viewLoopYieldsUsb() {
        return yieldsUsb;
    }

    boolean isViewingFile() {
        return viewingFile;
    }

    boolean isScrubberShowing() {
        return scrubberShowing;
    }

    void noteUsbGrab(boolean inside) {
        viewLoopInUsbGrab = inside;
    }

    void onRecordingStarted() {
        if (!eligible()) {
            hideScrubber();
            return;
        }
        Aedat4FileOutputStream writer = viewer.getAedat4RecordingOutputStream();
        resetMarks();
        markedFile = viewer.getRecordingFile();
        histogram.reset(writer.getBaseUnixUs());
        clearSparkline();
        SwingUtilities.invokeLater(this::clearMarkWidgets);
        showScrubber(false);
        startHistogramTimer();
    }

    void onRecordingStopped() {
        leaveTimeshift();
        persistMarks(markedFile);
        resetMarks();
        markedFile = null;
        SwingUtilities.invokeLater(this::clearMarkWidgets);
        stopHistogramTimer();
        histogram.reset(0);
        hideScrubber();
    }

    /** New cassette: histogram and reader follow the new writer. */
    void onCassetteRolled() {
        if (!scrubberShowing || !eligible()) {
            return;
        }
        Aedat4FileOutputStream writer = viewer.getAedat4RecordingOutputStream();
        if (writer == null) {
            return;
        }
        boolean resume = viewingFile;
        File previous = markedFile;
        if (resume) {
            closeReader();
        }
        persistMarks(previous);
        resetMarks();
        markedFile = viewer.getRecordingFile();
        SwingUtilities.invokeLater(this::clearMarkWidgets);
        histogram.reset(writer.getBaseUnixUs());
        clearSparkline();
        if (resume) {
            boolean wasPaused = viewer.getAePlayer().isPaused();
            try {
                openReader(0f);
            } catch (IOException e) {
                log.log(Level.WARNING, "timeshift rebind after VCR roll failed: " + e, e);
                backToLive();
                return;
            }
            if (wasPaused) {
                viewer.getAePlayer().setPaused(true);
            }
        }
    }

    void backToLive() {
        // Join before ViewLoop takes USB back. Do not interrupt: this thread writes the file.
        stopPumpIfStartedHere(true);
        closeReader();
        viewingFile = false;
        yieldsUsb = false;
        showScrubber(false);
        SwingUtilities.invokeLater(() -> {
            pinThumb();
            paintStoredMarks();
        });
    }

    /**
     * @return true when the slider event was consumed (pinned to the live edge,
     * or timeshift was opened and seeked)
     */
    boolean offerSlider(float fraction) {
        if (!scrubberShowing || viewingFile || viewer.getPlayMode() != AEViewer.PlayMode.LIVE) {
            return false;
        }
        if (fraction >= LIVE_EDGE_FRACTION) {
            viewer.getPlayerControls().pinSliderToEnd();
            return true;
        }
        enterTimeshift(fraction);
        return true;
    }

    /** Rewind key while the scrubber is showing and the view is still the camera. */
    boolean offerRewind() {
        if (!scrubberShowing || viewingFile) {
            return false;
        }
        enterTimeshift(0f);
        return viewingFile;
    }

    /** Step or jog backward opens the reader just behind the live edge. */
    boolean offerStepBack() {
        if (!scrubberShowing || viewingFile) {
            return false;
        }
        enterTimeshift(Math.max(0f, LIVE_EDGE_FRACTION - 0.02f));
        return viewingFile;
    }

    private void enterTimeshift(float fraction) {
        if (viewingFile || !eligible()) {
            return;
        }
        yieldsUsb = true;
        long deadline = System.currentTimeMillis() + 2000;
        while (viewLoopInUsbGrab && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        try {
            openReader(fraction);
        } catch (IOException e) {
            log.log(Level.WARNING, "timeshift open failed: " + e, e);
            yieldsUsb = false;
            closeReader();
            return;
        }
        viewingFile = true;
        startPumpIfNeeded();
        showScrubber(false);
        log.info("timeshift playback from fraction " + fraction + " file=" + viewer.getRecordingFile());
    }

    private void openReader(float fraction) throws IOException {
        Aedat4FileOutputStream writer = viewer.getAedat4RecordingOutputStream();
        if (writer == null || viewer.getRecordingFile() == null) {
            throw new IOException("no AEDAT-4 recording");
        }
        Aedat4FileInputStream in = Aedat4FileInputStream.openLiveTail(
                viewer.getRecordingFile(), viewer.getChip(), writer.copyLiveCatalog(), writer.getBaseUnixUs());
        in.setLiveCatalogSource(() -> {
            Aedat4FileOutputStream w = viewer.getAedat4RecordingOutputStream();
            if (w == null) {
                return List.of();
            }
            return w.copyLiveCatalog();
        });
        reader = in;
        viewer.aePlayer.attachLiveTail(in);
        if (viewer.getPlayerControls() != null) {
            viewer.getPlayerControls().addMeToPropertyChangeListeners(in);
        }
        viewer.aePlayer.setPlaybackSliderFraction(fraction);
        Marks saved = snapshotMarks();
        if (saved != null) {
            in.installPlaybackMarks(saved);
        }
    }

    private void closeReader() {
        viewingFile = false;
        Aedat4FileInputStream in = reader;
        reader = null;
        if (in != null) {
            captureFrom(in);
            if (viewer.getPlayerControls() != null) {
                viewer.getPlayerControls().removeMeFromPropertyChangeListeners(in);
            }
            if (viewer.aePlayer != null) {
                viewer.aePlayer.detachLiveTail();
            }
        }
    }

    private void leaveTimeshift() {
        // onRecordingStopped runs while AEViewer holds the writer lock. Joining
        // here would deadlock the pump inside recordPacketLocked.
        stopPumpIfStartedHere(false);
        closeReader();
        viewingFile = false;
        yieldsUsb = false;
    }

    private void startPumpIfNeeded() {
        FilterChain chain = viewer.getChip() == null ? null : viewer.getChip().getFilterChain();
        boolean acquisition = chain != null
                && AcquisitionCycle.acquiresOnUsb(chain.getProcessingMode(), viewer.getPlayMode());
        synchronized (pumpLock) {
            stopPump = false;
            if (pumpThread != null) {
                return;
            }
            pumpStartedHere = true;
            final boolean record = !acquisition;
            pumpThread = new Thread(() -> pumpLoop(record), record
                    ? "AEViewer.LiveRecordPump" : "AEViewer.LiveUsbDrain");
            pumpThread.setDaemon(true);
            pumpThread.start();
        }
    }

    /**
     * @param join wait for the pump to leave the writer. False when the caller
     * already holds the recording stream lock.
     */
    private void stopPumpIfStartedHere(boolean join) {
        Thread t;
        synchronized (pumpLock) {
            if (!pumpStartedHere) {
                return;
            }
            stopPump = true;
            pumpLock.notifyAll();
            t = pumpThread;
            pumpThread = null;
            pumpStartedHere = false;
        }
        if (t != null && join) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Rendering mode records here so ViewLoop can read the file. Acquisition mode
     * already records on the USB thread; this loop only swaps the capture pool
     * so that thread does not stall, and it does not record again.
     * <p>
     * Never {@link Thread#interrupt()} this thread. It calls
     * {@code FileChannel.position} while recording, and an interrupt closes that
     * channel ({@code ClosedByInterruptException}), which stops the recording.
     */
    private void pumpLoop(boolean record) {
        while (!stopPump) {
            // A stale interrupt flag closes the recording FileChannel on the next write.
            Thread.interrupted();
            try {
                boolean got = viewer.liveRecordPumpOnce(record);
                if (!got) {
                    waitForPumpWork(2);
                }
            } catch (InterruptedException e) {
                if (stopPump) {
                    break;
                }
            } catch (Exception e) {
                long now = System.currentTimeMillis();
                if (now - lastPumpWarnMs > 2000) {
                    lastPumpWarnMs = now;
                    log.log(Level.WARNING, "live record pump: " + e, e);
                }
                try {
                    waitForPumpWork(20);
                } catch (InterruptedException ie) {
                    if (stopPump) {
                        break;
                    }
                }
            }
        }
    }

    private void waitForPumpWork(long ms) throws InterruptedException {
        synchronized (pumpLock) {
            if (!stopPump) {
                pumpLock.wait(ms);
            }
        }
    }

    private boolean eligible() {
        if (viewer.getPlayMode() != AEViewer.PlayMode.LIVE) {
            return false;
        }
        if (!viewer.isRecordingEnabled() || viewer.getAedat4RecordingOutputStream() == null) {
            return false;
        }
        if (!viewer.isAedat4RecordingOwnedHere()) {
            return false;
        }
        JAERViewer jv = viewer.getJaerViewer();
        return jv == null || !jv.isMuxedAedat4Recording();
    }

    private void showScrubber(boolean fullControls) {
        scrubberShowing = true;
        SwingUtilities.invokeLater(() -> {
            viewer.showLiveRecordingScrubber(true, fullControls);
            if (!fullControls) {
                pinThumb();
            }
        });
    }

    private void hideScrubber() {
        scrubberShowing = false;
        SwingUtilities.invokeLater(() -> viewer.showLiveRecordingScrubber(false, false));
    }

    private void clearSparkline() {
        SwingUtilities.invokeLater(() -> {
            if (viewer.getPlayerControls() == null) {
                return;
            }
            ((PlaybackPositionSlider) viewer.getPlayerControls().getPlayerSlider()).setLogRelativeRates(null);
        });
    }

    private void pinThumb() {
        if (viewer.getPlayerControls() != null && !viewingFile) {
            viewer.getPlayerControls().pinSliderToEnd();
        }
    }

    private void startHistogramTimer() {
        stopHistogramTimer();
        histogramTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AEViewer.LiveHistogram");
            t.setDaemon(true);
            return t;
        });
        histogramTimer.scheduleAtFixedRate(this::histogramTick, 250, 250, TimeUnit.MILLISECONDS);
    }

    private void stopHistogramTimer() {
        ScheduledExecutorService timer = histogramTimer;
        histogramTimer = null;
        if (timer != null) {
            timer.shutdownNow();
        }
    }

    private void histogramTick() {
        if (!scrubberShowing) {
            return;
        }
        Aedat4FileOutputStream writer = viewer.getAedat4RecordingOutputStream();
        if (writer == null) {
            return;
        }
        List<Aedat4FileOutputStream.LiveCatalogEntry> catalog = writer.copyLiveCatalog();
        histogram.addCatalog(catalog, writer.getBaseUnixUs());
        SwingUtilities.invokeLater(() -> {
            if (!scrubberShowing || viewer.getPlayerControls() == null) {
                return;
            }
            int width = viewer.getPlayerControls().getPlayerSlider().getWidth();
            float[] rates = histogram.logRelativeRates(Math.max(32, width));
            ((PlaybackPositionSlider) viewer.getPlayerControls().getPlayerSlider()).setLogRelativeRates(rates);
            if (!viewingFile) {
                pinThumb();
                paintStoredMarks();
            } else {
                viewer.getPlayerControls().repositionMarksFromStream();
            }
        });
    }

    boolean marksArmed() {
        return scrubberShowing && viewer.getAedat4RecordingOutputStream() != null;
    }

    int getLastMarkerOffsetMs() {
        return lastMarkerOffsetMs;
    }

    long setMarkIn() {
        synchronized (markLock) {
            long pos = liveEdgeIndex();
            if (pos <= 0 || (!outFollowsEnd && pos > markOut)) {
                return markIn;
            }
            markIn = pos;
        }
        publishMarks();
        return markIn;
    }

    long setMarkOut() {
        synchronized (markLock) {
            long pos = liveEdgeIndex();
            if (pos <= markIn) {
                return outFollowsEnd ? -1 : markOut;
            }
            outFollowsEnd = false;
            markOut = pos;
        }
        publishMarks();
        return markOut;
    }

    boolean toggleMarker() {
        long pos;
        int offsetMs = 0;
        synchronized (markLock) {
            int reactionMs = viewer.aePlayer == null ? 0 : viewer.aePlayer.getMarkerReactionTimeMs();
            if (reactionMs > 0) {
                long latest = latestEventTimestampUs();
                if (latest > Long.MIN_VALUE) {
                    pos = eventIndexAtTime(latest - reactionMs * 1000L);
                    offsetMs = reactionMs;
                } else {
                    pos = liveEdgeIndex();
                }
            } else {
                pos = liveEdgeIndex();
            }
            lastMarkerOffsetMs = offsetMs;
            if (pos < 0) {
                return false;
            }
            if (!markers.add(pos)) {
                markers.remove(pos);
                publishMarks();
                return false;
            }
        }
        publishMarks();
        return true;
    }

    void clearMarks() {
        resetMarks();
        SwingUtilities.invokeLater(this::clearMarkWidgets);
    }

    void captureFromReader() {
        captureFrom(reader);
    }

    private void captureFrom(Aedat4FileInputStream in) {
        if (in == null) {
            return;
        }
        Marks m = in.getPlaybackMarks();
        synchronized (markLock) {
            markIn = m.markIn;
            if (in.isMarkOutSet()) {
                outFollowsEnd = false;
                markOut = m.markOut;
            } else {
                outFollowsEnd = true;
                markOut = Long.MAX_VALUE;
            }
            markers.clear();
            if (m.otherMarks != null) {
                markers.addAll(m.otherMarks);
            }
        }
    }

    private void resetMarks() {
        synchronized (markLock) {
            markIn = 0;
            markOut = Long.MAX_VALUE;
            outFollowsEnd = true;
            markers.clear();
            lastMarkerOffsetMs = 0;
        }
    }

    private Marks snapshotMarks() {
        synchronized (markLock) {
            if (markIn <= 0 && outFollowsEnd && markers.isEmpty()) {
                return null;
            }
            Marks m = new Marks();
            m.markIn = markIn;
            m.markOut = outFollowsEnd ? Long.MAX_VALUE : markOut;
            m.otherMarks = new TreeSet<>(markers);
            return m;
        }
    }

    private void persistMarks(File file) {
        if (file == null) {
            return;
        }
        AEFileInputStream.marksPutForFile(file, snapshotMarks());
    }

    private long liveEdgeIndex() {
        Aedat4FileOutputStream writer = viewer.getAedat4RecordingOutputStream();
        return writer == null ? 0 : writer.getEventsWritten();
    }

    private void publishMarks() {
        final Marks snap = snapshotMarks();
        final List<Aedat4FileOutputStream.LiveCatalogEntry> catalog = catalogSnapshot();
        SwingUtilities.invokeLater(() -> paintStoredMarks(snap, catalog));
    }

    private void paintStoredMarks() {
        paintStoredMarks(snapshotMarks(), catalogSnapshot());
    }

    private void paintStoredMarks(Marks snap, List<Aedat4FileOutputStream.LiveCatalogEntry> catalog) {
        AePlayerAdvancedControlsPanel controls = viewer.getPlayerControls();
        if (controls == null) {
            return;
        }
        if (snap == null) {
            controls.clearSliderMarks();
            return;
        }
        int sliderMax = controls.getPlayerSlider().getMaximum();
        Integer inPos = snap.markIn > 0 ? toSlider(snap.markIn, catalog, sliderMax) : null;
        Integer outPos = snap.markOut != Long.MAX_VALUE && snap.markOut >= 0
                ? toSlider(snap.markOut, catalog, sliderMax) : null;
        List<Integer> others = new ArrayList<>();
        if (snap.otherMarks != null) {
            for (Long p : snap.otherMarks) {
                if (p != null) {
                    others.add(toSlider(p, catalog, sliderMax));
                }
            }
        }
        controls.showMarkPositions(inPos, outPos, others);
    }

    private void clearMarkWidgets() {
        if (viewer.getPlayerControls() != null) {
            viewer.getPlayerControls().clearSliderMarks();
        }
    }

    private List<Aedat4FileOutputStream.LiveCatalogEntry> catalogSnapshot() {
        Aedat4FileOutputStream writer = viewer.getAedat4RecordingOutputStream();
        if (writer == null) {
            return List.of();
        }
        return writer.copyLiveCatalog();
    }

    private Integer toSlider(long index, List<Aedat4FileOutputStream.LiveCatalogEntry> catalog, int sliderMax) {
        float f = fractionForEvent(index, catalog);
        if (f < 0f) {
            f = 0f;
        } else if (f > 1f) {
            f = 1f;
        }
        return Math.round(f * sliderMax);
    }

    private float fractionForEvent(long index, List<Aedat4FileOutputStream.LiveCatalogEntry> catalog) {
        long origin = Long.MIN_VALUE;
        long end = Long.MIN_VALUE;
        long cum = 0;
        Long at = null;
        if (catalog != null) {
            for (Aedat4FileOutputStream.LiveCatalogEntry e : catalog) {
                if (!isEvents(e) || e.numElements <= 0) {
                    continue;
                }
                if (origin == Long.MIN_VALUE) {
                    origin = e.timestampStart;
                }
                end = e.timestampEnd;
                long next = cum + e.numElements;
                if (at == null && index < next) {
                    double frac = e.numElements <= 1 ? 0
                            : (index - cum) / (double) e.numElements;
                    long span = e.timestampEnd - e.timestampStart;
                    at = e.timestampStart + (long) (frac * span);
                }
                cum = next;
            }
        }
        if (origin == Long.MIN_VALUE || end <= origin) {
            return 1f;
        }
        if (at == null) {
            at = end;
        }
        return (float) ((at - origin) / (double) (end - origin));
    }

    private long latestEventTimestampUs() {
        long end = Long.MIN_VALUE;
        List<Aedat4FileOutputStream.LiveCatalogEntry> catalog = catalogSnapshot();
        for (Aedat4FileOutputStream.LiveCatalogEntry e : catalog) {
            if (isEvents(e) && e.timestampEnd > end) {
                end = e.timestampEnd;
            }
        }
        return end;
    }

    private long eventIndexAtTime(long timestampUs) {
        long cum = 0;
        List<Aedat4FileOutputStream.LiveCatalogEntry> catalog = catalogSnapshot();
        for (Aedat4FileOutputStream.LiveCatalogEntry e : catalog) {
            if (!isEvents(e) || e.numElements <= 0) {
                continue;
            }
            if (timestampUs <= e.timestampStart) {
                return cum;
            }
            if (timestampUs < e.timestampEnd && e.timestampEnd > e.timestampStart) {
                double frac = (timestampUs - e.timestampStart) / (double) (e.timestampEnd - e.timestampStart);
                return cum + Math.round(frac * e.numElements);
            }
            cum += e.numElements;
        }
        return cum;
    }

    private static boolean isEvents(Aedat4FileOutputStream.LiveCatalogEntry e) {
        int rem = e.streamId % Aedat4CameraTrack.STREAMS_PER_CAMERA;
        if (rem < 0) {
            rem += Aedat4CameraTrack.STREAMS_PER_CAMERA;
        }
        return rem == 0;
    }

    static EventPacket firstEventPacket(PacketBundle bundle) {
        if (bundle == null) {
            return null;
        }
        EventPacket polarity = bundle.getFirstPolarityPacket();
        if (polarity != null) {
            return polarity;
        }
        for (TypedDataPacket p : bundle) {
            if (p instanceof EventPacket) {
                return (EventPacket) p;
            }
        }
        return null;
    }
}
