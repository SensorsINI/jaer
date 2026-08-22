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
 * Publishes packed DVS event windows to a memory-mapped file for a local Python
 * consumer (FireNet / E2VID), with a localhost TCP JSON-lines control channel.
 * <p>
 * Same HELLO / FRAME_READY control path as {@link SharedMemoryDVSFrameSender},
 * but each slot holds a variable-length event window instead of a uint8
 * histogram frame. Each event is 16 bytes little-endian:
 * {@code int64 t_us, uint16 x, uint16 y, uint8 p, pad[3]}. Polarity is
 * {@code 0=Off, 1=On} (rpg_e2vid convention).
 */
@Description("Publishes DVS event windows to mmap + localhost TCP for FireNet / E2VID")
@Help("""
<html>
<body>
<h2>SharedMemoryEventWindowSender</h2>
<p>Publishes packed <b>(t, x, y, p)</b> event windows to a memory-mapped file so
<a href="https://github.com/SensorsINI/rpg_e2vid">rpg_e2vid / FireNet</a> can reconstruct
frames. This is <i>not</i> the 64&times;64 uint8 histogram from
<code>SharedMemoryDVSFrameSender</code>.</p>
<hr>
<h3>1. Enable this filter</h3>
<ol>
<li>Check <b>Enabled</b>. Leave <code>eventsPerWindow=0</code> to use
<code>width &times; height &times; numEventsPerPixel</code> (0.35, same as E2VID).</li>
<li>Note <code>mmapPath</code> (currently <code>{mmapPath}</code>) and
<code>controlPort</code> (currently <code>{controlPort}</code>).</li>
</ol>
<p>Default path: <code>%TEMP%\\jaer_dvs_events.mmap</code> (Windows) or
<code>/tmp/jaer_dvs_events.mmap</code>.</p>
<h3>2. Run FireNet / E2VID</h3>
<pre>
uv run python live_reconstruction.py -c pretrained/E2VID_lightweight.pth.tar --auto_hdr --display --show_events
</pre>
<p>Default TCP is <code>127.0.0.1:{controlPort}</code>. Use
<code>--jaer-tcp 127.0.0.1:{controlPort}</code> if you change the port.</p>
<hr>
<h3>Protocol</h3>
<p>Double-buffered mmap: two slots of 64-byte header plus
<code>maxEvents &times; 16</code> bytes. Magic <code>JAER</code>,
<code>dtype=EVENT</code> (2). TCP JSON lines on <code>127.0.0.1</code>:
<code>HELLO</code> on connect, <code>FRAME_READY</code> per window.
<code>seq</code> is written last (publication fence).</p>
<p>Event record (little-endian, 16 bytes): <code>int64 t_us</code>,
<code>uint16 x</code>, <code>uint16 y</code>, <code>uint8 p</code> (0=Off, 1=On),
3 pad bytes. <code>t_us</code> is the 32-bit jAER timestamp zero-extended.</p>
<p><b>flipY</b> (default on): jAER sensor <code>y=0</code> is lower-left.
Python / OpenCV / FireNet use upper-left. When <code>flipY</code> is checked,
exported <code>y</code> is <code>height-1-y</code> so row 0 is the top of the image.
Uncheck it only if the consumer also uses jAER lower-left coordinates.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SharedMemoryEventWindowSender extends EventFilter2D {

    public static final int PROTOCOL_VERSION = 1;
    public static final int HEADER_BYTES = 64;
    public static final int NUM_BUFFERS = 2;
    public static final int EVENT_BYTES = 16;
    public static final int DTYPE_EVENT = 2;
    public static final int DEFAULT_CONTROL_PORT = 14101;
    public static final int DEFAULT_MAX_EVENTS = 100000;
    public static final byte[] MAGIC = new byte[]{'J', 'A', 'E', 'R'};

    public enum TimeSliceMethod {
        EventCount,
        TimeIntervalUs
    }

    private String mmapPath;
    private int controlPort = getInt("controlPort", DEFAULT_CONTROL_PORT);
    private int eventsPerWindow = getInt("eventsPerWindow", 0);
    private float numEventsPerPixel = getFloat("numEventsPerPixel", 0.35f);
    private TimeSliceMethod timeSliceMethod = TimeSliceMethod.valueOf(
            getString("timeSliceMethod", TimeSliceMethod.EventCount.name()));
    private int timeDurationUs = getInt("timeDurationUs", 33000);
    private int maxEvents = getInt("maxEvents", DEFAULT_MAX_EVENTS);
    private boolean flipY = getBoolean("flipY", true);
    private boolean deleteMmapOnCleanup = getBoolean("deleteMmapOnCleanup", true);

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
    private int count;
    private long firstTimestampUs;
    private long lastTimestampUs;
    private boolean started;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Thread notifyThread;
    private final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();
    private final AtomicBoolean publisherRunning = new AtomicBoolean(false);
    private final AtomicReference<String> pendingNotify = new AtomicReference<>();
    private final Object notifyLock = new Object();

    public SharedMemoryEventWindowSender(AEChip chip) {
        super(chip);
        mmapPath = getString("mmapPath", defaultMmapPath());
        setPropertyTooltip("mmapPath", "memory-mapped file path shared with the Python consumer");
        setPropertyTooltip("controlPort", "localhost TCP port for JSON-lines HELLO / FRAME_READY (127.0.0.1 only)");
        setPropertyTooltip("eventsPerWindow", "Events per window when timeSliceMethod=EventCount; 0 = width*height*numEventsPerPixel");
        setPropertyTooltip("numEventsPerPixel", "Used when eventsPerWindow=0 (E2VID default 0.35)");
        setPropertyTooltip("timeSliceMethod", "Close a window after N events or after timeDurationUs");
        setPropertyTooltip("timeDurationUs", "Window duration in microseconds when timeSliceMethod=TimeIntervalUs");
        setPropertyTooltip("maxEvents", "Mmap slot capacity (must be >= events per window)");
        setPropertyTooltip("flipY", "Export y with 0 at the top (Python / OpenCV / FireNet). jAER sensor y=0 is lower-left; this writes height-1-y. Default on.");
        setPropertyTooltip("deleteMmapOnCleanup", "delete the mmap file when the filter is disabled or jAER exits");
        ensureWindowCapacity(maxEvents);
    }

    @Override
    public String getHelp() {
        String html = super.getHelp();
        if (html == null) {
            return null;
        }
        String path = mmapPath != null ? mmapPath : defaultMmapPath();
        return html.replace("{mmapPath}", path).replace("{controlPort}", Integer.toString(controlPort));
    }

    public static String defaultMmapPath() {
        return new File(System.getProperty("java.io.tmpdir"), "jaer_dvs_events.mmap").getAbsolutePath();
    }

    public static SharedMemoryEventWindowSender find(AEChip chip) {
        if (chip == null || chip.getFilterChain() == null) {
            return null;
        }
        return (SharedMemoryEventWindowSender) chip.getFilterChain().findFilter(SharedMemoryEventWindowSender.class);
    }

    /**
     * Appends a disabled sender if the saved filter chain does not include it.
     */
    public static void ensurePresent(AEChip chip) {
        if (chip == null) {
            return;
        }
        FilterChain chain = chip.getFilterChain();
        if (chain == null || chain.findFilter(SharedMemoryEventWindowSender.class) != null) {
            return;
        }
        try {
            SharedMemoryEventWindowSender f = new SharedMemoryEventWindowSender(chip);
            chain.add(f);
            f.initFilter();
            f.setPreferredEnabledState();
            ArrayList<String> names = new ArrayList<>();
            for (EventFilter2D x : chain) {
                names.add(x.getClass().getName());
            }
            chain.storePreferredFiltersForChip(names);
            log.info("Appended SharedMemoryEventWindowSender to filter chain for " + chip.getClass().getSimpleName());
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not add SharedMemoryEventWindowSender: " + e, e);
        }
    }

    public static int slotSize(int maxEvents) {
        return HEADER_BYTES + Math.max(0, maxEvents) * EVENT_BYTES;
    }

    public static int fileSize(int maxEvents) {
        return NUM_BUFFERS * slotSize(maxEvents);
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
        resetFilter();
        if (isFilterEnabled()) {
            startPublisher();
        }
    }

    @Override
    public void resetFilter() {
        count = 0;
        started = false;
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
    public synchronized EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!isFilterEnabled() || in == null || !publisherRunning.get()) {
            return in;
        }
        int width = chipWidth();
        int height = chipHeight();
        int target = targetEventsPerWindow();
        int cap = Math.max(maxEvents, target);
        if (mapped == null || mappedWidth != width || mappedHeight != height || mappedMaxEvents < cap) {
            maxEvents = cap;
            remap(width, height, cap);
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
            if (!started) {
                started = true;
                firstTimestampUs = tUs;
            }
            lastTimestampUs = tUs;
            if (count >= mappedMaxEvents) {
                publishWindow(width, height);
            }
            windowTUs[count] = tUs;
            windowX[count] = x;
            windowY[count] = y;
            windowP[count] = p;
            count++;
            if (windowComplete(target, tUs)) {
                publishWindow(width, height);
            }
        }
        return in;
    }

    private boolean windowComplete(int target, long tUs) {
        if (!started || count < 1) {
            return false;
        }
        if (timeSliceMethod == TimeSliceMethod.TimeIntervalUs) {
            long dt = tUs - firstTimestampUs;
            if (dt < 0) {
                dt += 0x100000000L;
            }
            return dt >= timeDurationUs;
        }
        return count >= target;
    }

    synchronized void startPublisher() {
        if (publisherRunning.get()) {
            return;
        }
        try {
            int target = targetEventsPerWindow();
            int cap = Math.max(maxEvents, target);
            maxEvents = cap;
            ensureWindowCapacity(cap);
            remap(chipWidth(), chipHeight(), cap);
            startControlServer();
            publisherRunning.set(true);
            log.info(String.format("SharedMemoryEventWindowSender mmap=%s  TCP=127.0.0.1:%d  %dx%d  maxEvents=%d  N=%d",
                    mmapPath, controlPort, chipWidth(), chipHeight(), mappedMaxEvents, target));
        } catch (IOException e) {
            log.warning("could not start event-window publisher: " + e);
            stopPublisher();
        }
    }

    synchronized void stopPublisher() {
        publisherRunning.set(false);
        stopControlServer();
        unmap(deleteMmapOnCleanup);
        resetFilter();
    }

    private void remap(int width, int height, int maxEv) {
        if (width <= 0 || height <= 0 || maxEv <= 0) {
            return;
        }
        if (mapped != null && mappedWidth == width && mappedHeight == height && mappedMaxEvents == maxEv) {
            return;
        }
        unmap(false);
        try {
            File f = new File(mmapPath);
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            mmapFile = new RandomAccessFile(f, "rw");
            long size = fileSize(maxEv);
            mmapFile.setLength(size);
            mmapChannel = mmapFile.getChannel();
            mapped = mmapChannel.map(FileChannel.MapMode.READ_WRITE, 0, size);
            mapped.order(ByteOrder.LITTLE_ENDIAN);
            mappedWidth = width;
            mappedHeight = height;
            mappedMaxEvents = maxEv;
            writeEmptyHeaders();
            log.info(String.format("mapped %s (%d bytes, 2 x %d events, %dx%d)",
                    mmapPath, size, maxEv, width, height));
        } catch (IOException e) {
            log.warning("could not map " + mmapPath + ": " + e);
            unmap(false);
        }
    }

    private void writeEmptyHeaders() {
        if (mapped == null) {
            return;
        }
        for (int slot = 0; slot < NUM_BUFFERS; slot++) {
            writeHeader(slot, 0, mappedWidth, mappedHeight, 0, 0);
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

    private void publishWindow(int width, int height) {
        if (!publisherRunning.get() || count < 1 || mapped == null) {
            resetFilter();
            return;
        }
        int n = count;
        int slot = (nextSeq & 1);
        int seq = nextSeq++;
        int eventBase = slot * slotSize(mappedMaxEvents) + HEADER_BYTES;
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
        writeHeader(slot, seq, width, height, n, lastTimestampUs);
        mapped.force();
        String json = String.format(
                "{\"type\":\"FRAME_READY\",\"seq\":%d,\"width\":%d,\"height\":%d,\"event_count\":%d,\"dtype\":\"EVENT\",\"buffer_index\":%d,\"timestamp_us\":%d,\"event_bytes\":%d}",
                seq, width, height, n, slot, lastTimestampUs, EVENT_BYTES);
        pendingNotify.set(json);
        synchronized (notifyLock) {
            notifyLock.notifyAll();
        }
        resetFilter();
    }

    private void writeHeader(int slot, int seq, int width, int height, int eventCount, long timestampUs) {
        int base = slot * slotSize(mappedMaxEvents);
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

    private void startControlServer() throws IOException {
        stopControlServer();
        serverSocket = new ServerSocket(controlPort, 8, InetAddress.getByName("127.0.0.1"));
        acceptThread = new Thread(this::acceptLoop, "SharedMemoryEventWindowSender-accept");
        acceptThread.setDaemon(true);
        notifyThread = new Thread(this::notifyLoop, "SharedMemoryEventWindowSender-notify");
        notifyThread.setDaemon(true);
        acceptThread.start();
        notifyThread.start();
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
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        if (notifyThread != null) {
            notifyThread.interrupt();
            synchronized (notifyLock) {
                notifyLock.notifyAll();
            }
            notifyThread = null;
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
        return String.format(
                "{\"type\":\"HELLO\",\"version\":%d,\"kind\":\"event_window\",\"path\":\"%s\",\"width\":%d,\"height\":%d,\"header_bytes\":%d,\"num_buffers\":%d,\"event_bytes\":%d,\"max_events\":%d,\"dtype\":\"EVENT\"}",
                PROTOCOL_VERSION, jsonEscape(mmapPath), chipWidth(), chipHeight(), HEADER_BYTES, NUM_BUFFERS,
                EVENT_BYTES, mappedMaxEvents > 0 ? mappedMaxEvents : maxEvents);
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public String getMmapPath() {
        return mmapPath;
    }

    public synchronized void setMmapPath(String mmapPath) {
        String old = this.mmapPath;
        this.mmapPath = mmapPath;
        putString("mmapPath", mmapPath);
        getSupport().firePropertyChange("mmapPath", old, mmapPath);
        if (publisherRunning.get()) {
            mappedWidth = 0;
            remap(chipWidth(), chipHeight(), Math.max(maxEvents, targetEventsPerWindow()));
        }
    }

    public int getControlPort() {
        return controlPort;
    }

    public synchronized void setControlPort(int controlPort) {
        int old = this.controlPort;
        this.controlPort = controlPort;
        putInt("controlPort", controlPort);
        getSupport().firePropertyChange("controlPort", old, controlPort);
        if (publisherRunning.get()) {
            try {
                startControlServer();
            } catch (IOException e) {
                log.warning("could not restart control server on port " + controlPort + ": " + e);
            }
        }
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

    public TimeSliceMethod getTimeSliceMethod() {
        return timeSliceMethod;
    }

    public void setTimeSliceMethod(TimeSliceMethod timeSliceMethod) {
        TimeSliceMethod old = this.timeSliceMethod;
        this.timeSliceMethod = timeSliceMethod;
        putString("timeSliceMethod", timeSliceMethod.name());
        getSupport().firePropertyChange("timeSliceMethod", old, timeSliceMethod);
    }

    public int getTimeDurationUs() {
        return timeDurationUs;
    }

    public void setTimeDurationUs(int timeDurationUs) {
        int old = this.timeDurationUs;
        this.timeDurationUs = Math.max(1, timeDurationUs);
        putInt("timeDurationUs", this.timeDurationUs);
        getSupport().firePropertyChange("timeDurationUs", old, this.timeDurationUs);
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public synchronized void setMaxEvents(int maxEvents) {
        int old = this.maxEvents;
        this.maxEvents = Math.max(1, maxEvents);
        putInt("maxEvents", this.maxEvents);
        getSupport().firePropertyChange("maxEvents", old, this.maxEvents);
        if (publisherRunning.get()) {
            remap(chipWidth(), chipHeight(), Math.max(this.maxEvents, targetEventsPerWindow()));
        }
    }

    public boolean isFlipY() {
        return flipY;
    }

    public void setFlipY(boolean flipY) {
        boolean old = this.flipY;
        this.flipY = flipY;
        putBoolean("flipY", flipY);
        getSupport().firePropertyChange("flipY", old, flipY);
    }

    public boolean isDeleteMmapOnCleanup() {
        return deleteMmapOnCleanup;
    }

    public void setDeleteMmapOnCleanup(boolean deleteMmapOnCleanup) {
        this.deleteMmapOnCleanup = deleteMmapOnCleanup;
        putBoolean("deleteMmapOnCleanup", deleteMmapOnCleanup);
    }
}
