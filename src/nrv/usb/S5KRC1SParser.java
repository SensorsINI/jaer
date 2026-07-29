package nrv.usb;

import java.util.logging.Logger;

/**
 * Decodes S5KRC1S AER USB packets into jAER raw addresses and timestamps.
 * Port of PacketParser::S5KRC1SDataProcess and AnalyzeData from the NRV SDK.
 *
 * <p>NRV timestamps are absolute device time: 22-bit reference milliseconds plus
 * 10-bit sub-microsecond offset. Unlike DAVIS (15-bit timestamps plus explicit
 * wrap events in the USB stream), NRV time must be assembled in software. Internal
 * math uses {@code long} because {@code refMs * 1000} overflows signed 32-bit int
 * above ~35 minutes and the 22-bit reference rolls over about every 70 minutes.
 *
 * <p>jAER output timestamps are {@code int} microseconds relative to {@link #timestampOriginUs},
 * starting at 0. When relative time exceeds {@link Integer#MAX_VALUE} (~2147 s), output wraps
 * through {@link Integer#MIN_VALUE} and continues (DAVIS/DVX big-wrap convention).
 *
 * <p>Reference/sub timestamp packets are normal packets (P=0). Group event packets
 * (P=1) can also have {@code pkt[0] & 0x7C == 0x08} when the group-2 row offset is 2;
 * they must not be parsed as timestamps (see NRV FileStream timestamp detection).
 *
 * <p>Hardware timestamp cadence is controlled by I2C registers
 * {@code TSTAMP_SUB_UNIT_VAL} (0x32B1:32B2) and {@code TSTAMP_REF_UNIT_VAL} (0x32B3:32B4).
 * Samsung factory presets scale SUB with output rate (e.g. 100→0x0B, 1000→0x7D); lower SUB
 * generally yields more frequent sub-timestamp packets in the USB stream.
 *
 * @see https://nrv.kr/
 */
public class S5KRC1SParser {

