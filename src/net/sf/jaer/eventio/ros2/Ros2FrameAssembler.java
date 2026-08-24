/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.ros2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Assembles DVS events into event-count, polarity timestamp, or voxel-grid
 * frames independently of the AEViewer renderer.
 */
public class Ros2FrameAssembler {

    public enum FrameType {
        EventCountHistogram,
        TimestampImages,
        VoxelGrid
    }

    public enum TimeSliceMethod {
        EventCount,
        TimeIntervalUs
    }

    public enum FoxgloveFrameEncoding {
        Float32,
        Rgb8,
        Mono8
    }

    public static final String TOPIC_EVENT_COUNT = "event_count";
    public static final String TOPIC_TIME_ON = "time_surface_on";
    public static final String TOPIC_TIME_OFF = "time_surface_off";
    public static final String TOPIC_VOXEL = "voxel_grid";

    private int width = 0;
    private int height = 0;
    private int voxelBins = 5;
    private int grayScale = 16;
    private int eventsPerFrame = 2000;
    private int timeDurationUs = 10000;
    private FrameType frameType = FrameType.EventCountHistogram;
    private TimeSliceMethod timeSliceMethod = TimeSliceMethod.EventCount;
    private boolean flipY = true;

    private int[] eventCount;
    private int[] timeOnUs;
    private int[] timeOffUs;
    private float[] voxel;
    private final ArrayList<int[]> sliceEvents = new ArrayList<>();
    private int firstTimestampUs;
    private int lastTimestampUs;
    private int accumulated;
    private boolean started;

    public synchronized void setSize(int width, int height) {
        if (width == this.width && height == this.height && eventCount != null) {
            return;
        }
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        allocate();
    }

    public synchronized void setVoxelBins(int bins) {
        int b = Math.max(2, bins);
        if (b == voxelBins && voxel != null) {
            return;
        }
        voxelBins = b;
        allocate();
    }

    public void setGrayScale(int grayScale) {
        this.grayScale = Math.max(1, grayScale);
    }

    public void setEventsPerFrame(int eventsPerFrame) {
        this.eventsPerFrame = Math.max(1, eventsPerFrame);
    }

    public void setTimeDurationUs(int timeDurationUs) {
        this.timeDurationUs = Math.max(1, timeDurationUs);
    }

    public void setFrameType(FrameType frameType) {
        this.frameType = frameType == null ? FrameType.EventCountHistogram : frameType;
    }

    public void setTimeSliceMethod(TimeSliceMethod timeSliceMethod) {
        this.timeSliceMethod = timeSliceMethod == null ? TimeSliceMethod.EventCount : timeSliceMethod;
    }

    public void setFlipY(boolean flipY) {
        this.flipY = flipY;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getVoxelBins() {
        return voxelBins;
    }

    public int getGrayScale() {
        return grayScale;
    }

    public FrameType getFrameType() {
        return frameType;
    }

    public int getFirstTimestampUs() {
        return firstTimestampUs;
    }

    public int getLastTimestampUs() {
        return lastTimestampUs;
    }

    public int getAccumulatedEventCount() {
        return accumulated;
    }

    public synchronized void allocate() {
        int n = width * height;
        if (n <= 0) {
            eventCount = null;
            timeOnUs = null;
            timeOffUs = null;
            voxel = null;
            return;
        }
        eventCount = new int[n];
        timeOnUs = new int[n];
        timeOffUs = new int[n];
        voxel = new float[voxelBins * n];
        clear();
    }

    public synchronized void clear() {
        if (eventCount != null) {
            java.util.Arrays.fill(eventCount, 0);
            java.util.Arrays.fill(timeOnUs, 0);
            java.util.Arrays.fill(timeOffUs, 0);
            java.util.Arrays.fill(voxel, 0f);
        }
        sliceEvents.clear();
        accumulated = 0;
        started = false;
        firstTimestampUs = 0;
        lastTimestampUs = 0;
    }

    /**
     * Adds one polarity event. Returns true if this event completed a slice
     * (caller should publish then {@link #clear()}).
     */
    public synchronized boolean addEvent(int x, int y, boolean on, int timestampUs) {
        if (eventCount == null || width <= 0 || height <= 0) {
            return false;
        }
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }
        if (!started) {
            started = true;
            firstTimestampUs = timestampUs;
        }
        lastTimestampUs = timestampUs;
        int i = index(x, y);
        int signed = on ? 1 : -1;
        int sum = eventCount[i] + signed;
        if (sum > grayScale) {
            sum = grayScale;
        } else if (sum < -grayScale) {
            sum = -grayScale;
        }
        eventCount[i] = sum;
        if (on) {
            timeOnUs[i] = timestampUs;
        } else {
            timeOffUs[i] = timestampUs;
        }
        sliceEvents.add(new int[]{x, y, timestampUs, on ? 1 : 0});
        accumulated++;
        if (!sliceComplete()) {
            return false;
        }
        rasterizeVoxel();
        return true;
    }

