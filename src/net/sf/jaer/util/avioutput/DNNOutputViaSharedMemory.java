/*
 * Copyright (C) 2026 Tobi Delbruck / SensorsINI.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.util.avioutput;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

/**
 * Publishes DVS data to a memory-mapped file plus localhost TCP for a local
 * Python DNN consumer. Two payload modes share the same HELLO / FRAME_READY
 * control path: 64×64 uint8 event-count frames (Roshambo) or packed
 * {@code (t, x, y, p)} event windows (FireNet / E2VID).
 * <p>
 * Open from File → Remote → DNN shared memory output…
 *
 * @author tobi
 */
@Description("Publishes DVS frames or event windows to mmap + localhost TCP for a Python DNN (Roshambo / FireNet)")
@Help("""
<html>
<body>
<h2>DNNOutputViaSharedMemory</h2>
<p>Publishes DVS data to a <b>memory-mapped file</b> plus a localhost
<b>TCP JSON-lines</b> control channel so a local Python process can run a DNN.
Choose <code>outputMode</code> for the consumer:</p>
<ul>
<li><b>EventCountFrames</b> — 64&times;64 uint8 event-count histograms for
<a href="https://github.com/SensorsINI/dextra-roshambo-python">dextra-roshambo-python</a>
(replaces <code>producer.py</code> / pyaer).</li>
<li><b>EventWindows</b> — packed <code>(t, x, y, p)</code> windows for
<a href="https://github.com/SensorsINI/rpg_e2vid">rpg_e2vid / FireNet</a>
frame reconstruction.</li>
</ul>
<p>Also under File → Remote → <b>DNN shared memory output…</b>.
See the <a href="https://sensors.ini.ch/research/projects/dextra">Dextra project</a>
and <a href="https://github.com/SensorsINI/jaer">jAER</a>.</p>
<hr>
<h3>EventCountFrames (Roshambo)</h3>
<ol>
<li>Set <code>outputMode</code> to <b>EventCountFrames</b>. Defaults: output
<b>64&times;64</b>, <code>dvsGrayScale=16</code>, <code>rectifyPolarities=true</code>,
<code>normalizeFrame=false</code>, <code>showFrames=true</code>, TCP port
<code>14100</code>.</li>
<li>Note <code>mmapPath</code> (currently <code>{mmapPath}</code>) and
<code>controlPort</code> (currently <code>{controlPort}</code>).</li>
<li>Play a live camera, or sample throws:
<a href="https://drive.google.com/file/d/1hEI4HMODwAu6Pm9P4oDecePbfv--Lwbg/view?usp=drive_link">Davis346 Roshambo throws</a>
(chip <b>Davis346blue</b>).</li>
<li>In <code>dextra-roshambo-python</code>:
<pre>
python consumer.py --jaer-mmap {mmapPath} --serial_port None --windowed
</pre>
<code>--jaer-tcp 127.0.0.1:{controlPort}</code> is the default with
<code>--jaer-mmap</code>. Use <code>--jaer-tcp None</code> to poll mmap sequence
numbers only.</li>
</ol>
<p>Linux/macOS typical path: <code>/tmp/jaer_dvs_frames.mmap</code>.
Windows: <code>%TEMP%\\jaer_dvs_frames.mmap</code>.</p>
<p>If the CNN image looks upside-down, toggle <code>flipY</code> so row 0 matches
the training set (OpenCV / top-left origin). Default is off (jAER lower-left).</p>
<p>Pixels are rectified event counts clipped at <code>dvsGrayScale</code> and
scaled to 0–255 (same as dextra-roshambo-python <code>producer.py</code>), not
the 3-sigma display pixmap. Each slot is little-endian. Slot size =
64 + width&times;height bytes. Magic <code>JAER</code>, <code>channels=1</code>,
<code>dtype=U8</code>. Byte at <code>(x, y)</code> is at
<code>offset = 64 + y * width + x</code>.</p>
<hr>
<h3>EventWindows (FireNet / E2VID)</h3>
<ol>
<li>Set <code>outputMode</code> to <b>EventWindows</b>. Leave
<code>eventsPerWindow=0</code> to use
<code>width &times; height &times; numEventsPerPixel</code> (0.35, same as E2VID).
Default TCP port is <code>14101</code>.</li>
<li>Leave <code>flipY</code> checked (default on in this mode): jAER
<code>y=0</code> is lower-left; Python / OpenCV / FireNet use upper-left, so
exported <code>y</code> is <code>height-1-y</code>.</li>
<li>In <code>rpg_e2vid</code>:
<pre>
uv run python live_reconstruction.py -c pretrained/E2VID_lightweight.pth.tar --auto_hdr --display --show_events
</pre>
Default TCP is <code>127.0.0.1:{controlPort}</code>.</li>
</ol>
<p>Default path: <code>%TEMP%\\jaer_dvs_events.mmap</code> (Windows) or
<code>/tmp/jaer_dvs_events.mmap</code>.</p>
<p>Double-buffered mmap: two slots of 64-byte header plus
<code>maxEvents &times; 16</code> bytes. Magic <code>JAER</code>,
<code>dtype=EVENT</code> (2). Event record (little-endian, 16 bytes):
<code>int64 t_us</code>, <code>uint16 x</code>, <code>uint16 y</code>,
<code>uint8 p</code> (0=Off, 1=On), 3 pad bytes.
<code>t_us</code> is the 32-bit jAER timestamp zero-extended.
<code>seq</code> is written last (publication fence).</p>
<hr>
<h3>Protocol (shared)</h3>
<p>Frames and windows share a <b>double-buffered mmap</b> and a localhost
<b>TCP control channel</b> (JSON lines on <code>127.0.0.1</code>):
<code>HELLO</code> on connect, <code>FRAME_READY</code> per buffer.
If TCP is unused, the consumer can poll slot headers for a new sequence number.</p>
<p>jAER does not load TensorFlow or PyTorch; CNN weights stay in the Python project.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DNNOutputViaSharedMemory extends DvsFramerSingleFrame {

    public static final int PROTOCOL_VERSION = 1;
    public static final int HEADER_BYTES = 64;
    public static final int NUM_BUFFERS = 2;
    public static final int EVENT_BYTES = 16;
    public static final int DTYPE_U8 = 1;
    public static final int DTYPE_EVENT = 2;
    public static final int DEFAULT_CONTROL_PORT_FRAMES = 14100;
    public static final int DEFAULT_CONTROL_PORT_EVENTS = 14101;
    public static final int DEFAULT_MAX_EVENTS = 100000;
    public static final byte[] MAGIC = new byte[]{'J', 'A', 'E', 'R'};

    public static final String GROUP_OUTPUT = "1. Output";
    public static final String GROUP_EVENTS = "2. Event windows (FireNet)";
    public static final String GROUP_FRAMES = "3. Frames (Roshambo)";
    public static final String GROUP_CROPPING = "4. Cropping";

    /**
     * Payload written to each mmap slot.
     */
    public enum OutputMode {
        EventCountFrames,
        EventWindows
    }

    /**
     * How EventWindows slices are closed (independent of DvsFramer frame slicing).
     */
    public enum EventWindowTimeSliceMethod {
        EventCount,
        TimeIntervalUs
    }

    private OutputMode outputMode = OutputMode.valueOf(
            getString("outputMode", OutputMode.EventCountFrames.name()));
    private String mmapPath;
    private int controlPort;
    private boolean flipY;
    private boolean deleteMmapOnCleanup = getBoolean("deleteMmapOnCleanup", true);

    private int eventsPerWindow = getInt("eventsPerWindow", 0);
    private float numEventsPerPixel = getFloat("numEventsPerPixel", 0.35f);
    private EventWindowTimeSliceMethod eventWindowTimeSliceMethod = EventWindowTimeSliceMethod.valueOf(
            getString("eventWindowTimeSliceMethod", EventWindowTimeSliceMethod.EventCount.name()));
    private int eventWindowDurationUs = getInt("eventWindowDurationUs", 33000);
    private int maxEvents = getInt("maxEvents", DEFAULT_MAX_EVENTS);

    private RandomAccessFile mmapFile;
    private FileChannel mmapChannel;
    private MappedByteBuffer mapped;
    private int mappedWidth;
    private int mappedHeight;
    private int mappedMaxEvents;
    private int nextSeq = 1;

    private long[] windowTUs;
    private int[] windowX;
    private int[] windowY;
    private byte[] windowP;
    private int eventCount;
    private long firstTimestampUs;
    private long lastTimestampUs;
    private boolean windowStarted;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Thread notifyThread;
    private final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();
    private final AtomicBoolean publisherRunning = new AtomicBoolean(false);
    private final AtomicReference<String> pendingNotify = new AtomicReference<>();
    private final Object notifyLock = new Object();
    private volatile String lastError;

    public DNNOutputViaSharedMemory(AEChip chip) {
        super(chip);
        loadSettingsForMode();
        setDvsGrayScale(getInt("dvsGrayScale", 16));
        setNormalizeFrame(getBoolean("normalizeFrame", false));
        setShowFrames(getBoolean("showFrames", true));
        setPropertyTooltipBold(GROUP_OUTPUT, "outputMode",
                "EventCountFrames: 64x64 uint8 histograms (Roshambo). EventWindows: packed (t,x,y,p) (FireNet / E2VID)");
        setPropertyTooltip(GROUP_OUTPUT, "mmapPath", "memory-mapped file path shared with the Python consumer");
        setPropertyTooltip(GROUP_OUTPUT, "controlPort", "localhost TCP port for JSON-lines HELLO / FRAME_READY (127.0.0.1 only)");
        setPropertyTooltip(GROUP_OUTPUT, "flipY",
                "EventCountFrames: write row 0 as sensor y=height-1 (OpenCV). EventWindows: export y with 0 at the top (default on)");
        setPropertyTooltip(GROUP_OUTPUT, "deleteMmapOnCleanup", "delete the mmap file when the filter is disabled or jAER exits");
        setPropertyTooltip(GROUP_FRAMES, "outputImageWidth", "histogram width (Roshambo default 64)");
        setPropertyTooltip(GROUP_FRAMES, "outputImageHeight", "histogram height (Roshambo default 64)");
        setPropertyTooltip(GROUP_FRAMES, "dvsGrayScale", "full-scale event count; pixels scaled to 0-255");
        setPropertyTooltip(GROUP_FRAMES, "dvsEventsPerFrame", "events accumulated per histogram frame");
        setPropertyTooltip(GROUP_FRAMES, "timeSliceMethod", "close a histogram after N events or after timeDurationUsPerFrame");
        setPropertyTooltip(GROUP_FRAMES, "timeDurationUsPerFrame", "histogram duration when timeSliceMethod is TimeIntervalUs");
        setPropertyTooltip(GROUP_FRAMES, "rectifyPolarities", "ignore ON/OFF sign (Roshambo default on)");
        setPropertyTooltip(GROUP_FRAMES, "normalizeFrame", "3-sigma normalize (Roshambo default off)");
        setPropertyTooltip(GROUP_FRAMES, "showFrames", "show accumulated histogram in a separate window");
        setPropertyTooltip(GROUP_CROPPING, "frameCutTop", "pixels to cut from the top of the original image before downsampling");
        setPropertyTooltip(GROUP_CROPPING, "frameCutBottom", "pixels to cut from the bottom of the original image before downsampling");
        setPropertyTooltip(GROUP_CROPPING, "frameCutLeft", "pixels to cut from the left of the original image before downsampling");
        setPropertyTooltip(GROUP_CROPPING, "frameCutRight", "pixels to cut from the right of the original image before downsampling");
        setPropertyTooltip(GROUP_EVENTS, "eventsPerWindow", "events per window when EventCount; 0 = width*height*numEventsPerPixel");
        setPropertyTooltip(GROUP_EVENTS, "numEventsPerPixel", "used when eventsPerWindow=0 (E2VID default 0.35)");
        setPropertyTooltip(GROUP_EVENTS, "eventWindowTimeSliceMethod", "close a window after N events or after eventWindowDurationUs");
        setPropertyTooltip(GROUP_EVENTS, "eventWindowDurationUs", "window duration in microseconds when TimeIntervalUs");
        setPropertyTooltip(GROUP_EVENTS, "maxEvents", "mmap slot capacity (must be >= events per window)");
        ensureWindowCapacity(maxEvents);
    }

    @Override
    public String getHelp() {
        String html = super.getHelp();
        if (html == null) {
            return null;
        }
        String path = mmapPath != null ? mmapPath : defaultMmapPath(outputMode);
        return html.replace("{mmapPath}", path).replace("{controlPort}", Integer.toString(controlPort));
    }

    public static DNNOutputViaSharedMemory find(AEChip chip) {
        if (chip == null || chip.getFilterChain() == null) {
            return null;
        }
        return (DNNOutputViaSharedMemory) chip.getFilterChain().findFilter(DNNOutputViaSharedMemory.class);
    }

    /**
     * Appends a disabled sender if the saved filter chain does not include it.
     */
    public static void ensurePresent(AEChip chip) {
        if (chip == null) {
            return;
        }
        FilterChain chain = chip.getFilterChain();
        if (chain == null || chain.findFilter(DNNOutputViaSharedMemory.class) != null) {
            return;
        }
        try {
            DNNOutputViaSharedMemory f = new DNNOutputViaSharedMemory(chip);
            chain.add(f);
            f.initFilter();
            f.setPreferredEnabledState();
            ArrayList<String> names = new ArrayList<>();
            for (EventFilter2D x : chain) {
                names.add(x.getClass().getName());
            }
            chain.storePreferredFiltersForChip(names);
            log.info("Appended DNNOutputViaSharedMemory to filter chain for " + chip.getClass().getSimpleName());
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not add DNNOutputViaSharedMemory: " + e, e);
        }
    }

    /**
     * Maps pre-merge sender class names to this combined filter.
     */
    public static String remapLegacyClassName(String className) {
        if (className == null) {
            return null;
        }
        if (className.equals("net.sf.jaer.util.avioutput.SharedMemoryDVSFrameSender")
                || className.equals("net.sf.jaer.util.avioutput.SharedMemoryEventWindowSender")) {
            return DNNOutputViaSharedMemory.class.getName();
        }
        return className;
    }

    public static String defaultMmapPath(OutputMode mode) {
        String name = mode == OutputMode.EventWindows ? "jaer_dvs_events.mmap" : "jaer_dvs_frames.mmap";
        return new File(System.getProperty("java.io.tmpdir"), name).getAbsolutePath();
    }

    public static int defaultControlPort(OutputMode mode) {
        return mode == OutputMode.EventWindows ? DEFAULT_CONTROL_PORT_EVENTS : DEFAULT_CONTROL_PORT_FRAMES;
    }

    public static boolean defaultFlipY(OutputMode mode) {
        return mode == OutputMode.EventWindows;
    }

    public static int slotSizeU8(int width, int height) {
        return HEADER_BYTES + Math.max(0, width) * Math.max(0, height);
    }

    public static int fileSizeU8(int width, int height) {
        return NUM_BUFFERS * slotSizeU8(width, height);
    }

    public static int slotSizeEvents(int maxEvents) {
        return HEADER_BYTES + Math.max(0, maxEvents) * EVENT_BYTES;
    }

    public static int fileSizeEvents(int maxEvents) {
        return NUM_BUFFERS * slotSizeEvents(maxEvents);
    }

    private String mmapPathKey() {
        return "mmapPath." + outputMode.name();
    }

    private String controlPortKey() {
        return "controlPort." + outputMode.name();
    }

    private String flipYKey() {
        return "flipY." + outputMode.name();
    }

    private void loadSettingsForMode() {
        mmapPath = getString(mmapPathKey(), defaultMmapPath(outputMode));
        controlPort = getInt(controlPortKey(), defaultControlPort(outputMode));
        flipY = getBoolean(flipYKey(), defaultFlipY(outputMode));
    }

    private int chipWidth() {
        return chip != null ? Math.max(1, chip.getSizeX()) : 1;
    }

    private int chipHeight() {
        return chip != null ? Math.max(1, chip.getSizeY()) : 1;
    }

    int targetEventsPerWindow() {
        if (eventsPerWindow > 0) {
            return eventsPerWindow;
        }
        return Math.max(1, Math.round(chipWidth() * chipHeight() * numEventsPerPixel));
    }

    private void ensureWindowCapacity(int n) {
        if (n < 1) {
            n = 1;
        }
        if (windowTUs != null && windowTUs.length >= n) {
            return;
        }
        windowTUs = new long[n];
        windowX = new int[n];
        windowY = new int[n];
        windowP = new byte[n];
    }

    @Override
    public void initFilter() {
        super.initFilter();
        resetEventWindow();
        if (isFilterEnabled()) {
            startPublisher();
        }
    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        resetEventWindow();
    }

    private void resetEventWindow() {
        eventCount = 0;
        windowStarted = false;
        firstTimestampUs = 0;
        lastTimestampUs = 0;
    }

    @Override
    synchronized public void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            startPublisher();
        } else {
            stopPublisher();
        }
    }

    @Override
    synchronized public void cleanup() {
        stopPublisher();
        super.cleanup();
    }

    @Override
    synchronized public void setOutputImageWidth(int width) {
        super.setOutputImageWidth(width);
        if (outputMode == OutputMode.EventCountFrames && publisherRunning.get()) {
            remapU8(width, getOutputImageHeight());
        }
    }

    @Override
    synchronized public void setOutputImageHeight(int height) {
        super.setOutputImageHeight(height);
        if (outputMode == OutputMode.EventCountFrames && publisherRunning.get()) {
            remapU8(getOutputImageWidth(), height);
        }
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!isFilterEnabled() || in == null || !publisherRunning.get()) {
            return in;
        }
        if (outputMode == OutputMode.EventCountFrames) {
            return super.filterPacket(in);
        }
        return filterEventWindows(in);
    }

    @Override
    protected void processDvsFrame(DvsFrame frame) {
        if (outputMode != OutputMode.EventCountFrames) {
            return;
        }
        publishU8Frame(frame);
    }

    private EventPacket<? extends BasicEvent> filterEventWindows(EventPacket<? extends BasicEvent> in) {
        int width = chipWidth();
        int height = chipHeight();
        int target = targetEventsPerWindow();
        int cap = Math.max(maxEvents, target);
        if (mapped == null || mappedWidth != width || mappedHeight != height || mappedMaxEvents < cap) {
            maxEvents = cap;
            remapEvents(width, height, cap);
        }
        ensureWindowCapacity(mappedMaxEvents);
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
            if (x < 0 || y < 0 || x >= width || y >= height) {
                continue;
            }
            if (flipY) {
                y = height - 1 - y;
            }
            long tUs = pe.timestamp & 0xffffffffL;
            byte p = (byte) (pe.polarity == PolarityEvent.Polarity.On ? 1 : 0);
            if (!windowStarted) {
                windowStarted = true;
                firstTimestampUs = tUs;
            }
            lastTimestampUs = tUs;
            if (eventCount >= mappedMaxEvents) {
                publishEventWindow(width, height);
            }
            windowTUs[eventCount] = tUs;
            windowX[eventCount] = x;
            windowY[eventCount] = y;
            windowP[eventCount] = p;
            eventCount++;
            if (eventWindowComplete(target, tUs)) {
                publishEventWindow(width, height);
            }
        }
        return in;
    }

    private boolean eventWindowComplete(int target, long tUs) {
        if (!windowStarted || eventCount < 1) {
            return false;
        }
        if (eventWindowTimeSliceMethod == EventWindowTimeSliceMethod.TimeIntervalUs) {
            long dt = tUs - firstTimestampUs;
            if (dt < 0) {
                dt += 0x100000000L;
            }
            return dt >= eventWindowDurationUs;
        }
        return eventCount >= target;
    }

    synchronized void startPublisher() {
        if (publisherRunning.get()) {
            return;
        }
        try {
            if (outputMode == OutputMode.EventWindows) {
                int target = targetEventsPerWindow();
                int cap = Math.max(maxEvents, target);
                maxEvents = cap;
                ensureWindowCapacity(cap);
                remapEvents(chipWidth(), chipHeight(), cap);
            } else {
                remapU8(getOutputImageWidth(), getOutputImageHeight());
            }
            startControlServer();
            publisherRunning.set(true);
            lastError = null;
            if (outputMode == OutputMode.EventWindows) {
                log.info(String.format("DNNOutputViaSharedMemory EventWindows mmap=%s  TCP=127.0.0.1:%d  %dx%d  maxEvents=%d  N=%d",
                        mmapPath, controlPort, chipWidth(), chipHeight(), mappedMaxEvents, targetEventsPerWindow()));
            } else {
                log.info(String.format("DNNOutputViaSharedMemory EventCountFrames mmap=%s  TCP=127.0.0.1:%d  %dx%d",
                        mmapPath, controlPort, getOutputImageWidth(), getOutputImageHeight()));
            }
        } catch (IOException e) {
            lastError = e.toString();
            log.warning("could not start shared-memory publisher: " + e);
            stopPublisher();
        }
    }

    synchronized void stopPublisher() {
        publisherRunning.set(false);
        stopControlServer();
        unmap(deleteMmapOnCleanup);
        resetEventWindow();
    }

    private void remapU8(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (mapped != null && mappedWidth == width && mappedHeight == height && mappedMaxEvents == 0) {
            return;
        }
        unmap(false);
        mapFile(fileSizeU8(width, height), width, height, 0);
        writeEmptyU8Headers();
        if (mapped != null) {
            log.info(String.format("mapped %s (%d bytes, 2 x %dx%d uint8)", mmapPath, fileSizeU8(width, height), width, height));
        }
    }

    private void remapEvents(int width, int height, int maxEv) {
        if (width <= 0 || height <= 0 || maxEv <= 0) {
            return;
        }
        if (mapped != null && mappedWidth == width && mappedHeight == height && mappedMaxEvents == maxEv) {
            return;
        }
        unmap(false);
        mapFile(fileSizeEvents(maxEv), width, height, maxEv);
        writeEmptyEventHeaders();
        if (mapped != null) {
            log.info(String.format("mapped %s (%d bytes, 2 x %d events, %dx%d)",
                    mmapPath, fileSizeEvents(maxEv), maxEv, width, height));
        }
    }

    private void mapFile(long size, int width, int height, int maxEv) {
        try {
            File f = new File(mmapPath);
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            mmapFile = new RandomAccessFile(f, "rw");
            mmapFile.setLength(size);
            mmapChannel = mmapFile.getChannel();
            mapped = mmapChannel.map(FileChannel.MapMode.READ_WRITE, 0, size);
            mapped.order(ByteOrder.LITTLE_ENDIAN);
            mappedWidth = width;
            mappedHeight = height;
            mappedMaxEvents = maxEv;
        } catch (IOException e) {
            log.warning("could not map " + mmapPath + ": " + e);
            unmap(false);
        }
    }

    private void writeEmptyU8Headers() {
        if (mapped == null) {
            return;
        }
        for (int slot = 0; slot < NUM_BUFFERS; slot++) {
            writeU8Header(slot, 0, mappedWidth, mappedHeight, 0);
        }
        mapped.force();
    }

    private void writeEmptyEventHeaders() {
        if (mapped == null) {
            return;
        }
        for (int slot = 0; slot < NUM_BUFFERS; slot++) {
            writeEventHeader(slot, 0, mappedWidth, mappedHeight, 0, 0);
        }
        mapped.force();
    }

    private void unmap(boolean deleteFile) {
        mapped = null;
        mappedWidth = 0;
        mappedHeight = 0;
        mappedMaxEvents = 0;
        if (mmapChannel != null) {
            try {
                mmapChannel.close();
            } catch (IOException e) {
                log.fine("closing mmap channel: " + e);
            }
            mmapChannel = null;
        }
        if (mmapFile != null) {
            try {
                mmapFile.close();
            } catch (IOException e) {
                log.fine("closing mmap file: " + e);
            }
            mmapFile = null;
        }
        if (deleteFile && mmapPath != null) {
            File f = new File(mmapPath);
            if (f.exists() && !f.delete()) {
                log.fine("could not delete mmap file " + mmapPath + " (still mapped by another process?)");
            }
        }
    }

    private void publishU8Frame(DvsFrame frame) {
        if (!publisherRunning.get()) {
            return;
        }
        int width = frame.getWidth();
        int height = frame.getHeight();
        int[] eventSum = frame.getEventSum();
        if (eventSum == null || width <= 0 || height <= 0) {
            return;
        }
        if (mapped == null || mappedWidth != width || mappedHeight != height || mappedMaxEvents != 0) {
            remapU8(width, height);
        }
        if (mapped == null) {
            return;
        }
        int slot = (nextSeq & 1);
        int seq = nextSeq++;
        int gray = Math.max(1, getDvsGrayScale());
        int n = width * height;
        int pixelBase = slot * slotSizeU8(width, height) + HEADER_BYTES;
        for (int i = 0; i < n; i++) {
            int x = i % width;
            int y = i / width;
            int srcY = flipY ? (height - 1 - y) : y;
            int src = x + width * srcY;
            int count = eventSum[src];
            if (count < 0) {
                count = -count;
            }
            if (count > gray) {
                count = gray;
            }
            int u8 = (count * 255) / gray;
            mapped.put(pixelBase + i, (byte) u8);
        }
        writeU8Header(slot, seq, width, height, frame.getLastTimestampUs() & 0xffffffffL);
        mapped.force();
        String json = String.format(
                "{\"type\":\"FRAME_READY\",\"seq\":%d,\"width\":%d,\"height\":%d,\"channels\":1,\"dtype\":\"U8\",\"buffer_index\":%d,\"timestamp_us\":%d,\"stride_bytes\":%d}",
                seq, width, height, slot, frame.getLastTimestampUs() & 0xffffffffL, width);
        queueNotify(json);
    }

    private void publishEventWindow(int width, int height) {
        if (!publisherRunning.get() || eventCount < 1 || mapped == null) {
            resetEventWindow();
            return;
        }
        int n = eventCount;
        int slot = (nextSeq & 1);
        int seq = nextSeq++;
        int eventBase = slot * slotSizeEvents(mappedMaxEvents) + HEADER_BYTES;
        mapped.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            int off = eventBase + i * EVENT_BYTES;
            mapped.putLong(off, windowTUs[i]);
            mapped.putShort(off + 8, (short) windowX[i]);
            mapped.putShort(off + 10, (short) windowY[i]);
            mapped.put(off + 12, windowP[i]);
            mapped.put(off + 13, (byte) 0);
            mapped.put(off + 14, (byte) 0);
            mapped.put(off + 15, (byte) 0);
        }
        writeEventHeader(slot, seq, width, height, n, lastTimestampUs);
        mapped.force();
        String json = String.format(
                "{\"type\":\"FRAME_READY\",\"seq\":%d,\"width\":%d,\"height\":%d,\"event_count\":%d,\"dtype\":\"EVENT\",\"buffer_index\":%d,\"timestamp_us\":%d,\"event_bytes\":%d}",
                seq, width, height, n, slot, lastTimestampUs, EVENT_BYTES);
        queueNotify(json);
        resetEventWindow();
    }

    private void writeU8Header(int slot, int seq, int width, int height, long timestampUs) {
        int base = slot * slotSizeU8(width, height);
        mapped.order(ByteOrder.LITTLE_ENDIAN);
        mapped.put(base, MAGIC[0]);
        mapped.put(base + 1, MAGIC[1]);
        mapped.put(base + 2, MAGIC[2]);
        mapped.put(base + 3, MAGIC[3]);
        mapped.putShort(base + 4, (short) PROTOCOL_VERSION);
        mapped.putShort(base + 6, (short) 0);
        mapped.putInt(base + 12, width);
        mapped.putInt(base + 16, height);
        mapped.putInt(base + 20, width);
        mapped.putShort(base + 24, (short) 1);
        mapped.putShort(base + 26, (short) DTYPE_U8);
        mapped.putLong(base + 28, timestampUs);
        mapped.putInt(base + 8, seq);
    }

    private void writeEventHeader(int slot, int seq, int width, int height, int eventCount, long timestampUs) {
        int base = slot * slotSizeEvents(mappedMaxEvents);
        mapped.order(ByteOrder.LITTLE_ENDIAN);
        mapped.put(base, MAGIC[0]);
        mapped.put(base + 1, MAGIC[1]);
        mapped.put(base + 2, MAGIC[2]);
        mapped.put(base + 3, MAGIC[3]);
        mapped.putShort(base + 4, (short) PROTOCOL_VERSION);
        mapped.putShort(base + 6, (short) 0);
        mapped.putInt(base + 12, width);
        mapped.putInt(base + 16, height);
        mapped.putInt(base + 20, eventCount);
        mapped.putShort(base + 24, (short) EVENT_BYTES);
        mapped.putShort(base + 26, (short) DTYPE_EVENT);
        mapped.putLong(base + 28, timestampUs);
        mapped.putInt(base + 36, mappedMaxEvents);
        mapped.putInt(base + 8, seq);
    }

    private void queueNotify(String json) {
        pendingNotify.set(json);
        synchronized (notifyLock) {
            notifyLock.notifyAll();
        }
    }

    private void startControlServer() throws IOException {
        stopControlServer();
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), controlPort), 8);
        serverSocket = ss;
        acceptThread = new Thread(this::acceptLoop, "DNNOutputViaSharedMemory-accept");
        acceptThread.setDaemon(true);
        notifyThread = new Thread(this::notifyLoop, "DNNOutputViaSharedMemory-notify");
        notifyThread.setDaemon(true);
        acceptThread.start();
        notifyThread.start();
        log.info("DNNOutputViaSharedMemory control server listening on 127.0.0.1:" + controlPort);
    }

    private void stopControlServer() {
        ServerSocket ss = serverSocket;
        serverSocket = null;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException e) {
                log.fine("closing control server: " + e);
            }
        }
        Thread acc = acceptThread;
        acceptThread = null;
        if (acc != null) {
            acc.interrupt();
            try {
                acc.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Thread ntf = notifyThread;
        notifyThread = null;
        if (ntf != null) {
            ntf.interrupt();
            synchronized (notifyLock) {
                notifyLock.notifyAll();
            }
            try {
                ntf.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        for (Socket s : clients) {
            try {
                s.close();
            } catch (IOException e) {
                log.fine("closing client: " + e);
            }
        }
        clients.clear();
    }

    private void acceptLoop() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket s = serverSocket.accept();
                s.setTcpNoDelay(true);
                s.setSoTimeout(0);
                clients.add(s);
                sendLine(s, helloJson());
                log.info("Python control client connected from " + s.getRemoteSocketAddress() + " (" + clients.size() + " clients)");
            } catch (SocketException e) {
                if (serverSocket == null || serverSocket.isClosed()) {
                    break;
                }
                log.fine("accept: " + e);
            } catch (IOException e) {
                if (serverSocket == null || serverSocket.isClosed()) {
                    break;
                }
                log.warning("accept failed: " + e);
            }
        }
    }

    private void notifyLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            String msg;
            synchronized (notifyLock) {
                msg = pendingNotify.getAndSet(null);
                if (msg == null) {
                    try {
                        notifyLock.wait(200);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }
            }
            broadcast(msg);
        }
    }

    private void broadcast(String jsonLine) {
        Iterator<Socket> it = clients.iterator();
        while (it.hasNext()) {
            Socket s = it.next();
            if (!sendLine(s, jsonLine)) {
                clients.remove(s);
                try {
                    s.close();
                } catch (IOException e) {
                    log.fine("closing dead client: " + e);
                }
            }
        }
    }

    private boolean sendLine(Socket s, String jsonLine) {
        try {
            OutputStream out = s.getOutputStream();
            out.write((jsonLine + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            log.fine("control write failed: " + e);
            return false;
        }
    }

    private String helloJson() {
        if (outputMode == OutputMode.EventWindows) {
            return String.format(
                    "{\"type\":\"HELLO\",\"version\":%d,\"kind\":\"event_window\",\"path\":\"%s\",\"width\":%d,\"height\":%d,\"header_bytes\":%d,\"num_buffers\":%d,\"event_bytes\":%d,\"max_events\":%d,\"dtype\":\"EVENT\"}",
                    PROTOCOL_VERSION, jsonEscape(mmapPath), chipWidth(), chipHeight(), HEADER_BYTES, NUM_BUFFERS,
                    EVENT_BYTES, mappedMaxEvents > 0 ? mappedMaxEvents : maxEvents);
        }
        return String.format(
                "{\"type\":\"HELLO\",\"version\":%d,\"path\":\"%s\",\"width\":%d,\"height\":%d,\"header_bytes\":%d,\"num_buffers\":%d,\"channels\":1,\"dtype\":\"U8\"}",
                PROTOCOL_VERSION, jsonEscape(mmapPath), getOutputImageWidth(), getOutputImageHeight(), HEADER_BYTES, NUM_BUFFERS);
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Built only when the canvas asks (overlay preference on). Do not call from the publish path.
     */
    public String getOverlayText() {
        if (!isFilterEnabled()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(96);
        sb.append("Writing mmap  ").append(outputMode);
        sb.append('\n');
        if (!publisherRunning.get() || mapped == null) {
            sb.append("not publishing");
            if (lastError != null && !lastError.isEmpty()) {
                sb.append("  ").append(lastError);
            }
        } else if (outputMode == OutputMode.EventWindows) {
            sb.append(Math.max(0, nextSeq - 1)).append(" windows  ");
            sb.append(chipWidth()).append('x').append(chipHeight());
            sb.append("  TCP :").append(controlPort);
            int n = clients.size();
            sb.append("  (").append(n).append(n == 1 ? " client)" : " clients)");
        } else {
            sb.append(Math.max(0, nextSeq - 1)).append(" frames  ");
            sb.append(getOutputImageWidth()).append('x').append(getOutputImageHeight());
            sb.append("  TCP :").append(controlPort);
            int n = clients.size();
            sb.append("  (").append(n).append(n == 1 ? " client)" : " clients)");
        }
        sb.append('\n').append(shortOverlayPath(mmapPath));
        return sb.toString();
    }

    private static String shortOverlayPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        if (path.length() <= 56) {
            return path;
        }
        return "…" + path.substring(path.length() - 55);
    }

    public OutputMode getOutputMode() {
        return outputMode;
    }

    public synchronized void setOutputMode(OutputMode outputMode) {
        OutputMode old = this.outputMode;
        if (outputMode == null || outputMode == old) {
            return;
        }
        boolean wasRunning = publisherRunning.get();
        if (wasRunning) {
            stopPublisher();
        }
        this.outputMode = outputMode;
        putString("outputMode", outputMode.name());
        String oldPath = mmapPath;
        int oldPort = controlPort;
        boolean oldFlip = flipY;
        loadSettingsForMode();
        getSupport().firePropertyChange("outputMode", old, outputMode);
        getSupport().firePropertyChange("mmapPath", oldPath, mmapPath);
        getSupport().firePropertyChange("controlPort", oldPort, controlPort);
        getSupport().firePropertyChange("flipY", oldFlip, flipY);
        if (outputMode == OutputMode.EventWindows) {
            disposeShowFramesWindow();
        } else if (isShowFramesPreviewActive() && lastDvsFrame != null) {
            final DvsFrame f = lastDvsFrame;
            final float[] pixmapCopy = java.util.Arrays.copyOf(f.getImage(), f.getImage().length);
            final int w = f.getWidth();
            final int h = f.getHeight();
            javax.swing.SwingUtilities.invokeLater(() -> drawCopied(w, h, pixmapCopy));
        }
        if (wasRunning) {
            startPublisher();
        }
    }

    @Override
    protected boolean isShowFramesPreviewActive() {
        return super.isShowFramesPreviewActive() && outputMode == OutputMode.EventCountFrames;
    }

    public String getMmapPath() {
        return mmapPath;
    }

    public synchronized void setMmapPath(String mmapPath) {
        String old = this.mmapPath;
        this.mmapPath = mmapPath;
        putString(mmapPathKey(), mmapPath);
        getSupport().firePropertyChange("mmapPath", old, mmapPath);
        if (publisherRunning.get()) {
            if (outputMode == OutputMode.EventWindows) {
                mappedWidth = 0;
                remapEvents(chipWidth(), chipHeight(), Math.max(maxEvents, targetEventsPerWindow()));
            } else {
                mappedWidth = 0;
                remapU8(getOutputImageWidth(), getOutputImageHeight());
            }
        }
    }

    public int getControlPort() {
        return controlPort;
    }

    public synchronized void setControlPort(int controlPort) {
        int port = Math.max(1, Math.min(65535, controlPort));
        int old = this.controlPort;
        this.controlPort = port;
        putInt(controlPortKey(), this.controlPort);
        getSupport().firePropertyChange("controlPort", old, this.controlPort);
        boolean listening = serverSocket != null && !serverSocket.isClosed()
                && serverSocket.getLocalPort() == this.controlPort;
        if (isFilterEnabled() && !listening) {
            try {
                startControlServer();
            } catch (IOException e) {
                lastError = e.toString();
                log.warning("could not bind control server on 127.0.0.1:" + this.controlPort + ": " + e);
            }
        }
    }

    public boolean isFlipY() {
        return flipY;
    }

    public void setFlipY(boolean flipY) {
        boolean old = this.flipY;
        this.flipY = flipY;
        putBoolean(flipYKey(), flipY);
        getSupport().firePropertyChange("flipY", old, flipY);
    }

    public boolean isDeleteMmapOnCleanup() {
        return deleteMmapOnCleanup;
    }

    public void setDeleteMmapOnCleanup(boolean deleteMmapOnCleanup) {
        this.deleteMmapOnCleanup = deleteMmapOnCleanup;
        putBoolean("deleteMmapOnCleanup", deleteMmapOnCleanup);
    }

    public int getEventsPerWindow() {
        return eventsPerWindow;
    }

    public void setEventsPerWindow(int eventsPerWindow) {
        int old = this.eventsPerWindow;
        this.eventsPerWindow = Math.max(0, eventsPerWindow);
        putInt("eventsPerWindow", this.eventsPerWindow);
        getSupport().firePropertyChange("eventsPerWindow", old, this.eventsPerWindow);
    }

    public float getNumEventsPerPixel() {
        return numEventsPerPixel;
    }

    public void setNumEventsPerPixel(float numEventsPerPixel) {
        float old = this.numEventsPerPixel;
        this.numEventsPerPixel = Math.max(0.01f, numEventsPerPixel);
        putFloat("numEventsPerPixel", this.numEventsPerPixel);
        getSupport().firePropertyChange("numEventsPerPixel", old, this.numEventsPerPixel);
    }

    public EventWindowTimeSliceMethod getEventWindowTimeSliceMethod() {
        return eventWindowTimeSliceMethod;
    }

    public void setEventWindowTimeSliceMethod(EventWindowTimeSliceMethod eventWindowTimeSliceMethod) {
        EventWindowTimeSliceMethod old = this.eventWindowTimeSliceMethod;
        this.eventWindowTimeSliceMethod = eventWindowTimeSliceMethod;
        putString("eventWindowTimeSliceMethod", eventWindowTimeSliceMethod.name());
        getSupport().firePropertyChange("eventWindowTimeSliceMethod", old, eventWindowTimeSliceMethod);
    }

    public int getEventWindowDurationUs() {
        return eventWindowDurationUs;
    }

    public void setEventWindowDurationUs(int eventWindowDurationUs) {
        int old = this.eventWindowDurationUs;
        this.eventWindowDurationUs = Math.max(1, eventWindowDurationUs);
        putInt("eventWindowDurationUs", this.eventWindowDurationUs);
        getSupport().firePropertyChange("eventWindowDurationUs", old, this.eventWindowDurationUs);
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public synchronized void setMaxEvents(int maxEvents) {
        int old = this.maxEvents;
        this.maxEvents = Math.max(1, maxEvents);
        putInt("maxEvents", this.maxEvents);
        getSupport().firePropertyChange("maxEvents", old, this.maxEvents);
        if (outputMode == OutputMode.EventWindows && publisherRunning.get()) {
            remapEvents(chipWidth(), chipHeight(), Math.max(this.maxEvents, targetEventsPerWindow()));
        }
    }
}
