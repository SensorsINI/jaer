/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.opencv;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Packed 8-bit frame for OpenCV (gray8 or BGR8), OpenCV row 0 = top.
 */
public final class OpenCvRawFrame {

    public final int width;
    public final int height;
    /** 1 = gray8, 3 = BGR8. */
    public final int channels;
    public final byte[] data;

    public OpenCvRawFrame(int width, int height, int channels, byte[] data) {
        this.width = width;
        this.height = height;
        this.channels = channels == 3 ? 3 : 1;
        this.data = data;
    }

    public boolean isBgr() {
        return channels == 3;
    }

    public OpenCvRawFrame scaled(int destW, int destH) {
        if (destW <= 0 || destH <= 0 || (destW == width && destH == height)) {
            return this;
        }
        byte[] out = new byte[destW * destH * channels];
        for (int y = 0; y < destH; y++) {
            int sy = (int) ((y / (float) destH) * height);
            if (sy >= height) {
                sy = height - 1;
            }
            int srcRow = sy * width * channels;
            int dstRow = y * destW * channels;
            for (int x = 0; x < destW; x++) {
                int sx = (int) ((x / (float) destW) * width);
                if (sx >= width) {
                    sx = width - 1;
                }
                int si = srcRow + sx * channels;
                int di = dstRow + x * channels;
                out[di] = data[si];
                if (channels == 3) {
                    out[di + 1] = data[si + 1];
                    out[di + 2] = data[si + 2];
                }
            }
        }
        return new OpenCvRawFrame(destW, destH, channels, out);
    }

    /**
     * Packed YUYV (Y0 U Y1 V), 2 bytes/pixel. Gray uses U=V=128.
     */
    public byte[] toYuyv() {
        int w = width & ~1;
        if (w < 2) {
            w = 2;
        }
        byte[] yuyv = new byte[w * height * 2];
        int p = 0;
        for (int y = 0; y < height; y++) {
            int row = y * width * channels;
            for (int x = 0; x < w; x += 2) {
                int i0 = row + Math.min(x, width - 1) * channels;
                int i1 = row + Math.min(x + 1, width - 1) * channels;
                int y0, u, v, y1;
                if (channels == 3) {
                    int b0 = data[i0] & 0xff, g0 = data[i0 + 1] & 0xff, r0 = data[i0 + 2] & 0xff;
                    int b1 = data[i1] & 0xff, g1 = data[i1 + 1] & 0xff, r1 = data[i1 + 2] & 0xff;
                    y0 = rgbToY(r0, g0, b0);
                    y1 = rgbToY(r1, g1, b1);
                    u = rgbToU(r0, g0, b0);
                    v = rgbToV(r0, g0, b0);
                } else {
                    y0 = data[i0] & 0xff;
                    y1 = data[i1] & 0xff;
                    u = 128;
                    v = 128;
                }
                yuyv[p++] = (byte) y0;
                yuyv[p++] = (byte) u;
                yuyv[p++] = (byte) y1;
                yuyv[p++] = (byte) v;
            }
        }
        return yuyv;
    }

    public int yuyvWidth() {
        int w = width & ~1;
        return w < 2 ? 2 : w;
    }

    public byte[] toJpeg(float quality) throws IOException {
        int type = channels == 3 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        BufferedImage img = new BufferedImage(width, height, type);
        WritableRaster raster = img.getRaster();
        raster.setDataElements(0, 0, width, height, data);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageWriter");
        }
        ImageWriter iw = writers.next();
        ImageWriteParam param = iw.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(clamp01(quality));
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(4096, width * height / 4));
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
            iw.setOutput(ios);
            iw.write(null, new IIOImage(img, null, null), param);
        } finally {
            iw.dispose();
        }
        return bos.toByteArray();
    }

    static int rgbToY(int r, int g, int b) {
        return clamp255((77 * r + 150 * g + 29 * b) >> 8);
    }

    static int rgbToU(int r, int g, int b) {
        return clamp255(((-43 * r - 85 * g + 128 * b) >> 8) + 128);
    }

    static int rgbToV(int r, int g, int b) {
        return clamp255(((128 * r - 107 * g - 21 * b) >> 8) + 128);
    }

    static int clamp255(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 255) {
            return 255;
        }
        return v;
    }

    static float clamp01(float v) {
        if (v < 0.05f) {
            return 0.05f;
        }
        if (v > 1f) {
            return 1f;
        }
        return v;
    }
}
