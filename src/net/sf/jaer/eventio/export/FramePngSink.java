package net.sf.jaer.eventio.export;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.imageio.ImageIO;
import net.sf.jaer.event.FramePacket;
import eu.seebetter.ini.chips.DavisChip;

/**
 * Writes APS {@link FramePacket}s as compressed PNGs plus {@code timestamps.txt}.
 */
public final class FramePngSink implements AutoCloseable {

    private final File dir;
    private final PrintWriter timestamps;
    private final int maxAdc;
    private long framesWritten;

    public FramePngSink(File dir, File sourceFile, int maxAdc) throws IOException {
        this.dir = dir;
        this.maxAdc = maxAdc > 0 ? maxAdc : DavisChip.MAX_ADC;
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create frames folder " + dir);
        }
        File tsFile = new File(dir, "timestamps.txt");
        this.timestamps = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tsFile), StandardCharsets.UTF_8)));
        timestamps.println("# jAER APS frame timestamps (microseconds), one per PNG");
        timestamps.println("# created " + new Date());
        timestamps.println("# source-file: " + (sourceFile != null ? sourceFile : "(unknown)"));
        timestamps.println("# files: <timestamp_us>.png (y=0 at top)");
    }

    public void write(FramePacket frame) throws IOException {
        if (frame == null || frame.getSize() == 0 || frame.getPixels() == null) {
            return;
        }
        int w = frame.getWidth();
        int h = frame.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        long ts = frame.getTimestampEndUs() != 0 ? frame.getTimestampEndUs() : frame.getTimestampStartUs();
        BufferedImage img = toImage(frame);
        File png = uniquePng(ts);
        ImageIO.write(img, "png", png);
        timestamps.println(ts);
        framesWritten++;
    }

    private File uniquePng(long ts) {
        File f = new File(dir, ts + ".png");
        if (!f.exists()) {
            return f;
        }
        int i = 1;
        while (true) {
            File alt = new File(dir, ts + "-" + i + ".png");
            if (!alt.exists()) {
                return alt;
            }
            i++;
        }
    }

    private BufferedImage toImage(FramePacket frame) {
        int w = frame.getWidth();
        int h = frame.getHeight();
        short[] px = frame.getPixels();
        FramePacket.ColorMode mode = frame.getColorMode();
        int type = mode == FramePacket.ColorMode.GRAYSCALE
                ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(w, h, type);
        int ch = frame.channelsPerPixel();
        float scale = 255f / Math.max(1, maxAdc);
        for (int y = 0; y < h; y++) {
            int srcY = h - 1 - y; // jAER y=0 at bottom
            for (int x = 0; x < w; x++) {
                int src = (srcY * w + x) * ch;
                if (mode == FramePacket.ColorMode.GRAYSCALE) {
                    int g = clamp8((int) (px[src] * scale));
                    img.setRGB(x, y, (g << 16) | (g << 8) | g);
                } else {
                    int r = clamp8((int) (px[src] * scale));
                    int g = clamp8((int) (px[src + 1] * scale));
                    int b = clamp8((int) (px[src + Math.min(2, ch - 1)] * scale));
                    img.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
        }
        return img;
    }

    private static int clamp8(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 255) {
            return 255;
        }
        return v;
    }

    public long getFramesWritten() {
        return framesWritten;
    }

    @Override
    public void close() {
        timestamps.close();
    }
}
