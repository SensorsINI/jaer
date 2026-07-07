package net.sf.jaer.hardwareinterface.usb.prophesee.evt3;

/**
 * EVT3 decoder for Prophesee IMX636 (Metavision/openeb evt3_decoder.h).
 */
public class Evt3Parser {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final int TYPEMASK = 1 << 22;
    private static final int TIME_HIGH_WRAP = 1 << 11;

    private int x;
    private int y;
    private boolean polarityOn;
    private long tUs;
    private long timestampOriginUs = -1;
    private long lastOutputAbsoluteUs = -1;

    private int previousMsbT;
    private int previousLsbT;
    private int overflows;

    private long traceLsbUpdates;
    private long traceMsbUpdates;
    private long traceBackwardRejections;
    private long traceVect12Triples;
    private long traceOthersSkipped;

    public Evt3Parser() {
        reset();
    }

    public void reset() {
        x = 0;
        y = 0;
        polarityOn = false;
        tUs = 0;
        timestampOriginUs = -1;
        lastOutputAbsoluteUs = -1;
        previousMsbT = 0;
        previousLsbT = 0;
        overflows = 0;
    }

    public void resetTimestampOrigin() {
        final long anchor = lastOutputAbsoluteUs >= 0 ? lastOutputAbsoluteUs : tUs;
        timestampOriginUs = anchor;
        lastOutputAbsoluteUs = anchor;
    }

    /**
     * @return number of events written, or -1 on buffer overrun
     */
    public int parse(byte[] data, int len, int[] addresses, int[] timestamps, int eventOffset, int maxEvents) {
        int eventCount = eventOffset;
        for (int i = 0; i + 1 < len; i += 2) {
            final int word = readWord(data, i);
            switch (word >>> 12) {
                case 0b0000:
                    y = word & 0x7FF;
                    break;
                case 0b0001:
                    break;
                case 0b0010:
                    x = word & 0x7FF;
                    polarityOn = (word & (1 << 11)) != 0;
                    if (x < WIDTH && y < HEIGHT) {
                        eventCount = emitEvent(addresses, timestamps, eventCount, maxEvents, x, y, polarityOn);
                        if (eventCount < 0) {
                            return -1;
                        }
                    }
                    break;
                case 0b0011:
                    x = word & 0x7FF;
                    polarityOn = (word & (1 << 11)) != 0;
                    break;
                case 0b0100:
                    if (isVect12Triple(data, i, len)) {
                        traceVect12Triples++;
                        final int baseX = x & 0x7FF;
                        if (baseX < WIDTH && y < HEIGHT) {
                            final int valid1 = readWord(data, i) & 0xFFF;
                            final int valid2 = readWord(data, i + 2) & 0xFFF;
                            final int valid3 = readWord(data, i + 4) & 0xFF;
                            eventCount = emitVectorMask(addresses, timestamps, eventCount, maxEvents,
                                    baseX, y, polarityOn, valid1, 0, 12);
                            if (eventCount < 0) {
                                return -1;
                            }
                            eventCount = emitVectorMask(addresses, timestamps, eventCount, maxEvents,
                                    baseX, y, polarityOn, valid2, 12, 12);
                            if (eventCount < 0) {
                                return -1;
                            }
                            eventCount = emitVectorMask(addresses, timestamps, eventCount, maxEvents,
                                    baseX, y, polarityOn, valid3, 24, 8);
                            if (eventCount < 0) {
                                return -1;
                            }
                        }
                        x = (x + 32) & 0xffff;
                        i += 4;
                    }
                    break;
                case 0b0101:
                    if (x < WIDTH && y < HEIGHT) {
                        final int set = word & ((1 << Math.min(8, WIDTH - x)) - 1);
                        for (int bit = 0; bit < 8; bit++) {
                            if ((set & (1 << bit)) != 0) {
                                eventCount = emitEvent(addresses, timestamps, eventCount, maxEvents,
                                        x + bit, y, polarityOn);
                                if (eventCount < 0) {
                                    return -1;
                                }
                            }
                        }
                        x = (x + 8) & 0xffff;
                    }
                    break;
                case 0b0110:
                    updateLsbTimestamp(word & 0xFFF);
                    break;
                case 0b1000:
                    updateMsbTimestamp(word & 0xFFF);
                    break;
                case 0b1010:
                    break;
                case 0b1110:
                    i += skipOthersEvent(data, i, len);
                    break;
                default:
                    break;
            }
        }
        return eventCount - eventOffset;
    }