    /**
     * Zhu / EV-FlowNet bilinear interpolation in time over {@code voxelBins}
     * using the completed slice interval {@code [first, last]}.
     */
    void rasterizeVoxel() {
        if (voxel == null || voxelBins < 2) {
            return;
        }
        java.util.Arrays.fill(voxel, 0f);
        int span = lastTimestampUs - firstTimestampUs;
        if (span < 1) {
            span = 1;
        }
        int n = width * height;
        for (int[] e : sliceEvents) {
            int x = e[0], y = e[1], ts = e[2], signed = e[3] == 1 ? 1 : -1;
            float tn = (voxelBins - 1) * ((ts - firstTimestampUs) / (float) span);
            if (tn < 0) {
                tn = 0;
            } else if (tn > voxelBins - 1) {
                tn = voxelBins - 1;
            }
            int t0 = (int) Math.floor(tn);
            int t1 = Math.min(voxelBins - 1, t0 + 1);
            float frac = tn - t0;
            int pix = index(x, y);
            voxel[t0 * n + pix] += signed * (1f - frac);
            if (t1 != t0) {
                voxel[t1 * n + pix] += signed * frac;
            }
        }
    }

    private boolean sliceComplete() {
        if (!started) {
            return false;
        }
        if (timeSliceMethod == TimeSliceMethod.EventCount) {
            return accumulated >= eventsPerFrame;
        }
        int dt = lastTimestampUs - firstTimestampUs;
        if (dt < 0) {
            dt += Integer.MAX_VALUE; // wrap
        }
        return dt >= timeDurationUs;
    }

    private int index(int x, int y) {
        return x + width * y;
    }

    private int rowSourceY(int outRow) {
        return flipY ? (height - 1 - outRow) : outRow;
    }

    /**
     * ROS2 scientific tensors: signed {@code 32FC1} (counts, µs, or voxel weights).
     */
    public synchronized List<EncodedImage> encodeRos() {
        if (eventCount == null) {
            return Collections.emptyList();
        }
        switch (frameType) {
            case TimestampImages:
                List<EncodedImage> ts = new ArrayList<>(2);
                ts.add(encodeFloat32(TOPIC_TIME_ON, relativeUs(timeOnUs), 1, false));
                ts.add(encodeFloat32(TOPIC_TIME_OFF, relativeUs(timeOffUs), 1, false));
                return ts;
            case VoxelGrid:
                return Collections.singletonList(encodeFloat32(TOPIC_VOXEL, voxel, voxelBins, false));
            case EventCountHistogram:
            default:
                return Collections.singletonList(encodeIntAsFloat32(TOPIC_EVENT_COUNT, eventCount, false));
        }
    }

