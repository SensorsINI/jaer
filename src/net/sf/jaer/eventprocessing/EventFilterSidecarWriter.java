package net.sf.jaer.eventprocessing;

import java.beans.PropertyChangeEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;

/**
 * {@link EventFilter2D} that records a CSV sidecar beside the camera file and
 * seeks it on playback.
 * <p>
 * Recording start, stop, Save As, discarded takes, file open, and play-mode
 * changes are handled here. Each sample is stamped with the latest camera
 * packet timestamp and that timestamp converted by
 * {@link Aedat4FileOutputStream#cameraTimestampToUnixUs(int, int)}. Playback
 * floor-looks up AEDAT-4 Unix µs, or maps the slider across older files that
 * only have host receive time.
 * <p>
 * Subclasses own the row format and the live sensor or socket.
 * Overrides of {@link #propertyChange} must call {@code super}.
 *
 * @param <T> one sidecar row
 */
public abstract class EventFilterSidecarWriter<T> extends EventFilter2D {

    private volatile int lastCameraUs;
    private boolean listenersAdded;
    private BufferedWriter sidecarWriter;
    private File sidecarFile;
    private TreeMap<Long, T> playback;
    /** True when sidecar keys are AEDAT-4 packet Unix µs, not host receive ms. */
    private boolean playbackByAedat4Unix;
    private boolean playbackMode;

    protected EventFilterSidecarWriter(AEChip chip) {
        super(chip);
    }

    /** Log prefix, for example {@code GNSS} or {@code WitMotion}. */
    protected abstract String sidecarLogTag();

    protected abstract File sidecarFileFor(File recording);

    /** @return writer, or {@code null} when another filter already owns the path */
    protected abstract BufferedWriter tryOpenSidecar(File sidecar, File recording) throws IOException;

    protected abstract void closeSidecarStore(File sidecar, BufferedWriter writer) throws IOException;

    protected abstract File relocateSidecarFile(File sidecar, File recording) throws IOException;

    protected abstract void appendSidecarRow(BufferedWriter writer, T row) throws IOException;

    protected abstract TreeMap<Long, T> loadSidecarFile(File sidecar) throws IOException;

    /** Nonzero when this row was stored on the AEDAT-4 Unix µs clock. */
    protected abstract long rowAedat4UnixUs(T row);

    protected abstract void stopSidecarSource();

    protected abstract boolean isSidecarSourceRunning();

    /** Open the live source. Called only while this filter is enabled and the viewer is LIVE or WAITING. */
    protected abstract void startSidecarSource();

    /** Viewer entered playback. The live source is already stopped. */
    protected abstract void onSidecarPlaybackEntered();

    /** Viewer left playback. The loaded map is already cleared. */
    protected void onSidecarPlaybackLeft() {
    }

    /** Enabled, but the viewer is neither LIVE, WAITING, nor playback. */
    protected abstract void onSidecarIdle();

    protected abstract void onSidecarLoaded(TreeMap<Long, T> map, File sidecar);

    protected abstract void onPlaybackRow(T row);

    protected final void noteCameraTimestamp(EventPacket<?> in) {
        if (in != null && !in.isEmpty()) {
            lastCameraUs = in.getLastTimestamp();
        }
    }

    protected final int lastCameraTimestampUs() {
        return lastCameraUs;
    }

