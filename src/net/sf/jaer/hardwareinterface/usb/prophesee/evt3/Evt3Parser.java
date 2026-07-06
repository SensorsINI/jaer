package net.sf.jaer.hardwareinterface.usb.prophesee.evt3;

/**
 * EVT3 decoder for Prophesee IMX636 (port of neuromorphic-drivers adapters/evt3.rs).
 */
public class Evt3Parser {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    private static final int TYPEMASK = 1 << 22;

    private int x;
    private int y;
    private boolean polarityOn;
    private long tUs;
    private long timestampOriginUs = -1;
    private long lastOutputAbsoluteUs = -1;

    private int previousMsbT;
    private int previousLsbT;
    private int overflows;

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
            final int word = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8);
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
                    if (x < WIDTH && y < HEIGHT) {
                        final int set = word & ((1 << Math.min(12, WIDTH - x)) - 1);
                        for (int bit = 0; bit < 12; bit++) {
                            if ((set & (1 << bit)) != 0) {
                                eventCount = emitEvent(addresses, timestamps, eventCount, maxEvents,
                                        x + bit, y, polarityOn);
                                if (eventCount < 0) {
                                    return -1;
                                }
                            }
                        }
                        x = (x + 12) & 0xffff;
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
                default:
                    break;
            }
        }
        return eventCount - eventOffset;
    }

    private void updateLsbTimestamp(int lsbT) {
        if (lsbT != previousLsbT) {
            previousLsbT = lsbT;
            tUs = assembleTimestamp();
        }
    }

    private void updateMsbTimestamp(int msbT) {
        if (msbT != previousMsbT) {
            if (msbT > previousMsbT) {
                if ((msbT - previousMsbT) < (1 << 11)) {
                    previousLsbT = 0;
                    previousMsbT = msbT;
                }
            } else if ((previousMsbT - msbT) > (1 << 11)) {
                overflows++;
                previousLsbT = 0;
                previousMsbT = msbT;
            }
            tUs = assembleTimestamp();
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
        final long absoluteUs = tUs;
        if (timestampOriginUs < 0) {
            timestampOriginUs = absoluteUs;
        }
        long outUs = absoluteUs - timestampOriginUs;
        if (lastOutputAbsoluteUs >= 0 && outUs < lastOutputAbsoluteUs - timestampOriginUs) {
            outUs = lastOutputAbsoluteUs - timestampOriginUs;
        }
        lastOutputAbsoluteUs = absoluteUs;
        if (outUs > Integer.MAX_VALUE) {
            outUs = Integer.MAX_VALUE;
        }
        addresses[eventCount] = ex | (ey << 11) | (on ? TYPEMASK : 0);
        timestamps[eventCount] = (int) outUs;
        return eventCount + 1;
    }
}
