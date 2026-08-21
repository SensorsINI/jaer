/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventio.ros2;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventio.ros2.Ros2FrameAssembler.FoxgloveFrameEncoding;
import net.sf.jaer.eventio.ros2.Ros2FrameAssembler.FrameType;
import net.sf.jaer.eventio.ros2.Ros2FrameAssembler.TimeSliceMethod;

/**
 * Publishes assembled DVS frames to ROS2 (jros2 DDS) and/or Foxglove Studio
 * (WebSocket {@code foxglove.RawImage}). Independent of the OpenGL renderer.
 */
@Description("Publishes DVS frames to ROS2 and/or Foxglove Studio (File → Remote)")
@Help("""
<html>
<body>
<h2>ROSOutput</h2>
<p>Assembles event-camera frames (not the AEViewer pixmap) and publishes them
to <b>ROS2</b> (IHMC jros2 / Fast-DDS, no ROS2 install on the jAER machine) and/or
<b>Foxglove Studio</b> over a local WebSocket.</p>
<h3>Foxglove (no ROS2)</h3>
<ol>
<li>Enable this filter and check <b>publishFoxglove</b>.</li>
<li>In Foxglove: Open connection → Foxglove WebSocket → paste
<code>ws://localhost:8765</code> (or the URL shown in File → Remote).</li>
<li>Layouts → Create new layout → choose the <b>Image</b> template.</li>
<li>Select topic <code>/jaer/event_count</code> (or time-surface / voxel topics).</li>
</ol>
<p><b>foxgloveFrameEncoding:</b> <code>Float32</code> is 32FC1 in 0–1 (colormap);
<code>Rgb8</code> is ON red / OFF green; <code>Mono8</code> is mid-gray = zero.</p>
<h3>ROS2</h3>
<p>Enable <b>publishRos2</b>. Topics are <code>sensor_msgs/Image</code> encoding
<code>32FC1</code> with signed counts / microseconds / voxel weights.
Set domain ID (or environment <code>ROS_DOMAIN_ID</code>). On another machine:
<code>ros2 topic hz /jaer/event_count</code>.</p>
<h3>Frame types</h3>
<ul>
<li><b>EventCountHistogram</b> — signed ON=+1 OFF=−1 histogram.</li>
<li><b>TimestampImages</b> — last timestamp per polarity
(<code>/jaer/time_surface_on</code>, <code>_off</code>).</li>
<li><b>VoxelGrid</b> — bilinear time interpolation into B bins, stacked as
height = B×H on <code>/jaer/voxel_grid</code>.</li>
</ul>
<p>Place this filter after denoisers if you want filtered events.
<b>skipChipRendering</b> skips OpenGL pixmap updates while still publishing.</p>
<h3>Drop detection</h3>
<p>ROS2 <code>sensor_msgs/Image</code> has no sequence field (ROS2
<code>Header</code> dropped <code>seq</code>). DDS/RTPS still numbers samples
on the BEST_EFFORT writer. Foxglove <code>RawImage</code> JSON includes an
optional <code>sequence</code> integer (same counter as the overlay);
Studio’s Image panel ignores it. A gap means a dropped frame.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ROSOutput extends EventFilter2D {

    public static final String GROUP_SINKS = "Sinks";
    public static final String GROUP_ROS = "ROS";
    public static final String GROUP_FRAME = "Frame";
    public static final String GROUP_SLICE = "Slice";

    private final Ros2FrameAssembler assembler = new Ros2FrameAssembler();
    private final Ros2FramePublisher rosPublisher = new Ros2FramePublisher();
    private FoxgloveWebSocketServer foxgloveServer;
    private final ArrayBlockingQueue<PublishJob> publishQueue = new ArrayBlockingQueue<>(8);
    private Thread publishThread;
    private volatile boolean publishThreadStop;

    private boolean publishRos2 = getBoolean("publishRos2", false);
    private boolean publishFoxglove = getBoolean("publishFoxglove", true);
    private int foxglovePort = getInt("foxglovePort", 8765);
    private String foxgloveBindAddress = getString("foxgloveBindAddress", "127.0.0.1");
    private FoxgloveFrameEncoding foxgloveFrameEncoding = FoxgloveFrameEncoding.valueOf(
            getString("foxgloveFrameEncoding", FoxgloveFrameEncoding.Float32.name()));
    private int domainId = getInt("domainId", defaultDomainId());
    private String nodeName = getString("nodeName", "jaer");
    private String topicPrefix = getString("topicPrefix", "/jaer");
    private String frameId = getString("frameId", "dvs");
    private FrameType frameType = FrameType.valueOf(getString("frameType", FrameType.EventCountHistogram.name()));
    private int outputImageWidth = getInt("outputImageWidth", 0);
    private int outputImageHeight = getInt("outputImageHeight", 0);
    private int grayScale = getInt("grayScale", 16);
    private int voxelBins = getInt("voxelBins", 5);
    private boolean flipY = getBoolean("flipY", true);
    private TimeSliceMethod timeSliceMethod = TimeSliceMethod.valueOf(
            getString("timeSliceMethod", TimeSliceMethod.EventCount.name()));
    private int eventsPerFrame = getInt("eventsPerFrame", 2000);
    private int timeDurationUs = getInt("timeDurationUs", 10000);
    private boolean skipChipRendering = getBoolean("skipChipRendering", false);

    private volatile double publishHz;
    private volatile String lastError;
    private volatile long publishedFrameCount;
    private long hzWindowStartNs;
    private int hzCount;

    public ROSOutput(AEChip chip) {
        super(chip);
        setPropertyTooltip(GROUP_SINKS, "publishRos2", "Publish sensor_msgs/Image on DDS (jros2 Fast-DDS)");
        setPropertyTooltip(GROUP_SINKS, "publishFoxglove", "Host Foxglove WebSocket (foxglove.RawImage); no ROS2 needed");
        setPropertyTooltip(GROUP_SINKS, "foxglovePort", "Foxglove WebSocket port (Studio default 8765)");
        setPropertyTooltip(GROUP_SINKS, "foxgloveBindAddress", "Bind address; 127.0.0.1 local, 0.0.0.0 LAN");
        setPropertyTooltip(GROUP_SINKS, "foxgloveFrameEncoding", "Float32 0–1 colormap, Rgb8 ON-red/OFF-green, or Mono8");
        setPropertyTooltip(GROUP_SINKS, "skipChipRendering", "Skip AEViewer OpenGL pixmap while still publishing frames");
        setPropertyTooltip(GROUP_ROS, "domainId", "ROS_DOMAIN_ID (0–101)");
        setPropertyTooltip(GROUP_ROS, "nodeName", "ROS2 node name");
        setPropertyTooltip(GROUP_ROS, "topicPrefix", "Topic prefix, e.g. /jaer");
        setPropertyTooltip(GROUP_ROS, "frameId", "header.frame_id / foxglove frame_id");
        setPropertyTooltip(GROUP_FRAME, "frameType", "Event-count histogram, polarity timestamp maps, or voxel grid");
        setPropertyTooltip(GROUP_FRAME, "outputImageWidth", "Output width; 0 = chip sizeX");
        setPropertyTooltip(GROUP_FRAME, "outputImageHeight", "Output height; 0 = chip sizeY");
        setPropertyTooltip(GROUP_FRAME, "grayScale", "Full-scale signed event count (clip / 0–1 mapping)");
        setPropertyTooltip(GROUP_FRAME, "voxelBins", "Temporal bins for VoxelGrid (stacked height = bins×H)");
        setPropertyTooltip(GROUP_FRAME, "flipY", "Row 0 is sensor top (image / Foxglove +y down)");
        setPropertyTooltip(GROUP_SLICE, "timeSliceMethod", "Close a frame after N events or after a time interval");
        setPropertyTooltip(GROUP_SLICE, "eventsPerFrame", "Events per frame when timeSliceMethod is EventCount");
        setPropertyTooltip(GROUP_SLICE, "timeDurationUs", "Slice duration in microseconds when TimeIntervalUs");
        applyAssemblerSettings();
    }

    public static ROSOutput find(AEChip chip) {
        if (chip == null || chip.getFilterChain() == null) {
            return null;
        }
        return (ROSOutput) chip.getFilterChain().findFilter(ROSOutput.class);
    }

    /**
     * Appends a disabled ROSOutput if the saved filter chain does not include it.
     */
    public static void ensurePresent(AEChip chip) {
        if (chip == null) {
            return;
        }
        FilterChain chain = chip.getFilterChain();
        if (chain == null) {
            return;
        }
        if (chain.findFilter(ROSOutput.class) != null) {
            return;
        }
        try {
            ROSOutput f = new ROSOutput(chip);
            chain.add(f);
            f.initFilter();
            f.setPreferredEnabledState();
            ArrayList<String> names = new ArrayList<>();
            for (EventFilter2D x : chain) {
                names.add(x.getClass().getName());
            }
            chain.storePreferredFiltersForChip(names);
            log.info("Appended ROSOutput to filter chain for " + chip.getClass().getSimpleName());
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not add ROSOutput: " + e, e);
        }
    }

    private static int defaultDomainId() {
        try {
            String env = System.getenv("ROS_DOMAIN_ID");
            if (env != null && !env.isBlank()) {
                return Integer.parseInt(env.trim());
            }
        } catch (Exception ignore) {
        }
        return 0;
    }

    private void applyAssemblerSettings() {
        assembler.setFrameType(frameType);
        assembler.setTimeSliceMethod(timeSliceMethod);
        assembler.setGrayScale(grayScale);
        assembler.setEventsPerFrame(eventsPerFrame);
        assembler.setTimeDurationUs(timeDurationUs);
        assembler.setVoxelBins(voxelBins);
        assembler.setFlipY(flipY);
        int w = outputImageWidth > 0 ? outputImageWidth : Math.max(1, chip.getSizeX());
        int h = outputImageHeight > 0 ? outputImageHeight : Math.max(1, chip.getSizeY());
        assembler.setSize(w, h);
    }

    @Override
    public synchronized void setFilterEnabled(boolean enabled) {
        super.setFilterEnabled(enabled);
        if (enabled) {
            if (!publishRos2 && !publishFoxglove) {
                setPublishFoxglove(true);
            }
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
        if (!isFilterEnabled() || in == null) {
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
                enqueuePublish();
                assembler.clear();
            }
        }
        return in;
    }

    private void enqueuePublish() {
        List<EncodedImage> rosImgs = publishRos2 ? assembler.encodeRos() : List.of();
        List<EncodedImage> fgImgs = publishFoxglove ? assembler.encodeFoxglove(foxgloveFrameEncoding) : List.of();
        long tNs = System.currentTimeMillis() * 1_000_000L;
        PublishJob job = new PublishJob(rosImgs, fgImgs, tNs, frameId);
        if (!publishQueue.offer(job)) {
            publishQueue.poll();
            publishQueue.offer(job);
        }
    }

    private synchronized void startSinks() {
        startPublishThread();
        List<String> topics = currentTopics();
        if (publishRos2) {
            if (!rosPublisher.isOpen()) {
                rosPublisher.open(nodeName, domainId);
            }
            rosPublisher.setTopics(topics);
        } else {
            rosPublisher.close();
        }
        if (publishFoxglove) {
            startFoxglove(topics);
        } else {
            stopFoxglove();
        }
    }

    private List<String> currentTopics() {
        List<String> t = new ArrayList<>();
        String p = topicPrefix.endsWith("/") ? topicPrefix.substring(0, topicPrefix.length() - 1) : topicPrefix;
        switch (frameType) {
            case TimestampImages:
                t.add(p + "/" + Ros2FrameAssembler.TOPIC_TIME_ON);
                t.add(p + "/" + Ros2FrameAssembler.TOPIC_TIME_OFF);
                break;
            case VoxelGrid:
                t.add(p + "/" + Ros2FrameAssembler.TOPIC_VOXEL);
                break;
            case EventCountHistogram:
            default:
                t.add(p + "/" + Ros2FrameAssembler.TOPIC_EVENT_COUNT);
                break;
        }
        return t;
    }

    private String fullTopic(EncodedImage img) {
        String p = topicPrefix.endsWith("/") ? topicPrefix.substring(0, topicPrefix.length() - 1) : topicPrefix;
        return p + "/" + img.topicSuffix;
    }

    private void startFoxglove(List<String> topics) {
        try {
            if (foxgloveServer != null) {
                foxgloveServer.setTopics(topics);
                return;
            }
            String schema = FoxgloveWebSocketServer.loadRawImageSchema();
            InetSocketAddress addr = new InetSocketAddress(foxgloveBindAddress, foxglovePort);
            foxgloveServer = new FoxgloveWebSocketServer(addr, schema);
            foxgloveServer.start();
            foxgloveServer.setTopics(topics);
        } catch (Exception e) {
            lastError = e.toString();
            log.log(Level.WARNING, "Foxglove server failed: " + e, e);
            foxgloveServer = null;
        }
    }

    private void stopFoxglove() {
        if (foxgloveServer != null) {
            try {
                foxgloveServer.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.log(Level.FINE, e.toString(), e);
            }
            foxgloveServer = null;
        }
    }

    private void startPublishThread() {
        if (publishThread != null && publishThread.isAlive()) {
            return;
        }
        publishThreadStop = false;
        publishThread = new Thread(this::publishLoop, "ROSOutput-publish");
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
        rosPublisher.close();
        stopFoxglove();
        publishHz = 0;
        publishedFrameCount = 0;
        hzCount = 0;
        hzWindowStartNs = 0;
    }

    private void publishLoop() {
        while (!publishThreadStop) {
            try {
                PublishJob job = publishQueue.poll(200, TimeUnit.MILLISECONDS);
                if (job == null) {
                    continue;
                }
                if (publishRos2) {
                    for (EncodedImage img : job.ros) {
                        rosPublisher.publish(fullTopic(img), img, job.frameId, job.timestampNs);
                    }
                }
                FoxgloveWebSocketServer fg = foxgloveServer;
                long seq = publishedFrameCount;
                if (publishFoxglove && fg != null) {
                    for (EncodedImage img : job.foxglove) {
                        fg.publish(fullTopic(img), img, job.frameId, job.timestampNs, seq);
                    }
                }
                notePublished();
                String err = rosPublisher.getLastError();
                if (err != null) {
                    lastError = err;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                lastError = e.toString();
                log.log(Level.WARNING, "ROSOutput publish: " + e, e);
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

    /**
     * Built only when the canvas asks (overlay preference on). Do not call from the publish path.
     */
    public String getOverlayText() {
        if (!isFilterEnabled()) {
            return "";
        }
        String exposure = timeSliceMethod == TimeSliceMethod.TimeIntervalUs ? "DurationUs" : "EventCount";
        StringBuilder sb = new StringBuilder(96);
        sb.append("File/Remote/Foxglove ROS2");
        sb.append('\n').append(publishedFrameCount).append(' ').append(exposure)
                .append(' ').append(frameType).append(" frames  ");
        sb.append(String.format("%.0f Hz", publishHz));
        if (publishRos2) {
            sb.append("\nROS2 DDS");
        }
        if (publishFoxglove) {
            int n = foxgloveServer == null ? 0 : foxgloveServer.getClientCount();
            sb.append("\nFoxglove ws://").append(foxgloveBindAddress).append(':').append(foxglovePort);
            sb.append(" (").append(n).append(n == 1 ? " client)" : " clients)");
        }
        if (lastError != null && !lastError.isEmpty()) {
            sb.append("\nerr: ").append(lastError);
        }
        return sb.toString();
    }

    public double getPublishHz() {
        return publishHz;
    }

    public String getLastError() {
        return lastError;
    }

    public String getFoxgloveUrl() {
        return "ws://" + foxgloveBindAddress + ":" + foxglovePort;
    }

    /**
     * URL to paste in Foxglove Studio's WebSocket dialog. Loopback / any-interface
     * binds are shown as {@code localhost}.
     */
    public String getFoxgloveClientUrl() {
        String host = foxgloveBindAddress;
        if (host == null || host.isBlank()
                || "0.0.0.0".equals(host)
                || "::".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)) {
            host = "localhost";
        }
        return "ws://" + host + ":" + foxglovePort;
    }

    // --- getters/setters (FilterPanel) ---

    public boolean isPublishRos2() {
        return publishRos2;
    }

    public synchronized void setPublishRos2(boolean publishRos2) {
        this.publishRos2 = publishRos2;
        putBoolean("publishRos2", publishRos2);
        if (isFilterEnabled()) {
            startSinks();
        }
        getSupport().firePropertyChange("publishRos2", !publishRos2, publishRos2);
    }

    public boolean isPublishFoxglove() {
        return publishFoxglove;
    }

    public synchronized void setPublishFoxglove(boolean publishFoxglove) {
        boolean old = this.publishFoxglove;
        this.publishFoxglove = publishFoxglove;
        putBoolean("publishFoxglove", publishFoxglove);
        if (isFilterEnabled()) {
            startSinks();
        }
        getSupport().firePropertyChange("publishFoxglove", old, publishFoxglove);
    }

    public int getFoxglovePort() {
        return foxglovePort;
    }

    public void setFoxglovePort(int foxglovePort) {
        int old = this.foxglovePort;
        this.foxglovePort = foxglovePort;
        putInt("foxglovePort", foxglovePort);
        if (isFilterEnabled() && publishFoxglove && old != foxglovePort) {
            stopFoxglove();
            startSinks();
        }
        getSupport().firePropertyChange("foxglovePort", old, foxglovePort);
    }

    public String getFoxgloveBindAddress() {
        return foxgloveBindAddress;
    }

    public void setFoxgloveBindAddress(String foxgloveBindAddress) {
        String old = this.foxgloveBindAddress;
        this.foxgloveBindAddress = foxgloveBindAddress;
        putString("foxgloveBindAddress", foxgloveBindAddress);
        if (isFilterEnabled() && publishFoxglove && !old.equals(foxgloveBindAddress)) {
            stopFoxglove();
            startSinks();
        }
        getSupport().firePropertyChange("foxgloveBindAddress", old, foxgloveBindAddress);
    }

    public FoxgloveFrameEncoding getFoxgloveFrameEncoding() {
        return foxgloveFrameEncoding;
    }

    public void setFoxgloveFrameEncoding(FoxgloveFrameEncoding foxgloveFrameEncoding) {
        FoxgloveFrameEncoding old = this.foxgloveFrameEncoding;
        this.foxgloveFrameEncoding = foxgloveFrameEncoding;
        putString("foxgloveFrameEncoding", foxgloveFrameEncoding.name());
        getSupport().firePropertyChange("foxgloveFrameEncoding", old, foxgloveFrameEncoding);
    }

    public int getDomainId() {
        return domainId;
    }

    public synchronized void setDomainId(int domainId) {
        int old = this.domainId;
        this.domainId = domainId;
        putInt("domainId", domainId);
        if (isFilterEnabled() && publishRos2 && old != domainId) {
            rosPublisher.close();
            startSinks();
        }
        getSupport().firePropertyChange("domainId", old, domainId);
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        String old = this.nodeName;
        this.nodeName = nodeName;
        putString("nodeName", nodeName);
        getSupport().firePropertyChange("nodeName", old, nodeName);
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public synchronized void setTopicPrefix(String topicPrefix) {
        String old = this.topicPrefix;
        this.topicPrefix = topicPrefix;
        putString("topicPrefix", topicPrefix);
        if (isFilterEnabled()) {
            startSinks();
        }
        getSupport().firePropertyChange("topicPrefix", old, topicPrefix);
    }

    public String getFrameId() {
        return frameId;
    }

    public void setFrameId(String frameId) {
        String old = this.frameId;
        this.frameId = frameId;
        putString("frameId", frameId);
        getSupport().firePropertyChange("frameId", old, frameId);
    }

    public FrameType getFrameType() {
        return frameType;
    }

    public synchronized void setFrameType(FrameType frameType) {
        FrameType old = this.frameType;
        this.frameType = frameType;
        putString("frameType", frameType.name());
        assembler.setFrameType(frameType);
        assembler.clear();
        if (isFilterEnabled()) {
            startSinks();
        }
        getSupport().firePropertyChange("frameType", old, frameType);
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

    public int getVoxelBins() {
        return voxelBins;
    }

    public void setVoxelBins(int voxelBins) {
        int old = this.voxelBins;
        this.voxelBins = Math.max(2, voxelBins);
        putInt("voxelBins", this.voxelBins);
        assembler.setVoxelBins(this.voxelBins);
        getSupport().firePropertyChange("voxelBins", old, this.voxelBins);
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

    private static final class PublishJob {
        final List<EncodedImage> ros;
        final List<EncodedImage> foxglove;
        final long timestampNs;
        final String frameId;

        PublishJob(List<EncodedImage> ros, List<EncodedImage> foxglove, long timestampNs, String frameId) {
            this.ros = ros;
            this.foxglove = foxglove;
            this.timestampNs = timestampNs;
            this.frameId = frameId;
        }
    }
}
