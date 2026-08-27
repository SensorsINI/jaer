/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.opencv;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

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
import net.sf.jaer.graphics.DavisRenderer;

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
Exposure and biases stay in the OpenCV camera output dialog. Browser preview:
<code>http://127.0.0.1:8090/</code> · snapshot <code>/snapshot.jpg</code>.
Start/Stop is the green/red button; closing the dialog does not stop publishing.</p>
<h3>frameSource</h3>
<ul>
<li><b>Auto</b> / <b>RenderedPixmap</b> — AEViewer pixmap (Davis frames + events in the
current color scheme; DVS-only chips show events). Updates from event slices
even if APS frames are off.</li>
<li><b>ApsFrames</b> — Davis intensity only (no events; freezes if APS is off).</li>
<li><b>DvsEventCount</b> — assembled event histogram (mid-gray = zero), ignore APS.</li>
</ul>
<p>DVS is Mono8; Davis gray is Mono8; Davis RGB is BGR8 (OpenCV color).
<code>flipY</code> default on (OpenCV row 0 = top).
<b>outputSize</b>: Native (sensor), QVGA (320x240), VGA (640x480),
SD (720x480), XGA (1024x768), HD (1280x720). V4L2 requires a standard size.</p>
<h3>Linux v4l2loopback (Cheese, Zoom, Google Meet)</h3>
<p>HTTP MJPEG is not a webcam. The overlay <code>MJPEG …/video.mjpg</code>
does <b>not</b> mean Cheese/Zoom see a camera. Activate V4L2:</p>
<ol>
<li>Set <b>outputSize</b> to a standard size, typically <b>VGA (640x480)</b>
(not Native / Davis 346×260).</li>
<li>Leave <b>v4l2Mjpeg</b> checked (default). Uncheck only for raw YUYV.</li>
<li>Check <b>publishV4l2</b>; leave <code>v4l2Device</code> as
<code>/dev/video10</code>.</li>
<li>Start streaming. Overlay must show
<code>/dev/video10 open MJPEG</code>.
HTTP MJPEG stops unless you also check <b>nonExclusive</b>.</li>
</ol>
<p>jAER does not load the kernel module. <code>modprobe</code> needs
<b>sudo</b> (no printed output means success). Unload first if the module
is already loaded with the wrong device number:</p>
<pre>
sudo apt install v4l2loopback-dkms v4l-utils
sudo modprobe -r v4l2loopback
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label=jAER exclusive_caps=1
ls -l /dev/video10
</pre>
<p>Do <b>not</b> run <code>v4l2-ctl --all</code>, <code>--list-devices</code>,
gst, or Cheese until the overlay shows <code>open</code>. Those QUERYCAP
calls on an idle <code>exclusive_caps=1</code> node make the next
<code>S_FMT</code> fail with EINVAL until you reload the module.
After a successful open, jAER sets v4l2loopback <code>keep_format</code>
so a later jAER restart should not need another <code>modprobe</code>.
If you still see <code>S_FMT</code> errno 22, reload the module, then
start streaming before gst/Cheese/<code>v4l2-ctl</code>.
<code>exclusive_caps=1</code> is required for Chrome/Zoom.</p>
<p>Direct preview (MJPEG):</p>
<pre>
gst-launch-1.0 v4l2src device=/dev/video10 ! jpegdec ! videoconvert ! autovideosink
</pre>
<p>Ubuntu Cheese always uses <code>pipewiresrc</code> and fails with
<code>not-negotiated</code>. Keep jAER streaming and run:</p>
<pre>
bash scripts/cheese-jaer.sh
</pre>
<p>That script omits the PipeWire GStreamer plugin, selects camera
<b>jAER</b> (not the Logitech), and uses <code>v4l2src</code>.
Plain <code>cheese</code> or <code>cheese --device=/dev/video10</code>
will not work. Quit Cheese before trying Zoom.</p>
<p>Zoom / Chrome: after overlay shows <code>open</code>, rescan PipeWire
(Zoom caches the list; quit and reopen Zoom, then pick <b>jAER</b>):</p>
<pre>
systemctl --user restart pipewire-media-session
</pre>
<p>OpenCV can also use <code>cv2.VideoCapture(10, cv2.CAP_V4L2)</code>.</p>
<p><b>skipChipRendering</b> skips OpenGL pixmap updates. Leave it off for
Auto/RenderedPixmap so the published image matches the chip view.</p>
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

    /** Published frame size. Native is the chip; the rest are webcam-standard. */
    public enum OutputSize {
        Native(0, 0),
        QVGA(320, 240),
        VGA(640, 480),
        SD(720, 480),
        XGA(1024, 768),
        HD(1280, 720);

        public final int width;
        public final int height;

        OutputSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public boolean isNative() {
            return width <= 0 || height <= 0;
        }

        public boolean isStandard() {
            return !isNative();
        }

        public static OutputSize fromPixels(int width, int height) {
            if (width <= 0 || height <= 0) {
                return Native;
            }
            for (OutputSize s : values()) {
                if (s.width == width && s.height == height) {
                    return s;
                }
            }
            return Native;
        }

        @Override
        public String toString() {
            if (isNative()) {
                return "Native (sensor)";
            }
            return name() + " (" + width + "x" + height + ")";
        }
    }

    private final Ros2FrameAssembler assembler = new Ros2FrameAssembler();
    private final ArrayBlockingQueue<OpenCvRawFrame> publishQueue = new ArrayBlockingQueue<>(2);
    private final AtomicReference<OpenCvRawFrame> latestRaw = new AtomicReference<>();
    private MjpegHttpServer httpServer;
    private V4l2LoopbackSink v4l2Sink;
    private Thread publishThread;
    private volatile boolean publishThreadStop;

    private FrameSource frameSource = FrameSource.valueOf(getString("frameSource", FrameSource.RenderedPixmap.name()));
    private OutputSize outputSize = loadOutputSize();
    private String bindAddress = getString("bindAddress", "127.0.0.1");
    private int httpPort = getInt("httpPort", 8090);
    private float jpegQuality = getFloat("jpegQuality", 0.8f);
    private int grayScale = getInt("grayScale", 2);
    private boolean flipY = getBoolean("flipY", true);
    private TimeSliceMethod timeSliceMethod = TimeSliceMethod.valueOf(
            getString("timeSliceMethod", TimeSliceMethod.TimeIntervalUs.name()));
    private int eventsPerFrame = getInt("eventsPerFrame", 10000);
    private int timeDurationUs = getInt("timeDurationUs", 10000);
    private boolean skipChipRendering = getBoolean("skipChipRendering", false);
    private boolean nonExclusive = getBoolean("nonExclusive", false);
    private boolean publishV4l2 = getBoolean("publishV4l2", false);
    private boolean v4l2Mjpeg = getBoolean("v4l2Mjpeg", true);
    private String v4l2Device = getString("v4l2Device", "/dev/video10");

    private volatile double publishHz;
    private volatile String lastError;
    private volatile long publishedFrameCount;
    private long hzWindowStartNs;
    private int hzCount;

    public OpenCVOutput(AEChip chip) {
        super(chip);
        setPropertyTooltip(GROUP_SINKS, "skipChipRendering",
                "Skip AEViewer OpenGL; leave off so Auto/RenderedPixmap can copy frames+events");
        setPropertyTooltip(GROUP_SINKS, "nonExclusive",
                "Keep HTTP MJPEG while publishV4l2 is on (default off: V4L2 replaces MJPEG)");
        setPropertyTooltip(GROUP_HTTP, "bindAddress", "Bind address; 127.0.0.1 local, 0.0.0.0 LAN");
        setPropertyTooltip(GROUP_HTTP, "httpPort", "HTTP port for /video.mjpg (default 8090)");
        setPropertyTooltip(GROUP_HTTP, "jpegQuality", "JPEG quality 0.05–1.0");
        setPropertyTooltip(GROUP_FRAME, "frameSource",
                "Auto/RenderedPixmap: chip view (frames+events). ApsFrames: APS only. DvsEventCount: event histogram");
        setPropertyTooltip(GROUP_FRAME, "outputSize",
                "Native = sensor size. V4L2 (Cheese/Zoom) needs a standard size such as VGA (640x480)");
        setPropertyTooltip(GROUP_FRAME, "outputImageWidth", "Output width; 0 = chip / frame size");
        setPropertyTooltip(GROUP_FRAME, "outputImageHeight", "Output height; 0 = chip / frame size");
        setPropertyTooltip(GROUP_FRAME, "grayScale", "Full-scale signed DVS event count (mid-gray = 0)");
        setPropertyTooltip(GROUP_FRAME, "flipY", "Row 0 is sensor top (OpenCV +y down)");
        setPropertyTooltip(GROUP_SLICE, "timeSliceMethod", "Close a DVS frame after N events or after a time interval");
        setPropertyTooltip(GROUP_SLICE, "eventsPerFrame", "Events per frame when timeSliceMethod is EventCount");
        setPropertyTooltip(GROUP_SLICE, "timeDurationUs", "Slice duration in microseconds when TimeIntervalUs");
        setPropertyTooltip(GROUP_V4L2, "publishV4l2",
                "Linux: write frames to /dev/video10 so Cheese/Zoom/Meet see camera jAER (needs a standard outputSize)");
        setPropertyTooltip(GROUP_V4L2, "v4l2Mjpeg",
                "MJPEG (default): Cheese/Zoom. Uncheck for raw YUYV (gst-launch without jpegdec)");
        setPropertyTooltip(GROUP_V4L2, "v4l2Device", "v4l2loopback node, e.g. /dev/video10");
        setPropertyTooltip(GROUP_V4L2, "v4l2OutputWidth",
                "v4l2 width; 0 = native. Cheese/PipeWire often fail on Davis 346x260 — use 640");
        setPropertyTooltip(GROUP_V4L2, "v4l2OutputHeight",
                "v4l2 height; 0 = native. Cheese/PipeWire: use 480 with width 640");
        hideProperty("outputImageWidth");
        hideProperty("outputImageHeight");
        hideProperty("v4l2OutputWidth");
        hideProperty("v4l2OutputHeight");
        if (!V4l2LoopbackSink.isLinux()) {
            hideProperty("nonExclusive");
            hideProperty("publishV4l2");
            hideProperty("v4l2Mjpeg");
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

    private int resolvedOutputWidth() {
        return outputSize.isNative() ? Math.max(1, chip.getSizeX()) : outputSize.width;
    }

    private int resolvedOutputHeight() {
        return outputSize.isNative() ? Math.max(1, chip.getSizeY()) : outputSize.height;
    }

    private OutputSize loadOutputSize() {
        String stored = getString("outputSize", "");
        if (stored != null && !stored.isEmpty()) {
            try {
                return OutputSize.valueOf(stored);
            } catch (IllegalArgumentException ignore) {
            }
        }
        int w = getInt("v4l2OutputWidth", 0);
        int h = getInt("v4l2OutputHeight", 0);
        if (w <= 0 || h <= 0) {
            w = getInt("outputImageWidth", 0);
            h = getInt("outputImageHeight", 0);
        }
        return OutputSize.fromPixels(w, h);
    }

    private void applyAssemblerSettings() {
        assembler.setTimeSliceMethod(timeSliceMethod);
        assembler.setGrayScale(grayScale);
        assembler.setEventsPerFrame(eventsPerFrame);
        assembler.setTimeDurationUs(timeDurationUs);
        assembler.setFlipY(flipY);
        assembler.setSize(resolvedOutputWidth(), resolvedOutputHeight());
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
                OpenCvRawFrame raw = encodeDvsMono8();
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
        if (frameSource != FrameSource.ApsFrames) {
            return in;
        }
        OpenCvRawFrame raw = copyFramePacket(in);
        if (raw != null) {
            enqueue(raw);
        }
        return in;
    }

    private boolean wantDvsAssembly() {
        if (frameSource == FrameSource.ApsFrames) {
            return false;
        }
        if (frameSource == FrameSource.DvsEventCount) {
            return true;
        }
        // Auto/RenderedPixmap: chip view is copied after AEViewer render(); histogram only if rendering is skipped.
        return skipChipRendering;
    }

    /**
     * Copy APS+events (current color scheme) after {@code renderBundle}.
     * No-op for ApsFrames / DvsEventCount.
     */
    public void publishChipViewAfterRender() {
        if (!isFilterEnabled()) {
            return;
        }
        if (frameSource == FrameSource.ApsFrames || frameSource == FrameSource.DvsEventCount) {
            return;
        }
        OpenCvRawFrame raw = copyRenderedPixmap();
        if (raw != null) {
            enqueue(raw);
        }
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

    /**
     * Chip view: APS pixmap plus DVS overlay (same alpha test as
     * {@code ChipRendererDisplayMethodRGBA}), using the current color scheme.
     * Pure DVS chips have events in the pixmap already.
     */
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
            float[] dvs = null;
            boolean displayFrames = true;
            boolean displayEvents = true;
            if (renderer instanceof DavisRenderer) {
                DavisRenderer davis = (DavisRenderer) renderer;
                displayFrames = davis.isDisplayFrames();
                displayEvents = davis.isDisplayEvents();
                java.nio.FloatBuffer em = davis.getDvsEventsMap();
                dvs = em != null ? em.array() : null;
            }
            if (pixmap == null && dvs == null) {
                return null;
            }
            float bg = renderer.getGrayValue();
            bgr = new byte[w * h * 3];
            int p = 0;
            for (int y = 0; y < h; y++) {
                int srcY = flipY ? (h - 1 - y) : y;
                for (int x = 0; x < w; x++) {
                    int pi = renderer.getPixMapIndex(x, srcY);
                    float r = bg;
                    float g = bg;
                    float b = bg;
                    if (displayFrames && pixmap != null && pi >= 0 && (pi + 2) < pixmap.length) {
                        r = pixmap[pi];
                        g = pixmap[pi + 1];
                        b = pixmap[pi + 2];
                    }
                    // GL_ALPHA_TEST GL_GREATER 0: event pixels replace the APS (or gray) background.
                    if (displayEvents && dvs != null && pi >= 0 && (pi + 3) < dvs.length && dvs[pi + 3] > 0f) {
                        r = dvs[pi];
                        g = dvs[pi + 1];
                        b = dvs[pi + 2];
                    } else if (dvs == null && pixmap != null && pi >= 0 && (pi + 2) < pixmap.length) {
                        r = pixmap[pi];
                        g = pixmap[pi + 1];
                        b = pixmap[pi + 2];
                    }
                    bgr[p++] = (byte) OpenCvRawFrame.clamp255(Math.round(255f * b));
                    bgr[p++] = (byte) OpenCvRawFrame.clamp255(Math.round(255f * g));
                    bgr[p++] = (byte) OpenCvRawFrame.clamp255(Math.round(255f * r));
                }
            }
        }
        return applyOutputSize(new OpenCvRawFrame(w, h, 3, bgr));
    }

    private OpenCvRawFrame applyOutputSize(OpenCvRawFrame raw) {
        if (outputSize.isNative()) {
            return raw;
        }
        return raw.scaled(outputSize.width, outputSize.height);
    }

    private void enqueue(OpenCvRawFrame raw) {
        latestRaw.set(raw);
        if (!publishQueue.offer(raw)) {
            publishQueue.poll();
            publishQueue.offer(raw);
        }
    }

    private boolean wantHttp() {
        return !publishV4l2 || nonExclusive;
    }

    private synchronized void startSinks() {
        if (publishV4l2 && outputSize.isNative() && SwingUtilities.isEventDispatchThread()) {
            if (!offerStandardSizeForV4l2()) {
                publishV4l2 = false;
                putBoolean("publishV4l2", false);
                getSupport().firePropertyChange("publishV4l2", true, false);
            }
        }
        if (wantHttp()) {
            startHttp();
        } else {
            stopHttp();
        }
        startV4l2();
        startPublishThread();
    }

    private void startHttp() {
        if (!wantHttp()) {
            stopHttp();
            return;
        }
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
        if (outputSize.isNative()) {
            lastError = "V4L2 needs a standard outputSize (e.g. VGA)";
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
                MjpegHttpServer http = httpServer;
                if (http != null) {
                    http.setLatestJpeg(raw.toJpeg(jpegQuality));
                }
                V4l2LoopbackSink v4l = v4l2Sink;
                if (publishV4l2 && v4l != null && outputSize.isStandard()) {
                    v4l.write(raw, outputSize.width, outputSize.height, v4l2Mjpeg, jpegQuality);
                    lastError = v4l.getLastError();
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
        sb.append("OpenCV  ");
        sb.append(String.format("%.1f Hz", publishHz));
        sb.append("  ").append(outputSize);
        if (outputSize.isNative() && chip != null) {
            sb.append(' ').append(chip.getSizeX()).append('x').append(chip.getSizeY());
        }
        if (httpServer != null) {
            sb.append("\nMJPEG  ").append(getOpenCvClientUrl());
        }
        if (publishV4l2) {
            sb.append('\n').append(v4l2Device);
            if (v4l2Sink != null && v4l2Sink.isOpen()) {
                sb.append(" open");
            }
            sb.append(v4l2Mjpeg ? " MJPEG" : " YUYV");
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
        getSupport().firePropertyChange("frameSource", old, frameSource);
    }

    public OutputSize getOutputSize() {
        return outputSize;
    }

    public synchronized void setOutputSize(OutputSize outputSize) {
        if (outputSize == null) {
            outputSize = OutputSize.Native;
        }
        if (outputSize.isNative() && publishV4l2) {
            if (!confirmDisableV4l2ForNative()) {
                getSupport().firePropertyChange("outputSize", OutputSize.Native, this.outputSize);
                return;
            }
            setPublishV4l2(false);
        }
        OutputSize old = this.outputSize;
        this.outputSize = outputSize;
        putString("outputSize", outputSize.name());
        applyAssemblerSettings();
        getSupport().firePropertyChange("outputSize", old, this.outputSize);
    }

    private Component dialogParent() {
        return chip != null ? chip.getAeViewer() : null;
    }

    /** Cheese/Zoom/Meet reject odd sensor sizes; offer VGA or cancel. */
    private boolean offerStandardSizeForV4l2() {
        String vga = OutputSize.VGA.toString();
        int n = JOptionPane.showOptionDialog(dialogParent(),
                "Cheese, Zoom, and Google Meet need a standard camera size.\n"
                        + "Native sensor size (e.g. Davis 346x260) is not accepted.\n\n"
                        + "Use " + vga + "?",
                "V4L2 needs a standard size",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                new Object[] { vga, "Cancel" },
                vga);
        if (n != 0) {
            return false;
        }
        OutputSize old = this.outputSize;
        this.outputSize = OutputSize.VGA;
        putString("outputSize", this.outputSize.name());
        applyAssemblerSettings();
        getSupport().firePropertyChange("outputSize", old, this.outputSize);
        return true;
    }

    private boolean confirmDisableV4l2ForNative() {
        int n = JOptionPane.showConfirmDialog(dialogParent(),
                "V4L2 webcam output needs a standard size.\n"
                        + "Disable V4L2 to use native sensor size?",
                "Native size and V4L2",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return n == JOptionPane.YES_OPTION;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public synchronized void setBindAddress(String bindAddress) {
        String old = this.bindAddress;
        this.bindAddress = bindAddress;
        putString("bindAddress", bindAddress);
        if (isFilterEnabled() && !old.equals(bindAddress) && wantHttp()) {
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
        if (isFilterEnabled() && old != this.httpPort && wantHttp()) {
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
        return outputSize.isNative() ? 0 : outputSize.width;
    }

    public void setOutputImageWidth(int outputImageWidth) {
        setOutputSize(OutputSize.fromPixels(outputImageWidth, getOutputImageHeight()));
    }

    public int getOutputImageHeight() {
        return outputSize.isNative() ? 0 : outputSize.height;
    }

    public void setOutputImageHeight(int outputImageHeight) {
        setOutputSize(OutputSize.fromPixels(getOutputImageWidth(), outputImageHeight));
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

    public boolean isNonExclusive() {
        return nonExclusive;
    }

    public synchronized void setNonExclusive(boolean nonExclusive) {
        boolean old = this.nonExclusive;
        this.nonExclusive = nonExclusive;
        putBoolean("nonExclusive", nonExclusive);
        if (isFilterEnabled() && old != nonExclusive) {
            startSinks();
        }
        getSupport().firePropertyChange("nonExclusive", old, nonExclusive);
    }

    public synchronized void setPublishV4l2(boolean publishV4l2) {
        if (publishV4l2 && outputSize.isNative()) {
            if (!offerStandardSizeForV4l2()) {
                getSupport().firePropertyChange("publishV4l2", true, false);
                return;
            }
        }
        boolean old = this.publishV4l2;
        this.publishV4l2 = publishV4l2;
        putBoolean("publishV4l2", publishV4l2);
        if (isFilterEnabled() && old != publishV4l2) {
            startSinks();
        }
        getSupport().firePropertyChange("publishV4l2", old, this.publishV4l2);
    }

    public boolean isV4l2Mjpeg() {
        return v4l2Mjpeg;
    }

    public synchronized void setV4l2Mjpeg(boolean v4l2Mjpeg) {
        boolean old = this.v4l2Mjpeg;
        this.v4l2Mjpeg = v4l2Mjpeg;
        putBoolean("v4l2Mjpeg", v4l2Mjpeg);
        if (isFilterEnabled() && publishV4l2 && old != v4l2Mjpeg) {
            stopV4l2();
            startV4l2();
        }
        getSupport().firePropertyChange("v4l2Mjpeg", old, this.v4l2Mjpeg);
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
        return getOutputImageWidth();
    }

    public void setV4l2OutputWidth(int v4l2OutputWidth) {
        setOutputImageWidth(v4l2OutputWidth);
    }

    public int getV4l2OutputHeight() {
        return getOutputImageHeight();
    }

    public void setV4l2OutputHeight(int v4l2OutputHeight) {
        setOutputImageHeight(v4l2OutputHeight);
    }
}