    /**
     * Foxglove Image-panel encodings ({@code 32FC1} 0–1, {@code rgb8}, {@code mono8}).
     */
    public synchronized List<EncodedImage> encodeFoxglove(FoxgloveFrameEncoding encoding) {
        if (eventCount == null) {
            return Collections.emptyList();
        }
        FoxgloveFrameEncoding enc = encoding == null ? FoxgloveFrameEncoding.Float32 : encoding;
        switch (frameType) {
            case TimestampImages:
                return encodeTimestampFoxglove(enc);
            case VoxelGrid:
                return encodeVoxelFoxglove(enc);
            case EventCountHistogram:
            default:
                return encodeEventCountFoxglove(enc);
        }
    }

    private List<EncodedImage> encodeEventCountFoxglove(FoxgloveFrameEncoding enc) {
        switch (enc) {
            case Rgb8:
                return Collections.singletonList(encodeEventCountRgb8());
            case Mono8:
                return Collections.singletonList(encodeSignedMono8(TOPIC_EVENT_COUNT, eventCount));
            case Float32:
            default:
                return Collections.singletonList(encodeIntAsFloat32(TOPIC_EVENT_COUNT, eventCount, true));
        }
    }

    private List<EncodedImage> encodeTimestampFoxglove(FoxgloveFrameEncoding enc) {
        float[] on = relativeUnit(timeOnUs);
        float[] off = relativeUnit(timeOffUs);
        List<EncodedImage> out = new ArrayList<>(2);
        if (enc == FoxgloveFrameEncoding.Mono8) {
            out.add(encodeUnitMono8(TOPIC_TIME_ON, on));
            out.add(encodeUnitMono8(TOPIC_TIME_OFF, off));
        } else {
            out.add(encodeFloat32(TOPIC_TIME_ON, on, 1, true));
            out.add(encodeFloat32(TOPIC_TIME_OFF, off, 1, true));
        }
        return out;
    }

    private List<EncodedImage> encodeVoxelFoxglove(FoxgloveFrameEncoding enc) {
        if (enc == FoxgloveFrameEncoding.Rgb8 && voxelBins >= 3) {
            return Collections.singletonList(encodeVoxelRgb8());
        }
        if (enc == FoxgloveFrameEncoding.Mono8) {
            return Collections.singletonList(encodeSignedMono8Stacked(TOPIC_VOXEL, voxel, voxelBins));
        }
        return Collections.singletonList(encodeFloat32(TOPIC_VOXEL, voxel, voxelBins, true));
    }

    float[] relativeUs(int[] lastTs) {
        float[] out = new float[lastTs.length];
        for (int i = 0; i < lastTs.length; i++) {
            if (lastTs[i] == 0 && !started) {
                out[i] = 0;
            } else if (lastTs[i] == 0) {
                out[i] = 0;
            } else {
                out[i] = lastTs[i] - firstTimestampUs;
            }
        }
        return out;
    }

    float[] relativeUnit(int[] lastTs) {
        int span = Math.max(1, lastTimestampUs - firstTimestampUs);
        if (timeSliceMethod == TimeSliceMethod.TimeIntervalUs) {
            span = Math.max(1, timeDurationUs);
        }
        float[] out = new float[lastTs.length];
        for (int i = 0; i < lastTs.length; i++) {
            if (lastTs[i] == 0) {
                out[i] = 0;
            } else {
                float u = (lastTs[i] - firstTimestampUs) / (float) span;
                if (u < 0) {
                    u = 0;
                } else if (u > 1) {
                    u = 1;
                }
                out[i] = u;
            }
        }
        return out;
    }

    EncodedImage encodeIntAsFloat32(String topic, int[] src, boolean unitRange) {
        int n = width * height;
        byte[] data = new byte[n * 4];
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        float gs = grayScale;
        for (int y = 0; y < height; y++) {
            int sy = rowSourceY(y);
            for (int x = 0; x < width; x++) {
                float v = src[index(x, sy)];
                if (unitRange) {
                    v = clamp01(0.5f + 0.5f * v / gs);
                }
                buf.putFloat(v);
            }
        }
        return new EncodedImage(topic, width, height, width * 4, "32FC1", data);
    }

