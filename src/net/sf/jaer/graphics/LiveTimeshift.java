package net.sf.jaer.graphics;

import java.io.IOException;
import java.util.List;
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
        histogram.reset(writer.getBaseUnixUs());
        clearSparkline();
        showScrubber(false);
        startHistogramTimer();
    }

    void onRecordingStopped() {
        leaveTimeshift();
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
        histogram.reset(writer.getBaseUnixUs());
        clearSparkline();
        if (viewingFile) {
            boolean wasPaused = viewer.getAePlayer().isPaused();
            closeReader();
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
        stopPumpIfStartedHere();
        closeReader();
        viewingFile = false;
        yieldsUsb = false;
        showScrubber(false);
        SwingUtilities.invokeLater(this::pinThumb);
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
        viewer.aePlayer.setPlaybackSliderFraction(fraction);
    }

    private void closeReader() {
        viewingFile = false;
        Aedat4FileInputStream in = reader;
        reader = null;
        if (in != null && viewer.aePlayer != null) {
            viewer.aePlayer.detachLiveTail();
        }
    }

    private void leaveTimeshift() {
        stopPumpIfStartedHere();
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

    private void stopPumpIfStartedHere() {
        Thread t;
        synchronized (pumpLock) {
            if (!pumpStartedHere) {
                return;
            }
            stopPump = true;
            t = pumpThread;
            pumpThread = null;
            pumpStartedHere = false;
        }
        if (t != null) {
            t.interrupt();
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
     */
    private void pumpLoop(boolean record) {
        while (!stopPump) {
            try {
                boolean got = viewer.liveRecordPumpOnce(record);
                if (!got) {
                    Thread.sleep(2);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                long now = System.currentTimeMillis();
                if (now - lastPumpWarnMs > 2000) {
                    lastPumpWarnMs = now;
                    log.log(Level.WARNING, "live record pump: " + e, e);
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
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
            }
        });
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