    protected final boolean isLiveOrWaiting() {
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return false;
        }
        AEViewer.PlayMode m = v.getPlayMode();
        return m == AEViewer.PlayMode.LIVE || m == AEViewer.PlayMode.WAITING;
    }

    protected final boolean isSidecarPlayback() {
        return playbackMode;
    }

    /** Loaded playback rows, or {@code null} while live. */
    protected final TreeMap<Long, T> sidecarPlayback() {
        return playback;
    }

    /** Camera packet timestamp converted with the open AEDAT-4 recording, or 0 when not recording. */
    protected final long aedat4UnixUsForCamera(int cameraTimestampUs) {
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return 0;
        }
        Aedat4FileOutputStream out = v.getAedat4RecordingOutputStream();
        if (out == null) {
            return 0;
        }
        return out.cameraTimestampToUnixUs(cameraTimestampUs, v.getAedat4RecordingTrackIndex());
    }

    protected final void writeSidecarRow(T row) {
        BufferedWriter w = sidecarWriter;
        if (w == null || row == null || rowAedat4UnixUs(row) <= 0) {
            return;
        }
        try {
            synchronized (this) {
                if (sidecarWriter != null) {
                    appendSidecarRow(sidecarWriter, row);
                }
            }
        } catch (IOException e) {
            log.log(Level.WARNING, sidecarLogTag() + " sidecar write: " + e, e);
        }
    }

    protected final void ensureSidecarListeners() {
        if (listenersAdded) {
            return;
        }
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return;
        }
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_RECORDING_STARTED, this);
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_RECORDING_STOPPED, this);
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_RECORDING_RENAMED, this);
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_FILEOPEN, this);
        v.getSupport().addPropertyChangeListener(AEViewer.EVENT_PLAYMODE, this);
        if (v.getAePlayer() != null) {
            v.getAePlayer().getSupport().addPropertyChangeListener(AbstractAEPlayer.EVENT_FILEOPEN, this);
        }
        listenersAdded = true;
        syncSidecarToPlayMode();
        loadPlaybackSidecar();
    }

    protected final void updateSidecarPlayback() {
        if (!playbackMode || playback == null || playback.isEmpty()) {
            return;
        }
        long t0 = playback.firstKey();
        long t1 = playback.lastKey();
        long unix;
        if (playbackByAedat4Unix) {
            unix = playheadUnixUs();
        } else {
            float f = 0f;
            AEViewer v = chip.getAeViewer();
            AEFileInputStreamInterface in = v != null ? v.getAeFileInputStream() : null;
            if (in != null) {
                f = in.getPlaybackSliderFraction();
            }
            if (f < 0f) {
                f = 0f;
            } else if (f > 1f) {
                f = 1f;
            }
            unix = t0 + (long) (f * (t1 - t0));
        }
        Map.Entry<Long, T> e = playback.floorEntry(unix);
        if (e == null) {
            e = playback.ceilingEntry(unix);
        }
        if (e != null) {
            onPlaybackRow(e.getValue());
        }
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            ensureSidecarListeners();
            syncSidecarToPlayMode();
        } else {
            stopSidecarSource();
            closeSidecar();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        String n = evt.getPropertyName();
        if (AEViewer.EVENT_RECORDING_STARTED.equals(n) && evt.getNewValue() instanceof File file) {
            openSidecar(file);
        } else if (AEViewer.EVENT_RECORDING_STOPPED.equals(n)) {
            File dest = evt.getNewValue() instanceof File file ? file : null;
            closeSidecarFollowing(dest);
        } else if (AEViewer.EVENT_RECORDING_RENAMED.equals(n) && evt.getNewValue() instanceof File destRec) {
            File destSide = sidecarFileFor(destRec);
            if (sidecarFile != null && destSide != null && sidecarFile.isFile()
                    && !sidecarFile.getAbsoluteFile().equals(destSide.getAbsoluteFile())) {
                closeSidecarFollowing(destRec);
            }
        } else if (AEViewer.EVENT_FILEOPEN.equals(n) || AbstractAEPlayer.EVENT_FILEOPEN.equals(n)) {
            loadPlaybackSidecar();
        } else if (AEViewer.EVENT_PLAYMODE.equals(n)) {
            syncSidecarToPlayMode();
        }
    }

    private void syncSidecarToPlayMode() {
        AEViewer v = chip.getAeViewer();
        playbackMode = v != null && v.getPlayMode() == AEViewer.PlayMode.PLAYBACK;
        if (!isFilterEnabled()) {
            stopSidecarSource();
            return;
        }
        if (playbackMode) {
            stopSidecarSource();
            onSidecarPlaybackEntered();
            loadPlaybackSidecar();
            return;
        }
        playback = null;
        playbackByAedat4Unix = false;
        onSidecarPlaybackLeft();
        if (isLiveOrWaiting()) {
            if (!isSidecarSourceRunning()) {
                startSidecarSource();
            }
        } else {
            stopSidecarSource();
            onSidecarIdle();
        }
    }

    private synchronized void openSidecar(File recording) {
        closeSidecar();
        File side = sidecarFileFor(recording);
        String tag = sidecarLogTag();
        try {
            BufferedWriter w = tryOpenSidecar(side, recording);
            if (w == null) {
                log.info(tag + " sidecar already open for " + side);
                return;
            }
            sidecarFile = side;
            sidecarWriter = w;
            log.info(tag + " sidecar opened " + side.getAbsolutePath());
        } catch (IOException e) {
            log.warning(tag + " sidecar open failed: " + e);
        }
    }

    protected final synchronized void closeSidecar() {
        closeSidecarFollowing(null);
    }

    /**
     * Close the writer, then move the file beside {@code destRecording}
     * (Save As), or delete it when that take was discarded.
     */
    private synchronized void closeSidecarFollowing(File destRecording) {
        File side = sidecarFile;
        boolean wasOpen = sidecarWriter != null;
        String tag = sidecarLogTag();
        try {
            closeSidecarStore(sidecarFile, sidecarWriter);
        } catch (IOException e) {
            log.warning(tag + " sidecar close failed: " + e);
        }
        sidecarWriter = null;
        sidecarFile = null;
        if (side == null) {
            return;
        }
        if (destRecording == null) {
            if (wasOpen) {
                log.info(tag + " sidecar closed " + side.getAbsolutePath());
            }
            return;
        }
        if (destRecording.isFile()) {
            try {
                File moved = relocateSidecarFile(side, destRecording);
                if (moved != null && moved.isFile()
                        && !moved.getAbsoluteFile().equals(side.getAbsoluteFile())) {
                    log.info(tag + " sidecar renamed " + side.getAbsolutePath()
                            + " -> " + moved.getAbsolutePath());
                } else if (wasOpen) {
                    log.info(tag + " sidecar closed " + side.getAbsolutePath());
                }
            } catch (IOException e) {
                log.warning(tag + " sidecar rename failed " + side + " -> "
                        + sidecarFileFor(destRecording) + ": " + e);
            }
            return;
        }
        if (side.isFile()) {
            boolean deleted = side.delete();
            if (deleted) {
                log.info(tag + " sidecar deleted (recording discarded) " + side.getAbsolutePath());
            } else {
                log.warning(tag + " sidecar close " + side.getAbsolutePath()
                        + " (could not delete leftover sidecar)");
            }
        } else if (wasOpen) {
            log.info(tag + " sidecar closed " + side.getAbsolutePath());
        }
    }

    private void loadPlaybackSidecar() {
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return;
        }
        AEFileInputStreamInterface in = v.getAeFileInputStream();
        if (in == null) {
            return;
        }
        File rec = in.getFile();
        File side = sidecarFileFor(rec);
        try {
            TreeMap<Long, T> loaded = loadSidecarFile(side);
            TreeMap<Long, T> byPacketUnix = new TreeMap<>();
            for (T row : loaded.values()) {
                long unix = rowAedat4UnixUs(row);
                if (unix > 0) {
                    byPacketUnix.put(unix, row);
                }
            }
            // Host unix_ms keys are ~1e12; packet Unix µs are ~1e15. Mixing them
            // makes firstKey() a millisecond and slider lookup stick on one row.
            if (!byPacketUnix.isEmpty()) {
                playback = byPacketUnix;
                playbackByAedat4Unix = true;
            } else {
                playback = loaded;
                playbackByAedat4Unix = false;
            }
            TreeMap<Long, T> map = playback;
            if (map.isEmpty()) {
                log.info(sidecarLogTag() + " sidecar empty " + side);
            } else {
                log.info(sidecarLogTag() + " sidecar loaded " + map.size()
                        + (playbackByAedat4Unix ? " aedat4-unix" : " host-ms")
                        + " keys " + map.firstKey() + ".." + map.lastKey()
                        + " " + side);
            }
            onSidecarLoaded(map, side);
        } catch (IOException e) {
            log.log(Level.WARNING, sidecarLogTag() + " sidecar load: " + e, e);
        }
    }

    private long playheadUnixUs() {
        AEViewer v = chip.getAeViewer();
        if (v == null) {
            return 0;
        }
        AEFileInputStreamInterface in = v.getAeFileInputStream();
        if (in instanceof Aedat4FileInputStream a4) {
            return a4.getBaseUnixUs() + a4.getPositionTimestampUs();
        }
        if (in == null) {
            return 0;
        }
        return in.getAbsoluteStartingTimeMs() * 1000L + in.getPositionTimestampUs();
    }
}
