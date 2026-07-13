/*
 * DavisFrameAssembler.java
 *
 * jAER 3.0: assemble APS address-events into FramePacket; discard AE afterwards.
 */
package eu.seebetter.ini.chips.davis;

import java.util.Arrays;
import java.util.logging.Logger;

import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.FramePacket;

/**
 * Stateful assembler that consumes APS ResetRead/SignalRead address-events and
 * emits completed {@link FramePacket}s (CDS = reset − signal). Used by
 * {@link DavisBaseCamera.DavisEventExtractor#extractBundle} so APS AE never
 * enter the cooked polarity pipeline.
 *
 * @author tobi
 */
public class DavisFrameAssembler {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    private final DavisBaseCamera chip;
    private final int fixedWidth;
    private final int fixedHeight;
    private int exposureUsFallback;
    private short[] resetBuf;
    private FramePacket building;
    private boolean inFrame;
    private long timestampSofUs;
    private long timestampSoeUs;
    private long timestampEoeUs;
    private long timestampEofUs;
    private int warningCount;

    public DavisFrameAssembler(DavisBaseCamera chip) {
        this.chip = chip;
        this.fixedWidth = 0;
        this.fixedHeight = 0;
        this.exposureUsFallback = 0;
    }

    /**
     * Chip-free constructor for unit/smoke tests.
     */
    public DavisFrameAssembler(int width, int height, int exposureUsFallback) {
        this.chip = null;
        this.fixedWidth = width;
        this.fixedHeight = height;
        this.exposureUsFallback = exposureUsFallback;
    }

    private int width() {
        return chip != null ? chip.getSizeX() : fixedWidth;
    }

    private int height() {
        return chip != null ? chip.getSizeY() : fixedHeight;
    }

    private int exposureFallbackUs() {
        if (chip != null && chip.getDavisConfig() != null) {
            return (int) (chip.getDavisConfig().getExposureDelayMs() * 1000);
        }
        return exposureUsFallback;
    }

    public void reset() {
        inFrame = false;
        building = null;
        timestampSofUs = timestampSoeUs = timestampEoeUs = timestampEofUs = 0;
    }

    /**
     * Process one APS pixel readout. Returns a completed {@link FramePacket}
     * when the last signal-read pixel arrives; otherwise null. Returned packet
     * owns its pixel buffer (safe to keep in a {@link net.sf.jaer.event.PacketBundle}).
     */
    public FramePacket process(final int adcSample, final int timestamp, final short x, final short y,
            final ApsDvsEvent.ReadoutType readoutType, final boolean pixFirst, final boolean pixLast,
            final boolean rollingShutter) {

        final int w = width();
        final int h = height();
        ensureBuffers(w, h);

        if (readoutType == ApsDvsEvent.ReadoutType.ResetRead) {
            if (pixFirst) {
                startFrame(w, h, timestamp);
                if (rollingShutter) {
                    timestampSoeUs = timestamp;
                }
            }
            if (!inFrame) {
                startFrame(w, h, timestamp);
            }
            final int idx = index(x, y, w, h);
            if (idx >= 0) {
                resetBuf[idx] = (short) adcSample;
            }
            if (pixLast && !rollingShutter) {
                timestampSoeUs = timestamp;
            }
            return null;
        }

        if (readoutType == ApsDvsEvent.ReadoutType.SignalRead) {
            if (!inFrame) {
                if ((warningCount++ % 1000) == 0) {
                    log.warning("APS SignalRead without active frame; ignoring until next ResetRead SOF");
                }
                return null;
            }
            if (pixFirst) {
                timestampEoeUs = timestamp;
            }
            final int idx = index(x, y, w, h);
            if (idx >= 0) {
                int cds = (resetBuf[idx] & 0xffff) - (adcSample & 0xffff);
                if (cds < 0) {
                    cds = 0;
                }
                if (cds > 65535) {
                    cds = 65535;
                }
                building.getPixels()[idx] = (short) cds;
            }
            if (pixLast) {
                timestampEofUs = timestamp;
                return finishFrame();
            }
            return null;
        }

        return null;
    }

    private void startFrame(int w, int h, int timestamp) {
        inFrame = true;
        timestampSofUs = timestamp;
        timestampSoeUs = 0;
        timestampEoeUs = 0;
        timestampEofUs = 0;
        if (building == null || building.getWidth() != w || building.getHeight() != h) {
            building = new FramePacket(w, h, FramePacket.ColorMode.GRAYSCALE);
        } else {
            building.allocate(w, h, FramePacket.ColorMode.GRAYSCALE);
            building.clear();
        }
        Arrays.fill(resetBuf, (short) 0);
        building.setTimestampStartUs(timestamp);
    }

    private FramePacket finishFrame() {
        inFrame = false;
        building.setTimestampStartUs(timestampSofUs != 0 ? timestampSofUs : timestampSoeUs);
        building.setTimestampEndUs(timestampEofUs != 0 ? timestampEofUs : timestampEoeUs);
        int exposure;
        if (timestampEoeUs != 0 && timestampSoeUs != 0) {
            exposure = (int) Math.abs(timestampEoeUs - timestampSoeUs);
        } else {
            exposure = exposureFallbackUs();
        }
        building.setExposureUs(exposure);

        FramePacket out = new FramePacket(building.getWidth(), building.getHeight(), FramePacket.ColorMode.GRAYSCALE);
        System.arraycopy(building.getPixels(), 0, out.getPixels(), 0, building.getPixels().length);
        out.setTimestampStartUs(building.getTimestampStartUs());
        out.setTimestampEndUs(building.getTimestampEndUs());
        out.setExposureUs(building.getExposureUs());
        out.setSource(building.getSource());
        return out;
    }

    private void ensureBuffers(int w, int h) {
        final int n = w * h;
        if (resetBuf == null || resetBuf.length != n) {
            resetBuf = new short[n];
        }
    }

    private static int index(short x, short y, int w, int h) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return -1;
        }
        return y * w + x;
    }
}
