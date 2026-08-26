/*
 * FramePacket.java
 *
 * jAER 3.0: assembled APS / intensity frame as a typed packet (not pixel AEs).
 */
package net.sf.jaer.event;

/**
 * A single image frame assembled from APS address-events (or read from
 * AEDAT-4). In the jAER 3.0 pipeline, APS ResetRead/SignalRead address-events
 * are consumed by the extractor to build this packet and are then discarded;
 * filters and renderers see only completed frames.
 * <p>
 * Mirrors the DV / AEDAT-4 Frame concept: contiguous pixel buffer plus
 * timing metadata, not a stream of per-pixel events.
 *
 * @author tobi
 */
public class FramePacket implements TypedDataPacket {

    public enum ColorMode {
        GRAYSCALE,
        RGB,
        RGBA
    }

    private int width;
    private int height;
    /** Exposure start timestamp (µs). */
    private long timestampStartUs;
    /** Exposure end / frame-ready timestamp (µs). */
    private long timestampEndUs;
    private int exposureUs;
    private ColorMode colorMode = ColorMode.GRAYSCALE;
    /**
     * Pixel samples. For GRAYSCALE: length = width*height. For RGB: 3 channels
     * interleaved (RGBRGB…) length = width*height*3.
     */
    private short[] pixels;
    private int streamId;
    private byte source;
    private long timestampEpoch = UNASSIGNED_TIMESTAMP_EPOCH;

    public FramePacket() {
    }

    public FramePacket(int width, int height, ColorMode colorMode) {
        allocate(width, height, colorMode);
    }

    /**
     * Allocates or reallocates the pixel buffer for the given geometry.
     */
    public final void allocate(int width, int height, ColorMode colorMode) {
        clearTimestampEpoch();
        this.width = width;
        this.height = height;
        this.colorMode = colorMode == null ? ColorMode.GRAYSCALE : colorMode;
        int n = width * height * channelsPerPixel();
        if (pixels == null || pixels.length != n) {
            pixels = new short[n];
        }
    }

    public int channelsPerPixel() {
        switch (colorMode) {
            case RGB:
                return 3;
            case RGBA:
                return 4;
            case GRAYSCALE:
            default:
                return 1;
        }
    }

    @Override
    public PacketType getPacketType() {
        return PacketType.FRAME;
    }

    @Override
    public long getTimestampEpoch() {
        return timestampEpoch;
    }

    @Override
    public void setTimestampEpoch(final long timestampEpoch) {
        if (timestampEpoch < 0) {
            throw new IllegalArgumentException("timestamp epoch must be non-negative");
        }
        this.timestampEpoch = timestampEpoch;
    }

    @Override
    public void clearTimestampEpoch() {
        timestampEpoch = UNASSIGNED_TIMESTAMP_EPOCH;
    }

    /**
     * A frame packet always represents one frame when non-empty (pixels
     * allocated and size &gt; 0).
     */
    @Override
    public int getSize() {
        return (pixels != null && width > 0 && height > 0) ? 1 : 0;
    }

    @Override
    public void clear() {
        clearTimestampEpoch();
        timestampStartUs = 0;
        timestampEndUs = 0;
        exposureUs = 0;
        if (pixels != null) {
            java.util.Arrays.fill(pixels, (short) 0);
        }
        // keep width/height/buffer for reuse
    }

    /**
     * Marks this packet empty without freeing the buffer (for pool reuse).
     */
    public void invalidate() {
        width = 0;
        height = 0;
        clear();
    }

    @Override
    public long getFirstTimestampUs() {
        return timestampStartUs != 0 ? timestampStartUs : timestampEndUs;
    }

    @Override
    public long getLastTimestampUs() {
        return timestampEndUs != 0 ? timestampEndUs : timestampStartUs;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getTimestampStartUs() {
        return timestampStartUs;
    }

    public void setTimestampStartUs(long timestampStartUs) {
        this.timestampStartUs = timestampStartUs;
    }

    public long getTimestampEndUs() {
        return timestampEndUs;
    }

    public void setTimestampEndUs(long timestampEndUs) {
        this.timestampEndUs = timestampEndUs;
    }

    public int getExposureUs() {
        return exposureUs;
    }

    public void setExposureUs(int exposureUs) {
        this.exposureUs = exposureUs;
    }

    public ColorMode getColorMode() {
        return colorMode;
    }

    public void setColorMode(ColorMode colorMode) {
        this.colorMode = colorMode;
    }

    /**
     * @return backing pixel array (do not retain across clear/allocate)
     */
    public short[] getPixels() {
        return pixels;
    }

    public void setPixel(int x, int y, short value) {
        pixels[y * width + x] = value;
    }

    public short getPixel(int x, int y) {
        return pixels[y * width + x];
    }

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    public byte getSource() {
        return source;
    }

    public void setSource(byte source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return String.format("FramePacket %dx%d %s ts=[%d,%d] exp=%dus",
                width, height, colorMode, timestampStartUs, timestampEndUs, exposureUs);
    }
}
