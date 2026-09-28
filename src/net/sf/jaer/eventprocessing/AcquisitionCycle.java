/*
 * AcquisitionCycle.java
 *
 * Filter and record only the events appended by the current USB callback.
 */
package net.sf.jaer.eventprocessing;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketSuffix;
import net.sf.jaer.graphics.AEViewer;

/**
 * USB-thread hook for {@link FilterChain.ProcessingMode#ACQUISITION}.
 * <p>
 * Call {@link #markRaw} and/or {@link #markTyped} before the callback appends
 * events, then {@link #finish} after. In rendering mode both are no-ops and do
 * not snapshot packets. A non-empty typed suffix is filtered and recorded;
 * the raw window is used only when typed demux did not append anything.
 */
public final class AcquisitionCycle {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** Labels a copied raw window (stereo eye bit). Must not touch the live buffer. */
    @FunctionalInterface
    public interface RawLabeler {
        void label(AEPacketRaw slice);
    }

    private final PacketSuffix.Mark typedMark = new PacketSuffix.Mark();
    private PacketBundle typed;
    private AEPacketRaw raw;
    private int rawCount;
    private AEPacketRaw rawSlice;

    /**
     * True when live USB (not playback) should run the chain on the acquisition thread.
     */
    public static boolean acquiresOnUsb(FilterChain.ProcessingMode mode, AEViewer.PlayMode play) {
        return mode == FilterChain.ProcessingMode.ACQUISITION && play == AEViewer.PlayMode.LIVE;
    }

    /** ViewLoop runs {@code filterBundle} except on the live acquisition path. */
    public static boolean viewLoopFilters(FilterChain.ProcessingMode mode, AEViewer.PlayMode play) {
        return !acquiresOnUsb(mode, play);
    }

    /** ViewLoop writes the log except on the live acquisition path (the writer thread owns it). */
    public static boolean viewLoopRecords(FilterChain.ProcessingMode mode, AEViewer.PlayMode play) {
        return viewLoopFilters(mode, play);
    }

    /**
     * Enabled filters force a full render except in live acquisition mode, where
     * the chain already ran and display may skip.
     */
    public static boolean filtersBlockRenderSkip(FilterChain.ProcessingMode mode, AEViewer.PlayMode play,
            boolean anyFilterEnabled, boolean recordFiltered) {
        if (acquiresOnUsb(mode, play)) {
            return false;
        }
        return anyFilterEnabled || recordFiltered;
    }

    /**
     * Watermark the raw write buffer. Clears any typed mark. No-op unless this
     * chip is in live acquisition mode and a filter is enabled or recording is on.
     */
    public void markRaw(AEChip chip, AEPacketRaw packet) {
        if (!shouldCapture(chip)) {
            clearMarks();
            return;
        }
        typed = null;
        typedMark.clear();
        raw = packet;
        rawCount = packet == null ? 0 : packet.getNumEvents();
    }

    /**
     * Watermark the typed write buffer.
     *
     * @param keepRaw true when {@link #markRaw} already ran for this URB
     * (typed slice wins in {@link #finish} when it is non-empty)
     */
    public void markTyped(AEChip chip, PacketBundle bundle, boolean keepRaw) {
        if (!shouldCapture(chip)) {
            clearMarks();
            return;
        }
        if (!keepRaw) {
            raw = null;
            rawCount = 0;
        }
        typed = bundle;
        if (bundle == null) {
            typedMark.clear();
        } else {
            typedMark.capture(bundle);
        }
    }

    /**
     * Filter the new suffix and offer a record copy. Call while the USB pool
     * lock is still held.
     */
    public void finish(AEChip chip, RawLabeler labeler) {
        PacketBundle typedBundle = typed;
        AEPacketRaw rawPacket = raw;
        int from = rawCount;
        typed = null;
        raw = null;
        rawCount = 0;
        if (!shouldCapture(chip)) {
            typedMark.clear();
            return;
        }
        try {
            PacketBundle slice = PacketSuffix.slice(typedBundle, typedMark);
            if (slice != null && !slice.isEmpty()) {
                deliver(chip, slice);
                return;
            }
            if (rawPacket == null) {
                return;
            }
            int to = rawPacket.getNumEvents();
            if (from < 0 || to <= from) {
                return;
            }
            AEPacketRaw window = copyRawWindow(rawPacket, from, to);
            if (labeler != null) {
                labeler.label(window);
            }
            EventExtractor2D extractor = chip.getEventExtractor();
            if (extractor == null) {
                return;
            }
            synchronized (extractor) {
                PacketBundle extracted = extractor.extractBundle(window);
                deliver(chip, extracted);
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "acquisition-cycle filter failed: " + e, e);
        } finally {
            typedMark.clear();
        }
    }

    private void deliver(AEChip chip, PacketBundle slice) {
        if (slice == null || slice.isEmpty()) {
            return;
        }
        PacketBundle out = slice;
        FilterChain chain = chip.getFilterChain();
        if (chain != null && chain.isFilteringEnabled() && chain.isAnyFilterEnabled()) {
            if (chain.isShowAcquisitionCycleOverlay()) {
                chain.noteAcquisitionCycle(System.nanoTime());
            }
            out = chain.filterBundle(slice);
        }
        if (out == null || out.isEmpty()) {
            return;
        }
        AEViewer viewer = chip.getAeViewer();
        if (viewer != null && viewer.isRecordingEnabled() && !viewer.isRecordingPaused()) {
            viewer.offerAcquisitionRecording(out);
        }
    }

    private AEPacketRaw copyRawWindow(AEPacketRaw src, int from, int to) {
        int n = to - from;
        if (rawSlice == null) {
            rawSlice = new AEPacketRaw(n);
        } else {
            rawSlice.ensureCapacity(n);
        }
        System.arraycopy(src.getAddresses(), from, rawSlice.getAddresses(), 0, n);
        System.arraycopy(src.getTimestamps(), from, rawSlice.getTimestamps(), 0, n);
        rawSlice.setNumEvents(n);
        return rawSlice;
    }

    private void clearMarks() {
        typed = null;
        raw = null;
        rawCount = 0;
        typedMark.clear();
    }

    static boolean shouldCapture(AEChip chip) {
        if (chip == null) {
            return false;
        }
        FilterChain chain = chip.getFilterChain();
        AEViewer viewer = chip.getAeViewer();
        if (chain == null || viewer == null) {
            return false;
        }
        if (!acquiresOnUsb(chain.getProcessingMode(), viewer.getPlayMode())) {
            return false;
        }
        boolean recording = viewer.isRecordingEnabled() && !viewer.isRecordingPaused();
        return chain.isAnyFilterEnabled() || recording;
    }
}
