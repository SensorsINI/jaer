/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.opencv;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.ros2.EncodedImage;
import net.sf.jaer.eventio.ros2.Ros2FrameAssembler;
import net.sf.jaer.eventio.ros2.Ros2FrameAssembler.FoxgloveFrameEncoding;
import net.sf.jaer.eventio.ros2.Ros2FrameAssembler.TimeSliceMethod;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.Chip2DRenderer;

/**
 * Publishes DVS / Davis frames as HTTP MJPEG for stock OpenCV
 * {@code VideoCapture}, and optionally to Linux v4l2loopback.
 * <p>
 * Open from File → Remote → OpenCV camera output…
 */
@Description("Publishes DVS/Davis frames as HTTP MJPEG for OpenCV VideoCapture (File → Remote)")
@Help("""
<html>
<body>
<h2>OpenCVOutput</h2>
<p>Assembles event-camera or Davis APS frames and serves them as an
<b>HTTP Motion JPEG</b> stream so stock OpenCV can open the sensor like a
mono or color camera. Also under File → Remote → <b>OpenCV camera output…</b>.</p>
<h3>OpenCV (any OS)</h3>
<pre>
import cv2
cap = cv2.VideoCapture("http://127.0.0.1:8090/video.mjpg", cv2.CAP_FFMPEG)
ok, frame = cap.read()
</pre>
<pre>
cv::VideoCapture cap("http://127.0.0.1:8090/video.mjpg", cv::CAP_FFMPEG);
</pre>
<p>Subset: <code>open</code> / <code>read</code> / <code>release</code> and
<code>CAP_PROP_FRAME_WIDTH</code> / <code>HEIGHT</code> after the first frame.
Exposure and biases stay in this dialog. Browser preview:
<code>http://127.0.0.1:8090/</code> · snapshot <code>/snapshot.jpg</code>.</p>
<h3>frameSource</h3>
<ul>
<li><b>Auto</b> — Davis / HVS <code>FramePacket</code> when present, else DVS event-count.</li>
<li><b>ApsFrames</b> — intensity only.</li>
<li><b>DvsEventCount</b> — assembled histogram (mid-gray = zero).</li>
<li><b>RenderedPixmap</b> — AEViewer pixmap (what you see).</li>
</ul>
<p>DVS is Mono8; Davis gray is Mono8; Davis RGB is BGR8 (OpenCV color).
<code>flipY</code> default on (OpenCV row 0 = top).</p>
<h3>Linux v4l2loopback</h3>
<p>Optional <code>publishV4l2</code> writes the same raw frames to
<code>/dev/video10</code> so Cheese, Zoom, and
<code>cv2.VideoCapture(10, cv2.CAP_V4L2)</code> see a real camera.
jAER does not load the module:</p>
<pre>
sudo apt install v4l2loopback-dkms
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label=jAER exclusive_caps=1
v4l2-ctl --list-devices
</pre>
<p><b>skipChipRendering</b> skips OpenGL pixmap updates while still publishing.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class OpenCVOutput extends EventFilter2D {

    public static final String GROUP_SINKS = "Sinks";
    public static final String GROUP_HTTP = "HTTP";
    public static final String GROUP_FRAME = "Frame";
    public static final String GROUP_SLICE = "Slice";
    public static final String GROUP_V4L2 = "V4L2";

    public enum FrameSource {
        Auto,
        ApsFrames,
        DvsEventCount,
        RenderedPixmap
    }

    private final Ros2FrameAssembler assembler = new Ros2FrameAssembler();
    private final ArrayBlockingQueue<OpenCvRawFrame> publishQueue = new ArrayBlockingQueue<>(2);
    private final AtomicReference<OpenCvRawFrame> latestRaw = new AtomicReference<>();
    private MjpegHttpServer httpServer;
    private V4l2LoopbackSink v4l2Sink;
    private Thread publishThread;
    private volatile boolean publishThreadStop;

    private FrameSource frameSource = FrameSource.valueOf(getString("frameSource", FrameSource.Auto.name()));
    private String bindAddress = getString("bindAddress", "127.0.0.1");
    private int httpPort = getInt("httpPort", 8090);
    private float jpegQuality = getFloat("jpegQuality", 0.8f);
    private int outputImageWidth = getInt("outputImageWidth", 0);
    private int outputImageHeight = getInt("outputImageHeight", 0);
    private int grayScale = getInt("grayScale", 2);
    private boolean flipY = getBoolean("flipY", true);
    private TimeSliceMethod timeSliceMethod = TimeSliceMethod.valueOf(
            getString("timeSliceMethod", TimeSliceMethod.TimeIntervalUs.name()));
    private int eventsPerFrame = getInt("eventsPerFrame", 10000);
    private int timeDurationUs = getInt("timeDurationUs", 10000);
    private boolean skipChipRendering = getBoolean("skipChipRendering", false);
    private boolean publishV4l2 = getBoolean("publishV4l2", false);
    private String v4l2Device = getString("v4l2Device", "/dev/video10");
    private int v4l2OutputWidth = getInt("v4l2OutputWidth", 0);
    private int v4l2OutputHeight = getInt("v4l2OutputHeight", 0);

    private volatile boolean seenApsFrame;
    private volatile double publishHz;
    private volatile String lastError;
    private volatile long publishedFrameCount;
    private long hzWindowStartNs;
    private int hzCount;

    public OpenCVOutput(AEChip chip) {
        super(chip);
        setPropertyTooltip(GROUP_SINKS, "skipChipRendering", "Skip AEViewer OpenGL pixmap while still publishing frames");
        setPropertyTooltip(GROUP_HTTP, "bindAddress", "Bind address; 127.0.0.1 local, 0.0.0.0 LAN");
        setPropertyTooltip(GROUP_HTTP, "httpPort", "HTTP port for /video.mjpg (default 8090)");
        setPropertyTooltip(GROUP_HTTP, "jpegQuality", "JPEG quality 0.05–1.0");
        setPropertyTooltip(GROUP_FRAME, "frameSource", "Auto: APS FramePacket when present, else DVS histogram");
        setPropertyTooltip(GROUP_FRAME, "outputImageWidth", "Output width; 0 = chip / frame size");
        setPropertyTooltip(GROUP_FRAME, "outputImageHeight", "Output height; 0 = chip / frame size");
        setPropertyTooltip(GROUP_FRAME, "grayScale", "Full-scale signed DVS event count (mid-gray = 0)");
        setPropertyTooltip(GROUP_FRAME, "flipY", "Row 0 is sensor top (OpenCV +y down)");
        setPropertyTooltip(GROUP_SLICE, "timeSliceMethod", "Close a DVS frame after N events or after a time interval");
        setPropertyTooltip(GROUP_SLICE, "eventsPerFrame", "Events per frame when timeSliceMethod is EventCount");
        setPropertyTooltip(GROUP_SLICE, "timeDurationUs", "Slice duration in microseconds when TimeIntervalUs");
        setPropertyTooltip(GROUP_V4L2, "publishV4l2", "Linux: write YUYV to v4l2loopback device (else no-op)");
        setPropertyTooltip(GROUP_V4L2, "v4l2Device", "v4l2loopback node, e.g. /dev/video10");
        setPropertyTooltip(GROUP_V4L2, "v4l2OutputWidth", "v4l2 width; 0 = raw frame width (even)");
        setPropertyTooltip(GROUP_V4L2, "v4l2OutputHeight", "v4l2 height; 0 = raw frame height");
        if (!V4l2LoopbackSink.isLinux()) {
            hideProperty("publishV4l2");
            hideProperty("v4l2Device");
            hideProperty("v4l2OutputWidth");
            hideProperty("v4l2OutputHeight");
        }
        applyAssemblerSettings();
    }

    public static OpenCVOutput find(AEChip chip) {
        if (chip == null || chip.getFilterChain() == null) {
            return null;
        }
        return (OpenCVOutput) chip.getFilterChain().findFilter(OpenCVOutput.class);
    }

    public static void ensurePresent(AEChip chip) {
        if (chip == null) {
            return;
        }
        FilterChain chain = chip.getFilterChain();
        if (chain == null || chain.findFilter(OpenCVOutput.class) != null) {
            return;
        }
        try {
            OpenCVOutput f = new OpenCVOutput(chip);
            chain.add(f);
            f.initFilter();
            f.setPreferredEnabledState();
            ArrayList<String> names = new ArrayList<>();
            for (EventFilter2D x : chain) {
                names.add(x.getClass().getName());
            }
            chain.storePreferredFiltersForChip(names);
            log.info("Appended OpenCVOutput to filter chain for " + chip.getClass().getSimpleName());
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not add OpenCVOutput: " + e, e);
        }
    }

    private void applyAssemblerSettings() {
        assembler.setTimeSliceMethod(timeSliceMethod);
        assembler.setGrayScale(grayScale);
        assembler.setEventsPerFrame(eventsPerFrame);
        assembler.setTimeDurationUs(timeDurationUs);
        assembler.setFlipY(flipY);
        int w = outputImageWidth > 0 ? outputImageWidth : Math.max(1, chip.getSizeX());
        int h = outputImageHeight > 0 ? outputImageHeight : Math.max(1, chip.getSizeY());
        assembler.setSize(w, h);
    }

    @Override
    public boolean accepts(PacketType type) {
        return type == PacketType.POLARITY || type == PacketType.FRAME;
    }

    @Override
    public synchronized void setFilterEnabled(boolean enabled) {
        super.setFilterEnabled(enabled);
        if (enabled) {
            startSinks();
        } else {
            stopSinks();
        }
    }

    @Override
    public void initFilter() {
        applyAssemblerSettings();
        if (isFilterEnabled()) {
            startSinks();
        }
    }

    @Override
    public void resetFilter() {
        assembler.clear();
        seenApsFrame = false;
        publishedFrameCount = 0;
        hzCount = 0;
        hzWindowStartNs = 0;
        publishHz = 0;
    }

    @Override
    public synchronized void cleanup() {
        stopSinks();
        super.cleanup();
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        return processPolarity(in);
    }

    @Override
    public EventPacket<? extends BasicEvent> processPolarity(EventPacket<? extends BasicEvent> in) {
        if (!isFilterEnabled() || in == null) {
            return in;
        }
        if (!wantDvsAssembly()) {
            return in;
        }
        applyAssemblerSettings();
        for (BasicEvent e : in) {
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            if (!(e instanceof PolarityEvent)) {
                continue;
            }
            if (e instanceof ApsDvsEvent && !((ApsDvsEvent) e).isDVSEvent()) {
                continue;
            }
            PolarityEvent pe = (PolarityEvent) e;
            int x = pe.x;
            int y = pe.y;
            int srcW = chip.getSizeX();
            int srcH = chip.getSizeY();
            int dw = assembler.getWidth();
            int dh = assembler.getHeight();
            if (srcW != dw && srcW > 0) {
                x = (int) Math.floor((x / (float) srcW) * dw);
            }
            if (srcH != dh && srcH > 0) {
                y = (int) Math.floor((y / (float) srcH) * dh);
            }
            boolean on = pe.polarity == PolarityEvent.Polarity.On;
            if (assembler.addEvent(x, y, on, pe.timestamp)) {
                OpenCvRawFrame raw = frameSource == FrameSource.RenderedPixmap
                        ? copyRenderedPixmap()
                        : encodeDvsMono8();
                if (raw != null) {
                    enqueue(raw);
                }
                assembler.clear();
            }
        }
        return in;
    }

    @Override
    public FramePacket processFrame(FramePacket in) {
        if (!isFilterEnabled() || in == null || in.isEmpty()) {
            return in;
        }
        if (frameSource == FrameSource.DvsEventCount) {
            return in;
        }
        if (frameSource == FrameSource.RenderedPixmap) {
            OpenCvRawFrame raw = copyRenderedPixmap();
            if (raw != null) {
                enqueue(raw);
            }
            return in;
        }
        OpenCvRawFrame raw = copyFramePacket(in);
        if (raw != null) {
            seenApsFrame = true;
            enqueue(raw);
        }
        return in;
    }

    private boolean wantDvsAssembly() {
        if (frameSource == FrameSource.ApsFrames) {
            return false;
        }
        if (frameSource == FrameSource.DvsEventCount || frameSource == FrameSource.RenderedPixmap) {
            return true;
        }
        return !seenApsFrame;
    }

    private OpenCvRawFrame encodeDvsMono8() {
        List<EncodedImage> imgs = assembler.encodeFoxglove(FoxgloveFrameEncoding.Mono8);
        if (imgs == null || imgs.isEmpty()) {
            return null;
        }
        EncodedImage img = imgs.get(0);
        return new OpenCvRawFrame(img.width, img.height, 1, img.data);
    }

    private OpenCvRawFrame copyFramePacket(FramePacket frame) {
        int w = frame.getWidth();
        int h = frame.getHeight();
        short[] pix = frame.getPixels();
        int ch = Math.max(1, frame.channelsPerPixel());
        if (w <= 0 || h <= 0 || pix == null || pix.length < w * h * ch) {
            return null;
        }
        int maxv = 1;
        for (int i = 0; i < pix.length; i++) {
            int v = pix[i] & 0xffff;
            if (v > maxv) {
                maxv = v;
            }
        }
        float scale = maxv <= 255 ? 255f : (maxv <= 1023 ? 1023f : 65535f);
        boolean color = ch >= 3;
        byte[] data = new byte[w * h * (color ? 3 : 1)];
        int p = 0;
        for (int y = 0; y < h; y++) {
            int srcY = flipY ? (h - 1 - y) : y;
            int row = srcY * w;
            for (int x = 0; x < w; x++) {
                int base = (row + x) * ch;
                if (color) {
                    int r = OpenCvRawFrame.clamp255(Math.round(255f * (pix[base] & 0xffff) / scale));
                    int g = OpenCvRawFrame.clamp255(Math.round(255f * (pix[base + 1] & 0xffff) / scale));
                    int b = OpenCvRawFrame.clamp255(Math.round(255f * (pix[base + 2] & 0xffff) / scale));
                    data[p++] = (byte) b;
                    data[p++] = (byte) g;
                    data[p++] = (byte) r;
                } else {
                    data[p++] = (byte) OpenCvRawFrame.clamp255(
                            Math.round(255f * (pix[base] & 0xffff) / scale));
                }
            }
        }
        OpenCvRawFrame raw = new OpenCvRawFrame(w, h, color ? 3 : 1, data);
        return applyOutputSize(raw);
    }

    private OpenCvRawFrame copyRenderedPixmap() {
        Chip2DRenderer renderer = chip.getRenderer();
        if (renderer == null) {
            return null;
        }
        int w = chip.getSizeX();
        int h = chip.getSizeY();
        if (w <= 0 || h <= 0) {
            return null;
        }
        byte[] bgr;
        synchronized (renderer) {
            float[] pixmap = renderer.getPixmapArray();
            if (pixmap == null) {
                return null;
            }
            bgr = new byte[w * h * 3];
            int p = 0;
            for (int y = 0; y < h; y++) {
                int srcY = flipY ? (h - 1 - y) : y;
                for (int x = 0; x < w; x++) {
                    int pi = renderer.getPixMapIndex(x, srcY);
                    if (pi < 0 || (pi + 2) >= pixmap.length) {
                        p += 3;
                        continue;
                    }
                    bgr[p++] = (byte) OpenCvRawFrame.clamp255(Math.round(255f * pixmap[pi + 2]));
                    bgr[p++] = (byte) OpenCvRawFrame.clamp255(Math.round(255f * pixmap[pi + 1]));
                    bgr[p++] = (byte) OpenCvRawFrame.clamp255(Math.round(255f * pixmap[pi]));
                }
            }
        }
        return applyOutputSize(new OpenCvRawFrame(w, h, 3, bgr));
    }

    private OpenCvRawFrame applyOutputSize(OpenCvRawFrame raw) {
        int dw = outputImageWidth > 0 ? outputImageWidth : raw.width;
        int dh = outputImageHeight > 0 ? outputImageHeight : raw.height;
        return raw.scaled(dw, dh);
    }

    private void enqueue(OpenCvRawFrame raw) {
        latestRaw.set(raw);
        if (!publishQueue.offer(raw)) {
            publishQueue.poll();
            publishQueue.offer(raw);
        }
    }

    private synchronized void startSinks() {
        startHttp();
        startV4l2();
        startPublishThread();
    }

    private void startHttp() {
        try {
            if (httpServer != null) {
                return;
            }
            httpServer = new MjpegHttpServer(bindAddress, httpPort);
            httpServer.start();
            lastError = null;
        } catch (Exception e) {
            lastError = e.toString();
            log.log(Level.WARNING, "OpenCV MJPEG server failed: " + e, e);
            httpServer = null;
        }
    }

    private void stopHttp() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    private void startV4l2() {
        if (!publishV4l2) {
            stopV4l2();
            return;
        }
        if (!V4l2LoopbackSink.isLinux()) {
            lastError = "v4l2loopback is Linux-only";
            log.warning(lastError);
            return;
        }
        if (v4l2Sink != null && v4l2Device.equals(v4l2Sink.getDevice())) {
            return;
        }
        stopV4l2();
        v4l2Sink = new V4l2LoopbackSink(v4l2Device);
    }

    private void stopV4l2() {
        if (v4l2Sink != null) {
            v4l2Sink.close();
            v4l2Sink = null;
        }
    }

    private void startPublishThread() {
        if (publishThread != null && publishThread.isAlive()) {
            return;
        }
        publishThreadStop = false;
        publishThread = new Thread(this::publishLoop, "OpenCVOutput-publish");
        publishThread.setDaemon(true);
        publishThread.start();
    }

    private void stopSinks() {
        publishThreadStop = true;
        if (publishThread != null) {
            publishThread.interrupt();
            publishThread = null;
        }
        publishQueue.clear();
        stopHttp();
        stopV4l2();
        publishHz = 0;
        publishedFrameCount = 0;
        hzCount = 0;
        hzWindowStartNs = 0;
    }

    private void publishLoop() {
        while (!publishThreadStop) {
            try {
                OpenCvRawFrame raw = publishQueue.poll(200, TimeUnit.MILLISECONDS);
                if (raw == null) {
                    continue;
                }
                byte[] jpeg = raw.toJpeg(jpegQuality);
                MjpegHttpServer http = httpServer;
                if (http != null) {
                    http.setLatestJpeg(jpeg);
                }
                V4l2LoopbackSink v4l = v4l2Sink;
                if (publishV4l2 && v4l != null) {
                    v4l.write(raw, v4l2OutputWidth, v4l2OutputHeight);
                    if (v4l.getLastError() != null) {
                        lastError = v4l.getLastError();
                    }
                }
                notePublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                lastError = e.toString();
                log.log(Level.WARNING, "OpenCVOutput publish: " + e, e);
            }
        }
    }

    private void notePublished() {
        publishedFrameCount++;
        hzCount++;
        long now = System.nanoTime();
        if (hzWindowStartNs == 0) {
            hzWindowStartNs = now;
        }
        double dt = (now - hzWindowStartNs) / 1e9;
        if (dt >= 1) {
            publishHz = hzCount / dt;
            hzCount = 0;
            hzWindowStartNs = now;
        }
    }

    public String getOverlayText() {
        if (!isFilterEnabled()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(96);
        sb.append("OpenCV MJPEG  ");
        sb.append(String.format("%.1f Hz", publishHz));
        sb.append('\n').append(getOpenCvClientUrl());
        if (publishV4l2) {
            sb.append('\n').append(v4l2Device);
            if (v4l2Sink != null && v4l2Sink.isOpen()) {
                sb.append(" open");
            }
        }
        if (lastError != null && !lastError.isEmpty()) {
            sb.append("\nerr: ").append(lastError);
        }
        return sb.toString();
    }

    public String getOpenCvClientUrl() {
        return "http://" + MjpegHttpServer.clientHost(bindAddress) + ":" + httpPort + "/video.mjpg";
    }

    /** HTML preview page ({@code /}) — better in a browser than raw {@code /video.mjpg}. */
    public String getOpenCvPageUrl() {
        return "http://" + MjpegHttpServer.clientHost(bindAddress) + ":" + httpPort + "/";
    }

    public double getPublishHz() {
        return publishHz;
    }

    public String getLastError() {
        return lastError;
    }

    public FrameSource getFrameSource() {
        return frameSource;
    }

    public void setFrameSource(FrameSource frameSource) {
        FrameSource old = this.frameSource;
        this.frameSource = frameSource;
        putString("frameSource", frameSource.name());
        if (frameSource != FrameSource.Auto) {
            seenApsFrame = false;
        }
        getSupport().firePropertyChange("frameSource", old, frameSource);
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public synchronized void setBindAddress(String bindAddress) {
        String old = this.bindAddress;
        this.bindAddress = bindAddress;
        putString("bindAddress", bindAddress);
        if (isFilterEnabled() && !old.equals(bindAddress)) {
            stopHttp();
            startHttp();
        }
        getSupport().firePropertyChange("bindAddress", old, bindAddress);
    }

    public int getHttpPort() {
        return httpPort;
    }

    public synchronized void setHttpPort(int httpPort) {
        int old = this.httpPort;
        this.httpPort = Math.max(1, httpPort);
        putInt("httpPort", this.httpPort);
        if (isFilterEnabled() && old != this.httpPort) {
            stopHttp();
            startHttp();
        }
        getSupport().firePropertyChange("httpPort", old, this.httpPort);
    }

    public float getJpegQuality() {
        return jpegQuality;
    }

    public void setJpegQuality(float jpegQuality) {
        float old = this.jpegQuality;
        this.jpegQuality = OpenCvRawFrame.clamp01(jpegQuality);
        putFloat("jpegQuality", this.jpegQuality);
        getSupport().firePropertyChange("jpegQuality", old, this.jpegQuality);
    }

    public int getOutputImageWidth() {
        return outputImageWidth;
    }

    public void setOutputImageWidth(int outputImageWidth) {
        int old = this.outputImageWidth;
        this.outputImageWidth = Math.max(0, outputImageWidth);
        putInt("outputImageWidth", this.outputImageWidth);
        applyAssemblerSettings();
        getSupport().firePropertyChange("outputImageWidth", old, this.outputImageWidth);
    }

    public int getOutputImageHeight() {
        return outputImageHeight;
    }

    public void setOutputImageHeight(int outputImageHeight) {
        int old = this.outputImageHeight;
        this.outputImageHeight = Math.max(0, outputImageHeight);
        putInt("outputImageHeight", this.outputImageHeight);
        applyAssemblerSettings();
        getSupport().firePropertyChange("outputImageHeight", old, this.outputImageHeight);
    }

    public int getGrayScale() {
        return grayScale;
    }

    public void setGrayScale(int grayScale) {
        int old = this.grayScale;
        this.grayScale = Math.max(1, grayScale);
        putInt("grayScale", this.grayScale);
        assembler.setGrayScale(this.grayScale);
        getSupport().firePropertyChange("grayScale", old, this.grayScale);
    }

    public boolean isFlipY() {
        return flipY;
    }

    public void setFlipY(boolean flipY) {
        boolean old = this.flipY;
        this.flipY = flipY;
        putBoolean("flipY", flipY);
        assembler.setFlipY(flipY);
        getSupport().firePropertyChange("flipY", old, flipY);
    }

    public TimeSliceMethod getTimeSliceMethod() {
        return timeSliceMethod;
    }

    public void setTimeSliceMethod(TimeSliceMethod timeSliceMethod) {
        TimeSliceMethod old = this.timeSliceMethod;
        this.timeSliceMethod = timeSliceMethod;
        putString("timeSliceMethod", timeSliceMethod.name());
        assembler.setTimeSliceMethod(timeSliceMethod);
        getSupport().firePropertyChange("timeSliceMethod", old, timeSliceMethod);
    }

    public int getEventsPerFrame() {
        return eventsPerFrame;
    }

    public void setEventsPerFrame(int eventsPerFrame) {
        int old = this.eventsPerFrame;
        this.eventsPerFrame = Math.max(1, eventsPerFrame);
        putInt("eventsPerFrame", this.eventsPerFrame);
        assembler.setEventsPerFrame(this.eventsPerFrame);
        getSupport().firePropertyChange("eventsPerFrame", old, this.eventsPerFrame);
    }

    public int getTimeDurationUs() {
        return timeDurationUs;
    }

    public void setTimeDurationUs(int timeDurationUs) {
        int old = this.timeDurationUs;
        this.timeDurationUs = Math.max(1, timeDurationUs);
        putInt("timeDurationUs", this.timeDurationUs);
        assembler.setTimeDurationUs(this.timeDurationUs);
        getSupport().firePropertyChange("timeDurationUs", old, this.timeDurationUs);
    }

    public boolean isSkipChipRendering() {
        return skipChipRendering;
    }

    public void setSkipChipRendering(boolean skipChipRendering) {
        boolean old = this.skipChipRendering;
        this.skipChipRendering = skipChipRendering;
        putBoolean("skipChipRendering", skipChipRendering);
        getSupport().firePropertyChange("skipChipRendering", old, skipChipRendering);
    }

    public boolean isPublishV4l2() {
        return publishV4l2;
    }

    public synchronized void setPublishV4l2(boolean publishV4l2) {
        boolean old = this.publishV4l2;
        this.publishV4l2 = publishV4l2;
        putBoolean("publishV4l2", publishV4l2);
        if (isFilterEnabled() && old != publishV4l2) {
            startV4l2();
        }
        getSupport().firePropertyChange("publishV4l2", old, publishV4l2);
    }

    public String getV4l2Device() {
        return v4l2Device;
    }

    public synchronized void setV4l2Device(String v4l2Device) {
        String old = this.v4l2Device;
        this.v4l2Device = v4l2Device == null || v4l2Device.isBlank() ? "/dev/video10" : v4l2Device;
        putString("v4l2Device", this.v4l2Device);
        if (isFilterEnabled() && publishV4l2 && !this.v4l2Device.equals(old)) {
            stopV4l2();
            startV4l2();
        }
        getSupport().firePropertyChange("v4l2Device", old, this.v4l2Device);
    }

    public int getV4l2OutputWidth() {
        return v4l2OutputWidth;
    }

    public void setV4l2OutputWidth(int v4l2OutputWidth) {
        int old = this.v4l2OutputWidth;
        this.v4l2OutputWidth = Math.max(0, v4l2OutputWidth);
        putInt("v4l2OutputWidth", this.v4l2OutputWidth);
        getSupport().firePropertyChange("v4l2OutputWidth", old, this.v4l2OutputWidth);
    }

    public int getV4l2OutputHeight() {
        return v4l2OutputHeight;
    }

    public void setV4l2OutputHeight(int v4l2OutputHeight) {
        int old = this.v4l2OutputHeight;
        this.v4l2OutputHeight = Math.max(0, v4l2OutputHeight);
        putInt("v4l2OutputHeight", this.v4l2OutputHeight);
        getSupport().firePropertyChange("v4l2OutputHeight", old, this.v4l2OutputHeight);
    }
}
