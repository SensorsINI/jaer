package net.sf.jaer.chip.opencv;

import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.event.FramePacket;

/**
 * RGB webcam frames: blit 8-bit samples as 0–1 without Davis APS contrast.
 */
public class OpenCvFrameRenderer extends DavisRenderer {

    public OpenCvFrameRenderer(net.sf.jaer.chip.AEChip chip) {
        super(chip);
        setMaxADC(255);
    }

    @Override
    protected void applyFramePacket(final FramePacket frame) {
        if (frame == null || frame.isEmpty()) {
            return;
        }
        if (skipFrame()) {
            return;
        }
        final int w = frame.getWidth();
        final int h = frame.getHeight();
        if (chip != null && (chip.getSizeX() != w || chip.getSizeY() != h) && w > 0 && h > 0) {
            chip.setSizeX(w);
            chip.setSizeY(h);
        }
        checkPixmapAllocation();
        startFrame((int) frame.getTimestampStartUs());
        final float[] buf = pixBuffer.array();
        final short[] pix = frame.getPixels();
        final int ch = Math.max(1, frame.channelsPerPixel());
        final boolean rgb = ch >= 3;
        minValue = Float.MAX_VALUE;
        maxValue = Float.MIN_VALUE;
        int written = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int base = (y * w + x) * ch;
                final int rawR = pix[base] & 0xffff;
                final int rawG = rgb ? (pix[base + 1] & 0xffff) : rawR;
                final int rawB = rgb ? (pix[base + 2] & 0xffff) : rawR;
                final int valR = rawR > 255 ? (rawR >> 8) : rawR;
                final int valG = rawG > 255 ? (rawG >> 8) : rawG;
                final int valB = rawB > 255 ? (rawB >> 8) : rawB;
                final int valHist = rgb ? ((valR + valG + valB) / 3) : valR;
                if (valHist < minValue) {
                    minValue = valHist;
                }
                if (valHist > maxValue) {
                    maxValue = valHist;
                }
                final int index = getPixMapIndex(x, y);
                if ((index < 0) || (index + 3 >= buf.length)) {
                    continue;
                }
                buf[index] = valR / 255f;
                buf[index + 1] = valG / 255f;
                buf[index + 2] = valB / 255f;
                buf[index + 3] = 1;
                written++;
            }
        }
        finalizeAppliedFramePacket((int) frame.getTimestampEndUs(), false);
        if (log.isLoggable(java.util.logging.Level.FINER)) {
            log.finer("OpenCV applyFramePacket " + w + "x" + h + " written=" + written);
        }
    }
}