    public long getTUs() {
        return tUs;
    }

    public long getTimestampOriginUs() {
        return timestampOriginUs;
    }

    public int getOverflows() {
        return overflows;
    }

    public long getTraceLsbUpdates() {
        return traceLsbUpdates;
    }

    public long getTraceMsbUpdates() {
        return traceMsbUpdates;
    }

    public long getTraceBackwardRejections() {
        return traceBackwardRejections;
    }

    public long getTraceVect12Triples() {
        return traceVect12Triples;
    }

    public long getTraceOthersSkipped() {
        return traceOthersSkipped;
    }

    public void clearTraceCounters() {
        traceLsbUpdates = 0;
        traceMsbUpdates = 0;
        traceBackwardRejections = 0;
        traceVect12Triples = 0;
        traceOthersSkipped = 0;
    }

    private static int readWord(byte[] data, int i) {
        return (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8);
    }

    private static boolean isVect12Triple(byte[] data, int i, int len) {
        if (i + 5 >= len) {
            return false;
        }
        return (readWord(data, i + 2) >>> 12) == 0b0100 && (readWord(data, i + 4) >>> 12) == 0b0101;
    }

    private int skipOthersEvent(byte[] data, int i, int len) {
        int extraBytes = 0;
        if (i + 3 < len && (readWord(data, i + 2) >>> 12) == 0b1111) {
            extraBytes = 2;
            if (i + 5 < len && (readWord(data, i + 4) >>> 12) == 0b1111) {
                extraBytes = 4;
                if (i + 7 < len && (readWord(data, i + 6) >>> 12) == 0b0111) {
                    extraBytes = 6;
                }
            }
        }
        if (extraBytes > 0) {
            traceOthersSkipped++;
        }
        return extraBytes;
    }

    private int emitVectorMask(int[] addresses, int[] timestamps, int eventCount, int maxEvents,
            int baseX, int ey, boolean on, int mask, int xOffset, int numBits) {
        final int limit = Math.min(numBits, WIDTH - baseX - xOffset);
        for (int bit = 0; bit < limit; bit++) {
            if ((mask & (1 << bit)) != 0) {
                eventCount = emitEvent(addresses, timestamps, eventCount, maxEvents,
                        baseX + xOffset + bit, ey, on);
                if (eventCount < 0) {
                    return -1;
                }
            }
        }
        return eventCount;
    }

    private void updateLsbTimestamp(int lsbT) {
        if (lsbT != previousLsbT) {
            previousLsbT = lsbT;
            traceLsbUpdates++;
            applyAssembledTimestamp();
        }
    }

    private void updateMsbTimestamp(int msbT) {
        if (msbT == previousMsbT) {
            return;
        }
        traceMsbUpdates++;
        if (previousMsbT >= TIME_HIGH_WRAP + msbT) {
            overflows++;
        }
        if (previousMsbT != msbT) {
            previousLsbT = 0;
        }
        previousMsbT = msbT;
        applyAssembledTimestamp();
    }

    private void applyAssembledTimestamp() {
        final long assembled = assembleTimestamp();
        if (assembled >= tUs) {
            tUs = assembled;
        } else {
            traceBackwardRejections++;
        }
    }

    private long assembleTimestamp() {
        return ((long) previousLsbT | ((long) previousMsbT << 12)) | ((long) overflows << 24);
    }

    private int emitEvent(int[] addresses, int[] timestamps, int eventCount, int maxEvents,
            int ex, int ey, boolean on) {
        if (eventCount >= maxEvents) {
            return -1;
        }
        addresses[eventCount] = ex | (ey << 11) | (on ? TYPEMASK : 0);
        timestamps[eventCount] = toOutputTimestamp(tUs);
        return eventCount + 1;
    }

    private int toOutputTimestamp(long absoluteUs) {
        if (lastOutputAbsoluteUs >= 0 && absoluteUs < lastOutputAbsoluteUs) {
            absoluteUs = lastOutputAbsoluteUs;
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
}