    public static final int WIDTH = 960;
    public static final int HEIGHT = 720;
    public static final int DEFAULT_SKIP_PERIOD_MS = 10;
    /** 22-bit reference timestamp field (milliseconds). */
    private static final long REF_MS_MASK = 0x3FFFFFL;
    /** Microseconds added when the 22-bit reference millisecond counter wraps. */
    private static final long REF_WRAP_US = (REF_MS_MASK + 1L) * 1000L;
    /** Full span of signed {@code int} timestamps in microseconds (~4295 s). */
    private static final long OUTPUT_INT_US_SPAN = (long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE + 1L;
    private static final int SENSOR_COUNT = 2;

    /** Per-interval USB packet counters (development trace). */
    public static final class TimingStats {
        int refPackets;
        int subPackets;
        int frameEndPackets;
        int colPackets;
        int eventCount;
        long refMs0;
        long fullUs0;
        long lastOutUs;
        int posX0;
        int maxChunkSpanUs;

        void resetIntervalCounters() {
            refPackets = 0;
            subPackets = 0;
            frameEndPackets = 0;
            colPackets = 0;
            eventCount = 0;
            maxChunkSpanUs = 0;
        }

        void noteChunkSpanUs(int spanUs) {
            if (spanUs > maxChunkSpanUs) {
                maxChunkSpanUs = spanUs;
            }
        }
    }

    private final TimingStats timingStats = new TimingStats();

    private final long[] refTimeStampMs = new long[SENSOR_COUNT];
    private final long[] refWrapUs = new long[SENSOR_COUNT];
    private final long[] fullTimeStampUs = new long[SENSOR_COUNT];
    private final int[] posX = new int[SENSOR_COUNT];
    private long frameStartTimeMs = 0;
    private int skipPeriodMs = DEFAULT_SKIP_PERIOD_MS;
    private boolean mirrorFlag = false;
    private boolean droppedEventsBeforeColumnAddress;
    /** {@code TSTAMP_REF_UNIT_VAL} (0x32B3:32B4); factory presets use 0x03E7 (999). */
    private int tstampRefUnitVal = 999;
    /** {@code TSTAMP_SUB_UNIT_VAL} LSB (0x32B2); scales 10-bit sub fields to µs within each ref ms. */
    private int tstampSubUnitVal = 0x7D;
    /** Absolute device time (µs) subtracted from output; jAER time 0 = this value. */
    private long timestampOriginUs = -1;
    /** Last emitted absolute timestamp (µs), for monotonic output. */
    private long lastOutputAbsoluteUs = -1;
    /** Count of signed 32-bit output timestamp big-wraps since {@link #reset()}. */
    private int bigWrapCount;

    public S5KRC1SParser() {
        reset();
    }

    /**
     * Clears reference/full timestamp state after a live timing-register write.
     * Keeps column position and jAER session origin so the next USB ref/sub packets re-base event time.
     */
    public synchronized void resyncTimingState(int regAddr, String reason) {
        final long savedOrigin = timestampOriginUs;
        final long savedLastOut = lastOutputAbsoluteUs;
        final int savedPosX0 = posX[0];
        for (int i = 0; i < SENSOR_COUNT; i++) {
            refTimeStampMs[i] = 0;
            refWrapUs[i] = 0;
            fullTimeStampUs[i] = 0;
        }
        frameStartTimeMs = 0;
        NRVTrace.logTimingResync(regAddr, reason, savedOrigin, savedLastOut, savedPosX0);
    }

    TimingStats getTimingStats() {
        return timingStats;
    }

    void noteChunkTimestampSpanUs(int spanUs) {
        timingStats.noteChunkSpanUs(spanUs);
    }

    /** Clears all parser state (stream restart). */
    public synchronized void reset() {
        for (int i = 0; i < SENSOR_COUNT; i++) {
            refTimeStampMs[i] = 0;
            refWrapUs[i] = 0;
            fullTimeStampUs[i] = 0;
            posX[i] = -1;
        }
        frameStartTimeMs = 0;
        skipPeriodMs = DEFAULT_SKIP_PERIOD_MS;
        mirrorFlag = false;
        droppedEventsBeforeColumnAddress = false;
        timestampOriginUs = -1;
        lastOutputAbsoluteUs = -1;
        bigWrapCount = 0;
    }

    /** Signed 32-bit output wraps since the last {@link #reset()}. */
    public int getBigWrapCount() {
        return bigWrapCount;
    }

    /**
     * Re-zero jAER output timestamps at the current device time without clearing
     * column position or reference-timestamp tracking. Used when the user presses '0'.
     * NRV CX3/FX20 firmware exposes no DAVIS-style hardware timestamp-reset command.
     */
    public synchronized void resetTimestampOrigin() {
        long anchorUs = lastOutputAbsoluteUs;
        if (anchorUs < 0) {
            anchorUs = Math.max(fullTimeStampUs[0], fullTimeStampUs[1]);
        }
        timestampOriginUs = anchorUs;
        lastOutputAbsoluteUs = anchorUs;
    }

    public void setSkipPeriodMs(int skipPeriodMs) {
        this.skipPeriodMs = skipPeriodMs;
    }

    /**
     * Updates sub-timestamp scaling from I2C {@code TSTAMP_REF_UNIT_VAL} / {@code TSTAMP_SUB_UNIT_VAL}.
     * Reserved for future use; the USB parser matches NRV SDK {@code PacketParser.cpp} and treats
     * 10-bit sub fields on dedicated ref/sub packets as microseconds within each ref ms.
     */
    public void setTimestampScale(int refUnitVal, int subUnitVal) {
        this.tstampRefUnitVal = Math.max(0, refUnitVal);
        this.tstampSubUnitVal = Math.max(0, subUnitVal);
    }

    /** Column packet embeds a 10-bit sub field (unused — SDK does not apply it to {@code mFullTimeStamp}). */
    static int decodeColumnSubTimestamp(byte pkt1, byte pkt2) {
        return ((pkt1 & 0x0F) << 6) | ((pkt2 & 0xFC) >> 2);
    }

    public int getSkipPeriodMs() {
        return skipPeriodMs;
    }

    /**
     * Clears the post-overrun ref-ms skip window. Called when the consumer swaps
     * AE raw buffers so group/column parsing resumes on the fresh write buffer.
     */
    public synchronized void clearOverrunSkip() {
        frameStartTimeMs = 0;
    }

    /** Normal-packet reference/sub timestamp (P=0 and header 0x08). */
    static boolean isTimestampPacket(byte pkt0) {
        return (pkt0 & 0x80) == 0 && (pkt0 & 0x7C) == 0x08;
    }

    /**
     * Result of parsing one USB transfer.
     */
    static final class ParseResult {
        final int eventsWritten;
        final boolean overflowed;

        ParseResult(int eventsWritten, boolean overflowed) {
            this.eventsWritten = eventsWritten;
            this.overflowed = overflowed;
        }
    }

    /**
     * @return number of events written to addresses/timestamps
     */
    public int parse(byte[] pkt, int len, int[] addresses, int[] timestamps, int eventOffset, int maxEvents) {
        final ParseResult result = parseWithResult(pkt, len, addresses, timestamps, eventOffset, maxEvents);
        return result.overflowed ? -1 : result.eventsWritten;
    }

    /**
     * Parses the complete USB transfer, even after the output arrays become full.
     * Continuing the scan is essential because timestamp and column packets update
     * parser state needed by later transfers. A {@code maxEvents} value of zero
     * therefore provides state-only parsing while dropping decoded events.
     */
    synchronized ParseResult parseWithResult(byte[] pkt, int len, int[] addresses, int[] timestamps,
            int eventOffset, int maxEvents) {
        int eventCount = eventOffset;
        boolean overflowed = false;
        final long skipFrameStart = frameStartTimeMs;
        final int skipPeriod = skipPeriodMs;
        final boolean traceTiming = NRVTrace.TIMING_ENABLED;
        for (int i = 0; i + 3 < len; i += 4) {
            final int b0 = pkt[i] & 0xFF;
            final int header = b0 & 0x7C;
            final int sensorID = (b0 & 0x02) >> 1;

            if ((b0 & 0x80) == 0 && header == 0x08) {
                if ((pkt[i + 1] & 0x80) != 0) {
                    final int subTs = ((pkt[i + 2] & 0x03) << 8) | (pkt[i + 3] & 0xFF);
                    applySubTimestamp(sensorID, subTs);
                    if (traceTiming) {
                        timingStats.subPackets++;
                        NRVTrace.logTimingRefSub(sensorID, true, refTimeStampMs[sensorID], subTs, fullTimeStampUs[sensorID]);
                    }
                } else {
                    final long refMs = ((pkt[i + 1] & 0x3F) << 16) | ((pkt[i + 2] & 0xFF) << 8) | (pkt[i + 3] & 0xFF);
                    applyReferenceTimestamp(sensorID, refMs);
                    if (traceTiming) {
                        timingStats.refPackets++;
                        NRVTrace.logTimingRefSub(sensorID, false, refTimeStampMs[sensorID], 0, fullTimeStampUs[sensorID]);
                    }
                }
                continue;
            }

            if (skipFrameStart != 0) {
                final long refMs = refTimeStampMs[sensorID];
                if (refMs < skipFrameStart && refMs >= skipFrameStart - skipPeriod) {
                    continue;
                }
            }

            if ((b0 & 0x80) != 0) {
                int grpAddr = ((pkt[i + 1] & 0xFC) >> 2) | ((b0 & 0x01) << 6);
                int posY0 = grpAddr << 3;
                int pol = pkt[i + 1] & 0x01;
                int mask = pkt[i + 3] & 0xFF;
                int groupCount = 2;
                while (groupCount > 0) {
                    if (mask != 0) {
                        final int added = analyzeData(mask, posY0, pol, sensorID,
                                addresses, timestamps, eventCount, maxEvents);
                        if (added < 0) {
                            eventCount += ~added;
                            if (!overflowed) {
                                // Start the short overload skip only for the transfer
                                // that actually filled an output buffer. State-only
                                // parsing of later transfers must not keep extending it.
                                if (maxEvents > 0) {
                                    frameStartTimeMs = refTimeStampMs[sensorID] + skipPeriod;
                                }
                                overflowed = true;
                            }
                        } else {
                            eventCount += added;
                        }
                    }
                    if (groupCount == 2) {
                        grpAddr = mirrorFlag ? grpAddr - (header >> 2) : grpAddr + (header >> 2);
                        posY0 = grpAddr << 3;
                        pol = (pkt[i + 1] & 0x02) >> 1;
                        mask = pkt[i + 2] & 0xFF;
                    }
                    groupCount--;
                }
                continue;
            }

            if (header == 0x0C) {
                if (traceTiming) {
                    timingStats.frameEndPackets++;
                }
                continue;
            }
            if (header == 0x04) {
                mirrorFlag = (pkt[i + 1] & 0x80) != 0;
                final int sFlag = pkt[i + 1] & 0x20;
                posX[sensorID] = ((pkt[i + 2] & 0x07) << 8) | (pkt[i + 3] & 0xFF);
                if (posX[sensorID] >= WIDTH) {
                    posX[sensorID] = WIDTH - 1;
                }
                if (traceTiming) {
                    timingStats.colPackets++;
                }
                if (sFlag != 0) {
                    continue;
                }
            }
        }
        if (traceTiming) {
            timingStats.refMs0 = refTimeStampMs[0];
            timingStats.fullUs0 = fullTimeStampUs[0];
            timingStats.lastOutUs = lastOutputAbsoluteUs;
            timingStats.posX0 = posX[0];
            NRVTrace.logTimingSummary(timingStats);
        }
        return new ParseResult(eventCount - eventOffset, overflowed);
    }

    private long absoluteRefUs(int sensorID) {
        return refWrapUs[sensorID] + refTimeStampMs[sensorID] * 1000L;
    }

    private void applyReferenceTimestamp(int sensorID, long newRefMs) {
        newRefMs &= REF_MS_MASK;
        final long prevRefMs = refTimeStampMs[sensorID];
        if (prevRefMs != 0 && newRefMs < prevRefMs && (prevRefMs - newRefMs) > (REF_MS_MASK >> 1)) {
            refWrapUs[sensorID] += REF_WRAP_US;
        }
        refTimeStampMs[sensorID] = newRefMs;
        if (frameStartTimeMs == 0) {
            frameStartTimeMs = newRefMs;
        }
        syncRefToOtherSensors(sensorID);
    }

    private void syncRefToOtherSensors(int source) {
        for (int s = 0; s < SENSOR_COUNT; s++) {
            if (s != source) {
                refTimeStampMs[s] = refTimeStampMs[source];
                refWrapUs[s] = refWrapUs[source];
            }
        }
    }

    private void syncFullTimestampToOtherSensors(int source, long newTs) {
        for (int s = 0; s < SENSOR_COUNT; s++) {
            if (s != source && fullTimeStampUs[s] < newTs) {
                fullTimeStampUs[s] = newTs;
            }
        }
    }

    private void applySubTimestamp(int sensorID, int subTs) {
        // Match NRV SDK PacketParser::S5KRC1SDataProcess: 10-bit sub field is µs within ref ms.
        long newTs = absoluteRefUs(sensorID) + subTs;
        if (newTs <= fullTimeStampUs[sensorID]) {
            final long prevRefMs = fullTimeStampUs[sensorID] / 1000L;
            final long currentRefBucketMs = absoluteRefUs(sensorID) / 1000L;
            if (prevRefMs == currentRefBucketMs) {
                refTimeStampMs[sensorID]++;
                if (refTimeStampMs[sensorID] > REF_MS_MASK) {
                    refTimeStampMs[sensorID] = 0;
                    refWrapUs[sensorID] += REF_WRAP_US;
                }
                newTs = absoluteRefUs(sensorID) + subTs;
            }
        }
        fullTimeStampUs[sensorID] = newTs;
        syncFullTimestampToOtherSensors(sensorID, newTs);
    }

    private int toOutputTimestamp(long absoluteUs) {
        // Allow equal timestamps for events in the same hardware time bucket (like EVT3).
        // Only clamp backward jumps so output stays weakly monotonic.
        if (lastOutputAbsoluteUs >= 0 && absoluteUs < lastOutputAbsoluteUs) {
            absoluteUs = lastOutputAbsoluteUs;
        } else if (absoluteUs > lastOutputAbsoluteUs) {
            lastOutputAbsoluteUs = absoluteUs;
        }
        if (timestampOriginUs < 0) {
            timestampOriginUs = absoluteUs;
        }
        long outUs = absoluteUs - timestampOriginUs;
        while (outUs > Integer.MAX_VALUE) {
            outUs -= OUTPUT_INT_US_SPAN;
            bigWrapCount++;
        }
        return (int) outUs;
    }

    private int analyzeData(int mask, int posY0, int pol, int sensorID,
            int[] addresses, int[] timestamps, int eventCount, int maxEvents) {
        final int lastPosX = posX[sensorID];
        if (lastPosX < 0 || posY0 < 0) {
            if (!droppedEventsBeforeColumnAddress) {
                droppedEventsBeforeColumnAddress = true;
                Logger.getLogger("net.sf.jaer").fine(
                        "NRV parser: dropping group events until first column-address packet (posX="
                                + lastPosX + ", posY0=" + posY0 + ")");
            }
            return 0;
        }
        if (lastPosX >= WIDTH || posY0 + 7 >= HEIGHT) {
            return 0;
        }

        final int outputTs = toOutputTimestamp(fullTimeStampUs[sensorID]);
        int added = 0;
        int workingMask = mask;
        while (workingMask != 0) {
            final int index = Integer.numberOfTrailingZeros(workingMask);
            workingMask &= (workingMask - 1);
            final int posY = posY0 + index;
            if (eventCount + added >= maxEvents) {
                return ~added;
            }
            addresses[eventCount + added] = packAddress(lastPosX, posY, pol);
            timestamps[eventCount + added] = outputTs;
            added++;
        }
        if (NRVTrace.TIMING_ENABLED) {
            timingStats.eventCount += added;
        }
        return added;
    }

    public static int packAddress(int x, int y, int polarity) {
        return (x & 0x3FF) | ((y & 0x3FF) << 10) | ((1 - polarity) << 20);
    }
}