    EncodedImage encodeFloat32(String topic, float[] src, int bins, boolean unitRange) {
        int n = width * height;
        int rows = height * bins;
        byte[] data = new byte[bins * n * 4];
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        float gs = grayScale;
        for (int b = 0; b < bins; b++) {
            int base = b * n;
            for (int y = 0; y < height; y++) {
                int sy = rowSourceY(y);
                for (int x = 0; x < width; x++) {
                    float v = src[base + index(x, sy)];
                    if (unitRange) {
                        v = clamp01(0.5f + 0.5f * v / gs);
                    }
                    buf.putFloat(v);
                }
            }
        }
        return new EncodedImage(topic, width, rows, width * 4, "32FC1", data);
    }

    EncodedImage encodeEventCountRgb8() {
        byte[] data = new byte[width * height * 3];
        int p = 0;
        float gs = grayScale;
        for (int y = 0; y < height; y++) {
            int sy = rowSourceY(y);
            for (int x = 0; x < width; x++) {
                int c = eventCount[index(x, sy)];
                int r = 0, g = 0, b = 0;
                if (c > 0) {
                    r = clamp255(Math.round(255f * c / gs));
                } else if (c < 0) {
                    g = clamp255(Math.round(255f * (-c) / gs));
                }
                data[p++] = (byte) r;
                data[p++] = (byte) g;
                data[p++] = (byte) b;
            }
        }
        return new EncodedImage(TOPIC_EVENT_COUNT, width, height, width * 3, "rgb8", data);
    }

    EncodedImage encodeVoxelRgb8() {
        byte[] data = new byte[width * height * 3];
        int n = width * height;
        int p = 0;
        float gs = grayScale;
        for (int y = 0; y < height; y++) {
            int sy = rowSourceY(y);
            for (int x = 0; x < width; x++) {
                int pix = index(x, sy);
                data[p++] = (byte) signedToByte(voxel[pix], gs);
                data[p++] = (byte) signedToByte(voxel[n + pix], gs);
                data[p++] = (byte) signedToByte(voxel[2 * n + pix], gs);
            }
        }
        return new EncodedImage(TOPIC_VOXEL, width, height, width * 3, "rgb8", data);
    }

    EncodedImage encodeSignedMono8(String topic, int[] src) {
        byte[] data = new byte[width * height];
        int p = 0;
        float gs = grayScale;
        for (int y = 0; y < height; y++) {
            int sy = rowSourceY(y);
            for (int x = 0; x < width; x++) {
                data[p++] = (byte) signedToByte(src[index(x, sy)], gs);
            }
        }
        return new EncodedImage(topic, width, height, width, "mono8", data);
    }

    EncodedImage encodeSignedMono8Stacked(String topic, float[] src, int bins) {
        int n = width * height;
        byte[] data = new byte[bins * n];
        int p = 0;
        float gs = grayScale;
        for (int b = 0; b < bins; b++) {
            int base = b * n;
            for (int y = 0; y < height; y++) {
                int sy = rowSourceY(y);
                for (int x = 0; x < width; x++) {
                    data[p++] = (byte) signedToByte(src[base + index(x, sy)], gs);
                }
            }
        }
        return new EncodedImage(topic, width, height * bins, width, "mono8", data);
    }

    EncodedImage encodeUnitMono8(String topic, float[] unit) {
        byte[] data = new byte[width * height];
        int p = 0;
        for (int y = 0; y < height; y++) {
            int sy = rowSourceY(y);
            for (int x = 0; x < width; x++) {
                data[p++] = (byte) clamp255(Math.round(255f * unit[index(x, sy)]));
            }
        }
        return new EncodedImage(topic, width, height, width, "mono8", data);
    }

    static float clamp01(float v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
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

    static int signedToByte(float v, float grayScale) {
        return clamp255(Math.round(128f + 127f * v / grayScale));
    }

    /** Visible for tests. */
    synchronized int[] copyEventCount() {
        return eventCount == null ? new int[0] : eventCount.clone();
    }

    synchronized float[] copyVoxel() {
        return voxel == null ? new float[0] : voxel.clone();
    }
}
