package net.sf.jaer.hardwareinterface.usb.nrv;

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
 * starting at 0 and advancing until signed 32-bit wrap (~2147 s session span).
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
    private static final int SENSOR_COUNT = 2;

    private final long[] refTimeStampMs = new long[SENSOR_COUNT];
    private final long[] refWrapUs = new long[SENSOR_COUNT];
    private final long[] fullTimeStampUs = new long[SENSOR_COUNT];
    private final int[] posX = new int[SENSOR_COUNT];
    private long frameStartTimeMs = 0;
    private int skipPeriodMs = DEFAULT_SKIP_PERIOD_MS;
    private boolean mirrorFlag = false;
    private boolean droppedEventsBeforeColumnAddress;
    /** Absolute device time (µs) subtracted from output; jAER time 0 = this value. */
    private long timestampOriginUs = -1;
    /** Last emitted absolute timestamp (µs), for monotonic output. */
    private long lastOutputAbsoluteUs = -1;

    public S5KRC1SParser() {
        reset();
    }

    /** Clears all parser state (stream restart). */
    public void reset() {
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
    }

    /**
     * Re-zero jAER output timestamps at the current device time without clearing
     * column position or reference-timestamp tracking. Used when the user presses '0'.
     * NRV CX3/FX20 firmware exposes no DAVIS-style hardware timestamp-reset command.
     */
    public void resetTimestampOrigin() {
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

    public int getSkipPeriodMs() {
        return skipPeriodMs;
    }

    /** Normal-packet reference/sub timestamp (P=0 and header 0x08). */
    static boolean isTimestampPacket(byte pkt0) {
        return (pkt0 & 0x80) == 0 && (pkt0 & 0x7C) == 0x08;
    }

    /**
     * @return number of events written to addresses/timestamps
     */
    public int parse(byte[] pkt, int len, int[] addresses, int[] timestamps, int eventOffset, int maxEvents) {
        int eventCount = eventOffset;
        for (int i = 0; i + 3 < len; i += 4) {
            final int header = pkt[i] & 0x7C;
            final int sensorID = (pkt[i] & 0x02) >> 1;

            if (isTimestampPacket(pkt[i])) {
                if ((pkt[i + 1] & 0x80) != 0) {
                    applySubTimestamp(sensorID, ((pkt[i + 2] & 0x03) << 8) | (pkt[i + 3] & 0xFF));
                } else {
                    applyReferenceTimestamp(sensorID,
                            ((pkt[i + 1] & 0x3F) << 16) | ((pkt[i + 2] & 0xFF) << 8) | (pkt[i + 3] & 0xFF));
                }
            }

            if (refTimeStampMs[sensorID] < frameStartTimeMs
                    && refTimeStampMs[sensorID] >= frameStartTimeMs - skipPeriodMs) {
                continue;
            }

            if ((pkt[i] & 0x80) != 0) {
                int grpAddr = ((pkt[i + 1] & 0xFC) >> 2) | ((pkt[i] & 0x01) << 6);
                int posY0 = grpAddr << 3;
                int pol = pkt[i + 1] & 0x01;
                int mask = pkt[i + 3] & 0xFF;
                int groupCount = 2;
                while (groupCount > 0) {
                    if (mask != 0) {
                        final int added = analyzeData(mask, posY0, pol, sensorID,
                                addresses, timestamps, eventCount, maxEvents);
                        if (added < 0) {
                            frameStartTimeMs = refTimeStampMs[sensorID] + skipPeriodMs;
                            return -1;
                        }
                        eventCount += added;
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

            switch (header) {
                case 0x0C:
                    break;
                case 0x04:
                    mirrorFlag = (pkt[i + 1] & 0x80) != 0;
                    final int sFlag = pkt[i + 1] & 0x20;
                    posX[sensorID] = ((pkt[i + 2] & 0x07) << 8) | (pkt[i + 3] & 0xFF);
                    if (posX[sensorID] >= WIDTH) {
                        posX[sensorID] = WIDTH - 1;
                    }
                    if (sFlag != 0) {
                        continue;
                    }
                    break;
                default:
                    break;
            }
        }
        return eventCount - eventOffset;
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
        if (lastOutputAbsoluteUs >= 0 && absoluteUs <= lastOutputAbsoluteUs) {
            absoluteUs = lastOutputAbsoluteUs + 1;
        } else {
            lastOutputAbsoluteUs = absoluteUs;
        }
        if (timestampOriginUs < 0) {
            timestampOriginUs = absoluteUs;
        }
        long outUs = absoluteUs - timestampOriginUs;
        if (outUs > Integer.MAX_VALUE) {
            outUs = Integer.MAX_VALUE;
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
                return -1;
            }
            addresses[eventCount + added] = packAddress(lastPosX, posY, pol);
            timestamps[eventCount + added] = outputTs;
            added++;
        }
        return added;
    }

    public static int packAddress(int x, int y, int polarity) {
        return (x & 0x3FF) | ((y & 0x3FF) << 10) | ((1 - polarity) << 20);
    }

    /** Distinct timestamps and step sizes in a jAER event buffer slice. */
    public static final class TimestampSpread {
        public final int count;
        public final int uniqueTs;
        public final int spanUs;
        public final int minStepUs;
        public final int maxStepUs;

        TimestampSpread(int count, int uniqueTs, int spanUs, int minStepUs, int maxStepUs) {
            this.count = count;
            this.uniqueTs = uniqueTs;
            this.spanUs = spanUs;
            this.minStepUs = minStepUs;
            this.maxStepUs = maxStepUs;
        }
    }

    public static TimestampSpread computeTimestampSpread(int[] timestamps, int start, int count) {
        if (count <= 0) {
            return new TimestampSpread(0, 0, 0, 0, 0);
        }
        if (count == 1) {
            return new TimestampSpread(1, 1, 0, 0, 0);
        }
        int uniqueTs = 1;
        int minStep = Integer.MAX_VALUE;
        int maxStep = 0;
        for (int i = start + 1; i < start + count; i++) {
            final int step = timestamps[i] - timestamps[i - 1];
            if (step > 0) {
                if (step < minStep) {
                    minStep = step;
                }
                if (step > maxStep) {
                    maxStep = step;
                }
            }
            if (timestamps[i] != timestamps[i - 1]) {
                uniqueTs++;
            }
        }
        final int spanUs = timestamps[start + count - 1] - timestamps[start];
        if (minStep == Integer.MAX_VALUE) {
            minStep = 0;
        }
        return new TimestampSpread(count, uniqueTs, spanUs, minStep, maxStep);
    }
}
